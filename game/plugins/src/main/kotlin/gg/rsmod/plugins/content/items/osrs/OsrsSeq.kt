package gg.rsmod.plugins.content.items.osrs

/**
 * OSRS player sequences imported into both rev-667 caches by `OsrsFxImportTool` batch "weaponseq1" (tx-20260917-182310, owner
 * 2026-09-17c: every imported weapon animates exactly like OSRS). Local id = the rev-667 sequence id; the OSRS id and Jagex name
 * come from RuneLite `gameval/AnimationID.java`. The frames animate the human rig merged with the 667 rig
 * (`OsrsFxImportTool.mergePlayerBase`). `_PVN` is the variant the OSRS server plays against npcs.
 */
object OsrsSeq {
    const val ABYSSAL_DAGGER_HACK = 15409 // OSRS 3294
    const val ABYSSAL_DAGGER_BLOCK = 15410 // OSRS 3295
    const val ABYSSAL_DAGGER_IDLE = 15411 // OSRS 3296
    const val ABYSSAL_DAGGER_LUNGE = 15412 // OSRS 3297
    const val ABYSSAL_DAGGER_SPECIAL = 15413 // OSRS 3300
    const val BALLISTA_ATTACK = 15414 // OSRS 7218
    const val BALLISTA_DEFEND = 15415 // OSRS 7219
    const val BALLISTA_READY = 15416 // OSRS 7220
    const val BALLISTA_RUN = 15417 // OSRS 7221
    const val BALLISTA_SPECIAL_ATTACK = 15418 // OSRS 7222
    const val BALLISTA_WALK = 15419 // OSRS 7223
    const val BALLISTA_ATTACK_PVN = 15420 // OSRS 7555
    const val BALLISTA_SPECIAL_ATTACK_PVN = 15421 // OSRS 7556
    const val SNAKEBOSS_BLOWPIPE_ATTACK = 15422 // OSRS 5061 (Toxic blowpipe)
    const val SNAKEBOSS_BLOWPIPE_ATTACK_ORNAMENT = 15423 // OSRS 10656 (Blazing blowpipe)
    const val CAMPHOR_BLOWPIPE_ATTACK = 15424 // OSRS 13142
    const val IRONWOOD_BLOWPIPE_ATTACK = 15425 // OSRS 13143
    const val ROSEWOOD_BLOWPIPE_ATTACK = 15426 // OSRS 13144
    const val ROSEWOOD_BLOWPIPE_SPECIAL_ATTACK = 15427 // OSRS 13145
    const val HUMAN_OSMUMTENS_FANG = 15428 // OSRS 9471
    const val WEAPON_SWORD_OSMUMTEN03_SPECIAL = 15429 // OSRS 11222
    const val HUMAN_DHUNTER_LANCE_ATTACK = 15430 // OSRS 8288
    const val HUMAN_DHUNTER_LANCE_SLASH = 15431 // OSRS 8289
    const val HUMAN_DHUNTER_LANCE_CRUSH = 15432 // OSRS 8290
    const val DRAGON_WARHAMMER_SA_PLAYER = 15433 // OSRS 1378
    const val HUMAN_SPECIAL02_VOIDWAKER = 15434 // OSRS 11275
    const val HUMAN_WEAPON_BURNING_CLAWS_02_SPEC = 15435 // OSRS 11140
    const val HUMAN_WEAPON_EMBERLIGHT_01_SPEC = 15436 // OSRS 11138
    const val DARK_SPEC_PLAYER = 15437 // OSRS 2890 (Arclight / Darklight)
    const val NGS_SPECIAL_PLAYER = 15438 // OSRS 9171 (Ancient godsword)
    const val PMOON_MACUAHUITL_CRUSH = 15439 // OSRS 10989
    const val HUMAN_DRAGON_KNIFE = 15440 // OSRS 8194
    const val HUMAN_DRAGON_KNIFE_P = 15441 // OSRS 8195
    const val HUMAN_DRAGON_TKNIVES_SPEC = 15442 // OSRS 8291
    const val HUMAN_DRAGON_TKNIVES_SPEC_POISON = 15443 // OSRS 8292
    const val HUMAN_SPECIAL01_WEBWEAVER = 15444 // OSRS 9964
    const val ZCB_ATTACK = 15445 // OSRS 9166
    const val ZCB_ATTACK_PVN = 15446 // OSRS 9168
    const val XBOWS_HUMAN_FIRE_AND_RELOAD_PVN = 15447 // OSRS 7552
    const val VENATOR_BOW_READY = 15448 // OSRS 9857
    const val VENATOR_BOW_SHOOT = 15449 // OSRS 9858
    const val VENATOR_BOW_WALK = 15450 // OSRS 9859
    const val VENATOR_BOW_RUN = 15451 // OSRS 9860
    const val VENATOR_BOW_STEPLEFT = 15452 // OSRS 9861
    const val VENATOR_BOW_STEPRIGHT = 15453 // OSRS 9862
    const val VENATOR_BOW_TURN = 15454 // OSRS 9863
    const val HUMAN_ATLATL_ATTACK_RANGED_01 = 15455 // OSRS 11057
    const val HUMAN_SPECIAL_ATLATL_01 = 15456 // OSRS 11060
    const val HUMAN_GLAIVE_RALOS01_CHARGED_SPECIAL = 15457 // OSRS 10914
    const val HUMAN_GLAIVE_RALOS01_UNCHARGED_SPECIAL = 15458 // OSRS 10916
    const val HUMAN_GLAIVE_RALOS01_UNCHARGED_THROW = 15459 // OSRS 10922
    const val HUMAN_GLAIVE_RALOS01_CHARGED_THROW = 15460 // OSRS 10923
    const val NIGHTMARE_STAFF_SPECIAL = 15461 // OSRS 8532
    const val HUMAN_HALBERD_VIRULENCE_01 = 15462 // OSRS 11513 (Noxious halberd)
    const val HUMAN_HALBERD_VIRULENCE_02 = 15463 // OSRS 11514 - the special: its frames play noxious_halberd_special_attack_build/impact
    const val HUMAN_HALBERD_VIRULENCE_03 = 15464 // OSRS 11515
    const val HUMAN_HALBERD_VIRULENCE_04 = 15465 // OSRS 11517
}

