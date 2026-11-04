package gg.rsmod.plugins.content.quests.tab

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.drops.DropEntry
import gg.rsmod.plugins.content.drops.DropTable
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.GUARANTEED_TABLE_NAME
import gg.rsmod.plugins.content.mechanics.prayer.PrayerScrolls
import gg.rsmod.plugins.content.mechanics.prayer.RuinousBookDrops
import gg.rsmod.plugins.content.mechanics.prayer.RuinousUnlocks
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachIds
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestConfig
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestIds
import gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestLoot
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import gg.rsmod.plugins.content.skills.summoning.Pets
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Everything a kill (or a chest) can give, per Drop Viewer entry (N108 + A37, owner design 2026-10-24 GOEDGEKEURD 01-03, spec
 * NACHTOPDRACHT sections A, E and G). Every chance is read from the code the reward itself runs - nothing here is a second list:
 *
 *  - monster tables: [DropTableFactory.tables] + [DropTableFactory.lineChances] (the roll's own chances, the Annihilation rate
 *    included); a shared nested table ("Rare drop table") is one line that folds open to its own lines, rolled at 1x inside;
 *  - the boss pet: [Pets.dropChance] (the `on_npc_killed` roll, with the bracket boost since N313; a breach boss its own pet);
 *  - the tier 1 Deadman emblem: [DeadmanEmblem.dropChance] (150+ hp, only without an emblem, +10% in the Wilderness);
 *  - breach monsters: [BreachLoot] (the chest-list extra without pets (N306), the rare trigger, the prayer scroll, the two regular rolls) and the
 *    Archaic emblem for 250+ damage;
 *  - the Deadman's chest: [DeadmanChestLoot] (fixed items + pets, owned pets excluded for the opener) and its prayer scroll;
 *  - Zemouregal: the Archaic emblem for the top damage dealer; TzTok-Jad: the Fire cape; the Barrows chest: [Barrows.chances].
 *
 * "Your chance" uses the player's own rate; "Normal" is the same code at 1x.
 */
object DropSources {
    enum class Group(val title: String) { FAVOURITES("Favourites"), DEADMAN("Deadman"), BOSSES("Bosses"), MONSTERS("Monsters") }

    enum class Kind { NPC, BREACH, CHEST, GUARDIAN, BARROWS, JAD }

    class Source(
        val key: String,
        val name: String,
        /** The npc whose model the viewer shows (-1: none). */
        val modelNpc: Int,
        /** Every npc id whose kill counts for this entry (kill count, collection log). */
        val npcs: IntArray,
        val level: Int,
        val group: Group,
        val kind: Kind,
        /** The npc id whose drop table is read ([Kind.NPC]). */
        val tableNpc: Int = -1,
    )

    enum class Tag { NONE, PET, TABLE }

    /** One line of the viewer. [your] / [normal] are chances per kill; [kids] the lines of a nested table (folded by default). */
    class Line(
        val key: String,
        val item: Int,
        val name: String,
        val quantity: String,
        val your: Double,
        val normal: Double,
        val tag: Tag = Tag.NONE,
        val kids: List<Line> = emptyList(),
        /** Extra words after the chance ("250+ damage", "Fire cape for the Fight Caves"). */
        val note: String? = null,
        /** N312: a second, smaller line under the name ("1 item from the Deadman's Chest"). */
        val sub: String? = null,
    )

    class View(val always: List<Line>, val lines: List<Line>)

    // ------------------------------------------------------------------ the entries

    /** Bosses group (owner: the bosses that exist here); matched on the npc name. */
    val BOSS_NAMES =
        setOf(
            "king black dragon", "kalphite queen", "chaos elemental", "corporeal beast", "dagannoth rex", "dagannoth prime",
            "dagannoth supreme", "tormented demon", "glacor", "wildywyrm", "bork", "nex", "general graardor", "kree'arra",
            "commander zilyana", "k'ril tsutsaroth", "giant mole", "chaos fanatic", "crazy archaeologist", "scorpia", "venenatis", "spindel",
            "vet'ion", "callisto", "artio", "calvar'ion", "kalphite king", "queen black dragon", "tztok-jad", "jad", "revenant maledictus",
        )

    @Volatile private var built: List<Source>? = null

    /** Every entry; built once from the registered tables ([player] only builds the tables, see [DropTableFactory.tables]). */
    fun all(player: Player): List<Source> =
        built ?: synchronized(this) {
            built ?: run {
                val start = System.nanoTime()
                build(player).also {
                    built = it
                    gg.rsmod.game.Server.logger.info("Drop Viewer: {} entries built in {} ms.", it.size, (System.nanoTime() - start) / 1_000_000)
                }
            }
        }

