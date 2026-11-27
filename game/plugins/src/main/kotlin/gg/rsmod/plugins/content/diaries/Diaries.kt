package gg.rsmod.plugins.content.diaries

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playJingle

/**
 * DIARIES-OSRS (nachtrun 27-11): the OSRS Achievement Diaries. Tasks can be done in any order; a task is a [Trigger] that is matched
 * against generic events - a kill, an item that appears in the inventory, a place, a teleport, experience in a skill, a talk, an
 * agility lap or a named event that a content plugin sends with [event]. Composite tasks: [Trigger.Steps] ("from scratch", in order)
 * and [Trigger.All] (any order). OSRS: "Completing or updating any other task in-between ... will reset the progress" for the tasks
 * marked [Trigger.Steps.reset] / [Trigger.All.reset].
 *
 * Storage per player (persisted): the done task ids, the progress of the unfinished tasks and the number of claimed tiers per diary.
 */
object Diaries {
    enum class Tier(val title: String) { EASY("Easy"), MEDIUM("Medium"), HARD("Hard"), ELITE("Elite") }

    /** [journalHeader]: the first journal line '<header> Tasks' (OSRS 741 qj1; RuneLite DiaryRequirementsPlugin's title keys). */
    enum class Diary(val title: String, val areaName: String, val taskmaster: String, val journalHeader: String) {
        KANDARIN("Kandarin", "Kandarin", "the 'Wedge'", "Kandarin"),
        WESTERN("Western Provinces", "Western Provinces", "the Elder gnome child", "Western Area"),
        WILDERNESS("Wilderness", "Wilderness", "the Lesser Fanatic", "Wilderness Area"),
        VARROCK("Varrock", "Varrock", "Toby", "Varrock"),
        ARDOUGNE("Ardougne", "Ardougne", "Two-pints", "Ardougne"),
    }

    class Box(val x1: Int, val z1: Int, val x2: Int, val z2: Int, val h: Int = -1) {
        fun has(t: Tile) = t.x in x1..x2 && t.z in z1..z2 && (h < 0 || t.height == h)
    }

    class Area(val name: String, vararg val boxes: Box, val pred: ((Tile) -> Boolean)? = null) {
        fun has(t: Tile) = boxes.any { it.has(t) } || pred?.invoke(t) == true
    }

    /** The skill whose experience must come with these items (-1 = any gain counts: bought, picked, given by an npc). */
    fun producedBy(names: Set<String>): Int {
        val s = gg.rsmod.plugins.api.Skills
        fun any(vararg n: String) = names.any { it in n }
        return when {
            any("bass", "monkfish", "shark", "chocolate bomb", "lobster", "swordfish", "trout", "salmon") -> s.COOKING
            names.any { it.startsWith("raw ") } || any("leaping trout", "leaping salmon", "leaping sturgeon") -> s.FISHING
            names.any { it.contains("longbow") || it.contains("shortbow") } -> s.FLETCHING
            any("adamant spear", "rune hasta", "gold helmet", "golden helmet") -> s.SMITHING
            any("teak logs", "mahogany logs", "yew logs", "magic logs", "maple logs") -> s.WOODCUTTING
            names.any { it.endsWith(" ore") } -> s.MINING
            any("clean irit", "super antipoison(3)", "super antipoison(4)") || names.any { it.startsWith("stamina mix") } -> s.HERBLORE
            else -> -1
        }
    }

    sealed class Trigger {
        /** Kill an npc with one of these (lower-case) names, optionally in an area and only while [cond] holds. */
        class Kill(val names: Set<String>, val area: Area? = null, val cond: ((Player) -> Boolean)? = null) : Trigger()

        /**
         * [count] of these items appear in the inventory (never from the bank). A PRODUCED item ([skill] >= 0: caught, cooked, cut,
         * mined, fletched, smithed, cleaned, mixed) only counts in a tick that also gave [skill] experience - picking a shark up from
         * the floor, a trade or a spawn is not 'cooking a shark' (D7 27-11: the bot's own sharks finished Wilderness elite 3).
         */
        class Gain(val names: Set<String>, val area: Area? = null, val count: Int = 1, val ids: Set<Int> = emptySet(), val cond: ((Player) -> Boolean)? = null,
                   val skill: Int = producedBy(names), val once: Boolean = false) : Trigger()

        /** These logs leave the inventory in the same tick as Firemaking experience. */
        class Burn(val names: Set<String>, val area: Area? = null) : Trigger()

        /** Be in the area (with [cond]). Area null = anywhere. */
        class Enter(val area: Area?, val cond: ((Player) -> Boolean)? = null) : Trigger()

