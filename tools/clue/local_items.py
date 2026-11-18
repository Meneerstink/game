"""Read-only target items for CLUE; mirrors ItemDef/OsrsItemImportTool including opcode 240.

Based on C:/RSPS/tools/cache667/items.py, extended locally because the canonical
cache now contains the project's wide inventory/worn/head model encoding.
No cache writer is imported. Model IDs are retained as ints, never truncated.
"""
from items import Buf
import struct


def decode(raw):
    row = dict(name=None, inv=[None]*5, ground=[None]*5, params={}, stacks=False, tradeable=False)
    r = Buf(raw)
    while True:
        op = r.u8()
        if op == 0:
            break
        if op in (1,4,5,6,7,8,24,26,78,79,90,91,92,93,95,110,111,112,139,140):
            value = r.u16()
            if op == 1: row['model'] = value
            if op in (139,140): row[op] = value
            if op in (90,91,92,93): row[f'head{op}'] = value
        elif op == 240:
            field, value = r.u8(), r.i32()
            if field == 1: row['model'] = value
            elif field in (23,25,78,79): row[f'wear{field}'] = value
            elif field in (90,91,92,93): row[f'head{field}'] = value
            elif field in (24,26): row[f'wear{field}'] = value
            else: raise ValueError(f'Unsupported wide model field {field}')
        elif op == 2: row['name'] = r.s()
        elif op == 11: row['stacks'] = True
        elif op == 65: row['tradeable'] = True
        elif op == 16: pass
        elif op == 12: row['cost'] = r.i32()
        elif op == 18: r.u16()
        elif op in (23,25): row[f'wear{op}'] = r.u16()
        elif 30 <= op < 35: row['ground'][op-30] = r.s()
        elif 35 <= op < 40: row['inv'][op-35] = r.s()
        elif op in (40,41):
            count = r.u8(); r.p += 4*count
        elif op == 42:
            count = r.u8(); r.p += count
        elif op in (96,113,114,115,134): r.u8()
        elif op in (97,98,121,122): row[op] = r.u16()
        elif 100 <= op < 110: r.p += 4
        elif op == 124: r.p += 12
        elif op in (125,126):
            row[op] = struct.unpack('>bbb',raw[r.p:r.p+3]); r.p += 3
        elif op in (127,128,129,130): r.p += 3
        elif op == 132:
            count = r.u8(); r.p += 2*count
        elif op == 249:
            for _ in range(r.u8()):
                is_string = r.u8() == 1
                key = r.u24()
                row['params'][key] = r.s() if is_string else r.i32()
        else: raise ValueError(f'Unsupported opcode {op} at {r.p}')
    row['worn'] = [row['params'].get(528+i) for i in range(8)]
    return row
