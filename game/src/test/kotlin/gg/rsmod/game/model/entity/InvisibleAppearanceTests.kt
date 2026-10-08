package gg.rsmod.game.model.entity

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.sync.block.UpdateBlockType
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-10-08 ("i can see the player is invisible"): after the new-player tour (TutorIntro: invisible during the
 * tour) the player's own model stayed hidden. The appearance block carries the "hidden" byte (PlayerUpdateBlockSegment),
 * and setting [Pawn.invisible] queued no appearance update, so the client kept the last hidden appearance. Darkan's
 * Appearance.setHidden regenerates the appearance data; every change of a player's invisible flag must re-send it.
 */
class InvisibleAppearanceTests {
    private val world =
        World(mockk<GameContext>(relaxed = true), mockk<DevContext>(relaxed = true)).also {
            it.filestore = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())
            it.definitions.loadAll(it.filestore)
            it.playerUpdateBlocks.load(gg.rsmod.util.ServerProperties().loadYaml(Paths.get("..", "data", "blocks.yml").toFile()).extract("players"))
        }

    private val appearanceBit get() = world.playerUpdateBlocks.updateBlocks.getValue(UpdateBlockType.APPEARANCE).bit

    private fun player(): Client =
        Client(EmbeddedChannel(), world).also {
            it.loginUsername = "hidetest"
            it.username = "hidetest"
            it.tile = Tile(3162, 3489)
            it.lastTile = Tile(3162, 3489)
        }

    private fun appearanceQueued(p: Client) = p.blockBuffer.hasBit(appearanceBit)

    @Test
    fun `hiding and showing a player re-sends its appearance`() {
        val p = player()
        p.blockBuffer.clean()
        p.invisible = true
        assertTrue(appearanceQueued(p), "invisible = true queued no appearance update")
        p.blockBuffer.clean()
        p.invisible = false
        assertTrue(appearanceQueued(p), "invisible = false queued no appearance update - the client keeps the player hidden")
    }

    @Test
    fun `setting the same value again sends nothing`() {
        val p = player()
        p.blockBuffer.clean()
        p.invisible = false
        assertFalse(appearanceQueued(p))
    }
}
