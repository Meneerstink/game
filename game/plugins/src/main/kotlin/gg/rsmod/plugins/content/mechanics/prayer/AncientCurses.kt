package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.PrayerIcon
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
 * `prayers.plugin.kts` via [onBookButton]. The `curse` commands remain as admin/diagnostic tools.
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
 *    was a bug, not a sourced value); Deflects block like Protect prayers and reflect 10% of qualifying
 *    of hits; Berserker extends boosts by 15% (in `stat_restoration.plugin.kts`).
 */
object AncientCurses {
    private val TURMOIL_ACTIVE_ATTR = AttributeKey<Boolean>()
    private val TURMOIL_BONUS_ATTR = AttributeKey<MutableMap<Int, Int>>()

    /**
     * Stands in for the real "Temple at Senntisten" unlock quest (PROJECT_PLAN SS18/SS7 confirms an
     * unlock miniquest is expected for Ancient Curses) - a one-off ritual (`curse unlock`).
     */
    val UNLOCKED_ATTR = AttributeKey<Boolean>(persistenceKey = "ancient_curses_unlocked")
    private const val UNLOCK_COST = 50_000
    const val TURMOIL_LEVEL = 95
    /** Void/Novite 667: Ancient Protect Item is level 50, unlike normal Protect Item (25). */
    const val PROTECT_ITEM_LEVEL = 50

    const val SAP_BASE_PCT = 10
    const val SAP_CAP_PCT = 20
    const val LEECH_DRAIN_BASE_PCT = 10
    const val LEECH_DRAIN_CAP_PCT = 25
    const val LEECH_BOOST_BASE_PCT = 5
    const val LEECH_BOOST_CAP_PCT = 10
    const val TURMOIL_TARGET_LEVEL_CAP = 99

    /** Source-proven rev-667 curse-book activation visuals from Novite's Prayer.java. */
    const val PROTECT_ITEM_ACTIVATION_ANIMATION = 12567
    const val PROTECT_ITEM_ACTIVATION_GRAPHIC = 2213
    const val TURMOIL_ACTIVATION_ANIMATION = 12565
    const val TURMOIL_ACTIVATION_GRAPHIC = 2226

    /**
     * Curse audio model, CURSES-2011 (2026-09-14), PROVEN from the revision-667 cache itself
     * (`reference/curses-audio-research/FINDINGS.md`, OpenRS2 #1473 and the production cache are
     * byte-identical for every curse spotanim/seq/synth) and from the client source:
     *
     *  * The server sends **no** activation sound. Every authentic curse sound is a frame sound on the
     *    *graphic's* sequence, which the client plays by itself when the spotanim renders
     *    (`EntitySpotAnimation` -> `Animator.newFrame` -> `Static431`): Protect Item 2213/seq 12568
     *    -> synth 8117, Sap caster 2214/2217/2220/2223 -> 12570 -> 8115, Turmoil 2226 -> 12566 ->
     *    8111, Deflect reflect 2227..2230 -> 12574 -> 8107, Leech Energy/Special caster 2251/2255 ->
     *    12576 -> 8116, Wrath ring 2260 -> 12581 -> 8118 (radius 5), Berserker 2266 -> 12590 -> 8106.
     *    Every player body sequence (12565/12567/12569/12573/12575/12589) and every projectile/impact
     *    sequence is silent, and curses without an activation graphic (Sap, Leech, Deflect, Wrath,
     *    Soul Split) have no activation visual in Novite rev-667 `Prayer.switchPrayer` or Divergent
     *    667 `Prayer.java` either, so they are silent on activation.
     *  * Novite's generic 2662 (`Prayer.java:628`, the normal book's Improved Reflexes track) was the
     *    owner's live "same wrong extra sound on every curse" (client trace 2026-09-14: 2662 packet,
     *    then 2226 -> 8111 from the sequence). It is no longer sent for any curse.
     *  * Deactivation 2663 - Novite sends 2663 on an explicit toggle-off (`Prayer.java:493/499`) and
     *    Void's `prayerStop` plays `deactivate_prayer` = 2663 for both books.
     *  * A curse switched off *because another curse replaced it* is silent: Novite's `closePrayers`
     *    sends no sound.
     *
     * Still unsourced (SOURCE_BLOCKED, nothing sent rather than guessed): synths 8108/8109/8110/8112/
     * 8113/8114/8119 in the same curse range have no name hash and no sequence owner; a 2018
     * rune-server post names 8112/8113/8119 as Soul Split files without a phase mapping.
     */
    private const val CURSE_DEACTIVATE_SOUND = Sfx.CANCEL_PRAYER

    /**
     * Server-sent activation sound per curse group for the 17 curses that have no activation
     * graphic (Sap, Leech, Deflect, Wrath, Soul Split). The real 2011 server sent one, like the
     * normal book's per-prayer 2660-2690 tracks; the ids are the 7 synths of the curse block that
     * no sequence owns (8108/8109/8110/8112/8113/8114/8119). No source labels them, so an entry is
     * only filled by explicit owner decision after auditioning in game (`sound <id>`), and is
     * PROVISIONAL (owner choice), never SOURCE VERIFIED. A missing entry means silent.
     * Berserker (FREE) and Turmoil/Protect Item are graphic-borne and never listed here.
     *
     * PROVISIONAL (owner choice by ear, 2026-09-14 ~21:10): Sap 8108, Leech 8109, Deflect 8110,
     * Wrath 8114, Soul Split 8112 (toggle) with 8113/8119 for the Soul Split hit/return below.
     */
    private val ACTIVATION_SOUND_BY_CATEGORY: Map<AncientCurse.Category, Int> =
        mapOf(
            AncientCurse.Category.SAP to 8108,
            AncientCurse.Category.LEECH to 8109,
            AncientCurse.Category.DEFLECT_COMBAT to 8110,
            AncientCurse.Category.DEFLECT_SUMMONING to 8110,
            AncientCurse.Category.WRATH to 8114,
            AncientCurse.Category.SOUL_SPLIT to 8112,
        )

