package gg.rsmod.plugins.content.items.osrs

/*
 * Ring of shadows (owner 2026-09-19: the Desert Treasure II reward "we forgot to add"; import batch "owner0919",
 * tx-20260919-211216, upstream 28327 charged / 28329 uncharged).
 *
 * OSRS Wiki "Ring of shadows": the ring is charged with one blood, one soul, one death and one law rune per charge, holds
 * at most 1,000 charges, and uncharging refunds the runes. Its five teleports (Ancient Vault, Ghorrock Dungeon, The Scar,
 * Lassar Undercity, The Stranglewood) go to areas this rev-667 world does not have; owner decision 2026-09-19: keep the
 * charges, the bonuses and Check, and tell the player the realm cannot be reached instead of inventing a destination.
 */

val RING_OF_SHADOWS_CHARGES = AttributeKey<Int>(persistenceKey = "ring_of_shadows_charges")

val SHADOW_RUNES = intArrayOf(Items.BLOOD_RUNE, Items.SOUL_RUNE, Items.DEATH_RUNE, Items.LAW_RUNE)
val SHADOW_MAX_CHARGES = 1000

/** The wiki's five teleport destinations, in the order the worn options list them. */
val SHADOW_DESTINATIONS = arrayOf("Ancient Vault", "Ghorrock Dungeon", "The Scar", "Lassar Undercity", "The Stranglewood")

fun shadowCharges(player: Player): Int = player.attr[RING_OF_SHADOWS_CHARGES] ?: 0

fun setShadowCharges(
    player: Player,
    charges: Int,
) {
    player.attr[RING_OF_SHADOWS_CHARGES] = charges.coerceIn(0, SHADOW_MAX_CHARGES)
}

/** How many full rune sets the player can pay for right now. */
fun shadowSetsAvailable(player: Player): Int = SHADOW_RUNES.minOf { player.inventory.getItemCount(it) }

fun chargeShadowRing(player: Player) {
    val ring = if (player.inventory.contains(Items.RING_OF_SHADOWS)) Items.RING_OF_SHADOWS else Items.RING_OF_SHADOWS_UNCHARGED
    val room = SHADOW_MAX_CHARGES - shadowCharges(player)
    if (room <= 0) {
        player.message("Your ring of shadows is already fully charged.")
        return
    }
    val sets = minOf(shadowSetsAvailable(player), room)
    if (sets <= 0) {
        player.message("You need a blood rune, a soul rune, a death rune and a law rune for each charge.")
        return
    }
    SHADOW_RUNES.forEach { rune -> player.inventory.remove(rune, sets, assureFullRemoval = true) }
    setShadowCharges(player, shadowCharges(player) + sets)
    if (ring == Items.RING_OF_SHADOWS_UNCHARGED) {
        player.inventory.remove(Items.RING_OF_SHADOWS_UNCHARGED, 1, assureFullRemoval = true)
        player.inventory.add(Items.RING_OF_SHADOWS)
    }
    player.message("You add $sets charge${if (sets == 1) "" else "s"} to your ring of shadows. It now has ${shadowCharges(player)}.")
}

/** Uncharging refunds the runes; what does not fit goes to the floor, as the wiki's overflow rule does. */
fun unchargeShadowRing(player: Player) {
    val charges = shadowCharges(player)
    if (charges <= 0) {
        player.message("Your ring of shadows has no charges to remove.")
        return
    }
    setShadowCharges(player, 0)
    SHADOW_RUNES.forEach { rune ->
        val added = player.inventory.add(rune, charges)
        val left = charges - added.completed
        if (left > 0) player.world.spawn(GroundItem(rune, left, player.tile, player))
    }
    player.inventory.remove(Items.RING_OF_SHADOWS, 1, assureFullRemoval = true)
    player.inventory.add(Items.RING_OF_SHADOWS_UNCHARGED)
    player.message("You remove all $charges charges from your ring of shadows.")
}

fun checkShadowRing(player: Player) {
    player.message("Your ring of shadows has ${shadowCharges(player)} charge${if (shadowCharges(player) == 1) "" else "s"}.")
}

fun shadowTeleportUnavailable(player: Player) {
    player.message("The ring hums, but that realm is not part of this world yet.")
}

SHADOW_RUNES.forEach { rune ->
    listOf(Items.RING_OF_SHADOWS, Items.RING_OF_SHADOWS_UNCHARGED).forEach { ring ->
        on_item_on_item(item1 = rune, item2 = ring) { chargeShadowRing(player) }
    }
}

/*
 * Bind only the options each ring variant really carries in the cache.
 *
 * Both menus are short and the two variants differ: the charged ring 23847 has worn options
 * [Check, Ghorrock Dungeon, The Scar, Lassar Undercity, The Stranglewood] (five slots, so "Ancient Vault" did not
 * fit), and the uncharged ring 23848 has inventory options [Wear, Charge, Destroy] only - no Check, Uncharge or
 * Teleport. Asking on_item_option / on_equipment_option for an option the cache does not have throws straight out
 * of PluginRepository.init and kills the whole server boot (owner 2026-09-20 "i cant start my rsps"), so every bind
 * below is filtered against the real menu first.
 */
listOf(Items.RING_OF_SHADOWS, Items.RING_OF_SHADOWS_UNCHARGED).forEach { ring ->
    val def = world.definitions.get(ItemDef::class.java, ring)
    val bagOptions = def.inventoryMenu.filterNotNull().filter { it.isNotBlank() }
    val wornOptions = def.equipmentMenu.filterNotNull().filter { it.isNotBlank() }
    fun hasBag(option: String) = bagOptions.any { it.equals(option, ignoreCase = true) }
    fun hasWorn(option: String) = wornOptions.any { it.equals(option, ignoreCase = true) }

    if (hasBag("charge")) on_item_option(item = ring, option = "charge") { chargeShadowRing(player) }
    if (hasBag("check")) on_item_option(item = ring, option = "check") { checkShadowRing(player) }
    if (hasBag("uncharge")) on_item_option(item = ring, option = "uncharge") { unchargeShadowRing(player) }
    if (hasBag("teleport")) on_item_option(item = ring, option = "teleport") { shadowTeleportUnavailable(player) }

    if (hasWorn("Check")) on_equipment_option(ring, option = "Check") { checkShadowRing(player) }
    SHADOW_DESTINATIONS.filter { hasWorn(it) }.forEach { destination ->
        on_equipment_option(ring, option = destination) { shadowTeleportUnavailable(player) }
    }
}
