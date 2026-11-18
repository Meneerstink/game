"""Prepare CLUE's narrowly scoped client codec patch. NEVER writes the client worktree."""
from pathlib import Path
from hashlib import sha256
import difflib
import json

ROOT = Path(__file__).resolve().parents[2]
CLIENT = Path('C:/RSPS/2011scape-client/2011scape-client')
RELATIVE = Path('runescape/src/main/java/Component.java')


def main():
    original_bytes = (CLIENT / RELATIVE).read_bytes()
    original = original_bytes.decode('utf-8').replace('\r\n', '\n')
    edited = original
    before = '        this.type = packet.g1();\n'
    after = before + '''        // CLUE: archive 37 models use a g4 id; the original IF3 layout stays unchanged.
        final boolean wideInterfaceModel = (this.type & 0x40) != 0;
        this.type &= ~0x40;
        if (wideInterfaceModel && (this.type & 0x7F) != TYPE_MODEL) {
            throw new IllegalArgumentException("Wide model flag on non-model component");
        }
'''
    assert edited.count(before) == 1
    edited = edited.replace(before, after)
    before = '            this.obj = packet.g2();\n            if (this.obj == 65535) {\n'
    after = '''            this.obj = wideInterfaceModel ? packet.g4() : packet.g2();
            if (wideInterfaceModel && (this.obj < 65536 || this.obj > 131071)) {
                throw new IllegalArgumentException("Interface model outside archive 37 namespace");
            }
            if (!wideInterfaceModel && this.obj == 65535) {
'''
    assert edited.count(before) == 1
    edited = edited.replace(before, after)
    before = '            @Pc(271) long key = crc & 0x3FFFFFFFFFL | (long) this.obj << 38 | (long) this.objType << 54 | (long) toolkit.index << 59;'
    after = '            @Pc(271) long key = interfaceModelCacheKey(crc, this.obj, this.objType, toolkit.index);'
    assert edited.count(before) == 1
    edited = edited.replace(before, after)
    # This cache is read/written exclusively in OBJ_TYPE_MODEL above. Its original
    # type field therefore has an unused bit 55: keep every old key byte-identical,
    # and store the new seventeenth model bit there instead of aliasing bit 54.
    helper = '''
    static long interfaceModelCacheKey(long crc, int model, int type, int toolkit) {
        if (type != OBJ_TYPE_MODEL || model < 0 || model > 131071) {
            throw new IllegalArgumentException("Invalid static interface model");
        }
        return crc & 0x3FFFFFFFFFL | (long) (model & 0xFFFF) << 38
                | (long) type << 54 | (long) (model >>> 16) << 55 | (long) toolkit << 59;
    }
'''
    anchor = '    public static final ReferenceCache modelCache = new ReferenceCache(50);\n'
    assert edited.count(anchor) == 1
    edited = edited.replace(anchor, anchor + helper)
    staged = ROOT / 'build/clue/client-patch'
    staged.mkdir(parents=True, exist_ok=True)
    (staged / 'Component.java').write_text(edited, encoding='utf-8')
    patch = ''.join(difflib.unified_diff(original.splitlines(True), edited.splitlines(True),
                                      fromfile='a/' + RELATIVE.as_posix(), tofile='b/' + RELATIVE.as_posix()))
    destination = ROOT / 'docs/CLUE/CLIENT-WIDE-INTERFACE.patch'
    destination.write_text(patch, encoding='utf-8')
    destination.with_suffix('.json').write_text(json.dumps(dict(
        source=str(CLIENT / RELATIVE), sourceSha256=sha256(original_bytes).hexdigest(),
        purpose='IF3 type flag 0x40: g4 static model, existing archive37 routing; no gameplay values',
        clientWritten=False, stagedSource=str(staged / 'Component.java')), indent=2)+'\n', encoding='utf-8')
    print('Prepared', destination, '; client worktree unchanged')


if __name__ == '__main__':
    main()
