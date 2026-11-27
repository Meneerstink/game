package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*

/**
 * CONSTRUCTION-99 part 4: the workshop and every other built piece that has an option of its own.
 *
 * Workbench (2009scape `WorkbenchListeners`): "Work-at" makes flatpacks - the piece's materials go, its Construction experience is
 * given there and the flatpack item comes out; a bench makes pieces up to level 20 (wooden), 40 (oak), 60 (steel framed), 80 (with
 * vice) and 99 (with lathe). The categories are 2009scape's, without the costume room's storage (the owner took that storage out
 * on 2026-10-10). A flatpack is built on its hotspot with only a hammer ([PohConstruct.placePiece]). "Upgrade" builds the next
 * bench in its place. The clockmaker's bench makes clockwork (OSRS Wiki "Clockwork": a steel bar, Crafting 8, 15 experience).
 *
 * The rest are the pieces OSRS gives a look-only option (clocks, telescopes, charts, mounted heads, instruments) or that need other
 * players (games room, combat ring, the throne room lever): they answer with a line instead of "Nothing interesting happens".
 */
object PohWorkshop {
    private val WORKBENCHES = listOf(13704, 13705, 13706, 13707, 13708)
    private val CRAFTING_TABLES = setOf(13709, 13710, 13711, 13712)
    private const val CRAFT_ANIM = 4109
    private const val CLOCKWORK_ANIM = 3243

    /** 2009scape `WorkbenchListeners.decoCategories`, without the costume room's storage. */
    private val CATEGORIES =
        listOf(
            "Chairs" to "CHAIRS_1",
            "Bookcases" to "BOOKCASE",
            "Barrels" to "BARRELS",
            "Kitchen tables" to "KITCHEN_TABLE",
            "Dining tables" to "DINING_TABLE",
            "Dining benches" to "DINING_BENCH_1",
            "Beds" to "BED",
            "Dressers" to "DRESSER",
            "Drawers and wardrobes" to "DRAWERS",
            "Clocks" to "CLOCK",
        )

    private val RINGS = (13126..13145).toSet()
    private val CHESTS = setOf(13283, 13285, 13287, 13289, 13291, 13385, 13387, 13389)
    private val GAMES = setOf(13379, 13390, 13392, 13393, 13394, 13395, 13396, 13397, 13398, 13400, 13402, 13404)
    private val HEADS = (13481..13487).toSet()
    private val LOOK = (13155..13171).toSet() + (13200..13207).toSet() + (13214..13216).toSet() + (13656..13664).toSet() +
        setOf(13313, 13316, 13319, 13322, 13325, 13480, 13672, 13673, 13674, 13716, 13717, 13718, 13639)

    fun handles(id: Int): Boolean = id in WORKBENCHES || id in CRAFTING_TABLES || id in RINGS || id in CHESTS || id in GAMES || id in HEADS || id in LOOK

    fun handle(
        player: Player,
        obj: GameObject,
        option: String,
    ): Boolean {
        val id = obj.id
        when {
            option == "upgrade" -> player.queue { PohConstruct.upgradePiece(this, obj) }
            option == "work-at" && id in WORKBENCHES -> player.queue { workAt(this, (WORKBENCHES.indexOf(id) + 1) * 20) }
            option == "craft" && id in CRAFTING_TABLES -> player.queue { clockwork(this) }
            option == "direct-portal" && id == 13639 -> return PohFurnitureUse.handle(player, obj, option)
            option == "scry" -> player.message("You gaze into the pool, but there is nobody in your house to watch.")
            option == "climb-over" || option == "walk-through" -> across(player, obj)
            option == "stand-on" || option == "get-down" -> player.message("There is nobody here to duel with on the balance beam.")
            id in CHESTS -> player.message("The chest is empty.")
            id in GAMES -> player.message("There is nobody here to play with.")
            id in HEADS -> player.message("The mounted head stares back at you silently.")
            option == "read" -> player.message("It's ${java.time.LocalTime.now(java.time.ZoneOffset.UTC).let { "%d:%02d".format(it.hour, it.minute) }} (game time).")
            option == "light" -> player.message("The lights are already burning as bright as they can.")
            option == "play" -> {
                player.playSound(Sfx.POH_BUILD_WOOD)
                player.message("You play a short tune.")
            }
            option == "change-clothes" || option == "preen" -> player.message("You admire your reflection; for a new look, visit a hairdresser or Thessalia.")
            option == "observe" && id in TELESCOPE_SPREAD -> telescope(player, TELESCOPE_SPREAD.getValue(id))
            option == "study" -> player.message("You study the chart for a while.")
            option == "pull" || option == "challenge-mode" -> player.message("There are no guests in your house to drop into the oubliette.")
            option == "drink" -> player.message("The fountain is only for show.")
            option == "build-in" -> player.message("The cage is built with the oubliette's cage door space.")
            (option == "make-helmet" || option == "use") && id in PohHeraldry.STANDS -> player.queue { PohHeraldry.paint(this, id) }
            else -> return false
        }
        return true
    }

