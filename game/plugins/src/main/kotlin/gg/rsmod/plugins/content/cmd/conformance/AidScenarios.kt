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
import gg.rsmod.plugins.content.mechanics.combatresponse.Vengeance

/**
 * `conform systems acceptaid | potionshare` (owner 2026-10-01: "alles wat niet gelukt is doen"). Real packets only.
 * - acceptaid: Accept Aid (varp 427) starts on, the Options button 261:7 switches it, Vengeance Other / Heal Other / Energy Transfer
 *   / Teleother are refused while it is off (AcceptAid), work again when it is on; the login default is applied once per account.
 * - potionshare: Boost Potion Share on a Super strength(4): the caster and the Accept Aid player in the 3x3 are boosted, the one with
 *   Accept Aid off and the one two tiles away are not, one dose is used per recipient (RuneScape Wiki; PotionShare). Stat Restore Pot
 *   Share restores prayer. A hunter's mix is not shared outside multicombat (OSRS Wiki "Hunter's mix").
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
}
