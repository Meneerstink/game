package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.AvTrace
import gg.rsmod.game.model.BreachTrace
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.attr.TEAM_TEST_ACCOUNT_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.service.serializer.TeamTestAccounts
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Varps
import gg.rsmod.plugins.api.ext.calculateAndSetCombatLevel
import gg.rsmod.plugins.api.ext.getSpellbook
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.switchSpellbook
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.mechanics.moderation.TeamAccountPresets
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.skills.summoning.BeastOfBurden
import gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR
import java.io.File

/**
 * T1 (owner 2026-10-13, AGENTS.md 4: "reproduce an owner report with its own data"): replays a bugs\bug-*.txt report onto a
 * team test account on the test server. Mechanism ported from Darkan's debug `copy [player name]` (world-server-dev
 * commands/debug/Debug.java:317-344, only registered on a debug server): every equipment and inventory slot is SET with
 * the same id and amount - never worn through the equip action, so a report about gear the equip action refuses still
 * replays - then the skills, then the appearance is rebuilt. Adapted: the source is the report file instead of a live
 * player, slots the report leaves empty are emptied too (Darkan only overwrites the filled ones), and the report's
 * "Replay:" line ([stateLines], written by the `bug` command since T1) adds the prayer book, spellbook, run, special
 * energy, attack style and auto-retaliate. Old reports without that line keep the account's own skills and say so.
 * Gate (Darkan: Debug.startup() returns unless the server is a debug server): test server only and only the fixed team
 * test accounts ([TeamTestAccounts]), after their login preset ([TeamAccountPresets.PRESET_APPLIED_ATTR]).
 */
object BugReplay {
    /** Dynamic areas (POH, other instances) are allocated from here; a report position there is someone else's copy. */
    const val INSTANCE_X = 6400

    private const val RUN_VARP = Varps.RUN_STATE

    class Report(
        val position: Tile?,
        val traces: Map<String, Boolean>,
        val inventory: List<Triple<Int, Int, Int>>,
        val equipment: List<Triple<Int, Int, Int>>,
        val replay: Map<String, String>,
        val problems: List<String>,
    )

    /** "slot:idxamount slot:idxamount" - the format of the report's Inventory / Equipment lines. */
    fun containerLine(container: ItemContainer): String =
        container.rawItems.mapIndexedNotNull { i, it -> it?.let { "$i:${it.id}x${it.amount}" } }.joinToString(" ")

    /** The report's Position line (format of the debug toolkit, unchanged). */
    fun positionLine(player: Player): String = player.tile.let { t -> "Position: ${t.x},${t.z},${t.height}  region=${t.regionId}  chunk=${t.chunkCoords}" }

    /**
     * The machine-readable part of a report: the Inventory / Equipment lines (format unchanged since the debug toolkit) and
     * the "Replay:" line with what a faithful replay needs beyond them. Position stays in the report writer.
     */
    fun stateLines(player: Player): List<String> {
        val t = player.tile
        val skills = (0 until player.skills.maxSkills).joinToString(",") { "${player.skills.getCurrentLevel(it)}:${player.skills.getCurrentXp(it)}" }
        val book = runCatching { AncientCurses.getBook(player).name }.getOrDefault("NORMAL")
        val prayers =
            runCatching {
                if (book == AncientCurses.PrayerBook.ANCIENT.name) {
                    AncientCurse.values().filter { AncientCurses.isCurseActive(player, it) }.map { it.name }
                } else {
                    Prayer.values().filter { Prayers.isActive(player, it) }.map { it.name }
                }
            }.getOrDefault(emptyList())
        val familiar = runCatching { player.attr[FAMILIAR_ATTR]?.get()?.id }.getOrNull()
        val bob = runCatching { BeastOfBurden.activeContainer(player)?.let { containerLine(it) } }.getOrNull()
        val replay =
            listOf(
                "v=1",
                "skills=$skills",
                "hp=${player.getCurrentLifepoints()}",
                "prayer=${player.getCurrentPrayerPoints()}",
                "prayerbook=$book",
                "spellbook=${runCatching { player.getSpellbook().name }.getOrDefault("?")}",
                "run=${player.runEnergy}/${if (player.getVarp(RUN_VARP) == 1) "on" else "off"}",
                "spec=${AttackTab.getEnergy(player)}",
                "style=${player.getVarp(AttackTab.ATTACK_STYLE_VARP)}",
                "retaliate=${if (player.getVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP) == 0) "on" else "off"}",
                "prayers=${prayers.joinToString(",").ifEmpty { "none" }}",
                "familiar=${familiar ?: "none"}",
                "bob=${bob?.replace(' ', ',')?.ifEmpty { "empty" } ?: "none"}",
                "skull=${player.skullIcon}",
                "instance=${if (t.x >= INSTANCE_X) "dynamic(local ${t.x and 63},${t.z and 63})" else "no"}",
            )
        return listOf(
            "Inventory: " + containerLine(player.inventory),
            "Equipment: " + containerLine(player.equipment),
            "Replay: " + replay.joinToString(" "),
        )
    }

