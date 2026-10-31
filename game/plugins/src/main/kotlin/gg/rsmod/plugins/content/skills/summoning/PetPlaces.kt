package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message

/**
 * N407 checklist point 15 (K02): the places a pet may not follow - OSRS Wiki "Pet", section Restrictions (fetched 2026-10-31),
 * for the ones this world has. The messages are the wiki's.
 *
 * - Hidden follower (Pest Control, Soul Wars, Sorceress's Garden): a following pet is hidden on the way in ("Your pet has been
 *   hidden as you enter this area."), can't be called, picked up or dropped inside, and is out again on leaving ([Pets.tick]).
 *   Soul Wars also refuses a pet carried in the backpack (its join calls [carriesPet]).
 * - Unable to enter with a follower: Corporeal Beast's cave (a teleport in hides the pet, [HiddenArea.CORP_CAVE]), Nex (also not
 *   in the backpack), building mode in the house.
 * - Ape Atoll Agility Course: an obstacle puts a following pet in the backpack ([beforeApeAtollObstacle]).
 * - Castle Wars (OSRS: hidden, and no pet in the backpack - "You can't take non-combat items into the arena.") WAITS: N476, Castle
 *   Wars work only after the owner's N442 test - then a CASTLE_WARS entry here (its waiting/playing lists) and a check in joinPortal.
 * Not here: Barbarian Assault, Fishing Trawler, Rogues' Den, Brimhaven Agility Arena, Chambers of Xeric, Mogre Camp, Sled Racing.
 */
object PetPlaces {
    const val MSG_HIDDEN = "Your pet has been hidden as you enter this area."
    const val MSG_CORP_ENTER = "Your follower hides in fear and refuses to enter the cave."
    const val MSG_CORP_DROP = "Your follower hides in fear and won't come out."
    const val MSG_NEX = "This doesn't seem like a suitable place to take a pet."
    const val MSG_BUILD_MODE = "You should pick up your follower before entering building mode."
    const val MSG_BUILD_MODE_DROP = "You cannot drop items while in building mode."
    const val MSG_SOUL_WARS = "You can't enter a waiting area with a follower."
    const val MSG_APE_ATOLL = "Your little friend can't make it over the obstacle..."

    /**
     * Ape Atoll Agility Course: a following pet goes into the backpack at an obstacle. False when it can't (backpack full, an
     * Upgraded pet carrying items): the obstacle is then not attempted.
     */
    fun beforeApeAtollObstacle(player: Player): Boolean {
        if (Pets.current(player) == null) return true
        player.message(MSG_APE_ATOLL)
        if (Pets.stow(player)) return true
        // ADAPTED (no source for a full backpack): Pick-up's own refusal, or the Upgraded pet's pack.
        player.message(if (player.inventory.hasSpace) "Your pet is carrying items - pick it up first." else "You don't have enough inventory space to do that.")
        return false
    }

    /** A minigame that hides a following pet; [dropRefusal] answers a pet item dropped inside. */
    enum class HiddenArea(val dropRefusal: String) {
        PEST_CONTROL("Your follower hides in your pack, this is too violent an area for it."),
        SOUL_WARS(MSG_SOUL_WARS),
        SORCERESS_GARDEN("Your pet stays in your pack, frightened by something."),

        /** The cave entrance refuses a following pet; a teleport in (games necklace, house portal) hides it instead. */
        CORP_CAVE(MSG_CORP_DROP),
    }

    /**
     * The Soul Wars lobby (Balance portal 1889,3161, barriers 1879/1900,3162): a player on the lobby list counts only here - the
     * Balance portal lists a player without moving them and walking off does not unlist them.
     */
    const val SOUL_WARS_LOBBY_REGION = 7473

    /** The Sorceress's Garden (all four seasons and the house between them, under Al Kharid). */
    const val SORCERESS_GARDEN_REGION = 11605

    /** The Corporeal Beast's cave and lair: these two regions on plane 2 (corporeal_beast_lair.plugin.kts tiles 2885,4372 - 3001,4397). */
    val CORP_CAVE_REGIONS = setOf(11588, 11844)
    const val CORP_CAVE_HEIGHT = 2

    /** The minigame [player] is in that hides a pet: waiting or playing, by the minigame's own lists (any way in counts). */
    fun hiddenArea(player: Player): HiddenArea? {
        val pestControl = gg.rsmod.plugins.content.activity.pest_control.PestControlHandler
        val soulWars = gg.rsmod.plugins.content.activity.soul_wars.SoulWarsMatch
        return when {
            pestControl.lobbyOf(player) != null || player.attr[pestControl.GAME_ATTR]?.active == true -> HiddenArea.PEST_CONTROL
            player.attr[gg.rsmod.plugins.content.activity.soul_wars.SW_TEAM] != null ||
                (player in soulWars.lobbyPlayers && player.tile.regionId == SOUL_WARS_LOBBY_REGION) -> HiddenArea.SOUL_WARS
            player.tile.regionId == SORCERESS_GARDEN_REGION -> HiddenArea.SORCERESS_GARDEN
            player.tile.height == CORP_CAVE_HEIGHT && player.tile.regionId in CORP_CAVE_REGIONS -> HiddenArea.CORP_CAVE
            else -> null
        }
    }

    /** Why a pet item can't be dropped where [player] stands; null = it may come out. */
    fun dropRefusal(player: Player): String? =
        hiddenArea(player)?.dropRefusal
            ?: when {
                gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter.inArena(player.tile) -> MSG_NEX
                gg.rsmod.plugins.content.areas.poh.PlayerHouse.isBuildMode(player) &&
                    gg.rsmod.plugins.content.areas.poh.PlayerHouse.ownsHouseAt(player) -> MSG_BUILD_MODE_DROP
                else -> null
            }

    /** A pet item in [player]'s backpack (Soul Wars and Nex refuse one). */
    fun carriesPet(player: Player): Boolean = player.inventory.toList().any { it != null && Pets.isPetItem(it.id) }
}
