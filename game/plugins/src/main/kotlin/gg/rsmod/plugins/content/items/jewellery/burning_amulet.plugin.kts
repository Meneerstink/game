package gg.rsmod.plugins.content.items.jewellery

import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/*
 * Burning amulet (owner 2026-09-19 "import burning amulet fully functioning"; import batch "owner0919",
 * tx-20260919-211216, upstream 21166/21169/21171/21173/21175).
 *
 * OSRS Wiki "Burning amulet": five charges, one per teleport, and "after all five charges are used, the amulet will
 * disintegrate". Rubbed in the inventory or used through its worn options; destinations Chaos Temple (3234, 3634,
 * level 15 Wilderness), Bandit Camp (3038, 3651, level 17) and Lava Maze (3028, 3842, level 41). "Players will be
 * given a warning before teleporting" - the wiki does not quote that dialogue, so the warning text here is ADAPTED
 * from the wording the other Wilderness teleports on this server use.
 */

val BURNING_AMULET =
    intArrayOf(
        Items.BURNING_AMULET_5,
        Items.BURNING_AMULET_4,
        Items.BURNING_AMULET_3,
        Items.BURNING_AMULET_2,
        Items.BURNING_AMULET_1,
    )


private val BURNING_LOCATIONS =
    linkedMapOf(
        "Chaos Temple" to (Tile(3234, 3634, 0) to 15),
        "Bandit Camp" to (Tile(3038, 3651, 0) to 17),
        "Lava Maze" to (Tile(3028, 3842, 0) to 41),
    )

/*
 * Only bind the options each charge tier really carries in the cache. A revision-667 item menu is short (five worn
 * entries) and an import can silently drop one, and asking on_item_option / on_equipment_option for an option the
 * cache does not have throws out of PluginRepository.init and takes the whole server boot down - which is exactly
 * what the ring of shadows did on 2026-09-20 ("Ancient Vault" / "check" not found).
 */
BURNING_AMULET.forEach { amulet ->
    val def = world.definitions.get(ItemDef::class.java, amulet)
    val bagOptions = def.inventoryMenu.filterNotNull().filter { it.isNotBlank() }
    val wornOptions = def.equipmentMenu.filterNotNull().filter { it.isNotBlank() }

    if (bagOptions.any { it.equals("rub", ignoreCase = true) }) {
        on_item_option(item = amulet, option = "rub") {
            val self = player
            self.queue {
                val choice = options("Chaos Temple.", "Bandit Camp.", "Lava Maze.", "Nowhere.")
                val entry = BURNING_LOCATIONS.entries.toList().getOrNull(choice - 1) ?: return@queue
                if (confirmWilderness(entry.key, entry.value.second)) {
                    self.teleportWithAmulet(entry.value.first, isEquipped = false)
                }
            }
        }
    }

    BURNING_LOCATIONS.filter { (name, _) -> wornOptions.any { it.equals(name, ignoreCase = true) } }.forEach { (name, destination) ->
        on_equipment_option(amulet, option = name) {
            val self = player
            self.queue(TaskPriority.STRONG) {
                if (confirmWilderness(name, destination.second)) {
                    self.teleportWithAmulet(destination.first, isEquipped = true)
                }
            }
        }
    }
}

/** The warning OSRS shows before a burning amulet teleport (text ADAPTED, the level is the wiki's). */
suspend fun QueueTask.confirmWilderness(
    name: String,
    wildernessLevel: Int,
): Boolean =
    options(
        "Yes, teleport me to $name.",
        "No, I'll stay here.",
        title = "$name is in level $wildernessLevel Wilderness. Teleport there?",
    ) == 1

/** Teleports with a burning amulet and spends one charge; the last charge destroys the amulet. */
fun Player.teleportWithAmulet(
    endTile: Tile,
    isEquipped: Boolean,
) {
    val used = getInteractingItemId()
    canTeleport(TeleportType.JEWELRY) {
        // Start/land sounds come from TeleportType.JEWELRY in the shared teleport (one rule, no second copy here).
        val replacement = amuletReplacement(used)
        if (isEquipped) {
            equipment[EquipmentType.AMULET.id] = if (replacement > -1) Item(replacement) else null
        } else {
            inventory.remove(getInteractingItem())
            if (replacement > -1) {
                inventory.add(replacement)
            }
        }
        teleport(endTile, TeleportType.JEWELRY)
        message(amuletChargeMessage(used))
    }
}

/** The next lower charge, or -1 when the amulet disintegrates. */
fun amuletReplacement(original: Int): Int = if (original in Items.BURNING_AMULET_5..Items.BURNING_AMULET_2) original + 1 else -1

fun amuletChargeMessage(original: Int): String =
    when (original) {
        Items.BURNING_AMULET_5 -> "Your burning amulet has four uses left."
        Items.BURNING_AMULET_4 -> "Your burning amulet has three uses left."
        Items.BURNING_AMULET_3 -> "Your burning amulet has two uses left."
        Items.BURNING_AMULET_2 -> "Your burning amulet has one use left."
        else -> "Your burning amulet disintegrates."
    }
