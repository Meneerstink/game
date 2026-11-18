package gg.rsmod.plugins.content.areas.falador

import gg.rsmod.plugins.content.npcs.definitions.barrows.Barrows
import gg.rsmod.plugins.content.quests.getCurrentStage
import gg.rsmod.plugins.content.quests.impl.PiratesTreasure
import gg.rsmod.plugins.content.scrolls.ClueScrollManager

/**
 * Whether gardener has been spawned
 */
val GARDENER_SPAWNED_ATTR = AttributeKey<Boolean>(persistenceKey = "gardener_spawned")

/**
 * 18-11 (WACHTWERK 1): every spade dig plays the dig animation (830) AND the spade sound (Sfx.DIGSPADE 1470: wiki Spade sound
 * 'Digging'; seq 830 carries no frame sound). Before, a dig anywhere but here/Barrows - also on a clue spot - showed nothing.
 * No chat line when nothing is found: OSRS has none on record (wiki Spade, RuneLite clue plugin), so none is invented.
 */
fun digWithSpade(player: gg.rsmod.game.model.entity.Player) {
    player.animate(Anims.DIG_SPADE)
    player.playSound(Sfx.DIGSPADE)
}

on_item_option(Items.SPADE, "Dig") {
    if ((player.tile.x == 2999 || player.tile.x == 3000) && player.tile.z == 3383) {
        if (player.getCurrentStage(PiratesTreasure) != 3) {
            player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {
                digWithSpade(player)
                wait(2)
            }
            player.animate(Anims.DIG_SPADE)
            return@on_item_option
        }
        val spawned = player.attr.has(GARDENER_SPAWNED_ATTR) && player.attr[GARDENER_SPAWNED_ATTR]!!
        if (spawned) {
            if (world.npcs.any { npc -> npc.id == Npcs.GARDENER_3914 && npc.owner == player && npc.isAlive()}) {
                player.message("I can't dig up anything with him attacking me!")
            }
            else {
                player.lockingQueue(lockState = LockState.DELAY_ACTIONS) {
                    digWithSpade(player)
                    wait(3)
                    PiratesTreasure.finishQuest(player)
                }
            }
        }
        else {
            player.attr[GARDENER_SPAWNED_ATTR] = true
            val gardener = Npc(
                owner = player,
                id = Npcs.GARDENER_3914,
                tile = Tile(2996, 3383),
                world = world
            )
            player.lockingQueue {
                digWithSpade(player)
                world.spawn(gardener)
                gardener.respawns = false
                wait(2)
                gardener.forceChat("First moles, now this! Take this, vandal!")
                wait(1)
                gardener.attack(player)
            }
        }
    } else if (!Barrows.digMound(player)) {
        digWithSpade(player)
        ClueScrollManager.tryDig(player)
    }
}
