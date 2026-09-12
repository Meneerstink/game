package gg.rsmod.plugins.content.areas.godwars.nex

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarp


/**
 * Nex (Ancient Prison) encounter manager. One fight per world, started when the first player enters
 * the arena and torn down once the arena is empty.
 *
 * Phase model (2011): Nex has 3,000 runtime lifepoints, split in five phases. The hand-written
 * combat DSL converts the donor's historical x10 values at the definition boundary. When her
 * life points drop to
 * the next threshold she calls a minion ("Fumus, don't fail me!") and becomes immune until that
 * minion is killed, after which she switches element. In the final phase she shouts
 * "NOW, THE POWER OF ZAROS!", regains 600 lifepoints and fights with Soul Split, Deflect Melee
 * and Turmoil until she dies, unleashing Wrath around her.
 *
 * Ids, tiles, animations and graphics ported from the Novite donor (ZarosGodwars, Nex, NexCombat,
 * Fumus/Umbra/Cruor/GlaciesCombat) and the Matrix 718 combat definitions.
 */
object NexEncounter {
    val ARENA_X = 2909..2939
    val ARENA_Z = 5186..5220
    val ENTRY_TILE = Tile(2911, 5204, 0)
    val CENTRE = Tile(2924, 5202, 0)
    val NEX_SPAWN = Tile(2924, 5202, 0)

    /** North, east, south, west — "There is... NO ESCAPE!" rush start points. */
    val NO_ESCAPE_TILES = listOf(Tile(2924, 5213, 0), Tile(2934, 5202, 0), Tile(2924, 5192, 0), Tile(2913, 5202, 0))

    /** Fumus, Umbra, Cruor, Glacies spawn corners. */
    val MINION_SPAWNS = listOf(Tile(2913, 5215, 0), Tile(2937, 5215, 0), Tile(2937, 5191, 0), Tile(2913, 5191, 0))
    val MINION_IDS = intArrayOf(Npcs.FUMUS, Npcs.UMBRA, Npcs.CRUOR, Npcs.GLACIES)

    // The DSL divides historical hand-written x10 source values by ten before runtime.
    const val MAX_LIFEPOINTS = 3000
    const val PHASE_LIFEPOINTS = 600
    const val ZAROS_HEAL = 600
    const val SOUND_START = 3295
    const val SOUND_DEATH = 3323
    const val SOUND_VIRUS = 3296
    const val SOUND_BLOOD_SACRIFICE = 3293
    const val SOUND_NO_ESCAPE_START = 3294
    const val SOUND_NO_ESCAPE_HIT = 3292
    const val SOUND_SHADOW_TRAPS = 3314
    const val SOUND_DARKNESS = 3322
    const val SOUND_SIPHON = 3317
    const val SOUND_ICE_PRISON = 3308
    const val SOUND_ICE_BARRICADE = 3316
    const val HIT_SOUND_THRESHOLD = 15
    val HIT_SOUNDS = intArrayOf(3326, 3324, 3320, 3319, 3318, 3315, 3311, 3309, 3305, 3301, 3300, 3297)

    const val ANIM_START = 6355
    const val ANIM_CALL_MINION = 6987
    const val ANIM_MAGIC = 6986
    const val ANIM_SPECIAL = 6984
    const val ANIM_SIPHON = 6948
    const val ANIM_NO_ESCAPE = 6321
    const val ANIM_DEATH = 6951
    const val GFX_START = 3353
    const val GFX_MAGIC_CAST = 3375
    const val GFX_ZAROS = 3376
    const val GFX_WRATH = 2259
    const val GFX_DARKNESS = 1217
    const val GFX_ICE_PRISON = 1215
    const val GFX_SIPHON = 1201
    const val GFX_NO_ESCAPE = 1216
    const val GFX_SHADOW_TRAP = 383
    const val GFX_BLOOD_HIT = 376
    const val GFX_ICE_HIT = 369
    const val GFX_REAVER_SPAWN = 1315
    const val GFX_PLAYER_SACRIFICE = 2767
    const val PROJ_MAGIC = 362
    const val PROJ_SHADOW = 380
    const val PROJ_BLOOD = 374
    const val PROJ_SMOKE_VIRUS = 471
    const val PROJ_MINION_POWER = 2244
    const val PROJ_WRATH = 2261
    const val PROJ_WRATH_CENTRE = 2260
    const val DARKNESS_VARP = 1435

