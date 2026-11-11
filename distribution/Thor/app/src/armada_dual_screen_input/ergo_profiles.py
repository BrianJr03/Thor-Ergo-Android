"""National mobile layouts; Unicode text never changes the system keyboard map.

US retains the proven physical-event path. Other layouts commit their displayed
characters through Gamescope. Ctrl/Alt/Super use letter-based US physical codes.
"""
from __future__ import annotations
PROFILES = {
 'pt_PT': ('qwertyuiop','asdfghjklç','zxcvbnm'),
 'en_US': ('qwertyuiop','asdfghjkl;','zxcvbnm'),
 'es_ES': ('qwertyuiop','asdfghjklñ','zxcvbnm'),
 'fr_FR': ('azertyuiop','qsdfghjklm','wxcvbnç'),
 'de_DE': ('qwertzuiopü','asdfghjklöä','yxcvbnmß'),
 'it_IT': ('qwertyuiop','asdfghjklò','zxcvbnm'),
 'zh_CN': ('qwertyuiop','asdfghjkl。','zxcvbnm'),
}
ACCENTS_BY_LANGUAGE = {
 'pt_PT': {'a':('á','à','â','ã'),'e':('é','ê'),'i':('í',),'o':('ó','ô','õ'),'u':('ú','ü')},
 'en_US': {},
 'es_ES': {'a':('á',),'e':('é',),'i':('í',),'o':('ó',),'u':('ú','ü'),'n':('ñ',)},
 'fr_FR': {'a':('à','â','æ'),'c':('ç',),'e':('é','è','ê','ë'),'i':('î','ï'),'o':('ô','œ'),'u':('ù','û','ü'),'y':('ÿ',)},
 'de_DE': {'a':('ä',),'o':('ö',),'u':('ü',),'s':('ß',)},
 'it_IT': {'a':('à',),'e':('è','é'),'i':('ì',),'o':('ò','ó'),'u':('ù',)},
 'zh_CN': {},
}
EXTRA_SYMBOLS = {
 'pt_PT': '$£¥ºª', 'es_ES': '¿¡€ºª', 'fr_FR':'éèàùœ',
 'de_DE': 'äöüß€','it_IT':'àèìòù','zh_CN':'，。？！、',
}

def upper(text):
    # A single sharp-s key should not unexpectedly send two physical letters.
    return 'ẞ' if text == 'ß' else text.upper()

def accents(language, char):
    return ACCENTS_BY_LANGUAGE.get(language, {}).get(char, ())
