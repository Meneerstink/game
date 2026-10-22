package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.Area
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * Pest Control data (owner 2026-10-02: "finish pest control fully").
 *
 * Sources, in order: this project's rev-667 cache (npc/object/interface probes 2026-10-02, quoted where used), the
 * 2009scape Pest Control port (`import-source/donors/2009scape/.../minigame/pestcontrol`, rev 530) and Void
 * (`Donors/void/data/minigame/pest_control`, rev 634). The 2009scape logic is what is ported; every id it uses was
 * re-checked against the 667 cache because the revisions differ.
 *
 * Portal ids (runNpcDefProbeTool 2026-10-02): 6142-6145 and 6150-6153 are "Portal" size 3 with "Attack"; 6146-6149 and
 * 6154-6157 are the same portals without any option (the shielded form, base id + 4). 7551-7558 are NOT portals in
 * this cache (General Khazard, Citizen, Tool leprechaun) - the 2009scape veteran ids came from rev 530. The cache has two
 * portal sets, so the intermediate and the veteran lander both use 6150 (the 2009scape portal health: 200 novice, 250 above).
 */
enum class PestControlTier(
    val displayName: String,
    val combatReq: Int,
    val portalBaseId: Int,
    /**
     * Portal hitpoints: 2009scape / OSRS 200 novice, 250 above. Owner 2026-10-10 "portals have 2000hp thats to much": npc HP is 1:1
     * since the HP unit migration (NpcCombatDsl divides its x10 input), so the old 667 x10 values gave portals ten times their health.
     */
    val portalLifepoints: Int,
    /**
     * Commendation points per won game: six times OSRS's novice 3 / intermediate 4 / veteran 5 (owner 2026-10-10 "6x points": full void
     * in about 29 veteran games instead of 170; the item prices stay OSRS's).
     */
    val pointsPerWin: Int,
    /** Index into the monster tables below (2009scape PCPortalNPC.getDifficultyIndex). */
    val difficulty: Int,
    /** Where the player stands after leaving the lander or the game (2009scape getLeaveLocation). */
    val leaveTile: Tile,
    /**
     * The boat deck: 2009scape PCLanderZone.configure borders. Its novice box reads (2659, 2637)-(2664, 2664), which would take in
     * twenty rows of the outpost north of the boat; the deck is capped at z 2644 like the other two boats (8 rows).
     */
    val lander: Area,
    /** Tile on the boat after crossing the gangplank (2009scape PCObjectHandler.startActivity). */
    val boardTile: Tile,
    val gangplankId: Int,
    val exitLadderId: Int,
) {
    NOVICE("Novice", 40, Npcs.PORTAL, 200, 18, 0, Tile(2657, 2639, 0), Area(2659, 2637, 2664, 2644), Tile(2661, 2639, 0), 14315, 14314),
    INTERMEDIATE("Intermediate", 70, Npcs.PORTAL_6150, 250, 24, 1, Tile(2644, 2644, 0), Area(2637, 2641, 2642, 2648), Tile(2640, 2644, 0), 25631, 25629),
    VETERAN("Veteran", 100, Npcs.PORTAL_6150, 250, 30, 2, Tile(2638, 2653, 0), Area(2631, 2648, 2636, 2655), Tile(2634, 2653, 0), 25632, 25630),
    ;

    val shieldedPortalBaseId: Int get() = portalBaseId + 4
}

/** Region 10536 (the island) is copied into every game's instance; local coordinates below are from its south-west corner. */
const val PC_REGION_ID = 10536
val PC_REGION_BASE = Tile(2624, 2560, 0)

/** Portal local offsets, in 2009scape spawn order (0 = purple/west, 1 = blue/east, 2 = yellow/south-east, 3 = red/south-west). */
val PC_PORTAL_OFFSETS = arrayOf(4 to 31, 56 to 28, 45 to 10, 21 to 9)
val PC_PORTAL_NAMES = arrayOf("purple, western", "blue, eastern", "yellow, south-eastern", "red, south-western")

val PC_SQUIRE_OFFSET = 32 to 32

