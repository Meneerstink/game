package gg.rsmod.plugins.content.mechanics.pvp.breach

import gg.rsmod.plugins.api.cfg.Items
import java.util.Random

/**
 * Breach monster loot, OSRS Wiki template "DeadmanAnnihilationBreachDropTable" (the table every breach monster page transcludes):
 * "Drops are received by the first 16 players to deal damage to a breach monster. There is a tertiary roll for rare drops, then two
 * rolls for non-unique drops. After both regular drops are decided, there is a 50% chance that one of them is replaced by chitin."
 * The permanent world matches ("Each breach monster now drops 1 Chitin per kill. There's a 50% chance to receive a second regular
 * supply drop instead of a Chitin.", runescapeguides.com World 345 breaches).
 *
 * Owner exclusions (Deadman product decision): the Trinket of advanced weaponry only turns into a corrupted weapon, which this server
 * does not have - its slot in the rare table gives nothing. Archaic emblems are Deadman: Annihilation skull-shop points, which the
 * permanent world (and this server) does not have. [ItemIds] holds the ids of the items imported for this table.
 */
object BreachLoot {
    const val ELIGIBLE = 16

    /** "a roughly 1/72 chance for eligible players to roll this table": the listed x/9576 weights sum to 133. */
    const val RARE_DENOMINATOR = 9576

    /** "Within the trinkets and weapons table, there is a roughly 1/92 chance to receive a piece of equipment." */
    const val MEGA_RARE_CHANCE = 92

    /** Wrath rune, local 23743 (OsrsItemImportTool batch "runes", 2026-09-17). */
    private const val WRATH_RUNE = 23743

    const val REGULAR_ROLLS = 2
    const val REGULAR_TOTAL = 351

    class Drop(
        val item: Int,
        val amount: Int,
        val noted: Boolean = false,
    )

    class Line(
        val item: Int,
        val min: Int,
        val max: Int,
        val weight: Int,
        val noted: Boolean = false,
    )

    /** -1 = the Trinket of advanced weaponry slot (nothing, see the class comment). */
    val RARE: List<Line> =
        listOf(
            Line(-1, 1, 1, 60),
            Line(ItemIds.TRINKET_OF_FAIRIES, 1, 1, 20),
            Line(ItemIds.TRINKET_OF_AVARICE, 1, 1, 10),
            Line(ItemIds.TRINKET_OF_UNDEAD, 1, 1, 10),
            Line(Items.MORRIGANS_THROWING_AXE, 100, 100, 10),
            Line(Items.MORRIGANS_JAVELIN, 100, 100, 10),
            Line(Items.STATIUSS_WARHAMMER, 1, 1, 3),
            Line(Items.ZURIELS_STAFF, 1, 1, 3),
            Line(Items.VESTAS_SPEAR, 1, 1, 3),
            Line(Items.VESTAS_LONGSWORD, 1, 1, 3),
            Line(ItemIds.TRINKET_OF_FORTUITY_INACTIVE, 1, 1, 1),
        )

    /** Equal 1/145728 each. OSRS d'hide bodies are the rev-667 "Armadyl body" / "Saradomin body" / "Zamorak body". */
    val MEGA_RARE: List<Int> =
        listOf(
            Items.ANCIENT_STAFF, Items.MASTER_WAND, Items.MAGES_BOOK,
            Items.STATIUSS_FULL_HELM, Items.STATIUSS_PLATEBODY, Items.STATIUSS_PLATELEGS,
            Items.VESTAS_CHAINBODY, Items.VESTAS_PLATESKIRT,
            Items.ZURIELS_HOOD, Items.ZURIELS_ROBE_TOP, Items.ZURIELS_ROBE_BOTTOM,
            Items.MORRIGANS_COIF, Items.MORRIGANS_LEATHER_BODY, Items.MORRIGANS_LEATHER_CHAPS,
            Items.MYSTIC_ROBE_TOP, Items.MYSTIC_ROBE_BOTTOM,
            Items.ARMADYL_BODY, Items.ARMADYL_CHAPS, Items.SARADOMIN_BODY, Items.SARADOMIN_CHAPS, Items.ZAMORAK_BODY, Items.ZAMORAK_CHAPS,
        )

