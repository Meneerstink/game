package gg.rsmod.plugins.api.ext

import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.message.impl.*
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.bits.BitStorage
import gg.rsmod.game.model.bits.StorageBits
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.interf.InterfaceSet
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.*
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.items.armor.BrawlingGloves
import gg.rsmod.plugins.content.mechanics.music.RegionMusicService
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.skills.crafting.jewellery.JewelleryData
import gg.rsmod.plugins.content.skills.crafting.silver.SilverData
import gg.rsmod.plugins.content.skills.farming.constants.Constants
import gg.rsmod.plugins.content.skills.farming.logic.FarmingManager
import gg.rsmod.plugins.content.skills.slayer.data.SlayerMaster
import gg.rsmod.plugins.content.skills.slayer.getSlayerAssignment
import gg.rsmod.plugins.content.skills.smithing.data.BarProducts
import gg.rsmod.plugins.content.skills.smithing.data.BarType
import gg.rsmod.plugins.content.skills.smithing.data.SmithingType
import gg.rsmod.util.BitManipulation
import gg.rsmod.util.Misc
import java.lang.ref.WeakReference
import java.util.Arrays
import kotlin.math.floor
import kotlin.math.max

/**
 * The interface key used by inventory overlays
 */
const val INVENTORY_INTERFACE_KEY = 93

/**
 * The id of the script used to initialise the interface overlay options. The 'big' variant of this script
 * is used as it supports up to eight options rather than five.
 *
 * https://github.com/RuneStar/cs2-scripts/blob/master/scripts/[clientscript,interface_inv_init_big].cs2
 */
const val INTERFACE_INV_INIT_BIG = 150

fun openCharacterCustomizing(player: Player) {
    player.openFullscreenInterface(1028)
    player.setEvents(interfaceId = 1028, component = 65, to = 11)
    player.setEvents(interfaceId = 1028, component = 128, to = 50)
    player.setEvents(interfaceId = 1028, component = 132, to = 250)
    player.setVarbit(Varbits.CUSTOMIZATION_GENDER, if (player.appearance.gender == Gender.MALE) 0 else 1)
    player.setVarc(197, 0)
    player.runClientScript(368, "str2")
    // script_368(widget(1028, 116), str2, getTextWidth(str2, 495) + 30, "");
    // player.setVarbit(Varbits.VARBIT_8246, 1)
    // player.setVarbit(Varbits.VARBIT_8247, 0)
    // player.setComponentText(interfaceId = 1028, component = 115, text = "Modify Further")
}

/**
 * Opens a shop interface for the player.
 *
 * @param shop The name of the shop to open.
 * @param points If true, the shop is a points shop and will not display item prices.
 */
fun Player.openShop(
    shop: String,
    points: Boolean = false,
) {
    if (gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp.isHoldingTempGear(this)) {
        message("You can't access shops while in a Practice PvP match.")
        return
    }
    val currentShop = world.getShop(shop)
    val shopInterface = 620
    val mainStockComponent = 25
    val freeStockComponent = 26

    if (currentShop != null) {
        // Set the current shop attribute
        attr[CURRENT_SHOP_ATTR] = currentShop

        // Set the main stock container id
        setVarp(Varps.CURRENT_CONTAINER_ID, 4)

        // Set the secondary container id if the shop contains samples, otherwise set to -1
        if (currentShop.containsSamples) {
            setVarp(Varps.SECONDARY_CONTAINER_ID, 6)
        } else {
            setVarp(Varps.SECONDARY_CONTAINER_ID, -1)
        }

        // Set the currency for the shop
        if (currentShop.currency.currencyItem > -1) {
            setVarp(Varps.SHOP_CURRENCY, currentShop.currency.currencyItem)
        }

        shopDirty = true
        setVarc(199, -1)

        // Open the shop interfaces
        openInterface(interfaceId = 621, dest = InterfaceDestination.TAB_AREA)
        openInterface(interfaceId = 620, dest = InterfaceDestination.MAIN_SCREEN)

        // Show item prices if the shop isn't a points shop
        if (!points) {
            for (i in 0..40) {
                setVarc(946 + i, 0) // sets price amount on individual item container
            }
        }

        // Enable interface events for main stock items
        setInterfaceEvents(
            interfaceId = shopInterface,
            component = mainStockComponent,
            range = 0..currentShop.items.filterNotNull().size * 6,
            setting = 1150,
        )

        // Enable interface events for free sample items if any
        if (currentShop.sampleItems.filterNotNull().isNotEmpty()) {
            setInterfaceEvents(
                interfaceId = shopInterface,
                component = freeStockComponent,
                range = 0..currentShop.sampleItems.filterNotNull().size * 4,
                setting = 1150,
            )
        }

        // Set the shop name in the interface
        setComponentText(interfaceId = shopInterface, component = 20, text = currentShop.name)
        // Keep the inventory/sell control synchronized with the shop being opened.
        // The previous state leaked from a prior general store into specialist shops.
        val acceptsPlayerItems = currentShop.purchasePolicy != PurchasePolicy.BUY_NONE
        setComponentHidden(interfaceId = shopInterface, component = 19, hidden = !acceptsPlayerItems)
    } else {
        // Log a warning message if the shop does not exist
        World.logger.warn { "Player \"$username\" is unable to open shop \"$shop\" as it does not exist." }
    }
}

fun Player.transformObject(
    objectId: Int,
    currentX: Int,
    currentZ: Int,
    currentRotation: Int,
    newObjectIdParam: Int,
    nextXParam: Int,
    nextZParam: Int,
    newRotationParam: Int,
    waitTimeParam: Int,
) {
    var waitTime = waitTimeParam
    var newObjectId = newObjectIdParam
    var nextX = nextXParam
    var nextZ = nextZParam
    var newRotation = newRotationParam

    if (newRotation == -1) newRotation = currentRotation
    if (newObjectId == -1) newObjectId = objectId
    if (nextX == -1) nextX = currentX
    if (nextZ == -1) nextZ = currentZ
    if (waitTime == -1) waitTime = 2

    val oldObject =
        DynamicObject(id = objectId, type = 0, rot = currentRotation, tile = Tile(x = currentX, z = currentZ, height = tile.height))

    lockingQueue(lockState = LockState.DELAY_ACTIONS) {
        val newObject = DynamicObject(id = newObjectId, type = 0, rot = newRotation, tile = Tile(x = nextX, z = nextZ, height = tile.height))
        world.remove(oldObject)
        world.spawn(newObject)
        wait(waitTime)
        world.remove(newObject)
        world.spawn(oldObject)
    }
}

fun Player.handleTemporaryDoor(
    obj: DynamicObject,
    moveObjX: Int,
    moveObjZ: Int,
    newDoorId: Int,
    newRotation: Int,
    movePlayerX: Int,
    movePlayerZ: Int,
    waitTime: Int,
) {
    val moveX = if (moveObjX == -1) obj.tile.x else moveObjX
    val moveZ = if (moveObjZ == -1) obj.tile.x else moveObjZ
    val nextDoorId = if (newDoorId == -1) obj.id else newDoorId
    val rotation = if (newRotation == -1) obj.rot else newRotation
    val wait = if (waitTime == -1) 2 else waitTime
    lockingQueue(lockState = LockState.DELAY_ACTIONS) {
        world.remove(obj)
        val openDoor = DynamicObject(id = nextDoorId, type = 0, rot = newRotation, tile = Tile(x = moveX, z = moveZ))
        playSound(Sfx.DOOR_OPEN)
        world.spawn(openDoor)
        if (movePlayerX != -1 || movePlayerZ != -1) {
            walkTo(tile = Tile(x = movePlayerX, z = movePlayerZ), detectCollision = false)
        }
        wait(wait)
        world.remove(openDoor)
        world.spawn(obj)
        playSound(Sfx.DOOR_CLOSE)
    }
}

/**
 * Used primarily for firemaking, and finding the next available tile
 * for the player to walk to. Another use-case I found was for bird nests
 * from woodcutting
 */
fun Player.findWesternTile(): Tile {
    return listOf(
        Direction.WEST,
        Direction.EAST,
        Direction.SOUTH,
        Direction.NORTH,
    ).firstNotNullOfOrNull { direction ->
        if (world.collision.isBlocked(tile, direction, false)) {
            null
        } else {
            tile.step(direction, 1).takeUnless(world.collision::isClipped)
        }
    } ?: tile
}

fun Player.message(
    message: String,
    type: ChatMessageType = ChatMessageType.GAME_MESSAGE,
    username: String? = null,
) {
    // A Check / Inspect item option in progress shows its lines in the item dialogue instead (ItemActionGuard).
    if (type == ChatMessageType.GAME_MESSAGE && attr.has(gg.rsmod.plugins.content.items.ItemActionGuard.STATUS_CAPTURE)) {
        (attr[gg.rsmod.plugins.content.items.ItemActionGuard.STATUS_CAPTURE] as? MutableList<String>)?.let {
            it += message
            return
        }
    }
    write(MessageGameMessage(type = type.id, message = message, username = username))
}

fun Player.filterableMessage(message: String) {
    write(MessageGameMessage(type = ChatMessageType.FILTERED.id, message = message, username = null))
}

fun Player.runClientScript(
    id: Int,
    vararg args: Any,
) {
    write(RunClientScriptMessage(id, *args))
}

