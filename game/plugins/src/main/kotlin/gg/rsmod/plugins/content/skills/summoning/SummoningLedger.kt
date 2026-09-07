package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.model.World

/**
 * Cross-entity boot assertions for the Summoning ledger.
 *
 * [SummoningFamiliarDefinitions.validate], [SummoningCombatDefinitions.validate] and
 * [SummoningSpecialMoves.validate] each check one table in isolation. The four revision-667
 * roster bugs that were found in this repository (Spirit Tz-Kih, Void shifter, Void spinner and
 * Phoenix all pointing at the wrong base NPC) were invisible to those per-table checks because
 * every individual table stayed internally consistent - only the *relationships between* the
 * tables were broken.
 *
 * These checks therefore prove the relationships instead:
 *  - every pouch maps to exactly one base familiar NPC,
 *  - every pouch maps to exactly one scroll, and that scroll agrees about the base NPC,
 *  - every fighting familiar's combat transform is that familiar's own base NPC + 1,
 *  - every pouch has a [SummoningCatalogue] row, and that row's sourced classification agrees
 *    with the combat table about whether the familiar fights and with [BeastOfBurden] about what
 *    it can carry.
 *
 * The transform rule is not a guess: in the 667 cache every summonable familiar occupies a pair
 * of consecutive NPC slots, the lower being the idle/follow form and the higher the combat form
 * (spirit wolf 6829/6830, dreadfowl 6825/6826, thorny snail 6806/6807, ...). It holds for all 73
 * fighting rows, and it is exactly the invariant each of the four roster bugs violated.
 */
object SummoningLedger {
    fun validate() {
        validatePouchIdentity()
        validateScrollRelationships()
        validateCatalogue()
        validateCombatTransforms()
        validateInventories()
        validateSpecialMoveCompleteness()
        validateInterfaceCapabilities()
    }

    /**
     * Every one of the 78 familiars must resolve to a real idle animation from the cache
     * (owner requirement H13).
     *
     * This gate needs a loaded [World] and so is separate from [validate]. It walks the same chain
     * the Follower Details panel does: `NpcDef.basId` (NPCType opcode 127) -> `BasDef` (config
     * group 32) -> `ready`, or the weighted `readyAnimations` pool for the two familiars whose set
     * varies its idle. Every familiar resolves as of 2026-09-07 - see
     * `C:\RSPS\summoning_refs\familiar_bastypes.txt` - so anything that stops resolving is a
     * regression in the decode chain, not a data gap, and the run should not boot past it silently.
     */
    fun validateRenderData(world: World) {
        val unresolved =
            SummoningPouchData.values.mapNotNull { pouch ->
                val basId = world.definitions.get(NpcDef::class.java, pouch.npc).basId
                if (basId == -1) {
                    return@mapNotNull "${pouch.name} (npc ${pouch.npc}) has no basId"
                }
                val idle = world.definitions.get(BasDef::class.java, basId).idleAnimation()
                if (idle == -1) "${pouch.name} (npc ${pouch.npc}, bas $basId) has no idle animation" else null
            }
        check(unresolved.isEmpty()) {
            "${unresolved.size} of 78 familiars have no resolvable idle animation, so the Follower " +
                "Details panel would fall back to the client's pet default for them: $unresolved"
        }
        validateCombatLevels(world)
        validatePathingData(world)
        validateCombatVisuals(world)
    }

