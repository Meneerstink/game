package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*

/**
 * Building mode (owner 2026-10-10 night run): the house stays fully furnished; building mode makes exactly two things buildable -
 * the dining room's table space (Construction training: the mahogany table) and the Superior Garden's theme space (the OSRS
 * Otherworldly theme) - and lets every room be rotated from a doorway's door hotspot.
 *
 * Mechanism ported from Darkan (`House.openBuildInterface` / `build` / `removeBuild`, rev 727) and 2009scape
 * (`ConstructionInterface` / `BuildingUtils` / `RemovalDialogue` / `BuildRoomDialogue`): a hotspot's Build lists what fits it with
 * level and materials, the level, materials and tools are checked, the materials are taken, the build animation (3683; plants
 * 2293) plays and the piece replaces the hotspot with its experience; Remove (building mode only, "Really remove it?") plays 3685,
 * gives nothing back and puts the hotspot back.
 *
 * The furniture menu is a chat menu: the rev-667 cache's furniture creation interfaces (394/396) have no clickable item slots
 * (decoded with InterfaceHookProbeTool: the item layer 11 has no children and no ops in this revision; Darkan's 1306 is not in the
 * 667 cache), so each line names the piece with its level and the materials are listed when it is chosen.
 */
object PohBuild {
    /** One buildable piece: its loc, OSRS Wiki level / experience / materials (item -> amount; [ANY_NAILS] = nails of any metal). */
    data class Piece(
        val obj: Int,
        val name: String,
        val level: Int,
        val xp: Double,
        val materials: List<Pair<Int, Int>>,
        val plant: Boolean = false,
    )

    /** Nails of any metal (OSRS / 2009scape `NailType`): bronze, iron, steel, black, mithril, adamantite, rune. */
    const val ANY_NAILS = -1
    private val NAILS = intArrayOf(4819, 4820, 1539, 4821, 4822, 4823, 4824)

    /** OSRS Wiki "Table space" (dining room); locs 13293-13299 (Darkan `HouseObjects` WOOD_DINING .. OPULENT_TABLE). */
    val DINING_TABLES =
        listOf(
            Piece(13293, "Wood dining table", 10, 115.0, listOf(Items.PLANK to 4, ANY_NAILS to 4)),
            Piece(13294, "Oak dining table", 22, 240.0, listOf(Items.OAK_PLANK to 4)),
            Piece(13295, "Carved oak table", 31, 360.0, listOf(Items.OAK_PLANK to 6)),
            Piece(13296, "Teak table", 38, 360.0, listOf(Items.TEAK_PLANK to 4)),
            Piece(13297, "Carved teak table", 45, 600.0, listOf(Items.TEAK_PLANK to 6, Items.BOLT_OF_CLOTH to 4)),
            Piece(13298, "Mahogany table", 52, 840.0, listOf(Items.MAHOGANY_PLANK to 6)),
            Piece(13299, "Opulent table", 72, 3100.0, listOf(Items.MAHOGANY_PLANK to 6, Items.BOLT_OF_CLOTH to 4, Items.GOLD_LEAF to 4, Items.MARBLE_BLOCK to 2)),
        )

    /**
     * OSRS Wiki "Otherworldly theme": level 75, 316 experience, 8 supercompost, a blue dye, 4 mushrooms and magic secateurs (all
     * consumed - the wiki's total cost includes the secateurs), built with a watering can holding at least one dose. The OSRS
     * menu also offers the Zen (65) and Volcanic (85) themes; owner: only the Otherworldly theme is buildable.
     */
    val OTHERWORLDLY =
        Piece(
            PlayerHouse.OTHERWORLDLY_THEME,
            "Otherworldly theme",
            75,
            316.0,
            listOf(Items.SUPERCOMPOST to 8, Items.BLUE_DYE to 1, Items.BITTERCAP_MUSHROOM to 4, Items.MAGIC_SECATEURS to 1),
            plant = true,
        )

    /** Darkan `House.build`: furniture 3683, plants 2293; `removeBuild` 3685. */
    private const val BUILD_ANIM = 3683
    private const val REMOVE_ANIM = 3685

    private val WATERING_CANS = (Items.WATERING_CAN_1..Items.WATERING_CAN_8).toList()

    fun requireBuildMode(player: Player): Boolean {
        if (PlayerHouse.isBuildMode(player)) return true
        player.message("You can only do that in building mode.")
        return false
    }

