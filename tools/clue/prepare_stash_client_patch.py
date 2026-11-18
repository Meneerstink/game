"""Stage the exact wide-STASH decoder; never change the canonical client worktree."""
from hashlib import sha256
from pathlib import Path
import difflib
import json

ROOT = Path(__file__).resolve().parents[2]
CLIENT = Path('C:/RSPS/2011scape-client/2011scape-client')
RELATIVE = Path('runescape/src/main/java/com/jagex/game/runetek6/config/loctype/LocType.java')


def main():
    raw = (CLIENT / RELATIVE).read_bytes()
    original = raw.decode('utf8').replace('\r\n', '\n')
    before = '        } else if (code == 78) {\n'
    after = '''        } else if (code == 242) {
            // CLUE: 32-bit destination IDs for appended STASH scenery; old 77/92 stay unchanged.
            this.multivarbit = packet.g2();
            if (this.multivarbit == 65535) this.multivarbit = -1;
            this.multivarp = packet.g2();
            if (this.multivarp == 65535) this.multivarp = -1;
            int fallback = packet.g4();
            int count = packet.g1();
            this.multiloc = new int[count + 2];
            for (int i = 0; i <= count; i++) {
                this.multiloc[i] = packet.g4();
                if (this.multiloc[i] < -1) throw new IllegalArgumentException("Invalid wide loc transform");
            }
            if (fallback < -1) throw new IllegalArgumentException("Invalid wide loc fallback");
            this.multiloc[count + 1] = fallback;
''' + before
    assert original.count(before) == 1, 'Client decoder changed; review the current source'
    edited = original.replace(before, after)
    staged = ROOT / 'build/clue/client-stash-patch/LocType.java'
    staged.parent.mkdir(parents=True, exist_ok=True)
    staged.write_text(edited, encoding='utf8')
    destination = ROOT / 'docs/CLUE/CLIENT-WIDE-STASH.patch'
    destination.write_text(''.join(difflib.unified_diff(original.splitlines(True), edited.splitlines(True),
        fromfile='a/'+RELATIVE.as_posix(), tofile='b/'+RELATIVE.as_posix())), encoding='utf8')
    destination.with_suffix('.json').write_text(json.dumps(dict(source=str(CLIENT/RELATIVE), sourceSha256=sha256(raw).hexdigest(),
        opcode=242, purpose='u16 selectors + signed g4 fallback/destinations; no change to old 77/92',
        clientWritten=False, stagedSource=str(staged)), indent=2)+'\n', encoding='utf8')
    print('Prepared', destination, '; canonical client untouched')


if __name__ == '__main__':
    main()
