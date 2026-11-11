"""Seven-language layout, labels, interaction and translation regressions."""
import string
import unittest
from string import Formatter
from unittest.mock import patch
import cairo
from armada_dual_screen_input.ergo_i18n import LANGUAGES,CATALOGS,effective_ui,LANGUAGE_NAMES
from armada_dual_screen_input.ergo_profiles import PROFILES,accents,upper
from armada_dual_screen_input.ergo_layout import build,PAGES,VIEWS,SETTING_ROWS,language_picker,text_for_key,normalize_settings
from armada_dual_screen_input import ergo_render
from tests.helpers import controller,tap

class InternationalTests(unittest.TestCase):
    def test_seven_complete_catalogs(self):
        self.assertEqual(len(LANGUAGES),7)
        for language,cat in CATALOGS.items():
            self.assertEqual(set(cat),set(CATALOGS['en_US']))
            for key,value in cat.items():
                self.assertTrue(isinstance(value,str) and value.strip(),(language,key))
                fields=lambda s:{name for _,name,_,_ in Formatter().parse(s) if name is not None}
                self.assertEqual(fields(key),fields(value),(language,key))
    def test_auto_interface_follows_keyboard(self):
        for lang in LANGUAGES:self.assertEqual(effective_ui({'ui_language':'auto','language':lang}),lang)
    def test_interface_can_be_independent(self):
        for lang in LANGUAGES:self.assertEqual(effective_ui({'ui_language':'pt_PT','language':lang}),'pt_PT')
    def test_credit_in_all_languages(self):
        for cat in CATALOGS.values():
            self.assertIn('seekingalphacrawler',cat['Design gráfico e disposição do teclado: seekingalphacrawler'])
            self.assertIn('sa1utyeggs',cat['Base: Armada Dual Screen Input — sa1utyeggs e colaboradores'])
            self.assertIn('venatrix-ritz',cat['Entrada: Ratatoskr — Ven / venatrix-ritz e colaboradores'])
    def test_default_is_english_with_safe_ayn(self):
        s=normalize_settings({});self.assertEqual(s['language'],'en_US');self.assertEqual(s['ui_language'],'auto')
        self.assertFalse(s['ayn_toggle_enabled'])
    def test_invalid_boolean_cannot_enable_button(self):
        for v in ('true',1,[],{},None):self.assertFalse(normalize_settings({'ayn_toggle_enabled':v})['ayn_toggle_enabled'])
    def test_all_picker_languages_fit(self):
        for name in ('language','ui_language'):
            keys=language_picker(name,'en_US')
            self.assertEqual(len(keys),7+(name=='ui_language'))
            for k in keys:self.assertLessEqual(k.rect.y+k.rect.h,936)
    def test_settings_picker_changes_language(self):
        for lang in LANGUAGES:
            c,_,_,_,changed=controller();tap(c,'header:settings');tap(c,'setting:language')
            self.assertEqual(c.picker,'language')
            k=next(k for k in language_picker('language',c.ui_language) if k.value[1]==lang)
            c.down(20,*k.rect.center);c.up(20)
            self.assertEqual(c.settings['language'],lang);self.assertIsNone(c.picker)
            self.assertIn(('language',lang),changed)
    def test_picker_rejects_failed_save(self):
        c,*_=controller();c.setting_changed=lambda *a:False
        tap(c,'header:settings');tap(c,'setting:language')
        k=next(k for k in language_picker('language',c.ui_language) if k.value[1]=='de_DE')
        c.down(20,*k.rect.center);c.up(20);self.assertEqual(c.settings['language'],'pt_PT')
    def test_ayn_setting_only_sends_setting(self):
        c,_,events,_,changes=controller();tap(c,'header:settings');tap(c,'settings:ayn');tap(c,'setting:ayn_toggle_enabled')
        self.assertFalse(events);self.assertTrue(c.settings['ayn_toggle_enabled']);self.assertIn(('ayn_toggle_enabled',True),changes)
    def test_german_sharp_s_is_single_capital(self):
        self.assertEqual(upper('ß'),'ẞ')
    def test_native_letters_on_their_main_layout(self):
        for lang,chars in {'pt_PT':'ç','es_ES':'ñ','de_DE':'üöäß','fr_FR':'ç','it_IT':'ò','zh_CN':'。'}.items():
            vals={k.value for k in build(language=lang).keys if k.action=='text'}
            for char in chars:self.assertIn(char,vals)
    def test_french_azerty(self):self.assertEqual(PROFILES['fr_FR'][0],'azertyuiop')
    def test_german_qwertz(self):self.assertTrue(PROFILES['de_DE'][0].startswith('qwertz'))
    def test_spanish_inverted_punctuation(self):
        vals={k.value for p in PAGES for k in build(p,language='es_ES').keys if k.action=='text'}
        self.assertTrue({'¿','¡','ñ'}<=vals)
    def test_french_accents_accessible(self):
        self.assertTrue(set('éèêë')<=set(accents('fr_FR','e')))
        self.assertTrue(set('àâæ')<=set(accents('fr_FR','a')))
    def test_german_long_press_sharp_s(self):
        self.assertIn('ß',accents('de_DE','s'))
    def test_no_accents_popup_in_us_or_pinyin(self):
        for lang in ('en_US','zh_CN'):
            c,clock,*_=controller({'language':lang});k=c.layout.find('char:a');c.down(1,*k.rect.center);clock.advance(.6)
            self.assertIsNone(c.popup)
    def test_candidates_have_nonoverlapping_keys(self):
        lay=build(language='zh_CN');candidates=[k for k in lay.keys if k.action=='candidate']
        self.assertEqual(len(candidates),6)
        self.assertLess(max(k.rect.y+k.rect.h for k in candidates),min(k.rect.y for k in lay.keys if k.ident=='char:q'))
    def test_candidate_selection_contains_revision(self):
        c,_,events,*_=controller({'language':'zh_CN'});c.set_ime_state('ni',['你','尼'],9)
        tap(c,'ime:choice:1');self.assertEqual(events[-1],{'kind':'choose','index':1,'revision':9})
    def test_empty_candidate_is_noop(self):
        c,_,events,*_=controller({'language':'zh_CN'});tap(c,'ime:choice:5');self.assertFalse(events)
    def test_candidate_page_clamped(self):
        c,*_=controller({'language':'zh_CN'});c.set_ime_state('ni',list('你尼泥拟逆匿霓昵倪呢'),3)
        for _ in range(10):tap(c,'ime:next')
        self.assertEqual(c.ime_page,1)
        c.set_ime_state('nihao',['你好'],4);self.assertEqual(c.ime_page,0)

