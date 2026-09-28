package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.message.Message
import gg.rsmod.game.model.KeyNames
import gg.rsmod.game.model.Probe
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.plugins.api.ext.isMulti
import java.lang.ref.Reference
import java.util.IdentityHashMap

/**
 * Conformance suite (P8, docs\conformance_catalogue.md) - records everything measurable about the tracked pawns, tick by tick:
 *
 *  - out   : every update block the client receives (animation, graphics, hitmarks/hitbars, facing, forced chat/movement,
 *            appearance, steps walked/run, teleports) - [Probe.blocks]
 *  - msg   : every packet written to a tracked player (sounds, varps/varbits, interfaces, inventories, chat, stats, camera,
 *            zone updates with projectiles/objects/ground items, ...)
 *  - proj / areasound : every projectile and area sound spawned within [NEAR] tiles of a tracked pawn
 *  - state : every change of the pawn's full state since the previous tick (true tile, all skills and xp, hitpoints, prayer,
 *            run energy, weight, all timers and attributes by name, all varps, every container slot, open interfaces,
 *            queued tasks, planned path, lock, facing, multi-way, collision under the tile, npc stats/transform)
 *
 * The first tick of a tracked pawn logs its whole state once (kind "init"); after that only changes, so a scenario's timeline
 * reads as "what happened on which tick". Scenarios query [events] to compare against expected values.
 */