    fun parse(lines: List<String>): Report {
        val problems = mutableListOf<String>()
        fun line(prefix: String) = lines.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()
        val position =
            line("Position:")?.substringBefore(' ')?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.let {
                if (it.size == 3) Tile(it[0], it[1], it[2]) else null
            }
        if (position == null) problems += "no Position line"
        val traces =
            line("Traces:")?.split(' ')?.filter { '=' in it }?.associate { it.substringBefore('=') to (it.substringAfter('=') == "on") }.orEmpty()
        fun slots(prefix: String): List<Triple<Int, Int, Int>> {
            val text = line(prefix)
            if (text == null) {
                problems += "no $prefix line"
                return emptyList()
            }
            return text.split(' ').filter { it.isNotBlank() }.mapNotNull { token ->
                val m = SLOT.matchEntire(token)
                if (m == null) {
                    problems += "$prefix '$token' unreadable"
                    null
                } else {
                    Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                }
            }
        }
        val inventory = slots("Inventory:")
        val equipment = slots("Equipment:")
        val replay = line("Replay:")?.split(' ')?.filter { '=' in it }?.associate { it.substringBefore('=') to it.substringAfter('=') }.orEmpty()
        return Report(position, traces, inventory, equipment, replay, problems)
    }

    /** Null when [name] may be replayed onto; otherwise the reason it is refused. */
    fun refusal(
        name: String,
        player: Player?,
        testServer: Boolean,
    ): String? {
        val lower = name.trim().lowercase()
        return when {
            !testServer -> "refused: replay only runs on the test server (-Drsps.testServer=true, ports 50215/50217)"
            lower !in TeamTestAccounts.NAMES -> "refused: $name is not a team test account (${TeamTestAccounts.NAMES.joinToString("/")})"
            player == null -> "refused: $name is not logged in on the test server"
            player.attr[TEAM_TEST_ACCOUNT_ATTR] != true -> "refused: $name is not a fresh team test account"
            player.attr[TeamAccountPresets.PRESET_APPLIED_ATTR] != true -> "wait: $name has not put on its login preset yet (it would overwrite the replay) - retry in 2 seconds"
            else -> null
        }
    }

    /** A report file name in [bugsDir]: only `bug-*.txt` names, never a path. */
    fun reportFile(
        name: String,
        bugsDir: File,
    ): File? = if (FILE_NAME.matches(name)) File(bugsDir, name) else null

