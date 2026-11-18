package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.game.model.attr.OTHER_ITEM_SLOT_ATTR
import gg.rsmod.plugins.content.inter.bank.openBank
import gg.rsmod.plugins.content.inter.bank.BankPin
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface
import gg.rsmod.plugins.content.skills.runecrafting.Altar

// No spawns here: Claude alone changes world_edits. Native bank Collect is already bound by banker_collect.plugin.kts.
Dmm2Boot.install()
Dmm2Ids.interfaceId(230)?.let { root ->
    on_interface_close(root) { Dmm2SafeDepositUi.close(player) }
    on_button(root, Dmm2SafeDepositUi.CLOSE) { player.closeInterface(root) }
    on_button(root, Dmm2SafeDepositUi.DEPOSIT_INVENTORY) { if (Dmm2Interactions.option(player) == 1) Dmm2SafeDepositUi.depositAll(player, false) }
    on_button(root, Dmm2SafeDepositUi.DEPOSIT_WORN) { if (Dmm2Interactions.option(player) == 1) Dmm2SafeDepositUi.depositAll(player, true) }
    repeat(10) { slot -> on_button(root, Dmm2SafeDepositUi.FIRST_SLOT + slot) {
        Dmm2SafeDepositUi.click(player, true, slot, Dmm2Interactions.option(player), player.getInteractingItemId())
    } }
}
Dmm2Ids.interfaceId(232)?.let { root ->
    on_interface_close(root) { Dmm2SafeDepositUi.close(player) }
    repeat(28) { slot -> on_button(root, Dmm2SafeDepositUi.SIDE_FIRST_SLOT + slot) {
        Dmm2SafeDepositUi.click(player, false, slot, Dmm2Interactions.option(player), player.getInteractingItemId())
    } }
}
Dmm2Ids.interfaceId(819)?.let { root ->
    on_interface_close(root) { Dmm2RewardUi.close(player) }
    on_button(root,11) { if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.toggleView(player) }
    on_button(root,12) { if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.help(player) }
    on_button(root,gg.rsmod.game.tools.importer.Dmm2WindowPort.INFO_FRAME_CLOSE) {
        if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.closeDetails(player)
    }
    on_button(root, gg.rsmod.game.tools.importer.Dmm2WindowPort.REWARD_CLOSE) { Dmm2RewardUi.close(player) }
    Dmm2RewardUi.PRODUCTS.indices.forEach { index ->
        on_button(root, gg.rsmod.game.tools.importer.Dmm2WindowPort.REWARD_FIRST + index*gg.rsmod.game.tools.importer.Dmm2WindowPort.REWARD_STRIDE) {
            Dmm2RewardUi.product(player, index, Dmm2Interactions.option(player))
        }
    }
    repeat(4) { index -> on_button(root, 26+index) {
        if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.buySelected(player, index)
    } }
}
Dmm2Ids.interfaceId(806)?.let { root ->
    on_interface_close(root) { Dmm2RewardUi.close(player) }
    on_button(root, 3) { if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.closeInfo(player) }
    repeat(28) { slot -> on_button(root, gg.rsmod.game.tools.importer.Dmm2WindowPort.SIDE_FIRST + slot) {
        Dmm2RewardUi.inventory(player, slot, Dmm2Interactions.option(player), player.getInteractingItemId())
    } }
    repeat(4) { index -> on_button(root, 8+index) {
        if (Dmm2Interactions.option(player) == 1) Dmm2RewardUi.sellSelected(player, index)
    } }
}
on_login {
    BreachRetirement.migrate(player)
    BloodMoney.deliverPending(player)
}
on_logout {
    Dmm2SafeDepositUi.close(player)
    Dmm2RewardUi.close(player)
    Dmm2Interactions.closeSafe(player)
    Dmm2Interactions.closeRewards(player)
    UndeadThralls.clear(player)
}

fun itemOption(id: Int, option: String, action: () -> Unit) {
    if (world.definitions.getNullable(ItemDef::class.java, id)?.inventoryMenu?.any { it.equals(option, true) } == true) action()
}