fun Player.runClientScriptReversed(
    id: Int,
    vararg args: Any,
) {
    val reversedArgs = args.reversedArray()
    write(RunClientScriptMessage(id, *reversedArgs))
}

fun Player.focusTab(tab: Int) {
    runClientScript(115, tab)
}

fun Player.unlockIComponentOptionSlots(
    interfaceId: Int,
    component: Int,
    fromSlot: Int,
    toSlot: Int,
    vararg optionSlots: Int,
) {
    var settingsHash = 0
    for (slot in optionSlots) {
        settingsHash = settingsHash or (2 shl slot)
    }
    setInterfaceEvents(interfaceId, component, fromSlot..toSlot, settingsHash)
}

fun Player.setInterfaceEvents(
    interfaceId: Int,
    component: Int,
    range: IntRange,
    setting: Int,
) {
    write(
        IfSetEventsMessage(
            hash = ((interfaceId shl 16) or component),
            fromChild = range.first,
            toChild = range.last,
            setting = setting,
        ),
    )
}

fun Player.setComponentText(
    interfaceId: Int,
    component: Int,
    text: String,
) {
    write(IfSetTextMessage(interfaceId, component, text))
}

fun Player.setVarcString(
    id: Int,
    text: String,
) {
    write(VarcStringMessage(id, text))
}

fun Player.setComponentHidden(
    interfaceId: Int,
    component: Int,
    hidden: Boolean,
) {
    write(IfSetHideMessage(hash = ((interfaceId shl 16) or component), hidden = hidden))
}

fun Player.setComponentSprite(
    interfaceId: Int,
    component: Int,
    sprite: Int,
) {
    write(IfSetSpriteMessage(hash = ((interfaceId shl 16) or component), sprite = sprite))
}

fun Player.setComponentItem(
    interfaceId: Int,
    component: Int,
    item: Int,
    amountOrZoom: Int,
) {
    write(IfSetObjectMessage(hash = ((interfaceId shl 16) or component), item = item, amount = amountOrZoom))
}

fun Player.setComponentNpcHead(
    interfaceId: Int,
    component: Int,
    npc: Int,
) {
    write(IfSetNpcHeadMessage(hash = ((interfaceId shl 16) or component), npc = npc))
}

fun Player.setComponentPlayerHead(
    interfaceId: Int,
    component: Int,
) {
    write(IfSetPlayerHeadMessage(hash = ((interfaceId shl 16) or component)))
}

fun Player.setComponentAnim(
    interfaceId: Int,
    component: Int,
    anim: Int,
) {
    write(IfSetAnimMessage(hash = ((interfaceId shl 16) or component), anim = anim))
}

/**
 * Use this method to open an interface id on top of an [InterfaceDestination]. This
 * method should always be preferred over
 *
 * ```
 * openInterface(parent: Int, child: Int, component: Int, type: Int, isMainComponent: Boolean)
 * ```
 *
 * as it holds logic that must be handled for certain [InterfaceDestination]s.
 */
fun Player.openInterface(
    interfaceId: Int,
    dest: InterfaceDestination,
    fullscreen: Boolean = false,
) {
    val displayMode = if (!fullscreen) interfaces.displayMode else DisplayMode.FULLSCREEN
    val child = getChildId(dest, displayMode)
    val parent = getDisplayComponentId(displayMode)
    if (displayMode == DisplayMode.FULLSCREEN) {
        openOverlayInterface(displayMode)
    }
    openInterface(
        parent,
        child,
        interfaceId,
        if (dest.clickThrough) 1 else 0,
        isModal = dest == InterfaceDestination.MAIN_SCREEN || dest == InterfaceDestination.MAIN_SCREEN_FULL,
    )
    // RCV-012 B5: mounting a side inventory over the tab area (bank, shop, GE, trade, duel, price checker, equipment
    // stats, BoB) makes the client redraw the tab strip with the cache's baked flags - re-arm at this one shared point.
    if (dest == InterfaceDestination.TAB_AREA) {
        GameframeRebuild.rearm(this)
    }
}

/**
 * Handles opening of fullscreen interfaces.
 * Example: World Map
 */
fun Player.openFullscreenInterface(interfaceId: Int) {
    interfaces.open(0, 0, interfaceId)
    write(IfOpenTopMessage(interfaceId, 0))
    GameframeRebuild.rearm(this)
}

fun Player.closeFullscreenInterface() {
    val display = if (interfaces.displayMode.isResizable()) 746 else 548
    write(IfOpenTopMessage(display, 2))
    // RCV-010 A4: the restored top level comes back with the cache's baked tab-strip flags.
    GameframeRebuild.rearm(this)
}

/**
 * Use this method to open an interface id on top of an [InterfaceDestination]. This
 * method should always be preferred over
 *
 * ```
 * openInterface(parent: Int, child: Int, component: Int, type: Int, isMainComponent: Boolean)
 * ```
 *
 * as it holds logic that must be handled for certain [InterfaceDestination]s.
 */
fun Player.openChatboxInterface(
    interfaceId: Int,
    child: Int,
    dest: InterfaceDestination,
    fullscreen: Boolean = false,
) {
    val displayMode = if (!fullscreen) interfaces.displayMode else DisplayMode.FULLSCREEN
    val parent = dest.interfaceId
    if (displayMode == DisplayMode.FULLSCREEN) {
        openOverlayInterface(displayMode)
    }
    openInterface(
        parent,
        child,
        interfaceId,
        if (dest.clickThrough) 1 else 0,
        isModal = dest == InterfaceDestination.MAIN_SCREEN,
    )
}

/**
 * Use this method to "re-open" an [InterfaceDestination]. This method should always
 * be preferred over
 *
 * ```
 * openInterface(parent: Int, child: Int, interfaceId: Int, type: Int, mainInterface: Boolean)
 * ````
 *
 * as it holds logic that must be handled for certain [InterfaceDestination]s.
 */
fun Player.openInterface(
    dest: InterfaceDestination,
    autoClose: Boolean = false,
) {
    val displayMode = if (!autoClose) interfaces.displayMode else DisplayMode.FULLSCREEN
    val child = getChildId(dest, displayMode)
    val parent = getDisplayComponentId(displayMode)
    if (displayMode == DisplayMode.FULLSCREEN) {
        openOverlayInterface(displayMode)
    }
    openInterface(
        parent,
        child,
        dest.interfaceId,
        if (dest.clickThrough) 1 else 0,
        isModal = dest == InterfaceDestination.MAIN_SCREEN,
    )
}

fun Player.openInterface(
    parent: Int,
    child: Int,
    interfaceId: Int,
    type: Int = 0,
    isModal: Boolean = false,
) {
    if (isModal) {
        val replacedHash = interfaces.openModal(parent, child, interfaceId)
        if (replacedHash != -1) {
            write(IfCloseSubMessage(replacedHash))
        }
    } else {
        interfaces.open(parent, child, interfaceId)
    }
    write(IfOpenSubMessage(parent, child, interfaceId, type))
}

fun Player.closeInterface(interfaceId: Int) {
    val hash = interfaces.close(interfaceId)
    if (hash != -1) {
        write(IfCloseSubMessage(hash))
    }
}

/**
 * Closes the exact type-0 destination tracked by [InterfaceSet]. The client's CLOSE_MODAL
 * packet has no interface id and locally removes every type-0 sub-interface; mirroring the
 * destination hash prevents an old fixed/resizable entry from surviving as an invisible menu.
 */
fun Player.closeModalInterface() {
    val hash = interfaces.closeModal()
    if (hash != -1) {
        write(IfCloseSubMessage(hash))
    }
}

fun Player.closeInterface(dest: InterfaceDestination) {
    val displayMode = interfaces.displayMode
    val child = getChildId(dest, displayMode)
    val parent = getDisplayComponentId(displayMode)
    val hash = interfaces.close(parent, child)
    if (hash != -1) {
        write(IfCloseSubMessage((parent shl 16) or child))
        // RCV-012 B5: restoring the tab area (any modal close, bank/shop/GE/duel/BoB exit) redraws the tab strip too.
        if (dest == InterfaceDestination.TAB_AREA) {
            GameframeRebuild.rearm(this)
        }
    }
}

fun Player.closeMainInterface() {
    closeInterface(InterfaceDestination.MAIN_SCREEN)
    closeInterface(InterfaceDestination.MAIN_SCREEN_FULL)
    closeInterface(InterfaceDestination.MAIN_SCREEN_OVERLAY)
}

fun Player.closeComponent(
    parent: Int,
    child: Int,
) {
    interfaces.close(parent, child)
    write(IfCloseSubMessage((parent shl 16) or child))
}

fun Player.closeInputDialog() {
    runClientScript(101)
}

fun Player.getInterfaceAt(dest: InterfaceDestination): Int {
    val displayMode = interfaces.displayMode
    val child = getChildId(dest, displayMode)
    val parent = getDisplayComponentId(displayMode)
    return interfaces.getInterfaceAt(parent, child)
}

fun Player.isInterfaceVisible(interfaceId: Int): Boolean = interfaces.isVisible(interfaceId)

