"""Bake the source STASH frame/rows into ordinary IF3 components, without runtime/cache writes.

Frame instructions run from cache2686 scripts 703/711/98/6388. Row dimensions,
colours and check sprites come from 1478; native p12 measurements keep the text
inside the real 667 font's cells. A dimensional expression becomes its equivalent
IF3 alignment mode, so frame edges follow the viewport instead of a guessed size.
"""
from dataclasses import dataclass
from copy import deepcopy
from pathlib import Path
import json
import re
import base64
import struct
from build_catalogue import ROOT


@dataclass(frozen=True)
class Dim:
    parent: int
    axis: str
    offset: int = 0
    def __sub__(self, value):
        assert isinstance(value, int)
        return Dim(self.parent, self.axis, self.offset-value)


class Frame:
    def __init__(self, base, scripts):
        self.nodes = {int(k): deepcopy(v) for k, v in base.items()}
        self.scripts = scripts
        self.children = {}
        self.stack = []
        self.strings = []
        self.active = None

    def pop(self, count=1):
        out = self.stack[-count:]; del self.stack[-count:]
        return out[0] if count == 1 else out

    def node(self, hash):
        return self.nodes[hash & 65535]

    def geometry(self, node, values, size):
        keys = ['originalWidth','originalHeight','widthMode','heightMode'] if size else ['originalX','originalY','xPositionMode','yPositionMode']
        x, y, xm, ym = values
        for axis, value, mode, field, modefield in [('w',x,xm,keys[0],keys[2]),('h',y,ym,keys[1],keys[3])]:
            if isinstance(value, Dim):
                assert value.parent == node['parentId'] and value.axis == axis and mode == 0
                if size:
                    node[field], node[modefield] = -value.offset, 1
                else:
                    # Source proc6388 sets position before size; resolve the
                    # right/bottom alignment only after the final size is known.
                    node[field], node[modefield] = value, 0
            else:
                node[field], node[modefield] = value, mode

    def run(self, id):
        if id == 1972:
            self.stack.append(-1) # Source desktop branch; this 667 client has no mobile platform.
            return
        script = self.scripts[str(id)]
        ni, ns = script['intStackCount'], script['stringStackCount']
        ints = self.stack[-ni:] if ni else []
        strings = self.strings[-ns:] if ns else []
        if ni: del self.stack[-ni:]
        if ns: del self.strings[-ns:]
        ints += [0]*(script['localIntCount']-len(ints))
        strings += ['']*(script['localStringCount']-len(strings))
        pc = 0
        while pc < len(script['instructions']):
            op, operand = script['instructions'][pc], script['intOperands'][pc]
            target = self.active
            if 2000 <= op < 2500:
                target=self.node(self.pop());op-=1000
            if op == 0: self.stack.append(operand)
            elif op == 3: self.strings.append(script['stringOperands'][pc])
            elif op == 33: self.stack.append(ints[operand])
            elif op == 34: ints[operand] = self.pop()
            elif op == 35: self.strings.append(strings[operand])
            elif op == 38: self.pop()
            elif op == 40: self.run(operand)
            elif op == 21: return
            elif op == 6: pc += operand
            elif op == 8:
                a,b = self.pop(2)
                if a == b: pc += operand
            elif op == 102:
                parent = self.pop()
                assert parent not in self.children # Only initial source construction is baked.
                self.children[parent] = {}
            elif op == 100:
                parent, type, index, extra = self.pop(4)
                assert extra == 0 and index not in self.children.setdefault(parent,{})
                uid = max(self.nodes)+1
                self.nodes[uid] = dict(id=(493<<16)|uid, type=type, parentId=parent,
                    originalX=0,originalY=0,originalWidth=0,originalHeight=0)
                self.children[parent][index] = uid
                self.active = self.nodes[uid]
                self.active['sourceDynamicIndex'] = index
            elif op == 1000: self.geometry(self.active, self.pop(4), False)
            elif op == 1001: self.geometry(self.active, self.pop(4), True)
            elif op == 1003: target['isHidden']=bool(self.pop())
            elif op in (1101,1105,1107,1113,1115):
                key = {1101:'textColor',1105:'spriteId',1107:'spriteTiling',1113:'fontId',1115:'textShadowed'}[op]
                value = self.pop()
                self.active[key] = bool(value) if op in (1107,1115) else value
            elif op == 1114:
                self.active['xTextAlignment'],self.active['yTextAlignment'],self.active['lineHeight'] = self.pop(3)
            elif op == 1112: self.active['text'] = self.strings.pop()
            elif op == 1702: self.stack.append(self.active['sourceDynamicIndex'])
            elif op in (2502,2503): self.stack.append(Dim(self.pop(),'w' if op==2502 else 'h'))
            elif op == 4001:
                a,b=self.pop(2);self.stack.append(a-b)
            elif op == 4000:
                a,b=self.pop(2);self.stack.append(a+b)
            elif op == 4010:
                a,b=self.pop(2);self.stack.append(1 if a & (1<<b) else 0)
            elif op == 1300:
                index=self.pop();value=self.strings.pop();assert index==1
                self.active['actions']=[value]
            elif op in (1403,1404,1409):
                signature=self.strings.pop(); assert not signature.endswith('Y')
                values=self.pop(len(signature)+1)
                target.setdefault('sourceHooks',{})[str(op)] = values
            elif op == 1400:
                signature=self.strings.pop();assert not signature.endswith('Y')
                target.setdefault('sourceHooks',{})[str(op)]=self.pop(len(signature)+1)
            else: raise ValueError((id,pc,op,operand))
            pc += 1