    /** Test hook: rebuilds from the registered tables. */
    fun reset() {
        built = null
        viewCache.clear()
        indexes.clear()
    }

    fun byKey(player: Player, key: String?): Source? = key?.let { k -> all(player).firstOrNull { it.key == k } }

    fun npcName(world: World, id: Int): String = world.definitions.getNullable(NpcDef::class.java, id)?.name?.takeIf { it.isNotBlank() && it != "null" } ?: ""

    fun npcLevel(world: World, id: Int): Int = world.definitions.getNullable(NpcDef::class.java, id)?.combatLevel ?: 0

    private fun build(player: Player): List<Source> {
        val world = player.world
        val out = ArrayList<Source>()
        val breachIds = BreachMonsters.ROSTER.map { it.id }.toSet()
        val guardian = DeadmanChestIds.guardianNpc
        // One entry per name + level; the id with the most table lines is the one shown (Kalphite Queen's second form drops).
        val byName = LinkedHashMap<String, MutableList<Int>>()
        for (id in DropTableFactory.tableIds().sorted()) {
            if (id in breachIds || id == guardian) continue
            val name = npcName(world, id)
            if (name.isBlank()) continue
            byName.getOrPut("${name.lowercase()}|${npcLevel(world, id)}") { ArrayList() } += id
        }
        // Every npc id with the same name and level counts for the entry's kill count, also a copy without a table of its own
        // (the farm's chickens are several ids).
        val sameName = HashMap<String, MutableList<Int>>()
        val count = runCatching { world.definitions.getCount(NpcDef::class.java) }.getOrDefault(0)
        for (id in 0 until count) {
            if (id in breachIds || id == guardian) continue
            val name = npcName(world, id)
            if (name.isBlank()) continue
            val key = "${name.lowercase()}|${npcLevel(world, id)}"
            if (key in byName) sameName.getOrPut(key) { ArrayList() } += id
        }
        for ((key, ids) in byName) {
            val table = ids.maxByOrNull { id -> DropTableFactory.tables(player, id)?.sumOf { it.entries.size } ?: 0 } ?: continue
            val name = npcName(world, table)
            val boss = name.lowercase() in BOSS_NAMES
            val all = (ids + sameName[key].orEmpty()).distinct().sorted().toIntArray()
            out += Source("npc:$table", name, table, all, npcLevel(world, table), if (boss) Group.BOSSES else Group.MONSTERS, Kind.NPC, table)
        }
        // TzTok-Jad: no table, the Fire cape comes from the Fight Caves.
        world.definitions.getNullable(NpcDef::class.java, Jad.ID)?.let {
            if (out.none { s -> s.npcs.contains(Jad.ID) }) out += Source("jad", "TzTok-Jad", Jad.ID, intArrayOf(Jad.ID), it.combatLevel, Group.BOSSES, Kind.JAD)
        }
        out += Source("barrows", "Barrows chest", Barrows.Brother.values().first().npc, intArrayOf(), 0, Group.BOSSES, Kind.BARROWS)
        // Maledictus awards two dragon rolls but is a separate kill, model and source.
        val maledictus = gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC
        if (DropTableFactory.hasTable(Npcs.REVENANT_DRAGON_OSRS) && npcName(world, maledictus).isNotBlank()) {
            out += Source("npc:$maledictus", npcName(world, maledictus), maledictus, intArrayOf(maledictus), npcLevel(world, maledictus), Group.BOSSES, Kind.NPC, Npcs.REVENANT_DRAGON_OSRS)
        }
        out += Source("chest", "Deadman's Chest", -1, intArrayOf(), 0, Group.DEADMAN, Kind.CHEST)
        if (guardian > 0) out += Source("guardian", npcName(world, guardian).ifBlank { "Zemouregal" }, guardian, intArrayOf(guardian), npcLevel(world, guardian), Group.DEADMAN, Kind.GUARDIAN)
        BreachMonsters.ROSTER.filter { !it.summon }.forEach { m ->
            out += Source("breach:${m.id}", m.name, m.id, intArrayOf(m.id), npcLevel(world, m.id), Group.DEADMAN, Kind.BREACH)
        }
        return out
    }

    /** The source a kill of [npcId] counts for (kill count, collection log). */
    fun forNpc(player: Player, npcId: Int): Source? = all(player).firstOrNull { s -> s.npcs.contains(npcId) }

    private object Jad {
        const val ID = 2745
    }

