package gg.rsmod.plugins.content.combat

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.Gender
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sound audit 2026-09-29: a hit player made no sound (every player block animation is silent in the rev-667 cache). Void
 * Block.sounds plays male_defend_0..2 (518-520) or female_defend_0 (509) to the hit player and to a player attacker.
 */
class PlayerDefendSoundTests {
    private fun player(
        gender: Gender,
        sounds: MutableList<Int>,
    ): Player {
        val world = mockk<World>(relaxed = true)
        return mockk<Player>(relaxed = true).also { p ->
            every { p.world } returns world
            every { p.appearance.gender } returns gender
            every { p.write(*varargAll<Message> { m -> (m as? SynthSoundMessage)?.let { sounds += it.sound }; true }) } just Runs
        }
    }

    @Test
    fun `a hit male player and his attacker hear the same male defend grunt`() {
        val heardByTarget = mutableListOf<Int>()
        val heardByAttacker = mutableListOf<Int>()
        repeat(30) { playPlayerDefendSound(player(Gender.MALE, heardByTarget), player(Gender.FEMALE, heardByAttacker)) }
        assertEquals(heardByTarget, heardByAttacker)
        assertTrue(heardByTarget.all { it in 518..520 }, "$heardByTarget")
    }

    @Test
    fun `a hit female player hears the female defend grunt and an npc attacker hears nothing`() {
        val heard = mutableListOf<Int>()
        playPlayerDefendSound(player(Gender.FEMALE, heard), null)
        assertEquals(listOf(509), heard)
    }
}
