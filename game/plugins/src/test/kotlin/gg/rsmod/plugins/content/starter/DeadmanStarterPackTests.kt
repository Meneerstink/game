package gg.rsmod.plugins.content.starter

import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.attr.NEW_ACCOUNT_ATTR
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.content.mechanics.deadman.DeadmanPlayerFixture
import gg.rsmod.plugins.content.newplayer.NewPlayerConfig
import kotlin.test.*

class DeadmanStarterPackTests {
    private val f = DeadmanPlayerFixture()
    private val ids = DeadmanStarterPack.Ids(700, 701, 702, 703, 704, 705, 706, 707)
    private val pack = NewPlayerConfig.current.starterChestItem
    init {
        f.attr[NEW_ACCOUNT_ATTR] = true
        (700..707).forEach { f.define(it) }
        listOf(pack, Items.DRAMEN_STAFF, Items.DEADMANS_SKULL, 900).forEach { f.define(it) }
        f.define(Items.COINS_995, true); f.define(Items.TELEPORT_TO_HOUSE, true)
        DeadmanStarterPack.install(ids)
        // PROJECT48 SIGILS: a starter pack can hold a random sigil (one per 24 hours).
        gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigil.values().forEach { f.define(it.unattunedId) }
    }

    private fun sigilsHeld(): Int =
        f.inventory.filterNotNull().count { gg.rsmod.plugins.content.mechanics.deadman.sigils.Sigils.isUnattunedItem(it.id) }

    @Test fun `a starter pack holds one random sigil per 24 hours and never one without effect`() {
        val drops = gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilDrops
        f.inventory[0] = Item(pack)
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(1, sigilsHeld(), "the first pack holds a sigil")
        assertNotNull(f.attr[drops.STARTER_SIGIL_AT], "the 24 hours start with the sigil")
        f.inventory.rawItems.indices.forEach { f.inventory[it] = null }
        f.inventory[0] = Item(pack)
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(0, sigilsHeld(), "a second pack within 24 hours holds none")
        val given = f.attr[drops.STARTER_SIGIL_AT]!!
        assertFalse(drops.starterSigilReady(f.player, given + drops.STARTER_SIGIL_SECONDS - 1))
        assertTrue(drops.starterSigilReady(f.player, given + drops.STARTER_SIGIL_SECONDS))
        // Owner 22-11: the 24-hour starter sigil is a tier 1 sigil.
        assertEquals(1, drops.rollStarterSigil(f.player, given + drops.STARTER_SIGIL_SECONDS)?.tier)
        assertTrue(drops.STARTER_POOL.isNotEmpty() && drops.STARTER_POOL.all { it.tier == 1 })
    }

    @Test fun `a full inventory keeps the starter sigil for the next pack`() {
        val drops = gg.rsmod.plugins.content.mechanics.deadman.sigils.SigilDrops
        f.inventory[0] = Item(pack)
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        // A later pack holds fewer items than the first (money and tabs are given once), so the room it needs is measured on a second
        // pack - opened inside the 24 hours, so without a sigil.
        f.inventory.rawItems.indices.forEach { f.inventory[it] = null }
        f.inventory[0] = Item(pack)
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(0, sigilsHeld())
        val used = f.inventory.rawItems.count { it != null }
        // Refill exactly so that the pack's own items fit and nothing else does.
        f.inventory.rawItems.indices.forEach { f.inventory[it] = null }
        f.attr.remove(drops.STARTER_SIGIL_AT)
        f.inventory[0] = Item(pack)
        (1..(28 - used)).forEach { f.inventory[it] = Item(900) }
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(0, sigilsHeld(), "no room: no sigil")
        assertNull(f.attr[drops.STARTER_SIGIL_AT], "and no 24-hour mark, so the next pack holds it")
    }

