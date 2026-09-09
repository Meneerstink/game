package gg.rsmod.plugins.content.combat.strategy.magic

/**
 * Secondary effect applied by a [CombatSpell] when its hit lands (or, for the effect-only
 * spells such as Bind and the curse spells, when the accuracy roll succeeds).
 *
 * Sourced from the 2009scape ancient/modern spell handlers (BloodSpells, IceSpells,
 * ShadowSpells, SmokeSpells, MiasmicSpells, BindSpell, CurseSpells, TeleblockSpell) which are
 * the same-era mechanics; ticks and percentages match the RuneScape Wiki 2011 descriptions.
 */
sealed class SpellEffect {
    /** Freeze the target for [ticks] game cycles (Ice spells, Bind/Snare/Entangle). */
    data class Freeze(val ticks: Int) : SpellEffect()

    /** Poison the target starting at [damage] (Smoke spells). */
    data class Poison(val damage: Int) : SpellEffect()

    /** Heal the caster for 25% of the damage dealt (Blood spells). */
    object BloodHeal : SpellEffect()

    /** Drain the target's Attack by 10% of its base level (Shadow spells). */
    object ShadowDrain : SpellEffect()

    /** Halve the target's attack speed for [ticks] cycles, with a matching immunity (Miasmic). */
    data class Miasmic(val ticks: Int) : SpellEffect()

    /** Drain [skill] by [percent] of its base level, only if not already drained below that. */
    data class StatDrain(val skill: Int, val percent: Int) : SpellEffect()

    /** Teleport-block the target for 500 ticks (250 with Protect from Magic / Deflect Magic). */
    object Teleblock : SpellEffect()
}
