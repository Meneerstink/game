package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.*
import kotlin.random.Random

/** Uri belongs to the exact held clue. A medium clue needs first emote, second emote, then Talk-to. */
object TreasureTrailEmotes {
    class Session(val player: Player, val step: TreasureTrailSourceStep, val contract: ClueStepContract, val uri: Npc) {
        var secondEmote = step.source.secondEmote == null
            internal set
        var settled = false
            internal set
    }
    private val sessions = HashMap<Player, Session>()
    fun session(player: Player): Session? = sessions[player]
    fun available(world: gg.rsmod.game.model.World, row: TreasureTrailStepCatalogue.Step): Boolean =
        row.action == TreasureTrailStepCatalogue.Action.EMOTE && row.challenge == TreasureTrailStepCatalogue.Challenge.URI &&
            row.equipment.all { group -> group.any { it.local() != null } } &&
            TreasureTrailUri.current.actor(world, row.tier) != null

    fun attire(player: Player, row: TreasureTrailStepCatalogue.Step): Boolean =
        row.acceptsEquipment(player.equipment.map { it?.id })

    fun perform(player: Player, component: Int, spawn: (Player, TreasureTrailSourceStep) -> Npc? = ::spawn): Boolean {
        val previous = sessions[player]
        if (previous != null && !previous.contract.valid(player)) remove(player)
        val current = sessions[player]
        if (current != null && !current.settled && current.contract.valid(player) &&
            current.step.source.at(player.tile) && attire(player, current.step.source)) {
            if (component == current.step.source.secondEmote) {
                current.secondEmote = true
                return true
            }
            if (component == current.step.source.firstEmote) return true // Never post multiple Uris.
        }
        for (tier in ClueScrollTier.values()) {
            val step = ClueScrollManager.activeStep(player, tier) as? TreasureTrailSourceStep ?: continue
            val row = step.source
            if (row.action != TreasureTrailStepCatalogue.Action.EMOTE || row.challenge != TreasureTrailStepCatalogue.Challenge.URI ||
                row.firstEmote != component || !row.at(player.tile) || !attire(player, row)) continue
            val contract = ClueStepContract.capture(player, step) ?: continue
            val npc = spawn(player, step) ?: return true
            remove(player)
            sessions[player] = Session(player, step, contract, npc)
            return true
        }
        return false
    }

    /** Talk-to rechecks attire; changing into combat gear or replacing/banking the scroll never advances. */
    fun finish(player: Player, uri: Npc): Boolean {
        val session = sessions[player] ?: return false
        if (session.uri !== uri || session.settled || !session.secondEmote ||
            !session.contract.valid(player) || !attire(player, session.step.source)) return false
        if (!session.contract.advance(player)) return false
        session.settled = true
        return true
    }

    fun talk(player: Player, uri: Npc, option: Int): Boolean {
        val session = sessions.values.firstOrNull { it.uri === uri } ?: return false
        val def = player.world.definitions.get(NpcDefClass, uri.id)
        if (!def.options.getOrNull(option-1).equals("Talk-to", true)) return false
        val dialogue = TreasureTrailUri.current
        // OSRS grants the clue when speaking; leaving the later dialogue must not award it twice.
        if (!finish(player, uri)) {
            player.queue { chatNpc(dialogue.refusal, npc=uri.id, wrap=true) }
            return true
        }
        val phrase = dialogue.phrases.random(Random.Default)
        player.queue {
            chatNpc(phrase, npc=uri.id, wrap=true)
            chatPlayer(dialogue.playerReply, wrap=true)
            if (sessions[player] === session) remove(player)
        }
        return true
    }

    fun cycle() {
        // No invented numeric lifetime. Always clear stale/finished actors even if dialogue was cancelled.
        sessions.values.toList().filter { !it.contract.valid(it.player) }.forEach { remove(it.player) }
    }
    fun remove(player: Player) { sessions.remove(player)?.let { it.uri.world.remove(it.uri) } }

    private val NpcDefClass = gg.rsmod.game.fs.def.NpcDef::class.java
    private fun spawn(player: Player, step: TreasureTrailSourceStep): Npc? {
        val id = TreasureTrailUri.current.actor(player.world, step.tier) ?: return null
        val direction = Direction.values().firstOrNull { it != Direction.NONE && player.world.collision.canStep(player.tile, it) }
            ?: run { println("PROBLEM CLUE Uri has no reachable adjacent spawn at ${player.tile}"); return null }
        // The Wiki explicitly fixed Uri appearing underneath the player; use a reachable adjacent square.
        val npc = Npc(id, player.tile.step(direction), player.world)
        npc.respawns = false
        npc.facePawn(player)
        if (!player.world.spawn(npc)) {
            println("PROBLEM CLUE Uri spawn refused at ${npc.tile}; clue retained")
            return null
        }
        return npc
    }
}
