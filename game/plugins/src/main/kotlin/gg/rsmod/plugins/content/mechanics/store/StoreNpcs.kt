package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs

/**
 * The three 78 Store npcs at the Grand Exchange (owner, RSPS_MASTERPLAN_ACTUEEL.md: "Store NPCs: 3168,3495 Donator /
 * 3160,3488 Deadman / 3165,3486 Loyalty"; owner 2026-09-19: "i dont see any Npcs in the grand exchange with the 3 shops").
 *
 * - [DONATOR_STORE] 14436 "Donator Store": clone of OSRS Sigmund The Merchant 3894 (Talk-to, Trade).
 * - [DEADMAN_STORE] 14437 "Deadman Store": clone of OSRS Emblem Trader 308 (Talk-to, Rewards, Skull).
 * - [Npcs.XUAN] 13727: the 667 loyalty programme npc, which already opens the Loyalty tab.
 * (`OsrsNpcImportTool` clone batch `store-npcs`, tx-20260919-154310.)
 *
 * Each npc stands on its owner tile when that tile is walkable, reachable from the Grand Exchange floor and not taken
 * by Skully or his chest; otherwise on the nearest such tile (reported in the boot line). It faces the open side
 * towards the exchange floor.
 */
object StoreNpcs {
    const val DONATOR_STORE = 14436
    const val DEADMAN_STORE = 14437

    class Post(
        val npcId: Int,
        val shop: StoreCatalogue.Shop,
        val tile: Tile,
    )

    val POSTS: List<Post> =
        listOf(
            // 2026-09-25: inside the Royal Hall (GeHomeHall.kt), south row east of the entrance, facing north.
            Post(DONATOR_STORE, StoreCatalogue.Shop.DONATOR, Tile(3168, 3486, 0)),
            Post(DEADMAN_STORE, StoreCatalogue.Shop.DEADMAN, Tile(3169, 3486, 0)),
            Post(Npcs.XUAN, StoreCatalogue.Shop.LOYALTY, Tile(3170, 3486, 0)),
        )

    /** A Grand Exchange floor tile players stand on (the Skully site's customer-side anchor). */
    val EXCHANGE_FLOOR = Tile(3165, 3487, 0)

    fun shopFor(npcId: Int): StoreCatalogue.Shop? = POSTS.firstOrNull { it.npcId == npcId }?.shop

    fun spawnAll(world: World): String {
        // The posts are fixed stalls inside the home hall, so each keeper stands exactly on its post and faces into
        // the hall (north); the old snap-to-the-nearest-reachable-exchange-tile search belonged to the open GE floor.
        val lines = ArrayList<String>()
        POSTS.forEach { post ->
            val npc =
                Npc(post.npcId, post.tile, world).also {
                    it.respawnOverride = true
                    it.static = true
                    it.walkRadius = 0
                }
            world.spawn(npc)
            npc.setCombatLevel(0)
            npc.setSpawnFacing(Direction.NORTH)
            npc.faceTile(post.tile.step(Direction.NORTH))
            lines += "${post.shop.title}: npc ${post.npcId} at ${post.tile.x},${post.tile.z}"
        }
        return "StoreNpcs: " + lines.joinToString("; ")
    }
}
