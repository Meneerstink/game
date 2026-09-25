package gg.rsmod.plugins.content.mechanics.poison

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/** Audit C-08: poison and venom keep ticking while a modal interface (bank, shop) is open, as in OSRS. */
class PoisonModalTests {
    @Test
    fun `poison and venom timers do not pause on an open modal`() {
        listOf("poison_plugin.plugin.kts", "venom_plugin.plugin.kts").forEach { name ->
            val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/poison/$name").readText()
            assertFalse("currentModal" in source, "$name must not pause on a modal")
        }
    }
}
