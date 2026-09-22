package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * OSRS spotanim (graphic / projectile) import into both rev-667 caches (owner decision (e) 2026-09-14).
 *
 * One OSRS spotanim brings its model (index 7, id <= 32767 because the spotanim model field is a signed short), its sequence
 * (index 20), every frameset group and base the sequence uses (indexes 0 and 1) and every synth sound its frames play (index 4).
 * The byte conversions are proven against spotanims that exist in both caches (`OsrsFxProbeTool`, OSRS_IMPORT_MASTER.yml):
 * frames get a leading `0x01` and per-base-type value scaling (origin/translate x4, rotation x16), bases get the 667 boolean and
 * part-mask arrays, spotanim opcode 3 (int model) becomes opcode 1.
 *
 * Sequence/spotanim ids are appended after the current maximum (the 667 client sizes both lists from the last group); frameset,
 * base and synth groups take the next ids after their index maximum. Sequences that use OSRS skeletal (animaya) data cannot be
 * represented and are refused per spotanim, never guessed.
 *
 * Usage: `<batch> [--apply]` - without `--apply` only plan + preflight run.
 */
object OsrsFxImportTool {
    val TARGETS = OsrsItemImportTool.TARGETS
    const val INDEX_FRAMES = 0
    const val INDEX_BASES = 1
    const val INDEX_SYNTH = 4
    const val INDEX_SEQ = 20
    const val INDEX_SPOTANIM = 21

    /**
     * Spotanims named after imported items in RuneLite `gameval/SpotanimID.java` (Jagex names from the cache).
     */
    val BATCHES: Map<String, List<Int>> =
        mapOf(
            "fxpilot" to
                listOf(
                    1228, 1229, // SOTD_SPECIAL_START / EXTRA (Power of Death)
                    1539, 1540, 1541, 1542, // SANGUINESTI_STAFF_TRAVEL / CASTING / IMPACT / HEAL
                    665, 1040, 1042, // TOXIC_TOTS_CASTING / PROJECTILE / IMPACT (Trident of the swamp)
                    1250, 1251, 1252, 1253, // SLAYER_TOTS_CHARGE / CASTING / PROJECTILE / IMPACT (Trident of the seas)
                    1043, // TOXIC_BLOWPIPE_SPECIALATTACK
                    301, 1995, 1181, 1468, // ACB_SPECIALATTACK, ZCB_SPECIALATTACK, ACB_CROSSBOWBOLT_TRAVEL, DRAGON_CROSSBOWBOLT_TRAVEL
                    344, 1301, 1386, // BALLISTA_SPECIAL, DRAGON_JAVELIN_TRAVEL, AMETHYST_JAVELIN_TRAVEL
                    1283, 1292, // ABYSSAL_DAGGER_SPECIAL_SPOTANIM, DRAGON_WARHAMMER_SA_SPOTANIM
                    2363, 2834, 2930, // FX_VOIDWAKER_IMPACT, FX_VOIDWAKER02_SPECIAL, VFX_NOXIOUS_HALBERD_SPEC
                    1887, 1888, 1936, 1937, // SP_ATTACK_ARROW_TRAVEL/LAUNCH_FAERDHINEN, AMETHYST_DART_TRAVEL/LAUNCH
                ),
            // Owner answer Q10, The Mimic: TRAIL_MIMIC_SWEET_PURPLE / GREEN / RED / BLUE 1670-1673 and TRAIL_MIMIC_EXPLOSION (+ GREEN /
            // RED / BLUE) 1674-1677 (RuneLite gameval SpotanimID).
            "mimic" to listOf(1670, 1671, 1672, 1673, 1674, 1675, 1676, 1677),
            // Owner 2026-09-17c (every weapon's special / projectile graphic exactly like OSRS); names from gameval SpotanimID.
            "weaponfx2" to
                listOf(
                    483, // DARK_SPEC_SPOT (Arclight / Darklight)
                    1759, 1760, 1761, 1762, // NIGHTMARE_STAFF_VOLATILE_HIT / _CAST, NIGHTMARE_STAFF_ELDRITCH_HIT / _CAST
                    1996, // NGS_SPECIAL_SPOTANIM (Ancient godsword)
                    2289, 2291, // ARROW_VENATOR01_LAUNCH01 / TRAVEL01
                    2354, 2355, // FX_WEBWEAVER01_LAUNCH / IMPACT
                    2792, // SPECIAL_DUAL_MACUAHUITL_SPOTANIM
                    2794, 2795, 2796, 2797, 2798, // SPECIAL_ATLATL, VFX_ATLATL_PROJECTILE_01 / IMPACT_01, SPECIAL_ATLATL_CAST / IMPACT
                    2806, 2807, 2808, 2809, 2908, // VFX_(HUMAN_)SCORCHING_BOW special / PROJECTILE / SPOTANIM / END / IMPACT_01
                    2810, 2814, // VFX_EMBERLIGHT_SPEC_02, VFX_BURNING_CLAWS_SPEC_02
                    2833, // SPOTANIM_WEAPON_SWORD_OSMUMTEN_SPECIAL
                    3486, // ROSEWOOD_BLOWPIPE_SPECIAL_TRAVEL
                    28, 697, 699, 1629, 1630, // DRAGON_TKNIFE_TRAVEL / _P / _SPEC / _SPEC_P / LAUNCH
                ),
            // Owner 2026-09-19 (exact OSRS Deadman breaches), names from gameval SpotanimID.
            "deadman-breach" to
                listOf(
                    // VFX_DM_BREACH_PROJ. VFX_DM_BREACH_SPAWN / LOOP / DESPAWN 2519/2522/2553 and VFX_DM_BOSS_* 2554-2558 are refused:
                    // their sequences are skeletal (animaya), which rev 667 cannot represent (dry run 2026-09-19).
                    2559,
                    643, 644, // SMOKE_DEVIL_SMOKE_PLAYER_SPOTANIM / SMOKE_DEVIL_SMOKE_PROJ (Thermonuclear smoke devil)
                    1512, 1514, 1518, // MA2_GUTHIX_PROJ (Derwen), MA2_ZAMORAK_PROJ (Porazdir), WILD_ZEALOT_LIGHTNING (Justiciar Zachariah)
                    649, 650, 651, 652, 653, // SPLATTER_EXPLODING_SPOTANIM1-5
                    1568, 1569, // TOB_BLOAT_FLIES_LARGE / SMALL
                    2655, 2901, // VFX_MAHJARRAT_TELEPORT_ZEMOUREGAL, VFX_MAHJARRAT_SUMMON_ZEMOUREGAL
                    1272, // BLACK_CHINCHOMPA_GRENADE (Ranging Ro)
                ),
            /*
             * Owner 2026-09-20: "we need exact OSRS surge animations".
             *
             * The cast sequence was already imported (OSRS 7855 HUMAN_CAST_SURGE -> OsrsSeq.HUMAN_CAST_SURGE); what
             * was still 667 art is the casting, travel and impact graphics. The four surges used a mix: only Earth
             * had a real 667 surge cast graphic, Water and Fire fell back to the generic element cast, and Wind was
             * firing the *wave* projectile. These twelve spotanims replace all of that with the OSRS set.
             *
             * Names and ids from RuneLite `gameval/SpotanimID.java`:
             *   wind  1455/1456/1457, water 1458/1459/1460, earth 1461/1462/1463, fire 1464/1465/1466
             * (CASTING / TRAVEL / IMPACT each). The `*_CASTING_FAST` variants 2903-2906 are the fast-cast animation
             * set and are deliberately not imported - this server casts surges on the normal 5-tick cycle.
             */
            "surge" to
                listOf(
                    1455, 1456, 1457, // WINDSURGE_CASTING / TRAVEL / IMPACT
                    1458, 1459, 1460, // WATERSURGE_CASTING / TRAVEL / IMPACT
                    1461, 1462, 1463, // EARTHSURGE_CASTING / TRAVEL / IMPACT
                    1464, 1465, 1466, // FIRESURGE_CASTING / TRAVEL / IMPACT
                ),
            /*
             * Owner 2026-09-22: Tele Block "osrs animations exactly". OSRS casts it with no casting spotanim; the
             * projectile is 1300 TELE_BLOCK_TRAVEL_FORFAIL (model 5800, seq 1821 TELE_BLOCK_TRAVEL, the only OSRS
             * spotanim on that sequence) and the impact 345 TELE_BLOCK_IMPACT (model 5799, seq 1822). 667 spotanim
             * 1300 is an unrelated graphic, so both are imported rather than referenced by id.
             */
            "teleblock" to listOf(1300, 345),
        )

