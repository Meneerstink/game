package gg.rsmod.plugins.content.items.helios

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.strategy.CombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy

/**
 * Crown of Helios - the staff-only "yellow partyhat" (item 22327, a dual-cache clone of item 1040).
 *
 * While an ADMIN_POWER player wears it, their auto-attacks are driven by [CrownOfHeliosCombatStrategy]
 * instead of the weapon-derived melee/ranged/magic strategies (see the three hooks in
 * [CombatConfigs]): one attack per tick, a 10-tile reach in every mode, and every hit lands for
 * the selected [Power]. The mode and power are chosen from the crown's right-click menu
 * (`crown_of_helios.plugin.kts`). Nothing here applies to a player without admin power, even if
 * they somehow hold the item - [isActive] gates every hook.
 */
object CrownOfHelios {
    const val ITEM = Items.CROWN_OF_HELIOS

    /** Attack style the crown fights with; drives the hit type, animation and projectile. */
    enum class Mode(
        val label: String,
        val combatClass: CombatClass,
    ) {
        MELEE("Melee", CombatClass.MELEE),
        RANGED("Ranged", CombatClass.RANGED),
        MAGIC("Magic", CombatClass.MAGIC),
    }

    /** Damage per hit in the server's 1:1 lifepoint unit. */
    enum class Power(
        val label: String,
        val maxHit: Double,
    ) {
        LIGHT("Light (10s)", 10.0),
        HEAVY("Heavy (50s)", 50.0),
        OVERKILL("Overkill (100s)", 100.0),
        OBLITERATE("Obliterate (one-shot)", -1.0),
    }

    val MODE_ATTR = AttributeKey<Mode>()
    val POWER_ATTR = AttributeKey<Power>()

    /** A named tile the crown's Teleport menu can jump back to (a favorite or a recent trip). */
    data class SavedLocation(val label: String, val tile: Tile)

    /** Admin-saved teleport shortcuts (Teleport > Favorites), kept for the session. */
    val FAVORITES_ATTR = AttributeKey<MutableList<SavedLocation>>()

    /** Auto-tracked last few Crown teleports (Teleport > Recent), newest first. */
    val RECENT_ATTR = AttributeKey<MutableList<SavedLocation>>()

    /** Ring buffer of the last Crown dev-tool actions (AV Tester plays, spawns, etc). */
    val LAST_ACTIONS_ATTR = AttributeKey<MutableList<String>>()

    /**
     * Player-state snapshot (Dev Tools > Snapshot/Restore) - deliberately position/HP/run/skill
     * levels only, never inventory or equipment: a bulk raw-slot restore of those risks an item
     * duplication bug, which this dev tool must never be able to cause.
     */
    data class Snapshot(
        val tile: Tile,
        val hp: Int,
        val runEnergy: Double,
        val levels: IntArray,
    )

    val SNAPSHOT_ATTR = AttributeKey<Snapshot>()

    fun mode(player: Player): Mode = player.attr[MODE_ATTR] ?: Mode.MELEE

    fun power(player: Player): Power = player.attr[POWER_ATTR] ?: Power.OVERKILL

    fun isAdmin(player: Player): Boolean = player.world.privileges.isEligible(player.privilege, Privilege.ADMIN_POWER)

    /** True when the crown is worn by a player who is allowed to use it. */
    fun isActive(pawn: Pawn): Boolean =
        pawn is Player && pawn.getEquipment(EquipmentType.HEAD)?.id == ITEM && isAdmin(pawn)

    /** The max hit this attack should roll, resolved against the target for [Power.OBLITERATE]. */
    fun maxHitAgainst(
        player: Player,
        target: Pawn,
    ): Double {
        val power = power(player)
        if (power != Power.OBLITERATE) {
            return power.maxHit
        }
        return target.getCurrentLifepoints().toDouble() + 1.0
    }
}

object CrownOfHeliosCombatStrategy : CombatStrategy {
    private const val ATTACK_RANGE = 10

    override fun getAttackRange(pawn: Pawn): Int = ATTACK_RANGE

    override fun canAttack(
        pawn: Pawn,
        target: Pawn,
    ): Boolean = true

    override fun attack(
        pawn: Pawn,
        target: Pawn,
    ) {
        val player = pawn as? Player ?: return
        val world = player.world
        val maxHit = CrownOfHelios.maxHitAgainst(player, target)
        // dealHit rolls Random.nextDouble(minHit, maxHit), which needs minHit < maxHit; a 0.1
        // window keeps every hit at the chosen max (99..100 lifepoints for Overkill).
        val minHit = maxHit - 0.1

        when (CrownOfHelios.mode(player)) {
            CrownOfHelios.Mode.MELEE -> {
                player.animate(Anims.ATTACK_GODSWORD_SLASH)
                target.animate(CombatConfigs.getBlockAnimation(target), priority = false)
                player.dealHit(target = target, minHit = minHit, maxHit = maxHit, landHit = true, delay = 1, hitType = HitType.MELEE)
            }
            CrownOfHelios.Mode.RANGED -> {
                player.animate(Anims.ATTACK_BOW)
                player.graphic(Gfx.DRAGON_ARROW_DRAWBACK, 96)
                world.spawn(player.createProjectile(target, Gfx.DRAGON_ARROW_IN_FLIGHT, ProjectileType.ARROW))
                val delay = RangedCombatStrategy.getHitDelay(player.getCentreTile(), target.getCentreTile())
                player.dealHit(target = target, minHit = minHit, maxHit = maxHit, landHit = true, delay = delay, hitType = HitType.RANGE)
            }
            CrownOfHelios.Mode.MAGIC -> {
                player.graphic(Gfx.FIRE_SPELL_CAST, 22)
                player.animate(Anims.FIRE_SPELL)
                val projectile = player.createProjectile(target, Gfx.FIRE_SURGE_PROJ, ProjectileType.MAGIC)
                world.spawn(projectile)
                target.graphic(Gfx.FIRE_SURGE_IMPACT, 32, projectile.lifespan)
                val delay = MagicCombatStrategy.getHitDelay(player.getCentreTile(), target.getCentreTile())
                player.dealHit(target = target, minHit = minHit, maxHit = maxHit, landHit = true, delay = delay, hitType = HitType.MAGIC)
            }
        }
    }
}