// These bindings wait for actual cache options; the behaviour itself uses existing food/teleport gates.
gg.rsmod.plugins.content.starter.DeadmanStarterPack.ids?.let { starter ->
    val actions = gg.rsmod.plugins.content.starter.StarterPackInteractions
    itemOption(starter.tuna, "Eat") { on_item_option(starter.tuna, "Eat") {
        actions.eatTuna(player, player.getInteractingItemSlot(), starter.tuna)
    } }
    itemOption(starter.chronicle, "Check charges") { on_item_option(starter.chronicle, "Check charges") {
        player.message(actions.checkMessage(player))
    } }
    itemOption(starter.chronicle, "Teleport") { on_item_option(starter.chronicle, "Teleport") {
        val slot = player.getInteractingItemSlot()
        player.inventory.rawItems.getOrNull(slot)?.let { actions.travel(player, it, slot, false) }
    } }
    val book = world.definitions.get(ItemDef::class.java, starter.chronicle)
    if (book.equipmentMenu.any { it.equals("Check charges", true) }) on_equipment_option(starter.chronicle, "Check charges") {
        player.message(actions.checkMessage(player))
    }
    if (book.equipmentMenu.any { it.equals("Teleport", true) }) on_equipment_option(starter.chronicle, "Teleport") {
        val slot = EquipmentType.SHIELD.id
        player.equipment[slot]?.let { actions.travel(player, it, slot, true) }
    }
    Dmm2Ids.ItemId.TELEPORT_CARD.local?.let { cards -> on_item_on_item(item1 = cards, item2 = starter.chronicle) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory.rawItems.getOrNull(first)?.id == starter.chronicle) first else second
        actions.charge(player, slot, starter.chronicle)
    } }
    listOf(starter.bow, starter.staff).forEach { weapon ->
        fun check(player: Player, item: gg.rsmod.game.model.item.Item?) {
            if (item?.id == weapon) player.message("Your weapon has ${gg.rsmod.plugins.content.starter.DeadmanStarterPack.charges(item)} charges.")
        }
        itemOption(weapon, "Check") { on_item_option(weapon, "Check") { check(player, player.inventory[player.getInteractingItemSlot()]) } }
        if (world.definitions.get(ItemDef::class.java, weapon).equipmentMenu.any { it.equals("Check", true) }) on_equipment_option(weapon, "Check") {
            check(player, player.equipment[EquipmentType.WEAPON.id])
        }
    }
}

