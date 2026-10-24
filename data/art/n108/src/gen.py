"""N108/N104/N103/N109 sprite sheet: every background piece of the approved designs (screens/team/N110/GOEDGEKEURD/bron/*.html),
drawn with the designs' own CSS by Chrome at 1x, then cut into one PNG per sprite + manifest.json (name -> x, y, w, h).
Text is NOT in the sprites (the game draws it with p11/p12/b12), except a few fixed labels (Pet, PvP, Danger, filter buttons).
Usage: python gen.py  (writes sheet.html), then render.cmd, then python cut.py."""
import json

CSS = r"""
@font-face{font-family:rs;src:url(runescape.ttf)}
@font-face{font-family:rsb;src:url(runescape_bold.ttf)}
@font-face{font-family:rss;src:url(runescape_small.ttf)}
:root{--orange:#ff981f;--gold:#ffcf4a;--yellow:#ffff00;--grey:#9a9083;--dim:#7a7064;--txt:#ece2cf;
  --stone:#3e3529;--well:#1f1a14;--line:#110d0a;--edge:#5a4d3c;--pet:#d38cff;--green:#56e156}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:transparent}
body{position:relative;font-family:rss;font-size:16px;color:#fff;width:1200px}
.t{text-shadow:1px 1px 0 #000;white-space:nowrap}
.sp{position:absolute;overflow:hidden}
.sp>*{position:absolute}
img{image-rendering:auto}
.win{left:0;top:0;width:100%;height:100%;background:var(--stone);border:1px solid #000;
  box-shadow:inset 0 0 0 1px #6b5e4b,inset 0 0 0 3px #2e271f,inset 0 0 0 4px #57493a;
  background-image:radial-gradient(rgba(255,255,255,.025) 1px,transparent 1px),radial-gradient(rgba(0,0,0,.08) 1px,transparent 1px);
  background-size:3px 3px,5px 5px;background-position:0 0,1px 2px}
.close{width:21px;height:19px;background:#3a3127;border:1px solid #000;box-shadow:inset 1px 1px 0 #6b5e4b,inset -1px -1px 0 #221c15;
  color:#c8bba6;font-family:rsb;text-align:center;line-height:17px}
.well{background:var(--well);border:1px solid var(--line);box-shadow:0 1px 0 var(--edge),inset 0 1px 0 #29221a;overflow:hidden}
.search{width:150px;height:22px;background:#12100c;border:1px solid #000;box-shadow:0 1px 0 var(--edge),inset 0 0 0 1px #2c251d}
.search.on{box-shadow:0 1px 0 var(--edge),inset 0 0 0 1px #8a7250}
.mag{position:absolute;left:5px;top:4px;width:9px;height:9px;border:2px solid #a89a82;border-radius:50%}
.mag:after{content:"";position:absolute;left:6px;top:7px;width:5px;height:2px;background:#a89a82;transform:rotate(45deg)}
.model{background:radial-gradient(ellipse 70% 62% at 50% 42%,#80684a 0,#4a3c2b 45%,#1d1812 100%)}
.model:before{content:"";position:absolute;left:12px;right:12px;bottom:6px;height:9px;border-radius:50%;background:radial-gradient(rgba(0,0,0,.7),transparent 70%)}
.mapbtn{height:18px;width:84px;background:linear-gradient(#56493a,#40362a);border:1px solid #000;box-shadow:inset 1px 1px 0 #7d6e57,inset -1px -1px 0 #261f18}
.mapbtn img{position:absolute;left:4px;top:2px;height:13px}
.brk{position:absolute;left:0;top:0;width:100%;height:20px;background:linear-gradient(90deg,#6a4c10 0,#46340f 55%,#2a2116 100%);border-bottom:1px solid #000;box-shadow:inset 0 1px 0 #8f6c22}
.brk img{position:absolute;left:6px;top:4px;height:12px}
.alw{position:absolute;left:0;top:20px;width:100%;height:18px;background:#191510;border-bottom:1px solid var(--line)}
.thead{position:absolute;left:0;top:38px;width:100%;height:15px;border-bottom:1px solid #3d3326}
.grp{height:17px;background:linear-gradient(#32291f,#251e16);border-bottom:1px solid var(--line);border-top:1px solid #40362a}
.lisel{background:linear-gradient(90deg,#66512f,#3f321f)}
.lisel:before{content:"";position:absolute;left:0;top:0;bottom:0;width:2px;background:var(--gold)}
.th{border:1px solid #0b0907;border-radius:2px;background:radial-gradient(circle at 50% 40%,#53442f,#211b14 75%);box-shadow:inset 0 0 0 1px #3d3326}
.aro{width:0;height:0;border-left:4px solid transparent;border-right:4px solid transparent;border-top:5px solid #a8977a}
.ars{width:0;height:0;border-top:4px solid transparent;border-bottom:4px solid transparent;border-left:5px solid #a8977a}
.two{width:0;height:0;border-left:4px solid transparent;border-right:4px solid transparent;border-top:5px solid #c9bca2}
.twc{width:0;height:0;border-top:4px solid transparent;border-bottom:4px solid transparent;border-left:5px solid #c9bca2}
.row{border-top:1px solid rgba(0,0,0,.2)}
.tg{height:13px;line-height:11px;padding:0 2px;border:1px solid;color:var(--pet);border-color:#d38cff66;font-family:rss}
.ok{width:10px;height:10px;border-radius:50%;background:#2f9a2a;box-shadow:0 0 0 1px #000}
.ok:after{content:"";position:absolute;left:3px;top:1px;width:3px;height:6px;border:solid #fff;border-width:0 2px 2px 0;transform:rotate(45deg)}
.tag{height:16px;line-height:15px;padding:0 4px;border:1px solid #000;background:#29221a;box-shadow:inset 0 0 0 1px #43392c}
.tag img{height:11px;vertical-align:-1px;margin-right:2px}
.cat{height:20px;background:linear-gradient(#352c22,#2a231b);border:1px solid #000;border-bottom:none;box-shadow:inset 1px 1px 0 #4d4234}
.cat.on{background:linear-gradient(#5b4b36,#3f3427);box-shadow:inset 1px 1px 0 #8a7656}
.cllist{background:#211b15;border:1px solid #000}
.clpage{background:#28221a;border:1px solid #000}
.clh{position:absolute;left:0;top:0;width:100%;height:56px;border-bottom:2px solid #0e0b08;box-shadow:0 1px 0 #4a3f31}
.pane{background:#3e3529;box-shadow:inset 0 0 0 1px #5a4d3c;
  background-image:radial-gradient(rgba(255,255,255,.025) 1px,transparent 1px),radial-gradient(rgba(0,0,0,.08) 1px,transparent 1px);background-size:3px 3px,5px 5px}
.tab{height:26px;background:linear-gradient(#2f2a22,#24201a);border:1px solid #0f0c09;border-bottom:none;box-shadow:inset 1px 1px 0 #4a4234}
.tab.on{height:27px;background:radial-gradient(ellipse at 50% 60%,#7a2e1c,#4a2016 70%);box-shadow:inset 0 0 0 1px #a8452a,inset 0 1px 0 #d06a46}
.tabic{filter:drop-shadow(1px 0 0 #000) drop-shadow(-1px 0 0 #000) drop-shadow(0 1px 0 #000) drop-shadow(0 -1px 0 #000) drop-shadow(1px 1px 0 #000) saturate(.85) brightness(1.05)}
.box{background:#2a231b;border:1px solid #000;box-shadow:inset 0 0 0 1px #4f4334}
.hr{height:1px;background:#4f4334;box-shadow:0 1px 0 #15110c}
.btn{height:17px;background:linear-gradient(#56493a,#40362a);border:1px solid #000;box-shadow:inset 1px 1px 0 #7d6e57,inset -1px -1px 0 #261f18}
.ck{width:9px;height:9px;border-radius:50%;background:#2f9a2a;box-shadow:0 0 0 1px #000}
.ck:after{content:"";position:absolute;left:3px;top:1px;width:2px;height:5px;border:solid #fff;border-width:0 2px 2px 0;transform:rotate(45deg)}
.ck7{width:7px;height:7px;border-radius:50%;background:#2f9a2a;box-shadow:0 0 0 1px #000}
.ck7:after{content:"";position:absolute;left:2px;top:0;width:2px;height:4px;border:solid #fff;border-width:0 2px 2px 0;transform:rotate(45deg)}
.dot{width:7px;height:7px;border-radius:50%;background:#ffed4c;box-shadow:0 0 0 1px #000}
.dot5{width:5px;height:5px;border-radius:50%;background:#ffed4c;box-shadow:0 0 0 1px #000}
.lock{width:8px;height:6px;background:#ff5252;border-radius:1px;box-shadow:0 0 0 1px #000}
.lock:before{content:"";position:absolute;left:1px;top:-5px;width:4px;height:5px;border:1px solid #ff5252;border-bottom:none;border-radius:3px 3px 0 0}
.qtw{width:0;height:0;border-top:3px solid transparent;border-bottom:3px solid transparent;border-left:4px solid #a8977a}
.qtwo{width:0;height:0;border-left:3px solid transparent;border-right:3px solid transparent;border-top:4px solid #a8977a}
.brk4{border:1px solid #000;background:linear-gradient(135deg,#7a5714,#4a360f 60%,#33270f);box-shadow:inset 0 0 0 1px #b08a35}
.badge{position:absolute;left:5px;top:8px;width:50px;height:32px;background:radial-gradient(ellipse at 50% 30%,#3a2a0c,#1e1507);border:1px solid #000;box-shadow:inset 0 0 0 1px #c9a24a}
.cell{height:32px;background:#1f1a14;border:1px solid #000;box-shadow:inset 0 0 0 1px #3a3126}
.clock{width:15px;height:15px;border-radius:50%;border:2px solid #c9b48a;box-shadow:0 0 0 1px #000}
.clock:before{content:"";position:absolute;left:5px;top:1px;width:2px;height:6px;background:#c9b48a}
.clock:after{content:"";position:absolute;left:5px;top:5px;width:5px;height:2px;background:#c9b48a}
.clock.s{width:13px;height:13px}
.clock.s:before{left:3px;top:0;height:5px}.clock.s:after{left:3px;top:3px;width:4px}
.ev2{background:#1f1a14;border:1px solid #000}
.ev4{background:#1f1a14;border:1px solid #000;box-shadow:inset 0 0 0 1px #3a3126}
.mp{height:13px;width:34px;background:linear-gradient(#56493a,#40362a);border:1px solid #000;box-shadow:inset 1px 1px 0 #7d6e57}
.redsk{filter:sepia(1) saturate(6) hue-rotate(-30deg) brightness(.95) drop-shadow(1px 1px 0 #000)}
.bar6{height:6px;background:#0d0b08;border:1px solid #000}
.chev{width:0;height:0;border-top:4px solid transparent;border-bottom:4px solid transparent;border-left:5px solid #a8977a}
.task4{background:#1f1a14;border:1px solid #000;border-left-width:3px}
.rnd{border-radius:50%;background:radial-gradient(#5a4a33,#2a2219 75%)}
.pb5{height:5px;background:#0d0b08;border:1px solid #000}
.grn{background:linear-gradient(#7ce36b,#2f9a2a)}
.card{background:linear-gradient(#4a3e2f,#33291f);border:1px solid #000;box-shadow:inset 1px 1px 0 #7d6e57,inset -1px -1px 0 #1d1812}
.cardic{border:1px solid #0b0907;background:radial-gradient(circle at 50% 40%,#5a4a33,#1e1913 75%);overflow:hidden}
.rv{background:#1f1a14;border:1px solid #000}
.ib{border-radius:50%;background:radial-gradient(#6a5638,#2a2219 75%)}
.slot{background:#1f1a14;border:1px solid #000;box-shadow:inset 0 0 0 1px #3a3126}
.tier.sel{background:linear-gradient(90deg,#5a4630,#33291e)}
.tier.sel:before{content:"";position:absolute;left:0;top:0;bottom:0;width:2px;background:var(--gold)}
.tierb{border-bottom:1px solid #0d0a07}
.rew{background:#2a2219;border:1px solid #000;box-shadow:inset 0 0 0 1px #4f4334}
.fb{height:20px;line-height:18px;padding:0 8px;background:linear-gradient(#352c22,#2a231b);border:1px solid #000;box-shadow:inset 1px 1px 0 #4d4234;color:#a29680;text-align:center}
.fb.on{background:linear-gradient(#5b4b36,#3f3427);color:var(--orange);box-shadow:inset 1px 1px 0 #8a7656}
.thd{background:#17130e;border-bottom:1px solid #3d3326}
.trow{border-bottom:1px solid rgba(0,0,0,.35);border-left:3px solid #c98a4b}
.ttag{height:13px;line-height:11px;padding:0 2px;border:1px solid}
.pvp{color:#ff5252;border-color:#ff525266}.dng{color:#ff9a3c;border-color:#ff9a3c66}
"""

