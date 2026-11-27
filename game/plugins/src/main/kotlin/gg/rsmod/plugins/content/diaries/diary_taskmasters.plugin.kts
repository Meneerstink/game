package gg.rsmod.plugins.content.diaries

import gg.rsmod.plugins.content.diaries.Diaries.Diary
import gg.rsmod.plugins.content.diaries.Diaries.Tier

/**
 * DIARIES-OSRS D3: the taskmasters (OSRS Wiki infobox ids + map pins): the 'Wedge' in front of Camelot (2759,3476), the Elder gnome
 * child south of the Grand Tree (2467,3459), the Lesser Fanatic in Edgeville (3121,3516). Talk-to: claim the next tier, get a lost
 * item back, or hear how the diary works.
 */
class Master(val npc: Int, val diary: Diary, val x: Int, val z: Int)

val MASTERS = listOf(
    Master(DiaryNpcs.WEDGE, Diary.KANDARIN, 2759, 3476),
    Master(DiaryNpcs.ELDER_GNOME_CHILD, Diary.WESTERN, 2467, 3459),
    Master(DiaryNpcs.LESSER_FANATIC, Diary.WILDERNESS, 3121, 3516),
    Master(DiaryNpcs.TOBY, Diary.VARROCK, 3225, 3415),
    Master(DiaryNpcs.TWO_PINTS, Diary.ARDOUGNE, 2574, 3323),
)

MASTERS.forEach { m ->
    spawn_npc(npc = m.npc, x = m.x, z = m.z, height = 0, walkRadius = 2)
    on_npc_option(npc = m.npc, option = "talk-to") { player.queue { talk(this, m) } }
}

suspend fun talk(task: gg.rsmod.game.model.queue.QueueTask, m: Master) {
    val player = task.player
    val area = m.diary.areaName
    task.chatNpc("Hello there. I keep track of the Achievement Diary for the $area area. Is there something I can help you with?")
    val opts = mutableListOf<String>()
    val claimable = DiaryRewards.claimable(player, m.diary)
    if (claimable != null) opts += "I'd like to claim my ${claimable.title.lowercase()} reward."
    if (Diaries.claimed(player, m.diary) > 0) opts += "I've lost my reward item."
    if (m.diary == Diary.KANDARIN && Diaries.hasClaimed(player, Diary.KANDARIN, Tier.HARD)) opts += "Change my Camelot Teleport destination."
    if (m.diary == Diary.VARROCK && Diaries.hasClaimed(player, Diary.VARROCK, Tier.MEDIUM)) opts += "Change my Varrock Teleport destination."
    if (m.diary == Diary.ARDOUGNE && Diaries.hasClaimed(player, Diary.ARDOUGNE, Tier.HARD)) opts += "Change my Watchtower Teleport destination."
    opts += "What is the Achievement Diary?"
    opts += "Nothing, thanks."
    when (opts[task.options(*opts.toTypedArray()) - 1]) {
        opts.firstOrNull { it.startsWith("I'd like to claim") } -> {
            task.chatPlayer(opts[0])
            val msg = DiaryRewards.claim(player, m.diary)
            task.chatNpc(msg)
            if (msg.startsWith("Here is your reward")) {
                val next = Tier.values().getOrNull(Diaries.claimed(player, m.diary))
                if (next != null) task.chatNpc("If you complete the ${next.title.lowercase()} tasks in the $area area, come back and I'll reward you again.")
                else task.chatNpc("You have completed every task in the $area area. Well done!")
            }
        }
        "I've lost my reward item." -> {
            task.chatPlayer("I've lost my reward item.")
            task.chatNpc(DiaryRewards.reclaim(player, m.diary))
        }
        "Change my Camelot Teleport destination." -> {
            // Kandarin hard (OSRS Wiki): Camelot Teleport can teleport to Seers' Village bank instead.
            when (task.options("Camelot castle", "Seers' Village bank", title = "Where should Camelot Teleport take you?")) {
                1 -> player.attr[DiaryItems.CAMELOT_SEERS] = false
                2 -> player.attr[DiaryItems.CAMELOT_SEERS] = true
            }
            task.chatNpc("Your Camelot Teleport will now take you to ${if (player.attr[DiaryItems.CAMELOT_SEERS] == true) "Seers' Village bank" else "Camelot castle"}.")
        }
        "Change my Varrock Teleport destination." -> {
            // Varrock medium (OSRS Wiki): Varrock Teleport can go to the Grand Exchange instead of Varrock Square.
            when (task.options("Varrock Square", "Grand Exchange", title = "Where should Varrock Teleport take you?")) {
                1 -> player.attr[DiaryItems.VARROCK_GE] = false
                2 -> player.attr[DiaryItems.VARROCK_GE] = true
            }
            task.chatNpc("Your Varrock Teleport will now take you to ${if (player.attr[DiaryItems.VARROCK_GE] == true) "the Grand Exchange" else "Varrock Square"}.")
        }
        "Change my Watchtower Teleport destination." -> {
            // Ardougne hard (OSRS Wiki): Watchtower Teleport can teleport to Yanille instead.
            when (task.options("The Watchtower", "Yanille", title = "Where should Watchtower Teleport take you?")) {
                1 -> player.attr[DiaryItems.WATCHTOWER_YANILLE] = false
                2 -> player.attr[DiaryItems.WATCHTOWER_YANILLE] = true
            }
            task.chatNpc("Your Watchtower Teleport will now take you to ${if (player.attr[DiaryItems.WATCHTOWER_YANILLE] == true) "Yanille" else "the Watchtower"}.")
        }
        "What is the Achievement Diary?" -> {
            task.chatPlayer("What is the Achievement Diary?")
            task.chatNpc("It's a record of the tasks you've done around the world. Open the Achievement Diaries panel in your quest tab - the green gem - to see the $area tasks.")
            task.chatNpc("Each set of tasks - easy, medium, hard and elite - earns a reward from me once they're all done. You must claim them in order.")
        }
        else -> {}
    }
}
