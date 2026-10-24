package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS-IMPORT batch capes - max cape variants (OSRS Wiki raw wikitext 2026-09-14: "Imbued Saradomin max cape", "Assembler max cape",
 * "Masori assembler", "Masori assembler max cape", "Dizana's max cape"):
 * - "the result of combining [the item] with a max cape, during which the max hood required to be in the player's inventory will
 *   automatically convert to the [variant] max hood" (Recipe: 0 ticks, no skill; the Masori recipes list "tools = Needle").
 * - "The max cape and [item] can be separated by using a knife on the cape."
 * - "does not have the max cape's stats or perks; it only acts as a cosmetic upgrade to [the item]" - the item's own effects apply
 *   ([gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices.ASSEMBLERS], [DizanasQuiver.BLESSED]).
 * - Masori assembler: Ava's assembler + Masori crafting kit with a needle; "A needle is required in order to create and dismantle";
 *   Masori assembler max cape also from "a Masori crafting kit on an Assembler max cape".
 * The 667 Max cape / Max hood are 20767 / 20768 (20747 and 20751 are the unwearable copies).
 * ADAPTED: messages; the knife returns the plain max hood when the variant hood is carried (the page states the cape split only).
 * ADJACENT GAP (not built): the max cape "exceptions when worn" (Cooks' Guild / Crafting Guild access, essence pouch protection,
 * warm clothing, Skill Cape emote) - the 667 guild doors here do not honour skillcapes either.
 */
object MaxCapes {
    data class Variant(
        val component: Int,
        val cape: Int,
        val hood: Int,
        val tool: Int? = null,
    )

    const val MAX_CAPE = Items.MAX_CAPE_20767
    const val MAX_HOOD = Items.MAX_HOOD_20768

    val VARIANTS =
        listOf(
            Variant(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_MAX_CAPE, Items.IMBUED_SARADOMIN_MAX_HOOD),
            Variant(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_MAX_CAPE, Items.IMBUED_ZAMORAK_MAX_HOOD),
            Variant(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_MAX_CAPE, Items.IMBUED_GUTHIX_MAX_HOOD),
            Variant(Items.AVAS_ASSEMBLER, Items.ASSEMBLER_MAX_CAPE, Items.ASSEMBLER_MAX_HOOD),
            Variant(Items.MASORI_ASSEMBLER, Items.MASORI_ASSEMBLER_MAX_CAPE, Items.MASORI_ASSEMBLER_MAX_HOOD, tool = Items.NEEDLE),
            Variant(Items.BLESSED_DIZANAS_QUIVER, Items.DIZANAS_MAX_CAPE, Items.DIZANAS_MAX_HOOD),
            // Night run 2026-09-19 (OSRS Wiki "Fire max cape", "Infernal max cape", "Accumulator max cape"): the same max cape +
            // component recipe and knife split; imported in tx-20260919-023421 (OSRS 13329/13330, 21285/21282, 13337/13338).
            Variant(Items.FIRE_CAPE, Items.FIRE_MAX_CAPE, Items.FIRE_MAX_HOOD),
            Variant(Items.INFERNAL_CAPE, Items.INFERNAL_MAX_CAPE, Items.INFERNAL_MAX_HOOD),
            Variant(Items.AVAS_ACCUMULATOR, Items.ACCUMULATOR_MAX_CAPE, Items.ACCUMULATOR_MAX_HOOD),
        )

    /** Every wearable max cape variant: the combined capes and their (l) versions (the broken ones cannot be worn). */
    val WEARABLE_VARIANTS: Set<Int> by lazy {
        VARIANTS.map { it.cape }.toSet() +
            setOf(
                Items.IMBUED_SARADOMIN_MAX_CAPE_L, Items.IMBUED_ZAMORAK_MAX_CAPE_L, Items.IMBUED_GUTHIX_MAX_CAPE_L,
                Items.ASSEMBLER_MAX_CAPE_L, Items.MASORI_ASSEMBLER_MAX_CAPE_L, Items.DIZANAS_MAX_CAPE_L, Items.INFERNAL_MAX_CAPE_L,
            )
    }

    /*
     * Owner 2026-10-03 (buglist E3): "a cape on the max cape combines, but without the OSRS message, sound and dialogue". The OSRS Wiki
     * ("Max cape", the variant pages, "Max hood", "Mac"; raw wikitext 2026-10-03) quotes no combine dialogue or message, so the
     * wording is ADAPTED: the shared item warning GUI (interface 94, as every ornament kit attach here), a chat line and the
     * item dialogue, built only on what the pages state - the hood converts with the cape, and "the max cape and [item] can be
     * separated by using a knife on the cape". Sound: ItemAttachSounds (attach on combine, detach on the knife split).
     */
    const val COMBINE_DETAIL = "Your max hood will change with it. A knife on the cape separates them again."

    /** "Infernal cape" -> "infernal cape", "Imbued Saradomin cape" -> "imbued Saradomin cape" (proper nouns keep their capital). */
    fun lower(name: String): String = name.replaceFirstChar { it.lowercaseChar() }

    fun combineQuestion(componentName: String): String = "Are you sure you want to combine your max cape with the ${lower(componentName)}?"

    fun combinedMessage(componentName: String): String = "You combine your max cape with the ${lower(componentName)}."

    fun separatedMessage(componentName: String): String = "You separate the ${lower(componentName)} from your max cape."

    fun forComponent(itemId: Int): Variant? = VARIANTS.firstOrNull { it.component == itemId }

    fun forCape(itemId: Int): Variant? = VARIANTS.firstOrNull { it.cape == itemId }
}
