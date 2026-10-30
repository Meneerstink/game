package gg.rsmod.plugins.content.mechanics

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound

/**
 * The sound of putting an item away into a store (bank, deposit box, beast of burden) or taking one out.
 *
 * Owner decision 2026-10-03: "just make it the normal sound when taking item or putting in the bank" - no donor (Void, Novite,
 * 2009scape) plays a sound for bank or familiar transfers, so these reuse the server's own item cues: 2739 put_down (the drop
 * sound, IfButton1Handler) for putting in and 2582 (the ground item pick-up sound, GroundItemPathAction) for taking out.
 *
 * One sound per kind per tick: a Deposit-all moves up to 28 stacks in one tick and must sound once, not 28 times.
 */
object ItemMoveSounds {
    /**
     * N433 (owner 2026-10-30: storing in an Upgraded pet "hoor je het drop-geluid"; choice "Alles 'bank drawer'"): every put-in -
     * a beast of burden, the rune pouch, essence pouches, charging an item - sounds like the bank deposit, no longer like a drop.
     */
    const val PUT_IN = gg.rsmod.plugins.api.cfg.Sfx.BANK_DRAWER
    const val TAKE_OUT = 2582

    /** N65 (owner 2026-10-22: "het verkeerde geluid (drop)", choice: Bank drawer): depositing into the bank or a deposit box. */
    const val BANK_DEPOSIT = gg.rsmod.plugins.api.cfg.Sfx.BANK_DRAWER

    private val LAST_SOUND = AttributeKey<Long>()

    fun putIn(player: Player) = play(player, PUT_IN)

    fun bankDeposit(player: Player) = play(player, BANK_DEPOSIT)

    fun takeOut(player: Player) = play(player, TAKE_OUT)

    private fun play(player: Player, id: Int) {
        val stamp = (player.world.currentCycle.toLong() shl 16) or id.toLong()
        if (player.attr[LAST_SOUND] == stamp) return
        player.attr[LAST_SOUND] = stamp
        player.playSound(id)
    }
}
