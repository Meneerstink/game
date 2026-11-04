package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.inter.bank.Bank

/**
 * Owner 2026-10-10: "make me like 10 commands in the gui one for full eclipse with boots gloves amulet cape and so other for full
 * crystal bow feardhinen and so on" - owner-only `::gear_<name>` presets of the OSRS PvP gear this server has (the ids of
 * data/cfg/osrs_gear_testlist.txt), shown on the Gear tab of the staff command list (`::mod`) where one click wears the set.
 *
 * [apply]: the worn gear goes to the bank (the bank's own "Deposit worn items", so charges stay and loot keys are refused), then
 * every piece is worn through [EquipAction] - the click path, so the weapon tab, attack style and autocast follow - with the ammo
 * in the ammo slot; [Preset.extras] (spec weapon, switch bolts, runes) go to the inventory, or the bank when it is full. Only
 * charge-free variants are used (blessed quiver, active crystal), so nothing needs charging first.
 */
object GearPresets {
    class Preset(val command: String, val title: String, val worn: List<Pair<Int, Int>>, val extras: List<Pair<Int, Int>> = emptyList())

    private fun set(vararg items: Int) = items.map { it to 1 }

    private val OWNER_COMMON = set(Items.NEITIZNOT_FACEGUARD, Items.INFERNAL_CAPE, Items.AMULET_OF_FURY, Items.GLOVES_7462, Items.ETERNAL_BOOTS, Items.RING_OF_SUFFERING_R)
    private val RANGED_ACCESSORIES = set(Items.NECKLACE_OF_ANGUISH, Items.BLESSED_DIZANAS_QUIVER, Items.ZARYTE_VAMBRACES, Items.PEGASIAN_BOOTS)
    private val MELEE_ACCESSORIES = set(Items.AMULET_OF_RANCOUR_S, Items.INFERNAL_CAPE, Items.FEROCIOUS_GLOVES, Items.PRIMORDIAL_BOOTS, Items.BERSERKER_RING_I)
    private val MAGIC_ACCESSORIES = set(Items.OCCULT_NECKLACE, Items.IMBUED_ZAMORAK_CAPE, Items.TORMENTED_BRACELET, Items.ETERNAL_BOOTS, Items.MAGUS_RING)
    private val BARRAGE_RUNES = listOf(Items.BLOOD_RUNE to 5000, Items.DEATH_RUNE to 5000, Items.WATER_RUNE to 5000, Items.SOUL_RUNE to 2000)

