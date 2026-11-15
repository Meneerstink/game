package gg.rsmod.plugins.content.drops.global

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableFactory

object Seeds {
    val allotmentSeedTable =
        DropTableFactory.build {
            main {
                total(90)
                obj(Items.POTATO_SEED, quantity = 4, slots = 32)
                obj(Items.ONION_SEED, quantity = 4, slots = 25)
                obj(Items.CABBAGE_SEED, quantity = 4, slots = 16)
                obj(Items.TOMATO_SEED, quantity = 3, slots = 9)
                obj(Items.SWEETCORN_SEED, quantity = 3, slots = 5)
                obj(Items.STRAWBERRY_SEED, quantity = 2, slots = 2)
                obj(Items.WATERMELON_SEED, quantity = 2, slots = 1)
            }
        }

    val uncommonSeedTable =
        DropTableFactory.build {
            main {
                total(1048)
                obj(Items.LIMPWURT_SEED, quantity = 1, slots = 137)
                obj(Items.STRAWBERRY_SEED, quantity = 1, slots = 131)
                obj(Items.MARRENTILL_SEED, quantity = 1, slots = 125)
                obj(Items.JANGERBERRY_SEED, quantity = 1, slots = 92)
                obj(Items.TARROMIN_SEED, quantity = 1, slots = 85)
                obj(Items.WILDBLOOD_SEED, quantity = 1, slots = 83)
                obj(Items.WATERMELON_SEED, quantity = 1, slots = 83)
                obj(Items.HARRALANDER_SEED, quantity = 1, slots = 86)
                obj(Items.RANARR_SEED, quantity = 1, slots = 49)
                obj(Items.WHITEBERRY_SEED, quantity = 1, slots = 39)
                obj(Items.TOADFLAX_SEED, quantity = 1, slots = 27)
                obj(Items.BELLADONNA_SEED, quantity = 1, slots = 18)
                obj(Items.IRIT_SEED, quantity = 1, slots = 18)
                obj(Items.POISON_IVY_SEED, quantity = 1, slots = 13)
                obj(Items.AVANTOE_SEED, quantity = 1, slots = 18)
                obj(Items.CACTUS_SEED, quantity = 1, slots = 16)
                obj(Items.KWUARM_SEED, quantity = 1, slots = 13)
                obj(Items.SNAPDRAGON_SEED, quantity = 1, slots = 5)
                obj(Items.CADANTINE_SEED, quantity = 1, slots = 4)
                obj(Items.LANTADYME_SEED, quantity = 1, slots = 3)
                obj(Items.DWARF_WEED_SEED, quantity = 1, slots = 2)
                obj(Items.TORSTOL_SEED, quantity = 1, slots = 1)
            }
        }

    /**
     * N614 (15-11): OSRS rare seed drop table, OSRS Wiki 'Drop table#Rare seed drop table' (x/1090). Potato cactus seed (70) and
     * snape grass seed x3 (20) do not exist in our game -> their 90 slots drop nothing, so every other seed keeps its exact OSRS rate.
     */
    val rareSeedTable =
        DropTableFactory.build {
            main {
                total(1090)
                obj(Items.TOADFLAX_SEED, quantity = 1, slots = 216)
                obj(Items.IRIT_SEED, quantity = 1, slots = 148)
                obj(Items.BELLADONNA_SEED, quantity = 1, slots = 143)
                obj(Items.AVANTOE_SEED, quantity = 1, slots = 103)
                obj(Items.POISON_IVY_SEED, quantity = 1, slots = 101)
                obj(Items.CACTUS_SEED, quantity = 1, slots = 96)
                obj(Items.KWUARM_SEED, quantity = 1, slots = 69)
                obj(Items.SNAPDRAGON_SEED, quantity = 1, slots = 46)
                obj(Items.CADANTINE_SEED, quantity = 1, slots = 32)
                obj(Items.LANTADYME_SEED, quantity = 1, slots = 23)
                obj(Items.DWARF_WEED_SEED, quantity = 1, slots = 14)
                obj(Items.TORSTOL_SEED, quantity = 1, slots = 9)
                nothing(90)
            }
        }
}
