package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Herbs
import gg.rsmod.plugins.content.drops.global.Rare

val ids = intArrayOf(Npcs.ABYSSAL_DEMON, Npcs.ABYSSAL_DEMON_4230, Npcs.ABYSSAL_DEMON_9086)

val table = DropTableFactory
val abyssalDemon =
    table.build {
        // N614 (15-11): OSRS Wiki 'Abyssal demon' drops, Standard version (x/128). Abyssal ashes -> INFERNAL_ASHES (our ashes for
        // OSRS abyssal ashes). The /128 lines fill 128 exactly, so the whip (1/512) and dagger (1/32000) are separate rolls.
        // NVT: ensouled abyssal head (no Arceuus), brimstone key (no Konar), Catacombs/Wilderness Slayer Cave tables (not in our game).
        guaranteed {
            obj(Items.INFERNAL_ASHES)
        }
        main {
            total(128)
            obj(Items.BLACK_SWORD, quantity = 1, slots = 4)
            obj(Items.STEEL_BATTLEAXE, quantity = 1, slots = 3)
            obj(Items.BLACK_HATCHET, quantity = 1, slots = 2)
            obj(Items.MITHRIL_KITESHIELD, quantity = 1, slots = 1)
            obj(Items.RUNE_CHAINBODY, quantity = 1, slots = 1)
            obj(Items.RUNE_MED_HELM, quantity = 1, slots = 1)

            obj(Items.AIR_RUNE, quantity = 50, slots = 8)
            obj(Items.CHAOS_RUNE, quantity = 10, slots = 7)
            obj(Items.BLOOD_RUNE, quantity = 7, slots = 4)
            obj(Items.LAW_RUNE, quantity = 3, slots = 1)

            table(Herbs.minorHerbTable, slots = 19)

            obj(Items.PURE_ESSENCE_NOTED, quantity = 60, slots = 5)
            obj(Items.ADAMANT_BAR, quantity = 1, slots = 2)

            obj(Items.COINS_995, quantity = 132, slots = 35)
            obj(Items.COINS_995, quantity = 220, slots = 9)
            obj(Items.COINS_995, quantity = 30, slots = 7)
            obj(Items.COINS_995, quantity = 44, slots = 6)
            obj(Items.COINS_995, quantity = 460, slots = 1)

            obj(Items.LOBSTER, quantity = 1, slots = 2)
            obj(Items.COSMIC_TALISMAN, quantity = 1, slots = 1)
            obj(Items.CHAOS_TALISMAN, quantity = 1, slots = 1)
            obj(Items.DEFENCE_POTION_3, quantity = 1, slots = 1)

            table(Rare.rareTable, slots = 2)
            table(Gems.gemTable, slots = 5)
        }
        table("Abyssal whip") {
            total(512)
            obj(Items.ABYSSAL_WHIP, quantity = 1, slots = 1)
            nothing(slots = 511)
        }
        table("Abyssal dagger") {
            total(32000)
            obj(Items.ABYSSAL_DAGGER, quantity = 1, slots = 1)
            nothing(slots = 31999)
        }
        table("Hard clue") {
            total(128)
            obj(Items.CLUE_SCROLL_HARD, quantity = 1, slots = 1)
            nothing(slots = 127)
        }
        table("Elite clue") {
            total(1200)
            obj(Items.CLUE_SCROLL_ELITE, quantity = 1, slots = 1)
            nothing(slots = 1199)
        }
        table("Abyssal head") {
            total(6000)
            obj(Items.ABYSSAL_HEAD, quantity = 1, slots = 1)
            nothing(slots = 5999)
        }
    }

table.register(abyssalDemon, *ids)

on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}
ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 3
            attackStyle = StyleType.SLASH
        }
        stats {
            hitpoints = 1500
            attack = 97
            strength = 67
            defence = 135
            magic = 1
            ranged = 1
        }
        bonuses {
            defenceStab = 20
            defenceSlash = 20
            defenceCrush = 20
            defenceMagic = 0
            defenceRanged = 20
        }
        anims {
            attack = Anims.ABYSSAL_DEMON_ATTACK
            death = Anims.ABYSSAL_DEMON_DEATH
            block = Anims.ABYSSAL_DEMON_BLOCK
        }
        slayer {
            assignment = SlayerAssignment.ABYSSAL_DEMON
            level = 85
            experience = 150.0
        }
    }
}
