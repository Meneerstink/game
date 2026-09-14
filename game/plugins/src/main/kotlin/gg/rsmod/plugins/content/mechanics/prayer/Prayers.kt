package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.bits.INFINITE_VARS_STORAGE
import gg.rsmod.game.model.bits.InfiniteVarsType
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*

object Prayers {
    private val PRAYER_DRAIN_COUNTER = AttributeKey<Int>()

    val PRAYER_DRAIN = TimerKey(removeOnZero = false)
    private val DISABLE_OVERHEADS = TimerKey()

    private const val DEACTIVATE_PRAYER_SOUND = Sfx.CANCEL_PRAYER

    /**
     * Whether this player currently has the prayer book in quick-prayer *selection* mode. This used
     * to be a single `var` on this object, i.e. one flag shared by every player on the server.
     */
    private val QUICK_PRAYER_SELECT_MODE = AttributeKey<Boolean>()

    private const val PRAYER_POINTS_VARP = 2382
    const val ACTIVE_PRAYERS_VARP = 1395

    /**
     * Varc 181 is the client's quick-prayer *selection-mode* flag, not a set of prayers. Interface
     * 271's component 4 carries `onVarcTransmit` script 1236 with varc trigger 181; 1236 calls
     * client script 1717, which branches on `varc 181 == 1` to decide whether to show component 42
     * (the "Select Quick Prayers" checkbox grid, built by client script 1388) and hide component 0,
     * or the other way around. Client scripts 1237, 1293 and 1704 branch on the same varc.
     *
     * The selected prayers themselves live in one varbit per prayer - see [Prayer.qpVarbit].
     */
    const val QUICK_PRAYER_SELECT_MODE_VARC = 181
    const val QUICK_PRAYERS_ACTIVE_VARC = 182

    /** Slot grid holding the "Activate" prayer buttons; built by client script 1237. */
    private const val ACTIVATE_PRAYERS_COMPONENT = 8

    /** Slot grid holding the "Select"/"Deselect" quick-prayer buttons; built by client script 1388. */
    private const val SELECT_QUICK_PRAYERS_COMPONENT = 42

    private const val PRAYER_BOOK_INTERFACE = 271

    /** `1 shl 1` - enable op 1 only, which is the single op both grids' children are given. */
    private const val OP1_ONLY = 2

    /*
     * `getInteractingOption()` is the index of the received opcode in the IfButtonMessage opcode
     * list of data/packets.yml (`10,64,61,4,52,81,18,25,91,20`), plus one - it is NOT the client's
     * op index. Component 749:1 bakes op1 = "Turn Quick Prayers On" (IF_BUTTON1, opcode 61 -> 3)
     * and op2 = "Select Quick Prayers" (IF_BUTTON2, opcode 64 -> 2).
     */
    const val QUICK_PRAYERS_SELECT_OPTION = 2
    const val QUICK_PRAYERS_TOGGLE_OPTION = 3

    // Unused
    private const val KING_RANSOMS_QUEST_VARBIT = 3909 // Used for chivalry/piety prayer.
    const val RIGOUR_UNLOCK_VARBIT = 5451
    const val AUGURY_UNLOCK_VARBIT = 5452

    enum class StatMod {
        ATTACK,
        STRENGTH,
        DEFENSE,
        RANGE,
        MAGE,
    }

    fun init(player: Player) {
        // player.setvarp(if (curses) 1582 else 1395, 0)
        resetStatMods(player)
        setQuickPrayerSelectMode(player, false)
    }

    private fun isSelectingQuickPrayers(player: Player): Boolean = player.attr.getOrDefault(QUICK_PRAYER_SELECT_MODE, false)

    /**
     * Enters or leaves quick-prayer selection mode. Both halves matter: the varc tells the client
     * which of the two grids to build and show, and the events tell it which of the two grids may
     * actually transmit a click. Neither grid has any baked events of its own - both component 8
     * and component 42 decode as `events=0x000000` - so without the [Player.setEvents] call the
     * client draws the menu entry and then silently drops the click.
     */
    private fun setQuickPrayerSelectMode(
        player: Player,
        selecting: Boolean,
    ) {
        player.attr[QUICK_PRAYER_SELECT_MODE] = selecting
        player.setVarc(QUICK_PRAYER_SELECT_MODE_VARC, if (selecting) 1 else 0)
        unlockPrayerBookButtons(player)
    }

