package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.RspsPaths
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.npcs.TranscriptDialogue
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.io.File

/** Wiki Bloodhound rev15375113/Call follower: dig for a trail at the PLAYER'S current tile.
 * Both ordinary and existing owner-approved Upgraded Bloodhound use the actual personal follower.
 */
object CluePetDig {
    const val SOURCE_DIG = 14498 // Jagex gameval DOG_UPDATE_DIG; absent from pinned2686, no guessed local sequence.
    fun parse(text: String): Int {
        val lines=text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.first().startsWith("#") && lines.first().endsWith("applied=true"))
        val row=lines.filterNot { it.startsWith('#') }.single().split('\t')
        require(row.size==3 && row[0]=="seq" && row[1].toInt()==SOURCE_DIG)
        return row[2].toInt().also { require(it in 0..65534) }
    }
    private val sequence by lazy {
        File(RspsPaths.data("cfg/clue/pet-dig.tsv")).takeIf(File::isFile)?.readText()?.let(::parse)
    }
    fun animation(world: World): Int?=sequence?.takeIf { world.definitions.getNullable(AnimDef::class.java,it)!=null }
    fun eligible(player: Player,npc: Npc): Boolean = player.isOnline && !player.isDead() &&
        Pets.current(player)===npc && Pets.baseNpc(npc.id)==TranscriptDialogue.BLOODHOUND &&
        npc.world===player.world && npc.isSpawned() && !npc.isDead()

    /** Missing source animation spends no clue, attempt or progress. Never animates the player with a spade. */
    fun dig(player: Player,npc: Npc,visual: (World)->Int? = ::animation,
            progress: (Player)->Boolean = { ClueScrollManager.tryDig(it) }): Boolean {
        if(!eligible(player,npc))return false
        val anim=visual(player.world) ?: run {
            println("PROBLEM CLUE Bloodhound dig source14498 unavailable; clue retained")
            return false
        }
        npc.animate(anim)
        progress(player) // existing exact held-clue/point/guardian contract, not the follower's adjacent square
        return true
    }
}
