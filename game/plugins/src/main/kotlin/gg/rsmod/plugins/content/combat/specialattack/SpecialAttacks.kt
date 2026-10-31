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
     * The reach of a special that is not fired from its weapon's normal attack range, e.g. the Eclipse atlatl's Eclipse
     * ("performed at melee distance" from a 5-tile ranged weapon). Owner 2026-09-29: the combat cycle only knew the weapon's
     * range, so the player stayed where he was and every such special was refused ("You need to be next to your target").
     * The combat cycle reads [armedReach] instead of the weapon range while such a special is armed: it walks the player to
     * [range] (1 = a melee tile, OSRS "if used far away it will make your character run towards your target"), keeps chasing
     * until he stands there, and skips the ammo check when [needsAmmo] is false. [requirement] is what the special itself
     * demands (e.g. the full Eclipse set); without it the special is refused where the player stands, as before.
     */
    class SpecialReach(
        val range: Int,
        val needsAmmo: Boolean,
        val requirement: (Player) -> Boolean,
    )

    private val reaches = mutableMapOf<Int, SpecialReach>()

    fun registerReach(
        vararg weapon: Int,
        range: Int,
        needsAmmo: Boolean = true,
        requirement: (Player) -> Boolean = { true },
    ) {
        weapon.forEach { reaches[it] = SpecialReach(range, needsAmmo, requirement) }
    }

    /** The [SpecialReach] of the wielded weapon while its special is armed and can fire, else null. */
    fun armedReach(player: Player): SpecialReach? {
        if (!AttackTab.isSpecialEnabled(player)) return null
        val weaponId = player.getEquipment(EquipmentType.WEAPON)?.id ?: return null
        val reach = reaches[weaponId] ?: return null
        val special = attacks[weaponId] ?: return null
        if (AttackTab.getEnergy(player) < special.energyRequired || !reach.requirement(player)) return null
        return reach
    }

    /**
     * Whether [itemId] has a registered special attack. Used to hide the special attack bar
     * (interface 884, component 4) for weapons with no special attack, e.g. Twisted bow.
     */
    fun hasSpecialAttack(itemId: Int): Boolean = attacks.containsKey(itemId) || instantAttacks.containsKey(itemId)

    /** Whether [itemId]'s special fires from the special bar itself ([executeInstant]) rather than on the next attack. */
    fun hasInstantSpecial(itemId: Int): Boolean = instantAttacks.containsKey(itemId)

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

    /** Audit C-15: what a special-attack attempt did this combat cycle. */
    enum class Outcome {
        /** No special ran (no special on the weapon, too little energy, refused target): the normal attack follows. */
        NOT_USED,

        /** The special was performed and its energy spent. */
        PERFORMED,

        /** The special ran but could not be performed ([CombatContext.specialFailed]): energy returned, no attack this cycle. */
        FAILED,
    }

    fun execute(
        player: Player,
        target: Pawn?,
        world: World,
    ): Boolean = perform(player, target, world) == Outcome.PERFORMED

    fun perform(
        player: Player,
        target: Pawn?,
        world: World,
    ): Outcome {
        val weaponItem = player.getEquipment(EquipmentType.WEAPON) ?: return Outcome.NOT_USED
        val special = attacks[weaponItem.id] ?: return Outcome.NOT_USED

        if (AttackTab.getEnergy(player) < special.energyRequired) {
            player.message("You don't have enough power left.")
            return Outcome.NOT_USED
        }

        if (RangedProjectile.MORRIGANS_JAVELIN.items.contains(weaponItem.id) &&
            target is Npc
        ) {
            player.message("This special attack can only be used against another player.")
            return Outcome.NOT_USED
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

        if (combatContext.failed) {
            // Audit C-15: the energy is only really spent on success. It is drained before the attack (specials read the
            // remaining energy) and returned here when the special could not be performed.
            AttackTab.setEnergy(player, minOf(100, AttackTab.getEnergy(player) + special.energyRequired))
            return Outcome.FAILED
        }
        return Outcome.PERFORMED
    }

    private val attacks = mutableMapOf<Int, SpecialAttack>()
}
