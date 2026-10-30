package gg.rsmod.plugins.content.mechanics.combatresponse

import gg.rsmod.game.model.attr.RING_OF_RECOIL_CHARGE_ATTR
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
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
 * Recoil-immune monsters: [RECOIL_IMMUNE_NPCS] (the OSRS Wiki list, as far as those monsters exist here).
 */
object RingOfRecoil {
    const val CHARGE_LIMIT = 40

    // Ring of suffering / ring of suffering (i) have no item id anywhere in this codebase
    // yet (grep-confirmed against Items.kt) - add them here the moment one is sourced;
    // everything below already generalizes to any ring in this list for free.
    private val RECOIL_RING_IDS = intArrayOf(Items.RING_OF_RECOIL)

    /**
     * OSRS Wiki "Ring of recoil", immune monsters: of that list only Nex exists on this server (all four forms). Combat run
     * 2026-09-29: recoil and the Ring of suffering reflect used to hit her like any other npc.
     */
    val RECOIL_IMMUNE_NPCS = setOf(Npcs.NEX, Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX_13450)

    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (target !is Player) return
        if (attacker is gg.rsmod.game.model.entity.Npc && attacker.id in RECOIL_IMMUNE_NPCS) return
        // Ring of suffering (r)/(ri): the same reflect, paid from the ring's own charges (RingOfSuffering).
        val ring = target.getEquipment(EquipmentType.RING)
        if (ring != null && gg.rsmod.plugins.content.items.osrs.RingOfSuffering.isCharged(ring.id)) {
            val reflect = (damage / 10) + 1
            val after = gg.rsmod.plugins.content.items.osrs.RingOfSuffering.afterRecoil(ring, reflect) ?: return
            attacker.hit(damage = reflect, type = HitType.REFLECTED)
            attacker.damageMap.add(target, reflect)
            gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(attacker, target, reflect)
            target.equipment[EquipmentType.RING.id] = after
            return
        }
        if (!target.hasEquipped(EquipmentType.RING, *RECOIL_RING_IDS)) return

        val reflected = (damage / 10) + 1
        attacker.hit(damage = reflected, type = HitType.REFLECTED)
        attacker.damageMap.add(target, reflected)
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(attacker, target, reflected)

        val charge = (target.attr[RING_OF_RECOIL_CHARGE_ATTR] ?: 0) + reflected
        if (charge >= CHARGE_LIMIT) {
            target.attr.remove(RING_OF_RECOIL_CHARGE_ATTR)
            shatter(target)
        } else {
            target.attr[RING_OF_RECOIL_CHARGE_ATTR] = charge
        }
    }

    /**
     * K12 N11 (the worn "Check" was a dead option, census 2026-10-30): the damage the ring can still reflect before it shatters.
     * Wording from Void's RingOfRecoil (worn Check); the OSRS Wiki only says the worn Check shows the remaining damage.
     */
    fun checkMessage(player: Player): String =
        "You can inflict ${CHARGE_LIMIT - (player.attr[RING_OF_RECOIL_CHARGE_ATTR] ?: 0)} more points of damage before a ring will shatter."

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