    /**
     * Player sequences of the imported OSRS weapons (owner 2026-09-17c: "attack animation ... exactly like osrs"). Ids and
     * Jagex names: RuneLite `gameval/AnimationID.java`; the weapon each one belongs to: the names themselves plus the RuneLite
     * combat-logger plugin's observed attack table (SuperNerdEric/combat-logger `AnimationIds.java`). `_PVN` = the variant the
     * OSRS server plays against npcs.
     */
    val SEQ_BATCHES: Map<String, List<Int>> =
        mapOf(
            // Owner 2026-09-21 ("there is no breach"): the loc animations of OSRS "Breach" 49561 and "Boss Spawn"
            // 49563, kept here as the record of a settled question. `OsrsLocImportTool` dropped both animations as
            // "not an imported classic sequence", so the obvious next step was to import the sequences and re-import
            // the locs with them. This batch is that attempt, and it is REFUSED: `decodeOsrsSeq` reports both as
            // skeletal (animaya) sequences, which revision 667 cannot represent - and `OsrsLocImportTool` separately
            // drops the two models' animaya skinning for the same reason. The breach therefore cannot animate itself
            // in this cache by any import, which is why `DeadmanBreach` pulses a graphic over it instead. Running
            // this batch re-proves that in a few seconds; it can never apply anything.
            "breach-anim" to listOf(10418, 10423),
            "weaponseq1" to
                listOf(
                    3294, 3295, 3296, 3297, 3300, // ABYSSAL_DAGGER_HACK / BLOCK / IDLE / LUNGE / SPECIAL
                    7217, 7218, 7219, 7220, 7221, 7222, 7223, 7555, 7556, // BALLISTA_SPLINTER/ATTACK/DEFEND/READY/RUN/SPECIAL_ATTACK/WALK, _PVN
                    5061, 10656, 13142, 13143, 13144, 13145, // SNAKEBOSS_BLOWPIPE_ATTACK (+ORNAMENT), CAMPHOR/IRONWOOD/ROSEWOOD (+SPECIAL)
                    9471, 11222, // HUMAN_OSMUMTENS_FANG, WEAPON_SWORD_OSMUMTEN03_SPECIAL
                    8288, 8289, 8290, // HUMAN_DHUNTER_LANCE_ATTACK / SLASH / CRUSH
                    1378, // DRAGON_WARHAMMER_SA_PLAYER (667 sequence 1378 is a different animation)
                    11275, // HUMAN_SPECIAL02_VOIDWAKER
                    11140, 11138, 2890, // HUMAN_WEAPON_BURNING_CLAWS_02_SPEC, HUMAN_WEAPON_EMBERLIGHT_01_SPEC, DARK_SPEC_PLAYER (Arclight)
                    9171, // NGS_SPECIAL_PLAYER (Ancient godsword)
                    10989, // PMOON_MACUAHUITL_CRUSH (Dual macuahuitl)
                    8194, 8195, 8291, 8292, // HUMAN_DRAGON_KNIFE (+_P), HUMAN_DRAGON_TKNIVES_SPEC (+_POISON)
                    9964, // HUMAN_SPECIAL01_WEBWEAVER
                    9166, 9168, 7552, // ZCB_ATTACK (+_PVN), XBOWS_HUMAN_FIRE_AND_RELOAD_PVN
                    9857, 9858, 9859, 9860, 9861, 9862, 9863, // HUMAN_WEAPON_BOW_VENATOR01_READY/SHOOT/WALK/RUN/STEPLEFT/STEPRIGHT/TURN
                    11057, 11060, // HUMAN_ATLATL_ATTACK_RANGED_01, HUMAN_SPECIAL_ATLATL_01
                    10914, 10916, 10922, 10923, // HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL/UNCHARGED_SPECIAL/UNCHARGED_THROW/CHARGED_THROW
                    8532, // NIGHTMARE_STAFF_SPECIAL
                    11513, 11514, 11515, 11517, // HUMAN_HALBERD_VIRULENCE_01-04 (Noxious halberd special "Virulence" variants)
                ),
            // Tick 2 (2026-09-17c): OSRS ids that exist in 667 as a DIFFERENT animation (SeqProbe: frame counts differ) or not at all.
            "weaponseq2" to
                listOf(
                    7043, 7044, 7045, 7046, 7047, 7048, 7052, 7053, 7054, 7055, 7056, // DH_SWORD_UPDATE_RUN/TURNONSPOT/SLASH/CHOP/WALK_RIGHT/WALK_LEFT/WALK/READY/SMASH/BLOCK/DEFEND (godswords, 2h)
                    7638, 7639, 7640, 7641, 7642, 7643, 7644, 7645, // ZGS / SGS / BGS / AGS _SPECIAL_PLAYER and _SPECIAL_ORNATE_PLAYER
                    4504, 4505, // HUMAN_NIGHTMARE_STAFF_READY / _CRUSH
                    1702, 1703, 1704, 1705, 1706, 1707, 1709, 1710, 1711, 1712, 1713, // HUMAN_ZAMORAKSPEAR_* (Blue moon spear: combat-logger)
                    7855, // HUMAN_CAST_SURGE (Harmonised nightmare staff: combat-logger)
                ),
            // Owner 2026-09-19 night run: Teleblock with the exact OSRS cast animation (RuneLite gameval AnimationID
            // HUMAN_CASTING_TELE_BLOCK 1819 / HUMAN_CASTING_TELE_BLOCK_STAFF 1820).
            "spellseq1" to listOf(1819, 1820),
        )

