package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.priv.Privilege

/**
 * N522 bundle 3b: the OSRS revenants of the OSRS Revenant Caves ([RevenantCaveRevenants]) and their two items ([RevenantGear]).
 */

/** OSRS Wiki "Revenant <kind>" infoboxes, slayxp (2026-11-05). */
val REVENANT_SLAYER_XP = mapOf(
    Npcs.REVENANT_IMP_OSRS to 10.0, Npcs.REVENANT_GOBLIN_OSRS to 14.0, Npcs.REVENANT_PYREFIEND_OSRS to 48.0,
    Npcs.REVENANT_HOBGOBLIN_OSRS to 73.0, Npcs.REVENANT_CYCLOPS_OSRS to 119.0, Npcs.REVENANT_HELLHOUND_OSRS to 86.0,
    Npcs.REVENANT_DEMON_OSRS to 86.0, Npcs.REVENANT_ORK_OSRS to 115.0, Npcs.REVENANT_DARK_BEAST_OSRS to 157.0,
    Npcs.REVENANT_KNIGHT_OSRS to 168.0, Npcs.REVENANT_DRAGON_OSRS to 186.0,
)

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
        // N522 fix 3: Slayer xp per revenant (OSRS Wiki infobox slayxp, fetched 2026-11-05; 0 XP before).
        slayer { experience = REVENANT_SLAYER_XP.getValue(rev.id) }
    }

    rev.spawns.forEach { t -> spawn_npc(npc = rev.id, x = t.x, z = t.z, walkRadius = 4, direction = Direction.SOUTH) }

    DropTableFactory.register(RevenantCaveRevenants.table(rev.combat), rev.id)

    // A charged Bracelet of ethereum makes revenants tolerant (OSRS Wiki); otherwise they are aggressive.
    on_npc_spawn(rev.id) {
        npc.aggroCheck = { _, p -> !RevenantGear.wearsCharged(p) }
        RevenantCaveRevenants.resetHeals(npc) // a new life rolls its own 8-36 heals
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
        val amount = RevenantCaveRevenants.rollEther(rev.combat)
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

// Warning before wearing it - donor Near Reality AmuletOfAvariceEquipPlugin (Kris): "Wearing this amulet will give you a PK skull."
// with "Give me a PK skull." / "Cancel."; only the confirmed second attempt goes through.
val AVARICE_CONFIRMED = gg.rsmod.game.model.attr.AttributeKey<Boolean>()

can_equip_item(Items.AMULET_OF_AVARICE) {
    if (player.attr[AVARICE_CONFIRMED] == true) {
        player.attr.remove(AVARICE_CONFIRMED)
        return@can_equip_item true
    }
    player.queue {
        if (options("Give me a PK skull.", "Cancel.", title = "Wearing this amulet will give you a PK skull.") != 1) return@queue
        val slot = player.inventory.getItemIndex(Items.AMULET_OF_AVARICE, skipAttrItems = false)
        val item = player.inventory[slot.coerceAtLeast(0)]?.takeIf { slot >= 0 && it.id == Items.AMULET_OF_AVARICE } ?: return@queue
        player.attr[AVARICE_CONFIRMED] = true
        gg.rsmod.game.action.EquipAction.equip(player, item, slot)
        player.attr.remove(AVARICE_CONFIRMED)
    }
    false
}

on_item_equip(Items.AMULET_OF_AVARICE) { RevenantGear.onAvariceEquip(player) }
on_timer(RevenantGear.AVARICE_TIMER) { RevenantGear.refreshAvariceSkull(player) }
on_login { if (RevenantGear.wearsAvarice(player)) RevenantGear.onAvariceEquip(player) }
// "Check" (worn) is not handled: its OSRS message is not on the wiki or in a donor.

// ------------------------------------------------------------------ Revenant maledictus (N522 bundle 3c)

// OSRS Wiki infobox: level 397, 1,250 hitpoints, attack speed 5, aggressive, poison and venom immune.
set_combat_def(npc = RevenantMaledictus.NPC) {
    configs {
        waitFullDeathAnimation = true
        attackSpeed = 5
        attackStyle = StyleType.RANGED
        respawnDelay = Int.MAX_VALUE
        poisonImmune = true
        venomImmune = true
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
    // N522 fix 3: OSRS Wiki "Revenant maledictus" infobox: slayxp 920 (Krystilia boss task; 0 XP before).
    slayer { experience = 920.0 }
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

// Forinthry Surge around the amulet - donor Near Reality ForinthrySurge.kt / AmuletOfAvaricePlugin.kt (Andys1814): "Check" tells the time
// left, taking the amulet off ends the surge, and running out says so (OSRS Wiki: the surge needs the amulet worn).
fun surgeLost(player: Player) = player.message("<col=4f006f>You have lost your Forinthry surge.</col>")

on_equipment_option(item = Items.AMULET_OF_AVARICE, option = "check") {
    val ticks = if (player.timers.has(RevenantMaledictus.FORINTHRY_SURGE)) player.timers[RevenantMaledictus.FORINTHRY_SURGE] else 0
    if (ticks <= 0) {
        player.message("You don't have Forinthry surge.")
        return@on_equipment_option
    }
    player.message("You have ${RevenantGear.surgeTimeText(ticks)} of Forinthry surge remaining.")
}

on_item_unequip(Items.AMULET_OF_AVARICE) {
    if (!player.timers.has(RevenantMaledictus.FORINTHRY_SURGE)) return@on_item_unequip
    player.timers.remove(RevenantMaledictus.FORINTHRY_SURGE)
    surgeLost(player)
}

on_timer(RevenantMaledictus.FORINTHRY_SURGE) { surgeLost(player) }
