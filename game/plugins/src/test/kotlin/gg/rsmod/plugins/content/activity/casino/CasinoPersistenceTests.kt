package gg.rsmod.plugins.content.activity.casino

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.service.serializer.PlayerSerializerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Audit E-07: rotating the server seed publishes the old one, so the new seed must be on disk before anything else can
 * happen; a settled round (stake, payout, consumed nonce) is saved at once as well. Both go through `persistNow`, i.e.
 * [PlayerSerializerService.saveClientData].
 */
class CasinoPersistenceTests {
    private fun client(serializer: PlayerSerializerService): Client {
        val coinDef =
            ItemDef(CasinoWallet.CURRENCY).apply {
                name = "Coins"
                stacks = true
            }
        val definitions = mockk<DefinitionSet>(relaxed = true)
        every { definitions.get(ItemDef::class.java, any()) } returns coinDef
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.getService(PlayerSerializerService::class.java, true) } returns serializer

        val client = mockk<Client>(relaxed = true)
        every { client.world } returns world
        every { client.username } returns "tester"
        every { client.attr } returns AttributeMap()
        every { client.inventory } returns ItemContainer(definitions, 28, ContainerStackType.STACK)
        every { client.bank } returns ItemContainer(definitions, 800, ContainerStackType.STACK)
        client.inventory.add(CasinoWallet.CURRENCY, 100_000)
        client.attr[CasinoSeeds.CLIENT_SEED] = "client"
        client.attr[CasinoSeeds.SERVER_SEED] = "server"
        client.attr[CasinoSeeds.NONCE] = "0"
        return client
    }

    @Test
    fun `a settled round is saved at once`() {
        val serializer = mockk<PlayerSerializerService>(relaxed = true)
        val p = client(serializer)
        assertNotNull(DiceGame.roll(p, 1_000, 50))
        verify(exactly = 1) { serializer.saveClientData(p) }
    }

    @Test
    fun `a seed rotation is saved at once`() {
        val serializer = mockk<PlayerSerializerService>(relaxed = true)
        val p = client(serializer)
        assertNotNull(Casino.rotateSeed(p))
        verify(exactly = 1) { serializer.saveClientData(p) }
    }
}
