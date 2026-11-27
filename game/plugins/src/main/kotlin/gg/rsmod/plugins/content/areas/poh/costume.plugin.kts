package gg.rsmod.plugins.content.areas.poh

/**
 * CONSTRUCTION-99 nachtrun 27-11 (C4): the "Select" buttons of the costume-room storage interface 467 ([PohStorageUi]); the built
 * pieces open it through the object override ([PohCostume.handle]).
 */
(0 until PohStorageUi.ROWS).forEach { row ->
    on_button(interfaceId = PohStorageUi.INTERFACE, component = PohStorageUi.buttonComponent(row)) {
        PohStorageUi.select(player, row)
    }
}
