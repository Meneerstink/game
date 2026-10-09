package gg.rsmod.plugins.api.ext

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.attr.*
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.FREEZE_IMMUNITY_TIMER
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.game.model.timer.STUN_TIMER
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.combat.CombatConfigs

fun Pawn.getCommandArgs(): Array<String> = attr[COMMAND_ARGS_ATTR]!!

fun Pawn.getInteractingSlot(): Int = attr[INTERACTING_SLOT_ATTR]!!

fun Pawn.getInteractingItem(): Item = attr[INTERACTING_ITEM]!!.get()!!

fun Pawn.getInteractingItemId(): Int = attr[INTERACTING_ITEM_ID]!!

fun Pawn.getInteractingGroundItem(): GroundItem = attr[INTERACTING_GROUNDITEM_ATTR]!!.get()!!

fun Pawn.getInteractingButton(): Int = attr[INTERACTING_BUTTON_ID]!!

fun Pawn.getInteractingItemSlot(): Int = attr[INTERACTING_ITEM_SLOT]!!

fun Pawn.getInteractingOtherItemId(): Int = attr[OTHER_ITEM_ID_ATTR]!!

fun Pawn.getInteractingOtherItemSlot(): Int = attr[OTHER_ITEM_SLOT_ATTR]!!

fun Pawn.getInteractingOption(): Int = attr[INTERACTING_OPT_ATTR]!!

fun Pawn.getInteractingOpcode(): Int = attr[INTERACTING_OPCODE_ATTR]!!

fun Pawn.getInteractingGameObj(): GameObject = attr[INTERACTING_OBJ_ATTR]!!.get()!!

fun Pawn.getInteractingNpc(): Npc = attr[INTERACTING_NPC_ATTR]!!.get()!!

fun Pawn.getInteractingPlayer(): Player = attr[INTERACTING_PLAYER_ATTR]!!.get()!!

fun Pawn.hasPrayerIcon(icon: PrayerIcon): Boolean = prayerIcon == icon.id

/**
 * Whether this pawn's current overhead protects against [style].
 *
 * Always prefer this to `hasPrayerIcon(PrayerIcon.PROTECT_FROM_X)`: eight different frames in this
 * cache's `headicons_prayer` sheet block melee, and an exact-id test recognises exactly one of
 * them. See [PrayerIcon.protects] for the full list and for what that cost the player.
 */
fun Pawn.isProtectedFrom(style: CombatClass): Boolean = PrayerIcon.protectsAgainst(prayerIcon, style)

/**
 * Whether this pawn's current overhead is a Summoning protection - normal-book Protect from
 * Summoning or curse-book Deflect Summoning, including every combined frame. This is what makes a
 * familiar's damage bounce; see [PrayerIcon.protectsSummoning].
 */
fun Pawn.isProtectedFromSummoning(): Boolean = PrayerIcon.protectsAgainstSummoning(prayerIcon)

fun Pawn.getBonus(slot: BonusSlot): Int = equipmentBonuses[slot.id]

fun Pawn.getAddedFriend(): String = attr[ADDED_FRIEND]!!

fun Pawn.getDeletedFriend(): String = attr[DELETED_FRIEND]!!

fun Pawn.getAddedIgnore(): String = attr[ADDED_IGNORE]!!

fun Pawn.getDeletedIgnore(): String = attr[DELETED_IGNORE]!!

fun Pawn.hit(
    damage: Int,
    type: HitType = if (damage == 0) HitType.BLOCK else HitType.REGULAR_HIT,
    delay: Int = 0,
    /** Who dealt it, when a pawn did (OSRS hitsplats: the source sees its own hit lit, not tinted). */
    source: Pawn? = null,
): Hit {
    val hit =
        Hit
            .Builder()
            .setDamageDelay(delay)
            .addHit(damage = damage, type = type.id, group = type.group)
            .build()
    hit.source = source
    // Kill credit for a pawn's damage outside dealHit (Ancient godsword sacrifice, revenant bleed, Karil's second hit - block 3
    // audit 2026-10-09), the way Deflect, Ring of recoil and Vengeance credit theirs: damage map and Deadman breach record.
    if (source != null && damage > 0) {
        damageMap.add(source, damage)
        gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.recordDamage(this, source, damage)
    }

    addHit(hit)
    return hit
}

