package gg.rsmod.plugins.content.drops.global

import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableFactory

/**
 * @author Alycia <https://github.com/alycii>
 */
object MegaRare {
    val megaRareTable =
        DropTableFactory.build {
            main {

                // every ring of wealth (charged ones and the imbued (i) rings too), not only the uncharged id
                val hasRingOfWealth = gg.rsmod.plugins.content.areas.wilderness.npcs.WildernessNpcDrops.wealth(player)

                total(1024)
                if (!hasRingOfWealth) nothing(904)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = if (hasRingOfWealth) 545 else 64)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = if (hasRingOfWealth) 273 else 32)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = if (hasRingOfWealth) 206 else 24)
            }
        }
}