    /**
     * Nachtrun 27-11 (C1): the telescope, as before 2026-10-11 (2009scape TelescopePlugin): the sky view 782, the look-through
     * animation 3649, then when the next shooting star lands as a guess whose spread shrinks with the telescope (oak 24, teak 9,
     * mahogany 2 minutes either way) - this server's own star timer (ShootingStarHandler, 100 ticks a minute).
     */
    private val TELESCOPE_SPREAD = mapOf(13656 to 24, 13657 to 9, 13658 to 2)

    private fun telescope(
        player: Player,
        spread: Int,
    ) {
        // WEAK: a STANDARD task pauses while a main-screen interface is open (ROOT_CAUSES #92), and 782 is one.
        player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
            player.animate(3649)
            player.openInterface(782, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
            wait(2)
            val world = player.world
            val timer = gg.rsmod.plugins.content.activity.shooting_star.ShootingStarEventTimer
            val minutes = (if (world.timers.has(timer)) world.timers[timer] else 0) / 100
            val guess = (minutes + world.random(-spread..spread)).coerceAtLeast(1)
            messageBox("You see a shooting star! The star looks like it will land", "in about $guess minutes!")
        }
    }

    /** Workbench "Work-at": a category, then a piece up to the bench's level; materials -> flatpack, with the experience. */
    private suspend fun workAt(
        task: QueueTask,
        benchLevel: Int,
    ) {
        val player = task.player
        val categories = CATEGORIES.mapIndexed { i, c -> PohBuild.Piece(i, c.first, 0, 0.0, emptyList()) }
        val category = PohBuild.choose(task, categories, "Make a flatpack of") { it.name } ?: return
        val hotspot = CATEGORIES[category.obj].second.let { k -> PohFurniture.HOTSPOTS.firstOrNull { it.key == k } } ?: return
        val decos = hotspot.decos.filterNotNull().filter { it.flatpack > 0 && it.level <= benchLevel }
        if (decos.isEmpty()) {
            task.messageBox("This workbench can't make any of those.")
            return
        }
        val pieces = decos.map { PohBuild.Piece(it.objs.first(), it.name, it.level, it.xp, it.materials) }
        val choice = PohBuild.choose(task, pieces, "Make which flatpack?") ?: return
        val deco = decos[pieces.indexOf(choice)]
        if (!PohBuild.canBuild(task, choice)) return
        if (player.inventory.freeSlotCount == 0 && choice.materials.none { (item, qty) -> PohBuild.countOf(player, item) == qty }) {
            task.messageBox("You don't have enough inventory space.")
            return
        }
        PohBuild.takeMaterials(player, choice)
        player.animate(CRAFT_ANIM)
        player.playSound(Sfx.POH_BUILD_WOOD)
        task.wait(3)
        player.inventory.add(deco.flatpack, 1)
        player.addXp(Skills.CONSTRUCTION, deco.xp, checkBrawlingGloves = true)
        player.message("You make a flatpack of the ${deco.name.lowercase()}.")
    }

    /** Clockmaker's bench "Craft": clockwork from a steel bar (OSRS Wiki: Crafting 8, 15 experience). */
    private suspend fun clockwork(task: QueueTask) {
        val player = task.player
        if (player.skills.getCurrentLevel(Skills.CRAFTING) < 8) {
            task.messageBox("You need a Crafting level of 8 to make clockwork.")
            return
        }
        if (!player.inventory.contains(Items.STEEL_BAR)) {
            task.messageBox("You need a steel bar to make clockwork.")
            return
        }
        player.inventory.remove(Items.STEEL_BAR, 1)
        player.animate(CLOCKWORK_ANIM)
        task.wait(3)
        player.inventory.add(Items.CLOCKWORK, 1)
        player.addXp(Skills.CRAFTING, 15.0, checkBrawlingGloves = true)
        player.message("You make some clockwork.")
    }

    /** Over a combat ring's rope or through a magic barrier: to the tile on the far side of [obj] from the player. */
    private fun across(
        player: Player,
        obj: GameObject,
    ) {
        val dx = Integer.signum(obj.tile.x - player.tile.x)
        val dz = Integer.signum(obj.tile.z - player.tile.z)
        val dest = obj.tile.transform(dx, dz)
        if (player.world.collision.isClipped(dest)) {
            player.message("You can't get over there.")
            return
        }
        player.moveTo(dest)
    }
}
