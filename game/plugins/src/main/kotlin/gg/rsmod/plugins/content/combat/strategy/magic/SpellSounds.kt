package gg.rsmod.plugins.content.combat.strategy.magic

/**
 * Combat-spell cast and impact synths. Every spell sequence and graphic in the rev-667 cache is silent (probe
 * `tools/cache667/js5.py`), so like RuneScape the server sends a cast sound to the caster and an impact (or
 * [SPLASH]) sound when the spell lands. Ids: Void donor `data/skill/magic/magic.sounds.toml` (Jagex config names,
 * pre-2007 synth ids shared by the 667 cache). Void itself reuses the Wave sounds for the Surge spells ("Surge
 * sounds are unknown"); spells absent from that table (Storm of Armadyl, Miasmic, npc-only spells) stay silent
 * rather than guessed.
 */
object SpellSounds {
    const val SPLASH = 227

    data class Sounds(val cast: Int, val impact: Int)

    private val table: Map<CombatSpell, Sounds> =
        mapOf(
            CombatSpell.WIND_STRIKE to Sounds(220, 221),
            CombatSpell.WIND_BOLT to Sounds(218, 219),
            CombatSpell.WIND_BLAST to Sounds(216, 217),
            CombatSpell.WIND_WAVE to Sounds(222, 223),
            CombatSpell.WIND_SURGE to Sounds(10334, 10333), // OSRS 4028 windsurge_cast_and_fire / 4027 windsurge_hit (tx-20260919-205526)
            CombatSpell.WATER_STRIKE to Sounds(211, 212),
            CombatSpell.WATER_BOLT to Sounds(209, 210),
            CombatSpell.WATER_BLAST to Sounds(207, 208),
            CombatSpell.WATER_WAVE to Sounds(213, 214),
            CombatSpell.WATER_SURGE to Sounds(10336, 10335), // OSRS 4030 watersurge_cast_and_fire / 4029 watersurge_hit
            CombatSpell.EARTH_STRIKE to Sounds(132, 133),
            CombatSpell.EARTH_BOLT to Sounds(130, 131),
            CombatSpell.EARTH_BLAST to Sounds(128, 129),
            CombatSpell.EARTH_WAVE to Sounds(134, 135),
            CombatSpell.EARTH_SURGE to Sounds(10331, 10332), // OSRS 4025 earthsurge_cast_and_fire / 4026 earthsurge_hit
            CombatSpell.FIRE_STRIKE to Sounds(160, 161),
            CombatSpell.FIRE_BOLT to Sounds(157, 158),
            CombatSpell.FIRE_BLAST to Sounds(155, 156),
            CombatSpell.FIRE_WAVE to Sounds(162, 163),
            CombatSpell.FIRE_SURGE to Sounds(10338, 10337), // OSRS 4032 firesurge_cast_and_fire / 4031 firesurge_hit
            CombatSpell.CRUMBLE_UNDEAD to Sounds(122, 124),
            CombatSpell.IBAN_BLAST to Sounds(162, 163),
            CombatSpell.MAGIC_DART to Sounds(1718, 174),
            CombatSpell.SARADOMIN_STRIKE to Sounds(-1, 1659),
            CombatSpell.CLAWS_OF_GUTHIX to Sounds(-1, 1653),
            CombatSpell.FLAMES_OF_ZAMORAK to Sounds(-1, 1655),
            CombatSpell.CONFUSE to Sounds(119, 121),
            CombatSpell.WEAKEN to Sounds(3011, 3010),
            CombatSpell.CURSE to Sounds(127, 126),
            CombatSpell.BIND to Sounds(101, 99),
            CombatSpell.SNARE to Sounds(3003, 3002),
            CombatSpell.ENTANGLE to Sounds(151, 152),
            CombatSpell.VULNERABILITY to Sounds(3009, 3008),
            CombatSpell.ENFEEBLE to Sounds(148, 150),
            CombatSpell.STUN to Sounds(3004, 3005),
            CombatSpell.TELEPORT_BLOCK to Sounds(202, 203),
            CombatSpell.SMOKE_RUSH to Sounds(183, 185),
            CombatSpell.SMOKE_BURST to Sounds(183, 182),
            CombatSpell.SMOKE_BLITZ to Sounds(183, 181),
            CombatSpell.SMOKE_BARRAGE to Sounds(183, 180),
            CombatSpell.SHADOW_RUSH to Sounds(178, 179),
            CombatSpell.SHADOW_BURST to Sounds(178, 177),
            CombatSpell.SHADOW_BLITZ to Sounds(178, 176),
            CombatSpell.SHADOW_BARRAGE to Sounds(178, 175),
            CombatSpell.BLOOD_RUSH to Sounds(108, 110),
            CombatSpell.BLOOD_BURST to Sounds(106, 105),
            CombatSpell.BLOOD_BLITZ to Sounds(106, 104),
            CombatSpell.BLOOD_BARRAGE to Sounds(106, 102),
            CombatSpell.ICE_RUSH to Sounds(171, 173),
            CombatSpell.ICE_BURST to Sounds(171, 170),
            CombatSpell.ICE_BLITZ to Sounds(171, 169),
            CombatSpell.ICE_BARRAGE to Sounds(171, 169),
        )

    fun of(spell: CombatSpell): Sounds? = table[spell]
}
