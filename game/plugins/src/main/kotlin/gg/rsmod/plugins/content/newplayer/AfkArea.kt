package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims

/**
 * The AFK skill basement under the Grand Exchange hall (owner 2026-09-26). Its map square and stations were built by
 * AfkBasementMapTool (m46_95 / l46_95, tx-20260926-160841); the coordinates and loc ids here are that tool's, pinned by
 * AfkAreaTests.
 *
 * One click on a station starts training its skill: every game tick the player gets [NewPlayerConfig.afkXpPerTick] x
 * [NewPlayerConfig.afkRate] experience - only experience, never items - until they move, do something else or log out.
 * Training stops exactly on the XP of [NewPlayerConfig.afkLevelCap] (55); a skill already there cannot train here.
 * Owner decisions: only non-combat skills and no Slayer or Dungeoneering (question b); the whole room is a safe zone
 * (GuardedZones) and the server has no idle logout at all, so nothing logs an AFK player out (question c).
 */
object AfkArea {
    const val REGION_ID = (46 shl 8) or 95
    const val MIN_X = 46 * 64 + 16
    const val MAX_X = 46 * 64 + 47
    const val MIN_Z = 95 * 64 + 20
    const val MAX_Z = 95 * 64 + 43

    /** The basement's spiral staircase bottom (62779, 2 x 2) and where players arrive when they come down. */
    val BASEMENT_STAIRS = Tile(46 * 64 + 31, 95 * 64 + 21, 0)
    val BASEMENT_ARRIVAL = Tile(46 * 64 + 31, 95 * 64 + 23, 0)

    /** The hall's way down: the marble spiral staircase top (62780, 2 x 2) in the south-east corner, from home_decor.txt. */
    const val HALL_STAIRS_DOWN = 62780
    const val BASEMENT_STAIRS_UP = 62779
    val HALL_STAIRS = Tile(3170, 3486, 0)
    val HALL_ARRIVAL = Tile(3169, 3488, 0)

    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in MIN_X..MAX_X && tile.z in MIN_Z..MAX_Z

    /** A station: the loc copy the tool placed (option 1 "Train"), its skill and the animation shown while training. */
    class Station(val loc: Int, val skill: Int, val animation: Int)

    /** AfkBasementMapTool.STATIONS order, copies 62851.. (FIRST_STATION_LOC). Animations are the cache-named skill anims. */
    val STATIONS: List<Station> =
        listOf(
            Station(62851, Skills.COOKING, Anims.COOK_RANGE),
            Station(62852, Skills.FIREMAKING, Anims.LIGHT_FIRE),
            Station(62853, Skills.FISHING, Anims.FISH_SMALL_FISHING_NET),
            Station(62854, Skills.HERBLORE, Anims.MIX_POTION),
            Station(62855, Skills.WOODCUTTING, Anims.CHOP_BRONZE_HATCHET),
            Station(62856, Skills.FLETCHING, Anims.FLETCH_SHORTBOW),
            Station(62857, Skills.CRAFTING, Anims.SPIN_SPINNING_WHEEL),
            Station(62858, Skills.SMITHING, Anims.SMITH_ANVIL),
            Station(62859, Skills.MINING, Anims.MINE_BRONZE_PICKAXE),
            Station(62860, Skills.CONSTRUCTION, Anims.HAMMER_FIX_SHIELD),
            Station(62861, Skills.RUNECRAFTING, Anims.CRAFT_RUNES),
            Station(62862, Skills.THIEVING, Anims.PICKPOCKET),
            Station(62863, Skills.AGILITY, Anims.AGIL_JUMP_INTO_PIPE),
            Station(62864, Skills.FARMING, Anims.RAKE_PATCH),
            Station(62865, Skills.HUNTER, -1),
        )

    /** Every non-combat skill except Slayer and Dungeoneering (owner, question b): exactly one station each. */
    val TRAINABLE: Set<Int> =
        (Skills.ATTACK..Skills.DUNGEONEERING).toSet() -
            setOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC, Skills.PRAYER, Skills.CONSTITUTION, Skills.SUMMONING) -
            setOf(Skills.SLAYER, Skills.DUNGEONEERING)

    fun station(loc: Int): Station? = STATIONS.firstOrNull { it.loc == loc }

    fun capXp(config: NewPlayerConfig = NewPlayerConfig.current): Double = SkillSet.getXpForLevel(config.afkLevelCap)

    fun atCap(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Boolean =
        player.skills.getMaxLevel(skill) >= config.afkLevelCap || player.skills.getCurrentXp(skill) >= capXp(config)

    /** The XP one tick gives [skill] now: the configured amount, cut so the skill ends exactly on the cap. */
    fun tickXp(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Double {
        val left = capXp(config) - player.skills.getCurrentXp(skill)
        if (left <= 0.0 || player.skills.getMaxLevel(skill) >= config.afkLevelCap) return 0.0
        return minOf(config.afkXpPerTick * config.afkRate, left)
    }

    /** One training tick: adds [tickXp] (raw, no level curve or bonus) and says whether training may go on. */
    fun train(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Boolean {
        val xp = tickXp(player, skill, config)
        if (xp <= 0.0) return false
        player.addXp(skill, xp, modifiers = false, disableBonusExperience = true)
        return !atCap(player, skill, config)
    }
}
