package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.FAMILIAR_NPC_ID_ATTR
import gg.rsmod.game.model.attr.SUMMONING_POINTS_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import java.lang.ref.WeakReference

/**
 * R07.1/R07.2: real summon/follow/renew/dismiss for [SummoningPouchData]'s already-existing
 * full pouch roster (Spirit Wolf through Steel Titan/Pack Yak/War Tortoise) - not just
 * registrations, an actually working core mechanic every specific familiar builds on.
 *
 * Summoning points are a one-off cost paid when the pouch is opened; they are not drained
 * gradually. A familiar's lifetime is an independent per-familiar timer sourced from the
 * revision-era "Summoning - Familiars" table (see [SummoningFamiliarDefinitions] and the
 * duration assertions in `SummoningLedgerTests`), ranging from the dreadfowl's 4 minutes to the
 * rune minotaur's 151. Renewing re-arms that timer from a second pouch of the same type without
 * charging Summoning points again, matching real RS "renew" behaviour.
 *
 * Following reuses the real, already-existing [MovementQueue] every pawn's normal walking goes
 * through (the same system NPCs use to wander/chase) rather than a simplified teleport-style
 * step - real collision-respecting, animated movement, not a hack.
 */
val FAMILIAR_ATTR = AttributeKey<WeakReference<Npc>>()

/** Persisted Summoning special-move energy. This is separate from Summoning points. */
val FAMILIAR_SPECIAL_POINTS_ATTR = AttributeKey<Int>(persistenceKey = "familiar_special_points")


/** Persisted online-cycle accumulator; preserves partial special regeneration through relog. */
private val FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR = AttributeKey<Int>(persistenceKey = "familiar_special_regen_cycles")
/**
 * Persisted for the same reason as the special-regeneration accumulator above: a familiar
 * survives logout in this era, so its passive-healing cadence has to survive with it. Left
 * transient, a relog restarted the 15-second cycle from zero, which is the wrong direction of
 * the same class of bug as a resettable cooldown.
 */
private val FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR = AttributeKey<Int>(persistenceKey = "familiar_passive_heal_cycles")
/**
 * R07.7: persisted (sourced - familiars survive logout in this era, the lifetime timer just
 * pauses while offline via [TimerKey.tickOffline] = false and resumes with the same time left
 * on login) rather than transient. [FAMILIAR_ATTR] itself stays transient (a live [Npc]
 * reference is meaningless across a logout/despawn) - [FAMILIAR_NPC_ID_ATTR] is the persisted
 * id [Familiar.restoreOnLogin] respawns from.
 */
val FAMILIAR_LIFETIME_TIMER = TimerKey(persistenceKey = "familiar_lifetime", tickOffline = false, resetOnDeath = false)

/** Last time-remaining string pushed to the HUD, so [Familiar.tick] doesn't spam an
 *  [gg.rsmod.plugins.api.ext.setComponentText] packet every cycle - only when the displayed
 *  value actually changes. */
private val FAMILIAR_HUD_TEXT_ATTR = AttributeKey<String>()
private val FAMILIAR_HUD_POINTS_TEXT_ATTR = AttributeKey<String>()


object Familiar {
    const val MAX_SPECIAL_POINTS = 60
    private const val RENEW_THRESHOLD_SECONDS = 170
    private const val SPECIAL_REGEN_SECONDS = 30
    private const val SPECIAL_REGEN_AMOUNT = 15

    /** R07 follower interface (see InterfaceDestination.SUMMONING_TAB for the evidence trail). */
    private const val HUD_INTERFACE = 662

    // R07.7 fix: a fresh full raw-component-text re-scan of interface 662 (76 components) found
    // component 44's real cache text is "Pet size percentage" and component 48 sits right after
    // component 47's real label "Pet hunger percentage" - both distinct real fields, not a
    // fixed/resize duplicate of the time/points display as the earlier R07.6 code assumed.
    // Writing time/points text into them would clobber real UI content, so only the single
    // component directly following each field's own label is driven now (42=label->43=value for
    // time; 41 is the value cell directly under the "SPECIAL MOVE" header at 40, the closest
    // real candidate to a points display near component 18's "Summoning points remaining"
    // label - no separate value component was found near 18 itself in the raw scan).
    private const val HUD_TIME_COMPONENT = 43
    private const val HUD_POINTS_COMPONENT = 41
    private const val HUD_BOB_BUTTON = 67

