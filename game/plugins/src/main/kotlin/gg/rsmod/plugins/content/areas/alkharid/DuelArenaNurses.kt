package gg.rsmod.plugins.content.areas.alkharid

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.playSound

/**
 * RCV-012 B12 (owner live: "Heal on the Duel Arena npcs does nothing"). The revision-667 cache gives A'abla (959),
 * Sabreen (960), Surgeon General Tafani (961) and Jaraah (962) a "Heal" option (NpcDefProbeTool `option heal`), but no
 * plugin bound it, so every click fell through to "Nothing interesting happens"; Tafani's own "Can you heal me?" dialogue
 * choice only echoed the line. One heal for every entry point, ported from Void `duel_arena/Nurses.kt` `heal`:
 * below max hitpoints the npc plays `pick_pocket` (881) with sound `heal` (166), hitpoints are restored to max and the
 * player is told "You feel a little better."; otherwise the npc says "You look healthy to me!".
 */
object DuelArenaNurses {
    /** A'abla has no generated Npcs constant; 959 is her cache id (NpcDefProbeTool: NPC_959 name=A'abla). */
    const val A_ABLA = 959

    val HEALERS = intArrayOf(A_ABLA, Npcs.SABREEN, Npcs.SURGEON_GENERAL_TAFANI, Npcs.JARAAH)

    const val HEAL_ANIM = 881
    const val HEAL_SOUND = 166

    suspend fun heal(
        task: QueueTask,
        npc: Npc,
    ) {
        val player = task.player
        npc.facePawn(player)
        val max = player.getMaximumLifepoints()
        if (player.getCurrentLifepoints() < max) {
            npc.animate(HEAL_ANIM)
            player.playSound(HEAL_SOUND)
            player.setCurrentLifepoints(max)
            player.message("You feel a little better.")
            return
        }
        task.chatNpc("You look healthy to me!", npc = npc.id)
    }
}
