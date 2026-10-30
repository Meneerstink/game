package gg.rsmod.plugins.content.areas.kandarin.castlewars

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ContainerStackType
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * K07 N442 (owner 2026-10-30: "Castle Wars start niet ... moet starten bij 1 tegen 1", "de gui wanneer die start ... dan een
 * countdown") and N441 ("na portal in + teleport of uitloggen houdt de speler de teamkleur en de Castle Wars hood/cape").
 */
class CastleWarsLobbyTests {
    private val definitions =
        mockk<DefinitionSet>(relaxed = true).also {
            every { it.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { it.getNullable(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { it.get(gg.rsmod.game.fs.def.VarbitDef::class.java, any()) } answers { gg.rsmod.game.fs.def.VarbitDef(secondArg()) }
        }
    private val world = mockk<World>(relaxed = true).also { every { it.definitions } returns definitions }

    private fun player(name: String): Player {
        val p = mockk<Player>(relaxed = true)
        var tile = CastleWarsRound.LOBBY
        every { p.world } returns world
        every { p.username } returns name
        every { p.isOnline } returns true
        every { p.attr } returns AttributeMap()
        every { p.inventory } returns ItemContainer(definitions, 28, ContainerStackType.NORMAL)
        every { p.equipment } returns ItemContainer(definitions, 14, ContainerStackType.NORMAL)
        every { p.tile } answers { tile }
        every { p.moveTo(any<Tile>()) } answers { tile = firstArg() }
        return p
    }

    private fun Player.teleportedTo(to: Tile) {
        every { tile } returns to
    }

    @BeforeTest
    @AfterTest
    fun clean() {
        CastleWarsTeam.values().forEach {
            CastleWarsRound.waiting[it]!!.clear()
            CastleWarsRound.playing[it]!!.clear()
        }
        CastleWarsRound.reset()
    }

    @Test
    fun `one against one counts down and starts, and the countdown stops when a team empties`() {
        val sara = player("sara")
        val zam = player("zam")
        CastleWarsHandler.joinPortal(sara, CastleWarsTeam.SARADOMIN)
        assertFalse(CastleWarsRound.active, "one team alone does not count down")
        CastleWarsHandler.joinPortal(zam, CastleWarsTeam.ZAMORAK)
        assertTrue(CastleWarsRound.active, "1 against 1 counts down")
        assertEquals(CastleWarsRound.LOBBY_MINUTES, CastleWarsRound.minutesLeft)

        CastleWarsHandler.removeWaitingPlayer(zam)
        assertFalse(CastleWarsRound.active, "the countdown stops when a team has nobody")
        assertNull(zam.equipment[EquipmentType.CAPE.id], "the colours come off at the exit portal")

        CastleWarsHandler.joinPortal(zam, CastleWarsTeam.ZAMORAK)
        repeat(CastleWarsRound.LOBBY_MINUTES) { CastleWarsHandler.tickMinute(world) }
        assertTrue(CastleWarsRound.matchRunning, "the game starts when the countdown ends")
        assertEquals(CastleWarsRound.MATCH_MINUTES, CastleWarsRound.minutesLeft)
        assertEquals<List<Player>>(listOf(sara), CastleWarsRound.playing[CastleWarsTeam.SARADOMIN]!!)
        assertEquals<List<Player>>(listOf(zam), CastleWarsRound.playing[CastleWarsTeam.ZAMORAK]!!)
        assertEquals(CastleWarsTeam.SARADOMIN.baseTile, sara.tile)
    }

    @Test
    fun `a waiting player who teleports out loses the colours where he is`() {
        val sara = player("sara")
        CastleWarsHandler.joinPortal(sara, CastleWarsTeam.SARADOMIN)
        assertEquals(Items.SARADOMIN_TEAM_HOOD, sara.equipment[EquipmentType.HEAD.id]?.id)
        val home = Tile(3164, 3494, 0)
        sara.teleportedTo(home)
        CastleWarsHandler.sweep()
        assertNull(sara.attr[CW_TEAM])
        assertNull(sara.equipment[EquipmentType.HEAD.id], "hood off")
        assertNull(sara.equipment[EquipmentType.CAPE.id], "cape off")
        assertTrue(CastleWarsRound.waiting[CastleWarsTeam.SARADOMIN]!!.isEmpty())
    }

    @Test
    fun `a game whose team leaves ends at once without tickets`() {
        val sara = player("sara")
        val zam = player("zam")
        CastleWarsHandler.joinPortal(sara, CastleWarsTeam.SARADOMIN)
        CastleWarsHandler.joinPortal(zam, CastleWarsTeam.ZAMORAK)
        repeat(CastleWarsRound.LOBBY_MINUTES) { CastleWarsHandler.tickMinute(world) }
        assertTrue(CastleWarsRound.matchRunning)

        zam.teleportedTo(Tile(3164, 3494, 0))
        CastleWarsHandler.sweep()
        assertFalse(CastleWarsRound.matchRunning, "no Zamorak player left: the game is over")
        assertNull(sara.attr[CW_TEAM], "the Saradomin player leaves the game too")
        assertEquals(CastleWarsRound.LOBBY, sara.tile)
        assertEquals(0, sara.inventory.getItemCount(Items.CASTLE_WARS_TICKET), "no tickets for a game nobody played")
    }

    @Test
    fun `a played game gives tickets and a player who waited in the last minute gets the next countdown`() {
        val sara = player("sara")
        val zam = player("zam")
        CastleWarsHandler.joinPortal(sara, CastleWarsTeam.SARADOMIN)
        CastleWarsHandler.joinPortal(zam, CastleWarsTeam.ZAMORAK)
        repeat(CastleWarsRound.LOBBY_MINUTES) { CastleWarsHandler.tickMinute(world) }
        repeat(CastleWarsRound.MATCH_MINUTES - 1) { CastleWarsHandler.tickMinute(world) }
        val late1 = player("late1")
        val late2 = player("late2")
        CastleWarsHandler.joinPortal(late1, CastleWarsTeam.SARADOMIN)
        CastleWarsHandler.joinPortal(late2, CastleWarsTeam.ZAMORAK)
        CastleWarsHandler.tickMinute(world)
        assertFalse(CastleWarsRound.matchRunning, "the game ended")
        assertEquals(1, sara.inventory.getItemCount(Items.CASTLE_WARS_TICKET), "a 0-0 draw gives 1 ticket (OSRS)")
        assertTrue(CastleWarsRound.active, "the two who waited get the next countdown")
        assertEquals<List<Player>>(listOf(late1), CastleWarsRound.waiting[CastleWarsTeam.SARADOMIN]!!)
    }

    @Test
    fun `team colours worn outside a game come off at login`() {
        val p = player("restart")
        p.equipment[EquipmentType.HEAD.id] = gg.rsmod.game.model.item.Item(Items.ZAMORAK_TEAM_HOOD)
        p.equipment[EquipmentType.CAPE.id] = gg.rsmod.game.model.item.Item(Items.HOODED_CLOAK_4042)
        assertTrue(CastleWarsHandler.stripTeamGear(p))
        assertNull(p.equipment[EquipmentType.HEAD.id])
        assertNull(p.equipment[EquipmentType.CAPE.id])
    }
}