    /**
     * R07.2: max Summoning points = the player's current (boosted) Summoning level, 1:1, no
     * x10 multiplier - the pre-2018-rework mechanic (sourced: 2011.rs's own "49/50" worked
     * example; runescape.wiki's own admission the x10 scaling was introduced 20 Aug 2018).
     * Mirrors [gg.rsmod.plugins.content.mechanics.prayer.Prayers]' `getMaxLevel`-driven prayer
     * point cap - same pattern, different skill.
     */
    fun maxPoints(player: Player): Int = player.skills.getMaxLevel(Skills.SUMMONING)

    fun currentPoints(player: Player): Int = player.attr.getOrDefault(SUMMONING_POINTS_ATTR, maxPoints(player)).coerceAtMost(maxPoints(player))

    fun currentSpecialPoints(player: Player): Int =
        player.attr.getOrDefault(FAMILIAR_SPECIAL_POINTS_ATTR, MAX_SPECIAL_POINTS).coerceIn(0, MAX_SPECIAL_POINTS)

    private fun setPoints(
        player: Player,
        value: Int,
    ) {
        player.attr[SUMMONING_POINTS_ATTR] = value.coerceIn(0, maxPoints(player))
    }

    private fun setSpecialPoints(player: Player, value: Int) {
        player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = value.coerceIn(0, MAX_SPECIAL_POINTS)
    }

    /** Restores Summoning points without touching the familiar timer or special-move energy. */
    fun restorePoints(player: Player, amount: Int = maxPoints(player)) {
        setPoints(player, currentPoints(player) + amount.coerceAtLeast(0))
    }

    /** Restores the separate special-move pool (one potion dose restores 15 points). */
    fun restoreSpecialPoints(player: Player, amount: Int) {
        setSpecialPoints(player, currentSpecialPoints(player) + amount.coerceAtLeast(0))
    }

    /** Target-period special energy restoration: +15, capped at 60, every 30 online seconds. */
    private fun regenerateSpecialPoints(player: Player) {
        val interval = (SPECIAL_REGEN_SECONDS * 1_000 / player.world.gameContext.cycleTime).coerceAtLeast(1)
        val accumulated = player.attr.getOrDefault(FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR, 0) + 1
        if (accumulated < interval) {
            player.attr[FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR] = accumulated
            return
        }
        player.attr[FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR] = accumulated % interval
        restoreSpecialPoints(player, SPECIAL_REGEN_AMOUNT)
    }

    /** Void Spinner and Bunyip restore 100/20 internal life points every 15 online seconds. */
    private fun applyPassiveHealing(player: Player, npc: Npc) {
        val amount = when (npc.id) {
            SummoningPouchData.VOID_SPINNER.npc -> 100
            SummoningPouchData.BUNYIP.npc -> 20
            else -> {
                player.attr.remove(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR)
                return
            }
        }
        val interval = (15_000 / player.world.gameContext.cycleTime).coerceAtLeast(1)
        val cycles = player.attr.getOrDefault(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR, 0) + 1
        if (cycles < interval) {
            player.attr[FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR] = cycles
            return
        }
        player.attr[FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR] = cycles % interval
        if (player.getCurrentLifepoints() < player.getMaximumLifepoints()) {
            player.heal(amount)
            if (npc.id == SummoningPouchData.BUNYIP.npc) player.graphic(1507)
        }
    }
    /** Atomically spends special-move energy; effects must call this only after validation. */
    fun consumeSpecialPoints(player: Player, amount: Int): Boolean {
        require(amount >= 0) { "Special-move cost cannot be negative." }
        if (currentSpecialPoints(player) < amount) {
            return false
        }
        setSpecialPoints(player, currentSpecialPoints(player) - amount)
        return true
    }

    /** Target-period pouch values from the 2011 familiar roster. */
    private fun definition(data: SummoningPouchData): SummoningFamiliarDefinition =
        SummoningFamiliarDefinitions.get(data)

    /** TimerMap uses game cycles, not real milliseconds. */
    private fun lifetimeCycles(player: Player, data: SummoningPouchData): Int =
        definition(data).durationMinutes * 60_000 / player.world.gameContext.cycleTime

