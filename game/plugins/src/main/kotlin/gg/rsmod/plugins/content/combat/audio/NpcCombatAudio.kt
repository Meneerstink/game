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
    ) {
        val row = rowFor(npc.id) ?: return
        if (row.attack.isEmpty()) return
        val cycle = npc.world.currentCycle
        if (npc.attr[LAST_ATTACK_SOUND_CYCLE] == cycle) return
        npc.attr[LAST_ATTACK_SOUND_CYCLE] = cycle
        row.attack.forEach { sound -> play(if (sound.on == NPC) npc else target, sound, FamiliarAudio.isFamiliarNpc(npc.id)) }
    }

    fun onDefend(
        source: Pawn,
        npc: Npc,
    ) {
        val id = rowFor(npc.id)?.defend ?: return
        if (id >= 0 && source is Player) {
            if (FamiliarAudio.isFamiliarNpc(npc.id)) FamiliarAudio.play(source, id) else source.playSound(id)
        }
    }

    fun onDeath(
        killer: Player,
        npc: Npc,
    ) {
        val id = rowFor(npc.id)?.death ?: return
        if (id >= 0) {
            if (FamiliarAudio.isFamiliarNpc(npc.id)) FamiliarAudio.play(killer, id) else killer.playSound(id)
        }
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
