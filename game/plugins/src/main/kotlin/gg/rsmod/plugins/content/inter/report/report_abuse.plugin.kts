package gg.rsmod.plugins.content.inter.report

/**
 * Report Abuse (owner 2026-09-19: "report abuse button does not work"). The chat-bar button 751:13 ("Report Abuse", CS2 131
 * only clears varcstr 24) sends IF_BUTTON1 and nothing on the server opened the window. The 667 cache's Report Abuse window
 * is interface 594 (player select, rule list, Send-report, Ignore-player); its own CS2 drives every step and Send-report
 * sends SEND_SNAPSHOT (ReportAbuseHandler). The server only opens it and closes it on its Close / Cancel-report buttons.
 */
val REPORT_ABUSE_INTERFACE = 594
val REPORT_BUTTON_PARENT = 751
val REPORT_BUTTON_COMPONENT = 13

/** 594 components whose only option is Close / Cancel-report (decoded with InterfaceHookProbeTool layout 594). */
val REPORT_CLOSE_COMPONENTS = intArrayOf(3, 17, 48, 126, 127, 128, 131, 132, 133, 137)

on_button(interfaceId = REPORT_BUTTON_PARENT, component = REPORT_BUTTON_COMPONENT) {
    player.openInterface(REPORT_ABUSE_INTERFACE, InterfaceDestination.MAIN_SCREEN)
}

REPORT_CLOSE_COMPONENTS.forEach { component ->
    on_button(interfaceId = REPORT_ABUSE_INTERFACE, component = component) {
        player.closeInterface(REPORT_ABUSE_INTERFACE)
    }
}
