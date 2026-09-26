package gg.rsmod.plugins.content.areas.deathsoffice

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.DEATHS_OFFICE_RETURN_ATTR
import gg.rsmod.game.model.collision.ObjectType
import gg.rsmod.game.model.attr.LAST_ACTIVE_CYCLE_ATTR
import gg.rsmod.game.model.attr.RESPAWN_TILE_ATTR
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.queue.TaskPriority
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.content.areas.deathsoffice.DeathDialogue.firstDeath
import gg.rsmod.plugins.content.areas.deathsoffice.DeathDialogue.notFinished
import gg.rsmod.plugins.content.areas.deathsoffice.DeathDialogue.talk
import gg.rsmod.plugins.content.mechanics.death.CofferInterface
import gg.rsmod.plugins.content.mechanics.death.DeathExecutor
import gg.rsmod.plugins.content.mechanics.death.DeathsOffice
import gg.rsmod.plugins.content.mechanics.death.GraveInterface
import gg.rsmod.plugins.content.mechanics.death.Gravestone
import gg.rsmod.plugins.content.mechanics.death.GuidePriceValueProvider
import gg.rsmod.plugins.content.mechanics.death.ItemsKeptOnDeath
import gg.rsmod.plugins.content.mechanics.death.OfficeInterface
import gg.rsmod.plugins.content.mechanics.death.SafeDeath
import gg.rsmod.plugins.content.mechanics.pvp.DeadmanTimerGate
import gg.rsmod.plugins.content.mechanics.pvp.SevenSecondAction

/*
 * Death's Office and the gravestone in the world (OSRS "Death Changes", 25 June 2020; OSRS Wiki "Death's Office", "Grave",
 * "Death (NPC)", "Portal (Death's Office)", "Death's Domain (scenery)"). The screens are in mechanics/death.
 */

// ---- Death in his office ----
spawn_npc(DeathsOfficeArea.DEATH, DeathsOfficeArea.DEATH_TILE.x, DeathsOfficeArea.DEATH_TILE.z, 0, walkRadius = 0, direction = DeathsOfficeArea.DEATH_FACING)

/* Death sits behind his desk: he is spoken to across it (the desk does not block line of sight). */
on_npc_option(npc = DeathsOfficeArea.DEATH, option = "talk-to", lineOfSightDistance = 4) {
    player.queue { talk() }
}

on_npc_option(npc = DeathsOfficeArea.DEATH, option = "collect", lineOfSightDistance = 4) {
    OfficeInterface.open(player)
}

/* "Viewing Items Kept on Death whilst in the Death's Office will show ... the office being a safe area" - dying here is safe. */
SafeDeath.register { DeathsOfficeArea.inOffice(it.tile) }

/*
 * OSRS Wiki "Player-owned house": "If any player dies within a house (via the combat room or dungeon rooms), they will be
 * teleported to outside the house but will retain all their items" (PlayerDeathAction already respawns an instance death at
 * the instance's exit).
 */
SafeDeath.register { gg.rsmod.plugins.content.areas.poh.PlayerHouse.isSafeTile(it.tile) }

// ---- entrances and the portal ----

on_world_init {
    DeathsOfficeArea.ENTRANCES.forEach { entrance ->
        entrance.replaces.forEach { tile ->
            world.getObject(tile, ObjectType.INTERACTABLE)?.let { world.remove(it) }
        }
        world.spawn(DynamicObject(entrance.loc, 10, entrance.rotation, entrance.tile))
    }
}

/**
 * "Enter": the player goes to Death's Office; the portal brings them back to the tile they entered from (OSRS Wiki "Portal
 * (Death's Office)": "It returns the player to a specific tile surrounding the coffin they accessed the area from"). A Death's
 * Domain entrance is a non-magical escape route, so the Deadman countdown applies to it like every other transport.
 */
