package gg.rsmod.plugins.content.npcs.bankers

/*
 * Owner 2026-09-23 "doe alle banks": every bank had its booths/chests but 41 of its bankers were never spawned, so the booths stood
 * unmanned (Ardougne north and south, Yanille, Canifis, Shilo, Keldagrim, Lunar Isle, Sophanem, Dorgesh-Kaan, Oo'glog, Tutorial
 * Island, Mos Le'Harmless, the Fishing and Legends' Guilds, Zanaris, the Void outpost squires). Positions and facings: the Void
 * (634) npc spawn tables, the same npc ids in this 667 cache (NpcDefProbeTool: all "Banker"/"Sirsal Banker"/"Ogress banker"/
 * "Squire"). Every id's Bank option is already bound in bankers.plugin.kts / bankers_missing.plugin.kts.
 * Where Void gives no facing the banker faces the booth side (ADAPTED, noted per line).
 * Left out on purpose: the Grand Exchange (home, owner-designed), Draynor (already staffed), the Draynor bank-robbery guard and the
 * Fist of Guthix banker (parked minigame).
 */

class BankStaff(val id: Int, val x: Int, val z: Int, val height: Int = 0, val facing: Direction)

val STAFF =
    listOf(
        // Keldagrim
        BankStaff(2163, 2836, 10205, facing = Direction.NORTH),
        BankStaff(2164, 2838, 10205, facing = Direction.NORTH),
        // Lunar Isle (ADAPTED facing: the booths are south of him)
        BankStaff(4519, 2097, 3921, facing = Direction.SOUTH),
        // Ardougne north (2616: ADAPTED facing, as its neighbours)
        BankStaff(494, 2614, 3330, facing = Direction.NORTH),
        BankStaff(495, 2616, 3330, facing = Direction.NORTH),
        BankStaff(495, 2618, 3330, facing = Direction.NORTH),
        BankStaff(495, 2619, 3330, facing = Direction.NORTH),
        // Ardougne south
        BankStaff(494, 2657, 3286, facing = Direction.WEST),
        BankStaff(494, 2657, 3283, facing = Direction.WEST),
        BankStaff(495, 2657, 3280, facing = Direction.WEST),
        // Oo'glog (ADAPTED facing: the booths are east of these 2x2 ogresses)
        BankStaff(7049, 2553, 2839, facing = Direction.EAST),
        BankStaff(7050, 2553, 2837, facing = Direction.EAST),
        // Fishing Guild
        BankStaff(494, 2583, 3422, facing = Direction.EAST),
        BankStaff(495, 2583, 3423, facing = Direction.EAST),
        // Legends' Guild, top floor (ADAPTED facing)
        BankStaff(494, 2732, 3381, height = 2, facing = Direction.SOUTH),
        BankStaff(494, 2731, 3377, height = 2, facing = Direction.SOUTH),
        BankStaff(495, 2732, 3379, height = 2, facing = Direction.SOUTH),
        BankStaff(495, 2733, 3377, height = 2, facing = Direction.SOUTH),
        // Yanille
        BankStaff(494, 2615, 3094, facing = Direction.WEST),
        BankStaff(494, 2615, 3092, facing = Direction.WEST),
        BankStaff(495, 2615, 3091, facing = Direction.WEST),
        // Shilo Village (ADAPTED facing)
        BankStaff(499, 2851, 2954, facing = Direction.SOUTH),
        BankStaff(499, 2853, 2955, facing = Direction.SOUTH),
        // Sophanem (underground)
        BankStaff(5258, 2798, 5171, facing = Direction.SOUTH),
        BankStaff(5258, 2801, 5171, facing = Direction.SOUTH),
        BankStaff(5260, 2799, 5171, facing = Direction.SOUTH),
        BankStaff(5260, 2800, 5171, facing = Direction.SOUTH),
        // Dorgesh-Kaan
        BankStaff(5776, 2699, 5349, facing = Direction.EAST),
        BankStaff(5777, 2699, 5348, facing = Direction.EAST),
        // Burgh de Rott (owner 2026-09-24 "burg de roth bank volledig werkend maken"): Cornelius runs this bank - its booths
        // 12800/12801 carry no option in the cache - and was never spawned. Void spawns npc 3567 (varbit-transformed to Cornelius
        // 3569 once the bank is open) at 3493,3211 on the staff side of the booths at x 3494 (the bank's chest stands there too),
        // in the gap between them; he faces the customers to the east (ADAPTED facing).
        BankStaff(3569, 3493, 3211, facing = Direction.EAST),
        // Tutorial Island
        BankStaff(953, 3120, 3125, facing = Direction.SOUTH),
        BankStaff(953, 3122, 3125, facing = Direction.SOUTH),
        // Zanaris (ADAPTED facing: the booth is east of him)
        BankStaff(909, 2379, 4459, facing = Direction.EAST),
        // Canifis
        BankStaff(1036, 3514, 3481, facing = Direction.WEST),
        BankStaff(1036, 3514, 3479, facing = Direction.WEST),
        // Mos Le'Harmless
        BankStaff(3198, 3682, 2983, facing = Direction.WEST),
        BankStaff(3198, 3682, 2981, facing = Direction.WEST),
        BankStaff(3199, 3682, 2982, facing = Direction.WEST),
        // Void Knight outpost bank squires (ADAPTED facing: towards the booths north of them)
        BankStaff(3794, 2666, 2650, facing = Direction.NORTH),
        BankStaff(3795, 2668, 2650, facing = Direction.NORTH),
        // Oo'glog cave bank (ADAPTED facing: the booths are east)
        BankStaff(7049, 2607, 5516, facing = Direction.EAST),
        BankStaff(7050, 2607, 5514, facing = Direction.EAST),
    )

STAFF.forEach { spawn_npc(npc = it.id, x = it.x, z = it.z, height = it.height, walkRadius = 0, direction = it.facing, static = true) }