S = []  # (name, w, h, inner html, extra style for the box)


def sp(name, w, h, html, style=""):
    S.append((name, w, h, html, style))


# ---------------------------------------------------------------- Drop Viewer (dropviewer.html)
sp("dv_bg", 500, 322, """
<div class="win"></div>
<div class="close t" style="left:472px;top:5px">X</div>
<div class="search" style="left:7px;top:30px"><div class="mag"></div></div>
<div class="well" style="left:7px;top:56px;width:150px;height:259px"></div>
<div class="well model" style="left:162px;top:30px;width:92px;height:72px"></div>
<div class="mapbtn" style="left:408px;top:30px"><img src="icons/World_map_icon.png"></div>
<div class="well" style="left:162px;top:106px;width:331px;height:209px"><div class="brk"><img src="icons/Skull.png"></div><div class="alw"></div><div class="thead"></div></div>
""")
sp("dv_search_on", 150, 22, """<div class="search on" style="left:0;top:0"><div class="mag"></div></div>""")
sp("dv_grp", 132, 17, """<div class="grp" style="left:0;top:0;width:132px"></div>""")
sp("dv_lisel", 132, 24, """<div class="lisel" style="left:0;top:0;width:132px;height:24px"></div>""")
sp("dv_thumb", 22, 22, """<div class="th" style="left:0;top:0;width:22px;height:22px"></div>""")
sp("ar_open", 8, 5, """<div class="aro" style="left:0;top:0"></div>""")
sp("ar_shut", 5, 8, """<div class="ars" style="left:0;top:0"></div>""")
sp("tw_open", 8, 5, """<div class="two" style="left:0;top:0"></div>""")
sp("tw_shut", 5, 8, """<div class="twc" style="left:0;top:0"></div>""")
sp("star_gold", 12, 14, """<div class="t" style="left:0;top:-2px;color:#ffcf4a;font-family:rss">&#9733;</div>""")
sp("star_dim", 12, 14, """<div class="t" style="left:0;top:-2px;color:#6f6556;font-family:rss">&#9733;</div>""")
sp("skull12", 13, 12, """<img src="icons/Skull.png" style="left:0;top:0;height:12px">""")
# rows: the well colour under them (so they are opaque)
for name, extra in (("dv_row_even", ""), ("dv_row_odd", "background:rgba(255,255,255,.018)")):
    sp(name, 314, 18, f"""<div style="left:0;top:0;width:314px;height:18px;background:#1f1a14"></div><div class="row" style="left:0;top:0;width:314px;height:18px;{extra}"></div>""")
