"""Execute native q8 paragraph layout for every source contributor, without a window/cache writer."""
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    verified = Path((ROOT/'build/clue/watson-boards/latest-verify.txt').read_text().strip())
    args = [json.loads(line) for line in (verified/'libraries.args').read_text().splitlines()]
    native = Path('C:/RSPS/2011scape-client/2011scape-client/runescape/build/classes/java/main')
    cp = str(native)+';'+args[args.index('-cp')+1]
    out = ROOT/'build/clue/board-fonts'
    out.mkdir(parents=True,exist_ok=True)
    def run(command):
        subprocess.run(list(map(str,command)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    run([JAVA/'javac.exe','-cp',cp,'-d',out,ROOT/'tools/clue/ClueBoardFontRegression.java'])
    run([JAVA/'java.exe','-Xmx128m','-cp',str(out)+';'+cp,'ClueBoardFontRegression',
         ROOT/'data/cfg/clue/watson-contributors.json','C:/RSPS/game/game/data/cache',out/'proof.json'])


if __name__=='__main__':
    main()
