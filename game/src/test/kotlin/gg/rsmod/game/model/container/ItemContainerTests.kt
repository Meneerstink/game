package gg.rsmod.game.model.container

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.*

/**
 * @author Tom <rspsmods@gmail.com>
 */
class ItemContainerTests {
    @Test
    fun createContainer() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)

        assertEquals(container.capacity, CAPACITY)
        assertEquals(container.nextFreeSlot, 0)
        assertEquals(container.freeSlotCount, CAPACITY)
        assertEquals(container.occupiedSlotCount, 0)
    }

    /*
     * A negative amount reached add()/remove() through overflowed or client-controlled amounts.
     * add() then inserted one item (`++completed >= amount` was immediately true) and remove()
     * *grew* a stack (`min(cur, negative)` subtracted a negative). Both must be no-ops.
     */
    @Test
    fun negativeOrZeroAmountsAreNoOps() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        assertTrue(container.add(item = 995, amount = 1000).hasSucceeded())
        assertTrue(container.add(item = 4151, amount = 1).hasSucceeded())

        assertEquals(0, container.remove(item = 995, amount = -500, assureFullRemoval = true).completed)
        assertEquals(0, container.remove(item = 995, amount = -500).completed)
        assertEquals(0, container.remove(item = 4151, amount = 0).completed)
        assertEquals(1000, container.getItemCount(995))
        assertEquals(1, container.getItemCount(4151))

        assertEquals(0, container.add(item = 4151, amount = -3).completed)
        assertEquals(0, container.add(item = 995, amount = -3).completed)
        assertEquals(0, container.add(item = 4151, amount = 0).completed)
        assertEquals(1000, container.getItemCount(995))
        assertEquals(1, container.getItemCount(4151))
        assertEquals(2, container.occupiedSlotCount)
    }

    @Test
    fun addNoted() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val result =
            container.add(
                item = 4152,
                amount = 3,
                forceNoStack = false,
                assureFullInsertion = true,
                beginSlot = 0,
            )

        assertTrue(result.hasSucceeded())
        assertEquals(container.occupiedSlotCount, 1)
        assertEquals(container.nextFreeSlot, 1)
        assertTrue(container[0] != null)
        assertEquals(container[0]!!.amount, 3)
    }

    @Test
    fun addUnnoted() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val result =
            container.add(
                item = 4151,
                amount = 3,
                forceNoStack = false,
                assureFullInsertion = true,
                beginSlot = 0,
            )

        assertTrue(result.hasSucceeded())
        assertEquals(container.occupiedSlotCount, 3)
        assertEquals(container.nextFreeSlot, 3)
        assertNotNull(container[0])
        assertEquals(container[0]!!.id, 4151)
        assertEquals(container[0]!!.amount, 1)
        assertNotNull(container[1])
        assertEquals(container[1]!!.id, 4151)
        assertEquals(container[1]!!.amount, 1)
        assertNotNull(container[2])
        assertEquals(container[2]!!.id, 4151)
        assertEquals(container[2]!!.amount, 1)
        assertNull(container[3])
    }

    @Test
    fun addNotedNoStack() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val result =
            container.add(
                item = 4152,
                amount = 3,
                forceNoStack = true,
                assureFullInsertion = true,
                beginSlot = 0,
            )

        assertTrue(result.hasSucceeded())
        assertEquals(container.occupiedSlotCount, 3)
        assertEquals(container.nextFreeSlot, 3)
        assertNotNull(container[0])
        assertEquals(container[0]!!.id, 4152)
        assertEquals(container[0]!!.amount, 1)
        assertNotNull(container[1])
        assertEquals(container[1]!!.id, 4152)
        assertEquals(container[1]!!.amount, 1)
        assertNotNull(container[2])
        assertEquals(container[2]!!.id, 4152)
        assertEquals(container[2]!!.amount, 1)
        assertNull(container[3])
    }

    @Test
    fun addOneTooManyStrict() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val result =
            container.add(
                item = 4151,
                amount = CAPACITY + 1,
                forceNoStack = false,
                assureFullInsertion = true,
                beginSlot = 0,
            )
        assertFalse(result.hasSucceeded())
        assertEquals(container.nextFreeSlot, 0)
    }

    @Test
    fun addOneTooManyLoose() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val loose =
            container.add(
                item = 4151,
                amount = CAPACITY + 1,
                forceNoStack = false,
                assureFullInsertion = false,
                beginSlot = 0,
            )
        assertFalse(loose.hasSucceeded())
        assertEquals(container.freeSlotCount, 0)
    }

    @Test
    fun addOverflowAmount() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)

        container.add(
            item = 4152,
            amount = Int.MAX_VALUE,
            forceNoStack = false,
            assureFullInsertion = true,
            beginSlot = 0,
        )
        assertEquals(container.getItemCount(4152), Int.MAX_VALUE)

        val result =
            container.add(
                item = 4152,
                amount = 1,
                forceNoStack = false,
                assureFullInsertion = true,
                beginSlot = 0,
            )
        assertFalse(result.hasSucceeded())
        assertEquals(result.getLeftOver(), 1)
        assertEquals(container.getItemCount(4152), Int.MAX_VALUE)
    }

    @Test
    fun addToMiddle() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.NORMAL)
        val result =
            container.add(
                item = 4151,
                amount = 1,
                forceNoStack = false,
                assureFullInsertion = false,
                beginSlot =
                    CAPACITY / 2,
            )
        assertTrue(result.hasSucceeded())
        assertEquals(container.freeSlotCount, CAPACITY - 1)
        assertEquals(container.nextFreeSlot, 0)
        assertNotNull(container[CAPACITY / 2])
        assertEquals(container[CAPACITY / 2]!!.id, 4151)
    }

    @Test
    fun addToStackContainer() {
        val container = ItemContainer(definitions, CAPACITY, ContainerStackType.STACK)
        val result =
            container.add(
                item = 4151,
                amount = Int.MAX_VALUE,
                forceNoStack = false,
                assureFullInsertion = false,
                beginSlot = 0,
            )

        assertTrue(result.hasSucceeded())
        assertNotNull(container[0])
        assertEquals(container[0]!!.amount, Int.MAX_VALUE)
    }

    companion object {
        private const val CAPACITY = 28

        private val definitions = DefinitionSet()

        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "data", "cache").toFile().toString())

            definitions.loadAll(store)

            assertNotEquals(definitions.getCount(ItemDef::class.java), 0)
        }
    }
}
