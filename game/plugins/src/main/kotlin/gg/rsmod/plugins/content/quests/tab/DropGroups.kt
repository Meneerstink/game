package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.plugins.api.cfg.Items

/**
 * N279 (owner 2026-10-24, design B5 GOEDGEKEURD): the Drop Viewer's lines in groups with a heading, like the OSRS Wiki drop tables -
 * Pets, Charms, Uniques, Weapons and armour, Runes and ammo, Materials, Herbs and seeds, Food and potions, Coins and other, Extra
 * rolls, Drop tables; empty groups are left out. Inside a group the rarest line comes first (the NORMAL chance, so the order never
 * moves with the bracket), lines with the same chance keep the same item together; the charms keep their fixed order.
 *
 * A line's group is the item's own category, read from its definition: a wield/wear option or an equipment slot = equipment
 * (the ammo slot = ammo), a rune, an Eat/Drink option, a herb or a seed, a material by its name ([MATERIAL_WORDS]); what fits
 * none of them is "Coins and other". The few items whose name or options mislead are in [OVERRIDES] (tested).
 */
object DropGroups {
    enum class Group(val title: String) {
        /** Only when the Always items do not fit the Always bar (DropViewer.alwaysInBar): then they head the table, like the Wiki. */
        ALWAYS("Always"),
        PETS("Pets"),
        CHARMS("Charms"),
        UNIQUES("Uniques"),
        GEAR("Weapons and armour"),
        RUNES("Runes and ammo"),
        MATERIALS("Materials"),
        HERBS("Herbs and seeds"),
        FOOD("Food and potions"),
        OTHER("Coins and other"),
        EXTRA("Extra rolls"),
        TABLES("Drop tables"),
    }

    /** Summoning charms in the Wiki's order: gold, green, crimson, blue. */
    val CHARMS = listOf(Items.GOLD_CHARM, Items.GREEN_CHARM, Items.CRIMSON_CHARM, Items.BLUE_CHARM)

    /** The ammo slot (667 equipment slot 13). */
    private const val AMMO_SLOT = 13

    private val EQUIP_OPTIONS = setOf("wield", "wear", "equip")

    /** Thrown and fired ammunition by name (a 667 cache item has no slot of its own; the server's metadata may not be loaded). */
    private val AMMO_WORDS = listOf(" arrow", " arrows", " bolts", " bolt", " dart", " darts", " javelin", " knife", " knives", " thrownaxe", "bolt rack", "brutal")

    private val HERBS =
        setOf(
            "guam leaf", "marrentill", "tarromin", "harralander", "ranarr weed", "toadflax", "irit leaf", "avantoe", "kwuarm", "snapdragon",
            "cadantine", "lantadyme", "dwarf weed", "torstol", "spirit weed", "wergali", "fellstalk",
        )

    /** Name endings of crafting and skilling materials (ores, bars, logs, hides, leather, planks, bones, arrowtips, unstrung bows ...). */
    private val MATERIAL_ENDINGS =
        listOf(
            " ore", " bar", "logs", "hide", "dragonhide", " plank", "essence", "coal", "bones", " scale", " shards", "flax", "bowstring",
            "bow string", "feather", "feathers", "molten glass", "clay", "limestone", "nails", "granite", "sandstone", "silk", "wool", "ashes",
            "fur", "leather", "arrowtips", "arrowheads", "bolt tips", "limbs", " (u)", "seaweed", "soda ash", "bucket of sand", "oyster pearl",
            "oyster pearls", "bark", "tortoise shell", "papyrus", "swamp tar",
        )

    /** Herblore secondaries: with the herbs (owner: what belongs together stands together). */
    private val HERB_SECONDARIES =
        setOf(
            "limpwurt root", "snape grass", "eye of newt", "red spiders' eggs", "unicorn horn", "unicorn horn dust", "white berries",
            "potato cactus", "cactus spine", "desert goat horn", "goat horn dust", "crushed nest", "bird nest", "mort myre fungus",
            "wine of zamorak", "jangerberries", "toad's legs", "volcanic ash", "dragon scale dust", "chocolate dust", "kebbit teeth dust",
            "wolfbane", "ground mud runes", "nail beast nails", "poison ivy berries", "lava scale shard", "zulrah's scales",
        )

    /** Cooking ingredients: with the food. */
    private val FOOD_INGREDIENTS = setOf("grain", "pot of flour", "egg", "bread dough", "pastry dough", "pizza base", "grapes", "coconut", "bucket of milk", "cheese", "tomato", "onion", "cabbage", "potato")

    /** Name starts of materials: uncut gems. */
    private val MATERIAL_STARTS = listOf("uncut ")

    private val GEMS = setOf("sapphire", "emerald", "ruby", "diamond", "dragonstone", "onyx", "opal", "jade", "red topaz", "zenyte")

