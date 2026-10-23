package gg.rsmod.plugins.content.mechanics.pvp

import gg.rsmod.game.model.LockState
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.DEATH_FLAG
import gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER
import gg.rsmod.game.model.timer.TELEBLOCK_TIMER
import gg.rsmod.game.model.timer.TELEPORT_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.plugins.api.ext.getWildernessLevel
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bug search 2026-10-02 (ROOT_CAUSES class 21): every route a player reaches without walking to it goes through
 * [RemoteAccess]. `dungeon` teleported out of deep Wilderness through Tele Block mid-fight; `duel`/`duelaccept`, Pest
 * Control's start, Practice PvP's match start and `clanwarenter` moved players out of any death zone; the Deadman Store
 * and the casino opened in the middle of a fight.
 */
class RemoteAccessTests {
    private val varrock = Tile(3212, 3428, 0) // guarded city: safe
    private val edgeville = Tile(3093, 3493, 0) // Deadman death zone

    private fun player(
        tile: Tile = varrock,
        skulled: Boolean = false,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap().also { if (skulled) it[SKULL_ICON_DURATION_TIMER] = PvpSkull.SKULL_DURATION_CYCLES }
        every { player.tile } returns tile
        every { player.lock } returns LockState.NONE
        every { player.isLocked() } returns false
        every { player.isDead() } returns false
        val world = mockk<World>(relaxed = true)
        every { world.npcs } returns PawnList(arrayOfNulls<Npc>(2))
        every { player.world } returns world
        return player
    }

    @Test
    fun `a player standing in a safe zone out of combat may use remote services and be moved`() {
        val p = player()
        assertNull(RemoteAccess.refusal(p, "use the store"))
        assertNull(RemoteAccess.moveRefusal(p))
        assertNull(RemoteAccess.escapeRefusal(p))
    }

    @Test
    fun `outside the guarded cities nothing remote works`() {
        val p = player(edgeville)
        assertEquals("You can only use the store from a safe zone.", RemoteAccess.refusal(p, "use the store"))
        assertEquals("You can only be taken there from a safe zone.", RemoteAccess.moveRefusal(p))
    }

    @Test
    fun `combat, dying and locks refuse even in a safe zone`() {
        val fighting = player()
        fighting.timers[ACTIVE_COMBAT_TIMER] = 10
        assertEquals("You can't play casino games while in combat.", RemoteAccess.refusal(fighting, "play casino games"))

        val dying = player()
        dying.attr[DEATH_FLAG] = true
        assertEquals(RemoteAccess.NOT_NOW, RemoteAccess.refusal(dying))

        val locked = player()
        every { locked.isLocked() } returns true
        assertEquals(RemoteAccess.NOT_NOW, RemoteAccess.refusal(locked))
    }

    @Test
    fun `a server-made move obeys Tele Block, the skull and recent hits`() {
        val blocked = player()
        blocked.timers[TELEBLOCK_TIMER] = 100
        assertEquals("A magical force has stopped you from teleporting.", RemoteAccess.moveRefusal(blocked))

        assertEquals("You can't do that while you are skulled.", RemoteAccess.escapeRefusal(player(skulled = true)))

        val hit = player()
        hit.timers[TELEPORT_COMBAT_TIMER] = 12
        hit.attr[LAST_HIT_BY_ATTR] = WeakReference(mockk<Player>(relaxed = true))
        assertEquals(DeadmanTimerGate.blockedMessage(hit), RemoteAccess.escapeRefusal(hit))
    }

    @Test
    fun `nobody is moved out of deep Wilderness`() {
        val deep = Tile(3100, 3760, 0)
        assertTrue(deep.getWildernessLevel() > 20)
        assertEquals("A mysterious force blocks your teleport spell!", RemoteAccess.escapeRefusal(player(deep)))
    }

    /**
     * Census over the whole set: every player-available command (no power required) in every plugin script whose own
     * block moves a player must go through the shared gate. A new command with a raw `moveTo`/`teleportTo` fails here.
     */
    @Test
    fun `every player command that moves a player goes through the shared gate`() {
        val command = Regex("""^on_command\("([a-z0-9_]+)"\s*\)\s*\{""", RegexOption.MULTILINE)
        val moves = listOf(".moveTo(", ".teleportTo(", " moveTo(", " teleportTo(", ".teleport(")
        val offenders = mutableListOf<String>()
        content.walkTopDown().filter { it.name.endsWith(".plugin.kts") }.forEach { file ->
            val text = file.readText()
            command.findAll(text).forEach { match ->
                val start = match.range.first
                val next = Regex("""\n[^\s}]""").find(text, match.range.last)?.range?.first ?: text.length
                val block = text.substring(start, next)
                if (moves.any { block.contains(it) } && !block.contains("RemoteAccess") && !block.contains("canTeleport(")) {
                    offenders += "${file.relativeTo(content)}: ${match.groupValues[1]}"
                }
            }
        }
        assertTrue(offenders.isEmpty(), "player commands moving a player without RemoteAccess/canTeleport: $offenders")
    }

    @Test
    fun `routes that move a player later, or open a remote service, use the shared gate`() {
        // DGN 2026-10-21: `::dungeon` and the Daemonheim floor loop are removed with Dungeoneering (DungeoneeringRemovedTests).
        read("activity/pest_control/PestControlHandler.kt").let {
            assertTrue(it.contains("RemoteAccess.refuse(player"), "boarding")
            assertTrue(it.contains("RemoteAccess.moveRefusal(p"), "the game start re-checks every boarder")
        }
        read("mechanics/practicepvp/PracticePvp.kt").let {
            assertTrue(it.contains("RemoteAccess.refuse(player"), "queueing")
            assertTrue(it.contains("RemoteAccess.moveRefusal(next"), "the waiting player is only pulled when allowed")
        }
        assertTrue(read("mechanics/clan/clan_wars_full.plugin.kts").contains("RemoteAccess.travel(player"))
        assertTrue(read("activity/duel_arena/DuelArenaRules.kt").contains("RemoteAccess.escapeRefusal(player)"))
        assertEquals(2, Regex("""RemoteAccess\.refuse\(player""").findAll(read("mechanics/store/StoreUi.kt")).count(), "store open and buy")
        assertTrue(read("mechanics/exchange/grand_exchange.plugin.kts").contains("RemoteAccess.refusal(player"), "GE commands share the rule")
        assertTrue(read("activity/casino/Casino.kt").contains("RemoteAccess.refuse(player"), "casino")
    }

    private val content = File("src/main/kotlin/gg/rsmod/plugins/content")

    private fun read(path: String): String = File(content, path).readText()
}
