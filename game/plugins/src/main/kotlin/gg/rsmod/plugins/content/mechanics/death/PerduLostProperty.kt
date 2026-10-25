package gg.rsmod.plugins.content.mechanics.death

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items

/**
 * Perdu's OSRS "Lost Property" list (owner 2026-10-04: "exactly OSRS"; data agent-handoffs/perdu-death-data-2026-10-04.md, OSRS
 * Wiki "Perdu" / "Lost Property shop", fetched 2026-10-04). Perdu sells these back at the OSRS price to a player who has earned
 * the item once - "The colossal blade only has to be unlocked once; if lost in any situation, it can be re-obtained from Perdu
 * ... as long as you do not have another copy of this item" - and who has "none of the item on them, in their bank or in the
 * POH"; the [Entry.multiple] items are the wiki's exceptions that "can be purchased multiples of".
 *
 * "Earned once" is recorded the first time this server sees the item with the player ([observe]: on login, on death and when
 * Perdu is asked), so an item lost in any way afterwards can be bought back. Payment as OSRS: carried coins, then the bank.
 * The 200-coin achievement diary items are [DIARY_ITEMS] (owner Q14=A, 2026-10-06). Not ported: the items this cache lacks
 * (Book of the Dead, Chronicle, Arkan blade, ...); the POH costume room is not searched (this server stores no costume-room items).
 */
object PerduLostProperty {
    data class Entry(
        val itemId: Int,
        val price: Long,
        val multiple: Boolean = false,
        /** Every id that counts as owning this item (broken/locked forms, duplicates, charge states). */
        val variants: Set<Int> = setOf(itemId),
        /** An item Perdu takes as well (Ava's assembler: "Vorkath's head is required to purchase an assembler"). */
        val consumes: Int? = null,
    )

    /**
     * Owner Q14=A (2026-10-06): the achievement diary items, OSRS Wiki "Perdu" StoreLine list "Karamja gloves 200 | Ardougne cloak
     * 200 | Falador shield 200 | Fremennik sea boots 200 | ... | Explorer's ring 200 | ... | Varrock armour 200", and "Lost Property
     * shop" exceptions: "Achievement diary reward items" "can be purchased multiples of". The wiki does not say which tier Perdu
     * returns (UNSOURCED) and this server does not track diary completion, so every tier this cache has (1-4) is on the list; like
     * every Lost Property item, a tier is offered once the player has been seen with it ([observe]). Kandarin headgear, Desert
     * amulet, Morytania legs, Wilderness sword, Western banner and Rada's blessing are not in this cache.
     */
    const val DIARY_PRICE = 200L

    val DIARY_ITEMS: List<Int> =
        listOf(
            Items.KARAMJA_GLOVES_1, Items.KARAMJA_GLOVES_2, Items.KARAMJA_GLOVES_3, Items.KARAMJA_GLOVES_4,
            Items.ARDOUGNE_CLOAK_1, Items.ARDOUGNE_CLOAK_2, Items.ARDOUGNE_CLOAK_3, Items.ARDOUGNE_CLOAK_4,
            Items.FALADOR_SHIELD_1, Items.FALADOR_SHIELD_2, Items.FALADOR_SHIELD_3, Items.FALADOR_SHIELD_4,
            Items.FREMENNIK_SEA_BOOTS_1, Items.FREMENNIK_SEA_BOOTS_2, Items.FREMENNIK_SEA_BOOTS_3, Items.FREMENNIK_SEA_BOOTS_4,
            Items.EXPLORERS_RING_1, Items.EXPLORERS_RING_2, Items.EXPLORERS_RING_3, Items.EXPLORERS_RING_4,
            Items.VARROCK_ARMOUR_1, Items.VARROCK_ARMOUR_2, Items.VARROCK_ARMOUR_3, Items.VARROCK_ARMOUR_4,
        )

