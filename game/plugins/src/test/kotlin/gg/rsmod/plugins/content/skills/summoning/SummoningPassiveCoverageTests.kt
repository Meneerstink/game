package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase N of the re-audit: passives and unique abilities, across the whole roster.
 *
 * `SummoningCompletenessTests` checks that the invisible-boost parser still recognises the
 * catalogue's own text, by asserting two named familiars are in the result. That proves the regex
 * did not rot; it does not prove the roster is fully covered, and it would not notice a familiar
 * whose ability text stopped parsing.
 *
 * The catalogue stores the 2011 Knowledge Base "Other Abilities" column **verbatim**, so it is the
 * source of truth for what each familiar is supposed to do passively. These tests compare that text
 * against what the code actually derives from it, and name the familiars where the two disagree.
 * Nothing here asserts an authentic boost value: the values are whatever the catalogue text says.
 */
class SummoningPassiveCoverageTests {
    private val roster = SummoningPouchData.values().toList()

    /** The catalogue's own flat invisible-boost form, e.g. `Mining boost (7) - invisible`. */
    private val invisibleBoostText = Regex("""^(\w+) boost \((\d+)\) - invisible$""")

    /** Familiars whose ability text advertises a flat invisible skilling boost. */
    private fun advertisesInvisibleBoost(pouch: SummoningPouchData): Boolean =
        SummoningCatalogue[pouch].abilities.any { invisibleBoostText.matchEntire(it.trim()) != null }

    /**
     * The rule that matters: every familiar the Knowledge Base says gives an invisible skilling
     * boost must actually give one, and no familiar that the text does not describe that way may
     * hand one out.
     *
     * This is the check that would have caught the original fault - roughly twenty familiars whose
     * whole purpose is a skilling boost having no effect on skilling at all - and it catches it for
     * the whole roster rather than for two named examples.
     */
    @Test
    fun `invisible boosts are derived for exactly the familiars whose text advertises one, all 78`() {
        val advertised = roster.filter { advertisesInvisibleBoost(it) }.map { it.npc }.toSet()
        val derived = SummoningBoosts.boostedFamiliars.toSet()

        val missing = (advertised - derived).map { npc -> roster.first { it.npc == npc }.name }
        val extra = (derived - advertised).map { npc -> roster.firstOrNull { it.npc == npc }?.name ?: "npc $npc" }

        assertTrue(missing.isEmpty(), "${missing.size} familiars advertise an invisible boost but get none: $missing")
        assertTrue(extra.isEmpty(), "${extra.size} familiars give an invisible boost their text does not describe: $extra")
    }

    /**
     * The boost set must not be empty and must not be everything. An empty set means the parser
     * broke; the whole roster means it started matching the percentage combat entries, which are a
     * different mechanic applied in a different place and are excluded on purpose.
     */
    @Test
    fun `the invisible boost set is a real subset of the roster`() {
        val derived = SummoningBoosts.boostedFamiliars
        assertTrue(derived.isNotEmpty(), "no familiar gives an invisible skilling boost; the parser has broken")
        assertTrue(
            derived.size < roster.size,
            "every familiar gives an invisible boost, which means the percentage combat entries are being " +
                "parsed as skilling levels",
        )
    }

    /**
     * Every boosted familiar must boost a skill that exists, by a positive amount. A zero boost
     * would be a familiar that appears to help and does not.
     */
    @Test
    fun `every derived boost names a real skill and a positive amount`() {
        val faults =
            roster.mapNotNull { pouch ->
                if (pouch.npc !in SummoningBoosts.boostedFamiliars) return@mapNotNull null
                val parsed =
                    SummoningCatalogue[pouch].abilities
                        .mapNotNull { invisibleBoostText.matchEntire(it.trim()) }
                        .map { it.groupValues[1] to it.groupValues[2].toInt() }
                when {
                    parsed.isEmpty() -> "${pouch.name} is boosted but its text parses to nothing"
                    parsed.any { it.second <= 0 } -> "${pouch.name} has a non-positive boost: $parsed"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} familiars have an unusable boost: $faults")
    }

    /**
     * The percentage combat entries must stay out. They are a bonus multiplier rather than a level
     * addition, applied somewhere else entirely, and approximating them as invisible levels would
     * silently change combat.
     */
    @Test
    fun `percentage combat abilities are never treated as invisible skilling levels`() {
        val percentageOnly =
            roster.filter { pouch ->
                val abilities = SummoningCatalogue[pouch].abilities
                abilities.any { it.contains('%') } && !advertisesInvisibleBoost(pouch)
            }
        val leaked = percentageOnly.filter { it.npc in SummoningBoosts.boostedFamiliars }.map { it.name }
        assertTrue(leaked.isEmpty(), "${leaked.size} familiars had a percentage ability parsed as levels: $leaked")
    }

    /**
     * What "defensive-only" actually means, which is worth stating because it is easy to get
     * backwards: it governs **auto-assist**, not whether the player may order an attack.
     *
     * `FamiliarCombat.assist` returns early for a `DEFENSIVE_ONLY` familiar unless the target is
     * the pawn that last hit the owner, so those four never join a fight the owner picks - they
     * only hit back. They are still combat-capable familiars with real stats, and the orb's Attack
     * command still applies to them, exactly as in 2011.
     *
     * So the rule to assert is that defensive-only implies *fightable*, and that the assist gate is
     * the thing carrying the restriction. A defensive-only familiar that could not fight at all
     * would be miscategorised - it would belong in `NONE`.
     */
    @Test
    fun `defensive-only familiars are combat-capable, and the restriction lives in the assist gate`() {
        val defensive = SummoningCombatDefinitions.values.filter { it.assistMode == FamiliarAssistMode.DEFENSIVE_ONLY }
        assertTrue(defensive.isNotEmpty(), "no familiar is defensive-only; the combat ledger has changed shape")

        val faults =
            defensive.mapNotNull { definition ->
                when {
                    !definition.canFight ->
                        "${definition.pouch.name} is defensive-only but cannot fight; it belongs in NONE"
                    FamiliarCapabilityTable.forNpc(definition.pouch.npc)?.supports(FamiliarAction.ATTACK) != true ->
                        "${definition.pouch.name} is a combat familiar but is not offered Attack"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} defensive-only familiars are miscategorised: $faults")
    }

    /**
     * The complement, and the one that protects the display: a familiar with `NONE` assist mode
     * genuinely cannot fight, so it must never be offered Attack. That is the Beaver / Macaw /
     * Magpie / Ibis / Fruit bat group.
     */
    @Test
    fun `no non-assisting familiar is offered the Attack action`() {
        val faults =
            SummoningCombatDefinitions.values
                .filter { it.assistMode == FamiliarAssistMode.NONE }
                .filter { FamiliarCapabilityTable.forNpc(it.pouch.npc)?.supports(FamiliarAction.ATTACK) == true }
                .map { it.pouch.name }
        assertTrue(faults.isEmpty(), "${faults.size} non-combat familiars are offered Attack: $faults")
    }

    /**
     * Every familiar's ability text must be reachable at all. A familiar with no catalogue row
     * would have no passives, no abilities and no Knowledge Base description, and every derivation
     * above would silently skip it.
     */
    @Test
    fun `every familiar has catalogue ability text to derive passives from, all 78`() {
        val missing = roster.filter { SummoningCatalogue.byPouch[it] == null }.map { it.name }
        assertEquals(emptyList(), missing, "${missing.size} familiars have no catalogue row")
    }
}
