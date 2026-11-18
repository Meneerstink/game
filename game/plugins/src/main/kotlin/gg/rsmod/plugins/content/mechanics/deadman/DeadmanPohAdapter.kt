package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.poh.PlayerHouse
import gg.rsmod.plugins.content.areas.poh.PohBuild

/**
 * DMM2 POH adapter for the Deadman rug (dossier DMM-PAKKET-2 18-11 10:5x plan, advice chosen: the rug spots become a third
 * buildable place in our fully furnished house, only for the Deadman rug).
 *
 * Source OSRS Wiki "Deadman rug": built on a rug space (parlour, bedroom, skill hall, quest hall, chapel, dungeon, portal nexus),
 * Construction 28, 283 XP, 4 bolts of cloth + 1 Armageddon rug, saw + hammer. OSRS locs 54114 corner / 54115 side / 54116 middle
 * (RuneLite POH_DEADMAN_RUGCORNER/RUGSIDE/RUGMIDDLE) = ours 66909/66910/66911 via data/cfg/dmm2/ids.tsv.
 *
 * Our house fills rug spot 15274 (corner) and 15273 (side) with the opulent rug ([PlayerHouse.RUG_SPOTS]); there is no middle spot.
 * One build covers every rug spot of the room, like Construction. State and materials live in [NigelHouseRewards] (one RUG slot per
 * [PlayerHouse.Room]); this object only connects its results to the house: the pieces per spot, the Build/Remove menus and messages.
 */
object DeadmanPohAdapter {
    private const val SPOT_SIDE = 15273
    private const val SPOT_CORNER = 15274

    /** OSRS loc ids; the local ids come from the DMM2 mapping, never hard-coded. */
    private const val SOURCE_CORNER = 54114
    private const val SOURCE_SIDE = 54115

    /** RuneLite gameval POH_EXIT_PORTAL_WILDERNESS (OsrsLocImportTool batch 'dmm2-exit-portal'). */
    private const val SOURCE_EXIT_PORTAL = 60789

    /** Wiki 'Annihilation exit portal blueprints': 10 iron bars + the blueprints, hammer + saw, in the garden centrepiece space. */
    private const val PORTAL_BARS = 10

    private const val LEVEL = 28
    private const val XP = 283.0

    val SLOTS: Map<PlayerHouse.Room, NigelHouseRewards.Slot> =
        PlayerHouse.Room.values().associateWith { NigelHouseRewards.Slot(it.name.lowercase(), NigelHouseRewards.Kind.RUG) }

    /** Our house has one centrepiece space, the garden's (A25). */
    val EXIT_SLOT = NigelHouseRewards.Slot("garden_exit", NigelHouseRewards.Kind.EXIT_PORTAL, room = "garden")

    fun corner(): Int? = Dmm2Ids.loc(SOURCE_CORNER)
    fun side(): Int? = Dmm2Ids.loc(SOURCE_SIDE)
    fun exitPortal(): Int? = Dmm2Ids.loc(SOURCE_EXIT_PORTAL)

    /** The Deadman rug pieces placed in the house (Remove handlers bind on these). */
    fun pieces(): List<Int> = listOfNotNull(corner(), side())

    @Volatile
    private var rewards: NigelHouseRewards? = null

    /** Called at DMM2 boot after the Nigel reward store is installed; without store or loc mapping the house stays as it was. */
    fun install() {
        val store = NigelRewardStore.active
        if (store == null || corner() == null || side() == null) {
            rewards = null
            PlayerHouse.deadmanSpot = null
            return
        }
        rewards =
            NigelHouseRewards(
                store,
                SLOTS.values.toSet() + EXIT_SLOT,
                validHotspot = { player, slot ->
                    val room = PlayerHouse.roomAt(player.tile)
                    if (slot == EXIT_SLOT) room == PlayerHouse.Room.GARDEN else room?.let { SLOTS[it] } == slot
                },
                // Our house always has its exit portal in the garden centre (A25).
                otherExitPortals = { 1 },
            )
        PlayerHouse.deadmanSpot = { player, buildMode, room, spot -> pieceFor(player, buildMode, room, spot) }
    }

    fun installed(player: Player, room: PlayerHouse.Room): Boolean = rewards?.installed(player, SLOTS.getValue(room)) == true

    private fun pieceFor(
        player: Player,
        buildMode: Boolean,
        room: PlayerHouse.Room,
        spot: Int,
    ): Int? {
        if (spot == PlayerHouse.EXIT_PORTAL_SPOT) {
            val portal = exitPortal() ?: return null
            return when {
                rewards?.installed(player, EXIT_SLOT) == true -> portal
                // Building mode with the blueprints in the inventory: the centrepiece space comes back so it can be built on;
                // otherwise the house keeps its own exit portal (our house has exactly one, never left without one).
                buildMode && blueprints()?.let { player.inventory.contains(it) } == true -> PlayerHouse.KEEP_HOTSPOT
                else -> null
            }
        }
        return when {
            installed(player, room) -> if (spot == SPOT_CORNER) corner() else side()
            buildMode -> PlayerHouse.KEEP_HOTSPOT
            else -> null
        }
    }

