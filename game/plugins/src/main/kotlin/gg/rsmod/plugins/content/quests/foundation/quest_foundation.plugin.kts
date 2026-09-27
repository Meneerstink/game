package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.skill.XpRates
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import gg.rsmod.plugins.content.magic.teleport
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.content.newplayer.SummoningKit

/*
 * The new-player foundation (owner 2026-09-26): the Quest Guide in the Grand Exchange hall (permanent quest choices, the
 * one-time Summoning start, quest experience lamps), the npcs of the eight short quests, and the login work that keeps
 * the quest list right (cohort, OSRS list entries, "unfinished quests on top").
 */

val config = NewPlayerConfig.reload()
XpRates.normal = config.normalXpRate
FoundationQuests.register()

// ------------------------------------------------------------------------------------------------------ login

on_login {
    // The legacy cohort's quests and the OSRS list entries are completed by QuestStatus.login (quest_tab.plugin.kts).
    // Owner 2026-09-26: quest lamps are inventory items; lamps that waited at the Quest Guide are handed out once.
    FoundationRewards.itemiseOldLamps(player)
    FoundationQuests.applyQuestListOrder(player)
}

// The short quests' fight steps: the owner's kill of a quest foe completes the step (QuestFights).
on_npc_killed { killer, npc -> QuestFights.onKilled(killer, npc) }

// The quest bosses' own mechanics (QuestBosses: Dessous, Kamil, Damis, Fareed, Elvarg).
QuestBosses.SCRIPTS.forEach { script ->
    on_npc_combat(*script.ids) {
        npc.queue { script.handleSpecialCombat(this) }
    }
}

// Quest Helper (QuestHelper): arrows to the tracked quest's next step, and fight-step bosses appearing on arrival.
on_login {
    if (QuestHelper.needsTimer(player)) QuestHelper.start(player)
}

on_timer(QuestHelper.TIMER) {
    QuestHelper.tick(player)
    if (QuestHelper.needsTimer(player)) player.timers[QuestHelper.TIMER] = QuestHelper.INTERVAL
}

on_command("questhelper") {
    val quest = QuestHelper.tracked(player) ?: FoundationQuests.SHORT.firstOrNull { FoundationQuests.inProgress(player, it) }
    if (quest == null) {
        player.message("You have no quest in progress to track.")
    } else {
        QuestHelper.toggle(player, quest)
    }
}

// ---------------------------------------------------------------------------------------------- the Quest Guide

// Moved or replaced through data/cfg/new_player.yml (choice_npc) - the later intro sends new players here.
spawn_npc(
    npc = config.choiceNpc,
    x = config.choiceNpcTile.x,
    z = config.choiceNpcTile.z,
    height = config.choiceNpcTile.height,
    walkRadius = 0,
    direction = config.choiceNpcFacing,
)

// Owner 2026-09-26: right-click "Quest choices" opens "Choose your path" (also to see what was chosen).
on_npc_option(npc = config.choiceNpc, option = "quest choices") {
    QuestGuideUi.open(player)
}

on_npc_option(npc = config.choiceNpc, option = "talk-to") {
    if (player.getInteractingNpc().spawnTile != config.choiceNpcTile) {
        player.queue { chatNpc("The Quest Tutor in the Grand Exchange hall keeps the quest records now.") }
        return@on_npc_option
    }
    player.queue { questGuide() }
}

// ---- "Choose your path" (QuestGuideUi): the permanent quest choices as one window ----
on_button(interfaceId = QuestGuideUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.CLOSE) {
    QuestGuideUi.close(player)
}
on_interface_close(interfaceId = QuestGuideUi.INTERFACE_ID) {
    QuestGuideUi.onClosed(player)
}
for (r in 0 until gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.ROWS) {
    on_button(interfaceId = QuestGuideUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.rowBase(r) + gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.ROW_CONFIRM) {
        QuestGuideUi.clickConfirm(player, r)
    }
    for (c in 0 until gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.CARDS) {
        on_button(interfaceId = QuestGuideUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.cardBase(r, c)) {
            QuestGuideUi.clickCard(player, r, c)
        }
    }
}
on_button(interfaceId = QuestGuideUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.YES) {
    QuestGuideUi.clickYes(player)
}
on_button(interfaceId = QuestGuideUi.INTERFACE_ID, component = gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool.NO) {
    QuestGuideUi.clickNo(player)
}
suspend fun QueueTask.questGuide() {
    chatNpc(
        "Welcome to the hall, adventurer. I keep the records",
        "of every quest in Gielinor - and of the paths each",
        "new hero chooses.",
        facialExpression = FacialExpression.CALM_TALK,
    )
    while (true) {
        val menu = mutableListOf<Pair<String, suspend QueueTask.() -> Unit>>()
        // The choice window also shows what was chosen, so it is always on offer (and on the npc's right-click).
        menu += (if (!QuestChoices.isLegacy(player) && ChoiceGroup.values().any { QuestChoices.chosen(player, it) == null }) "Make my quest choices." else "View my quest choices.") to { QuestGuideUi.open(player) }
        if (player.attr[SummoningKit.CLAIMED] != true) menu += "Claim my Summoning start." to { claimSummoning() }
        menu += "Where do the quests start?" to { questDirections() }
        menu += "Goodbye." to { }
        val choice = options(*menu.take(5).map { it.first }.toTypedArray(), title = "Quest Tutor")
        val entry = menu.getOrNull(choice - 1) ?: return
        if (entry.first == "Goodbye.") return
        entry.second(this)
        // The choice window replaces the dialogue.
        if (entry.first.endsWith("my quest choices.")) return
    }
}

