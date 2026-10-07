package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool
import gg.rsmod.plugins.content.items.armor.BarrowsRepair

/**
 * The furniture the house gained on 2026-09-21 (owner: "we want nexus teleport and every teleport available in the
 * house put it in spirit tree obelisk fairy ring ... barrows armour stand"). The rooms themselves, and which object
 * stands where, are [PlayerHouse]'s; this file is only the behaviour.
 *
 * The spirit tree in the garden needs nothing here: `mechanics/travel/spirit_tree.plugin.kts` already binds loc 1293
 * and, when no spirit-tree station is within 30 tiles - which is always true inside an instanced house - offers all
 * five stations. Binding it a second time would throw out of `PluginRepository.init` and take the boot down.
 *
 * Every teleport goes through `canTeleport`, so house travel obeys the same wilderness/Deadman gates as the matching
 * item or spell.
 */

// ------------------------------------------------------------------ the nexus

/*
 * The teleport nexus. Revision 667 has no Portal Nexus loc - it is a much later OSRS object - so the house uses the
 * cache's own Scrying pool (13639), the one POH loc that exists to send a player somewhere of their choosing. Its
 * "Direct-portal" and "Scry" both open the OSRS Portal Nexus Teleport Menu ([PohTeleportUi.openNexus], owner bug
 * 2026-10-06 183223) with the full directory ([PohTeleports]), which holds every teleport in the game (owner 2026-09-19:
 * "all the teleports in the game must be in the POH").
 */
listOf("direct-portal", "scry").forEach { option ->
    on_obj_option(obj = PlayerHouse.NEXUS, option = option) {
        PohTeleportUi.openNexus(player)
    }
}

(0 until PohTeleportInterfaceImportTool.Nexus.ROWS).forEach { r ->
    on_button(interfaceId = PohTeleportUi.NEXUS, component = PohTeleportInterfaceImportTool.Nexus.row(r)) {
        val leaf = PohTeleportUi.NEXUS_ROWS.getOrNull(r) ?: return@on_button
        PohTeleportUi.go(player, PohTeleportUi.NEXUS, leaf)
    }
}

listOf(PohTeleportUi.NEXUS, PohTeleportUi.JEWELLERY_BOX).forEach { screen ->
    on_button(interfaceId = screen, component = PohTeleportUi.CLOSE) {
        player.closeInterface(screen)
    }
}

// ------------------------------------------------------------------ the garden

// Fairy ring: the world's own dial 734 + travel log 735 (mechanics/travel/fairy_rings.plugin.kts, FairyRings.Source.HOUSE) - owner
// 2026-10-02: house options are interfaces, not chat menus. Binding it here would pre-empt that fallback.

/*
 * The small obelisk in the garden needs nothing here. `skills/summoning/summoning_obelisks.plugin.kts` already binds
 * EVERY loc in the cache that carries a "Renew-points" option - all 39 of them, 5787 among them - and renews the
 * player's Summoning points with the proper obelisk graphic, animation and sound. Binding it a second time threw
 * "Object is already bound to a plugin: 5787 [opt=1]" out of `PluginRepository.init` and stopped the boot
 * (2026-09-21 15:42). Renewing Summoning points is also what a house obelisk does in RuneScape, so the garden
 * obelisk is complete as it stands; the house's travel lives on the nexus, the spirit tree and the fairy ring.
 */

// ------------------------------------------------------------------ the chapel

// Armour repair stand: the Barrows repair dialogue, at the stand's Smithing discount.
on_obj_option(obj = PlayerHouse.ARMOUR_STAND, option = "repair") {
    player.queue {
        BarrowsRepair.repairAtStand(this)
    }
}

// Jewellery box: the OSRS jewellery box screen (OSRS 590) with the six jewellery items' destinations.
on_obj_option(obj = PlayerHouse.JEWELLERY_BOX, option = "teleport") {
    PohTeleportUi.openJewelleryBox(player)
}

PohTeleportInterfaceImportTool.BOXES.forEachIndexed { i, box ->
    box.destinations.indices.forEach { d ->
        on_button(interfaceId = PohTeleportUi.JEWELLERY_BOX, component = PohTeleportInterfaceImportTool.Box.button(i, d)) {
            val leaf = PohTeleportUi.jewelleryLeaf(PohTeleportInterfaceImportTool.Box.button(i, d)) ?: return@on_button
            PohTeleportUi.go(player, PohTeleportUi.JEWELLERY_BOX, leaf)
        }
    }
}

