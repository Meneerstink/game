package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestFight
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q16: Roving Elves (bracket 51-70). OSRS Wiki: Regicide, Waterfall Quest, Agility 56 (boostable), "defeat a level 84
// enemy"; 1 QP, 10,000 Strength XP, Crystal bow or Crystal shield with 500 charges.
// ONTGR: the Crystal bow and shield are wielded after the quest (OsrsQuestRequirements.WEAR).

private val ISLWYN_POST = Tile(2290, 3143, 0)
private val ELUNED_POST = Tile(2292, 3141, 0)
private val GLARIAL_TOMB = Tile(2555, 9843, 0)

object RovingElves : ShortQuest(
    name = "Roving Elves",
    startPoint = "Speak to Islwyn in Isafdar.",
    rewards = "1 Quest Point, 10,000 Strength XP, a Crystal bow or Crystal shield, the right to wield them.",
    pointReward = 1,
    questId = Varps.ROVING_ELVES_PROGRESS,
    slot = 90,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.ISLWYN,
    startNpcName = "Islwyn",
    startPlace = "Isafdar",
    startLocation = ISLWYN_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.AGILITY to 56),
            quests = listOf("Regicide", "Waterfall Quest"),
            boostable = setOf(Skills.AGILITY),
            extra = listOf("Must be able to defeat a level 84 enemy."),
        )

    override val recommendation = Recommendation.combat(60)

    override val reward =
        QuestReward(
            icon = Items.CRYSTAL_BOW_FULL,
            xp = listOf(Skills.STRENGTH to 10_000.0),
            lines = listOf("10,000 Strength XP", "A Crystal bow or a Crystal shield (your choice)", "The right to wield them"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "Glarial's tomb is no longer consecrated: the elven",
            "grave is desecrated. We need a human to renew the",
            "ritual - Eluned will explain.",
            facialExpression = FacialExpression.WORRIED,
        )
        if (options("I'll help.", "Not right now.", title = "Start Roving Elves?") != 1) return false
        chatPlayer("I'll help renew the tomb.", facialExpression = FacialExpression.CALM_TALK)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.ELUNED_2376,
                journal = listOf("I should speak to ${red("Eluned")} next to Islwyn."),
                done = listOf("Eluned told me to take a consecration seed from Glarial's", "tomb."),
                place = "Isafdar",
                location = ELUNED_POST,
                talk = {
                    chatNpc("Glarial's Tomb lies east of the Waterfall. A moss", "guardian haunts it - defeat him and take the seed.", facialExpression = FacialExpression.CALM_TALK)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ELUNED_2376,
                journal = listOf("I must defeat the ${red("moss guardian")} (level 84) in Glarial's tomb", "and take the consecration seed."),
                done = listOf("I defeated the moss guardian and took the consecration", "seed."),
                place = "Glarial's tomb",
                location = GLARIAL_TOMB,
                gives = listOf(Items.CONSECRATION_SEED to 1),
                tip = "In OSRS you take no weapons or armour into the tomb. Bring food.",
                fight = QuestFight(Npcs.MOSS_GIANT_1681, "moss guardian", 84, GLARIAL_TOMB, "The tomb is mine!"),
                talk = {
                    chatNpc("The tomb is guarded by a moss guardian.", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ELUNED_2376,
                journal = listOf("I should bring the ${red("consecration seed")} to ${red("Eluned")}."),
                done = listOf("Eluned enchanted the seed; I planted it by the tomb."),
                place = "Isafdar",
                location = ELUNED_POST,
                talk = {
                    if (!player.inventory.contains(Items.CONSECRATION_SEED)) {
                        chatNpc("Bring me the consecration seed from Glarial's tomb.", facialExpression = FacialExpression.CALM_TALK)
                        if (FoundationRewards.waitingInBank(player, listOf(Items.CONSECRATION_SEED))) chatNpc(FoundationRewards.IN_BANK_LINE, facialExpression = FacialExpression.CALM_TALK)
                        false
                    } else {
                        player.inventory.remove(Items.CONSECRATION_SEED)
                        chatNpc("The seed is ready. Plant it, and the tomb is", "consecrated again.", facialExpression = FacialExpression.HAPPY)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.ISLWYN,
                journal = listOf("I should tell ${red("Islwyn")} the tomb is consecrated again."),
                done = listOf("Islwyn thanked me with a crystal weapon."),
                place = "Isafdar",
                location = ISLWYN_POST,
                talk = {
                    chatNpc("Glarial can rest. Choose a crystal weapon as our", "thanks - the bow or the shield.", facialExpression = FacialExpression.HAPPY)
                    if (options("Crystal bow", "Crystal shield", title = "Choose your reward") == 1) {
                        FoundationRewards.grant(player, Items.CRYSTAL_BOW_FULL, 1)
                    } else {
                        FoundationRewards.grant(player, Items.CRYSTAL_SHIELD_FULL, 1)
                    }
                    true
                },
            ),
        )
}
