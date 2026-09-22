package gg.rsmod.plugins.content.items

import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport

/**
 * Teleport items whose cache options had no handler (2026-09-22 item-option census, owner: "audit the complete teleport
 * system ... fix every discovered teleport"). One route for all of them: [itemTeleport] checks the teleport rules, uses
 * a charge (replacing the item with its next-lower charge, or removing it), then teleports.
 *
 * Destinations:
 * - Camulet, Digsite pendant, Pharaoh's sceptre: the Void donor's same-era areas (camulet_teleport, dig_site_teleport,
 *   jalsavrah/jaleustrophos/jaldraocht_teleport) and its charge rules (Camulet 4 charges; sceptre 3 charges).
 * - Void seal (8 charges, "Once its 8 charges are used, it dissolves"), Ardougne cloaks, Karamja gloves 3/4, Juju teleport
 *   spiritbag (single use), Witchdoctor mask, Drakan's medallion, Teleport crystal (2011: one-click Lletya, 4 charges,
 *   then the tiny elf crystal): RS Wiki `{{Teleport map}}` squares on each item's page.
 * ADAPTED: items with no sourced animation use TeleportType.JEWELRY. Quest requirements the cache has no quest system for
 * (Mourning's End, The Branches of Darkmeyer, ...) are not enforced, as with Ava's devices (owner decision).
 */
data class Dest(val name: String, val tile: Tile)

fun Player.itemTeleport(
    item: Int,
    worn: EquipmentType?,
    next: Int?,
    dest: Tile,
    type: TeleportType = TeleportType.JEWELRY,
    onUse: (() -> Unit)? = null,
) {
    canTeleport(type) {
        if (worn != null) {
            if (equipment[worn.id]?.id != item) return@canTeleport
            if (next != null) equipment[worn.id] = if (next > 0) Item(next) else null
        } else {
            val slot = inventory.getItemIndex(item, skipAttrItems = false)
            if (slot == -1) return@canTeleport
            if (next != null) {
                inventory.remove(item, beginSlot = slot)
                if (next > 0) inventory.add(next, beginSlot = slot)
            }
        }
        onUse?.invoke()
        queue(TaskPriority.STRONG) { teleport(dest, type) }
    }
}

/** Binds [option] (inventory or worn) of [item] to a menu of [dests] (one destination teleports straight away). */
fun bindTeleport(
    item: Int,
    option: String,
    worn: EquipmentType?,
    next: Int?,
    dests: List<Dest>,
    type: TeleportType = TeleportType.JEWELRY,
) {
    val logic: Plugin.() -> Unit = {
        if (dests.size == 1) {
            player.itemTeleport(item, worn, next, dests[0].tile, type)
        } else {
            player.queue {
                val choice = options(*dests.map { it.name }.toTypedArray(), "Nowhere.", title = "Where would you like to teleport to?")
                dests.getOrNull(choice - 1)?.let { player.itemTeleport(item, worn, next, it.tile, type) }
            }
        }
    }
    if (worn == null) on_item_option(item = item, option = option, logic = logic) else on_equipment_option(item, option = option, logic = logic)
}

fun bindChain(
    chain: IntArray,
    option: String,
    slot: EquipmentType,
    dests: List<Dest>,
    last: Int = -1,
) {
    chain.forEachIndexed { i, item ->
        val next = chain.getOrNull(i + 1) ?: last
        bindTeleport(item, option, null, next, dests)
        bindTeleport(item, option, slot, next, dests)
    }
}

// Digsite pendant (5) -> (1), then gone.
bindChain(
    intArrayOf(Items.DIGSITE_PENDANT_5, Items.DIGSITE_PENDANT_4, Items.DIGSITE_PENDANT_3, Items.DIGSITE_PENDANT_2, Items.DIGSITE_PENDANT_1),
    "Rub",
    EquipmentType.AMULET,
    listOf(Dest("Digsite.", Tile(3340, 3445))),
)

// Void seal (8) -> (1), then it dissolves (RS Wiki "Void knight seal", {{Teleport map|2658,2660}}).
bindChain(
    intArrayOf(
        Items.VOID_SEAL_8, Items.VOID_SEAL_7, Items.VOID_SEAL_6, Items.VOID_SEAL_5,
        Items.VOID_SEAL_4, Items.VOID_SEAL_3, Items.VOID_SEAL_2, Items.VOID_SEAL_1,
    ),
    "Rub",
    EquipmentType.AMULET,
    listOf(Dest("Void Knights' Outpost.", Tile(2658, 2660))),
)

