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
}
