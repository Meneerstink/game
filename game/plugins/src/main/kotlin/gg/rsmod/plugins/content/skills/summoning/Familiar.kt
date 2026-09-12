package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.MovementQueue
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.game.model.attr.FAMILIAR_NPC_ID_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.path.PathRequest
import gg.rsmod.game.model.path.strategy.BFSPathFindingStrategy
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import java.lang.ref.WeakReference

/**
 * R07.1/R07.2: real summon/follow/renew/dismiss for [SummoningPouchData]'s already-existing
 * full pouch roster (Spirit Wolf through Steel Titan/Pack Yak/War Tortoise) - not just
 * registrations, an actually working core mechanic every specific familiar builds on.
 *
 * Summoning points are paid in two parts, exactly as the 2011 Knowledge Base describes: an
 * initial pouch cost plus a slow drain for as long as the familiar is out ("When you summon a
 * familiar, you will notice that your Summoning level begins to fall. Like Prayer, Summoning a
 * familiar will drain your Summoning points, which can only be regained by visiting a Summoning
 * obelisk or drinking a Summoning potion." - Summoning: The Basics, archived at 2011.rs, local
 * copy `C:\RSPS\2011RS_SUMMONING_BASICS.md`). See [drainPoints] for the rate and its evidence.
 * A familiar's lifetime is an independent per-familiar timer sourced from the
 * revision-era "Summoning - Familiars" table (see [SummoningFamiliarDefinitions] and the
 * duration assertions in `SummoningLedgerTests`), ranging from the dreadfowl's 4 minutes to the
 * rune minotaur's 151. Renewing re-arms that timer from a second pouch of the same type without
 * charging Summoning points again, matching real RS "renew" behaviour.
 *
 * Following reuses the real, already-existing [MovementQueue] every pawn's normal walking goes
 * through (the same system NPCs use to wander/chase) rather than a simplified teleport-style
 * step - real collision-respecting, animated movement, not a hack.
 */
val FAMILIAR_ATTR = AttributeKey<WeakReference<Npc>>()

/** Persisted Summoning special-move energy. This is separate from Summoning points. */
val FAMILIAR_SPECIAL_POINTS_ATTR = AttributeKey<Int>(persistenceKey = "familiar_special_points")


/** Persisted online-cycle accumulator; preserves partial special regeneration through relog. */
private val FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR = AttributeKey<Int>(persistenceKey = "familiar_special_regen_cycles")

/**
 * Persisted online-cycle accumulator for the lifetime Summoning-point drain. Persisted for the
 * same reason as the two accumulators around it: the familiar itself survives a logout, so its
 * drain cadence has to as well, otherwise relogging repeatedly would pay for a familiar once.
 */
private val FAMILIAR_DRAIN_CYCLES_ATTR = AttributeKey<Int>(persistenceKey = "familiar_drain_cycles")
/**
 * Persisted for the same reason as the special-regeneration accumulator above: a familiar
 * survives logout in this era, so its passive-healing cadence has to survive with it. Left
 * transient, a relog restarted the 15-second cycle from zero, which is the wrong direction of
 * the same class of bug as a resettable cooldown.
 */
private val FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR = AttributeKey<Int>(persistenceKey = "familiar_passive_heal_cycles")
/**
 * R07.7: persisted (sourced - familiars survive logout in this era, the lifetime timer just
 * pauses while offline via [TimerKey.tickOffline] = false and resumes with the same time left
 * on login) rather than transient. [FAMILIAR_ATTR] itself stays transient (a live [Npc]
 * reference is meaningless across a logout/despawn) - [FAMILIAR_NPC_ID_ATTR] is the persisted
 * id [Familiar.restoreOnLogin] respawns from.
 */
val FAMILIAR_LIFETIME_TIMER = TimerKey(persistenceKey = "familiar_lifetime", tickOffline = false, resetOnDeath = false)


object Familiar {
    const val MAX_SPECIAL_POINTS = 60
    private const val SPECIAL_REGEN_SECONDS = 30
    private const val SPECIAL_REGEN_AMOUNT = 15

