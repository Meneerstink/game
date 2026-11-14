package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell

/** OSRS Wiki Battle mage / Mage Arena: a matching god cape prevents initial aggression, not retaliation. */
object MageArenaBattleMages {
    val SPELLS = mapOf(
        Npcs.BATTLE_MAGE_913 to CombatSpell.SARADOMIN_STRIKE,
        Npcs.BATTLE_MAGE_914 to CombatSpell.CLAWS_OF_GUTHIX,
        Npcs.BATTLE_MAGE to CombatSpell.FLAMES_OF_ZAMORAK,
    )

    fun tolerates(npc: Npc, player: Player): Boolean {
        val spell = SPELLS[npc.id] ?: return false
        val cape = player.getEquipment(EquipmentType.CAPE)?.id ?: return false
        return MagicCombatFormula.chargeApplies(spell, cape) ||
            (spell == CombatSpell.CLAWS_OF_GUTHIX && cape == Items.GUTHIX_CAPE_10720)
    }
}
