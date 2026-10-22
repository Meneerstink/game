package gg.rsmod.plugins.content.newplayer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.api.cfg.FacialExpression

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
    // The Quest Tutor's camera tour only flies through: the welcome waits for the player's own first visit.
    if (gg.rsmod.plugins.content.quests.foundation.TutorIntro.isActive(player)) return@on_enter_region
    if (AfkArea.markIntro(player)) {
        player.queue {
            wait(2)
            expertIntro()
        }
    }
}

suspend fun QueueTask.expert(vararg lines: String, face: FacialExpression = FacialExpression.HAPPY_TALKING) {
    lines.forEach { chatNpc(it, npc = AfkArea.SURVIVAL_EXPERT, facialExpression = face) }
}

suspend fun QueueTask.expertIntro() {
    expert(*AfkArea.introLines(NewPlayerConfig.current.afkLevelCap).toTypedArray())
}

// Talk-to: a real conversation with questions, instead of the welcome speech every time (owner 2026-09-26).
suspend fun QueueTask.expertTalk(player: Player) {
    val cap = NewPlayerConfig.current.afkLevelCap
    expert("Hello again, adventurer. How are you getting on down here?")
    while (true) {
        when (options("What is this place?", "How does the training work?", "Where do I find each skill?", "How far have I come here?", "Goodbye.", title = "Survival Expert")) {
            1 -> {
                chatPlayer("What is this place?", facialExpression = FacialExpression.THINKING)
                expert(
                    "This is the Skilling Basement, the guild cellar under the mansion.",
                    "It's a quiet place to build up your skills before you head out into the world. No tools, no materials, no danger.",
                )
            }
            2 -> {
                chatPlayer("How does the training work?", facialExpression = FacialExpression.THINKING)
                expert(
                    "Click a station once and you'll keep training on your own. You earn experience every moment, but never any items.",
                    "Walk away, click something else, use an item or open an interface, and you'll stop.",
                    "You can train each skill here up to level $cap. After that, the world is your teacher.",
                )
            }
            3 -> {
                chatPlayer("Where do I find each skill?", facialExpression = FacialExpression.THINKING)
                expert(*AfkArea.WHERE_LINES.toTypedArray())
            }
            4 -> {
                chatPlayer("How far have I come down here?", facialExpression = FacialExpression.THINKING)
                val done = AfkArea.TRAINABLE.filter { AfkArea.atCap(player, it) }.map { Skills.getSkillName(world, it) }.sorted()
                if (done.isEmpty()) {
                    expert("You haven't reached level $cap in any skill down here yet. Keep at it!")
                } else if (done.size == AfkArea.TRAINABLE.size) {
                    expert("Every skill I teach is at level $cap. There's nothing more I can show you - the world is waiting!", face = FacialExpression.LAUGH_EXCITED)
                } else {
                    expert("You've already reached level $cap in ${AfkArea.joinNames(done)}. Train ${if (done.size == 1) "that one" else "those"} out in the world now.")
                }
            }
            else -> {
                chatPlayer("Thanks, goodbye!")
                expert("Good luck with your training, adventurer!")
                return
            }
        }
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
        player.message(AfkArea.alreadyCappedMessage(name, config.afkLevelCap))
        return
    }
    val session = AfkArea.newSession(player)
    val startTile = player.tile
    val items = itemsFingerprint(player)
    // N181 (owner 2026-10-22): once per skilling object, not on every (spam) click.
    if (AfkArea.firstClickOn(player, station.loc, target)) player.message("You start training $name. Move or do something else to stop.")
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
            // N180: the skill's sound every animation cycle (at most every 4 ticks: the balance walk loops each tick), unless the
            // animation plays an audible sound of its own.
            if (tick % maxOf(station.animationTicks, 4) == 0 && station.sound > 0 && !AfkArea.animationAudible(player, station.animation)) {
                player.playSound(station.sound)
            }
            if (!AfkArea.train(player, station.skill, config)) {
                AfkArea.endSession(player)
                player.animate(-1)
                player.message(AfkArea.capReachedMessage(name, config.afkLevelCap))
                if (AfkArea.markCapped(player, station.skill)) {
                    AfkArea.capLines(name, config.afkLevelCap).forEach { chatNpc(it, npc = AfkArea.SURVIVAL_EXPERT, facialExpression = FacialExpression.HAPPY) }
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
