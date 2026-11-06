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
            // Owner 2026-09-29 (revenant weapons): gameval SpotanimID SPELLS_VETION01_TRAVEL 2337 / _SPECIAL 2338 / _TRAVEL02 2339
            // (Accursed sceptre), SPELLS_THAMMARON01_TRAVEL01 2340 (Thammaron's sceptre), FX_URSINE01/02_SPECIAL 2341 / 2342.
            "revfx" to listOf(2337, 2338, 2339, 2340, 2341, 2342),
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
            // Owner 2026-09-24 ("alle osrs geporte weapons moeten precies als osrs behaven"): the Tonalztics of Ralos still threw the 667
            // rune thrownaxe. Gameval SpotanimID: VFX_GLAIVE_CHARGED_SPECIAL 2725, VFX_GLAIVE_UNCHARGED_SPECIAL 2726,
            // PROJANIM_GLAIVE_01_SPECIAL 2727 / _02_SPECIAL 2728, PROJANIM_GLAIVE_01_REGULAR 2729 / _02_REGULAR 2730,
            // VFX_GLAIVE_01_IMPACT_SPECIAL_01 2731, VFX_GLAIVE_01_IMPACT_REGULAR_01 2732, VFX_GLAIVE_02_IMPACT_SPECIAL_01 2733,
            // VFX_GLAIVE_CHARGED_REGULAR 2734.
            "glaive" to listOf(2725, 2726, 2727, 2728, 2729, 2730, 2731, 2732, 2733, 2734),
            // OSRS max cape emote (every max cape, OSRS Wiki "Max cape"): gameval SpotanimID MAX_CAPE 1286 (sequence MAX_CAPE_SPOTANIM 7120);
            // the player plays MAX_CAPE_PLAYER_ANIM 7121 (SEQ_BATCHES, same batch name).
            "maxcapeemote" to listOf(1286),
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
            // Owner 2026-09-27 ("de animaties van zemouregal lijken niet helemaal ok"): his magic attack had no graphic. Gameval: his
            // attack is MAHJARRAT_ATTACK_MAGIC_05 (10123) and VFX_MAHJARRAT_ATTACK_LIGHTNING05_SPOTANIM (seq 11413) is the matching
            // "_05" lightning; the only spotanim on that sequence is LUC2_LUCIEN_LIGHTNING_SPOT 2864 (OSRS cache scan 2026-09-27), and
            // the breach npc's config name is deadman_breach_lucien (OSRS Wiki trivia). ADAPTED by name: no page states the pairing.
            "breachzemolightning" to listOf(2864),
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
            /*
             * Owner 2026-09-24: every normal-spellbook combat spell with the exact OSRS look, like the surges ("alleen normal
             * spellbook"). Names from RuneLite gameval SpotanimID; the 667 spotanims with these ids are the 2010 remakes
             * (OsrsFxProbeTool: different models/sequences), so they are imported, never referenced by id.
             */
            "standardspells" to
                listOf(
                    90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, // WIND/WATER/EARTH/FIRESTRIKE_CASTING / TRAVEL / IMPACT
                    117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, // WIND/WATER/EARTH/FIREBOLT_*
                    132, 133, 134, 135, 136, 137, 138, 139, 140, 129, 130, 131, // WIND/WATER/EARTH/FIREBLAST_*
                    158, 159, 160, 161, 162, 163, 164, 165, 166, 155, 156, 157, // WIND/WATER/EARTH/FIREWAVE_*
                    102, 103, 104, 105, 106, 107, 108, 109, 110, // CONFUSE / WEAKEN / CURSE _CASTING / TRAVEL / IMPACT
                    167, 168, 169, 170, 171, 172, 173, 174, 175, // VULNERABILITY / ENFEEBLE / STUN _*
                    177, 178, 179, 180, 181, // ENTANGLE_CASTING / TRAVEL / IMPACT, SNARE_IMPACT, BIND_IMPACT
                    145, 146, 147, // CRUMBLEUNDEAD_*
                    87, 88, 89, // IBANBLAST_*
                    328, 329, // SLAYER_MAGICDART_TRAVEL / IMPACT
                    76, 77, 78, // SARADOMIN_LIGHTNING, GUNTHIX_CLAW, ZAMORAK_FLAME
                ),
            // OSRS pet Beef's rare "Emote": the whirlwind that spins it round (RuneLite gameval SpotanimID VFX_BEEF_TORNADO 3587;
            // OSRS Wiki Beef "Emotes"). Beef's own sequences are in SEQ_BATCHES under the same name.
            "pet-beef" to listOf(3587),
            // N486 (owner 2026-10-31, balance plan step 4): Ruinous Powers Wrath on death shows OSRS's white clouds, not the purple
            // 667 curse Wrath (OSRS Wiki File:Wrath.gif). RuneLite gameval SpotanimID NEX_MUSHROOM_CLOUD_PROJANIM 2012,
            // NEX_MUSHROOM_CLOUD_SPOTANIM 2013, NEX_MUSHROOM_CLOUD_SPOTANIM_SMALL 2014 (Nex's own Wrath, the same effect).
            "ruinouswrath" to listOf(2012, 2013, 2014),
            // N522 bundle 1: Chaos Fanatic and Crazy archaeologist graphics whose OSRS definition differs from the 667 one with the
            // same id. 552 (the explosion on the target's tile) has a byte-identical spotanim definition but showed nothing in the 667 client
            // (test client 2026-11-01: its rev-667 sequence 3152 differs), so it is imported too. Ids: Kronos-184 bosses/ChaosFanatic.java (554 attack, 551 ground,
            // 157 explosion, 305 disarm hit), tarnish projectile_definitions "Chaos Fanatic Range" (553 cast), RuneLite gameval
            // SpotanimID CRAZY_ARCHAEOLOGIST_BOOK 1259 / _BOOK_SPECIAL 1260, RuneLite aoewarnings CHAOS_FANATIC_AOE 551.
            "wildy-bosses-1" to listOf(551, 553, 554, 157, 305, 1259, 1260, 552),
            // N522 bundle 2 (Scorpia), Kronos-184 bosses/scorpia: guardian heal projectile 109, guardian spawn 144, offspring projectile
            // 663. Same model/seq ids in both caches, imported anyway like 552 (the rev-667 sequences of the same id can differ).
            "wildy-bosses-2" to listOf(109, 144, 663),
            // N522 bundle 3b: the OSRS revenants' ranged projectile 206, magic projectile 1415, magic impact 1454 and heal 1221
            // (Kronos-184 activities/miscpvm/Revenant.java).
            "rev-caves" to listOf(206, 1415, 1454, 1221),
            // N522 bundle 3c: the Revenant maledictus - RuneLite gameval SpotanimID REVENANT_SUPERIOR_STANDARD_ATTACK 2033,
            // _SPECIAL_2_IMPACT 2034, _BARRAGE_IMPACT 2035, WINDSURGE_TRAVEL 1456, WINDBLAST_IMPACT 134, ICE_BARRAGE_IMPACT 369,
            // SHADOW_BURST_IMPACT 382 (Near Reality RevenantMaledictus.kt uses 2033, 1456, 134, 2034, 382, 369).
            "rev-maledictus" to listOf(2033, 2034, 2035, 1456, 134, 369, 382),
            // N522 bundle 4a: Venenatis - RuneLite gameval SpotanimID FX_VENENATIS_RANGED_PROJECTILE 2356 / _RANGED_IMPACT 2357 /
            // _MAGIC_PROJECTILE 2358 / _MAGIC_IMPACT 2359 / _WEB_PROJECTILE 2360 / _WEB_IMPACT 2361 (Near Reality Venenatis.java / WebObject).
            "venenatis" to listOf(2356, 2357, 2358, 2359, 2360, 2361),
            // N522 nachtrun deel 1: the OSRS Chaos Elemental's casting / travel / impact graphics (RuneLite gameval SpotanimID
            // CHAOSELEMENTAL_SPOTANIM_MADNESS_CASTING 550 .. _DISCORD_IMPACT 558). The 667 spotanims with these ids are the HD remakes
            // (other models and sequences, OsrsFxProbeTool 2026-11-04); 551-554 already came with wildy-bosses-1 (Chaos Fanatic).
            "chaos-elemental" to listOf(550, 555, 556, 557, 558),
            // N522 nachtrun deel 2: Artio (the Callisto rig) - RuneLite gameval SpotanimID FX_CALLISTO_TRAP 2343, FX_CALLISTO_RANGED_SPOTANIM
            // 2349 / _PROJECTILE 2350 / _IMPACT 2351, FX_CALLISTO_ENRAGE 2352 / _SMALL 2353, WINDBLAST_TRAVEL 133 (the knockback orb, dreambot
            // MagicFightArtio "Projectile: 133"; WINDBLAST_IMPACT 134 came with rev-maledictus).
            // Calvar'ion: RuneLite gameval SpotanimID FX_VETION_ATTACK_MAGIC_01 2344 / _02 2345, _MAGIC_IMPACT_01 2346 (blue lightning) /
            // _02 2347 (orange, enraged), FX_VETION_ATTACK_MELEE 2348 (Near Reality Vetion.java REGULAR_ / ENRAGED_LIGHTNING).
            "calvarion" to listOf(2344, 2345, 2346, 2347, 2348),
            // N522 ronde 2: Calvar'ion's shield-bash markers as the live game sends them - DreamBot CalvarionData LESSER_AOE_ATTACK
            // {1446, 2184} (graphics objects) = gameval GARGBOSS_DEBRIS_SHADOW_90 1446 + ZEBAK_ROAR_WAVE_DUST 2184.
            "n522-r2" to listOf(1446, 2184),
            "artio" to listOf(2343, 2349, 2350, 2351, 133, 1255), // 1255: Near Reality KNOCKBACK_PLAYER_GRAPHICS; // ENRAGE 2352/2353: skeletal sequence 3473408, refused (glow not shown)
            // N522 bundle 5 (cache 1151): the old Callisto's shockwave 395, heal 157, roar on the player 245; the old Vet'ion's magic 280 /
            // impact 281 (Kronos-184 bosses/Callisto.java, Vetion.java).
            "wildy-old" to listOf(395, 245, 280, 281), // heal 157 = 3127 (wildy-bosses-1)
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
            // Owner 2026-09-29 ("Belle's folly has a weird lunge attack"): OSRS Wiki "Belle's folly" - "the attack animation for
            // Belle's folly is identical to the Ghrazi rapier". Rapier set (gameval + the LostCity port manifest of OSRS obj 22324):
            // stab 8145 GHRAZI_RAPIER_ATTACK, slash 390 HUMAN_SWORD_SLASH (already imported), defence 4177 warguild_parry_defend.
            "rapierseq" to listOf(8145, 4177),
            // Owner 2026-09-29 (revenant weapons "fully working"): gameval AnimationID WILD_CAVE_CHAINMACE_CRUSH 245 / _STAB 246 /
            // _DEFEND 7200, HUMAN_SPECIAL_ACCURSED 9961, HUMAN_SPECIAL_URSINE 9962 / HUMAN_SPECIAL02_URSINE 9963.
            "revseq" to listOf(245, 246, 7200, 9961, 9962, 9963),
            // N522 bundle 1 follow-up (2026-11-02): the worn Fedora option "Tip" plays gameval AnimationID FEDORA_TIP 2418 (667 seq 2418 is
            // an npc animation on base 263, not the hat tip).
            "fedora-tip" to listOf(2418),
            // N541: the wall torches (OSRS loc 196) of the Edgeville jail animate with gameval AnimationID TORCH 481.
            "edgeville-jail-seq" to listOf(481),
            // N532: the animated locs of the OSRS surface squares 12345/12346/12601/12602 (volcano removal): gameval AnimationID
            // CANDLE_FLICKER 2564, ELEM1_QIP_FURNACE_GLOW 4860, WBR_VETION_ENTRANCE_FRAME 9985.
            "wilderness-volcano-seq" to listOf(2564, 4860, 9985),
            // N522: the animated locs of the four Wilderness boss lairs (OsrsRegionImportTool 'wildy-lairs'): gameval AnimationID
            // ROCK_POOL 1727, DTTD_LITTLE_DRIP 4217, LEAGUE3_FOG_IDLE 9227, COFFIN_VETION_CANDLES01 10028 (N522-WILDERNESS-ONDERZOEK-2 1b.4).
            "wildy-lairs-seq" to listOf(1727, 4217, 9227, 10028),
            // N564: the lava gas and crater locs around the Callisto's Den / Escape Caves entrances (gameval GAS 523, CRATER 524).
            "lair-surface-seq" to listOf(523, 524),
            // Owner 2026-11-04 21:5x: the animated locs of the OSRS squares 13114/13115 around the Escape Caves entrances
            // (OsrsRegionImportTool 'escape-surface'): gameval AnimationID CAMEL_STEAM 1881, WILD_BANNER 3940.
            "escape-surface-seq" to listOf(1881, 3940),
            // N523 step 1: the animated locs of the Theatre of Blood area (OsrsRegionImportTool 'tob-area'): gameval AnimationID FIRE 475,
            // DS2_LITHKREN_BARRIER_GLOW 7929, TOB_BLOAT_CHAMBER_IDLE 8086, TOB_BLOAT_SWINGING_CHAIN 8087, HAZARDOUS_WATER_CURSED_* 13516/13518/13521.
            "tob-area-seq" to listOf(475, 7929, 8086, 8087, 13516, 13518, 13521),
            // N462 ToA map: the animated locs of the raid block + lobby (OsrsRegionImportTool 'toa-raid' / 'toa-lobby'), all classic (no
            // animaya): gameval AnimationID WILLOWTHEWISP 1071, _100GUIDE_SPOTLIGHT 3445, LUNAR_DREAM_DREAM_LIGHT_SHIMMER 3542,
            // UNICORN_REWORK_READY 6374, GATEWAY_TOMB01_BARRIER01_IDLE01 9495, WALLKIT_TOMB_MONKEY01_BLINK01 9497, TOA_DUST_FALLING 9498,
            // POTTERY_TOMB_BOBBING01 9499, WALLKIT_TOMB_CROCODILE01_IDLE01 9500, TOA_TELEPORTER_IDLE 9501, TOA_PILLAR_IDLE 9503,
            // TOA_OSMUMTEN_CHEST_CLOSED 9504, CRONDIS_SPEAR_TRAP_IDLE 9562, TOA_WARDENS_DEBRIS_FLOATING 9712, FX_FIRE01_IDLE 9808.
            "toa-map-seq" to listOf(1071, 3445, 3542, 6374, 9495, 9497, 9498, 9499, 9500, 9501, 9503, 9504, 9562, 9712, 9808),
            // N462 ToA spotanims (GPT-A N462-notities\toa\50-spot-defs.tsv x 50-skeletal-sequences.tsv): 100 with classic sequences, then
            // the 43 with a skeletal sequence (baked by the skeletal spot route in main; SKELETAL_SPOT lines -> SkeletalSidecarExportTool).
            "toa-spot" to
                listOf(
                    355, 2075, 2076, 2077, 2078, 2079, 2080, 2081, 2132, 2133, 2134, 2135, 2136, 2137, 2138, 2139, 2140, 2145, 2152, 2153,
                    2154, 2155, 2156, 2157, 2158, 2159, 2160, 2161, 2162, 2163, 2164, 2165, 2167, 2168, 2169, 2170, 2171, 2174, 2175, 2180,
                    2181, 2182, 2184, 2187, 2188, 2189, 2190, 2193, 2194, 2195, 2197, 2198, 2199, 2200, 2201, 2202, 2203, 2205, 2206, 2207,
                    2208, 2209, 2211, 2212, 2213, 2224, 2225, 2226, 2227, 2228, 2236, 2237, 2238, 2239, 2240, 2242, 2243, 2244, 2245, 2246,
                    2247, 2248, 2250, 2251, 2252, 2253, 2254, 2255, 2256, 2257, 2258, 2259, 2260, 2953, 3027, 3178, 3763, 3979, 3980, 3981,
                    // skeletal (baked + SKELETAL_SPOT sidecar):
                    2141, 2142, 2143, 2144, 2146, 2147, 2148, 2149, 2150, 2151, 2166, 2172, 2173, 2176, 2177, 2178, 2179, 2183, 2185, 2186,
                    2191, 2192, 2204, 2210, 2214, 2215, 2216, 2217, 2218, 2219, 2220, 2221, 2222, 2223, 2229, 2230, 2231, 2232, 2233, 2234,
                    2235, 2241, 2249,
                ),
            // N462 Necropolis squares (OsrsRegionImportTool 'necropolis'): gameval AnimationID HAZARDOUS_WATER_SUNBAKED_01_3X3_IDLE_01 13509,
            // _2X2_IDLE_01 13510, _2X2_IDLE_02 13511, _1X1_IDLE_01 13512, _1X1_IDLE_02 13513, SEAWEED_SURFACE_IDLE01 13564.
            "necropolis-seq" to listOf(13509, 13510, 13511, 13512, 13513, 13564),
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
            // Owner 2026-09-24: normal-spellbook cast animations (RuneLite gameval AnimationID names; which spell uses which from
            // the combat-logger plugin's AnimationIds.java where it lists them): HUMAN_CASTSTRIKE (+_STAFF) strike/bolt/blast,
            // HUMAN_CASTWAVE (+_STAFF), HUMAN_CASTENTANGLE (+_STAFF) bind/snare/entangle, HUMAN_CASTCONFUSE / WEAKEN / CURSE /
            // ENFEEBLE / STUN (+_STAFF), HUMAN_CASTCRUMBLEUNDEAD (+_STAFF), HUMAN_CASTIBANBLAST, SLAYER_MAGICDART_CAST,
            // HUMAN_CASTING (god spells).
            "standardspellseq" to
                listOf(711, 1162, 727, 1167, 710, 1161, 716, 1163, 717, 1164, 718, 1165, 728, 1168, 729, 1169, 724, 1166, 708, 1576, 811),
            // HUMAN_CASTSTRIKE_STAFF again, on the player rig: the first batch reused the npc tool's 1162 (Malevolent Mage 15704) and
            // overwrote its frames; they were restored from the journal (tx-20260924-174902) and the player copy gets its own ids.
            "standardspellseq2" to listOf(1162),
            // Owner 2026-09-24 ("FIX alle OSRS GEPORTE WEAPONS ... met de correcte OSRS Attack animaties"): the classic melee and thrown
            // sequences the imported OSRS weapons play, as OSRS frames on the player rig instead of trusting the same-numbered 667
            // sequences (RuneLite gameval names): HUMAN_SWORD_STAB 386, HUMAN_SWORD_DEF 388, HUMAN_SWORD_SLASH 390, HUMAN_SWORD_LUNGE 392,
            // HUMAN_AXE_CHOP 393 (staff bash), HUMAN_STAFF_PUMMEL 414 (wand melee), HUMAN_SPEAR_SPIKE 428, HUMAN_SCYTHE_SWEEP 440,
            // II_HUMAN_DART_THROW_PVN 7554, HUMAN_STAKE2_PVN 7617 (thrown weapons against npcs).
            "osrsweaponseq" to listOf(386, 388, 390, 392, 393, 414, 428, 440, 7554, 7617),
            // The rest of the classes the imported weapons use: HUMAN_AXE_HACK 395, HUMAN_BLUNT_SPIKE 400, HUMAN_BLUNT_POUND 401,
            // HUMAN_DHSWORD_CHOP 406, HUMAN_DHSWORD_SLASH 407, HUMAN_SPEAR_LUNGE 429, HUMAN_BOW 426, XBOWS_HUMAN_FIRE_AND_RELOAD 4230,
            // II_HUMAN_DART_THROW 6600, HUMAN_STAKE2 929, HUMAN_CHINCHOMPA_ATTACK 2779, HUMAN_CHINCHOMPA_ATTACK_PVN 7618,
            // SLAYER_GRANITE_MAUL_ATTACK 1665.
            "osrsweaponseq2" to listOf(395, 400, 401, 406, 407, 429, 426, 4230, 6600, 929, 2779, 7618, 1665),
            // Claws (D_CLAWS_PUNCH 1067) and whips (SLAYER_ABYSSAL_WHIP_ATTACK 1658 / _DEFEND 1659).
            "osrsweaponseq3" to listOf(1067, 1658, 1659),
            // Owner 2026-10-04 153608 ("collosal blade is not wielded like the osrs item"): the blade's own stance, gameval AnimationID
            // HUMAN_WEAPON_GIANT01_RUN 9459 / _READY 9460 / _WALK 9461 / _WALKR 10054 / _WALKL 10055 (it attacks with the anchor's 5865).
            "colossalseq" to listOf(9459, 9460, 9461, 10054, 10055),
            // N34 (OsrsWeaponLooks against RuneLite weapon-animation-replacer data.json, the in-game observed sets): the OSRS DEFEND of
            // staves 420, wands 415, blunt 403, axes/pickaxes 397, 2h swords 410, spears 383, chinchompas 3176, and the Colossal blade's
            // observed attack 7516 / defend 7517 (the elder maul pair; DPS-helper plugin agrees on 7516).
            "n34seq" to listOf(420, 415, 403, 397, 410, 383, 3176, 7516, 7517, 424), // 424 bows/thrown: only an npc-rig copy existed
            // OSRS max cape emote, player side: MAX_CAPE_PLAYER_ANIM 7121 (the spotanim is in BATCHES under the same name).
            "maxcapeemote" to listOf(7121),
            // OSRS pet Beef (Interact -> Emote, OSRS Wiki Beef): the sniff COWBOSS_PET_EMOTE 13801 and, with the rare whirlwind,
            // COWBOSS_PET_EMOTE_CHARGE 13803 (5.16 s, the length of the whirlwind's own sequence 13802). RuneLite gameval AnimationID.
            "pet-beef" to listOf(13801, 13803),
            // K02 N397 (owner 2026-10-24: the OSRS pets' chat heads moved with the human poses and looked wrong): the pets' own OSRS
            // chathead sequences, RuneLite gameval AnimationID - SNAKEBOSS_PET_CHATHEAD_TALK 2415, ABYSSAL_SCION_CHATHEAD_TALK 7130,
            // ROCK_GOLEM_CHATHEAD_IDLE 7179, CHINCHOMPA_CHATHEAD_IDLE 7182, OLM_PET_CHATHEAD_IDLE 7399, GG_PET_CHATHEAD_TALK_BASIC
            // 7805 / _LAUGH 7804, DS2_VORKATH_CHATHEAD_TALK 7958, ZUKREK_CHATHEAD_TALK 7979, VERZIK_PET_CHATHEAD_TALK 8135,
            // HYDRA_CHATHEAD_TALK 8265, HUNLLEF_PET_CHAT_TALK 8427, ZALCANO_CHAT_TALK 8446, NIGHTMARE_PET_CHAT_TALK 8636 / _IDLE
            // 8635, JALTOKJAD_CHATHEAD_PET 8858, FSB_PET_CHATHEAD 8907.
            "pet-chatheads" to listOf(2415, 7130, 7179, 7182, 7399, 7804, 7805, 7958, 7979, 8135, 8265, 8427, 8446, 8635, 8636, 8858, 8907),
            // N522 ronde 2 / N576: the idle animation of the Ferox Enclave energy field (OSRS loc 39656 opcode 24 = seq 6954, classic),
            // dropped by the Ferox import - without it the field stands still at half height. FeroxBarrierOptionTool binds it to 62438.
            "n522-r2" to listOf(6954),
            // Owner 2026-10-10 ("de rejuvenation pool model van osrs importeren"): the water animation of OSRS loc 29241
            // "Ornate pool of Rejuvenation" (OsrsLocImportTool batch of the same name keeps it once this is imported).
            "poh-pool" to listOf(7304),
            // Owner 2026-10-10 (POH OSRS pieces): the idle animations of the Altar of the Occult multiloc 29150 (7303) and the
            // Portal Nexus 33410 (367), for OsrsLocImportTool batch "poh-house".
            "poh-house" to listOf(7303, 367),
            // N522 bundle 3: the only animated loc of the OSRS Revenant Caves squares (OSRS loc 40376, seq 4218), for
            // OsrsRegionImportTool batch "rev-caves".
            "rev-caves" to listOf(4218),
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
            // N581 (dagrun 2026-11-06): the hellhounds' summon cry wbr_vention_hellhound_call_01 6910 (N522-GELUIDSBRONNEN, owner V1
            // 2026-11-05 18:0x: the current OSRS Vet'ion sounds per function) - their "spawn" cue had no local sound yet.
            "n522-houndcall" to listOf(6910),
            // N3 (Accursed sceptre special silent): its special shows Vet'ion's own spell (SPELLS_VETION01_SPECIAL / _TRAVEL02), so it
            // casts with Vet'ion's magic-attack sound wbr_vetion_attack_magic_first_arm_movement_01 6862 (OSRS Wiki sound list; ADAPTED:
            // the weapon's own server-sent special sound is undocumented).
            "n3sfx" to listOf(6862),
            // Owner 2026-09-19 (Surge spells must sound like OSRS): OSRS Wiki "List of sound IDs" (raw wikitext 2026-09-19)
            // 4025 earthsurge_cast_and_fire, 4026 earthsurge_hit, 4027 windsurge_hit, 4028 windsurge_cast_and_fire,
            // 4029 watersurge_hit, 4030 watersurge_cast_and_fire, 4031 firesurge_hit, 4032 firesurge_cast_and_fire.
            "surgesfx" to listOf(4025, 4026, 4027, 4028, 4029, 4030, 4031, 4032),
            // Owner 2026-09-27 (Boss Spawn "must spawn, make a sound and disappear"; the OSRS breach sequences carry no frame
            // sounds and no breach sound name exists in the list): 4274 noa_rift_open_1 (appear), 4322 noa_rift_end_screech_1
            // (disappear) - ADAPTED, chosen by the owner.
            "breachspawnsfx" to listOf(4274, 4322),
            // Owner 2026-09-27 ("check that every breach monster has all its sounds", BreachAuditTests): OSRS Wiki "List of sound
            // IDs" - 3867 godwars_bandos_avatar_hit (Graardor defend), 3860 godwars_saradomin_avatar_death (Zilyana death);
            // ADAPTED by name family where OSRS names none: 255 tzhaar_ket_attack_punch (TzTok-Jad / Jad-Rek melee), 497
            // cave_horror_howl (Cave abomination attack, which already uses the Cave horror hit / death sounds).
            "breachsfx2" to listOf(3867, 3860, 255, 497),
            // Owner 2026-09-29 ("ancient godsword has the wrong blood sacrifice sound"): 2911 blood_sacrifice is the Ruby bolts (e)
            // sound (wiki note). The Ancient godsword came with Nex 2021 and its Blood Sacrifice is Nex's sacrifice mechanic
            // (mark, explode unless 5+ tiles away, heal): 5239 nex2021_sacrifice_attack_impact - ADAPTED by name.
            "agssfx" to listOf(5239),
            // Owner 2026-09-29 ("the 25 hit still sounds strange"): no source names the sound, so the other Nex 2021 sacrifice sounds
            // are imported for the owner to listen to and choose: 5182 nex2021_sacrifice_cast, 5192 nex2021_sacrifice_player_marked,
            // 5223 nex2021_sacrifice_start.
            "agssfx2" to listOf(5182, 5192, 5223),
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
        /**
         * Frame -> OSRS sound "location" (the audible radius in tiles). Breach audit 2026-09-27 (cause A1): it was dropped, so every
         * imported frame sound was packed with radius 0, which the 667 client (`Static431.method5827`) plays only for the local
         * player's own animation - a sound on an npc or on another player's animation was never heard by anyone.
         */
        val soundRadius: MutableMap<Int, Int> = sortedMapOf(),
        /** Every OSRS frame sound as [frame, synth, loops, radius], in cache order (two on one frame both kept). */
        val soundList: MutableList<IntArray> = mutableListOf(),
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
                        val location = c.u8()
                        c.u8() // retain
                        if (id >= 1 && loops >= 1) {
                            seq.sounds[frame] = id to loops
                            seq.soundRadius[frame] = location
                            seq.soundList += intArrayOf(frame, id, loops, location)
                        }
                    }
                    seq.dropped += "frame sound retain/weight (no 667 fields)"
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

    /** The 5-bit radius field of a 667 frame sound (`soundInfo & 0x1F`). */
    fun packedRadius(location: Int): Int = location.coerceIn(0, 31)

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
                    val packed = (sound.first shl 8) or (sound.second.coerceAtMost(7) shl 5) or packedRadius(seq.soundRadius[frame] ?: 0)
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

    /** "kind:upstream" keys the npc import tool owns (converted for an npc skeleton, never for the player rig). */
    fun npcOwnedFx(assetMap: File): Set<String> {
        val root = ObjectMapper(YAMLFactory()).readTree(assetMap) ?: return emptySet()
        return root.path("imports")
            .filter { it.path("fx_kind").isTextual && it.path("upstream_fx_id").isInt && it.path("status").asText() == "IMPORTED_BY_OSRS_NPC_TOOL" }
            .map { "${it.path("fx_kind").asText()}:${it.path("upstream_fx_id").asInt()}" }
            .toSet()
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
        val npcOwned = npcOwnedFx(assetMap)
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
                // A player sequence converts its frames onto the merged 667 player rig; OsrsNpcImportTool converts the same OSRS frames
                // 1:1 for an npc skeleton. The two conversions never share ids: reusing the npc tool's frameset/seq (2026-09-24: OSRS
                // 1162, Malevolent Mage 15704) overwrote the npc frames with player-rig frames.
                val playerSeq = framesets.any { fs ->
                    reader.files(INDEX_FRAMES, fs).values.firstOrNull()?.let { b -> (((b[0].toInt() and 0xFF) shl 8) or (b[1].toInt() and 0xFF)) == PLAYER_BASE } == true
                }
                fun key(kind: String, upstream: Int): String = if (playerSeq && "$kind:$upstream" in npcOwned) "player$kind" else kind
                val framesetMap = framesets.associateWith { fs -> local(key("frameset", fs), fs) { nextFrameset++ } }
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
                val localSeq = local(key("seq", osrsSeq), osrsSeq) { nextSeq++ }
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
                    var localModel = -1
                    // N462 (skeletal route A, 2026-11-06): a spotanim whose sequence is skeletal (animaya) gets the same treatment as the
                    // skeletal npcs (OsrsPetImportTool skeletal-npc): its model + sequence baked to classic frames (the timeline, frame sounds
                    // and the Software-SSE fallback); the client plays the real clip once SkeletalSidecarExportTool wrote skin_s<localSpot>.
                    val skeletalSeq = spot.seq.takeIf { it >= 0 }
                        ?.let { reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, it) }
                        ?.let { SkeletalBakeTool.decodeSkeletalSeq(it) }
                    if (skeletalSeq != null && spot.model >= 0) {
                        val localBase = local("spot_baked_base", spotId) { nextBase++ }
                        val baked = SkeletalBakeTool.bakeSet(reader, listOf(spot.model), listOf(spot.seq), localBase)
                        staged += { put(INDEX_BASES, localBase, 0, baked.base, "spotanim $spotId: baked base") }
                        localModel = local("spot_baked_model", spotId) { modelHoles.removeAt(0) }
                        staged += { put(ModelConvertTool.MODEL_INDEX, localModel, 0, baked.models[0], "spotanim $spotId: osrs model ${spot.model} baked") }
                        val frameset = local("spot_baked_frameset", spotId) { nextFrameset++ }
                        baked.frames[0].forEachIndexed { fi, bytes ->
                            staged += { put(INDEX_FRAMES, frameset, fi, bytes, "spotanim $spotId: baked frame $fi of osrs seq ${spot.seq}") }
                        }
                        val seq = Seq(frameDurations = baked.durations[0], frames = IntArray(baked.frames[0].size) { (frameset shl 16) or it })
                        val ticks = baked.ticks[0]
                        skeletalSeq.sounds.sortedBy { it[0] }.forEach { (tick, synth, loops, radius) ->
                            val localSynth = stageSynth(synth, staged)
                            var frame = ticks.indexOfLast { it <= tick }.coerceAtLeast(0)
                            while (frame in seq.sounds) frame++
                            if (frame >= ticks.size || loops < 1) {
                                dropped += "spotanim $spotId: seq ${spot.seq} sound $synth at tick $tick (no free baked frame)"
                            } else {
                                seq.sounds[frame] = localSynth to loops
                                seq.soundRadius[frame] = radius
                            }
                        }
                        val seq667 = encode667Seq(seq)
                        decode667SeqFrames(seq667)
                        localSeq = local("spot_baked_seq", spotId) { nextSeq++ }
                        val ls = localSeq
                        staged += { put(INDEX_SEQ, ls ushr 7, ls and 0x7F, seq667, "spotanim $spotId: baked osrs seq ${spot.seq}") }
                        dropped += spot.dropped.map { "spotanim $spotId: $it" }
                        val localSpot = local("spotanim", spotId) { nextSpot++ }
                        val spot667 = encode667Spot(spot, localModel, localSeq)
                        staged += { put(INDEX_SPOTANIM, localSpot ushr 8, localSpot and 0xFF, spot667, "osrs spotanim $spotId (skeletal, baked)") }
                        staged.forEach { it() }
                        println("PLAN spotanim $spotId -> $localSpot model ${spot.model}->$localModel seq ${spot.seq}->$localSeq SKELETAL vertices=${baked.vertices} error max=${"%.1f".format(baked.maxError)}")
                        println("SKELETAL_SPOT s$localSpot ${spot.model} $localSeq=${spot.seq}")
                        continue
                    }
                    if (spot.seq >= 0) localSeq = stageSeq(spot.seq, staged)
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
