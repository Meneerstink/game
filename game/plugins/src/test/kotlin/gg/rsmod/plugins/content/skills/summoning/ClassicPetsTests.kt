package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeMap
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.container.key.INVENTORY_KEY
import gg.rsmod.game.model.entity.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * N216 (owner 2026-10-23: a baby dragon or terrier from the store landed on the floor instead of following) and N243 (every way a pet
 * arrives gives the OSRS message and the "Getting pet" sound): the 667 pets follow like the OSRS pets, and a bought pet goes through
 * Pets.award - never a duplicate, since the player paid for it. N88 part 3: the generated voice table serves every voiced follower.
 */
class ClassicPetsTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()

        @BeforeClass
        @JvmStatic
        fun load() {
            assertTrue(Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File(DIR, "pets_667.tsv")) > 180)
            PetVoices.load(File(DIR, "pet_voices.tsv"))
        }

        @AfterClass
        @JvmStatic
        fun reset() {
            Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv"), File("does-not-exist"))
        }
    }

    /** The item ids of the 78 Store's "Pets" listing (StoreCatalogue), read from its source like the store builds it. */
    private fun storePets(): List<Int> {
        val source = File("src/main/kotlin/gg/rsmod/plugins/content/mechanics/store/StoreCatalogue.kt").readText()
        val block = source.substringBefore(".forEach { (item, price) -> list += itemEntry(l, item, price, \"Pets\") }").substringAfterLast("listOf(")
        return Regex("""Items\.(\w+) to""").findAll(block).map { gg.rsmod.plugins.api.cfg.Items::class.java.getField(it.groupValues[1]).getInt(null) }.toList()
    }

    @Test
    fun `every 667 pet item follows as its own npc with Pick-up, and none collides with an OSRS pet`() {
        val defs = SummoningTestCache.definitions
        val classic = Pets.variants.filter { it.classic }
        assertTrue(classic.size >= 190, "${classic.size} 667 pets")
        assertEquals(Pets.variants.size, Pets.variants.map { it.item }.toSet().size, "an item is two pets")
        assertEquals(Pets.variants.size, Pets.variants.map { it.npc }.toSet().size, "an npc follows for two pets")
        val wrong = mutableListOf<String>()
        classic.forEach { v ->
            val item = defs.getNullable(ItemDef::class.java, v.item)
            val npc = defs.getNullable(NpcDef::class.java, v.npc)
            if (item == null || item.name != v.name) wrong += "${v.pet}: item ${v.item} is '${item?.name}'"
            if (npc == null || npc.options.none { it.equals("Pick-up", ignoreCase = true) }) wrong += "${v.pet}: npc ${v.npc} has no Pick-up"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun `N216 - every pet the 78 Store sells comes out as a follower`() {
        val sold = storePets()
        assertTrue(sold.size >= 20, "store pets found: $sold")
        val notPets = sold.filterNot { Pets.isPetItem(it) }.map { "$it ${SummoningTestCache.definitions.get(ItemDef::class.java, it).name}" }
        // N300 (owner 2026-10-24): the Pet rock (an item, not a follower) left the store, so every pet sold follows. N295 (owner 2026-10-24): the Baby icefiend follows like
        // every other pet - npc 7713 got Pick-up and Talk-to, the item Drop (cache), and it is in the 667 pet table.
        assertEquals(emptyList(), notPets)
    }

    @Test
    fun `N243 N437 - a new bought pet goes to the backpack with the OSRS message, a second one of a pet the player has is never given`() {
        val dragons = Pets.variants.filter { it.classic && it.pet == Pets.variants.first { v -> v.classic && v.name == "Baby dragon" }.pet }
        assertTrue(dragons.size >= 2, "baby dragon colours: $dragons")
        val item = dragons[0].item
        val inventory = ItemContainer(SummoningTestCache.definitions, INVENTORY_KEY)
        val world = mockk<World>(relaxed = true)
        every { world.definitions } returns SummoningTestCache.definitions
        val player = mockk<Player>(relaxed = true)
        val messages = mutableListOf<gg.rsmod.game.message.Message>()
        every { player.world } returns world
        every { player.attr } returns AttributeMap()
        every { player.inventory } returns inventory
        val bank = ItemContainer(SummoningTestCache.definitions, 800, gg.rsmod.game.model.container.ContainerStackType.STACK)
        every { player.bank } returns bank
        // K01 deel 3: the duplicate rule looks in every container the player has (Possessions).
        every { player.containers } returns hashMapOf(INVENTORY_KEY to inventory, gg.rsmod.game.model.container.key.BANK_KEY to bank)
        every { player.write(*anyVararg<gg.rsmod.game.message.Message>()) } answers { messages += (args[0] as Array<*>).filterIsInstance<gg.rsmod.game.message.Message>() }
        mockkObject(Pets)
        try {
            every { Pets.hasFollower(player) } returns true
            assertEquals(Pets.Award.BACKPACK, Pets.awardItem(player, item, purchased = true))
            assertEquals(1, inventory.getItemCount(item), "the bought one is in the backpack")
            val texts = messages.filterIsInstance<gg.rsmod.game.message.impl.MessageGameMessage>().map { it.message }
            assertTrue(texts.any { "sneaking into your backpack" in it }, "OSRS backpack message: $texts")
            // N437 (owner 2026-10-30 "altijd maar 1 pet"): a drop, a purchase, another colour - never a second one.
            assertEquals(Pets.Award.DUPLICATE, Pets.awardItem(player, item), "a drop of an owned pet stays the OSRS duplicate")
            assertEquals(Pets.Award.DUPLICATE, Pets.awardItem(player, item, purchased = true), "a bought one too")
            assertEquals(Pets.Award.DUPLICATE, Pets.awardItem(player, dragons[1].item, purchased = true), "another colour too")
            assertEquals(1, dragons.sumOf { inventory.getItemCount(it.item) }, "still one baby dragon")
            assertEquals("your inventory", Pets.whereOwned(player, dragons[0].pet))
            // The store: one per purchase (no Buy 5 / 10 / X for a pet).
            val storePet = gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.ENTRIES.first { Pets.isPetItem(it.purchaseItem) }
            assertTrue(!gg.rsmod.plugins.content.mechanics.store.StoreUi.multiple(storePet), "a pet is bought one at a time")
        } finally {
            unmockkObject(Pets)
        }
    }

    @Test
    fun `N301 - every Loyalty store pet has 2-3 conversations of player and pet, its sound only on a line with an animal noise`() {
        val loyalty = ClassicPetTalk.LOYALTY_PET_ITEMS
        assertTrue(loyalty.size >= 20, "${loyalty.size} Loyalty pets")
        for (item in loyalty) {
            val v = Pets.variantOfItem(item)!!
            val chats = ClassicPetTalk.chatsFor(v.npc)
            assertTrue(chats != null && chats.size in 2..3, "${v.name} ($item): ${chats?.size} conversations")
            for (chat in chats!!) {
                assertTrue(chat.size in 2..4, "${v.name}: ${chat.size} lines")
                assertTrue(chat.any { it.pet } && chat.any { !it.pet }, "${v.name}: player and pet both speak")
                chat.forEach { line -> assertTrue(line.text.length <= 70, "${v.name}: line too long for one dialogue box: ${line.text}") }
            }
            // A pet with a noise line has a sound to make it with.
            if (chats.flatten().any { it.sound }) assertTrue(PetVoices.speciesVoice(v) != null, "${v.name} has no voice")
        }
        // The sound follows the line: only a pet line that opens with a noise in asterisks.
        assertTrue(LoyaltyPetChats.Line(true, "*Ook ook!* It is MY banana.").sound)
        assertTrue(!LoyaltyPetChats.Line(true, "It is MY banana.").sound)
        assertTrue(!LoyaltyPetChats.Line(false, "*Woof!*").sound, "the player does not bark")
        // Every species written down is used by a store pet (no dead conversations).
        val used = loyalty.mapNotNull { LoyaltyPetChats.speciesOf(Pets.variantOfItem(it)!!.name) }.toSet()
        assertEquals(LoyaltyPetChats.CHATS.keys, used)
    }

    @Test
    fun `every other 667 pet has 2-3 conversations of its own (owner 2026-10-29), sound only where it has a voice`() {
        val classic = Pets.variants.filter { it.classic }
        assertTrue(classic.size >= 150, "${classic.size} 667 pets")
        for (v in classic) {
            val chats = ClassicPetTalk.chatsFor(v.npc)
            assertTrue(chats != null && chats.size in 2..3, "${v.name}: ${chats?.size} conversations")
            for (chat in chats!!) {
                assertTrue(chat.size in 2..4, "${v.name}: ${chat.size} lines")
                assertTrue(chat.any { it.pet } && chat.any { !it.pet }, "${v.name}: player and pet both speak")
                chat.forEach { line -> assertTrue(line.text.length <= 70, "${v.name}: line too long for one dialogue box: ${line.text}") }
            }
            if (chats.flatten().any { it.sound }) assertTrue(PetVoices.speciesVoice(v) != null, "${v.name} has no voice")
        }
        // A grown 667 monkey talks like a monkey now, not with the baby's Loyalty lines.
        val monkey = classic.first { it.name == "Monkey" }
        assertEquals(ClassicPetChats.CHATS.getValue("Monkey"), ClassicPetTalk.chatsFor(monkey.npc))
        // Every species written down is used by a 667 pet (no dead conversations).
        assertEquals(ClassicPetChats.CHATS.keys, classic.mapNotNull { ClassicPetChats.speciesOf(it.name) }.toSet())
    }

    @Test
    fun `N405 - every 667 pet form has a voice, and the all-but-inaudible ones were replaced`() {
        val silent = Pets.variants.filter { it.classic && PetVoices.idleVoiceOf(it.npc) == null }.map { it.name }.distinct()
        assertEquals(emptyList(), silent, "667 pets without a sound")
        val sfx = gg.rsmod.plugins.api.cfg.Sfx
        // Rendered with the client's SynthSound: peak 18 (kitten mew), 16 (jungle monkey), 31 (vulture) of 128.
        val quiet = setOf(sfx.KITTENS_MEW, sfx.JUNGLE_MONKEY_1, sfx.VULTURE_1)
        assertEquals(emptyMap(), PetVoices.CLASSIC.filterValues { it in quiet })
    }

    @Test
    fun `N299 - no 667 pet uses a Varrock Museum display sound, monkey, lizards and penguin have their own calls`() {
        val museum = setOf(gg.rsmod.plugins.api.cfg.Sfx.VM_MONKEY, gg.rsmod.plugins.api.cfg.Sfx.VM_LIZARD, gg.rsmod.plugins.api.cfg.Sfx.VM_PENGUIN)
        assertEquals(emptyMap(), PetVoices.CLASSIC.filterValues { it in museum })
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.BREW_MONKEY_TALK, PetVoices.CLASSIC["Baby monkey"])
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.LIZARD_ATTACK, PetVoices.CLASSIC["Baby gecko"])
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.LIZARD_ATTACK, PetVoices.CLASSIC["Baby chameleon"])
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.PENG_PENGUIN_CALL, PetVoices.CLASSIC["Baby penguin"])
    }

    @Test
    fun `N303 - every 667 pet option in the cache has a handler - Interact-with on the cats included`() {
        val defs = SummoningTestCache.definitions
        val plugin = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/pets.plugin.kts").readText()
        val handled = setOf("pick-up", "talk-to", "interact-with")
        val missing = Pets.variants.filter { it.classic }.flatMap { v ->
            defs.get(NpcDef::class.java, v.npc).options.filterNotNull().filter { it.isNotBlank() }.map { it.lowercase() }.filter { it !in handled }.map { "${v.name} (${v.npc}): $it" }
        }.distinct()
        // The Clockwork cat's "Shoo" has no donor behaviour (reported to the owner, not invented).
        assertEquals(emptyList(), missing.filterNot { it.startsWith("Clockwork cat") && it.endsWith("shoo") })
        assertTrue("option = \"interact-with\"" in plugin && "option = \"talk-to\"" in plugin)
    }

    @Test
    fun `walking sounds - baby dragons, dogs and the crab step softly, every other pet walks silently`() {
        val sfx = gg.rsmod.plugins.api.cfg.Sfx
        fun step(item: Int) = PetVoices.stepSound(Pets.variantOfItem(item)!!)
        assertEquals(sfx.DRAGONSLAYER_DRAGONWALK, step(gg.rsmod.plugins.api.cfg.Items.BABY_DRAGON_12472))
        assertEquals(sfx.FOOT_TRAMP_1, step(gg.rsmod.plugins.api.cfg.Items.TERRIER_PUPPY_12512))
        assertEquals(sfx.TBCU_SPIDER, step(gg.rsmod.plugins.api.cfg.Items.BABY_GIANT_CRAB_12500))
        assertEquals(null, step(gg.rsmod.plugins.api.cfg.Items.BABY_MONKEY))
        assertTrue(PetVoices.STEP_VOLUME < 128, "soft")
        // Every puppy of the Loyalty store steps.
        ClassicPetTalk.LOYALTY_PET_ITEMS.map { Pets.variantOfItem(it)!! }.filter { it.name.endsWith(" puppy") }
            .forEach { assertEquals(sfx.FOOT_TRAMP_1, PetVoices.stepSound(it), it.name) }
    }

    @Test
    fun `N335 N338 N340-N345 - the reported OSRS pets walk with their boss's or family's step, the voiceless ones got a voice`() {
        val sfx = gg.rsmod.plugins.api.cfg.Sfx
        val steps = mapOf("Pet Penance Queen" to 8473, "Baron" to 4494, "TzRek-Zuk" to 4519, "TzRek-Jad" to 4519, "Lil' Nylo" to sfx.TBCU_SPIDER,
            "Little Nightmare" to sfx.FOOT_TRAMP_1, "Little Parasite" to sfx.TBCU_SPIDER, "Scorpia's offspring" to sfx.TBCU_SPIDER,
            "Nid" to sfx.TBCU_SPIDER, "Rax" to sfx.TBCU_SPIDER, "Soup" to sfx.FOOT_TRAMP_1, "Youngllef" to sfx.FOOT_TRAMP_1,
            "Corrupted Youngllef" to sfx.FOOT_TRAMP_1)
        steps.forEach { (name, sound) ->
            val v = Pets.variants.first { !it.classic && it.name == name }
            assertEquals(sound, PetVoices.stepSound(v), name)
        }
        val voices = mapOf("Beef" to sfx.COW_ATTACK, "Scurry" to sfx.RAT_SQUEAK_1, "TzRek-Zuk" to 410, "Jal-Nib-Rek" to 410, "TzRek-Jad" to 3858,
            "Soup" to sfx.TOAD2, "Youngllef" to sfx.BEAR_ATTACK, "Corrupted Youngllef" to sfx.BEAR_ATTACK)
        voices.forEach { (name, sound) -> assertEquals(sound, PetVoices.voiceOf(Pets.variants.first { !it.classic && it.name == name }.npc), name) }
    }

    @Test
    fun `N257 - a 667 pet's chat head and Follower Details head take the cache's creature pose, an OSRS pet keeps its own rig`() {
        val creature = gg.rsmod.plugins.api.ext.CreatureChathead
        val monkey = Pets.variantOfItem(gg.rsmod.plugins.api.cfg.Items.BABY_MONKEY)!!.npc
        assertTrue(creature.isCreaturePet(monkey))
        assertEquals(8373, creature.animation(gg.rsmod.plugins.api.cfg.FacialExpression.HAPPY_TALKING))
        assertEquals(8374, creature.animation(gg.rsmod.plugins.api.cfg.FacialExpression.SAD))
        // Every Loyalty store pet is a 667 creature pet (the poses were checked in the client for each species).
        val loyalty = gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.ENTRIES
            .filter { it.shop == gg.rsmod.plugins.content.mechanics.store.StoreCatalogue.Shop.LOYALTY && Pets.isPetItem(it.purchaseItem) }
        assertTrue(loyalty.isNotEmpty())
        loyalty.forEach { assertTrue(creature.isCreaturePet(Pets.variantOfItem(it.purchaseItem)!!.npc), "${it.purchaseItem}") }
        // An OSRS pet is not one: it keeps the OSRS chathead rig.
        assertTrue(!creature.isCreaturePet(Pets.variants.first { it.pet == "Nexling" }.npc))
        val ui = File("src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/SummoningUi.kt").readText()
        assertTrue("CreatureChathead.TALK.takeIf { gg.rsmod.plugins.api.ext.CreatureChathead.isCreaturePet(npcId) }" in ui, "Follower Details")
    }

    @Test
    fun `N88 part 3 - the voice table serves every voiced follower and Huberte's screech line`() {
        val rows = PetVoices.parse(File(DIR, "pet_voices.tsv").readLines())
        PetVoices.install(rows, Pets.variants)
        val nexling = Pets.variants.single { it.name == "Nexling" }
        assertEquals(rows.single { it.pet == "Nexling" }.localSynth, PetVoices.voiceOf(nexling.npc))
        val midnight = Pets.variants.single { it.name == "Midnight" }
        assertEquals(rows.single { it.variant == "Midnight" }.localSynth, PetVoices.voiceOf(midnight.npc), "a variant row wins over the pet's")
        // N88 (owner: every OSRS pet has a sound): the Pet Kraken speaks with its family voice since 2026-10-29 (pet_voices.tsv).
        val kraken = Pets.variants.first { it.pet == "Pet Kraken" }
        assertEquals(rows.first { it.pet == "Pet Kraken" }.localSynth, PetVoices.voiceOf(kraken.npc), "Pet Kraken has its voice")
        // N257: a 667 pet answers Talk-to with its species' sound (it had none).
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.BREW_MONKEY_TALK, PetVoices.voiceOf(Pets.variants.first { it.classic && it.name == "Baby monkey" }.npc))
        val huberte = Pets.variants.single { it.pet == "Huberte" }
        assertEquals(rows.single { it.line == "*screeches*" }.localSynth, PetVoices.lineSound(huberte.npc, "*screeches*"))
        assertEquals(null, PetVoices.lineSound(huberte.npc, "*bird noises*"))
        val voiced = Pets.variants.filterNot { it.classic }.count { PetVoices.voiceOf(it.npc) != null }
        assertTrue(voiced >= 150, "$voiced of ${Pets.variants.count { !it.classic }} OSRS followers have a voice")
    }
}
