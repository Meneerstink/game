package gg.rsmod.plugins.content.quests.foundation.bracket

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.QuestRequirements
import gg.rsmod.plugins.content.quests.foundation.QuestReward
import gg.rsmod.plugins.content.quests.foundation.QuestStep
import gg.rsmod.plugins.content.quests.foundation.Recommendation
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.red

// PROJECT48 Q25: Enakhra's Lament (bracket 71-90, Experienced). OSRS Wiki: 50 Crafting, 43 Prayer (neither boostable), 45 Firemaking,
// 39 Magic (boostable); 2 QP, 7,000 Crafting/Mining/Firemaking/Magic XP, the Camulet. Tiles: Void donor enakhras_temple spawns.
// The boneguard test is a real check (Protect from Melee on); the Boneguard has no combat definition here, so it only "tries to attack"
// in its dialogue. ONTGR: the Camulet (a Camulet teleport exists already: items\teleport_items.plugin.kts).

private val LAZIM_POST = Tile(3191, 2926, 0)
private val TEMPLE_LAZIM = Tile(3127, 9325, 0)
private val BONEGUARD_POST = Tile(3105, 9299, 1)
private val AKTHANAKOS_POST = Tile(3105, 9297, 1)
private val ENAKHRA_POST = Tile(3102, 9311, 1)

object EnakhrasLament : ShortQuest(
    name = "Enakhra's Lament",
    startPoint = "Speak to Lazim just north-east of the Desert Quarry.",
    rewards = "2 Quest Points, 7,000 Crafting, Mining, Firemaking and Magic XP each, the Camulet.",
    pointReward = 2,
    questId = Varbits.ENAKHRAS_LAMENT_PROGRESS,
    slot = 39,
    completedValue = 5, // internal: steps.size + 1 (ownSteps)
    usesVarbits = true,
    startNpc = Npcs.LAZIM,
    startNpcName = "Lazim",
    startPlace = "the Desert Quarry",
    startLocation = LAZIM_POST,
) {
    override val ownSteps: Boolean get() = true

    override val needs =
        QuestRequirements(
            skills = listOf(Skills.CRAFTING to 50, Skills.FIREMAKING to 45, Skills.PRAYER to 43, Skills.MAGIC to 39),
            boostable = setOf(Skills.FIREMAKING, Skills.MAGIC),
            extra = listOf("Must be on the standard spellbook."),
        )

    override val recommendation = Recommendation.combat(40)

    override val reward =
        QuestReward(
            icon = Items.CAMULET,
            xp = listOf(Skills.CRAFTING to 7_000.0, Skills.MINING to 7_000.0, Skills.FIREMAKING to 7_000.0, Skills.MAGIC to 7_000.0),
            items = listOf(Items.CAMULET to 1),
            lines = listOf("2 Quest Points", "7,000 Crafting XP", "7,000 Mining XP", "7,000 Firemaking XP", "7,000 Magic XP", "The Camulet"),
        )

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc(
            "I wish to build a statue to honour the dead of this",
            "temple... but it needs a stone sculptor with great skill.",
            "Will you build it for me?",
            facialExpression = FacialExpression.CALM_TALK,
        )
        if (options("I'll build the statue.", "Not right now.", title = "Start Enakhra's Lament?") != 1) return false
        chatPlayer("I'll build the statue.", facialExpression = FacialExpression.CALM_TALK)
        chatNpc("Excellent! Come and see me in the temple below when it is done.", facialExpression = FacialExpression.HAPPY)
        return true
    }

    override val steps =
        listOf(
            QuestStep(
                npc = Npcs.LAZIM,
                journal = listOf("I should speak to ${red("Lazim")} in the temple below the desert."),
                done = listOf("Lazim gave me four sigils for the temple's guardians."),
                place = "Enakhra's Temple",
                location = TEMPLE_LAZIM,
                gives = listOf(Items.Z_SIGIL to 1, Items.M_SIGIL to 1, Items.R_SIGIL to 1, Items.K_SIGIL to 1),
                talk = {
                    chatNpc("The statue is finished - and it has opened a way down.", "Take these four sigils; the temple's guardian answers", "to them.", facialExpression = FacialExpression.HAPPY)
                    FoundationRewards.grant(player, Items.Z_SIGIL, 1)
                    FoundationRewards.grant(player, Items.M_SIGIL, 1)
                    FoundationRewards.grant(player, Items.R_SIGIL, 1)
                    FoundationRewards.grant(player, Items.K_SIGIL, 1)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.BONEGUARD_3140,
                journal = listOf("I should speak to the ${red("Boneguard")} in the temple while using", "Protect from Melee."),
                done = listOf("The Boneguard was impressed and asked for my help."),
                place = "Enakhra's Temple",
                location = BONEGUARD_POST,
                tip = "Turn on Protect from Melee before you speak to the Boneguard.",
                talk = {
                    if (!Prayers.isActive(player, Prayer.PROTECT_FROM_MELEE)) {
                        chatNpc("The Boneguard lashes out at you! You should have been protected from melee.", facialExpression = FacialExpression.ANGRY)
                        false
                    } else {
                        chatNpc("It tries to attack you - twice - and its blows glance off", "your prayer. It stops, impressed.", facialExpression = FacialExpression.CALM_TALK)
                        chatNpc("You... are strong. Enakhra's curse binds my brother", "and me. Free Akthanakos, I beg you.", facialExpression = FacialExpression.SAD)
                        true
                    }
                },
            ),
            QuestStep(
                npc = Npcs.AKTHANAKOS,
                journal = listOf("I should speak to ${red("Akthanakos")}, the freed boneguard."),
                done = listOf("Akthanakos told me how to defy Enakhra."),
                place = "Enakhra's Temple",
                location = AKTHANAKOS_POST,
                talk = {
                    chatNpc("Enakhra bound us with her magic. Speak to her - it", "is time she answered for what she did.", facialExpression = FacialExpression.ANGRY)
                    true
                },
            ),
            QuestStep(
                npc = Npcs.ENAKHRA,
                journal = listOf("I should confront ${red("Enakhra")} in the temple."),
                done = listOf("Enakhra and Akthanakos left for the north; the temple is at peace."),
                place = "Enakhra's Temple",
                location = ENAKHRA_POST,
                talk = {
                    chatNpc("Enough! My brother and I will leave this place. The", "temple is yours. Take this Camulet as a token.", facialExpression = FacialExpression.SAD)
                    true
                },
            ),
        )
}
