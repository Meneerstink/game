package gg.rsmod.plugins.content.areas.wilderness.npcs

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.drops.DropTableBuilder
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.quests.QuestStatus

/** N522-NPCS-ONDERZOEK appendix B: exact 128-slot herb weights, not the rounded legacy 667 table. */
object WildernessNpcDrops {
    /** Only the new families whose table depends on equipment, quest completion or diary entitlement. */
    fun isPlayerDependent(id: Int): Boolean = id == 15088 || id in 15091..15095 || id == 15098 || id == 15101 || id == 15102

    private val herbWeights = listOf(
        Items.GRIMY_GUAM to 32, Items.GRIMY_MARRENTILL to 24, Items.GRIMY_TARROMIN to 18,
        Items.GRIMY_HARRALANDER to 14, Items.GRIMY_RANARR to 11, Items.GRIMY_IRIT to 8,
        Items.GRIMY_AVANTOE to 6, Items.GRIMY_KWUARM to 5, Items.GRIMY_CADANTINE to 4,
        Items.GRIMY_LANTADYME to 3, Items.GRIMY_DWARF_WEED to 3,
    )

    val herbs = DropTableFactory.build {
        main { total(128); herbWeights.forEach { (id, weight) -> obj(id, slots = weight) } }
    }

    // A table() child only rolls its first named table. Enumerating the Cartesian product preserves TWO independent rolls,
    // including different herbs and duplicate herbs; using quantity=2 on one herb roll would not.
    val doubleHerbs = DropTableFactory.build {
        main {
            total(128 * 128)
            herbWeights.forEach { (first, a) ->
                herbWeights.forEach { (second, b) -> objs(Item(first), Item(second), slots = a * b) }
            }
        }
    }

    // Report section 9: one herb TYPE, amount 1/2/3/4 weighted 15/20/15/5, not a uniform 1..4 amount.
    val druidHerbs = DropTableFactory.build {
        main {
            total(128 * 55)
            herbWeights.forEach { (id, weight) ->
                listOf(1 to 15, 2 to 20, 3 to 15, 4 to 5).forEach { (amount, count) ->
                    obj(id, quantity = amount, slots = weight * count)
                }
            }
        }
    }

    val druidRobes = DropTableFactory.build {
        main {
            total(11)
            obj(Items.ZAMORAK_ROBE_1035, slots = 4)
            obj(Items.ZAMORAK_ROBE, slots = 4)
            obj(Items.ELDER_CHAOS_HOOD)
            obj(Items.ELDER_CHAOS_ROBE)
            obj(Items.ELDER_CHAOS_TOP)
        }
    }

    fun monkeyMadnessII(player: Player) = QuestStatus.isCompleted(player, "Monkey Madness II")
    fun observatory(player: Player) = QuestStatus.isCompleted(player, "Observatory Quest")
    fun legends(player: Player) = QuestStatus.isCompleted(player, "Legends' Quest")

    // No Wilderness Diary completion provider exists in this checkout. Never grant medium/elite benefits by default.
    // Claude handoff: connect the actual completion provider before enabling the alternate pirate table/Ent diary bonus.
    fun mediumDiary(@Suppress("UNUSED_PARAMETER") player: Player): Boolean = false

    fun wealth(player: Player): Boolean {
        val id = player.equipment[EquipmentType.RING.id]?.id ?: return false
        // The canonical 667 ring; imported charged/imbued variants have their cache name checked too.
        return id == Items.RING_OF_WEALTH || runCatching {
            player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name
                .lowercase().startsWith("ring of wealth")
        }.getOrDefault(false)
    }

    fun clueDenominator(player: Player, normal: Int): Int {
        val ring = player.equipment[EquipmentType.RING.id]?.id ?: return normal
        val name = runCatching { player.world.definitions.get(ItemDef::class.java, ring).name.lowercase() }.getOrDefault("")
        return if (name.startsWith("ring of wealth") && "(i" in name) normal / 2 else normal
        // Combat Achievement tiers and scroll boxes have no provider in this checkout; see handoff.
    }

    fun pirateItem(player: Player, id: Int): Int {
        if (player.equipment[EquipmentType.AMULET.id]?.id != Items.AMULET_OF_AVARICE) return id
        val def = player.world.definitions.get(ItemDef::class.java, id)
        return if (!def.noted && def.noteLinkId > 0) def.noteLinkId else id
    }

    val rare: DropTableBuilder.() -> Unit = {
        WildernessNpcRareDrops.rare(wealth(player), legends(player)).invoke(this)
    }
    val gems: DropTableBuilder.() -> Unit = {
        WildernessNpcRareDrops.gems(wealth(player), legends(player)).invoke(this)
    }

    fun longBones(builder: DropTableBuilder) = with(builder) {
        table("long_bone") { total(400); obj(Items.LONG_BONE); nothing(399) }
        // Factory and Drop Viewer apply this only after the long-bone roll failed (Mod Ash exact 399/2,000,000).
        table("curved_bone") { total(5000); obj(Items.CURVED_BONE); nothing(4999) }
    }
}
