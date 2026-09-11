package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.inter.attack.AttackTab

/**
 * Ancient Curses - the revision-appropriate 2011 book (PROJECT_PLAN SS1/SS22).
 *
 * Player-facing contract (cache-proven, see [AncientCurse] KDoc): the prayer tab (interface 271)
 * renders the curse grid when varbit [AncientCurse.BOOK_VARBIT] is 1, each slot lights up from
 * varbit 6820+slot, and a click arrives on component 8 with the book-slot index, routed here by
 * `prayers.plugin.kts` via [onBookButton]. The `::curse` commands remain as admin/diagnostic tools.
 *
 * Mechanics are version-locked to 2011 (wiki.darkan.org/Ancient_Curses):
 *  * Sap: drains the target's stats by 10% rising to 20% over time; Leech: drains 10% rising to
 *    25% and boosts the caster 5% rising to 10%. Each activation on a landed hit adds one
 *    percentage point until the cap ([AncientCurse.activationChancePercent] per curse - PROVEN
 *    from Novite's rev-667 `Player.java` `handleIngoingHit`, replacing this file's former single
 *    shared 25% guess; see that field's KDoc).
 *  * Turmoil: Attack +15% + 15% of the target's Attack level, Defence +15% + 15% of the target's
 *    Defence, Strength +23% + 10% of the target's Strength (target level contribution capped at
 *    [TURMOIL_TARGET_LEVEL_CAP], a provisional balance guard for very high NPC levels).
 *  * Soul Split heals 20% of the hit and drains a player target's prayer by 20% of the hit;
 *    Wrath deals up to 300% of the Prayer level in a 5x5 area on the wearer's death (PROVEN from
 *    the same Novite source - `Utils.getRandom(level * 3)` - this file previously had 250%, which
 *    was a bug, not a sourced value); Deflects block like Protect prayers and reflect 10% on ~63%
 *    of hits; Berserker extends boosts by 15% (in `stat_restoration.plugin.kts`).
 */
object AncientCurses {
    private val TURMOIL_ACTIVE_ATTR = AttributeKey<Boolean>()

    /**
     * Stands in for the real "Temple at Senntisten" unlock quest (PROJECT_PLAN SS18/SS7 confirms an
     * unlock miniquest is expected for Ancient Curses) - a one-off ritual (`::curse unlock`).
     */
    val UNLOCKED_ATTR = AttributeKey<Boolean>(persistenceKey = "ancient_curses_unlocked")
    private const val UNLOCK_COST = 50_000
    const val TURMOIL_LEVEL = 95

    const val SAP_BASE_PCT = 10
    const val SAP_CAP_PCT = 20
    const val LEECH_DRAIN_BASE_PCT = 10
    const val LEECH_DRAIN_CAP_PCT = 25
    const val LEECH_BOOST_BASE_PCT = 5
    const val LEECH_BOOST_CAP_PCT = 10
    const val TURMOIL_TARGET_LEVEL_CAP = 99

    /**
     * Toggle sounds shared by every curse (mirrors [Prayers]' own per-toggle sound pattern).
     * `CURSE_ALL`/`CURSE_LIFT`/`CURSE_CAST_AND_FIRE`/`CURSE_HIT` are real, 667-cache-named sound
     * track ids from the generated [Sfx] table (curse book activation, deactivation, an
     * activation firing on a landed hit, and the effect landing on the target respectively) - the
     * ids themselves are cache-proven; which curse-book *event* each one accompanies is this
     * batch's own inference from the track names (there is no decoded client script proving the
     * exact trigger), so the audible mapping stays PENDING_HUMAN_RETEST like every other audio
     * claim in this file.
     */
    private const val CURSE_ACTIVATE_SOUND = Sfx.CURSE_ALL
    private const val CURSE_DEACTIVATE_SOUND = Sfx.CURSE_LIFT
    private const val CURSE_CAST_SOUND = Sfx.CURSE_CAST_AND_FIRE
    private const val CURSE_LAND_SOUND = Sfx.CURSE_HIT

    /** Per-target escalation state: skill -> drain percentage currently applied by curses. */
    private val CURSE_DRAIN_PCT_ATTR = AttributeKey<MutableMap<Int, Int>>()

