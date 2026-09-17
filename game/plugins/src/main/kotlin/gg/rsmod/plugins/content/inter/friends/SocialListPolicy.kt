package gg.rsmod.plugins.content.inter.friends

/**
 * RCV-010 D4: the add rule shared by the friends list and the ignore list. Adding a name that is already on the list
 * used to do nothing silently. Both donors agree on the refusal and its wording: Novite 667
 * `FriendsIgnores.addFriend/addIgnore` and Void `FriendsList.kt`/`IgnoreList.kt` ("<name> is already on your friends
 * list." / "<name> is already on your ignores list.").
 *
 * Deliberately not added (SOURCE_CONFLICT or single donor): self-add wording (Novite "You can't add yourself." vs Void
 * joke lines), list-size limits (Novite 200/100 with short text vs Void configurable max with member wording), and
 * Void's "remove from your ignore list first" cross-list check (Novite has none).
 */
object SocialListPolicy {
    /**
     * The revision-667 client holds at most 200 friends and 100 ignores; both donors use the same
     * sizes (Novite `FriendsIgnores` 200/100). Without a cap the persisted lists grow without bound
     * and every login/update fans out over all of them. The refusal wording differs between donors,
     * so a neutral line is used here.
     */
    enum class ListType(
        val label: String,
        val capacity: Int,
    ) {
        FRIENDS("friends", 200),
        IGNORES("ignores", 100),
    }

    fun addRefusal(
        list: ListType,
        name: String,
        current: Collection<String>,
    ): String? =
        when {
            current.any { it.equals(name, ignoreCase = true) } -> "$name is already on your ${list.label} list."
            current.size >= list.capacity -> "Your ${list.label} list is full."
            else -> null
        }
}
