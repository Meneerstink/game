package gg.rsmod.plugins.content.activity.toa.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.content.activity.toa.ToaIds
import io.mockk.*
import kotlin.test.*

class ToaWardensVisualTests {
    @Test fun `scripted floor removes only overlapping source floor decor and restores it exactly once on close`() {
        mockkObject(ToaIds)
        try {
            every { ToaIds.loc(any()) } answers { firstArg<Int>() + 20000 }
            val f = ToaTestFixture()
            val centre = Tile(3300, 3300, 1)
            val saved = (45646..45648).mapIndexed { i, id -> DynamicObject(id + 20000, 22, i, centre.transform(i, 0)) }
            val unrelated = DynamicObject(999, 22, 3, centre.transform(3, 0))
            val decor = (saved + unrelated).associateBy { it.tile }.toMutableMap()
            val spawned = mutableListOf<GameObject>()
            val removed = mutableListOf<GameObject>()
            every { f.world.getObject(any<Tile>(), ObjectType.FLOOR_DECORATION) } answers { decor[firstArg<Tile>()] }
            every { f.world.getObject(any<Tile>(), ObjectType.INTERACTABLE) } returns null
            every { f.world.remove(any<GameObject>()) } answers { firstArg<GameObject>().let { removed += it; decor.remove(it.tile) }; Unit }
            every { f.world.spawn(any<GameObject>()) } answers { spawned += firstArg<GameObject>(); Unit }
            val s = WardensCombat.Session(f.npc, ToaTestContext())
            s.phase = WardensCombat.Phase.CORE
            s.phaseChanged = { s.centre = centre }
            s.startPhaseThree()
            assertEquals(saved.toSet(), removed.toSet())
            assertEquals(unrelated, decor[unrelated.tile])
            assertEquals(336, spawned.count { it.type == 10 })
            assertTrue(spawned.none { it.type == 22 })
            s.close(); s.close()
            val restored = spawned.filter { it.type == 22 }
            assertEquals(3, restored.size)
            assertEquals(saved.map { Triple(it.id, it.settings, it.tile) }.toSet(), restored.map { Triple(it.id, it.settings, it.tile) }.toSet())
        } finally { unmockkObject(ToaIds) }
    }
}
