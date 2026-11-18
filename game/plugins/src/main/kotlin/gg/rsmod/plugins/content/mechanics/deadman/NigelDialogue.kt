package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.ext.*

/**
 * Nigel's Talk-to = Transcript:Nigel 'Standard dialogue' (Permanent Deadman), DMM2 punt 3 (Claude 18-11; owner 18-11 16:xx chose
 * 'OSRS-tekst, aangepast': the question branch keeps the OSRS lines and only changes what works differently on this server).
 * OSRS order: question | XP | rewards | (Wilderness theme only) blueprints | Goodbye. Ours adds 'Deadman armour.' (free reclaim of
 * the blood money armour - wiki Deadman's chest says it 'can be reclaimed for free' from Nigel, the route is not described).
 * The 667 chatbox holds 5 options, so a longer menu pages with OSRS 'More options...' / 'Previous options...'.
 * Changed against OSRS (no bank loss, no experience loss, no key/chest after a kill on this server; every world is Deadman):
 * 'How do I play Deadman mode?' is left out (it is about choosing a Deadman world), the death/kill/protect answers drop the
 * bank/experience/key parts, 'Lucrative' drops the bank and experience loss. Historical 'protect my stats' is not offered (OSRS
 * marks it historical). The Annihilation access/waiver branch is temporary-event only (OSRS 'Prior to the release').
 */
object NigelDialogue {
    private const val QUESTION = "I have a question about Deadman mode."
    private const val XP = "Could you stop me from gaining XP?"
    private const val REWARDS = "Can I purchase some Deadman rewards?"
    private const val BLUEPRINTS = "Do you have any spare exit portal blueprints?"
    private const val ARMOUR = "Deadman armour."
    private const val GOODBYE = "Goodbye."
    private const val MORE = "More options..."
    private const val PREVIOUS = "Previous options..."

    suspend fun talk(task: QueueTask, npc: Npc) = with(task) {
        if (!Dmm2Interactions.near(player, npc) || !Dmm2Interactions.isNigel(npc)) return@with
        chatPlayer("Hey, who are you?")
        chatNpc("I'm Nigel. It is my job to tell you about Deadman Mode.", npc = npc.id)
        repeat(20) {
            val blueprints = NigelRewardStore.unlocked(player, NigelRewardStore.Reward.BLUEPRINTS)
            val menu = listOfNotNull(QUESTION, XP, REWARDS, if (blueprints) BLUEPRINTS else null, ARMOUR, GOODBYE)
            when (paged(menu)) {
                QUESTION -> if (!question(npc)) return@with
                XP -> { NigelExperience.adjust(this, npc.id); return@with }
                REWARDS -> { rewards(npc); return@with }
                BLUEPRINTS -> { blueprints(npc); return@with }
                ARMOUR -> { armour(npc); return@with }
                GOODBYE -> { chatPlayer("Goodbye."); return@with }
                else -> return@with
            }
            if (!Dmm2Interactions.near(player, npc)) return@with
        }
    }

    /** One 'Select an option' menu of at most 5 rows; longer lists show 4 rows + 'More options...' and back. */
    private suspend fun QueueTask.paged(all: List<String>): String? {
        if (all.size <= 5) return all.getOrNull(options(*all.toTypedArray()) - 1)
        var page = 0
        repeat(10) {
            val rows = if (page == 0) all.take(4) + MORE else all.drop(4) + PREVIOUS
            when (val picked = rows.getOrNull(options(*rows.toTypedArray()) - 1)) {
                MORE -> page = 1
                PREVIOUS -> page = 0
                else -> return picked
            }
        }
        return null
    }