fun Pawn.hit(
    damage: Int,
    type: Int,
    delay: Int = 0,
): Hit {
    var hit =
        // If player has spear wall active and it's a melee hit, we block it
    if (this is Player && attr.has(SPEAR_WALL) && type == HitType.MELEE.id) {
        Hit.Builder()
            .setDamageDelay(delay)
            .addHit(damage = 0, type = HitType.BLOCK.id, group = HitType.BLOCK.group)
            .build()
    }
    else {
        Hit.Builder()
            .setDamageDelay(delay)
            .addHit(damage = damage, type = type, group = HitType.groupOf(type))
            .build()
    }

    addHit(hit)
    return hit
}

// Sourced from the OSRS wiki's "Freeze" article — see FREEZE_IMMUNITY_TIMER's doc comment in
// Timers.kt for the full sourcing note.
const val FREEZE_IMMUNITY_TICKS = 5

/**
 * Npc ids with 100 % freeze resistance (OSRS Wiki infobox "freezeresistance = 100", e.g. the Deadman breach Durial321, Magic Mark,
 * Pestilent Bloat and Bee Swarm - breach conformance 2026-09-27); registered by the content that owns them. They are never frozen.
 */
val FREEZE_RESISTANT_NPCS: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()

fun Pawn.freeze(
    cycles: Int,
    source: Pawn? = null,
    onFreeze: () -> Unit,
): Boolean {
    if (timers.has(FROZEN_TIMER) || timers.has(FREEZE_IMMUNITY_TIMER)) {
        return false
    }
    if (this is Npc && id in FREEZE_RESISTANT_NPCS) {
        gg.rsmod.game.model.BreachTrace.log("freeze.resisted") { "npc=$id#$index cycles=$cycles" }
        return false
    }
    stopMovement()
    timers[FROZEN_TIMER] = cycles
    // Set to outlast the freeze itself by FREEZE_IMMUNITY_TICKS, so once FROZEN_TIMER expires
    // this timer still has FREEZE_IMMUNITY_TICKS left — reproducing "X ticks of immunity after
    // the freeze ends" with a single countdown and no separate on-expiry driver.
    timers[FREEZE_IMMUNITY_TIMER] = cycles + FREEZE_IMMUNITY_TICKS
    // A player's freeze on a player only holds while both stay within 10 tiles (FreezeLeash, OSRS Wiki "Freeze").
    gg.rsmod.plugins.content.mechanics.freeze.FreezeLeash.start(this, source)
    onFreeze()
    return true
}

fun Pawn.freeze(
    cycles: Int,
    source: Pawn? = null,
) {
    freeze(cycles, source) {
        if (this is Player) {
            this.message("You have been frozen.")
        }
    }
}

/** The stun timer plus one tick: while it runs the pawn cannot be stunned again ([stun]). */
val STUN_IMMUNITY_TIMER = gg.rsmod.game.model.timer.TimerKey(resetOnDeath = true)

/** Not stunned and not in the one-tick immunity after a stun. */
fun Pawn.canBeStunned(): Boolean = !timers.has(STUN_TIMER) && !timers.has(STUN_IMMUNITY_TIMER)

fun Pawn.stun(
    cycles: Int,
    onStun: () -> Unit,
): Boolean {
    if (!canBeStunned()) {
        return false
    }
    stopMovement()
    timers[STUN_TIMER] = cycles
    // OSRS Wiki "Dragon spear": "a one tick (0.6 seconds) period of immunity after a stun wears off" (PvP run 2026-10-03).
    timers[STUN_IMMUNITY_TIMER] = cycles + 1
    onStun()
    return true
}

fun Pawn.stun(cycles: Int) {
    stun(cycles) {
        if (this is Player) {
            val blockAnimation = CombatConfigs.getBlockAnimation(this)
            animate(blockAnimation)
            graphic(Gfx.STUNNED_YELLOW_BIRDS, 124)
            playSound(Sfx.STUNNED, delay = 20)
            resetInteractions()
            interruptQueues()
            message("You have been stunned!")
        }
    }
}
