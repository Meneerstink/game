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
 * Not ported: the 200-coin achievement diary items (the wiki does not say which tier Perdu returns) and the items this cache
 * lacks (Book of the Dead, Chronicle, Arkan blade, ...); the POH costume room is not searched (this server stores no
 * costume-room items).
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
            Entry(Items.IMBUED_GUTHIX_CAPE, 250_000, variants = setOf(Items.IMBUED_GUTHIX_CAPE, Items.IMBUED_GUTHIX_CAPE_BROKEN, Items.IMBUED_GUTHIX_CAPE_L)),
            Entry(Items.IMBUED_ZAMORAK_CAPE, 250_000, variants = setOf(Items.IMBUED_ZAMORAK_CAPE, Items.IMBUED_ZAMORAK_CAPE_BROKEN, Items.IMBUED_ZAMORAK_CAPE_L)),
            Entry(Items.IMBUED_SARADOMIN_CAPE, 250_000, variants = setOf(Items.IMBUED_SARADOMIN_CAPE, Items.IMBUED_SARADOMIN_CAPE_BROKEN, Items.IMBUED_SARADOMIN_CAPE_L)),
            Entry(Items.DRAKANS_MEDALLION, 30_000, multiple = true),
            Entry(Items.THREAD_OF_ELIDINIS, 350_000),
        )

    private val byVariant: Map<Int, Entry> = ENTRIES.flatMap { e -> e.variants.map { it to e } }.toMap()

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
    }

    /** OSRS: "none of the item on them, in their bank or in the POH". */
    fun owns(
        player: Player,
        entry: Entry,
    ): Boolean =
        sequenceOf(player.inventory, player.equipment, player.bank).any { container ->
            container.rawItems.any { it != null && it.toUnnoted(player.world.definitions).id in entry.variants }
        }

    /** What Perdu offers [player] now: earned items they do not own (the [Entry.multiple] ones always). */
    fun available(player: Player): List<Entry> {
        val earned = unlocked(player)
        return ENTRIES.filter { it.itemId in earned && (it.multiple || !owns(player, it)) }
    }
}
