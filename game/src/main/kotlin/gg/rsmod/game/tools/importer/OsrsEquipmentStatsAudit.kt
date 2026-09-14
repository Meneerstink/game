package gg.rsmod.game.tools.importer

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File
import java.util.Locale

/**
 * OSRS-IMPORT equipment stats audit (owner requirement 2026-09-14 ~04:55): `items.yml` equipment bonuses against the
 * pinned build-240 item params (OSRS Wiki "Equipment Stats"): attack/defence params 0-9, melee strength 10, prayer 11,
 * ranged strength 189 (gear) else 12 (ammunition, thrown weapons), magic damage 299 in tenths of a percent, attack speed
 * 14 on the weapon slot.
 *
 * Two audits:
 * - every imported item (`RSPS_IMPORT_ASSET_MAP.yml` `upstream_item:<id>` identities) on every field;
 * - every other wearable `items.yml` entry whose name is an OSRS wearable item (same id first, else a unique stat set
 *   among the OSRS items of that name), used for the owner's "all OSRS items ranged strength and magic damage" rule.
 *
 * Usage: `<report file> [--apply-imported] [--apply-ranged-magic]` - the apply modes rewrite only the named fields
 * of the named entries in `items.yml`, line by line, keeping every other byte.
 */
object OsrsEquipmentStatsAudit {
    const val ITEMS_YML = "C:\\RSPS\\game\\game\\data\\cfg\\items.yml"

    val BONUS_FIELDS =
        listOf(
            "attack_stab", "attack_slash", "attack_crush", "attack_magic", "attack_ranged",
            "defence_stab", "defence_slash", "defence_crush", "defence_magic", "defence_ranged",
            "melee_strength", "prayer", "ranged_strength", "magic_damage",
        )
    val FIELDS = BONUS_FIELDS + "attack_speed"
    val RANGED_MAGIC_FIELDS = setOf("ranged_strength", "magic_damage")

    /**
     * Name-matched 667 items whose OSRS ranged strength / magic damage is not applied, with the reason (recorded in
     * OSRS_IMPORT_STATUS.md). Everything else in the ranged/magic list takes the build-240 value.
     */
    val RANGED_MAGIC_EXCLUDED =
        mapOf(
            825 to "667 thrown javelin; the OSRS value is Heavy ballista ammunition (imported separately)",
            826 to "667 thrown javelin; OSRS ballista ammunition value",
            827 to "667 thrown javelin; OSRS ballista ammunition value",
            828 to "667 thrown javelin; OSRS ballista ammunition value",
            829 to "667 thrown javelin; OSRS ballista ammunition value",
            830 to "667 thrown javelin; OSRS ballista ammunition value",
            10146 to "salamander: OSRS 0 belongs to the level-based OSRS salamander formula, not implemented here",
            10147 to "salamander: OSRS level-based formula not implemented",
            10148 to "salamander: OSRS level-based formula not implemented",
            10149 to "salamander: OSRS level-based formula not implemented",
            10501 to "Snowball: holiday item, OSRS value 0 not proven to be the same item behaviour",
            21581 to "Blisterwood stake: name-only match to OSRS 33716, identity not proven",
        )

    /**
     * Owner decision 2026-09-14 (d): every name-matched 667 item that exists in OSRS takes the OSRS stats on every field, except
     * these 667 items (different items sharing a name or id, or owner-kept 667 behaviour).
     */
    val ALL_FIELDS_EXCLUDED: Map<Int, String> =
        RANGED_MAGIC_EXCLUDED.mapValues { (id, reason) ->
            when (id) {
                in 825..830 -> "owner decision (g): 667 thrown javelins keep 667 values; $reason"
                in 10146..10149 -> "owner decision (f): salamanders keep 667 values; $reason"
                else -> reason
            }
        } + (4212..4234).associateWith {
            "legacy crystal bow/shield definition: build 240 keeps it with all-zero stats (the OSRS crystal bow/shield are 23983/23991)"
        } + mapOf(10566 to "build 240 10566 'Fire cape' has all-zero stats; the OSRS combat Fire cape is 6570") +
            listOf(14641, 14642, 14645, 15432, 15433, 15434, 15435).associateWith {
                "667 team cape (+12 prayer, +8 defences; own examine such as \"It's a cape, and it's red.\"), a different item from the OSRS " +
                    "plain Red/Blue cape 1007/1021 (= the 667 zero-stat \"Cape\" 1007/1021)"
            }

