package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Special-move **coverage** across the whole roster (phase F of the re-audit).
 *
 * The existing special-move tests are good at depth - `SummoningSpecialMoveTests` drives nine
 * moves and asserts their real effects, resource costs and failure paths - and say nothing at all
 * about breadth. That is the gap this file closes: in 2011 every familiar has a scroll and a
 * special move, so any familiar without an executable binding is a hole in the subsystem, and
 * until now nothing failed when one existed.
 *
 * These tests are deliberately written to **name the offending familiars** rather than assert a
 * count, so a failure says which entries are missing instead of only that the total moved.
 */
class SummoningSpecialMoveCoverageTests {
    private val roster = SummoningPouchData.values().toList()

    private fun bindingFor(pouch: SummoningPouchData) =
        SummoningSpecialMoves.bindings.firstOrNull { binding ->
            binding.scrolls.any { pouch.npc in it.familiars }
        }

    /**
     * The five familiars that still have no special move, and exactly why each is not simply
     * written. All five have fully sourced scroll data - item id, level, costs, experience and the
     * Knowledge Base description - so what is missing is mechanical detail that would have to be
     * invented, which the project forbids.
     *
     * * **Spirit wolf / Howl** - "Causes NPC foes to flee". Which foes (derivable: the ones
     *   attacking you) is fine; how far and for how long they flee is not sourced.
     * * **Spirit scorpion / Venom Shot** - "Makes your next Ranged attack mildly poisonous". The
     *   poison API takes an initial damage figure, and "mildly" is not a number.
     * * **Compost mound / Generate Compost**, **Beaver / Multichop**, **Hydra / Regrowth** - all
     *   three act on a scenery object (a compost bin, a tree, a farming stump), and
     *   [FamiliarSpecialTarget] has no object mode at all: it offers only `INSTANT`, `NPC`,
     *   `PLAYER` and `INVENTORY_ITEM`. Adding one means new client target-mask work, not just a
     *   handler. Their quantities ("up to 3 logs", the supercompost chance) are also unsourced.
     *
     * Pinned as an exact set rather than a count so that a familiar silently *losing* its binding
     * fails here, and so that filling one of these gaps has to be a deliberate edit to this list.
     *
     * ## A source exists for two of them and must NOT be adopted (checked 2026-09-12)
     *
     * Darkan (`jojo162/world-server`,
     * `src/main/java/com/rs/game/content/skills/summoning/Scroll.java`) - the same file this
     * project already trusts for the 78-familiar spawn/despawn animation table - does implement
     * Howl and Venom Shot, with complete-looking ids:
     *
     * * `HOWL(12425, COMBAT, ...)` - `familiar.sync(8294, 1334)`, projectile 1333, a **magic**
     *   hit for up to 20.
     * * `VENOM_SHOT(12432, COMBAT, ...)` - `familiar.sync(8124, 1403)`, a **ranged** hit for up to
     *   50, target spot-anim 1404 and `makePoisoned(50)`.
     *
     * Both were ported and then reverted, because they contradict the authoritative 2011 source.
     * This project's own Knowledge Base capture (`C:\RSPS\summoning_refs1kb_scrolls_table.json`,
     * the table every other row of [SummoningScrollData] is verified against) gives
     * `["Howl", "Spirit wolf", "1", "0.1", "Causes NPC foes to flee", "3", "0.1"]` and
     * `["Venom Shot", "Spirit scorpion", "19", "1", "Makes your next Ranged attack mildly
     * poisonous, provided the ammunition you are using can be poisoned", "6", "1"]`. Howl is a
     * fear effect and does no damage at all; Venom Shot is a buff on the **player's** next ranged
     * attack and is not a familiar attack. Darkan's versions are Darkan's own inventions dressed
     * in real animation ids, which is precisely the shape of source this project must refuse.
     *
     * So both stay blocked, now for a sharper reason than "unsourced": the numbers that exist are
     * provably wrong, and the numbers the real behaviour needs (how long and how far a scared NPC
     * flees; what "mildly" poisonous is, in a codebase whose player ranged attacks apply no weapon
     * poison at all) still do not exist anywhere that has been checked.
     */
    /*
     * 2026-09-18 (owner P0 remainder): all five are now bound. Howl and Venom Shot follow the approved Void donor
     * (FamiliarCombatSpecials / FamiliarBoostSpecials: Howl is a no-damage flee, Venom Shot a charge on the owner's next
     * ranged hit - matching the KB text above, unlike Darkan); Generate Compost, Multichop and Regrowth use the new
     * OBJECT target (TGT_LOC) and the spell-on-object route. Unsourced quantities are labelled ADAPTED in the code.
     */
    private val knownUnbound = emptySet<SummoningPouchData>()

