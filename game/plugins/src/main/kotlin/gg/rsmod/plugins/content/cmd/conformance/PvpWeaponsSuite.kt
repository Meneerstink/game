package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.Spellbook
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setSpellbook
import gg.rsmod.plugins.api.ext.setVarbit
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.content.inter.attack.AttackTab
import gg.rsmod.plugins.content.magic.MagicSpells
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurse
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers

/**
 * PvP audit of the OSRS-ported high-level weapons (owner 2026-10-09, 10-hour plan block 3: "audit ONLY OSRS-ported high-level
 * weapons + prayers/curses interaction ... test bot vs bot with tick traces"). Bot Pv1 attacks bot Pv2 with each weapon of
 * [WEAPONS] (cache items whose name matches, wielded with fitting ammo / charges) in five states of Pv2: no prayer, Protect
 * from Melee, Protect from Missiles, Protect from Magic, and the Deflect curse of the weapon's own style. Every swing's
 * `[trace:combat] hit` line (style, max hit, damage) is captured.
 *
 * Rules checked (OSRS Wiki "Protect from Melee/Missiles/Magic": "in PvP ... reduce damage by 40%"; "Deflect Melee" etc. the same
 * 40% in PvP): the weapon swings; a protection prayer of the hit's own style leaves floor(max x 0.6) (+-1 for the floored
 * steps), any other protection prayer leaves the max unchanged; the matching Deflect curse keeps every landed hit at or below
 * that same 60%; no hit above its own traced max; the special attack (when the weapon has one) fires and obeys the same.
 *
 *   conform pvpweapons [name filter]
 */
object PvpWeaponsSuite : Conformance.Suite {
    /** Research 2026-10-09 (plan-research-osrs-weapons-pvp.md): the OSRS-ported high-level weapons of this server. */
    val WEAPONS =
        listOf(
            "Armadyl godsword", "Bandos godsword", "Saradomin godsword", "Zamorak godsword", "Ancient godsword", "Voidwaker",
            "Osmumten's fang", "Dragon warhammer", "Abyssal dagger", "Burning claws", "Granite maul", "Noxious halberd",
            "Arclight", "Emberlight", "Dragon scimitar", "Frozen abyssal whip", "Volcanic abyssal whip", "Statius's warhammer",
            "Vesta's longsword", "Dual macuahuitl", "Blue moon spear", "Dragon hunter lance", "Armadyl crossbow",
            "Zaryte crossbow", "Dragon crossbow", "Heavy ballista", "Toxic blowpipe", "Blazing blowpipe", "Dark bow",
            "Magic shortbow (i)", "Webweaver bow", "Scorching bow", "Tonalztics of ralos", "Eclipse atlatl", "Twisted bow",
            "Bow of faerdhinen", "Venator bow", "Craw's bow", "Dragon hunter crossbow", "Volatile nightmare staff",
            "Eldritch nightmare staff", "Harmonised nightmare staff", "Staff of the dead", "Toxic staff of the dead",
            "Staff of balance", "Kodai wand", "Sanguinesti staff", "Trident of the swamp", "Trident of the seas",
            "Ancient sceptre", "Zuriel's staff",
        )

    private val NO_PLAYERS = listOf("Trident of the seas", "Trident of the swamp", "Sanguinesti staff")

    private val STATES = listOf("no prayer", "Protect from Melee", "Protect from Missiles", "Protect from Magic", "Deflect")

    private val HIT = Regex("""hit Pv1 -> Pv2 type=(\w+) maxHit=([\d.]+) landed=(\w+) damage=(\d+)""")

