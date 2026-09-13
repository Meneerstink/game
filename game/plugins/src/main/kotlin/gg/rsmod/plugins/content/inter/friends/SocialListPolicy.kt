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
    enum class ListType(val label: String) {
        FRIENDS("friends"),
        IGNORES("ignores"),
    }

    fun addRefusal(
        list: ListType,
        name: String,
        current: Collection<String>,
    ): String? =
        if (current.any { it.equals(name, ignoreCase = true) }) "$name is already on your ${list.label} list." else null
}
