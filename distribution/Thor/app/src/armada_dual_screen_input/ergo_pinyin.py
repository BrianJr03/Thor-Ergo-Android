"""Small, offline Pinyin composer with explicit candidates and focus guards.

This is deterministic syllable/curated-phrase conversion, not predictive text,
contextual sentence conversion, user learning, or a libpinyin replacement.
The dictionaries are read-only. Composition exists only in this process.
"""
from __future__ import annotations
import json
from functools import lru_cache
from pathlib import Path

@lru_cache(maxsize=1)
def dictionaries():
    root=Path(__file__).resolve().parents[2]/'data'
    return (json.loads((root/'pinyin-index.json').read_text(encoding='utf-8')),
            json.loads((root/'pinyin-phrases.json').read_text(encoding='utf-8')))

class PinyinComposition:
    LIMIT=64
    def __init__(self,transport,index=None,phrases=None):
        self.transport=transport
        self.index,self.phrases=dictionaries() if index is None else (index,phrases or {})
        self.buffer='';self.target=None;self.revision=0;self.options=[]

    def reset(self):
        self.buffer='';self.target=None;self.options=[];self.revision+=1

    def _refresh(self):
        # Prefer full-buffer words, then one syllable, then longest valid prefix.
        # Apostrophes separate syllables explicitly (xi'an, for example).
        raw=self.buffer.lower()
        normalized=raw.replace("'",'')
        results=[];seen=set()
        def add(text,consume):
            if text and text not in seen:
                seen.add(text);results.append((text,consume))
        for text in self.phrases.get(normalized,[]):add(text,len(raw))
        if normalized in self.index and "'" not in raw:
            for text in self.index[normalized]:add(text,len(raw))
        if not results and raw:
            first=raw.split("'",1)[0]
            matched=False
            # A full syllable is a safer prefix than a phrase ending mid-syllable.
            for length in range(min(6,len(first)),0,-1):
                prefix=first[:length]
                if prefix in self.index:
                    consume=length+(1 if raw[length:length+1]=="'" else 0)
                    for text in self.phrases.get(prefix,[]):add(text,consume)
                    for text in self.index[prefix]:add(text,consume)
                    matched=True;break
            if not matched:
                results=[]
        self.options=results

    def state(self,status='ready',**extra):
        return {'status':status,'preedit':self.buffer,
                'candidates':[item[0] for item in self.options],
                'revision':self.revision,**extra}

    def _focus(self):
        if self.target is not None and not self.transport.matches(self.target):
            self.reset();return False
        if self.target is None:self.target=self.transport.focus()
        return True

    def _choose(self,index):
        if type(index) is not int or not 0<=index<len(self.options):
            raise ValueError('Invalid candidate')
        text,consume=self.options[index]
        self.transport.commit(text,self.target)
        self.buffer=self.buffer[consume:].lstrip("'")
        self.revision+=1;self._refresh()
        if not self.buffer:self.target=None

    def handle(self,request):
        kind=request.get('kind')
        if kind in ('reset','ergo_barrier'):
            if self.target is not None and not self.transport.matches(self.target):
                self.reset();return self.state('focus_changed')
            self.reset();return self.state()
        if kind not in ('key','ergo_literal','choose'):raise ValueError('Invalid Pinyin operation')
        if not self._focus():return self.state('focus_changed')
        if kind=='choose':
            if request.get('revision')!=self.revision:
                # A late tap must never select a different character.
                return self.state()
            self._choose(request.get('index'));return self.state()
        text=request.get('text','');code=request.get('code',0)
        if (type(code) is not int or not 0<=code<=767 or not isinstance(text,str)
                or len(text)>1 or (text and not text.isprintable())):
            raise ValueError('Invalid Pinyin input')
        if kind=='ergo_literal':
            if not text:raise ValueError('Empty literal')
            # Literal is explicitly requested; pending conversion is cancelled.
            self.buffer='';self.options=[]
            self.transport.commit(text,self.target);self.reset();return self.state()
        if text and (text.isascii() and text.isalpha() or text=="'" and self.buffer):
            if len(self.buffer)<self.LIMIT:
                self.buffer+=text;self.revision+=1;self._refresh()
            return self.state()
        if text==' ' and self.buffer:
            if self.options:self._choose(0)
            else:self.transport.commit(self.buffer,self.target);self.reset()
            return self.state()
        if code==14 and self.buffer:
            self.buffer=self.buffer[:-1];self.revision+=1;self._refresh()
            if not self.buffer:self.target=None
            return self.state()
        if code==1 and self.buffer:
            self.reset();return self.state()
        if code==28 and self.buffer:
            # Enter accepts literal Latin spelling; another Enter sends Return.
            self.transport.commit(self.buffer,self.target);self.reset();return self.state()
        if text:
            # A symbol is unambiguous, but do not silently choose a Han candidate.
            # Preserve uncommitted Latin spelling before the symbol.
            self.transport.commit(self.buffer+text,self.target);self.reset();return self.state()
        target=self.target
        self.reset()
        actions={28:1,14:2,111:3,105:4,106:5}
        if code in actions:self.transport.action(actions[code],target)
        elif code in (1,15,103,108):return self.state(forward_key=code)
        return self.state()
