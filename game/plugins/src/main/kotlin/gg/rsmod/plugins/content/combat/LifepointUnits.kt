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

/**
 * Audit C-02: OSRS combat experience per point of 1:1 damage. The old strategy factors (0.4 / 0.2 / 0.133 / 0.1)
 * were the 667 figures per x10 life point and were never scaled when damage became 1:1.
 */
object CombatXpRates {
    /** Attack, Strength, Defence or Ranged on a single-skill style. */
    const val COMBAT_PER_DAMAGE = 4.0

    /** Controlled melee: 1.33 to each of Attack, Strength and Defence. */
    const val CONTROLLED_MELEE_PER_DAMAGE = 4.0 / 3.0

    /** Ranged longrange: 2 Ranged and 2 Defence. */
    const val LONGRANGE_PER_DAMAGE = 2.0

    /** Offensive magic (plus the spell's base experience). */
    const val MAGIC_PER_DAMAGE = 2.0

    /** Defensive casting: 1.33 Magic and 1 Defence (plus the spell's base experience on Magic). */
    const val DEFENSIVE_MAGIC_PER_DAMAGE = 4.0 / 3.0
    const val DEFENSIVE_DEFENCE_PER_DAMAGE = 1.0

    const val HITPOINTS_PER_DAMAGE = 4.0 / 3.0
}
