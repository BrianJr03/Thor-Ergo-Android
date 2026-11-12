#!/usr/bin/env python3
"""Build the English swipe-typing word list from the included, unmodified source.

Data-derived output remains CC-BY-SA-4.0 (FrequencyWords, OpenSubtitles 2018). This build program is MIT.
No network or external Python package is used. Run from anywhere: python3 tools/swipe/build_swipe_lexicon.py
"""
from pathlib import Path
import hashlib
import json
import re

LIMIT=20000
# Contraction fragments, apostrophe-less spellings and subtitle OCR errors (l for I) that are not words.
EXCLUDED={'don','didn','doesn','isn','wasn','aren','weren','couldn','wouldn','shouldn','haven','hasn','hadn','ain',
          'dont','didnt','doesnt','isnt','wasnt','arent','werent','couldnt','wouldnt','shouldnt','havent','hasnt','hadnt',
          'cant','wont','im','ive','youre','theyre','thats','whats','theres','hes','shes',
          'll','ve','re','lt','ls','lf','ln','lm','ld','lnto','lsn'}
SINGLE={'a','i'}

def build(root=None):
    root=Path(root) if root else Path(__file__).resolve().parents[2]
    source=root/'licenses/frequencywords/en_50k.txt'
    contractions={k:v for k,v in json.loads((root/'tools/swipe/swipe-contractions.json').read_text(encoding='utf-8')).items() if not k.startswith('_')}
    counts={}
    for line in source.read_text(encoding='utf-8').splitlines():
        word,count=line.split(' ')
        counts.setdefault(word,int(count))
    shares={}
    for token in contractions.values():
        if token not in counts:raise ValueError('A contraction source token is not in the data: '+token)
        shares[token]=shares.get(token,0)+1
    words={w:c for w,c in counts.items()
           if re.fullmatch('[a-z]+',w) and w not in EXCLUDED and (len(w)>1 or w in SINGLE)}
    words['I']=words.pop('i')
    # A suffix token ('t) also counts the contractions that have their own fragment (didn + 't); only the rest is shared.
    pool={t:counts[t] for t in shares}
    for word,token in contractions.items():
        if not token.startswith("'"):
            suffix="'"+word.split("'")[1]
            if suffix in pool:pool[suffix]-=counts[token]
    for word,token in contractions.items():words[word]=max(1,pool[token])//shares[token]
    ranked=sorted(words.items(),key=lambda e:(-e[1],e[0]))[:LIMIT]
    data=root/'core/swipe/src/main/resources/swipe'
    (data/'en_US.tsv').write_text(''.join(f'{w}\t{c}\n' for w,c in ranked),encoding='utf-8')
    provenance={'schema':1,'source':'FrequencyWords content/2018/en/en_50k.txt (OpenSubtitles 2018), commit 525f9b560de45753a5ea01069454e72e9aa541c6',
                'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
                'license':'CC-BY-SA-4.0','modifications':'Letters a-z only; contraction fragments, apostrophe-less spellings and OCR errors removed; '
                'hand-listed contractions added with counts from their source tokens; "i" written as "I"; top words by count.',
                'words':len(ranked),'contractions':len(contractions)}
    (data/'swipe-provenance.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return provenance
if __name__=='__main__':print(json.dumps(build(),ensure_ascii=False,indent=2))
