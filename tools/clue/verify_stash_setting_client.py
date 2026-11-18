"""Compile only the actual unmodified Component decoder and three setting fixtures."""
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLIENT = Path('C:/RSPS/2011scape-client/2011scape-client')
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')


def main():
    out = ROOT/'build/clue/stash-setting-client'
    out.mkdir(parents=True, exist_ok=True)
    annotations = next((Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1/org.openrs2/openrs2-deob-annotations').rglob('*.jar'))
    cp = ';'.join(str(p) for p in [CLIENT/'runescape/build/classes/java/main', annotations])
    subprocess.run([str(JAVA/'javac.exe'), '-cp', cp, '-d', str(out), str(CLIENT/'runescape/src/main/java/Component.java'),
        str(ROOT/'tools/clue/ClueStashSettingClientRegression.java')], check=True, cwd=ROOT, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    subprocess.run([str(JAVA/'java.exe'), '-Xmx128m', '-cp', str(out)+';'+cp, 'ClueStashSettingClientRegression',
        str(ROOT/'build/clue/compile/stash-setting-tests/fixtures')], check=True, cwd=ROOT, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)


if __name__ == '__main__':
    main()