    val ENTRIES: List<Entry> =
        listOf(
            Entry(Items.ECTOPHIAL, 4_600, variants = setOf(Items.ECTOPHIAL, Items.ECTOPHIAL_4252)),
            Entry(Items.GUTHIX_CAPE, 23_000, multiple = true),
            Entry(Items.ZAMORAK_CAPE, 23_000, multiple = true),
            Entry(Items.SARADOMIN_CAPE, 23_000, multiple = true),
            Entry(Items.BOOK_OF_BALANCE, 12_000, multiple = true),
            Entry(Items.BOOK_OF_DARKNESS, 14_000, multiple = true),
            Entry(Items.BOOK_OF_LAW, 14_000, multiple = true),
            Entry(Items.BOOK_OF_WAR, 14_000, multiple = true),
            Entry(Items.HOLY_BOOK, 12_000, multiple = true),
            Entry(Items.UNHOLY_BOOK, 12_000, multiple = true),
            Entry(Items.GHOSTLY_HOOD, 15_000),
            Entry(Items.GHOSTLY_ROBE, 45_000),
            Entry(Items.GHOSTLY_ROBE_6108, 43_000),
            Entry(Items.GHOSTLY_GLOVES, 13_500),
            Entry(Items.GHOSTLY_BOOTS, 13_500, variants = setOf(Items.GHOSTLY_BOOTS, Items.GHOSTLY_BOOTS_10607)),
            Entry(Items.GHOSTLY_CLOAK, 15_000),
            Entry(Items.LUNAR_STAFF, 60_000),
            Entry(Items.LUNAR_HELM, 22_000),
            Entry(Items.LUNAR_TORSO, 140_000),
            Entry(Items.LUNAR_LEGS, 95_000),
            Entry(Items.LUNAR_GLOVES, 15_000),
            Entry(Items.LUNAR_BOOTS, 15_000),
            Entry(Items.LUNAR_CAPE, 18_000),
            Entry(Items.LUNAR_AMULET, 9_000),
            Entry(Items.LUNAR_RING, 8_000, multiple = true),
            Entry(Items.BARRELCHEST_ANCHOR, 290_000, variants = setOf(Items.BARRELCHEST_ANCHOR, Items.BARRELCHEST_ANCHOR_10888)),
            Entry(Items.BOOTS_OF_LIGHTNESS, 9_800, variants = setOf(Items.BOOTS_OF_LIGHTNESS, Items.BOOTS_OF_LIGHTNESS_89)),
            Entry(Items.CAPE_OF_LEGENDS, 1_900),
            Entry(Items.KERIS, 25_000),
            Entry(Items.BEARHEAD, 10_000, multiple = true),
            Entry(Items.BEACON_RING, 6_000, multiple = true),
            Entry(Items.COLOSSAL_BLADE, 300_000),
            Entry(Items.DWARVEN_HELMET, 115_000, multiple = true),
            Entry(Items.HELM_OF_NEITIZNOT, 95_000, multiple = true),
            Entry(Items.IVANDIS_FLAIL_30, 30_000, multiple = true, variants = (Items.IVANDIS_FLAIL_30..Items.IVANDIS_FLAIL_1).toSet()),
            Entry(Items.AVAS_ATTRACTOR, 7_000, multiple = true),
            Entry(Items.AVAS_ACCUMULATOR, 7_000, multiple = true),
            Entry(Items.AVAS_ASSEMBLER, 7_000, multiple = true, consumes = Items.VORKATHS_HEAD),
            Entry(Items.IMBUED_GUTHIX_CAPE, 250_000, variants = setOf(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_BROKEN, Items.IMBUED_GUTHIX_CAPE_L, Items.IMBUED_GUTHIX_CAPE_L_BROKEN, Items.IMBUED_GUTHIX_CAPE_L_MANGLED)),
            Entry(Items.IMBUED_ZAMORAK_CAPE, 250_000, variants = setOf(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_BROKEN, Items.IMBUED_ZAMORAK_CAPE_L, Items.IMBUED_ZAMORAK_CAPE_L_BROKEN, Items.IMBUED_ZAMORAK_CAPE_L_MANGLED)),
            Entry(Items.IMBUED_SARADOMIN_CAPE, 250_000, variants = setOf(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_BROKEN, Items.IMBUED_SARADOMIN_CAPE_L, Items.IMBUED_SARADOMIN_CAPE_L_BROKEN, Items.IMBUED_SARADOMIN_CAPE_L_MANGLED)),
            Entry(Items.DRAKANS_MEDALLION, 30_000, multiple = true),
            Entry(Items.THREAD_OF_ELIDINIS, 350_000),
        ) + DIARY_ITEMS.map { Entry(it, DIARY_PRICE, multiple = true) }

