package gg.rsmod.plugins.content.mechanics.pvp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner 2026-09-24: "als je met skull de shortcut naar de grand exchange pakt attacken de guards je maar ze doen dat al terwijl je de
 * agility shortcut nog oversteekt! fix de rootcauses vermoedelijk doen ze dit ook bij andere animaties". Root cause: Player.forceMove
 * puts the server tile on the destination at once while the client still slides the player there; every zone rule read that tile.
 * Every zone rule on a pawn must judge [gg.rsmod.game.model.entity.Pawn.zoneTile] (the tile the client shows), and every forced
 * movement must start the transit.
 */
class ZoneTransitGuardTests {
    private val zoneReads =
        listOf(
            Regex("""GuardedZones\.contains\(\s*\w+\.tile\s*\)"""),
            Regex("""isGuardedZone\(\s*(player|target|attacker|victim|pawn|other|p)\.tile\s*\)"""),
            Regex("""isPvpAllowed\(\s*(player|target|attacker|victim|pawn|other|dead|p)\.tile\s*,"""),
            Regex("""isDangerous\(\s*(player|target|attacker|victim|pawn|other|p)\.tile\s*\)"""),
        )

    @Test
    fun `zone rules on pawns read the displayed tile`() {
        val offenders =
            File("src/main/kotlin").walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".kts")) }.flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (zoneReads.any { it.containsMatchIn(line) }) "${file.name}:${i + 1}: ${line.trim()}" else null
                }.asSequence()
            }.toList()
        assertEquals(emptyList(), offenders)
    }

    @Test
    fun `every forced movement starts a transit`() {
        val player = File("../src/main/kotlin/gg/rsmod/game/model/entity/Player.kt").readText()
        val moves = Regex("""suspend fun forceMove\(""").findAll(player).count()
        assertTrue(moves >= 2)
        assertEquals(moves, Regex("""beginTransit\(""").findAll(player).count(), "each forceMove overload calls beginTransit")
    }
}
