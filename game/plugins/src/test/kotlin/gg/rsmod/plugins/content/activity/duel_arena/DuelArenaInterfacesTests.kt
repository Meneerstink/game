package gg.rsmod.plugins.content.activity.duel_arena

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** RCV-010 C2-a: native 667 Duel Arena screen data and stake handling (Novite DuelArena/DuelRules). */
class DuelArenaInterfacesTests {
    private fun player(name: String): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns name
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.tile } returns Tile(3367, 3275, 0)
        return player
    }

    @Test
    fun `every rule and lock has a unique component on both rule screens`() {
        listOf(DuelArenaInterfaces.STAKE_RULES, DuelArenaInterfaces.FRIENDLY_RULES).forEach { screen ->
            val components = DuelRule.values().map { if (screen == DuelArenaInterfaces.STAKE_RULES) it.id631 else it.id637 } +
                DuelEquipLock.values().map { if (screen == DuelArenaInterfaces.STAKE_RULES) it.id631 else it.id637 }
            assertEquals(components.size, components.toSet().size, "component collision on $screen")
            DuelRule.values().forEach { rule ->
                assertEquals(rule, DuelArenaInterfaces.ruleForComponent(screen, if (screen == DuelArenaInterfaces.STAKE_RULES) rule.id631 else rule.id637))
            }
            DuelEquipLock.values().forEach { lock ->
                assertEquals(lock, DuelArenaInterfaces.lockForComponent(screen, if (screen == DuelArenaInterfaces.STAKE_RULES) lock.id631 else lock.id637))
            }
            val buttons = setOf(DuelArenaInterfaces.STAKE_ACCEPT, DuelArenaInterfaces.STAKE_DECLINE, DuelArenaInterfaces.STAKE_REMOVE_COMPONENT,
                DuelArenaInterfaces.FRIENDLY_ACCEPT, DuelArenaInterfaces.FRIENDLY_DECLINE)
            if (screen == DuelArenaInterfaces.STAKE_RULES) {
                assertTrue(components.none { it in setOf(DuelArenaInterfaces.STAKE_ACCEPT, DuelArenaInterfaces.STAKE_DECLINE, DuelArenaInterfaces.STAKE_REMOVE_COMPONENT) })
            } else {
                assertTrue(components.none { it in setOf(DuelArenaInterfaces.FRIENDLY_ACCEPT, DuelArenaInterfaces.FRIENDLY_DECLINE) }, "buttons $buttons")
            }
        }
        // Novite rule indices: every rule and lock maps to a distinct varp 286 bit.
        val indices = DuelRule.values().map { DuelArenaInterfaces.noviteRuleIndex(it) } + DuelEquipLock.values().map { DuelArenaInterfaces.noviteRuleIndex(it) }
        assertEquals(indices.size, indices.toSet().size)
        assertTrue(indices.all { it in 0..25 })
    }

    @Test
    fun `rules varp follows Novite setConfigs`() {
        val match = DuelArenaMatch(player("a"), player("b"))
        match.rules.clear()
        assertEquals(0, DuelArenaInterfaces.rulesVarp(match))
        match.rules.add(DuelRule.NO_RANGED)
        assertEquals(16, DuelArenaInterfaces.rulesVarp(match))
        match.rules.add(DuelRule.NO_FORFEIT)
        assertEquals(16 + (16 shl 7) + 5, DuelArenaInterfaces.rulesVarp(match))
        match.rules.add(DuelRule.NO_MOVEMENT)
        match.lockedSlots.add(DuelEquipLock.WEAPON)
        assertEquals(16 + (16 shl 7) + 5 + (16 shl 25) + 6 + (16 shl 13), DuelArenaInterfaces.rulesVarp(match))
    }

    @Test
    fun `lobby area, spoils options hash and confirmation texts`() {
        assertTrue(DuelArenaInterfaces.inLobby(Tile(3367, 3275, 0)))
        assertFalse(DuelArenaInterfaces.inLobby(Tile(3346, 3251, 0)), "the arena rooms are not the lobby")
        assertEquals((2 shl 0) or (2 shl 1) or (2 shl 2) or (2 shl 3) or (2 shl 4) or (2 shl 5), DuelArenaInterfaces.OPTIONS_0_TO_5)
        assertEquals("Absolutely nothing!", DuelArenaInterfaces.confirmationStakeText(false))
        assertEquals("", DuelArenaInterfaces.confirmationStakeText(true))
    }

    @Test
    fun `staking moves real items, refuses untradeables, friendly duels and confirmed screens`() {
        val a = player("a")
        val b = player("b")
        val match = DuelArenaMatch(a, b)
        a.inventory.add(Items.COINS_995, 1000)
        assertTrue(DuelArenaInterfaces.addStake(match, a, 0, 400))
        assertEquals(600, a.inventory.getItemCount(Items.COINS_995))
        assertEquals(400, match.stakeOf(a).getItemCount(Items.COINS_995))
        assertTrue(DuelArenaInterfaces.removeStake(match, a, 0, 100))
        assertEquals(700, a.inventory.getItemCount(Items.COINS_995))
        assertEquals(300, match.stakeOf(a).getItemCount(Items.COINS_995))

        val untradeable = DEFINITIONS.getAll<ItemDef>(ItemDef::class.java).values.filterIsInstance<ItemDef>().first { !it.tradeable && !it.noted && it.name.isNotBlank() }
        a.inventory[27] = Item(untradeable.id)
        assertFalse(DuelArenaInterfaces.addStake(match, a, 27, 1))
        assertEquals(untradeable.id, a.inventory[27]!!.id)

        match.confirming = true
        assertFalse(DuelArenaInterfaces.addStake(match, a, 0, 1), "no staking once the confirmation screen is up")
        assertFalse(DuelArenaInterfaces.removeStake(match, a, 0, 1))
        match.confirming = false
        match.friendly = true
        assertFalse(DuelArenaInterfaces.addStake(match, a, 0, 1), "friendly duels carry no stake")
        match.friendly = false
        match.stage = DuelStage.FIGHTING
        assertFalse(DuelArenaInterfaces.addStake(match, a, 0, 1), "no staking during the fight")
        assertEquals(1000, a.inventory.getItemCount(Items.COINS_995) + match.stakeOf(a).getItemCount(Items.COINS_995), "no coins created or lost")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
            applyRealTradeableFlags()
        }

        /**
         * [ItemDef.tradeable] is filled at world boot by `ItemMetadataService` from `data/cfg/items.yml`, not by the
         * cache decode, so a bare [DefinitionSet] reports every item as untradeable. Apply the real flag for every
         * entry of that file, exactly as the service does, so staking is tested against the production data.
         */
        private fun applyRealTradeableFlags() {
            var id = -1
            java.io.File(Paths.get("..", "..", "data", "cfg", "items.yml").toFile().path).forEachLine { line ->
                when {
                    line.startsWith("- id: ") -> id = line.removePrefix("- id: ").trim().toInt()
                    line.startsWith("  tradeable: ") && id >= 0 ->
                        DEFINITIONS.getNullable(ItemDef::class.java, id)?.tradeable = line.removePrefix("  tradeable: ").trim() == "true"
                }
            }
        }
    }
}
