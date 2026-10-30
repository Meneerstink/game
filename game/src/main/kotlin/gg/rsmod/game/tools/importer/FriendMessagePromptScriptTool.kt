package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary

/**
 * N440 (owner 2026-10-30, V9 "Bouw OSRS-venster"): "Message" on a friend opens the OSRS input box "Enter message to send to
 * <name>:" instead of only switching the typing line to "To <name>:".
 *
 * The 667 cache still has that box: clientscript 107 (chatbox 752, VARC 5 = 6, the name from its string argument into VARCSTR 23)
 * and its key handler 112, which on Enter sends CHAT_SENDPRIVATE (VARCSTR 23, VARCSTR 22) and closes the box. Nothing calls 107
 * any more; both "Message" options call the newer 2011 typing line instead (script 1558 with VARC 1650 = 1 and VARCSTR 23 =
 * the name):
 *
 *   clientscript 126 - onop of a friends-list row (550, rows made by script 125), case "Message"
 *   clientscript 86  - onop of a name in the chatbox (rows made by script 84), case "Message"
 *
 *   PUSH_CONSTANT_INT 1, POP_VARC 1650, PUSH_STRING_LOCAL n, POP_VARCSTR 23, PUSH_CONSTANT_INT 0, GOSUB 1558, RETURN
 * becomes
 *   PUSH_CONSTANT_INT 0, POP_VARC 1650, PUSH_STRING_LOCAL n, GOSUB 107, RETURN
 *
 * VARC 1650 = 0 so the normal chat is not left in "To <name>" mode. Quick Message, Tab-reply and the friends-chat stay as
 * they are. Applied transactionally to both caches; `--apply` writes, without it only the preflight.
 */
object FriendMessagePromptScriptTool {
    const val CLIENTSCRIPT_INDEX = 12
    const val PROMPT_SCRIPT = 107
    const val TYPING_LINE_SCRIPT = 1558
    const val PRIVATE_TARGET_VARC = 1650
    const val TARGET_NAME_VARCSTR = 23

    /** Script -> where its "Message" case lives. */
    val SCRIPTS = mapOf(126 to "friends list row", 86 to "chatbox name")

    private const val PUSH_INT = 0
    private const val POP_VARC = 43
    private const val PUSH_STRING_LOCAL = 35
    private const val POP_VARCSTR = 48
    private const val GOSUB = 40
    private const val RETURN = 21

    @JvmStatic
    fun main(args: Array<String>) {
        val apply = "--apply" in args
        val library = CacheLibrary(LootKeyInterfaceImportTool.TARGETS[0])
        val current =
            try {
                SCRIPTS.keys.associateWith { requireNotNull(library.data(CLIENTSCRIPT_INDEX, it, 0)) { "Missing clientscript $it" } }
            } finally {
                library.close()
            }
        val mutations =
            current.map { (id, data) ->
                CacheMutation(
                    CLIENTSCRIPT_INDEX,
                    id,
                    0,
                    patch(id, data),
                    "clientscript $id (${SCRIPTS[id]}): Message opens the OSRS box 'Enter message to send to' (script $PROMPT_SCRIPT)",
                    expectedCurrentSha1 = CacheItemProbeTool.sha1(data),
                )
            }
        val transaction = CacheTransaction(targets = LootKeyInterfaceImportTool.TARGETS, mutations = mutations)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKED: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY RUN: pass --apply to write clientscripts ${SCRIPTS.keys}")
            return
        }
        val applied = transaction.apply(plan)
        val problems = transaction.verify()
        println("APPLIED ${applied.applied} skipped=${applied.skipped} journal=${applied.journalDir}")
        check(problems.isEmpty()) { "verify failed: $problems" }
        println("VERIFY_OK transaction=${transaction.id}")
    }

    /** [data] with its "Message" case calling [PROMPT_SCRIPT]; unchanged when it already does. */
    fun patch(
        id: Int,
        data: ByteArray,
    ): ByteArray {
        val script = ClientScriptEditor.decode(data)
        val done = script.find(PUSH_INT to 0, POP_VARC to PRIVATE_TARGET_VARC, PUSH_STRING_LOCAL to null, GOSUB to PROMPT_SCRIPT, RETURN to null)
        if (done.size == 1) return data
        val old =
            script.find(
                PUSH_INT to 1, POP_VARC to PRIVATE_TARGET_VARC, PUSH_STRING_LOCAL to null, POP_VARCSTR to TARGET_NAME_VARCSTR,
                PUSH_INT to 0, GOSUB to TYPING_LINE_SCRIPT, RETURN to null,
            )
        check(old.size == 1) { "clientscript $id: expected one Message case calling $TYPING_LINE_SCRIPT, found ${old.size}" }
        val at = old.single()
        script.code[at].int = 0
        script.code[at + 5].int = PROMPT_SCRIPT
        // POP_VARCSTR 23 and the 1558 argument go: script 107 takes the name as its one string argument
        script.code.removeAt(at + 4)
        script.code.removeAt(at + 3)
        return script.encode()
    }
}
