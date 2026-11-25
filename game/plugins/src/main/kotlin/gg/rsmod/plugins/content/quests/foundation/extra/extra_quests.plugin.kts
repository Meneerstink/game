package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFights
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.plugins.api.NpcSpecies

spawn_npc(npc = Npcs.VESTRI, x = 2821, z = 3487, direction = Direction.SOUTH)

// Every option below was checked against the revision-667 cache. Existing spawns are reused.
listOf(Npcs.AUSTRI, Npcs.VESTRI, Npcs.BONZO).forEach { id ->
    on_npc_option(id, "Talk-to") {
        player.queue {
            if (!FishingContest.isFinished(player) && FishingContest.stage(player) > 0) {
                if (id == Npcs.AUSTRI || id == Npcs.VESTRI) ExtraQuests.reclaim(player, Items.FISHING_PASS)
                if (id == Npcs.BONZO && FishingContest.stage(player) == 3) ExtraQuests.reclaim(player, Items.FISHING_TROPHY)
            }
            FoundationQuests.talk(this, if (id == Npcs.VESTRI) Npcs.AUSTRI else id)
        }
    }
}

spawn_npc(npc = Npcs.THORMAC, x = 2702, z = 3406, height = 3, direction = Direction.SOUTH)
spawn_npc(npc = Npcs.KHARID_SCORPION, x = 2877, z = 9796, direction = Direction.SOUTH)
on_npc_option(Npcs.THORMAC, "Talk-to") {
    player.queue {
        if (!ScorpionCatcher.isFinished(player)) ScorpionCatcher.reclaimCage(player)
        if (!FoundationQuests.talk(this, Npcs.THORMAC)) ScorpionCatcher.enchant(this)
    }
}
on_npc_option(Npcs.SEER, "Talk-to") { player.queue { FoundationQuests.talk(this, Npcs.SEER) } }
ScorpionCatcher.SCORPIONS.keys.forEach { id ->
    // OSRS Pick-up always stings; catching uses the cage on the npc, even when a cage is carried.
    on_npc_option(id, "Pick-up") {
        player.message("The scorpion stings you!")
        player.hit(3, type = HitType.REGULAR_HIT)
    }
    ScorpionCatcher.CAGES.forEach { cage ->
        on_item_on_npc(item = cage, npc = id) {
            if (ScorpionCatcher.capture(player, id)) player.queue { FoundationQuests.offerNextStep(this, ScorpionCatcher, false) }
        }
    }
}
on_npc_option(Npcs.BONZO, "Pay") { player.queue { FishingContest.pay(this) } }

on_npc_option(Npcs.ZEALOT, "Talk-to") {
    player.queue { if (!FoundationQuests.talk(this, Npcs.ZEALOT)) HauntedMine.returnKey(this) }
}
on_npc_option(Npcs.ZEALOT, "Pickpocket") {
    player.queue {
        if (HauntedMine.stealKey(player)) FoundationQuests.offerNextStep(this, HauntedMine, false)
    }
}
spawn_npc(npc = Npcs.INNOCENTLOOKING_KEY, x = 2788, z = 4455, direction = Direction.SOUTH)
on_npc_option(Npcs.INNOCENTLOOKING_KEY, "Take") {
    if (!HauntedMine.isFinished(player)) {
        when (HauntedMine.stage(player)) {
            4 -> QuestFights.start(player, HauntedMine, 4)
            5 -> ExtraQuests.reclaim(player, Items.CRYSTALMINE_KEY)
        }
    }
}
listOf(Objs.GLOWING_FUNGUS_4932, Objs.GLOWING_FUNGUS_4933).forEach { fungus ->
    on_obj_option(fungus, "Pick") {
        if (!HauntedMine.isFinished(player) && HauntedMine.stage(player) in 2..4) {
            if (HauntedMine.stage(player) == 2) FoundationQuests.trySearch(player, HauntedMine, 2)
            else ExtraQuests.reclaim(player, Items.GLOWING_FUNGUS)
        }
    }
}
listOf(Objs.CRYSTAL_OUTCROP, Objs.CRYSTAL_OUTCROP_4927, Objs.CRYSTAL_OUTCROP_4928).forEach { crystal ->
    on_obj_option(crystal, "Cut") {
        if (!HauntedMine.isFinished(player)) FoundationQuests.trySearch(player, HauntedMine, HauntedMine.stage(player))
        else if (player.inventory.contains(Items.CHISEL) && player.skills.getCurrentLevel(Skills.CRAFTING) >= 35) {
            FoundationRewards.grant(player, Items.SALVE_SHARD, 1)
        } else player.message("You need a chisel and level 35 Crafting to cut salve shards.")
    }
}

