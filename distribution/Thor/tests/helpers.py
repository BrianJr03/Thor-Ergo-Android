import heapq
from armada_dual_screen_input.ergo_controller import Controller

class Clock:
    def __init__(self):self.now=10.;self.next=0;self.jobs=[];self.cancelled=set()
    def time(self):return self.now
    def schedule(self,ms,callback):
        self.next+=1;heapq.heappush(self.jobs,(self.now+ms/1000.,self.next,callback));return self.next
    def cancel(self,token):self.cancelled.add(token)
    def advance(self,seconds):
        end=self.now+seconds
        while self.jobs and self.jobs[0][0]<=end:
            at,token,callback=heapq.heappop(self.jobs)
            self.now=at
            if token not in self.cancelled:callback()
        self.now=end

def controller(settings=None):
    clock=Clock();events=[];external=[];changes=[]
    c=Controller(emit=events.append,schedule=clock.schedule,cancel=clock.cancel,
                 changed=lambda:None,setting_changed=lambda *a:(changes.append(a) or True),
                 external=lambda *a:external.append(a),settings={'language':'pt_PT','ui_language':'pt_PT',**(settings or {})},clock=clock.time)
    return c,clock,events,external,changes

def tap(c,ident,tid=1):
    key=c.layout.find(ident);x,y=key.rect.center;c.down(tid,x,y);c.up(tid)
