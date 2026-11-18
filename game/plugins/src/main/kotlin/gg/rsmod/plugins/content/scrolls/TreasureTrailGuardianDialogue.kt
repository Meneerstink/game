package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.getCombatTarget

/** Pinned Wiki Transcript pages/revisions: docs/CLUE/GUARDIAN-DIALOGUE-SOURCES.json.
 * Random transcript alternatives use the ordinary uniform text picker; no combat odds change.
 */
object TreasureTrailGuardianDialogue {
    private val ancient = listOf("Die!", "For Zaros!", "Perish in agony!")
    private val cabbage = listOf(
        "Healing through deliciousness, must be a re-leaf [player name].",
        "Ah, you romaine calm enough for a nutritious meal [player name]. Wise.",
        "Leaf now and I'll spare you [player name]!",
        "Yes! Pray that nutrition can save you [player name]!",
    )
    private val spawn = mapOf(
        6587 to listOf("No warning! Be gone!"),
        6588 to listOf("Gggggrrrrrrrrr!!!"),
        7307 to ancient, 7308 to ancient, 7309 to ancient,
        7310 to listOf("Vegetate!", "Die deliciously!", "Rest in cabbage!", "Grow up or leave."),
    )

    /** Called only after the whole real spawn transaction has published successfully. */
    fun spawned(player: Player, sources: List<Int>, actors: List<Npc>,
                choose: (Int) -> Int = { player.world.random(it - 1) }) {
        if (sources.size != actors.size) return
        sources.zip(actors).forEach { (source, actor) ->
            val lines = spawn[source] ?: return@forEach
            actor.forceChat(lines[if (lines.size == 1) 0 else choose(lines.size)])
        }
    }

    /** Called after a real cabbage was consumed, never from a click or failed inventory removal. */
    fun ateCabbage(player: Player, choose: (Int) -> Int = { player.world.random(it - 1) }): Boolean {
        if (!player.isOnline || player.isDead()) return false
        val session = TreasureTrailGuardians.session(player) ?: return false
        if (!session.contract.valid(player)) return false
        val local = ClueNpcs.local(7310) ?: return false
        val mage = session.actors.singleOrNull {
            it.id == local && it.world === player.world && it.isSpawned() && !it.isDead() && it.getCombatTarget() === player
        } ?: return false
        mage.forceChat(cabbage[choose(cabbage.size)].replace("[player name]", player.username))
        return true
    }
}