    /*
     * 2026-09-06: the whole Summoning tab (662) and orb (747) are CLIENT-driven. Every field the
     * previous implementation tried to push with `IF_SETTEXT`/`IF_SETHIDE` is really rendered by
     * the cache's own cs2 off a handful of vars, so the server's only job is to keep those vars
     * accurate. Decoded from this cache with `runInterfaceHookProbeTool interface/disasm` and
     * `runVarbitDefProbeTool`; the full evidence table lives in
     * `C:\RSPS\RSPS_SUMMONING_2011_EVIDENCE.md`.
     *
     *   varp 448  -> active pouch OBJ id. 662:74 and 747:17 both list `varpTriggers=[448]`;
     *                script 751 resolves the familiar from it (`ENUM(o->n, 1320, var448)`) and
     *                names the follower from it (`OC_NAME(var448)` -> 662:54).
     *   varp 1174 -> active familiar NPC id. 662:1/747:16 `varpTriggers=[1174]`; script 751 does
     *                `OP_2201(var1174, 662:1)`, i.e. the tab's big familiar model.
     *   varp 1176 -> remaining time, as two varbits: 4534 (bits 7..31) whole minutes and 4290
     *                (bit 6) a "+30 seconds" flag. Script 752 renders exactly "M.00" / "M.30",
     *                or "---" when both are zero. This is why the real tab shows "4.00", not a
     *                free-form "4:00" string - the format is baked into the client.
     *   varp 1177 -> special-move points, 0..60. Script 756 shows/hides the 20 bar segments
     *                662:20..39 at 3 points each; tooltip script 777 prints
     *                "<var1177>/60 special move points remaining".
     *
     * Everything else on the tab follows for free once these are right: the scroll counter
     * (662:66) is computed client-side by script 769 as `INV_TOTAL(93, ENUM(o->o, 1283, var448))`,
     * and the Take-BoB button, familiar-panel and special-move icon visibility are all switched by
     * script 751 itself.
     */
    private const val POUCH_VARP = 448
    private const val FAMILIAR_NPC_VARP = 1174
    private const val SPECIAL_POINTS_VARP = 1177
    private const val TIME_MINUTES_VARBIT = 4534
    private const val TIME_HALF_MINUTE_VARBIT = 4290

    /**
     * varbit 4280 (varp 1160, bit 23) - "this account may use Summoning". `disasm 1364`, the
     * gameframe rebuild, shows the Summoning orb's entire familiar-option layer 747:8 only when
     * this is set *and* a sub-interface is mounted on gameframe slot 95; with it clear, the orb's
     * right-click menu has nothing on it but "Select left-click option", which is exactly what
     * the owner saw. Summoning here is not gated behind Wolf Whistle, so it is simply set.
     */
    private const val SUMMONING_UNLOCKED_VARBIT = 4280

    /**
     * Arms the client-side half of the Summoning HUD. Idempotent, and cheap enough to call on
     * every login: the varbit lives in a persisted varp, so this normally writes nothing.
     */
    fun unlockInterface(player: Player) {
        setVarbitIfChanged(player, SUMMONING_UNLOCKED_VARBIT, 1)
    }

    /**
     * R07.2: max Summoning points = the player's current (boosted) Summoning level, 1:1, no
     * x10 multiplier - the pre-2018-rework mechanic (sourced: 2011.rs's own "49/50" worked
     * example; runescape.wiki's own admission the x10 scaling was introduced 20 Aug 2018).
     * Mirrors [gg.rsmod.plugins.content.mechanics.prayer.Prayers]' `getMaxLevel`-driven prayer
     * point cap - same pattern, different skill.
     */
    fun maxPoints(player: Player): Int = player.skills.getMaxLevel(Skills.SUMMONING)

    /**
     * 2026-09-06 cache correction: Summoning points ARE the current level of skill 23, exactly
     * like Prayer points are the current level of skill 5 - they are not a private server-side
     * counter. Proof, decoded from this cache and recorded in `RSPS_SUMMONING_2011_EVIDENCE.md`:
     *
     *  - `disasm 755` (662:41's `onStatTransmit`, `statTriggers=[23]`) builds the tab's
     *    "32/34" cell as literally `STAT(23) + "/" + STAT_BASE(23)`.
     *  - `disasm 801` (747:5's `onStatTransmit`, `statTriggers=[23]`) builds the minimap orb's
     *    number as `STAT(23)`.
     *
     * Neither reads a varp, and neither can be driven by an `IF_SETTEXT`, because the client
     * recomputes both from the stat on every skill update. The previous implementation stored
     * points in a private `summoning_points` attribute and pushed text into 662:41 by hand, which the client
     * then overwrote from the (untouched, always-full) stat - that is why the orb and the tab
     * disagreed with the server's own idea of the player's points.
     */
    fun currentPoints(player: Player): Int = player.skills.getCurrentLevel(Skills.SUMMONING).coerceIn(0, maxPoints(player))

    fun currentSpecialPoints(player: Player): Int =
        player.attr.getOrDefault(FAMILIAR_SPECIAL_POINTS_ATTR, MAX_SPECIAL_POINTS).coerceIn(0, MAX_SPECIAL_POINTS)

    private fun setPoints(
        player: Player,
        value: Int,
    ) {
        player.skills.setCurrentLevel(Skills.SUMMONING, value.coerceIn(0, maxPoints(player)))
    }

    private fun setSpecialPoints(player: Player, value: Int) {
        player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = value.coerceIn(0, MAX_SPECIAL_POINTS)
        player.setVarp(SPECIAL_POINTS_VARP, currentSpecialPoints(player))
    }

    /** Restores Summoning points without touching the familiar timer or special-move energy. */
    fun restorePoints(player: Player, amount: Int = maxPoints(player)) {
        setPoints(player, currentPoints(player) + amount.coerceAtLeast(0))
    }

