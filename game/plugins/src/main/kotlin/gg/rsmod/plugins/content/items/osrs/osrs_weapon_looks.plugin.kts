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
// N537-HOUDING (25-11): the Corrupted / Deadman weapon variants hold their weapon like the base weapon (OSRS gives a variant the base
// weapon's stance); the imports had no param 644 and stood in the unarmed set 1426. Same BAS as the base item (667 param 644 or the
// OsrsBas override above); checked man + woman next to the Wiki equipped images (work\claude-scripts\n537\fixh).
RenderAnimations.register(1582, Items.DMM2_CORRUPTED_VOIDWAKER_OSRS_28531, Items.DMM2_VOIDWAKER_DEADMAN_OSRS_29607)
RenderAnimations.register(OsrsBas.GODSWORD, Items.DMM2_CORRUPTED_ARMADYL_GODSWORD_OSRS_28537, Items.DMM2_ARMADYL_GODSWORD_DEADMAN_OSRS_29605)
RenderAnimations.register(28, Items.DMM2_CORRUPTED_TUMEKEN_S_SHADOW_OSRS_28547, Items.DMM2_CORRUPTED_TUMEKEN_S_SHADOW_UNCHARGED_OSRS_28549)
RenderAnimations.register(303, Items.DMM2_CORRUPTED_DARK_BOW_OSRS_29599, Items.DMM2_DARK_BOW_DEADMAN_OSRS_29611)
RenderAnimations.register(
    OsrsBas.NIGHTMARE_STAFF,
    Items.DMM2_CORRUPTED_VOLATILE_NIGHTMARE_STAFF_OSRS_29602,
    Items.DMM2_VOLATILE_NIGHTMARE_STAFF_DEADMAN_OSRS_29609,
)
