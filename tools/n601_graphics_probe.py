"""N601 read-only ToA floor/model/bridge census. Requires the existing N462 probes.

Run from this worktree: python tools/n601_graphics_probe.py --output <notes-directory>
Both caches open rb; only evidence files in --output are written. IDs are config IDs,
not the +1 tile encoding. This script proposes no automatic material substitutions.
"""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import sys

ROOT = Path(r"C:\RSPS")
PROBES = ROOT / "team/dossiers/N462-notities/toa/probes"
sys.path.insert(0, str(PROBES))
sys.path.insert(0, str(ROOT / "tools/cache667"))
from cache_reader import Cache, Buf, jagex_hash, proof
from map_probe import terrain, floor, locs
from config_probe import config, model
from maplocs import xtea_decrypt
from sprite_png import frames, png

ROOMS = {13454: "lobby", 14160: "nexus", 14162: "scabaras", 14164: "kephri",
         14674: "het", 14676: "akkha", 15186: "apmeken", 15188: "baba",
         15698: "crondis", 15700: "zebak", 15184: "wardens12", 15696: "wardens3",
         14672: "vault", 13097: "necropolis51_41", 13098: "necropolis-west",
         13353: "necropolis52_41", 13354: "necropolis-town", 13355: "necropolis-north",
         13609: "necropolis53_41", 13610: "necropolis-coast", 13611: "necropolis-east"}


