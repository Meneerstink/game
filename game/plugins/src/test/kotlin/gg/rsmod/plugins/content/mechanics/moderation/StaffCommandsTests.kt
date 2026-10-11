package gg.rsmod.plugins.content.mechanics.moderation

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.tools.importer.StaffCommandsInterfaceImportTool
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Owner 2026-10-10: the staff command list (::commands) - every command documented, ranks right, filtered by the viewer's rank. */
class StaffCommandsTests {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "data/cfg/commands.yml").exists() }
    private val texts = StaffCommands.loadTexts(File(root, "data/cfg/commands.yml"))
    private val sources = File(root, "game/plugins/src/main/kotlin")

    /** Literal on_command("name"[, Privilege.X_POWER]) bindings in the plugin sources, name -> power (null = everyone). */
    private fun literalBindings(): Map<String, String?> {
        val re = Regex("""on_command\(\s*"([^"]+)"(?:\s*,\s*(?:gg\.rsmod\.game\.model\.priv\.)?Privilege\.([A-Z_]+))?""")
        val out = mutableMapOf<String, String?>()
        sources.walkTopDown().filter { it.extension == "kts" || it.extension == "kt" }.forEach { f ->
            re.findAll(f.readText()).forEach { m ->
                val power =
                    when (m.groupValues[2]) {
                        "" -> null
                        "MOD_POWER" -> Privilege.MOD_POWER
                        "ADMIN_POWER" -> Privilege.ADMIN_POWER
                        "DEV_POWER" -> Privilege.DEV_POWER
                        else -> Privilege.OWNER_POWER
                    }
                out[m.groupValues[1].lowercase()] = power
            }
        }
        return out
    }

    @Test
    fun `every literal command has a description and the rank the code requires`() {
        val bindings = literalBindings()
        assertTrue(bindings.size > 200, "found ${bindings.size} bindings")
        val missing = bindings.keys.filter { it !in texts }
        assertTrue(missing.isEmpty(), "commands without an entry in data/cfg/commands.yml: $missing")
        val yamlRank =
            File(root, "data/cfg/commands.yml").readLines().zipWithNext().filter { it.first.startsWith("- name:") }
                .associate { (a, b) -> a.substringAfter(':').trim() to b.substringAfter(':').trim() }
        val wrong =
            bindings.mapNotNull { (name, power) ->
                val expected = StaffCommands.rankOfPower(power).name.lowercase()
                val documented = yamlRank[name] ?: return@mapNotNull null
                if (documented != expected) "$name: code $expected, file $documented" else null
            }
        assertTrue(wrong.isEmpty(), "ranks in commands.yml differ from the code: $wrong")
    }

    @Test
    fun `the moderation commands and the list itself are moderator commands`() {
        val bindings = literalBindings()
        listOf("mute", "unmute", "ban", "unban", "jail", "unjail", "kick", "teleto", "teletome").forEach {
            assertEquals(Privilege.MOD_POWER, bindings[it], it)
        }
        listOf("home", "helios", "crown", "teleport").forEach { assertEquals(Privilege.ADMIN_POWER, bindings[it], it) }
    }

    @Test
    fun `a viewer sees his own rank and lower, and search narrows the list`() {
        val registry = mapOf("mute" to Privilege.MOD_POWER, "tele" to Privilege.ADMIN_POWER, "maxtest" to Privilege.OWNER_POWER, "players" to null)
        val all = StaffCommands.entries(registry, texts)
        assertEquals(setOf("mute", "players"), StaffCommands.visible(all, StaffCommands.Rank.MODERATOR, "").map { it.name }.toSet())
        assertEquals(setOf("mute", "tele", "players"), StaffCommands.visible(all, StaffCommands.Rank.ADMIN, "").map { it.name }.toSet())
        assertEquals(4, StaffCommands.visible(all, StaffCommands.Rank.OWNER, "").size)
        assertEquals(listOf("tele"), StaffCommands.visible(all, StaffCommands.Rank.OWNER, "::tele").map { it.name })
        assertEquals("No description yet.", StaffCommands.entries(mapOf("zzz" to null), emptyMap()).single().description)
        // Tabs: All, Recent, Gear, Player, Mod, Admin, Owner.
        assertEquals(StaffCommandsInterfaceImportTool.TAB_LABELS.size, StaffCommands.Tab.values().size)
        assertEquals(null, StaffCommands.tabRank(0))
        assertEquals(null, StaffCommands.tabRank(1))
        assertEquals(null, StaffCommands.tabRank(2))
        assertEquals(StaffCommands.Rank.MODERATOR, StaffCommands.tabRank(4))
        assertEquals(listOf("tele"), StaffCommands.visible(all, StaffCommands.Rank.OWNER, "", StaffCommands.tabRank(5)).map { it.name })
        assertEquals(listOf("players"), StaffCommands.visible(all, StaffCommands.Rank.ADMIN, "", StaffCommands.tabRank(3)).map { it.name })
    }

    @Test
    fun `recent repeats the arguments and drops commands above the viewer's rank`() {
        val registry = mapOf("mute" to Privilege.MOD_POWER, "tele" to Privilege.ADMIN_POWER, "players" to null)
        val all = StaffCommands.entries(registry, texts)
        val recent = StaffCommands.recent(all, StaffCommands.Rank.MODERATOR, listOf("tele 3200 3200", "mute bob 10", "gone", "players"))
        assertEquals(listOf("::mute bob 10", "::players"), recent.map { it.line })
        assertEquals(listOf("bob", "10"), recent.first().args)
    }

    @Test
    fun `run asks for arguments only when the usage has them`() {
        assertTrue(StaffCommands.requiredArgs("::mute <player> <minutes>"))
        assertTrue(!StaffCommands.requiredArgs("::mod [search]") && StaffCommands.optionalArgs("::mod [search]"))
        assertTrue(!StaffCommands.requiredArgs("::gear_tbow") && !StaffCommands.optionalArgs("::gear_tbow"))
    }

    @Test
    fun `every gear preset is an owner command with its own entry`() {
        val names = GearPresets.ALL.map { it.command }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.size >= 10, "$names")
        val yamlRank =
            File(root, "data/cfg/commands.yml").readLines().zipWithNext().filter { it.first.startsWith("- name:") }
                .associate { (a, b) -> a.substringAfter(':').trim() to b.substringAfter(':').trim() }
        names.forEach { assertEquals("owner", yamlRank[it], "commands.yml rank of $it") }
    }

    @Test
    fun `the screen has a row for every command`() {
        assertTrue(texts.size <= StaffCommandsInterfaceImportTool.ROWS, "${texts.size} commands, ${StaffCommandsInterfaceImportTool.ROWS} rows")
    }
}
