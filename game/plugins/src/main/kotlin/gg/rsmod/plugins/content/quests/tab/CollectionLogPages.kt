package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.EliteCasketTable
import gg.rsmod.plugins.content.mechanics.prayer.PrayerScrolls
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachIds
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import gg.rsmod.plugins.content.skills.summoning.Pets

/**
 * The Collection Log's pages (N104, owner "net als OSRS", GOEDGEKEURD 04): categories Bosses, Deadman, Clues, Minigames, Other;
 * per page the OSRS log items that really drop HERE - a list item that no reward on this server gives is left out, so every
 * page lists only what can be obtained (owner: "alleen onderdelen die op onze server bestaan").
 *
 *  - Bosses: per boss of the Drop Viewer's Bosses group, the OSRS Collection log list ([OSRS]) matched by name against what that
 *    boss can drop (Drop Viewer lines, pets included); a boss OSRS has no page for gets its pet and its rare lines (1/100 or rarer,
 *    not from a shared table). Dagannoth Kings are one page like OSRS. Barrows: [Barrows.COLLECTION_LOG_ITEMS].
 *  - Deadman: the Deadman's chest list, the breach uniques + Archaic emblem.
 *  - Clues: the elite casket's uniques (1/1,000 per roll or rarer, [EliteCasketTable.itemProbabilities]).
 *  - Other: every pet.
 */
object CollectionLogPages {
    enum class Category(val title: String) { BOSSES("Bosses"), DEADMAN("Deadman"), CLUES("Clues"), MINIGAMES("Minigames"), OTHER("Other") }

    class Page(
        val key: String,
        val category: Category,
        val name: String,
        /** Drop Viewer entries whose kills this page counts ("<name> kills: N"); empty = no kill line. */
        val sources: List<String>,
        val items: List<Int>,
        /** The count line's label ("King Black Dragon kills"); null = "<name> kills". */
        val countLabel: String? = null,
    )

