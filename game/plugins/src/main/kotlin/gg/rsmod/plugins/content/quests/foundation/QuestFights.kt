package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import java.lang.ref.WeakReference

/**
 * The fight steps of the short quests (owner 2026-09-26: "add fights to the short quest"). The step's npc sends the
 * player in; the foe is a real cache npc with its normal combat definition and drops, spawned for this player alone
 * ([Npc.owner]: only the owner sees and fights it, like the Restless Ghost's skeleton warlock) on the step's fight tile.
 * Killing it completes the step. It disappears when the player leaves (20 tiles), logs out, dies or the stage moves on;
 * speaking to the step's npc again brings it back. Deaths follow the normal death rules of the place.
 */
object QuestFights {
    private val FOE_QUEST = AttributeKey<Int>()
    private val FOE_STAGE = AttributeKey<Int>()
    private val ACTIVE_FOE = AttributeKey<WeakReference<Npc>>()

    /** How close the player has to be to the fight tile for the foe to appear. */
    const val REACH = 15

    /** Beyond this distance from the foe the fight is abandoned. */
    const val LEASH = 20

    fun active(player: Player): Npc? = player.attr[ACTIVE_FOE]?.get()?.takeIf { it.isSpawned() && it.isAlive() }

    /** Spawns the foe of [quest]'s fight step [stage] for [player]; false when it cannot (wrong place or no fight). */
    fun start(player: Player, quest: ShortQuest, stage: Int): Boolean {
        val fight = quest.steps.getOrNull(stage - 1)?.fight ?: return false
        if (active(player) != null) return true
        if (!player.tile.isWithinRadius(fight.tile, REACH)) {
            player.message("The ${fight.name} waits for you at ${quest.steps[stage - 1].place ?: "the quest location"}.")
            return false
        }
        val world = player.world
        val foe = Npc(player, fight.npc, fight.tile, world)
        foe.respawnOverride = false
        foe.attr[FOE_QUEST] = quest.slot
        foe.attr[FOE_STAGE] = stage
        if (!world.spawn(foe)) return false
        player.attr[ACTIVE_FOE] = WeakReference(foe)
        foe.forceChat(fight.shout)
        player.message("<col=ef1020>The ${fight.name} (level ${fight.level}) attacks! Dying here follows the normal death rules of this area.")
        foe.attack(player)
        foe.queue {
            while (foe.isSpawned() && foe.isAlive()) {
                wait(5)
                val gone = !player.isOnline || player.isDead() || !player.tile.isWithinRadius(foe.tile, LEASH) || quest.stage(player) != stage
                if (gone && foe.isSpawned() && foe.isAlive()) {
                    world.remove(foe)
                    player.attr.remove(ACTIVE_FOE)
                    if (player.isOnline && !player.isDead()) player.message("The ${fight.name} has fled. Speak to ${questNpcName(quest, stage)} to face it again.")
                }
            }
        }
        return true
    }

    /** A kill of a quest foe by its owner completes the step: its items, then the next stage (or the quest). */
    fun onKilled(killer: Player, npc: Npc) {
        val slot = npc.attr[FOE_QUEST] ?: return
        val stage = npc.attr[FOE_STAGE] ?: return
        if (npc.owner != killer) return
        val quest = FoundationQuests.SHORT.firstOrNull { it.slot == slot } ?: return
        if (quest.stage(killer) != stage) return
        killer.attr.remove(ACTIVE_FOE)
        FoundationQuests.completeStep(killer, quest, stage)
    }

    private fun questNpcName(quest: ShortQuest, stage: Int): String =
        FoundationQuests.NPC_NAMES[quest.steps[stage - 1].npc] ?: "the quest's contact"
}
