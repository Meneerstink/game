package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.ranged.RangedAmmo
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanPlayerFixture
import io.mockk.every
import io.mockk.mockk
import kotlin.test.*

class StarterWeaponCombatTests {
    private val f = DeadmanPlayerFixture()
    private val ids = DeadmanStarterPack.Ids(700, 701, 702, 703, 704, 705, 706, 707)
    init { DeadmanStarterPack.install(ids) }
    @AfterTest fun cleanup() { StarterWeaponCombat.staffAppearance = null; StarterWeaponCombat.bowAppearance = null }

    @Test fun `all starter weapons are refused by the shared PvP gate`() {
        val target = DeadmanPlayerFixture().player
        every { f.player.tile } returns Tile(3200, 3200)
        every { target.tile } returns Tile(3201, 3200)
        listOf(ids.sword, ids.bow, ids.staff).forEach {
            f.equipment[EquipmentType.WEAPON.id] = Item(it)
            assertFalse(Combat.canEngage(f.player, target))
        }
    }
    @Test fun `bow generates ammunition and cannot attack with no charges or missing AV contract`() {
        val npc = mockk<Npc>()
        val bow = Item(ids.bow).putAttr(ItemAttribute.CHARGES, 2000)
        f.equipment[EquipmentType.WEAPON.id] = bow
        assertNull(RangedAmmo.fired(f.player))
        assertFalse(RangedCombatStrategy.canAttack(f.player, npc))
        StarterWeaponCombat.bowAppearance = StarterWeaponCombat.Appearance(1, 2, 3, 4)
        assertTrue(RangedCombatStrategy.canAttack(f.player, npc))
        f.equipment[EquipmentType.WEAPON.id] = Item(ids.bow).putAttr(ItemAttribute.CHARGES, 0)
        assertFalse(RangedCombatStrategy.canAttack(f.player, npc))
    }
    @Test fun `staff routes to built in strategy with no rune spell or level requirement and rejects players`() {
        f.equipment[EquipmentType.WEAPON.id] = Item(ids.staff).putAttr(ItemAttribute.CHARGES, 500)
        StarterWeaponCombat.staffAppearance = StarterWeaponCombat.Appearance(1, 2, 3, 4)
        assertSame(StarterWeaponCombat, CombatConfigs.getCombatStrategy(f.player))
        assertTrue(StarterWeaponCombat.canAttack(f.player, mockk<Npc>()))
        assertFalse(StarterWeaponCombat.canAttack(f.player, DeadmanPlayerFixture().player))
        f.equipment[EquipmentType.WEAPON.id] = Item(ids.staff).putAttr(ItemAttribute.CHARGES, 0)
        assertFalse(StarterWeaponCombat.canAttack(f.player, mockk<Npc>()))
    }
    @Test fun `2000 bow attacks and 500 staff casts cannot regenerate charges`() {
        listOf(ids.bow to 2000, ids.staff to 500).forEach { (id, count) ->
            var item: Item? = Item(id).putAttr(ItemAttribute.CHARGES, count)
            repeat(count) { item = DeadmanStarterPack.afterAttack(item!!) }
            assertNull(item)
        }
    }
}
