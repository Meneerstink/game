package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Combat run 2026-09-29: player ranged attack ranges against the OSRS Wiki infobox values (reference\weapons\osrs_weapons.json,
 * `weapon_attack_range`) for the Deadman-relevant ranged weapons. A weapon missing from RangedCombatStrategy's table fell back to 7:
 * the blowpipes fired from 7 tiles instead of 5 and the 8-tile crossbows came one tile short (a frozen crossbow never reached 8).
 */
class RangedAttackRangeTests {
    private val wiki =
        mapOf(
            Items.TOXIC_BLOWPIPE to 5,
            Items.BLAZING_BLOWPIPE to 5,
            Items.CAMPHOR_BLOWPIPE to 5,
            Items.IRONWOOD_BLOWPIPE to 5,
            Items.ROSEWOOD_BLOWPIPE to 5,
            Items.ARMADYL_CROSSBOW to 8,
            Items.ZARYTE_CROSSBOW to 8,
            Items.HUNTERS_CROSSBOW to 8,
            Items.HUNTERS_SUNLIGHT_CROSSBOW to 8,
            Items.KARILS_CROSSBOW to 8,
            Items.KARILS_CROSSBOW_50 to 8,
            Items.DRAGON_THROWNAXE to 4,
            Items.MORRIGANS_THROWING_AXE to 4,
            Items.MORRIGANS_JAVELIN to 5,
            Items.DRAGON_KNIFE to 4,
            Items.TWISTED_BOW to 10,
            Items.HEAVY_BALLISTA to 9,
            Items.THIRDAGE_BOW to 9,
            Items.SCORCHING_BOW to 10,
            Items.ECLIPSE_ATLATL to 5,
            Items.MAGIC_LONGBOW to 10,
        )

    @Test
    fun `ranged weapons use their OSRS Wiki attack range`() {
        val wrong =
            wiki.mapNotNull { (id, range) ->
                val measured = RangedCombatStrategy.getAttackRange(player(id))
                if (measured == range) null else "$id wiki=$range server=$measured"
            }
        assertEquals(emptyList(), wrong)
    }

    private fun player(weaponId: Int): Player {
        val definitions = mockk<DefinitionSet>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.entityType } returns EntityType.PLAYER
        val attributes = AttributeMap()
        attributes[LAST_KNOWN_WEAPON_TYPE] = WeaponType.BOW.id
        every { player.attr } returns attributes
        every { player.varps.getState(any()) } returns 0
        every { player.equipment } returns ItemContainer(definitions, EQUIPMENT_KEY).apply { this[3] = Item(weaponId) }
        return player
    }
}
