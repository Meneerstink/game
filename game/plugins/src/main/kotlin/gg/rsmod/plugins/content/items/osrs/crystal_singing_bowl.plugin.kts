package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item

/**
 * OSRS-IMPORT singing bowl: seed singing, Bow of Faerdhinen corruption and the bow's seed-revert (owner document
 * `cRYSTAL.rtf`, 2026-09-16; full sourcing and values in [CrystalEquipment]'s "SEED SINGING" doc section).
 *
 * DELIBERATE OWNER OVERRIDE (not OSRS-authentic, explicit instruction): real OSRS only has the singing bowl as a fixed
 * Prifddinas object, never a Grand Exchange purchase. Building a fake tradeable "Singing bowl" item would mean inventing
 * a cache item/icon that has never existed in any OSRS revision, which the project's source-provenance rules forbid.
 * Instead, the real singing bowl object (already present in this cache under 50 real ids/recolours, options "Inspect",
 * "Sing-glass" and "Revert-crystal" - the cache's own text, not "Sing-crystal"; used verbatim) is placed at the Grand
 * Exchange for convenient access (`grand_exchange_hub.plugin.kts`), which satisfies the owner's accessibility intent
 * without inventing content.
 */

private val singingBowls =
    world.definitions
        .getAll(ObjectDef::class.java)
        .values
        .filterIsInstance<ObjectDef>()
        .filter { def -> def.options.any { it?.equals("Revert-crystal", ignoreCase = true) == true } }
        .map { it.id }
        .sorted()

check(singingBowls.size == 50) {
    "Expected the 50 sourced Singing bowl objects, found ${singingBowls.size}: $singingBowls"
}

fun Player.hasLevel(
    skill: Int,
    level: Int,
    boostable: Boolean,
): Boolean = (if (boostable) skills.getCurrentLevel(skill) else skills.getMaxLevel(skill)) >= level

fun itemName(id: Int): String = world.definitions.get(ItemDef::class.java, id).name