DeathsOfficeArea.ENTRANCE_LOCS.forEach { loc ->
    on_obj_option(obj = loc, option = "enter") {
        val from = Tile(player.tile)
        DeadmanTimerGate.requestRoute(player, SevenSecondAction.Kind.TRANSPORT) {
            player.attr[DEATHS_OFFICE_RETURN_ATTR] = from.as30BitInteger
            player.moveTo(DeathsOfficeArea.ARRIVAL)
        }
    }
}

on_obj_option(obj = DeathsOfficeArea.PORTAL, option = "use") {
    if (!DeathDialogue.mayLeave(player)) {
        player.queue { notFinished() }
        return@on_obj_option
    }
    DeathDialogue.leave(player)
    // "... or the player's respawn point on their first death." (Mod Ash, 8 November 2021)
    val back =
        player.attr[DEATHS_OFFICE_RETURN_ATTR]?.let { Tile.from30BitHash(it) }
            ?: player.attr[RESPAWN_TILE_ATTR]?.let { Tile.from30BitHash(it) }
            ?: world.gameContext.home.transform(0, -1)
    player.attr.remove(DEATHS_OFFICE_RETURN_ATTR)
    player.moveTo(back)
}

on_obj_option(obj = DeathsOfficeArea.COFFER, option = "sacrifice") {
    CofferInterface.open(player)
}

// ---- the gravestone ----

val GRAVE_TIMER = TimerKey()

fun startGraveTimer(player: gg.rsmod.game.model.entity.Player) {
    if (Gravestone.exists(player)) player.timers[GRAVE_TIMER] = 1
}

/*
 * One tick of the 15-minute gravestone timer. It stands still while the player is idle for more than 10 seconds, while the
 * gravestone window is open, and in Death's Office (the first-death tutorial holds it: "it will wait until you leave").
 */
on_timer(GRAVE_TIMER) {
    val idleTicks = world.currentCycle - (player.attr[LAST_ACTIVE_CYCLE_ATTR] ?: world.currentCycle)
    val active =
        idleTicks <= gg.rsmod.plugins.content.mechanics.death.DeathsDomainConfig.current.graveIdlePauseTicks &&
            !GraveInterface.isOpen(player) && !DeathsOfficeArea.inOffice(player.tile)
    val gone = Gravestone.tick(player, active)
    if (gone) {
        GravestoneWorld.despawn(player)
        if (OfficeInterface.isOpen(player)) OfficeInterface.refresh(player)
    }
    if (Gravestone.exists(player)) player.timers[GRAVE_TIMER] = 1
}

fun timeLeft(owner: gg.rsmod.game.model.entity.Player): String {
    val seconds = maxOf(1, Gravestone.ticksLeft(owner) * 3 / 5)
    return "${seconds / 60}:${"%02d".format(seconds % 60)}"
}

