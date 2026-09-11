package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.Server
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.player
import kotlin.random.Random

/**
 * Treasure Trail backbone: wires "read" on each tier's real 667 scroll id
 * ([ClueScrollTier]) to a persistent per-player, per-tier active step index, and
 * dispatches to whichever [ClueStep]s have been registered for that tier via
 * [registerStep]. Also owns step-completion: [tryDig]/[tryEmote] are called from the shared
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

        private val steps: Map<ClueScrollTier, MutableList<ClueStep>> =
            ClueScrollTier.values().toList().associateWith { mutableListOf<ClueStep>() }

        fun registerStep(step: ClueStep) {
            steps.getValue(step.tier).add(step)
        }

        private fun activeStep(
            player: Player,
            tier: ClueScrollTier,
        ): ClueStep? {
            if (!player.inventory.contains(tier.scrollId)) return null
            val index = player.attr[activeStepAttrs.getValue(tier)] ?: return null
            val available = steps.getValue(tier)
            return available.getOrNull(index)
        }

        /**
         * Tries to complete an active map-clue step by digging. Returns true if a dig-type step
         * was active and completed here (so the shared spade handler knows not to fall through
         * to its own "nothing to dig" case). Every tier is checked since a player could hold
         * scrolls of more than one tier at once.
         */
        fun tryDig(player: Player): Boolean {
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
            for (tier in ClueScrollTier.values()) {
                val step = activeStep(player, tier) as? EmoteClueStep ?: continue
                if (step.matches(player, emoteComponent)) {
                    complete(player, tier)
                    return
                }
            }
        }

        /**
         * Resolves a completed step: clears the active-step index (so the next read rolls a
         * fresh one) and, per [ClueScrollTier.completionChance], either grants the tier's real
         * casket item or hands out another scroll of the same tier - the same "another clue"
         * chain Novite's `ClueScrollManager.onSuccess` implements.
         */
        private fun complete(
            player: Player,
            tier: ClueScrollTier,
        ) {
            player.attr.remove(activeStepAttrs.getValue(tier))
            if (!player.inventory.contains(tier.scrollId)) return
            player.inventory.remove(tier.scrollId, 1)
            if (Random.nextInt(100) < tier.completionChance) {
                player.inventory.add(tier.casketId, 1)
                player.message("You've found a casket!")
            } else {
                player.inventory.add(tier.scrollId, 1)
                player.message("You've found another clue scroll!")
            }
        }
    }

    init {
        ClueScrollTier.values().toList().forEach { tier ->
            on_item_option(tier.scrollId, "read") {
                val available = steps.getValue(tier)
                if (available.isEmpty()) {
                    player.message("Your ${tier.name.lowercase()} clue scroll trail has not been recorded yet.")
                    return@on_item_option
                }

                val attr = activeStepAttrs.getValue(tier)
                var index = player.attr[attr]
                if (index == null || index !in available.indices) {
                    index = Random.nextInt(available.size)
                    player.attr[attr] = index
                }

                val step = available[index]
                val mapInterfaceId = step.mapInterfaceId
                if (mapInterfaceId != null) {
                    player.openInterface(dest = InterfaceDestination.MAIN_SCREEN_FULL, interfaceId = mapInterfaceId)
                } else {
                    player.message(step.hint)
                }
            }
        }
    }
}
