package gg.rsmod.game.action

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * Q-010 (RSPS_2DAY_DONOR_IMPORT_PLAN.md): regression coverage for [EquipAction] against real
 * 667 item definitions - the slot-conflict matrix (1h swap, shield<->2h, level gate, and the
 * no-free-space guard) is the "whole set" for this subsystem since there is no fixed roster.
 */
class EquipActionTests {
    @Test
    fun `equipping a one-handed weapon into empty slots succeeds`() {
        val player = newPlayer()

        val result = EquipAction.equip(player, Item(Items.RUNE_SCIMITAR))

        assertEquals(EquipAction.Result.SUCCESS, result)
        assertEquals(Items.RUNE_SCIMITAR, player.equipment[EquipmentType.WEAPON.id]?.id)
    }

    @Test
    fun `equipping a shield unequips the two-handed weapon it conflicts with`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.RUNE_2H_SWORD)

        val result = EquipAction.equip(player, Item(Items.RUNE_KITESHIELD))

        assertEquals(EquipAction.Result.SUCCESS, result)
        assertEquals(Items.RUNE_KITESHIELD, player.equipment[EquipmentType.SHIELD.id]?.id)
        assertNull(player.equipment[EquipmentType.WEAPON.id], "2h sword must be displaced by the shield")
        assertEquals(1, player.inventory.getItemCount(Items.RUNE_2H_SWORD), "displaced 2h sword must land in the inventory")
    }

    @Test
    fun `equipping a two-handed weapon unequips both the current weapon and shield`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.RUNE_SCIMITAR)
        player.equipment[EquipmentType.SHIELD.id] = Item(Items.RUNE_KITESHIELD)

        val result = EquipAction.equip(player, Item(Items.RUNE_2H_SWORD))

        assertEquals(EquipAction.Result.SUCCESS, result)
        assertEquals(Items.RUNE_2H_SWORD, player.equipment[EquipmentType.WEAPON.id]?.id)
        assertNull(player.equipment[EquipmentType.SHIELD.id])
        assertEquals(1, player.inventory.getItemCount(Items.RUNE_SCIMITAR))
        assertEquals(1, player.inventory.getItemCount(Items.RUNE_KITESHIELD))
    }

    @Test
    fun `equip is refused below the item's level requirement and nothing is equipped`() {
        val player = newPlayer(attackLevel = 1)

        val result = EquipAction.equip(player, Item(Items.DRAGON_LONGSWORD))

        assertEquals(EquipAction.Result.FAILED_REQUIREMENTS, result)
        assertNull(player.equipment[EquipmentType.WEAPON.id])
    }

    @Test
    fun `equip meeting the level requirement succeeds`() {
        val player = newPlayer(attackLevel = 99)

        val result = EquipAction.equip(player, Item(Items.DRAGON_LONGSWORD))

        assertEquals(EquipAction.Result.SUCCESS, result)
        assertEquals(Items.DRAGON_LONGSWORD, player.equipment[EquipmentType.WEAPON.id]?.id)
    }

    @Test
    fun `a two-handed swap that has no free inventory space is refused and nothing changes`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.RUNE_SCIMITAR)
        player.equipment[EquipmentType.SHIELD.id] = Item(Items.RUNE_KITESHIELD)
        fillInventory(player.inventory)
        assertEquals(0, player.inventory.freeSlotCount)

        val result = EquipAction.equip(player, Item(Items.RUNE_2H_SWORD))

        assertEquals(EquipAction.Result.NO_FREE_SPACE, result)
        assertEquals(Items.RUNE_SCIMITAR, player.equipment[EquipmentType.WEAPON.id]?.id, "weapon must be untouched on failure")
        assertEquals(Items.RUNE_KITESHIELD, player.equipment[EquipmentType.SHIELD.id]?.id, "shield must be untouched on failure")
    }

    @Test
    fun `unequip returns the item to the inventory and clears the slot`() {
        val player = newPlayer()
        player.equipment[EquipmentType.WEAPON.id] = Item(Items.RUNE_SCIMITAR)

        val result = EquipAction.unequip(player, EquipmentType.WEAPON.id)

        assertEquals(EquipAction.Result.SUCCESS, result)
        assertNull(player.equipment[EquipmentType.WEAPON.id])
        assertEquals(1, player.inventory.getItemCount(Items.RUNE_SCIMITAR))
    }

    /** Fills every slot with a distinct non-stackable item id - a stackable item would only
     * ever occupy one slot no matter how many times it is added, leaving [freeSlotCount] stuck
     * above zero forever. */
    private fun fillInventory(inventory: ItemContainer) {
        var itemId = Items.RUNE_SCIMITAR
        while (inventory.freeSlotCount > 0) {
            inventory.add(item = itemId, amount = 1)
            itemId++
        }
    }

    private fun newPlayer(attackLevel: Int = 99): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)

        val plugins = mockk<PluginRepository>(relaxed = true)
        every { plugins.executeEquipItemRequirement(any(), any()) } returns true
        every { plugins.canUnequipSlot(any(), any()) } returns true
        every { world.plugins } returns plugins

        val skills = SkillSet(maxSkills = Skills.MAGIC + 1)
        skills.setBaseLevel(Skills.ATTACK, attackLevel)
        skills.setCurrentLevel(Skills.ATTACK, attackLevel)
        // Defence is not the axis under test anywhere except via `attackLevel`-gated weapons;
        // fix it at 99 so shield equips (e.g. rune kiteshield, Defence 40) don't spuriously fail.
        skills.setBaseLevel(Skills.DEFENCE, 99)
        skills.setCurrentLevel(Skills.DEFENCE, 99)
        every { player.skills } returns skills
        return player
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        /**
         * [ItemDef.equipSlot]/[ItemDef.equipType]/[ItemDef.skillReqs] are populated in production
         * by [gg.rsmod.game.service.game.ItemMetadataService] from `data/cfg/items.yml` at world
         * boot, not by the raw cache decode - a bare [DefinitionSet.loadAll] leaves them at their
         * -1/0/null defaults. These values are copied verbatim from the real entries in
         * `data/cfg/items.yml` (ids 1201, 1305, 1319, 1333) so this test exercises the real
         * slot-conflict data instead of inventing it.
         */
        private fun applyRealEquipmentMetadata() {
            fun apply(
                id: Int,
                equipSlot: Int,
                equipType: Int,
                skill: Int,
                level: Int,
            ) {
                val def = DEFINITIONS.get(ItemDef::class.java, id)
                def.equipSlot = equipSlot
                def.equipType = equipType
                val reqs = it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap()
                reqs[skill.toByte()] = level.toByte()
                def.skillReqs = reqs
            }
            apply(Items.RUNE_KITESHIELD, equipSlot = 5, equipType = -1, skill = Skills.DEFENCE, level = 40)
            apply(Items.DRAGON_LONGSWORD, equipSlot = 3, equipType = -1, skill = Skills.ATTACK, level = 60)
            apply(Items.RUNE_2H_SWORD, equipSlot = 3, equipType = 5, skill = Skills.ATTACK, level = 40)
            apply(Items.RUNE_SCIMITAR, equipSlot = 3, equipType = -1, skill = Skills.ATTACK, level = 40)
        }

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
            applyRealEquipmentMetadata()
        }
    }
}
