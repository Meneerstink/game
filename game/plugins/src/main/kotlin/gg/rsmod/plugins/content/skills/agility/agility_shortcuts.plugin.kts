package gg.rsmod.plugins.content.skills.agility

/**
 * @author Harley <github.com/HarleyGilpin>
 * Created: 1/20/24
 * Modified: 2/10/24
 *
 * Plugin Description:
 * This plugin features agility shortcuts that enable players to quickly overcome obstacles using their agility skill,
 * thereby facilitating swifter travel throughout the game world.
 */

/**
 * Location: Edgeville Dungeon Monkeybars
 * Agility Requirement: 15
 */

/**
 * Darkan Edgeville.java:75-79 (handleEdgevilleMonkeybars) -> Agility.crossMonkeybars (Agility.java:120-130): level 15; route onto the
 * bars' own tile (obj.tile), face the end obj.tile + (0, y > 9967 ? -5 : 5), next tick anim 742 + the monkey-bar BAS, then a 5-tile
 * collision-free swing (BAS walk) and the stop anim 743, 20 xp. Replaces the five 25/60 forced moves from obj.x + 1 with a blind
 * wait. Darkan's BAS 2405 is absent from the 667 cache: BAS 662 (ready 745 / walk 744) is this cache's monkey-bar set.
 * Note: 29375 has no placement in the 667 map dump (region 12443 has no loc data in tools/conformance/traversal/placements_667.tsv).
 */
on_obj_option(obj = Objs.MONKEY_BARS_29375, option = 1) {
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 15) {
        player.message("You need an agility level of 15 to use this obstacle.")
        return@on_obj_option
    }
    obstacle { target ->
        val end = target.tile.transform(0, if (player.tile.z > 9967) -5 else 5)
        darkanCrossMonkeyBars(player, Tile(target.tile), end, 20.0)
    }
}

/**
 * Stiles: sourced_shortcuts.plugin.kts (Void Stiles.kt, shared climbOver).
 */

/**
 * Location: Falador Agility Shortcut.
 * Agility Requirement: 5
 * Experience: 0.5
 * Notes: Right next to the Falador west bank. Provides easy access to the areas south of Taverley.
 */

/**
 * Location: (Grapple) Over the River Lum to Al Kharid
 * Agility Requirement: 8
 * Experience: N/A
 * Notes: Players must have 19 Strength and 37 Ranged.
 */

/**
 * Location: (Grapple) Scale Falador Wall
 * Agility Requirement: 11
 * Experience: N/A
 * Notes: Players must have level 37 Strength and 19 Ranged.
 */

/**
 * Location: Varrock south fence jump.
 * Agility Requirement: 13
 * Experience: 0
 * Notes: Provides a shortcut to and from South Varrock.
 */

/**
 * Location: Yanille Agility Shortcut.
 * Agility Requirement: 16
 * Experience: 0
 * Notes: Near the Watchtower. Gives easy access to the west parts of Yanille from the teleport point.
 */

/**
 * Location: Coal Truck log balance.
 * Agility Requirement: 20
 * Experience: 8.5
 * Notes: Near the Coal Trucks west of Seers' Village. Provides easier access between the Mining spot and the pickup location
 *        near Seers' Village.
 */
// Ported to sourced_shortcuts.plugin.kts (coalTruckLog, Void LogBalance): the old handler walked collision-free from the click tile.

/**
 * Location: Grand Exchange underwall tunnel shortcut.
 * Agility Requirement: 21
 * Experience: 0
 * Notes: Grants easier access to Edgeville. Also means access to Grand Exchange is far easier from the nearby fairy ring
 *        and canoe. (It is known as 'Varrock agility shortcut' in the game guide.)
 */

/**
 * Location: Falador Agility Shortcut.
 * Agility Requirement: 26
 * Experience: 0
 * Notes: South-west corner of Falador. Provides easier travel between the Falador west bank and Rimmington, as well as
 *        the Crafting Guild.
 */

/**
 * Location: Draynor Manor log balance to Champions' Guild.
 * Agility Requirement: 31
 * Experience: 3 or 1 (if failed)
 * Notes: Near Draynor Manor. Allows you to cross the River Lum between the Manor and Varrock. There's a chance of taking
 *        a few points of damage when using this shortcut.
 */

/**
 * Location: (Grapple) Scale Catherby Cliff
 * Agility Requirement: 32
 * Experience: N/A
 * Notes: Players must have level 35 Strength and 35 Ranged and have completed the Fishing Contest quest to use this shortcut.
 */

