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

The grid-only repair left two grey patches of terrain showing through the P3
playable floor. Source map 15696 puts terrain and these models at the same
height; the 667 depth buffer could still choose the terrain over the model.

## Terrain clearance repair, 2026-11-09

`ToaWardenFloorDepthTool` derives each output from source cache 2686, keeps the
existing grid repair and moves every layer up by the existing decal clearance
of one model unit. X/Z coordinates, faces, colours and priorities stay the same.
The plain source model has priorities 2/3; the two broken variants have 2/3/4.
The tool checks those exact layer counts and accepts only the original,
grid-only or final cache bytes. Repeated apply cannot keep raising the floor.

Transaction `tx-20261109-032831` applied six mutations, verified in both caches.
Testserver, fileserver and clients were stopped. A second apply,
`tx-20261109-032859`, changed zero entries and skipped all six. An independent
probe translated the previously verified grid-only fixtures and matched all
six cache entries byte for byte. Rollback: `C:/RSPS/import-journal/tx-20261109-032831/`.

After stopping the local testserver/fileserver, reproduce with:

```powershell
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.ToaWardenFloorDepthTool plan
java -Xmx2g -cp 'game/build/install/game/lib/*' `
  gg.rsmod.game.tools.importer.ToaWardenFloorDepthTool apply
```

| Local model | Final SHA-256 |
| --- | --- |
| 16567 | 417b8c038b1e2ee5622068c5e1abdc8d48cacfec482859c020ff4a6ed06a36f2 |
| 16980 | c4bf1c0a41ecc5038b1dd4544adde767ea2927a01d726aebe9f8fc5bfd6e5458 |
| 17016 | c6d0f3f4bebcbfceb0ffc0b929ca01f42cb6631f54110ae07e5bdd5a07740ebe |

Validation: `ToaWardenFloorDepthTests` 2/2 green, own `installDist` green,
lint zero findings. Proof: `C:/RSPS/work/gpt-a/build/n601-floor-depth/`:
XML, logs, source hashes, expected-byte probe, cache manifest and rebuild log.

MID/GlToolkit on own testserver (2026-11-09): P1, actual P2 and P3 inspected;
two grey patches no longer show through the playable floor. Five P3 builds
(initial plus four wipes/retries) reached the proper P2/P3 NPC forms; the fifth
build retained the repaired floor. Screenshots in `C:/RSPS/screens/N601/`:
`a-p3-depth-p1-mid.png`, `a-p3-depth-p2-high-mid.png`,
`a-p3-depth-p3-high-mid.png`, `a-p3-depth-p3-side-mid.png`,
`a-p3-depth-p3-after-five-mid.png`. Compared against the original Kaoz frames
at 2140/2350 seconds. This used diagnostic clears/HP changes, not a raid run.
Client 1372 closed before committing; no new client code in this repair.

Open: grey terrain remains outside the playable floor near the collapsed edge;
Kephri's wall/camera comparison and the other N601 rooms also remain open.
No complete N601 or live claim. Claude must include this cache change in the
release and perform A9.
