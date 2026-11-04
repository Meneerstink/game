package gg.rsmod.plugins.content.skills.slayer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.home.BountyHunterHome
import gg.rsmod.plugins.content.quests.QuestStatus

/** Wiki Krystilia oldid=15389119. No OSRS NPC id is reused as a local 667 id. */
object Krystilia {
    const val OSRS_NPC = 7663
    val TILE = Tile(3109, 3516)
    val TASK = AttributeKey<Boolean>(persistenceKey = "wilderness_slayer_task")
    val STREAK = AttributeKey<Int>(persistenceKey = "wilderness_slayer_streak")
    val WARNED = AttributeKey<Boolean>(persistenceKey = "wilderness_slayer_warned")
    val MORE_SLAYER = AttributeKey<Boolean>(persistenceKey = "slayer_i_wildy_more_slayer")
    val AVIANSIES = AttributeKey<Boolean>(persistenceKey = "slayer_watch_the_birdie")
    val BOSSES = AttributeKey<Boolean>(persistenceKey = "slayer_like_a_boss")
    val EXTENDED = AttributeKey<MutableList<String>>(persistenceKey = "slayer_extended_tasks")

    fun npcId(world: World): Int? = world.definitions.getAll(NpcDef::class.java).values.filterIsInstance<NpcDef>()
        .singleOrNull { it.name.equals("Krystilia", true) }?.id

    data class Task(
        val assignment: SlayerAssignment, val amount: IntRange, val weight: Int, val names: Set<String>,
        val slayer: Int = 1, val thieving: Int = 1, val quest: String? = null,
        val godWars: Boolean = false, val unlock: AttributeKey<Boolean>? = null, val extended: IntRange? = null,
    ) {
        fun available(player: Player): Boolean = player.skills.getMaxLevel(Skills.SLAYER) >= slayer &&
            player.skills.getMaxLevel(Skills.THIEVING) >= thieving && (quest == null || QuestStatus.isCompleted(player, quest)) &&
            (!godWars || maxOf(player.skills.getMaxLevel(Skills.AGILITY), player.skills.getMaxLevel(Skills.STRENGTH)) >= 60) &&
            (unlock == null || player.attr[unlock] == true)
    }

    private fun task(a: SlayerAssignment, amount: IntRange, weight: Int, vararg names: String,
        slayer: Int = 1, thieving: Int = 1, quest: String? = null, godWars: Boolean = false,
        unlock: AttributeKey<Boolean>? = null, extended: IntRange? = null) =
        Task(a, amount, weight, names.map { it.lowercase() }.toSet(), slayer, thieving, quest, godWars, unlock, extended)

