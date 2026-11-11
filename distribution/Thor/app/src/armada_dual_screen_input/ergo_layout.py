"""Deterministic Thor keyboard geometry. No GTK, input devices or network.

All rectangles are in the same 1240x1080 logical DrawingArea coordinates as
Armada Dual Screen Input. The existing layer-shell allocation is not changed.
Drawing and touch hit-testing ALWAYS use these same rectangles.
"""
from __future__ import annotations
from dataclasses import dataclass, replace
from typing import Any
from .ergo_i18n import tr, LANGUAGES, LANGUAGE_NAMES
from .ergo_profiles import PROFILES, EXTRA_SYMBOLS, upper
from .ergo_us import US_MAP, US_SHIFT

WIDTH, HEIGHT = 1240.0, 1080.0
VERSION = '1.0'
PAGES = ('abc', 'numbers', 'symbols', 'fn')
VIEWS = ('combined', 'keyboard', 'pad')
ACCENTS = {'a': ('á', 'à', 'â', 'ã'), 'e': ('é', 'ê'), 'i': ('í',),
           'o': ('ó', 'ô', 'õ'), 'u': ('ú', 'ü')}
CODES = dict(zip('qwertyuiop', range(16, 26)))
CODES.update(zip('asdfghjkl', range(30, 39)))
CODES.update(zip('zxcvbnm', range(44, 51)))
CODES.update({'ç': 39, ',': 51, '.': 52, ' ': 57})
MOD_CODES = {'shift': 42, 'ctrl': 29, 'alt': 56, 'super': 125, 'altgr': 100}
REPEAT_CODES = frozenset((14, 111, 105, 103, 108, 106))
DEAD_KEYS = dict(zip(('´', '`', '^', '~', '¨'),
                     ('dead_acute', 'dead_grave', 'dead_circumflex', 'dead_tilde', 'dead_diaeresis')))
DEFAULTS = {'language': 'en_US', 'ui_language': 'auto', 'ayn_toggle_enabled': False, 'view': 'combined', 'long_press_ms': 400,
            'keyboard_repeat_enabled': True, 'keyboard_repeat_delay_ms': 350,
            'keyboard_repeat_interval_ms': 60, 'keyboard_caps_window_ms': 350,
            'sensitivity': 1.5, 'scroll_speed': 3, 'scroll_scale': 1.0,
            'tap_to_click': True, 'glide': True, 'friction': 5}
# (minimum, maximum, step, display unit)
RANGES = {'long_press_ms': (250, 900, 50, ' ms'),
          'keyboard_repeat_delay_ms': (150, 800, 25, ' ms'),
          'keyboard_repeat_interval_ms': (20, 200, 10, ' ms'),
          'keyboard_caps_window_ms': (150, 800, 25, ' ms'),
          'sensitivity': (.2, 5., .1, '×'), 'scroll_speed': (1, 5, 1, ''),
          'scroll_scale': (.01, 1., .05, '×'), 'friction': (1, 10, 1, '')}
SETTING_ROWS = {
 'language': (('language', 'Idioma do teclado'), ('ui_language', 'Idioma dos menus')),
 'keyboard': (('long_press_ms', 'Pressão longa: acentos'),
              ('keyboard_repeat_enabled', 'Repetir Apagar e setas'),
              ('keyboard_repeat_delay_ms', 'Espera antes de repetir'),
              ('keyboard_repeat_interval_ms', 'Intervalo de repetição'),
              ('keyboard_caps_window_ms', 'Duplo toque: maiúsculas')),
 'ayn': (('ayn_toggle_enabled', 'AYN: ligar/desligar o ecrã inferior'),),
 'mouse': (('sensitivity', 'Velocidade do ponteiro'), ('scroll_speed', 'Velocidade do scroll'),
           ('scroll_scale', 'Precisão do scroll'), ('tap_to_click', 'Tocar para clicar'),
           ('glide', 'Deslizamento com inércia'), ('friction', 'Travagem da inércia')),
}

