"""Extract exact reward probabilities and cache IDs; rejects unsupported wiki syntax.

Inputs are attributed in docs/CLUE/sources/manifest.json. No random rates, guessed
item IDs, or normalization of an incomplete probability distribution are allowed.
"""
from collections import defaultdict
from fractions import Fraction
from pathlib import Path
import ast
import base64
import csv
import json
import re
import struct
import sys
import yaml

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "build/clue"
SOURCES = ROOT / "docs/CLUE/sources"
OUT = ROOT / "data/cfg/clue"
TIERS = ("beginner", "easy", "medium", "hard", "elite", "master")
TARGET_CACHE = Path("C:/RSPS/game/game/data/cache")


def arithmetic(s):
    def visit(n):
        if isinstance(n, ast.Constant) and isinstance(n.value, int):
            return Fraction(n.value)
        if isinstance(n, ast.BinOp):
            a, b = visit(n.left), visit(n.right)
            if isinstance(n.op, ast.Add): return a + b
            if isinstance(n.op, ast.Sub): return a - b
            if isinstance(n.op, ast.Mult): return a * b
            if isinstance(n.op, ast.Div): return a / b
        raise ValueError(f"Unsupported expression: {s}")
    return visit(ast.parse(s.strip(), mode="eval").body)


def probability(text):
    # The Wiki rounds displayed inverse rates, not the underlying slot fractions.
    text = text.replace(" round 1", "")
    while "{{#expr:" in text:
        text = re.sub(r"\{\{#expr:([^{}]+)}}", lambda m: f"({arithmetic(m[1])})", text)
    return arithmetic(text)


def split_template(s):
    # Only split top-level pipes; #expr, Refn and [[links|labels]] contain pipes.
    result, start, braces, links = [], 0, 0, 0
    i = 0
    while i < len(s):
        pair = s[i:i+2]
        if pair == "{{": braces += 1; i += 2; continue
        if pair == "}}": braces -= 1; i += 2; continue
        if pair == "[[": links += 1; i += 2; continue
        if pair == "]]": links -= 1; i += 2; continue
        if s[i] == "|" and braces == 0 and links == 0:
            result.append(s[start:i]); start = i + 1
        i += 1
    result.append(s[start:])
    return result


def reward_rows(tier):
    s = (SOURCES / f"Reward_casket_({tier}).wiki").read_text(encoding="utf-8")
    rows = []
    section = ""
    for line_number, line in enumerate(s.splitlines(), 1):
        if line.startswith("===Tertiary"):
            break  # Separate once-per-casket rules, never part of a loot roll.
        if line.startswith("=="):
            section = line.strip("= ")
        if not line.startswith("{{DropsLineReward|") or section == "Mimic rewards":
            continue
        fields = dict(p.split("=", 1) for p in split_template(line[2:-2])[1:] if "=" in p)
        amount = fields["quantity"]
        match = re.fullmatch(r"(\d+)(?:-(\d+))?( \(noted\))?", amount)
        if match is None:
            raise ValueError((tier, fields))
        chance = probability(fields["rarity"])
        rows.append(dict(name=fields["name"], minimum=int(match[1]), maximum=int(match[2] or match[1]),
                         noted=bool(match[3]), numerator=chance.numerator, denominator=chance.denominator,
                         section=section, sourceLine=line_number))
    return rows


def canonical(name):
    name = name.lower().replace("’", "'")
    # Documented revision spelling changes, not substitutions of different items.
    name = {"black shortsword": "black sword", "zombie head (treasure trails)": "zombie head",
            "tooth half of a key": "tooth half of key", "loop half of a key": "loop half of key",
            "rune bar": "runite bar", "dragon bracelet": "dragonstone bracelet",
            "grimy ranarr weed": "grimy ranarr", "torstol": "clean torstol",
            "blue wizard robe": "wizard robe", "blue wizard hat": "wizard hat",
            "antifire potion(4)": "antifire(4)", "willow comp bow": "willow composite bow",
            "yew comp bow": "yew composite bow", "magic comp bow": "magic composite bow"}.get(name, name)
    name = re.sub(r"(\w) \(([1-6])\)", r"\1(\2)", name)
    return name.replace("3rd age", "third-age").replace("third age", "third-age").replace("axe", "hatchet")