    val TASKS = listOf(
        task(SlayerAssignment.ABYSSAL_DEMON, 75..125, 5, "Abyssal demon", slayer=85, quest="Priest in Peril", unlock=MORE_SLAYER, extended=200..250),
        task(SlayerAssignment.ANKOU, 75..125, 6, "Ankou", extended=91..150),
        task(SlayerAssignment.AVIANSIE, 75..125, 7, "Aviansie", "Armadylean guard", godWars=true, unlock=AVIANSIES, extended=200..250),
        task(SlayerAssignment.BANDIT, 75..125, 4, "Bandit"),
        task(SlayerAssignment.BEAR, 65..100, 6, "Grizzly bear", "Bear", "Black bear", "Callisto", "Artio"),
        task(SlayerAssignment.BLACK_DEMON, 100..150, 7, "Black demon", extended=200..250),
        task(SlayerAssignment.BLACK_DRAGON, 8..16, 4, "Black dragon", "Baby black dragon", quest="Dragon Slayer", extended=40..60),
        task(SlayerAssignment.BLACK_KNIGHT, 75..125, 3, "Black Knight"),
        task(SlayerAssignment.BLOODVELD, 70..110, 4, "Bloodveld", slayer=50, quest="Priest in Peril", godWars=true, extended=200..250),
        task(SlayerAssignment.CHAOS_DRUID, 50..90, 5, "Chaos druid", "Elder Chaos druid"),
        task(SlayerAssignment.DARK_WARRIOR, 75..125, 4, "Dark warrior"),
        task(SlayerAssignment.DUST_DEVIL, 75..125, 5, "Dust devil", slayer=65, quest="Desert Treasure", unlock=MORE_SLAYER, extended=200..250),
        task(SlayerAssignment.EARTH_WARRIOR, 75..125, 6, "Earth warrior"),
        task(SlayerAssignment.ENT, 35..60, 5, "Ent"),
        task(SlayerAssignment.FIRE_GIANT, 75..125, 7, "Fire giant"),
        task(SlayerAssignment.GREATER_DEMON, 100..150, 8, "Greater demon", extended=200..250),
        task(SlayerAssignment.GREEN_DRAGON, 65..100, 4, "Green dragon", "Baby green dragon", quest="Dragon Slayer"),
        task(SlayerAssignment.HELLHOUNDS, 75..125, 7, "Hellhound", "Skeleton Hellhound", "Greater Skeleton Hellhound"),
        task(SlayerAssignment.HILL_GIANT, 75..125, 3, "Hill Giant"),
        task(SlayerAssignment.ICE_GIANT, 100..150, 6, "Ice giant"),
        task(SlayerAssignment.ICE_WARRIOR, 100..150, 7, "Ice warrior"),
        task(SlayerAssignment.JELLY, 100..150, 5, "Jelly", slayer=52, unlock=MORE_SLAYER),
        task(SlayerAssignment.LAVA_DRAGON, 35..60, 3, "Lava dragon", quest="Dragon Slayer"),
        task(SlayerAssignment.LESSER_DEMON, 80..120, 6, "Lesser demon"),
        task(SlayerAssignment.MAGIC_AXE, 75..125, 7, "Magic axe", thieving=23),
        task(SlayerAssignment.MAMMOTH, 75..125, 6, "Mammoth"),
        task(SlayerAssignment.MOSS_GIANT, 100..150, 4, "Moss giant"),
        task(SlayerAssignment.NECHRYAEL, 75..125, 5, "Nechryael", slayer=80, quest="Priest in Peril", unlock=MORE_SLAYER, extended=200..250),
        task(SlayerAssignment.PIRATE, 62..75, 3, "Pirate", "Zombie pirate", thieving=39),
        task(SlayerAssignment.REVENANT, 40..100, 5, "Revenant imp", "Revenant goblin", "Revenant pyrefiend", "Revenant hobgoblin", "Revenant cyclops", "Revenant hellhound", "Revenant demon", "Revenant ork", "Revenant dark beast", "Revenant knight", "Revenant dragon", extended=100..150),
        task(SlayerAssignment.ROGUE, 75..125, 5, "Rogue"),
        task(SlayerAssignment.SCORPION, 65..100, 6, "Scorpion", "King Scorpion", "Scorpia", "Scorpia's guardian", "Scorpia's offspring"),
        task(SlayerAssignment.SKELETON, 65..100, 5, "Skeleton", "Vet'ion", "Vet'ion Reborn", "Calvar'ion", "Calvar'ion Reborn", "Skeleton Hellhound", "Greater Skeleton Hellhound"),
        task(SlayerAssignment.SPIDER, 65..100, 6, "Spider", "Giant spider", "Deadly red spider", "Poison spider", "Ice spider", "Venenatis", "Spindel"),
        task(SlayerAssignment.SPIRITUAL_CREATURE, 100..150, 6, "Spiritual ranger", "Spiritual warrior", "Spiritual mage", slayer=63, quest="Death Plateau", godWars=true, extended=181..250),
        task(SlayerAssignment.ZOMBIE, 75..125, 3, "Zombie", "Zombie pirate"),
    )
    // Boss category has weight 8 in total, not 8 per boss.
    val BOSS_TASKS = listOf(
        task(SlayerAssignment.CALLISTO, 3..35, 8, "Callisto", "Artio", unlock=BOSSES),
        task(SlayerAssignment.CHAOS_ELEMENTAL, 3..35, 8, "Chaos Elemental", unlock=BOSSES),
        task(SlayerAssignment.CHAOS_FANATIC, 3..35, 8, "Chaos Fanatic", unlock=BOSSES),
        task(SlayerAssignment.CRAZY_ARCHAEOLOGIST, 3..35, 8, "Crazy archaeologist", unlock=BOSSES),
        task(SlayerAssignment.SCORPIA, 3..35, 8, "Scorpia", unlock=BOSSES),
        task(SlayerAssignment.VENENATIS, 3..35, 8, "Venenatis", "Spindel", unlock=BOSSES),
        task(SlayerAssignment.VETION, 3..35, 8, "Vet'ion", "Vet'ion Reborn", "Calvar'ion", "Calvar'ion Reborn", unlock=BOSSES),
    )

