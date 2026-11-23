package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.plugins.api.ext.message
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.ext.persistNow
import gg.rsmod.plugins.content.mechanics.pvp.PvpSkull

/**
 * PROJECT48 SIGILS - the state and the rules (OSRS Wiki Deadman: Armageddon/Sigils):
 *  - at most [MAX_ATTUNED] sigils are attuned (= active) at any one time;
 *  - a sigil is attuned and unattuned only at a bank, never while skulled; attuning swaps the unattuned item for the attuned item in
 *    the same inventory slot, unattuning swaps it back;
 *  - an attuned sigil is untradeable and always kept on death ([isAttunedItem]); an unattuned sigil is tradeable on the Grand Exchange.
 * Every effect asks [has] (or [effective], which also applies the "does not stack" rules): one cheap check per hook.
 */
object Sigils {
    const val MAX_ATTUNED = 3

    /** Set once the player took the starting sigil (S-K5 a). */
    val START_CHOSEN = AttributeKey<Boolean>(persistenceKey = "sigil_start_chosen")

    /** The login timer that opens the starting-sigil choice at the first quiet moment ([quietForStartChoice]). */
    val START_PROMPT_TIMER = gg.rsmod.game.model.timer.TimerKey()
    const val START_PROMPT_FIRST_TICKS = 8
    const val START_PROMPT_RETRY_TICKS = 10

    /**
     * Whether the starting-sigil choice may open now. It is a chat choice that takes the player's attention, so it never opens over
     * the new-player introduction (its camera tour is a dialogue of its own), while the player is locked, dead, in a fight, has a
     * window open, or stands in a dangerous (PvP-enabled) area - a player who logs in there is asked once he is in a guarded zone.
     */
    fun quietForStartChoice(player: Player): Boolean =
        !gg.rsmod.plugins.content.quests.foundation.TutorIntro.isActive(player) &&
            !player.isLocked() &&
            !player.isDead() &&
            !gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.inCombat(player) &&
            player.interfaces.getModal() == -1 &&
            !gg.rsmod.plugins.content.mechanics.pvp.AreaState.isDangerous(player.tile)

    /** The three Tier 2 starting sigils of Deadman: Armageddon (wiki Sigils, 'Starting sigils'). */
    val START_CHOICES: List<Sigil> = listOf(Sigil.THE_RUTHLESS_RANGER, Sigil.THE_FORMIDABLE_FIGHTER, Sigil.THE_MENACING_MAGE)

    /** S04 Sigil of Exaggeration: the non-combat skills (Cooking .. Construction) and the boost they hold. */
    val EXAGGERATED_SKILLS: IntRange = 7..22
    const val EXAGGERATION_BOOST = 5

    /** The attuned sigils of a player: sigil numbers, comma separated (persisted). */
    val ATTUNED = AttributeKey<String>(persistenceKey = "p48_sigils_attuned")

    /** Epoch second until which an activatable sigil is cooling down (Int seconds: the player save reads numbers back as Int). */
    val COOLDOWN_UNTIL: Map<Sigil, AttributeKey<Int>> =
        Sigil.values().filter { it.activatable }.associateWith { AttributeKey<Int>(persistenceKey = "p48_sigil_cd_${it.number}") }

    /** Epoch second until which an activated sigil's window lasts. */
    val ACTIVE_UNTIL: Map<Sigil, AttributeKey<Int>> =
        Sigil.values().filter { it.activatable }.associateWith { AttributeKey<Int>(persistenceKey = "p48_sigil_on_${it.number}") }

    /** Sigils that cannot be attuned together ("cannot be used in conjunction with ..."). */
    private val EXCLUSIVE_GROUPS: List<Set<Sigil>> = listOf(setOf(Sigil.ARCANE_SWIFTNESS, Sigil.THE_GUNSLINGER, Sigil.THE_SWASHBUCKLER))

