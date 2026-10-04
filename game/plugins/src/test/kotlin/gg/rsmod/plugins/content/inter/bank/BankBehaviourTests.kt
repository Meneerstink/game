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
        // OSRS script 669 with the default quantity 1: op1 Withdraw-1, op3 -5, op4 -10, op5 -<n>, op6 -X, op7 -All, op8 -All-but-1.
        assertEquals(
            listOf(1, 1, 5, 10, 250, -1, 40, 39),
            listOf(61, 64, 4, 52, 81, 91, 18, 10).map { Bank.withdrawAmount(player, it, 40) },
        )
        assertNull(Bank.withdrawAmount(player, 20, 40), "op9 is 'Placeholder', no amount")
        assertNull(Bank.withdrawAmount(player, 25, 40))
        assertEquals(0, Bank.withdrawAmount(player, 10, 1), "all-but-one of a single item withdraws nothing")
        // OSRS script 172: op1 the default, op2-4 1 / 5 / 10, op5 Deposit-X, op6 Deposit-All.
        assertEquals(listOf(1, 1, 5, 10, -1, 7), listOf(61, 64, 4, 52, 81, 91).map { Bank.bankInventoryDepositAmount(player, it, 7) })
        // Deposit box menu (openDepositBox): op4 'Deposit-All', op5 'Deposit-X'.
        assertEquals(listOf(1, 5, 10, 7, -1, 7), listOf(61, 64, 4, 52, 81, 91).map { Bank.depositBoxAmount(it, 7) })
    }

    @Test
    fun `the default quantity is what a left-click withdraws and deposits`() {
        val player = newPlayer()
        val expected = mapOf(BankOptions.MODE_ONE to 1, BankOptions.MODE_FIVE to 5, BankOptions.MODE_TEN to 10, BankOptions.MODE_ALL to 40)
        expected.forEach { (mode, amount) ->
            BankOptions.setQuantityMode(player, mode)
            assertEquals(amount, Bank.withdrawAmount(player, BankOptions.OP1, 40), "withdraw, mode $mode")
            assertEquals(amount, Bank.bankInventoryDepositAmount(player, BankOptions.OP1, 40), "deposit, mode $mode")
        }
        // X: the custom amount, at least 1 (OSRS script 2579: max(1, n)).
        BankOptions.setQuantityMode(player, BankOptions.MODE_X)
        assertEquals(1, Bank.withdrawAmount(player, BankOptions.OP1, 40))
        player.setVarp(Bank.LAST_X_INPUT, 14)
        assertEquals(14, Bank.withdrawAmount(player, BankOptions.OP1, 40))
        assertEquals(14, Bank.bankInventoryDepositAmount(player, BankOptions.OP1, 40))
        // The mode is kept on the player (persisted attribute) and named to the client on the side channel.
        assertEquals(BankOptions.MODE_X, BankOptions.quantityMode(player))
        assertTrue(messages().any { it == "${BankOptions.CLIENT_PREFIX}state|${BankOptions.MODE_X}|0" })
        BankOptions.setQuantityMode(player, 9)
        assertEquals(BankOptions.MODE_X, BankOptions.quantityMode(player), "an unknown mode changes nothing")
    }

    @Test
    fun `the item menus are the OSRS menus for every default quantity`() {
        assertEquals(
            listOf("Withdraw-1", "", "Withdraw-5", "Withdraw-10", "", "Withdraw-X", "Withdraw-All", "Withdraw-All-but-1", "Placeholder", "Examine"),
            BankOptions.withdrawMenu(BankOptions.MODE_ONE, custom = 0, alwaysPlaceholder = false),
        )
        assertEquals(
            listOf("Withdraw-All", "Withdraw-1", "Withdraw-5", "Withdraw-10", "Withdraw-25", "Withdraw-X", "Withdraw-All", "Withdraw-All-but-1", "", "Examine"),
            BankOptions.withdrawMenu(BankOptions.MODE_ALL, custom = 25, alwaysPlaceholder = true),
        )
        assertEquals("Withdraw-25", BankOptions.withdrawMenu(BankOptions.MODE_X, custom = 25, alwaysPlaceholder = false)[0])
        assertEquals("Withdraw-1", BankOptions.withdrawMenu(BankOptions.MODE_X, custom = 0, alwaysPlaceholder = false)[0])
        assertEquals(
            listOf("Deposit-5", "Deposit-1", "", "Deposit-10", "Deposit-X", "Deposit-All"),
            BankOptions.depositMenu(BankOptions.MODE_FIVE, custom = 0),
        )
        assertEquals(
            listOf("Deposit-All", "Deposit-1", "Deposit-5", "Deposit-10", "Deposit-X", ""),
            BankOptions.depositMenu(BankOptions.MODE_ALL, custom = 0),
        )
        // Every op the menu shows maps to the action its text names.
        val opcodes = listOf(BankOptions.OP1, BankOptions.OP2, BankOptions.OP3, BankOptions.OP4, BankOptions.OP5, BankOptions.OP6, BankOptions.OP7, BankOptions.OP8, BankOptions.OP9, BankOptions.OP10)
        val named =
            mapOf(
                "Withdraw-1" to BankOptions.WithdrawAction.ONE,
                "Withdraw-5" to BankOptions.WithdrawAction.FIVE,
                "Withdraw-10" to BankOptions.WithdrawAction.TEN,
                "Withdraw-X" to BankOptions.WithdrawAction.X,
                "Withdraw-All" to BankOptions.WithdrawAction.ALL,
                "Withdraw-All-but-1" to BankOptions.WithdrawAction.ALL_BUT_ONE,
                "Placeholder" to BankOptions.WithdrawAction.PLACEHOLDER,
                "Examine" to BankOptions.WithdrawAction.EXAMINE,
            )
        BankOptions.withdrawMenu(BankOptions.MODE_TEN, custom = 7, alwaysPlaceholder = false).forEachIndexed { index, text ->
            val action = BankOptions.withdrawAction(opcodes[index], placeholder = false)
            if (index == 0) {
                assertEquals(BankOptions.WithdrawAction.DEFAULT, action)
            } else if (text == "Withdraw-7") {
                assertEquals(BankOptions.WithdrawAction.CUSTOM, action)
            } else if (text.isNotEmpty()) {
                assertEquals(named[text], action, text)
            }
        }
        // A placeholder: only op8 'Release' and 'Examine' (OSRS script 278).
        assertEquals(BankOptions.WithdrawAction.RELEASE, BankOptions.withdrawAction(BankOptions.OP8, placeholder = true))
        assertEquals(BankOptions.WithdrawAction.EXAMINE, BankOptions.withdrawAction(BankOptions.OP10, placeholder = true))
        assertNull(BankOptions.withdrawAction(BankOptions.OP1, placeholder = true))
    }

    @Test
    fun `Placeholder withdraws everything and keeps the slot and tab, and a deposit fills it again`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 1)
        player.bank[1] = Item(Items.DRAGON_DAGGER, 1)
        player.bank[2] = Item(Items.COINS_995, 50)
        BankTabs.setTabSize(player, 1, 2)

        Bank.withdraw(player, Items.ABYSSAL_WHIP, 1, 0, placeholder = true)

        assertEquals(1, player.inventory.getItemCount(Items.ABYSSAL_WHIP))
        assertTrue(Bank.isPlaceholder(player.bank[0]))
        assertEquals(Items.ABYSSAL_WHIP, player.bank[0]!!.id)
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0, 0), player.tabSizes(), "the tab keeps its slot")
        assertFalse(player.bank.contains(Items.ABYSSAL_WHIP), "a placeholder is not an owned item")

        // Nothing to withdraw from a placeholder.
        Bank.withdraw(player, Items.ABYSSAL_WHIP, 1, 0)
        assertEquals(1, player.inventory.getItemCount(Items.ABYSSAL_WHIP))

        // Viewing another tab: the deposit still goes back into the placeholder's slot.
        player.setVarbit(BankTabs.SELECTED_TAB_VARBIT, 1)
        assertTrue(Bank.deposit(player, player.inventory, player.inventory.getItemIndex(Items.ABYSSAL_WHIP, false), 1))
        assertEquals(listOf(Items.ABYSSAL_WHIP, Items.DRAGON_DAGGER, Items.COINS_995), player.bankIds())
        assertEquals(1, player.bank[0]!!.amount)
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
    }

    @Test
    fun `Always set placeholders leaves a placeholder when the last item is withdrawn, off removes the slot`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.COINS_995, 50)
        player.bank[1] = Item(Items.ABYSSAL_WHIP, 2)

        BankOptions.setAlwaysPlaceholder(player, true)
        Bank.withdraw(player, Items.COINS_995, 20, 0)
        assertEquals(30, player.bank[0]!!.amount, "a partial withdraw leaves the stack")
        Bank.withdraw(player, Items.COINS_995, 30, 0)
        assertTrue(Bank.isPlaceholder(player.bank[0]))
        assertTrue(messages().any { it == "${BankOptions.CLIENT_PREFIX}state|${BankOptions.MODE_ONE}|1" })

        BankOptions.setAlwaysPlaceholder(player, false)
        Bank.withdraw(player, Items.ABYSSAL_WHIP, 2, 1)
        assertEquals(listOf(Items.COINS_995), player.bankIds(), "without the setting the slot goes")
    }

    @Test
    fun `a full bank still takes an item back into its placeholder`() {
        val bank = ItemContainer(DEFINITIONS, ContainerKey("bank", capacity = 2, stackType = ContainerStackType.STACK))
        val player = newPlayer(bank)
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 0)
        player.bank[1] = Item(Items.COINS_995, 50)
        player.inventory[0] = Item(Items.ABYSSAL_WHIP)
        player.inventory[1] = Item(Items.DRAGON_DAGGER)

        Bank.depositInventory(player)

        assertEquals(1, player.bank[0]!!.amount)
        assertEquals(Items.DRAGON_DAGGER, player.inventory[1]?.id, "no room for a new item")
        assertEquals(1, messages().count { it == Bank.BANK_FULL_MESSAGE })
    }

    @Test
    fun `release, release all and remove placeholders of a tab close the bank up like a withdraw`() {
        val player = newPlayer()
        player.bank[0] = Item(Items.ABYSSAL_WHIP, 0) // tab 1
        player.bank[1] = Item(Items.DRAGON_DAGGER, 1) // tab 1
        player.bank[2] = Item(Items.DRAGON_SCIMITAR, 0) // tab 2 (only a placeholder)
        player.bank[3] = Item(Items.COINS_995, 10) // main
        player.bank[4] = Item(Items.DRAGON_LONGSWORD, 0) // main
        BankTabs.setTabSize(player, 1, 2)
        BankTabs.setTabSize(player, 2, 1)

        assertFalse(Bank.releasePlaceholder(player, 1), "a real item is not released")
        assertTrue(Bank.releasePlaceholder(player, 0))
        assertEquals(listOf(Items.DRAGON_DAGGER, Items.DRAGON_SCIMITAR, Items.COINS_995, Items.DRAGON_LONGSWORD), player.bankIds())
        assertEquals(listOf(1, 1, 0, 0, 0, 0, 0, 0), player.tabSizes())

        // Tab 2 holds only a placeholder: removing its placeholders removes the tab.
        assertEquals(1, Bank.removeTabPlaceholders(player, 2))
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0, 0), player.tabSizes())
        assertEquals(listOf(Items.DRAGON_DAGGER, Items.COINS_995, Items.DRAGON_LONGSWORD), player.bankIds())

        assertEquals(1, Bank.releaseAllPlaceholders(player))
        assertEquals(listOf(Items.DRAGON_DAGGER, Items.COINS_995), player.bankIds())
    }

    @Test
    fun `client-built buttons map to their actions`() {
        val buttons = BankOptions.buttons(BankOptions.MODE_TEN, alwaysPlaceholder = false)
        assertEquals(listOf("1", "5", "10", "X", "All", "Placeholders: Off"), buttons.map { it.label })
        assertEquals(listOf(false, false, true, false, false, false), buttons.map { it.selected })
        assertEquals(listOf(1, 3, 5, 7, 9, 11), buttons.map { it.labelChild }, "consecutive children: the client refuses gaps")
        assertEquals(BankOptions.ButtonPress.QUANTITY to BankOptions.MODE_ALL, BankOptions.pressed(9, BankOptions.OP1))
        assertEquals(BankOptions.ButtonPress.SET_CUSTOM to BankOptions.MODE_X, BankOptions.pressed(7, BankOptions.OP2))
        assertNull(BankOptions.pressed(5, BankOptions.OP2), "only X has a second op")
        assertNull(BankOptions.pressed(4, BankOptions.OP1), "a background is not clickable")
        assertEquals(BankOptions.ButtonPress.TOGGLE_PLACEHOLDER to 0, BankOptions.pressed(11, BankOptions.OP1))
        assertEquals(BankOptions.ButtonPress.RELEASE_ALL to 0, BankOptions.pressed(11, BankOptions.OP2))
        assertEquals("Disable", BankOptions.buttons(BankOptions.MODE_ONE, alwaysPlaceholder = true).last().op1)
        // Quantity buttons sit between Search (762:17, x 64..99) and the centred note toggle (x 238..273).
        val quantity = buttons.take(5)
        assertTrue(quantity.first().x >= 100 && quantity.last().x + quantity.last().width <= 238)
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