    val REGULAR: List<Line> =
        listOf(
            // Runes and ammunition
            Line(Items.NATURE_RUNE, 100, 200, 18),
            Line(Items.CANNONBALL, 150, 200, 10),
            Line(Items.DRAGON_BOLTS_E, 30, 80, 10),
            Line(Items.DEATH_RUNE, 300, 500, 10),
            Line(Items.CANNONBALL, 201, 300, 8),
            Line(Items.LAW_RUNE, 100, 200, 8),
            Line(Items.CANNONBALL, 100, 149, 6),
            Line(Items.BLOOD_RUNE, 300, 500, 6),
            Line(WRATH_RUNE, 300, 500, 5),
            Line(Items.BLIGHTED_ANCIENT_ICE_SACK, 150, 350, 5),
            Line(Items.CHAOS_RUNE, 100, 200, 4),
            // Weapons and armour
            Line(Items.RUNE_SCIMITAR, 1, 1, 6),
            Line(Items.RUNE_PLATELEGS, 1, 1, 6),
            Line(Items.RUNE_PLATESKIRT, 1, 1, 6),
            Line(Items.RUNE_MED_HELM, 1, 1, 6),
            Line(Items.RUNE_FULL_HELM, 1, 1, 6),
            Line(Items.RUNE_CHAINBODY, 1, 1, 6),
            Line(Items.RUNE_PLATEBODY, 1, 1, 5),
            Line(Items.RUNE_KITESHIELD, 1, 1, 4),
            // Food
            Line(Items.SHARK, 20, 40, 22, noted = true),
            Line(Items.MANTA_RAY, 20, 40, 20, noted = true),
            Line(Items.COOKED_KARAMBWAN, 20, 40, 8, noted = true),
            // Potions
            Line(Items.SARADOMIN_BREW_4, 8, 16, 9, noted = true),
            Line(Items.SARADOMIN_BREW_4, 3, 6, 8, noted = true),
            Line(Items.PRAYER_POTION_4, 3, 16, 8, noted = true),
            Line(Items.SUPER_COMBAT_POTION_4, 8, 16, 6, noted = true),
            Line(Items.SUPER_COMBAT_POTION_4, 3, 6, 6, noted = true),
            Line(Items.SUPER_RESTORE_4, 8, 16, 6, noted = true),
            Line(Items.RANGING_POTION_4, 8, 16, 6, noted = true),
            Line(Items.RANGING_POTION_4, 3, 6, 2, noted = true),
            // Herbs
            Line(Items.CLEAN_TORSTOL, 10, 20, 11, noted = true),
            Line(Items.CLEAN_CADANTINE, 10, 20, 6, noted = true),
            Line(Items.CLEAN_DWARF_WEED, 10, 20, 6, noted = true),
            Line(Items.CLEAN_LANTADYME, 10, 20, 6, noted = true),
            Line(Items.CLEAN_RANARR, 10, 20, 6, noted = true),
            Line(Items.CLEAN_SNAPDRAGON, 10, 20, 6, noted = true),
            Line(Items.CLEAN_TOADFLAX, 10, 20, 4, noted = true),
            Line(Items.CLEAN_AVANTOE, 10, 20, 2, noted = true),
            Line(Items.CLEAN_IRIT, 10, 20, 2, noted = true),
            Line(Items.CLEAN_KWUARM, 10, 20, 2, noted = true),
            // Secondaries
            Line(Items.BLUE_DRAGON_SCALE, 36, 40, 6, noted = true),
            Line(Items.POTATO_CACTUS, 36, 40, 6, noted = true),
            Line(Items.RED_SPIDERS_EGGS, 36, 40, 6, noted = true),
            Line(Items.SNAPE_GRASS, 36, 40, 6, noted = true),
            Line(Items.WHITE_BERRIES, 36, 40, 6, noted = true),
            Line(Items.WINE_OF_ZAMORAK, 36, 40, 6, noted = true),
            Line(Items.CRUSHED_NEST, 36, 40, 4, noted = true),
            Line(Items.LIMPWURT_ROOT, 36, 40, 2, noted = true),
            Line(Items.MORT_MYRE_FUNGUS, 36, 40, 2, noted = true),
            Line(Items.UNICORN_HORN, 36, 40, 2, noted = true),
            // Other
            Line(Items.COINS_995, 9000, 11000, 6),
            Line(Items.UNCUT_DIAMOND, 15, 30, 6, noted = true),
            Line(Items.UNCUT_RUBY, 15, 30, 2, noted = true),
            Line(Items.UNCUT_EMERALD, 15, 30, 2, noted = true),
            Line(Items.UNCUT_SAPPHIRE, 15, 30, 2, noted = true),
        )

    val RARE_TOTAL: Int = RARE.sumOf { it.weight }

    private fun pick(
        lines: List<Line>,
        random: Random,
    ): Line {
        var r = random.nextInt(lines.sumOf { it.weight })
        for (line in lines) {
            r -= line.weight
            if (r < 0) return line
        }
        return lines.last()
    }

    private fun Line.drop(random: Random) = Drop(item, min + random.nextInt(max - min + 1), noted)

    /** One eligible player's loot for one kill. */
    fun roll(random: Random): List<Drop> {
        val out = ArrayList<Drop>()
        if (random.nextInt(RARE_DENOMINATOR) < RARE_TOTAL) {
            if (random.nextInt(MEGA_RARE_CHANCE) == 0) {
                out += Drop(MEGA_RARE[random.nextInt(MEGA_RARE.size)], 1)
            } else {
                val line = pick(RARE, random)
                if (line.item != -1) out += line.drop(random)
            }
        }
        val regular = MutableList(REGULAR_ROLLS) { pick(REGULAR, random).drop(random) }
        if (random.nextBoolean()) regular[random.nextInt(REGULAR_ROLLS)] = Drop(ItemIds.CHITIN, 1, noted = true)
        out += regular
        return out
    }
}