    /** Per-caster escalation state: skill -> self-boost percentage currently applied by Leeches. */
    private val LEECH_BOOST_PCT_ATTR = AttributeKey<MutableMap<Int, Int>>()

    fun unlock(player: Player) {
        if (player.attr[UNLOCKED_ATTR] == true) {
            player.filterableMessage("You have already performed the ritual.")
            return
        }
        if (!player.inventory.remove(Items.COINS_995, UNLOCK_COST).hasSucceeded()) {
            player.filterableMessage("You need $UNLOCK_COST coins to perform the ritual.")
            return
        }
        player.attr[UNLOCKED_ATTR] = true
        player.filterableMessage("You perform the ritual and feel the ancients' power. Ancient Curses unlocked.")
    }

    // ---- Turmoil ------------------------------------------------------------------------------

    fun isTurmoilActive(player: Player): Boolean = player.attr[TURMOIL_ACTIVE_ATTR] == true

    fun toggleTurmoil(player: Player) {
        if (isTurmoilActive(player)) {
            setTurmoil(player, false)
            player.playSound(CURSE_DEACTIVATE_SOUND)
            player.filterableMessage("You deactivate Turmoil.")
            return
        }
        if (getBook(player) != PrayerBook.ANCIENT) {
            player.filterableMessage("You must switch to the ancient prayer book first.")
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - see ::curse unlock.")
            return
        }
        if (player.skills.getMaxLevel(Skills.PRAYER) < TURMOIL_LEVEL) {
            player.filterableMessage("You need a Prayer level of $TURMOIL_LEVEL to use Turmoil.")
            return
        }
        if (player.getCurrentPrayerPoints() <= 0) {
            player.filterableMessage("You don't have enough Prayer points left.")
            return
        }
        // Turmoil never runs together with a Sap or Leech.
        activeCurses(player).filter { it.conflictsWithTurmoil }.forEach { deactivateCurse(player, it) }
        setTurmoil(player, true)
        player.playSound(CURSE_ACTIVATE_SOUND)
        player.filterableMessage("You activate Turmoil.")
    }

    private fun setTurmoil(
        player: Player,
        active: Boolean,
    ) {
        player.attr[TURMOIL_ACTIVE_ATTR] = active
        player.setVarbit(AncientCurse.TURMOIL_VARBIT, if (active) 1 else 0)
    }

    /**
     * Turmoil multiplier for one of Attack/Strength/Defence against [target] (2011: base% plus a
     * share of the opponent's corresponding level). Without a target only the base applies.
     */
    fun turmoilMultiplier(
        player: Player,
        skill: Int,
        target: Pawn?,
    ): Double {
        if (!isTurmoilActive(player)) return 1.0
        val (base, share) =
            when (skill) {
                Skills.ATTACK -> 15.0 to 0.15
                Skills.STRENGTH -> 23.0 to 0.10
                Skills.DEFENCE -> 15.0 to 0.15
                else -> return 1.0
            }
        val targetLevel =
            when (target) {
                is Player -> target.skills.getCurrentLevel(skill)
                is Npc ->
                    target.stats.getCurrentLevel(
                        when (skill) {
                            Skills.ATTACK -> NpcSkills.ATTACK
                            Skills.STRENGTH -> NpcSkills.STRENGTH
                            else -> NpcSkills.DEFENCE
                        },
                    )
                else -> 0
            }.coerceIn(0, TURMOIL_TARGET_LEVEL_CAP)
        return 1.0 + (base + share * targetLevel) / 100.0
    }

    // ---- book -----------------------------------------------------------------------------

    enum class PrayerBook { NORMAL, ANCIENT }

    /**
     * Persisted as the enum *name*: the JSON player serializer hands persisted attribute values
     * back as plain [String]s, so a typed enum key would throw a ClassCastException on the first
     * login after the book was saved (and abort every login plugin queued behind it).
     */
    private val CURRENT_BOOK_ATTR = AttributeKey<String>(persistenceKey = "prayer_book")

