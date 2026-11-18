package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.SkillRequirement
import gg.rsmod.plugins.content.quests.Quest
import gg.rsmod.plugins.content.quests.QuestStage
import gg.rsmod.plugins.content.quests.QuestStatus

/** The full miniquest: no short-quest travel, Quest Tutor choice or completion shortcut. */
object MageArenaIIQuest : Quest(
    name = "Mage Arena II",
    startPoint = "Speak to Kolodion in the Mage Arena bank. Complete Mage Arena I and train all three god spells first.",
    requirements = listOf(SkillRequirement(Skills.MAGIC, 75)),
    requiredItems = "God staves, god spell runes and an item to cut webs.",
    combat = "Porazdir (235), Justiciar Zachariah (348), Derwen (235).",
    rewards = "One imbued god cape of your choice. Further capes require the respective creature's remains.",
    pointReward = 0, questId = MageArenaII.VARP, spriteId = 0, slot = MageArenaII.SLOT, stages = 2,
) {
    override fun getObjective(player: Player, stage: Int): QuestStage = QuestStage(MageArenaII.journal(player))
    override fun finishQuest(player: Player) {
        // This is display synchronization, never an alternate earned-state setter.
        if (MageArenaII.completed(player)) QuestStatus.sync(player)
    }
}