suspend fun QueueTask.chooseGroup() {
    val open = ChoiceGroup.values().filter { QuestChoices.chosen(player, it) == null }
    val labels = open.map { "${it.label}: choose one quest." } + "Not now."
    val group = open.getOrNull(options(*labels.toTypedArray(), title = "Which choice?") - 1) ?: return
    val quests = QuestChoices.available(player, group)
    if (quests.isEmpty()) {
        chatNpc("You have finished every ${group.label.lowercase()} quest already - there is nothing left to choose.")
        return
    }
    val pick = quests.getOrNull(options(*(quests.map { QuestChoices.UNLOCK_LABEL.getValue(it) } + "Not now.").toTypedArray(), title = "${group.label}: choose one") - 1) ?: return
    chatNpc(
        "Choose ${pick.name} and I will record it as done,",
        "reward and all. The other ${group.label.lowercase()} quests you",
        "would have to play yourself.",
        facialExpression = FacialExpression.THINKING,
    )
    val confirm = options("Yes - ${pick.name} (this choice is permanent).", "No, let me think it over.", title = "This choice is permanent")
    if (confirm != 1) return
    when (QuestChoices.choose(player, group, pick)) {
        QuestChoices.Result.CHOSEN -> Unit
        QuestChoices.Result.ALREADY_CHOSEN -> chatNpc("Your ${group.label.lowercase()} choice is already written in my records.")
        QuestChoices.Result.ALREADY_COMPLETE -> chatNpc("You have finished ${pick.name} already.")
        QuestChoices.Result.LEGACY_ACCOUNT -> chatNpc("Your quests were all recorded long before these choices existed.")
        QuestChoices.Result.NOT_IN_GROUP -> Unit
    }
}

suspend fun QueueTask.claimSummoning() {
    chatNpc(
        "Pikkupstix asked me to see every newcomer off with a",
        "wolf whistle and enough charms and shards to reach",
        "level 55 Summoning. Use them at an obelisk.",
        facialExpression = FacialExpression.HAPPY,
    )
    SummoningKit.claim(player)
}

suspend fun QueueTask.claimLamps() {
    val lamps = FoundationRewards.pendingLamps(player)
    // Three lamps a page ("More lamps"), so a player with nine waiting lamps sees them all.
    var lampPage = 0
    var picked: QuestLamp? = null
    while (picked == null) {
        val shown = lamps.drop(lampPage * 3).take(3)
        val moreLamps = (lampPage + 1) * 3 < lamps.size
        val labels = shown.map { it.label.replace(Regex(",? ?\\(?level \\d+\\+\\)?"), "").replace(" ()", "") } + (if (moreLamps) listOf("More lamps") else emptyList()) + "Not now."
        val choice = options(*labels.toTypedArray(), title = "Your quest lamps (${lamps.size})")
        when {
            choice in 1..shown.size -> picked = shown[choice - 1]
            moreLamps && choice == shown.size + 1 -> lampPage++
            else -> return
        }
    }
    val lamp = picked ?: return
    val skills = lamp.skills
    // Owner 2026-09-26: quest lamps have no level requirement.
    val title = "Choose a skill"
    var page = 0
    while (true) {
        val slice = skills.drop(page * 3).take(3)
        val more = (page + 1) * 3 < skills.size
        val labels = slice.map { Skills.getSkillName(world, it) } + (if (more) listOf("More skills") else emptyList()) + "Cancel"
        val choice = options(*labels.toTypedArray(), title = title)
        when {
            choice <= 0 || choice == labels.size -> return
            more && choice == labels.size - 1 -> page++
            else -> {
                val skill = slice[choice - 1]
                val current = FoundationRewards.pendingLamps(player).toMutableList()
                if (!current.remove(lamp)) return
                FoundationRewards.setPendingLamps(player, current)
                player.addXp(skill, lamp.xp.toDouble(), modifiers = false)
                player.message("The lamp grants you ${String.format(java.util.Locale.US, "%,d", lamp.xp)} ${Skills.getSkillName(world, skill)} experience.")
                return
            }
        }
    }
}

