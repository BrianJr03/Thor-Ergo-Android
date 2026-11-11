"""FIFO text/physical-key output, bounded and cancellable; no input logging."""
from __future__ import annotations
from collections import deque
import time
from .ergo_layout import MOD_CODES, CODES
from .ptpt_input import key_value, ACTIONS, FORWARD

from .ergo_us import US_MAP

class SerialOutput:
    def __init__(self, *, language, available, submit, key, schedule, cancel,
                 error, abort, clock=time.monotonic):
        self.language, self.available, self.submit=language,available,submit
        self.key,self.schedule,self.cancel_timer=key,schedule,cancel
        self.error,self.abort,self.clock=error,abort,clock
        self.queue=deque()
        self.active=None
        self.timer=None
        self.sequence=0
        self.held=[]
        self.closed=False

    def send(self,event):
        if self.closed:return False
        if len(self.queue)>=48:
            self.fail('Fila de escrita cheia. A entrada pendente foi cancelada.');return False
        self.queue.append((dict(event),self.clock()+.75))
        self.pump()
        return True

    def reset(self, abort=False):
        self.queue.clear()
        self.sequence+=1  # invalidate late replies
        self.active=None
        if self.timer is not None:
            self.cancel_timer(self.timer);self.timer=None
        for code in reversed(self.held): self.key(code,False)
        self.held=[]
        if abort:self.abort()

    def close(self):
        self.reset(abort=True);self.closed=True

    def fail(self,message):
        self.reset(abort=True)
        self.error(message)

    def _next(self):
        self.active=None
        if self.timer is not None:
            self.cancel_timer(self.timer);self.timer=None
        self.pump()

    def _physical(self,code,mods=()):
        if type(code) is not int or not 1<=code<248:
            self.fail('Código de tecla não suportado.');return
        mods=tuple(dict.fromkeys(mods))
        if any(c not in MOD_CODES.values() for c in mods):
            self.fail('Modificador inválido.');return
        self.active=('physical',code,mods)
        try:
            for mod in mods:
                if mod!=code:self.key(mod,True);self.held.append(mod)
            self.key(code,True);self.held.append(code)
        except Exception:
            self.fail('Não foi possível enviar a tecla.');return
        def release():
            self.timer=None
            for held in reversed(self.held):self.key(held,False)
            self.held=[]
            self._next()
            return False
        self.timer=self.schedule(20,release)

    def _worker(self,request,after=None):
        if not self.available():
            self.fail('Escrita indisponível. Usa Modo → Reativar escrita.');return
        self.sequence+=1
        token=self.sequence
        request={**request,'ergo_token':token}
        self.active=('worker',token,after)
        def expire():
            self.timer=None
            if self.active and self.active[:2]==('worker',token):
                self.fail('A escrita não respondeu. Não foram repetidas entradas pendentes.')
            return False
        self.timer=self.schedule(1500,expire)
        if not self.submit(request):
            self.fail('Não foi possível enviar a entrada. Reativa a escrita no menu Modo.')

    def reply(self,result):
        if (not self.active or self.active[0]!='worker'
                or result.get('ergo_token')!=self.active[1]):return
        status=result.get('status')
        if status not in ('ready',):
            self.fail('O destino da escrita mudou ou ficou indisponível. A entrada pendente foi cancelada.');return
        after=self.active[2]
        if self.timer is not None:
            self.cancel_timer(self.timer);self.timer=None
        self.active=None
        if after:
            self._physical(*after)
        elif type(result.get('forward_key')) is int and result['forward_key'] in FORWARD:
            self._physical(result['forward_key'])
        else:self.pump()

    def pump(self):
        if self.closed or self.active or not self.queue:return
        event,expires=self.queue.popleft()
        if self.clock()>expires:
            self.fail('Entrada expirada: não foi enviada com atraso.');return
        kind=event.get('kind')
        language=self.language()
        names=tuple(event.get('mods',()))
        if any(name not in MOD_CODES for name in names):
            self.fail('Modificador inválido.');return
        mods=tuple(MOD_CODES[name] for name in names)
        shortcut=any(name in names for name in ('ctrl','alt','super'))
        code=event.get('code',0)
        text=event.get('text','')
        if kind=='text' and language=='pt_PT' and 'altgr' in names and not shortcut and code:
            value=key_value(code,shift=event.get('shift','shift' in names),caps=event.get('caps',False),altgr=True)
            if 'dead' in value:
                self._worker({'kind':'key','code':0,'dead':value['dead']});return
            text=value.get('text','')
            if not text:
                self.error('Esta tecla não tem símbolo AltGr.');self.pump();return
        if kind=='text' and language not in ('en_US','pt_PT') and 'altgr' in names and not shortcut:
            self.error('AltGr nesta língua: usa a página de símbolos.');self.pump();return
        if kind=='text' and shortcut:
            if event.get('caps'):mods=tuple(c for c in mods if c!=42)
            # Explicit US symbols such as @ already have a physical code in
            # the layout; their required Shift must still accompany shortcuts.
            # Caps Lock never injects Shift into Ctrl+C (or other letter chords).
            if language=='en_US' and text in US_MAP and not text.isalpha():
                code,extra=US_MAP[text];mods=tuple(dict.fromkeys(mods+extra))
            if not code:
                combo=US_MAP.get(text)
                if combo is None:
                    self.fail('Este símbolo não tem atalho físico direto.');return
                code,extra=combo;mods=tuple(dict.fromkeys(mods+extra))
            if language!='en_US' and self.available():
                self._worker({'kind':'ergo_barrier'},(code,mods))
            else:self._physical(code,mods)
        elif kind=='choose' and language=='zh_CN':
            self._worker({'kind':'choose','index':event.get('index'), 'revision':event.get('revision')})
        elif kind in ('text','literal'):
            if not isinstance(text,str) or len(text)!=1 or not text.isprintable():
                self.fail('Carácter inválido.');return
            if language=='en_US':
                combo=US_MAP.get(text)
                if combo is None:
                    self.error('Este carácter não está disponível no teclado US.');self.pump();return
                self._physical(*combo)
            else:
                self._worker({'kind':'ergo_literal','text':text} if kind=='literal'
                             else {'kind':'key','code':0,'text':text})
        elif kind=='dead':
            if language in ('en_US','zh_CN'):
                self.error('Este carácter não está disponível no teclado US.');self.pump();return
            self._worker({'kind':'key','code':0,'dead':event.get('dead')})
        elif kind=='key':
            if language!='en_US' and not mods and code in (set(ACTIONS)|FORWARD|{111}):
                self._worker({'kind':'key','code':code})
            elif language!='en_US' and self.available():
                self._worker({'kind':'ergo_barrier'},(code,mods))
            else:self._physical(code,mods)
        else:self.fail('Ação de escrita desconhecida.')
