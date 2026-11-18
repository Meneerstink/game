"""Compile the actual unmodified NPCType and read the tested server fixture, without a client window."""
from pathlib import Path
import os
import subprocess

ROOT=Path(__file__).resolve().parents[2]
CLIENT=Path('C:/RSPS/2011scape-client/2011scape-client')
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    out=ROOT/'build/clue/watson-client';out.mkdir(parents=True,exist_ok=True)
    annotations=next((Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1/org.openrs2/openrs2-deob-annotations').rglob('*.jar'))
    cp=';'.join(str(p) for p in [CLIENT/'runescape/build/classes/java/main',annotations])
    source=CLIENT/'runescape/src/main/java/com/jagex/game/runetek6/config/npctype/NPCType.java'
    subprocess.run([str(JAVA/'javac.exe'),'-cp',cp,'-d',str(out),str(source),str(ROOT/'tools/clue/ClueWatsonClientRegression.java')],
        check=True,cwd=ROOT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    subprocess.run([str(JAVA/'java.exe'),'-Xmx128m','-cp',str(out)+';'+cp,'ClueWatsonClientRegression',
        str(ROOT/'build/clue/compile/watson-tests/fixtures/watson-root.bin')],check=True,cwd=ROOT,
        creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)


if __name__=='__main__':main()
