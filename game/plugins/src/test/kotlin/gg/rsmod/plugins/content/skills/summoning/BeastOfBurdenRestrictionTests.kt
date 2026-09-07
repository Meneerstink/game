package gg.rsmod.plugins.content.skills.summoning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase G of the re-audit: the beast-of-burden **rules**, across the whole roster.
 *
 * `BeastOfBurdenTests` is already state-based and covers deposit, withdrawal, partial transfers,
 * per-familiar containers and the essence restriction by example. What it does not do is check the
 * rules that have to hold for *every* carrier at once - and those are precisely the rules the two
 * Summoning surfaces read when they decide whether to draw a Take BoB button.
 *
 * Every assertion here is derived from the sourced tables. Nothing is hand-listed per familiar, so
 * a capability can never drift away from the data it came from.
 */
class BeastOfBurdenRestrictionTests {
    private val roster = SummoningPouchData.values().toList()

    private fun storage(pouch: SummoningPouchData) = BeastOfBurden.storageFor(pouch)

    /**
     * The rule the owner's Steel titan and Unicorn reports both came down to: a familiar that
     * cannot carry must not be offered Take BoB anywhere. Asserted from the storage table against
     * the capability model, for all 78, so the two can never disagree.
     */
    @Test
    fun `Take BoB is offered exactly to the familiars that can carry, all 78`() {
        val faults =
            roster.mapNotNull { pouch ->
                val carries = storage(pouch) != null
                val offered = FamiliarCapabilityTable.forNpc(pouch.npc)?.supports(FamiliarAction.TAKE_BOB) == true
                when {
                    carries && !offered -> "${pouch.name} can carry but is not offered Take BoB"
                    !carries && offered -> "${pouch.name} cannot carry but is offered Take BoB"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} familiars disagree about Take BoB: $faults")
    }

    /** A carrier with no room would offer a button that can never do anything. */
    @Test
    fun `every carrier has real capacity, all applicable`() {
        val faults =
            roster.mapNotNull { pouch ->
                val key = storage(pouch)?.key ?: return@mapNotNull null
                if (key.capacity <= 0) "${pouch.name} carries ${key.capacity} items" else null
            }
        assertTrue(faults.isEmpty(), "${faults.size} carriers have no capacity: $faults")
    }

    /**
     * The two carrying contracts must stay distinct and exhaustive. A beast of burden accepts
     * deposits; a forager is withdraw-only. A carrier that was somehow both, or neither, would
     * fall through the capability model's `isBeastOfBurden` / `isForager` split.
     */
    @Test
    fun `every carrier is either a beast of burden or a forager, never both, all applicable`() {
        val faults =
            roster.mapNotNull { pouch ->
                val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc) ?: return@mapNotNull null
                if (!capabilities.carries) {
                    return@mapNotNull if (capabilities.isBeastOfBurden || capabilities.isForager) {
                        "${pouch.name} does not carry but claims a carrying contract"
                    } else {
                        null
                    }
                }
                when {
                    capabilities.isBeastOfBurden && capabilities.isForager -> "${pouch.name} is both a BoB and a forager"
                    !capabilities.isBeastOfBurden && !capabilities.isForager -> "${pouch.name} carries but is neither"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} carriers have a broken carrying contract: $faults")
    }

    /**
     * Container keys must be unique per familiar. Two familiars sharing one key would share their
     * cargo, which is a duplication and loss bug at the same time: deposit into one, withdraw from
     * the other.
     */
    @Test
    fun `no two familiars share a beast of burden container`() {
        val shared =
            roster.mapNotNull { pouch -> storage(pouch)?.key?.let { it to pouch } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
                .map { (key, pouches) -> "${key.name} is shared by ${pouches.map { it.name }}" }
        assertTrue(shared.isEmpty(), "familiars sharing a cargo container: $shared")
    }

    /**
     * The essence-only restriction belongs to the three abyssal carriers and nobody else. Pinned as
     * an exact set: a fourth familiar silently gaining it would refuse every ordinary deposit, and
     * one of these three losing it would let them carry anything.
     */
    @Test
    fun `only the three abyssal carriers are essence-restricted`() {
        val restricted = roster.filter { storage(it)?.essenceOnly == true }.toSet()
        assertEquals(
            setOf(
                SummoningPouchData.ABYSSAL_PARASITE,
                SummoningPouchData.ABYSSAL_LURKER,
                SummoningPouchData.ABYSSAL_TITAN,
            ),
            restricted,
            "the essence-only carrier set changed",
        )
    }

    /**
     * Foragers are withdraw-only and beasts of burden are not. This is the flag the deposit path
     * checks, so a beast of burden marked withdraw-only would silently refuse every deposit while
     * still drawing a Take BoB button.
     */
    @Test
    fun `withdraw-only is set for every forager and no beast of burden, all applicable`() {
        val faults =
            roster.mapNotNull { pouch ->
                val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc) ?: return@mapNotNull null
                val withdrawOnly = storage(pouch)?.withdrawOnly ?: return@mapNotNull null
                when {
                    capabilities.isForager && !withdrawOnly -> "${pouch.name} is a forager but accepts deposits"
                    capabilities.isBeastOfBurden && withdrawOnly -> "${pouch.name} is a BoB but is withdraw-only"
                    else -> null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} carriers have the wrong deposit direction: $faults")
    }

    /**
     * A carrier's capacity must be one the Familiar Inventory window can actually draw. Interface
     * 671's grid is 6x5 = 30 cells, so anything above 30 would be cargo the player could put in and
     * never see.
     */
    @Test
    fun `no carrier holds more than the Familiar Inventory window can draw, all applicable`() {
        val faults =
            roster.mapNotNull { pouch ->
                val key = storage(pouch)?.key ?: return@mapNotNull null
                if (key.capacity > FAMILIAR_INVENTORY_CELLS) {
                    "${pouch.name} carries ${key.capacity}, more than interface 671's $FAMILIAR_INVENTORY_CELLS cells"
                } else {
                    null
                }
            }
        assertTrue(faults.isEmpty(), "${faults.size} carriers exceed the window: $faults")
    }

    companion object {
        /** Interface 671's item grid: 6 columns by 5 rows. */
        private const val FAMILIAR_INVENTORY_CELLS = 30
    }
}
