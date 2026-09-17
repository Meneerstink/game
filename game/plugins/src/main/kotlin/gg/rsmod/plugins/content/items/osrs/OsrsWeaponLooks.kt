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

    /** Sequences that carry the same id and animation in the 667 cache and in OSRS (RuneLite gameval names). */
    const val HUMAN_SPEAR_SPIKE = 428
    const val HUMAN_SCYTHE_SWEEP = 440

    val OSRS_GODSWORDS =
        intArrayOf(
            Items.ANCIENT_GODSWORD, Items.ARMADYL_GODSWORD_OR, Items.BANDOS_GODSWORD_OR, Items.SARADOMIN_GODSWORD_OR, Items.ZAMORAK_GODSWORD_OR,
            Items.GILDED_2H_SWORD,
        )
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
    }

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
     * The fang's stab has its own sound; its slash keeps the sword class audio.
     */
    fun attackSound(
        weaponId: Int,
        attackAnimation: Int,
    ): Int? =
        when (weaponId) {
            Items.DUAL_MACUAHUITL -> OsrsSfx.MACUAHUITL_CRUSH
            Items.OSMUMTENS_FANG -> if (attackAnimation == fangStab) OsrsSfx.OSMUMTENS_FANG_STAB else null
            else -> null
        }

    /** OSRS plays XBOWS_HUMAN_FIRE_AND_RELOAD_PVN against npcs for ordinary crossbows. */
    val PVN_CROSSBOWS = setOf(Items.ARMADYL_CROSSBOW, Items.DRAGON_CROSSBOW, Items.DRAGON_HUNTER_CROSSBOW, Items.HUNTERS_SUNLIGHT_CROSSBOW)
}