def local_floor(raw):
    b = Buf(raw)
    d = dict(rgb=0, texture=-1)
    while True:
        op = b.u8()
        if op == 0:
            b.end()
            return d
        if op == 1: d["rgb"] = b.u24()
        elif op == 2: d["texture"] = b.u8()
        elif op == 3: d["texture"] = b.u16()
        elif op in (7, 13): d[f"colour{op}"] = b.u24()
        elif op in (5, 8, 10, 12): d[f"flag{op}"] = True
        elif op == 9: d[f"op{op}"] = b.u16()
        elif op in (11, 14, 16): d[f"op{op}"] = b.u8()
        else: raise ValueError(("overlay opcode", op, raw.hex()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--global-slot-usage", action="store_true", help="also census overlay reuse outside the 21 target squares")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    def dump(name, obj):
        (args.output / name).write_text(json.dumps(obj, indent=2), encoding="utf-8")

    keys = {x["mapsquare"]: x["key"] for x in json.loads((ROOT / "game/game/data/xteas/xteas.json").read_text())}
    idmap = {}
    for line in Path("data/cfg/toa/ids.tsv").read_text().splitlines():
        fields = line.split("\t")
        if len(fields) >= 3 and fields[1].isdigit(): idmap[fields[0], int(fields[1])] = int(fields[2])
    rows, validation, native = [], [], defaultdict(list)
    textures = defaultdict(lambda: {"models": [], "locs": [], "rooms": set()})
    model_cache, loc_cache, bridge = {}, {}, {}
    with Cache(ROOT / "import-source/openrs2-2686/cache") as src, Cache(ROOT / "game/game/data/cache", modern=False) as dst, Cache(ROOT / "file-server/cache", modern=False) as files:
        source_floors = {i: {"rgb": 0, "texture": -1, **floor(raw, True)} for i, raw in src.files(2, 4).items()}
        local_floors = {i: local_floor(raw) for i, raw in dst.files(2, 4).items()}
        dump("overlay-defs.json", {"source": source_floors, "local": local_floors})
        names = {v: g for g, v in dst.reference(5)["names"].items()}

        def map_bytes(prefix, rx, rz):
            g = names.get(jagex_hash(f"{prefix}{rx}_{rz}"))
            if g is None: return None
            raw = dst.raw(5, g)
            assert raw == files.raw(5, g), f"both cache copies differ: {prefix}{rx}_{rz}"
            if prefix == "l":
                # Imported maps can be stored with zero keys; only decrypt when the
                # container is not already readable (as in Rev667RegionProbeTool).
                try:
                    return dst.decompress(raw)
                except (OSError, EOFError, ValueError):
                    pass
                key = keys[(rx << 8) | rz]
                raw = raw[:5] + xtea_decrypt(raw[5:], key)
            return dst.decompress(raw)

        for region, room in ROOMS.items():
            rx, rz = region >> 8, region & 255
            tx, tz = (rx + 9, rz + 24) if region in ROOMS and region >= 14000 else (rx, rz)
            a = terrain(src.file(5, region, 0))[0]
            local_map = map_bytes("m", tx, tz)
            assert local_map is not None, (room, tx, tz)
            b = terrain(local_map, modern=False)[0]
            for tile in b: tile["overlay"] &= 255
            validation.append(dict(room=room, region=region,
                height_byte_diffs=sum(x["height"] != y["height"] for x, y in zip(a, b)),
                bridge_flag_diffs=sum((x["settings"] & 2) != (y["settings"] & 2) for x, y in zip(a, b)),
                missing_upper_flag8=sum(x["p"] > 0 and bool(x["settings"] & 8) and not (y["settings"] & 8) for x, y in zip(a, b)),
                source_map=proof(src.file(5, region, 0)), local_map=proof(local_map)))
            counts = Counter((x["p"], x["overlay"] - 1, y["overlay"] - 1) for x, y in zip(a, b) if x["overlay"] > 0)
            for (plane, upstream, local), count in sorted(counts.items()):
                sample = next(x for x, y in zip(a, b) if (x["p"], x["overlay"] - 1, y["overlay"] - 1) == (plane, upstream, local))
                rows.append(dict(room=room, region=region, plane=plane, osrs=upstream, local=local,
                                 count=count, source=source_floors[upstream], target=local_floors.get(local),
                                 sample=[rx*64+sample["x"], rz*64+sample["y"], plane]))
            placements = locs(src.file(5, region, 1))
            for row in placements:
                pending = [row["id"]]
                seen = set()
                while pending:
                    lid = pending.pop()
                    if lid < 0 or lid in seen: continue
                    seen.add(lid)
                    if lid not in loc_cache: loc_cache[lid] = config(src.file(2, 6, lid), "loc")
                    d = loc_cache[lid]
                    pending.extend(d.get("transforms", []))
                    for mid in d.get("models", []):
                        if mid not in model_cache: model_cache[mid] = model(src.file(7, mid))
                        for tex in model_cache[mid]["textures"]:
                            # Opcode 41 retextures apply after decoding the raw model.
                            effective = dict(d.get("retexture", [])).get(tex, tex)
                            v = textures[effective]
                            if mid not in v["models"]: v["models"].append(mid)
                            if lid not in v["locs"]: v["locs"].append(lid)
                            v["rooms"].add(room)
            if region == 15700:
                nearby = [q for q in placements if 48 <= q["x"] <= 63 and 24 <= q["y"] <= 40]
                local_placements = locs(map_bytes("l", tx, tz))
                bridge = dict(source_terrain=[t for t in a if 48 <= t["x"] <= 63 and 24 <= t["y"] <= 40],
                              local_terrain=[t for t in b if 48 <= t["x"] <= 63 and 24 <= t["y"] <= 40],
                              source_locs=[dict(q, definition=loc_cache[q["id"]], localId=idmap.get(("loc", q["id"]))) for q in nearby],
                              local_locs=[q for q in local_placements if 48 <= q["x"] <= 63 and 24 <= q["y"] <= 40],
                              source_map_proof=proof(src.file(5, region, 0)), local_map_proof=proof(local_map))
            print(room, "overlay rows", len(counts), "placements", len(placements), flush=True)

        # Native material provenance: desert towns, Pyramid Plunder, desert dungeons.
        native_squares = set((x, z) for x in range(49, 56) for z in range(43, 51))
        native_squares.update((x, z) for x in range(29, 32) for z in range(68, 71))
        native_squares.update((x, z) for x in range(49, 56) for z in range(139, 151))
        for rx, rz in sorted(native_squares):
            raw = map_bytes("m", rx, rz)
            if raw is None: continue
            tiles = terrain(raw, modern=False)[0]
            for tile in tiles: tile["overlay"] &= 255
            count = Counter((t["overlay"] - 1, t["p"]) for t in tiles if t["overlay"] > 0)
            for (overlay, p), n in sorted(count.items()):
                sample = next(t for t in tiles if t["overlay"] - 1 == overlay and t["p"] == p)
                native[overlay].append(dict(square=f"{rx}_{rz}", plane=p, count=n,
                                            sample=[rx*64+sample["x"], rz*64+sample["y"], p]))
        texture_defs = {}
        sprite_dir = args.output / "textures"
        sprite_dir.mkdir(exist_ok=True)
        for tid, v in sorted(textures.items()):
            v["rooms"] = sorted(v["rooms"])
            raw = src.file(9, 0, tid)
            assert len(raw) == 7, (tid, raw.hex())
            sprite = int.from_bytes(raw[:2], "big")
            width, height, ox, oy, crop_w, crop_h, indices, alpha, palette = frames(src.file(8, sprite))[0]
            canvas = bytearray(width * height * 4)
            for y in range(crop_h):
                for x in range(crop_w):
                    i = y * crop_w + x
                    k = indices[i]
                    colour = palette[k]
                    a = (alpha[i] if alpha else 255) if k else 0
                    at = ((y+oy) * width + x+ox) * 4
                    canvas[at:at+4] = bytes([(colour >> 16) & 255, (colour >> 8) & 255, colour & 255, a])
            png(str(sprite_dir / f"osrs-texture-{tid}.png"), width, height, canvas)
            texture_defs[tid] = dict(sprite=sprite, colour=int.from_bytes(raw[2:4], "big"),
                                     flag4=raw[4], direction=raw[5], speed=raw[6],
                                     sprite_layout=dict(canvas=[width, height], offset=[ox, oy], crop=[crop_w, crop_h],
                                                        zero_index_pixels=indices.count(0)), **v)
        current_models = {}
        for lid in sorted({lid for v in texture_defs.values() for lid in v["locs"]}):
            local_id = idmap.get(("loc", lid))
            if local_id is None:
                current_models[str(lid)] = {"unmapped": True}
                continue
            raw = dst.file(16, local_id >> 8, local_id & 255)
            b = Buf(raw)
            opcode = b.u8()
            assert opcode in (1, 241), (lid, local_id, "unexpected local model prefix", opcode)
            target_groups = {}
            for _ in range(b.u8()):
                shape = b.u8()
                target_groups[shape] = [(b.u32() if opcode == 241 else b.u16()) for _ in range(b.u8())]
            d = loc_cache[lid]
            source_groups = defaultdict(list)
            for i, mid in enumerate(d["models"]):
                source_groups[(d.get("modelTypes") or [10]*len(d["models"]))[i]].append(mid)
            assert {k: len(v) for k, v in source_groups.items()} == {k: len(v) for k, v in target_groups.items()}, (lid, "model group mismatch")
            pairs = []
            for shape, mids in source_groups.items():
                for upstream, local in zip(mids, target_groups[shape]):
                    payload = dst.file(37 if local >= 65536 else 7, local-65536 if local >= 65536 else local)
                    target = model(payload)
                    pairs.append(dict(source=upstream, local=local, shape=shape, source_textures=model_cache[upstream]["textures"],
                                      local_textures=target["textures"], local_proof=proof(payload),
                                      source_vertices=model_cache[upstream]["vertices"], source_faces=model_cache[upstream]["faces"],
                                      local_vertices=target["vertices"], local_faces=target["faces"]))
            current_models[str(lid)] = dict(local_loc=local_id, models=pairs)
        # Plain sprite programs can be compared as source images. Procedural programs
        # need the client renderer; never label their first embedded sprite as output.
        programs = {}
        for tid in dst.reference(9)["files"]:
            raw = dst.file(9, tid)
            if len(raw) >= 8 and raw[2] == 39 and raw[4:6] == bytes([1, 0]):
                programs[tid] = dict(sprite=int.from_bytes(raw[6:8], "big"), program=raw.hex())
        dump("floor-census.json", rows)
        dump("map-validation.json", validation)
        dump("native-overlay-usage.json", native)
        dump("object-textures.json", texture_defs)
        dump("current-object-models.json", current_models)
        dump("source-loc-defs.json", loc_cache)
        dump("source-models.json", model_cache)
        dump("zebak-bridge.json", bridge)
        dump("native-sprite-textures.json", programs)
        if args.global_slot_usage:
            own = {(r >> 8, r & 255) if r < 14000 else ((r >> 8) + 9, (r & 255) + 24) for r in ROOMS}
            usage = defaultdict(list)
            scanned = 0
            for rx in range(256):
                for rz in range(256):
                    group = names.get(jagex_hash(f"m{rx}_{rz}"))
                    if group is None: continue
                    raw = dst.file(5, group)
                    at = 0
                    counts = Counter()
                    for _ in range(16384):
                        while True:
                            op = raw[at]; at += 1
                            if op == 0: break
                            if op == 1:
                                at += 1
                                break
                            if op <= 49:
                                overlay = raw[at]; at += 1
                                if overlay: counts[overlay-1] += 1
                    for overlay, n in counts.items():
                        usage[overlay].append(dict(square=f"{rx}_{rz}", count=n, target=(rx, rz) in own))
                    scanned += 1
            dump("global-overlay-usage.json", dict(scanned_squares=scanned, usage=usage))
            print("GLOBAL_OVERLAY_USAGE", scanned, "squares", flush=True)
    print("COMPLETE", len(rows), "floor rows;", len(textures), "object textures;", len(programs), "native sprite programs", flush=True)


if __name__ == "__main__":
    main()
