package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.areas.poh.PlayerHouse
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.prepareForTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.SkillcapePerks.DailyUse

/**
 * The OSRS max cape menus (owner 2026-09-24: "zorg dat elke Maxcape goed functioneert en elke optie kan die hij moet ... precies t
 * zelfde als de osrs max cape"). OSRS Wiki "Max cape": inventory "Wear, Teleports, Spellbook, Features, Drop"; worn "Home, Crafting
 * Guild, Guild Teleports, Skilling Areas, POH Portals, Spellbook, Features" (the cache menu is set by MaxCapeMenuTool). The perks
 * behind them are the skillcapes' own (OSRS Wiki "Cape of Accomplishment"):
 * - teleports: Strength (Warriors' Guild), Fishing (Fishing Guild, Otto's Grotto), Crafting (Crafting Guild), Hunter ("Five teleports
 *   per day to the black or carnivorous chinchompa Hunter areas"), Construction (own house and every house portal) - [MaxCapeTeleports];
 * - Spellbook: Magic cape, "permanently change their spellbook up to five times per day";
 * - Features: Agility cape ("Once per day ... restore 100% run energy and provide the effect of a stamina potion for one minute"),
 *   Fletching cape ("searched for a mithril grapple and bronze crossbow up to three times each day"), Herblore cape ("Can be searched
 *   for a pestle and mortar"), and the owner's look chooser ([MaxCapeLooks]).
 * ADAPTED: the Features menu lists the operable perks (the wiki names the option but not its lines). The 667 inventory menu holds five
 * options with the wear option second, so the order is Teleports, Wear, Spellbook, Features, Drop. Defence cape's ring of life is not
 * offered: OSRS Wiki "Ring of life" - it does not work in Deadman Mode, which this server is.
 */

val CAPE = MaxCapes.MAX_CAPE

fun arrivalTile(tile: gg.rsmod.game.model.Tile) = CityGuards.nearestWalkable(world, tile, radius = 3) ?: tile

fun capeTeleport(
    player: Player,
    destination: MaxCapeTeleports.Destination,
) {
    if (destination.dailyHunterLimit && SkillcapePerks.remainingToday(player, DailyUse.HUNTER_TELEPORT) == 0) {
        player.message("You have used all ${MaxCapeTeleports.HUNTER_TELEPORTS_PER_DAY} of today's Hunter area teleports.")
        return
    }
    player.canTeleport(TeleportType.JEWELRY) {
        if (destination.dailyHunterLimit && !SkillcapePerks.takeDailyUse(player, DailyUse.HUNTER_TELEPORT)) return@canTeleport
        player.teleport(arrivalTile(destination.tile), TeleportType.JEWELRY)
    }
}

fun teleportHome(player: Player) {
    player.canTeleport(TeleportType.JEWELRY) {
        val type = TeleportType.JEWELRY
        player.lock = LockState.FULL_WITH_DAMAGE_IMMUNITY
        player.queue(TaskPriority.STRONG) {
            player.prepareForTeleport()
            type.startSound?.let { player.playSound(it) }
            player.animate(type.animation)
            type.graphic?.let { player.graphic(it) }
            wait(type.teleportDelay)
            player.animate(Anims.RESET)
            player.unlock()
            val arrival = PlayerHouse.build(player)
            if (arrival == null) player.message("Your house can't be reached right now. Try again in a moment.") else player.moveTo(arrival)
        }
    }
}

suspend fun QueueTask.chooseDestination(
    title: String,
    destinations: List<MaxCapeTeleports.Destination>,
): MaxCapeTeleports.Destination? {
    // 667 option menus hold five lines: four destinations + "More..." per page.
    var start = 0
    while (true) {
        val rest = destinations.drop(start)
        if (rest.size <= 5) return rest.getOrNull(options(*rest.map { it.name }.toTypedArray(), title = title) - 1)
        val picked = options(*(rest.take(4).map { it.name } + "More...").toTypedArray(), title = title)
        if (picked == 5) start += 4 else return rest.getOrNull(picked - 1)
    }
}

fun openTeleports(player: Player) {
    player.queue {
        when (options("Guild Teleports", "Skilling Areas", "POH Portals", title = "Teleports")) {
            1 -> chooseDestination("Guild Teleports", MaxCapeTeleports.GUILDS)?.let { capeTeleport(player, it) }
            2 -> chooseDestination("Skilling Areas", MaxCapeTeleports.SKILLING_AREAS)?.let { capeTeleport(player, it) }
            3 -> openPohPortals(player, this)
        }
    }
}

