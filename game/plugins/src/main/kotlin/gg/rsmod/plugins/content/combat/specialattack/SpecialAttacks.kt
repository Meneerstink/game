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
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import java.util.concurrent.atomic.AtomicLong

/**
 * @author Tom <rspsmods@gmail.com>
 */
object SpecialAttacks {
    private val deflectAttackTokens = AtomicLong()
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
    fun hasSpecialAttack(itemId: Int): Boolean = attacks.containsKey(itemId) || instantAttacks.containsKey(itemId)

    /**
     * Registers an "instant" special that fires the moment the special bar is clicked, without a
     * target (Excalibur, Dragon battleaxe, Dragon pickaxe/hatchet, Staff of light, Granite maul's
     * queued swing). [attack] receives the player; energy is only drained when it returns true.
     */
    fun registerInstant(
        energy: Int,
        vararg weapon: Int,
        attack: (Player) -> Boolean,
    ) {
        weapon.forEach { instantAttacks[it] = InstantSpecial(energy, attack) }
    }

    /**
     * Called when the special bar is toggled. Returns true when the wielded weapon has an instant
     * special (which was then attempted), so the caller must not toggle the special-attack varp.
     */
    fun executeInstant(player: Player): Boolean {
        val weaponItem = player.getEquipment(EquipmentType.WEAPON) ?: return false
        val special = instantAttacks[weaponItem.id] ?: return false
        if (AttackTab.getEnergy(player) < special.energyRequired) {
            player.message("You don't have enough power left.")
            return true
        }
        if (special.attack(player)) {
            AttackTab.setEnergy(player, AttackTab.getEnergy(player) - special.energyRequired)
        }
        return true
    }

    private data class InstantSpecial(val energyRequired: Int, val attack: (Player) -> Boolean)

    private val instantAttacks = mutableMapOf<Int, InstantSpecial>()

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
        // The special's hits are dealt inside attack(); WeaponPoison reads this for the Abyssal tentacle's 1/2 poison chance.
        player.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] = true
        player.attr[AncientCurses.DEFLECT_ATTACK_TOKEN_ATTR] = deflectAttackTokens.incrementAndGet()
        try {
            special.attack(combatContext)
        } finally {
            player.attr.remove(gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS)
            player.attr.remove(AncientCurses.DEFLECT_ATTACK_TOKEN_ATTR)
        }

        return true
    }

    private val attacks = mutableMapOf<Int, SpecialAttack>()
}
