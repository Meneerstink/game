package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.RenderAnimations

/*
 * OSRS-IMPORT: imported weapons whose stand / walk / run sequences are their own in OSRS (OsrsBas, built by OsrsBasImportTool). The
 * appearance block reads RenderAnimations before the item's cache param 644.
 */
RenderAnimations.register(OsrsBas.BALLISTA, Items.HEAVY_BALLISTA, Items.HEAVY_BALLISTA_OR)
RenderAnimations.register(OsrsBas.VENATOR_BOW, Items.VENATOR_BOW, Items.VENATOR_BOW_UNCHARGED)
RenderAnimations.register(OsrsBas.ABYSSAL_DAGGER, *AbyssalDagger.IDS)
RenderAnimations.register(OsrsBas.GODSWORD, *OsrsWeaponLooks.OSRS_GODSWORDS)
RenderAnimations.register(OsrsBas.NIGHTMARE_STAFF, *OsrsWeaponLooks.NIGHTMARE_STAVES)
RenderAnimations.register(OsrsBas.ZAMORAK_SPEAR, Items.BLUE_MOON_SPEAR)
RenderAnimations.register(OsrsBas.COLOSSAL_BLADE, Items.COLOSSAL_BLADE)
// N630 (17-11): the Keris partisan stands with the partisan UPRIGHT beside the body, arm down (OSRS video howgood.mp4 8 s) = exactly the
// staff stance (render 28 = Staff of air param 644; screens\N630\stance-compare2.png); the RuneLite replacer set "Spear" (item 25979)
// confirms stand 813 / walk 1205 / run 1210 = the staff pose set. The ToA import has no param 644 -> default stance held it diagonally.
RenderAnimations.register(28, *OsrsWeaponLooks.kerisIds().toIntArray())
