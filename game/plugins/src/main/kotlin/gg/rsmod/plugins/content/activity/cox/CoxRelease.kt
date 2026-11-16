package gg.rsmod.plugins.content.activity.cox

/**
 * CoX release switch (like ToA's interim 'closed on live except admins', 07-11): while false the Chambers of Xeric are only reachable by
 * the test server / check accounts (CoxTestGate) and the Drop Viewer source + Collection Log page stay hidden, so no player sees a raid
 * that is not open. Set to true in the live bundle that opens the raid (owner: 'volledig geintegreerd zoals ToA').
 */
object CoxRelease {
    const val OPEN = true
}