    fun unlockPrayerBookButtons(player: Player) {
        val selecting = isSelectingQuickPrayers(player)
        setPrayerGridEvents(player, ACTIVATE_PRAYERS_COMPONENT, enabled = !selecting)
        setPrayerGridEvents(player, SELECT_QUICK_PRAYERS_COMPONENT, enabled = selecting)
    }

    private fun setPrayerGridEvents(
        player: Player,
        component: Int,
        enabled: Boolean,
    ) {
        player.setEvents(
            interfaceId = PRAYER_BOOK_INTERFACE,
            component = component,
            from = 0,
            to = if (AncientCurses.getBook(player) == AncientCurses.PrayerBook.ANCIENT) AncientCurse.TURMOIL_SLOT else Prayer.values.size - 1,
            setting = if (enabled) OP1_ONLY else 0,
        )
    }

    fun isQuickPrayerSelected(
        player: Player,
        prayer: Prayer,
    ): Boolean = player.getVarbit(prayer.qpVarbit) != 0

    private fun selectedQuickPrayers(player: Player): List<Prayer> = Prayer.values.filter { isQuickPrayerSelected(player, it) }

    /**
     * The prayer stat modifiers backing varbits 6857-6861. These are per-player values, so they
     * cannot live in a single array on this object - one player's modifiers would otherwise be
     * transmitted to every other player's prayer interface.
     */
    private val STAT_MODS = AttributeKey<IntArray>()

    private fun statMods(player: Player): IntArray =
        player.attr[STAT_MODS] ?: IntArray(StatMod.values().size).also { player.attr[STAT_MODS] = it }

    fun decreaseStatModifier(
        player: Player,
        mod: StatMod,
        bonus: Int,
        max: Int,
    ): Boolean {
        val statMods = statMods(player)
        if (statMods[mod.ordinal] > max) {
            statMods[mod.ordinal]--
            updateStatMod(player, mod)
            return true
        }
        return false
    }

    fun increaseStatModifier(
        player: Player,
        mod: StatMod,
        bonus: Int,
        max: Int,
    ): Boolean {
        val statMods = statMods(player)
        if (statMods[mod.ordinal] < max) {
            statMods[mod.ordinal]++
            updateStatMod(player, mod)
            return true
        }
        return false
    }

    private fun resetStatMods(player: Player) {
        StatMod.values().forEach { mod ->
            setStatMod(player, mod, 0)
        }
    }

    private fun getStatMod(
        player: Player,
        mod: StatMod,
    ): Int = statMods(player)[mod.ordinal]

    private fun setStatMod(
        player: Player,
        mod: StatMod,
        bonus: Int,
    ) {
        statMods(player)[mod.ordinal] = bonus
        updateStatMod(player, mod)
    }

    private fun updateStatMod(
        player: Player,
        mod: StatMod,
    ) {
        player.setVarbit(6857 + mod.ordinal, 30 + statMods(player)[mod.ordinal])
    }

    private fun updateStatMods(player: Player) {
        for (m in StatMod.values()) {
            updateStatMod(player, m)
        }
    }

    fun disableOverheads(
        p: Player,
        cycles: Int,
    ) {
        p.timers[DISABLE_OVERHEADS] = cycles
    }