    /**
     * Phase J: every animation, graphic and projectile id the combat ledger wires for a familiar
     * must actually exist in the production cache.
     *
     * ## Why an existence check is worth a boot gate
     *
     * An animation id that does not resolve is not a crash and not a log line. The server sends it,
     * the client looks it up, finds nothing, and renders nothing - so the familiar attacks in its
     * idle pose, or dies without a death animation, and the only symptom is a human saying "the
     * animation is wrong" about one familiar out of 78. That is precisely the class of fault this
     * subsystem keeps producing, and it is the class an id-existence check catches cheaply.
     *
     * This gate deliberately proves only **existence**, which is the whole of what the server can
     * be wrong about here: whether the *right* animation was chosen is a sourcing question settled
     * in `SummoningCombatDefinitions`, and whether it *looks* right is a human question. Claiming
     * more from a lookup would repeat the mistake the 2026-09-07 verification reset was called for.
     *
     * `-1` means "this familiar has none", which is real ledger state and is skipped rather than
     * failed. Only a positive id that does not resolve is a fault.
     */
    private fun validateCombatVisuals(world: World) {
        fun missing(
            kind: String,
            id: Int,
            resolves: (Int) -> Boolean,
        ): String? = if (id >= 0 && !resolves(id)) "$kind $id" else null

        val seqExists = { id: Int -> world.definitions.getNullable(AnimDef::class.java, id) != null }
        val gfxExists = { id: Int -> world.definitions.getNullable(SpotAnimDef::class.java, id) != null }

        val faults =
            SummoningCombatDefinitions.values.mapNotNull { definition ->
                val broken =
                    listOfNotNull(
                        missing("attack animation", definition.attackAnimation, seqExists),
                        missing("block animation", definition.blockAnimation, seqExists),
                        missing("death animation", definition.deathAnimation, seqExists),
                        missing("attack graphic", definition.attackGraphic, gfxExists),
                        // A projectile is a spotanim in this revision - the same archive the
                        // graphic ids come from - so it is checked the same way.
                        missing("projectile", definition.projectile, gfxExists),
                    )
                if (broken.isEmpty()) null else "${definition.pouch.name}: ${broken.joinToString(", ")} not in the cache"
            }
        check(faults.isEmpty()) {
            "${faults.size} familiars are wired to combat visuals this cache does not contain, so " +
                "they would silently render nothing: $faults"
        }
    }

    /**
     * Owner requirement H16: following, pathing and collision must be correct for all 78.
     *
     * The movement code itself is generic - [Familiar.follow] chases the owner's end-of-cycle tile,
     * mirrors the owner's speed, routes with `BFSPathFindingStrategy` against real collision, and
     * subtracts the owner's own footprint so a familiar cannot stand inside its owner. It has no
     * per-familiar branches. What it *does* depend on, per familiar, is two pieces of cache data:
     *
     * * **`NpcDef.size`** - fed to `PathRequest.setSourceSize` and to the footprint calculation. A
     *   familiar whose size were wrong would either fail to path through gaps it should fit, or
     *   overlap its owner despite the footprint subtraction.
     * * **`BasDef.walk`** - the sequence the familiar plays while moving. Without it a familiar
     *   slides to its destination in its idle pose.
     *
     * Neither is something the movement code can check for itself, and both are exactly the kind of
     * data that used to be silently absent - `walk` was unreachable at all until the `basId` decode
     * was fixed on 2026-09-07. This gate makes a regression in either fail the boot rather than
     * surface later as an unexplained visual fault in one familiar out of 78.
     *
     * A familiar whose body-animation set genuinely carries no walk sequence is reported rather
     * than tolerated, because as of 2026-09-07 all 78 resolve one
     * (`C:\RSPS\summoning_refs\familiar_bastypes.txt`); the number may only go down by regression.
     */
    private fun validatePathingData(world: World) {
        val faults =
            SummoningPouchData.values.mapNotNull { pouch ->
                val def = world.definitions.get(NpcDef::class.java, pouch.npc)
                when {
                    def.size < 1 ->
                        "${pouch.name} (npc ${pouch.npc}) has size ${def.size}, which would break " +
                            "its path request and its owner-overlap footprint"
                    def.basId == -1 -> "${pouch.name} (npc ${pouch.npc}) has no basId, so it has no walk sequence"
                    world.definitions.get(BasDef::class.java, def.basId).walk == -1 ->
                        "${pouch.name} (npc ${pouch.npc}, bas ${def.basId}) has no walk sequence, so it " +
                            "would slide while following"
                    else -> null
                }
            }
        check(faults.isEmpty()) {
            "${faults.size} of 78 familiars are missing the cache data their following and " +
                "collision depend on: $faults"
        }
    }