    // ------------------------------------------------------------------ the lines

    private val viewCache = java.util.concurrent.ConcurrentHashMap<String, View>()

    /** The view of [source] at [rate] for [player] (cached per source + rate; the player-specific parts are added on top). */
    fun view(player: Player, source: Source, rate: Double = DropTableFactory.killRate(player)): View {
        val base = viewCache.getOrPut("${source.key}|$rate") { buildView(player, source, rate) }
        val personal = personal(player, source, base)
        // N490: only the new drop line; ownership/unlock is personal and must never enter the shared view cache.
        val kind = ruinousSource(player.world, source) ?: return personal
        val chance = 1.0 / kind.denominator
        val eligible = RuinousUnlocks.eligibleForDrop(player)
        val line = Line("ruinous", Items.RUINOUS_POWERS, itemName(player.world, Items.RUINOUS_POWERS), "1",
            if (eligible) chance else 0.0, chance, note = if (eligible) null else "Already unlocked or owned")
        return View(personal.always, sorted(player.world, personal.lines + line))
    }

    fun ruinousSource(world: World, source: Source): RuinousBookDrops.Source? = when (source.kind) {
        Kind.CHEST -> RuinousBookDrops.Source.CHEST
        Kind.NPC, Kind.BREACH, Kind.GUARDIAN, Kind.JAD -> RuinousBookDrops.sourceOf(world, if (source.kind == Kind.NPC) source.tableNpc else source.modelNpc)
        else -> null
    }

    /** Lines that depend on the player himself: the emblem (only without one), a chest pet he already owns. */
    private fun personal(player: Player, source: Source, base: View): View {
        val world = player.world
        return when (source.kind) {
            Kind.NPC -> {
                val emblem = emblemLine(world, source) ?: return base
                val owns = runCatching { DeadmanEmblem.ownedTier(player) > 0 }.getOrDefault(false)
                val line = if (owns) Line(emblem.key, emblem.item, emblem.name, emblem.quantity, 0.0, emblem.normal, note = "You have one") else emblem
                View(base.always, sorted(world, base.lines + line))
            }
            Kind.CHEST -> {
                val owned = Pets.infos.keys.filter { runCatching { Pets.owns(player, it) }.getOrDefault(false) }.toSet()
                if (owned.isEmpty()) base else chestView(world, rate = 1.0, owned = owned)
            }
            else -> base
        }
    }

    private fun buildView(player: Player, source: Source, rate: Double): View {
        val world = player.world
        return when (source.kind) {
            Kind.NPC -> if (source.modelNpc == gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC) maledictusView(player, source, rate) else npcView(player, source, rate)
            Kind.BREACH -> breachView(world, rate, source.modelNpc)
            Kind.CHEST -> chestView(world, 1.0, emptySet())
            Kind.GUARDIAN -> View(listOfNotNull(archaic(world, "top damage")), sorted(world, trinketLines(world, source.modelNpc)))
            Kind.BARROWS -> barrowsView(world, rate)
            Kind.JAD -> jadView(world, source, rate)
        }
    }

    /** The item's name; a bank note reads "<item> (noted)" (667 notes carry no name of their own). */
    fun itemName(world: World, id: Int): String {
        val def = world.definitions.getNullable(ItemDef::class.java, id) ?: return "Item $id"
        if (def.noted && def.noteLinkId > 0) return itemName(world, def.noteLinkId) + " (noted)"
        return def.name.takeIf { it.isNotBlank() && it != "null" } ?: "Item $id"
    }

