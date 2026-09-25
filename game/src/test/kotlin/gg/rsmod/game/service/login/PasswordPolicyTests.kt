package gg.rsmod.game.service.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Audit S-04/S-06/S-08: one password rule for registration, ::changepass and the recovery page. */
class PasswordPolicyTests {
    @Test
    fun `five to twenty letters and digits`() {
        assertNull(PasswordPolicy.problem("anudd", "abc12"))
        assertNull(PasswordPolicy.problem("anudd", "a".repeat(20)))
        assertEquals(PasswordPolicy.RULE_MESSAGE, PasswordPolicy.problem("anudd", "abcd"))
        assertEquals(PasswordPolicy.RULE_MESSAGE, PasswordPolicy.problem("anudd", "a".repeat(21)))
        assertEquals(PasswordPolicy.RULE_MESSAGE, PasswordPolicy.problem("anudd", "pass word"))
        assertEquals(PasswordPolicy.RULE_MESSAGE, PasswordPolicy.problem("anudd", ""))
    }

    @Test
    fun `not the account name`() {
        assertEquals(PasswordPolicy.SAME_AS_NAME_MESSAGE, PasswordPolicy.problem("Anudd", "anudd"))
        assertEquals(PasswordPolicy.SAME_AS_NAME_MESSAGE, PasswordPolicy.problem("big bob", "BIGBOB"))
    }
}
