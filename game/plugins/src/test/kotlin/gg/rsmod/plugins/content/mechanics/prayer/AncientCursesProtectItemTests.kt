package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.setVarp
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Source-backed level/unlock coverage for Ancient-book Protect Item. */
class AncientCursesProtectItemTests {
    private fun player(
        prayerLevel: Int,
        unlocked: Boolean = true,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val definitions = mockk<DefinitionSet>()
        every { definitions.get(VarbitDef::class.java, any()) } returns VarbitDef(0)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { player.world } returns world
        every { player.attr } returns AttributeMap().apply {
            if (unlocked) this[AncientCurses.UNLOCKED_ATTR] = true
        }
        every { player.varps } returns VarpSet((0..8000).toSet())
        every { player.varcs } returns MutableList(2000) { 0 }
        every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, prayerLevel) }
        every { player.isDead() } returns false
        every { player.lock.canUsePrayer() } returns true
        return player
    }

    @Test
    fun `direct curse Protect Item requires level fifty and the unlock ritual`() {
        val underLevel = player(AncientCurses.PROTECT_ITEM_LEVEL - 1)
        AncientCurses.switchBook(underLevel, AncientCurses.PrayerBook.ANCIENT)
        underLevel.setVarp(2382, 49)
        runBlocking {
            AncientCurses.onBookButton(QueueTask(underLevel, TaskPriority.STANDARD), AncientCurse.PROTECT_ITEM_SLOT)
        }
        assertFalse(Prayers.isActive(underLevel, Prayer.PROTECT_ITEM))

        val locked = player(AncientCurses.PROTECT_ITEM_LEVEL, unlocked = false)
        AncientCurses.switchBook(locked, AncientCurses.PrayerBook.ANCIENT)
        locked.setVarp(2382, AncientCurses.PROTECT_ITEM_LEVEL)
        runBlocking {
            AncientCurses.onBookButton(QueueTask(locked, TaskPriority.STANDARD), AncientCurse.PROTECT_ITEM_SLOT)
        }
        assertFalse(Prayers.isActive(locked, Prayer.PROTECT_ITEM))
    }

    @Test
    fun `quick curse Protect Item uses the level fifty requirement`() {
        val underLevel = player(AncientCurses.PROTECT_ITEM_LEVEL - 1)
        AncientCurses.switchBook(underLevel, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.selectQuickCurse(underLevel, AncientCurse.PROTECT_ITEM_SLOT)
        assertEquals(emptyList(), AncientCurses.selectedQuickCurseSlots(underLevel))

        val eligible = player(AncientCurses.PROTECT_ITEM_LEVEL)
        AncientCurses.switchBook(eligible, AncientCurses.PrayerBook.ANCIENT)
        AncientCurses.selectQuickCurse(eligible, AncientCurse.PROTECT_ITEM_SLOT)
        assertTrue(AncientCurses.selectedQuickCurseSlots(eligible).contains(AncientCurse.PROTECT_ITEM_SLOT))
    }
}