    /**
     * Items whose definition says something else than what they are (doubtful cases, owner: "in een vaste lijst, met test"), by
     * lowercase name: keys and key halves are "Coins and other" (no category), a battlestaff is a weapon, a few names hold a
     * material word but are not one.
     */
    val OVERRIDES: Map<String, Group> =
        mapOf(
            "loop half of a key" to Group.OTHER,
            "tooth half of a key" to Group.OTHER,
            "shield left half" to Group.OTHER,
            "dragon spear" to Group.GEAR,
            "battlestaff" to Group.MATERIALS,
            "mud battlestaff" to Group.GEAR,
            "steam battlestaff" to Group.GEAR,
            "lava battlestaff" to Group.GEAR,
            "air orb" to Group.MATERIALS,
            "water orb" to Group.MATERIALS,
            "earth orb" to Group.MATERIALS,
            "fire orb" to Group.MATERIALS,
            "unpowered orb" to Group.MATERIALS,
            "mole skin" to Group.MATERIALS,
            "mole claw" to Group.MATERIALS,
            "rune essence" to Group.MATERIALS,
            "pure essence" to Group.MATERIALS,
            "coins" to Group.OTHER,
            "clue scroll (easy)" to Group.OTHER,
            "clue scroll (medium)" to Group.OTHER,
            "clue scroll (hard)" to Group.OTHER,
            "clue scroll (elite)" to Group.OTHER,
            "kbd heads" to Group.OTHER,
            "kq head" to Group.OTHER,
            "cannonball" to Group.RUNES,
            "weapon poison" to Group.FOOD,
        )

    /** The real item behind a note, and its lowercase name. */
    private fun def(world: World, id: Int): ItemDef? {
        val d = world.definitions.getNullable(ItemDef::class.java, id) ?: return null
        return if (d.noted && d.noteLinkId > 0) world.definitions.getNullable(ItemDef::class.java, d.noteLinkId) ?: d else d
    }

    /** The item's own category ([Group.GEAR] .. [Group.OTHER]). */
    fun category(world: World, id: Int): Group {
        val d = def(world, id) ?: return Group.OTHER
        val name = d.name.lowercase().trim()
        OVERRIDES[name]?.let { return it }
        if (id in CHARMS || d.id in CHARMS) return Group.CHARMS
        val options = d.inventoryMenu.filterNotNull().map { it.lowercase() }
        val wearable = d.equipSlot >= 0 || options.any { it in EQUIP_OPTIONS }
        val ammo = d.equipSlot == AMMO_SLOT || AMMO_WORDS.any { name.endsWith(it) || name.contains("$it ") || name.contains("$it(") }
        if (name.endsWith(" rune") || name.endsWith(" runes")) return Group.RUNES
        if (ammo && (wearable || d.stackable)) return Group.RUNES
        if (wearable) return Group.GEAR
        // Raw fish and meat sit with the food they cook into.
        if (options.any { it == "eat" || it == "drink" } || name.startsWith("raw ")) return Group.FOOD
        if (name in FOOD_INGREDIENTS) return Group.FOOD
        // 667 names the clean herbs "Clean ranarr", "Clean irit" ...
        if (name.startsWith("grimy ") || name.startsWith("clean ") || name in HERBS || name in HERB_SECONDARIES || name.endsWith(" seed") || name.endsWith(" seeds") || name.endsWith(" spore")) return Group.HERBS
        if (name in GEMS || MATERIAL_ENDINGS.any { name.endsWith(it) } || MATERIAL_STARTS.any { name.startsWith(it) }) return Group.MATERIALS
        return Group.OTHER
    }

    /** The extra rolls on top of the drop table: the Deadman emblem, the prayer scrolls, the Archaic emblem. */
    fun isExtraRoll(line: DropSources.Line): Boolean = line.key == "emblem" || line.key == "archaic" || line.key.startsWith("scroll")

    /** The group of a top-level line; [uniques] = the source's collection log items (a boss's uniques). */
    fun groupOf(world: World, line: DropSources.Line, uniques: Set<Int>): Group =
        when {
            line.tag == DropSources.Tag.PET -> Group.PETS
            line.tag == DropSources.Tag.TABLE -> Group.TABLES
            isExtraRoll(line) -> Group.EXTRA
            line.item in CHARMS -> Group.CHARMS
            DropSources.picture(world, line.item) in uniques -> Group.UNIQUES
            else -> category(world, line.item)
        }

    /** A chance rounded so two lines with the same weight compare equal (floating point sums). */
    private fun key(p: Double): Double = if (p <= 0.0) Double.MAX_VALUE else Math.round(1.0 / p * 1e6) / 1e6

    /** Rarest first on the normal chance, the same item together, then the smaller quantity first. */
    val RAREST_FIRST: Comparator<DropSources.Line> =
        compareByDescending<DropSources.Line> { key(it.normal) }.thenBy { it.name.lowercase() }.thenBy { DropSources.averageQuantity(it.quantity) }.thenBy { it.key }

    fun sortGroup(group: Group, lines: List<DropSources.Line>): List<DropSources.Line> =
        if (group == Group.CHARMS) {
            lines.sortedWith(compareBy<DropSources.Line> { CHARMS.indexOf(it.item).let { i -> if (i < 0) CHARMS.size else i } }.then(RAREST_FIRST))
        } else {
            lines.sortedWith(RAREST_FIRST)
        }

    /** The lines in their groups, in the groups' order, empty groups left out. */
    fun grouped(world: World, lines: List<DropSources.Line>, uniques: Set<Int>): List<Pair<Group, List<DropSources.Line>>> {
        val by = lines.groupBy { groupOf(world, it, uniques) }
        return Group.values().mapNotNull { g -> by[g]?.takeIf { it.isNotEmpty() }?.let { g to sortGroup(g, it) } }
    }
}
