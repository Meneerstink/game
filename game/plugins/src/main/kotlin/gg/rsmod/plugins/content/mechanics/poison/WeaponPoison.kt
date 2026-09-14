package gg.rsmod.plugins.content.mechanics.poison

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items

/**
 * Poisoned weapons and ammunition (OSRS Wiki "Poison", section Weapon poisoning). Before this, no (p)/(p+)/(p++)/(kp) item poisoned
 * anything: only npc combat defs, smoke spells and emerald bolt procs applied poison.
 *
 * - "A player must land a successful hit with the poisoned weapon to have a chance at poisoning their enemy. The chances of inflicting
 *   poison are 1/4 for melee and 1/8 for ranged" (Mod Ash: "Damage must be non-zero").
 * - Severity: weapon poison 20, (+) 25, (++) 30, karambwan paste 30 (spears and hastae only), Abyssal tentacle 20 ("Special attack has
 *   a 1/2 chance of poisoning the target instead of 1/4"). "Poisons applied with ranged weapons have their Severity lowered by 14",
 *   which reproduces the table's ranged starting damage 2 / 3 / 4 through [Poison.poisonSeverity] (ceil(severity / 5)).
 * - The roster is every cache item whose name carries the (p), (p+), (p++) or (kp) marker (Dungeoneering (b) copies included); the
 *   poison comes from the melee weapon, the thrown weapon, or the arrows/bolts in the ammunition slot.
 *
 * The shared Poison model keeps a stronger dose, upgrades a weaker dose, and restarts the poison
 * timer for an equal or stronger landed application, matching the donor state model.
 */
object WeaponPoison {
    const val WEAPON_POISON = 20
    const val WEAPON_POISON_PLUS = 25
    const val WEAPON_POISON_PLUS_PLUS = 30
    const val KARAMBWAN_PASTE = 30
    const val RANGED_SEVERITY_REDUCTION = 14
    const val MELEE_CHANCE = 4
    const val RANGED_CHANCE = 8
    const val TENTACLE_SPECIAL_CHANCE = 2

    /** Set by SpecialAttacks.execute while a special attack's hits are being dealt. */
    val SPECIAL_ATTACK_IN_PROGRESS = AttributeKey<Boolean>()

    private val MARKER = Regex("""\((p|p\+|p\+\+|kp)\)""")

    /** The poison severity a weapon or ammunition name carries, or 0. */
    fun severityForName(name: String): Int =
        when (MARKER.find(name)?.groupValues?.get(1)) {
            "p" -> WEAPON_POISON
            "p+" -> WEAPON_POISON_PLUS
            "p++" -> WEAPON_POISON_PLUS_PLUS
            "kp" -> KARAMBWAN_PASTE
            else -> 0
        }

    fun severity(
        definitions: DefinitionSet,
        itemId: Int,
    ): Int {
        if (itemId == Items.ABYSSAL_TENTACLE) return WEAPON_POISON
        return severityForName(definitions.getNullable(ItemDef::class.java, itemId)?.name ?: return 0)
    }

    /** The severity applied to the target: the ranged reduction for arrows, bolts, darts, knives and javelins. */
    fun appliedSeverity(
        severity: Int,
        ranged: Boolean,
    ): Int = if (ranged) severity - RANGED_SEVERITY_REDUCTION else severity

    /** 1-in-N chance of the attack style (tentacle special 1/2). */
    fun chanceDenominator(
        ranged: Boolean,
        tentacleSpecial: Boolean,
    ): Int =
        when {
            tentacleSpecial -> TENTACLE_SPECIAL_CHANCE
            ranged -> RANGED_CHANCE
            else -> MELEE_CHANCE
        }

    /** The item that carries the poison for this hit: the weapon for melee and thrown weapons, otherwise the ammunition. */
    private fun poisonSource(
        player: Player,
        hitType: HitType,
    ): Int? {
        val weapon = player.equipment[EquipmentType.WEAPON.id]?.id
        return when (hitType) {
            HitType.MELEE -> weapon
            HitType.RANGE -> {
                if (weapon != null && severity(player.world.definitions, weapon) > 0) weapon else player.equipment[EquipmentType.AMMO.id]?.id
            }
            else -> null
        }
    }

    /** Called from dealHit for every player melee/ranged hit: rolls now, poisons when the hit lands with non-zero damage. */
    fun onPlayerHit(
        attacker: Player,
        target: Pawn,
        pawnHit: PawnHit,
        hitType: HitType,
    ) {
        if (!pawnHit.landed || (hitType != HitType.MELEE && hitType != HitType.RANGE)) return
        val source = poisonSource(attacker, hitType) ?: return
        val severity = severity(attacker.world.definitions, source)
        if (severity <= 0) return
        val ranged = hitType == HitType.RANGE
        val tentacleSpecial = source == Items.ABYSSAL_TENTACLE && attacker.attr[SPECIAL_ATTACK_IN_PROGRESS] == true
        if (attacker.world.random(chanceDenominator(ranged, tentacleSpecial) - 1) != 0) return
        val applied = appliedSeverity(severity, ranged)
        pawnHit.hit.addAction {
            if (pawnHit.hit.hitmarks.sumOf { it.damage } > 0 && !Poison.isImmune(target)) Poison.poisonSeverity(target, applied)
        }
    }
}