    /** Puts the report's state on [player]. Returns one line per restored / refused / missing part ("PROBLEM ..." for faults). */
    fun apply(
        player: Player,
        report: Report,
    ): List<String> {
        val out = mutableListOf<String>()
        val defs = player.world.definitions
        val itemCount = runCatching { defs.getCount(ItemDef::class.java) }.getOrDefault(Int.MAX_VALUE)
        fun fill(
            container: ItemContainer,
            what: String,
            slots: List<Triple<Int, Int, Int>>,
        ) {
            for (i in 0 until container.capacity) container[i] = null
            var restored = 0
            for ((slot, id, amount) in slots) {
                when {
                    slot !in 0 until container.capacity -> out += "PROBLEM $what slot $slot out of range (item $id x$amount)"
                    id !in 0 until itemCount || runCatching { defs.get(ItemDef::class.java, id) }.getOrNull() == null ->
                        out += "PROBLEM $what slot $slot: unknown item id $id"
                    amount < 1 -> out += "PROBLEM $what slot $slot: amount $amount"
                    else -> {
                        container[slot] = Item(id, amount)
                        restored++
                    }
                }
            }
            out += "$what: $restored/${slots.size} slots"
        }

        val pos = report.position
        when {
            pos == null -> out += "PROBLEM position: no Position line in the report"
            pos.x >= INSTANCE_X ->
                out += "position: instance (dynamic area x>=$INSTANCE_X, e.g. a POH) not restored - enter the house/instance by hand " +
                    "(region-local tile ${pos.x and 63},${pos.z and 63},${pos.height})"
            else -> {
                player.moveTo(pos)
                out += "position: ${pos.x},${pos.z},${pos.height}"
            }
        }

        // Darkan copy: equipment and inventory slot by slot, the equip action is not involved.
        fill(player.equipment, "equipment", report.equipment)
        fill(player.inventory, "inventory", report.inventory)
        val plugins = player.world.plugins
        for (slot in 0 until player.equipment.capacity) {
            runCatching { if (player.equipment[slot] != null) plugins.executeEquipSlot(player, slot) else plugins.executeUnequipSlot(player, slot) }
        }
        player.addBlock(UpdateBlockType.APPEARANCE)

        val r = report.replay
        val skills = r["skills"]?.split(',')?.map { it.split(':') }
        if (skills == null) {
            out += "skills: not in report (written before T1) - the account keeps its own levels"
        } else {
            skills.forEachIndexed { i, s ->
                if (i < player.skills.maxSkills && s.size == 2) {
                    val xp = s[1].toDoubleOrNull()
                    val level = s[0].toIntOrNull()
                    if (xp == null || level == null) {
                        out += "PROBLEM skills: skill $i '${s.joinToString(":")}' unreadable"
                    } else {
                        player.skills.setXp(i, xp)
                        player.skills.setCurrentLevel(i, level)
                    }
                }
            }
            runCatching { player.calculateAndSetCombatLevel() }
            out += "skills: ${skills.size}"
        }
        r["hp"]?.toIntOrNull()?.let { player.setCurrentLifepoints(it) }
        r["prayer"]?.toIntOrNull()?.let { player.setCurrentPrayerPoints(it) }
        r["prayerbook"]?.let { name ->
            val book = AncientCurses.PrayerBook.values().firstOrNull { it.name == name }
            if (book == null) {
                out += "PROBLEM prayerbook: '$name' unknown"
            } else if (AncientCurses.getBook(player) != book) {
                if (book == AncientCurses.PrayerBook.ANCIENT) TeamAccountPresets.useCurses(player) else AncientCurses.switchBook(player, book)
                if (AncientCurses.getBook(player) != book) out += "PROBLEM prayerbook: $name not switched"
            }
        }
        r["spellbook"]?.let { name ->
            val book = Spellbook.values.firstOrNull { it.name == name }
            if (book == null) out += "PROBLEM spellbook: '$name' unknown" else if (player.getSpellbook() != book) player.switchSpellbook(book)
        }
        r["run"]?.let { run ->
            run.substringBefore('/').toDoubleOrNull()?.let { player.runEnergy = it }
            player.setVarp(RUN_VARP, if (run.substringAfter('/') == "on") 1 else 0)
        }
        r["spec"]?.toIntOrNull()?.let { if (it in 0..100) AttackTab.setEnergy(player, it) }
        r["style"]?.toIntOrNull()?.let { player.setVarp(AttackTab.ATTACK_STYLE_VARP, it) }
        r["retaliate"]?.let { player.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, if (it == "on") 0 else 1) }
        if (r.isNotEmpty()) out += "prayerbook/spellbook/run/spec/style/retaliate: restored"
        val notRestored =
            listOfNotNull(
                r["prayers"]?.takeIf { it != "none" }?.let { "active prayers $it" },
                r["familiar"]?.takeIf { it != "none" }?.let { "familiar $it (bob ${r["bob"]})" },
                r["skull"]?.takeIf { it != "-1" }?.let { "skull icon $it" },
            )
        if (notRestored.isNotEmpty()) out += "not restored (do by hand): ${notRestored.joinToString("; ")}"

