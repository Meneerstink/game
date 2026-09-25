package gg.rsmod.plugins.content

import gg.rsmod.game.model.attr.DISPLAY_MODE_CHANGE_ATTR
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.LIFEPOINT_SCALE_MIGRATED_ATTR
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.FollowerDetailsTab
import gg.rsmod.plugins.content.skills.summoning.SummoningUi
import gg.rsmod.game.model.timer.*
import gg.rsmod.game.service.serializer.PlayerSerializerService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Closing main modal for players.
 */
set_modal_close_logic {
    // Owner 2026-09-19 picture "interface hang": a side panel (equipment stats 670, bank 763, shop, GE, ...) mounted over the
    // tab area outlived its modal (closed by combat or replaced by another modal). The old guard only removed the side panel
    // while a modal was still registered, so every later click on the world left the tab strip hidden for good. The side
    // panel belongs to the modal: remove it whenever the modal-close logic runs.
    if (player.interfaces.getModal() != -1) {
        player.closeModalInterface()
    }
    // The menu-open check below pauses every STANDARD queue while anything sits in a main-screen slot. Whatever it counts must
    // also be closed here, or an interface mounted there without being the registered modal kept actions paused ("hang").
    for (dest in arrayOf(InterfaceDestination.MAIN_SCREEN, InterfaceDestination.MAIN_SCREEN_FULL)) {
        if (player.getInterfaceAt(dest) != -1) {
            player.closeInterface(dest)
        }
    }
    if (player.getInterfaceAt(InterfaceDestination.TAB_AREA) != -1) {
        player.closeInterface(InterfaceDestination.TAB_AREA)
    }
}

fun updatePlayerCountInJson() {
    val filePath = "./plugins/configs/player_count.json"
    val file = File(filePath)

    // Check if the file exists; if not, initialize with default structure
    if (!file.exists()) {
        file.parentFile.mkdirs()
        file.writeText("""{"playerCount": 0}""")
    }

    // Read the existing content
    val content = file.readText()
    val json = Json.parseToJsonElement(content) as JsonObject

    // Calculate the new count
    val count = world.players.count()

    // Update the count in the JSON structure
    val updatedJson =
        buildJsonObject {
            put("playerCount", count)
            json.keys.filter { it != "playerCount" }.forEach { key ->
                put(key, json[key]!!)
            }
        }

    // Write the updated content back to the file
    file.writeText(updatedJson.toString())
}

/**
 * Check if the player has a menu opened.
 */
set_menu_open_check {
    player.getInterfaceAt(dest = InterfaceDestination.MAIN_SCREEN) != -1 ||
        player.getInterfaceAt(dest = InterfaceDestination.MAIN_SCREEN_FULL) != -1
}

set_window_status_logic {
    // The client's WINDOW_STATUS carries `InterfaceManager.getWindowMode()`: WindowMode.FIXED = 1,
    // RESIZABLE = 2, FULLSCREEN = 3 (client `com/jagex/core/constants/WindowMode.java`). Fullscreen
    // used to fall through to the fixed 548 gameframe; it is a resizable layout and gets 746.
    val mode =
        when (player.attr[DISPLAY_MODE_CHANGE_ATTR]) {
            2, 3 -> DisplayMode.RESIZABLE_NORMAL
            else -> DisplayMode.FIXED
        }
    player.toggleDisplayInterface(mode)
    // The Deadman zone/skull overlay (interface 381) is mounted in a gameframe slot that
    // toggleDisplayInterface does not re-open (PVP_OVERLAY has no interfaceId of its own); reset
    // its bookkeeping so the next tick re-mounts and re-sends it for the new window mode.
    gg.rsmod.plugins.content.mechanics.pvp.DeadmanHud.reset(player)
    // A client window-mode switch rebuilds the gameframe with its baked flags, which drops the
    // server-sent Summoning state: orb/panel gating and the Follower Details tab (548:99 / 746:47
    // are baked hidden with no ops). Owner live report 2026-09-13: the icon vanished when switching
    // to fixed or fullscreen. The old `changed` guard skipped resizable <-> fullscreen (both map to
    // RESIZABLE_NORMAL here) although the client still rebuilds, so re-arm on every report, and
    // again a few ticks later the same way login does, in case the client rebuild lands after the
    // first re-arm.
    Familiar.redrawInterfaces(player)
    SummoningUi.restorePanel(player)
    FollowerDetailsTab.install(player)
    player.queue {
        wait(3)
        Familiar.redrawInterfaces(player)
        SummoningUi.restorePanel(player)
        FollowerDetailsTab.install(player)
    }
}