    /** Restores the separate special-move pool (one potion dose restores 15 points). */
    fun restoreSpecialPoints(player: Player, amount: Int) {
        setSpecialPoints(player, currentSpecialPoints(player) + amount.coerceAtLeast(0))
    }

    /** Target-period special energy restoration: +15, capped at 60, every 30 online seconds. */
    private fun regenerateSpecialPoints(player: Player) {
        val interval = (SPECIAL_REGEN_SECONDS * 1_000 / player.world.gameContext.cycleTime).coerceAtLeast(1)
        val accumulated = player.attr.getOrDefault(FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR, 0) + 1
        if (accumulated < interval) {
            player.attr[FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR] = accumulated
            return
        }
        player.attr[FAMILIAR_SPECIAL_REGEN_CYCLES_ATTR] = accumulated % interval
        restoreSpecialPoints(player, SPECIAL_REGEN_AMOUNT)
    }

    /**
     * The lifetime Summoning-point drain (Phase 7).
     *
     * The 2011 Knowledge Base states plainly that there are two costs - "There is an initial
     * Summoning points cost to summon a familiar" *and* "Like Prayer, Summoning a familiar will
     * drain your Summoning points, which can only be regained by visiting a Summoning obelisk or
     * drinking a Summoning potion" - but Jagex never published the rate, and neither the pouch nor
     * the familiar article carries a point-cost column at all.
     *
     * The one surviving quantitative statement is runescape.wiki's Summoning-points article: a
     * familiar consumes its **required Summoning level x 10** points in total over its full
     * duration, upfront cost included (its worked example: a bunyip takes 70 on summoning and a
     * further 610 across its life, 680 = level 68 x 10). That x10 is purely the 20 August 2018
     * rescale which "separated summoning points from the Summoning skill level (with a
     * multiplication by 10)"; on this revision's 1:1 scale the same rule reads *total lifetime
     * cost = the familiar's required Summoning level*. That is also self-consistent with the era:
     * a player at exactly the level needed to summon a familiar, with full points, can sustain
     * exactly one full-duration familiar and no more, which is what the KB's "you must have
     * enough Summoning points to support this" is describing.
     *
     * So: `total = data.level`, of which [SummoningFamiliarDefinitions] `summonPoints` is taken
     * at the pouch, and the remainder is spread evenly across the familiar's own duration - one
     * point every `duration / remainder` cycles.
     *
     * Reaching zero does **not** dismiss the familiar; runescape.wiki is explicit that "the
     * familiar does not disappear when the points are depleted" and only its right-click
     * abilities stop working. [tick] therefore never expires a familiar on points.
     *
     * SOURCE_CONFLICT, recorded rather than hidden: the per-familiar upfront costs themselves are
     * a reconstruction (modern point cost / 10) - no 2011 primary source publishes them. The
     * mechanic and the total are sourced; the split between upfront and drain is not.
     */
    private fun drainPoints(
        player: Player,
        data: SummoningPouchData,
    ) {
        val drained = data.level - definition(data).summonPoints
        if (drained <= 0) {
            return
        }
        val interval = (lifetimeCycles(player, data) / drained).coerceAtLeast(1)
        val cycles = player.attr.getOrDefault(FAMILIAR_DRAIN_CYCLES_ATTR, 0) + 1
        if (cycles < interval) {
            player.attr[FAMILIAR_DRAIN_CYCLES_ATTR] = cycles
            return
        }
        player.attr[FAMILIAR_DRAIN_CYCLES_ATTR] = cycles % interval
        val points = currentPoints(player)
        if (points > 0) {
            setPoints(player, points - 1)
        }
    }

    /** Void Spinner and Bunyip restore 100/20 internal life points every 15 online seconds. */
    private fun applyPassiveHealing(player: Player, npc: Npc) {
        val amount = when (npc.id) {
            SummoningPouchData.VOID_SPINNER.npc -> 100
            SummoningPouchData.BUNYIP.npc -> 20
            else -> {
                player.attr.remove(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR)
                return
            }
        }
        val interval = (15_000 / player.world.gameContext.cycleTime).coerceAtLeast(1)
        val cycles = player.attr.getOrDefault(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR, 0) + 1
        if (cycles < interval) {
            player.attr[FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR] = cycles
            return
        }
        player.attr[FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR] = cycles % interval
        if (player.getCurrentLifepoints() < player.getMaximumLifepoints()) {
            player.heal(amount)
            if (npc.id == SummoningPouchData.BUNYIP.npc) player.graphic(1507)
        }
    }
    /** Atomically spends special-move energy; effects must call this only after validation. */
    fun consumeSpecialPoints(player: Player, amount: Int): Boolean {
        require(amount >= 0) { "Special-move cost cannot be negative." }
        if (currentSpecialPoints(player) < amount) {
            return false
        }
        setSpecialPoints(player, currentSpecialPoints(player) - amount)
        return true
    }

