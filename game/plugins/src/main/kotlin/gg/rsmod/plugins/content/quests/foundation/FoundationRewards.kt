package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.quests.buildQuestFinish

/**
 * An experience lamp a quest hands out, kept on the account until the player picks a skill at the Quest Guide
 * ([FoundationQuests.claimLamp]). [skills] are the skills it may go to and [minLevel] the level the chosen skill needs
 * (both from the quest's wiki reward), so a lamp waits safely until the player is ready, like the real items.
 */
data class QuestLamp(val xp: Int, val skills: List<Int>, val minLevel: Int, val label: String) {
    fun encode(): String = "$xp:${skills.joinToString(",")}:$minLevel:$label"

    companion object {
        fun decode(text: String): QuestLamp? {
            val parts = text.split(':', limit = 4)
            if (parts.size != 4) return null
            val skills = parts[1].split(',').mapNotNull { it.toIntOrNull() }
            return QuestLamp(parts[0].toIntOrNull() ?: return null, skills, parts[2].toIntOrNull() ?: return null, parts[3])
        }
    }
}

/**
 * What a foundation quest gives on completion. [xp] is exact wiki experience, added without the level curve, bonus xp or
 * rate multiplier (`modifiers = false`); [unlock] switches on what the quest unlocks through the flags the rest of the
 * server already checks (spellbooks, curses, Knight Waves, Ava's assembler, ...).
 */
class QuestReward(
    val icon: Int,
    val xp: List<Pair<Int, Double>> = emptyList(),
    val lamps: List<QuestLamp> = emptyList(),
    val items: List<Pair<Int, Int>> = emptyList(),
    val lines: List<String>,
    val unlock: (Player) -> Unit = {},
)

/**
 * The one completion path of the foundation quests - playing the short quest and picking it at the Quest Guide both end
 * here. It is idempotent per account: the persisted [rewardedKey] flag is written before anything is handed out, so a
 * second call (a second click, a reconnect mid-dialogue, a replayed packet) can never pay twice.
 */
object FoundationRewards {
    val PENDING_LAMPS = AttributeKey<String>(persistenceKey = "foundation_pending_lamps")

    private val rewardedKeys = mutableMapOf<Int, AttributeKey<Boolean>>()

    fun rewardedKey(quest: ShortQuest): AttributeKey<Boolean> =
        synchronized(rewardedKeys) {
            rewardedKeys.getOrPut(quest.slot) { AttributeKey(persistenceKey = "foundation_quest_rewarded_${quest.slot}") }
        }

    fun isRewarded(player: Player, quest: ShortQuest): Boolean = player.attr[rewardedKey(quest)] == true

    /** Completes [quest] with its full reward; false (and nothing happens) when the account already had it. */
    fun complete(player: Player, quest: ShortQuest): Boolean {
        if (isRewarded(player, quest)) return false
        player.attr[rewardedKey(quest)] = true
        val reward = quest.reward
        quest.setStage(player, quest.completedValue)
        player.setVarp(Varps.QUEST_POINTS, player.getVarp(Varps.QUEST_POINTS) + quest.pointReward)
        reward.unlock(player)
        reward.xp.forEach { (skill, amount) -> player.addXp(skill, amount, modifiers = false) }
        reward.lamps.forEach { addLamp(player, it) }
        reward.items.forEach { (item, amount) -> grant(player, item, amount) }
        if (reward.lamps.isNotEmpty()) {
            player.message("Your quest experience lamps wait for you at the Quest Guide in the Grand Exchange hall.")
        }
        val lines = mutableListOf("${quest.pointReward} Quest Point${if (quest.pointReward == 1) "" else "s"}")
        lines += reward.lines
        player.buildQuestFinish(quest, reward.icon, *lines.toTypedArray())
        return true
    }

    fun pendingLamps(player: Player): List<QuestLamp> =
        player.attr[PENDING_LAMPS]?.split(';')?.filter { it.isNotBlank() }?.mapNotNull { QuestLamp.decode(it) } ?: emptyList()

    fun setPendingLamps(player: Player, lamps: List<QuestLamp>) {
        if (lamps.isEmpty()) player.attr.remove(PENDING_LAMPS) else player.attr[PENDING_LAMPS] = lamps.joinToString(";") { it.encode() }
    }

    private fun addLamp(player: Player, lamp: QuestLamp) = setPendingLamps(player, pendingLamps(player) + lamp)

    /** Inventory first, then the bank; only when both are full does the rest land on the ground under the player. */
    fun grant(player: Player, item: Int, amount: Int) {
        var left = amount - player.inventory.add(item, amount).completed
        if (left > 0) {
            left -= player.bank.add(item, left).completed
            if (left < amount) player.message("Some of your reward was sent to your bank because your inventory is full.")
        }
        if (left > 0) {
            player.world.spawn(GroundItem(item, left, player.tile, player))
            player.message("Your bank is full, so the rest of your reward was placed at your feet.")
        }
    }

    val COMBAT_SKILLS = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC)
    val ALL_SKILLS = (Skills.ATTACK..Skills.DUNGEONEERING).toList()
}
