package gg.rsmod.plugins.content.mechanics.pvp.chest

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.RiskSkull
import gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.util.Random

/**
 * The Deadman's chest rules (owner 2026-10-17, dossier N101; OSRS wiki "Deadman's chest (scenery)"). Everything here is pure or takes
 * plain [Facts] so the rules are unit-tested without a world; the plugin glue (object option, npc-killed hook, scheduler) reads the
 * facts from the real [Player] / [Npc] through [factsOf] / [killFactsOf] - the SAME predicate at the start and at the end of a channel.
 */
object DeadmanChest {
    const val MSG_INSTANCE = "You can't open the chest while you are in an instance."
    const val MSG_RISK = "You need to be risking at least 100,000 coins to open the chest."
    const val MSG_PROTECTED = "You can't open the chest while you have PvP protection or immunity."
    const val MSG_ON_CHEST = "You can't open the chest while standing on it."
    const val MSG_TOO_FAR = "You need to stand next to the chest to open it."
    const val MSG_LOOTED = "Someone else has already looted the chest."
    const val MSG_INTERRUPTED = "Your attempt to open the chest was interrupted."

    /** What the opener's situation looks like to the requirement check (start AND completion). */
    data class Facts(
        val inInstance: Boolean,
        val risk: Long,
        val beginnerProtected: Boolean,
        val killGrace: Boolean,
        val onChestTile: Boolean = false,
        val adjacent: Boolean = true,
    )

    /** The refusal message for [facts], or null when the player may (keep) open(ing) the chest. */
    fun refusal(facts: Facts): String? =
        when {
            facts.inInstance -> MSG_INSTANCE
            facts.beginnerProtected || facts.killGrace -> MSG_PROTECTED
            facts.risk < DeadmanChestConfig.MIN_RISK -> MSG_RISK
            facts.onChestTile -> MSG_ON_CHEST
            !facts.adjacent -> MSG_TOO_FAR
            else -> null
        }

    /**
     * Facts of [player] for the cache-sized chest on [chestTile]: risk is [RiskSkull.calculateRiskedValue] (the value the
     * skull shows = DeathRules.pvpLoss), protection is BeginnerProtection + KillGrace (our 1-minute post-kill immunity).
     */
    fun factsOf(
        player: Player,
        chestTile: Tile,
    ): Facts =
        Facts(
            inInstance = player.world.instanceAllocator.getMap(player.tile) != null,
            risk = riskOf(player),
            beginnerProtected = BeginnerProtection.isProtected(player),
            killGrace = KillGrace.isProtected(player),
            onChestTile = DeadmanChestWorld.inside(player.world, chestTile, player.tile),
            adjacent = DeadmanChestWorld.adjacent(player.world, chestTile, player.tile),
        )

    /** The value the skull shows ([RiskSkull.calculateRiskedValue]); a seam so the engine tests can run without the GE guide prices. */
    @Volatile
    var riskOf: (Player) -> Long = { RiskSkull.calculateRiskedValue(it) }

    /** Ticks to open: 100 (60 s) in singles, 50 (30 s) in multi, decided by the CHEST tile. */
    fun channelTicks(
        world: gg.rsmod.game.model.World,
        chestTile: Tile,
    ): Int = if (chestTile.isMulti(world)) DeadmanChestConfig.OPEN_TICKS_MULTI else DeadmanChestConfig.OPEN_TICKS_SINGLES

    // ---------------------------------------------------------------------------------------------------------------- kill chests

    /** Plain facts of one npc kill (dossier gap 1, exclusions a-f). */
    data class KillFacts(
        val lifepoints: Int,
        val breachSummonOrTest: Boolean,
        val chestGuardian: Boolean,
        val petOrFamiliar: Boolean,
        val inInstance: Boolean,
        val killerInMinigame: Boolean,
        val dangerous: Boolean,
        val respawnsOrBreach: Boolean,
    )

    /** One predicate for "this kill may roll a chest"; each line has its own unit test. */
    fun killCounts(f: KillFacts): Boolean =
        f.lifepoints >= DeadmanChestConfig.MIN_LIFEPOINTS &&
            !f.breachSummonOrTest &&
            !f.chestGuardian &&
            !f.petOrFamiliar &&
            !f.inInstance &&
            !f.killerInMinigame &&
            f.dangerous &&
            f.respawnsOrBreach

    /** Set on the Zemouregal that spawns with a chest (his kills never roll a chest and he is not a breach npc). */
    val GUARDIAN_ATTR = gg.rsmod.game.model.attr.AttributeKey<Boolean>()

    fun killFactsOf(
        killer: Player,
        npc: Npc,
    ): KillFacts {
        val world = npc.world
        val breach = DeadmanBreach.isBreachNpc(npc)
        return KillFacts(
            lifepoints = npc.combatDef.lifepoints,
            breachSummonOrTest = DeadmanBreach.isTestNpc(npc) || (breach && gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters.BY_ID[npc.id]?.summon == true),
            chestGuardian = npc.attr[GUARDIAN_ATTR] == true,
            petOrFamiliar = Pets.isPetNpc(npc.id) || world.players.any { Familiar.current(it) === npc || Pets.current(it) === npc },
            inInstance = world.instanceAllocator.getMap(npc.tile) != null,
            killerInMinigame = DeadmanTimerGate.inMinigame(killer),
            dangerous = AreaState.isDangerous(npc.tile),
            respawnsOrBreach = npc.respawns || breach,
        )
    }

