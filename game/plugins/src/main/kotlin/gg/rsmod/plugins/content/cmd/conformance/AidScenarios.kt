package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.message.impl.OpHeldUMessage
import gg.rsmod.game.message.impl.OpPlayerExtraMessage
import gg.rsmod.game.message.impl.OpPlayerTMessage
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Run
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.magic.SpellbookData
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.mechanics.aid.AcceptAid
import gg.rsmod.plugins.content.mechanics.assist.AssistSystem
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance

/**
 * `conform systems acceptaid | potionshare | assist` (owner 2026-10-01: "alles wat niet gelukt is doen"). Real packets only.
 * - acceptaid: Accept Aid (varp 427) starts on, the Options button 261:7 switches it, Vengeance Other / Heal Other / Energy Transfer
 *   / Teleother are refused while it is off (AcceptAid), work again when it is on; the login default is applied once per account.
 * - potionshare: Boost Potion Share on a Super strength(4): the caster and the Accept Aid player in the 3x3 are boosted, the one with
 *   Accept Aid off and the one two tiles away are not, one dose is used per recipient (RuneScape Wiki; PotionShare). Stat Restore Pot
 *   Share restores prayer. A hunter's mix is not shared outside multicombat (OSRS Wiki "Hunter's mix").
 * - assist: the Assist System end to end (AssistSystem): Accept Aid needed, Req Assist (OPPLAYER7) sends a type-102 request, the
 *   Assist filter Off stops it, the assister's OPPLAYER7 starts the session (lock, 301, varbits), a borrowed level cuts a dragonstone
 *   and its experience goes to the assister (varbit 4103 in tenths), an own-level action keeps its experience, the daily cap,
 *   XP Earned/Time, the assister cannot cast, a teleport keeps the session, walking 21 squares ends it, combat ends it, closing 301
 *   ends it.
 */
