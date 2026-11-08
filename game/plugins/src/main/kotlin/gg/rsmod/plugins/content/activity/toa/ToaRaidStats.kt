package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.activity.toa.ui.ToaSummaryUi

/**
 * N462 what the Raid Summary (OSRS toa_raid_summary 482, read on the burial chamber's Scoreboard: "a summary of the raid, including details
 * such as total damage dealt and received, number of deaths, and an honorary title" - Tombs of Amascut?oldid=15391794 Rewards, line 599)
 * shows: the time of every room (paths and bosses tabs), the challenge and overall totals, and per player damage dealt/taken, deaths and a
 * title. ONZEKER: whether OSRS counts a room from its first or its last attempt is not in the sources - first start to clear here.
 */
class ToaRaidStats {
    private val roomStart = hashMapOf<ToaRoomKey, Int>()
    /** Game cycles from the room's first start to its clear. */
    val roomCycles = linkedMapOf<ToaRoomKey, Int>()
    val damageDealt = hashMapOf<PlayerUID, Int>()
    val damageTaken = hashMapOf<PlayerUID, Int>()

    // ---- title data (Wiki line 603)
    /** Rooms in which a member earned the fewest room points of the party (The Leech: "in at least five rooms"). */
    private val leastPointRooms = hashMapOf<PlayerUID, Int>()
    private val waterTicks = hashMapOf<PlayerUID, Int>()
    private val specialUsed = hashMapOf<PlayerUID, Int>()
    private val foodEaten = hashMapOf<PlayerUID, Int>()
    private val scarabKills = hashMapOf<PlayerUID, Int>()
    private val beamHit = hashMapOf<PlayerUID, Int>()
    private val diedToBeam = hashSetOf<PlayerUID>()
    private val loadoutValue = hashMapOf<PlayerUID, Long>()
    private var clutch: PlayerUID? = null

    fun started(key: ToaRoomKey, cycle: Int) { roomStart.putIfAbsent(key, cycle) }

    fun cleared(key: ToaRoomKey, cycle: Int) {
        roomStart[key]?.let { roomCycles[key] = (cycle - it).coerceAtLeast(0) }
    }

    /** At a room clear, before the scope removes them: what every member did to and took from the room's npcs (their damage maps). */
    fun credit(players: List<Player>, npcs: List<Npc>) {
        // Damage dealt per account (a reconnect makes a new Player object; ultrareview #4).
        val dealtByUid = HashMap<PlayerUID, Int>().also { m -> npcs.forEach { n -> n.damageMap.damageByPlayerUid().forEach { (u, d) -> m.merge(u, d, Int::plus) } } }
        players.forEach { p ->
            val dealt = dealtByUid[p.uid] ?: 0
            val taken = npcs.sumOf { p.damageMap.getDamageFrom(it) }
            if (dealt > 0) damageDealt.merge(p.uid, dealt, Int::plus)
            if (taken > 0) damageTaken.merge(p.uid, taken, Int::plus)
        }
        // The Entomophobe: a dead scarab counts for the member who dealt it the most damage (ONZEKER: OSRS's kill credit rule).
        npcs.filter { it.isDead() && ToaIds.osrsNpcOf(it.id) in SCARABS }.forEach { npc ->
            (npc.damageMap.getMostDamage() as? Player)?.let { scarabKills.merge(it.uid, 1, Int::plus) }
        }
    }

    /** Room points of the room being cleared (before ToaPointsLedger.completeRoom moves them): the single lowest scorer of a party. */
    fun roomPoints(points: Map<PlayerUID, Int>) {
        if (points.size < 2) return
        val least = points.values.minOrNull() ?: return
        points.entries.filter { it.value == least }.singleOrNull()?.let { leastPointRooms.merge(it.key, 1, Int::plus) }
    }

    fun inZebakWater(player: Player) { waterTicks.merge(player.uid, 1, Int::plus) }
    fun specialAttack(player: Player, percent: Int) { specialUsed.merge(player.uid, percent, Int::plus) }
    fun ate(player: Player) { foodEaten.merge(player.uid, 1, Int::plus) }
    fun hetBeam(player: Player, cycle: Int) { beamHit[player.uid] = cycle }

    /** A death within two cycles of a Path of Het light beam hit counts as "Died to a light beam" (ONZEKER: the cause is not recorded). */
    fun died(player: Player, cycle: Int) {
        if ((beamHit[player.uid] ?: Int.MIN_VALUE) >= cycle - 2) diedToBeam += player.uid
    }

