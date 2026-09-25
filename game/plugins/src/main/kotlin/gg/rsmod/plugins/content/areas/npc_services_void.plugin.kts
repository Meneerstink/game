package gg.rsmod.plugins.content.areas

import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.interpolate

/*
 * NPC options the cache offers that nothing handled, ported from Void (owner 2026-09-24: "Check alle donors Void en Novite voor dingen
 * die onze rsps nog niet heeft ... denk daarbij aan unhandled npcs items object ... (geen questen)"). Each block names its Void source;
 * quest steps Void ties to these npcs are left out (no quests here).
 */

// Void DesertPhoenix.kt - "Grab-feather": 25 Thieving, success(level, 100..240), 26 xp; on failure the phoenix squawks, strikes and
// stuns (8 ticks, 10 damage - the same units as the pickpocket table).
on_npc_option(npc = Npcs.DESERT_PHOENIX, option = "Grab-feather") {
    val phoenix = player.getInteractingNpc()
    if (player.inventory.contains(Items.PHOENIX_FEATHER) || player.inventory.contains(Items.PHOENIX_QUILL_PEN)) {
        player.message("You already have a phoenix tail-feather.")
        return@on_npc_option
    }
    if (player.skills.getCurrentLevel(Skills.THIEVING) < 25) {
        player.message("You need to be a level 25 thief to grab the phoenix's tail-feather.")
        return@on_npc_option
    }
    player.queue {
        player.filterableMessage("You attempt to grab the phoenix's tail-feather.")
        wait(1)
        if (interpolate(100, 240, player.skills.getCurrentLevel(Skills.THIEVING)) > world.random(255)) {
            player.animate(Anims.PICKPOCKET)
            if (player.inventory.add(Items.PHOENIX_FEATHER).hasFailed()) world.spawn(GroundItem(Items.PHOENIX_FEATHER, 1, player.tile, player))
            player.filterableMessage("You grab a tail-feather.")
            player.addXp(Skills.THIEVING, 26.0)
            return@queue
        }
        phoenix.forceChat("Squawk!")
        player.filterableMessage("You fail to grab the feather.")
        wait(1)
        phoenix.facePawn(player)
        player.stun(8)
        player.hit(10, HitType.REGULAR_HIT)
    }
}

// Void AliTheLeafletDropper.kt - "Take-flyer" and his advertising calls every 10 seconds.
val ALI_CALLS =
    listOf(
        "Ali's Discount Wares..." to "The finest store in the world!",
        "Dommik's crafting store..." to "The place for all your crafting needs.",
        "Ellis' Tannery..." to "The prices are better than the smell!",
        "Run your enemies through in style..." to "... with a Scimitar from Zeke's Superior Scimitars!",
        "Visit Louie's Armoured Legs Bazaar..." to "Number one for clanky trousers!",
        "Visit Ranael's Super Skirt Store .." to "... for the most stylish protection money can buy!",
        "Keep west as you travel south ..." to "... to avoid the killer scorpions!",
    )
val ALI_CALL_TIMER = gg.rsmod.game.model.timer.TimerKey()

on_npc_spawn(npc = Npcs.ALI_THE_LEAFLET_DROPPER) { npc.timers[ALI_CALL_TIMER] = 17 }

on_timer(ALI_CALL_TIMER) {
    val call = ALI_CALLS.random()
    npc.forceChat(call.first)
    npc.queue {
        wait(3)
        npc.forceChat(call.second)
    }
    npc.timers[ALI_CALL_TIMER] = 17
}

on_npc_option(npc = Npcs.ALI_THE_LEAFLET_DROPPER, option = "Take-flyer") {
    player.queue {
        if (player.inventory.contains(Items.AL_KHARID_FLYER)) {
            chatNpc("Are you trying to be funny or has age turned your brain to mush? You already have a flyer!")
            return@queue
        }
        if (player.inventory.isFull) {
            chatNpc("I'd give you a flyer but it looks like your hands are full. Come back when you have space for my flyer.")
            return@queue
        }
        chatNpc("Here! Take one and let me get back to work.")
        player.inventory.add(Items.AL_KHARID_FLYER)
    }
}

// Void ThakkradSigmundson.kt - "Craft-goods": cures yak-hide for 5 coins a hide.
fun cureYakHide(
    player: Player,
    wanted: Int,
): String {
    val hides = player.inventory.getItemCount(Items.YAKHIDE)
    if (hides == 0) return "You have no yak-hide to cure."
    val amount = minOf(wanted, hides)
    if (player.inventory.getItemCount(Items.COINS_995) < amount * 5) return "You don't have enough gold to pay me!"
    player.inventory.remove(Items.YAKHIDE, amount)
    player.inventory.remove(Items.COINS_995, amount * 5)
    player.inventory.add(Items.CURED_YAKHIDE, amount)
    return "There you go."
}

listOf(Npcs.THAKKRAD_SIGMUNDSON, Npcs.THAKKRAD_SIGMUNDSON_5506).forEach { thakkrad ->
    on_npc_option(npc = thakkrad, option = "Craft-goods") {
        player.queue {
            chatPlayer("Cure my yak hide please.")
            chatNpc("I will cure yak-hide for a fee of 5 gp per hide.")
            when (options("Cure all my hides.", "Cure one hide.", "Cure no hide.", title = "How many hides do you want cured?")) {
                1 -> chatNpc(cureYakHide(player, Int.MAX_VALUE))
                2 -> chatNpc(cureYakHide(player, 1))
                3 -> chatNpc("Bye.")
            }
        }
    }
}

// Void Apprentice.kt - "Teleport": sends the player into the Sorceress's Garden (2912, 5474) with the apprentice's curse spell.
on_npc_option(npc = Npcs.APPRENTICE_5532, option = "Teleport") {
    val apprentice = player.getInteractingNpc()
    player.queue {
        apprentice.facePawn(player)
        apprentice.forceChat("Seventior Disthinte Molesko!")
        apprentice.animate(718) // Void magic.anims.toml [curse]
        wait(4)
        player.moveTo(Tile(2912, 5474, 0))
    }
}

// Void FatherUrhney.kt / CuratorHaigHalen.kt - their "Pickpocket" answers (the curator's key belongs to a quest that is not here).
on_npc_option(npc = Npcs.FATHER_URHNEY, option = "Pickpocket") {
    player.message("<col=ff0000>You don't want to dip into those pockets without good reason.</col>")
    player.message("<col=ff0000>They're holy ...and filthy.</col>")
}

on_npc_option(npc = Npcs.CURATOR_HAIG_HALEN, option = "Pickpocket") {
    player.message("The curator doesn't seem to have anything of value.")
}
