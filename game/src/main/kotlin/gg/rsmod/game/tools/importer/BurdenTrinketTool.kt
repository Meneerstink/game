package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File

/**
 * N423 Beast of burden trinkets (owner 2026-10-29, design v6 approved): five items that look exactly like the grey Trinket of
 * fairies with the real emblem of a beast-of-burden pouch in the middle.
 *
 * Verbs:
 *  - `dump <outFile>` - the Trinket of fairies mesh and the five pouch meshes as JSON (vertices, faces, colours, priorities,
 *    alpha), read-only, for the composition step (work\claude-scripts\n423-trinkets\compose.py).
 */
object BurdenTrinketTool {
    const val TRINKET_OF_FAIRIES_MODEL = 63897

    /** Spirit kalphite, Bull ant, Spirit terrorbird, War tortoise, Pack yak pouch - inventory models. */
    val POUCH_MODELS = intArrayOf(30800, 30827, 30651, 30819, 30837)

    private const val MODEL_INDEX = ModelConvertTool.MODEL_INDEX

    fun json(model: ModelData): String =
        buildString {
            fun ints(a: IntArray?) = a?.joinToString(",", "[", "]") ?: "null"
            fun bytes(a: ByteArray?) = a?.joinToString(",", "[", "]") { (it.toInt() and 0xFF).toString() } ?: "null"
            fun shorts(a: ShortArray?) = a?.joinToString(",", "[", "]") { it.toInt().toString() } ?: "null"
            append("{\"vx\":${ints(model.vertexX)},\"vy\":${ints(model.vertexY)},\"vz\":${ints(model.vertexZ)}")
            append(",\"fa\":${ints(model.faceA)},\"fb\":${ints(model.faceB)},\"fc\":${ints(model.faceC)}")
            append(",\"colour\":${model.faceColour.joinToString(",", "[", "]") { (it.toInt() and 0xFFFF).toString() }}")
            append(",\"shading\":${bytes(model.shadingType)},\"priority\":${bytes(model.facePriority)},\"globalPriority\":${model.globalPriority}")
            append(",\"alpha\":${bytes(model.faceAlpha)},\"faceLabel\":${ints(model.faceLabel)},\"vertexLabel\":${ints(model.vertexLabel)}")
            append(",\"texture\":${shorts(model.faceTexture)},\"version\":${model.version}}")
        }

    @JvmStatic
    fun main(args: Array<String>) {
        when (args.getOrNull(0)) {
            "dump" -> {
                val out = File(requireNotNull(args.getOrNull(1)) { "dump <outFile>" })
                val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
                try {
                    val models =
                        (intArrayOf(TRINKET_OF_FAIRIES_MODEL) + POUCH_MODELS).associateWith { id ->
                            Rev667ModelDecoder.decode(requireNotNull(library.data(MODEL_INDEX, id, 0)) { "model $id missing" })
                        }
                    out.parentFile?.mkdirs()
                    out.writeText(models.entries.joinToString(",\n", "{\n", "\n}") { (id, m) -> "\"$id\":${json(m)}" })
                    models.forEach { (id, m) -> println("MODEL $id vertices=${m.vertexCount} faces=${m.faceCount} version=${m.version} textured=${m.faceTexture != null}") }
                } finally {
                    library.close()
                }
            }
            "build" -> build(File(requireNotNull(args.getOrNull(1)) { "build <meshFile> [--apply]" }), "--apply" in args)
            "npcs" -> upgradedNpcs("--apply" in args)
            "items" -> upgradedItems("--apply" in args)
            "remodel" -> remodel(File(requireNotNull(args.getOrNull(1)) { "remodel <meshFile> [--apply]" }), "--apply" in args)
            else -> error("Usage: dump <outFile> | build <meshFile> [--apply] | npcs [--apply]")
        }
    }

    const val PETS_TABLE = "data/cfg/pets/pets.tsv"
    const val CLASSIC_PETS_TABLE = "data/cfg/pets/pets_667.tsv"

    /** base pet npc -> its "Upgraded" copy, written by `npcs --apply` and read by the server (Pets). */
    const val UPGRADES_TABLE = "data/cfg/pets/pet_upgrades.tsv"

    /** The first npc id the copies may use (the cache's highest npc was 14671 on 2026-10-30; checked free before writing). */
    const val FIRST_UPGRADED_NPC = 14672

