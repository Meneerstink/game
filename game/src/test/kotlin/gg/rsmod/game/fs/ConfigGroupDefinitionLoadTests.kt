package gg.rsmod.game.fs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.StructDef
import gg.rsmod.game.fs.def.VarpDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * Config groups inside index 2 (varps, structs, BAS) have unused ids, and the loader once walked their file map by key
 * 0 until size, dropping the highest ids (varp 2396, the Adventurer's Log settings, owner 2026-10-04 032152).
 */
class ConfigGroupDefinitionLoadTests {
    @Test
    fun `every file of the varp, struct and bas groups is loaded`() {
        val cache = CacheLibrary(File("../data/cache").path)
        try {
            val definitions = DefinitionSet()
            listOf(VarpDef::class.java to 16, StructDef::class.java to 26, BasDef::class.java to 32).forEach { (type, group) ->
                definitions.load(cache, type)
                val ids = cache.index(2).archive(group)!!.files.keys
                ids.forEach { id -> assertNotNull("${type.simpleName} $id", definitions.getNullable(type, id)) }
                assertEquals(type.simpleName, ids.size, definitions.getCount(type))
            }
            assertNotNull(definitions.getNullable(VarpDef::class.java, 2396))
        } finally {
            cache.close()
        }
    }
}
