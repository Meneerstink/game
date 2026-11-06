package gg.rsmod.plugins.content.activity.toa.bosses

/** OSRS cache2686 / NpcID. Local ids are exclusively from Claude's 91-toa-ids-claude.tsv; never send OSRS ids to 667. */
object ToaBossNpcs {
    const val AKKHA = 11789
    const val AKKHA_SHADOW = 11797
    const val UNSTABLE_ORB = 11804
    const val ZEBAK = 11730
    const val ZEBAK_TAIL = 11731
    const val BABA = 11778
    const val BABOON = 11781
    const val KEPHRI = 11719
    const val SWARM = 11723
    const val SOLDIER = 11724
    const val SPITTING = 11725
    const val ARCANE = 11726
    const val AGILE = 11727
    const val OBELISK = 11751 // TOA_WARDENS_P1_OBELISK_NPC (11750 is inactive/non-attackable)
    const val ELIDINIS_P2 = 11753
    const val TUMEKEN_P2 = 11756
    const val ELIDINIS_P3 = 11761
    const val TUMEKEN_P3 = 11762
    const val CORE = 11770
    // Import not published yet. Missing mappings deliberately do not bind unrelated 667 NPCs.
    val mappings: Map<Int, Int> = emptyMap()
    fun local(osrs: Int): Int? = mappings[osrs]
}
