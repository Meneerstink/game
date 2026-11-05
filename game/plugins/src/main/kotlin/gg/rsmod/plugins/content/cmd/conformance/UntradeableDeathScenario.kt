package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.getWildernessLevel
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.mechanics.death.PerduLostProperty
import gg.rsmod.plugins.content.mechanics.death.PerduReclaim
import gg.rsmod.plugins.content.mechanics.death.UntradeableDeathProtection

/**
 * Owner 2026-10-24 ("test de broken items en de untradeables op elke mogelijke manier: pvp death, npc death; sommige items zoals void
 * hou je broken in de inventory, sommige zoals colossal blade, untradeable holy books en meer moeten naar perdu ... hou rekening met
 * trouver parchments, onze dmm regels"): real deaths in the game loop - a bot kills a bot at level 20 Wilderness or below, above level
 * 20, and a monster kills a bot - each carrying one item of every untradeable class, and every item's fate is checked as the player
 * finds it after the respawn: in the inventory (whole, broken, mangled), in the gravestone, or back from Perdu.
 *
 * The rules (owner decisions 2026-09-26 / 2026-10-04, OSRS Wiki "(broken)", "Trouver parchment", "Perdu"): a monster death keeps every
 * lockable/breakable untradeable and sends the rest to the gravestone; a PvP death at or below level 20 breaks the breakables in the
 * inventory, above level 20 they are destroyed (Perdu gives them back for the repair price) except the ones that break there too
 * (fire capes, defenders) and the Trouver-locked ones, which are mangled; the other untradeables are lost (coins for the killer) and
 * the Lost Property ones (Colossal blade, god books, god capes ...) can be bought back from Perdu; the max cape and pets are kept.
 */
object UntradeableDeathScenario {
    private val ITEMS =
        listOf(
            Items.VOID_KNIGHT_TOP, Items.ELITE_VOID_KNIGHT_TOP, Items.FIGHTER_TORSO, Items.FIRE_CAPE, Items.FIRE_CAPE_LOCKED_22324,
            Items.RUNE_DEFENDER, Items.DRAGON_DEFENDER, Items.INFERNAL_CAPE, Items.COLOSSAL_BLADE, Items.HOLY_BOOK, Items.SARADOMIN_CAPE,
            Items.GLOVES_7462, Items.MAX_CAPE, Items.RUNE_POUCH,
            // K01 2026-10-25: two of the new Trouver-locked items (clones with their own broken/mangled forms).
            Items.VOID_KNIGHT_TOP_L, Items.DRAGON_DEFENDER_L,
            // K01 deel 5: every kit untradeable and never lost - a kitted item and a loose kit.
            Items.ARMADYL_GODSWORD_OR, Items.FURY_ORNAMENT_KIT,
            // N472 (owner 2026-10-31): the Void Knight Deflector like the rest of Void, plain and locked.
            Items.VOID_KNIGHT_DEFLECTOR_19712, Items.VOID_KNIGHT_DEFLECTOR_L,
        )

