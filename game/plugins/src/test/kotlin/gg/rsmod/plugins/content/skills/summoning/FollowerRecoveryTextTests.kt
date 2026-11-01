package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.content.mechanics.death.DeathsOffice
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FollowerRecoveryTextTests {
    @Test
    fun `every pet pack recovers into the office and names the pet and the same destination`() {
        for (key in BeastOfBurden.PET_KEYS.values) {
            val (player, sent) = player()
            val pack = ItemContainer(player.world.definitions, key)
            player.containers[key] = pack
            repeat(key.capacity) { pack[it] = Item(1511) }
            BeastOfBurden.rescueStrandedPetCargo(player)
            assertEquals(key.capacity, player.deathRecovery.getItemCount(1511))
            assertTrue(pack.isEmpty)
            assertEquals(listOf("<col=ff0000> Your pet's items have been moved to Death's Office."), sent)
        }
    }

    @Test
    fun `familiar release and safe death recovery use the office name and preserve cargo`() {
        for (discard in listOf(false, true)) {
            val (player, sent) = player()
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns SummoningPouchData.PACK_YAK.npc
            every { player.world.npcs.contains(npc) } returns true
            player.attr[FAMILIAR_ATTR] = WeakReference(npc)
            val pack = ItemContainer(player.world.definitions, BeastOfBurden.PACK_YAK_KEY)
            player.containers[BeastOfBurden.PACK_YAK_KEY] = pack
            repeat(pack.capacity) { pack[it] = Item(1511) }
            if (discard) BeastOfBurden.discard(player, free = true) else BeastOfBurden.release(player)
            assertEquals(30, player.deathRecovery.getItemCount(1511))
            assertTrue(pack.isEmpty)
            assertEquals(listOf("<col=ff0000> Your familiar's items have been moved to Death's Office."), sent)
        }
    }

    @Test
    fun `partial recovery names the office in both messages and keeps the remainder`() {
        val (player, sent) = player(officeCapacity = 1)
        val key = BeastOfBurden.PET_KEYS.values.first()
        val pack = ItemContainer(player.world.definitions, key)
        player.containers[key] = pack
        pack[0] = Item(1511)
        pack[1] = Item(1511)
        BeastOfBurden.rescueStrandedPetCargo(player)
        assertEquals(1, player.deathRecovery.getItemCount(1511))
        assertEquals(1, pack.getItemCount(1511))
        assertEquals(listOf(
            "<col=ff0000> Your pet's items have been moved to Death's Office.",
            "<col=ff0000> Death's Office is full - 1 of your pet's items could not be stored.",
        ), sent)
    }

    @Test
    fun `pickup prompt login notice and all recovery routes use office not the entrance name`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        val pets = File(root, "skills/summoning/Pets.kt").readText()
        // N503 D17 (owner 2026-11-01): Pick-up keeps the items in the pet - no Death's Office prompt any more.
        assertTrue("Your pet keeps the items it is carrying." in pets)
        assertFalse("its items go to Death's Office." in pets.substringAfter("private fun keepCargo"))
        val login = File(root, "skills/summoning/familiar.plugin.kts").readText()
        assertTrue("You have items stored at Death's Office." in login)
        assertTrue("Death's Office" in DeathsOffice.loginNotices(null, true).single())
        for (file in File(root, "skills/summoning").walkTopDown().filter { it.isFile }) {
            assertFalse("Death's Domain" in file.readText(), "${file.name}: obsolete recovery destination")
        }
        // PvM death has its own gravestone route; once collapsed it uses the same Office storage.
        assertTrue("Death's Office" in File(root, "mechanics/death/death.plugin.kts").readText())
    }

    private fun player(officeCapacity: Int = 64): Pair<Player, MutableList<String>> {
        val definitions = mockk<DefinitionSet>()
        every { definitions.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world.definitions } returns definitions
        every { player.containers } returns HashMap()
        every { player.deathRecovery } returns ItemContainer(definitions,
            ContainerKey("test_office", officeCapacity, ContainerStackType.NORMAL))
        val sent = mutableListOf<String>()
        every { player.write(*anyVararg<Message>()) } answers {
            sent += firstArg<Array<Message>>().filterIsInstance<MessageGameMessage>().map { it.message }
            Unit
        }
        return player to sent
    }
}
