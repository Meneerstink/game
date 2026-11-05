package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory

/** Exact OSRS RDT/GDT for the ten imported families only; legacy global tables are rounded/different.
 * Source: N522-NPCS-ONDERZOEK sections 6/8/9, appendix B (pinned Wiki Lua fractions).
 * AFGELEID: L=(3R+5G)/128, M=(2R+3G)/128 => R=128(5M-3L), G=128(2L-3M).
 * Every item/quantity/state also checked against the third independent table E=(R+G)/129.
 * Separate entrances retain DropTableFactory's existing Annihilation boost routing.
 */
object WildernessNpcRareDrops {
    fun rare(wealth: Boolean, legends: Boolean): DropTableBuilder.() -> Unit = DropTableFactory.build {
        main {
            if (wealth == false && legends == false) {
                total(16384)
                nothing(2955)
                obj(Items.COINS_995, quantity = 3000, slots = 2688)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 640)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 2580)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 2580)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 320)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 160)
                obj(Items.RUNE_BAR, quantity = 1, slots = 640)
                obj(Items.NATURE_RUNE, quantity = 67, slots = 384)
                obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 384)
                obj(Items.RUNE_BATTLEAXE, quantity = 1, slots = 384)
                obj(Items.LAW_RUNE, quantity = 45, slots = 256)
                obj(Items.DEATH_RUNE, quantity = 45, slots = 256)
                obj(Items.STEEL_ARROW, quantity = 150, slots = 256)
                obj(Items.RUNE_ARROW, quantity = 42, slots = 256)
                obj(Items.ADAMANT_JAVELIN, quantity = 20, slots = 256)
                obj(Items.RUNE_SQ_SHIELD, quantity = 1, slots = 256)
                obj(Items.DRAGONSTONE, quantity = 1, slots = 256)
                obj(Items.SILVER_ORE_NOTED, quantity = 100, slots = 256)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 80)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 40)
                obj(Items.RUNE_KITESHIELD, quantity = 1, slots = 128)
                obj(Items.DRAGON_MED_HELM, quantity = 1, slots = 128)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 120)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 20)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 60)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 45)
            }
            if (wealth == true && legends == false) {
                total(1664)
                obj(Items.COINS_995, quantity = 3000, slots = 273)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 128)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 264)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 264)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 64)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 32)
                obj(Items.RUNE_BAR, quantity = 1, slots = 65)
                obj(Items.NATURE_RUNE, quantity = 67, slots = 39)
                obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 39)
                obj(Items.RUNE_BATTLEAXE, quantity = 1, slots = 39)
                obj(Items.LAW_RUNE, quantity = 45, slots = 26)
                obj(Items.DEATH_RUNE, quantity = 45, slots = 26)
                obj(Items.STEEL_ARROW, quantity = 150, slots = 26)
                obj(Items.RUNE_ARROW, quantity = 42, slots = 26)
                obj(Items.ADAMANT_JAVELIN, quantity = 20, slots = 26)
                obj(Items.RUNE_SQ_SHIELD, quantity = 1, slots = 26)
                obj(Items.DRAGONSTONE, quantity = 1, slots = 26)
                obj(Items.SILVER_ORE_NOTED, quantity = 100, slots = 26)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 16)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 8)
                obj(Items.RUNE_KITESHIELD, quantity = 1, slots = 13)
                obj(Items.DRAGON_MED_HELM, quantity = 1, slots = 13)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 104)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 4)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 52)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 39)
            }
            if (wealth == false && legends == true) {
                total(524288)
                nothing(95125)
                obj(Items.COINS_995, quantity = 3000, slots = 86016)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 20480)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 82560)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 82560)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 10240)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 5120)
                obj(Items.RUNE_BAR, quantity = 1, slots = 20480)
                obj(Items.NATURE_RUNE, quantity = 67, slots = 12288)
                obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 12288)
                obj(Items.RUNE_BATTLEAXE, quantity = 1, slots = 12288)
                obj(Items.LAW_RUNE, quantity = 45, slots = 8192)
                obj(Items.DEATH_RUNE, quantity = 45, slots = 8192)
                obj(Items.STEEL_ARROW, quantity = 150, slots = 8192)
                obj(Items.RUNE_ARROW, quantity = 42, slots = 8192)
                obj(Items.ADAMANT_JAVELIN, quantity = 20, slots = 8192)
                obj(Items.RUNE_SQ_SHIELD, quantity = 1, slots = 8192)
                obj(Items.DRAGONSTONE, quantity = 1, slots = 8192)
                obj(Items.SILVER_ORE_NOTED, quantity = 100, slots = 8192)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 1920)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 1280)
                obj(Items.RUNE_KITESHIELD, quantity = 1, slots = 4096)
                obj(Items.DRAGON_MED_HELM, quantity = 1, slots = 4096)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 3880)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 640)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 1940)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 1455)
            }
            if (wealth == true && legends == true) {
                total(24960)
                obj(Items.COINS_995, quantity = 3000, slots = 4095)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 1920)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 3960)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 3960)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 960)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 480)
                obj(Items.RUNE_BAR, quantity = 1, slots = 975)
                obj(Items.NATURE_RUNE, quantity = 67, slots = 585)
                obj(Items.RUNE_2H_SWORD, quantity = 1, slots = 585)
                obj(Items.RUNE_BATTLEAXE, quantity = 1, slots = 585)
                obj(Items.LAW_RUNE, quantity = 45, slots = 390)
                obj(Items.DEATH_RUNE, quantity = 45, slots = 390)
                obj(Items.STEEL_ARROW, quantity = 150, slots = 390)
                obj(Items.RUNE_ARROW, quantity = 42, slots = 390)
                obj(Items.ADAMANT_JAVELIN, quantity = 20, slots = 390)
                obj(Items.RUNE_SQ_SHIELD, quantity = 1, slots = 390)
                obj(Items.DRAGONSTONE, quantity = 1, slots = 390)
                obj(Items.SILVER_ORE_NOTED, quantity = 100, slots = 390)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 180)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 120)
                obj(Items.RUNE_KITESHIELD, quantity = 1, slots = 195)
                obj(Items.DRAGON_MED_HELM, quantity = 1, slots = 195)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 1592)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 60)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 796)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 597)
            }
        }
    }
    fun gems(wealth: Boolean, legends: Boolean): DropTableBuilder.() -> Unit = DropTableFactory.build {
        main {
            if (wealth == false && legends == false) {
                total(128)
                nothing(63)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 32)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 1)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 1)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 16)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 8)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 4)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 2)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 1)
            }
            if (wealth == true && legends == false) {
                total(65)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 32)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 1)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 1)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 16)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 8)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 4)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 2)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 1)
            }
            if (wealth == false && legends == true) {
                total(16384)
                nothing(8177)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 4096)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 128)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 128)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 2048)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 1024)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 384)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 256)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 8)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 128)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 4)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 3)
            }
            if (wealth == true && legends == true) {
                total(975)
                obj(Items.UNCUT_SAPPHIRE, quantity = 1, slots = 480)
                obj(Items.LOOP_HALF_OF_A_KEY, quantity = 1, slots = 15)
                obj(Items.TOOTH_HALF_OF_A_KEY, quantity = 1, slots = 15)
                obj(Items.UNCUT_EMERALD, quantity = 1, slots = 240)
                obj(Items.UNCUT_RUBY, quantity = 1, slots = 120)
                obj(Items.NATURE_TALISMAN, quantity = 1, slots = 45)
                obj(Items.UNCUT_DIAMOND, quantity = 1, slots = 30)
                obj(Items.RUNE_SPEAR, quantity = 1, slots = 8)
                obj(Items.RUNE_JAVELIN, quantity = 5, slots = 15)
                obj(Items.SHIELD_LEFT_HALF, quantity = 1, slots = 4)
                obj(Items.DRAGON_SPEAR, quantity = 1, slots = 3)
            }
        }
    }
}
