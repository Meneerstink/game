package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.entity.zoneTile

import gg.rsmod.game.action.PawnPathAction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_ATTR
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.game.model.timer.DEADMAN_LOGOUT_TIMER
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.strategy.CombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.mechanics.pvp.BossNpcs
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Combat {
    /**
     * An npc that only other npcs may attack (Pest Control's Void Knight, 2026-10-02): the cache gives it no "Attack" option,
     * so players cannot target it, yet the pests must be able to. Checked in [canEngage] next to the familiar exception.
     */
    val NPC_ONLY_TARGET_ATTR = AttributeKey<Boolean>()

    /**
     * The single-way "already under attack" window. OSRS Wiki "PJ timer": "In single-way combat when under attack by a monster,
     * the player starts a PJ timer of 8 game ticks (4.8 seconds) ... refreshed every time the monster attacks the player"; "NPCs
     * also have the same 4.8-second PJ timer applied towards them when hit by a player"; the longer PvP-only 20-tick timer is
     * "NOT applied in the Bounty Hunter or Deadman Mode worlds". Before 2026-10-01 single-way read ACTIVE_COMBAT_TIMER (17 ticks,
     * the 2023 rsmod default), which kept a PJ'd player locked to his first attacker more than twice as long.
     * N97 (owner VRAGEN 54 = B, 2026-10-21): back to his choice of 2026-09-25 - 17 ticks (10.2 s) for everyone, players and monsters.
     */
    const val PJ_TICKS = 17
    val PJ_TIMER = gg.rsmod.game.model.timer.TimerKey(tickOffline = false, resetOnDeath = true)

    /**
     * Modal interfaces that survive being attacked, plus -1 for "no modal is open".
     *
     * Blanket-closing every modal on the first incoming hit is not a pre-EoC rule; it is a
     * convenience this server added. It is wrong for Summoning in particular, which is built
     * around acting while under attack: 2011 players routinely withdrew food from a beast of
     * burden mid-fight, and the Knowledge Base describes the Familiar Inventory as an ordinary
     * interaction with no combat caveat. The live symptom was a pack yak's Familiar Inventory
     * opening and then closing again about a second later, as soon as anything hit the player.
     *
     * 671 is the Familiar Inventory window (see
     * [gg.rsmod.plugins.content.skills.summoning.FamiliarInventory]).
     */
    private val COMBAT_PERSISTENT_MODALS =
        intArrayOf(
            -1,
            671,
            // Owner 2026-10-04 (031046 / 030437): the Graphics (742) and Audio (743) settings and the gravestone loot screen stay
            // open while the player is hit ("in osrs the gui keeps open"; a grave could not be looted under attack).
            742,
            743,
            // ... and the Options tab's third settings page, Adventurer's Log settings (623): "or whatever setting" (031046).
            623,
            gg.rsmod.plugins.content.mechanics.death.GraveInterface.INTERFACE_ID,
            // Barrows puzzle (25): a crypt rat's hit closed it in the cycle it opened - the door "did nothing" (owner 03-2049,
            // reproduced 2026-10-05 in the agent's client: IfOpenSub 25 then IfCloseSub after the same OpLoc1).
            gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.PUZZLE_INTERFACE,
            // N470 (2026-10-30): the Barrows chest reward window (364) - looting starts the collapse and the first falling rocks hit
            // in the same cycle, so it closed before the player saw it (IfOpenSub 364 then IfCloseSub after the same OpLoc1).
            gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows.REWARD_INTERFACE,
        )

    /** A hit leaves this player's open window alone (and does not make him auto-retaliate, which would walk away from it). */
    fun keepsWindowOpen(player: Player): Boolean = player.interfaces.getModal() != -1 && player.interfaces.getModal() in COMBAT_PERSISTENT_MODALS

    /** Whether a hit leaves the main-screen interface [modal] open (CombatPersistentModalTests). */
    fun staysOpenWhenHit(modal: Int): Boolean = modal != -1 && modal in COMBAT_PERSISTENT_MODALS

    val CASTING_SPELL = AttributeKey<CombatSpell>()
    val DAMAGE_DEAL_MULTIPLIER = AttributeKey<Double>()
    val DAMAGE_TAKE_MULTIPLIER = AttributeKey<Double>()
    val BOLT_ENCHANTMENT_EFFECT = AttributeKey<Boolean>()

    /** The spell the current attack cast; set by MagicCombatStrategy.attack, read for the attack delay and cleared by [postAttack]. */
    val SPELL_OF_THIS_ATTACK = AttributeKey<CombatSpell>()

    const val PRIORITY_PID_VARP = 1075

    /** Display mirror of the active autocast spell (667 spellbook highlight, CS2 1121); written only by Autocast.sync. */
    const val SELECTED_AUTOCAST_VARP = 108

    /**
     * N24 (2026-10-17): the npc definition a PLAYER sees and attacks - the transmog's while [npc] shows one, else its own. The
     * client builds the menu from the transmog (an awake strykewyrm 9465 shows "Attack"), but the attack gates read the base
     * definition (the mound 9464, [Investigate] only), so every Attack click and auto-retaliate on an awake wyrm was refused.
     * Donor: Void `NPC.def(player)` (engine NPC.kt:98-103, the "transform_id" definition) as used by Void `Target.attackable`
     * (Target.kt:56-58) for a player attacker; npc attackers keep the base definition (Target.kt:59-63). A transmog of 0 or less
     * is "none" (relaxed test mocks answer 0).
     */
    fun attackOptionDef(npc: Npc): gg.rsmod.game.fs.def.NpcDef {
        val transmog = npc.getTransmogId()
        if (transmog <= 0) return npc.def
        return npc.world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, transmog) ?: npc.def
    }

    /**
     * Ends [pawn]'s fight. The pawn also stops facing that target (owner 2026-09-25: after combat ended - a weapon switch left
     * without ammo, "already under attack", a lock - the player kept turning towards the npc). [keepFacing] is only for a
     * manual single cast, which ends the fight but still looks at the target it just cast on.
     */
    fun reset(pawn: Pawn, keepFacing: Boolean = false) {
        val target = pawn.attr[COMBAT_TARGET_FOCUS_ATTR]?.get()
        pawn.attr.remove(COMBAT_TARGET_FOCUS_ATTR)
        if (!keepFacing && target != null && pawn.attr[gg.rsmod.game.model.attr.FACING_PAWN_ATTR]?.get() === target) {
            pawn.resetFacePawn()
        }
    }

    fun canAttack(
        pawn: Pawn,
        target: Pawn,
        combatClass: CombatClass,
    ): Boolean = canEngage(pawn, target) && getStrategy(combatClass).canAttack(pawn, target)

    fun canAttack(
        pawn: Pawn,
        target: Pawn,
        strategy: CombatStrategy,
    ): Boolean = canEngage(pawn, target) && strategy.canAttack(pawn, target)

    fun isAttackDelayReady(pawn: Pawn): Boolean = !pawn.timers.has(ATTACK_DELAY) &&
        !pawn.timers.has(gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.ICE_ATTACK_LOCK)

    /** Audit X-10: minimum logout hold after a player is attacked or takes a hit (poison and venom included). */
    const val LOGOUT_HOLD_TICKS = 16

    /** Audit X-10: extends (never shortens) the Deadman logout hold to at least [ticks]. */
    fun holdLogout(
        player: Player,
        ticks: Int = LOGOUT_HOLD_TICKS,
    ) {
        val remaining = if (player.timers.has(DEADMAN_LOGOUT_TIMER)) player.timers[DEADMAN_LOGOUT_TIMER] else 0
        if (ticks > remaining) {
            player.timers[DEADMAN_LOGOUT_TIMER] = ticks
        }
    }

    /** Set by an attack whose landing makes the attacker's next attack one tick sooner; consumed by [postAttack]. */
    val NEXT_ATTACK_ONE_TICK_FASTER = gg.rsmod.game.model.attr.AttributeKey<Boolean>(resetOnDeath = true)

    fun postAttack(
        pawn: Pawn,
        target: Pawn,
    ) {
        SinglesPlus.recordAttack(pawn, target)
        // Interruptible actions observe the actual swing, even when a re-armed combat timer stays at the same value.
        pawn.recordAction()
        target.recordAction()
        val attackDelay = CombatConfigs.getAttackDelay(pawn)
        pawn.attr.remove(SPELL_OF_THIS_ATTACK)
        pawn.timers[ATTACK_DELAY] = attackDelay
        // Blood moon armour Bloodrager: the dual macuahuitl attacks one tick earlier after a trigger (MoonSets).
        if (pawn.attr[gg.rsmod.plugins.content.items.osrs.MoonSets.BLOODRAGER] == true) {
            pawn.attr.remove(gg.rsmod.plugins.content.items.osrs.MoonSets.BLOODRAGER)
            pawn.timers[ATTACK_DELAY] = maxOf(1, attackDelay - 1)
        }
        // A landed Hamstring (Morrigan's throwing axe, Deadman Mode): "reduce the attack interval of the next attack by 1 tick".
        if (pawn.attr[NEXT_ATTACK_ONE_TICK_FASTER] == true) {
            pawn.attr.remove(NEXT_ATTACK_ONE_TICK_FASTER)
            pawn.timers[ATTACK_DELAY] = maxOf(1, attackDelay - 1)
        }
        // Granite maul homing: "for 5 ticks after attacking a target with any weapon" (GraniteMaul).
        pawn.attr[gg.rsmod.plugins.content.items.osrs.GraniteMaul.LAST_ATTACK_CYCLE] = pawn.world.currentCycle
        target.timers[ACTIVE_COMBAT_TIMER] = 17 // 10,2 seconds
        target.timers[PJ_TIMER] = PJ_TICKS
        // Deadman PvP guards plan (2026-09-16): the out-of-combat teleport gate's own 7-second
        // window, and "being attacked" cancels an in-progress 7-second logout/teleport/portal/
        // transport countdown.
        target.timers[TELEPORT_COMBAT_TIMER] = gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.DURATION_CYCLES
        // Audit X-10: being attacked by anything, bosses included, holds a disconnected body in the world until the attack's
        // pending hits have landed (a boss x-log used to escape an in-flight lethal hit). dealHit extends the hold again on impact.
        (target as? Player)?.let { holdLogout(it) }
        if (!BossNpcs.isBoss(target)) {
            (pawn as? Player)?.let { holdLogout(it, gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.DURATION_CYCLES) }
        }
        if (target is Player) {
            gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.cancel(target, "You have been attacked!")
        }
        pawn.attr[BOLT_ENCHANTMENT_EFFECT] = false

        pawn.attr[LAST_HIT_ATTR] = WeakReference(target)
        target.attr[LAST_HIT_BY_ATTR] = WeakReference(pawn)

        if (pawn is Player && target is Player) {
            PvpSkull.markAggression(attacker = pawn, victim = target)
            // The player's own outgoing player attack - the OSRS PvP autocast swap rule counts from this, never from being attacked.
            gg.rsmod.plugins.content.combat.magic.Autocast.onOutgoingPlayerAttack(pawn)
        }
        // Deadman 24-second high-value deposit block ("recently been in combat").
        (pawn as? Player)?.let { gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.markCombat(it) }
        (target as? Player)?.let { gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.markCombat(it) }
        // Toxic staff of the dead: 10 scales on entering combat and every further minute in combat (StaffOfTheDead).
        (pawn as? Player)?.let { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.onCombat(it) }
        (target as? Player)?.let { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.onCombat(it) }

        // Owner 2026-09-19: in a dangerous bank only a PKer's 2-tick attacks close the victim's bank; a slower attack
        // leaves it open and does not auto-retaliate (retaliating would walk away from the booth and close it anyway).
        val bankKeptOpen =
            target is Player && gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.keepsBankOpen(pawn, target, attackDelay)
        if (target is Player && !bankKeptOpen && target.interfaces.getModal() != -1 && target.interfaces.getModal() !in COMBAT_PERSISTENT_MODALS) {
            // Close the modal together with its tab-area side panel (equipment stats 670, bank 763, shop 621, ...): closing
            // only the modal left the side panel mounted over a hidden tab strip (owner picture "interface hang").
            target.closeModalInterface()
            if (target.getInterfaceAt(InterfaceDestination.TAB_AREA) != -1) {
                target.closeInterface(InterfaceDestination.TAB_AREA)
            }
        }

        if (target is Player && !bankKeptOpen && !keepsWindowOpen(target) && target.interfaces.isVisible(740)) {
            if (target.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0) {
                target.interruptQueues()
                target.closeComponent(parent = 752, child = 13)
                target.attack(pawn, notifyRefusal = false) // auto-retaliate (never skulls: PvpSkull.isRetaliation)
            }
        }

        // Data-driven NPC attacks apply poison/venom on their sourced impact section. The old
        // generic fallback used an unverified 40% roll after every swing, could poison on a miss,
        // and double-applied effects beside a real impact rule. Until each remaining hand-written
        // definition has a sourced attack-impact entry, it must stay explicitly unimplemented;
        // a guessed chance is not OSRS parity.
    }

    fun postDamage(
        pawn: Pawn,
        target: Pawn,
    ) {
        // An npc's spell and a player's autocast spell are cleared here (loaded again each cycle). A manual cast already ends itself after its
        // swing (MagicCombatStrategy: the single cast); clearing here wiped the NEXT manual cast queued while the previous one
        // was still in flight, so that swing became a melee whack with the staff (intro scene 2026-10-08, barrage spam-clicks;
        // Novite PlayerCombat.mageAttack: a manual cast stops only its own attack).
        if (pawn.attr.has(CASTING_SPELL) && (pawn !is Player || pawn.attr[gg.rsmod.plugins.content.combat.magic.Autocast.AUTO_CAST] == true)) {
            pawn.attr.remove(CASTING_SPELL)
        }
        if (target.isDead()) {
            return
        }
        if (target.lock.canAttack()) {
            if (target.entityType.isNpc) {
                // RCV-005 root cause (owner 2026-09-13: "alle bosses en monsters focussen nu op mijn summoning monster
                // en negeren mij"): an npc switched to whoever hit it last, so a familiar's hit always stole it from
                // its owner. Void Combat.retaliate: an npc that is already attacking while under attack keeps its
                // target; it only retaliates when it has no living target of its own.
                val current = target.attr[COMBAT_TARGET_FOCUS_ATTR]?.get()
                val retaliates = current == null || current.isDead() || (current is Npc && !current.isSpawned())
                if (target is Npc && gg.rsmod.game.model.BreachTrace.watches(target.id)) {
                    gg.rsmod.game.model.BreachTrace.log("retaliate") {
                        "npc=${target.id}#${target.index} hitBy=${(pawn as? Player)?.username ?: pawn.toString()} current=${(current as? Player)?.username ?: current?.toString() ?: "-"} " +
                            "switch=$retaliates"
                    }
                }
                if (retaliates) {
                    target.attack(pawn, notifyRefusal = false) // npc retaliation
                }
            } else if (target is Player) {
                if (target.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0 &&
                    target.getCombatTarget() == null &&
                    !target.hasMoveDestination()
                ) {
                    // OSRS Wiki "Auto Retaliate": "When a player is attacked, their attack delay is set to their current weapon's
                    // attack speed divided by two, rounded up, regardless of what it was prior." The retaliation used to swing on
                    // the next tick (conformance combat retaliation, combat run 2026-09-29). This runs in the hit phase, after this
                    // tick's timer countdown, and the combat queue swings the tick after the delay is gone - so a delay of d swings
                    // d + 1 ticks after the hit; ceil(speed / 2) - 1 lands the swing ceil(speed / 2) ticks after it (whip: 2).
                    val retaliationTicks = (CombatConfigs.getAttackDelay(target) + 1) / 2
                    if (retaliationTicks > 1) target.timers[ATTACK_DELAY] = retaliationTicks - 1 else target.timers.remove(ATTACK_DELAY)
                    target.attack(pawn, notifyRefusal = false) // auto-retaliate (never skulls: PvpSkull.isRetaliation)
                }
            }
        }
    }

    fun getNpcXpMultiplier(npc: Npc): Double {
        val attackLvl = npc.stats.getMaxLevel(NpcSkills.ATTACK)
        val strengthLvl = npc.stats.getMaxLevel(NpcSkills.STRENGTH)
        val defenceLvl = npc.stats.getMaxLevel(NpcSkills.DEFENCE)
        val hitpoints = npc.getMaximumLifepoints()

        if (npc.name.contains("kolodion", ignoreCase = true)) {
            return 0.0
        }

        val averageLvl = Math.floor((attackLvl + strengthLvl + defenceLvl + hitpoints) / 4.0)
        val averageDefBonus =
            Math.floor(
                (
                    npc.getBonus(BonusSlot.DEFENCE_STAB) + npc.getBonus(BonusSlot.DEFENCE_SLASH) +
                        npc.getBonus(BonusSlot.DEFENCE_CRUSH)
                ) /
                    3.0,
            )
        return (
            1.0 +
                Math.floor(averageLvl * (averageDefBonus + npc.getStrengthBonus() + npc.getAttackBonus()) / 5120.0) /
                40.0
        ) *
            npc.combatDef.xpMultiplier
    }

    fun raycast(
        pawn: Pawn,
        target: Pawn,
        distance: Int,
        projectile: Boolean,
    ): Boolean {
        val world = pawn.world
        val start = pawn.tile
        val end = target.tile

        return start.isWithinRadius(end, distance) && world.collision.raycast(start, end, projectile = projectile)
    }

    /**
     * Owner 2026-09-18 ("noxious halberd can attack from far away tiles, max 2"): the combat cycle attacked whenever the
     * route had succeeded, even when the target stepped away during the last walked tile, so nothing ever re-checked the
     * real reach. OSRS melee reach, measured between the two bounding boxes: [range] 1 = an orthogonally adjacent tile
     * (never diagonal, never underneath); halberds (range 2) = any tile within 2, diagonals included, with a clear line.
     */
    fun inMeleeReach(
        pawn: Pawn,
        target: Pawn,
        range: Int,
    ): Boolean {
        val s = pawn.tile
        val t = target.tile
        if (s.height != t.height) return false
        val sSize = pawn.getSize()
        val tSize = target.getSize()
        val dx = maxOf(0, t.x - (s.x + sSize - 1), s.x - (t.x + tSize - 1))
        val dz = maxOf(0, t.z - (s.z + sSize - 1), s.z - (t.z + tSize - 1))
        if (dx == 0 && dz == 0) return false
        // N596 (2026-11-06): range 1 also needs a shared edge WITHOUT a wall between the bodies (OSRS: no melee over a wall, fence or
        // table edge) - the same MeleeReach rule the route search uses to "arrive". Geometry alone let a swing land through a wall.
        if (range <= 1) return gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(pawn.world.collision, s, sSize, sSize, t, tSize, tSize)
        return maxOf(dx, dz) <= range && pawn.world.collision.raycast(s, t, projectile = true)
    }

    suspend fun moveToAttackRange(
        it: QueueTask,
        pawn: Pawn,
        target: Pawn,
        distance: Int,
        projectile: Boolean,
    ): Boolean {
        val world = pawn.world
        val start = pawn.tile
        val end = target.tile

        val srcSize = pawn.getSize()
        val dstSize = Math.max(distance, target.getSize())

        val touching =
            if (distance >
                1
            ) {
                areOverlapping(start.x, start.z, srcSize, srcSize, end.x, end.z, dstSize, dstSize)
            } else {
                areBordering(start.x, start.z, srcSize, srcSize, end.x, end.z, dstSize, dstSize)
            }
        // N447 (owner 2026-10-30, "Summoning familiars kunnen in Pest Control de portals niet aanvallen"): a melee reach is a shared
        // edge without a wall (MeleeReach, Darkan checkMeleeStep - what the player's own melee and every follower use). The raycast
        // from south-west tile to south-west tile stepped INTO the target's own tile; a portal or other big npc standing on blocked
        // scenery tiles was then never "in range", and the route search beside it failed every tick ("no route" in the npcpath trace).
        val withinRange =
            touching &&
                if (distance == 1 && !projectile) {
                    gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(world.collision, start, srcSize, srcSize, end, target.getSize(), target.getSize())
                } else {
                    world.collision.raycast(start, end, projectile = projectile)
                }
        return withinRange || PawnPathAction.walkTo(it, pawn, target, interactionRange = distance, lineOfSight = false)
    }

    /** Distance in game squares (Chebyshev), as the OSRS hit-delay formulas count it. */
    fun tileDistance(
        a: Tile,
        b: Tile,
    ): Int = maxOf(Math.abs(a.x - b.x), Math.abs(a.z - b.z))

    /** OSRS Wiki "Hit delay": "edge-to-edge in game squares" between the two footprints (0 when they overlap). */
    fun tileDistance(
        a: Pawn,
        b: Pawn,
    ): Int {
        val dx = maxOf(0, b.tile.x - (a.tile.x + a.getSize() - 1), a.tile.x - (b.tile.x + b.getSize() - 1))
        val dz = maxOf(0, b.tile.z - (a.tile.z + a.getSize() - 1), a.tile.z - (b.tile.z + b.getSize() - 1))
        return maxOf(dx, dz)
    }

    fun getProjectileLifespan(
        source: Pawn,
        target: Tile,
        type: ProjectileType,
    ): Int =
        when (type) {
            ProjectileType.MAGIC, ProjectileType.FIERY_BREATH, ProjectileType.TELEKINETIC_GRAB -> {
                val fastPath = source.world.collision.raycastTiles(source.tile, target)
                5 + (fastPath * 10)
            }
            else -> {
                val distance = source.tile.getDistance(target)
                type.calculateLife(distance)
            }
        }

    /** The player [pawn] fights for: itself, or the owner credited with a familiar's damage (DAMAGE_CREDIT_ATTR). */
    private fun actingFor(pawn: Pawn): Player? = pawn as? Player ?: pawn.attr[gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR]?.get() as? Player

    /**
     * [a] and [b] fight on the same side: the same pawn, or a player and the familiar fighting for him (DAMAGE_CREDIT_ATTR).
     * Owner A9 2026-10-11: a combat familiar may join its owner's fight against an npc in single-way combat; the single-way
     * "already under attack" / "someone is already fighting this" rules count owner and familiar as one attacker, so the
     * familiar's hits never lock its own owner out of his fight.
     */
    fun sameSide(
        a: Pawn,
        b: Pawn,
    ): Boolean = a === b || actingFor(a)?.let { it === actingFor(b) } == true || bossSide(a) === bossSide(b) && bossSide(a) != null

    /**
     * N522 (testclient 2026-11-04): a spider boss and the spiderlings she summoned are one attacker for the single-way rules - in the
     * singles-plus Web Chasm her spiderlings join her target ("otherwise her mechanics are identical", OSRS Wiki Venenatis/Strategies),
     * and the player must still be able to fight Spindel and her spiderlings (no "I'm already under attack!").
     */
    private fun bossSide(pawn: Pawn): Npc? {
        val npc = pawn as? Npc ?: return null
        val spiders = gg.rsmod.plugins.content.areas.wilderness.bosses.VenenatisCombat
        val calvarion = gg.rsmod.plugins.content.areas.wilderness.bosses.CalvarionCombat
        return when (npc.id) {
            spiders.SPIDERLING, spiders.SPINDEL_SPIDERLING -> npc.attr[spiders.OWNER]
            spiders.NPC, spiders.SPINDEL -> npc
            // N522 nachtrun deel 2: the same in the singles-plus Skeletal Tomb - Calvar'ion and his hellhounds are one attacker.
            calvarion.HOUND, calvarion.GREATER_HOUND -> npc.attr[calvarion.OWNER]
            calvarion.NORMAL, calvarion.ENRAGED -> npc
            else -> null
        }
    }

    /**
     * The Slayer gate (one rule for every attack route): an npc with a Slayer requirement above the player's Slayer level cannot be
     * wounded. The primary target gets [SLAYER_LEVEL_MESSAGE] (combat.plugin.kts); an area attack's other victims are skipped silently
     * (SpecialAttackSupport.canSplash; owner 2026-10-01: "splash slaat monsters over die je niet mag aanvallen", ROOT_CAUSES #115).
     */
    fun slayerLevelTooLow(
        player: Player,
        npc: Npc,
    ): Boolean = npc.combatDef.slayerReq > player.skills.getMaxLevel(Skills.SLAYER)

    const val SLAYER_LEVEL_MESSAGE = "You need a higher Slayer level to know how to wound this monster."

    fun canEngage(
        pawn: Pawn,
        target: Pawn,
    ): Boolean {
        if (pawn.isDead() || target.isDead() || pawn.invisible || target.invisible) {
            return false
        }

        val maxDistance =
            when {
                pawn is Player && pawn.hasLargeViewport() -> Player.LARGE_VIEW_DISTANCE
                else -> Player.NORMAL_VIEW_DISTANCE
            }
        if (!pawn.tile.isWithinRadius(target.tile, maxDistance)) {
            return false
        }

        val pvp = pawn.entityType.isPlayer && target.entityType.isPlayer

        // The low-level gate is also used by instant specials and manual spells, before resources are consumed.
        if (pawn is Player && target is Player && SinglesPlus.contains(pawn.tile) && SinglesPlus.contains(target.tile) &&
            (pawn.isAttackedByOtherThan(target) || target.isAttackedByOtherThan(pawn))) return false

        if (pawn is Player) {
            if (!pawn.isOnline) {
                return false
            }

            if (pawn.invisible && pvp) {
                pawn.message("You can't attack while invisible.")
                return false
            }
        } else if (pawn is Npc) {
            if (!pawn.isSpawned()) {
                return false
            }
        }

        if (target is Npc) {
            if (!target.isSpawned()) {
                return false
            }
            // Deadman guards (OSRS Wiki): "can only be attacked inside the guarded area while
            // skulled"; any other attempt - and every attempt on the unattackable Wizguard - gets
            // "You probably don't want to do that." on every route (click, spell, special, queue).
            if (pawn is Player && CityGuards.isGuard(target) && !CityGuards.mayBeAttackedBy(target, pawn)) {
                pawn.message(CityGuards.ATTACK_REFUSED_MESSAGE)
                return false
            }
            // The same rule for a pawn fighting for a player (a familiar, DAMAGE_CREDIT_ATTR): the owner may not send it at a
            // guard he may not attack himself.
            val owner = actingFor(pawn)
            if (pawn !is Player && owner != null && CityGuards.isGuard(target) && !CityGuards.mayBeAttackedBy(target, owner)) {
                return false
            }
            // Summoning familiars have no player-facing cache "Attack" option, but a public
            // familiar is still a valid PvP target in a multi-way area. The owner may never
            // attack their own familiar, and single-way combat must not open a second target.
            // `publicOwner` is the existing familiar visibility marker; private owner-bound NPCs
            // keep the ordinary cache-option gate below.
            val publicFamiliar = target.owner != null && target.publicOwner
            if (publicFamiliar && pawn is Player) {
                if (target.owner === pawn || !pawn.tile.isMulti(pawn.world) || !target.tile.isMulti(pawn.world)) {
                    if (pawn.world.plugins.notifyAttackRefusal) pawn.message("You can't attack this npc.")
                    return false
                }
            }
            // RCV-005 root cause: the cache "Attack" menu option is what lets a *player* attack an npc.
            // It was applied to every attacker, so an npc could never fight back against a summoned
            // familiar (a familiar deliberately has no "Attack" option). Void `Target.attackable:56-63`:
            // ordinary players need the option; public familiars are the explicit multi-combat
            // exception above, while npc attackers may still fight an owned familiar.
            val npcOnlyTarget = pawn !is Player && target.attr[NPC_ONLY_TARGET_ATTR] == true
            // N24: a player is gated on the definition his client shows (the transmog's), an npc attacker on the base one.
            val gateDef = if (pawn is Player) attackOptionDef(target) else target.def
            if ((!gateDef.isAttackable() && !publicFamiliar && !npcOnlyTarget && (pawn is Player || target.owner == null)) ||
            target.combatDef.lifepoints == -1) {
                // Owner 2026-09-24: players only ever see the game message, never internal npc ids.
                (pawn as? Player)?.message("You can't attack this npc.")
                return false
            }

        } else if (target is Player) {
            if (!target.isOnline || target.invisible) {
                return false
            }

            if (!target.lock.canBeAttacked()) {
                return false
            }

            if (pvp) {
                pawn as Player

                // Deadman PvP guards plan: the flat combat-level range (AreaState.MAX_COMBAT_LEVEL_DIFFERENCE, owner +/-14) now
                // lives in the shared AreaState.canPlayersFight gate (every attack entrypoint
                // inherits it), checked here first only so this specific, more helpful message
                // fires instead of the generic "can't attack players here" one when the range is
                // the actual reason. Superseded the old per-tile Wilderness-level-scaled formula.
                if (!PracticePvp.areMatched(pawn, target)) {
                    // Deadman (owner 2026-09-17): inside a guarded city the guards' message wins over
                    // every other reason - the zone is what forbids the attack there.
                    val home = pawn.world.gameContext.home
                    if (!AreaState.isPvpAllowed(pawn.zoneTile(), home) || !AreaState.isPvpAllowed(target.zoneTile(), home)) {
                        pawn.message(AreaState.SAFE_ZONE_ATTACK_MESSAGE)
                        return false
                    }
                    if (!AreaState.isWithinCombatLevelRange(pawn, target)) {
                        pawn.message("The level difference between you and your opponent is too great.")
                        return false
                    }
                }

                if (!AreaState.canPlayersFight(pawn, target)) {
                    if (KillGrace.isProtected(target)) {
                        pawn.message("That player has just won a fight and can't be attacked for a moment.")
                    } else {
                        pawn.message("You can't attack players here.")
                    }
                    return false
                }
            } else if (pawn is Npc && actingFor(pawn) != null) {
                // A familiar fighting for its owner obeys the owner's PvP gate (guarded zone, level bracket, kill grace): its hits
                // were cancelled in dealHit, but it kept attacking.
                if (!AreaState.canPlayersFight(actingFor(pawn)!!, target)) {
                    return false
                }
            } else if (pawn is Npc && CityGuards.isGuard(pawn)) {
                // Deadman PvP guards plan (2026-09-16): re-checked every combat cycle (this
                // function, not only at initial aggro pick), so a guard stops attacking the
                // instant the target is no longer skulled or leaves the guarded zone.
                if (!CityGuards.mayAttack(pawn, target)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getStrategy(combatClass: CombatClass): CombatStrategy =
        when (combatClass) {
            CombatClass.MELEE -> MeleeCombatStrategy
            CombatClass.RANGED -> RangedCombatStrategy
            CombatClass.MAGIC -> MagicCombatStrategy
        }

    private fun areOverlapping(
        x1: Int,
        z1: Int,
        width1: Int,
        length1: Int,
        x2: Int,
        z2: Int,
        width2: Int,
        length2: Int,
    ): Boolean {
        val a = Box(x1, z1, width1 - 1, length1 - 1)
        val b = Box(x2, z2, width2 - 1, length2 - 1)

        if (a.x1 > b.x2 || b.x1 > a.x2) {
            return false
        }

        if (a.z1 > b.z2 || b.z1 > a.z2) {
            return false
        }

        return true
    }

    /**
     * Checks to see if two AABB are bordering, but not overlapping.
     */
    fun areBordering(
        x1: Int,
        z1: Int,
        width1: Int,
        length1: Int,
        x2: Int,
        z2: Int,
        width2: Int,
        length2: Int,
    ): Boolean {
        val a = Box(x1, z1, width1 - 1, length1 - 1)
        val b = Box(x2, z2, width2 - 1, length2 - 1)

        if (b.x1 in a.x1..a.x2 && b.z1 in a.z1..a.z2 || b.x2 in a.x1..a.x2 && b.z2 in a.z1..a.z2) {
            return false
        }

        if (b.x1 > a.x2 + 1) {
            return false
        }

        if (b.x2 < a.x1 - 1) {
            return false
        }

        if (b.z1 > a.z2 + 1) {
            return false
        }

        if (b.z2 < a.z1 - 1) {
            return false
        }
        return true
    }

    data class Box(
        val x: Int,
        val z: Int,
        val width: Int,
        val length: Int,
    ) {
        val x1: Int get() = x

        val x2: Int get() = x + width

        val z1: Int get() = z

        val z2: Int get() = z + length
    }
}
