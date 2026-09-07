package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player

/**
 * The six actions a familiar can be given from the Summoning orb.
 *
 * This set is the owner's 2026-09-07 requirement H6 and it is deliberately **smaller** than the
 * eight entries interface 747 bakes. Two baked entries are excluded on purpose:
 *
 * * **Follower Details** (747:9 / 747:18) — moved off the orb entirely and onto the Skills tab
 *   (requirement H1). It is not a familiar command; it is a panel switch, and having it on the orb
 *   was what made the orb menu read as a settings menu rather than a command menu.
 * * **Interact** (747:15 / 747:26) — the familiar's own npc option already carries it, so on the
 *   orb it was a duplicate entry for the same conversation.
 *
 * Nothing here invents an action: all six are real baked ops on 747, quoted from
 * `runInterfaceHookProbeTool layout 747`.
 */
enum class FamiliarAction(
    /**
     * Every component on 747 that offers this action: the `op6` right-click entry, its `op1`
     * direct left-click twin, and for the special move the baked "Spell/Cast" chain as well.
     *
     * All of them are listed because this client collects menu entries from a component's **own**
     * hidden flag and not from its ancestors' - proved by live testing on 2026-09-07, where
     * hiding the parent layer 747:8 left every child's entry still in the orb menu. Hiding the
     * parent alone is therefore never enough.
     */
    val orbComponents: IntArray,
    /**
     * The real varbit-6454 value the cache's own left-click switch (clientscript 2671) uses for
     * this action, decoded from 2671's branch targets against 747's op labels.
     */
    val leftClickValue: Int,
    /** The label the cache itself bakes on that component. */
    val label: String,
) {
    /** 747:16 -> 24 -> 25 is the baked "Spell/Cast" twin; 747:17 is the dynamic button. */
    SPECIAL_MOVE(intArrayOf(16, 17, 24, 25), 1, "Special move"),
    ATTACK(intArrayOf(14, 23), 2, "Attack"),
    CALL(intArrayOf(10, 19), 3, "Call Follower"),
    DISMISS(intArrayOf(11, 20), 4, "Dismiss"),
    TAKE_BOB(intArrayOf(12, 21), 5, "Take BoB"),
    RENEW(intArrayOf(13, 22), 6, "Renew Familiar"),
    ;

    companion object {
        /** In the order the orb menu and the selection dialogue list them. */
        val ORDERED = values().toList()

        fun byLeftClickValue(value: Int): FamiliarAction? = ORDERED.firstOrNull { it.leftClickValue == value }

        /**
         * The two baked 747 actions that are deliberately **not** offered (requirement H1/H6):
         * "Follower Details" (9/18), which now lives on the Skills tab, and "Interact" (15/26),
         * which duplicates the familiar's own npc option. Hidden unconditionally, familiar or not.
         */
        val REMOVED_ORB_COMPONENTS = intArrayOf(9, 18, 15, 26)

        /** Every per-familiar option component on 747, whether offered or removed. */
        val ALL_ORB_COMPONENTS: IntArray =
            (ORDERED.flatMap { it.orbComponents.toList() } + REMOVED_ORB_COMPONENTS.toList())
                .distinct()
                .toIntArray()
    }
}

/**
 * Everything the two Summoning surfaces, the npc options and the left-click selector need to know
 * about one familiar, in exactly one place.
 *
 * ## Why this exists
 *
 * Before this, each surface decided for itself what to show: [SummoningUi] asked
 * [SummoningCombatDefinitions] and [BeastOfBurden] directly, `familiar.plugin.kts` re-checked
 * [BeastOfBurden] inside each handler, and the left-click selector offered all eight baked actions
 * to every familiar regardless. Three independent decisions over the same question is how a Steel
 * titan ends up being offered "Take BoB" on one surface after being correctly denied it on another
 * (the owner's requirement H8).
 *
 * Every field is **derived** from data that is already sourced. Nothing is hand-listed per
 * familiar, so a capability can never drift away from the ledger it came from.
 */
data class FamiliarCapabilities(
    val pouch: SummoningPouchData,
    val npcId: Int,
    /** Has real, executable, sourced combat data - i.e. can be ordered to attack. */
    val canFight: Boolean,
    /** Carries items at all: a beast of burden or a forager. Drives "Take BoB". */
    val carries: Boolean,
    /** Accepts deposits. Every beast of burden; no forager. */
    val isBeastOfBurden: Boolean,
    /** Withdraw-only. Every forager. */
    val isForager: Boolean,
    /** The familiar's special move, or `null` when it has none. */
    val special: FamiliarSpecialBinding?,
) {
    val hasSpecial: Boolean get() = special != null

    /** Instant, npc-targeted, player-targeted or inventory-item-targeted. */
    val specialTarget: FamiliarSpecialTarget? get() = special?.target

    /**
     * The orb actions this familiar authentically supports (requirement H7).
     *
     * Call, Dismiss and Renew are unconditional: every familiar has a lifetime that can be
     * refreshed with another pouch, can be recalled to its owner, and can be sent away. Attack,
     * Take BoB and Special Move are each gated on the corresponding sourced capability.
     */
    val actions: Set<FamiliarAction> =
        buildSet {
            add(FamiliarAction.CALL)
            add(FamiliarAction.DISMISS)
            add(FamiliarAction.RENEW)
            if (canFight) add(FamiliarAction.ATTACK)
            if (carries) add(FamiliarAction.TAKE_BOB)
            if (special != null) add(FamiliarAction.SPECIAL_MOVE)
        }

    fun supports(action: FamiliarAction): Boolean = action in actions
}

/**
 * The capability record for every one of the 78 canonical familiars, built once at class-load from
 * the same sourced tables the rest of the subsystem reads.
 */
object FamiliarCapabilityTable {
    private val byNpc: Map<Int, FamiliarCapabilities> =
        SummoningPouchData.values().associate { pouch ->
            val storage = BeastOfBurden.storageFor(pouch)
            pouch.npc to
                FamiliarCapabilities(
                    pouch = pouch,
                    npcId = pouch.npc,
                    canFight = SummoningCombatDefinitions.getByNpc(pouch.npc)?.isExecutable == true,
                    carries = storage != null,
                    isBeastOfBurden = storage != null && !storage.withdrawOnly,
                    isForager = storage != null && storage.withdrawOnly,
                    special =
                        SummoningSpecialMoves.bindings.firstOrNull { binding ->
                            binding.scrolls.any { pouch.npc in it.familiars }
                        },
                )
        }

    /** Every capability record, one per canonical familiar. */
    val all: Collection<FamiliarCapabilities> get() = byNpc.values

    fun forNpc(npcId: Int): FamiliarCapabilities? = byNpc[npcId]

    /** The record for the player's currently summoned familiar, or `null` when none is out. */
    fun active(player: Player): FamiliarCapabilities? = Familiar.current(player)?.let { forNpc(it.id) }

    /**
     * Whether the player's active familiar supports [action]. Every server-side handler behind an
     * orb or panel option calls this before acting, so an option that should not have been drawn
     * still cannot be executed by a forged or stale click.
     */
    fun activeSupports(
        player: Player,
        action: FamiliarAction,
    ): Boolean = active(player)?.supports(action) == true
}
