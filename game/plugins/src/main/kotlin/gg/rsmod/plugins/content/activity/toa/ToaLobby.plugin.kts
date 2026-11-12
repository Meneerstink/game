package gg.rsmod.plugins.content.activity.toa

// N604 (12-11): Inspect opens the OSRS Party List (772, Wiki Grouping Obelisk: "showing the available parties in the current world");
// Make Party / View Party / Apply / Accept / Kick / Leave / Disband and the leader's mode choice live in the party windows (ui/ToaParty*Ui).
on_obj_option(ToaLocs.GROUPING_OBELISK, "Inspect") {
    gg.rsmod.plugins.content.activity.toa.ui.ToaPartyListUi.open(player)
}

on_obj_option(ToaLocs.TOMB, "Enter") {
    if (ToaMap.inLobby(player.tile) && ToaLobby.canEnter(player)) ToaLobby.of(world).requestStart(player)
}

on_logout {
    ToaLobby.of(world).logout(player)
}

on_login { ToaLobby.of(world).login(player) }

// The Rewards Niche: toa_rewards_chest_lobby 46224 (varbit 14319) -> 46083 'False Door' closed / 46082 'Rewards Niche' open, both
// 'Claim'. The 667 import drops the multiloc table and the look is per player in OSRS, so ToaMultilocs places 46083 for everybody and
// both variants open the unclaimed rewards.
listOfNotNull(ToaIds.loc(46082), ToaIds.loc(46083)).forEach { id ->
    on_obj_option(id, "Claim") { if (ToaRewards.hasUnclaimed(player)) ToaUi.showRewards(player) }
}

// N462 Icthlarin's shroud chest (OSRS loc 46080 toa_lobby_cape_chest, "Search"; across from the Grouping Obelisk). OSRS Wiki Icthlarin's
// shroud: tiers 1-5 at 100 / 500 / 1,000 / 1,500 / 2,000 Normal/Expert completions; Transcript:Chest (Icthlarin's shroud): "Take
// Icthlarin's shroud (tier N)." -> "You take a mysterious shroud from the chest.". ONZEKER (no source): nothing unlocked, every unlocked
// tier already owned or a full inventory - the chest then just offers nothing / gives nothing.
ToaIds.loc(46080)?.let { chest ->
    val tiers = listOf(100 to 27257, 500 to 27259, 1_000 to 27261, 1_500 to 27263, 2_000 to 27265)
    on_obj_option(chest, "Search") {
        val done = player.attr[ToaChest.COMPLETIONS_NORMAL_EXPERT] ?: 0
        val offer = tiers.mapIndexedNotNull { index, (needed, osrs) ->
            val local = ToaIds.item(osrs) ?: return@mapIndexedNotNull null
            val held = player.inventory.contains(local) || player.bank.contains(local) || player.equipment.contains(local)
            if (done >= needed && !held) (index + 1) to local else null
        }
        // Icthlarin's hood (tier 5) (Wiki, OSRS 27267): from this chest after 2,000 Normal/Expert raids. ONZEKER: the transcript has no hood
        // line - offered as "Take Icthlarin's hood." in the shrouds' pattern.
        val hood = ToaIds.item(27267)?.takeIf { id -> done >= 2_000 && !player.inventory.contains(id) && !player.bank.contains(id) && !player.equipment.contains(id) }
        // A dialogue holds five lines: with all five shrouds offered the hood comes once one of them is taken.
        val lines = (offer.map { "Take Icthlarin's shroud (tier ${it.first})." } + listOfNotNull(hood?.let { "Take Icthlarin's hood." })).take(5)
        val ids = (offer.map { it.second } + listOfNotNull(hood)).take(5)
        if (ids.isEmpty()) return@on_obj_option
        player.queue {
            val picked = ids.getOrNull(options(*lines.toTypedArray()) - 1) ?: return@queue
            if (player.inventory.freeSlotCount == 0) return@queue
            player.inventory.add(picked)
            if (picked == hood) return@queue
            gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, gg.rsmod.plugins.content.quests.tab.CollectionLogPages.TOA, listOf(picked to 1))
            messageBox("You take a mysterious shroud from the chest.")
        }
    }
    // Icthlarin's shroud (tier 1)?oldid=15191015 l.23: 'the right-click "Change" option to alter the cape's appearance to other tiers,
    // providing that they have met the completion count required for them'. ONZEKER: the choice menu's wording is not sourced - the
    // tiers are offered by their item names.
    val shrouds = tiers.mapIndexedNotNull { index, (needed, osrs) -> ToaIds.item(osrs)?.let { Triple(index + 1, needed, it) } }
    shrouds.forEach { (_, _, held) ->
        on_item_option(item = held, option = "change") {
            val slot = player.getInteractingItemSlot()
            val done = player.attr[ToaChest.COMPLETIONS_NORMAL_EXPERT] ?: 0
            val choices = shrouds.filter { (_, needed, id) -> done >= needed && id != held }
            if (choices.isEmpty()) return@on_item_option
            player.queue {
                val names = choices.map { (tier, _, _) -> "Icthlarin's shroud (tier $tier)" }
                val pick = choices.getOrNull(options(*names.toTypedArray()) - 1) ?: return@queue
                if (player.inventory[slot]?.id == held) player.inventory[slot] = gg.rsmod.game.model.item.Item(pick.third)
            }
        }
    }
}