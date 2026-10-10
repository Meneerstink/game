package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.message.impl.CloseModalMessage
import gg.rsmod.game.message.impl.ResumePauseButtonMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.api.getChildId
import gg.rsmod.plugins.api.getDisplayComponentId
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.pvp.AreaState
import gg.rsmod.plugins.content.mechanics.pvp.DangerWarning
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.foundation.ChoiceGroup
import gg.rsmod.plugins.content.quests.foundation.DesertTreasure
import gg.rsmod.plugins.content.quests.foundation.DesertTreasureII
import gg.rsmod.plugins.content.quests.foundation.DragonSlayerII
import gg.rsmod.plugins.content.quests.foundation.FoundationQuests
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.quests.foundation.KNIGHT_WAVES_COMPLETE
import gg.rsmod.plugins.content.quests.foundation.KNIGHT_WAVES_VARBIT
import gg.rsmod.plugins.content.quests.foundation.KingsRansom
import gg.rsmod.plugins.content.quests.foundation.LunarDiplomacy
import gg.rsmod.plugins.content.quests.foundation.MonkeyMadnessII
import gg.rsmod.plugins.content.quests.foundation.QuestChoices
import gg.rsmod.plugins.content.quests.foundation.QuestFights
import gg.rsmod.plugins.content.quests.foundation.QuestGuideUi
import gg.rsmod.plugins.content.quests.foundation.QuestHelper
import gg.rsmod.plugins.content.quests.foundation.ShortQuest
import gg.rsmod.plugins.content.quests.foundation.SongOfTheElves
import gg.rsmod.plugins.content.quests.foundation.TempleAtSenntisten
import gg.rsmod.plugins.content.quests.foundation.TutorIntro
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards
import gg.rsmod.game.tools.importer.QuestGuideInterfaceImportTool as GuideUi
import gg.rsmod.game.tools.importer.QuestHelperPanelImportTool as HelperPanel

/**
 * Quests (owner 2026-10-07: "you need to test all the quests we have in our server! please fix everything, play every
 * quest"). Every quest is PLAYED by a headless bot through real input only - npc clicks (OpNpc), dialogue continues and
 * option answers (RESUME_PAUSEBUTTON, read from the chatbox text the bot was sent), interface buttons (IF_BUTTON), item
 * options, CLOSE_MODAL - and judged after every step:
 *
 *   conform quests                 everything
 *   conform quests <name>          entries whose name contains <name>: "play desert-treasure", "choose kings-ransom",
 *                                  "tutor directions", "classic cooks-assistant", ...
 *
 * Entries:
 *  - `play <quest>`   the eight short foundation quests (FoundationQuests.SHORT) from the start npc to the reward scroll.
 *                     Setup per bot: a fresh "choice"-cohort account (a bot logs in as a legacy account, which completes
 *                     the eight; the suite sets them back to not started - exactly what a new account has), 99 stats,
 *                     anti-dragon shield and ice gloves worn through their item option. Talk steps: the bot is put
 *                     beside the step's npc (moveTo = the player walking there), the npc must stand where the step sends
 *                     the player (same floor, within QuestHelper.NPC_RANGE). Fight steps: the step npc's own travel offer
 *                     ("Can you get me to ...?") and the Dangerous-area warning 382 "Enter" are answered, the foe must
 *                     appear on arrival and is killed through the bot's Attack clicks (setup lowers each foe's life to
 *                     [FOE_LIFE] so a run stays short and keeps the bot alive; the killing blows are real hits).
 *                     Puzzles (Brundt's question, the trial, Merlin's tumblers, the Desert Treasure II seals) are first
 *                     answered wrong once (the stage must stay) and then right, from the clue the game gives.
 *                     After every step: stage advanced, no lock, no dialogue/modal left open, journal page (quest tab
 *                     190 -> 275) shows the step done and the next one open, Quest Helper arrow and panel on the next
 *                     step. Every accepted travel: 382 shown exactly for a Dangerous destination, and the travel
 *                     (teleport animation or the move) starts within 2 ticks of "Enter" with no dialogue open (owner
 *                     2026-10-07: "the teleport only starts after the dialogue is clicked away").
 *                     End: quest complete in the quest list (clientscript 2193 rule = QuestList progress 2), quest points,
 *                     experience, items, lamps and the unlock exactly as the reward scroll promises, paid once: a second
 *                     talk and a Choose-your-path click on the finished quest pay nothing.
 *  - `choose <quest>` the permanent choice: Quest Guide "Quest choices" -> card -> Confirm -> Yes (QuestGuideUi), the
 *                     full reward once, the other quests of the row stay locked and unpaid, a second Yes pays nothing.
 *  - `tutor directions` the Quest Tutor's "Where do the quests start?" travel (382 + teleport timing).
 *  - `tutor combat 3`  the same for a combat-3 player: the Tutor says to train combat first (owner Q17 A).
 *  - `foe anims`       every quest foe (and chain foe) attacks and dies with an animation that exists in the cache; a
 *                     block animation no donor has is UNSOURCED.
 *  - `classic <quest>` the twelve 2011 quests in quests/impl, see [ClassicQuests].
 */
object QuestsSuite : Conformance.Suite {
    /** A foe's life after setup: the step stays a real kill through the combat path, but a short one. */
    private const val FOE_LIFE = 5

    private const val CHATBOX = 752
    private const val DIALOGUE = 13
    private const val ITEM_DIALOGUE = 12
    private const val QUEST_TAB = 190
    private const val QUEST_LIST = 18
    private const val JOURNAL = 275
    private const val QUEST_COMPLETE = 277

    private val GE_HALL = Tile(3162, 3489, 0)

    private const val TRAVEL = "travel answer"
    private const val ENTER = "Enter on 382"
    private const val ENTER_CLOSED = "Enter on 382, then the window closed by the client (CLOSE_MODAL)"

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val only = args.joinToString(" ").lowercase()

        suspend fun scenario(
            name: String,
            body: suspend QueueTask.() -> Unit,
        ) {
            if (Conformance.stopRequested || (only.isNotBlank() && !name.contains(only))) return
            run.progress(name)
            val start = run.world.currentCycle
            val mark = run.mark()
            try {
                body()
            } catch (e: Throwable) {
                run.add(Conformance.Result(name, "scenario ran", Verdict.FAIL, "no exception", e.toString().take(200)))
            }
            run.window(name, start)
            run.cleanupSince(mark)
            wait(2)
        }

