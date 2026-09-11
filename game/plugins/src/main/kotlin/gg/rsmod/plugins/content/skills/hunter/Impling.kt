package gg.rsmod.plugins.content.skills.hunter

import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*

/**
 * Classic Puro-Puro impling capture, ported from the donor's flat per-tier reward tables
 * (Novite `Hunter.FlyingEntities`) into this engine's plugin idiom. Only the impling NPCs that
 * already have real spawns wired (see godwars/area spawn files as they're added) will be
 * reachable in practice; the data below covers every jar/npc id already defined in this
 * engine's item/npc caches so any future spawn table works immediately without touching this file.
 */
data class ImplingTier(
    val npcId: Int,
    val jarItem: Int,
    val level: Int,
    val xp: Double,
    val common: List<Pair<Int, IntRange>>,
    val rare: List<Pair<Int, IntRange>>,
)

object ImplingData {
    val TIERS =
        listOf(
            ImplingTier(
                npcId = Npcs.BABY_IMPLING,
                jarItem = Items.BABY_IMPLING_JAR,
                level = 17,
                xp = 20.0,
                common = listOf(946 to 1..1, 1755 to 1..1, 1734 to 1..1, 1733 to 1..1, 2347 to 1..1, 1985 to 1..1),
                rare = listOf(1927 to 1..1, 319 to 1..1, 2007 to 1..1, 1779 to 1..1, 7170 to 1..1, 401 to 1..1, 1438 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.YOUNG_IMPLING,
                jarItem = Items.YOUNG_IMPLING_JAR,
                level = 22,
                xp = 48.0,
                common = listOf(361 to 1..1, 1901 to 1..1, 1539 to 5..5, 1784 to 4..4, 1523 to 1..1, 7936 to 1..1, 5970 to 1..1),
                rare = listOf(855 to 1..1, 1353 to 1..1, 2293 to 1..1, 7178 to 1..1, 247 to 1..1, 453 to 1..1, 1777 to 1..1, 231 to 1..1, 2347 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.GOURMET_IMPLING,
                jarItem = Items.GOURM_IMPLING_JAR,
                level = 28,
                xp = 82.0,
                common = listOf(361 to 1..1, 365 to 1..1, 1897 to 1..1, 2007 to 1..1, 2011 to 1..1, 2293 to 1..1, 2327 to 1..1, 5970 to 1..1),
                rare = listOf(247 to 1..1, 379 to 1..1, 385 to 1..1, 1883 to 1..1, 1885 to 1..1, 5755 to 1..1, 6969 to 1..1, 7170 to 1..1, 7178 to 1..1, 7188 to 1..1, 7754 to 1..1, 8244 to 1..1, 8526 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.EARTH_IMPLING,
                jarItem = Items.EARTH_IMPLING_JAR,
                level = 36,
                xp = 126.0,
                common = listOf(444 to 1..1, 557 to 32..32, 1441 to 6..6, 1442 to 1..1, 2353 to 1..1, 5104 to 1..1, 5535 to 1..1, 6032 to 1..1),
                rare = listOf(237 to 1..1, 447 to 1..1, 1273 to 1..1, 454 to 6..6, 1487 to 1..1, 5311 to 2..2, 5294 to 2..2),
            ),
            ImplingTier(
                npcId = Npcs.ESSENCE_IMPLING,
                jarItem = Items.ESS_IMPLING_JAR,
                level = 42,
                xp = 160.0,
                common = listOf(562 to 4..4, 554 to 50..50, 555 to 30..30, 556 to 60..60, 559 to 30..30, 1448 to 1..1, 7937 to 20..20),
                rare = listOf(564 to 4..4, 4694 to 4..4, 4696 to 4..4, 4698 to 4..4),
            ),
            ImplingTier(
                npcId = Npcs.ECLECTIC_IMPLING,
                jarItem = Items.ECLECTIC_IMPLING_JAR,
                level = 50,
                xp = 205.0,
                common = listOf(1273 to 1..1, 5970 to 1..1, 231 to 1..1, 556 to 41..41, 8779 to 4..4, 12111 to 1..1),
                rare = listOf(2358 to 5..5, 444 to 1..1, 4527 to 1..1, 237 to 1..1, 7937 to 25..25, 1199 to 1..1, 2349 to 1..1, 2351 to 1..1, 2353 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.NATURE_IMPLING,
                jarItem = Items.NATURE_IMPLING_JAR,
                level = 58,
                xp = 250.0,
                common = listOf(5100 to 1..1, 5104 to 1..1, 5281 to 1..1, 5294 to 1..1, 6016 to 1..1, 1513 to 1..1, 253 to 4..4),
                rare = listOf(5298 to 5..5, 5299 to 1..1, 5297 to 1..1, 3051 to 1..1, 5285 to 1..1, 5286 to 1..1, 5313 to 1..1, 5974 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.MAGPIE_IMPLING,
                jarItem = Items.MAGPIE_IMPLING_JAR,
                level = 65,
                xp = 289.0,
                common = listOf(1682 to 3..3, 1732 to 3..3, 2569 to 3..3, 3391 to 1..1, 5541 to 1..1, 1748 to 6..6),
                rare = listOf(1333 to 1..1, 1347 to 1..1, 2571 to 5..5, 4097 to 1..1, 4095 to 1..1, 2364 to 2..2, 1603 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.NINJA_IMPLING,
                jarItem = Items.NINJA_IMPLING_JAR,
                level = 74,
                xp = 339.0,
                common = listOf(6328 to 1..1, 3385 to 1..1, 3391 to 1..1, 4097 to 1..1, 4095 to 1..1, 3101 to 1..1, 1333 to 1..1, 1347 to 1..1, 1215 to 1..1, 6313 to 1..1, 892 to 70..70, 811 to 70..70),
                rare = listOf(9342 to 1..1, 6155 to 1..1),
            ),
            ImplingTier(
                npcId = Npcs.SPIRIT_IMPLING,
                jarItem = Items.SPIRIT_IMPLING_JAR,
                level = 83,
                xp = 400.0,
                common = listOf(2135 to 25..25, 2139 to 25..25, 9979 to 15..15, 3363 to 5..5, 1934 to 14..14, 1964 to 25..25),
                rare = listOf(2360 to 1..1, 2361 to 1..1, 2363 to 1..1, 6155 to 1..1, 7939 to 1..1, 10819 to 7..7),
            ),
            ImplingTier(
                npcId = Npcs.KINGLY_IMPLING,
                jarItem = Items.KINGLY_IMPLING_JAR,
                level = 98,
                xp = 480.0,
                common = listOf(1705 to 3..11, 1684 to 3..3, 1618 to 17..34, 990 to 2..2),
                rare = listOf(1631 to 1..1, 1615 to 1..1, 9341 to 40..70, 9342 to 57..57, 11212 to 40..144, 9193 to 62..70, 11230 to 182..319, 11232 to 70..70),
            ),
        )

    fun byNpc(npc: Int): ImplingTier? = TIERS.firstOrNull { it.npcId == npc }

    fun byJar(item: Int): ImplingTier? = TIERS.firstOrNull { it.jarItem == item }
}

object Impling {
    private const val CATCH_ANIM = 6606

    fun catch(
        player: Player,
        npc: Npc,
    ) {
        val tier = ImplingData.byNpc(npc.id) ?: return
        val net = player.hasEquipped(EquipmentType.WEAPON, Items.BUTTERFLY_NET, Items.MAGIC_BUTTERFLY_NET)
        val requiredLevel = if (net) tier.level else tier.level + 10
        val level = player.skills.getMaxLevel(Skills.HUNTER)
        if (level < requiredLevel) {
            player.filterableMessage(
                if (net) {
                    "You need a Hunter level of $requiredLevel to catch this impling."
                } else {
                    "You need a Hunter level of $requiredLevel to catch this impling barehanded."
                },
            )
            return
        }
        val hasJar = player.inventory.getItemCount(Items.IMPLING_JAR) > 0
        if (!hasJar && player.inventory.isFull) {
            player.filterableMessage("You'll need to clear some space in your pack to catch this impling barehanded.")
            return
        }
        player.animate(CATCH_ANIM)
        player.filterableMessage("You swing your net...")
        player.queue {
            wait(2)
            val chance = interpolate(90, 220, player.skills.getCurrentLevel(Skills.HUNTER))
            if (chance <= RANDOM.nextInt(255)) {
                player.filterableMessage("...you stumble and miss the ${npc.name.lowercase()}.")
                return@queue
            }
            player.world.remove(npc)
            player.addXp(Skills.HUNTER, tier.xp, checkBrawlingGloves = true)
            if (hasJar) {
                player.inventory.remove(Items.IMPLING_JAR, 1)
                player.inventory.add(tier.jarItem, 1)
                player.filterableMessage("You manage to catch the impling and squeeze it into a jar.")
            } else {
                rollLoot(player, tier)
                player.filterableMessage("You manage to catch the impling and acquire some loot.")
            }
        }
    }

    fun openJar(
        player: Player,
        jarItem: Int,
    ) {
        val tier = ImplingData.byJar(jarItem) ?: return
        if (player.inventory.isFull) {
            player.filterableMessage("You'll need to clear some space in your pack before opening this.")
            return
        }
        player.inventory.remove(jarItem, 1)
        if (RANDOM.nextInt(5) == 0) {
            player.filterableMessage("You press too hard on the jar and the glass shatters in your hands.")
            return
        }
        rollLoot(player, tier)
    }

    private fun rollLoot(
        player: Player,
        tier: ImplingTier,
    ) {
        val rare = tier.rare.isNotEmpty() && RANDOM.nextInt(100) < 30
        val table = if (rare) tier.rare else tier.common
        if (table.isEmpty()) return
        val (item, amounts) = table[RANDOM.nextInt(table.size)]
        val amount = amounts.first + RANDOM.nextInt(amounts.last - amounts.first + 1)
        if (player.inventory.hasSpace) {
            player.inventory.add(item, amount)
        } else {
            player.world.spawn(GroundItem(item, amount, player.tile, player))
        }
    }
}
