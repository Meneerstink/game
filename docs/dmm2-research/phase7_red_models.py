"""Read-only visual census of red spotanim meshes. Layout: project's ModernModelDecoder.decodeBody.

Candidate generation only; a colour/shape similarity is not proof of an OSRS action mapping.
"""
import sys
import json
import struct
import colorsys
import math
from pathlib import Path
from PIL import Image, ImageDraw
from probe_cache import Cache, SOURCE, group

OUT = Path(__file__).parent / 'phase7-sources'


class Cursor:
    def __init__(self, b, p=0): self.b, self.p = b, p
    def u8(self): x=self.b[self.p]; self.p+=1; return x
    def u16(self): return (self.u8()<<8)|self.u8()
    def smart(self): return self.u8()-64 if self.b[self.p]<128 else self.u16()-49152


def mesh(b):
    typ = b[-2:]
    assert typ in (b'\xff\xff', b'\xff\xfd'), typ.hex()
    r=Cursor(b,len(b)-(26 if typ==b'\xff\xfd' else 23))
    nv,nf,nt=r.u16(),r.u16(),r.u8()
    flat,pri,alpha,fg,tex,vl=[r.u8() for _ in range(6)]
    if typ==b'\xff\xfd': r.u8()
    lx,ly,lz,fd,ts=[r.u16() for _ in range(5)]
    vls=r.u16() if typ==b'\xff\xfd' else (nv if vl else 0)
    p=nt; vp=p; p+=nv
    if flat: p+=nf
    ft=p;p+=nf
    if pri==255:p+=nf
    if fg:p+=nf
    p+=vls; ap=p
    if alpha:p+=nf
    fp=p;p+=fd
    if tex:p+=nf*2
    p+=ts; cp=p;p+=nf*2
    xp=p;p+=lx;yp=p;p+=ly;zp=p
    xx,yy,zz=Cursor(b,xp),Cursor(b,yp),Cursor(b,zp)
    vs=[];x=y=z=0
    for i in range(nv):
        flag=b[vp+i]
        x+=xx.smart() if flag&1 else 0;y+=yy.smart() if flag&2 else 0;z+=zz.smart() if flag&4 else 0
        vs.append((x,y,z))
    f=Cursor(b,fp);a=c=last=bb=0;faces=[]
    for i in range(nf):
        t=b[ft+i]
        if t==1:a=f.smart()+last;bb=f.smart()+a;c=f.smart()+bb;last=c
        elif t==2:bb=c;c=f.smart()+last;last=c
        elif t==3:a=c;c=f.smart()+last;last=c
        elif t==4:a,bb=bb,a;c=f.smart()+last;last=c
        else:raise ValueError(('face encoding',t))
        assert min(a,bb,c)>=0 and max(a,bb,c)<nv
        colour=struct.unpack_from('>H',b,cp+2*i)[0]
        faces.append((a,bb,c,colour,b[ap+i] if alpha else 0))
    return vs,faces


def draw_mesh(vs,fs):
    # Fixed camera, same orientation for every candidate. Static frame, no texture/alpha animation claim.
    points=[(x*.92-z*.38,y*.92-(x*.38+z*.92)*.38,x*.38+z*.92) for x,y,z in vs]
    xs=[p[0] for p in points];ys=[p[1] for p in points]
    cx=(max(xs)+min(xs))/2;cy=(max(ys)+min(ys))/2
    scale=145/max(max(xs)-min(xs),max(ys)-min(ys),1)
    im=Image.new('RGB',(180,180),(45,45,45));d=ImageDraw.Draw(im)
    for a,b,c,col,alpha in sorted(fs,key=lambda f:-sum(points[i][2] for i in f[:3])):
        if alpha>=254:continue
        h=((col>>10)&63)/64;s=((col>>7)&7)/8;l=(col&127)/128
        rgb=tuple(int(v*255) for v in colorsys.hls_to_rgb(h,l,s))
        d.polygon([(90+(points[i][0]-cx)*scale,90+(points[i][1]-cy)*scale) for i in (a,b,c)],fill=rgb)
    return im


def main():
    c=Cache(str(SOURCE));c.osrs=True
    models={}; errors={}
    for sid,b in group(c,2,13).items():
        r=Cursor(b);mid=None
        try:
            while (op:=r.u8()):
                if op==1:mid=r.u16()
                elif op==3:mid=int.from_bytes(b[r.p:r.p+4],'big');r.p+=4
                elif op in (2,4,5,6):r.u16()
                elif op in (7,8):r.u8()
                elif op in (40,41):
                    for _ in range(r.u8()):r.u16();r.u16()
                else:raise ValueError('unsupported spot opcode '+str(op))
            if mid is not None:models.setdefault(mid,[]).append(sid)
        except Exception as e:errors[str(sid)]=str(e)
    candidates=[]
    for mid,sids in models.items():
        try:
            vs,fs=mesh(c.file(7,mid,0))
            if not (30<=len(fs)<=3000):continue
            red=sum(1 for *_,col,alpha in fs if alpha<250 and ((col>>10)<7 or (col>>10)>60) and ((col>>7)&7)>=4)
            ratio=red/len(fs)
            if ratio<.3:continue
            candidates.append((mid,sids,ratio,vs,fs))
        except Exception:continue
    candidates.sort(key=lambda x:-x[2])
    for page in range(math.ceil(len(candidates)/64)):
        batch=candidates[page*64:(page+1)*64]
        out=Image.new('RGB',(1440,math.ceil(len(batch)/8)*210),'white');d=ImageDraw.Draw(out)
        for i,(mid,sids,ratio,vs,fs) in enumerate(batch):
            x=i%8*180;y=i//8*210;out.paste(draw_mesh(vs,fs),(x,y))
            d.text((x,y+181),f'm{mid} g{sids[:3]}',fill='black')
        out.save(OUT/f'red-spot-models-{page}.png')
    (OUT/'red-spot-census.json').write_text(json.dumps(dict(decoded_spot_models=len(models),unsupported_spots=errors,
        red_candidates=[dict(model=m,spots=s,red_fraction=r,vertices=len(v),faces=len(f)) for m,s,r,v,f in candidates]),indent=2))
    print('spot models',len(models),'unsupported spots',len(errors),'red candidates',len(candidates))


if __name__=='__main__':main()