sp("dv_row_hl", 314, 18, """<div style="left:0;top:0;width:314px;height:18px;background:linear-gradient(90deg,#57442a,#3a2e1f);box-shadow:inset 2px 0 0 #ffcf4a"></div>""")
sp("dv_row_kid", 314, 18, """<div style="left:0;top:0;width:314px;height:18px;background:#16120e;border-top:1px solid rgba(0,0,0,.2)"></div><div style="left:10px;top:0;width:1px;height:18px;background:#4b3f30"></div>""")
sp("dv_row_kid2", 314, 18, """<div style="left:0;top:0;width:314px;height:18px;background:#16120e;border-top:1px solid rgba(0,0,0,.2)"></div><div style="left:10px;top:0;width:1px;height:18px;background:#4b3f30"></div><div style="left:22px;top:0;width:1px;height:18px;background:#4b3f30"></div>""")
sp("tag_pet", 22, 13, """<div class="tg t" style="left:0;top:0">Pet</div>""")
sp("ok", 12, 12, """<div class="ok" style="left:1px;top:1px"></div>""")
# head tags (text in the game font on top; box widths measured by the server from the same text)
# Head tags: the box with its icon, the text invisible (the game draws it); the box auto-sizes to the text like the design.
for name, icon, label in (("tag_wild", "Skull_(status)_icon", "Wild 44"), ("tag_multi", "Multicombat", "Multi"), ("tag_singles", None, "Singles"),
                          ("tag_dangerous", None, "Dangerous"), ("tag_safe", None, "Safe"), ("tag_breach", None, "Breach"), ("tag_top16", None, "Top 16 loot")):
    img = f'<img src="icons/{icon}.png">' if icon else ""
    sp(name, 120, 16, f"""<div class="tag t auto" style="left:0;top:0">{img}<span style="color:transparent;text-shadow:none">{label}</span></div>""")

