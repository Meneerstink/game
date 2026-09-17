package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.RenderAnimations

/*
 * OSRS-IMPORT: imported weapons whose stand / walk / run sequences are their own in OSRS (OsrsBas, built by OsrsBasImportTool). The
 * appearance block reads RenderAnimations before the item's cache param 644.
 */
RenderAnimations.register(OsrsBas.BALLISTA, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR)
RenderAnimations.register(OsrsBas.VENATOR_BOW, Items.VENATOR_BOW, Items.VENATOR_BOW_UNCHARGED)
RenderAnimations.register(OsrsBas.ABYSSAL_DAGGER, *AbyssalDagger.IDS)
