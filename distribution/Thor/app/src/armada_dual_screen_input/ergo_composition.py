"""Small extension of the bundled Unicode text worker.

Barriers acknowledge all earlier text before physical shortcuts are emitted.
A literal accent chosen from a popup replaces, rather than doubles, a pending
accent. The upstream transport still verifies upper Gamescope focus.
"""
from __future__ import annotations
from .ptpt_input import PortugueseComposition as PreviousComposition

class PortugueseComposition(PreviousComposition):
    def handle(self,request):
        kind=request.get('kind')
        if kind=='ergo_literal':
            text=request.get('text')
            if not isinstance(text,str) or len(text)!=1 or not text.isprintable():
                raise ValueError('Invalid literal')
            if self.target is not None and not self.transport.matches(self.target):
                self.reset();return self.state('focus_changed')
            target=self.target if self.target is not None else self.transport.focus()
            self.transport.commit(text,target)
            self.reset()
            return self.state()
        if kind=='key' and request.get('code')==111 and not request.get('text') and not request.get('dead'):
            if self.target is not None and not self.transport.matches(self.target):
                self.reset();return self.state('focus_changed')
            target=self.target if self.target is not None else self.transport.focus()
            self.transport.action(3,target)
            self.reset()
            return self.state()
        if kind=='ergo_barrier':
            if self.target is not None and not self.transport.matches(self.target):
                self.reset();return self.state('focus_changed')
            self.reset()
            return self.state()
        return super().handle(request)
