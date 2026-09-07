package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Roster-wide **resource accounting** for familiar special moves.
 *
 * ## What this covers that the per-move tests do not
 *
 * `SummoningSpecialMoveTests` and `FamineSpecialMoveTests` prove the *effect* of nine specific
 * special moves. That leaves the large majority of the bound moves with no proof at all about the
 * thing that actually costs the player something: what they consume.
 *
 * Proving 73 individual effects is a different and much larger job, and several of them need world
 * state a unit test cannot honestly build. But the **accounting invariant** is the same for every
 * one of them and is provable generically, so it is proved here for every instant special on the
 * roster rather than for a chosen example:
 *
 *  * a cast that **succeeds** consumes exactly one scroll and exactly the scroll's own point cost;
 *  * a cast that **fails** consumes nothing at all — no scroll, no points;
 *  * a cast with **no scroll** always fails and always consumes nothing;
 *  * a cast with **insufficient points** always fails and always consumes nothing.
 *
 * Those four are the duplication, the free cast and the double-charge, which are the faults that
 * matter most and the ones a per-move effect test would not catch anyway.
 *
 * ## Why only the instant moves
 *
 * An instant special is the only kind that can be cast from a unit test without inventing world
 * state: the targeted ones need a live npc, player or item selection, and a stub for those would be
 * testing the stub. Restricting the sweep is a limit on coverage, stated plainly, rather than a
 * weaker assertion pretending to be a full one. The targeted moves' accounting is reachable the
 * same way once the run has a harness that can supply real targets.
 */
class SummoningSpecialResourceTests {
    /** Every familiar whose special move fires without a target. */
    private fun instantFamiliars(): List<SummoningPouchData> =
        SummoningPouchData.values().filter { pouch ->
            FamiliarCapabilityTable.forNpc(pouch.npc)?.specialTarget == FamiliarSpecialTarget.INSTANT
        }

    /** The scroll a given familiar's special actually consumes. */
    private fun scrollFor(pouch: SummoningPouchData): SummoningScrollData? {
        val binding = FamiliarCapabilityTable.forNpc(pouch.npc)?.special ?: return null
        return binding.scrolls.firstOrNull { pouch.npc in it.familiars } ?: binding.scroll
    }

    @Test
    fun `the sweep actually covers a meaningful slice of the roster`() {
        // Guards against this whole class silently becoming a no-op if the target modes are ever
        // re-derived and nothing resolves to INSTANT any more.
        val instants = instantFamiliars()
        assertTrue(
            instants.size >= 20,
            "expected a substantial number of instant specials, found ${instants.size}",
        )
        instants.forEach { pouch ->
            assertNotNull(scrollFor(pouch), "${pouch.name} is instant but resolves no scroll")
        }
    }

    @Test
    fun `a successful instant cast consumes exactly one scroll and exactly its point cost`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.inventory.add(scroll.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            val fired = SummoningSpecialMoves.castInstant(player)
            val scrollsLeft = player.inventory.getItemCount(scroll.scroll)
            val pointsLeft = Familiar.currentSpecialPoints(player)

            if (fired) {
                if (scrollsLeft != 4) {
                    offenders += "${pouch.name}: succeeded but consumed ${5 - scrollsLeft} scrolls, expected 1"
                }
                val spent = Familiar.MAX_SPECIAL_POINTS - pointsLeft
                if (spent != scroll.specialPoints) {
                    offenders += "${pouch.name}: succeeded but spent $spent points, expected ${scroll.specialPoints}"
                }
            } else {
                // A refusal is legitimate - "you are already at full life points" and the like -
                // but it must be free.
                if (scrollsLeft != 5) {
                    offenders += "${pouch.name}: failed but still consumed ${5 - scrollsLeft} scrolls"
                }
                if (pointsLeft != Familiar.MAX_SPECIAL_POINTS) {
                    offenders += "${pouch.name}: failed but still spent " +
                        "${Familiar.MAX_SPECIAL_POINTS - pointsLeft} points"
                }
            }
        }
        assertEquals(emptyList<String>(), offenders, "special-move resource accounting broken")
    }

    @Test
    fun `no instant special can be cast without its scroll, and none charges points for the refusal`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast with no ${scroll.name} in the inventory"
            }
            if (Familiar.currentSpecialPoints(player) != Familiar.MAX_SPECIAL_POINTS) {
                offenders += "${pouch.name}: charged points for a cast it had no scroll for"
            }
        }
        assertEquals(emptyList<String>(), offenders, "scroll requirement not enforced")
    }

    @Test
    fun `no instant special can be cast without enough points, and none consumes a scroll for the refusal`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.inventory.add(scroll.scroll, 5)
            // One point short of the cost: the boundary, not an arbitrary low value.
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = scroll.specialPoints - 1

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast at ${scroll.specialPoints - 1} points, costing ${scroll.specialPoints}"
            }
            if (player.inventory.getItemCount(scroll.scroll) != 5) {
                offenders += "${pouch.name}: consumed a scroll for a cast it could not afford"
            }
        }
        assertEquals(emptyList<String>(), offenders, "point cost not enforced")
    }

    @Test
    fun `a familiar cannot cast another familiar's special move`() {
        val offenders = mutableListOf<String>()
        val instants = instantFamiliars()
        instants.forEach { pouch ->
            val ownScroll = scrollFor(pouch) ?: return@forEach
            // Any other familiar's scroll, which this one must refuse.
            val foreign = instants.firstNotNullOfOrNull { other ->
                scrollFor(other)?.takeIf { it.scroll != ownScroll.scroll }
            } ?: return@forEach

            val player = newPlayer(pouch.npc)
            player.inventory.add(foreign.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast using ${foreign.name}, which is not its scroll"
            }
            if (player.inventory.getItemCount(foreign.scroll) != 5) {
                offenders += "${pouch.name}: consumed ${foreign.name}, another familiar's scroll"
            }
        }
        assertEquals(emptyList<String>(), offenders, "familiars can consume scrolls that are not theirs")
    }

    @Test
    fun `with no familiar summoned nothing is ever consumed`() {
        val scroll = instantFamiliars().firstNotNullOfOrNull { scrollFor(it) }!!
        val player = newPlayer(familiarNpcId = null)
        player.inventory.add(scroll.scroll, 5)
        player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

        assertEquals(false, SummoningSpecialMoves.castInstant(player))
        assertEquals(5, player.inventory.getItemCount(scroll.scroll))
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))
    }

    private fun newPlayer(familiarNpcId: Int?): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs
        every { world.gameContext.cycleTime } returns 600
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
            skills.setBaseLevel(skill, 99)
            skills.setCurrentLevel(skill, 99)
        }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.containers } returns HashMap()
        every { player.skills } returns skills
        every { player.tile } returns Tile(0, 0, 0)
        if (familiarNpcId != null) {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns familiarNpcId
            every { npc.tile } returns Tile(0, 0, 0)
            every { npc.world } returns world
            every { npc.attr } returns AttributeMap()
            every { npc.index } returns 0
            npcs.entries[0] = npc
            player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        }
        return player
    }

    companion object {
        /**
         * Shared rather than a private copy: 38 test classes in this module each loaded their own
         * full `DefinitionSet` into a companion object, and adding a 39th produced an
         * `OutOfMemoryError` in an unrelated class. See [SummoningTestCache].
         */
        private val DEFINITIONS get() = SummoningTestCache.definitions
    }
}
