"""Read-only CLUE NPC census; based on tools/cache667/npcs.py.
Mirrors current NpcDef option opcodes 150..154 and sound/binding payload lengths.
Read-only revision-667 npc definition reader (models, name, options, size, BAS).

Usage:
  python npcs.py <id> [id...]      models, name, ops, size
  python npcs.py grep <substring>  npcs whose name contains the substring
"""
import struct, sys
from js5 import Cache

NPC_INDEX = 18


def decode(b):
    d = {"name": None, "models": [], "ops": [None] * 5, "heads": [], "size": 1, "recol": [], "bas": -1}
    p = 0

    def u8():
        nonlocal p
        v = b[p]; p += 1; return v

    def u16():
        nonlocal p
        v = struct.unpack(">H", b[p:p + 2])[0]; p += 2; return v

    def s():
        nonlocal p
        e = b.index(0, p)
        v = b[p:e].decode("latin-1"); p = e + 1; return v

    while p < len(b):
        op = u8()
        if op == 0:
            break
        if op == 1:
            n = u8(); d["models"] = [u16() for _ in range(n)]
        elif op == 2:
            d["name"] = s()
        elif op == 12:
            d["size"] = u8()
        elif 30 <= op < 35 or 150 <= op < 155:
            d["ops"][op - (30 if op < 35 else 150)] = s()
        elif op == 40:
            n = u8(); d["recol"] = [(u16(), u16()) for _ in range(n)]
        elif op == 41:
            n = u8(); [u16() for _ in range(n * 2)]
        elif op == 42:
            n = u8(); p += n
        elif op in (241, 242):  # 78legacy N462 wide model / head-model ids: count + 4-byte ids (client NPCType 241/242)
            n = u8(); ids = [(u16() << 16) | u16() for _ in range(n)]
            d["models" if op == 241 else "heads"] = ids
        elif op == 60:
            n = u8(); d["heads"] = [u16() for _ in range(n)]
        elif op in (93, 99, 107, 109, 111, 141, 143, 158, 159, 162):
            pass
        elif op in (95, 97, 98, 102, 103, 114, 122, 123, 137, 138, 139, 142):
            v = u16()
            if op == 95: d["level"] = v
            if op == 142: d["mapelement"] = v
            if op in (97, 98): d["scale%d" % op] = v
        elif op == 127:
            d["bas"] = u16()
        elif op == 100 or op == 101 or op == 125 or op == 128 or op == 140 or op == 163 or op == 165 or op == 168:
            if op in (100, 101): d["light%d" % op] = u8()
            else: p += 1
        elif op in (106, 118):
            u16(); u16()
            if op == 118: u16()
            n = u8(); [u16() for _ in range(n + 1)]
        elif op == 113:
            u16(); u16()
        elif op == 119:
            p += 1
        elif op == 121:
            n = u8(); p += n * 4
        elif op == 134:
            p += 9
        elif op == 135 or op == 136:
            p += 3
        elif op == 155:
            p += 4
        elif op == 160:
            n = u8(); p += n * 2
        elif op == 164:
            p += 4
        elif op == 115:
            p += 2
        elif op == 249:
            n = u8()
            for _ in range(n):
                string = u8(); p += 3
                if string: s()
                else: p += 4
        else:
            d["stopped_at"] = op
            break
        if op == 123 or op == 137:
            pass
    return d