    /**
     * Owner requirement H15: authentic combat levels, for combat familiars only.
     *
     * ## Where the level actually lives
     *
     * A probe of all 78 familiar npc definitions shows **every following-form familiar carries
     * `combatLevel = 0`**. The level is on the familiar's **combat form** instead - the id
     * [SummoningCombatDefinition.combatNpc] names:
     *
     * | Familiar | following form | combat form |
     * |---|---|---|
     * | Steel titan | 7343, level 0 | 7344, level 230 |
     * | Spirit wolf | 6829, level 0 | 6830, level 26 |
     * | Unicorn stallion | 6822, level 0 | 6823, level 70 |
     *
     * So the authentic rule is not "push a level onto the familiar": it is that a familiar which is
     * merely following shows no level, and the combat form it transforms into shows one. That is
     * the cache's own design and it is already what the roster models.
     *
     * What this gate checks is that the two records agree: [SummoningCatalogue]'s combat levels,
     * which come from the 2011 Knowledge Base familiars table, must match what the cache puts on
     * the corresponding combat form. Two independent sources for the same number, so a
     * disagreement means one of them is wrong and the level shown in game would be unsourced.
     *
     * A familiar with no combat form is skipped rather than failed: not every combat familiar in
     * the roster has a separate combat npc id, and the absence of one is real cache state.
     */
    private fun validateCombatLevels(world: World) {
        val mismatches =
            SummoningCombatDefinitions.values.mapNotNull { definition ->
                val combatNpc = definition.combatNpc ?: return@mapNotNull null
                val expected = SummoningCatalogue[definition.pouch].combatLevel ?: return@mapNotNull null
                val actual = world.definitions.get(NpcDef::class.java, combatNpc).combatLevel
                if (actual == expected) {
                    null
                } else {
                    "${definition.pouch.name}: catalogue says level $expected, cache npc $combatNpc says $actual"
                }
            }
        check(mismatches.isEmpty()) {
            "${mismatches.size} familiars' combat levels disagree between the 2011 Knowledge Base " +
                "catalogue and this cache's own combat-form npc definitions: $mismatches"
        }

        // The other half of H15: a familiar with no sourced combat level must not be carrying
        // combat data that would let the client show one.
        val unexpected =
            SummoningCombatDefinitions.values.mapNotNull { definition ->
                if (SummoningCatalogue[definition.pouch].combatLevel != null || !definition.canFight) {
                    null
                } else {
                    "${definition.pouch.name} has combat data but no sourced combat level"
                }
            }
        check(unexpected.isEmpty()) {
            "familiars that would fight without a sourced combat level: $unexpected"
        }
    }

    /**
     * Every special move a familiar can be offered must be fully specified before the button that
     * offers it is drawn: a scroll to consume, a point cost that fits the client's own field, a
     * target mode the dispatch understands, and panel text to describe it.
     *
     * This is the gate the run brief asks for - "fail the build/test if future data introduces an
     * option with no implementation". Adding a familiar whose special is half-declared now stops
     * the server at boot instead of producing a button that does nothing, which is exactly the
     * class of fault the Special Move button was.
     */
    private fun validateSpecialMoveCompleteness() {
        SummoningSpecialMoves.bindings.forEach { binding ->
            binding.scrolls.forEach { scroll ->
                check(scroll.scroll > 0) {
                    "${scroll.name} has no scroll item id, so its special move cannot be consumed."
                }
                check(scroll.specialPoints in 1..Familiar.MAX_SPECIAL_POINTS) {
                    "${scroll.name} costs ${scroll.specialPoints} special-move points, which is outside 1..${Familiar.MAX_SPECIAL_POINTS}."
                }
                check(scroll.specialPoints <= SummoningSpecialMoves.MAX_PANEL_COST) {
                    "${scroll.name} costs ${scroll.specialPoints} points, which does not fit varbit 4288."
                }
                check(SummoningSpecialMoveText[scroll] != null) {
                    "${scroll.name} has no sourced name/description, so the follower panel would " +
                        "render an empty special-move line for it."
                }
                scroll.familiars.forEach { npc ->
                    check(SummoningPouchData.values.any { it.npc == npc }) {
                        "${scroll.name} is bound to familiar NPC $npc, which is not in the roster."
                    }
                }
            }
        }
    }

