package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare
import gg.rsmod.plugins.content.drops.global.Seeds

val ids = intArrayOf(Npcs.NECHRYAEL)

val table = DropTableFactory
val nechryael =
    table.build {
        // N614 (15-11): OSRS Wiki 'Nechryael' drops (x/116; doubled to x/232 for the 10.5 and 2.5 coin lines). Malicious ashes ->
        // ACCURSED_ASHES (as the greater demon). Rare seed table 18/116 is rolled TWICE (wiki); limpwurt seed separately 6/116.
        // NVT: brimstone key (no Konar). Charms = our Summoning (kept).
        guaranteed {
            obj(Items.ACCURSED_ASHES)
        }
        main {
            total(232)
            obj(Items.ADAMANT_PLATELEGS, quantity = 1, slots = 8)
            obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 8)
            obj(Items.RUNE_FULL_HELM, quantity = 1, slots = 6)
            obj(Items.ADAMANT_KITESHIELD, quantity = 1, slots = 4)
            obj(Items.RUNE_BOOTS, quantity = 1, slots = 2)

            obj(Items.CHAOS_RUNE, quantity = 37, slots = 16)
            obj(Items.DEATH_RUNE, quantity = 5, slots = 12)
            obj(Items.DEATH_RUNE, quantity = 10, slots = 12)
            obj(Items.LAW_RUNE, quantityRange = 25..35, slots = 10)
            obj(Items.BLOOD_RUNE, quantityRange = 15..20, slots = 8)

            table(Seeds.rareSeedTable, slots = 36, rolls = 2)
            obj(Items.LIMPWURT_SEED, quantity = 1, slots = 12)

            obj(Items.COINS_995, quantityRange = 1000..1499, slots = 26)
            obj(Items.COINS_995, quantityRange = 1500..2000, slots = 21)
            obj(Items.COINS_995, quantityRange = 2500..2999, slots = 12)
            obj(Items.COINS_995, quantityRange = 3000..3500, slots = 6)
            obj(Items.COINS_995, quantityRange = 500..999, slots = 5)
            obj(Items.COINS_995, quantity = 5000, slots = 2)

            obj(Items.SOFT_CLAY_NOTED, quantity = 25, slots = 8)
            obj(Items.TUNA, quantity = 1, slots = 6)

            table(Rare.rareTable, slots = 2)
            table(Gems.gemTable, slots = 10)
        }
        table("Hard clue") {
            total(128)
            obj(Items.CLUE_SCROLL_HARD, quantity = 1, slots = 1)
            nothing(slots = 127)
        }
        table("Charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 90)
            obj(Items.GREEN_CHARM, quantity = 1, slots = 40)
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 310)
            obj(Items.BLUE_CHARM, quantity = 1, slots = 11)
            nothing(slots = 549)
        }
    }

table.register(nechryael, *ids)


on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 59
            attackStyle = StyleType.SLASH
        }
        stats {
            hitpoints = 1050
            attack = 97
            strength = 97
            defence = 105
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
            attack = Anims.NECHRYAEL_ATTACK
            death = Anims.NECHRYAEL_DEATH
            block = Anims.NECHRYAEL_BLOCK
        }
        slayer {
            assignment = SlayerAssignment.NECHRYAEL
            level = 80
            experience = 105.0
        }
    }
}
