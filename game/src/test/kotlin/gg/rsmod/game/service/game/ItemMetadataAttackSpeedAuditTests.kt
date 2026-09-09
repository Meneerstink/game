package gg.rsmod.game.service.game

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Owner-reported: "many [weapon attack speeds] are incorrectly ~2 ticks, Chaotic maul is one
 * example." Root cause found by inspection: `ItemMetadataService.Equipment.attackSpeed` defaults
 * to `-1` (`@JsonProperty("attack_speed") val attackSpeed: Int = -1`) whenever an item's block in
 * `data/cfg/items.yml` has no `attack_speed` key at all - which was true for Chaotic maul. That
 * `-1` then reaches `CombatConfigs.getAttackDelay()`, which does `Math.max(MIN_ATTACK_SPEED, speed)`
 * with `MIN_ATTACK_SPEED = 1`, so a weapon missing this field attacks every single tick instead of
 * at its real speed - the fastest possible attack rate, not a specific "~2 ticks" but the same class
 * of "way too fast" symptom.
 *
 * This test parses the real `data/cfg/items.yml` generically (not through
 * [ItemMetadataService]'s own private `Metadata`/`Equipment` types, which are private to that
 * class) and asserts every weapon entry (`weapon_type` present and not -1) has a real
 * `attack_speed`, by name, so a future re-import that drops the field again fails a fast test
 * instead of shipping a silently-too-fast weapon.
 *
 * The audit originally found 28 offending ids. Dark bow (15701-15704) was fixed here using a
 * confirmed real value (9 ticks accurate/longrange, dropping to 8 on rapid via the existing
 * `CombatConfigs` -1-for-rapid rule - see the OSRS Wiki's Dark bow infobox, fetched 2026-09-09).
 * The remaining 24 (including "(broken)" variants of the same weapons) could not be fixed the
 * same way: they are 2011-era `RuneScape` items (Chaotic
 * weapons, the Zaryte/Saradomin/Guthix/Zamorak bows, Granite mace, Dragon pickaxe, Zanik's
 * crossbow, Iron hatchet), and the live `runescape.wiki` only shows their modern post-Evolution-
 * of-Combat ability stats - it no longer publishes the old tick-based speed those pages once had,
 * and the Wayback Machine is not reachable from this environment. Per this project's own rule to
 * never guess mandatory combat stats, these are recorded as SOURCE_BLOCKED (see
 * `RSPS_LIVE_BUG_BACKLOG.md`) rather than filled in from memory, and allowlisted here by exact id
 * so this test still fails on any *new* regression while not permanently red over a known,
 * tracked, externally-sourced gap. Remove an id from this list the moment it is fixed with a real
 * source.
 */
class ItemMetadataAttackSpeedAuditTests {
    /** SOURCE_BLOCKED - see the class doc comment. Real ids, exact as found by this audit. */
    private val sourceBlockedIds =
        setOf(
            14679, 14681, // Granite mace
            14684, // Zanik's crossbow
            15261, // Dragon pickaxe
            15298, // Iron hatchet
            // Rapier/longsword/maul resolved from Divergent667's scheduler-normalized timing.
            18355, 18357, // Chaotic staff/crossbow
            18356, 18358, // ...and their (broken) variants
            19143, 19145, // Saradomin bow
            19146, 19148, // Guthix bow
            19149, 19151, // Zamorak bow
            20171, 20173, 20174, // Zaryte bow, including (broken)
        )

    @Test
    fun everyWeaponEntryHasARealAttackSpeed() {
        val path = Paths.get("../data/cfg/items.yml")
        assertTrue("expected to find items.yml at $path", Files.exists(path))

        val mapper = ObjectMapper(YAMLFactory())
        mapper.propertyNamingStrategy = PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val items = Files.newBufferedReader(path).use { mapper.readValue(it, Array<RawItem>::class.java) }

        val offenders =
            items
                .filter { it.equipment != null && it.equipment.weaponType != -1 }
                .filter { it.id !in sourceBlockedIds }
                .filter { (it.equipment!!.attackSpeed) <= 1 }
                .map { "${it.id} (${it.name}): attack_speed=${it.equipment!!.attackSpeed}" }

        assertTrue(
            "weapon items with a missing/invalid attack_speed (defaults to a 1-tick attack rate): " +
                offenders.joinToString(),
            offenders.isEmpty(),
        )
    }

    private data class RawItem(
        val id: Int = -1,
        val name: String = "",
        val equipment: RawEquipment? = null,
    )

    private data class RawEquipment(
        val weaponType: Int = -1,
        val attackSpeed: Int = -1,
    )
}
