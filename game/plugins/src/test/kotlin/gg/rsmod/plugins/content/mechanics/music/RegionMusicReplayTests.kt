package gg.rsmod.plugins.content.mechanics.music

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedMap
import io.mockk.*
import kotlin.test.*

class RegionMusicReplayTests {
    @Test fun `all twelve ToA source squares keep their own song after its first playback ends`() {
        val world = mockk<World>(relaxed = true)
        val player = mockk<Player>()
        val map = mockk<InstancedMap>()
        every { player.world } returns world
        every { player.tile } returns Tile(6400, 256)
        every { world.instanceAllocator.getMap(any()) } returns map
        val service = RegionMusicService()
        val regions = listOf(16488, 17002, 17004, 17514, 17516, 16490, 16492, 18026, 18028, 17512, 18024, 17000)
        regions.forEachIndexed { index, region ->
            service.musicTrackList.add(RegionMusicService.MusicTrack("song$region", index,
                listOf(RegionMusicService.MusicTrackArea(region = region)), -1, 0))
        }
        for (region in regions) {
            every { map.sourceTile(player.tile) } returns Tile((region shr 8) * 64 + 8, (region and 255) * 64 + 8)
            repeat(3) { assertEquals(listOf("song$region"), service.tracksAt(player).map { it.name }) }
        }
        every { world.instanceAllocator.getMap(any()) } returns null
        every { player.tile } returns Tile(3200, 3200)
        service.musicTrackList.add(RegionMusicService.MusicTrack("outside", 99,
            listOf(RegionMusicService.MusicTrackArea(region = player.tile.regionId)), -1, 0))
        assertEquals(listOf("outside"), service.tracksAt(player).map { it.name })
    }
}
