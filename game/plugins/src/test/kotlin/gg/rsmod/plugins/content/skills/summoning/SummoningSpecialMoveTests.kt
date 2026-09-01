package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
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
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
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

class SummoningSpecialMoveTests {
    @Test
    fun `core dispatcher uses unique verified components`() {
        SummoningSpecialMoves.validate()
        assertEquals(5, SummoningSpecialMoves.bindings.size)
        assertEquals(setOf(77, 139, 127, 121, 173), SummoningSpecialMoves.bindings.map { it.detailsComponent }.toSet())
        assertEquals(setOf(161, 130, 136, 139, 113), SummoningSpecialMoves.bindings.map { it.orbComponent }.toSet())
    }

    @Test
    fun `wrong familiar cannot consume testudo scroll or energy`() {
        val player = newPlayer(SummoningPouchData.DREADFOWL.npc)
        player.inventory[0] = Item(Items.TESTUDO_SCROLL)
        val binding = binding(SummoningScrollData.TESTUDO_SCROLL)

        assertFalse(SummoningSpecialMoves.castInstant(player, binding))
        assertEquals(1, player.inventory.getItemCount(Items.TESTUDO_SCROLL))
        assertEquals(60, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `testudo boosts defence and consumes exactly one scroll and twenty points`() {
        val player = newPlayer(SummoningPouchData.WAR_TORTOISE.npc)
        player.inventory[0] = Item(Items.TESTUDO_SCROLL, 2)
        val binding = binding(SummoningScrollData.TESTUDO_SCROLL)

        assertTrue(SummoningSpecialMoves.castInstant(player, binding))
        assertEquals(8, player.skills.getCurrentLevel(Skills.DEFENCE) - player.skills.getMaxLevel(Skills.DEFENCE))
        assertEquals(1, player.inventory.getItemCount(Items.TESTUDO_SCROLL))
        assertEquals(40, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `winter storage banks one selected item and consumes resources only on success`() {
        val player = newPlayer(SummoningPouchData.PACK_YAK.npc)
        player.inventory[0] = Item(Items.WINTER_STORAGE_SCROLL)
        player.inventory[5] = Item(Items.COINS_995, 10)
        val binding = binding(SummoningScrollData.WINTER_STORAGE_SCROLL)

        assertTrue(SummoningSpecialMoves.castOnInventoryItem(player, binding, 5))
        assertEquals(9, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(1, player.bank.getItemCount(Items.COINS_995))
        assertEquals(0, player.inventory.getItemCount(Items.WINTER_STORAGE_SCROLL))
        assertEquals(48, Familiar.currentSpecialPoints(player))
    }

    private fun binding(scroll: SummoningScrollData) = SummoningSpecialMoves.bindings.single { it.scroll == scroll }

    private fun newPlayer(familiarNpcId: Int): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs
        every { world.gameContext.cycleTime } returns 600
        val skills = SkillSet(Skills.SUMMONING + 1)
        for (skill in 0..Skills.SUMMONING) {
            skills.setBaseLevel(skill, 99)
            skills.setCurrentLevel(skill, 99)
        }
        val player = mockk<Player>(relaxed = true)
        val playerAttributes = AttributeMap()
        every { player.attr } returns playerAttributes
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.containers } returns HashMap()
        every { player.skills } returns skills
        every { player.tile } returns Tile(0, 0, 0)
        val npc = mockk<Npc>(relaxed = true)
        every { npc.id } returns familiarNpcId
        every { npc.tile } returns Tile(0, 0, 0)
        every { npc.world } returns world
        val npcAttributes = AttributeMap()
        every { npc.attr } returns npcAttributes
        every { npc.index } returns 0
        npcs.entries[0] = npc
        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(0, DEFINITIONS.getCount(ItemDef::class.java))
        }
    }
}
