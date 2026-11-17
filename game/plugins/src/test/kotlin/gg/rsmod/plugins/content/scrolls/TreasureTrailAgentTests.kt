package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.ext.NPC_STRENGTH_BONUS_INDEX
import io.mockk.*
import kotlin.test.*

class TreasureTrailAgentTests {
    private val accounts = mutableListOf<Player>()
    @BeforeTest fun setup() {
        mockkObject(ClueScrollManager, ClueItems)
        // A test-only import mapping; production IDs still come only from Claude's applied batch.
        every { ClueItems.local(19835) } returns 30500
    }
    @AfterTest fun cleanup() { accounts.forEach { TreasureTrailGuardians.remove(it); TreasureTrailEmotes.remove(it) }; unmockkAll() }
    private fun account(tier: ClueScrollTier = ClueScrollTier.HARD): Pair<Player, TreasureTrailSourceStep> {
        val row = TreasureTrailStepCatalogue.current.rows.first { it.tier == tier && it.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT }
        val defs = mockk<DefinitionSet>()
        every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns defs
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.tile } returns row.points.first()
        every { player.isOnline } returns true
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(defs, 28, ContainerStackType.NORMAL)
        every { player.equipment } returns ItemContainer(defs, 14, ContainerStackType.NORMAL)
        row.equipment.forEachIndexed { index, group -> player.equipment[index] = Item(group.first { it.local() != null }.local()!!) }
        val step = TreasureTrailSourceStep(row)
        player.inventory[0] = Item(tier.scrollId).putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
        every { ClueScrollManager.activeStep(player, any()) } returns null
        every { ClueScrollManager.activeStep(player, tier) } returns step
        every { ClueScrollManager.progress(player, tier) } returns 1
        every { ClueScrollManager.advance(player, any(), any(), any(), any()) } returns true
        accounts += player
        return player to step
    }
    private fun actor(player: Player): Npc = mockk<Npc>(relaxed = true).also { npc ->
        every { npc.world } returns player.world
        every { npc.isSpawned() } returns true
        every { npc.attr } returns AttributeMap()
    }

    @Test fun `first trail step posts Uri without a fight for hard and 78 but never grants the clue`() {
        for (tier in listOf(ClueScrollTier.HARD, ClueScrollTier.MASTER)) {
            val (p, step) = account(tier)
            every { ClueScrollManager.progress(p, tier) } returns 0
            val uri = actor(p)
            var fights = 0
            assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!,
                spawn = { _, _ -> fights++; null }, uri = { player, active -> TreasureTrailEmotes.afterGuardian(player, active) { _, _ -> uri } }))
            assertEquals(0, fights)
            assertSame(uri, TreasureTrailEmotes.session(p)!!.uri)
            verify(exactly = 0) { ClueScrollManager.advance(p, any(), any(), any(), any()) }
        }
    }

    @Test fun `later step spawns once fights in combat gear and permits Uri only after the exact agent dies`() {
        val (p, step) = account()
        val agent = actor(p)
        val uri = actor(p)
        var spawns = 0
        val spawn = { _: Player, _: List<Int> -> spawns++; listOf(agent) }
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn))
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn))
        assertEquals(1, spawns)
        assertFalse(TreasureTrailEmotes.afterGuardian(p, step) { _, _ -> uri })
        val gear = p.equipment[0]!!
        p.equipment[0] = null
        every { agent.isDead() } returns true
        var immediate = 0
        assertTrue(TreasureTrailAgents.killed(agent) { _, _ -> immediate++ })
        assertEquals(0, immediate)
        assertFalse(TreasureTrailAgents.killed(agent))
        assertNull(TreasureTrailEmotes.session(p))
        assertFalse(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn))
        p.equipment[0] = gear
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn,
            uri = { player, active -> TreasureTrailEmotes.afterGuardian(player, active) { _, _ -> uri } }))
        assertEquals(1, spawns)
        assertTrue(TreasureTrailEmotes.finish(p, uri))
        assertFalse(TreasureTrailEmotes.finish(p, uri))
    }

    @Test fun `source attire at death posts Uri once without another emote and refuses the other account`() {
        val (p, step) = account()
        val (other, _) = account()
        val agent = actor(p)
        val uri = actor(p)
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, { _, _ -> listOf(agent) }))
        assertTrue(TreasureTrailAgents.canAttack(p, agent))
        assertFalse(TreasureTrailAgents.canAttack(other, agent))
        assertFalse(TreasureTrailAgents.canAttack(agent, other))
        assertTrue(TreasureTrailAgents.canAttack(agent, p))
        // Killing in clue gear posts Uri even after moving away from the initial emote square.
        val spot = p.tile
        every { p.tile } returns Tile(spot.x + 2, spot.z + 1, spot.height)
        every { agent.isDead() } returns true
        var posted = 0
        val finish = { player: Player, active: TreasureTrailSourceStep ->
            posted++; TreasureTrailEmotes.afterGuardian(player, active) { _, _ -> uri }; Unit
        }
        assertTrue(TreasureTrailAgents.killed(agent, finish))
        assertFalse(TreasureTrailAgents.killed(agent, finish))
        assertEquals(1, posted)
        assertFalse(TreasureTrailEmotes.finish(other, uri))
        assertTrue(TreasureTrailEmotes.finish(p, uri))
        assertSame(p, TreasureTrailAgents.dropOwner(agent))
        assertNull(TreasureTrailAgents.dropOwner(agent))
    }

    @Test fun `wrong emote attire banked clue failed spawn and logout cannot earn a guardian victory`() {
        val (p, step) = account()
        val agent = actor(p)
        var spawns = 0
        val spawn = { _: Player, _: List<Int> -> spawns++; listOf(agent) }
        assertFalse(TreasureTrailAgents.emote(p, -1, spawn))
        val gear = p.equipment[0]!!
        p.equipment[0] = null
        assertFalse(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn))
        p.equipment[0] = gear
        assertEquals(0, spawns)
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, { _, _ -> null }))
        assertNull(TreasureTrailGuardians.session(p))
        assertTrue(TreasureTrailAgents.emote(p, step.source.firstEmote!!, spawn))
        p.inventory[0] = null
        TreasureTrailGuardians.cycle()
        assertNull(TreasureTrailGuardians.session(p))
        every { agent.isDead() } returns true
        assertFalse(TreasureTrailAgents.killed(agent))
        assertFalse(TreasureTrailEmotes.afterGuardian(p, step) { _, _ -> actor(p) })
        assertFalse(TreasureTrailAgents.canAttack(p, agent))
    }

    @Test fun `all three profiles preserve source HP stats strength speed experience and death animation`() {
        val profiles = TreasureTrailAgents.profiles
        assertEquals(listOf(80, 120, 160), profiles.map { it.hp })
        assertEquals(listOf(0, 40, 40), profiles.map { it.strength })
        profiles.forEach { source ->
            val actual = TreasureTrailAgents.definition(source)
            assertEquals(source.hp, actual.lifepoints)
            assertEquals(source.stats, actual.stats)
            assertEquals(source.strength, actual.bonuses[NPC_STRENGTH_BONUS_INDEX])
            assertEquals(4, actual.attackSpeed)
            assertEquals(1 + source.xp / 100, actual.xpMultiplier)
            assertEquals(listOf(836), actual.deathAnimation)
        }
    }
}
