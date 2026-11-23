package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket
import gg.rsmod.plugins.content.quests.QuestList
import gg.rsmod.plugins.content.quests.QuestStatus

/**
 * Automatic quest unlocks per combat bracket (DMM-ARMAGEDDON D7, owner 20-11-2026: "precies de foto").
 *
 * OSRS Wiki Deadman: Armageddon "Automatic quest unlocks" (owner photo C:\Users\Pa\Desktop\l\automatic quests unlock.png): "players
 * will automatically unlock certain quests as they move through the combat brackets ... Starting with the second bracket, players
 * will also be given the choice between two smaller lists of quests upon entering a new bracket, of which they can unlock one. It is
 * not required to have picked the partial set of quests from a storyline in order to choose the complete storyline later on ...
 * players can pick which storyline they would like to unlock at a time of their own choosing." "No experience is received ... Items
 * normally received as quest rewards will not be awarded". Recipe for Disaster: "Only the subquests ... are automatically unlocked".
 *
 * On our server every quest of the list used to be completed for everyone (QuestStatus); for accounts made after this release
 * ([QuestChoices.ARMAGEDDON]) the quests named on the photo are locked until their bracket is reached (or the storyline is chosen,
 * or the quest is really played); every other quest stays completed. Existing accounts keep everything (owner 20-11). The brackets
 * replace the Quest Guide choice for the photo's quests (Lunar Diplomacy, Desert Treasure, Monkey Madness II, Beneath Cursed Sands,
 * Dragon Slayer II, King's Ransom); Desert Treasure II, Song of the Elves and The Temple at Senntisten stay Quest Guide choices.
 *
 * Quests and rewards of the photo that do not exist on this server are listed in [Step.notOnServer] (shown, nothing to unlock).
 */
object QuestBrackets {
    class Storyline(
        val label: String,
        val quests: List<String>,
        val notOnServer: List<String> = emptyList(),
    )

    class Step(
        val bracket: Bracket,
        val fixed: List<String>,
        /** Recipe for Disaster council members freed by this bracket (the Culinaromancer's gloves follow it); -1 = none yet. */
        val rfdSubquests: Int,
        val option1: Storyline?,
        val option2: Storyline?,
        val notOnServer: List<String> = emptyList(),
    ) {
        val options: List<Storyline> get() = listOfNotNull(option1, option2)
    }

    /** RFD subquest count at which the whole quest is done (8 council members + the final fight). */
    const val RFD_COMPLETE = 9

    /**
     * Recipe for Disaster's 667 progress varbit (quest list: 1-4 in progress, 5 complete). The Culinaromancer's Chest in the Lumbridge
     * cellar (multiloc 12308) shows as the chest 12309 with "Buy-food" / "Buy-items" from [RFD_CHEST_STAGE] on - measured 20-11 on the
     * test server (below it the chest is not there). A bracket with RFD subquests sets the quest in progress at that stage.
     */
    const val RFD_VARBIT = gg.rsmod.plugins.api.cfg.Varbits.RECIPE_FOR_DISASTER_PROGRESS
    const val RFD_CHEST_STAGE = 3

