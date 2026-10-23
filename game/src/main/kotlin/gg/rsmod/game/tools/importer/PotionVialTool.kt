package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N254 (owner 2026-10-23: "waarom hebben alle potions een kurk nu ineens"; answer A: every potion without a cork). N242 put the
 * OSRS corked vial on the 116 classic potion doses; that change is undone byte for byte from its journal (CacheJournalRestoreTool
 * tx-20261023-190102). This tool gives the OSRS-imported potions (Super combat, Divine ..., Bastion ...) the same 2011 open vial,
 * so every dose of every potion looks alike and nothing changes on the vial after a sip (N242's original complaint).
 *
 * Both vials colour their liquid through the same model colour 61 (the recolour table of a classic potion was identical before
 * and after N242), and both use the same icon camera, so only the inventory model changes: OSRS vial -> the 667 vial of the same
 * dose. The OSRS lighting bytes (113 ambient / 114 contrast, written by the importer for the OSRS mesh) are removed, as no classic
 * potion has them on the 667 vial.
 *
 * Usage (after `:game:installDist`, the install lib folder on the class path): `PotionVialTool plan|apply`.
 */
object PotionVialTool {
    private const val INDEX_ITEM = 19

    /** The OSRS corked vial (local model ids, OsrsItemImportTool asset map) -> the 2011 vial of the same dose. */
    val OSRS_TO_667: Map<Int, Int> = mapOf(47509 to 2789, 47537 to 2697, 47564 to 2384, 47567 to 2621)

    /** The dose each OSRS vial holds (4, 3, 2, 1 sips). */
    val DOSE_OF: Map<Int, Int> = mapOf(47509 to 4, 47537 to 3, 47564 to 2, 47567 to 1)

    private val DOSE = Regex("""^.+?\s?\(([1-4])\)$""")

    /** [bytes] (a rev-667 item stream) on the 667 vial, or null when the item is not a potion dose on an OSRS vial. */
    fun uncorked(bytes: ByteArray): ByteArray? {
        val model = OsrsLookTool.inventoryModel(bytes) ?: return null
        val vial = OSRS_TO_667[model] ?: return null
        val name = ItemDefCodec.readName(bytes) ?: return null
        val dose = DOSE.matchEntire(name)?.groupValues?.get(1)?.toInt() ?: return null
        check(dose == DOSE_OF[model]) { "'$name' holds $dose doses but sits on the ${DOSE_OF[model]}-dose vial $model; refusing" }
        val present = OsrsLookTool.payloads(bytes)
        val out = ItemDefCodec.cloneWithOverrides(bytes, emptyMap(), shortOverrides = mapOf(1 to vial), removedOpcodes = setOf(113, 114).filter { it in present }.toSet())
        check(OsrsLookTool.inventoryModel(out) == vial) { "'$name': model rewrite failed" }
        check(OsrsLookTool.untouched(out) == OsrsLookTool.untouched(bytes)) { "'$name': more than the look changed" }
        check(OsrsLookTool.payloads(out)[40] == present[40]) { "'$name': the recolour table changed" }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.firstOrNull() ?: error("Usage: plan|apply")
        require(mode == "plan" || mode == "apply") { "Usage: plan|apply" }
        val mutations = ArrayList<CacheMutation>()
        val libraries = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            val library = libraries.first()
            for (id in CacheItemProbeTool.allItemIds(library)) {
                val current = CacheItemProbeTool.itemData(library, id) ?: continue
                val updated = uncorked(current) ?: continue
                libraries.drop(1).forEach { other ->
                    check(CacheItemProbeTool.itemData(other, id)?.contentEquals(current) == true) { "item $id differs between the caches" }
                }
                println("VIAL item $id '${ItemDefCodec.readName(current)}' model ${OsrsLookTool.inventoryModel(current)} -> ${OsrsLookTool.inventoryModel(updated)}")
                mutations += CacheMutation(INDEX_ITEM, id ushr 8, id and 0xFF, updated, "N254 potion $id on the 667 vial", CacheItemProbeTool.sha1(current))
            }
        } finally {
            libraries.forEach { it.close() }
        }
        if (mutations.isEmpty()) {
            println("NOTHING_TO_DO")
            return
        }
        val transaction = CacheTransaction(targets = OsrsItemImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("mutations=${mutations.size}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (mode == "plan") {
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
        println("APPLIED transaction=${applied.transactionId} writes=${applied.applied} skipped=${applied.skipped} VERIFY_OK")
    }
}
