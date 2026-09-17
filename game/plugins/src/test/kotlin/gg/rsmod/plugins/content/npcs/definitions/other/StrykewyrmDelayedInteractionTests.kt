package gg.rsmod.plugins.content.npcs.definitions.other

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: a delayed Strykewyrm burrow must not touch a logged-out victim. */
class StrykewyrmDelayedInteractionTests {
    @Test
    fun `burrow rejects an offline player before delayed damage and effects`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/npcs/definitions/other/Strykewyrms.kt").readText()
        val burrow = source.substringAfter("private suspend fun burrow").substringBefore("object")

        assertTrue(
            "target is Player && !target.isOnline" in burrow,
            "delayed Strykewyrm burrow must stop after victim logout",
        )
        assertTrue(burrow.indexOf("isOnline") < burrow.indexOf("target.hit(300"))
    }
}
