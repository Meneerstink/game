package gg.rsmod.plugins.content.activity.pest_control

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.setComponentText
import kotlin.random.Random
import gg.rsmod.game.tools.importer.PestControlRewardsInterfaceImportTool as Tool

/**
 * The Void Knights' reward screen (interface 267, "Exchange" on Void Knight 3786/3788/3789), ported from 2009scape
 * PCRewardInterface. The 667 cache layout of 267 (probe 2026-10-02) has exactly the 2009scape buttons: experience 34-62
 * (1 / 10 / 100 points per skill), items 41-48 and 67-70, charms 75-86, Confirm 96; texts 104 (choice) and 105 (points).
 *
 * Experience per point = floor(level^2 / 600) x N (N = 35 melee + Constitution, 32 Ranged/Magic, 18 Prayer), x 1.01 for 10
 * points and x 1.1 for 100 (2009scape calculateExperience). Skill rewards need level 25; Void items need 42 in every combat
 * skill and 22 Prayer.
 */
object PestControlRewards {
    private const val RED = "<col=FF0000>"
    private const val GREEN = "<col=04B404>"
    private const val WHITE = "<col=FFFFFF>"
    private const val UNSELECTED = "<col=784F1C>"

    private val SELECTED = AttributeKey<Pair<Reward, Int>>()

    private val SKILL_POINTS = intArrayOf(1, 10, 100)
    private val CHARM_POINTS = intArrayOf(2, 28, 56)
    private val CHARM_AMOUNTS = intArrayOf(1, 14, 28)

