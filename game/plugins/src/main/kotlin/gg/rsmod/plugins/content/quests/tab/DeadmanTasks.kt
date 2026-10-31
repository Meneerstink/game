package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PointBalance
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import java.io.File
import java.time.LocalDateTime

/**
 * Deadman Tasks (N104, owner design GOEDGEKEURD 05/06; owner 03:40 "B. Alles live, DP direct"). The mechanics and the progress are
 * real and live; THE LIST, THE DP PER TASK AND THE TIER BONUSES ARE PROVISIONAL (spec D / H9: modest values, shown to the owner
 * afterwards) and live only in [TASKS] / [TIER_BONUS].
 *
 * Abuse rules (H9): a PvP task counts only a kill the one PvP judge called valid ([gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill],
 * alt / multilog / farming refused there); a task pays once - it is marked done before the points are added (saturating
 * [PointBalance.add], N106); level tasks read the real level; every completion is logged ([LOG]).
 * Saved as "id:progress;..." plus the done ids - an account without them has no progress (spec H4).
 */
object DeadmanTasks {
    enum class Tier(val title: String, val colour: String) { EASY("Easy", "c98a4b"), MEDIUM("Medium", "7f9fe0"), HARD("Hard", "4fd0d6"), ELITE("Elite", "ff6262") }

    enum class Type(val label: String) { PVP("PvP"), BOSSES("Bosses"), BREACH("Breach"), CHEST("Chest"), SKILLS("Skills"), EMBLEM("Emblem") }

    enum class Track { PVP_KILL, PVP_KILL_SKULLED, PVP_KILL_MULTI, CHEST_OPEN, BREACH_DAMAGE, BREACH_KILL, COMBAT_LEVEL, TOTAL_LEVEL, SKULLED_MINUTES, KILLS, EMBLEM_TIER, BARROWS_CHEST }

    enum class Label { NONE, PVP, DANGER }

    class Task(
        val id: String,
        val tier: Tier,
        val type: Type,
        val name: String,
        val description: String,
        val track: Track,
        val target: Int,
        val dp: Int,
        val icon: Int,
        val label: Label = Label.NONE,
        /** [Track.KILLS]: the npc names whose kills count. */
        val npcs: Set<String> = emptySet(),
    )

    private fun t(id: String, tier: Tier, type: Type, name: String, desc: String, track: Track, target: Int, icon: Int, label: Label = Label.NONE, npcs: Set<String> = emptySet()) =
        Task(id, tier, type, name, desc, track, target, DP.getValue(tier), icon, label, npcs)

    /** PROVISIONAL DP per task (owner: modest, like the design: 5 / 10 / 15 / 25). */
    val DP = mapOf(Tier.EASY to 5, Tier.MEDIUM to 10, Tier.HARD to 15, Tier.ELITE to 25)

    /** PROVISIONAL bonus for finishing every task of a tier. */
    val TIER_BONUS = mapOf(Tier.EASY to 25, Tier.MEDIUM to 50, Tier.HARD to 75, Tier.ELITE to 100)

    private val GWD = setOf("general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth")
    private val DKS = setOf("dagannoth rex", "dagannoth prime", "dagannoth supreme")