// Reduced QuestFight: exact wiki base stats and melee max hit (8); machinery/teleport phases are omitted.
// The old combat-defs entry gave a strength bonus of 69, incorrectly turning its melee hit into 15.
set_combat_def(npc = Npcs.TREUS_DAYTH) {
    configs { attackSpeed = 4; attackStyle = StyleType.CRUSH; respawnDelay = 0; poisonImmune = true }
    stats { hitpoints = 1000; attack = 70; strength = 70; defence = 100; magic = 1; ranged = 1 }
    bonuses { strengthBonus = 0; defenceStab = 5; defenceSlash = 5; defenceCrush = 5; defenceMagic = -5; defenceRanged = 5 }
    anims { attack = 5540; block = 5541; death = 5542 }
    species { +NpcSpecies.UNDEAD }
    slayer { assignment = SlayerAssignment.GHOST; level = 1; experience = 100.0 }
}

on_item_on_item(item1 = Items.SALVE_SHARD, item2 = Items.BALL_OF_WOOL) {
    if (!HauntedMine.isFinished(player)) return@on_item_on_item
    if (!player.inventory.contains(Items.SALVE_SHARD) || !player.inventory.contains(Items.BALL_OF_WOOL)) return@on_item_on_item
    player.inventory.remove(Items.SALVE_SHARD)
    player.inventory.remove(Items.BALL_OF_WOOL)
    player.inventory.add(Items.SALVE_AMULET, 1)
}

spawn_npc(npc = Npcs.MISTAG, x = 3320, z = 9615, direction = Direction.WEST)
spawn_npc(npc = Npcs.KAZGAR, x = 3230, z = 9611, direction = Direction.EAST)
// The brooch is a real, replaceable ground find, also after the automatic first search.
spawn_item(item = Items.BROOCH, amount = 1, x = 3230, z = 9610, respawnCycles = 10)
on_npc_option(Npcs.SIGMUND_2082, "Talk-to") {
    player.queue {
        if (!FoundationQuests.talk(this, Npcs.SIGMUND_2082) && TheLostTribe.isFinished(player)) {
            chatNpc("...And I would have gotten away with it too,", "if it wasn't for that pesky adventurer!")
        }
    }
}
on_npc_option(Npcs.SIGMUND_2082, "Pickpocket") {
    player.queue {
        if (TheLostTribe.stealKey(player)) {
            with(TheLostTribe) { offerTravel("the castle spinning wheel room", CHEST_POST) }
        }
    }
}
on_obj_option(Objs.CHEST_6910, "Open") {
    player.queue {
        if (TheLostTribe.openChest(player)) {
            with(TheLostTribe) { offerTravel("the H.A.M. hideout entrance", CRATE_POST) }
        }
    }
}
on_obj_option(Objs.CRATE_6911, "Search") {
    player.queue {
        if (TheLostTribe.searchCrate(player)) {
            with(TheLostTribe) { offerTravel("Lumbridge Castle's first floor", startLocation!!, Npcs.DUKE_HORACIO) }
        }
    }
}
listOf(Npcs.MISTAG, Npcs.KAZGAR).forEach { id ->
    on_npc_option(id, "Talk-to") {
        player.queue {
            if (!FoundationQuests.talk(this, id) && (TheLostTribe.isFinished(player) || TheLostTribe.stage(player) >= 2)) {
                with(TheLostTribe) {
                    if (id == Npcs.KAZGAR) offerTravel("the Dorgeshuun mines", MISTAG_POST, Npcs.MISTAG)
                    else offerTravel("the cellar tunnel entrance", BROOCH_POST, Npcs.KAZGAR)
                }
            }
        }
    }
    on_npc_option(id, "Follow") {
        player.queue {
            if (TheLostTribe.isFinished(player) || TheLostTribe.stage(player) >= 2) {
                with(TheLostTribe) {
                    if (id == Npcs.KAZGAR) offerTravel("the Dorgeshuun mines", MISTAG_POST, Npcs.MISTAG)
                    else offerTravel("the cellar tunnel entrance", BROOCH_POST, Npcs.KAZGAR)
                }
            }
        }
    }
}