# ---------------------------------------------------------------- Collection Log (collection-log.html)
sp("cl_bg", 500, 322, """
<div class="win"></div>
<div class="close t" style="left:472px;top:5px">X</div>
<div class="cllist" style="left:7px;top:49px;width:160px;height:266px"></div>
<div class="clpage" style="left:171px;top:49px;width:322px;height:266px"><div class="clh"></div></div>
""")
for w in (95, 96):
    sp(f"cl_cat_on_{w}", w, 20, f"""<div class="cat on" style="left:0;top:0;width:{w}px"></div>""")
    sp(f"cl_cat_off_{w}", w, 20, f"""<div class="cat" style="left:0;top:0;width:{w}px"></div>""")
sp("cl_sel", 142, 16, """<div style="left:0;top:0;width:142px;height:16px;background:#5a4630"></div>""")

# ---------------------------------------------------------------- Quest tab (questtab-4-tabs.html), pane 190x261
sp("qt_pane", 190, 261, """<div class="pane" style="left:0;top:0;width:190px;height:261px"></div><div class="box" style="left:3px;top:27px;width:184px;height:233px"></div>""")
for w in (45, 46):
    sp(f"qt_tab_off_{w}", w, 27, f"""<div class="tab" style="left:0;top:1px;width:{w}px"></div>""")
    sp(f"qt_tab_on_{w}", w, 27, f"""<div class="tab on" style="left:0;top:0;width:{w}px"></div>""")
