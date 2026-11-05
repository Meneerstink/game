package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** N557 (owner 2026-11-02 'Inspect basilisk jaw geeft verkeerde tekst'): OSRS Wiki transcript texts, see [OsrsInspectTexts]. */
class OsrsInspectTextsTests {
    private fun player(smithing: Int = 1): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        val skills = SkillSet(maxSkills = Skills.SMITHING + 1)
        skills.setBaseLevel(Skills.SMITHING, smithing)
        every { player.skills } returns skills
        return player
    }

    @Test
    fun `the basilisk jaw points to Olaf until his story is heard, then to the helm of Neitiznot`() {
        val p = player()
        assertEquals(
            "It's the jaw of a Basilisk Knight. Perhaps Olaf the Bard knows a good story about it.",
            OsrsInspectTexts.textFor(p, Items.BASILISK_JAW),
        )
        p.attr[OsrsInspectTexts.BASILISK_STORY] = true
        assertEquals(
            "It's the jaw of a Basilisk Knight. It looks like it could be attached to a Helm of Neitiznot.",
            OsrsInspectTexts.textFor(p, Items.BASILISK_JAW),
        )
    }

    @Test
    fun `Belle's folly tells the 70 Smithing rule by level`() {
        assertTrue(OsrsInspectTexts.textFor(player(69), Items.BELLES_FOLLY_TARNISHED)!!.endsWith("you'll need 70 Smithing."))
        assertTrue(OsrsInspectTexts.textFor(player(70), Items.BELLES_FOLLY_TARNISHED)!!.endsWith("the polishing wheel in the Giants' Foundry."))
    }

    @Test
    fun `every sourced material has its wiki text, an unsourced one keeps the examine fallback`() {
        val p = player()
        listOf(
            Items.AVERNIC_DEFENDER_HILT, Items.HYDRA_LEATHER, Items.VORKATHS_HEAD, Items.VOIDWAKER_HILT, Items.VOIDWAKER_BLADE,
            Items.VOIDWAKER_GEM, Items.KODAI_INSIGNIA, Items.MENAPHITE_ORNAMENT_KIT, Items.MAGUS_VESTIGE, Items.VENATOR_VESTIGE,
            Items.SEERS_ICON, Items.ARCHER_ICON, Items.MAGUS_ICON, Items.VENATOR_ICON, Items.AQUANITE_TENDON, Items.CONFLICTION_GAUNTLETS,
            Items.ANCIENT_ICON, Items.BLOOD_QUARTZ, Items.ICE_QUARTZ, Items.SHADOW_QUARTZ, Items.SMOKE_QUARTZ, Items.MASORI_CRAFTING_KIT,
            Items.THREAD_OF_ELIDINIS,
        ).forEach { assertTrue("$it has a text", OsrsInspectTexts.has(it) && !OsrsInspectTexts.textFor(p, it).isNullOrBlank()) }
        assertEquals("Perhaps Ava would like to see this?", OsrsInspectTexts.textFor(p, Items.VORKATHS_HEAD))
        assertFalse(OsrsInspectTexts.has(Items.ABYSSAL_WHIP))
        assertNull(OsrsInspectTexts.textFor(p, Items.ABYSSAL_WHIP))
    }

    @Test
    fun `the faceguard needs Olaf's story and speaks the OSRS lines`() {
        val root = File("src/main/kotlin/gg/rsmod/plugins/content")
        val helm = File(root, "items/osrs/serpentine_helm.plugin.kts").readText()
        assertTrue("You're not quite sure how to do that. Perhaps Olaf the Bard can help." in helm)
        assertTrue("You attach the Basilisk Jaw to the Helm of Neitiznot." in helm)
        assertTrue("You remove the Basilisk Jaw from the Neitiznot Faceguard." in helm)
        val olaf = File(root, "areas/fremennik/olaf_the_bard.plugin.kts").readText()
        assertTrue("Do you know any great stories about Basilisk Jaws?" in olaf && "BASILISK_STORY] = true" in olaf)
    }
}