    /** [children] = header text, then the option buttons (2009scape child ids). */
    enum class Reward(
        val title: String,
        val children: IntArray,
        val skill: Int = -1,
        val points: Int = 0,
        val items: IntArray = intArrayOf(),
        val charm: Boolean = false,
        val skillReq: Pair<Int, Int>? = null,
        val void: Boolean = false,
        /** Elite void: swaps one of these pieces from the inventory (as at Commodore Tyr). */
        val upgradeFrom: IntArray = intArrayOf(),
        /** Only on the extended screen (PestControlRewardsInterfaceImportTool, owner 2026-10-10). */
        val extended: Boolean = false,
    ) {
        ATTACK("Attack", intArrayOf(10, 34, 49, 56), skill = Skills.ATTACK),
        STRENGTH("Strength", intArrayOf(11, 35, 50, 57), skill = Skills.STRENGTH),
        DEFENCE("Defence", intArrayOf(12, 36, 51, 58), skill = Skills.DEFENCE),
        RANGED("Ranged", intArrayOf(13, 37, 52, 59), skill = Skills.RANGED),
        MAGIC("Magic", intArrayOf(14, 38, 53, 60), skill = Skills.MAGIC),
        CONSTITUTION("Constitution", intArrayOf(15, 39, 54, 61), skill = Skills.CONSTITUTION),
        PRAYER("Prayer", intArrayOf(16, 40, 55, 62), skill = Skills.PRAYER),

        // Grimy herbs (2009scape Herbs.*.getHerb(): harralander, ranarr, toadflax, irit, avantoe, kwuarm, guam, marrentill).
        HERB_PACK("Herb Pack", intArrayOf(32, 45), points = 30, items = intArrayOf(205, 207, 3049, 209, 211, 213, 199, 201), skillReq = Skills.HERBLORE to 25),
        MINERAL_PACK("Mineral Pack", intArrayOf(47, 46), points = 15, items = intArrayOf(453, 440), skillReq = Skills.MINING to 25),
        SEED_PACK("Seed Pack", intArrayOf(33, 48), points = 15, items = intArrayOf(5320, 5322, 5100), skillReq = Skills.FARMING to 25),
        VOID_MACE("Void Knight Mace", intArrayOf(28, 41), points = 250, items = intArrayOf(8841), void = true),
        VOID_TOP("Void Knight Top", intArrayOf(29, 42), points = 250, items = intArrayOf(8839), void = true),
        VOID_ROBE("Void Knight Robe", intArrayOf(30, 43), points = 250, items = intArrayOf(8840), void = true),
        VOID_GLOVES("Void Knight Gloves", intArrayOf(31, 44), points = 150, items = intArrayOf(8842), void = true),
        VOID_MAGE_HELM("Void Knight Mage Helm", intArrayOf(63, 67), points = 200, items = intArrayOf(11663), void = true),
        VOID_RANGER_HELM("Void Knight Ranger Helm", intArrayOf(64, 68), points = 200, items = intArrayOf(11664), void = true),
        VOID_MELEE_HELM("Void Knight Melee Helm", intArrayOf(65, 69), points = 200, items = intArrayOf(11665), void = true),
        VOID_SEAL("Void Knight Seal", intArrayOf(66, 70), points = 10, items = intArrayOf(11666)),
        ELITE_VOID_TOP(
            "Elite Void Top",
            intArrayOf(Tool.ELITE_TOP_TITLE, Tool.ELITE_TOP_BUTTON),
            points = ELITE_VOID_UPGRADE_POINTS,
            items = intArrayOf(19785),
            void = true,
            upgradeFrom = intArrayOf(8839, 10611),
            extended = true,
        ),
        ELITE_VOID_ROBE(
            "Elite Void Robe",
            intArrayOf(Tool.ELITE_ROBE_TITLE, Tool.ELITE_ROBE_BUTTON),
            points = ELITE_VOID_UPGRADE_POINTS,
            items = intArrayOf(19786),
            void = true,
            upgradeFrom = intArrayOf(8840),
            extended = true,
        ),

        /** RuneScape Wiki "Void Knight deflector": 150 commendation points. */
        VOID_DEFLECTOR("Void Knight Deflector", intArrayOf(Tool.DEFLECTOR_TITLE, Tool.DEFLECTOR_BUTTON), points = 150, items = intArrayOf(19711), void = true, extended = true),
        SPINNER_CHARM("Spinner Charm", intArrayOf(71, 75, 76, 77), items = intArrayOf(12166), charm = true),
        RAVAGER_CHARM("Ravager Charm", intArrayOf(72, 81, 82, 83), items = intArrayOf(12164), charm = true),
        TORCHER_CHARM("Torcher Charm", intArrayOf(74, 78, 79, 80), items = intArrayOf(12167), charm = true),
        SHIFTER_CHARM("Shifter Charm", intArrayOf(73, 84, 85, 86), items = intArrayOf(12165), charm = true),
        ;

        val isSkill: Boolean get() = skill >= 0
        val header: Int get() = children[0]

        /** [option] is 1-based (the index of the clicked button in [children]). */
        fun cost(option: Int): Int =
            when {
                charm -> CHARM_POINTS[option - 1]
                isSkill -> SKILL_POINTS[option - 1]
                else -> points
            }

        fun optionText(option: Int): String =
            if (charm) {
                arrayOf("(2 Pts)", "(28 Pts)", "(56 Pts)")[option - 1]
            } else {
                arrayOf("(1 Pt)", "(10 Pts)", "(100 Pts)")[option - 1]
            }

        companion object {
            fun forButton(button: Int): Pair<Reward, Int>? {
                for (r in values()) {
                    val i = r.children.indexOf(button)
                    if (i > 0) return r to i
                }
                return null
            }
        }
    }

    /** Every clickable button of 267 (for the plugin's on_button binding). */
    val BUTTONS: List<Int> = Reward.values().flatMap { r -> r.children.drop(1) }

    const val CONFIRM = 96

    private fun points(player: Player): Int = player.attr[PEST_POINTS] ?: 0

    /** Whether the cache's 267 has the elite / deflector rows ([Tool], by component count as `GrandExchangeHistory.layout`). */
    fun hasExtendedRows(player: Player): Boolean =
        runCatching { player.world.filestore.index(3).archive(PC_REWARD_INTERFACE)?.fileIds()?.size ?: 0 }.getOrDefault(0) == Tool.COMPONENT_COUNT

    private fun shown(
        player: Player,
        extended: Boolean = hasExtendedRows(player),
    ): List<Reward> = Reward.values().filter { !it.extended || extended }

    private fun text(
        player: Player,
        child: Int,
        text: String,
    ) = player.setComponentText(PC_REWARD_INTERFACE, child, text)