fun Player.toggleDisplayInterface(newMode: DisplayMode) {
    if (interfaces.displayMode != newMode) {
        val previousParent = getDisplayComponentId(interfaces.displayMode)
        interfaces.displayMode = newMode

        openOverlayInterface(newMode)
        // IF_OPENTOP discards every sub-interface under the previous gameframe in the client.
        // Remove the same parent from server bookkeeping after the new top level is active. A
        // modal close hook can therefore restore any required tab on the new display mode.
        interfaces.clearDisplay(previousParent)
        InterfaceDestination.values.filter { pane -> pane.interfaceId != -1 }.forEach { pane ->
            openInterface(spellbookInterfaceId(pane), pane)
        }
    }
}

fun Player.openOverlayInterface(displayMode: DisplayMode) {
    val component = getDisplayComponentId(displayMode)
    interfaces.setVisible(parent = getDisplayComponentId(displayMode), child = 0, visible = true)
    write(IfOpenTopMessage(component, 1))
    GameframeRebuild.rearm(this)
}

fun Player.sendItemContainer(
    key: Int,
    items: Array<Item?>,
) {
    write(UpdateInvFullMessage(containerKey = key, items = items, invother = false))
}

fun Player.sendItemContainer(
    key: Int,
    container: ItemContainer,
) = sendItemContainer(key, container.rawItems)

/**
 * Sends a container type referred to as 'invother' in CS2, which is used for displaying a second container with
 * the same container key. An example of this is the trade accept screen, where the list of items being traded is stored
 * in container 90 for both the player's container, and the partner's container. A container becomes 'invother' when it's
 * component hash is less than -70000, which internally translates the container key to (key + 32768). We can achieve this by either
 * sending a component hash of less than -70000, or by setting the key ourselves. I feel like the latter makes more sense.
 *
 * Special thanks to Polar for explaining this concept to me.
 *
 * https://github.com/RuneStar/cs2-scripts/blob/a144f1dceb84c3efa2f9e90648419a11ee48e7a2/scripts/script768.cs2
 *
 *
 * -- Note* Alycia
 * It appears that 667 has a new way of doing this, and that is by sending a byte in the packet
 * to flag if the inventory/container is an "invother". More research into this would provide
 * better documentation, but I assume this is pretty self-explanatory with regard to the above note.
 *
 */
fun Player.sendItemContainerOther(
    key: Int,
    container: ItemContainer,
) {
    write(UpdateInvFullMessage(containerKey = key, items = container.rawItems, invother = true))
}

fun Player.sendRunEnergy(energy: Int) {
    write(UpdateRunEnergyMessage(energy))
}

fun Player.playSound(
    id: Int,
    volume: Int = 255,
    delay: Int = 0,
    rate: Int = 256,
    loops: Int = 1,
) {
    gg.rsmod.game.model.AvTrace.log { "sound player index=$index id=$id loops=$loops delay=$delay volume=$volume rate=$rate" }
    write(SynthSoundMessage(sound = id, loops = loops, delay = delay, volume = volume, rate = rate))
}

/**
 * Jingles (quest completion, level up, slayer task) go out as [MusicEffectMessage] / ServerProt.MIDI_JINGLE.
 * The client gates these on `ClientOptions.musicVolume`, not on the sound-effects volume
 * (`SoundManager.playMidiJingle` returns early when `musicVolume * volume >> 8 == 0`), so with the music
 * slider muted every jingle is silent while ordinary synth sounds still play. This trace lets a live test
 * tell "the server never sent it" apart from "the client dropped it" (owner 2026-09-20: "i hear no quest
 * complete sound and a level up skill sound").
 */
fun Player.playJingle(
    id: Int,
    volume: Int = 255,
) {
    gg.rsmod.game.model.AvTrace.log { "jingle player index=$index id=$id volume=$volume" }
    write(MusicEffectMessage(id = id, volume = volume))
}

/**
 * Sends a [MidiSongMessage] to the [Player] based on the provided [id]. The player's music tab
 * interface is also updated using the provided [name]. The player's currently playing song varbit
 * is also updated.
 *
 * @param id: The ID of the song to be played
 * @param name: The name of the song to be played
 */
fun Player.playSong(
    id: Int,
    name: String = "",
) {
    setComponentText(interfaceId = 187, component = 4, text = name)
    write(MidiSongMessage(10, id, 255))

    val index =
        world.definitions
            .get(EnumDef::class.java, 1351)
            .getKeyForValue(id)
    setVarbit(Varbits.CURRENT_SONG_INDEX, index)
}

/**
 * Unlocks a song for the player based on trackIndex and writes a message to the player's chat window.
 *
 * @param trackIndex: The index used to identify the song the cache
 * @param sendMessage: Whether to send a message to the [Player] when this song is unlocked
 */
fun Player.unlockSong(
    trackIndex: Int,
    sendMessage: Boolean = true,
) {
    val musicTrack =
        world
            .getService(
                RegionMusicService::class.java,
            )!!
            .musicTrackList
            .first { trackIndex == it.index }

    val bitNum = musicTrack.bitNum
    val oldValue = getVarp(musicTrack.varp)

    // Return if the player has already unlocked this song
    if (oldValue shr bitNum and 1 == 1) {
        return
    }

    val newValue = (1 shl bitNum) + oldValue
    setVarp(musicTrack.varp, newValue)

    val trackName = world.definitions.get(EnumDef::class.java, 1345).getString(trackIndex)
    if (sendMessage) {
        message(
            "<col=ff0000>You have unlocked a new music track: $trackName",
            ChatMessageType
                .GAME_MESSAGE,
        )
    }
}

/**
 * Add a song to the [Player]s playlist based off of the interface slot that was interacte with.
 * If the interface slot is odd that means that the "+" button was clicked, not right-click add song.
 * Song indices are stored in varbits 7081 - 7092
 *
 * @param interfaceSlot: The slot number of the interface that was clicked
 */
fun Player.addSongToPlaylist(interfaceSlot: Int) {
    var slot = interfaceSlot
    if (slot % 2 != 0) slot -= 1

    val trackIndex = slot / 2
    val playlistVarbit = (Varbits.PLAYLIST_SLOT_1..Varbits.PLAYLIST_SLOT_12).first { getVarbit(it) == 32767 }
    setVarbit(playlistVarbit, trackIndex)
}

/**
 * Remove a song from the [Player]s play based on the interface slot in either the main track list.
 * If [fromTrackList] is true, we need to figure out which varbit that song is in, otherwise we can just
 * use [interfaceSlot] to determine the varbit we need to remove. All songs in the following varbits are
 * moved back into the previous varbit.
 *
 * @param interfaceSlot: The slot number of the interface that was clicked
 * @param fromTrackList: Whether the clicked slot was on the main track list or on the playlist interface
 */
fun Player.removeSongFromPlaylist(
    interfaceSlot: Int,
    fromTrackList: Boolean = false,
) {
    var playlistSlot = interfaceSlot

    if (fromTrackList) {
        if (playlistSlot % 2 != 0) playlistSlot -= 1
        playlistSlot /= 2
        playlistSlot =
            (Varbits.PLAYLIST_SLOT_1..Varbits.PLAYLIST_SLOT_12).indexOfFirst {
                getVarbit(it) == playlistSlot
            }
    } else {
        if (playlistSlot > 11) playlistSlot -= 12
    }
    (playlistSlot..11).forEach {
        if (it == 11) {
            setVarbit(Varbits.PLAYLIST_SLOT_1 + it, -1)
            return@forEach
        }
        setVarbit(Varbits.PLAYLIST_SLOT_1 + it, getVarbit(Varbits.PLAYLIST_SLOT_1 + it + 1))
    }
}

/**
 * Sets all the [Player]s playlist varbits to -1 (Underflows to 32767)
 */
fun Player.clearPlaylist() {
    (Varbits.PLAYLIST_SLOT_1..Varbits.PLAYLIST_SLOT_12).forEach {
        setVarbit(it, -1)
    }
}

/**
 * Flips the [Player]s playlist varbit to 0 or 1
 */
fun Player.togglePlaylist() {
    setVarbit(Varbits.TOGGLE_PLAYLIST, getVarbit(Varbits.TOGGLE_PLAYLIST) + 1) // value of 2 overflows back to 0
}

/**
 * Flips the [Player]s playlist shuffle varbit to 0 or 1
 */
fun Player.togglePlaylistShuffle() {
    // value of 2 overflows back to 0
    setVarbit(Varbits.TOGGLE_PLAYLIST_SHUFFLE, getVarbit(Varbits.TOGGLE_PLAYLIST_SHUFFLE) + 1)
}

fun Player.getVarp(id: Int): Int = varps.getState(id)

fun Player.setVarp(
    id: Int,
    value: Int,
) {
    varps.setState(id, value)
}

fun Player.toggleVarp(id: Int) {
    varps.setState(id, varps.getState(id) xor 1)
}

fun Player.syncVarp(id: Int) {
    setVarp(id, getVarp(id))
}

fun Player.getVarbit(id: Int): Int {
    val def = world.definitions.get(VarbitDef::class.java, id)
    return varps.getBit(def.varp, def.startBit, def.endBit)
}

fun Player.setVarbit(
    id: Int,
    value: Int,
) {
    val def = world.definitions.get(VarbitDef::class.java, id)
    varps.setBit(def.varp, def.startBit, def.endBit, value)
}

