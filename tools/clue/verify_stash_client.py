"""Compile only staged LocType and test real server fixtures without launching a client."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CLIENT = Path('C:/RSPS/2011scape-client/2011scape-client')
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
OUT = ROOT / 'build/clue/client-stash-patch'


def main():
    subprocess.run(['python', ROOT/'tools/clue/prepare_stash_client_patch.py'], check=True, cwd=ROOT)
    annotations = next((Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1/org.openrs2/openrs2-deob-annotations').rglob('*.jar'))
    cp = ';'.join(str(p) for p in [CLIENT/'runescape/build/classes/java/main', annotations])
    subprocess.run([str(JAVA/'javac.exe'), '-cp', cp, '-d', str(OUT), str(OUT/'LocType.java'),
                    str(ROOT/'tools/clue/ClueWideStashRegression.java')], check=True, cwd=ROOT,
                    creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    subprocess.run([str(JAVA/'java.exe'), '-Xmx128m', '-cp', str(OUT)+';'+cp, 'ClueWideStashRegression',
                    str(ROOT/'build/clue/compile/stash-import-tests/fixtures'), str(ROOT/'build/clue/stash-preflight/fixtures')],
                    check=True, cwd=ROOT, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)


if __name__ == '__main__':
    main()
