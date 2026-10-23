package gg.rsmod.plugins.content.mechanics.moderation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.RECENT_COMMANDS_ATTR
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.service.log.LoggerService
import gg.rsmod.game.tools.importer.DeathsOfficeInterfaceImportTool
import gg.rsmod.game.tools.importer.StaffCommandsInterfaceImportTool as Ui
import gg.rsmod.plugins.api.ChatMessageType
import gg.rsmod.plugins.api.InterfaceDestination
import gg.rsmod.plugins.api.ext.inputString
import gg.rsmod.plugins.api.ext.isInterfaceVisible
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.openInterface
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.runClientScript
import gg.rsmod.plugins.api.ext.setComponentHidden
import gg.rsmod.plugins.api.ext.setComponentText
import gg.rsmod.plugins.api.ext.setInterfaceEvents
import java.io.File

/**
 * Owner 2026-10-10: the staff command list (`::commands`, moderators and up) - every chat command of the server with what it
 * does, searchable. A staff member sees the commands of his own rank and every lower rank (owner: everything).
 *
 * The list is the server's own command registry ([gg.rsmod.game.plugin.PluginRepository.allCommands], so it can never miss a
 * command or show a removed one) with the rank it really needs; the texts come from `data/cfg/commands.yml` (a registered command
 * without an entry still shows, as "No description yet"). Layout: [Ui].
 *
 * Owner 2026-10-10 ("its not working easy ... improve the mod command gui"): a row's "Run" runs the command - straight away when it
 * takes no arguments (after a yes/no for the ones that reset or remove something), otherwise after asking for the arguments with
 * the usage as the question; "Info" prints the usage and the example.
 *
 * A17 (owner 10-11: "the mod command is a little bit overwhelming" -> "fewer buttons, grouped in categories Players / Punish /
 * Teleport / Info"): four task tabs, each with only the commands of its `category` in commands.yml the viewer may use (Darkan's
 * mod set falls into them: kick/mute/ban = Punish, teleto/teletome = Teleport). Search finds every command of the viewer's rank,
 * also the ones without a category (dev tools, gear presets); searching "recent" lists the commands he ran last
 * ([RECENT_COMMANDS_ATTR], Run repeats them with their arguments) - VRAGEN 17 = A: no Recent / Gear tab.
 */
object StaffCommands {
    const val INTERFACE_ID = Ui.INTERFACE_ID
    const val PATH = "./data/cfg/commands.yml"

    enum class Rank(val label: String, val colour: String) {
        PLAYER("Player", "c0c0c0"),
        MODERATOR("Moderator", "7fbfff"),
        ADMIN("Admin", "ffff00"),
        OWNER("Owner", "ff5050"),
    }

    /** The owner's task categories (A17), `category:` in commands.yml. */
    enum class Category(val key: String, val label: String) {
        PLAYERS("players", "Players"),
        PUNISH("punish", "Punish"),
        TELEPORT("teleport", "Teleport"),
        INFO("info", "Info"),
        ;

        companion object {
            fun of(key: String?): Category? = values().firstOrNull { it.key == key?.trim()?.lowercase() }
        }
    }

    class Entry(
        val name: String,
        val rank: Rank,
        val usage: String,
        val description: String,
        val example: String,
        /** Recent: the arguments it was run with, repeated by Run. */
        val args: List<String> = emptyList(),
        val category: Category? = null,
    ) {
        fun withArgs(args: List<String>) = Entry(name, rank, usage, description, example, args, category)

        /** "::name arg1 arg2". */
        val line: String get() = (listOf("::$name") + args).joinToString(" ")
    }

    /** The tabs of [Ui.TAB_LABELS], in that order: one per [Category]. */
    enum class Tab(val category: Category) {
        PLAYERS(Category.PLAYERS),
        PUNISH(Category.PUNISH),
        TELEPORT(Category.TELEPORT),
        INFO(Category.INFO),
    }

    /** The search word that lists the viewer's recent commands. */
    const val RECENT_SEARCH = "recent"

    /** The rank a command's power needs: none = player, "mod" = moderator, "admin" = admin, "dev" / "owner" = owner. */
    fun rankOfPower(power: String?): Rank =
        when (power?.lowercase()) {
            null -> Rank.PLAYER
            Privilege.MOD_POWER -> Rank.MODERATOR
            Privilege.ADMIN_POWER -> Rank.ADMIN
            else -> Rank.OWNER
        }

    /** The highest rank [player] holds. */
    fun rankOf(player: Player): Rank {
        val powers = player.privilege.powers
        return when {
            Privilege.OWNER_POWER in powers || Privilege.DEV_POWER in powers -> Rank.OWNER
            Privilege.ADMIN_POWER in powers -> Rank.ADMIN
            Privilege.MOD_POWER in powers -> Rank.MODERATOR
            else -> Rank.PLAYER
        }
    }

    class Text(val usage: String, val description: String, val example: String, val category: String? = null)

