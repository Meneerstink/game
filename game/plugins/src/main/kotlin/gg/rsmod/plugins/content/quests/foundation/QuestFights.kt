package gg.rsmod.plugins.content.quests.foundation

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.collision.Landing
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import java.lang.ref.WeakReference

/**
 * The fight steps of the short quests (owner 2026-09-26: "add fights to the short quest"). The step's npc sends the
 * player in; the foe is a real cache npc with its normal combat definition and drops, spawned for this player alone
 * ([Npc.owner]: only the owner sees and fights it, like the Restless Ghost's skeleton warlock) on the step's fight tile.
 * Killing it completes the step. It disappears when the player leaves (20 tiles), logs out, dies or the stage moves on;
 * speaking to the step's npc again brings it back. Deaths follow the normal death rules of the place.
 */
object QuestFights {
    private val FOE_QUEST = AttributeKey<Int>()
    private val FOE_STAGE = AttributeKey<Int>()
    private val ACTIVE_FOE = AttributeKey<WeakReference<Npc>>()

    /** How close the player has to be to the fight tile for the foe to appear. */
    const val REACH = 15

    /** Beyond this distance from the foe the fight is abandoned. */
    const val LEASH = 20

    /** How many walkable tiles round the player a foe may appear on (a radius of about 12 on open ground). */
    const val NEAR_WALK = 600

    fun active(player: Player): Npc? = player.attr[ACTIVE_FOE]?.get()?.takeIf { it.isSpawned() && it.isAlive() }

    /** Spawns the foe of [quest]'s fight step [stage] for [player]; false when it cannot (wrong place or no fight). */
    fun start(player: Player, quest: ShortQuest, stage: Int): Boolean {
        val fight = quest.steps.getOrNull(stage - 1)?.fight ?: return false
        if (active(player) != null) return true
        if (player.tile.height != fight.tile.height || !player.tile.isWithinRadius(fight.tile, REACH)) {
            player.message("${fight.subject} waits for you at ${quest.steps[stage - 1].place ?: "the quest location"}.")
            return false
        }
        // Still landing (teleport lock): the foe's first attack would be dropped and it would stand idle; the helper tick
        // starts the fight once the player is free (conform quests 2026-10-07, Song of the Elves armed Mourner).
        if (player.isLocked()) return false
        val world = player.world
        val foe = Npc(player, fight.npc, spawnTile(player, fight.npc, fight.tile), world)
        foe.respawnOverride = false
        foe.attr[FOE_QUEST] = quest.slot
        foe.attr[FOE_STAGE] = stage
        if (!world.spawn(foe)) return false
        player.attr[ACTIVE_FOE] = WeakReference(foe)
        foe.forceChat(fight.shout)
        player.message("<col=ef1020>${fight.subject} (level ${fight.level}) attacks! Dying here follows the normal death rules of this area.")
        foe.attack(player)
        leash(player, quest, stage, foe, fight)
        return true
    }

    /** The foe leaves when its player goes (20 tiles), logs out, dies or the stage moves on; it comes back on return. */
    private fun leash(player: Player, quest: ShortQuest, stage: Int, foe: Npc, fight: QuestFight) {
        val world = player.world
        // A world task, never foe.queue: an npc runs only the head of its own queue, so this endless watcher sat in front of the
        // foe's combat loop and the foe never followed or hit (owner 2026-09-27: "dharok the wretched volgt niet").
        world.queue {
            while (foe.isSpawned() && foe.isAlive()) {
                wait(5)
                val gone = !player.isOnline || player.isDead() || !player.tile.isWithinRadius(foe.tile, LEASH) || quest.stage(player) != stage
                if (gone && foe.isSpawned() && foe.isAlive()) {
                    world.remove(foe)
                    player.attr.remove(ACTIVE_FOE)
                    if (player.isOnline && !player.isDead()) player.message("${fight.subject} has fled. Return to ${quest.steps[stage - 1].place ?: "the quest location"} to face it again.")
                }
            }
        }
    }

    /** A kill of a quest foe by its owner completes the step: its items, then the next stage (or the quest). */
    fun onKilled(killer: Player, npc: Npc) {
        val slot = npc.attr[FOE_QUEST] ?: return
        val stage = npc.attr[FOE_STAGE] ?: return
        if (npc.owner != killer) return
        val quest = FoundationQuests.SHORT.firstOrNull { it.slot == slot } ?: return
        if (quest.stage(killer) != stage) return
        val fight = quest.steps[stage - 1].fight ?: return
        // A foe with a second form (Damis) or waves (Knight Waves): the next one comes, only the last kill counts.
        val next = fight.next(npc.id)
        if (next != null) {
            val (second, shout) = next
            val world = killer.world
            val foe = Npc(killer, second, spawnTile(killer, second, if (fight.chainAtTile) fight.tile else npc.tile), world)
            foe.respawnOverride = false
            foe.attr[FOE_QUEST] = slot
            foe.attr[FOE_STAGE] = stage
            if (world.spawn(foe)) {
                killer.attr[ACTIVE_FOE] = WeakReference(foe)
                if (shout.isNotEmpty()) foe.forceChat(shout)
                foe.attack(killer)
                leash(killer, quest, stage, foe, fight)
                return
            }
        }
        killer.attr.remove(ACTIVE_FOE)
        FoundationQuests.completeStep(killer, quest, stage)
    }

    /**
     * Where a foe of [player] appears (conform quests 2026-10-07): [wanted] when the player can walk to it and the foe would
     * not stand on the player, else the nearest such tile. King's Ransom's third knight appeared on the fight tile the player
     * stood on; stacked, player and knight stepped out and back every tick and never came orthogonally next to each other.
     * Song of the Elves' armed Mourner stood behind a wall of West Ardougne: "I can't reach that!".
     */
    fun spawnTile(player: Player, npcId: Int, wanted: Tile): Tile {
        val world = player.world
        val size = world.definitions.getNullable(NpcDef::class.java, npcId)?.size?.coerceAtLeast(1) ?: 1
        // A short walk only (breadth-first, nearest tiles first): with 2048 tiles a fight tile on the far side of a wall counted as
        // "reachable" round the building, and Song of the Elves' elf warrior stood outside Lletya's hall with no route to the
        // player inside (quests run 2026-10-10). Within NEAR_WALK tiles the foe always walks straight up to the player.
        val reachable = Landing.pocket(world.collision, player.tile, NEAR_WALK)
        fun fits(t: Tile): Boolean {
            for (dx in 0 until size) for (dz in 0 until size) {
                val f = Tile(t.x + dx, t.z + dz, t.height)
                if (f == player.tile || f !in reachable) return false
            }
            return true
        }
        if (fits(wanted)) return wanted
        return reachable.filter { it.height == wanted.height && fits(it) }.minByOrNull { it.getDistance(wanted) } ?: wanted
    }

    private fun questNpcName(quest: ShortQuest, stage: Int): String =
        FoundationQuests.NPC_NAMES[quest.steps[stage - 1].npc] ?: "the quest's contact"
}