@dataclass(frozen=True)
class Rect:
    x: float
    y: float
    w: float
    h: float
    def contains(self, x: float, y: float) -> bool:
        return self.x <= x < self.x + self.w and self.y <= y < self.y + self.h
    @property
    def center(self): return (self.x + self.w / 2, self.y + self.h / 2)
    def overlaps(self, other: 'Rect') -> bool:
        return (max(self.x, other.x) < min(self.x+self.w, other.x+other.w)-1e-7
                and max(self.y, other.y) < min(self.y+self.h, other.y+other.h)-1e-7)

@dataclass(frozen=True)
class Key:
    ident: str
    label: str
    action: str = 'text'
    value: Any = ''
    code: int = 0
    rect: Rect = Rect(0, 0, 0, 0)
    icon: str = ''
    accent: bool = False
    small: bool = False

@dataclass(frozen=True)
class Layout:
    keys: tuple[Key, ...]
    pad: Rect | None
    page: str
    view: str
    def hit(self, x, y):
        return next((key for key in reversed(self.keys) if key.rect.contains(x, y)), None)
    def find(self, ident):
        return next(key for key in self.keys if key.ident == ident)


def text_key(char, ident=None, code=None):
    return Key(ident or 'char:'+char, char.upper() if char.isalpha() and char not in 'ºª' else char,
               value=char, code=CODES.get(char, 0) if code is None else code)

def raw(label, code, ident=None, icon=''):
    return Key(ident or 'key:'+str(code), label, 'key', code, code, icon=icon)

def modifier(name, label, icon=''):
    return Key('mod:'+name, label, 'modifier', name, MOD_CODES[name], icon=icon)

def page_key(page, label):
    return Key('page:'+page, label, 'page', page)

def row(keys, y, h, x=12., w=1216., weights=None, gap=12.):
    weights = weights or [1.] * len(keys)
    if len(keys) != len(weights) or not keys or min(weights) <= 0:
        raise ValueError('Invalid row')
    unit = (w-gap*(len(keys)-1))/sum(weights)
    result=[]
    for key, weight in zip(keys, weights):
        kw = unit * weight
        result.append(replace(key, rect=Rect(x, y, kw, h)))
        x += kw + gap
    return result

def header(ui_language='pt_PT'):
    keys = [Key('mouse:left', 'Clique esq.', 'mouse', 'left', icon='mouse_left', small=True),
            Key('mouse:right', 'Clique dir.', 'mouse', 'right', icon='mouse_right', small=True),
            Key('header:fn', 'Fn', 'fn'), Key('header:mode', 'Modo', 'mode', icon='dropdown', small=True),
            Key('header:settings', '', 'settings', icon='gear'),
            Key('header:hide', 'Ocultar', 'hide', small=True)]
    return row([replace(k, label=tr(k.label, ui_language)) for k in keys], 16, 90,
               weights=[234,222,190,156,136,218])

