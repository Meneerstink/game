package gg.rsmod.plugins.content.areas.deathsoffice

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.GRAVESTONE_TILE_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.DEATH_RECOVERY_KEY
import gg.rsmod.game.model.container.key.GRAVESTONE_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * N202 (owner 2026-10-22: a new player died for the first time and "Collect" showed none of his lost items; answer: items come
 * back from the gravestone like OSRS, explained in the death intro). Death holds nothing while the gravestone stands, so "Collect"
 * now tells where the items are and how to take them back (right-click the gravestone, Loot), and the intro names that option.
 */
class DeathCollectGravestoneTests {
    @Test
    fun `Collect points to the gravestone only while the items are in it and Death holds nothing`() {
        val player = player()
        assertFalse(DeathDialogue.itemsOnlyInGravestone(player), "no gravestone: the normal retrieval window")

        player.attr[GRAVESTONE_TILE_ATTR] = Tile(3113, 9841, 0).as30BitInteger
        player.gravestone.add(Items.COINS_995, 1_000_000)
        assertTrue(DeathDialogue.itemsOnlyInGravestone(player))

        player.deathRecovery.add(Items.TELEPORT_TO_HOUSE, 100)
        assertFalse(DeathDialogue.itemsOnlyInGravestone(player), "Death holds items too: open his window")
    }

    @Test
    fun `the hint names Loot, the gravestone and the time left`() {
        val line = DeathDialogue.gravestoneHint("14:27")
        assertTrue("Loot" in line && "gravestone" in line && "14:27" in line, line)
    }

    @Test
    fun `Collect and the first-death explanation both route to the gravestone`() {
        val office = File("src/main/kotlin/gg/rsmod/plugins/content/areas/deathsoffice/deaths_office.plugin.kts").readText()
        val collect = office.substringAfter("option = \"collect\"").substringBefore("OfficeInterface.open(player)")
        assertTrue("DeathDialogue.itemsOnlyInGravestone(player)" in collect)
        val dialogue = File("src/main/kotlin/gg/rsmod/plugins/content/areas/deathsoffice/DeathDialogue.kt").readText()
        assertTrue("choose \${red}Loot\$END to take your items back" in dialogue, "the intro names the Loot option")
    }

    private fun player(): Player {
        val p = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        val office = ItemContainer(DEFINITIONS, DEATH_RECOVERY_KEY)
        val grave = ItemContainer(DEFINITIONS, GRAVESTONE_KEY)
        every { p.attr } returns attrs
        every { p.deathRecovery } returns office
        every { p.gravestone } returns grave
        return p
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
