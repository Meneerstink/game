package gg.rsmod.game.tools.importer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.RspsPaths

/**
 * GRAFISCH 16-11 K5: CoX-kamers op verdieping 2 tonen in de verte een BEIGE achtergrond (screens\GRAFISCH\vanguard-normale-camera.png);
 * OSRS (video 595L4MEheXc, o.a. beeld 421/553) en onze verdieping-3-kamers tonen ZWART. De CoX-kaartvakken hebben GEEN omgevingsblok
 * (map_env_probe.py: trailer 0 bytes) -> client-standaard. Het ToA-blok (m64_104: 00 72 ... 001b150d 0190) zet via vlag 0x20 de kleur
 * Environment.anInt9538 (g4; client Environment.java:182-186, buiten de lightDetail-voorwaarde). Deze tool geeft elk CoX-kaartvak zonder blok
 * het blok [code 0, vlag 0x20, kleur 000000] = zwarte achtergrond; 'revert' haalt precies dat blok weer weg. ONZEKER tot de client-foto
 * bewijst dat anInt9538 de achtergrond/mist is.
 * Usage: plan|apply|revert
 */
object CoxEnvironmentTool {
    private val BLOCK = byteArrayOf(0, 0x20, 0, 0, 0, 0)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1 && args[0] in setOf("plan", "apply", "revert")) { "Usage: plan|apply|revert" }
        val revert = args[0] == "revert"
        val paths = listOf(RspsPaths.data("cache"), RspsPaths.root("file-server/cache"))
        val mutations = mutableListOf<CacheMutation>()
        val game = CacheLibrary(paths[0])
        val files = CacheLibrary(paths[1])
        try {
            for ((_, name) in ToaUpperTileFlagTool.targets("cox")) {
                val archive = game.index(5).archive(name) ?: run { println("SKIP $name ontbreekt"); null } ?: continue
                val other = requireNotNull(files.index(5).archive(name)) { "Fileserver missing $name" }
                val before = requireNotNull(archive.file(0)?.data)
                check(archive.id == other.id && before.contentEquals(other.file(0)?.data)) { "$name cache copies differ" }
                val map = Rev667TileCodec.decode(before)
                val t = map.trailing
                map.trailing = when {
                    !revert && t.isEmpty() -> BLOCK
                    revert && t.contentEquals(BLOCK) -> ByteArray(0)
                    else -> { println("SKIP $name trailer ${t.joinToString("") { "%02x".format(it) }}"); continue }
                }
                val after = Rev667TileCodec.encode(map)
                println("COX_ENV $name ${if (revert) "blok weg" else "zwart blok"}")
                mutations += CacheMutation(5, archive.id, 0, after, "CoX $name: environment ${if (revert) "removed" else "fog/background 000000"}",
                    CacheItemProbeTool.sha1(before))
            }
        } finally { game.close(); files.close() }
        if (mutations.isEmpty()) { println("NOTHING_TO_DO"); return }
        val tx = CacheTransaction(paths, mutations)
        val plan = tx.preflight()
        check(tx.blockingErrors(plan).isEmpty()) { tx.blockingErrors(plan).joinToString("\n") }
        if (args[0] == "plan") { println("DRY_RUN ${tx.id} maps=${mutations.size}"); return }
        val result = tx.apply(plan)
        check(tx.verify().isEmpty()) { "Cache verification failed: ${tx.id}" }
        println("COX_ENV transaction=${tx.id} applied=${result.applied} skipped=${result.skipped} verified=true")
    }
}
