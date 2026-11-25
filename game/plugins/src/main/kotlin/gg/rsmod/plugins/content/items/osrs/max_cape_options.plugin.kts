package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.areas.poh.PlayerHouse
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.prepareForTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.SkillcapeFeatures
import gg.rsmod.plugins.content.skills.SkillcapePerks.DailyUse

/**
 * The OSRS max cape menus (owner 2026-09-24: "zorg dat elke Maxcape goed functioneert en elke optie kan die hij moet ... precies t
 * zelfde als de osrs max cape"). OSRS Wiki "Max cape": inventory "Wear, Teleports, Spellbook, Features, Drop"; worn "Home, Crafting
 * Guild, Guild Teleports, Skilling Areas, POH Portals, Spellbook, Features" (the cache menu is set by MaxCapeMenuTool; worn it is
 * Home, Teleports, Spellbook, Features - the client shows four worn options). The perks
 * behind them are the skillcapes' own (OSRS Wiki "Cape of Accomplishment"):
 * - teleports: Strength (Warriors' Guild), Fishing (Fishing Guild, Otto's Grotto), Crafting (Crafting Guild), Hunter ("Five teleports
 *   per day to the black or carnivorous chinchompa Hunter areas"), Construction (own house and every house portal) - [MaxCapeTeleports];
 * - "Spellbook": the Magic cape's five spellbook changes a day, in both menus ([gg.rsmod.plugins.content.skills.SkillcapeSpellbook],
 *   bound in skillcape_spellbook.plugin.kts). Left out from buglist E2 (owner 2026-10-03) until the owner asked for it on 2026-10-29;
 * - Features: Agility cape ("Once per day ... restore 100% run energy and provide the effect of a stamina potion for one minute"),
 *   Fletching cape ("searched for a mithril grapple and bronze crossbow up to three times each day"), Herblore cape ("Can be searched
 *   for a pestle and mortar"), and the owner's look chooser ([MaxCapeLooks]).
 * ADAPTED: the Features menu lists the operable perks (the wiki names the option but not its lines). The 667 inventory menu holds five
 * options with the wear option second, so the order is Teleports, Wear, Spellbook, Features, Drop. Defence cape's ring of life is not
 * offered: OSRS Wiki "Ring of life" - it does not work in Deadman Mode, which this server is.
 */

val CAPE = MaxCapes.MAX_CAPE

fun hasMaxCape(player: Player): Boolean =
    player.inventory.contains(CAPE) || player.getEquipment(EquipmentType.CAPE)?.id == CAPE

fun arrivalTile(tile: gg.rsmod.game.model.Tile) = CityGuards.nearestWalkable(world, tile, radius = 3) ?: tile

fun capeTeleport(
    player: Player,
    destination: MaxCapeTeleports.Destination,
) {
    if (!hasMaxCape(player)) return
    if (destination.dailyHunterLimit && SkillcapePerks.remainingToday(player, DailyUse.HUNTER_TELEPORT) == 0) {
        player.message("You have used all ${MaxCapeTeleports.HUNTER_TELEPORTS_PER_DAY} of today's Hunter area teleports.")
        return
    }
    player.canTeleport(TeleportType.JEWELRY) {
        if (!hasMaxCape(player)) return@canTeleport
        if (destination.dailyHunterLimit && !SkillcapePerks.takeDailyUse(player, DailyUse.HUNTER_TELEPORT)) return@canTeleport
        player.teleport(arrivalTile(destination.tile), TeleportType.JEWELRY)
    }
}

fun teleportHome(player: Player) {
    if (!hasMaxCape(player)) return
    player.canTeleport(TeleportType.JEWELRY) {
        if (!hasMaxCape(player)) return@canTeleport
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
            PlayerHouse.enter(player)
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

fun staminaBoost(player: Player) {
    if (hasMaxCape(player) && SkillcapeFeatures.stamina(player)) {
        player.message("You feel refreshed and your run energy is restored.")
    }
}

fun searchCape(player: Player) {
    player.queue {
        val choice = options("Mithril grapple and bronze crossbow", "Pestle and mortar", "Cancel", title = "Search the cape")
        if (!hasMaxCape(player)) return@queue
        when (choice) {
            1 -> SkillcapeFeatures.searchFletching(player)
            2 -> SkillcapeFeatures.searchHerblore(player)
        }
    }
}

fun openFeatures(player: Player) {
    player.queue {
        val stamina = "Stamina boost (${SkillcapePerks.remainingToday(player, DailyUse.STAMINA)} left today)"
        val choice = options(stamina, "Search the cape", "Commune", "Customise look", "Cancel", title = "Features")
        if (!hasMaxCape(player)) return@queue
        when (choice) {
            1 -> staminaBoost(player)
            2 -> searchCape(player)
            3 -> AvasAssembler.commune(player)
            4 -> MaxCapeLooks.customise(player)
        }
    }
}

on_item_option(item = CAPE, option = "Teleports") { openTeleports(player) }
on_item_option(item = CAPE, option = "Features") { openFeatures(player) }

on_equipment_option(item = CAPE, option = "Home") { teleportHome(player) }
// Worn: four options fit (MaxCapeMenuTool.WORN), so the guild, skilling and portal teleports sit behind "Teleports" as in the inventory.
on_equipment_option(item = CAPE, option = "Teleports") { openTeleports(player) }
on_equipment_option(item = CAPE, option = "Features") { openFeatures(player) }