def build(page='abc', view='combined', language='pt_PT', settings=None, ui_language='pt_PT'):
    if page not in PAGES or view not in VIEWS:
        raise ValueError('Unknown keyboard page/view')
    keys=header(ui_language)
    if settings:
        keys.extend(settings_keys(settings, ui_language))
        return Layout(tuple(keys), None, page, view)
    if view == 'pad':
        return Layout(tuple(keys), Rect(12,142,1216,914), page, view)
    if page == 'abc':
        keys.extend(row([text_key(c) for c in PROFILES[language][0]],142,172,gap=10))
        chars = PROFILES[language][1]
        keys.extend(row([text_key(c, code=39 if c==';' else None) for c in chars],330,172,gap=10))
        keys.extend(row([modifier('shift','', 'shift')]+[text_key(c) for c in PROFILES[language][2]]+
                        [raw('',14,icon='backspace')],518,172,
                        weights=[1.3]+[1.]*len(PROFILES[language][2])+[1.8],gap=10))
    elif page == 'numbers':
        keys.extend(row([text_key(c,code=(11 if c=='0' else int(c)+1)) for c in '1234567890'],142,172,gap=10))
        keys.extend(row([text_key(c) for c in ('@#€%&-+=()' if language not in ('en_US','zh_CN') else '@#$%&-+=()')],330,172,gap=10))
        keys.extend(row([page_key('symbols','Símb.')]+[text_key(c) for c in "'\":;/?!"]+
                        [raw('',14,icon='backspace')],518,172,
                        weights=[1.3]+[1.]*7+[1.8],gap=10))
    elif page == 'symbols':
        if language != 'en_US':
            keys.extend(row([text_key(c) for c in '\\|_*<>[]{}'],142,172,gap=10))
            accent_row=([text_key(c) for c in '´`^~¨'] if language=='zh_CN' else
                        [Key('dead:'+c,c,'dead',DEAD_KEYS[c]) for c in '´`^~¨'])
            keys.extend(row(accent_row+[text_key(c) for c in EXTRA_SYMBOLS.get(language, '$£¥ºª')],330,172,gap=10))
            third = '«»…–—×÷'
        else:
            # Every visible character is available through the raw US map.
            # Use the spare width for larger brackets/operators, not dead keys
            # or unsupported currencies that would silently fail in US mode.
            keys.extend(row([text_key(c) for c in '[]{}<>'],142,172,gap=10))
            keys.extend(row([text_key(c) for c in '\\|_`~*^'],330,172,gap=10))
            third = "'\":;?!$"
        keys.extend(row([page_key('numbers','123')]+[text_key(c) for c in third]+
                        [raw('',14,icon='backspace')],518,172,
                        weights=[1.3]+[1.]*7+[1.8],gap=10))
    else:
        # The freed multimedia area becomes LARGE F keys in two rows, not blanks.
        fcodes=list(range(59,69))+[87,88]
        keys.extend(row([raw('F'+str(i+1),c) for i,c in enumerate(fcodes[:6])],142,124))
        keys.extend(row([raw('F'+str(i+7),c) for i,c in enumerate(fcodes[6:])],282,124))
        keys.extend(row([raw('Esc',1),raw('Tab',15),modifier('shift','Shift'),
                         modifier('ctrl','Ctrl'),modifier('alt','Alt'),modifier('super','Super'),
                         modifier('altgr','AltGr'),raw('Insert',110),raw('Delete',111)],422,124))
        keys.extend(row([Key('caps','Caps\nLock','caps'),raw('Num\nLock',69),raw('Scroll\nLock',70),
                         raw('Home',102),raw('End',107),raw('Page\nUp',104),raw('Page\nDown',109)],562,128))
    if language == 'zh_CN' and page == 'abc':
        # Reserve a real candidate strip; drawing and hit testing share it.
        keys = [replace(k,rect=Rect(k.rect.x,220+(k.rect.y-142)*470/548,
                                   k.rect.w,k.rect.h*470/548))
                if k.rect.y>=142 else k for k in keys]
        keys += row([Key('ime:previous','‹','ime_page',-1)] +
                    [Key('ime:choice:'+str(i),'','candidate',i) for i in range(6)] +
                    [Key('ime:next','›','ime_page',1)],154,54,
                    weights=[.5]+[1.]*6+[.5],gap=8)
    left_width = 740. if view=='combined' else 1216.
    keys.extend(row([page_key('numbers','123') if page=='abc' else page_key('abc','ABC'),
                     text_key(',',ident='footer:comma'),
                     Key('footer:space','Espaço','text',' ',57),
                     text_key('.',ident='footer:period')],716,154,w=left_width,
                     weights=[1.1,.85,3.1,.85]))
    enter=replace(raw('Enter',28,'footer:enter',icon='enter'),accent=True)
    if page=='abc':
        keys.extend(row([enter,text_key('@',ident='footer:at'),text_key('/',ident='footer:slash')],
                        890,166,w=left_width,weights=[2.,1.1,1.1]))
    else:
        keys.extend(row([enter,raw('',105,icon='left'),raw('',103,icon='up'),
                         raw('',108,icon='down'),raw('',106,icon='right')],
                        890,166,w=left_width,weights=[2.,.53,.53,.53,.53]))
    keys = [replace(k, label=tr(k.label, ui_language)) if k.action != 'text' else k
            for k in keys]
    # Space is a text event but its legend is interface text, not the typed value.
    keys = [replace(k, label=tr(k.label, ui_language)) if k.ident=='footer:space' else k
            for k in keys]
    if language == 'en_US':
        keys = [replace(k, code=US_MAP[k.value][0]) if k.action=='text' and k.value in US_MAP else k
                for k in keys]
    return Layout(tuple(keys),Rect(764,716,464,340) if view=='combined' else None,page,view)


