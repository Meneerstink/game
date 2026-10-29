package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.pvp.CityGuards
import gg.rsmod.plugins.content.skills.SkillcapePerks
import gg.rsmod.plugins.content.skills.SkillcapePerks.DailyUse

/**
 * K03 N410 part 5: the skillcapes' own OSRS teleports (OSRS Wiki "Cape of Accomplishment": Strength "Unlimited teleports to the
 * Warriors' Guild", Fishing "to the Fishing Guild and Otto's Grotto", Crafting "to the Crafting Guild", Hunter "Five teleports per
 * day to the black or carnivorous chinchompa hunter areas", Quest point "to the Legends' Guild gates"). The menus come from the
 * OSRS cache (SkillcapeMenuTool); the destinations and the Hunter day limit are the max cape's (MaxCapeTeleports).
 */

fun skillcapeTeleport(
    player: Player,
    destination: MaxCapeTeleports.Destination,
) {
    if (destination.dailyHunterLimit && SkillcapePerks.remainingToday(player, DailyUse.HUNTER_TELEPORT) == 0) {
        player.message("You have used all ${MaxCapeTeleports.HUNTER_TELEPORTS_PER_DAY} of today's Hunter area teleports.")
        return
    }
    player.canTeleport(TeleportType.JEWELRY) {
        if (destination.dailyHunterLimit && !SkillcapePerks.takeDailyUse(player, DailyUse.HUNTER_TELEPORT)) return@canTeleport
        val tile = CityGuards.nearestWalkable(world, destination.tile, radius = 3) ?: destination.tile
        player.teleport(tile, TeleportType.JEWELRY)
    }
}

listOf(Items.STRENGTH_CAPE, Items.STRENGTH_CAPE_T).forEach { cape ->
    on_item_option(item = cape, option = "Teleport") { skillcapeTeleport(player, MaxCapeTeleports.WARRIORS_GUILD) }
    on_equipment_option(item = cape, option = "Warriors' Guild") { skillcapeTeleport(player, MaxCapeTeleports.WARRIORS_GUILD) }
}

listOf(Items.FISHING_CAPE, Items.FISHING_CAPE_T).forEach { cape ->
    on_item_option(item = cape, option = "Fishing Guild") { skillcapeTeleport(player, MaxCapeTeleports.FISHING_GUILD) }
    on_item_option(item = cape, option = "Otto's Grotto") { skillcapeTeleport(player, MaxCapeTeleports.OTTOS_GROTTO) }
    on_equipment_option(item = cape, option = "Fishing Guild") { skillcapeTeleport(player, MaxCapeTeleports.FISHING_GUILD) }
    on_equipment_option(item = cape, option = "Otto's Grotto") { skillcapeTeleport(player, MaxCapeTeleports.OTTOS_GROTTO) }
}

listOf(Items.CRAFTING_CAPE, Items.CRAFTING_CAPE_T).forEach { cape ->
    on_item_option(item = cape, option = "Teleport") { skillcapeTeleport(player, MaxCapeTeleports.CRAFTING_GUILD) }
    on_equipment_option(item = cape, option = "Teleport") { skillcapeTeleport(player, MaxCapeTeleports.CRAFTING_GUILD) }
}

listOf(Items.HUNTER_CAPE, Items.HUNTER_CAPE_T).forEach { cape ->
    on_item_option(item = cape, option = "Teleport") {
        player.queue {
            val left = SkillcapePerks.remainingToday(player, DailyUse.HUNTER_TELEPORT)
            when (options("Carnivorous Chinchompas", "Black Chinchompas", title = "Teleports left today: $left")) {
                1 -> skillcapeTeleport(player, MaxCapeTeleports.FELDIP_HUNTER)
                2 -> skillcapeTeleport(player, MaxCapeTeleports.WILDERNESS_HUNTER)
            }
        }
    }
    on_equipment_option(item = cape, option = "Carnivorous Chinchompas") { skillcapeTeleport(player, MaxCapeTeleports.FELDIP_HUNTER) }
    on_equipment_option(item = cape, option = "Black Chinchompas") { skillcapeTeleport(player, MaxCapeTeleports.WILDERNESS_HUNTER) }
}

on_item_option(item = Items.QUEST_POINT_CAPE, option = "Teleport") { skillcapeTeleport(player, MaxCapeTeleports.LEGENDS_GUILD) }
on_equipment_option(item = Items.QUEST_POINT_CAPE, option = "Teleport") { skillcapeTeleport(player, MaxCapeTeleports.LEGENDS_GUILD) }
