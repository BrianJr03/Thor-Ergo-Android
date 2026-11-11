import unittest
from armada_dual_screen_input.ergo_layout import *

class LayoutTests(unittest.TestCase):
    def test_letter_rows_exact(self):
        keys=build().keys
        self.assertEqual(''.join(k.value for k in keys if k.rect.y==142),'qwertyuiop')
        self.assertEqual(''.join(k.value for k in keys if k.rect.y==330),'asdfghjklç')
        self.assertEqual(''.join(k.value for k in keys if k.rect.y==518 and k.action=='text'),'zxcvbnm')
    def test_enter_left_at_slash_same_row(self):
        lay=build();enter=lay.find('footer:enter');at=lay.find('footer:at');slash=lay.find('footer:slash')
        self.assertLess(enter.rect.x,at.rect.x);self.assertLess(at.rect.x,slash.rect.x)
        self.assertEqual(enter.rect.y,at.rect.y);self.assertEqual(at.rect.y,slash.rect.y)
        self.assertGreater(enter.rect.w,at.rect.w*1.6)
    def test_punctuation_row_unchanged(self):
        keys=[k for k in build().keys if k.rect.y==716]
        self.assertEqual([k.label for k in keys],['123',',','Espaço','.'])
    def test_arrows_only_alternatives(self):
        for p in PAGES:
            arrows=[k for k in build(p).keys if k.code in (105,103,108,106)]
            self.assertEqual(len(arrows),0 if p=='abc' else 4)
    def test_no_overlaps(self):
        for p in PAGES:
            for v in VIEWS:
                with self.subTest(page=p,view=v):
                    lay=build(p,v);rects=[k.rect for k in lay.keys]+([lay.pad] if lay.pad else [])
                    for i,r in enumerate(rects):
                        for s in rects[i+1:]:self.assertFalse(r.overlaps(s),(r,s))
    def test_bounds(self):
        for page in PAGES:
            for view in VIEWS:
                for r in [k.rect for k in build(page,view).keys]:
                    self.assertGreaterEqual(min(r.x,r.y,r.w,r.h),0)
                    self.assertLessEqual(r.x+r.w,WIDTH+1e-7);self.assertLessEqual(r.y+r.h,HEIGHT+1e-7)
    def test_all_centers_hit(self):
        for page in PAGES:
            lay=build(page)
            for key in lay.keys:self.assertEqual(lay.hit(*key.rect.center),key)
    def test_pad_constant(self):
        self.assertEqual(len(set(build(p).pad for p in PAGES)),1)
    def test_no_empty_fn_placeholders(self):
        for key in build('fn').keys:self.assertTrue(key.label or key.icon)
    def test_all_function_keys(self):
        labels=[k.label for k in build('fn').keys]
        for n in range(1,13):self.assertEqual(labels.count('F'+str(n)),1)
    def test_no_multimedia_keys(self):
        for page in PAGES:
            self.assertFalse({k.code for k in build(page).keys}&{113,114,115,224,225,227})
    def test_large_function_keys(self):
        for k in build('fn').keys:
            if k.label.startswith('F') and k.label[1:].isdigit():self.assertGreater(k.rect.w,180)
    def test_no_language_header(self):
        self.assertNotIn('PT-PT',[k.label for k in header()])
        self.assertGreater(build().find('header:fn').rect.w,175)
        self.assertGreater(build().find('header:settings').rect.w,125)
    def test_numbers_complete(self):
        self.assertEqual(''.join(k.value for k in build('numbers').keys if k.rect.y==142),'1234567890')
    def test_symbols_complete(self):
        values=[k.value for p in ('numbers','symbols') for k in build(p).keys if k.action=='text']
        for c in '@#€%&-+=()\'":;/?!\\|_*<>[]{}$£¥ºª«»…–—×÷':self.assertIn(c,values)
    def test_popup_in_bounds(self):
        for v in ACCENTS:
            key=build().find('char:'+v);rect,options=popup_geometry(key,ACCENTS[v])
            self.assertGreaterEqual(rect.x,0);self.assertGreaterEqual(rect.y,118)
            self.assertLessEqual(rect.x+rect.w,WIDTH);self.assertLessEqual(rect.y+rect.h,HEIGHT)
            for k in options:self.assertTrue(rect.contains(*k.rect.center))
    def test_settings_nonoverlap(self):
        for section in ('keyboard','mouse','about'):
            lay=build(settings=section)
            for i,k in enumerate(lay.keys):
                for k2 in lay.keys[i+1:]:self.assertFalse(k.rect.overlaps(k2.rect))
    def test_normalize_invalid_settings(self):
        s=normalize_settings({'language':'pt_BR','sensitivity':float('nan'),'view':'bad',
                              'long_press_ms':-1,'friction':1e8,'glide':'false'})
        self.assertEqual(s['language'],'en_US');self.assertEqual(s['sensitivity'],1.5)
        self.assertEqual(s['long_press_ms'],250);self.assertEqual(s['friction'],10)
        self.assertIs(s['glide'],True)
    def test_viewport_transform_roundtrip(self):
        # ToolkitTouch uses these exact ratios; no guessed Plasma bar subtraction.
        for w,h in ((1240,1080),(1240,1024),(620,510),(1024,900)):
            lay=build()
            for key in lay.keys:
                x,y=key.rect.center
                self.assertEqual(lay.hit((x*w/WIDTH)*WIDTH/w,(y*h/HEIGHT)*HEIGHT/h),key)
