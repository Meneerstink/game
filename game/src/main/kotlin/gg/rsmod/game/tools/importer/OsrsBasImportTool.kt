package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Body-animation sets (BASType, config group 32) for imported OSRS weapons whose stand / walk / run sequences are their own
 * (owner 2026-09-17c: "heavy ballista fires as a crossbow"; every weapon looks exactly like OSRS).
 *
 * OSRS keeps these per weapon as plain sequence ids on the item (stand, walk, run, turn, walk-back/left/right); rev 667 keys them
 * through a BASType the appearance block names. A new BAS is the 667 default human set [TEMPLATE] with only the movement
 * sequences replaced by the already imported OSRS sequences (`OsrsFxImportTool` batch "weaponseq1", local ids from the asset
 * map); everything else of the template (turn speeds, hill data, ...) is kept. The template's random idle list (opcode 52) is
 * dropped when the stand changes, otherwise the default stand would still play at random.
 *
 * Which OSRS sequence is which slot: RuneLite `gameval/AnimationID.java` names (BALLISTA_READY / _WALK / _RUN,
 * HUMAN_WEAPON_BOW_VENATOR01_READY / _WALK / _RUN / _STEPLEFT / _STEPRIGHT / _TURN, ABYSSAL_DAGGER_IDLE) and the Zenyte-lineage
 * equipment table (Near-Reality `ItemDefinitions.json`: ballista stand 7220 walk 7223 run 7221 and 7223 for every walk turn).
 *
 * Usage: `[--apply]`
 */
object OsrsBasImportTool {
    const val INDEX_CONFIG = 2
    const val GROUP_BAS = 32

    /** `PlayerUpdateBlockSegment`'s fallback render animation: the default human set. */
    const val TEMPLATE = 1426

    /** Null = keep the template's sequence. Values are OSRS sequence ids. */
    class Stance(
        val name: String,
        val ready: Int? = null,
        val walk: Int? = null,
        val run: Int? = null,
        val readyTurn: Int? = null,
        val walkBack: Int? = null,
        val walkLeft: Int? = null,
        val walkRight: Int? = null,
        /** The 667 BAS the rest is taken from: the default human set, or the item's own 667 class set (28 = staff) when only the stand changes. */
        val template: Int = TEMPLATE,
    )

    val STANCES =
        listOf(
            Stance("ballista", ready = 7220, walk = 7223, run = 7221, walkBack = 7223, walkLeft = 7223, walkRight = 7223),
            Stance("venator_bow", ready = 9857, walk = 9859, run = 9860, readyTurn = 9863, walkLeft = 9861, walkRight = 9862),
            Stance("abyssal_dagger", ready = 3296),
            // DH_SWORD_UPDATE_READY / WALK / RUN / TURNONSPOT / WALK_LEFT / WALK_RIGHT: the imported godswords and the gilded 2h sword.
            Stance("godsword_osrs", ready = 7053, walk = 7052, run = 7043, readyTurn = 7044, walkLeft = 7048, walkRight = 7047),
            // HUMAN_NIGHTMARE_STAFF_READY on the 667 staff set (Zenyte-lineage table: stand 4504, walk 1205, run 1210 = the staff set).
            Stance("nightmare_staff", ready = 4504, template = 28),
            // HUMAN_ZAMORAKSPEAR_READY / WALK_F / RUN / TURNONSPOT / WALK_B / WALKLEFT / WALKRIGHT: Blue moon spear.
            Stance("zamorak_spear_osrs", ready = 1713, walk = 1703, run = 1707, readyTurn = 1702, walkBack = 1704, walkLeft = 1706, walkRight = 1705),
        )

    /** Payload width of every fixed-size BASType opcode (`BASType.decode`, rev-667 client). */
    private val FIXED =
        mapOf(1 to 4, 26 to 2, 29 to 1, 30 to 2, 31 to 1, 32 to 2, 33 to 2, 34 to 1, 35 to 2, 36 to 2, 37 to 1, 43 to 2, 44 to 2, 45 to 2, 53 to 0, 54 to 2, 55 to 3, 56 to 7) +
            (2..9).associateWith { 2 } + (38..42).associateWith { 2 } + (46..51).associateWith { 2 }