    /**
     * The interface gate: every capability the two Summoning surfaces can *show* must be one the
     * server can actually *do*, and vice versa.
     *
     * Concretely, for every familiar in the roster:
     *  - "Take BoB" and the Familiar Inventory are offered exactly when the familiar really
     *    carries items, so a Unicorn stallion can never be offered a beast-of-burden action;
     *  - "Attack" is offered exactly when the familiar has executable combat data behind it;
     *  - the Special Move button is offered exactly when a fully specified special exists.
     *
     * [SummoningUi] is the single place both surfaces read those three answers from, so checking
     * it here checks the panel and the orb at once.
     */
    private fun validateInterfaceCapabilities() {
        SummoningPouchData.values.forEach { pouch ->
            val entry = SummoningCatalogue[pouch]
            val carries = SummoningUi.carries(pouch.npc)
            check(carries == (entry.inventory.kind != FamiliarInventoryKind.NONE)) {
                "${pouch.name} would be offered Take BoB: ${carries}, but the ledger says it carries " +
                    "${entry.inventory.kind}."
            }
            val fights = SummoningUi.canFight(pouch.npc)
            val combat = SummoningCombatDefinitions.get(pouch)
            check(fights == combat.isExecutable) {
                "${pouch.name} would be offered Attack: ${fights}, but its combat row is " +
                    "executable=${combat.isExecutable}."
            }
        }
    }

    private fun validatePouchIdentity() {
        val pouchItems = SummoningPouchData.values.map { it.pouch }
        check(pouchItems.distinct().size == pouchItems.size) {
            "Two Summoning pouches share the same pouch item id: " +
                pouchItems.groupBy { it }.filterValues { it.size > 1 }.keys
        }
        val baseNpcs = SummoningPouchData.values.map { it.npc }
        check(baseNpcs.distinct().size == baseNpcs.size) {
            "Two Summoning pouches share the same base familiar NPC: " +
                baseNpcs.groupBy { it }.filterValues { it.size > 1 }.keys
        }
    }

    private fun validateScrollRelationships() {
        val scrollByPouch = HashMap<Int, SummoningScrollData>()
        SummoningScrollData.values.forEach { scroll ->
            check(scroll.pouches.size == scroll.familiars.size) {
                "${scroll.name} lists ${scroll.pouches.size} pouches but ${scroll.familiars.size} familiars."
            }
            scroll.pouches.forEachIndexed { index, pouchItem ->
                val pouch =
                    requireNotNull(SummoningPouchData.getDataByPouchId(pouchItem)) {
                        "${scroll.name} references pouch item $pouchItem, which is not in the familiar roster."
                    }
                check(pouch.npc == scroll.familiars[index]) {
                    "${scroll.name} pairs ${pouch.name} with familiar NPC ${scroll.familiars[index]}, " +
                        "but that pouch summons NPC ${pouch.npc}."
                }
                val clash = scrollByPouch.put(pouchItem, scroll)
                check(clash == null) {
                    "${pouch.name} is claimed by both ${clash?.name} and ${scroll.name}."
                }
            }
        }
        val unmapped = SummoningPouchData.values.filterNot { scrollByPouch.containsKey(it.pouch) }
        check(unmapped.isEmpty()) {
            "These pouches have no Summoning scroll: ${unmapped.joinToString { it.name }}"
        }
    }

