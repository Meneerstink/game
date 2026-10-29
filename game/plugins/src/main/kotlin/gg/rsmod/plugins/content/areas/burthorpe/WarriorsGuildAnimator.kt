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

    // ---- K06 N372 part 4: shot put (OSRS Wiki "Calculator:Shot put": X = Strength + run energy + style - shot weight) ----

    const val SHOT_18LB = 15664
    const val SHOT_22LB = 15665

    /** The three throws, their bonus S and the player's throw animation (Novite ShotputD). */
    enum class Throw(val label: String, val bonus: Int, val anim: Int) {
        STANDING("Standing throw.", 30, 15079),
        STEP("Step and throw.", 20, 15080),
        SPIN("Spin and throw.", 10, 15078),
    }

    fun shotWeight(obj: Int): Int = if (obj == SHOT_22LB) 22 else 18

    fun shotX(
        strength: Int,
        runEnergy: Int,
        style: Throw,
        weight: Int,
    ): Int = strength + runEnergy + style.bonus - weight

    /** The distance table of the calculator (X < 58 -> 1 yard ... X >= 216 -> 14 yards). */
    private val DISTANCE_STEPS = intArrayOf(58, 71, 84, 97, 110, 124, 137, 150, 163, 176, 190, 203, 216)

    fun shotDistance(x: Int): Int = DISTANCE_STEPS.indexOfFirst { x < it }.let { if (it < 0) 14 else it + 1 }

    /** Success chance X / 250. */
    fun shotSuccess(x: Int): Double = (x / 250.0).coerceIn(0.0, 1.0)

    fun shotXp(x: Int): Double = x * 0.7

    fun shotEnergy(x: Int): Double = x * 0.1

    /** Tokens: 1 + distance (18lb) or 3 + distance (22lb). */
    fun shotTokens(
        weight: Int,
        distance: Int,
    ): Int = (if (weight == 22) 3 else 1) + distance

    const val MSG_SHOT_HANDS = "You must have both your hands free in order to throw a shotput."
    const val MSG_SHOT_BREATH = "You take a deep breath and prepare yourself."
    const val MSG_SHOT_FUMBLE = "You fumble and drop the shot onto your toe. Ow!"

    // ---- K06 N372 part 5: keg balance (Jimmy's challenge). OSRS Wiki "Keg": head, hands, weapon and shield slots empty, up to
    // five kegs, Strength xp and tokens while they stay on your head, run energy pays for it, a fall deals up to 4 damage and the
    // keg goes back. Donors Novite/Darkan: kegs 15669-15673 (keg 15668 while the player's varbit is 0), "One barrel".."Five
    // barrels" 8860-8864 in the head slot, walk anim 2671, pick-up anim 4180, fall gfx 689 - kegs. The wiki gives no rates, so the
    // drain, fall chance, xp and tokens below are CUSTOM (Darkan's xp; tokens kept under the catapult's 750 an hour).

    const val KEG = 15668

    /** Keg loc -> the player's varbit that hides it once picked up (cache loc defs, opcode 77). */
    val KEG_VARBITS = mapOf(15669 to 2253, 15670 to 2254, 15671 to 2255, 15672 to 2256, 15673 to 2252)
    const val MAX_KEGS = 5
    const val FIRST_BARREL = 8860
    val BARRELS = FIRST_BARREL until FIRST_BARREL + MAX_KEGS
    const val KEG_RENDER = 2671
    const val KEG_PICK_ANIM = 4180
    const val KEG_FALL_GFX = 689
    const val KEG_PICK_ENERGY = 5.0
    const val KEG_REWARD_EVERY = 10
    const val KEG_MAX_HIT = 4

    const val MSG_KEG_FREE = "You must have both your hands and head free to balance kegs."
    const val MSG_KEG_MAX = "You can't balance any more kegs."
    const val MSG_KEG_FALL = "You lose balance and the kegs fall onto your head."
    const val MSG_KEG_RUN = "You can't run while balancing kegs."

    fun barrel(kegs: Int): Int = FIRST_BARREL + kegs - 1

    /** The keg room on the first floor (south of the shot put pit). */
    fun inKegRoom(tile: gg.rsmod.game.model.Tile): Boolean = tile.height == 1 && tile.x in 2862..2878 && tile.z in 3529..3541

    /** Run energy per tick: 0.2% a keg (five kegs = 1% a tick, Darkan's drain). */
    fun kegDrain(kegs: Int): Double = 0.2 * kegs

    /** Fall chance per tick: none above 25% run energy, then rising to 10% a tick at 0. */
    fun kegFallChance(runEnergy: Double): Double = ((25.0 - runEnergy) / 250.0).coerceIn(0.0, 0.1)

    /** Every [KEG_REWARD_EVERY] ticks: Strength xp kegs + 1 (Darkan), token progress + kegs (a token per 5). */
    fun kegXp(kegs: Int): Double = (kegs + 1).toDouble()

    const val KEG_TOKEN_PROGRESS = 5

    // ---- K06 N372 part 6: the catapult room (Gamfred). OSRS Wiki "Warriors' Guild" / "Defensive shield": Claim-Shield, the shield
    // only on the target, the defence interface instead of the tabs, the catapult fires every 8 ticks, the right defence = 10 Defence
    // xp + 1 token. Donors Novite/Darkan: catapult 15616 at 2840,3548,1 (anim 4164), shot from 2842,3550 to the target 2842,3541,
    // gfx 679 + type, interface 411 buttons 0-3 (stab, blunt, slash, magic), block anims 4169/4168/4171/4170. A miss hurts (Darkan
    // 10-50 of 990 life points; here 1-5, CUSTOM scale).

    const val CATAPULT = 15616
    val CATAPULT_TILE = gg.rsmod.game.model.Tile(2840, 3548, 1)
    val CATAPULT_FROM = gg.rsmod.game.model.Tile(2842, 3550, 1)
    val CATAPULT_TARGET = gg.rsmod.game.model.Tile(2842, 3541, 1)
    const val CATAPULT_ANIM = 4164
    const val CATAPULT_EVERY = 8
    const val CATAPULT_GFX = 679
    val BLOCK_ANIMS = intArrayOf(4169, 4168, 4171, 4170)
    const val CATAPULT_XP = 10.0
    const val CATAPULT_TOKENS = 1
    const val CATAPULT_MAX_HIT = 5
    const val DEFENCE_INTERFACE = 411
    val DEFENCE_BUTTONS = 0..3

    const val MSG_SHIELD_GIVE = "Gamfred hands you a shield."
    const val MSG_SHIELD_HAVE = "You already have a defensive shield."
    const val MSG_SHIELD_TARGET = "You need to be standing on the target before you can wield the shield."
    const val MSG_DEFLECT = "You deflect the incoming attack."
    const val MSG_DEFLECT_FAIL = "You fail to deflect the incoming attack."

    fun inCatapultRoom(tile: gg.rsmod.game.model.Tile): Boolean = tile.height == 1 && tile.x in 2837..2847 && tile.z in 3538..3552

    /** The defence a shot needs: shot type 0-3 (gfx 679 + type) = button 0-3 on interface 411. */
    fun blocks(style: Int, shot: Int): Boolean = style == shot

    /** The pieces a kill gives back: the whole set, or - 1 in [BREAK_ONE_IN] - all but one ([roll] picks; -1 = nothing breaks). */
    fun returned(set: Set, roll: Int): List<Int> = if (roll < 0) set.pieces else set.pieces.filterIndexed { i, _ -> i != roll }
}
