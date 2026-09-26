package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.tools.importer.AfkBasementMapTool
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.ext.message

/**
 * The Skilling Basement "Guild Cellar" under the Grand Exchange hall (owner 2026-09-26). Its map square, stations and
 * the hall's way down were built by [AfkBasementMapTool] (tx-20260926-195800); the ids and tiles here are that tool's,
 * pinned by SkillingBasementTests.
 *
 * One click on a station starts training its skill: every game tick the player gets [NewPlayerConfig.afkXpPerTick] x
 * [NewPlayerConfig.afkRate] experience - only experience, never items - until they move, do something else, log out or
 * reach [NewPlayerConfig.afkLevelCap] (55): training stops exactly on that level's XP (166,636), never above it, and a
 * skill already there cannot train here. A level-up inside the basement is only a chat line ([quietLevelUp]).
 * Owner decisions: exactly these 15 skills, no combat, Slayer or Dungeoneering; the whole room is a safe zone
 * (GuardedZones); the client skips its idle logout here, so players may stay AFK as long as they like.
 */
object AfkArea {
    const val REGION_ID = AfkBasementMapTool.REGION_ID
    const val MIN_X = AfkBasementMapTool.BASE_X + 20
    const val MAX_X = AfkBasementMapTool.BASE_X + 43
    const val MIN_Z = AfkBasementMapTool.BASE_Z + 22
    const val MAX_Z = AfkBasementMapTool.BASE_Z + 39

    /** The basement's stairs up (2 x 3) and the carpet tile players arrive on when they come down. */
    const val BASEMENT_STAIRS_UP = AfkBasementMapTool.STAIRS_UP_LOC
    val BASEMENT_STAIRS = Tile(AfkBasementMapTool.BASE_X + AfkBasementMapTool.STAIRS_LX, AfkBasementMapTool.BASE_Z + AfkBasementMapTool.STAIRS_LZ, 0)
    val BASEMENT_ARRIVAL = Tile(BASEMENT_STAIRS.x, BASEMENT_STAIRS.z + 3, 0)

    /** The hall's way down: the stone cellar stairs with the dungeon icon, where the marble staircase stood. */
    const val HALL_STAIRS_DOWN = AfkBasementMapTool.ENTRANCE_LOC
    val HALL_STAIRS = Tile(AfkBasementMapTool.ENTRANCE_X, AfkBasementMapTool.ENTRANCE_Z, 0)
    val HALL_ARRIVAL = Tile(3162, 3487, 0)

    /** The pond's fishing spot npc and the Survival Expert beside the stairs. */
    const val FISHING_SPOT = AfkBasementMapTool.FISHING_SPOT_NPC
    val FISHING_SPOT_TILE = Tile(AfkBasementMapTool.BASE_X + AfkBasementMapTool.FISHING_LX, AfkBasementMapTool.BASE_Z + AfkBasementMapTool.FISHING_LZ, 0)
    const val SURVIVAL_EXPERT = 943
    val SURVIVAL_EXPERT_TILE = Tile(AfkBasementMapTool.BASE_X + AfkBasementMapTool.EXPERT_LX, AfkBasementMapTool.BASE_Z + AfkBasementMapTool.EXPERT_LZ, 0)

    fun contains(tile: Tile): Boolean = tile.height == 0 && tile.x in MIN_X..MAX_X && tile.z in MIN_Z..MAX_Z

    /**
     * A station: the loc copy the tool placed (or the fishing spot npc), its skill, its menu option and the animation
     * played while training, repeated every [animationTicks] ticks so it keeps running.
     */
    class Station(val loc: Int, val skill: Int, val option: String, val animation: Int, val animationTicks: Int = 4, val npc: Boolean = false)

    /** Box trap laying (Novite `Hunter.java`: BOX 19187 new Animation(5208)). */
    const val LAY_BOX_TRAP = 5208

    /** The log-balance walk (render animation 155's walk sequence, BasTypeProbeTool bas 155: walk=762), played on the spot. */
    const val BALANCE_WALK = 762

    private fun loc(index: Int) = AfkBasementMapTool.FIRST_STATION_LOC + index