/**
 * Execute when a player logs in.
 */
on_login {

    // Skill-related logic.
    if (player.skills.getMaxLevel(Skills.CONSTITUTION) < 10) {
        player.skills.setBaseLevel(Skills.CONSTITUTION, 10)
    }

    player.calculateAndSetCombatLevel()
    player.sendWeaponComponentInformation()

    // Interface-related logic.
    player.openOverlayInterface(player.interfaces.displayMode)

    // Sends the player tabs
    player.sendTabs()

    player.sendOption("Follow", 3)
    player.sendOption("Trade with", 4)
    player.sendOption("Req Assist", 5)

    player.setVarp(Varps.UNSTABLE_FOUNDATIONS_PROGRESS, 1000) // unlocks tutorial settings
    player.setVarp(Varps.VARP_1160, -1) // Unlocks summoning orb
    player.setVarp(Varps.VARP_678, 3) // recipe for disaster chest

    player.setVarbit(Varbits.RESET_BANK_TAB_VIEW_INDEX, 1) // resets bank tab view index
    player.setVarbit(Varbits.INCUBATOR_UNLOCK, 0) // unlock incubator
    player.setVarbit(Varbits.KILLERWATT_PORTAL, 1) // unlock killerwatt portal
    player.setVarbit(Varbits.FORGIVENESS_OF_A_CHAOS_DWARF_PROGRESS, 45) // chaos dwarf area
    player.setVarbit(Varbits.THE_LOST_TRIBE_PROGRESS, 4) // lumbridge underground
    player.setVarbit(Varbits.BALLOON_CASTLE_WARS, 1) // balloon (castle wars)
    player.setVarbit(Varbits.BALLOON_CRAFTING_GUILD, 1) // balloon (crafting guild)
    player.setVarbit(Varbits.BALLOON_GRAND_TREE, 1) // balloon (grand tree)
    player.setVarbit(Varbits.BALLOON_ENTRANA, 3) // balloon (entrana) (3 empty, 2 full, 1 half built with fire lit)
    player.setVarbit(Varbits.BALLOON_TAVERLY, 1) // balloon (taverly)
    player.setVarbit(Varbits.BALLOON_VARROCK, 1) // balloon (varrock)

    player.openChatboxInterface(interfaceId = 137, child = 9, dest = InterfaceDestination.CHAT_BOX_PANE)

    // send the active bonus experience weekend
    // message only if bonus experience is active
    if (world.gameContext.bonusExperience) {
        player.message("Bonus XP Weekend is now active!")
    }

    player.message("Welcome to ${world.gameContext.name}.", ChatMessageType.GAME_MESSAGE)

    player.checkEquipment()

    // Normalize the old persisted 990/9900-style values on login, then initialize new characters
    // directly in the server's 1:1 lifepoint/prayer-point unit. Gated behind
    // LIFEPOINT_SCALE_MIGRATED_ATTR so this only ever runs once per character: the "value is
    // greater than the real max and divisible by ten" check can never fire again once a value is
    // genuinely 1:1 (current can never exceed max by construction), so re-running it forever was
    // unnecessary. Known residual gap: a character whose real current HP/prayer at the moment of
    // the original x10-to-1:1 cutover was already <= ~10% of max is indistinguishable from an
    // already-correct 1:1 value by magnitude alone and will not be caught here; fixing that
    // requires a save-format migration tool, not a login heuristic, and is tracked separately.
    if (!player.attr.has(LIFEPOINT_SCALE_MIGRATED_ATTR)) {
        val maxLifepoints = player.skills.getMaxLevel(Skills.CONSTITUTION)
        val storedLifepoints = player.getVarbit(player.skills.LIFEPOINTS_VARBIT)
        when {
            storedLifepoints == 0 -> player.setVarbit(player.skills.LIFEPOINTS_VARBIT, maxLifepoints)
            storedLifepoints > maxLifepoints && storedLifepoints % 10 == 0 ->
                player.setVarbit(player.skills.LIFEPOINTS_VARBIT, (storedLifepoints / 10).coerceAtMost(maxLifepoints))
        }

        val maxPrayerPoints = player.getMaximumPrayerPoints()
        val storedPrayerPoints = player.getCurrentPrayerPoints()
        if (storedPrayerPoints > maxPrayerPoints && storedPrayerPoints % 10 == 0) {
            player.setCurrentPrayerPoints((storedPrayerPoints / 10).coerceAtMost(maxPrayerPoints))
        }

        player.attr[LIFEPOINT_SCALE_MIGRATED_ATTR] = true
    }

    // The 667 gameframe reads current HP from varbit 7198, while the Skills tab and the orb's
    // onStatTransmit dependency read Constitution's current level. Keep both client inputs equal
    // after loading older saves as well as during live damage/healing.
    player.setCurrentLifepoints(player.getCurrentLifepoints())

    val timersToInitialize = listOf(TIME_ONLINE, DAILY_TIMER, SAVE_TIMER, STAT_RESTORE)
    timersToInitialize.forEach { timer ->
        if (!player.timers.exists(timer)) {
            player.timers[timer] = 1
        }
    }
    updatePlayerCountInJson()
}