    private class Swing(val style: String, val max: Int, val damage: Int)

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val filter = args.joinToString(" ").lowercase()
        val arena = run.origin.transform(3, 0)
        run.note("arena ${arena.x},${arena.z},${arena.height} (stand where players may fight: a Deadman dangerous zone)")
        val a = run.bot(arena, "Pv1")
        val b = run.bot(arena.transform(1, 0), "Pv2")
        wait(2)
        for (p in listOf(a, b)) {
            for (s in 0 until 25) p.skills.setBaseLevel(s, 99)
            p.setVarbit(MagicSpells.INF_RUNES_VARBIT, 1)
        }
        AncientCurses.unlock(b)
        val defs =
            (0 until run.world.definitions.getCount(ItemDef::class.java)).mapNotNull { id ->
                val def = runCatching { run.world.definitions.getNullable(ItemDef::class.java, id) }.getOrNull() ?: return@mapNotNull null
                val name = def.name
                if (def.equipSlot != 3 || def.noted || def.isPlaceholder) return@mapNotNull null
                if (WEAPONS.none { it.equals(name, true) || (name.startsWith(it, true) && name.substring(it.length).trim().startsWith("(")) }) return@mapNotNull null
                if (filter.isNotEmpty() && !name.lowercase().contains(filter)) return@mapNotNull null
                def
            }
        run.note("weapons: ${defs.size}")
        for (def in defs) {
            if (Conformance.stopRequested) break
            val e = "pvp ${def.id} ${def.name}"
            run.progress(e)
            val c0 = run.world.currentCycle
            try {
                weapon(run, a, b, arena, def, e)
            } catch (t: Throwable) {
                run.add(Conformance.Result(e, "scenario", Conformance.Verdict.FAIL, "runs to the end", "threw $t"))
            }
            if (run.results.any { it.entry == e && it.verdict == Conformance.Verdict.FAIL }) run.window(e, c0)
        }
    }

    private suspend fun QueueTask.weapon(
        run: Conformance.Run,
        a: Client,
        b: Client,
        arena: Tile,
        def: ItemDef,
        e: String,
    ) {
        val bySate = LinkedHashMap<String, List<Swing>>()
        val specials = LinkedHashMap<String, List<Swing>>()
        for (state in STATES) {
            setState(a, b, arena, def, state)
            if (state == "Deflect") {
                val curse = deflectFor(def)
                run.expectTrue(e, "Deflect: ${curse.name} active on the target", AncientCurses.isCurseActive(b, curse), "book ${AncientCurses.getBook(b)} active ${AncientCurse.values.filter { AncientCurses.isCurseActive(b, it) }}")
            }
            bySate[state] = swings { a.attack(b) }
            if (SpecialAttacks.hasSpecialAttack(def.id) && (state == "no prayer" || state.startsWith("Protect"))) {
                setState(a, b, arena, def, state)
                AttackTab.setEnergy(a, 100)
                a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
                // Only the special's own hits: the bot stops after it (the next normal swing would land in the window too).
                specials[state] = swings(6, stopAfter = 3, stop = { a.resetInteractions(); Combat.reset(a) }) { a.attack(b) }
            }
        }
        val base = bySate["no prayer"].orEmpty()
        // OSRS Wiki (Trident of the seas / swamp, Sanguinesti staff): the built-in spell cannot be cast on players.
        if (NO_PLAYERS.any { def.name.startsWith(it, true) }) {
            run.expectTrue(e, "built-in spell refused against a player", base.isEmpty(), "hits ${base.map { it.damage }}", "OSRS Wiki: cannot be used on players")
            return
        }
        run.expectTrue(e, "swings at a player (no prayer)", base.isNotEmpty(), "no hit line within 8 ticks (refused, out of range, no ammo/charges)")
        if (base.isEmpty()) return
        val style = base.first().style
        val max = base.maxOf { it.max }
        val protectOf = mapOf("MELEE" to "Protect from Melee", "RANGE" to "Protect from Missiles", "MAGIC" to "Protect from Magic")
        val reduced = Math.floor(max * 0.6).toInt()
        for (state in STATES.drop(1)) {
            val s = bySate[state].orEmpty()
            if (s.isEmpty()) {
                run.expectTrue(e, "$state: swings", false, "no hit line within 8 ticks")
                continue
            }
            val m = s.maxOf { it.max }
            when {
                state == protectOf[style] ->
                    run.expectTrue(e, "$state ($style hit): max hit x0.6", Math.abs(m - reduced) <= 1, "max $m, unprotected $max -> expected $reduced", "OSRS Wiki: protection prayers -40% in PvP")
                state == "Deflect" ->
                    run.expectTrue(e, "Deflect ${style.lowercase()}: every hit <= 60% of $max", s.all { it.damage <= reduced + 1 }, "damages ${s.map { it.damage }}", "OSRS: Deflect = protection -40% in PvP")
                else ->
                    run.expectTrue(e, "$state ($style hit): max unchanged", m == max, "max $m, unprotected $max", "only the hit's own style is protected")
            }
            run.expectTrue(e, "$state: no hit above its max", s.all { it.damage <= it.max }, s.joinToString(" ") { "${it.damage}/${it.max}" })
        }
        run.expectTrue(e, "no prayer: no hit above its max", base.all { it.damage <= it.max }, base.joinToString(" ") { "${it.damage}/${it.max}" })
        if (SpecialAttacks.hasSpecialAttack(def.id)) {
            val sp = specials["no prayer"].orEmpty()
            run.expectTrue(e, "special: fires at a player", sp.isNotEmpty(), "no hit line within 6 ticks of the special")
            if (sp.isNotEmpty()) {
                val spStyle = sp.first().style
                val spMax = sp.filter { it.style == spStyle }.maxOf { it.max }
                // Only hits of the special's own style (a normal swing of another style can still fall in the window).
                val prot = specials[protectOf[spStyle] ?: ""].orEmpty().filter { it.style == spStyle }
                if (prot.isNotEmpty()) {
                    val pm = prot.maxOf { it.max }
                    run.expectTrue(
                        e, "special ($spStyle hit) vs ${protectOf[spStyle]}: max x0.6", Math.abs(pm - Math.floor(spMax * 0.6).toInt()) <= 1,
                        "special max $pm, unprotected $spMax", "the special's own hit style is the protected one",
                    )
                }
                run.note("$e special: ${sp.size} hits, style $spStyle, max $spMax, damages ${sp.map { it.damage }}")
            }
        }
        run.note("$e: style $style, max $max, per state ${bySate.map { (k, v) -> "$k=${v.maxOfOrNull { it.max }}" }}")
    }

    private suspend fun QueueTask.setState(
        a: Client,
        b: Client,
        arena: Tile,
        def: ItemDef,
        state: String,
    ) {
        a.resetInteractions()
        b.resetInteractions()
        Combat.reset(a)
        Combat.reset(b)
        a.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
        a.moveTo(arena)
        b.moveTo(arena.transform(1, 0))
        for (p in listOf(a, b)) {
            p.setCurrentLifepoints(p.getMaximumLifepoints() * 10)
            p.setCurrentPrayerPoints(99)
            p.skills.setCurrentLevel(Skills.PRAYER, 99)
        }
        Prayers.deactivateAll(b)
        AncientCurses.deactivateAllCurses(b)
        a.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
        equip(a, def)
        wait(1)
        castIfStaff(a, def)
        when (state) {
            "Protect from Melee" -> { AncientCurses.switchBook(b, AncientCurses.PrayerBook.NORMAL); Prayers.activate(b, Prayer.PROTECT_FROM_MELEE) }
            "Protect from Missiles" -> { AncientCurses.switchBook(b, AncientCurses.PrayerBook.NORMAL); Prayers.activate(b, Prayer.PROTECT_FROM_MISSILES) }
            "Protect from Magic" -> { AncientCurses.switchBook(b, AncientCurses.PrayerBook.NORMAL); Prayers.activate(b, Prayer.PROTECT_FROM_MAGIC) }
            "Deflect" -> {
                AncientCurses.switchBook(b, AncientCurses.PrayerBook.ANCIENT)
                val curse = deflectFor(def)
                AncientCurses.toggleCurse(b, curse, playActivationVisual = false)
            }
            else -> AncientCurses.switchBook(b, AncientCurses.PrayerBook.NORMAL)
        }
        wait(2)
    }

    /** The Deflect curse of the weapon's own style, from its name (staves/wands magic, bows/crossbows/blowpipes ranged). */
    private fun deflectFor(def: ItemDef): AncientCurse {
        val n = def.name.lowercase()
        return when {
            listOf("staff", "wand", "sceptre", "trident").any { n.contains(it) } && !n.contains("staff of the dead") && !n.contains("staff of balance") -> AncientCurse.DEFLECT_MAGIC
            listOf("bow", "ballista", "blowpipe", "atlatl", "tonalztics").any { n.contains(it) } -> AncientCurse.DEFLECT_MISSILES
            else -> AncientCurse.DEFLECT_MELEE
        }
    }

    /** Through the real inventory option (the weapon tab and attack style follow), like a player wields it. */
    private fun equip(
        a: Client,
        def: ItemDef,
    ) {
        val n = def.name.lowercase()
        var weapon = Item(def.id)
        val staff = gg.rsmod.plugins.content.items.osrs.PoweredStaves.staffFor(def.id)
        if (staff != null) weapon = gg.rsmod.plugins.content.items.osrs.PoweredStaves.withCharges(Item(staff.charged), staff.maxCharges - 1)
        if (gg.rsmod.plugins.content.items.osrs.Blowpipe.isBlowpipe(def.id)) {
            val pipe = gg.rsmod.plugins.content.items.osrs.Blowpipe.Pipe.forItem(def.id)!!
            weapon = Item(pipe.charged)
            weapon.attr[ItemAttribute.ATTACHED_ITEM_ID] = Items.DRAGON_DART
            weapon.attr[ItemAttribute.ATTACHED_ITEM_COUNT] = 16_000
            weapon.attr[ItemAttribute.CHARGES] = 16_000
        }
        a.equipment[3] = null
        a.inventory.removeAll()
        a.inventory.add(weapon)
        val option = listOf("Wield", "Wear", "Equip", "Hold").firstOrNull { o -> def.inventoryMenu.any { it?.equals(o, true) == true } } ?: "Wield"
        Bots.itemOption(a, 0, option)
        a.equipment[13] =
            when {
                n.contains("ballista") -> Item(Items.OSRS_DRAGON_JAVELIN, 5000)
                n.contains("atlatl") -> Item(Items.ATLATL_DART, 5000)
                n.contains("crossbow") -> Item(Items.RUNITE_BOLTS, 5000)
                n.contains("bow") && !n.contains("craw") && !n.contains("webweaver") && !n.contains("faerdhinen") -> Item(Items.DRAGON_ARROW, 5000)
                else -> null
            }
        a.addBlock(UpdateBlockType.APPEARANCE)
    }

    /** A staff without a built-in spell autocasts Ice Barrage (ancient book, infinite runes) - the PvP staple. */
    private fun castIfStaff(
        a: Client,
        def: ItemDef,
    ) {
        val n = def.name.lowercase()
        val builtIn = gg.rsmod.plugins.content.items.osrs.PoweredStaves.staffFor(def.id) != null
        if (!builtIn && listOf("staff", "wand", "sceptre").any { n.contains(it) } && !n.contains("staff of the dead") && !n.contains("staff of balance")) {
            a.setSpellbook(Spellbook.ANCIENT)
            gg.rsmod.plugins.content.combat.magic.Autocast.select(a, CombatSpell.ICE_BARRAGE, gg.rsmod.plugins.content.combat.magic.Autocast.Mode.STANDARD)
        }
    }

    private suspend fun QueueTask.swings(
        ticks: Int = 8,
        stopAfter: Int = -1,
        stop: () -> Unit = {},
        action: () -> Unit,
    ): List<Swing> {
        val lines = mutableListOf<String>()
        Trace.capture(lines)
        try {
            action()
            if (stopAfter > 0) {
                wait(stopAfter)
                stop()
            }
            wait(ticks)
        } finally {
            Trace.stopCapture()
        }
        return synchronized(lines) { lines.toList() }.mapNotNull { HIT.find(it) }.map { m ->
            Swing(m.groupValues[1], Math.floor(m.groupValues[2].toDouble() + 1e-9).toInt(), m.groupValues[4].toInt())
        }
    }
}