object AidScenarios {
    private fun msgs(
        run: Run,
        who: String,
        from: Int,
    ): List<String> =
        run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }.map { it.text }

    private fun saw(
        run: Run,
        who: String,
        from: Int,
        text: String,
    ) = msgs(run, who, from).any { it.contains(text) }

    /**
     * Moves every other player off the 7x7 around [center]: bots of earlier scenarios stay where they stood, and an area effect
     * (Potion Share takes up to 4 players, the mix 3) would count them - correctly - instead of this scenario's own players.
     */
    private fun clearAround(
        run: Run,
        center: Tile,
        keep: List<Client>,
    ) {
        val strays = mutableListOf<gg.rsmod.game.model.entity.Player>()
        run.world.players.forEach { p -> if (p !in keep && p.tile.height == center.height && p.tile.isWithinRadius(center, 3)) strays += p }
        strays.forEachIndexed { i, p -> p.moveTo(center.transform(-20 - i, -20)) }
    }

    private fun prepare(p: Client) {
        p.resetInteractions()
        p.inventory.removeAll()
        p.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        p.setCurrentLifepoints(p.getMaximumLifepoints())
    }

    private fun spellOnPlayer(
        caster: Client,
        target: Client,
        book: Int,
        component: Int,
    ) = Bots.inject(caster, OpPlayerTMessage(target.index, (book shl 16) or component, -1, 0))

    // ---- acceptaid -------------------------------------------------------------------------------------------------------

    suspend fun QueueTask.acceptAid(
        run: Run,
        arena: Tile,
    ) {
        val e = "acceptaid"
        val a = run.bot(arena, "AA")
        val b = run.bot(arena.transform(1, 0), "AB")
        wait(2)
        clearAround(run, arena, listOf(a, b))
        for (p in listOf(a, b)) {
            prepare(p)
            Vengeance.clear(p)
            p.timers.remove(gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN)
            Spellbooks.select(p, Spellbook.LUNAR)
        }
        run.check(e, "Accept Aid starts on (login default)", b.getVarp(AcceptAid.VARP), "owner 2026-10-01: default on" to AcceptAid.ON)
        Bots.button(b, 261, 7)
        wait(1)
        run.check(e, "Options 261:7 switches it off", b.getVarp(AcceptAid.VARP), "clientscript 1269: 0 = off" to 0)

        val c1 = run.world.currentCycle
        spellOnPlayer(a, b, 430, SpellbookData.VENGEANCE_OTHER.component)
        wait(3)
        run.expectTrue(e, "Vengeance Other refused while off", !Vengeance.isActive(b) && saw(run, "AA", c1, AcceptAid.REFUSED), msgs(run, "AA", c1).joinToString(" / ").take(160))

        b.setCurrentLifepoints(b.getMaximumLifepoints() / 2)
        val hp = b.getCurrentLifepoints()
        val c2 = run.world.currentCycle
        spellOnPlayer(a, b, 430, SpellbookData.HEAL_OTHER.component)
        wait(3)
        run.expectTrue(e, "Heal Other refused while off", b.getCurrentLifepoints() == hp && saw(run, "AA", c2, AcceptAid.REFUSED), "lp ${b.getCurrentLifepoints()} vs $hp")

        gg.rsmod.plugins.content.inter.attack.AttackTab.setEnergy(a, 100)
        gg.rsmod.plugins.content.inter.attack.AttackTab.setEnergy(b, 0)
        val c3 = run.world.currentCycle
        spellOnPlayer(a, b, 430, SpellbookData.ENERGY_TRANSFER.component)
        wait(3)
        run.expectTrue(
            e, "Energy Transfer refused while off",
            gg.rsmod.plugins.content.inter.attack.AttackTab.getEnergy(b) == 0 && saw(run, "AA", c3, AcceptAid.REFUSED), msgs(run, "AA", c3).take(3).toString(),
        )

        Spellbooks.select(a, Spellbook.STANDARD)
        val c4 = run.world.currentCycle
        spellOnPlayer(a, b, 192, SpellbookData.TELEOTHER_LUMBRIDGE.component)
        wait(3)
        run.expectTrue(e, "Teleother refused while off", saw(run, "AA", c4, AcceptAid.TELEOTHER_REFUSED), msgs(run, "AA", c4).take(3).toString())
        Spellbooks.select(a, Spellbook.LUNAR)

        Bots.button(b, 261, 7)
        wait(1)
        run.check(e, "Options 261:7 switches it on again", b.getVarp(AcceptAid.VARP), "clientscript 1269: 1 = on" to AcceptAid.ON)
        spellOnPlayer(a, b, 430, SpellbookData.VENGEANCE_OTHER.component)
        wait(3)
        run.expectTrue(e, "Vengeance Other works while on", Vengeance.isActive(b), "not venged")

        // The default is applied once: a player who switched it off keeps it off at his next login.
        b.setVarp(AcceptAid.VARP, 0)
        AcceptAid.applyDefault(b)
        run.check(e, "the login default never overrides the player's choice", b.getVarp(AcceptAid.VARP), "kept off" to 0)
        b.setVarp(AcceptAid.VARP, AcceptAid.ON)
        Vengeance.clear(b)
    }

    // ---- potionshare -----------------------------------------------------------------------------------------------------

    suspend fun QueueTask.potionShare(
        run: Run,
        arena: Tile,
    ) {
        val e = "potionshare"
        val a = run.bot(arena, "PA")
        val b = run.bot(arena.transform(1, 0), "PB")
        val c = run.bot(arena.transform(0, 1), "PC")
        val d = run.bot(arena.transform(3, 0), "PD")
        wait(2)
        clearAround(run, arena, listOf(a, b, c, d))
        for (p in listOf(a, b, c, d)) {
            prepare(p)
            p.skills.restore(Skills.STRENGTH)
            p.setVarp(AcceptAid.VARP, AcceptAid.ON)
        }
        c.setVarp(AcceptAid.VARP, 0)
        Spellbooks.select(a, Spellbook.LUNAR)
        a.inventory.add(Items.SUPER_STRENGTH_4, 1)
        val magic0 = a.skills.getCurrentXp(Skills.MAGIC)
        Bots.inject(a, OpHeldUMessage((430 shl 16) or SpellbookData.BOOST_POTION_SHARE.component, -1, -1, 679 shl 16, 0, Items.SUPER_STRENGTH_4))
        wait(3)
        val base = 99
        run.expectTrue(e, "caster boosted (free dose)", a.skills.getCurrentLevel(Skills.STRENGTH) > base, "str ${a.skills.getCurrentLevel(Skills.STRENGTH)}")
        run.expectTrue(e, "Accept Aid player in the 3x3 boosted", b.skills.getCurrentLevel(Skills.STRENGTH) > base, "str ${b.skills.getCurrentLevel(Skills.STRENGTH)}")
        run.check(e, "Accept Aid off: not boosted", c.skills.getCurrentLevel(Skills.STRENGTH), "AcceptAid" to base)
        run.check(e, "outside the 3x3: not boosted", d.skills.getCurrentLevel(Skills.STRENGTH), "RuneScape Wiki 3x3" to base)
        run.check(e, "one dose used per recipient", a.inventory.getItemCount(Items.SUPER_STRENGTH_3), "Super strength(3)" to 1)
        run.expectTrue(e, "Magic experience", a.skills.getCurrentXp(Skills.MAGIC) > magic0, "xp ${a.skills.getCurrentXp(Skills.MAGIC)}")

        b.setCurrentPrayerPoints(10)
        a.inventory.removeAll()
        a.inventory.add(Items.PRAYER_POTION_4, 1)
        wait(4)
        Bots.inject(a, OpHeldUMessage((430 shl 16) or SpellbookData.STAT_RESTORE_POT_SHARE.component, -1, -1, 679 shl 16, 0, Items.PRAYER_POTION_4))
        wait(3)
        run.expectTrue(e, "Stat Restore Pot Share restores the recipient's prayer", b.getCurrentPrayerPoints() > 10, "prayer ${b.getCurrentPrayerPoints()}")

        a.inventory.removeAll()
        a.inventory.add(Items.ATTACK_POTION_4, 1)
        val c5 = run.world.currentCycle
        Bots.inject(a, OpHeldUMessage((430 shl 16) or SpellbookData.STAT_RESTORE_POT_SHARE.component, -1, -1, 679 shl 16, 0, Items.ATTACK_POTION_4))
        wait(2)
        run.expectTrue(e, "a boost potion is not a Stat Restore potion", saw(run, "PA", c5, gg.rsmod.plugins.content.magic.lunar.PotionShare.CANNOT_SHARE), msgs(run, "PA", c5).take(2).toString())

        // A hunter's mix: in multicombat it reaches the adjacent players with Accept Aid on (OSRS Wiki "Hunter's mix"), outside it only
        // the drinker.
        for (p in listOf(a, b, c)) p.skills.restore(Skills.ATTACK)
        a.inventory.removeAll()
        a.inventory.add(Items.RUBY_HARVEST_MIX_2, 1)
        wait(4)
        Bots.itemOption(a, 0, "Drink")
        wait(2)
        val multi = a.tile.isMulti(run.world)
        val drinker = a.skills.getCurrentLevel(Skills.ATTACK)
        val adjacent = b.skills.getCurrentLevel(Skills.ATTACK)
        val refuser = c.skills.getCurrentLevel(Skills.ATTACK)
        run.expectTrue(e, "hunter's mix: the drinker is boosted", drinker > 99, "attack $drinker")
        if (multi) {
            run.expectTrue(e, "hunter's mix in multicombat: the adjacent Accept Aid player shares it", adjacent > 99, "attack $adjacent", "OSRS Wiki Hunter's mix")
            run.check(e, "hunter's mix in multicombat: Accept Aid off is left out", refuser, "OSRS Wiki Hunter's mix" to 99)
        } else {
            run.check(e, "hunter's mix outside multicombat: drinker only", adjacent, "OSRS Wiki Hunter's mix" to 99)
        }
        c.setVarp(AcceptAid.VARP, AcceptAid.ON)
    }

    // ---- assist ----------------------------------------------------------------------------------------------------------

    suspend fun QueueTask.assist(
        run: Run,
        arena: Tile,
    ) {
        val e = "assist"
        val a = run.bot(arena, "XA") // the one who is helped
        val b = run.bot(arena.transform(1, 0), "XB") // the assister
        wait(2)
        clearAround(run, arena, listOf(a, b))
        for (p in listOf(a, b)) {
            prepare(p)
            if (AssistSystem.inSession(p)) AssistSystem.end(p)
            p.attr.remove(AssistSystem.REQUESTED)
            p.setVarp(AssistSystem.FILTER_VARP, 0)
            AssistSystem.setEarned(p, 0.0)
        }
        a.skills.setBaseLevel(Skills.CRAFTING, 1)
        run.check(e, "Req Assist is player option 7", a.options[AssistSystem.OPTION_SLOT - 1], "clientscript 86: OPPLAYER7" to AssistSystem.OPTION)

        // Accept Aid off: no request.
        a.setVarp(AcceptAid.VARP, 0)
        val c0 = run.world.currentCycle
        Bots.inject(a, OpPlayerExtraMessage(AssistSystem.OPTION_SLOT, b.index))
        wait(3)
        run.expectTrue(e, "requester needs Accept Aid on", saw(run, "XA", c0, AssistSystem.NEED_ACCEPT_AID), msgs(run, "XA", c0).take(2).toString())
        a.setVarp(AcceptAid.VARP, AcceptAid.ON)
        a.attr.remove(AssistSystem.REQUESTED)

        // The assister's filter Off: the request never arrives.
        b.setVarp(AssistSystem.FILTER_VARP, AssistSystem.FILTER_OFF)
        val c1 = run.world.currentCycle
        Bots.inject(a, OpPlayerExtraMessage(AssistSystem.OPTION_SLOT, b.index))
        wait(3)
        run.expectTrue(e, "Assist filter Off: no request", msgs(run, "XB", c1).none { it.contains("type=102") }, msgs(run, "XB", c1).take(2).toString())
        b.setVarp(AssistSystem.FILTER_VARP, 0)
        a.attr.remove(AssistSystem.REQUESTED)

        // Request: type 102 from A to B.
        val c2 = run.world.currentCycle
        Bots.inject(a, OpPlayerExtraMessage(AssistSystem.OPTION_SLOT, b.index))
        wait(3)
        run.expectTrue(e, "request reaches the target as type 102", msgs(run, "XB", c2).any { it.contains("type=102") && it.contains(AssistSystem.REQUEST_TEXT) }, msgs(run, "XB", c2).take(2).toString())
        val c2b = run.world.currentCycle
        Bots.inject(a, OpPlayerExtraMessage(AssistSystem.OPTION_SLOT, b.index))
        wait(2)
        run.expectTrue(e, "a second request within 16 ticks is refused", saw(run, "XA", c2b, "only just made an assistance request"), msgs(run, "XA", c2b).take(2).toString())

        // Accept (the chat's "Give assistance" = B's OPPLAYER7 on A).
        Bots.inject(b, OpPlayerExtraMessage(AssistSystem.OPTION_SLOT, a.index))
        wait(3)
        run.expectTrue(e, "session started", AssistSystem.assistantOf(a) === b && AssistSystem.assistedBy(b) === a, "assistant=${AssistSystem.assistantOf(a)?.username}")
        run.check(e, "assister cannot move or act", b.lock, "RuneScape Wiki" to LockState.ASSISTING)
        run.expectTrue(e, "Assist XP display 301 open", b.interfaces.isVisible(AssistSystem.INTERFACE), "not open")
        run.check(e, "Crafting switched on (varbit 4091)", b.getVarbit(4091), "assister higher" to 1)

        // Borrowed level: A (Crafting 1) cuts a dragonstone (55); the experience goes to B.
        a.inventory.add(Items.CHISEL, 1)
        a.inventory.add(Items.UNCUT_DRAGONSTONE, 1)
        val axp = a.skills.getCurrentXp(Skills.CRAFTING)
        val bxp = b.skills.getCurrentXp(Skills.CRAFTING)
        Bots.inject(a, OpHeldUMessage(679 shl 16, 0, Items.CHISEL, 679 shl 16, 1, Items.UNCUT_DRAGONSTONE))
        wait(5)
        run.check(e, "borrowed level cuts the dragonstone", a.inventory.getItemCount(Items.DRAGONSTONE), "assister's 99 Crafting" to 1)
        run.check(e, "no experience for the helped player", a.skills.getCurrentXp(Skills.CRAFTING), "wiki: the assister receives all skill experience" to axp)
        val bGain = b.skills.getCurrentXp(Skills.CRAFTING) - bxp
        val mult = b.xpMultiplier(Skills.CRAFTING)
        run.expectTrue(e, "experience to the assister, at his own rate", Math.abs(bGain - 137.5 * mult) < 0.01, "gained $bGain, 137.5 x $mult = ${137.5 * mult}")
        run.check(e, "XP display varbit 4103 (tenths of the xp really gained)", b.getVarbit(AssistSystem.EARNED_VARBIT), "gained $bGain" to Math.round(bGain * 10).toInt())

        // The borrow is the action in progress only: a Crafting gain 12 ticks later (no requirement check) is A's own.
        repeat(12) { wait(1) }
        val axpLate = a.skills.getCurrentXp(Skills.CRAFTING)
        val bxpLate = b.skills.getCurrentXp(Skills.CRAFTING)
        a.addXp(Skills.CRAFTING, 10.0)
        run.expectTrue(e, "a later unrelated gain is the helped player's own", a.skills.getCurrentXp(Skills.CRAFTING) > axpLate && b.skills.getCurrentXp(Skills.CRAFTING) == bxpLate,
            "a +${a.skills.getCurrentXp(Skills.CRAFTING) - axpLate}, b +${b.skills.getCurrentXp(Skills.CRAFTING) - bxpLate}", "AssistSystem.BORROW_WINDOW_TICKS")

        // Own level: A (Crafting 20) cuts a sapphire (20) - his own experience.
        a.skills.setBaseLevel(Skills.CRAFTING, 20)
        a.inventory.add(Items.UNCUT_SAPPHIRE, 1)
        val axp2 = a.skills.getCurrentXp(Skills.CRAFTING)
        val earned2 = AssistSystem.earnedToday(b)
        Bots.inject(a, OpHeldUMessage(679 shl 16, 0, Items.CHISEL, 679 shl 16, a.inventory.getItemIndex(Items.UNCUT_SAPPHIRE, false), Items.UNCUT_SAPPHIRE))
        wait(5)
        run.expectTrue(e, "own level: the helped player keeps the experience", a.skills.getCurrentXp(Skills.CRAFTING) > axp2 && AssistSystem.earnedToday(b) == earned2,
            "a xp ${a.skills.getCurrentXp(Skills.CRAFTING) - axp2}, b earned ${AssistSystem.earnedToday(b) - earned2}")

        // The assister cannot cast.
        val c3 = run.world.currentCycle
        Spellbooks.select(b, Spellbook.LUNAR)
        Vengeance.clear(b)
        b.timers.remove(gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN)
        spellOnPlayer(b, a, 430, SpellbookData.VENGEANCE_OTHER.component)
        wait(3)
        run.expectTrue(e, "the assister cannot cast", !Vengeance.isActive(a), msgs(run, "XB", c3).take(2).toString())

        // XP Earned/Time on the Assist tab (751:16 op6).
        val c4 = run.world.currentCycle
        Bots.button(b, 751, 16, option = 6)
        wait(1)
        run.expectTrue(e, "XP Earned/Time", saw(run, "XB", c4, "You have earned ${bGain.toInt()} Xp"), msgs(run, "XB", c4).take(2).toString())

        // Daily cap: 30,000 - only the rest is given, then the cap message.
        AssistSystem.setEarned(b, 29_950.0)
        val bxpCap = b.skills.getCurrentXp(Skills.CRAFTING)
        a.skills.setBaseLevel(Skills.CRAFTING, 1)
        a.inventory.add(Items.UNCUT_DRAGONSTONE, 1)
        Bots.inject(a, OpHeldUMessage(679 shl 16, 0, Items.CHISEL, 679 shl 16, a.inventory.getItemIndex(Items.UNCUT_DRAGONSTONE, false), Items.UNCUT_DRAGONSTONE))
        wait(5)
        run.check(e, "daily cap 30,000", AssistSystem.earnedToday(b), "RuneScape Wiki" to 30_000.0)
        run.expectTrue(e, "the cap counts the experience really gained", Math.abs(b.skills.getCurrentXp(Skills.CRAFTING) - bxpCap - 50.0) < 0.05,
            "gained ${b.skills.getCurrentXp(Skills.CRAFTING) - bxpCap} of the last 50")
        val c5 = run.world.currentCycle
        Bots.button(b, 751, 16, option = 6)
        wait(1)
        run.expectTrue(e, "capped: XP Earned/Time names the limit", saw(run, "XB", c5, "maximum XP (30,000 Xp)"), msgs(run, "XB", c5).take(2).toString())
        AssistSystem.setEarned(b, 0.0)

        // Range (RuneScape Wiki): 20 squares from the assister; a teleport keeps it (then measured from the landing tile); being moved 25
        // squares without a teleport (forced movement, a shortcut) ends it, and so does walking 21 squares from the landing tile.
        a.teleportTo(arena.transform(30, 0))
        wait(2)
        run.expectTrue(e, "a teleport keeps the session", AssistSystem.assistantOf(a) === b, "ended")
        for (i in 1..11) {
            a.moveTo(a.tile.transform(2, 0))
            wait(1)
        }
        run.expectTrue(e, "walking past 20 squares from the landing ends it", AssistSystem.assistantOf(a) == null && b.lock != LockState.ASSISTING,
            "assistant=${AssistSystem.assistantOf(a)?.username} lock=${b.lock}")
        a.moveTo(arena)
        wait(2)
        AssistSystem.start(a, b)
        wait(1)
        a.moveTo(arena.transform(25, 0))
        wait(2)
        run.expectTrue(e, "moved 25 squares without a teleport: ends it", AssistSystem.assistantOf(a) == null, "still assisted")
        a.moveTo(arena)
        wait(2)

        // Vengeance is never cast with a lent Magic level (AssistSystem.NO_LENDING_SPELLS), even out of combat.
        AssistSystem.start(a, b)
        wait(1)
        a.skills.setBaseLevel(Skills.MAGIC, 1)
        a.skills.setBaseLevel(Skills.DEFENCE, 99)
        Spellbooks.select(a, Spellbook.LUNAR)
        Vengeance.clear(a)
        a.timers.remove(gg.rsmod.game.model.timer.VENGEANCE_COOLDOWN)
        val venge = MagicSpells.metadataByName("Vengeance")
        if (venge == null) {
            run.notTested(e, "Vengeance with a lent level", "no spell button")
        } else {
            Bots.button(a, venge.interfaceId, venge.component)
            wait(2)
            run.expectTrue(e, "Vengeance is not cast with a lent Magic level", !Vengeance.isActive(a), "venged", "AssistSystem.NO_LENDING_SPELLS (Deadman)")
        }
        a.skills.setBaseLevel(Skills.MAGIC, 99)
        Spellbooks.select(a, Spellbook.STANDARD)
        AssistSystem.end(a, "scenario")
        wait(1)

        // Closing 301 ends it.
        AssistSystem.start(a, b)
        wait(1)
        b.closeInterface(AssistSystem.INTERFACE)
        wait(1)
        run.expectTrue(e, "closing the XP display ends it", AssistSystem.assistantOf(a) == null && b.lock == LockState.NONE, "lock=${b.lock}")

        // Combat ends it before the attack resolves.
        AssistSystem.start(a, b)
        wait(1)
        val dummy = run.npc(gg.rsmod.plugins.api.cfg.Npcs.CHICKEN, arena.transform(0, 2), "XChicken")
        wait(1)
        // A lent Magic level never casts a combat spell (Deadman): Magic 60 (past the Ancient Magicks' own level-50 gate) with a 99
        // assister, Ice Barrage (94) is refused by the level check itself - Combat.canAttack asks it before the attack would end the
        // assistance.
        a.skills.setBaseLevel(Skills.MAGIC, 60)
        Spellbooks.select(a, Spellbook.ANCIENT)
        val c6 = run.world.currentCycle
        gg.rsmod.plugins.content.combat.magic.Autocast.markManualCast(a, gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ICE_BARRAGE, dummy)
        a.attack(dummy)
        wait(4)
        val chickenHits = run.recording.of("XChicken", "out").count { it.cycle >= c6 && it.text.startsWith("hits ") }
        run.expectTrue(e, "a lent Magic level never casts a combat spell", chickenHits == 0 && saw(run, "XA", c6, "Magic level is not high enough"),
            "hits=$chickenHits msgs=${msgs(run, "XA", c6).take(2)}", "AssistSystem: Deadman, MagicSpells.castProblem")
        a.resetInteractions()
        a.skills.setBaseLevel(Skills.MAGIC, 99)
        Spellbooks.select(a, Spellbook.STANDARD)
        if (AssistSystem.assistantOf(a) == null) AssistSystem.start(a, b)
        wait(1)
        a.attack(dummy)
        wait(3)
        run.expectTrue(e, "an attack ends the session first", AssistSystem.assistantOf(a) == null, "still assisted")
        a.resetInteractions()
        run.remove(dummy)
        for (p in listOf(a, b)) {
            if (AssistSystem.inSession(p)) AssistSystem.end(p)
            p.inventory.removeAll()
        }
    }
}
