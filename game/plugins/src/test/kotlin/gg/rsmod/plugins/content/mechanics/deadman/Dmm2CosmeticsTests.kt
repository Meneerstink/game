package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.CrystalEquipment
import gg.rsmod.plugins.content.items.osrs.StaffOfTheDead
import gg.rsmod.plugins.content.combat.magic.AutocastWeapons
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import kotlin.test.*

class Dmm2CosmeticsTests {
    private val f = DeadmanPlayerFixture()
    init {
        val skins = listOf(
            NigelRewardStore.Skin(NigelRewardStore.Reward.ANNIHILATION_WEAPONS, Items.CRYSTAL_BODY, 1800),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ANNIHILATION_WEAPONS, Items.CRYSTAL_BODY_INACTIVE, 1801),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ANNIHILATION_WEAPONS, Items.BOW_OF_FAERDHINEN_C, 1802),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ANNIHILATION_WEAPONS, Items.TOXIC_STAFF_OF_THE_DEAD, 1803),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ANNIHILATION_WEAPONS, Items.TOXIC_STAFF_UNCHARGED, 1804),
            NigelRewardStore.Skin(NigelRewardStore.Reward.CAPE_FABRIC, Items.IMBUED_ZAMORAK_CAPE, 1805),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ARMAGEDDON_WEAPONS, Items.ARMADYL_GODSWORD, 1806),
            NigelRewardStore.Skin(NigelRewardStore.Reward.ARMAGEDDON_WEAPONS, Items.DARK_BOW, 1807),
        )
        NigelRewardStore.install(NigelRewardStore.Contract(emptyMap(), skins, null))
        skins.forEach { f.define(it.base); f.define(it.cosmetic) }
        f.define(Items.ZULRAHS_SCALES, true); f.define(Items.CRYSTAL_SHARD, true)
    }
    @Test fun `crystal armour grants normal bonuses and changes to the matching cosmetic inactive state at zero`() {
        f.equipment[4] = Item(1800).putAttr(ItemAttribute.CHARGES, 1)
        assertEquals(3, CrystalEquipment.armourWeight(f.player))
        assertTrue(CrystalEquipment.isCrystalBow(1802))
        CrystalEquipment.onHitReceived(f.player)
        assertEquals(1801, f.equipment[4]?.id); assertEquals(0, CrystalEquipment.charges(f.equipment[4]!!))
        assertEquals(0, CrystalEquipment.armourWeight(f.player))
        val charged = CrystalEquipment.withCharges(f.equipment[4]!!, 100)
        assertEquals(1800, charged.id); assertEquals(100, CrystalEquipment.charges(charged))
        assertEquals(0, CrystalEquipment.charges(f.equipment[4]!!))
    }
    @Test fun `toxic staff scales persist through cosmetic depletion recharging and rune saving identity`() {
        val item = Item(1803).putAttr(ItemAttribute.CHARGES, 15)
        f.equipment[3] = item
        assertTrue(StaffOfTheDead.isWieldingDeadStaff(f.player)); assertTrue(StaffOfTheDead.isWieldingPowerOfDeathStaff(f.player))
        assertEquals(15, StaffOfTheDead.scales(item))
        val empty = StaffOfTheDead.withScales(item, 0)
        assertEquals(1804, empty.id); assertEquals(0, StaffOfTheDead.scales(empty)); assertEquals(15, StaffOfTheDead.scales(item))
        val recharged = StaffOfTheDead.withScales(empty, 10)
        assertEquals(1803, recharged.id); assertEquals(10, StaffOfTheDead.scales(recharged))
        assertEquals(Items.TOXIC_STAFF_OF_THE_DEAD, DeadmanCosmetics.base(1803))
    }
    @Test fun `manual and autocast god spell compatibility sees the underlying cosmetic staff`() {
        val def = f.defs.getValue(1803)
        def.name = "Toxic staff (Deadman)"
        def.weaponType = gg.rsmod.plugins.api.WeaponType.BLADED_STAFF.id
        assertNull(AutocastWeapons.incompatibility(def, CombatSpell.FLAMES_OF_ZAMORAK, f.player))
        assertNotNull(AutocastWeapons.incompatibility(def, CombatSpell.SARADOMIN_STRIKE, f.player))
    }
    @Test fun `skin charge transactions preserve state and full inventory refunds never overflow`() {
        f.inventory[0] = Item(1804); f.inventory[1] = Item(Items.ZULRAHS_SCALES, 20)
        assertEquals(DeadmanCosmetics.Result.OK, DeadmanCosmetics.charge(f.player, 0, 1804, Items.ZULRAHS_SCALES))
        assertEquals(1803, f.inventory[0]?.id); assertEquals(20, StaffOfTheDead.scales(f.inventory[0]!!))
        f.inventory[1] = Item(Items.ZULRAHS_SCALES, Int.MAX_VALUE)
        assertEquals(DeadmanCosmetics.Result.NO_SPACE, DeadmanCosmetics.unchargeToxic(f.player, 0, 1803))
        assertEquals(1803, f.inventory[0]?.id); assertEquals(20, StaffOfTheDead.scales(f.inventory[0]!!))
        f.inventory[1] = null
        assertEquals(DeadmanCosmetics.Result.OK, DeadmanCosmetics.unchargeToxic(f.player, 0, 1803))
        assertEquals(1804, f.inventory[0]?.id); assertEquals(20L, f.count(Items.ZULRAHS_SCALES))
        f.inventory[2] = Item(1801); f.inventory[3] = Item(Items.CRYSTAL_SHARD, 2)
        assertEquals(DeadmanCosmetics.Result.OK, DeadmanCosmetics.charge(f.player, 2, 1801, Items.CRYSTAL_SHARD))
        assertEquals(1800, f.inventory[2]?.id); assertEquals(200, CrystalEquipment.charges(f.inventory[2]!!))
    }
    @Test fun `cosmetic weapons reach ordinary base loot but god capes retain their base break route`() {
        val death = gg.rsmod.plugins.content.mechanics.death.PvpDeathBreakables
        assertTrue(death.converts(1806))
        assertEquals(Items.ARMADYL_GODSWORD, death.killerItems(Item(1806))!!.single().id)
        assertFalse(death.converts(1805))
        assertEquals(death.breakableFor(Items.IMBUED_ZAMORAK_CAPE), death.breakableFor(1805))
    }
    @Test fun `cosmetic dark bow retains two arrow identity and matching god cape retains Charge bonus`() {
        assertTrue(1807 in gg.rsmod.plugins.content.combat.strategy.ranged.weapon.Bows.DARK_BOWS)
        assertEquals(Items.DARK_BOW, CorruptedWeapons.bowBase(1807))
        val magic = gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
        assertTrue(magic.chargeApplies(CombatSpell.FLAMES_OF_ZAMORAK, 1805))
        assertFalse(magic.chargeApplies(CombatSpell.SARADOMIN_STRIKE, 1805))
        assertFalse(magic.chargeApplies(CombatSpell.FLAMES_OF_ZAMORAK, null))
    }
}
