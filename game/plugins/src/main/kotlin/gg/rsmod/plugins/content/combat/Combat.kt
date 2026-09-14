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
import gg.rsmod.plugins.api.BonusSlot
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
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull
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
        // Granite maul homing: "for 5 ticks after attacking a target with any weapon" (GraniteMaul).
        pawn.attr[gg.rsmod.plugins.content.items.osrs.GraniteMaul.LAST_ATTACK_CYCLE] = pawn.world.currentCycle
        target.timers[ACTIVE_COMBAT_TIMER] = 17 // 10,2 seconds
        pawn.attr[BOLT_ENCHANTMENT_EFFECT] = false

        pawn.attr[LAST_HIT_ATTR] = WeakReference(target)
        target.attr[LAST_HIT_BY_ATTR] = WeakReference(pawn)

        if (pawn is Player && target is Player) {
            PvpSkull.markAggression(attacker = pawn, victim = target)
        }

        if (target is Player && target.interfaces.getModal() !in COMBAT_PERSISTENT_MODALS) {
            target.closeInterface(target.interfaces.getModal())
            target.interfaces.setModal(-1)
        }

        if (target is Player && target.interfaces.isVisible(740)) {
            if (target.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0) {
                target.interruptQueues()
                target.closeComponent(parent = 752, child = 13)
                target.attack(pawn, notifyRefusal = false) // auto-retaliate: never a player-started attack
            }
        }

        // TODO: Find proper poison/venom chances - both currently reuse the same
        // unverified placeholder roll. Venom takes priority (real RS rule: a pawn is
        // never simultaneously poisoned and envenomed, and venom is the stronger effect).
        if (pawn is Npc) {
            if (pawn.combatDef.venomDamage > 0 && pawn.world.random(10) < 4) {
                target.venom()
            } else if (pawn.combatDef.poisonDamage > 0 && pawn.world.random(10) < 4) {
                target.poison(pawn.combatDef.poisonDamage)
            }
        }
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
                    target.attack(pawn, notifyRefusal = false) // auto-retaliate
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
            // RCV-005 root cause: the cache "Attack" menu option is what lets a *player* attack an npc.
            // It was applied to every attacker, so an npc could never fight back against a summoned
            // familiar (a familiar deliberately has no "Attack" option). Void `Target.attackable:56-63`:
            // players need the option; npc attackers need it too unless the target is an owned familiar.
            if ((!target.def.isAttackable() && (pawn is Player || target.owner == null)) ||
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

                if (!AreaState.canPlayersFight(pawn, target)) {
                    pawn.message("You can't attack players here.")
                    return false
                }

                // R03.3: the level-difference range is a Wilderness-specific mechanic (it scales
                // with Wilderness level); global PvP outside the Wilderness (R03.1) has no such
                // restriction, matching this codebase's other non-Wilderness PvP (Clan Wars etc).
                val wildLvl = pawn.tile.getWildernessLevel()
                if (wildLvl > 0) {
                    val combatLvlRange = getValidCombatLvlRange(pawn, wildLvl)
                    if (target.combatLevel !in combatLvlRange) {
                        pawn.message("The level difference between you and your opponent is too great.")
                        return false
                    }
                }
            }
        }
        return true
    }


    fun getValidCombatLvlRange(player: Player, wildLvl: Int): IntRange {
        val minLvl = Math.max(Skills.MIN_COMBAT_LVL, player.combatLevel - wildLvl)
        val maxLvl = Math.min(Skills.MAX_COMBAT_LVL, player.combatLevel + wildLvl)
        return minLvl..maxLvl
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
