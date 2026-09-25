package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Audit C-15: a special that cannot be performed costs no energy and starts no attack. */
class SpecialAttackEnergyRefundTests {
    private var energy = 100

    @BeforeTest
    fun setUp() {
        mockkObject(AttackTab)
        mockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        energy = 100
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("gg.rsmod.plugins.api.ext.PlayerExtKt")
        unmockkObject(AttackTab)
    }

    private fun player(weapon: Int): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.getEquipment(EquipmentType.WEAPON) } returns Item(weapon)
        every { AttackTab.getEnergy(player) } answers { energy }
        every { AttackTab.setEnergy(player, any()) } answers { energy = secondArg() }
        return player
    }

    @Test
    fun `a failed special keeps its energy and reports FAILED`() {
        val weapon = 990_001
        SpecialAttacks.register(55, weapon) { specialFailed() }
        val outcome = SpecialAttacks.perform(player(weapon), mockk<Pawn>(relaxed = true), mockk<World>(relaxed = true))
        assertEquals(SpecialAttacks.Outcome.FAILED, outcome)
        assertEquals(100, energy)
    }

    @Test
    fun `a performed special spends its energy`() {
        val weapon = 990_002
        SpecialAttacks.register(55, weapon) { }
        val outcome = SpecialAttacks.perform(player(weapon), mockk<Pawn>(relaxed = true), mockk<World>(relaxed = true))
        assertEquals(SpecialAttacks.Outcome.PERFORMED, outcome)
        assertEquals(45, energy)
    }

    @Test
    fun `the combat cycle starts no attack delay after a failed special and the dark bow reports its failure`() {
        val combat = File("src/main/kotlin/gg/rsmod/plugins/content/combat/combat.plugin.kts").readText()
        val failed = combat.substringAfter("SpecialAttacks.Outcome.FAILED ->").substringBefore("SpecialAttacks.Outcome.NOT_USED")
        assertTrue("postAttack" !in failed && "return true" in failed)
        val ranged = File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons/ranged_specials.plugin.kts").readText()
        val darkBow = ranged.substringAfter("You need at least two arrows in your quiver to use this special attack.\")")
        assertTrue(darkBow.trimStart().startsWith("specialFailed()"))
    }
}
