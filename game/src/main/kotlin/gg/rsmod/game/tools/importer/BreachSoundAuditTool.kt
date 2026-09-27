package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import java.io.File

/**
 * Breach conformance 2026-09-27 (owner: "are there walk sounds? and other sounds for some breach monsters?"). Read-only.
 *
 * OSRS npcs have no sound fields of their own (no ready / walk / run sound like rev-667 NPCTypes): every footstep, idle, attack or
 * death sound is a frame sound of the sequence the npc plays. For every breach npc (OsrsNpcImportTool batch deadman-breach) this
 * lists each OSRS movement sequence (stand, walk, turns, run) and every combat sequence of the breach import batch (EXTRA_SEQS),
 * with the frame sounds the OSRS sequence has and whether our imported copy kept them. Output lines:
 *   `SEQ <role> osrs=<id> local=<id> osrsSounds=<n> localSounds=<yes/no> <OK|LOST|NONE>`
 *
 * Usage: java ... BreachSoundAuditTool
 */
object BreachSoundAuditTool {
    @JvmStatic
    fun main(args: Array<String>) {
        val ids = OsrsFxImportTool.existingFx(File(OsrsItemImportTool.ASSET_MAP))
        val library = CacheLibrary(OsrsItemImportTool.TARGETS[0])
        val definitions = DefinitionSet()
        definitions.load(library, AnimDef::class.java)
        var lost = 0
        var withSound = 0
        ModernCacheReader(File(OsrsItemImportTool.SOURCE_CACHE)).use { reader ->
            val npcFiles = reader.files(ModernCacheReader.INDEX_CONFIG, OsrsNpcProbeTool.CONFIG_GROUP_NPC)
            fun audit(role: String, osrsSeq: Int) {
                val bytes = reader.file(ModernCacheReader.INDEX_CONFIG, ModernCacheReader.CONFIG_GROUP_SEQUENCE, osrsSeq)
                val osrs = bytes?.let { runCatching { OsrsFxImportTool.decodeOsrsSeq(it) }.getOrNull() }
                val skeletal = bytes != null && osrs == null
                val osrsSounds = osrs?.sounds?.size ?: SkeletalBakeTool.decodeSkeletalSeq(bytes ?: ByteArray(0))?.sounds?.size ?: 0
                val local = if (osrsSeq in OsrsNpcImportTool.SHARED_MOVEMENT_SEQS) osrsSeq else ids["seq:$osrsSeq"] ?: -1
                val localDef = if (local >= 0) definitions.getNullable(AnimDef::class.java, local) else null
                val localSounds = localDef?.hasFrameSounds == true
                val audible = localDef?.hasAudibleFrameSounds == true
                val verdict =
                    when {
                        osrsSounds == 0 -> "NONE"
                        localSounds && audible -> "OK"
                        localSounds -> "RADIUS0"
                        else -> "LOST"
                    }
                if (osrsSounds > 0) withSound++
                if (verdict == "LOST" || verdict == "RADIUS0") lost++
                println("SEQ $role osrs=$osrsSeq local=$local osrsSounds=$osrsSounds${if (skeletal) "(skeletal)" else ""} localSounds=$localSounds audible=$audible $verdict")
            }
            for (npcId in OsrsNpcImportTool.BATCHES.getValue("deadman-breach")) {
                val n = OsrsNpcProbeTool.decodeOsrs(npcId, npcFiles[npcId] ?: continue)
                println("NPC $npcId '${n.name}'")
                OsrsNpcProbeTool.movementSeqs(n).forEach { (role, seq) -> audit(role, seq) }
            }
            println("COMBAT (EXTRA_SEQS deadman-breach)")
            OsrsNpcImportTool.EXTRA_SEQS["deadman-breach"].orEmpty().forEach { audit("combat", it) }
        }
        library.close()
        println("SUMMARY osrs sequences with sounds=$withSound lost-or-silent=$lost")
    }
}