    fun open(player: Player) {
        player.attr.remove(SELECTED)
        player.openInterface(PC_REWARD_INTERFACE, InterfaceDestination.MAIN_SCREEN)
        refresh(player)
    }

    private fun refresh(player: Player) {
        val pts = points(player)
        text(player, 105, "Points: $pts")
        text(player, 104, "")
        for (r in shown(player)) {
            when {
                r.isSkill -> text(player, r.header, skillLine(player, r))
                r.charm -> text(player, r.header, if (pts < 2) "${RED}You need 2 points." else "$GREEN${r.title}")
                else ->
                    text(
                        player,
                        r.header,
                        when {
                            pts >= r.points -> "$GREEN${r.title}"
                            pts < 1 -> "${RED}You need at least 1 point."
                            else -> "${RED}You need ${r.points} points."
                        },
                    )
            }
            if (r.isSkill || r.charm) {
                for (option in 1 until r.children.size) text(player, r.children[option], "$UNSELECTED${r.optionText(option)}")
            }
        }
    }

    private fun skillLine(
        player: Player,
        r: Reward,
    ): String =
        if (player.skills.getMaxLevel(r.skill) < 25) {
            "${RED}Must reach level 25 first."
        } else {
            "$GREEN${r.title} - ${experience(player, r.skill, 1)} xp"
        }

    fun experience(
        player: Player,
        skill: Int,
        points: Int,
    ): Int = experience(player.skills.getMaxLevel(skill), skill, points)

    fun experience(
        level: Int,
        skill: Int,
        points: Int,
    ): Int {
        val n =
            when (skill) {
                Skills.PRAYER -> 18
                Skills.MAGIC, Skills.RANGED -> 32
                else -> 35
            }
        val perPoint = (level * level / 600) * n
        val bonus =
            when {
                points >= 100 -> 1.1
                points >= 10 -> 1.01
                else -> 1.0
            }
        return (points * perPoint * bonus).toInt()
    }

    /**
     * N136: the OSRS Void Knight reward scripts click (synth 2266 interface_select1) on a choice and on Confirm, and play 2277
     * (pillory_wrong) with the refusal message; the rebuilt 267 window had no sound at all.
     */
    private fun click(player: Player) = player.playSound(gg.rsmod.plugins.api.cfg.Sfx.INTERFACE_SELECT1)

    private fun refused(player: Player) = player.playSound(gg.rsmod.plugins.api.cfg.Sfx.PILLORY_WRONG)

    fun select(
        player: Player,
        button: Int,
    ) {
        if (points(player) == 0) {
            refused(player)
            player.message("You don't have enough points.")
            return
        }
        val (reward, option) = Reward.forButton(button) ?: return
        if (reward !in shown(player)) return
        if (points(player) < reward.cost(option)) {
            refused(player)
            player.message("You don't have enough points.")
            return
        }
        if (reward.isSkill && player.skills.getMaxLevel(reward.skill) < 25) {
            refused(player)
            player.message("You need a ${reward.title} level of 25 to purchase this.")
            return
        }
        click(player)
        refresh(player)
        player.attr[SELECTED] = reward to option
        text(player, 104, "${WHITE}${reward.title}")
        if (reward.isSkill || reward.charm) text(player, reward.children[option], "$WHITE${reward.optionText(option)}")
        text(player, reward.header, if (reward.isSkill) "$WHITE${reward.title} - ${experience(player, reward.skill, 1)} xp" else "$WHITE${reward.title}")
    }