    /** The Wardens died: worn equipment value per member (The Peasant / The Showoff) and The Clutch (the only member still fighting). */
    fun wardensDefeated(raid: ToaRaid) {
        raid.players().forEach { p ->
            loadoutValue[p.uid] = p.equipment.rawItems.filterNotNull().sumOf { item ->
                (p.world.definitions.getNullable(ItemDef::class.java, item.id)?.cost ?: 0).toLong() * item.amount
            }
        }
        val active = raid.activePlayers()
        clutch = active.singleOrNull()?.uid?.takeIf { raid.roster.size > 1 }
    }

    /**
     * Owner 07-11 21:3x (advice A): the first title of the Wiki table (line 603, in its order) that a member meets. ONZEKER (Claude): the
     * comparing titles (most / least) need a party of 2+ and one sole member at the top, else a solo raider would always be "The Leech";
     * The Baller ("Dropped more than 20 orbs during the Akkha fight") and the per-style damage titles have no tracked data yet.
     */
    fun title(raid: ToaRaid, uid: PlayerUID): String {
        val group = raid.roster.size >= 2
        fun <T : Comparable<T>> sole(values: Map<PlayerUID, T>, highest: Boolean): Boolean {
            if (!group) return false
            val all = raid.roster.associateWith { values[it] }
            val known = all.values.filterNotNull()
            if (known.isEmpty()) return false
            val best = if (highest) known.maxOrNull() else known.minOrNull()
            return all[uid] == best && all.values.count { it == best } == 1
        }
        fun zeros(m: Map<PlayerUID, Int>) = raid.roster.associateWith { m[it] ?: 0 }
        val deaths = raid.roster.associateWith { raid.points.deaths(it) }
        return when {
            group && (leastPointRooms[uid] ?: 0) >= 5 -> "The Leech"
            (waterTicks[uid] ?: 0) >= 50 -> "The Swimmer" // 30 seconds = 50 ticks
            (specialUsed[uid] ?: 0) > 0 && sole(zeros(specialUsed), highest = true) -> "The Specialist"
            sole(zeros(foodEaten), highest = false) -> "The Picky Eater"
            loadoutValue.isNotEmpty() && sole(loadoutValue, highest = false) -> "The Peasant"
            loadoutValue.isNotEmpty() && sole(loadoutValue, highest = true) -> "The Showoff"
            (damageTaken[uid] ?: 0) > 0 && sole(zeros(damageTaken), highest = true) -> "The Tank"
            (scarabKills[uid] ?: 0) > 0 && sole(zeros(scarabKills), highest = true) -> "The Entomophobe"
            uid in diedToBeam -> "The Moth"
            (damageDealt[uid] ?: 0) > 0 && sole(zeros(damageDealt), highest = true) -> "The Carry"
            (deaths[uid] ?: 0) > 0 && sole(deaths, highest = true) -> "The Anchor"
            (foodEaten[uid] ?: 0) > 0 && sole(zeros(foodEaten), highest = true) -> "The Glutton"
            uid == clutch -> "The Clutch"
            else -> ""
        }
    }

    fun rows(raid: ToaRaid): List<ToaSummaryUi.Row> = raid.roster.map { uid ->
        ToaSummaryUi.Row(raid.memberNames[uid] ?: "", damageDealt[uid] ?: 0, damageTaken[uid] ?: 0, raid.points.deaths(uid), title(raid, uid))
    }

    fun room(path: ToaPath?, boss: Boolean): Int? = roomCycles[ToaRoomKey(path, boss)]

    fun times(overallCycles: Int?): ToaSummaryUi.Times {
        // Wiki Tombs Speed Runner?oldid=14926068: cumulative challenge time includes paths AND boss fights, not downtime.
        val challenges = roomCycles.values
        return ToaSummaryUi.Times(
            kephri = room(ToaPath.SCABARAS, true), akkha = room(ToaPath.HET, true), baba = room(ToaPath.APMEKEN, true),
            zebak = room(ToaPath.CRONDIS, true), wardens = room(null, true),
            scabaras = room(ToaPath.SCABARAS, false), het = room(ToaPath.HET, false), apmeken = room(ToaPath.APMEKEN, false),
            crondis = room(ToaPath.CRONDIS, false),
            challengeTotal = challenges.takeIf { it.isNotEmpty() }?.sum(),
            overall = overallCycles,
        )
    }

    companion object {
        /** OSRS scarabs of the Path of Scabaras (Kephri): swarm, soldier, spitting, arcane, agile (ToaBossNpcs). */
        val SCARABS = setOf(11723, 11724, 11725, 11726, 11727)

        /** The raid's stats for [player] while he is inside it, for hooks outside the raid code (food, special attack). */
        fun of(player: Player): ToaRaidStats? = ToaLobby.of(player.world).raid(player.uid)?.takeIf { it.contains(player) }?.stats
    }
}
