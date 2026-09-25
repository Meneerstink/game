package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.plugins.api.cfg.Items

/**
 * OSRS attack / block animations of the imported OSRS weapons, read by `CombatConfigs.getAttackAnimation` /
 * `getBlockAnimation` before the 667 weapon-class fallbacks (owner 2026-09-17c: every weapon animates exactly like OSRS).
 *
 * Sources: the sequences are the OSRS ones ([OsrsSeq], Jagex names from RuneLite `gameval/AnimationID.java`). Which weapon
 * and style plays which sequence: the Jagex names, the RuneLite combat-logger plugin's observed table
 * (SuperNerdEric/combat-logger `AnimationIds.java`) and the Zenyte-lineage equipment table (Near-Reality
 * `data/items/ItemDefinitions.json`, accurate / aggressive / controlled / defensive per weapon).
 *
 * [attack] is indexed by the attack-style button (0-3). [attackPvn] is what OSRS plays against npcs where it differs.
 */
object OsrsWeaponLooks {
    class Look(
        val attack: IntArray,
        val attackPvn: IntArray? = null,
        val block: Int? = null,
    )

    private fun all(seq: Int) = intArrayOf(seq, seq, seq, seq)

    /**
     * The classic OSRS sequences, imported with their OSRS frames on the player rig (batch osrsweaponseq) instead of relying on the
     * same-numbered 667 sequence (RuneLite gameval names).
     */
    const val HUMAN_SPEAR_SPIKE = OsrsSeq.HUMAN_SPEAR_SPIKE
    const val HUMAN_SCYTHE_SWEEP = OsrsSeq.HUMAN_SCYTHE_SWEEP
    const val HUMAN_SWORD_STAB = OsrsSeq.HUMAN_SWORD_STAB
    const val HUMAN_SWORD_DEF = OsrsSeq.HUMAN_SWORD_DEF
    const val HUMAN_SWORD_SLASH = OsrsSeq.HUMAN_SWORD_SLASH
    const val HUMAN_SWORD_LUNGE = OsrsSeq.HUMAN_SWORD_LUNGE

    val OSRS_GODSWORDS =
        intArrayOf(
            Items.ANCIENT_GODSWORD, Items.ARMADYL_GODSWORD_OR, Items.BANDOS_GODSWORD_OR, Items.SARADOMIN_GODSWORD_OR, Items.ZAMORAK_GODSWORD_OR,
        )

    /** The four imported Dark bow colours (OSRS 12765-12768). */
    val DARK_BOWS = intArrayOf(23801, 23802, 23803, 23804)
    val NIGHTMARE_STAVES =
        intArrayOf(Items.NIGHTMARE_STAFF, Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF)

    private val looks = mutableMapOf<Int, Look>()

    private fun register(
        look: Look,
        vararg items: Int,
    ) = items.forEach { looks[it] = look }

