package gg.rsmod.plugins.content.items.osrs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** N197 (owner 2026-10-22): a Magic shortbow scroll used on a Magic shortbow asks first, then makes a Magic shortbow (i) with a sound. */
class MagicShortbowScrollTests {
    @Test
    fun `the scroll on the bow asks, imbues and plays a sound`() {
        val s = File("src/main/kotlin/gg/rsmod/plugins/content/items/osrs/magic_shortbow_scroll.plugin.kts").readText()
        assertTrue("on_item_on_item(item1 = Items.MAGIC_SHORTBOW_SCROLL, item2 = Items.MAGIC_SHORTBOW)" in s)
        val ask = s.indexOf("Are you sure?")
        val give = s.indexOf("player.inventory.add(Items.MAGIC_SHORTBOW_I, 1)")
        val sound = s.indexOf("player.playSound(")
        assertTrue(ask in 0 until give && give < sound, "asks first, then the imbued bow and the sound")
    }
}
