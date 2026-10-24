"""Cuts sheet.png into one PNG per sprite (rects measured by Chrome, dom.html) -> out/<name>.png + out/manifest.json."""
import json, os, re
from PIL import Image
dom = open("dom.html", encoding="utf-8").read()
m = json.loads(re.search(r'<pre id="m"[^>]*>(.*?)</pre>', dom, re.S).group(1))
sheet = Image.open("sheet.png").convert("RGBA")
os.makedirs("out", exist_ok=True)
man = {}
for name, (x, y, w, h) in m.items():
    im = sheet.crop((x, y, x + w, y + h))
    # 667 sprites: a pixel is either drawn with its alpha or not at all; nearly invisible pixels go.
    px = im.load()
    for j in range(h):
        for i in range(w):
            r, g, b, a = px[i, j]
            if a < 24:
                px[i, j] = (0, 0, 0, 0)
    im.save(f"out/{name}.png")
    man[name] = [w, h]
json.dump(man, open("out/manifest.json", "w"), sort_keys=True)
print(len(man), "sprites")
print({k: v for k, v in man.items() if k.startswith("tag_")})
