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

    /**
     * Visible boosts a familiar gives when its owner talks to it (Interact). RS Wiki "Geyser titan": "When spoken with, the
     * familiar grants a 1 + 3% (rounded down) Ranged temporary level boost." (2011 Knowledge Base: "Ranged Boost (1 + 3% of
     * your level)", [SummoningCatalogue]). It is a normal temporary boost on the real level - not an invisible one: Void lists it
     * in its invisible `familiar_boosts` table but never reads a Ranged row anywhere (conform summoning 2026-10-03).
     * familiar pouch -> (skill, flat, percent of the base level).
     */
    private val INTERACT_BOOSTS = mapOf(SummoningPouchData.GEYSER_TITAN to Triple(Skills.RANGED, 1, 3))

    /** The level [interactBoost] raises [skill] (base level [base]) to for this familiar, or null when talking to it boosts nothing. */
    fun interactBoostLevel(
        pouch: SummoningPouchData,
        skill: Int,
        base: Int,
    ): Int? {
        val (boosted, flat, percent) = INTERACT_BOOSTS[pouch] ?: return null
        if (boosted != skill) return null
        return base + flat + base * percent / 100
    }

    /** Talking to the familiar: its visible boost, potion-style (from the base level, never lowering a higher boost). */
    fun interactBoost(
        player: Player,
        pouch: SummoningPouchData,
    ) {
        val skill = INTERACT_BOOSTS[pouch]?.first ?: return
        val level = interactBoostLevel(pouch, skill, player.skills.getMaxLevel(skill)) ?: return
        if (player.skills.getCurrentLevel(skill) < level) player.skills.setCurrentLevel(skill, level)
    }

    /** Every familiar that gives at least one invisible skilling boost, for the ledger's checks. */
    val boostedFamiliars: Set<Int> get() = boosts.keys
}