    init {
        // Abyssal dagger: Stab / Lunge / Slash / Block -> lunge, lunge, hack, lunge; own block.
        register(
            Look(
                intArrayOf(OsrsSeq.ABYSSAL_DAGGER_LUNGE, OsrsSeq.ABYSSAL_DAGGER_LUNGE, OsrsSeq.ABYSSAL_DAGGER_HACK, OsrsSeq.ABYSSAL_DAGGER_LUNGE),
                block = OsrsSeq.ABYSSAL_DAGGER_BLOCK,
            ),
            *AbyssalDagger.IDS,
        )
        register(
            Look(all(OsrsSeq.BALLISTA_ATTACK), attackPvn = all(OsrsSeq.BALLISTA_ATTACK_PVN), block = OsrsSeq.BALLISTA_DEFEND),
            Items.HEAVY_BALLISTA,
            Items.HEAVY_BALLISTA_OR,
        )
        register(Look(all(OsrsSeq.SNAKEBOSS_BLOWPIPE_ATTACK)), Items.TOXIC_BLOWPIPE)
        register(Look(all(OsrsSeq.SNAKEBOSS_BLOWPIPE_ATTACK_ORNAMENT)), Items.BLAZING_BLOWPIPE)
        register(Look(all(OsrsSeq.CAMPHOR_BLOWPIPE_ATTACK)), Items.CAMPHOR_BLOWPIPE)
        register(Look(all(OsrsSeq.IRONWOOD_BLOWPIPE_ATTACK)), Items.IRONWOOD_BLOWPIPE)
        register(Look(all(OsrsSeq.ROSEWOOD_BLOWPIPE_ATTACK)), Items.ROSEWOOD_BLOWPIPE)
        // Dragon hunter lance: Lunge / Swipe / Pound / Block.
        register(
            Look(
                intArrayOf(
                    OsrsSeq.HUMAN_DHUNTER_LANCE_ATTACK,
                    OsrsSeq.HUMAN_DHUNTER_LANCE_SLASH,
                    OsrsSeq.HUMAN_DHUNTER_LANCE_CRUSH,
                    OsrsSeq.HUMAN_DHUNTER_LANCE_ATTACK,
                ),
            ),
            Items.DRAGON_HUNTER_LANCE,
        )
        register(Look(all(OsrsSeq.PMOON_MACUAHUITL_CRUSH)), Items.DUAL_MACUAHUITL)
        register(Look(all(OsrsSeq.HUMAN_DRAGON_KNIFE)), Items.DRAGON_KNIFE)
        register(Look(all(OsrsSeq.HUMAN_DRAGON_KNIFE_P)), Items.DRAGON_KNIFE_P, Items.DRAGON_KNIFE_P_PLUS, Items.DRAGON_KNIFE_P_PLUS_PLUS)
        register(Look(all(OsrsSeq.ZCB_ATTACK), attackPvn = all(OsrsSeq.ZCB_ATTACK_PVN)), Items.ZARYTE_CROSSBOW)
        register(Look(all(OsrsSeq.VENATOR_BOW_SHOOT)), Items.VENATOR_BOW)
        register(Look(all(OsrsSeq.HUMAN_ATLATL_ATTACK_RANGED_01)), Items.ECLIPSE_ATLATL)
        register(Look(all(OsrsSeq.HUMAN_GLAIVE_RALOS01_CHARGED_THROW)), Items.TONALZTICS_OF_RALOS)
        register(Look(all(OsrsSeq.HUMAN_GLAIVE_RALOS01_UNCHARGED_THROW)), Items.TONALZTICS_OF_RALOS_UNCHARGED)
        // Imported godswords and the gilded 2h sword: Chop / Slash / Smash / Block. The RuneLite combat-logger observes exactly three
        // godsword attack sequences - DH_SWORD_UPDATE_SLASH, _SMASH and _BLOCK (the Block style swings the "block" sequence);
        // _DEFEND is the block animation (Zenyte-lineage table: blockAnimation 7056).
        register(
            Look(
                intArrayOf(OsrsSeq.DH_SWORD_UPDATE_SLASH, OsrsSeq.DH_SWORD_UPDATE_SLASH, OsrsSeq.DH_SWORD_UPDATE_SMASH, OsrsSeq.DH_SWORD_UPDATE_BLOCK),
                block = OsrsSeq.DH_SWORD_UPDATE_DEFEND,
            ),
            *OSRS_GODSWORDS,
        )
        register(Look(all(OsrsSeq.HUMAN_NIGHTMARE_STAFF_CRUSH)), *NIGHTMARE_STAVES)
        // Blue moon spear (combat-logger: HUMAN_ZAMORAKSPEAR_STAB / _SLASH / _LUNGE; "LUNGE" is the spear class's crush): Lunge / Swipe /
        // Pound / Block.
        register(
            Look(
                intArrayOf(OsrsSeq.HUMAN_ZAMORAKSPEAR_STAB, OsrsSeq.HUMAN_ZAMORAKSPEAR_SLASH, OsrsSeq.HUMAN_ZAMORAKSPEAR_LUNGE, OsrsSeq.HUMAN_ZAMORAKSPEAR_STAB),
                block = OsrsSeq.HUMAN_ZAMORAKSPEAR_BLOCK,
            ),
            Items.BLUE_MOON_SPEAR,
        )
        // Noxious halberd: Jab / Swipe / Fend = the OSRS halberd pair HUMAN_SPEAR_SPIKE 428 / HUMAN_SCYTHE_SWEEP 440 - the RuneLite
        // combat-logger's observed attack table lists exactly those two for ItemID.NOXIOUS_HALBERD, and the Zenyte-lineage table agrees.
        // Both sequences exist under the same id in 667 (the 667 halberd class plays 438 for the jab, which is what looked wrong).
        // "Virulence" is the NAME OF ITS SPECIAL: HUMAN_HALBERD_VIRULENCE_01-04 are special-attack sequences, not normal attacks (the
        // first build of this table misread them); _02 carries the special's sounds and is what the special plays.
        register(Look(intArrayOf(HUMAN_SPEAR_SPIKE, HUMAN_SCYTHE_SWEEP, HUMAN_SPEAR_SPIKE, HUMAN_SPEAR_SPIKE)), Items.NOXIOUS_HALBERD)

        // Owner 2026-09-24: "Toxic Staff en een aantal andere wapens hebben nog bij een whack de 667 attack animatie ... FIX alle OSRS
        // GEPORTE WEAPONS". Root cause: an imported weapon without its own row fell back to the 667 weapon class, and the 667 slash-sword
        // and Staff-of-light classes animate with RS HD sequences (15071 / 15072 / 15074 / 12806) that OSRS never had. OSRS plays the
        // classic sequences, which carry the same ids and frames in the 667 cache (RuneLite gameval names below).
        // Slash swords (OSRS Wiki weapon category "Slash sword": Chop / Slash / Lunge / Block = slash, slash, stab, slash): HUMAN_SWORD_SLASH
        // 390, HUMAN_SWORD_STAB 386, block HUMAN_SWORD_DEF 388 (RuneLite combat-logger: 390 "Slash", 386 "Stab"; xrsps weapon table agrees).
        register(
            Look(intArrayOf(HUMAN_SWORD_SLASH, HUMAN_SWORD_SLASH, HUMAN_SWORD_STAB, HUMAN_SWORD_SLASH), block = HUMAN_SWORD_DEF),
            Items.THIRDAGE_LONGSWORD, Items.GILDED_SCIMITAR, Items.KATANA, Items.DRAGON_SCIMITAR_OR, Items.ARCLIGHT, Items.ARCLIGHT_INACTIVE,
            Items.EMBERLIGHT, Items.VOIDWAKER, Items.RUNE_SCIMITAR_GUTHIX, Items.RUNE_SCIMITAR_SARADOMIN, Items.RUNE_SCIMITAR_ZAMORAK,
        )
        // Stab sword (OSRS Wiki "Belle's folly": Accurate stab / Lunge stab / Slash / Block stab): HUMAN_SWORD_STAB 386, HUMAN_SWORD_LUNGE
        // 392, HUMAN_SWORD_SLASH 390 (xrsps stab-sword table 386 / 392 / 390 / 386).
        register(Look(intArrayOf(HUMAN_SWORD_STAB, HUMAN_SWORD_LUNGE, HUMAN_SWORD_SLASH, HUMAN_SWORD_STAB), block = HUMAN_SWORD_DEF), Items.BELLES_FOLLY)
        // Bladed staves (Staff of the dead, Toxic staff, Staff of balance): every melee style swings HUMAN_SCYTHE_SWEEP 440 (per-item OSRS
        // table of the Glabay OSRS server, attack_animations.json: 11791 / 12902 / 12904 -> 440). Block: no source names it (SOURCE_GAP),
        // so the class block stays.
        register(
            Look(all(HUMAN_SCYTHE_SWEEP)),
            Items.STAFF_OF_THE_DEAD, Items.TOXIC_STAFF_UNCHARGED, Items.TOXIC_STAFF_OF_THE_DEAD, Items.STAFF_OF_BALANCE,
        )
        // Staves (Bash / Pound / Focus): HUMAN_AXE_CHOP 393 - RuneLite combat-logger, observed in OSRS: "HUMAN_AXE_CHOP, // Staff bash".
        // (The xrsps table says 419 HUMAN_STAFFORB_PUMMEL; the combat-logger is the in-game observation, so it wins.)
        register(
            Look(all(OsrsSeq.HUMAN_AXE_CHOP)),
            Items.TRIDENT_OF_THE_SEAS, Items.TRIDENT_OF_THE_SEAS_FULL, Items.UNCHARGED_TRIDENT, Items.TRIDENT_OF_THE_SWAMP,
            Items.UNCHARGED_TOXIC_TRIDENT, Items.TRIDENT_OF_THE_SEAS_E, Items.UNCHARGED_TRIDENT_E, Items.TRIDENT_OF_THE_SWAMP_E,
            Items.UNCHARGED_TOXIC_TRIDENT_E, Items.SANGUINESTI_STAFF, Items.SANGUINESTI_STAFF_UNCHARGED, Items.MYSTIC_SMOKE_STAFF,
            Items.ANCIENT_SCEPTRE, Items.ANCIENT_SCEPTRE_L, Items.BLOOD_ANCIENT_SCEPTRE, Items.ICE_ANCIENT_SCEPTRE, Items.SMOKE_ANCIENT_SCEPTRE,
            Items.SHADOW_ANCIENT_SCEPTRE, Items.BLOOD_ANCIENT_SCEPTRE_L, Items.ICE_ANCIENT_SCEPTRE_L, Items.SMOKE_ANCIENT_SCEPTRE_L,
            Items.SHADOW_ANCIENT_SCEPTRE_L, Items.PURGING_STAFF, Items.SMOKE_BATTLESTAFF, Items.MIST_BATTLESTAFF, Items.MYSTIC_MIST_STAFF,
            Items.DUST_BATTLESTAFF, Items.MYSTIC_DUST_STAFF, Items.LAVA_BATTLESTAFF_OR, Items.STEAM_BATTLESTAFF_OR, Items.MYSTIC_STEAM_STAFF_OR,
        )
        // Wands: HUMAN_STAFF_PUMMEL 414 - combat-logger "Wand melee auto"; the Glabay per-item table agrees (21006 Kodai, 12422 3rd age wand).
        register(Look(all(OsrsSeq.HUMAN_STAFF_PUMMEL)), Items.KODAI_WAND, Items.THIRDAGE_WAND, Items.DRAGON_HUNTER_WAND)
        // Thrown: against players OSRS plays the classic throw (darts II_HUMAN_DART_THROW 6600, thrownaxes HUMAN_STAKE2 929, same ids in
        // 667), against npcs the _PVN versions (combat-logger: "II_HUMAN_DART_THROW_PVN, // Dart throw", "HUMAN_STAKE2_PVN, // Rune knife,
        // thrownaxe").
        // Every dart, not only the imported ones: the 667 copy of seq 6600 has no replay mode (default RESTART_LOOP) and lasts exactly
        // 2 ticks, so a rapid 2-tick throw arriving while the last one was still on its final frame did not restart - the dart flew
        // without a throw (owner 2026-09-25, dragon darts). The OSRS copies (15801 / 15791) carry replay mode 1 and restart.
        register(
            Look(all(OsrsSeq.II_HUMAN_DART_THROW), attackPvn = all(OsrsSeq.II_HUMAN_DART_THROW_PVN)),
            *gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts.DARTS.toIntArray(),
        )
        register(Look(all(OsrsSeq.HUMAN_STAKE2), attackPvn = all(OsrsSeq.HUMAN_STAKE2_PVN)), Items.DRAGON_THROWNAXE)
        // Chinchompas: HUMAN_CHINCHOMPA_ATTACK 2779, against npcs _PVN 7618 (combat-logger "HUMAN_CHINCHOMPA_ATTACK_PVN, // Chinchompa").
        register(Look(all(OsrsSeq.HUMAN_CHINCHOMPA_ATTACK), attackPvn = all(OsrsSeq.HUMAN_CHINCHOMPA_ATTACK_PVN)), Items.BLACK_CHINCHOMPA)
        // Axes (Chop / Hack slash, Smash crush, Block slash): HUMAN_AXE_HACK 395 "Axe", HUMAN_BLUNT_POUND 401 "Crush" (combat-logger).
        register(
            Look(intArrayOf(OsrsSeq.HUMAN_AXE_HACK, OsrsSeq.HUMAN_AXE_HACK, OsrsSeq.HUMAN_BLUNT_POUND, OsrsSeq.HUMAN_AXE_HACK)),
            Items.THIRDAGE_AXE, Items.GILDED_AXE,
        )
        // Pickaxes: HUMAN_BLUNT_SPIKE 400 (combat-logger "Pickaxe smash"; xrsps: pickaxe spike 400).
        register(Look(all(OsrsSeq.HUMAN_BLUNT_SPIKE)), Items.THIRDAGE_PICKAXE, Items.GILDED_PICKAXE, Items.DRAGON_PICKAXE_OR, Items.DRAGON_PICKAXE_OR_UPGRADED)
        // Blunt weapons (Pound / Pummel / Block, all crush): HUMAN_BLUNT_POUND 401 (combat-logger "Crush, DWH"; Glabay 13576 -> 401).
        register(Look(all(OsrsSeq.HUMAN_BLUNT_POUND)), Items.DRAGON_WARHAMMER, Items.GILDED_SPADE, Items.DRAGON_CANE)
        // Granite maul: SLAYER_GRANITE_MAUL_ATTACK 1665 (combat-logger "Granite maul").
        register(Look(all(OsrsSeq.SLAYER_GRANITE_MAUL_ATTACK)), Items.GRANITE_MAUL_ORNATE_HANDLE)
        // Spears (Lunge stab / Swipe slash / Pound crush / Block stab): HUMAN_SPEAR_SPIKE 428 "Spear stab", HUMAN_SCYTHE_SWEEP 440
        // "Spear slash", HUMAN_SPEAR_LUNGE 429 "Spear crush" (combat-logger).
        register(
            Look(intArrayOf(HUMAN_SPEAR_SPIKE, HUMAN_SCYTHE_SWEEP, OsrsSeq.HUMAN_SPEAR_LUNGE, HUMAN_SPEAR_SPIKE)),
            Items.GILDED_SPEAR, Items.GILDED_HASTA,
        )
        // Two-handed swords (Chop / Slash slash, Smash crush, Block slash): HUMAN_DHSWORD_SLASH 407 "2h slash", HUMAN_DHSWORD_CHOP 406
        // "2h crush" (combat-logger).
        register(
            Look(intArrayOf(OsrsSeq.HUMAN_DHSWORD_SLASH, OsrsSeq.HUMAN_DHSWORD_SLASH, OsrsSeq.HUMAN_DHSWORD_CHOP, OsrsSeq.HUMAN_DHSWORD_SLASH)),
            Items.GILDED_2H_SWORD,
        )
        // Claws (Chop / Slash slash, Lunge stab, Block slash): slash HUMAN_AXE_CHOP 393 (combat-logger lists the dragon claws under 393),
        // stab D_CLAWS_PUNCH 1067 ("Claw stab").
        register(
            Look(intArrayOf(OsrsSeq.HUMAN_AXE_CHOP, OsrsSeq.HUMAN_AXE_CHOP, OsrsSeq.D_CLAWS_PUNCH, OsrsSeq.HUMAN_AXE_CHOP)),
            Items.BURNING_CLAWS,
        )
        // Imported whips: SLAYER_ABYSSAL_WHIP_ATTACK 1658 / _DEFEND 1659 as OSRS frames.
        register(
            Look(all(OsrsSeq.SLAYER_ABYSSAL_WHIP_ATTACK), block = OsrsSeq.SLAYER_ABYSSAL_WHIP_DEFEND),
            Items.FROZEN_ABYSSAL_WHIP, Items.VOLCANIC_ABYSSAL_WHIP,
        )
        // Bows and crossbows without their own sequence: HUMAN_BOW 426 "Bow"; crossbows XBOWS_HUMAN_FIRE_AND_RELOAD 4230 (PVN below).
        register(
            Look(all(OsrsSeq.HUMAN_BOW)),
            Items.TWISTED_BOW, Items.BOW_OF_FAERDHINEN, Items.BOW_OF_FAERDHINEN_INACTIVE, Items.BOW_OF_FAERDHINEN_C, Items.MAGIC_SHORTBOW_I,
            Items.CRYSTAL_BOW_OSRS, Items.CRYSTAL_BOW_OSRS_INACTIVE, Items.CRAWS_BOW_U, Items.CRAWS_BOW, Items.WEBWEAVER_BOW_U, Items.WEBWEAVER_BOW,
            Items.VENATOR_BOW_UNCHARGED, Items.SCORCHING_BOW, Items.THIRDAGE_BOW, *DARK_BOWS,
        )
        register(
            Look(all(OsrsSeq.XBOWS_HUMAN_FIRE_AND_RELOAD), attackPvn = all(OsrsSeq.XBOWS_HUMAN_FIRE_AND_RELOAD_PVN)),
            Items.ARMADYL_CROSSBOW, Items.DRAGON_CROSSBOW, Items.DRAGON_HUNTER_CROSSBOW, Items.HUNTERS_SUNLIGHT_CROSSBOW,
        )
    }

