package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * Amulet of the damned (owner 2026-09-29: "important, fully working"). OSRS Wiki "Amulet of the Damned" - with the full matching
 * Barrows set:
 * - Dharok's: "25% chance of recoiling 15% of damage taken. This effect can stack with the Vengeance spell and ring of recoil".
 * - Verac's: "an additional +7 Prayer bonus beyond the amulet's base +3".
 * - Torag's: "Defence level increases by 1% invisibly for every missing Hitpoint".
 * - Guthan's: "healing up to 10 Hitpoints beyond the player's base level".
 * - Karil's: "25% chance of hitting twice with one attack. The second hit will deal half the damage of the first hit."
 * - Ahrim's: "The staff can autocast Ancient Magicks, plus spells have a 25% chance dealing 30% additional damage."
 * Degradation: the (full) amulet turns into the used one on entering combat and "will degrade at the same rate as any other barrows
 * armour", then "turns to dust" (EquipmentDegradation).
 * A set counts when the four pieces carry the brother's name (every degraded stage included), the way the Barrows sets are read here.
 */
object AmuletOfTheDamned {
    val IDS = setOf(Items.AMULET_OF_THE_DAMNED_FULL, Items.AMULET_OF_THE_DAMNED)

    const val DHAROK_CHANCE = 0.25
    const val DHAROK_RECOIL = 0.15
    const val VERAC_PRAYER = 7
    const val GUTHAN_OVERHEAL = 10
    const val KARIL_CHANCE = 0.25
    const val AHRIM_CHANCE = 0.25
    const val AHRIM_DAMAGE = 1.3

    fun worn(pawn: Pawn): Boolean = pawn is Player && pawn.getEquipment(EquipmentType.AMULET)?.id in IDS

    private val SET_SLOTS = listOf(EquipmentType.HEAD, EquipmentType.WEAPON, EquipmentType.CHEST, EquipmentType.LEGS)

    /** The four pieces of [brother]'s set ("Dharok's", "Karil's", ...) are worn, together with the amulet. */
    fun withSet(
        pawn: Pawn,
        brother: String,
    ): Boolean {
        if (!worn(pawn)) return false
        val player = pawn as Player
        return SET_SLOTS.all { slot ->
            val item = player.getEquipment(slot) ?: return@all false
            player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).name.startsWith(brother, ignoreCase = true)
        }
    }

    fun veracPrayerBonus(player: Player): Int = if (withSet(player, "Verac's")) VERAC_PRAYER else 0

    /** Torag's: +1 % Defence per missing Hitpoint, applied to the effective Defence level. */
    fun toragDefenceMultiplier(pawn: Pawn): Double {
        if (!withSet(pawn, "Torag's")) return 1.0
        val player = pawn as Player
        val missing = (player.skills.getMaxLevel(Skills.CONSTITUTION) - player.skills.getCurrentLevel(Skills.CONSTITUTION)).coerceAtLeast(0)
        return 1.0 + missing / 100.0
    }

    fun guthanOverheal(pawn: Pawn): Int = if (withSet(pawn, "Guthan's")) GUTHAN_OVERHEAL else 0

    /** Dharok's: the recoil of [damage] taken, or 0 (25 % chance). */
    fun dharokRecoil(
        defender: Pawn,
        damage: Int,
    ): Int {
        if (damage <= 0 || !withSet(defender, "Dharok's")) return 0
        if (defender.world.randomDouble() >= DHAROK_CHANCE) return 0
        return (damage * DHAROK_RECOIL).toInt()
    }

    fun karilDoubleHit(player: Player): Boolean = withSet(player, "Karil's") && player.world.randomDouble() < KARIL_CHANCE

    fun ahrimCanAutocastAncients(player: Player): Boolean = withSet(player, "Ahrim's")

    fun ahrimDamageMultiplier(player: Player): Double =
        if (withSet(player, "Ahrim's") && player.world.randomDouble() < AHRIM_CHANCE) AHRIM_DAMAGE else 1.0
}
