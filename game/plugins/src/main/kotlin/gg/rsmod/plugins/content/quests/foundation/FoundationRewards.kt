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
 * An experience lamp a quest hands out. Owner 2026-09-26: quest rewards go straight into the inventory, so every lamp is a
 * real item ([item]: the Experience lamp for any skill, the Combat lamp otherwise) and a record of what it gives waits on
 * the account; rubbing the item uses the oldest record for that item ([FoundationRewards.takeLamp]). [skills] are the
 * skills it may go to; lamps have no level requirement (owner 2026-09-26), [minLevel] is kept only for old records.
 */
data class QuestLamp(val xp: Int, val skills: List<Int>, val minLevel: Int, val label: String) {
    val item: Int get() = if (skills.size >= FoundationRewards.ALL_SKILLS.size) gg.rsmod.plugins.api.cfg.Items.EXPERIENCE_LAMP else gg.rsmod.plugins.api.cfg.Items.COMBAT_LAMP_15390

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
        gg.rsmod.plugins.content.quests.QuestStatus.sync(player) // Quest Points = the completed quests of the list
        reward.unlock(player)
        reward.xp.forEach { (skill, amount) -> player.addXp(skill, amount) } // N92: quest XP gets the Annihilation rate (owner 10-17)
        reward.lamps.forEach {
            addLamp(player, it)
            grant(player, it.item, 1)
        }
        reward.items.forEach { (item, amount) -> grant(player, item, amount) }
        if (reward.lamps.isNotEmpty()) {
            player.message("<col=ef1020>Your quest experience lamps are in your inventory - rub them to choose a skill.</col>")
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

    /** The oldest waiting lamp that [item] stands for, removed from the account; null when none waits (an ordinary lamp). */
    fun takeLamp(player: Player, item: Int): QuestLamp? {
        val lamps = pendingLamps(player).toMutableList()
        val index = lamps.indexOfFirst { it.item == item }
        if (index < 0) return null
        val lamp = lamps.removeAt(index)
        setPendingLamps(player, lamps)
        return lamp
    }

    /** The oldest waiting lamp for [item], without taking it. */
    fun peekLamp(player: Player, item: Int): QuestLamp? = pendingLamps(player).firstOrNull { it.item == item }

    val LAMPS_ITEMISED = AttributeKey<Boolean>(persistenceKey = "foundation_lamps_itemised")

    /** Lamps that waited at the Quest Guide before 2026-09-26 become items once. */
    fun itemiseOldLamps(player: Player) {
        if (player.attr[LAMPS_ITEMISED] == true) return
        player.attr[LAMPS_ITEMISED] = true
        val lamps = pendingLamps(player)
        lamps.forEach { grant(player, it.item, 1) }
        if (lamps.isNotEmpty()) player.message("<col=ef1020>Your ${lamps.size} waiting quest lamp${if (lamps.size == 1) " is" else "s are"} now in your inventory - rub to use.</col>")
    }

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

    /**
     * Night run 2026-10-08 (new players): [grant] sends a quest item to the bank when the inventory is full, and the next
     * step's npc then refused with no hint ("Bring all four diamonds", "No Seal, no voyage"). True when every item of
     * [items] the player does not carry is in the bank - the npc then says where it is.
     */
    fun waitingInBank(player: Player, items: Collection<Int>): Boolean {
        val missing = items.filter { !player.inventory.contains(it) && !player.equipment.contains(it) }
        return missing.isNotEmpty() && missing.all { player.bank.contains(it) }
    }

    const val IN_BANK_LINE = "It's in your bank - your inventory was full when you got it. Take it out and come back."

    val COMBAT_SKILLS = listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION, Skills.RANGED, Skills.MAGIC)
    val ALL_SKILLS = (Skills.ATTACK..Skills.DUNGEONEERING).toList()
}
