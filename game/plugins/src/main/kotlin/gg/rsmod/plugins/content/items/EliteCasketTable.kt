package gg.rsmod.plugins.content.items

/**
 * Owner answer Q9 (2026-09-14): the elite reward casket works like OSRS. OSRS Wiki "Reward casket (elite)" raw wikitext (2026-09-14):
 * "Elite clues generate a minimum of 4 rewards and a maximum of 6"; every table row's per-roll rarity is a product of slot fractions,
 * e.g. uniques `1/25 * 1/51`, the tuxedo pieces `1/25 * 1/51 * 1/10`, the mega-rare table `1/25 * 2/51 * 1/23` (gilded `* 5/23 * 1/11`,
 * 3rd age `* 1/23 * 1/17`), the standard rows `24/25 * 1/31` with their sub-slots (coins / purple sweets `7/20`, blessings `1/20`,
 * firelighters `1/5`, god pages `1/24`, keys `1/2`, seeds `1/3`, teleport scrolls `2/31 * 21/22 * 1/12`, master scroll book `2/31 * 1/22`).
 * This object is that slot tree verbatim; [itemProbabilities] recomputes every row's rarity (OsrsEliteCasketTests).
 *
 * Ids: the 667 item of the OSRS name (667 spellings "Third-age", "Rune bar", "Dragon bracelet", "Grimy ranarr", "... of a key"), the item
 * with the OSRS id where a name has several 667 copies, noted ids for "(noted)" rows; Purple sweets = the Treasure Trails item 10476 (wiki
 * infobox). BLOCKED rows keep their slot as nothing: Charge dragonstone jewellery scroll and Master scroll book (not on this server, earlier
 * owner-decided). Not in the rolls (separate rules): the 1/5 master clue (owner question 15), the 1/35 Mimic (owner answer Q10, pending),
 * scroll cases / heavy casket milestones (BLOCKED). SOURCE_GAP: how 4, 5 or 6 rolls are chosen (the wiki gives min 4, max 6, average 5; a
 * uniform choice is used).
 */
object EliteCasketTable {
    const val MIN_ROLLS = 4
    const val MAX_ROLLS = 6

    sealed class Entry(val slots: Int) {
        class Obj(val id: Int, val amount: IntRange, slots: Int = 1) : Entry(slots)

        class Table(val node: Node, slots: Int) : Entry(slots)

        class Nothing(slots: Int, val reason: String) : Entry(slots)
    }

    class Node(val name: String, val total: Int, val entries: List<Entry>) {
        init {
            check(entries.sumOf { it.slots } == total) { "$name: ${entries.sumOf { it.slots }} slots used of $total" }
        }
    }

    private fun obj(
        id: Int,
        amount: IntRange = 1..1,
        slots: Int = 1,
    ) = Entry.Obj(id, amount, slots)

    private fun objs(vararg ids: Int) = ids.map { obj(it) }

    val TUXEDO = Node("tuxedo", 10, objs(23468, 23474, 23472, 23470, 23476, 23524, 23530, 23528, 23526, 23532))

    val GILDED = Node("gilded", 11, objs(3486, 3481, 3483, 3485, 3488, 23498, 23488, 23500, 23624, 23626, 23628))

    val THIRD_AGE =
        Node("3rd age", 17, objs(10350, 10348, 10346, 23426, 10352, 10334, 10330, 10332, 10336, 10342, 10338, 10340, 10344, 23614, 23616, 23422, 23612))

    val MEGA_RARE =
        Node(
            "mega rare",
            23,
            objs(23694, 989, 23518) +
                listOf(obj(1392, 100..100), obj(23702, 30..30), obj(3025, 30..30), obj(6686, 30..30), obj(2445, 30..30)) +
                objs(23622, 23486, 23490, 23496, 23492, 23494, 23632, 23630, 23634) +
                listOf(Entry.Table(GILDED, 5), Entry.Table(THIRD_AGE, 1)),
        )

