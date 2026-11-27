package gg.rsmod.plugins.content.skills.construction.mahogany

import gg.rsmod.game.model.attr.INTERACTING_ITEM_SLOT
import gg.rsmod.plugins.content.mechanics.shops.PointCurrency

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C7): Mahogany Homes - npcs, contract dialogues, furniture options, reward shop, plank sack and
 * supply crate. The logic lives in [MahoganyHomes].
 */

MahoganyHomes.CONTRACTORS.forEach { spawn_npc(npc = it.npc, x = it.tile.x, z = it.tile.z, height = it.tile.height, walkRadius = 2) }
MahoganyHomes.HOMES.forEach { spawn_npc(npc = it.npc, x = it.npcTile.x, z = it.npcTile.z, height = it.npcTile.height, walkRadius = 2) }

on_world_init { MahoganyHomes.spawnWorld(world) }

/** Carpenter points; a bought log item goes in the Collection log (OSRS records Mahogany Homes rewards when bought). */
object CarpenterPoints : PointCurrency("carpenter point", "carpenter points", MahoganyHomes.CARPENTER_POINTS) {
    override fun sellToPlayer(
        p: gg.rsmod.game.model.entity.Player,
        shop: gg.rsmod.game.model.shop.Shop,
        slot: Int,
        amt: Int,
    ) {
        val id = shop.items[slot]?.item ?: return
        val before = p.inventory.getItemCount(id)
        super.sellToPlayer(p, shop, slot, amt)
        val bought = p.inventory.getItemCount(id) - before
        if (bought > 0 && id in gg.rsmod.plugins.content.quests.tab.CollectionLogPages.MAHOGANY_HOMES_ITEMS) {
            gg.rsmod.plugins.content.quests.tab.CollectionLog.record(p, gg.rsmod.plugins.content.quests.tab.CollectionLogPages.MAHOGANY_HOMES, listOf(id to bought))
        }
    }
}

create_shop(
    MahoganyHomes.SHOP,
    currency = CarpenterPoints,
    purchasePolicy = PurchasePolicy.BUY_NONE,
    containsSamples = false,
) {
    items[0] = ShopItem(MahoganyHomes.SUPPLY_CRATE, amount = Int.MAX_VALUE, sellPrice = 25)
    items[1] = ShopItem(MahoganyHomes.AMYS_SAW, amount = Int.MAX_VALUE, sellPrice = 500)
    items[2] = ShopItem(MahoganyHomes.PLANK_SACK, amount = Int.MAX_VALUE, sellPrice = 350)
    items[3] = ShopItem(MahoganyHomes.HELMET, amount = Int.MAX_VALUE, sellPrice = 400)
    items[4] = ShopItem(MahoganyHomes.SHIRT, amount = Int.MAX_VALUE, sellPrice = 800)
    items[5] = ShopItem(MahoganyHomes.TROUSERS, amount = Int.MAX_VALUE, sellPrice = 600)
    items[6] = ShopItem(MahoganyHomes.BOOTS, amount = Int.MAX_VALUE, sellPrice = 200)
}

fun openRewards(p: gg.rsmod.game.model.entity.Player) {
    p.openShop(MahoganyHomes.SHOP)
}

/** Contract from a contractor ([lastTier]: the "Last-tier contract" option). */
suspend fun contractFrom(
    task: QueueTask,
    who: MahoganyHomes.Contractor,
    lastTier: Boolean,
) {
    val player = task.player
    val current = MahoganyHomes.contract(player)
    if (current != null) {
        task.chatNpc(
            "You already have a contract: ${MahoganyHomes.describe(current)} in ${current.homeDef.city} is waiting for some " +
                "${MahoganyHomes.TIER_NAMES[current.tier].lowercase()} work.",
        )
        return
    }
    if (who.name != "Amy" && (player.attr[MahoganyHomes.CONTRACTS_DONE] ?: 0) == 0 && player.attr[MahoganyHomes.LAST_TIER] == null) {
        task.chatNpc("You'll want to speak to Amy in Falador first - she runs Mahogany Homes and hands out the first job herself.")
        return
    }
    val tiers = MahoganyHomes.tiersFor(player)
    val tier =
        if (lastTier) {
            val last = player.attr[MahoganyHomes.LAST_TIER]
            if (last == null || last !in tiers) {
                task.chatNpc("I don't have a record of the last kind of contract you took on. Let's pick one.")
                pickTier(task, tiers) ?: return
            } else {
                last
            }
        } else {
            pickTier(task, tiers) ?: return
        }
    val home = MahoganyHomes.start(player, tier)
    task.chatNpc(
        "Please could you go see ${home.name}, ${home.where}? They need some ${MahoganyHomes.TIER_NAMES[tier].lowercase()} work " +
            "done - bring ${MahoganyHomes.planksName(tier)}, a hammer, a saw and maybe a steel bar or two.",
    )
    player.message("New contract: ${home.name} in ${home.city}, ${home.where} (${MahoganyHomes.TIER_NAMES[tier]}).")
}

suspend fun pickTier(
    task: QueueTask,
    tiers: List<Int>,
): Int? {
    val labels = tiers.map { "${MahoganyHomes.TIER_NAMES[it]} (${MahoganyHomes.planksName(it)})" }
    if (labels.size == 1) return tiers[0]
    val picked = task.options(*labels.toTypedArray(), title = "What kind of contract would you like?") - 1
    return tiers.getOrNull(picked)
}

