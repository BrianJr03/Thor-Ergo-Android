#!/usr/bin/env python3
"""Reproduce the renamed reverse index from the included, unmodified source.

Data-derived output remains LPPL-1.3c-or-later. This build program is MIT.
No network, learning, personal word list or external Python package is used.
Run from anywhere: python3 tools/pinyin/build_pinyin_index.py
"""
from pathlib import Path
import hashlib
import json
import re
import unicodedata

def build(root=None):
    root=Path(root) if root else Path(__file__).resolve().parents[2]
    source=root/'licenses/xpinyin/xpinyin-database.def'
    preferred=json.loads((root/'tools/pinyin/pinyin-preferences.json').read_text(encoding='utf-8'))
    data=root/'core/pinyin/src/main/resources/pinyin'
    mapping={}
    for char,readings in re.findall(r'\\XPYUM?\{([^{}]+)\}\{\d+\}\{([^{}]+)\}',source.read_text(encoding='utf-8')):
        for reading in readings.split(','):
            decomposed=unicodedata.normalize('NFD',reading.lower()).replace('u\u0308','v')
            key=''.join(c for c in decomposed if not unicodedata.combining(c))
            if re.fullmatch('[a-zv]+',key) and len(char)==1:mapping.setdefault(key,set()).add(char)
    for key,char in preferred.items():
        if char not in mapping.get(key,set()):raise ValueError('A preferred reading is not in the source data: '+key)
    result={key:''.join(sorted(chars,key=lambda c:(c!=preferred.get(key),not ('\u4e00'<=c<='\u9fff'),ord(c))))
            for key,chars in sorted(mapping.items())}
    (data/'pinyin-index.json').write_text(json.dumps(result,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n',encoding='utf-8')
    phrases=json.loads((data/'pinyin-phrases.json').read_text(encoding='utf-8'))
    provenance={'schema':1,'source':'xpinyin 3.1 (2022-07-14), derived from Unihan 14.0.0',
                'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                'license':'LPPL-1.3c-or-later','modifications':'Renamed reverse Pinyin index; no tones; v for ü; editorial preferred candidates, not measured frequencies. Separate hand-entered phrase list.',
                'syllables':len(result),'character_reading_pairs':sum(map(len,result.values())),
                'common_phrases':len(phrases),'preferred_syllables':len(preferred)}
    (data/'pinyin-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return provenance
if __name__=='__main__':print(json.dumps(build(),ensure_ascii=False,indent=2))
