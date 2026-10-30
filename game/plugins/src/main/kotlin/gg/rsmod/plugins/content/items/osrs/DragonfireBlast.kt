package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula
import gg.rsmod.plugins.content.combat.formula.Draconic
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR

/**
 * K12 N11 (dead option "Activate" on the worn Dragonfire shield, census 2026-10-30): the shield's dragonfire blast. OSRS Wiki
 * "Dragonfire shield" (fetched 2026-10-30): activated from the worn shield it expends one charge and unleashes a blast "hitting up
 * to 25 damage"; "The accuracy roll is based on the player's Defence level, which is rolled against an opponent's Magic defence";
 * "a cooldown of about 1 minute and 56 seconds" that relogging does not skip; range 10 tiles, over obstacles; against a target with
 * an anti-dragon shield "the max hit of the dragonfire blast will be reduced to 3"; an antifire potion lowers it further; "The use
 * of Protect from Magic does not help"; it "will not work against chromatic dragons, metal dragons, wyverns, frost dragons".
 * Look and sound: Void (`dragonfire_shield_discharge`): animation 6696, cast graphic 1165, projectile 1166, impact 1167, sound 3761.
 *
 * ADAPTED (the wiki gives no figure): the attack roll is (Defence level + 9) x 64, no equipment bonus; with an anti-dragon shield
 * AND an antifire (or a super antifire alone) the max is 0; an antifire alone takes the max to 17 (the 35/50 of the OSRS dragonfire
 * table's antifire row); messages are Void's.
 */
object DragonfireBlast {
    const val MAX_HIT = 25
    const val SHIELDED_MAX_HIT = 3
    const val ANTIFIRE_MAX_HIT = 17
    const val RANGE = 10

    /** "about 1 minute and 56 seconds" = 116 s = 193 game ticks; persisted so a relog does not skip it. */
    const val COOLDOWN_TICKS = 193
    val COOLDOWN = TimerKey(persistenceKey = "dragonfire_blast_cooldown", tickOffline = true)

    const val ANIMATION = 6696
    const val CAST_GFX = 1165
    const val PROJECTILE_GFX = 1166
    const val IMPACT_GFX = 1167

    const val NO_CHARGES = "Your shield has no charges left."
    const val RECHARGING = "Your dragonfire shield is still recharging."
    const val NOT_IN_COMBAT = "You can only unleash the shield's dragonfire during combat."
    const val TOO_FAR = "You are too far away from your target."

    /** The max hit against [target]: draconic npcs are immune; a player's own dragonfire protection lowers it. */
    fun maxHit(target: Pawn): Int {
        if (target is Npc) return if (Draconic.isDraconic(target)) 0 else MAX_HIT
        val player = target as Player
        val protection = DragonfireFormula.protectionOf(player)
        if (protection.immune) return 0
        val superAntifire = player.timers.has(SUPER_ANTIFIRE_TIMER)
        val antifire = superAntifire || player.timers.has(ANTIFIRE_TIMER)
        return when {
            protection.shield && antifire -> 0
            protection.shield -> SHIELDED_MAX_HIT
            superAntifire -> 0
            antifire -> ANTIFIRE_MAX_HIT
            else -> MAX_HIT
        }
    }

    /** Hit chance: the player's Defence-level roll against the target's Magic defence roll (the magic formula's own). */
    fun accuracy(
        player: Player,
        target: Pawn,
    ): Double {
        val attack = (player.skills.getCurrentLevel(Skills.DEFENCE) + 9) * 64.0
        val defence =
            when (target) {
                is Player -> MagicCombatFormula.getDefenceRoll(target).toDouble()
                is Npc -> MagicCombatFormula.getDefenceRoll(player, target).toDouble()
                else -> 0.0
            }
        return if (attack > defence) 1.0 - (defence + 2.0) / (2.0 * (attack + 1.0)) else attack / (2.0 * (defence + 1.0))
    }

    /** Why [player] cannot fire now, or null when the blast may go. */
    fun refusal(
        player: Player,
        charges: Int,
        target: Pawn?,
    ): String? =
        when {
            charges <= 0 -> NO_CHARGES
            player.timers.has(COOLDOWN) -> RECHARGING
            target == null || target.isDead() || !Combat.canEngage(player, target) -> NOT_IN_COMBAT
            Combat.tileDistance(player, target) > RANGE -> TOO_FAR
            else -> null
        }

    /** The worn shield's Activate: spends one charge and fires at the player's current combat target. */
    fun activate(player: Player) {
        val shield = player.getEquipment(EquipmentType.SHIELD) ?: return
        val family = DragonfireShield.familyOf(shield.id)
        val charges = if (family == null) 0 else DragonfireShield.charges(shield)
        val target = player.attr[COMBAT_TARGET_FOCUS_ATTR]?.get()
        refusal(player, charges, target)?.let {
            player.message(it)
            return
        }
        target!!
        player.equipment[EquipmentType.SHIELD.id] = DragonfireShield.withCharges(shield, charges - 1)
        player.timers[COOLDOWN] = COOLDOWN_TICKS
        player.animate(ANIMATION)
        player.graphic(CAST_GFX)
        player.playSound(Sfx.DRAGONSLAYER_SHIELDFIRE)
        val projectile = player.createProjectile(target, gfx = PROJECTILE_GFX, type = ProjectileType.MAGIC)
        player.world.spawn(projectile)
        val delay = MagicCombatStrategy.getHitDelay(player, target)
        val max = maxHit(target)
        val land = max > 0 && accuracy(player, target) >= player.world.randomDouble()
        if (land) target.graphic(IMPACT_GFX, height = 100, delay = projectile.lifespan)
        player.dealHit(target = target, maxHit = max.toDouble(), landHit = land, delay = delay, hitType = HitType.MAGIC)
    }

    /** The two 667 cache ids that both carry the worn "Activate" (the charged model and the old uncharged id). */
    val SHIELDS = intArrayOf(gg.rsmod.plugins.api.cfg.Items.DRAGONFIRE_SHIELD, gg.rsmod.plugins.api.cfg.Items.DRAGONFIRE_SHIELD_11284)
}
