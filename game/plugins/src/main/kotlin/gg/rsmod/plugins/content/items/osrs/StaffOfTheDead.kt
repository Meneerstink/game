package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.STAFF_OF_LIGHT_TIMER
import gg.rsmod.game.model.timer.TOXIC_STAFF_SCALE_TIMER
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.combat.venom

/**
 * OSRS-IMPORT Staff of the dead, Toxic staff of the dead and Power of Death (OSRS Wiki raw wikitext "Staff of the Dead" and
 * "Toxic staff of the dead", fetched 2026-09-14):
 * - Staff of the dead: "1/7 chance" a combat spell uses no runes (Mod Ash, cited on the page); the toxic staff "shares the same
 *   features as the regular staff of the dead", and the uncharged version keeps them except the magic bonus.
 * - Toxic staff: charged with Zulrah's scales, maximum 11,000; "the staff will immediately use 10 scales when the player enters
 *   combat and will use another 10 if the player is still in combat after a minute has passed". Charged: "+8 magic attack bonus"
 *   (cache params) and "a chance to inflict venom on opponents struck by the staff or by spells cast while wielding it" - 25 %
 *   ("The player can also envenom opponents using the staff's melee attack"). 100 % against NPCs with a Serpentine helm: the
 *   helm is not in this cache (N/A). Options Wield/Check/Uncharge (charged), Wield/Dismantle (uncharged); made by using a Magic
 *   fang on a Staff of the dead with 59 Crafting, "reversible, provided that the staff is uncharged". Death: "any scales that were
 *   used to charge it will appear on the floor along with the uncharged staff."
 * - Power of Death (staff of the dead, toxic staff, staff of light, staff of balance): 100 % energy, "halves all of the opponent's
 *   melee damage" for one minute, "but will instantly end if the player takes damage while the staff is not equipped"; message
 *   "Spirits of deceased evildoers offer you their protection."; stacks with the PvP Protect from Melee reduction.
 *   SHARED 667 ID CHANGED ON PURPOSE: the 667 Staff of light had a 50 % chance to halve; OSRS always halves.
 * ADAPTED: scale use is checked when an attack starts or lands on the wielder (enter combat + one minute timer, then again on
 * the next attack after the minute). SOURCE_GAP (recorded): halving rounding (floor), whether the hit that ends the effect is
 * still halved (it is not), re-activation while active (refreshes the minute, never stacks), Check/Uncharge message wording.
 */
object StaffOfTheDead {
    const val MAX_SCALES = 11_000
    const val SCALES_PER_USE = 10
    const val SCALE_USE_TICKS = 100
    const val POWER_OF_DEATH_TICKS = 100
    const val POWER_OF_DEATH_ENERGY = 100
    const val RUNE_SAVE_CHANCE = 7
    const val VENOM_CHANCE = 0.25
    const val FANG_CRAFTING_LEVEL = 59
    const val POWER_OF_DEATH_MESSAGE = "Spirits of deceased evildoers offer you their protection."

    /** Staves with the rune-saving passive and no Ancient Magicks autocast. */
    val DEAD_STAVES = setOf(Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD)

    /** Staves sharing Power of Death that exist in this cache (the Staff of balance does not). */
    val POWER_OF_DEATH_STAVES = DEAD_STAVES + Items.STAFF_OF_LIGHT

    fun scales(item: Item): Int = if (item.id == Items.TOXIC_STAFF_OF_THE_DEAD) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun withScales(
        item: Item,
        scales: Int,
    ): Item {
        val id = if (scales > 0) Items.TOXIC_STAFF_OF_THE_DEAD else Items.TOXIC_STAFF_UNCHARGED
        return Item(id, item.amount).copyAttr(item).also {
            if (scales > 0) it.attr[ItemAttribute.CHARGES] = scales.coerceAtMost(MAX_SCALES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** Scales from a stack of [available] that fit into [item]. */
    fun scalesToAdd(
        item: Item,
        available: Int,
    ): Int = minOf(available, MAX_SCALES - scales(item)).coerceAtLeast(0)

    fun isWieldingDeadStaff(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id in DEAD_STAVES

    fun isWieldingPowerOfDeathStaff(player: Player): Boolean = player.getEquipment(EquipmentType.WEAPON)?.id in POWER_OF_DEATH_STAVES

    /** A combat spell cast with a dead staff uses no runes 1 time in 7. */
    fun savesRunes(player: Player): Boolean = isWieldingDeadStaff(player) && player.world.random(RUNE_SAVE_CHANCE - 1) == 0

    /** Called for the attacker and the target of every attack: uses 10 scales on entering combat and again after each minute. */
    fun onCombat(player: Player) {
        val weapon = player.getEquipment(EquipmentType.WEAPON) ?: return
        if (weapon.id != Items.TOXIC_STAFF_OF_THE_DEAD || player.timers.has(TOXIC_STAFF_SCALE_TIMER)) return
        player.equipment[EquipmentType.WEAPON.id] = withScales(weapon, scales(weapon) - SCALES_PER_USE)
        player.timers[TOXIC_STAFF_SCALE_TIMER] = SCALE_USE_TICKS
    }

    /** 25 % venom for a landed melee hit or combat spell while the charged toxic staff is wielded. */
    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        if (player.getEquipment(EquipmentType.WEAPON)?.id != Items.TOXIC_STAFF_OF_THE_DEAD) return
        if (player.world.randomDouble() < VENOM_CHANCE) target.venom()
    }

    fun activatePowerOfDeath(player: Player): Boolean {
        player.timers[STAFF_OF_LIGHT_TIMER] = POWER_OF_DEATH_TICKS
        player.message(POWER_OF_DEATH_MESSAGE)
        return true
    }

    /** Result of Power of Death on one incoming hit: the damage taken and whether the effect is still active. */
    fun powerOfDeathDamage(
        active: Boolean,
        wieldingStaff: Boolean,
        melee: Boolean,
        damage: Int,
    ): Pair<Int, Boolean> {
        if (!active) return damage to false
        if (damage > 0 && !wieldingStaff) return damage to false
        return (if (melee) damage / 2 else damage) to true
    }

    /** Applies Power of Death to a hit on [target]; ends the effect when damage lands while no Power of Death staff is wielded. */
    fun modifyIncomingDamage(
        target: Player,
        hitType: HitType,
        damage: Int,
    ): Int {
        val active = target.timers.has(STAFF_OF_LIGHT_TIMER)
        if (!active) return damage
        val (taken, stillActive) = powerOfDeathDamage(true, isWieldingPowerOfDeathStaff(target), hitType == HitType.MELEE, damage)
        if (!stillActive) target.timers.remove(STAFF_OF_LIGHT_TIMER)
        return taken
    }
}
