package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import java.io.File
import java.util.UUID

/**
 * Restores REPLACED entries from a persisted [CacheTransaction] journal in a fresh transaction (preflight, backup,
 * active-consumer guard, apply, verify). Supports decoded .orig and raw .stored + .ref backups, without decoding ciphertext.
 * Both targets must have identical backups and current content. CREATE entries (.absent) are deliberately excluded.
 * Usage: `<journalDir> <regex over "idx<i>_grp<g>_file<f>"> [--apply]`. Default is read-only.
 */
object CacheJournalRestoreTool {
    private val LOCATION = Regex("""idx(\d+)_grp(\d+)_file(\d+)""")

    @JvmStatic
    fun main(args: Array<String>) {
        val journal = File(args.getOrNull(0) ?: error("Usage: <journalDir> <filter regex> [--apply]"))
        val filter = Regex(args.getOrNull(1) ?: error("Usage: <journalDir> <filter regex> [--apply]"))
        val apply = "--apply" in args
        val targets = OsrsItemImportTool.TARGETS
        val slugs = targets.associateBy { it.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_') }
        val names = (journal.listFiles() ?: error("not a journal directory: $journal")).map { it.name }.toSet()
        val backups = mutableMapOf<String, MutableMap<String, CacheMutation>>()
        val currentPins = mutableMapOf<String, MutableMap<String, String?>>()
        val recordedPins = File(journal, "plan.txt").takeIf { it.isFile }?.readLines().orEmpty()
            .filter { it.startsWith("plan\t") }.associate { line ->
                val fields = line.split('\t').drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
                val slug = fields.getValue("target").replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
                "${slug}__${fields.getValue("location")}" to fields.getValue("current_sha1")
            }
        names.filter { it.endsWith(".orig") || it.endsWith(".stored") }.sorted().forEach { name ->
            val base = name.substringBeforeLast('.')
            if ("$base.absent" in names) return@forEach
            val parts = base.split("__", limit = 2)
            check(parts.size == 2) { "invalid journal filename $name" }
            val (slug, location) = parts
            if (!filter.containsMatchIn(location)) return@forEach
            val target = slugs[slug] ?: error("unknown target slug $slug")
            val (index, group, file) = (LOCATION.matchEntire(location) ?: error("invalid location $location")).destructured
            val bytes = File(journal, name).readBytes()
            val reference = if (name.endsWith(".stored")) StoredCacheReference.read(File(journal, "$base.ref")) else null
            if (reference != null) {
                check(CacheItemProbeTool.sha1(bytes) == recordedPins[base]) { "stored backup SHA1 differs from original plan: $name" }
            }
            val current = CacheLibrary(target).let { library ->
                try {
                    if (reference != null) library.index(index.toInt()).readArchiveSector(group.toInt())?.data
                    else library.data(index.toInt(), group.toInt(), file.toInt())
                } finally {
                    library.close()
                }
            }
            val pin = current?.let { CacheItemProbeTool.sha1(it) }
            val mutation = CacheMutation(index.toInt(), group.toInt(), file.toInt(), bytes,
                "restore $location from ${journal.name}", expectedCurrentSha1 = pin, storedReference = reference)
            check(backups.getOrPut(target) { linkedMapOf() }.put(location, mutation) == null) { "duplicate backup: $base" }
            currentPins.getOrPut(target) { linkedMapOf() }[location] = pin
        }
        val locations = backups.values.map { it.keys.toSet() }.distinct()
        check(locations.size == 1 && backups.keys.size == targets.size) { "the targets' journals differ or no backups matched" }
        val mutations = backups.getValue(targets[0]).values.toList()
        mutations.forEach { first ->
            val location = first.describeLocation()
            targets.drop(1).forEach { target ->
                val other = backups.getValue(target).getValue(location)
                check(first.newBytes!!.contentEquals(other.newBytes!!) && first.storedReference == other.storedReference) {
                    "the targets' backup bytes/reference differ at $location"
                }
                check(currentPins.getValue(targets[0])[location] == currentPins.getValue(target)[location]) {
                    "the targets' current content differs at $location"
                }
            }
        }
        println("RESTORE ${mutations.size} files per cache from ${journal.name}")
        // Unique even for two CLI invocations within a second; never overwrite the source journal or an earlier undo.
        val transaction = CacheTransaction(targets, mutations, id = "restore-${CacheTransaction.newTransactionId()}-${UUID.randomUUID()}")
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) return println("DRY RUN: pass --apply")
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
