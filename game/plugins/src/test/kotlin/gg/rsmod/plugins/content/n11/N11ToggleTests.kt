package gg.rsmod.plugins.content.n11

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.items.osrs.AvasAssembler
import gg.rsmod.plugins.content.combat.strategy.ranged.AvasDevices
import io.mockk.every
import io.mockk.verify
import kotlin.test.*

class N11ToggleTests {
    @Test fun `cocktail native mixing button keeps original shaker and cannot pay twice after replacement or logout`() {
        N11Fixture().use { f ->
            val making = gg.rsmod.plugins.content.skills.cooking.CocktailMaking
            f.menu(Items.COCKTAIL_SHAKER, "Mix-cocktail")
            making.POURS.forEach { f.menu(it.input, "Pour") }
            making.GARNISHES.forEach { f.menu(it.input, "Add-ingreds") }
            every { f.player.interfaces.isVisible(436) } returns true
            f.load("skills.cooking.Cocktail_making_plugin")
            val recipe = making.MIXES.getValue(23)
            fun prepare(): Item {
                f.inventory.removeAll()
                val shaker = Item(Items.COCKTAIL_SHAKER)
                f.inventory[0] = shaker
                recipe.ingredients.forEach { (id, amount) -> f.inventory.add(id, amount) }
                f.select(0)
                f.repo.executeItem(f.player, Items.COCKTAIL_SHAKER, 1)
                return shaker
            }
            prepare()
            f.repo.executeButton(f.player, 436, 23)
            f.repo.executeButton(f.player, 436, 23)
            repeat(4) { f.queues.cycle() }
            assertEquals(1, f.inventory.getItemCount(Items.MIXED_BLAST))
            verify(exactly = 1) { f.player.addXp(gg.rsmod.plugins.api.Skills.COOKING, 30.0, any(), any()) }
            val dropped = prepare()
            f.repo.executeButton(f.player, 436, 23)
            f.inventory[0] = Item(Items.COCKTAIL_SHAKER)
            repeat(4) { f.queues.cycle() }
            assertEquals(0, f.inventory.getItemCount(Items.MIXED_BLAST))
            assertFalse(making.make(f.player, recipe, 0, dropped))
            prepare()
            f.repo.executeButton(f.player, 436, 23)
            f.queues.terminateTasks()
            repeat(4) { f.queues.cycle() }
            assertEquals(0, f.inventory.getItemCount(Items.MIXED_BLAST))
        }
    }