    /** [AfkBasementMapTool.STATIONS] order (loc copies 62870..), then the fishing spot's two options. */
    val STATIONS: List<Station> =
        listOf(
            Station(loc(0), Skills.WOODCUTTING, "Chop down", Anims.CHOP_BRONZE_HATCHET),
            Station(loc(1), Skills.MINING, "Mine", Anims.MINE_BRONZE_PICKAXE),
            Station(loc(2), Skills.MINING, "Mine", Anims.MINE_BRONZE_PICKAXE),
            Station(loc(3), Skills.FARMING, "Rake", Anims.RAKE_PATCH),
            Station(loc(4), Skills.HUNTER, "Check", LAY_BOX_TRAP),
            Station(loc(5), Skills.COOKING, "Cook", Anims.COOK_RANGE),
            Station(loc(6), Skills.FIREMAKING, "Tend", Anims.LIGHT_FIRE),
            Station(loc(7), Skills.SMITHING, "Smelt", Anims.SMELT_FURNACE),
            Station(loc(8), Skills.SMITHING, "Smith", Anims.SMITH_ANVIL),
            Station(loc(9), Skills.CRAFTING, "Spin", Anims.SPIN_SPINNING_WHEEL),
            // the server's own potter's wheel forming (FormPotteryAction) plays COOK_RANGE
            Station(loc(10), Skills.CRAFTING, "Form", Anims.COOK_RANGE),
            Station(loc(11), Skills.FLETCHING, "Fletch", Anims.FLETCH_SHORTBOW),
            Station(loc(12), Skills.HERBLORE, "Mix", Anims.MIX_POTION),
            Station(loc(13), Skills.CONSTRUCTION, "Build", Anims.HAMMER_FIX_SHIELD),
            Station(loc(14), Skills.RUNECRAFTING, "Craft-rune", Anims.CRAFT_RUNES),
            Station(loc(15), Skills.AGILITY, "Balance", BALANCE_WALK, animationTicks = 1),
            Station(loc(16), Skills.THIEVING, "Steal-from", Anims.REACH_OUT_GRAB),
            Station(loc(17), Skills.THIEVING, "Steal-from", Anims.REACH_OUT_GRAB),
            Station(loc(18), Skills.THIEVING, "Steal-from", Anims.REACH_OUT_GRAB),
            Station(FISHING_SPOT, Skills.FISHING, "Net", Anims.FISH_SMALL_FISHING_NET, npc = true),
            Station(FISHING_SPOT, Skills.FISHING, "Bait", Anims.FISH_FISHING_ROD, npc = true),
        )

    /** Exactly the 15 skills of the owner's brief. */
    val TRAINABLE: Set<Int> =
        setOf(
            Skills.WOODCUTTING, Skills.FISHING, Skills.MINING, Skills.FARMING, Skills.HUNTER, Skills.COOKING, Skills.FIREMAKING,
            Skills.SMITHING, Skills.CRAFTING, Skills.FLETCHING, Skills.HERBLORE, Skills.CONSTRUCTION, Skills.RUNECRAFTING,
            Skills.AGILITY, Skills.THIEVING,
        )

    fun station(loc: Int): Station? = STATIONS.firstOrNull { !it.npc && it.loc == loc }

    fun capXp(config: NewPlayerConfig = NewPlayerConfig.current): Double = SkillSet.getXpForLevel(config.afkLevelCap)