    /** OSRS Wiki "Collection log" boss pages (item names as the OSRS log lists them). */
    val OSRS: Map<String, List<String>> =
        mapOf(
            "king black dragon" to listOf("Prince black dragon", "Kbd heads", "Draconic visage", "Dragon pickaxe"),
            "kalphite queen" to listOf("Kalphite princess", "Kq head", "Jar of sand", "Dragon 2h sword", "Dragon chainbody", "Dragon pickaxe"),
            "chaos elemental" to listOf("Pet chaos elemental", "Dragon 2h sword", "Dragon pickaxe"),
            "corporeal beast" to listOf("Pet dark core", "Elysian sigil", "Spectral sigil", "Arcane sigil", "Holy elixir", "Jar of spirits"),
            "giant mole" to listOf("Baby mole", "Mole skin", "Mole claw"),
            "general graardor" to listOf("Pet general graardor", "Bandos chestplate", "Bandos tassets", "Bandos boots", "Bandos hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "commander zilyana" to listOf("Pet zilyana", "Armadyl crossbow", "Saradomin hilt", "Saradomin sword", "Saradomin's light", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "kree'arra" to listOf("Pet kree'arra", "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Armadyl hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "k'ril tsutsaroth" to listOf("Pet k'ril tsutsaroth", "Staff of the dead", "Zamorakian spear", "Steam battlestaff", "Zamorak hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "nex" to listOf("Nexling", "Ancient hilt", "Nihil horn", "Zaryte vambraces", "Torva full helm", "Torva platebody", "Torva platelegs", "Pernix cowl", "Pernix body", "Pernix chaps", "Virtus mask", "Virtus robe top", "Virtus robe legs", "Zaryte bow", "Nihil shard"),
            "dagannoth kings" to listOf("Pet dagannoth prime", "Pet dagannoth supreme", "Pet dagannoth rex", "Berserker ring", "Archers ring", "Seers ring", "Warrior ring", "Dragon axe", "Seercull", "Mud battlestaff"),
            "tztok-jad" to listOf("Tzrek-jad", "Fire cape"),
        )

    private val DK = setOf("dagannoth rex", "dagannoth prime", "dagannoth supreme")

    @Volatile private var built: List<Page>? = null

    fun reset() {
        built = null
    }

    /** Test hook: these pages instead of the ones built from the drop tables. */
    fun install(pages: List<Page>) {
        built = pages
    }

    fun all(player: Player): List<Page> = built ?: build(player).also { built = it }

    fun byKey(player: Player, key: String?): Page? = key?.let { k -> all(player).firstOrNull { it.key == k } }

    /** Every item any page lists (an item on two pages counts once for "Obtained N / M"). */
    fun allItems(player: Player): Set<Int> = all(player).flatMap { it.items }.toSet()

    /** The pages a reward from [sourceKey] can fill (the boss page, the Dagannoth Kings page, the breach page ...). */
    fun pagesFor(player: Player, sourceKey: String?): List<Page> = all(player).filter { sourceKey != null && sourceKey in it.sources }

    private fun build(player: Player): List<Page> {
        val world = player.world
        val pages = ArrayList<Page>()
        fun name(id: Int) = world.definitions.getNullable(ItemDef::class.java, id)?.name?.lowercase() ?: ""
        val sources = DropSources.all(player)
        // ---- Bosses
        val bosses = sources.filter { it.group == DropSources.Group.BOSSES }
        val dks = bosses.filter { it.name.lowercase() in DK }
        for (boss in bosses.sortedBy { it.name.lowercase() }) {
            if (boss in dks) continue
            if (boss.kind == DropSources.Kind.BARROWS) {
                val items = Barrows.REWARD_ARMOUR.values.flatMap { it.toList() } + Items.BOLT_RACK
                pages += Page("barrows", Category.BOSSES, "Barrows Chests", listOf("barrows"), items.filter { it in Barrows.COLLECTION_LOG_ITEMS }, "Barrows Chests opened")
                continue
            }
            val items = bossItems(player, boss, OSRS[boss.name.lowercase()], ::name)
            if (items.isNotEmpty()) pages += Page(boss.key, Category.BOSSES, boss.name, listOf(boss.key), items)
        }
        if (dks.isNotEmpty()) {
            val seen = LinkedHashSet<Int>()
            val wanted = OSRS.getValue("dagannoth kings")
            // OSRS order: the list's order, whichever king drops the item.
            for (want in wanted) {
                for (dk in dks) {
                    val view = DropSources.view(player, dk, 1.0)
                    DropSources.itemChances(view).keys.firstOrNull { name(it) == want.lowercase() }?.let { seen += it }
                }
            }
            pages += Page("dks", Category.BOSSES, "Dagannoth Kings", dks.map { it.key }, seen.toList())
        }
        pages.sortWith(compareBy { it.name.lowercase() })
        // ---- Deadman
        val chestItems = DeadmanChestLoot.FIXED.map { it.item }.distinct() + PrayerScrolls.SCROLLS.toList()
        pages += Page("chest", Category.DEADMAN, "Deadman's Chest", listOf("chest"), chestItems, "Deadman's Chests opened")
        val breachKeys = sources.filter { it.kind == DropSources.Kind.BREACH }.map { it.key }
        val breachItems = (listOfNotNull(BreachIds.ARCHAIC_EMBLEM_TIER_5.takeIf { it > 0 }) + BreachLoot.RARE.map { it.item } + BreachLoot.MEGA_RARE + PrayerScrolls.SCROLLS.toList()).distinct()
        pages += Page("breach", Category.DEADMAN, "Breaches", breachKeys, breachItems, "Breach monster kills")
        // ---- Clues: the elite casket's uniques.
        val elite = runCatching { EliteCasketTable.itemProbabilities() }.getOrNull()
        if (elite != null) {
            val perItem = HashMap<Int, Double>()
            elite.forEach { (key, p) -> perItem[key.first] = (perItem[key.first] ?: 0.0) + p }
            val uniques = perItem.filter { it.value > 0.0 && it.value <= 1.0 / 1000 }.keys.sortedBy { name(it) }
            if (uniques.isNotEmpty()) pages += Page("elite", Category.CLUES, "Elite Treasure Trails", emptyList(), uniques)
        }
        // ---- Other: every pet.
        val pets = Pets.infos.keys.mapNotNull { Pets.baseItem(it) }.distinct().sortedBy { name(it) }
        if (pets.isNotEmpty()) pages += Page("pets", Category.OTHER, "All Pets", emptyList(), pets)
        return pages
    }

    private fun bossItems(player: Player, boss: DropSources.Source, osrs: List<String>?, name: (Int) -> String): List<Int> {
        val view = DropSources.view(player, boss, 1.0)
        val chances = DropSources.itemChances(view)
        if (osrs != null) {
            return osrs.mapNotNull { want -> chances.keys.firstOrNull { name(it) == want.lowercase() } }.distinct()
        }
        // No OSRS page: the pet and the rare lines of the boss's own table (1/100 or rarer), never a shared table's lines.
        val out = ArrayList<Int>()
        view.lines.filter { it.tag == DropSources.Tag.PET }.forEach { out += it.item }
        view.lines.filter { it.tag == DropSources.Tag.NONE && it.normal > 0.0 && it.normal <= 1.0 / 100 && !name(it.item).startsWith("clue scroll") }
            .sortedBy { it.normal }.forEach { out += it.item }
        return out.distinct()
    }
}
