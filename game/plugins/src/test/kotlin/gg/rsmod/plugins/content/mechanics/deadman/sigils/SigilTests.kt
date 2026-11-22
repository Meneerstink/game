package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * PROJECT48 SIGILS (S-K6): the attune rules (bank only, never skulled, max 3, exclusive groups), the stacking overrides, the death rule
 * ("attuned sigils are always kept") and the trade rule (no player-to-player trading) of Deadman: Armageddon sigils.
 */
class SigilTests {
    private fun newPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        val attributes = AttributeMap()
        every { player.attr } returns attributes
        every { player.inventory } returns ItemContainer(mockk<DefinitionSet>(relaxed = true), 28, ContainerStackType.NORMAL)
        return player
    }

    private fun attune(
        player: Player,
        sigil: Sigil,
        atBank: Boolean = true,
    ): Sigils.Result {
        player.inventory[0] = Item(sigil.unattunedId)
        return Sigils.attune(player, 0, atBank)
    }

    @Test
    fun `the 65 sigils are numbered 1 to 65 and their items never overlap`() {
        assertEquals((1..65).toList(), Sigil.values().map { it.number })
        val ids = Sigil.values().flatMap { listOf(it.unattunedId, it.attunedId) }
        assertEquals(ids.size, ids.toSet().size, "every sigil item id is used once")
        Sigil.values().forEach {
            assertEquals(it, Sigil.ofUnattuned(it.unattunedId))
            assertEquals(it, Sigil.ofAttuned(it.attunedId))
        }
    }

    @Test
    fun `a sigil is attuned only at a bank`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.NOT_AT_BANK, attune(player, Sigil.CONSISTENCY, atBank = false))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigil.CONSISTENCY.attunedId, player.inventory[0]!!.id)
        assertTrue(Sigils.has(player, Sigil.CONSISTENCY))
    }

    @Test
    fun `at most three sigils can be attuned`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.RESILIENCE))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.PRECISION))
        assertEquals(Sigils.Result.FULL, attune(player, Sigil.AGGRESSION))
        assertEquals(3, Sigils.attuned(player).size)
    }

    @Test
    fun `the same sigil cannot be attuned twice`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigils.Result.ALREADY, attune(player, Sigil.CONSISTENCY))
    }

    @Test
    fun `swiftness, gunslinger and swashbuckler exclude each other`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.THE_GUNSLINGER))
        assertEquals(Sigils.Result.EXCLUSIVE, attune(player, Sigil.THE_SWASHBUCKLER))
        assertEquals(Sigils.Result.EXCLUSIVE, attune(player, Sigil.ARCANE_SWIFTNESS))
    }

    @Test
    fun `unattuning gives the tradeable item back and frees the slot`() {
        val player = newPlayer()
        attune(player, Sigil.CONSISTENCY)
        assertEquals(Sigils.Result.OK, Sigils.unattune(player, 0, atBank = true))
        assertEquals(Sigil.CONSISTENCY.unattunedId, player.inventory[0]!!.id)
        assertFalse(Sigils.has(player, Sigil.CONSISTENCY))
        attune(player, Sigil.CONSISTENCY)
        assertEquals(Sigils.Result.NOT_AT_BANK, Sigils.unattune(player, 0, atBank = false))
        assertTrue(Sigils.has(player, Sigil.CONSISTENCY), "still attuned after the refused unattune")
    }

    @Test
    fun `titanium overrides resistance and the harvest sigil overrides the forager`() {
        val player = newPlayer()
        attune(player, Sigil.TITANIUM)
        attune(player, Sigil.RESISTANCE)
        attune(player, Sigil.ENHANCED_HARVEST)
        assertTrue(Sigils.effective(player, Sigil.TITANIUM))
        assertFalse(Sigils.effective(player, Sigil.RESISTANCE), "does not stack with Titanium")
        assertTrue(Sigils.effective(player, Sigil.ENHANCED_HARVEST))
    }

    @Test
    fun `attuned sigils are always kept on death and no sigil can be traded between players`() {
        Sigil.values().forEach {
            assertTrue(Sigils.isAttunedItem(it.attunedId))
            assertFalse(Sigils.isAttunedItem(it.unattunedId))
            assertTrue(Sigils.isSigilItem(it.attunedId) && Sigils.isSigilItem(it.unattunedId))
        }
        assertFalse(Sigils.isSigilItem(995))
    }

    @Test
    fun `the three starting sigils are the tier 2 choices of the wiki`() {
        assertEquals(3, Sigils.START_CHOICES.size)
        Sigils.START_CHOICES.forEach { assertEquals(2, it.tier) }
        // Owner 22-11 choice A: tier 1 = 1/600, tier 2 = 1/1200, tier 3 = 1/2000 per kill.
        assertEquals(1.0 / 600, SigilDrops.GLOBAL_TABLE_CHANCE[1])
        assertEquals(1.0 / 1200, SigilDrops.GLOBAL_TABLE_CHANCE[2])
        assertEquals(1.0 / 2000, SigilDrops.GLOBAL_TABLE_CHANCE[3])
        assertNotNull(Sigil.values().firstOrNull())
    }

    @Test
    fun `a consumed sigil leaves the inventory and frees its slot once`() {
        val player = newPlayer()
        assertEquals(Sigils.Result.OK, attune(player, Sigil.THE_GUARDIAN_ANGEL))
        assertTrue(Sigils.consume(player, Sigil.THE_GUARDIAN_ANGEL))
        assertFalse(Sigils.has(player, Sigil.THE_GUARDIAN_ANGEL))
        assertNull(player.inventory[0], "the attuned item is gone")
        assertFalse(Sigils.consume(player, Sigil.THE_GUARDIAN_ANGEL), "a second consume changes nothing")
    }

    @Test
    fun `the slaughter sigil keeps the task count about half of the time`() {
        val player = newPlayer()
        val world = mockk<gg.rsmod.game.model.World>(relaxed = true)
        every { player.world } returns world
        every { world.randomDouble() } returnsMany listOf(0.2, 0.7)
        assertFalse(SigilEffects.slaughterKeepsCount(player), "without the sigil every kill counts")
        attune(player, Sigil.SLAUGHTER)
        assertTrue(SigilEffects.slaughterKeepsCount(player), "a roll under 0.5 does not count")
        assertFalse(SigilEffects.slaughterKeepsCount(player), "a roll of 0.5 or more counts")
    }

    @Test
    fun `consistency adds one damage to a landed hit and aggression gives 10 percent more and 5 percent less protection`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val attacker = newPlayer()
        attune(attacker, Sigil.CONSISTENCY)
        assertEquals(6.0, SigilCombat.modifyDamage(attacker, npc, 5.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        assertEquals(0.0, SigilCombat.modifyDamage(attacker, npc, 0.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, false), "a missed hit stays 0")
        val fighter = newPlayer()
        attune(fighter, Sigil.AGGRESSION)
        assertEquals(22.0, SigilCombat.modifyDamage(fighter, npc, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val victim = newPlayer()
        attune(victim, Sigil.AGGRESSION)
        assertEquals(21.0, SigilCombat.modifyDamage(npc, victim, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "5% more damage taken")
    }

    @Test
    fun `titanium takes 60 percent and resistance 25 percent off monster hits only`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val titan = newPlayer()
        attune(titan, Sigil.TITANIUM)
        assertEquals(40.0, SigilCombat.modifyDamage(npc, titan, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val resist = newPlayer()
        attune(resist, Sigil.RESISTANCE)
        assertEquals(75.0, SigilCombat.modifyDamage(npc, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val other = newPlayer()
        assertEquals(100.0, SigilCombat.modifyDamage(other, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "a player's hit is not reduced")
    }

    // ---- lead fixes 21-11 (G2, G3, G5, G6, G8, G11) ----

    private fun container(size: Int) = ItemContainer(mockk<DefinitionSet>(relaxed = true), size, ContainerStackType.NORMAL)

    @Test
    fun `a boss mechanic's direct hit is a monster attack - titanium, resistance and aggression count, poison and unsourced hits do not`() {
        val hit = gg.rsmod.plugins.api.HitType.REGULAR_HIT
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val titan = newPlayer()
        attune(titan, Sigil.TITANIUM)
        assertEquals(40, SigilCombat.directHit(titan, npc, 100, hit))
        assertEquals(40, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.MAGIC))
        assertEquals(100, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.POISON), "poison is not an attack")
        assertEquals(100, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.REFLECTED), "nor is reflected damage")
        assertEquals(100, SigilCombat.directHit(titan, null, 100, hit), "a hit without a source (a trap, a fall) is not a monster")
        assertEquals(100, SigilCombat.directHit(titan, newPlayer(), 100, hit), "a player's direct hit is not reduced")
        assertEquals(0, SigilCombat.directHit(titan, npc, 0, hit))
        val resist = newPlayer()
        attune(resist, Sigil.RESISTANCE)
        assertEquals(75, SigilCombat.directHit(resist, npc, 100, hit))
        val angry = newPlayer()
        attune(angry, Sigil.AGGRESSION)
        assertEquals(21, SigilCombat.directHit(angry, npc, 20, hit), "5% more from all sources")
        assertEquals(21, SigilCombat.directHit(angry, newPlayer(), 20, hit))
        assertEquals(100, SigilCombat.directHit(newPlayer(), npc, 100, hit), "no sigil: untouched")
        assertEquals(100, SigilCombat.directHit(npc, npc, 100, hit), "an npc target has no sigils")
    }

    @Test
    fun `a second hit that is a share of a dealt hit is not changed again`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val angry = newPlayer()
        attune(angry, Sigil.AGGRESSION)
        val hit = gg.rsmod.plugins.api.HitType.RANGE
        assertEquals(20, SigilCombat.shareOfDealtHit { SigilCombat.directHit(angry, npc, 20, hit) })
        assertEquals(21, SigilCombat.directHit(angry, npc, 20, hit), "and the switch is off again afterwards")
    }

    @Test
    fun `rigorous ranger saves ammunition but not chinchompas and meticulous mage saves runes - only when attuned`() {
        // The mocked world rolls 0.0: a 50% chance always succeeds, so only the conditions are tested here.
        val plain = newPlayer()
        every { plain.equipment } returns container(14)
        assertFalse(SigilCombat.savesAmmo(plain))
        assertFalse(SigilCombat.savesRunes(plain))
        val ranger = newPlayer()
        every { ranger.equipment } returns container(14)
        attune(ranger, Sigil.THE_RIGOROUS_RANGER)
        assertTrue(SigilCombat.savesAmmo(ranger))
        assertFalse(SigilCombat.savesRunes(ranger), "the ranger sigil saves no runes")
        val mage = newPlayer()
        every { mage.equipment } returns container(14)
        attune(mage, Sigil.THE_METICULOUS_MAGE)
        assertTrue(SigilCombat.savesRunes(mage))
        assertFalse(SigilCombat.savesAmmo(mage), "the mage sigil saves no ammunition")
    }

    @Test
    fun `an attuned sigil whose item is gone from inventory and bank is unattuned at login - one in the bank stays`() {
        val player = newPlayer()
        val bank = container(100)
        every { player.bank } returns bank
        attune(player, Sigil.CONSISTENCY)
        attune(player, Sigil.TITANIUM)
        // After two attunes slot 0 holds the attuned Titanium; the attuned Consistency was overwritten = lost.
        assertEquals(listOf(Sigil.CONSISTENCY), Sigils.reconcile(player))
        assertEquals(listOf(Sigil.TITANIUM), Sigils.attuned(player), "the slot is free again, the held sigil stays")
        bank[0] = Item(Sigil.TITANIUM.attunedId)
        player.inventory[0] = null
        assertTrue(Sigils.reconcile(player).isEmpty(), "an attuned sigil in the bank is not lost")
        assertTrue(Sigils.has(player, Sigil.TITANIUM))
        assertTrue(Sigils.reconcile(newPlayer()).isEmpty(), "nothing attuned: nothing to repair")
    }

    @Test
    fun `the starting sigils are locked on the grand exchange below the 71-90 bracket - other sigils never`() {
        val low = newPlayer()
        every { low.combatLevel } returns 60
        val high = newPlayer()
        every { high.combatLevel } returns 71
        Sigils.START_CHOICES.forEach {
            assertTrue(SigilDrops.startSigilLockedOnExchange(low, it.unattunedId))
            assertFalse(SigilDrops.startSigilLockedOnExchange(high, it.unattunedId))
        }
        assertFalse(SigilDrops.startSigilLockedOnExchange(low, Sigil.CONSISTENCY.unattunedId))
        assertFalse(SigilDrops.startSigilLockedOnExchange(low, 995))
    }

    @Test
    fun `formidable fighter - a successful melee hit arms plus 10 for the next melee hit, other styles are untouched`() {
        // The mocked world rolls 0.0, so the 30% chance always arms the next hit.
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val melee = gg.rsmod.plugins.api.HitType.MELEE
        val fighter = newPlayer()
        attune(fighter, Sigil.THE_FORMIDABLE_FIGHTER)
        assertEquals(20.0, SigilCombat.modifyDamage(fighter, npc, 20.0, melee, true), "the first hit only arms the bonus")
        assertEquals(20.0, SigilCombat.modifyDamage(fighter, npc, 20.0, gg.rsmod.plugins.api.HitType.RANGE, true), "a ranged hit does not spend it")
        assertEquals(0.0, SigilCombat.modifyDamage(fighter, npc, 0.0, melee, false), "a miss does not spend it")
        assertEquals(30.0, SigilCombat.modifyDamage(fighter, npc, 20.0, melee, true), "the next successful melee hit deals +10")
        val plain = newPlayer()
        attune(plain, Sigil.CONSISTENCY)
        assertEquals(21.0, SigilCombat.modifyDamage(plain, npc, 20.0, melee, true), "without the sigil there is no +10")
        assertEquals(21.0, SigilCombat.modifyDamage(plain, npc, 20.0, melee, true))
    }

    @Test
    fun `accuracy sigils scale the hit chance against monsters - deft 20, precision 50, meticulousness 75 percent, the strongest counts`() {
        val melee = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val other = mockk<Player>(relaxed = true)
        val plain = newPlayer()
        assertEquals(0.20, SigilCombat.accuracy(plain, npc, 0.20, melee), 1e-9, "no sigil, no change")
        val deft = newPlayer()
        attune(deft, Sigil.DEFT_STRIKES)
        assertEquals(0.24, SigilCombat.accuracy(deft, npc, 0.20, melee), 1e-9)
        assertEquals(0.20, SigilCombat.accuracy(deft, other, 0.20, melee), 1e-9, "not against players")
        val precise = newPlayer()
        attune(precise, Sigil.PRECISION)
        assertEquals(0.30, SigilCombat.accuracy(precise, npc, 0.20, melee), 1e-9)
        val all = newPlayer()
        attune(all, Sigil.DEFT_STRIKES)
        attune(all, Sigil.PRECISION)
        attune(all, Sigil.METICULOUSNESS)
        assertEquals(0.35, SigilCombat.accuracy(all, npc, 0.20, melee), 1e-9, "they do not stack: +75% only")
    }

    @Test
    fun `finality guarantees every hit of the next special and is used up only when that special is performed`() {
        // Found in the game (21-11 run 2): with a dragon dagger only the first of the two special hits was guaranteed - the first
        // landed hit closed the window. Wiki: "guarantee 100% accuracy on your next special attack within the next 12 seconds".
        val melee = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val player = newPlayer()
        attune(player, Sigil.FINALITY)
        assertTrue(Sigils.activate(player, Sigil.FINALITY))
        val special = gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS
        assertEquals(0.20, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "a normal attack is not guaranteed")
        player.attr[special] = true
        assertEquals(1.0, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "first hit of the special")
        assertEquals(1.0, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "second hit of the same special")
        player.attr.remove(special)
        val source = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/SpecialAttacks.kt").readText()
        assertTrue(source.contains("Sigils.consumeWindow(player, gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigil.FINALITY)"))
        Sigils.consumeWindow(player, Sigil.FINALITY)
        player.attr[special] = true
        assertEquals(0.20, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "the special after that rolls normally")
    }

    @Test
    fun `every combat formula applies the sigil accuracy itself - the strategies roll with getAccuracy, not through a wrapper`() {
        // Found in the game (21-11 run 2): a bot with Meticulousness hit Evelot as often as one without - the factor sat in a
        // PawnExt.dealHit overload that no player attack used.
        val dir = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/combat")
        listOf("Melee", "Ranged", "Magic").forEach { style ->
            val source = java.io.File(dir, "formula/${style}CombatFormula.kt").readText()
            assertTrue(source.contains("SigilCombat.accuracy(pawn, target, accuracy, ${style}CombatFormula)"), "$style formula")
        }
        assertFalse(java.io.File(dir, "PawnExt.kt").readText().contains("SigilCombat.accuracy("), "never twice")
    }

    @Test
    fun `rampage - a second accuracy roll of the same attack leaves the count, a changed style raises it to 3 at most`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val world = mockk<gg.rsmod.game.model.World>(relaxed = true)
        var tick = 100
        every { world.currentCycle } answers { tick }
        val player = newPlayer()
        every { player.world } returns world
        attune(player, Sigil.RAMPAGE)
        val melee = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val magic = gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
        assertEquals(0.20, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "first attack: count 0")
        tick += 4
        assertEquals(0.206, SigilCombat.accuracy(player, npc, 0.20, magic), 1e-9, "changed style: count 1")
        assertEquals(0.206, SigilCombat.accuracy(player, npc, 0.20, magic), 1e-9, "the second roll of the same attack keeps count 1")
        tick += 4
        assertEquals(0.212, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "count 2")
        tick += 4
        assertEquals(0.218, SigilCombat.accuracy(player, npc, 0.20, magic), 1e-9, "count 3")
        tick += 4
        assertEquals(0.218, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "never above 3")
        tick += 4
        assertEquals(0.20, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "the same style twice resets the count")
    }

    @Test
    fun `rampage stays attuned and effective beside aggression - only its damage bonus does not stack`() {
        val player = newPlayer()
        attune(player, Sigil.RAMPAGE)
        attune(player, Sigil.AGGRESSION)
        assertTrue(Sigils.effective(player, Sigil.RAMPAGE), "Rampage keeps its count and accuracy")
        assertTrue(Sigils.effective(player, Sigil.AGGRESSION))
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        assertEquals(22.0, SigilCombat.modifyDamage(player, npc, 20.0, gg.rsmod.plugins.api.HitType.MELEE, true), "only Aggression's 10%")
    }

    @Test
    fun `bracket values - porcupine 2 below 71-90, curse 12 and cripple 10 in 3-50`() {
        fun at(level: Int) = newPlayer().also { every { it.combatLevel } returns level }
        assertEquals(2, SigilCombat.porcupineRecoil(at(40)))
        assertEquals(2, SigilCombat.porcupineRecoil(at(70)))
        assertEquals(4, SigilCombat.porcupineRecoil(at(71)))
        assertEquals(4, SigilCombat.porcupineRecoil(at(126)))
        assertEquals(12, SigilCombat.curseDamage(at(50)))
        assertEquals(18, SigilCombat.curseDamage(at(51)))
        assertEquals(10, SigilCombat.crippleDamage(at(50)))
        assertEquals(15, SigilCombat.crippleDamage(at(51)))
    }

    @Test
    fun `gathering multipliers - harvest 3x, forager 2x, hoarding 3x and the abyss 5x`() {
        val none = newPlayer()
        assertEquals(1, SigilSkilling.multiplier(none))
        assertEquals(1, SigilSkilling.hoardingMultiplier(none))
        assertEquals(1, SigilSkilling.runecraftMultiplier(none))
        val forager = newPlayer()
        attune(forager, Sigil.THE_FORAGER)
        assertEquals(2, SigilSkilling.multiplier(forager))
        attune(forager, Sigil.ENHANCED_HARVEST)
        assertEquals(3, SigilSkilling.multiplier(forager), "Enhanced Harvest wins over the Forager")
        val abyss = newPlayer()
        attune(abyss, Sigil.THE_ABYSS)
        attune(abyss, Sigil.HOARDING)
        assertEquals(5, SigilSkilling.runecraftMultiplier(abyss))
        assertEquals(3, SigilSkilling.hoardingMultiplier(abyss))
    }

    @Test
    fun `an un-attuned sigil used on a bank attunes it - the Sigil of Mobility has no Attune option in the cache`() {
        val player = newPlayer()
        player.inventory[0] = Item(Sigil.MOBILITY.unattunedId)
        assertTrue(Sigils.useOnBank(player, 0), "the bank use is handled")
        assertTrue(Sigil.MOBILITY in Sigils.attuned(player), "the sigil is attuned")
        assertEquals(Sigil.MOBILITY.attunedId, player.inventory[0]?.id, "the item became the attuned sigil")
    }

    @Test
    fun `every sigil has its wiki effect text and Inspect cuts it into chat lines that fit`() {
        assertEquals(Sigil.values().size, SigilTexts.size, "one text per sigil")
        for (sigil in Sigil.values()) {
            assertTrue(SigilTexts.of(sigil).length > 20, "${sigil.label} has an effect text")
            val lines = SigilTexts.lines(sigil)
            assertTrue(lines.all { it.length <= 76 }, "${sigil.label}: every chat line fits")
            assertEquals(SigilTexts.of(sigil), lines.joinToString(" "), "${sigil.label}: no word is lost")
        }
        assertEquals("Every attack does +1 damage to the target.", SigilTexts.of(Sigil.CONSISTENCY))
    }

    @Test
    fun `Prosperity raises every Deadman-table chance by 20 percent - a roll between 1-2000 and 1_2-2000 is a tier 3 sigil only with it`() {
        // 0.00055: above the tier 3 chance 1/2000 (0.0005) but under 1.2/2000 (0.0006); under the tier 2 chance 1/1200 (0.00083).
        fun rolled(withSigil: Boolean): Item? {
            val player = newPlayer()
            if (withSigil) attune(player, Sigil.PROSPERITY)
            val world = mockk<gg.rsmod.game.model.World>(relaxed = true)
            every { player.world } returns world
            every { world.randomDouble() } returns 0.00055
            every { world.random(any<Int>()) } returns 0
            return SigilDrops.rollGlobalTable(player, 117)
        }
        assertEquals(2, rolled(false)?.let { Sigil.ofUnattuned(it.id)?.tier }, "without Prosperity the roll misses tier 3 and lands on tier 2")
        assertEquals(3, rolled(true)?.let { Sigil.ofUnattuned(it.id)?.tier }, "with Prosperity the same roll is a tier 3 sigil")
    }

    @Test
    fun `Agile Fortune gives 10,000 coins on a lucky lap and nothing without the sigil or on an unlucky roll`() {
        fun coins(withSigil: Boolean, roll: Int): Int {
            val player = mockk<Player>(relaxed = true)
            every { player.attr } returns AttributeMap()
            // Coins need a real ItemDef: a relaxed DefinitionSet hands back a bare Definition mock (ClassCastException in add).
            val defs = mockk<DefinitionSet>(relaxed = true)
            every { defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, gg.rsmod.plugins.api.cfg.Items.COINS_995) } returns
                gg.rsmod.game.fs.def.ItemDef(gg.rsmod.plugins.api.cfg.Items.COINS_995).apply { name = "Coins"; stacks = true }
            every { player.inventory } returns ItemContainer(defs, 28, ContainerStackType.STACK)
            val bank = ItemContainer(defs, 800, ContainerStackType.STACK)
            every { player.bank } returns bank
            if (withSigil) attune(player, Sigil.AGILE_FORTUNE)
            player.inventory[0] = null
            val world = mockk<gg.rsmod.game.model.World>(relaxed = true)
            every { player.world } returns world
            every { world.random(2) } returns roll
            SigilSkilling.agileFortune(player)
            return player.inventory.getItemCount(gg.rsmod.plugins.api.cfg.Items.COINS_995) + bank.getItemCount(gg.rsmod.plugins.api.cfg.Items.COINS_995)
        }
        assertEquals(10_000, coins(withSigil = true, roll = 0), "a lucky lap with the sigil")
        assertEquals(0, coins(withSigil = true, roll = 1), "an unlucky lap (2 in 3)")
        assertEquals(0, coins(withSigil = false, roll = 0), "no sigil, no coins")
    }
}
