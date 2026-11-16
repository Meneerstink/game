"""Manual identity aid: actual Wiki drawings beside source-cache geometry.

Not a renderer/client test. Source paper texture, lighting and depth sorting
are intentionally absent from the geometry aid; no runtime asset is produced.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json
import math

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'build/clue/original-maps'


def main():
    rows = json.loads((OUT/'candidates.json').read_text())
    groups = json.loads((OUT/'interfaces.json').read_text())
    meshes = json.loads((OUT/'meshes.json').read_text())
    for start in range(0, len(rows), 4):
        sheet = Image.new('RGB', (1020, 4*350), 'white')
        labels = ImageDraw.Draw(sheet)
        for number, row in enumerate(rows[start:start+4]):
            group = str(row['candidateGroup'])
            components = groups[group]
            wiki = Image.open(OUT/row['image']).convert('RGB')
            wiki.thumbnail((500, 320))
            sheet.paste(wiki, ((510-wiki.width)//2, number*350+25))
            canvas = Image.new('RGB', (510, 330), '#e9d6ab')
            draw = ImageDraw.Draw(canvas)

            def origin(widget, visited=()):
                assert widget['xPositionMode'] == widget['yPositionMode'] == 0
                x, y = widget['originalX'], widget['originalY']
                if widget['parentId'] >= 0:
                    key = str(widget['parentId'] & 65535)
                    assert key not in visited
                    a, b = origin(components[key], visited+(key,))
                    x += a; y += b
                return x, y

            for widget in components.values():
                x0, y0 = origin(widget)
                if widget['type'] != 6 or widget['modelId'] == 3395:
                    continue
                mesh = meshes[str(widget['modelId'])]
                yaw, pitch = (widget[key]*math.pi/1024 for key in ['rotationZ', 'rotationX'])
                points = []
                for x, y, z in zip(mesh['vertexX'], mesh['vertexY'], mesh['vertexZ']):
                    xx = x*math.cos(yaw)+z*math.sin(yaw)
                    zz = z*math.cos(yaw)-x*math.sin(yaw)
                    yy = y*math.cos(pitch)-zz*math.sin(pitch)
                    depth = y*math.sin(pitch)+zz*math.cos(pitch)
                    scale = 512/(widget['modelZoom']+depth)
                    points.append((x0+widget['originalWidth']/2+xx*scale,
                                   y0+widget['originalHeight']/2+yy*scale))
                for a, b, c in zip(mesh['faceA'], mesh['faceB'], mesh['faceC']):
                    draw.polygon([points[a], points[b], points[c]], fill='#473c29')
            sheet.paste(canvas, (510, number*350+20))
            labels.text((8, number*350+2), row['image'], fill='black')
            labels.text((518, number*350+2), f'Source group {group}: geometry aid, NOT client proof', fill='black')
        path = OUT/f'comparison-{start//4+1}.png'
        sheet.save(path)
        print(path)


if __name__ == '__main__':
    main()
