package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DIARIES-OSRS D7: the Kandarin, Western Provinces and Wilderness diaries. Every task has a unique id and the OSRS task totals; a tier
 * is claimed only in order and only once; a lost reward item comes back only when none of that diary's items is owned (no dupe).
 */
class DiariesTests {
    private val definitions = mockk<DefinitionSet>()
    private val world = mockk<World>(relaxed = true)
    private val attr = AttributeMap()
    private val inv = ItemContainer(definitions, INVENTORY_KEY)
    private val equipment = ItemContainer(definitions, EQUIPMENT_KEY)
    private val bank = ItemContainer(definitions, BANK_KEY)
    private val player = mockk<Player>(relaxed = true).also { p ->
        every { p.world } returns world
        every { p.attr } returns attr
        every { p.inventory } returns inv
        every { p.equipment } returns equipment
        every { p.bank } returns bank
        every { p.username } returns "diarytest"
        every { world.definitions } returns definitions
        every { definitions.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
    }
    private val headgear = intArrayOf(30001, 30002, 30003, 30004)

    init {
        DiaryRewards.ids = mapOf(Diary.KANDARIN to headgear, Diary.WESTERN to intArrayOf(30011, 30012, 30013, 30014), Diary.WILDERNESS to intArrayOf(30021, 30022, 30023, 30024))
    }

    private fun completeTier(diary: Diary, tier: Tier) =
        Diaries.tasks(diary, tier).forEach { Diaries.adminSet(player, it.id, true) }

    @Test
    fun `every task id is unique and the totals are the OSRS ones`() {
        val ids = Diaries.TASKS.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate ids: ${ids.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}")
        assertEquals(43, Diaries.totalCount(Diary.KANDARIN))
        assertEquals(44, Diaries.totalCount(Diary.WESTERN))
        assertEquals(40, Diaries.totalCount(Diary.WILDERNESS))
        Diary.values().forEach { d -> Tier.values().forEach { t -> assertTrue(Diaries.totalCount(d, t) > 0, "$d $t has no tasks") } }
        // The panel shows the same totals for the three diaries that exist.
        DiaryUi.ROWS.forEachIndexed { i, (_, osrs) -> DiaryUi.diaryOfRow(i)?.let { assertEquals(osrs, Diaries.totalCount(it), "row $i") } }
    }

    @Test
    fun `tiers are claimed in order, once, with the item and the lamp`() {
        assertNull(DiaryRewards.claimable(player, Diary.KANDARIN))
        completeTier(Diary.KANDARIN, Tier.MEDIUM)
        assertNull(DiaryRewards.claimable(player, Diary.KANDARIN), "medium done but easy not: nothing to claim")
        completeTier(Diary.KANDARIN, Tier.EASY)
        assertEquals(Tier.EASY, DiaryRewards.claimable(player, Diary.KANDARIN))
        DiaryRewards.claim(player, Diary.KANDARIN)
        assertEquals(1, inv.getItemCount(headgear[0]))
        assertEquals(1, inv.getItemCount(DiaryRewards.LAMPS[0]))
        assertEquals(Tier.MEDIUM, DiaryRewards.claimable(player, Diary.KANDARIN))
        DiaryRewards.claim(player, Diary.KANDARIN)
        assertEquals(2, Diaries.claimed(player, Diary.KANDARIN))
        assertTrue(Diaries.hasClaimed(player, Diary.KANDARIN, Tier.MEDIUM))
        assertFalse(Diaries.hasClaimed(player, Diary.KANDARIN, Tier.HARD))
        assertNull(DiaryRewards.claimable(player, Diary.KANDARIN), "hard not done")
        assertEquals("You have no rewards to claim.", DiaryRewards.claim(player, Diary.KANDARIN))
        assertEquals(1, inv.getItemCount(DiaryRewards.LAMPS[1]))
    }

    @Test
    fun `a full inventory claims nothing`() {
        completeTier(Diary.WILDERNESS, Tier.EASY)
        inv.add(995, 1)
        for (i in 0 until 26) inv.add(1000 + i * 2, 1)
        assertEquals(1, inv.freeSlotCount)
        DiaryRewards.claim(player, Diary.WILDERNESS)
        assertEquals(0, Diaries.claimed(player, Diary.WILDERNESS))
    }

    @Test
    fun `a lost item comes back only when none is owned - inventory, worn or banked`() {
        completeTier(Diary.KANDARIN, Tier.EASY)
        completeTier(Diary.KANDARIN, Tier.MEDIUM)
        DiaryRewards.claim(player, Diary.KANDARIN)
        DiaryRewards.claim(player, Diary.KANDARIN)
        DiaryRewards.reclaim(player, Diary.KANDARIN)
        assertEquals(2, inv.getItemCount(headgear[0]) + inv.getItemCount(headgear[1]), "owned: no extra copy")
        inv.remove(headgear[0], 1)
        bank.add(headgear[1], 1)
        inv.remove(headgear[1], 1)
        DiaryRewards.reclaim(player, Diary.KANDARIN)
        assertEquals(0, inv.getItemCount(headgear[1]), "banked: no extra copy")
        bank.remove(headgear[1], 1)
        DiaryRewards.reclaim(player, Diary.KANDARIN)
        assertEquals(1, inv.getItemCount(headgear[1]), "lost: the highest claimed tier comes back")
        DiaryRewards.reclaim(player, Diary.KANDARIN)
        assertEquals(1, inv.getItemCount(headgear[1]), "a second reclaim gives nothing")
    }

    @Test
    fun `an event finishes its task once and is saved`() {
        val task = Diaries.TASKS.first { it.trigger is Diaries.Trigger.Ev && (it.trigger as Diaries.Trigger.Ev).area == null }
        val key = (task.trigger as Diaries.Trigger.Ev).key
        Diaries.event(player, key)
        assertTrue(Diaries.isDone(player, task.id))
        val count = Diaries.doneCount(player, task.diary)
        Diaries.event(player, key)
        assertEquals(count, Diaries.doneCount(player, task.diary))
        assertTrue(attr[Diaries.DONE]!!.contains(task.id))
    }
}
