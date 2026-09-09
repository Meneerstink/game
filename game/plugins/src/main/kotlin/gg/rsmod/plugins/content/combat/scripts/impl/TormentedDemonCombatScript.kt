package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * Tormented demons (npcs 8349-8351...).
 *
 * Attack ids ported from the Matrix 718 TormentedDemonCombat script (melee 10922 + gfx 1886,
 * magic 10918 + gfx 1883 + projectile 1884, ranged 10919 + gfx 1888 + projectile 1887); the
 * encounter rules follow the 2011 wiki:
 *  - the demon attacks with one style for ~5 attacks, then switches to another style;
 *  - it prays against the style that last damaged it (overhead icon), taking full damage
 *    only from the other styles;
 *  - its fire shield absorbs 75% of all damage until it is hit with Darklight/Silverlight,
 *    which lowers the shield for 60 seconds.
 * Max hits (2011): melee 18, magic 27, ranged 27.
 */
object TormentedDemonCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.TORMENTED_DEMON, Npcs.TORMENTED_DEMON_8350, 8351, 8352, 8353, 8354, 8355, 8356, 8357, 8358, 8359, 8360, 8361, 8362, 8363, 8364, 8365, 8366, 8367, 8368, 8369)

    val STYLE = AttributeKey<Int>()
    val ATTACKS_IN_STYLE = AttributeKey<Int>()
    val SHIELD_DOWN_UNTIL = AttributeKey<Int>()
    val PRAYING_AGAINST = AttributeKey<Int>()

    private const val MELEE_MAX = 18.0
    private const val MAGIC_MAX = 27.0
    private const val RANGED_MAX = 27.0

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            var style = npc.attr[STYLE] ?: world.random(2).also { npc.attr[STYLE] = it }
            var count = npc.attr[ATTACKS_IN_STYLE] ?: 0
            if (count >= 5) {
                style = (style + 1 + world.random(1)) % 3
                npc.attr[STYLE] = style
                count = 0
            }
            npc.attr[ATTACKS_IN_STYLE] = count + 1

            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            when (style) {
                0 -> {
                    if (distance <= 2 || npc.moveToAttackRange(it, target, distance = 2, projectile = false)) {
                        melee(npc, target)
                    } else {
                        magic(npc, target)
                    }
                }
                1 -> magic(npc, target)
                else -> ranged(npc, target)
            }

            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun melee(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(10922)
        npc.graphic(1886)
        val landHit = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MELEE_MAX, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    }

    private fun magic(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(10918)
        npc.graphic(1883, 96)
        npc.world.spawn(npc.createProjectile(target, 1884, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAGIC_MAX, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    }

    private fun ranged(
        npc: Npc,
        target: Pawn,
    ) {
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(10919)
        npc.graphic(1888)
        npc.world.spawn(npc.createProjectile(target, 1887, ProjectileType.ARROW))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = RANGED_MAX, landHit = landHit, delay = delay, hitType = HitType.RANGE)
    }

    /**
     * Incoming-damage rule, applied from the damage pipeline: the demon's shield absorbs 75% of
     * damage while up, and it prays against the last style that hurt it.
     */
    fun modifyIncomingDamage(
        npc: Npc,
        attacker: Pawn,
        style: CombatClass,
        damage: Int,
        weaponId: Int,
    ): Int {
        val world = npc.world
        var result = damage
        val praying = npc.attr[PRAYING_AGAINST]
        if (praying != null && praying == style.ordinal) {
            result = 0
            if (attacker is Player) attacker.filterableMessage("The demon is protecting itself against your attack style.")
        }
        val shieldDown = (npc.attr[SHIELD_DOWN_UNTIL] ?: 0) > world.currentCycle
        if (weaponId == gg.rsmod.plugins.api.cfg.Items.DARKLIGHT || weaponId == gg.rsmod.plugins.api.cfg.Items.SILVERLIGHT) {
            npc.attr[SHIELD_DOWN_UNTIL] = world.currentCycle + 100
            if (attacker is Player) attacker.message("The demon is temporarily weakened by your weapon.")
        } else if (!shieldDown) {
            result = result / 4
        }
        if (result > 0) {
            // NPC overhead prayer icons are not part of this server's npc sync, so the switch is
            // announced in chat instead of drawn above the demon.
            if (npc.attr[PRAYING_AGAINST] != style.ordinal && attacker is Player) {
                attacker.filterableMessage("The demon starts praying against your attack style.")
            }
            npc.attr[PRAYING_AGAINST] = style.ordinal
        }
        return result
    }
}
