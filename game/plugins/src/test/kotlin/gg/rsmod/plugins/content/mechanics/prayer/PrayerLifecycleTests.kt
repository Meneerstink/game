package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the persisted-varp to runtime-prayer lifecycle boundary. */
class PrayerLifecycleTests {
    @Test
    fun `login init restores normal active prayer and Protect Item runtime mirror`() {
        val fixture = Fixture()
        fixture.player.setVarbit(Prayer.PROTECT_FROM_MELEE.varbit, 1)
        fixture.player.setVarbit(Prayer.PROTECT_ITEM.varbit, 1)

        Prayers.init(fixture.player)

        assertTrue(Prayers.isActive(fixture.player, Prayer.PROTECT_FROM_MELEE))
        assertTrue(Prayers.isActive(fixture.player, Prayer.PROTECT_ITEM))
        assertTrue(fixture.attributes[PROTECT_ITEM_ATTR] == true)
    }

    @Test
    fun `login init rebuilds ancient curse runtime set and Turmoil from persisted varbits`() {
        val fixture = Fixture()
        // Switching to the Ancient book now needs the performed ritual (Azzanadra / Ancient Hymnal unlock flow).
        fixture.attributes[AncientCurses.UNLOCKED_ATTR] = true
        AncientCurses.switchBook(fixture.player, AncientCurses.PrayerBook.ANCIENT)
        fixture.player.setVarbit(AncientCurse.DEFLECT_MELEE.varbit, 1)
        fixture.player.setVarbit(AncientCurse.TURMOIL_VARBIT, 1)

        // A fresh login has no session-only ACTIVE_CURSES_ATTR/TURMOIL_ACTIVE_ATTR values.
        Prayers.init(fixture.player)

        assertTrue(AncientCurses.isCurseActive(fixture.player, AncientCurse.DEFLECT_MELEE))
        assertTrue(AncientCurses.isTurmoilActive(fixture.player))
        assertTrue(fixture.player.getVarbit(AncientCurse.TURMOIL_VARBIT) != 0)
    }

    @Test
    fun `normal login does not leave a stale curse runtime state`() {
        val fixture = Fixture()
        fixture.player.setVarbit(AncientCurse.DEFLECT_MAGIC.varbit, 1)

        Prayers.init(fixture.player)

        assertFalse(AncientCurses.isCurseActive(fixture.player, AncientCurse.DEFLECT_MAGIC))
        assertTrue(fixture.player.getVarbit(AncientCurse.DEFLECT_MAGIC.varbit) == 0)
    }

    private class Fixture {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val attributes = AttributeMap()
        private val definitions = mockk<DefinitionSet>()
        private val varps = VarpSet((0..8000).toSet())

        init {
            every { player.world } returns world
            every { world.definitions } returns definitions
            every { player.attr } returns attributes
            every { player.varps } returns varps
            every { player.varcs } returns MutableList(2000) { 0 }
            every { player.prayerIcon } returns PrayerIcon.NONE.id
            every { player.prayerIcon = any() } answers { }
            every { definitions.get(VarbitDef::class.java, any()) } answers {
                val id = secondArg<Int>()
                VarbitDef(id).apply {
                    when {
                        id == AncientCurse.BOOK_VARBIT -> {
                            varp = AncientCurse.ACTIVE_VARP
                            startBit = 20
                        }
                        id in AncientCurse.ACTIVE_VARBIT_BASE..AncientCurse.TURMOIL_VARBIT -> {
                            varp = AncientCurse.ACTIVE_VARP
                            startBit = id - AncientCurse.ACTIVE_VARBIT_BASE
                        }
                        Prayer.values.any { it.varbit == id } -> {
                            val prayer = Prayer.values.first { it.varbit == id }
                            varp = Prayers.ACTIVE_PRAYERS_VARP
                            startBit = prayer.slot
                        }
                        id in 6857..6861 -> {
                            varp = id
                            startBit = 0
                        }
                        else -> {
                            varp = id
                            startBit = 0
                        }
                    }
                    endBit = startBit
                }
            }
        }
    }
}
