package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.EnchantedBolts
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.items.osrs.DizanasQuiver
import gg.rsmod.plugins.content.items.osrs.PoweredStaves
import gg.rsmod.plugins.content.mechanics.deadman.items.*
import io.mockk.*
import kotlin.test.*

class ArchiveWeaponsTests {
    private fun ids() {
        Dmm2Ids.install(listOf(34725 to 9100, 34727 to 9101, 34681 to 9102, 34684 to 9103,
            34690 to 9104, 34696 to 9105, 34702 to 9106, 34708 to 9107, 34669 to 9108)
            .map { "item\t${it.first}\t${it.second}" })
    }

    @Test fun `tome runes roundtrip, cap, shortage and full inventory are atomic`() {
        ids()
        val f = DeadmanPlayerFixture()
        listOf(9100, 9101, 9999).forEach { f.define(it) }
        listOf(Items.ASTRAL_RUNE, Items.NATURE_RUNE).forEach { f.define(it, stackable = true) }
        val empty = Item(9101)
        f.inventory[0] = empty
        f.inventory.add(Items.ASTRAL_RUNE, 9)
        assertEquals(0, ZoryaCharges.charge(f.player, 0, empty, 3))
        assertEquals(9, f.inventory.getItemCount(Items.ASTRAL_RUNE))
        f.inventory.add(Items.NATURE_RUNE, 4)
        assertEquals(2, ZoryaCharges.charge(f.player, 0, empty, 3))
        assertEquals(2, PoweredStaves.charges(f.inventory[0]!!))
        assertEquals(0, ZoryaCharges.charge(f.player, 0, empty, 1), "stale prompt")
        assertTrue(ZoryaCharges.uncharge(f.player, 0, f.inventory[0]!!))
        assertEquals(9, f.inventory.getItemCount(Items.ASTRAL_RUNE))
        assertEquals(4, f.inventory.getItemCount(Items.NATURE_RUNE))
        assertFalse(ZoryaCharges.uncharge(f.player, 0, f.inventory[0]!!), "no duplicate refund")
        assertEquals(1, PoweredStaves.chargesAffordable(PoweredStaves.Staff.ZORYA, 19999) { 100000 })
        val charged = PoweredStaves.withCharges(Item(9100), 1)
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(9999) }
        f.inventory[0] = charged
        assertFalse(ZoryaCharges.uncharge(f.player, 0, charged))
        assertSame(charged, f.inventory[0])
        assertEquals(1, PoweredStaves.charges(charged))
    }

    @Test fun `worn Charge uses inventory runes and refuses a stale equipment prompt`() {
        ids()
        val f = DeadmanPlayerFixture()
        f.define(9100); f.define(9101)
        f.define(Items.ASTRAL_RUNE, stackable = true); f.define(Items.NATURE_RUNE, stackable = true)
        val book = PoweredStaves.withCharges(Item(9100), 1)
        f.equipment[EquipmentType.WEAPON.id] = book
        f.inventory.add(Items.ASTRAL_RUNE, 3); f.inventory.add(Items.NATURE_RUNE, 2)
        assertEquals(1, ZoryaCharges.charge(f.player, EquipmentType.WEAPON.id, book, 1, equipped = true))
        assertEquals(2, PoweredStaves.charges(f.equipment[EquipmentType.WEAPON.id]!!))
        assertEquals(0, f.inventory.getItemCount(Items.ASTRAL_RUNE))
        assertEquals(0, ZoryaCharges.charge(f.player, EquipmentType.WEAPON.id, book, 1, equipped = true))
    }

    @Test fun `Zorya successful specs store five after the second empowered spec and refresh expiry`() {
        val f = DeadmanPlayerFixture()
        var cycle = 10
        var teleported = -1
        every { f.world.currentCycle } answers { cycle }
        every { f.player.lastTeleportCycle } answers { teleported }
        assertEquals(3, ZoryaTome.prepare(f.player, true).delay)
        ZoryaTome.successfulSpecial(f.player)
        assertEquals(3, ZoryaTome.remaining(f.player))
        val second = ZoryaTome.prepare(f.player, true)
        assertEquals(4.0, second.accuracy)
        assertEquals(2.2, second.damage)
        assertEquals(2, second.delay)
        ZoryaTome.successfulSpecial(f.player)
        assertEquals(5, ZoryaTome.remaining(f.player))
        cycle = 59
        assertEquals(1.6, ZoryaTome.prepare(f.player, false).damage)
        cycle = 60
        assertEquals(4, ZoryaTome.remaining(f.player), "using a buff resets 30s expiry")
        teleported = 60
        assertEquals(0, ZoryaTome.remaining(f.player))
        ZoryaTome.successfulSpecial(f.player)
        cycle = 110
        assertEquals(0, ZoryaTome.remaining(f.player))
        assertEquals(1.0, ZoryaTome.prepare(f.player, false).damage)
    }

    @Test fun `missing assets and zero charge never fire or spend energy`() {
        ids()
        val f = DeadmanPlayerFixture()
        f.define(9100)
        f.equipment[EquipmentType.WEAPON.id] = Item(9100)
        val target = mockk<Npc>(relaxed = true)
        assertFalse(gg.rsmod.plugins.content.combat.strategy.PoweredStaffCombatStrategy.canAttack(f.player, target))
        assertFalse(gg.rsmod.plugins.content.combat.strategy.ZoryaSpell.fire(f.player, target, false))
        assertEquals(0, ZoryaTome.remaining(f.player))
        assertFalse(gg.rsmod.plugins.content.combat.strategy.ZoryaSpell.fire(f.player, mockk<Player>(), true))
    }

    @Test fun `Zorya uses the current short flight hit delay at every attack range`() {
        assertEquals(listOf(2, 2, 2, 2, 3, 3, 3, 3, 3), (1..9).map(ZoryaTome::hitDelay))
    }

    @Test fun `a special below 50 percent is refused before firing`() {
        ids()
        val f = DeadmanPlayerFixture()
        f.equipment[EquipmentType.WEAPON.id] = Item(9100)
        val varps = gg.rsmod.game.model.varp.VarpSet(setOf(300, 301))
        varps[300].state = 499
        every { f.player.varps } returns varps
        var fired = false
        SpecialAttacks.register(ZoryaTome.SPECIAL_ENERGY, 9100) { fired = true }
        assertEquals(SpecialAttacks.Outcome.NOT_USED, SpecialAttacks.perform(f.player, mockk<Npc>(), f.world))
        assertFalse(fired)
        assertEquals(499, varps.getState(300))
    }

    @Test fun `shards do not turn into floor bolts when inventory or output stack is full`() {
        ids()
        val f = DeadmanPlayerFixture()
        every { f.player.skills } returns mockk(relaxed = true)
        every { f.player.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.FLETCHING) } returns 86
        f.define(9108, stackable = true); f.define(9103, stackable = true)
        f.define(Items.CHISEL); f.define(9999)
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(9999) }
        f.inventory[0] = Item(Items.CHISEL)
        f.inventory[1] = Item(9108, 2)
        assertFalse(AscensionCrafting.carve(f.player))
        assertEquals(2, f.inventory[1]!!.amount)
        f.inventory[2] = Item(9103, Int.MAX_VALUE)
        assertFalse(AscensionCrafting.carve(f.player))
        assertEquals(2, f.inventory[1]!!.amount)
        f.inventory[2] = null
        assertTrue(AscensionCrafting.carve(f.player))
        assertEquals(1, f.inventory.getItemCount(9108))
        assertEquals(10, f.inventory.getItemCount(9103))
    }

    @Test fun `crossbows only accept their own bolts and other crossbows cannot fire Ascension bolts`() {
        ids()
        assertEquals(setOf(9103, 9104, 9105, 9106, 9107), RangedAmmo.validAmmo(9102)!!.toSet())
        assertTrue(ArchiveWeapons.accepts(9102, 9105))
        assertFalse(ArchiveWeapons.accepts(9102, Items.DIAMOND_BOLTS_E))
        assertFalse(RangedAmmo.validAmmo(Items.RUNE_CROSSBOW)!!.contains(9105))
        assertTrue(DizanasQuiver.isArrowOrBolt(9105))
        val f = DeadmanPlayerFixture()
        f.equipment[EquipmentType.WEAPON.id] = Item(Items.RUNE_CROSSBOW)
        val shot = EnchantedBolts.resolve(f.player, mockk<Npc>(), 9105, false, 40.0, EnchantedBolts.Special.ZARYTE_EVOKE, 0.0)
        assertNull(shot.bolt)
        assertFalse(shot.landHit)
    }

    @Test fun `Ascension bolt proc chances are halved once, diamond ignores misses and onyx needs a hit`() {
        ids()
        val diamond = EnchantedBolts.boltFor(9105)!!
        val onyx = EnchantedBolts.boltFor(9107)!!
        assertEquals(0.05, EnchantedBolts.chance(diamond, mockk<Npc>(), EnchantedBolts.Special.NONE))
        assertEquals(0.025, EnchantedBolts.chance(diamond, mockk<Player>(), EnchantedBolts.Special.NONE))
        assertFalse(diamond.needsSuccessfulHit)
        assertEquals(0.055, onyx.monsterChance)
        assertEquals(0.05, onyx.playerChance)
        assertTrue(onyx.needsSuccessfulHit)
        assertEquals(3, ArchiveWeapons.baseSpeed(false))
        assertEquals(4, ArchiveWeapons.baseSpeed(true))
    }

    @Test fun `a lost charged tome produces one empty tome without charge attributes`() {
        ids()
        val charged = PoweredStaves.withCharges(Item(9100), 123)
        val loot = gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables.killerItems(charged)!!
        assertEquals(1, loot.size)
        assertEquals(9101, loot.single().id)
        assertEquals(1, loot.single().amount)
        assertEquals(0, PoweredStaves.charges(loot.single()))
        assertFalse(loot.single().hasAnyAttr())
        assertEquals(123, PoweredStaves.charges(charged), "conversion reads the death item without mutating its source")
    }

    @Test fun `Ascension firing alternates between left and right across targets`() {
        ids()
        Dmm2Ids.install(listOf("seq\t15113\t9200", "seq\t15114\t9201", "seq\t15115\t9202", "seq\t15116\t9203"))
        val f = DeadmanPlayerFixture()
        assertEquals(9201, ArchiveWeapons.nextAnimation(f.player, false))
        assertEquals(9202, ArchiveWeapons.nextAnimation(f.player, true))
        assertEquals(9201, ArchiveWeapons.nextAnimation(f.player, false))
    }
}