    /**
     * Anti-farm limiter for kill chests (PROVISIONAL numbers in [DeadmanChestConfig]): one chest per account / address key per
     * cooldown, one active chest per killer, a world-wide cap, and never within [DeadmanChestConfig.MIN_CHEST_DISTANCE] tiles of another.
     * [keys] are the account AND the address key (alts on one IP share the cooldown).
     */
    class KillLimiter {
        private val lastSpawn = HashMap<String, Int>()
        private val active = ArrayList<Pair<String, Tile>>()

        fun allowed(
            keys: Collection<String>,
            killer: String,
            tile: Tile,
            now: Int,
        ): Boolean =
            keys.none { (lastSpawn[it] ?: -DeadmanChestConfig.KILL_COOLDOWN_TICKS) + DeadmanChestConfig.KILL_COOLDOWN_TICKS > now } &&
                active.none { it.first == killer } &&
                active.size < DeadmanChestConfig.MAX_KILL_CHESTS &&
                active.none { it.second.height == tile.height && it.second.getDistance(tile) < DeadmanChestConfig.MIN_CHEST_DISTANCE }

        fun spawned(
            keys: Collection<String>,
            killer: String,
            tile: Tile,
            now: Int,
        ) {
            keys.forEach { lastSpawn[it] = now }
            active += killer to tile
        }

        fun removed(tile: Tile) {
            active.removeAll { it.second == tile }
        }

        fun activeCount(): Int = active.size

        fun clear() {
            lastSpawn.clear()
            active.clear()
        }
    }

    // ---------------------------------------------------------------------------------------------------------------- channel

    /**
     * One chest in the world. [looted] is set BEFORE the reward is made, in the same step that checks it, so two completions on one tick
     * give exactly one reward (dupe guard).
     */
    class State(
        val tile: Tile,
        val timed: Boolean,
        val expiresAt: Int,
    ) {
        var looted = false
            private set
        var opener: String? = null
            private set

        /** True for the single first completer. */
        fun tryLoot(by: String): Boolean {
            if (looted) return false
            looted = true
            opener = by
            return true
        }
    }

    /** One player's open attempt: counts ticks, resets to nothing on any interrupt (he must click again). */
    class Channel(
        val needed: Int,
    ) {
        var elapsed = 0
            private set
        var stopped = false
            private set

        /** Returns true on the tick the channel completes. A refusal / interrupt stops it for good. */
        fun tick(
            interrupted: Boolean,
            facts: Facts,
        ): Result {
            if (stopped) return Result.STOPPED
            if (interrupted || refusal(facts) != null) {
                stopped = true
                elapsed = 0
                return Result.STOPPED
            }
            elapsed++
            return if (elapsed >= needed) Result.DONE else Result.RUNNING
        }

        enum class Result { RUNNING, DONE, STOPPED }
    }

    // ---------------------------------------------------------------------------------------------------------------- reward

    /**
     * Gives the roll to [player]. Items: inventory, else a ground item for the opener only (never deleted); javelin/axe stay one
     * stack of 100. Pets go through [Pets.award] order (follower / backpack / bank) but a pet that would be LOST becomes a ground item.
     */
    fun giveReward(
        player: Player,
        random: Random = Random(),
        scrollRandom: Random = Random(),
    ): DeadmanChestLoot.Reward {
        val reward = DeadmanChestLoot.roll(random, ownsPet = { Pets.owns(player, it) })
        // N104: the chest's count first, so the Collection Log records the item at this chest's number.
        gg.rsmod.plugins.content.quests.tab.KillCounts.chestOpened(player)
        runCatching { gg.rsmod.plugins.content.quests.tab.DeadmanTasks.onChestOpened(player) }
        when (reward) {
            is DeadmanChestLoot.Reward.Item -> {
                addOrDrop(player, reward.item, reward.amount)
                runCatching { gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, "chest", listOf(reward.item to reward.amount)) }
            }
            is DeadmanChestLoot.Reward.Pet -> {
                val outcome = Pets.awardOutcome(Pets.owns(player, reward.pet), Pets.hasFollower(player), player.inventory.hasSpace, player.bank.hasSpace)
                if (outcome == Pets.Award.LOST) addOrDrop(player, reward.item, 1) else Pets.award(player, reward.pet, reward.item, source = "chest")
            }
        }
        // N98 (owner 2026-10-22): 1 in 10 chests add a Dexterous or Arcane prayer scroll.
        // Its own roll ([scrollRandom]): the loot roll above stays exactly the table's.
        gg.rsmod.plugins.content.mechanics.prayer.PrayerScrolls.roll(scrollRandom, gg.rsmod.plugins.content.mechanics.prayer.PrayerScrolls.CHEST_CHANCE)?.let {
            addOrDrop(player, it, 1)
            player.message("<col=ef1020>The chest also holds a prayer scroll!</col>")
            runCatching { gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, "chest", listOf(it to 1)) }
        }
        return reward
    }

    private fun addOrDrop(
        player: Player,
        item: Int,
        amount: Int,
    ) {
        val added = player.inventory.add(item, amount)
        if (added.hasFailed() || added.completed < amount) {
            val left = amount - added.completed
            val ground = gg.rsmod.game.model.entity.GroundItem(item, left, player.tile, player)
            player.world.spawn(ground)
        }
    }
}
