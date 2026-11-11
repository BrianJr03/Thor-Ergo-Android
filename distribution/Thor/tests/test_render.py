import unittest
import cairo
from tests.helpers import controller
from armada_dual_screen_input.ergo_render import draw

class RenderTests(unittest.TestCase):
    def test_pages_settings_views_and_popup_offscreen(self):
        c,clock,*_=controller()
        for view in ('combined','keyboard','pad'):
            for page in ('abc','numbers','symbols','fn'):
                for w,h in ((1240,1080),(1240,1024),(620,510)):
                    with self.subTest(view=view,page=page,w=w,h=h):
                        c.page=page;c.view=view;c.rebuild()
                        surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,w,h)
                        cr=cairo.Context(surface);cr.scale(w/1240,h/1080);draw(cr,c)
                        self.assertTrue(any(surface.get_data()));surface.finish()
        c.view='combined';c.page='abc'
        for section in ('keyboard','mouse','about'):
            c.settings_tab=section;c.rebuild()
            surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,1240,1080);draw(cairo.Context(surface),c);surface.finish()
        c.settings_tab=None;c.rebuild();k=c.layout.find('char:a');c.down(1,*k.rect.center);clock.advance(.41)
        self.assertIsNotNone(c.popup)
        surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,1240,1080);draw(cairo.Context(surface),c);surface.finish()
    def test_no_plasma_bar_is_painted(self):
        c,*_=controller();labels=[]
        def label(cr,text,*a,**kw):labels.append(str(text))
        surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,1240,1080)
        draw(cairo.Context(surface),c,label);surface.finish()
        self.assertFalse(any('16:42' in x or '94%' in x or 'Thor' in x for x in labels))
