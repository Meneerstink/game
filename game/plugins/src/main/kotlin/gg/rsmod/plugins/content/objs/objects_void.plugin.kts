package gg.rsmod.plugins.content.objs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef

/*
 * Object options the cache offers that nothing handled, ported from Void (owner 2026-09-24: "Check alle donors Void en Novite ...
 * port t dan"). Ids / animations / sounds are Void's own data (`*.objs.toml`, `*.anims.toml`, `*.sounds.toml`, same rev-634/667 ids).
 */

val CLIMB_DOWN_ANIM = 827 // Void climb_down
val PICKING_LOW_ANIM = 2282 // Void picking_low
val PICK_SOUND = 2581 // Void pick

// Void Nettles.kt - "Pick" on every nettle loc (by cache name): needs gloves or you are stung (20 poison damage).
world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    if (!def.name.equals("Nettles", ignoreCase = true) || def.options.getOrNull(opt - 1)?.lowercase() != "pick") return@bindObjectFallback false
    if (player.inventory.isFull) {
        player.queue { messageBox("You can't carry any more nettles.") }
        return@bindObjectFallback true
    }
    val gloves = player.getEquipment(EquipmentType.GLOVES)?.let { player.world.definitions.get(ItemDef::class.java, it.id).name.lowercase() }
    player.queue {
        player.animate(CLIMB_DOWN_ANIM)
        wait(2)
        if (gloves == null || !gloves.contains("gloves")) {
            player.hit(20, HitType.POISON)
            player.playSound(if (player.appearance.gender.isMale()) 513 else 506)
            player.message("You have been stung by the nettles.")
            return@queue
        }
        player.playSound(PICK_SOUND)
        player.message("You pick a handful of nettles.")
        player.inventory.add(Items.NETTLES)
        val nettle = DynamicObject(obj)
        world.remove(obj)
        world.queue {
            wait(15)
            world.spawn(nettle)
        }
    }
    true
}

// Void BushPicking.kt - cadava / redberry bushes: full -> half -> empty, regrowing after 200 ticks.
data class Bush(val full: Int, val half: Int, val empty: Int, val berry: Int)

val BUSHES =
    listOf(
        Bush(23625, 23626, 23627, Items.CADAVA_BERRIES),
        Bush(23628, 23629, 23630, Items.REDBERRIES),
    )

BUSHES.forEach { bush ->
    listOf(bush.full, bush.half).forEach { stage ->
        on_obj_option(obj = stage, option = "Pick-from") {
            val obj = player.getInteractingGameObj()
            if (player.inventory.add(bush.berry).hasFailed()) {
                player.message("Your inventory is too full to pick the berries from the bush.")
                return@on_obj_option
            }
            player.playSound(PICK_SOUND)
            player.animate(PICKING_LOW_ANIM)
            val next = if (stage == bush.full) bush.half else bush.empty
            world.spawnTemporaryObject(DynamicObject(next, obj.type, obj.rot, obj.tile), 200, DynamicObject(bush.full, obj.type, obj.rot, obj.tile))
        }
    }
    on_obj_option(obj = bush.empty, option = "Pick-from") {
        player.message("There are no berries on this bush at the moment.")
    }
}

// Void HayBales.kt (Jagex Ash on x.com): 2 % a prick for 1 hitpoint, 10 % a needle, otherwise nothing.
world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    val name = def.name.lowercase()
    if ((name != "hay bales" && name != "hay bale") || def.options.getOrNull(opt - 1)?.lowercase() != "search") return@bindObjectFallback false
    player.queue {
        player.animate(CLIMB_DOWN_ANIM)
        player.message(if (name == "hay bale") "You search the hay bale..." else "You search the hay bales...")
        wait(2)
        val roll = world.random(99)
        when {
            roll < 2 -> {
                player.hit(10, HitType.REGULAR_HIT)
                chatPlayer("Ow! There's something sharp in there!")
            }
            roll < 12 -> {
                if (player.inventory.add(Items.NEEDLE).hasFailed()) world.spawn(GroundItem(Items.NEEDLE, 1, player.tile, player))
                chatPlayer("Wow! A needle!", "Now what are the chances of finding that?")
            }
            else -> player.message("You find nothing of interest.")
        }
    }
    true
}

// Void LumbridgeChurch.kt - the organ and the bell.
on_obj_option(obj = 36978, option = "Play") {
    player.animate(3675) // Void play_organ
    player.playJingle(72) // Void jingle ambient_church_happy
}

on_obj_option(obj = 36976, option = "Ring") {
    val bell = player.getInteractingGameObj()
    player.queue {
        wait(1)
        player.animate(9880) // Void ring_bell
        wait(1)
        world.spawnTemporaryObject(DynamicObject(36977, bell.type, bell.rot, bell.tile), 4, DynamicObject(bell))
        player.message("You ring the church bell, confusing the citizens of Lumbridge.")
    }
}