    /** A tile with open ground around it whose Wilderness level is in [levels]. */
    private fun wildTile(
        run: Conformance.Run,
        levels: IntRange,
    ): Tile? {
        fun open(t: Tile) = (-1..2).all { dx -> (-1..2).all { dz -> !runCatching { run.world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        for (z in 3528..3960 step 4) for (x in 3040..3300 step 4) {
            val t = Tile(x, z, 0)
            if (t.getWildernessLevel() in levels && open(t)) return t
        }
        return null
    }

    private fun prepare(
        p: Client,
        tile: Tile,
    ) {
        p.resetInteractions()
        Combat.reset(p)
        p.equipment.removeAll()
        p.inventory.removeAll()
        p.bank.removeAll()
        p.gravestone.removeAll()
        p.deathRecovery.removeAll()
        p.attr.remove(gg.rsmod.plugins.content.mechanics.pvp.LootKeys.SLOTS)
        p.timers.remove(gg.rsmod.game.model.timer.SKULL_ICON_DURATION_TIMER)
        gg.rsmod.plugins.content.mechanics.prayer.Prayers.deactivateAll(p)
        p.moveTo(tile)
        p.setCurrentLifepoints(p.getMaximumLifepoints())
    }

    suspend fun QueueTask.untradeableDeaths(run: Conformance.Run) {
        val e = "untradeables"
        val low = wildTile(run, 5..15)
        val deep = wildTile(run, 25..40)
        if (low == null || deep == null) {
            run.notTested(e, "wilderness tiles", "no open tile found (low=$low deep=$deep)")
            return
        }
        val a = run.bot(low, "UA")
        val b = run.bot(low.transform(1, 0), "UB")
        wait(2)

        /** [killer] kills [victim] carrying [ITEMS] at [tile]; returns what the victim holds afterwards (inventory + worn). */
        suspend fun pvpKill(
            label: String,
            tile: Tile,
        ): List<Item>? {
            prepare(a, tile)
            prepare(b, tile.transform(1, 0))
            a.inventory.add(Items.ABYSSAL_WHIP)
            gg.rsmod.game.action.EquipAction.equip(a, Item(Items.ABYSSAL_WHIP), a.inventory.getItemIndex(Items.ABYSSAL_WHIP, skipAttrItems = false))
            ITEMS.forEach { b.inventory.add(it, 1) }
            gg.rsmod.plugins.content.mechanics.death.PerduLostProperty.observe(b)
            wait(1)
            b.setCurrentLifepoints(20)
            a.attack(b)
            var died = false
            val deathTile = b.tile
            for (t in 0 until 150) {
                a.setCurrentLifepoints(a.getMaximumLifepoints())
                wait(1)
                if (b.isDead()) died = true
                if (died && !b.isDead() && b.tile != deathTile) break
            }
            a.resetInteractions()
            Combat.reset(a)
            wait(2)
            run.expectTrue(e, "$label: the victim died and respawned", died && !b.isDead(), "dead=${b.isDead()} at ${deathTile.getWildernessLevel()} wild")
            if (!died) return null
            return (b.inventory.toList() + b.equipment.toList()).filterNotNull()
        }

        fun has(
            items: List<Item>,
            id: Int,
        ) = items.any { it.id == id }

        // ---- PvP at or below level 20 ----
        val l = pvpKill("PvP <=20", low)
        if (l != null) {
            listOf(
                Items.VOID_KNIGHT_TOP to Items.VOID_KNIGHT_TOP_BROKEN, Items.ELITE_VOID_KNIGHT_TOP to Items.ELITE_VOID_TOP_BROKEN,
                Items.FIGHTER_TORSO to Items.FIGHTER_TORSO_BROKEN, Items.FIRE_CAPE to Items.FIRE_CAPE_BROKEN,
                Items.RUNE_DEFENDER to Items.RUNE_DEFENDER_BROKEN, Items.DRAGON_DEFENDER to Items.DRAGON_DEFENDER_BROKEN,
                Items.INFERNAL_CAPE to Items.INFERNAL_CAPE_BROKEN, Items.VOID_KNIGHT_DEFLECTOR_19712 to Items.VOID_KNIGHT_DEFLECTOR_BROKEN,
            ).forEach { (item, broken) ->
                run.expectTrue(e, "PvP <=20: ${name(run, item)} is in the inventory as ${name(run, broken)}", has(l, broken) && !has(l, item), "held=${l.map { it.id }}")
            }
            // N416: the locked fire cape breaks into the visible Fire cape (l) (broken), no longer a hidden mark on the whole cape.
            val lockedCape = l.firstOrNull { it.id == Items.FIRE_CAPE_L_BROKEN }
            // K01 deel 5: the kits go to the victim's gravestone - the AGS (or)'s kit and the carried Fury ornament kit; never to the killer.
            val lowGrave = b.gravestone.toList().filterNotNull().map { it.id }
            listOf(Items.ARMADYL_GODSWORD_ORNAMENT_KIT, Items.FURY_ORNAMENT_KIT).forEach {
                run.expectTrue(e, "PvP <=20: ${name(run, it)} waits in the victim's gravestone", it in lowGrave && !has(l, it), "gravestone=$lowGrave")
            }
            run.expectTrue(e, "PvP <=20: the AGS (or) is gone from the victim", !has(l, Items.ARMADYL_GODSWORD_OR), "held=${l.map { it.id }}")
            run.expectTrue(e, "PvP <=20: Fire cape (l) stays as Fire cape (l) (broken), still locked", lockedCape != null && UntradeableDeathProtection.isDamaged(lockedCape) && !has(l, Items.FIRE_CAPE_LOCKED_22324), "held=${l.map { it.id }}")
            listOf(
                Items.VOID_KNIGHT_TOP_L to Items.VOID_KNIGHT_TOP_L_BROKEN, Items.DRAGON_DEFENDER_L to Items.DRAGON_DEFENDER_L_BROKEN,
                Items.VOID_KNIGHT_DEFLECTOR_L to Items.VOID_KNIGHT_DEFLECTOR_L_BROKEN,
            ).forEach { (item, broken) ->
                run.expectTrue(e, "PvP <=20: ${name(run, item)} stays as ${name(run, broken)}", has(l, broken) && !has(l, item), "held=${l.map { it.id }}")
            }
            run.expectTrue(e, "PvP <=20: the max cape is kept whole", has(l, Items.MAX_CAPE), "held=${l.map { it.id }}")
            run.expectTrue(e, "PvP <=20: the rune pouch is kept", has(l, Items.RUNE_POUCH), "held=${l.map { it.id }}")
            listOf(Items.COLOSSAL_BLADE, Items.HOLY_BOOK, Items.SARADOMIN_CAPE, Items.GLOVES_7462).forEach {
                run.expectTrue(e, "PvP <=20: ${name(run, it)} is gone from the victim", !has(l, it), "held=${l.map { i -> i.id }}")
            }
            val offered = PerduLostProperty.available(b).map { it.itemId }
            listOf(Items.COLOSSAL_BLADE, Items.HOLY_BOOK, Items.SARADOMIN_CAPE).forEach {
                run.expectTrue(e, "PvP <=20: Perdu sells the ${name(run, it)} back", it in offered, "Perdu offers $offered")
            }
            // Owner 2026-10-24 (B): every other lost untradeable can be bought back from Perdu too, for its store value.
            val lowReclaims = PerduReclaim.records(b).map { it.itemId }
            run.expectTrue(e, "PvP <=20: Perdu sells the Barrows gloves back (any lost untradeable)", Items.GLOVES_7462 in lowReclaims, "reclaims=$lowReclaims")
            run.expectTrue(e, "PvP <=20: a Lost Property item is not listed twice", Items.COLOSSAL_BLADE !in lowReclaims, "reclaims=$lowReclaims")
            // Perdu repairs every broken item back to the whole one, at its repair price.
            l.filter { UntradeableDeathProtection.isDamaged(it) }.forEach {
                val back = UntradeableDeathProtection.repaired(it)
                run.expectTrue(e, "PvP <=20: Perdu repairs ${name(run, it.id)} into ${name(run, back.id)}", !UntradeableDeathProtection.isDamaged(back) && UntradeableDeathProtection.repairCost(run.world.definitions, it) > 0,
                    "cost=${UntradeableDeathProtection.repairCost(run.world.definitions, it)}")
            }
        }

        // ---- PvP above level 20 ----
        val d = pvpKill("PvP >20", deep)
        if (d != null) {
            listOf(Items.FIRE_CAPE to Items.FIRE_CAPE_BROKEN, Items.RUNE_DEFENDER to Items.RUNE_DEFENDER_BROKEN, Items.DRAGON_DEFENDER to Items.DRAGON_DEFENDER_BROKEN)
                .forEach { (item, broken) -> run.expectTrue(e, "PvP >20: ${name(run, item)} breaks there too (OSRS broken table)", has(d, broken), "held=${d.map { it.id }}") }
            val lockedCape = d.firstOrNull { TrouverDamaged.isMangledFireCape(it) }
            run.expectTrue(e, "PvP >20: Fire cape (l) stays, mangled and locked", lockedCape != null, "held=${d.map { "${it.id}" }}")
            listOf(
                Items.VOID_KNIGHT_TOP_L to Items.VOID_KNIGHT_TOP_L_MANGLED, Items.DRAGON_DEFENDER_L to Items.DRAGON_DEFENDER_L_MANGLED,
                Items.VOID_KNIGHT_DEFLECTOR_L to Items.VOID_KNIGHT_DEFLECTOR_L_MANGLED,
            ).forEach { (item, mangled) ->
                run.expectTrue(e, "PvP >20: ${name(run, item)} stays as ${name(run, mangled)}", has(d, mangled) && !has(d, item), "held=${d.map { it.id }}")
            }
            val reclaims = PerduReclaim.records(b).map { it.itemId }
            listOf(Items.VOID_KNIGHT_TOP, Items.ELITE_VOID_KNIGHT_TOP, Items.FIGHTER_TORSO, Items.INFERNAL_CAPE, Items.VOID_KNIGHT_DEFLECTOR_19712).forEach {
                run.expectTrue(e, "PvP >20: ${name(run, it)} is destroyed and Perdu gives it back", !has(d, it) && it in reclaims, "reclaims=$reclaims")
            }
            run.expectTrue(e, "PvP >20: the max cape is kept whole", has(d, Items.MAX_CAPE), "held=${d.map { it.id }}")
            val offered = PerduLostProperty.available(b).map { it.itemId }
            listOf(Items.COLOSSAL_BLADE, Items.HOLY_BOOK, Items.SARADOMIN_CAPE).forEach {
                run.expectTrue(e, "PvP >20: Perdu sells the ${name(run, it)} back", it in offered, "Perdu offers $offered")
            }
        }

        // ---- killed by a monster (outside the Wilderness and guarded zones) ----
        fun open(t: Tile) = (-1..2).all { dx -> (-1..2).all { dz -> !runCatching { run.world.collision.isClipped(t.transform(dx, dz)) }.getOrDefault(true) } }
        val origin = run.origin
        val spot = (6..80 step 2).flatMap { r -> listOf(origin.transform(-r, r), origin.transform(r, r), origin.transform(-r, -r), origin.transform(r, -r)) }
            .firstOrNull { open(it) && !gg.rsmod.plugins.content.mechanics.pvp.GuardedZones.contains(it) && it.getWildernessLevel() == 0 }
        if (spot == null) {
            run.notTested(e, "monster death", "no open spot")
            return
        }
        val giant = run.npc(gg.rsmod.plugins.api.cfg.Npcs.HILL_GIANT, spot.transform(1, 0), "UN")
        prepare(b, spot)
        ITEMS.forEach { b.inventory.add(it, 1) }
        b.skills.setBaseLevel(Skills.DEFENCE, 1)
        b.setCurrentLifepoints(3)
        giant.attack(b)
        var died = false
        for (i in 0 until 120) {
            giant.setCurrentLifepoints(giant.getMaximumLifepoints())
            if (i % 10 == 9 && !died) giant.attack(b)
            wait(1)
            if (b.isDead()) died = true
            if (died && !b.isDead()) break
        }
        giant.resetInteractions()
        Combat.reset(giant)
        wait(4)
        b.skills.setBaseLevel(Skills.DEFENCE, 99)
        run.expectTrue(e, "monster: the npc killed him", died, "dead=${b.isDead()}")
        if (!died) return
        val held = (b.inventory.toList() + b.equipment.toList()).filterNotNull()
        val grave = b.gravestone.toList().filterNotNull().map { it.id }
        // N593 (owner 2026-11-05 15:5x, replaces the 10-04 rule 'a lockable is always kept on a monster death'): like OSRS only an
        // actually LOCKED (l) item is an extra keep; an unlocked lockable is one of the 3 most valuable or waits in the gravestone.
        listOf(Items.FIRE_CAPE_LOCKED_22324, Items.VOID_KNIGHT_TOP_L, Items.DRAGON_DEFENDER_L, Items.VOID_KNIGHT_DEFLECTOR_L).forEach {
            run.expectTrue(e, "monster: locked ${name(run, it)} is kept whole", held.any { h -> h.id == it && !UntradeableDeathProtection.isDamaged(h) }, "held=${held.map { h -> h.id }}")
        }
        // N503 D2 (owner 2026-11-01): an unskulled monster death keeps the 3 most valuable items - so these may be kept instead of
        // waiting in the gravestone, but each is in one of the two places and at most 3 of them are kept.
        val graveOrKept = listOf(Items.COLOSSAL_BLADE, Items.HOLY_BOOK, Items.SARADOMIN_CAPE, Items.GLOVES_7462,
            Items.VOID_KNIGHT_TOP, Items.ELITE_VOID_KNIGHT_TOP, Items.FIGHTER_TORSO, Items.FIRE_CAPE, Items.RUNE_DEFENDER, Items.DRAGON_DEFENDER,
            Items.INFERNAL_CAPE, Items.RUNE_POUCH, Items.MAX_CAPE, Items.VOID_KNIGHT_DEFLECTOR_19712)
        graveOrKept.forEach {
            run.expectTrue(e, "monster: ${name(run, it)} waits in the gravestone or is one of the 3 kept", it in grave || held.any { h -> h.id == it }, "gravestone=$grave held=${held.map { h -> h.id }}")
        }
        run.expectTrue(e, "monster: at most 3 of them kept (D2)", graveOrKept.count { id -> held.any { it.id == id } } <= 3, "held=${held.map { it.id }}")
        gg.rsmod.plugins.content.mechanics.death.Gravestone.clear(b)
        b.gravestone.removeAll()
    }

    private fun name(
        run: Conformance.Run,
        id: Int,
    ): String = run.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, id).name

    private object TrouverDamaged {
        fun isMangledFireCape(item: Item): Boolean =
            (item.id == Items.FIRE_CAPE_LOCKED_22324 && UntradeableDeathProtection.state(item) == UntradeableDeathProtection.STATE_MANGLED) ||
                gg.rsmod.plugins.content.mechanics.trouver.TrouverRegistry.entryForDamaged(item.id)?.lockedItemId == Items.FIRE_CAPE_LOCKED_22324
    }
}
