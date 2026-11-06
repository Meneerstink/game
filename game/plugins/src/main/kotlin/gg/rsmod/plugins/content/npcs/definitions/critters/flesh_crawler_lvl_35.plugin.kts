package gg.rsmod.plugins.content.npcs.definitions.critters

import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.npc
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Herbs
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * @author Harley <https://github.com/HarleyGilpin>
 */

val ids = intArrayOf(Npcs.FLESH_CRAWLER_4390)

val table = DropTableFactory
val fleshCrawler =
    table.build {
        main {
            total(100)
            // N571 (2026-11-06): OSRS Wiki 'Flesh crawler' members drop table, out of 100 (same for levels 28/35/41). The old 667
            // table filled 280 of 256 slots and had a Black full helm + Starved ancient effigy that OSRS never drops.
            // Runes
            obj(Items.BODY_RUNE, quantityRange = 3..12, slots = 10)
            obj(Items.FIRE_RUNE, quantity = 42, slots = 4)
            obj(Items.DUST_RUNE, quantityRange = 3..9, slots = 10)
            obj(Items.NATURE_RUNE, quantity = 5, slots = 4)
            // Materials
            obj(Items.IRON_ORE, quantity = 1, slots = 5)
            obj(Items.IRON_ORE_NOTED, quantityRange = 3..7, slots = 12)
            obj(Items.SILVER_BAR, quantity = 1, slots = 2)
            // Other
            obj(Items.COINS_995, quantityRange = 5..84, slots = 7)
            obj(Items.ASHES, quantity = 1, slots = 4)
            obj(Items.BOTTOM_OF_SCEPTRE, quantity = 1, slots = 3)
            // Tables
            table(Herbs.minorHerbTable, slots = 35)
            table(Rare.rareTable, slots = 2)
            nothing(2)
        }
        table("Charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 40)
            obj(Items.GREEN_CHARM, quantity = 1, slots = 60)
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 8)
            obj(Items.BLUE_CHARM, quantity = 1, slots = 2)
            nothing(slots = 890)
        }
    }

table.register(fleshCrawler, *ids)

on_npc_pre_death(*ids) {
    val p = npc.killer() ?: return@on_npc_pre_death
    p.playSound(Sfx.CAVE_CRAWLER_DEATH)
}

on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 3
            respawnDelay = 15
            attackStyle = StyleType.SLASH
        }
        stats {
            hitpoints = 250
            attack = 80
            strength = 2
            defence = 10
        }
        bonuses {
            defenceStab = 15
            defenceSlash = 15
            defenceCrush = 15
            defenceMagic = 15
            defenceRanged = 15
        }
        anims {
            attack = Anims.FLESH_CRAWLER_ATTACK
            block = Anims.FLESH_CRAWLER_BLOCK
            death = Anims.FLESH_CRAWLER_DEATH
        }
        aggro {
            radius = 4
        }
        slayer {
            assignment = SlayerAssignment.FLESH_CRAWLER
            experience = 25.0
            level = 1
        }
    }
}
