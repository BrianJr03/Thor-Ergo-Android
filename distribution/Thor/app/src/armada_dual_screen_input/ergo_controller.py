"""Touch UI state machine. All callbacks run on the GTK/main-loop thread.

Injectable clock/timers/output allow testing without a display or /dev/uinput.
No typed data is logged or persisted. Contact ownership never changes on move.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Any, Callable
import time
from .ergo_layout import (ACCENTS, MOD_CODES, REPEAT_CODES, RANGES, DEFAULTS,
                          Key, build, menu_keys, popup_geometry, normalize_settings, text_for_key, language_picker)
from .ergo_i18n import effective_ui
from .ergo_profiles import accents, upper

@dataclass
class Press:
    key: Key
    x: float
    y: float
    mods: tuple[str,...] = ()
    upper: bool = False
    caps: bool = False
    inside: bool = True
    committed: bool = False
    timer: Any = None
    repeat: Any = None
    cancelled: bool = False

@dataclass
class Popup:
    tid: int
    rect: Any
    options: tuple
    selected: int | None = None

class Controller:
    def __init__(self, *, emit: Callable, schedule: Callable, cancel: Callable,
                 changed: Callable, setting_changed: Callable, external: Callable,
                 settings=None, clock=time.monotonic):
        self.emit, self.schedule, self.cancel_timer = emit, schedule, cancel
        self.changed, self.setting_changed, self.external = changed, setting_changed, external
        self.clock=clock
        self.settings=normalize_settings(settings)
        self.page='abc'
        self.view=self.settings['view']
        self.settings_tab=None
        self.picker=None
        self.ime_preedit=''
        self.ime_candidates=[]
        self.ime_revision=0
        self.ime_page=0
        self.ayn_status='off'
        self.ayn_reason=''
        self.ayn_backend=''
        self.menu=False
        self.previous_page='abc'
        self.modifiers={name:0 for name in MOD_CODES}  # 0 off, 1 one-shot, 2 locked
        self.last_mod_taps={}
        self.contacts: dict[int,Press]={}
        self.popup: Popup | None=None
        self.note=''
        self.layout=build(self.page,self.view,self.settings['language'], ui_language=self.ui_language)

    @property
    def ui_language(self):
        return effective_ui(self.settings)

    def set_ime_state(self, preedit, candidates, revision):
        if revision != self.ime_revision:
            self.ime_page=0
        self.ime_preedit=preedit
        self.ime_candidates=list(candidates)
        self.ime_revision=revision
        self.changed()

    def rebuild(self):
        self.layout=build(self.page,self.view,self.settings['language'],settings=self.settings_tab,
                          ui_language=self.ui_language)
        self.changed()

    def upper(self):
        return bool(self.modifiers['shift'])

    def mods_snapshot(self):
        return tuple(name for name in MOD_CODES if self.modifiers[name])

    def _cancel_timers(self,press):
        for field_name in ('timer','repeat'):
            token=getattr(press,field_name)
            if token is not None:
                self.cancel_timer(token)
                setattr(press,field_name,None)

    def reset_contacts(self, clear_modifiers=True):
        for press in self.contacts.values(): self._cancel_timers(press)
        self.contacts.clear()
        self.popup=None
        self.picker=None
        self.menu=False
        if clear_modifiers:
            self.modifiers={name:0 for name in MOD_CODES}
            self.last_mod_taps.clear()
        self.changed()

    def pressed_ids(self):
        return {p.key.ident for p in self.contacts.values() if p.inside and not p.cancelled}

    def hit(self,x,y):
        if self.picker:
            return next((k for k in language_picker(self.picker,self.ui_language) if k.rect.contains(x,y)),None)
        if self.menu:
            return next((key for key in menu_keys(self.ui_language) if key.rect.contains(x,y)),None)
        return self.layout.hit(x,y)

    def down(self,tid,x,y,now=None):
        if tid in self.contacts: return
        now=self.clock() if now is None else now
        if self.popup:
            # A second finger must not type through an open accent menu.
            self.cancel_contact(self.popup.tid)
        key=self.hit(x,y)
        if key is None:
            if self.menu:
                self.menu=False; self.changed()
            return
        if key.action=='mouse':
            if not self.settings_tab:
                self.contacts[tid]=Press(key,x,y)
                self.external('mouse_down',{'button':key.value,'tid':tid})
                self.changed()
            return
        # Resolve older ordinary letters before a new key, preserving typing order
        # when two thumbs overlap. Never commit a cancelled or accent-popup press.
        for previous in tuple(self.contacts.values()):
            if (not previous.committed and not previous.cancelled and previous.inside
                    and previous.key.action=='text'):
                self._send(previous)
                previous.committed=True
                self._cancel_timers(previous)
        press=Press(key,x,y,self.mods_snapshot(),self.upper(),self.modifiers['shift']==2)
        self.contacts[tid]=press
        if key.action=='text' and accents(self.settings['language'],key.value) and not any(m in press.mods for m in ('ctrl','alt','super','altgr')):
            def open_popup():
                current=self.contacts.get(tid)
                if current is not press or press.cancelled or not press.inside or press.committed:
                    return False
                press.timer=None
                variants=accents(self.settings['language'],key.value)
                if press.upper: variants=tuple(upper(c) for c in variants)
                rect,options=popup_geometry(key,variants)
                self.popup=Popup(tid,rect,options)
                self.changed()
                return False
            press.timer=self.schedule(self.settings['long_press_ms'],open_popup)
        elif key.action=='key' and key.code in REPEAT_CODES:
            self._send(press,consume=False)
            press.committed=True
            if self.settings['keyboard_repeat_enabled']:
                def again():
                    if self.contacts.get(tid) is not press or press.cancelled or not press.inside:
                        press.repeat=None
                        return False
                    press.repeat=None
                    self._send(press,consume=False)
                    press.repeat=self.schedule(self.settings['keyboard_repeat_interval_ms'],again)
                    return False
                press.repeat=self.schedule(self.settings['keyboard_repeat_delay_ms'],again)
        self.changed()

    def move(self,tid,x,y,now=None):
        press=self.contacts.get(tid)
        if press is None: return
        press.x,press.y=x,y
        if self.popup and self.popup.tid==tid:
            self.popup.selected=next((i for i,k in enumerate(self.popup.options) if k.rect.contains(x,y)),None)
            self.changed();return
        inside=press.key.rect.contains(x,y)
        # No slide-to-a-different-letter after leaving the initial key; cancel
        # instead, avoiding key/pad crossing and stale long-press callbacks.
        if not inside and press.key.action!='mouse':
            press.cancelled=True
            self._cancel_timers(press)
        if inside!=press.inside:
            press.inside=inside
            self.changed()

    def up(self,tid,now=None):
        press=self.contacts.pop(tid,None)
        if press is None: return
        self._cancel_timers(press)
        if press.key.action=='mouse':
            self.external('mouse_up',{'button':press.key.value,'tid':tid})
        elif self.popup and self.popup.tid==tid:
            popup=self.popup
            self.popup=None
            if popup.selected is not None:
                self.emit({'kind':'literal','text':popup.options[popup.selected].value})
                self.consume_modifiers(press.mods)
        elif not press.cancelled and press.inside and not press.committed:
            self._activate(press,self.clock() if now is None else now)
        elif press.committed:
            self.consume_modifiers(press.mods)
        self.changed()

    def cancel_contact(self,tid):
        press=self.contacts.pop(tid,None)
        if press:
            self._cancel_timers(press)
            if press.key.action=='mouse':
                self.external('mouse_up',{'button':press.key.value,'tid':tid})
        if self.popup and self.popup.tid==tid: self.popup=None
        self.changed()

    def consume_modifiers(self,names):
        for name in names:
            if self.modifiers.get(name)==1: self.modifiers[name]=0

    def _send(self,press,consume=True):
        key=press.key
        if key.action=='text':
            value=text_for_key(key, self.settings['language'],
                               2 if press.caps else (1 if press.upper else 0))
            self.emit({'kind':'text','text':value,'code':key.code,'mods':press.mods,
                       'caps':press.caps,'shift':press.upper and not press.caps})
        elif key.action=='key':
            self.emit({'kind':'key','code':key.code,
                       'mods':tuple(m for m in press.mods if m!='shift' or not press.caps)})
        if consume: self.consume_modifiers(press.mods)

    def change_page(self,page):
        # Page switches cancel touches but KEEP one-shot modifiers so Ctrl, ABC,
        # C and Shift, Fn, F1 remain possible. The host drops mouse contacts.
        self.reset_contacts(clear_modifiers=False)
        self.external('page_reset',None)
        self.page=page
        self.settings_tab=None
        self.rebuild()

    def _activate(self,press,now):
        key=press.key
        action=key.action
        if action in ('text','key'):
            self._send(press)
        elif action=='dead':
            self.emit({'kind':'dead','dead':key.value})
            self.change_page('abc')
        elif action=='modifier':
            name=key.value
            current=self.modifiers[name]
            window=self.settings['keyboard_caps_window_ms']/1000.
            double=now-self.last_mod_taps.get(name,-100.)<window
            self.modifiers[name]=2 if current==1 and double else (1 if current==0 else 0)
            self.last_mod_taps[name]=now
        elif action=='caps':
            self.modifiers['shift']=0 if self.modifiers['shift']==2 else 2
        elif action=='page': self.change_page(key.value)
        elif action=='fn':
            if self.settings_tab:
                self.settings_tab=None
                self.external('settings_leave',None)
            if self.page!='fn':
                self.previous_page=self.page
                self.change_page('fn')
            else: self.change_page(self.previous_page)
        elif action=='mode':
            self.reset_contacts(clear_modifiers=False)
            self.external('page_reset',None)
            self.menu=True
        elif action=='menu':
            self.menu=False
            if key.value in ('combined','keyboard','pad'):
                self.reset_contacts()
                self.external('view_reset',None)
                if self.setting_changed('view',key.value):
                    self.view=key.value;self.settings['view']=key.value
                self.settings_tab=None
                self.external('settings_leave',None)
                self.rebuild()
            elif key.value=='retry': self.external('retry',None)
            else:
                self.reset_contacts();self.external('release',None)
        elif action=='settings':
            if self.settings_tab:
                self.settings_tab=None
                self.external('settings_leave',None)
            else:
                self.reset_contacts()
                self.external('settings_enter',None)
                self.settings_tab='language'
            self.rebuild()
        elif action=='settings_tab':
            self.settings_tab=key.value;self.rebuild()
        elif action=='candidate':
            index=self.ime_page*6+key.value
            if 0<=index<len(self.ime_candidates):
                self.emit({'kind':'choose','index':index,'revision':self.ime_revision})
        elif action=='ime_page':
            self.ime_page=max(0,min((len(self.ime_candidates)-1)//6,self.ime_page+key.value))
        elif action=='choose_language':
            name,new=key.value
            self.picker=None
            self.reset_contacts()
            if self.setting_changed(name,new):
                self.settings[name]=new
                self.note='Guardado.'
            else:self.note='Não foi possível guardar. A definição anterior foi mantida.'
            self.rebuild()
        elif action=='setting':
            name,step=key.value
            old=self.settings[name]
            if name in ('language','ui_language'):
                self.picker=name;self.changed();return
            elif isinstance(old,bool): new=not old
            else:
                low,high,delta,_=RANGES[name]
                new=max(low,min(high,round(old+step*delta,4)))
                if delta>=1:new=int(round(new))
            if self.setting_changed(name,new):
                self.settings[name]=new
                self.note='Guardado.'
                self.rebuild()
            else:self.note='Não foi possível guardar. A definição anterior foi mantida.'
        elif action=='settings_back':
            self.settings_tab=None
            self.external('settings_leave',None)
            self.rebuild()
        elif action=='release':
            self.reset_contacts();self.external('release',None)
        elif action=='hide':
            self.reset_contacts();self.external('hide',None)
