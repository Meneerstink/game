package gg.rsmod.plugins.content.mechanics

import gg.rsmod.plugins.api.cfg.Sfx
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-10-22: N64 storing / taking runes in a rune pouch makes a sound; N65 depositing into the bank plays the bank drawer
 * sound (owner's choice), not the drop sound (avtrace on the test server: 2739 from ItemMoveSounds); N89 "not enough coins/points"
 * in every shop and store plays the error sound.
 */
class ItemSoundsN64N65N89Tests {
    private fun src(path: String) = File("src/main/kotlin/gg/rsmod/plugins/content/$path").readText()

    @Test
    fun `N65 - the bank deposit plays the bank drawer sound`() {
        assertEquals(Sfx.BANK_DRAWER, ItemMoveSounds.BANK_DEPOSIT)
        assertTrue("ItemMoveSounds.bankDeposit(player)" in src("inter/bank/Bank.kt"))
    }

    @Test
    fun `N64 - every rune pouch store and take-out path makes a sound`() {
        val window = src("magic/RunePouchInterface.kt")
        assertTrue("ItemMoveSounds.takeOut(player)" in window && "ItemMoveSounds.putIn(player)" in window)
        val plugin = src("magic/rune_pouch.plugin.kts")
        assertTrue("ItemMoveSounds.putIn(player)" in plugin && "ItemMoveSounds.takeOut(player)" in plugin)
    }

    @Test
    fun `N89 - every not-enough refusal goes through the line-and-sound helper`() {
        listOf("mechanics/shops/ItemCurrency.kt", "mechanics/shops/PointCurrency.kt").forEach { path ->
            val s = src(path)
            assertTrue("p.message(\"You don't have enough \$pluralCurrency.\")" !in s, "$path still refuses silently")
            assertTrue("p.notEnough(\"You don't have enough \$pluralCurrency.\")" in s, path)
        }
        assertTrue("player.notEnough(\"You need \${entry.price.format()}" in src("mechanics/store/StoreUi.kt"))
        assertTrue("playSound(Sfx.ERRORSOUND)" in src("mechanics/shops/NotEnough.kt"))
    }
}