    const val DEATH_DELAY = 6
    const val RESTART_DELAY = 100

    val INFECTED = AttributeKey<Int>()
    val SACRIFICE_TARGET = AttributeKey<Boolean>()

    enum class Phase(val minionName: String, val callout: String, val startSound: Int, val transitionSound: Int) {
        SMOKE("Fumus", "Fill my soul with smoke!", 3325, 3310),
        SHADOW("Umbra", "Darken my shadow!", 3313, 3307),
        BLOOD("Cruor", "Flood my lungs with blood!", 3299, 3298),
        ICE("Glacies", "Infuse me with the power of ice!", 3304, 3327),
        ZAROS("", "NOW, THE POWER OF ZAROS!", -1, 3312),
    }

    lateinit var world: World

    var nex: Npc? = null
        private set
    var phase = Phase.SMOKE
        private set
    val minions = arrayOfNulls<Npc>(4)
    val reavers = ArrayList<Npc>()

    /** True while Nex is waiting for the current phase's minion to die. */
    var awaitingMinion = false
        private set

    /** True from the first "AT LAST!" until Nex begins attacking. */
    var intro = false
        private set
    var firstStageAttack = false
        private set
    var fightActive = false
        private set
    var restartCountdown = -1
    var siphoning = false
    var darkness = false
        private set

    fun inArena(tile: Tile): Boolean = tile.height == 0 && tile.x in ARENA_X && tile.z in ARENA_Z

    fun players(): List<Player> {
        val list = ArrayList<Player>()
        world.players.forEach { if (inArena(it.tile) && it.isAlive()) list.add(it) }
        return list
    }

    /** Called from the arena plugin when a player enters; starts a fight if none is running. */
    fun playerEntered(player: Player) {
        if (darkness) player.setVarp(DARKNESS_VARP, 100)
        if (!fightActive) {
            start()
        }
    }

    fun playerLeft(player: Player) {
        player.setVarp(DARKNESS_VARP, 255)
        player.attr.remove(INFECTED)
        player.attr.remove(SACRIFICE_TARGET)
        if (players().isEmpty()) {
            end()
        }
    }

    /** Processes one game tick of encounter bookkeeping; bound to a world timer in the plugin. */
    fun cycle() {
        if (restartCountdown > 0) {
            restartCountdown--
            if (restartCountdown == 0) {
                if (players().isNotEmpty()) start() else end()
            }
        }
        if (!fightActive) return
        if (players().isEmpty()) {
            end()
            return
        }
        val boss = nex ?: return
        if (!awaitingMinion && !intro && boss.isAlive()) {
            checkPhaseThreshold(boss)
        }
        cycleInfection()
    }

    fun start() {
        if (fightActive) return
        fightActive = true
        intro = true
        firstStageAttack = false
        awaitingMinion = false
        siphoning = false
        phase = Phase.SMOKE
        restartCountdown = -1
        val boss = Npc(Npcs.NEX, Tile(NEX_SPAWN), world)
        boss.respawns = false
        boss.walkRadius = 0
        boss.hitModifier = { hit -> modifyHit(boss, hit) }
        world.spawn(boss)
        nex = boss
        playSound(boss, SOUND_START)
        world.queue {
            boss.forceChat("AT LAST!")
            boss.animate(ANIM_START)
            boss.graphic(GFX_START)
            wait(5)
            for (index in MINION_IDS.indices) {
                if (!fightActive) return@queue
                val minion = Npc(MINION_IDS[index], Tile(MINION_SPAWNS[index]), world)
                minion.respawns = false
                minion.walkRadius = 0
                minion.hitModifier = { hit -> if (awaitingMinion.not() || minionIndex(minion) != phase.ordinal) hit.hitmarks.forEach { it.damage = 0 } }
                world.spawn(minion)
                minions[index] = minion
                val phase = Phase.values()[index]
                boss.forceChat("${phase.minionName}!")
                boss.animate(ANIM_CALL_MINION)
                minion.animate(ANIM_CALL_MINION)
                playSound(boss, phase.startSound)
                world.spawn(boss.createProjectile(minion, PROJ_MINION_POWER, ProjectileType.MAGIC))
                wait(5)
            }
            if (!fightActive) return@queue
            boss.forceChat(Phase.SMOKE.callout)
            playSound(boss, Phase.SMOKE.transitionSound)
            firstStageAttack = true
            intro = false
            engageRandomPlayer(boss)
        }
    }