    /** Not persisted: like the normal book, active curses are cleared on logout/death. */
    private val ACTIVE_CURSES_ATTR = AttributeKey<MutableSet<AncientCurse>>()

    fun getBook(player: Player): PrayerBook {
        val raw = player.attr[CURRENT_BOOK_ATTR] ?: return PrayerBook.NORMAL
        return PrayerBook.values().firstOrNull { it.name == raw } ?: PrayerBook.NORMAL
    }

    /** Pushes the persisted book choice to the client (login) so the prayer tab renders the right grid. */
    fun syncBookVarbit(player: Player) {
        player.setVarbit(AncientCurse.BOOK_VARBIT, if (getBook(player) == PrayerBook.ANCIENT) 1 else 0)
    }

    fun switchBook(
        player: Player,
        book: PrayerBook,
    ) {
        if (getBook(player) == book) {
            player.filterableMessage("You are already using the ${book.name.lowercase()} prayer book.")
            return
        }
        // The two books' overhead icon and drain loops are not designed to run at once.
        Prayers.deactivateAll(player)
        deactivateAllCurses(player)
        player.attr[CURRENT_BOOK_ATTR] = book.name
        syncBookVarbit(player)
        player.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, 0)
        Prayers.confirmQuickPrayerSelection(player)
        player.filterableMessage("You switch to the ${book.name.lowercase()} prayer book.")
    }

    /**
     * A click on the prayer grid (interface 271 component 8) while the curse book is shown.
     * Slot 0 is Protect Item, which is the normal book's own shared effect: it is toggled through
     * [Prayers] (same drain/death integration) and mirrored to the curse varbit for the icon.
     */
    suspend fun onBookButton(
        task: QueueTask,
        slot: Int,
    ) {
        val player = task.player
        when (slot) {
            AncientCurse.PROTECT_ITEM_SLOT -> {
                // Protect Item is the normal book's shared effect (class KDoc), so `Prayers.toggle`
                // runs the *normal* book's activate/deactivate path, which ends in
                // `Prayers.setOverhead` - that function only knows about the 7 normal Protect/
                // Retribution/Smite/Redemption prayers, none of which can be active while the
                // curses book is shown, so it unconditionally computes PrayerIcon.NONE and wipes
                // out any curse overhead (e.g. an active Deflect Melee) that was genuinely still
                // active. Re-apply the real curse overhead immediately afterward so toggling
                // Protect Item under the curses book cannot silently clear a curse's icon.
                Prayers.toggle(task, Prayer.PROTECT_ITEM)
                player.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, if (Prayers.isActive(player, Prayer.PROTECT_ITEM)) 1 else 0)
                refreshCurseOverhead(player)
            }
            AncientCurse.TURMOIL_SLOT -> toggleTurmoil(player)
            else -> AncientCurse.bySlot(slot)?.let { toggleCurse(player, it) }
        }
        player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
    }

    private fun quickCurseLevel(slot: Int): Int? = when (slot) {
        AncientCurse.PROTECT_ITEM_SLOT -> Prayer.PROTECT_ITEM.level
        AncientCurse.TURMOIL_SLOT -> TURMOIL_LEVEL
        else -> AncientCurse.bySlot(slot)?.level
    }

    fun selectedQuickCurseSlots(player: Player): List<Int> =
        (AncientCurse.PROTECT_ITEM_SLOT..AncientCurse.TURMOIL_SLOT).filter {
            player.getVarbit(AncientCurse.QUICK_VARBIT_BASE + it) != 0
        }

    /** Uses the same cache selection bits and exclusions as the ordinary curse grid. */
    fun selectQuickCurse(player: Player, slot: Int) {
        val level = quickCurseLevel(slot) ?: return
        if (getBook(player) != PrayerBook.ANCIENT || player.isDead() || !player.lock.canUsePrayer()) return
        val varbit = AncientCurse.QUICK_VARBIT_BASE + slot
        if (player.getVarbit(varbit) != 0) {
            player.setVarbit(varbit, 0)
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true || player.skills.getMaxLevel(Skills.PRAYER) < level) {
            player.setVarbit(varbit, 0)
            player.filterableMessage("You have not met the requirements for this curse.")
            return
        }
        val curse = AncientCurse.bySlot(slot)
        selectedQuickCurseSlots(player).forEach { otherSlot ->
            val other = AncientCurse.bySlot(otherSlot)
            val conflicts = when {
                slot == AncientCurse.TURMOIL_SLOT -> other?.conflictsWithTurmoil == true
                otherSlot == AncientCurse.TURMOIL_SLOT -> curse?.conflictsWithTurmoil == true
                curse != null && other != null -> curse.conflictsWith(other)
                else -> false
            }
            if (conflicts) player.setVarbit(AncientCurse.QUICK_VARBIT_BASE + otherSlot, 0)
        }
        player.setVarbit(varbit, 1)
    }

    fun toggleQuickCurses(player: Player) {
        if (getBook(player) != PrayerBook.ANCIENT || player.isDead() || !player.lock.canUsePrayer()) return
        val selected = selectedQuickCurseSlots(player)
        if (selected.isEmpty()) {
            player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
            player.filterableMessage("You haven't selected any quick-curses.")
            return
        }
        val active = activeCurses(player).map { it.slot }.toMutableSet()
        if (isTurmoilActive(player)) active.add(AncientCurse.TURMOIL_SLOT)
        if (Prayers.isActive(player, Prayer.PROTECT_ITEM)) active.add(AncientCurse.PROTECT_ITEM_SLOT)
        val turningOff = active == selected.toSet()
        Prayers.deactivateAll(player)
        deactivateAllCurses(player)
        if (turningOff) return
        if (player.getCurrentPrayerPoints() <= 0 || player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You cannot activate your quick-curses right now.")
            return
        }
        selected.filter { player.skills.getMaxLevel(Skills.PRAYER) >= (quickCurseLevel(it) ?: Int.MAX_VALUE) }.forEach { slot ->
            when (slot) {
                AncientCurse.PROTECT_ITEM_SLOT -> Prayers.activate(player, Prayer.PROTECT_ITEM)
                AncientCurse.TURMOIL_SLOT -> toggleTurmoil(player)
                else -> AncientCurse.bySlot(slot)?.let { toggleCurse(player, it) }
            }
        }
        val anyActive = activeCurses(player).isNotEmpty() || isTurmoilActive(player) || Prayers.isActive(player, Prayer.PROTECT_ITEM)
        player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, if (anyActive) 1 else 0)
    }

    /**
     * Total drain effect of everything active on the curse book, in the shared units
     * [Prayer.drainEffect] uses. Summed into [Prayers.drainPrayer]'s counter, so curses drain on
     * exactly the same schedule and with exactly the same prayer-bonus resistance as prayers.
     */
    fun activeCurseDrainEffect(player: Player): Int {
        var rate = activeCurses(player).sumOf { it.drainEffect }
        if (isTurmoilActive(player)) rate += AncientCurse.TURMOIL_DRAIN_EFFECT
        return rate
    }

    private fun activeCurses(player: Player): MutableSet<AncientCurse> {
        var set = player.attr[ACTIVE_CURSES_ATTR]
        if (set == null) {
            set = mutableSetOf()
            player.attr[ACTIVE_CURSES_ATTR] = set
        }
        return set
    }

    fun isCurseActive(
        player: Player,
        curse: AncientCurse,
    ): Boolean = activeCurses(player).contains(curse)

    fun toggleCurse(
        player: Player,
        curse: AncientCurse,
    ) {
        if (isCurseActive(player, curse)) {
            deactivateCurse(player, curse)
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - see ::curse unlock.")
            return
        }
        if (getBook(player) != PrayerBook.ANCIENT) {
            player.filterableMessage("You must switch to the ancient book first - see ::curse book ancient.")
            return
        }
        if (player.skills.getMaxLevel(Skills.PRAYER) < curse.level) {
            player.filterableMessage("You need a Prayer level of ${curse.level} to use ${curse.curseName}.")
            return
        }
        if (player.getCurrentPrayerPoints() <= 0) {
            player.filterableMessage("You don't have enough Prayer points left.")
            return
        }
        activeCurses(player).filter { curse.conflictsWith(it) }.forEach { deactivateCurse(player, it) }
        if (curse.conflictsWithTurmoil && isTurmoilActive(player)) {
            setTurmoil(player, false)
            player.filterableMessage("You deactivate Turmoil.")
        }
        activeCurses(player).add(curse)
        player.setVarbit(curse.varbit, 1)
        player.playSound(CURSE_ACTIVATE_SOUND)
        player.filterableMessage("You activate ${curse.curseName}.")
        refreshCurseOverhead(player)
    }

    fun deactivateCurse(
        player: Player,
        curse: AncientCurse,
    ) {
        if (activeCurses(player).remove(curse)) {
            player.setVarbit(curse.varbit, 0)
            player.playSound(CURSE_DEACTIVATE_SOUND)
            player.filterableMessage("You deactivate ${curse.curseName}.")
            refreshCurseOverhead(player)
            if (curse.category == AncientCurse.Category.LEECH) resetLeechBoosts(player)
        }
    }

    fun deactivateAllCurses(player: Player) {
        if (activeCurses(player).isEmpty() && !isTurmoilActive(player)) return
        activeCurses(player).forEach { player.setVarbit(it.varbit, 0) }
        activeCurses(player).clear()
        setTurmoil(player, false)
        player.playSound(CURSE_DEACTIVATE_SOUND)
        resetLeechBoosts(player)
        refreshCurseOverhead(player)
    }

    private fun refreshCurseOverhead(player: Player) {
        // Deflect Summoning can run alongside a combat Deflect (wiki.darkan.org: "Deflect
        // Summoning can be used with other Deflect Curses, but not with Wrath, or Soul Split"), so
        // two icon-bearing curses can genuinely be active at once. The previous code had to drop
        // one of them, because only one head icon renders at a time.
        //
        // 2026-09-06: it does not have to drop anything. The decoded `headicons_prayer` sheet has
        // dedicated *combined* frames for exactly these pairings - 16/17/18 are Deflect Summoning
        // drawn together with Deflect Melee/Missiles/Magic (see [PrayerIcon]) - which is how real
        // RS shows both. Everything else stays mutually exclusive by [AncientCurse.conflictsWith],
        // so at most one non-Summoning icon can be active anyway.
        val active = activeCurses(player)
        val summoning = active.any { it.category == AncientCurse.Category.DEFLECT_SUMMONING }
        val combat = active.firstOrNull { it.icon != null && it.category != AncientCurse.Category.DEFLECT_SUMMONING }?.icon
        val icon = PrayerIcon.combinedDeflect(summoning, combat) ?: PrayerIcon.NONE
        if (player.prayerIcon != icon.id) {
            player.prayerIcon = icon.id
            player.addBlock(UpdateBlockType.APPEARANCE)
        }
    }

    // ---- Sap / Leech escalation --------------------------------------------------------------

    private fun drainState(target: Pawn): MutableMap<Int, Int> {
        var map = target.attr[CURSE_DRAIN_PCT_ATTR]
        if (map == null) {
            map = mutableMapOf()
            target.attr[CURSE_DRAIN_PCT_ATTR] = map
        }
        return map
    }

    private fun boostState(player: Player): MutableMap<Int, Int> {
        var map = player.attr[LEECH_BOOST_PCT_ATTR]
        if (map == null) {
            map = mutableMapOf()
            player.attr[LEECH_BOOST_PCT_ATTR] = map
        }
        return map
    }

    /** Called when the target dies/logs out so the next fight starts from the base drain again. */
    fun clearDrainState(target: Pawn) {
        target.attr.remove(CURSE_DRAIN_PCT_ATTR)
    }

    private fun resetLeechBoosts(player: Player) {
        player.attr.remove(LEECH_BOOST_PCT_ATTR)
    }

    private fun maxLevel(
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
    ): Int =
        when (target) {
            is Player -> target.skills.getMaxLevel(playerSkill)
            is Npc -> target.stats.getMaxLevel(npcSkill)
            else -> 0
        }

    private fun currentLevel(
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
    ): Int =
        when (target) {
            is Player -> target.skills.getCurrentLevel(playerSkill)
            is Npc -> target.stats.getCurrentLevel(npcSkill)
            else -> 0
        }

    /**
     * One escalation step of a Sap/Leech drain on [target]'s stat: the first activation applies
     * [basePct], every later one adds one point up to [capPct]. The target's current level is never
     * pushed below `max * (1 - capPct)` by curses, so a stale state after natural restoration
     * cannot over-drain. Returns true when a level was actually removed.
     */
    private fun escalateDrain(
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
        basePct: Int,
        capPct: Int,
    ): Boolean {
        val max = maxLevel(target, playerSkill, npcSkill)
        if (max <= 0) return false
        val state = drainState(target)
        val current = state[playerSkill] ?: 0
        val next = if (current == 0) basePct else (current + 1).coerceAtMost(capPct)
        state[playerSkill] = next
        val floor = max - (max * capPct / 100.0).toInt().coerceAtLeast(1)
        val targetLevel = max - (max * next / 100.0).toInt().coerceAtLeast(1)
        val now = currentLevel(target, playerSkill, npcSkill)
        val wanted = maxOf(targetLevel, floor).coerceAtLeast(0)
        if (now <= wanted) return false
        val amount = now - wanted
        when (target) {
            is Player -> target.skills.alterCurrentLevel(playerSkill, -amount)
            is Npc -> target.stats.alterCurrentLevel(npcSkill, -amount)
        }
        return true
    }

    /** Leech self-boost: first activation +5%, then +1% per activation up to +10% of the caster's max level. */
    private fun escalateBoost(
        player: Player,
        skill: Int,
    ) {
        val max = player.skills.getMaxLevel(skill)
        val state = boostState(player)
        val current = state[skill] ?: 0
        val next = if (current == 0) LEECH_BOOST_BASE_PCT else (current + 1).coerceAtMost(LEECH_BOOST_CAP_PCT)
        state[skill] = next
        val cap = (max * next / 100.0).toInt().coerceAtLeast(1)
        val now = player.skills.getCurrentLevel(skill)
        val wanted = max + cap
        if (now >= wanted) return
        player.skills.alterCurrentLevel(skill, wanted - now, capValue = cap)
    }

    private fun sap(
        target: Pawn,
        vararg skills: Pair<Int, Int>,
    ): Boolean = skills.map { (p, n) -> escalateDrain(target, p, n, SAP_BASE_PCT, SAP_CAP_PCT) }.any { it }

    private fun leech(
        attacker: Player,
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
    ) {
        escalateDrain(target, playerSkill, npcSkill, LEECH_DRAIN_BASE_PCT, LEECH_DRAIN_CAP_PCT)
        escalateBoost(attacker, playerSkill)
    }

    /**
     * Combat hook for Sap/Leech/Soul Split, called once per landed hit from the single shared
     * `dealHit` entry point every combat style routes through (see combat/PawnExt.kt). Soul Split
     * fires on every hit; Sap/Leech roll [SAP_LEECH_ACTIVATION_CHANCE] per landed hit and then take
     * one escalation step. Wrath's on-death explosion fires from the curse plugin's `on_player_death`.
     */
    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (attacker !is Player || damage <= 0) return
        val active = activeCurses(attacker)
        if (active.isEmpty()) return
        if (active.contains(AncientCurse.SOUL_SPLIT)) {
            applySoulSplit(attacker, target, damage)
        }
        val sapLeech = active.filter { it.category == AncientCurse.Category.SAP || it.category == AncientCurse.Category.LEECH }
        sapLeech.forEach { curse ->
            if (attacker.world.percentChance(curse.activationChancePercent)) applySapLeech(attacker, target, curse)
        }
    }

    /**
     * Soul Split's real visual, PROVEN from Novite `Player.handleSoulSplit`: an outgoing
     * projectile (2263) from caster to target, a graphic (2264) on the target one tick later, and
     * a return projectile (2263) from target back to caster - the "souls" travelling out and back.
     * Timing/arc reuses [ProjectileType.MAGIC] (the closest existing preset); only the gfx id
     * itself is source-proven, not the exact tick-for-tick flight parameters (see [AncientCurse]
     * class KDoc on this file's general anim/gfx sourcing).
     */
    private const val SOUL_SPLIT_PROJECTILE_GFX = 2263
    private const val SOUL_SPLIT_TARGET_GFX = 2264

    private fun applySoulSplit(
        attacker: Player,
        target: Pawn,
        damage: Int,
    ) {
        attacker.world.spawn(attacker.createProjectile(target, SOUL_SPLIT_PROJECTILE_GFX, ProjectileType.MAGIC))
        attacker.heal((damage * 0.2).toInt().coerceAtLeast(0))
        if (target is Player) target.decreasePrayerPoints((damage * 0.2 * 10).toInt())
        target.graphic(SOUL_SPLIT_TARGET_GFX, delay = 1)
        attacker.playSound(CURSE_LAND_SOUND)
        attacker.queue {
            wait(1)
            if (!attacker.isDead()) {
                attacker.world.spawn(target.createProjectile(attacker, SOUL_SPLIT_PROJECTILE_GFX, ProjectileType.MAGIC))
            }
        }
    }

    private fun applySapLeech(
        attacker: Player,
        target: Pawn,
        curse: AncientCurse,
    ) {
        curse.castAnimation?.let { attacker.animate(it) }
        curse.castGraphic?.let { attacker.graphic(it) }
        curse.projectileGraphic?.let { attacker.world.spawn(attacker.createProjectile(target, it, ProjectileType.MAGIC)) }
        curse.targetGraphic?.let { target.graphic(it, delay = 1) }
        attacker.playSound(CURSE_CAST_SOUND)
        when (curse) {
            AncientCurse.SAP_WARRIOR ->
                if (sap(target, Skills.ATTACK to NpcSkills.ATTACK, Skills.STRENGTH to NpcSkills.STRENGTH, Skills.DEFENCE to NpcSkills.DEFENCE)) {
                    curseMessages(attacker, target, "Attack, Strength and Defence")
                }
            AncientCurse.SAP_RANGER ->
                if (sap(target, Skills.RANGED to NpcSkills.RANGED, Skills.DEFENCE to NpcSkills.DEFENCE)) {
                    curseMessages(attacker, target, "Ranged and Defence")
                }
            AncientCurse.SAP_MAGE ->
                if (sap(target, Skills.MAGIC to NpcSkills.MAGIC, Skills.DEFENCE to NpcSkills.DEFENCE)) {
                    curseMessages(attacker, target, "Magic and Defence")
                }
            AncientCurse.SAP_SPIRIT ->
                if (target is Player) {
                    val energy = AttackTab.getEnergy(target)
                    if (energy > 0) {
                        AttackTab.setEnergy(target, (energy - 10).coerceIn(0, 100))
                        curseMessages(attacker, target, "special attack energy")
                    }
                }
            AncientCurse.LEECH_ATTACK -> leech(attacker, target, Skills.ATTACK, NpcSkills.ATTACK).also { leechMessages(attacker, target, "Attack") }
            AncientCurse.LEECH_RANGED -> leech(attacker, target, Skills.RANGED, NpcSkills.RANGED).also { leechMessages(attacker, target, "Ranged") }
            AncientCurse.LEECH_MAGIC -> leech(attacker, target, Skills.MAGIC, NpcSkills.MAGIC).also { leechMessages(attacker, target, "Magic") }
            AncientCurse.LEECH_DEFENCE -> leech(attacker, target, Skills.DEFENCE, NpcSkills.DEFENCE).also { leechMessages(attacker, target, "Defence") }
            AncientCurse.LEECH_STRENGTH -> leech(attacker, target, Skills.STRENGTH, NpcSkills.STRENGTH).also { leechMessages(attacker, target, "Strength") }
            AncientCurse.LEECH_ENERGY ->
                if (target is Player && target.runEnergy > 0) {
                    val stolen = target.runEnergy.coerceAtMost(10.0)
                    target.runEnergy = (target.runEnergy - stolen).coerceIn(0.0, 100.0)
                    attacker.runEnergy = (attacker.runEnergy + stolen).coerceIn(0.0, 100.0)
                    leechMessages(attacker, target, "run energy")
                }
            AncientCurse.LEECH_SPECIAL_ATTACK ->
                if (target is Player) {
                    val stolen = AttackTab.getEnergy(target).coerceIn(0, 10)
                    if (stolen > 0) {
                        AttackTab.setEnergy(target, AttackTab.getEnergy(target) - stolen)
                        AttackTab.setEnergy(attacker, (AttackTab.getEnergy(attacker) + stolen).coerceAtMost(100))
                        leechMessages(attacker, target, "special attack energy")
                    }
                }
            else -> {}
        }
    }

    private fun curseMessages(
        attacker: Player,
        target: Pawn,
        what: String,
    ) {
        attacker.filterableMessage("Your curse drains $what from the enemy.")
        if (target is Player) target.filterableMessage("Your $what has been drained by an enemy curse.")
    }

    private fun leechMessages(
        attacker: Player,
        target: Pawn,
        what: String,
    ) {
        attacker.filterableMessage("Your curse drains $what from the enemy, boosting your $what.")
        if (target is Player) target.filterableMessage("Your $what has been leeched by an enemy curse.")
    }

    // ---- Wrath / Deflect -------------------------------------------------------------------

    /** Wrath's death-explosion graphic (centre, PROVEN from Novite `Player.sendDeath`). */
    private const val WRATH_CENTRE_GFX = 2259

    /**
     * Wrath: on the WEARER's own death, up to 300% of the Prayer level in a 5x5 area - called from
     * the curse plugin's `on_player_death`, before curses are cleared. Only pawns that could legally
     * be hit are struck: NPCs always, players only where the two could fight (Wilderness rules).
     * The 300% multiplier and centre graphic 2259 are PROVEN from Novite `Player.sendDeath`
     * (`Utils.getRandom(skills.getLevelForXp(Skills.PRAYER) * 3)`); this file previously used 2.5,
     * which was a bug carried over from documentation rather than this source.
     */
    /** Exposed for deterministic testing of the sourced 300% multiplier without mocking world dispatch. */
    internal fun wrathMaxDamage(player: Player): Int = (player.skills.getMaxLevel(Skills.PRAYER) * 3.0).toInt()

    fun wrathExplosion(player: Player) {
        val damage = wrathMaxDamage(player)
        if (damage <= 0) return
        player.graphic(WRATH_CENTRE_GFX)
        val tile = player.tile
        val world = player.world
        val targets = mutableListOf<Pawn>()
        world.npcs.forEach { if (it.tile.isWithinRadius(tile, 2) && !it.isDead()) targets.add(it) }
        world.players.forEach {
            if (it != player && it.tile.isWithinRadius(tile, 2) && !it.isDead() &&
                gg.rsmod.plugins.content.mechanics.pvp.AreaState.canPlayersFight(player, it)
            ) {
                targets.add(it)
            }
        }
        targets.forEach { it.hit(damage = world.random(damage)) }
    }

    /** Melee/Ranged/Magic only - Deflect Summoning keeps its block-only behaviour. */
    private val DEFLECT_STYLE =
        mapOf(
            CombatClass.MELEE to AncientCurse.DEFLECT_MELEE,
            CombatClass.RANGED to AncientCurse.DEFLECT_MISSILES,
            CombatClass.MAGIC to AncientCurse.DEFLECT_MAGIC,
        )

    /**
     * Deflect reflection: ~63% chance per landed hit to reflect 10% of the damage back, no recoil
     * below 10 reflected damage. Reflected damage is dealt raw so it can never re-trigger a Deflect
     * on the original attacker (no recursion).
     */
    fun onIncomingHit(
        attacker: Pawn,
        target: Pawn,
        style: CombatClass,
        damage: Int,
    ) {
        if (target !is Player || damage <= 0) return
        val curse = DEFLECT_STYLE[style] ?: return
        if (!isCurseActive(target, curse)) return
        if (!target.world.percentChance(63.0)) return
        val reflected = (damage * 0.10).toInt()
        if (reflected < 10) return
        curse.reflectAnimation?.let { target.animate(it) }
        curse.reflectGraphic?.let { target.graphic(it) }
        attacker.hit(damage = reflected)
    }
}
