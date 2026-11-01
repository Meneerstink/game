package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.tools.importer.PohTeleportInterfaceImportTool
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.content.items.armor.BarrowsRepair
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.mechanics.travel.FairyRings
import gg.rsmod.plugins.content.mechanics.travel.SpiritTrees

/**
 * The house's furniture behaviour. The rooms themselves, and which object stands where, are [PlayerHouse]'s.
 *
 * Owner 2026-10-10: "alleen dingen die teleporteren en functies hebben voor pvp pvm" - the costume room's storage and the
 * study are gone; the pieces here are the OSRS Portal Nexus, Spiritual Fairy Tree, Altar of the Occult, Ornate Jewellery
 * Box and ornate combat dummy, plus the armour repair stand.
 *
 * Every teleport goes through `canTeleport`, so house travel obeys the same wilderness/Deadman gates as the matching
 * item or spell.
 */

// ------------------------------------------------------------------ the portal chamber

/*
 * The OSRS Portal Nexus (crystalline, OSRS 33410). Its "Teleport" and "Teleport Menu" both open the OSRS Portal Nexus
 * Teleport Menu ([PohTeleportUi.openNexus], owner bug 2026-10-06 183223) with the full directory ([PohTeleports]), which
 * holds every teleport in the game (owner 2026-09-19: "all the teleports in the game must be in the POH").
 */
listOf("teleport", "teleport menu").forEach { option ->
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

/*
 * The Spiritual Fairy Tree (OSRS 29229): "Tree" opens the spirit tree list (no station is near an instanced house, so every
 * one is offered), "Ring-configure" the world's fairy ring dial and travel log, "Ring-Zanaris" goes straight to Zanaris and
 * "Ring-last-destination" repeats the last code ([FairyRings.LAST_CODE]) - with no journey made yet it opens the dial.
 */
on_obj_option(obj = PlayerHouse.SPIRITUAL_FAIRY_TREE, option = "tree") {
    SpiritTrees.open(player)
}

on_obj_option(obj = PlayerHouse.SPIRITUAL_FAIRY_TREE, option = "ring-configure") {
    FairyRings.open(player, FairyRings.Source.HOUSE)
}

on_obj_option(obj = PlayerHouse.SPIRITUAL_FAIRY_TREE, option = "ring-zanaris") {
    player.canTeleport(FairyRings.Source.HOUSE.teleport) {
        player.teleport(FairyRings.ZANARIS, FairyRings.Source.HOUSE.teleport)
    }
}

on_obj_option(obj = PlayerHouse.SPIRITUAL_FAIRY_TREE, option = "ring-last-destination") {
    val code = player.attr[FairyRings.LAST_CODE]
    if (code == null) {
        FairyRings.open(player, FairyRings.Source.HOUSE)
    } else {
        FairyRings.travel(player, code, FairyRings.Source.HOUSE)
    }
}

/*
 * The garden's wilderness obelisk needs nothing here: `areas/wilderness/wilderness_obelisk.plugin.kts` owns "Activate" and
 * recognises a house obelisk.
 */

// ------------------------------------------------------------------ the chapel

// Armour repair stand: the Barrows repair dialogue, at the stand's Smithing discount.
on_obj_option(obj = PlayerHouse.ARMOUR_STAND, option = "repair") {
    player.queue {
        BarrowsRepair.repairAtStand(this)
    }
}

// ------------------------------------------------------------------ the PvP/PvM room

// N506: cache 62926 currently has only Teleport Menu; Claude adds Last-destination separately.
if (if_obj_has_option(obj = PlayerHouse.JEWELLERY_BOX, option = JewelleryBoxRecent.OPTION)) {
    on_obj_option(obj = PlayerHouse.JEWELLERY_BOX, option = JewelleryBoxRecent.OPTION) {
        JewelleryBoxRecent.repeat(player)
    }
}

// Ornate Jewellery Box (OSRS 37520): the OSRS jewellery box screen (OSRS 590) with the six jewellery items' destinations.
on_obj_option(obj = PlayerHouse.JEWELLERY_BOX, option = "teleport menu") {
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

/*
 * Altar of the Occult (OSRS multiloc 29150): the client shows the variant for the player's spellbook, whose menu holds
 * "Venerate" and the two books the player is not on. A book's own option switches straight to it; "Venerate" asks which.
 * Every switch goes through [Spellbooks.select], so the Desert Treasure / Lunar Diplomacy and Magic level gates of every
 * other altar apply here too. Arceuus, the fourth OSRS book, does not exist in this revision and is not offered.
 */
val OCCULT_BOOKS = mapOf("standard" to Spellbook.STANDARD, "ancient" to Spellbook.ANCIENT, "lunar" to Spellbook.LUNAR)

PlayerHouse.OCCULT_ALTAR_VARIANTS.forEach { altar ->
    OCCULT_BOOKS.forEach { (option, book) ->
        if (if_obj_has_option(obj = altar, option = option)) {
            on_obj_option(obj = altar, option = option) {
                Spellbooks.select(player, book)
            }
        }
    }
    on_obj_option(obj = altar, option = "venerate") {
        val others = Spellbook.values.filter { it != player.getSpellbook() }
        player.queue {
            val names = others.map { it.name.lowercase().replaceFirstChar(Char::uppercase) }
            val choice = options(*(names + "Cancel").toTypedArray(), title = "Select a spellbook")
            others.getOrNull(choice - 1)?.let { Spellbooks.select(player, it) }
        }
    }
}

/*
 * The ornate combat dummy's "Swap" (OSRS Wiki "Ornate undead combat dummy"): a max house has every variant unlocked, so the
 * swap only asks which one. The dummy is replaced on its own tile and the choice is kept for the next visit.
 */
PlayerHouse.Dummy.values().forEach { variant ->
    on_npc_option(npc = variant.npc, option = "swap") {
        val npc = player.getInteractingNpc()
        player.queue {
            val all = PlayerHouse.Dummy.values().filter { it != variant }
            val first = all.take(4)
            val pick =
                when (val choice = options(*(first.map { it.label } + "More...").toTypedArray(), title = "Swap the combat dummy")) {
                    in 1..first.size -> first[choice - 1]
                    else -> {
                        val rest = all.drop(4)
                        rest.getOrNull(options(*(rest.map { it.label } + "Cancel").toTypedArray(), title = "Swap the combat dummy") - 1)
                    }
                } ?: return@queue
            if (!npc.isSpawned() || !PlayerHouse.isCombatDummy(npc)) return@queue
            val tile = npc.tile
            world.remove(npc)
            PlayerHouse.spawnDummy(world, pick, tile)
            player.attr[PlayerHouse.DUMMY_VARIANT_ATTR] = pick.ordinal
            player.message("You swap the combat dummy to the ${pick.label.lowercase()} variant.")
        }
    }
}

// ------------------------------------------------------------------ the removed costume room

// Owner 2026-10-10 removed the costume room's storage: whatever a player had stored goes to the bank on login.
on_login {
    PohStorage.returnToBank(player)
}
