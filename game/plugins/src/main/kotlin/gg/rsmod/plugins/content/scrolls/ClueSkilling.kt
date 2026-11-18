package gg.rsmod.plugins.content.scrolls

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.areas.wilderness.npcs.WildernessNpcDrops
import gg.rsmod.plugins.content.skills.fishing.Fish
import gg.rsmod.plugins.content.skills.mining.RockType
import gg.rsmod.plugins.content.skills.woodcutting.TreeType
import java.io.File
import kotlin.math.floor
import kotlin.random.Random

/** Pinned Wiki Clue nest/geode/bottle formula; independent rolls per eligible tier. */
object ClueSkilling {
    private val multipliers = linkedMapOf(ClueScrollTier.EASY to 1.7, ClueScrollTier.MEDIUM to 2.0,
        ClueScrollTier.HARD to 3.3, ClueScrollTier.ELITE to 10.0)
    private val rates by lazy {
        JsonParser().parse(File(RspsPaths.data("cfg/clue/skilling.json")).readText()).asJsonObject.entrySet().associate { (skill, value) ->
            skill to value.asJsonObject["activities"].asJsonObject.entrySet().associate { (activity, base) -> activity to base.asInt }
        }
    }
    private val reportedMissing = mutableSetOf<Int>()

    fun base(skill: String, activity: String): Int? = rates[skill]?.get(activity)

    fun denominator(base: Int, visibleLevel: Int, tier: ClueScrollTier, combatAchievement: Boolean = false): Int {
        require(base > 0 && visibleLevel >= 0 && tier in multipliers)
        val adjusted = if (combatAchievement) base.toLong() * 95 / 100 else base.toLong()
        return floor(adjusted.toDouble() / (100.0 + visibleLevel) * multipliers.getValue(tier)).toLong()
            .coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
    }

    fun rolls(base: Int, level: Int, eligible: (ClueScrollTier) -> Boolean, random: Random = Random.Default,
              wealth: Boolean = false, combatAchievement: (ClueScrollTier) -> Boolean = { false }): List<ClueScrollTier> =
        multipliers.keys.filter { tier -> eligible(tier) && random.nextDouble() <
            (if (wealth) 2.0 else 1.0) / denominator(base, level, tier, combatAchievement(tier)) }

    fun woodcutting(player: Player, tree: TreeType) = award(player, "woodcutting", tree.name, Skills.WOODCUTTING, nest = true)
    fun mining(player: Player, rock: RockType) = award(player, "mining", rock.name, Skills.MINING)
    fun fishing(player: Player, fish: Fish) = award(player, "fishing", fish.name, Skills.FISHING)

    private fun award(player: Player, skill: String, activity: String, skillId: Int, nest: Boolean = false) {
        val base = base(skill, activity) ?: return // RS2-only activities have no invented OSRS rate.
        val wealth = player.tile.getWildernessLevel() > 0 && WildernessNpcDrops.clueDenominator(player, 128) == 64
        // No Combat Achievements provider exists in this server, so none of its rewards are unlocked.
        // Invisible familiar boosts, 200m pet modifiers and the woodcutting cape do not enter this formula.
        val selected = rolls(base, player.skills.getCurrentLevel(skillId), { tier ->
            ClueContainers.count(player, tier) < ClueContainers.capacity(player, tier)
        }, wealth = wealth)
        for (tier in selected) {
            val box = ClueContainers.rewardId(player, tier)
            if (box == null) {
                if (reportedMissing.add(tier.osrsScrollId)) println("PROBLEM CLUE ${tier.name} skilling scroll box not imported")
                continue
            }
            if (player.world.definitions.getNullable(ItemDef::class.java, box) == null) {
                if (reportedMissing.add(box)) println("PROBLEM CLUE skilling scroll box definition $box missing")
                continue
            }
            val added = player.inventory.add(box, 1, assureFullInsertion = true)
            if (!added.hasSucceeded()) player.world.spawn(ClueGroundItems.configure(GroundItem(Item(box), player.tile, player)))
            if (nest) {
                player.world.spawn(GroundItem(Item(Items.BIRDS_NEST_5075), player.tile, player))
                player.playSound(gg.rsmod.plugins.content.skills.woodcutting.BIRD_NEST_DROP_SYNTH)
            }
        }
    }
}
