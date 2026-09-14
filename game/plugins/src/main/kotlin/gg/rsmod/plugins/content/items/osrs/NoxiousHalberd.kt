package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.POISON_TICKS_LEFT_ATTR
import gg.rsmod.game.model.attr.VENOM_TICKS_ELAPSED_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.venom
import gg.rsmod.plugins.content.mechanics.poison.Poison
import gg.rsmod.plugins.content.mechanics.poison.Venom

/**
 * OSRS-IMPORT Noxious halberd (OSRS Wiki "Noxious halberd" raw wikitext and "Special attacks", 2026-09-14).
 *
 * - Passive: "has a 33% chance (50% with a Serpentine helm equipped) to envenom the target (if not venom immune)". No
 *   Serpentine helm exists in this server, so the chance is 33%. The page gives no hit condition: the chance is rolled
 *   for every attack when its hit resolves, as for the Toxic blowpipe (SOURCE_GAP recorded). Venom immunity is checked
 *   by `Venom.envenom`.
 * - Virulence (50 %): "can only be used if the player is afflicted with poison or venom", "instantly cures the condition";
 *   "The player's next accurate attack with the halberd will have its minimum hit increased by the damage the player
 *   would have taken from their cured affliction." "This effect is lost if the player changes weapon or logs out."
 *   "Virulence does not give any period of immunity towards poison and venom." Failure message quoted verbatim.
 *   OWNER DECISION 2026-09-14: the cured damage is the NEXT poison/venom hit (poison `ticksLeft / 5 + 1`, venom its
 *   current 6..20 value). A minimum above the max hit is clamped to the max hit by `rollDamage`. The effect is a plain
 *   (non-persistent) attribute, so logging out clears it; unequipping the halberd clears it too.
 */
object NoxiousHalberd {
    const val VENOM_CHANCE = 0.33
    const val VIRULENCE_ENERGY = 50
    const val VIRULENCE_FAIL_MESSAGE = "You can only use this special attack whilst you are poisoned."

    /** Minimum hit stored by Virulence for the next accurate halberd attack; not persisted (lost on logout). */
    val VIRULENCE_MINIMUM = AttributeKey<Int>()

    fun isWielding(player: Player): Boolean = player.hasEquipped(EquipmentType.WEAPON, Items.NOXIOUS_HALBERD)

    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        if (player.world.randomDouble() < VENOM_CHANCE) target.venom()
    }

    /** The damage the pawn's next poison or venom hit would deal, or null when it is not afflicted. */
    fun nextAfflictionHit(pawn: Pawn): Int? {
        pawn.attr[VENOM_TICKS_ELAPSED_ATTR]?.let { return Venom.damageForTick(it) }
        pawn.attr[POISON_TICKS_LEFT_ATTR]?.let { return Poison.getDamageForTicks(it) }
        return null
    }

    /** Virulence: cures poison/venom (no immunity) and stores its next hit as a minimum. False = not afflicted (no energy used). */
    fun activateVirulence(player: Player): Boolean {
        val damage = nextAfflictionHit(player)
        if (damage == null) {
            player.message(VIRULENCE_FAIL_MESSAGE)
            return false
        }
        if (!Venom.cure(player, immunityTicks = 0)) Poison.cure(player)
        player.attr[VIRULENCE_MINIMUM] = damage
        return true
    }

    /** Minimum hit for an attack; an accurate attack consumes the stored Virulence effect, a miss keeps it. */
    fun takeMinimum(
        player: Player,
        landHit: Boolean,
    ): Int {
        if (!landHit) return 0
        val minimum = player.attr[VIRULENCE_MINIMUM] ?: return 0
        player.attr.remove(VIRULENCE_MINIMUM)
        return minimum
    }

    fun clearVirulence(player: Player) {
        player.attr.remove(VIRULENCE_MINIMUM)
    }
}