    /** Every imported OSRS weapon with its own OSRS look (guard: `OsrsWeaponLooksTests`). */
    val COVERED: Set<Int> get() = looks.keys + Items.OSMUMTENS_FANG

    /** Osmumten's fang: only the stab styles have their own sequence; Slash keeps the sword slash (combat-logger: HUMAN_SWORD_SLASH). */
    private val fangStab = OsrsSeq.HUMAN_OSMUMTENS_FANG

    fun attackAnimation(
        weaponId: Int,
        styleIndex: Int,
        againstNpc: Boolean,
        stabStyle: Boolean,
    ): Int? {
        if (weaponId == Items.OSMUMTENS_FANG) return if (stabStyle) fangStab else null
        val look = looks[weaponId] ?: return null
        val table = if (againstNpc) look.attackPvn ?: look.attack else look.attack
        return table[styleIndex.coerceIn(0, 3)]
    }

    fun blockAnimation(weaponId: Int): Int? = looks[weaponId]?.block

    /**
     * The weapon's own OSRS attack sound (Jagex config names, OSRS Wiki "List of sound IDs"), or null for the item's 667 attack audio.
     * The fang plays the stab sword sounds (its own fang sounds belong to the special).
     */
    fun attackSound(
        weaponId: Int,
        attackAnimation: Int,
    ): Int? =
        when (weaponId) {
            Items.DUAL_MACUAHUITL -> OsrsSfx.MACUAHUITL_CRUSH
            // Owner 2026-09-18 "uses special sound for a non special hit": OSRS Wiki sound list - stab plays stabsword_stab 2549,
            // slash stabsword_slash 2548; a_r_osmumtens_fang_sword_stab_01 (9366) is part 2 of the special only.
            Items.OSMUMTENS_FANG -> if (attackAnimation == fangStab) gg.rsmod.plugins.api.cfg.Sfx.STABSWORD_STAB else gg.rsmod.plugins.api.cfg.Sfx.STABSWORD_SLASH
            // Owner 2026-09-19 "noxious halberd makes no sound": OSRS Wiki "Noxious halberd" sounds - stab staff_stab 2562, slash
            // scythe_slash 2524 (same ids/names in the 667 sound table). Its sequences 428 / 440 carry no frame sounds.
            Items.NOXIOUS_HALBERD -> if (attackAnimation == HUMAN_SCYTHE_SWEEP) gg.rsmod.plugins.api.cfg.Sfx.SCYTHE_SLASH else gg.rsmod.plugins.api.cfg.Sfx.STAFF_STAB
            // Every whip-class weapon swings the OSRS whip sequence (Animations.WHIP), which has no frame sounds: OSRS "whip" 2720.
            else -> if (attackAnimation == gg.rsmod.plugins.content.combat.Animations.WHIP.slash.id || attackAnimation == OsrsSeq.SLAYER_ABYSSAL_WHIP_ATTACK) gg.rsmod.plugins.api.cfg.Sfx.WHIP else null
        }

    /** OSRS plays XBOWS_HUMAN_FIRE_AND_RELOAD_PVN against npcs for ordinary crossbows. */
    val PVN_CROSSBOWS = setOf(Items.ARMADYL_CROSSBOW, Items.DRAGON_CROSSBOW, Items.DRAGON_HUNTER_CROSSBOW, Items.HUNTERS_SUNLIGHT_CROSSBOW)
}