    /** "Does not stack": when both are attuned only the first one (the stronger) works. */
    private val OVERRIDES: List<Pair<Sigil, Sigil>> =
        listOf(
            Sigil.ENHANCED_HARVEST to Sigil.THE_FORAGER,
            Sigil.TITANIUM to Sigil.RESISTANCE,
            Sigil.THE_RAMPART to Sigil.FORTIFICATION,
        )

    enum class Result {
        OK,
        NOT_A_SIGIL,
        REMOVED,
        NOT_AT_BANK,
        SKULLED,
        FULL,
        EXCLUSIVE,
        ALREADY,
        NOT_ATTUNED,
        ITEM_CHANGED,
    }

    private fun nowSeconds(): Int = (System.currentTimeMillis() / 1000L).toInt()

    fun attuned(player: Player): List<Sigil> {
        val numbers = player.attr[ATTUNED]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return emptyList()
        return Sigil.available.filter { it.number in numbers }
    }

    private fun store(
        player: Player,
        list: List<Sigil>,
    ) {
        player.attr[ATTUNED] = list.joinToString(",") { it.number.toString() }
    }

    /** Whether [sigil] is attuned (the plain check; see [effective] for the stacking rules). */
    fun has(
        player: Player,
        sigil: Sigil,
    ): Boolean = attuned(player).contains(sigil)

    /** Whether [sigil] works now: attuned and not overridden by a stronger sigil that does not stack with it. */
    fun effective(
        player: Player,
        sigil: Sigil,
    ): Boolean {
        val on = attuned(player)
        if (sigil !in on) return false
        return OVERRIDES.none { (strong, weak) -> weak == sigil && strong in on }
    }

    fun isAttunedItem(itemId: Int): Boolean = Sigil.ofAttuned(itemId) != null

    /** Any sigil item, attuned or not: wiki "they cannot be traded directly from player to player" (the Grand Exchange is the only market). */
    fun isSigilItem(itemId: Int): Boolean = Sigil.ofAttuned(itemId) != null || Sigil.ofUnattuned(itemId) != null

    fun isUnattunedItem(itemId: Int): Boolean = Sigil.ofUnattuned(itemId) != null

    /** Attunes the unattuned sigil in inventory [slot]. [atBank] = the player stands at / is using a bank (the caller decides). */
    fun attune(
        player: Player,
        slot: Int,
        atBank: Boolean,
    ): Result {
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        val sigil = Sigil.ofUnattuned(item.id) ?: return Result.NOT_A_SIGIL
        if (!sigil.enabled) return Result.REMOVED
        if (!atBank) return Result.NOT_AT_BANK
        if (PvpSkull.hasSkullTimer(player)) return Result.SKULLED
        val on = attuned(player)
        if (sigil in on) return Result.ALREADY
        if (on.size >= MAX_ATTUNED) return Result.FULL
        if (EXCLUSIVE_GROUPS.any { sigil in it && on.any { other -> other in it } }) return Result.EXCLUSIVE
        player.inventory[slot] = Item(sigil.attunedId)
        store(player, on + sigil)
        onChanged(player, sigil, attuned = true)
        SigilFx.attuned(player, sigil)
        SigilWornSlot.refresh(player)
        player.persistNow()
        return Result.OK
    }