// Teleport crystal (4) -> (1), then the tiny elf crystal. 2011: one-click teleport to Lletya ({{Teleport map|2332,3170}}).
listOf(Items.TELEPORT_CRYSTAL_4, Items.TELEPORT_CRYSTAL_3, Items.TELEPORT_CRYSTAL_2, Items.TELEPORT_CRYSTAL_1).let { chain ->
    chain.forEachIndexed { i, item ->
        bindTeleport(item, "Activate", null, chain.getOrNull(i + 1) ?: Items.TINY_ELF_CRYSTAL, listOf(Dest("Lletya.", Tile(2332, 3170))))
    }
}

// Camulet: 4 charges kept on the player (Void Camulet.kt), recharged with ugthanki dung.
val CAMULET_CHARGES = AttributeKey<Int>("camulet_charges")
val CAMULET_DEST = Tile(3193, 2925)

fun Player.camuletCharges(): Int = attr[CAMULET_CHARGES] ?: 4

fun Plugin.rubCamulet(worn: EquipmentType?) {
    if (player.camuletCharges() <= 0) {
        player.message("Your Camulet has run out of teleport charges. You can renew them by applying camel dung.")
        return
    }
    player.itemTeleport(Items.CAMULET, worn, null, CAMULET_DEST) {
        player.attr[CAMULET_CHARGES] = player.camuletCharges() - 1
        player.message("You rub the amulet...")
    }
}

on_item_option(item = Items.CAMULET, option = "Rub") { rubCamulet(null) }
on_equipment_option(Items.CAMULET, option = "Rub") { rubCamulet(EquipmentType.AMULET) }
on_item_option(item = Items.CAMULET, option = "Check-charge") {
    val charges = player.camuletCharges()
    player.message("Your Camulet has $charges ${if (charges == 1) "charge" else "charges"} left.")
    if (charges == 0) player.message("You can recharge it by applying camel dung.")
}

// Pharaoh's sceptre (3) -> (1), then the uncharged sceptre.
val SCEPTRE_DESTS =
    listOf(
        Dest("Jalsavrah", Tile(1934, 4428, 3)),
        Dest("Jaleustrophos", Tile(3341, 2827)),
        Dest("Jaldraocht", Tile(3232, 2897)),
    )
listOf(Items.PHARAOHS_SCEPTRE_3, Items.PHARAOHS_SCEPTRE_2, Items.PHARAOHS_SCEPTRE_1).let { chain ->
    chain.forEachIndexed { i, item ->
        val next = chain.getOrNull(i + 1) ?: Items.PHARAOHS_SCEPTRE
        bindTeleport(item, "Teleport", null, next, SCEPTRE_DESTS)
        SCEPTRE_DESTS.forEach { d -> bindTeleport(item, d.name, EquipmentType.WEAPON, next, listOf(d)) }
    }
}
on_item_option(item = Items.PHARAOHS_SCEPTRE, option = "Teleport") { player.message("Your sceptre has no charges left.") }
on_equipment_option(Items.PHARAOHS_SCEPTRE, option = "Teleport") { player.message("Your sceptre has no charges left.") }