        // The report's trace switches (server-wide; the test server is the agent's own).
        if (report.traces.isNotEmpty()) {
            Trace.Cat.values().forEach { cat -> report.traces[cat.key]?.let { Trace.set(cat, it) } }
            report.traces["av"]?.let { AvTrace.enabled = it }
            report.traces["breach"]?.let { BreachTrace.enabled = it }
            out += "traces: ${Trace.status()}"
        }
        return out
    }

    /** What still differs between [player] and [report] (position, every slot, every restored Replay field). Empty = identical. */
    fun diff(
        player: Player,
        report: Report,
    ): List<String> {
        val out = mutableListOf<String>()
        val pos = report.position
        if (pos != null && pos.x < INSTANCE_X && (player.tile.x != pos.x || player.tile.z != pos.z || player.tile.height != pos.height)) {
            out += "position ${player.tile.x},${player.tile.z},${player.tile.height} != ${pos.x},${pos.z},${pos.height}"
        }
        val expectInv = report.inventory.joinToString(" ") { "${it.first}:${it.second}x${it.third}" }
        val expectEq = report.equipment.joinToString(" ") { "${it.first}:${it.second}x${it.third}" }
        if (containerLine(player.inventory) != expectInv) out += "inventory '${containerLine(player.inventory)}' != '$expectInv'"
        if (containerLine(player.equipment) != expectEq) out += "equipment '${containerLine(player.equipment)}' != '$expectEq'"
        if (report.replay.isNotEmpty()) {
            val now = parse(stateLines(player)).replay
            for (key in RESTORED) {
                val want = report.replay[key] ?: continue
                if (now[key] != want) out += "$key '${now[key]}' != '$want'"
            }
        }
        return out
    }

    /**
     * The client log that belongs to a report (T1). Before, the writer took the newest launcher-logs\client-*.out.log on the
     * server PC, which since the team's hook clients (RestartClient.ps1) can be an agent's log on the owner's report, and is
     * never a friend's. Agent test clients print "[testhook]" at start: on the test server only those logs fit, on the live
     * server only the others, and only when the reporter's client runs on this PC ([reporterIsLocal]); else none.
     */
    fun pickClientLog(
        logs: List<File>,
        testServer: Boolean,
        reporterIsLocal: Boolean,
    ): File? {
        if (!reporterIsLocal) return null
        return logs.filter { it.name.startsWith("client-") && it.name.endsWith(".out.log") }
            .sortedByDescending { it.lastModified() }
            .firstOrNull { isAgentClientLog(it) == testServer }
    }

    fun isAgentClientLog(log: File): Boolean =
        runCatching {
            log.inputStream().use { input ->
                val head = ByteArray(16384)
                val n = input.read(head)
                n > 0 && String(head, 0, n, Charsets.ISO_8859_1).contains("[testhook]")
            }
        }.getOrDefault(false)

    private val RESTORED = listOf("skills", "hp", "prayer", "prayerbook", "spellbook", "run", "spec", "style", "retaliate")
    private val SLOT = Regex("(\\d+):(\\d+)x(\\d+)")
    private val FILE_NAME = Regex("bug-[A-Za-z0-9_.-]+\\.txt")
}
