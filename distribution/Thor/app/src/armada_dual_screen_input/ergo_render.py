"""Graphite/blue Cairo UI, preserving the app's original font renderer.

No raster keyboard artwork: every key and popup is drawn from the SAME geometry
used for touch hit-testing. The actual Plasma status bar is not drawn here.
"""
from __future__ import annotations
import math
import cairo
from dataclasses import replace
from .ergo_i18n import tr, LANGUAGE_NAMES
from .ergo_layout import (WIDTH,HEIGHT,Rect,Key,RANGES,SETTING_ROWS,VERSION,menu_keys,text_for_key)

def rgb(hexvalue):
    return tuple(int(hexvalue[i:i+2],16)/255. for i in (1,3,5))
BG=rgb('#171A21');PANEL=rgb('#1B2029');SURFACE=rgb('#202630')
KEY=rgb('#343B46');KEY_BOTTOM=rgb('#292F39');BORDER=rgb('#556170')
FG=rgb('#F1F3F5');MUTED=rgb('#9BA7B7');BLUE=rgb('#285F85')
BLUE_BOTTOM=rgb('#174665');PRESSED=rgb('#1A9FFF');ERROR=rgb('#E4A0A6')


def path(cr,r,radius=20):
    x,y,w,h=r.x,r.y,r.w,r.h
    radius=min(radius,w/2,h/2)
    cr.new_sub_path()
    cr.arc(x+w-radius,y+radius,radius,-math.pi/2,0)
    cr.arc(x+w-radius,y+h-radius,radius,0,math.pi/2)
    cr.arc(x+radius,y+h-radius,radius,math.pi/2,math.pi)
    cr.arc(x+radius,y+radius,radius,math.pi,3*math.pi/2)
    cr.close_path()


def box(cr,r,top=KEY,bottom=KEY_BOTTOM,border=BORDER,radius=20,width=1.8):
    path(cr,r,radius)
    gradient=cairo.LinearGradient(r.x,r.y,r.x+r.w*.22,r.y+r.h)
    gradient.add_color_stop_rgb(0,*top)
    gradient.add_color_stop_rgb(1,*bottom)
    cr.set_source(gradient);cr.fill_preserve()
    cr.set_source_rgb(*border);cr.set_line_width(width);cr.stroke()


def stroke(cr,points,width=5,color=FG,close=False):
    cr.set_source_rgb(*color);cr.set_line_width(width)
    cr.set_line_cap(cairo.LINE_CAP_ROUND);cr.set_line_join(cairo.LINE_JOIN_ROUND)
    cr.move_to(*points[0])
    for p in points[1:]:cr.line_to(*p)
    if close:cr.close_path()
    cr.stroke()


def icon(cr,name,x,y,size,color=FG):
    cr.save();cr.translate(x,y);s=size/48.;cr.scale(s,s)
    cr.set_source_rgb(*color)
    if name in ('left','right','up','down'):
        angle={'left':0,'right':math.pi,'up':math.pi/2,'down':-math.pi/2}[name]
        cr.rotate(angle)
        stroke(cr,[(18,0),(-18,0),(-4,-14)],5,color)
        stroke(cr,[(-18,0),(-4,14)],5,color)
    elif name=='enter':
        stroke(cr,[(20,-18),(20,9),(-20,9),(-7,-4)],5,color)
        stroke(cr,[(-20,9),(-7,22)],5,color)
    elif name=='backspace':
        stroke(cr,[(-26,0),(-11,-17),(25,-17),(25,17),(-11,17)],4.5,color,True)
        stroke(cr,[(-3,-7),(11,7)],4.5,color);stroke(cr,[(-3,7),(11,-7)],4.5,color)
    elif name=='shift':
        stroke(cr,[(0,-22),(-22,0),(-10,0),(-10,22),(10,22),(10,0),(22,0)],4.2,color,True)
    elif name=='gear':
        cr.new_path()
        # Eight teeth with smoothly readable notches, hole cut out by even-odd.
        for i in range(64):
            angle=2*math.pi*i/64
            radius=24 if i%8 in (1,2,3,4) else 18
            point=(math.cos(angle)*radius,math.sin(angle)*radius)
            if i==0:cr.move_to(*point)
            else:cr.line_to(*point)
        cr.close_path();cr.new_sub_path();cr.arc(0,0,9,0,2*math.pi)
        cr.set_fill_rule(cairo.FILL_RULE_EVEN_ODD);cr.fill()
        cr.set_fill_rule(cairo.FILL_RULE_WINDING)
    elif name.startswith('mouse'):
        path(cr,Rect(-15,-24,30,48),14);cr.set_line_width(3);cr.stroke()
        stroke(cr,[(0,-23),(0,-1)],2.6,color)
        stroke(cr,[(-14,0),(14,0)],2.6,color)
        # Mark the appropriate mouse half, not two identical left-click icons.
        cr.set_source_rgb(*color)
        cr.rectangle(-11 if name=='mouse_left' else 3,-17,8,13);cr.fill()
    elif name=='dropdown':
        cr.move_to(-8,-4);cr.line_to(8,-4);cr.line_to(0,5);cr.close_path();cr.fill()
    cr.restore()