    /** PROVISIONAL (owner delegated, same date): Soul Split souls leaving the target / returning to heal. */
    const val SOUL_SPLIT_HIT_SOUND = 8113
    const val SOUL_SPLIT_RETURN_SOUND = 8119

    fun activationSound(curse: AncientCurse): Int? = ACTIVATION_SOUND_BY_CATEGORY[curse.category]

    /** Novite's Sap/Leech projectile packet: start/end 35, speed 20, delay 5, curve 0. */
    private const val CURSE_PROJECTILE_START_HEIGHT = 35
    private const val CURSE_PROJECTILE_END_HEIGHT = 35
    private const val CURSE_PROJECTILE_DELAY = 5
    private const val CURSE_PROJECTILE_LIFESPAN = 20

    /**
     * Sap/Leech stat model, from the owner-supplied 2011 Knowledge Base text (2026-09-14), which Void
     * (`Leech.kt`, `Prayer.effectiveLevelModifier`) and Novite (`adjustStat` -> varbits 6857+)
     * implement the same way:
     *
     *  * "Once a sap or leech curse is activated, it immediately drains your opponent's stat by
     *    10%": the first proc registers a **base drain**, an invisible combat modifier
     *    ([drainMultiplier]) that lasts exactly as long as the caster's curse stays active.
     *  * "Keeping the curse activated will slowly continue to drain ... up to 20% (sap) / 25%
     *    (leech)": every later proc removes one more percent of the max level as a **real level
     *    drain** ([CURSE_DRAIN_PCT_ATTR] counts the steps), so it "regenerates over time as usual".
     *  * "When the curse is deactivated, the initial 10% ... will be immediately restored":
     *    [releaseCurseEffects] drops the caster from every target's base-drain set.
     *  * "The boost to your own stat when using a leech curse works in the same way": base 5 % is a
     *    modifier while the Leech is active ([leechMultiplier]); +1 % per proc up to +10 % is a real
     *    level boost that decays like a potion.
     *
     * Player casters and victims see it in the prayer tab through varbits 6857..6861 (varp 1583,
     * 6 bits each, cache-proven; 30 = 0 %, Void: 11..42 = -25..+15 %).
     */
    /** Per-target: skill -> escalated drain steps (percent of max already removed as real levels). */
    private val CURSE_DRAIN_PCT_ATTR = AttributeKey<MutableMap<Int, Int>>()

    /** Per-target: skill -> casters whose active Sap/Leech currently applies the base 10 % modifier. */
    private val CURSE_BASE_DRAIN_ATTR = AttributeKey<MutableMap<Int, MutableSet<Player>>>()

    /** Per-caster: curse -> targets it has cursed, so deactivation can release their base drain. */
    private val CURSED_TARGETS_ATTR = AttributeKey<MutableMap<AncientCurse, MutableSet<Pawn>>>()

    /** Per-caster: skill -> escalated self-boost steps already applied as real levels by Leeches. */
    private val LEECH_BOOST_PCT_ATTR = AttributeKey<MutableMap<Int, Int>>()

    private val STAT_MODIFIER_VARBITS =
        mapOf(Skills.ATTACK to 6857, Skills.STRENGTH to 6858, Skills.DEFENCE to 6859, Skills.RANGED to 6860, Skills.MAGIC to 6861)

    /** Combat modifier for [target]'s [skill] level while any caster's Sap/Leech base drain applies. */
    fun drainMultiplier(target: Pawn, skill: Int): Double =
        if (target.attr[CURSE_BASE_DRAIN_ATTR]?.get(skill)?.isNotEmpty() == true) 1.0 - SAP_BASE_PCT / 100.0 else 1.0

    /** Pushes the prayer-tab stat modifiers (boost minus drain) for a player caster or victim. */
    fun syncStatVarbits(player: Player) {
        STAT_MODIFIER_VARBITS.forEach { (skill, varbit) ->
            val boost =
                (if (leechSkills[skill]?.let { isCurseActive(player, it) } == true) LEECH_BOOST_BASE_PCT else 0) +
                    (player.attr[LEECH_BOOST_PCT_ATTR]?.get(skill) ?: 0)
            val drain =
                (if (player.attr[CURSE_BASE_DRAIN_ATTR]?.get(skill)?.isNotEmpty() == true) SAP_BASE_PCT else 0) +
                    (player.attr[CURSE_DRAIN_PCT_ATTR]?.get(skill) ?: 0)
            val value = 30 + Math.round(boost * 12.0 / 15.0).toInt() - Math.round(drain * 19.0 / 25.0).toInt()
            player.setVarbit(varbit, value.coerceIn(0, 63))
        }
    }

    /** Immediate release of everything bound to [curse] staying active (KB: the initial 10 % / 5 %). */
    private fun releaseCurseEffects(player: Player, curse: AncientCurse) {
        val stillDrained = activeCurses(player).filter { it != curse }.flatMap { it.drains }.toSet()
        val targets = player.attr[CURSED_TARGETS_ATTR]?.remove(curse) ?: emptySet<Pawn>()
        for (target in targets) {
            val sets = target.attr[CURSE_BASE_DRAIN_ATTR] ?: continue
            for (skill in curse.drains) {
                if (skill in stillDrained) continue
                sets[skill]?.let { casters ->
                    casters.remove(player)
                    if (casters.isEmpty()) sets.remove(skill)
                }
            }
            if (target is Player) syncStatVarbits(target)
        }
        player.attr[LEECH_BOOST_PCT_ATTR]?.let { state -> curse.drains.forEach { state.remove(it) } }
        syncStatVarbits(player)
    }

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

