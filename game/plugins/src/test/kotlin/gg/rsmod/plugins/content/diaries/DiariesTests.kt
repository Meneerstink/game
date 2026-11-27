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
        every { p.skills } returns maxed
        every { p.combatLevel } returns 126
    }
    private val maxed = mockk<gg.rsmod.game.model.skill.SkillSet>().also { s ->
        every { s.getMaxLevel(any()) } returns 99
        every { s.getCurrentLevel(any()) } returns 99
    }
    private val headgear = intArrayOf(30001, 30002, 30003, 30004)

    init {
        DiaryRewards.ids = mapOf(Diary.KANDARIN to headgear, Diary.WESTERN to intArrayOf(30011, 30012, 30013, 30014), Diary.WILDERNESS to intArrayOf(30021, 30022, 30023, 30024),
            Diary.VARROCK to intArrayOf(30031, 30032, 30033, 30034), Diary.ARDOUGNE to intArrayOf(30041, 30042, 30043, 30044), Diary.FALADOR to intArrayOf(30051, 30052, 30053, 30054), Diary.LUMBRIDGE to intArrayOf(30061, 30062, 30063, 30064), Diary.KARAMJA to intArrayOf(30071, 30072, 30073, 30074), Diary.FREMENNIK to intArrayOf(30081, 30082, 30083, 30084), Diary.MORYTANIA to intArrayOf(30091, 30092, 30093, 30094), Diary.DESERT to intArrayOf(30101, 30102, 30103, 30104))
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
        assertEquals(42, Diaries.totalCount(Diary.VARROCK))
        assertEquals(listOf(14, 13, 10, 5), Tier.values().map { Diaries.totalCount(Diary.VARROCK, it) })
        assertEquals(listOf(10, 12, 12, 8), Tier.values().map { Diaries.totalCount(Diary.ARDOUGNE, it) })
        assertEquals(listOf(11, 14, 11, 6), Tier.values().map { Diaries.totalCount(Diary.FALADOR, it) })
        assertEquals(listOf(12, 12, 11, 6), Tier.values().map { Diaries.totalCount(Diary.LUMBRIDGE, it) })
        assertEquals(listOf(10, 19, 10, 5), Tier.values().map { Diaries.totalCount(Diary.KARAMJA, it) })
        assertEquals(listOf(10, 9, 9, 6), Tier.values().map { Diaries.totalCount(Diary.FREMENNIK, it) })
        assertEquals(listOf(11, 11, 10, 6), Tier.values().map { Diaries.totalCount(Diary.MORYTANIA, it) })
        assertEquals(listOf(11, 12, 10, 6), Tier.values().map { Diaries.totalCount(Diary.DESERT, it) })
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
    fun `journal reads like the OSRS scroll - header, tiers, done tasks struck through, requirements`() {
        val skills = mockk<gg.rsmod.game.model.skill.SkillSet>()
        every { player.skills } returns skills
        every { skills.getMaxLevel(any()) } returns 1
        every { skills.getMaxLevel(10) } returns 99 // Fishing
        every { skills.getCurrentLevel(any()) } returns 1
        val first = Diaries.tasks(Diary.KANDARIN, Tier.EASY).first()
        Diaries.adminSet(player, first.id, true)
        val lines = DiaryUi.journalLines(player, Diary.KANDARIN)
        assertEquals("<col=800000>Kandarin Tasks</col>", lines[0])
        assertEquals("<col=800000>Easy</col>", lines[2])
        assertTrue(lines[3].startsWith("<str>") && !lines[3].contains("("), lines[3])
        assertEquals(Tier.values().map { "<col=800000>${it.title}</col>" }, lines.filter { l -> Tier.values().any { l == "<col=800000>${it.title}</col>" } })
        assertTrue(lines.size <= DiaryUi.J.LINES)
        // A task with a skill requirement: met = struck through, missing = dark red.
        assertTrue(DiaryUi.requirement(player, "Fishing 16").startsWith("<str>Fishing 16"))
        assertTrue(DiaryUi.requirement(player, "Farming 13").startsWith("<col=800000>Farming 13"))
        assertEquals("<col=800000>Western Area Tasks</col>", DiaryUi.journalLines(player, Diary.WESTERN)[0])
        lines.forEach { assertTrue(it.replace(Regex("<[^>]*>"), "").length <= DiaryUi.JOURNAL_CHARS + 30, it) }
    }

    @Test
    fun `a task needs its OSRS requirement - boosted counts, it completes once the level is there`() {
        val task = Diaries.TASKS.first { it.req == "Agility 35" && it.trigger is Diaries.Trigger.Ev }
        val key = (task.trigger as Diaries.Trigger.Ev).key
        val low = mockk<gg.rsmod.game.model.skill.SkillSet>()
        every { player.skills } returns low
        every { low.getMaxLevel(any()) } returns 30
        every { low.getCurrentLevel(any()) } returns 30
        Diaries.event(player, key)
        assertFalse(Diaries.isDone(player, task.id))
        every { low.getCurrentLevel(16) } returns 35 // a boost to 35
        Diaries.event(player, key)
        assertTrue(Diaries.isDone(player, task.id))
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

    @Test
    fun `wilderness elite notes dragon bones only in the wilderness and only after claiming`() {
        val bones = listOf(gg.rsmod.game.model.item.Item(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES, 1))
        val wildy = gg.rsmod.game.model.Tile(3100, 3900, 0)
        val lumbridge = gg.rsmod.game.model.Tile(3222, 3218, 0)
        assertEquals(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES, DiaryItems.wildyDragonBones(player, wildy, bones)[0].id)
        Diaries.setClaimed(player, Diary.WILDERNESS, 4)
        assertEquals(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES_NOTED, DiaryItems.wildyDragonBones(player, wildy, bones)[0].id)
        assertEquals(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES, DiaryItems.wildyDragonBones(player, lumbridge, bones)[0].id)
        Diaries.setClaimed(player, Diary.WILDERNESS, 3)
        assertEquals(gg.rsmod.plugins.api.cfg.Items.DRAGON_BONES, DiaryItems.wildyDragonBones(player, wildy, bones)[0].id)
    }

    @Test
    fun `camelot to seers needs both the kandarin hard claim and the choice`() {
        assertFalse(DiaryItems.camelotToSeers(player))
        attr[DiaryItems.CAMELOT_SEERS] = true
        assertFalse(DiaryItems.camelotToSeers(player))
        Diaries.setClaimed(player, Diary.KANDARIN, 3)
        assertTrue(DiaryItems.camelotToSeers(player))
        attr[DiaryItems.CAMELOT_SEERS] = false
        assertFalse(DiaryItems.camelotToSeers(player))
    }

    @Test
    fun `rogues chest gives nothing without the medium wilderness diary`() {
        Diaries.setClaimed(player, Diary.WILDERNESS, 1)
        repeat(50) { assertTrue(RoguesChest.roll(player).isEmpty()) }
    }

    @Test
    fun `headgear tier follows the reward item order`() {
        assertEquals(3, DiaryItems.tierOf(player, Diary.KANDARIN, headgear[2]))
        assertEquals(4, DiaryItems.tierOf(player, Diary.KANDARIN, headgear[3]))
        assertEquals(0, DiaryItems.tierOf(player, Diary.KANDARIN, 1))
    }

    @Test
    fun `varrock - 100 earth runes must come in one go, the palace altar task needs Smite`() {
        val task = Diaries.task("VAR_EL5")!!
        val gain = task.trigger as Diaries.Trigger.Gain
        assertTrue(gain.once && gain.count == 100, "elite 5 counts one craft of 100 or more")
        assertEquals("Varrock", Diary.VARROCK.title)
        assertTrue((Diaries.task("VAR_HA9")!!.trigger as Diaries.Trigger.PrayUp).cond != null)
    }

    @Test
    fun `slayer tower xp - 2,5 percent per claimed Morytania tier, every floor and the basement, nothing outside`() {
        every { player.tile } returns gg.rsmod.game.model.Tile(3420, 3550, 0)
        assertEquals(1.0, DiaryItems.slayerTowerFactor(player), 1e-12)
        Diaries.setClaimed(player, Diary.MORYTANIA, 4)
        assertEquals(1.1, DiaryItems.slayerTowerFactor(player), 1e-12)
        every { player.tile } returns gg.rsmod.game.model.Tile(3420, 3550, 2)
        assertEquals(1.1, DiaryItems.slayerTowerFactor(player), 1e-12)
        every { player.tile } returns gg.rsmod.game.model.Tile(3420, 9960, 0)
        assertEquals(1.1, DiaryItems.slayerTowerFactor(player), 1e-12)
        Diaries.setClaimed(player, Diary.MORYTANIA, 1)
        assertEquals(1.025, DiaryItems.slayerTowerFactor(player), 1e-12)
        every { player.tile } returns gg.rsmod.game.model.Tile(3492, 3472, 0) // Canifis
        assertEquals(1.0, DiaryItems.slayerTowerFactor(player), 1e-12)
    }
}
