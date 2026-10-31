package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.game.tools.importer.RuinousPowersCacheTool
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.createProjectile

/**
 * N463 Ruinous Powers, the third prayer book (owner 2026-10-31: "ruinous prayers volledig werkend alles correct"; decisions: the
 * Deadman 2026 version, a third book next to Normal and Curses, unlocked by Prayer level alone, switched at the altars and not in
 * combat, Protect Item not in PvP). The tab is interface 271 in the curse layout with the Ruinous flag varbit set
 * ([RuinousPowersCacheTool]); this object owns the server side: clicks, quick prayers, drain, overheads and every effect.
 *
 * Effects (OSRS item param 1754 texts, the in-game tooltips, and the OSRS Wiki pages of each prayer):
 * - Ancient Strength / Sight / Will, Trinitas, Decimate, Annihilate, Vaporise, Intensify: the multipliers below, read by the three
 *   combat formulas. No Defence boost (the book has none).
 * - Dampen Magic / Ranged / Melee: the overheads 27-29 protect like a Protect prayer ([PrayerIcon.protects]): all of an npc's hit,
 *   x0.6 of a player's ("100% protection ... (40% in PvP)").
 * - Purge: "1/3 of damage dealt is also removed from both yours and your opponent's Prayer points."
 * - Rebuke: "Increases recoil damage by 10%" ([rebuke], every recoil effect +10% rounded down); Vindication: like Redemption (below 10% HP: heal 25% of the Prayer
 *   level, Prayer points to 0); Wrath: on death up to 25% of the base Prayer level to opponents in a 5x5 area (the curse Wrath hit rules, OSRS clouds - N486).
 * - Rejuvenation: Hitpoints regenerate 3x faster; Berserker: boosted combat stats last 50% longer; Ruinous Grace: running drains no
 *   run energy (the prayer's own drain is the cost, OSRS Wiki: stamina potions do not count); Metabolise: 25 ticks after activation
 *   food delays the next attack 1 tick less.
 * - Fumus' Vow: a poison an attack inflicts deals its poison damage at once; Umbra's Vow: the opponent's Defence drops by 5% of the
 *   damage dealt, at most 15% of its base level; Glacies' Vow: the same for a monster's Magic; Cruor's Vow: a kill heals 3 Hitpoints
 *   and drains 3 Prayer points.
 * - Protect Item: one extra item kept on a PvM death; a PvP death ignores it (owner: "niet in PvP").
 */
object RuinousPowers {
    /**
     * The cycle Metabolise / Berserker were switched on: both work only after 15 seconds (25 ticks) of being active (OSRS Wiki
     * "Metabolise": "must be active for at least 15 seconds in order to take effect"; "Berserker": the same).
     */
    private val METABOLISE_SINCE = AttributeKey<Int>()
    private val BERSERKER_SINCE = AttributeKey<Int>()

    /** The cycle a Cruor's Vow kill was last credited on this target (one credit per death, also with several hits landing). */
    private val CRUOR_CREDITED = AttributeKey<Int>()

    const val METABOLISE_DELAY_TICKS = 25
    private const val VOW_DRAIN_CAP_PCT = 15
    private const val CRUOR_AMOUNT = 3

    fun isBook(player: Player): Boolean = AncientCurses.getBook(player) == AncientCurses.PrayerBook.RUINOUS

    fun isActive(
        player: Player,
        prayer: RuinousPrayer,
    ): Boolean = player.getVarbit(prayer.varbit) != 0

    fun active(player: Player): List<RuinousPrayer> = RuinousPrayer.values.filter { isActive(player, it) }

    fun anyActive(player: Player): Boolean = player.getVarp(RuinousPowersCacheTool.ACTIVE_VARP) != 0

    /** Total drain of the active Ruinous prayers in [Prayer.drainEffect] units; a prayer switched on this tick does not drain. */
    fun drainEffect(player: Player): Int =
        if (!anyActive(player)) 0 else active(player).filter { !Prayers.activatedThisTick(player, it) }.sumOf { it.drainEffect }

    // ---- book / login ----------------------------------------------------------------------

