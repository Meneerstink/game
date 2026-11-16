package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.attr.GROUNDITEM_PICKUP_TRANSACTION
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.api.ext.messageBox
import gg.rsmod.game.fs.def.ItemDef
import kotlin.random.Random

/**
 * Treasure Trail lifecycle. The step belongs to the scroll; completed steps and
 * trail length belong to the player/tier, including when juggling dropped clues.
 * [tryDig]/[tryEmote] are called from the shared
 * spade-dig handler (`gardener.plugin.kts`, this engine's single-slot `on_item_option(SPADE,
 * "Dig")` binding) and the emote-tab hook (`EmotesTab.performEmote`) respectively, since neither
 * of those bindings can be duplicated - the same pattern this project already uses for Barrows'
 * mound-digging via the same shared spade handler.
 */
class ClueScrollManager(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    companion object {
        private val activeStepAttrs: Map<ClueScrollTier, AttributeKey<Int>> =
            ClueScrollTier.values().toList().associateWith { tier ->
                AttributeKey(persistenceKey = "clue_step_${tier.name.lowercase()}")
            }

        private val progressAttrs = ClueScrollTier.values().associateWith { AttributeKey<Int>(persistenceKey = "clue_progress_${it.name.lowercase()}") }
        private val lengthAttrs = ClueScrollTier.values().associateWith { AttributeKey<Int>(persistenceKey = "clue_length_${it.name.lowercase()}") }

        private val steps: Map<ClueScrollTier, MutableList<ClueStep>> =
            ClueScrollTier.values().toList().associateWith { mutableListOf<ClueStep>() }

        fun registerStep(step: ClueStep) {
            check(steps.getValue(step.tier).none { it.persistenceId == step.persistenceId }) { "Duplicate clue step key: ${step.tier} ${step.hint}" }
            steps.getValue(step.tier).add(step)
        }

        fun progress(player: Player, tier: ClueScrollTier): Int = player.attr[progressAttrs.getValue(tier)] ?: 0

        /** An uninitialised newly obtained clue resets tier progress; re-picking a read clue does not. */
        fun prepare(player: Player, item: Item, tier: ClueScrollTier) {
            val available = steps.getValue(tier)
            if (available.isEmpty()) return
            val previousKey = item.getAttr(ItemAttribute.CLUE_STEP_KEY)
            if (previousKey != null && available.any { it.persistenceId == previousKey }) return
            // A removed donor step cannot remain an unreadable permanent clue.
            // Its former list index has no meaning in the sourced tier pool.
            player.attr.remove(activeStepAttrs.getValue(tier))
            val step = ClueStepSelection.choose(available)
            item.putAttr(ItemAttribute.CLUE_STEP_KEY, step.persistenceId)
            item.removeAttr(ItemAttribute.CLUE_TASK_KEY).removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
            if (previousKey == null || player.attr[lengthAttrs.getValue(tier)] == null) {
                player.attr[progressAttrs.getValue(tier)] = 0
                player.attr[lengthAttrs.getValue(tier)] = tier.rollTrailLength(Random.Default)
            }
            player.inventory.dirty = true
        }

        fun activeStep(
            player: Player,
            tier: ClueScrollTier,
        ): ClueStep? {
            val item = ClueOwnership.carried(player, tier) ?: return null
            val id = item.getAttr(ItemAttribute.CLUE_STEP_KEY) ?: return null
            val available = steps.getValue(tier)
            return available.firstOrNull { it.persistenceId == id }
        }

        /**
         * Tries to complete an active map-clue step by digging. Returns true if a dig-type step
         * was active and completed here (so the shared spade handler knows not to fall through
         * to its own "nothing to dig" case). Every tier is checked since a player could hold
         * scrolls of more than one tier at once.
         */
        fun tryDig(player: Player): Boolean {
            if (TreasureTrailBasicSteps.dig(player)) return true
            for (tier in ClueScrollTier.values()) {
                val step = activeStep(player, tier) ?: continue
                if (step.mapInterfaceId != null && step.onAttempt(player)) {
                    complete(player, tier)
                    return true
                }
            }
            return false
        }

        /**
         * Tries to complete an active emote-clue step. Called from the emote-tab hook after an
         * emote animation is performed, passing the button component id of the emote that fired.
         */
        fun tryEmote(
            player: Player,
            emoteComponent: Int,
        ) {
            if (TreasureTrailEmotes.perform(player, emoteComponent)) return
            for (tier in ClueScrollTier.values()) {
                val step = activeStep(player, tier) as? EmoteClueStep ?: continue
                if (step.matches(player, emoteComponent)) {
                    complete(player, tier)
                    return
                }
            }
        }

        /**
         * Length is rolled once per trail, with OSRS bounds, rather than an
         * unbounded donor percentage on every step. Validate replacement before mutation.
         */
        private fun complete(player: Player, tier: ClueScrollTier) {
            advance(player, tier)
        }

        /**
         * Advance only the exact scroll a delayed NPC/puzzle interaction started
         * with. Auxiliary items (three torn parts) and the next clue/casket are
         * committed together; a full inventory or missing casket spends nothing.
         */
        fun advance(
            player: Player,
            tier: ClueScrollTier,
            expected: Item? = null,
            expectedProgress: Int? = null,
            consume: List<Int> = emptyList(),
        ): Boolean {
            val slot = player.inventory.rawItems.indexOfFirst { it != null && ClueOwnership.tier(it.id) == tier }
            if (slot < 0) return false
            val scroll = player.inventory[slot] ?: return false
            if (expected != null && scroll !== expected) return false
            if (expectedProgress != null && progress(player, tier) != expectedProgress) return false
            val completed = progress(player, tier) + 1
            val length = player.attr[lengthAttrs.getValue(tier)] ?: return false
            val staged = ItemContainer(player.inventory)
            for (id in consume) {
                if (!staged.remove(id, 1, assureFullRemoval = true).hasSucceeded()) return false
            }
            if (completed >= length) {
                if (tier.casketId < 0) return false
                staged[slot] = null
                if (!staged.add(tier.casketId, 1, assureFullInsertion = true).hasSucceeded()) return false
            } else {
                val available = steps.getValue(tier)
                if (available.isEmpty()) return false
                if (staged[slot]!!.id != tier.scrollId) staged[slot] = Item(tier.scrollId).copyAttr(staged[slot]!!)
                staged[slot]!!.putAttr(ItemAttribute.CLUE_STEP_KEY, ClueStepSelection.choose(available).persistenceId)
                staged[slot]!!.removeAttr(ItemAttribute.CLUE_TASK_KEY).removeAttr(ItemAttribute.CLUE_TASK_COMPLETE)
            }
            if (player.inventory[slot] !== scroll) return false
            for (i in 0 until staged.capacity) {
                val old = player.inventory[i]
                val next = staged[i]
                if (old?.id != next?.id || old?.amount != next?.amount || old?.attr != next?.attr) player.inventory[i] = next
            }
            if (completed >= length) {
                player.attr.remove(progressAttrs.getValue(tier))
                player.attr.remove(lengthAttrs.getValue(tier))
                player.message("You've found a casket!")
            } else {
                player.attr[progressAttrs.getValue(tier)] = completed
                player.message("You've found another clue scroll!")
            }
            return true
        }
    }

    init {
        ClueScrollTier.values().filter { it.scrollId >= 0 }.forEach { tier -> ClueOwnership.items(tier).forEach { id ->
            set_ground_item_condition(id) { ClueOwnership.mayReceive(player, id) }
            if (world.definitions.get(ItemDef::class.java, id).inventoryMenu.any { it.equals("Check steps", true) }) {
                on_item_option(id, "check steps") {
                    // Native chatbox presentation: sourced option label and actual persistent tier count.
                    player.queue { messageBox("Check steps: ${progress(player, tier)}") }
                }
            }
            on_item_option(id, "read") {
                val available = steps.getValue(tier)
                if (available.isEmpty()) {
                    player.message("Your ${tier.name.lowercase()} clue scroll trail has not been recorded yet.")
                    return@on_item_option
                }

                val item = ClueOwnership.carried(player, tier) ?: return@on_item_option
                prepare(player, item, tier)
                val step = activeStep(player, tier) ?: return@on_item_option
                if (TreasureTrailNumberChallenge.read(player, step)) return@on_item_option
                if (TreasureTrailSherlock.read(player, step)) return@on_item_option
                if (TreasureTrailMaps.read(player, step)) return@on_item_option
                val mapInterfaceId = step.mapInterfaceId
                if (mapInterfaceId != null) {
                    player.openInterface(dest = InterfaceDestination.MAIN_SCREEN_FULL, interfaceId = mapInterfaceId)
                } else {
                    ClueInterfaces.text(player, step.hint)
                }
            }
        } }
        on_global_item_pickup {
            player.attr[GROUNDITEM_PICKUP_TRANSACTION]?.get()?.items?.forEach { slot ->
                ClueOwnership.tier(slot.item.id)?.let { prepare(player, slot.item, it) }
            }
        }
    }
}
