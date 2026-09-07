package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase I of the re-audit: the combat values themselves, across the whole roster.
 *
 * `SummoningCombatDefinitionTests` pins the shape of the ledger - how many familiars fight, which
 * four are defensive-only, which five do not fight at all - and then checks three named
 * representatives. That leaves the actual per-familiar numbers unaudited: a familiar could carry
 * zero lifepoints, an attack range that contradicts its style, or a ranged attack with no
 * projectile, and nothing would fail.
 *
 * Each test below enumerates the roster and **names the offending familiars**, so a failure says
 * which entries are wrong rather than only that a total moved.
 *
 * These assert *internal consistency and cache agreement*, never invented authentic values. Where
 * a real 2011 figure is not known, no figure is asserted - only that the data cannot be
 * self-contradictory in a way that would break the fight at runtime.
 */
class FamiliarCombatValueTests {
    private val fighting get() = SummoningCombatDefinitions.executableCombatValues

    /**
     * Lifepoints. A familiar that fights with zero hitpoints would die to the first hit it took,
     * or divide by zero in the healing paths, depending on which reached it first.
     */
    @Test
    fun `every fighting familiar has real lifepoints`() {
        val faults = fighting.filter { it.hitpoints <= 0 }.map { "${it.pouch.name} has ${it.hitpoints} lifepoints" }
        assertTrue(faults.isEmpty(), "${faults.size} fighting familiars have no lifepoints: $faults")
    }

    /**
     * Attack speed, as a tick count. Not an authenticity claim - the real per-familiar speeds are
     * what the ledger says - but a speed of zero would make a familiar attack every cycle and a
     * negative one would never let it attack at all. Ten ticks is a generous upper bound; nothing
     * in this revision is slower.
     */
    @Test
    fun `every fighting familiar has a usable attack speed`() {
        val faults =
            fighting.filterNot { it.attackSpeed in 1..10 }
                .map { "${it.pouch.name} attacks every ${it.attackSpeed} ticks" }
        assertTrue(faults.isEmpty(), "${faults.size} fighting familiars have an unusable attack speed: $faults")
    }

