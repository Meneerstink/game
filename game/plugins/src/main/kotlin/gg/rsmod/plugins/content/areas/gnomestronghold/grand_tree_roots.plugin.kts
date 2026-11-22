package gg.rsmod.plugins.content.areas.gnomestronghold

import gg.rsmod.game.message.impl.LocAnimMessage
import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.quests.QuestStatus
import gg.rsmod.plugins.content.quests.foundation.bracket.TheGrandTree
import gg.rsmod.plugins.content.skills.agility.darkanForceMove
import gg.rsmod.plugins.content.skills.agility.obstacle
import gg.rsmod.plugins.content.skills.agility.shortcut
import gg.rsmod.plugins.content.skills.agility.walkToTile

/*
 * PROJECT48 O16: the roots 2451 that close off the Grand Tree mine (north part of the Grand Tree Tunnels, 2466,9904).
 * OSRS Wiki 'Grand Tree mine' / 'Grand Tree Tunnels': "Players can enter the mine after the quest by pushing the roots that block
 * access from the central area. No axe or other equipment is required to push the roots aside."
 * Movement, animations and sound: 2009scape GrandTreeListeners (on 2451 'push', rev 530): only with The Grand Tree done; a forced
 * move of 2 tiles north from 2467/2468,9903 (or 2 south from the mine side), client cycles 25/60, walk animation 819, push
 * animation 2572, the roots animate 452 and sound 2316 (tanglevine appear). Without the quest the donor does nothing: here the
 * engine's own "Nothing interesting happens." so the click is not silent.
 */
private val ROOTS = 2451
private val ROOTS_Z = 9904

shortcut(ROOTS, "Push") {
    if (!QuestStatus.isCompleted(player, TheGrandTree)) {
        player.message("Nothing interesting happens.")
        return@shortcut
    }
    val roots = player.getInteractingGameObj()
    obstacle {
        val x = player.tile.x.coerceIn(2467, 2468)
        val fromSouth = player.tile.z < ROOTS_Z
        val front = Tile(x, if (fromSouth) ROOTS_Z - 1 else ROOTS_Z + 1, 0)
        val landing = Tile(x, if (fromSouth) ROOTS_Z + 1 else ROOTS_Z - 1, 0)
        walkToTile(player, front)
        player.animate(2572)
        player.playSound(Sfx.TANGLEVINE_APPEAR)
        world.players.forEach { p -> if (p.tile.isWithinRadius(roots.tile, 15)) p.write(LocAnimMessage(roots, 452)) }
        darkanForceMove(player, landing, 25, 60, 819)
    }
}
