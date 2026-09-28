package gg.rsmod.plugins.content.mechanics.pvp.breach

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import java.io.File
import java.io.FileReader

/**
 * The Deadman breach monsters (OSRS Wiki "Deadman Mode" #Breaches, navbox "Deadman breach monsters").
 *
 * Every entry is the wiki infobox version labelled "Deadman: Annihilation" (owner 2026-09-23: "all breach monsters need to have
 * the exact stats as deadmanmode anihilation"; only the hitpoints differ from the "Permanent" version),
 * imported from the pinned OSRS cache by `OsrsNpcImportTool deadman-breach` (local id = [Monster.id], OSRS id = [Monster.osrsId]).
 * Attack, defend and death sequences are the imported OSRS sequences (gameval AnimationID names in the tool batch); humanoid
 * monsters use the shared rev-667 human sequences like the imported Deadman guards.
 *
 * Attacks: a monster the wiki describes as attacking "like its regular version" clones the regular version's Void-sourced row
 * ([template], npc-attacks.json) with the breach max hits and the imported sequences; its combat_def stays the regular one so the
 * regular mechanics hooks (Dharok's Wretched Strength, K'ril's poison and slam, dragonfire types) apply unchanged. Other monsters
 * get their own sections built from the wiki text.
 *
 * Not in the roster: Kree'arra (not in Deadman: Annihilation breaches) and the seven skeletal-animation monsters
 * (Vardorvis, Scurrius, Phantom Muspah, Tumeken's and Elidinis' Wardens, Sol Heredit, Yama) - rev 667 cannot play their sequences.
 */
object BreachMonsters {
    class Stats(
        val hp: Int,
        val att: Int,
        val str: Int,
        val def: Int,
        val mage: Int,
        val range: Int,
        val attbns: Int = 0,
        val strbns: Int = 0,
        val amagic: Int = 0,
        val mbns: Int = 0,
        val arange: Int = 0,
        val rngbns: Int = 0,
        val dstab: Int = 0,
        val dslash: Int = 0,
        val dcrush: Int = 0,
        val dmagic: Int = 0,
        /** OSRS "dstandard" (rev 667 has one ranged defence; brothers.plugin.kts precedent). */
        val dranged: Int = 0,
    )

    /** How a monster fights: a cloned Void row ([Template]) or its own sections ([Custom]). */
    sealed class Attacks

    class Template(
        val rowId: Int,
        /** Void section id -> local sequence id (the imported OSRS sequence). */
        val anims: Map<String, Int> = emptyMap(),
        /** Void section id -> breach max hit (real hitpoints). */
        val maxHits: Map<String, Int> = emptyMap(),
        /** Void section id -> hits to add when the Void section has none (its damage came from a boss script). */
        val addHits: Map<String, NpcAttacks.HitDef> = emptyMap(),
        /** Void section id -> extra integer section fields (serialized names, e.g. `multi_target_radius`). */
        val sectionInts: Map<String, Map<String, Int>> = emptyMap(),
        /** Void section id -> cast (`sounds`), `target_sounds` and `impact_sounds` to set on the cloned section (null = keep). */
        val sectionSounds: Map<String, SectionSounds> = emptyMap(),
        /** Void section id -> projectile / impact graphic to set when the Void section has none. */
        val sectionVisuals: Map<String, SectionVisuals> = emptyMap(),
    ) : Attacks()

    class SectionSounds(
        val cast: List<Int>? = null,
        val target: List<Int>? = null,
        val impact: List<Int>? = null,
    )

    class SectionVisuals(
        val projectile: NpcAttacks.Proj? = null,
        val impactGfx: NpcAttacks.Gfx? = null,
    )

    class Custom(
        val combatDef: String,
        val range: Int,
        val sections: List<NpcAttacks.Attack>,
    ) : Attacks()

    class Monster(
        val id: Int,
        val osrsId: Int,
        val name: String,
        val stats: Stats,
        val speed: Int,
        val style: StyleType,
        val attackAnim: Int,
        val blockAnim: Int,
        val deathAnim: Int,
        val attacks: Attacks,
        /** Combat classes that deal no damage to it ("completely immune to melee and ranged attacks"). */
        val immuneMelee: Boolean = false,
        val immuneRanged: Boolean = false,
        /** Summoned by Zemouregal instead of spawning from a breach. */
        val summon: Boolean = false,
    )

    private fun hit(
        offense: String,
        max: Int,
        defence: String = offense,
    ) = NpcAttacks.HitDef(offense = offense, defence = defence, max = max * 10)

    private fun melee(
        style: String,
        anim: Int,
        max: Int,
        chance: Int = 1,
        hits: Int = 1,
        id: String = "melee",
        condition: String = "",
    ) = NpcAttacks.Attack(id = id, chance = chance, range = 1, condition = condition, anim = anim, hits = List(hits) { hit(style, max) })

    private fun proj(id: Int) = NpcAttacks.Proj(id = id, defHeight = 43, defEndHeight = 31, defDelay = 51, defCurve = 16, timeOffset = 5, multiplier = 5)

    private fun gfx(id: Int) = NpcAttacks.Gfx(id = id, defHeight = 60)

    // Local sequence ids assigned by OsrsNpcImportTool deadman-breach (asset map, transaction tx-20260919-162121).
    private const val DK_DEFEND = 15605
    private const val DK_DEATH = 15609

    // Local spotanim ids assigned by OsrsFxImportTool deadman-breach (transaction tx-20260919-161941).
    const val GFX_BREACH_PROJECTILE = 3050
    private const val GFX_SMOKE_DEVIL_HIT = 3051
    private const val GFX_SMOKE_DEVIL_PROJ = 3052
    private const val GFX_MA2_GUTHIX_PROJ = 3053
    private const val GFX_MA2_ZAMORAK_PROJ = 3054
    private const val GFX_ZEALOT_LIGHTNING = 3055
    const val GFX_SPLATTER_EXPLODE = 3056
    const val GFX_BLOAT_FLIES = 3061
    const val GFX_ZEMOUREGAL_SUMMON = 3064
    const val GFX_ZEMOUREGAL_LIGHTNING = 3177 // OSRS 2864, tx-20260927-191716
    private const val GFX_BLACK_CHINCHOMPA = 3065

