package gg.rsmod.plugins.content.areas.poh

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player

/**
 * The costume room's storage (owner 2026-09-21: "please search in the donor and port every poh thing we can port",
 * after "we are still missing some options in our poh ... make sure we have everything a max house has").
 *
 * Six containers, one per piece of costume-room furniture, each holding the set of items that piece accepts. An item
 * goes in through the furniture's own "Open" option, comes back out the same way, and never occupies inventory space
 * in between - which is the whole point of a costume room.
 *
 * WHERE THE ITEM LISTS COME FROM. They are 2009scape's `Storable` table
 * (`content/global/skill/construction/decoration/pohstorage/Storable.kt`), which is the donor's audited record of
 * what each piece of costume-room furniture accepts, read out mechanically rather than retyped: every
 * `Items.NAME_<id>` and every `(a..b step n)` range in a costume-room row, with the donor's three treasure-chest
 * tiers merged into one chest and its two cape racks into one rack, since this house always builds the top tier of
 * each. Every id was then checked against this server's own 667 item definitions - all 761 of them exist here, none
 * were dropped.
 *
 * The bookcase family is deliberately not ported: it stores quest books, which is a separate feature with its own
 * read/unread state, and the costume room does not contain a bookcase.
 */
object PohStorage {
    /** One container. [furniture] is the loc the player opens; [ids] is everything it accepts. */
    enum class Family(
        val displayName: String,
        val furniture: Int,
        val ids: Set<Int>,
    ) {
        CAPE_RACK(
            "Magic cape rack",
            18771,
            setOf(
                1052, 2412, 2413, 2414, 4315, 4317, 4319, 4321, 4323, 4325, 4327, 4329,
                4331, 4333, 4335, 4337, 4339, 4341, 4343, 4345, 4347, 4349, 4351, 4353,
                4355, 4357, 4359, 4361, 4363, 4365, 4367, 4369, 4371, 4373, 4375, 4377,
                4379, 4381, 4383, 4385, 4387, 4389, 4391, 4393, 4395, 4397, 4399, 4401,
                4403, 4405, 4407, 4409, 4411, 4413, 6568, 6570, 9747, 9748, 9749, 9750,
                9751, 9752, 9753, 9754, 9755, 9756, 9757, 9758, 9759, 9760, 9761, 9762,
                9763, 9764, 9765, 9766, 9767, 9768, 9769, 9770, 9771, 9772, 9773, 9774,
                9775, 9776, 9777, 9778, 9779, 9780, 9781, 9782, 9783, 9784, 9785, 9786,
                9787, 9788, 9789, 9790, 9791, 9792, 9793, 9794, 9795, 9796, 9797, 9798,
                9799, 9800, 9801, 9802, 9803, 9804, 9805, 9806, 9807, 9808, 9809, 9810,
                9811, 9812, 9813, 9814, 9948, 9949, 9950, 10638, 10639, 10640, 10641, 10642,
                10643, 10644, 10645, 10646, 10647, 10648, 10649, 10650, 10651, 10652, 10653, 10654,
                10655, 10656, 10657, 10658, 10659, 10660, 10661, 10662, 10663, 10664, 12169, 12170,
                12171, 12524,
            ),
        ),
        ARMOUR_CASE(
            "Armour case",
            18782,
            setOf(
                4068, 4069, 4070, 4071, 4072, 4298, 4300, 4302, 4304, 4306, 4308, 4310,
                4503, 4504, 4505, 4506, 4507, 4508, 4509, 4510, 4511, 4512, 5553, 5554,
                5555, 5574, 5575, 5576, 6065, 6066, 6069, 6070, 6128, 6129, 6130, 6131,
                6133, 6135, 6143, 6145, 6149, 6151, 6335, 6337, 6339, 6617, 6623, 6625,
                6627, 8839, 8840, 8842, 8952, 8953, 8954, 8955, 8956, 8957, 8958, 8959,
                8960, 8961, 8962, 8963, 8964, 8965, 8991, 8992, 8993, 8994, 8995, 8996,
                8997, 9672, 9674, 9676, 9678, 9944, 9945, 10035, 10037, 10039, 10041, 10043,
                10045, 10047, 10049, 10051, 10053, 10055, 10057, 10059, 10061, 10063, 10067, 10610,
                10611, 10612, 10613, 10614, 10615, 10616, 10617, 10618, 10619, 10620, 10621, 10622,
                10623, 10624, 10625, 10626, 10627, 10628, 10863, 10864, 10865, 10939, 10940, 10941,
                10945, 11135, 11274, 11663, 11664, 11665, 11674, 11675, 11676, 13181, 13182, 13183,
                13184, 13185, 13186, 13187, 14490, 14492, 14494,
            ),
        ),
        MAGIC_WARDROBE(
            "Magic wardrobe",
            18796,
            setOf(
                3385, 3387, 3389, 3391, 3393, 4089, 4091, 4093, 4095, 4097, 4099, 4101,
                4103, 4105, 4107, 4109, 4111, 4113, 4115, 4117, 6106, 6107, 6108, 6109,
                6110, 6111, 6137, 6139, 6141, 6147, 6153, 6916, 6918, 6920, 6922, 6924,
                9068, 9070, 9071, 9072, 9073, 9096, 9097, 9098, 9099, 9100, 9101, 9102,
                9104, 10601, 10602, 10603, 10604, 10605, 10606, 10607, 10608, 10609, 13614, 13615,
                13617, 13619, 13620, 13622, 13624, 13625, 13627, 13656, 13657, 13658, 14497, 14499,
                14501,
            ),
        ),
        TOY_BOX(
            "Toy box",
            18802,
            setOf(
                1037, 1419, 4079, 4566, 6722, 6856, 6857, 6858, 6859, 6860, 6861, 6862,
                6863, 6865, 6866, 6867, 7927, 9815, 9816, 9920, 9921, 9922, 9923, 9924,
                9925, 10507, 10508, 10722, 10723, 10724, 10725, 10726, 10727, 10728, 10729, 10730,
                10731, 10732, 10733, 10734, 10735, 11019, 11020, 11021, 11022, 11789, 11949, 12634,
                12645, 14076, 14077, 14078, 14079, 14081, 14088, 14537, 14570, 14595, 14596, 14600,
                14602, 14603, 14604, 14605,
            ),
        ),
        FANCY_DRESS(
            "Fancy dress box",
            18776,
            setOf(
                546, 548, 3057, 3058, 3059, 3060, 3061, 6180, 6181, 6182, 6184, 6185,
                6186, 6187, 6188, 6654, 6655, 6656, 7592, 7593, 7594, 7595, 7596, 10629,
                10630, 10631, 10632, 10633, 10634, 10721,
            ),
        ),
        TREASURE_CHEST(
            "Treasure chest",
            18808,
            setOf(
                2577, 2579, 2581, 2583, 2585, 2587, 2589, 2591, 2593, 2595, 2597, 2599,
                2601, 2603, 2605, 2607, 2609, 2611, 2613, 2615, 2617, 2619, 2621, 2623,
                2625, 2627, 2629, 2631, 2633, 2635, 2637, 2639, 2641, 2643, 2645, 2647,
                2649, 2651, 2653, 2655, 2657, 2659, 2661, 2663, 2665, 2667, 2669, 2671,
                2673, 2675, 3472, 3473, 3474, 3475, 3476, 3477, 3478, 3479, 3480, 3481,
                3483, 3485, 3486, 3488, 7319, 7321, 7323, 7325, 7327, 7332, 7334, 7336,
                7338, 7340, 7342, 7344, 7346, 7348, 7350, 7352, 7354, 7356, 7358, 7360,
                7362, 7364, 7366, 7368, 7370, 7372, 7374, 7376, 7378, 7380, 7382, 7384,
                7386, 7388, 7390, 7392, 7394, 7396, 7398, 7399, 7400, 10286, 10288, 10290,
                10292, 10294, 10296, 10298, 10300, 10302, 10304, 10306, 10308, 10310, 10312, 10314,
                10316, 10318, 10320, 10322, 10324, 10362, 10364, 10366, 10368, 10370, 10372, 10374,
                10376, 10378, 10380, 10382, 10384, 10386, 10388, 10390, 10392, 10394, 10396, 10398,
                10400, 10402, 10404, 10406, 10408, 10410, 10412, 10414, 10420, 10422, 10424, 10426,
                10428, 10430, 10432, 10434, 10436, 10438, 10440, 10442, 10444, 10446, 10448, 10450,
                10452, 10454, 10456, 10458, 10460, 10462, 10464, 10466, 10468, 10470, 10472, 10474,
                10665, 10666, 10667, 10668, 10669, 10670, 10671, 10672, 10673, 10674, 10675, 10676,
                10677, 10678, 10679, 10680, 10681, 10682, 10683, 10684, 10685, 10686, 10687, 10688,
                10689, 10690, 10691, 10692, 10693, 10694, 10695, 10696, 10697, 10698, 10699, 10700,
                10701, 10702, 10703, 10704, 10705, 10706, 10707, 10708, 10709, 10710, 10711, 10712,
                10713, 10714, 10715, 10716, 10717, 10718, 10719, 10736, 10738, 10740, 10742, 10744,
                10746, 10748, 10750, 10752, 10754, 10756, 10758, 10760, 10762, 10764, 10766, 10768,
                10770, 10772, 10774, 10776, 10778, 10780, 10782, 10784, 10786, 10788, 10790, 10792,
                10794, 10796, 10798, 10800, 10802, 10804, 10806, 11277, 11278, 11280, 11282, 13095,
                13097, 13099, 13101, 13103, 13105, 13107, 13109, 13111, 13113, 13115, 13163, 13164,
                13165, 13166, 13167, 13168, 13169, 13170, 13171, 13172, 13173,
            ),
        ),
        ;

        companion object {
            fun forFurniture(loc: Int): Family? = values().firstOrNull { it.furniture == loc }
        }
    }