    /** Target-period pouch values from the 2011 familiar roster. */
    private fun definition(data: SummoningPouchData): SummoningFamiliarDefinition =
        SummoningFamiliarDefinitions.get(data)

    /** TimerMap uses game cycles, not real milliseconds. */
    private fun lifetimeCycles(player: Player, data: SummoningPouchData): Int =
        definition(data).durationMinutes * 60_000 / player.world.gameContext.cycleTime

    /**
     * Remaining lifetime expressed the way the client expects it: whole minutes plus a separate
     * "+30 seconds" flag (script 752 renders only ".00" and ".30"). Rounded UP so a familiar with
     * any time left never reads as "---" while it is still following.
     */
    private fun remainingTimeVarbits(player: Player): Pair<Int, Int> {
        val cycles = if (player.timers.has(FAMILIAR_LIFETIME_TIMER)) player.timers[FAMILIAR_LIFETIME_TIMER] else 0
        if (cycles <= 0) return 0 to 0
        val seconds = cycles * player.world.gameContext.cycleTime / 1000
        val halfMinutes = (seconds + 29) / 30
        return (halfMinutes / 2) to (halfMinutes % 2)
    }

    /**
     * Writes the five real Summoning vars (see the constant block above). Cheap and idempotent:
     * [gg.rsmod.game.model.Varps.setState] only queues a packet when the value actually changes,
     * so this is safe to call every cycle from [tick] as well as from every lifecycle event.
     */
    fun updateHud(player: Player) {
        val npc = current(player)
        val pouch = npc?.let { active -> SummoningPouchData.values.firstOrNull { it.npc == active.id } }
        // [gg.rsmod.game.model.varp.VarpSet.setState] marks a varp dirty even when the value is
        // unchanged, so every write here is guarded - this runs once per cycle per online player.
        setVarpIfChanged(player, POUCH_VARP, pouch?.pouch ?: -1)
        setVarpIfChanged(player, FAMILIAR_NPC_VARP, npc?.id ?: -1)
        val (minutes, halfMinute) = remainingTimeVarbits(player)
        setVarbitIfChanged(player, TIME_MINUTES_VARBIT, minutes)
        setVarbitIfChanged(player, TIME_HALF_MINUTE_VARBIT, halfMinute)
        setVarpIfChanged(player, SPECIAL_POINTS_VARP, currentSpecialPoints(player))
        // The orb's special-move button has to offer the target types this familiar's own special
        // uses - see SummoningSpecialMoves.refreshOrbButton for why nothing targeted worked before.
        SummoningSpecialMoves.refreshOrbButton(player)
        // ...and the follower panel's special-move name/description/cost, which the client takes
        // from varcstr 204/205 and varbit 4288 rather than from the cache.
        SummoningSpecialMoves.refreshPanelText(player)
    }

    /**
     * Forces both Summoning surfaces to be redrawn from scratch on the next cycle. Used on login,
     * where the client has just been rebuilt and holds none of the hide state the server last
     * sent it. See [SummoningUi.invalidate] for why this is deferred rather than immediate.
     */
    fun redrawInterfaces(player: Player) {
        SummoningUi.invalidate(player)
    }

    private fun setVarpIfChanged(
        player: Player,
        id: Int,
        value: Int,
    ) {
        if (player.getVarp(id) != value) player.setVarp(id, value)
    }

    private fun setVarbitIfChanged(
        player: Player,
        id: Int,
        value: Int,
    ) {
        if (player.getVarbit(id) != value) player.setVarbit(id, value)
    }

    fun current(player: Player): Npc? {
        val npc = player.attr[FAMILIAR_ATTR]?.get() ?: return null
        if (!player.world.npcs.contains(npc)) {
            player.attr.remove(FAMILIAR_ATTR)
            return null
        }
        return npc
    }

    /**
     * Real RS puts a summoned or recalled familiar *beside* its owner, on tiles its own footprint
     * actually fits on - it does not stack it on the owner's exact tile, which is what this code
     * used to do for summon, call, plane changes and teleport recovery alike.
     *
     * The eight candidates are the whole adjacent ring expressed as south-west corner tiles, so
     * they stay correct for the 2x2 and 3x3 familiars (titans, pack yak) as well as the 1x1 ones.
     * Which direction real RS prefers is not sourced, so the order here is only deterministic;
     * adjacency and collision validity are the parts that are sourced.
     *
     * If nothing in the ring fits - a doorway, a dense object cluster - the owner's own tile is
     * the last resort. A familiar must always arrive; being stranded is worse than overlapping.
     */
    private fun placementTile(
        player: Player,
        npcId: Int,
    ): Tile {
        val size = player.world.definitions.get(NpcDef::class.java, npcId).size.coerceAtLeast(1)
        val offsets =
            arrayOf(
                -size to 0, 1 to 0, 0 to -size, 0 to 1,
                -size to -size, 1 to -size, -size to 1, 1 to 1,
            )
        val ownerFootprint = footprint(player.tile, player.getSize())
        offsets.forEach { (x, z) ->
            val candidate = player.tile.transform(x, z)
            // Never place a familiar on a tile its owner is standing on: at 2x2 and 3x3 an
            // otherwise-legal ring offset can still swallow the owner.
            if (fits(player, candidate, size) && footprint(candidate, size).none { it in ownerFootprint }) {
                return candidate
            }
        }
        return player.tile
    }