class Recording(
    private val world: World,
) : Probe.Recorder {
    data class Ev(
        val cycle: Int,
        val who: String,
        val kind: String,
        val text: String,
    ) {
        override fun toString() = "t$cycle $who $kind $text"
    }

    val events = ArrayList<Ev>()
    private val tracked = IdentityHashMap<Pawn, String>()
    private val last = IdentityHashMap<Pawn, Map<String, String>>()

    val startCycle = world.currentCycle

    fun track(
        pawn: Pawn,
        label: String,
    ) = synchronized(this) { tracked[pawn] = label }

    fun untrack(pawn: Pawn) =
        synchronized(this) {
            tracked.remove(pawn)
            last.remove(pawn)
        }

    fun label(pawn: Pawn?): String =
        when {
            pawn == null -> "-"
            tracked.containsKey(pawn) -> tracked[pawn]!!
            pawn is Player -> "player:${pawn.username}"
            pawn is Npc -> "npc${pawn.id}#${pawn.index}"
            else -> pawn.toString()
        }

    fun attach() {
        KeyNames.loader = Recording::class.java.classLoader
        Probe.recorder = this
    }

    fun detach() {
        if (Probe.recorder === this) Probe.recorder = null
    }

    private fun add(
        who: String,
        kind: String,
        text: String,
    ) = synchronized(this) { events += Ev(world.currentCycle, who, kind, text) }

    // ---- probe hooks ------------------------------------------------------------------------------------------------------

    override fun message(
        player: Player,
        message: Message,
    ) {
        val who = synchronized(this) { tracked[player] } ?: return
        add(who, "msg", describe(message))
    }

    override fun endOfCycle(pawn: Pawn) {
        val who = synchronized(this) { tracked[pawn] } ?: return
        Probe.blocks(pawn).forEach { add(who, "out", it) }
        // A part of the state that cannot be read is itself recorded, never thrown into the sync.
        val now = runCatching { snapshot(pawn) }.getOrElse { mapOf("snapshot.error" to it.toString().take(200)) }
        val before = synchronized(this) { last.put(pawn, now) }
        if (before == null) {
            add(who, "init", now.entries.joinToString(" | ") { "${it.key}=${it.value}" })
            return
        }
        for ((k, v) in now) {
            val old = before[k]
            if (old != v) add(who, "state", "$k: ${old ?: "(none)"} -> $v")
        }
        for (k in before.keys) if (k !in now) add(who, "state", "$k: ${before[k]} -> (none)")
    }

    override fun projectile(projectile: Projectile) {
        val near = nearestTracked(projectile.tile.x, projectile.tile.z, projectile.tile.height) ?: return
        add(
            near,
            "proj",
            "gfx=${projectile.gfx} from=${projectile.tile.x},${projectile.tile.z} to=${projectile.targetPawn?.let { label(it) } ?: "${projectile.targetTile.x},${projectile.targetTile.z}"}" +
                " startH=${projectile.startHeight} endH=${projectile.endHeight} delay=${projectile.delay} life=${projectile.lifespan} steep=${projectile.steepness} angle=${projectile.angle}",
        )
    }

    override fun areaSound(sound: AreaSound) {
        val near = nearestTracked(sound.tile.x, sound.tile.z, sound.tile.height) ?: return
        add(near, "areasound", "id=${sound.id} at=${sound.tile.x},${sound.tile.z} radius=${sound.radius} volume=${sound.volume} delay=${sound.delay}")
    }

    private fun nearestTracked(
        x: Int,
        z: Int,
        h: Int,
    ): String? =
        synchronized(this) {
            tracked.entries.filter { it.key.tile.height == h }.minByOrNull { maxOf(Math.abs(it.key.tile.x - x), Math.abs(it.key.tile.z - z)) }
                ?.takeIf { maxOf(Math.abs(it.key.tile.x - x), Math.abs(it.key.tile.z - z)) <= NEAR }?.value
        }

    // ---- state ------------------------------------------------------------------------------------------------------------

    /** The full measurable state of [pawn], one flat key per value (a change of any key is a "state" event). */
    fun snapshot(pawn: Pawn): Map<String, String> {
        val s = LinkedHashMap<String, String>()
        val t = pawn.tile
        s["tile"] = "${t.x},${t.z},${t.height}"
        s["hp"] = "${pawn.getCurrentLifepoints()}/${pawn.getMaximumLifepoints()}"
        s["multi"] = runCatching { t.isMulti(world).toString() }.getOrDefault("?")
        s["clip"] = runCatching { world.collision.isClipped(t).toString() }.getOrDefault("?")
        Probe.internals(pawn).forEach { (k, v) -> s[k] = v }
        pawn.timers.getTimers().forEach { (key, left) -> s["timer.${KeyNames.timer(key)}"] = left.toString() }
        pawn.attr.forEachEntry { key, value -> s["attr.${KeyNames.attr(key)}"] = runCatching { render(value) }.getOrElse { "(unreadable ${it.javaClass.simpleName})" } }
        when (pawn) {
            is Player -> {
                for (i in 0 until pawn.skills.maxSkills) {
                    s["skill.$i"] = "${pawn.skills.getCurrentLevel(i)}/${pawn.skills.getMaxLevel(i)} xp=${"%.1f".format(java.util.Locale.ENGLISH, pawn.skills.getCurrentXp(i))}"
                }
                s["run"] = "%.2f".format(java.util.Locale.ENGLISH, pawn.runEnergy)
                s["prayer"] = "%.2f".format(java.util.Locale.ENGLISH, pawn.prayerPoints)
                s["weight"] = "%.2f".format(java.util.Locale.ENGLISH, pawn.weight)
                s["skull"] = pawn.skullIcon.toString()
                s["combatlevel"] = pawn.combatLevel.toString()
                s["appearance"] = pawn.appearance.toString().take(200)
                s["interfaces"] = pawn.interfaces.describeVisible()
                s["modal"] = pawn.interfaces.getModal().toString()
                pawn.varps.getAll().forEach { v -> if (v.state != 0) s["varp.${v.id}"] = v.state.toString() }
                pawn.containers.forEach { (key, container) ->
                    val name = key.name
                    for (slot in 0 until container.capacity) {
                        val item = container[slot] ?: continue
                        s["inv.$name.$slot"] = "${item.id}x${item.amount}"
                    }
                }
            }
            is Npc -> {
                s["npc"] = pawn.id.toString()
                s["stats"] = (0 until pawn.stats.nStats).joinToString(",") { "${pawn.stats.getCurrentLevel(it)}/${pawn.stats.getMaxLevel(it)}" }
                s["owner"] = label(pawn.owner)
            }
        }
        return s
    }

    private fun render(value: Any?): String =
        when (value) {
            null -> "null"
            is Reference<*> -> "ref(" + render(value.get()) + ")"
            is Pawn -> label(value)
            is Collection<*> -> "[${value.size}] " + value.take(8).joinToString(",") { render(it) }
            is Map<*, *> -> "{${value.size}} " + value.entries.take(8).joinToString(",") { "${render(it.key)}=${render(it.value)}" }
            is Array<*> -> "[${value.size}] " + value.take(8).joinToString(",") { render(it) }
            is IntArray -> value.joinToString(",", "[", "]")
            is Function<*> -> "fn"
            else -> value.toString().let { if (it.contains('@') && it.length < 80 && !it.contains(' ')) it.substringBefore('@').substringAfterLast('.') else it }.take(160)
        }

    /** A packet as text: its data-class form when it has one, else its fields by reflection (arrays shortened). */
    private fun describe(message: Message): String {
        val cls = message.javaClass
        val own = runCatching { cls.getMethod("toString").declaringClass != Any::class.java }.getOrDefault(false)
        if (own) return message.toString().take(400)
        val fields =
            cls.declaredFields.filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }.joinToString(", ") { f ->
                f.isAccessible = true
                val v = runCatching { f.get(message) }.getOrNull()
                "${f.name}=" +
                    when (v) {
                        is IntArray -> v.take(24).joinToString(",", "[", if (v.size > 24) ",..]" else "]")
                        is ByteArray -> "bytes(${v.size})"
                        is Array<*> -> v.take(12).joinToString(",", "[", if (v.size > 12) ",..]" else "]")
                        else -> v.toString().take(120)
                    }
            }
        return "${cls.simpleName}($fields)".take(400)
    }

    // ---- queries ----------------------------------------------------------------------------------------------------------

    fun of(
        who: String,
        kind: String? = null,
    ): List<Ev> = synchronized(this) { events.filter { it.who == who && (kind == null || it.kind == kind) } }

    fun since(cycle: Int): List<Ev> = synchronized(this) { events.filter { it.cycle >= cycle } }

    companion object {
        const val NEAR = 20
    }
}