listOfNotNull(Dmm2Ids.npc(8391), Dmm2Ids.npc(8390)).distinct().forEach { id ->
    on_npc_option(id, "Talk-to", lineOfSightDistance = 2) {
        val npc = player.getInteractingNpc()
        player.queue { NigelDialogue.talk(this, npc) }
    }
    on_npc_option(id, "Trade", lineOfSightDistance = 2) { Dmm2Interactions.openRewards(player, player.getInteractingNpc()) }
    // Transcript:Nigel 'Using blood money on Nigel' (all 3 / 2 / 1 / no items available), verbatim.
    Dmm2Ids.ItemId.BLOOD_MONEY.local?.let { money ->
        on_item_on_npc(item = money, npc = id) {
            val npc = player.getInteractingNpc()
            player.queue {
                val left = BloodMoney.Armour.values().filter { player.attr[it.unlocked] != 1 }
                fun label(a: BloodMoney.Armour) = when (a) {
                    BloodMoney.Armour.CHEST -> "Deadman's chest"; BloodMoney.Armour.LEGS -> "Deadman's legs"; BloodMoney.Armour.CAPE -> "Deadman's cape"
                }
                fun reward(a: BloodMoney.Armour) = when (a) {
                    BloodMoney.Armour.CHEST -> "Nigel rewards you with the Deadman's chest armour."
                    BloodMoney.Armour.LEGS -> "Nigel rewards you with the Deadman's leg armour."
                    BloodMoney.Armour.CAPE -> "Nigel rewards you with the Deadman's cape."
                }
                val armour = when (left.size) {
                    0 -> {
                        chatNpc("More blood money! I can only commend you on your prowess. Alas, I have no more armour to give to you at this time.", npc = npc.id)
                        return@queue
                    }
                    1 -> {
                        chatNpc("More blood money! Truly the blood lust is strong in you. Here, take this final item. You deserve it!", npc = npc.id)
                        left.single()
                    }
                    else -> {
                        chatNpc(if (left.size == 3) "Aha, blood money, the currency of a warrior! You must be rewarded! What would you like?"
                            else "More blood money! I commend you on your strength. What item would you like this time?", npc = npc.id)
                        left.getOrNull(options(*left.map(::label).toTypedArray(), title = "Select an Option") - 1) ?: return@queue
                    }
                }
                if (!Dmm2Interactions.near(player, npc)) return@queue
                armour.item.local?.let { item ->
                    if (BloodMoney.unlock(player, armour) == BloodMoney.Result.OK) itemMessageBox(reward(armour), item = item)
                }
            }
        }
    }
    NigelRewardStore.active?.let { store ->
        store.contract.skins.flatMap { it.acceptedBases + it.cosmetic }.distinct().forEach { item ->
            on_item_on_npc(item = item, npc = id) {
                val npc = player.getInteractingNpc(); val slot = player.getInteractingItemSlot()
                player.queue {
                    if (options("Yes.", "No.", title = "Add or remove the Deadman cosmetic?") == 1 && Dmm2Interactions.near(player, npc)) store.convert(player, slot, item)
                }
            }
        }
        // Transcript:Nigel 'Using Armageddon cape fabric / Annihilation weapon scroll on Nigel' (Dmm2ScrollText); teleport scrolls are read.
        store.contract.rewards.forEach { (reward, item) ->
            val unlockOption = Dmm2ScrollText.nigelOption(reward) ?: return@forEach
            on_item_on_npc(item = item, npc = id) {
                val npc = player.getInteractingNpc(); val slot = player.getInteractingItemSlot()
                player.queue {
                    if (options(unlockOption, "Cancel.", title = "This will consume the scroll.") == 1 && Dmm2Interactions.near(player, npc) &&
                        store.unlock(player, reward, slot, item) == NigelRewardStore.Result.OK) {
                        Dmm2ScrollText.nigelReply(reward)?.let { chatNpc(it, npc = npc.id) }
                    }
                }
            }
        }
    }
}

// Scrolls explicitly offer Read in the source cache. Fabric and blueprints retain their NPC routes.
NigelRewardStore.active?.let { store -> store.contract.rewards.forEach { (reward, item) ->
    if (reward in setOf(NigelRewardStore.Reward.CAPE_FABRIC, NigelRewardStore.Reward.BLUEPRINTS)) itemOption(item, "Read") {
        on_item_option(item, "Read") {
            player.queue { itemMessageBox(if (reward == NigelRewardStore.Reward.CAPE_FABRIC)
                "You can use this on Nigel in Lumbridge to unlock the Deadman: Armageddon ornament theme for your capes."
                else "You can hand this in to an Estate Agent to unlock the Wilderness theme for your player-owned house.", item = item) }
        }
    } else if (Dmm2ScrollText.weaponRead(reward) != null) itemOption(item, "Read") {
        // Transcript:<weapon scroll>: Read only explains; the scroll is used on Nigel.
        on_item_option(item, "Read") { player.queue { itemMessageBox(Dmm2ScrollText.weaponRead(reward)!!, item = item) } }
    } else if (reward.key != null) itemOption(item, "Read") {
        on_item_option(item, "Read") {
            val slot = player.getInteractingItemSlot()
            val text = Dmm2ScrollText.teleport(reward) ?: return@on_item_option
            player.queue {
                if (NigelRewardStore.unlocked(player, reward)) {
                    text.already?.let { itemMessageBox(it, item = item) }
                    return@queue
                }
                itemMessageBox(text.ask, item = item)
                if (options(text.option, "Cancel.", title = "This will consume the scroll.") == 1 &&
                    store.unlock(player, reward, slot, item) == NigelRewardStore.Result.OK) {
                    player.message(text.unlocked)
                    itemMessageBox(text.learned, item = item)
                }
            }
        }
    }
} }

