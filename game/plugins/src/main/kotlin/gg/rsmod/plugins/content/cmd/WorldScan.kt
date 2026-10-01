package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.combat.audio.NpcCombatAudio
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug toolkit phase 3 (manual: C:\RSPS\TRACES.md): one scan of the whole loaded world - cache, data tables, plugin
 * registries and every spawned npc - that lists what is broken without anybody playing. Runs on the live server with the
 * in-game command `scan`, or at boot while `C:\RSPS\scan.on` exists. Writes `C:\RSPS\bugs\scan-<stamp>.txt` (every finding,
 * one `SCAN <section> ...` line each) and the four census CSVs next to the server.
 *
 * Sections:
 *  - census: cache menu options without a handler (npcs, items, objects) - "Nothing interesting happens" (ROOT_CAUSES #1, #38, #61)
 *  - npc-spawn: spawned npcs standing where they cannot step in any direction (#25, #50, #66)
 *  - npc-combat: attackable npcs without a combat definition; combat definitions whose attack/block/death animations do not
 *    exist in the cache; combat npcs with no attack sound source at all (section, sound table or frame sound) (#9, #43, #80)
 *  - npc-bas: stand/walk animations (BAS) missing from the cache (the npc T-poses or slides)
 *  - npc-attacks: every animation, graphic, projectile and sound id in the npc attack table that the cache does not have
 *  - npc-sounds: sound-table ids (attack/defend/death) missing from the cache
 *  - item-model: wearable items whose worn model is missing (invisible when worn)
 */
object WorldScan {
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /**
     * Runs [run] on its own thread (it reads every definition and the cache: 7.7 s on the game thread once) and hands the
     * result to [done] back on the game thread. The npc list is copied on the game thread first, so the scan never walks
     * the live list while the game thread changes it. Call from the game thread.
     */
    fun runInBackground(
        world: World,
        done: (Result<File>) -> Unit,
    ) {
        val npcs = ArrayList<Npc>(world.npcs.count())
        world.npcs.forEach { npcs += it }
        val game = world.getService(gg.rsmod.game.service.GameService::class.java)
        Thread({
            val result = runCatching { run(world, npcs) }
            result.exceptionOrNull()?.let { gg.rsmod.game.model.Trace.crash("world scan", it) }
            if (game != null) game.submitGameThreadJob { done(result) } else logger.warn("World scan: no GameService to report back on - {}", result.getOrNull())
        }, "world-scan").apply { isDaemon = true }.start()
    }

    private val logger = mu.KotlinLogging.logger {}

    fun run(
        world: World,
        npcs: List<Npc>,
    ): File {
        val findings = mutableListOf<String>()
        val summary = linkedMapOf<String, Int>()

        fun add(
            section: String,
            text: String,
        ) {
            findings += "SCAN $section $text"
            summary[section] = (summary[section] ?: 0) + 1
        }

        fun animExists(id: Int) = id < 0 || runCatching { world.definitions.getNullable(AnimDef::class.java, id) != null }.getOrDefault(true)

        fun gfxExists(id: Int) = id < 0 || runCatching { world.definitions.getNullable(SpotAnimDef::class.java, id) != null }.getOrDefault(true)

        val modelIds: Set<Int> = runCatching { world.filestore.index(7).archiveIds().toHashSet() }.getOrDefault(emptySet())

        // ---- census (existing tools, now in one report)
        val census = mutableListOf<String>()
        runCatching { census += "npcs: " + gg.rsmod.game.model.npc.NpcCensus.writeCsv(world, npcs = npcs) }
        runCatching { census += "item options: " + gg.rsmod.game.model.item.ItemOptionCensus.writeCsv(world) }
        runCatching { census += "items: " + gg.rsmod.game.model.item.ItemCensus.writeCsv(world) }
        runCatching { census += "objects: " + gg.rsmod.game.model.obj.ObjectCensus.writeCsv(world) }
        runCatching {
            File("./item_option_census.csv").readLines().drop(1).forEach { add("census", "item option without handler: $it") }
        }

        // ---- spawned npcs
        val seenIds = HashSet<Int>()
        val stuckReported = HashSet<String>()
        npcs.forEach { npc ->
            val def = runCatching { npc.def }.getOrNull() ?: return@forEach
            val name = def.name
            if (npc.getSize() == 1 && npc.walkRadius > 0 && stuck(world, npc.tile) && stuckReported.add("${npc.id}:${npc.tile}")) {
                add("npc-spawn-suspect", "npc ${npc.id} '$name' at ${npc.tile.x},${npc.tile.z},${npc.tile.height} cannot step north/east/south/west on the collision map (verify in game before fixing)")
            }
            if (!seenIds.add(npc.id)) return@forEach
            val attackable = def.options.any { it.equals("Attack", ignoreCase = true) }
            val combat = npc.combatDef
            if (attackable && combat === NpcCombatDef.DEFAULT) {
                add("npc-combat", "npc ${npc.id} '$name' has Attack but no combat definition (default stats/anims) at ${npc.tile.x},${npc.tile.z}")
            }
            if (combat !== NpcCombatDef.DEFAULT) {
                (listOf(combat.attackAnimation, combat.blockAnimation) + combat.deathAnimation).filter { !animExists(it) }.forEach {
                    add("npc-combat", "npc ${npc.id} '$name' combat animation $it does not exist in the cache")
                }
                if (attackable) {
                    val row = NpcAttacks.rowFor(npc.id)
                    val sectionSound = row?.attacks?.any { it.sounds.isNotEmpty() || it.targetSounds.isNotEmpty() || it.impactSounds.isNotEmpty() } == true
                    val tableSound = NpcCombatAudio.rowFor(npc.id)?.attack?.isNotEmpty() == true
                    // A radius-0 frame sound counts: NpcCombatAudio sends it to the player itself (sound audit 2026-09-29).
                    val frameSound = NpcCombatAudio.hasAudibleFrameSounds(npc, combat.attackAnimation) || NpcCombatAudio.unheardFrameSound(npc, combat.attackAnimation) >= 0
                    if (!sectionSound && !tableSound && !frameSound) add("npc-combat", "npc ${npc.id} '$name' attacks silently (no section, sound-table or frame sound)")
                    if (combat.attackAnimation < 0 && row?.attacks?.all { it.anim < 0 } != false) add("npc-combat", "npc ${npc.id} '$name' has no attack animation")
                    if (NpcCombatAudio.rowFor(npc.id)?.defend ?: -1 < 0 && !NpcCombatAudio.hasAudibleFrameSounds(npc, combat.blockAnimation) &&
                        NpcCombatAudio.unheardFrameSound(npc, combat.blockAnimation) < 0
                    ) {
                        add("npc-combat", "npc ${npc.id} '$name' has no hit/defend sound")
                    }
                    if (NpcCombatAudio.rowFor(npc.id)?.death ?: -1 < 0 && combat.deathAnimation.none { NpcCombatAudio.hasAudibleFrameSounds(npc, it) }) {
                        add("npc-combat", "npc ${npc.id} '$name' has no death sound")
                    }
                }
            }
            if (def.basId >= 0) {
                val bas = runCatching { world.definitions.getNullable(BasDef::class.java, def.basId) }.getOrNull()
                if (bas == null) {
                    add("npc-bas", "npc ${npc.id} '$name' uses BAS ${def.basId} which does not exist")
                } else {
                    listOf("ready" to bas.ready, "walk" to bas.walk).filter { (_, a) -> !animExists(a) }.forEach { (what, a) ->
                        add("npc-bas", "npc ${npc.id} '$name' BAS ${def.basId} $what animation $a does not exist in the cache")
                    }
                }
            }
        }

        // ---- one npc placed twice on one tile (two spawn sources, ROOT_CAUSES #66)
        val perTile = HashMap<String, Int>()
        npcs.forEach { perTile.merge("${it.id}@${it.tile.x},${it.tile.z},${it.tile.height}", 1, Int::plus) }
        perTile.filterValues { it > 1 }.forEach { (key, n) -> add("npc-spawn", "npc $key spawned $n times on the same tile (duplicate spawn source)") }

        // ---- npc attack table ids
        NpcAttacks.rows().forEach { row ->
            row.attacks.forEach { a ->
                val where = "row ${row.id} '${row.name}' section ${a.id}"
                listOf(a.anim, a.targetAnim, a.impactAnim).filter { !animExists(it) }.forEach { add("npc-attacks", "$where animation $it does not exist") }
                (a.gfx + a.targetGfx + a.impactGfx).map { it.id }.filter { !gfxExists(it) }.forEach { add("npc-attacks", "$where graphic $it does not exist") }
                a.projectiles.map { it.id }.filter { !gfxExists(it) }.forEach { add("npc-attacks", "$where projectile $it does not exist") }
                (a.sounds + a.targetSounds + a.impactSounds).map { it.id }.filter { !world.soundExists(it) }.forEach { add("npc-attacks", "$where sound $it does not exist") }
            }
        }

        // ---- npc sound table ids
        NpcCombatAudio.rows().forEach { row ->
            (row.attack.map { it.id } + row.defend + row.death).filter { it >= 0 && !world.soundExists(it) }.forEach {
                add("npc-sounds", "row ${row.id} '${row.name}' sound $it does not exist in the cache")
            }
        }

        // ---- traversal objects (ladders, stairs, ropes, trapdoors, cave/dungeon entrances): where each one puts the player
        // The player clicks from a tile next to the object; `delta` shifts THAT tile (ObjectTeleports.destination), so every free
        // neighbour is tried and only an object whose every approach lands somewhere the player cannot move is reported.
        val teleports = gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports
        teleports.all().forEach { e ->
            val at = e.tile ?: return@forEach
            val objTile = Tile(at.x, at.z, at.height)
            val approaches = listOf(objTile.transform(0, 1), objTile.transform(1, 0), objTile.transform(0, -1), objTile.transform(-1, 0), objTile).filter { !stuck(world, it) }
            if (approaches.isEmpty()) {
                add("traversal", "object ${e.id} '${e.option}' at ${at.x},${at.z},${at.height} has no free tile around it to click from (${e.source})")
                return@forEach
            }
            val landings = approaches.map { teleports.destination(e, it) }
            val bad = landings.filter { it.height !in 0..3 || stuck(world, it) }
            if (bad.size == landings.size) {
                val d = bad.first()
                add("traversal", "object ${e.id} '${e.option}' at ${at.x},${at.z},${at.height} lands at ${d.x},${d.z},${d.height} where the player cannot move (${e.source})")
            }
        }

        // ---- literal ids in the code (animate(123), graphic(456), playSound(789), AreaSound(id = ..), TileGraphic(id = ..))
        val patterns =
            listOf(
                "anim" to Regex("""\.animate\(\s*(\d+)"""),
                "graphic" to Regex("""\.graphic\(\s*(\d+)"""),
                "sound" to Regex("""playSound\(\s*(\d+)"""),
                "sound" to Regex("""AreaSound\([^)]*?\bid\s*=\s*(\d+)"""),
                "graphic" to Regex("""TileGraphic\([^)]*?\bid\s*=\s*(\d+)"""),
            )
        listOf(File("C:/RSPS/game/game/game/plugins/src/main"), File("C:/RSPS/game/game/game/src/main")).filter { it.isDirectory }.forEach { root ->
            root.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.forEach { f ->
                f.readLines().forEachIndexed { i, line ->
                    if (line.trimStart().startsWith("//") || line.trimStart().startsWith("*")) return@forEachIndexed
                    patterns.forEach { (kind, rx) ->
                        rx.findAll(line).forEach { m ->
                            val id = m.groupValues[1].toInt()
                            val ok =
                                when (kind) {
                                    "anim" -> animExists(id)
                                    "graphic" -> gfxExists(id)
                                    else -> world.soundExists(id)
                                }
                            if (!ok) add("code-ids", "${f.name}:${i + 1} $kind $id does not exist in the cache")
                        }
                    }
                }
            }
        }

        // ---- shops: items the cache does not have, and items sold for 0 (free) in a coin/item-currency shop
        runCatching { gg.rsmod.plugins.content.mechanics.exchange.GeHousePricing.allShops(world) }.getOrDefault(emptyList()).forEach { shop ->
            shop.items.filterNotNull().forEach { si ->
                if (runCatching { world.definitions.getNullable(ItemDef::class.java, si.item) }.getOrNull() == null) {
                    add("shop", "'${shop.name}' stocks item ${si.item} which does not exist in the cache")
                    return@forEach
                }
                val currency = shop.currency as? gg.rsmod.plugins.content.mechanics.shops.ItemCurrency
                val price = if (currency != null) runCatching { currency.sellPriceOf(world, si) }.getOrDefault(-1) else si.sellPrice ?: -1
                if (price == 0) add("shop", "'${shop.name}' sells item ${si.item} for 0")
            }
            if (shop.items.all { it == null }) add("shop", "'${shop.name}' has no stock at all")
        }

        // ---- wearable items with a missing worn model
        if (modelIds.isNotEmpty()) {
            val itemCount = runCatching { world.definitions.getCount(ItemDef::class.java) }.getOrDefault(0)
            for (id in 0 until itemCount) {
                val def = runCatching { world.definitions.getNullable(ItemDef::class.java, id) }.getOrNull() ?: continue
                if (def.noted || def.isPlaceholder || def.equipSlot < 0) continue
                listOf(def.maleWornModel, def.maleWornModel2).filter { it >= 0 && it !in modelIds }.forEach {
                    add("item-model", "item $id '${def.name}' worn model $it does not exist (invisible when worn)")
                }
            }
        }

        val dir = File("C:/RSPS/bugs")
        dir.mkdirs()
        val file = File(dir, "scan-${LocalDateTime.now().format(STAMP)}.txt")
        val out = StringBuilder()
        out.appendLine("RSPS world scan ${LocalDateTime.now()}  findings=${findings.size}")
        out.appendLine("per section: " + summary.entries.joinToString("  ") { "${it.key}=${it.value}" })
        out.appendLine()
        out.appendLine("census summaries (full CSVs next to the server: npc_inventory.csv, item_option_census.csv, item_inventory.csv, object_inventory.csv):")
        census.forEach { out.appendLine("  $it") }
        out.appendLine()
        findings.forEach { out.appendLine(it) }
        file.writeText(out.toString())
        return file
    }

    private val CARDINALS = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)

    private fun stuck(
        world: World,
        tile: Tile,
    ): Boolean = CARDINALS.none { world.collision.canTraverse(tile = tile, direction = it, projectile = false, water = false) }

    @Suppress("unused")
    private fun label(npc: Npc) = "${npc.id}#${npc.index}"
}