fun Player.setVarc(
    id: Int,
    value: Int,
) {
    val message =
        // RCV-012: the small packet carries the VALUE as one byte, so the choice depends on the value (Novite
        // DefaultGameEncoder.sendGlobalConfig); choosing by id truncated any low-id varc holding a larger value.
        if (value in Byte.MIN_VALUE..Byte.MAX_VALUE) VarcSmallMessage(id, value) else VarcLargeMessage(id, value)
    write(message)
    varcs[id] = value
}

fun Player.getVarc(id: Int): Int {
    return varcs[id]
}

/**
 * Write a varbit message to the player's client without actually modifying
 * its varp value in [Player.varps].
 */
fun Player.sendTempVarbit(
    id: Int,
    value: Int,
) {
    val def = world.definitions.get(VarbitDef::class.java, id)
    val state = BitManipulation.setBit(varps.getState(def.varp), def.startBit, def.endBit, value)
    val message =
        if (state in -Byte.MAX_VALUE..Byte.MAX_VALUE) {
            VarpSmallMessage(def.varp, state)
        } else {
            VarpLargeMessage(
                def.varp,
                state,
            )
        }
    write(message)
}

fun Player.toggleVarbit(id: Int) {
    val def = world.definitions.get(VarbitDef::class.java, id)
    varps.setBit(def.varp, def.startBit, def.endBit, getVarbit(id) xor 1)
}

fun Player.setMapFlag(
    x: Int,
    z: Int,
) {
    write(SetMapFlagMessage(x, z))
}

fun Player.clearMapFlag() {
    setMapFlag(255, 255)
}

fun Player.setMinimapState(state: Int) {
    write(MinimapToggleMessage(state))
}

fun Player.sendOption(
    option: String,
    id: Int,
    leftClick: Boolean = false,
) {
    check(id in 1..options.size) { "Option id must range from [1-${options.size}]" }
    val index = id - 1
    options[index] = option
    write(SetOpPlayerMessage(option, index, leftClick, -1))
}

/**
 * Checks if the player has an option with the name [option] (case-sensitive).
 */
fun Player.hasOption(
    option: String,
    id: Int = -1,
): Boolean {
    check(id == -1 || id in 1..options.size) { "Option id must range from [1-${options.size}]" }
    return if (id != -1) options.any { it == option } else options[id - 1] == option
}

/**
 * Removes the option with [id] from this player.
 */
fun Player.removeOption(id: Int) {
    check(id in 1..options.size) { "Option id must range from [1-${options.size}]" }
    val index = id - 1
    write(SetOpPlayerMessage("null", index, false, -1))
    options[index] = null
}

fun Player.getStorageBit(
    storage: BitStorage,
    bits: StorageBits,
): Int = storage.get(this, bits)

fun Player.hasStorageBit(
    storage: BitStorage,
    bits: StorageBits,
): Boolean = storage.get(this, bits) != 0

fun Player.setStorageBit(
    storage: BitStorage,
    bits: StorageBits,
    value: Int,
) {
    storage.set(this, bits, value)
}

fun Player.toggleStorageBit(
    storage: BitStorage,
    bits: StorageBits,
) {
    storage.set(this, bits, storage.get(this, bits) xor 1)
}

fun Player.heal(
    amount: Int,
    capValue: Int = 0,
) {
    // Smoke ancient sceptre: 20 % less healing for 6 seconds after smoke spell damage while poisoned (AncientSceptres).
    val healed =
        if (timers.has(gg.rsmod.game.model.timer.SMOKE_SCEPTRE_HEAL_REDUCTION_TIMER)) {
            kotlin.math.floor(amount * gg.rsmod.plugins.content.items.osrs.AncientSceptres.SMOKE_HEAL_MULTIPLIER).toInt()
        } else {
            amount
        }
    alterLifepoints(value = healed, capValue = capValue)
}

fun Player.restorePrayer(
    amount: Int,
    capValue: Int = 0,
) {
    alterPrayerPoints(value = amount, capValue = capValue)
}

fun Player.hasSpellbook(book: Spellbook): Boolean = getVarbit(Varbits.SPELLBOOK) == book.id

fun Player.getSpellbook(): Spellbook = Spellbook.values.first { getVarbit(Varbits.SPELLBOOK) == it.id }

/**
 * Varbit 357 (varp 439 bits 0-1) is the 667 client's own spellbook value: CS2 1121 (the spellbook's onVarTransmit) reads it
 * to pick the autocast highlight components (0 = 192:30/29, 1 = 193:50/49). Only [Varbits.SPELLBOOK] used to be set, so an
 * Ancient Magicks autocast worked but was never highlighted (owner 2026-09-19).
 */
const val CLIENT_SPELLBOOK_VARBIT = 357

fun Player.setSpellbook(book: Spellbook) {
    setVarbit(Varbits.SPELLBOOK, book.id)
    setVarbit(CLIENT_SPELLBOOK_VARBIT, book.id)
}

fun Player.getWeaponType(): Int = attr[LAST_KNOWN_WEAPON_TYPE] ?: 0

fun Player.getMaximumMakeQuantity(): Int = getVarbit(Varbits.MAKE_MAX_QUANTITY)

fun Player.getMakeQuantity(): Int = getVarbit(Varbits.MAKE_QUANTITY)

fun Player.getAttackStyle(): Int = getVarp(Varps.ATTACK_STYLE)

fun Player.hasWeaponType(
    type: WeaponType,
    vararg others: WeaponType,
): Boolean = getWeaponType() == type.id || others.isNotEmpty() && getWeaponType() in others.map { it.id }

fun Player.hasEquipped(
    slot: EquipmentType,
    vararg items: Int,
): Boolean {
    check(items.isNotEmpty()) { "Items shouldn't be empty." }
    return items.any { equipment.hasAt(slot.id, it) }
}

fun Player.hasEquipped(items: IntArray) = items.all { equipment.contains(it) }

fun Player.hasEquippedWithName(nameKeywords: Array<String>): Boolean {
    return EquipmentType.values.any { slot ->
        val item = getEquipment(slot)
        item?.let { itemName ->
            nameKeywords.any { keyword -> itemName.getName(world.definitions).contains(keyword, ignoreCase = true) }
        } ?: false
    }
}

fun Player.getEquipment(slot: EquipmentType): Item? = equipment[slot.id]

fun Player.setSkullIcon(icon: SkullIcon) {
    skullIcon = icon.id
    addBlock(UpdateBlockType.APPEARANCE)
}

fun Player.skull(
    icon: SkullIcon,
    durationCycles: Int,
) {
    check(icon != SkullIcon.NONE)
    setSkullIcon(icon)
    timers[SKULL_ICON_DURATION_TIMER] = durationCycles
}

fun Player.hasSkullIcon(icon: SkullIcon): Boolean = skullIcon == icon.id

fun Player.isClientResizable(): Boolean =
    interfaces.displayMode == DisplayMode.RESIZABLE_NORMAL || interfaces.displayMode == DisplayMode.FULLSCREEN

fun Player.sendWorldMapTile() {
    runClientScript(1749, tile.as30BitInteger)
}

fun Player.sendWeaponComponentInformation() {
    for (slot in 11..14) {
        setEvents(interfaceId = 884, component = slot, from = -1, to = 0, setting = 2)
    }
    val weapon = getEquipment(EquipmentType.WEAPON)
    if (weapon != null) {
        val definition = world.definitions.get(ItemDef::class.java, weapon.id)
        attr[LAST_KNOWN_WEAPON_TYPE] = max(0, definition.weaponType)
    } else {
        attr[LAST_KNOWN_WEAPON_TYPE] = WeaponType.NONE.id
    }
    // Hide the special attack bar (component 4) for weapons with no registered special attack.
    setComponentHidden(interfaceId = 884, component = 4, hidden = weapon == null || !SpecialAttacks.hasSpecialAttack(weapon.id))
}

fun Player.getGnomeAgilityStage(): Int {
    val lastStage = attr[GNOME_AGILITY_STAGE]
    if (lastStage == null) {
        setGnomeAgilityStage(0)
        return getGnomeAgilityStage()
    }
    return lastStage
}

fun Player.setGnomeAgilityStage(stage: Int) {
    attr[GNOME_AGILITY_STAGE] = stage
}

fun Player.calculateAndSetCombatLevel(): Boolean {
    val old = combatLevel
    val attack = skills.getMaxLevel(Skills.ATTACK)
    val defence = skills.getMaxLevel(Skills.DEFENCE)
    val strength = skills.getMaxLevel(Skills.STRENGTH)
    val hitpoints = skills.getMaxLevel(Skills.CONSTITUTION)
    val prayer = skills.getMaxLevel(Skills.PRAYER)
    val ranged = skills.getMaxLevel(Skills.RANGED)
    val magic = skills.getMaxLevel(Skills.MAGIC)
    val summoning = skills.getMaxLevel(Skills.SUMMONING)
    val meleeCombat =
        floor(
            0.25 * (defence + hitpoints + floor((prayer * 0.50)) + floor(summoning * 0.50)) +
                0.325 * (attack + strength),
        )
    // floor(0.25 * (attack + strength
    // 1/4 * [1.3 * Max(Att+Str, 2*Mag, 2*Rng) + Def + Hp + Pray/2 + Summ/2]
    val rangingCombat =
        floor(
            0.25 * (defence + hitpoints + floor((prayer * 0.50)) + floor(summoning * 0.50)) +
                0.325 * (floor((ranged * 0.50)) + ranged),
        )
    val magicCombat =
        floor(
            0.25 * (defence + hitpoints + floor((prayer * 0.50)) + floor(summoning * 0.50)) +
                0.325 * (floor((magic * 0.50)) + magic),
        )

    combatLevel =
        when {
            meleeCombat >= rangingCombat && meleeCombat >= magicCombat -> meleeCombat.toInt()
            rangingCombat >= meleeCombat && rangingCombat >= magicCombat -> rangingCombat.toInt()
            else -> magicCombat.toInt()
        }

    val changed = combatLevel != old
    if (changed) {
        addBlock(UpdateBlockType.APPEARANCE)
        return true
    }
    return false
}

