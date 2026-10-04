package gg.rsmod.plugins.content.inter.bank

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Varbits
import gg.rsmod.plugins.api.ext.*
import mu.KLogging

/**
 * @author Tom <rspsmods@gmail.com>
 *
 * E7 (owner 2026-10-03, "onze bank moet precies functioneren als osrs bank"): every component used below was read
 * from the revision-667 cache with `runInterfaceHookProbeTool layout|interface 762 / 763 / 11 / 667` and the CS2
 * scripts behind them (`disasm 1449 1465 1471 1472 2318 2319 150`), never assumed.
 */
class Bank {
    companion object : KLogging() {
        const val DEPOSIT_BOX_INTERFACE_ID = 11
        const val BANK_INTERFACE_ID = 762
        const val BANK_HELP_INTERFACE_ID = 767
        const val BANK_MAINTAB_COMPONENT = 93
        const val INV_INTERFACE_ID = 763
        const val INV_INTERFACE_CHILD = 0

        const val REARRANGE_MODE_VARP = 304
        const val WITHDRAW_AS_VARB = 115
        const val LAST_X_INPUT = 1249

        /** 762:17 op1 'Search'. */
        const val SEARCH_COMPONENT = 17

        /**
         * Varc 190 arms the search button: CS2 1449 binds 762:17's onVarcTransmit to script 1472 on varc 190, and 1472
         * only puts 'Search' + onOp 1471 back when varc 190 is true. 1471 (the click) clears it to 0, so the server has
         * to set it back to 1 after every click (void `armBankSearch`), otherwise the button goes dead after one use.
         */
        const val SEARCH_ARM_VARC = 190
        const val SEARCH_ARM_SCRIPT = 1472

        /**
         * CS2 1465 (bound by 1449 on varcs 192/1038 to the 762:21 layer) prints the bank space counters 762:29-32:
         * varc 1038 = slots used by free-to-play items, varc 192 = all slots used (members = 192 - 1038).
         */
        const val SPACE_USED_FREE_VARC = 1038
        const val SPACE_USED_TOTAL_VARC = 192

        /** 762:37 / 11:22 op1 'Deposit beast of burden inventory'. */
        const val DEPOSIT_BOB_COMPONENT = 37
        const val DEPOSIT_BOX_BOB_COMPONENT = 22

        /** 762:117 op1 'Show Equipment Stats' (icon 762:118). */
        const val EQUIPMENT_STATS_COMPONENT = 117
        const val EQUIPMENT_STATS_ICON_COMPONENT = 118

        /**
         * Varbit 8348 (varp 638): CS2 2318 (onOp of 762:117 and of 667:49) toggles it client-side and CS2 2319 then hides
         * the bank (762:0, 763:0) and shows the equipment screen (667:0, 763:1) - or the reverse. The server keeps it in
         * step: 1 while the equipment stats replace the bank, 0 whenever the bank is shown.
         */
        const val EQUIPMENT_VIEW_VARBIT = 8348
        const val EQUIPMENT_BONUS_INTERFACE_ID = 667
        const val EQUIPMENT_SIDE_INTERFACE_ID = 670

        /** 667:49 op1 'Show bank' ("Return to the bank"). */
        const val SHOW_BANK_COMPONENT = 49

        /** Same masks the equipment stats screen itself sends (ops 1, 9, 10). */
        private const val EQUIPMENT_SCREEN_EVENTS = 1538

        /** Source: OSRS Wiki, Bank (Withdraw-All but one). */
        const val ALL_BUT_ONE_MESSAGE = "You only have one of this item, so withdrawing all but one does nothing."

        /** Source: OSRS Wiki, Bank (stack limit). */
        const val FULL_STACK_MESSAGE = "You already have a full stack of that item in the bank."

        /** Source: Donors/void BankWithdraw.kt. */
        const val CANNOT_NOTE_MESSAGE = "This item cannot be withdrawn as a note."

        /** Source: Donors/void BankDeposit.kt. */
        const val EMPTY_INVENTORY_MESSAGE = "You have no items in your inventory to deposit."
        const val EMPTY_EQUIPMENT_MESSAGE = "You have no equipped items to deposit."
        const val EMPTY_FAMILIAR_MESSAGE = "Your familiar has no items to deposit."

        /** Same text the server's own familiar inventory window uses when no carrier is out. */
        const val NO_FAMILIAR_MESSAGE = "You don't have a familiar that can carry items."

        const val BANK_FULL_MESSAGE = "Bank full."

        /** Prompt of Withdraw-X / Deposit-X. OSRS: "Enter amount:". */
        const val AMOUNT_PROMPT = "Enter amount:"

        /**
         * Clean any empty slots in the bank. Shift tabs as needed.
         */
        fun cleanEmptySlots(player: Player) {
            var hasSeenItem = false
            for (index in player.bank.capacity - 1 downTo 0) {
                if (player.bank[index] == null) {
                    if (hasSeenItem) {
                        val tab = BankTabs.getCurrentTab(player, index)
                        if (tab != 0) {
                            BankTabs.updateTabSize(player, tab, delta = -1)
                        }
                    }
                } else {
                    hasSeenItem = true
                    player.bank[index]!!.removeAttr(ItemAttribute.BANK_TAB)
                }
            }

            player.bank.shift()
            BankTabs.shiftTabs(player)
        }

        fun depositInventory(player: Player) {
            depositAll(player, player.inventory, EMPTY_INVENTORY_MESSAGE)
        }

        fun depositEquipment(player: Player) {
            val equipment = player.equipment
            if (equipment.occupiedSlotCount == 0) {
                player.filterableMessage(EMPTY_EQUIPMENT_MESSAGE)
                return
            }
            for (index in 0 until equipment.capacity) {
                val item = equipment[index] ?: continue
                val result = depositInternal(player, equipment, index, item.amount)
                if (result.deposited > 0) {
                    EquipAction.onItemUnequip(player, item.id, index)
                }
                if (result.bankFull) {
                    break
                }
            }
        }

        /**
         * 762:37 / 11:22 'Deposit beast of burden inventory': everything the summoned beast of burden carries goes
         * straight to the bank.
         */
        fun depositBeastOfBurden(player: Player) {
            val container = gg.rsmod.plugins.content.skills.summoning.BeastOfBurden.activeContainer(player)
            if (container == null) {
                player.filterableMessage(NO_FAMILIAR_MESSAGE)
                return
            }
            depositAll(player, container, EMPTY_FAMILIAR_MESSAGE)
        }

        /**
         * Deposits every item of [from]. One "bank full" stops the whole run, so the message is shown once instead of
         * once per remaining item.
         */
        fun depositAll(
            player: Player,
            from: ItemContainer,
            emptyMessage: String,
        ) {
            if (from.occupiedSlotCount == 0) {
                player.filterableMessage(emptyMessage)
                return
            }
            for (index in 0 until from.capacity) {
                val item = from[index] ?: continue
                if (depositInternal(player, from, index, item.amount).bankFull) {
                    break
                }
            }
        }

        fun deposit(
            player: Player,
            from: ItemContainer,
            fromSlot: Int,
            amt: Int,
        ): Boolean = depositInternal(player, from, fromSlot, amt).success

        internal data class DepositResult(
            val deposited: Int,
            val success: Boolean,
            val bankFull: Boolean,
        )

        private fun depositInternal(
            player: Player,
            from: ItemContainer,
            fromSlot: Int,
            amt: Int,
        ): DepositResult {
            val to = player.bank
            val item = from[fromSlot] ?: return DepositResult(0, success = false, bankFull = false)
            if (amt <= 0) return DepositResult(0, success = false, bankFull = false)
            // Audit D-01: a loot key's loot lives with the key, so the key itself can't be banked.
            if (gg.rsmod.plugins.content.mechanics.pvp.LootKeys.isKey(item.id)) {
                player.filterableMessage("You can't bank a loot key. Open it at Skully instead.")
                return DepositResult(0, success = false, bankFull = false)
            }
            val amount = from.getItemCount(item.id).coerceAtMost(amt)
            val value = gg.rsmod.plugins.content.mechanics.pvp.LootKeys.value(player.world.definitions, listOf(Item(item.id, amount)))
            if (gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.blocksDeposit(player, value)) {
                player.filterableMessage("You can't deposit items worth 20,000 coins or more so soon after combat.")
                return DepositResult(0, success = false, bankFull = false)
            }
            // The bank always holds the unnoted form; a full stack of it takes nothing more (OSRS stack limit).
            val bankId = item.toUnnoted(player.world.definitions).id
            if (to.getItemCount(bankId) == Int.MAX_VALUE) {
                player.filterableMessage(FULL_STACK_MESSAGE)
                return DepositResult(0, success = false, bankFull = false)
            }
            val currentTab = BankTabs.selectedTab(player)

            var deposited = 0
            var transferFailed = false
            var bankFull = false
            while (deposited < amount && !transferFailed && !bankFull) {
                val slot = determineDepositSlot(player, item)
                if (slot == -1) {
                    bankFull = true
                } else {
                    val isEmptySlot = to[slot] == null
                    val copy = Item(item, amount - deposited)
                    val transfer =
                        from.transfer(
                            to,
                            item = copy,
                            fromSlot = fromSlot,
                            toSlot = slot,
                            note = false,
                            unnote = true,
                        )
                    if (transfer == null) {
                        transferFailed = true
                    } else {
                        deposited += transfer.completed
                        // Only a new stack that really landed in the free slot moves into the viewed tab.
                        if (currentTab > 0 && isEmptySlot && to[slot] != null) {
                            BankTabs.dropToTab(player, currentTab, slot)
                        }
                    }
                }
            }

            if (bankFull) {
                player.filterableMessage(BANK_FULL_MESSAGE)
            } else if (transferFailed && deposited > 0 && to.getItemCount(bankId) == Int.MAX_VALUE) {
                player.filterableMessage(FULL_STACK_MESSAGE)
            }
            if (deposited > 0) {
                refreshSpaceCounts(player)
                gg.rsmod.plugins.content.mechanics.ItemMoveSounds.putIn(player)
            }

            return DepositResult(deposited, success = !transferFailed && !bankFull, bankFull = bankFull)
        }

        /**
         * Figures out the slot an item should be deposited to. The bank stacks the unnoted id, so a noted item looks
         * for its unnoted stack - it used to look for the noted id, miss the existing stack and treat the deposit as a
         * new slot, which with a tab selected moved an empty slot into that tab.
         */
        internal fun determineDepositSlot(
            player: Player,
            item: Item,
        ): Int {
            // A bank placeholder (the item with count 0, see [isPlaceholder]) is that item's stack: the deposit fills it in place.
            val bankId = item.toUnnoted(player.world.definitions).id
            return player.bank
                .indexOfFirst {
                    it != null && it.id == bankId && it.amount < Int.MAX_VALUE
                }.takeUnless { it == -1 } ?: player.bank.nextFreeSlot
        }

        /**
         * E7: an OSRS bank placeholder is the item itself with count 0 (OSRS script 278 draws it with `cc_setobject(obj, 0)`,
         * transparency 120, "Release" / "Examine"). It keeps its slot and tab; a deposit of the item fills it
         * ([determineDepositSlot]).
         */
        fun isPlaceholder(item: Item?): Boolean = item != null && item.amount == 0

        /** Leaves a placeholder in [slot] (just emptied by a withdraw) when [explicit] ('Placeholder') or 'Always set placeholders' is on. */
        private fun leavesPlaceholder(
            player: Player,
            explicit: Boolean,
        ): Boolean = explicit || BankOptions.alwaysPlaceholder(player)

        /** 'Release' (op8 of a placeholder): the slot is freed and the bank closes up, like withdrawing the last item. */
        fun releasePlaceholder(
            player: Player,
            slot: Int,
        ): Boolean {
            if (slot !in 0 until player.bank.capacity || !isPlaceholder(player.bank[slot])) {
                player.bank.dirty = true
                return false
            }
            val tab = BankTabs.getCurrentTab(player, slot)
            player.bank[slot] = null
            onSlotEmptied(player, tab)
            return true
        }

        /** Releases every placeholder in [slots] (descending, so a release never moves a slot still to be visited). */
        private fun releasePlaceholders(
            player: Player,
            slots: IntRange,
        ): Int {
            var released = 0
            for (slot in slots.reversed()) {
                if (slot in 0 until player.bank.capacity && isPlaceholder(player.bank[slot]) && releasePlaceholder(player, slot)) {
                    released++
                }
            }
            return released
        }

        /** 'Release all placeholders' (OSRS script 1272). */
        fun releaseAllPlaceholders(player: Player): Int = releasePlaceholders(player, 0 until player.bank.capacity)

        /** Tab op 'Remove placeholders' (OSRS scripts 506 / 507); tab 0 is the 'View all' tab: the whole bank. */
        fun removeTabPlaceholders(
            player: Player,
            tab: Int,
        ): Int {
            if (tab == 0) return releaseAllPlaceholders(player)
            if (tab !in 1..BankTabs.MAX_BANK_TABS || BankTabs.getTabSize(player, tab) == 0) return 0
            return releasePlaceholders(player, BankTabs.tabIndices(player, tab))
        }

        /** A bank slot just became empty: close the gap, shrink its tab (a tab that empties disappears) and recount the space. */
        private fun onSlotEmptied(
            player: Player,
            tab: Int,
        ) {
            player.bank.shift()
            if (tab != 0) {
                BankTabs.updateTabSize(player, tab, -1)
                if (BankTabs.getTabSize(player, tab) == 0) {
                    BankTabs.shiftTabs(player)
                    BankTabs.afterTabRemoved(player, tab)
                }
            }
            refreshSpaceCounts(player)
        }

        fun withdraw(
            player: Player,
            id: Int,
            amt: Int,
            fromSlot: Int,
            placeholder: Boolean = false,
        ) {
            val from = player.bank
            val to = player.inventory

            /*
             * Withdraw-X resumes after an input dialog, by which time the bank may have shifted
             * (another withdrawal, tab move, deposit). Only act if the slot still holds the exact
             * item the click was made on; otherwise this withdrew a different item, or threw on an
             * empty slot.
             */
            if (fromSlot !in 0 until from.capacity) {
                player.bank.dirty = true
                return
            }
            val slotItem = from[fromSlot]
            // A placeholder (count 0) has nothing to withdraw.
            if (slotItem == null || slotItem.id != id || amt <= 0 || slotItem.amount <= 0) {
                player.bank.dirty = true
                return
            }
            val tab = BankTabs.getCurrentTab(player, fromSlot)

            val amount = from.getItemCount(id).coerceAtMost(amt)
            val note = player.getVarp(WITHDRAW_AS_VARB) == 1
            // Stackable items need no note; only an unstackable item without a noted form is refused (and withdrawn as itself).
            if (note && !slotItem.getDef(player.world.definitions).stackable && slotItem.toNoted(player.world.definitions).id == slotItem.id) {
                player.filterableMessage(CANNOT_NOTE_MESSAGE)
            }

            val copy = Item(slotItem, amount)
            val withdrawn =
                from.transfer(to, item = copy, fromSlot = fromSlot, note = note, unnote = !note)?.completed ?: 0
            if (withdrawn > 0) {
                gg.rsmod.plugins.content.mechanics.ItemMoveSounds.takeOut(player)
                if (from[fromSlot] == null) {
                    if (leavesPlaceholder(player, placeholder)) {
                        // The slot, its tab and its place in the bank stay: the item's placeholder (OSRS 'Placeholder' /
                        // 'Always set placeholders').
                        from[fromSlot] = Item(slotItem.id, 0)
                    } else {
                        onSlotEmptied(player, tab)
                    }
                }
            }

            if (withdrawn == 0) {
                player.filterableMessage("You don't have enough inventory space.")
            } else if (withdrawn != amount) {
                player.filterableMessage("You don't have enough inventory space to withdraw that many.")
            }
        }

        /**
         * The withdraw amount of a bank item menu option (762:93, OSRS script 669 layout - see [BankOptions.withdrawMenu]):
         * -1 for 'Withdraw-X' (asks), null for an op that withdraws no amount ('Placeholder', 'Examine', none).
         */
        fun withdrawAmount(
            player: Player,
            opcode: Int,
            stackAmount: Int,
        ): Int? {
            val custom = BankOptions.customQuantity(player)
            return when (BankOptions.withdrawAction(opcode, placeholder = false)) {
                BankOptions.WithdrawAction.DEFAULT -> BankOptions.defaultAmount(BankOptions.quantityMode(player), custom, stackAmount)
                BankOptions.WithdrawAction.ONE -> 1
                BankOptions.WithdrawAction.FIVE -> 5
                BankOptions.WithdrawAction.TEN -> 10
                BankOptions.WithdrawAction.CUSTOM -> custom.takeIf { it > 0 }
                BankOptions.WithdrawAction.X -> -1
                BankOptions.WithdrawAction.ALL -> stackAmount
                BankOptions.WithdrawAction.ALL_BUT_ONE -> stackAmount - 1
                else -> null
            }
        }

        /**
         * Sends the bank space counters (varc 1038 free-to-play slots used, varc 192 all slots used) that CS2 1465 prints
         * on 762:29-32. They were never sent, so the bank always read 0 used.
         */
        fun refreshSpaceCounts(
            player: Player,
            force: Boolean = false,
        ) {
            val (free, total) = spaceCounts(player)
            if (force || player.getVarc(SPACE_USED_FREE_VARC) != free) {
                player.setVarc(SPACE_USED_FREE_VARC, free)
            }
            if (force || player.getVarc(SPACE_USED_TOTAL_VARC) != total) {
                player.setVarc(SPACE_USED_TOTAL_VARC, total)
            }
        }

        /** (free-to-play slots used, all slots used). */
        fun spaceCounts(player: Player): Pair<Int, Int> {
            val definitions = player.world.definitions
            var total = 0
            var free = 0
            for (index in 0 until player.bank.capacity) {
                val item = player.bank[index] ?: continue
                total++
                if (!item.getDef(definitions).members) {
                    free++
                }
            }
            return free to total
        }

        /** Re-arms the search button after a click (see [SEARCH_ARM_VARC]). */
        fun armSearch(player: Player) {
            player.setVarc(SEARCH_ARM_VARC, 1)
            player.runClientScript(SEARCH_ARM_SCRIPT)
        }

        fun open(player: Player) {
            if (gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.denyBank(player)) return
            if (gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp.isHoldingTempGear(player)) {
                player.message("You can't access your bank while in a Practice PvP match.")
                return
            }
            /*
             * The PIN is asked for here rather than at each booth, chest and banker, so that every
             * way into the bank is behind it - including the ones added later.
             */
            if (BankPin.required(player)) {
                BankPin.request(player) { open(it) }
                return
            }
            // The bank is the visible screen again (CS2 2319 hides 762:0 while varbit 8348 is 1).
            player.setVarbit(EQUIPMENT_VIEW_VARBIT, 0)
            // E7: the quantity / placeholder state first, so the client's first build of both item menus already uses it.
            BankOptions.sendState(player)
            player.openInterface(BANK_INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
            player.openInterface(INV_INTERFACE_ID, InterfaceDestination.TAB_AREA)
            player.inventory.dirty = true
            player.bank.dirty = true
            // Ops 1-10: the OSRS menu has 'Withdraw-All-but-1' at op8 and 'Placeholder' at op9 (placeholders: op8 'Release').
            player.setEvents(interfaceId = BANK_INTERFACE_ID, component = BANK_MAINTAB_COMPONENT, to = player.bank.capacity - 1, setting = BankOptions.BANK_ITEM_EVENTS)
            BankOptions.sendControls(player)
            player.setEvents(interfaceId = INV_INTERFACE_ID, to = 27, setting = 0x25047E)

            /*
             * E7: 'Withdraw as: Note' and the rearrange mode are kept between visits (OSRS; void persists varp 115 and
             * 304). The note toggle used to be reset to Item on every open. 762:117/118 'Show Equipment Stats' is no
             * longer hidden - it now opens the equipment screen (see [showEquipmentStats]).
             */
            if (player.getVarbit(Varbits.RESET_BANK_TAB_VIEW_INDEX) == 0) {
                player.setVarbit(Varbits.RESET_BANK_TAB_VIEW_INDEX, 1)
            }
            armSearch(player)
            refreshSpaceCounts(player, force = true)
        }

        /**
         * 762:117 'Show Equipment Stats' (OSRS "Show worn items"): the equipment stats screen 667 with its own inventory
         * 670 replaces the bank, with 667:49 'Show bank' to come back ([showBank]). Varbit 4894 tells 667 it was opened
         * from the bank. Same mount as the equipment tab's own route (equipment_stats_interface.plugin.kts).
         */
        fun showEquipmentStats(player: Player) {
            if (!player.interfaces.isVisible(BANK_INTERFACE_ID)) return
            player.setVarbit(EQUIPMENT_VIEW_VARBIT, 1)
            player.openInterface(EQUIPMENT_BONUS_INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
            player.setVarbit(Varbits.IS_BANK_EQUIPMENT_INTERFACE, 1)
            player.openInterface(EQUIPMENT_SIDE_INTERFACE_ID, InterfaceDestination.TAB_AREA)
            player.setInterfaceEvents(EQUIPMENT_SIDE_INTERFACE_ID, 0, 0 until player.inventory.capacity, EQUIPMENT_SCREEN_EVENTS)
            player.setInterfaceEvents(EQUIPMENT_BONUS_INTERFACE_ID, 7, 0 until player.equipment.capacity, EQUIPMENT_SCREEN_EVENTS)
            player.refreshBonuses()
            player.equipment.dirty = true
            player.inventory.dirty = true
        }

        /** 667:49 'Show bank': only when the equipment screen was opened from the bank. */
        fun showBank(player: Player) {
            if (player.getVarbit(Varbits.IS_BANK_EQUIPMENT_INTERFACE) != 1) {
                // The client already toggled varbit 8348 locally; 1 keeps 667:0 shown (CS2 2319), and [open] resets it.
                player.setVarbit(EQUIPMENT_VIEW_VARBIT, 1)
                return
            }
            player.setVarbit(Varbits.IS_BANK_EQUIPMENT_INTERFACE, 0)
            open(player)
        }

        fun openDepositBox(player: Player) {
            if (gg.rsmod.plugins.content.mechanics.pvp.BankSecurity.denyBank(player)) return
            if (gg.rsmod.plugins.content.mechanics.practicepvp.PracticePvp.isHoldingTempGear(player)) {
                player.message("You can't access your bank while in a Practice PvP match.")
                return
            }
            player.openInterface(DEPOSIT_BOX_INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
            player.openInterface(INV_INTERFACE_ID, InterfaceDestination.TAB_AREA)
            /*
             * CS2 150 takes six ints (`disasm 150`: intArgCount=6) - component, inv, columns, rows, 0, -1 - then the
             * ops. The -1 was missing, so the client ran it with a 0 there. Options op1..op5 (Novite ButtonHandler 11:17).
             */
            player.runClientScript(
                150,
                DEPOSIT_BOX_INTERFACE_ID shl 16 or 17,
                93,
                6,
                5,
                0,
                -1,
                "Deposit-1",
                "Deposit-5",
                "Deposit-10",
                "Deposit-All",
                "Deposit-X",
            )
            player.setEvents(interfaceId = DEPOSIT_BOX_INTERFACE_ID, component = 17, to = 27, setting = 1150)
        }

        /**
         * The deposit amount of a deposit-box item op (11:17, ops from [openDepositBox]): op4 is Deposit-All and op5
         * Deposit-X there. op4 used to deposit the last-X amount while the menu said "Deposit-All".
         */
        fun depositBoxAmount(
            opcode: Int,
            inventoryCount: Int,
        ): Int? =
            when (opcode) {
                61 -> 1
                64 -> 5
                4 -> 10
                52 -> inventoryCount // op4 Deposit-All
                81 -> -1 // op5 Deposit-X
                91 -> inventoryCount
                else -> null
            }

        /**
         * The deposit amount of a bank side inventory op (763:0, OSRS script 172 layout - see [BankOptions.depositMenu]):
         * op1 the default quantity, op2-4 1 / 5 / 10, op5 Deposit-X (-1, asks), op6 Deposit-All.
         */
        fun bankInventoryDepositAmount(
            player: Player,
            opcode: Int,
            inventoryCount: Int,
        ): Int? =
            when (BankOptions.depositAction(opcode)) {
                BankOptions.DepositAction.DEFAULT ->
                    BankOptions.defaultAmount(BankOptions.quantityMode(player), BankOptions.customQuantity(player), inventoryCount)
                BankOptions.DepositAction.ONE -> 1
                BankOptions.DepositAction.FIVE -> 5
                BankOptions.DepositAction.TEN -> 10
                BankOptions.DepositAction.X -> -1
                BankOptions.DepositAction.ALL -> inventoryCount
                else -> null
            }

        fun swap(
            player: Player,
            from: Int,
            to: Int,
        ) {
            player.bank.swap(from, to)
        }

        /**
         * Inserts an item from one slot in the bank to another slot, adjusting tab sizes when necessary
         */
        fun tabSafeInsert(
            player: Player,
            from: Int,
            to: Int,
        ) {
            val targetTab = BankTabs.getCurrentTab(player, to)
            val sourceTab = BankTabs.getCurrentTab(player, from)
            player.bank.insert(from, to)
            if (targetTab != 0) {
                BankTabs.updateTabSize(player, targetTab, 1)
            }

            if (sourceTab != 0) {
                BankTabs.updateTabSize(player, sourceTab, -1)
                if (BankTabs.getTabSize(player, sourceTab) == 0) {
                    BankTabs.shiftTabs(player)
                }
            }
        }

        fun ItemContainer.insert(
            from: Int,
            to: Int,
        ) {
            val fromItem = this[from]!! // Shouldn't be null

            this[from] = null

            if (from < to) {
                for (i in from until to) {
                    this[i] = this[i + 1]
                }
            } else {
                for (i in from downTo to + 1) {
                    this[i] = this[i - 1]
                }
            }
            this[to] = fromItem
        }
    }
}
