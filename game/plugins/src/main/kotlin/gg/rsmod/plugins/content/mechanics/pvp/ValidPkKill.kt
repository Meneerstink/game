package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.util.io.AtomicFiles
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The one server-side answer to "does this PvP death count as a real PK kill?" (owner 2026-09-25, Deadman emblems).
 *
 * The killer is never decided here: the death pipeline's own attribution (damage map, then the PvP aggressor window -
 * `death.plugin.kts`) names him, and this checker only judges the kill. Safe-minigame deaths (duels, Clan Wars, Fight
 * Cave - [gg.rsmod.plugins.content.mechanics.death.SafeDeath]) never reach it because the death pipeline stops first.
 *
 * Verdicts:
 *  - [Verdict.VALID]: counts (emblem upgrade, killstreak and Deadman Points, the post-kill grace).
 *  - blocked kills that still are real deaths ([Verdict.REPEAT_VICTIM], [Verdict.DAILY_CAP], [Verdict.LOW_RISK],
 *    [Verdict.FED_KILL]): nothing upgrades or scores.
 *  - abuse ([Verdict.SAME_ADDRESS], [Verdict.STAFF], [Verdict.SELF], [Verdict.NO_KILLER]): nothing may move between the two
 *    accounts at all (owner: same-IP/alt and admin kills destroy the emblem).
 *
 * Cooldowns (persisted, so a restart does not reset them), Audit D-07:
 *  - one valid kill per killer/victim PAIR (either direction, so two accounts cannot trade kills back and forth), and
 *  - one valid kill per VICTIM whoever the killer is (N alt killers can no longer each score the same victim), both per
 *    [REPEAT_VICTIM_COOLDOWN_MS];
 *  - at most [DAILY_VALID_KILL_CAP] valid kills per killer per rolling [DAY_MS].
 */
object ValidPkKill {
    const val MIN_RISK = 1_000_000L
    const val REPEAT_VICTIM_COOLDOWN_MS = 60L * 60L * 1000L

    /** Audit D-07: rolling window of the per-killer cap. */
    const val DAY_MS = 24L * 60L * 60L * 1000L

    /**
     * Audit D-07: valid kills one killer can score per rolling 24 hours. PROVISIONAL (not owner-tuned): enough for a
     * real PKer (every one of them needs a different victim risking 1M+), low enough that an alt farm cannot push an
     * emblem through all six tiers and bank the points repeatedly in a day.
     */
    const val DAILY_VALID_KILL_CAP = 15

    /** Audit D-07: the victim must have hit the killer within this window, otherwise the kill is a fed kill. */
    const val FED_KILL_WINDOW_MS = 5L * 60L * 1000L

    /** Audit D-07: value the killer handed the victim within this window does not count as the victim's risk. */
    const val GIFT_WINDOW_MS = DAY_MS

    enum class Verdict(val valid: Boolean, val abuse: Boolean, val reason: String) {
        VALID(true, false, "valid kill"),
        LOW_RISK(false, false, "the victim risked less than 1M"),
        REPEAT_VICTIM(false, false, "this player (or this pair) already counted as a kill within the hour"),
        DAILY_CAP(false, false, "you have reached today's limit of counted kills"),
        FED_KILL(false, false, "the victim never fought back"),
        SAME_ADDRESS(false, true, "both accounts play from the same address"),
        STAFF(false, true, "a staff account was involved"),
        SELF(false, true, "no other player was the killer"),
        NO_KILLER(false, true, "no player killer"),
    }

    class Result(val verdict: Verdict, val suspicious: List<String>)

    /** Where the emblem system keeps its state and log files (the server's `data` directory; tests point it elsewhere). */
    @Volatile var dataDir = File("data")

    private val stateFile get() = File(dataDir, "deadman_emblem_pairs.txt")
    private val dailyFile get() = File(dataDir, "deadman_pk_daily.txt")

    /** Pair keys ([pairKey]) and victim keys ([victimKey]) -> time of the last valid kill. */
    private val lastValidPair = ConcurrentHashMap<String, Long>()

    /** Audit D-07: lower-case killer name -> times of his valid kills inside the last [DAY_MS]. */
    private val validKillTimes = ConcurrentHashMap<String, MutableList<Long>>()

    /** Audit D-07: "giver>receiver" (lower case) -> (time, value) of what the giver handed the receiver. Session-only. */
    private val gifts = ConcurrentHashMap<String, MutableList<Pair<Long, Long>>>()