    fun end() {
        fightActive = false
        intro = false
        firstStageAttack = false
        awaitingMinion = false
        siphoning = false
        restartCountdown = -1
        nex?.let { if (it.isSpawned()) world.remove(it) }
        nex = null
        minions.forEachIndexed { i, m -> m?.let { if (it.isSpawned()) world.remove(it) }; minions[i] = null }
        clearReavers()
        applyDarkness(false)
    }

    fun minionIndex(npc: Npc): Int = minions.indexOfFirst { it === npc }

    fun engageRandomPlayer(boss: Npc) {
        val target = players().randomOrNull() ?: return
        boss.attack(target)
    }

    /**
     * Nex cannot be damaged below the next phase threshold until the corresponding minion is
     * dead, and every hit is capped at 500 (2011 damage cap) outside the Zaros phase.
     */
    private fun modifyHit(boss: Npc, hit: gg.rsmod.game.model.Hit) {
        val damage = hit.hitmarks.sumOf { it.damage }
        if (shouldPlayHitSound(damage)) {
            playSound(boss, HIT_SOUNDS[boss.world.random(HIT_SOUNDS.lastIndex)])
        }
        if (intro) {
            hit.hitmarks.forEach { it.damage = 0 }
            return
        }
        if (siphoning) {
            var total = 0
            hit.hitmarks.forEach { total += it.damage; it.damage = 0 }
            if (total > 0) {
                heal(boss, total)
            }
            return
        }
        val floor = phaseFloor(phase)
        hit.hitmarks.forEach { mark ->
            if (phase != Phase.ZAROS && mark.damage > 500) mark.damage = 500
            if (awaitingMinion) {
                mark.damage = 0
            } else {
                val remaining = boss.getCurrentLifepoints() - floor
                if (mark.damage > remaining) mark.damage = remaining.coerceAtLeast(0)
            }
        }
    }

    private fun checkPhaseThreshold(boss: Npc) {
        if (phase == Phase.ZAROS) return
        val floor = phaseFloor(phase)
        if (boss.getCurrentLifepoints() <= floor) {
            awaitingMinion = true
            boss.forceChat("${phase.minionName}, don't fail me!")
            val minion = minions[phase.ordinal]
            if (minion == null || !minion.isSpawned() || minion.isDead()) {
                onMinionDeath(phase.ordinal)
            } else {
                players().randomOrNull()?.let { minion.attack(it) }
            }
        }
    }

    /** Called from the plugin when Fumus/Umbra/Cruor/Glacies die. */
    fun onMinionDeath(index: Int) {
        minions[index] = null
        val boss = nex ?: return
        if (!fightActive || boss.isDead()) return
        if (index != phase.ordinal) return
        awaitingMinion = false
        when (phase) {
            Phase.SMOKE -> players().forEach { it.attr.remove(INFECTED) }
            Phase.SHADOW -> applyDarkness(false)
            Phase.BLOOD -> clearReavers()
            else -> {}
        }
        phase = Phase.values()[phase.ordinal + 1]
        firstStageAttack = true
        boss.forceChat(phase.callout)
        playSound(boss, phase.transitionSound)
        boss.animate(ANIM_MAGIC)
        boss.graphic(GFX_MAGIC_CAST)
        if (phase == Phase.ZAROS) {
            boss.graphic(GFX_ZAROS)
            heal(boss, ZAROS_HEAL)
        }
        world.queue {
            wait(2)
            if (fightActive && boss.isAlive() && boss.getCombatTargetOrNull() == null) engageRandomPlayer(boss)
        }
    }

    fun heal(boss: Npc, amount: Int) {
        val max = MAX_LIFEPOINTS
        boss.setCurrentLifepoints((boss.getCurrentLifepoints() + amount).coerceAtMost(max))
    }

    fun phaseFloor(phase: Phase): Int =
        if (phase == Phase.ZAROS) 0 else MAX_LIFEPOINTS - PHASE_LIFEPOINTS * (phase.ordinal + 1)

    private fun playSound(npc: Npc, id: Int) {
        if (id >= 0) npc.world.spawn(AreaSound(tile = npc.tile, id = id, radius = 10, volume = 1))
    }

    internal fun playEncounterSound(npc: Npc, id: Int) = playSound(npc, id)

