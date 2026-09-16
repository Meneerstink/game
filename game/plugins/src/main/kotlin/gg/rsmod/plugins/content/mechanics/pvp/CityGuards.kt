package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.SkullIcon
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.hasSkullIcon

/**
 * Deadman PvP guards plan (owner-approved 2026-09-16), Batch 3: level-1337 guard subsystem for
 * the ten named guarded/safe cities (Varrock + Grand Exchange, Falador, Catherby, Camelot bank,
 * Warrior Guild, Rellekka, Ardougne, Grand Tree, Yanille, Lumbridge). This is an original RSPS
 * mechanic, not an OSRS import (the plan's own section 2: no OSRS-wiki sourcing needed for these
 * numbers, only correct implementation of the owner's spec).
 *
 * Guarded-zone membership deliberately reuses [BankZones.isSafe] - a real, cache-derived scan of
 * every bank booth/chest object in the loaded world (R03.1) - rather than hand-pinning ten tile
 * sets from map screenshots. A live boot-time diagnostic (temporary, M1 Batch 3, 2026-09-16)
 * confirmed all 92 real bank objects and cross-matched nine of the ten named cities by tile/region
 * against `Donors/Novite`'s own teleport landing tiles (exact region matches: Varrock 12853,
 * Catherby 11061, Camelot 10806, Ardougne 10547, Lumbridge 12850) and the owner's own reference
 * screenshot (Grand Exchange region 12342, tile (3095,3491) in `foto/1.png`/`2.png` - an exact
 * match). The Warrior Guild - no bank in real OSRS - turned out to already have a real bank CHEST
 * object in this specific cache at region 11575 (matching Novite's WARRIORS_GUILD tile exactly),
 * so it needs no manual area either. Open item: Rellekka was not confidently identified by name
 * among the 92 clusters (several remain geographically ambiguous without further research) - if
 * its bank is genuinely absent from this cache that is a separate map-completeness gap, not a
 * guard-mechanic one; if present under an unidentified cluster, it is already protected generically
 * regardless of name, since guards are placed at every real bank cluster, not a hardcoded list.
 */
object CityGuards {
    /** Reuses the existing dedicated market-guard cache id (melee variant, already wired into
     * [BankSecurity]/combat formulas before this batch). */
    const val MELEE_GUARD_ID = BankSecurity.BANK_GUARD_ID

    /** Dedicated, previously-unused cache id (confirmed: no other spawn/content in this codebase
     * references it) for the ranged guard variant - "a dedicated cache id prevents changing
     * ordinary [npcs] elsewhere", the same principle [MELEE_GUARD_ID] already follows. */
    const val RANGED_GUARD_ID = Npcs.TOWER_ARCHER

    /** Dedicated, previously-unused cache id for the "Wizguard" freeze variant. LIVE/AV note:
     * this id's cache model is an archer, not a robed mage - functionally correct (freeze +
     * combat def) but cosmetically a placeholder until a wizard-styled unused id is sourced and
     * swapped in; flagged in the M1 handoff rather than silently presented as final. */
    const val WIZGUARD_ID = Npcs.TOWER_ARCHER_690

    val GUARD_IDS = setOf(MELEE_GUARD_ID, RANGED_GUARD_ID, WIZGUARD_ID)

    /** Owner spec 2026-09-16: guards are "unique level 1337". [Npc.setCombatLevel] overrides only
     * the client-displayed number via the COMBAT_LEVEL extended-info block; it does not touch the
     * shared cache definition or this npc's real combat stats/formula inputs. */
    const val DISPLAYED_COMBAT_LEVEL = 1337

    /** Owner spec 2026-09-16: 1.2 seconds = 2 game cycles at this server's 0.6s cycle. */
    const val ATTACK_SPEED_CYCLES = 2

    /** Owner spec 2026-09-16: "frozen briefly" - no exact duration given; 5 cycles (3s) is this
     * implementation's own reasonable default for "briefly", not a sourced OSRS freeze duration. */
    const val WIZGUARD_FREEZE_CYCLES = 5

    fun isGuard(npc: Npc): Boolean = npc.id in GUARD_IDS

    /** Owner spec 2026-09-16: "their damage ignores protection prayers entirely" - shared by both
     * [gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula] and
     * [gg.rsmod.plugins.content.combat.formula.RangedCombatFormula] so the two formulas cannot
     * drift on which ids are exempt. */
    fun bypassesProtectionPrayer(pawn: Pawn): Boolean = pawn is Npc && isGuard(pawn)

    /**
     * Whether [guard] may still engage [target]: the target must be skulled AND currently inside
     * a guarded zone. [gg.rsmod.plugins.content.combat.Combat.canEngage] re-checks this every
     * single combat cycle for an already-engaged pawn (not only at initial aggro pick), so wiring
     * this one predicate into that shared gate is what makes a guard "stop the instant the player
     * leaves the guarded area" (owner spec) - no separate leave-detection needed.
     */
    fun mayAttack(
        @Suppress("UNUSED_PARAMETER") guard: Npc,
        target: Player,
    ): Boolean = target.hasSkullIcon(SkullIcon.RED) && isGuardedZone(target.tile)

    fun isGuardedZone(tile: Tile): Boolean = BankZones.isSafe(tile)

    // ---- consecutive-hit damage ramp ----