    val ALL: List<Preset> =
        listOf(
            // Owner 2026-11-04: the standard max test sets ("waarom maak je geen standaard max gear test set"), with potions.
            Preset(
                "gear_max", "Max melee (Torva, Osmumten's fang, Avernic, rancour, ferocious, Ultor, primordial, infernal)",
                set(Items.TORVA_FULL_HELM, Items.TORVA_PLATEBODY, Items.TORVA_PLATELEGS, Items.OSMUMTENS_FANG, Items.AVERNIC_DEFENDER,
                    Items.AMULET_OF_RANCOUR, Items.FEROCIOUS_GLOVES, Items.ULTOR_RING, Items.PRIMORDIAL_BOOTS, Items.INFERNAL_CAPE),
                listOf(Items.SUPER_COMBAT_POTION_4 to 4, Items.SUPER_RESTORE_4 to 4, Items.SHARK to 10),
            ),
            Preset(
                "gear_maxrange", "Max range (Masori (f), Zaryte crossbow, buckler, anguish, Dizana's quiver, diamond dragon bolts (e))",
                set(Items.MASORI_MASK_F, Items.MASORI_BODY_F, Items.MASORI_CHAPS_F, Items.ZARYTE_CROSSBOW, Items.TWISTED_BUCKLER,
                    Items.NECKLACE_OF_ANGUISH, Items.DIZANAS_QUIVER, Items.ZARYTE_VAMBRACES, Items.PEGASIAN_BOOTS, Items.VENATOR_RING) +
                    listOf(Items.DIAMOND_DRAGON_BOLTS_E to 5000),
                listOf(Items.RANGING_POTION_4 to 4, Items.SUPER_RESTORE_4 to 4, Items.SHARK to 10),
            ),
            // The owner's own sets from his bug reports (teamtest1/2 wore them until 2026-11-04).
            Preset(
                "gear_owner", "Owner hybrid (Toxic staff of the dead + Elidinis' ward, Karil's)",
                OWNER_COMMON + listOf(Items.TOXIC_STAFF_OF_THE_DEAD to 1, Items.ELIDINIS_WARD to 1, Items.KARILS_TOP to 1,
                    Items.KARILS_SKIRT to 1, Items.OPAL_DRAGON_BOLTS_E to 301),
                listOf(Items.SHARK to 10),
            ),
            Preset(
                "gear_ownerrange", "Owner ranged (Dark bow + dragon arrows, Karil's 100)",
                OWNER_COMMON + listOf(Items.DARK_BOW to 1, Items.KARILS_TOP_100 to 1, Items.KARILS_SKIRT_100 to 1, Items.DRAGON_ARROW to 98),
                listOf(Items.SHARK to 10),
            ),
            Preset(
                "gear_eclipse", "Eclipse moon + Eclipse atlatl",
                set(Items.ECLIPSE_MOON_HELM, Items.ECLIPSE_MOON_CHESTPLATE, Items.ECLIPSE_MOON_TASSETS, Items.ECLIPSE_ATLATL, Items.TWISTED_BUCKLER) +
                    MELEE_ACCESSORIES + listOf(Items.ATLATL_DART to 5000),
            ),
            Preset(
                "gear_crystal", "Crystal armour + Bow of Faerdhinen",
                set(Items.CRYSTAL_HELM, Items.CRYSTAL_BODY, Items.CRYSTAL_LEGS, Items.BOW_OF_FAERDHINEN_C, Items.VENATOR_RING) + RANGED_ACCESSORIES,
            ),
            Preset(
                "gear_tbow", "Masori (f) + Twisted bow",
                set(Items.MASORI_MASK_F, Items.MASORI_BODY_F, Items.MASORI_CHAPS_F, Items.TWISTED_BOW, Items.VENATOR_RING) + RANGED_ACCESSORIES +
                    listOf(Items.DRAGON_ARROW to 5000),
            ),
            Preset(
                "gear_zcb", "Masori (f) + Zaryte crossbow, enchanted dragon bolts",
                set(Items.MASORI_MASK_F, Items.MASORI_BODY_F, Items.MASORI_CHAPS_F, Items.ZARYTE_CROSSBOW, Items.TWISTED_BUCKLER, Items.RING_OF_SUFFERING_RI) +
                    RANGED_ACCESSORIES + listOf(Items.DIAMOND_DRAGON_BOLTS_E to 5000),
                listOf(Items.RUBY_DRAGON_BOLTS_E to 5000, Items.DRAGONSTONE_DRAGON_BOLTS_E to 5000, Items.ONYX_DRAGON_BOLTS_E to 5000),
            ),
            Preset(
                "gear_ballista", "Morrigan's + Heavy ballista",
                set(Items.MORRIGANS_COIF, Items.MORRIGANS_LEATHER_BODY, Items.MORRIGANS_LEATHER_CHAPS, Items.HEAVY_BALLISTA, Items.RING_OF_SUFFERING_RI) +
                    RANGED_ACCESSORIES + listOf(Items.OSRS_DRAGON_JAVELIN to 5000),
                listOf(Items.MORRIGANS_JAVELIN to 500),
            ),
            Preset(
                "gear_ancestral", "Twisted ancestral + Kodai wand (Volatile spec)",
                set(Items.TWISTED_ANCESTRAL_HAT, Items.TWISTED_ANCESTRAL_ROBE_TOP, Items.TWISTED_ANCESTRAL_ROBE_BOTTOM, Items.KODAI_WAND, Items.ELIDINIS_WARD_F) +
                    MAGIC_ACCESSORIES,
                set(Items.VOLATILE_NIGHTMARE_STAFF) + BARRAGE_RUNES,
            ),
            Preset(
                "gear_bluemoon", "Blue moon + Blue moon spear",
                set(Items.BLUE_MOON_HELM, Items.BLUE_MOON_CHESTPLATE, Items.BLUE_MOON_TASSETS, Items.BLUE_MOON_SPEAR) + MAGIC_ACCESSORIES,
                BARRAGE_RUNES,
            ),
            Preset(
                "gear_bloodmoon", "Blood moon + Dual macuahuitl",
                set(Items.BLOOD_MOON_HELM, Items.BLOOD_MOON_CHESTPLATE, Items.BLOOD_MOON_TASSETS, Items.DUAL_MACUAHUITL) + MELEE_ACCESSORIES,
            ),
            Preset(
                "gear_torva", "Torva + Osmumten's fang (AGS, Voidwaker, Burning claws)",
                set(Items.TORVA_FULL_HELM, Items.TORVA_PLATEBODY, Items.TORVA_PLATELEGS, Items.OSMUMTENS_FANG, Items.AVERNIC_DEFENDER) + MELEE_ACCESSORIES,
                set(Items.ANCIENT_GODSWORD, Items.VOIDWAKER, Items.BURNING_CLAWS),
            ),
            Preset(
                "gear_statius", "Statius's + Vesta's longsword (Statius's warhammer)",
                set(Items.STATIUSS_FULL_HELM, Items.STATIUSS_PLATEBODY, Items.STATIUSS_PLATELEGS, Items.VESTAS_LONGSWORD, Items.AVERNIC_DEFENDER) +
                    MELEE_ACCESSORIES,
                set(Items.STATIUSS_WARHAMMER, Items.VESTAS_CHAINBODY, Items.VESTAS_PLATESKIRT),
            ),
        )