def export_local():
    sys.path.insert(0, "C:/RSPS/tools/cache667")
    from js5 import Cache
    from items import decode
    # Worktree cache copies are not kept in sync with Claude's imports. Read the
    # canonical target, in rb mode, and reject an inventory taken across a write.
    marker = TARGET_CACHE / "main_file_cache.dat2"
    before = (marker.stat().st_size, marker.stat().st_mtime_ns)
    cache = Cache(str(TARGET_CACHE))
    out = {}
    try:
        for group, ids in cache.reference(19).items():
            for file in ids:
                id = group * 256 + file
                data = cache.file(19, group, file)
                if not data:
                    continue
                row = decode(data)
                out[id] = row
    finally:
        cache.dat.close()
    assert before == (marker.stat().st_size, marker.stat().st_mtime_ns), "Target cache changed during inventory; retry after the transaction"
    (BUILD / "local-items.json").write_text(json.dumps(out), encoding="utf-8")
    (BUILD / "local-cache-provenance.json").write_text(json.dumps(dict(path=str(TARGET_CACHE), bytes=before[0], mtime_ns=before[1]), indent=2), encoding="utf-8")
    return out


def enum_items(encoded):
    data = base64.b64decode(encoded)
    pos, values = 0, []
    while pos < len(data):
        opcode = data[pos]; pos += 1
        if opcode == 0:
            break
        if opcode in (1, 2):
            pos += 1
        elif opcode == 3:
            pos = data.index(0, pos) + 1
        elif opcode == 4:
            pos += 4
        elif opcode in (5, 6):
            count = struct.unpack_from('>H', data, pos)[0]; pos += 2
            for _ in range(count):
                pos += 4  # key
                if opcode == 5:
                    pos = data.index(0, pos) + 1
                else:
                    values.append(struct.unpack_from('>i', data, pos)[0]); pos += 4
        else:
            raise ValueError((opcode, pos))
    return values


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    source = {int(id): row for id, row in json.loads((BUILD / "osrs-items.json").read_text()).items()}
    local = ({int(k): {(int(field) if field.isdigit() else field): value for field, value in v.items()}
              for k, v in json.loads((BUILD / "local-items.json").read_text()).items()}
             if (BUILD / "local-items.json").exists() and (BUILD / "local-cache-provenance.json").exists() and "--refresh-local" not in sys.argv else export_local())
    ledger = yaml.load(Path("C:/RSPS/OSRS_IMPORT_MASTER.yml").read_text(encoding="utf-8"), Loader=yaml.CSafeLoader)
    imported = {r["upstream_item_id"]: r["local_item_id"] for r in ledger["imports"]
                if "upstream_item_id" in r and "local_item_id" in r}
    by_name = defaultdict(list)
    local_by_name = defaultdict(list)
    for id, row in source.items():
        if row["notedTemplate"] < 0 and row["placeholderTemplateId"] < 0 and row["boughtTemplateId"] < 0 and 59 not in row["params"] and "59" not in row["params"]:
            by_name[canonical(row["name"])].append(id)
    for id, row in local.items():
        if not row.get(98):
            local_by_name[canonical(row.get("name") or "")].append(id)
    rewards, missing, ambiguous = {}, set(), {}
    ids = {}
    for tier in TIERS:
        rows = reward_rows(tier)
        total = sum((Fraction(r["numerator"], r["denominator"]) for r in rows), Fraction())
        print(tier, len(rows), "probability total", total, float(total))
        for row in rows:
            candidates = by_name[canonical(row["name"])]
            # Shared pre-OSRS items sometimes have several cache copies (e.g. sweets).
            # Overrides are exact Wiki-infobox/cache identities, never guessed numbers.
            overrides = {"Purple sweets": 10476, "Master scroll book (empty)": 21387,
                         "Coins": 995, "Zombie head (Treasure Trails)": 19912}
            upstream = overrides.get(row["name"])
            if upstream is None:
                exact = [id for id in candidates if source[id]["name"].casefold() == row["name"].casefold()]
                tradeable = [id for id in candidates if source[id]["geTradeable"]]
                if len(exact) == 1:
                    upstream = exact[0]
                elif len(tradeable) == 1:
                    upstream = tradeable[0]
                elif len(candidates) == 1:
                    upstream = candidates[0]
                else:
                    ambiguous[row["name"]] = candidates
                    continue
            assert canonical(source[upstream]["name"]) == canonical(row["name"]), row
            if row["noted"]:
                upstream = source[upstream]["notedId"]
                assert upstream >= 0, row
            row["osrsId"] = upstream
            unnoted = source[upstream]["notedId"] if row["noted"] else upstream
            name = source[unnoted]["name"]
            candidates = local_by_name[canonical(name)]
            if unnoted in imported:
                target = imported[unnoted]
                assert target in local, (unnoted, target)
            elif unnoted in local and canonical(local[unnoted].get("name") or "") == canonical(name):
                target = unnoted
            elif len(candidates) == 1:
                target = candidates[0]
            else:
                target = None
            if target is not None and row["noted"]:
                target = local[target].get(97)
                if target not in local or not local[target].get(98):
                    target = None
            if target is None:
                missing.add(unnoted)
            else:
                ids[upstream] = target
        rewards[tier] = rows
    # One hard mega-rare outcome gives THREE potions together. Summing marginal
    # item probabilities would exceed 1 by 1/8125; don't normalize or roll them separately.
    for row in rewards["hard"]:
        if row["name"] in ("Super attack(4)", "Super strength(4)", "Super defence(4)"):
            row["bundle"] = "hard-combat-potions"
    for tier, rows in rewards.items():
        seen = set()
        total = Fraction()
        for row in rows:
            if row.get("bundle") in seen:
                continue
            if row.get("bundle"):
                seen.add(row["bundle"])
            total += Fraction(row["numerator"], row["denominator"])
        assert total == 1, (tier, total)
    (BUILD / "reward-name-ambiguities.json").write_text(json.dumps(ambiguous, indent=2), encoding="utf-8")
    (BUILD / "reward-rows.json").write_text(json.dumps(rewards, indent=2), encoding="utf-8")
    print("Unresolved source names", len(ambiguous), ambiguous)
    print("Missing local items", len(missing))
    (BUILD / "missing-items.json").write_text(json.dumps(sorted(missing)), encoding="utf-8")
    (BUILD / "mapped-items.json").write_text(json.dumps(ids, indent=2), encoding="utf-8")
    if ambiguous:
        raise ValueError("Resolve source identities before generating runtime data")
    (OUT / "rewards.json").write_text(json.dumps(rewards, indent=2) + "\n", encoding="utf-8")
    core = {2677:2677, 2801:2801, 2722:2722, 12073:19043,
            20546:2714, 20545:2802, 20544:2724, 20543:19039, 23184:23729}
    ids.update(core)
    # Generic clue/casket entry points and skilling/scroll-box containers come
    # directly from this cache. The four existing scroll/casket IDs stay compatible.
    extra = []
    enums = json.loads((BUILD / "osrs-enums.json").read_text())
    enum_ids = dict(beginner=2322, easy=2141, medium=2142, hard=2143, elite=2144, master=2145, shared=2146)
    log = {tier: dict(enum=id, items=enum_items(enums[str(id)])) for tier, id in enum_ids.items()}
    for page in log.values():
        extra.extend(page["items"])
    for id in extra:
        if id in ids:
            continue
        if id in imported:
            ids[id] = imported[id]
        elif id in local and canonical(local[id].get("name") or "") == canonical(source[id]["name"]):
            ids[id] = id
        else:
            missing.add(id)
    (OUT / "collection-log.json").write_text(json.dumps(log, indent=2) + "\n", encoding="utf-8")
    for id, row in source.items():
        n = row["name"]
        if row["placeholderTemplateId"] >= 0 or row["notedTemplate"] >= 0 or row["params"].get("59") or row["params"].get("295"):
            continue
        if (n.startswith(("Clue geode (", "Clue bottle (", "Clue nest (", "Scroll box (")) or
                "scroll case" in n.lower() or
                id in (23182, 23245, 19835, 19836)):
            extra.append(id)
            if id not in ids:
                if id in imported:
                    ids[id] = imported[id]
                else:
                    missing.add(id)
    (OUT / "items.tsv").write_text("# OSRS id\tlocal id\tcache name; generated from cache + OSRS_IMPORT_MASTER, not GE data\n" +
        "".join(f"{id}\t{target}\t{source[id]['name']}\n" for id, target in sorted(ids.items())), encoding="utf-8")
    (OUT / "import-items.tsv").write_text("# OSRS id\tnoted variant required\tOSRS cache name\n" +
        "".join(f"{id}\t{str(source[id]['notedId'] >= 0).lower()}\t{source[id]['name']}\n" for id in sorted(missing)), encoding="utf-8")
    print("Generated runtime reward data; import roster", len(missing), "log sizes", {k: len(v["items"]) for k, v in log.items()})


if __name__ == "__main__":
    main()