    /**
     * Login (and a book switch): with the Ruinous book shown, the saved active bits stand and the runtime mirrors (Protect Item,
     * Metabolise start, overhead) are rebuilt; on another book any stale Ruinous bit from an older save is cleared.
     */
    fun restore(player: Player) {
        if (!isBook(player)) {
            if (anyActive(player)) player.setVarp(RuinousPowersCacheTool.ACTIVE_VARP, 0)
            return
        }
        if (isActive(player, RuinousPrayer.METABOLISE)) player.attr[METABOLISE_SINCE] = player.world.currentCycle
        if (isActive(player, RuinousPrayer.BERSERKER)) player.attr[BERSERKER_SINCE] = player.world.currentCycle
        if (isActive(player, RuinousPrayer.PROTECT_ITEM)) player.attr[PROTECT_ITEM_ATTR] = true
        setOverhead(player)
    }

    // ---- clicks ---------------------------------------------------------------------------

    /** The refusal for switching [prayer] on now, or null. Sends the varbit back so the client's optimistic tick is undone. */
    private fun refusal(
        player: Player,
        prayer: RuinousPrayer,
    ): String? =
        when {
            player.skills.getMaxLevel(Skills.PRAYER) < prayer.level -> "You need a Prayer level of at least ${prayer.level} to use ${prayer.named}."
            prayer.group == RuinousPrayer.Group.OVERHEAD && Prayers.overheadsDisabled(player) -> "You cannot use overhead prayers right now."
            else -> null
        }

    /** A click on grid slot [slot] (271:8) while the Ruinous book is shown; handled on the click's own tick like the other books. */
    fun click(
        player: Player,
        slot: Int,
    ) {
        val prayer = RuinousPrayer.bySlot(slot) ?: return
        if (!isBook(player)) return
        if (player.lock.delaysPrayer()) {
            player.queue(TaskPriority.STRONG) {
                terminateAction = { player.syncVarp(RuinousPowersCacheTool.ACTIVE_VARP) }
                while (player.lock.delaysPrayer()) wait(1)
                toggle(player, prayer)
            }
            return
        }
        toggle(player, prayer)
    }