    /** `data/cfg/commands.yml`, by command name. */
    fun loadTexts(file: File = File(PATH)): Map<String, Text> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val rows = ObjectMapper(YAMLFactory()).readValue(file, List::class.java) as List<Map<String, Any?>>
            rows.associate { row ->
                row["name"].toString().lowercase() to
                    Text(row["usage"]?.toString() ?: "", row["description"]?.toString() ?: "", row["example"]?.toString() ?: "", row["category"]?.toString())
            }
        }.getOrElse { e ->
            Trace.problem(Trace.Cat.INTERFACE) { "staff command list: $PATH unreadable: ${e.message}" }
            emptyMap()
        }
    }

    @Volatile private var texts: Map<String, Text>? = null

    private fun texts(): Map<String, Text> = texts ?: loadTexts().also { texts = it }

    /** Every registered command with its texts, sorted by rank (highest first) and name. */
    fun entries(
        registry: Map<String, String?>,
        texts: Map<String, Text>,
    ): List<Entry> =
        registry.map { (name, power) ->
            val t = texts[name]
            Entry(
                name, rankOfPower(power), t?.usage?.ifBlank { null } ?: "::$name", t?.description?.ifBlank { null } ?: "No description yet.",
                t?.example ?: "", category = Category.of(t?.category),
            )
        }.sortedWith(compareByDescending<Entry> { it.rank }.thenBy { it.name })

    /** What [viewer] may see, narrowed to [category] (null = every command) and [search] (matched in the name, usage and description). */
    fun visible(
        all: List<Entry>,
        viewer: Rank,
        search: String,
        category: Category? = null,
    ): List<Entry> {
        val q = search.trim().lowercase().removePrefix("::")
        return all.filter { it.rank <= viewer && (category == null || it.category == category) }.filter {
            q.isEmpty() || it.name.contains(q) || it.usage.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    private val SEARCH_ATTR = AttributeKey<String>()
    private val TAB_ATTR = AttributeKey<Int>()
    private val SHOWN_ATTR = AttributeKey<List<Entry>>()

    /** IF_SETEVENTS: op 1 only; op 1 and op 2 (a row's Run and Info). */
    private const val OP1 = 2
    private const val OP1_OP2 = 2 or 4

    /** IfButtonMessage opcodes of op 1 (Run) and op 2 (Info) - see notes.plugin.kts for why the opcode and not the option index. */
    const val OPCODE_RUN = 61
    const val OPCODE_INFO = 64

    fun tab(index: Int): Tab = Tab.values().getOrNull(index) ?: Tab.PLAYERS

    /** The Recent tab: [lines] ("name arg1 ...", newest first) as entries of commands [viewer] may still use. */
    fun recent(
        all: List<Entry>,
        viewer: Rank,
        lines: List<String>,
    ): List<Entry> {
        val byName = all.associateBy { it.name }
        return lines.mapNotNull { line ->
            val words = line.split(" ").filter { it.isNotEmpty() }
            val entry = words.firstOrNull()?.let { byName[it] } ?: return@mapNotNull null
            if (entry.rank > viewer) null else entry.withArgs(words.drop(1))
        }
    }

    /** A tab button: the selected one shows its pressed look. */
    private fun drawButton(
        player: Player,
        id: Int,
        pressed: Boolean,
    ) {
        player.setComponentHidden(INTERFACE_ID, id + Ui.LOOK_OUT, pressed)
        player.setComponentHidden(INTERFACE_ID, id + Ui.LOOK_IN, !pressed)
    }

    fun open(
        player: Player,
        search: String = player.attr[SEARCH_ATTR] ?: "",
        tab: Int = player.attr[TAB_ATTR] ?: 0,
    ) {
        val viewer = rankOf(player)
        val selected = tab(tab)
        val tabIndex = selected.ordinal
        player.attr[SEARCH_ATTR] = search
        player.attr[TAB_ATTR] = tabIndex
        val all = entries(player.world.plugins.allCommands(), texts())
        val mine = all.count { it.rank <= viewer }
        val recent = search.trim().equals(RECENT_SEARCH, ignoreCase = true)
        val shown =
            when {
                recent -> recent(all, viewer, player.attr[RECENT_COMMANDS_ATTR].orEmpty().map { it.toString() })
                search.isBlank() -> visible(all, viewer, "", selected.category)
                else -> visible(all, viewer, search)
            }
        if (shown.size > Ui.ROWS) {
            Trace.problem(Trace.Cat.INTERFACE) { "staff command list: ${shown.size} commands but the screen has ${Ui.ROWS} rows - raise ROWS" }
        }
        val rows = shown.take(Ui.ROWS)
        player.attr[SHOWN_ATTR] = rows
        player.openInterface(INTERFACE_ID, InterfaceDestination.MAIN_SCREEN)
        player.setInterfaceEvents(INTERFACE_ID, DeathsOfficeInterfaceImportTool.CLOSE, -1..-1, OP1)
        player.setInterfaceEvents(INTERFACE_ID, Ui.SEARCH, -1..-1, OP1)
        Ui.TABS.forEachIndexed { i, id ->
            val used = i < Tab.values().size
            player.setComponentHidden(INTERFACE_ID, id, !used)
            if (!used) return@forEachIndexed
            // A tab shows pressed while its own list is on screen; a search result belongs to no tab.
            drawButton(player, id, pressed = i == tabIndex && search.isBlank())
            player.setInterfaceEvents(INTERFACE_ID, id, -1..-1, OP1)
        }
        player.setComponentHidden(INTERFACE_ID, Ui.CLEAR, true)
        val status =
            when {
                recent -> "${rows.size} recent commands"
                search.isBlank() -> "${rows.size} ${selected.category.label} commands"
                else -> "${rows.size} of $mine - search: <col=ff981f>$search</col>"
            }
        player.setComponentText(INTERFACE_ID, Ui.STATUS, status)
        for (r in 0 until Ui.ROWS) {
            val row = Ui.row(r)
            val entry = rows.getOrNull(r)
            player.setComponentHidden(INTERFACE_ID, row, entry == null)
            if (entry == null) continue
            player.setComponentText(INTERFACE_ID, row + Ui.ROW_NAME, entry.line)
            player.setComponentText(INTERFACE_ID, row + Ui.ROW_RANK, "<col=${entry.rank.colour}>${entry.rank.label}</col>")
            player.setComponentText(INTERFACE_ID, row + Ui.ROW_DESCRIPTION, entry.description)
            player.setInterfaceEvents(INTERFACE_ID, row, -1..-1, OP1_OP2)
        }
        player.runClientScript(
            DeathsOfficeInterfaceImportTool.SCROLL_SCRIPT,
            (INTERFACE_ID shl 16) or Ui.SCROLL,
            (INTERFACE_ID shl 16) or Ui.SCROLLBAR,
            maxOf(rows.size * Ui.ROW_HEIGHT, Ui.SCROLL_H),
        )
        if (rows.isEmpty()) {
            player.message(
                when {
                    recent -> "No recent commands yet."
                    search.isBlank() -> "No ${selected.category.label} commands for your rank."
                    else -> "No command matches '$search'."
                },
            )
        }
    }

    /** Words that make a no-argument command ask "are you sure" before Run. */
    private val CONFIRM_WORDS = listOf("reset", "clear", "wipe", "delete", "remove", "drain", "empty", "kill", "shutdown", "restart", "update")

    /** Whether [usage] ("::name <a> [b]") takes required ('<') or optional ('[') arguments. */
    fun requiredArgs(usage: String): Boolean = usage.substringAfter(' ', "").contains('<')

    fun optionalArgs(usage: String): Boolean = usage.substringAfter(' ', "").contains('[')

    /** Whether Run of a no-argument command asks first. */
    fun needsConfirm(entry: Entry): Boolean {
        val text = "${entry.name} ${entry.description}".lowercase()
        return CONFIRM_WORDS.any { text.contains(it) }
    }

    /** "Run" on row [index]: see the class comment. */
    fun run(
        player: Player,
        index: Int,
    ) {
        val entry = player.attr[SHOWN_ATTR]?.getOrNull(index) ?: return
        val asks = entry.args.isEmpty() && (requiredArgs(entry.usage) || optionalArgs(entry.usage))
        when {
            asks ->
                player.queue {
                    val typed = inputString("${entry.usage.take(70)} - type the arguments:").trim()
                    val args = typed.split(" ").filter { it.isNotEmpty() }
                    if (args.isEmpty() && requiredArgs(entry.usage)) {
                        player.message("Cancelled: ${entry.usage} needs arguments.")
                        return@queue
                    }
                    execute(player, entry.withArgs(args))
                }
            entry.args.isEmpty() && needsConfirm(entry) ->
                player.queue {
                    if (options("Yes, run ::${entry.name}", "No", title = entry.description.take(60)) == 1) execute(player, entry)
                }
            else -> execute(player, entry)
        }
    }

    private fun execute(
        player: Player,
        entry: Entry,
    ) {
        val args = entry.args.toTypedArray()
        if (!player.world.plugins.executeCommand(player, entry.name, args.takeIf { it.isNotEmpty() })) {
            player.message("You can't use ::${entry.name}.")
            return
        }
        player.world.getService(LoggerService::class.java, searchSubclasses = true)?.logCommand(player, entry.name, *args)
        // The recent list follows at once while it is still on screen (a command may have opened its own window instead).
        if (player.isInterfaceVisible(INTERFACE_ID) && (player.attr[SEARCH_ATTR] ?: "").trim().equals(RECENT_SEARCH, ignoreCase = true)) open(player)
    }

    /** "Show" on row [index]: the usage, description and example in the chatbox. */
    fun show(
        player: Player,
        index: Int,
    ) {
        val entry = player.attr[SHOWN_ATTR]?.getOrNull(index) ?: return
        player.message("<col=ff981f>${entry.usage}</col> (${entry.rank.label}): ${entry.description}", type = ChatMessageType.GAME_MESSAGE)
        if (entry.example.isNotBlank()) player.message("Example: <col=ff981f>${entry.example}</col>", type = ChatMessageType.GAME_MESSAGE)
    }
}
