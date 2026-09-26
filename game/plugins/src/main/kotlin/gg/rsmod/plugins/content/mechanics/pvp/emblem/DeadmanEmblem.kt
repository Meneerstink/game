package gg.rsmod.plugins.content.mechanics.pvp.emblem

import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud
import gg.rsmod.plugins.content.mechanics.pvp.ValidPkKill
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue
import gg.rsmod.util.io.AtomicFiles
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Deadman emblems (owner 2026-09-25) - the one place for tiers, values, PvM drops, PK upgrades, the PvP death transfer
 * and cash-out. The server is the source of truth; the items (23864-23869, `DeadmanEmblemTool`) only mirror it.
 *
 * Loop: a PvM kill of an NPC with 150+ HP can give a tier 1 emblem -> every valid PK kill ([ValidPkKill]) while the emblem
 * is in the killer's inventory raises it exactly one tier (no RNG) -> the owner cashes it in at the Emblem Trader
 * (Deadman Points or a tier-scaled XP lamp) or keeps risking it -> on a PvP death the emblem is always lost and the killer
 * receives the exact tier.
 *
 * Owner decisions (2026-09-25): a player owns at most one emblem (inventory + bank + pending delivery); when a second one
 * would arrive, the higher tier is kept and the lower one is cashed in for its Deadman Points automatically. A won emblem
 * goes straight to the killer's inventory, else his bank, else it is cashed in - it is never left on the floor. A death
 * that is not a valid kill still hands the emblem over without an upgrade, except for abuse (same address, staff, no real
 * killer), where the emblem is destroyed. Values below are the owner-approved table.
 */
object DeadmanEmblem {
    const val MAX_TIER = 6
    const val FIRST_EMBLEM = 23864
    const val FIRST_LAMP = 23870

    /** Cash-out value per tier: Deadman Points (Deadman Store currency) and XP-lamp experience. */
    val POINTS = intArrayOf(60, 110, 200, 360, 650, 1_150)
    val LAMP_XP = intArrayOf(25_000, 45_000, 80_000, 145_000, 260_000, 470_000)

    /** Only NPCs with at least 150 hitpoints (1,500 lifepoints) can drop an emblem. */
    const val MIN_NPC_LIFEPOINTS = 1_500

    /**
     * Drop balance: about one emblem per 3-4 hours of serious PvM. A kill's chance scales with the NPC's hitpoints (time
     * to kill scales with them too): one emblem per ~40,000 hitpoints killed, i.e. 1/267 for a 150 HP NPC, 1/167 for a
     * 240 HP King Black Dragon; capped at 1/20 per kill for the largest bosses. Wilderness NPCs +10 % (relative).
     */
    const val HITPOINTS_PER_EMBLEM = 40_000.0
    const val MAX_CHANCE = 1.0 / 20
    const val WILDERNESS_BONUS = 1.10

    val EMBLEM_IDS = IntArray(MAX_TIER) { FIRST_EMBLEM + it }
    val LAMP_IDS = IntArray(MAX_TIER) { FIRST_LAMP + it }

    fun isEmblem(itemId: Int): Boolean = itemId in FIRST_EMBLEM until FIRST_EMBLEM + MAX_TIER

    fun isLamp(itemId: Int): Boolean = itemId in FIRST_LAMP until FIRST_LAMP + MAX_TIER

    /** 1..6 for an emblem item, 0 otherwise. */
    fun tierOf(itemId: Int): Int = if (isEmblem(itemId)) itemId - FIRST_EMBLEM + 1 else 0

    fun emblemId(tier: Int): Int = EMBLEM_IDS[tier - 1]

    fun lampId(tier: Int): Int = LAMP_IDS[tier - 1]

    fun points(tier: Int): Int = POINTS[tier - 1]

    fun lampXp(tier: Int): Int = LAMP_XP[tier - 1]

    // ------------------------------------------------------------------ ownership

    /** One physical emblem the player owns: its container and slot. */
    class Held(val container: ItemContainer, val slot: Int, val tier: Int, val inBank: Boolean)

    fun holdings(player: Player): List<Held> {
        val out = ArrayList<Held>()
        fun scan(container: ItemContainer, bank: Boolean) {
            for (slot in 0 until container.capacity) {
                val item = container[slot] ?: continue
                if (item.amount > 0 && isEmblem(item.id)) out += Held(container, slot, tierOf(item.id), bank)
            }
        }
        scan(player.inventory, false)
        scan(player.bank, true)
        // Owner 2026-09-26: a PvM death sends the emblem to the gravestone, and from there to Death's Office. It is still owned
        // there, so the one-emblem rule sees it (stored like a banked one) and a reclaim goes through [receive].
        scan(player.gravestone, true)
        scan(player.deathRecovery, true)
        return out
    }