spawn_npc(npc = Npcs.EFFIGY, x = 2640, z = 3218, direction = Direction.EAST)
spawn_npc(npc = Npcs.THE_GUNS, x = 2643, z = 3226, direction = Direction.EAST)
spawn_npc(npc = Npcs.HOMUNCULUS, x = 2649, z = 3218, height = 3, direction = Direction.WEST)
listOf(Npcs.EFFIGY, Npcs.BONAFIDO, Npcs.HOMUNCULUS).forEach { id ->
    on_npc_option(id, "Talk-to") {
        player.queue {
            if (!FoundationQuests.talk(this, id) && TowerOfLife.isFinished(player)) {
                with(TowerOfLife) { offerTravel("the Tower of Life basement", BASEMENT) }
            }
        }
    }
}
on_npc_option(Npcs.BLACKEYE, "Talk-to") { player.queue { TowerOfLife.hardHat(this) } }
on_npc_option(Npcs.THE_GUNS, "Talk-to") {
    player.queue {
        if (TowerOfLife.canGetOutfit(player)) {
            chatNpc("Bring me a regular beer and you can have a shirt.")
            TowerOfLife.shirt(player)
        }
    }
}
on_npc_option(Npcs.GUMMY, "Talk-to") {
    player.queue {
        if (TowerOfLife.canGetOutfit(player)) {
            chatNpc("My spare trousers blew off the washing line.", "Search the plants around the tower.")
        }
    }
}
on_npc_option(Npcs.NO_FINGERS, "Talk-to") {
    player.queue {
        if (TowerOfLife.canGetOutfit(player)) chatNpc("Only real builders can wear these boots.", "I'm not giving them to you!")
    }
}
on_obj_option(Objs.PLANT_21924, "Search") {
    if (TowerOfLife.canGetOutfit(player)) ExtraQuests.reclaim(player, Items.BUILDERS_TROUSERS)
}
on_obj_option(Objs.PRESSURE_MACHINE, "Fix") {
    if (!TowerOfLife.isFinished(player) && TowerOfLife.stage(player) == 2) FoundationQuests.trySearch(player, TowerOfLife, 2)
}
// TOWER-KELDERLUIK (24-11): OSRS Wiki "Trapdoor (Tower of Life)" (objects 21921/21922): on the tower's ground floor, leads to the
// basement, usable after partial completion of the quest. Neither the 667 nor the OSRS map holds it (a server spawn), and no
// source gives its tile: owner 24-11 chose a free ground-floor tile beside the crates (2651,3220). Partial completion = the
// alchemists created the Homunculus (step 2 done).
spawn_obj(obj = Objs.TRAPDOOR_21921, x = TowerOfLife.TRAPDOOR.x, z = TowerOfLife.TRAPDOOR.z, type = 22, rot = 0)
fun towerBasementOpen(player: gg.rsmod.game.model.entity.Player): Boolean = TowerOfLife.isFinished(player) || TowerOfLife.stage(player) >= 3
on_obj_option(Objs.TRAPDOOR_21921, "Open") {
    val trapdoor = player.getInteractingGameObj()
    if (!towerBasementOpen(player)) {
        player.message("You need to help the builders finish the tower before you can go down there.")
        return@on_obj_option
    }
    player.lockingQueue(lockState = LockState.FULL) {
        player.animate(Anims.REACH_FORWARD_OPEN)
        wait(1)
        world.spawn(DynamicObject(Objs.TRAPDOOR_21922, trapdoor.type, trapdoor.rot, trapdoor.tile))
    }
}
on_obj_option(Objs.TRAPDOOR_21922, "Close") {
    val trapdoor = player.getInteractingGameObj()
    player.lockingQueue(lockState = LockState.FULL) {
        player.animate(Anims.REACH_FORWARD_OPEN)
        wait(1)
        world.spawn(DynamicObject(Objs.TRAPDOOR_21921, trapdoor.type, trapdoor.rot, trapdoor.tile))
    }
}
on_obj_option(Objs.TRAPDOOR_21922, "Climb-down") {
    if (!towerBasementOpen(player)) {
        player.message("You need to help the builders finish the tower before you can go down there.")
        return@on_obj_option
    }
    player.lockingQueue(lockState = LockState.FULL) {
        player.animate(Anims.REACH_DOWN)
        wait(2)
        // NOT-QUEST-TRAVEL: climbing down the Tower of Life trapdoor is an ordinary object move; OSRS shows no warning.
        player.moveTo(TowerOfLife.BASEMENT)
    }
}
// 17974 is both the basement ladder (OSRS 3038,4375) and the tower's floor-2 ladder (2647,3221 -> 17975 on floor 3). Before
// 24-11 every 17974 sent the player outside the tower, the floor-2 one too; now the basement one climbs to the trapdoor and the
// tower one climbs a floor like the ladder fallback.
on_obj_option(17974, "Climb-up") {
    val ladder = player.getInteractingGameObj()
    val destination = if (player.tile.z > 4000) TowerOfLife.TRAPDOOR_EXIT else gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.destination(
        world, ladder, player.tile, gg.rsmod.plugins.content.mechanics.ladders.GenericLadders.Direction.UP)
    if (destination == null) {
        player.message(gg.rsmod.game.model.entity.Entity.NOTHING_INTERESTING_HAPPENS)
        return@on_obj_option
    }
    player.lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.animate(Anims.LADDER_CLIMB, idleOnly = true)
        wait(2)
        // NOT-QUEST-TRAVEL: a ladder climb (basement -> trapdoor, or one tower floor up); OSRS shows no warning.
        player.moveTo(destination)
    }
}

// Repeatable gathering after losing the worm; the automatic search is the short quest's first step.
listOf(58, 2013, 2989, 2990, 2991, 2992, 2993, 2994).filter { if_obj_has_option(it, "Check") }.forEach { vine ->
    on_obj_option(vine, "Check") {
        if (!FishingContest.isFinished(player) && FishingContest.stage(player) in 1..2) {
            if (!player.inventory.contains(Items.SPADE)) player.message("You need a spade to dig for red vine worms.")
            else if (FishingContest.stage(player) == 1) FoundationQuests.trySearch(player, FishingContest, 1)
            else ExtraQuests.reclaim(player, Items.RED_VINE_WORM)
        }
    }
}
