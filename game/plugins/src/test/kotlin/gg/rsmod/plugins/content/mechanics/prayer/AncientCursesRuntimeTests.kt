package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.queue.impl.WorldQueueTaskSet
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real varp bits and world queue suspension exercise the book and drain integration. */
class AncientCursesRuntimeTests {
    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val queues = WorldQueueTaskSet()

        init {
            val definitions = mockk<DefinitionSet>()
            every { definitions.get(VarbitDef::class.java, any()) } answers {
                val id = secondArg<Int>()
                VarbitDef(id).apply {
                    val prayer = Prayer.values.firstOrNull { it.varbit == id }
                    when {
                        id in 6820..6839 -> { varp = 1582; startBit = id - 6820 }
                        id in 6862..6881 -> { varp = 1587; startBit = id - 6862 }
                        prayer != null -> { varp = Prayers.ACTIVE_PRAYERS_VARP; startBit = prayer.slot }
                        else -> varp = id
                    }
                    endBit = startBit
                }
            }
            every { world.definitions } returns definitions
            every { world.queue(any()) } answers {
                queues.queue(world, Dispatchers.Unconfined, TaskPriority.STANDARD, firstArg<suspend QueueTask.(CoroutineScope) -> Unit>())
            }
            every { player.world } returns world
            every { player.attr } returns AttributeMap()
            every { player.varps } returns VarpSet((0..8000).toSet())
            every { player.varcs } returns MutableList(2000) { 0 }
            every { player.skills } returns SkillSet(7).apply { setBaseLevel(Skills.PRAYER, 99) }
            every { player.getCurrentPrayerPoints() } returns 99
            every { player.isOnline } returns true
            every { player.isDead() } returns false
            every { player.lock.canUsePrayer() } returns true
            player.attr[AncientCurses.UNLOCKED_ATTR] = true
        }

