package gg.rsmod.plugins.content.mechanics.death

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [QuiverDeathRules]: OSRS Wiki "Dizana's quiver" Death - an unprotected Wilderness PvP death loses
 * any stored ammo and all charges, whether the quiver itself is dropped to the killer or kept because
 * it is Trouver-locked.
 */
class QuiverDeathRulesTests {
    @Test
    fun `a lost quiver drops stripped of ammo and charges, and its ammo becomes a separate ground item`() {
        val victim = newPlayer()
        val killer = newPlayer()
        val filled =
            DizanasQuiver.fill(DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER), 500).result, Item(Items.RUNE_ARROW, 200))
                .let { (it as DizanasQuiver.FillResult.Filled).quiver }
        val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, filled))
        val resolved = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, killer, DeathItemRiskResult(0, emptyList(), lost))

        val (result, dropped) = QuiverDeathRules.stripLost(resolved)

        val strippedQuiver = result.itemRisk.lost.single().item
        assertEquals(Items.DIZANAS_QUIVER, strippedQuiver.id)
        assertEquals(0, DizanasQuiver.charges(strippedQuiver))
        assertEquals(null, DizanasQuiver.storedAmmo(strippedQuiver))
        assertEquals(listOf(Items.RUNE_ARROW to 200), dropped.map { it.id to it.amount })
    }

    @Test
    fun `a death outside the wilderness never strips a quiver`() {
        val victim = newPlayer()
        val filled =
            DizanasQuiver.fill(DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER), 500).result, Item(Items.RUNE_ARROW, 200))
                .let { (it as DizanasQuiver.FillResult.Filled).quiver }
        val lost = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, 0, filled))
        val resolved = DeathResolutionResult(DeathContext.PVM_SAFE, victim, null, DeathItemRiskResult(0, emptyList(), lost))

        val (result, dropped) = QuiverDeathRules.stripLost(resolved)

        assertSame(resolved, result)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `a Trouver-locked (protected) quiver is kept but stripped of ammo and charges in place`() {
        val victim = newPlayer()
        val filled =
            DizanasQuiver.fill(DizanasQuiver.charge(Item(Items.DIZANAS_QUIVER_L), 300).result, Item(Items.RUNE_ARROW, 40))
                .let { (it as DizanasQuiver.FillResult.Filled).quiver }
        victim.equipment[EquipmentSlot] = filled
        val protected = listOf(DeathSlotItem(DeathContainerSource.EQUIPMENT, EquipmentSlot, filled))
        val resolved = DeathResolutionResult(DeathContext.WILDERNESS_PVP, victim, null, DeathItemRiskResult(1, protected, emptyList()))

        val dropped = QuiverDeathRules.stripProtected(victim, resolved)

        val kept = victim.equipment[EquipmentSlot]!!
        assertEquals(Items.DIZANAS_QUIVER_L, kept.id, "the locked quiver itself is kept")
        assertEquals(0, DizanasQuiver.charges(kept))
        assertEquals(null, DizanasQuiver.storedAmmo(kept))
        assertEquals(listOf(Items.RUNE_ARROW to 40), dropped.map { it.id to it.amount })
    }

    @Test
    fun `dropAmmo spawns one killer-owned ground item per stack and no-ops for an empty list`() {
        val victim = newPlayer()
        val killer = newPlayer()
        val world = mockk<World>(relaxed = true)

        QuiverDeathRules.dropAmmo(world, victim, killer, emptyList())
        verify(exactly = 0) { world.spawn(any<GroundItem>()) }

        QuiverDeathRules.dropAmmo(world, victim, killer, listOf(Item(Items.RUNE_ARROW, 40)))
        verify(exactly = 1) { world.spawn(match<GroundItem> { it.item == Items.RUNE_ARROW && it.amount == 40 }) }
    }

    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(3200, 3700, 0)
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.deathRecovery } returns ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.gravestone } returns ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        return player
    }

    companion object {
        /** Any valid equipment slot index - only used as a consistent key between the test's container write and its DeathSlotItem. */
        private const val EquipmentSlot = 0
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            assertTrue(DEFINITIONS.getCount(ItemDef::class.java) > Items.DIZANAS_QUIVER_L)
        }
    }
}