for name, src in (("qt_ic_quests", "Quest_point_icon"), ("qt_ic_deadman", "Skull"), ("qt_ic_tasks", "Combat_icon"), ("qt_ic_log", "Collection_log")):
    sp(name, 26, 24, f"""<img class="tabic" src="icons/{src}.png" style="left:50%;top:50%;transform:translate(-50%,-50%);height:20px">""")
sp("qt_hr", 174, 2, """<div class="hr" style="left:0;top:0;width:174px"></div>""")
sp("q_ck", 11, 11, """<div class="ck" style="left:1px;top:1px"></div>""")
sp("q_ck7", 9, 9, """<div class="ck7" style="left:1px;top:1px"></div>""")
sp("q_dot", 9, 9, """<div class="dot" style="left:1px;top:1px"></div>""")
sp("q_dot5", 7, 7, """<div class="dot5" style="left:1px;top:1px"></div>""")
sp("q_lock", 10, 13, """<div class="lock" style="left:1px;top:6px"></div>""")
sp("q_star", 12, 14, """<div class="t" style="left:0;top:-2px;color:#ffcf4a;font-family:rss">&#9733;</div>""")
sp("q_tw", 4, 6, """<div class="qtw" style="left:0;top:0"></div>""")
sp("q_two", 6, 4, """<div class="qtwo" style="left:0;top:0"></div>""")
# Deadman panel
sp("dm_btn", 36, 17, """<div class="btn" style="left:0;top:0;width:36px"></div>""")
sp("dm_brk", 174, 50, """<div class="brk4" style="left:0;top:0;width:174px;height:50px"><div class="badge"></div></div>""")
sp("dm_cell2", 85, 32, """<div class="cell" style="left:0;top:0;width:85px"></div>""")
sp("dm_cell3", 56, 32, """<div class="cell" style="left:0;top:0;width:56px"></div>""")
sp("dm_cell3w", 56, 32, """<div class="cell" style="left:0;top:0;width:56px"></div>""")
sp("dm_skull", 15, 14, """<img src="icons/Skull_(status)_icon.png" style="left:0;top:0;height:14px">""")
sp("dm_shield", 15, 14, """<img src="icons/Protect_Item.png" style="left:0;top:0;height:14px">""")
sp("dm_clock", 16, 16, """<div class="clock s" style="left:1px;top:1px"></div>""")
sp("dm_star", 12, 14, """<div class="t" style="left:0;top:-2px;color:#ffcf4a;font-family:rss">&#9733;</div>""")
sp("dm_coins", 16, 14, """<img src="icons/Coins_250.png" style="left:0;top:0;height:14px">""")
sp("dm_redskull14", 17, 15, """<img class="redsk" src="icons/Skull.png" style="left:0;top:0;height:14px">""")
sp("dm_redskull13", 16, 14, """<img class="redsk" src="icons/Skull.png" style="left:0;top:0;height:13px">""")
sp("dm_redskull12", 15, 13, """<img class="redsk" src="icons/Skull.png" style="left:0;top:0;height:12px">""")
sp("dm_ev2", 174, 19, """<div class="ev2" style="left:0;top:0;width:174px;height:19px"></div>""")
sp("dm_ev4", 174, 26, """<div class="ev4" style="left:0;top:0;width:174px;height:26px"></div>""")
sp("dm_chest", 16, 16, """<img src="icons/Deadmans_chest_(scenery).png" style="left:0;top:0;width:16px;height:16px;object-fit:contain;filter:brightness(1.8)">""")
sp("dm_mp", 34, 13, """<div class="mp" style="left:0;top:0"></div>""")
# Tasks panel
sp("tk_bar", 54, 6, """<div class="bar6" style="left:0;top:0;width:54px"></div>""")
sp("tk_chev", 5, 8, """<div class="chev" style="left:0;top:0"></div>""")
for name, src in (("tk_easy", "Bronze_scimitar"), ("tk_medium", "Mithril_scimitar"), ("tk_hard", "Rune_scimitar"), ("tk_elite", "Dragon_scimitar"), ("tk_all", "Combat_icon")):
    sp(name, 17, 15, f"""<img src="icons/{src}.png" style="left:0;top:0;height:15px">""")
    sp(name + "19", 21, 19, f"""<img src="icons/{src}.png" style="left:0;top:0;height:19px">""")