    private val CONSECUTIVE_GUARD_HITS_ATTR = AttributeKey<Int>()
    private val LAST_RAMP_CYCLE_ATTR = AttributeKey<Int>()

    /**
     * Owner spec 2026-09-16: "damage ramps up per consecutive hit, starting at 20% of the
     * victim's hitpoints". The starting point (20% of max hitpoints) is the owner's explicit
     * number; the ongoing per-hit step (+20% of max hitpoints, capped at 100%) is this
     * implementation's own reasonable provisional curve for how the ramp continues, since the
     * owner did not specify one - flagged in the M1 handoff for owner retune, not presented as a
     * sourced fact. Idempotent per world cycle ([LAST_RAMP_CYCLE_ATTR]) so a formula that is
     * queried more than once for the same real attack cannot double-advance the ramp.
     */
    fun rampedMaxHit(
        guard: Npc,
        target: Player,
    ): Int {
        val cycle = guard.world.currentCycle
        val lastCycle = target.attr[LAST_RAMP_CYCLE_ATTR]
        val hits =
            if (lastCycle == cycle) {
                target.attr[CONSECUTIVE_GUARD_HITS_ATTR] ?: 1
            } else {
                val next = (target.attr[CONSECUTIVE_GUARD_HITS_ATTR] ?: 0) + 1
                target.attr[CONSECUTIVE_GUARD_HITS_ATTR] = next
                target.attr[LAST_RAMP_CYCLE_ATTR] = cycle
                next
            }
        val fraction = (0.20 * hits).coerceAtMost(1.0)
        return (target.getMaximumLifepoints() * fraction).toInt().coerceAtLeast(1)
    }

    /** Call when [target] leaves a guarded zone or its skull clears, so a later unrelated guard
     * encounter starts the ramp fresh at 20% rather than continuing an old count. */
    fun resetDamageRamp(target: Player) {
        target.attr.remove(CONSECUTIVE_GUARD_HITS_ATTR)
        target.attr.remove(LAST_RAMP_CYCLE_ATTR)
    }

    // ---- reactive spawn-on-skulled-entry ----

    private val WAS_IN_GUARDED_ZONE_ATTR = AttributeKey<Boolean>()
    private val REACTIVE_GUARDS_ATTR = AttributeKey<MutableList<java.lang.ref.WeakReference<Npc>>>()

    /**
     * Owner spec 2026-09-16: guards "additionally spawn directly on top of any skulled player who
     * is inside a guarded (safe) area", regardless of the area's normal multicombat flag, in
     * addition to any already-stationed patrol guards. Called every cycle alongside
     * [BankSecurity.monitor] from the same existing polling timer (this revision has no generic
     * player-step event).
     */
    fun onZoneCheck(player: Player) {
        val world = player.world
        val inZone = isGuardedZone(player.tile)
        val wasInZone = player.attr[WAS_IN_GUARDED_ZONE_ATTR] ?: false
        player.attr[WAS_IN_GUARDED_ZONE_ATTR] = inZone

        if (!inZone) {
            if (wasInZone) {
                resetDamageRamp(player)
                despawnReactiveGuards(world, player)
            }
            return
        }

        if (!player.hasSkullIcon(SkullIcon.RED)) {
            return
        }

        val entering = !wasInZone
        val existing = player.attr[REACTIVE_GUARDS_ATTR]?.mapNotNull { it.get() }?.filter { it.isActive() } ?: emptyList()
        if (entering || existing.isEmpty()) {
            despawnReactiveGuards(world, player)
            spawnReactiveGuards(world, player)
            spawnWizguardFreeze(world, player)
        }
    }

    private fun spawnReactiveGuards(
        world: World,
        player: Player,
    ) {
        val spawned = ArrayList<java.lang.ref.WeakReference<Npc>>(2)
        for (guardId in listOf(MELEE_GUARD_ID, RANGED_GUARD_ID)) {
            val guard =
                Npc(guardId, Tile(player.tile), world).also {
                    it.respawns = false
                    it.walkRadius = 0
                }
            world.spawn(guard)
            guard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
            guard.forceChat("We don't want your sort here, ${player.username}!")
            guard.aggroCheck = { _, target -> mayAttack(guard, target) }
            if (guard.canEngageCombat(player)) {
                guard.attack(player)
            }
            spawned += java.lang.ref.WeakReference(guard)
        }
        player.attr[REACTIVE_GUARDS_ATTR] = spawned
    }

    private fun despawnReactiveGuards(
        world: World,
        player: Player,
    ) {
        player.attr[REACTIVE_GUARDS_ATTR]?.forEach { ref ->
            ref.get()?.let { guard -> if (guard.isActive()) world.remove(guard) }
        }
        player.attr.remove(REACTIVE_GUARDS_ATTR)
    }

    private fun spawnWizguardFreeze(
        world: World,
        player: Player,
    ) {
        val wizguard = Npc(WIZGUARD_ID, Tile(player.tile), world).also { it.respawns = false; it.walkRadius = 0 }
        world.spawn(wizguard)
        wizguard.setCombatLevel(DISPLAYED_COMBAT_LEVEL)
        player.freeze(WIZGUARD_FREEZE_CYCLES) {
            player.filterableMessage("A Wizguard casts a binding spell on you!")
        }
        world.remove(wizguard)
    }
}