        FoundationQuests.SHORT.forEach { quest -> scenario("play ${key(quest)}") { playShort(run, quest) } }
        QuestGuideUi.ROWS.forEachIndexed { row, group ->
            group.quests.forEachIndexed { card, quest -> scenario("choose ${key(quest)}") { choose(run, row, card, group, quest) } }
        }
        scenario("tutor directions") { tutorDirections(run) }
        scenario("tutor combat 3") { tutorDirections(run, combat = 3) }
        scenario("foe anims") { foeAnims(run) }
        // Owner 2026-10-07 night run: the Quest Tutor's new-player introduction (TutorIntro).
        scenario("bank seal") { bankSeal(run) }
        scenario("intro tour") { introTour(run, "intro tour", skipAfter = null, logoutAt = null) }
        scenario("intro skip") { introTour(run, "intro skip", skipAfter = 2, logoutAt = null) }
        scenario("intro logout") { introTour(run, "intro logout", skipAfter = null, logoutAt = 3) }
        ClassicQuests.ALL.forEach { classic -> scenario("classic ${classic.key}") { with(ClassicQuests) { play(run, classic) } } }
    }

    // ---- names --------------------------------------------------------------------------------------------------------

    fun key(quest: ShortQuest): String = quest.name.substringBefore(" - ").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    private fun code(quest: ShortQuest): String =
        when (quest) {
            DesertTreasure -> "DT"
            LunarDiplomacy -> "LD"
            TempleAtSenntisten -> "TAS"
            KingsRansom -> "KR"
            DesertTreasureII -> "DTII"
            DragonSlayerII -> "DSII"
            SongOfTheElves -> "SOTE"
            MonkeyMadnessII -> "MMII"
            else -> quest.slot.toString()
        }

    // ---- what each quest promises (its reward scroll / overview text, the wiki values the quest file cites) -------------

    private class Promise(
        val points: Int,
        val xp: Map<Int, Int>,
        val items: Map<Int, Int>,
        val xpLamps: Int,
        val combatLamps: Int,
        val unlockText: String,
        val unlock: (Client) -> Boolean,
    )

    private val PROMISES: Map<ShortQuest, Promise> =
        mapOf(
            DesertTreasure to
                Promise(3, mapOf(Skills.MAGIC to 20_000), emptyMap(), 0, 0, "Ancient Magicks") { it.attr[UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED] == true },
            LunarDiplomacy to
                Promise(2, mapOf(Skills.MAGIC to 5_000, Skills.RUNECRAFTING to 5_000), mapOf(Items.ASTRAL_RUNE to 50), 0, 0, "Lunar spellbook") {
                    it.attr[UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED] == true
                },
            TempleAtSenntisten to
                Promise(2, mapOf(Skills.PRAYER to 10_000), mapOf(Items.ANCIENT_HYMNAL to 1), 1, 2, "Ancient Curses") { it.attr[AncientCurses.UNLOCKED_ATTR] == true },
            KingsRansom to
                Promise(1, emptyMap(), emptyMap(), 1, 2, "Knight Waves done (Chivalry, Piety)") { it.getVarbit(KNIGHT_WAVES_VARBIT) >= KNIGHT_WAVES_COMPLETE },
            DesertTreasureII to
                Promise(5, emptyMap(), mapOf(Items.RING_OF_SHADOWS_UNCHARGED to 1), 0, 3, "ancient rings and sceptres") {
                    it.attr[UnlockNpcRewards.ANCIENT_RINGS_UNLOCKED] == true && it.attr[UnlockNpcRewards.DESERT_TREASURE_II_UNLOCKED] == true
                },
            DragonSlayerII to
                Promise(
                    5,
                    mapOf(Skills.SMITHING to 80_000, Skills.MINING to 60_000, Skills.AGILITY to 50_000, Skills.THIEVING to 50_000),
                    emptyMap(), 0, 4, "Myths' Guild and Ava's assembler",
                ) { it.attr[UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED] == true && it.attr[UnlockNpcRewards.MYTHS_GUILD_UNLOCKED] == true },
            SongOfTheElves to
                Promise(
                    4,
                    listOf(Skills.AGILITY, Skills.CONSTRUCTION, Skills.FARMING, Skills.HERBLORE, Skills.HUNTER, Skills.MINING, Skills.SMITHING, Skills.WOODCUTTING)
                        .associateWith { 40_000 },
                    emptyMap(), 0, 0, "crystal equipment (quest complete)",
                ) { true },
            MonkeyMadnessII to
                Promise(
                    4,
                    mapOf(Skills.SLAYER to 80_000, Skills.AGILITY to 60_000, Skills.THIEVING to 50_000, Skills.HUNTER to 50_000),
                    emptyMap(), 0, 2, "zenyte and the heavy ballista (quest complete)",
                ) { true },
        )

    private const val PROMISE_SOURCE = "reward scroll / overview text (FoundationQuests reward lines, wiki 2026-09-26)"
    private const val QP_SOURCE = "RS Wiki / OSRS Wiki quest points"

    // ---- account setup --------------------------------------------------------------------------------------------------

    /**
     * A bot logs in without NEW_ACCOUNT_ATTR, so QuestChoices puts it in the legacy cohort and QuestStatus.login completes
     * the eight choice quests with their unlocks. This puts the account back where a brand-new account starts: choice
     * cohort, nothing chosen, the eight quests not started and unpaid, their unlocks off.
     */
    fun freshAccount(bot: Client) {
        bot.attr[QuestChoices.COHORT] = QuestChoices.CHOICE
        ChoiceGroup.values().forEach { bot.attr.remove(it.key) }
        FoundationQuests.SHORT.forEach { q ->
            if (q.usesVarbits) bot.setVarbit(q.questId, 0) else bot.setVarp(q.questId, 0)
            bot.attr.remove(FoundationRewards.rewardedKey(q))
        }
        listOf(
            UnlockNpcRewards.ANCIENT_MAGIC_UNLOCKED, UnlockNpcRewards.ANCIENT_MAGIC_REWARDED,
            UnlockNpcRewards.LUNAR_MAGIC_UNLOCKED, UnlockNpcRewards.LUNAR_MAGIC_REWARDED,
            UnlockNpcRewards.ANCIENT_CURSES_REWARDED,
            UnlockNpcRewards.DESERT_TREASURE_II_REWARDED, UnlockNpcRewards.DESERT_TREASURE_II_UNLOCKED,
            UnlockNpcRewards.SCAR_ESSENCE_MINE_UNLOCKED, UnlockNpcRewards.ANCIENT_RINGS_UNLOCKED,
            UnlockNpcRewards.FORGOTTEN_FOUR_UNLOCKED, UnlockNpcRewards.DEMONIC_BRUTUS_UNLOCKED,
            UnlockNpcRewards.DRAGON_SLAYER_II_REWARDED, UnlockNpcRewards.DRAGON_SLAYER_II_UNLOCKED,
            UnlockNpcRewards.MYTHS_GUILD_UNLOCKED, UnlockNpcRewards.AVAS_ASSEMBLER_UNLOCKED,
            AncientCurses.UNLOCKED_ATTR, AncientCurses.NPC_UNLOCKED_ATTR,
            FoundationRewards.PENDING_LAMPS, QuestHelper.TRACKED, QuestHelper.OFF,
        ).forEach { bot.attr.remove(it) }
        bot.setVarbit(KNIGHT_WAVES_VARBIT, 0)
        QuestStatus.sync(bot)
    }

    /** Worn through the items' own Wield/Wear option: Elvarg's fire and Fareed's heat are part of the quests. */
    private suspend fun QueueTask.gear(bot: Client) {
        for (item in listOf(Items.ANTIDRAGON_SHIELD, Items.ICE_GLOVES)) {
            bot.inventory.add(item, 1)
            wait(1)
            val slot = bot.inventory.getItemIndex(item, false)
            if (slot >= 0) listOf("Wield", "Wear", "Equip").firstOrNull { Bots.itemOption(bot, slot, it) }
            wait(1)
        }
    }

    private fun heal(bot: Client) {
        if (bot.getCurrentLifepoints() < bot.getMaximumLifepoints()) bot.setCurrentLifepoints(bot.getMaximumLifepoints())
    }

    fun held(
        bot: Client,
        item: Int,
    ): Int = bot.inventory.getItemCount(item) + bot.bank.getItemCount(item) + bot.equipment.getItemCount(item)

    fun xpOf(bot: Client): Map<Int, Double> = (0 until bot.skills.maxSkills).associateWith { bot.skills.getCurrentXp(it) }

    // ---- screen reading (what the bot's client was sent) ----------------------------------------------------------------

    private val SET_TEXT = Regex("""^IfSetTextMessage\(parent=(\d+), child=(\d+), text=(.*)\)$""")

    /** The latest IfOpenSub event that put [iface] in the chatbox (or anywhere when [chatboxOnly] is false). */
    private fun openEvent(
        run: Conformance.Run,
        bot: Client,
        iface: Int,
        chatboxOnly: Boolean = true,
    ): Recording.Ev? =
        run.recording.of(bot.username, "msg").lastOrNull {
            it.text.startsWith("IfOpenSubMessage(") && it.text.contains("component=$iface,") && (!chatboxOnly || it.text.contains("parent=$CHATBOX,"))
        }

    /** Component texts of [iface] since its latest open (component -> text). */
    private fun textsOf(
        run: Conformance.Run,
        bot: Client,
        iface: Int,
        chatboxOnly: Boolean = true,
    ): Map<Int, String> {
        val events = run.recording.of(bot.username, "msg")
        val open = openEvent(run, bot, iface, chatboxOnly)
        val from = if (open == null) 0 else events.indexOfLast { it === open }.coerceAtLeast(0)
        val texts = HashMap<Int, String>()
        for (i in from until events.size) {
            val m = SET_TEXT.find(events[i].text) ?: continue
            if (m.groupValues[1].toInt() == iface) texts[m.groupValues[2].toInt()] = m.groupValues[3]
        }
        return texts
    }

    /** The chatbox dialogue on screen (752:13, or the item dialogue slot 752:12), or -1. */
    fun chatOpen(bot: Client): Int {
        val a = bot.interfaces.getInterfaceAt(CHATBOX, DIALOGUE)
        return if (a != -1) a else bot.interfaces.getInterfaceAt(CHATBOX, ITEM_DIALOGUE)
    }

    private fun isOptions(iface: Int) = iface in 228..240 && iface % 2 == 0

    /** The component a click on "Click here to continue" sends for the server's dialogues (QueueTaskExt). */
    private fun continueComponent(iface: Int): Int =
        when (iface) {
            in 241..244 -> 4 + (iface - 240)
            in 64..67 -> 4 + (iface - 63)
            210 -> 1
            in 211..214 -> iface - 208
            else -> 0
        }

    fun describeScreen(
        run: Conformance.Run,
        bot: Client,
    ): String {
        val chat = chatOpen(bot)
        val chatText = if (chat == -1) "none" else "$chat '" + textsOf(run, bot, chat).toSortedMap().values.joinToString(" ").take(120) + "'"
        return "chatbox=$chatText modal=${bot.interfaces.getModal()} 382=${bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)} lock=${bot.lock}"
    }

    // ---- dialogue driving -----------------------------------------------------------------------------------------------

    /** How the bot answers option dialogues: by the text on screen, like a player reading it. */
    class Policy(
        val quest: ShortQuest? = null,
        /** Answer the first puzzle prompt wrong once (the stage must stay), then right. */
        var wrongFirst: Boolean = false,
        /** Accept a travel offer to [destination] (null = unknown destination). */
        val travel: (Tile?) -> Boolean = { false },
        /** Checked before the built-in rules; null = not handled. */
        val custom: ((String, List<String>) -> Int?)? = null,
    ) {
        var puzzle = false

        fun answer(
            title: String,
            options: List<String>,
            destination: (String) -> Tile?,
        ): Int? {
            custom?.invoke(title, options)?.let { return it }
            fun index(text: String): Int? = options.indexOfFirst { it == text }.takeIf { it >= 0 }?.plus(1)
            // The Quest Helper's highlighted answers are the bot's answers (QuestDialogueHints, one source).
            val correct: String? = gg.rsmod.plugins.content.quests.foundation.QuestDialogueHints.puzzleAnswer(title, options)
            if (correct != null) {
                puzzle = true
                if (wrongFirst) {
                    wrongFirst = false
                    return options.indexOfFirst { it != correct }.takeIf { it >= 0 }?.plus(1)
                }
                return index(correct)
            }
            return when {
                title == "Quests" && quest != null -> index("${quest.name}.")
                title.startsWith("Start ") -> 1
                title == "Travel" -> {
                    val yes = options.indexOfFirst { QuestsSuite.isTravelYes(it) }
                    val no = options.indexOfFirst { it.contains("own way") }
                    if (yes >= 0 && travel(destination(options[yes]))) yes + 1 else if (no >= 0) no + 1 else null
                }
                title.startsWith("Choose a skill") -> 1
                else -> null
            }
        }
    }

    /** A travel offer's yes: a quest npc's ("Can you get me to ...?") or the quest's own after a step (owner 2026-10-09 "Take me to ..."). */
    fun isTravelYes(text: String) = text.startsWith("Can you get me to ") || text.startsWith("Send me to ") || text.startsWith("Take me to ")

    /** The tile a travel offer names: a step's place or the quest's start place. */
    fun destinationOf(
        quest: ShortQuest?,
        option: String,
    ): Tile? {
        val place = option.removePrefix("Can you get me to ").removePrefix("Send me to ").removePrefix("Take me to ").trimEnd('?', '.')
        val quests = if (quest != null) listOf(quest) else FoundationQuests.SHORT
        for (q in quests) {
            q.steps.firstOrNull { it.place == place && it.location != null }?.let { return it.location }
            if (q.startPlace == place) q.startLocation?.let { return it }
        }
        return null
    }

    class Conv {
        var opened = false
        val transcript = ArrayList<String>()
        val titles = ArrayList<String>()
        val unexpected = ArrayList<String>()
        var travels = 0
        var warnings = 0
        var stuckWarnings = 0

        fun brief(): String = transcript.takeLast(10).joinToString(" / ").take(360)
    }

    private class Pending(val cycle: Int, val kind: String, val from: Tile, val destination: Tile?)

    /**
     * Plays the conversation on the bot's screen until nothing is open for 3 ticks: continues every chat line, answers
     * every option dialogue through [policy], clicks "Enter" on the Dangerous-area warning. A travel answer or an Enter is
     * judged 2 ticks later (no click in between): the teleport animation or the move must have started and no dialogue may
     * be open. A warning still open 2 ticks after Enter is closed the way the client closes a window (CLOSE_MODAL) so the
     * play goes on; that is reported as its own FAIL.
     */
    suspend fun QueueTask.converse(
        run: Conformance.Run,
        bot: Client,
        entry: String,
        where: String,
        policy: Policy,
        maxTicks: Int = 90,
    ): Conv {
        val conv = Conv()
        var pending: Pending? = null
        var idle = 0
        var ticks = 0
        var lastClicked: Recording.Ev? = null
        var sameFor = 0
        while (ticks < maxTicks) {
            wait(1)
            ticks++
            val now = run.world.currentCycle
            val p = pending
            if (p != null) {
                idle = 0
                if (p.kind == TRAVEL && bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)) {
                    warnCheck(run, entry, where, p, shown = true)
                    pending = null
                } else if (now >= p.cycle + 2) {
                    pending = judge(run, bot, entry, where, p, conv)
                    continue
                } else {
                    continue
                }
            }
            if (bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)) {
                conv.opened = true
                idle = 0
                val open = openEvent(run, bot, DangerWarning.INTERFACE_ID, chatboxOnly = false)
                if (open != null && open === lastClicked && sameFor++ < 3) continue
                lastClicked = open
                sameFor = 0
                conv.warnings++
                conv.transcript += "382 '" + (textsOf(run, bot, DangerWarning.INTERFACE_ID, chatboxOnly = false)[DangerWarning.BODY] ?: "").take(60) + "' -> Enter"
                Bots.inject(bot, ResumePauseButtonMessage(DangerWarning.INTERFACE_ID, DangerWarning.ENTER, -1))
                pending = Pending(now, ENTER, Tile(bot.tile), null)
                continue
            }
            val chat = chatOpen(bot)
            if (chat != -1) {
                conv.opened = true
                idle = 0
                val open = openEvent(run, bot, chat)
                // The click of the previous tick may not be handled yet: never answer the same dialogue twice.
                if (open != null && open === lastClicked && sameFor++ < 3) continue
                lastClicked = open
                sameFor = 0
                val texts = textsOf(run, bot, chat)
                if (isOptions(chat)) {
                    val count = (chat - 224) / 2
                    val title = texts[1] ?: ""
                    val options = (0 until count).map { texts[it + 2] ?: "" }
                    val picked = policy.answer(title, options) { destinationOf(policy.quest, it) }
                    val n = picked ?: options.size
                    if (picked == null) conv.unexpected += "'$title' [${options.joinToString("|")}]"
                    conv.titles += title
                    conv.transcript += "'$title' [${options.joinToString("|")}] -> ${options.getOrNull(n - 1)}"
                    Bots.answerOption(bot, n)
                    val chosen = options.getOrNull(n - 1) ?: ""
                    if (title == "Travel" && isTravelYes(chosen)) {
                        conv.travels++
                        pending = Pending(now, TRAVEL, Tile(bot.tile), destinationOf(policy.quest, chosen))
                    }
                } else {
                    conv.transcript += texts.toSortedMap().values.joinToString(" ").take(200)
                    Bots.inject(bot, ResumePauseButtonMessage(chat, continueComponent(chat), -1))
                }
                continue
            }
            if (bot.lock != LockState.NONE || bot.movementQueue.hasDestination()) {
                idle = 0
                continue
            }
            idle++
            if (conv.opened && idle >= 3) break
            if (!conv.opened && ticks >= 30) break
        }
        pending?.let { judge(run, bot, entry, where, it, conv) }
        return conv
    }

    private fun warnCheck(
        run: Conformance.Run,
        entry: String,
        where: String,
        p: Pending,
        shown: Boolean,
    ) {
        val dest = p.destination ?: return
        val dangerous = AreaState.isDangerous(dest)
        run.check(
            entry, "$where: travel to ${dest.x},${dest.z},${dest.height} asks on 382 exactly when the destination is Dangerous",
            shown, "DangerWarning.confirmQuestTravel (owner 2026-09-26/27, AreaState.isDangerous=$dangerous)" to dangerous,
        )
    }

    /** Judges a travel answer / Enter click 2 ticks later; returns a new pending judgement when it had to close 382. */
    private fun judge(
        run: Conformance.Run,
        bot: Client,
        entry: String,
        where: String,
        p: Pending,
        conv: Conv,
    ): Pending? {
        val now = run.world.currentCycle
        if (p.kind == TRAVEL) warnCheck(run, entry, where, p, shown = false)
        val anim = TeleportType.MODERN.animation
        val animated = run.recording.of(bot.username, "out").any { it.cycle in p.cycle..p.cycle + 2 && it.text.startsWith("anim id=$anim ") }
        val moved = bot.tile.height != p.from.height || bot.tile.getDistance(p.from) > 10
        val chat = chatOpen(bot)
        val warn = bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)
        val ok = (animated || moved) && chat == -1 && !warn
        val detail =
            "teleport anim $anim within 2 ticks=$animated, moved=$moved (from ${p.from.x},${p.from.z},${p.from.height} now ${bot.tile.x},${bot.tile.z},${bot.tile.height}), " +
                describeScreen(run, bot)
        run.expectTrue(
            entry, "$where: ${p.kind} -> the travel starts within 2 ticks, no dialogue open", ok, detail,
            rule = "owner 2026-10-07: the quest teleport starts at once after Enter/the travel answer (MODERN teleport anim or the move within 2 ticks), no dialogue in between",
        )
        if (warn && p.kind != ENTER_CLOSED) {
            // The player's way out: the window is clicked away (CLOSE_MODAL). The paused dialogue task then resumes.
            conv.stuckWarnings++
            conv.transcript += "382 still open 2 ticks after Enter -> CLOSE_MODAL"
            Bots.inject(bot, CloseModalMessage())
            return Pending(now, ENTER_CLOSED, Tile(bot.tile), null)
        }
        return null
    }

    /** Clicks [npc]'s menu option [option] ("talk-to") as the 667 client sends it; -1 when the npc has no such option. */
    fun optionIndex(
        run: Conformance.Run,
        npcId: Int,
        option: String,
    ): Int {
        val def = run.world.definitions.getNullable(NpcDef::class.java, npcId) ?: return -1
        val i = def.options.indexOfFirst { it?.equals(option, true) == true }
        return if (i < 0) -1 else i + 1
    }

    suspend fun QueueTask.talkTo(
        run: Conformance.Run,
        bot: Client,
        npc: Npc,
        entry: String,
        where: String,
        policy: Policy,
        option: String = "talk-to",
    ): Conv {
        val op = optionIndex(run, npc.id, option)
        if (op < 0) {
            run.expectTrue(entry, "$where: npc ${npc.id} has a '$option' option", false, "cache menu ${run.world.definitions.getNullable(NpcDef::class.java, npc.id)?.options?.toList()}")
            return Conv()
        }
        Bots.npcOption(bot, npc, op)
        return converse(run, bot, entry, where, policy)
    }

    // ---- positions ------------------------------------------------------------------------------------------------------

    /** A walkable tile next to [tile] (same floor), or [tile] itself. */
    fun standNear(
        run: Conformance.Run,
        tile: Tile,
    ): Tile {
        for (r in 1..3) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(Math.abs(dx), Math.abs(dz)) != r) continue
                val t = tile.transform(dx, dz)
                if (!runCatching { run.world.collision.isClipped(t) }.getOrDefault(true)) return t
            }
        }
        return tile
    }

    /**
     * The world's [id] npc for a step at [place]: it must stand where the step sends the player - same floor, within
     * QuestHelper.NPC_RANGE - or the travel lands the player where nobody is and the helper's arrow points at an empty spot.
     */
    fun locate(
        run: Conformance.Run,
        entry: String,
        where: String,
        id: Int,
        place: Tile?,
        placeName: String?,
        near: Tile? = null,
    ): Npc? {
        val all = ArrayList<Npc>()
        run.world.npcs.forEach { if (it.id == id && it.owner == null && it.isSpawned()) all += it }
        if (all.isEmpty()) {
            run.expectTrue(entry, "$where: npc $id stands in the world", false, "no npc $id spawned anywhere")
            return null
        }
        if (place == null) {
            val from = near ?: return all.first()
            return all.minByOrNull { (if (it.tile.height != from.height) 1000 else 0) + it.tile.getDistance(from) }
        }
        // Distance on the map, any floor: an npc right above the place (the hall's first floor) is reached by its stairs,
        // and the Quest Helper points at those stairs (owner 2026-10-07).
        fun flat(t: Tile) = Tile(t.x, t.z, place.height).getDistance(place)
        val best = all.minByOrNull { flat(it.tile) * 4 + Math.abs(it.tile.height - place.height) }!!
        val ok = flat(best.tile) <= QuestHelper.NPC_RANGE
        run.expectTrue(
            entry, "$where: npc $id stands at ${placeName ?: "its place"} (<= ${QuestHelper.NPC_RANGE} tiles, any floor of it)", ok,
            "nearest at ${best.tile.x},${best.tile.z},${best.tile.height}, place ${place.x},${place.z},${place.height}, ${all.size} in world",
            rule = "the quest travel lands at the step's place; the Quest Helper points at the npc, or at the stairs when it is on another floor",
        )
        return best
    }

    /** Puts the bot beside [npc] (the player walking there) unless it is already close. */
    suspend fun QueueTask.goTo(
        run: Conformance.Run,
        bot: Client,
        npc: Npc,
        to: Tile? = null,
    ) {
        val landing = to?.let { gg.rsmod.plugins.content.quests.foundation.FoundationQuests.landing(run.world, npc.id, it) }
        // Where a player arrives: the quest travel's landing tile when it is on the npc's floor and near it - the npc click
        // then walks the real path from there (a tile beside the npc can be on the other side of a wall).
        if (landing != null && landing.height == npc.tile.height && landing.isWithinRadius(npc.tile, QuestHelper.NPC_RANGE)) {
            if (!bot.tile.isWithinRadius(landing, 3)) {
                bot.moveTo(landing)
                wait(2)
            }
            return
        }
        // Another floor (the hall's first floor): the player takes the stairs - placed on the npc's floor.
        if (bot.tile.height == npc.tile.height && bot.tile.isWithinRadius(npc.tile, 10)) return
        bot.moveTo(standNear(run, npc.tile))
        wait(2)
    }

    /**
     * Night run 2026-10-08 (played in the client): Anna stood in a bedroom with no doorway ("I can't reach that!" from every
     * side) while every check passed - the quest travel and [goTo] both put the player inside the npc's own walkable area.
     * This walks the map from the step's place (where the travel or the previous step leaves the player) and needs a tile
     * orthogonally next to the npc's post. A closed door counts as passable (a player opens it): a blocked step is crossed
     * when a wall loc with an "Open" option stands on either side of that edge.
     */
    private fun reachable(
        run: Conformance.Run,
        entry: String,
        where: String,
        bot: Client,
        npc: Npc,
        place: Tile?,
    ) {
        val location = place ?: return
        // Where the player is: still at the previous step's npc when the place is near (no travel is offered within 15
        // tiles, ShortQuest.offerTravel), else where the quest travel lands (FoundationQuests.landing).
        val from =
            if (bot.tile.height == location.height && bot.tile.isWithinRadius(location, 15)) bot.tile
            else FoundationQuests.landing(run.world, npc.id, location)
        val post = npc.spawnTile
        if (from.height != post.height || !from.isWithinRadius(post, 60)) return
        val world = run.world
        val doorCache = HashMap<Tile, Boolean>()
        fun door(t: Tile): Boolean =
            doorCache.getOrPut(t) {
                (0..3).any { type ->
                    val obj = world.getObject(t, gg.rsmod.game.model.collision.ObjectType.values().first { it.value == type }) ?: return@any false
                    val def = world.definitions.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, obj.id) ?: return@any false
                    def.options.any { it != null && it.equals("open", ignoreCase = true) }
                }
            }
        val start = from
        val seen = hashSetOf(start)
        val parent = HashMap<Tile, Tile>()
        val queue = ArrayDeque(listOf(start))
        val goals = gg.rsmod.game.model.Direction.NESW.map { post.step(it) }.toSet()
        var found = start in goals
        while (queue.isNotEmpty() && !found && seen.size < 20000) {
            val t = queue.removeFirst()
            for (dir in gg.rsmod.game.model.Direction.NESW) {
                val next = t.step(dir)
                if (next in seen || !next.isWithinRadius(post, 70)) continue
                if (!world.collision.canStep(t, dir) && !door(t) && !door(next)) continue
                seen += next
                parent[next] = t
                if (next in goals) found = true
                queue.addLast(next)
            }
        }
        if (found) {
            // Which closed doors the route needs (a player has to open them): named in the notes for the handoff.
            var t = goals.first { it in seen }
            val doors = mutableListOf<String>()
            while (parent[t] != null) {
                val p = parent[t]!!
                val dir = gg.rsmod.game.model.Direction.NESW.first { p.step(it) == t }
                if (!world.collision.canStep(p, dir)) doors += "${p.x},${p.z}->${t.x},${t.z}"
                t = p
            }
            if (doors.isNotEmpty()) run.note("$entry $where: route to the npc opens doors at ${doors.reversed()}")
        }
        run.expectTrue(
            entry, "$where: the npc's post ${post.x},${post.z},${post.height} is reachable on foot from ${from.x},${from.z} (doors opened)", found,
            "searched ${seen.size} tiles from ${start.x},${start.z}",
            rule = "night run 2026-10-08: every quest npc can be walked to from where the quest leaves the player",
        )
    }

    /** A tile orthogonally next to [foe]'s footprint can be walked to from [from] without opening anything (60-tile search). */
    private fun meleeReach(
        run: Conformance.Run,
        from: Tile,
        foe: Npc,
    ): Boolean {
        val size = maxOf(1, foe.getSize())
        val body = (0 until size).flatMap { dx -> (0 until size).map { dz -> Tile(foe.tile.x + dx, foe.tile.z + dz, foe.tile.height) } }.toSet()
        val goals = body.flatMap { t -> gg.rsmod.game.model.Direction.NESW.map { t.step(it) } }.filter { it !in body }.toSet()
        if (from in goals) return true
        val area = gg.rsmod.game.model.collision.Landing.pocket(run.world.collision, from, 3600)
        return goals.any { it in area }
    }

    /** Before a talk: no window left from the last step, out of combat (a teleport offer would wait 7 seconds otherwise). */
    private suspend fun QueueTask.prepare(bot: Client) {
        if (bot.interfaces.getModal() != -1) {
            Bots.inject(bot, CloseModalMessage())
            wait(1)
        }
        var n = 0
        while (n++ < 30 && DeadmanTimerGate.teleportDecision(bot) != DeadmanTimerGate.Teleport.INSTANT) {
            heal(bot)
            wait(1)
        }
    }

    // ---- short quests ---------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.playShort(
        run: Conformance.Run,
        quest: ShortQuest,
    ) {
        val entry = "play ${key(quest)}"
        val start = quest.startLocation ?: GE_HALL
        val bot = run.bot(standNear(run, start), "Q${code(quest)}")
        freshAccount(bot)
        gear(bot)
        wait(2)
        run.expectTrue(
            entry, "fresh choice-cohort account: quest not started", quest.stage(bot) == 0 && QuestStatus.state(bot, quest) == QuestStatus.State.NOT_STARTED,
            "stage ${quest.stage(bot)} state ${QuestStatus.state(bot, quest)}",
        )
        val nearDest: (Tile?) -> Boolean = { dest -> dest == null || !bot.tile.isWithinRadius(dest, 20) }

        // Start.
        val startNpc = locate(run, entry, "start", quest.startNpc, start, quest.startPlace) ?: run.npc(quest.startNpc, standNear(run, start), "start-npc")
        goTo(run, bot, startNpc, start)
        val c0 = run.world.currentCycle
        val conv = talkTo(run, bot, startNpc, entry, "start (${quest.startNpcName})", Policy(quest, travel = nearDest))
        run.expectTrue(
            entry, "start: ${quest.startNpcName} starts the quest (stage 1)", quest.stage(bot) == 1,
            "stage ${quest.stage(bot)}; ${conv.brief()}; unexpected ${conv.unexpected}",
        )
        if (quest.stage(bot) != 1) return
        run.expectTrue(
            entry, "start: 'You've started a new quest' message",
            run.recording.of(bot.username, "msg").any { it.cycle >= c0 && it.text.contains("started a new quest") }, "chatbox messages since the click",
        )
        run.expectTrue(entry, "start: the Quest Helper tracks the quest", QuestHelper.tracked(bot) == quest, "tracked=${QuestHelper.tracked(bot)?.name}")
        var travels = conv.travels
        var warnings = conv.warnings

        // Steps.
        var guard = 0
        while (!quest.isFinished(bot) && guard++ < quest.steps.size * 2) {
            val stage = quest.stage(bot)
            val step = quest.steps.getOrNull(stage - 1) ?: break
            val xp0 = xpOf(bot)
            val held0 = (PROMISES[quest]?.items?.keys.orEmpty() + listOf(Items.EXPERIENCE_LAMP, Items.COMBAT_LAMP_15390)).associateWith { held(bot, it) }
            val qp0 = bot.getVarp(Varps.QUEST_POINTS)
            val done =
                if (step.fight != null) {
                    fightStep(run, entry, bot, quest, stage).also { travels += it.second; warnings += it.third }.first
                } else {
                    talkStep(run, entry, bot, quest, stage).also { travels += it.second; warnings += it.third }.first
                }
            if (!done) return
            afterStep(run, entry, bot, quest, stage)
            if (quest.isFinished(bot)) completion(run, entry, bot, quest, xp0, held0, qp0)
        }
        run.expectTrue(entry, "the quest is completed by playing it", quest.isFinished(bot), "stage ${quest.stage(bot)} of ${quest.completedValue}")
        run.expectTrue(
            entry, "travel exercised: at least one npc travel offer accepted", travels >= 1, "$travels travel answers, $warnings Dangerous-area warnings answered",
            rule = "owner 2026-10-07: the travel offer is exercised at least once per quest",
        )
        if (quest.isFinished(bot)) paidOnce(run, entry, bot, quest)
        if (quest == TempleAtSenntisten && quest.isFinished(bot)) hymnal(run, entry, bot)
    }

    /** One talk step; returns (stage advanced, travels, warnings). */
    private suspend fun QueueTask.talkStep(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ): Triple<Boolean, Int, Int> {
        val step = quest.steps[stage - 1]
        val name = FoundationQuests.NPC_NAMES[step.npc] ?: run.world.definitions.getNullable(NpcDef::class.java, step.npc)?.name ?: "npc ${step.npc}"
        val where = "step $stage ($name)"
        prepare(bot)
        val npc = locate(run, entry, where, step.npc, step.location, step.place) ?: run.npc(step.npc, standNear(run, step.location ?: bot.tile), "step-npc")
        reachable(run, entry, where, bot, npc, step.location)
        goTo(run, bot, npc, step.location)
        val policy = Policy(quest, wrongFirst = true, travel = { dest -> dest != null && !bot.tile.isWithinRadius(dest, 20) })
        var conv = talkTo(run, bot, npc, entry, where, policy)
        var travels = conv.travels
        var warnings = conv.warnings
        if (policy.puzzle && quest.stage(bot) == stage) {
            run.expectTrue(
                entry, "$where: a wrong answer keeps the stage, no lock, no dialogue left", quest.stage(bot) == stage && bot.lock == LockState.NONE && chatOpen(bot) == -1,
                "stage ${quest.stage(bot)}; ${describeScreen(run, bot)}",
            )
            prepare(bot)
            goTo(run, bot, npc, step.location)
            conv = talkTo(run, bot, npc, entry, where, policy)
            travels += conv.travels
            warnings += conv.warnings
        }
        val next = quest.stage(bot)
        val ok = next == stage + 1 || (stage == quest.steps.size && quest.isFinished(bot))
        run.expectTrue(
            entry, "$where: talking completes the step", ok,
            "stage $stage -> $next; ${conv.brief()}; unexpected ${conv.unexpected}; ${describeScreen(run, bot)}",
        )
        if (conv.unexpected.isNotEmpty()) {
            run.expectTrue(entry, "$where: every option dialogue belongs to the quest", false, conv.unexpected.joinToString(" ").take(250))
        }
        return Triple(ok, travels, warnings)
    }

    /** One fight step: the step npc's travel offer, the foe on arrival, the kill. Returns (advanced, travels, warnings). */
    private suspend fun QueueTask.fightStep(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ): Triple<Boolean, Int, Int> {
        val step = quest.steps[stage - 1]
        val fight = step.fight!!
        val where = "step $stage (${fight.name})"
        var travels = 0
        var warnings = 0
        if (QuestFights.active(bot) == null && !(bot.tile.height == fight.tile.height && bot.tile.isWithinRadius(fight.tile, QuestFights.REACH))) {
            prepare(bot)
            val npcName = FoundationQuests.NPC_NAMES[step.npc] ?: "npc ${step.npc}"
            // The step npc where the quest last sent the player to it (Ned in Draynor, not the Ned on the Port Sarim ship).
            val home = quest.steps.take(stage).lastOrNull { it.npc == step.npc && it.fight == null }
            val homeTile = home?.location ?: if (step.npc == quest.startNpc) quest.startLocation else null
            val npc =
                locate(run, entry, "$where via $npcName", step.npc, homeTile, home?.place ?: quest.startPlace, near = bot.tile)
                    ?: run.npc(step.npc, standNear(run, bot.tile), "step-npc")
            goTo(run, bot, npc, homeTile)
            val conv = talkTo(run, bot, npc, entry, where, Policy(quest, travel = { true }))
            travels += conv.travels
            warnings += conv.warnings
            run.expectTrue(entry, "$where: $npcName offers the travel to ${step.place}", conv.titles.contains("Travel") || conv.warnings > 0, conv.brief())
            var n = 0
            while (n++ < 15 && !(bot.tile.height == fight.tile.height && bot.tile.isWithinRadius(fight.tile, QuestFights.REACH))) wait(1)
            run.expectTrue(
                entry, "$where: the travel lands within ${QuestFights.REACH} tiles of the fight", bot.tile.height == fight.tile.height && bot.tile.isWithinRadius(fight.tile, QuestFights.REACH),
                "bot at ${bot.tile.x},${bot.tile.z},${bot.tile.height}, fight at ${fight.tile.x},${fight.tile.z},${fight.tile.height}; ${describeScreen(run, bot)}",
            )
        }
        var n = 0
        while (n++ < 12 && QuestFights.active(bot) == null && quest.stage(bot) == stage) wait(1)
        val first = QuestFights.active(bot)
        run.expectTrue(
            entry, "$where: the foe appears on arrival (QuestHelper tick / step talk)", first != null && first.id == fight.npc,
            "active=${first?.id} at ${first?.tile}; bot at ${bot.tile}",
        )
        if (first == null) return Triple(false, travels, warnings)
        // Quests run 2026-10-10: the elf warrior appeared behind Lletya's counter and shot a melee player who could only answer
        // "I can't reach that!". A melee player must be able to walk up to every foe (no door, no counter in between).
        run.expectTrue(
            entry, "$where: a melee player can walk up to the foe", meleeReach(run, bot.tile, first),
            "foe ${first.id} at ${first.tile}; bot at ${bot.tile}",
            rule = "quests run 2026-10-10: QuestFights.spawnTile places the foe within a short walk of the player",
        )
        val attack = optionIndex(run, fight.npc, "attack")
        run.expectTrue(entry, "$where: the foe has an Attack option", attack > 0, "cache menu ${run.world.definitions.getNullable(NpcDef::class.java, fight.npc)?.options?.toList()}")
        val seen = LinkedHashSet<Int>()
        val lowered = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Npc, Boolean>())
        var ticks = 0
        var lastClick = -10
        while (ticks < 600 && quest.stage(bot) == stage) {
            wait(1)
            ticks++
            heal(bot)
            if (bot.interfaces.isVisible(DangerWarning.INTERFACE_ID) || chatOpen(bot) != -1) {
                val conv = converse(run, bot, entry, "$where (after the kill)", Policy(quest, travel = { true }), maxTicks = 20)
                warnings += conv.warnings
                continue
            }
            val foe = QuestFights.active(bot) ?: continue
            seen += foe.id
            if (lowered.add(foe)) foe.setCurrentLifepoints(minOf(foe.getCurrentLifepoints(), FOE_LIFE))
            if (bot.getCombatTarget() !== foe && ticks - lastClick >= 3) {
                val op = optionIndex(run, foe.id, "attack")
                if (op > 0) Bots.npcOption(bot, foe, op)
                lastClick = ticks
            }
        }
        val expectedFoes = listOf(fight.npc) + fight.chain.map { it.first }
        run.check(entry, "$where: foes fought in order", seen.toList(), "QuestFight npc + chain" to expectedFoes)
        val ok = quest.stage(bot) == stage + 1 || quest.isFinished(bot)
        run.expectTrue(
            entry, "$where: killing the foe completes the step", ok,
            "stage $stage -> ${quest.stage(bot)} after $ticks ticks; foes $seen; bot hp ${bot.getCurrentLifepoints()}; ${describeScreen(run, bot)}",
        )
        // The return trip (Elvarg) and anything else the kill queued.
        wait(2)
        if (bot.interfaces.isVisible(DangerWarning.INTERFACE_ID) || chatOpen(bot) != -1) {
            val conv = converse(run, bot, entry, "$where (after the kill)", Policy(quest, travel = { true }), maxTicks = 30)
            warnings += conv.warnings
        }
        if (ok) {
            for ((item, amount) in step.gives) {
                run.expectTrue(entry, "$where: the step hands out item $item x$amount", held(bot, item) >= amount, "held ${held(bot, item)}")
            }
        }
        return Triple(ok, travels, warnings)
    }

    /** After a step: stage, no lock, no window left, journal, Quest Helper arrow and panel. */
    private suspend fun QueueTask.afterStep(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ) {
        wait(QuestHelper.INTERVAL + 1)
        // The quest's own trip to the next step (owner 2026-10-09) waits until the player may teleport (Deadman: 7 seconds after a
        // hit), so it can open after the step's conversation ended: answer it like a player would.
        var n = 0
        while (n++ < 14 && chatOpen(bot) == -1 && !bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)) wait(1)
        if (chatOpen(bot) != -1 || bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)) {
            converse(run, bot, entry, "step $stage (trip on)", Policy(quest, travel = { true }), maxTicks = 30)
        }
        val now = quest.stage(bot)
        val finished = quest.isFinished(bot)
        val where = "step $stage"
        run.check(entry, "$where: stage after the step", now, "QuestStep order" to if (stage == quest.steps.size) quest.completedValue else stage + 1)
        run.expectTrue(entry, "$where: no lock left", bot.lock == LockState.NONE, "lock ${bot.lock}")
        val modal = bot.interfaces.getModal()
        run.expectTrue(
            entry, "$where: no dialogue or window left open", chatOpen(bot) == -1 && !bot.interfaces.isVisible(DangerWarning.INTERFACE_ID) && (modal == -1 || (finished && modal == QUEST_COMPLETE)),
            describeScreen(run, bot),
        )
        if (finished) {
            run.expectTrue(entry, "$where: the quest-complete scroll (277) shows", bot.interfaces.isVisible(QUEST_COMPLETE), describeScreen(run, bot))
            Bots.inject(bot, CloseModalMessage())
            wait(1)
            run.expectTrue(entry, "$where: the scroll closes with the client's close", !bot.interfaces.isVisible(QUEST_COMPLETE), describeScreen(run, bot))
        }
        journal(run, entry, bot, quest, stage)
        if (!finished) helper(run, entry, bot, quest, now)
    }

    /** The journal page the quest tab opens (190:18 -> 275): the step struck through, the next step's lines open. */
    private suspend fun QueueTask.journal(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ) {
        val step = quest.steps[stage - 1]
        val expected = step.done.map { "<str>$it</str>" } + (quest.steps.getOrNull(stage)?.journal ?: listOf(ShortQuest.questCompleteLine))
        val where = "step $stage: journal"
        if (!bot.interfaces.isVisible(QUEST_TAB)) {
            val lines = quest.getObjective(bot, quest.stage(bot)).objectives
            val missing = expected.filter { it !in lines }
            run.expectTrue(entry, "$where shows the step done and the next one (Quest.getObjective; quest tab 190 not open on the bot)", missing.isEmpty(), "missing $missing")
            return
        }
        val c0 = run.world.currentCycle
        Bots.button(bot, QUEST_TAB, QUEST_LIST, option = 1, slot = quest.slot)
        wait(1)
        val lines =
            run.recording.of(bot.username, "msg").filter { it.cycle >= c0 }.mapNotNull { ev ->
                SET_TEXT.find(ev.text)?.takeIf { it.groupValues[1].toInt() == JOURNAL && it.groupValues[2].toInt() >= 17 }?.groupValues?.get(3)
            }
        val opened = bot.interfaces.isVisible(JOURNAL)
        val missing = expected.filter { it !in lines }
        run.expectTrue(entry, "$where (quest tab 190 -> 275) shows the step done and the next one", opened && missing.isEmpty(), "275 open=$opened, missing $missing")
        Bots.inject(bot, CloseModalMessage())
        wait(1)
        run.expectTrue(entry, "$where closes with the client's close", !bot.interfaces.isVisible(JOURNAL), describeScreen(run, bot))
    }

    /** The Quest Helper points at the open step: its npc/foe, or its place; the panel says "Step n of N". */
    private fun helper(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ) {
        val step = quest.steps.getOrNull(stage - 1) ?: return
        val where = "step $stage: Quest Helper"
        run.expectTrue(entry, "$where tracks the quest", QuestHelper.tracked(bot) == quest, "tracked=${QuestHelper.tracked(bot)?.name}")
        val ids = step.fight?.let { f -> listOf(f.npc) + f.chain.map { it.first } } ?: listOf(step.npc)
        val tile = step.fight?.tile ?: step.location
        val msgs = run.recording.of(bot.username, "msg")
        val last = msgs.lastOrNull { it.text.startsWith("HintArrowMessage(slot=${QuestHelper.SLOT},") || it.text.startsWith("HintArrowTileMessage(slot=${QuestHelper.SLOT},") }
        val text = last?.text ?: "none"
        val ok =
            when {
                last == null -> false
                text.startsWith("HintArrowTileMessage") -> {
                    val m = Regex("""x=(\d+), z=(\d+)""").find(text)
                    val onPlace = tile != null && m != null && m.groupValues[1].toInt() == tile.x && m.groupValues[2].toInt() == tile.z
                    // Night run 2026-10-08: when the step is on another floor the arrow shows the way there - a staircase or
                    // ladder on the player's floor (QuestHelper.climbLoc, or the one that reaches the npc offering the travel).
                    val onClimb =
                        tile != null && m != null && tile.height != bot.tile.height && run.world.chunks.get(Tile(m.groupValues[1].toInt(), m.groupValues[2].toInt(), bot.tile.height))
                            ?.getEntities<gg.rsmod.game.model.entity.GameObject>(Tile(m.groupValues[1].toInt(), m.groupValues[2].toInt(), bot.tile.height), gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                            ?.any { o -> run.world.definitions.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, o.id)?.options?.any { it != null && it.lowercase().startsWith("climb") } == true } == true
                    onPlace || onClimb
                }
                else -> {
                    val type = Regex("""type=(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
                    val target = Regex("""target=(\d+)""").find(text)?.groupValues?.get(1)?.toInt()
                    when (type) {
                        1 -> target != null && run.world.npcs[target]?.id?.let { it in ids } == true
                        0 -> tile != null && tile.height != bot.tile.height
                        else -> false
                    }
                }
            }
        run.expectTrue(entry, "$where arrow on the open step (npc ${ids.first()} or ${tile?.x},${tile?.z})", ok, "last arrow: $text", rule = "QuestHelper: npc/foe of the step when near, else the step's place; on another floor a staircase/ladder on the player's floor")
        val stepText =
            msgs.lastOrNull { SET_TEXT.find(it.text)?.let { m -> m.groupValues[1].toInt() == HelperPanel.INTERFACE_ID && m.groupValues[2].toInt() == HelperPanel.STEP } == true }
                ?.let { SET_TEXT.find(it.text)!!.groupValues[3] }
        val mode = bot.interfaces.displayMode
        val occupant = bot.interfaces.getInterfaceAt(getDisplayComponentId(mode), getChildId(InterfaceDestination.MAIN_SCREEN_OVERLAY, mode))
        // The header fits the quest name beside the label (QuestHelper.Panel.header): "Step n of N", or "n/N" beside a long name.
        val header = gg.rsmod.plugins.content.quests.foundation.QuestHelper.Panel.header(run.world, quest.name.substringBefore(" - "), stage to quest.steps.size)
        run.check(entry, "$where panel (1166) shows the step", stepText, "QuestHelper.Panel.header" to header.second)
        if (stepText == null) run.note("$entry: overlay slot holds interface $occupant, so the helper panel stepped aside")
        sidePanel(run, entry, bot, quest, stage)
    }

    /**
     * The 78 Plugins sidebar's Quest Helper panel (owner 2026-10-07 night run, QuestHelperPanel.kt -> client RspsQuestHelper):
     * the HUD payloads the bot was sent are decoded exactly as the client's RspsQuestHelper.handle does (the last
     * qhb..qhe overview and the last qhq rows) and compared with the server's quest state at this step.
     */
    private fun sidePanel(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        stage: Int,
    ) {
        val where = "step $stage: Quest Helper side panel"
        val payloads =
            run.recording.of(bot.username, "msg").mapNotNull { e ->
                val at = e.text.indexOf("message=__RSPS_HUD__:")
                if (at < 0) null else e.text.substring(at + "message=__RSPS_HUD__:".length).removeSuffix(")")
            }
        val begin = payloads.indexOfLast { it.startsWith("qhb|") }
        val end = payloads.indexOfLast { it == "qhe" }
        if (begin < 0 || end < begin) {
            run.expectTrue(entry, "$where received an overview (qhb..qhe)", false, "payloads ${payloads.filter { it.startsWith("qh") }.takeLast(4)}")
            return
        }
        val head = payloads[begin].split("|")
        val steps = mutableMapOf<Int, String>()
        for (p in payloads.subList(begin + 1, end)) {
            val f = p.split("|", limit = 4)
            if (f.size == 4 && f[0] == "qhs" && f[1] == "step") steps[f[2].toInt()] = (steps[f[2].toInt()] ?: "") + f[3]
        }
        val shown = "slot ${head[1]} state ${head[2]} stage ${head[3]} tracked ${head[4]} steps ${head[5]}"
        run.expectTrue(
            entry, "$where shows this quest at this step, tracked", head[1].toInt() == quest.slot && head[3].toInt() == stage && head[4] == "1" && head[5].toInt() == quest.steps.size,
            "$shown (server: slot ${quest.slot} stage $stage of ${quest.steps.size})",
        )
        val journal = quest.steps[stage - 1].journal.joinToString(" ").replace(Regex("<[^>]+>"), "").replace('|', '/').trim()
        run.expectTrue(entry, "$where current step text = the journal's open step", steps[stage] == journal, "panel '${steps[stage]?.take(80)}' journal '${journal.take(80)}'")
        run.expectTrue(entry, "$where lists every step", steps.keys == (1..quest.steps.size).toSet(), "steps ${steps.keys}")
        val row = payloads.lastOrNull { it.startsWith("qhq|${quest.slot}|") }?.split("|")
        run.expectTrue(entry, "$where quest list: in progress and tracked", row != null && row[2] == "1" && row[3] == "1", "row $row")
        // RuneLite Quest Helper hints (owner 2026-10-08, client RspsQuestHelperHints): the world-map icon / minimap arrow /
        // npc outline target is this step's npc (or foe) and place, and the dialogue highlights are this step's.
        val step = quest.steps[stage - 1]
        val wantNpc = step.fight?.npc ?: step.npc
        val target = payloads.lastOrNull { it == "qht" || it.startsWith("qht|") }?.split("|")
        val npcOk = target != null && target.size == 5 && target[4].toInt() == wantNpc
        val near = target != null && target.size == 5 && (step.fight?.tile ?: step.location)?.let { Tile(target[1].toInt(), target[2].toInt(), it.height).isWithinRadius(it, 40) } ?: true // any floor of the place (GE hall npcs stand on its first floor)
        run.expectTrue(entry, "step $stage: Quest Helper map/minimap target is the step's npc, at its place", npcOk && near, "qht $target (npc $wantNpc at ${step.fight?.tile ?: step.location})")
        // Decoded as the client does: the last qhd starts the list, the qhda messages after it continue it.
        val start = payloads.indexOfLast { it == "qhd" || it.startsWith("qhd|") }
        val hints =
            if (start < 0) {
                null
            } else {
                payloads.subList(start, payloads.size).takeWhile { it == payloads[start] || it.startsWith("qhda|") || !it.startsWith("qhd") }
                    .filter { it.startsWith("qhd|") || it.startsWith("qhda|") }.flatMap { it.split("|").drop(1) }.joinToString("|")
            }
        val want = gg.rsmod.plugins.content.quests.foundation.QuestDialogueHints.hints(quest, stage).joinToString("|") { "${it.first}~${it.second}" }
        run.expectTrue(entry, "step $stage: Quest Helper dialogue highlights are this step's", hints == want, "${hints?.take(120)}")
    }

    /** The reward of the finished quest, measured across its last step. */
    private fun completion(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        xp0: Map<Int, Double>,
        held0: Map<Int, Int>,
        qp0: Int,
    ) {
        val promise = PROMISES[quest] ?: return
        val xp1 = xpOf(bot)
        rewardChecks(run, entry, bot, quest, promise, xp0, xp1, held0, qp0)
    }

    private fun rewardChecks(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
        promise: Promise,
        xp0: Map<Int, Double>,
        xp1: Map<Int, Double>,
        held0: Map<Int, Int>,
        qp0: Int,
    ) {
        run.expectTrue(entry, "reward: quest status COMPLETED", QuestStatus.state(bot, quest) == QuestStatus.State.COMPLETED, "state ${QuestStatus.state(bot, quest)} stage ${quest.stage(bot)}")
        val listEntry = QuestList.ENTRIES.firstOrNull { it.slot == quest.slot }
        run.check(entry, "reward: quest list shows it complete (clientscript 2193 value)", listEntry?.progress?.read?.invoke(bot), "clientscript 2193 complete" to QuestStatus.COMPLETE)
        run.check(entry, "reward: quest points gained (varp 101)", bot.getVarp(Varps.QUEST_POINTS) - qp0, QP_SOURCE to promise.points)
        run.expectTrue(entry, "reward: paid flag set", FoundationRewards.isRewarded(bot, quest), "rewarded=${FoundationRewards.isRewarded(bot, quest)}")
        for (skill in 0 until bot.skills.maxSkills) {
            val gained = Math.round((xp1[skill] ?: 0.0) - (xp0[skill] ?: 0.0)).toInt()
            val expected = promise.xp[skill] ?: 0
            if (expected != 0 || gained != 0) run.check(entry, "reward: experience in skill $skill", gained, PROMISE_SOURCE to expected)
        }
        for ((item, amount) in promise.items) run.check(entry, "reward: item $item", held(bot, item) - (held0[item] ?: 0), PROMISE_SOURCE to amount)
        run.check(entry, "reward: experience lamps (any skill)", held(bot, Items.EXPERIENCE_LAMP) - (held0[Items.EXPERIENCE_LAMP] ?: 0), PROMISE_SOURCE to promise.xpLamps)
        run.check(entry, "reward: combat lamps", held(bot, Items.COMBAT_LAMP_15390) - (held0[Items.COMBAT_LAMP_15390] ?: 0), PROMISE_SOURCE to promise.combatLamps)
        run.check(entry, "reward: lamp records waiting", FoundationRewards.pendingLamps(bot).size, PROMISE_SOURCE to promise.xpLamps + promise.combatLamps)
        run.expectTrue(entry, "reward: unlock (${promise.unlockText})", promise.unlock(bot) && QuestChoices.unlockHeld(bot, quest), "unlock=${promise.unlock(bot)} unlockHeld=${QuestChoices.unlockHeld(bot, quest)}")
    }

    /** Paid once: a second talk to the last npc and a Choose-your-path click on the finished quest pay nothing. */
    private suspend fun QueueTask.paidOnce(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        quest: ShortQuest,
    ) {
        prepare(bot)
        val xp0 = xpOf(bot)
        val qp0 = bot.getVarp(Varps.QUEST_POINTS)
        val lamps0 = held(bot, Items.EXPERIENCE_LAMP) + held(bot, Items.COMBAT_LAMP_15390)
        val last = quest.steps.last()
        val npc = locate(run, entry, "after completion", last.npc, last.location, last.place)
        if (npc != null) {
            goTo(run, bot, npc, last.location)
            talkTo(run, bot, npc, entry, "after completion", Policy(quest, custom = { _, options -> options.size }))
        }
        // The permanent choice of the finished quest: its card is finished, so a click claims nothing.
        val row = QuestGuideUi.ROWS.indexOfFirst { quest in it.quests }
        val card = QuestGuideUi.ROWS.getOrNull(row)?.quests?.indexOf(quest) ?: -1
        val guide = guideNpc(run, entry)
        if (guide != null && row >= 0 && card >= 0) {
            bot.moveTo(standNear(run, guide.tile))
            wait(2)
            if (openGuide(run, entry, bot, guide)) {
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.cardBase(row, card))
                wait(1)
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.rowBase(row) + GuideUi.ROW_CONFIRM)
                wait(1)
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.YES)
                wait(2)
                Bots.inject(bot, CloseModalMessage())
                wait(1)
            }
        }
        val xp1 = xpOf(bot)
        val changed = (0 until bot.skills.maxSkills).filter { Math.abs((xp1[it] ?: 0.0) - (xp0[it] ?: 0.0)) > 0.5 }
        run.expectTrue(
            entry, "paid once: a second talk and a Choose-your-path click pay nothing",
            changed.isEmpty() && bot.getVarp(Varps.QUEST_POINTS) == qp0 && held(bot, Items.EXPERIENCE_LAMP) + held(bot, Items.COMBAT_LAMP_15390) == lamps0 &&
                ChoiceGroup.values().none { QuestChoices.chosen(bot, it) == quest },
            "xp changed in $changed, qp $qp0 -> ${bot.getVarp(Varps.QUEST_POINTS)}, lamps $lamps0 -> ${held(bot, Items.EXPERIENCE_LAMP) + held(bot, Items.COMBAT_LAMP_15390)}",
            rule = "FoundationRewards.complete is idempotent per account",
        )
    }

    /** The Ancient hymnal: "Read" turns the prayer book to the curses and (owner 2026-10-07) the hymnal is gone afterwards. */
    private suspend fun QueueTask.hymnal(
        run: Conformance.Run,
        entry: String,
        bot: Client,
    ) {
        prepare(bot)
        val slot = bot.inventory.getItemIndex(Items.ANCIENT_HYMNAL, false)
        run.expectTrue(entry, "hymnal: the Ancient hymnal is in the inventory", slot >= 0, "slot $slot")
        if (slot < 0) return
        val read = Bots.itemOption(bot, slot, "Read")
        run.expectTrue(entry, "hymnal: it has a Read option", read, "inventory menu of ${Items.ANCIENT_HYMNAL}")
        if (!read) return
        converse(run, bot, entry, "hymnal", Policy())
        run.check(entry, "hymnal: prayer book after reading", AncientCurses.getBook(bot).name, "grandexchange_unlocks hymnal Read (owner 2026-09-26)" to AncientCurses.PrayerBook.ANCIENT.name)
        run.check(entry, "hymnal: the hymnal is gone after reading", held(bot, Items.ANCIENT_HYMNAL), "owner 2026-10-07" to 0)
    }

    // ---- Quest Guide -----------------------------------------------------------------------------------------------------

    fun guideNpc(
        run: Conformance.Run,
        entry: String,
    ): Npc? {
        val config = NewPlayerConfig.current
        var guide: Npc? = null
        run.world.npcs.forEach { if (guide == null && it.id == config.choiceNpc && it.spawnTile == config.choiceNpcTile) guide = it }
        run.expectTrue(entry, "the Quest Guide stands on its new_player.yml tile", guide != null, "npc ${config.choiceNpc} at ${config.choiceNpcTile}")
        return guide
    }

    /** Opens "Choose your path": the Quest Guide's "Quest choices" option (or Talk-to -> "... my quest choices."). */
    private suspend fun QueueTask.openGuide(
        run: Conformance.Run,
        entry: String,
        bot: Client,
        guide: Npc,
    ): Boolean {
        val op = optionIndex(run, guide.id, "quest choices")
        if (op > 0) {
            Bots.npcOption(bot, guide, op)
            var n = 0
            while (n++ < 20 && !bot.interfaces.isVisible(QuestGuideUi.INTERFACE_ID)) wait(1)
        } else {
            run.expectTrue(entry, "the Quest Guide has a 'Quest choices' menu option", false, "cache menu ${run.world.definitions.getNullable(NpcDef::class.java, guide.id)?.options?.toList()}")
            talkTo(run, bot, guide, entry, "Quest Guide", Policy(custom = { title, options -> if (title == "Quest Tutor") options.indexOfFirst { it.endsWith("my quest choices.") }.takeIf { it >= 0 }?.plus(1) else null }))
        }
        val open = bot.interfaces.isVisible(QuestGuideUi.INTERFACE_ID)
        run.expectTrue(entry, "Choose your path (${QuestGuideUi.INTERFACE_ID}) opens", open, describeScreen(run, bot))
        return open
    }

    private suspend fun QueueTask.choose(
        run: Conformance.Run,
        row: Int,
        card: Int,
        group: ChoiceGroup,
        quest: ShortQuest,
    ) {
        val entry = "choose ${key(quest)}"
        val guide = guideNpc(run, entry) ?: return
        val bot = run.bot(standNear(run, guide.tile), "C${code(quest)}")
        freshAccount(bot)
        wait(2)
        if (!openGuide(run, entry, bot, guide)) return
        val xp0 = xpOf(bot)
        val held0 = (PROMISES[quest]?.items?.keys.orEmpty() + listOf(Items.EXPERIENCE_LAMP, Items.COMBAT_LAMP_15390)).associateWith { held(bot, it) }
        val qp0 = bot.getVarp(Varps.QUEST_POINTS)
        Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.cardBase(row, card))
        wait(1)
        Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.rowBase(row) + GuideUi.ROW_CONFIRM)
        wait(1)
        run.expectTrue(entry, "card + Confirm alone complete nothing (the Yes/No question first)", !quest.isFinished(bot) && QuestChoices.chosen(bot, group) == null, "stage ${quest.stage(bot)}")
        Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.YES)
        wait(2)
        run.expectTrue(entry, "Yes completes the quest", quest.isFinished(bot), "stage ${quest.stage(bot)} of ${quest.completedValue}")
        run.check(entry, "the row's choice is recorded", QuestChoices.chosen(bot, group)?.name, "QuestChoices.choose" to quest.name)
        run.expectTrue(entry, "the quest-complete scroll (277) replaces the window", bot.interfaces.isVisible(QUEST_COMPLETE) && !bot.interfaces.isVisible(QuestGuideUi.INTERFACE_ID), describeScreen(run, bot))
        PROMISES[quest]?.let { rewardChecks(run, entry, bot, quest, it, xp0, xpOf(bot), held0, qp0) }
        Bots.inject(bot, CloseModalMessage())
        wait(1)
        // Once: the other cards of the row are locked, a second Yes claims nothing.
        val xp1 = xpOf(bot)
        val qp1 = bot.getVarp(Varps.QUEST_POINTS)
        if (openGuide(run, entry, bot, guide)) {
            group.quests.indices.filter { it != card }.forEach { other ->
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.cardBase(row, other))
                wait(1)
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.rowBase(row) + GuideUi.ROW_CONFIRM)
                wait(1)
                Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.YES)
                wait(1)
            }
            Bots.button(bot, QuestGuideUi.INTERFACE_ID, GuideUi.CLOSE)
            wait(1)
            run.expectTrue(entry, "the window's close button closes it", !bot.interfaces.isVisible(QuestGuideUi.INTERFACE_ID), describeScreen(run, bot))
        }
        val others = group.quests.filter { it != quest }
        run.expectTrue(
            entry, "the row is locked: the other quests stay unfinished and unpaid", others.none { it.isFinished(bot) || FoundationRewards.isRewarded(bot, it) },
            others.joinToString { "${it.name}=${it.stage(bot)}" },
        )
        val changed = (0 until bot.skills.maxSkills).filter { Math.abs((xpOf(bot)[it] ?: 0.0) - (xp1[it] ?: 0.0)) > 0.5 }
        run.expectTrue(entry, "a second claim pays nothing", changed.isEmpty() && bot.getVarp(Varps.QUEST_POINTS) == qp1, "xp changed in $changed, qp $qp1 -> ${bot.getVarp(Varps.QUEST_POINTS)}")
        run.expectTrue(entry, "the other rows stay open", ChoiceGroup.values().filter { it != group }.all { QuestChoices.chosen(bot, it) == null }, "chosen ${ChoiceGroup.values().map { QuestChoices.chosen(bot, it)?.name }}")
    }

    /** The Quest Tutor's directions: "Where do the quests start?" -> King's Ransom -> "Send me to ..." (382 + teleport). */
    /**
     * The new-player introduction (owner 2026-10-07): a fresh account watches the Quest Tutor's camera tour with real
     * input (Click here to continue, the "Next / Skip" choice after every stop). Measured every tick: the player is locked
     * and invisible (nothing can attack an invisible pawn), the Dangerous-area warning (382) never opens; every stop is
     * reached (the player stands at the stop's tile, in the house for the house stop); at the end the player stands in
     * front of the Tutor, unlocked and visible, "Choose your path" is open, the intro is marked seen, the Quest Helper is
     * left to the player, only the Deadman starter pack was added and no experience changed (quests run 2026-10-10: the
     * guard scene now comes before stop 0). [skipAfter]: "Skip" after that stop.
     * [logoutAt]: the client closes (Player.requestLogout) during that stop - the player must end in the hall, intro seen.
     */
    private suspend fun QueueTask.introTour(
        run: Conformance.Run,
        entry: String,
        skipAfter: Int?,
        logoutAt: Int?,
    ) {
        val guide = guideNpc(run, entry) ?: return
        val bot = run.bot(standNear(run, guide.tile), "QINTRO")
        freshAccount(bot)
        bot.attr.remove(TutorIntro.SEEN)
        wait(2)
        val itemsBefore = (0 until bot.inventory.capacity).mapNotNull { bot.inventory[it]?.let { i -> "${i.id}x${i.amount}" } }.sorted()
        val xpBefore = xpOf(bot)
        TutorIntro.start(bot)
        val scenes = TutorIntro.SCENES
        val reached = BooleanArray(scenes.size)
        // The guard scene comes first (owner 2026-10-08) and its "Next / Skip" choice leads to stop 0: -1 is the guard scene.
        var scene = -1
        var hiddenEveryTick = true
        var notHidden = ""
        var warning = false
        var loggedOut = false
        // After the last choice (or Skip) the player is back in front of the Tutor: the closing fade plays with the player
        // already unlocked and visible in the hall (restore runs behind the black screen).
        var ending = false
        var ticks = 0
        while (ticks++ < 1500) {
            wait(1)
            val active = TutorIntro.isActive(bot)
            if (!active) break
            if (!ending && (bot.lock != gg.rsmod.game.model.LockState.FULL_WITH_LOGOUT || !bot.invisible)) {
                if (hiddenEveryTick) notHidden = "t$ticks scene $scene lock=${bot.lock} invisible=${bot.invisible}"
                hiddenEveryTick = false
            }
            if (bot.interfaces.isVisible(DangerWarning.INTERFACE_ID)) warning = true
            val s = scenes.getOrNull(scene)
            if (s != null) {
                val there = if (s.house) gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(bot.tile) else bot.tile.isWithinRadius(s.stand, 1)
                if (there) reached[scene] = true
            }
            if (logoutAt != null && scene == logoutAt && reached[scene] && !loggedOut) {
                loggedOut = true
                bot.requestLogout()
                continue
            }
            val chat = chatOpen(bot)
            if (chat == -1) continue
            if (isOptions(chat)) {
                val skip = skipAfter != null && scene >= skipAfter && scene >= 0
                Bots.answerOption(bot, if (skip) 2 else 1)
                if (skip || scene == scenes.size - 1) ending = true
                scene++
            } else {
                Bots.inject(bot, ResumePauseButtonMessage(chat, continueComponent(chat), -1))
            }
            wait(1)
        }
        // The closing lines and the window come after the last scene's choice.
        var n = 0
        while (n++ < 40 && TutorIntro.isActive(bot)) {
            wait(1)
            val chat = chatOpen(bot)
            if (chat != -1 && !isOptions(chat)) Bots.inject(bot, ResumePauseButtonMessage(chat, continueComponent(chat), -1))
        }
        wait(2)
        val last = when {
            logoutAt != null -> logoutAt
            skipAfter != null -> skipAfter
            else -> scenes.size - 1
        }
        run.expectTrue(entry, "the tour ended (finished, skipped or interrupted)", !TutorIntro.isActive(bot), "after $ticks ticks, scene $scene")
        run.expectTrue(entry, "locked and invisible (cannot be attacked) every tick of every stop", hiddenEveryTick, notHidden)
        run.expectTrue(entry, "the Dangerous-area warning (382) never opened during the tour", !warning, "")
        val missing = (0..last).filter { !reached[it] }.map { scenes[it].title }
        run.expectTrue(entry, "every stop up to '${scenes[last].title}' was reached", missing.isEmpty(), "missing $missing")
        run.expectTrue(entry, "ends in front of the Quest Tutor", bot.tile == TutorIntro.endTile(), "bot at ${bot.tile.x},${bot.tile.z},${bot.tile.height}")
        run.expectTrue(entry, "unlocked and visible again", bot.lock == gg.rsmod.game.model.LockState.NONE && !bot.invisible, "lock=${bot.lock} invisible=${bot.invisible}")
        run.expectTrue(entry, "the intro is marked seen (saved attribute)", bot.attr[TutorIntro.SEEN] == true, "${bot.attr[TutorIntro.SEEN]}")
        run.expectTrue(entry, "the Dangerous-area warning's intro window is closed", bot.attr[DangerWarning.INTRO] != true && bot.attr[DangerWarning.SUPPRESSED] != true, "intro=${bot.attr[DangerWarning.INTRO]} suppressed=${bot.attr[DangerWarning.SUPPRESSED]}")
        // TutorIntro (owner): the tour ends at the Tutor with "Choose your path" open - the Quest Helper is the player's own choice.
        run.expectTrue(entry, "the Quest Helper is left to the player (nothing tracked)", QuestHelper.tracked(bot) == null, "tracked ${QuestHelper.tracked(bot)?.name}")
        if (logoutAt == null) {
            run.expectTrue(entry, "Choose your path (${QuestGuideUi.INTERFACE_ID}) is open at the end", bot.interfaces.isVisible(QuestGuideUi.INTERFACE_ID), describeScreen(run, bot))
        }
        val itemsAfter = (0 until bot.inventory.capacity).mapNotNull { bot.inventory[it]?.let { i -> "${i.id}x${i.amount}" } }.sorted()
        // Owner 2026-10-08: the Deadman starter pack is a new player's only item, handed out once the first introduction is over.
        val pack = gg.rsmod.plugins.content.newplayer.NewPlayerConfig.current.starterChestItem
        val expected = (itemsBefore + "${pack}x1").sorted()
        run.expectTrue(entry, "only the Deadman starter pack was added", itemsAfter == expected, "before $itemsBefore after $itemsAfter", rule = "NewPlayerStart.grantStarterPack after the first introduction")
        val xpChanged = xpOf(bot).filter { (k, v) -> v != xpBefore[k] }.keys
        run.expectTrue(entry, "no experience changed", xpChanged.isEmpty(), "skills $xpChanged")
    }

    /**
     * Night run 2026-10-08 (prompt: "items sent to the bank when the inventory is full must not leave a step waiting"):
     * FoundationRewards.grant banks a quest item when the inventory is full; the next npc must say where it is. Lunar
     * Diplomacy step 3 with the Seal of passage in the bank: Lokar refuses and names the bank.
     */
    private suspend fun QueueTask.bankSeal(run: Conformance.Run) {
        val entry = "bank seal"
        val quest = LunarDiplomacy
        val step = quest.steps[2]
        val npc = locate(run, entry, "step 3", step.npc, step.location, step.place) ?: return
        val bot = run.bot(standNear(run, npc.tile), "QBANK")
        freshAccount(bot)
        quest.setStage(bot, 3)
        bot.bank.add(Items.SEAL_OF_PASSAGE, 1)
        wait(2)
        val conv = talkTo(run, bot, npc, entry, "Lokar with the Seal in the bank", Policy(quest, travel = { false }))
        val said = conv.transcript.joinToString(" / ")
        run.expectTrue(entry, "the step waits (no Seal carried)", quest.stage(bot) == 3, "stage ${quest.stage(bot)}")
        run.expectTrue(entry, "the npc says the Seal is in the bank", said.contains("in your bank"), said.take(300))
    }

    /**
     * 2026-10-08, King's Ransom fought by hand: the Knight Waves knights hit without any animation (combat-defs.json
     * attack/block/death -1). Every quest foe's registered combat definition - the one World.spawn gives the npc - needs
     * an attack and a death animation that exist in the cache (tools\npc-combat-defs\fill-anims-2009scape.js).
     */
    private fun foeAnims(run: Conformance.Run) {
        val entry = "foe anims"
        val defs = run.world.definitions
        FoundationQuests.SHORT.forEach { quest ->
            quest.steps.mapNotNull { it.fight }.forEach { fight ->
                (listOf(fight.npc) + fight.chain.map { it.first }).forEach { id ->
                    val name = defs.getNullable(NpcDef::class.java, id)?.name ?: "?"
                    val combat = run.world.plugins.allNpcCombatDefs()[id]
                    val exists = { anim: Int -> anim >= 0 && defs.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, anim) != null }
                    val fights = combat != null && exists(combat.attackAnimation) && combat.deathAnimation.isNotEmpty() && combat.deathAnimation.all(exists)
                    run.expectTrue(
                        entry, "${quest.name}: $name ($id) attacks and dies with cache animations", fights,
                        if (combat == null) "no combat definition" else "attack ${combat.attackAnimation} death ${combat.deathAnimation}",
                    )
                    // No donor (Matrix, 2009scape, Darkan, Void) has a block animation for some foes: recorded, not invented.
                    val block = combat?.blockAnimation ?: -1
                    if (block >= 0) {
                        run.expectTrue(entry, "${quest.name}: $name ($id) blocks with a cache animation", exists(block), "block $block")
                    } else {
                        run.check(entry, "${quest.name}: $name ($id) block animation", block)
                    }
                }
            }
        }
    }

    private suspend fun QueueTask.tutorDirections(run: Conformance.Run, combat: Int? = null) {
        val entry = if (combat == null) "tutor directions" else "tutor combat $combat"
        val guide = guideNpc(run, entry) ?: return
        val bot = run.bot(standNear(run, guide.tile), if (combat == null) "QTUTOR" else "QTUTORLOW")
        freshAccount(bot)
        if (combat != null) bot.combatLevel = combat
        wait(2)
        val target = KingsRansom
        val policy =
            Policy(
                target,
                travel = { true },
                custom = { title, options ->
                    when (title) {
                        "Quest Tutor" -> options.indexOf("Where do the quests start?").takeIf { it >= 0 }?.plus(1)
                        "Which quest?" -> (options.indexOf(target.name).takeIf { it >= 0 } ?: options.indexOf("More quests").takeIf { it >= 0 })?.plus(1)
                        // Night run 2026-10-08: the Tutor's quest menu offers the Quest Helper for the chosen quest.
                        target.name -> options.indexOf("Start the Quest Helper for this quest.").takeIf { it >= 0 }?.plus(1)
                        else -> null
                    }
                },
            )
        val conv = talkTo(run, bot, guide, entry, "Quest Tutor", policy)
        var n = 0
        while (n++ < 15 && !bot.tile.isWithinRadius(target.startLocation!!, 20)) wait(1)
        run.expectTrue(entry, "the tutor offers the travel to ${target.startPlace}", conv.titles.contains("Travel"), conv.brief())
        run.expectTrue(
            entry, "the travel lands at ${target.startPlace}", bot.tile.isWithinRadius(target.startLocation!!, 20),
            "bot at ${bot.tile.x},${bot.tile.z},${bot.tile.height}; ${conv.brief()}",
        )
        run.expectTrue(entry, "every option dialogue was the tutor's", conv.unexpected.isEmpty(), conv.unexpected.joinToString(" ").take(250))
        run.expectTrue(entry, "the tutor's quest menu starts the Quest Helper for ${target.name}", QuestHelper.tracked(bot) == target, "tracked ${QuestHelper.tracked(bot)?.name}")
        // Owner 2026-10-10: a player below the quest's wiki recommendation is told to train first, with that recommendation;
        // a quest without one (The Temple at Senntisten) never warns.
        val advice = target.trainingAdvice(bot)
        val warned = conv.transcript.any { it.contains("Train your combat first") }
        run.expectTrue(
            entry, "the tutor warns a combat-${bot.combatLevel} player exactly when below the recommendation (${target.recommendation?.text ?: "none"})",
            warned == (advice != null) && (advice == null || conv.transcript.any { it.contains(advice.text) }), conv.brief(),
            rule = "ShortQuest.recommendation (OSRS / RuneScape Wiki 'Recommended', owner 2026-10-10)",
        )
    }
}

/**
 * The twelve 2011 quests of quests/impl, played step by step through real input (npc talk with the answers a player picks,
 * item on npc, object options, item on object, item options, kills). Every entry first measures what a NEW account gets
 * (QuestStatus completes every non-choice quest at login, so the quest may not be startable at all - a FAIL with that
 * reason), then resets the quest's own varp/varbit to 0 (the state the quest was written for) and plays it.
 *
 * Setup that is not the quest route is named in the step label: materials a player gathers by skilling or from random
 * drops (ores, beads, meats, bars, essence, a golden-key drop) are handed to the bot, and a long walk or a boat ride is a
 * moveTo. A step that fails is reported with the exact screen, then the stage it should have set is written so the rest
 * of the quest is still measured ("forced past" in the report notes) - every later bug is found in one run.
 */
object ClassicQuests {
    sealed class Act {
        class Talk(val npc: Int, val near: Tile, val answers: List<String> = emptyList(), val option: String = "talk-to") : Act()

        class UseOnNpc(val item: Int, val npc: Int, val near: Tile, val answers: List<String> = emptyList()) : Act()

        class Loc(val ids: List<Int>, val near: Tile, val option: String, val answers: List<String> = emptyList(), val radius: Int = 12) : Act()

        class UseOnLoc(val item: Int, val ids: List<Int>, val near: Tile, val answers: List<String> = emptyList(), val radius: Int = 12) : Act()

        class ItemOp(val item: Int, val option: String, val answers: List<String> = emptyList()) : Act()

        class Kill(val npc: Int, val near: Tile) : Act()

        class Give(val item: Int, val amount: Int = 1) : Act()

        class Move(val tile: Tile) : Act()

        class Arrive(val tile: Tile, val radius: Int, val what: String) : Act()

        class Holds(val item: Int, val amount: Int = 1) : Act()
    }

    /** One step: its acts, then the quest stage they must have set (null: no stage change expected). */
    class Step(val label: String, val stage: Int?, val acts: List<Act>)

    class Classic(
        val key: String,
        val code: String,
        val quest: gg.rsmod.plugins.content.quests.Quest,
        /** The reward experience the RS Wiki gives (skill to xp), checked across the last step; null = not checked. */
        val xp: Pair<Int, Int>?,
        val steps: List<Step>,
    )

    private fun t(
        x: Int,
        z: Int,
        h: Int = 0,
    ) = Tile(x, z, h)

    private fun talk(
        npc: Int,
        near: Tile,
        vararg answers: String,
    ) = Act.Talk(npc, near, answers.toList())

    val ALL: List<Classic> by lazy {
        listOf(
            Classic(
                "cooks-assistant", "COOK", gg.rsmod.plugins.content.quests.impl.CooksAssistant, Skills.COOKING to 300,
                listOf(
                    Step("start at the Cook", 1, listOf(talk(278, t(3209, 3215), "What's wrong?", "Yes", "I know where to find this stuff"))),
                    Step("hand in egg, milk and flour (setup: gathered items)", 2, listOf(Act.Give(15412), Act.Give(15413), Act.Give(15414), talk(278, t(3209, 3215)))),
                ),
            ),
            Classic(
                "dorics-quest", "DORIC", gg.rsmod.plugins.content.quests.impl.DoricsQuest, Skills.MINING to 1300,
                listOf(
                    Step("start at Doric", 1, listOf(talk(284, t(2953, 3450), "I wanted to use your anvils", "Yes.", "Certainly, I'll be right back"))),
                    Step("hand in 6 clay, 4 copper, 2 iron (setup: mined)", 100, listOf(Act.Give(434, 6), Act.Give(436, 4), Act.Give(440, 2), talk(284, t(2953, 3450)))),
                ),
            ),
            Classic(
                "druidic-ritual", "DRUID", gg.rsmod.plugins.content.quests.impl.DruidicRitual, Skills.HERBLORE to 250,
                listOf(
                    Step("start at Kaqemeex", 1, listOf(talk(455, t(2925, 3485), "I'm in search of a quest", "Ok, I will try and help"))),
                    Step("Sanfew upstairs", 2, listOf(talk(454, t(2899, 3430, 1)))),
                    Step(
                        "four raw meats into the Cauldron of Thunder (setup: raw meats)", null,
                        listOf(2132, 2134, 2136, 2138).flatMap { listOf(Act.Give(it), Act.UseOnLoc(it, listOf(2142), t(2893, 9831))) } +
                            listOf(Act.Holds(522), Act.Holds(523), Act.Holds(524), Act.Holds(525)),
                    ),
                    Step("enchanted meats to Sanfew", 3, listOf(talk(454, t(2899, 3430, 1)))),
                    Step("back to Kaqemeex", 4, listOf(talk(455, t(2925, 3485)))),
                ),
            ),
            Classic(
                "imp-catcher", "IMP", gg.rsmod.plugins.content.quests.impl.ImpCatcher, Skills.MAGIC to 875,
                listOf(
                    Step("start at Wizard Mizgog", 1, listOf(talk(706, t(3103, 3163, 2), "Give me a quest", "I'll try"))),
                    Step("four beads to Mizgog (setup: imp drops)", 2, listOf(Act.Give(1474), Act.Give(1470), Act.Give(1476), Act.Give(1472), talk(706, t(3103, 3163, 2)))),
                ),
            ),
            Classic(
                "lost-city", "LOST", gg.rsmod.plugins.content.quests.impl.LostCity, null,
                listOf(
                    Step(
                        "start at the Warrior", 1,
                        listOf(talk(650, t(3148, 3205), "What are you camped out here for", "What makes you think", "Looks like you don't know either", "Yes")),
                    ),
                    Step("chop the tree: Shamus appears", 2, listOf(Act.Loc(listOf(2409), t(3139, 3212), "chop"))),
                    Step("talk to Shamus", 3, listOf(talk(654, t(3139, 3211), "How does it fit in a shed", "No"))),
                    Step(
                        "sail to Entrana with the axe the Dramen tree needs (bronze hatchet)", null,
                        listOf(Act.Give(1351), talk(657, t(3048, 3236), "Yes, okay, I'm ready to go"), Act.Arrive(t(2834, 3335), 25, "Entrana")),
                    ),
                    Step(
                        "down the Entrana ladder, chop the Dramen tree, kill the tree spirit (setup: on Entrana with an axe)", 4,
                        listOf(
                            Act.Move(t(2821, 3374)), Act.Loc(listOf(2408), t(2820, 3374), "climb-down"),
                            Act.Loc(listOf(1292), t(2860, 9734), "chop down"), Act.Kill(655, t(2860, 9737)),
                        ),
                    ),
                    Step("chop a Dramen branch", 5, listOf(Act.Loc(listOf(1292), t(2860, 9734), "chop down"), Act.Holds(771))),
                    Step(
                        "the shed door with the Dramen staff wielded (setup: knife on branch = staff)", 6,
                        listOf(Act.Give(772), Act.ItemOp(772, "wield"), Act.Move(t(3201, 3168)), Act.Loc(listOf(2406), t(3201, 3169), "open")),
                    ),
                ),
            ),
            Classic(
                "pirates-treasure", "PIRATE", gg.rsmod.plugins.content.quests.impl.PiratesTreasure, null,
                listOf(
                    Step("start at Redbeard Frank", 1, listOf(talk(375, t(3053, 3249), "I'm in search of treasure", "Ok, I will bring you some rum"))),
                    Step(
                        "Karamja: job with Luthas, rum in the crate, bananas on top (setup: rum bought, bananas picked)", null,
                        listOf(
                            Act.Move(t(2938, 3154)), talk(379, t(2939, 3156), "Could you offer me employment"),
                            Act.Give(431), Act.UseOnLoc(431, listOf(2072), t(2943, 3151)),
                            Act.Give(1963, 10), Act.Loc(listOf(2072), t(2943, 3151), "fill"), talk(379, t(2939, 3156)),
                        ),
                    ),
                    Step(
                        "Port Sarim: job at Wydin's in a white apron, the rum from the back-room crate (setup: apron)", null,
                        listOf(
                            Act.Move(t(3014, 3204)), Act.Give(1005), Act.ItemOp(1005, "wear"), talk(557, t(3012, 3203), "Can I get a job here?"),
                            Act.Loc(listOf(2069), t(3012, 3204), "open"), Act.Loc(listOf(2071), t(3009, 3207), "search", listOf("No")), Act.Holds(431),
                        ),
                    ),
                    Step("rum to Frank: chest key", 2, listOf(talk(375, t(3053, 3249)), Act.Holds(432))),
                    Step("open the Blue Moon Inn chest", null, listOf(Act.Move(t(3228, 3395, 1)), Act.Loc(listOf(2079), t(3228, 3395, 1), "open", radius = 20), Act.Holds(433))),
                    Step("read the pirate message", 3, listOf(Act.ItemOp(433, "read"))),
                    Step(
                        "dig in Falador park, kill the gardener, dig again (setup: spade)", 4,
                        listOf(Act.Give(952), Act.Move(t(2999, 3383)), Act.ItemOp(952, "dig"), Act.Kill(3914, t(2999, 3383)), Act.ItemOp(952, "dig")),
                    ),
                ),
            ),
            Classic(
                "priest-in-peril", "PRIEST", gg.rsmod.plugins.content.quests.impl.PriestInPeril, Skills.PRAYER to 1406,
                listOf(
                    Step("start at King Roald", 1, listOf(talk(648, t(3220, 3471), "Sure"))),
                    Step("knock at the temple door", 2, listOf(Act.Move(t(3407, 3489)), Act.Loc(listOf(30707, 30708), t(3406, 3489), "knock-at", listOf("Roald sent me", "Sure")))),
                    Step("kill the Temple guardian", 3, listOf(Act.Move(t(3405, 9900)), Act.Kill(7711, t(3405, 9902)))),
                    Step("report to King Roald", 4, listOf(talk(648, t(3220, 3471)))),
                    Step("Drezel in the temple", 5, listOf(Act.Move(t(3416, 3488, 2)), talk(1049, t(3415, 3489, 2), "Tell me anyway", "Yes"))),
                    Step(
                        "golden key on the monument, water from the well (setup: golden key drop, bucket)", null,
                        listOf(
                            Act.Give(2944), Act.Give(1925), Act.Move(t(3420, 9890)),
                            Act.UseOnLoc(2944, listOf(3497), t(3420, 9890), radius = 35), Act.Holds(2945),
                            Act.UseOnLoc(1925, listOf(3485), t(3420, 9890), radius = 35), Act.Holds(2953),
                        ),
                    ),
                    Step("iron key in the cell door", 6, listOf(Act.Move(t(3414, 3488, 2)), Act.UseOnLoc(2945, listOf(3463), t(3413, 3487, 2)))),
                    Step("Drezel blesses the water", null, listOf(talk(1049, t(3415, 3489, 2)), Act.Holds(2954))),
                    Step("blessed water on the coffin", 7, listOf(Act.UseOnLoc(2954, listOf(30728), t(3413, 3487, 2), radius = 15))),
                    Step("Drezel underground", 8, listOf(Act.Move(t(3439, 9896)), talk(7707, t(3440, 9895)))),
                    Step("50 pure essence to Drezel (setup: essence); Wolfbane once", 61, listOf(Act.Give(7936, 50), talk(7707, t(3440, 9895)), Act.Holds(2952))),
                ),
            ),
            Classic(
                "prince-ali-rescue", "PRINCE", gg.rsmod.plugins.content.quests.impl.PrinceAliRescue, null,
                listOf(
                    Step("start at Hassan", 1, listOf(talk(923, t(3302, 3163), "Can I help you"))),
                    Step("Osman", 2, listOf(talk(924, t(3286, 3180)))),
                    Step("Leela in Draynor", 3, listOf(talk(13172, t(3113, 3262)))),
                    Step(
                        "key imprint: soft clay on Lady Keli, imprint + bronze bar to Osman (setup: soft clay, bronze bar)", 4,
                        listOf(Act.Give(1761), Act.UseOnNpc(1761, 919, t(3128, 3245)), Act.Give(2349), talk(924, t(3286, 3180)), Act.Holds(2418)),
                    ),
                    Step(
                        "three beers for the jail guard (setup: beers)", 6,
                        listOf(Act.Give(1917, 3), Act.UseOnNpc(1917, 917, t(3125, 3250)), Act.UseOnNpc(1917, 917, t(3125, 3250)), Act.UseOnNpc(1917, 917, t(3125, 3250))),
                    ),
                    Step("rope on Lady Keli (setup: rope)", 7, listOf(Act.Give(954), Act.UseOnNpc(954, 919, t(3128, 3245)))),
                    Step("disguise to Prince Ali (setup: wig, skirt, paste from Ned/Aggie)", 8, listOf(Act.Give(2419), Act.Give(1013), Act.Give(2424), talk(920, t(3123, 3241)))),
                    Step("back to Hassan", 110, listOf(talk(923, t(3302, 3163)))),
                ),
            ),
            Classic(
                "rune-mysteries", "RUNE", gg.rsmod.plugins.content.quests.impl.RuneMysteries, null,
                listOf(
                    Step("start at Duke Horacio", 1, listOf(talk(741, t(3212, 3220, 1), "Have you any quests", "Yes"))),
                    Step("talisman and package with Sedridor", 3, listOf(talk(300, t(3105, 9569), "I'm looking for the head wizard", "Yes, certainly"), Act.Holds(290))),
                    Step("package to Aubury", 5, listOf(talk(5913, t(3253, 3403), "package for you", "No"), Act.Holds(291))),
                    Step("notes to Sedridor", 6, listOf(talk(300, t(3105, 9569)))),
                ),
            ),
            Classic(
                "the-knights-sword", "KNIGHT", gg.rsmod.plugins.content.quests.impl.TheKnightsSword, Skills.SMITHING to 12_725,
                listOf(
                    Step(
                        "start at the Squire", 1,
                        listOf(talk(606, t(2974, 3343), "And how is life as a squire", "I can make a new sword", "So would these dwarves make another one", "Ok, I'll give it a go")),
                    ),
                    Step("Reldo", 2, listOf(talk(2660, t(3208, 3496), "Imcando dwarves"))),
                    Step("redberry pie for Thurgo (setup: pie)", 3, listOf(Act.Give(2325), talk(604, t(3001, 3144), "Would you like a redberry pie"))),
                    Step("ask Thurgo for the sword", 4, listOf(talk(604, t(3001, 3144), "Can you make a special sword"))),
                    Step("back to the Squire", 5, listOf(talk(606, t(2974, 3343)))),
                    Step(
                        "the portrait from Sir Vyvin's cupboard", null,
                        listOf(
                            Act.Move(t(2985, 3336, 2)), Act.Loc(listOf(2271), t(2985, 3336, 2), "open", radius = 15),
                            Act.Loc(listOf(2272), t(2985, 3336, 2), "search", radius = 15), Act.Holds(666),
                        ),
                    ),
                    Step("portrait to Thurgo", 6, listOf(talk(604, t(3001, 3144), "About that sword"))),
                    Step(
                        "blurite and two iron bars to Thurgo (setup: mined/smelted)", null,
                        listOf(Act.Give(668), Act.Give(2351, 2), talk(604, t(3001, 3144), "Can you make that replacement sword now"), Act.Holds(667)),
                    ),
                    Step("the sword to the Squire", 7, listOf(talk(606, t(2974, 3343)))),
                ),
            ),
            Classic(
                "the-restless-ghost", "GHOST", gg.rsmod.plugins.content.quests.impl.TheRestlessGhost, null,
                listOf(
                    Step("start at Father Aereck", 1, listOf(talk(456, t(3244, 3205), "I'm looking for a quest", "Okay, let me help then"))),
                    Step("Father Urhney: the Ghostspeak amulet", 2, listOf(talk(458, t(3207, 3149), "Father Aereck sent me", "A ghost is haunting his graveyard"), Act.Holds(552))),
                    Step(
                        "wear the amulet, open the coffin, talk to the ghost", 3,
                        listOf(Act.ItemOp(552, "wear"), Act.Loc(listOf(2145), t(3249, 3192), "open"), talk(457, t(3250, 3195), "Yep. Now, tell me what the problem is")),
                    ),
                    Step("search the rock for the skull", 4, listOf(Act.Loc(listOf(47713, 47714), t(3234, 3145), "search"), Act.Holds(553))),
                    Step("kill the skeleton warlock, the skull in the coffin", 5, listOf(Act.Kill(459, t(3234, 3145)), Act.UseOnLoc(553, listOf(2145, 5728, 5729), t(3249, 3192)))),
                ),
            ),
            Classic(
                "vampyre-slayer", "VAMPYRE", gg.rsmod.plugins.content.quests.impl.VampyreSlayer, Skills.ATTACK to 4825,
                listOf(
                    Step("start at Morgan", 1, listOf(talk(755, t(3100, 3268), "What terrible threat", "Yes"))),
                    Step("Dr Harlow", 2, listOf(talk(756, t(3222, 3398), "Are you Dr Harlow"))),
                    Step("a beer for Harlow: stake and hammer (setup: beer)", null, listOf(Act.Give(1917), talk(756, t(3222, 3398)), Act.Holds(1549), Act.Holds(15417))),
                    Step(
                        "the coffin in the Draynor Manor basement, kill Count Draynor", null,
                        listOf(Act.Move(t(3077, 9772)), Act.Loc(listOf(158), t(3077, 9771), "open", radius = 20), Act.Kill(9356, t(3077, 9771))),
                    ),
                    Step("back to Morgan", 3, listOf(talk(755, t(3100, 3268)))),
                ),
            ),
        )
    }

    private fun stageOf(
        bot: Client,
        quest: gg.rsmod.plugins.content.quests.Quest,
    ): Int = if (quest.usesVarbits) bot.getVarbit(quest.questId) else bot.getVarp(quest.questId)

    private fun setStage(
        bot: Client,
        quest: gg.rsmod.plugins.content.quests.Quest,
        value: Int,
    ) {
        if (quest.usesVarbits) bot.setVarbit(quest.questId, value) else bot.setVarp(quest.questId, value)
    }

    /** Answers by the scripted texts (first option containing one of them); anything else gets "Yes..." or option 1. */
    private fun answers(list: List<String>): QuestsSuite.Policy =
        QuestsSuite.Policy(
            custom = { _, options ->
                list.asSequence().mapNotNull { a -> options.indexOfFirst { it.contains(a, ignoreCase = true) }.takeIf { it >= 0 } }.firstOrNull()?.plus(1)
                    ?: (options.indexOfFirst { it.startsWith("Yes") }.coerceAtLeast(0) + 1)
            },
        )

    private fun findNpc(
        run: Conformance.Run,
        bot: Client,
        id: Int,
        near: Tile,
    ): Npc? {
        var best: Npc? = null
        var score = Int.MAX_VALUE
        run.world.npcs.forEach {
            if (it.id != id || !it.isSpawned() || (it.owner != null && it.owner != bot)) return@forEach
            val s = (if (it.tile.height != near.height) 1000 else 0) + it.tile.getDistance(near) - (if (it.owner == bot) 500 else 0)
            if (s < score) {
                score = s
                best = it
            }
        }
        return best
    }

    private fun findLoc(
        run: Conformance.Run,
        ids: List<Int>,
        near: Tile,
        radius: Int,
    ): gg.rsmod.game.model.entity.GameObject? {
        for (r in 0..radius) {
            for (dx in -r..r) for (dz in -r..r) {
                if (maxOf(Math.abs(dx), Math.abs(dz)) != r) continue
                val tile = near.transform(dx, dz)
                val obj =
                    run.world.chunks.get(tile, createIfNeeded = false)
                        ?.getEntities<gg.rsmod.game.model.entity.GameObject>(tile, gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT)
                        ?.firstOrNull { it.id in ids }
                if (obj != null) return obj
            }
        }
        return null
    }

    private fun opLoc(
        slot: Int,
        id: Int,
        tile: Tile,
    ): gg.rsmod.game.message.Message =
        when (slot) {
            1 -> gg.rsmod.game.message.impl.OpLoc1Message(0, tile.x, id, tile.z)
            2 -> gg.rsmod.game.message.impl.OpLoc2Message(id, tile.x, tile.z, 0)
            3 -> gg.rsmod.game.message.impl.OpLoc3Message(id, tile.x, tile.z, 0)
            4 -> gg.rsmod.game.message.impl.OpLoc4Message(id, tile.x, tile.z, 0)
            else -> gg.rsmod.game.message.impl.OpLoc5Message(id, tile.x, tile.z, 0)
        }

    private suspend fun QueueTask.near(
        run: Conformance.Run,
        bot: Client,
        tile: Tile,
    ) {
        if (bot.tile.isWithinRadius(tile, 8)) return
        bot.moveTo(QuestsSuite.standNear(run, tile))
        wait(2)
    }

    private fun heal(bot: Client) {
        if (bot.getCurrentLifepoints() < bot.getMaximumLifepoints()) bot.setCurrentLifepoints(bot.getMaximumLifepoints())
    }

    /** Runs one act; returns null when it could be done, else what stopped it. */
    private suspend fun QueueTask.act(
        run: Conformance.Run,
        entry: String,
        where: String,
        bot: Client,
        act: Act,
        log: MutableList<String>,
    ): String? =
        with(QuestsSuite) {
            when (act) {
                is Act.Give -> {
                    bot.inventory.add(act.item, act.amount)
                    null
                }
                is Act.Move -> {
                    bot.moveTo(act.tile)
                    wait(2)
                    null
                }
                is Act.Arrive -> if (bot.tile.isWithinRadius(act.tile, act.radius)) null else "not at ${act.what}: bot at ${bot.tile.x},${bot.tile.z},${bot.tile.height}"
                is Act.Holds -> if (held(bot, act.item) >= act.amount) null else "item ${act.item} x${act.amount} not received (held ${held(bot, act.item)})"
                is Act.Talk -> talkAct(run, entry, where, bot, act, log)
                is Act.UseOnNpc -> {
                    near(run, bot, act.near)
                    val npc = findNpc(run, bot, act.npc, act.near)
                    val slot = bot.inventory.getItemIndex(act.item, false)
                    when {
                        npc == null -> "npc ${act.npc} is not in the world"
                        slot < 0 -> "item ${act.item} not in the inventory"
                        else -> {
                            Bots.itemOnNpc(bot, slot, npc)
                            val conv = converse(run, bot, entry, where, answers(act.answers), maxTicks = 40)
                            log += "item ${act.item} on npc ${act.npc}: ${conv.brief()}"
                            null
                        }
                    }
                }
                is Act.Loc -> {
                    near(run, bot, act.near)
                    val obj = findLoc(run, act.ids, act.near, act.radius)
                    if (obj == null) {
                        "object ${act.ids} not found within ${act.radius} tiles of ${act.near.x},${act.near.z},${act.near.height}"
                    } else {
                        val def = obj.getDef(run.world.definitions)
                        val i = def.options.indexOfFirst { it?.equals(act.option, true) == true }
                        if (i < 0) {
                            "object ${obj.id} has no '${act.option}' option (menu ${def.options.toList()})"
                        } else {
                            Bots.inject(bot, opLoc(i + 1, obj.id, obj.tile))
                            val conv = converse(run, bot, entry, where, answers(act.answers), maxTicks = 40)
                            log += "${act.option} ${obj.id}@${obj.tile.x},${obj.tile.z}: ${conv.brief()}"
                            null
                        }
                    }
                }
                is Act.UseOnLoc -> {
                    near(run, bot, act.near)
                    val obj = findLoc(run, act.ids, act.near, act.radius)
                    val slot = bot.inventory.getItemIndex(act.item, false)
                    when {
                        obj == null -> "object ${act.ids} not found within ${act.radius} tiles of ${act.near.x},${act.near.z},${act.near.height}"
                        slot < 0 -> "item ${act.item} not in the inventory"
                        else -> {
                            Bots.inject(bot, gg.rsmod.game.message.impl.OpLocUMessage(obj.id, obj.tile.x, obj.tile.z, slot, act.item, 0))
                            val conv = converse(run, bot, entry, where, answers(act.answers), maxTicks = 40)
                            log += "item ${act.item} on ${obj.id}@${obj.tile.x},${obj.tile.z}: ${conv.brief()}"
                            null
                        }
                    }
                }
                is Act.ItemOp -> {
                    val slot = bot.inventory.getItemIndex(act.item, false)
                    when {
                        slot < 0 -> "item ${act.item} not in the inventory"
                        !Bots.itemOption(bot, slot, act.option) -> "item ${act.item} has no '${act.option}' option"
                        else -> {
                            val conv = converse(run, bot, entry, where, answers(act.answers), maxTicks = 40)
                            log += "${act.option} ${act.item}: ${conv.brief()}"
                            null
                        }
                    }
                }
                is Act.Kill -> killAct(run, entry, where, bot, act)
            }
        }

    private suspend fun QueueTask.talkAct(
        run: Conformance.Run,
        entry: String,
        where: String,
        bot: Client,
        act: Act.Talk,
        log: MutableList<String>,
    ): String? =
        with(QuestsSuite) {
            near(run, bot, act.near)
            var n = 0
            var npc = findNpc(run, bot, act.npc, act.near)
            while (npc == null && n++ < 6) {
                wait(1)
                npc = findNpc(run, bot, act.npc, act.near)
            }
            if (npc == null) return "npc ${act.npc} is not in the world"
            if (!bot.tile.isWithinRadius(npc.tile, 10)) {
                bot.moveTo(standNear(run, npc.tile))
                wait(2)
            }
            val conv = talkTo(run, bot, npc, entry, where, answers(act.answers), act.option)
            log += "talk ${act.npc}: ${conv.brief()}"
            if (!conv.opened) "talking to npc ${act.npc} opened nothing" else null
        }

    private suspend fun QueueTask.killAct(
        run: Conformance.Run,
        entry: String,
        where: String,
        bot: Client,
        act: Act.Kill,
    ): String? =
        with(QuestsSuite) {
            var n = 0
            var foe = findNpc(run, bot, act.npc, act.near)
            while ((foe == null || foe.isDead()) && n++ < 10) {
                wait(1)
                foe = findNpc(run, bot, act.npc, act.near)
            }
            val target = foe ?: return "npc ${act.npc} to kill did not appear"
            if (!bot.tile.isWithinRadius(target.tile, 10)) {
                bot.moveTo(standNear(run, target.tile))
                wait(2)
            }
            target.setCurrentLifepoints(minOf(target.getCurrentLifepoints(), 5))
            val op = optionIndex(run, target.id, "attack")
            var ticks = 0
            while (ticks++ < 200 && target.isSpawned() && !target.isDead()) {
                heal(bot)
                if (op > 0 && bot.getCombatTarget() !== target && ticks % 3 == 1) Bots.npcOption(bot, target, op)
                wait(1)
            }
            wait(3)
            if (bot.interfaces.isVisible(DangerWarning.INTERFACE_ID) || chatOpen(bot) != -1) converse(run, bot, entry, where, answers(emptyList()), maxTicks = 30)
            if (target.isSpawned() && !target.isDead()) "npc ${act.npc} not killed in 200 ticks (attack option $op)" else null
        }

    suspend fun QueueTask.play(
        run: Conformance.Run,
        c: Classic,
    ) {
        val entry = "classic ${c.key}"
        val quest = c.quest
        val first = c.steps.first().acts.filterIsInstance<Act.Talk>().first()
        val bot = run.bot(QuestsSuite.standNear(run, first.near), "K${c.code}".take(12))
        QuestsSuite.freshAccount(bot)
        wait(2)
        val listEntry = QuestList.ENTRIES.firstOrNull { it.slot == quest.slot }
        val registered = gg.rsmod.plugins.content.quests.Quest.getQuest(quest.slot)
        run.expectTrue(
            entry, "registered in the quest list (Quest.quests + QuestList slot ${quest.slot})", registered === quest && listEntry != null,
            "Quest.getQuest=${registered?.name} list=${listEntry?.name}",
        )

        // 1. What a new account gets.
        val loginStage = stageOf(bot, quest)
        run.check(entry, "new account: quest state", QuestStatus.state(bot, quest).name, "owner 2026-10-07 'play every quest'" to QuestStatus.State.NOT_STARTED.name)
        val log0 = ArrayList<String>()
        val firstStep = c.steps.first()
        for (a in firstStep.acts) if (act(run, entry, "new account: ${firstStep.label}", bot, a, log0) != null) break
        run.check(entry, "new account: '${firstStep.label}' starts the quest (stage)", stageOf(bot, quest), "quest script" to firstStep.stage)
        if (stageOf(bot, quest) != firstStep.stage) {
            run.note("$entry: new account, quest varp/varbit ${quest.questId} = $loginStage after login (QuestStatus.login/sync writes the complete value); ${log0.joinToString(" | ").take(300)}")
        }
        if (bot.interfaces.getModal() != -1) {
            Bots.inject(bot, CloseModalMessage())
            wait(1)
        }

        // 2. The quest as written, from stage 0.
        setStage(bot, quest, 0)
        c.steps.forEachIndexed { index, step ->
            if (Conformance.stopRequested) return
            val where = "step ${index + 1} (${step.label})"
            val xp0 = QuestsSuite.xpOf(bot)
            val c0 = run.world.currentCycle
            val before = stageOf(bot, quest)
            val log = ArrayList<String>()
            var problem: String? = null
            for (a in step.acts) {
                problem = act(run, entry, where, bot, a, log)
                if (problem != null) break
            }
            val after = stageOf(bot, quest)
            val last = index == c.steps.size - 1
            val stageOk =
                when {
                    last -> after != 0 && listEntry?.progress?.read?.invoke(bot) == QuestStatus.COMPLETE
                    step.stage == null -> true
                    else -> after == step.stage
                }
            val ok = problem == null && stageOk
            run.expectTrue(
                entry, "$where${if (step.stage != null) ": stage -> ${step.stage}" else ""}", ok,
                "stage $before -> $after${if (problem != null) "; STOPPED: $problem" else ""}; ${log.joinToString(" | ").take(320)}; ${QuestsSuite.describeScreen(run, bot)}",
            )
            run.expectTrue(entry, "$where: no lock left", bot.lock == LockState.NONE, "lock ${bot.lock}")
            if (last) {
                val scroll = run.recording.of(bot.username, "msg").any { it.cycle >= c0 && it.text.startsWith("IfOpenSubMessage(") && it.text.contains("component=277,") }
                run.expectTrue(entry, "$where: the quest-complete scroll (277) shows", scroll, "since cycle $c0")
                c.xp?.let { (skill, amount) ->
                    val gained = Math.round((QuestsSuite.xpOf(bot)[skill] ?: 0.0) - (xp0[skill] ?: 0.0)).toInt()
                    run.check(entry, "$where: reward experience (skill $skill)", gained, "RS Wiki quest reward" to amount)
                }
            }
            if (!ok && step.stage != null && after != step.stage) {
                // Forced past: the rest of the quest is still measured.
                setStage(bot, quest, step.stage)
                run.note("$entry: $where failed, stage forced to ${step.stage} so the later steps are still played")
            }
            if (bot.interfaces.getModal() != -1) {
                Bots.inject(bot, CloseModalMessage())
                wait(1)
            }
        }
    }
}
