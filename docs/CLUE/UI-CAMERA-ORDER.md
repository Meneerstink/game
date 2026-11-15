# OSRS to 667 model camera field order

The existing CLUE UI converter exchanges the second and third model camera
angles. This affects the reward chest, whose source tuple is104,761,0,914.
It previously wrote104,0,761,914. The old regression only repeated that mistake.

Evidence, read-only15-Nov20:02:

* `cache/ui-camera-order.json` preserves raw cache2686 widget bytes, hashes,
  camera offsets and tuples for73/203/351/354. Reproduce with
  `tools/clue/ClueCameraProbe.java <source-cache> <output-json>` in Java11
  source-file mode and the existing core/lib classpath.
* Primary RuneLite `InterfaceLoader.decodeIf3` reads the three raw shorts into
  `rotationX`, **rotationZ**, **rotationY**, in that order:
  https://github.com/runelite/runelite/blob/master/cache/src/main/java/net/runelite/cache/definitions/loaders/InterfaceLoader.java
* The actual667 `Component.decode` reads the same three camera shorts into
  `xan2d`, `yan2d`, `zan2d`. `InterfaceManager` renders those fields at1168-1178.
  Existing native clue glyph camera tuples agree with the source raw order;
  they differ from the converter's Y/Z-label ordering. This does not establish
  mesh equivalence or client visual acceptance.

Correct adapter: xan=rotationX, yan=rotationZ, zan=rotationY. A regression must
derive the expected tuple from preserved source bytes, rather than hardcode the
converter's former output. The actual unchanged667 Component decoder must also
receive the corrected server fixtures. No canonical client edit is needed for
the angles; the already staged wide-model patch remains a separate requirement.

The frozen39-class/149-test integration passed before promotion. The correction
and its5-test regression are now in the actual source tree; normalized source
hashes prove they are identical to the tested staged files
(`build/clue/interface-port/promoted-proof.json`). The actual unmodified667
Component decoder also accepted the corrected legacy/wide/missing fixtures,
close hover hooks and cache-key regression. No canonical client file was edited.

Corrected preflight tx-20261115-193224 passed27 logical mutations; both caches,
ledger and runtime mapping stayed identical. Exact proposal: UI-PREFLIGHT.tsv,
interfaces1201/1202, models66637/66638, sprites8954-8956, texture1423. These are
proposals, not reservations. Final branch Gradle acceptance is recorded separately.
Claude must replan after preceding batches and check the reward chest and map
glyphs in the loaded OpenGL client before application/release acceptance.