    /** The photo, column by column. Quest names as our quest list writes them ("Dragon Slayer" = Dragon Slayer I). */
    val STEPS: List<Step> =
        listOf(
            Step(
                Bracket.B3_50,
                fixed =
                    listOf(
                        "Ernest the Chicken", "Goblin Diplomacy", "Rune Mysteries", "Shield of Arrav", "Druidic Ritual", "Priest in Peril",
                        "The Restless Ghost", "Animal Magnetism", "Holy Grail", "Merlin's Crystal", "Fairy Tale II - Cure a Queen",
                        "Fairy Tale I - Growing Pains", "Lost City", "Nature Spirit",
                    ),
                rfdSubquests = -1,
                option1 = null,
                option2 = null,
                notOnServer = listOf("Alfred Grimhand's Barcrawl"),
            ),
            Step(
                Bracket.B51_70,
                fixed = emptyList(),
                // RFD: Another Cook's Quest + Mountain Dwarf, Goblin Generals, Pirate Pete, Lumbridge Guide, Evil Dave, Skrach Uglogwee.
                rfdSubquests = 6,
                option1 = Storyline("Fremennik storyline", listOf("The Fremennik Trials", "The Fremennik Isles", "Lunar Diplomacy", "The Fremennik Exiles")),
                option2 = Storyline("Elf storyline", listOf("Plague City", "Biohazard", "Underground Pass", "Regicide", "Roving Elves")),
                notOnServer = listOf("Slayer reward: Bigger and Badder"),
            ),
            Step(
                Bracket.B71_90,
                fixed = listOf("Tree Gnome Village", "The Grand Tree", "Monkey Madness", "Troll Stronghold"),
                // + RFD: King Awowogei.
                rfdSubquests = 7,
                option1 = Storyline("Gnome storyline", listOf("The Grand Tree", "The Eyes of Glouphrie", "Monkey Madness", "Monkey Madness II")),
                option2 =
                    Storyline(
                        "Mahjarrat storyline #1-#6",
                        listOf("The Dig Site", "Hazeel Cult", "Fight Arena", "Enakhra's Lament", "Temple of Ikov", "Desert Treasure"),
                    ),
                notOnServer = listOf("Slayer reward: Malevolent masquerade"),
            ),
            Step(
                Bracket.B91_110,
                fixed = listOf("Dragon Slayer", "Heroes' Quest"),
                // + RFD: Sir Amik Varze.
                rfdSubquests = 8,
                option1 = Storyline("Desert storyline", listOf("Prince Ali Rescue", "Icthlarin's Little Helper", "Contact!", "Beneath Cursed Sands")),
                option2 =
                    Storyline(
                        "Myreque storyline",
                        listOf(
                            "In Search of the Myreque", "In Aid of the Myreque", "Darkness of Hallowvale", "A Taste of Hope",
                            "A Night at the Theatre",
                        ),
                    ),
            ),
            Step(
                Bracket.B111_PLUS,
                fixed =
                    listOf(
                        "Recipe for Disaster", "Black Knights' Fortress", "Murder Mystery", "One Small Favour", "Shilo Village", "Jungle Potion",
                        "King's Ransom",
                    ),
                rfdSubquests = RFD_COMPLETE,
                option1 = Storyline("Dragonkin storyline", listOf("Dragon Slayer", "A Tail of Two Cats", "Dragon Slayer II")),
                option2 =
                    Storyline(
                        "Mahjarrat storyline #7-#11",
                        listOf(
                            "Hazeel Cult", "Fight Arena", "Devious Minds", "Secrets of the North", "Making Friends with My Arm",
                            "The General's Shadow", "His Faithful Servants",
                        ),
                    ),
            ),
        )

    fun step(bracket: Bracket): Step = STEPS.first { it.bracket == bracket }

    /** Chat lines of at most [MAX_LINE] characters: [title], then the names comma-separated, wrapped between names. */
    const val MAX_LINE = 80