for tier, c in (("easy", "#c98a4b"), ("medium", "#7f9fe0"), ("hard", "#4fd0d6"), ("elite", "#ff6262")):
    sp(f"tk_card_{tier}", 174, 25, f"""<div class="task4" style="left:0;top:0;width:174px;height:25px;border-left-color:{c}"></div>""")
    sp(f"tw_row_{tier}", 313, 30, f"""<div style="left:0;top:0;width:313px;height:30px;background:#1f1a14"></div><div class="trow" style="left:0;top:0;width:313px;height:30px;border-left-color:{c}"></div>""")
sp("tk_rnd18", 18, 18, """<div class="rnd" style="left:0;top:0;width:18px;height:18px"></div>""")
sp("tk_rnd20", 20, 20, """<div class="rnd" style="left:0;top:0;width:20px;height:20px"></div>""")
sp("tk_pb110", 110, 5, """<div class="pb5" style="left:0;top:0;width:110px"></div>""")
sp("grn_fill", 120, 4, """<div class="grn" style="left:0;top:0;width:120px;height:4px"></div>""")
# Drops & Log panel
sp("dl_card1", 174, 38, """<div class="card" style="left:0;top:0;width:174px;height:38px"><div class="cardic" style="position:absolute;left:5px;top:2px;width:30px;height:30px"><img src="icons/Looting_bag.png" style="position:absolute;left:4px;top:2px;width:20px;height:22px;object-fit:contain"></div></div>""")
sp("dl_card2", 174, 44, """<div class="card" style="left:0;top:0;width:174px;height:44px"><div class="cardic" style="position:absolute;left:5px;top:3px;width:30px;height:30px"><img src="icons/Collection_log.png" style="position:absolute;left:5px;top:3px;width:18px;height:20px;object-fit:contain"></div><div class="pb5" style="position:absolute;left:41px;top:33px;width:122px;height:6px"></div></div>""")
sp("dl_rv", 174, 17, """<div class="rv" style="left:0;top:0;width:174px;height:17px"><div class="ib" style="position:absolute;left:2px;top:0;width:16px;height:15px"></div></div>""")
sp("dl_slot33", 33, 32, """<div class="slot" style="left:0;top:0;width:33px;height:32px"></div>""")
sp("dl_slot34", 34, 32, """<div class="slot" style="left:0;top:0;width:34px;height:32px"></div>""")

