package gg.rsmod.plugins.content.activity.duel_arena

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.PawnList
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RCV-010 C2-b: every Duel Arena rule and equipment lock enforced through the shared gates (Novite 667 semantics). */
class DuelArenaRulesTests {
    private fun player(name: String): Player {
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns DEFINITIONS
        val npcs = mockk<PawnList<Npc>>(relaxed = true)
        every { world.npcs } returns npcs
        val player = mockk<Player>(relaxed = true)
        every { player.world } returns world
        every { player.username } returns name
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns ItemContainer(DEFINITIONS, INVENTORY_KEY)
        every { player.equipment } returns ItemContainer(DEFINITIONS, EQUIPMENT_KEY)
        every { player.tile } returns Tile(3346, 3251, 0)
        return player
    }

    private fun fight(vararg rules: DuelRule): Triple<DuelArenaMatch, Player, Player> {
        val a = player("a")
        val b = player("b")
        val match = DuelArenaMatch(a, b)
        match.rules.clear()
        match.rules.addAll(rules)
        a.attr[DUEL_MATCH_ATTR] = match
        b.attr[DUEL_MATCH_ATTR] = match
        match.stage = DuelStage.FIGHTING
        a.attr[DuelArenaRules.CAN_FIGHT_ATTR] = true
        return Triple(match, a, b)
    }

    @Test
    fun `every activity rule is refused only while its rule is on`() {
        val cases = mapOf(
            DuelRule.NO_FOOD to RestrictedAction.EAT,
            DuelRule.NO_DRINKS to RestrictedAction.DRINK,
            DuelRule.NO_PRAYER to RestrictedAction.PRAYER,
            DuelRule.NO_SPECIAL_ATTACKS to RestrictedAction.SPECIAL_ATTACK,
        )
        cases.forEach { (rule, action) ->
            assertNotNull(DuelArenaRules.activityRefusal(fight(rule).second, action), "$rule must refuse $action")
            assertNull(DuelArenaRules.activityRefusal(fight().second, action), "$action allowed without $rule")
        }
        assertEquals("Summoning has been disabled during this duel!", DuelArenaRules.activityRefusal(fight().second, RestrictedAction.SUMMON))
        assertNull(DuelArenaRules.activityRefusal(fight(DuelRule.ENABLE_SUMMONING).second, RestrictedAction.SUMMON))
        assertNotNull(DuelArenaRules.activityRefusal(fight().second, RestrictedAction.TELEPORT))
        // Nothing is refused outside a fight.
        assertNull(DuelArenaRules.activityRefusal(player("idle"), RestrictedAction.EAT))
    }

    @Test
    fun `attack rules follow Novite keepCombating for every style rule, target and fun weapons`() {
        val styleRules = mapOf(DuelRule.NO_MELEE to CombatClass.MELEE, DuelRule.NO_RANGED to CombatClass.RANGED, DuelRule.NO_MAGIC to CombatClass.MAGIC)
        styleRules.forEach { (rule, style) ->
            val (_, a, b) = fight(rule)
            assertNotNull(DuelArenaRules.attackRefusal(a, b, style, -1), "$rule blocks $style")
            CombatClass.values().filter { it != style }.forEach { other -> assertNull(DuelArenaRules.attackRefusal(a, b, other, -1), "$rule allows $other") }
        }
        val (_, a, b) = fight(DuelRule.FUN_WEAPONS)
        assertEquals("You can only use fun weapons in this duel!", DuelArenaRules.attackRefusal(a, b, CombatClass.MELEE, Items.ABYSSAL_WHIP))
        assertNull(DuelArenaRules.attackRefusal(a, b, CombatClass.MELEE, DuelArenaRules.FUN_WEAPON))
        assertTrue(DuelArenaRules.attackRefusal(a, player("stranger"), CombatClass.MELEE, -1)!!.startsWith("You may only attack your target"))
        a.attr[DuelArenaRules.CAN_FIGHT_ATTR] = false
        assertEquals("The duel hasn't started yet.", DuelArenaRules.attackRefusal(a, b, CombatClass.MELEE, -1))
    }

    @Test
    fun `every equipment lock blocks exactly its own slot during the fight`() {
        val bySlot = DEFINITIONS.getAll<ItemDef>(ItemDef::class.java).values.filterIsInstance<ItemDef>()
            .filter { it.equipSlot >= 0 && !it.noted }.groupBy { it.equipSlot }.mapValues { it.value.first() }
        DuelEquipLock.values().forEach { lock ->
            val (match, a, _) = fight()
            match.lockedSlots.add(lock)
            DuelEquipLock.values().forEach { other ->
                val item = bySlot[other.slot.id] ?: return@forEach
                val refusal = DuelArenaRules.equipRefusal(a, item.id)
                if (other == lock) assertNotNull(refusal, "$lock must block ${item.name}") else assertNull(refusal, "$lock must not block ${other.slot}")
            }
        }
    }

