package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.StructDef
import io.netty.buffer.Unpooled
import java.io.File
import java.security.MessageDigest

/** OSRS struct 6245 setting in the existing 667 Settings tab. Default PLAN/PREFLIGHT. */
object ClueStashSettingCacheTool {
    const val SOURCE = 6245
    const val GROUP = 261
    private const val SOURCE_SHA = "c160459064efde53b1fb24881c3a17c85d8e12d8999e5ed327b63a177f6b1bae"

    fun source(raw: ByteArray): Pair<String, String> {
        val hash = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        require(hash == SOURCE_SHA) { "STASH setting source changed; review struct 6245" }
        val buffer = Unpooled.wrappedBuffer(raw)
        val def = StructDef(SOURCE).also { it.decode(buffer) }
        require(!buffer.isReadable)
        return def.getString(1086) to def.getString(1096)
    }

    /** y=176..202 is between the native 133..173 and 206..246 rows. No old component changes. */
    fun components(first: Int, font: Int, off: Int, label: String): List<OsrsInterfacePortTool.C> {
        require(first in 26..65532 && font >= 0 && off >= 0 && label.isNotBlank())
        fun component(id: Int, type: Int, x: Int, y: Int, w: Int, h: Int, parent: Int,
                      sprite: Int = -1, text: String = "", ops: List<String> = emptyList()) =
            OsrsInterfacePortTool.C(id, type, x, y, w, h, parent, 0, 0, 0, 0, false,
                sprite = sprite, font = if (type == 4) font else -1, text = text,
                lineHeight = 11, alignY = 1, shadow = true, colour = 0xff981f,
                ops = ops, opBase = if (ops.isEmpty()) "" else label)
        return listOf(
            component(first, 0, 8, 176, 174, 26, 0, ops = listOf("Toggle")),
            component(first + 1, 5, 0, 4, 17, 17, first, sprite = off),
            component(first + 2, 4, 22, 0, 152, 26, first, text = label),
        )
    }

    @JvmStatic fun main(args: Array<String>) {
        require(args.size >= 2) { "Usage: <runtime-mapping.tsv> <plan.tsv> [--apply]" }
        val runtime = File(args[0]); val plan = File(args[1]); val apply = "--apply" in args
        require(runtime.canonicalFile != plan.canonicalFile)
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = OsrsFxImportTool.existingFx(assetMap)
        val caches = OsrsItemImportTool.TARGETS.map { CacheLibrary(it) }
        try {
            val originals = caches.map { cache -> cache.index(3).archive(GROUP)!!.fileIds().associateWith { file ->
                cache.data(3, GROUP, file) ?: error("Settings component $file missing")
            } }
            check(originals.all { it.keys == originals.first().keys }) { "Settings cache component sets differ" }
            originals.first().forEach { (id, raw) -> check(originals.all { it.getValue(id).contentEquals(raw) }) }
            check(InterfaceHookProbeTool.componentSize(originals.first().getValue(0)) == (190 to 261))
            val fonts = caches.map { it.index(8).archive("p11_full")?.id ?: error("Native p11 font missing") }
            check(fonts.distinct().size == 1)
            val first = existing["clue_stash_setting_button:$SOURCE"] ?: (originals.first().keys.maxOrNull()!! + 1)
            check(originals.first().keys.all { it in 0..25 || it in first..first + 2 &&
                existing["clue_stash_setting_${listOf("button", "checkbox", "label")[it - first]}:$SOURCE"] == it }) {
                "Settings grew beyond the reviewed layout; recheck the free band before adding this setting"
            }
            val off = KeybindingInterfaceImportTool.sprite(8378)
            val on = KeybindingInterfaceImportTool.sprite(8379)
            val label: String
            ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
                label = source(reader.file(2, 34, SOURCE) ?: error("Source setting struct missing")).first
                for ((osrs, local) in listOf(8378 to off, 8379 to on)) {
                    val bytes = reader.file(8, osrs, 0) ?: error("Source checkbox $osrs missing")
                    check(caches.all { it.data(8, local, 0)?.contentEquals(bytes) == true }) {
                        "Installed keybinding checkbox $local is not source $osrs; review before importing"
                    }
                }
            }
            val kinds = listOf("button", "checkbox", "label")
            val mutations = components(first, fonts.first(), off, label).mapIndexed { index, component ->
                val old = originals.first()[component.id]
                check(old == null || existing["clue_stash_setting_${kinds[index]}:$SOURCE"] == component.id) {
                    "Unowned Settings destination $GROUP:${component.id}"
                }
                CacheMutation(3, GROUP, component.id, OsrsInterfacePortTool.encode(component),
                    "STASH source setting $SOURCE ${kinds[index]}", old?.let(CacheItemProbeTool::sha1))
            }
            val tx = CacheTransaction(OsrsItemImportTool.TARGETS, mutations)
            val preflight = tx.preflight()
            check(tx.blockingErrors(preflight).isEmpty()) { "STASH setting preflight blocked: ${tx.blockingErrors(preflight)}" }
            val mapping = linkedMapOf("interface" to GROUP, "button" to first, "checkbox" to first + 1,
                "label" to first + 2, "off" to off, "on" to on)
            val text = mapping.entries.joinToString("\n", "# CLUE STASH setting ${tx.id}; applied=$apply\n", "\n") {
                "${it.key}\t$SOURCE\t${it.value}"
            }
            plan.parentFile?.mkdirs(); plan.writeText(text)
            println("PREFLIGHT ${tx.id} mutations=${mutations.size} ${mapping.entries.joinToString()}")
            if (!apply) { println("PLAN_ONLY; caches/ledger/runtime mapping unchanged"); return }
            val result = tx.apply(preflight)
            val errors = tx.verify()
            if (errors.isNotEmpty()) { tx.rollback(); error("STASH setting verification rolled back: $errors") }
            val records = kinds.mapIndexedNotNull { index, kind ->
                if (existing["clue_stash_setting_$kind:$SOURCE"] == first + index) null else
                    "  - fx_kind: clue_stash_setting_$kind\n    upstream_fx_id: $SOURCE\n    local_fx_id: ${first + index}\n    status: IMPORTED_BY_CLUE_STASH_SETTING_TOOL\n    transaction: ${tx.id}\n"
            }
            if (records.isNotEmpty()) assetMap.appendText(records.joinToString("", "\n"))
            runtime.parentFile?.mkdirs(); runtime.writeText(text)
            println("APPLIED ${tx.id} writes=${result.applied} VERIFY_OK")
        } finally { caches.forEach { it.close() } }
    }
}
