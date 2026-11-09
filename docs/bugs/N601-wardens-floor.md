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

P3 still has two patches of ground showing through the floor. The grid lift
does not solve that separate overlap. Read-only terrain measurements find
plane 0 and plane 1 at the same height (raw height 1); the imported map omits
the source hidden overlays 41/123, and plane 0 contains grey underlay 55.
These observations narrow the next investigation; they are not a proven fix.
No complete N601 claim or live verification is made here. The release must
include the cache change.
