package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.combat.CombatConfigs
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.inter.attack.AttackTab
import io.mockk.*
import java.io.File
import kotlin.test.*

class Dmm2WeaponPresentationTests {
    private val directory = File("../../data/cfg/dmm2")
    private fun boot() = Dmm2Boot.install(directory)

    @Test fun `real corrupted claws resolve the same normal swing cue as ordinary claws in every melee style`() {
        boot()
        val f = DeadmanPlayerFixture()
        every { f.definitions.getNullable(AnimDef::class.java, any()) } returns AnimDef(393)
        mockkObject(CombatConfigs)
        try {
            for ((style, sound) in listOf(StyleType.SLASH to Sfx.HACKSWORD_SLASH, StyleType.STAB to Sfx.HACKSWORD_STAB,
                    StyleType.CRUSH to Sfx.HACKSWORD_SLASH)) {
                every { CombatConfigs.getCombatStyle(f.player) } returns style
                f.equipment[3] = Item(Items.DRAGON_CLAWS)
                val ordinary = MeleeCombatStrategy.attackSound(f.player, 393)
                f.equipment[3] = Item(Dmm2Ids.ItemId.CLAWS.local!!).putAttr(ItemAttribute.CHARGES, 37)
                assertEquals(sound, ordinary)
                assertEquals(ordinary, MeleeCombatStrategy.attackSound(f.player, 393))
                assertEquals(37, f.equipment[3]!!.getAttr(ItemAttribute.CHARGES))
                assertEquals(24960, f.equipment[3]!!.id)
            }
        } finally { unmockkObject(CombatConfigs) }
    }

    @Test fun `frame sounds suppress a second server cue and missing equipment emits no sound`() {
        boot()
        val f = DeadmanPlayerFixture()
        val framed = mockk<AnimDef>()
        every { framed.hasFrameSounds } returns true
        every { f.definitions.getNullable(AnimDef::class.java, any()) } returns framed
        f.equipment[3] = Item(Dmm2Ids.ItemId.CLAWS.local!!)
        assertNull(MeleeCombatStrategy.attackSound(f.player, 393))
        f.equipment[3] = null
        assertNull(MeleeCombatStrategy.attackSound(f.player, 393))
    }

    @Test fun `all five corrupted special weapons dispatch to the base callback with the physical item and normal energy cost`() {
        boot()
        val f = DeadmanPlayerFixture()
        mockkObject(AttackTab)
        try {
            var energy = 100
            every { AttackTab.canAffordSpecial(f.player, any()) } answers { energy >= secondArg<Int>() }
            every { AttackTab.spendSpecial(f.player, any()) } answers { energy -= secondArg<Int>() }
            every { AttackTab.refundSpecial(f.player, any()) } answers { energy += secondArg<Int>() }
            for ((family, base, cost) in listOf(
                Triple(Dmm2Ids.ItemId.VOIDWAKER, Items.VOIDWAKER, 50),
                Triple(Dmm2Ids.ItemId.CLAWS, Items.DRAGON_CLAWS, 50),
                Triple(Dmm2Ids.ItemId.GODSWORD, Items.ARMADYL_GODSWORD, 50),
                Triple(Dmm2Ids.ItemId.DARK_BOW, Items.DARK_BOW, 55),
                Triple(Dmm2Ids.ItemId.VOLATILE, Items.VOLATILE_NIGHTMARE_STAFF, 55))) {
                val physical = family.local!!
                f.equipment[3] = Item(physical).putAttr(ItemAttribute.CHARGES, 19)
                var calls = 0
                SpecialAttacks.register(cost, base) { assertEquals(physical, player.equipment[3]!!.id); calls++ }
                assertTrue(SpecialAttacks.hasSpecialAttack(physical))
                energy = 100
                assertEquals(SpecialAttacks.Outcome.PERFORMED, SpecialAttacks.perform(f.player, mockk<Pawn>(relaxed = true), f.world))
                assertEquals(1, calls); assertEquals(100 - cost, energy)
                energy = cost - 1
                assertEquals(SpecialAttacks.Outcome.NOT_USED, SpecialAttacks.perform(f.player, mockk<Pawn>(relaxed = true), f.world))
                assertEquals(1, calls); assertEquals(cost - 1, energy)
                assertEquals(physical, f.equipment[3]!!.id); assertEquals(19, f.equipment[3]!!.getAttr(ItemAttribute.CHARGES))
            }
            listOf(Dmm2Ids.ItemId.TWISTED_BOW, Dmm2Ids.ItemId.SCYTHE, Dmm2Ids.ItemId.SHADOW).forEach {
                assertFalse(SpecialAttacks.hasSpecialAttack(it.local!!))
            }
        } finally { unmockkObject(AttackTab) }
    }

    @Test fun `swords staves bows and cosmetic states resolve only presentation without replacing physical stats`() {
        boot()
        assertEquals(Sfx.HACKSWORD_SLASH, CorruptedWeapons.swingSound(Dmm2Ids.ItemId.VOIDWAKER.local!!, StyleType.SLASH))
        assertEquals(Sfx.HACKSWORD_STAB, CorruptedWeapons.swingSound(Dmm2Ids.ItemId.KHOPESH.local!!, StyleType.STAB))
        for (family in listOf(Dmm2Ids.ItemId.SCYTHE, Dmm2Ids.ItemId.SCYTHE_EMPTY)) {
            assertEquals(Sfx.SCYTHE_SLASH, CorruptedWeapons.swingSound(family.local!!, StyleType.SLASH))
            assertEquals(Sfx.SCYTHE_DOUBLE, CorruptedWeapons.swingSound(family.local!!, StyleType.CRUSH))
        }
        for (family in listOf(Dmm2Ids.ItemId.SHADOW, Dmm2Ids.ItemId.SHADOW_EMPTY, Dmm2Ids.ItemId.VOLATILE))
            assertEquals(Sfx.STAFF_CRUSH, CorruptedWeapons.swingSound(family.local!!, StyleType.CRUSH))
        for ((family, base) in listOf(Dmm2Ids.ItemId.GODSWORD to Items.ARMADYL_GODSWORD,
                Dmm2Ids.ItemId.TWISTED_BOW to Items.TWISTED_BOW, Dmm2Ids.ItemId.DARK_BOW to Items.DARK_BOW,
                Dmm2Ids.ItemId.VOLATILE to Items.VOLATILE_NIGHTMARE_STAFF))
            assertEquals(base, CorruptedWeapons.presentationBase(family.local!!))
        NigelRewardStore.active!!.contract.skins.forEach { assertEquals(it.base, CorruptedWeapons.presentationBase(it.cosmetic)) }
        assertNull(CorruptedWeapons.attackAnimation(Dmm2Ids.ItemId.SCYTHE.local!!, 0), "Await the real new seq mapping")
        assertEquals(8056, CorruptedWeapons.SCYTHE_ATTACK_SOURCE)
    }
}
