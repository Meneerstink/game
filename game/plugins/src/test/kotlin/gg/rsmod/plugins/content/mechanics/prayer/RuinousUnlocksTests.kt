package gg.rsmod.plugins.content.mechanics.prayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.*
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.tools.importer.RuinousPowersCacheTool
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.mechanics.death.*
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChest
import gg.rsmod.plugins.content.quests.foundation.*
import gg.rsmod.plugins.content.quests.tab.*
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import java.io.File
import java.nio.file.Paths
import java.util.Random
import kotlin.test.*

/** N490 owner final plan V1-V9: real containers/varps and persisted attributes; no player saves or running server. */
class RuinousUnlocksTests {
    companion object {
        private val definitions by lazy {
            val cache = CacheLibrary(Paths.get("..", "..", "data", "cache").toString())
            DefinitionSet().apply {
                load(cache, ItemDef::class.java)
                load(cache, VarbitDef::class.java)
                load(cache, NpcDef::class.java)
            }
        }
    }

    private class Account {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val attributes = AttributeMap()
        val messages = mutableListOf<MessageGameMessage>()
        val containers = linkedMapOf<ContainerKey, ItemContainer>()
        init {
            listOf(INVENTORY_KEY, BANK_KEY, EQUIPMENT_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY).forEach {
                containers[it] = ItemContainer(definitions, it)
            }
            every { world.definitions } returns definitions
            every { player.world } returns world
            every { player.username } returns "ruinous_test"
            every { player.attr } returns attributes
            every { player.containers } returns containers
            every { player.inventory } returns containers.getValue(INVENTORY_KEY)
            every { player.bank } returns containers.getValue(BANK_KEY)
            every { player.equipment } returns containers.getValue(EQUIPMENT_KEY)
            every { player.varps } returns VarpSet((0..9000).toSet())
            every { player.varcs } returns MutableList(2000) { 0 }
            every { player.skills } returns SkillSet(25)
            every { player.timers } returns TimerMap()
            every { player.tile } returns Tile(3200, 3200)
            every { world.players } returns PawnList(arrayOf(player))
            every { player.write(*anyVararg<Message>()) } answers {
                messages += firstArg<Array<Message>>().filterIsInstance<MessageGameMessage>()
            }
        }
        fun quest() = DesertTreasureII.setStage(player, DesertTreasureII.completedValue)
        fun book() = player.inventory.add(Items.RUINOUS_POWERS, 1)
    }

    @After
    fun resetViews() {
        DropSources.reset()
        CollectionLogPages.reset()
    }