listOfNotNull(Dmm2Ids.npc(6516), Dmm2Ids.npc(6485)).distinct().forEach { id ->
    on_npc_option(id, "Bank", lineOfSightDistance = 2) { player.openBank() }
    on_npc_option(id, "Deposit-box", lineOfSightDistance = 2) {
        if (!Dmm2Interactions.openSafe(player, player.getInteractingNpc()) && gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(player)) player.message(SafeDepositBox.SKULL_MESSAGE)
    }
    on_npc_option(id, "Talk-to", lineOfSightDistance = 2) {
        val npc = player.getInteractingNpc()
        player.queue {
            val seer = id == Dmm2Ids.npc(6485)
            chatNpc(if (seer) "Can I help you?" else "Good day, how may I help you?", npc = id)
            val choices = listOf("I'd like to access my bank account, please.", "I'd like to check my PIN settings.", "I'd like to collect items.") +
                if (seer) listOf("What's a Financial Seer?") else listOf("What is this place?", "What's a Financial Wizard?")
            when (val choice = options(*choices.toTypedArray())) {
                1 -> if (Dmm2Interactions.near(player, npc)) player.openBank()
                2 -> if (Dmm2Interactions.near(player, npc)) BankPin.manage(player)
                3 -> if (Dmm2Interactions.near(player, npc)) GrandExchangeInterface.openCollectionBox(player)
                4, 5 -> {
                    if (!seer && choice == 4) {
                        chatPlayer("What is this place?")
                        chatNpc("This is a branch of the Bank of Gielinor. We have branches in many towns.", npc = id)
                        chatPlayer("And what do you do?")
                        chatNpc("We will look after your items and money for you. Leave your valuables with us if you want to keep them safe.", npc = id)
                        return@queue
                    }
                    // Transcript:Financial_Seer / Transcript:Financial_Wizard: the player first repeats the chosen question (Claude 18-11).
                    chatPlayer(if (seer) "What's a Financial Seer?" else "What's a Financial Wizard?")
                    if (seer) chatNpc("In towns elsewhere in the world, there are bankers who look after your items. Among them are Financial Wizards, who are able to access your safe deposit box on Deadman Worlds.", npc = id)
                    chatNpc(if (seer) "Although we Fremenniks have little interest in magic, I can nevertheless access your safe deposit box on this Deadman World. Would you like to do that now?"
                        else "Ah, we're special bankers who can access your safe deposit box on Deadman Worlds. Would you like to do that now?", npc = id)
                    if (options("Yes, please.", "No, thank you.") == 1) Dmm2Interactions.openSafe(player, npc)
                }
            }
        }
    }
}

Dmm2Ids.ItemId.FORTUITY_EMPTY.local?.let { id -> itemOption(id, "Charge") {
    on_item_option(id, "Charge") {
        val slot = player.getInteractingItemSlot()
        player.queue {
            if (options("Yes.", "No.", title = "Charge for 10,000,000 coins?") == 1 &&
                DeadmanTrinkets.charge(player, slot, id, true) == DeadmanTrinkets.Result.OK) {
                itemMessageBox("You charge your trinket with 10,000,000 coins.", item = Dmm2Ids.ItemId.FORTUITY.local!!)
            }
        }
    }
} }

