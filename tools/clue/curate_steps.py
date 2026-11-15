"""Explicit limited CLUE selection, pinned to the already reviewed Wiki archive.

This is research data, not an activation switch. Missing target assets, subtype
handlers and loaded-client reachability must be resolved before registration.
No source row is silently replaced by a donor's similarly named step.
"""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import json
import re

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
        "Search a bookcase in Lumbridge Swamp", "Search the chest in the Duke of Lumbridge's bedroom",
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


def main():
    archive = json.loads((ROOT / "docs/CLUE/steps-source.json").read_text(encoding="utf-8"))
    index = {row["key"]: row for row in archive}
    manifest = json.loads((SOURCES / "step-manifest.json").read_text(encoding="utf-8"))
    pins = {row["file"]: row for row in manifest["pages"] if "file" in row}
    guide_manifest = json.loads((SOURCES / "manifest.json").read_text(encoding="utf-8"))
    guide_pins = {row["file"]: row for row in guide_manifest["pages"] if "file" in row}
    result = []
    for tier, names in SELECTION.items():
        assert len(names) == (15 if tier in ("easy", "medium", "hard") else 12)
        for name in names:
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
            elif "[[puzzle box]]" in notes.lower():
                challenge = dict(type="SLIDING_PUZZLE")
            if row["kind"] == "COORDINATE":
                challenge = dict(type={"medium": "NONE", "hard": "WIZARD", "elite": "GUARD", "master": "WIZARDS"}[tier])
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
                               activation="pending target assets, subtype handler and client verification"))
    assert len(result) == 69 and len({r["key"] for r in result}) == 69
    destination = ROOT / "docs/CLUE/STEPS-CURATED.json"
    destination.write_text(json.dumps(result, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")
    print(f"{len(result)} source candidates; {Counter(r['tier'] for r in result)}")
    print("Research only: no runtime registration or cache writes.")


if __name__ == "__main__":
    main()
