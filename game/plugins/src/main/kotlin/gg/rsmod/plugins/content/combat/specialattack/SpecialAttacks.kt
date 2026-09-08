package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedProjectile
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * @author Tom <rspsmods@gmail.com>
 */
object SpecialAttacks {
    fun register(
        energy: Int,
        vararg weapon: Int,
        attack: CombatContext.() -> Unit,
    ) {
        weapon.forEach {
            attacks[it] = SpecialAttack(energy, attack)
        }
    }

    /**
     * Whether [itemId] has a registered special attack. Used to hide the special attack bar
     * (interface 884, component 4) for weapons with no special attack, e.g. Twisted bow.
     */
    fun hasSpecialAttack(itemId: Int): Boolean = attacks.containsKey(itemId)

    fun execute(
        player: Player,
        target: Pawn?,
        world: World,
    ): Boolean {
        val weaponItem = player.getEquipment(EquipmentType.WEAPON) ?: return false
        val special = attacks[weaponItem.id] ?: return false

        if (AttackTab.getEnergy(player) < special.energyRequired) {
            player.message("You don't have enough power left.")
            return false
        }

        if (RangedProjectile.MORRIGANS_JAVELIN.items.contains(weaponItem.id) && target is Npc) {
            player.message("This special attack can only be used against another player.")
            return false
        }

        AttackTab.setEnergy(player, AttackTab.getEnergy(player) - special.energyRequired)

        val combatContext = CombatContext(world, player)
        target?.let { combatContext.target = it }
        special.attack(combatContext)

        return true
    }

    private val attacks = mutableMapOf<Int, SpecialAttack>()
}
