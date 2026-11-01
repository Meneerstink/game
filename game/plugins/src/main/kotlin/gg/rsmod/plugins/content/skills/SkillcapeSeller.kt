package gg.rsmod.plugins.content.skills

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player

/**
 * K03 N410 part 1 (owner 2026-10-24: "hoe unlock je em"): the Cape of Accomplishment sellers whose own dialogue never sold the
 * cape. OSRS Wiki "Cape of Accomplishment": Armour salesman (Ranged), Cap'n Izzy No-Beard (Agility), Martin Thwait (Thieving),
 * Duradel/Kuradal (Slayer), Aubury (Runecraft), Hunting expert (Hunter); Pikkupstix sells the 667 Summoning cape and Larriar
 * keeps the 667 Runecrafting one. 99,000 coins, cape + hood, trimmed with two or more 99s - the same purchase as every other
 * master ([Skills.purchaseSkillcape]); only a real (unboosted) 99 counts.
 */
object SkillcapeSeller {
    const val PRICE = 99_000

    val SELLERS: Map<Int, Skillcapes> =
        mapOf(
            Npcs.ARMOUR_SALESMAN to Skillcapes.RANGED,
            Npcs.CAPN_IZZY_NOBEARD to Skillcapes.AGILITY,
            Npcs.MARTIN_THWAIT to Skillcapes.THIEVING,
            Npcs.DURADEL to Skillcapes.SLAYER,
            Npcs.DURADEL_8310 to Skillcapes.SLAYER,
            Npcs.DURADEL_8466 to Skillcapes.SLAYER,
            Npcs.KURADAL to Skillcapes.SLAYER,
            Npcs.AUBURY to Skillcapes.RUNECRAFTING,
            Npcs.LARRIAR to Skillcapes.RUNECRAFTING,
            Npcs.HUNTING_EXPERT to Skillcapes.HUNTER,
            Npcs.PIKKUPSTIX to Skillcapes.SUMMONING,
            // N410 (ChatGPT-B cape table 11-01: "Herblore/Farming/Construction missen verkooproute"): OSRS Wiki - the Estate
            // agents sell the Construction cape. Kaqemeex (Herblore) and Martin the Master Gardener (Farming) sell it through
            // their own dialogue ([sell]).
            Npcs.ESTATE_AGENT to Skillcapes.CONSTRUCTION,
            Npcs.ESTATE_AGENT_6715 to Skillcapes.CONSTRUCTION,
        )

    /** The skill behind a cape: the enum follows the 667 skill ids (Attack 0 ... Summoning 23). */
    fun skill(cape: Skillcapes): Int = cape.ordinal

    fun has99(
        player: Player,
        cape: Skillcapes,
    ): Boolean = player.skills.getMaxLevel(skill(cape)) >= 99

    /**
     * Offers the cape when the player has a real 99 in the seller's skill. Returns false when there is nothing to offer or the
     * player wants something else, so the npc's own conversation carries on.
     */
    suspend fun offer(
        task: QueueTask,
        npcId: Int,
    ): Boolean {
        val cape = SELLERS[npcId] ?: return false
        if (!has99(task.player, cape)) return false
        val name = Skills.getSkillName(task.player.world, skill(cape))
        task.chatNpc(
            "I can see you have mastered the art of $name.",
            "Perhaps you have come to buy a Cape of Accomplishment?",
        )
        if (task.options("May I buy a skillcape, please?", "Something else.") != 1) return false
        buy(task, cape, name)
        return true
    }

    /**
     * A master's own "sell me a skillcape" choice (Kaqemeex, Martin the Master Gardener): the same purchase for a real 99,
     * otherwise a short refusal - never a dead end.
     */
    suspend fun sell(
        task: QueueTask,
        cape: Skillcapes,
    ) {
        val name = Skills.getSkillName(task.player.world, skill(cape))
        if (!has99(task.player, cape)) {
            task.chatNpc("I can only sell a Skillcape of $name to someone", "who has truly mastered the skill.")
            return
        }
        buy(task, cape, name)
    }

    private suspend fun buy(
        task: QueueTask,
        cape: Skillcapes,
        name: String,
    ) {
        val player = task.player
        task.chatPlayer("May I buy a Skillcape of $name, please?")
        task.chatNpc(
            "Most certainly; being such a prestigious item, it is",
            "appropriately expensive. That will be 99,000 coins.",
        )
        if (task.options("99,000 coins! That's too much.", "I'll gladly pay for such a splendid cape.") != 2) {
            task.chatPlayer("99,000 coins! That's too much.")
            task.chatNpc("You can find me here if you change your mind.")
            return
        }
        task.chatPlayer("I'll gladly pay for such a splendid cape.")
        if (player.inventory.freeSlotCount < 2) {
            task.chatNpc("You don't have enough free space in your inventory", "for the cape and its hood.")
            return
        }
        if (player.inventory.getItemCount(Items.COINS_995) < PRICE || !Skills.purchaseSkillcape(player, cape)) {
            task.chatPlayer("But, unfortunately, I don't have enough coins.")
            task.chatNpc("Well, come back and see me when you do.")
            return
        }
        SkillcapeTrim.trimAll(player)
        task.chatNpc("Excellent! Wear that cape with pride my friend.")
    }
}