def enums():
    out = {}
    all = json.loads((ROOT/'build/clue/osrs-enums.json').read_text())
    for id in range(1526,1531):
        raw=base64.b64decode(all[str(id)]);pos=0;rows={}
        while raw[pos]:
            op=raw[pos];pos+=1
            if op in (1,2):pos+=1
            elif op==3:pos=raw.index(0,pos)+1
            elif op==4:pos+=4
            elif op==6:
                count=struct.unpack_from('>H',raw,pos)[0];pos+=2
                for _ in range(count):
                    key,value=struct.unpack_from('>ii',raw,pos);pos+=8;rows[key]=value
            else:raise ValueError((id,op))
        out[id]=rows
    return out


def main():
    base=json.loads((ROOT/'docs/CLUE/cache/stash-chart-interface.json').read_text())['493']
    scripts=json.loads((ROOT/'docs/CLUE/cache/stash-chart-scripts.json').read_text())
    frame=Frame(base,scripts)
    frame.stack=[(493<<16)|1,0];frame.strings=['STASH Units'];frame.run(703)
    assert not frame.strings and len(frame.stack)==0
    tabs=[(493<<16)|id for id in range(30,35)]+[(493<<16)|29]
    frame.stack=[tabs[0]]+tabs;frame.run(1481)
    assert not frame.stack and not frame.strings
    nodes=frame.nodes
    for node in nodes.values():
        for axis,field in [('w','originalX'),('h','originalY')]:
            value=node.get(field)
            if isinstance(value,Dim):
                extent=node.get('originalWidth' if axis=='w' else 'originalHeight',0)
                assert isinstance(extent,int)
                node[field]=-value.offset-extent
                node['xPositionMode' if axis=='w' else 'yPositionMode']=2
    nodes[1].pop('onLoadListener',None)
    close=next(id for id,n in nodes.items() if n.get('actions')==['Close'])
    closeNode=nodes[close]
    assert closeNode['sourceHooks']['1403'][0]==closeNode['sourceHooks']['1404'][0]==229
    closeNode['sourceMouseOverSprite']=closeNode['sourceHooks']['1403'][-2]
    closeNode['sourceMouseLeaveSprite']=closeNode['sourceHooks']['1404'][-2]
    catalogue=json.loads((ROOT/'data/cfg/clue/stashes.json').read_text())
    by_id={unit['osrsId']:unit for unit in catalogue['units']}
    labels=json.loads((ROOT/'docs/CLUE/cache/stash-labels.json').read_text())
    source_enums=enums()
    controls={'close':close,'tiers':{},'checks':[]}

    def add(type,parent,**fields):
        uid=max(nodes)+1
        nodes[uid]=dict(id=(493<<16)|uid,type=type,parentId=(493<<16)|parent,
            originalX=0,originalY=0,originalWidth=0,originalHeight=0)
        nodes[uid].update(fields)
        return uid

    for index,tier in enumerate(['easy','medium','hard','elite','master']):
        body=6+index*2;scroll=body+1;tab=30+index
        units=[value for key,value in sorted(source_enums[1526+index].items()) if value in by_id]
        assert len(units)==sum(u['tier']==tier for u in by_id.values()),(tier,units)
        y=0
        for row in range(0,len(units),2):
            pair=units[row:row+2]
            h=max(20,*[12*labels['labels'][str(id)]['nativeLines143']+9 for id in pair])
            if row%4==0:
                add(3,body,originalY=y,originalHeight=h,widthMode=1,fill=True,textColor=16777215,opacity=245)
            for column,id in enumerate(pair):
                label=labels['labels'][str(id)]['text']
                add(4,body,originalX=85+232*column,originalY=y,originalWidth=143,originalHeight=h,
                    text=label,fontId=495,textColor=16750623,textShadowed=True,yTextAlignment=1)
                for filled in (False,True):
                    mark=add(5,body,originalX=(54 if filled else 12)+232*column,originalY=y+(h-17)//2,
                        originalWidth=17,originalHeight=17,spriteId=1210,isHidden=True)
                    controls['checks'].append(dict(component=mark,sourceId=id,filled=filled))
            y+=h
        nodes[body]['scrollHeight']=y
        nodes[body]['isHidden']=index!=0
        # Keep the source row dimensions; reject expansions that would require
        # the as-yet unported scrollbar at the native fixed viewport.
        assert all(nodes[id]['heightMode'] == 1 for id in (0, 2, body))
        native_height = 334 - sum(nodes[id]['originalHeight'] for id in (0, 2, body))
        assert y <= native_height, (tier, 'source rows no longer fit native fixed viewport', y, native_height)
        nodes[scroll]['isHidden']=True
        nodes[tab]['actions']=[tier.title() if tier!='master' else '78']
        if tier == 'master':
            for node in nodes.values():
                if node.get('parentId') == (493<<16)|tab and node.get('text') == 'Master':
                    node['text']='78'
        nodes[tab]['originalX']-=40 # Remove the owner's excluded beginner tab; centre five original 80px tabs in 480px.
        children=sorted(((id,n) for id,n in nodes.items() if n.get('parentId')==(493<<16)|tab),
                        key=lambda p:p[1]['sourceDynamicIndex'])
        assert [n['type'] for _,n in children]==[5,5,5,4]
        controls['tiers'][tier]=dict(body=body,tab=tab,sprites=[id for id,_ in children[:3]],
            label=children[3][0],text='78' if tier=='master' else tier.title())
    assert len(controls['checks'])==2*len(by_id)
    nodes[4]['isHidden']=nodes[5]['isHidden']=nodes[29]['isHidden']=True
    hover=json.loads((ROOT/'docs/CLUE/cache/stash-tab-hover-script.json').read_text())['1483']
    values=[arg for op,arg in zip(hover['instructions'],hover['intOperands']) if op==0 and arg>=900]
    assert values==[998,999,1000,1001,1002,1003]
    selected=controls['tiers']['easy'];unselected=controls['tiers']['medium']
    states=dict(selectedSprites=[nodes[id]['spriteId'] for id in selected['sprites']],
        unselectedSprites=[nodes[id]['spriteId'] for id in unselected['sprites']],
        selectedColour=nodes[selected['label']]['textColor'],unselectedColour=nodes[unselected['label']]['textColor'])
    assert states['selectedSprites']==values[:3] and states['unselectedSprites']==values[3:]
    controls['states']=states
    result=dict(sourceGroup=493,activation='Baked review only; no cache/runtime mapping',
        sourceFrameScripts=[703,711,98,6388,1972], sourceRowsScript=1478,
        components={str(id):node for id,node in nodes.items()},controls=controls,
        pending=['Scrollbar/client verification at small native viewport','IF3 import/preflight and runtime chart bindings'])
    (ROOT/'docs/CLUE/cache/stash-chart-baked.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8')
    (ROOT/'data/cfg/clue/stash-chart.json').write_text(json.dumps(controls,indent=2)+'\n',encoding='utf8')
    print('Source frame/rows baked:',len(nodes),'components,',len(controls['checks']),'private check marks; no cache or runtime mapping writes')


if __name__=='__main__':
    main()
