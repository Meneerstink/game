package gg.rsmod.plugins.content.mechanics.removed

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.magic.RunePouch

/**
 * Items taken out of the game (owner 2026-09-24: "Verwijder Armadyl runes volledig (dus elke trace ervan grand exchange shops spells)",
 * "Verwijder aether rune volledig!"). Their cache definitions stay, because item ids must stay contiguous, but nothing may offer,
 * trade or keep them: the Grand Exchange refuses them ([gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface.exchangeable]),
 * the spells, rune pouch and runecrafting no longer know them, and [purge] takes any copy out of a player's inventory, worn items,
 * bank, gravestone, Death's Office and rune pouches at login. One list, so every route asks the same question.
 */
object RemovedItems {
    // Owner 2026-09-26: the Crown of Helios item is removed too (the staff `crown` menu stays, without the item).
    val IDS: Set<Int> = setOf(Items.ARMADYL_RUNE, Items.AETHER_RUNE, Items.AETHER_CATALYST, Items.CROWN_OF_HELIOS)

    /**
     * N457, owner 2026-10-30: all native 667 corrupt weapons AND armour, including (deg), are removed completely.
     * Login deletes held copies without replacement; never convert these through DeadmanGear. Poisoned Morrigan javelins
     * are included. Noted forms follow through [isUnobtainable]. OSRS corrupted Gauntlet / crystal items are outside this set.
     */
    val CORRUPT_IDS: Set<Int> =
        setOf(
            Items.CORRUPT_VESTAS_LONGSWORD, Items.C_VESTAS_LONGSWORD_DEG,
            Items.CORRUPT_VESTAS_SPEAR, Items.CORRUPT_VESTAS_SPEAR_DEG,
            Items.CORRUPT_VESTAS_CHAINBODY, Items.CORRUPT_VESTAS_CHAINBODY_DEG,
            Items.CORRUPT_VESTAS_PLATESKIRT, Items.CORRUPT_VESTAS_PLATESKIRT_DEG,
            Items.CORRUPT_STATIUSS_WARHAMMER, Items.C_STATIUSS_WARHAMMER_DEG,
            Items.CORRUPT_STATIUSS_FULL_HELM, Items.CORRUPT_STATIUS_FULL_HELM_DEG,
            Items.CORRUPT_STATIUSS_PLATEBODY, Items.CORRUPT_STATIUSS_PLATEBODY_DEG,
            Items.CORRUPT_STATIUSS_PLATELEGS, Items.CORRUPT_STATIUSS_PLATELEGS_DEG,
            Items.CORRUPT_ZURIELS_STAFF, Items.CORRUPT_ZURIELS_STAFF_DEG,
            Items.CORRUPT_ZURIELS_HOOD, Items.CORRUPT_ZURIELS_HOOD_DEG,
            Items.CORRUPT_ZURIELS_ROBE_TOP, Items.CORRUPT_ZURIELS_ROBE_TOP_DEG,
            Items.CORRUPT_ZURIELS_ROBE_BOTTOM, Items.CORRUPT_ZURIELS_ROBE_BOTTOM_DEG,
            Items.CORRUPT_MORRIGANS_COIF, Items.CORRUPT_MORRIGANS_COIF_DEG,
            Items.CORRUPT_MORRIGANS_LEATHER_BODY, Items.CORRUPT_MORRIGANS_LEATHER_BODY_DEG,
            Items.CORRUPT_MORRIGANS_LEATHER_CHAPS, Items.CORRUPT_MORRIGANS_LEATHER_CHAPS_DEG,
            Items.CORRUPT_MORRIGANS_JAVELIN, Items.C_MORRIGANS_JAVELIN_P,
            Items.C_MORRIGANS_JAVELIN_P_13955, Items.C_MORRIGANS_JAVELIN_P_13956,
            Items.C_MORRIGANS_THROWING_AXE, Items.CORRUPT_DRAGON_CHAINBODY,
            Items.CORRUPT_DRAGON_CHAINBODY_DEG, Items.CORRUPT_DRAGON_MED_HELM,
            Items.CORRUPT_DRAGON_MED_HELM_DEG, Items.CORRUPT_DRAGON_SQ_SHIELD,
            Items.CORRUPT_DRAGON_SQ_SHIELD_DEG, Items.CORRUPT_DRAGON_PLATESKIRT,
            Items.CORRUPT_DRAGON_PLATESKIRT_DEG, Items.CORRUPT_DRAGON_PLATELEGS,
            Items.CORRUPT_DRAGON_PLATELEGS_DEG, Items.CORRUPT_DRAGON_BATTLEAXE,
            Items.C_DRAGON_BATTLEAXE_DEG, Items.CORRUPT_DRAGON_DAGGER,
            Items.C_DRAGON_DAGGER_DEG, Items.CORRUPT_DRAGON_SCIMITAR,
            Items.C_DRAGON_SCIMITAR_DEG, Items.CORRUPT_DRAGON_LONGSWORD,
            Items.C_DRAGON_LONGSWORD_DEG, Items.CORRUPT_DRAGON_MACE,
            Items.CORRUPT_DRAGON_MACE_DEG, Items.CORRUPT_DRAGON_SPEAR,
            Items.CORRUPT_DRAGON_SPEAR_DEG,
        )

