package gg.rsmod.plugins.content.npcs.bankers

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeCollectors
import gg.rsmod.plugins.content.mechanics.exchange.GrandExchangeInterface

/**
 * RCV-012.B16: `Collect` on every bank npc opens the Grand Exchange collection box (Novite `NPCHandler.handleOption5`). The npc set
 * comes from the cache (definitions carrying both `Bank` and `Collect`, see [GrandExchangeCollectors]), not a hand list.
 */
val bankCollectNpcs =
    (0 until world.definitions.getCount(NpcDef::class.java)).filter { id ->
        val def = world.definitions.getNullable(NpcDef::class.java, id) ?: return@filter false
        GrandExchangeCollectors.isBankCollectNpc(def.options)
    }

bankCollectNpcs.forEach { npc ->
    on_npc_option(npc, option = "Collect", lineOfSightDistance = 2) {
        GrandExchangeInterface.openCollectionBox(player)
    }
}