    /** Transcript 'I have a question about Deadman mode.'; true = back to the first menu (OSRS 'initial'). */
    private suspend fun QueueTask.question(npc: Npc): Boolean {
        chatPlayer(QUESTION)
        chatNpc("What would you like to know?", npc = npc.id)
        val asked = listOf(
            "What is Deadman mode?",
            "What happens when I die in Deadman mode?",
            "What happens when I kill a player in Deadman mode?",
            "How can I protect my items in Deadman mode?",
            "What are safe zones in Deadman mode?",
        )
        val picked = asked.getOrNull(options(*asked.toTypedArray()) - 1) ?: return false
        if (!Dmm2Interactions.near(player, npc)) return false
        chatPlayer(picked)
        when (picked) {
            asked[0] -> {
                chatNpc("Deadman mode is an incredibly dangerous survival game mode. Anyone can attack anyone, anywhere. Death comes at a huge cost and killing other players can be extremely lucrative.", npc = npc.id)
                chatPlayer("Lucrative, you say?")
                chatNpc("Killing a player will earn you all of the items they had on them. For the unfortunate soul, it's quite the set-back.", npc = npc.id)
                chatPlayer("Wow, that is brutal!")
                chatNpc("It is, but it is no simple task to kill someone in Deadman mode.", npc = npc.id)
                chatNpc("Very high level guards patrol every major city and bank. They will kill anyone who has a skull. Getting yourself a kill is only half the battle.", npc = npc.id)
            }
            asked[1] -> {
                chatNpc("If you die to a player you will lose all items that you have on your person.", npc = npc.id)
                chatNpc("If you die to a monster when you have a PK skull you will lose all items that you have on your person.", npc = npc.id)
                chatNpc("If you die to a monster when you do not have a PK skull you will lose all of the items on your person except for the 3 most valuable. You will not lose experience or any items from your bank.", npc = npc.id)
            }
            asked[2] -> chatNpc("When you kill a player you will receive any items they have on their person.", npc = npc.id)
            asked[3] -> chatNpc("You can protect up to 10 items by placing them in your safe deposit box. Items in your safe deposit box will never be lost. Remember that you can only place 10 single items into your safe deposit box, no stacks.", npc = npc.id)
            asked[4] -> chatNpc("Safe zones are areas patrolled by very high level guards which can be found in various places across the map. The guards watch out for any player with a PK skull and will attack them if they're spotted. It is still possible to be attacked inside safe zones but the person doing the attacking will not stand a chance of making it out alive.", npc = npc.id)
        }
        return true
    }

    /** Transcript: the player asks, Nigel 'Certainly! ...' and the store opens; a closed store = 'Unfortunately ...'. */
    private suspend fun QueueTask.rewards(npc: Npc) {
        if (NigelRewardStore.active == null) {
            chatNpc("Unfortunately the store is not currently available.", npc = npc.id)
            return
        }
        chatPlayer(REWARDS)
        chatNpc("Certainly! I have a number of unique rewards which you might be interested in.", npc = npc.id)
        Dmm2Interactions.openRewards(player, npc)
    }

    /** Transcript: only once the Wilderness house theme is unlocked; 'Of course, here you go.' / 'Thank you, Nigel.'. */
    private suspend fun QueueTask.blueprints(npc: Npc) {
        val store = NigelRewardStore.active ?: return
        chatPlayer("Do you have any spare blueprints to recolour my exit portal in my player-owned house?")
        if (!Dmm2Interactions.near(player, npc)) return
        when (store.reclaimExitBlueprints(player)) {
            NigelRewardStore.Result.OK -> {
                chatNpc("Of course, here you go.", npc = npc.id)
                chatPlayer("Thank you, Nigel.")
            }
            NigelRewardStore.Result.NO_SPACE -> player.message("You don't have enough inventory space.")
            else -> Unit
        }
    }

    /** Ours (no OSRS route): reclaim an unlocked blood money armour piece for free, or unlock one for 1 blood money. */
    private suspend fun QueueTask.armour(npc: Npc) {
        val index = options("Deadman's chest.", "Deadman's legs.", "Deadman's cape.", "Never mind.") - 1
        val armour = BloodMoney.Armour.values().getOrNull(index) ?: return
        if (!Dmm2Interactions.near(player, npc)) return
        if (player.attr[armour.unlocked] == 1) BloodMoney.reclaim(player, armour)
        else if (options("Yes.", "No.", title = "Unlock this armour for 1 blood money?") == 1 && Dmm2Interactions.near(player, npc)) BloodMoney.unlock(player, armour)
    }
}