// Reuse the underlying charge economies for physical cosmetic items; no hidden conversion to a base item in inventory.
NigelRewardStore.active?.contract?.skins?.forEach { skin ->
    val id = skin.cosmetic
    val resource = when (skin.base) {
        Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD -> Items.ZULRAHS_SCALES
        in gg.rsmod.plugins.content.items.osrs.CrystalEquipment.INACTIVE_FOR,
        in gg.rsmod.plugins.content.items.osrs.CrystalEquipment.CHARGED_FOR -> Items.CRYSTAL_SHARD
        else -> null
    }
    if (resource != null) on_item_on_item(item1 = resource, item2 = id) {
        val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
        val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
        val slot = if (player.inventory[first]?.id == id) first else second
        DeadmanCosmetics.charge(player, slot, id, resource)
    }
    if (skin.base == Items.TOXIC_STAFF_OF_THE_DEAD) itemOption(id, "Uncharge") {
        on_item_option(id, "Uncharge") { DeadmanCosmetics.unchargeToxic(player, player.getInteractingItemSlot(), id) }
    }
}
Dmm2Ids.ItemId.FORTUITY.local?.let { id -> itemOption(id, "Uncharge") {
    on_item_option(id, "Uncharge") { DeadmanTrinkets.charge(player, player.getInteractingItemSlot(), id, false) }
} }
Dmm2Ids.ItemId.AVARICE.local?.let { id ->
    itemOption(id, "Note/Unnote") { on_item_option(id, "Note/Unnote") {
        DeadmanTrinkets.settings(player, true, player.attr[DeadmanTrinkets.NOTE_LOOT] == 0, player.attr[DeadmanTrinkets.LOOT_MIN_VALUE] ?: 0)
    } }
    itemOption(id, "Value") { on_item_option(id, "Value") {
        player.queue { val value = inputInt("Minimum loot value:"); DeadmanTrinkets.settings(player, true, player.attr[DeadmanTrinkets.NOTE_LOOT] != 0, value) }
    } }
}
Dmm2Ids.ItemId.UNDEAD.local?.let { id -> UndeadThralls.Choice.values().forEach { choice ->
    val option = when (choice) { UndeadThralls.Choice.ZUK -> "Summon Lil Zuk"; UndeadThralls.Choice.KAL -> "Summon Lil Kal"; else -> "Summon Lil Wisp" }
    itemOption(id, option) { on_item_option(id, option) { UndeadThralls.summon(player, choice) } }
} }
Dmm2Ids.ItemId.VENGEANCE.local?.let { id -> itemOption(id, "Cast") {
    on_item_option(id, "Cast") {
        if (DeadmanTrinkets.vengeance(player) == DeadmanTrinkets.Result.OK) {
            player.animate(4410); player.graphic(726, 96); player.playSound(Sfx.LUNAR_VENGENCE)
        }
    }
} }
gg.rsmod.plugins.content.items.osrs.AncientCape.assets.fabric?.let { fabric ->
    listOf(29613, 29615, 29617).mapNotNull { Dmm2Ids.item(it) }.forEach { cape ->
        on_item_on_item(item1 = fabric, item2 = cape) {
            val slot = player.inventory.getItemIndex(cape, skipAttrItems = false)
            player.queue {
                if (options("Yes, I wish to proceed.", "No, I do not wish to proceed.", title = "This process is irreversible. Are you sure you wish to proceed?") == 1)
                    NigelRewardStore.active?.craftAncientCape(player, slot, cape)
            }
        }
    }
}
Dmm2Ids.ItemId.FIRE_RUBY.local?.let { id ->
    on_item_on_obj(obj = Altar.FIRE.altar, item = id) {
        fun requirements(result: FireAmulet.Result) {
            if (result in setOf(FireAmulet.Result.LEVEL, FireAmulet.Result.INGREDIENTS)) player.message(FireAmulet.REQUIREMENTS)
        }
        val ready = FireAmulet.check(player)
        if (ready != FireAmulet.Result.OK) {
            requirements(ready)
            return@on_item_on_obj
        }
        val tile = Tile(player.tile)
        player.queue(TaskPriority.WEAK) {
            // Claude 17-11: our own 667 seq 721 + spotanim 116 = OSRS 116 byte-identical (model 3103, seq 679); the imported
            // copies (17752/3470) drew the effect green in the test client, so the native ids are used ('fix first, import where needed').
            player.animate(Anims.LVL_3_ENCHANT)
            player.graphic(Gfx.LVL_3_ENCHANT, 92)
            // Same named ruby-amulet enchant sound as the visually identical action. See PHASE5-SUPPLEMENT: inference, not an audio capture.
            player.playSound(Sfx.ENCHANT_RUBY_AMULET)
            wait(FireAmulet.CRAFT_TICKS)
            if (player.tile == tile && !player.isDead()) requirements(FireAmulet.craft(player))
        }
    }
}