    @Volatile private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        val now = System.currentTimeMillis()
        if (stateFile.exists()) {
            stateFile.readLines().forEach { line ->
                val at = line.lastIndexOf('=')
                if (at <= 0) return@forEach
                val time = line.substring(at + 1).toLongOrNull() ?: return@forEach
                if (now - time < REPEAT_VICTIM_COOLDOWN_MS) lastValidPair[line.substring(0, at)] = time
            }
        }
        if (dailyFile.exists()) {
            dailyFile.readLines().forEach { line ->
                val at = line.lastIndexOf('=')
                if (at <= 0) return@forEach
                val times = line.substring(at + 1).split(',').mapNotNull { it.trim().toLongOrNull() }.filter { now - it < DAY_MS }
                if (times.isNotEmpty()) validKillTimes[line.substring(0, at)] = times.toMutableList()
            }
        }
    }

    private fun save() {
        val now = System.currentTimeMillis()
        lastValidPair.entries.removeIf { now - it.value >= REPEAT_VICTIM_COOLDOWN_MS }
        runCatching { AtomicFiles.writeText(stateFile, lastValidPair.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
        validKillTimes.values.forEach { times -> synchronized(times) { times.removeIf { now - it >= DAY_MS } } }
        validKillTimes.entries.removeIf { it.value.isEmpty() }
        runCatching {
            AtomicFiles.writeText(dailyFile, validKillTimes.entries.joinToString("\n") { e -> "${e.key}=${synchronized(e.value) { e.value.joinToString(",") }}" })
        }
    }

    fun pairKey(a: String, b: String): String {
        val x = a.lowercase()
        val y = b.lowercase()
        return if (x < y) "$x|$y" else "$y|$x"
    }

    /** Audit D-07: the per-victim cooldown key (usernames never contain ':', so it cannot clash with a [pairKey]). */
    fun victimKey(victim: String): String = "victim:${victim.lowercase()}"

    private fun address(p: Player): java.net.InetAddress? =
        ((p as? gg.rsmod.game.model.entity.Client)?.channel?.remoteAddress() as? java.net.InetSocketAddress)?.address

    /**
     * Audit D-07: the client's machine id ([gg.rsmod.game.model.entity.Client.uuid]), or null when unknown. A blank id is
     * "unknown", never a match: the 667 login decoder currently always sends "" (`LoginDecoder`), so the id only starts
     * to bite once the decoder reads a real one.
     */
    private fun machineId(p: Player): String? =
        (p as? gg.rsmod.game.model.entity.Client)?.let { runCatching { it.uuid }.getOrNull() }?.takeIf { it.isNotBlank() }

    /**
     * True when both players are the same person by connection: the same client machine id (Audit D-07: whatever the IP -
     * a VPN, a local proxy or loopback), or the same remote IP. Loopback without a machine id is the owner's local
     * two-client test and does not count as the same address.
     */
    fun sameAddress(a: Player, b: Player): Boolean {
        val idA = machineId(a)
        if (idA != null && idA == machineId(b)) return true
        val first = address(a) ?: return false
        if (first.isLoopbackAddress) return false
        return first == address(b)
    }

    fun isStaff(player: Player): Boolean = player.world.privileges.isEligible(player.privilege, Privilege.MOD_POWER)

    /**
     * Pure rule order, testable without players: abuse first (it decides whether anything may move), then the cooldowns
     * (pair or victim), the killer's daily cap, the risk, and finally the fed-kill rule.
     */
    fun judge(
        hasKiller: Boolean,
        self: Boolean,
        staff: Boolean,
        sameAddress: Boolean,
        pairOnCooldown: Boolean,
        risk: Long,
        dailyCapReached: Boolean = false,
        fedKill: Boolean = false,
    ): Verdict =
        when {
            !hasKiller -> Verdict.NO_KILLER
            self -> Verdict.SELF
            staff -> Verdict.STAFF
            sameAddress -> Verdict.SAME_ADDRESS
            pairOnCooldown -> Verdict.REPEAT_VICTIM
            dailyCapReached -> Verdict.DAILY_CAP
            risk < MIN_RISK -> Verdict.LOW_RISK
            fedKill -> Verdict.FED_KILL
            else -> Verdict.VALID
        }

    /**
     * Audit D-07: a fed kill - [victim] never landed damage on [killer] within [FED_KILL_WINDOW_MS] (a naked alt standing
     * still). A 0 hitsplat does not count as fighting back.
     */
    fun isFedKill(killer: Player, victim: Player, now: Long = System.currentTimeMillis()): Boolean {
        val stack = killer.damageMap[victim] ?: return true
        return stack.totalDamage <= 0 || now - stack.lastHit > FED_KILL_WINDOW_MS
    }

    /** Audit D-07: value (coins/guide price) [giver] handed [receiver] inside [GIFT_WINDOW_MS]. */
    fun giftedRecently(giver: String, receiver: String, now: Long = System.currentTimeMillis()): Long {
        val list = gifts[giftKey(giver, receiver)] ?: return 0L
        return synchronized(list) {
            list.removeIf { now - it.first >= GIFT_WINDOW_MS }
            list.sumOf { it.second }
        }
    }

    /**
     * Audit D-07: records that [giver] handed [receiver] items worth [value] (a completed trade). That value is subtracted
     * from the receiver's risk if [giver] kills him inside [GIFT_WINDOW_MS], so a PKer cannot fund the "1M risk" of his own
     * victims (e.g. the loot of the previous kill's key passed on to the next alt). Fed by [DeadmanTrade.onTradeCompleted].
     */
    fun noteGift(giver: String, receiver: String, value: Long, now: Long = System.currentTimeMillis()) {
        if (value <= 0 || giver.equals(receiver, ignoreCase = true)) return
        val list = gifts.getOrPut(giftKey(giver, receiver)) { ArrayList() }
        synchronized(list) {
            list.removeIf { now - it.first >= GIFT_WINDOW_MS }
            list += now to value
        }
    }

    private fun giftKey(giver: String, receiver: String) = "${giver.lowercase()}>${receiver.lowercase()}"

    private fun onCooldown(last: Long?, now: Long) = last != null && now - last < REPEAT_VICTIM_COOLDOWN_MS

    /** Audit D-07: valid kills [killer] scored within the last [DAY_MS]. */
    fun validKillsToday(killer: String, now: Long = System.currentTimeMillis()): Int {
        load()
        val list = validKillTimes[killer.lowercase()] ?: return 0
        return synchronized(list) { list.count { now - it < DAY_MS } }
    }

    fun evaluate(killer: Player?, victim: Player, risk: Long, now: Long = System.currentTimeMillis()): Result {
        load()
        val gifted = if (killer != null && killer !== victim) giftedRecently(killer.username, victim.username, now) else 0L
        val effectiveRisk = (risk - gifted).coerceAtLeast(0L)
        val fed = killer != null && killer !== victim && isFedKill(killer, victim, now)
        val verdict =
            judge(
                hasKiller = killer != null,
                self = killer === victim || (victim.username.isNotBlank() && killer?.username.equals(victim.username, ignoreCase = true) == true),
                staff = killer != null && (isStaff(killer) || isStaff(victim)),
                sameAddress = killer != null && sameAddress(killer, victim),
                pairOnCooldown =
                    killer != null &&
                        (onCooldown(lastValidPair[pairKey(killer.username, victim.username)], now) || onCooldown(lastValidPair[victimKey(victim.username)], now)),
                risk = effectiveRisk,
                dailyCapReached = killer != null && validKillsToday(killer.username, now) >= DAILY_VALID_KILL_CAP,
                fedKill = fed,
            )
        val suspicious = ArrayList<String>()
        if (killer != null && killer !== victim) {
            if (fed) suspicious += "victim dealt no damage to the killer"
            if (gifted > 0) suspicious += "killer handed the victim $gifted of the $risk risked"
            if (verdict == Verdict.REPEAT_VICTIM) suspicious += "repeat victim inside the cooldown"
            if (verdict == Verdict.DAILY_CAP) suspicious += "killer reached the daily valid-kill cap"
            if (verdict.abuse) suspicious += verdict.reason
        }
        return Result(verdict, suspicious)
    }

    /** Starts the pair and victim cooldowns and counts the killer's daily kill; call once for every kill judged [Verdict.VALID]. */
    fun record(killer: Player, victim: Player, now: Long = System.currentTimeMillis()) {
        load()
        lastValidPair[pairKey(killer.username, victim.username)] = now
        lastValidPair[victimKey(victim.username)] = now
        val list = validKillTimes.getOrPut(killer.username.lowercase()) { ArrayList() }
        synchronized(list) { list += now }
        save()
    }
}