    /** OSRS synth sounds by Jagex config name (OSRS Wiki "List of sound IDs", read 2026-09-17). */
    val SYNTH_BATCHES: Map<String, List<Int>> =
        mapOf(
            "weaponseq1" to
                listOf(
                    7917, 7930, // varlamore_pm_macuahuitl_special_01 / crush_01
                    9316, // burning_claws_swipe_01
                    9365, 9366, 9367, 9368, // a_r_osmumtens_fang_sword_metallic_woosh_01 / stab_01 / woosh_02 / woosh_01
                    9403, 9404, // noxious_halberd_special_attack_build_01 / impact_01
                ),
            "weaponsfx2" to
                listOf(
                    7918, 7932, // varlamore_pm_atlatl_special_impact_01 / _cast_01
                    7936, 7946, // varlamore_glaive_remove_charge_01 / _add_charge_01 (Tonalztics of Ralos)
                    7937, 7938, 7945, // varlamore_glaive_uncharged_special_throw_spin_01 / _whoosh_01 / _impact_01
                    7939, 7942, 7943, 7944, // varlamore_glaive_charged_special_throw_01 / _whoosh_01 / _spin_01 / _impact_01
                    7940, 7941, // varlamore_glaive_regular_throw_whoosh_01, varlamore_glaive_projectile_01
                ),
            // Owner 2026-09-18 P0 buglist: the Dragon claws special played the Burning claws swipe. OSRS Wiki "List of sound IDs"
            // (raw wikitext read 2026-09-18): 4138/4140/4141 dragonclaws_special_1/2/3, 4139 dragonclaws_normal. Above ~3800 the
            // OSRS and 667 synth ids no longer match, so they are imported.
            "weaponsfx3" to
                listOf(
                    4138, 4139, 4140, 4141,
                    // OSRS Wiki "Voidwaker" trivia: the special's sound is Superior Demonbane's cast layered with a ToA Wardens
                    // attack sound; 5027 superior_demonbane_cast (the Wardens half is not identified by any source).
                    5027,
                ),
            // Owner 2026-09-18 re-research: OSRS Wiki "Voidwaker" sound list names the Wardens half of the special:
            // 6182 toa_wardens_square_thunder1_01 ("Special attack", next to 5027).
            "weaponsfx4" to listOf(6182),
            // Owner 2026-09-19 (breach monsters "sounds, animations, everything"): OSRS server-sent attack / hit / death sounds of the
            // OSRS-only breach monsters are not public, so the owner chose real OSRS sounds picked by Jagex config name (OSRS Wiki
            // "List of sound IDs", raw wikitext 2026-09-19). Imported even where the id is below ~3800 so the bytes are OSRS's own.
            "breachsfx" to
                listOf(
                    400, 404, 403, // demon_attack / demon_hit / demon_death (Porazdir, ADAPTED family)
                    2564, 513, 512, // human_attack / human_hit / human_death (humanoid breach monsters)
                    4647, 4738, 4755, // lore_ent_attack / lore_ent_defend / lore_ent_death (Derwen, ADAPTED family)
                    595, 597, 596, // lavabeast_attack / _hit / _death (Jal-ImKot, ADAPTED family)
                    605, 607, 606, // lizard_attack / _hit / _death (Sulphur Lizard)
                    6904, 6928, 6952, // wbr_vention_hellhound_attack_bark_01 / _defend_snarl_01 / _death_01 (Cerberus, hellhound family)
                    7969, 821, // varl_audio_modifier_bee_swarm_attack01, swarm_hit (Bee Swarm)
                    414, 416, 415, // dust_devil_attack / _hit / _death (Thermonuclear smoke devil, ADAPTED family)
                    7878, 7884, 7901, // jaguar_attack_01 / jaguar_defend_01 / jaguar_death_01 (Jaguar warrior)
                    2720, 168, 102, // whip, ice_barrage_impact, blood_barrage_impact (Durial321, Magic Mark)
                    359, 360, // chinchompa_attack / chinchompa_explode (Ranging Ro)
                    7852, // zemo_lightning (Zemouregal's cast, ADAPTED)
                    809, 811, 810, // splatter_attack / _hit / _death
                    3544, 3971, 3965, // tob_bloat_flies_attack_1 / tob_bloat_hit / tob_bloat_death (Pestilent Bloat)
                    918, 923, 922, // zombie_attack / _hit / _death (Zemouregal's Undead One summon)
                ),
            // Owner 2026-09-18 (Zaryte crossbow / Ancient godsword specials): Jagex sound config names from the gameval table
            // (Alter-rework data/cfg/rscm/sound.rscm, 2026-09-19): 5306 zaryte_crossbow_special, 3869 godwars_godsword_special_attack.
            "weaponsfx5" to listOf(5306, 3869),
            // Owner 2026-09-19 (Surge spells must sound like OSRS): OSRS Wiki "List of sound IDs" (raw wikitext 2026-09-19)
            // 4025 earthsurge_cast_and_fire, 4026 earthsurge_hit, 4027 windsurge_hit, 4028 windsurge_cast_and_fire,
            // 4029 watersurge_hit, 4030 watersurge_cast_and_fire, 4031 firesurge_hit, 4032 firesurge_cast_and_fire.
            "surgesfx" to listOf(4025, 4026, 4027, 4028, 4029, 4030, 4031, 4032),
        )

    // ---- smart values ---------------------------------------------------------------------------

    class Cursor(val data: ByteArray, var pos: Int = 0) {
        fun u8(): Int = data[pos++].toInt() and 0xFF

        fun s8(): Int = data[pos++].toInt()

        fun u16(): Int = (u8() shl 8) or u8()

        fun i32(): Int = (u16() shl 16) or u16()