listOf(DeathsOfficeArea.GRAVE, DeathsOfficeArea.GRAVE_ANGEL).forEach { grave ->
    // Owner 2026-09-26: "Check" tells how long the gravestone has left (the OSRS wording is not documented); on someone
    // else's gravestone the RS 2009 line (2009scape GraveController).
    on_npc_option(npc = grave, option = "check") {
        val owner = GravestoneWorld.ownerOf(player.getInteractingNpc()) ?: return@on_npc_option
        if (owner === player) {
            player.message("Your gravestone will collapse in ${timeLeft(owner)}.")
        } else {
            player.message("This is ${owner.username}'s gravestone. It looks like it'll survive another ${timeLeft(owner)}.")
        }
    }
    // "can be accessed from a distance of up to 7 tiles away, provided the player has line-of-sight"; only the owner.
    on_npc_option(npc = grave, option = "loot", lineOfSightDistance = 7) {
        if (GravestoneWorld.ownerOf(player.getInteractingNpc()) !== player) {
            player.message("This isn't your gravestone.")
            return@on_npc_option
        }
        GraveInterface.open(player)
    }
    // Owner 2026-09-26: Bless and Repair like RS 2009 (2009scape GraveController): once each per gravestone.
    on_npc_option(npc = grave, option = "bless") {
        val owner = GravestoneWorld.ownerOf(player.getInteractingNpc()) ?: return@on_npc_option
        when (val outcome = Gravestone.bless(player, owner)) {
            Gravestone.PrayOutcome.OwnGrave -> player.message("The gods don't seem to approve of people attempting to bless their own gravestones.")
            Gravestone.PrayOutcome.AlreadyDone -> player.message("This grave has already been blessed.")
            is Gravestone.PrayOutcome.LevelTooLow -> player.message("You need a Prayer level of ${outcome.level} to bless a grave.")
            Gravestone.PrayOutcome.NoPrayerPoints -> player.message("You do not have enough prayer points to do that.")
            is Gravestone.PrayOutcome.Done -> {
                player.animate(Anims.ALTAR_PRAY)
                player.playSound(Sfx.PRAYER_RECHARGE)
                player.message("You bless the gravestone. It will last another ${outcome.minutes} minutes.")
                owner.message("<col=ff0000>Your grave has been blessed.</col>")
            }
        }
    }
    on_npc_option(npc = grave, option = "repair") {
        val owner = GravestoneWorld.ownerOf(player.getInteractingNpc()) ?: return@on_npc_option
        when (val outcome = Gravestone.repair(player, owner)) {
            Gravestone.PrayOutcome.AlreadyDone -> player.message("This grave has already been repaired.")
            is Gravestone.PrayOutcome.LevelTooLow -> player.message("You need a Prayer level of ${outcome.level} to repair a grave.")
            Gravestone.PrayOutcome.NoPrayerPoints -> player.message("You do not have enough prayer points to do that.")
            is Gravestone.PrayOutcome.Done -> {
                player.animate(Anims.ALTAR_PRAY)
                player.playSound(Sfx.PRAYER_RECHARGE)
                player.message("You repair the gravestone. It will last another ${outcome.minutes} minutes.")
                if (owner !== player) owner.message("<col=ff0000>Your grave has been repaired.</col>")
            }
            Gravestone.PrayOutcome.OwnGrave -> {}
        }
    }
}

// Owner 2026-09-26: every respawn point Death sells must be a standable tile in a Deadman safe zone (RespawnPoints).
on_world_init {
    // Never allowed to stop the boot: on any error no point is offered and everyone respawns at the Grand Exchange.
    runCatching { RespawnPoints.verify(world) }.onSuccess { lines -> lines.forEach { println(it) } }
        .onFailure { println("respawn_verify: FAILED ($it) - only the Grand Exchange respawn is offered") }
}

on_login {
    DeathsOffice.migrateLegacy(player)
    RespawnPoints.sanitize(player)
    if (Gravestone.exists(player) && player.gravestone.isEmpty) Gravestone.clear(player)
    if (Gravestone.exists(player)) {
        GravestoneWorld.respawn(player)
        startGraveTimer(player)
    }
}

on_logout {
    GraveInterface.close(player)
    OfficeInterface.close(player)
    CofferInterface.close(player)
    ItemsKeptOnDeath.close(player)
    GravestoneWorld.despawn(player)
}

/*
 * After the respawn: the gravestone appears, "Some of your items have been added to your previous gravestone." follows
 * "Oh dear, you are dead!", and a first death that cost items takes the player to Death for the explanation.
 */
on_player_death {
    val deposit = player.attr[DeathExecutor.LAST_DEPOSIT] ?: return@on_player_death
    player.attr.remove(DeathExecutor.LAST_DEPOSIT)
    if (deposit.addedToPrevious) player.message("Some of your items have been added to your previous gravestone.")
    if (!Gravestone.exists(player)) return@on_player_death
    GravestoneWorld.respawn(player)
    startGraveTimer(player)
    if (DeathDialogue.needsTutorial(player)) {
        player.attr[DEATHS_OFFICE_RETURN_ATTR] = player.tile.as30BitInteger
        DeathDialogue.set(player, DeathDialogue.IN_TUTORIAL)
        player.moveTo(DeathsOfficeArea.ARRIVAL)
        player.queue(TaskPriority.STANDARD) {
            wait(2)
            firstDeath()
        }
    }
}