        /** Arrive in the area by a jump of more than 15 tiles (teleport, fairy ring, spirit tree, glider, boat, lever). */
        class Tele(val area: Area, val from: Area? = null) : Trigger()

        /** Experience in a skill while in the area (shortcuts, chests, health checks). */
        class Xp(val skill: Int, val area: Area) : Trigger()

        /** Prayer points go up while in the area (praying at an altar), optionally only while [cond] holds. */
        class PrayUp(val area: Area, val cond: ((Player) -> Boolean)? = null) : Trigger()

        /** Equip a cape whose name starts with [prefix] while in the area. */
        class EquipCape(val prefix: String, val area: Area) : Trigger()

        /** Talk to (interact with) an npc with this name. */
        class Talk(val names: Set<String>, val area: Area? = null, val cond: ((Player) -> Boolean)? = null) : Trigger()

        /** A named event from a content plugin, see [event]. */
        class Ev(val key: String, val area: Area? = null) : Trigger()

        class Steps(val steps: List<Trigger>, val reset: Boolean = false) : Trigger()

        class All(val parts: List<Trigger>, val reset: Boolean = false) : Trigger()
    }

    class Task(val id: String, val diary: Diary, val tier: Tier, val text: String, val trigger: Trigger, val req: String = "", val osrs: String? = null)

    val DONE = AttributeKey<String>(persistenceKey = "diary_done")
    val PROGRESS = AttributeKey<String>(persistenceKey = "diary_progress")
    val CLAIMED = AttributeKey<String>(persistenceKey = "diary_claimed")

    private val SNAP_INV = AttributeKey<Map<Int, Int>>()
    private val SNAP_XP = AttributeKey<DoubleArray>()
    private val SNAP_TILE = AttributeKey<Tile>()
    private val SNAP_PRAY = AttributeKey<Int>()
    private val SNAP_CAPE = AttributeKey<Int>()
    private val SNAP_TALK = AttributeKey<Int>()

    val TASKS: List<Task> by lazy { DiaryTasks.ALL }
    private val BY_ID by lazy { TASKS.associateBy { it.id } }

    fun tasks(diary: Diary, tier: Tier) = TASKS.filter { it.diary == diary && it.tier == tier }

    fun done(player: Player): Set<String> = (player.attr[DONE] ?: "").split(',').filter { it.isNotBlank() }.toSet()

    fun isDone(player: Player, id: String) = id in done(player)

