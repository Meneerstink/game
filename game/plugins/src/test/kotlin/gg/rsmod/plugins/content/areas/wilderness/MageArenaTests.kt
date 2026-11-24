package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.Server
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.game.action.EquipAction
import it.unimi.dsi.fastutil.bytes.Byte2ByteOpenHashMap
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.game.model.Hit
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.attr.COMPLETED_MAGE_ARENA
import gg.rsmod.game.model.attr.prayedAtStatue
import gg.rsmod.game.model.attr.receivedStaff
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.SlayerAssignment
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.EQUIPMENT_KEY
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.model.timer.TimerMap
import gg.rsmod.game.model.timer.GOD_SPELL_CHARGE_TIMER
import gg.rsmod.game.model.attr.GOD_SPELL_CHARGE_ATTR
import gg.rsmod.game.model.shop.Shop
import gg.rsmod.game.model.shop.PurchasePolicy
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.game.plugin.Plugin
import gg.rsmod.game.model.attr.INTERACTING_OBJ_ATTR
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.plugins.api.cfg.Objs
import gg.rsmod.plugins.content.magic.TeleportType
import gg.rsmod.plugins.content.magic.canTeleport
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.lang.ref.WeakReference
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class MageArenaTests {
    @Test fun `all normal god capes and staves use metadata Magic60 base requirement including duplicate Guthix cape`() {
        val metadata = ObjectMapper(YAMLFactory()).readTree(File("../../data/cfg/items.yml")).toList()
        for (id in listOf(2412, 2413, 2414, 2415, 2416, 2417, 10720)) {
            val eq = metadata.single { it.path("id").asInt() == id }.path("equipment")
            val reqs = eq.path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }
            assertEquals(mapOf(Skills.MAGIC to 60), reqs, "item $id metadata")
            for (base in listOf(59, 60)) {
                val a = Account()
                val def = ItemDef(id).apply {
                    equipSlot = eq.path("equip_slot").asInt()
                    equipType = -1
                    skillReqs = Byte2ByteOpenHashMap().apply { reqs.forEach { (skill, level) -> put(skill.toByte(), level.toByte()) } }
                }
                every { a.defs.get(ItemDef::class.java, id) } returns def
                every { a.world.plugins } returns PluginRepository(a.world)
                a.skills.setBaseLevel(Skills.MAGIC, base)
                a.skills.setCurrentLevel(Skills.MAGIC, 99) // a temporary boost must not satisfy wield requirements
                a.inventory[0] = Item(id)
                assertEquals(if (base == 59) EquipAction.Result.FAILED_REQUIREMENTS else EquipAction.Result.SUCCESS,
                    EquipAction.equip(a.player, Item(id), 0), "item $id base $base")
                if (base == 59) assertEquals(id, a.inventory[0]?.id) else assertEquals(id, a.equipment[def.equipSlot]?.id)
            }
        }
    }

    @Test fun `god spells dispatch their Prayer Defence and NPC Magic drains at impact without stacking below base`() {
        val a = Account()
        val caster = Account().player
        val effect = MagicCombatStrategy.javaClass.getDeclaredMethod("applyEffect",
            Pawn::class.java, Pawn::class.java, CombatSpell::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        fun cast(target: Pawn, spell: CombatSpell) { effect.invoke(MagicCombatStrategy, caster, target, spell, 0) }
        a.skills.setBaseLevel(Skills.PRAYER, 3)
        a.skills.setCurrentLevel(Skills.PRAYER, 3)
        repeat(5) { cast(a.player, CombatSpell.SARADOMIN_STRIKE) }
        assertEquals(0, a.skills.getCurrentLevel(Skills.PRAYER))
        val npc = Npc(1, MageArena.FOE_TILE, a.world).apply { stats = Npc.Stats(5) }
        for ((spell, playerSkill, npcSkill) in listOf(
            Triple(CombatSpell.CLAWS_OF_GUTHIX, Skills.DEFENCE, NpcSkills.DEFENCE),
            Triple(CombatSpell.FLAMES_OF_ZAMORAK, Skills.MAGIC, NpcSkills.MAGIC))) {
            a.skills.setBaseLevel(playerSkill, 99)
            npc.stats.setMaxLevel(npcSkill, 99)
            a.skills.setCurrentLevel(playerSkill, 99); npc.stats.setCurrentLevel(npcSkill, 99)
            cast(a.player, spell); cast(npc, spell)
            assertEquals(94, a.skills.getCurrentLevel(playerSkill))
            assertEquals(94, npc.stats.getCurrentLevel(npcSkill))
            repeat(3) { cast(a.player, spell); cast(npc, spell) }
            assertEquals(94, a.skills.getCurrentLevel(playerSkill))
            assertEquals(94, npc.stats.getCurrentLevel(npcSkill))
            a.skills.setCurrentLevel(playerSkill, 99)
            cast(a.player, spell)
            assertEquals(94, a.skills.getCurrentLevel(playerSkill), "recovery to base permits a fresh drain")
            a.skills.setCurrentLevel(playerSkill, 119)
            cast(a.player, spell)
            assertEquals(113, a.skills.getCurrentLevel(playerSkill), "boosted stats are not immune (Contact! update)")
            a.skills.setBaseLevel(playerSkill, 1); a.skills.setCurrentLevel(playerSkill, 1)
            cast(a.player, spell)
            assertEquals(0, a.skills.getCurrentLevel(playerSkill))
        }
        val strategy = File("src/main/kotlin/gg/rsmod/plugins/content/combat/strategy/MagicCombatStrategy.kt").readText()
        assertTrue("if (landHit) {\n            pawnHit.hit.addAction { applyEffect" in strategy.replace("\r\n", "\n"),
            "splashes must never schedule the impact effect")
    }

    @Test fun `Charge recast cooldown ends at100 while its700 tick buff remains and refreshes on recast`() {
        val a = Account()
        val timers = TimerMap()
        every { a.player.timers } returns timers
        MageArenaCharge.activate(a.player)
        fun tick() { timers.getTimers().entries.forEach { it.setValue(it.value - 1) } }
        repeat(99) { tick() }
        assertTrue(timers.has(MageArenaCharge.COOLDOWN))
        assertEquals(601, timers[GOD_SPELL_CHARGE_TIMER])
        tick()
        assertFalse(timers.has(MageArenaCharge.COOLDOWN))
        assertEquals(600, timers[GOD_SPELL_CHARGE_TIMER])
        assertEquals(true, a.attrs[GOD_SPELL_CHARGE_ATTR])
        MageArenaCharge.activate(a.player)
        assertEquals(700, timers[GOD_SPELL_CHARGE_TIMER])
        assertEquals(100, timers[MageArenaCharge.COOLDOWN])
    }

    @Test fun `compiled entrance lever requires MA1 while bank and arena exit remain available before MA1`() {
        val a = Account()
        every { a.defs.get(ObjectDef::class.java, any()) } answers {
            ObjectDef(secondArg()).apply { options[0] = "Pull" }
        }
        every { a.world.isSpawned(any<GameObject>()) } returns true
        every { a.player.interfaces.currentModal } returns -1
        every { a.player.executePlugin<Unit>(any()) } answers { firstArg<Plugin.() -> Unit>().invoke(Plugin(a.player)) }
        val repo = PluginRepository(a.world)
        every { a.world.plugins } returns repo
        var teleports = 0
        mockkStatic("gg.rsmod.plugins.content.magic.PawnExtKt")
        try {
            every { a.player.canTeleport(TeleportType.LEVER, any()) } answers { teleports++; true }
            URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader).use {
                it.loadClass("gg.rsmod.plugins.content.objs.levers.Levers_plugin")
                    .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                    .newInstance(repo, a.world, mockk<Server>(relaxed = true))
                fun pull(id: Int, tile: Tile) {
                    a.attrs[INTERACTING_OBJ_ATTR] = WeakReference<GameObject>(DynamicObject(id, 0, 0, tile))
                    assertTrue(repo.executeObject(a.player, id, 1))
                }
                pull(Objs.LEVER_9706, Tile(3104, 3956))
                assertEquals(0, teleports)
                pull(Objs.LEVER_5959, Tile(3090, 3956))
                pull(Objs.LEVER_9707, Tile(3105, 3952))
                assertEquals(2, teleports)
                a.complete()
                pull(Objs.LEVER_9706, Tile(3104, 3956))
                assertEquals(3, teleports)
            }
        } finally { unmockkStatic("gg.rsmod.plugins.content.magic.PawnExtKt") }
    }

    @Test fun `compiled guardian shop charges 80000 refuses staff sales and restocks one per100 ticks`() {
        val a = Account()
        every { a.defs.get(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).apply { cost = 80000; stacks = id == Items.COINS_995; tradeable = true }
        }
        // ItemCurrency.sellPriceOf reads the definition through getNullable.
        every { a.defs.getNullable(ItemDef::class.java, any()) } answers {
            ItemDef(secondArg()).apply { cost = 80000; stacks = id == Items.COINS_995; tradeable = true }
        }
        // The script binds "talk-to" and "trade"; on_npc_option checks the npc's cache options (Chamber Guardian: Talk-to, Trade).
        every { a.defs.get(NpcDef::class.java, any()) } answers {
            NpcDef(secondArg()).also { it.options[0] = "Talk-to"; it.options[2] = "Trade" }
        }
        val repo = PluginRepository(a.world)
        every { a.world.plugins } returns repo
        URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader).use {
            it.loadClass("gg.rsmod.plugins.content.areas.wilderness.Chamber_guardian_plugin")
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(repo, a.world, mockk<Server>(relaxed = true))
        }
        val field = PluginRepository::class.java.getDeclaredField("shops").apply { isAccessible = true }
        val shop = (field.get(repo) as Map<*, *>)["Mage Arena Staves"] as Shop
        assertEquals(PurchasePolicy.BUY_NONE, shop.purchasePolicy)
        assertEquals(MageArena.STAFF_BY_CAPE.values.toList(), shop.items.filterNotNull().map { it.item })
        shop.cycle(a.world) // align to cycle1; normal periodic shop restock
        shop.items.filterNotNull().forEachIndexed { slot, item ->
            assertEquals(5, item.currentAmount)
            assertEquals(100, item.resupplyCycles)
            a.inventory.add(Items.COINS_995, 80000)
            shop.currency.sellToPlayer(a.player, shop, slot, 1)
            assertEquals(0, a.inventory.getItemCount(Items.COINS_995))
            assertEquals(1, a.inventory.getItemCount(item.item))
            assertEquals(4, item.currentAmount)
            shop.currency.buyFromPlayer(a.player, shop, a.inventory.getItemIndex(item.item, skipAttrItems = false), 1)
            assertEquals(1, a.inventory.getItemCount(item.item))
            assertEquals(0, a.inventory.getItemCount(Items.COINS_995))
        }
        repeat(99) { shop.cycle(a.world) }
        assertTrue(shop.items.filterNotNull().all { it.currentAmount == 4 })
        shop.cycle(a.world)
        assertTrue(shop.items.filterNotNull().all { it.currentAmount == 5 })
    }

    private class Account {
        val player = mockk<Player>(relaxed = true)
        val attrs = AttributeMap()
        val defs = mockk<DefinitionSet>()
        val world = mockk<World>(relaxed = true)
        val skills = SkillSet(25)
        val inventory = ItemContainer(defs, INVENTORY_KEY)
        val equipment = ItemContainer(defs, EQUIPMENT_KEY)
        init {
            every { defs.get(ItemDef::class.java, any()) } answers { ItemDef(secondArg()) }
            every { defs.get(NpcDef::class.java, any()) } answers { NpcDef(secondArg()) }
            every { defs.get(ObjectDef::class.java, any()) } answers {
                ObjectDef(secondArg()).apply { options[0] = "Pray at"; options[1] = "Step-into" }
            }
            every { world.definitions } returns defs
            every { player.world } returns world
            every { player.attr } returns attrs
            every { player.skills } returns skills
            every { player.inventory } returns inventory
            every { player.equipment } returns equipment
            every { player.isOnline } returns true
            every { player.isDead() } returns false
            every { player.tile } returns MageArena.ARRIVAL
        }
        fun complete() { attrs[COMPLETED_MAGE_ARENA] = true; attrs[prayedAtStatue] = true }
    }

    @Test fun `60 Magic means base level not a temporary boost and false completion flag is not complete`() {
        val a = Account()
        a.skills.setBaseLevel(Skills.MAGIC, 59)
        a.skills.setCurrentLevel(Skills.MAGIC, 75)
        a.attrs[COMPLETED_MAGE_ARENA] = false
        assertFalse(MageArena.completed(a.player))
        assertFalse(MageArena.canStart(a.player))
        a.skills.setBaseLevel(Skills.MAGIC, 60)
        assertTrue(MageArena.canStart(a.player))
        a.complete()
        assertFalse(MageArena.canStart(a.player))
    }

    @Test fun `Charge unlock needs the first staff claim and persists after losing staff or cape`() {
        val a = Account()
        assertFalse(MageArena.canCharge(a.player))
        a.complete()
        a.inventory.add(Items.STAFF_OF_THE_DEAD)
        assertFalse(MageArena.canCharge(a.player), "upgraded staff ownership does not prove the first reward")
        a.inventory.add(Items.SARADOMIN_CAPE)
        assertEquals(MageArena.StaffResult.GRANTED, MageArena.claimFirstStaff(a.player))
        a.inventory.removeAll()
        assertTrue(MageArena.canCharge(a.player), "Charge needs no cape or staff equipped to cast")
    }

    @Test fun `all normal cape and god staff pairs reject conflicting equips in either order including Guthix duplicate`() {
        val a = Account()
        val capes = MageArena.STAFF_BY_CAPE + (Items.GUTHIX_CAPE_10720 to Items.GUTHIX_STAFF)
        capes.forEach { (cape, matchingStaff) ->
            MageArena.STAFF_BY_CAPE.values.forEach { staff ->
                a.equipment[EquipmentType.CAPE.id] = Item(cape)
                assertEquals(staff == matchingStaff, MageArena.canEquip(a.player, staff))
                a.equipment[EquipmentType.WEAPON.id] = Item(staff)
                assertEquals(staff == matchingStaff, MageArena.canEquip(a.player, cape))
            }
        }
        assertTrue(MageArena.compatibleGear(null, Items.SARADOMIN_STAFF))
        assertTrue(MageArena.compatibleGear(Items.GUTHIX_CAPE, null))
        assertTrue(MageArena.canEquip(a.player, Items.SHARK))
    }

    @Test fun `wearable legacy Guthix duplicate still entitles the completed player to the matching first staff`() {
        for (worn in listOf(false, true)) {
            val a = Account(); a.complete()
            if (worn) a.equipment[EquipmentType.CAPE.id] = Item(Items.GUTHIX_CAPE_10720)
            else a.inventory.add(Items.GUTHIX_CAPE_10720)
            assertEquals(MageArena.StaffResult.GRANTED, MageArena.claimFirstStaff(a.player))
            assertEquals(1, a.inventory.getItemCount(Items.GUTHIX_STAFF))
            assertEquals(MageArena.StaffResult.ALREADY_CLAIMED, MageArena.claimFirstStaff(a.player))
        }
    }

    @Test fun `first staff cannot be claimed by visiting the room or by dropping the cape`() {
        val a = Account()
        a.inventory.add(Items.SARADOMIN_CAPE)
        assertEquals(MageArena.StaffResult.NOT_COMPLETED, MageArena.claimFirstStaff(a.player))
        a.complete()
        a.inventory.removeAll()
        assertEquals(MageArena.StaffResult.NO_CAPE, MageArena.claimFirstStaff(a.player))
        assertNull(a.attrs[receivedStaff])
    }

    @Test fun `full inventory preserves free staff entitlement and retry cannot duplicate reward`() {
        val a = Account(); a.complete()
        a.inventory.add(Items.GUTHIX_CAPE)
        a.inventory.add(Items.SHARK, 27)
        assertEquals(MageArena.StaffResult.INVENTORY_FULL, MageArena.claimFirstStaff(a.player))
        assertNull(a.attrs[receivedStaff])
        a.inventory.remove(Items.SHARK, 1)
        assertEquals(MageArena.StaffResult.GRANTED, MageArena.claimFirstStaff(a.player))
        assertEquals(1, a.inventory.getItemCount(Items.GUTHIX_STAFF))
        assertEquals(MageArena.StaffResult.ALREADY_CLAIMED, MageArena.claimFirstStaff(a.player))
        assertEquals(1, a.inventory.getItemCount(Items.GUTHIX_STAFF))
    }

    @Test fun `all three first staves match the cape including a worn cape`() {
        MageArena.STAFF_BY_CAPE.forEach { (cape, staff) ->
            val a = Account(); a.complete()
            a.equipment[EquipmentType.CAPE.id] = gg.rsmod.game.model.item.Item(cape)
            assertEquals(MageArena.StaffResult.GRANTED, MageArena.claimFirstStaff(a.player))
            assertEquals(1, a.inventory.getItemCount(staff))
            assertEquals(1, a.equipment.getItemCount(cape))
        }
    }

    @Test fun `Kolodion rejects melee ranged cannon poison and another player at impact`() {
        val a = Account()
        val npc = Npc(a.player, MageArena.FORMS.first().npc, Tile(3105, 3933), a.world)
        MageArena.installImmunity(npc)
        for (style in listOf(null, CombatClass.MELEE, CombatClass.RANGED, CombatClass.MAGIC)) {
            val hit = Hit.Builder().addHit(20, HitType.REGULAR_HIT.id).build()
            hit.source = a.player; hit.sourceCombatClass = style
            npc.hitModifier!!(hit)
            assertEquals(if (style == CombatClass.MAGIC) 20 else 0, hit.hitmarks.single().damage)
        }
        val foreign = Hit.Builder().addHit(20, HitType.MAGIC.id).build()
        foreign.source = mockk<Player>(); foreign.sourceCombatClass = CombatClass.MAGIC
        npc.hitModifier!!(foreign)
        assertEquals(0, foreign.hitmarks.single().damage)
        for (type in HitType.values()) {
            assertEquals(if (type == HitType.MAGIC || type == HitType.CRIT_MAGIC) 20 else 0,
                MageArena.modifyIncomingDamage(npc, a.player, type, 20))
        }
        assertEquals(0, MageArena.modifyIncomingDamage(npc, foreign.source!!, HitType.MAGIC, 20))
        val breach = Npc(14446, MageArena.FOE_TILE, a.world)
        assertEquals(20, MageArena.modifyIncomingDamage(breach, a.player, HitType.MELEE, 20))
    }

    @Test fun `actual compiled script publishes OSRS stats real HP god magic and no ordinary respawn`() {
        val a = Account()
        val repo = PluginRepository(a.world)
        every { a.world.plugins } returns repo
        URLClassLoader(arrayOf(File("scripts/build/classes/kotlin/main").toURI().toURL()), javaClass.classLoader).use {
            it.loadClass("gg.rsmod.plugins.content.areas.wilderness.Mage_arena_plugin")
                .getConstructor(PluginRepository::class.java, World::class.java, Server::class.java)
                .newInstance(repo, a.world, mockk<Server>(relaxed = true))
        }
        val defs = MageArena.FORMS.map { repo.allNpcCombatDefs().getValue(it.npc) }
        assertEquals(listOf(3, 65, 78, 78, 107), defs.map { it.lifepoints })
        assertEquals(listOf(7, 7, 7, 7, 4), defs.map { it.attackSpeed })
        assertTrue(defs.all { it.spell == 503 && it.respawnDelay == 0 && it.aggressiveRadius == 0 })
        assertEquals(listOf(false, false, false, false, true), defs.map { it.completesEncounter })
        assertTrue(defs.dropLast(1).all { it.slayerAssignment == null })
        assertEquals(SlayerAssignment.BLACK_DEMON, defs.last().slayerAssignment)
        assertEquals(107.0, defs.last().slayerXp)
        assertTrue(NpcSpecies.DEMON in defs.last().species)
        MageArena.FORMS.zip(defs).forEach { (f, d) ->
            assertEquals(listOf(f.attack, f.strength, f.defence, f.magic, 1), d.stats)
        }
    }

    @Test fun `five real owned deaths advance only their own duel and final callback cannot be replayed`() {
        val a = Account()
        val other = Account()
        val repo = PluginRepository(a.world)
        every { a.world.plugins } returns repo
        val spawns = mutableListOf<Npc>()
        every { a.world.spawn(any<Npc>()) } answers {
            firstArg<Npc>().also { it.index = spawns.size + 1; it.setCurrentLifepoints(10); spawns += it }
            true
        }
        // Start at the same private spawn routine the landing queue invokes; no server, teleport or client needed.
        val spawn = MageArena.javaClass.getDeclaredMethod("spawn", Player::class.java, Int::class.javaPrimitiveType, Tile::class.java)
        spawn.isAccessible = true
        spawn.invoke(MageArena, a.player, 0, MageArena.FOE_TILE)
        assertEquals(1, spawns.size)
        repeat(5) { form ->
            val foe = spawns.last()
            assertSame(a.player, foe.owner)
            assertFalse(foe.respawnOverride!!)
            assertEquals(MageArena.FORMS[form].npc, foe.id)
            foe.damageMap.add(a.player, 10)
            foe.setCurrentLifepoints(0)
            MageArena.killed(foe)
            val count = spawns.size
            MageArena.killed(foe)
            assertEquals(count, spawns.size, "stale death must not create another transformation")
            assertEquals(form + 1, a.attrs[MageArena.FORM])
            assertFalse(MageArena.completed(other.player))
        }
        assertEquals(5, spawns.size)
        assertTrue(MageArena.completed(a.player))
        assertNull(MageArena.active(a.player))
    }

    @Test fun `shared attack layer retains all three existing 20 damage god spells for every form`() {
        NpcAttacks.load(File("../../data/cfg/npcs/npc-attacks.json"))
        val a = Account()
        MageArena.FORMS.forEach { form ->
            val npc = Npc(a.player, form.npc, MageArena.FOE_TILE, a.world)
            assertTrue(NpcAttacks.handles(npc))
            val attacks = NpcAttacks.rowFor(form.npc)!!.attacks
            assertEquals(setOf("saradomin_strike", "claws_of_guthix", "flames_of_zamorak"), attacks.map { it.id }.toSet())
            assertTrue(attacks.flatMap { it.hits }.all { it.offense == "magic" && it.max == 200 })
        }
    }
}