    private fun fits(
        player: Player,
        tile: Tile,
        size: Int,
    ): Boolean {
        for (x in 0 until size) {
            for (z in 0 until size) {
                if (player.world.collision.isClipped(tile.transform(x, z))) {
                    return false
                }
            }
        }
        return true
    }

    /**
     * The real summon-appearance effect, sourced from Novite's rev-667 `Familiar.java`
     * `call(boolean)`: `setNextGraphics(new Graphics(getDefinitions().size > 1 ? 1315 : 1314))`.
     * Played whenever a familiar is placed or repositioned beside its owner - initial summon,
     * "Call familiar", and login restore (Novite's own `respawnFamiliar` reaches this same
     * `call(true)` path). Voluntary dismiss deliberately gets no matching effect: Novite's
     * `dissmissFamiliar` plays none either, so that absence is a real sourced silence, not a gap
     * (see [SummoningAudioTests]'s own note on this, now half-resolved rather than fully silent).
     */
    private fun playAppearanceGraphic(npc: Npc) {
        val size = npc.world.definitions.get(NpcDef::class.java, npc.id).size
        npc.graphic(if (size > 1) 1315 else 1314)
    }

    fun summon(
        player: Player,
        data: SummoningPouchData,
    ): Boolean {
        if (player.skills.getMaxLevel(Skills.SUMMONING) < data.level) {
            player.message("You need a Summoning level of ${data.level} to summon this familiar.")
            return false
        }
        // Authentic RS behaviour: summoning while a follower is already out does NOT swap it for
        // the new one - the new summon is rejected outright and the existing familiar is
        // untouched. (2026-09-06 owner human retest: summoning another pouch incorrectly replaced
        // the old familiar; this used to unconditionally `dismiss(player)` below instead.)
        if (current(player) != null) {
            player.message("You can only have one follower at a time.")
            return false
        }
        val definition = definition(data)
        val cost = definition.summonPoints
        if (currentPoints(player) < cost) {
            player.message("You don't have enough Summoning points to summon this familiar.")
            return false
        }
        // R07.1 pouch-consumption fix: summon() never removed the pouch at all (root cause of
        // the owner-reported "pouch remains in inventory after summon" bug) - consume exactly
        // one pouch, and fail before touching XP/timer/familiar state if it's not actually there
        // (e.g. this same pouch triggered summon twice in one client tick).
        if (!player.inventory.remove(data.pouch, assureFullRemoval = true).hasSucceeded()) {
            return false
        }

        val npc = Npc(player, data.npc, placementTile(player, data.npc), player.world)
        npc.publicOwner = true
        // Familiars walk in their owner's footsteps; other creatures must not stop them dead.
        npc.ignoresEntityCollision = true
        npc.respawnOverride = false
        npc.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        // G9: the cache gives every familiar combatLevel 0, so the client draws no level at all
        // unless the server sends one. Combat familiars get their sourced 2011 level; non-combat
        // ones are left alone and keep showing none, which is correct for them.
        SummoningCombatLevels.forNpc(npc.id)?.let { npc.setCombatLevel(it) }
        player.world.spawn(npc)
        playAppearanceGraphic(npc)

        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        player.attr[FAMILIAR_NPC_ID_ATTR] = data.npc
        player.timers[FAMILIAR_LIFETIME_TIMER] = lifetimeCycles(player, data)
        setPoints(player, currentPoints(player) - cost)
        player.addXp(Skills.SUMMONING, data.summonExperience)
        player.message("You summon your familiar.")
        updateHud(player)
        // Mount the panel before focusing it. The same mount is kept when no familiar is out so
        // the owner's permanent spare-tab entry can still open an empty Follower Details panel.
        SummoningUi.restorePanel(player)
        return true
    }

    /**
     * Renew consumes one matching pouch and restores the familiar's native duration to full; it
     * does not charge Summoning points a second time.
     *
     * 2026-09-06 owner human retest: "Renew Familiar behavior/message/threshold previously
     * behaved incorrectly." The former "You need less than 2:50 remaining" gate had no source -
     * runescape.wiki's Summoning familiars article describes no minimum/maximum remaining-time
     * requirement for Renew at all, and its one Renew-related changelog entry ("Players can no
     * longer prevent their familiars from despawning by clicking the Renew button at the right
     * moment", 5 Jul 2010) is a fix for exploiting renewal *at* the expiry moment, which only
     * makes sense if Renew was otherwise usable at any remaining duration, including near zero.
     * A 170-second floor invented on top of that would itself block the exact case the patch note
     * describes fixing an exploit around, not a real restriction. Removed rather than retuned.
     */
    fun renew(player: Player): Boolean {
        val npc = current(player) ?: return false
        val data = SummoningPouchData.values.firstOrNull { it.npc == npc.id } ?: return false
        if (!player.inventory.remove(data.pouch, assureFullRemoval = true).hasSucceeded()) {
            player.message("You need another pouch of this type to renew your familiar.")
            return false
        }
        player.timers[FAMILIAR_LIFETIME_TIMER] = lifetimeCycles(player, data)
        player.message("You renew your familiar's summoning duration.")
        updateHud(player)
        return true
    }