def toy_label(cr,text,x,y,size,*,bold=False,max_width=None,centered=False):
    """Offscreen fallback for tests only; runtime uses upstream Pango/Noto."""
    cr.select_font_face('Noto Sans',cairo.FONT_SLANT_NORMAL,
                        cairo.FONT_WEIGHT_BOLD if bold else cairo.FONT_WEIGHT_NORMAL)
    cr.set_font_size(size)
    ext=cr.text_extents(str(text))
    if max_width and ext.width>max_width:
        cr.set_font_size(size*max_width/ext.width);ext=cr.text_extents(str(text))
    cr.move_to(x-ext.x_bearing-ext.width/2 if centered else x-ext.x_bearing,
               y-ext.y_bearing-ext.height/2 if centered else y-ext.y_bearing)
    cr.show_text(str(text))


def label_lines(cr,label,text,x,y,size,max_width,color=FG):
    lines=str(text).split('\n')
    cr.set_source_rgb(*color)
    for i,line in enumerate(lines):
        label(cr,line,x,y+(i-(len(lines)-1)/2)*(size*1.2),size,bold=True,
              max_width=max_width,centered=True)


def draw_key(cr,key,label,*,pressed=False,active=False,locked=False,disabled=False):
    r=key.rect
    top,bottom,border=(PRESSED,BLUE,PRESSED) if pressed else ((BLUE,BLUE_BOTTOM,rgb('#4384AC'))
                       if key.accent or active else (KEY,KEY_BOTTOM,BORDER))
    box(cr,r,top,bottom,border,radius=min(22,r.h*.17))
    text_color=MUTED if disabled else FG
    cx,cy=r.center
    cr.set_source_rgb(*text_color)
    if key.action=='setting' and key.label in ('−','+'):
        stroke(cr,[(cx-12,cy),(cx+12,cy)],4,text_color)
        if key.label=='+':stroke(cr,[(cx,cy-12),(cx,cy+12)],4,text_color)
    elif key.icon.startswith('mouse'):
        icon(cr,key.icon,r.x+35,cy,38,text_color)
        label(cr,key.label,r.x+r.w*.61,cy,27,bold=True,max_width=r.w-64,centered=True)
    elif key.icon=='gear':icon(cr,'gear',cx,cy,53,text_color)
    elif key.icon=='enter':
        icon(cr,'enter',cx-r.w*.28,cy,52,text_color)
        label(cr,key.label,cx+r.w*.10,cy,min(47,r.w*.16),bold=True,max_width=r.w*.67,centered=True)
    elif key.icon=='dropdown':
        label(cr,key.label,cx-11,cy,29,bold=True,max_width=r.w-42,centered=True)
        icon(cr,'dropdown',r.x+r.w-22,cy,38,text_color)
    elif key.icon and not key.label:
        icon(cr,key.icon,cx,cy,min(60,r.w*.62),text_color)
    else:
        if key.ident.startswith('header:'):
            size=45 if key.label=='Fn' else 28
        elif key.ident.startswith(('settings:','setting:','menu:')):size=29
        elif key.ident=='footer:space':size=47
        elif len(key.label)==1:size=60
        elif key.label in ('ABC','123','Símb.','Sym.'):size=43
        elif key.label.startswith('F') and key.label[1:].isdigit():size=45
        elif '\n' in key.label:size=27
        else:size=30
        label_lines(cr,label,key.label,cx,cy,size,r.w-16,text_color)
    if locked:
        stroke(cr,[(r.x+16,r.y+r.h-10),(r.x+r.w-16,r.y+r.h-10)],3,PRESSED)


def settings_value(name,value,ui_language='pt_PT'):
    if name in ('language','ui_language'):
        return tr('Seguir o teclado',ui_language) if value=='auto' else LANGUAGE_NAMES.get(value,str(value))
    if isinstance(value,bool):return tr('Ligado' if value else 'Desligado',ui_language)
    if name in RANGES:
        _,_,step,unit=RANGES[name]
        number=str(int(value)) if step>=1 else f'{value:.2f}'
        if ui_language=='pt_PT':number=number.replace('.',',')
        return number+unit
    return str(value)


