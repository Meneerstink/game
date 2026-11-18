package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef

/**
 * N631 (Claude 18-11): the Keris partisan (ToA, local 24649-24653) shows the dagger combat tab (item param 686 = 5: Stab / Lunge / SLASH /
 * Block). OSRS Wiki Weapons/Categories 'Partisan': Stab (stab, accurate) / Lunge (stab, aggressive) / POUND (CRUSH, aggressive) / Block
 * (stab, defensive). The style icons are the same sprites: OSRS 240/239/238/237 = CombatStyles_(partisan,_stab/lunge/pound/block).png with
 * 0 differing pixels, and the 667 sprites 240/239/238/237 the dagger branch already pushes are pixel-equal too (n631_icon_match.py) - so no
 * sprite import.
 *
 * Clientscript 1142 (the only script that reads param 686: OC_PARAM -> SWITCH table 0, one case per category) gets one new case
 * [PARTISAN]: a copy of the branch of the Keris' current category whose third entry reads 'Pound' with tooltip 'Aggressive<br>Crush<br>
 * Strength XP' (same sprite 238), appended at the end and jumping to the same exit. The five Keris items get param 686 = [PARTISAN].
 * Server side: WeaponType.PARTISAN + AttackStyleData.PARTISAN + items.yml weapon_type (not this tool).
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.PartisanStyleSetTool plan|apply` (servers stopped for apply).
 */
object PartisanStyleSetTool {
    private val GAME_CACHE = RspsPaths.data("cache")
    private val FILE_SERVER_CACHE = RspsPaths.root("file-server/cache")
    private const val INDEX_SCRIPTS = 12
    private const val INDEX_ITEMS = 19
    const val STYLE_SCRIPT = 1142
    const val STYLE_PARAM = 686
    private const val DRAW_SCRIPT = 1143
    private const val GOSUB_WITH_PARAMS = 40
    private const val JOIN_STRING = 37

    /** New category; must equal WeaponType.PARTISAN.id on the server. */
    const val PARTISAN = 28
    val KERIS = (24649..24653).toList()

