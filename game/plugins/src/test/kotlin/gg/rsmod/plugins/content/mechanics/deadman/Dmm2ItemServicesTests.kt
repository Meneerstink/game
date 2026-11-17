package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.deadman.Dmm2Ids.ItemId
import io.mockk.every
import kotlin.test.*

class Dmm2ItemServicesTests {
    private val f = DeadmanPlayerFixture()
    private val ids = ItemId.values().associateWith { 1000 + it.ordinal }
    init {
        Dmm2Ids.install(ids.map { "item\t${it.key.source}\t${it.value}" })
        ids.values.forEach { f.define(it) }
        listOf(Items.COINS_995, Items.BLOOD_RUNE, Items.FIRE_RUNE, Items.SOUL_RUNE, Items.CHAOS_RUNE).forEach { f.define(it, true) }
        f.define(Items.AMULET_OF_MAGIC)
        val timers = TimerMap()
        every { f.player.timers } returns timers
        every { f.player.skills.getCurrentLevel(Skills.RUNECRAFTING) } returns 21
        every { f.player.skills.getCurrentLevel(Skills.MAGIC) } returns 75
    }
    private fun id(item: ItemId) = ids.getValue(item)
    @Test fun `fire altar preview spends nothing and completion rechecks both a lost boost and removed ingredients`() {
        f.inventory[0] = Item(id(ItemId.FIRE_RUBY))
        f.inventory[1] = Item(Items.AMULET_OF_MAGIC)
        f.inventory[2] = Item(Items.FIRE_RUNE, 100)
        repeat(5) { assertEquals(FireAmulet.Result.OK, FireAmulet.check(f.player)) }
        assertEquals(1L, f.count(id(ItemId.FIRE_RUBY)))
        assertEquals(100L, f.count(Items.FIRE_RUNE))
        every { f.player.skills.getCurrentLevel(Skills.RUNECRAFTING) } returns 20
        assertEquals(FireAmulet.Result.LEVEL, FireAmulet.craft(f.player))
        assertEquals(1L, f.count(Items.AMULET_OF_MAGIC))
        every { f.player.skills.getCurrentLevel(Skills.RUNECRAFTING) } returns 21
        f.inventory[2] = Item(Items.FIRE_RUNE, 99)
        assertEquals(FireAmulet.Result.INGREDIENTS, FireAmulet.craft(f.player))
        assertEquals(1L, f.count(id(ItemId.FIRE_RUBY)))
        f.inventory[2] = Item(Items.FIRE_RUNE, 100)
        assertEquals(FireAmulet.Result.OK, FireAmulet.craft(f.player))
        assertEquals(1L, f.count(id(ItemId.FIRE_AMULET)))
        assertEquals(FireAmulet.Result.INGREDIENTS, FireAmulet.craft(f.player))
        assertEquals(1L, f.count(id(ItemId.FIRE_AMULET)))
    }
    @Test fun `mapping validates duplicate and unknown ids without using source ids as local items`() {
        assertFailsWith<IllegalArgumentException> { Dmm2Ids.parse(listOf("item\t28531\t100", "item\t28531\t101")) }
        assertFailsWith<IllegalArgumentException> { Dmm2Ids.parse(listOf("item\t28531\t0")) }
        assertFailsWith<IllegalArgumentException> { Dmm2Ids.parse(listOf("banana\t1\t2")) }
        assertNotEquals(ItemId.SHADOW.source, ItemId.SHADOW.local)
    }
    @Test fun `fortuity conserves ten million across charge refund and repeats`() {
        repeat(3) {
            f.inventory[0] = Item(id(ItemId.FORTUITY_EMPTY)); f.inventory[1] = Item(Items.COINS_995, 10_000_000)
            assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.charge(f.player, 0, id(ItemId.FORTUITY_EMPTY), true))
            assertEquals(0L, f.count(Items.COINS_995)); assertEquals(1.0, DeadmanTrinkets.bonus(f.player))
            assertEquals(DeadmanTrinkets.Result.ITEM_CHANGED, DeadmanTrinkets.charge(f.player, 0, id(ItemId.FORTUITY_EMPTY), true))
            assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.charge(f.player, 0, id(ItemId.FORTUITY), false))
            assertEquals(10_000_000L, f.count(Items.COINS_995)); assertEquals(0.0, DeadmanTrinkets.bonus(f.player))
        }
    }
    @Test fun `fortuity no money overflow and full inventory rollback and bank confers no effect`() {
        f.inventory[0] = Item(id(ItemId.FORTUITY_EMPTY))
        assertEquals(DeadmanTrinkets.Result.NO_COINS, DeadmanTrinkets.charge(f.player, 0, id(ItemId.FORTUITY_EMPTY), true))
        f.inventory[0] = Item(id(ItemId.FORTUITY)); f.inventory[1] = Item(Items.COINS_995, Int.MAX_VALUE)
        assertEquals(DeadmanTrinkets.Result.NO_SPACE, DeadmanTrinkets.charge(f.player, 0, id(ItemId.FORTUITY), false))
        assertEquals(id(ItemId.FORTUITY), f.inventory[0]?.id); assertEquals(Int.MAX_VALUE.toLong(), f.count(Items.COINS_995))
        f.bank[0] = f.inventory[0]; f.inventory[0] = null
        assertEquals(0.0, DeadmanTrinkets.bonus(f.player))
    }
    @Test fun `advanced weaponry has eight outcomes empty charged weapons and stale double click safety`() {
        val result = mutableSetOf<Int>()
        repeat(8) { roll ->
            f.inventory[0] = Item(id(ItemId.ADVANCED_WEAPONRY))
            assertEquals(DeadmanTrinkets.Result.OBSTRUCTED, DeadmanTrinkets.openAdvanced(f.player, 0, id(ItemId.ADVANCED_WEAPONRY), false) { error("must not roll") })
            assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.openAdvanced(f.player, 0, id(ItemId.ADVANCED_WEAPONRY), true) { bound -> assertEquals(8, bound); roll })
            result += f.inventory[0]!!.id
            assertEquals(DeadmanTrinkets.Result.ITEM_CHANGED, DeadmanTrinkets.openAdvanced(f.player, 0, id(ItemId.ADVANCED_WEAPONRY), true) { error("double click rolled") })
        }
        assertEquals(8, result.size); assertTrue(id(ItemId.SCYTHE_EMPTY) in result); assertTrue(id(ItemId.SHADOW_EMPTY) in result)
    }
    @Test fun `scythe independent max splits batches last charge and non well uncharge destroy charges`() {
        assertEquals(listOf(47, 23, 11), CorruptedWeapons.scytheMaxHits(47, 5))
        assertEquals(listOf(48, 24), CorruptedWeapons.scytheMaxHits(48, 2))
        f.inventory[0] = Item(id(ItemId.SCYTHE_EMPTY)); f.inventory[1] = Item(Items.BLOOD_RUNE, 600)
        assertEquals(CorruptedWeapons.Result.OK, CorruptedWeapons.chargeScythe(f.player, 0, id(ItemId.SCYTHE_EMPTY), 2))
        assertEquals(200, CorruptedWeapons.scytheCharges(f.inventory[0]!!)); assertEquals(0L, f.count(Items.BLOOD_RUNE))
        val last = Item(id(ItemId.SCYTHE)).putAttr(ItemAttribute.CHARGES, 1)
        assertEquals(id(ItemId.SCYTHE_EMPTY), CorruptedWeapons.consumeScythe(last).id)
        assertFalse(CorruptedWeapons.consumeScythe(last).hasAnyAttr())
        assertEquals(1, last.getAttr(ItemAttribute.CHARGES))
        assertEquals(CorruptedWeapons.Result.OK, CorruptedWeapons.unchargeScythe(f.player, 0, id(ItemId.SCYTHE)))
        assertEquals(0L, f.count(Items.BLOOD_RUNE))
    }
    @Test fun `corrupted shadow is separate tier with unchanged costs and reduced multiplier model`() {
        val staff = PoweredStaves.staffFor(id(ItemId.SHADOW))!!
        assertTrue(staff.corrupted); assertEquals(75, staff.requiredMagic); assertEquals(20_000, staff.maxCharges)
        assertEquals(listOf(Items.SOUL_RUNE to 2, Items.CHAOS_RUNE to 5), staff.chargeCost.map { it.id to it.amount })
        val charged = PoweredStaves.withCharges(Item(id(ItemId.SHADOW_EMPTY)), 5)
        assertEquals(id(ItemId.SHADOW), charged.id); assertEquals(5, PoweredStaves.charges(charged))
        assertEquals(id(ItemId.SHADOW_EMPTY), PoweredStaves.withCharges(charged, 0).id)
        assertEquals(7, CorruptedWeapons.attackSpeed(id(ItemId.DARK_BOW)))
        assertEquals(5, CorruptedWeapons.attackSpeed(id(ItemId.SHADOW)))
    }
    @Test fun `fire altar recipe is atomic costs exact ingredients and never succeeds twice`() {
        f.inventory[0] = Item(id(ItemId.FIRE_RUBY)); f.inventory[1] = Item(Items.AMULET_OF_MAGIC); f.inventory[2] = Item(Items.FIRE_RUNE, 99)
        assertEquals(FireAmulet.Result.INGREDIENTS, FireAmulet.craft(f.player)); assertEquals(1L, f.count(id(ItemId.FIRE_RUBY)))
        f.inventory[2] = Item(Items.FIRE_RUNE, 100)
        assertEquals(FireAmulet.Result.OK, FireAmulet.craft(f.player)); assertEquals(1L, f.count(id(ItemId.FIRE_AMULET)))
        assertEquals(0L, f.count(Items.FIRE_RUNE)); assertEquals(FireAmulet.Result.INGREDIENTS, FireAmulet.craft(f.player))
        f.equipment[2] = Item(id(ItemId.FIRE_AMULET))
        assertEquals(2, FireAmulet.bonus(f.player, CombatSpell.FIRE_BOLT)); assertEquals(0, FireAmulet.bonus(f.player, CombatSpell.WATER_BOLT))
        assertEquals(0, FireAmulet.bonus(f.player, null))
    }
    @Test fun `avarice handles bones notes full inventory threshold and never grants from the bank`() {
        f.define(500); f.define(501, true); f.defs.getValue(500).noteLinkId = 501; f.defs.getValue(501).noteTemplateId = 799
        f.inventory[0] = Item(id(ItemId.AVARICE))
        assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.settings(f.player, true, true, 100))
        assertFalse(DeadmanTrinkets.collectDrop(f.player, Item(500), 99))
        assertTrue(DeadmanTrinkets.collectDrop(f.player, Item(500), 100)); assertEquals(1L, f.count(501))
        f.inventory[1] = Item(501, Int.MAX_VALUE)
        assertFalse(DeadmanTrinkets.collectDrop(f.player, Item(500), 100)); assertEquals(Int.MAX_VALUE.toLong(), f.count(501))
        f.bank[0] = f.inventory[0]; f.inventory[0] = null
        assertFalse(DeadmanTrinkets.collectDrop(f.player, Item(500), 100))
    }
    @Test fun `vengeance uses the same spell cooldown without consuming the trinket`() {
        f.inventory[0] = Item(id(ItemId.VENGEANCE))
        assertEquals(DeadmanTrinkets.Result.OK, DeadmanTrinkets.vengeance(f.player))
        assertEquals(DeadmanTrinkets.Result.COOLDOWN, DeadmanTrinkets.vengeance(f.player)); assertEquals(1L, f.count(id(ItemId.VENGEANCE)))
    }
    @Test fun `khopesh uses snapshot tile and four pvp bolt times`() {
        val tile = gg.rsmod.game.model.Tile(3200, 3200)
        assertTrue(ThunderKhopesh.withinBolt(tile, tile.transform(1, 1)))
        assertFalse(ThunderKhopesh.withinBolt(tile, tile.transform(2, 0)))
        assertFalse(ThunderKhopesh.withinBolt(tile, gg.rsmod.game.model.Tile(3200, 3200, 1)))
        assertEquals(listOf(7, 9, 11, 13), ThunderKhopesh.PVP_BOLT_TICKS)
    }
    @Test fun `undead has a persistent shared summon cooldown and never displaces the familiar`() {
        val actor = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val familiar = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>()
        val reference = java.lang.ref.WeakReference(familiar)
        f.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR] = reference
        f.inventory[0] = Item(id(ItemId.UNDEAD))
        val look = io.mockk.mockk<UndeadThralls.Appearance>(relaxed = true)
        every { look.spawn(f.player, any()) } returns actor
        UndeadThralls.appearance = look
        try {
            assertEquals(UndeadThralls.Result.OK, UndeadThralls.summon(f.player, UndeadThralls.Choice.ZUK))
            assertEquals(UndeadThralls.Result.COOLDOWN, UndeadThralls.summon(f.player, UndeadThralls.Choice.WISP))
            assertSame(reference, f.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR])
            assertEquals(17, f.player.timers[UndeadThralls.SUMMON_COOLDOWN])
            assertEquals("dmm2_undead_cooldown", UndeadThralls.SUMMON_COOLDOWN.persistenceKey)
        } finally { UndeadThralls.appearance = null }
    }
    @Test fun `scythe cleave covers three adjacent squares for each facing without reaching behind or across planes`() {
        val origin = gg.rsmod.game.model.Tile(3200, 3200)
        for (dx in -1..1) for (dz in -1..1) {
            if (dx == 0 && dz == 0) continue
            val arc = CorruptedWeapons.scytheArc(origin, origin.transform(dx, dz))
            assertEquals(3, arc.distinct().size)
            assertTrue(origin.transform(dx, dz) in arc)
            assertFalse(origin in arc)
            assertFalse(origin.transform(-dx, -dz) in arc)
            assertTrue(arc.all { it.height == origin.height && it.isWithinRadius(origin, 1) })
        }
        assertTrue(CorruptedWeapons.scytheArc(origin, gg.rsmod.game.model.Tile(3201, 3200, 1)).isEmpty())
    }
    @Test fun `well refunds only whole hundred charge batches and overflow rolls back the complete scythe`() {
        f.inventory[0] = Item(id(ItemId.SCYTHE)).putAttr(ItemAttribute.CHARGES, 199)
        f.inventory[1] = Item(Items.BLOOD_RUNE, Int.MAX_VALUE)
        assertEquals(CorruptedWeapons.Result.NO_SPACE, CorruptedWeapons.unchargeScythe(f.player, 0, id(ItemId.SCYTHE), true))
        assertEquals(199, CorruptedWeapons.scytheCharges(f.inventory[0]!!))
        assertEquals(Int.MAX_VALUE.toLong(), f.count(Items.BLOOD_RUNE))
        f.inventory[1] = null
        assertEquals(CorruptedWeapons.Result.OK, CorruptedWeapons.unchargeScythe(f.player, 0, id(ItemId.SCYTHE), true))
        assertEquals(300L, f.count(Items.BLOOD_RUNE)); assertEquals(id(ItemId.SCYTHE_EMPTY), f.inventory[0]?.id)
        assertEquals(CorruptedWeapons.Result.ITEM_CHANGED, CorruptedWeapons.unchargeScythe(f.player, 0, id(ItemId.SCYTHE), true))
        assertEquals(300L, f.count(Items.BLOOD_RUNE))
    }
    @Test fun `PvP loss converts charged corrupted weapons and fortuity to exact loot without changing the source items`() {
        val death = gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
        val scythe = Item(id(ItemId.SCYTHE)).putAttr(ItemAttribute.CHARGES, 1000)
        assertTrue(death.converts(scythe.id))
        val scytheLoot = death.killerItems(scythe)!!.single()
        assertEquals(id(ItemId.SCYTHE_EMPTY), scytheLoot.id); assertFalse(scytheLoot.hasAnyAttr())
        assertEquals(1000, scythe.getAttr(ItemAttribute.CHARGES))
        val shadow = Item(id(ItemId.SHADOW)).putAttr(ItemAttribute.CHARGES, 50)
        assertEquals(id(ItemId.SHADOW_EMPTY), death.killerItems(shadow)!!.single().id)
        assertEquals(50, shadow.getAttr(ItemAttribute.CHARGES))
        val fortuityLoot = death.killerItems(Item(id(ItemId.FORTUITY)))!!
        assertEquals(2, fortuityLoot.size)
        assertEquals(id(ItemId.FORTUITY_EMPTY), fortuityLoot[0].id)
        assertEquals(Items.COINS_995, fortuityLoot[1].id)
        assertEquals(10_000_000, fortuityLoot[1].amount)
    }
    @Test fun `saved charged scythe with no charges cannot borrow charged bonuses for a free swing`() {
        f.equipment[3] = Item(id(ItemId.SCYTHE))
        val target = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>()
        assertFalse(gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy.canAttack(f.player, target))
        assertEquals(id(ItemId.SCYTHE_EMPTY), f.equipment[3]?.id)
        assertTrue(gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy.canAttack(f.player, target))
    }
    @Test fun `missing advanced weaponry mapping reserves its rare weight instead of increasing other breach rewards`() {
        val loot = gg.rsmod.plugins.content.mechanics.pvp.breach.BreachLoot
        val forced = object : java.util.Random() {
            override fun nextInt(bound: Int): Int = when (bound) {
                loot.RARE_DENOMINATOR -> 0
                loot.MEGA_RARE_CHANCE -> 1
                loot.RARE_TRIGGER -> loot.RARE_TRIGGER - 1
                else -> bound - 1
            }
            override fun nextDouble(): Double = 0.999999
        }
        assertTrue(loot.roll(forced, scrollRandom = forced).any { it.item == id(ItemId.ADVANCED_WEAPONRY) })
        Dmm2Ids.install(emptyList())
        val rareIds = loot.RARE.map { it.item }.toSet()
        assertFalse(loot.roll(forced, scrollRandom = forced).any { it.item in rareIds })
        assertEquals(133, loot.RARE_TOTAL)
    }
    @Test fun `fortuity boosts boss kills once but not ordinary or breach monster kills`() {
        val census = gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
        io.mockk.mockkObject(census)
        try {
            every { f.player.combatLevel } returns 99
            every { census.sourceOf(f.world, 9001) } returns gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.BOSS
            every { census.sourceOf(f.world, 9002) } returns gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops.Source.BREACH_MONSTER
            every { census.sourceOf(f.world, 9003) } returns null
            f.inventory[0] = Item(id(ItemId.FORTUITY))
            val drops = gg.rsmod.plugins.content.drops.DropTableFactory
            assertEquals(6.0, drops.killRate(f.player, npcId = 9001))
            assertEquals(5.0, drops.killRate(f.player, npcId = 9002))
            assertEquals(5.0, drops.killRate(f.player, npcId = 9003))
            f.bank[0] = f.inventory[0]; f.inventory[0] = null
            assertEquals(5.0, drops.killRate(f.player, npcId = 9001))
        } finally { io.mockk.unmockkObject(census) }
    }
    @Test fun `khopesh passive magic bolt does not inherit melee protection from the triggering swing`() {
        val formula = gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
        val target = io.mockk.mockk<gg.rsmod.game.model.entity.Npc>(relaxed = true)
        val look = io.mockk.mockk<ThunderKhopesh.Appearance>(relaxed = true)
        io.mockk.mockkObject(formula)
        f.equipment[3] = Item(id(ItemId.KHOPESH))
        every { f.world.randomDouble() } returns 0.0
        every { formula.getMaxHitIgnoringMeleeProtection(f.player, target) } returns 60.0
        every { formula.getMaxHit(f.player, target) } throws AssertionError("Melee protection must not reduce a magic bolt")
        ThunderKhopesh.appearance = look
        try {
            ThunderKhopesh.passive(f.player, target, false)
            io.mockk.verify(exactly = 0) { formula.getMaxHitIgnoringMeleeProtection(f.player, target) }
            ThunderKhopesh.passive(f.player, target, true)
            io.mockk.verify(exactly = 1) { formula.getMaxHitIgnoringMeleeProtection(f.player, target) }
        } finally {
            ThunderKhopesh.appearance = null
            io.mockk.unmockkObject(formula)
        }
    }
}