    @Test
    fun `no movement and obstacles exclude each other, and forfeit follows its rule`() {
        val (match, _, _) = fight()
        match.stage = DuelStage.CONFIGURING
        DuelArenaRules.toggleRule(match, DuelRule.OBSTACLES)
        assertEquals(listOf("You can't have movement without obstacles."), DuelArenaRules.toggleRule(match, DuelRule.NO_MOVEMENT))
        assertTrue(DuelRule.OBSTACLES !in match.rules && DuelRule.NO_MOVEMENT in match.rules)
        assertEquals(listOf("You can't have obstacles without movement."), DuelArenaRules.toggleRule(match, DuelRule.OBSTACLES))
        assertTrue(DuelRule.NO_MOVEMENT !in match.rules)
        assertEquals("You're not fighting yet.", DuelArenaRules.forfeitRefusal(match))
        match.stage = DuelStage.FIGHTING
        assertNull(DuelArenaRules.forfeitRefusal(match))
        match.rules.add(DuelRule.NO_FORFEIT)
        assertEquals("Forfeiting is disabled for this duel.", DuelArenaRules.forfeitRefusal(match))
    }

    @Test
    fun `accept validation, no-movement restriction and payout overflow`() {
        val (match, a, b) = fight(DuelRule.NO_RANGED, DuelRule.NO_MELEE, DuelRule.NO_MAGIC)
        assertEquals("You have to be able to use atleast one combat style in a duel.", DuelArenaRules.acceptRefusal(match, a))
        match.rules.clear()
        match.rules.add(DuelRule.ENABLE_SUMMONING)
        repeat(28) { a.inventory.add(Items.BONES) }
        a.equipment[EquipmentType.HEAD.id] = Item(Items.RUNE_FULL_HELM)
        match.lockedSlots.add(DuelEquipLock.HEAD)
        assertEquals("You do not have enough inventory space to remove all the equipment.", DuelArenaRules.acceptRefusal(match, a))

        match.rules.add(DuelRule.NO_MOVEMENT)
        DuelArenaRules.onFightStart(match)
        assertEquals(DuelArenaRules.NO_MOVEMENT_MESSAGE, a.attr[gg.rsmod.game.model.attr.MOVEMENT_RESTRICTION_ATTR])
        DuelArenaRules.onFightEnd(a)
        assertNull(a.attr[gg.rsmod.game.model.attr.MOVEMENT_RESTRICTION_ATTR])

        match.opponentStake.add(Items.RUNE_PLATEBODY, 1)
        match.challengerStake.add(Items.COINS_995, 1000)
        val world = a.world
        DuelArenaRules.payout(match, a) // a's pack is full of bones: both the platebody and the coins go to the ground
        verify(exactly = 2) { world.spawn(any<GroundItem>()) }
        assertTrue(match.opponentStake.rawItems.all { it == null } && match.challengerStake.rawItems.all { it == null }, "stakes are emptied once paid")
    }

    @Test
    fun `every rule consumer asks the shared activity gate`() {
        val main = File("src/main/kotlin/gg/rsmod/plugins/content")
        val consumers = mapOf(
            "items/food/eating.plugin.kts" to "EAT",
            "items/potion/Potions.kt" to "DRINK",
            "mechanics/prayer/Prayers.kt" to "PRAYER",
            "mechanics/prayer/AncientCurses.kt" to "PRAYER",
            "inter/attack/attack_tab.plugin.kts" to "SPECIAL_ATTACK",
            "magic/PawnExt.kt" to "TELEPORT",
            "skills/summoning/Familiar.kt" to "SUMMON",
        )
        val missing = consumers.filter { (path, action) ->
            !File(main, path).readText().contains(Regex("""ActivityRestrictions\.refuse\(\s*\w+,\s*gg\.rsmod\.plugins\.content\.mechanics\.restrictions\.RestrictedAction\.$action"""))
        }
        assertTrue(missing.isEmpty(), "consumers not wired to the shared gate: $missing")
        assertEquals(RestrictedAction.values().map { it.name }.toSet(), consumers.values.toSet(), "every restricted action has a consumer")
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            DEFINITIONS.loadAll(CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString()))
        }
    }
}
