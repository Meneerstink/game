package gg.rsmod.plugins.content.quests.foundation

/**
 * The dialogue options the Quest Helper highlights (RuneLite Quest Helper DialogChoiceStep / WidgetChoiceStep: the
 * right option drawn in the text highlight colour with its number, "[1] ..."). One source for the client's highlight
 * (QuestHelper `qhd` payload) and the conformance bots' answers (QuestsSuite.Policy), so they never disagree.
 *
 * A hint is (title, answer) for an option dialogue with that title. Matching, on the client as here: "Start *" = the
 * title starts with "Start "; an answer "#1" = the first option; "x*" = the option starts with x; "*x" = it ends with x;
 * anything else = the exact option text.
 */
object QuestDialogueHints {
    /** The puzzle answers, by the title of the option dialogue that asks. */
    private val PUZZLES: Map<ShortQuest, List<Pair<String, String>>> =
        mapOf(
            LunarDiplomacy to listOf("Brundt's question" to "Honour."),
            KingsRansom to
                listOf(
                    "Your evidence" to "A man in black armour was seen at the back door.",
                    "And the butler?" to "The butler had a wet boot - he came in from the moat.",
                    "Tumbler 1" to "High",
                    "Tumbler 2" to "Low",
                    "Tumbler 3" to "Low",
                    "Tumbler 4" to "High",
                ),
            // DT2 (owner 2026-10-07): solved from the inscription - "the blade before the ice, the ice before the deep, the
            // deep before the whisper" - by the clue word each seal carries.
            DesertTreasureII to
                listOf(
                    "Which seal was set first?" to "*(the blade)",
                    "Which seal was set second?" to "*(the ice)",
                    "Which seal was set third?" to "*(the deep)",
                    "Which seal was set last?" to "*(the whisper)",
                ),
        )

    /** Every hint for [quest] at [stage]: starting it, its travel offers, and its puzzle answers. */
    fun hints(
        quest: ShortQuest,
        stage: Int,
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        if (stage <= 0) {
            out += "Quests" to "${quest.name}."
            out += "Start *" to "#1"
        }
        out += "Travel" to "Can you get me to *"
        out += "Travel" to "Send me to *"
        out += PUZZLES[quest].orEmpty()
        return out
    }

    /** The puzzle answer for an option dialogue [title] with [options], or null when no quest asks it. */
    fun puzzleAnswer(
        title: String,
        options: List<String>,
    ): String? {
        val answer = PUZZLES.values.flatten().firstOrNull { matches(it.first, title) }?.second ?: return null
        return options.firstOrNull { matches(answer, it) } ?: "(no option matches $answer)"
    }

    fun matches(
        pattern: String,
        text: String,
    ): Boolean =
        when {
            pattern.endsWith("*") -> text.startsWith(pattern.dropLast(1))
            pattern.startsWith("*") -> text.endsWith(pattern.drop(1))
            else -> text == pattern
        }
}