    @Test fun `introduction and repeated opening never farm money or tabs`() {
        repeat(2) { NewPlayerStart.grantStarterPack(f.player) }
        assertEquals(1_000_000L, f.count(Items.COINS_995))
        assertEquals(100L, f.count(Items.TELEPORT_TO_HOUSE))
        assertEquals(1L, f.count(pack))
        repeat(10) {
            assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
            val bow = f.inventory.filterNotNull().first { it.id == ids.bow }
            assertEquals(2000, bow.getAttr(ItemAttribute.CHARGES))
            assertEquals(500, f.inventory.filterNotNull().first { it.id == ids.staff }.getAttr(ItemAttribute.CHARGES))
            assertEquals((it + 1) * 10, f.attr[DeadmanStarterPack.CHRONICLE_CHARGES])
            assertFalse(f.inventory.filterNotNull().first { it.id == ids.chronicle }.hasAnyAttr())
            assertEquals(1_000_000L, f.count(Items.COINS_995)); assertEquals(100L, f.count(Items.TELEPORT_TO_HOUSE))
            f.inventory.rawItems.indices.filter { f.inventory[it]?.id !in setOf(Items.COINS_995, Items.TELEPORT_TO_HOUSE) }.forEach { f.inventory[it] = null }
            assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.reclaim(f.player))
        }
    }

    @Test fun `legacy unopened reward migrates once while already opened never pays again`() {
        f.attr[NewPlayerStart.STARTER_PACK_GIVEN] = true
        f.inventory[0] = Item(pack)
        DeadmanStarterPack.migrate(f.player)
        assertEquals(1, f.attr[DeadmanStarterPack.INTRO_REWARD])
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(1_000_000L, f.count(Items.COINS_995))
        DeadmanStarterPack.migrate(f.player)
        assertEquals(DeadmanStarterPack.Result.ALREADY_GIVEN, DeadmanStarterPack.grantIntroductionReward(f.player))
        val opened = DeadmanPlayerFixture()
        opened.attr[NewPlayerStart.STARTER_PACK_GIVEN] = true
        DeadmanStarterPack.migrate(opened.player)
        assertEquals(2, opened.attr[DeadmanStarterPack.INTRO_REWARD])
        val unknownLegacy = DeadmanPlayerFixture()
        DeadmanStarterPack.migrate(unknownLegacy.player)
        assertEquals(DeadmanStarterPack.Result.ALREADY_GIVEN, DeadmanStarterPack.grantIntroductionReward(unknownLegacy.player))
    }

    @Test fun `pack in bank prevents reclaim and eleven space gate is atomic`() {
        f.bank[0] = Item(pack)
        assertEquals(DeadmanStarterPack.Result.ALREADY_OWNED, DeadmanStarterPack.reclaim(f.player))
        f.bank[0] = null
        f.inventory[0] = Item(pack)
        (1..17).forEach { f.inventory[it] = Item(900) }
        assertEquals(DeadmanStarterPack.Result.NO_SPACE, DeadmanStarterPack.open(f.player))
        assertEquals(1L, f.count(pack)); assertEquals(0L, f.count(ids.sword))
        f.inventory[17] = null // Eleven free meets the wiki gate, but twelve nonstackable tuna must also fit.
        assertEquals(DeadmanStarterPack.Result.NO_SPACE, DeadmanStarterPack.open(f.player))
        assertEquals(1L, f.count(pack)); assertEquals(0L, f.count(ids.sword))
        (7..16).forEach { f.inventory[it] = null }
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.open(f.player))
        assertEquals(12L, f.count(ids.tuna))
    }

    @Test fun `full inventory and bank preserve unpaid marker and retry reward without partial cash`() {
        f.inventory.rawItems.indices.forEach { f.inventory[it] = Item(900) }
        f.bank.rawItems.indices.forEach { f.bank[it] = Item(900) }
        assertEquals(DeadmanStarterPack.Result.NO_SPACE, DeadmanStarterPack.grantIntroductionReward(f.player))
        assertEquals(1, f.attr[DeadmanStarterPack.INTRO_REWARD])
        assertEquals(0L, f.count(Items.COINS_995))
        f.inventory[0] = null // cash fits, tabs do not: still no partial payment
        assertEquals(DeadmanStarterPack.Result.NO_SPACE, DeadmanStarterPack.grantIntroductionReward(f.player))
        assertEquals(0L, f.count(Items.COINS_995))
        f.inventory[1] = null
        assertEquals(DeadmanStarterPack.Result.OK, DeadmanStarterPack.grantIntroductionReward(f.player))
        assertEquals(2, f.attr[DeadmanStarterPack.INTRO_REWARD])
    }

    @Test fun `charge copies do not alias and last shot destroys weapon`() {
        val bow = Item(ids.bow).putAttr(ItemAttribute.CHARGES, 2)
        val last = DeadmanStarterPack.afterAttack(bow)!!
        assertEquals(2, DeadmanStarterPack.charges(bow)); assertEquals(1, DeadmanStarterPack.charges(last))
        assertNull(DeadmanStarterPack.afterAttack(last))
        assertTrue(DeadmanStarterPack.isWeapon(ids.sword)); assertFalse(DeadmanStarterPack.isWeapon(900))
    }
}