    /** Highest tier the player owns anywhere (inventory, bank, pending delivery), 0 for none. */
    fun ownedTier(player: Player): Int = maxOf(holdings(player).maxOfOrNull { it.tier } ?: 0, Ledger.pendingFor(player.username).maxOrNull() ?: 0)

    /** Tier of the emblem in the inventory (the one at risk and able to upgrade), 0 for none. */
    fun carriedTier(player: Player): Int {
        var best = 0
        for (item in player.inventory.rawItems) if (item != null && item.amount > 0) best = maxOf(best, tierOf(item.id))
        return best
    }

    // ------------------------------------------------------------------ receiving (the single entry for every new emblem)

    enum class Source { DROP, PVP_TRANSFER, ADMIN_CREATE, PENDING, RECLAIM }

    /**
     * Gives [player] an emblem of [tier] under the one-emblem rule. Keeps the best emblem, cashes the other in for Deadman
     * Points, places a new emblem in the inventory, else the bank, else cashes it in. Never creates a second emblem and never
     * deletes an earned one without paying it out. Returns the tier the player owns afterwards.
     */
    fun receive(player: Player, tier: Int, source: Source, context: String = ""): Int {
        require(tier in 1..MAX_TIER)
        val held = holdings(player).sortedByDescending { it.tier }
        val best = held.firstOrNull()
        // Integrity: more than one emblem already (should never happen) - keep the best, cash the rest.
        held.drop(1).forEach { extra -> cashAway(player, extra, "duplicate found while receiving") }
        if (best != null && best.tier >= tier) {
            payPoints(player, tier)
            player.message("<col=ef1020>You already own a tier ${best.tier} Deadman emblem, so the tier $tier emblem was cashed in for ${points(tier)} Deadman Points.")
            EmblemLog.write("CASHOUT", player, "tierBefore" to tier, "tierAfter" to 0, "reward" to "${points(tier)} points", "reason" to "auto: owns tier ${best.tier}", "source" to source.name, "context" to context)
            return best.tier
        }
        if (best != null) {
            // The incoming emblem is better: the old one is cashed in. A carried one is replaced in its own slot; a banked
            // one leaves the bank and the new emblem is placed like a first emblem (inventory first).
            payPoints(player, best.tier)
            best.container[best.slot] = if (best.inBank) null else Item(emblemId(tier), 1)
            player.message("<col=ef1020>Your tier ${best.tier} Deadman emblem was cashed in for ${points(best.tier)} Deadman Points to make room for the tier $tier emblem.")
            EmblemLog.write("CASHOUT", player, "tierBefore" to best.tier, "tierAfter" to 0, "reward" to "${points(best.tier)} points", "reason" to "auto: replaced by tier $tier", "source" to source.name, "context" to context)
            if (!best.inBank) return tier
        }
        when {
            player.inventory.add(emblemId(tier), 1).hasSucceeded() -> Unit
            player.bank.add(emblemId(tier), 1).hasSucceeded() -> player.message("<col=ef1020>Your inventory is full, so the tier $tier Deadman emblem was sent to your bank.")
            else -> {
                payPoints(player, tier)
                player.message("<col=ef1020>Your inventory and bank are full, so the tier $tier Deadman emblem was cashed in for ${points(tier)} Deadman Points.")
                EmblemLog.write("CASHOUT", player, "tierBefore" to tier, "tierAfter" to 0, "reward" to "${points(tier)} points", "reason" to "auto: no space", "source" to source.name, "context" to context)
                return 0
            }
        }
        return tier
    }

    private fun cashAway(player: Player, held: Held, reason: String) {
        held.container[held.slot] = null
        payPoints(player, held.tier)
        EmblemLog.write("CASHOUT", player, "tierBefore" to held.tier, "tierAfter" to 0, "reward" to "${points(held.tier)} points", "reason" to "auto: $reason")
    }

    private fun payPoints(player: Player, tier: Int) {
        val attr = StoreCatalogue.Currency.DEADMAN.attr
        player.attr[attr] = (player.attr[attr] ?: 0) + points(tier)
    }

    // ------------------------------------------------------------------ PvM

    fun dropChance(lifepoints: Int, wilderness: Boolean): Double {
        if (lifepoints < MIN_NPC_LIFEPOINTS) return 0.0
        val base = (lifepoints / 10.0 / HITPOINTS_PER_EMBLEM).coerceAtMost(MAX_CHANCE)
        return if (wilderness) base * WILDERNESS_BONUS else base
    }

