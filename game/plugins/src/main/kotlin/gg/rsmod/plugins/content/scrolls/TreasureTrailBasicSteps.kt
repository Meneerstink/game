package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*

/** Source subtypes share the exact held-clue contract, never account-global pending answers. */
object TreasureTrailBasicSteps {
    /** Only implemented subtypes with their actual assets enter the random pool. */
    fun available(playerWorld: gg.rsmod.game.model.World, row: TreasureTrailStepCatalogue.Step): Boolean {
        if (row.tornPart || row.kind == "MAP") return false // dedicated handlers/assets required
        return when (row.action) {
            TreasureTrailStepCatalogue.Action.DIG -> row.challenge == TreasureTrailStepCatalogue.Challenge.NONE
            TreasureTrailStepCatalogue.Action.TALK -> {
                val npc = row.existingNpcs.any { id ->
                    playerWorld.definitions.getNullable(NpcDef::class.java, id)?.let { definition ->
                        definition.name.equals(row.npcName, true) && definition.options.any { it.equals("Talk-to", true) }
                    } == true
                }
                npc && when (row.challenge) {
                    TreasureTrailStepCatalogue.Challenge.NONE -> row.npcName in setOf("Vannaka", "Captain Tobias", "Luthas")
                    TreasureTrailStepCatalogue.Challenge.NUMBER -> row.npcName in TreasureTrailNumberDialogue.lines &&
                        TreasureTrailNumberChallenge.sources[row.tier]?.let(ClueItems::local)?.let {
                            playerWorld.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, it)
                        } != null
                    else -> false
                }
            }
            TreasureTrailStepCatalogue.Action.SEARCH -> TreasureTrailSearchTargets.current.singleOrNull { it.key == row.key }?.let {
                playerWorld.definitions.getNullable(gg.rsmod.game.fs.def.ObjectDef::class.java, it.localId)?.let { definition ->
                    definition.name == it.name && definition.options.any { option -> option.equals("Search", true) }
                }
            } == true
            TreasureTrailStepCatalogue.Action.KILL -> row.existingNpcs.isNotEmpty()
            else -> false
        }
    }
    /** Coordinate tools are no longer required: pinned Wiki Coordinates change history. */
    fun dig(player: Player): Boolean {
        for (tier in ClueScrollTier.values()) {
            when (val active = ClueScrollManager.activeStep(player, tier)) {
                is TreasureTrailThreePartStep -> {
                    val part = active.digPart(player) ?: continue
                    TreasureTrailTornParts.obtain(player, active, part)
                    return true
                }
                is TreasureTrailSourceStep -> {
                    val row = active.source
                    if (row.action != TreasureTrailStepCatalogue.Action.DIG || !row.at(player.tile)) continue
                    if (row.challenge !in setOf(TreasureTrailStepCatalogue.Challenge.NONE)) continue
                    return ClueStepContract.capture(player, active)?.advance(player) ?: false
                }
            }
        }
        return false
    }

    fun matches(npc: Npc, row: TreasureTrailStepCatalogue.Step): Boolean {
        if (npc.id !in row.existingNpcs) return false
        val definition = npc.world.definitions.get(NpcDef::class.java, npc.id)
        return definition.name.equals(row.npcName, ignoreCase = true)
    }

    fun killed(player: Player, npc: Npc) {
        for (tier in ClueScrollTier.values()) {
            val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: continue
            if (step.source.action == TreasureTrailStepCatalogue.Action.KILL && matches(npc, step.source))
                ClueStepContract.capture(player, step)?.advance(player)
        }
    }

    /** Only the real Talk-to option claims the interaction; shops/quests remain ordinary otherwise. */
    fun talk(player: Player, npc: Npc, option: Int): Boolean {
        val definition = npc.world.definitions.get(NpcDef::class.java, npc.id)
        if (!definition.options.getOrNull(option - 1).equals("Talk-to", true)) return false
        for (tier in ClueScrollTier.values()) {
            val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: continue
            val row = step.source
            if (row.action != TreasureTrailStepCatalogue.Action.TALK || !matches(npc, row)) continue
            if (row.challenge == TreasureTrailStepCatalogue.Challenge.NUMBER) {
                return TreasureTrailNumberDialogue.talk(player, npc, step)
            }
            if (row.challenge != TreasureTrailStepCatalogue.Challenge.NONE || row.tornPart) continue
            val line = when (row.npcName) {
                // Exact pinned Transcript pages; no fabricated quest/shop override dialogue.
                "Vannaka" -> "Here, have this."
                "Captain Tobias" -> "Well done, matey! Here you go!"
                "Luthas" -> "Congratulations."
                else -> return false
            }
            val contract = ClueStepContract.capture(player, step) ?: return false
            player.queue {
                chatNpc(line, npc = npc.id, wrap = true)
                if (!terminated) contract.advance(player)
            }
            return true
        }
        return false
    }
}