        fun smarts(): Int {
            val first = data[pos].toInt() and 0xFF
            return if (first < 128) u8() - 64 else u16() - 49152
        }

        fun string(): String {
            val start = pos
            while (data[pos].toInt() != 0) pos++
            return String(data, start, pos++ - start, Charsets.ISO_8859_1)
        }

        val remaining: Int get() = data.size - pos
    }

    private fun ByteArrayOutputStream.u8(v: Int) = write(v and 0xFF)

    private fun ByteArrayOutputStream.u16(v: Int) {
        write(v ushr 8 and 0xFF)
        write(v and 0xFF)
    }

    private fun ByteArrayOutputStream.smarts(v: Int) {
        when (v) {
            in -64..63 -> u8(v + 64)
            in -16384..16383 -> u16(v + 49152)
            else -> error("value $v does not fit a signed smart")
        }
    }

    // ---- bases and frames ----------------------------------------------------------------------

    /** Base types of an OSRS framemap (count, types...). */
    fun baseTypes(osrsBase: ByteArray): IntArray {
        val c = Cursor(osrsBase)
        val count = c.u8()
        return IntArray(count) { c.u8() }
    }

    /** OSRS framemap -> rev-667 AnimBase: count, types, `count` booleans 0, `count` part masks 0xFFFF, sizes, maps. */
    fun convertBase(osrsBase: ByteArray): ByteArray {
        val c = Cursor(osrsBase)
        val count = c.u8()
        val types = IntArray(count) { c.u8() }
        types.forEach { check(it in SCALE_BY_TYPE) { "unsupported base type $it" } }
        val out = ByteArrayOutputStream()
        out.u8(count)
        types.forEach { out.u8(it) }
        repeat(count) { out.u8(0) }
        repeat(count) { out.u16(0xFFFF) }
        // Sizes and maps exactly as RuneLite FramemapLoader reads them; the newer OSRS framemap carries trailing bytes after the
        // maps that neither RuneLite's loader nor the 667 AnimBase reads (proven by OsrsFxConversionTests against base 871).
        val sizes = IntArray(count) { c.u8() }
        sizes.forEach { out.u8(it) }
        sizes.forEach { size -> repeat(size) { out.u8(c.u8()) } }
        return out.toByteArray()
    }

    /** The human rig: player sequences of both caches animate framemap 0 (SeqProbe 2026-09-17: 1062, 1167, 4230 in both caches). */
    const val PLAYER_BASE = 0

    private class BaseGroups(val types: IntArray, val labels: List<List<Int>>)

    private fun readGroups(base: ByteArray, is667: Boolean): BaseGroups {
        val c = Cursor(base)
        val count = c.u8()
        val types = IntArray(count) { c.u8() }
        if (is667) c.pos += count * 3
        val sizes = IntArray(count) { c.u8() }
        return BaseGroups(types, sizes.map { size -> List(size) { c.u8() } })
    }

    /** Base type of an alpha (face transparency) group; its labels are FACE labels, a different label space from vertex labels. */
    const val TYPE_ALPHA = 5
    private const val TYPE_ORIGIN = 0
    private const val WHOLE_BODY = 100

    /**
     * The labels the 667 rig uses for the same pivot as OSRS origin group [origin].
     *
     * A pivot is the centroid of the vertices that carry the origin group's labels. OSRS pivots the right-hand weapon on label 27; the
     * 667 rig pivots the same limb group on 196, 200 and 27 - the HD body kits carry the pivot on 196 / 200, so with the OSRS labels alone
     * no vertex is found, the pivot falls to the model origin and the weapon swings away from the hands (owner screenshots 2026-09-17c:
     * godsword, blue moon spear and ballista floating beside the character). The 667 origin group is found through the limb it serves:
     * the first limb group after the OSRS origin is matched to the 667 limb group of the same type with the most labels in common (at
     * least half), and that group's own origin supplies the extra pivot labels.
     */
    private fun pivotCompanions(
        osrs: BaseGroups,
        local: BaseGroups,
        origin: Int,
    ): List<Int> {
        val served =
            (origin + 1 until osrs.types.size)
                .takeWhile { osrs.types[it] != TYPE_ORIGIN }
                .firstOrNull { osrs.types[it] != TYPE_ALPHA && osrs.labels[it].size in 1 until WHOLE_BODY } ?: return emptyList()
        val limb = osrs.labels[served].toSet()
        var best = -1
        var bestScore = 0.0
        for (j in local.types.indices) {
            if (local.types[j] != osrs.types[served] || local.labels[j].size >= WHOLE_BODY) continue
            val other = local.labels[j].toSet()
            val score = limb.count { it in other }.toDouble() / (limb.size + other.size - limb.count { it in other })
            if (score > bestScore) {
                bestScore = score
                best = j
            }
        }
        if (best < 0 || bestScore < 0.5) return emptyList()
        val localOrigin = (best - 1 downTo 0).firstOrNull { local.types[it] == TYPE_ORIGIN } ?: return emptyList()
        // Only when the two origins are the same pivot to begin with (they share a label).
        if (local.labels[localOrigin].none { it in osrs.labels[origin] }) return emptyList()
        return local.labels[localOrigin]
    }

