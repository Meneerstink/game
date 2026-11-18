# A6 source/runtime audit

## Number challenge: Father Aereck

The selected pinned `Clue_scroll_(medium)_-_AREA_CHEF_TREK.wiki` does not allow both answers unconditionally. Its footnote says **20 only if the player failed to save Jarvis in the 2017 Halloween event**; otherwise 19. The current generated `answers` set contains `{19,20}` and must not be treated as an unconditional accepted set. There is no Jarvis/2017 Halloween implementation or saved event-state provider in this server (targeted source/cfg search, 15 November). Therefore the supported ordinary state accepts **19**, with the absent historical state recorded explicitly. Do not invent an event completion flag or silently accept 20.

Question, answer and footnote provenance: `sources/Clue_scroll_(medium)_-_AREA_CHEF_TREK.wiki`, pinned by `sources/step-manifest.json`; the generator validates the source SHA. This is a source correction to apply after the frozen integration, not an owner question.

## STASH Settings source

`tools/clue/ClueSettingProbe.java` decodes source-only struct 6245 in cache2686, archive2/group34, with exact framing and SHA. Output `cache/stash-setting.json`: `c160459064efde53b1fb24881c3a17c85d8e12d8999e5ed327b63a177f6b1bae`.

Source fields retain the actual label **STASH units take equipped items** and help text describing inventory priority. Parameter1077=451 is retained as an uninterpreted source field; it is not proof of local varp451. Do not copy source selectors into the target namespace or claim a Settings handler from a source string. Runtime storage already reads the persistent TAKE_EQUIPPED flag; the Settings entry/visual route remains required.

Reproduce:

```powershell
& 'C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe' -Xmx256m -cp 'game/build/install/game/lib/*' tools/clue/ClueSettingProbe.java C:/RSPS/import-source/openrs2-2686/cache docs/CLUE/cache/stash-setting.json 6245
```

No target cache was opened, no apply or CacheTransaction was used. The actual probe completed successfully and refuses unknown opcodes/trailing bytes.

## Puzzle source identities (B, 15 November 15:06; research, no activation)

Read-only source script exports:

- `cache/puzzle-extra-scripts.json`: 690,2094,1335,1336,191,98,1972,8303.
- `cache/puzzle-tile-scripts.json`: 1485,692,691,6388,208.
- Reproduce with `ClueScriptProbe.java` using the pinned cache2686 (the command
  used `game/build/install/game/lib/*`; no target cache or server process).

Important correction for the next runtime/import batch: source **12128** is the
Sherlock elite challenge: **Read / Check steps / Check completion**. Source
**12139** is the numeric elite challenge: **Read / Check steps**. Both names are
`Challenge scroll (elite)`. The existing 245-item roster includes only 12128.
Do not use its extra Check completion option as an unexplained no-op on numeric
questions. Add 12139 through `build_catalogue.py`, rerun the item preflight, and
update the exact handoff before numeric challenge activation. Current frozen
integration inputs have not been changed for this research finding.

Source 690 uses enums **1864 / 1865** to resolve the complete image's piece
mapping and inverse. Source varp261 is the source picture selector; it is not a
safe native selector (our Settings also uses that namespace). For standard
Treasure Trail pictures:

| Picture selector | forward enum | inverse enum | first source tile item |
|---:|---:|---:|---:|
|1|1354|1866|2749|
|2|1355|1867|3619|
|3|1356|1868|3643|
|4|1357|1869|20283|
|5|1358|1870|20307|
|6|1359|1871|20331|
|9|2318|2319|23418|

Each has 24 source tile items; the missing cell is the blank. Selectors 7/8 and
10 are other quest puzzles; source code handles them separately. Do not include
those as treasure trail pictures. Source 1485 writes the actual source tile
positions to varcs81..102,104..106; 692 positions the pieces from those values.
This is client presentation, not permission to reuse those varcs in 667.

Source 1339 draws the **25** lamps from source varp1356 using lamp items20356/20357
and source 1334 builds the **eight** Press controls. It proves the board display
and button labels/hover; it does **not** reveal the server's random switch wiring.
Current `TreasureTrailLightBox` correctly requires explicit wiring rather than
inventing a fixed pattern. Before activation, obtain observed source wiring (eight
single-button changes for one reset board), or a primary source for its generator.
Advice: use a source-proven sample for the limited supported puzzle set, with
unobserved generation/timing disabled like Mimic Q1; never ship fabricated masks.

The actual source item cache confirms blank-name UI-only tile items. Classic
native IDs2749/3619/3643 happen to use the same numeric model IDs but this is not
mesh/pixel equivalence proof. Modern source tile20283 is a local smelting urn,
20307 a woodcutting urn,20331 a fishing urn,23418 bottled dragonbreath. Never reuse
those local numeric IDs. Port real source assets or source-verified model mappings;
do not synthesize player-visible placeholder items.


## Corrected item roster, B 15 November 15:29

The generator now replaces unused source25612 (blueprint, owner Q5) with
source12139 (numeric elite challenge). Source12128 remains the distinct Sherlock
skill challenge. Count stays245. `python tools/clue/preflight_items.py` ran the
actual current importer as PLAN/PREFLIGHT: tx-20261115-142946,436 definitions,
no applied/finalized/recovered entries. Both caches, ledger, runtime mapping and
roster fingerprints stayed identical. Exact proposal: `ITEM-PREFLIGHT.tsv`;
proof: `build/clue/item-preflight-current/read-only-proof.json`.

Additional Wiki pins appended to the existing manifest without refreshing older
sources: Nechryael15385695, Slayer Tower15399874 and Gem stall15362094. These
establish the actual Slayer Tower floors and distinguish the Ardougne gem stall
from other towns. Full contents and SHA256 are retained in `sources/manifest.json`.