    /** Returns the patched script; [from] = the category the Keris has now. */
    fun patch(current: ByteArray, from: Int): ByteArray {
        val editor = ClientScriptEditor.decode(current)
        check(editor.encode().contentEquals(current)) { "clientscript $STYLE_SCRIPT does not round-trip" }
        val table = editor.switchTables()[0]
        check(table.none { it.first == PARTISAN }) { "category $PARTISAN already has a case in script $STYLE_SCRIPT" }
        val code = editor.code
        val start = editor.indexOf(editor.switchTarget(0, from) ?: error("no case for category $from"))
        val gosub = (start until code.size).first { code[it].opcode == GOSUB_WITH_PARAMS && code[it].int == DRAW_SCRIPT }
        val exit = code[gosub + 1]
        check(exit.opcode == ClientScriptEditor.BRANCH) { "branch of category $from does not end in a jump" }
        val branch = code.subList(start, gosub + 2)
        check(branch.dropLast(1).none { it.opcode in ClientScriptEditor.BRANCHES }) { "branch of category $from has inner jumps" }
        // Entry = name (string), sprite (int), style, <br>, type, <br>, xp, JOIN_STRING 5.
        val entries = branch.indices.filter { branch[it].opcode == ClientScriptEditor.STRING_CONSTANT &&
            branch.getOrNull(it + 1)?.opcode == ClientScriptEditor.PUSH_CONSTANT_INT }
        check(entries.map { branch[it].string } == listOf("Stab", "Lunge", "Slash", "Block")) {
            "category $from is not Stab/Lunge/Slash/Block: ${entries.map { branch[it].string }}"
        }
        val third = entries[2]
        check(branch[third + 1].int == 238 && branch[third + 4].string == "Slash" && branch[third + 7].opcode == JOIN_STRING) {
            "third entry layout differs: ${branch.subList(third, third + 8)}"
        }
        val copy = branch.mapIndexed { i, ins ->
            when (i) {
                third -> ClientScriptEditor.Ins(ins.opcode, string = "Pound")
                third + 4 -> ClientScriptEditor.Ins(ins.opcode, string = "Crush")
                else -> ClientScriptEditor.Ins(ins.opcode, ins.int, ins.string, ins.long, ins.target)
            }
        }
        editor.insert(code.size, copy)
        table += PARTISAN to copy.first()
        val out = editor.encode()
        ClientScriptEditor.decode(out).let { check(it.switchTarget(0, PARTISAN) != null) }
        return out
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = args.getOrNull(0) ?: "plan"
        require(mode == "plan" || mode == "apply" || mode == "keys") { "Usage: plan|apply|keys" }
        if (mode == "keys") {
            val lib = CacheLibrary(GAME_CACHE)
            try {
                val e = ClientScriptEditor.decode(lib.data(INDEX_SCRIPTS, STYLE_SCRIPT, 0)!!)
                println("CASES ${e.switchTables()[0].map { it.first }.sorted()}")
            } finally { lib.close() }
            return
        }
        val library = CacheLibrary(GAME_CACHE)
        val mutations = ArrayList<CacheMutation>()
        try {
            val definitions = DefinitionSet()
            definitions.load(library, ItemDef::class.java)
            val from = KERIS.map { id ->
                val def = definitions.get(ItemDef::class.java, id)
                check(def.name.startsWith("Keris partisan")) { "item $id is '${def.name}', not a Keris partisan" }
                def.params[STYLE_PARAM] as? Int ?: error("item $id has no param $STYLE_PARAM")
            }.distinct().single().let { it }
            if (from == PARTISAN) {
                println("NOTHING_TO_DO the Keris already has category $PARTISAN")
                return
            }
            val script = library.data(INDEX_SCRIPTS, STYLE_SCRIPT, 0) ?: error("clientscript $STYLE_SCRIPT missing")
            val patched = patch(script, from)
            mutations += CacheMutation(INDEX_SCRIPTS, STYLE_SCRIPT, 0, patched,
                "clientscript $STYLE_SCRIPT: Partisan category $PARTISAN (Stab/Lunge/Pound/Block, N631)", CacheItemProbeTool.sha1(script))
            KERIS.forEach { id ->
                val bytes = library.data(INDEX_ITEMS, id ushr 8, id and 0xFF) ?: error("item $id missing")
                val edited = ItemDefCodec.cloneWithOverrides(bytes, emptyMap(), intParams = mapOf(STYLE_PARAM to PARTISAN))
                println("ITEM $id: param $STYLE_PARAM $from -> $PARTISAN")
                mutations += CacheMutation(INDEX_ITEMS, id ushr 8, id and 0xFF, edited, "item $id: Partisan combat tab (N631)", CacheItemProbeTool.sha1(bytes))
            }
            println("SCRIPT $STYLE_SCRIPT: ${script.size} -> ${patched.size} bytes, case $PARTISAN copies category $from with Pound/Crush")
        } finally {
            library.close()
        }
        val tx = CacheTransaction(listOf(GAME_CACHE, FILE_SERVER_CACHE), mutations)
        val preflight = tx.preflight()
        preflight.forEach { println("PREFLIGHT $it") }
        val blocking = tx.blockingErrors(preflight)
        blocking.forEach { println("BLOCKING: $it") }
        if (mode == "plan") {
            println("PLAN_ONLY transaction=${tx.id} (nothing written)")
            return
        }
        check(blocking.isEmpty()) { "Preflight has blocking errors; refusing to apply." }
        val result = tx.apply(preflight)
        println("APPLIED transaction=${tx.id} applied=${result.applied} skipped=${result.skipped}")
        val verify = tx.verify()
        verify.forEach { println("VERIFY_ERROR: $it") }
        check(verify.isEmpty()) { "Post-apply verification failed; see journal ${tx.id} for rollback." }
        println("VERIFY_OK both targets hold intended bytes (journal ${tx.journalDir})")
    }
}
