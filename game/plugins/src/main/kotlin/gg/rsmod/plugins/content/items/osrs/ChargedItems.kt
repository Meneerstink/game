package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * Owner 2026-09-18 (binding): "a toxic blowpipe when not charged should give the weapon's name ... inactive - this is for every
 * weapon which uses some kind of charges, check every weapon". OSRS has a separate item for every charge weapon without charges
 * ("Toxic blowpipe (empty)", "Craw's bow (u)", "Sanguinesti staff (uncharged)", "Bow of Faerdhinen (inactive)", ...), and the
 * depletion routes here already switch to it. Root cause of the report: a charged id created without charge attributes (the
 * `item` spawn, a shop, the Grand Exchange, a reward) stayed the charged item with zero charges, so it kept the charged name.
 *
 * [UNCHARGED_FOR] is built from each family's own charged/uncharged table - never a hand-written copy - and only for families
 * whose charge reader treats a missing charge attribute as 0 (so the item really holds nothing). Excluded on purpose: the
 * Dragonfire shield family (a spawned charged shield counts as 50 charges, owner 2026-09-18), the (full) tridents (full by
 * definition), Ring of suffering (r), Amulet of blood fury and crystal armour seeds (no separate empty item / different rule).
 */
object ChargedItems {
    val UNCHARGED_FOR: Map<Int, Int> by lazy {
        val map = LinkedHashMap<Int, Int>()
        Blowpipe.Pipe.values().forEach { map[it.charged] = it.empty }
        map.putAll(DizanasQuiver.UNCHARGED_FOR)
        PoweredStaves.Staff.values().forEach { map[it.charged] = it.uncharged }
        map.putAll(CrystalEquipment.INACTIVE_FOR)
        RevenantBows.CHARGED_FOR.forEach { (uncharged, charged) -> map[charged] = uncharged }
        map[Items.VENATOR_BOW] = Items.VENATOR_BOW_UNCHARGED
        map[Items.TONALZTICS_OF_RALOS] = Items.TONALZTICS_OF_RALOS_UNCHARGED
        map[Items.TOXIC_STAFF_OF_THE_DEAD] = Items.TOXIC_STAFF_UNCHARGED
        Tomes.Tome.values().forEach { map[it.charged] = it.empty }
        map[Items.ARCLIGHT] = Items.ARCLIGHT_INACTIVE
        map
    }

    /** The id an attribute-less [itemId] must have: its uncharged item when it is a charged one, else null (unchanged). */
    fun unchargedFor(itemId: Int): Int? = UNCHARGED_FOR[itemId]
}
