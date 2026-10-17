package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.queue.QueueTask
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
FoundationQuests.register()

// ------------------------------------------------------------------------------------------------------ login

on_login {
    // The legacy cohort's quests and the OSRS list entries are completed by QuestStatus.login (quest_tab.plugin.kts).
    // Owner 2026-09-26: quest lamps are inventory items; lamps that waited at the Quest Guide are handed out once.
    FoundationRewards.itemiseOldLamps(player)
    FoundationQuests.applyQuestListOrder(player)
}

// The new-player introduction (TutorIntro): a logout or disconnect mid-tour leaves the player in the hall, intro seen.
on_logout { TutorIntro.finish(player, interrupted = true) }

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
    QuestHelperPanel.push(player, force = true)
}

// The Quest Helper side panel (QuestHelperPanel, client RspsQuestHelper): its buttons send these.
on_command("questpanel") {
    val args = player.getCommandArgs()
    val quest = args.getOrNull(1)?.toIntOrNull()?.let { slot -> FoundationQuests.SHORT.firstOrNull { it.slot == slot } }
    when (args.getOrNull(0)) {
        "list" -> player.attr.remove(QuestHelperPanel.VIEW)
        "view" -> if (quest != null) player.attr[QuestHelperPanel.VIEW] = quest.slot
        "track" -> if (quest != null) {
            player.attr.remove(QuestHelperPanel.VIEW)
            QuestHelper.track(player, quest)
        }
        "stop" -> {
            // "Stop helper" keeps it off until the player starts it again (as the quest list's Toggle Map Hint does).
            player.attr[QuestHelper.OFF] = true
            QuestHelper.stop(player)
        }
        "travel" -> {
            val target = quest?.let { QuestHelperPanel.travelTarget(player, it) }
            if (quest != null && target != null && QuestHelperPanel.inHall(player)) {
                player.queue { travel(target.first, target.second, target.third) }
            } else {
                player.message("The Quest Tutor sends you on from the Grand Exchange.")
            }
        }
    }
    QuestHelperPanel.push(player, force = true)
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

// Admin test tool (2026-10-07, "play every quest"): the account becomes a new-player ("choice") account with every short
// quest, choice and reward record back at the start, so the quests can be replayed (use it on the test server: it saves
// nothing there). Unlocks already held stay.
on_command("questreset", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    player.attr[QuestChoices.COHORT] = QuestChoices.CHOICE
    ChoiceGroup.values().forEach { player.attr.remove(it.key) }
    FoundationQuests.SHORT.forEach { quest ->
        player.attr.remove(FoundationRewards.rewardedKey(quest))
        quest.setStage(player, 0)
    }
    QuestHelper.stop(player, silent = true)
    player.attr.remove(QuestHelper.OFF)
    gg.rsmod.plugins.content.quests.QuestStatus.sync(player)
    player.message("Quest reset: all eight short quests and the quest choices are open again.")
}

// Admin test tools (2026-10-07): `talknpc <id> [option]` clicks the nearest such npc's option exactly as the client does
// (the conformance bots' OpNpc packet, real handler and walk), `questguide` opens "Choose your path".
on_command("talknpc", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val id = args.getOrNull(0)?.toIntOrNull() ?: return@on_command player.message("talknpc <npc id> [option 1-5]")
    val option = args.getOrNull(1)?.toIntOrNull() ?: 1
    var best: gg.rsmod.game.model.entity.Npc? = null
    player.world.npcs.forEach { n ->
        if (n.id == id && n.tile.height == player.tile.height && n.tile.isWithinRadius(player.tile, 40) &&
            (best == null || n.tile.getDistance(player.tile) < best!!.tile.getDistance(player.tile))
        ) {
            best = n
        }
    }
    val npc = best ?: return@on_command player.message("No npc $id within 40 tiles.")
    val client = player as? gg.rsmod.game.model.entity.Client ?: return@on_command
    gg.rsmod.plugins.content.cmd.conformance.Bots.npcOption(client, npc, option)
}

on_command("introscene", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val n = player.getCommandArgs().getOrNull(0)?.toIntOrNull()
    if (n == null) TutorIntro.start(player) else TutorIntro.preview(player, n)
}

// Camera tuning for the intro (admin): `camworld x z height lookX lookZ lookHeight [rate step]` in world tiles; a rate below
// 100 glides there. `camworld` alone resets the camera.
on_command("camworld", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    val args = player.getCommandArgs()
    val relative = args.firstOrNull() == "r"
    val a = args.mapNotNull { it.toIntOrNull() }.mapIndexed { i, v -> if (!relative) v else when (i) { 0, 3 -> v + player.tile.x; 1, 4 -> v + player.tile.z; else -> v } }
    if (a.size < 6) {
        player.resetCamera()
        return@on_command
    }
    val base = player.lastKnownRegionBase ?: return@on_command
    val rate = a.getOrElse(6) { 100 }
    val step = a.getOrElse(7) { 0 }
    player.moveCameraTo(rate, a[0] - base.x, a[1] - base.z, a[2], step)
    player.cameraLookAt(a[3] - base.x, a[4] - base.z, a[5], step, rate)
    player.message("camworld from ${a[0]},${a[1]} h${a[2]} look ${a[3]},${a[4]} h${a[5]} rate $rate (base ${base.x},${base.z}, me ${player.tile.x},${player.tile.z})")
}

on_command("questguide", gg.rsmod.game.model.priv.Privilege.ADMIN_POWER) {
    QuestGuideUi.open(player)
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

// Its Talk-to answers only on the yml tile, so the developer-mode editor must not move it (WorldEdits.codePostOf).
on_world_init {
    world.queue {
        // A tick after boot, when every spawn_npc line stands.
        wait(1)
        world.npcs.forEach {
            if (it.id == config.choiceNpc && it.spawnTile == config.choiceNpcTile) {
                gg.rsmod.plugins.content.mechanics.worldedit.WorldEdits.registerCodePost(it, "data/cfg/new_player.yml, choice_npc")
            }
        }
    }
}

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
        // Owner 2026-10-07: the new-player introduction can be watched again here.
        menu += "Show me the introduction again." to { TutorIntro.start(player) }
        menu += "Goodbye." to { }
        val choice = options(*menu.take(5).map { it.first }.toTypedArray(), title = "Quest Tutor")
        val entry = menu.getOrNull(choice - 1) ?: return
        if (entry.first == "Goodbye.") return
        entry.second(this)
        // The tour runs in its own queue.
        if (entry.first.startsWith("Show me the introduction")) return
        // Night run 2026-10-08: after the directions (and the travel they offer) the conversation ends - the menu used to
        // open again at the travel's destination, far from the Tutor.
        if (entry.first == "Where do the quests start?") return
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
                // N92: lamps get the Annihilation rate (owner 10-17, OSRS); the line names the real gain.
                val gained = lamp.xp * player.xpMultiplier(skill)
                player.addXp(skill, lamp.xp.toDouble())
                player.message("The lamp grants you ${String.format(java.util.Locale.US, "%,d", gained.toLong())} ${Skills.getSkillName(world, skill)} experience.")
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
    // Owner 2026-10-07: the Tutor's quest menu starts the Quest Helper for the chosen quest.
    if (QuestHelper.enabled(player) && QuestHelper.tracked(player) != chosen &&
        options("Start the Quest Helper for this quest.", "No thanks.", title = chosen.name) == 1
    ) {
        QuestHelper.track(player, chosen)
    }
    // Owner 2026-10-08 (OWNER_QUESTIONS Q17 A): the quests stay as they are; a player below the quest's strongest foe is
    // told to train first - below the quest's wiki recommendation (ShortQuest.trainingAdvice, owner 2026-10-10).
    chosen.trainingAdvice(player)?.let { advice ->
        chatNpc(
            "Train your combat first! For this quest I'd",
            "recommend ${advice.text}.",
            "Come back when you are ready.",
            facialExpression = FacialExpression.WORRIED,
        )
    }
    val stage = chosen.stage(player)
    if (stage <= 0) {
        chatNpc("${chosen.name} begins with ${chosen.startNpcName}, at ${chosen.startPlace}.", facialExpression = FacialExpression.CALM_TALK)
        chosen.startLocation?.let { if (chosen.startPlace != "the Grand Exchange hall") travel(chosen.startPlace, it, chosen.startNpc) }
    } else {
        val step = chosen.steps[stage - 1]
        chatNpc(*step.journal.map { it.replace(Regex("<[^>]+>"), "") }.toTypedArray(), facialExpression = FacialExpression.CALM_TALK)
        val place = step.place
        val location = step.location
        if (location != null && place != null && place != "the Grand Exchange hall") travel(place, location, step.npc.takeIf { step.fight == null })
    }
}

suspend fun QueueTask.travel(place: String, destination: Tile, npc: Int? = null) {
    if (options("Send me to $place.", "I'll find my own way.", title = "Travel") != 1) return
    // Beside the npc the player is sent to (FoundationQuests.landing), so it can be reached on foot.
    val tile = FoundationQuests.landing(player.world, npc, destination)
    // Owner 2026-09-26/27: a Dangerous destination always asks first (interface 382).
    if (!gg.rsmod.plugins.content.mechanics.pvp.DangerWarning.confirmQuestTravel(this, tile)) return
    player.canTeleport(TeleportType.MODERN) { player.teleport(tile, TeleportType.MODERN, exact = tile != destination) }
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