fun Player.buildSmithingInterface(bar: BarType) {
    val type = BarProducts.getBars(bar)
    attr[BAR_TYPE] = bar.item
    val componentIds = arrayOf(266, 169, 65, 97, 81, 89, 209, 161, 169)
    // Hide all the "extra" components, dart tips, lanterns, claws, etc..
    componentIds.forEach { component ->
        setComponentHidden(interfaceId = 300, component = component, hidden = true)
    }

    // Unlocks pickaxe component
    setComponentHidden(interfaceId = 300, component = 266, hidden = false)

    type.filterNotNull().forEach {
        // Unlock "extra" components if the type exists
        when (it.smithingType) {
            SmithingType.TYPE_GRAPPLE_TIP -> setComponentHidden(interfaceId = 300, component = 169, hidden = false)
            SmithingType.TYPE_DART_TIPS -> setComponentHidden(interfaceId = 300, component = 65, hidden = false)
            SmithingType.TYPE_IRON_SPIT -> setComponentHidden(interfaceId = 300, component = 89, hidden = false)
            SmithingType.TYPE_WIRE -> setComponentHidden(interfaceId = 300, component = 81, hidden = false)
            SmithingType.TYPE_CLAWS -> setComponentHidden(interfaceId = 300, component = 209, hidden = false)
            SmithingType.TYPE_OIL_LANTERN -> setComponentHidden(interfaceId = 300, component = 161, hidden = false)
            SmithingType.TYPE_BULLSEYE -> setComponentHidden(interfaceId = 300, component = 161, hidden = false)
            SmithingType.TYPE_STUDS -> setComponentHidden(interfaceId = 300, component = 97, hidden = false)
            else -> {}
        }

        var color = ""

        // If the level requirement matches or exceeds the players current level
        if (skills.getCurrentLevel(Skills.SMITHING) >= it.level) {
            color = "<col=FFFFFF>"
        }

        // Set the items name
        setComponentText(
            interfaceId = 300,
            component = it.smithingType.componentId,
            text = "$color${Misc.formatSentence(
                it.smithingType.name
                    .replace("TYPE_", "")
                    .replace("_", " "),
            )}",
        )

        // If the players inventory contains the proper amount of bars
        if (inventory.getItemCount(it.barType.item) >= it.smithingType.barRequirement) {
            color = "<col=2DE120>"
            val str = "Bar"
            setComponentText(
                interfaceId = 300,
                component = it.smithingType.componentId + 1,
                text = "$color${it.smithingType.barRequirement} ${str.pluralSuffix(it.smithingType.barRequirement)}",
            )
        }

        // Finally, send the item on the interface
        setComponentItem(
            interfaceId = 300,
            component = it.smithingType.componentId - 1,
            item = it.result,
            amountOrZoom = it.smithingType.producedAmount,
        )
    }

    // Send the title
    setComponentText(interfaceId = 300, component = 14, text = bar.barName)

    // Open the main interface
    openInterface(dest = InterfaceDestination.MAIN_SCREEN, interfaceId = 300)
}

fun openTanningInterface(player: Player) {
    player.openInterface(dest = InterfaceDestination.MAIN_SCREEN, interfaceId = 324)
}

/**
 * Opens the interface used for crafting summoning pouches.
 *
 * @param player: The [Player] to open the interface for
 */
fun openPouchInterface(player: Player) {
    player.openInterface(672, InterfaceDestination.MAIN_SCREEN)
    val scriptId = 757
    val interfaceId = 672
    val componentId = 16
    val slotLength = 78
    val width = 8
    val height = 10
    val setting = 254

    player.runClientScript(
        scriptId,
        (interfaceId shl 16 or componentId),
        width,
        height,
        "Infuse<col=FF9040>",
        "Infuse-5<col=FF9040>",
        "Infuse-10<col=FF9040>",
        "Infuse-X<col=FF9040>",
        "Infuse-All<col=FF9040>",
        "List<col=FF9040>",
        1,
        slotLength,
    )
    player.setInterfaceEvents(interfaceId, componentId, IntRange(0, 400), setting)
}

/**
 * Opens the interface used for crafting summoning scrolls.
 *
 * @param player: The [Player] to open the interface for
 */
fun openScrollInterface(player: Player) {
    player.openInterface(666, InterfaceDestination.MAIN_SCREEN)
    val scriptId = 763
    val interfaceId = 666
    val componentId = 16
    val slotLength = 78
    val width = 8
    val height = 10
    val setting = 254

    player.runClientScript(
        scriptId,
        (interfaceId shl 16 or componentId),
        width,
        height,
        "Transform<col=FF9040>",
        "Transform-5<col=FF9040>",
        "Transform-10<col=FF9040>",
        "Transform-X<col=FF9040>",
        "Transform-All<col=FF9040>",
        1,
        slotLength,
    )
    player.setInterfaceEvents(interfaceId, componentId, IntRange(0, 400), setting)
}

/**
 * Opens the interface used for trading in summoning scrolls for shards.
 *
 * @param player: The [Player] to open the interface for
 */
fun openScrollTradeInInterface(player: Player) {
    val scrollTradeInComponentScriptId = 320

    val scrollTradeInComponentId = 14
    val pouchTradeInComponentId = 15
    val scrollTabScrollBarComponentId = 16
    val pouchTabScrollBarComponentId = 17
    val pouchTradeInTextComponentId = 18
    val scrollTradeInTextComponentId = 19
    val pouchTabBackgroundComponentId = 24
    val pouchTabSpriteComponentId = 25
    val scrollTabBackgroundComponentId = 26
    val scrollTabSpriteComponentId = 27
    val tradeInInterfaceId = 78

    val activeBackgroundSprite = 952
    val inactiveBackgroundSprite = 953
    val scrollTabActiveSprite = 1190
    val pouchTabInactiveSprite = 1193

    val slotLength = 78
    val width = 8
    val height = 10
    // Six right click options with no examine
    val setting = 190

    player.openInterface(tradeInInterfaceId, InterfaceDestination.MAIN_SCREEN)

    // Hide pouch trade in and show scroll trade in
    player.setComponentHidden(tradeInInterfaceId, scrollTradeInTextComponentId, false)
    player.setComponentHidden(tradeInInterfaceId, pouchTradeInTextComponentId, true)
    player.setComponentHidden(tradeInInterfaceId, scrollTradeInComponentId, false)
    player.setComponentHidden(tradeInInterfaceId, pouchTradeInComponentId, true)

    // Updating the tab icon sprites
    player.setComponentSprite(tradeInInterfaceId, pouchTabSpriteComponentId, pouchTabInactiveSprite)
    player.setComponentSprite(tradeInInterfaceId, scrollTabSpriteComponentId, scrollTabActiveSprite)

    // Updating the tab background sprites
    player.setComponentSprite(tradeInInterfaceId, scrollTabBackgroundComponentId, activeBackgroundSprite)
    player.setComponentSprite(tradeInInterfaceId, pouchTabBackgroundComponentId, inactiveBackgroundSprite)

    // Showing the correct scroll bar
    player.setComponentHidden(tradeInInterfaceId, scrollTabScrollBarComponentId, false)
    player.setComponentHidden(tradeInInterfaceId, pouchTabScrollBarComponentId, true)

    // Builds the trade components with scroll icons
    player.runClientScript(
        scrollTradeInComponentScriptId,
        (tradeInInterfaceId shl 16 or scrollTradeInComponentId),
        width,
        height,
        1,
        slotLength,
        "Value<col=FF9040>",
        "Trade<col=FF9040>",
        "Trade-5<col=FF9040>",
        "Trade-10<col=FF9040>",
        "Trade-X<col=FF9040>",
        "Trade-All<col=FF9040>",
    )

    // Sets up the options / right-click menu for trading
    player.setInterfaceEvents(tradeInInterfaceId, scrollTradeInComponentId, IntRange(0, 400), setting)
}

/**
 * Opens the interface used for trading in summoning pouches for shards.
 *
 * @param player: The [Player] to open the interface for
 */