    /** The table space's Build: the OSRS dining table list, five lines a page. */
    suspend fun buildTable(
        task: QueueTask,
        space: GameObject,
    ) {
        val player = task.player
        if (!requireBuildMode(player)) return
        val piece = choose(task, DINING_TABLES, "Table space") ?: return
        if (!canBuild(task, piece)) return
        build(player, piece) {
            player.attr[PlayerHouse.DINING_TABLE_ATTR] = piece.obj
            player.world.spawn(DynamicObject(piece.obj, space.type, space.rot, space.tile))
        }
    }

    /** The theme space's Build. */
    suspend fun buildTheme(task: QueueTask) {
        val player = task.player
        if (!requireBuildMode(player)) return
        val piece = choose(task, listOf(OTHERWORLDLY), "Theme space") ?: return
        if (!canBuild(task, piece)) return
        val garden = PlayerHouse.gardenFrame(player) ?: return
        build(player, piece) {
            player.attr[PlayerHouse.GARDEN_THEME_ATTR] = true
            PlayerHouse.spawnTheme(player, garden)
        }
    }

    /** Remove on a built table: building mode only, confirmed, nothing comes back (OSRS / 2009scape `RemovalDialogue`). */
    suspend fun removeTable(
        task: QueueTask,
        table: GameObject,
    ) {
        val player = task.player
        if (!requireBuildMode(player) || !confirmRemove(task)) return
        remove(player) {
            player.attr.remove(PlayerHouse.DINING_TABLE_ATTR)
            player.world.spawn(DynamicObject(PlayerHouse.TABLE_SPACE, table.type, table.rot, table.tile))
        }
    }

    suspend fun removeTheme(task: QueueTask) {
        val player = task.player
        if (!requireBuildMode(player) || !confirmRemove(task)) return
        val garden = PlayerHouse.gardenFrame(player) ?: return
        remove(player) {
            player.attr.remove(PlayerHouse.GARDEN_THEME_ATTR)
            PlayerHouse.spawnTheme(player, garden)
        }
    }

    private suspend fun confirmRemove(task: QueueTask): Boolean = task.options("Yes", "No", title = "Really remove it?") == 1

    private suspend fun choose(
        task: QueueTask,
        pieces: List<Piece>,
        title: String,
    ): Piece? {
        var start = 0
        while (true) {
            val rest = pieces.drop(start)
            val page = if (rest.size <= 5) rest else rest.take(4)
            val labels = page.map { "${it.name} (level ${it.level})" } + if (rest.size > 5) listOf("More...") else emptyList()
            val picked = task.options(*labels.toTypedArray(), title = title)
            if (rest.size > 5 && picked == 5) {
                start += 4
                if (start >= pieces.size) start = 0
                continue
            }
            return page.getOrNull(picked - 1)
        }
    }

    private fun countOf(
        player: Player,
        item: Int,
    ): Int = if (item == ANY_NAILS) NAILS.sumOf { player.inventory.getItemCount(it) } else player.inventory.getItemCount(item)