    private val byVariant: Map<Int, Entry> = ENTRIES.flatMap { e -> e.variants.map { it to e } }.toMap()

    /** Whether [itemId] (or a form of it) is on Perdu's own Lost Property list. */
    fun isLostPropertyItem(itemId: Int): Boolean = itemId in byVariant

    private val UNLOCKED = AttributeKey<String>(persistenceKey = "perdu_lost_property")

    fun unlocked(player: Player): Set<Int> = (player.attr[UNLOCKED] ?: "").split(',').mapNotNull { it.toIntOrNull() }.toSet()

    /** Records every Lost Property item the player has with them or in the bank as earned. */
    fun observe(player: Player) {
        val seen = unlocked(player).toMutableSet()
        val before = seen.size
        sequenceOf(player.inventory, player.equipment, player.bank).forEach { container ->
            container.rawItems.forEach { item -> item?.let { byVariant[it.toUnnoted(player.world.definitions).id]?.let { e -> seen += e.itemId } } }
        }
        if (seen.size != before) player.attr[UNLOCKED] = seen.sorted().joinToString(",")
        gg.rsmod.plugins.content.skills.summoning.Pets.observe(player)
    }

    /**
     * OSRS: "none of the item on them, in their bank or in the POH". N250 (owner 2026-10-24): the gravestone and Death's Office
     * hold the player's own items too; K01 deel 3 (2026-10-25): so do the looting bag, a familiar's pack and the Grand Exchange -
     * an item waiting anywhere is not lost, so Perdu does not sell a second copy of it.
     */
    fun owns(
        player: Player,
        entry: Entry,
    ): Boolean = whereOwned(player, entry) != null

    /** Where [player] still has [entry] ("your bank", "your looting bag" ...), or null when it is really lost. */
    fun whereOwned(
        player: Player,
        entry: Entry,
    ): String? = gg.rsmod.plugins.content.mechanics.ownership.Possessions.whereOwned(player, entry.variants)

    /**
     * N414 (owner 2026-10-25: "verloren pet gratis terug bij Perdu, een van elke pet", OSRS Probita): every pet the player had
     * once and has nowhere now, free; one of each, as the pet is owned again the moment it is handed over.
     */
    fun lostPets(player: Player): List<Entry> {
        val pets = gg.rsmod.plugins.content.skills.summoning.Pets
        return pets.lost(player).mapNotNull { pet ->
            pets.baseItem(pet)?.let { item -> Entry(item, 0L, variants = pets.variantsOf(pet).map { it.item }.toSet()) }
        }
    }

    /** Earned items Perdu does not offer because the player still has them somewhere (the reason his list is empty). */
    fun stillOwned(player: Player): List<Pair<Entry, String>> {
        val earned = unlocked(player)
        return ENTRIES.filter { it.itemId in earned && !it.multiple }.mapNotNull { e -> whereOwned(player, e)?.let { e to it } }
    }

    /** What Perdu offers [player] now: earned items they do not own (the [Entry.multiple] ones always). */
    fun available(player: Player): List<Entry> {
        val earned = unlocked(player)
        return ENTRIES.filter { it.itemId in earned && (it.multiple || !owns(player, it)) } + lostPets(player)
    }
}
