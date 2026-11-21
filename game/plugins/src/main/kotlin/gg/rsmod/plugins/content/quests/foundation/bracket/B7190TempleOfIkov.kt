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

// PROJECT48 Q26: Temple of Ikov (bracket 71-90; the Guardians of Armadyl's side). OSRS Wiki: 42 Thieving (boostable), 40 Ranged, a
// level 84 Fire Warrior of Lesarkus; 1 QP, 10,500 Ranged XP, 8,000 Fletching XP, the Armadyl pendant. ONTGR: none (the Boots of
// lightness and the Pendant of Lucien's door are not part of the mini-quest). Estimated tiles: the fight spots - see STAND.txt.

private val LUCIEN_POST = Tile(2573, 3321, 0)
private val WINELDA_POST = Tile(2656, 9875, 0)
private val FIRE_WARRIOR_FIGHT = Tile(2650, 9879, 0)
private val GUARDIAN_POST = Tile(2648, 9904, 0)
private val LUCIEN_FIGHT = Tile(2575, 3323, 0)

object TempleOfIkov : ShortQuest(
    name = "Temple of Ikov",
    startPoint = "Speak to Lucien at the Flying Horse Inn in the north-west of East Ardougne.",
    rewards = "1 Quest Point, 10,500 Ranged XP, 8,000 Fletching XP, the Armadyl pendant.",
    pointReward = 1,
    questId = Varps.TEMPLE_OF_IKOV_PROGRESS,
    slot = 106,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = false,
    startNpc = Npcs.LUCIEN,
    startNpcName = "Lucien",
    startPlace = "East Ardougne",
    startLocation = LUCIEN_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.THIEVING to 42, Skills.RANGED to 40),
            boostable = setOf(Skills.THIEVING),
            extra = listOf("Must be able to defeat a level 84 Fire Warrior of Lesarkus with Ranged."),
        )

    override val recommendation = Recommendation.combat(55)

    override val reward =
        QuestReward(
            icon = Items.ARMADYL_PENDANT,
            xp = listOf(Skills.RANGED to 10_500.0, Skills.FLETCHING to 8_000.0),
            lines = listOf("1 Quest Point", "10,500 Ranged XP", "8,000 Fletching XP", "The Armadyl pendant"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I need the Staff of Armadyl from the Temple of Ikov.",
            "There are traps and guardians - and a fire warrior.",
            "Will you fetch it for me?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll go.", "Not right now.", title = "Start Temple of Ikov?") != 1) return false
        chatPlayer("I'll find the staff.", facialExpression = FacialExpression.CALM_TALK)
        FoundationRewards.grant(player, Items.PENDANT_OF_LUCIEN, 1)
        chatNpc("Take this pendant. Winelda the witch will let you through.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.WINELDA,
                journal = listOf("I should speak to ${red("Winelda")}, the witch in the Temple of Ikov."),
                done = listOf("Winelda let me through and gave me ice arrows."),
                place = "the Temple of Ikov",
                location = WINELDA_POST,
                gives = listOf(Items.ICE_ARROWS to 25),
                talk = {
                    chatNpc("A pendant from Lucien? Pass, then - and take these ice", "arrows: only they hurt the fire warrior.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.ICE_ARROWS, 25)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.WINELDA,
                journal = listOf("I must defeat the ${red("Fire Warrior of Lesarkus")} (level 84) with ranged", "attacks."),
                done = listOf("I defeated the Fire Warrior of Lesarkus."),
                place = "the Temple of Ikov",
                location = FIRE_WARRIOR_FIGHT,
                tip = "Only ice arrows hurt the Fire Warrior in OSRS: fire them with a bow.",
                fight = QuestFight(Npcs.FIRE_WARRIOR_OF_LESARKUS, "Fire Warrior of Lesarkus", 84, FIRE_WARRIOR_FIGHT, "Fool! You cannot pass!"),
                talk = {
                    chatNpc("The Fire Warrior of Lesarkus guards the stair. Use the", "ice arrows!", facialExpression = FacialExpression.WORRIED)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GUARDIAN_OF_ARMADYL,
                journal = listOf("I should speak to a ${red("Guardian of Armadyl")} in the sanctuary."),
                done = listOf("The Guardian of Armadyl gave me the Armadyl pendant."),
                place = "the Temple of Ikov sanctuary",
                location = GUARDIAN_POST,
                gives = listOf(Items.ARMADYL_PENDANT to 1),
                talk = {
                    chatNpc("You have beaten the warrior and bear the pendant of", "Lucien... Take this Armadyl pendant: we will deal with", "Lucien ourselves.", facialExpression = FacialExpression.CALM_TALK)
                    FoundationRewards.grant(player, Items.ARMADYL_PENDANT, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.GUARDIAN_OF_ARMADYL,
                journal = listOf("I must defeat ${red("Lucien")} (level 14) for betraying the temple."),
                done = listOf("I defeated Lucien."),
                place = "East Ardougne",
                location = LUCIEN_FIGHT,
                tip = "Lucien is only level 14.",
                fight = QuestFight(Npcs.LUCIEN, "Lucien", 14, LUCIEN_FIGHT, "You will not stop me!"),
                talk = {
                    chatNpc("Lucien lied to you. Face him at the Flying Horse Inn!", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
        )
}