/**
 * Location: Ardougne log balance shortcut.
 * Agility Requirement: 33
 * Experience: 4 or 2 (if failed)
 * Notes: Allows easy passage across the river. There's a chance of taking a few points of damage when using this shortcut.
 */

/**
 * Location: (Grapple) Escape from the Water Obelisk Island
 * Agility Requirement: 36
 * Experience: 15
 * Notes: Players must have level 22 Strength and 39 Ranged.
 */

/**
 * Location: Gnome Stronghold shortcut.
 * Agility Requirement: 37
 * Experience: 0
 * Notes: The Grand Tree and Tree Gnome Village quests needed. North-east of the Grand Tree. Allows easy access to Barbarian
 *        Outpost and the Lighthouse.
 */

/**
 * Location: Al Kharid mining pit cliff side scramble
 * Agility Requirement: 38
 * Experience: 0
 * Notes: This shortcut is at the north end of the mine, and is useful for exiting to go north instead of going all the
 *        way around from the southern end.
 */

/**
 * Location: (Grapple) Scale Yanille Wall
 * Agility Requirement: 39
 * Experience: N/A
 * Notes: Players must have level 38 Strength and 21 Ranged.
 */

/**
 * Location: Trollheim, easy cliffside scramble.
 * Agility Requirement: 41
 * Experience: 0
 * Notes: Allows easier travel up and down the lower path of the west side of the hill near the Troll Stronghold.
 */

/**
 * Location: Dwarven mine, narrow crevice.
 * Agility Requirement: 42
 * Experience: 0
 * Notes: Provides a shortcut between the two arms of the mine.
 */

/**
 * Location: Trollheim, medium cliffside scramble.
 * Agility Requirement: 43
 * Experience: 0
 * Notes: Allows easier travel up and down the higher paths of the west side of the hill near the Troll Stronghold.
 */

/**
 * Location: Trollheim, advanced cliffside scramble.
 * Agility Requirement: 44
 * Experience: 0
 * Notes: Allows easier travel up and down the east side of the hill near the Troll Stronghold.
 *        This is useful to avoid the Troll Throwers, on the north side of the hill.
 */

/**
 * Location: Cosmic Temple, medium narrow walkway.
 * Agility Requirement: 46
 * Experience: 10 or 6 (on failure)
 * Notes: Provides faster travel between the bank and the Cosmic Altar at Zanaris. If you fail, you take a small amount
 *        of damage, but you still pass through.
 */

/**
 * Location: Trollheim, hard cliffside scramble.
 * Agility Requirement: 47
 * Experience: 8
 * Notes: Allows easier travel up and down the higher paths of the west side of the hill near the Troll Stronghold.
 */

/**
 * Location: Log balance to Fremennik Province.
 * Agility Requirement: 48
 * Experience: 0
 * Notes: North of Camelot. Provides slightly faster travel to Eastern Fremennik Province. There's a chance of taking a
 *        few points of damage when using this shortcut.
 */

/**
 * Location: Pipe from Edgeville dungeon to Varrock sewers
 * Agility Requirement: 51
 * Experience: 10
 * Notes: Allows passage between the eastern part of the Edgeville Dungeon and the Moss giants in the Varrock Sewers.
 *        There's a chance of taking a few points of damage when using this shortcut.
 */
on_obj_option(obj = Objs.PIPE_29370, option = 1) {
    // Level corrected 2026-09-18: 2009scape's PipeShortcut sources obj 29370 (Edgeville Dungeon) at level 51, matching the OSRS
    // Wiki value (Darkan: 53); xp 10 as the header above.
    if (player.skills.getCurrentLevel(Skills.AGILITY) < 51) {
        player.message("You need an agility level of 51 to use this obstacle.")
        return@on_obj_option
    }
    // Darkan Edgeville.java:207-216 (handleEdgevilleDungeonPipe, pipes at 3150,9906 r2 / 3153,9906 r0): entering = player.x < 3153;
    // Agility.walkToAgility(player, BAS 295, entering ? EAST : WEST, 6, 6, xp) - from the route tile a 6-tile collision-free crawl
    // with the pipe BAS 295 (ready 2338 / walk 844), no forced move. Replaces Void's two 10580 slides + mid-pipe teleport.
    obstacle {
        val entering = player.tile.x < 3153
        player.filterableMessage("You squeeze into the pipe...")
        darkanWalkAgility(player, 295, if (entering) Direction.EAST else Direction.WEST, 6)
        player.filterableMessage("You pulled yourself through the pipes.")
        player.addXp(Skills.AGILITY, 10.0)
    }
}

