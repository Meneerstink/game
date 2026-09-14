package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * Cosmetic OSRS ornament and colour kits imported by OSRS-IMPORT. One table drives the combine
 * (`CombinationData`), the "Dismantle" action (returns base item + kit, OSRS Wiki item pages) and the
 * PvP death conversion ("Items Kept on Death": an ornamented tradeable item is dropped to the PKer as
 * the non-ornamented item together with its tradeable ornament kit).
 *
 * [Ornament.pvpConvert] is false for kits that are not tradeable (the Bounty Hunter elder chaos and Dagon'hai kits): "Items Kept
 * on Death" gives no rule for a tradeable item carrying an untradeable kit (SOURCE_GAP), so those pieces keep the default death
 * handling.
 */
object OsrsOrnamentKits {
    data class Ornament(
        val ornamented: Int,
        val base: Int,
        val kit: Int,
        val pvpConvert: Boolean = true,
    )

    val ALL =
        listOf(
            Ornament(Items.OCCULT_NECKLACE_OR, Items.OCCULT_NECKLACE, Items.OCCULT_ORNAMENT_KIT),
            Ornament(Items.NECKLACE_OF_ANGUISH_OR, Items.NECKLACE_OF_ANGUISH, Items.ANGUISH_ORNAMENT_KIT),
            Ornament(Items.AMULET_OF_TORTURE_OR, Items.AMULET_OF_TORTURE, Items.TORTURE_ORNAMENT_KIT),
            Ornament(Items.TORMENTED_BRACELET_OR, Items.TORMENTED_BRACELET, Items.TORMENTED_ORNAMENT_KIT),
            // magearmour: "It can be attached to infinity hat, top and bottoms"; "The kit can be detached safely, receiving both
            // items" (OSRS Wiki light/dark infinity colour kit, both kits tradeable).
            Ornament(Items.LIGHT_INFINITY_HAT, Items.INFINITY_HAT, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.LIGHT_INFINITY_TOP, Items.INFINITY_TOP, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.LIGHT_INFINITY_BOTTOMS, Items.INFINITY_BOTTOMS, Items.LIGHT_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_HAT, Items.INFINITY_HAT, Items.DARK_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_TOP, Items.INFINITY_TOP, Items.DARK_INFINITY_COLOUR_KIT),
            Ornament(Items.DARK_INFINITY_BOTTOMS, Items.INFINITY_BOTTOMS, Items.DARK_INFINITY_COLOUR_KIT),
            // "combined with any piece of the ancestral robes set"; "can be detached ... returning both items" (tradeable kit).
            Ornament(Items.TWISTED_ANCESTRAL_HAT, Items.ANCESTRAL_HAT, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            Ornament(Items.TWISTED_ANCESTRAL_ROBE_TOP, Items.ANCESTRAL_ROBE_TOP, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            Ornament(Items.TWISTED_ANCESTRAL_ROBE_BOTTOM, Items.ANCESTRAL_ROBE_BOTTOM, Items.TWISTED_ANCESTRAL_COLOUR_KIT),
            // "It can be dismantled at any time, returning both parts"; kit untradeable (Bounty Hunter).
            Ornament(Items.ELDER_CHAOS_TOP_OR, Items.ELDER_CHAOS_TOP, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.ELDER_CHAOS_ROBE_OR, Items.ELDER_CHAOS_ROBE, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.ELDER_CHAOS_HOOD_OR, Items.ELDER_CHAOS_HOOD, Items.ELDER_CHAOS_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // "the kit could be detached from the item"; kit untradeable (Bounty Hunter). Base robes are the 667 Dagon'hai robes.
            Ornament(Items.DAGONHAI_HAT_OR, Items.DAGONHAI_HAT, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.DAGONHAI_ROBE_TOP_OR, Items.DAGONHAI_ROBE_TOP, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // Heavy ballista (or): "It can be dismantled at any time, returning both parts"; untradeable Bounty Hunter kit (no PvP rule).
            Ornament(Items.HEAVY_BALLISTA_OR, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_ORNAMENT_KIT, pvpConvert = false),
            Ornament(Items.DAGONHAI_ROBE_BOTTOM_OR, Items.DAGONHAI_ROBE_BOTTOM, Items.DAGONHAI_ROBES_ORNAMENT_KIT, pvpConvert = false),
            // magegeara: "Elidinis' ward (f)" + "Menaphite ornament kit" (both untradeable); "Dismantle" separates them; on a PvP death
            // "the Menaphite ornament kit will be placed in their gravestone" (no base + kit drop to the PKer).
            Ornament(Items.ELIDINIS_WARD_OR, Items.ELIDINIS_WARD_F, Items.MENAPHITE_ORNAMENT_KIT, pvpConvert = false),
        )

    private val byOrnamented = ALL.associateBy { it.ornamented }

    fun forOrnamented(itemId: Int): Ornament? = byOrnamented[itemId]

    /** Ornaments whose PvP death drops the base item and the kit to the killer. */
    fun forPvpConversion(itemId: Int): Ornament? = byOrnamented[itemId]?.takeIf { it.pvpConvert }
}
