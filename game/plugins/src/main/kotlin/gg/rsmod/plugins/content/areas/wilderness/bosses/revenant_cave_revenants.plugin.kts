package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.priv.Privilege

/**
 * N522 bundle 3b: the OSRS revenants of the OSRS Revenant Caves ([RevenantCaveRevenants]) and their two items ([RevenantGear]).
 */

RevenantCaveRevenants.REVS.forEach { rev ->
    set_combat_def(npc = rev.id) {
        configs {
            attackSpeed = rev.speed
            attackStyle = rev.melee
            respawnDelay = rev.respawn
        }
        aggro {
            radius = 5
            alwaysAggro()
        }
        stats {
            hitpoints = rev.hp * 10
            attack = rev.att
            strength = rev.str
            defence = rev.def
            magic = rev.mage
            ranged = rev.range
        }
        bonuses {
            attackBonus = rev.attBns
            strengthBonus = rev.strBns
            attackMagic = rev.aMagic
            magicDamageBonus = rev.mBns
            attackRanged = rev.aRange
            rangedStrengthBonus = rev.rngBns
            defenceStab = rev.dStab
            defenceSlash = rev.dSlash
            defenceCrush = rev.dCrush
            defenceMagic = rev.dMagic
            defenceRanged = rev.dRange
        }
        anims {
            attack = rev.attack
            block = rev.defend
            death = rev.death
        }
    }

    rev.spawns.forEach { t -> spawn_npc(npc = rev.id, x = t.x, z = t.z, walkRadius = 4, direction = Direction.SOUTH) }

    DropTableFactory.register(RevenantCaveRevenants.table(rev.combat), rev.id)

    // A charged Bracelet of ethereum makes revenants tolerant (OSRS Wiki); otherwise they are aggressive.
    on_npc_spawn(rev.id) {
        npc.aggroCheck = { _, p -> !RevenantGear.wearsCharged(p) }
    }
}

on_npc_combat(*RevenantCaveRevenants.Combat.ids) {
    npc.queue {
        RevenantCaveRevenants.Combat.handleSpecialCombat(this)
    }
}

on_npc_death(*RevenantCaveRevenants.ids) {
    // N522 bundle 3c: every revenant slain feeds the Revenant maledictus' rolling spawn chance (its combat level).
    RevenantMaledictus.onRevenantDeath(world, RevenantCaveRevenants.BY_ID.getValue(npc.id).combat)
    val killer = npc.damageMap.getMostDamage() as? Player ?: return@on_npc_death
    DropTableFactory.getDrop(world, killer, npc.id, npc.tile)
    // Toggled absorption: the ether (left out of the table for this player) goes into the bracelet.
    if (RevenantGear.absorbsEther(killer)) {
        val rev = RevenantCaveRevenants.BY_ID.getValue(npc.id)
        val amount = RevenantCaveRevenants.evenEther(world.random(2..RevenantCaveRevenants.maxEther(rev.combat)))
        RevenantGear.absorbEther(killer, amount, npc.tile)
    }
}

// ------------------------------------------------------------------ Bracelet of ethereum

fun etherUsedOn(player: Player, bracelet: Int): Int? {
    val a = player.attr[INTERACTING_ITEM_SLOT] ?: return null
    val b = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return null
    return listOf(a, b).firstOrNull { player.inventory[it]?.id == bracelet }
}

listOf(Items.BRACELET_OF_ETHEREUM, Items.BRACELET_OF_ETHEREUM_UNCHARGED).forEach { bracelet ->
    on_item_on_item(item1 = Items.REVENANT_ETHER, item2 = bracelet) {
        val slot = etherUsedOn(player, bracelet) ?: return@on_item_on_item
        RevenantGear.charge(player, slot)
    }
    on_item_option(item = bracelet, option = "toggle-absorption") { RevenantGear.toggleAbsorption(player) }
    on_equipment_option(item = bracelet, option = "toggle absorption") { RevenantGear.toggleAbsorption(player) }
    on_equipment_option(item = bracelet, option = "check") {
        val worn = RevenantGear.wornBracelet(player) ?: return@on_equipment_option
        RevenantGear.check(player, worn)
    }
}