    private val BY_COMMAND = ALL.associateBy { it.command }

    fun forCommand(command: String): Preset? = BY_COMMAND[command]

    /** One line for the list: the set and what it holds. */
    fun summary(
        player: Player,
        preset: Preset,
    ): String {
        val defs = player.world.definitions
        val names = (preset.worn + preset.extras).map { (id, _) -> defs.getNullable(ItemDef::class.java, id)?.name ?: "#$id" }
        return "<col=ff981f>${preset.title}</col>: ${names.joinToString(", ")}"
    }

    /** Wears [preset] (see the class comment); tells [player] what happened. */
    fun apply(
        player: Player,
        preset: Preset,
    ) {
        if (player.inventory.freeSlotCount == 0) {
            player.message("${preset.command}: make one free inventory slot first.")
            return
        }
        val defs = player.world.definitions
        if (player.equipment.occupiedSlotCount > 0) Bank.depositEquipment(player)
        // Refused by the bank (full, a loot key, BankSecurity right after combat): the swap below puts those pieces in the inventory.
        val notBanked = player.equipment.occupiedSlotCount
        // A two-handed weapon would take a listed shield off again; that shield goes with the extras instead.
        val twoHanded = preset.worn.any { (id, _) -> defs.get(ItemDef::class.java, id).let { it.equipSlot == 3 && it.equipType == 5 } }
        val worn = preset.worn.filter { (id, _) -> !(twoHanded && defs.get(ItemDef::class.java, id).equipSlot == 5) }
        val extras = preset.extras + preset.worn.filter { it !in worn }
        val failed = mutableListOf<String>()
        // Weapon before shield and ammo, so a weapon swap never undoes them.
        worn.sortedBy { (id, _) -> if (defs.get(ItemDef::class.java, id).equipSlot == 3) 0 else 1 }.forEach { (id, amount) ->
            val slot = player.inventory.add(id, amount, assureFullInsertion = true).items.firstOrNull()?.slot
            val item = slot?.let { player.inventory[it] }
            val result = if (slot == null || item == null) EquipAction.Result.NO_FREE_SPACE else EquipAction.equip(player, item, slot)
            if (result != EquipAction.Result.SUCCESS) failed += "${defs.get(ItemDef::class.java, id).name} ($result)"
        }
        var banked = 0
        extras.forEach { (id, amount) ->
            if (!player.inventory.add(id, amount, assureFullInsertion = true).hasSucceeded()) {
                if (player.bank.add(id, amount, assureFullInsertion = false).hasSucceeded()) banked++ else failed += defs.get(ItemDef::class.java, id).name
            }
        }
        player.message("<col=ff981f>${preset.title}</col> equipped. Your old gear is in the bank.")
        if (notBanked > 0) player.message("$notBanked worn item(s) could not be banked and are in your inventory.")
        if (banked > 0) player.message("$banked extra item(s) went to the bank (inventory full).")
        if (failed.isNotEmpty()) player.message("<col=ff0000>Not worn/given:</col> ${failed.joinToString(", ")}")
    }
}
