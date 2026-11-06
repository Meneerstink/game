package gg.rsmod.plugins.content.mechanics.prayer

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.game.fs.def.StructDef
import gg.rsmod.game.fs.def.VarbitDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.PROTECT_ITEM_ATTR
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.ACTIVE_COMBAT_TIMER
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.varp.VarpSet
import gg.rsmod.game.tools.importer.RuinousPowersCacheTool
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.getVarbit
import gg.rsmod.plugins.api.ext.setVarbit
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** N463 Ruinous Powers: the cache rows the tab draws and the server rules behind every click (owner 2026-10-31). */
class RuinousPowersTests {
    companion object {
        private val definitions: DefinitionSet by lazy {
            val store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DefinitionSet().apply {
                load(store, VarbitDef::class.java)
                load(store, EnumDef::class.java)
                load(store, StructDef::class.java)
                load(store, SpotAnimDef::class.java)
            }
        }
    }

    private fun player(
        prayerLevel: Int = 99,
        points: Int = 99,
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns definitions
        every { world.currentCycle } returns 100
        lastWorld = world
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.timers } returns TimerMap()
        every { player.varps } returns VarpSet((0..9000).toSet())
        every { player.varcs } returns MutableList(2000) { 0 }
        every { player.skills } returns SkillSet(25).apply { setBaseLevel(Skills.PRAYER, prayerLevel) }
        every { player.isDead() } returns false
        every { player.lock.canUsePrayer() } returns true
        every { player.lock.delaysPrayer() } returns false
        every { player.getCurrentPrayerPoints() } returns points
        var icon = -1
        every { player.prayerIcon } answers { icon }
        every { player.prayerIcon = any() } answers { icon = firstArg() }
        // N490: mechanics fixtures represent a player who completed DT2 and consumed the drop.
        gg.rsmod.plugins.content.quests.foundation.DesertTreasureII.setStage(player, gg.rsmod.plugins.content.quests.foundation.DesertTreasureII.completedValue)
        player.attr[RuinousUnlocks.UNLOCKED] = true
        AncientCurses.switchBook(player, AncientCurses.PrayerBook.RUINOUS)
        return player
    }

    /** The world of the last [player]; stubbed directly (re-stubbing through `p.world` would replace its definitions). */
    private lateinit var lastWorld: World

    @Test
    fun `the cache draws the 24 prayers in the OSRS order with their own level, name and varbits`() {
        assertEquals(RuinousPowersCacheTool.CELLS.size, RuinousPrayer.values.size)
        val enum = definitions.get(EnumDef::class.java, RuinousPowersCacheTool.ENUM_ID)
        RuinousPrayer.values.forEachIndexed { slot, prayer ->
            val cell = RuinousPowersCacheTool.CELLS[slot]
            assertEquals(cell.name, prayer.named)
            assertEquals(cell.level, prayer.level)
            val struct = definitions.get(StructDef::class.java, enum.getInt(slot))
            assertEquals(RuinousPowersCacheTool.STRUCT_BASE + slot, struct.id)
            assertEquals(prayer.level, struct.getInt(737))
            // N503 D1 (owner 2026-11-01): ProtectItemHighRiskCacheTool replaced the Protect Item line in the cache.
            val text = if (prayer == RuinousPrayer.PROTECT_ITEM) gg.rsmod.game.tools.importer.ProtectItemHighRiskCacheTool.DISABLED_LINE else cell.text
            assertEquals("Level ${prayer.level}<br>${prayer.named}<br>$text", struct.getString(734))
            val active = definitions.get(VarbitDef::class.java, prayer.varbit)
            assertEquals(RuinousPowersCacheTool.ACTIVE_VARP, active.varp)
            assertEquals(slot, active.startBit)
            val quick = definitions.get(VarbitDef::class.java, prayer.quickVarbit)
            assertEquals(RuinousPowersCacheTool.QUICK_VARP, quick.varp)
            assertEquals(slot, quick.startBit)
        }
        // Levels rise through the grid, as in the OSRS book (60 Rejuvenation ... 92 Intensify).
        assertEquals(RuinousPrayer.values.map { it.level }.sorted(), RuinousPrayer.values.map { it.level })
    }

    @Test
    fun `switching to the Ruinous book sets the curse layout and the Ruinous flag`() {
        val p = player()
        assertEquals(AncientCurses.PrayerBook.RUINOUS, AncientCurses.getBook(p))
        assertEquals(1, p.getVarbit(AncientCurse.BOOK_VARBIT))
        assertEquals(1, p.getVarbit(RuinousPowersCacheTool.BOOK_FLAG_VARBIT))
        AncientCurses.switchBook(p, AncientCurses.PrayerBook.NORMAL)
        assertEquals(0, p.getVarbit(AncientCurse.BOOK_VARBIT))
        assertEquals(0, p.getVarbit(RuinousPowersCacheTool.BOOK_FLAG_VARBIT))
    }

    @Test
    fun `offensive prayers, overheads and vows each allow one at a time - the rest stack`() {
        val p = player()
        RuinousPowers.click(p, RuinousPrayer.DECIMATE.slot)
        RuinousPowers.click(p, RuinousPrayer.REJUVENATION.slot)
        RuinousPowers.click(p, RuinousPrayer.INTENSIFY.slot)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.DECIMATE))
        assertTrue(RuinousPowers.isActive(p, RuinousPrayer.INTENSIFY))
        assertTrue(RuinousPowers.isActive(p, RuinousPrayer.REJUVENATION))

        RuinousPowers.click(p, RuinousPrayer.FUMUS_VOW.slot)
        RuinousPowers.click(p, RuinousPrayer.CRUOR_VOW.slot)
        assertEquals(listOf(RuinousPrayer.CRUOR_VOW), RuinousPowers.active(p).filter { it.group == RuinousPrayer.Group.VOW })

        RuinousPowers.click(p, RuinousPrayer.DAMPEN_MELEE.slot)
        RuinousPowers.click(p, RuinousPrayer.PURGE.slot)
        assertEquals(listOf(RuinousPrayer.PURGE), RuinousPowers.active(p).filter { it.group == RuinousPrayer.Group.OVERHEAD })
        assertEquals(PrayerIcon.RUINOUS_PURGE.id, p.prayerIcon)

        // A second click switches it off again.
        RuinousPowers.click(p, RuinousPrayer.PURGE.slot)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.PURGE))
        assertEquals(-1, p.prayerIcon)
    }

    @Test
    fun `a prayer above the player's Prayer level stays off`() {
        val p = player(prayerLevel = 81)
        RuinousPowers.click(p, RuinousPrayer.DECIMATE.slot)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.DECIMATE))
        RuinousPowers.click(p, RuinousPrayer.VINDICATION.slot)
        assertTrue(RuinousPowers.isActive(p, RuinousPrayer.VINDICATION))
    }

    @Test
    fun `Dampen shows its OSRS overhead and protects like a Protect prayer`() {
        val p = player()
        RuinousPowers.click(p, RuinousPrayer.DAMPEN_MELEE.slot)
        assertEquals(PrayerIcon.RUINOUS_DAMPEN_MELEE.id, p.prayerIcon)
        assertTrue(PrayerIcon.protectsAgainst(p.prayerIcon, CombatClass.MELEE))
        RuinousPowers.click(p, RuinousPrayer.DAMPEN_MAGIC.slot)
        assertTrue(PrayerIcon.protectsAgainst(p.prayerIcon, CombatClass.MAGIC))
        assertFalse(PrayerIcon.protectsAgainst(p.prayerIcon, CombatClass.MELEE))
        RuinousPowers.deactivateProtection(p)
        assertEquals(-1, p.prayerIcon)
    }

    @Test
    fun `the drain is the sum of the OSRS drain effects and every shutdown clears the book`() {
        val p = player()
        every { lastWorld.currentCycle } returns 200
        RuinousPowers.click(p, RuinousPrayer.TRINITAS.slot)
        RuinousPowers.click(p, RuinousPrayer.DAMPEN_RANGED.slot)
        every { lastWorld.currentCycle } returns 201
        assertEquals(220 + 140, RuinousPowers.drainEffect(p))
        Prayers.deactivateAll(p)
        assertTrue(RuinousPowers.active(p).isEmpty())
        assertEquals(-1, p.prayerIcon)
    }

    @Test
    fun `quick prayers keep the same exclusions and turn the set on and off`() {
        val p = player()
        RuinousPowers.selectQuick(p, RuinousPrayer.ANCIENT_STRENGTH.slot)
        RuinousPowers.selectQuick(p, RuinousPrayer.DECIMATE.slot)
        RuinousPowers.selectQuick(p, RuinousPrayer.DAMPEN_MELEE.slot)
        assertEquals(listOf(RuinousPrayer.DAMPEN_MELEE, RuinousPrayer.DECIMATE), RuinousPowers.selectedQuick(p))
        RuinousPowers.toggleQuick(p)
        assertEquals(setOf(RuinousPrayer.DAMPEN_MELEE, RuinousPrayer.DECIMATE), RuinousPowers.active(p).toSet())
        RuinousPowers.toggleQuick(p)
        assertTrue(RuinousPowers.active(p).isEmpty())
        // Too low a level cannot be selected.
        val low = player(prayerLevel = 70)
        RuinousPowers.selectQuick(low, RuinousPrayer.DAMPEN_MELEE.slot)
        assertTrue(RuinousPowers.selectedQuick(low).isEmpty())
    }

    @Test
    fun `N503 D1 - Ruinous Protect Item cannot be switched on or quick-selected`() {
        val p = player()
        RuinousPowers.click(p, RuinousPrayer.PROTECT_ITEM.slot)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.PROTECT_ITEM))
        assertTrue(p.attr[PROTECT_ITEM_ATTR] != true)
        RuinousPowers.activate(p, RuinousPrayer.PROTECT_ITEM)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.PROTECT_ITEM), "no route switches it on")
        RuinousPowers.selectQuick(p, RuinousPrayer.PROTECT_ITEM.slot)
        assertTrue(RuinousPowers.selectedQuick(p).isEmpty())
    }

    @Test
    fun `combat multipliers follow the N496 balance (owner 2026-10-31 - Ruinous above Curses above Normal)`() {
        val p = player()
        RuinousPowers.click(p, RuinousPrayer.DECIMATE.slot)
        assertEquals(1.30, RuinousPowers.meleeAttack(p))
        assertEquals(1.28, RuinousPowers.meleeStrength(p))
        assertEquals(1.0, RuinousPowers.rangedAttack(p))
        RuinousPowers.click(p, RuinousPrayer.ANNIHILATE.slot)
        assertEquals(1.30, RuinousPowers.rangedAttack(p))
        assertEquals(1.31, RuinousPowers.rangedStrength(p))
        RuinousPowers.click(p, RuinousPrayer.VAPORISE.slot)
        assertEquals(1.35, RuinousPowers.magicAttack(p))
        assertEquals(0.08, RuinousPowers.magicDamage(p))
        assertEquals(1.0, RuinousPowers.meleeStrength(p))
        RuinousPowers.click(p, RuinousPrayer.TRINITAS.slot)
        assertEquals(1.15, RuinousPowers.meleeAttack(p))
        assertEquals(1.15, RuinousPowers.rangedStrength(p))
        assertEquals(0.02, RuinousPowers.magicDamage(p))
        RuinousPowers.click(p, RuinousPrayer.INTENSIFY.slot)
        assertEquals(1.40, RuinousPowers.rangedAttack(p))
        assertEquals(1.40, RuinousPowers.meleeAttack(p))
        assertEquals(1.40, RuinousPowers.magicAttack(p))
        assertEquals(1.0, RuinousPowers.rangedStrength(p))
    }

    /** N496: the hover texts (CurseTooltipCacheTool) and the book's own cells name the numbers the formulas use. */
    @Test
    fun `N496 tooltips say the new values`() {
        val texts = gg.rsmod.game.tools.importer.CurseTooltipCacheTool.TOOLTIPS.associate { it.struct to it.new }
        val p = player()
        RuinousPowers.click(p, RuinousPrayer.DECIMATE.slot)
        assertEquals("Increases your Attack by ${pct(RuinousPowers.meleeAttack(p))}% and Strength by ${pct(RuinousPowers.meleeStrength(p))}%.", texts.getValue(2777))
        RuinousPowers.click(p, RuinousPrayer.ANNIHILATE.slot)
        assertEquals(
            "Increases your Ranged Attack by ${pct(RuinousPowers.rangedAttack(p))}% and Ranged Strength by ${pct(RuinousPowers.rangedStrength(p))}%.",
            texts.getValue(2778),
        )
        RuinousPowers.click(p, RuinousPrayer.VAPORISE.slot)
        assertEquals(
            "Increases your Magic Attack by ${pct(RuinousPowers.magicAttack(p))}% and Magic Damage by ${Math.round(RuinousPowers.magicDamage(p) * 100)}%.",
            texts.getValue(2779),
        )
        assertEquals("Increases your accuracy with all styles by ${pct(RuinousPowers.INTENSIFY_ACCURACY)}%.", texts.getValue(2785))
        assertTrue(texts.getValue(2784).contains("up to ${RuinousPowers.WRATH_PRAYER_PCT}%"))
        assertTrue(
            texts.getValue(907).contains("plus ${AncientCurses.TURMOIL_TARGET_ATTACK_PCT}% of enemy's level, and Strength by 23%, plus ${AncientCurses.TURMOIL_TARGET_STRENGTH_PCT}%"),
        )
        // The struct writer of the book (RuinousPowersCacheTool.CELLS) carries the same sentences.
        listOf(2777, 2778, 2779, 2784, 2785).forEach { struct ->
            assertTrue(gg.rsmod.game.tools.importer.RuinousPowersCacheTool.CELLS.any { it.text == texts.getValue(struct) }, "cell text of struct $struct")
        }
    }

    private fun pct(multiplier: Double) = Math.round((multiplier - 1) * 100)

    /** N496: Ruinous Wrath up to 33 % of the base Prayer level (curse Wrath 30 %, Retribution 25 %). */
    @Test
    fun `Wrath hits up to a third of the Prayer level`() {
        assertEquals(32, RuinousPowers.wrathMaxDamage(player(prayerLevel = 99)))
        assertEquals(30, RuinousPowers.wrathMaxDamage(player(prayerLevel = 91)))
    }

    /**
     * N487 (balance plan 2026-10-31): the Ruinous offence prayers raise accuracy only. OSRS Wiki Ancient Will, Trinitas, Vaporise and
     * Intensify: "Magic defence 0%". Before, the magic defence roll reused the attack multiplier, so Intensify made a player 50 %
     * harder to hit with magic as well.
     */
    /**
     * N488 (owner 2026-10-31: "0 praypoints ... geen sound van lege prayer"): OSRS `[clientscript,prayer_op]` refuses with
     * synth 2673 and "You need to recharge your Prayer at an altar."; a level that is too low gets the same sound.
     */
    @Test
    fun `a click with no Prayer points plays the refusal sound and says to recharge`() {
        val p = player(points = 0)
        val sent = written(p)
        RuinousPowers.click(p, RuinousPrayer.DECIMATE.slot)
        assertFalse(RuinousPowers.isActive(p, RuinousPrayer.DECIMATE))
        assertTrue(sent.any { it is gg.rsmod.game.message.impl.SynthSoundMessage && it.sound == Prayers.REFUSE_SOUND })
        assertTrue(sent.any { it is gg.rsmod.game.message.impl.MessageGameMessage && it.message == Prayers.NO_POINTS_MESSAGE })
        val low = player(prayerLevel = 70)
        val lowSent = written(low)
        RuinousPowers.click(low, RuinousPrayer.DECIMATE.slot)
        assertFalse(RuinousPowers.isActive(low, RuinousPrayer.DECIMATE))
        assertTrue(lowSent.any { it is gg.rsmod.game.message.impl.SynthSoundMessage && it.sound == Prayers.REFUSE_SOUND })
    }

    /** Every message [player] is sent from now on. */
    private fun written(player: Player): MutableList<Any> {
        val sent = mutableListOf<Any>()
        every { player.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers { sent.addAll(firstArg<Array<gg.rsmod.game.message.Message>>()); Unit }
        return sent
    }

    /** N486: Ruinous Wrath's OSRS clouds (OsrsFxImportTool batch ruinouswrath) are in the cache; the curse Wrath keeps 2259/2260. */
    @Test
    fun `Wrath uses the imported OSRS clouds`() {
        listOf(RuinousPowers.WRATH_STREAK_GFX, RuinousPowers.WRATH_CLOUD_GFX, RuinousPowers.WRATH_SMALL_CLOUD_GFX).forEach { id ->
            val def = definitions.getNullable(SpotAnimDef::class.java, id)
            assertTrue(def != null, "spotanim $id missing")
        }
        assertTrue(RuinousPowers.WRATH_VISUALS !== AncientCurses.CURSE_WRATH_VISUALS)
    }

    @Test
    fun `the Ruinous offence prayers give no magic defence`() {
        val p = player()
        assertEquals(1.0, gg.rsmod.plugins.content.combat.formula.MagicCombatFormula.getPrayerMagicDefenceMultiplier(p))
        listOf(RuinousPrayer.ANCIENT_WILL, RuinousPrayer.TRINITAS, RuinousPrayer.VAPORISE, RuinousPrayer.INTENSIFY).forEach { prayer ->
            RuinousPowers.click(p, prayer.slot)
            assertTrue(RuinousPowers.magicAttack(p) > 1.0, prayer.name)
            assertEquals(1.0, gg.rsmod.plugins.content.combat.formula.MagicCombatFormula.getPrayerMagicDefenceMultiplier(p), prayer.name)
        }
    }

    @Test
    fun `Metabolise takes a tick off the food delay only after 25 ticks`() {
        val p = player()
        every { lastWorld.currentCycle } returns 1000
        RuinousPowers.click(p, RuinousPrayer.METABOLISE.slot)
        every { lastWorld.currentCycle } returns 1024
        assertEquals(3, RuinousPowers.foodAttackDelay(p, 3))
        every { lastWorld.currentCycle } returns 1025
        assertEquals(2, RuinousPowers.foodAttackDelay(p, 3))
    }

    @Test
    fun `Rebuke adds 10 percent to the recoil`() {
        val p = player()
        assertEquals(5, RuinousPowers.rebuke(p, 5))
        RuinousPowers.click(p, RuinousPrayer.REBUKE.slot)
        // OSRS Wiki: every recoil effect (ring, Vengeance) deals 10 % more, rounded down.
        assertEquals(5, RuinousPowers.rebuke(p, 5))
        assertEquals(11, RuinousPowers.rebuke(p, 10))
        assertEquals(41, RuinousPowers.rebuke(p, 38))
        assertEquals(0, RuinousPowers.rebuke(p, 0))
        assertEquals(PrayerIcon.RUINOUS_REBUKE.id, p.prayerIcon)
    }

    @Test
    fun `the book is not changed during combat`() {
        val p = player()
        p.timers[ACTIVE_COMBAT_TIMER] = 10
        AncientCurses.switchBook(p, AncientCurses.PrayerBook.NORMAL)
        assertEquals(AncientCurses.PrayerBook.RUINOUS, AncientCurses.getBook(p))
    }
    @Test
    fun `Grace, Rejuvenation and Berserker are on only with their prayer and Prayer points`() {
        val p = player()
        assertFalse(RuinousPowers.graceKeepsRunEnergy(p))
        RuinousPowers.click(p, RuinousPrayer.RUINOUS_GRACE.slot)
        RuinousPowers.click(p, RuinousPrayer.REJUVENATION.slot)
        RuinousPowers.click(p, RuinousPrayer.BERSERKER.slot)
        assertTrue(RuinousPowers.graceKeepsRunEnergy(p))
        assertTrue(RuinousPowers.rejuvenation(p))
        // Berserker works after 15 seconds (25 ticks) of being on (OSRS Wiki).
        assertFalse(RuinousPowers.berserker(p))
        every { lastWorld.currentCycle } returns 100 + RuinousPowers.METABOLISE_DELAY_TICKS
        assertTrue(RuinousPowers.berserker(p))
        val empty = player(points = 0)
        empty.setVarbit(RuinousPrayer.RUINOUS_GRACE.varbit, 1)
        assertFalse(RuinousPowers.graceKeepsRunEnergy(empty))
        // On another book the bits mean nothing.
        AncientCurses.switchBook(p, AncientCurses.PrayerBook.NORMAL)
        assertFalse(RuinousPowers.rejuvenation(p))
    }

    @Test
    fun `Purge takes a third of the damage from both players' Prayer points`() {
        val attacker = player()
        val target = player()
        RuinousPowers.click(attacker, RuinousPrayer.PURGE.slot)
        RuinousPowers.onDamageDealt(attacker, target, 31)
        io.mockk.verify { attacker.decreasePrayerPoints(10) }
        io.mockk.verify { target.decreasePrayerPoints(10) }
    }

    @Test
    fun `Umbra's Vow drains 5 percent of the damage from Defence, never below 85 percent of the base level`() {
        val attacker = player()
        val target = player()
        target.skills.setBaseLevel(Skills.DEFENCE, 99)
        RuinousPowers.click(attacker, RuinousPrayer.UMBRA_VOW.slot)
        RuinousPowers.onDamageDealt(attacker, target, 40)
        assertEquals(97, target.skills.getCurrentLevel(Skills.DEFENCE))
        repeat(20) { RuinousPowers.onDamageDealt(attacker, target, 60) }
        assertEquals(99 - 99 * 15 / 100, target.skills.getCurrentLevel(Skills.DEFENCE))
        // N496: a potted target (118) loses at most 15 % of that level (to 101), not the 33 levels down to 85 % of the base.
        val potted = player()
        potted.skills.setBaseLevel(Skills.DEFENCE, 99)
        potted.skills.setCurrentLevel(Skills.DEFENCE, 118)
        repeat(30) { RuinousPowers.onDamageDealt(attacker, potted, 60) }
        assertEquals(118 - 118 * 15 / 100, potted.skills.getCurrentLevel(Skills.DEFENCE))
        // A new potion above the start level starts a new run from there.
        potted.skills.setCurrentLevel(Skills.DEFENCE, 120)
        repeat(30) { RuinousPowers.onDamageDealt(attacker, potted, 60) }
        assertEquals(120 - 120 * 15 / 100, potted.skills.getCurrentLevel(Skills.DEFENCE))
        // Glacies' Vow drains monsters only - a player keeps his Magic.
        RuinousPowers.click(attacker, RuinousPrayer.GLACIES_VOW.slot)
        target.skills.setBaseLevel(Skills.MAGIC, 99)
        RuinousPowers.onDamageDealt(attacker, target, 60)
        assertEquals(99, target.skills.getCurrentLevel(Skills.MAGIC))
    }

    @Test
    fun `Vindication below 10 percent Hitpoints heals a quarter of the Prayer level and empties the Prayer points`() {
        val p = player()
        every { p.getMaximumLifepoints() } returns 99
        every { p.getCurrentLifepoints() } returns 9
        RuinousPowers.click(p, RuinousPrayer.VINDICATION.slot)
        val attacker = player()
        RuinousPowers.onDamageDealt(attacker, p, 20)
        io.mockk.verify { p.setCurrentPrayerPoints(0) }
        assertTrue(RuinousPowers.active(p).isEmpty())
        // N485: the heal shows Redemption's graphic (and plays its heal sound, Redemption.playHealEffect).
        io.mockk.verify { p.graphic(Redemption.HEAL_GRAPHIC) }
        // Not below 10 %: nothing happens.
        val q = player()
        every { q.getMaximumLifepoints() } returns 99
        every { q.getCurrentLifepoints() } returns 10
        RuinousPowers.click(q, RuinousPrayer.VINDICATION.slot)
        RuinousPowers.onDamageDealt(attacker, q, 20)
        assertTrue(RuinousPowers.isActive(q, RuinousPrayer.VINDICATION))
        io.mockk.verify(exactly = 0) { q.graphic(Redemption.HEAL_GRAPHIC) }
    }

    /**
     * N583 Ruinous audit (owner 2026-11-06: "show stat adjustment echte data uit de server"): the prayer tab's panel (clientscript
     * RuinousPowersCacheTool.STAT_SCRIPT) shows, per skill, the strongest active Ruinous prayer's percentage. Every number it can show
     * is the server's own multiplier for that prayer alone, and a prayer the panel leaves out of a skill does nothing to it.
     */
    @Test
    fun `the stat adjustments panel shows exactly the server multipliers`() {
        val readers =
            mapOf<Int, (Player) -> Double>(
                0 to RuinousPowers::meleeAttack,
                2 to RuinousPowers::meleeStrength,
                4 to RuinousPowers::rangedAttack,
                6 to RuinousPowers::magicAttack,
            )
        assertEquals(readers.keys, RuinousPowersCacheTool.STAT_TABLE.map { it.first }.toSet())
        RuinousPowersCacheTool.STAT_TABLE.forEach { (skill, rows) ->
            val read = readers.getValue(skill)
            RuinousPrayer.values.filter { it != RuinousPrayer.PROTECT_ITEM }.forEach { prayer ->
                val p = player()
                RuinousPowers.click(p, prayer.slot)
                val shown = rows.firstOrNull { it.first == prayer.slot }?.second ?: 0
                assertEquals(shown.toLong(), pct(read(p)), "skill $skill, ${prayer.named}")
            }
        }
    }

    /** OSRS Wiki Fumus' Vow: a dragon dagger(p++) (severity 30) deals 6 damage at once; the poison itself is not used up. */
    @Test
    fun `Fumus' Vow deals the poison damage at once`() {
        val attacker = player()
        val target = player()
        RuinousPowers.afterPoisonInflicted(attacker, target, 30)
        io.mockk.verify(exactly = 0) { target.addHit(any()) }
        RuinousPowers.click(attacker, RuinousPrayer.FUMUS_VOW.slot)
        RuinousPowers.afterPoisonInflicted(attacker, target, 30)
        io.mockk.verify { target.addHit(match { h -> h.hitmarks.single().damage == 6 && h.hitmarks.single().type == gg.rsmod.plugins.api.HitType.POISON.id && h.source === attacker }) }
    }

    /** OSRS Wiki Cruor's Vow: "upon killing an opponent" +3 Hitpoints and -3 Prayer points - once per kill, nothing for a hit. */
    @Test
    fun `Cruor's Vow heals 3 and drains 3 once per kill`() {
        val attacker = player()
        RuinousPowers.click(attacker, RuinousPrayer.CRUOR_VOW.slot)
        val alive = player()
        every { alive.getCurrentLifepoints() } returns 50
        RuinousPowers.onDamageDealt(attacker, alive, 20)
        io.mockk.verify(exactly = 0) { attacker.decreasePrayerPoints(3) }
        val dead = player()
        every { dead.getCurrentLifepoints() } returns 0
        RuinousPowers.onDamageDealt(attacker, dead, 20)
        RuinousPowers.onDamageDealt(attacker, dead, 15)
        io.mockk.verify(exactly = 1) { attacker.decreasePrayerPoints(3) }
    }

    /** OSRS Wiki Rejuvenation: 1 Hitpoint every ~20 seconds (3x the normal 60), switching it on or off restarts the timer. */
    @Test
    fun `Rejuvenation restores Hitpoints three times as fast`() {
        val rates = gg.rsmod.plugins.content.mechanics.restoration.RestorationRates
        val normal = player()
        val rejuv = player()
        RuinousPowers.click(rejuv, RuinousPrayer.REJUVENATION.slot)
        assertEquals(1, rates.lifePointRate(normal))
        assertEquals(3, rates.lifePointRate(rejuv))
        // 600 ticks = 6 minutes = 60 restoration steps of 10 ticks: 6 points normally, 18 with Rejuvenation.
        assertEquals(6, (1..60).sumOf { rates.lifeDue(normal) })
        assertEquals(18, (1..60).sumOf { rates.lifeDue(rejuv) })
    }

    /** OSRS Wiki Berserker (Ruinous): after 15 seconds on, a boosted combat stat drains every 90 seconds instead of 60 (50 % longer). */
    @Test
    fun `Berserker makes a boosted combat stat last 50 percent longer after 15 seconds`() {
        val boosted = gg.rsmod.plugins.content.mechanics.restoration.BoostedCombatStats
        val p = player()
        assertEquals(100, boosted.ticksUntilDecay(p))
        RuinousPowers.click(p, RuinousPrayer.BERSERKER.slot)
        assertEquals(100, boosted.ticksUntilDecay(p))
        every { lastWorld.currentCycle } returns 100 + RuinousPowers.METABOLISE_DELAY_TICKS
        assertEquals(150, boosted.ticksUntilDecay(p))
    }

    /** OSRS Wiki Ruinous Powers drain table: drain effect per prayer (x10 here) - the server drains exactly these. */
    @Test
    fun `every prayer drains at its OSRS Wiki rate`() {
        val wiki =
            listOf(4, 18, 18, 18, 18, 1, 14, 14, 14, 22, 2, 18, 12, 12, 9, 28, 28, 28, 14, 14, 14, 14, 3, 28)
        assertEquals(wiki.map { it * 10 }, RuinousPrayer.values.map { it.drainEffect })
    }
}