package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.attr.DRAGONFIRE_IMMUNITY_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ANTIFIRE_TIMER
import gg.rsmod.game.model.timer.SUPER_ANTIFIRE_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.isProtectedFrom

/**
 * @author Tom <rspsmods@gmail.com>
 *
 * @since 21/03/2023 -> Kevin Senez <ksenez94@gmail.com>
 *
 * RCV-012 owner decision "dragonfire = OSRS model" (2026-09-14): the max hit for every protection combination comes from
 * [DragonfireTable] (OSRS Wiki "Dragonfire", Damage reduction) instead of percentages of a per-npc data max. Dragonfire always lands;
 * where the wiki row is split, the dragon's Magic accuracy against the player's Magic defence decides between the "failed" and "won"
 * max, and the player gets the sourced chatbox message for that outcome. Rows without a split have no sourced message (SOURCE_GAP).
 *
 * ADAPTED: OSRS subtracts the (super) antifire potion's protection after the damage roll (Mod Ash, cited on the wiki page); the wiki
 * gives only the resulting maxima, so the hit is rolled uniformly up to the tabled max.
 */
class DragonfireFormula(
    private val type: DragonfireTable.Type = DragonfireTable.Type.CHROMATIC,
    private val minHit: Double = 0.0,
) : CombatFormula {
    /** Dragonfire never misses on accuracy; the Magic accuracy roll only selects the failed / won max in [getMaxHit]. */
    override fun getAccuracy(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
    ): Double = 1.0

    override fun getMaxHit(
        pawn: Pawn,
        target: Pawn,
        specialAttackMultiplier: Double,
        specialPassiveMultiplier: Double,
    ): Double {
        if (pawn !is Npc || target !is Player) {
            return DragonfireTable.max(type, shield = false, prayer = false, potion = DragonfireTable.Potion.NONE).failed.toDouble()
        }
        val outcome =
            resolve(type, protectionOf(target)) {
                MagicCombatFormula.getAccuracy(pawn, target, specialAttackMultiplier) < pawn.world.randomDouble()
            }
        outcome.message?.let { target.filterableMessage(it) }
        gg.rsmod.plugins.content.items.osrs.DragonfireShield.gainChargeFromDragonfire(target)
        return outcome.max.toDouble().coerceAtLeast(minHit)
    }

    data class Outcome(val max: Int, val message: String?)

    data class Protection(
        val shield: Boolean,
        val prayer: Boolean,
        val potion: DragonfireTable.Potion,
        val immune: Boolean,
    )

    companion object {
        private val ANTI_DRAGON_SHIELDS = intArrayOf(Items.ANTIDRAGON_SHIELD)
        // OSRS-IMPORT magegearb: the Dragonfire ward "acts like a regular anti-dragon shield in terms of dragonfire protection" and the
        // Ancient wyvern shield "provides both dragonfire protection" and wyvern icy breath protection (OSRS Wiki), charged or not.
        private val DRAGONFIRE_SHIELDS =
            intArrayOf(
                Items.DRAGONFIRE_SHIELD, Items.DRAGONFIRE_SHIELD_11284, Items.DRAGONFIRE_WARD, Items.DRAGONFIRE_WARD_UNCHARGED,
                Items.ANCIENT_WYVERN_SHIELD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED,
            )

        /**
         * Every shield this server treats as anti-dragon-tier dragonfire protection ([ANTI_DRAGON_SHIELDS] +
         * [DRAGONFIRE_SHIELDS]), exposed for other dragonfire-adjacent mechanics (e.g. the Dragonstone dragon
         * bolts (e) "Dragon's Breath" effect in `EnchantedBolts`) so the shield roster is defined once, not
         * duplicated and left to drift - audit round 2026-09-17b found the bolt effect's own copy missing
         * Dragonfire ward and Ancient wyvern shield.
         */
        val ALL_ANTI_DRAGON_SHIELDS: IntArray = ANTI_DRAGON_SHIELDS + DRAGONFIRE_SHIELDS

        /** The tabled max and sourced message; [playerWonRoll] is only consulted when the wiki row is split by the accuracy roll. */
        fun resolve(
            type: DragonfireTable.Type,
            protection: Protection,
            playerWonRoll: () -> Boolean,
        ): Outcome {
            if (protection.immune) return Outcome(0, null)
            val max = DragonfireTable.max(type, protection.shield, protection.prayer, protection.potion)
            if (!max.splitByAccuracy) return Outcome(max.failed, null)
            return if (playerWonRoll()) Outcome(max.won, DragonfireTable.RESIST_MESSAGE) else Outcome(max.failed, DragonfireTable.BURNT_MESSAGE)
        }

        /** The player's dragonfire protection layers; a super antifire outranks a regular one when both timers run. */
        fun protectionOf(target: Player): Protection =
            Protection(
                shield = target.hasEquipped(EquipmentType.SHIELD, *ANTI_DRAGON_SHIELDS) || target.hasEquipped(EquipmentType.SHIELD, *DRAGONFIRE_SHIELDS),
                prayer = target.isProtectedFrom(CombatClass.MAGIC),
                potion =
                    when {
                        target.timers.has(SUPER_ANTIFIRE_TIMER) -> DragonfireTable.Potion.SUPER_ANTIFIRE
                        target.timers.has(ANTIFIRE_TIMER) -> DragonfireTable.Potion.ANTIFIRE
                        else -> DragonfireTable.Potion.NONE
                    },
                immune = target.attr[DRAGONFIRE_IMMUNITY_ATTR] ?: false,
            )
    }
}