def wrapped(cr,label,text,rect,size=24,color=MUTED):
    maxchars=max(12,int(rect.w/(size*.54)))
    words=(list(str(text)) if any('\u4e00'<=c<='\u9fff' for c in str(text)) else str(text).split());lines=[];line=''
    joiner='' if any('\u4e00'<=c<='\u9fff' for c in str(text)) else ' '
    for word in words:
        if line and len(line)+1+len(word)>maxchars:lines.append(line);line=word
        else:line=(line+joiner+word).strip()
    if line:lines.append(line)
    cr.set_source_rgb(*color)
    for i,line in enumerate(lines):
        if (i+1)*size*1.35>rect.h:break
        label(cr,line,rect.x,rect.y+i*size*1.35,size,max_width=rect.w)


def draw(cr,controller,label=toy_label,*,message='',preedit='',mouse_held=()):
    cr.set_source_rgb(*BG);cr.paint()
    layout=controller.layout
    ui=controller.ui_language
    pressed=controller.pressed_ids()
    for key in layout.keys:
        active=(key.ident=='header:fn' and controller.page=='fn' and not controller.settings_tab
                or key.ident=='header:settings' and bool(controller.settings_tab)
                or key.action=='modifier' and controller.modifiers.get(key.value,0)>0
                or key.action=='caps' and controller.modifiers['shift']==2
                or key.action=='settings_tab' and key.value==controller.settings_tab)
        locked=bool(key.action=='modifier' and controller.modifiers.get(key.value)==2)
        # Render and typing share the same character resolver. Only a frozen
        # drawing copy changes; hit areas, action values and settings stay intact.
        if key.action=='candidate':
            index=controller.ime_page*6+key.value
            if index>=len(controller.ime_candidates):continue
            key=replace(key,label=str(controller.ime_candidates[index]))
        if key.action=='text' and key.ident!='footer:space':
            key=replace(key,label=text_for_key(key,controller.settings['language'],
                                              controller.modifiers['shift']))
        if key.action=='setting' and key.value[0] not in RANGES:
            key=replace(key,label=settings_value(key.value[0],controller.settings[key.value[0]],ui))
        draw_key(cr,key,label,pressed=key.ident in pressed or key.ident in mouse_held,active=active,
                 locked=locked,disabled=bool(controller.settings_tab and key.action=='mouse'))
    if layout.pad:
        pad=layout.pad
        box(cr,pad,top=rgb('#27313F'),bottom=SURFACE,border=rgb('#92A2B7'),radius=22,width=2.8)
        if preedit and controller.settings['language']!='zh_CN':
            wrapped(cr,label,tr('Acento pendente: {accent} · Espaço escreve o acento; Apagar cancela.',ui).format(accent=preedit),
                    Rect(pad.x+20,pad.y+20,pad.w-40,140),25,FG)
        if message:
            wrapped(cr,label,tr(message,ui),Rect(pad.x+20,pad.y+(145 if preedit else 20),pad.w-40,pad.h-40-(145 if preedit else 0)),24,
                    ERROR if ('indisponível' in message or 'cancelada' in message) else MUTED)
    elif message and not controller.settings_tab:
        # Bounded toast over the gap/header area; no relocation of keys.
        box(cr,Rect(20,112,1200,38),PANEL,PANEL,rgb('#454D59'),radius=10)
        cr.set_source_rgb(*MUTED)
        label(cr,tr(message,ui),620,131,22,max_width=1160,centered=True)
    if controller.settings_tab:
        cr.set_source_rgb(*FG)
        label(cr,tr('Definições',ui),24,143,34,bold=True,max_width=1192)
        section=controller.settings_tab
        if section in SETTING_ROWS:
            for i,(name,title) in enumerate(SETTING_ROWS[section]):
                y=304+i*102
                cr.set_source_rgb(*FG)
                label(cr,tr(title,ui),32,y+25,29,max_width=765)
                if name in RANGES:
                    label(cr,settings_value(name,controller.settings[name],ui),1020,y+38,28,
                          bold=True,max_width=180,centered=True)
            if section=='language':
                hints=('Os menus podem seguir o teclado ou ter uma língua diferente.',
                       'A escolha fica guardada nesta aplicação; não muda o idioma do sistema.',
                       'Os layouts não-US requerem Game Mode para escrita Unicode.',
                       'US envia teclas físicas; o destino deve ter disposição US.',
                       'Pinyin local: escreve sem tons e escolhe um candidato.',
                       'Sem previsão de frases nem aprendizagem do texto escrito.')
                for i,text in enumerate(hints):
                    wrapped(cr,label,tr(text,ui),Rect(32,535+i*62,1176,58),24)
            if section=='ayn':
                status={'off':'Desligado','starting':'A iniciar…','ready':'Pronto',
                        'screen_off':'Ecrã apagado','unavailable':'Indisponível'}
                cr.set_source_rgb(*FG)
                label(cr,tr('Estado AYN:',ui)+' '+tr(status.get(controller.ayn_status,'Indisponível'),ui),
                      32,432,30,bold=True,max_width=1170)
                hints=('Um clique apaga; outro volta a ligar. O ecrã superior não muda.',
                       'O botão fica reservado a esta função enquanto a opção estiver ativa.',
                       'Desativar repõe a função anterior e liberta o botão.',
                       'Steam no ecrã superior; Plasma Mobile no ecrã inferior.',
                       'Não altera controladores, permissões, brilho nem serviços do sistema.')
                for i,text in enumerate(hints):
                    wrapped(cr,label,tr(text,ui),Rect(32,510+i*67,1176,62),24)
                if controller.ayn_status=='unavailable':
                    from .ergo_ayn import reason_text
                    wrapped(cr,label,tr(reason_text(controller.ayn_reason),ui),
                            Rect(32,865,1176,62),23,ERROR)
                elif controller.ayn_backend=='inputplumber' and controller.ayn_status in ('ready','screen_off'):
                    wrapped(cr,label,tr('InputPlumber: encaminhamento do botão AYN ativo.',ui),
                            Rect(32,865,1176,62),23)
        else:
            info=('Thor Ergo '+VERSION,
                  'Design gráfico e disposição do teclado: seekingalphacrawler',
                  'Base: Armada Dual Screen Input — sa1utyeggs e colaboradores',
                  'Entrada: Ratatoskr — Ven / venatrix-ritz e colaboradores',
                  'Plataforma: equipa ArmadaOS; origens adicionais nos ficheiros de créditos.',
                  'Pinyin: dados xpinyin / Unihan; autores e licenças incluídos.',
                  'Distribuição independente; não é uma edição oficial dos projetos de base.',
                  'Licenças e código-fonte completos incluídos no pacote.',
                  'InputPlumber: ShadowBlip e colaboradores; integração por API.',
                  'PyYAML: Ingy döt Net, Kirill Simonov e colaboradores — MIT.')
            for i,text in enumerate(info):
                cr.set_source_rgb(*(FG if i in (0,1) else MUTED))
                label(cr,tr(text,ui),32,303+i*59,25 if i else 32,
                      bold=i in (0,1),max_width=1176)
        if controller.note:
            cr.set_source_rgb(*MUTED);label(cr,tr(controller.note,ui),32,925,23,max_width=1176)
    if controller.popup:
        popup=controller.popup
        box(cr,popup.rect,PANEL,SURFACE,rgb('#7CB5D9'),radius=23,width=3)
        for i,key in enumerate(popup.options):
            draw_key(cr,key,label,pressed=i==popup.selected)
    if controller.menu:
        cr.set_source_rgba(0,0,0,.45);cr.rectangle(0,112,WIDTH,HEIGHT-112);cr.fill()
        box(cr,Rect(598,110,630,454),PANEL,SURFACE,BORDER,20)
        for key in menu_keys(ui):
            draw_key(cr,key,label,active=key.value==controller.view)

    if (controller.settings['language']=='zh_CN' and controller.page=='abc'
            and not controller.settings_tab and controller.view!='pad'):
        cr.set_source_rgb(*MUTED)
        text=tr('Pinyin',ui)+': '+(controller.ime_preedit or tr('Pinyin local: escreve sem tons e escolhe um candidato.',ui))
        label(cr,text,18,115,23,max_width=1192)
    if controller.picker:
        from .ergo_layout import language_picker
        cr.set_source_rgba(0,0,0,.72);cr.paint()
        box(cr,Rect(76,122,1088,814),PANEL,SURFACE,BORDER,22)
        cr.set_source_rgb(*FG)
        label(cr,tr('Seleciona uma língua; a mudança é imediata.',ui),110,147,25,max_width=1020)
        for key in language_picker(controller.picker,ui):
            draw_key(cr,key,label,active=controller.settings[controller.picker]==key.value[1])