fun openPouchTradeInInterface(player: Player) {
    val pouchTradeInComponentScriptId = 317

    val scrollTradeInComponentId = 14
    val pouchTradeInComponentId = 15
    val scrollTabScrollBarComponentId = 16
    val pouchTabScrollBarComponentId = 17
    val pouchTradeInTextComponentId = 18
    val scrollTradeInTextComponentId = 19
    val pouchTabBackgroundComponentId = 24
    val pouchTabSpriteComponentId = 25
    val scrollTabBackgroundComponentId = 26
    val scrollTabSpriteComponentId = 27
    val tradeInInterfaceId = 78

    val activeBackgroundSprite = 952
    val inactiveBackgroundSprite = 953
    val pouchTabActiveSprite = 1191
    val scrollTabInactiveSprite = 1192

    val slotLength = 78
    val width = 8
    val height = 10
    // Six right click options with no examine
    val setting = 190

    player.openInterface(tradeInInterfaceId, InterfaceDestination.MAIN_SCREEN)

    // Hide scroll trade in and show pouch trade in
    player.setComponentHidden(tradeInInterfaceId, scrollTradeInTextComponentId, true)
    player.setComponentHidden(tradeInInterfaceId, pouchTradeInTextComponentId, false)
    player.setComponentHidden(tradeInInterfaceId, scrollTradeInComponentId, true)
    player.setComponentHidden(tradeInInterfaceId, pouchTradeInComponentId, false)

    // Updating the tab icon sprites
    player.setComponentSprite(tradeInInterfaceId, pouchTabSpriteComponentId, pouchTabActiveSprite)
    player.setComponentSprite(tradeInInterfaceId, scrollTabSpriteComponentId, scrollTabInactiveSprite)

    // Updating the tab background sprites
    player.setComponentSprite(tradeInInterfaceId, scrollTabBackgroundComponentId, inactiveBackgroundSprite)
    player.setComponentSprite(tradeInInterfaceId, pouchTabBackgroundComponentId, activeBackgroundSprite)

    // Showing the correct scroll bar
    player.setComponentHidden(tradeInInterfaceId, scrollTabScrollBarComponentId, true)
    player.setComponentHidden(tradeInInterfaceId, pouchTabScrollBarComponentId, false)

    // Builds the trade components with pouch icons
    player.runClientScript(
        pouchTradeInComponentScriptId,
        (tradeInInterfaceId shl 16 or pouchTradeInComponentId),
        width,
        height,
        1,
        slotLength,
        "Value<col=FF9040>",
        "Trade<col=FF9040>",
        "Trade-5<col=FF9040>",
        "Trade-10<col=FF9040>",
        "Trade-X<col=FF9040>",
        "Trade-All<col=FF9040>",
    )

    // Sets up the options / right-click menu for trading
    player.setInterfaceEvents(tradeInInterfaceId, pouchTradeInComponentId, IntRange(0, 400), setting)
}

fun essenceTeleport(
    player: Player,
    dialogue: String = "Senventior disthine molenko!",
    targetTile: Tile,
) {
    val npc = player.getInteractingNpc()
    npc.attr[INTERACTING_PLAYER_ATTR] = WeakReference(player)
    val p = npc.getInteractingPlayer()
    npc.queue {
        npc.facePawn(npc.getInteractingPlayer())
        npc.forceChat(dialogue)
        npc.animate(Anims.CAST_BIND_SPELL)
        npc.graphic(Gfx.CURSE_SPELL)
        val projectile = npc.createProjectile(p, Gfx.CURSE_SPELL_PROJ, ProjectileType.MAGIC)
        p.world.spawn(projectile)
        p.playSound(Sfx.CURSE_CAST_AND_FIRE)
        wait(MagicCombatStrategy.getHitDelay(npc.tile, p.tile) + 1)
        p.attr[ESSENCE_MINE_INTERACTED_WITH] = npc.id
        p.moveTo(targetTile)
        wait(1)
        p.graphic(Gfx.CURSE_SPELL_TARGET_EFFECT)
        p.playSound(Sfx.CURSE_HIT)
    }
}

// Note: this does not take ground items, that may belong to the player, into
// account.
fun Player.hasItem(
    item: Int,
    amount: Int = 1,
): Boolean = containers.values.firstOrNull { container -> container.getItemCount(item) >= amount } != null

fun Player.isPrivilegeEligible(to: String): Boolean = world.privileges.isEligible(privilege, to)

fun Player.getSummoningBonus(): Int = equipmentBonuses[BonusSlot.SUMMONING_BONUS.id]

fun Player.getStrengthBonus(): Int = equipmentBonuses[BonusSlot.STRENGTH_BONUS.id]

fun Player.getRangedStrengthBonus(): Int =
    when {
        hasWeaponType(WeaponType.THROWN) || hasWeaponType(WeaponType.CHINCHOMPA) || hasWeaponType(WeaponType.SLING) -> {
            // The weapon is its own ammunition, so whatever sits in the ammo slot does not count - but every OTHER worn item does.
            // The 667 rule here was "the weapon's own bonus only", which was equivalent in 2011 (only ammunition carried ranged strength)
            // and silently dropped the ranged strength of the imported OSRS gear (Necklace of anguish, Masori, Zaryte vambraces,
            // Dizana's quiver, ...) for knives, darts, blowpipes, chinchompas and the Tonalztics (combat audit 2026-09-17c).
            val ammoSlot =
                equipment[EquipmentType.AMMO.id]?.let { world.definitions.get(ItemDef::class.java, it.id).bonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] } ?: 0
            equipmentBonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] - ammoSlot +
                // OSRS Wiki "Toxic blowpipe": its +20 stacks with the loaded darts' ranged strength (dragon darts: +55).
                gg.rsmod.plugins.content.items.osrs.Blowpipe.dartStrength(equipment[3])
        }
        // Dizana's Sunfire: +1 Ranged strength for arrows and bolts while the quiver is charged or blessed.
        // A shot from Dizana's quiver's second slot uses the stored ammo's strength instead of the unused slot ammo's.
        else ->
            equipmentBonuses[BonusSlot.RANGED_STRENGTH_BONUS.id] + gg.rsmod.plugins.content.items.osrs.DizanasQuiver.strengthBonus(this) +
                gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo.quiverBonusCorrection(this, BonusSlot.RANGED_STRENGTH_BONUS)
    }

fun Player.getMagicDamageBonus(): Int = equipmentBonuses[BonusSlot.MAGIC_DAMAGE_BONUS.id]

fun Player.getPrayerBonus(): Int = equipmentBonuses[BonusSlot.PRAYER_BONUS.id]

fun Player.completedAllQuests(): Boolean {
    return getVarp(Varps.QUEST_POINTS) >= Quest.quests.sumOf { it.pointReward }
}

fun Player.checkEquipment() {
    equipment.filterNotNull().forEach { item ->
        if ((item.id == Items.QUEST_POINT_HOOD || item.id == Items.QUEST_POINT_CAPE) && !completedAllQuests()) {
            disableEquipment(item.id)
        }
    }
}

fun Player.disableEquipment(itemId: Int) {
    val itemName = world.definitions.get(ItemDef::class.java, itemId).name
    equipment.remove(itemId)
    val container = if (inventory.hasSpace) inventory else bank
    container.add(itemId)
    message(
        "Your $itemName was removed from your equipment and added to your ${if (container == bank) "bank" else "inventory"}.",
    )
}

fun Player.setSkillTargetEnabled(
    skill: Int,
    enabled: Boolean,
) {
    enabledSkillTarget[skill] = enabled
    refreshSkillTarget()
}

fun Player.setSkillTargetMode(
    skill: Int,
    enabled: Boolean,
) {
    skillTargetMode[skill] = enabled
    refreshSkillTargetMode()
}

fun Player.setSkillTargetValue(
    skill: Int,
    value: Int,
) {
    skillTargetValue[skill] = value
    refreshSkillsTargetsValues()
}

fun Player.refreshSkillTarget() {
    val value: Int = Misc.get32BitValue(enabledSkillTarget, true)
    setVarp(Varps.SKILL_TARGETS_SET, value)
}

fun Player.refreshSkillTargetMode() {
    val value: Int = Misc.get32BitValue(skillTargetMode, true)
    setVarp(Varps.SKILL_TARGET_MODES, value)
}

fun Player.refreshSkillsTargetsValues() {
    for (i in 0..24) {
        setVarp(Varps.SKILL_TARGET_ATTACK + i, skillTargetValue[i])
    }
}

fun Player.setSkillTarget(
    usingLevel: Boolean,
    skill: Int,
    target: Int,
) {
    setSkillTargetEnabled(skill, true)
    setSkillTargetMode(skill, usingLevel)
    setSkillTargetValue(skill, target)
}

fun Player.getWeaponRenderAnimation(): Int {
    val weapon = equipment[3]
    if (weapon != null) {
        val def: Any = gg.rsmod.game.model.RenderAnimations.forWeapon(weapon.id) ?: weapon.getDef(world.definitions).params.get(644) ?: 1426
        return def as Int
    }
    return 1
}

fun Player.handleLadder(
    x: Int = -1,
    z: Int = -1,
    height: Int = 0,
    underground: Boolean = false,
) {
    val climbUp = getInteractingGameObj().getDef(world.definitions).options.any { it?.lowercase() == "climb-up" }
    lockingQueue(lockState = LockState.FULL) {
        wait(1)
        animate(Anims.LADDER_CLIMB, idleOnly = true)
        wait(2)
        val zOffset =
            when (climbUp) {
                true -> -6400
                false -> 6400
            }
        moveTo(
            x = if (x > -1) x else player.tile.x,
            z =
                if (z > -1) {
                    z
                } else if (underground) {
                    player.tile.z + zOffset
                } else {
                    player.tile.z
                },
            height = height,
        )
    }
}

