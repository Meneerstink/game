"""Explicit limited CLUE selection, pinned to the already reviewed Wiki archive.

This is research data, not an activation switch. Missing target assets, subtype
handlers and loaded-client reachability must be resolved before registration.
No source row is silently replaced by a donor's similarly named step.
"""
from collections import Counter
import argparse
from hashlib import sha256
from pathlib import Path
import json
import re
from build_step_catalogue import templates

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "docs/CLUE/sources"

# Exact page suffixes. The old donor's incorrect tier/equipment is never reused.
SELECTION = {
    "easy": [
        "Dance at the crossroads north of Draynor", "Headbang in the mine north of Al Kharid",
        "Cheer at the Druids' Circle", "Clap on the causeway to the Wizards' Tower",
        "Jump for joy at the beehives", "At the standing stones north of Falador",
        "Brother Galahad's house", "Directly behind the Wizards' Tower",
        "Dig between some ominous stones in Falador", "Dig near some giant mushrooms",
        "I wonder how many bronze shortswords he has handed out", "One of the sailors in Port Sarim",
        "Search the crate in Rommiks crafting shop in Rimmington", "Search for a crate in Varrock Castle",
        "Search the bucket in the Port Sarim jail",
    ],
    "medium": [
        "Cheer in the Edgeville general store", "Shrug in Catherby bank",
        "Yawn in the Castle Wars lobby", "Spin on the bridge by the Barbarian Village",
        "00.13S 13.58E", "00.20S 23.15E", "00.30N 24.16E",
        "A BAKER", "HALT US", "AREA CHEF TREK", "GUHCHO", "XJABSE USBJCPSO",
        "South of Draynor Village bank", "Behind the Chemist's house in Rimmington",
        "North of the Tower of Life",
    ],
    "hard": [
        "Salute in the banana plantation", "Blow a raspberry in the Fishing Guild bank",
        "Yawn in the rogues' general store", "01.30N 20.01E", "02.39N 29.58E",
        "02.48N 08.05E", "BLUE GRIM GUIDED", "DO SAY MORE", "RAT MAT WITHIN",
        "IWPPLQTP", "ZHLUG ROG PDQ", "Yanille anvils", "A crate in the Lumber Yard",
        "46 is my number", "Aggie I see",
    ],
    "elite": [
        "Bow upstairs in the Edgeville Monastery", "Salute in the Warriors' guild bank",
        "Laugh in front of the gem store in Ardougne market", "02.35S 11.52E", "05.07S 13.26E",
        "DR HITMAN", "COOL NERD", "W marks the spot", "Three rule below and three sit at top",
        "South-west of Tree Gnome Village", "Ghostly bones", "Show this to Sherlock",
    ],
    "master": [
        "Show your anger at the Wise old man", "Clap in the magic axe hut", "03.26N 12.18E",
        "03.50N 09.07E", "Anger Abbot Langley", "Robin wishes to see your finest ranged equipment",
        "@Mandrith", "@Radimus", "Falo the bard wants to see you",
        "A massive battle rages beneath",
        # Sherlock and normal Jorral hot/cold have guide entries, not individual Clue-info pages.
        "@Sherlock", "@Jorral",
    ],
}