/** OSRS synth sounds of the same batch; names from the OSRS Wiki "List of sound IDs" (Jagex config names). */
object OsrsSfx {
    const val NOXIOUS_HALBERD_SPECIAL_BUILD = 10242 // OSRS 9403 noxious_halberd_special_attack_build_01
    const val NOXIOUS_HALBERD_SPECIAL_IMPACT = 10243 // OSRS 9404 noxious_halberd_special_attack_impact_01
    const val MACUAHUITL_SPECIAL = 10244 // OSRS 7917 varlamore_pm_macuahuitl_special_01
    const val MACUAHUITL_CRUSH = 10245 // OSRS 7930 varlamore_pm_macuahuitl_crush_01
    const val BURNING_CLAWS_SWIPE = 10246 // OSRS 9316 burning_claws_swipe_01
    const val OSMUMTENS_FANG_METALLIC_WOOSH = 10247 // OSRS 9365 a_r_osmumtens_fang_sword_metallic_woosh_01
    const val OSMUMTENS_FANG_STAB = 10248 // OSRS 9366 a_r_osmumtens_fang_sword_stab_01
    const val OSMUMTENS_FANG_WOOSH_02 = 10249 // OSRS 9367
    const val OSMUMTENS_FANG_WOOSH_01 = 10250 // OSRS 9368
}

/** Body-animation sets built by `OsrsBasImportTool` (tx-20260917-184006): the 667 default human set with OSRS stand / walk / run. */
object OsrsBas {
    const val BALLISTA = 2212 // BALLISTA_READY / _WALK / _RUN
    const val VENATOR_BOW = 2213 // HUMAN_WEAPON_BOW_VENATOR01_READY / _WALK / _RUN / _TURN / _STEPLEFT / _STEPRIGHT
    const val ABYSSAL_DAGGER = 2214 // ABYSSAL_DAGGER_IDLE
}
