package gg.rsmod.plugins.content.skills.runecrafting

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Graphic
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Gfx
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import kotlin.math.min

/**
 * @author Triston Plummer ("Dread")
 *
 * Handles the action of crafting runes at an altar
 */
object RunecraftAction {

    /**
     * The number of ticks between playing the animation, and receiving the runes
     */
    private const val RUNECRAFT_WAIT_CYCLE = 3

    /**
     * The graphic played when crafting a rune
     */
    private val RUNECRAFT_GRAPHIC = Graphic(Gfx.CRAFT_RUNES, 100)

    /**
     * The sound effect played when crafting a const
     */
    private const val RUNECRAFT_SOUND = 2710

    /**
     * Handles the pre-crafting action (the animation, graphics, and lock)
     *
     * @param it    The queued action task
     */
    private suspend fun preCraft(it: QueueTask) {
        val player = it.player

        player.lock()

        player.animate(Anims.CRAFT_RUNES)
        player.graphic(RUNECRAFT_GRAPHIC)
        player.playSound(RUNECRAFT_SOUND)

        it.wait(RUNECRAFT_WAIT_CYCLE)

        player.unlock()
    }

    /**
     * Handles the crafting of a combination rune
     *
     * @param it    The queued action task
     * @param combo The combination rune to craft
     */
    suspend fun craftCombination(
        it: QueueTask,
        combo: CombinationRune,
    ) {
        val player = it.player
        val world = player.world

        if (!canCraftCombo(it, combo)) {
            return
        }

        preCraft(it)

        val inventory = player.inventory
        var count = min(inventory.getItemCount(Items.PURE_ESSENCE), inventory.getItemCount(combo.rune))
        if (combo.catalyst != -1) count = min(count, inventory.getItemCount(combo.catalyst))
        if (count <= 0) return

        // Lunar Magic Imbue: no talisman is needed (or consumed) while the charge is active.
        val imbued = player.attr[gg.rsmod.game.model.attr.MAGIC_IMBUE_ATTR] == true
        val removeTalismanTrans = if (imbued) null else inventory.remove(combo.talisman)

        if (imbued || removeTalismanTrans!!.hasSucceeded()) {
            val removeEssTrans = inventory.remove(item = Items.PURE_ESSENCE, amount = count)
            val removeRuneTrans = inventory.remove(item = combo.rune, amount = removeEssTrans.completed)
            if (combo.catalyst != -1) inventory.remove(item = combo.catalyst, amount = removeEssTrans.completed)

            if (removeRuneTrans.hasSucceeded()) {
                // OSRS Wiki "Mist rune" et al.: "Combinations have a 50% success rate (or 100%, if the player is wearing a binding
                // necklace)" per essence; "Binding necklace": "It has 16 uses (one use is one click on an altar)", charges stored per
                // player. (Previously a uniform 1..count roll, never zero, and the necklace never used a charge.)
                val binding = player.hasEquipped(EquipmentType.AMULET, Items.BINDING_NECKLACE)
                val runeCount = if (binding) count else combinationSuccesses(count) { world.random(1) == 0 }
                if (binding) BindingNecklace.useCharge(player)
                if (runeCount > 0) inventory.add(item = combo.id, amount = runeCount)
                // SOURCE_GAP (unchanged): experience stays per essence bound.
                player.addXp(Skills.RUNECRAFTING, count * combo.xp)
            }
        }
    }

    /** Successful combination runes from [essence] independent 50 % rolls. */
    fun combinationSuccesses(
        essence: Int,
        succeeds: () -> Boolean,
    ): Int = (0 until essence).count { succeeds() }

    /**
     * Handles the action of crafting a rune from essence
     *
     * @param it    The queued task instance
     * @param rune  The rune being crafted
     */
    suspend fun craftRune(
        it: QueueTask,
        rune: Rune,
    ) {
        val player = it.player

        if (!canCraftRune(it, rune)) {
            return
        }

        preCraft(it)

        val inventory = player.inventory
        val essence = inventory.filter { it != null }.map { it?.id!! }.first { rune.essence.contains(it) }
        val essAmount = inventory.getItemCount(essence)
        val transaction = inventory.remove(item = essence, amount = essAmount)

        val count = rune.getAmount(player.skills.getCurrentLevel(Skills.RUNECRAFTING), player, transaction.items.size)

        if (transaction.hasSucceeded()) {
            player.inventory.add(rune.id, count)
            player.addXp(Skills.RUNECRAFTING, rune.xp * essAmount)
        }
    }

    /**
     * Checks if a player can craft a specified rune
     *
     * @param it    The queued task instance
     * @param rune  The rune being crafted
     */
    private suspend fun canCraftRune(
        it: QueueTask,
        rune: Rune,
    ): Boolean {
        val player = it.player
        val def = player.world.definitions.get(ItemDef::class.java, rune.id)

        if (player.skills.getCurrentLevel(Skills.RUNECRAFTING) < rune.level) {
            it.messageBox(
                "You need a ${Skills.getSkillName(
                    player.world,
                    Skills.RUNECRAFTING,
                )} level of at least ${rune.level} to craft ${def.name.lowercase()}s.",
            )
            return false
        }

        val essence = rune.essence
        val essenceDef = player.world.definitions.get(ItemDef::class.java, essence.first())

        if (!player.inventory.filter { it != null && essence.contains(it.id) }.any()) {
            it.messageBox("You do not have any ${essenceDef.name.lowercase()} to bind.")
            return false
        }

        return true
    }

    /**
     * Checks if a player can craft a combination rune
     *
     * @param it    The queued action task
     * @param combo The combination rune to craft
     */
    private suspend fun canCraftCombo(
        it: QueueTask,
        combo: CombinationRune,
    ): Boolean {
        val player = it.player

        val comboName =
            player.world.definitions
                .get(ItemDef::class.java, combo.id)
                .name
                .lowercase()
        val runeName =
            player.world.definitions
                .get(ItemDef::class.java, combo.rune)
                .name
                .lowercase()
        val talismanName =
            player.world.definitions
                .get(ItemDef::class.java, combo.talisman)
                .name
                .lowercase()

        if (player.skills.getCurrentLevel(Skills.RUNECRAFTING) < combo.level) {
            it.messageBox(
                "You need a ${Skills.getSkillName(
                    player.world,
                    Skills.RUNECRAFTING,
                )} level of at least ${combo.level} to craft ${comboName}s.",
            )
            return false
        }

        if (!player.inventory.contains(Items.PURE_ESSENCE)) {
            player.message("You need pure essence to bind ${comboName}s.")
            return false
        }

        if (!player.inventory.contains(combo.rune)) {
            player.message("You need ${runeName}s to bind ${comboName}s.")
            return false
        }

        if (combo.catalyst != -1 && !player.inventory.contains(combo.catalyst)) {
            player.message("You need aether catalysts to bind ${comboName}s.")
            return false
        }

        if (combo.requiresImbue && player.attr[gg.rsmod.game.model.attr.MAGIC_IMBUE_ATTR] != true) {
            player.message("You need to cast Magic Imbue to bind ${comboName}s.")
            return false
        }

        if (player.attr[gg.rsmod.game.model.attr.MAGIC_IMBUE_ATTR] != true && !player.inventory.contains(combo.talisman)) {
            player.message("You need a $talismanName to bind ${comboName}s.")
            return false
        }

        return true
    }
}