suspend fun contractorTalk(
    task: QueueTask,
    who: MahoganyHomes.Contractor,
) {
    task.chatNpc("Hello! Welcome to Mahogany Homes - we take care of people's furniture all over the land. How can I help?")
    val opts = mutableListOf("Could I have a contract?", "What's Mahogany Homes?")
    if (who.name == "Amy") opts += "Could I see the rewards?"
    opts += "Nothing, thanks."
    when (opts.getOrNull(task.options(*opts.toTypedArray()) - 1)) {
        "Could I have a contract?" -> contractFrom(task, who, lastTier = false)
        "What's Mahogany Homes?" ->
            task.chatNpc(
                "We fix and replace furniture in homes in Falador, Varrock and Ardougne. Take a contract, do the work, then speak to " +
                    "the owner - you'll earn Construction experience and carpenter points to spend in Amy's shop.",
            )
        "Could I see the rewards?" -> openRewards(task.player)
    }
}

MahoganyHomes.CONTRACTORS.forEach { who ->
    on_npc_option(npc = who.npc, option = "talk-to") { player.queue { contractorTalk(this, who) } }
    on_npc_option(npc = who.npc, option = "contract") { player.queue { contractFrom(this, who, lastTier = false) } }
    on_npc_option(npc = who.npc, option = "last-tier contract") { player.queue { contractFrom(this, who, lastTier = true) } }
}
on_npc_option(npc = MahoganyHomes.AMY.npc, option = "rewards") { openRewards(player) }

MahoganyHomes.HOMES.forEachIndexed { i, home ->
    on_npc_option(npc = home.npc, option = "talk-to") { player.queue { MahoganyHomes.ownerTalk(this, i) } }
}

listOf("remove" to MahoganyHomesData.REMOVE_IDS, "build" to MahoganyHomesData.BUILD_IDS, "repair" to MahoganyHomesData.REPAIR_IDS).forEach { (option, ids) ->
    ids.forEach { id ->
        on_obj_option(obj = id, option = option) {
            val obj = player.getInteractingGameObj()
            player.queue { MahoganyHomes.act(this, obj, option) }
        }
    }
}

on_login { if (MahoganyHomes.contract(player) != null) player.timers[MahoganyHomes.SYNC_TIMER] = 3 }
on_timer(MahoganyHomes.SYNC_TIMER) { MahoganyHomes.tick(player) }

on_item_option(item = MahoganyHomes.PLANK_SACK, option = "fill") { MahoganyHomes.fillSack(player) }
on_item_option(item = MahoganyHomes.PLANK_SACK, option = "empty") { MahoganyHomes.emptySack(player) }
on_item_option(item = MahoganyHomes.PLANK_SACK, option = "check") { MahoganyHomes.check(player) }
MahoganyHomes.PLANKS.forEach { plank -> on_item_on_item(item1 = plank, item2 = MahoganyHomes.PLANK_SACK) { MahoganyHomes.fillSack(player) } }

on_item_option(item = MahoganyHomes.SUPPLY_CRATE, option = "open") {
    MahoganyHomes.openCrate(player, player.attr[INTERACTING_ITEM_SLOT] ?: return@on_item_option)
}
on_item_option(item = MahoganyHomes.SUPPLY_CRATE, option = "open-all") {
    while (player.inventory.contains(MahoganyHomes.SUPPLY_CRATE)) {
        val slot = player.inventory.getItemIndex(MahoganyHomes.SUPPLY_CRATE, skipAttrItems = false)
        if (!MahoganyHomes.openCrate(player, slot)) break
    }
}

// Test command (agent bot tests): `mhinfo <name>` = the contract and per task spot: varbit, tile, state, the id the player sees.
gg.rsmod.game.CommandServerCommands.handlers["mhinfo"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    val c = p?.let { MahoganyHomes.contract(it) }
    if (p == null) {
        "usage: mhinfo <name> [points <n>]"
    } else if (a.getOrNull(1) == "points") {
        p.attr[MahoganyHomes.CARPENTER_POINTS] = a.getOrNull(2)?.toIntOrNull() ?: 0
        "points ${p.attr[MahoganyHomes.CARPENTER_POINTS]}"
    } else if (c == null) {
        "no contract (points ${p.attr[MahoganyHomes.CARPENTER_POINTS] ?: 0}, done ${p.attr[MahoganyHomes.CONTRACTS_DONE] ?: 0})"
    } else {
        "contract ${c.encode()} home=${c.homeDef.name}\n" +
            c.states.entries.joinToString("\n") { (vb, st) ->
                val s = c.homeDef.spot(vb)
                "spot $vb ${s.tile.x} ${s.tile.z} ${s.tile.height} state=$st shown=${s.variants.getOrNull(st)} base=${s.base}"
            }
    }
}

// Test command: `invof <name>` = the inventory as id x amount (agent bot tests).
gg.rsmod.game.CommandServerCommands.handlers["invof"] = { w, a ->
    val p = w.getPlayerForName(a.getOrNull(0)?.replace("_", " ") ?: "")
    p?.inventory?.rawItems?.withIndex()?.filter { it.value != null }?.joinToString(" ") { "${it.index}:${it.value!!.id}x${it.value!!.amount}" } ?: "usage: invof <name>"
}
