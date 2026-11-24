package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.content.activity.cox.*
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class TektonRoomTests {
    @Test fun `anvil repair and enrage transitions retain drained Defence and current hitpoints`() {
        val world = mockk<World>(relaxed = true)
        every { world.gameContext.npcStatCount } returns 5
        every { world.npcUpdateBlocks } returns gg.rsmod.plugins.content.skills.summoning.SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns gg.rsmod.plugins.content.skills.summoning.SummoningTestCache.definitions
        every { world.players.entries } returns arrayOfNulls<Player>(0)
        val raid = CoxRaid(1, CoxLayout(null, false, emptyList()), mockk<InstancedMap>(), world, "test")
        val room = TektonRoom(raid, 0, CoxLaidRoom(gg.rsmod.plugins.content.activity.cox.CoxRoom.TEKTON, 0, 0, CoxDirection.NORTH, 0))
        val npc = Npc(1, Tile(3200, 3200), world)
        room.configure(npc, 7540, 3, -1)
        npc.stats.setCurrentLevel(2, 80)
        npc.setCurrentLifepoints(100)
        for (form in listOf(7540, 7543, 7540, 7543, 7540)) {
            room.configureForm(npc, form)
            assertEquals(80, npc.stats.getCurrentLevel(2), "Defence must survive form $form")
            assertEquals(205, npc.stats.getMaxLevel(2))
            assertEquals(100, npc.getCurrentLifepoints())
            assertEquals(if (form == 7543) 280 else 155, npc.equipmentBonuses[5], "enrage bonuses still change")
        }
        npc.stats.setCurrentLevel(2, 0)
        room.configureForm(npc, 7543)
        assertEquals(0, npc.stats.getCurrentLevel(2), "fully drained Defence must not be restored")
    }
}
