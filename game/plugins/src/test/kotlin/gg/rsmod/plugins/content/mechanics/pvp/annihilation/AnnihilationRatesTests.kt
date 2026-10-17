package gg.rsmod.plugins.content.mechanics.pvp.annihilation

import com.displee.cache.CacheLibrary
import gg.rsmod.game.DevContext
import gg.rsmod.game.GameContext
import gg.rsmod.game.Server
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.service.Service
import gg.rsmod.game.service.xtea.XteaKeyService
import gg.rsmod.game.system.GameSystem
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.calculateAndSetCombatLevel
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.DropTableType
import gg.rsmod.plugins.content.drops.GUARANTEED_TABLE_NAME
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.content.newplayer.SummoningKit
import gg.rsmod.plugins.content.npcs.bulk.BulkNpcDropTables
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.tab.DropViewer
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData
import gg.rsmod.util.ServerProperties
import io.mockk.every
import io.mockk.mockk
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Paths
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * N92 + N100 (one cause, owner 2026-10-17): Deadman "Annihilation" rates per combat bracket, ONE tier table for XP and drops.
 *
 * N92 (bug-20261017-092934-anudd, 3168,3487 GE mansion, combat 138, all skills 99): "our rsps needs the same exp as dmm
 * anihilation". Owner VRAGEN 22 = A + "VERVANGEN": combat XP 10x (3-60) / 15x (61-80) / 15x (81-95) / 20x (96+), skilling
 * 10x, Prayer = combat (OSRS Wiki Deadman: Annihilation), Summoning = combat (VRAGEN 33 team default A), lamps and quest XP
 * get the rate too, the 2011 curve (x1 -> x5) and the time-played bonus are gone, a RED chat line once per rate change.
 * N100 (owner 10-17): drop rate 3x / 3x / 4x / 5x per bracket on the CHANCE of every non-guaranteed drop line, max 100 %,
 * quantities unchanged, guaranteed drops 1x; monster/boss kills only (VRAGEN 34 team default b = A, mechanism a = B
 * "every line its own chance"); the Drop Viewer shows the real chance.
 *
 * Old cause: Player.addXp = xp * interpolate(1.0, 5.0, level) * bonus (the 2011 curve: ~x5.04 at 99 for every skill, x1 at
 * level 1); quest XP / quest lamps / the Wolf Whistle reward used modifiers = false (exact); the drop roll had no factor.
 *
 * The test runs on the real engine: real cache definitions, a real World, real Client players and the compiled level-up
 * script (the red line is sent from the real level-up route), the real 1,707 bulk npc drop tables.
 */
class AnnihilationRatesTests {
    private val players = mutableListOf<Client>()
    private val written = HashMap<Client, MutableList<Message>>()

    @After
    fun cleanUp() {
        players.forEach { world.unregister(it) }
        players.clear()
    }

    // ------------------------------------------------------------------------------------------------------------- N92 XP

    @Test
    fun `N92 owner case - combat 138 with every skill 99 gains combat XP at 20x and skilling XP at 10x`() {
        val owner = player("n92owner", OWNER_TILE)
        for (skill in 0 until SKILL_COUNT) owner.skills.setBaseXp(skill, OWNER_XP)
        owner.calculateAndSetCombatLevel()
        assertEquals(138, owner.combatLevel)

        val attack = gain(owner, Skills.ATTACK, 4.0) // one 1-damage melee hit: 4 raw Attack XP
        val hitpoints = gain(owner, Skills.CONSTITUTION, 1.33)
        val woodcutting = gain(owner, Skills.WOODCUTTING, 25.0) // one normal log
        assertEquals("Attack: 4 raw x 20 (Annihilation 96+ combat), not the 2011 curve x5.04", 80.0, attack, 1e-6)
        assertEquals("Hitpoints count as combat (wiki: ALL combat skills including Hitpoints)", 1.33 * 20, hitpoints, 1e-6)
        assertEquals("Woodcutting: 25 raw x 10 (skilling is 10x in every bracket)", 250.0, woodcutting, 1e-6)
    }