    fun progress(player: Player): MutableMap<String, Int> =
        (player.attr[PROGRESS] ?: "").split(',').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }
            .toMap(HashMap())

    private fun saveProgress(player: Player, map: Map<String, Int>) {
        val s = map.filter { it.value != 0 }.entries.joinToString(",") { "${it.key}=${it.value}" }
        if (s.isEmpty()) player.attr.remove(PROGRESS) else player.attr[PROGRESS] = s
    }

    fun tierDone(player: Player, diary: Diary, tier: Tier): Boolean {
        val d = done(player)
        return tasks(diary, tier).all { it.id in d }
    }

    fun doneCount(player: Player, diary: Diary, tier: Tier? = null): Int {
        val d = done(player)
        return TASKS.count { it.diary == diary && (tier == null || it.tier == tier) && it.id in d }
    }

    fun totalCount(diary: Diary, tier: Tier? = null) = TASKS.count { it.diary == diary && (tier == null || it.tier == tier) }

    /** Tiers claimed at the taskmaster: 0 = none, 1 = easy ... 4 = elite. */
    fun claimed(player: Player, diary: Diary): Int =
        (player.attr[CLAIMED] ?: "").split(',').firstOrNull { it.startsWith(diary.name + "=") }?.substringAfter('=')?.toIntOrNull() ?: 0

    fun setClaimed(player: Player, diary: Diary, tiers: Int) {
        val m = (player.attr[CLAIMED] ?: "").split(',').filter { it.contains('=') && !it.startsWith(diary.name + "=") }.toMutableList()
        if (tiers > 0) m += "${diary.name}=$tiers"
        if (m.isEmpty()) player.attr.remove(CLAIMED) else player.attr[CLAIMED] = m.joinToString(",")
    }

    /** OSRS rewards and perks follow the CLAIMED tier ("you must claim the reward to unlock the benefits"). */
    fun hasClaimed(player: Player, diary: Diary, tier: Tier) = claimed(player, diary) > tier.ordinal

    // ---------------------------------------------------------------------------------------------------------- events

    private sealed class E {
        class Killed(val name: String) : E()
        /** [xp] = the skills that gained experience in the same tick. */
        class Gained(val id: Int, val name: String, val amount: Int, val xp: Set<Int> = emptySet()) : E()
        class Burned(val name: String) : E()
        object At : E()
        class Jump(val from: Tile) : E()
        class Xp(val skill: Int) : E()
        object PrayUp : E()
        class Cape(val name: String) : E()
        class Talked(val name: String) : E()
        class Key(val key: String) : E()
    }

    fun onKill(player: Player, npc: Npc) = handle(player, listOf(E.Killed(npc.def.name.lowercase())))

    /** A named event from a content plugin, for example "pest_control:veteran", "lap:35609", "hunt:copper longtail". */
    fun event(player: Player, key: String) = runCatching { handle(player, listOf(E.Key(key.lowercase()))) }
        .onFailure { gg.rsmod.game.model.Trace.crash("diary event $key", it) }

    /** Every tick (diary timer): the inventory, xp, prayer, cape and tile against the last tick. */
    fun tick(player: Player) {
        val events = ArrayList<E>(4)
        val inv = HashMap<Int, Int>()
        for (it in player.inventory.rawItems) if (it != null) inv.merge(it.id, it.amount, Int::plus)
        val xp = DoubleArray(player.skills.maxSkills) { player.skills.getCurrentXp(it) }
        val bankOpen = player.interfaces.isVisible(762) || player.interfaces.isVisible(11) || player.interfaces.isVisible(335)
        val oldInv = player.attr[SNAP_INV]
        val oldXp = player.attr[SNAP_XP]
        val oldTile = player.attr[SNAP_TILE]
        if (oldInv != null && oldXp != null && !bankOpen) {
            val fmXp = xp[gg.rsmod.plugins.api.Skills.FIREMAKING] > oldXp[gg.rsmod.plugins.api.Skills.FIREMAKING]
            val xpSkills = xp.indices.filter { xp[it] > oldXp[it] }.toSet()
            inv.forEach { (id, n) ->
                val d = n - (oldInv[id] ?: 0)
                if (d > 0) {
                    val def = player.world.definitions.get(ItemDef::class.java, id)
                    if (!def.noted) events += E.Gained(id, def.name.lowercase(), d, xpSkills)
                }
            }
            if (fmXp) oldInv.forEach { (id, n) -> if ((inv[id] ?: 0) < n) events += E.Burned(player.world.definitions.get(ItemDef::class.java, id).name.lowercase()) }
            for (s in xp.indices) if (xp[s] > oldXp[s]) events += E.Xp(s)
        }
        val pray = player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.PRAYER)
        if ((player.attr[SNAP_PRAY] ?: pray) < pray) events += E.PrayUp
        val cape = player.equipment[gg.rsmod.plugins.api.EquipmentType.CAPE.id]?.id ?: -1
        val oldCape = player.attr[SNAP_CAPE]
        if (oldCape != null && cape != oldCape && cape >= 0) events += E.Cape(player.world.definitions.get(ItemDef::class.java, cape).name.lowercase())
        if (oldTile != null && (oldTile.height != player.tile.height || oldTile.getDistance(player.tile) > 15)) events += E.Jump(oldTile)
        val talk = player.attr[gg.rsmod.game.model.attr.INTERACTING_NPC_ATTR]?.get()
        if (talk != null && talk.index != player.attr[SNAP_TALK] && talk.tile.getDistance(player.tile) <= 2) {
            player.attr[SNAP_TALK] = talk.index
            events += E.Talked(talk.def.name.lowercase())
        } else if (talk == null) {
            player.attr.remove(SNAP_TALK)
        }
        events += E.At
        player.attr[SNAP_INV] = inv
        player.attr[SNAP_XP] = xp
        player.attr[SNAP_TILE] = player.tile
        player.attr[SNAP_PRAY] = pray
        player.attr[SNAP_CAPE] = cape
        handle(player, events)
    }

    private fun handle(player: Player, events: List<E>) {
        val done = done(player)
        val prog = progress(player)
        var changed = false
        val updated = ArrayList<Task>()
        for (task in TASKS) {
            if (task.id in done) continue
            val before = prog[task.id] ?: 0
            var p = before
            for (e in events) p = advance(player, task.trigger, e, p)
            if (p != before) {
                prog[task.id] = p
                changed = true
                updated += task
            }
        }
        if (!changed) return
        // A task without its OSRS requirements keeps its progress and completes on a later event once the levels are there.
        val completed = updated.filter { complete(it.trigger, prog[it.id] ?: 0) && runCatching { DiaryUi.meetsRequirements(player, it.req) }.getOrDefault(true) }
        // OSRS: completing or updating another task resets the 'from scratch' / 'in a row' tasks.
        for (task in TASKS) {
            if (task.id in done || task in updated) continue
            val reset = when (val t = task.trigger) { is Trigger.Steps -> t.reset; is Trigger.All -> t.reset; else -> false }
            if (reset && (prog[task.id] ?: 0) != 0) prog[task.id] = 0
        }
        completed.forEach { prog.remove(it.id) }
        saveProgress(player, prog)
        completed.forEach { finish(player, it) }
    }

    private fun advance(player: Player, t: Trigger, e: E, p: Int): Int = when (t) {
        is Trigger.Steps -> if (p < t.steps.size && advance(player, t.steps[p], e, 0) >= need(t.steps[p])) p + 1 else p
        is Trigger.All -> {
            var m = p
            t.parts.forEachIndexed { i, part -> if (m and (1 shl i) == 0 && advance(player, part, e, 0) >= need(part)) m = m or (1 shl i) }
            m
        }
        else -> p + amount(player, t, e)
    }

    private fun need(t: Trigger) = if (t is Trigger.Gain) t.count else 1

    private fun complete(t: Trigger, p: Int) = when (t) {
        is Trigger.Steps -> p >= t.steps.size
        is Trigger.All -> p == (1 shl t.parts.size) - 1
        else -> p >= need(t)
    }

    private fun inArea(player: Player, a: Area?) = a == null || a.has(player.tile)

    private fun amount(player: Player, t: Trigger, e: E): Int = when (t) {
        is Trigger.Kill -> if (e is E.Killed && e.name in t.names && inArea(player, t.area) && t.cond?.invoke(player) != false) 1 else 0
        is Trigger.Gain -> if (e is E.Gained && (e.name in t.names || e.id in t.ids) && (t.skill < 0 || t.skill in e.xp) && inArea(player, t.area) &&
            t.cond?.invoke(player) != false && (!t.once || e.amount >= t.count)) e.amount else 0
        is Trigger.Burn -> if (e is E.Burned && e.name in t.names && inArea(player, t.area)) 1 else 0
        is Trigger.Enter -> if (e === E.At && inArea(player, t.area) && t.cond?.invoke(player) != false) 1 else 0
        is Trigger.Tele -> if (e is E.Jump && t.area.has(player.tile) && !t.area.has(e.from) && (t.from == null || t.from.has(e.from))) 1 else 0
        is Trigger.Xp -> if (e is E.Xp && e.skill == t.skill && t.area.has(player.tile)) 1 else 0
        is Trigger.PrayUp -> if (e === E.PrayUp && t.area.has(player.tile) && t.cond?.invoke(player) != false) 1 else 0
        is Trigger.EquipCape -> if (e is E.Cape && e.name.startsWith(t.prefix) && t.area.has(player.tile)) 1 else 0
        is Trigger.Talk -> if (e is E.Talked && e.name in t.names && inArea(player, t.area) && t.cond?.invoke(player) != false) 1 else 0
        is Trigger.Ev -> if (e is E.Key && e.key == t.key && inArea(player, t.area)) 1 else 0
        is Trigger.Steps, is Trigger.All -> 0
    }

    private fun finish(player: Player, task: Task) {
        player.attr[DONE] = (done(player) + task.id).joinToString(",")
        val tier = task.tier.title.lowercase()
        val article = if (tier[0] in "aeiou") "an" else "a"
        player.message("<col=dc143c>Well done! You have completed $article $tier task in the ${task.diary.areaName} area. Your Achievement Diary has been updated.</col>")
        if (tierDone(player, task.diary, task.tier)) {
            player.playJingle(JINGLE_TIER)
            player.message("<col=dc143c>Congratulations! You have completed all of the $tier tasks in the ${task.diary.areaName} area. Speak to ${task.diary.taskmaster} to claim your reward.</col>")
        }
        runCatching { DiaryUi.refresh(player) }
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.PLUGIN) { "diary ${player.username} done ${task.id}" }
    }

    /** OSRS jingle 'Achievement Diary tier complete' does not exist in 667 - the quest-complete jingle. */
    const val JINGLE_TIER = 152

    /** Admin/bot: set or clear a task, a tier or a whole diary. */
    fun adminSet(player: Player, what: String, on: Boolean): Int {
        val match = TASKS.filter { it.id.equals(what, true) || it.id.startsWith(what.uppercase() + "_") || it.diary.name.equals(what, true) }
        val d = done(player).toMutableSet()
        if (on) d += match.map { it.id } else d -= match.map { it.id }.toSet()
        if (d.isEmpty()) player.attr.remove(DONE) else player.attr[DONE] = d.joinToString(",")
        runCatching { DiaryUi.refresh(player) }
        return match.size
    }

    fun task(id: String) = BY_ID[id]
}
