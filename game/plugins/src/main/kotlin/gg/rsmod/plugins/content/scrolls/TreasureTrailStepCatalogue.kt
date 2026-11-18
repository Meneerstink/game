package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.content.inter.emotes.Emote
import java.io.File

/** The limited, individually sourced step selection. Nothing is inferred from a donor tier. */
class TreasureTrailStepCatalogue private constructor(val rows: List<Step>) {
    enum class Action { DIG, EMOTE, TALK, SEARCH, KILL, SHERLOCK, FALO, HOT_COLD }
    enum class Challenge { NONE, URI, DOUBLE_AGENT, GUARD, NUMBER, SLIDING_PUZZLE, LIGHT_BOX, PUZZLE_CHOICE }
    data class Equipment(val osrs: Int, val existingLocal: Int?) {
        fun local(): Int? = ClueItems.local(osrs) ?: existingLocal
    }
    data class Step(
        val key: String,
        val tier: ClueScrollTier,
        val kind: String,
        val action: Action,
        val hint: String,
        val points: List<Tile>,
        val npcName: String?,
        val existingNpcs: Set<Int>,
        val equipment: List<List<Equipment>>,
        val onlyListedEquipment: Boolean,
        val emptySlots: Set<Int>,
        val firstEmote: Int?,
        val secondEmote: Int?,
        val challenge: Challenge,
        val guards: List<Int>,
        val chooseGuard: Boolean,
        val question: String?,
        val answers: Set<Int>,
        val puzzles: Set<Challenge>,
        val prayerBelow: Int?,
        val rangedAtLeast: Int?,
        val excludeBlowpipe: Boolean,
        val source: String,
    ) {
        /** An internal save key, not an allocated cache ID. */
        val id: Int get() = key.hashCode()
        val tornPart: Boolean get() = kind == "THREE_PART_COMPONENT"
        fun at(tile: Tile): Boolean = points.any { it == tile }

        /** Missing alternatives never turn a required equipment group into an empty check. */
        fun acceptsEquipment(slots: List<Int?>, resolve: (Equipment) -> Int? = Equipment::local): Boolean {
            val worn = slots.filterNotNull().toSet()
            if (emptySlots.any { slots.getOrNull(it) != null }) return false
            if (!equipment.all { group -> group.any { resolve(it)?.let(worn::contains) == true } }) return false
            if (onlyListedEquipment) {
                val allowed = equipment.flatten().mapNotNull(resolve).toSet()
                if (worn.any { it !in allowed }) return false
            }
            return true
        }
    }

    init {
        require(rows.isNotEmpty())
        require(rows.map { it.key }.distinct().size == rows.size) { "Duplicate sourced clue key" }
        require(rows.map { it.id }.distinct().size == rows.size) { "Clue persistence-key collision" }
        rows.forEach { row ->
            require(row.hint.isNotBlank() && row.source.startsWith("https://oldschool.runescape.wiki/"))
            require(row.equipment.none { it.isEmpty() }) { "Empty equipment requirement: ${row.key}" }
            require(row.action != Action.EMOTE || row.equipment.isNotEmpty() || row.onlyListedEquipment) {
                "Emote equipment requirements disappeared: ${row.key}"
            }
            require(row.action != Action.EMOTE || row.firstEmote != null)
            require(row.action != Action.EMOTE || (row.tier == ClueScrollTier.MEDIUM) == (row.secondEmote != null))
            require(row.challenge != Challenge.NUMBER || row.question != null && row.answers.isNotEmpty())
            require(row.challenge != Challenge.GUARD || row.guards.isNotEmpty())
            require(row.action != Action.DIG && row.action != Action.SEARCH && row.action != Action.EMOTE || row.points.isNotEmpty())
            require(row.kind != "ANAGRAM" || row.tier != ClueScrollTier.MASTER || row.puzzles.isNotEmpty()) {
                "Master anagram lost its puzzle requirement: ${row.key}"
            }
        }
    }

    companion object {
        val current by lazy { parse(File(RspsPaths.data("cfg/clue/steps.json")).readText()) }

        fun parse(json: String): TreasureTrailStepCatalogue {
            fun JsonObject.optional(key: String) = get(key)?.takeUnless { it.isJsonNull }
            fun emote(value: String?): Int? = value?.let { name ->
                Emote.values.firstOrNull { it.name == name.uppercase().replace(' ', '_') }?.component
                    ?: error("Unmapped source emote '$name'")
            }
            val rows = JsonParser().parse(json).asJsonArray.map { value ->
                val row = value.asJsonObject
                val challenge = row["challenge"].asJsonObject
                val requirements = row["requirements"].asJsonObject
                val type = challenge.optional("type")?.asString?.let(Challenge::valueOf) ?: Challenge.NONE
                Step(
                    key = row["key"].asString,
                    tier = ClueScrollTier.valueOf(row["tier"].asString.uppercase()),
                    kind = row["kind"].asString,
                    action = Action.valueOf(row["action"].asString),
                    hint = row["hint"].asString,
                    points = row["points"].asJsonArray.map { point -> point.asJsonArray.let { Tile(it[0].asInt, it[1].asInt, it[2].asInt) } },
                    npcName = row.optional("npcName")?.asString,
                    existingNpcs = row["npcCandidates"].asJsonArray.map { it.asJsonObject["id"].asInt }.toSet(),
                    equipment = row["equipment"].asJsonArray.map { group -> group.asJsonArray.map { item ->
                        item.asJsonObject.let { Equipment(it["osrs"].asInt, it.optional("local")?.asInt) }
                    } },
                    onlyListedEquipment = requirements.optional("onlyListedEquipment")?.asBoolean ?: false,
                    emptySlots = requirements.optional("emptySlots")?.asJsonArray?.map {
                        EquipmentType.valueOf(if (it.asString == "BODY") "CHEST" else it.asString).id
                    }?.toSet().orEmpty(),
                    firstEmote = emote(challenge.optional("emote")?.asString),
                    secondEmote = emote(challenge.optional("secondEmote")?.asString),
                    challenge = type,
                    guards = challenge.optional("guards")?.asJsonArray?.map { it.asInt }.orEmpty(),
                    chooseGuard = challenge.optional("spawnMode")?.asString == "CHOICE",
                    question = challenge.optional("question")?.asString,
                    answers = challenge.optional("answer")?.asString?.let { text -> Regex("\\d+").findAll(text).map { it.value.toInt() }.toSet() }.orEmpty(),
                    puzzles = challenge.optional("alternatives")?.asJsonArray?.map { Challenge.valueOf(it.asString) }?.toSet().orEmpty(),
                    prayerBelow = requirements.optional("prayerBonusBelow")?.asInt,
                    rangedAtLeast = requirements.optional("rangedBonusAtLeast")?.asInt,
                    excludeBlowpipe = requirements.optional("excludeBlowpipeBonus")?.asBoolean ?: false,
                    source = row["source"].asString,
                )
            }
            return TreasureTrailStepCatalogue(rows)
        }
    }
}
