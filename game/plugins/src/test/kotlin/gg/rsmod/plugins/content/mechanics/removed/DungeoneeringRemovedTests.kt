package gg.rsmod.plugins.content.mechanics.removed

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.container.key.RANDOM_EVENT_GIFT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService
import gg.rsmod.plugins.content.mechanics.exchange.OfferType
import gg.rsmod.plugins.content.mechanics.pvp.LootingBag
import gg.rsmod.plugins.content.skills.summoning.BeastOfBurden
import io.mockk.every
import io.mockk.mockk
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DGN (owner 2026-10-11: "Chaotics en ALLES van Dungeoneering volledig verwijderen; de skill volledig weg; Daemonheim blijft
 * zonder Dungeoneering; bestaande items weg bij login; max cape zonder Dungeoneering") + N46 (Amulet of zealots 19892).
 * Class test: EVERY Dungeoneering item (the Void donor list), EVERY container a player can hold one in, the player's GE
 * offers, EVERY route that still trained, offered or counted the skill - and the normal neighbours stay.
 */
class DungeoneeringRemovedTests {
    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    /** One of every Dungeoneering kind (the whole list is checked against the donor below). */
    private val samples =
        listOf(
            Items.RING_OF_KINSHIP, Items.CHAOTIC_RAPIER, Items.CHAOTIC_KITESHIELD, Items.PRIMAL_2H_SWORD, Items.GRAVITE_2H_SWORD,
            Items.ARCANE_STREAM_NECKLACE, Items.BONECRUSHER, Items.GEM_BAG, Items.COAL_BAG, Items.HERBICIDE,
            Items.SCROLL_OF_RENEWAL, Items.SCROLL_OF_AUGURY, Items.SCROLL_OF_RIGOUR, Items.SPIRIT_CAPE,
            // N46: the Amulet of zealots, whose +5% only the client's curse scripts 1712/1713 ever applied.
            Items.AMULET_OF_ZEALOTS,
            Items.DUNGEONEERING_CAPE, 18508, 18509, 18510, 19709, 19710,
        )

    /** Normal items whose ids sit between or next to the Dungeoneering ids - they must stay. */
    private val neighbours =
        listOf(
            Items.DRAGON_PICKAXE, Items.DARK_BOW, Items.SUMMONING_CAPE, Items.KORASIS_SWORD, Items.SARADOMIN_BOW,
            Items.BLACK_PLATEBODY_H1, Items.FROST_DRAGON_MASK, Items.THIRDAGE_DRUIDIC_STAFF, Items.ELITE_VOID_KNIGHT_TOP,
            Items.ABYSSAL_WHIP, Items.COINS_995,
        )

    private fun def(id: Int) = DEFINITIONS.get(ItemDef::class.java, id)

    @Test
    fun `every Dungeoneering item of the Void donor list and its noted form is unobtainable`() {
        val donor = File("../../../../Donors/void/data/skill/dungeoneering/dungeoneering.items.toml")
        if (donor.exists()) {
            val ids = Regex("""(?m)^id = (\d+)""").findAll(donor.readText()).map { it.groupValues[1].toInt() }.toSet()
            assertTrue(ids.size > 2500, "donor list read (${ids.size})")
            assertEquals(ids - DungeoneeringItems.IDS, emptySet(), "donor ids missing from DungeoneeringItems")
            assertEquals(DungeoneeringItems.IDS - ids - DungeoneeringItems.CAPES, emptySet(), "ids not in the donor list")
        }
        (samples + DungeoneeringItems.IDS).forEach { id ->
            val d = def(id)
            assertTrue(RemovedItems.isUnobtainable(d), "$id '${d.name}' still obtainable")
            if (!d.noted && d.noteLinkId > 0) assertTrue(RemovedItems.isUnobtainable(def(d.noteLinkId)), "noted $id")
            assertFalse(GrandExchangeInterface.exchangeable(d), "$id '${d.name}' still on the Grand Exchange")
        }
        // Kept out of IDS (the removed-rune rules) and out of the Pernix / Deadman set (PernixVirtusRemovedTests).
        assertTrue(DungeoneeringItems.IDS.none { it in RemovedItems.IDS || it in RemovedItems.UNOBTAINABLE_IDS })
        neighbours.forEach { id -> assertFalse(RemovedItems.isUnobtainable(def(id)), "$id '${def(id).name}' must stay") }
        // Every id the list holds is named like Dungeoneering content in the cache, never a clue / (h) / god item.
        val wrong = DungeoneeringItems.IDS.map { def(it).name }.filter { Regex("""(?i)clue|scroll box|\(h\d\)|korasi|third-age""").containsMatchIn(it) }
        assertTrue(wrong.isEmpty(), "non-Dungeoneering names in the list: $wrong")
    }

    @Test
    fun `the login purge takes Dungeoneering items out of every container and keeps the rest`() {
        val bob = ItemContainer(DEFINITIONS, BeastOfBurden.WAR_TORTOISE_KEY)
        val bag = ItemContainer(DEFINITIONS, LootingBag.KEY)
        val gift = ItemContainer(DEFINITIONS, RANDOM_EVENT_GIFT_KEY)
        val player = player(mapOf(BeastOfBurden.WAR_TORTOISE_KEY to bob, LootingBag.KEY to bag, RANDOM_EVENT_GIFT_KEY to gift))
        // A noted Dungeoneering stack too (the chaotics have no noted form; the first noteable Dgn item of the list).
        val notedDgn = DungeoneeringItems.IDS.sorted().map(::def).first { !it.noted && it.noteLinkId > 0 }.noteLinkId
        val all = listOf(player.inventory, player.equipment, player.bank, player.gravestone, player.deathRecovery, bob, bag, gift)
        all.forEach { c ->
            c[0] = Item(Items.CHAOTIC_RAPIER)
            c[1] = Item(Items.AMULET_OF_ZEALOTS)
            c[2] = Item(Items.DRAGON_PICKAXE)
            c[3] = Item(notedDgn, 2)
        }
        val taken = RemovedItems.purge(player)
        all.forEach { c ->
            assertNull(c[0], "${c.key.name}: chaotic rapier left")
            assertNull(c[1], "${c.key.name}: amulet of zealots left")
            assertNull(c[3], "${c.key.name}: noted Dungeoneering stack $notedDgn left")
            assertEquals(Items.DRAGON_PICKAXE, c[2]?.id, "${c.key.name}: the Dragon pickaxe must stay")
        }
        assertEquals(all.size * 3, taken)
        assertEquals(0, RemovedItems.purge(player), "a second login takes nothing")
    }

    @Test
    fun `Grand Exchange offers for a Dungeoneering item end, coins stay collectable`() {
        val file = Files.createTempFile("dgn-ge", ".json").toFile().apply { deleteOnExit() }
        val ge = GrandExchangeService(file)
        val sell = ge.submit("dgntest", OfferType.SELL, Items.CHAOTIC_RAPIER, 1_000, 1, slot = 0)!!.first
        val buy = ge.submit("dgntest", OfferType.BUY, Items.AMULET_OF_ZEALOTS, 500, 3, slot = 1)!!.first
        val keep = ge.submit("dgntest", OfferType.SELL, Items.DRAGON_PICKAXE, 2_000, 1, slot = 2)!!.first
        val gone: (Int) -> Boolean = { RemovedItems.isUnobtainable(def(it)) }
        assertEquals(2, ge.withdrawItems("dgntest", gone))
        assertNull(ge.offer(sell.id), "the sell offer owes nothing and is released")
        val refunded = ge.offer(buy.id)!!
        assertEquals(1_500L, refunded.collectableCoins, "unspent buy coins come back")
        assertEquals(0, refunded.collectableItems)
        assertEquals(Items.DRAGON_PICKAXE, ge.offer(keep.id)?.itemId, "a normal offer stays")
        assertEquals(1, ge.withdrawItems("dgntest", gone), "only the refund is left, nothing new")
    }

    @Test
    fun `the skill is gone - no XP, not in the total, trimmed cape, max cape, lamps, choosers or guides`() {
        assertTrue(SkillSet.isRemovedSkill(Skills.DUNGEONEERING))
        assertEquals(24, SkillSet.activeSkills().size, "24 skills, as OSRS")
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        SkillSet.activeSkills().forEach { skills.setBaseLevel(it, 1) }
        skills.setBaseLevel(Skills.DUNGEONEERING, 99)
        assertEquals(24, skills.calculateTotalLevel, "24 skills at level 1; the Dungeoneering 99 adds nothing")
        // A Dungeoneering 99 + one other 99 is not two 99s.
        val player = player(emptyMap(), skills)
        skills.setBaseLevel(Skills.ATTACK, 99)
        assertFalse(Skills.hasTwo99s(player))
        skills.setBaseLevel(Skills.STRENGTH, 99)
        assertTrue(Skills.hasTwo99s(player))

        val engine = File("../src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        val addXp = engine.substring(engine.indexOf("    fun addXp("))
        assertTrue(addXp.indexOf("SkillSet.isRemovedSkill(skill)) return") in 0 until addXp.indexOf("val oldXp"),
            "Player.addXp refuses a removed skill before any other route")
        val maxCape = read("areas/grandexchange/max_cape.plugin.kts")
        assertTrue("RemovedSkills.activeSkills(player)" in maxCape, "max cape asks the one rule")
        val lamp = read("items/lamps/lamp_interface.plugin.kts")
        assertFalse("skillId = Skills.DUNGEONEERING" in lamp, "the lamp has no Dungeoneering choice")
        assertTrue("SkillSet.REMOVED_SKILLS" in lamp)
        assertFalse("\"Dungeoneering\" to" in read("areas/grandexchange/grandexchange_unlocks.plugin.kts"), "GE unlock XP chooser")
        assertTrue("filterNot { SkillSet.isRemovedSkill(it.id) }" in read("inter/skillguides/skill_guides.plugin.kts"), "guides")
        val commands = read("cmd/commands.plugin.kts")
        assertEquals(2, Regex("""RemovedSkills\.isRemoved\(skill\)""").findAll(commands).count(), "::setxp and ::setlvl refuse it")
        assertEquals(2, Regex("""RemovedSkills\.activeSkills\(player\)""").findAll(commands).count(), "::master and ::maxtest skip it")
    }

    @Test
    fun `login migration resets old Dungeoneering XP and the old floor progress`() {
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        skills.setBaseXp(Skills.DUNGEONEERING, 13_034_431.0)
        skills.setBaseLevel(Skills.MINING, 80)
        val player = player(emptyMap(), skills)
        val floor = AttributeKey<Int>(persistenceKey = "dungeoneering_floor")
        player.attr[floor] = 37
        assertTrue(RemovedSkills.migrate(player))
        assertEquals(0.0, skills.getCurrentXp(Skills.DUNGEONEERING))
        assertEquals(1, skills.getCurrentLevel(Skills.DUNGEONEERING))
        assertEquals(80, skills.getMaxLevel(Skills.MINING), "other skills untouched")
        assertFalse(player.attr.has(floor))
        assertFalse(RemovedSkills.migrate(player), "a second login changes nothing")
        assertTrue("RemovedSkills.migrate(player)" in read("mechanics/removed/removed_items.plugin.kts"), "runs at login")
    }

    @Test
    fun `no route trains, teleports to, sells or spawns Dungeoneering any more - Daemonheim and its banker stay`() {
        val offenders = mutableListOf<String>()
        val banned =
            listOf(
                Regex("""addXp\(\s*Skills\.DUNGEONEERING"""), Regex("""on_command\("dungeon""""),
                Regex("""Npcs\.(DUNGEONEERING_TUTOR|THOK_MASTER_OF_DUNGEONEERING|REWARDS_TRADER)\b"""),
                Regex("""Items\.RING_OF_KINSHIP"""), Regex("""TeleportType\.(RING_OF_KINSHIP|DUNGEONEERING_GATESTONE)"""),
                Regex("""Skillcapes\.DUNGEONEERING"""), Regex("""Items\.DUNGEONEERING_(CAPE|HOOD)"""),
            )
        content.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                if (!line.trimStart().startsWith("//") && banned.any { it.containsMatchIn(line) }) offenders += "${f.name}:${i + 1}"
            }
        }
        assertTrue(offenders.isEmpty(), "Dungeoneering routes left: $offenders")
        assertFalse(File(content, "areas/daemonheim/Dungeoneering.kt").exists(), "the floor loop is gone")
        assertFalse(File(content, "items/jewellery/ring_of_kinship.plugin.kts").exists(), "the kinship teleport is gone")
        assertTrue(File(content, "areas/daemonheim/fremennik_banker.plugin.kts").exists(), "the Daemonheim banker stays")
        assertTrue("Npcs.FREMENNIK_BANKER" in read("areas/spawns/spawns_13625.plugin.kts"))
        val yml = File("../../data/cfg/commands.yml").readText()
        assertFalse(Regex("""(?m)^\s*-?\s*name:\s*"?dungeon"?\s*$""").containsMatchIn(yml) || Regex("""(?m)^\s*dungeon:""").containsMatchIn(yml),
            "commands.yml still lists ::dungeon")
    }

    private fun read(path: String) = File(content, path).readText()

    private fun player(
        extra: Map<ContainerKey, ItemContainer>,
        skills: SkillSet = SkillSet(SkillSet.DEFAULT_SKILL_COUNT),
    ): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        val inventory = ItemContainer(DEFINITIONS, INVENTORY_KEY)
        val equipment = ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        val bank = ItemContainer(DEFINITIONS, BANK_KEY)
        val gravestone = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        val death = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        every { player.inventory } returns inventory
        every { player.equipment } returns equipment
        every { player.bank } returns bank
        every { player.gravestone } returns gravestone
        every { player.deathRecovery } returns death
        every { player.containers } returns
            HashMap<ContainerKey, ItemContainer>().apply {
                put(INVENTORY_KEY, inventory)
                put(EQUIPMENT_KEY, equipment)
                put(BANK_KEY, bank)
                put(GRAVESTONE_KEY, gravestone)
                put(DEATH_RECOVERY_KEY, death)
                putAll(extra)
            }
        every { player.skills } returns skills
        val attr = AttributeMap()
        every { player.attr } returns attr
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