    /**
     * The census, as a hard assertion rather than a print. If this fails, either a familiar lost a
     * special move it had, or one gained a special move without this list being updated.
     */
    @Test
    fun `exactly the five documented familiars lack a special move, all 78`() {
        val withoutSpecial = roster.filter { bindingFor(it) == null }.toSet()
        assertEquals(
            knownUnbound,
            withoutSpecial,
            "the set of familiars without a special move changed; see this test's documentation for " +
                "why each of the five is blocked, and update it deliberately",
        )
        assertEquals(78, roster.size - withoutSpecial.size, "special-move coverage changed")
    }

    /**
     * A familiar that is *offered* a special move must have a complete, executable one behind it.
     * This is the rule that keeps the orb and the panel honest: the capability model derives
     * `SPECIAL_MOVE` purely from the binding existing, so a binding that is bound but unusable
     * would put a button on both surfaces that does nothing.
     */
    @Test
    fun `every offered special move has a scroll, a cost, a target mode and panel text, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc) ?: return@mapNotNull "${pouch.name} has no capability record"
                if (!capabilities.supports(FamiliarAction.SPECIAL_MOVE)) return@mapNotNull null
                val binding = capabilities.special ?: return@mapNotNull "${pouch.name} offers SPECIAL_MOVE with no binding"
                val scroll = binding.scrolls.firstOrNull { pouch.npc in it.familiars }
                when {
                    scroll == null -> "${pouch.name} offers SPECIAL_MOVE but no scroll names its npc ${pouch.npc}"
                    scroll.scroll <= 0 -> "${pouch.name} special ${scroll.name} has no scroll item id"
                    scroll.specialPoints !in 1..SummoningSpecialMoves.MAX_PANEL_COST ->
                        "${pouch.name} special ${scroll.name} costs ${scroll.specialPoints}, outside 1..${SummoningSpecialMoves.MAX_PANEL_COST}"
                    SummoningSpecialMoveText[scroll] == null -> "${pouch.name} special ${scroll.name} has no panel text"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} familiars offer an incomplete special move: $faults")
    }

    /**
     * The inverse, and the one that actually protects the two surfaces: a familiar that is **not**
     * offered a special move must not have a usable binding sitting behind it either, or the
     * server would refuse an action the player has every reason to expect.
     */
    @Test
    fun `no familiar is denied a special move it actually has, all 78`() {
        val denied =
            roster.filter { pouch ->
                val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)
                capabilities != null && !capabilities.supports(FamiliarAction.SPECIAL_MOVE) && bindingFor(pouch) != null
            }
        assertTrue(
            denied.isEmpty(),
            "${denied.size} familiars have a special-move binding but are not offered the action: " +
                denied.joinToString { "${it.name}(npc ${it.npc})" },
        )
    }

    /**
     * Every scroll a binding names must belong to a familiar that is actually on the roster.
     * A binding pointing at an npc id no pouch summons is dead configuration, and would make the
     * coverage figures above look better than they are.
     */
    @Test
    fun `no special move is bound to an npc outside the 78`() {
        val rosterNpcs = roster.map { it.npc }.toSet()
        val strays =
            SummoningSpecialMoves.bindings.flatMap { binding ->
                binding.scrolls.flatMap { scroll ->
                    scroll.familiars.filterNot { it in rosterNpcs }.map { "${scroll.name} -> npc $it" }
                }
            }
        assertTrue(strays.isEmpty(), "${strays.size} special-move bindings name npcs outside the roster: $strays")
    }

    /**
     * Two familiars must never share one scroll entry unless the cache genuinely gives them the
     * same scroll item. A duplicated npc id across two different scrolls would make
     * `FamiliarCapabilityTable`'s `firstOrNull` pick whichever was declared first, silently giving
     * one familiar another's special move.
     */
    @Test
    fun `no familiar resolves to more than one special move`() {
        val ambiguous =
            roster.mapNotNull { pouch ->
                val matches =
                    SummoningSpecialMoves.bindings.flatMap { binding ->
                        binding.scrolls.filter { pouch.npc in it.familiars }
                    }
                if (matches.size > 1) "${pouch.name}(npc ${pouch.npc}) -> ${matches.map { it.name }}" else null
            }
        assertTrue(ambiguous.isEmpty(), "${ambiguous.size} familiars resolve to more than one special move: $ambiguous")
    }

    /**
     * Every target mode a binding declares must be one the dispatcher can actually enter. An
     * unhandled mode would arm the button, take the click, and then do nothing - the shape of the
     * inventory-targeting fault the owner reported against Winter Storage.
     */
    @Test
    fun `every bound target mode is one the engine implements`() {
        val known = FamiliarSpecialTarget.values().toSet()
        val unknown = SummoningSpecialMoves.bindings.map { it.target }.filterNot { it in known }
        assertTrue(unknown.isEmpty(), "bindings declare target modes the engine does not implement: $unknown")
        // Every mode the engine declares should also be in use; a mode nothing uses is either dead
        // code or a familiar that was never wired up to it.
        val used = SummoningSpecialMoves.bindings.map { it.target }.toSet()
        val unused = known - used
        assertTrue(unused.isEmpty(), "target modes declared by the engine but used by no familiar: $unused")
    }
}
