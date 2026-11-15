package gg.rsmod.plugins.content.npcs.definitions.other

import gg.rsmod.game.action.NpcDeathAction
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.npc
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Gems
import gg.rsmod.plugins.content.drops.global.Rare

/**
 * @author Harley <https://github.com/HarleyGilpin>
 */

val ids = intArrayOf(Npcs.GARGOYLE, Npcs.GARGOYLE_9087)

val table = DropTableFactory
val gargoyle =
    table.build {
        // N614 (15-11): OSRS Wiki 'Gargoyle' drops (x/128). Pre-rolls: granite maul 1/256, mystic robe top (dark) 1/512.
        // NVT: brimstone key (no Konar), brittle key (no Grotesque Guardians). Charms = our Summoning (kept).
        main {
            total(128)
            obj(Items.ADAMANT_PLATELEGS, quantity = 1, slots = 4)
            obj(Items.RUNE_FULL_HELM, quantity = 1, slots = 3)
            obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 2)
            obj(Items.ADAMANT_BOOTS, quantity = 1, slots = 1)
            obj(Items.RUNE_BATTLEAXE, quantity = 1, slots = 1)
            obj(Items.RUNE_PLATELEGS, quantity = 1, slots = 1)

            obj(Items.FIRE_RUNE, quantity = 75, slots = 10)
            obj(Items.CHAOS_RUNE, quantity = 30, slots = 8)
            obj(Items.FIRE_RUNE, quantity = 150, slots = 6)
            obj(Items.DEATH_RUNE, quantity = 15, slots = 5)

            obj(Items.GOLD_ORE_NOTED, quantityRange = 10..20, slots = 10)
            obj(Items.PURE_ESSENCE_NOTED, quantity = 150, slots = 6)
            obj(Items.STEEL_BAR_NOTED, quantity = 15, slots = 6)
            obj(Items.GOLD_BAR_NOTED, quantityRange = 10..15, slots = 3)
            obj(Items.MITHRIL_BAR_NOTED, quantity = 15, slots = 2)
            obj(Items.RUNITE_ORE, quantity = 1, slots = 2)

            obj(Items.COINS_995, quantityRange = 400..800, slots = 28)
            obj(Items.COINS_995, quantityRange = 500..1000, slots = 20)
            obj(Items.COINS_995, quantity = 10000, slots = 5)

            table(Gems.gemTable, slots = 5)
        }
        table("Granite maul") {
            total(256)
            obj(Items.GRANITE_MAUL, quantity = 1, slots = 1)
            nothing(slots = 255)
        }
        table("Mystic robe top (dark)") {
            total(512)
            obj(Items.MYSTIC_ROBE_TOP_4101, quantity = 1, slots = 1)
            nothing(slots = 511)
        }
        table("Hard clue") {
            total(128)
            obj(Items.CLUE_SCROLL_HARD, quantity = 1, slots = 1)
            nothing(slots = 127)
        }
        table("Charms") {
            total(1000)
            obj(Items.GOLD_CHARM, quantity = 1, slots = 100)
            obj(Items.GREEN_CHARM, quantity = 1, slots = 50)
            obj(Items.CRIMSON_CHARM, quantity = 1, slots = 50)
            obj(Items.BLUE_CHARM, quantity = 1, slots = 70)
            nothing(slots = 730)
        }
    }

table.register(gargoyle, *ids)

on_npc_pre_death(Npcs.GARGOYLE_9087) {
    val p = npc.killer() ?: return@on_npc_pre_death
    p.playSound(Sfx.GARGOYLE_DEATH)
}

on_npc_death(*ids) {
    table.getDrop(world, npc.killer() ?: return@on_npc_death, npc.id, npc.tile)
}

ids.forEach {
    set_combat_def(it) {
        configs {
            attackSpeed = 4
            respawnDelay = 15
            deathDelay = 2
            attackStyle = StyleType.CRUSH
        }
        stats {
            hitpoints = 1050
            attack = 75
            strength = 105
            defence = 107
            magic = 1
            ranged = 1
        }
        bonuses {
            defenceStab = 20
            defenceSlash = 20
            defenceMagic = 20
            defenceRanged = 20
        }
        anims {
            attack = Anims.GARGOYLE_ATTACK
            block = Anims.GARGOYLE_BLOCK
            death = Anims.GARGOYLE_DEATH
        }
        aggro {
            radius = 4
        }
        slayer {
            assignment = SlayerAssignment.GARGOYLE
            experience = 105.0
            level = 75
            deathBlowLifepoints = 100
        }
    }

    on_item_on_npc(item = Items.ROCK_HAMMER, npc = it) {
        smash(player, player.getInteractingNpc())
    }
}

/*
 * The cache's "Smash" option (op 3 on 1610 only). It used to sit inside the per-id loop above, which bound 1610 op 3 several times
 * and broke boot, so it was commented out and the right-click did nothing. Bound once here, same logic as the rock hammer.
 */
ids.filter { if_npc_has_option(it, "smash") }.forEach { id ->
    on_npc_option(npc = id, option = "smash") {
        smash(player, player.getInteractingNpc())
    }
}

fun smash(player: Player, npc: Npc) {
    if (npc.getCombatTarget() != player) {
        player.message("Someone else is fighting that.") // Void 667 Gargoyle.kt
        return
    }
    if (npc.getCurrentLifepoints() > npc.combatDef.deathBlowLifepoints) {
        player.filterableMessage("The gargoyle is not weak enough to smash yet.")
        return
    }
    if (!player.inventory.contains(Items.ROCK_HAMMER)) {
        player.filterableMessage("You need a rock hammer to smash the gargoyle.")
        return
    }
    player.animate(Anims.HAMMER_GARGOYLE)
    npc.setTransmogId(Npcs.GARGOYLE_1827)
    npc.setCurrentLifepoints(0)
    npc.executePlugin(NpcDeathAction.deathPlugin)
    player.filterableMessage("You smash the gargoyle with the rock hammer and it shatters into pieces.")
}
