package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.NpcDef
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * The Grand Exchange hall's Quest Guide as its own npc (owner 2026-09-26: "de npc die we als quest tutor gebruiken is te
 * basic ... hebben we niet een npc met een questcape" - not the Wise Old Man). Npc [NPC_ID] "Quest Guide" is dressed like a
 * player who has done every quest: the cache's Quest point hood and Quest point cape wear models over a blue Mystic robe
 * (top, bottom, gloves, boots), a bald head with the long white beard, on the player-style stance set [BAS]. Right-click:
 * "Talk-to" and "Quest choices" (opens "Choose your path").
 *
 * Model ids (revision-667 cache, `tools/cache667`): identikit 0 head 230 (chathead 39799), identikit 10 beard 249
 * (chathead 39254); Quest point hood 9814 -> 18914, Quest point cape 9813 -> 18946, Mystic robe top 4091 -> 4952 (sleeves,
 * item opcode 23) + 5028 (torso, opcode 24), bottom 4093 -> 4844, gloves 4095 -> 4953, boots 4097 -> 4955 - every male wear
 * model the items have (opcodes 23/24/78), dressed like a player: the robe top hides the arms kit (remove_arms), so
 * identikit 26 (arms 151) is not worn. Until 2026-09-30 only opcode 23 was taken - no torso 5028, arms kit 151 instead -
 * so the Quest Tutor had no body (owner: "Quest Tutor: body onzichtbaar").
 *
 * Usage: `java -cp <game lib> gg.rsmod.game.tools.importer.QuestGuideNpcTool [--apply]` - both caches, one transaction; a
 * re-run replaces only its own definition (pinned to what is there).
 */
object QuestGuideNpcTool {
    /** The first free npc id (probe 2026-09-26: highest 14481). */
    const val NPC_ID = 14482
    /** Owner 2026-09-26 (quest tab mission): in game this one npc is the "Quest Tutor". */
    const val NAME = "Quest Tutor"

    /** Names this tool itself wrote before, so a rename re-run still recognises its own definition. */
    val PREVIOUS_NAMES = setOf("Quest Guide")
    const val BAS = 4

    val MODELS = intArrayOf(230, 249, 18914, 18946, 4952, 5028, 4844, 4953, 4955)

    /** The items the Quest Tutor wears (every male wear model of each is in [MODELS]; guarded by QuestGuideNpcToolTests). */
    val WORN_ITEMS = intArrayOf(9814, 9813, 4091, 4093, 4095, 4097)
    val HEADS = intArrayOf(39799, 39254)
    val OPTIONS = arrayOf("Talk-to", "Quest choices", null, null, null)

    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        fun p1(v: Int) = out.write(v and 0xFF)
        fun p2(v: Int) {
            p1(v shr 8)
            p1(v)
        }
        fun str(s: String) {
            out.write(s.toByteArray(Charsets.ISO_8859_1))
            p1(0)
        }
        p1(1)
        p1(MODELS.size)
        MODELS.forEach { p2(it) }
        p1(2)
        str(NAME)
        p1(12)
        p1(1)
        OPTIONS.forEachIndexed { i, option ->
            if (option != null) {
                p1(30 + i)
                str(option)
            }
        }
        p1(60)
        p1(HEADS.size)
        HEADS.forEach { p2(it) }
        p1(95)
        p2(0) // no combat level shown
        p1(127)
        p2(BAS)
        p1(0)
        return out.toByteArray()
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val bytes = encode()
        val check = NpcDef(NPC_ID).also { it.decode(Unpooled.wrappedBuffer(bytes)) }
        check(check.name == NAME && check.basId == BAS && check.options.toList().take(2) == OPTIONS.toList().take(2)) { "re-decode mismatch" }
        val group = NPC_ID ushr 7
        val file = NPC_ID and 0x7F
        val library = CacheLibrary(NpcOptionsTool.TARGETS[0])
        val current: ByteArray?
        try {
            current = library.data(NpcOptionsTool.INDEX_NPC, group, file)
        } finally {
            library.close()
        }
        if (current != null) {
            val existing = NpcDef(NPC_ID).also { it.decode(Unpooled.wrappedBuffer(current)) }
            check(existing.name == NAME || existing.name in PREVIOUS_NAMES) { "npc $NPC_ID already holds '${existing.name}' - refusing to overwrite another npc" }
        }
        val mutation = CacheMutation(NpcOptionsTool.INDEX_NPC, group, file, bytes, "npc $NPC_ID '$NAME' (quest cape guide)", expectedCurrentSha1 = current?.let(CacheItemProbeTool::sha1))
        val transaction = CacheTransaction(targets = NpcOptionsTool.TARGETS, mutations = listOf(mutation))
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write npc $NPC_ID")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }
}
