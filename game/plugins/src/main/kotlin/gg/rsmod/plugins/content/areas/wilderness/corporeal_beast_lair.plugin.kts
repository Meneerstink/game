package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile

/**
 * Corporeal Beast lair access (level 21 Wilderness cave entrance east of the Graveyard of Shadows).
 *
 * - Cave entrance 38815 at 3209,3780: requires 23 Summoning, 37 Woodcutting, 45 Mining, 47 Firemaking
 *   and 55 Prayer (the Summer's End requirement set) and moves the player to 2885,4372 on plane 2.
 * - Cave exit 37928 at 2883,4370 returns the player to 3214,3782 in the Wilderness.
 * - Passages 37929 / 38811 ("Go-through") move the player three tiles east or one tile west of the
 *   passage anchor, two tiles north. The passage into the beast's chamber first shows the warning
 *   screen (interface 650: 17 = enter, 18 = close, 20 = don't-ask-again toggle, varbit 5366).
 * - "Peek-in" on the chamber passage reports whether anyone is already inside the lair.
 *
 * Sources: Void CorporealBeastsLair.kt + corporeal_beasts_lair data (ids, tiles, offsets, warning
 * varbit/interface), Novite ObjectHandler/ButtonHandler (skill requirements, warning components).
 */
val CORP_ENTRANCE_TILE = Tile(3209, 3780)
val CORP_CAVE_ARRIVAL = Tile(2885, 4372, 2)
val CORP_EXIT_TILE = Tile(2883, 4370, 2)
val CORP_WILDERNESS_ARRIVAL = Tile(3214, 3782)
val CORP_LAIR_X = 2972..3001
val CORP_LAIR_Z = 4370..4397
val CORP_LAIR_HEIGHT = 2

val CORP_WARNING_INTERFACE = 650
val CORP_WARNING_ENTER = 17
val CORP_WARNING_CLOSE = 18
val CORP_WARNING_DONT_ASK = 20
val CORP_WARNING_ASK_AGAIN_COMPONENT = 21
val CORP_WARNING_VARBIT = 5366
val CORP_WARNING_TOGGLE_SHOWN = 6
val CORP_WARNING_DISABLED = 7

val CORP_PENDING_TILE = AttributeKey<Tile>()

fun inCorpLair(tile: Tile): Boolean = tile.height == CORP_LAIR_HEIGHT && tile.x in CORP_LAIR_X && tile.z in CORP_LAIR_Z

fun Player.meetsCorpLairRequirements(): Boolean =
    skills.getMaxLevel(Skills.SUMMONING) >= 23 &&
        skills.getMaxLevel(Skills.WOODCUTTING) >= 37 &&
        skills.getMaxLevel(Skills.MINING) >= 45 &&
        skills.getMaxLevel(Skills.FIREMAKING) >= 47 &&
        skills.getMaxLevel(Skills.PRAYER) >= 55

/**
 * Void moves the player relative to the passage anchor: east side +3,+2, west side -1,+2.
 */
fun passageDestination(player: Player, obj: GameObject): Tile =
    if (player.tile.x <= obj.tile.x) {
        obj.tile.transform(3, 2)
    } else {
        obj.tile.transform(-1, 2)
    }

fun Player.goThroughPassage(destination: Tile) {
    lockingQueue(lockState = LockState.FULL) {
        wait(1)
        player.moveTo(destination)
    }
}

fun Player.showCorpWarning(destination: Tile) {
    val count = getVarbit(CORP_WARNING_VARBIT)
    if (count >= CORP_WARNING_DISABLED) {
        goThroughPassage(destination)
        return
    }
    if (count < CORP_WARNING_TOGGLE_SHOWN) {
        setVarbit(CORP_WARNING_VARBIT, count + 1)
    }
    attr[CORP_PENDING_TILE] = destination
    openInterface(interfaceId = CORP_WARNING_INTERFACE, dest = InterfaceDestination.MAIN_SCREEN)
    setComponentHidden(
        interfaceId = CORP_WARNING_INTERFACE,
        component = CORP_WARNING_ASK_AGAIN_COMPONENT,
        hidden = getVarbit(CORP_WARNING_VARBIT) < CORP_WARNING_TOGGLE_SHOWN,
    )
}

on_obj_option(obj = Objs.ENTRANCE_37749, option = "go-through") {
    if (!player.meetsCorpLairRequirements()) {
        player.message("You need 23 Summoning, 37 Woodcutting, 45 Mining, 47 Firemaking and 55 Prayer to enter this dungeon.")
        return@on_obj_option
    }
    player.goThroughPassage(CORP_CAVE_ARRIVAL)
}

on_obj_option(obj = Objs.EXIT_37928, option = "go-through") {
    val obj = player.getInteractingGameObj()
    if (obj.tile.x != CORP_EXIT_TILE.x || obj.tile.z != CORP_EXIT_TILE.z) {
        return@on_obj_option
    }
    player.goThroughPassage(CORP_WILDERNESS_ARRIVAL)
}

arrayOf(Objs.PASSAGE_37929, Objs.PASSAGE_38811).forEach { passage ->
    on_obj_option(obj = passage, option = "go-through") {
        val obj = player.getInteractingGameObj()
        val destination = passageDestination(player, obj)
        if (!inCorpLair(player.tile) && inCorpLair(destination)) {
            player.showCorpWarning(destination)
        } else {
            player.goThroughPassage(destination)
        }
    }

}

on_obj_option(obj = Objs.PASSAGE_38811, option = "peek-in") {
    var occupants = 0
    world.players.forEach { other ->
        if (inCorpLair(other.tile)) {
            occupants++
        }
    }
    if (occupants > 0) {
        player.message("You peek into the chamber and see ${if (occupants == 1) "someone" else "$occupants adventurers"} fighting the beast.")
    } else {
        player.message("You peek into the chamber. The lair is empty.")
    }
}

on_button(interfaceId = CORP_WARNING_INTERFACE, component = CORP_WARNING_ENTER) {
    player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
    val destination = player.attr[CORP_PENDING_TILE] ?: return@on_button
    player.attr.remove(CORP_PENDING_TILE)
    player.goThroughPassage(destination)
}

on_button(interfaceId = CORP_WARNING_INTERFACE, component = CORP_WARNING_CLOSE) {
    player.attr.remove(CORP_PENDING_TILE)
    player.closeInterface(dest = InterfaceDestination.MAIN_SCREEN)
}

on_button(interfaceId = CORP_WARNING_INTERFACE, component = CORP_WARNING_DONT_ASK) {
    val count = player.getVarbit(CORP_WARNING_VARBIT)
    if (count < CORP_WARNING_TOGGLE_SHOWN) {
        return@on_button
    }
    if (count == CORP_WARNING_DISABLED) {
        player.setVarbit(CORP_WARNING_VARBIT, CORP_WARNING_TOGGLE_SHOWN)
    } else {
        player.setVarbit(CORP_WARNING_VARBIT, CORP_WARNING_DISABLED)
        player.message("You have toggled this warning screen off. You will not see this warning screen unless you speak to the Doomsayer in Lumbridge to turn it on again.")
    }
}
