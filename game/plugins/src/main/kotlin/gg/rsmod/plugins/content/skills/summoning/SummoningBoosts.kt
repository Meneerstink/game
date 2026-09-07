package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills

/**
 * The familiars' invisible skilling boosts.
 *
 * The 2011 Knowledge Base familiars table lists these in its "Other Abilities" column, and
 * [SummoningCatalogue] already stores that column verbatim - "Mining boost (7) - invisible",
 * "Fishing boost (3) - invisible", and so on. Nothing read them, so twenty-odd familiars whose
 * whole point is a skilling boost had no effect on skilling at all.
 *
 * "Invisible" is the mechanic, not a description: the boost raises the level used to roll for
 * success, and does **not** raise the level shown in the skills tab, does **not** let the player
 * gather something they are not high enough to gather, and does **not** count towards a
 * requirement. That is why [effectiveLevel] is only ever called from a success roll.
 *
 * ## What is deliberately not here
 *
 * The same column also carries percentage combat entries - "Defence boost (10% bonus to your stab,
 * slash and crush Defence)". Those are a different mechanic (a bonus multiplier, not a level
 * addition) applied in a different place, so they are excluded rather than approximated as levels.
 * [BOOST_PATTERN] only matches the flat `(N)` skilling form, and [boosts] is built by parsing the
 * catalogue's own strings rather than by re-listing them, so the knowledge base text stays the
 * single source and a new familiar's boost is picked up automatically.
 */
object SummoningBoosts {
    /** e.g. `Mining boost (7) - invisible`. The percentage form is intentionally not matched. */
    private val BOOST_PATTERN = Regex("""^(\w+) boost \((\d+)\) - invisible$""")

    private val SKILLS_BY_NAME =
        mapOf(
            "Mining" to Skills.MINING,
            "Fishing" to Skills.FISHING,
            "Woodcutting" to Skills.WOODCUTTING,
            "Firemaking" to Skills.FIREMAKING,
            "Hunter" to Skills.HUNTER,
        )

    /** familiar npc id -> (skill -> invisible levels), derived from the catalogue's own text. */
    private val boosts: Map<Int, Map<Int, Int>> =
        SummoningCatalogue.byPouch.values
            .mapNotNull { entry ->
                val parsed =
                    entry.abilities.mapNotNull { ability ->
                        val match = BOOST_PATTERN.matchEntire(ability.trim()) ?: return@mapNotNull null
                        val skill = SKILLS_BY_NAME[match.groupValues[1]] ?: return@mapNotNull null
                        skill to match.groupValues[2].toInt()
                    }
                if (parsed.isEmpty()) null else entry.pouch.npc to parsed.toMap()
            }.toMap()

    /** The invisible boost the player's active familiar gives [skill], or 0. */
    fun invisibleBoost(
        player: Player,
        skill: Int,
    ): Int {
        val npc = Familiar.current(player) ?: return 0
        return boosts[npc.id]?.get(skill) ?: 0
    }

    /**
     * The level a success roll should use: the player's current (visible) level plus any invisible
     * familiar boost. Never use this for a level *requirement* - an invisible boost does not
     * unlock content.
     */
    fun effectiveLevel(
        player: Player,
        skill: Int,
    ): Int = player.skills.getCurrentLevel(skill) + invisibleBoost(player, skill)

    /** Every familiar that gives at least one invisible skilling boost, for the ledger's checks. */
    val boostedFamiliars: Set<Int> get() = boosts.keys
}
