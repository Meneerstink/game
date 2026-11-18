package gg.rsmod.plugins.content.npcs.definitions.demons

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems


val ids =
    intArrayOf(
        Npcs.GREATER_DEMON,
        Npcs.GREATER_DEMON_4698,
        Npcs.GREATER_DEMON_4699,
        Npcs.GREATER_DEMON_4700,
        Npcs.GREATER_DEMON_4701,
    )

val table = DropTableFactory
val greaterdemon =
    table.build {
        guaranteed {
            obj(Items.ACCURSED_ASHES)
        }
        /** Running total: 246 **/
        main {
            // N614 (15-11): exactly the OSRS Wiki Greater demon (level 92) table over 128. Before: total 246 filling 252 (every rate diluted)
            // and one fire-rune line 'quantity = (37..75).random()' (any amount 37-75) where the wiki has two lines: 75 at 8/128, 37 at 1/128.
            total(128)
            obj(Items.STEEL_2H_SWORD, quantity = 1, slots = 4)
            obj(Items.STEEL_HATCHET, quantity = 1, slots = 3)
            obj(Items.STEEL_BATTLEAXE, quantity = 1, slots = 3)
            obj(Items.MITHRIL_KITESHIELD, quantity = 1, slots = 1)
            obj(Items.ADAMANT_PLATELEGS, quantity = 1, slots = 1)
            obj(Items.RUNE_FULL_HELM, quantity = 1, slots = 1)
            obj(Items.FIRE_RUNE, quantity = 75, slots = 8)
            obj(Items.CHAOS_RUNE, quantity = 15, slots = 3)
            obj(Items.DEATH_RUNE, quantity = 5, slots = 3)
            obj(Items.FIRE_RUNE, quantity = 37, slots = 1)
            obj(Items.COINS_995, quantity = 132, slots = 40)
            obj(Items.COINS_995, quantity = 44, slots = 29)
            obj(Items.COINS_995, quantity = 220, slots = 10)
            obj(Items.COINS_995, quantity = 11, slots = 7)
            obj(Items.COINS_995, quantity = 460, slots = 1)
            nothing(slots = 2)
            obj(Items.TUNA, quantity = 1, slots = 3)
            obj(Items.GOLD_BAR, quantity = 1, slots = 2)
            obj(Items.THREAD, quantity = 10, slots = 1)
            table(Gems.gemTable, slots = 5)
        }
        table("Charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 70) // 7% drop rate
            obj(Items.GREEN_CHARM, quantity = 1, slots = 40) // 4% drop rate
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 260) // 26% drop rate
            obj(Items.BLUE_CHARM, quantity = 1, slots = 10) // 1% drop rate (estimation)
            nothing(slots = 620) // 62% drop rate for nothing
        }
    }

table.register(greaterdemon, *ids)

on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}
ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 15
            attackStyle = StyleType.SLASH
        }
        stats {
            hitpoints = 870
            attack = 76
            strength = 78
            defence = 81
            magic = 1
            ranged = 1
        }
        bonuses {
            defenceMagic = -10
        }
        anims {
            attack = Anims.GREATER_DEMON_ATTACK
            death = Anims.GREATER_DEMON_DEATH
            block = Anims.GREATER_DEMON_BLOCK
        }
        aggro {
            radius = 4
        }
        slayer {
            assignment = SlayerAssignment.GREATER_DEMON
            level = 1
            experience = 87.0
        }
    }
}