    /**
     * OSRS human framemap -> rev-667 AnimBase that also moves the 667-only vertex labels.
     *
     * Both rigs descend from the 2007 rig and share the body-part vertex labels; rev 667 added HD-only labels (218+: fingers, cape,
     * shoulder and head pieces). An OSRS frame knows nothing about them, so with a plain conversion those vertices of 667 body kits
     * and armour stay behind while the limb moves. Only the first ~70 groups of the two framemaps line up by index (MergeAudit
     * 2026-09-17c: 74 of 205), and the finer OSRS head / leg groups come later - owner live test: "head and legs skeleton seems to
     * bug" with the first, index-based rule. The rule is therefore by CONTENT: a 667-only label x rides with its companions K = the
     * OSRS-known labels of the smallest 667 limb group that holds x; x joins every OSRS limb group that holds all of K or more than
     * half of it, and every whole-body group. Alpha groups are face labels and are
     * never touched. A label the OSRS rig knows keeps the OSRS grouping. Origin (pivot) groups gain the 667 pivot labels ([pivotCompanions]).
     */
    fun mergePlayerBase(
        osrsBase: ByteArray,
        base667: ByteArray,
    ): ByteArray {
        val osrs = readGroups(osrsBase, false)
        val local = readGroups(base667, true)
        val osrsKnown = osrs.labels.filterIndexed { i, _ -> osrs.types[i] != TYPE_ALPHA }.flatten().toSet()
        val localLimbs =
            local.labels.filterIndexed { i, g -> local.types[i] != TYPE_ALPHA && local.types[i] != TYPE_ORIGIN && g.size < WHOLE_BODY }
        val localOnly =
            local.labels.filterIndexed { i, _ -> local.types[i] != TYPE_ALPHA && local.types[i] != TYPE_ORIGIN }.flatten().toSet() - osrsKnown
        val companions: Map<Int, Set<Int>> =
            localOnly.associateWith { x ->
                localLimbs.filter { x in it }.sortedBy { it.size }.map { g -> g.filter { it in osrsKnown }.toSet() }.firstOrNull { it.isNotEmpty() } ?: emptySet()
            }
        val merged =
            osrs.labels.mapIndexed { i, labels ->
                val type = osrs.types[i]
                if (type == TYPE_ORIGIN) return@mapIndexed labels + pivotCompanions(osrs, local, i).filter { it !in labels }
                if (type == TYPE_ALPHA || labels.isEmpty()) return@mapIndexed labels
                val own = labels.toSet()
                val extra =
                    localOnly.filter { x ->
                        if (labels.size >= WHOLE_BODY) return@filter true
                        val k = companions.getValue(x)
                        if (k.isEmpty()) return@filter false
                        val shared = k.count { it in own }
                        shared == k.size || shared * 2 > k.size
                    }
                labels + extra.sorted()
            }
        osrs.types.forEach { check(it in SCALE_BY_TYPE) { "unsupported base type $it" } }
        val out = ByteArrayOutputStream()
        out.u8(osrs.types.size)
        osrs.types.forEach { out.u8(it) }
        repeat(osrs.types.size) { out.u8(0) }
        repeat(osrs.types.size) { out.u16(0xFFFF) }
        merged.forEach { check(it.size <= 255) { "merged group exceeds 255 labels" }; out.u8(it.size) }
        merged.forEach { labels -> labels.forEach { out.u8(it) } }
        return out.toByteArray()
    }

    /** Proven per-type value scale (origin/translate x4, rotation x16, scale and alpha unchanged). */
    val SCALE_BY_TYPE = mapOf(0 to 4, 1 to 4, 2 to 16, 3 to 1, 5 to 1)

    /** OSRS frame -> rev-667 frame for [localBaseId]; [types] are the OSRS base's types. */
    fun convertFrame(
        osrsFrame: ByteArray,
        types: IntArray,
        localBaseId: Int,
        dropAlpha: Boolean = false,
    ): ByteArray {
        val c = Cursor(osrsFrame)
        c.u16()
        val length = c.u8()
        val sourceFlags = IntArray(length) { c.u8() }
        // Player frames: OSRS fades FACE labels that mean something else on 667 equipment models (owner live test: "my gear
        // dissapears"), so the alpha channel of a human frame is not carried over. The values are still consumed from the source.
        val flags = IntArray(length) { if (dropAlpha && types.getOrNull(it) == TYPE_ALPHA) 0 else sourceFlags[it] }
        val out = ByteArrayOutputStream()
        out.u8(1)
        out.u16(localBaseId)
        out.u8(length)
        flags.forEach { out.u8(it) }
        for (i in 0 until length) {
            if (sourceFlags[i] == 0) continue
            val scale = SCALE_BY_TYPE[types.getOrElse(i) { -1 }] ?: error("frame group $i has unsupported base type ${types.getOrNull(i)}")
            for (bit in 0..2) {
                if (sourceFlags[i] and (1 shl bit) == 0) continue
                val value = c.smarts() * scale
                if (flags[i] != 0) out.smarts(value)
            }
        }
        check(c.remaining == 0) { "frame has ${c.remaining} trailing bytes" }
        return out.toByteArray()
    }

    // ---- sequences -----------------------------------------------------------------------------

    class Seq(
        var frameDurations: IntArray = IntArray(0),
        var frames: IntArray = IntArray(0),
        var loopOffset: Int? = null,
        var blend: IntArray? = null,
        var priority: Int? = null,
        var maxLoops: Int? = null,
        var animatingPrecedence: Int? = null,
        var walkingPrecedence: Int? = null,
        var replayMode: Int? = null,
        var secondaryFrames: IntArray? = null,
        /** Rev-667 values of opcodes 6 / 7 (only "hide the hand item" = 0xFFFF is representable, see [decodeOsrsSeq]). */
        var leftHand: Int? = null,
        var rightHand: Int? = null,
        val sounds: MutableMap<Int, Pair<Int, Int>> = sortedMapOf(),
        val dropped: MutableList<String> = mutableListOf(),
    )

