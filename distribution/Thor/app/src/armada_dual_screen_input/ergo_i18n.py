"""Offline complete UI catalogs. No service calls or process-global locale changes."""
from __future__ import annotations
import json
from pathlib import Path
LANGUAGES = ('pt_PT','en_US','es_ES','fr_FR','de_DE','it_IT','zh_CN')
LANGUAGE_NAMES = {'pt_PT':'Português (Portugal)','en_US':'English (US)',
 'es_ES':'Español (España)','fr_FR':'Français (France)',
 'de_DE':'Deutsch (Deutschland)','it_IT':'Italiano (Italia)','zh_CN':'简体中文 (拼音)'}
CATALOGS = {lang:json.loads((Path(__file__).with_name('locales')/(lang+'.json')).read_text(encoding='utf-8'))
            for lang in LANGUAGES}
EN = CATALOGS['en_US']
def effective_ui(settings):
    selected = settings.get('ui_language','auto')
    return settings.get('language','en_US') if selected == 'auto' else selected

def tr(text,language='en_US'):
    return CATALOGS.get(language,CATALOGS['en_US']).get(text,text)