/** Void climb_through_pipe. */
val ANIM_CLIMB_THROUGH_PIPE_10580 = 10580

/**
 * Location: (Grapple) Karamja Volcano
 * Agility Requirement: 53
 * Experience: N/A
 * Notes: Players must have level 21 Strength and 42 Ranged. Provides easier travel between the Karamja Volcano area and
 *        lower Karamja.
 */

/**
 * Location: Port Phasmatys, Ectopool shortcut.
 * Agility Requirement: 58
 * Experience: 0
 * Notes: Provides faster travel down to the pool of slime.
 */

/**
 * Location: Elven Overpass (Arandar), easy cliffside scramble.
 * Agility Requirement: 59
 * Experience: 0
 * Notes: Helps when using the elven shortcut to Tirannwn.
 */

/**
 * Location: Slayer tower, medium spiked chain climb.
 * Agility Requirement: 61
 * Experience: 0
 * Notes: Shortcut between Crawling Hand area and Aberrant spectre area on the 2nd floor. Nose peg or Slayer helmet
 *        required due to Aberrant spectres. There's a chance of taking a few points of damage when using this shortcut.
 */

/**
 * Location: Fremennik Slayer Dungeon, narrow crevice.
 * Agility Requirement: 62
 * Experience: 0
 * Notes: Shortcut between Basilisk area and Turoth area.
 */

/**
 * Location: Trollheim Wilderness Route.
 * Agility Requirement: 64
 * Experience: 0
 * Notes: Shortcut from Trollheim to level 20 Wilderness. One way.
 */

/**
 * Location: Temple on the Salve to Morytania shortcut.
 * Agility Requirement: 65
 * Experience: 0
 * Notes: Slightly faster way to get from east Varrock to Canifis, bypassing the underground route at the Temple.
 */

/**
 * Location: Cosmic Temple, advanced narrow walkway.
 * Agility Requirement: 66
 * Experience: 10
 * Notes: Much faster travel between the bank and the Cosmic Altar at Zanaris. If you fail, you take a small amount of
 *        damage, but you still pass through.
 */

/**
 * Location: Elven Overpass (Arandar, medium cliffside scramble.
 * Agility Requirement: 68
 * Experience: 0
 * Notes: Helps when using the elven shortcut to Tirannwn.
 */

/**
 * Location: Taverley Dungeon pipe squeeze to blue dragon lair.
 * Agility Requirement: 70
 * Experience: 10
 * Notes: Quick passage between the entrance and the Blue dragon area. Bypasses need for the Dusty key. There's a chance
 *        of taking a few points of damage when using this shortcut.
 */

/**
 * Location: (Grapple) Cross cave, south of Dorgesh-Kaan.
 * Agility Requirement: 70
 * Experience: N/A
 * Notes: No additional notes
 */

/**
 * Location: Slayer Tower, advanced spiked chain climb.
 * Agility Requirement: 71
 * Experience: 0
 * Notes: Shortcut between Infernal Mages area on the 2nd floor and Nechryael area on the 3rd floor. There's a chance of
 *        taking a few points of damage when using this shortcut.
 */

/**
 * Location: Shilo Village, stepping stones over the river.
 * Agility Requirement: 74
 * Experience: 0
 * Notes: Shortcut allows you to cross the river in Shilo Village, making it useful for fly-fishing. Requires completion
 *        of Medium Karamja Diary.
 */

/**
 * Location: Taverley Dungeon spiked blades jump.
 * Agility Requirement: 80
 * Experience: 12.5
 * Notes: Shortcut between the entrance and the Poison spider area. Bypasses need for the Dusty key.
 */

/**
 * Location: Fremennik Slayer Dungeon chasm jump.
 * Agility Requirement: 81
 * Experience: 5 x 2
 * Notes: No additional notes
 */

/**
 * Location: Elven Overpass (Arandar), advanced cliffside scramble.
 * Agility Requirement: 85
 * Experience: 0
 * Notes: Helps when using the elven shortcut to Tirannwn.
 */

/**
 * Location: Kuradal's Dungeon wall climb.
 * Agility Requirement: 86
 * Experience: 0
 * Notes: No additional notes
 */

/**
 * Location: Kuradal's Dungeon wall run.
 * Agility Requirement: 90
 * Experience: 0
 * Notes: No additional notes
 */
