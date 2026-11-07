package gg.rsmod.plugins.content.activity.toa.core

import gg.rsmod.game.model.PlayerUID

/**
 * N462 ToA mode (owner 2026-11-06 21:4x, decision A): no individual invocations - the leader picks one of three modes. The raid levels are
 * the OSRS mode boundaries (GPT-A 10-invocations.txt "Entry 0-149, Normal 150-299, Expert 300-600"): each mode plays at its lowest level.
 */
enum class ToaMode(val raidLevel: Int, val label: String) {
    ENTRY(0, "Entry"),
    NORMAL(150, "Normal"),
    EXPERT(300, "Expert"),
    ;

    companion object {
        fun of(raidLevel: Int): ToaMode = when {
            raidLevel >= EXPERT.raidLevel -> EXPERT
            raidLevel >= NORMAL.raidLevel -> NORMAL
            else -> ENTRY
        }
    }
}

/**
 * Damage -> room points per source (OSRS Wiki Chest (Tombs of Amascut) oldid 15385393; GPT-A 20-punten-formules.tsv). Anything else 1.0.
 * Wardens P2 gives no more points after its third down (the caller passes [WARDENS_P2_AFTER_THIRD_DOWN]).
 */
enum class ToaPointSource(val multiplier: Double) {
    DEFAULT(1.0),
    APMEKEN_BABOON(1.2),
    BABA(2.0),
    ZEBAK(1.5),
    KEPHRI_SCARAB(0.5),
    HETS_SEAL(2.5),
    OBELISK(1.5),
    WARDENS_P2(2.0),
    WARDENS_P2_AFTER_THIRD_DOWN(0.0),
    WARDENS_P3(2.5),
    CORE(0.0),
    ;

    companion object {
        /** OSRS npc id -> source (RuneLite gameval NpcID names: TOA_PATH_APMEKEN_BABOON_* 11709-11718, TOA_BABA 11778, TOA_ZEBAK 11730/11732,
         *  TOA_KEPHRI_SHIELD_SCARAB, GUARDIAN_* and SCARAB_RANGEKITE 11723-11727, TOA_HET_GOAL(_VULNERABLE) 11706/11707, WARDENS_P1/P2_OBELISK
         *  11751/11752, TOA_WARDEN_*_PHASE2_* 11753-11758, *_PHASE3 11761/11762, TOA_WARDEN_*_CORE 11770/11771). */
        fun of(osrsNpc: Int): ToaPointSource = when (osrsNpc) {
            in 11709..11718 -> APMEKEN_BABOON
            11778 -> BABA
            11730, 11732 -> ZEBAK
            in 11723..11727 -> KEPHRI_SCARAB
            11706, 11707 -> HETS_SEAL
            11751, 11752 -> OBELISK
            in 11753..11758 -> WARDENS_P2
            11761, 11762 -> WARDENS_P3
            11770, 11771 -> CORE
            else -> DEFAULT
        }
    }
}

/**
 * The points ledger of one raid (OSRS Update: Tombs of Amascut Drop Mechanics & Osmumten's Fang oldid 14338876; Chest oldid 15385393):
 * every member starts with 5,000 total points; damage earns room points (capped at 20,000 per player per room); a completed room adds its
 * room points to the total (capped at 64,000) and gives the room's MVP 300 x team size; every death costs max(20% of the total, 1,000);
 * the reward points are the total minus the starting 5,000.
 *
 * ONZEKER (GPT-A 20-beloningen.txt, open in the sources): overkill and rounding per hit (here: floor of damage x multiplier), the MVP tie
 * (here: no MVP when two players share the highest room score), and puzzle completion points (not in the sources: no points).
 */
class ToaPointsLedger(roster: Collection<PlayerUID>) {
    private val total = linkedMapOf<PlayerUID, Int>().apply { roster.forEach { put(it, START_TOTAL) } }
    private val room = hashMapOf<PlayerUID, Int>()

    /** The team size of the MVP bonus: everybody who entered, leavers included (their points stay in the party sum, Chest wiki). */
    val teamSize: Int get() = total.size

    fun damage(player: PlayerUID, damage: Int, source: ToaPointSource = ToaPointSource.DEFAULT) {
        if (player !in total || damage <= 0) return
        val earned = (damage * source.multiplier).toInt()
        room[player] = minOf(ROOM_CAP, (room[player] ?: 0) + earned)
    }

    fun roomPoints(player: PlayerUID): Int = room[player] ?: 0

    /** A room is completed: room points move to the totals, the single highest scorer gets the MVP bonus. Returns the MVP, if any. */
    fun completeRoom(): PlayerUID? {
        val best = room.values.maxOrNull()
        val mvp = room.entries.filter { it.value == best && it.value > 0 }.singleOrNull()?.key
        room.forEach { (player, points) -> add(player, points) }
        mvp?.let { add(it, MVP_PER_MEMBER * teamSize) }
        room.clear()
        return mvp
    }

    private val deaths = hashMapOf<PlayerUID, Int>()

    /** Nobody in the party died this raid (OSRS challenge rewards). */
    val deathless: Boolean get() = deaths.isEmpty()

    fun deaths(player: PlayerUID): Int = deaths[player] ?: 0

    fun death(player: PlayerUID) {
        if (player in total) deaths[player] = (deaths[player] ?: 0) + 1
        val current = total[player] ?: return
        total[player] = maxOf(0, current - maxOf(current / 5, DEATH_MINIMUM))
    }

    fun total(player: PlayerUID): Int = total[player] ?: 0

    /** P: the total minus the 5,000 every player started with (never below zero). */
    fun rewardPoints(player: PlayerUID): Int = maxOf(0, total(player) - START_TOTAL)

    fun partyRewardPoints(): Int = total.keys.sumOf { rewardPoints(it) }

    fun players(): Set<PlayerUID> = total.keys

    private fun add(player: PlayerUID, points: Int) {
        total[player] = minOf(TOTAL_CAP, (total[player] ?: 0) + points)
    }

    companion object {
        const val START_TOTAL = 5_000
        const val ROOM_CAP = 20_000
        const val TOTAL_CAP = 64_000
        const val MVP_PER_MEMBER = 300
        const val DEATH_MINIMUM = 1_000
    }
}
