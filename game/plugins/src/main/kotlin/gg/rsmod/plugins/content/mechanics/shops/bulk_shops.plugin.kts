package gg.rsmod.plugins.content.mechanics.shops

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.model.shop.ShopItem

/**
 * Bulk shop import (2026-09-10) from the 2009scape `shops.json` (245 shops, same-era npc/item ids).
 *
 * Every coin shop is registered here and bound to its shopkeepers' "Trade" option at world init,
 * after all hand-written shop plugins have loaded, so an npc or shop title that already exists is
 * skipped instead of clashing. Shops whose keeper is missing from this cache or has no Trade
 * option are skipped as well (logged at boot). Stock amounts are the 2009scape base stock;
 * general stores buy any tradeable, specialist shops only buy their own stock.
 */

data class BulkShop(val title: String, val npcs: IntArray, val generalStore: Boolean, val stock: List<Pair<Int, Int>>)

val BULK_SHOPS = listOf(
    BulkShop("Ava's Odds and Ends", intArrayOf(5198, 5199), false, listOf(314 to 1000, 884 to 40, 886 to 10, 40 to 30, 41 to 20)),
    BulkShop("Edgeville General Store", intArrayOf(528, 529), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Al Kharid General Store", intArrayOf(525, 524), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Void Knight General Store", intArrayOf(3799), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10, 7934 to 25)),
    BulkShop("Ifaba's General Store", intArrayOf(1436), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Aemad's Adventuring Supplies", intArrayOf(590, 591), true, listOf(227 to 10, 1265 to 10, 1349 to 10, 2142 to 10, 590 to 10, 1759 to 100, 882 to 1000, 954 to 10, 970 to 10, 946 to 10, 1935 to 10)),
    BulkShop("West Ardougne General Store", intArrayOf(971), true, listOf(1931 to 10, 954 to 10, 1265 to 10, 1925 to 30, 590 to 10, 2347 to 10, 1061 to 10, 841 to 10, 882 to 100, 329 to 10, 2327 to 10, 2309 to 10, 2142 to 10)),
    BulkShop("Bandit Bargains", intArrayOf(1917), true, listOf(1823 to 5, 1831 to 5, 1937 to 5, 1921 to 5, 1929 to 5, 1935 to 5, 1923 to 5, 1925 to 5, 1837 to 5, 1833 to 5, 1835 to 5, 946 to 5)),
    BulkShop("Bandit Duty Free", intArrayOf(597), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Burthorpe Supplies", intArrayOf(1083), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Aurel's Supplies", intArrayOf(3541, 7396), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("General Store (Canifis)", intArrayOf(1040), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10, 3377 to 10)),
    BulkShop("Arhein's Store", intArrayOf(563), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Dorgesh-Kaan General Supplies", intArrayOf(5798), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Dwarven Shopping Store", intArrayOf(582), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Falador General Store", intArrayOf(526, 527), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Dal's General Ogre Supplies", intArrayOf(873), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Karamja General Store", intArrayOf(532, 533), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Karamja Wines, Spirits, and Beers", intArrayOf(568), true, listOf(1917 to 3, 431 to 3, 1993 to 1)),
    BulkShop("Gunslik's Assorted Items", intArrayOf(2154), true, listOf(1935 to 10, 1925 to 30, 590 to 10, 1755 to 10, 2347 to 10, 36 to 10, 596 to 10, 973 to 10, 1059 to 10, 229 to 300, 233 to 10, 954 to 10)),
    BulkShop("The Lighthouse Store", intArrayOf(1334), true, listOf(954 to 10, 2347 to 10, 1755 to 10, 946 to 10, 952 to 10, 590 to 5, 36 to 10, 273 to 10, 233 to 10, 1931 to 10, 1925 to 5, 1929 to 5, 1935 to 5, 1937 to 5, 229 to 10, 227 to 10, 2019 to 5, 2021 to 5, 2015 to 5, 1915 to 5, 2017 to 5, 1909 to 5, 1913 to 5, 1907 to 5)),
    BulkShop("Lletya General Store", intArrayOf(2352), true, listOf(1931 to 10, 954 to 10, 1265 to 10, 1925 to 30, 590 to 10, 2347 to 10, 1061 to 10, 841 to 10, 882 to 100, 329 to 10, 2327 to 10, 2309 to 10, 2142 to 10)),
    BulkShop("Moon Clan General Store", intArrayOf(4516), true, listOf(1931 to 30, 1935 to 10, 1735 to 10, 1925 to 30, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 9003 to 10, 229 to 1000)),
    BulkShop("Lumbridge General Store", intArrayOf(520, 521), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Trader Sven's Black Market Goods", intArrayOf(4716), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Miscellanian General Store", intArrayOf(3922), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Razmire General Store", intArrayOf(1254), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Nardah General Store", intArrayOf(3039), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Arnold's Eclectic Supplies", intArrayOf(3824), true, listOf(303 to 10, 311 to 10, 7944 to 0, 7946 to 0, 2309 to 10, 1931 to 30, 1927 to 10, 1733 to 10, 1734 to 1000, 1755 to 10, 1917 to 10, 1785 to 10, 946 to 10)),
    BulkShop("Pollnivneach General Store", intArrayOf(1866), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Port Phasmatys General Store", intArrayOf(1699), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Rimmington General Store", intArrayOf(530, 531), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Shantay Pass Shop", intArrayOf(836), true, listOf(1823 to 30, 1831 to 30, 1937 to 10, 1921 to 10, 1929 to 10, 946 to 10, 1833 to 10, 1835 to 10, 1837 to 10, 2349 to 0, 314 to 1000, 2347 to 10, 1925 to 30, 1923 to 10, 1935 to 10, 1854 to 300, 954 to 100)),
    BulkShop("Obli's General Store", intArrayOf(516), true, listOf(590 to 10, 229 to 1000, 233 to 10, 1931 to 30, 1351 to 10, 1265 to 10, 1349 to 10, 1129 to 10, 1059 to 10, 1061 to 10, 2142 to 10, 2309 to 10, 952 to 10, 36 to 10, 596 to 10, 1755 to 10, 2347 to 10, 970 to 10, 973 to 10, 227 to 1000, 975 to 10, 954 to 10)),
    BulkShop("Jiminua's Jungle Store", intArrayOf(560), true, listOf(590 to 10, 36 to 10, 596 to 10, 1931 to 30, 954 to 10, 1129 to 10, 1059 to 10, 1061 to 10, 2142 to 10, 2309 to 10, 227 to 300, 233 to 10, 175 to 10, 970 to 10, 973 to 10, 946 to 10, 2347 to 10, 975 to 10, 1755 to 10, 952 to 10, 1351 to 10, 1265 to 10, 1349 to 10, 229 to 300)),
    BulkShop("Bolkoy's Village Shop", intArrayOf(471), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Quartermaster's Stores", intArrayOf(1208), true, listOf(1931 to 30, 1935 to 10, 1735 to 10, 590 to 10, 2309 to 10, 3190 to 10, 3192 to 10, 3194 to 10, 3196 to 10, 3198 to 10, 3200 to 10, 3202 to 10, 3204 to 10)),
    BulkShop("Varrock General Store", intArrayOf(522, 523), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Trader Stan's Trading Post", intArrayOf(4651, 4652, 4653, 4654, 4655, 4656, 4650), true, listOf(1931 to 30, 1935 to 10, 1735 to 10, 1925 to 30, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10, 954 to 10, 946 to 10, 2114 to 0, 1963 to 10, 2108 to 0, 1785 to 10, 1783 to 0, 401 to 0, 1781 to 0, 301 to 10, 307 to 10, 1941 to 10, 9629 to 10, 3226 to 10, 1025 to 10)),
    BulkShop("Zanaris General Store", intArrayOf(534, 535), true, listOf(1931 to 30, 1935 to 30, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Davon's Amulet Store", intArrayOf(588), false, listOf(1718 to 0, 1727 to 0, 1729 to 0, 1725 to 0, 1731 to 0)),
    BulkShop("Lletya Archery Shop", intArrayOf(2356), false, listOf(884 to 1000, 886 to 300, 888 to 100, 890 to 100, 892 to 30, 877 to 1000, 843 to 10, 837 to 10, 849 to 10)),
    BulkShop("Void Knight Archery Store", intArrayOf(3796), false, listOf(825 to 300, 826 to 300, 827 to 100, 828 to 100, 829 to 250, 830 to 10, 39 to 100, 40 to 100, 41 to 100, 42 to 30, 43 to 30, 44 to 10)),
    BulkShop("Brian's Archery Supplies", intArrayOf(1860), false, listOf(886 to 100, 888 to 30, 890 to 0, 843 to 10, 845 to 10, 849 to 10, 847 to 10, 853 to 10, 851 to 10)),
    BulkShop("Lowe's Archery Emporium", intArrayOf(550), false, listOf(882 to 1000, 884 to 100, 886 to 100, 888 to 100, 890 to 30, 877 to 300, 841 to 10, 843 to 10, 849 to 10, 853 to 10, 837 to 10, 839 to 10, 845 to 10, 847 to 10, 851 to 10)),
    BulkShop("Hickton's Archery Emporium", intArrayOf(575), false, listOf(877 to 300, 882 to 1000, 884 to 100, 886 to 0, 888 to 0, 890 to 0, 892 to 0, 4773 to 0, 4778 to 0, 4783 to 0, 4788 to 0, 4793 to 0, 4798 to 0, 4803 to 0, 841 to 10, 837 to 10, 843 to 10, 4827 to 10, 1133 to 10, 1097 to 10)),
    BulkShop("Dargaud's Bows And Arrows", intArrayOf(683), false, listOf(52 to 1000, 39 to 1000, 40 to 300, 41 to 300, 42 to 100, 43 to 100, 44 to 100, 882 to 1000, 884 to 300, 886 to 300, 888 to 100, 890 to 100, 892 to 100, 4773 to 0, 4778 to 0, 4783 to 0, 4788 to 0, 4793 to 0, 4798 to 0, 4803 to 0, 841 to 10, 843 to 10, 849 to 10, 839 to 20, 845 to 20, 847 to 20, 4827 to 10)),
    BulkShop("Aaron's Archery Appendages", intArrayOf(682), false, listOf(1129 to 10, 1131 to 10, 1133 to 10, 1095 to 10, 1097 to 10, 1169 to 10, 1167 to 10, 1063 to 10)),
    BulkShop("Crossbow Shop (dwarven Mine)", intArrayOf(4563), false, listOf(9440 to 10, 9442 to 10, 9444 to 10, 9446 to 10, 9448 to 10, 9450 to 10, 9452 to 0, 9420 to 10, 9423 to 10, 9425 to 10, 9427 to 10, 9429 to 10, 9431 to 0)),
    BulkShop("Crossbow Shop (keldagrim)", intArrayOf(4558), false, listOf(9440 to 10, 9442 to 10, 9444 to 10, 9446 to 10, 9448 to 10, 9450 to 10, 9452 to 0, 9420 to 10, 9423 to 10, 9425 to 10, 9427 to 10, 9429 to 10, 9431 to 0)),
    BulkShop("Crossbow Shop (white Wolf Mountain)", intArrayOf(4559), false, listOf(9440 to 10, 9442 to 10, 9444 to 10, 9446 to 10, 9448 to 10, 9450 to 10, 9452 to 0, 9420 to 10, 9423 to 10, 9425 to 10, 9427 to 10, 9429 to 10, 9431 to 0)),
    BulkShop("Bob's Brilliant Axes", intArrayOf(519), false, listOf(1265 to 10, 1351 to 10, 1349 to 10, 1353 to 10, 1363 to 10, 1365 to 10, 1369 to 10)),
    BulkShop("Brian's Battleaxe Bazaar", intArrayOf(559), false, listOf(1375 to 4, 1363 to 3, 1365 to 2, 1367 to 1, 1369 to 1, 1371 to 1)),
    BulkShop("Candle Shop", intArrayOf(562), false, listOf(36 to 10, 38 to 10)),
    BulkShop("Candle Shop", intArrayOf(562), false, listOf(36 to 10)),
    BulkShop("Wayne's Chains", intArrayOf(581), false, listOf(1103 to 3, 1101 to 2, 1105 to 1, 1107 to 1, 1109 to 1, 1111 to 1)),
    BulkShop("Fancy Clothes Store", intArrayOf(554), false, listOf(1949 to 0, 579 to 10, 1023 to 10, 958 to 10, 948 to 10, 1733 to 10, 1734 to 1000, 1059 to 30, 1061 to 10, 428 to 10, 426 to 10, 1757 to 10, 1013 to 10, 1015 to 10, 1011 to 10, 1007 to 10, 1025 to 10, 10088 to 0, 10090 to 0, 10091 to 0, 10089 to 0, 10087 to 0, 11525 to 0)),
    BulkShop("Thessalia's Fine Clothes", intArrayOf(548), false, listOf(1005 to 10, 1129 to 10, 1059 to 10, 1061 to 10, 1757 to 10, 1013 to 10, 1015 to 10, 1011 to 10, 1007 to 10, 950 to 10, 426 to 10, 428 to 10)),
    BulkShop("Fine Fashions", intArrayOf(601), false, listOf(646 to 5, 648 to 5, 650 to 5, 652 to 5, 654 to 5, 656 to 5, 658 to 5, 660 to 5, 662 to 5, 664 to 5, 636 to 5, 638 to 5, 640 to 5, 642 to 5, 644 to 5, 626 to 5, 628 to 5, 630 to 30, 632 to 30, 634 to 30)),
    BulkShop("Yrsa's Accoutrements", intArrayOf(1301), false, listOf(3775 to 10, 3773 to 10, 3767 to 10, 3769 to 10, 3771 to 10, 3793 to 10, 3795 to 10, 3797 to 10, 3791 to 10, 3799 to 10, 3759 to 10, 3761 to 10, 3765 to 10, 3763 to 10, 3777 to 10, 3779 to 10, 3781 to 10, 3783 to 10, 3785 to 10, 3787 to 10, 3789 to 10)),
    BulkShop("Barker's Haberdashery", intArrayOf(1039), false, listOf(2894 to 5, 2896 to 5, 2898 to 5, 2900 to 5, 2902 to 5, 2904 to 5, 2906 to 5, 2908 to 5, 2910 to 5, 2912 to 5, 2914 to 5, 2916 to 5, 2918 to 5, 2920 to 5, 2922 to 5, 2924 to 5, 2926 to 5, 2928 to 5, 2930 to 5, 2932 to 5, 2934 to 5, 2936 to 5, 2938 to 5, 2940 to 5, 2942 to 5, 1007 to 5, 1023 to 5, 1019 to 5, 1021 to 5, 1027 to 5)),
    BulkShop("Lletya Seamstress", intArrayOf(2353), false, listOf(1734 to 1000, 1733 to 10, 1759 to 100, 1763 to 10, 1765 to 10, 1767 to 10, 1769 to 10, 1771 to 10, 1773 to 10)),
    BulkShop("Dodgy Mikes Second-hand Clothing", intArrayOf(3166), false, listOf(7114 to 10, 7122 to 10, 7128 to 10, 7134 to 10, 7110 to 10, 7126 to 10, 7132 to 10, 7138 to 10, 7116 to 10, 7124 to 10, 7130 to 10, 7112 to 10, 7136 to 10)),
    BulkShop("Vermundi's Clothes Stall", intArrayOf(2162), false, listOf(950 to 10)),
    BulkShop("Grand Tree Groceries", intArrayOf(600), false, listOf(2171 to 10, 2128 to 10, 1933 to 10, 2169 to 10, 1957 to 10, 1942 to 10, 1965 to 10, 1982 to 10, 1985 to 10, 2120 to 10, 2108 to 10, 2102 to 10, 2114 to 10, 2126 to 10, 2025 to 10, 1973 to 10, 1975 to 10, 2130 to 10, 1927 to 10, 946 to 10, 2167 to 10, 2164 to 10, 2165 to 10, 2166 to 10)),
    BulkShop("Frenita's Cookery Shop.", intArrayOf(593), false, listOf(2313 to 5, 1955 to 2, 1887 to 2, 1923 to 2, 1942 to 5, 590 to 4, 1935 to 1, 1931 to 8, 1973 to 2, 1933 to 8, 1980 to 20)),
    BulkShop("Dommik's Crafting Store", intArrayOf(545), false, listOf(1755 to 10, 1592 to 10, 1597 to 10, 1595 to 10, 1733 to 10, 1734 to 1000, 1599 to 10, 2976 to 10, 5523 to 10, 9434 to 10, 11065 to 10)),
    BulkShop("Rommik's Crafty Supplies", intArrayOf(585), false, listOf(1755 to 10, 1592 to 10, 1597 to 10, 1595 to 10, 1733 to 10, 1734 to 1000, 1599 to 10, 2976 to 10, 5523 to 10, 9434 to 10, 11065 to 10)),
    BulkShop("Jamila's Craft Stall", intArrayOf(5268), false, listOf(1755 to 10, 1592 to 10, 1597 to 10, 1595 to 10, 1733 to 10, 1734 to 1000, 1599 to 10, 2976 to 10, 5523 to 10, 11065 to 10)),
    BulkShop("Vanessa's Farming Shop", intArrayOf(2305), false, listOf(5376 to 10, 6032 to 300, 5418 to 10, 6036 to 10, 5350 to 100, 5341 to 10, 5329 to 10, 5343 to 10, 952 to 10, 5325 to 10, 1925 to 100, 5331 to 30, 12622 to 10, 5996 to 0, 6006 to 0, 1965 to 0, 5994 to 0, 5931 to 0, 6000 to 0, 1957 to 0, 5504 to 0, 5986 to 0, 1982 to 0, 5982 to 0, 6002 to 0, 5998 to 0)),
    BulkShop("Alice's Farming Shop", intArrayOf(2307), false, listOf(5376 to 10, 6032 to 300, 5418 to 10, 6036 to 10, 5350 to 100, 5341 to 10, 5329 to 10, 5343 to 10, 952 to 10, 5325 to 10, 1925 to 100, 5331 to 30, 12622 to 10, 5996 to 0, 6006 to 0, 1965 to 0, 5994 to 0, 5931 to 0, 6000 to 0, 1957 to 0, 5504 to 0, 5986 to 0, 1982 to 0, 5982 to 0, 6002 to 0, 5998 to 0)),
    BulkShop("Sarah's Farming Shop", intArrayOf(2304), false, listOf(5376 to 10, 6032 to 300, 5418 to 10, 6036 to 10, 5350 to 100, 5341 to 10, 5329 to 10, 5343 to 10, 952 to 10, 5325 to 10, 1925 to 100, 5331 to 30, 12622 to 10, 5996 to 0, 6006 to 0, 1965 to 0, 5994 to 0, 5931 to 0, 6000 to 0, 1957 to 0, 5504 to 0, 5986 to 0, 1982 to 0, 5982 to 0, 6002 to 0, 5998 to 0)),
    BulkShop("Richard's Farming Shop", intArrayOf(2306), false, listOf(5376 to 10, 6032 to 300, 5418 to 10, 6036 to 10, 5350 to 100, 5341 to 10, 5329 to 10, 5343 to 10, 952 to 10, 5325 to 10, 1925 to 100, 5331 to 30, 12622 to 10, 5996 to 0, 6006 to 0, 1965 to 0, 5994 to 0, 5931 to 0, 6000 to 0, 1957 to 0, 5504 to 0, 5986 to 0, 1982 to 0, 5982 to 0, 6002 to 0, 5998 to 0)),
    BulkShop("Wydin's Food Store", intArrayOf(557), false, listOf(1933 to 500, 2132 to 10, 2138 to 10, 1965 to 10, 1963 to 0, 1951 to 0, 2309 to 10, 1973 to 10, 1985 to 10, 1982 to 10, 1550 to 10)),
    BulkShop("Rufus's Meat Emporium", intArrayOf(1038), false, listOf(2132 to 100, 2138 to 30, 2134 to 10, 2136 to 10, 335 to 0, 349 to 0, 331 to 0, 383 to 0)),
    BulkShop("Solihib's Consumables Stall", intArrayOf(1433), false, listOf(4012 to 10, 1963 to 10, 4016 to 10, 4014 to 10)),
    BulkShop("Fresh Meat", intArrayOf(7054), false, listOf(2134 to 10, 2132 to 300, 2136 to 10, 2142 to 10, 2138 to 10, 2140 to 10, 3226 to 100, 7224 to 0, 7223 to 0, 3228 to 10, 2876 to 0, 7230 to 0, 2878 to 0, 7566 to 0, 2337 to 0, 9978 to 100, 9984 to 0, 9980 to 0, 9986 to 0, 9992 to 0, 9988 to 0)),
    BulkShop("Ardougne Baker's Stall", intArrayOf(571), false, listOf(2309 to 10, 1891 to 10, 1973 to 100)),
    BulkShop("Keepa Kettilon's store", intArrayOf(5487), false, listOf(361 to 20, 329 to 20, 339 to 20, 379 to 10, 373 to 0, 385 to 0)),
    BulkShop("Gianne's Restaurant", intArrayOf(851), false, listOf(2223 to 10, 2225 to 10, 2221 to 10, 2219 to 10, 2227 to 10, 2233 to 10, 2231 to 10, 2235 to 10, 2229 to 10, 2241 to 10, 2243 to 10, 2239 to 10, 2237 to 10)),
    BulkShop("Warriors' Guild Potion Shop", intArrayOf(4294), false, listOf(115 to 10, 121 to 10, 133 to 10)),
    BulkShop("Warriors' Guild Food Shop", intArrayOf(4293), false, listOf(333 to 10, 365 to 10, 2289 to 10, 6705 to 10, 2003 to 10)),
    BulkShop("Warriors' Guild Armoury", intArrayOf(4295), false, listOf(1363 to 10, 1365 to 10, 1369 to 10, 1277 to 10, 1279 to 10, 1281 to 10, 1283 to 10, 1285 to 10, 1287 to 10, 1307 to 10, 1309 to 10, 1311 to 10, 1313 to 10, 1315 to 10, 1317 to 10, 1211 to 10, 1301 to 10, 1297 to 10)),
    BulkShop("Herquin's Gems", intArrayOf(584), false, listOf(1623 to 0, 1621 to 0, 1619 to 0, 1617 to 0, 1607 to 0, 1605 to 0, 1603 to 0, 1601 to 0)),
    BulkShop("Ardougne Gem Stall", intArrayOf(570), false, listOf(1623 to 0, 1621 to 0, 1619 to 0, 1617 to 0, 1607 to 0, 1605 to 0, 1603 to 0, 1601 to 0)),
    BulkShop("Green Gemstone Gem", intArrayOf(2157), false, listOf(1607 to 0, 1605 to 0, 1603 to 0, 1601 to 0)),
    BulkShop("Gem Trader (stall)", intArrayOf(540), false, listOf(1623 to 0, 1621 to 0, 1619 to 0, 1617 to 0, 1607 to 0, 1605 to 0, 1603 to 0, 1601 to 0)),
    BulkShop("Gem Trader (stall)", intArrayOf(540), false, listOf(1623 to 0, 1621 to 0, 1619 to 0, 1617 to 0, 1607 to 0, 1605 to 0, 1603 to 0, 1601 to 0)),
    BulkShop("Gem Store", intArrayOf(2157), false, listOf(1623 to 1, 1621 to 1, 1619 to 0, 1617 to 0)),
    BulkShop("Ardougne Silver Stall", intArrayOf(569), false, listOf(1714 to 10, 442 to 0, 2355 to 0)),
    BulkShop("Ardougne Spice Stall", intArrayOf(572), false, listOf(2007 to 10, 946 to 10, 1550 to 10)),
    BulkShop("Shop of Distate", intArrayOf(958), false, listOf(2518 to 1)),
    BulkShop("Ardougne Fur Stall", intArrayOf(573), false, listOf(948 to 10, 958 to 10, 10117 to 0, 10121 to 0, 10119 to 0, 10123 to 0, 10093 to 0, 10095 to 0, 10097 to 0, 10099 to 0, 10101 to 0, 10103 to 0)),
    BulkShop("Peksa's Helmet Shop", intArrayOf(538), false, listOf(1139 to 5, 1137 to 3, 1141 to 3, 1143 to 1, 1145 to 1, 1155 to 4, 1153 to 3, 1157 to 2, 1159 to 1, 1161 to 1)),
    BulkShop("Skulgrimen's Battle Gear", intArrayOf(1303), false, listOf(1337 to 10, 1335 to 10, 1339 to 10, 1341 to 10, 1343 to 10, 1347 to 0, 3749 to 10, 3751 to 10, 3753 to 10, 3755 to 10)),
    BulkShop("Tea Shop", intArrayOf(595), false, listOf(712 to 10)),
    BulkShop("Grud's Herblore Stall.", intArrayOf(704), false, listOf(229 to 10, 233 to 10, 221 to 10)),
    BulkShop("Jatix's Herblore Shop", intArrayOf(587), false, listOf(229 to 300, 233 to 10, 221 to 1000)),
    BulkShop("Aleck's Hunter Emporium", intArrayOf(5110), false, listOf(10010 to 10, 10012 to 300, 10025 to 300, 10150 to 10, 10006 to 30, 10008 to 30, 10029 to 10, 596 to 10, 10031 to 30, 12539 to 0)),
    BulkShop("Nardah Hunter Shop", intArrayOf(5109), false, listOf(10010 to 10, 10012 to 300, 10025 to 300, 10150 to 10, 10006 to 30, 10008 to 30, 10029 to 10, 596 to 10, 10031 to 30, 12539 to 0)),
    BulkShop("Grum's Gold Exchange", intArrayOf(556), false, listOf(1635 to 0, 1637 to 0, 1639 to 0, 1641 to 0, 1643 to 0, 1654 to 0, 1656 to 0, 1658 to 0, 1660 to 0, 1662 to 0, 1692 to 0, 1694 to 0, 1696 to 0, 1698 to 0, 1700 to 0, 11069 to 0, 11072 to 0, 11076 to 0, 11085 to 0, 11092 to 0)),
    BulkShop("Ali's Discount Wares", intArrayOf(1862), false, listOf(1931 to 30, 1935 to 10, 1825 to 10, 1833 to 10, 1837 to 10, 1925 to 30, 4593 to 10, 4591 to 10, 970 to 1, 946 to 10, 590 to 10, 1265 to 10, 2138 to 10)),
    BulkShop("Ali the Kebab Seller", intArrayOf(1865), false, listOf(1971 to 10)),
    BulkShop("Karim", intArrayOf(543), false, listOf(1971 to 10)),
    BulkShop("Kjut's KEbabs", intArrayOf(2198), false, listOf(1971 to 10)),
    BulkShop("Flynn's Mace Market", intArrayOf(580), false, listOf(1422 to 5, 1420 to 4, 1424 to 4, 1428 to 3, 1430 to 2)),
    BulkShop("Ali's Discount Wares#runes", intArrayOf(1862), false, listOf(1931 to 30, 1935 to 10, 1833 to 10, 1837 to 10, 1925 to 10, 4593 to 10, 4591 to 10, 970 to 10, 946 to 10, 590 to 10, 1265 to 10, 2138 to 10, 6416 to 10, 4600 to 10, 556 to 300, 555 to 300, 557 to 300, 554 to 300, 561 to 300, 563 to 300, 562 to 300, 560 to 300, 558 to 300, 559 to 300, 565 to 100, 564 to 300, 566 to 100, 6382 to 30, 6388 to 30, 6390 to 30, 1835 to 30)),
    BulkShop("Betty's Magic Emporium", intArrayOf(583), false, listOf(554 to 300, 555 to 300, 556 to 300, 557 to 300, 558 to 100, 559 to 100, 562 to 30, 560 to 10, 221 to 100, 579 to 10, 1017 to 10)),
    BulkShop("Aubury's Rune Shop", intArrayOf(553), false, listOf(554 to 5000, 555 to 5000, 556 to 5000, 557 to 5000, 558 to 5000, 559 to 5000, 562 to 500, 560 to 500)),
    BulkShop("Magic Guild Store", intArrayOf(461), false, listOf(556 to 5000, 555 to 5000, 557 to 5000, 554 to 5000, 558 to 5000, 559 to 5000, 562 to 250, 561 to 250, 560 to 250, 563 to 250, 565 to 250, 566 to 250, 1391 to 5, 1387 to 2, 1383 to 2, 1381 to 2, 1385 to 2)),
    BulkShop("Baba Yaga's Magic Shop", intArrayOf(4513), false, listOf(556 to 1000, 555 to 1000, 557 to 1000, 554 to 1000, 558 to 1000, 559 to 1000, 562 to 300, 561 to 300, 560 to 300, 563 to 100, 565 to 100, 566 to 100, 9075 to 100, 1391 to 20, 1387 to 10, 1383 to 10, 1381 to 10, 1385 to 10, 9078 to 10)),
    BulkShop("Lundail's Arena-side Rune Shop", intArrayOf(903), false, listOf(554 to 1000, 555 to 1000, 556 to 1000, 557 to 1000, 558 to 1000, 559 to 1000, 561 to 300, 562 to 300, 563 to 100, 564 to 300, 560 to 300)),
    BulkShop("Tutab's Magical Market", intArrayOf(1435), false, listOf(554 to 1000, 555 to 1000, 556 to 1000, 557 to 1000, 563 to 100, 4009 to 10, 4006 to 10, 4023 to 10)),
    BulkShop("Void Knight Magic Store", intArrayOf(3798), false, listOf(554 to 1000, 555 to 1000, 556 to 1000, 557 to 1000, 558 to 1000, 559 to 1000, 562 to 300, 560 to 300)),
    BulkShop("Magic Guild Store", intArrayOf(1658), false, listOf(4089 to 1000, 4091 to 1000, 4093 to 1000, 4095 to 1000, 4097 to 1000)),
    BulkShop("Nurmof's Pickaxe Shop", intArrayOf(594), false, listOf(1265 to 10, 1267 to 10, 1269 to 10, 1273 to 10, 1271 to 10, 1275 to 10)),
    BulkShop("Drogo's Mining Emporium", intArrayOf(579), false, listOf(2347 to 10, 1265 to 10, 436 to 0, 438 to 0, 440 to 0, 453 to 0, 2349 to 0, 2351 to 0, 2357 to 0)),
    BulkShop("Pickaxe-Is-Mine", intArrayOf(2160), false, listOf(1265 to 10, 1269 to 10, 1273 to 10, 1271 to 10, 1275 to 10)),
    BulkShop("Pet Shop", intArrayOf(6750, 6898, 6893), false, listOf(12130 to 10, 12125 to 1000, 12127 to 10, 12183 to 125000)),
    BulkShop("Zenesha's Platebody Shop", intArrayOf(589), false, listOf(1117 to 10, 1115 to 10, 1119 to 10, 1125 to 10, 1121 to 10)),
    BulkShop("Horvik's Armour Shop", intArrayOf(549), false, listOf(1103 to 10, 1101 to 10, 1173 to 10, 1175 to 10, 1075 to 10, 1067 to 10, 1087 to 10, 1081 to 10, 1117 to 10, 1115 to 10, 1119 to 10, 1125 to 10, 1121 to 10, 1097 to 10, 1133 to 10)),
    BulkShop("Armour Shop", intArrayOf(5485), false, listOf(1109 to 4, 1143 to 4, 1159 to 4, 1181 to 4, 1197 to 4, 1071 to 4, 1085 to 4, 1121 to 4)),
    BulkShop("Louie's Armoured Legs Bazaar", intArrayOf(542), false, listOf(1075 to 10, 1067 to 10, 1069 to 10, 1077 to 10, 1071 to 10, 1073 to 10)),
    BulkShop("Horvik's Armour Shop", intArrayOf(549), false, listOf(1103 to 10, 1101 to 10, 1173 to 10, 1175 to 10, 1075 to 10, 1067 to 10, 1087 to 10, 1081 to 10, 1117 to 10, 1115 to 10, 1119 to 10, 1125 to 10, 1121 to 10, 1097 to 10, 1133 to 10)),
    BulkShop("Seddus Adventurers Store", intArrayOf(3038), false, listOf(1093 to 10, 1079 to 10, 1113 to 10, 1099 to 10, 1193 to 10)),
    BulkShop("Ranael's Super Skirt Store", intArrayOf(544), false, listOf(1087 to 10, 1081 to 10, 1083 to 10, 1089 to 10, 1085 to 10, 1091 to 10)),
    BulkShop("Zeke's Superior Scimitars", intArrayOf(541), false, listOf(1321 to 10, 1323 to 10, 1325 to 10, 1329 to 10)),
    BulkShop("Daga's Scimitar Smithy", intArrayOf(1434), false, listOf(1321 to 10, 1323 to 10, 1325 to 10, 1329 to 10, 4587 to 10)),
    BulkShop("Cassie's Shield Shop", intArrayOf(577), false, listOf(1171 to 5, 1173 to 3, 1189 to 3, 1175 to 2, 1191 to 0, 1177 to 0, 1193 to 0, 1181 to 0)),
    BulkShop("Multicannon parts for sale", intArrayOf(209), false, listOf(6 to 5, 8 to 5, 10 to 5, 12 to 5, 5 to 5, 4 to 5)),
    BulkShop("The Spice Is Right", intArrayOf(1980), false, listOf(1931 to 30, 2169 to 10, 5970 to 0, 175 to 10)),
    BulkShop("Staff Shop", intArrayOf(546), false, listOf(1379 to 10, 1389 to 10, 1381 to 10, 1383 to 10, 1385 to 10, 1387 to 10)),
    BulkShop("Pikkupstix's Summoning Shop", intArrayOf(6971), false, listOf(12204 to 10, 12207 to 10, 12210 to 10, 12213 to 10, 12216 to 10, 12219 to 10, 12222 to 10, 12183 to 125000, 12155 to 5000)),
    BulkShop("Bogrog's Summoning Shop", intArrayOf(4472), false, listOf(12204 to 10, 12207 to 10, 12210 to 0, 12213 to 0, 12216 to 0, 12219 to 0, 12222 to 0, 12183 to 65000, 12155 to 5000)),
    BulkShop("Fortunato's Fine Wine", intArrayOf(3671), false, listOf(1993 to 10, 7810 to 10, 7919 to 10, 1935 to 10)),
    BulkShop("Gerrant's Fishy Business", intArrayOf(558), false, listOf(303 to 10, 307 to 10, 309 to 10, 311 to 30, 301 to 10, 13431 to 10, 313 to 1000, 314 to 1000, 317 to 0, 327 to 10, 345 to 0, 321 to 0, 335 to 0, 349 to 0, 331 to 0, 359 to 0, 377 to 0, 371 to 0)),
    BulkShop("Harry's Fishing Shop", intArrayOf(576), false, listOf(303 to 10, 307 to 10, 311 to 1000, 301 to 10, 313 to 100, 305 to 10, 317 to 0, 327 to 0, 345 to 0, 353 to 0, 341 to 0, 321 to 0, 359 to 0, 377 to 0, 363 to 0, 371 to 0, 383 to 0, 7810 to 1000)),
    BulkShop("Blades By Urbi", intArrayOf(5266), false, listOf(1205 to 10, 1203 to 10, 1207 to 10, 1217 to 0, 1209 to 10, 1211 to 10, 1213 to 10, 1215 to 0, 1321 to 10, 1323 to 10, 1325 to 10)),
    BulkShop("Gaius' Two Handed Shop.", intArrayOf(586), false, listOf(1307 to 4, 1309 to 3, 1311 to 2, 1313 to 1, 1315 to 1, 1317 to 1)),
    BulkShop("Gulluck And Sons", intArrayOf(602), false, listOf(882 to 1000, 877 to 300, 880 to 0, 841 to 10, 837 to 10, 1363 to 10, 1365 to 10, 1369 to 10, 1307 to 10, 1309 to 10, 1311 to 10, 1313 to 10, 1315 to 10, 1317 to 10)),
    BulkShop("Varrock Sword Shop", intArrayOf(552, 551), false, listOf(1277 to 10, 1279 to 10, 1281 to 10, 1283 to 10, 1285 to 10, 1287 to 10, 1291 to 10, 1293 to 10, 1295 to 10, 1297 to 10, 1299 to 10, 1301 to 10, 1205 to 10, 1203 to 10, 1207 to 10, 1217 to 10, 1209 to 10, 1211 to 10)),
    BulkShop("Zeke's Superior Scimitars", intArrayOf(541), false, listOf(1321 to 10, 1323 to 10, 1325 to 10, 1329 to 10)),
    BulkShop("Authentic Throwing Weapons", intArrayOf(692), false, listOf(825 to 1000, 826 to 1000, 827 to 1000, 828 to 300, 829 to 100, 830 to 100, 800 to 900, 801 to 800, 802 to 700, 803 to 600, 804 to 500, 805 to 400)),
    BulkShop("Happy Heroes Hemporium", intArrayOf(797), false, listOf(1377 to 1, 1434 to 1)),
    BulkShop("Jukat's Dragon Sword Shop", intArrayOf(564), false, listOf(1305 to 2, 1215 to 2)),
    BulkShop("Nardok's Bone Weapons", intArrayOf(4312), false, listOf(5018 to 10, 5016 to 10, 8872 to 10, 8880 to 10, 8882 to 25000)),
    BulkShop("Quality Weapons Shop", intArrayOf(2152), false, listOf(1365 to 10, 1369 to 10, 1285 to 10, 1287 to 10, 1325 to 10, 1297 to 10, 1303 to 10, 853 to 10, 888 to 100, 890 to 100)),
    BulkShop("Quartermaster's Stores", intArrayOf(1208), false, listOf(1931 to 30, 1935 to 10, 1735 to 10, 590 to 10, 2309 to 10, 3190 to 10, 3192 to 10, 3194 to 10, 3196 to 10, 3198 to 10, 3200 to 10, 3202 to 10, 3204 to 10)),
    BulkShop("Draynor Seed Market", intArrayOf(2572, 2233), false, listOf(5318 to 20, 5319 to 10, 5324 to 10, 5322 to 0, 5320 to 0, 5323 to 0, 5321 to 0, 5305 to 20, 5306 to 5, 5097 to 20, 5096 to 20, 5307 to 20, 5308 to 10, 5309 to 5, 5310 to 0, 5311 to 0)),
    BulkShop("Romily Weaklax Pie's", intArrayOf(3205), false, listOf(2323 to 1, 2325 to 1, 2327 to 1, 7178 to 1, 7188 to 1, 7198 to 1)),
    BulkShop("Slayer Equipment", intArrayOf(70, 1598, 1596, 1597, 1599, 8274, 8275, 8276, 8277, 8273, 8271, 8270, 2253, 7780), false, listOf(4155 to 50, 4156 to 50, 4158 to 50, 4160 to 50000, 4161 to 5000, 4162 to 50, 4164 to 50, 4166 to 50, 4168 to 50, 4170 to 50, 4551 to 50, 6664 to 5000, 6696 to 3000, 6720 to 50, 7051 to 50, 7159 to 50, 7421 to 50, 7432 to 50, 8923 to 50, 10952 to 50, 13279 to 5000, 13278 to 3000)),
    BulkShop("Battle Runes", intArrayOf(2257, 2258, 2259), false, listOf(554 to 100, 555 to 100, 556 to 100, 557 to 100, 558 to 100, 559 to 100, 562 to 30, 560 to 30)),
    BulkShop("Smithing Smiths Shop", intArrayOf(3162), false, listOf(1321 to 10, 1323 to 10, 1325 to 10, 1329 to 10, 2347 to 10)),
    BulkShop("Tamayu's Spear Stall", intArrayOf(1167), false, listOf(3188 to 10)),
    BulkShop("Vigr's Warhammers", intArrayOf(2151), false, listOf(1337 to 10, 1335 to 10, 1339 to 10, 1341 to 10, 1343 to 10)),
    BulkShop("Weapons galore", intArrayOf(5486), false, listOf(1299 to 4, 1343 to 4, 1369 to 4, 3099 to 4, 1315 to 4)),
    BulkShop("Construction supplies", intArrayOf(4250), false, listOf(8794 to 10, 8790 to 300, 4819 to 300, 4820 to 300, 1539 to 300)),
    BulkShop("The Toad and Chicken", intArrayOf(1079, 1357, 1358), false, listOf(1905 to 12, 1907 to 12, 1913 to 12)),
    BulkShop("Martin Thwait's Lost and Found.", intArrayOf(2270), false, listOf(954 to 50, 1523 to 25, 1755 to 30, 946 to 20, 5560 to 25, 864 to 15, 863 to 10, 865 to 5, 3095 to 3, 3096 to 2, 3097 to 1)),
    BulkShop("Diango's Toy Store", intArrayOf(970), false, listOf(2526 to 10, 2520 to 10, 2522 to 10, 2524 to 10, 4613 to 10, 12844 to 10)),
    BulkShop("The Shrimp and Parrot", intArrayOf(793), false, listOf(347 to 5, 339 to 5, 379 to 3, 373 to 2, 3144 to 3)),
    BulkShop("Oziach's Armour", intArrayOf(747), false, listOf(1127 to 2, 1135 to 2)),
    BulkShop("Scavvo's Rune Store", intArrayOf(537), false, listOf(1093 to 1, 1079 to 1, 1432 to 1, 1113 to 1, 1303 to 1, 1289 to 1, 1099 to 1, 1065 to 1, 1169 to 2)),
    BulkShop("Valaine's Shop of Champions.", intArrayOf(536), false, listOf(1021 to 2, 1165 to 1, 1077 to 1, 1123 to 1)),
    BulkShop("Bebadin Village Bartering", intArrayOf(833), true, listOf(1823 to 30, 1831 to 30, 1937 to 10, 1921 to 10, 1929 to 10, 946 to 10, 2347 to 10)),
    BulkShop("Irksol", intArrayOf(566), false, listOf(1641 to 5)),
    BulkShop("Mage Arena Staffs", intArrayOf(904), false, listOf(2415 to 5, 2416 to 5, 2417 to 5)),
    BulkShop("Sir Tiffy Cashien", intArrayOf(2290), false, listOf(5574 to 10, 5576 to 10, 5575 to 10, 9672 to 10, 9676 to 10, 9674 to 10, 9678 to 10)),
    BulkShop("Legends' Guild Shop of Useful Items", intArrayOf(933), false, listOf(299 to 100, 1542 to 5, 1590 to 3, 1052 to 1, 2368 to 3)),
    BulkShop("MoonClan Fine Clothes", intArrayOf(4518), false, listOf(9068 to 10, 9069 to 10, 9070 to 10, 9071 to 10, 9072 to 10, 9073 to 10, 9074 to 10, 1733 to 50, 1734 to 500)),
    BulkShop("Neitiznot Supplies", intArrayOf(5509), false, listOf(946 to 10, 2347 to 10, 1734 to 1000, 1733 to 10, 1351 to 10, 1759 to 1000, 4819 to 1000)),
    BulkShop("William's Wilderness Cape Shop", intArrayOf(1778), false, listOf(4315 to 100, 4335 to 100, 4355 to 100, 4375 to 100, 4395 to 100)),
    BulkShop("Ian's Wilderness Cape Shop", intArrayOf(1779), false, listOf(4317 to 100, 4337 to 100, 4357 to 100, 4377 to 100, 4397 to 100)),
    BulkShop("Larry's Wilderness Cape Shop", intArrayOf(1780), false, listOf(4319 to 100, 4339 to 100, 4359 to 100, 4379 to 100, 4399 to 100)),
    BulkShop("Darren's Wilderness Cape Shop", intArrayOf(1781), false, listOf(4321 to 100, 4341 to 100, 4361 to 100, 4381 to 100, 4401 to 100)),
    BulkShop("Edward's Wilderness Cape Shop", intArrayOf(1782), false, listOf(4323 to 100, 4343 to 100, 4363 to 100, 4383 to 100, 4403 to 100)),
    BulkShop("Richard's Wilderness Cape Shop", intArrayOf(1783), false, listOf(4325 to 100, 4345 to 100, 4365 to 100, 4385 to 100, 4405 to 100)),
    BulkShop("Neil's Wilderness Cape Shop", intArrayOf(1784), false, listOf(4327 to 100, 4347 to 100, 4367 to 100, 4387 to 100, 4407 to 100)),
    BulkShop("Edmond's Wilderness Cape Shop", intArrayOf(1785), false, listOf(4329 to 100, 4349 to 100, 4369 to 100, 4389 to 100, 4409 to 100)),
    BulkShop("Simon's Wilderness Cape Shop", intArrayOf(1786), false, listOf(4331 to 100, 4351 to 100, 4371 to 100, 4391 to 100, 4411 to 100)),
    BulkShop("Sam's Wilderness Cape Shop", intArrayOf(1787), false, listOf(4333 to 100, 4353 to 100, 4373 to 100, 4393 to 100, 4413 to 100)),
    BulkShop("Holy Wares", intArrayOf(1369), false, listOf(6746 to 1, 732 to 100)),
    BulkShop("Rok's Choc Box", intArrayOf(3045), false, listOf(6794 to 30, 1973 to 25)),
    BulkShop("Ak-haranu's Exotic Goods", intArrayOf(1688), false, listOf(4740 to 100000)),
    BulkShop("The Garden Centre", intArrayOf(4251), false, listOf(8417 to 10, 8419 to 10, 8421 to 10, 8423 to 10, 8425 to 10, 8427 to 10, 8429 to 10, 8431 to 1000, 8433 to 10, 8435 to 10, 8451 to 10, 8453 to 10, 8455 to 10, 8457 to 10, 8459 to 10, 8461 to 10, 8437 to 10, 8439 to 10, 8441 to 10, 8443 to 10, 8445 to 10, 8447 to 10, 8449 to 10)),
    BulkShop("Rasolo's Goods", intArrayOf(1972), false, listOf(1969 to 2, 2023 to 4, 740 to 10, 1215 to 1, 550 to 1, 583 to 2, 1941 to 10, 970 to 1, 975 to 1, 1599 to 1, 2976 to 1, 1823 to 1, 1837 to 1, 1864 to 5, 2520 to 1, 3377 to 1, 626 to 1, 1909 to 1, 3787 to 1, 3711 to 1, 3678 to 1, 3424 to 5, 3420 to 1, 5 to 1)),
    BulkShop("Fishing Guild Shop.", intArrayOf(592), false, listOf(313 to 1000, 314 to 1000, 341 to 0, 353 to 0, 363 to 0, 359 to 0, 377 to 0, 371 to 0, 339 to 0, 355 to 0, 365 to 0, 361 to 0, 379 to 0, 373 to 0)),
    BulkShop("Ore Store", intArrayOf(5483), false, listOf(436 to 10, 438 to 10, 440 to 0, 442 to 0, 453 to 0, 444 to 0, 447 to 0, 449 to 0)),
    BulkShop("Tony's Pizza Bases", intArrayOf(596), false, listOf(2283 to 10)),
    BulkShop("Silver Cog Silver Stall", intArrayOf(2159), false, listOf(1714 to 2, 442 to 1, 2355 to 1)),
    BulkShop("Carefree Crafting Stall", intArrayOf(2158), false, listOf(1755 to 2, 1592 to 4, 1597 to 2, 1733 to 3, 1734 to 100, 1759 to 100)),
    BulkShop("Keldagrim's Best Bread", intArrayOf(2156), false, listOf(2309 to 10, 1891 to 3, 1901 to 8)),
    BulkShop("Quality Armour Shop", intArrayOf(2153), false, listOf(1105 to 3, 1109 to 1, 1107 to 1, 1111 to 1, 1141 to 3, 1143 to 1, 1145 to 1, 1177 to 0, 1195 to 0)),
    BulkShop("Funch's Fine Groceries", intArrayOf(603), false, listOf(2021 to 10, 2019 to 10, 2015 to 10, 2017 to 10, 2114 to 10, 2128 to 20, 2108 to 20, 2102 to 20, 2126 to 5, 2025 to 10, 1973 to 20, 1975 to 10, 2130 to 5, 1927 to 5, 946 to 5, 2026 to 20)),
    BulkShop("Frincos' Fabulous Herb Store.", intArrayOf(578), false, listOf(229 to 10, 233 to 10, 221 to 10)),
    BulkShop("Port Khazard General Store", intArrayOf(555), true, listOf(1941 to 200, 954 to 10, 583 to 10, 1931 to 30, 1935 to 10, 1735 to 10, 1925 to 10, 1923 to 10, 1887 to 10, 590 to 10, 1755 to 10, 2347 to 10, 550 to 10, 9003 to 10)),
    BulkShop("Agmundi Quality Clothes", intArrayOf(2161), false, listOf(5050 to 3, 5052 to 3, 5038 to 3, 5040 to 3, 5044 to 3, 5046 to 3, 5026 to 3, 5028 to 3, 5032 to 3, 5034 to 3)),
    BulkShop("Fernahei's Fishing Hut", intArrayOf(517), false, listOf(307 to 10, 309 to 10, 313 to 1000, 314 to 1000, 335 to 0, 349 to 0, 331 to 0)),
    BulkShop("Lletya Food Store", intArrayOf(2357), false, listOf(2309 to 10, 379 to 10, 1993 to 10, 1985 to 10, 1891 to 10)),
    BulkShop("Lletya Archery Shop", intArrayOf(2356), false, listOf(884 to 2000, 886 to 500, 888 to 500, 890 to 450, 892 to 400, 877 to 1500, 843 to 5, 845 to 5, 837 to 5, 849 to 5, 847 to 5)),
    BulkShop("Sigmund the Merchant", intArrayOf(1282), false, listOf(590 to 10, 954 to 10, 1931 to 30, 2142 to 10, 2309 to 10, 952 to 10, 36 to 10, 1755 to 10, 229 to 310, 227 to 310, 1925 to 30, 1944 to 10, 1942 to 10, 233 to 10, 2347 to 10, 1929 to 10)),
    BulkShop("Fremennik Fishmonger", intArrayOf(1315), false, listOf(303 to 5, 307 to 5, 309 to 5, 311 to 2, 301 to 2, 313 to 1000, 314 to 1000, 305 to 5, 317 to 10, 327 to 10, 345 to 0, 353 to 0, 341 to 0, 321 to 0, 335 to 0, 331 to 0, 359 to 0, 377 to 0, 363 to 0, 371 to 0, 383 to 0)),
    BulkShop("Rellekka Longhall Bar", intArrayOf(1300), false, listOf(1917 to 10, 3803 to 10, 3711 to 10)),
    BulkShop("Gnomic Supplies", intArrayOf(7420), false, listOf(1931 to 100, 1929 to 100, 1937 to 100, 2313 to 100, 1887 to 100, 590 to 100, 1735 to 100, 1951 to 100, 1969 to 100, 1949 to 100)),
    BulkShop("Keldagrim Stonemason", intArrayOf(4248), false, listOf(3420 to 500, 8786 to 30, 8784 to 30, 8788 to 30)),
    BulkShop("Lovecraft's Tackle", intArrayOf(4856), false, listOf(303 to 10, 307 to 10, 309 to 10, 311 to 1000, 301 to 10, 313 to 1000, 314 to 1000, 317 to 0, 327 to 10, 345 to 0, 321 to 0, 335 to 0, 349 to 0, 331 to 0, 359 to 0, 377 to 0, 371 to 0)),
    BulkShop("Flosi's Fishmongers", intArrayOf(5484), false, listOf(377 to 5, 359 to 20, 331 to 20, 341 to 20, 383 to 0)),
    BulkShop("Contraband yak produce.", intArrayOf(5495), false, listOf(10818 to 25, 10816 to 50, 10814 to 50, 10820 to 10)),
    BulkShop("Tiadeche's Karambwan Stall", intArrayOf(1163, 1164), false, listOf(3142 to 10, 3157 to 10)),
    BulkShop("Leprechaun Larry's Farming Supplies.", intArrayOf(4965), false, listOf(5341 to 10, 5343 to 10, 5329 to 10, 952 to 10, 5325 to 10, 5331 to 10, 1925 to 10, 6036 to 10, 2026 to 10, 1480 to 10)),
    BulkShop("Blurberry Bar", intArrayOf(849), false, listOf(2028 to 10, 2030 to 10, 2032 to 10, 2034 to 10, 2036 to 10, 2038 to 10, 2040 to 10)),
    BulkShop("The Armour Store", intArrayOf(2565), false, listOf(1147 to 0, 1163 to 0, 1127 to 0, 1093 to 0, 1185 to 0, 1201 to 0, 1113 to 0, 1079 to 0, 1145 to 0, 1161 to 0, 1123 to 0, 1091 to 0, 1183 to 0, 1199 to 0, 1111 to 0, 1073 to 0, 1143 to 0, 1159 to 0, 1121 to 0, 1085 to 0, 1181 to 0, 1197 to 0, 1109 to 0, 1071 to 0)),
    BulkShop("Gift Shop", intArrayOf(7048), false, listOf(590 to 10, 2866 to 100, 2878 to 10, 314 to 1000, 1351 to 10, 954 to 10, 1931 to 30, 1925 to 30, 2347 to 10, 946 to 10, 12561 to 10, 12563 to 10, 12565 to 0, 12567 to 0, 12568 to 0, 12559 to 0, 12570 to 0)),
    BulkShop("Miscellanian Food Shop", intArrayOf(3923), false, listOf(2309 to 5, 1985 to 5, 1965 to 5, 1942 to 5, 1957 to 5, 1933 to 5, 1973 to 5, 1927 to 5)),
    BulkShop("The Esoterican Arms", intArrayOf(3920), false, listOf(1917 to 10, 5763 to 10, 1993 to 5, 1798 to 5)),
    BulkShop("Miscellanian Clothes Shop", intArrayOf(1383, 3921), false, listOf(3767 to 5, 3769 to 5, 3771 to 5, 3773 to 5, 3775 to 5, 3795 to 5, 5050 to 3, 5052 to 3, 5038 to 3, 5040 to 3, 5044 to 3, 5046 to 3, 5026 to 3, 5028 to 3, 5032 to 3, 5034 to 3)),
    BulkShop("Island Fishmonger", intArrayOf(1393), false, listOf(303 to 5, 307 to 5, 309 to 5, 311 to 2, 301 to 2, 313 to 1500, 314 to 1000, 305 to 5, 317 to 0, 325 to 200, 345 to 0, 353 to 0, 341 to 0, 321 to 0, 335 to 0, 349 to 0, 331 to 0, 359 to 0, 377 to 0, 363 to 0, 371 to 0, 383 to 0)),
    BulkShop("Greengrocer of Miscellania", intArrayOf(1394), false, listOf(1965 to 10, 1942 to 10, 1957 to 10, 1982 to 10, 1550 to 2)),
    BulkShop("Legends Guild General Store", intArrayOf(932), false, listOf(373 to 20, 2323 to 5, 121 to 3, 886 to 500)),
    BulkShop("Two Feet Charley's Fish Shop", intArrayOf(3161), false, listOf(317 to 10, 327 to 10, 345 to 10, 353 to 10, 341 to 10, 321 to 10, 359 to 0, 377 to 0, 363 to 0)),
    BulkShop("Fremennik Fur Trader", intArrayOf(1316), false, listOf(948 to 10, 958 to 10, 10117 to 0, 10121 to 0, 10119 to 0, 10123 to 0, 10093 to 0, 10095 to 0, 10097 to 0, 10099 to 0, 10101 to 0, 10103 to 0)),
    BulkShop("Leon's Prototype Crossbow", intArrayOf(5111), false, listOf(10156 to 2)),
    BulkShop("Uglug's Stuffsies", intArrayOf(2039), false, listOf(4844 to 100, 10927 to 0, 2862 to 100, 1777 to 10, 2876 to 0, 2878 to 10, 4850 to 0, 946 to 5, 4773 to 0, 4778 to 0, 4783 to 0, 4788 to 0, 4793 to 0, 4798 to 0, 4803 to 0, 4827 to 0)),
)

on_world_init_late {
    var created = 0
    var bound = 0
    val skipped = mutableListOf<String>()
    BULK_SHOPS.forEach { shop ->
        if (world.getShop(shop.title) != null) {
            skipped.add("${shop.title} (title exists)")
            return@forEach
        }
        val validItems = shop.stock.filter { (id, _) -> id in 0 until world.definitions.getCount(gg.rsmod.game.fs.def.ItemDef::class.java) }
        if (validItems.isEmpty()) {
            skipped.add("${shop.title} (no items)")
            return@forEach
        }
        val keepers =
            shop.npcs.toList().mapNotNull { id: Int ->
                if (id < 0 || id >= world.definitions.getCount(NpcDef::class.java)) return@mapNotNull null
                val def = world.definitions.get(NpcDef::class.java, id)
                val slot = def.options.indexOfFirst { it != null && (it.equals("trade", true) || it.equals("shop", true) || it.equals("buy", true)) }
                if (slot == -1) return@mapNotNull null
                if (world.plugins.boundNpcOptions(id).contains(slot + 1)) return@mapNotNull null
                id to slot + 1
            }
        if (keepers.isEmpty()) {
            skipped.add("${shop.title} (no free keeper)")
            return@forEach
        }
        create_shop(
            shop.title,
            currency = CoinCurrency(),
            purchasePolicy = if (shop.generalStore) PurchasePolicy.BUY_TRADEABLES else PurchasePolicy.BUY_STOCK,
            stockSize = maxOf(40, validItems.size),
            containsSamples = false,
        ) {
            validItems.forEachIndexed { index, pair -> items[index] = ShopItem(pair.first, pair.second) }
        }
        created++
        keepers.forEach { (id, slot) ->
            world.plugins.bindNpc(id, slot) {
                player.openShop(shop.title)
            }
            bound++
        }
    }
    println("bulk_shops: created $created shops, bound $bound shopkeepers, skipped ${skipped.size}.")
    if (skipped.isNotEmpty()) {
        println("bulk_shops skipped: ${skipped.joinToString("; ")}")
    }
}