    /** The picture to show for [id]: a note shows its item (the Wiki does the same). */
    /**
     * The amount the picture is drawn for: the largest number of the Qty ("20500-21000" -> 21000), so coins, arrows and runes show
     * their stack picture like the OSRS Wiki drop tables. The viewer never draws the count itself (client RspsUi.OWN_COUNT_INTERFACES).
     */
    fun pictureAmount(quantity: String): Int {
        val numbers =
            Regex("([0-9][0-9,.]*)\\s*([KkMm]?)").findAll(quantity).mapNotNull { m ->
                val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
                val mult = when (m.groupValues[2].lowercase()) { "k" -> 1_000.0; "m" -> 1_000_000.0; else -> 1.0 }
                (n * mult).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        return numbers.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    fun picture(world: World, id: Int): Int {
        val def = world.definitions.getNullable(ItemDef::class.java, id) ?: return id
        return if (def.noted && def.noteLinkId > 0) def.noteLinkId else id
    }

    private fun npcView(player: Player, source: Source, rate: Double): View {
        val world = player.world
        val tables = DropTableFactory.tables(player, source.tableNpc) ?: emptyList()
        val always = ArrayList<Line>()
        val lines = ArrayList<Line>()
        for (table in tables) {
            if (table.name.equals(GUARANTEED_TABLE_NAME, ignoreCase = true)) {
                // Every line of the guaranteed table drops on every kill (DropTableFactory.guaranteedItems).
                for ((at, entry) in table.entries.withIndex()) {
                    when (val d = entry.drop) {
                        is DropEntry.ItemDrop -> always += Line("a:$at", d.item.id, itemName(world, d.item.id), d.item.amount.toString(), 1.0, 1.0)
                        is DropEntry.ItemRangeDrop -> always += Line("a:$at", d.item.id, itemName(world, d.item.id), "${d.quantityRange.first}-${d.quantityRange.last}", 1.0, 1.0)
                        is DropEntry.MultiDrop -> d.items.forEachIndexed { i, it -> always += Line("a:$at.$i", it.id, itemName(world, it.id), it.amount.toString(), 1.0, 1.0) }
                        else -> Unit
                    }
                }
                continue
            }
            val main = table.name == "main"
            val longBone = tables.firstOrNull { it.name == "long_bone" }
            val boneWeight = if (table.name == "curved_bone" && longBone != null) 1 - DropTableFactory.lineChances(longBone, rate).sum() else 1.0
            val normalBoneWeight = if (table.name == "curved_bone" && longBone != null) 1 - DropTableFactory.lineChances(longBone, 1.0).sum() else 1.0
            walk(world, table, DropTableFactory.lineChances(table, rate), DropTableFactory.lineChances(table, 1.0),
                if (main) DropTableFactory.mainTableWeight(source.tableNpc, tables, rate) else boneWeight,
                if (main) DropTableFactory.mainTableWeight(source.tableNpc, tables, 1.0) else normalBoneWeight, "t:${table.name}", lines)
        }
        petLine(world, source.tableNpc, rate)?.let { lines += it }
        lines += trinketLines(world, source.tableNpc)
        return View(mergeAlways(always), sorted(world, merge(lines)))
    }

    /** The top-damage reward and participation supplies, in the existing viewer row format. */
    private fun maledictusView(player: Player, source: Source, rate: Double): View {
        val world = player.world
        val dragon = npcView(player, source, rate)
        val flat = ArrayList<Line>()
        fun flatten(line: Line) { if (line.tag == Tag.TABLE) line.kids.forEach(::flatten) else flat += line }
        dragon.lines.forEach(::flatten)
        val lines = merge(flat).map { line ->
            Line(line.key, line.item, line.name, line.quantity, twice(line.your), twice(line.normal), note = "Top damage; 2 rolls")
        }.toMutableList()
        for (tier in 1..2) {
            val item = gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantCaveRevenants.emblem(tier)
            val at = lines.indexOfFirst { it.item == item && it.quantity == "1" }
            val prior = lines.getOrNull(at)
            val line = Line("maledictus:emblem:$tier", item, itemName(world, item), "1", 1 - 0.5 * (1 - (prior?.your ?: 0.0)),
                1 - 0.5 * (1 - (prior?.normal ?: 0.0)), note = "Top damage")
            if (at >= 0) lines[at] = line else lines += line
        }
        fun supply(item: Int, quantity: String, chance: Double) = Line("maledictus:supply:$item", item, itemName(world, item), quantity, chance, chance, note = "Other damage dealers")
        lines += supply(Items.BLIGHTED_SUPER_RESTORE4, "1", 1.0)
        listOf(Items.BLIGHTED_ANGLERFISH, Items.BLIGHTED_KARAMBWAN, Items.BLIGHTED_MANTA_RAY).forEach { lines += supply(it, "2", 1.0 / 3) }
        val always = dragon.always.map { line ->
            val quantity = Regex("\\d+").replace(line.quantity) { (it.value.toInt() * 2).toString() }
            Line(line.key, line.item, line.name, quantity, 1.0, 1.0, note = "Top damage; 2 rolls")
        }
        return View(always, sorted(world, lines))
    }

    fun twice(chance: Double): Double = 1 - (1 - chance) * (1 - chance)

    /**
     * N423 deel 5: the burden trinkets a kill of [npc] can give - the same chance for everyone (no bracket boost), so Your = Normal
     * ([gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops]).
     */
    private fun trinketLines(world: World, npc: Int): List<Line> {
        val drops = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
        val kind = drops.sourceOf(world, npc) ?: return emptyList()
        return trinketLines(world, kind)
    }

    private fun trinketLines(world: World, kind: gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source): List<Line> {
        val drops = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
        return drops.DROPPED.map { t ->
            val c = drops.chance(kind, t)
            Line("trinket:${t.item}", t.item, itemName(world, t.item), "1", c, c)
        }
    }

    /** N313: the boss pet of [npc] - Normal the OSRS chance, Your chance with the bracket boost (the roll's own [Pets.dropChance]). */
    private fun petLine(world: World, npc: Int, rate: Double): Line? {
        val pet = Pets.dropFor(npc) ?: return null
        val item = Pets.baseItem(pet.pet) ?: return null
        return Line("pet", item, itemName(world, item), "1", Pets.dropChance(npc, rate), Pets.dropChance(npc), Tag.PET)
    }

    /** The tier 1 emblem line of a monster with 150+ hp outside the guards (deadman_emblem.plugin.kts). */
    private fun emblemLine(world: World, source: Source): Line? {
        val combatId = if (source.modelNpc == gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC) source.modelNpc else source.tableNpc
        val combat = world.plugins.allNpcCombatDefs()[combatId] ?: return null
        if (gg.rsmod.plugins.content.mechanics.pvp.CityGuards.GUARD_IDS.contains(source.tableNpc)) return null
        val wild = combatId == gg.rsmod.plugins.content.areas.wilderness.bosses.RevenantMaledictus.NPC || DropViewerPlaces.inWilderness(world, combatId)
        val chance = DeadmanEmblem.dropChance(combat.lifepoints, wild)
        if (chance <= 0.0) return null
        val item = DeadmanEmblem.FIRST_EMBLEM
        return Line("emblem", item, itemName(world, item), "1", chance, chance, note = if (wild) "Wilderness +10%" else null)
    }

    /**
     * One table's lines at [weight] (the chance of reaching it): [your] / [normal] are its line chances at the player's rate and
     * at 1x. A named nested table is one line with its own lines folded under it (rolled at 1x inside, like the roll).
     */
    private fun walk(
        world: World,
        table: DropTable,
        your: DoubleArray,
        normal: DoubleArray,
        yourWeight: Double,
        normalWeight: Double,
        path: String,
        out: MutableList<Line>,
    ) {
        for ((at, entry) in table.entries.withIndex()) {
            val y = yourWeight * your[at]
            val n = normalWeight * normal[at]
            when (val drop = entry.drop) {
                is DropEntry.ItemDrop -> out += Line("$path/$at", drop.item.id, itemName(world, drop.item.id), drop.item.amount.toString(), y, n)
                is DropEntry.ItemRangeDrop -> out += Line("$path/$at", drop.item.id, itemName(world, drop.item.id), "${drop.quantityRange.first}-${drop.quantityRange.last}", y, n)
                is DropEntry.MultiDrop -> drop.items.forEachIndexed { i, it -> out += Line("$path/$at.$i", it.id, itemName(world, it.id), it.amount.toString(), y, n) }
                is DropEntry.TableDrop -> {
                    val inner = DropTableFactory.lineChances(drop.table, 1.0)
                    if (drop.label == null) {
                        walk(world, drop.table, inner, inner, y, n, "$path/$at", out)
                    } else {
                        val kids = ArrayList<Line>()
                        walk(world, drop.table, inner, inner, y, n, "$path/$at", kids)
                        val sortedKids = sorted(world, merge(kids))
                        val icon = TABLE_ICONS[drop.label] ?: sortedKids.firstOrNull()?.item ?: -1
                        out += Line("$path/$at", icon, drop.label, "1", y, n, Tag.TABLE, sortedKids)
                    }
                }
                DropEntry.NothingDrop -> Unit
            }
        }
    }

    private val TABLE_ICONS =
        mapOf(
            "Rare drop table" to Items.LOOP_HALF_OF_A_KEY,
            "Gem drop table" to Items.UNCUT_SAPPHIRE,
            "Mega-rare drop table" to Items.SHIELD_LEFT_HALF,
            "Herb drop table" to Items.GRIMY_RANARR,
            "Seed drop table" to Items.RANARR_SEED,
        )

    /** The same item and quantity from two tables of one kill: one line, chances added (at most 1). Table lines stay apart. */
    fun merge(lines: List<Line>): List<Line> {
        val merged = LinkedHashMap<String, Line>()
        for (l in lines) {
            val k = if (l.tag == Tag.TABLE) l.key else "${l.item}|${l.quantity}|${l.tag}"
            val have = merged[k]
            merged[k] = if (have == null) l else Line(have.key, l.item, l.name, l.quantity, minOf(1.0, have.your + l.your), minOf(1.0, have.normal + l.normal), l.tag, l.kids, l.note ?: have.note, l.sub ?: have.sub)
        }
        return merged.values.toList()
    }

    private fun mergeAlways(lines: List<Line>): List<Line> = lines.distinctBy { "${it.item}|${it.quantity}" }

    /**
     * N279 (owner 2026-10-24, replaces the N272 price order): the pet at the top, then the rarest on the NORMAL chance (the order of
     * a monster never moves with the bracket), the same item together ([DropGroups.RAREST_FIRST]). The viewer then puts the lines
     * in their groups ([DropGroups]); the Always bar stays apart, in its own order.
     */
    fun sorted(world: World, lines: List<Line>): List<Line> = lines.sortedWith(compareBy<Line> { it.tag != Tag.PET }.then(DropGroups.RAREST_FIRST))

    /** "100" -> 100, "20-78" -> 49 (the viewer's quantity text). */
    fun averageQuantity(quantity: String): Double {
        val parts = quantity.replace(",", "").split('-').mapNotNull { it.trim().toIntOrNull() }
        return if (parts.isEmpty()) 1.0 else parts.average()
    }

    // ---- breach monsters

    /** `random.nextInt(bound) < x` is true for ceil(x) of the bound's values. */
    private fun intChance(x: Double, bound: Int): Double = ceil(x).coerceIn(0.0, bound.toDouble()) / bound

    private fun archaic(world: World, note: String): Line? {
        val item = BreachIds.ARCHAIC_EMBLEM_TIER_5
        if (item <= 0) return null
        return Line("archaic", item, itemName(world, item), "1", 1.0, 1.0, note = note)
    }

    private fun breachView(world: World, rate: Double, npc: Int): View {
        val lines = ArrayList<Line>()
        // N306: a breach boss gives only its own pet (pet_tiers drop npcs), with the bracket boost (N313).
        petLine(world, npc, rate)?.let { lines += it }
        lines += trinketLines(world, npc)
        // The chest-list extra roll (BreachLoot.roll, BREACH_EXTRA_CHANCE), then one pick of the chest list's items (no pets, N306).
        val chestY = intChance(rate, DeadmanChestConfig.BREACH_EXTRA_CHANCE)
        val chestN = intChance(1.0, DeadmanChestConfig.BREACH_EXTRA_CHANCE)
        // N312 (owner 2026-10-24, A): "Chest reward" with the explanation on its own line, the items under it with their real chance.
        lines += Line("chest", Items.TRINKET_OF_FAIRIES, "Chest reward", "1", chestY, chestN, Tag.TABLE, chestLines(world, chestY, chestN, emptySet(), withPets = false), sub = "1 item from the Deadman's Chest")
        // The rare trigger, then 1/92 the mega-rare equipment (equal), else one RARE line by weight.
        val rareY = intChance(BreachLoot.RARE_TRIGGER * rate, BreachLoot.RARE_DENOMINATOR)
        val rareN = intChance(BreachLoot.RARE_TRIGGER.toDouble(), BreachLoot.RARE_DENOMINATOR)
        val rareKids = ArrayList<Line>()
        val mega = 1.0 / BreachLoot.MEGA_RARE_CHANCE
        BreachLoot.MEGA_RARE.forEachIndexed { i, item ->
            val c = mega / BreachLoot.MEGA_RARE.size
            rareKids += Line("rare/m$i", item, itemName(world, item), "1", rareY * c, rareN * c)
        }
        BreachLoot.RARE.forEachIndexed { i, l ->
            val c = (1 - mega) * l.weight / BreachLoot.RARE_TOTAL
            rareKids += Line("rare/r$i", l.item, itemName(world, l.item), qty(l.min, l.max), rareY * c, rareN * c)
        }
        lines += Line("rare", Items.VESTAS_LONGSWORD, "Rare table", "1", rareY, rareN, Tag.TABLE, sorted(world, merge(rareKids)))
        // N98: 1 in 200 one of the two prayer scrolls (not boosted).
        PrayerScrolls.SCROLLS.forEachIndexed { i, item ->
            val c = 1.0 / PrayerScrolls.BREACH_CHANCE / PrayerScrolls.SCROLLS.size
            lines += Line("scroll$i", item, itemName(world, item), "1", c, c)
        }
        // Two regular rolls: the chance of a line at least once per kill.
        val yourRegular = BreachLoot.regularChances(rate)
        val normalRegular = BreachLoot.regularChances(1.0)
        BreachLoot.REGULAR.forEachIndexed { i, l ->
            fun twice(p: Double) = 1 - (1 - p).pow(BreachLoot.REGULAR_ROLLS)
            lines += Line("reg$i", l.item, itemName(world, l.item) + if (l.noted) " (noted)" else "", qty(l.min, l.max), twice(yourRegular[i]), twice(normalRegular[i]))
        }
        // N307 (owner 2026-10-24, B): the emblem is once per account per breach (DeadmanBreach.claimArchaicEmblem), not every kill.
        return View(listOfNotNull(archaic(world, "${BreachLoot.EMBLEM_DAMAGE}+ dmg, 1x per breach")), sorted(world, merge(lines)))
    }

    private fun qty(min: Int, max: Int) = if (max > min) "$min-$max" else "$min"

    // ---- the Deadman's chest

    private fun chestLines(world: World, yourWeight: Double, normalWeight: Double, owned: Set<String>, withPets: Boolean = true): List<Line> {
        val out = ArrayList<Line>()
        val pets = if (withPets) DeadmanChestLoot.pets() else emptyList()
        val yourPets = pets.filterNot { it.first in owned }
        val normalTotal = DeadmanChestLoot.FIXED.sumOf { it.weight } + pets.size * DeadmanChestLoot.PET_WEIGHT
        val yourTotal = DeadmanChestLoot.FIXED.sumOf { it.weight } + yourPets.size * DeadmanChestLoot.PET_WEIGHT
        DeadmanChestLoot.FIXED.forEachIndexed { i, e ->
            out += Line("chest/f$i", e.item, itemName(world, e.item), e.amount.toString(), yourWeight * e.weight / yourTotal, normalWeight * e.weight / normalTotal)
        }
        pets.forEachIndexed { i, (pet, item) ->
            val mine = pet !in owned
            out += Line("chest/p$i", item, itemName(world, item), "1", if (mine) yourWeight * DeadmanChestLoot.PET_WEIGHT / yourTotal else 0.0, normalWeight * DeadmanChestLoot.PET_WEIGHT / normalTotal, Tag.PET, note = if (mine) null else "You have it")
        }
        return sorted(world, out)
    }

    private fun chestView(world: World, rate: Double, owned: Set<String>): View {
        val lines = ArrayList(chestLines(world, rate.coerceAtMost(1.0), 1.0, owned))
        PrayerScrolls.SCROLLS.forEachIndexed { i, item ->
            val c = 1.0 / PrayerScrolls.CHEST_CHANCE / PrayerScrolls.SCROLLS.size
            lines += Line("scroll$i", item, itemName(world, item), "1", c, c)
        }
        lines += trinketLines(world, gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.CHEST)
        return View(emptyList(), sorted(world, lines))
    }

    // ---- Barrows chest: all six brothers, the highest reward potential (1012), seven rolls

    private fun barrowsView(world: World, rate: Double): View {
        val kills = 6
        val potential = Barrows.rewardPotential(1000, kills)
        val rolls = Barrows.rollCount(kills)
        val your = Barrows.chances(potential, kills, rate)
        val normal = Barrows.chances(potential, kills, 1.0)
        fun perChest(p: Double) = 1 - (1 - p).pow(rolls)
        val lines = ArrayList<Line>()
        val pieces = Barrows.REWARD_ARMOUR.values.flatMap { it.toList() }
        pieces.forEachIndexed { i, item ->
            lines += Line("arm$i", item, itemName(world, item), "1", perChest(your[0] / pieces.size), perChest(normal[0] / pieces.size))
        }
        Barrows.TABLE.forEachIndexed { i, l ->
            lines += Line("tab$i", l.item, itemName(world, l.item), l.quantity, perChest(your[i + 1]), perChest(normal[i + 1]))
        }
        return View(emptyList(), sorted(world, merge(lines)))
    }

    private fun jadView(world: World, source: Source, rate: Double): View {
        val lines = ArrayList<Line>()
        petLine(world, source.modelNpc, rate)?.let { lines += it }
        lines += trinketLines(world, source.modelNpc)
        return View(listOf(Line("cape", Items.FIRE_CAPE, itemName(world, Items.FIRE_CAPE), "1", 1.0, 1.0, note = "Fight Caves")), lines)
    }

    /** One item line of a raw table, every fold opened (tests compare it with the roll; the viewer itself uses [view]). */
    class FlatRow(val item: Int, val quantity: String, val chance: Double, val normal: Double)

    /**
     * Every item of drop table [tableId] at [rate] as one list: the Always items at 1, the rest per kill, a folded table's lines
     * included, the same item and quantity added up (at most 1) - the chances the roll gives.
     */
    fun tableRows(player: Player, tableId: Int, rate: Double = DropTableFactory.killRate(player)): List<FlatRow> {
        val source = Source("npc:$tableId", "", -1, intArrayOf(tableId), 0, Group.MONSTERS, Kind.NPC, tableId)
        val view = npcView(player, source, rate)
        val out = LinkedHashMap<String, FlatRow>()
        fun add(l: Line) {
            if (l.tag == Tag.TABLE) {
                l.kids.forEach(::add)
                return
            }
            val k = "${l.item}|${l.quantity}"
            val have = out[k]
            out[k] = if (have == null) FlatRow(l.item, l.quantity, l.your, l.normal) else FlatRow(l.item, l.quantity, minOf(1.0, have.chance + l.your), minOf(1.0, have.normal + l.normal))
        }
        view.always.forEach(::add)
        view.lines.forEach(::add)
        return out.values.toList()
    }

    // ------------------------------------------------------------------ search

    /** Per item: every entry that can give it, with the chance per kill (your, normal), at one drop rate. */
    class ItemIndex(val names: Map<Int, String>, val sources: Map<Int, List<Triple<Source, Double, Double>>>)

    private val indexes = java.util.concurrent.ConcurrentHashMap<Double, ItemIndex>()

    @Volatile private var warming = false

    /**
     * Builds the item index of every bracket rate on the game thread, a few dozen entries per tick (first login after a start), so
     * no player's first search waits ~1 s in one tick. A search before it is done builds what it needs itself.
     */
    fun warmUp(player: Player) {
        if (warming || indexes.size >= RATES.size) return
        warming = true
        player.world.queue {
            val sources = all(player)
            // The logged-in player's own rate first: most likely the first search.
            val own = DropTableFactory.killRate(player)
            for (rate in listOf(own) + RATES.filter { it != own }) {
                if (indexes.containsKey(rate)) continue
                val names = HashMap<Int, String>()
                val bySource = HashMap<Int, MutableList<Triple<Source, Double, Double>>>()
                sources.forEachIndexed { i, s ->
                    for ((item, c) in itemChances(view(player, s, rate))) {
                        names.getOrPut(item) { itemName(player.world, item) }
                        bySource.getOrPut(item) { ArrayList() } += Triple(s, c.first, c.second)
                    }
                    if (i % WARM_UP_PER_TICK == WARM_UP_PER_TICK - 1) wait(1)
                }
                indexes.putIfAbsent(rate, ItemIndex(names, bySource))
            }
        }
    }

    private const val WARM_UP_PER_TICK = 40

    private val RATES: List<Double> by lazy { gg.rsmod.plugins.content.mechanics.pvp.annihilation.AnnihilationTier.values().map { it.dropRate }.distinct() }

    /** Built once per drop rate (the first search at that rate), then a search is a lookup. */
    fun itemIndex(player: Player, rate: Double = DropTableFactory.killRate(player)): ItemIndex =
        indexes[rate] ?: synchronized(indexes) {
            indexes[rate] ?: run {
                val start = System.nanoTime()
                val names = HashMap<Int, String>()
                val bySource = HashMap<Int, MutableList<Triple<Source, Double, Double>>>()
                for (s in all(player)) {
                    for ((item, c) in itemChances(view(player, s, rate))) {
                        names.getOrPut(item) { itemName(player.world, item) }
                        bySource.getOrPut(item) { ArrayList() } += Triple(s, c.first, c.second)
                    }
                }
                ItemIndex(names, bySource).also {
                    indexes[rate] = it
                    gg.rsmod.game.Server.logger.info("Drop Viewer: item index at {}x ({} items) built in {} ms.", rate, names.size, (System.nanoTime() - start) / 1_000_000)
                }
            }
        }


    /** Every item of a view with its chance per kill (folded tables included, the same item added up). */
    fun itemChances(view: View): Map<Int, Pair<Double, Double>> {
        val out = HashMap<Int, Pair<Double, Double>>()
        fun add(l: Line) {
            if (l.tag == Tag.TABLE) {
                l.kids.forEach(::add)
                return
            }
            val have = out[l.item]
            out[l.item] = if (have == null) l.your to l.normal else minOf(1.0, have.first + l.your) to minOf(1.0, have.second + l.normal)
        }
        view.always.forEach(::add)
        view.lines.forEach(::add)
        return out
    }
}