    private fun blueprints(): Int? = NigelRewardStore.active?.contract?.exitBlueprint

    /** Build on the garden centrepiece space with the blueprints: the Annihilation exit portal (blueprints are used up). */
    suspend fun buildExitPortal(task: QueueTask) {
        val player = task.player
        val rewards = rewards ?: return player.message("Nothing interesting happens.")
        if (!PohBuild.requireBuildMode(player)) return
        val portal = exitPortal() ?: return player.message("Nothing interesting happens.")
        val blueprint = blueprints() ?: return player.message("Nothing interesting happens.")
        val piece = PohBuild.Piece(portal, "Annihilation exit portal", 1, 0.0, listOf(Items.IRON_BAR to PORTAL_BARS, blueprint to 1))
        val chosen = PohBuild.choose(task, listOf(piece), "Centrepiece space") ?: return
        if (!PohBuild.canBuild(task, chosen)) return
        if (rewards.buildExitPortal(player, EXIT_SLOT) != NigelHouseRewards.Result.OK) return player.message("Nothing interesting happens.")
        player.lock = gg.rsmod.game.model.LockState.FULL
        player.animate(PohBuild.BUILD_ANIM)
        player.playSound(Sfx.POH_BUILD_WOOD)
        task.wait(2)
        player.unlock()
        PlayerHouse.rebuild(player)
    }

    /** Remove on the Annihilation exit portal: the blueprints do not come back (wiki), the house's own exit portal returns. */
    suspend fun removeExitPortal(task: QueueTask) {
        val player = task.player
        val rewards = rewards ?: return
        if (!PohBuild.requireBuildMode(player) || !PohBuild.confirmRemove(task)) return
        if (rewards.remove(player, EXIT_SLOT) != NigelHouseRewards.Result.OK) return
        player.lock = gg.rsmod.game.model.LockState.FULL
        player.animate(PohBuild.REMOVE_ANIM)
        task.wait(2)
        player.unlock()
        PlayerHouse.rebuild(player)
    }

    private fun piece(): PohBuild.Piece? {
        val rug = NigelRewardStore.active?.contract?.rewards?.get(NigelRewardStore.Reward.RUG) ?: return null
        val side = side() ?: return null
        return PohBuild.Piece(side, "Deadman rug", LEVEL, XP, listOf(Items.BOLT_OF_CLOTH to 4, rug to 1))
    }

    /** Build on a rug space (building mode): the Deadman rug, 5 ticks with the build animation, then the house reloads. */
    suspend fun build(task: QueueTask) {
        val player = task.player
        val rewards = rewards ?: return player.message("Nothing interesting happens.")
        if (!PohBuild.requireBuildMode(player)) return
        val room = PlayerHouse.roomAt(player.tile) ?: return
        val slot = SLOTS.getValue(room)
        val piece = piece() ?: return player.message("Nothing interesting happens.")
        val chosen = PohBuild.choose(task, listOf(piece), "Rug space") ?: return
        if (!PohBuild.canBuild(task, chosen)) return
        when (rewards.beginRug(player, slot)) {
            NigelHouseRewards.Result.OK -> {}
            NigelHouseRewards.Result.OCCUPIED -> return
            else -> return player.message("Nothing interesting happens.")
        }
        player.lock = gg.rsmod.game.model.LockState.FULL
        player.animate(PohBuild.BUILD_ANIM)
        player.playSound(Sfx.POH_BUILD_WOOD)
        task.wait(5)
        val result = rewards.finishRug(player)
        player.unlock()
        if (result == NigelHouseRewards.Result.OK) PlayerHouse.rebuild(player)
    }

    /** Remove on a Deadman rug piece (building mode, confirmed): the Armageddon rug comes back, the opulent rug returns. */
    suspend fun remove(task: QueueTask) {
        val player = task.player
        val rewards = rewards ?: return
        if (!PohBuild.requireBuildMode(player)) return
        val room = PlayerHouse.roomAt(player.tile) ?: return
        if (!PohBuild.confirmRemove(task)) return
        when (rewards.remove(player, SLOTS.getValue(room))) {
            NigelHouseRewards.Result.OK -> {
                player.lock = gg.rsmod.game.model.LockState.FULL
                player.animate(PohBuild.REMOVE_ANIM)
                task.wait(2)
                player.unlock()
                PlayerHouse.rebuild(player)
            }
            NigelHouseRewards.Result.NO_SPACE -> player.message("You don't have enough inventory space.")
            else -> {}
        }
    }
}
