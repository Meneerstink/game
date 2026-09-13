package gg.rsmod.plugins.content.combat

/**
 * RCV-010 B1: the one conversion from the donors' historical life-point unit (Void, Novite and 2011 wiki
 * figures: real HP x10) to this server's 1:1 lifepoints, hitsplats and damage.
 *
 * Every hard-coded heal, drain or damage figure copied from an x10 source goes through [fromLedger] at the
 * call site, so `MixedLifepointUnitTests` can tell a converted figure from a raw x10 literal.
 */
object LifepointUnits {
    const val LEDGER_UNITS_PER_LIFEPOINT = 10

    fun fromLedger(ledger: Int): Int = ledger / LEDGER_UNITS_PER_LIFEPOINT
}
