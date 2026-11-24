package gg.rsmod.plugins.content.combat.strategy

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.region.Chunk
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttackSupport
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** M0 interaction stability: effect-only magic callbacks must not mutate stale actors after impact delay. */
class MagicDelayedEffectTests {
    @Test
    fun `burst barrage and target based splashes cross from single to multi with victim gates intact`() {
        val world = mockk<World>(relaxed = true)
        val caster = mockk<Player>(relaxed = true)
        every { caster.world } returns world
        every { caster.attr } returns AttributeMap()
        every { world.instanceAllocator.getMap(any()) } returns null
        every { caster.tile } returns Tile(2816, 5570)
        every { world.getMultiCombatChunks() } returns emptySet()
        every { world.getMultiCombatRegions() } returns emptySet()
        val chunk = mockk<Chunk>(relaxed = true)
        every { world.chunks.get(any<Tile>(), any()) } returns chunk
        fun victim(x: Int, z: Int): Npc {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.tile } returns Tile(x, z)
            every { npc.world } returns world
            every { npc.attr } returns AttributeMap()
            every { npc.owner } returns null
            every { npc.isSpawned() } returns true
            every { npc.getCurrentLifepoints() } returns 100
            every { npc.def } returns NpcDef(1).also { it.options = arrayOf("Attack", null, null, null, null) }
            every { npc.combatDef } returns NpcCombatDef.DEFAULT.copy(lifepoints = 100)
            return npc
        }
        val primary = victim(2816, 5571)
        val secondary = victim(2817, 5571)
        val single = victim(2817, 5570)
        val owned = victim(2815, 5571)
        val owner = mockk<Player>(relaxed = true)
        every { owned.owner } returns owner
        owned.attr[Combat.OWNER_ONLY_TARGET_ATTR] = true
        every { world.plugins.canAttack(caster, any(), false) } returns true
        every { chunk.getEntities<Npc>(any<Tile>(), EntityType.NPC) } returns listOf(primary, secondary, single, owned)
        val support = SpecialAttackSupport
        assertEquals(listOf(secondary), support.adjacentTargets(caster, primary))
        assertTrue(support.adjacentTargets(caster, primary, requireCasterMulti = true).isEmpty())
        for (spell in listOf(CombatSpell.ICE_BURST,
            CombatSpell.ICE_BARRAGE)) {
            assertEquals(listOf(primary, secondary), MagicCombatStrategy.collectTargets(caster, primary, spell))
            assertEquals(listOf(single), MagicCombatStrategy.collectTargets(caster, single, spell))
        }
        every { world.plugins.canAttack(caster, secondary, false) } returns false
        assertTrue(support.adjacentTargets(caster, primary).isEmpty(), "team and encounter hooks still gate splashes")
        every { world.plugins.canAttack(caster, secondary, false) } returns true
        every { secondary.combatDef } returns NpcCombatDef.DEFAULT.copy(lifepoints = 100, slayerReq = 99)
        assertTrue(support.adjacentTargets(caster, primary).isEmpty(), "Slayer level still gates secondary victims")
        every { owned.owner } returns caster
        assertTrue(support.canSplash(caster, owned), "the owner may still hit their own owner-only target")
    }

    @Test
    fun `effect-only callback rejects offline target and caster`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        val effectQueue = source.substringAfter("if (!spell.damaging)").substringBefore("val maxHit")

        assertTrue("target is Player && !target.isOnline" in effectQueue)
        assertTrue("pawn is Player && !pawn.isOnline" in effectQueue)
        assertTrue(effectQueue.indexOf("isOnline") < effectQueue.indexOf("applyEffect"))
    }
}

