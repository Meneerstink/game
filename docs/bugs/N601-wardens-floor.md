# N601: Wardens floor grid in the 667 depth buffer

The Wardens floor showed broken black grid lines in MID/OpenGL. The imported
models put the grid directly on their background faces. OSRS cache 2686 gives
the 32 grid faces a depth bias of 2 and a higher drawing priority; the conversion
to 667 preserves the geometry and priorities but drops the face bias.

The existing `OsrsDecalLiftTool` places those detail faces just above the faces
that cover them. This fixes the overlap within each floor model. It does not
change terrain textures or the separate P3 base-scenery lifecycle.

| OSRS model | Local model | Grid faces | Maximum move | Unchanged background faces |
| --- | --- | --- | --- | --- |
| 45432 | 16567 | 32 | 1 model unit | 2 |
| 45469 | 16980 | 32 | 2 model units | 4 |
| 45471 | 17016 | 32 | 2 model units | 4 |

The four vertices shared with lower-priority faces are copied in each model.
Only the grid layer moves. Face colours and the X/Z footprint stay unchanged.
Encode/decode preserves the resulting geometry; a second lift changes nothing.

Sources:

- OSRS cache 2686, models 45432/45469/45471; Wardens locs 45643..45648.
- [RuneLite GPU ModelUploader](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/gpu/ModelUploader.java)
  uses `Model.getFaceBias()` when uploading the faces.
