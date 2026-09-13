package gg.rsmod.plugins.content.activity.duel_arena

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player

val DUEL_MATCH_ATTR = AttributeKey<DuelArenaMatch>()

fun Player.getDuelMatch(): DuelArenaMatch? = this.attr[DUEL_MATCH_ATTR]

enum class DuelStage { CONFIGURING, FIGHTING }

/**
 * One challenge/duel, shared by reference between both participants (via [DUEL_MATCH_ATTR] on
 * each [Player]) so a rule/stake change made by either side is immediately visible to both -
 * avoids the two-object-mirroring bookkeeping Novite's own `DuelRules` needs.
 */
class DuelArenaMatch(
    val challenger: Player,
    val opponent: Player,
) {
    val rules =
        mutableSetOf<DuelRule>().apply {
            DuelRule.values().filter { it.defaultActive }.forEach { add(it) }
        }
    val lockedSlots = mutableSetOf<DuelEquipLock>()
    val challengerStake = ItemContainer(challenger.world.definitions, challenger.inventory.capacity, ContainerStackType.NORMAL)
    val opponentStake = ItemContainer(opponent.world.definitions, opponent.inventory.capacity, ContainerStackType.NORMAL)
    var challengerAccepted = false
    var opponentAccepted = false
    var stage = DuelStage.CONFIGURING
    var arenaTile: gg.rsmod.game.model.Tile? = null

    /** RCV-010 C2-a: friendly duels use interfaces 637/639 and carry no stake. */
    var friendly = false

    /** True once both players accepted the rules screen and the confirmation screen (626/639) is shown. */
    var confirming = false

    fun other(player: Player): Player = if (player == challenger) opponent else challenger

    fun stakeOf(player: Player): ItemContainer = if (player == challenger) challengerStake else opponentStake

    fun setAccepted(player: Player, value: Boolean) {
        if (player == challenger) challengerAccepted = value else opponentAccepted = value
    }

    fun isAccepted(player: Player): Boolean = if (player == challenger) challengerAccepted else opponentAccepted

    fun bothAccepted(): Boolean = challengerAccepted && opponentAccepted

    /** Resets both sides' accept flags - Novite cancels acceptance whenever a rule/stake changes. */
    fun resetAccepted() {
        challengerAccepted = false
        opponentAccepted = false
    }

    fun clear() {
        challenger.attr.remove(DUEL_MATCH_ATTR)
        opponent.attr.remove(DUEL_MATCH_ATTR)
    }
}