    private fun nameOf(
        player: Player,
        item: Int,
    ): String = if (item == ANY_NAILS) "nails" else player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item).name

    /** Level, materials and tools, with 2009scape `ConstructionInterface`'s messages. */
    private suspend fun canBuild(
        task: QueueTask,
        piece: Piece,
    ): Boolean {
        val player = task.player
        if (!gg.rsmod.plugins.content.mechanics.assist.AssistSystem.meets(player, Skills.CONSTRUCTION, piece.level, boostable = true)) {
            task.messageBox("You need to have a Construction level of ${piece.level} to build that.")
            return false
        }
        val missing = piece.materials.filter { (item, qty) -> countOf(player, item) < qty }
        if (missing.isNotEmpty()) {
            task.messageBox(
                "You don't have the right materials. The ${piece.name} needs " +
                    piece.materials.joinToString(", ") { (item, qty) -> "$qty x ${nameOf(player, item)}" } + ".",
            )
            return false
        }
        if (piece.plant) {
            if (WATERING_CANS.none { player.inventory.contains(it) }) {
                task.messageBox("You need a watering can with some water in it to plant this.")
                return false
            }
        } else if (!player.inventory.contains(Items.HAMMER) || !player.inventory.contains(Items.SAW)) {
            task.messageBox("You will need a hammer and saw to build furniture.")
            return false
        }
        return true
    }

    private fun takeMaterials(
        player: Player,
        piece: Piece,
    ) {
        piece.materials.forEach { (item, qty) ->
            if (item == ANY_NAILS) {
                var left = qty
                for (nail in NAILS) {
                    if (left == 0) break
                    val take = minOf(left, player.inventory.getItemCount(nail))
                    if (take > 0) {
                        player.inventory.remove(nail, take)
                        left -= take
                    }
                }
            } else {
                player.inventory.remove(item, qty)
            }
        }
        if (piece.plant) {
            // One dose of the watering can (OSRS: planting in the house waters the plant).
            val can = WATERING_CANS.last { player.inventory.contains(it) }
            val slot = player.inventory.getItemIndex(can, skipAttrItems = false)
            if (slot >= 0) player.inventory[slot] = gg.rsmod.game.model.item.Item(if (can == Items.WATERING_CAN_1) Items.WATERING_CAN else can - 1)
        }
    }

    private fun build(
        player: Player,
        piece: Piece,
        place: () -> Unit,
    ) {
        takeMaterials(player, piece)
        player.lock = gg.rsmod.game.model.LockState.FULL
        player.animate(if (piece.plant) Anims.WATER_PATCH else BUILD_ANIM)
        player.playSound(if (piece.plant) Sfx.FARMING_WATERING else Sfx.POH_BUILD_WOOD)
        player.queue {
            wait(2)
            place()
            player.addXp(Skills.CONSTRUCTION, piece.xp, checkBrawlingGloves = true)
            if (piece.plant) player.addXp(Skills.FARMING, piece.xp, checkBrawlingGloves = true)
            player.unlock()
        }
    }

    private fun remove(
        player: Player,
        restore: () -> Unit,
    ) {
        player.lock = gg.rsmod.game.model.LockState.FULL
        player.animate(REMOVE_ANIM)
        player.queue {
            wait(2)
            restore()
            player.unlock()
        }
    }

    /**
     * Door hotspot Build in building mode: rotate the room on the other side of the doorway (from where the player stands) -
     * 2009scape `BuildRoomDialogue` "Rotate clockwise / Rotate anticlockwise", over the rotations that keep a doorway facing the
     * garden ([PlayerHouse.allowedRotations]); every turn reloads the house around the player, as building mode does.
     */
    suspend fun rotateRoom(
        task: QueueTask,
        hotspot: GameObject,
    ) {
        val player = task.player
        if (!requireBuildMode(player)) return
        val across =
            when (hotspot.rot) {
                0 -> hotspot.tile.transform(-1, 0)
                1 -> hotspot.tile.transform(0, 1)
                2 -> hotspot.tile.transform(1, 0)
                else -> hotspot.tile.transform(0, -1)
            }
        val mine = PlayerHouse.roomAt(player.tile)
        val sides = listOfNotNull(PlayerHouse.roomAt(hotspot.tile), PlayerHouse.roomAt(across)).distinct()
        val room = sides.firstOrNull { it != mine } ?: sides.firstOrNull() ?: return
        while (true) {
            val choice = task.options("Rotate clockwise", "Rotate anticlockwise", "Done", title = "Rotate the ${room.displayName}")
            if (choice != 1 && choice != 2) return
            val style = PlayerHouse.styleOf(player)
            val allowed = PlayerHouse.allowedRotations(player.world, style, room)
            val current = PlayerHouse.roomRotation(player, room)
            val step = if (choice == 1) 1 else 3
            var next = (current + step) and 3
            while (next != current && next !in allowed) next = (next + step) and 3
            if (next == current) {
                task.messageBox("The ${room.displayName} can only face this way: its doorway must open onto the garden.")
                return
            }
            PlayerHouse.setRoomRotation(player, room, next)
            player.playSound(Sfx.POH_BUILD_WOOD)
            if (!PlayerHouse.rebuild(player)) return
        }
    }

    /** Building mode on or off (House Options, the house portal): saved, shown on the House Options panel, and the house reloads. */
    fun setBuildMode(
        player: Player,
        on: Boolean,
    ) {
        val changed = PlayerHouse.isBuildMode(player) != on
        player.attr[PlayerHouse.BUILD_MODE_ATTR] = on
        player.setVarbit(PlayerHouse.BUILD_MODE_VARBIT, if (on) 1 else 0)
        if (changed && PlayerHouse.ownsHouseAt(player)) {
            PlayerHouse.rebuild(player)
        }
    }

}
