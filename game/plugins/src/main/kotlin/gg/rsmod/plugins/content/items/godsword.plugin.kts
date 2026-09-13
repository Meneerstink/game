package gg.rsmod.plugins.content.items

/**
 * "Dismantle" on a godsword, splitting it back into its hilt and the Godsword blade.
 *
 * All four godswords carry `Dismantle` as inventory option 3 in the production cache
 * (`./gradlew :game:runItemParamProbeTool --args="<cache> 11694 11696 11698 11700"` ->
 * `INV_MENU=[null, Wield, Dismantle, null, null]` for each), but nothing was ever bound to it, so
 * `OpHeld3Handler` fell through to its `Unhandled item action` fallback - the reported symptom.
 *
 * The reverse direction (hilt on blade) is registered as four `CombinationData` entries rather than
 * being duplicated here, so that dismantling is never a one-way item sink.
 */

val godswordHilts =
    mapOf(
        Items.ARMADYL_GODSWORD to Items.ARMADYL_HILT,
        Items.BANDOS_GODSWORD to Items.BANDOS_HILT,
        Items.SARADOMIN_GODSWORD to Items.SARADOMIN_HILT,
        Items.ZAMORAK_GODSWORD to Items.ZAMORAK_HILT,
        // OSRS-IMPORT: the Ancient godsword carries Dismantle upstream (option 3) and splits into the Ancient hilt + blade.
        Items.ANCIENT_GODSWORD to Items.ANCIENT_HILT,
    )

godswordHilts.forEach { (godsword, hilt) ->
    on_item_option(item = godsword, option = "Dismantle") {
        // One sword becomes two items, so a single free slot is needed on top of the one the
        // sword itself vacates.
        if (player.inventory.freeSlotCount < 1) {
            player.message("You don't have enough inventory space to do that.")
            return@on_item_option
        }
        if (!player.inventory
                .remove(
                    item = godsword,
                    beginSlot = player.getInteractingItemSlot(),
                ).hasSucceeded()
        ) {
            return@on_item_option
        }
        player.inventory.add(item = hilt, assureFullInsertion = true)
        player.inventory.add(item = Items.GODSWORD_BLADE, assureFullInsertion = true)
        player.filterableMessage("You detach the hilt from the godsword blade.")
    }
}