    fun decode(bytes: ByteArray): LinkedHashMap<Int, MutableList<ByteArray>> {
        val out = LinkedHashMap<Int, MutableList<ByteArray>>()
        var pos = 0
        while (true) {
            val op = bytes[pos++].toInt() and 0xFF
            if (op == 0) break
            val width =
                when (op) {
                    27 -> 13
                    28 -> 1 + (bytes[pos].toInt() and 0xFF)
                    52 -> 1 + (bytes[pos].toInt() and 0xFF) * 3
                    else -> FIXED[op] ?: error("unknown BASType opcode $op")
                }
            out.getOrPut(op) { mutableListOf() } += bytes.copyOfRange(pos, pos + width)
            pos += width
        }
        check(pos == bytes.size) { "BASType has ${bytes.size - pos} trailing bytes" }
        return out
    }

    fun encode(ops: Map<Int, List<ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ops.forEach { (op, payloads) ->
            payloads.forEach { payload ->
                out.write(op)
                out.write(payload)
            }
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())

    fun build(
        template: ByteArray,
        stance: Stance,
        localSeq: (Int) -> Int,
    ): ByteArray {
        val ops = decode(template)
        val base = ops[1]?.single() ?: error("template has no ready/walk pair")
        val ready = stance.ready?.let(localSeq) ?: (((base[0].toInt() and 0xFF) shl 8) or (base[1].toInt() and 0xFF))
        val walk = stance.walk?.let(localSeq) ?: (((base[2].toInt() and 0xFF) shl 8) or (base[3].toInt() and 0xFF))
        ops[1] = mutableListOf(u16(ready) + u16(walk))
        if (stance.ready != null) ops.remove(52)
        stance.run?.let { ops[6] = mutableListOf(u16(localSeq(it))) }
        stance.readyTurn?.let {
            ops[38] = mutableListOf(u16(localSeq(it)))
            ops[39] = mutableListOf(u16(localSeq(it)))
        }
        stance.walkBack?.let { ops[40] = mutableListOf(u16(localSeq(it))) }
        stance.walkLeft?.let { ops[41] = mutableListOf(u16(localSeq(it))) }
        stance.walkRight?.let { ops[42] = mutableListOf(u16(localSeq(it))) }
        return encode(ops).also { decode(it) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = OsrsFxImportTool.existingFx(assetMap)
        val library = CacheLibrary(OsrsFxImportTool.TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<Pair<String, Int>>()
        try {
            var next = (library.index(INDEX_CONFIG).archive(GROUP_BAS)?.fileIds()?.maxOrNull() ?: error("no BAS group")) + 1
            STANCES.forEach { stance ->
                val template = library.data(INDEX_CONFIG, GROUP_BAS, stance.template) ?: error("BAS ${stance.template} missing")
                val bytes = build(template, stance) { osrs -> existing["seq:$osrs"] ?: error("OSRS sequence $osrs is not imported") }
                val known = existing["bas:${stance.ready ?: stance.walk}"]
                val id = known ?: next++
                val current = library.data(INDEX_CONFIG, GROUP_BAS, id)?.let { CacheItemProbeTool.sha1(it) }
                mutations += CacheMutation(INDEX_CONFIG, GROUP_BAS, id, bytes, "osrs bas ${stance.name}", expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
                if (known == null) records += "${stance.ready ?: stance.walk}" to id
                println("PLAN bas ${stance.name} -> $id")
            }
        } finally {
            library.close()
        }
        val transaction = CacheTransaction(targets = OsrsFxImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${mutations.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { (upstream, localId) ->
                block.append("  - fx_kind: bas\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_BAS_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} bas entries")
        }
    }
}
