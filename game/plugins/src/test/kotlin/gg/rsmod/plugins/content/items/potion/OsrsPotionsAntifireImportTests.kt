package gg.rsmod.plugins.content.items.potion

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.skills.herblore.mixing.PotionData
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Owner answer Q8 + OSRS-IMPORT batch potions-antifire against the OSRS Wiki antifire potion pages (every antifire dose). */
class OsrsPotionsAntifireImportTests {
    private fun player(timers: TimerMap): Player = mockk<Player>(relaxed = true).also { every { it.timers } returns timers }

    @Test
    fun `durations, messages and mix values follow the wiki`() {
        assertEquals(600, AntifirePotions.ANTIFIRE_TICKS, "6 minutes")
        assertEquals(1200, AntifirePotions.EXTENDED_ANTIFIRE_TICKS, "12 minutes (1200 game ticks)")
        assertEquals(300, AntifirePotions.SUPER_ANTIFIRE_TICKS, "three minutes")
        assertEquals(600, AntifirePotions.EXTENDED_SUPER_ANTIFIRE_TICKS, "exactly six minutes")
        assertEquals("<col=7f007f>Your antifire potion is about to expire.</col>", AntifirePotions.ANTIFIRE_WARNING)
        assertEquals("<col=7f007f>Your super antifire potion has expired.</col>", AntifirePotions.SUPER_ANTIFIRE_EXPIRED)
        assertEquals(6, AntifirePotions.MIX_HEAL)
        listOf(PotionType.SUPER_ANTIFIRE_MIX, PotionType.EXTENDED_ANTIFIRE_MIX, PotionType.EXTENDED_SUPER_ANTIFIRE_MIX).forEach {
            assertEquals("You drink the lumpy potion", it.message, "$it")
        }
        mapOf(
            PotionData.SUPER_ANTIFIRE_MIX to listOf(Items.SUPER_ANTIFIRE_2, Items.SUPER_ANTIFIRE_MIX_2, 98, 70),
            PotionData.EXTENDED_ANTIFIRE_MIX to listOf(Items.EXTENDED_ANTIFIRE_2, Items.EXTENDED_ANTIFIRE_MIX_2, 91, 61),
            PotionData.EXTENDED_SUPER_ANTIFIRE_MIX to listOf(Items.EXTENDED_SUPER_ANTIFIRE_2, Items.EXTENDED_SUPER_ANTIFIRE_MIX_2, 99, 78),
        ).forEach { (recipe, v) ->
            assertEquals(v, listOf(recipe.primary, recipe.product, recipe.levelRequirement, recipe.experience.toInt()), "$recipe")
            assertEquals(Items.CAVIAR, recipe.secondary)
        }
    }

    @Test
    fun `every antifire dose drinks in order with its sourced timer`() {
        val expected =
            mapOf(
                PotionType.ANTIFIRE to (listOf(Items.ANTIFIRE_4, Items.ANTIFIRE_3, Items.ANTIFIRE_2, Items.ANTIFIRE_1) to (ANTIFIRE_TIMER to 600)),
                PotionType.SUPER_ANTIFIRE to (listOf(Items.SUPER_ANTIFIRE_4, Items.SUPER_ANTIFIRE_3, Items.SUPER_ANTIFIRE_2, Items.SUPER_ANTIFIRE_1) to (SUPER_ANTIFIRE_TIMER to 300)),
                PotionType.EXTENDED_ANTIFIRE to (listOf(Items.EXTENDED_ANTIFIRE_4, Items.EXTENDED_ANTIFIRE_3, Items.EXTENDED_ANTIFIRE_2, Items.EXTENDED_ANTIFIRE_1) to (ANTIFIRE_TIMER to 1200)),
                PotionType.EXTENDED_ANTIFIRE_MIX to (listOf(Items.EXTENDED_ANTIFIRE_MIX_2, Items.EXTENDED_ANTIFIRE_MIX_1) to (ANTIFIRE_TIMER to 1200)),
                PotionType.SUPER_ANTIFIRE_MIX to (listOf(Items.SUPER_ANTIFIRE_MIX_2, Items.SUPER_ANTIFIRE_MIX_1) to (SUPER_ANTIFIRE_TIMER to 300)),
                PotionType.EXTENDED_SUPER_ANTIFIRE to (listOf(Items.EXTENDED_SUPER_ANTIFIRE_4, Items.EXTENDED_SUPER_ANTIFIRE_3, Items.EXTENDED_SUPER_ANTIFIRE_2, Items.EXTENDED_SUPER_ANTIFIRE_1) to (SUPER_ANTIFIRE_TIMER to 600)),
                PotionType.EXTENDED_SUPER_ANTIFIRE_MIX to (listOf(Items.EXTENDED_SUPER_ANTIFIRE_MIX_2, Items.EXTENDED_SUPER_ANTIFIRE_MIX_1) to (SUPER_ANTIFIRE_TIMER to 600)),
            )
        expected.forEach { (type, pair) ->
            val (doses, timer) = pair
            doses.forEachIndexed { index, id ->
                val potion = Potion.values().single { it.item == id }
                assertEquals(type, potion.potionType, "$id")
                assertEquals(doses.getOrElse(index + 1) { Items.VIAL }, potion.replacement, "$id")
            }
            val timers = TimerMap()
            type.apply(player(timers))
            assertEquals(timer.second, timers.get(timer.first), "$type timer")
            val warning = if (timer.first == ANTIFIRE_TIMER) AntifirePotions.ANTIFIRE_WARNING_TIMER else AntifirePotions.SUPER_ANTIFIRE_WARNING_TIMER
            assertEquals(timer.second - AntifirePotions.WARNING_TICKS, timers.get(warning), "$type warning")
        }
    }

    @Test
    fun `a regular antifire never downgrades a running super antifire and the messages are wired`() {
        val timers = TimerMap()
        timers[SUPER_ANTIFIRE_TIMER] = 100
        PotionType.EXTENDED_ANTIFIRE.apply(player(timers))
        assertFalse(timers.has(ANTIFIRE_TIMER))
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/potion/potion_timed_effects.plugin.kts").readText()
        listOf("AntifirePotions.ANTIFIRE_WARNING)", "AntifirePotions.ANTIFIRE_EXPIRED)", "AntifirePotions.SUPER_ANTIFIRE_WARNING)", "AntifirePotions.SUPER_ANTIFIRE_EXPIRED)")
            .forEach { assertTrue(it in plugin, it) }
    }
}