# Retain the old pool through the same source handlers, with OSRS tiers,
# equipment and second emotes. These six non-overlapping originals need no new
# item identity. Other original maps/emotes are audited separately, never
# restored with the donor's every-tier registration or its incorrect gear.
LEGACY_SELECTION = {
    "easy": ["Dance a jig by the entrance to the Fishing Guild", "Dance in the shack in Lumbridge Swamp",
             "Dance in the Party Room", "Cheer for the monks at Port Sarim"],
    "medium": ["Cheer in the Barbarian Agility Arena", "Jump for joy in Yanille bank"],
}


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--original-maps', dest='original_maps', action='store_true')
    mode.add_argument('--base-only', dest='original_maps', action='store_false')
    parser.set_defaults(original_maps=True)
    parser.add_argument('--output', type=Path, default=ROOT/'docs/CLUE/STEPS-CURATED.json')
    args = parser.parse_args()
    if not args.original_maps:
        assert args.output.resolve() != (ROOT/'docs/CLUE/STEPS-CURATED.json').resolve(), 'Historical selection needs a separate research output'
    legacy = {key: list(value) for key, value in LEGACY_SELECTION.items()}
    if args.original_maps:
        from audit_original_pool import MAPS
        for tier, name in MAPS.values():
            if name not in SELECTION[tier] and name not in legacy.get(tier, []):
                legacy.setdefault(tier, []).append(name)
    archive = json.loads((ROOT / "docs/CLUE/steps-source.json").read_text(encoding="utf-8"))
    index = {row["key"]: row for row in archive}
    manifest = json.loads((SOURCES / "step-manifest.json").read_text(encoding="utf-8"))
    pins = {row["file"]: row for row in manifest["pages"] if "file" in row}
    guide_manifest = json.loads((SOURCES / "manifest.json").read_text(encoding="utf-8"))
    guide_pins = {row["file"]: row for row in guide_manifest["pages"] if "file" in row}
    result = []
    for tier, names in SELECTION.items():
        assert len(names) == (15 if tier in ("easy", "medium", "hard") else 12)
        for name in names + legacy.get(tier, []):
            if name.startswith("@"):
                anagram = name in ("@Mandrith", "@Radimus")
                file = "Treasure_Trails__Guide__Anagrams__Master.wiki" if anagram else "Treasure_Trails__Guide__Cryptic_clues__Master.wiki"
                pin = guide_pins[file]
                digest = sha256((SOURCES/file).read_bytes()).hexdigest()
                assert digest == pin["sha256"], file
                special = dict(anagram="Dim Tharn", npc="Mandrith", challenges=["LIGHT_BOX", "SLIDING_PUZZLE"]) if name == "@Mandrith" else (
                    dict(anagram="Mus Kil Reader", npc="Radimus Erkle", challenges=["LIGHT_BOX", "SLIDING_PUZZLE"]) if name == "@Radimus" else {})
                result.append(dict(tier=tier, key=f"master:{name[1:].lower()}",
                                   kind="ANAGRAM" if anagram else "SHERLOCK" if name == "@Sherlock" else "HOT_COLD",
                                   sourceFile=file, sourceSha256=digest, source=pin["url"], revision=pin["revision"],
                                   **special,
                                   activation="pending subtype handler and client verification"))
                continue
            key = f"Clue scroll ({tier}) - {name}"
            row = index[key]
            assert not row["eventSpecific"] and not row["missingSquares"], key
            assert row["kind"] == "KILL" or len(row["points"]) == 1, key
            pin = pins[row["sourceFile"]]
            digest = sha256((SOURCES/row["sourceFile"]).read_bytes()).hexdigest()
            assert digest == pin["sha256"], key
            notes = row["fields"].get("notes", "")
            challenge = {}
            question = re.search(r"Question:\s*''(.*?)''", notes, re.S)
            answer = re.search(r"Answer:\s*'''(.*?)'''", notes, re.S)
            if question or answer:
                assert question and answer, key
                challenge = dict(type="NUMBER", question=question[1], answer=answer[1])
                if name == "AREA CHEF TREK":
                    # The source's second answer is conditional on failing to save
                    # Jarvis in Halloween 2017. This event/state does not exist here;
                    # ordinary players therefore answer 19, never an unconditional 20.
                    condition = "If players failed to save [[Jarvis]] during the [[2017 Halloween event]], the answer is 20 instead of 19."
                    assert answer[1] == "19 or 20" and condition in notes, key
                    challenge.update(answer="19", sourceAnswer=answer[1],
                        unavailableEventAnswer=dict(answer=20, condition=condition))
            elif "[[puzzle box]]" in notes.lower():
                challenge = dict(type="SLIDING_PUZZLE")
            if row["kind"] == "COORDINATE":
                # The per-coordinate table is authoritative, including its
                # exceptions. The hard Emir's Arena step explicitly has NO
                # fight; both selected master steps explicitly use Brassican.
                guide = f"Treasure_Trails__Guide__Coordinates__{tier.title()}.wiki"
                guide_raw = (SOURCES/guide).read_bytes()
                guide_pin = guide_pins[guide]
                guide_hash = sha256(guide_raw).hexdigest()
                assert guide_hash == guide_pin['sha256'], guide
                def coordinates(fields):
                    parts = re.findall(r'(\d+)\s*degrees\s*(\d+)\s*minutes\s*(north|south|east|west)',
                                       fields.get('coords',''), re.I)
                    return ' '.join(f'{int(d):02}.{int(m):02}{direction[0].upper()}' for d,m,direction in parts)
                # Match the written coordinates. The Crandor table's image
                # filename says 12.16E, whereas its actual coordinates say 12.18E.
                matches = [f for f in templates(guide_raw.decode('utf8'), 'Coordinates') if coordinates(f) == name]
                assert len(matches) == 1, (key, matches)
                fight = matches[0]['fight']
                identities = {'Saradomin wizard': [2955], 'Zamorak wizard': [2954],
                              'Armadylean guard': [6587], 'Bandosian guard': [6588],
                              'Brassican Mage': [7310], 'Ancient Wizard': [7307,7308,7309]}
                names = re.findall(r'\[\[([^]|]+)', fight)
                assert (fight == 'No') != bool(names), (key, fight)
                assert all(n in identities for n in names), (key, names)
                challenge = dict(type='GUARD' if names else 'NONE',
                                 guards=[id for n in names for id in identities[n]],
                                 spawnMode='CHOICE' if len(names)>1 else 'ALL',
                                 source=dict(file=guide, sha256=guide_hash, url=guide_pin['url']))
            if row["kind"] == "EMOTE":
                second = row["fields"].get("emote2")
                if tier == "medium" and second is None:
                    # Some Wiki templates omit emote2; use their explicit clue
                    # text, never a blanket guessed second emote.
                    match = re.search(r"\. ([A-Za-z ]+) before you talk to me\.", row["hint"])
                    assert match, key
                    second = match[1]
                challenge = dict(type="DOUBLE_AGENT" if tier in ("hard", "master") else "URI",
                                 emote=row["fields"]["emote"], secondEmote=second,
                                 equipment={k: v for k, v in row["fields"].items() if k.startswith("equipment")},
                                 requirements=row["fields"].get("items", ""))
                assert (tier == "medium") == (challenge["secondEmote"] is not None), key
            # Master's cryptic subtasks belong to a three-part scroll. They are
            # candidates for its component pool, never standalone free progress.
            subtype = "THREE_PART_COMPONENT" if tier == "master" and row["kind"] == "CRYPTIC" and name != "Falo the bard wants to see you" else row["kind"]
            if name == "Show this to Sherlock":
                subtype = "SHERLOCK"
            if name == "Falo the bard wants to see you":
                subtype = "FALO"
            result.append(dict(row, subtype=subtype, sourceSha256=digest, challenge=challenge,
                               restoredOriginal=name in legacy.get(tier, []),
                               activation="pending target assets, subtype handler and client verification"))
    expected = 92 if args.original_maps else 75
    assert len(result) == expected and len({r["key"] for r in result}) == expected
    assert sum(r.get('restoredOriginal', False) for r in result) == (23 if args.original_maps else 6)
    destination = args.output
    destination.write_text(json.dumps(result, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")
    print(f"{len(result)} source candidates; {Counter(r['tier'] for r in result)}")
    print("Research only: no runtime registration or cache writes.")


if __name__ == "__main__":
    main()