    fun atCap(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Boolean =
        player.skills.getMaxLevel(skill) >= config.afkLevelCap || player.skills.getCurrentXp(skill) >= capXp(config)

    /** The XP one tick gives [skill] now: the configured amount, cut so the skill ends exactly on the cap. */
    fun tickXp(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Double {
        val left = capXp(config) - player.skills.getCurrentXp(skill)
        if (left <= 0.0 || player.skills.getMaxLevel(skill) >= config.afkLevelCap) return 0.0
        return minOf(config.afkXpPerTick * config.afkRate, left)
    }

    /**
     * One training tick: adds [tickXp] raw (modifiers off, so no level curve, bonus XP or xp.normal_rate multiplier can
     * push it past the cap) and says whether training may go on.
     */
    fun train(player: Player, skill: Int, config: NewPlayerConfig = NewPlayerConfig.current): Boolean {
        val xp = tickXp(player, skill, config)
        if (xp <= 0.0) return false
        player.addXp(skill, xp, modifiers = false, disableBonusExperience = true)
        return !atCap(player, skill, config)
    }

    // ------------------------------------------------------------------------------------------ sessions

    /**
     * The running training session's number. Every start takes a new one, and a training loop stops as soon as the
     * number is no longer its own - so a player trains on one station at a time and never gets two XP ticks at once.
     */
    val SESSION = AttributeKey<Int>()

    /** Per tick, whether a session may give XP: same session, still online, still in the basement, not logging out. */
    fun mayTrain(player: Player, session: Int): Boolean =
        player.attr[SESSION] == session && player.isOnline && !player.isLogoutPending && contains(player.tile)

    /** Starts a new session and returns its number (the previous one, if any, ends on its next tick). */
    fun newSession(player: Player): Int {
        val next = (player.attr[SESSION] ?: 0) + 1
        player.attr[SESSION] = next
        return next
    }

    fun endSession(player: Player) {
        player.attr[SESSION] = (player.attr[SESSION] ?: 0) + 1
    }

    // ------------------------------------------------------------------------------------------ level-up and the expert

    /** The chat line a basement level-up shows instead of the level-up window, jingle and fireworks. */
    fun levelUpMessage(skillName: String, level: Int): String = "Your $skillName level is now $level."

    /**
     * Owner 2026-09-26: a level-up inside the basement is only a chat message - no window, jingle, fireworks or
     * flashing icon - and training carries on. Returns true when it handled the level-up (the player is in the basement).
     */
    fun quietLevelUp(player: Player, skill: Int, skillName: String): Boolean {
        if (!contains(player.tile)) return false
        player.message(levelUpMessage(skillName, player.skills.getMaxLevel(skill)))
        return true
    }

    /** The Survival Expert has welcomed this player (once, the first time they come down). */
    val INTRO_SEEN = AttributeKey<Boolean>(persistenceKey = "skilling_basement_intro_seen")

    /** Bit per skill: the Survival Expert has congratulated this player on reaching the cap in that skill here. */
    val CAPPED_SKILLS = AttributeKey<Int>(persistenceKey = "skilling_basement_capped_skills")

    /** Marks [skill]'s cap talk as given; true only the first time. */
    fun markCapped(player: Player, skill: Int): Boolean {
        val mask = player.attr[CAPPED_SKILLS] ?: 0
        if (mask and (1 shl skill) != 0) return false
        player.attr[CAPPED_SKILLS] = mask or (1 shl skill)
        return true
    }

    /** Marks the welcome as given; true only the first time. */
    fun markIntro(player: Player): Boolean {
        if (player.attr[INTRO_SEEN] == true) return false
        player.attr[INTRO_SEEN] = true
        return true
    }

    fun introLines(cap: Int): List<String> =
        listOf(
            "Welcome to the Skilling Basement, adventurer! Down here you can train fifteen skills without any tools or materials.",
            "Woodcutting, Fishing, Mining, Farming and Hunter are in the garden to the west. Cooking, Firemaking, Smithing, Crafting, Fletching, Herblore, Construction and Runecrafting are in the workshop to the east.",
            "Agility and Thieving are along the north wall. Click a station once and you keep training by yourself - you only earn experience, never items.",
            "Every skill can be trained here up to level $cap. Walk away or do anything else and you stop training.",
            "The whole cellar is a safe zone, and you may stay AFK down here for as long as you like.",
            "A level-up down here is just a chat message, so your training simply carries on.",
        )

    fun capLines(skillName: String, cap: Int): List<String> =
        listOf(
            "Well done! Your $skillName has reached level $cap - the most the basement can teach you.",
            "Down here you can train every skill up to level $cap. From now on, train $skillName out in the world, the normal RuneScape way.",
            "Any of your other skills still below level $cap can be brought up to $cap here.",
        )
}
