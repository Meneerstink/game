package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N136: OSRS pest_rewards_select / pest_rewards_confirmop click (synth 2266) and pest_rewards_refuse plays 2277 (pillory_wrong)
 * with the refusal message; Death's office keep window (deathkeep_opitem / deathkeep_right_opbutton) clicks too.
 */
class PestControlRewardSoundsTests {
    @Test
    fun `a refused reward pick plays the refusal sound, a confirm without a pick too`() {
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns AttributeMap()
        PestControlRewards.select(p, PestControlRewards.BUTTONS.first())
        PestControlRewards.confirm(p)
        verify(exactly = 2) { p.write(match { it is SynthSoundMessage && it.sound == 2277 }) }
        verify(exactly = 0) { p.write(match { it is SynthSoundMessage && it.sound == 2266 }) }
    }

    @Test
    fun `every refusal message of the reward window comes with the refusal sound, every success with a click`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/activity/pest_control/PestControlRewards.kt").readText()
        val select = source.substringAfter("    fun select(").substringBefore("    fun confirm(")
        val confirm = source.substringAfter("    fun confirm(").substringBefore("    private fun meetsItemRequirement(")
        assertEquals(3, Regex("refused\\(player\\)").findAll(select).count())
        // N449: the elite upgrade without the normal piece refuses with the sound too (4); a given reward plays the shops' sound.
        assertEquals(4, Regex("refused\\(player\\)").findAll(confirm).count())
        assertTrue("click(player)" in select && "click(player)" in confirm)
        assertEquals(1, Regex("bought\\(player\\)").findAll(confirm).count(), "N449: a bought reward plays the transaction sound")
        assertTrue("ShopSounds.TRANSACTION" in source)
        val death = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/death/death_screens.plugin.kts").readText()
        assertEquals(6, Regex("officeClick\\(player\\)").findAll(death).count(), "item pick + Take 1 / 5 / All / X / Take all")
    }
}
