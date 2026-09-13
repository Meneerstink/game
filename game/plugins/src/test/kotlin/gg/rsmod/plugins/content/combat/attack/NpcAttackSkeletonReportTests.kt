package gg.rsmod.plugins.content.combat.attack

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.NpcDef
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RCV-005 skeleton audit of every Void attack animation (owner 2026-09-13: "per monstergroep met een 667-bron
 * nalopen"). Writes `build/anim-skeleton-report.json`: for each section anim that does not animate its npc's
 * rev-667 skeleton, the Void anim and skeleton, the npc's idle anim and skeleton, and the Novite 667 combat
 * definition anims (attack/defence) with whether they animate that skeleton. `tools/npc-attacks/merge-skeleton-fixes.js`
 * turns only skeleton-verified 667 anims into sourced overrides; everything else stays reported per family.
 *
 * Also records the skeletons of the standard player-humanoid anims (idle 808, punch 422, stab 386) so a
 * humanoid "mismatch" can be told apart from a real remodel.
 */
class NpcAttackSkeletonReportTests {
    @Test
    fun `write the per-npc attack animation skeleton report`() {
        val novite = HashMap<Int, Pair<Int, Int>>()
        File(NOVITE_LIST).forEachLine { line ->
            val m = Regex("""^(\d+) - \d+ (-?\d+) (-?\d+) """).find(line) ?: return@forEachLine
            novite[m.groupValues[1].toInt()] = m.groupValues[2].toInt() to m.groupValues[3].toInt()
        }
        // Second 667-era source: the 2012 Matrix NPCCombatDefinitions list already imported as data/cfg/npcs/combat-defs.json.
        val matrix = HashMap<Int, Pair<Int, Int>>()
        File(MATRIX_DEFS).forEachLine { line ->
            val id = Regex(""""id":(\d+)""").find(line)?.groupValues?.get(1)?.toInt() ?: return@forEachLine
            val attack = Regex(""""attack_anim":(-?\d+)""").find(line)?.groupValues?.get(1)?.toInt() ?: -1
            val block = Regex(""""block_anim":(-?\d+)""").find(line)?.groupValues?.get(1)?.toInt() ?: -1
            matrix[id] = attack to block
        }
        // Third source: 2009scape (rev 530) npc_configs.json, per-style melee/magic/range anims. Older than 667, so it is
        // only ever used when the skeleton check proves the anim animates the npc's rev-667 model.
        val scape = HashMap<Int, Triple<Int, Int, Int>>()
        val configType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any?>>>() {}.type
        val configs: List<Map<String, Any?>> = File(SCAPE_CONFIGS).reader().use { com.google.gson.Gson().fromJson(it, configType) }
        configs.forEach { c ->
            val id = c["id"]?.toString()?.toIntOrNull() ?: return@forEach
            val anim = { key: String -> c[key]?.toString()?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 } ?: -1 }
            scape[id] = Triple(anim("melee_animation"), anim("magic_animation"), anim("range_animation"))
        }
        val skel = { anim: Int -> AnimSkeletons.skeletonOfAnim(definitions, store, anim) }
        val entries = mutableListOf<String>()
        var mismatches = 0
        NpcAttacks.rows().sortedBy { it.id }.forEach { row ->
            val npcSkeleton = AnimSkeletons.skeletonOfNpc(definitions, store, row.id)
            val basId = definitions.getNullable(NpcDef::class.java, row.id)?.basId ?: -1
            val idle = if (basId >= 0) definitions.getNullable(BasDef::class.java, basId)?.idleAnimation() ?: -1 else -1
            row.attacks.filter { it.anim >= 0 && !AnimSkeletons.fits(definitions, store, row.id, it.anim) }.forEach { a ->
                mismatches++
                val (nAttack, nDefence) = novite[row.id] ?: (-1 to -1)
                val (mAttack, mBlock) = matrix[row.id] ?: (-1 to -1)
                val offense = a.hits.firstOrNull()?.offense ?: ""
                val (sMelee, sMagic, sRange) = scape[row.id] ?: Triple(-1, -1, -1)
                val styled = when (offense) { "range" -> sRange; "magic" -> sMagic; else -> sMelee }
                val sAnim = if (styled >= 0) styled else sMelee
                val scapeFields =
                    """"offense":${json(offense)},"scape_anim":$sAnim,"scape_anim_skeleton":${skel(sAnim)},"scape_anim_fits":${sAnim >= 0 && skel(sAnim) == npcSkeleton},"""
                val matrixFields =
                    """"matrix_attack":$mAttack,"matrix_attack_skeleton":${skel(mAttack)},"matrix_attack_fits":${mAttack >= 0 && skel(mAttack) == npcSkeleton},""" +
                        """"matrix_block":$mBlock,"matrix_block_skeleton":${skel(mBlock)},"""
                entries +=
                    """{"id":${row.id},"name":${json(row.name)},"combat_def":${json(row.combatDef)},"attack":${json(a.id)},""" +
                    """"void_anim":${a.anim},"void_skeleton":${skel(a.anim)},"npc_skeleton":$npcSkeleton,"idle_anim":$idle,""" +
                    """"novite_attack":$nAttack,"novite_attack_skeleton":${skel(nAttack)},"novite_attack_fits":${nAttack >= 0 && skel(nAttack) == npcSkeleton},""" +
                    matrixFields + scapeFields + externalFields(row.id, npcSkeleton, skel) + voidNamedFields(npcSkeleton, skel) +
                    """"novite_defence":$nDefence,"novite_defence_skeleton":${skel(nDefence)}}"""
            }
        }
        val humanoid = listOf(808, 422, 386, 390, 451).joinToString(",") { """"$it":${skel(it)}""" }
        val out = File("build/anim-skeleton-report.json")
        out.parentFile.mkdirs()
        out.writeText("""{"humanoid_skeletons":{$humanoid},"mismatches":[""" + entries.joinToString(",\n") + "]}\n")
        println("NpcAttackSkeletonReport: $mismatches mismatches written to ${out.absolutePath}")
        assertTrue(out.length() > 0)
    }

    private fun json(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * Fifth source: Void's own animation names (every `*.anims.toml`, not only anims used by combat sections). Returns
     * the Void anims whose name contains "attack" and that animate [npcSkeleton], as "name=id".
     */
    private fun voidNamedFields(
        npcSkeleton: Int,
        skel: (Int) -> Int,
    ): String {
        val fits = if (npcSkeleton < 0) emptyList() else voidAttackAnims.filter { (_, id) -> skel(id) == npcSkeleton }
        return """"void_named_attack":[${fits.joinToString(",") { (name, id) -> json("$name=$id") }}],"""
    }

    /** Fourth source: the first public Matrix-family list candidate (external-anim-candidates.json) that fits the skeleton. */
    private fun externalFields(
        npcId: Int,
        npcSkeleton: Int,
        skel: (Int) -> Int,
    ): String {
        val fit = externalCandidates[npcId]?.firstOrNull { npcSkeleton >= 0 && skel(it.first) == npcSkeleton }
        return """"external_anim":${fit?.first ?: -1},"external_source":${json(fit?.second ?: "")},"external_fits":${fit != null},"""
    }

    companion object {
        private const val NOVITE_LIST = "C:/RSPS/Donors/Novite/data/npcs/unpackedCombatDefinitionsList.txt"
        private const val MATRIX_DEFS = "../../data/cfg/npcs/combat-defs.json"
        private const val SCAPE_CONFIGS = "C:/RSPS/import-source/donors/2009scape/Server/data/configs/npc_configs.json"
        private const val EXTERNAL_CANDIDATES = "C:/RSPS/tools/npc-attacks/external-anim-candidates.json"
        private const val VOID_DATA = "C:/RSPS/Donors/void/data"

        /** Every Void animation whose name contains "attack", as (name, id), read from all `*.anims.toml`. */
        private val voidAttackAnims: List<Pair<String, Int>> by lazy {
            val result = mutableListOf<Pair<String, Int>>()
            File(VOID_DATA).walkTopDown().filter { it.isFile && it.name.endsWith(".anims.toml") }.forEach { file ->
                var name: String? = null
                file.forEachLine { raw ->
                    val line = raw.substringBefore('#').trim()
                    val header = Regex("""^\[([a-z0-9_]+)]$""").find(line)
                    if (header != null) {
                        name = header.groupValues[1]
                    } else {
                        val id = Regex("""^id\s*=\s*(\d+)$""").find(line)?.groupValues?.get(1)?.toInt()
                        val current = name
                        if (id != null && current != null && current.contains("attack")) result += current to id
                    }
                }
            }
            result
        }

        /** npc id -> (anim, source) candidates from tools/npc-attacks/collect-external-candidates.js; empty when absent. */
        private val externalCandidates: Map<Int, List<Pair<Int, String>>> by lazy {
            val file = File(EXTERNAL_CANDIDATES)
            if (!file.exists()) return@lazy emptyMap()
            val type = object : com.google.gson.reflect.TypeToken<Map<String, List<Map<String, Any?>>>>() {}.type
            val text = file.readText().removePrefix("\uFEFF")
            val parsed: Map<String, List<Map<String, Any?>>> = com.google.gson.Gson().fromJson(text, type) ?: emptyMap()
            parsed.entries.mapNotNull { (key, list) ->
                val id = key.toIntOrNull() ?: return@mapNotNull null
                id to list.mapNotNull { c ->
                    val anim = c["anim"]?.toString()?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
                    anim to (c["source"]?.toString() ?: "")
                }
            }.toMap()
        }
        private val definitions = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun load() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            NpcAttacks.load(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile())
        }

        @AfterClass
        @JvmStatic
        fun close() {
            store.close()
        }
    }
}