fun clearSurroundings(player: Player): Boolean =
    Direction.values().filter { it != Direction.NONE }.all { player.world.collision.canStep(player.tile, it) } &&
        player.world.players.none { it !== player && it.tile.isWithinRadius(player.tile, 1) }

Dmm2Ids.ItemId.ADVANCED_WEAPONRY.local?.let { id -> itemOption(id, "Open") {
    on_item_option(id, "Open") {
        val slot = player.getInteractingItemSlot()
        if (!clearSurroundings(player)) {
            player.message("You must have nothing surrounding you to open this trinket.")
            return@on_item_option
        }
        // Claude 18-11 (lead, advice): the OSRS opening effect (scenery 49564/49565 + objectanim 10426, player pose, duration) is not
        // proven yet (PHASE7-ADVANCED-PLAN). Until it is, the trinket still works - reward and message without an invented animation -
        // instead of doing nothing at all. With a proven appearance the effect plays first.
        val appearance = DeadmanTrinkets.advancedAppearance
        val tile = Tile(player.tile)
        player.queue(TaskPriority.WEAK) {
            if (appearance != null) {
                player.faceTile(tile.transform(0, 1)); player.animate(appearance.animation)
                wait(appearance.ticks)
            }
            if (player.tile == tile && !player.isDead() &&
                DeadmanTrinkets.openAdvanced(player, slot, id, clearSurroundings(player)) { bound -> world.random.nextInt(bound) } == DeadmanTrinkets.Result.OK) {
                val reward = player.inventory[slot] ?: return@queue
                player.message("Your trinket gave you a ${reward.getDef(world.definitions).name}!")
            }
        }
    }
} }
Dmm2Ids.ItemId.SCYTHE_EMPTY.local?.let { empty -> Dmm2Ids.ItemId.SCYTHE.local?.let { charged ->
    listOf(empty, charged).forEach { id ->
        on_item_on_item(item1 = Items.BLOOD_RUNE, item2 = id) {
            val first = player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_on_item
            val second = player.attr[OTHER_ITEM_SLOT_ATTR] ?: return@on_item_on_item
            val slot = if (player.inventory[first]?.id == id) first else second
            val held = player.inventory.rawItems.getOrNull(slot) ?: return@on_item_on_item
            val batches = minOf(player.inventory.getItemCount(Items.BLOOD_RUNE) / 300, (CorruptedWeapons.SCYTHE_CAPACITY - CorruptedWeapons.scytheCharges(held)) / 100)
            CorruptedWeapons.chargeScythe(player, slot, id, batches)
        }
        itemOption(id, "Uncharge") { on_item_option(id, "Uncharge") {
            val slot = player.getInteractingItemSlot()
            player.queue { if (options("Yes.", "No.", title = "This will destroy the remaining charges.") == 1) CorruptedWeapons.unchargeScythe(player, slot, id) }
        } }
        itemOption(id, "Check") { on_item_option(id, "Check") {
            val item = player.inventory[player.getInteractingItemSlot()] ?: return@on_item_option
            player.message("Your weapon has ${CorruptedWeapons.scytheCharges(item)} charges.")
        } }
    }
} }

// Claude 18-11: the Reward Store footer follows the Deadman Points balance while the store is open (Dmm2RewardUi.tickPoints).
on_timer(Dmm2RewardUi.POINTS_TIMER) { Dmm2RewardUi.tickPoints(player) }
