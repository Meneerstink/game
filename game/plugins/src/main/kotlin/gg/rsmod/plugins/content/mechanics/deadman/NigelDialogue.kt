package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*

/** V9 selected OSRS transcript services. No OSRS account reset, bank raids or event teleport is advertised. */
object NigelDialogue {
    suspend fun talk(task: QueueTask, npc: Npc) = with(task) {
        if (!Dmm2Interactions.near(player, npc) || !Dmm2Interactions.isNigel(npc)) return@with
        chatNpc("I'm Nigel. It is my job to tell you about Deadman Mode.", npc = npc.id)
        when (options("I'd like to access the Reward Store.", "I'd like to adjust experience gains.", "Deadman armour.", "Cosmetic rewards.", "Goodbye.")) {
            1 -> Dmm2Interactions.openRewards(player, npc)
            2 -> NigelExperience.adjust(this, npc.id)
            3 -> {
                val index = options("Deadman's chest.", "Deadman's legs.", "Deadman's cape.", "Never mind.") - 1
                val armour = BloodMoney.Armour.values().getOrNull(index) ?: return@with
                if (!Dmm2Interactions.near(player, npc)) return@with
                if (player.attr[armour.unlocked] == 1) BloodMoney.reclaim(player, armour)
                else if (options("Yes.", "No.", title = "Unlock this armour for 1 blood money?") == 1 && Dmm2Interactions.near(player, npc)) BloodMoney.unlock(player, armour)
            }
            4 -> {
                val store = NigelRewardStore.active ?: return@with
                when (options("Annihilation exit portal blueprints.", "Home Teleport animation.", "Never mind.")) {
                    1 -> if (Dmm2Interactions.near(player, npc)) store.reclaimExitBlueprints(player)
                    2 -> {
                        val choice = options("Default.", "Armageddon.", "Annihilation.") - 1
                        val selected = NigelRewardStore.Teleport.values().getOrNull(choice) ?: return@with
                        if (Dmm2Interactions.near(player, npc)) store.selectTeleport(player, selected)
                    }
                }
            }
        }
    }
}
