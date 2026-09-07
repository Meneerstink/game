package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.isMulti
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.lang.ref.WeakReference
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FamineSpecialMoveTests {
    @Test
    fun famineConsumesExactlyOneTargetFoodAndResources() {
        val setup = setup()
        setup.attacker.inventory[0] = Item(Items.FAMINE_SCROLL)
        setup.target.inventory[0] = Item(Items.SHARK, 2)

        assertEquals(2, setup.target.inventory.getItemCount(Items.SHARK))
        assertEquals(FamiliarSpecialTarget.PLAYER, SummoningSpecialMoves.resolveBinding(setup.attacker)?.target)
        assertTrue(setup.attacker.tile.isMulti(setup.world))
        assertTrue(setup.target.tile.isMulti(setup.world))
        assertTrue(setup.world.plugins.canAttack(setup.attacker, setup.target))
        assertTrue(SummoningSpecialMoves.castOnPlayer(setup.attacker, setup.target))
        assertEquals(1, setup.target.inventory.getItemCount(Items.SHARK))
        assertEquals(0, setup.attacker.inventory.getItemCount(Items.FAMINE_SCROLL))
        assertEquals(48, Familiar.currentSpecialPoints(setup.attacker))
    }

    @Test
    fun famineWithNoTargetFoodConsumesNothing() {
        val setup = setup()
        setup.attacker.inventory[0] = Item(Items.FAMINE_SCROLL)

        assertFalse(SummoningSpecialMoves.castOnPlayer(setup.attacker, setup.target))
        assertEquals(1, setup.attacker.inventory.getItemCount(Items.FAMINE_SCROLL))
        assertEquals(60, Familiar.currentSpecialPoints(setup.attacker))
    }

    private data class Setup(
        val world: World,
        val attacker: Player,
        val target: Player,
    )

    private fun setup(): Setup {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val tile = Tile(3200, 3600, 0)
        every { world.getMultiCombatRegions() } returns setOf(tile.regionId)
        every { world.getMultiCombatChunks() } returns emptySet()

        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs

        val attacker = mockk<Player>(relaxed = true)
        val target = mockk<Player>(relaxed = true)
        val attackerInventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val targetInventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { attacker.world } returns world
        every { target.world } returns world
        every { attacker.attr } returns AttributeMap()
        every { target.attr } returns AttributeMap()
        every { attacker.inventory } returns attackerInventory
        every { target.inventory } returns targetInventory
        every { attacker.tile } returns tile
        every { target.tile } returns tile
        every { attacker.isOnline } returns true
        every { target.isOnline } returns true
        every { world.plugins.canAttack(attacker, target) } returns true


        val familiar = mockk<Npc>(relaxed = true)
        every { familiar.id } returns Npcs.RAVENOUS_LOCUST
        every { familiar.tile } returns tile
        every { familiar.world } returns world
        every { familiar.index } returns 1
        npcs.add(familiar)
        attacker.attr[FAMILIAR_ATTR] = WeakReference(familiar)
        return Setup(world, attacker, target)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(gg.rsmod.game.fs.def.ItemDef::class.java), 0)
        }
    }
}