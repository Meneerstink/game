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
    fun `worn slot uses OSRS resilience when empty and retains attunement slot order`() {
        val player = newPlayer()
        assertEquals(Sigil.RESILIENCE.unattunedId, SigilWornSlot.iconItem(player))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.RESILIENCE))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.CONSISTENCY))
        assertEquals(Sigils.Result.OK, attune(player, Sigil.PRECISION))
        assertEquals(listOf(Sigil.RESILIENCE, Sigil.CONSISTENCY, Sigil.PRECISION), Sigils.attuned(player))
        assertEquals(Sigil.RESILIENCE.attunedId, SigilWornSlot.iconItem(player))
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
        assertEquals(Sigils.Result.FULL, attune(player, Sigil.DEFT_STRIKES))
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
    fun `consistency adds one damage and legacy aggression never changes damage`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val attacker = newPlayer()
        attune(attacker, Sigil.CONSISTENCY)
        assertEquals(6.0, SigilCombat.modifyDamage(attacker, npc, 5.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        assertEquals(0.0, SigilCombat.modifyDamage(attacker, npc, 0.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, false), "a missed hit stays 0")
        val fighter = newPlayer()
        fighter.attr[Sigils.ATTUNED] = "36"
        assertEquals(20.0, SigilCombat.modifyDamage(fighter, npc, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val victim = newPlayer()
        victim.attr[Sigils.ATTUNED] = "36"
        assertEquals(20.0, SigilCombat.modifyDamage(npc, victim, 20.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "retired sigil has no effect")
    }

    @Test
    fun `titanium takes 25 percent and resistance 15 percent off monster hits only`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val titan = newPlayer()
        attune(titan, Sigil.TITANIUM)
        assertEquals(75.0, SigilCombat.modifyDamage(npc, titan, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val resist = newPlayer()
        attune(resist, Sigil.RESISTANCE)
        assertEquals(85.0, SigilCombat.modifyDamage(npc, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true))
        val other = newPlayer()
        assertEquals(100.0, SigilCombat.modifyDamage(other, resist, 100.0, gg.rsmod.plugins.api.HitType.REGULAR_HIT, true), "a player's hit is not reduced")
    }

    // ---- lead fixes 21-11 (G2, G3, G5, G6, G8, G11) ----

    private fun container(size: Int) = ItemContainer(mockk<DefinitionSet>(relaxed = true), size, ContainerStackType.NORMAL)

    @Test
    fun `a boss mechanic's direct hit is a monster attack - titanium and resistance count but retired aggression does not, poison and unsourced hits do not`() {
        val hit = gg.rsmod.plugins.api.HitType.REGULAR_HIT
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val titan = newPlayer()
        attune(titan, Sigil.TITANIUM)
        assertEquals(75, SigilCombat.directHit(titan, npc, 100, hit))
        assertEquals(75, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.MAGIC))
        assertEquals(100, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.POISON), "poison is not an attack")
        assertEquals(100, SigilCombat.directHit(titan, npc, 100, gg.rsmod.plugins.api.HitType.REFLECTED), "nor is reflected damage")
        assertEquals(100, SigilCombat.directHit(titan, null, 100, hit), "a hit without a source (a trap, a fall) is not a monster")
        assertEquals(100, SigilCombat.directHit(titan, newPlayer(), 100, hit), "a player's direct hit is not reduced")
        assertEquals(0, SigilCombat.directHit(titan, npc, 0, hit))
        val resist = newPlayer()
        attune(resist, Sigil.RESISTANCE)
        assertEquals(85, SigilCombat.directHit(resist, npc, 100, hit))
        val angry = newPlayer()
        angry.attr[Sigils.ATTUNED] = "36"
        assertEquals(20, SigilCombat.directHit(angry, npc, 20, hit), "retired aggression does not amplify incoming hits")
        assertEquals(20, SigilCombat.directHit(angry, newPlayer(), 20, hit))
        assertEquals(100, SigilCombat.directHit(newPlayer(), npc, 100, hit), "no sigil: untouched")
        assertEquals(100, SigilCombat.directHit(npc, npc, 100, hit), "an npc target has no sigils")
    }

    @Test
    fun `a second hit that is a share of a dealt hit is not changed again`() {
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val angry = newPlayer()
        attune(angry, Sigil.RESISTANCE)
        val hit = gg.rsmod.plugins.api.HitType.RANGE
        assertEquals(20, SigilCombat.shareOfDealtHit { SigilCombat.directHit(angry, npc, 20, hit) })
        assertEquals(17, SigilCombat.directHit(angry, npc, 20, hit), "and the switch is off again afterwards")
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
    fun `accuracy sigils scale the hit chance against monsters - deft 10, precision 20, meticulousness 30 percent, the strongest counts`() {
        val melee = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val other = mockk<Player>(relaxed = true)
        val plain = newPlayer()
        assertEquals(0.20, SigilCombat.accuracy(plain, npc, 0.20, melee), 1e-9, "no sigil, no change")
        val deft = newPlayer()
        attune(deft, Sigil.DEFT_STRIKES)
        assertEquals(0.22, SigilCombat.accuracy(deft, npc, 0.20, melee), 1e-9)
        assertEquals(0.20, SigilCombat.accuracy(deft, other, 0.20, melee), 1e-9, "not against players")
        val precise = newPlayer()
        attune(precise, Sigil.PRECISION)
        assertEquals(0.24, SigilCombat.accuracy(precise, npc, 0.20, melee), 1e-9)
        val all = newPlayer()
        attune(all, Sigil.DEFT_STRIKES)
        attune(all, Sigil.PRECISION)
        attune(all, Sigil.METICULOUSNESS)
        assertEquals(0.26, SigilCombat.accuracy(all, npc, 0.20, melee), 1e-9, "they do not stack: +30% only")
    }

    @Test
    fun `finality boosts every hit of the next special by 50 percent and is used up only when that special is performed`() {
        // Preserve the multi-hit fix of 22-11 while applying the owner 23-11 accuracy nerf.
        val melee = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val player = newPlayer()
        attune(player, Sigil.FINALITY)
        assertTrue(Sigils.activate(player, Sigil.FINALITY))
        val special = gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS
        assertEquals(0.20, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "a normal attack is not boosted")
        player.attr[special] = true
        assertEquals(0.30, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "first hit of the special")
        assertEquals(0.30, SigilCombat.accuracy(player, npc, 0.20, melee), 1e-9, "second hit of the same special")
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
    fun `rampage stays effective with a legacy aggression number in the save`() {
        val player = newPlayer()
        attune(player, Sigil.RAMPAGE)
        player.attr[Sigils.ATTUNED] = "57,36"
        assertTrue(Sigils.effective(player, Sigil.RAMPAGE), "Rampage keeps its count and accuracy")
        assertFalse(Sigils.effective(player, Sigil.AGGRESSION))
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        assertEquals(20.0, SigilCombat.modifyDamage(player, npc, 20.0, gg.rsmod.plugins.api.HitType.MELEE, true), "no Aggression bonus")
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
    fun `gathering nerfs - harvest 2x, forager 50 percent, hoarding 1_5x and the abyss 2x`() {
        val none = newPlayer()
        assertEquals(1, SigilSkilling.multiplier(none))
        assertEquals(5, SigilSkilling.hoardingReward(none, 5))
        assertEquals(1, SigilSkilling.runecraftMultiplier(none))
        val forager = newPlayer()
        attune(forager, Sigil.THE_FORAGER)
        assertEquals(2, SigilSkilling.multiplier(forager))
        attune(forager, Sigil.ENHANCED_HARVEST)
        assertEquals(2, SigilSkilling.multiplier(forager), "Enhanced Harvest wins over the Forager")
        val abyss = newPlayer()
        attune(abyss, Sigil.THE_ABYSS)
        attune(abyss, Sigil.HOARDING)
        assertEquals(2, SigilSkilling.runecraftMultiplier(abyss))
        assertEquals(7, SigilSkilling.hoardingReward(abyss, 5))
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
    fun `retired sigils cannot attune and legacy saves free the slot without deleting items`() {
        assertEquals(setOf(11, 36, 50, 60), Sigil.values().filterNot { it.enabled }.map { it.number }.toSet())
        assertEquals(61, Sigil.available.size)
        for (retired in Sigil.values().filterNot { it.enabled }) {
            val player = newPlayer()
            every { player.bank } returns container(100)
            assertEquals(Sigils.Result.REMOVED, attune(player, retired))
            assertEquals(retired.unattunedId, player.inventory[0]?.id)
            player.attr[Sigils.ATTUNED] = "1,${retired.number},6"
            player.inventory[0] = Item(retired.attunedId)
            player.inventory[1] = Item(Sigil.CONSISTENCY.attunedId)
            player.bank[0] = Item(Sigil.RESILIENCE.attunedId)
            assertFalse(Sigils.has(player, retired))
            assertFalse(Sigils.effective(player, retired))
            assertEquals(listOf(Sigil.CONSISTENCY, Sigil.RESILIENCE), Sigils.attuned(player))
            assertNull(AttunedSigilsUi.sigilAt(player, 2), "third slot is free")
            assertTrue(Sigils.reconcile(player).isEmpty(), "held active sigils remain")
            assertEquals("1,6", player.attr[Sigils.ATTUNED])
            assertEquals(retired.attunedId, player.inventory[0]?.id, "legacy item is preserved")
            assertEquals(Sigils.Result.REMOVED, Sigils.unattune(player, 0, atBank = true))
            assertTrue(Sigils.useOnBank(player, 0))
            assertFalse(Sigils.activate(player, retired))
            assertEquals(Sigils.REMOVED_MESSAGE, SigilTexts.of(retired))
            player.inventory[2] = Item(Sigil.PRECISION.unattunedId)
            assertEquals(Sigils.Result.OK, Sigils.attune(player, 2, atBank = true))
            assertEquals(3, Sigils.attuned(player).size)
            val empty = newPlayer()
            empty.attr[Sigils.ATTUNED] = retired.number.toString()
            assertTrue(Sigils.reconcile(empty).isEmpty(), "removed-only save loads safely")
            assertTrue(Sigils.attuned(empty).isEmpty())
            assertEquals("", empty.attr[Sigils.ATTUNED])
        }
    }

    @Test
    fun `forager rolls one extra per resource at 50 percent and never stacks with enhanced harvest`() {
        val player = newPlayer()
        attune(player, Sigil.THE_FORAGER)
        every { player.world.randomDouble() } returnsMany listOf(0.499999, 0.5, 0.999999)
        assertEquals(2, SigilSkilling.multiplier(player))
        assertEquals(1, SigilSkilling.multiplier(player))
        assertEquals(1, SigilSkilling.multiplier(player))
        attune(player, Sigil.ENHANCED_HARVEST)
        assertFalse(Sigils.effective(player, Sigil.THE_FORAGER))
        assertEquals(2, SigilSkilling.multiplier(player))
        every { player.world.randomDouble() } throws AssertionError("Harvest must not roll Forager")
        assertEquals(2, SigilSkilling.multiplier(player))
    }

    @Test
    fun `hoarding scales an award once and floors odd totals without a random second award`() {
        val player = newPlayer()
        attune(player, Sigil.HOARDING)
        for ((base, expected) in listOf(0 to 0, 1 to 1, 3 to 4, 5 to 7, 18 to 27)) {
            assertEquals(expected, SigilSkilling.hoardingReward(player, base))
        }
    }

    @Test
    fun `titanium overrides resistance on normal and direct monster hits`() {
        val player = newPlayer()
        val npc = mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        attune(player, Sigil.RESISTANCE)
        attune(player, Sigil.TITANIUM)
        assertEquals(75.0, SigilCombat.modifyDamage(npc, player, 100.0, gg.rsmod.plugins.api.HitType.MELEE, true))
        assertEquals(75, SigilCombat.directHit(player, npc, 100, gg.rsmod.plugins.api.HitType.MAGIC))
    }

    @Test
    fun `augmented thrall max hit is one and a half times three rounded down`() {
        val player = newPlayer()
        assertEquals(3, gg.rsmod.plugins.content.mechanics.deadman.UndeadThralls.maxHit(player))
        attune(player, Sigil.THE_AUGMENTED_THRALL)
        assertEquals(4, gg.rsmod.plugins.content.mechanics.deadman.UndeadThralls.maxHit(player))
    }

    @Test
    fun `finality works against players and monsters on every style with a real sixty second cooldown`() {
        val player = newPlayer()
        attune(player, Sigil.FINALITY)
        attune(player, Sigil.SPECIALISED_STRIKES)
        assertTrue(Sigils.activate(player, Sigil.FINALITY))
        assertEquals(60, Sigil.FINALITY.cooldownSeconds)
        val until = player.attr[Sigils.COOLDOWN_UNTIL.getValue(Sigil.FINALITY)]!!
        repeat(5) { assertFalse(Sigils.activate(player, Sigil.FINALITY)) }
        assertEquals(until, player.attr[Sigils.COOLDOWN_UNTIL.getValue(Sigil.FINALITY)])
        // At elapsed 30 seconds it is still blocked; at 60 it becomes ready without sleeping.
        val now = (System.currentTimeMillis() / 1000L).toInt()
        player.attr[Sigils.COOLDOWN_UNTIL.getValue(Sigil.FINALITY)] = now + 30
        assertFalse(Sigils.activate(player, Sigil.FINALITY))
        assertTrue(Sigils.activate(player, Sigil.SPECIALISED_STRIKES))
        assertEquals(15, SigilCombat.specialCost(player, 25))
        player.attr[gg.rsmod.plugins.content.mechanics.poison.WeaponPoison.SPECIAL_ATTACK_IN_PROGRESS] = true
        val targets = listOf(newPlayer(), mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true))
        val formulas = listOf(gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula,
            gg.rsmod.plugins.content.combat.formula.RangedCombatFormula,
            gg.rsmod.plugins.content.combat.formula.MagicCombatFormula)
        for (target in targets) for (formula in formulas) {
            assertEquals(0.30, SigilCombat.accuracy(player, target, 0.20, formula), 1e-9)
            assertEquals(1.0 - 0.2 / 1.5, SigilCombat.accuracy(player, target, 0.80, formula), 1e-9)
            assertEquals(0.0, SigilCombat.accuracy(player, target, 0.0, formula), 1e-9, "no guaranteed hit")
        }
        SigilCombat.windowUsed(player)
        assertEquals(25, SigilCombat.specialCost(player, 25))
        assertTrue(Sigils.windowOpen(player, Sigil.FINALITY), "cost discount cannot consume Finality")
        Sigils.consumeWindow(player, Sigil.FINALITY)
        assertFalse(Sigils.windowOpen(player, Sigil.FINALITY))
        assertFalse(Sigils.activate(player, Sigil.FINALITY), "using the special does not reset cooldown")
        player.attr[Sigils.COOLDOWN_UNTIL.getValue(Sigil.FINALITY)] = now - 1
        assertTrue(Sigils.activate(player, Sigil.FINALITY))
    }

    @Test
    fun `jeweller saves a charge only on the lower half of rolls and never without attunement`() {
        val player = newPlayer()
        assertEquals(-1, SigilJeweller.keep(player, 2552, -1))
        attune(player, Sigil.THE_ETERNAL_JEWELLER)
        every { player.world.randomDouble() } returnsMany listOf(0.499999, 0.5, 0.999999)
        assertEquals(2552, SigilJeweller.keep(player, 2552, -1))
        assertEquals(-1, SigilJeweller.keep(player, 2552, -1))
        assertEquals(-1, SigilJeweller.keep(player, 2552, -1))
    }

    @Test
    fun `retired sigils never appear in any award pool even when every pool index is rolled`() {
        val player = newPlayer()
        val world = player.world
        assertTrue(SigilDrops.STARTER_POOL.all { it.enabled && it.tier == 1 })
        for ((tier, roll) in listOf(3 to 0.0, 2 to 0.0006, 1 to 0.001)) {
            every { world.randomDouble() } returns roll
            val pool = Sigil.available.filter { it.tier == tier }
            for (index in pool.indices) {
                every { world.random(any<Int>()) } returns index
                assertEquals(pool[index].unattunedId, SigilDrops.rollGlobalTable(player, 0)?.id)
                assertEquals(pool[index], SigilDrops.gatherRoll(player))
                if (tier == 1) assertEquals(pool[index], SigilDrops.rollStarterSigil(player, 100000))
            }
        }
    }

    @Test
    fun `retired sigils are blocked on the GE but preserved by the removal purge`() {
        val player = newPlayer()
        val retired = Sigil.values().filterNot { it.enabled }
        val hidden = java.io.File("../../data/cfg/removed/ge_hidden.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.toInt() }.toSet()
        for ((slot, sigil) in retired.withIndex()) {
            player.inventory[slot] = Item(sigil.unattunedId)
            for (id in listOf(sigil.attunedId, sigil.unattunedId)) {
                val def = gg.rsmod.game.fs.def.ItemDef(id).apply { tradeable = true }
                assertTrue(id in hidden, "retired sigil is excluded from the GE search table")
                assertTrue(gg.rsmod.plugins.content.mechanics.removed.RemovedItems.isExchangeBlocked(def))
                assertFalse(gg.rsmod.plugins.content.mechanics.removed.RemovedItems.isUnobtainable(def), "no destructive login purge")
            }
        }
    }

    @Test
    fun `nature gives one separated extra growth cycle per five minutes and stops after unattuning`() {
        val player = newPlayer()
        val constants = gg.rsmod.plugins.content.skills.farming.constants.Constants
        val manager = mockk<gg.rsmod.plugins.content.skills.farming.logic.FarmingManager>(relaxed = true)
        player.attr[constants.farmingManagerAttr] = manager
        every { player.world.attr } returns AttributeMap().apply { this[constants.worldFarmTick] = 100 }
        every { player.timers } returns gg.rsmod.game.model.timer.TimerMap()
        attune(player, Sigil.NATURE)
        val nature = gg.rsmod.plugins.content.skills.farming.core.PlayerManager
        nature.onFarmingTick(player)
        io.mockk.verify(exactly = 1) { manager.onFarmingTick(any()) }
        assertEquals(250, player.timers[nature.natureTimer], "one extra at 2.5 minutes")
        player.timers.remove(nature.natureTimer) // timer expiry dispatch
        nature.onNatureStep(player)
        io.mockk.verify(exactly = 2) { manager.onFarmingTick(any()) }
        assertFalse(player.timers.has(nature.natureTimer), "no further extras scheduled")
        nature.onNatureStep(player)
        io.mockk.verify(exactly = 2) { manager.onFarmingTick(any()) }
        nature.onFarmingTick(player)
        assertEquals(Sigils.Result.OK, Sigils.unattune(player, 0, atBank = true))
        nature.onNatureStep(player)
        io.mockk.verify(exactly = 3) { manager.onFarmingTick(any()) }
    }

    @Test
    fun `cooldown refusal uses the DMM video lines (SIGIL-TEKST-BRON 23-11)`() {
        kotlin.test.assertEquals("Your Sigil still needs another 13 seconds before it's ready.", Sigils.notReady(13))
        kotlin.test.assertEquals("Your Sigil can be activated in 1 second.", Sigils.notReady(1))
    }
}
