package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.XpMode
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.content.combat.CombatConfigs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit C-02: OSRS combat experience per 1:1 damage point (4 / 1.33 each / 2 magic / 1.33 Hitpoints). */
class CombatXpRateTests {
    private val xp = HashMap<Int, Double>()

    @BeforeTest
    fun setUp() {
        mockkObject(CombatConfigs)
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        xp.clear()
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        unmockkObject(CombatConfigs)
    }

    private fun record(
        skill: Int,
        amount: Double,
    ) {
        xp[skill] = (xp[skill] ?: 0.0) + amount
    }

    private fun attacker(mode: XpMode): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { CombatConfigs.getXpMode(player) } returns mode
        // The brawling-gloves extension (named argument selects it); the receiver is the first argument.
        every { player.addXp(any(), any(), checkBrawlingGloves = any()) } answers {
            record(secondArg(), thirdArg())
            1.0
        }
        // The member Player.addXp (Hitpoints and the shared split).
        every { player.addXp(any<Int>(), any<Double>(), any<Boolean>(), any<Boolean>()) } answers {
            record(firstArg(), secondArg())
        }
        return player
    }

    private fun target(): Player {
        val target = mockk<Player>(relaxed = true)
        every { target.entityType } returns EntityType.PLAYER
        return target
    }

    @Test
    fun `ten melee damage on accurate gives 40 Attack and 13_3 Hitpoints experience`() {
        MeleeCombatStrategy.addCombatXp(attacker(XpMode.ATTACK_XP), target(), 10)
        assertEquals(40.0, xp[Skills.ATTACK]!!, 1e-9)
        assertEquals(40.0 / 3.0, xp[Skills.CONSTITUTION]!!, 1e-9)
    }

    @Test
    fun `controlled melee gives 1_33 to each melee skill`() {
        MeleeCombatStrategy.addCombatXp(attacker(XpMode.SHARED_XP), target(), 3)
        listOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.CONSTITUTION).forEach {
            assertEquals(4.0, xp[it]!!, 1e-9, "skill $it")
        }
    }

    @Test
    fun `ranged gives 4 Ranged per damage and longrange 2 plus 2 Defence`() {
        RangedCombatStrategy.addCombatXp(attacker(XpMode.RANGED_XP), target(), 10)
        assertEquals(40.0, xp[Skills.RANGED]!!, 1e-9)
        xp.clear()
        RangedCombatStrategy.addCombatXp(attacker(XpMode.SHARED_XP), target(), 10)
        assertEquals(20.0, xp[Skills.RANGED]!!, 1e-9)
        assertEquals(20.0, xp[Skills.DEFENCE]!!, 1e-9)
    }

    @Test
    fun `offensive magic gives the spell experience plus 2 Magic per damage`() {
        MagicCombatStrategy.addCombatXp(attacker(XpMode.MAGIC_XP), target(), 10, baseXp = 52.0)
        assertEquals(52.0 + 20.0, xp[Skills.MAGIC]!!, 1e-9)
        assertEquals(40.0 / 3.0, xp[Skills.CONSTITUTION]!!, 1e-9)
    }
}