    fun confirm(player: Player) {
        val (reward, option) =
            player.attr[SELECTED] ?: run {
                refused(player)
                player.message("Please choose a reward.")
                return
            }
        // An elite upgrade swaps the normal piece, so it needs no free slot.
        if (!reward.isSkill && reward.upgradeFrom.isEmpty() && player.inventory.freeSlotCount == 0) {
            refused(player)
            player.message("You don't have enough inventory space.")
            return
        }
        val cost = reward.cost(option)
        if (points(player) < cost) {
            refused(player)
            player.message("You don't have enough points.")
            return
        }
        if (!meetsItemRequirement(player, reward)) return
        if (reward.upgradeFrom.isNotEmpty()) {
            // Elite void (owner 2026-10-10, same rule as Commodore Tyr): the normal piece is swapped for the elite one.
            val piece = reward.upgradeFrom.firstOrNull { player.inventory.contains(it) }
            if (piece == null) {
                player.message("You need a ${reward.title.removePrefix("Elite Void ").lowercase().let { "void knight $it" }} in your inventory to upgrade.")
                return
            }
            if (!player.inventory.remove(piece).hasSucceeded()) return
        }
        click(player)
        player.closeInterface(PC_REWARD_INTERFACE)
        player.attr.remove(SELECTED)
        gg.rsmod.game.model.attr.PointBalance.take(player.attr, PEST_POINTS, cost) // N106: one balance helper, never below 0
        val message =
            if (reward.isSkill) {
                // N376 (owner 2026-10-24): a bought XP reward is a fixed reward - no bracket rate; the message names what was gained.
                val xp = experience(player, reward.skill, cost)
                val before = player.skills.getCurrentXp(reward.skill)
                player.addXp(reward.skill, xp.toDouble(), modifiers = false)
                val gained = (player.skills.getCurrentXp(reward.skill) - before).toInt()
                "The Void Knight has granted you $gained ${reward.title} experience."
            } else {
                val items =
                    when {
                        reward.charm -> listOf(Item(reward.items[0], CHARM_AMOUNTS[option - 1]))
                        reward.items.size > 1 -> pack(player, reward)
                        else -> listOf(Item(reward.items[0]))
                    }
                items.forEach { give(player, it) }
                // N276: the Collection Log's Pest Control page (OSRS logs a reward when it is bought).
                gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, gg.rsmod.plugins.content.quests.tab.CollectionLogPages.PEST_CONTROL, items.map { it.id to it.amount })
                "The Void Knight has given you a ${reward.title}."
            }
        player.message(message)
        player.message("<col=571D07>Remaining Void Knight Commendation Points: ${points(player)}")
    }

    private fun meetsItemRequirement(
        player: Player,
        reward: Reward,
    ): Boolean {
        reward.skillReq?.let { (skill, level) ->
            if (player.skills.getCurrentLevel(skill) < level) {
                val name = if (skill == Skills.HERBLORE) "herblore" else if (skill == Skills.MINING) "mining" else "farming"
                player.message("You need level $level $name to purchase this pack.")
                return false
            }
        }
        if (reward.void) {
            val skills = intArrayOf(Skills.CONSTITUTION, Skills.ATTACK, Skills.DEFENCE, Skills.STRENGTH, Skills.RANGED, Skills.MAGIC, Skills.PRAYER)
            if (skills.any { player.skills.getMaxLevel(it) < if (it == Skills.PRAYER) 22 else 42 }) {
                player.message("You need level 42 in constitution, attack, defence, strength, ranged, magic, and")
                player.message("22 prayer to purchase the ${reward.title.removePrefix("Void Knight ").lowercase()}.")
                return false
            }
        }
        return true
    }

    /** 2009scape constructPack: herbs/seeds 13-18 in total (1-5 each), minerals 38-43 (16-25 each); herbs and ore noted. */
    private fun pack(
        player: Player,
        reward: Reward,
    ): List<Item> {
        val small = reward == Reward.SEED_PACK || reward == Reward.HERB_PACK
        var left = if (small) Random.nextInt(13, 19) else Random.nextInt(38, 44)
        val out = mutableListOf<Item>()
        for (id in reward.items) {
            var amount = if (small) Random.nextInt(1, 6) else Random.nextInt(16, 26)
            if (amount > left) amount = left
            if (amount < 1) continue
            val itemId =
                if (reward == Reward.SEED_PACK) {
                    id
                } else {
                    val def = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id)
                    if (def.noteLinkId > 0) def.noteLinkId else id
                }
            out.add(Item(itemId, amount))
            left -= amount
        }
        return out
    }

    private fun give(
        player: Player,
        item: Item,
    ) {
        val result = player.inventory.add(item.id, item.amount)
        if (result.completed < item.amount) {
            player.world.spawn(GroundItem(item.id, item.amount - result.completed, player.tile, player))
        }
    }
}