    /**
     * Every pet follower npc in the server's own order (Pets.load: the OSRS table, then the 667 table without the items or npcs the
     * OSRS table already names).
     */
    fun petNpcs(root: File = File(".")): List<Int> {
        fun rows(path: String) = File(root, path).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val osrs = rows(PETS_TABLE)
        val osrsItems = osrs.map { it[2].trim().toInt() }.toSet()
        val osrsNpcs = osrs.map { it[3].trim().toInt() }
        val classic = rows(CLASSIC_PETS_TABLE).map { it[1].trim().toInt() to it[2].trim().toInt() }.filter { (item, npc) -> item !in osrsItems && npc !in osrsNpcs }.map { it.second }
        return (osrsNpcs + classic).distinct()
    }

    /** Talk-to, Store, Pick-up, then the pet's other ops in their own order (design v6: "Talk-to, Store, Pick-up (+ Metamorphosis/Interact)"). */
    fun upgradedOptions(options: List<String?>): Array<String?> {
        val present = options.filterNotNull().filter { it.isNotBlank() }
        val talk = present.filter { it.equals("Talk-to", ignoreCase = true) }
        val pickUp = present.filter { it.equals("Pick-up", ignoreCase = true) }
        val rest = present - talk.toSet() - pickUp.toSet()
        val ordered = talk + "Store" + pickUp + rest
        require(ordered.size <= 5) { "no room for Store: $options" }
        return Array(5) { ordered.getOrNull(it) }
    }

