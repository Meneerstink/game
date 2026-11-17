package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.areas.poh.PlayerHouse
import io.mockk.*
import kotlin.test.*

class NigelHouseRewardsTests {
    private val f = DeadmanPlayerFixture()
    private val rug = NigelHouseRewards.Slot("chapel", NigelHouseRewards.Kind.RUG)
    private val portal1 = NigelHouseRewards.Slot("garden_1", NigelHouseRewards.Kind.EXIT_PORTAL)
    private val portal2 = NigelHouseRewards.Slot("garden_2", NigelHouseRewards.Kind.EXIT_PORTAL)
    private val store = NigelRewardStore(NigelRewardStore.Contract(mapOf(NigelRewardStore.Reward.RUG to 800), emptyList(), 850))
    private var cycle = 10
    private var hotspotValid = true
    private val service = NigelHouseRewards(store, setOf(rug, portal1, portal2), { _, _ -> hotspotValid }, { 0 })
    init {
        mockkObject(PlayerHouse)
        every { PlayerHouse.ownsHouseAt(f.player) } returns true
        every { PlayerHouse.isBuildMode(f.player) } returns true
        every { f.player.tile } returns Tile(3200, 3200)
        every { f.world.currentCycle } answers { cycle }
        every { f.player.skills.getCurrentLevel(Skills.CONSTRUCTION) } returns 28
        listOf(800, 850, 900, Items.SAW, Items.HAMMER, Items.BOLT_OF_CLOTH, Items.IRON_BAR).forEach { f.define(it) }
        f.inventory[0] = Item(Items.SAW); f.inventory[1] = Item(Items.HAMMER)
    }
    @AfterTest fun cleanup() = unmockkObject(PlayerHouse)

    private fun materials(rugMaterial: Boolean = true) {
        f.inventory[2] = Item(if (rugMaterial) 800 else 850)
        repeat(if (rugMaterial) 4 else 10) { f.inventory[3 + it] = Item(if (rugMaterial) Items.BOLT_OF_CLOTH else Items.IRON_BAR) }
    }
    @Test fun `rug consumes once after five ticks awards XP once and furniture removal returns only rug`() {
        materials()
        assertEquals(NigelHouseRewards.Result.OK, service.beginRug(f.player, rug))
        cycle = 14
        assertEquals(NigelHouseRewards.Result.TOO_EARLY, service.finishRug(f.player))
        assertEquals(4L, f.count(Items.BOLT_OF_CLOTH))
        cycle = 15
        assertEquals(NigelHouseRewards.Result.OK, service.finishRug(f.player))
        assertTrue(service.installed(f.player, rug))
        assertEquals(0L, f.count(Items.BOLT_OF_CLOTH)); assertEquals(0L, f.count(800))
        assertEquals(NigelHouseRewards.Result.NOT_STARTED, service.finishRug(f.player))
        verify(exactly = 1) { f.player.addXp(Skills.CONSTRUCTION, 283.0, true, false) }
        assertEquals(NigelHouseRewards.Result.OK, service.remove(f.player, rug))
        assertEquals(1L, f.count(800)); assertEquals(0L, f.count(Items.BOLT_OF_CLOTH))
        assertEquals(NigelHouseRewards.Result.EMPTY, service.remove(f.player, rug))
    }
    @Test fun `interruption lost materials moved hotspot and ownership are revalidated without consumption`() {
        materials()
        assertEquals(NigelHouseRewards.Result.OK, service.beginRug(f.player, rug))
        service.cancel(f.player)
        cycle = 15
        assertEquals(NigelHouseRewards.Result.NOT_STARTED, service.finishRug(f.player))
        assertEquals(NigelHouseRewards.Result.OK, service.beginRug(f.player, rug))
        cycle = 20
        hotspotValid = false
        assertEquals(NigelHouseRewards.Result.INVALID_HOTSPOT, service.finishRug(f.player))
        assertEquals(4L, f.count(Items.BOLT_OF_CLOTH)); assertEquals(1L, f.count(800))
        hotspotValid = true
        every { PlayerHouse.ownsHouseAt(f.player) } returns false
        assertEquals(NigelHouseRewards.Result.NOT_OWNER, service.beginRug(f.player, rug))
    }
    @Test fun `full inventory cannot lose rug on furniture removal but deleting room returns nothing`() {
        materials(); service.beginRug(f.player, rug); cycle = 15; service.finishRug(f.player)
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        assertEquals(NigelHouseRewards.Result.NO_SPACE, service.remove(f.player, rug))
        assertTrue(service.installed(f.player, rug))
        assertEquals(NigelHouseRewards.Result.OK, service.remove(f.player, rug, deletingRoom = true))
        assertFalse(service.installed(f.player, rug)); assertEquals(0L, f.count(800))
    }
    @Test fun `portal needs ten iron bars and blueprint and last exit cannot be removed`() {
        materials(false)
        assertEquals(NigelHouseRewards.Result.OK, service.buildExitPortal(f.player, portal1))
        assertEquals(0L, f.count(850)); assertEquals(0L, f.count(Items.IRON_BAR))
        assertEquals(NigelHouseRewards.Result.LAST_PORTAL, service.remove(f.player, portal1))
        materials(false)
        assertEquals(NigelHouseRewards.Result.OK, service.buildExitPortal(f.player, portal2))
        assertEquals(NigelHouseRewards.Result.OK, service.remove(f.player, portal1))
        assertEquals(NigelHouseRewards.Result.LAST_PORTAL, service.remove(f.player, portal2, deletingRoom = true))
        assertEquals(0L, f.count(850))
    }
}
