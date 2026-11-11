#!/usr/bin/env python3
"""No-device, no-network tests. --native additionally tests real GTK and Pango.

No PanelApp, window, uinput bridge or Gamescope text connection is instantiated.
Only cairo.ImageSurface offscreen frames are drawn. The live GUI is started by
the installer later, after these checks succeed.
"""
from __future__ import annotations
import argparse
import importlib
import importlib.util
import os
from pathlib import Path
import sys
import unittest
sys.dont_write_bytecode=True
from native_runtime import ensure_native_startup
HERE=Path(__file__).resolve().parent

def native_frames():
    import gi
    gi.require_version('Gio','2.0')
    from gi.repository import Gio, GLib
    # Import validation only: do not connect to the system bus or change a profile.
    assert callable(Gio.bus_get_sync) and callable(GLib.Variant)
    import cairo
    from unittest.mock import patch
    from armada_dual_screen_input.ergo_render import draw
    from armada_dual_screen_input.ergo_controller import Controller,Popup
    from armada_dual_screen_input.ergo_layout import popup_geometry
    from armada_dual_screen_input.ergo_i18n import LANGUAGES
    from armada_dual_screen_input.ergo_profiles import accents
    original=os.open
    def safe_open(path,*a,**kw):
        p=os.fsdecode(path)
        if p.startswith('/dev/input') or p=='/dev/uinput':raise RuntimeError('Input device access during self-test')
        return original(path,*a,**kw)
    frames=0
    with patch('os.open',side_effect=safe_open):
        panel=importlib.import_module('armada_dual_screen_input.panel_app')
        if not getattr(panel.PanelApp,'_ergo_extension',False):raise RuntimeError('Ergo extension missing')
        style=importlib.import_module('armada_dual_screen_input.steam_style')
        def paint(c,w=1240,h=1080):
            nonlocal frames
            surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,w,h)
            cr=cairo.Context(surface);cr.scale(w/1240,h/1080)
            draw(cr,c,style.label);surface.flush();surface.finish();frames+=1
        for lang in LANGUAGES:
            c=Controller(emit=lambda e:None,schedule=lambda *a:None,cancel=lambda *a:None,
                         changed=lambda:None,setting_changed=lambda *a:True,external=lambda *a:None,
                         settings={'language':lang,'ui_language':'auto'})
            for page in ('abc','numbers','symbols','fn'):
                c.page=page;c.rebuild()
                for size in ((1240,1080),(1240,1024),(620,510)):paint(c,*size)
            for section in ('language','keyboard','mouse','ayn','about'):
                c.settings_tab=section;c.rebuild();paint(c)
            c.settings_tab='ayn';c.rebuild()
            c.ayn_status='unavailable';c.ayn_reason='ip_permission_denied';paint(c)
            c.ayn_status='ready';c.ayn_backend='inputplumber';paint(c)
            c.ayn_status='off';c.ayn_reason='';c.ayn_backend=''
            c.settings_tab='language';c.rebuild()
            c.picker='language';paint(c);c.picker='ui_language';paint(c);c.picker=None
            c.settings_tab=None;c.page='abc';c.rebuild()
            for state in (0,1,2):c.modifiers['shift']=state;paint(c)
            c.modifiers['shift']=0
            options=accents(lang,'a')
            if options:
                rect,keys=popup_geometry(c.layout.find('char:a'),options)
                c.popup=Popup(1,rect,keys,0);paint(c);c.popup=None
            if lang=='zh_CN':
                c.set_ime_state('nihao',['你好','你','呢','妳','泥','拟'],4);paint(c)
            c.menu=True;paint(c)
    print(f'Native GTK/Pango integration and {frames} offscreen frames: OK. No input sent.',flush=True)
    return frames

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--native',action='store_true')
    args=parser.parse_args()
    if args.native:ensure_native_startup(HERE/'app')
    sys.path.insert(0,str(HERE/'app/src'));sys.path.insert(0,str(HERE))
    os.environ['THOR_ERGO_TEST_APP']=str(HERE/'app')
    suite=unittest.defaultTestLoader.discover(str(HERE/'tests'),pattern='test_*.py')
    result=unittest.TextTestRunner(verbosity=1).run(suite)
    if not result.wasSuccessful():return 1
    if args.native:native_frames()
    print(f'Isolated tests passed: {result.testsRun}',flush=True)
    return 0
if __name__=='__main__':raise SystemExit(main())
