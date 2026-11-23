package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFights
import gg.rsmod.game.model.combat.StyleType

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
        player.hit(30, type = HitType.REGULAR_HIT)
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
    configs { attackSpeed = 4; attackStyle = StyleType.CRUSH; respawnDelay = 0 }
    stats { hitpoints = 1000; attack = 70; strength = 70; defence = 100; magic = 1; ranged = 1 }
    bonuses { strengthBonus = 0; defenceStab = 5; defenceSlash = 5; defenceCrush = 5; defenceMagic = -5; defenceRanged = 5 }
    anims { attack = 5540; block = 5541; death = 5542 }
}

on_item_on_item(item1 = Items.SALVE_SHARD, item2 = Items.BALL_OF_WOOL) {
    if (!HauntedMine.isFinished(player)) return@on_item_on_item
    if (!player.inventory.contains(Items.SALVE_SHARD) || !player.inventory.contains(Items.BALL_OF_WOOL)) return@on_item_on_item
    player.inventory.remove(Items.SALVE_SHARD)
    player.inventory.remove(Items.BALL_OF_WOOL)
    player.inventory.add(Items.SALVE_AMULET, 1)
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