    fun active(player: Player): Boolean = player.attr[TASK] == true && (player.attr[SLAYER_AMOUNT] ?: 0) > 0
    fun current(player: Player): Task? = (TASKS + BOSS_TASKS).firstOrNull { it.assignment.identifier == player.attr[SLAYER_ASSIGNMENT] }
    fun wilderness(player: Player, tile: Tile): Boolean = tile.getWildernessLevel() > 0 &&
        !BountyHunterHome.isSafe(tile, player.world.gameContext.home)

    fun matches(player: Player, npc: Npc): Boolean = active(player) && wilderness(player, player.tile) &&
        wilderness(player, npc.tile) && current(player)?.names?.contains(npc.def.name.lowercase()) == true

    fun points(streak: Int): Int = when {
        streak < 5 -> 0
        streak % 1000 == 0 -> 1250
        streak % 250 == 0 -> 875
        streak % 100 == 0 -> 625
        streak % 50 == 0 -> 375
        streak % 10 == 0 -> 125
        else -> 25
    }

    /** Completion is consumed once, on the same task slot normal masters use. */
    fun complete(player: Player) {
        val streak = (player.attr[STREAK] ?: 0) + 1
        player.attr[STREAK] = streak
        val points = points(streak)
        player.addSlayerPoints(points)
        player.message("You have completed $streak tasks in a row and receive $points slayer points!")
        player.message("You've completed your slayer task; return to a Slayer master.")
        player.playJingle(61)
        player.attr.remove(SLAYER_ASSIGNMENT, SLAYER_AMOUNT, TASK)
    }

    fun assign(player: Player, task: Task, amount: Int, npcId: Int) {
        require(amount in (if (task.assignment.identifier in player.attr[EXTENDED].orEmpty()) task.extended ?: task.amount else task.amount))
        player.attr[SLAYER_ASSIGNMENT] = task.assignment.identifier
        player.attr[SLAYER_AMOUNT] = amount
        player.attr[SLAYER_MASTER] = npcId
        player.attr[SLAYER_PREVIOUS_TASK] = task.assignment.identifier
        player.attr[STARTED_SLAYER] = true
        player.attr[TASK] = true
    }

    fun eligible(player: Player): List<Task> = TASKS.filter {
        it.available(player) && it.assignment.identifier !in player.attr[BLOCKED_TASKS].orEmpty()
    }

    fun eligibleBosses(player: Player): List<Task> = BOSS_TASKS.filter {
        it.available(player) && it.assignment.identifier !in player.attr[BLOCKED_TASKS].orEmpty()
    }

    fun choose(player: Player): Task? {
        val normal = eligible(player)
        val bosses = eligibleBosses(player)
        val total = normal.sumOf { it.weight } + if (bosses.isNotEmpty()) 8 else 0
        if (total == 0) return null
        var roll = player.world.random(total - 1)
        for (task in normal) {
            if (roll < task.weight) return task
            roll -= task.weight
        }
        return bosses[player.world.random(bosses.size - 1)]
    }
}
