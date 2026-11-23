package gg.rsmod.plugins.content.quests.foundation.extra

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.quests.foundation.*

/** OSRS Wiki The Lost Tribe. Four main steps; the silverware investigation uses the real key/chest/crate. */
object TheLostTribe : ShortQuest(
    name = "The Lost Tribe", startPoint = "Speak to Sigmund on the first floor of Lumbridge Castle.",
    rewards = "1 Quest Point, 3,000 Mining XP, ring of life and access to the Dorgeshuun mines.", pointReward = 1,
    questId = Varbits.THE_LOST_TRIBE_PROGRESS, slot = 69, completedValue = 5, usesVarbits = true,
    startNpc = Npcs.SIGMUND_2082, startNpcName = "Sigmund", startPlace = "Lumbridge Castle's first floor",
    startLocation = Tile(3210, 3222, 1),
) {
    override val ownSteps = true
    override val showStartedInList = true
    override val needs = QuestRequirements(skills = listOf(Skills.AGILITY to 13, Skills.THIEVING to 13, Skills.MINING to 17),
        quests = listOf("Goblin Diplomacy", "Rune Mysteries"), boostable = setOf(Skills.AGILITY, Skills.THIEVING, Skills.MINING))
    override val reward = QuestReward(Items.RING_OF_LIFE, xp = listOf(Skills.MINING to 3_000.0), items = listOf(Items.RING_OF_LIFE to 1),
        lines = listOf("3,000 Mining XP", "A ring of life", "Access to the Dorgeshuun mines and Nardok's store", "Goblin Bow and Goblin Salute emotes"))
    val BROOCH_RETURNED = AttributeKey<Boolean>(persistenceKey = "extra_lost_tribe_brooch_returned")
    val THEFT_KNOWN = AttributeKey<Boolean>(persistenceKey = "extra_lost_tribe_theft_known")
    val HAM_FOUND = AttributeKey<Boolean>(persistenceKey = "extra_lost_tribe_ham_found")
    val BROOCH_POST = Tile(3230, 9610, 0)
    val MISTAG_POST = Tile(3320, 9615, 0)
    val CHEST_POST = Tile(3209, 3216, 1) // south of the chest: from the north side (3209,3218 is a wall) it cannot be reached (bot X5TRIBE 23-11)
    val CRATE_POST = Tile(3153, 9645, 0) // 3152,9644 is not standable (bot X5TRIBE 23-11)

    override fun onReset(player: Player) {
        player.attr.remove(BROOCH_RETURNED)
        player.attr.remove(THEFT_KNOWN)
        player.attr.remove(HAM_FOUND)
    }

    override suspend fun QueueTask.intro(): Boolean {
        chatNpc("A wall has collapsed in the castle cellar.", "I suspect goblins, though the Duke disagrees.", "Investigate the tunnel and find out the truth.")
        return options("I'll investigate.", "Not now.", title = "Start The Lost Tribe?") == 1
    }

    override val steps = listOf(
        QuestStep(-1, listOf("Investigate the tunnel beneath Lumbridge Castle."), listOf("I found a brooch in the tunnel."),
            place = "the Lumbridge cellar tunnel", location = BROOCH_POST,
            search = QuestSearch(BROOCH_POST, "You find a brooch in the rubble.", gives = listOf(Items.BROOCH to 1))),
        QuestStep(Npcs.MISTAG, listOf("Show the brooch to Mistag in the Dorgeshuun mines."), listOf("Mistag explained that the cave goblins want peace."),
            place = "the Dorgeshuun mines", location = MISTAG_POST,
            talk = {
                if (player.attr[BROOCH_RETURNED] != true) {
                    if (!ExtraQuests.take(player, Items.BROOCH)) return@QuestStep false
                    player.attr[BROOCH_RETURNED] = true
                    FoundationRewards.grant(player, Items.MINING_HELMET, 1)
                }
                chatNpc("We broke through the wall by accident.", "We do not want war with the surface people.", "Thank you for returning our brooch.")
                true
            }),
        QuestStep(Npcs.DUKE_HORACIO, listOf("Investigate the missing silverware and return it to Duke Horacio."),
            listOf("I exposed Sigmund's plot and recovered the silverware."), place = "Lumbridge Castle's first floor", location = startLocation,
            gives = listOf(Items.PEACE_TREATY to 1), talk = {
                if (player.attr[THEFT_KNOWN] != true) {
                    player.attr[THEFT_KNOWN] = true
                    chatNpc("Our silverware has vanished from the cellar.", "Find it before I can agree to peace.")
                    false
                } else if (!player.inventory.contains(Items.SILVERWARE)) {
                    chatNpc("Investigate Sigmund and recover the silverware.", "He must have left evidence somewhere.")
                    val next = helperStep(player, 3)!!
                    with(this@TheLostTribe) { offerTravel(next.place!!, next.location!!, next.npc.takeIf { it >= 0 }) }
                    false
                } else {
                    player.inventory.remove(Items.SILVERWARE)
                    ExtraQuests.reclaim(player, Items.PEACE_TREATY)
                    chatNpc("Sigmund has deceived us. He is dismissed.", "Take this treaty to Mistag. We shall make peace.")
                    true
                }
            }),
        QuestStep(Npcs.MISTAG, listOf("Bring the peace treaty to Mistag."), listOf("The Duke and the Dorgeshuun made peace."),
            place = "the Dorgeshuun mines", location = MISTAG_POST, talk = {
                if (!ExtraQuests.take(player, Items.PEACE_TREATY)) false
                else { chatNpc("Ur-tag will sign the Duke's treaty.", "You are welcome in our mines, friend."); true }
            }),
    )

    override fun helperStep(player: Player, stage: Int): QuestStep? {
        if (stage == 2 && player.attr[BROOCH_RETURNED] != true && !player.inventory.contains(Items.BROOCH)) return steps[0]
        if (stage != 3 || player.attr[THEFT_KNOWN] != true || player.inventory.contains(Items.SILVERWARE)) return super.helperStep(player, stage)
        return when {
            player.attr[HAM_FOUND] == true -> QuestStep(-1, listOf("Search the crate by the H.A.M. hideout entrance."), emptyList(),
                place = "the H.A.M. hideout entrance", location = CRATE_POST)
            !player.inventory.contains(Items.KEY_5010) -> QuestStep(startNpc, listOf("Pickpocket Sigmund for his little key."), emptyList(),
                place = startPlace, location = startLocation)
            else -> QuestStep(-1, listOf("Open Sigmund's chest in the spinning wheel room."), emptyList(),
                place = "the castle spinning wheel room", location = CHEST_POST)
        }
    }

    fun stealKey(p: Player): Boolean {
        // Transcript has no pickpocket result outside the investigation: do not invent one.
        if (isFinished(p) || stage(p) != 3 || p.attr[THEFT_KNOWN] != true) return false
        if (p.skills.getCurrentLevel(Skills.THIEVING) < 13) { p.message("You need level 13 Thieving."); return false }
        ExtraQuests.reclaim(p, Items.KEY_5010)
        return true
    }

    fun openChest(p: Player): Boolean {
        if (isFinished(p) || stage(p) != 3 || p.attr[THEFT_KNOWN] != true) return false
        if (!p.inventory.contains(Items.KEY_5010)) { p.message("You don't have the key to open this chest."); return false }
        if (p.attr[HAM_FOUND] != true) {
            p.attr[HAM_FOUND] = true
            FoundationRewards.grant(p, Items.HAM_SHIRT, 1)
            FoundationRewards.grant(p, Items.HAM_ROBE, 1)
        }
        p.message("The H.A.M. robes in the chest reveal Sigmund's allegiance.")
        return true
    }

    fun searchCrate(p: Player): Boolean {
        if (isFinished(p) || stage(p) != 3 || p.attr[HAM_FOUND] != true) return false
        ExtraQuests.reclaim(p, Items.SILVERWARE)
        return true
    }
}
