package gg.rsmod.plugins.content.combat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * RC-1 (HANDOFF_CURRENT.md, RCV-005 step 1): roster-wide source coverage of every place the engine
 * interrupts a player. [gg.rsmod.game.model.queue.QueueTask.persistent] only helps if every call
 * site picks the right mode, so each message handler that calls `fullInterruption` must be
 * classified as a hard stop (walk, teleport, new entity interaction) or a soft action (inventory
 * option, interface target). A new, unclassified handler fails here by name.
 */
class CombatInterruptionPolicyTests {
    private val gameMain = File("../src/main/kotlin/gg/rsmod/game")
    private val pluginsMain = File("src/main/kotlin/gg/rsmod/plugins")

    /** Walking, teleporting and choosing a new npc/player/object/ground item/item-on-item end combat. */
    private val hard =
        setOf("ClickMapHandler", "ClickMinimapHandler", "TeleportHandler", "OpNpcUHandler", "OpObjUHandler", "OpLocUHandler", "OpHeldUHandler") +
            (1..5).map { "OpNpc${it}Handler" } + (1..8).map { "OpPlayer${it}Handler" } +
            (1..3).map { "OpObj${it}Handler" } + (1..5).map { "OpLoc${it}Handler" }

    /**
     * Inventory: OSRS (owner decision 2026-09-12) - the "Eat"/"Drink" option keeps combat (OSRS Wiki
     * Food: food delays the next attack by 3 ticks; Void Eating.consume), every other inventory
     * option and drop end combat.
     */
    private val inventory = setOf("IfButton1Handler")

    private val softAlways = emptySet<String>()

    /** Interface targets (familiar command/special, Lunar) keep combat; item-use (parent 679) walks. */
    private val softExceptItemUse = setOf("OpNpcTHandler", "OpPlayerTHandler")

    private val call = Regex("""fullInterruption\(([^)]*)\)""")

    /** A `persistent = ...` argument passed to any queue call (not an unrelated variable). */
    private val queueArg = Regex("""[qQ]ueue\([^)]*\bpersistent\s*=""")

    @Test
    fun `every handler interruption is classified as a hard stop or a combat-preserving action`() {
        val handlers = File(gameMain, "message/handler").listFiles { f -> f.name.endsWith(".kt") }!!
        val seen = mutableSetOf<String>()
        for (file in handlers.sortedBy { it.name }) {
            val name = file.nameWithoutExtension
            val args = call.findAll(file.readText()).map { it.groupValues[1] }.toList()
            if (args.isEmpty()) continue
            seen += name
            args.forEach { a ->
                when (name) {
                    in hard -> {
                        assertTrue("preserveCombat" !in a, "$name must stay a hard stop: $a")
                        assertTrue("interactions = true" in a && "queue = true" in a, "$name must end the combat target and loop: $a")
                    }
                    in inventory ->
                        if ("preserveCombat" in a) {
                            assertTrue("preserveCombat = consumes" in a, "$name keeps combat only for Eat/Drink: $a")
                        } else {
                            assertTrue("interactions = true" in a && "queue = true" in a, "$name drop must end combat: $a")
                        }
                    in softAlways -> assertTrue("preserveCombat = true" in a, "$name must keep combat: $a")
                    in softExceptItemUse -> assertTrue("preserveCombat = parent != 679" in a, "$name must keep combat except for item use: $a")
                    else -> fail("Unclassified player interruption in $name: $a")
                }
            }
        }
        assertEquals(hard + inventory + softAlways + softExceptItemUse, seen, "classification table is stale")
        val ifButton = File(gameMain, "message/handler/IfButton1Handler.kt").readText()
        assertTrue("""menuOption == "eat" || menuOption == "drink"""" in ifButton, "Eat/Drink must be derived from the item's menu option")
    }

    @Test
    fun `equipping keeps the combat target`() {
        val text = File(gameMain, "action/EquipAction.kt").readText()
        assertTrue("resetInteractions(preserveCombat = true)" in text)
        assertTrue("resetInteractions()" !in text, "EquipAction must not clear the combat target")
    }

    @Test
    fun `only the player combat loop is persistent`() {
        val engine = setOf("QueueTask.kt", "QueueTaskSet.kt", "PawnQueueTaskSet.kt", "Pawn.kt")
        val hits =
            (gameMain.walk() + pluginsMain.walk())
                .filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) && it.name !in engine }
                .flatMap { f -> f.readLines().filter { queueArg.containsMatchIn(it) && !it.trim().startsWith("//") && !it.trim().startsWith("*") }.map { f.name to it.trim() } }
                .toList()
        assertEquals(listOf("combat.plugin.kts" to "pawn.queue(persistent = pawn is Player) {"), hits)
    }

    @Test
    fun `teleports still end combat through their own hard reset`() {
        val ext = File(pluginsMain, "content/magic/PawnExt.kt").readText()
        val prepare = ext.substringAfter("fun Pawn.prepareForTeleport()").substringBefore("}")
        assertTrue("resetInteractions()" in prepare, "teleport must clear the combat target")
        val combat = File(pluginsMain, "content/combat/combat.plugin.kts").readText()
        assertTrue("if (!pawn.lock.canAttack())" in combat, "a teleport lock must end the combat loop")
    }
}