/** Self-service or Reese-assisted armour piece creation. Returns whether a piece was made. */
fun singArmour(
    player: Player,
    recipe: CrystalEquipment.CreationRecipe,
    viaReese: Boolean,
): Boolean {
    if (player.inventory.getItemCount(Items.CRYSTAL_ARMOUR_SEED) < recipe.seeds) {
        player.message("You need ${recipe.seeds} crystal armour seed${if (recipe.seeds > 1) "s" else ""} to sing a ${itemName(recipe.active).lowercase()}.")
        return false
    }
    val shardsNeeded = recipe.shards + if (viaReese) recipe.npcFeeShards else 0
    if (player.inventory.getItemCount(Items.CRYSTAL_SHARD) < shardsNeeded) {
        player.message("You need $shardsNeeded crystal shards to sing a ${itemName(recipe.active).lowercase()}${if (viaReese) " (Reese's fee included)" else ""}.")
        return false
    }
    if (!viaReese && !(player.hasLevel(Skills.SMITHING, recipe.smithing, boostable = true) && player.hasLevel(Skills.CRAFTING, recipe.crafting, boostable = true))) {
        player.message("You need level ${recipe.smithing} Smithing and ${recipe.crafting} Crafting to sing this yourself, or ask Reese to sing it for you.")
        return false
    }
    player.inventory.remove(Items.CRYSTAL_ARMOUR_SEED, recipe.seeds)
    player.inventory.remove(Items.CRYSTAL_SHARD, shardsNeeded)
    player.inventory.add(CrystalEquipment.withCharges(Item(recipe.active), recipe.startCharges))
    if (!viaReese) {
        player.addXp(Skills.SMITHING, recipe.xp)
        player.addXp(Skills.CRAFTING, recipe.xp)
    }
    player.message("You sing the crystal into a ${itemName(recipe.active).lowercase()}.")
    return true
}

fun singBow(
    player: Player,
    viaReese: Boolean,
): Boolean {
    if (!QuestStubs.songOfTheElvesCompleted()) {
        player.message("You need to have completed Song of the Elves to do that.")
        return false
    }
    if (player.inventory.getItemCount(Items.ENHANCED_CRYSTAL_WEAPON_SEED) < 1) {
        player.message("You need an enhanced crystal weapon seed to sing a bow of Faerdhinen.")
        return false
    }
    val shardsNeeded = CrystalEquipment.BowfaCreation.SHARDS + if (viaReese) CrystalEquipment.BowfaCreation.NPC_FEE_SHARDS else 0
    if (player.inventory.getItemCount(Items.CRYSTAL_SHARD) < shardsNeeded) {
        player.message("You need $shardsNeeded crystal shards to sing a bow of Faerdhinen${if (viaReese) " (Reese's fee included)" else ""}.")
        return false
    }
    if (!viaReese &&
        !(
            player.hasLevel(Skills.SMITHING, CrystalEquipment.BowfaCreation.SMITHING, boostable = true) &&
                player.hasLevel(Skills.CRAFTING, CrystalEquipment.BowfaCreation.CRAFTING, boostable = true)
        )
    ) {
        player.message(
            "You need level ${CrystalEquipment.BowfaCreation.SMITHING} Smithing and ${CrystalEquipment.BowfaCreation.CRAFTING} Crafting to sing this yourself, or ask Reese to sing it for you.",
        )
        return false
    }
    player.inventory.remove(Items.ENHANCED_CRYSTAL_WEAPON_SEED, 1)
    player.inventory.remove(Items.CRYSTAL_SHARD, shardsNeeded)
    player.inventory.add(CrystalEquipment.withCharges(Item(Items.BOW_OF_FAERDHINEN), CrystalEquipment.BowfaCreation.START_CHARGES))
    if (!viaReese) {
        player.addXp(Skills.SMITHING, CrystalEquipment.BowfaCreation.XP)
        player.addXp(Skills.CRAFTING, CrystalEquipment.BowfaCreation.XP)
    }
    player.message("You sing the crystal into a bow of Faerdhinen.")
    return true
}

fun corruptBow(
    player: Player,
    viaReese: Boolean,
): Boolean {
    if (player.inventory.getItemCount(Items.BOW_OF_FAERDHINEN) < 1) {
        player.message("You need a charged bow of Faerdhinen to corrupt it.")
        return false
    }
    val shardsNeeded = CrystalEquipment.BowfaCreation.CORRUPT_SHARDS + if (viaReese) CrystalEquipment.BowfaCreation.CORRUPT_NPC_FEE_SHARDS else 0
    if (player.inventory.getItemCount(Items.CRYSTAL_SHARD) < shardsNeeded) {
        player.message("You need $shardsNeeded crystal shards to corrupt the bow${if (viaReese) " (Reese's fee included)" else ""}.")
        return false
    }
    if (!viaReese &&
        !(
            player.hasLevel(Skills.SMITHING, CrystalEquipment.BowfaCreation.SMITHING, boostable = true) &&
                player.hasLevel(Skills.CRAFTING, CrystalEquipment.BowfaCreation.CRAFTING, boostable = true)
        )
    ) {
        player.message(
            "You need level ${CrystalEquipment.BowfaCreation.SMITHING} Smithing and ${CrystalEquipment.BowfaCreation.CRAFTING} Crafting to corrupt this yourself, or ask Reese to do it for you.",
        )
        return false
    }
    if (!player.inventory.remove(Items.BOW_OF_FAERDHINEN, 1).hasSucceeded()) return false
    player.inventory.remove(Items.CRYSTAL_SHARD, shardsNeeded)
    player.inventory.add(Items.BOW_OF_FAERDHINEN_C, 1)
    player.message("The singing bowl corrupts your bow of Faerdhinen. It will now stay charged permanently.")
    return true
}

fun revertBowToSeed(player: Player): Boolean {
    if (player.inventory.getItemCount(Items.BOW_OF_FAERDHINEN_INACTIVE) < 1) {
        player.message("You need an inactive bow of Faerdhinen to revert it back into a seed.")
        return false
    }
    if (player.inventory.getItemCount(Items.CRYSTAL_SHARD) < CrystalEquipment.BowfaCreation.REVERT_SHARDS) {
        player.message("You need ${CrystalEquipment.BowfaCreation.REVERT_SHARDS} crystal shards to revert the bow back into a seed.")
        return false
    }
    if (!player.inventory.remove(Items.BOW_OF_FAERDHINEN_INACTIVE, 1).hasSucceeded()) return false
    player.inventory.remove(Items.CRYSTAL_SHARD, CrystalEquipment.BowfaCreation.REVERT_SHARDS)
    player.inventory.add(Items.ENHANCED_CRYSTAL_WEAPON_SEED, 1)
    player.message("You revert the bow back into an enhanced crystal weapon seed.")
    return true
}

/** "crystal items need to give a warning when your try to revert" (owner instruction, `cRYSTAL.rtf`). */
suspend fun QueueTask.confirmRevert(action: String): Boolean =
    options("Yes, revert it.", "No, cancel.", title = "Revert $action? Any crystal shard charges are lost.") == 1

// Item-on-bowl: the real OSRS singing-bowl creation flow.
singingBowls.forEach { bowl ->
    on_item_on_obj(item = Items.CRYSTAL_ARMOUR_SEED, obj = bowl) {
        player.queue {
            when (options("Crystal helm.", "Crystal legs.", "Crystal body.", title = "What would you like to sing?")) {
                1 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_HELM), viaReese = false)
                2 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_LEGS), viaReese = false)
                3 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_BODY), viaReese = false)
            }
        }
    }

    on_item_on_obj(item = Items.ENHANCED_CRYSTAL_WEAPON_SEED, obj = bowl) {
        singBow(player, viaReese = false)
    }

    on_item_on_obj(item = Items.BOW_OF_FAERDHINEN, obj = bowl) {
        player.queue {
            if (confirmRevert("your bow of Faerdhinen into a corrupted (permanent) bow, consuming ${CrystalEquipment.BowfaCreation.CORRUPT_SHARDS} shards")) {
                corruptBow(player, viaReese = false)
            }
        }
    }

    on_item_on_obj(item = Items.BOW_OF_FAERDHINEN_INACTIVE, obj = bowl) {
        player.queue {
            if (confirmRevert("your inactive bow of Faerdhinen back into an enhanced crystal weapon seed")) {
                revertBowToSeed(player)
            }
        }
    }

    // Bare object options (cache text, used verbatim): a guided menu for players who click the bowl directly.
    on_obj_option(obj = bowl, option = "Sing-glass") {
        player.queue {
            when (options("Crystal helm.", "Crystal legs.", "Crystal body.", "Bow of Faerdhinen.", title = "What would you like to sing?")) {
                1 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_HELM), viaReese = false)
                2 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_LEGS), viaReese = false)
                3 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_BODY), viaReese = false)
                4 -> singBow(player, viaReese = false)
            }
        }
    }

    on_obj_option(obj = bowl, option = "Revert-crystal") {
        player.queue {
            if (confirmRevert("your inactive bow of Faerdhinen back into an enhanced crystal weapon seed")) {
                revertBowToSeed(player)
            }
        }
    }
}

// Reese: no level requirement, extra shard fee, no experience (owner document; Conwenna is absent from this cache).
on_npc_option(Npcs.REESE, "talk-to") {
    player.queue {
        chatNpc("I can sing crystal for you, for a few extra shards.", npc = Npcs.REESE)
        when (
            options(
                "Crystal helm.",
                "Crystal legs.",
                "Crystal body.",
                "Bow of Faerdhinen.",
                "Corrupt my bow of Faerdhinen.",
                title = "What would you like Reese to sing?",
            )
        ) {
            1 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_HELM), viaReese = true)
            2 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_LEGS), viaReese = true)
            3 -> singArmour(player, CrystalEquipment.ARMOUR_CREATION.getValue(Items.CRYSTAL_BODY), viaReese = true)
            4 -> singBow(player, viaReese = true)
            5 -> corruptBow(player, viaReese = true)
        }
    }
}
