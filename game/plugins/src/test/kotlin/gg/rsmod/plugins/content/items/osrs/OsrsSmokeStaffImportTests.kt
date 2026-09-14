package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** OSRS-IMPORT batch "smokestaff": Mystic smoke staff stats, requirements and 667 staff class against the wiki. */
class OsrsSmokeStaffImportTests {
    @Test
    fun `the staff has the wiki bonuses, speed 5, the staff weapon type and 40 Attack plus 40 Magic`() {
        val root = ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile())
        val yml = root.filter { it.path("id").asInt() in Items.MYSTIC_SMOKE_STAFF..Items.MYSTIC_SMOKE_STAFF_NOTED }.associateBy { it.path("id").asInt() }
        val eq = yml.getValue(Items.MYSTIC_SMOKE_STAFF).path("equipment")
        // Wiki infobox: astab +10, aslash -1, acrush +40, amagic +14, dmagic +14, str +50, speed 5.
        assertEquals(listOf(10, -1, 40, 14), listOf("attack_stab", "attack_slash", "attack_crush", "attack_magic").map { eq.path(it).asInt() })
        assertEquals(14, eq.path("defence_magic").asInt())
        assertEquals(50, eq.path("melee_strength").asInt())
        assertEquals(5, eq.path("attack_speed").asInt())
        assertEquals(1, eq.path("weapon_type").asInt(), "staff weapon type")
        assertEquals(mapOf(6 to 40, 0 to 40), eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() })
        assertTrue(yml.getValue(Items.MYSTIC_SMOKE_STAFF_NOTED).path("equipment").isNull)
    }

    @Test
    fun `the cache gives the staff the 667 mystic staff class and a note`() {
        val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
        try {
            val definitions = DefinitionSet()
            definitions.load(store, ItemDef::class.java)
            val def = definitions.get(ItemDef::class.java, Items.MYSTIC_SMOKE_STAFF)
            assertEquals("Mystic smoke staff", def.name)
            assertEquals(28, def.params[644], "render animation (667 Mystic fire staff)")
            assertEquals(1, def.params[686], "staff style set")
            assertTrue(definitions.get(ItemDef::class.java, Items.MYSTIC_SMOKE_STAFF_NOTED).noted)
        } finally {
            store.close()
        }
    }
}
