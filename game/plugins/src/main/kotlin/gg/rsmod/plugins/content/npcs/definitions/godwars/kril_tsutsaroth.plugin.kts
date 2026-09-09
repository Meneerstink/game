package gg.rsmod.plugins.content.npcs.definitions.godwars

import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/** K'ril Tsutsaroth, leader of the Zamorak faction. See kreearra.plugin.kts for notes. */
val KRIL = Npcs.KRIL_TSUTSAROTH
val table = DropTableFactory

val drops =
    table.build {
        guaranteed {
            obj(Items.BIG_BONES)
        }

        main {
            total(1000)
            obj(Items.COINS_995, quantityRange = 400..3000, slots = 300)
            obj(Items.RUNITE_ORE, quantityRange = 1..3, slots = 60)
            obj(Items.ADAMANT_BAR, quantity = 4, slots = 60)
            obj(Items.DEATH_RUNE, quantity = 45, slots = 60)
            obj(Items.LAW_RUNE, quantity = 75, slots = 60)
            obj(Items.YEW_LOGS, quantity = 70, slots = 60)
            obj(Items.MAGIC_LOGS, quantity = 15, slots = 40)

            table(Gems.gemTable, slots = 60)
            table(Rare.rareTable, slots = 40)

            // Zamorak equipment - rare
            obj(Items.STEAM_BATTLESTAFF, slots = 4)
            obj(Items.ZAMORAK_HILT, slots = 2)
            obj(Items.FROZEN_KEY_PIECE_ZAMORAK, slots = 3)

            nothing(27)
        }
    }

table.register(drops, KRIL)

on_npc_death(KRIL) {
    table.getDrop(world, npc.damageMap.getMostDamage()!! as Player, npc.id, npc.tile)
}

set_combat_def(npc = KRIL) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.SLASH
        respawnDelay = 60
    }
    stats {
        hitpoints = 2550
        attack = 310
        strength = 310
        defence = 260
        magic = 280
        ranged = 1
    }
    bonuses {
        defenceStab = 50
        defenceSlash = 50
        defenceCrush = 50
        defenceMagic = 60
        defenceRanged = 50
    }
    anims {
        // melee 14962, special 14963, block 14965. Death: the donor carries none and 14964 is absent from the 667 cache - SOURCE_BLOCKED, so no animation rather than a human one. Matrix 718 NPCCombatDefinitions / combat script ids; each id verified present in the 667 cache AnimDefs.
        attack = 14962
        block = 14965
        death = -1
    }
    aggro {
        radius = 15
    }
}
