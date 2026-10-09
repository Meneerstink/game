package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.model.Tile

/**
 * The three 78 Store npcs at the Grand Exchange.
 *
 * - [DONATOR_STORE] 14436 "Donator Store": clone of OSRS Sigmund The Merchant 3894 (Talk-to, Trade).
 * - [DEADMAN_STORE] 14437 "Deadman Store": clone of OSRS Emblem Trader 308 (Talk-to, Rewards, Skull).
 * - `Npcs.XUAN` 13727: the 667 loyalty programme npc - left the hall on 2026-10-09 (owner: "Xuan weg"); the Loyalty Shop is a
 *   tab of the same 78 Store window.
 * (`OsrsNpcImportTool` clone batch `store-npcs`, tx-20260919-154310.)
 *
 * Owner 2026-09-27: they stand in the Royal Hall's "stores" corner (north-east of the ground floor), posted once by
 * `data/world_edits.json` like every hall npc (GeHomeHall: one source per npc); this object no longer spawns them.
 */
object StoreNpcs {
    const val DONATOR_STORE = 14436
    const val DEADMAN_STORE = 14437

    /** A Grand Exchange floor tile players stand on (the Skully site's customer-side anchor). */
    val EXCHANGE_FLOOR = Tile(3165, 3487, 0)
}