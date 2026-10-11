package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.inter.bank.Bank

/**
 * Owner decision 2026-10-11 (dossier 4a-1, "precieze OSRS-werking voor alle items"), sourced from the OSRS Wiki:
 * - "Ancient warriors' equipment": the Deadman Mode versions of Vesta's, Statius's, Zuriel's and Morrigan's equipment were
 *   "released with their degradation mechanic removed", and "no corrupted versions of the armour exist in Deadman Mode".
 * - "Torva platebody": OSRS Torva does not degrade (the damaged Nex drop is restored once; used/broken are RS2 history).
 *
 * So none of these degrade any more ([DegradeTable] has no rows for them), and every degraded, corrupt, used or broken copy a
 * player still holds becomes the normal item ([migrate], at login, before [gg.rsmod.plugins.content.mechanics.removed.RemovedItems]
 * purges anything unobtainable). Corrupt items are no longer offered anywhere: drops give the normal item ([normal]) and the
 * Grand Exchange refuses them (RemovedItems.UNOBTAINABLE_IDS). Zaryte bow, Barrows, Moon armour, the Amulet of the damned and
 * the PvP-death breakables keep their OSRS degradation (owner: do not touch).
 */
object DeadmanGear {
    /** Every (deg), corrupt, corrupt (deg), used and broken id -> the normal item it becomes (unnoted ids; noted forms follow). */
    val CONVERT: Map<Int, Int> =
        buildMap {
            fun to(target: Int, vararg from: Int) = from.forEach { put(it, target) }
            // Vesta's / Statius's / Zuriel's / Morrigan's: (deg), corrupt and corrupt (deg) -> the normal piece.
            to(Items.VESTAS_LONGSWORD, Items.VESTAS_LONGSWORD_DEG, Items.CORRUPT_VESTAS_LONGSWORD, Items.C_VESTAS_LONGSWORD_DEG)
            to(Items.VESTAS_SPEAR, Items.VESTAS_SPEAR_DEG, Items.CORRUPT_VESTAS_SPEAR, Items.CORRUPT_VESTAS_SPEAR_DEG)
            to(Items.VESTAS_CHAINBODY, Items.VESTAS_CHAINBODY_DEG, Items.CORRUPT_VESTAS_CHAINBODY, Items.CORRUPT_VESTAS_CHAINBODY_DEG)
            to(Items.VESTAS_PLATESKIRT, Items.VESTAS_PLATESKIRT_DEG, Items.CORRUPT_VESTAS_PLATESKIRT, Items.CORRUPT_VESTAS_PLATESKIRT_DEG)
            to(Items.STATIUSS_WARHAMMER, Items.STATIUS_WARHAMMER_DEG, Items.CORRUPT_STATIUSS_WARHAMMER, Items.C_STATIUSS_WARHAMMER_DEG)
            to(Items.STATIUSS_FULL_HELM, Items.STATIUS_FULL_HELM_DEG, Items.CORRUPT_STATIUSS_FULL_HELM, Items.CORRUPT_STATIUS_FULL_HELM_DEG)
            to(Items.STATIUSS_PLATEBODY, Items.STATIUSS_PLATEBODY_DEG, Items.CORRUPT_STATIUSS_PLATEBODY, Items.CORRUPT_STATIUSS_PLATEBODY_DEG)
            to(Items.STATIUSS_PLATELEGS, Items.STATIUSS_PLATELEGS_DEG, Items.CORRUPT_STATIUSS_PLATELEGS, Items.CORRUPT_STATIUSS_PLATELEGS_DEG)
            to(Items.ZURIELS_STAFF, Items.ZURIELS_STAFF_DEG, Items.CORRUPT_ZURIELS_STAFF, Items.CORRUPT_ZURIELS_STAFF_DEG)
            to(Items.ZURIELS_HOOD, Items.ZURIELS_HOOD_DEG, Items.CORRUPT_ZURIELS_HOOD, Items.CORRUPT_ZURIELS_HOOD_DEG)
            to(Items.ZURIELS_ROBE_TOP, Items.ZURIELS_ROBE_TOP_DEG, Items.CORRUPT_ZURIELS_ROBE_TOP, Items.CORRUPT_ZURIELS_ROBE_TOP_DEG)
            to(Items.ZURIELS_ROBE_BOTTOM, Items.ZURIELS_ROBE_BOTTOM_DEG, Items.CORRUPT_ZURIELS_ROBE_BOTTOM, Items.CORRUPT_ZURIELS_ROBE_BOTTOM_DEG)
            to(Items.MORRIGANS_COIF, Items.MORRIGANS_COIF_DEG, Items.CORRUPT_MORRIGANS_COIF, Items.CORRUPT_MORRIGANS_COIF_DEG)
            to(Items.MORRIGANS_LEATHER_BODY, Items.MORRIGANS_LEATHER_BODY_DEG, Items.CORRUPT_MORRIGANS_LEATHER_BODY, Items.CORRUPT_MORRIGANS_LEATHER_BODY_DEG)
            to(Items.MORRIGANS_LEATHER_CHAPS, Items.MORRIGANS_LEATHER_CHAPS_DEG, Items.CORRUPT_MORRIGANS_LEATHER_CHAPS, Items.CORRUPT_MORRIGANS_LEATHER_CHAPS_DEG)
            to(Items.MORRIGANS_JAVELIN, Items.CORRUPT_MORRIGANS_JAVELIN)
            to(Items.MORRIGANS_JAVELIN_P, Items.C_MORRIGANS_JAVELIN_P)
            to(Items.MORRIGANS_JAVELIN_P_13881, Items.C_MORRIGANS_JAVELIN_P_13955)
            to(Items.MORRIGANS_JAVELIN_P_13882, Items.C_MORRIGANS_JAVELIN_P_13956)
            to(Items.MORRIGANS_THROWING_AXE, Items.C_MORRIGANS_THROWING_AXE)
            // Corrupt dragon equipment (a PvP-world item, not in OSRS Deadman Mode) -> the normal dragon piece.
            to(Items.DRAGON_CHAINBODY_3140, Items.CORRUPT_DRAGON_CHAINBODY, Items.CORRUPT_DRAGON_CHAINBODY_DEG)
            to(Items.DRAGON_MED_HELM, Items.CORRUPT_DRAGON_MED_HELM, Items.CORRUPT_DRAGON_MED_HELM_DEG)
            to(Items.DRAGON_SQ_SHIELD, Items.CORRUPT_DRAGON_SQ_SHIELD, Items.CORRUPT_DRAGON_SQ_SHIELD_DEG)
            to(Items.DRAGON_PLATESKIRT, Items.CORRUPT_DRAGON_PLATESKIRT, Items.CORRUPT_DRAGON_PLATESKIRT_DEG)
            to(Items.DRAGON_PLATELEGS, Items.CORRUPT_DRAGON_PLATELEGS, Items.CORRUPT_DRAGON_PLATELEGS_DEG)
            to(Items.DRAGON_BATTLEAXE, Items.CORRUPT_DRAGON_BATTLEAXE, Items.C_DRAGON_BATTLEAXE_DEG)
            to(Items.DRAGON_DAGGER, Items.CORRUPT_DRAGON_DAGGER, Items.C_DRAGON_DAGGER_DEG)
            to(Items.DRAGON_SCIMITAR, Items.CORRUPT_DRAGON_SCIMITAR, Items.C_DRAGON_SCIMITAR_DEG)
            to(Items.DRAGON_LONGSWORD, Items.CORRUPT_DRAGON_LONGSWORD, Items.C_DRAGON_LONGSWORD_DEG)
            to(Items.DRAGON_MACE, Items.CORRUPT_DRAGON_MACE, Items.CORRUPT_DRAGON_MACE_DEG)
            to(Items.DRAGON_SPEAR, Items.CORRUPT_DRAGON_SPEAR, Items.CORRUPT_DRAGON_SPEAR_DEG)
            // Torva: used (id + 2) and broken (id + 3) -> Torva.
            to(Items.TORVA_FULL_HELM, Items.TORVA_FULL_HELM_20137, Items.TORVA_FULL_HELM_BROKEN)
            to(Items.TORVA_PLATEBODY, Items.TORVA_PLATEBODY_20141, Items.TORVA_PLATEBODY_BROKEN)
            to(Items.TORVA_PLATELEGS, Items.TORVA_PLATELEGS_20145, Items.TORVA_PLATELEGS_BROKEN)
        }

