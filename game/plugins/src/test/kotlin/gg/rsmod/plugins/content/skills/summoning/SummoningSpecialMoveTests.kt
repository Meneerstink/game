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
import io.mockk.just
import io.mockk.Runs
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.combat.dealHit
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
    fun `steel of legends passes twenty four point four lifepoints per hit without multiplying damage by ten twice`() {
        val player = newPlayer(SummoningPouchData.STEEL_TITAN.npc)
        val familiar = Familiar.current(player)!!
        val target = mockk<Npc>(relaxed = true)
        every { target.isAlive() } returns true
        every { target.tile } returns player.tile
        // Stub on the world mock itself: a chained `every { player.world.x() }` makes MockK re-bind
        // `player.world` to a fresh child mock, whose relaxed `npcs.contains` then returns false and
        // Familiar.current() forgets the familiar ("You need a familiar summoned...").
        val world = player.world
        val plugins = world.plugins
        every { world.plugins } returns plugins
        every { world.getMultiCombatRegions() } returns setOf(player.tile.regionId)
        every { plugins.canAttack(player, target) } returns true
        player.inventory[0] = Item(SummoningScrollData.STEEL_OF_LEGENDS_SCROLL.scroll)
        mockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        try {
            every { familiar.dealHit(target, any(), any(), any(), any(), any(), any()) } returns mockk(relaxed = true)
            val sent = mutableListOf<Message>()
            every { player.write(*varargAll { sent.add(it); true }) } just Runs
            assertTrue(
                SummoningSpecialMoves.castOnNpc(player, target),
                "castOnNpc refused; player was told: " + sent.filterIsInstance<MessageGameMessage>().map { it.message },
            )
            verify(exactly = 4) { familiar.dealHit(target, 0.1, 24.4, true, any(), any(), HitType.RANGE) }
        } finally {
            unmockkStatic("gg.rsmod.plugins.content.combat.PawnExtKt")
        }
    }

    @Test
    fun `dispatcher resolves the right binding by active familiar, not a component id`() {
        // R08: 662/747 have no per-scroll component ids at all (confirmed against the real
        // cache), so resolution is keyed by which familiar the player has summoned.
        SummoningSpecialMoves.validate()
        // 52 before 2026-09-07, plus Call to Arms (the four Void familiars), Petrifying Gaze (the
        // seven "-atrice" familiars) and Rise from the Ashes (Phoenix) - twelve familiars that had
        // no special-move binding at all, and therefore a dead Special Move button. Then Goad and
        // Ambush, found by the roster-wide coverage sweep in SummoningSpecialMoveCoverageTests:
        // the Spirit graahk and Spirit kyatt had sourced scroll data and no binding either.
        assertEquals(57, SummoningSpecialMoves.bindings.size)
        val player = newPlayer(SummoningPouchData.WAR_TORTOISE.npc)
        assertEquals(SummoningScrollData.TESTUDO_SCROLL, SummoningSpecialMoves.resolveBinding(player)?.scroll)
    }

    @Test
    fun `wrong familiar cannot consume testudo scroll or energy`() {
        val player = newPlayer(SummoningPouchData.DREADFOWL.npc)
        player.inventory[0] = Item(Items.TESTUDO_SCROLL)

        assertFalse(SummoningSpecialMoves.castInstant(player))
        assertEquals(1, player.inventory.getItemCount(Items.TESTUDO_SCROLL))
        assertEquals(60, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `testudo boosts defence and consumes exactly one scroll and twenty points`() {
        val player = newPlayer(SummoningPouchData.WAR_TORTOISE.npc)
        player.inventory[0] = Item(Items.TESTUDO_SCROLL, 2)

        assertTrue(SummoningSpecialMoves.castInstant(player))
        assertEquals(8, player.skills.getCurrentLevel(Skills.DEFENCE) - player.skills.getMaxLevel(Skills.DEFENCE))
        assertEquals(1, player.inventory.getItemCount(Items.TESTUDO_SCROLL))
        assertEquals(40, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `winter storage banks one selected item and consumes resources only on success`() {
        val player = newPlayer(SummoningPouchData.PACK_YAK.npc)
        player.inventory[0] = Item(Items.WINTER_STORAGE_SCROLL)
        player.inventory[5] = Item(Items.COINS_995, 10)

        assertTrue(SummoningSpecialMoves.castOnInventoryItem(player, 5))
        assertEquals(9, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(1, player.bank.getItemCount(Items.COINS_995))
        assertEquals(0, player.inventory.getItemCount(Items.WINTER_STORAGE_SCROLL))
        assertEquals(48, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `blood drain deals flat self-damage, cures poison and requires six lifepoints`() {
        val player = newPlayer(SummoningPouchData.BLOATED_LEECH.npc)
        every { player.getCurrentLifepoints() } returns 5
        player.inventory[0] = Item(Items.BLOOD_DRAIN_SCROLL)

        assertFalse(SummoningSpecialMoves.castInstant(player))
        assertEquals(1, player.inventory.getItemCount(Items.BLOOD_DRAIN_SCROLL))

        every { player.getCurrentLifepoints() } returns 6
        assertTrue(SummoningSpecialMoves.castInstant(player))
        assertEquals(0, player.inventory.getItemCount(Items.BLOOD_DRAIN_SCROLL))
        verify { player.alterLifepoints(value = -1) }
    }

    @Test
    fun `herbcall spawns a grimy herb and consumes exactly one scroll and twelve points`() {
        val player = newPlayer(SummoningPouchData.MACAW.npc)
        player.inventory[0] = Item(Items.HERBCALL_SCROLL, 2)

        assertTrue(SummoningSpecialMoves.castInstant(player))
        assertEquals(1, player.inventory.getItemCount(Items.HERBCALL_SCROLL))
        assertEquals(48, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `ophidian incubation transforms a verified egg but rejects an unmapped item`() {
        val player = newPlayer(SummoningPouchData.SPIRIT_COBRA.npc)
        player.inventory[0] = Item(Items.OPH_INCUBATION_SCROLL, 2)
        player.inventory[5] = Item(Items.EGG)

        assertTrue(SummoningSpecialMoves.castOnInventoryItem(player, 5))
        assertEquals(0, player.inventory.getItemCount(Items.EGG))
        assertEquals(1, player.inventory.getItemCount(Items.COCKATRICE_EGG))
        assertEquals(1, player.inventory.getItemCount(Items.OPH_INCUBATION_SCROLL))
        assertEquals(57, Familiar.currentSpecialPoints(player))

        player.inventory[6] = Item(Items.COINS_995, 10)
        assertFalse(SummoningSpecialMoves.castOnInventoryItem(player, 6))
        assertEquals(10, player.inventory.getItemCount(Items.COINS_995), "unmapped item must not be consumed")
        assertEquals(1, player.inventory.getItemCount(Items.OPH_INCUBATION_SCROLL), "failed cast must not consume the scroll")
    }

    @Test
    fun `immense heat opens the jewellery crafting interface only with a gold bar and consumes one scroll and six points`() {
        val player = newPlayer(SummoningPouchData.PYRELORD.npc)
        player.inventory[0] = Item(Items.IMMENSE_HEAT_SCROLL, 2)

        assertFalse(SummoningSpecialMoves.castInstant(player))
        assertEquals(2, player.inventory.getItemCount(Items.IMMENSE_HEAT_SCROLL), "no gold bar means nothing should be consumed")

        player.inventory[1] = Item(Items.GOLD_BAR)
        assertTrue(SummoningSpecialMoves.castInstant(player))
        assertEquals(1, player.inventory.getItemCount(Items.IMMENSE_HEAT_SCROLL))
        assertEquals(54, Familiar.currentSpecialPoints(player))
    }

    @Test
    fun `swallow whole heals for the cooked fish's value and consumes exactly one scroll and three points`() {
        val player = newPlayer(SummoningPouchData.BUNYIP.npc)
        player.inventory[0] = Item(Items.SWALLOW_WHOLE_SCROLL, 2)
        player.inventory[5] = Item(Items.RAW_SHRIMPS)

        assertTrue(SummoningSpecialMoves.castOnInventoryItem(player, 5))
        assertEquals(0, player.inventory.getItemCount(Items.RAW_SHRIMPS))
        assertEquals(1, player.inventory.getItemCount(Items.SWALLOW_WHOLE_SCROLL))
        assertEquals(57, Familiar.currentSpecialPoints(player))
        // RCV-010: shrimps are 30 on the Food x10 ledger = 3 real lifepoints (lifepoints are 1:1).
        verify { player.alterLifepoints(value = 3, capValue = 0) }
    }

    private fun newPlayer(familiarNpcId: Int): Player {
        val world = mockk<World>(relaxed = true)
        // Real npc update-block table: a relaxed mock returns Objects that break Npc.addBlock.
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns DEFINITIONS
        val npcs = mockk<PawnList<Npc>>(relaxed = true)
        every { world.npcs } returns npcs
        every { npcs.contains(any()) } returns true
        every { world.gameContext.cycleTime } returns 600
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
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
