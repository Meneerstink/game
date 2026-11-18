"""Read source dig/woodbuilding sequences and both real target pets without a cache writer."""
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    verified=Path((ROOT/'build/clue/watson-boards/latest-verify.txt').read_text().strip())
    args=[json.loads(line) for line in (verified/'libraries.args').read_text().splitlines()]
    cp=args[args.index('-cp')+1]
    out=ROOT/'build/clue/dig-source-probe';out.mkdir(parents=True,exist_ok=True)
    def run(command):subprocess.run(list(map(str,command)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    run([JAVA/'javac.exe','-cp',cp,'-d',out,ROOT/'tools/clue/ClueDigSourceProbe.java'])
    run([JAVA/'java.exe','-Xmx256m','-cp',str(out)+';'+cp,'ClueDigSourceProbe',
        'C:/RSPS/import-source/openrs2-2686/cache','C:/RSPS/game/game/data/cache','C:/RSPS/file-server/cache',out/'proof.json'])


if __name__=='__main__':main()
