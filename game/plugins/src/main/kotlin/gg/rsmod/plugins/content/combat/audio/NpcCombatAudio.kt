package gg.rsmod.plugins.content.combat.audio

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.content.skills.summoning.FamiliarAudio
import java.io.File
import java.io.FileReader

/**
 * RCV-005 root cause: the shared NPC combat path had no audio data or dispatch at all
 * (`NpcCombatDef` carries no sound field), so only npcs with a hand-written table - the God Wars
 * bodyguard overlay - or their own script ever made a sound.
 *
 * This is the single table and the single dispatch for every npc. The table
 * (`data/cfg/npcs/combat-sounds.json`) is generated from Void's rev-667-era combat, sound and npc
 * definitions by `C:\RSPS\tools\npc-combat-sounds\generate.js`, and the dispatch follows Void:
 * - attack: `Attack.kt` - attacker sounds with a radius as an area sound at the npc, target sounds
 *   direct to a player target (or as an area sound at the target when they carry a radius);
 * - defend: `Block.kt` - the attacking player hears the npc's defend sound;
 * - death: `NPCDeath.kt` - the killing player hears the npc's death sound.
 */
object NpcCombatAudio {
    const val DEFAULT_PATH = "./data/cfg/npcs/combat-sounds.json"

    class Sound(
        val id: Int = -1,
        val radius: Int = 0,
        val delay: Int = 0,
        val on: String = TARGET,
    )

    class Row(
        val id: Int = -1,
        val name: String = "",
        @SerializedName("void_key") val voidKey: String = "",
        @SerializedName("combat_def") val combatDef: String = "",
        val attack: List<Sound> = emptyList(),
        @SerializedName("attack_blocked") val attackBlocked: String? = null,
        val defend: Int = -1,
        val death: Int = -1,
    )

    private const val TARGET = "target"
    private const val NPC = "npc"

    /** Void sends an area sound at most 15 tiles wide ([AreaSound] enforces the same bound). */
    private const val MAX_RADIUS = 15

    private val LAST_ATTACK_SOUND_CYCLE = AttributeKey<Int>()

    @Volatile
    private var rows: Map<Int, Row> = emptyMap()

    fun load(file: File = File(DEFAULT_PATH)): Int {
        val loaded: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        rows = loaded.associateBy { it.id }
        return rows.size
    }

    /**
     * Owner live retest 2026-09-19 ("they have no sounds"): the table is keyed by npc id and generated from Void's rev-667 npcs,
     * so every OSRS-imported npc (local ids 14000+: Deadman guards, breach monsters) had no row and fought silently. Imported npcs
     * get a row in code ([register]) or reuse the row of the rev-667 npc they are a version of ([alias]); both survive a [load]
     * in either order because they are resolved at lookup time.
     */
    private val registered = java.util.concurrent.ConcurrentHashMap<Int, Row>()
    private val aliases = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun register(row: Row) {
        registered[row.id] = row
    }

    fun alias(
        npcId: Int,
        soundsOfNpcId: Int,
    ) {
        aliases[npcId] = soundsOfNpcId
    }

    fun rowFor(npcId: Int): Row? = rows[npcId] ?: registered[npcId] ?: aliases[npcId]?.let { rows[it] }

    /**
     * True when the shared table plays a death sound for [npcId]. A hand-written death block that
     * still plays its own sound must check this, so the npc never hears two death sounds.
     */
    fun hasDeathSound(npcId: Int): Boolean = (rowFor(npcId)?.death ?: -1) >= 0

    fun rows(): Collection<Row> = rows.values

    /**
     * Called from the shared hit pipeline whenever an npc deals a hit. A multi-target or multi-hit
     * attack registers several hits in one tick; the attack sound is played once per tick.
     */
    fun onAttack(
        npc: Npc,
        target: Pawn,
        anim: Int = npc.combatDef.attackAnimation,
    ) {
        val traced = gg.rsmod.game.model.BreachTrace.watches(npc.id)
        val row = rowFor(npc.id)
        val skip =
            when {
                row == null -> "no-sound-row"
                row.attack.isEmpty() -> "row-has-no-attack-sound"
                // One source per sound (breach audit 2026-09-27, A3): an animation that carries its own audible frame sounds already
                // plays them for everyone nearby, so the table's cue on top of it was a second copy.
                hasAudibleFrameSounds(npc, anim) -> "anim-$anim-has-frame-sounds"
                npc.attr[LAST_ATTACK_SOUND_CYCLE] == npc.world.currentCycle -> "already-this-cycle"
                else -> null
            }
        if (traced) {
            gg.rsmod.game.model.BreachTrace.log("sound.attack") { "npc=${npc.id}#${npc.index} anim=$anim sounds=${row?.attack?.map { it.id }} result=${skip ?: "played"}" }
        }
        if (row == null || skip != null) return
        val cycle = npc.world.currentCycle
        npc.attr[LAST_ATTACK_SOUND_CYCLE] = cycle
        val area = areaRadius(npc)
        row.attack.forEach { sound ->
            if (area > 0 && sound.radius == 0) {
                npc.world.spawn(AreaSound(tile = npc.tile, id = sound.id, radius = area, volume = 1, delay = sound.delay))
            } else {
                play(if (sound.on == NPC) npc else target, sound, FamiliarAudio.isFamiliarNpc(npc.id))
            }
        }
    }