        fun ancient() = AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
    }

    @Test
    fun `Turmoil cannot be activated on the normal book`() {
        val fixture = Fixture()
        AncientCurses.toggleTurmoil(fixture.player)
        assertFalse(AncientCurses.isTurmoilActive(fixture.player))
        assertEquals(0, fixture.queues.size)
    }

    @Test
    fun `activating a curse no longer starts a private drain loop`() {
        // 2026-09-06: curses drain through Prayers' shared per-tick counter, not through one
        // `world.queue { while (...) wait(n) }` coroutine each. The three tests that used to live
        // here pinned the retirement/suspension semantics of those coroutines; there are none left
        // to retire, and the behaviour they protected (a re-toggled curse must not double-charge)
        // is now structural - a curse contributes to `activeCurseDrainEffect` exactly while it is
        // in the active set.
        val fixture = Fixture()
        fixture.ancient()
        AncientCurses.toggleCurse(fixture.player, AncientCurse.SAP_WARRIOR)
        AncientCurses.toggleTurmoil(fixture.player)
        repeat(10) { fixture.queues.cycle() }
        assertEquals(0, fixture.queues.size)
        verify(exactly = 0) { fixture.player.decreasePrayerPoints(any()) }
    }

    @Test
    fun `active drain effect sums every active curse and Turmoil`() {
        val fixture = Fixture()
        fixture.ancient()
        assertEquals(0, AncientCurses.activeCurseDrainEffect(fixture.player))

        AncientCurses.toggleCurse(fixture.player, AncientCurse.DEFLECT_MELEE)
        assertEquals(AncientCurse.DEFLECT_MELEE.drainEffect, AncientCurses.activeCurseDrainEffect(fixture.player))

        AncientCurses.toggleCurse(fixture.player, AncientCurse.DEFLECT_SUMMONING)
        assertEquals(
            AncientCurse.DEFLECT_MELEE.drainEffect + AncientCurse.DEFLECT_SUMMONING.drainEffect,
            AncientCurses.activeCurseDrainEffect(fixture.player),
        )

        AncientCurses.toggleTurmoil(fixture.player)
        assertEquals(
            AncientCurse.DEFLECT_MELEE.drainEffect + AncientCurse.DEFLECT_SUMMONING.drainEffect +
                AncientCurse.TURMOIL_DRAIN_EFFECT,
            AncientCurses.activeCurseDrainEffect(fixture.player),
        )

        AncientCurses.deactivateAllCurses(fixture.player)
        assertEquals(0, AncientCurses.activeCurseDrainEffect(fixture.player))
    }

    @Test
    fun `Deflect Summoning and a combat Deflect render the one combined overhead`() {
        val fixture = Fixture()
        fixture.ancient()
        AncientCurses.toggleCurse(fixture.player, AncientCurse.DEFLECT_SUMMONING)
        AncientCurses.toggleCurse(fixture.player, AncientCurse.DEFLECT_MISSILES)
        verify { fixture.player.prayerIcon = PrayerIcon.DEFLECT_SUMMONING_AND_MISSILES.id }
    }
    @Test
    fun `quick curses preserve compatible selections and remove conflicting saps and Turmoil`() {
        val fixture = Fixture()
        fixture.ancient()
        val player = fixture.player
        AncientCurses.selectQuickCurse(player, AncientCurse.BERSERKER.slot)
        AncientCurses.selectQuickCurse(player, AncientCurse.SAP_WARRIOR.slot)
        AncientCurses.selectQuickCurse(player, AncientCurse.SAP_RANGER.slot)
        // Owner 2026-09-18: Sap Ranger replaces Sap Warrior (style saps are exclusive).
        assertEquals(setOf(2, 5),AncientCurses.selectedQuickCurseSlots(player).toSet())
        AncientCurses.selectQuickCurse(player, AncientCurse.TURMOIL_SLOT)
        assertEquals(setOf(5, 19), AncientCurses.selectedQuickCurseSlots(player).toSet())
        AncientCurses.selectQuickCurse(player, AncientCurse.LEECH_ATTACK.slot)
        assertEquals(setOf(5, 10), AncientCurses.selectedQuickCurseSlots(player).toSet())
        AncientCurses.selectQuickCurse(player, AncientCurse.LEECH_ATTACK.slot)
        assertEquals(listOf(5), AncientCurses.selectedQuickCurseSlots(player))
    }

    @Test
    fun `quick curse overhead selection uses the ordinary curse exclusions`() {
        val fixture = Fixture()
        fixture.ancient()
        val player = fixture.player
        AncientCurses.selectQuickCurse(player, AncientCurse.DEFLECT_MELEE.slot)
        AncientCurses.selectQuickCurse(player, AncientCurse.DEFLECT_SUMMONING.slot)
        assertEquals(setOf(6, 9), AncientCurses.selectedQuickCurseSlots(player).toSet())
        AncientCurses.selectQuickCurse(player, AncientCurse.SOUL_SPLIT.slot)
        assertEquals(listOf(18), AncientCurses.selectedQuickCurseSlots(player))
    }

    @Test
    fun `orb activates and deactivates quick curses including Protect Item and Turmoil`() {
        val fixture = Fixture()
        fixture.ancient()
        val player = fixture.player
        listOf(0, 5, 19).forEach { AncientCurses.selectQuickCurse(player, it) }
        Prayers.toggleQuickPrayers(player, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        assertTrue(AncientCurses.isTurmoilActive(player))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.BERSERKER))
        assertTrue(Prayers.isActive(player, Prayer.PROTECT_ITEM))
        assertTrue(player.attr[PROTECT_ITEM_ATTR] == true)
        assertEquals(1, player.getVarbit(AncientCurse.PROTECT_ITEM_VARBIT))
        assertEquals(1, player.varcs[Prayers.QUICK_PRAYERS_ACTIVE_VARC])
        Prayers.toggleQuickPrayers(player, Prayers.QUICK_PRAYERS_TOGGLE_OPTION)
        assertFalse(AncientCurses.isTurmoilActive(player))
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.BERSERKER))
        assertFalse(Prayers.isActive(player, Prayer.PROTECT_ITEM))
        assertFalse(player.attr[PROTECT_ITEM_ATTR] == true)
        assertEquals(0, player.getVarbit(AncientCurse.PROTECT_ITEM_VARBIT))
        assertEquals(listOf(0, 5, 19), AncientCurses.selectedQuickCurseSlots(player))
    }

    @Test
    fun `switching books clears active curses and quick mode but preserves selection`() {
        val fixture = Fixture()
        fixture.ancient()
        val player = fixture.player
        AncientCurses.selectQuickCurse(player, AncientCurse.SAP_WARRIOR.slot)
        AncientCurses.toggleQuickCurses(player)
        Prayers.toggleQuickPrayers(player, Prayers.QUICK_PRAYERS_SELECT_OPTION)
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
        assertFalse(AncientCurses.isCurseActive(player, AncientCurse.SAP_WARRIOR))
        assertEquals(0, player.getVarbit(AncientCurse.BOOK_VARBIT))
        assertEquals(0, player.varcs[Prayers.QUICK_PRAYER_SELECT_MODE_VARC])
        assertEquals(0, player.varcs[Prayers.QUICK_PRAYERS_ACTIVE_VARC])
        assertEquals(listOf(1), AncientCurses.selectedQuickCurseSlots(player))
        repeat(10) { fixture.queues.cycle() }
        verify(exactly = 0) { player.decreasePrayerPoints(any()) }
    }

    @Test
    fun `persisted high-level quick selection is rechecked before activation`() {
        val fixture = Fixture()
        fixture.ancient()
        val player = fixture.player
        player.setVarbit(AncientCurse.QUICK_VARBIT_BASE + AncientCurse.TURMOIL_SLOT, 1)
        player.skills.setBaseLevel(Skills.PRAYER, 50)
        AncientCurses.toggleQuickCurses(player)
        assertFalse(AncientCurses.isTurmoilActive(player))
        assertEquals(0, player.varcs[Prayers.QUICK_PRAYERS_ACTIVE_VARC])
    }

    @Test
    fun `toggling Protect Item on the curse book does not clear an active curse's overhead icon`() {
        // Regression for a real bug: Protect Item is the normal book's shared effect, so
        // `onBookButton`'s PROTECT_ITEM_SLOT case runs through `Prayers.toggle`, which ends in
        // `Prayers.setOverhead` - a function that only knows about the 7 normal Protect/
        // Retribution/Smite/Redemption prayers, none of which can be active on the curse book, so
        // it used to unconditionally compute PrayerIcon.NONE and silently wipe out a genuinely
        // still-active curse's overhead (e.g. Deflect Melee).
        val fixture = Fixture()
        val player = fixture.player
        var currentIcon = -1
        every { player.prayerIcon } answers { currentIcon }
        every { player.prayerIcon = any() } answers { currentIcon = firstArg() }
        val task = QueueTask(ctx = player, priority = TaskPriority.STANDARD)

        fixture.ancient()
        // `Prayers.rechargePrayerPoints` calls `Player.setCurrentPrayerPoints`, a real member on
        // the actual class - on this relaxed mock that's a no-op, so set the real backing varp
        // directly (mirrors the private `Prayers.PRAYER_POINTS_VARP = 2382`) for `Prayers.toggle`'s
        // own points-remaining guard.
        player.setVarp(2382, 99)
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MELEE)
        assertEquals(PrayerIcon.DEFLECT_MELEE.id, currentIcon)

        kotlinx.coroutines.runBlocking { AncientCurses.onBookButton(task, AncientCurse.PROTECT_ITEM_SLOT) }

        assertTrue(Prayers.isActive(player, Prayer.PROTECT_ITEM))
        assertEquals(PrayerIcon.DEFLECT_MELEE.id, currentIcon)
    }

    @Test
    fun `Deflect Summoning stacked with a combat Deflect shows the combined overhead`() {
        // wiki.darkan.org: "Deflect Summoning can be used with other Deflect Curses, but not with
        // Wrath, or Soul Split" - both can be genuinely active together. 2026-09-06: the decoded
        // headicons_prayer sheet has a dedicated frame for that pairing (see [PrayerIcon]), so
        // neither icon has to be dropped any more; frame 18 shows the wolf and the wizard hat.

        val fixture = Fixture()
        val player = fixture.player
        var currentIcon = -1
        every { player.prayerIcon } answers { currentIcon }
        every { player.prayerIcon = any() } answers { currentIcon = firstArg() }

        fixture.ancient()
        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_SUMMONING)
        assertEquals(PrayerIcon.DEFLECT_SUMMONING.id, currentIcon)

        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MAGIC)
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_SUMMONING))
        assertTrue(AncientCurses.isCurseActive(player, AncientCurse.DEFLECT_MAGIC))
        assertEquals(PrayerIcon.DEFLECT_SUMMONING_AND_MAGIC.id, currentIcon)

        AncientCurses.toggleCurse(player, AncientCurse.DEFLECT_MAGIC)
        assertEquals(PrayerIcon.DEFLECT_SUMMONING.id, currentIcon)
    }
}
