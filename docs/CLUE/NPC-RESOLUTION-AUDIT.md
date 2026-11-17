# NPC source reconciliation, 17 November

The original acquisition HTML renders Farmer's combat level blank and Vyrewatch
as a display range. Neither is a trustworthy enumeration of actual variants.
The separately pinned Wiki infoboxes prove Farmer7 and Vyrewatch87/105/110/120/125.
The generator now uses those exact levels, not every integer inside the range.
Local Vyrewatch130/140 remain excluded from this new source mapping.

The eleven pre-2022 native667Jelly definitions are pinned by exact cache hashes
in cache/legacy-jellies.json (from the original conflict census at73997fbd8).
They correspond to regular78Jellies with hard clues1/128. The Wiki's different
Wilderness Slayer Cave variants11241..11245 were introduced3February2022;
the generator no longer conflates those new actors with the original native
variants. Any change to an original cache hash refuses this automatic resolution.
The absent cave variants remain explicitly listed, without a fabricated1/64
rate on a regular Jelly. The ordinary Wilderness ring modifier still uses the
existing verified WildernessNpcDrops provider where applicable.

Result:625tier routes for587NPCidentities (easy240/medium130/hard191/elite64),
61new routes:4Farmer,11Jelly,46Vyrewatch. No original route was removed.
NPC-SOURCE-CONFLICTS is empty. NPC-SOURCE-RESOLUTIONS retains the source revisions,
hashes, exact infobox levels and per-native-Jelly decision. NPC-SOURCE-GAPS still
distinguishes absent OSRS actors/variants from incompatible names/levels; those
are not guessed equivalents (e.g. the differently levelled Deadman breach bosses).

The existing runtime automatically uses these same rows for independent clue
rolls and Drop Viewer, without a viewer-layout change. Existing source tests plus
the new regular-Jelly/exact-Vyrewatch regression passed5fresh tests against the
production source/data; proof validation/npc-resolution-actual-source.json.
No new cache item/NPC/UI identity and no apply are involved. Claude should check
a regular Jelly, Farmer and both110/120Vyrewatch in Drop Viewer and on kill,
including a full scroll-box account and a second account with free capacity.