    /** Rolls a tier 1 emblem for [killer] (the NPC's top damage dealer). Only players without an emblem can roll. */
    fun onNpcKilled(world: World, killer: Player, npc: Npc) {
        val wilderness = npc.tile.getWildernessLevel() > 0
        val chance = dropChance(npc.combatDef.lifepoints, wilderness)
        if (chance <= 0.0 || ownedTier(killer) > 0) return
        if (world.randomDouble() >= chance) return
        val placed = receive(killer, 1, Source.DROP, "npc ${npc.id}")
        if (placed == 0) return
        killer.message("<col=ef1020><shad=000000>A Deadman emblem (tier 1) has dropped for you!</shad></col>")
        killer.playSound(Sfx.ENCHANT_ONYX_RING)
        EmblemLog.write("DROP", killer, "tierBefore" to 0, "tierAfter" to 1, "npc" to "${npc.id} ${npc.def.name} lp=${npc.combatDef.lifepoints}", "chance" to "%.6f".format(chance), "area" to area(killer))
    }

    // ------------------------------------------------------------------ PvP

    /**
     * Real value of [items] for the 1M risk rule: coins at face value, everything else at the same Grand Exchange guide
     * price the death pipeline ranks kept items by ([gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider]).
     * An item that cannot be priced counts 0 - a pricing problem may never break a death.
     */
    fun riskValue(world: World, items: List<Item>): Long {
        val prices = gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider(world)
        return items.sumOf { item ->
            if (item.id == gg.rsmod.plugins.api.cfg.Items.COINS_995) {
                item.amount.toLong()
            } else {
                runCatching { prices.getValue(item.id) }.getOrDefault(0L).coerceAtLeast(0L) * item.amount
            }
        }
    }

    /**
     * Called exactly once per PvP death from `DeathExecutor` (after its exactly-once guard), with the tiers of the emblems
     * removed from the victim ([lostTiers]; the removal itself is part of the death's item transfer) and the real value the
     * victim lost to the killer ([risk], excluding the emblem).
     *
     * This is the single place a PvP death is judged: the one [ValidPkKill.evaluate] of the death also drives the
     * killstreak / Deadman Points reward (Audit D-08, [gg.rsmod.plugins.content.mechanics.pvp.Killstreaks.onJudgedKill])
     * and the post-kill grace (Audit D-06, [gg.rsmod.plugins.content.mechanics.pvp.KillGrace.grantForKill]), so all three
     * see the same verdict before [ValidPkKill.record] starts the cooldowns. Returns that result.
     */
    fun onPvpDeath(world: World, victim: Player, killer: Player?, lostTiers: List<Int>, risk: Long): ValidPkKill.Result {
        val result = ValidPkKill.evaluate(killer, victim, risk)
        val verdict = result.verdict
        val victimTier = lostTiers.maxOrNull() ?: 0
        if (result.suspicious.isNotEmpty()) {
            EmblemLog.write("SUSPICIOUS", victim, "killer" to (killer?.username ?: "-"), "victim" to victim.username, "risk" to risk, "verdict" to verdict.name, "notes" to result.suspicious.joinToString("; "), "area" to area(victim))
        }
        if (killer != null) {
            gg.rsmod.plugins.content.mechanics.pvp.Killstreaks.onJudgedKill(killer, victim, verdict)
            gg.rsmod.plugins.content.mechanics.pvp.KillGrace.grantForKill(killer, victim, verdict, victim.tile.isMulti(world))
        }
        lostTiers.sortedDescending().drop(1).forEach { extra ->
            EmblemLog.write("DESTROY", victim, "tierBefore" to extra, "tierAfter" to 0, "reason" to "second emblem on a PvP death (integrity)")
        }
        if (killer == null || verdict.abuse) {
            if (victimTier > 0) {
                victim.message("<col=ef1020>Your tier $victimTier Deadman emblem crumbles to dust.")
                EmblemLog.write("DESTROY", victim, "tierBefore" to victimTier, "tierAfter" to 0, "killer" to (killer?.username ?: "-"), "victim" to victim.username, "risk" to risk, "reason" to verdict.reason, "area" to area(victim))
            }
            return result
        }
        // 1) The kill itself: a valid kill raises the emblem the killer carries by exactly one tier.
        if (verdict.valid) {
            ValidPkKill.record(killer, victim)
            upgradeCarried(world, killer, victim, risk)
        } else if (carriedTier(killer) > 0) {
            killer.message("<col=ef1020>This kill did not empower your emblem: ${verdict.reason}.")
        }
        // 2) The victim's emblem goes to the killer at its exact tier.
        if (victimTier > 0) {
            victim.message("<col=ef1020>You have lost your tier $victimTier Deadman emblem to ${killer.username}.")
            val context = "from ${victim.username} risk=$risk verdict=${verdict.name}"
            if (killer.isOnline && !killer.isDead()) {
                val after = receive(killer, victimTier, Source.PVP_TRANSFER, context)
                if (after == victimTier) killer.message("<col=ef1020><shad=000000>You have claimed ${victim.username}'s tier $victimTier Deadman emblem!</shad></col>")
                killer.playSound(Sfx.ENCHANT_DRAGON_RING)
            } else {
                // Offline or dying the same tick: delivered on his next login / respawn, never dropped or lost.
                Ledger.addPending(killer.username, victimTier)
                if (killer.isOnline) {
                    world.queue {
                        repeat(100) {
                            wait(5)
                            if (!killer.isOnline) return@queue
                            if (!killer.isDead()) {
                                deliverPending(killer)
                                return@queue
                            }
                        }
                    }
                }
            }
            EmblemLog.write("PVP_TRANSFER", killer, "tierBefore" to victimTier, "tierAfter" to victimTier, "killer" to killer.username, "victim" to victim.username, "risk" to risk, "verdict" to verdict.name, "area" to area(victim))
        }
        return result
    }

