package gg.rsmod.plugins.content.areas.tzhaar.fightcaves

import gg.rsmod.game.model.Direction
import gg.rsmod.plugins.api.cfg.Npcs
import java.io.File

/**
 * TzHaar Fight Cave wave composition and spawn rotations, loaded from the Void donor's
 * `tzhaar_fight_cave_waves.toml` (63 waves, 15 spawn rotations each, recorded from 2010-2011
 * game footage).
 */
object FightCaveWaves {
    const val DEFAULT_PATH = "./data/cfg/minigames/tzhaar_fight_cave_waves.toml"
    const val TOTAL_WAVES = 63
    const val ROTATIONS = 15

    private val npcIds = mapOf(
        "tz_kih" to Npcs.TZKIH_2734,
        "tz_kih_spawn_point" to Npcs.TZKIH_2735,
        "tz_kek" to Npcs.TZKEK_2736,
        "tz_kek_spawn_point" to Npcs.TZKEK_2737,
        "tz_kek_spawn" to Npcs.TZKEK_2738,
        "tok_xil" to Npcs.TOKXIL_2739,
        "tok_xil_spawn_point" to Npcs.TOKXIL_2740,
        "yt_mej_kot" to Npcs.YTMEJKOT,
        "yt_mej_kot_spawn_point" to Npcs.YTMEJKOT_2742,
        "ket_zek" to Npcs.KETZEK,
        "ket_zek_spawn_point" to Npcs.KETZEK_2744,
        "tztok_jad" to Npcs.TZTOKJAD,
    )

    private val waves = Array<List<Int>>(TOTAL_WAVES) { emptyList() }
    private val rotations = Array(TOTAL_WAVES) { Array<List<Direction>>(ROTATIONS) { emptyList() } }

    fun npcs(wave: Int): List<Int> = waves[wave - 1]

    fun spawns(wave: Int, rotation: Int): List<Direction> = rotations[wave - 1][rotation - 1]

    fun load(file: File = File(DEFAULT_PATH)) {
        var wave = -1
        file.forEachLine { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            if (line.startsWith("[wave_")) {
                wave = line.removePrefix("[wave_").removeSuffix("]").toInt() - 1
                return@forEachLine
            }
            val (key, value) = line.split("=", limit = 2).map { it.trim() }
            val entries = value.removePrefix("[").removeSuffix("]").split(",").map { it.trim().trim('"') }.filter { it.isNotEmpty() }
            if (key == "npcs") {
                waves[wave] = entries.map { npcIds[it] ?: error("Unknown fight cave npc '$it'") }
            } else if (key.startsWith("rotation_")) {
                val rotation = key.removePrefix("rotation_").toInt() - 1
                rotations[wave][rotation] = entries.map { Direction.valueOf(it.uppercase()) }
            }
        }
        check(waves.all { it.isNotEmpty() }) { "Fight cave wave table incomplete" }
    }
}
