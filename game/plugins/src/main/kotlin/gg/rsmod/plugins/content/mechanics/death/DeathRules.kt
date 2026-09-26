package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.plugins.content.items.helios.CrownOfHelios
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.mechanics.trouver.Trouver

/**
 * The death rules shared by the real death (`death.plugin.kts`) and the Items Kept on Death preview ([ItemsKeptOnDeath]),
 * so the screen can never predict something the death does not do.
 */
object DeathRules {
    /**
     * Items kept whatever the keep-3 ranking says. Deadman emblems (owner 2026-09-25): kept on a PvM death, always lost on a
     * PvP death. Audit D-15: on a PvP death generic untradeables are no longer kept whole; they rank in the normal keep-3
     * and, when lost, break in place ([UntradeableDeathProtection.splitGeneric]).
     */
    fun alwaysProtected(
        definitions: DefinitionSet,
        pvpDeath: Boolean,
    ): (Int) -> Boolean =
        { itemId ->
            if (DeadmanEmblem.isEmblem(itemId)) {
                !pvpDeath
            } else {
                itemId == CrownOfHelios.ITEM || Trouver.protectedFromDeath(itemId) ||
                    (!pvpDeath && UntradeableDeathProtection.shouldProtect(definitions, itemId))
            }
        }
}
