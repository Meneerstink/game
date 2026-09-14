package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.game.model.attr.DRAGONFIRE_IMMUNITY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.hasPrayerIcon
import kotlin.math.floor

/**
 * @author Tom <rspsmods@gmail.com>
 *
 * @since 21/03/2023 -> Kevin Senez <ksenez94@gmail.com>
 *
 * Stacking percentages corrected 2026-09-02 (further-foundations autonomous pass) against
 * the OSRS Wiki "Dragonfire" page's chromatic dragon table (50 base max hit): none = 100%,
 * anti-dragon/dragonfire shield alone = 10%, Protect from Magic alone = 20%, shield+prayer
 * together = 10% (no better than shield alone), antifire potion alone = 70%, super antifire
 * potion alone = full immunity, and any potion (either tier) combined with a shield or
 * prayer = full immunity. Previously this used incorrect ad-hoc values (20%/0%/66.5%/66.5%/
 * 33.5%) and read a dead `ANTIFIRE_POTION_CHARGES_ATTR` attribute that nothing ever wrote -
 * see `RSPS_DECISIONS.md` for the full write-up. Only verified against the chromatic dragon
 * table; King Black Dragon's non-zero damage floors for its combo breath attacks are a
 * separate, not-yet-sourced nuance left for a future pass (see `RSPS_DECISIONS.md`).
 */
class DragonfireFormula(
    private val maxHit: Int,
    private val minHit: Double = 0.0,
) : CombatFormula {
    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double {
        return MagicCombatFormula.getAccuracy(pawn, target, specialAttackMultiplier)
    }

    override fun getMaxHit(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        var max = maxHit.toDouble()

        if (target is Player) {
            val magicProtection = target.isProtectedFrom(CombatClass.MAGIC)
            val antiFirePotion = target.timers.has(ANTIFIRE_TIMER)
            val superAntiFirePotion = target.timers.has(SUPER_ANTIFIRE_TIMER)
            val dragonFireImmunity = target.attr[DRAGONFIRE_IMMUNITY_ATTR] ?: false
            val antiFireShield = target.hasEquipped(EquipmentType.SHIELD, *ANTI_DRAGON_SHIELDS)
            val dragonfireShield = target.hasEquipped(EquipmentType.SHIELD, *DRAGONFIRE_SHIELDS)
            val anyShield = antiFireShield || dragonfireShield

            if (pawn is Npc) {
                val message: String =
                    when {
                        /**
                         * Full immunity: an explicit immunity flag, a super antifire potion on
                         * its own, or either potion tier stacked with a shield or the prayer.
                         */
                        dragonFireImmunity || superAntiFirePotion || (antiFirePotion && (anyShield || magicProtection)) -> {
                            max = minHit
                            "You are completely immune to dragonfire."
                        }

                        /**
                         * Shield alone (or shield + prayer, which adds nothing further).
                         */
                        anyShield -> {
                            max *= 0.10
                            "Your shield absorbs most of the dragon's fiery breath."
                        }

                        /**
                         * Protect from Magic alone, no shield.
                         */
                        magicProtection -> {
                            max *= 0.20
                            "Your prayer absorbs some of the dragonfire."
                        }

                        /**
                         * Regular antifire potion alone, no shield or prayer.
                         */
                        antiFirePotion -> {
                            max *= 0.70
                            "You manage to resist some of the dragonfire."
                        }

                        else -> {
                            "You are horribly burned by the dragon's breath!"
                        }
                    }

                /**
                 * Send the filterable message to the player on dragonfire attack.
                 */
                target.filterableMessage(message)
            }
        }
        return minHit.coerceAtLeast(floor(max))
    }

    companion object {
        private val ANTI_DRAGON_SHIELDS = intArrayOf(Items.ANTIDRAGON_SHIELD)
        // OSRS-IMPORT magegearb: the Dragonfire ward "acts like a regular anti-dragon shield in terms of dragonfire protection" and the
        // Ancient wyvern shield "provides both dragonfire protection" and wyvern icy breath protection (OSRS Wiki), charged or not.
        private val DRAGONFIRE_SHIELDS =
            intArrayOf(
                Items.DRAGONFIRE_SHIELD, Items.DRAGONFIRE_SHIELD_11284, Items.DRAGONFIRE_WARD, Items.DRAGONFIRE_WARD_UNCHARGED,
                Items.ANCIENT_WYVERN_SHIELD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED,
            )
    }
}