// Ardougne cloaks: Kandarin Monastery ({{Teleport map|2606,3219}}); cloak 2+ also Manor Farm ({{Teleport map|2671,3376}}).
val MONASTERY = Dest("Kandarin Monastery", Tile(2606, 3219))
val ARDOUGNE_FARM = Dest("Ardougne Farm", Tile(2671, 3376))
bindTeleport(Items.ARDOUGNE_CLOAK_1, "Teleport", null, null, listOf(MONASTERY), TeleportType.MONASTERY)
bindTeleport(Items.ARDOUGNE_CLOAK_1, "Kandarin Monastery", EquipmentType.CAPE, null, listOf(MONASTERY), TeleportType.MONASTERY)
listOf(Items.ARDOUGNE_CLOAK_2, Items.ARDOUGNE_CLOAK_3, Items.ARDOUGNE_CLOAK_4).forEach { cloak ->
    on_item_option(item = cloak, option = "Teleports") {
        player.queue {
            when (options("Kandarin Monastery.", "Ardougne Farm.", "Nowhere.", title = "Where would you like to teleport to?")) {
                1 -> player.itemTeleport(cloak, null, null, MONASTERY.tile, TeleportType.MONASTERY)
                2 -> player.itemTeleport(cloak, null, null, ARDOUGNE_FARM.tile, TeleportType.FARM_PATCH)
            }
        }
    }
    bindTeleport(cloak, "Kandarin Monastery", EquipmentType.CAPE, null, listOf(MONASTERY), TeleportType.MONASTERY)
    bindTeleport(cloak, "Ardougne Farm", EquipmentType.CAPE, null, listOf(ARDOUGNE_FARM), TeleportType.FARM_PATCH)
}

// Karamja gloves 3/4: Shilo Village underground gem mine ({{Teleport map|2840,9386}}), unlimited.
listOf(Items.KARAMJA_GLOVES_3, Items.KARAMJA_GLOVES_4).forEach { gloves ->
    val mine = listOf(Dest("Gem mine", Tile(2840, 9386)))
    bindTeleport(gloves, "Teleport", null, null, mine)
    bindTeleport(gloves, "Teleport", EquipmentType.GLOVES, null, mine)
}

// Herblore Habitat, near Papa Mambo ({{Teleport map|2953,2933}}): the spiritbag is used up, the mask is unlimited.
val HERBLORE_HABITAT = listOf(Dest("Herblore Habitat", Tile(2953, 2933)))
bindTeleport(Items.JUJU_TELEPORT_SPIRITBAG, "Teleport", null, -1, HERBLORE_HABITAT, TeleportType.JUJU_SPIRIT)
bindTeleport(Items.WITCHDOCTOR_MASK, "Teleport", null, null, HERBLORE_HABITAT)
bindTeleport(Items.WITCHDOCTOR_MASK, "Teleport", EquipmentType.HEAD, null, HERBLORE_HABITAT)

// Drakan's medallion (released 31 August 2011): the five destinations on RS Wiki "Drakan's medallion".
val MEDALLION_DESTS =
    listOf(
        Dest("Barrows", Tile(3561, 3311)),
        Dest("Burgh de Rott", Tile(3496, 3202)),
        Dest("Meiyerditch", Tile(3627, 9617)),
        Dest("Darkmeyer", Tile(3625, 3365)),
        Dest("Meiyerditch Laboratories", Tile(3633, 9696)),
    )
bindTeleport(Items.DRAKANS_MEDALLION, "Teleport", null, null, MEDALLION_DESTS, TeleportType.DRAKAN_MEDALLION)
bindTeleport(Items.DRAKANS_MEDALLION, "Teleport", EquipmentType.AMULET, null, MEDALLION_DESTS, TeleportType.DRAKAN_MEDALLION)

/**
 * Grand seed pod Squash. Cache evidence: item 9469 advertises Launch and Squash. The 2011 item
 * teleports from up to level 30 Wilderness, consumes one pod, drains Farming by five, and lands
 * beside King Narnode on the Grand Tree ground floor. The landing tile is derived from this
 * cache's King Narnode spawn (2466,3497,0), not guessed.
 *
 * Launch deliberately remains separate: it requires an outdoor-only delayed Captain Lamdoo
 * pickup to the top-floor glider and the available source does not establish its exact delay.
 */
on_item_option(item = Items.GRAND_SEED_POD, option = "Squash") {
    player.canTeleport(TeleportType.GRAND_SEED_POD) {
        val slot = player.inventory.getItemIndex(Items.GRAND_SEED_POD, skipAttrItems = false)
        if (slot == -1 || !player.inventory.remove(Items.GRAND_SEED_POD, beginSlot = slot).hasSucceeded()) {
            return@canTeleport
        }
        val farming = player.skills.getCurrentLevel(Skills.FARMING)
        player.skills.setCurrentLevel(Skills.FARMING, (farming - 5).coerceAtLeast(0))
        player.teleport(Tile(2465, 3497, 0), TeleportType.GRAND_SEED_POD)
    }
}
