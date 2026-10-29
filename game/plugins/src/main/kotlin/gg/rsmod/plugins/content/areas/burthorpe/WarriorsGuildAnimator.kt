package gg.rsmod.plugins.content.areas.burthorpe

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import java.lang.ref.WeakReference

/**
 * K06 N372 part 1 (owner 2026-10-24: "armour op de Magical animator moet Animated armour spawnen zoals OSRS"): the Warriors' Guild
 * Magical animator. Donor: Novite rev 667 `WarriorsGuild.handleItemOnObject` (object 15621, the full helm / platebody / platelegs
 * of one metal taken, "The animator hums, something appears to be working." / "You stand back.", Animated <metal> Armour 4278 + set,
 * "IM ALIVE!") and `AnimatedArmor` (the armour back on death, 1 in 16 that one piece is destroyed; the armour back in the
 * inventory when the fight ends without a kill). OSRS Wiki "Animated Armour": the tokens per set (bronze 5 ... rune 40) instead of
 * Novite's RS warrior points - OSRS is the rule here, the token is the 667 Warrior guild token 8851.
 */
object WarriorsGuildAnimator {
    const val ANIMATOR = 15621
    const val FIRST_ARMOUR_NPC = 4278

    /** Animator emote 827 (put down), the armour's rise 4166 (Novite). */
    const val PUT_ANIM = 827
    const val RISE_ANIM = 4166

    /** 1 in [BREAK_ONE_IN] kills one piece of the set is destroyed (Novite `AnimatedArmor.getDroppedItems`). */
    const val BREAK_ONE_IN = 16

    const val MSG_HUMS = "The animator hums, something appears to be working."
    const val MSG_STAND_BACK = "You stand back."
    const val MSG_BUSY = "You are already in combat with an animation."
    const val MSG_INCOMPLETE = "You need a plate body, plate legs and full helm of the same type to activate the armour animator."

    /** One metal's set (Novite `WarriorsGuildData.WarriorSet`) and its tokens (OSRS Wiki "Animated Armour"). */
    enum class Set(val helm: Int, val body: Int, val legs: Int, val tokens: Int) {
        BRONZE(Items.BRONZE_FULL_HELM, Items.BRONZE_PLATEBODY, Items.BRONZE_PLATELEGS, 5),
        IRON(Items.IRON_FULL_HELM, Items.IRON_PLATEBODY, Items.IRON_PLATELEGS, 10),
        STEEL(Items.STEEL_FULL_HELM, Items.STEEL_PLATEBODY, Items.STEEL_PLATELEGS, 15),
        BLACK(Items.BLACK_FULL_HELM, Items.BLACK_PLATEBODY, Items.BLACK_PLATELEGS, 20),
        MITHRIL(Items.MITHRIL_FULL_HELM, Items.MITHRIL_PLATEBODY, Items.MITHRIL_PLATELEGS, 25),
        ADAMANT(Items.ADAMANT_FULL_HELM, Items.ADAMANT_PLATEBODY, Items.ADAMANT_PLATELEGS, 30),
        RUNE(Items.RUNE_FULL_HELM, Items.RUNE_PLATEBODY, Items.RUNE_PLATELEGS, 40),
        ;

        val pieces: List<Int> get() = listOf(helm, body, legs)
        val npc: Int get() = FIRST_ARMOUR_NPC + ordinal
    }

    fun setOf(item: Int): Set? = Set.values().firstOrNull { item in it.pieces }

    fun setOfNpc(npcId: Int): Set? = Set.values().firstOrNull { it.npc == npcId }

    /** The player's living animation (one at a time). */
    val ANIMATION = AttributeKey<WeakReference<Npc>>()

    fun active(player: Player): Npc? = player.attr[ANIMATION]?.get()?.takeIf { player.world.npcs.contains(it) && !it.isDead() }

    /** The pieces a kill gives back: the whole set, or - 1 in [BREAK_ONE_IN] - all but one ([roll] picks; -1 = nothing breaks). */
    fun returned(set: Set, roll: Int): List<Int> = if (roll < 0) set.pieces else set.pieces.filterIndexed { i, _ -> i != roll }
}
