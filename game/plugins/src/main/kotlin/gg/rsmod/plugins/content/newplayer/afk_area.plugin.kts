package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.GameObject

/*
 * The Skilling Basement under the Grand Exchange hall (AfkArea): the stairs in both directions, the stations, the
 * pond's fishing spot and the Survival Expert. Every station is a loc copy (or npc copy) that exists nowhere else, so
 * the world's own trees, rocks, fishing spots and stalls keep their normal skill code.
 */

on_world_init {
    val cap = NewPlayerConfig.current.afkLevelCap
    // "<Skill> - AFK up to level 55" on every station's examine
    AfkArea.STATIONS.forEach { station ->
        val examine = "${Skills.getSkillName(world, station.skill)} - AFK up to level $cap."
        if (station.npc) {
            world.definitions.getNullable(NpcDef::class.java, station.loc)?.examine = examine
        } else {
            world.definitions.getNullable(ObjectDef::class.java, station.loc)?.examine = examine
        }
    }
    world.plugins.bindObjectOverride { p, obj, _ ->
        val down = obj.id == AfkArea.HALL_STAIRS_DOWN && obj.tile == AfkArea.HALL_STAIRS
        val up = obj.id == AfkArea.BASEMENT_STAIRS_UP && obj.tile == AfkArea.BASEMENT_STAIRS
        if (!down && !up) return@bindObjectOverride false
        p.faceTile(obj.tile)
        p.lockingQueue(lockState = LockState.FULL) {
            wait(2)
            p.moveTo(if (down) AfkArea.BASEMENT_ARRIVAL else AfkArea.HALL_ARRIVAL)
        }
        true
    }
}

spawn_npc(AfkArea.FISHING_SPOT, AfkArea.FISHING_SPOT_TILE.x, AfkArea.FISHING_SPOT_TILE.z, 0, walkRadius = 0)
spawn_npc(AfkArea.SURVIVAL_EXPERT, AfkArea.SURVIVAL_EXPERT_TILE.x, AfkArea.SURVIVAL_EXPERT_TILE.z, 0, walkRadius = 0, direction = Direction.WEST)

AfkArea.STATIONS.filter { !it.npc }.distinctBy { it.loc }.forEach { station ->
    on_obj_option(obj = station.loc, option = station.option.lowercase()) {
        val obj = player.getInteractingGameObj()
        if (AfkArea.contains(obj.tile)) startTraining(player, station, obj.tile)
    }
}

// The fishing spot sits in the middle of the pond: fish from any bank, up to two tiles away.
AfkArea.STATIONS.filter { it.npc }.forEach { station ->
    on_npc_option(npc = station.loc, option = station.option.lowercase(), lineOfSightDistance = 2) {
        val npc = player.getInteractingNpc()
        if (AfkArea.contains(npc.tile)) startTraining(player, station, npc.tile)
    }
}

on_npc_option(npc = AfkArea.SURVIVAL_EXPERT, option = "talk-to") {
    player.queue { expertTalk(player) }
}

// The first time a player comes down, the Survival Expert explains the basement (once, saved on the account).
on_enter_region(AfkArea.REGION_ID) {
    if (AfkArea.markIntro(player)) {
        player.queue {
            wait(2)
            expertIntro()
        }
    }
}

suspend fun QueueTask.expertIntro() {
    val lines = AfkArea.introLines(NewPlayerConfig.current.afkLevelCap)
    lines.chunked(2).forEach { chatNpc(*it.toTypedArray(), npc = AfkArea.SURVIVAL_EXPERT) }
}

suspend fun QueueTask.expertTalk(player: Player) {
    expertIntro()
    val cap = NewPlayerConfig.current.afkLevelCap
    val done = AfkArea.TRAINABLE.filter { AfkArea.atCap(player, it) }.map { Skills.getSkillName(world, it) }.sorted()
    if (done.isNotEmpty()) {
        chatNpc("You've already reached level $cap here in: ${done.joinToString(", ")}. Train those out in the world now.", npc = AfkArea.SURVIVAL_EXPERT)
    }
}

/** A cheap fingerprint of the inventory and equipment: it changes when the player uses an item. */
fun itemsFingerprint(player: Player): Int {
    var hash = 17
    listOf(player.inventory, player.equipment).forEach { container ->
        container.rawItems.forEach { item -> hash = hash * 31 + (item?.let { it.id * 65536 + it.amount } ?: -1) }
    }
    return hash
}

fun startTraining(player: Player, station: AfkArea.Station, target: Tile) {
    val config = NewPlayerConfig.current
    val name = Skills.getSkillName(world, station.skill)
    player.faceTile(target)
    if (AfkArea.atCap(player, station.skill, config)) {
        AfkArea.endSession(player)
        player.message("Your $name is already level ${config.afkLevelCap}; train it out in the world now.")
        return
    }
    val session = AfkArea.newSession(player)
    val startTile = player.tile
    val items = itemsFingerprint(player)
    player.message("You start training $name. Move or do something else to stop.")
    // Movement, another click on a loc, npc, player or the map, a teleport and logging out all end the queue;
    // mayTrain and the checks below end it too when the player does anything else (an item, an interface, a lock).
    player.queue {
        var tick = 0
        while (true) {
            if (!AfkArea.mayTrain(player, session) || player.tile != startTile || itemsFingerprint(player) != items ||
                player.interfaces.getModal() != -1 || player.isLocked()
            ) {
                break
            }
            if (tick % station.animationTicks == 0) player.animate(station.animation)
            if (!AfkArea.train(player, station.skill, config)) {
                AfkArea.endSession(player)
                player.animate(-1)
                player.message("Your $name has reached level ${config.afkLevelCap}, the most the basement can teach.")
                if (AfkArea.markCapped(player, station.skill)) {
                    AfkArea.capLines(name, config.afkLevelCap).forEach { chatNpc(it, npc = AfkArea.SURVIVAL_EXPERT) }
                }
                return@queue
            }
            tick++
            wait(1)
        }
        if (player.attr[AfkArea.SESSION] == session) AfkArea.endSession(player)
        player.animate(-1)
    }
}
