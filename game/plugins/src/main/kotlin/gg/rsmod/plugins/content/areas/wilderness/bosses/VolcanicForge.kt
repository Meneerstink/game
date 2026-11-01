package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.skills.agility.exactMove

/**
 * N522 bundle 2: the OSRS "Volcanic Forge" (OSRS loc 26755, imported as [FORGE] with OsrsRegionImportTool batch volcanic-forge,
 * tx-20261101-223411) where the three shards of the Wilderness bosses become a ward. OSRS Wiki "Volcanic Forge" / "Odium ward" /
 * "Malediction ward" (2026-11-01): use any shard on the forge with all three of its kind; no level, no tool. OSRS map square 13373
 * places two forges at (3366,3937) and (3367,3937), rotation 2; the rev-667 surface is kept, so the server places them.
 * Sequence, texts and timing: Kronos-184 activities/wilderness/Volcano.java - dialogue "You drop the three shield shards into the
 * mouth of the volcanic<br>chamber of fire.", anim 4411 (RuneLite QUEST_LUNAR_PUSHING_MAGIC_ANIMATION), 1 tick, pushed back one tile
 * south facing north (client cycles 45..126), 1 tick, anim 734 (HUMAN_BLOWN_START, delay 5), 3 ticks, shards -> ward, then the OSRS
 * Wiki message. Both seqs exist with the same ids in the rev-667 cache.
 * Not ported: the gold ring trivia (a goblin, OSRS npc 3028 / anim 6184, not imported).
 */
object VolcanicForge {
    const val FORGE = 62958 // OSRS 26755

    val TILES = listOf(Tile(3366, 3937), Tile(3367, 3937))
    const val ROTATION = 2

    const val ANIM_PUSH = 4411
    const val ANIM_BLOWN = 734

    const val TEXT_DROP = "You drop the three shield shards into the mouth of the volcanic<br>chamber of fire."
    const val TEXT_DONE_1 = "You forge the shield pieces together in the chambers of fire and are blown back by"
    const val TEXT_DONE_2 = "the intense heat."

    enum class Ward(val shards: List<Int>, val ward: Int, val shardName: String) {
        MALEDICTION(listOf(Items.MALEDICTION_SHARD_1, Items.MALEDICTION_SHARD_2, Items.MALEDICTION_SHARD_3), Items.MALEDICTION_WARD, "Malediction"),
        ODIUM(listOf(Items.ODIUM_SHARD_1, Items.ODIUM_SHARD_2, Items.ODIUM_SHARD_3), Items.ODIUM_WARD, "Odium"),
    }

    val SHARDS: Map<Int, Ward> = Ward.values().flatMap { w -> w.shards.map { it to w } }.toMap()

    /** All three shards of [ward] carried (Kronos: "You need all <name> shards to forge a shield."). */
    fun hasAll(player: Player, ward: Ward): Boolean = ward.shards.all { player.inventory.contains(it) }

    private const val CHATBOX = 210
    private const val CHATBOX_TEXT = 1
    private const val CHATBOX_CONTINUE = 2

    fun forge(player: Player, ward: Ward) {
        if (!hasAll(player, ward)) {
            player.filterableMessage("You need all ${ward.shardName} shards to forge a shield.")
            return
        }
        player.lockingQueue(lockState = LockState.FULL) {
            player.openInterface(interfaceId = CHATBOX, parent = 752, child = 13)
            player.setComponentHidden(CHATBOX, CHATBOX_CONTINUE, hidden = true)
            player.setComponentText(CHATBOX, CHATBOX_TEXT, TEXT_DROP)
            player.animate(ANIM_PUSH)
            wait(1)
            exactMove(player, player.tile.transform(0, -1), delay = 126, direction = Direction.NORTH, startDelay = 45)
            wait(1)
            player.animate(ANIM_BLOWN, delay = 5)
            wait(3)
            // Re-checked after the waits: the shards cannot leave a locked inventory, but never hand out a ward for missing shards.
            if (hasAll(player, ward) && ward.shards.all { player.inventory.remove(it).hasSucceeded() }) {
                player.inventory.add(ward.ward)
                player.filterableMessage(TEXT_DONE_1)
                player.filterableMessage(TEXT_DONE_2)
            }
            player.setComponentHidden(CHATBOX, CHATBOX_CONTINUE, hidden = false)
            player.closeComponent(parent = 752, child = 13)
        }
    }
}
