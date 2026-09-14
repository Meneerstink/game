package gg.rsmod.plugins.content.combat.formula

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.hasEquipped
import gg.rsmod.plugins.api.ext.isProtectedFrom

/**
 * OSRS Wiki "Dragonfire", section Icy breath (2026-09-14): "Wyverns use a long-ranged icy breath instead of regular dragonfire, which can
 * freeze the player for 6.6 seconds, preventing attacking and movement. The maximum damage of icy breath is reduced to 10 when an elemental
 * shield, mind shield, dragonfire shield, dragonfire ward, or ancient wyvern shield is equipped; a regular anti-dragon shield and antifire
 * potions have no effect. Using Protect from Magic without a shield reduces the max hit to 20. [...] Furthermore, the freezing effect of the
 * attack is only prevented by the ancient wyvern shield."
 * Without protection the attack table max applies (skeletal wyvern 50; wiki infobox "50+"). SOURCE_CONFLICT recorded: Mod Ash's quoted
 * tweet ("capping the damage at 20 rather than 30") suggests a lower unprotected cap for that wyvern; the table value is kept.
 */
object WyvernIcyBreath {
    const val SHIELD_MAX = 10
    const val PROTECT_FROM_MAGIC_MAX = 20

    /** 6.6 seconds = 11 game ticks. */
    const val FREEZE_TICKS = 11

    val SHIELDS =
        intArrayOf(
            Items.ELEMENTAL_SHIELD, Items.MIND_SHIELD, Items.DRAGONFIRE_SHIELD, Items.DRAGONFIRE_SHIELD_11284, Items.DRAGONFIRE_WARD,
            Items.DRAGONFIRE_WARD_UNCHARGED, Items.ANCIENT_WYVERN_SHIELD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED,
        )

    val FREEZE_SHIELDS = intArrayOf(Items.ANCIENT_WYVERN_SHIELD, Items.ANCIENT_WYVERN_SHIELD_UNCHARGED)

    fun maxHit(
        target: Player,
        tableMax: Double,
    ): Double =
        when {
            target.hasEquipped(EquipmentType.SHIELD, *SHIELDS) -> minOf(tableMax, SHIELD_MAX.toDouble())
            target.isProtectedFrom(CombatClass.MAGIC) -> minOf(tableMax, PROTECT_FROM_MAGIC_MAX.toDouble())
            else -> tableMax
        }

    fun blocksFreeze(target: Player): Boolean = target.hasEquipped(EquipmentType.SHIELD, *FREEZE_SHIELDS)
}