    fun dismiss(player: Player) {
        val npc = current(player) ?: return
        BeastOfBurden.release(player)
        player.world.remove(npc)
        clearState(player)
        player.message("Your familiar is dismissed.")
        updateHud(player)
        // Owner requirement (2026-09-09): the Follower Details tab is a permanent fixture, always
        // reachable - SummoningUi.refreshPanel blanks slot 95's content, but the player is no
        // longer moved off it. See FollowerDetailsTab.
    }

    /**
     * R07.7: logout - despawn the npc (a dead client connection can't render it) but keep the
     * persisted state ([FAMILIAR_NPC_ID_ATTR], the lifetime timer, the drain-remaining total)
     * intact so [restoreOnLogin] can bring the same familiar back with the same time/points
     * left. Distinct from [dismiss], which is a full, permanent clear.
     */
    fun disconnect(player: Player) {
        val npc = current(player) ?: return
        player.world.remove(npc)
        player.attr.remove(FAMILIAR_ATTR)
    }

    /**
     * R07.7: login counterpart to [disconnect] - if persisted state says the player had an
     * active familiar with time left (the timer paused offline via `tickOffline = false`, so a
     * present, non-zero value means it's still owed), respawn it in place rather than leaving
     * the player's persisted points/timer state orphaned with no npc to show for it. If the
     * persisted npc id is missing its timer (e.g. it or the drain-remaining state was cleared
     * independently), the state is stale - clear it instead of respawning nothing.
     */
    fun restoreOnLogin(player: Player) {
        val npcId = player.attr[FAMILIAR_NPC_ID_ATTR]
        if (npcId == null) {
            return
        }
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER) || player.timers[FAMILIAR_LIFETIME_TIMER] <= 0) {
            player.attr.remove(FAMILIAR_NPC_ID_ATTR)
            player.timers.remove(FAMILIAR_LIFETIME_TIMER)
            return
        }
        val npc = Npc(player, npcId, placementTile(player, npcId), player.world)
        npc.publicOwner = true
        // Familiars walk in their owner's footsteps; other creatures must not stop them dead.
        npc.ignoresEntityCollision = true
        npc.respawnOverride = false
        npc.attr[DAMAGE_CREDIT_ATTR] = WeakReference(player)
        // G9: the cache gives every familiar combatLevel 0, so the client draws no level at all
        // unless the server sends one. Combat familiars get their sourced 2011 level; non-combat
        // ones are left alone and keep showing none, which is correct for them.
        SummoningCombatLevels.forNpc(npc.id)?.let { npc.setCombatLevel(it) }
        player.world.spawn(npc)
        playAppearanceGraphic(npc)
        player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        updateHud(player)
    }

    /**
     * "Call familiar" - instant recall, distinct from passive following. Uses the real
     * [gg.rsmod.game.model.entity.Pawn.teleportNpc] respawn-style reposition API (sets
     * moved/teleported/invisible + clears movement queue), now onto a valid [placementTile]
     * beside the owner rather than the owner's own tile.
     *
     * The knowledge base also gives this button a second job: "If you are fighting in a
     * multicombat area, this button will also make your familiar attack your enemy." That is a
     * re-order, not just the passive per-tick assistance - it pulls a familiar off a stale target
     * onto whatever the owner is currently fighting.
     */
    fun call(player: Player): Boolean {
        val npc = current(player) ?: return false
        npc.teleportNpc(placementTile(player, npc.id))
        playAppearanceGraphic(npc)
        player.message("You call your familiar to your side.")
        FamiliarCombat.recallToOwnerTarget(player)
        updateHud(player)
        return true
    }

    /**
     * Sourced revision-667 owner-death behaviour, and deliberately not the modern one.
     *
     * Two later RuneScape updates fix what this era did: the 22 August 2016 ninja strike added
     * "A beast of burden's inventory is now dropped to the floor when a player dies", and the
     * 13 November 2023 patch added "Familiars no longer despawn on death". Both are changes
     * *away* from the behaviour of this revision, so here the familiar despawns when its owner
     * dies and whatever it was carrying goes with it - it is not dropped for the killer, and it
     * is not kept.
     *
     * This is destructive, so it is isolated in its own function rather than reusing [dismiss]
     * (which drops the cargo, the correct behaviour for a voluntary dismissal). Serving the
     * modern, friendlier rule instead is a one-line change: call [dismiss] from the owner-death
     * hook in `familiar.plugin.kts`.
     */
    fun ownerDeath(player: Player) {
        val npc = current(player) ?: return
        BeastOfBurden.discard(player)
        player.world.remove(npc)
        clearState(player)
        // The cargo is no longer lost here - CUSTOM_SERVER_OVERRIDE, see BeastOfBurden.release,
        // which posts its own red Death's Domain line. This one only reports the familiar.
        player.message("Your familiar vanishes.")
        updateHud(player)
    }

    /**
     * Handles the familiar itself dying. Beast-of-burden cargo is released at the
     * familiar's death tile, then the owner's familiar state is cleared so a dead
     * NPC cannot continue following, attacking, renewing or receiving specials.
     */
    fun onDeath(npc: Npc) {
        val owner = npc.attr[DAMAGE_CREDIT_ATTR]?.get() as? Player ?: return
        if (owner.attr[FAMILIAR_ATTR]?.get() !== npc) return
        if (BeastOfBurden.isBobNpc(npc.id)) {
            BeastOfBurden.release(owner)
        }
        clearState(owner)
        updateHud(owner)
    }
    private fun clearState(player: Player) {
        // The Familiar Inventory window belongs to a familiar that no longer exists - close it
        // before the state it renders is gone, on every despawn route (dismiss, expiry, either
        // death). Leaving it open would show a live-looking grid backed by nothing.
        FamiliarInventory.close(player)
        player.attr.remove(FAMILIAR_ATTR)
        player.attr.remove(FAMILIAR_NPC_ID_ATTR)
        player.timers.remove(FAMILIAR_LIFETIME_TIMER)
        player.attr.remove(FAMILIAR_PASSIVE_HEAL_CYCLES_ATTR)
        player.attr.remove(FAMILIAR_DRAIN_CYCLES_ATTR)
    }

    /** Common despawn-and-notify cleanup shared by expiry (timer or points) and manual dismiss. */
    private fun expire(
        player: Player,
        npc: Npc,
    ) {
        BeastOfBurden.release(player)
        player.world.remove(npc)
        clearState(player)
        player.message("Your familiar has run out of time and returns home.")
        updateHud(player)
        // Same reason as dismiss: the Follower Details tab is permanent now, so expiry only needs
        // to blank the panel content (SummoningUi.refreshPanel), not move the player off it.
    }

    /** Called once/cycle per online player - see `familiar.plugin.kts`. */
    fun tick(player: Player) {
        // First, and unconditionally: the deferred interface gating. It has to run on the cycle
        // *after* the varps it competes with were flushed (see SummoningUi.invalidate), and it has
        // to run when there is no familiar too - clearing the panel after a dismiss is precisely
        // the case the owner reported as "stale Steel titan graphics".
        SummoningUi.settle(player)
        val npc = current(player) ?: return
        regenerateSpecialPoints(player)
        applyPassiveHealing(player, npc)
        SummoningPouchData.values.firstOrNull { it.npc == npc.id }?.let { drainPoints(player, it) }
        FamiliarCombat.assist(player)
        if (!player.timers.has(FAMILIAR_LIFETIME_TIMER)) {
            // Lifetime ran out - real RS despawns the familiar, it doesn't just sit there inert.
            expire(player, npc)
            return
        }
        follow(player, npc)
        updateHud(player)
    }

    /**
     * Authentic follower movement, rewritten 2026-09-06 after the owner's round-2 retest still
     * reported "stays much too far behind" and "clips through walls".
     *
     * The three things real 2011 familiar following actually requires, and what each fixes:
     *
     * (1) CHASE THE OWNER'S END-OF-CYCLE TILE, NOT ITS CURRENT ONE. This runs inside
     *     `SequentialPlayerCycleTask`, well before `SequentialSynchronizationTask` moves anyone;
     *     `PlayerPreSynchronizationTask` (players) runs there *before*
     *     `NpcPreSynchronizationTask` (npcs), so within one cycle the owner moves first and the
     *     familiar moves second - exactly like real RS, where a familiar steps into the tile its
     *     owner just left. To land there, the target must be where the owner will *finish* this
     *     cycle. [MovementQueue.cycle] consumes two queued steps for a running pawn and one for a
     *     walking one, so that tile is the owner's second queued step while running and its first
     *     while walking. The previous code always used the first step, which is one full tile
     *     short every single cycle a running owner moves - a permanent, compounding gap that
     *     never closes while the owner keeps running. That is the "much too far behind" bug.
     *
     * (2) MATCH THE OWNER'S SPEED. A familiar chasing a running owner has to cover two tiles per
     *     cycle too, so the step type mirrors how far the owner is actually moving this cycle
     *     rather than the owner's run *toggle* (which is on even while the owner stands still).
     *
     * (3) DON'T STOP DEAD ON OTHER CREATURES. [MovementQueue.cycle] clears an npc's entire queue
     *     when its next tile holds any other pawn. A familiar walking one tile behind its owner
     *     targets occupied tiles constantly, and every such cycle it froze and lost another tile.
     *     [Npc.ignoresEntityCollision] (set at spawn) exempts familiars from that entity check
     *     only - scenery/wall collision is a separate `canTraverse` test and is untouched, so
     *     this cannot reintroduce wall clipping.
     *
     * Walls: routing stays on [BFSPathFindingStrategy] - genuine breadth-first search with
     * per-tile `isStepBlocked` collision - rather than [gg.rsmod.game.model.path.strategy.SimplePathFindingStrategy],
     * which is what `Pawn.walkTo` resolves to for a non-player pawn and which has no real obstacle
     * routing. Remaining reports of a familiar crossing scenery are the recovery teleport below
     * firing, not the walk: it is now gated on the familiar genuinely being unable to path (a
     * different plane, or beyond the distance at which the client can even render it), which is
     * the same "your familiar reappears next to you" behaviour real RS has after a teleport.
     */
    private fun follow(
        player: Player,
        npc: Npc,
    ) {
        if (npc.tile.height != player.tile.height ||
            npc.tile.getDistance(player.tile) > Player.NORMAL_VIEW_DISTANCE
        ) {
            npc.teleportNpc(placementTile(player, npc.id))
            return
        }
        val ownerRunning = player.isRunning()
        val ownerSteps = player.movementQueue.peekSteps(if (ownerRunning) 2 else 1)
        val target = ownerSteps.lastOrNull()?.tile ?: player.tile
        val ownerFootprint = footprint(target, player.getSize())
        if (isInFollowSlot(npc.tile, npc.getSize(), target, player.getSize())) {
            return
        }
        val request =
            PathRequest.Builder()
                .setPoints(Tile(npc.tile), Tile(target))
                .setSourceSize(npc.getSize(), npc.getSize())
                .setTargetSize(player.getSize(), player.getSize())
                .setTouchRadius(1)
                .clipPathNodes(node = true, link = true)
                .clipOverlapTiles()
                .build()
        val route = BFSPathFindingStrategy(npc.world.collision).calculateRoute(request)
        /*
         * The familiar must not *stop* on its owner. It may legitimately pass over them - a
         * familiar does not collide with players, which is why [Npc.ignoresEntityCollision] is set
         * at spawn - so the tiles to remove are the trailing ones, not every overlapping one.
         *
         * This used to be a `filterNot` over the whole route, which is unsafe for a different
         * reason than it looks: removing a tile from the *middle* of a path leaves two
         * non-adjacent tiles next to each other in the queue, and [MovementQueue.addStep]
         * interpolates a straight line between them. Those interpolated tiles were never seen by
         * the pathfinder, so the familiar could be handed a step around a corner that no
         * breadth-first search had ever approved. Truncating instead keeps every remaining step
         * exactly as the route produced it, which is the only form in which the route's collision
         * guarantee still means anything.
         */
        val path: java.util.Queue<Tile> =
            java.util.ArrayDeque(
                route.path.toList().dropLastWhile { step -> footprint(step, npc.getSize()).any { it in ownerFootprint } },
            )
        npc.walkPath(
            path,
            stepType = if (ownerSteps.size > 1) MovementQueue.StepType.FORCED_RUN else MovementQueue.StepType.FORCED_WALK,
            detectCollision = true,
        )
    }

    /**
     * Whether a familiar of [npcSize] with its south-west corner on [npcTile] is already standing
     * where a follower should stand, relative to an owner of [ownerSize] on [ownerTile].
     *
     * Being in the follow slot is **two** conditions, and the previous implementation tested
     * neither of them properly. It asked `npc.tile.isWithinRadius(target, 1)`, which compares the
     * familiar's south-west *corner* against the owner's tile. For a size-2 familiar the corner
     * can sit on the owner's south-west diagonal - inside that radius - while the familiar's own
     * body covers the owner completely. Because the caller returns as soon as this is true, such
     * a familiar never routed anywhere again and stayed embedded in its owner permanently. That
     * is the owner's "familiar can appear partly inside the player" report, and it applied to the
     * 43 of 78 familiars this cache gives `size=2` - the common case, not an exotic one.
     *
     * So the test is expressed over footprints: the familiar must be **touching** the owner and
     * must **not overlap** them, which is what a real 2011 follower does.
     */
    fun isInFollowSlot(
        npcTile: Tile,
        npcSize: Int,
        ownerTile: Tile,
        ownerSize: Int,
    ): Boolean {
        val owner = footprint(ownerTile, ownerSize)
        val standing = footprint(npcTile, npcSize)
        if (standing.any { it in owner }) {
            return false
        }
        return standing.any { tile -> owner.any { tile.isWithinRadius(it, 1) } }
    }

    /** Every tile a pawn of [size] occupies with its south-west corner on [origin]. */
    private fun footprint(
        origin: Tile,
        size: Int,
    ): List<Tile> =
        (0 until size.coerceAtLeast(1)).flatMap { x ->
            (0 until size.coerceAtLeast(1)).map { z -> origin.transform(x, z) }
        }
}