suspend fun QueueTask.questDirections() {
    val open = FoundationQuests.SHORT.filterNot { it.isFinished(player) }
    if (open.isEmpty()) {
        chatNpc("Every quest in my records carries your name already.", facialExpression = FacialExpression.HAPPY)
        return
    }
    // Three quests a page, so all eight fit: "More quests" pages on, "Never mind" leaves.
    var page = 0
    var quest: ShortQuest? = null
    while (quest == null) {
        val shown = open.drop(page * 3).take(3)
        val more = (page + 1) * 3 < open.size
        val labels = shown.map { it.name } + (if (more) listOf("More quests") else emptyList()) + "Never mind."
        val choice = options(*labels.toTypedArray(), title = "Which quest?")
        when {
            choice in 1..shown.size -> quest = shown[choice - 1]
            more && choice == shown.size + 1 -> page++
            else -> return
        }
    }
    val chosen = quest ?: return
    val stage = chosen.stage(player)
    if (stage <= 0) {
        chatNpc("${chosen.name} begins with ${chosen.startNpcName}, at ${chosen.startPlace}.", facialExpression = FacialExpression.CALM_TALK)
        chosen.startLocation?.let { if (chosen.startPlace != "the Grand Exchange hall") travel(chosen.startPlace, it) }
    } else {
        val step = chosen.steps[stage - 1]
        chatNpc(*step.journal.map { it.replace(Regex("<[^>]+>"), "") }.toTypedArray(), facialExpression = FacialExpression.CALM_TALK)
        val place = step.place
        val location = step.location
        if (location != null && place != null && place != "the Grand Exchange hall") travel(place, location)
    }
}

suspend fun QueueTask.travel(place: String, tile: Tile) {
    if (options("Send me to $place.", "I'll find my own way.", title = "Travel") != 1) return
    // Owner 2026-09-26/27: a Dangerous destination always asks first (interface 382).
    if (!gg.rsmod.plugins.content.mechanics.pvp.DangerWarning.confirmQuestTravel(this, tile)) return
    player.canTeleport(TeleportType.MODERN) { player.teleport(tile, TeleportType.MODERN) }
}

// ------------------------------------------------------------------------------------- the quests' own npcs

FoundationQuests.NPC_POSTS.forEach { post ->
    spawn_npc(npc = post.npc, x = post.tile.x, z = post.tile.z, height = post.tile.height, walkRadius = 0, direction = post.facing)
    on_npc_option(npc = post.npc, option = "talk-to") {
        player.queue {
            if (!FoundationQuests.talk(this, post.npc)) chatNpc(*post.idle.toTypedArray(), facialExpression = FacialExpression.CALM_TALK)
        }
    }
}

// King Arthur and Sir Lancelot already stand in Camelot (spawns_11062) but had nothing to say.
on_npc_option(npc = Npcs.KING_ARTHUR, option = "talk-to") {
    player.queue {
        if (!FoundationQuests.talk(this, Npcs.KING_ARTHUR)) {
            chatNpc("Welcome to Camelot. The Round Table is always glad of", "a brave soul.", facialExpression = FacialExpression.HAPPY)
        }
    }
}

on_npc_option(npc = Npcs.SIR_LANCELOT, option = "talk-to") {
    player.queue {
        if (!FoundationQuests.talk(this, Npcs.SIR_LANCELOT)) {
            chatNpc("I am Sir Lancelot, the greatest knight in the land.", "Try not to swoon.", facialExpression = FacialExpression.SNOBBY)
        }
    }
}

// Npcs of the longer short quests that stand in the world already but had nothing to say (owner 2026-09-26).
mapOf(
    Npcs.BRUNDT_THE_CHIEFTAIN to listOf("Welcome to Rellekka, outerlander. Mind your", "manners in my longhall."),
    Npcs.GARKOR to listOf("The 10th squad stands ready. Ook!", "Er - I mean, at your service."),
    Npcs.ANITA to listOf("It's so peaceful by the Grand Tree now.", "Thank you for everything."),
    // Edmond already stands in East Ardougne (spawns_10292); the quest used to spawn a second one next to him.
    Npcs.EDMOND to listOf("Ardougne is quieter these days, thank Saradomin.", "Elena sends her regards from Prifddinas."),
).forEach { (id, idle) ->
    on_npc_option(npc = id, option = "talk-to") {
        player.queue {
            if (!FoundationQuests.talk(this, id)) chatNpc(*idle.toTypedArray(), facialExpression = FacialExpression.CALM_TALK)
        }
    }
}

// Lunar Diplomacy (OSRS Wiki quick guide): "Cannot equip any different equipment when fighting Me".
can_equip_any_item { p, _ ->
    val foe = QuestFights.active(p)
    if (foe != null && foe.id == Npcs.ME) {
        p.message("You can't change your equipment in the dream world.")
        false
    } else {
        true
    }
}