    /** PROVISIONAL list (spec D). */
    val TASKS: List<Task> =
        listOf(
            t("e_pk1", Tier.EASY, Type.PVP, "First blood", "Kill a player", Track.PVP_KILL, 1, Items.SKULL, Label.PVP),
            t("e_chest1", Tier.EASY, Type.CHEST, "Open a DMM Chest", "Be the one who opens it", Track.CHEST_OPEN, 1, Items.CASKET, Label.DANGER),
            t("e_breach1", Tier.EASY, Type.BREACH, "Breach fighter", "Get loot from a breach monster", Track.BREACH_KILL, 1, Items.VESTAS_LONGSWORD, Label.DANGER),
            t("e_cb40", Tier.EASY, Type.SKILLS, "Reach combat 40", "Train your combat", Track.COMBAT_LEVEL, 40, Items.DRAGON_SCIMITAR),
            t("e_skull5", Tier.EASY, Type.PVP, "Skulled survivor", "Stay skulled 5 minutes", Track.SKULLED_MINUTES, 5, Items.SKULL, Label.PVP),
            t("e_kbd1", Tier.EASY, Type.BOSSES, "King Black Dragon", "Kill the King Black Dragon", Track.KILLS, 1, Items.KBD_HEADS, Label.DANGER, setOf("king black dragon")),
            t("e_chaos1", Tier.EASY, Type.BOSSES, "Chaos Elemental", "Kill the Chaos Elemental", Track.KILLS, 1, Items.DRAGON_BONES, Label.DANGER, setOf("chaos elemental")),
            t("e_kq1", Tier.EASY, Type.BOSSES, "Kalphite Queen", "Kill the Kalphite Queen", Track.KILLS, 1, Items.DRAGON_BONES, npcs = setOf("kalphite queen")),
            t("e_dks1", Tier.EASY, Type.BOSSES, "Dagannoth King", "Kill a Dagannoth King", Track.KILLS, 1, Items.DRAGON_BONES, npcs = DKS),
            t("e_emblem1", Tier.EASY, Type.EMBLEM, "Find an emblem", "Own a Deadman emblem", Track.EMBLEM_TIER, 1, DeadmanEmblem.FIRST_EMBLEM),
            t("e_total500", Tier.EASY, Type.SKILLS, "Total level 500", "Train any skills", Track.TOTAL_LEVEL, 500, Items.QUEST_POINT_CAPE),
            t("e_barrows1", Tier.EASY, Type.BOSSES, "Barrows", "Open a Barrows chest", Track.BARROWS_CHEST, 1, Items.DHAROKS_GREATAXE),
            t("m_pk5", Tier.MEDIUM, Type.PVP, "Player hunter", "Kill 5 players", Track.PVP_KILL, 5, Items.SKULL, Label.PVP),
            t("m_pkskull3", Tier.MEDIUM, Type.PVP, "Kill skulled players", "Kill 3 skulled players", Track.PVP_KILL_SKULLED, 3, Items.SKULL, Label.PVP),
            t("m_pkmulti3", Tier.MEDIUM, Type.PVP, "Multi-way PK", "Kill 3 players in multi", Track.PVP_KILL_MULTI, 3, Items.SKULL, Label.PVP),
            t("m_chest3", Tier.MEDIUM, Type.CHEST, "Chest hunter", "Open 3 Deadman's Chests", Track.CHEST_OPEN, 3, Items.CASKET, Label.DANGER),
            t("m_breachdmg", Tier.MEDIUM, Type.BREACH, "Breach boss damage", "250 damage on one boss", Track.BREACH_DAMAGE, 1, Items.STATIUSS_WARHAMMER, Label.DANGER),
            t("m_breach10", Tier.MEDIUM, Type.BREACH, "Breach regular", "Loot 10 breach monsters", Track.BREACH_KILL, 10, Items.VESTAS_LONGSWORD, Label.DANGER),
            t("m_cb81", Tier.MEDIUM, Type.SKILLS, "Reach combat 81", "Get to bracket 81-95", Track.COMBAT_LEVEL, 81, Items.DRAGON_SCIMITAR),
            t("m_skull15", Tier.MEDIUM, Type.PVP, "Skulled for a while", "Stay skulled 15 minutes", Track.SKULLED_MINUTES, 15, Items.SKULL, Label.PVP),
            t("m_kbd25", Tier.MEDIUM, Type.BOSSES, "Dragon slayer", "Kill the King Black Dragon 25 times", Track.KILLS, 25, Items.KBD_HEADS, Label.DANGER, setOf("king black dragon")),
            t("m_gwd10", Tier.MEDIUM, Type.BOSSES, "God Wars", "Kill 10 God Wars generals", Track.KILLS, 10, Items.BANDOS_CHESTPLATE, npcs = GWD),
            t("m_dks30", Tier.MEDIUM, Type.BOSSES, "Kings of the sea", "Kill 30 Dagannoth Kings", Track.KILLS, 30, Items.DRAGON_BONES, npcs = DKS),
            t("m_emblem3", Tier.MEDIUM, Type.EMBLEM, "Emblem tier 3", "Own a tier 3 emblem", Track.EMBLEM_TIER, 3, DeadmanEmblem.FIRST_EMBLEM),
            t("m_total1000", Tier.MEDIUM, Type.SKILLS, "Total level 1,000", "Train any skills", Track.TOTAL_LEVEL, 1000, Items.QUEST_POINT_CAPE),
            t("m_barrows10", Tier.MEDIUM, Type.BOSSES, "Barrows regular", "Open 10 Barrows chests", Track.BARROWS_CHEST, 10, Items.DHAROKS_GREATAXE),
            t("h_pk25", Tier.HARD, Type.PVP, "Killer", "Kill 25 players", Track.PVP_KILL, 25, Items.SKULL, Label.PVP),
            t("h_pkskull10", Tier.HARD, Type.PVP, "Skull collector", "Kill 10 skulled players", Track.PVP_KILL_SKULLED, 10, Items.SKULL, Label.PVP),
            t("h_pkmulti10", Tier.HARD, Type.PVP, "Multi menace", "Kill 10 players in multi", Track.PVP_KILL_MULTI, 10, Items.SKULL, Label.PVP),
            t("h_chest10", Tier.HARD, Type.CHEST, "Chest raider", "Open 10 Deadman's Chests", Track.CHEST_OPEN, 10, Items.CASKET, Label.DANGER),
            t("h_breach50", Tier.HARD, Type.BREACH, "Breach veteran", "Loot 50 breach monsters", Track.BREACH_KILL, 50, Items.VESTAS_LONGSWORD, Label.DANGER),
            t("h_cb96", Tier.HARD, Type.SKILLS, "Reach combat 96", "Get to bracket 96+", Track.COMBAT_LEVEL, 96, Items.DRAGON_SCIMITAR),
            t("h_skull60", Tier.HARD, Type.PVP, "Living dangerously", "Stay skulled 60 minutes", Track.SKULLED_MINUTES, 60, Items.SKULL, Label.PVP),
            t("h_corp10", Tier.HARD, Type.BOSSES, "Corporeal Beast", "Kill the Corporeal Beast 10 times", Track.KILLS, 10, Items.SPIRIT_SHIELD, npcs = setOf("corporeal beast")),
            t("h_nex1", Tier.HARD, Type.BOSSES, "Nex", "Kill Nex", Track.KILLS, 1, Items.TORVA_FULL_HELM, npcs = setOf("nex")),
            t("h_gwd50", Tier.HARD, Type.BOSSES, "General slayer", "Kill 50 God Wars generals", Track.KILLS, 50, Items.BANDOS_CHESTPLATE, npcs = GWD),
            t("h_emblem5", Tier.HARD, Type.EMBLEM, "Emblem tier 5", "Own a tier 5 emblem", Track.EMBLEM_TIER, 5, DeadmanEmblem.FIRST_EMBLEM),
            t("h_total1500", Tier.HARD, Type.SKILLS, "Total level 1,500", "Train any skills", Track.TOTAL_LEVEL, 1500, Items.QUEST_POINT_CAPE),
            t("h_barrows50", Tier.HARD, Type.BOSSES, "Barrows hunter", "Open 50 Barrows chests", Track.BARROWS_CHEST, 50, Items.DHAROKS_GREATAXE),
            t("el_pk100", Tier.ELITE, Type.PVP, "Deadman legend", "Kill 100 players", Track.PVP_KILL, 100, Items.SKULL, Label.PVP),
            t("el_pkskull50", Tier.ELITE, Type.PVP, "Skull hunter", "Kill 50 skulled players", Track.PVP_KILL_SKULLED, 50, Items.SKULL, Label.PVP),
            t("el_chest25", Tier.ELITE, Type.CHEST, "Chest master", "Open 25 Deadman's Chests", Track.CHEST_OPEN, 25, Items.CASKET, Label.DANGER),
            t("el_breach200", Tier.ELITE, Type.BREACH, "Breach master", "Loot 200 breach monsters", Track.BREACH_KILL, 200, Items.VESTAS_LONGSWORD, Label.DANGER),
            t("el_cb126", Tier.ELITE, Type.SKILLS, "Max combat", "Reach combat 126", Track.COMBAT_LEVEL, 126, Items.DRAGON_SCIMITAR),
            t("el_skull180", Tier.ELITE, Type.PVP, "Born skulled", "Stay skulled 180 minutes", Track.SKULLED_MINUTES, 180, Items.SKULL, Label.PVP),
            t("el_nex25", Tier.ELITE, Type.BOSSES, "Nex hunter", "Kill Nex 25 times", Track.KILLS, 25, Items.TORVA_FULL_HELM, npcs = setOf("nex")),
            t("el_corp50", Tier.ELITE, Type.BOSSES, "Beast hunter", "Kill the Corporeal Beast 50 times", Track.KILLS, 50, Items.SPIRIT_SHIELD, npcs = setOf("corporeal beast")),
            t("el_kbd250", Tier.ELITE, Type.BOSSES, "Dragon master", "Kill the King Black Dragon 250 times", Track.KILLS, 250, Items.KBD_HEADS, Label.DANGER, setOf("king black dragon")),
            t("el_jad", Tier.ELITE, Type.BOSSES, "Fire cape", "Kill TzTok-Jad", Track.KILLS, 1, Items.FIRE_CAPE, npcs = setOf("tztok-jad")),
            t("el_emblemmax", Tier.ELITE, Type.EMBLEM, "Top emblem", "Own a tier ${DeadmanEmblem.MAX_TIER} emblem", Track.EMBLEM_TIER, DeadmanEmblem.MAX_TIER, DeadmanEmblem.FIRST_EMBLEM),
            t("el_total2000", Tier.ELITE, Type.SKILLS, "Total level 2,000", "Train any skills", Track.TOTAL_LEVEL, 2000, Items.QUEST_POINT_CAPE),
        )

