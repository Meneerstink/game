package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.NoxiousHalberd

/**
 * OSRS-IMPORT Noxious halberd - Virulence (rules and sources in [NoxiousHalberd]). An instant special: clicking the bar
 * cures poison/venom and arms the minimum hit; energy is only used when the player was afflicted. The OSRS special
 * animation/graphic are not in 667, so none is played (ADAPTED_TO_667).
 */
SpecialAttacks.registerInstant(NoxiousHalberd.VIRULENCE_ENERGY, Items.NOXIOUS_HALBERD) { p ->
    NoxiousHalberd.activateVirulence(p)
}

// "This effect is lost if the player changes weapon or logs out." (logout: the attribute is not persisted)
on_item_unequip(item = Items.NOXIOUS_HALBERD) {
    NoxiousHalberd.clearVirulence(player)
}
