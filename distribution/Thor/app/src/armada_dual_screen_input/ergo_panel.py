"""Ergonomic front end for the bundled Armada Dual Screen Input 0.1.7 engine.

Keeps the original supervisor, window, layer-shell anchors, viewport mapping,
GTK touch-sequence lifecycle, /dev/uinput bridge and touchpad gesture engine.
Only renders/routs the keyboard UI. It never reads or grabs an evdev device.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import tempfile
import time
import traceback
from .ergo_layout import WIDTH,HEIGHT,VERSION,DEFAULTS,normalize_settings
from .ergo_controller import Controller
from .ergo_output import SerialOutput
from .ergo_render import draw


def atomic_json(path,data):
    path=Path(path)
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_symlink(): raise OSError('Configuration is a symbolic link')
    fd,temp=tempfile.mkstemp(prefix='.ergo-',dir=path.parent)
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as stream:
            json.dump(data,stream,ensure_ascii=False,indent=2)
            stream.write('\n');stream.flush();os.fsync(stream.fileno())
        os.chmod(temp,0o600);os.replace(temp,path)
    finally:
        Path(temp).unlink(missing_ok=True)


def read_object(path):
    path=Path(path)
    if not path.exists():return {}
    if path.is_symlink():raise OSError('Configuration is a symbolic link')
    result=json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(result,dict):raise ValueError('Configuration is not an object')
    return result


class Host:
    def __init__(self,app,upstream,GLib,style):
        self.app,self.upstream,self.GLib,self.style=app,upstream,GLib,style
        self.config_path=Path(upstream.CONFIG_PATH).with_name('ergo.json')
        self.message=''
        self.needs_retry=False
        self.mouse_holds={}
        self.ready_written=False
        self.resetting=False
        self.closed=False
        source={key:value for key,value in app.input_cfg.items() if key in DEFAULTS}
        source['language']=app.input_cfg.get('keyboard_language','en_US')
        source.update(read_object(self.config_path))
        settings=normalize_settings(source)
        self.output=SerialOutput(language=lambda:self.controller.settings['language'],
             available=lambda:bool(app.languages.ready and app.languages.worker and not app.languages.closed),
             submit=lambda request:app.languages.worker.submit(request) if app.languages.worker else False,
             key=app.bridge.key,schedule=self.schedule,cancel=self.cancel,
             error=self.error,abort=self.abort_worker)
        self.controller=Controller(emit=self.output.send,schedule=self.schedule,cancel=self.cancel,
             changed=self.changed,setting_changed=self.setting_changed,external=self.external,settings=settings)
        previous_response=app.languages._response
        def response(generation,result):
            if self.closed or generation!=app.languages._generation:return
            token=result.get('ergo_token')
            safe=dict(result)
            if type(token) is int:safe.pop('forward_key',None)
            previous_response(generation,safe)
            if result.get('status')=='unavailable':
                self.needs_retry=True
                self.message='Escrita indisponível. Usa Modo → Reativar escrita.'
                if self.output.active:self.output.fail(self.message)
            elif type(token) is int:self.output.reply(result)
            self.controller.set_ime_state(app.languages.preedit, app.languages.candidates, app.languages.revision)
            self.changed()
        app.languages._response=response
        if app.languages.language!=settings['language']:
            app.languages.set_language(settings['language'])
        app.input_cfg['keyboard_language']=settings['language']
        app.mode=app._last_input_mode='split'
        self.ayn_timer=self.GLib.timeout_add(1500,self.poll_ayn)
        self.poll_ayn()

    def poll_ayn(self):
        if self.closed:return False
        from .ergo_ayn import paths, read_json, STATE_NAMES
        status=read_json(paths()[1]/'ayn-status.json')
        value=status.get('status','unavailable')
        if not self.controller.settings['ayn_toggle_enabled']:value='off'
        if value not in STATE_NAMES:value='unavailable'
        reason=status.get('reason','')
        backend=status.get('backend','')
        if not isinstance(reason,str):reason=''
        if backend not in ('inputplumber','dedicated_gpio'):backend=''
        if (self.controller.ayn_status,self.controller.ayn_reason,self.controller.ayn_backend)!=(value,reason,backend):
            if value=='screen_off' and self.controller.ayn_status!=value:self.reset()
            self.controller.ayn_status=value
            self.controller.ayn_reason=reason
            self.controller.ayn_backend=backend
            if self.controller.settings_tab=='ayn':self.changed()
        return True

    def schedule(self,ms,callback):return self.GLib.timeout_add(max(1,int(ms)),callback)
    def cancel(self,token):
        try:self.GLib.source_remove(token)
        except (TypeError,ValueError):pass
    def changed(self):
        if not self.closed and not getattr(self.app,'_cleaned',False):
            self.app.drawing_area.queue_draw()
    def error(self,message):
        self.message=message
        self.needs_retry=True
        self.changed()
    def abort_worker(self):
        self.app.languages.stop()
    def retry(self):
        self.output.reset(abort=True)
        self.message=''
        self.needs_retry=False
        self.app.languages.set_language(self.controller.settings['language'])
        self.sync();self.changed()

    def sync(self):
        app=self.app
        visible=(not app.collapsed and app.window.get_mapped()
                 and self.controller.view!='pad' and not self.controller.settings_tab)
        if app.keyboard_guard is not None:
            app.keyboard_guard.update(app.session_mode=='game' and visible)
        app.languages.sync(visible)
        if (visible and not self.needs_retry and self.controller.settings['language']!='en_US'
                and app.languages.worker is None and not app.languages.closed
                and app.session_mode=='game'):
            app.languages.set_language(self.controller.settings['language'])

    def setting_changed(self,name,value):
        try:
            current=read_object(self.config_path)
            changed=normalize_settings({**self.controller.settings,name:value})
            # Own settings are authoritative in this copy; leave the prior app's
            # config.json untouched so reversing also restores its preferences.
            atomic_json(self.config_path,{**current,**changed})
            app=self.app
            if name=='language':
                self.output.reset(abort=True)
                self.needs_retry=False;self.message=''
                app.input_cfg['keyboard_language']=value
                app.languages.set_language(value)
                self.controller.set_ime_state('',[],0)
            elif name in ('sensitivity','scroll_speed','tap_to_click','glide','friction'):
                app.gesture.set_settings(**{name:value})
                app.input_cfg[name]=value
            elif name=='scroll_scale':
                app.scroll_scale=value;app.scroll_output.scale=value
                app.scroll_output.rest_x=app.scroll_output.rest_y=0.
            elif name.startswith('keyboard_'):
                app.input_cfg[name]=value
                if name in app.keyboard_cfg:app.keyboard_cfg[name]=value
            return True
        except (OSError,ValueError,TypeError):return False

    def apply_settings(self):
        values=self.controller.settings
        self.app.gesture.set_settings(**values)
        self.app.scroll_scale=values['scroll_scale']
        self.app.scroll_output.scale=values['scroll_scale']
        for key in self.app.keyboard_cfg:
            if key in values:self.app.keyboard_cfg[key]=values[key]

    def mouse(self,name,tid,down):
        code=self.upstream.BTN_LEFT if name=='left' else self.upstream.BTN_RIGHT
        if down:
            first=code not in self.mouse_holds.values()
            self.mouse_holds[tid]=code
            self.app.gesture.suppress_taps_for_current_group()
            if first:self.app.bridge.mouse_button(code,True)
        else:
            self.mouse_holds.pop(tid,None)
            if code not in self.mouse_holds.values():self.app.bridge.mouse_button(code,False)
        self.changed()

    def release_pointer(self):
        self.app.gesture.glide_stop.set()
        self.app.gesture.reset_all()
        for code in set(self.mouse_holds.values()):self.app.bridge.mouse_button(code,False)
        self.mouse_holds.clear()
        self.app._touch_owner.clear()

    def reset(self,abort=True):
        if self.resetting:return
        self.resetting=True
        try:
            self.controller.reset_contacts()
            self.release_pointer()
            self.output.reset(abort=abort)
            self.needs_retry=False
            self.message=''
        finally:self.resetting=False

    def external(self,action,value):
        if action=='mouse_down':self.mouse(value['button'],value['tid'],True)
        elif action=='mouse_up':self.mouse(value['button'],value['tid'],False)
        elif action=='page_reset':
            # No native text reset here: a pending accent survives Symbols -> ABC.
            self.release_pointer()
        elif action=='view_reset':self.reset()
        elif action=='settings_enter':
            self.reset()
            self.app.languages.sync(False)
            if self.app.keyboard_guard:self.app.keyboard_guard.update(False)
        elif action=='settings_leave':
            # Controller clears settings_tab before this callback.
            self.message='';self.needs_retry=False
            self.sync()
        elif action=='retry':self.retry()
        elif action=='release':
            self.reset();self.app.bridge.release_all();self.retry()
        elif action=='hide':self.app._set_collapsed(True)

    def owner(self,x,y):
        c=self.controller
        if c.settings_tab or c.menu or c.popup or c.picker:return 'ergo'
        if c.layout.pad and c.layout.pad.contains(x,y):return 'trackpad'
        return 'ergo'

    def down(self,tid,x,y,now):
        owner=self.owner(x,y)
        self.app._touch_owner[tid]=owner
        if owner=='trackpad':
            self.app.gesture.touch_down(tid,x,y,now)
            if self.mouse_holds:self.app.gesture.suppress_taps_for_current_group()
        else:self.controller.down(tid,x,y,now)

    def move(self,tid,x,y,now,interval=None):
        if self.app._touch_owner.get(tid)=='trackpad':
            self.app.gesture.touch_move(tid,x,y,now,motion_interval=interval)
        else:self.controller.move(tid,x,y,now)

    def up(self,tid,now):
        owner=self.app._touch_owner.pop(tid,None)
        if owner=='trackpad':
            if self.mouse_holds:self.app.gesture.suppress_taps_for_current_group()
            self.app.gesture.touch_up(tid,now)
        else:self.controller.up(tid,now)

    def render(self,widget,cr):
        app=self.app
        allocation=widget.get_allocation()
        cr.save()
        try:
            cr.scale(allocation.width/WIDTH,allocation.height/HEIGHT)
            message=self.message
            if (not message and self.controller.settings['language']!='en_US'
                    and self.controller.view!='pad' and not self.controller.settings_tab
                    and not app.languages.ready):
                message=('A preparar a escrita…' if app.session_mode=='game' else
                         'Este teclado requer Game Mode. Em Desktop, usa English (US).')
            draw(cr,self.controller,self.style.label,message=message,
                 preedit=app.languages.preedit,
                 mouse_held=tuple('mouse:'+('left' if code==self.upstream.BTN_LEFT else 'right')
                                  for code in self.mouse_holds.values()))
        finally:cr.restore()
        if not self.ready_written and allocation.width>=240 and allocation.height>=200:
            # Only records software readiness/actual allocated geometry, never
            # keystrokes or the contents of the upper screen.
            root=Path(os.environ.get('XDG_STATE_HOME',str(Path.home()/'.local/state')))/'thor-ergo'
            atomic_json(root/'ready.json',{'version':VERSION,'pid':os.getpid(),
               'app':str(Path(__file__).resolve().parents[2]),'time':time.time(),
               'width':allocation.width,'height':allocation.height,'rendered':True})
            self.ready_written=True
        return True

    def close(self):
        if self.closed:return
        self.cancel(self.ayn_timer)
        self.reset()
        self.output.close()
        self.closed=True


def install(PanelApp,upstream,GLib,style):
    if getattr(PanelApp,'_ergo_extension',False):raise RuntimeError('Ergo applied twice')
    PanelApp._ergo_extension=True
    old_init=PanelApp.__init__
    old_draw=PanelApp.on_draw
    old_reset=PanelApp._reset_input
    old_cleanup=PanelApp.cleanup
    old_sync=PanelApp._sync_keyboard_guard
    old_language=PanelApp._queue_language_draw
    old_owner=PanelApp._owner_at
    old_down=PanelApp._handle_touch_down
    old_move=PanelApp._handle_touch_move
    old_timed=PanelApp._handle_timed_touch_move
    old_up=PanelApp._handle_touch_up
    old_touch_display=PanelApp._sync_touch_display
    old_stats=PanelApp._queue_stats_draw

    def init(app,*args,**kwargs):
        app.ergo_host=None
        old_init(app,*args,**kwargs)
        app.ergo_host=Host(app,upstream,GLib,style)
        app.ergo_host.apply_settings()

    def on_draw(app,widget,cr):
        host=getattr(app,'ergo_host',None)
        if host is None:return old_draw(app,widget,cr)
        if app.collapsed:
            from .ergo_i18n import tr
            allocation=widget.get_allocation()
            cr.set_source_rgb(0.106,0.125,0.161);cr.paint()
            cr.set_source_rgb(0.945,0.953,0.961)
            style.label(cr,'Thor Ergo',24,allocation.height/2,22,bold=True,
                        max_width=allocation.width*.45)
            style.label(cr,tr('Abrir teclado',host.controller.ui_language),
                        allocation.width*.72,allocation.height/2,21,
                        max_width=allocation.width*.40,centered=True)
            return True
        try:return host.render(widget,cr)
        except Exception:
            # A render failure must never leave an invisible input-capturing UI.
            traceback.print_exc()
            host.reset()
            app.exit_code=78
            app.request_quit()
            return True

    def reset(app):
        host=getattr(app,'ergo_host',None)
        if host:host.reset()
        return old_reset(app)

    def cleanup(app):
        if getattr(app,'ergo_host',None):app.ergo_host.close()
        return old_cleanup(app)

    def sync(app,*args):
        if getattr(app,'ergo_host',None):return app.ergo_host.sync()
        return old_sync(app,*args)

    def language(app):
        if getattr(app,'ergo_host',None):return app.ergo_host.changed()
        return old_language(app)

    def owner(app,x,y):
        if getattr(app,'ergo_host',None):return app.ergo_host.owner(x,y)
        return old_owner(app,x,y)

    def down(app,tid,x,y,now):
        if getattr(app,'ergo_host',None):return app.ergo_host.down(tid,x,y,now)
        return old_down(app,tid,x,y,now)

    def move(app,tid,x,y,now):
        if getattr(app,'ergo_host',None):return app.ergo_host.move(tid,x,y,now)
        return old_move(app,tid,x,y,now)

    def timed(app,tid,x,y,now,interval):
        if getattr(app,'ergo_host',None):return app.ergo_host.move(tid,x,y,now,interval)
        return old_timed(app,tid,x,y,now,interval)

    def up(app,tid,now):
        if getattr(app,'ergo_host',None):return app.ergo_host.up(tid,now)
        return old_up(app,tid,now)

    def touch_display(app):
        if getattr(app,'ergo_host',None):return  # no old top-pad overlay on new keys
        return old_touch_display(app)

    def stats(app):
        if getattr(app,'ergo_host',None):return  # no unnecessary full-screen 1 Hz repaint
        return old_stats(app)

    PanelApp.__init__=init
    PanelApp.on_draw=on_draw
    PanelApp._reset_input=reset
    PanelApp.cleanup=cleanup
    PanelApp._sync_keyboard_guard=sync
    PanelApp._queue_language_draw=language
    PanelApp._owner_at=owner
    PanelApp._handle_touch_down=down
    PanelApp._handle_touch_move=move
    PanelApp._handle_timed_touch_move=timed
    PanelApp._handle_touch_up=up
    PanelApp._sync_touch_display=touch_display
    PanelApp._queue_stats_draw=stats