/** The Void Knight being defended: 2009scape picks 3782 or 3785 (667 cache: both "Void Knight", no options). */
val PC_KNIGHT_IDS = intArrayOf(Npcs.VOID_KNIGHT, Npcs.VOID_KNIGHT_3785)

/** Void Knight hitpoints: the 2009scape overlay starts at "200" (1:1 since the HP unit migration, was x 10). */
const val PC_KNIGHT_LIFEPOINTS = 200

/** 2009scape: a destroyed portal gives the Void Knight 50 hitpoints back. */
const val PC_KNIGHT_PORTAL_HEAL = 50

/** The squire by the landing (667 cache 3781 "Squire": Talk-to, Leave) at local (30, 47). */
const val PC_LEAVE_SQUIRE = Npcs.SQUIRE_3781
val PC_LEAVE_SQUIRE_OFFSET = 30 to 47

/** Arrival / respawn: local (32..35, 49..54) - 2009scape addPlayer and death(). */
const val PC_SPAWN_X = 32
const val PC_SPAWN_Z = 49

/** Monster ids per difficulty index (2009scape PCPortalNPC tables, ids verified "Splatter"/"Shifter"/... in Npcs.kt). */
val PC_SPLATTERS = arrayOf(intArrayOf(3727, 3728, 3729), intArrayOf(3728, 3729, 3730), intArrayOf(3729, 3730, 3731))
val PC_SHIFTERS =
    arrayOf(intArrayOf(3732, 3733, 3734, 3735, 3736, 3737), intArrayOf(3734, 3735, 3736, 3737, 3738, 3739), intArrayOf(3736, 3737, 3738, 3739, 3740, 3741))
val PC_RAVAGERS = arrayOf(intArrayOf(3742, 3743, 3744), intArrayOf(3743, 3744, 3745), intArrayOf(3744, 3745, 3746))
val PC_SPINNERS = arrayOf(intArrayOf(3747, 3748, 3749), intArrayOf(3748, 3749, 3750), intArrayOf(3749, 3750, 3751))
val PC_TORCHERS =
    arrayOf(intArrayOf(3752, 3753, 3754, 3755, 3756, 3757), intArrayOf(3754, 3755, 3756, 3757, 3758, 3759), intArrayOf(3756, 3757, 3758, 3759, 3760, 3761))
val PC_DEFILERS =
    arrayOf(intArrayOf(3762, 3763, 3764, 3765, 3766, 3767), intArrayOf(3764, 3765, 3766, 3767, 3768, 3769), intArrayOf(3766, 3767, 3768, 3769, 3770, 3771))
val PC_BRAWLERS = arrayOf(intArrayOf(3772, 3773, 3774), intArrayOf(3773, 3774, 3775), intArrayOf(3774, 3775, 3776))

enum class PestMonster { SPLATTER, SHIFTER, RAVAGER, SPINNER, TORCHER, DEFILER, BRAWLER }

fun pestMonsterOf(id: Int): PestMonster? =
    when (id) {
        in 3727..3731 -> PestMonster.SPLATTER
        in 3732..3741 -> PestMonster.SHIFTER
        in 3742..3746 -> PestMonster.RAVAGER
        in 3747..3751 -> PestMonster.SPINNER
        in 3752..3761 -> PestMonster.TORCHER
        in 3762..3771 -> PestMonster.DEFILER
        in 3772..3776 -> PestMonster.BRAWLER
        else -> null
    }

/**
 * Real durations, ported verbatim from 2009scape PestControlActivityPlugin/PestControlSession. Minimum team: owner 2026-10-22 (N204,
 * "elke boot mag starten met 1 speler") - was 2 (owner 2026-10-04); RS/Darkan Landers need 5, which no solo-ish server ever fills.
 */
const val PC_MIN_TEAM_SIZE = 1

/** N204 (owner 2026-10-22): the red chat line on the lander 30 seconds before departure. */
const val PC_START_SOON_MESSAGE = "<col=ff0000>Pest Control will start in 30 seconds.</col>"
const val PC_MAX_TEAM_SIZE = 25

