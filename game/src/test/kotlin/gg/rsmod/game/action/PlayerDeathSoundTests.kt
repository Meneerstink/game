package gg.rsmod.game.action

import gg.rsmod.game.model.Appearance
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** N170/N177 (owner 2026-10-22): a dying player's death cry is heard by the killer and everyone nearby, not only by himself. */
class PlayerDeathSoundTests {
    @Test
    fun `the death cry is an area sound at the dying player, male or female`() {
        val tile = Tile(3093, 3493, 0)
        val male = player(tile, Appearance.DEFAULT)
        val female = player(tile, Appearance.DEFAULT_FEMALE)

        val maleSound = PlayerDeathAction.deathSound(male)
        assertEquals(PlayerDeathAction.MALE_DEATH_SOUND, maleSound.id)
        assertEquals(tile, maleSound.tile)
        assertTrue(maleSound.radius >= 8, "radius ${maleSound.radius}: the killer stands up to ~10 tiles away (magic/ranged)")
        assertEquals(PlayerDeathAction.FEMALE_DEATH_SOUND, PlayerDeathAction.deathSound(female).id)
    }

    @Test
    fun `the death cry is spawned with the death animation`() {
        val source = File("src/main/kotlin/gg/rsmod/game/action/PlayerDeathAction.kt").readText()
        val anim = source.indexOf("player.animate(deathAnim.id)")
        val sound = source.indexOf("world.spawn(deathSound(player))")
        assertTrue(anim >= 0 && sound > anim, "the area sound is spawned right after the death animation starts")
        assertTrue(sound < source.indexOf("wait(deathAnim.cycleLength + 1)"), "before the animation's wait")
    }

    private fun player(
        at: Tile,
        looks: Appearance,
    ): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.tile } returns at
        every { p.appearance } returns looks
        return p
    }
}