    /*
     * OSRS sounds for the OSRS-only breach monsters (owner 2026-09-19: real OSRS sounds chosen by Jagex config name, OSRS Wiki
     * "List of sound IDs"; ADAPTED where only a family fits). OsrsFxImportTool batch "breachsfx", tx-20260919-194353; human hit /
     * death were already imported (10289 / 10290). Local id <- OSRS id and name:
     */
    const val SFX_HUMAN_ATTACK = 10295 // 2564 human_attack
    const val SFX_HUMAN_HIT = 10289 // 513 human_hit
    const val SFX_HUMAN_DEATH = 10290 // 512 human_death
    private const val SFX_WHIP = 10316 // 2720 whip
    private const val SFX_ICE_BARRAGE_IMPACT = 10317 // 168 ice_barrage_impact
    private const val SFX_BLOOD_BARRAGE_IMPACT = 10318 // 102 blood_barrage_impact
    private const val SFX_CHINCHOMPA_ATTACK = 10319 // 359 chinchompa_attack
    private const val SFX_CHINCHOMPA_EXPLODE = 10320 // 360 chinchompa_explode
    private const val SFX_ZEMO_LIGHTNING = 10321 // 7852 zemo_lightning (ADAPTED: Zemouregal's cast)
    const val SFX_BLOAT_FLIES = 10325 // 3544 tob_bloat_flies_attack_1

    /** rev-667 dragon scimitar attack sound (items.yml attack_audio of item 4587). */
    private const val SFX_DRAGON_SCIMITAR = 2500

    // OSRS Wiki "List of sound IDs": 2540 sever (the dragon scimitar special). Owner breachtrace 2026-09-28: I DSCIM YOU's Sever
    // swings were silent (16x "row-has-no-attack-sound").
    private const val SFX_SEVER = 2540

    /*
     * Owner live test 2026-09-28 ("veel sounds missen porazdir"): the Mage Arena II guardians' magic had no impact sound. OSRS does
     * not publish theirs; ADAPTED by god, the god spells' impact synths (SpellSounds, ids shared by the 667 cache and the OSRS Wiki
     * "List of sound IDs"): Porazdir (Zamorak) 1655, Derwen (Guthix) 1653, Justiciar Zachariah (Saradomin) 1659 saradomin_strike.
     */
    private const val SFX_FLAMES_OF_ZAMORAK = 1655
    private const val SFX_FIREBREATH = 159

    // Spell cast / impact synths of the players' spells (SpellSounds, Void magic.sounds.toml ids shared by the 667 cache).
    private const val SFX_FIRE_BLAST_CAST = 155
    private const val SFX_FIRE_BLAST_HIT = 156
    private const val SFX_ICE_BARRAGE_CAST = 171
    private const val SFX_BLOOD_BARRAGE_CAST = 106
    private const val SFX_TZHAAR_CLUB = 250
    private const val SFX_CLAWS_OF_GUTHIX = 1653
    private const val SFX_SARADOMIN_STRIKE = 1659

    private fun snd(id: Int) = listOf(NpcAttacks.Sound(id = id))

    /**
     * Breach conformance 2026-09-27 (A1): the God Wars generals' room-wide attacks. OSRS Wiki "General Graardor": "he slams the
     * ground, hitting all players in the room" and "Despite it being a Ranged attack, Graardor can only use it if he is within
     * melee range of his current target"; "Commander Zilyana": "She will only use her magic attacks at melee distance". Their Void
     * sections therefore keep range 1 (the earlier B7 change to 8 tiles contradicted the wiki and is undone), and hit every player
     * in the chamber (`multi_target_area` = the GWD room coordinates). A breach has no chamber - with the GWD coordinates the attack
     * found nobody there and hit the primary target alone. [attackRows] replaces every chamber area by the players within
     * [ROOM_RADIUS] tiles of the monster's footprint. ADAPTED: 8 tiles, half the long side of the Bandos chamber (13 x 19) the
     * wiki's "room" refers to.
     */
    const val ROOM_RADIUS = 8