    /**
     * 667 items that are the same item as the build-240 definition with the same id but are spelled differently (verified pair by pair
     * 2026-09-14 from the MISSING roster: "Bronze dart (p)" = "Bronze dart(p)", "Bronze fire arrows" = "Bronze fire arrow" and its
     * lit copy, "Ahrim's robe top" = "Ahrim's robetop", "Seers' ring", "Mages' book", "Third-age robe top" = "3rd Age robe top",
     * 667 "Dragon bolts (e)"/"Dragon bolts" 9244/9341 = OSRS "Dragonstone bolts (e)"/"Dragonstone bolts"). Ids from 11770 up
     * diverge (667 11770 is Root cutting, OSRS 11770 Seers ring (i)) and are never matched by id alone.
     */
    val SAME_ITEM_BY_ID: Set<Int> =
        (listOf(598, 942, 3094, 6731, 6889, 9244, 9341, 10338, 10340, 10342, 11217, 11222, 11227, 11228, 11229, 11231, 11233, 11234) +
            (812..817) + (870..876) + listOf(883, 885, 887, 889, 891, 893) + (2532..2541) + listOf(4712, 4714) + (4868..4871) +
            (4874..4877) + (5616..5641) + (5654..5667)).toSet()

    /**
     * 667 item -> the build-240 item it is, where both id and name differ (verified 2026-09-14): 667 "Virtus robe legs" = OSRS
     * "Virtus robe bottom"; 667 "Broad-tipped bolts" = OSRS "Broad bolts" (Slayer broad bolts, +100 ranged strength in both).
     */
    val SAME_ITEM_BY_NAME: Map<Int, Int> = mapOf(20167 to 26245, 20169 to 26245, 13280 to 11875, 15018 to 11770)

    data class Mismatch(val localId: Int, val name: String, val upstreamId: Int, val field: String, val local: Int?, val osrs: Int?) {
        override fun toString() = "$localId \"$name\" (osrs $upstreamId) $field: items.yml=${format(field, local)} osrs=${format(field, osrs)}"
    }

    data class NameMatch(val localId: Int, val name: String, val upstreamId: Int, val sameId: Boolean, val diffs: List<Mismatch>)

    fun format(field: String, value: Int?): String =
        when {
            value == null -> "-"
            field == "magic_damage" && value % 10 != 0 -> String.format(Locale.ROOT, "%.1f", value / 10.0)
            field == "magic_damage" -> (value / 10).toString()
            else -> value.toString()
        }

    /**
     * Same wear rule as the importer ([OsrsItemImportTool.isWearable]): a wear position alone is not enough - OSRS materials and the
     * empty Blazing/Sailing blowpipes ("options = Drop") carry one without a Wear/Wield option and cannot be worn.
     */
    fun osrsWearable(def: ModernItemDef) =
        OsrsItemImportTool.isWearable(def) && def.notedTemplate < 0 && def.placeholderTemplateId < 0 && def.boughtTemplateId < 0 && def.name != "null"

    fun osrsStats(def: ModernItemDef): Map<String, Int> {
        fun p(id: Int) = (def.params[id] as? Int) ?: 0
        val out = LinkedHashMap<String, Int>()
        (0 until 10).forEach { out[BONUS_FIELDS[it]] = p(it) }
        out["melee_strength"] = p(10)
        out["prayer"] = p(11)
        out["ranged_strength"] = if (def.params.containsKey(189)) p(189) else p(12)
        out["magic_damage"] = p(299)
        if (def.wearPos1 == 3) out["attack_speed"] = if (def.params.containsKey(14)) p(14) else 4
        return out
    }

