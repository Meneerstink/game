package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import java.lang.ref.WeakReference

/**
 * OSRS-IMPORT Confliction gauntlets (OSRS Wiki raw wikitext, 2026-09-14):
 * - "The gauntlets have a passive effect that occurs upon a missed magic attack, which causes the next magic attack of that weapon or
 *   spell against the same enemy to roll accuracy twice."
 * - "When using multi-target spells (e.g. Blood Barrage), the passive effect will only roll accuracy twice against the primary target
 *   of the spell; secondary targets will roll accuracy once as normal."
 * - "the passive effect does not apply at all when casting ice spells against other players, including against the primary target."
 * - "This effect is disabled if using a two-handed weapon" (items.yml equip_type 5 hides the shield slot).
 * ADAPTED: the pending double roll is kept until the next magic attack of the same weapon/spell on that enemy, and is dropped by a
 * magic attack on another enemy or with another weapon/spell (the page does not say whether it survives those).
 */
object ConflictionGauntlets {
    private data class Pending(val target: WeakReference<Pawn>, val key: Int)

    private val PENDING = AttributeKey<Pending>()

    fun isActive(player: Player): Boolean {
        if (player.getEquipment(EquipmentType.GLOVES)?.id != Items.CONFLICTION_GAUNTLETS) return false
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return true
        return player.world.definitions.get(ItemDef::class.java, weapon.id).equipType != 5
    }

    /**
     * Rolls a magic attack's accuracy for [player] against [target] with [accuracy] (hit chance). [key] identifies the spell (unique
     * id) or powered staff (weapon id); [eligible] is false for secondary targets and for ice spells against players.
     */
    fun roll(
        player: Player,
        target: Pawn,
        key: Int,
        accuracy: Double,
        eligible: Boolean,
        random: () -> Double,
    ): Boolean {
        val active = eligible && isActive(player)
        val pending = player.attr[PENDING]
        val doubled = active && pending != null && pending.key == key && pending.target.get() === target
        val landHit = accuracy >= random() || (doubled && accuracy >= random())
        if (active) {
            if (landHit) player.attr.remove(PENDING) else player.attr[PENDING] = Pending(WeakReference(target), key)
        }
        return landHit
    }
}
