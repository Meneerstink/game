# N446 - Locator orb (V13)

Owner 2026-10-30: craft an inert orb, Ava activates it without a quest, and replaces a lost orb free only when none is owned.

## Claude's required cache import (before functional client testing)

| OSRS item | Name | Reserved 667 item | Inventory options (slots 1..5) | Inventory model |
| --- | --- | --- | --- | --- |
| 22079 | Inert locator orb | 24617 | null, null, null, null, Destroy | OSRS 31914 |
| 22081 | Locator orb | 24618 | Feel, null, null, null, Destroy | OSRS 31914 |

Both: examine `A glass orb with a dragonstone in the centre.`, value 2 coins, weight 0.01 kg, members,
untradeable, non-stackable, non-noteable, not equipable and not alchemisable (OSRS param 295 = 1).
The inert Destroy explanation is `You'll have to make another one.`; the active explanation is
`Hopefully Ava can give you another.` The plugin supplies both in interface 94.

Read-only decoding of pinned `C:\RSPS\import-source\openrs2-2686\cache` (index 2/group 10/file 22079 and 22081)
with `C:\RSPS\tools\cache667\osrs_items.py` confirmed the names, menu slots, cost, model and param 295.
Read the source model from index 7/group 31914; allocate a free local model with the importer and share it between the two items.
Do not assume model 31914 is free locally. Import all display transforms/recolours from each source item too.
IDs 24617/24618 are deliberately reserved by `LocatorOrb`, not upstream OSRS IDs; no existing item constants/config/ledger rows
used these item IDs when checked. Claude must recheck the shared allocation before importing. The import/ledger and both caches
remain Claude's work. Restart/reload plugins after import, since Feel is bound by name at script construction.

Until the expected definitions exist, crafting/Ava fail without consuming anything. The server item overrides are already in
`data/cfg/items.yml`; they do not substitute for cache definitions or models.

## Behaviour and provenance

- [Locator orb](https://oldschool.runescape.wiki/w/Locator_orb): Feel removes up to 10 HP, stops at 1; Ava activation and free replacement.
- [Inert locator orb](https://oldschool.runescape.wiki/w/Inert_locator_orb) and
  [Dragon Slayer II quick guide](https://oldschool.runescape.wiki/w/Dragon_Slayer_II/Quick_guide): use a glassblowing pipe on a
  **cut dragonstone**, with two unnoted molten glass. Pipe is retained. No Crafting XP is invented.
- Crafting 62 is taken from [Dragon Slayer II's requirements](https://oldschool.runescape.wiki/w/Dragon_Slayer_II), since the
  inert-item page states no separate level. This is the retained Crafting gate after removing the quest gate; current level is
  checked like existing glassblowing. SOURCE_GAP: an independent orb-specific level/boost rule is not documented on that item page.
- The Wiki supplies the use message, which is reproduced in `feel`: the orb is unstable and hurts the player.
- SOURCE_GAP: the consulted Wiki pages do not give the exact Feel confirmation dialogue. The requested confirmation is implemented
  with plain wording, once per player (persistent), Yes applies one use; No/closing applies no damage. No claim of verbatim OSRS text.
- Donors searched first: Darkan `content/world/areas/draynor/Draynor.java` has Ava devices; Void and Novite have no DS2 orb route.
  Upstream `import-source/donors/pablomgp-osrs/.../areas/draynor/ava.plugin.kts` supplies the existing device dialogue skeleton.
  New orb lifecycle is built here from the Wiki and owner V13. Local RuneLite gameval contains AnimationID only, no ItemID;
  item IDs/models came from the pinned cache instead.
- Existing Ava NPC (`Npcs.AVA`) at Draynor Manor (3099,3355) is reused. Talk-to offers the orb before device-shop skill gates;
  using the inert orb on Ava also activates it. First acquisition requires the crafted orb; successful activation saves entitlement.
- Replacement uses `Possessions.whereOwned` for **both** forms: every registered container, equipment, bank, gravestone,
  Death's Office, BoB, follower and Grand Exchange. Ava does not bypass an orb still in death storage.
- K01 unchanged: both forms are ordinary non-lockable untradeables. PvM lost items go to the gravestone/Death's Office;
  PvP loses them for their HA coin value (1 coin at value 2), Perdu records the usual store-value reclaim (2 coins).
  Protect Item still follows normal ranking. No Trouver lock or special always-kept entry is added. After a true loss Ava's
  active replacement is free; Perdu's own possession check prevents buying another while it is owned.
- Feel has no combat gate and no attacking source/kill credit. Damage is recomputed by the Hit cancellation callback at resolution
  so earlier combat hits and multiple queued orb clicks cannot make the orb itself kill. Other combat damage can still kill.
- The 667 inventory-button handler preserves the combat target/queue for Locator orb's Feel (as for food). Both Destroy
  packet paths (OpHeld5 and interface action 10) use the same item-specific confirmation.

## Validation / Claude's client checks

Targets: `gg.rsmod.plugins.content.items.osrs.LocatorOrbTests` (including loading/executing the compiled Ava/orb scripts) and
`gg.rsmod.game.message.handler.IfButtonLocatorOrbTests` (real inventory-button handling). Tests use imported-definition fixtures,
not cache writes. Script compilation is a dependency of the targeted plugin tests.
The existing `AvaShardRuntimeTests` also needs a real cache in this worktree; the initial attempt cannot load that absent cache.

After the import: craft at 61/62 Crafting, omit each material, use noted glass, use a full inventory; check correct icon, menus
and examine. Activate with item-on-Ava and Talk-to on a character without Ava's device skills/quest. Cancel/accept first Feel,
then repeat through 11/10/2/1 HP and spam clicks, also during combat. Destroy and reclaim, relog and reclaim; check refusal when
either orb is in bank/BoB/gravestone/Death's Office, and when inventory is full. Check PvM/PvP/Perdu against K01.

Open source questions for Claude/owner: confirm Crafting 62/current-level interpretation if an orb-specific OSRS rule is available;
replace warning wording if an exact OSRS transcript becomes available. Neither uncertainty affects the documented material/HP rules.

### Completed targeted validation (2026-10-30, ChatGPT-A)

- `LocatorOrbTests`: 9 tests, 0 failures/errors, including actual compiled Ava Talk-to, item activation, crafting,
  Feel accept/No/close, persisted confirmation, same-tick damage clamping and K01 fate.
- `IfButtonLocatorOrbTests`: 1 test, 0 failures/errors (Feel preserves combat; other actions keep their existing rules).
- Combined targeted Gradle run completed with exit 0 / BUILD SUCCESSFUL, `--continue --priority low`.
- Worktree-scoped Rsps-Lint over all six changed production sources: 0 findings; `git diff --check`: clean.
- No game/client/server started, no cache writes, push or merge by ChatGPT-A. Client gameplay remains Claude's check above.
