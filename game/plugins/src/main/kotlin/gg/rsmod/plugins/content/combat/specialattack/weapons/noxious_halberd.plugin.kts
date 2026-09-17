package gg.rsmod.plugins.content.combat.specialattack.weapons

import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.NoxiousHalberd

/**
 * OSRS-IMPORT Noxious halberd - Virulence (rules and sources in [NoxiousHalberd]). An instant special: clicking the bar
 * cures poison/venom and arms the minimum hit; energy is only used when the player was afflicted. Look: the imported OSRS
 * sequence HUMAN_HALBERD_VIRULENCE_02 (its frames play noxious_halberd_special_attack_build / _impact) and VFX_NOXIOUS_HALBERD_SPEC.
 */
SpecialAttacks.registerInstant(NoxiousHalberd.VIRULENCE_ENERGY, Items.NOXIOUS_HALBERD) { p ->
    NoxiousHalberd.activateVirulence(p).also { activated ->
        if (activated) p.animate(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_HALBERD_VIRULENCE_02)
        if (activated) p.graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.NOXIOUS_HALBERD_SPECIAL) // OSRS VFX_NOXIOUS_HALBERD_SPEC (fxpilot)
    }
}

// "This effect is lost if the player changes weapon or logs out." (logout: the attribute is not persisted)
on_item_unequip(item = Items.NOXIOUS_HALBERD) {
    NoxiousHalberd.clearVirulence(player)
}
