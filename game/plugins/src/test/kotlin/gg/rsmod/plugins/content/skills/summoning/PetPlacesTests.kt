package gg.rsmod.plugins.content.skills.summoning

import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N407 checklist point 15 (K02): the places a pet may not follow ([PetPlaces], OSRS Wiki "Pet", Restrictions). The messages are the
 * wiki's, and every way into those places asks [PetPlaces] - the in-game behaviour is checked in the test client (screens\N407\15-*).
 */
class PetPlacesTests {
    private val content = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content").toFile()

    private fun source(path: String) = File(content, path).readText()

    @Test
    fun `the restriction messages are the OSRS Wiki's`() {
        assertEquals("Your pet has been hidden as you enter this area.", PetPlaces.MSG_HIDDEN)
        assertEquals("Your follower hides in fear and refuses to enter the cave.", PetPlaces.MSG_CORP_ENTER)
        assertEquals("Your follower hides in fear and won't come out.", PetPlaces.MSG_CORP_DROP)
        assertEquals("This doesn't seem like a suitable place to take a pet.", PetPlaces.MSG_NEX)
        assertEquals("You should pick up your follower before entering building mode.", PetPlaces.MSG_BUILD_MODE)
        assertEquals("You cannot drop items while in building mode.", PetPlaces.MSG_BUILD_MODE_DROP)
        assertEquals("You can't enter a waiting area with a follower.", PetPlaces.MSG_SOUL_WARS)
        assertEquals("Your little friend can't make it over the obstacle...", PetPlaces.MSG_APE_ATOLL)
        assertEquals("Your follower hides in your pack, this is too violent an area for it.", PetPlaces.HiddenArea.PEST_CONTROL.dropRefusal)
        assertEquals("Your pet stays in your pack, frightened by something.", PetPlaces.HiddenArea.SORCERESS_GARDEN.dropRefusal)
        // N476: Castle Wars waits for the owner's N442 test.
        assertEquals(setOf("PEST_CONTROL", "SOUL_WARS", "SORCERESS_GARDEN", "CORP_CAVE"), PetPlaces.HiddenArea.values().map { it.name }.toSet())
    }

    @Test
    fun `every way into a place a pet may not follow asks PetPlaces`() {
        val corp = source("areas/wilderness/corporeal_beast_lair.plugin.kts")
        assertTrue("PetPlaces.MSG_CORP_ENTER" in corp, "Corporeal Beast's cave entrance")
        val nex = source("areas/godwars/nex/nex_arena.plugin.kts")
        assertTrue("pets.carriesPet(player)" in nex && "option == 1 && !refused()" in nex, "Nex: before the warning and again on Climb down")
        assertTrue("PetPlaces.carriesPet(player)" in source("activity/soul_wars/SoulWarsHandler.kt"), "Soul Wars lobby")
        assertTrue("PetPlaces.MSG_BUILD_MODE" in source("areas/poh/PohBuild.kt"), "building mode")
        assertTrue("if (PohBuild.setBuildMode(player, choice == 2)) enterHouse(player)" in source("areas/poh/player_house.plugin.kts"), "house portal")
        val ape = source("mechanics/traversal/batch9/ape_atoll.plugin.kts")
        assertEquals(ape.split("player.wrongGreegree()) return@obstacle").size - 1, ape.split("PetPlaces.beforeApeAtollObstacle(player)").size - 1, "every Ape Atoll obstacle")
        val pets = source("skills/summoning/Pets.kt")
        assertTrue("PetPlaces.dropRefusal(player)" in pets, "a pet item is never dropped where it may not come out")
        assertTrue("|| isHidden(player)" in pets, "a hidden pet still counts as the follower (a new pet goes to the backpack)")
    }
}
