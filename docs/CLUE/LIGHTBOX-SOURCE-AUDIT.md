# Light boxes and master puzzle handovers, B17-Nov

The master anagrams `master:mandrith` and `master:radimus` retain both source
puzzle types. The dialogue comes from the pinned Mandrith/Radimus transcripts:
receive a box, solve it, then return to that NPC for the next clue/casket.
Opening/solving alone never advances a trail. The four sliding pictures use
source20280/20281/20282/23417 and existing source306/seven-picture atlas; light
box20355 uses source322. All seven item identities, including internal lamp
items20356/20357, already belong to the current247-item roster.

The light grid is the actual cache2686 script1339. The native port remaps the
private varp1356, lamp item IDs and create/delete opcodes100/102->150/152.
It removes the proven fourth zero creation argument, then relocates branches
through instruction identities. It retains item rendering1205, quantity0,
outline1 and all source integer spacing, including the1px horizontal offset
and8px top inset. It redraws through its mapped varp and after native resize.
This needs no new client code. The existing wider-model patch is still needed
for the other clue interfaces in the combined bundle.

The source322 frame and eight labels are44 static components. Native ports of
134/135/208/98/6388/1335/1336/1337 retain normal/hover/pressed button frames,
label hover colour and the exact15client-cycle press timer. The frame uses the
source228 desktop/classic branch. All52 hooks are independently decoded by the
real native Component class. Ten ported scripts also pass native ClientScript
decoding. The source draw program executes150 item-icon checks over two sizes
and three light states, including correct mapped on/off icons and outline.

`File:Killerwatt attack.wav` pins press sound576; `File:Killerwatt death.wav`
pins completion580. Both synth files are byte-identical in source2686 and both
native caches. The old death sequence3162 contains no frame sound; guessing a
nearby sound ID would have been wrong. Revisions/hashes and dual-cache sound
proof are in cache/lightbox-audio-manifest.json and lightbox-items.json.

The eight switch masks and current25-bit light state persist on the physical
box, using nine appended ItemAttributes. All30 pre-existing enum ordinals are
unchanged. Bank transfers preserve all nine fields. Stale/replaced/banked
scrolls, another account, duplicate stored boxes and failed reward transactions
cannot advance. Destroying the box removes its network. Corrupt or absent
network fields are retained/refused, never treated as a solved box or rerolled.
The source Gnome-child puzzle20282 is rejected by the common bank deposit path;
the other master pictures/light box retain normal banking.

Twelve freshly compiled tests pass in one JVM, plus native decoder/script
execution. The bank test's missing-sound2021 trace comes from its mocked
DefinitionSet, not a real cache lookup; bank audio production code is unchanged.
Durable proofs: validation/lightbox-actual-source.json and
lightbox-attribute-ordinals.json. Full Gradle/script and loaded-client checks
remain separate acceptance points.

PREFLIGHT tx-20261117-032937 passes92mutations:44components,10scripts,
37sprites and one private varp. No extra models/items are imported by this UI
tool. Both caches, shared ledger, item roster and all runtime mappings are
unchanged; proof in validation/lightbox-preflight.json. Exact proposed IDs are
in LIGHTBOX-PREFLIGHT.tsv. The item arguments in this preflight are proposals;
apply explicitly requires both lamp mappings to match the durable applied
item ledger. Re-run after the item/STASH/chart batches have allocated their IDs.

## Source question Q7

Public Wiki/cache evidence specifies the25-light/eight-switch operation and
the NPC's light-box/sliding-box alternatives, but not the server's seed/matrix
generator or relative type weights. The current compatibility advice chooses
the two types uniformly, the four sliding pictures uniformly, and generates
only solvable, initially unsolved switch networks. This is an implementation
choice, not measured evidence of Jagex's hidden random distribution. Record Q7
for source review; do not claim that distribution or full CLUE acceptance.
Loot-table and master clue-step category probabilities are unchanged.

Claude client checks: both NPCs, all four sliding pictures and light box,
normal/hover/pressed button frames, lamp outlines/colours/spacing, native and
resized windows, press/completion audio, close/reopen/relog, full inventory,
banked/duplicate boxes, two accounts, death and return-to-NPC handover. The
Gnome-child bank refusal must retain the item in deposit/all/equipment routes.
