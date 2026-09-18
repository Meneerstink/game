package gg.rsmod.net.codec.game

/**
 * Every client->server packet the revision-667 client can send, with its size exactly as ClientProt declares it
 * (C:\RSPS\2011scape-client ... com/jagex/ClientProt.java): a fixed byte count, -1 for a byte length prefix, -2 for a
 * short length prefix.
 *
 * Used only for opcodes that data/packets.yml does not register. Before, [GamePacketDecoder] answered such an opcode by
 * discarding every readable byte - which also threw away the unrelated packets that arrived in the same read and, with
 * ISAAC-encrypted opcodes, desynchronised every following opcode until relog (owner 2026-09-18 P0: interactions that
 * "sometimes" do nothing). Now the packet is skipped by its exact size and the stream stays aligned.
 */
object ClientProtSizes {
    const val VARIABLE_BYTE = -1
    const val VARIABLE_SHORT = -2

    val SIZES: Map<Int, Int> =
        mapOf(
            0 to 7, // OPLOC4
            1 to -1, // FRIENDS_CHAT_CHANGE
            2 to 7, // OPLOC2
            3 to 4, // RESUME_P_COUNTDIALOG
            4 to 8, // IF_BUTTON3
            5 to 4, // EVENT_CAMERA_POSITION
            6 to 8, // SEND_PING_REPLY
            7 to -1, // RESUME_P_NAMEDIALOG
            8 to -1, // FRIENDLIST_DEL
            9 to 3, // OPNPC1
            10 to 8, // IF_BUTTON8
            11 to 7, // OPLOC1
            12 to 5, // MOVE_GAMECLICK
            13 to 2, // RESUME_P_OBJDIALOG
            14 to 3, // OPPLAYER1
            15 to 4, // TRANSMITVAR_VERIFYID
            16 to 0, // NO_TIMEOUT
            17 to -1, // IGNORELIST_ADD
            18 to 8, // IF_BUTTON7
            19 to 3, // OPPLAYER10
            20 to 8, // IF_BUTTON9
            21 to 15, // OPOBJT
            22 to 2, // RESUME_P_HSLDIALOG
            23 to 1, // CHAT_SETMODE
            24 to 7, // OPOBJ3
            25 to 8, // IF_BUTTON10
            26 to 16, // IF_BUTTOND
            27 to 7, // OPOBJ6
            28 to 3, // OPNPC5
            29 to -1, // EVENT_MOUSE_MOVE
            30 to -1, // MESSAGE_QUICKCHAT_PUBLIC
            31 to 3, // OPNPC3
            32 to -1, // CLAN_KICKUSER
            33 to 0, // MAP_BUILD_COMPLETE
            34 to 4, // WORLDLIST_FETCH
            35 to 7, // OPOBJ5
            36 to -1, // MESSAGE_PUBLIC
            37 to -1, // CLIENT_DETAILOPTIONS_STATUS
            38 to -1, // IGNORELIST_DEL
            39 to 12, // APCOORDT
            40 to 11, // OPPLAYERT
            41 to -1, // FRIEND_SETRANK
            42 to 15, // OPLOCT
            43 to 3, // OPPLAYER9
            44 to -1, // AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL
            45 to 7, // OPOBJ1
            46 to 3, // OPPLAYER4
            47 to 7, // OPLOC6
            48 to 0, // CUTSCENE_FINISHED
            49 to 3, // OPPLAYER7
            50 to 3, // OPPLAYER5
            51 to -1, // FRIENDLIST_ADD
            52 to 8, // IF_BUTTON4
            53 to 3, // OPPPLAYER2
            54 to 6, // RESUME_PAUSEBUTTON
            55 to 7, // OPOBJ2
            56 to 0, // CLOSE_MODAL
            57 to 3, // SET_CHATFILTERSETTINGS
            58 to 2, // VIDEO_END
            59 to -1, // RESUME_P_STRINGDIALOG
            60 to -1, // CLANCHANNEL_KICKUSER
            61 to 8, // IF_BUTTON1
            62 to 3, // OPPLAYER8
            63 to 4, // FACE_SQUARE
            64 to 8, // IF_BUTTON2
            65 to 11, // OPNPCT
            66 to 3, // OPNPC2
            67 to 3, // OPNPC4
            68 to -1, // EVENT_KEYBOARD
            69 to 7, // OPLOC5
            70 to -1, // CLIENT_CHEAT
            71 to 4, // DETECT_MODIFIED_CLIENT
            72 to -2, // MESSAGE_PRIVATE
            73 to 16, // IF_BUTTONT
            74 to -1, // A_CLIENT_PROT___82
            75 to 4, // SOUND_SONGEND
            76 to 7, // OPLOC3
            77 to 3, // OPPLAYER3
            78 to -1, // REFLECTION_CHECK_REPLY
            79 to -1, // MESSAGE_QUICKCHAT_PRIVATE
            80 to -1, // SEND_SNAPSHOT
            81 to 8, // IF_BUTTON5
            82 to 3, // OPPLAYER6
            83 to 18, // MOVE_MINIMAPCLICK
            84 to 6, // EVENT_MOUSE_CLICK
            85 to 2, // PING_STATISTICS
            86 to 7, // OPOBJ4
            87 to 6, // WINDOW_STATUS
            88 to -1, // URL_REQUEST
            89 to 4, // CLICKWORLDMAP
            90 to -1, // CLIENT_PROT_90
            91 to 8, // IF_BUTTON6
            92 to 3, // OPNPC6
            93 to 1, // EVENT_APPLET_FOCUS
        )
}