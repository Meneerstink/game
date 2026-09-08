package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.RING_OF_RECOIL_CHARGE_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.message

/**
 * Ring of recoil (and, once sourced, ring of suffering / ring of suffering (i), which share
 * this exact effect once charged - see [RECOIL_RING_IDS]). Sourced from the OSRS Wiki "Ring
 * of recoil" page: reflects floor(10% of damage taken) + 1 back onto whoever dealt it, works
 * against both players and NPCs, and the ring can absorb a cumulative 40 damage worth of
 * reflects - tracked per-player, not per physical ring - before it shatters.
 *
 * Not implemented (no source found this pass): the specific list of high-level bosses immune
 * to the recoil effect entirely. That is a per-NPC exclusion list, not part of this generic
 * foundation, and guessing at which NPCs belong on it would violate the "never guess IDs"
 * rule - left as a documented follow-up for whichever session adds those NPCs' combat defs.
 */
object RingOfRecoil {
    const val CHARGE_LIMIT = 40

    // Ring of suffering / ring of suffering (i) have no item id anywhere in this codebase
    // yet (grep-confirmed against Items.kt) - add them here the moment one is sourced;
    // everything below already generalizes to any ring in this list for free.
    private val RECOIL_RING_IDS = intArrayOf(Items.RING_OF_RECOIL)

    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (target !is Player) return
        if (!target.hasEquipped(EquipmentType.RING, *RECOIL_RING_IDS)) return

        val reflected = (damage / 10) + 1
        attacker.hit(damage = reflected, type = HitType.REFLECTED)
        attacker.damageMap.add(target, reflected)

        val charge = (target.attr[RING_OF_RECOIL_CHARGE_ATTR] ?: 0) + reflected
        if (charge >= CHARGE_LIMIT) {
            target.attr.remove(RING_OF_RECOIL_CHARGE_ATTR)
            shatter(target)
        } else {
            target.attr[RING_OF_RECOIL_CHARGE_ATTR] = charge
        }
    }

    private fun shatter(target: Player) {
        val ringId = target.getEquipment(EquipmentType.RING)?.id ?: return
        if (ringId !in RECOIL_RING_IDS) return
        target.equipment.remove(ringId)
        // Exact client wording for the shatter notice isn't in the sourced wiki text (only
        // that "the message a player receives as their ring shatters has been recoloured")
        // - this is a functional, non-quoted equivalent rather than a verified transcript.
        target.message("Your ring of recoil has shattered.")
    }
}