// ------------------------------------------------------------------ the costume room

/*
 * Every piece of costume-room furniture opens its own storage on the real interface 467 ([PohStorageUi]; owner 2026-10-02: house options
 * are interfaces, not chat menus). The cape rack's cache option is "Search" and the rest carry "Open"; both are bound so the menu entry a
 * player sees is the one the loc actually advertises.
 */
PohStorage.Family.values().forEach { family ->
    listOf("open", "search").forEach { option ->
        if (if_obj_has_option(obj = family.furniture, option = option)) {
            on_obj_option(obj = family.furniture, option = option) {
                PohStorageUi.open(player, family)
            }
        }
    }
}

(0 until PohStorageUi.ROWS).forEach { row ->
    on_button(interfaceId = PohStorageUi.INTERFACE, component = PohStorageUi.buttonComponent(row)) {
        PohStorageUi.select(player, row)
    }
}

// ------------------------------------------------------------------ the study

/*
 * The telescope (owner 2026-10-02: interfaces, not chat lines) - 2009scape TelescopePlugin: the sky view 782 (this cache: a full-screen
 * layer and two models), the look-through animation 3649, then when the next shooting star lands, as a guess whose spread shrinks with
 * the telescope (oak 24, teak 9, mahogany 2 minutes either way). The next landing is this server's own star timer
 * (ShootingStarHandler, 100 ticks a minute).
 */
val TELESCOPE_SPREAD = mapOf(Objs.TELESCOPE_13656 to 24, Objs.TELESCOPE_13657 to 9, Objs.TELESCOPE_13658 to 2)

TELESCOPE_SPREAD.forEach { (telescope, spread) ->
    on_obj_option(obj = telescope, option = "observe") {
        // WEAK: a STANDARD task pauses while a main-screen interface is open (ROOT_CAUSES #92), and 782 is one.
        player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
            player.animate(3649)
            player.openInterface(782, gg.rsmod.plugins.api.InterfaceDestination.MAIN_SCREEN)
            wait(2)
            val timer = gg.rsmod.plugins.content.activity.shooting_star.ShootingStarEventTimer
            val minutes = (if (world.timers.has(timer)) world.timers[timer] else 0) / 100
            val guess = (minutes + world.random(-spread..spread)).coerceAtLeast(1)
            messageBox("You see a shooting star! The star looks like it will land", "in about $guess minutes!")
        }
    }
}

// The bookcase is flavour.

on_obj_option(obj = Objs.BOOKCASE_13599, option = "search") {
    player.message("You search the bookcase, but find nothing you have not already read.")
}

/*
 * The lectern (13648) makes magic tablets on the rev-667 tablet interface 400 - its buttons 2..16 and Make/5/10/X/All ops read out
 * of this cache match the 2009scape LecternPlugin table one for one, so levels, experience and materials are that donor's (soft clay
 * plus the spell's runes; staves, combination runes and the rune pouch pay through MagicSpells like a cast). A house here is fully
 * furnished, so its lectern offers every tablet (varps 261/262 = the top eagle and demon tiers, as the donor sets per lectern).
 * The donor's animations (1894 opening, 782 per tablet) are kept; its own note says the per-tablet one is a stand-in (ADAPTED).
 * Owner 2026-09-24: "poh alle objecten werken niet" - Study had no route.
 */
data class Tablet(val level: Int, val xp: Double, val product: Int, val materials: List<Item>)

