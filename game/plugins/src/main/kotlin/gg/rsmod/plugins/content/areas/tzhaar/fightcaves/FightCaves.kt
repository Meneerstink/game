package gg.rsmod.plugins.content.areas.tzhaar.fightcaves

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.instance.InstancedChunkSet
import gg.rsmod.game.model.instance.InstancedMap
import gg.rsmod.game.model.instance.InstancedMapAttribute
import gg.rsmod.game.model.instance.InstancedMapConfiguration
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.closeInterface
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.hit

/**
 * TzHaar Fight Cave (2011): a private instanced copy of region 9551 in which 63 waves of
 * TzHaar creatures spawn from five cave mouths; wave 63 is TzTok-Jad. Leaving, dying or
 * teleporting ends the attempt and awards TokKul; killing Jad awards a Fire cape.
 *
 * Ported from the Void donor (TzhaarFightCave, TzhaarFightCaveWaves, TzHaarHealers) with
 * Novite's Matrix-derived exit tiles. Wave state persists across logout (the player resumes
 * the wave they were on, like the 2011 "logged out at the end of the wave" behaviour).
 */
object FightCaves {
    const val REGION_BASE_X = 2368
    const val REGION_BASE_Z = 5056
    const val OVERLAY_INTERFACE = 316
    const val WAVE_VARBIT = 1549
    const val COOLDOWN_TICKS = 200
    const val WAVE_DELAY_TICKS = 10

    val CENTRE = Tile(2400, 5088)
    val ENTRANCE = Tile(2413, 5117)
    val OUTSIDE = Tile(2438, 5168)

    val WAVE_ATTR = AttributeKey<Int>(persistenceKey = "fight_cave_wave")
    val ROTATION_ATTR = AttributeKey<Int>(persistenceKey = "fight_cave_rotation")
    val LOGOUT_WARNED = AttributeKey<Boolean>()
    val COOLDOWN_ATTR = AttributeKey<Int>()

    /** Live sessions keyed by player. */
    private val sessions = HashMap<Player, Session>()

    class Session(val player: Player, val map: InstancedMap) {
        var wave = 1
        var remaining = 0
        var pendingWave = -1
        var pendingTicks = 0
        val npcs = ArrayList<Npc>()
        var jad: Npc? = null
        var healersSpawned = false
        var finished = false

        val offsetX get() = map.area.bottomLeftX - REGION_BASE_X
        val offsetZ get() = map.area.bottomLeftZ - REGION_BASE_Z

        fun relative(tile: Tile): Tile = Tile(tile.x + offsetX, tile.z + offsetZ, tile.height)
    }

    enum class SpawnArea(val direction: Direction, val x: IntRange, val z: IntRange) {
        NORTH_WEST(Direction.NORTH_WEST, 2378..2385, 5102..5109),
        SOUTH_WEST(Direction.SOUTH_WEST, 2379..2385, 5070..5076),
        SOUTH(Direction.SOUTH, 2402..2408, 5070..5076),
        SOUTH_EAST(Direction.SOUTH_EAST, 2416..2422, 5080..5086),
        CENTRE(Direction.NONE, 2397..2403, 5085..5091),
        ;

        companion object {
            fun of(direction: Direction): SpawnArea = values().firstOrNull { it.direction == direction } ?: CENTRE
        }
    }

    fun session(player: Player): Session? = sessions[player]

    fun sessionFor(npc: Npc): Session? = sessions.values.firstOrNull { it.map.area.contains(npc.tile) }

    fun inCave(player: Player): Boolean = sessions[player]?.map?.area?.contains(player.tile) == true

    fun isOnCooldown(player: Player): Int {
        val until = player.attr[COOLDOWN_ATTR] ?: return 0
        return (until - player.world.currentCycle).coerceAtLeast(0)
    }

