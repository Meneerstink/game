package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.ItemAttribute

/** Fight state belongs to the held scroll, not a global wizard-killed flag.
 * Actor creation/combat assets are supplied by the verified encounter adapter.
 * Wiki Double agent and Treasure Trails/Guide/Coordinates, pinned in CLUE sources.
 */
object TreasureTrailGuardians {
    enum class Result { UNHANDLED, UNAVAILABLE, FIGHTING, READY }
    class Session(val player: Player, val step: TreasureTrailSourceStep,
                  val contract: ClueStepContract, val actors: List<Npc>) {
        private val dead = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Npc, Boolean>())
        val defeated: Boolean get() = actors.all { it in dead }
        internal fun killed(npc: Npc): Boolean = actors.any { it === npc } && dead.add(npc)
        internal fun alive() = actors.filter { it !in dead }
    }
    private val sessions = HashMap<Player, Session>()
    private val spawning = HashSet<Player>()
    fun session(player: Player): Session? = sessions[player]

    /** Identity, never an NPC name/id whitelist: ordinary copies remain ordinary combatants. */
    fun owner(actor: Npc): Player? = sessions.values.firstOrNull { session ->
        session.actors.any { it === actor } && session.player.isOnline && session.contract.valid(session.player)
    }?.player

    /** The source explicitly exempts the first emote step, including master emotes. */
    fun agentSource(tier: ClueScrollTier, wilderness: Boolean): Int? = when (tier) {
        ClueScrollTier.HARD -> if (wilderness) 1777 else 1778
        ClueScrollTier.MASTER -> 7312
        else -> null
    }
    private fun guarded(step: TreasureTrailSourceStep) = step.source.let {
        it.action == TreasureTrailStepCatalogue.Action.DIG && it.challenge == TreasureTrailStepCatalogue.Challenge.GUARD ||
            it.action == TreasureTrailStepCatalogue.Action.HOT_COLD && TreasureTrailHotCold.current.supports(it) ||
            it.action == TreasureTrailStepCatalogue.Action.EMOTE &&
            it.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT && agentSource(it.tier, false) != null
    }
    private fun atSite(player: Player, step: TreasureTrailSourceStep): Boolean =
        if (step.source.action == TreasureTrailStepCatalogue.Action.HOT_COLD)
            TreasureTrailHotCold.current.site(player, step) != null else step.source.at(player.tile)
    private fun marked(player: Player, contract: ClueStepContract): Boolean =
        contract.scroll.getAttr(ItemAttribute.CLUE_GUARD_KEY) == contract.step.persistenceId &&
            contract.scroll.getAttr(ItemAttribute.CLUE_GUARD_PROGRESS) == ClueScrollManager.progress(player, contract.tier) + 1

    fun ready(player: Player, step: TreasureTrailSourceStep): Boolean {
        if (!guarded(step) || !player.isOnline) return false
        val contract = ClueStepContract.capture(player, step) ?: return false
        return step.source.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT &&
            ClueScrollManager.progress(player, step.tier) == 0 || marked(player, contract)
    }

    /** Caller chooses only a source-listed alternative; no guessed guard probabilities here.
     * Spawn must be atomic: its adapter removes partial actors before returning null.
     */
    fun attempt(player: Player, step: TreasureTrailSourceStep, sources: List<Int>, wilderness: Boolean = false, component: Int? = null,
                spawn: (Player, List<Int>) -> List<Npc>?): Result {
        val row = step.source
        if (!guarded(step) || !player.isOnline || !atSite(player, step)) return Result.UNHANDLED
        if (row.action == TreasureTrailStepCatalogue.Action.EMOTE &&
            (component != row.firstEmote || !TreasureTrailEmotes.attire(player, row))) return Result.UNHANDLED
        val contract = ClueStepContract.capture(player, step) ?: return Result.UNHANDLED
        sessions[player]?.takeUnless { it.contract.valid(player) }?.let { remove(player) }
        if (ready(player, step)) return Result.READY
        sessions[player]?.let { existing ->
            if (existing.step === step) return Result.FIGHTING
            remove(player)
        }
        val allowed = if (row.action == TreasureTrailStepCatalogue.Action.HOT_COLD)
            TreasureTrailHotCold.current.site(player, step)?.guards == sources
        else when (row.challenge) {
            TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT -> sources.size == 1 &&
                sources.single() == agentSource(row.tier, wilderness)
            TreasureTrailStepCatalogue.Challenge.GUARD -> if (row.chooseGuard)
                sources.size == 1 && sources.single() in row.guards
            else sources == row.guards
            else -> false
        }
        if (!allowed || sources.isEmpty()) return Result.UNAVAILABLE
        if (!spawning.add(player)) return Result.UNAVAILABLE
        try {
            val actors = spawn(player, sources) ?: return Result.UNAVAILABLE
            val identities = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Npc, Boolean>())
            val unique = actors.filter { identities.add(it) }
            if (actors.size != sources.size || unique.size != actors.size ||
                actors.any { it.world !== player.world || !it.isSpawned() || it.isDead() } ||
                !contract.valid(player) || !player.isOnline) {
                unique.forEach { if (it.isSpawned()) it.world.remove(it) }
                return Result.UNAVAILABLE
            }
            sessions[player] = Session(player, step, contract, actors.toList())
            TreasureTrailGuardianDialogue.spawned(player, sources, actors)
            return Result.FIGHTING
        } finally {
            spawning.remove(player)
        }
    }

    /** Only the actual actors spawned for this exact account/scroll may satisfy its fight.
     * A dead actor callback can repeat (pre-death / death); it must never count twice.
     */
    fun killed(npc: Npc, onAgentReady: (Player, TreasureTrailSourceStep) -> Unit = { _, _ -> }): Boolean {
        println("PROBLEM CLUE guard killed() called for ${npc.id} dead=${npc.isDead()} sessions=${sessions.size}")
        if (!npc.isDead()) return false
        val session = sessions.values.firstOrNull { it.actors.any { actor -> actor === npc } } ?: run { println("PROBLEM CLUE guard killed ${npc.id} without a session (sessions=${sessions.size})"); return false }
        if (!session.player.isOnline || !session.contract.valid(session.player)) { println("PROBLEM CLUE guard killed ${npc.id} but contract invalid for ${session.player.username}"); remove(session.player); return false }
        if (!session.killed(npc)) return false
        if (!session.defeated) return true
        val player = session.player
        session.contract.scroll.putAttr(ItemAttribute.CLUE_GUARD_KEY, session.step.persistenceId)
            .putAttr(ItemAttribute.CLUE_GUARD_PROGRESS, ClueScrollManager.progress(player, session.step.tier) + 1)
        player.inventory.dirty = true
        sessions.remove(player)
        // Combat gear is allowed while fighting. Uri appears immediately only in clue gear.
        if (session.step.source.challenge == TreasureTrailStepCatalogue.Challenge.DOUBLE_AGENT &&
            TreasureTrailEmotes.attire(player, session.step.source)) onAgentReady(player, session.step)
        return true
    }

    /** A post-fight coordinate still requires the correct dig; killing never grants a casket. */
    fun dig(player: Player, step: TreasureTrailSourceStep): Boolean =
        step.source.action in setOf(TreasureTrailStepCatalogue.Action.DIG, TreasureTrailStepCatalogue.Action.HOT_COLD) && atSite(player, step) &&
            ready(player, step) && ClueStepContract.capture(player, step)?.advance(player) == true

    fun cycle() {
        sessions.values.toList().forEach {
            if (!it.contract.valid(it.player) || !it.player.isOnline) { println("PROBLEM CLUE guard session dropped: contract invalid for ${it.player.username}"); remove(it.player) }
            // Removal without death (despawn/escape) cannot satisfy a kill.
            else if (it.alive().any { actor -> !actor.isSpawned() }) remove(it.player)
        }
    }
    fun remove(player: Player) {
        sessions.remove(player)?.alive()?.forEach { if (!it.isDead() && it.isSpawned()) it.world.remove(it) }
    }
}
