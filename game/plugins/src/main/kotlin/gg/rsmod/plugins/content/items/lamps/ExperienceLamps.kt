package gg.rsmod.plugins.content.items.lamps

/**
 * Every experience lamp that opens the skill-choice interface 1139 (owner 2026-09-23: "antique lamps are unhandled").
 *
 * One table instead of one script per lamp. Antique lamp rewards and level requirements: RuneScape Wiki "Antique lamp" list and each
 * lamp's own page (item ids from their infoboxes); the genie [LAMP] gives ten times the chosen skill's level (RS Wiki "Lamp").
 * Messages: Void `content/quest/free/gunnars_ground/AntiqueLamp.kt` ("This skill is not high enough to gain experience from this
 * lamp.", "Your wish has been granted!" / "You have been awarded N <skill> experience!").
 */
object ExperienceLamps {
    const val LAMP = 2528

    sealed class Reward {
        /** A fixed amount in any skill whose (unboosted) level is at least [minLevel]. */
        class Fixed(val xp: Int, val minLevel: Int = 1) : Reward()

        /** [perLevel] times the chosen skill's level (genie lamp). */
        class PerLevel(val perLevel: Int) : Reward()

        /**
         * Jobs lamp (RS Wiki "Antique lamp (Jobs)"): 25 times the level from level 19; below that, the experience still needed for
         * the next level.
         */
        object Jobs : Reward()
    }

    val REWARDS: Map<Int, Reward> =
        mapOf(
            LAMP to Reward.PerLevel(10),
            4447 to Reward.Fixed(10_000, 30), // One Small Favour
            6543 to Reward.Fixed(2_500, 30), // A Tail of Two Cats / A Fairy Tale II
            7498 to Reward.Fixed(20_000, 50), // Recipe for Disaster
            11137 to Reward.Fixed(1_000, 30), // Easy Karamja Tasks
            11139 to Reward.Fixed(5_000, 40), // Medium Karamja Tasks
            11141 to Reward.Fixed(10_000, 50), // Hard Karamja Tasks
            11185 to Reward.Fixed(500, 10), // Varrock Museum
            11186 to Reward.Fixed(1_000, 20), // Kudos
            11187 to Reward.Fixed(1_000, 20), // Kudos
            11188 to Reward.Fixed(1_000, 20), // Kudos
            11189 to Reward.Fixed(10_000, 50), // Historian Minas
            11679 to Reward.Fixed(5_000, 50), // King's Ransom
            11753 to Reward.Fixed(1_000, 30), // Easy Varrock Tasks
            11754 to Reward.Fixed(5_000, 40), // Medium Varrock Tasks
            11755 to Reward.Fixed(10_000, 50), // Hard Varrock Tasks
            12627 to Reward.Fixed(500, 1), // Misthalin Training Centre of Excellence
            12628 to Reward.Fixed(500, 1), // Misthalin Training Centre of Excellence
            13439 to Reward.Fixed(250), // Learning the Ropes
            13446 to Reward.Fixed(500, 1), // Beginner Lumbridge Tasks
            13447 to Reward.Fixed(1_000, 30), // Easy Lumbridge Tasks
            13448 to Reward.Fixed(1_500, 35), // Medium Lumbridge Tasks
            13463 to Reward.Jobs, // Jobs
            14574 to Reward.Fixed(5_000, 30), // Easy Fremennik Tasks
            14575 to Reward.Fixed(10_000, 40), // Medium Fremennik Tasks
            14576 to Reward.Fixed(15_000, 50), // Hard Fremennik Tasks
            14580 to Reward.Fixed(1_000, 30), // Easy Falador Tasks
            14581 to Reward.Fixed(5_000, 40), // Medium Falador Tasks
            14582 to Reward.Fixed(10_000, 50), // Hard Falador Tasks
            14633 to Reward.Fixed(1_000, 30), // Easy Seers' Village Tasks
            14634 to Reward.Fixed(5_000, 40), // Medium Seers' Village Tasks
            14635 to Reward.Fixed(10_000, 50), // Hard Seers' Village Tasks
            14711 to Reward.Fixed(2_500, 35), // Glorious Memories
            14740 to Reward.Fixed(200, 10), // The Tale of the Muspah
            15346 to Reward.Fixed(1_000, 25), // Easy Ardougne Tasks
            15348 to Reward.Fixed(7_500, 45), // Medium Ardougne Tasks
            15350 to Reward.Fixed(28_000, 60), // Hard Ardougne Tasks
            15351 to Reward.Fixed(50_000, 85), // Elite Ardougne Tasks
            18783 to Reward.Fixed(5_000, 50), // Ancient effigies
            19750 to Reward.Fixed(30_000, 90), // Elite Falador Tasks, 30k
            19751 to Reward.Fixed(50_000, 72), // Elite Falador Tasks, 50k level 72
            19752 to Reward.Fixed(50_000, 84), // Elite Falador Tasks, 50k level 84
            19755 to Reward.Fixed(30_000, 64), // Elite Karamja Tasks, 30k
            19756 to Reward.Fixed(55_000, 87), // Elite Karamja Tasks, 55k
            19758 to Reward.Fixed(30_000, 65), // Elite Varrock Tasks, 30k
            19759 to Reward.Fixed(40_000, 88), // Elite Varrock Tasks, 40k
            19761 to Reward.Fixed(9_000, 49), // Hard Lumbridge Tasks
            19764 to Reward.Fixed(25_000, 70), // Elite Seers' Village Tasks, 25k
            19765 to Reward.Fixed(30_000, 83), // Elite Seers' Village Tasks, 30k
            19767 to Reward.Fixed(30_000, 86), // Elite Fremennik Tasks, 30k
            19768 to Reward.Fixed(40_000, 89), // Elite Fremennik Tasks, 40k
            19775 to Reward.Fixed(200, 5), // Gunnar's Ground
        )

    const val NOT_HIGH_ENOUGH = "This skill is not high enough to gain experience from this lamp."

    /** Experience [reward] grants in a skill at [level] with [xp] experience, or null when the level is too low. */
    fun experience(
        reward: Reward,
        level: Int,
        xp: Double,
    ): Int? =
        when (reward) {
            is Reward.Fixed -> if (level < reward.minLevel) null else reward.xp
            is Reward.PerLevel -> level * reward.perLevel
            Reward.Jobs ->
                if (level >= 19) {
                    level * 25
                } else {
                    (gg.rsmod.game.model.skill.SkillSet.getXpForLevel(level + 1) - xp).toInt().coerceAtLeast(0)
                }
        }
}

/** Which lamp the open interface 1139 belongs to. */
object LampInterfaceState {
    val RUBBED_LAMP = gg.rsmod.game.model.attr.AttributeKey<Int>()
}