    val UNIQUES =
        Node(
            "elite uniques",
            51,
            objs(
                23654, 23650, 23652, 23656, 23664, 19333, 22858, 22860, 15509, 23578, 23576, 15507, 23550, 23554, 23552, 23450, 23452, 23454,
                23456, 23568, 23564, 23508, 19296, 19299, 19302, 19305, 23428, 23580, 23444, 23514, 23536, 23562, 23596, 23636, 23638, 23464,
                23458, 23478, 23430, 23446, 13101, 23538, 23640, 23582, 23566, 23598, 23484, 23482,
            ) + listOf(Entry.Table(TUXEDO, 1), Entry.Table(MEGA_RARE, 2)),
        )

    val KEY_HALVES = Node("key halves", 2, objs(985, 987))

    val SEEDS = Node("seeds", 3, objs(5289, 5315, 5316))

    val COINS_SWEETS_BLESSINGS =
        Node("coins, sweets, blessings", 20, listOf(obj(995, 10000..15000, 7), obj(10476, 8..12, 7)) + objs(23600, 23602, 23604, 23608, 23606, 23610))

    val FIRELIGHTERS = Node("firelighters", 5, listOf(7329, 7330, 7331, 10326, 10327).map { obj(it, 9..15) })

    val GOD_PAGES =
        Node(
            "god pages",
            24,
            objs(3827, 3828, 3829, 3830, 3831, 3832, 3833, 3834, 3835, 3836, 3837, 3838, 19600, 19601, 19602, 19603, 19604, 19605, 19606, 19607, 19608, 19609, 19610, 19611),
        )

    val TELEPORT_SCROLLS =
        Node(
            "teleport scrolls",
            12,
            listOf(Entry.Nothing(1, "Charge dragonstone jewellery scroll: not on this server")) +
                listOf(19475, 23688, 23684, 23682, 23683, 23681, 23686, 23685, 19479, 23689, 23687).map { obj(it, 5..15) },
        )

    val SCROLLS_OR_BOOK =
        Node("teleport scrolls or master scroll book", 22, listOf(Entry.Table(TELEPORT_SCROLLS, 21), Entry.Nothing(1, "Master scroll book (empty): not on this server")))

    val STANDARD =
        Node(
            "standard",
            31,
            objs(1127, 1079, 1093, 1201, 9185, 1215, 1434, 1305) +
                listOf(obj(9194, 8..12), obj(563, 50..75), obj(560, 50..75), obj(565, 50..75), obj(566, 50..75)) +
                objs(11115, 1664, 1645) +
                listOf(obj(7061, 15..20), obj(7219, 15..20), obj(8779, 60..80), obj(8781, 40..50), obj(8783, 20..30), obj(2364, 1..3)) +
                listOf(obj(995, 20000..30000), obj(10476, 9..23)) +
                listOf(
                    Entry.Table(KEY_HALVES, 1), Entry.Table(SEEDS, 1), Entry.Table(COINS_SWEETS_BLESSINGS, 1), Entry.Table(FIRELIGHTERS, 1),
                    Entry.Table(GOD_PAGES, 1), Entry.Table(SCROLLS_OR_BOOK, 2),
                ),
        )

    /** One reward roll: 1/25 the elite unique table, otherwise the standard table. */
    val ROLL = Node("elite reward roll", 25, listOf(Entry.Table(UNIQUES, 1), Entry.Table(STANDARD, 24)))

    /** Per-roll probability of each (item id, amount range) row, summed where a row appears twice. */
    fun itemProbabilities(): Map<Pair<Int, IntRange>, Double> {
        val out = LinkedHashMap<Pair<Int, IntRange>, Double>()
        fun walk(
            node: Node,
            chance: Double,
        ) {
            node.entries.forEach { entry ->
                val p = chance * entry.slots / node.total
                when (entry) {
                    is Entry.Obj -> out.merge(entry.id to entry.amount, p, Double::plus)
                    is Entry.Table -> walk(entry.node, p)
                    is Entry.Nothing -> Unit
                }
            }
        }
        walk(ROLL, 1.0)
        return out
    }
}
