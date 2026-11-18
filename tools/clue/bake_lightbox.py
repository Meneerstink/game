"""Bake source322 and its 228/134/1334/1339 children; review JSON only, never cache writes."""
from copy import deepcopy
from hashlib import sha256
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[2]
GROUP = 322


def main():
    folder=ROOT/'docs/CLUE/cache'
    base=json.loads((folder/'interfaces.json').read_text())['322']
    scripts=json.loads((folder/'lightbox-scripts.json').read_text())
    original=json.loads((folder/'scripts.json').read_text())
    items=json.loads((folder/'lightbox-items.json').read_text())['items']
    nodes={int(k):deepcopy(v) for k,v in base.items()}
    def add(type,parent,x=0,y=0,w=0,h=0,wm=0,hm=0,xm=0,ym=0,**fields):
        uid=max(nodes)+1
        nodes[uid]=dict(id=(GROUP<<16)|uid,type=type,parentId=(GROUP<<16)|parent,
            originalX=x,originalY=y,originalWidth=w,originalHeight=h,
            widthMode=wm,heightMode=hm,xPositionMode=xm,yPositionMode=ym,isHidden=False)
        nodes[uid].update(fields)
        return uid
    def graphic(parent,sprite,x,y,w,h,wm=0,hm=0,xm=0,ym=0,**fields):
        return add(5,parent,x,y,w,h,wm,hm,xm,ym,spriteId=sprite,spriteTiling=True,**fields)
    # Source228 desktop default: classic steel border, b12 title, native close-button hover.
    assert [v for op,v in zip(scripts['228']['instructions'],scripts['228']['intOperands']) if op==0].count(297)==1
    graphic(1,297,0,0,2,2,1,1,1,1)
    add(4,1,0,6,12,24,1,0,1,0,fontId=496,text='Light box',xTextAlignment=1,yTextAlignment=1,
        textShadowed=True,textColor=0xff981f)
    for x,y,xm,ym,w,h,wm,hm,sprite in [(0,0,0,0,25,30,0,0,310),(0,0,2,0,25,30,0,0,311),
        (0,0,0,2,25,30,0,0,312),(0,0,2,2,25,30,0,0,313),(-15,0,0,1,36,60,0,1,172),
        (-15,0,2,1,36,60,0,1,315),(0,-15,1,0,50,36,1,0,314),(0,-15,1,2,50,36,1,0,173),
        (0,14,1,0,10,26,1,0,2546)]:
        graphic(1,sprite,x,y,w,h,wm,hm,xm,ym)
    close=graphic(1,535,3,6,26,23,0,0,2,0,actions=['Close'],
        onMouseOverListener=[44,-2147483645,536],onMouseLeaveListener=[44,-2147483645,535])
    assert close==35
    # Source93 ->134, the unpressed outline uses913..920 (not the different208 filled frame).
    assert all(sprite in original['134']['intOperands'] for sprite in range(913,921))
    labels=[]
    for index,parent in enumerate(range(8,16)):
        nodes[parent]['actions']=['Press'];nodes[parent]['name']='<col=ff981f>Button'
        # Source1334 dynamic child4: 4px inset, width/height minus4, p12 label.
        labels.append(add(4,16+index,0,0,4,4,1,1,fontId=495,text=chr(ord('A')+index),textColor=16750623,
            xTextAlignment=1,yTextAlignment=1,lineHeight=0))
    for node in nodes.values():
        for key in ['onLoadListener','onVarTransmitListener','varTransmitTriggers']:
            node.pop(key,None)
    assert list(nodes)==list(range(44))
    payload={'322':{str(k):v for k,v in nodes.items()}}
    (folder/'lightbox-interface.json').write_text(json.dumps(payload,indent=2)+'\n')
    (folder/'lightbox-layout.json').write_text(json.dumps(dict(sourceGroup=322,components=44,
        close=close,buttons=list(range(8,16)),labels=labels,viewport=[512,334],sourceScripts=[228,93,134,1333,1334,1339],
        sourceHashes={name:sha256((folder/name).read_bytes()).hexdigest() for name in
            ['interfaces.json','scripts.json','lightbox-scripts.json','lightbox-items.json']},
        dynamic='Port1339 draws25 actual item icons with source1205/outline1;134/135/208 draw button states;1336/1337 retain15client-cycle press timing',
        pending=['Native loaded-client verification after cache apply']),indent=2)+'\n')
    print('Source322 baked44 static components; source CS2 draws grid/button states dynamically; close35')


if __name__=='__main__':main()
