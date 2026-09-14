package gg.rsmod.plugins.content.items

import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner answer Q9: every row of the OSRS Wiki "Reward casket (elite)" tables (raw wikitext 2026-09-14, per-roll rarity as the product of
 * its slot fractions) against [EliteCasketTable], whole roster (180 rows: uniques, tuxedo, mega-rare, gilded, 3rd age, standard, shared).
 */
class OsrsEliteCasketTests {
    private val wiki =
        listOf(
            Triple(23654, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon full helm ornament kit
            Triple(23650, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon chainbody ornament kit
            Triple(23652, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon legs/skirt ornament kit
            Triple(23656, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon sq shield ornament kit
            Triple(23664, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon scimitar ornament kit
            Triple(19333, 1..1, 1.0 / 25 * 1.0 / 51), // Fury ornament kit
            Triple(22858, 1..1, 1.0 / 25 * 1.0 / 51), // Light infinity colour kit
            Triple(22860, 1..1, 1.0 / 25 * 1.0 / 51), // Dark infinity colour kit
            Triple(15509, 1..1, 1.0 / 25 * 1.0 / 51), // Royal crown
            Triple(23578, 1..1, 1.0 / 25 * 1.0 / 51), // Royal gown top
            Triple(23576, 1..1, 1.0 / 25 * 1.0 / 51), // Royal gown bottom
            Triple(15507, 1..1, 1.0 / 25 * 1.0 / 51), // Royal sceptre
            Triple(23550, 1..1, 1.0 / 25 * 1.0 / 51), // Musketeer hat
            Triple(23554, 1..1, 1.0 / 25 * 1.0 / 51), // Musketeer tabard
            Triple(23552, 1..1, 1.0 / 25 * 1.0 / 51), // Musketeer pants
            Triple(23450, 1..1, 1.0 / 25 * 1.0 / 51), // Black d'hide body (g)
            Triple(23452, 1..1, 1.0 / 25 * 1.0 / 51), // Black d'hide body (t)
            Triple(23454, 1..1, 1.0 / 25 * 1.0 / 51), // Black d'hide chaps (g)
            Triple(23456, 1..1, 1.0 / 25 * 1.0 / 51), // Black d'hide chaps (t)
            Triple(23568, 1..1, 1.0 / 25 * 1.0 / 51), // Rangers' tunic
            Triple(23564, 1..1, 1.0 / 25 * 1.0 / 51), // Ranger gloves
            Triple(23508, 1..1, 1.0 / 25 * 1.0 / 51), // Holy wraps
            Triple(19296, 1..1, 1.0 / 25 * 1.0 / 51), // Bronze dragon mask
            Triple(19299, 1..1, 1.0 / 25 * 1.0 / 51), // Iron dragon mask
            Triple(19302, 1..1, 1.0 / 25 * 1.0 / 51), // Steel dragon mask
            Triple(19305, 1..1, 1.0 / 25 * 1.0 / 51), // Mithril dragon mask
            Triple(23428, 1..1, 1.0 / 25 * 1.0 / 51), // Adamant dragon mask
            Triple(23580, 1..1, 1.0 / 25 * 1.0 / 51), // Rune dragon mask
            Triple(23444, 1..1, 1.0 / 25 * 1.0 / 51), // Arceuus scarf
            Triple(23514, 1..1, 1.0 / 25 * 1.0 / 51), // Hosidius scarf
            Triple(23536, 1..1, 1.0 / 25 * 1.0 / 51), // Lovakengj scarf
            Triple(23562, 1..1, 1.0 / 25 * 1.0 / 51), // Piscarilius scarf
            Triple(23596, 1..1, 1.0 / 25 * 1.0 / 51), // Shayzien scarf
            Triple(23636, 1..1, 1.0 / 25 * 1.0 / 51), // Katana
            Triple(23638, 1..1, 1.0 / 25 * 1.0 / 51), // Dragon cane
            Triple(23464, 1..1, 1.0 / 25 * 1.0 / 51), // Bucket helm
            Triple(23458, 1..1, 1.0 / 25 * 1.0 / 51), // Blacksmith's helm
            Triple(23478, 1..1, 1.0 / 25 * 1.0 / 51), // Deerstalker
            Triple(23430, 1..1, 1.0 / 25 * 1.0 / 51), // Afro
            Triple(23446, 1..1, 1.0 / 25 * 1.0 / 51), // Big pirate hat
            Triple(13101, 1..1, 1.0 / 25 * 1.0 / 51), // Top hat
            Triple(23538, 1..1, 1.0 / 25 * 1.0 / 51), // Monocle
            Triple(23640, 1..1, 1.0 / 25 * 1.0 / 51), // Briefcase
            Triple(23582, 1..1, 1.0 / 25 * 1.0 / 51), // Sagacious spectacles
            Triple(23468, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Dark bow tie
            Triple(23474, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Dark tuxedo jacket
            Triple(23472, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Dark tuxedo cuffs
            Triple(23470, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Dark trousers
            Triple(23476, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Dark tuxedo shoes
            Triple(23524, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Light bow tie
            Triple(23530, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Light tuxedo jacket
            Triple(23528, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Light tuxedo cuffs
            Triple(23526, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Light trousers
            Triple(23532, 1..1, 1.0 / 25 * 1.0 / 51 * 1.0 / 10), // Light tuxedo shoes
            Triple(23566, 1..1, 1.0 / 25 * 1.0 / 51), // Rangers' tights
            Triple(23598, 1..1, 1.0 / 25 * 1.0 / 51), // Uri's hat
            Triple(23484, 1..1, 1.0 / 25 * 1.0 / 51), // Giant boot
            Triple(23482, 1..1, 1.0 / 25 * 1.0 / 51), // Fremennik kilt
            Triple(23694, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Ring of nature
            Triple(989, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Crystal key
            Triple(23518, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Lava dragon mask
            Triple(1392, 100..100, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Battlestaff
            Triple(23702, 30..30, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Extended antifire(4)
            Triple(3025, 30..30, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Super restore(4)
            Triple(6686, 30..30, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Saradomin brew(4)
            Triple(2445, 30..30, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Ranging potion(4)
            Triple(3486, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded full helm
            Triple(3481, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded platebody
            Triple(3483, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded platelegs
            Triple(3485, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded plateskirt
            Triple(3488, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded kiteshield
            Triple(23498, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded med helm
            Triple(23488, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded chainbody
            Triple(23500, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded sq shield
            Triple(23624, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded 2h sword
            Triple(23626, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded spear
            Triple(23628, 1..1, 1.0 / 25 * 2.0 / 51 * 5.0 / 23 * 1.0 / 11), // Gilded hasta
            Triple(23622, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded scimitar
            Triple(23486, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded boots
            Triple(23490, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded coif
            Triple(23496, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded d'hide vambraces
            Triple(23492, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded d'hide body
            Triple(23494, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded d'hide chaps
            Triple(23632, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded pickaxe
            Triple(23630, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded axe
            Triple(23634, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23), // Gilded spade
            Triple(10350, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age full helmet
            Triple(10348, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age platebody
            Triple(10346, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age platelegs
            Triple(23426, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age plateskirt
            Triple(10352, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age kiteshield
            Triple(10334, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age range coif
            Triple(10330, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age range top
            Triple(10332, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age range legs
            Triple(10336, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age vambraces
            Triple(10342, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age mage hat
            Triple(10338, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age robe top
            Triple(10340, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age robe
            Triple(10344, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age amulet
            Triple(23614, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age longsword
            Triple(23616, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age wand
            Triple(23422, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age cloak
            Triple(23612, 1..1, 1.0 / 25 * 2.0 / 51 * 1.0 / 23 * 1.0 / 17), // 3rd Age bow
            Triple(1127, 1..1, 24.0 / 25 * 1.0 / 31), // Rune platebody
            Triple(1079, 1..1, 24.0 / 25 * 1.0 / 31), // Rune platelegs
            Triple(1093, 1..1, 24.0 / 25 * 1.0 / 31), // Rune plateskirt
            Triple(1201, 1..1, 24.0 / 25 * 1.0 / 31), // Rune kiteshield
            Triple(9185, 1..1, 24.0 / 25 * 1.0 / 31), // Rune crossbow
            Triple(1215, 1..1, 24.0 / 25 * 1.0 / 31), // Dragon dagger
            Triple(1434, 1..1, 24.0 / 25 * 1.0 / 31), // Dragon mace
            Triple(1305, 1..1, 24.0 / 25 * 1.0 / 31), // Dragon longsword
            Triple(9194, 8..12, 24.0 / 25 * 1.0 / 31), // Onyx bolt tips
            Triple(563, 50..75, 24.0 / 25 * 1.0 / 31), // Law rune
            Triple(560, 50..75, 24.0 / 25 * 1.0 / 31), // Death rune
            Triple(565, 50..75, 24.0 / 25 * 1.0 / 31), // Blood rune
            Triple(566, 50..75, 24.0 / 25 * 1.0 / 31), // Soul rune
            Triple(11115, 1..1, 24.0 / 25 * 1.0 / 31), // Dragonstone bracelet
            Triple(1664, 1..1, 24.0 / 25 * 1.0 / 31), // Dragon necklace
            Triple(1645, 1..1, 24.0 / 25 * 1.0 / 31), // Dragonstone ring
            Triple(7061, 15..20, 24.0 / 25 * 1.0 / 31), // Tuna potato
            Triple(7219, 15..20, 24.0 / 25 * 1.0 / 31), // Summer pie
            Triple(8779, 60..80, 24.0 / 25 * 1.0 / 31), // Oak plank
            Triple(8781, 40..50, 24.0 / 25 * 1.0 / 31), // Teak plank
            Triple(8783, 20..30, 24.0 / 25 * 1.0 / 31), // Mahogany plank
            Triple(2364, 1..3, 24.0 / 25 * 1.0 / 31), // Runite bar
            Triple(985, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 2), // Tooth half of key
            Triple(987, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 2), // Loop half of key
            Triple(5289, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 3), // Palm tree seed
            Triple(5315, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 3), // Yew seed
            Triple(5316, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 3), // Magic seed
            Triple(995, 20000..30000, 24.0 / 25 * 1.0 / 31), // Coins
            Triple(995, 10000..15000, 24.0 / 25 * 1.0 / 31 * 7.0 / 20), // Coins
            Triple(10476, 9..23, 24.0 / 25 * 1.0 / 31), // Purple sweets
            Triple(10476, 8..12, 24.0 / 25 * 1.0 / 31 * 7.0 / 20), // Purple sweets
            Triple(7329, 9..15, 24.0 / 25 * 1.0 / 31 * 1.0 / 5), // Red firelighter
            Triple(7330, 9..15, 24.0 / 25 * 1.0 / 31 * 1.0 / 5), // Green firelighter
            Triple(7331, 9..15, 24.0 / 25 * 1.0 / 31 * 1.0 / 5), // Blue firelighter
            Triple(10326, 9..15, 24.0 / 25 * 1.0 / 31 * 1.0 / 5), // Purple firelighter
            Triple(10327, 9..15, 24.0 / 25 * 1.0 / 31 * 1.0 / 5), // White firelighter
            Triple(23600, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // Holy blessing
            Triple(23602, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // Unholy blessing
            Triple(23604, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // Peaceful blessing
            Triple(23608, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // War blessing
            Triple(23606, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // Honourable blessing
            Triple(23610, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 20), // Ancient blessing
            Triple(19475, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Nardah teleport
            Triple(23688, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Mos le'harmless teleport
            Triple(23684, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Mort'ton teleport
            Triple(23682, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Feldip hills teleport
            Triple(23683, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Lunar isle teleport
            Triple(23681, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Digsite teleport
            Triple(23686, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Piscatoris teleport
            Triple(23685, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Pest control teleport
            Triple(19479, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Tai bwo wannai teleport
            Triple(23689, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Lumberyard teleport
            Triple(23687, 5..15, 24.0 / 25 * 2.0 / 31 * 21.0 / 22 * 1.0 / 12), // Iorwerth camp teleport
            Triple(3827, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Saradomin page 1
            Triple(3828, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Saradomin page 2
            Triple(3829, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Saradomin page 3
            Triple(3830, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Saradomin page 4
            Triple(3831, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Zamorak page 1
            Triple(3832, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Zamorak page 2
            Triple(3833, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Zamorak page 3
            Triple(3834, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Zamorak page 4
            Triple(3835, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Guthix page 1
            Triple(3836, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Guthix page 2
            Triple(3837, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Guthix page 3
            Triple(3838, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Guthix page 4
            Triple(19600, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Bandos page 1
            Triple(19601, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Bandos page 2
            Triple(19602, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Bandos page 3
            Triple(19603, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Bandos page 4
            Triple(19604, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Armadyl page 1
            Triple(19605, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Armadyl page 2
            Triple(19606, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Armadyl page 3
            Triple(19607, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Armadyl page 4
            Triple(19608, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Ancient page 1
            Triple(19609, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Ancient page 2
            Triple(19610, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Ancient page 3
            Triple(19611, 1..1, 24.0 / 25 * 1.0 / 31 * 1.0 / 24), // Ancient page 4
        )

    @Test
    fun `every wiki row has exactly its per-roll rarity and nothing else can be rolled`() {
        val model = EliteCasketTable.itemProbabilities()
        val expected = HashMap<Pair<Int, IntRange>, Double>()
        wiki.forEach { (id, amount, chance) -> expected.merge(id to amount, chance, Double::plus) }
        assertEquals(180, wiki.size)
        val offenders = (expected.keys + model.keys).filter { abs((expected[it] ?: 0.0) - (model[it] ?: 0.0)) > 1e-12 }
            .map { "$it wiki=${expected[it]} model=${model[it]}" }
        assertEquals(emptyList(), offenders)
        assertEquals(4, EliteCasketTable.MIN_ROLLS, "a minimum of 4 rewards")
        assertEquals(6, EliteCasketTable.MAX_ROLLS, "a maximum of 6")
    }

    @Test
    fun `every rolled id exists in items yml and the casket plugin uses the table`() {
        val ids = Regex("""(?m)^- id: (\d+)$""").findAll(File("../../data/cfg/items.yml").readText()).map { it.groupValues[1].toInt() }.toSet()
        assertEquals(emptyList(), EliteCasketTable.itemProbabilities().keys.map { it.first }.filter { it !in ids })
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/items/casket.plugin.kts").readText()
        assertTrue("EliteCasketTable.ROLL" in plugin && "EliteCasketTable.MIN_ROLLS" in plugin && "EliteCasketTable.MAX_ROLLS" in plugin)
        assertTrue("ARMADYL_FULL_HELM" !in plugin.substringAfter("Items.CASKET_ELITE"), "the Novite 2011 elite table is gone")
    }
}