    /**
     * Attack range against attack style. A melee familiar that thinks it has range 7 would stand
     * off and swing at nothing; a ranged one with range 1 would walk into melee to shoot.
     */
    @Test
    fun `attack range agrees with attack style for every fighting familiar`() {
        val faults =
            fighting.mapNotNull { definition ->
                when {
                    definition.style == FamiliarAttackStyle.MELEE && definition.attackRange != 1 ->
                        "${definition.pouch.name} is melee but has range ${definition.attackRange}"
                    definition.style != FamiliarAttackStyle.MELEE && definition.attackRange <= 1 ->
                        "${definition.pouch.name} is ${definition.style} but has range ${definition.attackRange}"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} familiars' attack range contradicts their style: $faults")
    }

    /**
     * A ranged or magic attack has to be *visible*. Without a projectile the target simply takes
     * damage out of nowhere, which is the kind of fault that reads as "the special did nothing"
     * even when the damage landed.
     *
     * **Phoenix is a known gap and is `SOURCE_BLOCKED`.** It is `MAGIC` at range 7 with
     * `projectile = -1` and `attackGraphic = -1`, so its ordinary attack is invisible. Neither
     * value is recoverable: `NpcDef` carries no projectile field, its combat form 8576 decodes
     * nothing relevant, and the project's own research corpus has no Phoenix projectile or
     * spotanim. Inventing one is forbidden, so it is named here rather than papered over, and the
     * assertion is written as an exact set so a *second* familiar losing its attack visual still
     * fails.
     */
    @Test
    fun `every ranged or magic familiar throws something visible`() {
        val faults =
            fighting
                .filter { it.style != FamiliarAttackStyle.MELEE }
                .filter { it.projectile <= 0 && it.attackGraphic <= 0 }
                .map { it.pouch }
                .toSet()
        assertEquals(
            setOf(SummoningPouchData.PHOENIX),
            faults,
            "the set of ranged/magic familiars with no visible attack changed; only Phoenix is a known " +
                "SOURCE_BLOCKED gap, and a new entry means a projectile or graphic was lost",
        )
    }

    /**
     * Every fighting familiar's combat form must be a real npc in this cache. The combat form is a
     * separate npc from the one that follows the player, and a stale or mistyped id would leave
     * the client with nothing to render the moment a fight started.
     */
    @Test
    fun `every fighting familiar's combat form resolves in the production cache`() {
        val faults =
            fighting.mapNotNull { definition ->
                val combatNpc = definition.combatNpc ?: return@mapNotNull "${definition.pouch.name} fights with no combat npc"
                val def = DEFINITIONS.get(NpcDef::class.java, combatNpc)
                if (def.name.isNullOrBlank()) "${definition.pouch.name} combat npc $combatNpc has no name in this cache" else null
            }
        assertTrue(faults.isEmpty(), "${faults.size} familiars' combat forms do not resolve: $faults")
    }

    /**
     * The combat level shown to the player must be the one this cache's own combat-form npc
     * carries. `SummoningLedger` gates this at boot; asserting it here means a mismatch fails in
     * the test run too, without needing a server to start.
     */
    @Test
    fun `every combat level agrees with the cache's own combat-form npc, all applicable`() {
        val mismatches =
            SummoningCombatDefinitions.values.mapNotNull { definition ->
                val combatNpc = definition.combatNpc ?: return@mapNotNull null
                val expected = SummoningCatalogue[definition.pouch].combatLevel ?: return@mapNotNull null
                val actual = DEFINITIONS.get(NpcDef::class.java, combatNpc).combatLevel
                if (actual == expected) {
                    null
                } else {
                    "${definition.pouch.name}: catalogue says $expected, cache npc $combatNpc says $actual"
                }
            }
        assertTrue(mismatches.isEmpty(), "${mismatches.size} combat levels disagree with the cache: $mismatches")
    }

    /**
     * The inverse, and the one that protects the player-facing display: a familiar with no sourced
     * combat level must not be carrying combat data that would let the client show one.
     */
    @Test
    fun `no familiar fights without a sourced combat level`() {
        val faults =
            SummoningCombatDefinitions.values
                .filter { it.canFight && SummoningCatalogue[it.pouch].combatLevel == null }
                .map { it.pouch.name }
        assertTrue(faults.isEmpty(), "${faults.size} familiars fight with no sourced combat level: $faults")
    }

    /**
     * The five non-combat familiars must be inert, not merely flagged. A familiar that cannot
     * fight but still carries a max hit would be one capability-model change away from being able
     * to use it.
     */
    @Test
    fun `the five non-combat familiars carry no combat capability at all`() {
        val faults =
            SummoningCombatDefinitions.values
                .filterNot { it.canFight }
                .mapNotNull { definition ->
                    when {
                        definition.maxHit > 0 -> "${definition.pouch.name} cannot fight but has max hit ${definition.maxHit}"
                        FamiliarCapabilityTable.forNpc(definition.pouch.npc)?.supports(FamiliarAction.ATTACK) == true ->
                            "${definition.pouch.name} cannot fight but is offered the Attack action"
                        else -> null
                    }
                }
        assertTrue(faults.isEmpty(), "${faults.size} non-combat familiars carry combat capability: $faults")
    }

    /**
     * Every fighting familiar needs the animations a fight actually consumes. `isExecutable`
     * already requires an attack and a death animation, so those two are asserted with no
     * exceptions. A missing **block** animation is the one that survives that check and then shows
     * up as a familiar standing perfectly still while being hit.
     *
     * **Stranger plant is a known gap and is `SOURCE_BLOCKED`.** It has an attack (8208) and a
     * death (8209) animation but no block animation, and there is nowhere left to read one from:
     * `BasDef` carries only the body-animation set (`ready`, `walk`, `run`, `crawl`) and has no
     * block field at all, its combat form 6828 decodes nothing relevant, and the research corpus
     * has no entry for it. Named rather than invented, and asserted as an exact set so any *other*
     * familiar losing a block animation still fails.
     */
    @Test
    fun `every fighting familiar has attack and death animations, and a block animation bar one`() {
        val missingCore =
            fighting.mapNotNull { definition ->
                when {
                    definition.attackAnimation < 0 -> "${definition.pouch.name} has no attack animation"
                    definition.deathAnimation < 0 -> "${definition.pouch.name} has no death animation"
                    else -> null
                }
            }
        assertTrue(missingCore.isEmpty(), "${missingCore.size} fighting familiars cannot animate a fight: $missingCore")

        val missingBlock = fighting.filter { it.blockAnimation < 0 }.map { it.pouch }.toSet()
        assertEquals(
            setOf(SummoningPouchData.STRANGER_PLANT),
            missingBlock,
            "the set of fighting familiars with no block animation changed; only Stranger plant is a " +
                "known SOURCE_BLOCKED gap",
        )
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