- [Kaoz guide, Wardens](https://www.youtube.com/watch?v=DQ6FzzXdA4E&t=2000s):
  full grid in P1, P2 and P3; extracted reference frames at 2000/2140/2350 seconds.

## Applied cache transaction

`tx-20261108-224802`, 2026-11-08 23:48 Europe/Amsterdam: six mutations, zero
skips, verified in both `game/game/data/cache` and `file-server/cache`.
The testserver, fileserver and testclients were stopped before applying.

Reproduce with the existing tool after stopping the local testserver/fileserver:

```powershell
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.OsrsDecalLiftTool plan 16567 16980 17016
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.OsrsDecalLiftTool apply 16567 16980 17016
```

| Model | Before SHA-256 | After SHA-256 |
| --- | --- | --- |
| 16567 | a98c9468ee1aa4b5b84ade674849218aaeb9a760690c901e79fb93aedfd1156f | cd75f1368edfe9856b4f08d8635d1707bdd9ffe26faf70af127514b7bd1aff66 |
| 16980 | 1ffc1d4bfc3d6048376a6b8ad2e44f5bcd76dd97e7de21833db9e06485ec2e0a | 145e29eaabb3ab0ad352d19a72c64b78a5b3b28d98b0917971b2c4629dda033b |
| 17016 | 69823d44e8dd0e03cb2169693f4751b9dfbbed3953822541c168d4571b8f1734 | 9e478faa933729d00280b7498237c50349b9ef1e50b6180fec6a061fa0034ef4 |

Read-only verification exported both cache copies and matched each byte to the
independently checked output. Probe, fixtures, logs and hashes:
`C:/RSPS/work/gpt-b/build/n601-wardens-floor-proof/`.
Rollback originals: `C:/RSPS/import-journal/tx-20261108-224802/`.

## Runtime verification

MID/OpenGL on 2026-11-09 shows continuous grid lines in all three phases:
`C:/RSPS/screens/N601/p3-floor-after-p1-mid.png`,
`p3-floor-after-actual-p2-mid.png` and `p3-floor-after-actual-p3-mid.png`.
The setup uses test room clears and an HP reduction, so this is a graphics check,
not a full raid completion. OSRS reference frames from the Kaoz guide are
`build/n462-sources/d2/entry-video-{2000,2140,2350}.png`.

The grid-only repair initially left grey terrain showing through and around
the P3 floor. The cause was the removal of OSRS transparent terrain masks.

## Transparent source masks, 2026-11-09

OSRS overlay 41 has RGB `ff00ff`, no texture, no secondary colour and
`hideUnderlay=false`. The existing native 667 overlay 41 has the same fields.
The client decodes its colour to -1. In both `Class306.loadUnblended` and
`loadBlended`, an absent overlay changes shape 0 into underlay-only shape 12;
an existing transparent overlay preserves the source shape, then omits its
overlay faces. Dropping the overlay therefore exposed the grey underlay.

`ToaHiddenFloorTool` restores this source mask, shape and rotation only in ToA
maps. Heights, flags, underlays, unrelated tiles and environment bytes stay the
same. The tool checks both native definitions and refuses changed heights or
conflicting overlays. `OsrsRegionImportTool` preserves the same masks during
future ToA imports and does not allocate an unnecessary new colour slot.
Other import batches and hidden masks with secondary/minimap colours retain
their previous behaviour.

Cache transaction `tx-20261109-040030`: 19,002 masks in eleven maps, 22 verified
mutations across both caches. A second apply reports `NOTHING_TO_DO`. An
independent Python byte encoder matched all 26 target map entries, including
the two unchanged maps. Full hashes, original files and expected bytes are in
`C:/RSPS/work/gpt-a/build/n601-hidden-floor/expected-manifest.json` and that
folder. Rollback originals: `C:/RSPS/import-journal/tx-20261109-040030/`.

| Source square | Target tile file | Restored masks |
| --- | --- | --- |
| 13454 | m52_142 | 4258 |
| 14160 | m64_104 | 615 |
| 14162 | m64_106 | 983 |
| 14164 | m64_108 | 826 |
| 14672 | m66_104 | 1138 |
| 14674 | m66_106 | 0 |
| 14676 | m66_108 | 374 |
| 15184 | m68_104 | 440 |
| 15186 | m68_106 | 526 |
| 15188 | m68_108 | 2498 |
| 15696 | m70_104 | 4361 |
| 15698 | m70_106 | 2983 |
| 15700 | m70_108 | 0 |

After stopping testserver, fileserver and clients, reproduce with:

```powershell
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.ToaHiddenFloorTool plan
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.ToaHiddenFloorTool apply
```

A/B verification restored the three models to their grid-only heights using
`restore-tx-20261109-040105-040f8995-3a0a-4b67-a17c-0eb5cde9fd54`.
All six model payloads match the grid-only hashes in the earlier table. With
these original heights, restoring the masks removes the grey patches both
inside and outside the playable P3 floor. The one-unit floor-raising candidate
`87bd6f422` is superseded; its offline tool/tests are removed. Do not reapply
transaction `tx-20261109-032831`. The earlier grid repair remains required.

Validation: `ToaHiddenFloorTests` 4/4 and `ToaHdOverlayTests` 4/4 green,
independent map bytes 26/26, native mask definition checked in both caches,
git diff --check clean. The current change only affects offline cache/import tools. Runtime
uses the previously built own gameplay artefact (04:40) after a cache/server/
client restart; no new gameplay code or client code is involved.

MID/GlToolkit screenshots: `C:/RSPS/screens/N601/a-hidden-*-mid.png`, including
lobby, Nexus, all four puzzles/bosses, actual Wardens P1/P2/P3 and loot chamber.
P3 is compared with original Kaoz frame 2350s. The fifth confirmed P3 build
(initial plus four checked wipe/retries) is also inspected. These are graphics
and lifecycle checks using diagnostic clears/HP changes, not a raid run.
The earlier fixed-delay repetition had an uncompleted P1 and is excluded;
`runtime-break-checked.log` confirms both P2 and P3 for every accepted repeat.
Client 9852 closed during the resumed verification before committing; resumed P3 and loot screenshots inspected. Logs/XML/source hashes/census/byte proof
are in `C:/RSPS/work/gpt-a/build/n601-hidden-floor/`.

Remaining N601 work includes other floor materials and secondary-colour masks,
Kephri's exact camera comparison and the full OSRS room comparisons. No complete
N601 or A9/live claim. Claude must release the current cache, including the
retained grid repair and these map masks, then perform A9.
