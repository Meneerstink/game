package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.quests.foundation.FoundationRewards
import gg.rsmod.plugins.content.skills.summoning.Familiar
import gg.rsmod.plugins.content.skills.summoning.SummoningPouchData
import gg.rsmod.plugins.content.unlocks.UnlockNpcRewards

/**
 * The one-time Summoning start at the Quest Guide (owner 2026-09-26): Wolf Whistle's reward and enough supplies to
 * infuse pouches up to [NewPlayerConfig.summoningTargetLevel].
 *
 * Wolf Whistle (RuneScape Wiki / Void quests.toml): 276 Summoning XP and 275 gold charms, and the skill unlocked. The kit
 * is computed from the account's own Summoning XP after that, the plan and the XP rate in `new_player.yml` - the same
 * arithmetic as infusing (`summoning_crafting.plugin.kts`: pouch creation XP through Player.addXp, i.e. times the level
 * curve and the normal rate; bonus xp is not counted, so the kit is never short).
 */
object SummoningKit {
    val CLAIMED = AttributeKey<Boolean>(persistenceKey = "foundation_summoning_kit_claimed")

    const val WOLF_WHISTLE_XP = 276.0
    const val WOLF_WHISTLE_GOLD_CHARMS = 275

    data class Kit(val pouches: Map<SummoningPouchData, Int>, val items: List<Pair<Int, Int>>)

    /** The engine's level curve (Player.interpolate(1.0, 5.0, level)). */
    fun levelCurve(level: Int): Double = 1.0 + (5.0 - 1.0) / 97 * (level - 1)

    fun compute(config: NewPlayerConfig, startXp: Double, alreadyHeldGoldCharms: Int = 0): Kit {
        val pouches = linkedMapOf<SummoningPouchData, Int>()
        var xp = startXp
        val targetXp = SkillSet.getXpForLevel(config.summoningTargetLevel).toDouble()
        while (xp < targetXp) {
            val level = SkillSet.getLevelForXp(xp)
            val row = config.summoningPlan.last { it.fromLevel <= level }
            xp += row.pouch.creationExperience * levelCurve(level) * config.normalXpRate
            pouches[row.pouch] = (pouches[row.pouch] ?: 0) + 1
        }
        val items = linkedMapOf<Int, Int>()
        fun add(item: Int, amount: Int) {
            if (amount > 0) items[item] = (items[item] ?: 0) + amount
        }
        pouches.forEach { (pouch, count) ->
            add(pouch.charm, count)
            add(Items.SPIRIT_SHARDS, pouch.shards * count)
            add(Items.POUCH, count)
            pouch.tertiaries.forEach { add(it, count) }
        }
        items[Items.GOLD_CHARM]?.let { need -> items[Items.GOLD_CHARM] = need - minOf(need, alreadyHeldGoldCharms) }
        return Kit(pouches, items.filterValues { it > 0 }.map { it.key to it.value })
    }

    /** Wolf Whistle's reward (if Summoning was not unlocked yet) and the kit, once per account. */
    fun claim(player: Player, config: NewPlayerConfig = NewPlayerConfig.current): Boolean {
        if (player.attr[CLAIMED] == true) {
            player.message("You have already received your Summoning supplies.")
            return false
        }
        player.attr[CLAIMED] = true
        var wolfWhistleCharms = 0
        if (player.attr[UnlockNpcRewards.SUMMONING_REWARDED] != true) {
            player.attr[UnlockNpcRewards.SUMMONING_REWARDED] = true
            Familiar.unlockInterface(player)
            player.addXp(Skills.SUMMONING, WOLF_WHISTLE_XP, modifiers = false)
            FoundationRewards.grant(player, Items.GOLD_CHARM, WOLF_WHISTLE_GOLD_CHARMS)
            wolfWhistleCharms = WOLF_WHISTLE_GOLD_CHARMS
            player.message("Wolf Whistle: Summoning unlocked, 276 Summoning XP and 275 gold charms.")
        }
        val kit = compute(config, player.skills.getCurrentXp(Skills.SUMMONING), wolfWhistleCharms)
        kit.items.forEach { (item, amount) -> FoundationRewards.grant(player, item + noteOffset(player, item), amount) }
        if (kit.items.isEmpty()) {
            player.message("Your Summoning is already level ${config.summoningTargetLevel} or higher, so you need no training supplies.")
        } else {
            player.message("You receive Summoning supplies to train to level ${config.summoningTargetLevel}: ${kit.pouches.entries.joinToString { "${it.value} ${it.key.name.lowercase().replace('_', ' ')}" }} pouches.")
        }
        return true
    }

    /** Non-stackable supplies (bones, ore, flowers, meat, empty pouches) are handed out noted so they fit. */
    private fun noteOffset(player: Player, item: Int): Int {
        val def = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, item)
        if (def.stackable) return 0
        val noted = def.noteLinkId
        return if (noted > 0) noted - item else 0
    }
}