/** Darkan PestControl.createPestNPC: the Void Knight is spawn index 4 - a shifter every 15 ticks, at most 4 alive. */
const val KNIGHT_SPAWN_INDEX = 4
const val KNIGHT_SPAWN_TICKS = 15
const val KNIGHT_MAX_SHIFTERS = 4
const val PC_LOBBY_DEPARTURE_TICKS = 500
const val PC_GAME_DURATION_TICKS = 2000
/**
 * OSRS Wiki "Pest Control": "The first shield will always drop 15 seconds into the game, and the remaining shields deactivate every
 * 30 seconds thereafter. All shields will be down by 1 minute and 45 seconds" (owner: Pest Control rules = OSRS). Was 2009scape's
 * 50/100/150/200.
 */
val PC_SHIELD_DROP_TICKS = intArrayOf(25, 75, 125, 175)

/** 2009scape: a won game pays only players with at least 50 zeal (damage dealt, in hitpoints, plus 5 per repair). */
const val PC_MIN_ZEAL = 50
const val PC_REPAIR_ZEAL = 5

/** Interfaces (667 cache layout probe 2026-10-02 matches 2009scape's component use): lander, game overlay, rewards. */
const val PC_LANDER_INTERFACE = 407
const val PC_GAME_INTERFACE = 408
const val PC_REWARD_INTERFACE = 267

/** Void `pest_control.jingles.toml`. */
const val PC_JINGLE_DEFEAT = 144
const val PC_JINGLE_WIN = 145

/** The island's music: data/cfg/music/music_by_region.yaml "pest_control" = track index 401 (region 10536); the instance has no
 *  region of its own, so the track is played on arrival. */
const val PC_MUSIC_INDEX = 401

/**
 * Barricades: intact 14224-14226, damaged 14227-14229, broken 14230-14232 (+3 per step; island placement probe 2026-10-02:
 * 14224/14225/14226 stand at the 2009scape OBJECT_OFFSETS, 14230-14232 lie broken at (13, 11..14) and (52, 11..14)).
 * Gates 14233-14248 (+4 per step). Only the damaged and broken ids carry the cache's "Repair" option.
 */
val PC_BARRICADE_IDS = 14224..14232
val PC_REPAIRABLE_BARRICADE_IDS = 14227..14232
val PC_GATE_IDS = 14233..14248

/** Ids that cannot be damaged further (2009scape INVALID_OBJECT_IDS): the most damaged barricades and gates. */
val PC_DESTROYED_IDS = setOf(14230, 14231, 14232, 14245, 14246, 14247, 14248)

/** Tower ladder inside the camp (2009scape handleTurretTower). */
const val PC_TOWER_LADDER = 14296

const val PC_HAMMER = 2347
const val PC_LOGS = 1511
const val PC_REPAIR_ANIM = 898

/** Monster visuals (2009scape PC*NPC). */
const val PC_SPINNER_HEAL_ANIM = 3911
const val PC_SPINNER_HEAL_GFX = 658
const val PC_SPLATTER_EXPLODE_ANIM = 3888
const val PC_SPLATTER_EXPLODE_GFX_BASE = 649
const val PC_SHIFTER_TELEPORT_GFX = 654
const val PC_SHIFTER_TELEPORT_ANIM = 3904

/** Pest points balance - persisted under the same key since Q-050, so existing balances are kept. */
val PEST_POINTS = AttributeKey<Int>(persistenceKey = "pest_points")

/**
 * Won Pest Control games (every game whose portals all fell), counted from 2026-10-10. It was the elite void requirement (50 wins
 * instead of the OSRS Hard Western Provinces Diary); owner 2026-10-10 later the same day: "no win requirement" - points only.
 */
val PEST_WINS = AttributeKey<Int>(persistenceKey = "pest_wins")

/** OSRS: the elite top and robe cost 200 commendation points each and replace the normal piece (Tyr and the reward screen). */
const val ELITE_VOID_UPGRADE_POINTS = 200

val PC_MASTER_TICK = TimerKey()