    fun localStats(equipment: JsonNode): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        BONUS_FIELDS.forEach { out[it] = equipment.path(it).asInt() }
        out["magic_damage"] = Math.round(equipment.path("magic_damage").asDouble() * 10.0).toInt()
        if (equipment.path("equip_slot").asInt() == 3) out["attack_speed"] = equipment.path("attack_speed").asInt()
        return out
    }

    /**
     * Attack speed is compared only when both sides are weapon-slot items: a 667 entry whose `equip_slot` is not the weapon
     * slot (e.g. the 13444 "Abyssal whip" copy with slot 0) has no attack speed to compare (slot differences are recorded
     * separately in OSRS_IMPORT_STATUS.md, not rewritten by this audit).
     */
    private fun diff(localId: Int, name: String, upstreamId: Int, local: Map<String, Int>, osrs: Map<String, Int>) =
        FIELDS.filter { local[it] != osrs[it] }
            .filterNot { it == "attack_speed" && (local[it] == null || osrs[it] == null) }
            .map { Mismatch(localId, name, upstreamId, it, local[it], osrs[it]) }

    fun loadYml(file: File = File(ITEMS_YML)): List<JsonNode> = ObjectMapper(YAMLFactory()).readTree(file).toList()

    /**
     * Every decodable build-240 item; ids whose definitions use an opcode the decoder does not know (e.g. 117) are
     * collected in [undecodable] instead of being guessed.
     */
    fun loadSource(undecodable: MutableList<Int> = mutableListOf()): Map<Int, ModernItemDef> =
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            reader.files(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_ITEM)
                .mapNotNull { (id, bytes) ->
                    val def =
                        try {
                            ModernItemDefDecoder.decode(id, bytes)
                        } catch (e: RuntimeException) {
                            null
                        }
                    if (def == null || def.trailingBytes != 0) {
                        undecodable += id
                        null
                    } else {
                        id to def
                    }
                }.toMap()
        }

    /** Local item id -> upstream OSRS item id for every imported item. */
    fun importedMapping(assetMap: File = File(OsrsItemImportTool.ASSET_MAP)): Map<Int, Int> =
        ImportBatchOrchestrator.readExistingMapping(assetMap).itemLocalIdBySourceIdentity
            .filterKeys { it.startsWith("upstream_item:") }
            .entries.associate { (identity, local) -> local to identity.substringAfter(':').toInt() }

    fun auditImported(yml: List<JsonNode>, source: Map<Int, ModernItemDef>, mapping: Map<Int, Int>): List<Mismatch> {
        val byId = yml.associateBy { it.path("id").asInt() }
        return mapping.toSortedMap().flatMap { (local, upstream) ->
            val def = source[upstream] ?: error("upstream item $upstream missing from the pinned source cache")
            val node = byId[local] ?: return@flatMap listOf(Mismatch(local, def.name, upstream, "entry", null, 0))
            val equipment = node.path("equipment")
            val name = node.path("name").asText()
            if (equipment.isMissingNode || equipment.isNull) {
                val stats = osrsStats(def)
                if (osrsWearable(def) && BONUS_FIELDS.any { stats.getValue(it) != 0 }) {
                    listOf(Mismatch(local, name, upstream, "equipment", null, 1))
                } else {
                    emptyList()
                }
            } else {
                diff(local, name, upstream, localStats(equipment), osrsStats(def))
            }
        }
    }

    /**
     * A same-name copy of a main OSRS item, proven from the build-2686 definitions of every ambiguous name (2026-09-14): the Last Man Standing /
     * world-copy definitions (20429 Dragon platelegs, 20564 Proselyte hauberk, 20593 Armadyl godsword, 20417 / 20566 d'hide bodies, 20418 /
     * 20567 d'hide chaps, 25195 / 25207 / 25208 capes) carry param 59 = 1 and a token cost, and the arena copies (22665 Armadyl godsword, 22666
     * Rubber chicken) have the "Kill Area" option; the main items (4087, 9674, 11802, 2499, 2501, 2493, 2495, 1007, 1021, 4566) have neither.
     */
    fun isCopyDefinition(def: ModernItemDef): Boolean = def.params[59] == 1 || def.inventoryOptions.any { it == "Kill Area" }

    fun auditByName(yml: List<JsonNode>, source: Map<Int, ModernItemDef>, imported: Set<Int>, ambiguous: MutableList<String>): List<NameMatch> {
        val osrsByName = source.values.filter(::osrsWearable).groupBy { it.name.lowercase(Locale.ROOT) }
        return yml.mapNotNull { node ->
            val local = node.path("id").asInt()
            val equipment = node.path("equipment")
            if (local in imported || equipment.isMissingNode || equipment.isNull) return@mapNotNull null
            val name = node.path("name").asText()
            if (local in SAME_ITEM_BY_ID || local in SAME_ITEM_BY_NAME) {
                val def = source[SAME_ITEM_BY_NAME[local] ?: local]?.takeIf(::osrsWearable) ?: return@mapNotNull null
                return@mapNotNull NameMatch(local, name, def.id, true, diff(local, name, def.id, localStats(equipment), osrsStats(def)))
            }
            val candidates = osrsByName[name.lowercase(Locale.ROOT)] ?: return@mapNotNull null
            val same = candidates.firstOrNull { it.id == local }
            // Owner answer 2026-09-14 "same-name duplicate copies map to the main item": drop copy definitions when a main remains.
            val mains = candidates.filterNot(::isCopyDefinition)
            val chosen = if (same != null) listOf(same) else if (mains.isNotEmpty()) mains else candidates
            val statSets = chosen.map { osrsStats(it) }.distinct()
            if (statSets.size > 1) {
                ambiguous += "$local \"$name\": OSRS ${chosen.map { it.id }} carry different stats"
                return@mapNotNull null
            }
            val def = chosen.first()
            NameMatch(local, name, def.id, same != null, diff(local, name, def.id, localStats(equipment), statSets.single()))
        }
    }

    /** Rewrites `field: value` lines inside the named `- id:` blocks; a missing field is inserted before `attack_audio`. */
    fun applyToYml(file: File, changes: Map<Int, Map<String, Int>>) {
        val text = file.readText(Charsets.UTF_8)
        val newline = if ("\r\n" in text) "\r\n" else "\n"
        val lines = text.split(newline)
        val out = ArrayList<String>(lines.size + 64)
        var current = -1
        var pending: MutableMap<String, Int> = mutableMapOf()
        fun render(field: String, value: Int) = "    $field: ${if (field == "magic_damage") format(field, value).let { if ('.' in it) it else "$it.0" } else value}"
        for (line in lines) {
            if (line.startsWith("- id: ")) {
                check(pending.isEmpty()) { "item $current: fields $pending not written" }
                current = line.removePrefix("- id: ").trim().toInt()
                pending = changes[current]?.toMutableMap() ?: mutableMapOf()
            }
            val key = line.trim().substringBefore(':')
            if (pending.isNotEmpty() && line.startsWith("    ") && !line.startsWith("     ")) {
                if (key == "attack_audio") {
                    pending.forEach { (f, v) -> out += render(f, v) }
                    pending.clear()
                } else if (key in pending) {
                    out += render(key, pending.remove(key)!!)
                    continue
                }
            }
            out += line
        }
        check(pending.isEmpty()) { "item $current: fields $pending not written" }
        file.writeText(out.joinToString(newline), Charsets.UTF_8)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val report = File(args.firstOrNull { !it.startsWith("--") } ?: error("Usage: <report file> [--apply-imported] [--apply-ranged-magic]"))
        val yml = loadYml()
        val undecodable = mutableListOf<Int>()
        val source = loadSource(undecodable)
        val mapping = importedMapping()
        val imported = auditImported(yml, source, mapping)
        val ambiguous = mutableListOf<String>()
        val byName = auditByName(yml, source, mapping.keys, ambiguous)
        val sb = StringBuilder()
        sb.append("IMPORTED items=${mapping.size} mismatches=${imported.size}\n")
        imported.forEach { sb.append("  $it\n") }
        val rangedMagic = byName.filter { m -> m.diffs.any { it.field in RANGED_MAGIC_FIELDS } }
        sb.append("NAME-MATCHED 667 items=${byName.size} (same id ${byName.count { it.sameId }}) ranged/magic differences=${rangedMagic.size}\n")
        rangedMagic.forEach { m -> m.diffs.filter { it.field in RANGED_MAGIC_FIELDS }.forEach { sb.append("  ${if (m.sameId) "ID " else "NAME "}$it\n") } }
        sb.append("NAME-MATCHED other stat differences (not applied)\n")
        byName.forEach { m -> m.diffs.filter { it.field !in RANGED_MAGIC_FIELDS }.forEach { sb.append("  ${if (m.sameId) "ID " else "NAME "}$it\n") } }
        sb.append("AMBIGUOUS ${ambiguous.size}\n")
        ambiguous.forEach { sb.append("  $it\n") }
        // Owner scope 2026-09-14 ~05:30: every OSRS item that gives magic damage or ranged strength must exist here. MISSING =
        // decodable build-240 wearables with either stat whose id is not imported and whose name matches no items.yml entry.
        val localNames = yml.map { it.path("name").asText().lowercase(Locale.ROOT) }.toSet()
        val importedUpstream = mapping.values.toSet()
        val missing =
            source.values.filter(::osrsWearable)
                .filter { it.id !in importedUpstream && it.id !in SAME_ITEM_BY_ID && it.id !in SAME_ITEM_BY_NAME.values && it.name.lowercase(Locale.ROOT) !in localNames }
                .filter { def -> osrsStats(def).let { it.getValue("magic_damage") > 0 || it.getValue("ranged_strength") > 0 } }
                .sortedBy { it.id }
        sb.append("MISSING magic damage / ranged strength items=${missing.size}\n")
        missing.forEach { def ->
            val s = osrsStats(def)
            sb.append("  ${def.id} \"${def.name}\" slot=${def.wearPos1} magic_damage=${format("magic_damage", s["magic_damage"])} ranged_strength=${s["ranged_strength"]}\n")
        }
        sb.append("UNDECODABLE source items ${undecodable.size}: $undecodable\n")
        report.writeText(sb.toString(), Charsets.UTF_8)
        println("report: ${report.absolutePath} imported mismatches=${imported.size} ranged/magic=${rangedMagic.size}")

        val changes = LinkedHashMap<Int, MutableMap<String, Int>>()
        if ("--apply-imported" in args) {
            imported.filter { it.field in FIELDS }.forEach { changes.getOrPut(it.localId) { LinkedHashMap() }[it.field] = it.osrs!! }
        }
        if ("--apply-ranged-magic" in args) {
            rangedMagic.flatMap { it.diffs }.filter { it.field in RANGED_MAGIC_FIELDS && it.localId !in RANGED_MAGIC_EXCLUDED }
                .forEach { changes.getOrPut(it.localId) { LinkedHashMap() }[it.field] = it.osrs!! }
        }
        if ("--apply-all" in args) {
            byName.flatMap { it.diffs }.filter { it.localId !in ALL_FIELDS_EXCLUDED && it.osrs != null }
                .forEach { changes.getOrPut(it.localId) { LinkedHashMap() }[it.field] = it.osrs!! }
        }
        if (changes.isNotEmpty()) {
            applyToYml(File(ITEMS_YML), changes)
            println("items.yml: ${changes.size} entries rewritten")
        }
    }
}
