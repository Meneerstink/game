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

    /**
     * Live log 2026-09-25: "Invalid attack style" aborted every npc hit on a player autocasting with a staff - the OSRS Spell box
     * sets the style varp to 3, which a staff's 3-style table lacks. Autocasting gives no invisible bonus (OSRS Wiki "Attack styles").
     */
    @Test
    fun theAutocastSpellBoxHasNoInvisibleStyleBonusInsteadOfThrowing() {
        val p = player(4)
        p.attr[LAST_KNOWN_WEAPON_TYPE] = WeaponType.STAFF.id
        every { p.varps.getState(any()) } returns gg.rsmod.plugins.content.combat.magic.Autocast.AUTOCAST_STYLE
        assertEquals(gg.rsmod.game.model.combat.WeaponStyle.NONE, CombatConfigs.getAttackStyle(p))
    }

    /** Owner 2026-09-25: a fight that ends (weapon switch without ammo, "already under attack") must stop facing its target. */
    @Test
    fun endingAFightStopsFacingItsTargetUnlessAManualCastKeepsIt() {
        val target = mockk<Player>(relaxed = true)
        for (keep in listOf(false, true)) {
            val p = player(4)
            p.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR] = java.lang.ref.WeakReference(target)
            p.attr[gg.rsmod.game.model.attr.FACING_PAWN_ATTR] = java.lang.ref.WeakReference(target)
            Combat.reset(p, keepFacing = keep)
            io.mockk.verify(exactly = if (keep) 0 else 1) { p.resetFacePawn() }
            org.junit.Assert.assertNull(p.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR])
        }
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