    private fun upgradeCarried(world: World, killer: Player, victim: Player, risk: Long) {
        val held = holdings(killer).filter { !it.inBank }.maxByOrNull { it.tier } ?: return
        if (held.tier >= MAX_TIER) {
            killer.message("<col=ffb000>Your Deadman emblem is already at its peak.")
            return
        }
        val next = held.tier + 1
        held.container[held.slot] = Item(emblemId(next), 1)
        EmblemLog.write("UPGRADE", killer, "tierBefore" to held.tier, "tierAfter" to next, "killer" to killer.username, "victim" to victim.username, "risk" to risk, "area" to area(victim))
        announceUpgrade(world, killer, next)
    }

    /** Chat line, sound and visual per tier; tier 5 and 6 are broadcast, tier 6 with the strongest treatment. */
    fun announceUpgrade(world: World, player: Player, tier: Int) {
        player.message("<col=ef1020><shad=000000>Your Deadman emblem grows stronger: tier $tier (worth ${points(tier)} Deadman Points).</shad></col>")
        when (tier) {
            MAX_TIER -> {
                player.graphic(Gfx.LEVEL_UP_FIREWORKS, 100)
                player.playSound(Sfx.ENCHANT_ONYX_AMULET)
                val line = "<col=ffc84a><shad=6b0000>[Deadman] ${player.username} has forged a TIER 6 Deadman emblem! Hunt them down.</shad></col>"
                world.players.forEach { other ->
                    other.message(line)
                    other.playSound(Sfx.BELL_PEAL)
                }
            }
            5 -> {
                player.graphic(Gfx.FLAMES_OF_ZAMORAK_IMPACT, 0)
                player.playSound(Sfx.ENCHANT_ONYX_AMULET)
                val line = "<col=ff3030><shad=000000>[Deadman] ${player.username} has forged a tier 5 Deadman emblem.</shad></col>"
                world.players.forEach { it.message(line) }
            }
            else -> player.playSound(Sfx.ENCHANT_DRAGON_RING)
        }
    }

    /** Delivers pending emblems (won while offline or dying) - on login and after respawn. */
    fun deliverPending(player: Player) {
        if (!player.isOnline || player.isDead()) return
        val tiers = Ledger.takePending(player.username)
        tiers.forEach { tier ->
            receive(player, tier, Source.PENDING)
            player.message("<col=ef1020>You receive the tier $tier Deadman emblem you won.")
        }
    }

    // ------------------------------------------------------------------ cash-out / destroy / admin

    enum class CashOut { POINTS, LAMP }