    @Test
    fun `reading without DT2 refuses even after Temple at Senntisten and keeps the book`() {
        val a = Account()
        a.book()
        TempleAtSenntisten.setStage(a.player, TempleAtSenntisten.completedValue)
        a.player.attr[AncientCurses.UNLOCKED_ATTR] = true
        assertEquals(RuinousUnlocks.ReadResult.QUEST_REQUIRED, RuinousUnlocks.read(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertTrue(RuinousUnlocks.refusal(a.player)!!.contains("Desert Treasure II"))
    }

    @Test
    fun `one book is consumed permanently and a second book or repeated click stays untouched`() {
        val a = Account()
        a.quest()
        assertEquals(RuinousUnlocks.ReadResult.MISSING_BOOK, RuinousUnlocks.read(a.player))
        assertFalse(a.attributes[RuinousUnlocks.UNLOCKED] == true)
        a.book()
        a.book()
        assertEquals(RuinousUnlocks.ReadResult.UNLOCKED, RuinousUnlocks.read(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertEquals(RuinousUnlocks.ReadResult.ALREADY_UNLOCKED, RuinousUnlocks.read(a.player))
        assertEquals(1, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertEquals(true, a.attributes.toPersistentMap()["ruinous_unlocked"])
        assertNull(RuinousUnlocks.refusal(a.player))
    }

    @Test
    fun `central switch refuses each missing prerequisite and accepts both without needing Curses`() {
        val a = Account()
        AncientCurses.switchBook(a.player, AncientCurses.PrayerBook.RUINOUS)
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(a.player))
        a.attributes[RuinousUnlocks.UNLOCKED] = true
        AncientCurses.switchBook(a.player, AncientCurses.PrayerBook.RUINOUS)
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(a.player))
        a.quest()
        a.attributes.remove(RuinousUnlocks.UNLOCKED)
        AncientCurses.switchBook(a.player, AncientCurses.PrayerBook.RUINOUS)
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(a.player))
        a.book()
        RuinousUnlocks.read(a.player)
        AncientCurses.switchBook(a.player, AncientCurses.PrayerBook.RUINOUS)
        assertEquals(AncientCurses.PrayerBook.RUINOUS, AncientCurses.getBook(a.player))
        assertEquals(1, a.player.getVarbit(RuinousPowersCacheTool.BOOK_FLAG_VARBIT))
    }

    @Test
    fun `Quest Guide choice gives access but no book unlock and a consumed unlock survives a fresh session`() {
        val a = Account()
        a.attributes[NEW_ACCOUNT_ATTR] = true
        assertEquals(QuestChoices.Result.CHOSEN, QuestChoices.choose(a.player, ChoiceGroup.MAGIC, DesertTreasureII))
        assertFalse(a.attributes[RuinousUnlocks.UNLOCKED] == true)
        assertEquals(0, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        a.book()
        assertEquals(RuinousUnlocks.ReadResult.UNLOCKED, RuinousUnlocks.read(a.player))
        AncientCurses.switchBook(a.player, AncientCurses.PrayerBook.RUINOUS)
        val reloaded = Account()
        a.attributes.toPersistentMap().forEach { (key, value) ->
            reloaded.attributes[AttributeKey<Any>(persistenceKey = key)] = value
        }
        a.player.varps.getAll().filter { it.state != 0 }.forEach { reloaded.player.varps.setState(it.id, it.state) }
        RuinousUnlocks.login(reloaded.player)
        assertEquals(AncientCurses.PrayerBook.RUINOUS, AncientCurses.getBook(reloaded.player))
        assertNull(RuinousUnlocks.refusal(reloaded.player))
        assertEquals(0, reloaded.player.inventory.getItemCount(Items.RUINOUS_POWERS))
    }

    @Test
    fun `legacy Ruinous users reset on login even in combat while unlocked relogs retain the book`() {
        val a = Account()
        QuestChoices.applyLegacy(a.player)
        assertTrue(DesertTreasureII.isFinished(a.player))
        assertFalse(a.attributes[RuinousUnlocks.UNLOCKED] == true, "legacy quests do not grant the drop unlock")
        a.attributes[AttributeKey<String>(persistenceKey = "prayer_book")] = "RUINOUS"
        a.player.setVarp(RuinousPowersCacheTool.ACTIVE_VARP, 1 shl RuinousPrayer.DECIMATE.slot)
        a.player.timers[ACTIVE_COMBAT_TIMER] = 10
        RuinousUnlocks.login(a.player)
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(a.player))
        assertFalse(RuinousPowers.anyActive(a.player))
        assertTrue(a.messages.any { "reset to normal" in it.message && "Desert Treasure II" in it.message })
        a.attributes[RuinousUnlocks.UNLOCKED] = true
        a.attributes[AttributeKey<String>(persistenceKey = "prayer_book")] = "RUINOUS"
        RuinousUnlocks.login(a.player)
        assertEquals(AncientCurses.PrayerBook.RUINOUS, AncientCurses.getBook(a.player))
        // An inconsistent save with only the book flag is still rejected.
        DesertTreasureII.setStage(a.player, 0)
        RuinousUnlocks.login(a.player)
        assertEquals(AncientCurses.PrayerBook.NORMAL, AncientCurses.getBook(a.player))
    }

    @Test
    fun `all five rates are exact unboosted independent rolls and blocked drops do not draw randomness`() {
        val a = Account()
        val sources = RuinousBookDrops.Source.values()
        assertEquals(listOf(25, 300, 400, 150, 1000), sources.map { it.denominator })
        for (source in sources) {
            var draws = 0
            val random = object : Random() {
                override fun nextInt(bound: Int): Int {
                    assertEquals(source.denominator, bound)
                    return draws++
                }
            }
            assertEquals(1, (0 until source.denominator).count { RuinousBookDrops.roll(a.player, source, random) })
        }
        val noDraw = object : Random() { override fun nextInt(bound: Int): Int = error("Blocked drop must not roll") }
        for (key in listOf(INVENTORY_KEY, BANK_KEY, GRAVESTONE_KEY, DEATH_RECOVERY_KEY)) {
            a.containers.getValue(key).add(Items.RUINOUS_POWERS, 1)
            assertFalse(RuinousBookDrops.roll(a.player, sources.first(), noDraw), key.name)
            a.containers.getValue(key).remove(Items.RUINOUS_POWERS, 1)
        }
        a.attributes[RuinousUnlocks.UNLOCKED] = true
        assertFalse(RuinousBookDrops.roll(a.player, sources.first(), noDraw))
    }

    @Test
    fun `sources distinguish Nex Revenants bosses breach monsters summons and ordinary NPCs`() {
        val a = Account()
        fun source(id: Int) = RuinousBookDrops.sourceOf(a.world, id)
        assertEquals(RuinousBookDrops.Source.NEX, source(Npcs.NEX))
        val revs = (0 until definitions.getCount(NpcDef::class.java)).filter {
            definitions.getNullable(NpcDef::class.java, it)?.name?.lowercase()?.startsWith("revenant ") == true
        }
        assertTrue(revs.isNotEmpty())
        revs.forEach { assertEquals(RuinousBookDrops.Source.REVENANT, source(it)) }
        assertEquals(RuinousBookDrops.Source.BOSS, source(Npcs.GENERAL_GRAARDOR))
        assertNull(source(Npcs.CHICKEN))
        BreachMonsters.ROSTER.forEach {
            if (it.summon) assertNull(source(it.id)) else assertTrue(source(it.id) in listOf(RuinousBookDrops.Source.BOSS, RuinousBookDrops.Source.BREACH_MONSTER))
        }
    }

    @Test
    fun `viewer ownership stays personal and chest reward enters the Collection Log`() {
        val a = Account()
        val chest = DropSources.Source("chest", "Deadman's Chest", -1, intArrayOf(), 0, DropSources.Group.DEADMAN, DropSources.Kind.CHEST)
        fun line(account: Account) = DropSources.view(account.player, chest, 5.0).lines.single { it.item == Items.RUINOUS_POWERS }
        assertEquals(1.0 / 25, line(a).your)
        a.player.bank.add(Items.RUINOUS_POWERS, 1)
        assertEquals(0.0, line(a).your)
        assertEquals(1.0 / 25, line(a).normal)
        assertEquals(1.0 / 25, line(Account()).your, "shared cache must not retain another player's ownership")
        a.player.bank.remove(Items.RUINOUS_POWERS, 1)
        assertTrue(CollectionLogPages.all(a.player).single { it.key == "chest" }.items.contains(Items.RUINOUS_POWERS))
        KillCounts.chestOpened(a.player)
        RuinousBookDrops.award(a.player, a.player.tile, "chest", "a Deadman's Chest", inventory = true)
        assertEquals(1, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertTrue(CollectionLog.has(a.player, Items.RUINOUS_POWERS))
        assertEquals(1, CollectionLog.entries(a.player).getValue(Items.RUINOUS_POWERS).kc)
        assertTrue(a.messages.any { it.message.startsWith("<col=a53fff>") && "has received Ruinous powers" in it.message })
    }

    @Test
    fun `real chest reward adds the independent book and possession skips a second book roll`() {
        val a = Account()
        val zero = object : Random() { override fun nextInt(bound: Int): Int = 0 }
        val miss = object : Random() { override fun nextInt(bound: Int): Int = bound - 1 }
        DeadmanChest.giveReward(a.player, random = zero, scrollRandom = miss, ruinousRandom = zero)
        assertEquals(1, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertTrue(CollectionLog.has(a.player, Items.RUINOUS_POWERS))
        a.player.inventory.remove(Items.RUINOUS_POWERS, 1)
        a.player.bank.add(Items.RUINOUS_POWERS, 1)
        val noDraw = object : Random() { override fun nextInt(bound: Int): Int = error("Banked book must skip the roll") }
        DeadmanChest.giveReward(a.player, random = zero, scrollRandom = miss, ruinousRandom = noDraw)
        assertEquals(0, a.player.inventory.getItemCount(Items.RUINOUS_POWERS))
        assertEquals(1, a.player.bank.getItemCount(Items.RUINOUS_POWERS))
    }

    @Test
    fun `a drop without a viewer entry still counts in the shared Collection Log`() {
        val a = Account()
        RuinousBookDrops.award(a.player, a.player.tile, null, "a boss", inventory = true)
        assertEquals(1, CollectionLog.entries(a.player).getValue(Items.RUINOUS_POWERS).count)
        assertEquals(0, CollectionLog.entries(a.player).getValue(Items.RUINOUS_POWERS).kc)
    }

    @Test
    fun `book follows the existing untradeable death rules without a special exception`() {
        // items.yml's untradeable override is applied by the server; the cache only supplies models/menu.
        definitions.get(ItemDef::class.java, Items.RUINOUS_POWERS).tradeable = false
        assertFalse(UntradeableDeathProtection.lockable(Items.RUINOUS_POWERS))
        listOf(false, true).forEach { deep ->
            assertEquals(UntradeableFate.LOST_FOR_COINS, UntradeableDeathProtection.fateOf(definitions, Item(Items.RUINOUS_POWERS), deep).fate)
        }
        assertFalse(DeathRules.toGrave(Items.RUINOUS_POWERS), "no special grave-only rule")
        assertEquals(3, DeathRules.keepCount(DeathContext.PVM_SAFE, skulled = false))
        assertEquals(0, DeathRules.keepCount(DeathContext.PVM_SAFE, skulled = true))
    }

    @Test
    fun `altar NPC and command routes use the guarded switch and read sends the agreed confirmation`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        listOf("areas/poh/player_house.plugin.kts", "objs/prayeraltar/prayer_altar.plugin.kts",
            "areas/grandexchange/grandexchange_unlocks.plugin.kts", "cmd/commands.plugin.kts", "mechanics/prayer/ancient_curses.plugin.kts").forEach {
            val source = File(root, it).readText()
            assertTrue(Regex("AncientCurses\\.switchBook\\(player, (?:[\\w.]+\\.RUINOUS|book)\\)").containsMatchIn(source), it)
        }
        val read = File(root, "mechanics/prayer/ruinous_unlocks.plugin.kts").readText()
        assertTrue("Sfx.TRAILBLAZER_UNLOCK_POWER" in read)
        assertTrue("<col=ef1020>You have unlocked the Ruinous Powers.</col>" in read)
        assertTrue("messageBox(\"You have unlocked the Ruinous Powers." in read)
        assertEquals(Npcs.AZZANADRA, DesertTreasureII.steps.last().npc)
        assertTrue(DesertTreasureII.reward.lines.any { "Ruinous" in it && "book" in it })
        assertFalse(DesertTreasureII.reward.items.any { it.first == Items.RUINOUS_POWERS })
    }
}
