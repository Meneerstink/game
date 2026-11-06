package gg.rsmod.plugins.content.activity.toa

/** Names and source values: C:/RSPS/reference/runelite/gameval/NpcID.java, cache2686 2/9.
 * Local IDs come only from data/cfg/toa/ids.tsv. TODO Claude: fill toa-classic/toa-bosses mappings after import. */
enum class ToaNpcs(val osrsId: Int) {
    TOA_OSMUMTEN_VIS(11689),
    TOA_OSMUMTEN_WARDENS_VIS(11690),
    TOA_OSMUMTEN_VAULT_VIS_2(11693),
    TOA_MIDRAIDLOOT_TRADER(11694),
    TOA_HET_GOAL(11706),
    TOA_HET_GOAL_VULNERABLE(11707),
    TOA_KEPHRI_BOSS_SHIELDED(11719),
    TOA_ZEBAK(11730),
    TOA_WARDENS_P1_OBELISK_NPC(11751),
    AKKHA_SPAWN(11789),
    AKKHA_MELEE(11790),
    AKKHA_SHADOW(11797);

    val localId: Int? get() = ToaIds.npc(osrsId)
}