    @Test
    fun `N92 class - every skill gains exactly its bracket rate in every bracket and xpMultiplier tells the real gain`() {
        val failures = mutableListOf<String>()
        for (target in listOf(3, 60, 61, 80, 81, 95, 96, 126, 138)) {
            val p = player("n92c$target", OWNER_TILE)
            setCombat(p, target)
            val tier = WIKI.single { target in it.levels }
            for (skill in 0 until SKILL_COUNT) {
                val combatSkill = skill in COMBAT_SKILLS
                val expected = if (combatSkill) tier.combatXp else tier.skillingXp
                val shown = p.xpMultiplier(skill)
                val got = gain(p, skill, 10.0)
                if (abs(got - 10.0 * expected) > 1e-6) failures += "combat $target skill $skill: gained $got for 10 raw, expected ${10.0 * expected}"
                if (abs(shown - expected) > 1e-9) failures += "combat $target skill $skill: xpMultiplier $shown, expected $expected"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `N92 class - no experience route outside the AFK area and test bots bypasses the rate`() {
        // Owner 10-17: lamps and quest XP get the bracket rate too (OSRS: "The experience multipliers are also applied to
        // experience rewards obtained through lamps"). The AFK area keeps its own fixed numbers (new_player.yml, N110 open).
        val allowed = setOf("AfkArea.kt", "SummoningSuite.kt")
        val bypasses =
            File("src/main/kotlin").walkTopDown()
                .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name !in allowed }
                .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if ("modifiers = false" in line && !line.trimStart().startsWith("*") && !line.trimStart().startsWith("//")) "${f.name}:${i + 1}: ${line.trim()}" else null } }
                .toList()
        assertTrue("experience calls that skip the Annihilation rate:\n${bypasses.joinToString("\n")}", bypasses.isEmpty())
    }

    @Test
    fun `N92 quest XP, the Wolf Whistle reward and the Summoning kit follow the rate`() {
        val p = player("n92quest", OWNER_TILE)
        setCombat(p, 3)
        val quest = FoundationQuests.SHORT.first { it.reward.xp.isNotEmpty() }
        val before = quest.reward.xp.associate { (skill, _) -> skill to p.skills.getCurrentXp(skill) }
        FoundationRewards.complete(p, quest)
        quest.reward.xp.forEach { (skill, amount) ->
            assertEquals("${quest::class.simpleName} quest XP in skill $skill at combat 3 = ${amount}x10", amount * 10, p.skills.getCurrentXp(skill) - before.getValue(skill), 1e-6)
        }

        val s = player("n92kit", OWNER_TILE)
        setCombat(s, 3)
        val startXp = s.skills.getCurrentXp(Skills.SUMMONING)
        repeat(27) { s.inventory.add(gg.rsmod.plugins.api.cfg.Items.BRONZE_DAGGER) }
        assertTrue(SummoningKit.claim(s, NewPlayerConfig.DEFAULTS))
        val rate = WIKI.first().combatXp // VRAGEN 33 team default: Summoning = combat rate
        assertEquals("Wolf Whistle 276 XP x rate", startXp + 276.0 * rate, s.skills.getCurrentXp(Skills.SUMMONING), 1e-6)
        assertTrue("the whistle line prints the real gain: ${messages(s)}", messages(s).any { it.startsWith("Wolf Whistle:") && "2,760 Summoning XP" in it })
        // The kit line: "You receive Summoning supplies to train to level 55: 3 spirit wolf, 2 granite crab pouches."
        val line = messages(s).single { it.startsWith("You receive Summoning supplies") }
        val counts =
            line.substringAfter(": ").removeSuffix(" pouches.").split(", ").map { part ->
                val n = part.substringBefore(' ').toInt()
                val name = part.substringAfter(' ').uppercase().replace(' ', '_')
                SummoningPouchData.valueOf(name) to n
            }
        fun reached(list: List<Pair<SummoningPouchData, Int>>): Int {
            var xp = s.skills.getCurrentXp(Skills.SUMMONING)
            list.forEach { (pouch, n) -> repeat(n) { xp += pouch.creationExperience * rate } }
            return SkillSet.getLevelForXp(xp)
        }
        val target = NewPlayerConfig.DEFAULTS.summoningTargetLevel
        assertTrue("the kit reaches level $target at the real rate: $line", reached(counts) >= target)
        val oneLess = counts.toMutableList().also { val (pouch, n) = it.removeAt(it.lastIndex); if (n > 1) it += pouch to n - 1 }
        assertTrue("the kit is not oversized for the old 2011 curve (one pouch less must fall short): $line", reached(oneLess) < target)
    }

