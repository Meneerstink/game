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
 *  - Deadman: the Deadman's chest list and the breach uniques + Archaic emblem, weapons and armour first and the pets they can
 *    give at the bottom (N268, owner 2026-10-24; a breach monster's chest-list roll gives the chest's pets too).
 *  - Clues: the elite casket's uniques (1/1,000 per roll or rarer, [EliteCasketTable.itemProbabilities]).
 *  - Minigames (N276): Pest Control, the Void Knight reward items as OSRS lists them (+ the 2011 deflector this server sells).
 *  - Other: the five Beast of burden trinkets (N423), every pet.
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
            // N522: OSRS Wiki Collection log "Chaos Fanatic" / "Crazy Archaeologist" (3 items each).
            "chaos fanatic" to listOf("Odium shard 1", "Malediction shard 1", "Pet chaos elemental"),
            "crazy archaeologist" to listOf("Odium shard 2", "Malediction shard 2", "Fedora"),
            // N522: OSRS Wiki Collection log "Scorpia" (3 items).
            "scorpia" to listOf("Odium shard 3", "Malediction shard 3", "Scorpia's offspring"),
            // N522 bundle 5: OSRS Wiki Collection log "Callisto and Artio" / "Vet'ion and Calvar'ion" (6 items each; Artio and Calvar'ion
            // share the page, [PAIRS]).
            "callisto" to listOf("Callisto cub", "Tyrannical ring", "Dragon pickaxe", "Dragon 2h sword", "Claws of Callisto", "Voidwaker hilt"),
            "vet'ion" to listOf("Vet'ion Jr.", "Ring of the gods", "Dragon pickaxe", "Dragon 2h sword", "Skull of Vet'ion", "Voidwaker blade"),
            // N522: OSRS Wiki Collection log "Venenatis and Spindel" (6 items).
            "venenatis" to listOf("Venenatis spiderling", "Fangs of Venenatis", "Voidwaker gem", "Dragon 2h sword", "Dragon pickaxe", "Treasonous ring"),
            "corporeal beast" to listOf("Pet dark core", "Elysian sigil", "Spectral sigil", "Arcane sigil", "Holy elixir", "Jar of spirits"),
            "giant mole" to listOf("Baby mole", "Mole skin", "Mole claw"),
            "general graardor" to listOf("Pet general graardor", "Bandos chestplate", "Bandos tassets", "Bandos boots", "Bandos hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "commander zilyana" to listOf("Pet zilyana", "Armadyl crossbow", "Saradomin hilt", "Saradomin sword", "Saradomin's light", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "kree'arra" to listOf("Pet kree'arra", "Armadyl helmet", "Armadyl chestplate", "Armadyl chainskirt", "Armadyl hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "k'ril tsutsaroth" to listOf("Pet k'ril tsutsaroth", "Staff of the dead", "Zamorakian spear", "Steam battlestaff", "Zamorak hilt", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3"),
            "nex" to listOf("Nexling", "Ancient hilt", "Nihil horn", "Zaryte vambraces", "Zaryte crossbow", "Torva full helm", "Torva platebody", "Torva platelegs", "Pernix cowl", "Pernix body", "Pernix chaps", "Virtus mask", "Virtus robe top", "Virtus robe legs", "Nihil shard"),
            "dagannoth kings" to listOf("Pet dagannoth prime", "Pet dagannoth supreme", "Pet dagannoth rex", "Berserker ring", "Archers ring", "Seers ring", "Warrior ring", "Dragon axe", "Seercull", "Mud battlestaff"),
            "tztok-jad" to listOf("Tzrek-jad", "Fire cape"),
        )

    /** The Pest Control page's key: the source key [PestControlRewards] records its rewards with. */
    const val PEST_CONTROL = "pest_control"

    /** OSRS Collection log "Pest Control" order: Void knight top, robe, gloves, mace, elite top, elite robe, the three helms, the seal. */
    val PEST_CONTROL_ITEMS =
        listOf(
            Items.VOID_KNIGHT_TOP, Items.VOID_KNIGHT_ROBE, Items.VOID_KNIGHT_GLOVES, Items.VOID_KNIGHT_MACE, Items.ELITE_VOID_KNIGHT_TOP,
            Items.ELITE_VOID_KNIGHT_ROBE, Items.VOID_MAGE_HELM, Items.VOID_RANGER_HELM, Items.VOID_MELEE_HELM, Items.VOID_SEAL_8,
            // N471: the wearable deflector the Void Knights sell (VoidDeflectorMigration moves old 19711 entries here).
            Items.VOID_KNIGHT_DEFLECTOR_19712,
        )

    private val DK = setOf("dagannoth rex", "dagannoth prime", "dagannoth supreme")
    /**
     * OSRS shares one Collection log page between a Wilderness boss and its singles variant ("Venenatis and Spindel", N522 bundle 4b;
     * "Callisto and Artio", "Vet'ion and Calvar'ion", N522 nachtrun deel 2): main boss -> (singles boss, page title).
     */
    private val PAIRS =
        mapOf(
            "venenatis" to ("spindel" to "Venenatis and Spindel"),
            "callisto" to ("artio" to "Callisto and Artio"),
            "vet'ion" to ("calvar'ion" to "Vet'ion and Calvar'ion"),
        )

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
        val names = bosses.map { it.name.lowercase() }.toSet()
        val partners = PAIRS.values.map { it.first }.toSet()
        for (boss in bosses.sortedBy { it.name.lowercase() }) {
            // Maledictus' uniques fill the OSRS Revenants page below, rather than inventing a separate boss page.
            if (boss.modelNpc == gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC) continue
            if (boss in dks) continue
            val lower = boss.name.lowercase()
            // The singles variant fills its main boss's page; alone (main boss missing) it keeps a page of its own.
            if (lower in partners && PAIRS.any { (main, pair) -> pair.first == lower && main in names }) continue
            val pair = PAIRS[lower]
            if (pair != null && pair.first in names) {
                val items = bossItems(player, boss, OSRS[lower], ::name)
                val keys = bosses.filter { it.name.lowercase() == lower || it.name.lowercase() == pair.first }.map { it.key }
                if (items.isNotEmpty()) pages += Page(boss.key, Category.BOSSES, pair.second, keys, items)
                continue
            }
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
        // ---- Deadman: N268 (owner 2026-10-24) weapons and armour first, then the rest, the pets at the bottom; N316 the chest has only
        // the pets without a boss here; N306 the breach page has the breach bosses' own pets, no chest pets.
        val chestPets = DeadmanChestLoot.pets().map { it.second }.distinct()
        fun gearFirst(items: List<Int>) = items.distinct().sortedBy { if (DropGroups.category(world, it) == DropGroups.Group.GEAR) 0 else 1 }
        val chestItems = (gearFirst(DeadmanChestLoot.FIXED.map { it.item } + PrayerScrolls.SCROLLS.toList() + Items.RUINOUS_POWERS) + chestPets).distinct()
        pages += Page("chest", Category.DEADMAN, "Deadman's Chest", listOf("chest"), chestItems, "Deadman's Chests opened")
        val breaches = sources.filter { it.kind == DropSources.Kind.BREACH }
        val breachKeys = breaches.map { it.key }
        val breachPets = breaches.flatMap { it.npcs.toList() }.mapNotNull { Pets.dropFor(it)?.pet }.distinct().mapNotNull { Pets.baseItem(it) }.sortedBy { name(it) }
        val breachItems = (gearFirst(BreachLoot.MEGA_RARE + BreachLoot.RARE.map { it.item } + listOfNotNull(BreachIds.ARCHAIC_EMBLEM_TIER_5.takeIf { it > 0 }) + PrayerScrolls.SCROLLS.toList() + Items.RUINOUS_POWERS) + breachPets).distinct()
        pages += Page("breach", Category.DEADMAN, "Breaches", breachKeys, breachItems, "Breach monster kills")
        // ---- Clues: the elite casket's uniques.
        val elite = runCatching { EliteCasketTable.itemProbabilities() }.getOrNull()
        if (elite != null) {
            // N292: a note counts as its item - the page shows the plain item (the log records it so, CollectionLog.unnoted).
            val perItem = HashMap<Int, Double>()
            elite.forEach { (key, p) -> unnoted(world, key.first).let { id -> perItem[id] = (perItem[id] ?: 0.0) + p } }
            val uniques = perItem.filter { it.value > 0.0 && it.value <= 1.0 / 1000 }.keys.sortedBy { name(it) }
            if (uniques.isNotEmpty()) pages += Page("elite", Category.CLUES, "Elite Treasure Trails", emptyList(), uniques)
        }
        // ---- Minigames: Pest Control (the reward items recorded when the Void Knight hands them over, PestControlRewards.confirm).
        pages += Page(PEST_CONTROL, Category.MINIGAMES, "Pest Control", emptyList(), PEST_CONTROL_ITEMS)
        // ---- Other: N423 (owner 2026-10-29 23:4x: "alle 5 in de Collection Log") the five burden trinkets - three drop, two are sold.
        val revenants = sources.filter { s -> s.npcs.any { it in gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaveRevenants.BY_ID || it == gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC } }
        if (revenants.isNotEmpty()) {
            // OSRS Collection log: Revenants. The six artefacts are OUR Deadman emblems (owner N522 decision).
            val wanted = listOf(Items.CRAWS_BOW_U, Items.THAMMARONS_SCEPTRE_U, Items.VIGGORAS_CHAINMACE_U, Items.AMULET_OF_AVARICE,
                Items.BRACELET_OF_ETHEREUM_UNCHARGED, Items.ANCIENT_CRYSTAL) + (1..6).map { gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaveRevenants.emblem(it) }
            val available = revenants.flatMap { DropSources.itemChances(DropSources.view(player, it, 1.0)).keys }.map { unnoted(world, it) }.toSet()
            pages += Page("revenants", Category.OTHER, "Revenants", revenants.map { it.key }, wanted.filter { it in available })
        }
        pages += Page("burden_trinkets", Category.OTHER, "Burden Trinkets", emptyList(), gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.Tier.values().map { it.item })
        // N490: includes Revenants and the chest guardian, which have no regular boss log page.
        pages += Page("ruinous", Category.OTHER, "Ruinous Powers", sources.filter { DropSources.ruinousSource(world, it) != null }.map { it.key }, listOf(Items.RUINOUS_POWERS))
        // ---- Other: every pet.
        val pets = Pets.infos.keys.mapNotNull { Pets.baseItem(it) }.distinct().sortedBy { name(it) }
        if (pets.isNotEmpty()) pages += Page("pets", Category.OTHER, "All Pets", emptyList(), pets)
        return pages
    }

    private fun unnoted(world: gg.rsmod.game.model.World, id: Int): Int {
        val def = world.definitions.getNullable(ItemDef::class.java, id) ?: return id
        return if (def.noted && def.noteLinkId > 0) def.noteLinkId else id
    }

    private fun bossItems(player: Player, boss: DropSources.Source, osrs: List<String>?, name: (Int) -> String): List<Int> {
        val view = DropSources.view(player, boss, 1.0)
        val chances = DropSources.itemChances(view)
        if (osrs != null) {
            val ruinous = listOfNotNull(Items.RUINOUS_POWERS.takeIf { it in chances })
            return (osrs.mapNotNull { want -> chances.keys.firstOrNull { name(it) == want.lowercase() } } + ruinous).distinct()
        }
        // No OSRS page: the pet and the rare lines of the boss's own table (1/100 or rarer), never a shared table's lines.
        val out = ArrayList<Int>()
        view.lines.filter { it.tag == DropSources.Tag.PET }.forEach { out += it.item }
        // N423: the burden trinkets have their own page (they drop from every boss, not this one's).
        view.lines.filter { it.tag == DropSources.Tag.NONE && it.normal > 0.0 && it.normal <= 1.0 / 100 && !name(it.item).startsWith("clue scroll") &&
            !gg.rsmod.plugins.content.skills.summoning.BurdenTrinkets.isTrinket(it.item) }
            .sortedBy { it.normal }.forEach { out += it.item }
        return out.distinct()
    }
}