    fun toggle(
        player: Player,
        prayer: RuinousPrayer,
    ) {
        player.syncVarp(RuinousPowersCacheTool.ACTIVE_VARP)
        if (player.isDead() || !player.lock.canUsePrayer()) return
        if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
                player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.PRAYER,
            )
        ) {
            return
        }
        if (isActive(player, prayer)) {
            deactivate(player, prayer)
        } else {
            refusal(player, prayer)?.let {
                // N488: the OSRS refusal sound with the message (prayer_op synth 2673).
                if (player.skills.getMaxLevel(Skills.PRAYER) < prayer.level) Prayers.refuseSound(player)
                player.message(it)
                return
            }
            if (player.getCurrentPrayerPoints() <= 0) {
                Prayers.refuseNoPoints(player)
                return
            }
            activate(player, prayer)
        }
        player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
    }

    fun activate(
        player: Player,
        prayer: RuinousPrayer,
        playSound: Boolean = true,
    ) {
        if (isActive(player, prayer)) return
        RuinousPrayer.values.filter { prayer.conflictsWith(it) && isActive(player, it) }.forEach { deactivate(player, it, playSound) }
        player.setVarbit(prayer.varbit, 1)
        Prayers.markActivated(player, prayer)
        if (prayer == RuinousPrayer.METABOLISE) player.attr[METABOLISE_SINCE] = player.world.currentCycle
        if (prayer == RuinousPrayer.BERSERKER) player.attr[BERSERKER_SINCE] = player.world.currentCycle
        if (prayer == RuinousPrayer.PROTECT_ITEM) player.attr[PROTECT_ITEM_ATTR] = true
        // OSRS Wiki "Rejuvenation": switching it on restarts the Hitpoints regeneration timer (the next point in 20 seconds).
        if (prayer == RuinousPrayer.REJUVENATION) gg.rsmod.plugins.content.mechanics.restoration.RestorationRates.restartLifeTimer(player)
        if (playSound) player.playSound(prayer.sound, volume = Prayers.PRAYER_SOUND_VOLUME)
        setOverhead(player)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) {
            "ruinous on ${player.username} ${prayer.name} sound=${prayer.sound} points=${player.getCurrentPrayerPoints()}"
        }
    }

    fun deactivate(
        player: Player,
        prayer: RuinousPrayer,
        playSound: Boolean = true,
    ) {
        if (!isActive(player, prayer)) return
        player.setVarbit(prayer.varbit, 0)
        afterOff(player, prayer)
        if (playSound) player.playSound(Sfx.CANCEL_PRAYER, volume = Prayers.PRAYER_SOUND_VOLUME)
        setOverhead(player)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous off ${player.username} ${prayer.name}" }
    }

    /** Every Ruinous prayer off at once, silently (death, no Prayer points left, book switch, Nex, quick prayers). */
    fun deactivateAll(player: Player) {
        if (!anyActive(player)) return
        val wereOn = active(player)
        player.setVarp(RuinousPowersCacheTool.ACTIVE_VARP, 0)
        wereOn.forEach { afterOff(player, it) }
        setOverhead(player)
    }

    /** The runtime side of [prayer] going off (one prayer or all at once). */
    private fun afterOff(
        player: Player,
        prayer: RuinousPrayer,
    ) {
        when (prayer) {
            RuinousPrayer.PROTECT_ITEM -> player.attr[PROTECT_ITEM_ATTR] = false
            RuinousPrayer.BERSERKER -> player.attr.remove(BERSERKER_SINCE)
            // OSRS Wiki "Rejuvenation": switching it off restarts the timer too (the next point at the normal 60 seconds).
            RuinousPrayer.REJUVENATION -> gg.rsmod.plugins.content.mechanics.restoration.RestorationRates.restartLifeTimer(player)
            RuinousPrayer.METABOLISE -> {
                // OSRS Wiki "Metabolise": "If deactivated, the player's action delay is reset to their weapon's attack speed or left
                // unchanged, whichever is higher" - no flicking it off and on for a shorter eat-and-attack.
                if (player.attr.has(METABOLISE_SINCE)) {
                    val speed = gg.rsmod.plugins.content.combat.CombatConfigs.getAttackDelay(player)
                    val delay = gg.rsmod.game.model.timer.ATTACK_DELAY
                    val left = if (player.timers.has(delay)) player.timers[delay] else 0
                    if (speed > left) player.timers[delay] = speed
                }
                player.attr.remove(METABOLISE_SINCE)
            }
            else -> {}
        }
    }

    /** Sever / Breach / Nex: the Dampen prayers drop like the Protect prayers and Deflect curses. */
    fun deactivateProtection(player: Player) {
        listOf(RuinousPrayer.DAMPEN_MAGIC, RuinousPrayer.DAMPEN_RANGED, RuinousPrayer.DAMPEN_MELEE).forEach { deactivate(player, it) }
    }

    private fun setOverhead(player: Player) {
        if (!isBook(player)) return
        val icon = active(player).firstOrNull { it.icon != null }?.icon ?: PrayerIcon.NONE
        if (player.prayerIcon != icon.id) {
            player.prayerIcon = icon.id
            player.addBlock(UpdateBlockType.APPEARANCE)
        }
    }

    // ---- quick prayers -------------------------------------------------------------------

    fun isQuickSelected(
        player: Player,
        prayer: RuinousPrayer,
    ): Boolean = player.getVarbit(prayer.quickVarbit) != 0

    fun selectedQuick(player: Player): List<RuinousPrayer> = RuinousPrayer.values.filter { isQuickSelected(player, it) }

    /** 271:42 in quick-select mode: the same exclusions as the live book; a prayer above the player's level cannot be picked. */
    fun selectQuick(
        player: Player,
        slot: Int,
    ) {
        val prayer = RuinousPrayer.bySlot(slot) ?: return
        if (!isBook(player) || player.isDead() || !player.lock.canUsePrayer()) return
        if (isQuickSelected(player, prayer)) {
            player.setVarbit(prayer.quickVarbit, 0)
            return
        }
        if (player.skills.getMaxLevel(Skills.PRAYER) < prayer.level) {
            player.setVarbit(prayer.quickVarbit, 0)
            player.message("You need a Prayer level of at least ${prayer.level} to use ${prayer.named}.")
            return
        }
        RuinousPrayer.values.filter { prayer.conflictsWith(it) && isQuickSelected(player, it) }.forEach { player.setVarbit(it.quickVarbit, 0) }
        player.setVarbit(prayer.quickVarbit, 1)
    }

    /** The orb's "Turn quick prayers on/off" with the Ruinous book shown (the normal book's rules and messages). */
    fun toggleQuick(player: Player) {
        val selected = selectedQuick(player)
        val active = active(player)
        when {
            selected.isEmpty() -> {
                player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
                player.message("You haven't selected any quick-prayers.")
            }
            player.getCurrentPrayerPoints() <= 0 -> {
                player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
                player.message("You have run out of prayer points, you can recharge at an altar.")
            }
            active.toSet() == selected.toSet() -> {
                deactivateAll(player)
                player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, 0)
            }
            else -> {
                deactivateAll(player)
                selected.filter { refusal(player, it) == null }.forEach { activate(player, it, playSound = false) }
                player.setVarc(Prayers.QUICK_PRAYERS_ACTIVE_VARC, if (anyActive(player)) 1 else 0)
            }
        }
    }

    // ---- combat multipliers ----------------------------------------------------------------

    private fun on(
        player: Player,
        prayer: RuinousPrayer,
    ) = anyActive(player) && isBook(player) && isActive(player, prayer)

    fun meleeAttack(player: Player): Double =
        when {
            on(player, RuinousPrayer.INTENSIFY) -> 1.50
            on(player, RuinousPrayer.DECIMATE) -> 1.30
            on(player, RuinousPrayer.ANCIENT_STRENGTH) -> 1.20
            on(player, RuinousPrayer.TRINITAS) -> 1.15
            else -> 1.0
        }

    fun meleeStrength(player: Player): Double =
        when {
            on(player, RuinousPrayer.DECIMATE) -> 1.27
            on(player, RuinousPrayer.ANCIENT_STRENGTH) -> 1.20
            on(player, RuinousPrayer.TRINITAS) -> 1.15
            else -> 1.0
        }

    fun rangedAttack(player: Player): Double =
        when {
            on(player, RuinousPrayer.INTENSIFY) -> 1.50
            on(player, RuinousPrayer.ANNIHILATE) -> 1.30
            on(player, RuinousPrayer.ANCIENT_SIGHT) -> 1.20
            on(player, RuinousPrayer.TRINITAS) -> 1.15
            else -> 1.0
        }

    fun rangedStrength(player: Player): Double =
        when {
            on(player, RuinousPrayer.ANNIHILATE) -> 1.27
            on(player, RuinousPrayer.ANCIENT_SIGHT) -> 1.20
            on(player, RuinousPrayer.TRINITAS) -> 1.15
            else -> 1.0
        }

    fun magicAttack(player: Player): Double =
        when {
            on(player, RuinousPrayer.INTENSIFY) -> 1.50
            on(player, RuinousPrayer.VAPORISE) -> 1.30
            on(player, RuinousPrayer.ANCIENT_WILL) -> 1.20
            on(player, RuinousPrayer.TRINITAS) -> 1.15
            else -> 1.0
        }

    /** Extra magic damage, added to the magic damage bonus like Augury's 0.04. */
    fun magicDamage(player: Player): Double =
        when {
            on(player, RuinousPrayer.VAPORISE) -> 0.04
            on(player, RuinousPrayer.ANCIENT_WILL) -> 0.03
            on(player, RuinousPrayer.TRINITAS) -> 0.02
            else -> 0.0
        }

    // ---- other effects ---------------------------------------------------------------------

    fun rejuvenation(player: Player): Boolean = on(player, RuinousPrayer.REJUVENATION)

    /** Berserker: boosted combat stats last 50% longer, once the prayer has been on for 15 seconds (OSRS Wiki). */
    fun berserker(player: Player): Boolean {
        if (!on(player, RuinousPrayer.BERSERKER)) return false
        val since = player.attr[BERSERKER_SINCE] ?: return false
        return player.world.currentCycle - since >= METABOLISE_DELAY_TICKS
    }

    /** Ruinous Grace: run energy is not drained while it runs (with Prayer points left - at 0 every prayer is already off). */
    fun graceKeepsRunEnergy(player: Player): Boolean = on(player, RuinousPrayer.RUINOUS_GRACE) && player.getCurrentPrayerPoints() > 0

    /** Metabolise: the attack delay a food adds, one tick less once the prayer has been on for 25 ticks. */
    fun foodAttackDelay(
        player: Player,
        delay: Int,
    ): Int {
        if (!on(player, RuinousPrayer.METABOLISE)) return delay
        val since = player.attr[METABOLISE_SINCE] ?: return delay
        if (player.world.currentCycle - since < METABOLISE_DELAY_TICKS) return delay
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous metabolise ${player.username} food attack delay $delay -> ${(delay - 1).coerceAtLeast(0)}" }
        return (delay - 1).coerceAtLeast(0)
    }

    /**
     * Rebuke: OSRS Wiki "Rebuke" - "increases the amount of damage recoil effects deal (such as Vengeance) by 10%, rounded down":
     * the ring of recoil / suffering, Vengeance and the Dharok-damned reflect of the player praying it.
     */
    fun rebuke(
        target: Pawn,
        recoil: Int,
    ): Int {
        if (target !is Player || recoil <= 0 || !on(target, RuinousPrayer.REBUKE)) return recoil
        val rebuked = recoil * 11 / 10
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous rebuke ${target.username} recoil $recoil -> $rebuked" }
        return rebuked
    }

    /** Protect Item on this death: always on a PvM death, never on a PvP death (owner 2026-10-31). */
    fun protectItemCounts(
        player: Player,
        pvpDeath: Boolean,
    ): Boolean = !(pvpDeath && on(player, RuinousPrayer.PROTECT_ITEM))

    /** Fumus' Vow: after an attack of [attacker] (re)poisoned [target] at [severity], the poison damage lands at once. */
    fun afterPoisonInflicted(
        attacker: Pawn?,
        target: Pawn,
        severity: Int,
    ) {
        if (attacker !is Player || !on(attacker, RuinousPrayer.FUMUS_VOW) || target.isDead()) return
        val damage = gg.rsmod.plugins.content.mechanics.poison.Poison.getDamageForTicks(severity - 1)
        if (damage <= 0) return
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous fumus ${attacker.username} poison severity $severity -> instant hit $damage" }
        target.hit(damage = damage, type = gg.rsmod.plugins.api.HitType.POISON, delay = 0, source = attacker)
    }

    /** Once per landed hit, from the combat dispatcher (PawnExt.dealHit), next to Smite and Redemption. */
    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        vindication(target)
        if (attacker !is Player || !isBook(attacker) || damage <= 0) return
        if (on(attacker, RuinousPrayer.PURGE)) {
            val drain = damage / 3
            if (drain > 0) {
                attacker.decreasePrayerPoints(drain)
                if (target is Player) target.decreasePrayerPoints(drain)
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous purge ${attacker.username} hit $damage -> prayer -$drain (and ${(target as? Player)?.username ?: "npc"})" }
            }
        }
        if (on(attacker, RuinousPrayer.UMBRA_VOW)) vowDrain(target, if (target is Npc) NpcSkills.DEFENCE else Skills.DEFENCE, damage)
        if (on(attacker, RuinousPrayer.GLACIES_VOW) && target is Npc) vowDrain(target, NpcSkills.MAGIC, damage)
        if (on(attacker, RuinousPrayer.CRUOR_VOW) && target.getCurrentLifepoints() <= 0) {
            val cycle = attacker.world.currentCycle
            val last = target.attr[CRUOR_CREDITED]
            if (last == null || cycle - last > 10) {
                target.attr[CRUOR_CREDITED] = cycle
                attacker.heal(CRUOR_AMOUNT)
                attacker.decreasePrayerPoints(CRUOR_AMOUNT)
                gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous cruor ${attacker.username} kill: +$CRUOR_AMOUNT hitpoints, -$CRUOR_AMOUNT prayer" }
            }
        }
    }

    /** 5% of [damage] off [skill], never below 85% of the base level ("up to 15%"). */
    private fun vowDrain(
        target: Pawn,
        skill: Int,
        damage: Int,
    ) {
        val drain = damage * 5 / 100
        if (drain <= 0) return
        if (target is Npc) {
            val base = target.stats.getMaxLevel(skill)
            val floor = base - base * VOW_DRAIN_CAP_PCT / 100
            val current = target.stats.getCurrentLevel(skill)
            if (current > floor) target.stats.setCurrentLevel(skill, (current - drain).coerceAtLeast(floor))
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous vow npc ${target.id} skill $skill $current -> ${target.stats.getCurrentLevel(skill)} (hit $damage, floor $floor)" }
        } else if (target is Player) {
            val base = target.skills.getMaxLevel(skill)
            val floor = base - base * VOW_DRAIN_CAP_PCT / 100
            val current = target.skills.getCurrentLevel(skill)
            if (current > floor) target.skills.setCurrentLevel(skill, (current - drain).coerceAtLeast(floor))
            gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous vow ${target.username} skill $skill $current -> ${target.skills.getCurrentLevel(skill)} (hit $damage, floor $floor)" }
        }
    }

    /** Vindication (target side): below 10% Hitpoints heal 25% of the base Prayer level and drain all Prayer points (Redemption's rule). */
    private fun vindication(target: Pawn) {
        if (target !is Player || !on(target, RuinousPrayer.VINDICATION)) return
        val current = target.getCurrentLifepoints()
        if (current <= 0 || !Redemption.belowThreshold(current, target.getMaximumLifepoints())) return
        target.setCurrentPrayerPoints(0)
        Prayers.deactivateAll(target)
        AncientCurses.deactivateAllCurses(target)
        target.heal(target.skills.getMaxLevel(Skills.PRAYER) / 4)
        // N485: Vindication "functions exactly like Redemption" - the same graphic and heal sound.
        Redemption.playHealEffect(target)
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous vindication ${target.username} at $current hitpoints: +${target.skills.getMaxLevel(Skills.PRAYER) / 4}, prayer 0" }
    }

    /**
     * Wrath (death start): "up to 25% of your Prayer level" (base level, OSRS Wiki) to opponents in a 5x5 area - the curse Wrath's hit
     * rules - with OSRS's own look ([WRATH_VISUALS]).
     */
    fun onDeathStart(player: Player) {
        if (!on(player, RuinousPrayer.WRATH)) return
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.COMBAT) { "ruinous wrath ${player.username} max ${player.skills.getMaxLevel(Skills.PRAYER) / 4}" }
        AncientCurses.wrathExplosion(player, maxDamage = player.skills.getMaxLevel(Skills.PRAYER) / 4, visuals = WRATH_VISUALS)
    }

    /**
     * N486 (owner 2026-10-31): OSRS Wrath's white clouds (OSRS Wiki File:Wrath.gif), imported by OsrsFxImportTool batch
     * `ruinouswrath`: RuneLite gameval NEX_MUSHROOM_CLOUD_PROJANIM 2012 -> [WRATH_STREAK_GFX], NEX_MUSHROOM_CLOUD_SPOTANIM 2013 ->
     * [WRATH_CLOUD_GFX], NEX_MUSHROOM_CLOUD_SPOTANIM_SMALL 2014 -> [WRATH_SMALL_CLOUD_GFX]. The gif: on death a cloud rises on the
     * player and white streaks fly out; a moment later small clouds stand scattered round the player, about four tiles out (the
     * damage stays 5x5, the Wiki's area). [WRATH_STREAK_TARGETS] / [WRATH_SMALL_CLOUD_TILES] copy that scatter.
     */
    const val WRATH_STREAK_GFX = 3189
    const val WRATH_CLOUD_GFX = 3190
    const val WRATH_SMALL_CLOUD_GFX = 3191

    private val WRATH_STREAK_TARGETS = listOf(4 to 1, 1 to 4, -3 to 3, -4 to -1, -1 to -4, 3 to -3, 3 to 3, -3 to -3)
    private val WRATH_SMALL_CLOUD_TILES = WRATH_STREAK_TARGETS + listOf(2 to -1, -2 to 1, 1 to 2, -1 to -2)

    private const val WRATH_STREAK_HEIGHT = 40
    private const val WRATH_STREAK_DELAY = 0
    private const val WRATH_STREAK_LIFESPAN = 30

    val WRATH_VISUALS =
        object : AncientCurses.WrathVisuals {
            override fun launch(player: Player, origin: gg.rsmod.game.model.Tile) {
                player.graphic(WRATH_CLOUD_GFX)
                WRATH_STREAK_TARGETS.forEach { (dx, dz) ->
                    player.world.spawn(
                        player.createProjectile(
                            origin.transform(dx, dz), WRATH_STREAK_GFX, startHeight = WRATH_STREAK_HEIGHT, endHeight = 0, angle = 0,
                            steepness = 0, delay = WRATH_STREAK_DELAY, lifespan = WRATH_STREAK_LIFESPAN,
                        ),
                    )
                }
            }

            override fun blast(player: Player, origin: gg.rsmod.game.model.Tile) {
                WRATH_SMALL_CLOUD_TILES.forEach { (dx, dz) ->
                    player.world.spawn(gg.rsmod.game.model.TileGraphic(origin.transform(dx, dz), id = WRATH_SMALL_CLOUD_GFX, height = 0))
                }
            }
        }

    /** True while the player fights (the altars' book switch refuses then). */
    fun inCombat(player: Player): Boolean = player.timers.has(gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER)
}
