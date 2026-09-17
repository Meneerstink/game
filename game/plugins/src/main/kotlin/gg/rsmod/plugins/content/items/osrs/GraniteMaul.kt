package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ATTACK_DELAY
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.getLastHit
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * OSRS-IMPORT Granite maul - Quick Smash (OSRS Wiki "Granite maul" raw wikitext, Special attack + Mechanics sections, fetched
 * 2026-09-14):
 * - "consumes 60% of the player's special attack energy and deals an instant attack. This is reduced to 50% when using a
 *   granite maul (ornate handle)" (5 September 2019); "Quick Smash does not increase accuracy".
 * - Homing: "for 5 ticks after attacking a target with any weapon, clicking on the special attack bar with the granite maul
 *   equipped while next to that target will automatically activate a special attack without having to re-click the target.
 *   However, clicking the special attack bar multiple times in one game tick toggles the activation and reactivation of this
 *   homing feature".
 * - "when the special attack bar is clicked on to deselect it, a 3 tick window is initiated during which clicking on an
 *   opponent (or clicking the special attack bar a third time while next to an opponent one has attacked within 5 ticks, per
 *   the homing feature) will cause up to two special attacks to activate depending on available special attack energy"
 *   ("assuming neither click of the special attack bar triggers the homing feature").
 * - "the special attack does not give the player any attack cooldown, regardless of if it was used to initiate combat or not".
 * - "Players may no longer pre-queue the Granite maul special attack" (22 November 2018).
 * ADAPTED_TO_667: the 667 Quick Smash animation 1667, graphic 340 and sound; "next to" uses this server's melee reach; the
 * "wasteful" warning when initiating combat with the special is not shown (text not quoted on the page).
 */
object GraniteMaul {
    const val COST = 60
    const val ORNATE_HANDLE_COST = 50
    const val HOMING_TICKS = 5
    const val DESELECT_WINDOW_TICKS = 3
    const val MAX_PREPARED = 2
    const val ANIMATION = 1667
    const val GRAPHIC = 340
    const val NO_ENERGY_MESSAGE = "You don't have enough power left."

    /** Game cycle of the player's last attack (set by `Combat.postAttack`), for the 5-tick homing rule. */
    val LAST_ATTACK_CYCLE = AttributeKey<Int>()
    private val BAR_CLICK_CYCLE = AttributeKey<Int>()
    private val BAR_CLICKS = AttributeKey<Int>()
    private val WINDOW_OPENED = AttributeKey<Int>()

    fun cost(weaponId: Int?): Int? =
        when (weaponId) {
            Items.GRANITE_MAUL -> COST
            Items.GRANITE_MAUL_ORNATE_HANDLE -> ORNATE_HANDLE_COST
            else -> null
        }

    fun isWielding(player: Player): Boolean = cost(player.getEquipment(EquipmentType.WEAPON)?.id) != null

    /** Smashes that fire: one, or up to two inside the deselect window, limited by the energy. */
    fun smashCount(
        energy: Int,
        cost: Int,
        doubled: Boolean,
    ): Int = minOf(if (doubled) MAX_PREPARED else 1, energy / cost).coerceAtLeast(0)

    fun homingActive(
        lastAttackCycle: Int?,
        now: Int,
    ): Boolean = lastAttackCycle != null && now - lastAttackCycle in 0..HOMING_TICKS

    /** The deselect window counts only for clicks/attacks after the tick it opened in, for [DESELECT_WINDOW_TICKS] ticks. */
    fun windowActive(
        opened: Int?,
        now: Int,
    ): Boolean = opened != null && now > opened && now <= opened + DESELECT_WINDOW_TICKS

    /** Special bar click with a granite maul wielded: toggles the bar, opens the deselect window, evaluates homing next tick. */
    fun onBarClick(player: Player) {
        val now = player.world.currentCycle
        val wasEnabled = AttackTab.isSpecialEnabled(player)
        player.toggleVarp(AttackTab.SPECIAL_ATTACK_VARP)
        if (wasEnabled && !windowActive(player.attr[WINDOW_OPENED], now)) {
            player.attr[WINDOW_OPENED] = now
        }
        if (player.attr[BAR_CLICK_CYCLE] == now) {
            player.attr[BAR_CLICKS] = (player.attr[BAR_CLICKS] ?: 0) + 1
            return
        }
        player.attr[BAR_CLICK_CYCLE] = now
        player.attr[BAR_CLICKS] = 1
        player.world.queue {
            wait(1)
            if (!player.isOnline || player.isDead()) return@queue
            evaluateHoming(player, now)
        }
    }

    private fun evaluateHoming(
        player: Player,
        clickCycle: Int,
    ) {
        // An even number of clicks in one tick toggles the homing activation back off.
        if ((player.attr[BAR_CLICKS] ?: 0) % 2 == 0 || !isWielding(player)) return
        val target = player.getLastHit() ?: return
        if (target is Player && !target.isOnline) return
        if (!homingActive(player.attr[LAST_ATTACK_CYCLE], clickCycle) || !inReach(player, target)) return
        smash(player, target, doubled = windowActive(player.attr[WINDOW_OPENED], clickCycle))
    }

    /** Combat cycle hook, before the attack delay: an enabled bar or an open deselect window fires on the target in reach. */
    fun onCombatCycle(
        player: Player,
        target: Pawn,
    ): Boolean {
        if (!isWielding(player)) return false
        val doubled = !AttackTab.isSpecialEnabled(player) && windowActive(player.attr[WINDOW_OPENED], player.world.currentCycle)
        if (!AttackTab.isSpecialEnabled(player) && !doubled) return false
        if (!inReach(player, target)) return false
        return smash(player, target, doubled)
    }

    fun inReach(
        player: Player,
        target: Pawn,
    ): Boolean = !target.isDead() && player.tile.getDistance(target.tile) <= player.getSize() && Combat.canAttack(player, target, MeleeCombatStrategy)

    /** Performs the smash(es) without touching the attack delay; [drain] false when the caller already used the energy. */
    fun smash(
        player: Player,
        target: Pawn,
        doubled: Boolean,
        drain: Boolean = true,
    ): Boolean {
        val cost = cost(player.getEquipment(EquipmentType.WEAPON)?.id) ?: return false
        AttackTab.disableSpecial(player)
        player.attr.remove(WINDOW_OPENED)
        val count = if (drain) smashCount(AttackTab.getEnergy(player), cost, doubled) else 1
        if (count <= 0) {
            player.message(NO_ENERGY_MESSAGE)
            return false
        }
        repeat(count) {
            if (drain) AttackTab.setEnergy(player, AttackTab.getEnergy(player) - cost)
            player.animate(ANIMATION)
            player.graphic(GRAPHIC, 96)
            player.playSound(Sfx.QUICKSMASH)
            SpecialAttackSupport.meleeHit(player, target, delay = 0)
        }
        // "does not give the player any attack cooldown": combat bookkeeping, attack delay left as it was.
        val remaining = if (player.timers.has(ATTACK_DELAY)) player.timers[ATTACK_DELAY] else null
        Combat.postAttack(player, target)
        if (remaining != null) player.timers[ATTACK_DELAY] = remaining else player.timers.remove(ATTACK_DELAY)
        return true
    }
}