suspend fun openPohPortals(
    player: Player,
    task: QueueTask,
) {
    val home = MaxCapeTeleports.Destination("Home", gg.rsmod.game.model.Tile(0, 0, 0))
    val picked = task.chooseDestination("POH Portals", listOf(home) + MaxCapeTeleports.POH_PORTALS) ?: return
    if (picked === home) teleportHome(player) else capeTeleport(player, picked)
}

fun openSpellbook(player: Player) {
    val left = SkillcapePerks.remainingToday(player, DailyUse.SPELLBOOK)
    if (left == 0) {
        player.message("You have changed your spellbook ${MaxCapeTeleports.SPELLBOOK_SWAPS_PER_DAY} times today already.")
        return
    }
    player.queue {
        val books = Spellbook.values.filter { it != player.getSpellbook() }
        val names = books.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        val picked = books.getOrNull(options(*(names + "Cancel").toTypedArray(), title = "Change spellbook ($left left today)") - 1) ?: return@queue
        if (!SkillcapePerks.takeDailyUse(player, DailyUse.SPELLBOOK)) return@queue
        player.switchSpellbook(picked)
        player.message("Your spellbook is now the ${names[books.indexOf(picked)]} spellbook.")
    }
}

fun staminaBoost(player: Player) {
    if (!SkillcapePerks.takeDailyUse(player, DailyUse.STAMINA)) {
        player.message("You have already used the cape's stamina boost today.")
        return
    }
    gg.rsmod.plugins.content.items.potion.StaminaPotions.drink(player, 100.0, 100)
    player.message("You feel refreshed and your run energy is restored.")
}

fun searchCape(player: Player) {
    player.queue {
        val choice = options("Mithril grapple", "Bronze crossbow", "Pestle and mortar", "Cancel", title = "Search the cape")
        when (choice) {
            1, 2 -> {
                val item = if (choice == 1) Items.MITH_GRAPPLE else Items.BRONZE_CROSSBOW
                if (!SkillcapePerks.takeDailyUse(player, DailyUse.FLETCHING_SEARCH)) {
                    player.message("You have searched the cape ${MaxCapeTeleports.SEARCHES_PER_DAY} times today already.")
                    return@queue
                }
                if (player.inventory.add(item).hasFailed()) player.message("You don't have enough inventory space.")
            }
            3 -> if (player.inventory.add(Items.PESTLE_AND_MORTAR).hasFailed()) player.message("You don't have enough inventory space.")
        }
    }
}

fun openFeatures(player: Player) {
    player.queue {
        val stamina = "Stamina boost (${SkillcapePerks.remainingToday(player, DailyUse.STAMINA)} left today)"
        when (options(stamina, "Search the cape", "Customise look", "Cancel", title = "Features")) {
            1 -> staminaBoost(player)
            2 -> searchCape(player)
            3 -> MaxCapeLooks.customise(player)
        }
    }
}

on_item_option(item = CAPE, option = "Teleports") { openTeleports(player) }
on_item_option(item = CAPE, option = "Spellbook") { openSpellbook(player) }
on_item_option(item = CAPE, option = "Features") { openFeatures(player) }

on_equipment_option(item = CAPE, option = "Home") { teleportHome(player) }
on_equipment_option(item = CAPE, option = "Crafting Guild") { capeTeleport(player, MaxCapeTeleports.CRAFTING_GUILD) }
on_equipment_option(item = CAPE, option = "Guild Teleports") {
    player.queue { chooseDestination("Guild Teleports", MaxCapeTeleports.GUILDS)?.let { capeTeleport(player, it) } }
}
on_equipment_option(item = CAPE, option = "Skilling Areas") {
    player.queue { chooseDestination("Skilling Areas", MaxCapeTeleports.SKILLING_AREAS)?.let { capeTeleport(player, it) } }
}
on_equipment_option(item = CAPE, option = "POH Portals") { player.queue { openPohPortals(player, this) } }
on_equipment_option(item = CAPE, option = "Spellbook") { openSpellbook(player) }
on_equipment_option(item = CAPE, option = "Features") { openFeatures(player) }