    /** Unattunes the attuned sigil in inventory [slot]. */
    /**
     * An attuned sigil used on a bank: the cache items of many sigils have no "Unattune" option (only "Destroy"), so this is the way to
     * un-attune them. Returns true when [item] was an attuned sigil (the answer is already told to the player).
     */
    fun useOnBank(
        player: Player,
        slot: Int,
    ): Boolean {
        val item = player.inventory[slot] ?: return false
        if (Sigil.ofItem(item.id)?.enabled == false) {
            player.message(REMOVED_MESSAGE)
            return true
        }
        // The cache item of the Sigil of Mobility has no "Attune" option at all, so an un-attuned sigil used on a bank attunes it.
        if (Sigil.ofUnattuned(item.id) != null) {
            when (attune(player, slot, atBank = true)) {
                Result.REMOVED -> player.message(REMOVED_MESSAGE)
                Result.OK -> player.message("You feel a surge of power as you attune to the Sigil.")
                Result.SKULLED -> player.message("You cannot attune a sigil while skulled.")
                Result.FULL -> player.message("You already have attuned to $MAX_ATTUNED Sigils. You must unattune one before attuning to more.")
                Result.EXCLUSIVE -> player.message("This sigil cannot be used in conjunction with the sigil you already have attuned.")
                Result.ALREADY -> player.message("You already have this sigil attuned.")
                else -> player.message("Nothing interesting happens.")
            }
            return true
        }
        if (Sigil.ofAttuned(item.id) == null) return false
        when (unattune(player, slot, atBank = true)) {
            Result.REMOVED -> player.message(REMOVED_MESSAGE)
            Result.OK -> player.message("You unattune the Sigil. Its powers leave you for now.")
            Result.SKULLED -> player.message("You cannot unattune a sigil while skulled.")
            else -> player.message("Nothing interesting happens.")
        }
        return true
    }

    fun unattune(
        player: Player,
        slot: Int,
        atBank: Boolean,
    ): Result {
        if (slot !in 0 until player.inventory.capacity) return Result.ITEM_CHANGED
        val item = player.inventory[slot] ?: return Result.ITEM_CHANGED
        val sigil = Sigil.ofAttuned(item.id) ?: return Result.NOT_A_SIGIL
        if (!sigil.enabled) return Result.REMOVED
        if (!atBank) return Result.NOT_AT_BANK
        if (PvpSkull.hasSkullTimer(player)) return Result.SKULLED
        val on = attuned(player)
        player.inventory[slot] = Item(sigil.unattunedId)
        store(player, on - sigil)
        onChanged(player, sigil, attuned = false)
        SigilFx.unattuned(player)
        SigilWornSlot.refresh(player)
        player.persistNow()
        return Result.OK
    }

    /** The immediate effect of attuning or unattuning (the rest is read by the hooks): Exaggeration lifts / drops the +5 boost at once. */
    private fun onChanged(
        player: Player,
        sigil: Sigil,
        attuned: Boolean,
    ) {
        if (sigil != Sigil.EXAGGERATION) return
        for (skill in EXAGGERATED_SKILLS) {
            val max = player.skills.getMaxLevel(skill)
            if (attuned) {
                player.skills.alterCurrentLevel(skill, EXAGGERATION_BOOST, EXAGGERATION_BOOST)
            } else if (player.skills.getCurrentLevel(skill) > max) {
                player.skills.setCurrentLevel(skill, max)
            }
        }
    }

    /**
     * A sigil used up by its own effect (S65 Guardian Angel on a PvP death): the attuned item leaves the inventory (or the bank) and the
     * sigil leaves the attuned list, so its slot is free again. False when [sigil] was not attuned (nothing changes).
     */
    fun consume(
        player: Player,
        sigil: Sigil,
    ): Boolean {
        val on = attuned(player)
        if (sigil !in on) return false
        if (player.inventory.remove(sigil.attunedId).hasFailed()) player.bank.remove(sigil.attunedId)
        store(player, on - sigil)
        onChanged(player, sigil, attuned = false)
        SigilWornSlot.refresh(player)
        return true
    }

    /** Owner 23-11: retired items are inert; compensation is still the owner's decision. */
    const val REMOVED_MESSAGE = "This sigil has been removed and no longer grants any effect or uses an attunement slot."

    /** OSRS Wiki, infobox of every attuned sigil item: the answer to its Destroy option. */
    const val DESTROY_ATTUNED_MESSAGE = "You must unattune the Sigil before you can destroy it."

