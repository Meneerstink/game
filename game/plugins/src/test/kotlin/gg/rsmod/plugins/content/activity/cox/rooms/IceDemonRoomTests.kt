package gg.rsmod.plugins.content.activity.cox.rooms

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class IceDemonRoomTests {
    private val room = IceDemonRoom(mockk(relaxed = true), 0, mockk(relaxed = true))

    @Test fun `fire damage already boosted by the formula is not multiplied again`() {
        val hit = spellHit(CombatSpell.FIRE_WAVE, 50)
        room.resist(hit)
        assertEquals(50, hit.hitmarks.single().damage)
    }

    @Test fun `non fire spells still deal one third damage rounded down`() {
        val hit = spellHit(CombatSpell.WATER_WAVE, 50)
        room.resist(hit)
        assertEquals(16, hit.hitmarks.single().damage)
    }

    private fun spellHit(spell: CombatSpell, damage: Int): Hit {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap().also { it[Combat.CASTING_SPELL] = spell }
        every { player.equipment } returns ItemContainer(DefinitionSet(), EQUIPMENT_KEY)
        return Hit.Builder().addHit(damage, 0).build().also {
            it.source = player
            it.sourceCombatClass = CombatClass.MAGIC
        }
    }
}
