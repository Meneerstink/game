# CLUE takeover review — ChatGPT-B

Starting commit: `32847604d`. This review is independent of A's progress claims.
The current card gives B the entire clue scope; gear census remains a separate task.

## Findings before changes

* The reward catalogue and planner were unreachable: `casket.plugin.kts` still
  bound the four donor handlers, and no handler bound the master casket.
* The obsolete elite slot tree still replaced eight kits and two missing assets
  with nothing. Owner 14 November requires a weighted reroll for shop-only kits.
* Seven complete collection-log source enums exist in data, but missing imports
  currently prevent building those pages. Cache preparation is a release gate.
* The viewer's ordinary-roll catalogue omitted casket-level master clues,
  Bloodhound, milestone cosmetics and cases. Those need their actual probabilities.
* All 41 donor steps were registered in every tier, including master. These are
  not evidence of OSRS tier classification or of the requested new step set.
* `STEP-SOURCE-GAPS.json = []` means the source parser classified all rows;
  it does **not** mean the runtime implements those step types.
* Watson/Mimic transfer files are drafts. Watson's full transcript, remote check,
  scroll boxes, blueprint, arena, fight, retrieval and reward settlement need work.

## Verified source corrections

Pinned `The Mimic` / `Mimic` pages specify a one-time guaranteed encounter on
the 10th master or 25th elite opening, while opted in. First-try elite/master
bonus denominators are 250/228; later attempts use 264/240. Ring of 3rd Age is
six times the rare-equipment chance; the 23 rare items are equally likely.
Ordinary reward rolls remaining are 5 (elite) / 6 (master) minus failed attempts.
Death on the winning tick does not remove a roll. Boss loot is opened outside.

Watson accepts opened easy/medium/hard/elite clues, not boxes. He gives a master
box after X Marks the Spot, subject to box capacity. Remote contact checks stored
tiers. His stored clues do not block receiving another clue of those tiers.

Puzzle boxes belong to hard/elite/master; light boxes belong to master. The
earlier suggested medium light-box/puzzle coverage is not an OSRS rule and will
not be used to place a master-only step in medium.

Opening jingle: Wiki `Treasure! (Treasure Trails)`, cache 2686 index 11/group 193.
`ui-asset-evidence.json` proves byte equality with the local jingle. No invented
sound or arbitrary graphic is necessary for the 78-casket opening effect.

## Evidence still required

The Wiki describes candy composition, stomp progression, stats and attempts,
but does not state the precise candy scheduling, projectile travel ticks, or
the probability of the Mimic stepping onto its target. These must be verified
from an authoritative observation/source; a donor constant alone is insufficient.
Record unresolved values in the dossier rather than advertising OSRS parity.

Claude's assigned checks remain: cache application, loaded testclient comparison,
collision/accessibility, sounds, death routes, second account and live proof.
No server/client/cache/live action has been performed by B.
