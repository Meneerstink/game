package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.cfg.FacialExpression
import gg.rsmod.plugins.api.ext.chatNpc
import gg.rsmod.plugins.api.ext.chatPlayer
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.mechanics.store.StoreCatalogue

/**
 * Talk-to and Interact-with on the 667 pets (Loyalty store, owner 2026-10-24):
 *  - N301: a Loyalty store pet answers Talk-to with one of its species' conversations ([LoyaltyPetChats]) - player and pet in turn,
 *    the pet's head in the cache's creature pose (N257), its sound only on a line where it makes an animal noise.
 *  - N303: "Interact-with" on a 667 cat (Kitten, Cat, Overgrown cat, Hell-kitten, Hellcat, Overgrown hellcat - every cat npc
 *    that has the option in the cache) did nothing. 2009scape KittenInteractDialogue: "Stroke" (player 9224, cat 9173,
 *    "Purr...purr...", "That cat sure loves to be stroked.") and "Chase vermin" (a rat in reach squeaks and gets away). The donor's
 *    third choice, "Shoo away", makes the cat run off for good - not on a pet bought with Loyalty Points, so it is left out.
 */
object ClassicPetTalk {
    /** The pet items the Loyalty store sells (the store's own list, so a new store pet is covered without a second list). */
    val LOYALTY_PET_ITEMS: Set<Int> by lazy {
        StoreCatalogue.ENTRIES.filter { it.shop == StoreCatalogue.Shop.LOYALTY && Pets.isPetItem(it.purchaseItem) }.map { it.purchaseItem }.toSet()
    }

    /** The conversations of follower [npc]: a Loyalty store pet's species chats, else null (it keeps its transcript line). */
    fun chatsFor(npc: Int): List<List<LoyaltyPetChats.Line>>? {
        val variant = Pets.variantOfNpc(npc) ?: return null
        if (!variant.classic || variant.item !in LOYALTY_PET_ITEMS) return null
        return LoyaltyPetChats.speciesOf(variant.name)?.let { LoyaltyPetChats.CHATS[it] }
    }

    /** One random conversation of [npc]; its noise lines sound with the species' own voice ([PetVoices.speciesVoice]). */
    suspend fun QueueTask.talk(npc: Npc, chats: List<List<LoyaltyPetChats.Line>>) {
        val chat = chats[player.world.random(chats.indices)]
        val voice = Pets.variantOfNpc(npc.id)?.let { PetVoices.speciesVoice(it) }
        for (line in chat) {
            if (line.pet) {
                if (line.sound && voice != null) FamiliarAudio.play(player, voice, volume = 255, at = npc.tile)
                chatNpc(line.text, npc = npc.id, facialExpression = FacialExpression.HAPPY_TALKING)
            } else {
                chatPlayer(line.text, facialExpression = FacialExpression.HAPPY_TALKING)
            }
        }
    }

    const val PLAYER_STROKE = 9224
    const val CAT_STROKE = 9173

    /** A rat by name ("Rat", "Giant rat", "Dungeon rat") - not a "Pirate". */
    private val RAT = Regex("\\brats?\\b", RegexOption.IGNORE_CASE)

    /** How far a cat looks for a rat to chase (2009scape: 8 tiles from the cat). */
    const val VERMIN_RANGE = 8

    /** N303: Interact-with on a 667 cat - the player's own one. */
    suspend fun QueueTask.interact(cat: Npc) {
        if (Pets.current(player) !== cat) {
            player.message("This isn't your pet.")
            return
        }
        when (options("Stroke", "Chase vermin", title = "Interact with ${cat.def.name}")) {
            1 -> {
                cat.facePawn(player)
                player.animate(PLAYER_STROKE)
                cat.animate(CAT_STROKE)
                // N403 (owner 2026-10-24: "Stroke geen geluid"): the cat purrs - the 667 cache's own purr.
                FamiliarAudio.play(player, gg.rsmod.plugins.api.cfg.Sfx.PURR, volume = 255, at = cat.tile)
                cat.forceChat("Purr...purr...")
                chatPlayer("That cat sure loves to be stroked.")
            }
            2 -> {
                player.forceChat("Go on puss...kill that rat!")
                val rat = player.world.npcs.entries.filterNotNull().firstOrNull { n ->
                    n !== cat && RAT.containsMatchIn(n.def.name) && n.tile.height == cat.tile.height && n.tile.isWithinRadius(cat.tile, VERMIN_RANGE)
                }
                if (rat == null) {
                    player.message("Your cat cannot get to its prey.")
                    return
                }
                cat.facePawn(rat)
                Pets.variantOfNpc(cat.id)?.let { PetVoices.speciesVoice(it) }?.let { FamiliarAudio.play(player, it, volume = 255, at = cat.tile) }
                cat.forceChat("Meeeoooooowwww!")
                FamiliarAudio.play(player, gg.rsmod.plugins.api.cfg.Sfx.RAT_SQUEAK_1, volume = 255, at = rat.tile)
                rat.forceChat("Eeek!")
                wait(5)
                cat.facePawn(player)
                player.message("The rat manages to get away!")
            }
        }
    }
}
