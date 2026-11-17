"""Extract timestamped source-video samples and hashes; never infer game IDs or timings."""
import argparse
from hashlib import sha256
import json
from pathlib import Path

import cv2
from PIL import Image, ImageDraw


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('video',type=Path)
    parser.add_argument('output',type=Path)
    parser.add_argument('--start',type=float,required=True)
    parser.add_argument('--interval',type=float,required=True)
    parser.add_argument('--count',type=int,default=12)
    parser.add_argument('--crop',type=int,nargs=4)
    args=parser.parse_args()
    assert args.count>0 and args.interval>0 and args.start>=0
    video=cv2.VideoCapture(str(args.video));assert video.isOpened()
    sheet=Image.new('RGB',(4*400,((args.count+3)//4)*250))
    draw=ImageDraw.Draw(sheet)
    samples=[]
    for i in range(args.count):
        seconds=args.start+i*args.interval
        video.set(cv2.CAP_PROP_POS_MSEC,seconds*1000)
        ok,frame=video.read();assert ok,(args.video,seconds)
        image=Image.fromarray(cv2.cvtColor(frame,cv2.COLOR_BGR2RGB))
        if args.crop:image=image.crop(tuple(args.crop))
        image.thumbnail((400,225))
        x=(i%4)*400;y=(i//4)*250
        sheet.paste(image,(x,y+25));draw.text((x+3,y+3),f'{seconds:.3f}s',fill='white')
        samples.append(dict(requestedSeconds=seconds,decodedFrame=int(video.get(cv2.CAP_PROP_POS_FRAMES))-1))
    video.release()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    sheet.save(args.output)
    proof=dict(video=str(args.video),videoSha256=sha256(args.video.read_bytes()).hexdigest(),
               output=str(args.output),outputSha256=sha256(args.output.read_bytes()).hexdigest(),
               crop=args.crop,samples=samples,note='Source samples only; no certified animation IDs, game-tick alignment or hidden odds')
    args.output.with_suffix('.json').write_text(json.dumps(proof,indent=2)+'\n')
    print('Extracted',len(samples),'source samples:',args.output)


if __name__=='__main__':main()
