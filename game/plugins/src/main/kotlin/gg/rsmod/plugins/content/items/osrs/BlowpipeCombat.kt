package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.refreshBonuses
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.combat.venom

/** Firing side of the Toxic blowpipe (see [Blowpipe] for the sourced charge rules). */
object BlowpipeCombat {
    private const val WEAPON_SLOT = 3

    /**
     * OSRS Wiki "Hit delay": thrown weapons and the blowpipe use 1 + floor(distance / 6); Toxic Siphon hits after 2 ticks
     * at distance 4 or 5. This server schedules ranged hits one tick after the wiki count (its bow delay is
     * 2 + floor((3 + distance) / 6) against the wiki's 1 + ...), and the same offset is kept here.
     */
    fun hitDelay(
        distance: Int,
        special: Boolean,
    ): Int = (if (special && distance in 4..5) 2 else 1 + distance / 6) + 1

    /**
     * Fires one blowpipe shot when a charged blowpipe is wielded: the loaded dart's projectile (667 dart graphics), the
     * thrown sound, and the charge cost of the shot. Returns false when no blowpipe with darts is wielded.
     */
    fun fire(
        player: Player,
        target: Pawn,
    ): Boolean {
        val pipe = player.getEquipment(EquipmentType.WEAPON)?.takeIf { it.id == Items.TOXIC_BLOWPIPE } ?: return false
        val dart = Blowpipe.dart(pipe) ?: return false
        val world = player.world
        RangedProjectile.values.firstOrNull { dart.itemId in it.items }?.let { world.spawn(player.createProjectile(target, it.gfx, it.type)) }
        player.playSound(Sfx.THROWN)
        val cost = Blowpipe.spendShot(pipe, world.randomDouble(), world.randomDouble(), player.getEquipment(EquipmentType.CAPE)?.id)
        player.equipment[WEAPON_SLOT] = cost.result
        if (cost.result.id != pipe.id) player.refreshBonuses()
        return true
    }

    /** OSRS Wiki "Toxic blowpipe": a 25% chance of inflicting venom per attack. */
    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        if (player.world.randomDouble() < Blowpipe.VENOM_CHANCE) target.venom()
    }
}
