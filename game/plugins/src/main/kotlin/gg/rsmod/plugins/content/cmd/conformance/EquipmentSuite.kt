package gg.rsmod.plugins.content.cmd.conformance

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.setVarp
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Result
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Verdict
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.inter.attack.AttackTab
import java.io.File
import kotlin.math.floor

/**
 * Weapons and armour conformance (catalogue section 4 + armour): every equippable item the server has, matched by exact name
 * to the OSRS Wiki's own data (C:\RSPS\reference\weapons\osrs_weapons.json - Bucket API infobox_bonuses + "Special attacks"
 * page; combat is OSRS-exact by owner rule, so native 667 items with an OSRS namesake are judged against OSRS too).
 *
 * Every item: equipped through its real inventory option into the wiki's slot (2h removes the shield), all 14 bonuses on the
 * player vs the wiki, the appearance update. Weapons additionally, per attack style: swing interval vs the wiki speed (rapid
 * -1), attack animation plays and exists, swing sound audible/exists/not doubled, xp split per style (OSRS 4 : 1.33 per
 * damage), and on the most accurate style 40 swings for max hit (never exceeded, approached) and hit rate vs the OSRS
 * formulas; reach (in range it swings, one tile further it does not, the bot frozen); hit delay per distance; ammo used and
 * dropped per shot; special attack (energy vs the wiki, refused below the cost, it fires); block animation when hit.
 * Armour: the defence it gives, measured as the dummy's hit rate against the bot vs the OSRS defence roll.
 *
 * Runs in parallel lanes (a bot and a dummy per lane, each in its own arena) so thousands of items fit in one run:
 * `conform equipment [name filter] [lanes=8] [from=0] [count=N] [armour|weapons]`.
 */
object EquipmentSuite : Conformance.Suite {
    private const val REF = "C:/RSPS/reference/weapons/osrs_weapons.json"
    private const val DUMMY_NPC = 1
    private const val DUMMY_LIFE = 1_000_000
    private const val STAT_SWINGS = 40
    private const val SHIELD = Items.RUNE_KITESHIELD

    /** Ticks from a melee swing to its hitsplat on this server (OSRS melee hit delay 0), measured on the Abyssal whip. */
    private const val MELEE_BASELINE = 1

    /** Wiki equipment_slot -> the 667 equipment container slot. */
    private val SLOT = mapOf("head" to 0, "cape" to 1, "neck" to 2, "weapon" to 3, "2h" to 3, "body" to 4, "shield" to 5, "legs" to 7, "hands" to 9, "feet" to 10, "ring" to 12, "ammo" to 13)

    /** Wiki bonus field -> index in Pawn.equipmentBonuses (BonusSlot; 667 order puts magic attack before ranged). */
    private val BONUS =
        linkedMapOf(
            "stab_attack_bonus" to 0, "slash_attack_bonus" to 1, "crush_attack_bonus" to 2, "magic_attack_bonus" to 3, "range_attack_bonus" to 4,
            "stab_defence_bonus" to 5, "slash_defence_bonus" to 6, "crush_defence_bonus" to 7, "magic_defence_bonus" to 8, "range_defence_bonus" to 9,
            "strength_bonus" to 14, "ranged_strength_bonus" to 15, "prayer_bonus" to 16, "magic_damage_bonus" to 17,
        )

