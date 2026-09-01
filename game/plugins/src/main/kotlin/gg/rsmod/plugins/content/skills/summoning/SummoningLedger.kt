package gg.rsmod.plugins.content.skills.summoning

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
     * the implementation. Every beast of burden must already have a container of exactly the
     * sourced size, and no container may exist for a familiar that cannot carry anything.
     *
     * Foragers are the deliberate exception in the other direction: they are sourced as 30-slot
     * withdraw-only stores but have no container yet, so they are not asserted here.
     */
    private fun validateInventories() {
        SummoningCatalogue.byPouch.values.forEach { entry ->
            val storage = BeastOfBurden.storageFor(entry.pouch)
            if (entry.inventory.kind == FamiliarInventoryKind.BEAST_OF_BURDEN) {
                val key =
                    requireNotNull(storage) {
                        "${entry.pouch.name} carries ${entry.inventory.capacity} items but has no container."
                    }.key
                check(key.capacity == entry.inventory.capacity) {
                    "${entry.pouch.name} carries ${entry.inventory.capacity} items, " +
                        "but its container holds ${key.capacity}."
                }
                check(storage.essenceOnly == entry.inventory.essenceOnly) {
                    "${entry.pouch.name} essence-only is ${entry.inventory.essenceOnly} in the ledger " +
                        "but ${storage.essenceOnly} in its container."
                }
            } else if (storage != null) {
                // The albino rat's cheese store is a special internal inventory, not carrying capacity.
                check(entry.isIn(FamiliarCategory.FORAGER)) {
                    "${entry.pouch.name} carries nothing but has container ${storage.key.name}."
                }
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
