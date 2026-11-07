package gg.rsmod.plugins.content.activity.toa.ui

import gg.rsmod.game.message.impl.IfSetTextMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.content.activity.toa.ToaRecordStore
import gg.rsmod.plugins.content.activity.toa.core.ToaMode
import io.mockk.*
import java.nio.file.Files
import kotlin.test.*

class ToaScoreboardUiTests {
    @Test fun `opening and changing tabs reads server records and eight holder names with no personal bests`() {
        val file = Files.createTempDirectory("toa-scoreboard-ui").toFile().resolve("records.json")
        val store = ToaRecordStore(file)
        val names = (1..8).map { "Member $it" }
        store.submit(ToaMode.EXPERT, 101, names)
        store.submit(ToaMode.ENTRY, 50, listOf("Entry name"))
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.interfaces.isVisible(1192) } returns true
        mockkObject(ToaInterfaces, ToaRecordStore.Companion)
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        try {
            every { ToaInterfaces.of(ToaInterfaces.STATISTICS) } returns 1192
            every { ToaRecordStore.server } returns store
            every { player.openInterface(1192, InterfaceDestination.MAIN_SCREEN) } just Runs
            ToaScoreboardUi.open(player, ToaMode.EXPERT)
            verify { player.write(IfSetTextMessage(1192, 83, "1:00.60")) }
            assertEquals(names, ToaScoreboardUi.click(player, 83)?.names)
            assertNull(ToaScoreboardUi.click(player, 55))
            assertNull(ToaScoreboardUi.click(player, 85))
            verify { player.write(IfSetTextMessage(1192, 55, "0:30.00")) }
            assertEquals(listOf("Entry name"), ToaScoreboardUi.click(player, 55)?.names)
            assertNull(ToaScoreboardUi.click(player, 83))
            assertNull(ToaScoreboardUi.click(player, -1))
            every { player.interfaces.isVisible(1192) } returns false
            assertNull(ToaScoreboardUi.click(player, 55))
        } finally {
            unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
            unmockkObject(ToaInterfaces, ToaRecordStore.Companion)
            file.delete(); file.parentFile.delete()
        }
    }
}
