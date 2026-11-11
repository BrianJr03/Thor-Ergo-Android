"""Local PT-PT extension. No global keyboard changes, clipboard, or input logs.

The key table is the standard XKB 'pt'/'basic', not Brazilian ABNT2.
This module has no GUI dependencies and its composition tests use fake transport.
"""
from __future__ import annotations

import json
from pathlib import Path
import time
import unicodedata

KEYMAP = {int(k): v for k, v in json.loads(
    Path(__file__).with_name('ptpt_keymap.json').read_text(encoding='utf-8')
).items()}
# Spacing character, combining mark. The first five cover ordinary PT-PT use.
DEAD = {
    'dead_acute': ('´', '\u0301'), 'dead_grave': ('`', '\u0300'),
    'dead_tilde': ('~', '\u0303'), 'dead_circumflex': ('^', '\u0302'),
    'dead_diaeresis': ('¨', '\u0308'), 'dead_cedilla': ('¸', '\u0327'),
    'dead_ogonek': ('˛', '\u0328'), 'dead_abovering': ('˚', '\u030a'),
    'dead_macron': ('¯', '\u0304'), 'dead_breve': ('˘', '\u0306'),
    'dead_doubleacute': ('˝', '\u030b'), 'dead_caron': ('ˇ', '\u030c'),
    'dead_abovedot': ('˙', '\u0307'), 'dead_belowdot': ('\u0323', '\u0323'),
    'dead_hook': ('\u0309', '\u0309'), 'dead_horn': ('\u031b', '\u031b'),
}
ACTIONS = {28: 1, 14: 2, 105: 4, 106: 5}
FORWARD = {1, 15, 103, 108}


def key_value(code, *, shift=False, caps=False, altgr=False):
    values = KEYMAP.get(code)
    if values is None:
        return {}
    index = int(bool(shift)) | (int(bool(caps)) << 1) | (int(bool(altgr)) << 2)
    return dict(values[index])


def key_caption(value):
    if 'dead' in value:
        return DEAD[value['dead']][0]
    return value.get('text', '')


class PortugueseComposition:
    """Serialize accents and editing through the existing Gamescope adapter."""
    def __init__(self, transport):
        self.transport = transport
        self.dead = None
        self.target = None
        self.revision = 0

    def reset(self):
        self.dead = self.target = None
        self.revision += 1

    def state(self, status='ready', **extra):
        return {'status': status, 'preedit': DEAD[self.dead][0] if self.dead else '',
                'candidates': [], 'revision': self.revision, **extra}

    def handle(self, request):
        if request.get('kind') == 'reset':
            self.reset()
            return self.state()
        if request.get('kind') != 'key':
            raise ValueError('Unsupported PT-PT request')
        code = request.get('code')
        text, dead = request.get('text', ''), request.get('dead')
        if (type(code) is not int or not 0 <= code <= 767
                or not isinstance(text, str) or len(text) > 1
                or (dead is not None and dead not in DEAD)
                or (text and dead)):
            raise ValueError('Invalid PT-PT input')
        if self.target is not None and not self.transport.matches(self.target):
            self.reset()
            return self.state('focus_changed')
        target = self.target if self.target is not None else self.transport.focus()
        self.target = target
        if code in (14, 1) and self.dead:
            self.reset()   # Backspace/Escape cancels the uncommitted accent.
            return self.state()
        if dead:
            if self.dead == dead:
                self.transport.commit(DEAD[dead][0], target)
                self.reset()
            else:
                if self.dead:
                    self.transport.commit(DEAD[self.dead][0], target)
                self.dead = dead
        elif text and text.isprintable():
            result = text
            if self.dead:
                spacing, combining = DEAD[self.dead]
                if text == ' ':
                    result = spacing
                else:
                    composed = unicodedata.normalize('NFC', text + combining)
                    result = composed if len(composed) == 1 else spacing + text
            self.transport.commit(result, target)
            self.reset()
        elif code in ACTIONS:
            self.reset()
            self.transport.action(ACTIONS[code], target)
        elif code in FORWARD:
            self.reset()
            return self.state(forward_key=code)
        else:
            self.reset()
        if not self.dead:
            self.target = None
        self.revision += 1
        return self.state()


def extend_languages(base):
    """Keep all original English/Chinese behavior; add a third input language."""
    class PortugueseLanguages(base):
        pt_altgr = False

        def stop(self):
            self.pt_altgr = False
            super().stop()

        def reset(self):
            self.pt_altgr = False
            super().reset()

        def _start(self):
            if self.language != 'pt_PT':
                return super()._start()
            if self.closed:
                return
            if self.mode != 'game':
                self.message = 'PT-PT: disponível apenas no Game Mode. Usa EN neste modo.'
                return
            self.message = 'A preparar PT-PT…'
            generation = self._generation
            try:
                self.worker = self.worker_factory(
                    self.runtime, lambda result: self._response(generation, result), self.dispatch)
                if not self.worker.submit({'kind': 'init', 'language': 'pt_PT'}):
                    raise RuntimeError('Keyboard worker unavailable')
            except Exception:
                self.stop()
                self.message = 'PT-PT indisponível. Muda para EN; não foi enviado texto.'

        def key(self, code, *, shift=False, caps=False, shortcut=False):
            if self.language != 'pt_PT':
                return super().key(code, shift=shift, caps=caps, shortcut=shortcut)
            if shortcut or code not in KEYMAP and code not in ACTIONS and code not in FORWARD:
                return False  # Original physical-key route, including Ctrl+C/V.
            if not self.ready or self.worker is None:
                return True   # Never silently type an incorrect US character.
            value = key_value(code, shift=shift, caps=caps, altgr=self.pt_altgr)
            if not self.worker.submit({'kind': 'key', 'code': code, **value}):
                self.stop()
                self.message = 'PT-PT: fila indisponível. Muda para EN e volta a PT.'
                self.changed()
            return True

        def _response(self, generation, result):
            if self.language != 'pt_PT':
                return super()._response(generation, result)
            if self.closed or generation != self._generation:
                return
            status = result.get('status')
            if status == 'unavailable':
                self.stop()
                self.message = 'PT-PT indisponível neste destino. Usa EN ou reabre o painel.'
            else:
                self.ready = True
                self.preedit = result.get('preedit', '')
                self.candidates = []
                self.page = 0
                self.revision = result.get('revision', 0)
                self.message = {
                    'focus_changed': 'O destino mudou; o acento pendente foi cancelado.',
                    'expired': 'Entrada expirada; não foi repetida automaticamente.',
                }.get(status, 'Toca numa letra para acentuar; Espaço escreve só o acento.')
                code = result.get('forward_key')
                if (type(code) is int and code in FORWARD
                        and 0 <= time.monotonic() - result.get('received_at', 0) <= .2):
                    self.forward_key(code)
            self.changed()

    PortugueseLanguages.__name__ = 'KeyboardLanguages'
    return PortugueseLanguages
