package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.Animation
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.CombatStyle
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Darts
import gg.rsmod.plugins.content.combat.strategy.ranged.ammo.Knives

val FIRST = CombatStyle.FIRST
val SECOND = CombatStyle.SECOND
val THIRD = CombatStyle.THIRD
val FOURTH = CombatStyle.FOURTH

data class style(
    /**
     * Combat Style, CombatStyle.FIRST
     */
    val combatStyle: CombatStyle,
    /**
     *Attack Animation, Animation(id)
     */
    val attackAnimation: Animation,
)

enum class CombatAnimation(
    vararg val combatstyle: style,
    val blockAnimation: Animation = Animation(424),
    val weaponType: WeaponType = WeaponType.NONE,
    val equipType: EquipmentType = EquipmentType.WEAPON,
    val itemIds: List<Int> = emptyList(),
) {
    // DEFAULTS

    UNARMED(
        style(CombatStyle.FIRST, Animations.PUNCH.crush),
        style(CombatStyle.SECOND, Animations.KICK.crush),
        style(CombatStyle.THIRD, Animations.PUNCH.crush),
        blockAnimation = Animations.BLOCK.block,
        weaponType = WeaponType.NONE,
        itemIds = listOf(-1),
    ),

    SHIELD(
        blockAnimation = Animations.SHIELD.block,
        equipType = EquipmentType.SHIELD,
    ),

    WHIP(
        style(CombatStyle.FIRST, Animations.WHIP.slash),
        style(CombatStyle.SECOND, Animations.WHIP.slash),
        style(CombatStyle.THIRD, Animations.WHIP.slash),
        blockAnimation = Animations.WHIP.block,
        weaponType = WeaponType.WHIP,
    ),

    BOW(
        style(CombatStyle.FIRST, Animations.BOW.default),
        style(CombatStyle.SECOND, Animations.BOW.default),
        style(CombatStyle.THIRD, Animations.BOW.default),
        weaponType = WeaponType.BOW,
    ),
    SLING(
        style(CombatStyle.FIRST, Animations.SLING.default),
        style(CombatStyle.SECOND, Animations.SLING.default),
        style(CombatStyle.THIRD, Animations.SLING.default),
        weaponType = WeaponType.SLING,
    ),

    CROSSBOW(
        style(CombatStyle.FIRST, Animations.CROSSBOW.default),
        style(CombatStyle.SECOND, Animations.CROSSBOW.default),
        style(CombatStyle.THIRD, Animations.CROSSBOW.default),
        weaponType = WeaponType.CROSSBOW,
    ),

    STAFF(
        style(CombatStyle.FIRST, Animations.STAFF.crush),
        style(CombatStyle.SECOND, Animations.STAFF.crush),
        style(CombatStyle.THIRD, Animations.STAFF.crush),
        blockAnimation = Animations.STAFF.block,
        weaponType = WeaponType.STAFF,
    ),

    LONGSWORD(
        style(CombatStyle.FIRST, Animations.LONGSWORD.slash),
        style(CombatStyle.SECOND, Animations.LONGSWORD.slash),
        style(CombatStyle.THIRD, Animations.LONGSWORD.stab),
        style(CombatStyle.FOURTH, Animations.LONGSWORD.slash),
        blockAnimation = Animations.LONGSWORD.block,
        weaponType = WeaponType.LONG_SWORD,
        equipType = EquipmentType.WEAPON,
    ),

    DAGGER(
        style(CombatStyle.FIRST, Animations.SHORTSWORD.stab),
        style(CombatStyle.SECOND, Animations.SHORTSWORD.stab),
        style(CombatStyle.THIRD, Animations.SHORTSWORD.slash),
        style(CombatStyle.FOURTH, Animations.SHORTSWORD.stab),
        blockAnimation = Animations.SHORTSWORD.block,
        weaponType = WeaponType.DAGGER,
        equipType = EquipmentType.WEAPON,
    ),

    MACE(
        style(CombatStyle.FIRST, Animations.MACE.crush),
        style(CombatStyle.SECOND, Animations.MACE.crush),
        style(CombatStyle.THIRD, Animations.MACE.stab),
        style(CombatStyle.FOURTH, Animations.MACE.crush),
        blockAnimation = Animations.MACE.block,
        weaponType = WeaponType.MACE,
        equipType = EquipmentType.WEAPON,
    ),

    HALBERD(
        style(CombatStyle.FIRST, Animations.HALBERD.stab),
        style(CombatStyle.SECOND, Animations.HALBERD.slash),
        style(CombatStyle.THIRD, Animations.HALBERD.stab),
        blockAnimation = Animations.HALBERD.block,
        weaponType = WeaponType.HALBERD,
        equipType = EquipmentType.WEAPON,
    ),

    SPEAR(
        style(CombatStyle.FIRST, Animations.SPEAR.stab),
        // Swipe (slash) is the spear sweep 440 - Void 667 weapon.anims.toml spear_swipe = 440 and Novite 667
        // getWeaponAttackEmote "spear" style 1 = 440 agree; 429 is not a swipe in either (D5 family check, Vesta's spear).
        style(CombatStyle.SECOND, Animation(440)),
        style(CombatStyle.THIRD, Animations.SPEAR.stab),
        blockAnimation = Animations.SPEAR.block,
        weaponType = WeaponType.SPEAR,
        equipType = EquipmentType.WEAPON,
    ),

    HAMMER(
        style(CombatStyle.FIRST, Animations.WARHAMMER.crush),
        style(CombatStyle.SECOND, Animations.WARHAMMER.crush),
        style(CombatStyle.THIRD, Animations.WARHAMMER.crush),
        blockAnimation = Animations.WARHAMMER.block,
        weaponType = WeaponType.HAMMER,
        equipType = EquipmentType.WEAPON,
    ),

    HAMMER_EXTRA(
        style(CombatStyle.FIRST, Animations.WARHAMMER.stab),
        style(CombatStyle.SECOND, Animations.WARHAMMER.slash),
        style(CombatStyle.THIRD, Animations.WARHAMMER.stab),
        blockAnimation = Animations.WARHAMMER.block,
        weaponType = WeaponType.HAMMER_EXTRA,
        equipType = EquipmentType.WEAPON,
    ),

    CLAWS(
        style(CombatStyle.FIRST, Animations.CLAWS.slash),
        style(CombatStyle.SECOND, Animations.CLAWS.slash),
        style(CombatStyle.THIRD, Animations.CLAWS.stab),
        style(CombatStyle.FOURTH, Animations.CLAWS.slash),
        blockAnimation = Animations.CLAWS.block,
        weaponType = WeaponType.CLAWS,
    ),

    // Staff of light class (Void donor weapon.anims.toml: staff_of_light_jab 15072, _swipe 15071, _fend 414, _defend 12806). The imported
    // OSRS bladed staves share the class; their own OSRS attack sequences are not documented by any source read so far (SOURCE_GAP).
    BLADED_STAFF(
        style(CombatStyle.FIRST, Animation(15072)),
        style(CombatStyle.SECOND, Animation(15071)),
        style(CombatStyle.THIRD, Animation(414)),
        blockAnimation = Animation(12806),
        weaponType = WeaponType.BLADED_STAFF,
    ),

    // NON DEFAULTS, additions under here

    KNIFE(
        style(CombatStyle.FIRST, Animations.THROWING_KNIFE.default),
        style(CombatStyle.SECOND, Animations.THROWING_KNIFE.default),
        style(CombatStyle.THIRD, Animations.THROWING_KNIFE.default),
        weaponType = WeaponType.THROWN,
        itemIds = listOf(*Knives.KNIVES),
    ),

    DART(
        style(CombatStyle.FIRST, Animations.THROWING_DART.default),
        style(CombatStyle.SECOND, Animations.THROWING_DART.default),
        style(CombatStyle.THIRD, Animations.THROWING_DART.default),
        weaponType = WeaponType.THROWN,
        // OSRS-IMPORT: blowpipes attack with their imported OSRS sequences (OsrsWeaponLooks, read first); this entry only keeps
        // their block animation in the thrown class.
        itemIds = listOf(*Darts.DARTS, *gg.rsmod.plugins.content.items.osrs.Blowpipe.CHARGED_IDS.toTypedArray()),
    ),

    // Morrigan's javelins and throwing axes throw with their own sequences, not the generic thrown 929 (owner 2026-10-08, intro
    // scene: "i saw the hit but not the weapon"): Darkan items config.json attackAnims 13879-13882 / 13953-13956 -> 10501,
    // 13883 / 13957 -> 10504; Novite 667 PlayerCombat.getWeaponAttackEmote "javelin" 10501, "morrigan's throwing axe" 10504.
    MORRIGANS_JAVELIN(
        style(CombatStyle.FIRST, Animation(10501)),
        style(CombatStyle.SECOND, Animation(10501)),
        style(CombatStyle.THIRD, Animation(10501)),
        weaponType = WeaponType.THROWN,
        itemIds =
            listOf(
                Items.MORRIGANS_JAVELIN, Items.MORRIGANS_JAVELIN_P, Items.MORRIGANS_JAVELIN_P_13881, Items.MORRIGANS_JAVELIN_P_13882,
                Items.CORRUPT_MORRIGANS_JAVELIN, Items.C_MORRIGANS_JAVELIN_P, Items.C_MORRIGANS_JAVELIN_P_13955, Items.C_MORRIGANS_JAVELIN_P_13956,
            ),
    ),
    MORRIGANS_THROWING_AXE(
        style(CombatStyle.FIRST, Animation(10504)),
        style(CombatStyle.SECOND, Animation(10504)),
        style(CombatStyle.THIRD, Animation(10504)),
        weaponType = WeaponType.THROWN,
        itemIds = listOf(Items.MORRIGANS_THROWING_AXE, Items.C_MORRIGANS_THROWING_AXE),
    ),

    DEFENDERS(
        blockAnimation = Animations.DEFENDER.block,
        itemIds =
            listOf(
                Items.BRONZE_DEFENDER,
                Items.IRON_DEFENDER,
                Items.STEEL_DEFENDER,
                Items.BLACK_DEFENDER,
                Items.MITHRIL_DEFENDER,
                Items.ADAMANT_DEFENDER,
                Items.RUNE_DEFENDER,
                Items.RUNE_DEFENDER_T,
                Items.DRAGON_DEFENDER,
                Items.DRAGON_DEFENDER_T,
                Items.AVERNIC_DEFENDER,
                // K01 2026-10-25: the Trouver-locked defenders block like the defender itself.
                Items.MITHRIL_DEFENDER_L, Items.ADAMANT_DEFENDER_L, Items.RUNE_DEFENDER_L, Items.RUNE_DEFENDER_LT,
                Items.DRAGON_DEFENDER_L, Items.DRAGON_DEFENDER_LT, Items.AVERNIC_DEFENDER_L,
            ),
    ),

    // Owner 2026-09-29 (D5) "vesta longsword verdwijnt ook als je ermee attackt": Vesta's longsword (and its deg / corrupt ids) plays
    // its own sequences - Void 667 weapon_animations.toml [vestas_longsword]: default 12311, lunge 12310, defend 388
    // (melee.anims.toml); Novite 667 PlayerCombat.getWeaponAttackEmote "longsword": style 2 (Lunge) 12310, else 12311. The
    // cache style set is 6 (Chop / Slash / Lunge / Block, item param 686), so the third box is the lunge.
    VESTAS_LONGSWORD(
        style(CombatStyle.FIRST, Animation(12311)),
        style(CombatStyle.SECOND, Animation(12311)),
        style(CombatStyle.THIRD, Animation(12310)),
        style(CombatStyle.FOURTH, Animation(12311)),
        blockAnimation = Animation(388),
        weaponType = WeaponType.LONG_SWORD,
        equipType = EquipmentType.WEAPON,
        itemIds = listOf(Items.VESTAS_LONGSWORD, Items.VESTAS_LONGSWORD_DEG, Items.CORRUPT_VESTAS_LONGSWORD, Items.C_VESTAS_LONGSWORD_DEG),
    ),
    // (Declared last on purpose: getItemAnimation's weapon-type fallback takes the FIRST entry of a type, which must stay LONGSWORD.)
    ;

    companion object {
        fun getCorrectStyle(
            combatAnimation: CombatAnimation,
            combatStyle: CombatStyle = CombatStyle.FIRST,
        ): style? {
            return combatAnimation.combatstyle.find { it.combatStyle == combatStyle }
        }

        fun getItemAnimation(
            itemId: Int = -1,
            weaponType: Int = -1,
            equipType: Int = -1,
        ): CombatAnimation? {
            // id are prioritised over weapontype
            var matchingCombatAnimation = CombatAnimation.values().find { itemId in it.itemIds }
            // if fail to find by idMatching, check by weaponType
            if (matchingCombatAnimation == null) {
                matchingCombatAnimation = CombatAnimation.values().find { it.weaponType.id == weaponType }
            }
            // if fail to find by weaponTypeMatching, check by equiptype
            if (matchingCombatAnimation == null) {
                matchingCombatAnimation = CombatAnimation.values().find { it.equipType.id == equipType }
            }
            return matchingCombatAnimation
        }
    }
}
