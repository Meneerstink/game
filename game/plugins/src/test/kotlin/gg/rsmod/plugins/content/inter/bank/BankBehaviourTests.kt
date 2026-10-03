package gg.rsmod.plugins.content.inter.bank

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.ContainerKey
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import io.mockk.every
import io.mockk.mockk
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * E7 (owner 2026-10-03, "onze bank moet precies functioneren als osrs bank"): the bank behaviours fixed in this pass, on
 * real 667 item and varbit definitions.
 */
class BankBehaviourTests {
    private val sent = mutableListOf<Message>()

    private fun messages() = sent.filterIsInstance<MessageGameMessage>().map { it.message }

    private fun newPlayer(bank: ItemContainer = ItemContainer(DEFINITIONS, BANK_KEY)): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        every { player.world } returns world
        every { player.bank } returns bank
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.varps } returns VarpSet((0 until 4000).toSet())
        every { player.varcs } returns MutableList(4000) { 0 }
        every { player.write(*anyVararg<Message>()) } answers { sent.addAll(firstArg<Array<Message>>()) }
        // BankSecurity reads the tile and the last-combat attribute for valuable deposits: out of combat, in Varrock square.
        every { player.tile } returns Tile(3212, 3428)
        every { player.attr } returns AttributeMap()
        return player
    }

    private fun Player.tabSizes() = (1..BankTabs.MAX_BANK_TABS).map { BankTabs.getTabSize(this, it) }

    private fun Player.bankIds() = (0 until bank.capacity).mapNotNull { bank[it]?.id }

    @Test
    fun `depositing a noted item while a tab is viewed joins the existing stack in its own tab`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 2)
        player.bank[1] = Item(Items.COINS_995, 50)
        BankTabs.setTabSize(player, 1, 1)
        player.setVarbit(BankTabs.SELECTED_TAB_VARBIT, 2) // viewing tab 1
        val noted = Item(Items.ABYSSAL_WHIP).toNoted(DEFINITIONS)
        assertNotEquals(Items.ABYSSAL_WHIP, noted.id)
        player.inventory[0] = Item(noted.id, 3)

        assertTrue(Bank.deposit(player, player.inventory, 0, 3))

        assertEquals(5, player.bank.getItemCount(Items.ABYSSAL_WHIP))
        assertEquals(listOf(Items.ABYSSAL_WHIP, Items.COINS_995), player.bankIds(), "no second stack, nothing moved")
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertNull(player.inventory[0])
    }

    @Test
    fun `a new item deposited while a tab is viewed goes into that tab`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.COINS_995, 50)
        BankTabs.setTabSize(player, 1, 1)
        player.setVarbit(BankTabs.SELECTED_TAB_VARBIT, 2)
        player.inventory[0] = Item(Items.DRAGON_DAGGER)

        assertTrue(Bank.deposit(player, player.inventory, 0, 1))

        assertEquals(listOf(Items.ABYSSAL_WHIP, Items.DRAGON_DAGGER, Items.COINS_995), player.bankIds())
        assertEquals(2, BankTabs.getTabSize(player, 1))
    }

    @Test
    fun `a full stack in the bank refuses more with the OSRS message`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.COINS_995, Int.MAX_VALUE)
        player.inventory[0] = Item(Items.COINS_995, 100)

        assertFalse(Bank.deposit(player, player.inventory, 0, 100))

        assertEquals(100, player.inventory.getItemCount(Items.COINS_995))
        assertEquals(Int.MAX_VALUE, player.bank.getItemCount(Items.COINS_995))
        assertEquals(1, player.bankIds().size, "no second coin stack")
        assertTrue(Bank.FULL_STACK_MESSAGE in messages())
    }

    @Test
    fun `deposit inventory stops at a full bank and says so once`() {
        val bank = ItemContainer(DEFINITIONS, ContainerKey("bank", capacity = 2, stackType = ContainerStackType.STACK))
        val player = newPlayer(bank)
        player.inventory[0] = Item(Items.ABYSSAL_WHIP)
        player.inventory[1] = Item(Items.DRAGON_DAGGER)
        player.inventory[2] = Item(Items.DRAGON_SCIMITAR)
        player.inventory[3] = Item(Items.DRAGON_LONGSWORD)

        Bank.depositInventory(player)

        assertEquals(2, player.bankIds().size)
        assertEquals(2, player.inventory.occupiedSlotCount)
        assertEquals(1, messages().count { it == Bank.BANK_FULL_MESSAGE })
    }

    @Test
    fun `deposit buttons with nothing to deposit say so`() {
        val player = newPlayer()
        Bank.depositInventory(player)
        Bank.depositEquipment(player)
        assertEquals(listOf(Bank.EMPTY_INVENTORY_MESSAGE, Bank.EMPTY_EQUIPMENT_MESSAGE), messages())
    }

    @Test
    fun `withdraw as note refuses an unnotable item with a message and withdraws it as itself`() {
        val unnotable =
            (1 until 20000).first {
                val def = DEFINITIONS.get(ItemDef::class.java, it)
                def.name.isNotBlank() && def.name != "null" && !def.stackable && def.noteLinkId <= 0 && !def.isPlaceholder
            }
        val player = newPlayer()
        player.bank[0] = Item(unnotable, 2)
        player.setVarp(Bank.WITHDRAW_AS_VARB, 1)

        Bank.withdraw(player, unnotable, 2, 0)

        assertEquals(2, player.inventory.getItemCount(unnotable))
        assertTrue(Bank.CANNOT_NOTE_MESSAGE in messages())
    }

    @Test
    fun `withdraw as note gives notes for a notable item without a message`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 3)
        player.setVarp(Bank.WITHDRAW_AS_VARB, 1)

        Bank.withdraw(player, Items.ABYSSAL_WHIP, 3, 0)

        val noted = Item(Items.ABYSSAL_WHIP).toNoted(DEFINITIONS).id
        assertEquals(3, player.inventory.getItemCount(noted))
        assertNull(player.bank[0])
        assertFalse(Bank.CANNOT_NOTE_MESSAGE in messages())
    }

    @Test
    fun `withdrawing the last item of the viewed tab removes the tab and shows the whole bank`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.DRAGON_DAGGER, 1)
        player.bank[2] = Item(Items.COINS_995, 5)
        BankTabs.setTabSize(player, 1, 1)
        BankTabs.setTabSize(player, 2, 1)
        player.setVarbit(BankTabs.SELECTED_TAB_VARBIT, 3) // viewing tab 2

        Bank.withdraw(player, Items.ABYSSAL_WHIP, 1, 0) // empties tab 1: tab 2 becomes tab 1

        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertEquals(1, BankTabs.selectedTab(player), "the viewed tab followed its items")
    }

    @Test
    fun `menu ops map to the OSRS amounts`() {
        val player = newPlayer()
        player.setVarp(Bank.LAST_X_INPUT, 250)
        assertEquals(listOf(1, 5, 10, 250, -1, 40, 39), listOf(61, 64, 4, 52, 81, 91, 18).map { Bank.withdrawAmount(player, it, 40) })
        assertNull(Bank.withdrawAmount(player, 25, 40))
        assertEquals(0, Bank.withdrawAmount(player, 18, 1), "all-but-one of a single item withdraws nothing")
        assertEquals(listOf(1, 5, 10, 250, -1, 7), listOf(61, 64, 4, 52, 81, 91).map { Bank.bankInventoryDepositAmount(player, it, 7) })
        // Deposit box menu (openDepositBox): op4 'Deposit-All', op5 'Deposit-X'.
        assertEquals(listOf(1, 5, 10, 7, -1, 7), listOf(61, 64, 4, 52, 81, 91).map { Bank.depositBoxAmount(it, 7) })
    }

    @Test
    fun `collapsing the last tab with an empty main tab moves its items to the main tab`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.DRAGON_DAGGER, 1)
        player.bank[2] = Item(Items.DRAGON_SCIMITAR, 1)
        BankTabs.setTabSize(player, 1, 2)
        BankTabs.setTabSize(player, 2, 1)

        BankTabs.collapseTab(player, 2)

        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertEquals(listOf(Items.ABYSSAL_WHIP, Items.DRAGON_DAGGER, Items.DRAGON_SCIMITAR), player.bankIds())
        assertEquals(0, BankTabs.getCurrentTab(player, 2))
    }

    @Test
    fun `collapsing a middle tab keeps the other tabs and puts its items at the end of the main tab`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1) // tab 1
        player.bank[1] = Item(Items.DRAGON_DAGGER, 1) // tab 1
        player.bank[2] = Item(Items.DRAGON_SCIMITAR, 1) // tab 2
        player.bank[3] = Item(Items.COINS_995, 10) // main
        BankTabs.setTabSize(player, 1, 2)
        BankTabs.setTabSize(player, 2, 1)

        BankTabs.collapseTab(player, 1)

        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertEquals(listOf(Items.DRAGON_SCIMITAR, Items.COINS_995, Items.ABYSSAL_WHIP, Items.DRAGON_DAGGER), player.bankIds())
        assertEquals(0, BankTabs.selectedTab(player))
    }

    @Test
    fun `dropping onto a tab past the new-tab slot changes nothing`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.COINS_995, 10)
        BankTabs.setTabSize(player, 1, 1)

        BankTabs.dropToTab(player, 4, 1)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())

        BankTabs.dropToTab(player, 2, 1) // the '+' tab
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertEquals(listOf(Items.ABYSSAL_WHIP, Items.COINS_995), player.bankIds())
    }

    @Test
    fun `bank space counters split free and members slots`() {
        val player = newPlayer()
        assertTrue(DEFINITIONS.get(ItemDef::class.java, Items.ABYSSAL_WHIP).members)
        assertFalse(DEFINITIONS.get(ItemDef::class.java, Items.COINS_995).members)
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.COINS_995, 10)
        player.bank[2] = Item(Items.DRAGON_DAGGER, 1)

        assertEquals(1 to 3, Bank.spaceCounts(player))
        Bank.refreshSpaceCounts(player, force = true)
        assertEquals(1, player.varcs[Bank.SPACE_USED_FREE_VARC])
        assertEquals(3, player.varcs[Bank.SPACE_USED_TOTAL_VARC])
    }

    @Test
    fun `equipment view varbit is 8348 and the bank route keeps it in step`() {
        val player = newPlayer()
        assertEquals(0, player.getVarbit(Bank.EQUIPMENT_VIEW_VARBIT))
        // 667:49 without having come from the bank (varbit 4894 = 0): no bank opens and 8348 = 1 keeps 667:0 shown (CS2 2319).
        Bank.showBank(player)
        assertEquals(1, player.getVarbit(Bank.EQUIPMENT_VIEW_VARBIT))
    }

    @Test
    fun `open no longer resets the note toggle, arms search on varc 190 and sends the deposit box ops with six ints`() {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/Bank.kt").readText()
        assertFalse("setVarp(WITHDRAW_AS_VARB, 0)" in source, "withdraw-as-note persists like OSRS")
        assertFalse("sendTempVarbit(190" in source, "varbit 190 is not the search arm")
        assertFalse("component = 117, hidden = true" in source, "Show Equipment Stats is no longer hidden")
        val box = source.substringAfter("fun openDepositBox").substringBefore("setEvents")
        assertTrue(Regex("""5,\s*0,\s*-1,\s*"Deposit-1"""").containsMatchIn(box), "CS2 150 has six int args")
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/inter/bank/bank.plugin.kts").readText()
        listOf(
            "component = Bank.DEPOSIT_BOB_COMPONENT",
            "component = Bank.DEPOSIT_BOX_BOB_COMPONENT",
            "component = Bank.EQUIPMENT_STATS_COMPONENT",
            "interfaceId = Bank.EQUIPMENT_BONUS_INTERFACE_ID, component = Bank.SHOW_BANK_COMPONENT",
            "Bank.armSearch(player)",
        ).forEach { assertTrue(it in plugin, it) }
        assertFalse("Varps.VARP_190" in plugin)
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
