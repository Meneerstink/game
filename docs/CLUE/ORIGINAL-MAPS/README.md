# Original maps, B16-Nov continuation

All17 deferred original maps now have pinned Wiki pages, cache2686 DB rows,
identical tiers/coordinates, raw component/model hashes and named Wiki images.
Five geometry comparison sheets were manually inspected: all17 match the Wiki
landmarks, X/crate, orientation and routes. Geometry omits paper, lighting and
depth sorting; this is not loaded-client proof.

The26 drawings contain1998components and25source models. All14model hashes
shared by the old9/new17 exports match. Ranging Guild's individual clue/cache
location and illustration agree; its illustration is missing from the pinned
medium map guide. No tier or coordinate was inferred from a donor interface ID.

| Search | OSRS scenery | Native scenery | Tile |
|---|---:|---:|---|
| McGrubor's Wood |357|24202|2658,3488,0|
| Observatory stairs |18506|18506|2457,3182,0|
| Dark Warriors' Fortress |354|46331|3026,3628,0|
| Clock Tower |354|46331|2565,3248,0|

Both actual scenes prove each exact Crate/Search variant. The DB's generic
object identity is not assumed to equal the placed variant. Native46331 is
separated by tile and held clue. No menu or map import is needed for these four.

Staged runtime:12tests passed, including Read/Close, all13added digs with
tile/plane/held-clue checks and8search routes with wrong object/tile/menu.
Codec:10tests passed; real Component decoder1998/1998 across26groups.
Production source/data were promoted after SHA/semantic checks; only the core
test's fixture path changed to the identical committed JSON. Whole Gradle and
scripts still require acceptance.

PREFLIGHT tx-20261116-212134:2025mutations=1998components+25models+2sprites.
Proposals:groups1203..1228,models66727..66751,sprites8955/8956. Cache/ledger/
mappings unchanged; validation/original-maps-preflight.json. No apply. Rerun
after preceding item/NPC/STASH/reward UI batches; no allocation is reserved.

Catalogue92:22easy/26medium/20hard/12elite/12master. It represents38of41original
locations. Emir office scene and2unmapped old emotes remain under review.
The initial69 still overlaps15originals, so contains54unique additions. Three
torn parts form one choice. These are source catalogue counts, not active ones;
guard/assets/client gates still apply.

Reproduce with research_original_maps.py and preview_original_maps.py, then
curate_steps.py, build_step_runtime.py, build_step_evidence.py,
build_search_targets.py and build_map_sources.py. Cache DB probe output is an
input. The staging helpers write only under this worktree's build/clue.