    fun toggleTurmoil(player: Player, playActivationVisual: Boolean = true) {
        if (isTurmoilActive(player)) {
            setTurmoil(player, false)
            resetTurmoilBonus(player)
            player.playSound(CURSE_DEACTIVATE_SOUND, volume = Prayers.PRAYER_SOUND_VOLUME)
            player.filterableMessage("You deactivate Turmoil.")
            return
        }
        if (getBook(player) != PrayerBook.ANCIENT) {
            player.filterableMessage("You must switch to the ancient prayer book first.")
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - use: curse unlock.")
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
        activeCurses(player).filter { it.conflictsWithTurmoil }.forEach { deactivateCurse(player, it, playSound = false) }
        setTurmoil(player, true)
        player.attr[TURMOIL_BONUS_ATTR] = mutableMapOf()
        if (playActivationVisual) {
            player.animate(TURMOIL_ACTIVATION_ANIMATION)
            player.graphic(TURMOIL_ACTIVATION_GRAPHIC)
        }
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
    @Suppress("UNUSED_PARAMETER")
    fun turmoilMultiplier(
        player: Player,
        skill: Int,
        target: Pawn?,
    ): Double {
        if (!isTurmoilActive(player)) return 1.0
        val base =
            when (skill) {
                Skills.ATTACK -> 15
                Skills.STRENGTH -> 23
                Skills.DEFENCE -> 15
                else -> return 1.0
            }
        // [establishTurmoilBonus] already stores percentage points (opponent level, capped at 99,
        // times 15/10/15 %), so both terms are percentages here.
        val opponentBonus = player.attr[TURMOIL_BONUS_ATTR]?.get(skill) ?: 0
        return 1.0 + (base + opponentBonus) / 100.0
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

    /**
     * Rebuilds the session-only curse runtime state from the persisted active varbits. The active
     * curse set and Turmoil flag intentionally are not serialized because they are derived from
     * the cache-backed varp state; failing to rebuild them after reconnect made curses appear
     * selected in the client while they did not drain, protect or contribute combat effects.
     */
    fun restoreActiveState(player: Player) {
        val active = activeCurses(player)
        active.clear()
        if (getBook(player) == PrayerBook.ANCIENT) {
            AncientCurse.values
                .filter { it.slot != AncientCurse.TURMOIL_SLOT && player.getVarbit(it.varbit) != 0 }
                .forEach(active::add)
            setTurmoil(player, player.getVarbit(AncientCurse.TURMOIL_VARBIT) != 0)
            if (isTurmoilActive(player)) {
                player.attr[TURMOIL_BONUS_ATTR] = mutableMapOf()
            } else {
                player.attr.remove(TURMOIL_BONUS_ATTR)
            }
        } else {
            // A valid normal-book state cannot contain active curses. Clear only stale curse bits
            // from older/broken saves; Protect Item remains shared between both books.
            AncientCurse.values
                .filter { it.slot != AncientCurse.TURMOIL_SLOT }
                .forEach { player.setVarbit(it.varbit, 0) }
            setTurmoil(player, false)
            player.attr.remove(TURMOIL_BONUS_ATTR)
        }
        refreshCurseOverhead(player)
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
     * Slot 0 uses the normal book's shared Protect Item state for drain/death integration, but
     * retains the Ancient-book level-50 and unlock requirements before entering that path.
     */
    suspend fun onBookButton(
        task: QueueTask,
        slot: Int,
    ) {
        val player = task.player
        when (slot) {
            AncientCurse.PROTECT_ITEM_SLOT -> {
                if (player.attr[UNLOCKED_ATTR] != true) {
                    player.filterableMessage("You must perform the ritual first - use: curse unlock.")
                    return
                }
                if (player.skills.getMaxLevel(Skills.PRAYER) < PROTECT_ITEM_LEVEL) {
                    player.filterableMessage("You need a Prayer level of $PROTECT_ITEM_LEVEL to use Protect Item.")
                    return
                }
                // Protect Item is the normal book's shared state (class KDoc), so `Prayers.toggle`
                // runs the normal activation path, which ends in
                // `Prayers.setOverhead` - that function only knows about the 7 normal Protect/
                // Retribution/Smite/Redemption prayers, none of which can be active while the
                // curses book is shown, so it unconditionally computes PrayerIcon.NONE and wipes
                // out any curse overhead (e.g. an active Deflect Melee) that was genuinely still
                // active. Re-apply the real curse overhead immediately afterward so toggling
                // Protect Item under the curses book cannot silently clear a curse's icon.
                val wasActive = Prayers.isActive(player, Prayer.PROTECT_ITEM)
                Prayers.toggle(task, Prayer.PROTECT_ITEM)
                if (!wasActive && Prayers.isActive(player, Prayer.PROTECT_ITEM)) {
                    player.animate(PROTECT_ITEM_ACTIVATION_ANIMATION)
                    player.graphic(PROTECT_ITEM_ACTIVATION_GRAPHIC)
                }
                player.setVarbit(AncientCurse.PROTECT_ITEM_VARBIT, if (Prayers.isActive(player, Prayer.PROTECT_ITEM)) 1 else 0)
                refreshCurseOverhead(player)
            }
            AncientCurse.TURMOIL_SLOT -> toggleTurmoil(player)
            else -> AncientCurse.bySlot(slot)?.let { toggleCurse(player, it) }
        }
        player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
    }

    private fun quickCurseLevel(slot: Int): Int? = when (slot) {
        AncientCurse.PROTECT_ITEM_SLOT -> PROTECT_ITEM_LEVEL
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
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER,
            )
        ) {
            return
        }
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
                // Novite rev-667 `Prayer.switchPrayer`: every curse activation visual is inside
                // `if (!usingQuickPrayer)`, so quick-curse activation is visual-free (and, with no
                // graphic, silent - see the audio KDoc above).
                AncientCurse.PROTECT_ITEM_SLOT -> Prayers.activate(player, Prayer.PROTECT_ITEM)
                AncientCurse.TURMOIL_SLOT -> toggleTurmoil(player, playActivationVisual = false)
                else -> AncientCurse.bySlot(slot)?.let { toggleCurse(player, it, playActivationVisual = false) }
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
        playActivationVisual: Boolean = true,
    ) {
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER,
            )
        ) {
            return
        }
        if (isCurseActive(player, curse)) {
            deactivateCurse(player, curse)
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - use: curse unlock.")
            return
        }
        if (getBook(player) != PrayerBook.ANCIENT) {
            player.filterableMessage("You must switch to the ancient book first - use: curse book ancient.")
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
        // Dragon scimitar Sever (and every other overhead-disabling effect) blocks the curse book's Deflects too; before,
        // only the normal book checked the timer, so a severed player could re-enable a Deflect at once (owner 2026-09-18).
        if (curse.category == AncientCurse.Category.DEFLECT_COMBAT && Prayers.overheadsDisabled(player)) {
            player.setVarbit(curse.varbit, 0)
            player.message("You cannot use overhead prayers right now.")
            return
        }
        activeCurses(player).filter { curse.conflictsWith(it) }.forEach { deactivateCurse(player, it, playSound = false) }
        if (curse.conflictsWithTurmoil && isTurmoilActive(player)) {
            setTurmoil(player, false)
            resetTurmoilBonus(player)
            player.filterableMessage("You deactivate Turmoil.")
        }
        activeCurses(player).add(curse)
        player.setVarbit(curse.varbit, 1)
        if (playActivationVisual) {
            curse.activationAnimation?.let { player.animate(it) }
            curse.activationGraphic?.let { player.graphic(it) }
            // Graphic-borne sounds play client-side; only graphic-less curses get a server sound.
            if (curse.activationGraphic == null) activationSound(curse)?.let { player.playSound(it, volume = Prayers.PRAYER_SOUND_VOLUME) }
        }
        player.filterableMessage("You activate ${curse.curseName}.")
        refreshCurseOverhead(player)
    }

    fun deactivateCurse(
        player: Player,
        curse: AncientCurse,
        playSound: Boolean = true,
    ) {
        if (activeCurses(player).remove(curse)) {
            player.setVarbit(curse.varbit, 0)
            if (playSound) player.playSound(CURSE_DEACTIVATE_SOUND, volume = Prayers.PRAYER_SOUND_VOLUME)
            player.filterableMessage("You deactivate ${curse.curseName}.")
            refreshCurseOverhead(player)
            releaseCurseEffects(player, curse)
        }
    }

    fun deactivateAllCurses(player: Player) {
        if (activeCurses(player).isEmpty() && !isTurmoilActive(player)) return
        activeCurses(player).forEach { player.setVarbit(it.varbit, 0) }
        val released = activeCurses(player).toList()
        activeCurses(player).clear()
        released.forEach { releaseCurseEffects(player, it) }
        setTurmoil(player, false)
        player.attr.remove(TURMOIL_BONUS_ATTR)
        // Bulk shutdowns (death, logout, book switch, zero prayer, pool) mirror Novite's
        // closePrayers path and are silent. Sound 2663 belongs to an explicit toggle-off only.
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
        target.attr.remove(CURSE_BASE_DRAIN_ATTR)
        if (target is Player) syncStatVarbits(target)
    }

    private fun baseDrainState(target: Pawn): MutableMap<Int, MutableSet<Player>> =
        target.attr[CURSE_BASE_DRAIN_ATTR] ?: mutableMapOf<Int, MutableSet<Player>>().also { target.attr[CURSE_BASE_DRAIN_ATTR] = it }

    private fun cursedTargets(caster: Player): MutableMap<AncientCurse, MutableSet<Pawn>> =
        caster.attr[CURSED_TARGETS_ATTR] ?: mutableMapOf<AncientCurse, MutableSet<Pawn>>().also { caster.attr[CURSED_TARGETS_ATTR] = it }

    private fun resetLeechBoosts(player: Player) {
        player.attr.remove(LEECH_BOOST_PCT_ATTR)
    }

    private fun resetTurmoilBonus(player: Player) {
        player.attr.remove(TURMOIL_BONUS_ATTR)
    }

    /** Novite's first successful Turmoil melee hit snapshots the opponent's base levels. */
    private fun establishTurmoilBonus(
        player: Player,
        target: Pawn,
    ) {
        val bonuses = player.attr[TURMOIL_BONUS_ATTR] ?: mutableMapOf<Int, Int>().also { player.attr[TURMOIL_BONUS_ATTR] = it }
        val attack = maxLevel(target, Skills.ATTACK, NpcSkills.ATTACK).coerceAtMost(TURMOIL_TARGET_LEVEL_CAP)
        val strength = maxLevel(target, Skills.STRENGTH, NpcSkills.STRENGTH).coerceAtMost(TURMOIL_TARGET_LEVEL_CAP)
        val defence = maxLevel(target, Skills.DEFENCE, NpcSkills.DEFENCE).coerceAtMost(TURMOIL_TARGET_LEVEL_CAP)
        bonuses[Skills.ATTACK] = attack * 15 / 100
        bonuses[Skills.STRENGTH] = strength * 10 / 100
        bonuses[Skills.DEFENCE] = defence * 15 / 100
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
     * One escalation step of a Sap/Leech drain on [target]'s stat (see the stat-model KDoc above):
     * the first proc from [caster] applies the base modifier; every later proc removes one percent
     * of the max level as a real drain, up to `capPct - basePct` percent, never below that floor.
     * Returns false only when the target is already fully drained ("has no effect").
     */
    private fun escalateDrain(
        caster: Player,
        curse: AncientCurse,
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
        basePct: Int,
        capPct: Int,
    ): Boolean {
        val max = maxLevel(target, playerSkill, npcSkill)
        if (max <= 0) return false
        // Owner 2026-09-19 ("it shows the message but does not drain"): the first proc used to register only the invisible base
        // modifier, and with the 45-second proc cooldown the first visible level came 45 s later - in a normal fight never.
        // Every proc (the first included) now also removes one real level; the cap and the one-proc-per-45-s rule are unchanged.
        baseDrainState(target).getOrPut(playerSkill) { mutableSetOf() }.add(caster)
        cursedTargets(caster).getOrPut(curse) { mutableSetOf() }.add(target)
        val state = drainState(target)
        val extra = state[playerSkill] ?: 0
        val maxExtra = capPct - basePct
        val changed =
            when {
                extra >= maxExtra -> false
                else -> {
                    state[playerSkill] = extra + 1
                    val floor = max - (max * maxExtra / 100.0).toInt().coerceAtLeast(1)
                    val step = (max / 100.0).toInt().coerceAtLeast(1)
                    val now = currentLevel(target, playerSkill, npcSkill)
                    val wanted = maxOf(floor, now - step)
                    if (now > wanted) {
                        when (target) {
                            is Player -> target.skills.alterCurrentLevel(playerSkill, -(now - wanted))
                            is Npc -> target.stats.alterCurrentLevel(npcSkill, -(now - wanted))
                        }
                    }
                    true
                }
            }
        if (target is Player) syncStatVarbits(target)
        return changed
    }

    private val leechSkills = mapOf(
        Skills.ATTACK to AncientCurse.LEECH_ATTACK,
        Skills.STRENGTH to AncientCurse.LEECH_STRENGTH,
        Skills.DEFENCE to AncientCurse.LEECH_DEFENCE,
        Skills.RANGED to AncientCurse.LEECH_RANGED,
        Skills.MAGIC to AncientCurse.LEECH_MAGIC,
    )

    /** The Leech's base 5 % self-boost: a combat modifier that exists exactly while the Leech is active. */
    fun leechMultiplier(player: Player, skill: Int): Double {
        val curse = leechSkills[skill] ?: return 1.0
        return if (isCurseActive(player, curse)) 1.0 + LEECH_BOOST_BASE_PCT / 100.0 else 1.0
    }

    /**
     * Escalated self-boost: the first proc only establishes the base modifier; each later proc adds
     * one real level up to `(cap - base)` percent of the max level, which then decays like a potion.
     */
    private fun escalateBoost(
        player: Player,
        skill: Int,
    ) {
        val state = boostState(player)
        val maxExtra = LEECH_BOOST_CAP_PCT - LEECH_BOOST_BASE_PCT
        // Every proc, the first included, is a real +1 level (owner 2026-09-19, see escalateDrain).
        val current = state[skill] ?: 0
        when {
            current >= maxExtra -> {}
            else -> {
                state[skill] = current + 1
                val max = player.skills.getMaxLevel(skill)
                val cap = (max * maxExtra / 100.0).toInt().coerceAtLeast(1)
                if (player.skills.getCurrentLevel(skill) < max + cap) player.skills.alterCurrentLevel(skill, 1, capValue = cap)
            }
        }
        syncStatVarbits(player)
    }

    /**
     * Owner balance decision (2026-09-18, P0 buglist): Sap and Leech curses were draining far too much. Each Sap/Leech
     * curse may proc at most once per [SAP_LEECH_COOLDOWN_TICKS] (45 s) per caster, and every proc drains at most one
     * level (the escalation step), boosted stats included. Leech Special Attack is limited separately to
     * [LEECH_SPECIAL_PCT] % once per [LEECH_SPECIAL_COOLDOWN_TICKS] (60 s). These are owner values, not 2011 values.
     */
    const val SAP_LEECH_COOLDOWN_TICKS = 75
    const val LEECH_SPECIAL_COOLDOWN_TICKS = 100
    const val LEECH_SPECIAL_PCT = 5
    private val CURSE_PROC_CYCLE_ATTR = AttributeKey<MutableMap<AncientCurse, Int>>()

    private fun cooldownTicks(curse: AncientCurse): Int =
        if (curse == AncientCurse.LEECH_SPECIAL_ATTACK) LEECH_SPECIAL_COOLDOWN_TICKS else SAP_LEECH_COOLDOWN_TICKS

    internal fun offCooldown(attacker: Player, curse: AncientCurse): Boolean {
        val last = attacker.attr[CURSE_PROC_CYCLE_ATTR]?.get(curse) ?: return true
        return attacker.world.currentCycle - last >= cooldownTicks(curse)
    }

    private fun startCooldown(attacker: Player, curse: AncientCurse) {
        val map = attacker.attr[CURSE_PROC_CYCLE_ATTR] ?: mutableMapOf<AncientCurse, Int>().also { attacker.attr[CURSE_PROC_CYCLE_ATTR] = it }
        map[curse] = attacker.world.currentCycle
    }

    private fun sap(
        attacker: Player,
        curse: AncientCurse,
        target: Pawn,
        vararg skills: Pair<Int, Int>,
    ): Boolean = skills.map { (p, n) -> escalateDrain(attacker, curse, target, p, n, SAP_BASE_PCT, SAP_CAP_PCT) }.any { it }

    private fun leech(
        attacker: Player,
        curse: AncientCurse,
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
    ) {
        escalateDrain(attacker, curse, target, playerSkill, npcSkill, LEECH_DRAIN_BASE_PCT, LEECH_DRAIN_CAP_PCT)
        escalateBoost(attacker, playerSkill)
    }

    /**
     * Combat hook for Sap/Leech/Soul Split, called once per landed hit from the single shared
     * `dealHit` entry point every combat style routes through (see combat/PawnExt.kt). Novite's
     * order is important: Soul Split always fires; only one style-matching Sap/Leech/Turmoil
     * effect can fire; Defence/Energy/Special and Sap Spirit are checked afterward.
     */
    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
        style: CombatClass = CombatClass.MELEE,
    ) {
        if (attacker !is Player || damage <= 0) return
        val active = activeCurses(attacker)
        if (active.isEmpty() && !isTurmoilActive(attacker)) return
        if (active.contains(AncientCurse.SOUL_SPLIT)) {
            applySoulSplit(attacker, target, damage)
        }

        fun tryCurse(curse: AncientCurse): Boolean {
            if (!active.contains(curse) || !offCooldown(attacker, curse) || !attacker.world.percentChance(curse.activationChancePercent)) return false
            startCooldown(attacker, curse)
            applySapLeech(attacker, target, curse)
            return true
        }

        // Novite Prayer.java: Turmoil is evaluated first on a melee hit and snapshots the
        // opponent's base levels after its 1-in-5 roll succeeds.
        if (style == CombatClass.MELEE && isTurmoilActive(attacker)) {
            if (attacker.world.percentChance(20.0)) {
                establishTurmoilBonus(attacker, target)
                return
            }
        }

        val styleEffectTriggered =
            when (style) {
                CombatClass.MELEE ->
                    // The donor uses an else-if chain: when Sap Warrior is active, a failed Sap
                    // roll does not fall through to Leech Attack/Strength. Turmoil has the same
                    // gate above; only the style-independent curses may follow a failed roll.
                    if (isTurmoilActive(attacker)) {
                        false
                    } else {
                        when {
                            active.contains(AncientCurse.SAP_WARRIOR) -> tryCurse(AncientCurse.SAP_WARRIOR)
                            active.contains(AncientCurse.LEECH_ATTACK) -> tryCurse(AncientCurse.LEECH_ATTACK)
                            active.contains(AncientCurse.LEECH_STRENGTH) -> tryCurse(AncientCurse.LEECH_STRENGTH)
                            else -> false
                        }
                    }
                CombatClass.RANGED ->
                    when {
                        active.contains(AncientCurse.SAP_RANGER) -> tryCurse(AncientCurse.SAP_RANGER)
                        active.contains(AncientCurse.LEECH_RANGED) -> tryCurse(AncientCurse.LEECH_RANGED)
                        else -> false
                    }
                CombatClass.MAGIC ->
                    when {
                        active.contains(AncientCurse.SAP_MAGE) -> tryCurse(AncientCurse.SAP_MAGE)
                        active.contains(AncientCurse.LEECH_MAGIC) -> tryCurse(AncientCurse.LEECH_MAGIC)
                        else -> false
                    }
                else -> false
            }
        if (styleEffectTriggered) return

        // Novite's style-independent fall-through order. This intentionally permits only one
        // proc per landed hit, even when several compatible curses are active.
        when {
            tryCurse(AncientCurse.LEECH_DEFENCE) -> Unit
            tryCurse(AncientCurse.LEECH_ENERGY) -> Unit
            tryCurse(AncientCurse.LEECH_SPECIAL_ATTACK) -> Unit
            tryCurse(AncientCurse.SAP_SPIRIT) -> Unit
        }
    }

    /**
     * Soul Split's real visual, PROVEN from Novite `Player.handleSoulSplit`: an outgoing
     * projectile (2263) from caster to target, a graphic (2264) on the target one tick later, and
     * a return projectile (2263) from target back to caster - the "souls" travelling out and back.
     * Timing is the exact fixed 667 projectile route used by Novite's `handleSoulSplit`; the
     * outgoing and one-tick-later return packets use the same start/end heights and delay.
     */
    private const val SOUL_SPLIT_PROJECTILE_GFX = 2263
    private const val SOUL_SPLIT_TARGET_GFX = 2264

    private fun applySoulSplit(
        attacker: Player,
        target: Pawn,
        damage: Int,
    ) {
        attacker.world.spawn(
            attacker.createProjectile(
                target,
                SOUL_SPLIT_PROJECTILE_GFX,
                CURSE_PROJECTILE_START_HEIGHT,
                CURSE_PROJECTILE_END_HEIGHT,
                angle = 0,
                steepness = 0,
                delay = CURSE_PROJECTILE_DELAY,
                lifespan = CURSE_PROJECTILE_LIFESPAN,
            ),
        )
        attacker.heal((damage * 0.2).toInt().coerceAtLeast(0))
        if (target is Player) target.decreasePrayerPoints(damage / 5)
        attacker.playSound(SOUL_SPLIT_HIT_SOUND, volume = Prayers.PRAYER_SOUND_VOLUME)
        // Graphic packet delays are 20 ms client cycles; one game tick is 30 cycles.
        target.graphic(SOUL_SPLIT_TARGET_GFX, delay = 30)
        attacker.queue {
            wait(1)
            if (!attacker.isDead()) {
                attacker.playSound(SOUL_SPLIT_RETURN_SOUND, volume = Prayers.PRAYER_SOUND_VOLUME)
                attacker.world.spawn(
                    target.createProjectile(
                        attacker,
                        SOUL_SPLIT_PROJECTILE_GFX,
                        CURSE_PROJECTILE_START_HEIGHT,
                        CURSE_PROJECTILE_END_HEIGHT,
                        angle = 0,
                        steepness = 0,
                        delay = CURSE_PROJECTILE_DELAY,
                        lifespan = CURSE_PROJECTILE_LIFESPAN,
                    ),
                )
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
        curse.projectileGraphic?.let {
            attacker.world.spawn(
                attacker.createProjectile(
                    target,
                    it,
                    CURSE_PROJECTILE_START_HEIGHT,
                    CURSE_PROJECTILE_END_HEIGHT,
                    angle = 0,
                    steepness = 0,
                    delay = CURSE_PROJECTILE_DELAY,
                    lifespan = CURSE_PROJECTILE_LIFESPAN,
                ),
            )
        }
        curse.targetGraphic?.let { target.graphic(it, delay = 30) }
        when (curse) {
            AncientCurse.SAP_WARRIOR ->
                if (sap(attacker, curse, target, Skills.ATTACK to NpcSkills.ATTACK, Skills.STRENGTH to NpcSkills.STRENGTH, Skills.DEFENCE to NpcSkills.DEFENCE)) {
                    curseMessages(attacker, target, "Attack, Strength and Defence")
                }
            AncientCurse.SAP_RANGER ->
                if (sap(attacker, curse, target, Skills.RANGED to NpcSkills.RANGED, Skills.DEFENCE to NpcSkills.DEFENCE)) {
                    curseMessages(attacker, target, "Ranged and Defence")
                }
            AncientCurse.SAP_MAGE ->
                if (sap(attacker, curse, target, Skills.MAGIC to NpcSkills.MAGIC, Skills.DEFENCE to NpcSkills.DEFENCE)) {
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
            AncientCurse.LEECH_ATTACK -> leech(attacker, curse, target, Skills.ATTACK, NpcSkills.ATTACK).also { leechMessages(attacker, target, "Attack") }
            AncientCurse.LEECH_RANGED -> leech(attacker, curse, target, Skills.RANGED, NpcSkills.RANGED).also { leechMessages(attacker, target, "Ranged") }
            AncientCurse.LEECH_MAGIC -> leech(attacker, curse, target, Skills.MAGIC, NpcSkills.MAGIC).also { leechMessages(attacker, target, "Magic") }
            AncientCurse.LEECH_DEFENCE -> leech(attacker, curse, target, Skills.DEFENCE, NpcSkills.DEFENCE).also { leechMessages(attacker, target, "Defence") }
            AncientCurse.LEECH_STRENGTH -> leech(attacker, curse, target, Skills.STRENGTH, NpcSkills.STRENGTH).also { leechMessages(attacker, target, "Strength") }
            AncientCurse.LEECH_ENERGY ->
                if (target is Player) {
                    if (target.runEnergy <= 0.0) {
                        curseNoEffectMessage(attacker, target)
                    } else {
                        attacker.runEnergy = if (attacker.runEnergy > 90.0) 100.0 else attacker.runEnergy + 10.0
                        target.runEnergy = if (target.runEnergy > 10.0) target.runEnergy - 10.0 else 0.0
                        leechMessages(attacker, target, "run energy")
                    }
                }
            AncientCurse.LEECH_SPECIAL_ATTACK ->
                if (target is Player) {
                    if (AttackTab.getEnergy(target) <= 0) {
                        curseNoEffectMessage(attacker, target)
                    } else {
                        AttackTab.setEnergy(target, (AttackTab.getEnergy(target) - LEECH_SPECIAL_PCT).coerceAtLeast(0))
                        AttackTab.setEnergy(attacker, (AttackTab.getEnergy(attacker) + LEECH_SPECIAL_PCT).coerceAtMost(100))
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

    private fun curseNoEffectMessage(
        attacker: Player,
        target: Pawn,
    ) {
        attacker.filterableMessage("Your opponent has been weakened so much that your curse has no effect.")
        if (target is Player) target.filterableMessage("Your opponent's curse has no effect.")
    }

    // ---- Wrath / Deflect -------------------------------------------------------------------

    /** Wrath's death-explosion graphic (centre, PROVEN from Novite `Player.sendDeath`). */
    private const val WRATH_CENTRE_GFX = 2259
    const val WRATH_RING_GFX = 2260

    /** Exact Novite `World.sendProjectile` values for Wrath's expanding ring. */
    private const val WRATH_PROJECTILE_START_HEIGHT = 41
    private const val WRATH_PROJECTILE_DIAGONAL_START_HEIGHT = 24
    private const val WRATH_PROJECTILE_END_HEIGHT = 0
    private const val WRATH_PROJECTILE_DELAY = 35
    private const val WRATH_PROJECTILE_LIFESPAN = 41
    private const val WRATH_PROJECTILE_ANGLE = 30

    private val WRATH_OUTER_OFFSETS =
        listOf(
            2 to 2,
            2 to 0,
            2 to -2,
            -2 to 2,
            -2 to 0,
            -2 to -2,
            0 to 2,
            0 to -2,
        )

    private val WRATH_INNER_OFFSETS =
        listOf(
            1 to 1,
            1 to -1,
            -1 to 1,
            -1 to -1,
        )

    /**
     * Wrath: on the WEARER's own death, up to 300% of the Prayer level in a 5x5 area - called from
     * the curse plugin's `on_player_death`, before curses are cleared. Only pawns that could legally
     * be hit are struck: NPCs always, players only where the two could fight (Wilderness rules).
     * The 300% multiplier and centre graphic 2259 are PROVEN from Novite `Player.sendDeath`
     * (`Utils.getRandom(skills.getLevelForXp(Skills.PRAYER) * 3)`); this file previously used 2.5,
     * which was a bug carried over from documentation rather than this source.
     */
    /** Exposed for deterministic testing of the sourced 300% multiplier without mocking world dispatch. */
    internal fun wrathMaxDamage(player: Player): Int = player.skills.getMaxLevel(Skills.PRAYER) * 3 / 10

    fun wrathExplosion(player: Player) {
        val damage = wrathMaxDamage(player)
        if (damage <= 0) return
        val world = player.world
        val origin = Tile(player.tile)

        // Novite sends the outer ring as eight map projectiles immediately on death. The first
        // diagonal uses start height 24; the remaining seven use 41.
        WRATH_OUTER_OFFSETS.forEachIndexed { index, (dx, dz) ->
            val destination = origin.transform(dx, dz)
            val startHeight = if (index == 0) WRATH_PROJECTILE_DIAGONAL_START_HEIGHT else WRATH_PROJECTILE_START_HEIGHT
            world.spawn(
                player.createProjectile(
                    destination,
                    WRATH_RING_GFX,
                    startHeight,
                    WRATH_PROJECTILE_END_HEIGHT,
                    WRATH_PROJECTILE_ANGLE,
                    0,
                    WRATH_PROJECTILE_DELAY,
                    WRATH_PROJECTILE_LIFESPAN,
                ),
            )
        }

        // Novite schedules the actual blast one world tick later. This timing matters: the centre
        // graphic, hit and twelve impact tiles are all part of the delayed explosion, not the
        // initial projectile launch.
        // Owner 2026-09-18: Wrath left no hit on players. The blast ran on the dying player's own queue, which the
        // death routine clears, and each target was checked against a player who was already dead. Like Novite's
        // WorldTask it now runs on the world queue, and eligibility is decided from the death tile: in multi-combat
        // every attackable pawn within two tiles, in single-combat only the killer (Novite `Player.sendDeath`).
        val multi = origin.isMulti(world)
        val killer = player.attr[gg.rsmod.game.model.attr.KILLER_ATTR]?.get() as? Player
        world.queue {
            wait(1)
            player.graphic(WRATH_CENTRE_GFX)

            if (multi) {
                world.npcs.forEach {
                    if (!it.isDead() && it.def.isAttackable() && it.combatDef.lifepoints != -1 &&
                        it.tile.isWithinRadius(origin, 2)
                    ) {
                        it.hit(damage = world.random(damage))
                    }
                }
                world.players.forEach {
                    if (it != player && !it.isDead() && it.isOnline &&
                        it.tile.isWithinRadius(origin, 2) &&
                        gg.rsmod.plugins.content.mechanics.pvp.AreaState.canDeathEffectHit(player, it)
                    ) {
                        it.hit(damage = world.random(damage))
                    }
                }
            } else if (killer != null && killer != player && !killer.isDead() && killer.isOnline && killer.tile.isWithinRadius(origin, 2) &&
                gg.rsmod.plugins.content.mechanics.pvp.AreaState.canDeathEffectHit(player, killer)
            ) {
                killer.hit(damage = world.random(damage))
            }

            (WRATH_OUTER_OFFSETS + WRATH_INNER_OFFSETS).forEach { (dx, dz) ->
                world.spawn(TileGraphic(origin.transform(dx, dz), id = WRATH_RING_GFX, height = 0))
            }
        }
    }

    /** Melee/Ranged/Magic only - Deflect Summoning keeps its block-only behaviour. */
    private val DEFLECT_STYLE =
        mapOf(
            CombatClass.MELEE to AncientCurse.DEFLECT_MELEE,
            CombatClass.RANGED to AncientCurse.DEFLECT_MISSILES,
            CombatClass.MAGIC to AncientCurse.DEFLECT_MAGIC,
        )

    fun deflects(target: Pawn, style: CombatClass): Boolean =
        target is Player && DEFLECT_STYLE[style]?.let { isCurseActive(target, it) } == true

    /** Called with the roll BEFORE protection. NPC protection must not discard that roll. */
    fun deflectDamageTaken(attacker: Pawn, target: Pawn, style: CombatClass, damage: Int): Int =
        if (!deflects(target, style)) damage else if (attacker is Player) damage * 6 / 10 else 0

    /**
     * Deflect reflection: reflect 10% of qualifying damage, with no random roll and no recoil
     * threshold. Reflected damage is dealt raw so it can never re-trigger a Deflect on the original
     * attacker (no recursion).
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
        // Owner balance decision (2026-09-18): at most a 15 % chance to reflect, and at most one reflected hit per
        // attack - multi-hit attacks (claws, double hits, multi-target spells) landing on the same tick reflect once.
        if (!target.world.percentChance(DEFLECT_REFLECT_CHANCE_PCT)) return
        if (target.attr[LAST_DEFLECT_CYCLE_ATTR] == target.world.currentCycle) return
        val reflected = (damage * 0.10).toInt()
        // Novite Player.java:1267-1297 reflects whenever the 10% is above zero. The old `< 10` was
        // written for x10 life points and, after the 1:1 migration, blocked every hit under 100.
        if (reflected <= 0) return
        curse.reflectAnimation?.let { target.animate(it) }
        curse.reflectGraphic?.let { target.graphic(it) }
        target.attr[LAST_DEFLECT_CYCLE_ATTR] = target.world.currentCycle
        attacker.hit(damage = reflected)
    }

    const val DEFLECT_REFLECT_CHANCE_PCT = 15.0
    private val LAST_DEFLECT_CYCLE_ATTR = AttributeKey<Int>()
}