    fun consumeFirstStageAttack(): Boolean {
        if (!firstStageAttack) return false
        firstStageAttack = false
        return true
    }

    fun shouldPlayHitSound(damage: Int): Boolean = damage >= HIT_SOUND_THRESHOLD

    /** Nex death: Wrath burst, drops handled by the definition plugin, restart after a minute. */
    fun onNexDeath(boss: Npc) {
        if (!fightActive) return
        playSound(boss, SOUND_DEATH)
        boss.forceChat("Taste my wrath!")
        boss.graphic(GFX_WRATH)
        val centre = boss.getCentreTile()
        for (dx in -2..2 step 2) {
            for (dz in -2..2 step 2) {
                if (dx == 0 && dz == 0) continue
                world.spawn(boss.createProjectile(centre.transform(dx * 2, dz * 2), PROJ_WRATH, ProjectileType.MAGIC))
            }
        }
        players().filter { it.tile.isWithinRadius(centre, 10) }.forEach { player ->
            player.hit(world.random(60), HitType.REGULAR_HIT, 1)
        }
        minions.forEachIndexed { i, m -> m?.let { if (it.isSpawned()) world.remove(it) }; minions[i] = null }
        clearReavers()
        applyDarkness(false)
        fightActive = false
        awaitingMinion = false
        siphoning = false
        nex = null
        restartCountdown = RESTART_DELAY
    }

    /* ----------------------------- smoke phase: virus ----------------------------- */

    fun infect(player: Player) {
        player.attr[INFECTED] = 20
    }

    private fun cycleInfection() {
        if (phase != Phase.SMOKE) return
        for (player in players()) {
            val remaining = player.attr[INFECTED] ?: continue
            if (remaining <= 0) {
                player.attr.remove(INFECTED)
                continue
            }
            player.attr[INFECTED] = remaining - 1
            if (remaining % 4 == 0) {
                player.forceChat("*Cough*")
                player.hit(world.random(1..3), HitType.REGULAR_HIT)
                players().filter { it !== player && it.tile.isWithinRadius(player.tile, 1) && it.attr[INFECTED] == null }
                    .forEach { infect(it) }
            }
        }
    }

    /* ----------------------------- shadow phase: darkness ----------------------------- */

    fun applyDarkness(enabled: Boolean) {
        darkness = enabled
        world.players.forEach { if (inArena(it.tile)) it.setVarp(DARKNESS_VARP, if (enabled) 100 else 255) }
    }

    /* ----------------------------- blood phase: reavers ----------------------------- */

    fun spawnReavers(boss: Npc) {
        val count = 2 + world.random(2)
        repeat(count) {
            val tile = world.findRandomTileAround(boss.getCentreTile(), radius = 3) ?: return@repeat
            val reaver = Npc(13458, tile, world)
            reaver.respawns = false
            reaver.walkRadius = 3
            reaver.graphic(GFX_REAVER_SPAWN)
            if (world.spawn(reaver)) {
                reavers.add(reaver)
                players().randomOrNull()?.let { reaver.attack(it) }
            }
        }
    }

    fun clearReavers() {
        reavers.forEach { if (it.isSpawned()) world.remove(it) }
        reavers.clear()
    }

    /* ----------------------------- ice phase: prison ----------------------------- */

    fun icePrison(boss: Npc) {
        val base = boss.getCentreTile()
        world.queue {
            wait(5)
            if (!fightActive) return@queue
            for (dz in -2..2) {
                for (dx in -2..2) {
                    if (dx in -1..1 && dz in -1..1) continue
                    val tile = base.transform(dx, dz)
                    if (world.collision.isClipped(tile)) continue
                    players().filter { it.tile == tile }.forEach { player ->
                        player.animate(1113)
                        player.hit(world.random(35), HitType.REGULAR_HIT)
                        player.message("The icicle spikes you to the spot!")
                        player.message("You've been injured and can't use protection prayers!")
                        player.stopMovement()
                        NexPrayer.disableProtection(player, 12)
                    }
                    world.spawnTemporaryObject(DynamicObject(Objs.STALAGMITE_57263, 10, 0, tile), 12)
                }
            }
        }
    }
}

/** Extension mirroring PawnExt.getCombatTarget without a plugin-scope import cycle. */
fun Npc.getCombatTargetOrNull(): Pawn? = attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get()

typealias Pawn = gg.rsmod.game.model.entity.Pawn