    /** OSRS rev-226+ sequence (RuneLite `SequenceLoader` with rev226 = true). */
    fun decodeOsrsSeq(
        bytes: ByteArray,
        localItems: Map<Int, Int> = emptyMap(),
    ): Seq {
        val c = Cursor(bytes)
        val seq = Seq()
        while (true) {
            when (val op = c.u8()) {
                0 -> return seq
                1 -> {
                    val n = c.u16()
                    seq.frameDurations = IntArray(n) { c.u16() }
                    val lo = IntArray(n) { c.u16() }
                    seq.frames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                2 -> seq.loopOffset = c.u16()
                3 -> {
                    val n = c.u8()
                    seq.blend = IntArray(n) { c.u8() }
                }
                4 -> seq.dropped += "stretches flag (no 667 equivalent)"
                5 -> seq.priority = c.u8()
                6, 7 -> {
                    // OSRS: 0 hides the hand item, >= 512 shows OSRS item (value - 512). 667 `PlayerModel`: 0xFFFF hides, anything
                    // else is a 667 item id; [localItems] maps imported OSRS items to their 667 ids.
                    val value = c.u16()
                    val local = if (value == 0) 0xFFFF else localItems[value - 512]
                    if (local != null) {
                        if (op == 6) seq.leftHand = local else seq.rightHand = local
                    } else {
                        seq.dropped += "hand item ${value - 512} (OSRS item id, not imported)"
                    }
                }
                8 -> seq.maxLoops = c.u8()
                9 -> seq.animatingPrecedence = c.u8()
                10 -> seq.walkingPrecedence = c.u8()
                11 -> seq.replayMode = c.u8()
                12 -> {
                    val n = c.u8()
                    val lo = IntArray(n) { c.u16() }
                    seq.secondaryFrames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                13 -> error("skeletal (animaya) sequence ${c.i32()} cannot be represented in 667")
                14 -> {
                    val n = c.u16()
                    repeat(n) {
                        val frame = c.u16()
                        val id = c.u16()
                        c.u8() // weight
                        val loops = c.u8()
                        c.u8() // location
                        c.u8() // retain
                        if (id >= 1 && loops >= 1) seq.sounds[frame] = id to loops
                    }
                    seq.dropped += "frame sound location/retain/weight (no 667 fields)"
                }
                15 -> error("skeletal (animaya) frame range ${c.u16()}-${c.u16()} cannot be represented in 667")
                16 -> seq.dropped += "vertical offset ${c.s8()}"
                17 -> {
                    val n = c.u8()
                    repeat(n) { c.u8() }
                    error("skeletal (animaya) masks cannot be represented in 667")
                }
                18 -> seq.dropped += "debug name ${c.string()}"
                19 -> seq.dropped += "sounds cross world view flag"
                else -> error("unknown OSRS sequence opcode $op")
            }
        }
    }

    /** Rev-667 `SeqType.decode` stream; frame ids and sound ids already local. */
    fun encode667Seq(seq: Seq): ByteArray {
        val out = ByteArrayOutputStream()
        val n = seq.frames.size
        out.u8(1)
        out.u16(n)
        seq.frameDurations.forEach { out.u16(it) }
        seq.frames.forEach { out.u16(it and 0xFFFF) }
        seq.frames.forEach { out.u16(it ushr 16) }
        seq.loopOffset?.let { out.u8(2); out.u16(it) }
        seq.blend?.let { b -> out.u8(3); out.u8(b.size); b.forEach { out.u8(it) } }
        seq.priority?.let { out.u8(5); out.u8(it) }
        seq.leftHand?.let { out.u8(6); out.u16(it) }
        seq.rightHand?.let { out.u8(7); out.u16(it) }
        seq.maxLoops?.let { out.u8(8); out.u8(it) }
        seq.animatingPrecedence?.let { out.u8(9); out.u8(it) }
        seq.walkingPrecedence?.let { out.u8(10); out.u8(it) }
        seq.replayMode?.let { out.u8(11); out.u8(it) }
        seq.secondaryFrames?.let { s ->
            out.u8(12)
            out.u8(s.size)
            s.forEach { out.u16(it and 0xFFFF) }
            s.forEach { out.u16(it ushr 16) }
        }
        if (seq.sounds.isNotEmpty()) {
            out.u8(13)
            out.u16(n)
            for (frame in 0 until n) {
                val sound = seq.sounds[frame]
                if (sound == null) {
                    out.u8(0)
                } else {
                    out.u8(1)
                    val packed = (sound.first shl 8) or (sound.second.coerceAtMost(7) shl 5)
                    out.u8(packed ushr 16)
                    out.u16(packed and 0xFFFF)
                }
            }
        }
        out.u8(0)
        return out.toByteArray()
    }

    // ---- spotanims -----------------------------------------------------------------------------

    class Spot(var model: Int = -1, var seq: Int = -1, val keep: ByteArrayOutputStream = ByteArrayOutputStream(), val dropped: MutableList<String> = mutableListOf())

    /** OSRS spotanim (RuneLite `SpotAnimLoader`); opcodes 4-8 and 40 are copied verbatim, 41 retextures are dropped. */
    fun decodeOsrsSpot(bytes: ByteArray): Spot {
        val c = Cursor(bytes)
        val spot = Spot()
        while (true) {
            val start = c.pos
            when (val op = c.u8()) {
                0 -> return spot
                1 -> spot.model = c.u16()
                2 -> spot.seq = c.u16()
                3 -> spot.model = c.i32()
                4, 5, 6 -> {
                    c.u16()
                    spot.keep.write(bytes, start, 3)
                }
                7, 8 -> {
                    c.u8()
                    spot.keep.write(bytes, start, 2)
                }
                9 -> spot.dropped += "debug name ${c.string()}"
                40 -> {
                    val n = c.u8()
                    repeat(n) { c.u16(); c.u16() }
                    spot.keep.write(bytes, start, 2 + n * 4)
                }
                41 -> {
                    val n = c.u8()
                    repeat(n) { c.u16(); c.u16() }
                    spot.dropped += "retexture of $n OSRS textures (textures are flattened into the mesh colours)"
                }
                else -> error("unknown OSRS spotanim opcode $op")
            }
        }
    }

    fun encode667Spot(
        spot: Spot,
        localModel: Int,
        localSeq: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        if (localModel >= 0) {
            check(localModel <= OsrsItemImportTool.SPOTANIM_MODEL_LIMIT) { "spotanim model $localModel above the signed-short limit" }
            out.u8(1)
            out.u16(localModel)
        }
        if (localSeq >= 0) {
            out.u8(2)
            out.u16(localSeq)
        }
        out.write(spot.keep.toByteArray())
        out.u8(0)
        return out.toByteArray()
    }

    // ---- strict 667 re-decoders used after the write -------------------------------------------

    /** Walks a 667 sequence exactly like `SeqType.decode`; returns the frame ids. */
    fun decode667SeqFrames(bytes: ByteArray): IntArray {
        val c = Cursor(bytes)
        var frames = IntArray(0)
        while (true) {
            when (val op = c.u8()) {
                0 -> {
                    check(c.remaining == 0) { "sequence has trailing bytes" }
                    return frames
                }
                1 -> {
                    val n = c.u16()
                    repeat(n) { c.u16() }
                    val lo = IntArray(n) { c.u16() }
                    frames = IntArray(n) { lo[it] + (c.u16() shl 16) }
                }
                2 -> c.u16()
                3 -> repeat(c.u8()) { c.u8() }
                5, 8, 9, 10, 11 -> c.u8()
                6, 7 -> c.u16()
                12 -> {
                    val n = c.u8()
                    repeat(n * 2) { c.u16() }
                }
                13 -> {
                    val n = c.u16()
                    repeat(n) {
                        val options = c.u8()
                        if (options > 0) {
                            c.u8()
                            c.u16()
                            repeat(options - 1) { c.u16() }
                        }
                    }
                }
                14, 15, 16, 18 -> Unit
                else -> error("opcode $op is not a 667 sequence opcode")
            }
        }
    }

    /** Decodes a 667 frame against its 667 base exactly like `AnimFrame` (without its silent catch). */
    fun check667Frame(
        frame: ByteArray,
        base: ByteArray,
    ) {
        val baseCursor = Cursor(base)
        val count = baseCursor.u8()
        val types = IntArray(count) { baseCursor.u8() }
        val c = Cursor(frame, 3)
        val length = c.u8()
        check(length <= count) { "frame length $length exceeds base count $count" }
        val data = Cursor(frame, c.pos + length)
        for (i in 0 until length) {
            val flags = c.u8()
            if (flags <= 0) continue
            check(i < types.size)
            for (bit in 0..2) if (flags and (1 shl bit) != 0) data.smarts()
        }
        check(data.remaining == 0) { "frame data does not end where the flags say" }
        check(base.size == 1 + count * 4 + (base.size - 1 - count * 4)) // arrays present
    }

    // ---- asset map -----------------------------------------------------------------------------

    /** `kind:upstream` -> local id for fx entries already recorded in the asset map (`fx_kind` entries of `imports:`). */
    fun existingFx(assetMap: File): Map<String, Int> {
        val root = ObjectMapper(YAMLFactory()).readTree(assetMap) ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        root.path("imports").forEach { entry ->
            val kind = entry.path("fx_kind")
            if (kind.isTextual && entry.path("upstream_fx_id").isInt && entry.path("local_fx_id").isInt) {
                out["${kind.asText()}:${entry.path("upstream_fx_id").asInt()}"] = entry.path("local_fx_id").asInt()
            }
        }
        return out
    }

    /** OSRS item id -> local item id for every imported item recorded in the asset map. */
    fun importedItems(assetMap: File): Map<Int, Int> {
        val root = ObjectMapper(YAMLFactory()).readTree(assetMap) ?: return emptyMap()
        val out = mutableMapOf<Int, Int>()
        root.path("imports").forEach { entry ->
            if (entry.path("upstream_item_id").isInt && entry.path("local_item_id").isInt) {
                out.putIfAbsent(entry.path("upstream_item_id").asInt(), entry.path("local_item_id").asInt())
            }
        }
        return out
    }

    // ---- main ----------------------------------------------------------------------------------

    @JvmStatic
    fun main(args: Array<String>) {
        val batchName = args.firstOrNull { !it.startsWith("--") } ?: error("Usage: <batch> [--apply]")
        val known = BATCHES.keys + SEQ_BATCHES.keys + SYNTH_BATCHES.keys
        check(batchName in known) { "Unknown batch '$batchName' (known: $known)" }
        val spotIds = BATCHES[batchName] ?: emptyList()
        val seqIds = SEQ_BATCHES[batchName] ?: emptyList()
        val synthIds = SYNTH_BATCHES[batchName] ?: emptyList()
        val apply = "--apply" in args
        val assetMap = File(OsrsItemImportTool.ASSET_MAP)
        val existing = existingFx(assetMap)
        val reader = ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE))
        val library = CacheLibrary(TARGETS[0])
        val mutations = mutableListOf<CacheMutation>()
        val records = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val refused = mutableListOf<String>()
        try {
            fun next(index: Int): Int = (library.index(index).archiveIds().maxOrNull() ?: -1) + 1
            fun nextPaged(index: Int, shift: Int): Int {
                val group = library.index(index).archiveIds().maxOrNull() ?: return 0
                val maxFile = library.index(index).archive(group)?.fileIds()?.maxOrNull() ?: -1
                return (group shl shift) + maxFile + 1
            }
            var nextSpot = nextPaged(INDEX_SPOTANIM, 8)
            var nextSeq = nextPaged(INDEX_SEQ, 7)
            var nextFrameset = next(INDEX_FRAMES)
            var nextBase = next(INDEX_BASES)
            var nextSynth = next(INDEX_SYNTH)

            val census = ModelNamespaceCensusTool.census(TARGETS[0], TARGETS[1], assetMap)
            check(census.physicalDivergentIds.isEmpty() && census.referencedDivergentIds.isEmpty()) { "target caches diverge" }
            check(census.untracedReferenceTypes.isEmpty()) { "untraced model reference types: ${census.untracedReferenceTypes}" }
            val modelHoles = census.provenFreeHoles.filter { it in 1..OsrsItemImportTool.SPOTANIM_MODEL_LIMIT }.sorted().toMutableList()
            println("CENSUS spotanim_model_holes=${modelHoles.size} next spot=$nextSpot seq=$nextSeq frameset=$nextFrameset base=$nextBase synth=$nextSynth")

            val localIds = existing.toMutableMap()
            fun local(kind: String, upstream: Int, allocate: () -> Int): Int =
                localIds.getOrPut("$kind:$upstream") { allocate().also { records += "$kind|$upstream|$it" } }

            fun sha1At(index: Int, group: Int, file: Int): String? = library.data(index, group, file)?.let { CacheItemProbeTool.sha1(it) }

            fun put(index: Int, group: Int, file: Int, bytes: ByteArray, label: String) {
                val current = sha1At(index, group, file)
                mutations += CacheMutation(index, group, file, bytes, label, expectedCurrentSha1 = current?.takeIf { it != CacheItemProbeTool.sha1(bytes) })
            }

            val importedItems = importedItems(assetMap)
            val playerBase667 by lazy { library.data(INDEX_BASES, PLAYER_BASE, 0) ?: error("667 player base $PLAYER_BASE missing") }

            fun stageSynth(id: Int, staged: MutableList<() -> Unit>): Int {
                val synth = reader.file(INDEX_SYNTH, id, 0) ?: error("OSRS synth $id missing")
                return local("synth", id) { nextSynth++ }.also { localId -> staged += { put(INDEX_SYNTH, localId, 0, synth, "osrs synth $id") } }
            }

            /** Stages one OSRS sequence with its framesets, bases and frame sounds; returns the local sequence id. */
            fun stageSeq(osrsSeq: Int, staged: MutableList<() -> Unit>): Int {
                val seqBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq) ?: error("OSRS sequence $osrsSeq missing")
                val seq = decodeOsrsSeq(seqBytes, importedItems)
                dropped += seq.dropped.map { "seq $osrsSeq: $it" }
                val framesets = (seq.frames.map { it ushr 16 } + (seq.secondaryFrames?.map { it ushr 16 } ?: emptyList())).distinct()
                val framesetMap = framesets.associateWith { fs -> local("frameset", fs) { nextFrameset++ } }
                framesets.forEach { fs ->
                    reader.files(INDEX_FRAMES, fs).forEach { (file, frameBytes) ->
                        val osrsBase = ((frameBytes[0].toInt() and 0xFF) shl 8) or (frameBytes[1].toInt() and 0xFF)
                        val baseBytes = reader.file(INDEX_BASES, osrsBase, 0) ?: error("OSRS base $osrsBase missing")
                        // The human rig (base 0 in both caches) is merged with the 667 rig so the HD-only vertex labels of
                        // 667 body kits and armour follow the limb the OSRS frames move; every other base converts 1:1.
                        val player = osrsBase == PLAYER_BASE
                        val localBase = local(if (player) "playerbase" else "base", osrsBase) { nextBase++ }
                        val converted = convertFrame(frameBytes, baseTypes(baseBytes), localBase, dropAlpha = player)
                        val base667 = if (player) mergePlayerBase(baseBytes, playerBase667) else convertBase(baseBytes)
                        check667Frame(converted, base667)
                        staged += { put(INDEX_BASES, localBase, 0, base667, "osrs base $osrsBase") }
                        staged += { put(INDEX_FRAMES, framesetMap.getValue(fs), file, converted, "osrs frame $fs:$file") }
                    }
                }
                seq.frames = IntArray(seq.frames.size) { (framesetMap.getValue(seq.frames[it] ushr 16) shl 16) or (seq.frames[it] and 0xFFFF) }
                seq.secondaryFrames = seq.secondaryFrames?.let { s -> IntArray(s.size) { (framesetMap.getValue(s[it] ushr 16) shl 16) or (s[it] and 0xFFFF) } }
                val soundMap = seq.sounds.values.map { it.first }.distinct().associateWith { id -> stageSynth(id, staged) }
                seq.sounds.replaceAll { _, sound -> soundMap.getValue(sound.first) to sound.second }
                val localSeq = local("seq", osrsSeq) { nextSeq++ }
                val seq667 = encode667Seq(seq)
                decode667SeqFrames(seq667)
                staged += { put(INDEX_SEQ, localSeq ushr 7, localSeq and 0x7F, seq667, "osrs seq $osrsSeq") }
                return localSeq
            }

            /** Runs [block]; a refusal leaves no allocation, record or mutation behind. */
            fun guarded(label: String, block: (MutableList<() -> Unit>) -> Unit) {
                val staged = mutableListOf<() -> Unit>()
                val recordMark = records.size
                val idsBefore = localIds.toMap()
                val countersBefore = listOf(nextSpot, nextSeq, nextFrameset, nextBase, nextSynth)
                try {
                    block(staged)
                    staged.forEach { it() }
                } catch (e: RuntimeException) {
                    refused += "$label: ${e.javaClass.simpleName}: ${e.message}"
                    while (records.size > recordMark) records.removeAt(records.size - 1)
                    localIds.clear()
                    localIds.putAll(idsBefore)
                    nextSpot = countersBefore[0]
                    nextSeq = countersBefore[1]
                    nextFrameset = countersBefore[2]
                    nextBase = countersBefore[3]
                    nextSynth = countersBefore[4]
                }
            }

            for (osrsSeq in seqIds) {
                guarded("seq $osrsSeq") { staged -> println("PLAN seq $osrsSeq -> ${stageSeq(osrsSeq, staged)}") }
            }
            for (osrsSynth in synthIds) {
                guarded("synth $osrsSynth") { staged -> println("PLAN synth $osrsSynth -> ${stageSynth(osrsSynth, staged)}") }
            }

            for (spotId in spotIds) {
                val spotBytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SPOTANIM, spotId) ?: error("OSRS spotanim $spotId missing")
                val staged = mutableListOf<() -> Unit>()
                // A refused spotanim must leave no allocation, record or mutation behind.
                val recordMark = records.size
                val idsBefore = localIds.toMap()
                val holesBefore = modelHoles.toList()
                val countersBefore = listOf(nextSpot, nextSeq, nextFrameset, nextBase, nextSynth)
                try {
                    val spot = decodeOsrsSpot(spotBytes)
                    var localSeq = -1
                    if (spot.seq >= 0) localSeq = stageSeq(spot.seq, staged)
                    var localModel = -1
                    if (spot.model >= 0) {
                        val modelBytes = OsrsModelConversion.convert(reader, spot.model, dropped)
                        localModel = local("spotanim_model", spot.model) { modelHoles.removeAt(0) }
                        staged += { put(ModelConvertTool.MODEL_INDEX, localModel, 0, modelBytes, "osrs spotanim model ${spot.model}") }
                    }
                    dropped += spot.dropped.map { "spotanim $spotId: $it" }
                    val localSpot = local("spotanim", spotId) { nextSpot++ }
                    val spot667 = encode667Spot(spot, localModel, localSeq)
                    staged += { put(INDEX_SPOTANIM, localSpot ushr 8, localSpot and 0xFF, spot667, "osrs spotanim $spotId") }
                    staged.forEach { it() }
                    println("PLAN spotanim $spotId -> $localSpot model ${spot.model}->$localModel seq ${spot.seq}->$localSeq")
                } catch (e: RuntimeException) {
                    refused += "spotanim $spotId: ${e.javaClass.simpleName}: ${e.message}"
                    while (records.size > recordMark) records.removeAt(records.size - 1)
                    localIds.clear()
                    localIds.putAll(idsBefore)
                    modelHoles.clear()
                    modelHoles.addAll(holesBefore)
                    nextSpot = countersBefore[0]
                    nextSeq = countersBefore[1]
                    nextFrameset = countersBefore[2]
                    nextBase = countersBefore[3]
                    nextSynth = countersBefore[4]
                }
            }
        } finally {
            library.close()
            reader.close()
        }
        refused.forEach { println("REFUSED $it") }
        dropped.distinct().forEach { println("DROPPED $it") }
        val unique = mutations.distinctBy { it.describeLocation() }
        val transaction = CacheTransaction(targets = TARGETS, mutations = unique)
        val plan = transaction.preflight()
        val errors = transaction.blockingErrors(plan)
        println("PREFLIGHT transaction=${transaction.id} mutations=${unique.size} outcomes=${plan.groupingBy { it.outcome }.eachCount()}")
        errors.forEach { println("  BLOCKING: $it") }
        check(errors.isEmpty()) { "preflight blocked; nothing written" }
        if (!apply) {
            println("DRY_RUN records=${records.size}")
            return
        }
        val result = transaction.apply(plan)
        val problems = transaction.verify()
        if (problems.isNotEmpty()) {
            problems.forEach { println("  VERIFY_FAILURE: $it") }
            println("ROLLED_BACK ${transaction.rollback()}")
            error("transaction ${transaction.id} failed verification and was rolled back")
        }
        println("APPLIED transaction=${result.transactionId} writes=${result.applied} skipped=${result.skipped}")
        if (records.isNotEmpty()) {
            val block = StringBuilder()
            records.forEach { r ->
                val (kind, upstream, localId) = r.split('|')
                block.append("  - fx_kind: $kind\n    upstream_fx_id: $upstream\n    local_fx_id: $localId\n    status: IMPORTED_BY_OSRS_FX_TOOL\n    transaction: ${transaction.id}\n")
            }
            val text = assetMap.readText()
            assetMap.writeText(if (text.endsWith("\n")) text + block else "$text\n$block")
            println("ASSET_MAP appended ${records.size} fx entries")
        }
    }
}
