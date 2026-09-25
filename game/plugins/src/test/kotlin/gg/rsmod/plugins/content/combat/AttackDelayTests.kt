package gg.rsmod.plugins.content.combat

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.LAST_KNOWN_WEAPON_TYPE
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.WeaponType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AttackDelayTests {
    @Test
    fun missingOrInvalidWeaponMetadataDoesNotCreateOneTickAttacks() {
        for (speed in listOf(-1, 0)) {
            assertEquals(4, CombatConfigs.getAttackDelay(player(speed)))
            assertEquals(3, CombatConfigs.getAttackDelay(player(speed, rapid = true)))
        }
    }

    @Test
    fun explicitWeaponSpeedsAndRapidAdjustmentArePreserved() {
        for (speed in listOf(1, 4, 5, 6, 9)) {
            assertEquals(speed, CombatConfigs.getAttackDelay(player(speed)))
            assertEquals(maxOf(1, speed - 1), CombatConfigs.getAttackDelay(player(speed, rapid = true)))
        }
    }

    /**
     * Owner 2026-09-24 ("ik kan entangle spammen achter elkaar op npc"): MagicCombatStrategy.attack clears a manual cast before
     * Combat.postAttack reads the delay, so the delay fell back to the weapon's speed (a 2-tick weapon recast every 2 ticks). The spell
     * of the attack now decides: 5 ticks with any weapon, and with no weapon at all.
     */
    @Test
    fun aManualCastAlwaysWaitsTheSpellDelayWhateverTheWeapon() {
        for (speed in listOf(2, 3, 4, 6)) {
            val p = player(speed)
            p.attr[Combat.SPELL_OF_THIS_ATTACK] = gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ENTANGLE
            assertEquals(5, CombatConfigs.getAttackDelay(p))
        }
        val unarmed = player(4)
        unarmed.equipment[3] = null
        unarmed.attr[Combat.SPELL_OF_THIS_ATTACK] = gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.ENTANGLE
        assertEquals(5, CombatConfigs.getAttackDelay(unarmed))
    }

    private fun player(speed: Int, rapid: Boolean = false): Player {
        val item = ItemDef(18353).apply { attackSpeed = speed }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, 18353) } returns item
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.entityType } returns EntityType.PLAYER
        val attributes = AttributeMap()
        attributes[LAST_KNOWN_WEAPON_TYPE] = if (rapid) WeaponType.BOW.id else WeaponType.HAMMER.id
        every { player.attr } returns attributes
        every { player.varps.getState(any()) } returns if (rapid) 1 else 0
        every { player.equipment } returns ItemContainer(definitions, EQUIPMENT_KEY).apply {
            this[3] = Item(18353)
        }
        return player
    }
}
