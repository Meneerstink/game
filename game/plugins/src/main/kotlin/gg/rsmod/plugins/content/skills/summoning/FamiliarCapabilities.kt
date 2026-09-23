package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Player

/**
 * The six actions exposed by the revision-667 Summoning orb.
 *
 * This set is capability-driven and deliberately **smaller** than the eight entries interface
 * 747 bakes. Generic Attack is not an orb action in the requested 2011 layout; attack targeting
 * remains a separate, server-validated Follower Details control.
 *
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
    /** Whether this action participates in the orb right-click/left-click selector. */
    val orbAction: Boolean = true,
) {
    /** 747:16 -> 24 -> 25 is the baked "Spell/Cast" twin; 747:17 is the dynamic button. */
    SPECIAL_MOVE(intArrayOf(16, 17, 24, 25), 1, "Special move"),
    /** Kept for the panel/server action; deliberately excluded from the orb selector. */
    ATTACK(intArrayOf(14, 23), 2, "Attack", orbAction = false),
    FOLLOWER_DETAILS(intArrayOf(9, 18), 0, "Follower Details"),
    CALL(intArrayOf(10, 19), 3, "Call Follower"),
    DISMISS(intArrayOf(11, 20), 4, "Dismiss"),
    TAKE_BOB(intArrayOf(12, 21), 5, "Take BoB"),
    RENEW(intArrayOf(13, 22), 6, "Renew Familiar"),
    ;

    companion object {
        /** In the order the orb menu and the selection dialogue list them. */
        val ORDERED = values().filter { it.orbAction }

        fun byLeftClickValue(value: Int): FamiliarAction? = ORDERED.firstOrNull { it.leftClickValue == value }

        /**
         * Interact (15/26) is deliberately not offered because it duplicates the familiar's own
         * npc option. Attack (14/23) is also hidden, but is represented separately above so the
         * Follower Details panel and stale packet guards can still share its capability decision.
         */
        val REMOVED_ORB_COMPONENTS = intArrayOf(15, 26, 14, 23)

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
     * Follower Details, Call, Dismiss and Renew are unconditional. Take BoB and Special Move are
     * gated on the corresponding sourced capability. Attack is intentionally not in this set: it
     * remains a panel/server action, never an orb menu item.
     */
    val actions: Set<FamiliarAction> =
        buildSet {
            add(FamiliarAction.CALL)
            add(FamiliarAction.DISMISS)
            add(FamiliarAction.RENEW)
            add(FamiliarAction.FOLLOWER_DETAILS)
            if (carries) add(FamiliarAction.TAKE_BOB)
            if (special != null) add(FamiliarAction.SPECIAL_MOVE)
        }

    fun supports(action: FamiliarAction): Boolean =
        if (action == FamiliarAction.ATTACK) canReceiveAttackCommand else action in actions

    val combatMode: FamiliarCombatMode
        get() = when {
            !canFight -> FamiliarCombatMode.NON_COMBAT
            SummoningCombatDefinitions.getByNpc(npcId)?.assistMode == FamiliarAssistMode.DEFENSIVE_ONLY ->
                FamiliarCombatMode.SELF_DEFENCE_ONLY
            else -> FamiliarCombatMode.COMMANDABLE_COMBAT
        }

    val skillFocus: FamiliarSkillFocus
        get() = SummoningCatalogue.getByNpc(npcId)?.skillFocus ?: FamiliarSkillFocus.NONE

    internal val normalAttackProfile: FamiliarNormalAttackProfile?
        get() = SummoningCombatDefinitions.getByNpc(npcId)?.takeIf { it.isExecutable }?.let { definition ->
            FamiliarNormalAttackProfile(
                style = definition.style,
                skillFocus = skillFocus,
                range = definition.attackRange,
                speed = definition.attackSpeed,
                maxHit = definition.maxHit,
                animation = definition.attackAnimation,
                graphic = definition.attackGraphic,
                projectile = definition.projectile,
                sounds = FamiliarCombat.NORMAL_ATTACK_SOUNDS[pouch],
            )
        }

    val bobSlots: Int get() = BeastOfBurden.storageFor(pouch)?.key?.capacity ?: 0
    val bobType: FamiliarInventoryKind
        get() = when {
            isBeastOfBurden -> FamiliarInventoryKind.BEAST_OF_BURDEN
            isForager -> FamiliarInventoryKind.FORAGER
            else -> FamiliarInventoryKind.NONE
        }
    val bobEssenceOnly: Boolean get() = BeastOfBurden.storageFor(pouch)?.essenceOnly == true
    val canReceiveAttackCommand: Boolean get() = combatMode == FamiliarCombatMode.COMMANDABLE_COMBAT
    val retaliates: Boolean get() = combatMode != FamiliarCombatMode.NON_COMBAT
    val canBeTargetedInPvm: Boolean get() = combatMode != FamiliarCombatMode.NON_COMBAT
    val canBeTargetedInPvp: Boolean get() = combatMode != FamiliarCombatMode.NON_COMBAT
    val summonPoints: Int get() = SummoningFamiliarDefinitions.get(pouch).summonPoints
    val durationMinutes: Int get() = SummoningFamiliarDefinitions.get(pouch).durationMinutes
    val specialTrigger: FamiliarSpecialTrigger? get() = special?.trigger
    val specialPoints: Int? get() = special?.scroll?.specialPoints
}

enum class FamiliarCombatMode { NON_COMBAT, SELF_DEFENCE_ONLY, COMMANDABLE_COMBAT }

internal data class FamiliarNormalAttackProfile(
    val style: FamiliarAttackStyle,
    val skillFocus: FamiliarSkillFocus,
    val range: Int,
    val speed: Int,
    val maxHit: Int,
    val animation: Int,
    val graphic: Int,
    val projectile: Int,
    val sounds: FamiliarCombat.NormalAttackSounds?,
)

/**
 * The capability record for every canonical familiar, built once at class-load from
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