    @Test
    fun `N92 red line - exactly once per rate change at 61, 81 and 96, never at relog, never for an existing account`() {
        for (border in listOf(61, 81, 96)) {
            val p = player("n92b$border", OWNER_TILE)
            val (others, attack) = crossing(border)
            setLevels(p, others, attack)
            assertEquals(border - 1, p.combatLevel)
            login(p) // the account exists already: the bracket it is in is recorded silently
            assertEquals("login of an existing account: no rate line ${messages(p)}", 0, rateLines(p).size)
            val below = WIKI.single { border - 1 in it.levels }
            val above = WIKI.single { border in it.levels }
            levelUp(p, Skills.ATTACK, below)
            assertEquals("combat after the attack level-up", border, p.combatLevel)
            val lines = rateLines(p)
            assertEquals("one red rate line when combat reaches $border: ${messages(p)}", 1, lines.size)
            val text = lines.single()
            assertTrue("red: $text", text.startsWith("<col=ef1020>"))
            assertTrue("names the new XP and drop rates: $text", "${fmt(above.combatXp)}x combat" in text && "${fmt(above.skillingXp)}x skilling" in text && "drop rate ${fmt(above.dropRate)}x" in text)
            // The next gain already runs at the new rate.
            assertEquals(10.0 * above.combatXp, gain(p, Skills.ATTACK, 10.0), 1e-6)
            // Relog: no second line.
            login(p)
            assertEquals("relog: still one line ${messages(p)}", 1, rateLines(p).size)
            // Another level-up inside the same bracket: no line.
            levelUp(p, Skills.STRENGTH, above)
            assertEquals("level-up inside the bracket: still one line ${messages(p)}", 1, rateLines(p).size)
            // Never down (Annihilation "it is not possible to move back down a combat bracket"), even if a level is lowered.
            p.skills.setBaseLevel(Skills.ATTACK, 1)
            p.calculateAndSetCombatLevel()
            assertTrue("precondition: combat lowered to ${p.combatLevel}", p.combatLevel < border)
            assertEquals("the rate never drops", 10.0 * above.combatXp, gain(p, Skills.STRENGTH, 10.0), 1e-6)
        }
        val veteran = player("n92vet", OWNER_TILE)
        for (skill in 0 until SKILL_COUNT) veteran.skills.setBaseXp(skill, OWNER_XP)
        login(veteran)
        assertEquals("the owner's 138 account logs in after the deploy: no line ${messages(veteran)}", 0, rateLines(veteran).size)
        val fresh = player("n92new", OWNER_TILE)
        login(fresh)
        assertEquals("a new account starts in 3-60: no line ${messages(fresh)}", 0, rateLines(fresh).size)
    }

    @Test
    fun `N92 bonus XP is gone - with bonus-experience on, a login shows no bonus orb and no gain gets the time-played bonus`() {
        // The test world runs with game.yml bonus-experience ON (gameContext mock below) and the real bonus_experience script.
        // Owner 10-17 "VERVANGEN": the time-played bonus (2.7 .. 1.1) is gone for normal XP, so the orb must not promise it.
        val p = player("n92bonus", OWNER_TILE)
        setCombat(p, 3)
        p.setVarbit(BONUS_TIME_VARBIT, 1) // the first 30 minutes played: the old bonus was x2.7
        login(p)
        assertEquals("bonus XP orb (varbit $BONUS_ENABLED_VARBIT) after login", 0, p.getVarbit(BONUS_ENABLED_VARBIT))
        val bonusBefore = p.getVarp(BONUS_GAINED_VARP)
        assertEquals("one Attack gain at combat 3 is raw x10, no time-played bonus", 100.0, gain(p, Skills.ATTACK, 10.0), 1e-6)
        assertEquals("woodcutting at combat 3 is raw x10, no time-played bonus", 250.0, gain(p, Skills.WOODCUTTING, 25.0), 1e-6)
        assertEquals("the bonus-XP-gained counter (varp $BONUS_GAINED_VARP) does not grow", bonusBefore, p.getVarp(BONUS_GAINED_VARP))
    }