    override suspend fun QueueTask.run(
        run: Conformance.Run,
        args: List<String>,
    ) {
        val world = run.world
        val ref = ObjectMapper().readTree(File(REF))["weapons"]
        val byName = HashMap<String, JsonNode>()
        ref.forEach { w -> byName.putIfAbsent(w["name"].asText().lowercase(), w); byName.putIfAbsent(w["page"].asText().lowercase(), w) }
        val opts = args.filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val words = args.filter { !it.contains('=') }
        val kind = words.firstOrNull { it == "armour" || it == "weapons" }
        // Owner 2026-09-29: a Deadman server - high-level PvP/PvM gear first, the bronze-to-rune tiers are not worth the time.
        val high = words.contains("high")
        val filter = words.filter { it != "armour" && it != "weapons" && it != "high" }.joinToString(" ").lowercase()
        val lowTier = Regex("\\b(bronze|iron|steel|black|white|mithril|adamant|rune|runite|wooden|training|oak|willow|maple|yew)\\b", RegexOption.IGNORE_CASE)
        fun isHigh(d: ItemDef): Boolean {
            if (lowTier.containsMatchIn(d.name)) return false
            if (d.id >= 22328 || SpecialAttacks.hasSpecialAttack(d.id)) return true
            val b = byName[d.name.lowercase()]?.get("bonuses") ?: return false
            fun v(k: String) = b[k]?.asText()?.replace("+", "")?.toDoubleOrNull() ?: 0.0
            val offence = listOf("stab_attack_bonus", "slash_attack_bonus", "crush_attack_bonus", "range_attack_bonus", "magic_attack_bonus").maxOf { v(it) }
            val defence = listOf("stab_defence_bonus", "slash_defence_bonus", "crush_defence_bonus", "range_defence_bonus", "magic_defence_bonus").maxOf { v(it) }
            return offence >= 65 || v("strength_bonus") >= 40 || v("ranged_strength_bonus") >= 40 || v("magic_damage_bonus") > 0 || defence >= 60
        }
        val lanes = opts["lanes"]?.toIntOrNull() ?: 8
        val all =
            (0 until world.definitions.getCount(ItemDef::class.java)).mapNotNull { id ->
                val d = runCatching { world.definitions.getNullable(ItemDef::class.java, id) }.getOrNull() ?: return@mapNotNull null
                if (d.equipSlot < 0 || d.noted || d.isPlaceholder || d.name.isBlank() || d.name == "null") return@mapNotNull null
                if (kind == "weapons" && d.equipSlot != 3) return@mapNotNull null
                if (kind == "armour" && d.equipSlot == 3) return@mapNotNull null
                if (filter.isNotEmpty() && !d.name.lowercase().contains(filter)) return@mapNotNull null
                if (high && !isHigh(d)) return@mapNotNull null
                d
            }
        val items = all.drop(opts["from"]?.toIntOrNull() ?: 0).take(opts["count"]?.toIntOrNull() ?: Int.MAX_VALUE)
        run.note("items: ${items.size} (${items.count { it.equipSlot == 3 }} weapons), lanes $lanes, reference rows ${ref.size()}")
        // Lanes: each its own arena (32 tiles apart), bot and dummy; a world task per lane works through its share.
        val arenas = ArrayList<Tile>()
        var probe = run.origin.transform(3, 0)
        while (arenas.size < lanes) {
            val a = SummoningSuite.arenaFor(run, probe)
            if (arenas.none { it.isWithinRadius(a, 24) }) arenas += a
            probe = probe.transform(32, 0)
            if (arenas.size == 0 && probe.x > run.origin.x + 2000) break
        }
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        for ((lane, arena) in arenas.withIndex()) {
            val share = items.filterIndexed { i, _ -> i % arenas.size == lane }
            val bot = run.bot(arena, "W$lane")
            val dummy = run.npc(DUMMY_NPC, arena.transform(1, 0), "D$lane")
            dummy.attr[gg.rsmod.plugins.content.cmd.Sweeps.SWEEP_DUMMY_ATTR] = true
            world.queue {
                try {
                    wait(2)
                    for (def in share) {
                        if (Conformance.stopRequested) break
                        val entry = "${if (def.equipSlot == 3) "weapon" else "armour"} ${def.id} ${def.name}"
                        val from = world.currentCycle
                        run.laneStarts[lane] = from
                        try {
                            item(run, bot, dummy, arena, def, byName[def.name.lowercase()], entry, "W$lane", "D$lane")
                        } catch (e: Throwable) {
                            run.add(Result(entry, "scenario", Verdict.FAIL, "runs to the end", "threw $e"))
                        }
                        run.laneStarts.remove(lane)
                        run.window(entry, from)
                        val n = counter.incrementAndGet()
                        if (n % 25 == 0) run.progress("equipment $n/${items.size}")
                    }
                } finally {
                    done.incrementAndGet()
                }
            }
        }
        while (done.get() < arenas.size) wait(5)
        listOf(
            "wield requirements (levels, quests) per item",
            "set effects (void, barrows, inquisitor, crystal, obsidian, ...)",
            "passive effects (scythe, twisted bow, fang, lance, salve, slayer helm, ...)",
            "special attack effects beyond energy (drains, heals, freezes, multi-hit damage modifiers)",
            "poisoned weapons (p/p+/p++) and venom",
            "charges and degradation",
            "enchanted bolt effects",
            "magic: autocast with every staff, powered staves",
            "PvP: every weapon against a player, protection prayers",
            "projectile clipping over obstacles, reach against a moving target",
            "weapon stand/walk/run animations vs the OSRS/667 render set",
        ).forEach { run.notTested("equipment", it, "no scenario yet") }
    }