    private fun validateCatalogue() {
        val missing = SummoningPouchData.values.filterNot { SummoningCatalogue.byPouch.containsKey(it) }
        check(missing.isEmpty()) {
            "These pouches have no catalogue row: ${missing.joinToString { it.name }}"
        }
        SummoningCatalogue.byPouch.values.forEach { entry ->
            check(entry.canFight == (entry.skillFocus != FamiliarSkillFocus.NONE)) {
                "${entry.pouch.name} must credit a skill if and only if it can fight, but it is " +
                    "combat level ${entry.combatLevel} with focus ${entry.skillFocus}."
            }
            check(entry.canFight == entry.isIn(FamiliarCategory.COMBAT)) {
                "${entry.pouch.name} has a combat level but is not categorised as a combat familiar."
            }
            val kind = entry.inventory.kind
            check(entry.isIn(FamiliarCategory.BEAST_OF_BURDEN) == (kind == FamiliarInventoryKind.BEAST_OF_BURDEN)) {
                "${entry.pouch.name} is categorised as a beast of burden but carries $kind."
            }
            check((kind == FamiliarInventoryKind.NONE) == (entry.inventory.capacity == 0)) {
                "${entry.pouch.name} declares $kind with capacity ${entry.inventory.capacity}."
            }
        }
    }

    /**
     * The catalogue is the sourced statement of what each familiar can carry; [BeastOfBurden] is
     * the implementation. Every carrier - beast of burden or forager - must have a container of
     * exactly the sourced size, carrying items in the sourced direction, and no container may
     * exist for a familiar that carries nothing.
     */
    private fun validateInventories() {
        SummoningCatalogue.byPouch.values.forEach { entry ->
            val storage = BeastOfBurden.storageFor(entry.pouch)
            if (entry.inventory.kind == FamiliarInventoryKind.NONE) {
                check(storage == null) {
                    "${entry.pouch.name} carries nothing but has container ${storage?.key?.name}."
                }
                return@forEach
            }
            val carrier =
                requireNotNull(storage) {
                    "${entry.pouch.name} carries ${entry.inventory.capacity} items but has no container."
                }
            check(carrier.key.capacity == entry.inventory.capacity) {
                "${entry.pouch.name} carries ${entry.inventory.capacity} items, " +
                    "but its container holds ${carrier.key.capacity}."
            }
            check(carrier.essenceOnly == entry.inventory.essenceOnly) {
                "${entry.pouch.name} essence-only is ${entry.inventory.essenceOnly} in the ledger " +
                    "but ${carrier.essenceOnly} in its container."
            }
            val forager = entry.inventory.kind == FamiliarInventoryKind.FORAGER
            check(carrier.withdrawOnly == forager) {
                if (forager) {
                    "${entry.pouch.name} is a forager, so its container must be withdraw-only."
                } else {
                    "${entry.pouch.name} is a beast of burden, so its container must accept deposits."
                }
            }
            check(BeastOfBurden.isCarrierNpc(entry.pouch.npc)) {
                "${entry.pouch.name} carries items but its NPC ${entry.pouch.npc} is not a carrier."
            }
            check(BeastOfBurden.isBobNpc(entry.pouch.npc) != forager) {
                "${entry.pouch.name} is registered under the wrong carrying contract."
            }
        }
    }

    private fun validateCombatTransforms() {
        SummoningPouchData.values.forEach { pouch ->
            val combat = SummoningCombatDefinitions.get(pouch)
            val nonCombat = !SummoningCatalogue[pouch].canFight
            check(combat.canFight != nonCombat) {
                if (nonCombat) {
                    "${pouch.name} is a forager and must not have a combat style."
                } else {
                    "${pouch.name} must be able to fight."
                }
            }
            if (nonCombat) {
                check(combat.combatNpc == null) {
                    "${pouch.name} has no combat form but declares transform NPC ${combat.combatNpc}."
                }
            } else {
                check(combat.combatNpc == pouch.npc + 1) {
                    "${pouch.name} summons NPC ${pouch.npc}, so its combat transform must be " +
                        "${pouch.npc + 1}, not ${combat.combatNpc}."
                }
            }
        }
    }
}
