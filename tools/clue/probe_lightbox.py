"""Read cache2686's light-box scripts/items into review artifacts. No target-cache API."""
from pathlib import Path
from hashlib import sha256
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'build/clue/lightbox/source'
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    cp = ';'.join([str(ROOT/'game/build/classes/kotlin/main'),
                   str(ROOT/'game/build/libs/game-0.0.4.jar'), str(ROOT/'game/build/install/game/lib/*')])
    def run(args):
        subprocess.run(list(map(str,args)), cwd=ROOT, check=True, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    run([JAVA/'javac.exe', '-cp', cp, '-d', OUT, ROOT/'tools/clue/ClueScriptProbe.java', ROOT/'tools/clue/ClueLightBoxProbe.java'])
    cp = str(OUT)+';'+cp
    source = 'C:/RSPS/import-source/openrs2-2686/cache'
    run([JAVA/'java.exe','-Xmx512m','-cp',cp,'ClueScriptProbe',source,
         ROOT/'docs/CLUE/cache/lightbox-scripts.json','228,191,8303,1333,1334,1335,1336,1337,1338,1339,93,94,134,135,208,98,6388,1972'])
    run([JAVA/'java.exe','-Xmx512m','-cp',cp,'ClueLightBoxProbe',source,ROOT/'docs/CLUE/cache/lightbox-items.json'])
    files=['lightbox-scripts.json','lightbox-items.json']
    (ROOT/'docs/CLUE/cache/lightbox-manifest.json').write_text(json.dumps(dict(source=source,
        sha256={f:sha256((ROOT/'docs/CLUE/cache'/f).read_bytes()).hexdigest() for f in files}),indent=2)+'\n')


if __name__ == '__main__':main()
