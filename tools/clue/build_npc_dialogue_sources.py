"""Extract the selected NPCs' pinned trail transcript sections, retaining conditions.

Review data only. A sequence of conditional lines is never treated as dialogue
that can be played unconditionally. No runtime catalogue/cache changes.
"""
from hashlib import sha256
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / 'docs/CLUE/sources'
NAMES = ['Baraek', 'Father Aereck', 'Drezel', 'Wizard Traiborn', 'Luthas', 'Vannaka',
         'Captain Tobias', 'Doomsayer', 'Martin Thwait', 'Weird Old Man', 'Mandrith',
         'Old crone', 'Gunnjorn', 'Lumbridge Guide', 'Radimus Erkle']


def main():
    pins = {r['page']: r for r in json.loads((SOURCES/'manifest.json').read_text(encoding='utf8'))['pages'] if 'file' in r}
    records = []
    for name in NAMES:
        pin = pins['Transcript:' + name]
        raw = (SOURCES/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256'], pin['file']
        text = raw.decode('utf8')
        start = re.search(r'(?im)^==[^=\n]*(?:treasure|clue)[^=\n]*==', text)
        assert start, name
        section = text[start.end():]
        end = re.search(r'(?m)^==[^=\n]+==\s*$', section)
        if end:
            section = section[:end.start()]
        lines = [dict(depth=len(m[1]), speaker=m[2], text=m[3]) for m in
                 re.finditer(r"(?m)^(\*+) '''([^']+):''' (.*)$", section)]
        assert lines, name
        records.append(dict(npc=name, source=pin, section=section.strip(), lines=lines,
                            incomplete='{{tmissing' in section,
                            activation='research; runtime must preserve these conditions'))
    out = ROOT/'docs/CLUE/NPC-DIALOGUE-SOURCES.json'
    out.write_text(json.dumps(records, indent=2, ensure_ascii=False)+'\n', encoding='utf8')
    print(len(records), 'pinned NPC clue transcript sections; incomplete:', [r['npc'] for r in records if r['incomplete']])


if __name__ == '__main__':
    main()
