package gg.rsmod.plugins.content.drops

import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A drop is rolled against the table size the script declared with `total(n)`, so slots the script
 * never filled are its chance of nothing.
 *
 * Before this, the declared total was only validated ("Drop table has N total slots, but M were
 * used") and then thrown away: the roll used the *occupied* slot count, so every under-filled table
 * handed out its drops far more often than written - a table declaring 1024 slots but filling 280
 * rolled one-in-280, roughly 3.7x too common, and never rolled nothing at all.
 *
 * Each test stubs `nextInt` for one exact denominator, so a regression that rolls against the
 * occupied count fails with mockk's "no answer found" rather than quietly passing.
 */
class DropTableTotalSlotsTests {
    private val factory = DropTableFactory
    private val randomMock: SecureRandom = mockk()
    private val player: Player = mockk()

    init {
        factory.prng = randomMock
    }

    @Test
    fun `an under-filled table rolls against its declared total and drops nothing in the remainder`() {
        val npcId = 910001
        every { randomMock.nextInt(1024) } returnsMany listOf(0, 279, 280, 1023)

        factory.register(
            factory.build {
                main {
                    total(1024)
                    obj(Items.COAL, slots = 280)
                }
            },
            npcId,
        )

        val drops = (0 until 4).map { factory.getDrop(player, npcId) }

        assertEquals(Items.COAL, drops[0]!!.single().id)
        assertEquals(Items.COAL, drops[1]!!.single().id)
        assertEquals(0, drops[2]!!.size, "slot 280 is past the filled slots, so it must drop nothing")
        assertEquals(0, drops[3]!!.size, "the whole declared remainder is a chance of nothing")
    }

    @Test
    fun `a table that over-fills its declared total keeps every entry reachable`() {
        val npcId = 910002
        // 146 slots used against a declared 128: the declared total cannot be the denominator
        // without making the last entries unrollable, so the occupied count is used instead.
        every { randomMock.nextInt(146) } returnsMany listOf(0, 127, 145)

        factory.register(
            factory.build {
                main {
                    total(128)
                    obj(Items.COAL, slots = 100)
                    obj(Items.IRON_ORE, slots = 28)
                    obj(Items.GOLD_ORE, slots = 18)
                }
            },
            npcId,
        )

        val drops = (0 until 3).map { factory.getDrop(player, npcId) }

        assertEquals(Items.COAL, drops[0]!!.single().id)
        assertEquals(Items.IRON_ORE, drops[1]!!.single().id)
        assertEquals(Items.GOLD_ORE, drops[2]!!.single().id, "an entry past the declared total must still be reachable")
    }

    @Test
    fun `a table with no declared total still rolls against what it holds`() {
        val npcId = 910003
        every { randomMock.nextInt(10) } returnsMany listOf(0, 9)

        factory.register(
            factory.build {
                main {
                    obj(Items.COAL, slots = 10)
                }
            },
            npcId,
        )

        val drops = (0 until 2).map { factory.getDrop(player, npcId) }

        assertEquals(Items.COAL, drops[0]!!.single().id)
        assertEquals(Items.COAL, drops[1]!!.single().id)
    }
}