    @Test fun `all seven cocktail recipes pour real drinks with exact ingredients and one returned shaker in full inventory`() {
        val making = gg.rsmod.plugins.content.skills.cooking.CocktailMaking
        for (recipe in making.MIXES.values + making.POURS + making.GARNISHES) N11Fixture().use { f ->
            val input = Item(recipe.input)
            f.inventory[0] = input
            recipe.ingredients.forEach { (id, count) -> f.inventory.add(id, count) }
            every { f.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.COOKING) } returns recipe.level - 1
            assertFalse(making.make(f.player, recipe, 0, input))
            every { f.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.COOKING) } returns recipe.level
            val missing = recipe.ingredients.keys.first()
            val count = f.inventory.getItemCount(missing)
            f.inventory.remove(missing, count)
            assertFalse(making.make(f.player, recipe, 0, input))
            assertSame(input, f.inventory[0])
            f.inventory.add(missing, count)
            while (f.inventory.hasSpace) f.inventory.add(100000)
            assertTrue(making.make(f.player, recipe, 0, input), "recipe ${recipe.input}->${recipe.output}")
            assertEquals(1, f.inventory.getItemCount(recipe.output))
            assertEquals(if (recipe.returnsShaker) 1 else 0, f.inventory.getItemCount(Items.COCKTAIL_SHAKER))
            assertFalse(making.make(f.player, recipe, 0, input))
            if (recipe.xp > 0) verify(exactly = 1) { f.player.addXp(gg.rsmod.plugins.api.Skills.COOKING, recipe.xp, any(), any()) }
            else verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
        }
    }

    @Test fun `cocktail fruit preparation returns four pineapple rings only with room and preserves knife`() {
        val making = gg.rsmod.plugins.content.skills.cooking.CocktailMaking
        making.CUTS.forEach { cut ->
            for (output in listOf(cut.chunks, cut.slices)) N11Fixture().use { f ->
                val fruit = Item(cut.fruit)
                f.inventory[0] = fruit
                assertFalse(making.cut(f.player, cut, output, 0, fruit))
                f.inventory[1] = Item(Items.KNIFE)
                while (f.inventory.hasSpace) f.inventory.add(100000)
                val amount = if (output == cut.slices) cut.slicesAmount else 1
                if (amount > 1) {
                    assertFalse(making.cut(f.player, cut, output, 0, fruit))
                    for (i in 2..amount) f.inventory[i] = null
                }
                assertTrue(making.cut(f.player, cut, output, 0, fruit))
                assertEquals(amount, f.inventory.getItemCount(output))
                assertEquals(1, f.inventory.getItemCount(Items.KNIFE))
                assertFalse(making.cut(f.player, cut, output, 0, fruit))
                verify(exactly = 0) { f.player.addXp(any(), any(), any(), any()) }
            }
        }
    }

    @Test fun `cocktail heating reuses range cooking and never burns the drink`() {
        val saturday = gg.rsmod.plugins.content.skills.cooking.CookingData.MIXED_SATURDAY
        val dragon = gg.rsmod.plugins.content.skills.cooking.CookingData.MIXED_DRAGON
        assertEquals(32, saturday.levelRequirement)
        assertEquals(0.0, saturday.experience)
        assertEquals(118, dragon.lowChance)
        assertEquals(492, dragon.highChance)
        for (recipe in listOf(saturday, dragon)) N11Fixture().use { f ->
            assertTrue(recipe.rangeOnly)
            assertEquals(recipe.cooked, recipe.burnt)
            every { f.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, any()) } answers {
                gg.rsmod.game.fs.def.ObjectDef(secondArg()).apply { name = "Range" }
            }
            every { f.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.COOKING) } returns 99
            every { f.world.isSpawned(any<gg.rsmod.game.model.entity.GameObject>()) } returns true
            val range = gg.rsmod.game.model.entity.DynamicObject(4172, 10, 0, f.player.tile)
            f.attrs[gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR] = java.lang.ref.WeakReference(range)
            f.inventory[0] = Item(recipe.raw)
            f.player.queue {
                gg.rsmod.plugins.content.skills.cooking.CookingAction.cook(this, recipe, 1)
            }
            repeat(10) { f.queues.cycle() }
            assertEquals(1, f.inventory.getItemCount(recipe.cooked))
            assertEquals(0, f.inventory.getItemCount(recipe.raw))
        }
    }

    @Test fun `panning permission mud cooldown full inventory search and repeated clicks cannot duplicate finds or XP`() {
        N11Fixture().use { f ->
            f.menu(Items.PANNING_TRAY, "", "", "", "Search")
            f.menu(Items.PANNING_TRAY_679, "", "Search")
            every { f.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, any()) } answers {
                gg.rsmod.game.fs.def.ObjectDef(secondArg()).apply { options[0] = "Pan" }
            }
            val anim = io.mockk.mockk<gg.rsmod.game.fs.def.AnimDef>()
            every { anim.cycleLength } returns 4
            every { f.definitions.get(gg.rsmod.game.fs.def.AnimDef::class.java, any()) } returns anim
            f.load("areas.digsite.Panning_plugin")
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            f.inventory[0] = Item(Items.PANNING_TRAY)
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.pan(f.player))
            val tea = Item(Items.CUP_OF_TEA_1978)
            f.inventory[1] = tea
            assertTrue(gg.rsmod.plugins.content.areas.digsite.Panning.bribe(f.player, 1, tea))
            f.inventory[1] = Item(100001)
            assertTrue(gg.rsmod.plugins.content.areas.digsite.Panning.pan(f.player))
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.pan(f.player))
            assertEquals(Items.PANNING_TRAY_679, f.inventory[0]?.id)
            verify(exactly = 1) { f.player.addXp(gg.rsmod.plugins.api.Skills.FISHING, 2.0, any(), any()) }
            verify(exactly = 1) { f.player.addXp(gg.rsmod.plugins.api.Skills.MINING, 2.0, any(), any()) }
            val mud = f.inventory[0]!!
            f.timers.remove(gg.rsmod.plugins.content.areas.digsite.Panning.DELAY)
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.search(f.player, 0, mud))
            verify(exactly = 0) { f.world.random(any<Int>()) }
            f.inventory[1] = null
            every { f.world.random(39) } returns 4 // nuggets
            assertTrue(f.repo.executeItem(f.player, Items.PANNING_TRAY_679, 2))
            f.repo.executeItem(f.player, Items.PANNING_TRAY_679, 2)
            assertEquals(1, f.inventory.getItemCount(Items.NUGGETS))
            assertEquals(1, f.inventory.getItemCount(Items.PANNING_TRAY))
            f.queues.terminateTasks()
            assertEquals(true, f.attrs[gg.rsmod.plugins.content.areas.digsite.Panning.PERMISSION])
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.search(f.player, 0, mud))
        }
    }

    @Test fun `panning table has exact forty slots and tea and nugget transactions are idempotent`() {
        val table = (0..39).map { gg.rsmod.plugins.content.areas.digsite.Panning.reward(it, true) }.groupingBy { it }.eachCount()
        assertEquals(mapOf(Items.COINS_995 to 4, Items.NUGGETS to 4, Items.OYSTER to 3, Items.UNCUT_OPAL to 3,
            Items.UNCUT_JADE to 3, Items.SPECIAL_CUP to 3, null to 20), table)
        assertEquals(23, (0..39).count { gg.rsmod.plugins.content.areas.digsite.Panning.reward(it, false) == null })
        gg.rsmod.plugins.content.areas.digsite.Panning.TEA.forEach { teaId ->
            N11Fixture().use { f ->
                val tea = Item(teaId)
                f.inventory[0] = tea
                assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.bribe(f.player, 0, Item(teaId)))
                assertTrue(gg.rsmod.plugins.content.areas.digsite.Panning.bribe(f.player, 0, tea))
                val second = Item(teaId)
                f.inventory[0] = second
                assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.bribe(f.player, 0, second))
                assertSame(second, f.inventory[0])
            }
        }
        N11Fixture().use { f ->
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            f.inventory[0] = Item(Items.NUGGETS)
            f.inventory[1] = Item(Items.NUGGETS)
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.exchangeNuggets(f.player))
            f.inventory[2] = Item(Items.NUGGETS)
            assertTrue(gg.rsmod.plugins.content.areas.digsite.Panning.exchangeNuggets(f.player))
            assertEquals(0, f.inventory.getItemCount(Items.NUGGETS))
            assertEquals(1, f.inventory.getItemCount(Items.GOLD_ORE))
            assertFalse(gg.rsmod.plugins.content.areas.digsite.Panning.exchangeNuggets(f.player))
        }
    }

    private fun museum(f: N11Fixture): gg.rsmod.game.model.varp.VarpSet {
        val bank = f.container()
        every { f.player.bank } returns bank
        val varps = gg.rsmod.game.model.varp.VarpSet((0..1100).toSet())
        every { f.player.varps } returns varps
        every { f.definitions.get(gg.rsmod.game.fs.def.VarbitDef::class.java, any()) } answers {
            gg.rsmod.game.fs.def.VarbitDef(secondArg()).apply {
                varp = 1011
                startBit = mapOf(3645 to 2, 3646 to 3, 3647 to 4, 3648 to 5, 3649 to 6).getValue(id)
                endBit = startBit
            }
        }
        every { f.definitions.get(gg.rsmod.game.fs.def.ObjectDef::class.java, any()) } answers {
            gg.rsmod.game.fs.def.ObjectDef(secondArg()).apply { options[0] = if (id == 24452) "Look-at" else "Study" }
        }
        return varps
    }

    @Test fun `museum five correct displays each award ten Kudos once and never a guaranteed lamp`() {
        N11Fixture().use { f ->
            val varps = museum(f)
            varps.setState(1011, 3) // unrelated native bits must survive
            f.load("areas.varrock.Varrock_museum_displays_plugin")
            val displays = gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.EXHIBITS
            displays.forEachIndexed { i, display ->
                repeat(28) { f.inventory[it] = Item(100000 + it) }
                val held = Item(display.item)
                f.inventory[0] = held
                f.inventory[1] = Item(display.item)
                f.player.bank[0] = Item(display.item)
                f.attrs[gg.rsmod.game.model.attr.INTERACTING_ITEM] = java.lang.ref.WeakReference(held)
                val obj = gg.rsmod.game.model.entity.DynamicObject(display.root, 10, 0, display.tile)
                f.attrs[gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR] = java.lang.ref.WeakReference(obj)
                assertFalse(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.place(f.player, obj, 0, held), "identify first")
                f.attrs[gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.CASE_TOLD] = 31
                assertTrue(f.repo.executeItemOnObject(f.player, display.root, display.item))
                assertNull(f.inventory[0])
                assertEquals(if (display.item == Items.POTTERY) 1 else 0, f.inventory.getItemCount(display.item))
                assertEquals(if (display.item == Items.POTTERY) 1 else 0, f.player.bank.getItemCount(display.item))
                assertEquals((i + 1) * 10, varps.getState(1010))
                assertTrue(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.displayed(f.player, display.item))
                f.inventory[0] = Item(display.item)
                f.attrs[gg.rsmod.game.model.attr.INTERACTING_ITEM] = java.lang.ref.WeakReference(f.inventory[0]!!)
                f.repo.executeItemOnObject(f.player, display.root, display.item)
                assertEquals(display.item, f.inventory[0]?.id, "duplicate submission keeps the duplicate")
                assertEquals((i + 1) * 10, varps.getState(1010))
                assertTrue(f.inventory.none { it != null && it.id in 11185..11189 }, "cleaning case grants no lamp")
                f.attrs[gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.CASE_TOLD] = 0
            }
            assertEquals(127, varps.getState(1011))
            f.queues.terminateTasks() // native persistent varps, no delayed reward
            assertEquals(50, varps.getState(1010))
        }
    }

    @Test fun `museum rejects stale replaced dropped wrong-case and wrong-tile artefacts without changing Kudos`() {
        N11Fixture().use { f ->
            val varps = museum(f)
            f.attrs[gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.CASE_TOLD] = 31
            val first = gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.EXHIBITS.first()
            val other = gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.EXHIBITS.last()
            val obj = gg.rsmod.game.model.entity.DynamicObject(first.root, 10, 0, first.tile)
            val held = Item(first.item)
            f.inventory[0] = Item(first.item)
            assertFalse(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.place(f.player, obj, 0, held))
            f.inventory[0] = null
            assertFalse(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.place(f.player, obj, 0, held))
            f.inventory[0] = held
            assertFalse(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.place(f.player,
                gg.rsmod.game.model.entity.DynamicObject(other.root, 10, 0, other.tile), 0, held))
            assertFalse(gg.rsmod.plugins.content.areas.varrock.VarrockMuseum.place(f.player,
                gg.rsmod.game.model.entity.DynamicObject(first.root, 10, 0, first.tile.transform(1, 0)), 0, held))
            assertEquals(0, varps.getState(1010))
            assertSame(held, f.inventory[0])
        }
    }

    @Test fun `Slayer bell reserves one wall on a double click and restores it after death even if player logs out`() {
        N11Fixture().use { f ->
            val wall = gg.rsmod.game.model.entity.DynamicObject(22545, 4, 0, gg.rsmod.game.model.Tile(3201, 3200))
            val chunk = io.mockk.mockk<gg.rsmod.game.model.region.Chunk>()
            every { f.world.chunks.get(any<gg.rsmod.game.model.Tile>(), false) } returns null
            every { f.world.chunks.get(wall.tile, false) } returns chunk
            every { chunk.getEntities<gg.rsmod.game.model.entity.GameObject>(wall.tile,
                gg.rsmod.game.model.EntityType.STATIC_OBJECT, gg.rsmod.game.model.EntityType.DYNAMIC_OBJECT) } returns listOf(wall)
            val spawned = io.mockk.slot<gg.rsmod.game.model.entity.Npc>()
            every { f.world.spawn(capture(spawned)) } returns true
            val worldQueues = gg.rsmod.game.model.queue.impl.WorldQueueTaskSet()
            every { f.world.queue(any()) } answers {
                worldQueues.queue(f.world, kotlinx.coroutines.Dispatchers.Unconfined,
                    gg.rsmod.game.model.queue.TaskPriority.STANDARD, firstArg())
            }
            f.inventory[0] = Item(Items.SLAYER_BELL)
            val opponent = io.mockk.mockk<gg.rsmod.game.model.entity.Player>(relaxed = true)
            f.attrs[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR] = java.lang.ref.WeakReference(opponent)
            f.timers[gg.rsmod.plugins.content.combat.Combat.PJ_TIMER] = 17
            gg.rsmod.plugins.content.skills.slayer.Molanisks.ring(f.player)
            gg.rsmod.plugins.content.skills.slayer.Molanisks.ring(f.player)
            verify(exactly = 1) { f.world.spawn(any<gg.rsmod.game.model.entity.Npc>()) }
            verify(exactly = 1) { f.world.remove(wall) }
            assertEquals(false, spawned.captured.respawnOverride)
            assertSame(opponent, f.attrs[gg.rsmod.game.model.attr.LAST_HIT_BY_ATTR]?.get())
            assertEquals(17, f.timers[gg.rsmod.plugins.content.combat.Combat.PJ_TIMER])
            spawned.captured.combatDef = spawned.captured.combatDef.copy(respawnDelay = 2)
            gg.rsmod.plugins.content.skills.slayer.Molanisks.died(spawned.captured)
            gg.rsmod.plugins.content.skills.slayer.Molanisks.died(spawned.captured)
            f.queues.terminateTasks() // logout cannot cancel the world's restoration
            repeat(4) { worldQueues.cycle() }
            verify(exactly = 1) { f.world.spawn(wall) }
            assertEquals(1, f.inventory.getItemCount(Items.SLAYER_BELL))
        }
    }

    @Test fun `Slayer bell needs the real item and Slayer 39 but rings outside the cavern without consuming it`() {
        N11Fixture().use { f ->
            f.menu(Items.SLAYER_BELL, "Ring")
            f.load("skills.slayer.Molanisks_plugin")
            f.repo.executeItem(f.player, Items.SLAYER_BELL, 1)
            verify(exactly = 0) { f.player.animate(6083) }
            val bell = Item(Items.SLAYER_BELL)
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            f.inventory[0] = bell
            every { f.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.SLAYER) } returns 38
            f.repo.executeItem(f.player, Items.SLAYER_BELL, 1)
            assertSame(bell, f.inventory[0])
            verify(exactly = 1) { f.player.animate(6083) }
            verify(exactly = 0) { f.world.spawn(any<gg.rsmod.game.model.entity.Npc>()) }
            every { f.skills.getCurrentLevel(gg.rsmod.plugins.api.Skills.SLAYER) } returns 39
            f.repo.executeItem(f.player, Items.SLAYER_BELL, 1)
            f.repo.executeItem(f.player, Items.SLAYER_BELL, 1)
            verify(exactly = 3) { f.player.animate(6083) }
            assertSame(bell, f.inventory[0])
            verify(exactly = 0) { f.world.spawn(any<gg.rsmod.game.model.entity.Npc>()) }
        }
    }

    @Test fun `Slayer bell wall range includes three tiles and excludes four and other floors`() {
        val wall = gg.rsmod.game.model.Tile(2744, 5193)
        for (dx in -3..3) for (dz in -3..3) assertTrue(
            gg.rsmod.plugins.content.skills.slayer.Molanisks.inRange(wall.transform(dx, dz), wall))
        assertFalse(gg.rsmod.plugins.content.skills.slayer.Molanisks.inRange(wall.transform(4, 0), wall))
        assertFalse(gg.rsmod.plugins.content.skills.slayer.Molanisks.inRange(gg.rsmod.game.model.Tile(wall.x, wall.z, 1), wall))
    }

    @Test fun `demonic sigil chants five chosen words without consuming it and rejects a dropped sigil`() {
        N11Fixture().use { f ->
            f.menu(Items.DEMONIC_SIGIL, "Chant")
            f.load("items.Demonic_sigil_plugin")
            repeat(28) { f.inventory[it] = Item(100000 + it) }
            val held = Item(Items.DEMONIC_SIGIL)
            f.inventory[0] = held
            f.repo.executeItem(f.player, held.id, 1)
            f.queues.cycle()
            listOf("Tarren", "Caldar", "Camerinthum", "Agrith-Naar", "Nahudu").forEachIndexed { i, word ->
                f.queues.submitReturnValue(gg.rsmod.game.message.impl.ResumePauseButtonMessage(234, i + 2, i + 2))
                f.queues.cycle()
                verify(exactly = 1) { f.player.forceChat(word) }
            }
            assertSame(held, f.inventory[0])
            f.repo.executeItem(f.player, held.id, 1)
            f.queues.cycle()
            f.inventory[0] = null
            f.queues.submitReturnValue(gg.rsmod.game.message.impl.ResumePauseButtonMessage(234, 2, 2))
            f.queues.cycle()
            verify(exactly = 1) { f.player.forceChat("Tarren") }
            assertFalse(f.repo.hasItemOption(Items.DEMONIC_SIGIL_MOULD, 1))
        }
    }

    private fun prepare(f: N11Fixture) {
        AvasAssembler.LEGACY_DEVICES.forEach {
            f.menu(it, "", "Wear", "", "Toggle")
            f.def(it).equipmentMenu[0] = "Toggle"
        }
        listOf(Items.IRON_ARROW, Items.STEEL_ARROW).forEach {
            f.def(it).equipSlot = EquipmentType.AMMO.id
            f.def(it).stacks = true
        }
        f.load("items.osrs.Avas_assembler_plugin")
    }

    private fun tick(f: N11Fixture) {
        val field = f.repo.javaClass.getDeclaredField("timerPlugins").apply { isAccessible = true }
        val key = (field.get(f.repo) as Map<*, *>).keys.single() as TimerKey
        assertTrue(f.repo.executeTimer(f.player, key))
    }

    @Test fun `inventory and worn Toggle change one persisted preference twice immediately`() {
        N11Fixture().use { f ->
            prepare(f)
            assertEquals("avas_assembler_gathering_stopped", AvasAssembler.GATHERING_STOPPED.persistenceKey)
            for (id in AvasAssembler.LEGACY_DEVICES) {
                f.inventory[0] = Item(id)
                f.equipment[EquipmentType.CAPE.id] = Item(id)
                assertTrue(f.repo.executeItem(f.player, id, 4))
                assertEquals(true, f.attrs[AvasAssembler.GATHERING_STOPPED])
                assertTrue(f.repo.executeEquipmentOption(f.player, id, 1))
                assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED])
                f.repo.executeItem(f.player, id, 4)
                f.repo.executeItem(f.player, id, 4)
                assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED])
            }
            f.inventory[0] = Item(Items.BLACK_WARLOCK_NOTED)
            f.equipment[EquipmentType.CAPE.id] = null
            f.repo.executeItem(f.player, Items.AVAS_ACCUMULATOR, 4)
            f.repo.executeEquipmentOption(f.player, Items.AVAS_ATTRACTOR, 1)
            assertEquals(false, f.attrs[AvasAssembler.GATHERING_STOPPED], "stale or noted cannot toggle")
            assertFalse(f.repo.hasItemOption(Items.BLACK_WARLOCK_NOTED, 4))
        }
    }

    @Test fun `real gathering timer reads Toggle and uses iron steel and existing mithril table`() {
        for ((id, arrow) in listOf(Items.AVAS_ATTRACTOR to Items.IRON_ARROW, Items.AVAS_ACCUMULATOR to Items.STEEL_ARROW, Items.AVAS_ASSEMBLER to Items.MITHRIL_ARROW)) {
            N11Fixture().use { f ->
                prepare(f)
                every { f.world.random(any<Int>()) } returns 0
                f.equipment[EquipmentType.CAPE.id] = Item(id)
                f.attrs[AvasAssembler.GATHERING_STOPPED] = true
                tick(f)
                assertNull(f.equipment[EquipmentType.AMMO.id])
                f.attrs[AvasAssembler.GATHERING_STOPPED] = false
                tick(f)
                assertEquals(arrow, f.equipment[EquipmentType.AMMO.id]?.id)
                tick(f)
                assertEquals(1, f.equipment[EquipmentType.AMMO.id]?.amount, "movement gate")
                assertEquals(AvasDevices.AmmoOutcome.RECOVERED, AvasDevices.outcome(f.player, 99))
                f.attrs[AvasAssembler.GATHERING_STOPPED] = true
                assertEquals(AvasDevices.AmmoOutcome.RECOVERED, AvasDevices.outcome(f.player, 99), "Toggle does not disable ammunition recovery")
            }
        }
    }

    @Test fun `legacy gathering overflows full inventory onto ground and respects metal torso`() {
        N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ACCUMULATOR)
            every { f.world.random(any<Int>()) } returns 1999 // steel bar
            (0 until 28).forEach { f.inventory[it] = Item(100000 + it) }
            tick(f)
            verify(exactly = 1) { f.world.spawn(any<gg.rsmod.game.model.entity.GroundItem>()) }
        }
        N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ATTRACTOR)
            f.def(Items.IRON_PLATEBODY).name = "Iron platebody"
            f.equipment[EquipmentType.CHEST.id] = Item(Items.IRON_PLATEBODY)
            tick(f)
            assertNull(f.equipment[EquipmentType.AMMO.id])
        }
    }

    @Test fun `rare attracted helmet equips through shared requirements or falls back to inventory`() {
        for (baseDefence in listOf(1, 99)) N11Fixture().use { f ->
            prepare(f)
            f.equipment[EquipmentType.CAPE.id] = Item(Items.AVAS_ACCUMULATOR)
            f.def(Items.STEEL_MED_HELM).equipSlot = EquipmentType.HEAD.id
            f.def(Items.STEEL_MED_HELM).skillReqs = it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap().apply { put(1.toByte(), 5.toByte()) }
            every { f.skills.getMaxLevel(1) } returns baseDefence
            every { f.world.random(any<Int>()) } returns 1998
            tick(f)
            if (baseDefence < 5) {
                assertNull(f.equipment[EquipmentType.HEAD.id])
                assertEquals(1, f.inventory.getItemCount(Items.STEEL_MED_HELM))
            } else {
                assertEquals(Items.STEEL_MED_HELM, f.equipment[EquipmentType.HEAD.id]?.id)
                assertEquals(0, f.inventory.getItemCount(Items.STEEL_MED_HELM))
            }
        }
    }

    @Test fun `already bound inventory and worn options are preserved without duplicate registration`() {
        N11Fixture().use { f ->
            var invCalls = 0
            var wornCalls = 0
            f.repo.bindItem(Items.AVAS_ATTRACTOR, 4) { invCalls++ }
            f.repo.bindEquipmentOption(Items.AVAS_ACCUMULATOR, 1) { wornCalls++ }
            prepare(f)
            f.repo.executeItem(f.player, Items.AVAS_ATTRACTOR, 4)
            f.repo.executeEquipmentOption(f.player, Items.AVAS_ACCUMULATOR, 1)
            assertEquals(1, invCalls)
            assertEquals(1, wornCalls)
            assertEquals(null, f.attrs[AvasAssembler.GATHERING_STOPPED])
        }
    }

    @Test fun `each legacy weighted table contains exactly two thousand outcomes`() {
        for ((id, table) in listOf(Items.AVAS_ATTRACTOR to AvasAssembler.ATTRACTOR_TABLE, Items.AVAS_ACCUMULATOR to AvasAssembler.ACCUMULATOR_TABLE)) {
            assertEquals(2000, table.sumOf { it.second })
            assertEquals(table.toMap(), (0 until 2000).map { AvasAssembler.attracted(it, id) }.groupingBy { it }.eachCount())
        }
    }
}