def settings_keys(section, ui_language='pt_PT'):
    keys=row([Key('settings:language','Idiomas','settings_tab','language'),Key('settings:keyboard','Teclado','settings_tab','keyboard'),
              Key('settings:mouse','Rato','settings_tab','mouse'),
              Key('settings:ayn','Botão AYN','settings_tab','ayn'),
              Key('settings:about','Sobre e créditos','settings_tab','about')],202,72,x=24,w=1192)
    if section in SETTING_ROWS:
        for index,(name,label) in enumerate(SETTING_ROWS[section]):
            y=304+index*102
            if name in RANGES:
                keys.extend([Key('setting:'+name+':minus','−','setting',(name,-1),rect=Rect(824,y,92,76)),
                             Key('setting:'+name+':plus','+','setting',(name,1),rect=Rect(1124,y,92,76))])
            else:
                keys.append(Key('setting:'+name,'','setting',(name,0),rect=Rect(824,y,392,76)))
    keys.append(Key('settings:back','Voltar ao teclado','settings_back',rect=Rect(24,964,572,92),accent=True))
    keys.append(Key('settings:release','Libertar entradas','release',rect=Rect(620,964,596,92)))
    return [replace(k, label=tr(k.label, ui_language)) for k in keys]


def menu_keys(ui_language='pt_PT'):
    labels=(('combined','Teclado + touchpad'),('keyboard','Só teclado'),('pad','Só touchpad'),
            ('retry','Reativar escrita'),('release','Libertar teclas e rato'))
    return tuple(Key('menu:'+value,tr(label, ui_language),'menu',value,rect=Rect(610,122+i*86,606,78))
                 for i,(value,label) in enumerate(labels))


def popup_geometry(key: Key, variants: tuple[str,...]):
    count=len(variants)
    w=min(WIDTH-24, count*112+24+(count-1)*10)
    h=126.
    x=max(12.,min(key.rect.center[0]-w/2, WIDTH-w-12))
    y=key.rect.y-h-12
    if y<118: y=key.rect.y+key.rect.h+12
    y=min(HEIGHT-h-12,y)
    outer=Rect(x,y,w,h)
    options=tuple(row([Key('accent:'+c,c,'literal',c) for c in variants],y+12,h-24,x+12,w-24,gap=10))
    return outer,options


def normalize_settings(values):
    import math
    result=dict(DEFAULTS)
    if isinstance(values,dict):
        for key,default in DEFAULTS.items():
            value=values.get(key,default)
            if key in ('language','ui_language'): result[key]=value if value in (LANGUAGES + (('auto',) if key=='ui_language' else ())) else default
            elif key=='view': result[key]=value if value in VIEWS else default
            elif isinstance(default,bool): result[key]=value if isinstance(value,bool) else default
            else:
                low,high,step,_=RANGES[key]
                try:
                    v=float(value)
                    if isinstance(value,bool) or not math.isfinite(v): raise ValueError
                    result[key]=int(round(max(low,min(high,v)))) if step>=1 else round(max(low,min(high,v)),4)
                except (TypeError,ValueError,OverflowError): result[key]=default
    return result


def text_for_key(key: Key, language: str, shift_state: int) -> str:
    """Single source of truth for typed character and displayed key legend.

    Only immutable drawing copies are relabeled. Codes, hit rectangles and
    saved preferences are untouched. In US, momentary Shift also follows the
    normal US punctuation map; Caps Lock does not alter punctuation.
    """
    value = key.value
    if key.action != 'text' or not isinstance(value, str) or len(value) != 1:
        return value
    if value.isalpha() and value not in 'ºª':
        return upper(value) if shift_state else value.lower()
    if language == 'en_US' and shift_state == 1:
        return US_SHIFT.get(value, value)
    return value


def language_picker(setting, ui_language):
    values = (('auto',) if setting == 'ui_language' else ()) + LANGUAGES
    result=[]
    for i,value in enumerate(values):
        label=tr('Seguir o teclado',ui_language) if value=='auto' else LANGUAGE_NAMES[value]
        result.append(Key('choose:'+value,label,'choose_language',(setting,value),
                          rect=Rect(100,190+i*91,1040,80)))
    return tuple(result)