    /** Breach monster -> OSRS (attack, hit/defend, death) sound, local ids; -1 = none (e.g. the sound is on the death sequence). */
    val SOUND_ROWS: Map<Int, Triple<Int, Int, Int>> =
        mapOf(
            14446 to Triple(10292, 10293, 10294), // Porazdir: demon_attack / demon_hit / demon_death (ADAPTED family)
            14447 to Triple(SFX_HUMAN_ATTACK, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Justiciar Zachariah
            14448 to Triple(10296, 10297, 10298), // Derwen: lore_ent_attack / _defend / _death (ADAPTED family)
            14453 to Triple(10299, 10300, 10301), // Jal-ImKot: lavabeast_* (ADAPTED family)
            14456 to Triple(10302, 10303, 10304), // Sulphur Lizard: lizard_*
            14458 to Triple(10305, 10306, 10307), // Cerberus: wbr_vention_hellhound_attack_bark / defend_snarl / death
            14459 to Triple(10308, 10309, -1), // Bee Swarm: varl bee_swarm_attack01 / swarm_hit; death sound is on its death sequence
            14460 to Triple(10310, 10311, 10312), // Thermonuclear smoke devil: dust_devil_* (ADAPTED family)
            14461 to Triple(10313, 10314, 10315), // Jaguar warrior: jaguar_attack_01 / jaguar_defend_01 / jaguar_death_01
            14463 to Triple(-1, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Durial321 (whip / ice barrage sounds on its sections)
            14464 to Triple(-1, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Magic Mark (blood barrage impact on its section)
            14465 to Triple(-1, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Ranging Ro (chinchompa sounds on its section)
            14466 to Triple(-1, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Zemouregal (zemo_lightning on its section)
            14468 to Triple(10322, 10323, 10324), // Splatter: splatter_attack / _hit / _death
            14469 to Triple(-1, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // I DSCIM YOU (dragon scimitar sound on its sections)
            14470 to Triple(-1, 10326, 10327), // Pestilent Bloat: tob_bloat_hit / tob_bloat_death (flies: SFX_BLOAT_FLIES)
            14473 to Triple(10328, 10329, 10330), // Undead One summon: zombie_*
            // Owner 2026-09-27 sound audit (BreachAuditTests): OsrsFxImportTool batch breachsfx2, tx-20260927-153802.
            // Graardor: attack/death are frame sounds of his OSRS sequences; defend = 3867 godwars_bandos_avatar_hit.
            14442 to Triple(-1, 10341, -1),
            // Zilyana: attack/defend frame sounds (a skipped block animation's frame sound is sent by NpcCombatAudio.onDefend);
            // death = 3860 godwars_saradomin_avatar_death.
            14443 to Triple(-1, -1, 10342),
            // Cave abomination: attack 497 cave_horror_howl (ADAPTED), hit / death of the Cave horror row it already used (500 / 480).
            14452 to Triple(10344, 500, 480),
            // TzTok-Jad / Jad-Rek melee: 255 tzhaar_ket_attack_punch (ADAPTED, TzHaar family); hit / death of the 667 Jad row (410 / 409).
            // Owner live test 2026-09-28: the ADAPTED tzhaar_ket_attack_punch (10343) was too soft to hear; OSRS publishes no Jad melee
            // sound (Void names an undefined "tztok_jad_attack"). ADAPTED: 250 tzhaar_ket_attack_club (OSRS Wiki list, 667 cache id).
            14462 to Triple(SFX_TZHAAR_CLUB, 410, 409),
            14477 to Triple(SFX_TZHAAR_CLUB, 410, 409),
            14475 to Triple(SFX_HUMAN_ATTACK, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Fremennik summon
            14476 to Triple(SFX_HUMAN_ATTACK, SFX_HUMAN_HIT, SFX_HUMAN_DEATH), // Khazard summon
        )

    // Jaguar warrior's OSRS sequences (asset map seq:10847/10848/10849, tx-20260919-162121).
    private const val JAGUAR_ATTACK = 15669
    private const val JAGUAR_DEFEND = 15670
    private const val JAGUAR_DEATH = 15671

    // rev-667 Fire Blast (CombatSpell.FIRE_BLAST: Gfx.FIRE_BLAST_PROJ / FIRE_BLAST_IMPACT) for the Night beast's casts.
    private const val GFX_FIRE_BLAST_PROJ = 2731
    private const val GFX_FIRE_BLAST_IMPACT = 2739

    // rev-667 cache ids shared with the player combat code (Anims/Gfx), used by the humanoid monsters.
    private const val HUMAN_DEATH = 836

    // OSRS HUMAN_UNARMEDBLOCK 424 / HUMAN_DEATH 836 imported for the humanoids that stand on an imported OSRS human sequence.
    private const val OSRS_HUMAN_BLOCK = 15706
    private const val OSRS_HUMAN_DEATH = 15707
    private const val HUMAN_BLOCK = 424
    private const val WHIP_ATTACK = 1658
    private const val DSCIM_ATTACK = 390
    private const val ANCIENT_CAST = 1979
    private const val CHINCHOMPA_THROW = 2779

    val ROSTER: List<Monster> =
        listOf(
            Monster(
                14438, 12439, "Dagannoth Rex",
                Stats(3500, 255, 255, 255, 0, 255, dstab = 255, dslash = 255, dcrush = 255, dmagic = 10, dranged = 255),
                5, StyleType.SLASH, 15606, DK_DEFEND, DK_DEATH,
                Template(2883, anims = mapOf("melee" to 15606), maxHits = mapOf("melee" to 26)),
            ),
            Monster(
                14439, 12440, "King Black Dragon",
                Stats(500, 240, 240, 150, 240, 1, dstab = 70, dslash = 90, dcrush = 90, dmagic = 80, dranged = 40),
                4, StyleType.STAB, 15611, 15613, 15612,
                Template(
                    50,
                    anims = mapOf("melee" to 15611, "dragonfire" to 15610, "toxic" to 15610, "ice" to 15610, "shock" to 15610),
                    maxHits = mapOf("melee" to 25, "dragonfire" to 65),
                    // Owner 2026-09-28: the toxic / ice / shock breaths had no sound of their own. OSRS Wiki "List of sound IDs": 587
                    // toxicbreath, 586 kingicebreath, 584 lightningbreath (ids shared by the 667 cache); dragonfire keeps 3750.
                    sectionSounds =
                        mapOf(
                            "toxic" to SectionSounds(target = listOf(587)),
                            "ice" to SectionSounds(target = listOf(586)),
                            "shock" to SectionSounds(target = listOf(584)),
                        ),
                ),
            ),
            Monster(
                14440, 12441, "Dagannoth Supreme",
                Stats(3000, 255, 255, 84, 255, 255, dstab = 10, dslash = 10, dcrush = 10, dmagic = 255, dranged = 550),
                4, StyleType.RANGED, 15608, DK_DEFEND, DK_DEATH,
                // The Deadman infobox gives no max hit; the regular Dagannoth Supreme's (30) is kept from the template.
                // "capable of targeting multiple players at once, like its regular counterpart" - OSRS Wiki "Dagannoth Supreme":
                // "sending projectiles at any player standing within the 6x6 area around his target" (ADAPTED: radius 3, the
                // centred square an even 6x6 cannot be).
                Template(
                    2881,
                    anims = mapOf("ranged" to 15608),
                    sectionInts = mapOf("ranged" to mapOf("multi_target_radius" to 3, "multi_radius" to 3)),
                ),
            ),
            Monster(
                14441, 12442, "Dagannoth Prime",
                Stats(3000, 255, 255, 125, 255, 0, dstab = 255, dslash = 255, dcrush = 255, dmagic = 255, dranged = 10),
                4, StyleType.MAGIC, 15607, DK_DEFEND, DK_DEATH,
                Template(2882, anims = mapOf("magic" to 15607), maxHits = mapOf("magic" to 26)),
            ),
            Monster(
                14442, 12444, "General Graardor",
                Stats(2000, 280, 350, 250, 80, 350, attbns = 120, strbns = 43, arange = 100, rngbns = 40, dstab = 90, dslash = 90, dcrush = 90, dmagic = 201, dranged = 90),
                6, StyleType.CRUSH, 15614, 15615, 15616,
                Template(6260, anims = mapOf("melee" to 15614, "range" to 15617), maxHits = mapOf("melee" to 60)),
            ),
            Monster(
                14443, 12445, "Commander Zilyana",
                Stats(1500, 280, 196, 150, 300, 250, attbns = 195, strbns = 20, amagic = 200, dstab = 100, dslash = 100, dcrush = 100, dmagic = 100, dranged = 100),
                2, StyleType.CRUSH, 15618, 15620, 15619,
                Template(6247, anims = mapOf("melee" to 15618, "magic" to 15621), maxHits = mapOf("melee" to 27)),
            ),
            Monster(
                14444, 12446, "K'ril Tsutsaroth",
                Stats(1500, 340, 300, 270, 200, 1, attbns = 160, strbns = 31, dstab = 80, dslash = 80, dcrush = 80, dmagic = 130, dranged = 80),
                6, StyleType.SLASH, 15623, 15622, 15624,
                Template(
                    6203,
                    anims = mapOf("melee" to 15623, "melee_poison" to 15623, "melee_slam" to 15623, "melee_slam_poison" to 15623, "magic" to 15625),
                    maxHits = mapOf("melee" to 46, "melee_poison" to 46),
                ),
            ),
            Monster(
                14445, 12447, "Dharok the Wretched",
                Stats(300, 100, 100, 200, 1, 1, strbns = 105, amagic = -58, arange = -18, dstab = 252, dslash = 250, dcrush = 244, dmagic = -11, dranged = 249),
                // Stands on the imported OSRS BARROW_DHAROK_READY, so BARROW_DHAROK_SLASH and the OSRS human block/death are used.
                7, StyleType.SLASH, 15703, OSRS_HUMAN_BLOCK, OSRS_HUMAN_DEATH,
                Template(2026, anims = mapOf("melee" to 15703), maxHits = mapOf("melee" to 29)),
            ),
            Monster(
                14446, 12448, "Porazdir",
                Stats(2000, 250, 150, 100, 180, 1, amagic = 80, mbns = 80, dstab = 200, dslash = 200, dcrush = 200, dmagic = -60, dranged = 200),
                6, StyleType.MAGIC, 15628, 15626, 15629,
                Custom(
                    "breach_porazdir", 8,
                    listOf(
                        NpcAttacks.Attack(id = "magic", chance = 2, range = 8, anim = 15628, projectiles = listOf(proj(GFX_MA2_ZAMORAK_PROJ)), impactSounds = snd(SFX_FLAMES_OF_ZAMORAK), hits = listOf(hit("magic", 43))),
                        melee("slash", 15627, 16),
                    ),
                ),
                immuneMelee = true, immuneRanged = true,
            ),
            Monster(
                14447, 12449, "Justiciar Zachariah",
                Stats(2000, 500, 250, 100, 180, 1, attbns = 200, amagic = 80, mbns = 80, dstab = 200, dslash = 200, dcrush = 200, dmagic = -60, dranged = 200),
                6, StyleType.SLASH, 15630, 15633, 15631,
                Custom(
                    "breach_justiciar", 8,
                    listOf(
                        melee("slash", 15630, 43),
                        NpcAttacks.Attack(id = "magic", range = 8, anim = 15632, impactGfx = listOf(gfx(GFX_ZEALOT_LIGHTNING)), impactSounds = snd(SFX_SARADOMIN_STRIKE), hits = listOf(hit("magic", 26))),
                    ),
                ),
                immuneMelee = true, immuneRanged = true,
            ),
            Monster(
                14448, 12450, "Derwen",
                Stats(2000, 250, 150, 100, 180, 1, amagic = 80, mbns = 80, dstab = 200, dslash = 200, dcrush = 200, dmagic = -60, dranged = 200),
                6, StyleType.MAGIC, 15636, 15634, 15637,
                Custom(
                    "breach_derwen", 8,
                    listOf(
                        NpcAttacks.Attack(id = "magic", chance = 2, range = 8, anim = 15636, projectiles = listOf(proj(GFX_MA2_GUTHIX_PROJ)), impactSounds = snd(SFX_CLAWS_OF_GUTHIX), hits = listOf(hit("magic", 43))),
                        melee("crush", 15635, 16),
                    ),
                ),
                immuneMelee = true, immuneRanged = true,
            ),
            Monster(
                14449, 12451, "Greater abyssal demon",
                Stats(2000, 300, 260, 150, 1, 1, dstab = 50, dslash = 50, dcrush = 50, dmagic = 0, dranged = 50),
                4, StyleType.STAB, 15638, -1, 15639,
                Custom("breach_greater_abyssal_demon", 1, listOf(melee("stab", 15638, 27))),
            ),
            Monster(
                14450, 12452, "Giant goblin",
                Stats(3500, 150, 150, 200, 1, 1, attbns = 120, strbns = 43, dstab = -15, dslash = -15, dcrush = -15, dmagic = -15, dranged = -15),
                5, StyleType.CRUSH, 15643, 15642, 15641,
                Custom("breach_giant_goblin", 1, listOf(melee("crush", 15643, 27))),
            ),
            Monster(
                14451, 12453, "Flaming pyrelord",
                Stats(3500, 150, 200, 150, 1, 1, dstab = 18, dslash = 18, dcrush = 18, dmagic = 150, dranged = 18),
                // "applies the burn status effect with each hit, dealing 2 hitsplats of 5 burn damage each" (max hit 1, 11 with burn).
                4, StyleType.MAGIC, 15646, 15645, 15644,
                Custom("breach_pyrelord", 1, listOf(NpcAttacks.Attack(id = "magic", range = 1, anim = 15646, hits = listOf(hit("magic", 1))))),
            ),
            Monster(
                14452, 12454, "Cave abomination",
                Stats(3000, 280, 250, 142, 230, 1),
                5, StyleType.CRUSH, 15649, 15647, 15648,
                Custom("breach_cave_abomination", 1, listOf(melee("crush", 15649, 26))),
            ),
            Monster(
                14453, 12455, "Jal-ImKot",
                Stats(3000, 210, 290, 120, 120, 220, strbns = 40, dstab = 65, dslash = 65, dcrush = 65, dmagic = 30, dranged = 50),
                5, StyleType.SLASH, 15650, 15651, 15652,
                Custom("breach_jal_imkot", 1, listOf(melee("slash", 15650, 49))),
            ),
            Monster(
                14454, 12456, "Malevolent Mage",
                Stats(2000, 1, 1, 150, 255, 1, dmagic = 300),
                // The Deadman infobox gives no max hit; the regular Malevolent Mage's 20 is used (OSRS Wiki "Malevolent Mage").
                // Stands on the imported OSRS HUMAN_STAFFREADY: HUMAN_CASTSTRIKE_STAFF (ADAPTED, the wiki names no cast animation).
                4, StyleType.MAGIC, 15704, OSRS_HUMAN_BLOCK, OSRS_HUMAN_DEATH,
                // Owner live test 2026-09-28 ("maakt maar 1 soort geluid"): the Infernal Mage row it clones casts with fireblast_hit (156, OSRS
                // Wiki "List of sound IDs") as its only cue and has no projectile - its spell is Fire Blast. It now casts like the players'
                // OSRS Fire Blast (CombatSpell.FIRE_BLAST: projectile 3114, impact 3115; SpellSounds cast 155 / impact 156). ADAPTED.
                Template(
                    1643, anims = mapOf("magic" to 15704), maxHits = mapOf("magic" to 20),
                    sectionSounds = mapOf("magic" to SectionSounds(cast = listOf(155), target = emptyList(), impact = listOf(156))),
                    sectionVisuals = mapOf("magic" to SectionVisuals(projectile = proj(3114), impactGfx = NpcAttacks.Gfx(id = 3115, defHeight = 32))),
                ),
            ),
            Monster(
                14455, 12457, "Vitreous warped Jelly",
                Stats(3000, 200, 250, 50, 180, 1),
                4, StyleType.MAGIC, 15654, 15653, 15655,
                Template(1637, anims = mapOf("attack" to 15654), maxHits = mapOf("attack" to 19)),
            ),
            Monster(
                14456, 12458, "Sulphur Lizard",
                Stats(2500, 150, 200, 75, 1, 1, dstab = 15, dslash = 25, dcrush = 25, dmagic = 0, dranged = 15),
                // "It attacks with melee, knocking back any players hit by it."
                5, StyleType.CRUSH, 15656, -1, 15657,
                Custom("breach_sulphur_lizard", 1, listOf(melee("crush", 15656, 21))),
            ),
            Monster(
                14457, 12459, "Night beast",
                Stats(1000, 270, 290, 100, 300, 1, dstab = 75, dslash = 80, dcrush = 120, dmagic = 100, dranged = 100),
                // OSRS Wiki "Night beast (Deadman)": "attacks similar to its regular variant, and is capable of using its special 3x3
                // fireball attack". Regular "Night beast": first attack always magic, melee whenever it can reach, magic otherwise;
                // the standard magic hits up to 8 (Mod Ash, cited there - the Deadman infobox magic max is "?"). Special: it stops
                // attacking briefly, then three Fire Blast-like casts on the 3x3 around the target, each floor(current HP / 3) with
                // no accuracy roll (deadman_breach.plugin.kts, NightBeast*). Visuals: Fire Blast projectile/impact (rev-667 ids).
                4, StyleType.CRUSH, 15658, 15659, 15660,
                Custom(
                    "breach_night_beast", 8,
                    listOf(
                        melee("crush", 15658, 30, condition = "breach_nb_melee"),
                        NpcAttacks.Attack(
                            id = "magic", range = 8, condition = "breach_nb_magic", anim = 15658,
                            projectiles = listOf(proj(GFX_FIRE_BLAST_PROJ)), impactGfx = listOf(NpcAttacks.Gfx(id = GFX_FIRE_BLAST_IMPACT, defHeight = 32)),
                            // Fire Blast cast / impact synths (SpellSounds FIRE_BLAST 155 / 156): the casts "resemble Fire Blast" (OSRS Wiki).
                            sounds = snd(SFX_FIRE_BLAST_CAST), impactSounds = snd(SFX_FIRE_BLAST_HIT),
                            hits = listOf(hit("magic", 8)),
                        ),
                        NpcAttacks.Attack(
                            id = "fireball", range = 8, condition = "breach_nb_fireball", anim = 15658, multiTargetRadius = 1, multiRadius = 1,
                            impactRegardless = true, projectiles = listOf(proj(GFX_FIRE_BLAST_PROJ)), sounds = snd(SFX_FIRE_BLAST_CAST), impactSounds = snd(SFX_FIRE_BLAST_HIT),
                            impactGfx = listOf(NpcAttacks.Gfx(id = GFX_FIRE_BLAST_IMPACT, defHeight = 32)),
                        ),
                    ),
                ),
            ),
            Monster(
                14458, 13657, "Cerberus",
                Stats(2000, 300, 280, 150, 220, 220, attbns = 50, amagic = 50, arange = 50, dstab = 50, dslash = 100, dcrush = 25, dmagic = 100, dranged = 100),
                // "It attacks with melee using all three heads, resulting in three hitsplats per attack."
                3, StyleType.STAB, 15662, 15661, 15663,
                Custom("breach_cerberus", 1, listOf(melee("stab", 15662, 29, hits = 3))),
            ),
            Monster(
                14459, 13658, "Bee Swarm",
                Stats(5000, 400, 100, 30, 1, 1, attbns = 200, dstab = 10, dslash = 10, dcrush = 10, dmagic = 0, dranged = 0),
                1, StyleType.CRUSH, 15572, -1, 15665,
                Custom("breach_bee_swarm", 1, listOf(melee("crush", 15572, 11))),
            ),
            Monster(
                14460, 13659, "Thermonuclear smoke devil",
                Stats(1500, 230, 220, 150, 1, 310, dstab = 30, dslash = 12, dcrush = 20, dmagic = 200, dranged = 300),
                2, StyleType.RANGED, 15666, 15667, 15668,
                Custom(
                    "breach_smoke_devil", 8,
                    listOf(
                        NpcAttacks.Attack(
                            id = "range", range = 8, anim = 15666, projectiles = listOf(proj(GFX_SMOKE_DEVIL_PROJ)),
                            impactGfx = listOf(gfx(GFX_SMOKE_DEVIL_HIT)), hits = listOf(hit("range", 32)),
                        ),
                    ),
                ),
            ),
            Monster(
                14461, 13660, "Jaguar warrior",
                Stats(3000, 200, 165, 100, 100, 160, strbns = 15, dstab = 50, dslash = 50, dcrush = 50, dmagic = 100, dranged = 50),
                // Max hit "21 (x4)": four slash hitsplats per attack. Owner 2026-09-19 "fix everything": it now stands on its own imported
                // OSRS human stand/walk (OsrsNpcImportTool.OWN_OSRS_MOVEMENT_NPCS), so the exact OSRS NPC_JAGUAR_RANGER_CLAWS_ATTACK /
                // NPC_JAGUAR_HUMAN_UNARMED_DEF / NPC_JAGUAR_HUMAN_DEATH (upstream 10847-10849 -> local 15669-15671) animate it.
                6, StyleType.SLASH, JAGUAR_ATTACK, JAGUAR_DEFEND, JAGUAR_DEATH,
                Custom("breach_jaguar_warrior", 1, listOf(melee("slash", JAGUAR_ATTACK, 21, hits = 4))),
            ),
            Monster(
                14462, 13661, "TzTok-Jad",
                Stats(5000, 640, 770, 200, 750, 750),
                // Permanent max hits: 78 melee, 108 magic and ranged.
                8, StyleType.STAB, 15675, 15673, 15674,
                Template(
                    2745,
                    anims = mapOf("melee" to 15675, "range" to 15672, "magic" to 15676),
                    maxHits = mapOf("melee" to 78),
                    addHits = mapOf("range" to hit("range", 108), "magic" to hit("magic", 108)),
                    // OSRS Wiki "List of sound IDs": 159 firebreath "also used for the start of TzTok-Jad's magic attack".
                    sectionSounds = mapOf("magic" to SectionSounds(cast = listOf(SFX_FIREBREATH))),
                ),
            ),
            Monster(
                14463, 13662, "Durial321",
                Stats(2500, 95, 98, 88, 94, 80, strbns = 105, amagic = 150, arange = -12, dstab = 148, dslash = 134, dcrush = 162, dmagic = 69, dranged = 88),
                // Melee and Ice Barrage ("?" magic max on the infobox: the spell's own 30 is used).
                4, StyleType.SLASH, WHIP_ATTACK, HUMAN_BLOCK, HUMAN_DEATH,
                Custom(
                    "breach_durial321", 8,
                    listOf(
                        // Owner 2026-09-27: one Ice Barrage to open, then the whip (conditions in deadman_breach.plugin.kts).
                        NpcAttacks.Attack(
                            id = "melee", range = 1, condition = "breach_durial_whip", anim = WHIP_ATTACK, targetSounds = snd(SFX_WHIP),
                            hits = listOf(hit("slash", 28)),
                        ),
                        NpcAttacks.Attack(
                            id = "ice_barrage", range = 8, condition = "breach_durial_barrage", anim = ANCIENT_CAST, impactGfx = listOf(NpcAttacks.Gfx(id = 369)), sounds = snd(SFX_ICE_BARRAGE_CAST),
                            impactSounds = snd(SFX_ICE_BARRAGE_IMPACT), hits = listOf(hit("magic", 30)),
                        ),
                    ),
                ),
            ),
            Monster(
                14464, 13663, "Magic Mark",
                Stats(3500, 10, 30, 30, 175, 0, amagic = 100, dstab = 25, dslash = 25, dcrush = 25, dmagic = 150, dranged = 10),
                // Blood spells hitting an area around the target and healing him by the damage dealt.
                5, StyleType.MAGIC, ANCIENT_CAST, HUMAN_BLOCK, HUMAN_DEATH,
                Custom(
                    "breach_magic_mark", 8,
                    listOf(
                        NpcAttacks.Attack(
                            id = "blood_barrage", range = 8, anim = ANCIENT_CAST, multiTargetRadius = 1, multiRadius = 1, sounds = snd(SFX_BLOOD_BARRAGE_CAST),
                            impactGfx = listOf(NpcAttacks.Gfx(id = 377)), impactSounds = snd(SFX_BLOOD_BARRAGE_IMPACT), hits = listOf(hit("magic", 18)),
                        ),
                    ),
                ),
            ),
            Monster(
                14465, 13664, "Ranging Ro",
                Stats(3500, 10, 30, 100, 15, 150, amagic = 100, dstab = 100, dslash = 100, dcrush = 100, dmagic = 200, dranged = 100),
                // Black chinchompas hitting an area around the target.
                4, StyleType.RANGED, CHINCHOMPA_THROW, HUMAN_BLOCK, HUMAN_DEATH,
                Custom(
                    "breach_ranging_ro", 8,
                    listOf(
                        NpcAttacks.Attack(
                            id = "chinchompa", range = 8, anim = CHINCHOMPA_THROW, multiTargetRadius = 1, multiRadius = 1,
                            projectiles = listOf(proj(GFX_BLACK_CHINCHOMPA)), impactGfx = listOf(NpcAttacks.Gfx(id = 157)),
                            targetSounds = snd(SFX_CHINCHOMPA_ATTACK), impactSounds = snd(SFX_CHINCHOMPA_EXPLODE), hits = listOf(hit("range", 16)),
                        ),
                    ),
                ),
            ),
            Monster(
                14466, 15237, "Zemouregal",
                Stats(2500, 1, 1, 125, 255, 1, dstab = 30, dslash = 30, dcrush = 30, dmagic = 255, dranged = 15),
                7, StyleType.MAGIC, 15677, 15680, 15679,
                // Lightning on the target with its sound (owner 2026-09-27: his attack had no graphic): OSRS spotanim 2864
                // LUC2_LUCIEN_LIGHTNING_SPOT on seq VFX_MAHJARRAT_ATTACK_LIGHTNING05 (ADAPTED by name, OsrsFxImportTool breachzemolightning).
                Custom(
                    "breach_zemouregal", 8,
                    listOf(
                        NpcAttacks.Attack(
                            id = "magic", range = 8, anim = 15677, targetGfx = listOf(gfx(GFX_ZEMOUREGAL_LIGHTNING)), targetSounds = snd(SFX_ZEMO_LIGHTNING),
                            hits = listOf(hit("magic", 23)),
                        ),
                    ),
                ),
            ),
            Monster(
                14467, 15547, "Big Evil Chicken",
                Stats(3500, 255, 255, 25, 300, 0, dstab = 25, dslash = 25, dcrush = 25, dmagic = 255, dranged = 0),
                3, StyleType.MAGIC, 15683, 15681, 15682,
                Template(3375, anims = mapOf("magic" to 15683), maxHits = mapOf("magic" to 26)),
            ),
            Monster(
                14468, 15550, "Splatter",
                Stats(2000, 200, 250, 10, 300, 1),
                4, StyleType.CRUSH, 15686, 15685, 15684,
                Custom("breach_splatter", 1, listOf(melee("crush", 15686, 26))),
            ),
            Monster(
                14469, 15553, "I DSCIM YOU",
                Stats(1500, 150, 200, 25, 100, 25, dstab = 25, dslash = 25, dcrush = 25, dmagic = 50, dranged = 10),
                // Dragon scimitar; its special attack (Sever) turns the target's protection prayers off.
                3, StyleType.SLASH, DSCIM_ATTACK, HUMAN_BLOCK, HUMAN_DEATH,
                Custom(
                    "breach_dscim",
                    1,
                    // "It will always use the special attack if its target isn't currently under the effect of the special attack":
                    // the two sections are gated by the conditions registered in deadman_breach.plugin.kts. Rev-667 Sever look
                    // (12031 / 2118) as the players' dragon scimitar special (melee_specials.plugin.kts).
                    listOf(
                        NpcAttacks.Attack(id = "melee", range = 1, condition = "breach_target_severed", anim = DSCIM_ATTACK, targetSounds = snd(SFX_DRAGON_SCIMITAR), hits = listOf(hit("slash", 21))),
                        NpcAttacks.Attack(
                            id = "sever", range = 1, condition = "breach_sever_ready", anim = 12031, gfx = listOf(NpcAttacks.Gfx(id = 2118)),
                            sounds = snd(SFX_SEVER), hits = listOf(hit("slash", 21)),
                        ),
                    ),
                ),
            ),
            Monster(
                14470, 15556, "Pestilent Bloat",
                Stats(2000, 250, 340, 100, 150, 180, attbns = 150, strbns = 82, arange = 180, rngbns = 4, dstab = 40, dslash = 20, dcrush = 40, dmagic = 600, dranged = 800),
                // "It does not directly attack": its flies are issued by DeadmanBreach.bloatFlies, not by a combat section.
                5, StyleType.RANGED, -1, -1, 15687,
                Custom("breach_bloat", 0, emptyList()),
            ),
            // Zemouregal Summons (OSRS Wiki "Zemouregal Summon" versions Mummy / Shade / Undead One / Pirate / Fremennik / Khazard).
            Monster(14471, 15558, "Zemouregal Summon", Stats(35, 250, 150, 150, 1, 1), 4, StyleType.CRUSH, 15688, 15689, 15690, Custom("breach_summon_mummy", 1, listOf(melee("crush", 15688, 16))), summon = true),
            Monster(14472, 15559, "Zemouregal Summon", Stats(35, 250, 150, 10, 50, 1), 4, StyleType.CRUSH, 15691, 15692, 15693, Custom("breach_summon_shade", 1, listOf(melee("crush", 15691, 16))), summon = true),
            Monster(14473, 15560, "Zemouregal Summon", Stats(35, 200, 150, 100, 1, 1), 4, StyleType.SLASH, 15694, 15695, 15696, Custom("breach_summon_undead", 1, listOf(melee("slash", 15694, 16))), summon = true),
            Monster(14474, 15561, "Zemouregal Summon", Stats(35, 75, 100, 100, 1, 1), 5, StyleType.CRUSH, 15698, 15697, 15699, Custom("breach_summon_pirate", 1, listOf(melee("crush", 15698, 11))), summon = true),
            Monster(14475, 15562, "Zemouregal Summon", Stats(35, 250, 150, 50, 1, 1), 4, StyleType.SLASH, 15705, OSRS_HUMAN_BLOCK, OSRS_HUMAN_DEATH, Custom("breach_summon_fremennik", 1, listOf(melee("slash", 15705, 16))), summon = true),
            // OSRS Wiki "TzTok-Jad-Rek" (Deadman: Annihilation): "a smaller and weaker TzTok-Jad, summoned by the breach version of
            // TzTok-Jad" - 200 hp, 160/240/25/250/250, attack speed 8, stab/magic/ranged, max hit 25 melee and "?" otherwise.
            // TzTok-Jad's own imported sequences (same model at 70 % scale). ADAPTED: magic/ranged max 34 = 25 x the breach Jad's
            // 108/78 magic-to-melee ratio, since the wiki gives none.
            Monster(
                14477, 15557, "TzTok-Jad-Rek",
                Stats(200, 160, 240, 25, 250, 250),
                8, StyleType.STAB, 15675, 15673, 15674,
                Template(
                    2745,
                    anims = mapOf("melee" to 15675, "range" to 15672, "magic" to 15676),
                    maxHits = mapOf("melee" to 25),
                    addHits = mapOf("range" to hit("range", 34), "magic" to hit("magic", 34)),
                    sectionSounds = mapOf("magic" to SectionSounds(cast = listOf(SFX_FIREBREATH))),
                ),
                summon = true,
            ),
            Monster(14476, 15563, "Zemouregal Summon", Stats(35, 250, 150, 100, 1, 1), 4, StyleType.SLASH, 15700, 15680, 15701, Custom("breach_summon_khazard", 1, listOf(melee("slash", 15700, 16))), summon = true),
        )

    val BY_ID: Map<Int, Monster> = ROSTER.associateBy { it.id }

    /**
     * Breach monster -> the rev-667 npc whose combat-sound row (combat-sounds.json) it uses: the regular version it is a copy of
     * (owner 2026-09-19 "no sounds": imported npcs had no row). Monsters without a rev-667 counterpart (Cerberus, the Wilderness
     * bosses, Jal-ImKot, the Varlamore/PvP-legend npcs) only have the sounds of their attack sections.
     */
    val SOUND_ALIASES: Map<Int, Int> =
        mapOf(
            14438 to 2883, 14439 to 50, 14440 to 2881, 14441 to 2882, 14442 to 6260, 14443 to 6247, 14444 to 6203, 14445 to 2026,
            14449 to 1615, 14450 to 745, 14451 to 1633, 14452 to 4355, 14454 to 1643, 14455 to 1637, 14457 to 2783, 14462 to 2745,
            14467 to 3375, 14471 to 1961, 14472 to 1241, 14474 to 2837, 14477 to 2745,
        )

    /**
     * Demons and their demonbane vulnerability in percent (OSRS Wiki Deadman pages: "Porazdir ... only 1% vulnerable to demonbane
     * weapons", "Greater abyssal demon ... only 1%", "Flaming pyrelord ... 1%", "Cerberus ... 100%").
     */
    val DEMONBANE_VULNERABILITY: Map<Int, Int> = mapOf(14446 to 1, 14449 to 1, 14451 to 1, 14458 to 100)

    /**
     * Status-effect matrix (owner 2026-09-23 breach audit): OSRS Wiki infobox "Immunities" of each "(Deadman)" page (or the
     * monster's own page where it has no Deadman page), "Poison"/"Venom" rows, 100% resistance = immune. Read 2026-09-23.
     * Splatter (14468) shows "?" for both on the wiki: SOURCE_GAP, left susceptible.
     */
    val POISON_IMMUNE: Set<Int> =
        setOf(
            14442, 14443, 14444, 14446, 14447, 14448, 14449, 14451, 14452, 14453, 14454, 14455, 14457, 14458, 14460, 14461,
            14469, 14470, 14476,
        )

    /** Venom immune: every poison-immune monster plus Rex, Supreme, Prime, TzTok-Jad, Big Evil Chicken and TzTok-Jad-Rek. */
    val VENOM_IMMUNE: Set<Int> = POISON_IMMUNE + setOf(14438, 14440, 14441, 14462, 14467, 14477)

    /** Monsters that run after their target (OSRS Wiki: Durial321 14463, I DSCIM YOU 14469). */
    val RUNNERS: Set<Int> = setOf(14463, 14469)

    /**
     * Breach conformance 2026-09-27 (A2): the only breach monsters that route around obstacles. OSRS Wiki "Safespot": NPCs "always
     * move directly toward their target and do not detour around obstacles", "it is not possible to safespot NPCs with
     * routefinding"; "Zemouregal (Deadman)": "He utilises routefinding, meaning he cannot be safespotted as he will path around
     * obstacles to reach his target"; "Durial321 (Deadman)": "capable of pursuing targets, unlike most NPCs". Every other breach
     * monster uses the ordinary npc (dumb) pathing and can be safespotted like in OSRS - this undoes the earlier B3 rule that
     * gave all of them smart routes.
     *
     * Owner decision 2026-09-28 (live test, Derwen safespotted behind a tree): EVERY breach monster now routes around obstacles
     * (DeadmanBreach.spawnMonster, ADAPTED). This set stays as the OSRS reference only.
     */
    val ROUTEFINDERS: Set<Int> = setOf(14463, 14466, 14471, 14472, 14473, 14474, 14475, 14476)

    /**
     * OSRS Wiki "Zemouregal Summon": "They are aggressive towards all nearby players and can be very dangerous to unaware players, as
     * they can be lured even into safezones. They utilise routefinding, meaning they cannot be safespotted" - the summons are
     * [ROUTEFINDERS] and, unlike every other breach monster, follow their target into a Safe zone.
     */
    /**
     * Owner 2026-09-27 ("all DMM monsters must have the Deadman: Annihilation stats"): the combat level shown to players is the
     * Annihilation infobox "combat" of each monster (data/cfg/deadman/wiki-infobox, OSRS id -> level); the imported cache levels
     * were older versions (e.g. Dagannoth Rex 479, Annihilation 1104). Sent as the npc's combat-level override when it spawns.
     * Guarded by BreachWikiInfoboxTests.
     */
    val COMBAT_LEVEL_BY_OSRS: Map<Int, Int> =
        mapOf(
            13658 to 1420, 15547 to 1047, 12454 to 957, 13657 to 726, 12445 to 596, 12442 to 947, 12439 to 1104, 12441 to 936,
            12450 to 655, 12447 to 666, 13662 to 709, 12453 to 1026, 12444 to 624, 12452 to 1022, 12451 to 719, 15553 to 495,
            13660 to 893, 12455 to 240, 12449 to 768, 12446 to 650, 12440 to 318, 13663 to 967, 12456 to 661, 12459 to 457,
            15556 to 716, 12448 to 655, 13664 to 973, 15550 to 648, 12458 to 757, 13659 to 563, 15557 to 186, 13661 to 1758,
            12457 to 908, 15237 to 984,
        )

    val FOLLOW_INTO_SAFE_ZONES: Set<Int> = setOf(14471, 14472, 14473, 14474, 14475, 14476)

    /**
     * Breach conformance 2026-09-27: osrs-docs "Entity Collision" - "Excluded NPCs (Dagannoth Kings, Godwars generals, pets, etc.)"
     * neither set nor check the npc/player collision flags: they walk through players and other npcs. The breach Dagannoth Kings
     * and God Wars generals are the regular monsters' Deadman copies ("attacks the same way as its regular version"), so they keep
     * the exclusion.
     */
    val COLLISION_EXCLUDED: Set<Int> = setOf(14438, 14440, 14441, 14442, 14443, 14444)

    /**
     * 100 % freeze resistance (OSRS Wiki infobox "freezeresistance = 100", Deadman: Annihilation, read 2026-09-27 into
     * data/cfg/deadman/wiki-infobox): Bee Swarm, Durial321, Magic Mark, Pestilent Bloat. Guarded by BreachWikiInfoboxTests.
     */
    val FREEZE_IMMUNE: Set<Int> = setOf(14459, 14463, 14464, 14470)

    /**
     * Where the Annihilation infobox's elemental weakness differs from the regular monster of the same name (the shared by-name
     * table): Jal-ImKot "Water 30", Thermonuclear smoke devil none (the regular one is Air 20). Guarded by BreachWikiInfoboxTests.
     */
    val WEAKNESS_OVERRIDES: Map<Int, gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Weakness?> =
        mapOf(
            14453 to gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Weakness(gg.rsmod.plugins.content.combat.formula.ElementalWeakness.Element.WATER, 30),
            14460 to null,
        )

    /** The monsters a breach spawns (everything but Zemouregal's summons). */
    val SPAWNABLE: List<Monster> = ROSTER.filter { !it.summon }

    /** Zemouregal's six summons (TzTok-Jad-Rek is summoned by the breach TzTok-Jad instead). */
    val SUMMONS: List<Monster> = ROSTER.filter { it.summon && it.name == "Zemouregal Summon" }

    const val TZTOK_JAD = 14462
    const val TZTOK_JAD_REK = 14477

    fun isBreachMonster(npcId: Int): Boolean = npcId in BY_ID

    /** Builds every monster's attack row; template rows are read from [voidFile] so load order against npc_attacks does not matter. */
    fun attackRows(voidFile: File = File(NpcAttacks.DEFAULT_PATH)): List<NpcAttacks.Row> {
        val gson = Gson()
        val templates: Map<Int, JsonObject> by lazy {
            FileReader(voidFile).use { gson.fromJson(it, JsonArray::class.java) }
                .map { it.asJsonObject }
                .associateBy { it["id"].asInt }
        }
        return ROSTER.map { m ->
            when (val a = m.attacks) {
                is Custom -> NpcAttacks.Row(id = m.id, name = m.name, combatDef = a.combatDef, attackRange = a.range, attacks = a.sections)
                is Template -> {
                    val row = templates[a.rowId]?.deepCopy() ?: error("npc-attacks.json has no template row ${a.rowId} for ${m.name}")
                    row.addProperty("id", m.id)
                    row.addProperty("name", m.name)
                    row["attacks"].asJsonArray.forEach { el ->
                        val section = el.asJsonObject
                        val sectionId = section["id"].asString
                        // A1: a God Wars chamber area means "every player in the room"; a breach has no room (see ROOM_RADIUS).
                        if (section.has("multi_target_area") && !section["multi_target_area"].isJsonNull) {
                            section.remove("multi_target_area")
                            section.addProperty("multi_radius", ROOM_RADIUS)
                        }
                        a.anims[sectionId]?.let { section.addProperty("anim", it) }
                        a.maxHits[sectionId]?.let { max -> section["hits"].asJsonArray.forEach { h -> h.asJsonObject.addProperty("max", max * 10) } }
                        a.sectionInts[sectionId]?.forEach { (field, value) -> section.addProperty(field, value) }
                        a.sectionSounds[sectionId]?.let { s ->
                            fun set(field: String, ids: List<Int>?) {
                                if (ids != null) section.add(field, gson.toJsonTree(ids.map { NpcAttacks.Sound(id = it) }))
                            }
                            set("sounds", s.cast)
                            set("target_sounds", s.target)
                            set("impact_sounds", s.impact)
                        }
                        a.sectionVisuals[sectionId]?.let { v ->
                            fun empty(field: String) = !section.has(field) || section[field].isJsonNull || (section[field].isJsonArray && section[field].asJsonArray.size() == 0)
                            if (v.projectile != null && empty("projectiles")) section.add("projectiles", gson.toJsonTree(listOf(v.projectile)))
                            if (v.impactGfx != null && empty("impact_gfx")) section.add("impact_gfx", gson.toJsonTree(listOf(v.impactGfx)))
                        }
                        a.addHits[sectionId]?.let { extra ->
                            if (section["hits"].asJsonArray.size() == 0) section.add("hits", gson.toJsonTree(listOf(extra)))
                        }
                    }
                    gson.fromJson(row, NpcAttacks.Row::class.java)
                }
            }
        }
    }
}