# ---------------------------------------------------------------- Deadman Tasks window (deadman-tasks.html)
sp("tw_bg", 500, 322, """
<div class="win"></div>
<div class="close t" style="left:472px;top:5px">X</div>
<div class="well" style="left:7px;top:30px;width:150px;height:285px"><div class="rew" style="position:absolute;left:5px;top:184px;width:138px;height:62px"></div></div>
<div class="well" style="left:163px;top:54px;width:330px;height:261px"><div class="thd" style="position:absolute;left:0;top:0;width:100%;height:16px"></div></div>
""")
sp("tw_tier", 148, 36, """<div class="tierb" style="left:0;top:0;width:148px;height:36px"></div>""")
sp("tw_tier_sel", 148, 36, """<div class="tier sel tierb" style="left:0;top:0;width:148px;height:36px"></div>""")
sp("tw_bar", 112, 7, """<div class="bar6" style="left:0;top:0;width:112px;height:7px"></div>""")
for label, w in (("To do", 49), ("Done", 45), ("All", 36)):
    key = label.lower().replace(" ", "")
    sp(f"tw_fb_{key}_on", w, 20, f"""<div class="fb on t" style="left:0;top:0;width:{w}px">{label}</div>""")
    sp(f"tw_fb_{key}_off", w, 20, f"""<div class="fb t" style="left:0;top:0;width:{w}px">{label}</div>""")
sp("tw_fb_type", 96, 20, """<div class="fb" style="left:0;top:0;width:96px"></div>""")
sp("tw_tag_pvp", 25, 13, """<div class="ttag pvp t" style="left:0;top:0">PvP</div>""")
sp("tw_tag_dng", 40, 13, """<div class="ttag dng t" style="left:0;top:0">Danger</div>""")
sp("tw_pb56", 56, 6, """<div class="bar6" style="left:0;top:0;width:56px"></div>""")

