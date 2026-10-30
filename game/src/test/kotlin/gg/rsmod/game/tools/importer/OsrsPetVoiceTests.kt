package gg.rsmod.game.tools.importer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class OsrsPetVoiceTests {
    @Test
    fun `voice generator retains Zilyanas sourced shield and matches every runtime table row`() {
        val zilyana = OsrsPetImportTool.VOICES.single { it.pet == "Pet Zilyana" }
        assertEquals(3886, zilyana.osrsSynth)
        assertEquals("godwars_saradomin_avatar_shield", zilyana.name)
        val rows = File("../data/cfg/pets/pet_voices.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertEquals(OsrsPetImportTool.VOICES.size, rows.size)
        for (voice in OsrsPetImportTool.VOICES) {
            val row = rows.single { it[0] == voice.pet && it[1] == voice.variant && it[5] == voice.line }
            assertEquals(voice.osrsSynth, row[2].toInt(), voice.pet)
            assertEquals(voice.name, row[4], voice.pet)
            assertEquals(voice.source, row[6], voice.pet)
            if (voice.pet == "Pet Zilyana") assertEquals(10280, row[3].toInt())
        }
    }
}