    /**
     * Repairs a player whose attuned list names a sigil whose attuned item is gone from both the inventory and the bank. Without the
     * item that sigil can never be unattuned again, so its effect would stay forever and its slot would never come free. The list
     * follows the item: the lost sigils are dropped from it. Returns them (the caller tells the player).
     * An attuned item cannot normally be lost (untradeable, always kept on death, its Destroy option refuses), so this only fires for
     * an account that was already broken or an item removed by staff.
     */
    fun reconcile(player: Player): List<Sigil> {
        // Free retired attunement slots without deleting or exchanging the legacy items.
        val saved = player.attr[ATTUNED]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        val retired = Sigil.values().filter { !it.enabled && it.number in saved }
        val on = attuned(player)
        if (retired.isNotEmpty()) {
            store(player, on)
            player.message(REMOVED_MESSAGE)
        }
        if (on.isEmpty()) return emptyList()
        val lost = on.filter { !player.inventory.contains(it.attunedId) && !player.bank.contains(it.attunedId) }
        if (lost.isEmpty()) return emptyList()
        store(player, on - lost.toSet())
        lost.forEach { onChanged(player, it, attuned = false) }
        SigilWornSlot.refresh(player)
        return lost
    }

    /** Seconds left of the cooldown of an activatable [sigil] (0 = ready). */
    fun cooldownLeft(
        player: Player,
        sigil: Sigil,
    ): Int {
        val key = COOLDOWN_UNTIL[sigil] ?: return 0
        return ((player.attr[key] ?: 0) - nowSeconds()).coerceAtLeast(0)
    }

    /** Seconds left of an activation window of [sigil] (0 = not active). */
    fun windowLeft(
        player: Player,
        sigil: Sigil,
    ): Int {
        val key = ACTIVE_UNTIL[sigil] ?: return 0
        return ((player.attr[key] ?: 0) - nowSeconds()).coerceAtLeast(0)
    }

    /** Whether the activation window of [sigil] is open (the sigil is attuned and was activated within its window). */
    fun windowOpen(
        player: Player,
        sigil: Sigil,
    ): Boolean = effective(player, sigil) && windowLeft(player, sigil) > 0

    /** Closes the window of [sigil] early (a one-shot effect that was used up). */
    fun consumeWindow(
        player: Player,
        sigil: Sigil,
    ) {
        if (windowLeft(player, sigil) > 0) SigilFx.windowEnded(player, sigil)
        ACTIVE_UNTIL[sigil]?.let { player.attr.remove(it) }
    }

    /**
     * Activates [sigil]: checks that it is attuned and ready, starts the cooldown and the window. Returns false (with a message)
     * when it cannot be activated; the caller then runs the instant effect of sigils with window 0.
     */
    fun activate(
        player: Player,
        sigil: Sigil,
    ): Boolean {
        if (!sigil.activatable) return false
        if (!effective(player, sigil)) {
            player.message("This sigil is not attuned.")
            return false
        }
        val left = cooldownLeft(player, sigil)
        if (left > 0) {
            player.message("The ${sigil.label} will be ready in $left seconds.")
            return false
        }
        val now = nowSeconds()
        player.attr[COOLDOWN_UNTIL.getValue(sigil)] = now + sigil.cooldownSeconds
        if (sigil.windowSeconds > 0) player.attr[ACTIVE_UNTIL.getValue(sigil)] = now + sigil.windowSeconds
        return true
    }

    /** The chat lines of the overview (item option Check, command ::sigils). */
    fun overview(player: Player): List<String> {
        val on = attuned(player)
        if (on.isEmpty()) return listOf("You have no attuned sigils (max $MAX_ATTUNED). Attune a sigil at a bank.")
        return on.map { sigil ->
            val state =
                when {
                    !sigil.activatable -> "active"
                    cooldownLeft(player, sigil) > 0 -> "ready in ${cooldownLeft(player, sigil)} s"
                    else -> "ready"
                }
            "${sigil.label}: $state"
        }
    }
}
