package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.content.activity.toa.ToaIds
import gg.rsmod.plugins.content.skills.farming.logic.handler.WaterHandler
import io.mockk.*
import java.lang.ref.WeakReference
import kotlin.test.*

class ToaCrondisRoutingTests {
    //40-loc-placements: entrance barriers45135 at3950,5279..81; ToaRaid.passBarrier lands one tile west, inside the room.
    private val insideEntrance = Tile(3949, 5280)
    @Test fun `all four waterfalls have a reachable corridor entrance and fail with the former twenty tile search`() {
        val f = ToaPuzzleSourceCollision("crondis", Tile(3904, 5248))
        val start = insideEntrance
        CrondisRoom.WATERFALLS.forEachIndexed { index, waterfall ->
            val def = f.defs.getValue(45398) //50-loc-defs:7x5; south rot0/north rot2, neither swaps dimensions.
            assertFalse(f.route(start, waterfall, def.width, def.length, oldRadius = true).success,
                "old search must reproduce the client report for waterfall$index")
            val route = f.route(start, waterfall, def.width, def.length)
            assertTrue(route.success, "waterfall$index at$waterfall stops at${route.tail}")
            assertEquals(if (index % 2 == 0) 5255 else 5305, route.tail.z, "approach through the open mouth of the niche")
            assertTrue(route.tail.x in waterfall.x + 1..waterfall.x + 5)
            var previous = start
            route.path.forEach { next ->
                assertTrue(f.collision.canStep(previous, gg.rsmod.game.model.Direction.between(previous, next)))
                previous = next
            }
        }
    }

    @Test fun `every waterfall has a return route to the five tile palm with its shared two tile approach`() {
        val f = ToaPuzzleSourceCollision("crondis", Tile(3904, 5248))
        for (waterfall in CrondisRoom.WATERFALLS) {
            val approach = f.route(insideEntrance, waterfall, 7, 5)
            assertTrue(approach.success)
            val palm = f.route(approach.tail, CrondisRoom.PALM_TILE, 5, 5, CrondisInteractions.PALM_REACH)
            assertTrue(palm.success, "palm from${approach.tail} stops at${palm.tail}")
        }
    }

    @Test fun `real palm bindings give item use the same line of sight as Water for every growth variant and can`() {
        val world = mockk<World>(relaxed = true)
        val definitions = mockk<DefinitionSet>()
        val repository = spyk(PluginRepository(world))
        every { world.definitions } returns definitions
        every { world.plugins } returns repository
        every { definitions.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()).also { it.options[0] = "Water" } }
        mockkObject(ToaIds)
        try {
            every { ToaIds.npc(any()) } answers { firstArg<Int>() + 50000 }
            every { ToaIds.item(any()) } answers { firstArg<Int>() + 10000 }
            CrondisInteractions.bindPalm(object : KotlinPlugin(repository, world, mockk(relaxed = true)) {})
            for (source in CrondisRoom.PALM..CrondisRoom.PALM + 3) {
                val id = checkNotNull(ToaIds.npc(source))
                assertEquals(2, repository.getNpcInteractionDistance(id))
                val player = mockk<Player>(relaxed = true)
                val npc = mockk<Npc>(relaxed = true)
                every { npc.id } returns id
                every { npc.getTransform(player) } returns id
                every { player.world } returns world
                every { player.attr } returns AttributeMap().also { it[INTERACTING_NPC_ATTR] = WeakReference(npc) }
                clearMocks(repository, answers = false)
                PawnPathAction.itemUsePlugin.invoke(Plugin(player))
                verify(exactly = 1) { repository.getNpcInteractionDistance(id) }
                (WaterHandler.wateringCans.drop(1) + listOfNotNull(ToaIds.item(CrondisRoom.CONTAINER), ToaIds.item(CrondisRoom.GRICOLLER)))
                    .forEach { item -> assertTrue(repository.executeItemOnNpc(player, id, item), "missing item$item on palm$source") }
            }
        } finally { unmockkObject(ToaIds) }
    }
}