    /** The normal items themselves: a copy may still carry the charge count of the old degrading (removed at login). */
    val NORMAL: Set<Int> = CONVERT.values.toSet()

    /** Corrupt ids (with their (deg) forms): nothing may offer them any more (RemovedItems.UNOBTAINABLE_IDS). */
    val CORRUPT: Set<Int> =
        CONVERT.keys.filter { it in Items.CORRUPT_STATIUSS_PLATEBODY..Items.CORRUPT_DRAGON_SPEAR_DEG }.toSet()

    /** What a drop table, shop or reward gives for [itemId]: the normal item for a corrupt / degraded id, else [itemId]. */
    fun normal(itemId: Int): Int = CONVERT[itemId] ?: itemId

    /**
     * Converts every degraded / corrupt / used / broken copy [player] holds into the normal item (amount kept, the stale charge
     * count dropped) in the inventory, worn equipment, bank, gravestone and Death's Office. A bank that already holds the normal
     * item gets the amount added to that slot (bank items always stack), and the bank closes up. POH storage holds none of these
     * ids (its families' id sets). Returns how many stacks changed.
     */
    fun migrate(player: Player): Int {
        val defs = player.world.definitions
        var changed = 0
        for (container in listOf(player.inventory, player.equipment, player.gravestone, player.deathRecovery)) {
            changed += migrate(container, defs, bank = false)
        }
        val bankChanged = migrate(player.bank, defs, bank = true)
        if (bankChanged > 0) {
            changed += bankChanged
            Bank.cleanEmptySlots(player)
        }
        return changed
    }

