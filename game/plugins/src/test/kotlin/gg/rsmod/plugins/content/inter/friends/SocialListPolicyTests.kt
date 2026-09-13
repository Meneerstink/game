package gg.rsmod.plugins.content.inter.friends

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RCV-010 D4: duplicate adds are refused with the donor wording on both social lists. */
class SocialListPolicyTests {
    @Test
    fun `duplicate add is refused on every list, new names are allowed`() {
        SocialListPolicy.ListType.values().forEach { list ->
            val current = listOf("Zezima", "Lynx Titan")
            assertEquals("Zezima is already on your ${list.label} list.", SocialListPolicy.addRefusal(list, "Zezima", current), "$list exact")
            assertEquals("zezima is already on your ${list.label} list.", SocialListPolicy.addRefusal(list, "zezima", current), "$list case-insensitive")
            assertNull(SocialListPolicy.addRefusal(list, "Durial321", current), "$list new name")
            assertNull(SocialListPolicy.addRefusal(list, "Zezima", emptyList()), "$list empty list")
        }
        assertEquals("friends", SocialListPolicy.ListType.FRIENDS.label)
        assertEquals("ignores", SocialListPolicy.ListType.IGNORES.label)
    }
}
