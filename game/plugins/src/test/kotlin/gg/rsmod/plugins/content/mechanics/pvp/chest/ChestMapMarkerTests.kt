package gg.rsmod.plugins.content.mechanics.pvp.chest

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.hud.ClientHud
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N273 (owner 2026-10-24: the chest is in the Deadman tab, but the world map shows no blue arrow where it is): every player's
 * client gets the lootable timed chest's tile while it stands and an empty marker once it is looted or gone; only changes are sent,
 * and a login sends it again.
 */
class ChestMapMarkerTests {
    private val sent = ArrayList<String>()

    private fun player(): Player {
        val attr = AttributeMap()
        val p = mockk<Player>(relaxed = true)
        every { p.attr } returns attr
        every { p.username } returns "chestmarker_test"
        return p
    }

    private fun chest(tile: Tile) = DeadmanChestWorld.Chest(tile, true, "east of the Rimmington mine", DeadmanChest.State(tile, true, 1000), mockk<DynamicObject>(relaxed = true))

    @AfterTest
    fun clean() {
        DeadmanChestWorld.chests.clear()
        ClientHud.observer = null
    }

    @Test
    fun `the arrow follows the chest - shown while it stands, gone once it is looted, only changes are sent, again after a login`() {
        ClientHud.observer = { _, payload -> sent += payload }
        DeadmanChestWorld.chests.clear()
        val p = player()
        ChestMapMarker.sync(p)
        ChestMapMarker.sync(p)
        assertEquals(listOf("chest|"), sent, "no chest: one empty marker, not one per cycle")
        val tile = Tile(2988, 3242, 0)
        val c = chest(tile)
        DeadmanChestWorld.chests += c
        ChestMapMarker.sync(p)
        assertEquals("chest|2988|3242|0", sent.last())
        ChestMapMarker.reset(p)
        ChestMapMarker.sync(p)
        assertEquals(listOf("chest|", "chest|2988|3242|0", "chest|2988|3242|0"), sent, "a login sends it again")
        c.state.tryLoot("someone")
        ChestMapMarker.sync(p)
        assertEquals("chest|", sent.last(), "looted: the arrow goes")
    }

    @Test
    fun `the end-of-cycle hook drives it and a login resets it`() {
        val hook = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/hud/client_hud.plugin.kts").readText()
        assertTrue("ChestMapMarker.sync(player)" in hook && "ChestMapMarker.reset(player)" in hook)
    }
}
