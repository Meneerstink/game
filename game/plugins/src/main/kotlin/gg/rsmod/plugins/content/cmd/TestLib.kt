package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.message.Message
import gg.rsmod.game.message.impl.IfCloseSubMessage
import gg.rsmod.game.message.impl.IfOpenSubMessage
import gg.rsmod.game.message.impl.IfOpenTopMessage
import gg.rsmod.game.message.impl.MessageGameMessage
import gg.rsmod.game.message.impl.MidiSongMessage
import gg.rsmod.game.message.impl.MusicEffectMessage
import gg.rsmod.game.message.impl.SynthSoundMessage
import gg.rsmod.game.model.Probe
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * N620 TESTBIBLIOTHEEK (owner 14-11 'testen duurt altijd fucking lang hoe komt dat' + 'zo uitgebreid mogelijk'): what a test script
 * needs to KNOW instead of guessing with sleeps and log greps.
 *
 * - [watch] + [events]: per watched player a numbered stream of what the server sent him (chat messages, interfaces opened/closed,
 *   sounds, songs, his own animation/gfx/hit/forcechat blocks, area sounds near him) and every [deny]. A script asks
 *   `testevents <name> <since>` and waits for the line it expects - no fixed sleeps, no client that has to draw.
 * - [deny]: one call for a handler that refuses silently ('return' without a message): logs `[trace:input] DENY ...` and puts a
 *   `deny` event in the stream, so a test says WHY nothing happened (14-11: MageArenaII trackable empty = 15 min code reading).
 *
 * The recorder ([Probe.recorder]) is only attached while a player is watched; the conformance suite may take the slot for its own
 * run (it clears it only when it is its own), [ensureAttached] takes it back on the next query. Watching costs one set lookup per
 * packet; nothing is recorded for players nobody watches.
 */
object TestLib {
    const val MAX_EVENTS = 400

    class Stream {
        var seq = 0L
        val lines = ArrayDeque<Pair<Long, String>>(MAX_EVENTS)
    }

    private val streams = ConcurrentHashMap<String, Stream>()

    @Volatile
    private var world: World? = null

    private fun key(p: Player) = p.username.lowercase()

    fun watched(p: Player): Boolean = streams.containsKey(key(p))

    fun watch(p: Player): Stream {
        val s = streams.getOrPut(key(p)) { Stream() }
        world = p.world
        ensureAttached()
        return s
    }

    fun unwatch(p: Player) {
        streams.remove(key(p))
        if (streams.isEmpty() && Probe.recorder === recorder) Probe.recorder = null
    }

    fun add(
        p: Player,
        line: String,
    ) {
        val s = streams[key(p)] ?: return
        synchronized(s) {
            s.seq++
            if (s.lines.size >= MAX_EVENTS) s.lines.removeFirst()
            s.lines.addLast(s.seq to "c${p.world.currentCycle} $line")
        }
    }

    /** Events after [since] (max [limit]); first element = the newest sequence number. */
    fun events(
        p: Player,
        since: Long,
        limit: Int = 60,
    ): Pair<Long, List<String>> {
        val s = watch(p)
        synchronized(s) {
            val out = s.lines.filter { it.first > since }.takeLast(limit).map { "${it.first}:${it.second}" }
            return s.seq to out
        }
    }

    /**
     * A handler refuses an action without telling the player (or with a message that does not say why): call this so test runs
     * and the traces show the reason. Never changes gameplay.
     */
    fun deny(
        p: Player,
        reason: String,
    ) {
        Trace.log(Trace.Cat.INPUT) { "DENY ${p.username} at=${p.tile.x},${p.tile.z},${p.tile.height}: $reason" }
        add(p, "deny $reason")
    }

    fun ensureAttached() {
        if (streams.isNotEmpty() && Probe.recorder == null) Probe.recorder = recorder
    }

    private val recorder =
        object : Probe.Recorder {
            override fun message(
                player: Player,
                message: Message,
            ) {
                if (!watched(player)) return
                val line =
                    when (message) {
                        is MessageGameMessage -> if (message.message.startsWith("__RSPS_HUD__")) null else "msg type=${message.type} '${message.message}'"
                        is IfOpenSubMessage -> "ifopen ${message.parent}:${message.child}=${message.component} type=${message.type}"
                        is IfCloseSubMessage -> "ifclose ${message.hash ushr 16}:${message.hash and 0xffff}"
                        is IfOpenTopMessage -> "iftop ${message.top}"
                        is SynthSoundMessage -> "sound ${message.sound} loops=${message.loops} delay=${message.delay}"
                        is MidiSongMessage -> "song ${message.id}"
                        is MusicEffectMessage -> "jingle ${message.id}"
                        else -> null
                    } ?: return
                add(player, line)
            }

            override fun endOfCycle(pawn: Pawn) {
                if (pawn !is Player || !watched(pawn)) return
                // Only what the player sees happen to himself: animation, gfx, hits, overhead text, forced movement, teleport.
                val shown = Probe.blocks(pawn).filter { b -> !b.startsWith("step") && !b.startsWith("face") && !b.startsWith("combatlevel") && b != "appearance" }
                if (shown.isNotEmpty()) add(pawn, "self ${shown.joinToString("; ")} at=${pawn.tile.x},${pawn.tile.z},${pawn.tile.height}")
            }

            override fun projectile(projectile: Projectile) {}

            override fun areaSound(sound: AreaSound) {
                streams.keys.forEach { name ->
                    val p = world?.getPlayerForName(name) ?: return@forEach
                    if (p.tile.height == sound.tile.height && p.tile.isWithinRadius(sound.tile, 15)) {
                        add(p, "areasound ${sound.id} at=${sound.tile.x},${sound.tile.z} r=${sound.radius}")
                    }
                }
            }
        }

    /** True when a pawn of size 1 can stand on [t] and leave it in at least one direction (not inside a rock/wall). */
    fun standable(
        world: World,
        t: Tile,
    ): Boolean =
        gg.rsmod.game.model.Direction.NESW.any { d ->
            world.collision.canTraverse(tile = t, direction = d, projectile = false, water = false)
        }

    /** The nearest standable tile within [r] of [t] (ring by ring), or null. */
    fun nearestStandable(
        world: World,
        t: Tile,
        r: Int = 6,
    ): Tile? {
        if (standable(world, t)) return t
        for (d in 1..r) for (dx in -d..d) for (dz in -d..d) {
            if (maxOf(Math.abs(dx), Math.abs(dz)) != d) continue
            val c = t.transform(dx, dz)
            if (standable(world, c)) return c
        }
        return null
    }

    fun inWorld(
        x: Int,
        z: Int,
        h: Int,
    ): Boolean = x in 1..16000 && z in 1..16000 && h in 0..3

    /** The map around [t] was loaded (its chunk exists): 15-11 'tp 4 1' landed in nothing (no map = every tile 'standable'). */
    fun mapped(
        world: World,
        t: Tile,
    ): Boolean = world.chunks.get(t, createIfNeeded = false) != null
}
