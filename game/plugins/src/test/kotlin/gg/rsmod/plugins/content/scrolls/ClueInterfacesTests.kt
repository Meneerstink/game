package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.message.impl.IfSetHideMessage
import gg.rsmod.game.message.impl.IfSetObjectMessage
import gg.rsmod.game.model.ExamineEntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.openInterface
import io.mockk.*
import kotlin.test.*

class ClueInterfacesTests {
    private val world = mockk<World>(relaxed = true)
    private fun account(): Player = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.attr } returns AttributeMap()
        every { p.interfaces.isVisible(1196) } returns true
        every { p.openInterface(any<Int>(), any<InterfaceDestination>(), any<Boolean>()) } just Runs
    }

    @BeforeTest fun setup() {
        mockkObject(ClueInterfaces)
        every { ClueInterfaces.of(73) } returns 1196
        every { ClueInterfaces.of(203) } returns 1197
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
    }

    @AfterTest fun cleanup() {
        unmockkObject(ClueInterfaces)
        unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
    }

    @Test fun `reopening a smaller receipt hides old rewards and invalidates their examine buttons`() {
        val player = account()
        ClueInterfaces.rewards(player, listOf(Item(995, 3), Item(2000), Item(995, 4)))
        verify { player.write(IfSetObjectMessage((1196 shl 16) or 5, 995, 7)) }
        verify { player.write(IfSetObjectMessage((1196 shl 16) or 6, 2000, 1)) }
        ClueInterfaces.rewards(player, listOf(Item(3000)))
        verify { player.write(IfSetHideMessage((1196 shl 16) or 6, true)) }
        ClueInterfaces.examine(player, 6)
        verify(exactly = 0) { world.sendExamine(any(), 2000, ExamineEntityType.ITEM) }
        ClueInterfaces.examine(player, 5)
        verify(exactly = 1) { world.sendExamine(player, 3000, ExamineEntityType.ITEM) }
        every { player.interfaces.isVisible(1196) } returns false
        ClueInterfaces.examine(player, 5)
        verify(exactly = 1) { world.sendExamine(player, 3000, ExamineEntityType.ITEM) }
    }

    @Test fun `reward examines belong to the viewing account and opening a receipt awards nothing`() {
        val first = account(); val second = account()
        ClueInterfaces.rewards(first, listOf(Item(2000)))
        ClueInterfaces.rewards(second, listOf(Item(3000)))
        ClueInterfaces.examine(first, 5); ClueInterfaces.examine(second, 5)
        verify(exactly = 1) { world.sendExamine(first, 2000, ExamineEntityType.ITEM) }
        verify(exactly = 1) { world.sendExamine(second, 3000, ExamineEntityType.ITEM) }
        verify(exactly = 0) { world.sendExamine(first, 3000, ExamineEntityType.ITEM) }
        verify(exactly = 0) { first.inventory; second.inventory }
    }

    @Test fun `missing imports cannot open an invented interface`() {
        val player = account()
        every { ClueInterfaces.of(any()) } returns null
        assertFalse(ClueInterfaces.requireRewardScreen(player))
        assertFalse(ClueInterfaces.text(player, "A sourced clue"))
        verify(exactly = 0) { player.openInterface(any<Int>(), any<InterfaceDestination>(), any<Boolean>()) }
    }
}