val LECTERN_INTERFACE = 400
val TABLETS: Map<Int, Tablet> =
    mapOf(
        2 to Tablet(51, 61.0, Items.ARDOUGNE_TELEPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE, 2), Item(Items.WATER_RUNE, 2))),
        3 to Tablet(15, 25.0, Items.BONES_TO_BANANAS, listOf(Item(Items.SOFT_CLAY), Item(Items.NATURE_RUNE), Item(Items.EARTH_RUNE, 2), Item(Items.WATER_RUNE, 2))),
        4 to Tablet(60, 35.5, Items.BONES_TO_PEACHES_8015, listOf(Item(Items.SOFT_CLAY), Item(Items.NATURE_RUNE, 2), Item(Items.EARTH_RUNE, 4), Item(Items.WATER_RUNE, 4))),
        5 to Tablet(45, 55.5, Items.CAMELOT_TELEPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE), Item(Items.AIR_RUNE, 5))),
        6 to Tablet(57, 67.0, Items.ENCHANT_DIAMOND, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.EARTH_RUNE, 10))),
        7 to Tablet(68, 78.0, Items.ENCHANT_DRAGONSTN, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.EARTH_RUNE, 15), Item(Items.WATER_RUNE, 15))),
        8 to Tablet(27, 37.0, Items.ENCHANT_EMERALD, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.AIR_RUNE, 3))),
        9 to Tablet(87, 97.0, Items.ENCHANT_ONYX, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.EARTH_RUNE, 20), Item(Items.FIRE_RUNE, 20))),
        10 to Tablet(49, 59.0, Items.ENCHANT_RUBY, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.FIRE_RUNE, 5))),
        11 to Tablet(7, 17.5, Items.ENCHANT_SAPPHIRE, listOf(Item(Items.SOFT_CLAY), Item(Items.COSMIC_RUNE), Item(Items.WATER_RUNE))),
        12 to Tablet(37, 48.0, Items.FALADOR_TELEPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE), Item(Items.WATER_RUNE), Item(Items.AIR_RUNE, 3))),
        13 to Tablet(31, 41.0, Items.LUMBRIDGE_TELEPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE), Item(Items.EARTH_RUNE), Item(Items.AIR_RUNE, 3))),
        14 to Tablet(40, 30.0, Items.TELEPORT_TO_HOUSE, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE), Item(Items.EARTH_RUNE), Item(Items.AIR_RUNE))),
        15 to Tablet(25, 35.0, Items.VARROCK_TELEPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE), Item(Items.FIRE_RUNE), Item(Items.AIR_RUNE, 3))),
        16 to Tablet(58, 68.0, Items.WATCHTOWER_TPORT, listOf(Item(Items.SOFT_CLAY), Item(Items.LAW_RUNE, 2), Item(Items.EARTH_RUNE, 2))),
    )

on_obj_option(obj = Objs.LECTERN_13648, option = "study") {
    player.queue {
        player.animate(1894)
        wait(1)
        player.setVarp(261, 3)
        player.setVarp(262, 3)
        player.openInterface(LECTERN_INTERFACE, InterfaceDestination.MAIN_SCREEN)
    }
}

TABLETS.forEach { (component, tablet) ->
    on_button(interfaceId = LECTERN_INTERFACE, component = component) {
        val opcode = player.getInteractingOpcode()
        // ROOT_CAUSES 92 (2026-09-30): queued with the lectern open - as a STANDARD task it paused on that open menu, so no option
        // (1 / 5 / 10 / X / All) made a tablet until the player closed the lectern by hand.
        player.queue(gg.rsmod.game.model.queue.TaskPriority.WEAK) {
            val amount =
                when (opcode) {
                    61 -> 1
                    64 -> 5
                    4 -> 10
                    52 -> inputInt("Enter amount:")
                    else -> Int.MAX_VALUE
                }
            player.closeInterface(LECTERN_INTERFACE)
            var made = 0
            while (made < amount && gg.rsmod.plugins.content.magic.MagicSpells.canCast(player, tablet.level, tablet.materials)) {
                player.animate(782)
                wait(2)
                if (!gg.rsmod.plugins.content.magic.MagicSpells.canCast(player, tablet.level, tablet.materials)) break
                gg.rsmod.plugins.content.magic.MagicSpells.removeRunes(player, tablet.materials, spellId = -1)
                player.inventory.add(tablet.product, 1)
                player.addXp(Skills.MAGIC, tablet.xp)
                made++
                wait(4)
            }
        }
    }
}

on_button(interfaceId = LECTERN_INTERFACE, component = 17) {
    player.closeInterface(LECTERN_INTERFACE)
}

/*
 * The infernal chart's "Study": the rev-667 cache holds no chart interface (no interface text "Infernal" or "Alchemical"), so it
 * answers with a short description instead of "Nothing interesting happens" (ADAPTED, SOURCE_BLOCKED for the chart picture).
 */
on_obj_option(obj = Objs.INFERNAL_CHART_13664, option = "study") {
    player.message("You study the chart. It maps the infernal planes and the demons said to rule them.")
}
