package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.content.mechanics.pvp.SkullyRoster

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
            Post(DONATOR_STORE, StoreCatalogue.Shop.DONATOR, Tile(3168, 3495, 0)),
            Post(DEADMAN_STORE, StoreCatalogue.Shop.DEADMAN, Tile(3160, 3488, 0)),
            Post(Npcs.XUAN, StoreCatalogue.Shop.LOYALTY, Tile(3165, 3486, 0)),
        )

    /** A Grand Exchange floor tile players stand on (the Skully site's customer-side anchor). */
    val EXCHANGE_FLOOR = Tile(3165, 3487, 0)

    fun shopFor(npcId: Int): StoreCatalogue.Shop? = POSTS.firstOrNull { it.npcId == npcId }?.shop

    fun spawnAll(world: World): String {
        // Runs right after SkullyRoster.spawnAll: his chest already clips its tile (so it is not reachable) and every
        // standing npc's tile is taken.
        val reachable = SkullyRoster.reachableFrom(world, EXCHANGE_FLOOR)
        val taken = HashSet<Tile>()
        world.npcs.forEach { if (it.tile.getDistance(EXCHANGE_FLOOR) <= SkullyRoster.SEARCH_RADIUS + 2) taken += Tile(it.tile) }
        val lines = ArrayList<String>()
        POSTS.forEach { post ->
            val tile =
                if (post.tile in reachable && post.tile !in taken) {
                    post.tile
                } else {
                    reachable.filter { it !in taken && it != EXCHANGE_FLOOR }.minByOrNull { it.getDistance(post.tile) }
                }
            if (tile == null) {
                lines += "${post.shop.title}: NO reachable tile near ${post.tile.x},${post.tile.z}"
                return@forEach
            }
            taken += tile
            val npc =
                Npc(post.npcId, tile, world).also {
                    it.respawnOverride = true
                    it.static = true
                    it.walkRadius = 0
                }
            world.spawn(npc)
            npc.setCombatLevel(0)
            SkullyRoster.facing(world, tile, EXCHANGE_FLOOR)?.let {
                npc.setSpawnFacing(Direction.between(tile, it))
                npc.faceTile(it)
            }
            lines += "${post.shop.title}: npc ${post.npcId} at ${tile.x},${tile.z}" + if (tile != post.tile) " (snapped from ${post.tile.x},${post.tile.z})" else ""
        }
        return "StoreNpcs: " + lines.joinToString("; ")
    }
}