    // ---- one item -----------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.item(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        def: ItemDef,
        ref: JsonNode?,
        e: String,
        botLabel: String,
        dummyLabel: String,
    ) {
        val world = run.world
        if (ref == null) run.add(Result(e, "reference", Verdict.UNSOURCED, "no OSRS Wiki item of this exact name", "-"))
        val b = ref?.get("bonuses")
        reset(bot, arena)
        val wikiSlot = b?.get("equipment_slot")?.asText()
        val twoHanded = wikiSlot == "2h"
        if (def.equipSlot == 3) bot.equipment[5] = Item(SHIELD)
        bot.inventory.add(def.id, if (def.stackable) 5000 else 1)
        bot.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        // Equip through the real inventory option.
        val option = listOf("Wield", "Wear", "Equip", "Hold").firstOrNull { o -> def.inventoryMenu.any { it?.equals(o, true) == true } }
        run.expectTrue(e, "has a Wield/Wear/Equip option", option != null, "menu=${def.inventoryMenu.toList()}")
        if (option == null) return
        val c0 = world.currentCycle
        Bots.itemOption(bot, bot.inventory.getItemIndex(def.id, false), option)
        wait(2)
        val slot = (0 until bot.equipment.capacity).firstOrNull { bot.equipment[it]?.id == def.id }
        if (slot == null) {
            val said = run.recording.of(botLabel, "msg").filter { it.cycle >= c0 && it.text.startsWith("MessageGameMessage") }.map { it.text }
            run.add(Result(e, "equips (level 99 bot)", Verdict.FAIL, "in the equipment", "not equipped; messages: ${said.joinToString(" / ").take(160)}"))
            return
        }
        run.check(e, "equipment slot", slot, "wiki" to wikiSlot?.let { SLOT[it] })
        if (def.equipSlot == 3) run.check(e, "two-handed removes the shield", bot.equipment[5] == null, "wiki" to (twoHanded as Any?).takeIf { wikiSlot != null })
        run.expectTrue(e, "appearance updated on equip", run.recording.of(botLabel, "out").any { it.cycle >= c0 && it.text == "appearance" }, "no appearance block after equipping")
        // Bonuses on the player (all equipment is this item alone, the shield removed for armour-free numbers).
        if (!twoHanded && slot != 5) bot.equipment[5] = null
        bot.addBlock(UpdateBlockType.APPEARANCE)
        wait(1)
        if (b != null) {
            for ((field, idx) in BONUS) {
                val wiki = b[field]?.takeIf { !it.isNull }?.asText()?.replace("+", "")?.toDoubleOrNull() ?: continue
                run.check(e, "bonus $field", bot.equipmentBonuses[idx], "wiki" to wiki.toInt())
            }
        }
        if (def.equipSlot == 3) weapon(run, bot, dummy, arena, def, ref, e, botLabel, dummyLabel) else armour(run, bot, dummy, arena, def, ref, e, botLabel, dummyLabel)
        bot.equipment.removeAll()
        bot.inventory.removeAll()
    }

    // ---- weapons ------------------------------------------------------------------------------------------------------------

    private suspend fun QueueTask.weapon(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        def: ItemDef,
        ref: JsonNode?,
        e: String,
        botLabel: String,
        dummyLabel: String,
    ) {
        val world = run.world
        val b = ref?.get("bonuses")
        val wikiStyle = b?.get("combat_style")?.asText()?.lowercase() ?: ""
        val ranged = wikiStyle in setOf("bow", "crossbow", "thrown", "chinchompas", "salamander", "blaster", "gun", "multishot") ||
            listOf("bow", "crossbow", "dart", "knife", "javelin", "thrownaxe", "chinchompa", "blowpipe").any { def.name.lowercase().contains(it) }
        armAmmo(bot, def)
        val wikiSpeed = b?.get("weapon_attack_speed")?.takeIf { !it.isNull }?.asInt()
        // Every attack style the weapon offers (varp 43 index 0..3).
        for (style in 0..3) {
            if (Conformance.stopRequested) return
            bot.setVarp(AttackTab.ATTACK_STYLE_VARP, style)
            val ws = runCatching { CombatConfigs.getAttackStyle(bot).name }.getOrDefault("?")
            // A weapon with fewer than four styles reports NONE for the missing ones.
            if (ws == "NONE") continue
            placeDummy(dummy, arena, 1)
            keep(bot, dummy)
            val xp0 = (0 until bot.skills.maxSkills).map { bot.skills.getCurrentXp(it) }
            val c = world.currentCycle
            bot.attack(dummy)
            val swings = swingCycles(run, bot, dummy, botLabel, 6, 40)
            bot.resetInteractions()
            Combat.reset(bot)
            if (swings.size < 2) {
                run.add(Result(e, "style $style ($ws): swings", Verdict.FAIL, ">= 2 swings in 40 ticks", "${swings.size} (ammo ${bot.equipment[13]?.id}, messages ${lastMessages(run, botLabel, c)})"))
                continue
            }
            val interval = swings.zipWithNext { x, y -> y - x }.groupBy { it }.maxByOrNull { it.value.size }!!.key
            val expected = wikiSpeed?.let { if (ws.contains("RAPID") || ws.contains("ACCURATE") && ranged && style == 1) it - 1 else it }
            run.check(e, "style $style ($ws): attack speed (ticks)", interval, "wiki" to expected)
            val anims = run.recording.of(botLabel, "out").filter { it.cycle >= c && it.text.startsWith("anim ") }.map { idOf(it.text) }.distinct()
            run.expectTrue(e, "style $style ($ws): attack animation exists", anims.isNotEmpty() && anims.all { it < 0 || world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, it) != null }, "anims=$anims")
            swingSound(run, e, "style $style ($ws) swing", swings.first(), botLabel)
            // xp split: the style's skill vs Constitution per damage (OSRS 4 : 1.33, controlled 1.33 each).
            val gained = (0 until bot.skills.maxSkills).associateWith { bot.skills.getCurrentXp(it) - xp0[it] }.filterValues { it > 0 }
            val hp = gained[Skills.CONSTITUTION]
            val main = gained.filterKeys { it != Skills.CONSTITUTION }
            if (hp != null && hp > 0 && main.isNotEmpty()) {
                val ratio = main.values.sum() / hp
                run.check(e, "style $style ($ws): xp combat skills / constitution", "%.1f".format(java.util.Locale.ENGLISH, ratio), "osrs" to "3.0", same = { a, bb -> a != null && bb != null && Math.abs(a.toString().toDouble() - bb.toString().toDouble()) <= 0.15 })
                run.add(Result(e, "style $style ($ws): xp skills", Verdict.PASS, "-", main.keys.joinToString(",")))
            }
        }
        // Statistics on style 0: max hit and hit rate vs the OSRS formulas.
        bot.setVarp(AttackTab.ATTACK_STYLE_VARP, if (ranged) 0 else 1)
        statistics(run, bot, dummy, arena, def, b, ranged, e, botLabel, dummyLabel)
        // Reach: frozen bot, dummy at the wiki range swings; one tile further never.
        val range = b?.get("weapon_attack_range")?.asText()?.toIntOrNull() ?: if (ranged) null else 1
        if (range != null) reach(run, bot, dummy, arena, range, e, botLabel)
        // Special attack.
        if (SpecialAttacks.hasSpecialAttack(def.id) || ref?.get("special")?.isNull == false) special(run, bot, dummy, arena, def, ref, e, botLabel, dummyLabel)
        // Block animation: the dummy hits the bot once. It stands next to the bot first - after the reach test it was still one tile
        // beyond the weapon's range and never reached the bot inside the window (Karil's crossbow, Dragon hunter crossbow).
        placeDummy(dummy, arena, 1)
        keep(bot, dummy)
        wait(1)
        val cb = world.currentCycle
        bot.resetInteractions()
        dummy.attack(bot)
        wait(6)
        dummy.resetInteractions()
        Combat.reset(dummy)
        val blocks = run.recording.of(botLabel, "out").filter { it.cycle >= cb && it.text.startsWith("anim ") }.map { idOf(it.text) }
        run.expectTrue(e, "block animation when hit", blocks.isNotEmpty(), "anims=$blocks (expected ${runCatching { CombatConfigs.getBlockAnimation(bot) }.getOrNull()})")
    }

    private suspend fun QueueTask.statistics(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        def: ItemDef,
        b: JsonNode?,
        ranged: Boolean,
        e: String,
        botLabel: String,
        dummyLabel: String,
    ) {
        val world = run.world
        placeDummy(dummy, arena, if (ranged) 4 else 1)
        keep(bot, dummy)
        if (ranged) armAmmo(bot, def)
        val c = world.currentCycle
        bot.attack(dummy)
        val swings = swingCycles(run, bot, dummy, botLabel, STAT_SWINGS, STAT_SWINGS * 8)
        bot.resetInteractions()
        Combat.reset(bot)
        wait(6)
        val splats =
            run.recording.of(dummyLabel, "out").filter { it.cycle >= c && it.text.startsWith("hits ") }
                .flatMap { Regex("type=(\\d+) dmg=(\\d+)").findAll(it.text).map { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }.toList() }
                .filter { it.first != HitType.REFLECTED.id }
        val scale = if (bot.getMaximumLifepoints() > 200) 10 else 1
        run.add(Result(e, "damage sample", Verdict.PASS, "-", "${swings.size} swings, ${splats.size} hitsplats, max ${splats.maxOfOrNull { it.second }}, scale x$scale"))
        if (b == null || splats.size < 15) return
        val style = runCatching { CombatConfigs.getAttackStyle(bot).name }.getOrDefault("")
        val styleAtt = if (style.contains("ACCURATE")) 3 else if (style.contains("CONTROLLED")) 1 else 0
        val styleStr = if (style.contains("AGGRESSIVE")) 3 else if (style.contains("CONTROLLED")) 1 else 0
        val lvl = 99
        val maxHit: Int
        val attBonus: Int
        if (ranged) {
            val eff = lvl + styleAtt + 8
            val rstr = bot.equipmentBonuses[15]
            maxHit = floor(0.5 + eff * (rstr + 64) / 640.0).toInt()
            attBonus = bot.equipmentBonuses[4]
        } else {
            val effStr = lvl + styleStr + 8
            maxHit = floor(0.5 + effStr * (bot.equipmentBonuses[14] + 64) / 640.0).toInt()
            attBonus = listOf(0, 1, 2).maxOf { bot.equipmentBonuses[it] }
        }
        val observedMax = splats.maxOf { it.second }
        run.expectTrue(e, "max hit never above the OSRS formula (x$scale)", observedMax <= maxHit * scale, "highest $observedMax", "<= ${maxHit * scale} (OSRS max $maxHit with the equipped bonuses, level 99, $style)")
        val landed = splats.filter { it.second > 0 }
        if (landed.size >= 20) run.expectTrue(e, "max hit approached", observedMax >= maxHit * scale * 0.75, "highest $observedMax of ${maxHit * scale} over ${landed.size} landed", ">= 75 %")
        val effAtt = lvl + styleAtt + 8
        val a = effAtt * (attBonus + 64.0)
        val defLvl = dummy.stats.getMaxLevel(if (ranged) gg.rsmod.plugins.api.NpcSkills.DEFENCE else gg.rsmod.plugins.api.NpcSkills.DEFENCE)
        val defBonus = dummy.equipmentBonuses.getOrElse(if (ranged) 9 else 6) { 0 }
        val d = (defLvl + 9) * (defBonus + 64.0)
        val p = if (a > d) 1 - (d + 2) / (2 * (a + 1)) else a / (2 * (d + 1))
        val expectNonZero = p * maxHit / (maxHit + 1.0)
        val measured = landed.size.toDouble() / splats.size
        val sigma = Math.sqrt(expectNonZero * (1 - expectNonZero) / splats.size)
        val ok = Math.abs(measured - expectNonZero) <= 3 * sigma + 0.05
        run.add(Result(e, "accuracy: non-zero hit rate (${splats.size} attacks)", if (ok) Verdict.PASS else Verdict.FAIL, "%.2f (OSRS formula, att roll %.0f vs def roll %.0f)".format(java.util.Locale.ENGLISH, expectNonZero, a, d), "%.2f".format(java.util.Locale.ENGLISH, measured)))
        // Hit delay: ticks from the swing animation to the dummy's hitsplat at this distance.
        val delays =
            swings.mapNotNull { s -> run.recording.of(dummyLabel, "out").firstOrNull { it.cycle >= s && it.text.startsWith("hits ") }?.cycle?.minus(s) }
        val dist = SummoningSuite.gap(bot.tile, 1, dummy.tile, dummy.getSize())
        val common = delays.groupBy { it }.maxByOrNull { it.value.size }?.key
        // The hitsplat goes out on the tick after the damage tick here: OSRS melee (delay 0) measures 1 (whip 4151). Ranged is
        // judged as the OSRS formula plus that same one-tick baseline.
        val expected = if (ranged) 1 + (3 + dist) / 6 + MELEE_BASELINE else MELEE_BASELINE
        run.check(e, "hit delay at distance $dist (ticks, OSRS + $MELEE_BASELINE baseline)", common, "osrs wiki hit delay" to expected)
        if (ranged) ammo(run, bot, swings.size, def, e)
    }

    private fun ammo(
        run: Conformance.Run,
        bot: Client,
        shots: Int,
        def: ItemDef,
        e: String,
    ) {
        if (shots < 10) return
        val thrown = def.stackable
        val left = if (thrown) bot.equipment[3]?.amount else bot.equipment[13]?.amount
        val used = if (left == null) null else 5000 - left
        if (def.name.contains("blowpipe", true) || def.name.contains("crystal", true) || def.name.contains("bow of faerdhinen", true)) return
        run.check(e, "ammo used per shot over $shots shots", used?.let { "%.2f".format(java.util.Locale.ENGLISH, it.toDouble() / shots) }, "osrs" to "1.00", same = { a, b -> a != null && b != null && Math.abs(a.toString().toDouble() - b.toString().toDouble()) <= 0.05 })
    }

    private suspend fun QueueTask.reach(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        range: Int,
        e: String,
        botLabel: String,
    ) {
        for ((dist, shouldSwing) in listOf(range to true, range + 1 to false)) {
            placeDummy(dummy, arena, dist)
            keep(bot, dummy)
            // Both frozen: the dummy must not walk back into reach while it retaliates.
            bot.timers[gg.rsmod.game.model.timer.FROZEN_TIMER] = 30
            dummy.timers[gg.rsmod.game.model.timer.FROZEN_TIMER] = 30
            // Let a swing still pending from the previous placement fire before the window starts.
            bot.resetInteractions()
            Combat.reset(bot)
            wait(2)
            val c = run.world.currentCycle
            bot.attack(dummy)
            wait(12)
            bot.resetInteractions()
            Combat.reset(bot)
            bot.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
            dummy.timers.remove(gg.rsmod.game.model.timer.FROZEN_TIMER)
            val swung = run.recording.of(botLabel, "out").any { it.cycle >= c && it.text.startsWith("anim ") }
            run.expectTrue(e, "reach: ${if (shouldSwing) "swings" else "does not swing"} at $dist tiles (frozen)", swung == shouldSwing, "swung=$swung", "wiki range $range")
            bot.moveTo(arena)
            wait(1)
        }
    }

    private suspend fun QueueTask.special(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        def: ItemDef,
        ref: JsonNode?,
        e: String,
        botLabel: String,
        dummyLabel: String,
    ) {
        val wiki = ref?.get("special")?.takeIf { !it.isNull }
        val costs = Regex("(\\d+)\\s*%").findAll(wiki?.get("energy_text")?.asText() ?: "").map { it.groupValues[1].toInt() }.toList().distinct()
        val cost = wiki?.get("energy_percent")?.takeIf { !it.isNull }?.asInt()
        // The wiki "Special attacks" page lists each special once (under its main item), so only a listed one is evidence.
        if (wiki != null) run.check(e, "has a special attack", SpecialAttacks.hasSpecialAttack(def.id), "wiki" to true)
        if (!SpecialAttacks.hasSpecialAttack(def.id)) return
        placeDummy(dummy, arena, if (bot.equipment[13] != null || def.stackable) 3 else 1)
        keep(bot, dummy)
        bot.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
        // The 10 % regeneration (every 50 ticks since login) must not land inside the 6-tick window: every run reached the special
        // at the same tick after login, so ACB/ZCB measured 40/65 instead of 50/75 (combat run 2026-09-29).
        bot.timers[gg.rsmod.game.model.timer.SPECIAL_ATTACK_TIMER] = 1_000
        AttackTab.setEnergy(bot, 100)
        // An instant special (Dragon battleaxe, Staff of light, Dragon thrownaxe, ...) fires from the special bar click itself, not on
        // the next attack: the harness used to set the varp and attack, so these all measured 0 energy used (combat run 2026-09-29).
        val instant = SpecialAttacks.hasInstantSpecial(def.id)
        val c = run.world.currentCycle
        if (instant) {
            bot.attack(dummy)
            wait(1)
            SpecialAttacks.executeInstant(bot)
        } else {
            bot.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
            bot.attack(dummy)
        }
        wait(6)
        bot.resetInteractions()
        Combat.reset(bot)
        val used = 100 - AttackTab.getEnergy(bot)
        bot.timers[gg.rsmod.game.model.timer.SPECIAL_ATTACK_TIMER] = gg.rsmod.plugins.content.inter.attack.SpecialEnergyRegen.NORMAL_TICKS
        run.check(e, "special: energy used", used, "wiki" to (if (costs.size > 1) costs else cost), same = { a, b -> if (b is List<*>) b.any { it.toString() == a.toString() } else a.toString() == b.toString() })
        val hits = run.recording.of(dummyLabel, "out").filter { it.cycle >= c && it.text.startsWith("hits ") }
        // Instant specials are often not attacks at all (a stat boost, Power of Death): only a normal special must land.
        if (!instant) run.expectTrue(e, "special: lands on the target", hits.isNotEmpty(), "hitsplat blocks=${hits.size}")
        run.add(Result(e, "special: what it did", Verdict.NOT_TESTED, (wiki?.get("description")?.asText() ?: "-").take(120), "hits ${hits.joinToString(" ; ") { it.text.removePrefix("hits ") }.take(160)}"))
        val realCost = if (used in costs) used else cost
        if (realCost != null && realCost > 0) {
            val cost = realCost
            AttackTab.setEnergy(bot, cost - 1)
            bot.timers.remove(gg.rsmod.game.model.timer.ATTACK_DELAY)
            val c2 = run.world.currentCycle
            if (instant) {
                bot.attack(dummy)
                wait(1)
                SpecialAttacks.executeInstant(bot)
            } else {
                bot.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 1)
                bot.attack(dummy)
            }
            wait(4)
            bot.resetInteractions()
            Combat.reset(bot)
            run.check(e, "special: refused below $cost % (energy kept)", AttackTab.getEnergy(bot), "rule" to cost - 1)
            if (c2 < 0) run.note("unreachable")
        }
        bot.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
    }

    // ---- armour -------------------------------------------------------------------------------------------------------------

    /** The dummy attacks the bot for 40 swings: its hit rate vs the OSRS defence roll with this item's defence bonus. */
    private suspend fun QueueTask.armour(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        arena: Tile,
        def: ItemDef,
        ref: JsonNode?,
        e: String,
        botLabel: String,
        dummyLabel: String,
    ) {
        val world = run.world
        placeDummy(dummy, arena, 1)
        keep(bot, dummy)
        bot.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 1)
        val c = world.currentCycle
        dummy.attack(bot)
        var t = 0
        while (t++ < 120) {
            keep(bot, dummy)
            wait(1)
            if (run.recording.of(botLabel, "out").count { it.cycle >= c && it.text.startsWith("hits ") } >= STAT_SWINGS) break
        }
        dummy.resetInteractions()
        Combat.reset(dummy)
        bot.setVarp(AttackTab.DISABLE_AUTO_RETALIATE_VARP, 0)
        val splats = run.recording.of(botLabel, "out").filter { it.cycle >= c && it.text.startsWith("hits ") }.flatMap { damages(it.text) }
        run.add(Result(e, "defence sample (dummy attacking)", Verdict.PASS, "-", "${splats.size} attacks, ${splats.count { it > 0 }} landed"))
        if (ref == null || splats.size < 20) return
        // Man attacks with crush? The npc's attack style decides the defence bonus; the stab/slash/crush maximum is the bound.
        val attLvl = dummy.stats.getMaxLevel(gg.rsmod.plugins.api.NpcSkills.ATTACK)
        val a = (attLvl + 9) * (dummy.equipmentBonuses.getOrElse(0) { 0 } + 64.0)
        val defBonus = listOf(5, 6, 7).map { bot.equipmentBonuses[it] }
        val rates =
            defBonus.map { db ->
                val d = (99 + 8) * (db + 64.0)
                val p = if (a > d) 1 - (d + 2) / (2 * (a + 1)) else a / (2 * (d + 1))
                p
            }
        val measured = splats.count { it > 0 }.toDouble() / splats.size
        val lo = rates.minOrNull()!! * 0.5
        val hi = rates.maxOrNull()!! + 3 * Math.sqrt(0.25 / splats.size) + 0.05
        run.add(
            Result(
                e, "defence: npc hit rate against this item", if (measured <= hi) Verdict.PASS else Verdict.FAIL,
                "<= %.2f (OSRS roll, def bonuses stab/slash/crush %s, dummy att %d)".format(java.util.Locale.ENGLISH, hi, defBonus, attLvl),
                "%.2f".format(java.util.Locale.ENGLISH, measured),
            ),
        )
        if (lo < 0) run.note("unreachable")
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------------

    /** Cycles of the bot's swing animations until [count] swings or [maxTicks]; keeps both alive and re-attacks if dropped. */
    private suspend fun QueueTask.swingCycles(
        run: Conformance.Run,
        bot: Client,
        dummy: Npc,
        botLabel: String,
        count: Int,
        maxTicks: Int,
    ): List<Int> {
        val c = run.world.currentCycle
        var t = 0
        while (t++ < maxTicks) {
            keep(bot, dummy)
            wait(1)
            if (run.recording.of(botLabel, "out").count { it.cycle >= c && it.text.startsWith("anim ") } >= count + 1) break
            if (bot.getCombatTarget() == null) bot.attack(dummy)
        }
        return run.recording.of(botLabel, "out").filter { it.cycle >= c && it.text.startsWith("anim ") }.map { it.cycle }.distinct()
    }

    private fun swingSound(
        run: Conformance.Run,
        e: String,
        moment: String,
        cycle: Int,
        botLabel: String,
    ) {
        val world = run.world
        val anim = run.recording.of(botLabel, "out").firstOrNull { it.cycle == cycle && it.text.startsWith("anim ") }?.let { idOf(it.text) } ?: -1
        val frame = runCatching { world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java, anim) }.getOrNull()?.takeIf { it.hasAudibleFrameSounds }?.firstAudibleFrameSound
        val sent =
            run.recording.of(botLabel, "msg").filter { it.cycle in cycle..cycle + 1 && it.text.contains("Sound") && !it.text.startsWith("SoundSongEnd") }
                .mapNotNull { Regex("(?:sound|id)=(\\d+)").find(it.text)?.groupValues?.get(1)?.toInt() } +
                run.recording.events.filter { it.cycle in cycle..cycle + 1 && it.kind == "areasound" && it.who == botLabel }.map { idOf(it.text) }
        run.expectTrue(e, "sound: $moment audible", frame != null || sent.isNotEmpty(), "anim=$anim frame=$frame sent=$sent")
        sent.filter { !world.soundExists(it) }.forEach { run.add(Result(e, "sound: $moment id $it exists in the 667 cache", Verdict.FAIL, "exists", "missing")) }
        if (frame != null && frame in sent) run.add(Result(e, "sound: $moment not heard twice", Verdict.FAIL, "frame sound not also sent", "sent $frame and the animation plays it"))
    }

    private fun armAmmo(
        bot: Client,
        def: ItemDef,
    ) {
        val n = def.name.lowercase()
        val ammo =
            when {
                // Karil's crossbow first: it fires only bolt racks (it used to get runite bolts and never swung).
                n.contains("karil") -> Items.BOLT_RACK
                n.contains("crossbow") || n.contains("c'bow") -> Items.RUNITE_BOLTS
                n.contains("ogre") && n.contains("bow") -> Items.OGRE_ARROW
                n.contains("dark bow") || n.contains("twisted bow") -> Items.DRAGON_ARROW
                n.contains("bow") && !n.contains("crystal") && !n.contains("faerdhinen") -> Items.RUNE_ARROW
                else -> null
            }
        bot.equipment[13] = ammo?.let { Item(it, 5000) }
        if (def.stackable && bot.equipment[3]?.id == def.id) bot.equipment[3] = Item(def.id, 5000)
        bot.addBlock(UpdateBlockType.APPEARANCE)
    }

    private fun reset(
        bot: Client,
        arena: Tile,
    ) {
        bot.resetInteractions()
        Combat.reset(bot)
        bot.moveTo(arena)
        bot.equipment.removeAll()
        bot.inventory.removeAll()
        bot.setVarp(AttackTab.SPECIAL_ATTACK_VARP, 0)
        bot.setVarp(AttackTab.ATTACK_STYLE_VARP, 0)
        bot.setCurrentLifepoints(bot.getMaximumLifepoints())
    }

    private fun keep(
        bot: Client,
        dummy: Npc,
    ) {
        bot.setCurrentLifepoints(bot.getMaximumLifepoints())
        dummy.setCurrentLifepoints(DUMMY_LIFE)
    }

    private fun placeDummy(
        dummy: Npc,
        arena: Tile,
        dist: Int,
    ) {
        dummy.resetInteractions()
        Combat.reset(dummy)
        dummy.moveTo(arena.transform(dist.coerceAtLeast(1), 0))
    }

    private fun lastMessages(
        run: Conformance.Run,
        who: String,
        from: Int,
    ) = run.recording.of(who, "msg").filter { it.cycle >= from && it.text.startsWith("MessageGameMessage") }.takeLast(2).joinToString(" / ") { it.text.take(80) }

    private fun idOf(text: String): Int = Regex("\\bid=(-?\\d+)").find(text)?.groupValues?.get(1)?.toInt() ?: -1

    private fun damages(text: String): List<Int> = Regex("dmg=(\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList()

}