    // ------------------------------------------------------------------------------------------------------------- N100 drops

    @Test
    fun `N100 kill drops - every non-guaranteed line gets its bracket chance, guaranteed and quantities stay, nested once`() {
        DropTableFactory.register(TEST_TABLE, TEST_KILL_ID)
        val random = DropTableFactory.prng
        try {
            DropTableFactory.prng = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(9200L) }
            for ((combat, k) in listOf(138 to 5.0, 3 to 3.0, 85 to 4.0, 70 to 3.0)) {
                val killer = player("n100k$combat", DROP_TILE)
                setCombat(killer, combat)
                val counts = HashMap<Int, Int>()
                val amounts = HashMap<Int, MutableSet<Int>>()
                repeat(KILLS) {
                    DropTableFactory.getDrop(world, killer, TEST_KILL_ID, DROP_TILE)
                    while (true) {
                        val item = world.getGroundItem { it.tile == DROP_TILE } ?: break
                        counts.merge(item.item, 1, Int::plus)
                        amounts.getOrPut(item.item) { HashSet() } += item.amount
                        world.remove(item)
                    }
                }
                assertEquals("bones are guaranteed: exactly 1 per kill at ${k}x", KILLS, counts[BONES] ?: 0)
                assertTrue("bones quantity 1", amounts.getValue(BONES) == setOf(1))
                assertEquals("coins 32/128 x $k capped at 100%", min(1.0, k * 32 / 128) * KILLS, (counts[COINS] ?: 0).toDouble(), tolerance(min(1.0, k * 32 / 128)))
                assertTrue("coin stacks keep their 100-200 range: ${amounts[COINS]}", amounts.getValue(COINS).all { it in 100..200 })
                assertEquals("med helm 1/128 x $k", k / 128 * KILLS, (counts[MED_HELM] ?: 0).toDouble(), tolerance(k / 128))
                assertEquals("longsword through a nested table: (4/128 x $k) x 1/2, boosted once (never k*k)", k * 4 / 128 * 0.5 * KILLS, (counts[LONGSWORD] ?: 0).toDouble(), tolerance(k * 4 / 128 * 0.5))
                assertEquals("tertiary whip 1/1000 x $k", k / 1000 * KILLS, (counts[WHIP] ?: 0).toDouble(), tolerance(k / 1000))
            }
        } finally {
            DropTableFactory.prng = random
        }
    }

    @Test
    fun `N100 rolls that are not a monster kill stay 1x - boxes, chests, pickpockets, stalls and the bird-nest route`() {
        for (type in listOf(DropTableType.BOX, DropTableType.CHEST, DropTableType.PICKPOCKET, DropTableType.STALL)) DropTableFactory.register(TEST_TABLE, TEST_OTHER_ID, type = type)
        DropTableFactory.register(TEST_TABLE, TEST_NEST_ID)
        val random = DropTableFactory.prng
        try {
            DropTableFactory.prng = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(9201L) }
            val killer = player("n100o", DROP_TILE)
            setCombat(killer, 138)
            for ((type, id) in listOf(DropTableType.BOX to TEST_OTHER_ID, DropTableType.CHEST to TEST_OTHER_ID, DropTableType.PICKPOCKET to TEST_OTHER_ID, DropTableType.STALL to TEST_OTHER_ID, DropTableType.KILL to TEST_NEST_ID)) {
                var helms = 0
                repeat(KILLS) {
                    val items = DropTableFactory.getDrop(killer, id, type)!!
                    helms += items.count { it.id == MED_HELM }
                    assertTrue("one pick from the main table, as before: $items", items.count { it.id in MAIN_ITEMS } <= 1)
                }
                assertEquals("$type roll at combat 138 stays 1/128", KILLS / 128.0, helms.toDouble(), tolerance(1.0 / 128))
            }
        } finally {
            DropTableFactory.prng = random
        }
    }

    @Test
    fun `N100 Drop Viewer shows the killer's real chance for every bulk npc table at every bracket`() {
        val bulk = BulkNpcDropTables.load(world.definitions, File(Paths.get("..", "..", "data", "cfg", "npcs", "drop-tables.json").toString())).tables
        assertTrue("the real bulk tables (${bulk.size})", bulk.size > 1_000)
        bulk.forEach { (npc, table) -> DropTableFactory.register(table, VIEWER_OFFSET + npc) }
        val failures = mutableListOf<String>()
        for ((combat, k) in listOf(3 to 3.0, 70 to 3.0, 90 to 4.0, 138 to 5.0)) {
            val viewer = player("n100v$combat", DROP_TILE)
            setCombat(viewer, combat)
            for (npc in bulk.keys) {
                val id = VIEWER_OFFSET + npc
                val built = DropTableFactory.tables(viewer, id) ?: continue
                val expected = expectedRows(built, k)
                val shown = DropViewer.rows(viewer, id).associate { "${it.item}|${it.quantity}" to it.chance }
                if (shown.keys != expected.keys) {
                    failures += "npc $npc at ${k}x: lines ${shown.keys} != ${expected.keys}"
                    continue
                }
                expected.forEach { (key, chance) ->
                    if (abs(shown.getValue(key) - chance) > 1e-9) failures += "npc $npc at ${k}x: $key shows ${shown.getValue(key)}, expected $chance"
                }
            }
        }
        assertTrue("${failures.size} wrong viewer lines, first: ${failures.take(15).joinToString("\n")}", failures.isEmpty())
    }

    // ------------------------------------------------------------------------------------------------------------- helpers

    /** The chance per (item|quantity) a kill gives at [k]: guaranteed 1, each top line min(1, k x base), nested lines once. */
    private fun expectedRows(
        tables: List<DropTable>,
        k: Double,
    ): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        fun add(
            key: String,
            chance: Double,
        ) {
            out[key] = min(1.0, (out[key] ?: 0.0) + chance)
        }
        fun walk(
            table: DropTable,
            weight: Double,
            top: Boolean,
            guaranteed: Boolean,
        ) {
            val entries = table.entries
            if (entries.isEmpty()) return
            val denominator = maxOf(table.totalSlots, entries.last().index).toDouble()
            var previous = 0
            for (entry in entries) {
                val base = (entry.index - previous) / denominator
                previous = entry.index
                val chance = if (guaranteed) weight else if (top) min(1.0, k * base) else weight * base
                when (val drop = entry.drop) {
                    is DropEntry.ItemDrop -> add("${drop.item.id}|${drop.item.amount}", chance)
                    is DropEntry.ItemRangeDrop -> add("${drop.item.id}|${drop.quantityRange.first}-${drop.quantityRange.last}", chance)
                    is DropEntry.MultiDrop -> drop.items.forEach { add("${it.id}|${it.amount}", chance) }
                    is DropEntry.TableDrop -> walk(drop.table, chance, top = false, guaranteed = false)
                    DropEntry.NothingDrop -> Unit
                }
            }
        }
        tables.forEach { walk(it, 1.0, top = true, guaranteed = it.name.equals(GUARANTEED_TABLE_NAME, ignoreCase = true)) }
        return out
    }

    private fun tolerance(p: Double): Double = 5 * sqrt(KILLS * p * (1 - p)) + 2

    private fun gain(
        p: Client,
        skill: Int,
        raw: Double,
    ): Double {
        val before = p.skills.getCurrentXp(skill)
        p.addXp(skill, raw)
        return p.skills.getCurrentXp(skill) - before
    }

    /** Raises [skill] exactly one level with the raw XP the bracket's rate needs (the real level-up route runs). */
    private fun levelUp(
        p: Client,
        skill: Int,
        tier: Tier,
    ) {
        val level = p.skills.getMaxLevel(skill)
        val need = SkillSet.getXpForLevel(level + 1) - p.skills.getCurrentXp(skill)
        val rate = if (skill in COMBAT_SKILLS) tier.combatXp else tier.skillingXp
        p.addXp(skill, need / rate + 0.01)
        assertEquals("one level in skill $skill", level + 1, p.skills.getMaxLevel(skill))
    }

    private fun setLevels(
        p: Client,
        others: Int,
        attack: Int,
    ) {
        for (skill in listOf(Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION)) p.skills.setBaseLevel(skill, others)
        for (skill in listOf(Skills.PRAYER, Skills.SUMMONING, Skills.RANGED, Skills.MAGIC)) p.skills.setBaseLevel(skill, 1)
        p.skills.setBaseLevel(Skills.ATTACK, attack)
        p.calculateAndSetCombatLevel()
    }

    /** Strength/Defence/Hitpoints [first] and Attack [second] give combat [border] - 1; one more Attack level gives [border]. */
    private fun crossing(border: Int): Pair<Int, Int> {
        val probe = Client(EmbeddedChannel(), world)
        for (others in 1..99) for (attack in 1..98) {
            setLevels(probe, others, attack)
            if (probe.combatLevel != border - 1) continue
            setLevels(probe, others, attack + 1)
            if (probe.combatLevel == border) return others to attack
        }
        fail("no level set crosses combat $border with one Attack level")
        error("unreachable")
    }

    private fun setCombat(
        p: Client,
        target: Int,
    ) {
        if (target == 3) {
            for (skill in 0 until SKILL_COUNT) p.skills.setBaseLevel(skill, if (skill == Skills.CONSTITUTION) 10 else 1)
            p.calculateAndSetCombatLevel()
        } else if (target == 138) {
            for (skill in 0 until SKILL_COUNT) p.skills.setBaseLevel(skill, 99)
            p.calculateAndSetCombatLevel()
        } else {
            // Strength/Defence/Hitpoints/Prayer/Summoning one level, Attack another: every combat level 3..138 is reachable.
            fun set(o: Int, a: Int) {
                for (skill in listOf(Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.PRAYER, Skills.SUMMONING)) p.skills.setBaseLevel(skill, o)
                for (skill in listOf(Skills.RANGED, Skills.MAGIC)) p.skills.setBaseLevel(skill, 1)
                p.skills.setBaseLevel(Skills.ATTACK, a)
                p.calculateAndSetCombatLevel()
            }
            val (others, attack) = (1..99).asSequence().flatMap { o -> (1..99).asSequence().map { o to it } }.first { (o, a) -> set(o, a); p.combatLevel == target }
            set(others, attack)
        }
        assertEquals("combat level", target, p.combatLevel)
    }

    private fun login(p: Client) {
        world.plugins.executeLogin(p)
    }

    private fun rateLines(p: Client) = messages(p).filter { "XP rate is now" in it }

    private fun messages(p: Client) = written.getValue(p).filterIsInstance<MessageGameMessage>().map { it.message }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

    private fun player(
        name: String,
        at: Tile,
    ): Client {
        world.collision.isClipped(at) // loads the region
        val client = Client(EmbeddedChannel(), world)
        client.loginUsername = name
        client.username = name
        client.uid = gg.rsmod.game.model.PlayerUID(name) // ground-item owner of the kill drops
        client.tile = at
        val out = mutableListOf<Message>()
        val system = mockk<GameSystem>(relaxed = true)
        every { system.write(any()) } answers { out += firstArg<Message>() }
        client.gameSystem = system
        written[client] = out
        assertTrue("registered $name", world.register(client))
        players += client
        return client
    }

    data class Tier(val levels: IntRange, val combatXp: Double, val skillingXp: Double, val dropRate: Double)

    companion object {
        /** OSRS Wiki "Deadman: Annihilation" (fetched 2026-10-17): bracket | combat XP | skilling XP | drop rate. */
        val WIKI =
            listOf(
                Tier(3..60, 10.0, 10.0, 3.0),
                Tier(61..80, 15.0, 10.0, 3.0),
                Tier(81..95, 15.0, 10.0, 4.0),
                Tier(96..138, 20.0, 10.0, 5.0),
            )

        /** Prayer counts as combat (wiki); Summoning too (VRAGEN 33 team default A); Slayer is skilling. */
        val COMBAT_SKILLS = setOf(Skills.ATTACK, Skills.DEFENCE, Skills.STRENGTH, Skills.CONSTITUTION, Skills.RANGED, Skills.PRAYER, Skills.MAGIC, Skills.SUMMONING)

        const val SKILL_COUNT = 25

        /** bonus_experience.plugin.kts: orb on/off, minutes played, bonus XP gained. */
        const val BONUS_ENABLED_VARBIT = 7232
        const val BONUS_TIME_VARBIT = 7233
        const val BONUS_GAINED_VARP = 1878

        /** The owner's report: every skill ~13.03M XP (Constitution "106:1.3034431E7"). */
        const val OWNER_XP = 13_034_431.0

        val OWNER_TILE = Tile(3168, 3487, 0)
        val DROP_TILE = Tile(3170, 3480, 0)

        const val KILLS = 20_000
        const val TEST_KILL_ID = 9_920_001
        const val TEST_OTHER_ID = 9_920_002
        const val TEST_NEST_ID = 9_920_003
        const val VIEWER_OFFSET = 9_000_000

        const val BONES = 536
        const val MED_HELM = 1149
        const val COINS = 995
        const val LONGSWORD = 1305
        const val WHIP = 4151
        val MAIN_ITEMS = setOf(MED_HELM, COINS, LONGSWORD)

        val TEST_TABLE =
            DropTableFactory.build {
                guaranteed { obj(BONES) }
                main {
                    total(128)
                    obj(MED_HELM, slots = 1)
                    obj(COINS, quantityRange = 100..200, slots = 32)
                    table(DropTableFactory.build { main { total(2); obj(LONGSWORD, slots = 1); nothing(1) } }, slots = 4)
                }
                table("tertiary") {
                    total(1000)
                    obj(WHIP, slots = 1)
                }
            }

        private val SCRIPTS =
            listOf(
                "gg.rsmod.plugins.content.skills.Level_up_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/skills/level_up.plugin.kts",
                "gg.rsmod.plugins.content.mechanics.pvp.annihilation.Annihilation_tier_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/pvp/annihilation/annihilation_tier.plugin.kts",
                "gg.rsmod.plugins.content.mechanics.bonusexperience.Bonus_experience_plugin" to "src/main/kotlin/gg/rsmod/plugins/content/mechanics/bonusexperience/bonus_experience.plugin.kts",
            )

        val world: World by lazy {
            val ctx = mockk<GameContext>(relaxed = true)
            every { ctx.playerLimit } returns 64
            every { ctx.skillCount } returns 25
            every { ctx.npcStatCount } returns Npc.Stats.DEFAULT_NPC_STAT_COUNT
            every { ctx.home } returns Tile(3164, 3495, 0)
            every { ctx.bonusExperience } returns true // game.yml bonus-experience ON: the old time-played bonus would apply
            val world = World(ctx, mockk<DevContext>(relaxed = true))
            world.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            world.filestore = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            world.definitions.loadAll(world.filestore)
            val xteas = XteaKeyService()
            xteas.init(mockk(relaxed = true), world, ServerProperties().loadMap(mapOf("path" to Paths.get("..", "..", "data", "xteas").toString())))
            @Suppress("UNCHECKED_CAST")
            val services = World::class.java.getDeclaredField("services").apply { isAccessible = true }.get(world) as MutableList<Service>
            services += xteas
            World::class.java.declaredMethods.first { it.name.startsWith("loadUpdateBlocks") }.apply { isAccessible = true }
                .invoke(world, Paths.get("..", "..", "data", "blocks.yml").toFile())
            bind(world)
            world
        }

        private fun bind(world: World) {
            val classes = File("scripts/build/classes/kotlin/main")
            val loader = URLClassLoader(arrayOf(classes.toURI().toURL()), AnnihilationRatesTests::class.java.classLoader)
            SCRIPTS.forEach { (script, source) ->
                // On the pre-N92 code the Annihilation script does not exist yet: the test then measures the old route.
                if (!File(source).exists()) return@forEach
                val compiled = File(classes, script.replace('.', '/') + ".class")
                if (!compiled.exists() || compiled.lastModified() < File(source).lastModified()) {
                    fail("${compiled.path} is missing or older than $source: the scripts compile did not run first (plugins/build.gradle test dependsOn)")
                }
                loader.loadClass(script)
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(world.plugins, world, mockk<Server>(relaxed = true))
            }
        }

        /** The Annihilation script installs the engine's one XP hook globally; other test classes in this JVM expect none. */
        @JvmStatic
        @AfterClass
        fun uninstallXpHook() {
            runCatching {
                gg.rsmod.game.model.skill.XpRates::class.java.getDeclaredMethod("setFactor", Function2::class.java).invoke(gg.rsmod.game.model.skill.XpRates, null)
            }
        }
    }
}
