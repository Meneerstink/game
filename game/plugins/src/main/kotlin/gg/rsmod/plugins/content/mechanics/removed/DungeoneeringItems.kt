package gg.rsmod.plugins.content.mechanics.removed

/**
 * Every Dungeoneering item (owner 2026-10-11: "Chaotics en ALLES van Dungeoneering volledig verwijderen ... bestaande items
 * weg bij login"). GENERATED from the Void donor's curated list `data/skill/dungeoneering/dungeoneering.items.toml`
 * (2601 ids, extracted to team/prep/DGN-void-dungeoneering-item-ids.txt) plus the six Dungeoneering capes Void keeps in
 * `skill/skillcape.items.toml:850-916`. Ranges, not one block 15707..19896: the gaps are normal items (clue scrolls,
 * dragon masks, (h) armour, Korasi's sword, Third-age druidic, ...) that must stay.
 * Read through [RemovedItems.isUnobtainable] (GE, house, purge at login); kept out of [RemovedItems.IDS] (the rune rules).
 */
object DungeoneeringItems {
    /** Dungeoneering cape (15706, 18508), cape (t) 18509, hood 18510 and both master capes (19709, 19710). */
    val CAPES: Set<Int> = setOf(15706, 18508, 18509, 18510, 19709, 19710)

    val IDS: Set<Int> =
        listOf(
            15707..16427,
            16432..16432,
            16437..16437,
            16442..16442,
            16447..16447,
            16452..16452,
            16457..16457,
            16462..16462,
            16467..16467,
            16472..16472,
            16477..16477,
            16482..16482,
            16487..16487,
            16492..16492,
            16497..16497,
            16502..16502,
            16507..16507,
            16512..16512,
            16517..16517,
            16522..16522,
            16527..16527,
            16532..16532,
            16537..16537,
            16542..16542,
            16547..16547,
            16552..16552,
            16557..16557,
            16562..16562,
            16567..16567,
            16572..16572,
            16577..16577,
            16582..16582,
            16587..16587,
            16592..16592,
            16597..16597,
            16602..16602,
            16607..16607,
            16612..16612,
            16617..16617,
            16622..16622,
            16627..16627,
            16632..16632,
            16637..16637,
            16642..16642,
            16647..17487,
            17489..17742,
            17747..17747,
            17752..17776,
            17780..17834,
            17883..17885,
            17890..17890,
            17895..17895,
            17900..17900,
            17905..17905,
            17910..17910,
            17915..17915,
            17920..17920,
            17925..17925,
            17930..17930,
            17935..18344,
            18346..18374,
            18503..18507,
            18511..18579,
            18586..18611,
            18635..18635,
            18817..18834,
            18839..18839,
            19650..19690,
            19700..19703,
            19846..19879,
            19886..19896,
        ).flatMapTo(HashSet()) { it.toList() } + CAPES + DAEMONHEIM_QUESTS

    /**
     * N260 (owner 2026-10-23 "zoek uit of alle dungeoneering ... compleet verwijderd"; 10-29 cache scan by name): the Daemonheim
     * quest items Void's list lacks - Thok's sword ... Katagon full helm (Thok it to 'em, Vengeance, Three's company: Fractite /
     * Marmaros / Gorgonite / Katagon gear, Novite / Bathus / Zephyrium ore, Heim crab, Gatestone, the four rings of kinship) and
     * the Hexhunter bow with Eva's kiteshield / longsword and the blood-stained glass.
     */
    val DAEMONHEIM_QUESTS: Set<Int>
        get() = (20820..20877).toSet() + (21332..21336)

    fun isDungeoneering(itemId: Int): Boolean = itemId in IDS
}
