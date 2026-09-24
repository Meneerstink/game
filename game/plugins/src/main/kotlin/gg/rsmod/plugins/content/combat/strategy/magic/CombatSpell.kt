package gg.rsmod.plugins.content.combat.strategy.magic

import gg.rsmod.game.model.Graphic
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items

private val ZURIELS_STAVES = intArrayOf(Items.ZURIELS_STAFF, Items.ZURIELS_STAFF_DEG, Items.CORRUPT_ZURIELS_STAFF, Items.CORRUPT_ZURIELS_STAFF_DEG)
private const val ZURIELS_MESSAGE = "You need to be wielding Zuriel's staff to cast this spell."

/**
 * @author Tom <rspsmods@gmail.com>
 */
enum class CombatSpell(
    val uniqueId: Int,
    val componentId: Int,
    val maxHit: Int,
    val castGfx: Graphic?,
    val castAnimation: Array<Int>,
    val projectile: Int,
    val secondProjectile: Int = -1,
    val thirdProjectile: Int = -1,
    val impactGfx: Graphic?,
    val autoCastId: Int,
    val experience: Double = 0.0,
    /** Spellbook interface the spell lives in: 192 standard, 193 Ancient Magicks. */
    val interfaceId: Int = 192,
    /** Burst/Barrage: hit every pawn within 1 tile of the target when both are in a multi-way area. */
    val multiTarget: Boolean = false,
    /** Secondary effect applied on a successful cast. */
    val effect: SpellEffect? = null,
    /** When non-empty the caster must wield one of these weapons (Iban Blast, Magic Dart, Miasmic, Storm of Armadyl). */
    val requiredWeapons: IntArray = intArrayOf(),
    /** Weapon-requirement failure message. */
    val requiredWeaponMessage: String = "",
    /** Effect-only spells (Bind, Confuse, Teleport Block) roll accuracy but never show a damage hit. */
    val damaging: Boolean = true,
    /** Delay (cycles) before the impact graphic/hit when the spell has no projectile; -1 = use projectile lifespan. */
    val fixedHitDelay: Int = -1,
) {
    /**
     * Standard.
     */

    WIND_RUSH(
        uniqueId = 3759,
        componentId = 98,
        maxHit = 1,
        castGfx = Graphic(Gfx.WIND_SPELL_CAST, 22),
        castAnimation = arrayOf(Anims.WIND_SPELL, Anims.WIND_SPELL_WITH_STAFF),
        projectile = Gfx.WIND_RUSH_PROJ,
        impactGfx = Graphic(Gfx.WIND_RUSH_IMPACT, 32),
        autoCastId = 143,
        experience = 0.2,
    ),

    WIND_STRIKE(
        uniqueId = 15,
        componentId = 25,
        maxHit = 2,
        castGfx = Graphic(3080 /* OSRS 90 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3081 /* OSRS 91 */,
        impactGfx = Graphic(3082 /* OSRS 92 */, 32),
        autoCastId = 3,
        experience = 5.5,
    ),

    WATER_STRIKE(
        uniqueId = 17,
        componentId = 28,
        maxHit = 4,
        castGfx = Graphic(3083 /* OSRS 93 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3084 /* OSRS 94 */,
        impactGfx = Graphic(3085 /* OSRS 95 */, 32),
        autoCastId = 5,
        experience = 7.5,
    ),

    EARTH_STRIKE(
        uniqueId = 19,
        componentId = 30,
        maxHit = 6,
        castGfx = Graphic(3086 /* OSRS 96 */, -4),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3087 /* OSRS 97 */,
        impactGfx = Graphic(3088 /* OSRS 98 */, 60),
        autoCastId = 7,
        experience = 9.5,
    ),

    FIRE_STRIKE(
        uniqueId = 71,
        componentId = 32,
        maxHit = 8,
        castGfx = Graphic(3089 /* OSRS 99 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3090 /* OSRS 100 */,
        impactGfx = Graphic(3091 /* OSRS 101 */, 32),
        autoCastId = 9,
        experience = 11.5,
    ),

    WIND_BOLT(
        uniqueId = 73,
        componentId = 34,
        maxHit = 9,
        castGfx = Graphic(3092 /* OSRS 117 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3093 /* OSRS 118 */,
        impactGfx = Graphic(3094 /* OSRS 119 */, 32),
        autoCastId = 11,
        experience = 13.5,
    ),

    WATER_BOLT(
        uniqueId = 76,
        componentId = 39,
        maxHit = 10,
        castGfx = Graphic(3095 /* OSRS 120 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3096 /* OSRS 121 */,
        impactGfx = Graphic(3097 /* OSRS 122 */, 32),
        autoCastId = 13,
        experience = 16.5,
    ),

    EARTH_BOLT(
        uniqueId = 79,
        componentId = 42,
        maxHit = 11,
        castGfx = Graphic(3098 /* OSRS 123 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3099 /* OSRS 124 */,
        impactGfx = Graphic(3100 /* OSRS 125 */, 32),
        autoCastId = 15,
        experience = 19.5,
    ),

    FIRE_BOLT(
        uniqueId = 82,
        componentId = 45,
        maxHit = 12,
        castGfx = Graphic(3101 /* OSRS 126 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3102 /* OSRS 127 */,
        impactGfx = Graphic(3103 /* OSRS 128 */, 32),
        autoCastId = 17,
        experience = 22.5,
    ),

    WIND_BLAST(
        uniqueId = 85,
        componentId = 49,
        maxHit = 13,
        castGfx = Graphic(3104 /* OSRS 132 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3105 /* OSRS 133 */,
        impactGfx = Graphic(3106 /* OSRS 134 */, 32),
        autoCastId = 19,
        experience = 25.5,
    ),

    WATER_BLAST(
        uniqueId = 88,
        componentId = 52,
        maxHit = 14,
        castGfx = Graphic(3107 /* OSRS 135 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3108 /* OSRS 136 */,
        impactGfx = Graphic(3109 /* OSRS 137 */, 32),
        autoCastId = 21,
        experience = 28.5,
    ),

    EARTH_BLAST(
        uniqueId = 90,
        componentId = 58,
        maxHit = 15,
        castGfx = Graphic(3110 /* OSRS 138 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3111 /* OSRS 139 */,
        impactGfx = Graphic(3112 /* OSRS 140 */, 32),
        autoCastId = 23,
        experience = 31.5,
    ),

    FIRE_BLAST(
        uniqueId = 95,
        componentId = 63,
        maxHit = 16,
        castGfx = Graphic(3113 /* OSRS 129 */, 22),
        castAnimation = arrayOf(15754, 15774),
        projectile = 3114 /* OSRS 130 */,
        impactGfx = Graphic(3115 /* OSRS 131 */, 32),
        autoCastId = 25,
        experience = 34.5,
    ),

    SARADOMIN_STRIKE(
        uniqueId = 501,
        componentId = 66,
        maxHit = 20,
        castGfx = null,
        castAnimation = arrayOf(15773, 15773),
        projectile = -1, // OSRS: god spells have no projectile
        impactGfx = Graphic(3159 /* OSRS 76 */, 100),
        autoCastId = 26,
        experience = 35.0,
    ),

    CLAWS_OF_GUTHIX(
        uniqueId = 502,
        componentId = 67,
        maxHit = 20,
        castGfx = null,
        castAnimation = arrayOf(15773, 15773, Anims.CLAWS_OF_GUTHIX),
        projectile = -1, // OSRS: god spells have no projectile
        impactGfx = Graphic(3160 /* OSRS 77 */, 100),
        autoCastId = 28,
        experience = 35.0,
    ),

    FLAMES_OF_ZAMORAK(
        uniqueId = 503,
        componentId = 68,
        maxHit = 20,
        castGfx = null,
        castAnimation = arrayOf(15773, 15773),
        projectile = -1, // OSRS: god spells have no projectile
        impactGfx = Graphic(3161 /* OSRS 78 */, 0),
        autoCastId = 30,
        experience = 35.0,
    ),

    WIND_WAVE(
        uniqueId = 96,
        componentId = 70,
        maxHit = 17,
        castGfx = Graphic(3116 /* OSRS 158 */, 22),
        castAnimation = arrayOf(15755, 15756),
        projectile = 3117 /* OSRS 159 */,
        impactGfx = Graphic(3118 /* OSRS 160 */, height = 32),
        autoCastId = 27,
        experience = 36.0,
    ),

    WATER_WAVE(
        uniqueId = 98,
        componentId = 73,
        maxHit = 18,
        castGfx = Graphic(3119 /* OSRS 161 */, 22),
        castAnimation = arrayOf(15755, 15756),
        projectile = 3120 /* OSRS 162 */,
        impactGfx = Graphic(3121 /* OSRS 163 */, 32),
        autoCastId = 29,
        experience = 37.5,
    ),

    EARTH_WAVE(
        uniqueId = 101,
        componentId = 77,
        maxHit = 19,
        castGfx = Graphic(3122 /* OSRS 164 */, 22),
        castAnimation = arrayOf(15755, 15756),
        projectile = 3123 /* OSRS 165 */,
        impactGfx = Graphic(3124 /* OSRS 166 */, 32),
        autoCastId = 31,
        experience = 40.0,
    ),

    FIRE_WAVE(
        uniqueId = 102,
        componentId = 80,
        maxHit = 20,
        castGfx = Graphic(3125 /* OSRS 155 */, 22),
        castAnimation = arrayOf(15755, 15756),
        projectile = 3126 /* OSRS 156 */,
        impactGfx = Graphic(3127 /* OSRS 157 */, 32),
        autoCastId = 33,
        experience = 42.5,
    ),

    /*
     * The four Surges (owner 2026-09-20: "we need exact OSRS surge animations"; the fire and water ones were still
     * showing 667 art).
     *
     * SOURCE: RuneLite `gameval/SpotanimID.java` WINDSURGE / WATERSURGE / EARTHSURGE / FIRESURGE _CASTING, _TRAVEL
     * and _IMPACT (OSRS 1455-1466), imported into both caches by `OsrsFxImportTool` batch "surge" and listed in
     * [gg.rsmod.plugins.content.items.osrs.OsrsGfx]; the cast sequence is OSRS 7855 HUMAN_CAST_SURGE.
     *
     * Before this, only Earth had a real 667 surge cast graphic: Water and Fire fell back to the generic element
     * cast graphic and Wind was firing WIND_WAVE_PROJ - the wave projectile - which is why they read as "the old
     * build". All four now use the OSRS casting / travel / impact set.
     *
     * ADAPTED: heights are not carried by the cache (see OsrsGfx), so the established 22 for a cast stays. The
     * impact height is 32 for all four; Wind previously used 96, but the OSRS impact spotanims are one shared
     * model and sequence recoloured per element, so one height is right for all of them.
     *
     * Fire Surge fires ONE projectile, like the other three. The 667 base gave every Fire spell a three-projectile
     * volley (`secondProjectile` / `thirdProjectile`, still used by Fire Blast and Fire Wave); OSRS does not - each
     * surge sends a single FIRESURGE_TRAVEL. Keeping the volley was wrong and the owner called it out on
     * 2026-09-20 ("its shooting another projectile ... should be the fire surge from osrs").
     */
    WIND_SURGE(
        uniqueId = 815,
        componentId = 84,
        maxHit = 21,
        castGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WIND_SURGE_CASTING, 22),
        castAnimation = arrayOf(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE, gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE), // OSRS 7855 HUMAN_CAST_SURGE: every Surge, staff or not
        projectile = gg.rsmod.plugins.content.items.osrs.OsrsGfx.WIND_SURGE_TRAVEL,
        impactGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WIND_SURGE_IMPACT, 32),
        autoCastId = 47,
        experience = 44.5,
    ),

    WATER_SURGE(
        uniqueId = 816,
        componentId = 87,
        maxHit = 22,
        castGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WATER_SURGE_CASTING, 22),
        castAnimation = arrayOf(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE, gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE), // OSRS 7855 HUMAN_CAST_SURGE: every Surge, staff or not
        projectile = gg.rsmod.plugins.content.items.osrs.OsrsGfx.WATER_SURGE_TRAVEL,
        impactGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.WATER_SURGE_IMPACT, 32),
        autoCastId = 49,
        experience = 46.5,
    ),

    EARTH_SURGE(
        uniqueId = 817,
        componentId = 89,
        maxHit = 23,
        castGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.EARTH_SURGE_CASTING, 22),
        castAnimation = arrayOf(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE, gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE), // OSRS 7855 HUMAN_CAST_SURGE: every Surge, staff or not
        projectile = gg.rsmod.plugins.content.items.osrs.OsrsGfx.EARTH_SURGE_TRAVEL,
        impactGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.EARTH_SURGE_IMPACT, 32),
        autoCastId = 51,
        experience = 48.2,
    ),

    FIRE_SURGE(
        uniqueId = 818,
        componentId = 91,
        maxHit = 24,
        castGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.FIRE_SURGE_CASTING, 22),
        castAnimation = arrayOf(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE, gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CAST_SURGE), // OSRS 7855 HUMAN_CAST_SURGE: every Surge, staff or not
        projectile = gg.rsmod.plugins.content.items.osrs.OsrsGfx.FIRE_SURGE_TRAVEL,
        impactGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.FIRE_SURGE_IMPACT, 32),
        autoCastId = 53,
        experience = 50.5,
    ),

    /**
     * Standard spellbook: effect spells, staff spells and Teleport Block.
     *
     * Animation/graphic/projectile ids sourced from the 2009scape modern spell handlers
     * (BindSpell, CurseSpells, CrumbleUndead, IbanBlast, MagicDart, TeleblockSpell) and cross-checked
     * against Matrix 718 PlayerCombat; levels/runes come from the production cache (SpellbookData).
     * Max hits and experience follow the 2011 RuneScape Wiki (Ice Barrage 30, Iban Blast 25, ...).
     */

    CONFUSE(
        uniqueId = 16,
        componentId = 26,
        maxHit = 0,
        castGfx = Graphic(3128 /* OSRS 102 */, 96),
        castAnimation = arrayOf(15759, 15760),
        projectile = 3129 /* OSRS 103 */,
        impactGfx = Graphic(3130 /* OSRS 104 */, 96),
        autoCastId = -1,
        experience = 13.0,
        effect = SpellEffect.StatDrain(Skills.ATTACK, 5),
        damaging = false,
    ),
    WEAKEN(
        uniqueId = 20,
        componentId = 31,
        maxHit = 0,
        castGfx = Graphic(3131 /* OSRS 105 */, 96),
        castAnimation = arrayOf(15761, 15762),
        projectile = 3132 /* OSRS 106 */,
        impactGfx = Graphic(3133 /* OSRS 107 */, 96),
        autoCastId = -1,
        experience = 21.0,
        effect = SpellEffect.StatDrain(Skills.STRENGTH, 5),
        damaging = false,
    ),
    CURSE(
        uniqueId = 24,
        componentId = 35,
        maxHit = 0,
        castGfx = Graphic(3134 /* OSRS 108 */, 96),
        castAnimation = arrayOf(15763, 15764),
        projectile = 3135 /* OSRS 109 */,
        impactGfx = Graphic(3136 /* OSRS 110 */, 96),
        autoCastId = -1,
        experience = 29.0,
        effect = SpellEffect.StatDrain(Skills.DEFENCE, 5),
        damaging = false,
    ),
    BIND(
        uniqueId = 319,
        componentId = 36,
        maxHit = 0,
        castGfx = Graphic(3146 /* OSRS 177 */, 96),
        castAnimation = arrayOf(15757, 15758), // OSRS 710 HUMAN_CASTENTANGLE / 1161 HUMAN_CASTENTANGLE_STAFF (imported, batch standardspellseq)
        projectile = 3147 /* OSRS 178 */,
        impactGfx = Graphic(3150 /* OSRS 181 */, 96),
        autoCastId = -1,
        experience = 30.0,
        effect = SpellEffect.Freeze(8),
        damaging = false,
    ),
    SNARE(
        uniqueId = 320,
        componentId = 55,
        maxHit = 0,
        castGfx = Graphic(3146 /* OSRS 177 */, 96),
        castAnimation = arrayOf(15757, 15758), // OSRS 710 HUMAN_CASTENTANGLE / 1161 HUMAN_CASTENTANGLE_STAFF (imported, batch standardspellseq)
        projectile = 3147 /* OSRS 178 */,
        impactGfx = Graphic(3149 /* OSRS 180 */, 96),
        autoCastId = -1,
        experience = 60.0,
        effect = SpellEffect.Freeze(16),
        damaging = false,
    ),
    ENTANGLE(
        uniqueId = 321,
        componentId = 81,
        maxHit = 0,
        castGfx = Graphic(3146 /* OSRS 177 */, 96),
        castAnimation = arrayOf(15757, 15758), // OSRS 710 HUMAN_CASTENTANGLE / 1161 HUMAN_CASTENTANGLE_STAFF (imported, batch standardspellseq)
        projectile = 3147 /* OSRS 178 */,
        impactGfx = Graphic(3148 /* OSRS 179 */, 96),
        autoCastId = -1,
        experience = 89.0,
        effect = SpellEffect.Freeze(24),
        damaging = false,
    ),
    VULNERABILITY(
        uniqueId = 56,
        componentId = 75,
        maxHit = 0,
        castGfx = Graphic(3137 /* OSRS 167 */, 96),
        castAnimation = arrayOf(729, 729),
        projectile = 3138 /* OSRS 168 */,
        impactGfx = Graphic(3139 /* OSRS 169 */, 96),
        autoCastId = -1,
        experience = 76.0,
        effect = SpellEffect.StatDrain(Skills.DEFENCE, 10),
        damaging = false,
    ),
    ENFEEBLE(
        uniqueId = 57,
        componentId = 78,
        maxHit = 0,
        castGfx = Graphic(3140 /* OSRS 170 */, 96),
        castAnimation = arrayOf(15765, 15766),
        projectile = 3141 /* OSRS 171 */,
        impactGfx = Graphic(3142 /* OSRS 172 */, 96),
        autoCastId = -1,
        experience = 83.0,
        effect = SpellEffect.StatDrain(Skills.STRENGTH, 10),
        damaging = false,
    ),
    STUN(
        uniqueId = 58,
        componentId = 82,
        maxHit = 0,
        castGfx = Graphic(3143 /* OSRS 173 */, 96),
        castAnimation = arrayOf(15767, 15768),
        projectile = 3144 /* OSRS 174 */,
        impactGfx = Graphic(3145 /* OSRS 175 */, 96),
        autoCastId = -1,
        experience = 90.0,
        effect = SpellEffect.StatDrain(Skills.ATTACK, 10),
        damaging = false,
    ),
    TELEPORT_BLOCK(
        uniqueId = 1565,
        componentId = 86,
        maxHit = 0,
        castGfx = null, // OSRS Tele Block has no casting spotanim (owner 2026-09-22: "osrs animations exactly")
        castAnimation = arrayOf(gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CASTING_TELE_BLOCK, gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_CASTING_TELE_BLOCK_STAFF), // OSRS 1819 / 1820 (owner 2026-09-19)
        projectile = gg.rsmod.plugins.content.items.osrs.OsrsGfx.TELE_BLOCK_TRAVEL,
        impactGfx = Graphic(gg.rsmod.plugins.content.items.osrs.OsrsGfx.TELE_BLOCK_IMPACT, 0),
        autoCastId = -1,
        experience = 80.0,
        effect = SpellEffect.Teleblock,
        damaging = false,
    ),
    CRUMBLE_UNDEAD(
        uniqueId = 34,
        componentId = 47,
        maxHit = 15,
        castGfx = Graphic(3151 /* OSRS 145 */, 96),
        castAnimation = arrayOf(15769, 15770),
        projectile = 3152 /* OSRS 146 */,
        impactGfx = Graphic(3153 /* OSRS 147 */, 96),
        autoCastId = 35,
        experience = 24.5,
    ),
    IBAN_BLAST(
        uniqueId = 53,
        componentId = 54,
        maxHit = 25,
        castGfx = Graphic(3154 /* OSRS 87 */, 96),
        castAnimation = arrayOf(15771, 15771),
        projectile = 3155 /* OSRS 88 */,
        impactGfx = Graphic(3156 /* OSRS 89 */, 96),
        autoCastId = 45,
        experience = 30.0,
        requiredWeapons = intArrayOf(Items.IBANS_STAFF, Items.IBANS_STAFF_1410),
        requiredWeaponMessage = "You need to be wielding Iban's staff to cast this spell.",
    ),
    MAGIC_DART(
        uniqueId = 324,
        componentId = 56,
        maxHit = 19,
        castGfx = null,
        castAnimation = arrayOf(15772, 15772),
        projectile = 3157 /* OSRS 328 */,
        impactGfx = Graphic(3158 /* OSRS 329 */, 96),
        autoCastId = 37,
        experience = 30.0,
        // OSRS Wiki "Staff of the Dead": it can autocast Magic Dart; the toxic staff "shares the same features".
        // OSRS Wiki "Staff of Balance": autocast "Includes Crumble Undead, Magic Dart, and Claws of Guthix".
        requiredWeapons = intArrayOf(Items.SLAYERS_STAFF, Items.STAFF_OF_LIGHT, Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD, Items.STAFF_OF_BALANCE),
        requiredWeaponMessage = "You need to be wielding a slayer's staff or staff of light to cast this spell.",
    ),
    STORM_OF_ARMADYL(
        uniqueId = 7699,
        componentId = 99,
        maxHit = 16,
        castGfx = Graphic(457, 0),
        castAnimation = arrayOf(10546, 10546),
        projectile = 1019,
        impactGfx = Graphic(1019, 0),
        autoCastId = 145,
        experience = 70.0,
    ),

    /**
     * Ancient Magicks (interface 193).
     *
     * Animation/graphic/projectile ids from the 2009scape IceSpells/BloodSpells/ShadowSpells/
     * SmokeSpells/MiasmicSpells handlers (same ids as rev 667), autocast ids from Matrix 718
     * CombatDefinitions.getSpellAutoCastConfigValue, max hits/xp from the 2011 RuneScape Wiki.
     */

    SMOKE_RUSH(
        uniqueId = 329,
        componentId = 28,
        maxHit = 13, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 384,
        impactGfx = Graphic(385, 96),
        autoCastId = 63,
        experience = 30.0,
        interfaceId = 193,
        effect = SpellEffect.Poison(2),
    ),
    SHADOW_RUSH(
        uniqueId = 337,
        componentId = 32,
        maxHit = 14, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 378,
        impactGfx = Graphic(379, 96),
        autoCastId = 65,
        experience = 31.0,
        interfaceId = 193,
        effect = SpellEffect.ShadowDrain,
    ),
    BLOOD_RUSH(
        uniqueId = 333,
        componentId = 24,
        maxHit = 15, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 372,
        impactGfx = Graphic(373, 96),
        autoCastId = 67,
        experience = 33.0,
        interfaceId = 193,
        effect = SpellEffect.BloodHeal,
    ),
    ICE_RUSH(
        uniqueId = 325,
        componentId = 20,
        maxHit = 16, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 360,
        impactGfx = Graphic(361, 96),
        autoCastId = 69,
        experience = 34.0,
        interfaceId = 193,
        effect = SpellEffect.Freeze(8),
    ),
    SMOKE_BURST(
        uniqueId = 330,
        componentId = 30,
        maxHit = 17, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = 386,
        impactGfx = Graphic(387, 0),
        autoCastId = 71,
        experience = 36.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Poison(2),
    ),
    SHADOW_BURST(
        uniqueId = 338,
        componentId = 34,
        maxHit = 18, // OSRS Wiki "Ancient Magicks" (2026-09-22); the 2011 value was 2 higher
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = 380,
        impactGfx = Graphic(381, 0),
        autoCastId = 73,
        experience = 37.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.ShadowDrain,
    ),
    BLOOD_BURST(
        uniqueId = 334,
        componentId = 26,
        maxHit = 21,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = -1,
        impactGfx = Graphic(376, 0),
        autoCastId = 75,
        experience = 39.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.BloodHeal,
        fixedHitDelay = 2,
    ),
    ICE_BURST(
        uniqueId = 326,
        componentId = 22,
        maxHit = 22,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = 362,
        impactGfx = Graphic(363, 0),
        autoCastId = 77,
        experience = 40.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Freeze(16),
    ),
    SMOKE_BLITZ(
        uniqueId = 331,
        componentId = 29,
        maxHit = 23,
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 389,
        impactGfx = Graphic(388, 96),
        autoCastId = 79,
        experience = 42.0,
        interfaceId = 193,
        effect = SpellEffect.Poison(4),
    ),
    SHADOW_BLITZ(
        uniqueId = 339,
        componentId = 33,
        maxHit = 24,
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = -1,
        impactGfx = Graphic(382, 96),
        autoCastId = 81,
        experience = 43.0,
        interfaceId = 193,
        effect = SpellEffect.ShadowDrain,
        fixedHitDelay = 2,
    ),
    BLOOD_BLITZ(
        uniqueId = 335,
        componentId = 25,
        maxHit = 25,
        castGfx = null,
        castAnimation = arrayOf(1978, 1978),
        projectile = 374,
        impactGfx = Graphic(375, 96),
        autoCastId = 83,
        experience = 45.0,
        interfaceId = 193,
        effect = SpellEffect.BloodHeal,
    ),
    ICE_BLITZ(
        uniqueId = 327,
        componentId = 21,
        maxHit = 26,
        castGfx = Graphic(366, 96),
        castAnimation = arrayOf(1978, 1978),
        projectile = -1,
        impactGfx = Graphic(367, 96),
        autoCastId = 85,
        experience = 46.0,
        interfaceId = 193,
        effect = SpellEffect.Freeze(24),
        fixedHitDelay = 2,
    ),
    SMOKE_BARRAGE(
        uniqueId = 332,
        componentId = 31,
        maxHit = 27,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = 391,
        impactGfx = Graphic(390, 0),
        autoCastId = 87,
        experience = 48.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Poison(4),
    ),
    SHADOW_BARRAGE(
        uniqueId = 340,
        componentId = 35,
        maxHit = 28,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = -1,
        impactGfx = Graphic(383, 0),
        autoCastId = 89,
        experience = 49.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.ShadowDrain,
        fixedHitDelay = 2,
    ),
    BLOOD_BARRAGE(
        uniqueId = 336,
        componentId = 27,
        maxHit = 29,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = -1,
        impactGfx = Graphic(377, 0),
        autoCastId = 91,
        experience = 51.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.BloodHeal,
        fixedHitDelay = 2,
    ),
    ICE_BARRAGE(
        uniqueId = 328,
        componentId = 23,
        maxHit = 30,
        castGfx = null,
        castAnimation = arrayOf(1979, 1979),
        projectile = 368,
        impactGfx = Graphic(369, 0),
        autoCastId = 93,
        experience = 52.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Freeze(32),
    ),
    MIASMIC_RUSH(
        uniqueId = 1568,
        componentId = 36,
        maxHit = 20,
        castGfx = Graphic(1845, 0),
        castAnimation = arrayOf(10513, 10513),
        projectile = 1846,
        impactGfx = Graphic(1847, 40),
        autoCastId = 95,
        experience = 36.0,
        interfaceId = 193,
        effect = SpellEffect.Miasmic(20),
        requiredWeapons = ZURIELS_STAVES,
        requiredWeaponMessage = ZURIELS_MESSAGE,
    ),
    MIASMIC_BURST(
        uniqueId = 1569,
        componentId = 38,
        maxHit = 24,
        castGfx = Graphic(1848, 0),
        castAnimation = arrayOf(10516, 10516),
        projectile = -1,
        impactGfx = Graphic(1849, 20),
        autoCastId = 97,
        experience = 42.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Miasmic(40),
        requiredWeapons = ZURIELS_STAVES,
        requiredWeaponMessage = ZURIELS_MESSAGE,
        fixedHitDelay = 2,
    ),
    MIASMIC_BLITZ(
        uniqueId = 1567,
        componentId = 37,
        maxHit = 28,
        castGfx = Graphic(1850, 15),
        castAnimation = arrayOf(10524, 10524),
        projectile = 1852,
        impactGfx = Graphic(1851, 0),
        autoCastId = 99,
        experience = 48.0,
        interfaceId = 193,
        effect = SpellEffect.Miasmic(60),
        requiredWeapons = ZURIELS_STAVES,
        requiredWeaponMessage = ZURIELS_MESSAGE,
    ),
    MIASMIC_BARRAGE(
        uniqueId = 1566,
        componentId = 39,
        maxHit = 32,
        castGfx = Graphic(1853, 0),
        castAnimation = arrayOf(10518, 10518),
        projectile = -1,
        impactGfx = Graphic(1854, 0),
        autoCastId = 101,
        experience = 54.0,
        interfaceId = 193,
        multiTarget = true,
        effect = SpellEffect.Miasmic(80),
        requiredWeapons = ZURIELS_STAVES,
        requiredWeaponMessage = ZURIELS_MESSAGE,
        fixedHitDelay = 2,
    ),

    /**
     * NPC Spells
     */

    WEAK_FIRE_BLAST(
        uniqueId = 995,
        componentId = -1,
        maxHit = 8,
        castGfx = Graphic(Gfx.FIRE_SPELL_CAST, 22),
        castAnimation = arrayOf(Anims.FIRE_SPELL, Anims.FIRE_SPELL_WITH_STAFF),
        projectile = Gfx.FIRE_BLAST_PROJ,
        secondProjectile = Gfx.FIRE_BLAST_PROJ_2,
        impactGfx = Graphic(Gfx.FIRE_BLAST_IMPACT, 32),
        autoCastId = -1,
        experience = 0.0,
    ),
    WARLOCK_SKELETON_EARTH_STRIKE(
        uniqueId = 996,
        componentId = -1,
        maxHit = 6,
        castGfx = Graphic(Gfx.EARTH_SPELL_CAST, -4),
        castAnimation = arrayOf(Anims.WARLOCK_WEAK_EARTH_STRIKE),
        projectile = Gfx.EARTH_STRIKE_PROJ,
        impactGfx = Graphic(Gfx.EARTH_STRIKE_IMPACT, 60),
        // NPC-only spell: it used to share Earth Strike's autocast id 7, so an autocast lookup by id could pick it.
        autoCastId = -1,
        experience = 9.5,
    ),

    ;

    companion object {
        val values = enumValues<CombatSpell>()

        /** Spells keyed by (interfaceId shl 16 or componentId) so Ancient component ids never collide with the standard book. */
        val definitions = CombatSpell.values().filter { it.componentId != -1 }.associateBy { (it.interfaceId shl 16) or it.componentId }

        fun forComponent(interfaceId: Int, componentId: Int): CombatSpell? = definitions[(interfaceId shl 16) or componentId]

        /**
         * OSRS elemental tiers (Wind, Water, Earth, Fire) with each spell's Magic level. OSRS update 29 May 2024 (OSRS Wiki "Water Bolt"):
         * "The maximum hit of the spell now depends on how many bolt spells the player has unlocked, based on their Magic level" -
         * a spell hits as hard as the strongest spell of its tier the caster's Magic level has unlocked.
         */
        val ELEMENTAL_TIERS: List<List<Pair<CombatSpell, Int>>> =
            listOf(
                listOf(WIND_STRIKE to 1, WATER_STRIKE to 5, EARTH_STRIKE to 9, FIRE_STRIKE to 13),
                listOf(WIND_BOLT to 17, WATER_BOLT to 23, EARTH_BOLT to 29, FIRE_BOLT to 35),
                listOf(WIND_BLAST to 41, WATER_BLAST to 47, EARTH_BLAST to 53, FIRE_BLAST to 59),
                listOf(WIND_WAVE to 62, WATER_WAVE to 65, EARTH_WAVE to 70, FIRE_WAVE to 75),
                listOf(WIND_SURGE to 81, WATER_SURGE to 85, EARTH_SURGE to 90, FIRE_SURGE to 95),
            )

        /**
         * The spell's base max hit for a caster at [magicLevel] (the current, visible level - the one that unlocks spells):
         * elemental tier scaling, and Magic Dart "10 + ⌊visible Magic level / 10⌋" (OSRS Wiki "Magic Dart"). Every other spell keeps
         * its fixed [maxHit].
         */
        fun baseMaxHit(spell: CombatSpell, magicLevel: Int): Int {
            if (spell == MAGIC_DART) return 10 + magicLevel / 10
            val tier = ELEMENTAL_TIERS.firstOrNull { row -> row.any { it.first == spell } } ?: return spell.maxHit
            return tier.filter { (_, level) -> magicLevel >= level }.maxOfOrNull { it.first.maxHit }?.coerceAtLeast(spell.maxHit) ?: spell.maxHit
        }
    }
}
