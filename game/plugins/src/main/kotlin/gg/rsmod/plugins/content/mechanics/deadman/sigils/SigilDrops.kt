package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.content.skills.prayer.burying.BoneData
import gg.rsmod.plugins.content.skills.prayer.scattering.AshData

/** PROJECT48 SIGILS - what the sigils do to NPC drops (called from the one drop spot, DropTableFactory.createDrop). */
object SigilDrops {
    /**
     * S21 Sigil of Devotion: "Dropped Bones are automatically buried. Ashes are automatically scattered. Prayer XP gained from these
     * activities is increased by 200%" (3x). Returns true when the drop was used up this way (nothing lands on the ground).
     */
    fun devotion(
        player: Player,
        item: Item,
    ): Boolean {
        if (!Sigils.effective(player, Sigil.DEVOTION)) return false
        val xp =
            BoneData.boneDefinitions[item.id]?.experience
                ?: AshData.ashDefinitions[item.id]?.experience
                ?: return false
        player.addXp(Skills.PRAYER, xp * 3.0 * item.amount, checkBrawlingGloves = true)
        player.filterableMessage(if (BoneData.boneDefinitions.containsKey(item.id)) "Your sigil buries the bones." else "Your sigil scatters the ashes.")
        return true
    }

    /**
     * The Deadman-only drop table (S-K5 b): the chance of a sigil per kill, by tier. The wiki gives NO number for it (checked pages
     * "Deadman: Armageddon", "Deadman: Armageddon/Sigils": "obtained as drops ... as part of the Deadman-only drop table"), so the chances
     * stay 0 until the owner gives them (STAND.txt VRAGEN) - nothing is invented. Sigil of Prosperity raises the chance by 20%.
     */
    val GLOBAL_TABLE_CHANCE: Map<Int, Double> = mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)

    /** Rolls the Deadman-only table for one kill of [npcId]; returns the sigil item to drop, or null. */
    fun rollGlobalTable(
        player: Player,
        npcId: Int,
    ): Item? {
        val tier = 1 + player.world.random(2)
        val base = GLOBAL_TABLE_CHANCE[tier] ?: return null
        if (base <= 0.0) return null
        val chance = if (Sigils.effective(player, Sigil.PROSPERITY)) base * 1.2 else base
        if (player.world.randomDouble() >= chance) return null
        val sigil = Sigil.values().filter { it.tier == tier }.random()
        return Item(sigil.unattunedId)
    }

    /** S18 Treasure Hunter: "Clue Scrolls drop 3x more often" - the factor on every clue drop chance. */
    fun clueFactor(player: Player): Int = if (Sigils.effective(player, Sigil.THE_TREASURE_HUNTER)) 3 else 1

    /** S50 Bloodhound: a 75% chance that a completed clue step returns the reward casket. */
    fun bloodhound(player: Player): Boolean = Sigils.effective(player, Sigil.THE_BLOODHOUND) && player.world.randomDouble() < 0.75
}
