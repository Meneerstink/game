package gg.rsmod.plugins.content.activity.sorceress_garden

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.content.mechanics.run.RunEnergy

/**
 * Core Sorceress's Garden logic, ported from Void's `SorceressGarden.kt`/`Elementals.kt`. See
 * [GardenSeason]/[GardenElementals] for the sourced data this operates on.
 *
 * Disclosed simplifications (not guesses - the underlying mechanic is real and sourced, only the
 * presentation is simplified): teleports use a plain, silent [Npc]/[Player] position change
 * instead of Void's screen fade-out/fade-in + sound sequence (no fitting sound/interface id was
 * verified for those, so none was invented); the elemental "catch" check is a simple per-tick
 * proximity test (any player within 2 tiles of the elemental after it moves) rather than Void's
 * directional "2 tiles directly in front" cone, since this engine has no equivalent hunt-direction
 * API - the real effect (getting too close to a patrolling elemental ejects you) is preserved.
 */
object SorceressGardenHandler {
    fun enter(
        player: Player,
        season: GardenSeason,
    ) {
        if (player.skills.getMaxLevel(Skills.THIEVING) < season.thievingLevelReq) {
            player.filterableMessage("You need a Thieving level of ${season.thievingLevelReq} to pick the lock of this gate.")
            return
        }
        player.moveTo(season.entryTile)
        player.filterableMessage("You pass through the gate.")
    }

    fun leave(player: Player) {
        player.moveTo(GardenSeason.EXIT_TILE)
    }

    fun pickFruit(
        player: Player,
        season: GardenSeason,
    ) {
        if (!player.inventory.add(season.sqirkItem).hasSucceeded()) {
            player.filterableMessage("I cannot carry any more.")
            return
        }
        player.addXp(Skills.THIEVING, season.thievingXp)
        leave(player)
    }

    fun pickHerb(
        player: Player,
        season: GardenSeason,
    ) {
        if (player.inventory.freeSlotCount < 2) {
            player.filterableMessage("I cannot carry any more.")
            return
        }
        // Void rolls its weighted herb table twice per pick (2 herbs/pick) - ported verbatim below.
        repeat(2) {
            val herb = rollHerb(season)
            player.inventory.add(herb)
        }
        player.addXp(Skills.FARMING, season.farmingXp)
        leave(player)
    }

    /** Weighted pick over [GardenSeason.herbTable] - sums to [GardenSeason.herbTableTotal] exactly, so every roll yields an item (no "nothing" slot, matching Void's own table). */
    private fun rollHerb(season: GardenSeason): Int {
        var roll = RANDOM_GARDEN.nextInt(season.herbTableTotal)
        for ((item, chance) in season.herbTable) {
            if (roll < chance) {
                return item
            }
            roll -= chance
        }
        return season.herbTable.last().first
    }

    /** Elemental patrol tick: advance one waypoint step, then eject any player within 2 tiles. */
    fun patrolTick(npc: Npc) {
        val data = GardenElementals.ALL[npc.id] ?: return
        if (data.patrol.isEmpty()) return
        val index = (npcPatrolIndex.getOrDefault(npc.index, 0) + 1) % data.patrol.size
        npcPatrolIndex[npc.index] = index
        val next = data.patrol[index]
        npc.walkTo(next)

        npc.world.players.forEach { player ->
            if (player.tile.isWithinRadius(npc.tile, 2) && !recentlyCaught(player)) {
                catchPlayer(npc, player)
            }
        }
    }

    private val npcPatrolIndex = HashMap<Int, Int>()
    private val caughtUntil = HashMap<Int, Long>()

    private fun recentlyCaught(player: Player): Boolean {
        val until = caughtUntil[player.index] ?: return false
        return System.currentTimeMillis() < until
    }

    private fun catchPlayer(
        npc: Npc,
        player: Player,
    ) {
        caughtUntil[player.index] = System.currentTimeMillis() + 3000L
        player.filterableMessage("You have been spotted by an elemental!")
        player.moveTo(GardenSeason.EXIT_TILE)
    }

    fun drinkSqirkJuice(
        player: Player,
        season: GardenSeason,
    ) {
        val boost =
            when (season) {
                GardenSeason.SPRING -> 1
                GardenSeason.AUTUMN -> 2
                GardenSeason.SUMMER -> 3
                GardenSeason.WINTER -> 0
            }
        if (boost > 0) {
            player.skills.alterCurrentLevel(Skills.THIEVING, boost, boost)
        }
        val energyGain =
            when (season) {
                GardenSeason.WINTER -> player.runEnergy / 20.0
                GardenSeason.SPRING -> player.runEnergy / 10.0
                GardenSeason.AUTUMN -> player.runEnergy * 0.15
                GardenSeason.SUMMER -> player.runEnergy / 5.0
            }
        RunEnergy.renew(player, energyGain)
        player.filterableMessage("You drink some sq'irk juice.")
    }
}

private val RANDOM_GARDEN = java.util.concurrent.ThreadLocalRandom.current()
