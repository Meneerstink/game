"""Geometry navigation aid for manual Wiki comparison, NOT a client/rendering test.

Requires ClueMapGeometryProbe's decoded source meshes. Omits the textured paper,
lighting, clipping and depth sorting. Never produces runtime assets or cache writes.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
GROUPS = [348, 350, 351, 353, 354, 356, 360, 362, 86]


def main():
    widgets = json.loads((ROOT/'docs/CLUE/cache/map-interfaces.json').read_text())
    meshes = json.loads((ROOT/'build/clue/maps-source/meshes.json').read_text())
    atlas = Image.new('RGB', (1530, 1050), 'white')
    labels = ImageDraw.Draw(atlas)
    for index, group in enumerate(GROUPS):
        canvas = Image.new('RGB', (510, 330), '#e9d6ab')
        draw = ImageDraw.Draw(canvas)
        components = widgets[str(group)]

        def origin(widget, ancestors=()):
            parent = widget['parentId']
            x, y = widget['originalX'], widget['originalY']
            assert widget['xPositionMode'] == widget['yPositionMode'] == 0
            if parent >= 0:
                key = str(parent & 65535)
                assert key not in ancestors
                px, py = origin(components[key], ancestors + (key,))
                x += px
                y += py
            return x, y

        for widget in components.values():
            x0, y0 = origin(widget)
            if widget['type'] == 4:
                draw.text((x0, y0), widget.get('text', ''), fill='black')
                continue
            if widget['type'] != 6 or widget['modelId'] == 3395:
                continue
            model = meshes[str(widget['modelId'])]
            yaw, pitch = (widget[key] * math.pi / 1024 for key in ['rotationZ', 'rotationX'])
            points = []
            for x, y, z in zip(model['vertexX'], model['vertexY'], model['vertexZ']):
                xx = x * math.cos(yaw) + z * math.sin(yaw)
                zz = z * math.cos(yaw) - x * math.sin(yaw)
                yy = y * math.cos(pitch) - zz * math.sin(pitch)
                depth = y * math.sin(pitch) + zz * math.cos(pitch)
                scale = 512 / (widget['modelZoom'] + depth)
                points.append((x0 + widget['originalWidth']/2 + xx*scale,
                               y0 + widget['originalHeight']/2 + yy*scale))
            for a, b, c in zip(model['faceA'], model['faceB'], model['faceC']):
                draw.polygon([points[a], points[b], points[c]], fill='#473c29')
        x, y = index % 3 * 510, index // 3 * 350
        atlas.paste(canvas, (x, y+20))
        labels.text((x+10, y+2), f'{group}: source geometry navigation aid (not client)', fill='black')
    atlas.save(ROOT/'build/clue/maps-source/navigation-preview.png')
    print('Wrote build/clue/maps-source/navigation-preview.png; no runtime assets changed')


if __name__ == '__main__':
    main()