    /**
     * N423 part 3: a copy of every pet follower npc, named "Upgraded <name>" with a Store op, same models, chat head, animations and
     * size; ids from [FIRST_UPGRADED_NPC] in [petNpcs] order. Writes [UPGRADES_TABLE] after the cache transaction verified.
     */
    private fun upgradedNpcs(apply: Boolean) {
        val bases = petNpcs()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        val rows = ArrayList<String>()
        try {
            val existingTable = File(UPGRADES_TABLE).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() && !it.startsWith("#") }
            bases.forEachIndexed { i, base ->
                val id = FIRST_UPGRADED_NPC + i
                val bytes = requireNotNull(library.data(NpcOptionsTool.INDEX_NPC, base ushr 7, base and 0x7F)) { "pet npc $base missing" }
                val before = gg.rsmod.game.fs.def.NpcDef(base).also { it.decode(io.netty.buffer.Unpooled.wrappedBuffer(bytes)) }
                val name = "Upgraded ${before.name}"
                val options = upgradedOptions(before.options.toList().take(5))
                val renamed = NpcRenameTool.rename(NpcOptionsTool.withOptions(bytes, options), name).renamed
                val after = gg.rsmod.game.fs.def.NpcDef(id).also { it.decode(io.netty.buffer.Unpooled.wrappedBuffer(renamed)) }
                val problems = ArrayList<String>()
                if (after.name != name) problems += "name ${after.name}"
                if (after.options.toList().take(5).map { it?.ifEmpty { null } } != options.toList()) problems += "options ${after.options.toList()}"
                if (after.size != before.size || after.basId != before.basId || after.walkMask != before.walkMask) problems += "size/bas/walk"
                if (after.chatheadModels?.toList() != before.chatheadModels?.toList()) problems += "chathead"
                if (after.combatLevel != before.combatLevel || after.isAttackable() != before.isAttackable()) problems += "combat"
                if (after.transforms != null || before.transforms != null) problems += "varbit transforms (a copy would turn back into another npc)"
                check(problems.isEmpty()) { "npc $base -> $id: $problems" }
                val current = library.data(NpcOptionsTool.INDEX_NPC, id ushr 7, id and 0x7F)
                if (current == null) {
                    mutations += CacheMutation(NpcOptionsTool.INDEX_NPC, id ushr 7, id and 0x7F, renamed, "N423 npc $id = $name (copy of $base)")
                } else {
                    check(current.contentEquals(renamed)) { "npc $id is taken by other content - refusing to overwrite" }
                }
                rows += "$base\t$id\t$name"
                if (i < 3 || i == bases.size - 1) println("PLAN npc $base '${before.name}' ${before.options.toList()} -> $id '$name' ${options.toList()}")
            }
            check(library.data(NpcOptionsTool.INDEX_NPC, (FIRST_UPGRADED_NPC + bases.size) ushr 7, (FIRST_UPGRADED_NPC + bases.size) and 0x7F) == null) {
                "the npc after the copies is taken - the id range is not at the end of the cache"
            }
            if (existingTable != null) check(existingTable == rows) { "$UPGRADES_TABLE differs from the plan - refusing to renumber" }
        } finally {
            library.close()
        }
        println("NPCS ${bases.size} copies ${FIRST_UPGRADED_NPC}..${FIRST_UPGRADED_NPC + bases.size - 1}, ${mutations.size} to write")
        val header = "# N423: base pet npc -> its Upgraded copy (BurdenTrinketTool npcs). base\tupgraded\tname"
        if (mutations.isEmpty()) {
            if (!File(UPGRADES_TABLE).exists()) File(UPGRADES_TABLE).writeText((listOf(header) + rows).joinToString("\n", postfix = "\n"))
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN transaction=${transaction.id}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        File(UPGRADES_TABLE).writeText((listOf(header) + rows).joinToString("\n", postfix = "\n"))
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} VERIFY_OK, wrote $UPGRADES_TABLE")
    }

    /** One trinket: item name, the pouch whose emblem it carries, its examine (items.yml). */
    data class Trinket(val name: String, val pouchModel: Int, val spirit: String, val slots: Int)

    val TRINKETS =
        listOf(
            Trinket("Kalphite burden trinket", 30800, "kalphite", 6),
            Trinket("Bull ant burden trinket", 30827, "bull ant", 9),
            Trinket("Terrorbird burden trinket", 30651, "terrorbird", 12),
            Trinket("Tortoise burden trinket", 30819, "war tortoise", 18),
            Trinket("Pack yak burden trinket", 30837, "pack yak", 30),
        )

    /** compose.py's text mesh: "model <pouch>", "v x y z", "f a b c colour priority" - fine units (version 13). */
    fun readMeshes(file: File): Map<Int, ModelData> {
        val out = LinkedHashMap<Int, ModelData>()
        var id = -1
        val vs = ArrayList<IntArray>()
        val fs = ArrayList<IntArray>()
        fun flush() {
            if (id == -1) return
            val m = ModelData(vs.size, fs.size, 0)
            vs.forEachIndexed { i, v ->
                m.vertexX[i] = v[0]
                m.vertexY[i] = v[1]
                m.vertexZ[i] = v[2]
            }
            val priority = ByteArray(fs.size)
            fs.forEachIndexed { i, f ->
                m.faceA[i] = f[0]
                m.faceB[i] = f[1]
                m.faceC[i] = f[2]
                m.faceColour[i] = f[3].toShort()
                priority[i] = f[4].toByte()
            }
            m.facePriority = priority
            m.version = 13
            out[id] = m
            vs.clear()
            fs.clear()
        }
        file.readLines().filter { it.isNotBlank() }.forEach { line ->
            val p = line.trim().split(' ')
            when (p[0]) {
                "model" -> {
                    flush()
                    id = p[1].toInt()
                }
                "v" -> vs += intArrayOf(p[1].toInt(), p[2].toInt(), p[3].toInt())
                "f" -> fs += IntArray(5) { p[it + 1].toInt() }
                else -> error("bad mesh line: $line")
            }
        }
        flush()
        return out
    }

    /**
     * The item: the Trinket of fairies' own icon camera (zoom 1414, xan 324, yan 95, y offset 16), the new mesh, inventory ops
     * Bind / Check / Unbind and an EMPTY fifth op - the client's default fifth op is "Drop" (ObjTypeList.defaultIops) and the
     * inventory menu skips an empty verb, so the trinket offers no Drop and no Destroy (design v6: never lost). No op 65: not
     * on the Grand Exchange.
     */
    fun itemBytes(
        name: String,
        model: Int,
        cost: Int,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun u8(v: Int) = out.write(v and 0xFF)
        fun u16(v: Int) {
            u8(v ushr 8)
            u8(v)
        }
        fun str(s: String) {
            out.write(s.toByteArray(Charsets.ISO_8859_1))
            u8(0)
        }
        require(model in 0..0xFFFF) { "item model $model does not fit opcode 1 (u16)" }
        u8(1); u16(model)
        u8(2); str(name)
        u8(4); u16(1414)
        u8(5); u16(324)
        u8(6); u16(95)
        u8(7); u16(0)
        u8(8); u16(16)
        u8(12); u16(cost ushr 16); u16(cost and 0xFFFF)
        u8(16)
        u8(35); str("Bind")
        u8(36); str("Check")
        u8(37); str("Unbind")
        u8(39); str("")
        u8(0)
        return out.toByteArray()
    }

    /** base pet item -> its "Upgraded" copy, written by `items --apply` and read by the server (Pets). */
    const val ITEM_UPGRADES_TABLE = "data/cfg/pets/pet_item_upgrades.tsv"

    /** The first item id the copies may use (the five trinkets end at 24236; checked free before writing). */
    const val FIRST_UPGRADED_ITEM = 24237

    private const val INDEX_ITEM = 19

    /** Every pet item in the server's own order (Pets.load: the OSRS table, then the 667 table without the items it already has). */
    fun petItems(root: File = File(".")): List<Int> {
        fun rows(path: String) = File(root, path).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        val osrs = rows(PETS_TABLE).map { it[2].trim().toInt() }
        val classic = rows(CLASSIC_PETS_TABLE).map { it[1].trim().toInt() }.filter { it !in osrs }
        return (osrs + classic).distinct()
    }

    /**
     * N436 part 1 (owner 2026-10-30: an upgraded pet "moet dat ook zo laten zien in de inventory"; choice: the name first, the
     * emblem later): a copy of every pet item named "Upgraded <name>" - same model, icon camera, options and flags; its note / lend
     * links are dropped (a pet has none to share). Ids from [FIRST_UPGRADED_ITEM] in [petItems] order; [ITEM_UPGRADES_TABLE] is
     * written after the cache transaction verified.
     */
    private fun upgradedItems(apply: Boolean) {
        val bases = petItems()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val mutations = ArrayList<CacheMutation>()
        val rows = ArrayList<String>()
        try {
            val existingTable = File(ITEM_UPGRADES_TABLE).takeIf { it.exists() }?.readLines()?.filter { it.isNotBlank() && !it.startsWith("#") }
            bases.forEachIndexed { i, base ->
                val id = FIRST_UPGRADED_ITEM + i
                val bytes = requireNotNull(library.data(INDEX_ITEM, base ushr 8, base and 0xFF)) { "pet item $base missing" }
                val baseName = requireNotNull(ItemDefCodec.readName(bytes)) { "pet item $base has no name" }
                val name = "Upgraded $baseName"
                val copy = ItemDefCodec.cloneWithOverrides(bytes, mapOf(2 to name), removedOpcodes = setOf(97, 98, 121, 122))
                check(ItemDefCodec.readName(copy) == name) { "item $base -> $id: name did not take" }
                val current = library.data(INDEX_ITEM, id ushr 8, id and 0xFF)
                if (current == null) {
                    mutations += CacheMutation(INDEX_ITEM, id ushr 8, id and 0xFF, copy, "N436 item $id = $name (copy of $base)")
                } else {
                    check(current.contentEquals(copy)) { "item $id is taken by other content - refusing to overwrite" }
                }
                rows += "$base\t$id\t$name"
                if (i < 3 || i == bases.size - 1) println("PLAN item $base '$baseName' -> $id '$name'")
            }
            val after = FIRST_UPGRADED_ITEM + bases.size
            check(library.data(INDEX_ITEM, after ushr 8, after and 0xFF) == null) { "the item after the copies is taken - the id range is not free" }
            if (existingTable != null) check(existingTable == rows) { "$ITEM_UPGRADES_TABLE differs from the plan - refusing to renumber" }
        } finally {
            library.close()
        }
        println("ITEMS ${bases.size} copies $FIRST_UPGRADED_ITEM..${FIRST_UPGRADED_ITEM + bases.size - 1}, ${mutations.size} to write")
        val header = "# N436: base pet item -> its Upgraded copy (BurdenTrinketTool items). base\tupgraded\tname"
        if (mutations.isEmpty()) {
            if (!File(ITEM_UPGRADES_TABLE).exists()) File(ITEM_UPGRADES_TABLE).writeText((listOf(header) + rows).joinToString("\n", postfix = "\n"))
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN transaction=${transaction.id}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        File(ITEM_UPGRADES_TABLE).writeText((listOf(header) + rows).joinToString("\n", postfix = "\n"))
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} VERIFY_OK, wrote $ITEM_UPGRADES_TABLE")
    }

    /** The five trinket items, in [TRINKETS] order (items.yml 24232-24236). */
    val TRINKET_ITEMS = intArrayOf(24232, 24233, 24234, 24235, 24236)

    /**
     * N438 (owner 2026-10-30: the emblem looks "te low quality"; choice "scherper en groter"): a new [meshFile] (compose.py) is
     * written over the model each trinket item already uses (its item op 1) - no new ids, the items stay as they are.
     */
    private fun remodel(
        meshFile: File,
        apply: Boolean,
    ) {
        val meshes = readMeshes(meshFile)
        val mutations = ArrayList<CacheMutation>()
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        try {
            TRINKETS.forEachIndexed { i, t ->
                val item = TRINKET_ITEMS[i]
                val def = requireNotNull(library.data(19, item ushr 8, item and 0xFF)) { "item $item missing" }
                check(def[0].toInt() == 1) { "item $item does not start with its model (op 1)" }
                val model = ((def[1].toInt() and 0xFF) shl 8) or (def[2].toInt() and 0xFF)
                val mesh = requireNotNull(meshes[t.pouchModel]) { "no mesh for pouch ${t.pouchModel} in $meshFile" }
                val bytes = Rev667ModelEncoder.encode(mesh)
                check(ModelConvertTool.compare(mesh, Rev667ModelDecoder.decode(bytes)).isEmpty()) { "${t.name}: the mesh does not decode back" }
                val old = requireNotNull(library.data(MODEL_INDEX, model, 0)) { "model $model missing" }
                println("PLAN item $item '${t.name}' model $model: ${old.size} -> ${bytes.size} bytes, faces ${mesh.faceCount}")
                mutations += CacheMutation(MODEL_INDEX, model, 0, bytes, "N438 ${t.name} sharper, bigger emblem", CacheItemProbeTool.sha1(old))
            }
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN transaction=${transaction.id} mutations=${mutations.size}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }

    private fun build(
        meshFile: File,
        apply: Boolean,
    ) {
        val meshes = readMeshes(meshFile)
        val encoded =
            TRINKETS.associate { t ->
                val mesh = requireNotNull(meshes[t.pouchModel]) { "no mesh for pouch ${t.pouchModel} in $meshFile" }
                val bytes = Rev667ModelEncoder.encode(mesh)
                val back = Rev667ModelDecoder.decode(bytes)
                val differences = ModelConvertTool.compare(mesh, back)
                check(differences.isEmpty()) { "${t.name}: the mesh does not decode back: $differences" }
                println("MESH ${t.name} vertices=${mesh.vertexCount} faces=${mesh.faceCount} bytes=${bytes.size}")
                t.name to bytes
            }
        val targets = OsrsItemImportTool.TARGETS
        val census = ModelNamespaceCensusTool.census(targets[0], targets[1], File(OsrsItemImportTool.ASSET_MAP))
        check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
        // Item op 1 is a u16: only model ids up to 65535.
        val candidates = OsrsItemImportTool.meshModelCandidates(census).filter { it <= 0xFFFF }
        val batch =
            TRINKETS.map { t ->
                val model = ImportBatchOrchestrator.ModelSource("n423_trinket_mesh:${t.pouchModel}", "model", encoded.getValue(t.name))
                ImportBatchOrchestrator.ItemSource("n423_trinket:${t.slots}", t.name, listOf(model)) { local ->
                    itemBytes(t.name, local.getValue(model.sourceIdentity), 10_000 * t.slots)
                }
            }
        val result =
            ImportBatchOrchestrator.orchestrate(
                targets = targets,
                batch = batch,
                assetMapFile = File(OsrsItemImportTool.ASSET_MAP),
                modelSafeFreeStart = census.safeFreeRange.first,
                modelSafeFreeEnd = census.safeFreeRange.last,
                apply = apply,
                modelCandidates = candidates,
            ) { plan ->
                plan.items.forEach { println("PLAN item ${it.localId} '${it.source.name}' models=${it.models.map { m -> m.localId }} bytes=${it.bytes.size}") }
            }
        println("TRANSACTION ${result.transactionId} applied=${result.applyResult != null} finalized=${result.newlyFinalized.size}")
    }
}