    fun chatLines(
        title: String,
        names: List<String>,
    ): List<String> {
        val out = mutableListOf(title)
        var line = ""
        names.forEachIndexed { i, name ->
            val part = name + if (i < names.size - 1) "," else "."
            if (line.isNotEmpty() && line.length + 1 + part.length > MAX_LINE) {
                out += line
                line = part
            } else {
                line = if (line.isEmpty()) part else "$line $part"
            }
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    private fun announce(
        player: Player,
        title: String,
        names: List<String>,
    ) = chatLines(title, names).forEachIndexed { i, l -> player.message(if (i == 0) "<col=ef1020>$l</col>" else l) }

    private val slotOf: Map<String, Int> by lazy { QuestList.ENTRIES.associate { it.name to it.slot } }

    fun slot(name: String): Int = slotOf[name] ?: error("quest '$name' is not in the quest list")

    /** Every quest slot of the photo. */
    val PHOTO_SLOTS: Set<Int> by lazy { STEPS.flatMap { s -> s.fixed + s.options.flatMap { it.quests } }.map(::slot).toSet() }

    /** The short quests (Quest Guide choice quests and Beneath Cursed Sands) that the brackets unlock. */
    val BRACKET_SHORT_QUESTS: Set<ShortQuest> by lazy { PHOTO_SLOTS.mapNotNull { QuestStatus.CHOICE_QUESTS[it] }.toSet() }

    fun applies(player: Player): Boolean = QuestChoices.isArmageddon(player)

    /** The quest is unlocked through the brackets for this account (so not a Quest Guide choice). */
    fun viaBracket(
        player: Player,
        quest: ShortQuest,
    ): Boolean = applies(player) && quest in BRACKET_SHORT_QUESTS

    /** Whether the brackets decide [slot] for this account (else the quest's normal rule applies). */
    fun gates(
        player: Player,
        slot: Int,
    ): Boolean = applies(player) && slot in PHOTO_SLOTS && QuestStatus.CHOICE_QUESTS[slot] == null

    fun reached(player: Player): List<Step> = STEPS.filter { it.bracket.ordinal <= DeadmanBrackets.of(player).ordinal }

    /**
     * BARCRAWL (24-11): a quest of the photo that has no entry in our quest list ([Step.notOnServer], the miniquest Alfred Grimhand's
     * Barcrawl in bracket 3-50) is completed like the listed quests of its bracket: for an Armageddon account once that bracket is
     * reached (3-50 = every account from the start, as in OSRS Deadman: Armageddon), for older accounts always (they have every quest).
     * Any other unknown name stays false, so a typo in a requirement never opens a quest.
     */
    fun givesUnlisted(
        player: Player,
        name: String,
    ): Boolean {
        val step = STEPS.firstOrNull { name in it.notOnServer } ?: return false
        return !applies(player) || step in reached(player)
    }

    private val optionKeys: Map<Bracket, AttributeKey<Int>> =
        Bracket.values().associateWith { AttributeKey<Int>(persistenceKey = "armageddon_quest_option_${it.name.lowercase()}") }

    /** The storyline chosen in [bracket] (1 or 2), or null. */
    fun chosenOption(
        player: Player,
        bracket: Bracket,
    ): Int? = player.attr[optionKeys.getValue(bracket)]

    /** The quest slots this account has unlocked through its brackets and storylines. */
    fun unlockedSlots(player: Player): Set<Int> {
        val out = HashSet<Int>()
        for (s in reached(player)) {
            s.fixed.forEach { out += slot(it) }
            val option = chosenOption(player, s.bracket)?.let { s.options.getOrNull(it - 1) } ?: continue
            option.quests.forEach { out += slot(it) }
        }
        return out
    }

    fun isUnlocked(
        player: Player,
        slot: Int,
    ): Boolean = slot in unlockedSlots(player)

    /** Brackets reached whose storyline is not chosen yet. */
    fun openChoices(player: Player): List<Step> =
        if (!applies(player)) emptyList() else reached(player).filter { it.options.isNotEmpty() && chosenOption(player, it.bracket) == null }

    /** The RFD subquests this account has: [RFD_COMPLETE] when the quest is done, -1 before its first subquest. */
    fun rfdSubquests(player: Player): Int {
        if (QuestStatus.isCompleted(player, "Recipe for Disaster")) return RFD_COMPLETE
        if (!applies(player)) return RFD_COMPLETE
        // PROJECT48 Q42: parts played in the quest count like bracket parts (n parts done = subquest index n - 1).
        val played = gg.rsmod.plugins.content.quests.foundation.bracket.RecipeForDisaster.partsDone(player) - 1
        return maxOf(reached(player).maxOf { it.rfdSubquests }, played)
    }

    /** Culinaromancer's gloves in shop order: hardleather (0) ... dragon (8), barrows (9) - OSRS Wiki Culinaromancer's Chest. */
    fun gloveRefusal(
        player: Player,
        tier: Int,
    ): String? {
        val have = rfdSubquests(player)
        if (have >= tier) return null
        return if (have < 0) {
            "Recipe for Disaster starts with the 51-70 combat bracket."
        } else {
            "You need more of Recipe for Disaster for those gloves - they come with a higher combat bracket."
        }
    }

    /**
     * Test-server tool (::armageddonquests): turns this account into a new Armageddon account - cohort, no storylines, the photo's
     * list quests and bracket short quests back to not started - then applies its brackets again (no reward is paid twice: the
     * short quests keep their reward record and only get their stage back).
     */
    fun resetForTest(player: Player) {
        player.attr[QuestChoices.COHORT] = QuestChoices.ARMAGEDDON
        player.attr[DeadmanBrackets.REACHED] = DeadmanBrackets.forCombatLevel(player.combatLevel).ordinal
        Bracket.values().forEach { player.attr.remove(optionKeys.getValue(it)) }
        QuestList.ENTRIES.filter { it.slot in PHOTO_SLOTS && QuestStatus.CHOICE_QUESTS[it.slot] == null }.forEach { it.progress.reset(player) }
        BRACKET_SHORT_QUESTS.forEach { it.setStage(player, 0) }
        ensure(player)
        QuestStatus.sync(player)
    }

    enum class Result { CHOSEN, NOT_ARMAGEDDON, NOT_REACHED, NO_OPTIONS, ALREADY_CHOSEN, BAD_OPTION }

    /** Unlocks storyline [option] (1 or 2) of [bracket] - once per bracket, at any time after reaching it. */
    fun choose(
        player: Player,
        bracket: Bracket,
        option: Int,
    ): Result {
        if (!applies(player)) return Result.NOT_ARMAGEDDON
        val s = step(bracket)
        if (s.options.isEmpty()) return Result.NO_OPTIONS
        if (s !in reached(player)) return Result.NOT_REACHED
        val storyline = s.options.getOrNull(option - 1) ?: return Result.BAD_OPTION
        if (chosenOption(player, bracket) != null) return Result.ALREADY_CHOSEN
        player.attr[optionKeys.getValue(bracket)] = option
        storyline.quests.mapNotNull { QuestStatus.CHOICE_QUESTS[slot(it)] }.forEach { FoundationRewards.unlockOnly(player, it) }
        QuestStatus.sync(player)
        announce(player, "${storyline.label} unlocked:", storyline.quests)
        return Result.CHOSEN
    }

    /**
     * Makes sure the short quests of every reached bracket's fixed list are unlocked (idempotent: login and every new bracket).
     * Returns the short quests unlocked now.
     */
    fun ensure(player: Player): List<ShortQuest> {
        if (!applies(player)) return emptyList()
        // RFD subquests from 51-70 on: the quest is in progress and the Culinaromancer's Chest stands in the cellar.
        val rfd = reached(player).maxOf { it.rfdSubquests }
        if (rfd in 0 until RFD_COMPLETE && player.getVarbit(RFD_VARBIT) < RFD_CHEST_STAGE) player.setVarbit(RFD_VARBIT, RFD_CHEST_STAGE)
        val unlocked = reached(player).flatMap { it.fixed }.mapNotNull { QuestStatus.CHOICE_QUESTS[slot(it)] }.filter { FoundationRewards.unlockOnly(player, it) }
        return unlocked
    }

    /**
     * New combat bracket(s) reached: their quests unlock and each bracket above [previous] (the highest ordinal reached before; -1 =
     * only the current one) is announced, with its storyline choice - a big level jump skips no bracket's message.
     */
    fun onBracketReached(
        player: Player,
        previous: Int = DeadmanBrackets.of(player).ordinal - 1,
    ) {
        if (!applies(player)) return
        ensure(player)
        QuestStatus.sync(player)
        for (s in reached(player).filter { it.bracket.ordinal > previous }) {
            val names = s.fixed + if (s.rfdSubquests in 0 until RFD_COMPLETE) listOf("Recipe for Disaster subquests") else emptyList()
            if (names.isNotEmpty()) announce(player, "Quests unlocked by the ${s.bracket.label} bracket:", names)
            if (s.options.isNotEmpty() && chosenOption(player, s.bracket) == null) {
                player.message("<col=ef1020>You may unlock one storyline of the ${s.bracket.label} bracket at the Quest Guide:</col>")
                player.message("${s.option1!!.label} or ${s.option2!!.label}.")
            }
        }
    }
}
