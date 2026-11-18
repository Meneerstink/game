package gg.rsmod.plugins.content.activity.cox

/**
 * Captain Rimor at the CoX entrance (OSRS Wiki Captain_Rimor: Mount Quidamortem, map x=1234 y=3572 r=4, ops Talk-to / Claim-cape /
 * Set-difficulty, examine 'A high ranking officer of the Shayzien Army.'). Talk-to = Transcript:Captain_Rimor 'Standard dialogue' verbatim.
 * LATER (besluit 2a, Normal mode only): Claim-cape needs Challenge Mode completions (Xeric's guard .. champion). ONZEKER: Set-difficulty -
 * the transcript is marked incomplete for it (no lines published), so nothing is said or done (no invented text).
 */
CoxIds.npc(7595)?.let { rimor ->
    spawn_npc(npc = rimor, x = 1234, z = 3572, walkRadius = 4)
    on_npc_option(npc = rimor, option = "Talk-to") {
        player.queue {
            chatNpc("Beware the Chambers of Xeric, adventurer.", npc = rimor)
            chatNpc("These dungeons and the perils within them are unique. You will encounter both dangerous and mysterious creatures here.", npc = rimor)
            chatNpc("Remember that you may be kicked out at any time - any items dropped on the ground will be lost inside.", npc = rimor)
            chatNpc("Do not allow your curiosity to get the better of you.", npc = rimor)
            chatPlayer("Thanks for the warning.")
        }
    }
}
