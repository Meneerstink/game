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

    // ---- K06 N372 part 2: the cyclops room (OSRS Wiki "Warriors' Guild": at least 100 tokens to enter, 10 tokens a minute) ----

    /** The cyclops room door on the top floor (Novite CYCLOPS_LOBBY 2843,3535,2 / OUTSIDE_CYCLOPS 2844,3536,2: the lobby is west). */
    val CYCLOPS_DOOR = gg.rsmod.game.model.Tile(2847, 3536, 2)
    val OUTSIDE_CYCLOPS = gg.rsmod.game.model.Tile(2844, 3536, 2)
    const val TOKENS_TO_ENTER = 100
    const val TOKENS_PER_MINUTE = 10
    const val MINUTE_TICKS = 100

    /** CUSTOM wording (no OSRS transcript line found for these two). */
    const val MSG_NOT_ENOUGH = "You need at least 100 Warrior guild tokens to enter the cyclops room."
    const val MSG_RUN_OUT = "You have run out of Warrior guild tokens and are ushered out of the cyclops room."

    /** The top-floor cyclops room: the door's own tile and east of it (the door is on the tile's west face; the lobby is west). */
    fun inCyclopsRoom(tile: gg.rsmod.game.model.Tile): Boolean = tile.height == 2 && tile.x in 2847..2877 && tile.z in 3528..3557

    // ---- K06 N372 part 3: the dummy room (Ajjat). Novite WarriorTimer: every 14 ticks a dummy (15624-15630) pops up on one of 8
    // posts for 10 ticks; OSRS Wiki: "Each dummy correctly hit will reward players with 15 Attack experience and 2 warrior guild tokens."

    val DUMMY_POSTS: List<Pair<gg.rsmod.game.model.Tile, Int>> =
        listOf(
            gg.rsmod.game.model.Tile(2860, 3549, 0) to 1, gg.rsmod.game.model.Tile(2860, 3547, 0) to 1, gg.rsmod.game.model.Tile(2859, 3545, 0) to 2,
            gg.rsmod.game.model.Tile(2857, 3545, 0) to 2, gg.rsmod.game.model.Tile(2855, 3546, 0) to 3, gg.rsmod.game.model.Tile(2855, 3548, 0) to 3,
            gg.rsmod.game.model.Tile(2856, 3550, 0) to 0, gg.rsmod.game.model.Tile(2858, 3550, 0) to 0,
        )
    val DUMMIES = 15624..15630
    const val DUMMY_EVERY = 14
    const val DUMMY_UP_TICKS = 10
    const val DUMMY_XP = 15.0
    const val DUMMY_TOKENS = 2
    const val MSG_DUMMY_HIT = "You whack the dummy successfully!"
    const val MSG_DUMMY_WRONG = "You whack the dummy using the wrong attack style."
    const val MSG_DUMMY_AGAIN = "You have already hit this dummy."

    fun inDummyRoom(tile: gg.rsmod.game.model.Tile): Boolean = tile.height == 0 && tile.x in 2852..2863 && tile.z in 3542..3553

    /** Novite `isProperHit`: what each dummy wants (an xp style or an attack type). */
    fun rightStyle(
        dummy: Int,
        xp: gg.rsmod.game.model.combat.XpMode,
        type: gg.rsmod.game.model.combat.StyleType,
    ): Boolean =
        when (dummy) {
            15624 -> xp == gg.rsmod.game.model.combat.XpMode.ATTACK_XP
            15625 -> type == gg.rsmod.game.model.combat.StyleType.SLASH
            15626 -> xp == gg.rsmod.game.model.combat.XpMode.STRENGTH_XP
            15627 -> xp == gg.rsmod.game.model.combat.XpMode.SHARED_XP
            15628 -> type == gg.rsmod.game.model.combat.StyleType.CRUSH
            15629 -> type == gg.rsmod.game.model.combat.StyleType.STAB
            15630 -> xp == gg.rsmod.game.model.combat.XpMode.DEFENCE_XP
            else -> false
        }

    /** The pieces a kill gives back: the whole set, or - 1 in [BREAK_ONE_IN] - all but one ([roll] picks; -1 = nothing breaks). */
    fun returned(set: Set, roll: Int): List<Int> = if (roll < 0) set.pieces else set.pieces.filterIndexed { i, _ -> i != roll }
}
