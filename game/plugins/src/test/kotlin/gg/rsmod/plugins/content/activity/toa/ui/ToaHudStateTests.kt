package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.message.impl.IfSetSpriteMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.plugins.content.activity.toa.*
import io.mockk.*
import kotlin.test.*

class ToaHudStateTests {
    @Test fun `room changes update the HUD even when the raid clock has not advanced`() {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.tile } returns Tile(6400, 256)
        val world = mockk<World>(relaxed = true)
        val raid = mockk<ToaRaid>()
        val map = mockk<InstancedMap>()
        val progress = ToaRaidProgress(ToaRaidSettings())
        every { raid.world } returns world
        every { world.currentCycle } returns 100
        every { raid.progress } returns progress
        every { raid.map } returns map
        every { raid.settings } returns ToaRaidSettings(150)
        every { raid.hudParty() } returns emptyList()
        every { raid.roster } returns emptyList()
        var source = ToaMap.NEXUS
        every { map.sourceTile(player.tile) } answers { source.transform(ToaMap.CACHE_OFFSET_X, ToaMap.CACHE_OFFSET_Z) }
        val updates = mutableListOf<Int>()
        val previous = ToaUi.draw
        ToaUi.draw = { _, state -> updates += state!!.currentPath }
        try {
            ToaUi.refresh(player, raid)
            for (path in ToaPath.values()) {
                source = path.challengeEntry
                ToaUi.refresh(player, raid)
                val count = updates.size
                ToaUi.refresh(player, raid)
                source = path.bossEntry
                ToaUi.refresh(player, raid)
                assertEquals(count, updates.size, "same data and path must not redraw the overlay")
            }
            source = ToaRaid.WARDENS_ENTRY; ToaUi.refresh(player, raid)
            source = Tile(3937, 5158, 1); ToaUi.refresh(player, raid)
            source = ToaRaid.REWARD_ENTRY; ToaUi.refresh(player, raid)
            source = ToaMap.NEXUS; ToaUi.refresh(player, raid)
            assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 0), updates)
        } finally { ToaUi.draw = previous }
    }

    @Test fun `the HUD sends the cache path icon and hides both path panels at Wardens and rewards`() {
        mockkObject(ToaInterfaces)
        every { ToaInterfaces.of(ToaInterfaces.HUD) } returns 1186
        every { ToaInterfaces.sprite(any()) } answers { firstArg<Int>() + 50000 }
        try {
            for (path in 0..6) for (level in listOf(0, 150, 300)) {
                val player = mockk<Player>(relaxed = true)
                ToaHudUi.drawRaid(player, level, 100, path, IntArray(4))
                verify { player.write(IfSetHideMessage((1186 shl 16) or 47, path != 0)) }
                verify { player.write(IfSetHideMessage((1186 shl 16) or 43, path !in 1..4)) }
                val difficulty = 4660 + level / 150
                verify { player.write(IfSetSpriteMessage((1186 shl 16) or 41, difficulty + 50000)) }
                if (path in 1..4) verify { player.write(IfSetSpriteMessage((1186 shl 16) or 44, 4634 + path + 50000)) }
            }
        } finally { unmockkObject(ToaInterfaces) }
    }

    @Test fun `party orbs follow the OSRS status rules`() {
        // CS2 6584: min(26, scale(hp, base, 26)), at least 1 while alive; orb status = 1 + that.
        assertEquals(27, ToaUi.hpStatus(99, 99))
        assertEquals(14, ToaUi.hpStatus(50, 99))
        assertEquals(2, ToaUi.hpStatus(1, 99))
        assertEquals(1, ToaUi.hpStatus(0, 99))
        assertEquals(27, ToaUi.hpStatus(120, 99), "boosted hp stays a full orb")
        mockkObject(ToaInterfaces)
        every { ToaInterfaces.of(ToaInterfaces.HUD) } returns 1186
        every { ToaInterfaces.sprite(any()) } answers { firstArg<Int>() + 50000 }
        try {
            val player = mockk<Player>(relaxed = true)
            ToaHudUi.drawParty(player, listOf(ToaHudUi.Slot("check78", 27), ToaHudUi.Slot("bravo", 30), null), self = 0)
            verify { player.write(IfSetHideMessage((1186 shl 16) or 6, false)) }
            verify { player.write(IfSetHideMessage((1186 shl 16) or 10, false)) }
            for (i in 2 until 8) verify { player.write(IfSetHideMessage((1186 shl 16) or (6 + 4 * i), true)) }
        } finally { unmockkObject(ToaInterfaces) }
    }
}