    /** The id [itemId] becomes (noted forms map noted -> noted), or null when it stays. */
    fun target(
        itemId: Int,
        defs: gg.rsmod.game.fs.DefinitionSet,
    ): Int? {
        CONVERT[itemId]?.let { return it }
        val def = defs.getNullable(ItemDef::class.java, itemId) ?: return null
        if (!def.noted) return null
        val unnoted = CONVERT[def.noteLinkId] ?: return null
        val noted = defs.getNullable(ItemDef::class.java, unnoted)?.noteLinkId ?: return null
        return noted.takeIf { it > 0 }
    }

    private fun migrate(
        container: ItemContainer,
        defs: gg.rsmod.game.fs.DefinitionSet,
        bank: Boolean,
    ): Int {
        var changed = 0
        for (slot in 0 until container.capacity) {
            val item = container[slot] ?: continue
            val to = target(item.id, defs)
            if (to == null) {
                // A normal piece with the charge count of the old degrading: the count is meaningless now.
                if (item.id in NORMAL && item.hasAnyAttr()) {
                    container[slot] = Item(item.id, item.amount)
                    changed++
                }
                continue
            }
            // The bank stacks everything; elsewhere a noted (stackable) item must stay one stack.
            val stacks = bank || defs.getNullable(ItemDef::class.java, to)?.stackable == true
            val existing = if (stacks) (0 until container.capacity).firstOrNull { it != slot && container[it]?.id == to } else null
            if (existing != null) {
                val into = container[existing]!!
                container[existing] = Item(to, into.amount + item.amount)
                container[slot] = null
            } else {
                container[slot] = Item(to, item.amount)
            }
            changed++
        }
        if (changed > 0) container.dirty = true
        return changed
    }
}