on_item_option(item = Items.BRACELET_OF_ETHEREUM, option = "check") {
    val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
    RevenantGear.check(player, item)
}

on_item_option(item = Items.BRACELET_OF_ETHEREUM, option = "uncharge") {
    RevenantGear.uncharge(player, player.getInteractingItemSlot())
}

on_item_option(item = Items.BRACELET_OF_ETHEREUM_UNCHARGED, option = "dismantle") {
    val slot = player.getInteractingItemSlot()
    player.queue {
        // Kronos: "Are you sure you want to dismantle it?" / "The item will be destroyed and you will receive 250 revenant ethers."
        if (confirmItemAction(Items.BRACELET_OF_ETHEREUM_UNCHARGED, "Are you sure you want to dismantle it?", "The item will be destroyed and you will receive 250 revenant ethers.")) {
            RevenantGear.dismantle(player, slot)
        }
    }
}

// ------------------------------------------------------------------ Amulet of avarice

on_item_equip(Items.AMULET_OF_AVARICE) { RevenantGear.onAvariceEquip(player) }
on_timer(RevenantGear.AVARICE_TIMER) { RevenantGear.refreshAvariceSkull(player) }
on_login { if (RevenantGear.wearsAvarice(player)) RevenantGear.onAvariceEquip(player) }
// "Check" (worn) is not handled: its OSRS message is not on the wiki or in a donor.

// ------------------------------------------------------------------ Revenant maledictus (N522 bundle 3c)

// OSRS Wiki infobox: level 397, 1,250 hitpoints, attack speed 5, aggressive, poison and venom immune.
set_combat_def(npc = RevenantMaledictus.NPC) {
    configs {
        attackSpeed = 5
        attackStyle = StyleType.RANGED
        respawnDelay = Int.MAX_VALUE
    }
    aggro {
        radius = RevenantMaledictus.ATTACK_RANGE
        alwaysAggro()
    }
    stats {
        hitpoints = 12500
        attack = 106
        strength = 126
        defence = 90
        magic = 130
        ranged = 130
    }
    bonuses {
        attackBonus = 72
        strengthBonus = 78
        attackMagic = 60
        magicDamageBonus = 60
        attackRanged = 265
        rangedStrengthBonus = 60
        defenceStab = 201
        defenceSlash = 206
        defenceCrush = 188
        defenceMagic = 101
        defenceRanged = 231
    }
    anims {
        attack = RevenantMaledictus.ANIM_STANDARD
        death = RevenantMaledictus.ANIM_DEATH
    }
}

on_npc_combat(*RevenantMaledictus.Combat.ids) {
    npc.queue {
        RevenantMaledictus.Combat.handleSpecialCombat(this)
    }
}

on_npc_death(RevenantMaledictus.NPC) {
    RevenantMaledictus.onDeath(npc)
}

// Test path for the test server: ::maledictus [spawn index 0-10] spawns it at once (the OSRS spawn is a rare roll on revenant kills);
// ::maledictus hp <hitpoints> sets the active one's hitpoints (to test the kill and the drops without a long fight).
on_command("maledictus", Privilege.ADMIN_POWER) {
    val values = RevenantMaledictus.Spawn.values()
    val args = player.getCommandArgs()
    if (args.firstOrNull() == "hp") {
        val npc = RevenantMaledictus.instance?.takeIf { RevenantMaledictus.active() } ?: return@on_command player.message("No Revenant maledictus is active.")
        val hp = args.getOrNull(1)?.toIntOrNull() ?: return@on_command player.message("::maledictus hp <hitpoints>")
        npc.setCurrentLifepoints(hp.coerceIn(1, npc.getMaximumLifepoints()))
        return@on_command player.message("Revenant maledictus hitpoints: ${npc.getCurrentLifepoints()}.")
    }
    val index = player.getCommandArgs().firstOrNull()?.toIntOrNull()?.coerceIn(0, values.size - 1) ?: 0
    val ok = RevenantMaledictus.spawn(world, values[index])
    player.message(if (ok) "Revenant maledictus spawned: ${values[index]}." else "A Revenant maledictus is already active.")
}