    /**
     * The whole costume room in one persisted string: `FAMILY:id,id;FAMILY:id,id`. A string keeps the containers in
     * the ordinary attribute save pipeline (the same one the house style uses) instead of needing a new save format,
     * and a costume room holds tens of items, not thousands.
     */
    private val STORAGE_ATTR = AttributeKey<String>(persistenceKey = "poh_storage")

    private fun read(player: Player): MutableMap<Family, MutableList<Int>> {
        val map = Family.values().associateWith { mutableListOf<Int>() }.toMutableMap()
        val raw = player.attr[STORAGE_ATTR] ?: return map
        raw.split(';').forEach { part ->
            if (part.isBlank()) return@forEach
            val name = part.substringBefore(':')
            val family = Family.values().firstOrNull { it.name == name } ?: return@forEach
            part.substringAfter(':', "").split(',').forEach { id ->
                id.trim().toIntOrNull()?.let { map.getValue(family).add(it) }
            }
        }
        return map
    }

    private fun write(
        player: Player,
        map: Map<Family, List<Int>>,
    ) {
        player.attr[STORAGE_ATTR] =
            map.entries
                .filter { it.value.isNotEmpty() }
                .joinToString(";") { (family, ids) -> "${family.name}:${ids.joinToString(",")}" }
    }

    /** What [family] currently holds, in the order it was stored. */
    fun stored(
        player: Player,
        family: Family,
    ): List<Int> = read(player)[family].orEmpty()

    /** True when [family] accepts [itemId] at all. */
    fun accepts(
        family: Family,
        itemId: Int,
    ): Boolean = itemId in family.ids

    /** Puts [itemId] into [family] without touching the inventory; the caller removes the item. */
    fun put(
        player: Player,
        family: Family,
        itemId: Int,
    ) {
        val map = read(player)
        map.getValue(family).add(itemId)
        write(player, map)
    }

    /** Takes [itemId] out of [family] and reports whether it was there; the caller gives the item back. */
    fun take(
        player: Player,
        family: Family,
        itemId: Int,
    ): Boolean {
        val map = read(player)
        val removed = map.getValue(family).remove(itemId)
        if (removed) {
            write(player, map)
        }
        return removed
    }
}
