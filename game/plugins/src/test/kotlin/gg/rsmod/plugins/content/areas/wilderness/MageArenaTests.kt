package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.Server
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
import gg.rsmod.game.model.skill.SkillSet
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSpecies
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URLClassLoader
import kotlin.test.*

class MageArenaTests {
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