fun Player.handleStairs(
    x: Int = -1,
    z: Int = -1,
    height: Int = 0,
    underground: Boolean = false,
) {
    val climbUp = getInteractingGameObj().getDef(world.definitions).options.any { it?.lowercase() == "climb-up" }
    lockingQueue(lockState = LockState.FULL) {
        val zOffset =
            when (climbUp) {
                true -> -6400
                false -> 6400
            }
        wait(2)
        moveTo(
            x = if (x > -1) x else player.tile.x,
            z =
                if (z > -1) {
                    z
                } else if (underground) {
                    player.tile.z + zOffset
                } else {
                    player.tile.z
                },
            height = height,
        )
    }
}

/**
 * Handle the opening of the Jewellery Crafting Interface
 * @author Kevin Senez <ksenez94@gmail.com>
 */
fun Player.openJewelleryCraftingInterface() {
    openInterface(dest = InterfaceDestination.MAIN_SCREEN, interfaceId = 446)

    /**
     * Check for moulds and hide text if there.
     */
    setComponentHidden(interfaceId = 446, component = 17, hidden = inventory.contains(Items.RING_MOULD))
    setComponentHidden(interfaceId = 446, component = 21, hidden = inventory.contains(Items.NECKLACE_MOULD))
    setComponentHidden(interfaceId = 446, component = 26, hidden = inventory.contains(Items.AMULET_MOULD))
    setComponentHidden(interfaceId = 446, component = 30, hidden = inventory.contains(Items.BRACELET_MOULD))

    /**
     * Hide category if inventory doesn't contain mould
     */
    setComponentHidden(interfaceId = 446, component = 18, hidden = !inventory.contains(Items.RING_MOULD))
    setComponentHidden(interfaceId = 446, component = 23, hidden = !inventory.contains(Items.NECKLACE_MOULD))
    setComponentHidden(interfaceId = 446, component = 27, hidden = !inventory.contains(Items.AMULET_MOULD))
    setComponentHidden(interfaceId = 446, component = 31, hidden = !inventory.contains(Items.BRACELET_MOULD))

    /**
     * Hide of show model/option components according to if the player has the required items or not.
     */
    if (inventory.contains(Items.GOLD_BAR)) {
        JewelleryData.values.forEach { data ->
            data.products.forEach product@{ product ->
                if (data != JewelleryData.GOLD) {
                    if (inventory.contains(data.gemRequired)) {
                        if (data == JewelleryData.SLAYER_RING &&
                            attr[CRAFT_ROS] != true
                        ) {
                            return@product
                        }

                        setComponentItem(
                            interfaceId = 446,
                            component = product.modelComponent,
                            item = product.resultItem,
                            amountOrZoom = 1,
                        )
                    } else {
                        setComponentHidden(interfaceId = 446, component = product.modelComponent, hidden = true)
                        setComponentHidden(interfaceId = 446, component = product.optionComponent, hidden = true)
                    }
                } else {
                    setComponentItem(
                        interfaceId = 446,
                        component = product.modelComponent,
                        item = product.resultItem,
                        amountOrZoom = 1,
                    )
                }
            }
        }
    }
}

fun Player.contactSlayerMaster(fromRing: Boolean = false) {
    if (!attr.has(SLAYER_MASTER)) {
        message("You try to activate the gem; but get no response.")
        return
    }

    val master = SlayerMaster.values().firstOrNull { it.id == attr[SLAYER_MASTER] } ?: return
    queue {
        chatNpc("Hello there ${Misc.formatForDisplay(username)}, what can I help you with?", npc = master.id)
        when (options("How am I doing so far?", "Who are you?", "Where are you?", "Nothing really.")) {
            FIRST_OPTION -> {
                chatPlayer("How am I doing so far?")
                // TODO: Confirm this is the "point text" in 2011 - Taken from OSRS
                val pointText = if (fromRing) " Your reward point tally is ${attr[SLAYER_POINTS]}." else ""
                if (getSlayerAssignment() == null) {
                    chatNpc(
                        "You need something new to hunt. Come and see me when you can and I'll give you a new task." +
                            pointText,
                        wrap = true,
                        npc = master.id,
                    )
                } else {
                    chatNpc(
                        "You're currently assigned to kill ${getSlayerAssignment()!!.identifier.lowercase()}; only " +
                            "${attr[SLAYER_AMOUNT]} more to go." + pointText,
                        wrap = true,
                        npc = master.id,
                    )
                }
            }
            SECOND_OPTION -> {
                chatPlayer("Who are you?")
                chatNpc("My name's ${master.identifier}; I'm a Slayer Master.", npc = master.id)
            }
            THIRD_OPTION -> {
                // TODO: Add more options for this when adding more slayer masters
                chatPlayer("Where are you?")
                chatNpc("You'll find me in Burthorpe. I'll be here when you need a", "new task.", npc = master.id)
            }
            FOURTH_OPTION -> {
                chatPlayer("Nothing really.")
            }
        }
    }
}

fun Player.getSlayerKillsRemaining() {
    if (getSlayerAssignment() == null) {
        message("You currently do not have a task; speak to a Slayer Master to receive one.")
        return
    }
    message(
        "Your current assignment is ${getSlayerAssignment()!!.identifier.lowercase()}; only ${attr[SLAYER_AMOUNT]} " +
            "more to go.",
    )
}

/**
 * Handle opening of the Silver Crafting interface.
 * @author Kevin Senez <ksenez94@gmail.com>
 */
fun Player.openSilverCraftingInterface() {
    /**
     * Hides the silver key option (not used)
     */
    setComponentHidden(interfaceId = 438, component = 78, hidden = true)

    /**
     * Sends the related data on interface open
     */
    SilverData.values.forEach { data ->
        if (!inventory.contains(data.mould.id)) {
            setComponentHidden(interfaceId = 438, component = data.componentArray[0], hidden = true)
            setComponentHidden(interfaceId = 438, component = data.componentArray[4], hidden = false)
            setComponentItem(
                interfaceId = 438,
                component = data.componentArray[5],
                item = data.mould.id,
                amountOrZoom = 1,
            )
        } else {
            // Show the result item in interface
            setComponentItem(
                interfaceId = 438,
                component = data.componentArray[2],
                item = data.resultItem.id,
                amountOrZoom = 1,
            )
            // Show the options as available
            setComponentHidden(interfaceId = 438, component = data.componentArray[1], hidden = false)

            // Set text to red if players current crafting level isn't high enough to craft item
            if (skills.getCurrentLevel(Skills.CRAFTING) < data.levelRequired) {
                setComponentText(
                    interfaceId = 438,
                    component = data.componentArray[3],
                    text = "<col=ff0000>Make<br><col=ff0000>${
                        world.definitions.get(
                            ItemDef::class.java,
                            data.resultItem.id,
                        ).name.replace(" ", "<br><col=ff0000>")
                    }",
                )
            }
        }
    }

    openInterface(dest = InterfaceDestination.MAIN_SCREEN, interfaceId = 438)
}

fun Player.farmingManager(): FarmingManager {
    if (this.attr[Constants.farmingManagerAttr] == null) {
        this.attr[Constants.farmingManagerAttr] = FarmingManager(this)
    }
    return this.attr[Constants.farmingManagerAttr]!!
}

fun Player.sendTabs() {
    InterfaceDestination.values.filter { pane -> pane.interfaceId != -1 }.forEach { pane ->
        openInterface(spellbookInterfaceId(pane), pane)
    }
}

/**
 * The interface that belongs in [InterfaceDestination.MAGIC_TAB] for this player's current
 * spellbook: 192 standard, 193 Ancient Magicks, 430 Lunar (ids decoded from the production cache).
 */
fun Player.spellbookInterfaceId(pane: InterfaceDestination = InterfaceDestination.MAGIC_TAB): Int =
    if (pane != InterfaceDestination.MAGIC_TAB) pane.interfaceId else when (getSpellbook()) {
        Spellbook.ANCIENT -> 193
        Spellbook.LUNAR -> 430
        else -> 192
    }

/**
 * Switches the player to [book], clears any autocast/cast state and re-opens the magic tab.
 */
fun Player.switchSpellbook(book: Spellbook) {
    setSpellbook(book)
    attr.remove(gg.rsmod.plugins.content.combat.Combat.CASTING_SPELL)
    // OSRS: a spell of the old book can no longer be autocast; the autocast registry decides and logs the reset.
    gg.rsmod.plugins.content.combat.magic.Autocast.onSpellbookChanged(this)
    closeInterface(InterfaceDestination.MAGIC_TAB)
    openInterface(spellbookInterfaceId(), InterfaceDestination.MAGIC_TAB)
}