    /** True when [anim] has a frame sound with a radius ([gg.rsmod.game.fs.def.AnimDef.hasAudibleFrameSounds]). */
    fun hasAudibleFrameSounds(
        npc: Npc,
        anim: Int,
    ): Boolean =
        anim >= 0 && runCatching { npc.world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, anim)?.hasAudibleFrameSounds == true }.getOrDefault(false)

    /**
     * Breach monsters are OSRS npcs whose attack, hit and death sounds every player nearby hears (breach audit 2026-09-27, A5):
     * their radius-0 cues go out as area sounds at the monster ([gg.rsmod.plugins.content.combat.attack.NpcAttacks.BREACH_SOUND_RADIUS]).
     * Every other npc keeps the rev-667 Void rule (target / attacker / killer only).
     */
    private fun areaRadius(npc: Npc): Int =
        if (gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)) gg.rsmod.plugins.content.combat.attack.NpcAttacks.BREACH_SOUND_RADIUS else 0

    private fun playAt(
        npc: Npc,
        listener: Player?,
        id: Int,
    ) {
        val area = areaRadius(npc)
        when {
            area > 0 -> npc.world.spawn(AreaSound(tile = npc.tile, id = id, radius = area, volume = 1))
            listener == null -> Unit
            FamiliarAudio.isFamiliarNpc(npc.id) -> FamiliarAudio.play(listener, id)
            else -> listener.playSound(id)
        }
    }

    fun onDefend(
        source: Pawn,
        npc: Npc,
    ) {
        // Owner live test 2026-09-28 (Commander Zilyana, K'ril Tsutsaroth silent when hit): their hit sound lives only on the
        // block animation's frames, and an attack animation still running skips the block (Pawn.isAnimationProtected). The
        // block's frame sound only counts while that animation really plays; when it is skipped the server sends it itself.
        val blockSound =
            npc.combatDef.blockAnimation.takeIf { it >= 0 }?.let { anim ->
                runCatching { npc.world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, anim)?.firstAudibleFrameSound }.getOrNull()
            } ?: -1
        val skippedBlock = blockSound >= 0 && npc.isAnimationProtected()
        val id = (rowFor(npc.id)?.defend ?: -1).takeIf { it >= 0 } ?: if (skippedBlock) blockSound else -1
        val frame = id >= 0 && hasAudibleFrameSounds(npc, npc.combatDef.blockAnimation) && !skippedBlock
        if (gg.rsmod.game.model.BreachTrace.watches(npc.id)) {
            gg.rsmod.game.model.BreachTrace.log("sound.defend") {
                "npc=${npc.id}#${npc.index} id=$id blockAnim=${npc.combatDef.blockAnimation} result=${if (id < 0) "no-defend-sound" else if (frame) "block-anim-has-frame-sounds" else "played"}"
            }
        }
        if (id < 0 || frame) return
        if (source is Player || areaRadius(npc) > 0) playAt(npc, source as? Player, id)
    }

    fun onDeath(
        killer: Player,
        npc: Npc,
    ) {
        val id = rowFor(npc.id)?.death ?: -1
        val frame = id >= 0 && npc.combatDef.deathAnimation.any { hasAudibleFrameSounds(npc, it) }
        if (gg.rsmod.game.model.BreachTrace.watches(npc.id)) {
            gg.rsmod.game.model.BreachTrace.log("sound.death") {
                "npc=${npc.id}#${npc.index} id=$id deathAnims=${npc.combatDef.deathAnimation} killer=${killer.username} " +
                    "result=${if (id < 0) "no-death-sound" else if (frame) "death-anim-has-frame-sounds" else "played"}"
            }
        }
        if (id < 0 || frame) return
        playAt(npc, killer, id)
    }

    private fun play(
        at: Pawn,
        sound: Sound,
        familiar: Boolean,
    ) {
        if (sound.id < 0) return
        if (sound.radius > 0) {
            at.world.spawn(AreaSound(tile = at.tile, id = sound.id, radius = sound.radius.coerceAtMost(MAX_RADIUS), volume = 1, delay = sound.delay, playbackVolume = if (familiar) FamiliarAudio.SERVER_SOUND_VOLUME else 255))
        } else if (at is Player) {
            if (familiar) FamiliarAudio.play(at, sound.id, delay = sound.delay) else at.playSound(sound.id, delay = sound.delay)
        }
    }
}