on_logout {
    world.queue {
        wait(5)
        updatePlayerCountInJson()
    }
}

/**
 * Saves the player everytime the save timer
 * reaches 0. This is done for each individual player
 * as opposed to all the players at once to save
 * processing time.
 */
on_timer(key = SAVE_TIMER) {
    player.world
        .getService(PlayerSerializerService::class.java, searchSubclasses = true)
        ?.saveClientData(player as Client)
    player.timers[SAVE_TIMER] = 200
}

/**
 * Logic for swapping items in inventory.
 */
on_component_to_component_item_swap(
    srcInterfaceId = 679,
    srcComponent = 0,
    dstInterfaceId = 679,
    dstComponent = 0,
) {
    val srcSlot = player.attr[INTERACTING_ITEM_SLOT]!!
    val dstSlot = player.attr[OTHER_ITEM_SLOT_ATTR]!!

    val container = player.inventory

    if (srcSlot in 0 until container.capacity && dstSlot in 0 until container.capacity) {
        container.swap(srcSlot, dstSlot)
    } else {
        // Sync the container on the client
        container.dirty = true
    }
}

// Notes: handles border guards, a temporary solution
// also handles basic things like global object spawns, etc
on_world_init {
    val tiles =
        arrayOf(
            Tile(3070, 3277, 0),
            Tile(3070, 3275), // Draynor -> Falador
            Tile(3147, 3336, 0),
            Tile(3145, 3336),
            Tile(3147, 3337, 0),
            Tile(3145, 3337), // Draynor -> Barbarian Village, East
            Tile(3076, 3333, 0),
            Tile(3077, 3333),
            Tile(3078, 3333, 0),
            Tile(3079, 3333), // Draynor -> Barbarian Village, West
            Tile(3109, 3421, 0),
            Tile(3109, 3419), // Edgeville
            Tile(3261, 3172, 0),
            Tile(3261, 3174),
            Tile(3261, 3173), // Al-kharid, south-west
            Tile(3282, 3330, 0),
            Tile(3284, 3330),
            Tile(3283, 3329),
            Tile(3284, 3329), // Al-kharid, north
            Tile(3273, 3429, 0),
            Tile(3273, 3428), // Varrock east doors
            Tile(3293, 3385, 0),
            Tile(3291, 3385), // Varrock east guards
        )

    tiles.forEach {
        val obj = world.getObject(it, ObjectType.INTERACTABLE)
        if (obj != null) {
            world.remove(obj)
            world.spawn(DynamicObject(obj.id - 1, obj.type, obj.rot, obj.tile))
        }

        val wall = world.getObject(it, ObjectType.LENGTHWISE_WALL)
        if (wall != null) {
            world.remove(wall)
        }
    }

    world.spawn(DynamicObject(id = Objs.PORTAL_7352, type = 10, rot = 0, tile = Tile(2898, 4808, 0)))
    world.spawn(DynamicObject(id = Objs.PORTAL_7352, type = 10, rot = 0, tile = Tile(2886, 4848, 0)))
    world.spawn(DynamicObject(id = Objs.PORTAL_7352, type = 10, rot = 0, tile = Tile(2933, 4820, 0)))
    world.spawn(DynamicObject(id = Objs.PORTAL_7352, type = 10, rot = 0, tile = Tile(2923, 4854, 0)))
}
