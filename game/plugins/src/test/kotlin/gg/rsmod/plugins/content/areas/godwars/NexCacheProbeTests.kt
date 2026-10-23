package gg.rsmod.plugins.content.areas.godwars

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.cfg.Npcs
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    /**
     * C4 round 2: Nex's intro, phase change and Zaros switch sent spotanims 3353 / 3375 / 3376 (Matrix 718 ids) that do not exist in
     * the revision-667 cache (spotanims end at 3184): the client showed nothing. The class: EVERY animation, graphic and projectile
     * id the Nex fight sends - the ANIM_ / GFX_ / PROJ_ constants of NexEncounter plus the ids written inline in NexCombatScript
     * (smoke cast 1214, the minions' barrages 386/380/374/362 and their hit graphics 388/381/375/369, sacrifice 14388, No Escape
     * knock-back 10070) - exists in BOTH caches (game server + file server, the one the client downloads).
     * Donor ids: Darkan NexArena.java:163-164 intro 6355 + 1217, Nex.java:230-231 Zaros 6326 + 1204.
     */
    @Test
    fun `C4 every Nex animation, graphic and projectile id exists in both 667 caches`() {
        val fields = gg.rsmod.plugins.content.areas.godwars.nex.NexEncounter::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .onEach { it.isAccessible = true }
        val seqs = fields.filter { it.name.startsWith("ANIM_") }.associate { it.name to it.getInt(null) } +
            mapOf("inline sacrifice" to 14388, "inline no-escape knock-back" to 10070)
        val spots = fields.filter { it.name.startsWith("GFX_") || it.name.startsWith("PROJ_") }.associate { it.name to it.getInt(null) } +
            mapOf("inline smoke cast" to 1214, "fumus barrage" to 386, "umbra barrage" to 380, "cruor barrage" to 374, "glacies barrage" to 362,
                "fumus hit" to 388, "umbra hit" to 381, "cruor hit" to 375, "glacies hit" to 369)
        assertTrue(seqs.size >= 9 && spots.size >= 20, "constants found: ${seqs.keys} ${spots.keys}")
        val missing = mutableListOf<String>()
        for (path in listOf(Paths.get("..", "..", "data", "cache"), Paths.get("..", "..", "..", "..", "file-server", "cache"))) {
            val library = CacheLibrary(path.toFile().toString())
            try {
                seqs.forEach { (name, id) -> if (library.data(20, id ushr 7, id and 0x7F) == null) missing += "$path seq $name=$id" }
                spots.forEach { (name, id) -> if (library.data(21, id ushr 8, id and 0xFF) == null) missing += "$path spotanim $name=$id" }
            } finally {
                library.close()
            }
        }
        assertTrue(missing.isEmpty(), "Nex sends ids the 667 client does not have: $missing")
    }
}
