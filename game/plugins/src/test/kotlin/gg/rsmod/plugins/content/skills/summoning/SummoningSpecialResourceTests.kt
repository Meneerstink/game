package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.BANK_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.plugins.api.Skills
import io.mockk.every
import io.mockk.mockk
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Roster-wide **resource accounting** for familiar special moves.
 *
 * ## What this covers that the per-move tests do not
 *
 * `SummoningSpecialMoveTests` and `FamineSpecialMoveTests` prove the *effect* of nine specific
 * special moves. That leaves the large majority of the bound moves with no proof at all about the
 * thing that actually costs the player something: what they consume.
 *
 * Proving 73 individual effects is a different and much larger job, and several of them need world
 * state a unit test cannot honestly build. But the **accounting invariant** is the same for every
 * one of them and is provable generically, so it is proved here for every instant special on the
 * roster rather than for a chosen example:
 *
 *  * a cast that **succeeds** consumes exactly one scroll and exactly the scroll's own point cost;
 *  * a cast that **fails** consumes nothing at all — no scroll, no points;
 *  * a cast with **no scroll** always fails and always consumes nothing;
 *  * a cast with **insufficient points** always fails and always consumes nothing.
 *
 * Those four are the duplication, the free cast and the double-charge, which are the faults that
 * matter most and the ones a per-move effect test would not catch anyway.
 *
 * ## Why only the instant moves
 *
 * An instant special is the only kind that can be cast from a unit test without inventing world
 * state: the targeted ones need a live npc, player or item selection, and a stub for those would be
 * testing the stub. Restricting the sweep is a limit on coverage, stated plainly, rather than a
 * weaker assertion pretending to be a full one. The targeted moves' accounting is reachable the
 * same way once the run has a harness that can supply real targets.
 */
class SummoningSpecialResourceTests {
    /** Every familiar whose special move fires without a target. */
    private fun instantFamiliars(): List<SummoningPouchData> =
        SummoningPouchData.values().filter { pouch ->
            FamiliarCapabilityTable.forNpc(pouch.npc)?.specialTarget == FamiliarSpecialTarget.INSTANT
        }

    /** The scroll a given familiar's special actually consumes. */
    private fun scrollFor(pouch: SummoningPouchData): SummoningScrollData? {
        val binding = FamiliarCapabilityTable.forNpc(pouch.npc)?.special ?: return null
        return binding.scrolls.firstOrNull { pouch.npc in it.familiars } ?: binding.scroll
    }

    @Test
    fun `the sweep actually covers a meaningful slice of the roster`() {
        // Guards against this whole class silently becoming a no-op if the target modes are ever
        // re-derived and nothing resolves to INSTANT any more.
        val instants = instantFamiliars()
        assertTrue(
            instants.size >= 20,
            "expected a substantial number of instant specials, found ${instants.size}",
        )
        instants.forEach { pouch ->
            assertNotNull(scrollFor(pouch), "${pouch.name} is instant but resolves no scroll")
        }
    }

