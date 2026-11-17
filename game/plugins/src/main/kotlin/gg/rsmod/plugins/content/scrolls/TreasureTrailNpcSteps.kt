package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/** Exact Easy branches from NPC-COMPLETION-SOURCES.json; actors use existing server spawns. */
object TreasureTrailNpcSteps {
    data class Receipt(val item: Int, val text: String)
    data class Lines(val spoken: List<String>, val next: String = "You've been given another clue!",
                     val casket: String = "You've obtained a casket!")
    val lines = mapOf(
        "Hans" to Lines(emptyList()),
        "Doric" to Lines(listOf("So, you've come to the right place?")),
        "Arhein" to Lines(listOf("Well done, matey!"), next = "You've found another clue!"),
        "Ellis" to Lines(emptyList()),
        "Ned" to Lines(listOf("Well done!")),
        "Gaius" to Lines(listOf("Here you go.")),
        "Cassie" to Lines(emptyList()),
    )
    private data class Actor(val id: Int, val spawn: Tile)
    // Exact own-server spawn identity, not a guessed interaction radius or all same-name variants.
    private val actors = mapOf(
        "Hans" to Actor(0, Tile(3219, 3227, 0)),
        "Wizard Traiborn" to Actor(881, Tile(3112, 3162, 1)),
        "Doric" to Actor(284, Tile(2953, 3450, 0)),
        "Arhein" to Actor(563, Tile(2803, 3430, 0)),
        "Ellis" to Actor(2824, Tile(3275, 3193, 0)),
        "Ned" to Actor(918, Tile(3098, 3257, 0)),
        "Gaius" to Actor(586, Tile(2884, 3450, 0)),
        "Cassie" to Actor(577, Tile(2975, 3384, 0)),
        "Bolkoy" to Actor(471, Tile(2527, 3161, 1)),
        "Gnome Coach" to Actor(2802, Tile(2406, 3498, 0)),
        "Brother Omad" to Actor(279, Tile(2604, 3209, 0)),
        "Wise Old Man" to Actor(2253, Tile(3088, 3255, 0)),
        "Otto Godblessed" to Actor(2725, Tile(2502, 3487, 0)),
    )
    // The source clue changelog dates the Wizard prefix to6July2022. Native881
    // is still Traiborn at the exact same tower spawn; no duplicate NPC import.
    private const val TRAIBORN_CIPHER = "Clue scroll (medium) - XJABSE USBJCPSO"
    fun npcs(row: TreasureTrailStepCatalogue.Step): Set<Int> = row.existingNpcs +
        if (row.key == TRAIBORN_CIPHER && row.npcName == "Wizard Traiborn") setOf(881) else emptySet()
    fun matchesName(id: Int, name: String, row: TreasureTrailStepCatalogue.Step): Boolean =
        name.equals(row.npcName, true) || row.key == TRAIBORN_CIPHER &&
            row.npcName == "Wizard Traiborn" && id == 881 && name == "Traiborn"

    fun matchesActor(npc: Npc, row: TreasureTrailStepCatalogue.Step): Boolean {
        val actor = actors[row.npcName] ?: return true
        return npc.id == actor.id && npc.spawnTile == actor.spawn
    }
    fun supports(row: TreasureTrailStepCatalogue.Step): Boolean = row.tier == ClueScrollTier.EASY &&
        row.challenge == TreasureTrailStepCatalogue.Challenge.NONE && row.npcName in lines

    /** The inventory transaction is owned by the same held-clue contract as every other subtype. */
    fun finish(player: Player, before: ClueStepContract): Receipt? {
        val row = (before.step as? TreasureTrailSourceStep)?.source ?: return null
        if (!supports(row)) return null
        val text = lines.getValue(row.npcName!!)
        if (!before.advance(player)) return null
        return if (ClueScrollManager.progress(player, row.tier) == 0) Receipt(row.tier.casketId, text.casket)
               else Receipt(row.tier.scrollId, text.next)
    }
    fun talk(player: Player, npc: Npc, step: TreasureTrailSourceStep): Boolean {
        if (!supports(step.source) || !matchesActor(npc, step.source)) return false
        val before = ClueStepContract.capture(player, step) ?: return false
        player.queue {
            for (line in lines.getValue(step.source.npcName!!).spoken) {
                chatNpc(line, npc = npc.id, wrap = true)
                if (terminated || !before.valid(player)) return@queue
            }
            if (!terminated) finish(player, before)?.let { itemMessageBox(it.text, it.item) }
        }
        return true
    }
}