    fun deactivateAll(p: Player) {
        p.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, 0)
        Prayer.values.forEach { prayer ->
            if (isActive(p, prayer)) {
                deactivate(p, prayer)
            }
        }
        p.setVarp(ACTIVE_PRAYERS_VARP, 0)
        p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
        resetStatMods(p)
        p.attr.remove(PROTECT_ITEM_ATTR)
        if (p.prayerIcon != -1) {
            p.prayerIcon = -1
            p.addBlock(UpdateBlockType.APPEARANCE)
        }
    }

    suspend fun toggle(
        it: QueueTask,
        prayer: Prayer,
    ) {
        val p = it.player

        if (p.isDead() || !p.lock.canUsePrayer()) {
            p.syncVarp(ACTIVE_PRAYERS_VARP)
            return
        } else if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                p, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER,
            )
        ) {
            p.syncVarp(ACTIVE_PRAYERS_VARP)
            return
        } else if (!checkRequirements(it, prayer)) {
            return
        } else if (prayer.group == PrayerGroup.OVERHEAD && p.timers.has(DISABLE_OVERHEADS)) {
            p.syncVarp(ACTIVE_PRAYERS_VARP)
            p.message("You cannot use overhead prayers right now.")
            return
        } else if (p.getVarp(PRAYER_POINTS_VARP) == 0) {
            return
        }
        it.terminateAction = { p.syncVarp(ACTIVE_PRAYERS_VARP) }
        while (p.lock.delaysPrayer()) {
            it.wait(1)
        }
        p.syncVarp(ACTIVE_PRAYERS_VARP)
        val active = p.getVarbit(prayer.varbit) != 0
        if (active) {
            deactivate(p, prayer)
        } else {
            activate(p, prayer)
        }
        p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
    }

    fun activate(
        p: Player,
        prayer: Prayer,
    ) {
        if (!isActive(p, prayer)) {
            val others =
                Prayer.values.filter { other -> // TODO PROBABLY REDO for readability
                    val matchingGroup = prayer.group == other.group && prayer.group != PrayerGroup.RESTORATION
                    val matchingPrayer = prayer == other
                    val groupNull = other.group == null
                    !matchingPrayer &&
                        !groupNull &&
                        (matchingGroup || prayer.overlap.contains(other.group))
                }

            others.forEach { other ->
                if (isActive(p, other)) {
                    deactivate(p, other)
                }
            }

            p.setVarbit(
                prayer.varbit,
                1,
            )
            if (prayer.sound != -1 && AncientCurses.getBook(p) != AncientCurses.PrayerBook.ANCIENT) {
                p.playSound(prayer.sound)
            }

            setOverhead(p)
            if (prayer == Prayer.PROTECT_ITEM) {
                p.attr[PROTECT_ITEM_ATTR] = true
                if (AncientCurses.getBook(p) == AncientCurses.PrayerBook.ANCIENT) p.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, 1)
            }
        }
    }

    fun deactivate(
        p: Player,
        prayer: Prayer,
    ) {
        if (isActive(p, prayer)) {
            p.setVarbit(prayer.varbit, 0)
            p.playSound(DEACTIVATE_PRAYER_SOUND)
            setOverhead(p)

            if (prayer == Prayer.PROTECT_ITEM) {
                p.attr[PROTECT_ITEM_ATTR] = false
                p.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, 0)
            }
        }
    }

    private fun getDrainResistance(p: Player): Int {
        return 60 + (p.getPrayerBonus() * 2)
    }

    fun drainPrayer(p: Player) {
        // The curse book keeps its active state in its own varps, so `ACTIVE_PRAYERS_VARP == 0` is
        // not "nothing is on" for a player on the ancient book - gate on the real total instead.
        val drainRate = calculateDrainRate(p)
        if (p.isDead() || drainRate == 0 || p.hasStorageBit(INFINITE_VARS_STORAGE, InfiniteVarsType.PRAY)) {
            p.attr.remove(PRAYER_DRAIN_COUNTER)
            return
        }
        // Prayer points are now stored/displayed 1:1. The source drain counter still advances in
        // tenths-of-a-point units, so one whole point must consume ten resistance thresholds.
        // This preserves the sourced drain cadence while removing the old 990-point storage unit.
        val drainResistance = getDrainResistance(p) * 10
        var prayerDrainCounter = p.attr.getOrDefault(PRAYER_DRAIN_COUNTER, 0) + drainRate
        while (prayerDrainCounter >= drainResistance) {
            p.decreasePrayerPoints(1)
            prayerDrainCounter -= drainResistance
        }
        p.attr.put(PRAYER_DRAIN_COUNTER, prayerDrainCounter)

        if (p.getVarp(PRAYER_POINTS_VARP) == 0) {
            deactivateAll(p)
            AncientCurses.deactivateAllCurses(p)
            p.message("You have run out of prayer points, you can recharge at an altar.")
        }
    }

    fun selectQuickPrayer(
        it: Plugin,
        prayer: Prayer,
    ) {
        val player = it.player

        if (player.isDead() || !player.lock.canUsePrayer()) {
            player.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
            return
        }

        val enabled = isQuickPrayerSelected(player, prayer)

        it.player.queue {
            if (enabled) {
                player.setVarbit(prayer.qpVarbit, 0)
                return@queue
            }
            if (!checkRequirements(this, prayer)) {
                /*
                 * Client script 1388 optimistically ticks the box on click; re-transmitting the
                 * varbit restores the real state through its onVarTransmit hook (script 2291).
                 */
                player.setVarbit(prayer.qpVarbit, 0)
                return@queue
            }
            /*
             * Same mutual-exclusion rule the live activation path uses: a quick-prayer set may not
             * contain two prayers that could never be active at the same time.
             */
            Prayer.values
                .filter { other ->
                    prayer != other &&
                        other.group != null &&
                        (prayer.group == other.group || prayer.overlap.contains(other.group))
                }.forEach { other ->
                    if (isQuickPrayerSelected(player, other)) {
                        player.setVarbit(other.qpVarbit, 0)
                    }
                }
            player.setVarbit(prayer.qpVarbit, 1)
        }
    }

    fun toggleQuickPrayers(
        p: Player,
        option: Int,
    ) {
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                p, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER,
            )
        ) {
            return
        }
        if (p.isDead() || !p.lock.canUsePrayer()) {
            p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
            return
        }

        if (option == QUICK_PRAYERS_TOGGLE_OPTION) {
            if (AncientCurses.getBook(p) == AncientCurses.PrayerBook.ANCIENT) {
                AncientCurses.toggleQuickCurses(p)
                return
            }
            val quickPrayers = selectedQuickPrayers(p)
            val active = Prayer.values.filter { isActive(p, it) }
            when {
                quickPrayers.isEmpty() -> {
                    p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
                    p.message("You haven't selected any quick-prayers.")
                }
                p.skills.getCurrentLevel(Skills.PRAYER) <= 0 -> {
                    p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
                    p.message("You have run out of prayer points, you can recharge at an altar.")
                }
                active.toSet() == quickPrayers.toSet() -> {
                    /*
                     * All active prayers are quick-prayers - so we turn them off.
                     */
                    p.setVarp(ACTIVE_PRAYERS_VARP, 0)
                    p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 0)
                    setOverhead(p)
                }
                else -> {
                    /*
                     * Every active-prayer varbit lives in varp 1395, so clearing the varp clears
                     * them all in one transmission before the quick set is applied on top.
                     */
                    p.setVarp(ACTIVE_PRAYERS_VARP, 0)
                    quickPrayers.forEach { p.setVarbit(it.varbit, 1) }
                    p.setVarc(QUICK_PRAYERS_ACTIVE_VARC, 1)
                    setOverhead(p)
                }
            }
        } else if (option == QUICK_PRAYERS_SELECT_OPTION) {
            setQuickPrayerSelectMode(p, !isSelectingQuickPrayers(p))
            p.focusTab(Tabs.PRAYER)
        }
    }

    /** Leaves selection mode, e.g. from 271:43 "Confirm Selection". */
    fun confirmQuickPrayerSelection(player: Player) {
        setQuickPrayerSelectMode(player, false)
    }

    fun isActive(
        p: Player,
        prayer: Prayer,
    ): Boolean = p.getVarbit(prayer.varbit) != 0

    fun rechargePrayerPoints(player: Player) {
        player.skills.alterCurrentLevel(Skills.PRAYER, player.skills.getMaxLevel(Skills.PRAYER))
        player.setCurrentPrayerPoints(player.skills.getMaxLevel(Skills.PRAYER))
    }

    private suspend fun checkRequirements(
        it: QueueTask,
        prayer: Prayer,
    ): Boolean {
        val p = it.player

        if (p.skills.getMaxLevel(Skills.PRAYER) < prayer.level) {
            p.syncVarp(ACTIVE_PRAYERS_VARP)
            it.messageBox(
                "You need a <col=000080>Prayer</col> level of ${prayer.level} to use <col=000080>${prayer.named}.",
            )
            return false
        }

        // TODO: Add requirement back after adding King's Ransom quest.
        /**
         if (prayer == Prayer.CHIVALRY && p.getVarbit(KING_RANSOMS_QUEST_VARBIT) < 8) {
         p.syncVarp(ACTIVE_PRAYERS_VARP)
         it.messageBox("You have not unlocked this prayer.")
         return false
         }

         if (prayer == Prayer.PIETY && p.getVarbit(KING_RANSOMS_QUEST_VARBIT) < 8) {
         p.syncVarp(ACTIVE_PRAYERS_VARP)
         it.messageBox("You have not unlocked this prayer.")
         return false
         }

         if (prayer == Prayer.RIGOUR && p.getVarbit(RIGOUR_UNLOCK_VARBIT) == 0) {
         p.syncVarp(ACTIVE_PRAYERS_VARP)
         it.messageBox("You have not unlocked this prayer.")
         return false
         }

         if (prayer == Prayer.AUGURY && p.getVarbit(AUGURY_UNLOCK_VARBIT) == 0) {
         p.syncVarp(ACTIVE_PRAYERS_VARP)
         it.messageBox("You have not unlocked this prayer.")
         return false
         }
         **/

        return true
    }

    /**
     * 2026-09-06: Protect from Summoning is not exclusive with the three combat protections, so
     * the old top-of-the-chain `PROTECT_FROM_SUMMONING` branch silently hid whichever combat
     * protection was also on. The decoded `headicons_prayer` sheet has a dedicated combined frame
     * for each of those pairings (8/9/10 - see [PrayerIcon]), which is what real RS renders.
     */
    private fun setOverhead(p: Player) {
        val combat =
            when {
                isActive(p, Prayer.PROTECT_FROM_MELEE) -> PrayerIcon.PROTECT_FROM_MELEE
                isActive(p, Prayer.PROTECT_FROM_MISSILES) -> PrayerIcon.PROTECT_FROM_MISSILES
                isActive(p, Prayer.PROTECT_FROM_MAGIC) -> PrayerIcon.PROTECT_FROM_MAGIC
                isActive(p, Prayer.RETRIBUTION) -> PrayerIcon.RETRIBUTION
                isActive(p, Prayer.SMITE) -> PrayerIcon.SMITE
                isActive(p, Prayer.REDEMPTION) -> PrayerIcon.REDEMPTION
                else -> PrayerIcon.NONE
            }
        val icon = PrayerIcon.combined(isActive(p, Prayer.PROTECT_FROM_SUMMONING), combat) ?: PrayerIcon.NONE

        if (p.prayerIcon != icon.id) {
            p.prayerIcon = icon.id
            p.addBlock(UpdateBlockType.APPEARANCE)
        }
    }

    /**
     * 2026-09-06: Ancient Curses now drain through this same counter instead of each running its
     * own `world.queue { while(...) wait(n); decreasePrayerPoints(...) }` loop.
     *
     * The old bespoke loops were the cause of the "Prayer/Curse drain remains FAR too fast" retest
     * report. `AncientCurse.secondsPerPoint` held values like 0.24 for the Saps, which the loop
     * read as "one whole Prayer point every 0.24 seconds" - about 4 points a second, draining a
     * level-99 account in roughly 25 seconds - and Turmoil's own loop spent 15 points every 3
     * seconds. Those numbers are the source's rate divided by ten; the corroboration is that the
     * same source's Deflect value converts to exactly 3.0 seconds per point, which is precisely
     * what a Protect prayer (`drainEffect = 120`) costs in this table.
     *
     * Routing everything through one counter also means curses finally respect prayer bonus, and
     * that stacking a curse with Protect Item costs what stacking two prayers costs.
     */
    private fun calculateDrainRate(p: Player): Int {
        var rate = Prayer.values.filter { isActive(p, it) }.sumOf { it.drainEffect }
        rate += AncientCurses.activeCurseDrainEffect(p)
        return rate
    }
}