# ---------------------------------------------------------------- Deadman Info window (N274, owner GOEDGEKEURD 2026-10-24:
# screens	eam\INFO\info-v1.png, bron info-v1.html). Names start with "zi_" so they sort after every older sprite: the ids of the
# sprites above stay what they were (SPRITE_BASE + sorted index).
sp("zi_bg", 500, 322, """
<div class="win"></div>
<div class="close t" style="left:472px;top:5px">X</div>
<div style="left:7px;top:29px;width:150px;height:286px;background:#211b15;border:1px solid #000"></div>
<div style="left:161px;top:29px;width:332px;height:286px;background:#28221a;border:1px solid #000"><div style="position:absolute;left:0;top:0;width:330px;height:44px;border-bottom:2px solid #0e0b08;box-shadow:0 1px 0 #4a3f31"></div></div>
""")
sp("zi_sel", 148, 24, """<div style="left:0;top:0;width:148px;height:24px;background:#5a4630;box-shadow:inset 3px 0 0 #ff981f"></div>""")
sp("zi_stripe", 318, 18, """<div style="left:0;top:0;width:318px;height:18px;background:#211b15"></div>""")
sp("zi_th", 318, 18, """<div style="left:0;top:0;width:318px;height:18px;background:#1a1510;border-bottom:1px solid #000"></div>""")
sp("zi_me", 318, 18, """<div style="left:0;top:0;width:318px;height:18px;background:#4a3a22"></div>""")
sp("zi_bar", 318, 26, """<div style="left:0;top:0;width:318px;height:26px;background:#1f1a14;border:1px solid #000;box-shadow:inset 0 0 0 1px #3a3126"></div>""")
sp("zi_map", 88, 18, """<div style="left:0;top:0;width:88px;height:18px;background:linear-gradient(#5e503e,#43382b);border:1px solid #000;box-shadow:inset 1px 1px 0 #8a7a60,inset -1px -1px 0 #261f18"><img src="icons/World_map_icon.png" style="position:absolute;left:4px;top:2px;height:13px"></div>""")
sp("zi_btn_info", 46, 18, """<div style="left:0;top:0;width:46px;height:18px;background:linear-gradient(#5e503e,#43382b);border:1px solid #000;box-shadow:inset 1px 1px 0 #8a7a60,inset -1px -1px 0 #261f18"><span style="position:absolute;left:3px;top:2px;width:12px;height:12px;border-radius:50%;background:radial-gradient(circle at 40% 35%,#6aa8ff,#1d4fa8 70%);box-shadow:0 0 0 1px #000,inset 0 0 0 1px #9cc6ff;color:#fff;font-family:rsb;font-size:11px;line-height:12px;text-align:center">i</span></div>""")
# Subject icons: 16 px in the list, 32 px on the page (the mock's own pictures).
INFO_ICONS = (
    ("loyalty", None, "star"), ("donator", "Coins_250", ""), ("deadman", "Skull", "redsk"), ("emblem", "Archaic_emblem_(tier_1)", ""),
    ("breach", None, "clock"), ("chest", "Deadmans_chest_(scenery)", "chest"), ("protection", "Protect_Item", ""),
    ("skull", "Skull_(status)_icon", ""), ("brackets", "Combat_icon", ""), ("teleports", "Law_rune", ""),
)
for key, src, kind in INFO_ICONS:
    for size, prefix in ((16, "zi_ic_"), (32, "zi_big_")):
        if kind == "star":
            inner = f"""<div class="t" style="left:0;top:{-2 if size == 16 else -6}px;width:{size}px;text-align:center;color:#ffcf4a;font-family:rss;font-size:{16 if size == 16 else 36}px;line-height:{size + 4}px">&#9733;</div>"""
        elif kind == "clock":
            b = 2 if size == 16 else 3
            d = size - 4 if size == 16 else size - 6
            inner = f"""<div style="left:2px;top:2px;width:{d}px;height:{d}px;border-radius:50%;border:{b}px solid #c9b48a;box-shadow:0 0 0 1px #000"><div style="position:absolute;left:{d // 2 - b}px;top:{1}px;width:{b}px;height:{d // 2 - 1}px;background:#c9b48a"></div><div style="position:absolute;left:{d // 2 - b}px;top:{d // 2 - b}px;width:{d // 3}px;height:{b}px;background:#c9b48a"></div></div>"""
        else:
            cls = ' class="redsk"' if kind == "redsk" else ""
            flt = "filter:brightness(1.8) drop-shadow(1px 1px 0 #000);" if kind == "chest" else ("" if kind == "redsk" else "filter:drop-shadow(1px 1px 0 #000);")
            inner = f"""<img{cls} src="icons/{src}.png" style="left:0;top:0;width:{size}px;height:{size}px;object-fit:contain;{flt}">"""
        sp(prefix + key, size, size, inner)

# ---------------------------------------------------------------- layout
x = y = 0
rowh = 0
html = []
manifest = {}
for name, w, h, inner, style in S:
    if x + w > 1196:
        x = 0
        y += rowh + 4
        rowh = 0
    html.append(f'<div class="sp" id="{name}" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px;{style}">{inner}</div>')
    manifest[name] = [x, y, w, h]
    x += w + 4
    rowh = max(rowh, h)
height = y + rowh + 4
open("sheet.html", "w", encoding="utf-8").write(
    f"<!doctype html><html><head><meta charset='utf-8'><style>{CSS} html,body{{height:{height}px}}</style></head><body>{''.join(html)}"
    + "<script>document.fonts.ready.then(()=>{const m={};document.querySelectorAll('.sp').forEach(e=>{const a=e.querySelector('.auto');const r=(a||e).getBoundingClientRect();"
    + "m[e.id]=[Math.round(r.left),Math.round(r.top),Math.round(r.width),Math.round(r.height)]});const p=document.createElement('pre');p.id='m';p.style.display='none';p.textContent=JSON.stringify(m);document.body.appendChild(p)})</script></body></html>"
)
json.dump({"height": height, "sprites": manifest}, open("layout.json", "w"), indent=0)
print(len(S), "sprites, sheet height", height)