    /**
     * Owner 2026-10-06: "remove pernix and virtus completely from our rsps the rest needs to be degraded". Nothing may
     * offer them any more: Nex no longer drops them and the Grand Exchange (player offers and the house, which sells every
     * exchangeable item) refuses them through [isUnobtainable]. Copies already held are taken by the login [purge] too
     * (owner 2026-10-06: "Delete on login"); they stay out of [IDS], which also drives the removed-runes rules.
     * Intact, used (id + 2) and broken ids of the native 667 sets; the noted forms follow through [isUnobtainable].
     */
    val UNOBTAINABLE_IDS: Set<Int> =
        setOf(
            Items.PERNIX_COWL, Items.PERNIX_COWL_20149, Items.PERNIX_COWL_BROKEN,
            Items.PERNIX_BODY, Items.PERNIX_BODY_20153, Items.PERNIX_BODY_BROKEN,
            Items.PERNIX_CHAPS, Items.PERNIX_CHAPS_20157, Items.PERNIX_CHAPS_BROKEN,
            Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161, Items.VIRTUS_MASK_BROKEN,
            Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165, Items.VIRTUS_ROBE_TOP_BROKEN,
            Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169, Items.VIRTUS_ROBE_LEGS_BROKEN,
            // N464 (owner 2026-10-30: "zaryte bow moet volledig verwijderd worden dus ook uit de grand exc"; held copies: "Weg bij
            // inloggen", like Pernix/Virtus). Nex no longer drops it (N293); the Zaryte crossbow stays.
            Items.ZARYTE_BOW, Items.ZARYTE_BOW_20173, Items.ZARYTE_BOW_BROKEN,
        ) +
            // Owner 2026-10-11 (OSRS Deadman Mode): every non-corrupt degraded, used and broken Deadman gear / Torva id. A held copy
            // becomes the normal item at login first (DeadmanGear.migrate runs before [purge]); nothing may offer these again.
            gg.rsmod.plugins.content.items.armor.DeadmanGear.CONVERT.keys + CORRUPT_IDS +
            // N424 (owner 2026-10-29): the looting bag is out of the game; its old contents go to the bank first (LootingBag.retire).
            gg.rsmod.plugins.content.mechanics.pvp.LootingBag.IDS

    /**
     * N464 (owner 2026-10-30: the Zaryte bow "staat nog in de grand exc"): the client's Grand Exchange item search lists every item
     * with the cache's GE flag (opcode 65), so the removed and unobtainable items still showed there (Pernix too) although the GE
     * refused them. These ids lose that flag in both caches (RemovedContentCacheTool reads [GE_HIDDEN_TABLE]; a test keeps the table
     * equal to this set).
     */
    val geHiddenIds: java.util.SortedSet<Int>
        get() = (IDS + UNOBTAINABLE_IDS + DungeoneeringItems.IDS).toSortedSet()

    const val GE_HIDDEN_TABLE = "./data/cfg/removed/ge_hidden.txt"

    /** True for a removed item or its noted form. */
    fun isRemoved(def: ItemDef): Boolean = def.id in IDS || (def.noted && def.noteLinkId in IDS)

    /** True for anything nothing may offer: a removed item, a no-longer-obtainable item, or the noted form of either. */
    fun isUnobtainable(def: ItemDef): Boolean =
        isRemoved(def) || isGone(def.id) || (def.noted && isGone(def.noteLinkId))

    /** Owner 2026-10-11 (DGN): every Dungeoneering item joins the unobtainable items - GE, house and login purge alike. */
    private fun isGone(itemId: Int): Boolean = itemId in UNOBTAINABLE_IDS || DungeoneeringItems.isDungeoneering(itemId)

    fun isRemoved(
        player: Player,
        itemId: Int,
    ): Boolean = itemId in IDS || isRemoved(player.world.definitions.get(ItemDef::class.java, itemId))

    /**
     * Removes every removed or no-longer-obtainable item the player holds; returns how many item stacks were taken.
     * DGN 2026-10-21: EVERY container the player has (inventory, worn, bank, gravestone, Death's Office, random-event gift,
     * the retired looting bag, every beast-of-burden cargo - the save loads each one into [Player.containers]) and the player's own
     * Grand Exchange offers ([purgeExchange]); a fixed list of five let the looting bag and BoB cargo keep removed items.
     */
    fun purge(player: Player): Int {
        var taken = 0
        val containers =
            LinkedHashSet<ItemContainer>().apply {
                add(player.inventory)
                add(player.equipment)
                add(player.bank)
                add(player.gravestone)
                add(player.deathRecovery)
                addAll(player.containers.values)
            }
        for (container in containers) {
            taken += purge(player, container)
        }
        taken += purgeExchange(player)
        return taken
    }

    /**
     * Ends the player's Grand Exchange offers for unobtainable items: a sell offer loses the unsold items, a buy offer
     * gets its unspent coins back as collectable and loses the bought items; coins already earned stay collectable.
     */
    private fun purgeExchange(player: Player): Int {
        // Offers are keyed by the login name (GrandExchangeInterface.username); only a real client has one.
        val username = (player as? gg.rsmod.game.model.entity.Client)?.loginUsername ?: return 0
        val service =
            player.world.getService(gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeService::class.java) ?: return 0
        return service.withdrawItems(username) { itemId ->
            isUnobtainable(player.world.definitions.get(ItemDef::class.java, itemId))
        }
    }

    private fun purge(
        player: Player,
        container: ItemContainer,
    ): Int {
        var taken = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            if (isRemoved(player, item.id) || isUnobtainable(player.world.definitions.get(ItemDef::class.java, item.id))) {
                container[slot] = null
                taken++
                continue
            }
            if (RunePouch.isPouch(item.id)) {
                val runes = RunePouch.contents(item)
                val kept = runes.filterNot { it.id in IDS }
                if (kept.size != runes.size) {
                    container[slot] = RunePouch.withContents(item, kept)
                    taken += runes.size - kept.size
                }
            }
        }
        if (taken > 0) container.dirty = true
        return taken
    }
}