    /** Cashes the carried emblem in. Returns false (with a message) when nothing could be done. */
    fun cashOut(player: Player, how: CashOut): Boolean {
        val held = holdings(player).sortedWith(compareBy<Held> { it.inBank }.thenByDescending { it.tier }).firstOrNull() ?: run {
            player.message("You don't have a Deadman emblem to cash in.")
            return false
        }
        val tier = held.tier
        when (how) {
            CashOut.POINTS -> {
                held.container[held.slot] = null
                payPoints(player, tier)
                player.message("<col=ef1020>You cash in your tier $tier Deadman emblem for ${points(tier)} Deadman Points.")
                EmblemLog.write("CASHOUT", player, "tierBefore" to tier, "tierAfter" to 0, "reward" to "${points(tier)} points")
            }
            CashOut.LAMP -> {
                // The lamp takes the emblem's own slot, so a full inventory never blocks the exchange.
                held.container[held.slot] = Item(lampId(tier), 1)
                player.message("<col=ef1020>You cash in your tier $tier Deadman emblem for a Deadman lamp (${fmt(lampXp(tier))} XP).")
                EmblemLog.write("CASHOUT", player, "tierBefore" to tier, "tierAfter" to 0, "reward" to "lamp ${lampXp(tier)} xp")
            }
        }
        player.playSound(Sfx.ENCHANT_DRAGON_AMULET)
        return true
    }

    fun destroyed(player: Player, tier: Int) {
        EmblemLog.write("DESTROY", player, "tierBefore" to tier, "tierAfter" to 0, "reason" to "destroyed by the player", "area" to area(player))
    }

    /** The admin item-spawn commands route emblems here, so they obey the one-emblem rule and are logged. True = handled. */
    fun adminSpawn(admin: Player, itemId: Int): Boolean {
        val tier = tierOf(itemId)
        if (tier == 0) return false
        val after = adminCreate(admin, admin, tier)
        admin.message("Deadman emblem: you now own tier $after (spawn logged as ADMIN_CREATE).")
        return true
    }

    fun adminCreate(admin: Player, target: Player, tier: Int): Int {
        val after = receive(target, tier, Source.ADMIN_CREATE, "by ${admin.username}")
        EmblemLog.write("ADMIN_CREATE", target, "tierBefore" to 0, "tierAfter" to tier, "admin" to admin.username, "result" to after)
        return after
    }

    // ------------------------------------------------------------------ HUD / info

    /** Third field of the Deadman HUD timer text: "tier:points:nextPoints" for the carried emblem, "" without one. */
    fun hudField(player: Player): String {
        val tier = carriedTier(player)
        if (tier == 0) return ""
        val next = if (tier < MAX_TIER) points(tier + 1) else 0
        return "$tier:${points(tier)}:$next"
    }

    fun area(player: Player): String {
        val t = player.tile
        return "${t.x},${t.z},${t.height} ${DeadmanHud.zoneLabel(player)}"
    }

    // ------------------------------------------------------------------ persistence

    /** Emblems owed to players who were offline or dying when they won them; survives restarts. */
    object Ledger {
        private val file get() = File(ValidPkKill.dataDir, "deadman_emblem_pending.txt")
        private val pending = ConcurrentHashMap<String, MutableList<Int>>()

        @Volatile private var loaded = false

        @Synchronized private fun load() {
            if (loaded) return
            loaded = true
            if (!file.exists()) return
            file.readLines().forEach { line ->
                val at = line.indexOf('=')
                if (at <= 0) return@forEach
                val tiers = line.substring(at + 1).split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..MAX_TIER }
                if (tiers.isNotEmpty()) pending[line.substring(0, at).lowercase()] = tiers.toMutableList()
            }
        }

        @Synchronized private fun save() {
            runCatching { AtomicFiles.writeText(file, pending.entries.filter { it.value.isNotEmpty() }.joinToString("\n") { "${it.key}=${it.value.joinToString(",")}" }) }
        }

        @Synchronized fun addPending(name: String, tier: Int) {
            load()
            pending.getOrPut(name.lowercase()) { mutableListOf() } += tier
            save()
        }

        @Synchronized fun pendingFor(name: String): List<Int> {
            load()
            return pending[name.lowercase()]?.toList() ?: emptyList()
        }

        @Synchronized fun takePending(name: String): List<Int> {
            load()
            val tiers = pending.remove(name.lowercase()) ?: return emptyList()
            save()
            return tiers
        }
    }
}

/** Thousands with commas ("1,150"), independent of the host's locale. */
fun fmt(value: Int): String = String.format(java.util.Locale.US, "%,d", value)

/** Append-only audit log of every emblem event (`data/logs/deadman_emblem.log`). */
object EmblemLog : mu.KLogging() {
    private val file get() = File(ValidPkKill.dataDir, "logs/deadman_emblem.log")

    @Synchronized
    fun write(type: String, player: Player, vararg fields: Pair<String, Any?>) {
        val line = buildString {
            append(Instant.now()).append(" | ").append(type).append(" | player=").append(player.username)
            fields.forEach { (k, v) -> append(" | ").append(k).append('=').append(v) }
        }
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(line + "\n")
        }
        logger.info("[emblem] {}", line)
    }
}
