package gg.rsmod.plugins.content.activity.cox

/**
 * CoX parties (owner besluit 5a 'groep 1-8 zoals ToA, zelfde party-code'): OSRS uses the leader's clan chat ('Members with recruit rank and
 * up can join the raid' - Wiki Recruiting board); 78legacy has no raid clan chat, so a party is its own roster like ToA's, joined from the
 * party details ('Join'). The leader can advertise it on the recruiting board ('publicly list the party ... with preferred party size,
 * combat level and skill total', Wiki), kick members (party tab op 'Kick', [proc,raids_sidepanel_entry_setup]) and disband it.
 * The party's raid: the first member to use the entrance steps makes it (CoxRaids.start); the others' 'Enter' joins it until it begins.
 */
class CoxParty(val id: Long, leader: String) {
    companion object { const val CAPACITY = 8 }

    private val roster = linkedSetOf(leader.lowercase())
    val members: List<String> get() = roster.toList()
    var leader: String = leader.lowercase()
        private set
    /** 507 'Preferred party size' / 'Preferred combat level' / 'Preferred skill total' (0 = '-' on the board). */
    var preferredSize = 0
    var preferredCombat = 0
    var preferredSkillTotal = 0
    /** 507 'Scaling' (the raid scales to the higher of raiders and this, tsps). 0 = none. */
    var scaling = 0
    /** 'Advertise (14)': listed on the board until this cycle. */
    var advertisedUntil = -1
    var advertisedAt = 0
    /** 'Advertise (14)' counts down (tsps: 15 a day, cache raids_daily_adverts). ONZEKER: per party here, not per account a day. */
    var advertsLeft = CoxPartyBoard.DAILY_ADVERTS
    val createdAt = System.currentTimeMillis()
    /** The party's raid once its first member entered (CoxRaid id), -1 before. */
    var raidId = -1L

    fun isMember(name: String) = name.lowercase() in roster

    fun join(name: String): Boolean = roster.size < CAPACITY && roster.add(name.lowercase())

    fun leave(name: String): Boolean {
        val key = name.lowercase()
        if (!roster.remove(key)) return false
        if (leader == key && roster.isNotEmpty()) leader = roster.first()
        return true
    }

    fun kick(actor: String, member: String): Boolean = actor.lowercase() == leader && actor.lowercase() != member.lowercase() && leave(member)

    fun listed(cycle: Int): Boolean = advertisedUntil > cycle
}

object CoxParties {
    private val parties = java.util.concurrent.ConcurrentHashMap<Long, CoxParty>()
    private var nextId = 1L

    fun of(name: String): CoxParty? = parties.values.firstOrNull { it.isMember(name) }

    fun create(leader: String): CoxParty? {
        if (of(leader) != null) return null
        val party = CoxParty(nextId++, leader)
        parties[party.id] = party
        return party
    }

    fun byId(id: Long): CoxParty? = parties[id]

    fun all(): Collection<CoxParty> = parties.values

    /** The board's rows: advertised parties, newest last (the board sorts on the client side in OSRS; members by default). */
    fun listed(cycle: Int): List<CoxParty> = parties.values.filter { it.listed(cycle) }.sortedBy { it.createdAt }

    fun disband(party: CoxParty) { parties.remove(party.id) }

    fun leave(name: String) {
        val party = of(name) ?: return
        party.leave(name)
        if (party.members.isEmpty()) disband(party)
    }

    internal fun clearForTests() { parties.clear(); nextId = 1L }
}