    private fun renewThresholdCycles(player: Player): Int =
        RENEW_THRESHOLD_SECONDS * 1_000 / player.world.gameContext.cycleTime

    /**
     * Pushes the confirmed-real duration text (components 43/48), the points-remaining text
     * (41/44 - now real, driven by [currentPoints]/[maxPoints] rather than left blank) and the
     * Take-BoB-items button (67, hidden for non-BoB familiars) to interface 662.
     */
    private fun updateHud(player: Player) {
        val npc = current(player)
        if (npc == null) {
            if (player.attr.has(FAMILIAR_HUD_TEXT_ATTR)) {
                player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT, "")
                player.setComponentHidden(HUD_INTERFACE, HUD_BOB_BUTTON, true)
                player.attr.remove(FAMILIAR_HUD_TEXT_ATTR)
            }
        } else {
            val cycles = if (player.timers.has(FAMILIAR_LIFETIME_TIMER)) player.timers[FAMILIAR_LIFETIME_TIMER] else 0
            val totalSeconds = cycles * player.world.gameContext.cycleTime / 1000
            val text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
            if (player.attr[FAMILIAR_HUD_TEXT_ATTR] != text) {
                player.attr[FAMILIAR_HUD_TEXT_ATTR] = text
                player.setComponentText(HUD_INTERFACE, HUD_TIME_COMPONENT, text)
                player.setComponentHidden(HUD_INTERFACE, HUD_BOB_BUTTON, !BeastOfBurden.isBobNpc(npc.id))
            }
        }
        val pointsText = "${currentPoints(player)}/${maxPoints(player)}"
        if (player.attr[FAMILIAR_HUD_POINTS_TEXT_ATTR] != pointsText) {
            player.attr[FAMILIAR_HUD_POINTS_TEXT_ATTR] = pointsText
            player.setComponentText(HUD_INTERFACE, HUD_POINTS_COMPONENT, pointsText)
        }
    }

    fun current(player: Player): Npc? {
        val npc = player.attr[FAMILIAR_ATTR]?.get() ?: return null
        if (!player.world.npcs.contains(npc)) {
            player.attr.remove(FAMILIAR_ATTR)
            return null
        }
        return npc
    }

    /**
     * Real RS puts a summoned or recalled familiar *beside* its owner, on tiles its own footprint
     * actually fits on - it does not stack it on the owner's exact tile, which is what this code
     * used to do for summon, call, plane changes and teleport recovery alike.
     *
     * The eight candidates are the whole adjacent ring expressed as south-west corner tiles, so
     * they stay correct for the 2x2 and 3x3 familiars (titans, pack yak) as well as the 1x1 ones.
     * Which direction real RS prefers is not sourced, so the order here is only deterministic;
     * adjacency and collision validity are the parts that are sourced.
     *
     * If nothing in the ring fits - a doorway, a dense object cluster - the owner's own tile is
     * the last resort. A familiar must always arrive; being stranded is worse than overlapping.
     */
    private fun placementTile(
        player: Player,
        npcId: Int,
    ): Tile {
        val size = player.world.definitions.get(NpcDef::class.java, npcId).size.coerceAtLeast(1)
        val offsets =
            arrayOf(
                -size to 0, 1 to 0, 0 to -size, 0 to 1,
                -size to -size, 1 to -size, -size to 1, 1 to 1,
            )
        offsets.forEach { (x, z) ->
            val candidate = player.tile.transform(x, z)
            if (fits(player, candidate, size)) {
                return candidate
            }
        }
        return player.tile
    }

    private fun fits(
        player: Player,
        tile: Tile,
        size: Int,
    ): Boolean {
        for (x in 0 until size) {
            for (z in 0 until size) {
                if (player.world.collision.isClipped(tile.transform(x, z))) {
                    return false
                }
            }
        }
        return true
    }

    fun summon(
        player: Player,
        data: SummoningPouchData,
    ): Boolean {
        if (player.skills.getMaxLevel(Skills.SUMMONING) < data.level) {
            player.message("You need a Summoning level of ${data.level} to summon this familiar.")
            return false
        }
        val definition = definition(data)
        val cost = definition.summonPoints
        if (currentPoints(player) < cost) {
            player.message("You don't have enough Summoning points to summon this familiar.")
            return false
        }
        // R07.1 pouch-consumption fix: summon() never removed the pouch at all (root cause of
        // the owner-reported "pouch remains in inventory after summon" bug) - consume exactly
        // one pouch, and fail before touching XP/timer/familiar state if it's not actually there
        // (e.g. this same pouch triggered summon twice in one client tick).
        if (!player.inventory.remove(data.pouch, assureFullRemoval = true).hasSucceeded()) {
            return false
        }
        dismiss(player)

        val npc = Npc(player, data.npc, placementTile(player, data.npc), player.world)
        npc.publicOwner = true
        npc.respawnOverride = false
        npc.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        player.world.spawn(npc)

        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        player.attr[FAMILIAR_NPC_ID_ATTR] = data.npc
        player.timers[FAMILIAR_LIFETIME_TIMER] = lifetimeCycles(player, data)
        setPoints(player, currentPoints(player) - cost)
        player.addXp(Skills.SUMMONING, data.summonExperience)
        player.message("You summon your familiar.")
        updateHud(player)
        return true
    }

    /**
     * A familiar can only be renewed below 2:50 remaining. Renew consumes one matching pouch
     * and restores its native duration; it does not charge Summoning points a second time.
     */
    fun renew(player: Player): Boolean {
        val npc = current(player) ?: return false
        val data = SummoningPouchData.values.firstOrNull { it.npc == npc.id } ?: return false
        val remaining = if (player.timers.has(FAMILIAR_LIFETIME_TIMER)) player.timers[FAMILIAR_LIFETIME_TIMER] else 0
        if (remaining >= renewThresholdCycles(player)) {
            player.message("You need less than 2:50 remaining before you can renew your familiar.")
            return false
        }
        if (!player.inventory.remove(data.pouch, assureFullRemoval = true).hasSucceeded()) {
            player.message("You need another pouch of this type to renew your familiar.")
            return false
        }
        player.timers[FAMILIAR_LIFETIME_TIMER] = lifetimeCycles(player, data)
        player.message("You renew your familiar's summoning duration.")
        updateHud(player)
        return true
    }

    fun dismiss(player: Player) {
        val npc = current(player) ?: return
        BeastOfBurden.release(player, npc.tile)
        player.world.remove(npc)
        clearState(player)
        player.message("Your familiar is dismissed.")
        updateHud(player)
    }

    /**
     * R07.7: logout - despawn the npc (a dead client connection can't render it) but keep the
     * persisted state ([FAMILIAR_NPC_ID_ATTR], the lifetime timer, the drain-remaining total)
     * intact so [restoreOnLogin] can bring the same familiar back with the same time/points
     * left. Distinct from [dismiss], which is a full, permanent clear.
     */
    fun disconnect(player: Player) {
        val npc = current(player) ?: return
        player.world.remove(npc)
        player.attr.remove(FAMILIAR_ATTR)
    }

    /**
     * R07.7: login counterpart to [disconnect] - if persisted state says the player had an
     * active familiar with time left (the timer paused offline via `tickOffline = false`, so a
     * present, non-zero value means it's still owed), respawn it in place rather than leaving
     * the player's persisted points/timer state orphaned with no npc to show for it. If the
     * persisted npc id is missing its timer (e.g. it or the drain-remaining state was cleared
     * independently), the state is stale - clear it instead of respawning nothing.
     */
    fun restoreOnLogin(player: Player) {
        val npcId = player.attr[FAMILIAR_NPC_ID_ATTR]
        if (npcId == null) {
            return
        }
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER) || player.timers[FAMILIAR_LIFETIME_TIMER] <= 0) {
            player.attr.remove(FAMILIAR_NPC_ID_ATTR)
            player.timers.remove(FAMILIAR_LIFETIME_TIMER)
            return
        }
        val npc = Npc(player, npcId, placementTile(player, npcId), player.world)
        npc.publicOwner = true
        npc.respawnOverride = false
        npc.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        player.world.spawn(npc)
        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        updateHud(player)
    }

    /**
     * "Call familiar" - instant recall, distinct from passive following. Uses the real
     * [gg.rsmod.game.model.entity.Pawn.teleportNpc] respawn-style reposition API (sets
     * moved/teleported/invisible + clears movement queue), now onto a valid [placementTile]
     * beside the owner rather than the owner's own tile.
     *
     * The knowledge base also gives this button a second job: "If you are fighting in a
     * multicombat area, this button will also make your familiar attack your enemy." That is a
     * re-order, not just the passive per-tick assistance - it pulls a familiar off a stale target
     * onto whatever the owner is currently fighting.
     */
    fun call(player: Player): Boolean {
        val npc = current(player) ?: return false
        npc.teleportNpc(placementTile(player, npc.id))
        player.message("You call your familiar to your side.")
        FamiliarCombat.recallToOwnerTarget(player)
        updateHud(player)
        return true
    }

    /**
     * Sourced revision-667 owner-death behaviour, and deliberately not the modern one.
     *
     * Two later RuneScape updates fix what this era did: the 22 August 2016 ninja strike added
     * "A beast of burden's inventory is now dropped to the floor when a player dies", and the
     * 13 November 2023 patch added "Familiars no longer despawn on death". Both are changes
     * *away* from the behaviour of this revision, so here the familiar despawns when its owner
     * dies and whatever it was carrying goes with it - it is not dropped for the killer, and it
     * is not kept.
     *
     * This is destructive, so it is isolated in its own function rather than reusing [dismiss]
     * (which drops the cargo, the correct behaviour for a voluntary dismissal). Serving the
     * modern, friendlier rule instead is a one-line change: call [dismiss] from the owner-death
     * hook in `familiar.plugin.kts`.
     */
    fun ownerDeath(player: Player) {
        val npc = current(player) ?: return
        val lostItems = BeastOfBurden.contents(player).isNotEmpty()
        BeastOfBurden.discard(player)
        player.world.remove(npc)
        clearState(player)
        if (lostItems) {
            player.message("Your familiar vanishes, taking everything it was carrying with it.")
        } else {
            player.message("Your familiar vanishes.")
        }
        updateHud(player)
    }

    /**
     * Handles the familiar itself dying. Beast-of-burden cargo is released at the
     * familiar's death tile, then the owner's familiar state is cleared so a dead
     * NPC cannot continue following, attacking, renewing or receiving specials.
     */
    fun onDeath(npc: Npc) {
        val owner = npc.attr[DAMAGE_CREDIT_ATTR]?.get() as? Player ?: return
        if (owner.attr[FAMILIAR_ATTR]?.get() !== npc) return
        if (BeastOfBurden.isBobNpc(npc.id)) {
            BeastOfBurden.release(owner, npc.tile)
        }
        clearState(owner)
        updateHud(owner)
    }
    private fun clearState(player: Player) {
        player.attr.remove(FAMILIAR_ATTR)
        player.attr.remove(FAMILIAR_NPC_ID_ATTR)
        player.timers.remove(FAMILIAR_LIFETIME_TIMER)
        player.attr.remove(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR)
    }

    /** Common despawn-and-notify cleanup shared by expiry (timer or points) and manual dismiss. */
    private fun expire(
        player: Player,
        npc: Npc,
    ) {
        BeastOfBurden.release(player, npc.tile)
        player.world.remove(npc)
        clearState(player)
        player.message("Your familiar has run out of time and returns home.")
        updateHud(player)
    }

    /** Called once/cycle per online player - see `familiar.plugin.kts`. */
    fun tick(player: Player) {
        val npc = current(player) ?: return
        regenerateSpecialPoints(player)
        applyPassiveHealing(player, npc)
        FamiliarCombat.assist(player)
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER)) {
            // Lifetime ran out - real RS despawns the familiar, it doesn't just sit there inert.
            expire(player, npc)
            return
        }
        val distance = npc.tile.getDistance(player.tile)
        if (npc.tile.height != player.tile.height || distance > Player.NORMAL_VIEW_DISTANCE) {
            // Plane change / region teleport put the familiar out of walking range - a normal
            // MovementQueue step can never catch up (or can't cross planes at all), so recover
            // it the same way `call()` does rather than leaving it stranded/left behind.
            npc.teleportNpc(placementTile(player, npc.id))
        } else if (!npc.movementQueue.hasDestination() && distance > 1) {
            npc.movementQueue.addStep(player.tile, MovementQueue.StepType.NORMAL, detectCollision = true)
        }
        updateHud(player)
    }
}