# Each parameter is separately named so an actual failing profile is visible.
def geometry_test(lang):
    def test(self):
        for page in PAGES:
            for view in VIEWS:
                lay=build(page,view,lang,ui_language=lang)
                rects=[k.rect for k in lay.keys]+([lay.pad] if lay.pad else [])
                for i,r in enumerate(rects):
                    self.assertGreaterEqual(min(r.x,r.y,r.w,r.h),0)
                    self.assertLessEqual(r.x+r.w,1240.000001);self.assertLessEqual(r.y+r.h,1080.000001)
                    for other in rects[i+1:]:self.assertFalse(r.overlaps(other),(lang,page,r,other))
                if view!='pad':self.assertEqual(sum(k.code in (103,105,106,108) for k in lay.keys),0 if page=='abc' else 4)
    return test

def labels_test(lang):
    def test(self):
        c,_,events,*_=controller({'language':lang,'ui_language':'auto'})
        for state in (0,1,2):
            c.modifiers['shift']=state
            for key in c.layout.keys:
                if key.action=='text' and key.value.isalpha():
                    self.assertEqual(text_for_key(key,lang,state),upper(key.value) if state else key.value.lower())
        for letter in PROFILES[lang][0]+PROFILES[lang][1]+PROFILES[lang][2]:
            c.modifiers['shift']=0;tap(c,'char:'+letter)
            self.assertEqual(events[-1]['text'],letter.lower())
    return test

def render_test(lang):
    def test(self):
        c,*_=controller({'language':lang,'ui_language':'auto'})
        def paint():
            surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,620,540)
            cr=cairo.Context(surface);cr.scale(.5,.5);ergo_render.draw(cr,c)
            self.assertTrue(any(surface.get_data()));surface.finish()
        for page in PAGES:c.page=page;c.rebuild();paint()
        for tab in (*SETTING_ROWS,'about'):c.settings_tab=tab;c.rebuild();paint()
        c.picker='ui_language';paint()
    return test

for _lang in LANGUAGES:
    setattr(InternationalTests,'test_geometry_'+_lang,geometry_test(_lang))
    setattr(InternationalTests,'test_letters_'+_lang,labels_test(_lang))
    setattr(InternationalTests,'test_render_'+_lang,render_test(_lang))