fun Player.refreshBonuses() {
    // EquipAction mutates the container before the next Player.cycle() marks the cached array
    // clean. Recalculate here as well so the equipment interface cannot display one cycle of
    // stale values after an equip, unequip, death cleanup, or OSRS weapon ammo swap.
    Arrays.fill(equipmentBonuses, 0)
    equipment.filterNotNull().forEach { item ->
        item.getDef(world.definitions).bonuses.forEachIndexed { index, bonus ->
            equipmentBonuses[index] += bonus
        }
    }
    calculateBonuses = false

    val names =
        listOf(
            "Stab", "Slash", "Crush", "Magic", "Ranged",
            "Stab", "Slash", "Crush", "Magic", "Ranged",
            "Melee STR", "Ranged STR", "Magic DMG", "Prayer",
        )
    val bonusIndices = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 14, 15, 17, 16)
    val components = intArrayOf(31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 45, 46, 47, 48)

    setVarc(779, getWeaponRenderAnimation())
    for (i in bonusIndices.indices) {
        val bonusIndex = bonusIndices[i]
        var bonusName: String = StringBuilder(names[i]).append(": ").toString()
        val bonus: Int = equipmentBonuses[bonusIndex]
        // Slot 17 (magic damage) holds tenths of a percent: 5 -> "0.5", 150 -> "15".
        val value = if (bonusIndex == 17 && bonus % 10 != 0) String.format(java.util.Locale.ROOT, "%.1f", bonus / 10.0) else if (bonusIndex == 17) (bonus / 10).toString() else bonus.toString()
        bonusName = StringBuilder(bonusName).append(if (bonus >= 0) "+" else "").append(value).toString()
        if (bonusIndex == 17) {
            bonusName = StringBuilder(bonusName).append("%").toString()
        }
        setComponentText(667, components[i], bonusName)
    }
    // These are valid combat-internal fields but are not part of the OSRS equipment-bonus
    // screen. Clear their old 667 text so an earlier cached value can never remain visible.
    for (component in 41..44) {
        setComponentText(667, component, "")
    }
}

/**
 * Sets the [Player]'s camera angle with a roll of 0.
 *
 * @param pitch The pitch of the camera
 * @param yaw The yaw of the camera
 */
fun Player.forceCameraAngle(
    pitch: Int,
    yaw: Int,
) {
    write(CameraForceAngleMessage(pitch, yaw))
}

/**
 * Snaps or pans the camera to the specified location
 *
 * @param rate How fast the camera moves to the specified location. Values >= 100 are instant movement.
 * @param x The local x location to move the camera to.
 * @param z The local z location to move the camera to.
 * @param height The height to move the camera to.
 * @param step Currently uncertain exactly what this setting does.
 */
fun Player.moveCameraTo(
    rate: Int,
    x: Int,
    z: Int,
    height: Int,
    step: Int,
) {
    write(CameraMoveToMessage(rate, x, z, height, step))
}

/**
 * Resets the camera to its default state instantly.
 */
fun Player.resetCamera() {
    write(CameraResetMessage())
}

/**
 * Resets the camera to its default state slowly.
 */
fun Player.resetCameraSmooth() {
    write(CameraSmoothResetMessage())
}

/**
 * Forces the camera to look at a specific point in the local area.
 *
 * @param x The x coord in the local area to look at
 * @param z The z coord in the local area to look at
 * @param height The height to look at
 * @param step How many steps are taken in moving the camera
 * @param speed The speed at which the camera moves. Values >= 100 are instant
 */
fun Player.cameraLookAt(
    x: Int,
    z: Int,
    height: Int,
    step: Int,
    speed: Int,
) {
    write(CameraLookAtMessage(x, z, height, step, speed))
}

/**
 * Shakes the [Player]'s camera
 *
 * @param frequency How quickly the camera shakes
 * @param index The direction being shaken: 0=x, 1=y, 2=z, 3=yaw, 4=pitch
 * @param time Uncertain at the moment what this does
 * @param center How shaky the movement is (Think 0 as bobbing on the water, higher as explosions)
 * @param amplitude How far the up and down / left and right / etc. the camera moves
 */
fun Player.shakeCamera(
    frequency: Int,
    index: Int,
    time: Int,
    center: Int,
    amplitude: Int,
) {
    write(CameraShakeMessage(frequency, index, time, center, amplitude))
}

private val entranaPermittedItems: List<Int> =
    listOf(
        Items.PENANCE_GLOVES,
        Items.PENANCE_GLOVES_10554,
        Items.ICE_GLOVES,
        Items.GLOVES_OF_SILENCE,
        Items.SPINACH_ROLL,
        Items.BREAD,
        Items.CHOCOLATE_SLICE,
        Items.BAGUETTE,
        Items.KEBAB,
        Items.STEW,
        Items.SPICY_STEW,
        Items.CABBAGE,
        Items.ONION,
        Items.SPICY_SAUCE,
        Items.CHILLI_CON_CARNE,
        Items.SCRAMBLED_EGG,
        Items.EVIL_TURNIP,
        Items._23_EVIL_TURNIP,
        Items._13_EVIL_TURNIP,
        Items.TUNA_AND_CORN,
        Items.POT_OF_CREAM,
        Items.DWELLBERRIES,
        Items.PEACH,
        Items.ROLL,
        Items.CHOCOLATE_BAR,
        Items.ANCHOVIES,
        Items.STUFFED_SNAKE,
        Items.UGTHANKI_MEAT,
        Items.SHRIMPS,
        Items.SARDINE,
        Items.HERRING,
        Items.MACKEREL,
        Items.COD,
        Items.TROUT,
        Items.CAVE_EEL,
        Items.CAVE_EEL_O,
        Items.PIKE,
        Items.SALMON,
        Items.TUNA,
        Items.FURY_SHARK,
        Items.LAVA_EEL,
        Items.LOBSTER,
        Items.BASS,
        Items.SHARK,
        Items.SHARK_6969,
        Items.SEA_TURTLE,
        Items.MANTA_RAY,
        Items.CRAB_MEAT,
        Items.FROG_MASK,
        Items.FROG_MASK_10721,
        Items.GNOME_SCARF,
        Items.GNOME_SCARF_22215,
        Items.GNOME_SCARF_22216,
        Items.GNOME_SCARF_22217,
        Items.GNOME_SCARF_22218,
    )

fun Player.hasEntranaRestrictedEquipment(): Boolean {
    val allItemsList =
        (inventory.sequence() + equipment.sequence())
            .filterNot { item ->
                exceptionList(item) ||
                    excludedNames(this, item) ||
                    item.getDef(world.definitions).equipSlot < 0
            }.toList()

    return allItemsList.any()
}

/**
 *  Adds the given experience, giving extra if there are brawling gloves that
 *  are being worn and have charges to take
 *  Returns the boost that was given. Will be 1.0 if no boost given
 */
fun Player.addXp(skill: Int, xp: Double, checkBrawlingGloves: Boolean = false): Double {
    val type = BrawlingGloves.values().firstOrNull { skill == it.skill }
    val gloves = equipment[EquipmentType.GLOVES.id]
    val inWildy = tile.getWildernessLevel() > 0
    if (type == null || gloves == null || !checkBrawlingGloves) {
        addXp(skill, xp)
        return 1.0
    }
    var tookCharge = false
    if (gloves.id == type.itemId) {
        if (gloves.hasAnyAttr()) {
            val charges = gloves.attr[ItemAttribute.CHARGES]
            val newCharges = charges!! - 1
            gloves.attr[ItemAttribute.CHARGES] = newCharges
            if (newCharges <= 0) {
                equipment.remove(gloves)
                message("<col=ff0000>Your brawling gloves disintegrate.")
                graphic(Graphic(Gfx.GFX_1858, 0, 0))
            }
            tookCharge = true
        }
        else {
            val charges = type.maxCharges.coerceAtMost(
                interpolate(
                    (type.maxCharges / 2.0),
                    type.maxCharges.toDouble(),
                    skills.getMaxLevel(type.skill)
                ).toInt()
            )
            gloves.attr[ItemAttribute.CHARGES] = charges - 1
            tookCharge = true
        }
    }
    val xpBoost = if (inWildy) type.wildernessBonus else type.nonWildernessBonus
    val xpToGive = if (tookCharge) xp * xpBoost else xp
    addXp(skill, xpToGive)
    return if (tookCharge) xpBoost else 1.0
}

private fun exceptionList(item: Item): Boolean {
    return item.id in entranaPermittedItems
}

private fun excludedNames(
    player: Player,
    item: Item,
): Boolean {
    val name = item.getName(player.world.definitions)
    if (name.startsWith("Ring of ")) {
        return true
    }
    if (name.startsWith("Amulet of ")) {
        return true
    }
    if (name.startsWith("Ancient ceremonial")) {
        return true
    }
    if (name.contains(" ring")) {
        return true
    }
    if (name.contains(" amulet")) {
        return true
    }
    if (name.contains(" necklace")) {
        return true
    }
    if (name.contains(" pendant")) {
        return true
    }
    if (name.contains(" bracelet")) {
        return true
    }
    if (name.endsWith(" cloak")) {
        return true
    }
    if (name.endsWith(" dragon mask")) {
        return true
    }
    if (name.endsWith(" bolts")) {
        return true
    }
    if (name.endsWith(" bolts (e)")) {
        return true
    }
    if (name.endsWith(" afro")) {
        return true
    }
    if (name.lowercase().contains("arrow")) {
        return true
    }
    if (name.endsWith(" (p)")) {
        return true
    }
    if (name.endsWith(" (p+)")) {
        return true
    }
    if (name.endsWith(" (p++)")) {
        return true
    }
    if (name.endsWith(" partyhat")) {
        return true
    }
    if (name.endsWith(" of lightness")) {
        return true
    }
    if (name.contains("cavalier")) {
        return true
    }
    if (name.contains("book")) {
        return true
    }
    if (name.contains(" cape")) {
        return true
    }
    if (name.contains("beret")) {
        return true
    }
    if (name.startsWith("Ghostly")) {
        return true
    }
    return false
}

fun Player.setComponentModel(
    interfaceId: Int,
    component: Int,
    model: Int,
) {
    write(IfSetModelMessage(hash = ((interfaceId shl 16) or component), model = model))
}
