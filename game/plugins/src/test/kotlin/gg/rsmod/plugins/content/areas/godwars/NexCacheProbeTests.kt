package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.cfg.Npcs
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * RCV-011 Q-043-c evidence probe: the revision-667 definitions of Nex's forms and her four mages (name, overhead
 * headIcon, transforms, options). Novite switches Nex between 13447/13448/13449 for deflect prayers; the cache's own
 * headIcon per form decides what each form really shows before any port. Read-only.
 */
class NexCacheProbeTests {
    @Test
    fun `nex forms and mages exist in the 667 cache with their overhead icons`() {
        val library = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val defs = DefinitionSet().also { it.loadAll(library) }
            val ids = listOf(Npcs.NEX, Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX_13450, Npcs.FUMUS, Npcs.UMBRA, Npcs.CRUOR, Npcs.GLACIES)
            ids.forEach { id ->
                val def = assertNotNull(defs.getNullable(NpcDef::class.java, id), "npc $id missing")
                println(
                    "NEX_PROBE id=$id name='${def.name}' headIcon=${def.headIcon} combat=${def.combatLevel} " +
                        "transforms=${def.transforms?.toList()} varbit=${def.varbit} varp=${def.varp} options=${def.options.toList()}",
                )
            }
            listOf(Npcs.NEX, Npcs.NEX_13448, Npcs.NEX_13449, Npcs.NEX_13450).forEach { assertEquals("Nex", defs.get(NpcDef::class.java, it).name) }
            assertEquals(listOf("Fumus", "Umbra", "Cruor", "Glacies"), listOf(Npcs.FUMUS, Npcs.UMBRA, Npcs.CRUOR, Npcs.GLACIES).map { defs.get(NpcDef::class.java, it).name })
        } finally {
            library.close()
        }
    }
}