    val PROGRESS = AttributeKey<String>(persistenceKey = "deadman_tasks_progress")
    val DONE = AttributeKey<String>(persistenceKey = "deadman_tasks_done")
    val EARNED = AttributeKey<Int>(persistenceKey = "deadman_tasks_dp")
    val BONUSES = AttributeKey<String>(persistenceKey = "deadman_tasks_bonuses")

    const val LOG = "./data/logs/deadman_tasks.log"

    private val PARSED = AttributeKey<HashMap<String, Int>>()

    fun progressMap(player: Player): HashMap<String, Int> =
        player.attr[PARSED] ?: HashMap<String, Int>().also { map ->
            (player.attr[PROGRESS] ?: "").split(';').forEach { p ->
                val at = p.indexOf(':')
                if (at > 0) p.substring(at + 1).toIntOrNull()?.let { map[p.substring(0, at)] = it }
            }
            player.attr[PARSED] = map
        }

    fun done(player: Player): Set<String> = (player.attr[DONE] ?: "").split(';').filter { it.isNotBlank() }.toSet()

    fun earned(player: Player): Int = player.attr[EARNED] ?: 0

    /** Live progress of [task]: counters from the map, levels and emblem tiers read from the player now. */
    fun progress(player: Player, task: Task): Int {
        if (task.id in done(player)) return task.target
        val v =
            when (task.track) {
                Track.COMBAT_LEVEL -> player.combatLevel
                Track.TOTAL_LEVEL -> runCatching { player.skills.calculateTotalLevel }.getOrDefault(0)
                Track.EMBLEM_TIER -> runCatching { DeadmanEmblem.ownedTier(player) }.getOrDefault(0)
                else -> progressMap(player)[task.id] ?: 0
            }
        return v.coerceIn(0, task.target)
    }

