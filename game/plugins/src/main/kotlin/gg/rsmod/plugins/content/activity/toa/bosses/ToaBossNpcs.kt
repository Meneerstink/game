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
    // Import manifest owned by Claude; only measured same-model source variants are canonicalised here.
    private val aliases = mapOf(
        11720 to 11719,
        11721 to 11719,
        11722 to 11719,
        11732 to 11730,
        11733 to 11730,
        11734 to 11731,
        11746 to 11753,
        11747 to 11756,
        11748 to 11753,
        11749 to 11756,
        11754 to 11753,
        11757 to 11756,
        11763 to 11761,
        11764 to 11762,
        11768 to 11767,
        11779 to 11778,
        11780 to 11778,
        11790 to 11789,
        11791 to 11789,
        11792 to 11789,
        11793 to 11789,
        11794 to 11789,
        11795 to 11789,
        11796 to 11789,
        11798 to 11797,
        11799 to 11797,
    )
    fun canonical(osrs: Int): Int = aliases[osrs] ?: osrs
    fun local(osrs: Int): Int? = gg.rsmod.plugins.content.activity.toa.ToaIds.npc(osrs)
        ?: gg.rsmod.plugins.content.activity.toa.ToaIds.npc(canonical(osrs))
}
