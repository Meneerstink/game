package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket
import gg.rsmod.plugins.content.quests.foundation.ShortQuest

/**
 * PROJECT48 F3: the register of the bracket mini-quests (Deadman: Armageddon "automatic quests", played as short quests).
 *
 * A new quest only touches its own bracket file (B0350Quests ...) and one line here. [ALL] joins FoundationQuests.SHORT (so the
 * talk/step/fight/Quest Helper code that loops over the short quests sees them); [NPCS] are the quest npcs the plugin spawns and
 * binds a Talk-to for. An npc that already has a Talk-to plugin does NOT go in [NPCS]: its handler calls FoundationQuests.talk first.
 */
object BracketQuests {
    /** The quests per combat bracket, in play order (the order of VOORTGANG.tsv). */
    val BY_BRACKET: Map<Bracket, List<ShortQuest>> by lazy {
        mapOf(
            Bracket.B3_50 to B0350Quests.ALL,
        )
    }

    val ALL: List<ShortQuest> by lazy { BY_BRACKET.values.flatten() }

    val NPCS: List<BracketNpc> by lazy { B0350Quests.NPCS }

    private val bySlotMap: Map<Int, ShortQuest> by lazy { ALL.associateBy { it.slot } }

    fun bySlot(slot: Int): ShortQuest? = bySlotMap[slot]
}

/**
 * A quest npc of a bracket quest. [tile] = where the plugin spawns it (null: the npc stands in the world already, only its Talk-to
 * is bound); [idle] is what it says when it has no quest business with the player.
 */
class BracketNpc(val npc: Int, val tile: Tile?, val facing: Direction, val idle: List<String>)
