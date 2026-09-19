package gg.rsmod.plugins.content.combat

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
import gg.rsmod.plugins.content.mechanics.pvp.KillGrace
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
import gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp
import java.lang.ref.WeakReference

/**
 * @author Tom <rspsmods@gmail.com>
 */
object Combat {
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
    private val COMBAT_PERSISTENT_MODALS = intArrayOf(-1, 671)

    val CASTING_SPELL = AttributeKey<CombatSpell>()
    val DAMAGE_DEAL_MULTIPLIER = AttributeKey<Double>()
    val DAMAGE_TAKE_MULTIPLIER = AttributeKey<Double>()
    val BOLT_ENCHANTMENT_EFFECT = AttributeKey<Boolean>()

    const val PRIORITY_PID_VARP = 1075

    const val SELECTED_AUTOCAST_VARP = 108
    const val DEFENSIVE_CAST_VARP = 439

    fun reset(pawn: Pawn) {
        pawn.attr.remove(COMBAT_TARGET_FOCUS_ATTR)
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

    fun isAttackDelayReady(pawn: Pawn): Boolean = !pawn.timers.has(ATTACK_DELAY)

    fun postAttack(
        pawn: Pawn,
        target: Pawn,
    ) {
        pawn.timers[ATTACK_DELAY] = CombatConfigs.getAttackDelay(pawn)
        // Blood moon armour Bloodrager: the dual macuahuitl attacks one tick earlier after a trigger (MoonSets).
        if (pawn.attr[gg.rsmod.plugins.content.items.osrs.MoonSets.BLOODRAGER] == true) {
            pawn.attr.remove(gg.rsmod.plugins.content.items.osrs.MoonSets.BLOODRAGER)
            pawn.timers[ATTACK_DELAY] = maxOf(1, CombatConfigs.getAttackDelay(pawn) - 1)
        }
        // Granite maul homing: "for 5 ticks after attacking a target with any weapon" (GraniteMaul).
        pawn.attr[gg.rsmod.plugins.content.items.osrs.GraniteMaul.LAST_ATTACK_CYCLE] = pawn.world.currentCycle
        target.timers[ACTIVE_COMBAT_TIMER] = 17 // 10,2 seconds
        // Deadman PvP guards plan (2026-09-16): the out-of-combat teleport gate's own 7-second
        // window, and "being attacked" cancels an in-progress 7-second logout/teleport/portal/
        // transport countdown.
        target.timers[TELEPORT_COMBAT_TIMER] = gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.DURATION_CYCLES
        if (target is Player) {
            gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction.cancel(target, "You have been attacked!")
        }
        pawn.attr[BOLT_ENCHANTMENT_EFFECT] = false

        pawn.attr[LAST_HIT_ATTR] = WeakReference(target)
        target.attr[LAST_HIT_BY_ATTR] = WeakReference(pawn)

        if (pawn is Player && target is Player) {
            PvpSkull.markAggression(attacker = pawn, victim = target)
        }
        // Toxic staff of the dead: 10 scales on entering combat and every further minute in combat (StaffOfTheDead).
        (pawn as? Player)?.let { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.onCombat(it) }
        (target as? Player)?.let { gg.rsmod.plugins.content.items.osrs.StaffOfTheDead.onCombat(it) }

        if (target is Player && target.interfaces.getModal() != -1 && target.interfaces.getModal() !in COMBAT_PERSISTENT_MODALS) {
            // Close the modal together with its tab-area side panel (equipment stats 670, bank 763, shop 621, ...): closing
            // only the modal left the side panel mounted over a hidden tab strip (owner picture "interface hang").
            target.closeModalInterface()
            if (target.getInterfaceAt(InterfaceDestination.TAB_AREA) != -1) {
                target.closeInterface(InterfaceDestination.TAB_AREA)
            }
        }

        if (target is Player && target.interfaces.isVisible(740)) {
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
        if (pawn.attr.has(CASTING_SPELL)) {
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
                if (current == null || current.isDead() || (current is Npc && !current.isSpawned())) {
                    target.attack(pawn, notifyRefusal = false) // npc retaliation
                }
            } else if (target is Player) {
                if (target.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0 &&
                    target.getCombatTarget() == null &&
                    !target.hasMoveDestination()
                ) {
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
        if (range <= 1) return (dx == 1 && dz == 0) || (dx == 0 && dz == 1)
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
        val withinRange = touching && world.collision.raycast(start, end, projectile = projectile)
        return withinRange || PawnPathAction.walkTo(it, pawn, target, interactionRange = distance, lineOfSight = false)
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
            // Deadman guards are never attackable by players (owner 2026-09-17; the cache "Attack"
            // option is stripped, this closes the spell / special / queued-attack routes with the
            // owner's message instead of the generic "missing combat definitions" text).
            if (pawn is Player && CityGuards.isGuard(target)) {
                pawn.message(CityGuards.ATTACK_REFUSED_MESSAGE)
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
            if ((!target.def.isAttackable() && !publicFamiliar && (pawn is Player || target.owner == null)) ||
            target.combatDef.lifepoints == -1) {
                (pawn as? Player)?.message("You can't attack this npc.")
                (pawn as? Player)?.message(
                    "Npc ID: ${target.def.id} is missing combat definitions, please report this on Discord.",
                )
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

                // Deadman PvP guards plan (2026-09-16): the flat +/-12 combat-level range now
                // lives in the shared AreaState.canPlayersFight gate (every attack entrypoint
                // inherits it), checked here first only so this specific, more helpful message
                // fires instead of the generic "can't attack players here" one when the range is
                // the actual reason. Superseded the old per-tile Wilderness-level-scaled formula.
                if (!PracticePvp.areMatched(pawn, target)) {
                    // Deadman (owner 2026-09-17): inside a guarded city the guards' message wins over
                    // every other reason - the zone is what forbids the attack there.
                    val home = pawn.world.gameContext.home
                    if (!AreaState.isPvpAllowed(pawn.tile, home) || !AreaState.isPvpAllowed(target.tile, home)) {
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
