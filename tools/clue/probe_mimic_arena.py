"""Compile/read the pinned arena fixture; never opens a target cache writer."""
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    out=ROOT/'build/clue/mimic-arena/source';out.mkdir(parents=True,exist_ok=True)
    cp=';'.join(str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar'))
    def run(args):subprocess.run(list(map(str,args)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    run([JAVA/'javac.exe','-cp',cp,'-d',out,ROOT/'tools/clue/ClueMimicArenaSourceProbe.java'])
    run([JAVA/'java.exe','-Xmx256m','-cp',str(out)+';'+cp,'ClueMimicArenaSourceProbe',
         'C:/RSPS/import-source/openrs2-2686/cache','C:/RSPS/game/game/data/cache','C:/RSPS/file-server/cache',
         ROOT/'docs/CLUE/cache/mimic-arena-source.json'])


if __name__=='__main__':main()
