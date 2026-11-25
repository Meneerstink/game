package gg.rsmod.plugins.content.combat.specialattack

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Combat run 2026-09-29: area attacks (Burst/Barrage, chinchompas, area specials) share [SpecialAttackSupport.canSplash], and the
 * can_attack hooks (Castle Wars / Soul Wars teams, Duel Arena, breach protection) now gate player victims as well as npcs.
 */
class SplashTargetTests {
    @Test
    fun `shield bash uses the primary zone and self based powerstab keeps its caster gate`() {
        val root = java.io.File("src/main/kotlin/gg/rsmod/plugins/content/combat/specialattack/weapons")
        val shield = java.io.File(root, "cox_weapon_specials.plugin.kts").readText().substringAfter("CoxLootItems.local(21015)")
        assertTrue("if (!primary.tile.isMulti(world)) return@register" in shield)
        assertFalse("if (!player.tile.isMulti(world))" in shield)
        val powerstab = java.io.File(root, "melee_specials.plugin.kts").readText()
            .substringAfter("SpecialAttacks.register(60, Items.DRAGON_2H_SWORD)").substringBefore("SpecialAttacks.register(")
        assertTrue("requireCasterMulti = true" in powerstab)
    }

    private val tile = Tile(3200, 3200)

    @Test
    fun `a player victim refused by a can_attack hook is not splashed`() {
        val world = world(canAttack = false)
        assertFalse(SpecialAttackSupport.canSplash(caster(world), victim(world)))
    }

    @Test
    fun `a player victim every hook allows is splashed`() {
        val world = world(canAttack = true)
        assertTrue(SpecialAttackSupport.canSplash(caster(world), victim(world)))
    }

    @Test
    fun `dead, invisible or offline victims are never splashed`() {
        val world = world(canAttack = true)
        val dead = victim(world).also { every { it.isDead() } returns true }
        val invisible = victim(world).also { every { it.invisible } returns true }
        val offline = victim(world).also { every { it.isOnline } returns false }
        listOf(dead, invisible, offline).forEach { assertFalse(SpecialAttackSupport.canSplash(caster(world), it)) }
    }

    private fun world(canAttack: Boolean): World {
        val world = mockk<World>(relaxed = true)
        every { world.getMultiCombatRegions() } returns mutableSetOf(tile.regionId)
        every { world.plugins.canAttack(any(), any(), any()) } returns canAttack
        return world
    }

    private fun caster(world: World): Npc {
        val npc = mockk<Npc>(relaxed = true)
        every { npc.world } returns world
        return npc
    }

    private fun victim(world: World): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.world } returns world
        every { p.tile } returns tile
        every { p.isDead() } returns false
        every { p.invisible } returns false
        every { p.isOnline } returns true
        every { p.lock } returns LockState.NONE
        return p
    }
}