    @Test
    fun `a successful instant cast consumes exactly one scroll and exactly its point cost`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.inventory.add(scroll.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            val fired = SummoningSpecialMoves.castInstant(player)
            val scrollsLeft = player.inventory.getItemCount(scroll.scroll)
            val pointsLeft = Familiar.currentSpecialPoints(player)

            if (fired) {
                if (scrollsLeft != 4) {
                    offenders += "${pouch.name}: succeeded but consumed ${5 - scrollsLeft} scrolls, expected 1"
                }
                val spent = Familiar.MAX_SPECIAL_POINTS - pointsLeft
                if (spent != scroll.specialPoints) {
                    offenders += "${pouch.name}: succeeded but spent $spent points, expected ${scroll.specialPoints}"
                }
            } else {
                // A refusal is legitimate - "you are already at full life points" and the like -
                // but it must be free.
                if (scrollsLeft != 5) {
                    offenders += "${pouch.name}: failed but still consumed ${5 - scrollsLeft} scrolls"
                }
                if (pointsLeft != Familiar.MAX_SPECIAL_POINTS) {
                    offenders += "${pouch.name}: failed but still spent " +
                        "${Familiar.MAX_SPECIAL_POINTS - pointsLeft} points"
                }
            }
        }
        assertEquals(emptyList<String>(), offenders, "special-move resource accounting broken")
    }

    @Test
    fun `repeated instant casts exhaust the persisted special-point pool instead of staying free`() {
        val pouch = SummoningPouchData.GRANITE_CRAB
        val scroll = SummoningScrollData.STONY_SHELL_SCROLL
        val player = newPlayer(pouch.npc)
        player.inventory.add(scroll.scroll, 6)
        player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS
        player.skills.setCurrentLevel(Skills.DEFENCE, 1)

        // Stony Shell costs twelve points. Starting from 60 therefore gives exactly five casts;
        // calling updateHud between casts also guards the live tick path from restoring a stale bar.
        repeat(Familiar.MAX_SPECIAL_POINTS / scroll.specialPoints) {
            assertTrue(SummoningSpecialMoves.castInstant(player), "cast ${it + 1} should succeed")
            Familiar.updateHud(player)
        }

        assertEquals(0, Familiar.currentSpecialPoints(player))
        assertEquals(1, player.inventory.getItemCount(scroll.scroll))
        assertEquals(false, SummoningSpecialMoves.castInstant(player))
        assertEquals(0, Familiar.currentSpecialPoints(player))
        assertEquals(1, player.inventory.getItemCount(scroll.scroll))
    }

    /**
     * Owner live failure: special-move scrolls could be spammed. Void starts a 3-tick
     * `familiar_special_delay` clock per cast; inside it every further cast of every instant
     * special is refused and free, and the successful cast sends Void's player animation 7660,
     * graphic 1316 and cast sound 4161.
     */
    @Test
    fun `every instant special is refused and free inside the special-move delay and plays the cast sound`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            val timers = gg.rsmod.game.model.timer.TimerMap()
            every { player.timers } returns timers
            player.inventory.add(scroll.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            val first = SummoningSpecialMoves.castInstant(player)
            if (!timers.has(SummoningSpecialMoves.SPECIAL_MOVE_DELAY_TIMER)) {
                if (pouch.name in DELAY_STARTS_ON_TELEPORT) return@forEach
                if (first || "${pouch.name} (${scroll.name})" !in CONDITIONAL_ON_UNPROVIDED_STATE) {
                    offenders += "${pouch.name}: no special-move delay started"
                }
                return@forEach
            }
            val scrolls = player.inventory.getItemCount(scroll.scroll)
            val points = Familiar.currentSpecialPoints(player)
            if (SummoningSpecialMoves.castInstant(player)) offenders += "${pouch.name}: second cast inside the delay succeeded"
            if (player.inventory.getItemCount(scroll.scroll) != scrolls || Familiar.currentSpecialPoints(player) != points) {
                offenders += "${pouch.name}: refused cast inside the delay consumed resources"
            }
            if (first) {
                io.mockk.verify(exactly = 1) { player.animate(SummoningSpecialMoves.SPECIAL_CAST_ANIMATION) }
                io.mockk.verify(exactly = 1) { player.graphic(SummoningSpecialMoves.SPECIAL_CAST_GRAPHIC) }
                io.mockk.verify(exactly = 1) {
                    player.write(
                        gg.rsmod.game.message.impl.SynthSoundMessage(
                            sound = SummoningSpecialMoves.SPECIAL_CAST_SOUND,
                            loops = 1,
                            delay = 0,
                            volume = FamiliarAudio.SERVER_SOUND_VOLUME,
                        ),
                    )
                }
            }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `no instant special can be cast without its scroll, and none charges points for the refusal`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast with no ${scroll.name} in the inventory"
            }
            if (Familiar.currentSpecialPoints(player) != Familiar.MAX_SPECIAL_POINTS) {
                offenders += "${pouch.name}: charged points for a cast it had no scroll for"
            }
        }
        assertEquals(emptyList<String>(), offenders, "scroll requirement not enforced")
    }

    @Test
    fun `no instant special can be cast without enough points, and none consumes a scroll for the refusal`() {
        val offenders = mutableListOf<String>()
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            val player = newPlayer(pouch.npc)
            player.inventory.add(scroll.scroll, 5)
            // One point short of the cost: the boundary, not an arbitrary low value.
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = scroll.specialPoints - 1

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast at ${scroll.specialPoints - 1} points, costing ${scroll.specialPoints}"
            }
            if (player.inventory.getItemCount(scroll.scroll) != 5) {
                offenders += "${pouch.name}: consumed a scroll for a cast it could not afford"
            }
        }
        assertEquals(emptyList<String>(), offenders, "point cost not enforced")
    }

    @Test
    fun `a familiar cannot cast another familiar's special move`() {
        val offenders = mutableListOf<String>()
        val instants = instantFamiliars()
        instants.forEach { pouch ->
            val ownScroll = scrollFor(pouch) ?: return@forEach
            // Any other familiar's scroll, which this one must refuse.
            val foreign = instants.firstNotNullOfOrNull { other ->
                scrollFor(other)?.takeIf { it.scroll != ownScroll.scroll }
            } ?: return@forEach

            val player = newPlayer(pouch.npc)
            player.inventory.add(foreign.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

            if (SummoningSpecialMoves.castInstant(player)) {
                offenders += "${pouch.name}: cast using ${foreign.name}, which is not its scroll"
            }
            if (player.inventory.getItemCount(foreign.scroll) != 5) {
                offenders += "${pouch.name}: consumed ${foreign.name}, another familiar's scroll"
            }
        }
        assertEquals(emptyList<String>(), offenders, "familiars can consume scrolls that are not theirs")
    }

    /**
     * The **targeted** half of the roster, covered on the axis a unit test can honestly reach.
     *
     * A targeted special cannot be fired end-to-end here — `castOnNpc` requires multi-combat tiles
     * and a real `canAttack` ruling, and stubbing those would test the stub. What *is* reachable,
     * and is worth pinning, is the **entry-point mismatch**: sending a targeted special down the
     * instant path, or an instant special down the item path, must be refused **before** anything
     * is committed.
     *
     * That ordering is the whole point. `castInstant` rejects a mismatched target mode above its
     * call to `validateResources`; if a refactor ever moved the resource check first, every
     * targeted familiar would burn a scroll and its points on a click that then did nothing. This
     * test fails the moment that ordering inverts.
     */
    @Test
    fun `a special cast through the wrong entry point is refused before anything is consumed`() {
        val offenders = mutableListOf<String>()
        SummoningPouchData.values().forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc) ?: return@forEach
            val mode = capabilities.specialTarget ?: return@forEach
            val scroll = scrollFor(pouch) ?: return@forEach

            if (mode != FamiliarSpecialTarget.INSTANT) {
                val player = newPlayer(pouch.npc)
                player.inventory.add(scroll.scroll, 5)
                player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS
                if (SummoningSpecialMoves.castInstant(player)) {
                    offenders += "${pouch.name}: a $mode special fired through the instant path"
                }
                if (player.inventory.getItemCount(scroll.scroll) != 5) {
                    offenders += "${pouch.name}: consumed a scroll for a mismatched instant cast"
                }
                if (Familiar.currentSpecialPoints(player) != Familiar.MAX_SPECIAL_POINTS) {
                    offenders += "${pouch.name}: spent points for a mismatched instant cast"
                }
            }

            if (mode != FamiliarSpecialTarget.INVENTORY_ITEM) {
                val player = newPlayer(pouch.npc)
                player.inventory.add(scroll.scroll, 5)
                // Slot 1 holds something that is not the scroll, so the refusal is about the target
                // mode rather than about the familiar refusing to bank its own scroll.
                player.inventory.add(scroll.scroll, 1)
                player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS
                val before = player.inventory.getItemCount(scroll.scroll)
                if (SummoningSpecialMoves.castOnInventoryItem(player, slot = 0)) {
                    offenders += "${pouch.name}: a $mode special fired through the item-target path"
                }
                if (player.inventory.getItemCount(scroll.scroll) != before) {
                    offenders += "${pouch.name}: consumed a scroll for a mismatched item cast"
                }
                if (Familiar.currentSpecialPoints(player) != Familiar.MAX_SPECIAL_POINTS) {
                    offenders += "${pouch.name}: spent points for a mismatched item cast"
                }
            }
        }
        assertEquals(emptyList<String>(), offenders, "target-mode mismatch is not refused cleanly")
    }

    /**
     * **No instant special may charge the player and then do nothing.**
     *
     * Effects are individually proven for only nine of the bound moves. Proving the remaining
     * effects one by one is a much larger job and several need world state a unit test cannot
     * honestly build — but there is one property that holds for *all* of them and that catches the
     * worst outcome: a move that takes a scroll and its points and changes no observable state at
     * all. That is a silent theft of resources, and it is exactly what an unimplemented or
     * mis-wired branch in the big `when (scroll)` dispatch looks like from the player's side.
     *
     * The check is deliberately about *whether* state moved, not *by how much*: asserting the
     * amounts here would mean copying the per-move constants out of the implementation into the
     * test, which proves only that two copies of a number match. The amounts belong in the
     * individually-sourced per-move tests.
     *
     * Observable state, for the purposes of this test, is every skill's current level plus
     * lifepoints and run energy — the things an instant familiar special can move without a target.
     * A move whose real effect is outside that set (spawning a ground item, opening an interface)
     * is exempted by name, with the reason, rather than being allowed to weaken the rule for
     * everything else.
     */
    @Test
    fun `no instant special consumes resources without changing observable state`() {
        val offenders = mutableListOf<String>()
        var provenEffective = 0
        var swept = 0
        val refused = mutableListOf<String>()
        var fired = 0
        instantFamiliars().forEach { pouch ->
            val scroll = scrollFor(pouch) ?: return@forEach
            if (scroll in UNOBSERVABLE_IN_THIS_HARNESS) return@forEach
            swept++

            val player = newPlayer(pouch.npc)
            player.inventory.add(scroll.scroll, 5)
            player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS
            // Leave room for a heal or a boost to be visible: a player already at maximum
            // everything cannot show a change, and would make this test vacuous.
            for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
                player.skills.setCurrentLevel(skill, 50)
            }
            val before = observableState(player, scroll.scroll)

            if (SummoningSpecialMoves.castInstant(player)) {
                fired++
                if (observableState(player, scroll.scroll) == before) {
                    offenders += "${pouch.name} (${scroll.name}): consumed a scroll and " +
                        "${scroll.specialPoints} points but changed nothing observable"
                } else {
                    provenEffective++
                }
            } else {
                refused += "${pouch.name} (${scroll.name})"
            }
        }
        assertEquals(emptyList<String>(), offenders, "special moves are charging for no effect")
        /*
         * Without this the test could pass by proving nothing - every move exempted, or every cast
         * refused. It has to actually witness moves *working*.
         *
         * 14 is the number observed today, recorded as a floor rather than chosen as a target: it
         * can only be tightened. It rose from 12 when the harness was given real lifepoints, which
         * is what let the heal-based moves - Healing Aura among them - actually run at all.
         *
         */
        assertTrue(
            provenEffective >= 14,
            "only $provenEffective of $swept swept instant specials were observed changing state " +
                "($fired fired, refused: $refused); this test has stopped proving anything",
        )
        /*
         * Every move that fired changed state, so the sweep's shortfall is entirely moves that
         * **declined to fire** - not moves that fired and did nothing. Each is conditional on
         * something this harness deliberately does not provide, and the set is pinned so a *new*
         * silent refusal, a move that quietly stops working, cannot hide inside the same shortfall.
         */
        assertEquals(
            CONDITIONAL_ON_UNPROVIDED_STATE,
            refused.toSet(),
            "the set of instant specials that decline to fire has changed; a move that used to " +
                "work may have silently stopped, or a newly conditional one needs documenting",
        )
        assertEquals(
            swept,
            provenEffective + refused.size,
            "a move both fired and changed nothing; the offender list above should have caught it",
        )
    }

    /**
     * Everything an instant familiar special can move that this harness can actually see: every
     * skill's current level, lifepoints, and the contents of both the player's inventory and the
     * familiar's own store (which is where a forager special such as Cheese Feast puts its items).
     *
     * The inventory is snapshotted *excluding the scroll being consumed*, because consuming the
     * scroll is the cost, not the effect — counting it would make every move look effective.
     */
    private fun observableState(
        player: Player,
        consumedScroll: Int,
    ): List<Int> {
        val skills = (0 until SkillSet.DEFAULT_SKILL_COUNT).map { player.skills.getCurrentLevel(it) }
        val inventory =
            (0 until player.inventory.capacity).map { slot ->
                player.inventory[slot]?.takeIf { it.id != consumedScroll }?.let { it.id * 31 + it.amount } ?: 0
            }
        val store =
            BeastOfBurden.activeKey(player)?.let { key ->
                val container = player.containers.getOrPut(key) { ItemContainer(SummoningTestCache.definitions, key) }
                (0 until container.capacity).map { slot ->
                    container[slot]?.let { it.id * 31 + it.amount } ?: 0
                }
            }.orEmpty()
        // Venom Shot's effect is a charge on the owner's next ranged hit (SummoningSpecialMoves.VENOM_SHOT_CHARGED_ATTR).
        val charges = listOf(if (player.attr[SummoningSpecialMoves.VENOM_SHOT_CHARGED_ATTR] == true) 1 else 0)
        return skills + listOf(player.getCurrentLifepoints()) + inventory + store + charges
    }

    @Test
    fun `with no familiar summoned nothing is ever consumed`() {
        val scroll = instantFamiliars().firstNotNullOfOrNull { scrollFor(it) }!!
        val player = newPlayer(familiarNpcId = null)
        player.inventory.add(scroll.scroll, 5)
        player.attr[FAMILIAR_SPECIAL_POINTS_ATTR] = Familiar.MAX_SPECIAL_POINTS

        assertEquals(false, SummoningSpecialMoves.castInstant(player))
        assertEquals(5, player.inventory.getItemCount(scroll.scroll))
        assertEquals(Familiar.MAX_SPECIAL_POINTS, Familiar.currentSpecialPoints(player))
    }

    private fun newPlayer(familiarNpcId: Int?): Player {
        val world = mockk<World>(relaxed = true)
        // Real npc update-block table: a relaxed mock returns Objects that break Npc.addBlock.
        every { world.npcUpdateBlocks } returns SummoningTestCache.npcUpdateBlocks
        every { world.definitions } returns DEFINITIONS
        val npcs = PawnList(arrayOfNulls<Npc>(10))
        every { world.npcs } returns npcs
        every { world.gameContext.cycleTime } returns 600
        val skills = SkillSet(SkillSet.DEFAULT_SKILL_COUNT)
        for (skill in 0 until SkillSet.DEFAULT_SKILL_COUNT) {
            skills.setBaseLevel(skill, 99)
            skills.setCurrentLevel(skill, 99)
        }
        val player = mockk<Player>(relaxed = true)
        every { player.attr } returns AttributeMap()
        every { player.world } returns world
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.bank } returns ItemContainer(DEFINITIONS, BANK_KEY)
        every { player.containers } returns HashMap()
        every { player.skills } returns skills
        every { player.tile } returns Tile(0, 0, 0)
        /*
         * Real lifepoints, backed by a variable.
         *
         * Without this every heal-based special is invisible: `Player.heal` is an extension that
         * delegates to `Player.alterLifepoints`, a member the relaxed mock swallows, and
         * `getCurrentLifepoints`/`getMaximumLifepoints` both answer 0 — so a heal correctly reports
         * "you are already at full life points" and the move looks like a no-op.
         *
         * Only the three accessors are stubbed. `alterLifepoints` itself is delegated back to the
         * real implementation with `callOriginal()`, so the production capping and signum logic is
         * what runs; reimplementing it here would mean asserting against a copy of the code rather
         * than against the code.
         */
        var lifepoints = HALF_LIFEPOINTS
        every { player.getMaximumLifepoints() } returns MAX_LIFEPOINTS
        every { player.getCurrentLifepoints() } answers { lifepoints }
        every { player.setCurrentLifepoints(any()) } answers { lifepoints = firstArg() }
        every { player.alterLifepoints(any(), any()) } answers { callOriginal() }
        if (familiarNpcId != null) {
            val npc = mockk<Npc>(relaxed = true)
            every { npc.id } returns familiarNpcId
            every { npc.tile } returns Tile(0, 0, 0)
            every { npc.world } returns world
            every { npc.attr } returns AttributeMap()
            every { npc.index } returns 0
            npcs.entries[0] = npc
            player.attr[FAMILIAR_ATTR] = WeakReference(npc)
        }
        return player
    }

    companion object {
        /**
         * The instant specials that legitimately decline to fire in this harness, each because it
         * needs world or inventory state the harness deliberately does not provide. None of these
         * is broken — every one of them is a real precondition.
         *
         * Pinned as an exact set, so a move that *silently stops working* cannot hide among them.
         *
         * | Familiar | Why it declines |
         * |---|---|
         * | Spirit tz-kih, Spirit kalphite, Giant chinchompa, Smoke devil | area-of-effect moves with no npcs in range of the mock world |
         * | Pyrelord | Immense Heat needs a gold bar; its effect is proven directly in `SummoningSpecialMoveTests` |
         * | Abyssal titan | Essence Shipment needs essence already in the familiar's store |
         */
        private val CONDITIONAL_ON_UNPROVIDED_STATE =
            setOf(
                "SPIRIT_TZ_KIH (FIREBALL_ASSAULT_SCROLL)",
                "SPIRIT_KALPHITE (SANDSTORM_SCROLL)",
                "GIANT_CHINCHOMPA (EXPLODE_SCROLL)",
                "PYRELORD (IMMENSE_HEAT_SCROLL)",
                "SMOKE_DEVIL (DUST_CLOUD_SCROLL)",
                "ABYSSAL_TITAN (ESSENCE_SHIPMENT_SCROLL)",
            )

        /**
         * Call to Arms goes through the shared teleport gate (canTeleport + Deadman route revalidation, WORK_QUEUE 1.0): the
         * special-move delay starts when that teleport completes, which a mocked world never runs.
         */
        private val DELAY_STARTS_ON_TELEPORT =
            setOf("VOID_RAVAGER", "VOID_SHIFTER", "VOID_SPINNER", "VOID_TORCHER")

        /** Hitpoints 99 on the server's 1:1 scale. */
        private const val MAX_LIFEPOINTS = 99

        /** Start injured so a heal has room to be visible; a full-health player cannot show one. */
        private const val HALF_LIFEPOINTS = 49

        /**
         * Instant specials whose real effect this **harness** cannot observe. Each is listed with
         * the reason, because the distinction matters: none of these is a move that does nothing,
         * they are moves whose effect lands somewhere a mocked world does not record.
         *
         * The first pass of this test flagged five of them as offenders. That was the test being
         * wrong, not the code — Herbcall in particular already has its effect proven directly in
         * `SummoningSpecialMoveTests`. Widening what counts as observable (the inventory and the
         * familiar's own container are now included) reclaimed one of them; the rest are genuine
         * harness limits and are named rather than quietly tolerated.
         */
        private val UNOBSERVABLE_IN_THIS_HARNESS =
            mapOf(
                // world.spawn(GroundItem(...)) on a relaxed mock World is swallowed.
                SummoningScrollData.EGG_SPAWN_SCROLL to "spawns ground items",
                SummoningScrollData.HERBCALL_SCROLL to "spawns a ground item",
                SummoningScrollData.FISH_RAIN_SCROLL to "spawns ground items",
                SummoningScrollData.FRUITFALL_SCROLL to "spawns ground items",
                // Player.runEnergy is a mocked property; a write to it is recorded by mockk but
                // the getter still answers its default, so a restore is invisible here.
                SummoningScrollData.UNBURDEN_SCROLL to "restores run energy, a mocked property",
                // Moves the player; Player.tile is stubbed to a constant.
                SummoningScrollData.CALL_TO_ARMS_SCROLL to "teleports the player",
            )

        /**
         * Shared rather than a private copy: 38 test classes in this module each loaded their own
         * full `DefinitionSet` into a companion object, and adding a 39th produced an
         * `OutOfMemoryError` in an unrelated class. See [SummoningTestCache].
         */
        private val DEFINITIONS get() = SummoningTestCache.definitions
    }
}
