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
 *  - [Verdict.VALID]: counts (emblem upgrade).
 *  - blocked kills that still are real deaths ([Verdict.LOW_RISK], [Verdict.REPEAT_VICTIM]): nothing upgrades.
 *  - abuse ([Verdict.SAME_ADDRESS], [Verdict.STAFF], [Verdict.SELF], [Verdict.NO_KILLER]): nothing may move between the two
 *    accounts at all (owner: same-IP/alt and admin kills destroy the emblem).
 *
 * Repeat-victim cooldown: one valid kill per killer/victim PAIR (either direction, so two accounts cannot trade kills back
 * and forth) per [REPEAT_VICTIM_COOLDOWN_MS]. Persisted, so a restart does not reset it.
 */
object ValidPkKill {
    const val MIN_RISK = 1_000_000L
    const val REPEAT_VICTIM_COOLDOWN_MS = 60L * 60L * 1000L

    enum class Verdict(val valid: Boolean, val abuse: Boolean, val reason: String) {
        VALID(true, false, "valid kill"),
        LOW_RISK(false, false, "the victim risked less than 1M"),
        REPEAT_VICTIM(false, false, "this pair already scored a kill within the hour"),
        SAME_ADDRESS(false, true, "both accounts play from the same address"),
        STAFF(false, true, "a staff account was involved"),
        SELF(false, true, "no other player was the killer"),
        NO_KILLER(false, true, "no player killer"),
    }

    class Result(val verdict: Verdict, val suspicious: List<String>)

    /** Where the emblem system keeps its state and log files (the server's `data` directory; tests point it elsewhere). */
    @Volatile var dataDir = File("data")

    private val stateFile get() = File(dataDir, "deadman_emblem_pairs.txt")
    private val lastValidPair = ConcurrentHashMap<String, Long>()

    @Volatile private var loaded = false

    private fun load() {
        if (loaded) return
        loaded = true
        if (!stateFile.exists()) return
        val now = System.currentTimeMillis()
        stateFile.readLines().forEach { line ->
            val at = line.lastIndexOf('=')
            if (at <= 0) return@forEach
            val time = line.substring(at + 1).toLongOrNull() ?: return@forEach
            if (now - time < REPEAT_VICTIM_COOLDOWN_MS) lastValidPair[line.substring(0, at)] = time
        }
    }

    private fun save() {
        val now = System.currentTimeMillis()
        lastValidPair.entries.removeIf { now - it.value >= REPEAT_VICTIM_COOLDOWN_MS }
        runCatching { AtomicFiles.writeText(stateFile, lastValidPair.entries.joinToString("\n") { "${it.key}=${it.value}" }) }
    }

    fun pairKey(a: String, b: String): String {
        val x = a.lowercase()
        val y = b.lowercase()
        return if (x < y) "$x|$y" else "$y|$x"
    }

    /** True when both players are connected from the same IP address; loopback is the owner's local two-client test. */
    fun sameAddress(a: Player, b: Player): Boolean {
        fun address(p: Player): java.net.InetAddress? =
            ((p as? gg.rsmod.game.model.entity.Client)?.channel?.remoteAddress() as? java.net.InetSocketAddress)?.address
        val first = address(a) ?: return false
        if (first.isLoopbackAddress) return false
        return first == address(b)
    }

    fun isStaff(player: Player): Boolean = player.world.privileges.isEligible(player.privilege, Privilege.MOD_POWER)

    /**
     * Pure rule order, testable without players: abuse first (it decides whether anything may move), then the pair
     * cooldown, then the risk.
     */
    fun judge(
        hasKiller: Boolean,
        self: Boolean,
        staff: Boolean,
        sameAddress: Boolean,
        pairOnCooldown: Boolean,
        risk: Long,
    ): Verdict =
        when {
            !hasKiller -> Verdict.NO_KILLER
            self -> Verdict.SELF
            staff -> Verdict.STAFF
            sameAddress -> Verdict.SAME_ADDRESS
            pairOnCooldown -> Verdict.REPEAT_VICTIM
            risk < MIN_RISK -> Verdict.LOW_RISK
            else -> Verdict.VALID
        }

    fun evaluate(killer: Player?, victim: Player, risk: Long, now: Long = System.currentTimeMillis()): Result {
        load()
        val last = killer?.let { lastValidPair[pairKey(it.username, victim.username)] }
        val verdict =
            judge(
                hasKiller = killer != null,
                self = killer === victim || (victim.username.isNotBlank() && killer?.username.equals(victim.username, ignoreCase = true) == true),
                staff = killer != null && (isStaff(killer) || isStaff(victim)),
                sameAddress = killer != null && sameAddress(killer, victim),
                pairOnCooldown = last != null && now - last < REPEAT_VICTIM_COOLDOWN_MS,
                risk = risk,
            )
        val suspicious = ArrayList<String>()
        if (killer != null && killer !== victim) {
            // Fed kills: the victim never hit back. Logged, not blocked - a clean one-sided kill is legitimate.
            if (victim.damageMap.getDamageFrom(killer) > 0 && killer.damageMap.getDamageFrom(victim) == 0) {
                suspicious += "victim dealt no damage to the killer"
            }
            if (verdict == Verdict.REPEAT_VICTIM) suspicious += "repeat victim inside the cooldown"
            if (verdict.abuse) suspicious += verdict.reason
        }
        return Result(verdict, suspicious)
    }

    /** Starts the pair cooldown; call once for every kill judged [Verdict.VALID]. */
    fun record(killer: Player, victim: Player, now: Long = System.currentTimeMillis()) {
        load()
        lastValidPair[pairKey(killer.username, victim.username)] = now
        save()
    }
}