    fun tierTasks(tier: Tier) = TASKS.filter { it.tier == tier }

    fun tierDone(player: Player, tier: Tier): Int = tierTasks(tier).count { it.id in done(player) }

    private fun save(player: Player) {
        player.attr[PROGRESS] = progressMap(player).entries.filter { e -> TASKS.any { it.id == e.key } }.joinToString(";") { "${it.key}:${it.value}" }
    }

    /** Adds [amount] to every open task tracking [track] that [accept]s, then completes what reached its target. */
    fun advance(player: Player, track: Track, amount: Int = 1, accept: (Task) -> Boolean = { true }) {
        val doneIds = done(player)
        var changed = false
        TASKS.filter { it.track == track && it.id !in doneIds && accept(it) }.forEach { task ->
            val map = progressMap(player)
            map[task.id] = ((map[task.id] ?: 0).toLong() + amount).coerceAtMost(task.target.toLong()).toInt()
            changed = true
        }
        if (changed) save(player)
        check(player)
    }

    /** Completes every task whose progress reached its target (levels and emblems are checked here too). */
    fun check(player: Player) {
        val doneIds = done(player).toMutableSet()
        for (task in TASKS) {
            if (task.id in doneIds) continue
            if (progress(player, task) < task.target) continue
            // Done first, then the points: a task can never pay twice.
            doneIds += task.id
            player.attr[DONE] = doneIds.joinToString(";")
            PointBalance.add(player.attr, StoreCatalogue.Currency.DEADMAN.attr, task.dp)
            player.attr[EARNED] = ((player.attr[EARNED] ?: 0).toLong() + task.dp).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            player.message("<col=ef1020>Deadman Task complete: ${task.name}!</col> You receive ${task.dp} Deadman Points.")
            log(player, "TASK ${task.id} +${task.dp}")
        }
        val bonuses = (player.attr[BONUSES] ?: "").split(';').filter { it.isNotBlank() }.toMutableSet()
        for (tier in Tier.values()) {
            if (tier.name in bonuses) continue
            if (tierTasks(tier).any { it.id !in doneIds }) continue
            bonuses += tier.name
            player.attr[BONUSES] = bonuses.joinToString(";")
            val bonus = TIER_BONUS.getValue(tier)
            PointBalance.add(player.attr, StoreCatalogue.Currency.DEADMAN.attr, bonus)
            player.attr[EARNED] = ((player.attr[EARNED] ?: 0).toLong() + bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            player.message("<col=ef1020>Every ${tier.title} Deadman Task done!</col> You receive $bonus bonus Deadman Points.")
            log(player, "TIER ${tier.name} +$bonus")
        }
    }

    private fun log(player: Player, what: String) {
        runCatching {
            val file = File(LOG)
            file.parentFile?.mkdirs()
            file.appendText("${LocalDateTime.now()}\t${player.username}\t$what\n")
        }
    }

    // ---- hooks

    fun onPvpKill(killer: Player, victimSkulled: Boolean, multi: Boolean) {
        advance(killer, Track.PVP_KILL)
        if (victimSkulled) advance(killer, Track.PVP_KILL_SKULLED)
        if (multi) advance(killer, Track.PVP_KILL_MULTI)
    }

    fun onChestOpened(player: Player) = advance(player, Track.CHEST_OPEN)

    fun onBreachLoot(player: Player, damage: Int) {
        advance(player, Track.BREACH_KILL)
        if (damage >= gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot.EMBLEM_DAMAGE) advance(player, Track.BREACH_DAMAGE)
    }

    fun onNpcKilled(player: Player, npcName: String) {
        val name = npcName.lowercase()
        advance(player, Track.KILLS) { name in it.npcs }
    }

    fun onBarrowsChest(player: Player) = advance(player, Track.BARROWS_CHEST)

    fun onSkulledMinute(player: Player) = advance(player, Track.SKULLED_MINUTES)

    /**
     * A skulled minute counts only while the skull really counts down in a dangerous area: [PvpSkull] pauses the countdown after a
     * minute on one tile (and in instances), so a skull left standing in a bank never earns the "Stay skulled" tasks. The
     * area is the tile the client shows ([zoneTile], 09-24 transit rule), not the far end of an agility shortcut.
     */
    fun skulledMinuteCounts(player: Player): Boolean =
        PvpSkull.hasSkullTimer(player) && !player.timers.isPaused(SKULL_ICON_DURATION_TIMER) && AreaState.isDangerous(player.zoneTile())

    // ---- views

    /** "Closest to done": open tasks with the most progress first (share of the target), at most [n]. */
    fun closest(player: Player, n: Int = 4): List<Task> {
        val doneIds = done(player)
        return TASKS.filter { it.id !in doneIds }
            .sortedWith(compareByDescending<Task> { progress(player, it).toDouble() / it.target }.thenBy { it.tier.ordinal }.thenBy { it.target })
            .take(n)
    }
}