    /** Allocates the instance and moves the player inside; [wave] is the wave to (re)start. */
    fun enter(player: Player, wave: Int, resume: Boolean) {
        val world = player.world
        sessions.remove(player)?.let { it.finished = true }

        val chunks = InstancedChunkSet.Builder()
        for (x in 0 until 8) {
            for (z in 0 until 8) {
                for (height in 0..3) {
                    chunks.set(x, z, height, 0, Tile(REGION_BASE_X + x * 8, REGION_BASE_Z + z * 8, height))
                }
            }
        }
        val config = InstancedMapConfiguration.Builder()
            .setOwner(player.uid)
            .setExitTile(OUTSIDE)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_LOGOUT)
            .addAttribute(InstancedMapAttribute.DEALLOCATE_ON_DEATH)
            .build()
        val map = world.instanceAllocator.allocate(world, chunks.build(), config)
        if (map == null) {
            player.message("The cave is too crowded right now. Try again in a moment.")
            return
        }
        for (rx in (map.area.bottomLeftX shr 6)..(map.area.topRightX shr 6)) {
            for (rz in (map.area.bottomLeftZ shr 6)..(map.area.topRightZ shr 6)) {
                world.plugins.addMultiCombatRegion((rx shl 8) or rz)
            }
        }
        val session = Session(player, map)
        session.wave = wave
        sessions[player] = session
        player.attr[WAVE_ATTR] = wave
        if (wave == 1 || player.attr[ROTATION_ATTR] == null) {
            player.attr[ROTATION_ATTR] = 1 + world.random(FightCaveWaves.ROTATIONS - 1)
        }
        player.attr.remove(LOGOUT_WARNED)
        player.teleportTo(session.relative(if (resume) CENTRE else ENTRANCE))
        openOverlay(player, wave)
        session.pendingWave = wave
        session.pendingTicks = if (resume) 5 else WAVE_DELAY_TICKS
    }

    fun openOverlay(player: Player, wave: Int) {
        player.setVarbit(WAVE_VARBIT, wave)
        player.openInterface(dest = InterfaceDestination.PVP_OVERLAY, interfaceId = OVERLAY_INTERFACE)
    }

    /** Per-tick bookkeeping bound to a world timer. */
    fun cycle(world: World) {
        val iterator = sessions.values.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            val player = session.player
            if (session.finished) {
                iterator.remove()
                continue
            }
            if (!player.isOnline) {
                session.finished = true
                iterator.remove()
                continue
            }
            if (!session.map.area.contains(player.tile)) {
                // Left the cave (teleport, death teleport, exit cave).
                iterator.remove()
                leave(session, defeatedJad = false, alreadyOutside = true)
                continue
            }
            if (session.pendingWave != -1) {
                if (--session.pendingTicks <= 0) {
                    val wave = session.pendingWave
                    session.pendingWave = -1
                    startWave(session, wave)
                }
                continue
            }
            session.npcs.removeAll { !it.isSpawned() || it.isDead() }
            if (session.remaining <= 0 && session.npcs.isEmpty()) {
                onWaveCleared(session)
            }
        }
    }

    fun startWave(session: Session, wave: Int) {
        val player = session.player
        val world = player.world
        session.wave = wave
        player.attr[WAVE_ATTR] = wave
        openOverlay(player, wave)
        if (wave == 1) {
            player.message("You're on your own now, JalYt. Prepare to fight for your life!")
        } else if (wave == FightCaveWaves.TOTAL_WAVES) {
            player.message("<col=ff0000>Look out, here comes TzTok-Jad!")
        }
        player.message("Wave: $wave")
        val ids = FightCaveWaves.npcs(wave)
        val directions = FightCaveWaves.spawns(wave, player.attr[ROTATION_ATTR] ?: 1)
        session.remaining = ids.sumOf { id -> val n: Int = if (id == Npcs.TZKEK_2736 || id == Npcs.TZKEK_2737) 3 else 1; n }
        session.healersSpawned = false
        session.jad = null
        for (i in ids.indices) {
            val id = ids[i]
            val direction = directions.getOrElse(i) { Direction.NONE }
            val tile = randomTile(session, SpawnArea.of(direction), world, size = world.definitions.get(gg.rsmod.game.fs.def.NpcDef::class.java, id).size)
            val npc = spawn(session, id, tile)
            if (id == Npcs.TZTOKJAD) session.jad = npc
        }
    }

    fun spawn(session: Session, id: Int, tile: Tile): Npc {
        val world = session.player.world
        val npc = Npc(id, tile, world)
        npc.respawns = false
        npc.walkRadius = 3
        if (id == Npcs.TZKEK_2736 || id == Npcs.TZKEK_2737 || id == Npcs.TZKEK_2738) {
            // Tz-Kek recoil: melee attackers take 10 damage per landed hit (only the owner fights here).
            npc.hitModifier = { hit ->
                if (hit.hitmarks.any { it.type == gg.rsmod.plugins.api.HitType.MELEE.id && it.damage > 0 }) {
                    session.player.hit(10, gg.rsmod.plugins.api.HitType.REGULAR_HIT)
                }
            }
        }
        world.spawn(npc)
        session.npcs.add(npc)
        world.queue {
            wait(1)
            if (npc.isSpawned() && !npc.isDead()) npc.attack(session.player)
        }
        return npc
    }

    private fun randomTile(session: Session, area: SpawnArea, world: World, size: Int): Tile {
        repeat(30) {
            val x = world.random(area.x)
            val z = world.random(area.z)
            val tile = session.relative(Tile(x, z, 0))
            var clear = true
            loop@ for (dx in 0 until size) {
                for (dz in 0 until size) {
                    if (world.collision.isClipped(tile.transform(dx, dz))) { clear = false; break@loop }
                }
            }
            if (clear) return tile
        }
        return session.relative(Tile(area.x.first, area.z.first, 0))
    }

    /** Called from the plugin when a wave npc dies. */
    fun onNpcDeath(npc: Npc) {
        val session = sessionFor(npc) ?: return
        session.npcs.remove(npc)
        session.remaining--
        if (npc.id == Npcs.TZTOKJAD) {
            session.remaining = 0
            session.npcs.filter { it.id == Npcs.YTHURKOT }.forEach { healer ->
                if (healer.isSpawned()) npc.world.remove(healer)
            }
            session.npcs.removeAll { it.id == Npcs.YTHURKOT }
            leave(session, defeatedJad = true, alreadyOutside = false)
            return
        }
        if (npc.id == Npcs.TZKEK_2736 || npc.id == Npcs.TZKEK_2737) {
            // A Tz-Kek splits into two smaller Tz-Keks.
            val world = npc.world
            val tiles = listOf(Tile(npc.tile), Tile(npc.tile).transform(1, 0))
            tiles.forEach { spawn(session, Npcs.TZKEK_2738, it) }
        }
    }

    private fun onWaveCleared(session: Session) {
        if (session.wave >= FightCaveWaves.TOTAL_WAVES) return
        val player = session.player
        if (player.attr[LOGOUT_WARNED] == true) {
            // 2011 behaviour: the player asked to log out and is logged out at the end of the wave.
            player.attr[WAVE_ATTR] = session.wave + 1
            player.requestLogout()
            return
        }
        session.pendingWave = session.wave + 1
        session.pendingTicks = WAVE_DELAY_TICKS
        player.message("<col=ff0000>Wave ${session.wave} complete. The next wave will begin shortly.")
    }

    /** Spawns the four Yt-HurKot healers once Jad drops below half health. */
    fun checkJadHealers(session: Session) {
        val jad = session.jad ?: return
        if (session.healersSpawned || jad.isDead()) return
        if (jad.getCurrentLifepoints() > jad.getMaximumLifepoints() / 2) return
        session.healersSpawned = true
        val world = jad.world
        repeat(4) {
            val tile = world.findRandomTileAround(jad.getCentreTile(), radius = 4) ?: return@repeat
            val healer = Npc(Npcs.YTHURKOT, tile, world)
            healer.respawns = false
            healer.walkRadius = 6
            world.spawn(healer)
            session.npcs.add(healer)
            world.queue {
                wait(1)
                if (healer.isSpawned() && !healer.isDead()) FightCaveCombatScripts.YtHurKot.startHealing(healer, jad)
            }
        }
    }

    /**
     * Ends the attempt: TokKul for the wave reached, a Fire cape for defeating Jad, cooldown and
     * teleport outside. [alreadyOutside] means the player left by other means (teleport/death).
     */
    fun leave(session: Session, defeatedJad: Boolean, alreadyOutside: Boolean) {
        if (session.finished) return
        session.finished = true
        sessions.remove(session.player)
        val player = session.player
        val world = player.world
        val wave = session.wave
        session.npcs.forEach { if (it.isSpawned()) world.remove(it) }
        session.npcs.clear()
        player.attr.remove(WAVE_ATTR)
        player.attr.remove(ROTATION_ATTR)
        player.attr.remove(LOGOUT_WARNED)
        player.attr[COOLDOWN_ATTR] = world.currentCycle + COOLDOWN_TICKS
        player.closeInterface(dest = InterfaceDestination.PVP_OVERLAY)
        if (!alreadyOutside) {
            player.teleportTo(OUTSIDE)
        }
        var tokkul = wave * (wave + 1)
        if (defeatedJad) {
            tokkul += 4000
            addOrDrop(player, Item(Items.FIRE_CAPE))
        }
        if (tokkul > 0) addOrDrop(player, Item(Items.TOKKUL, tokkul))
        world.queue {
            wait(1)
            if (defeatedJad) {
                player.message("<col=ff0000>You were victorious!!")
                player.message("TzHaar-Mej-Jal: You even defeated TzTok-Jad, I am most impressed! Please accept this gift as a reward.")
            } else if (tokkul > 0) {
                player.message("TzHaar-Mej-Jal: Well done in the cave, here, take TokKul as a reward.")
            } else {
                player.message("TzHaar-Mej-Jal: Well I suppose you tried... better luck next time.")
            }
        }
        world.instanceAllocator.release(world, session.map)
    }

    private fun addOrDrop(player: Player, item: Item) {
        val result = player.inventory.add(item)
        if (!result.hasSucceeded()) {
            val left = item.amount - result.completed
            if (left > 0) {
                player.world.spawn(GroundItem(item.id, left, player.tile, player))
            }
        }
    }

    /** Logout: drop the live session without rewards; the saved wave attribute resumes it later. */
    fun clearSession(player: Player) {
        val session = sessions.remove(player) ?: return
        session.finished = true
        val world = player.world
        session.npcs.forEach { if (it.isSpawned()) world.remove(it) }
        session.npcs.clear()
        world.instanceAllocator.release(world, session.map)
    }

    fun clearAll() {
        sessions.values.forEach { it.finished = true }
        sessions.clear()
    }
}
