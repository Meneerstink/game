package gg.rsmod.game.tools.importer

import gg.rsmod.game.tools.importer.OsrsInterfacePortTool.C

/** Static equivalents of cache2686 CC_CREATE. No cache writes here; the existing transaction tool owns PLAN/apply.
 * Component ids are the append-only server contract, not local interface ids. Source scripts are pinned in phase5-sources.
 */
object Dmm2WindowPort {
    const val REWARD_TITLE = 56
    const val REWARD_CLOSE = 66
    const val REWARD_FIRST = 67
    const val REWARD_STRIDE = 12
    const val REWARD_INFO_TITLE = 151
    const val REWARD_INFO_ITEM = 152
    const val REWARD_INFO_STOCK = 153
    const val REWARD_INFO_COST = 154
    const val REWARD_INFO_TEXT = 155
    const val SIDE_FIRST = 14
    const val SIDE_TITLE = 42
    const val SIDE_ITEM = 43
    const val SIDE_OWNED = 44
    const val SIDE_VALUE = 45
    const val SIDE_CLOSE_TEXT = 46
    const val VIEW_ICON = 193
    const val HELP_ICON = 194
    const val FILTER_ARROW = 197
    const val FILTER_LABEL = 198
    const val INFO_FRAME_FIRST = 199
    const val INFO_FRAME_TITLE = 200
    const val INFO_FRAME_CLOSE = 210
    /** Server translation of 7155/7142 layout, allocated by the existing importer; never a guessed local script id. */
    const val LAYOUT_SOURCE = 7155
    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)
    fun cardLayout(expanded: Boolean, index: Int): List<Rect> {
        require(index in 0..6)
        // Cache7144: grid width / nominal card width,3px gaps; expanded cards omit the name and are50px high.
        val grid = if (expanded) 437 else 231
        val columns = grid / if (expanded) 54 else 110
        val width = (grid - (columns + 1)*3) / columns
        val height = if (expanded) 50 else 74
        val x = index%columns*(width+3)+3; val y = index/columns*(height+3)+3
        return listOf(Rect(x+1,y+1,width-2,height-2),Rect(x+4,y+4,width-8,height-8),
            Rect(x+4,y,width-8,4),Rect(x+4,y+height-4,width-8,4),Rect(x,y+4,4,height-8),Rect(x+width-4,y+4,4,height-8),
            Rect(x,y,4,4),Rect(x+width-4,y,4,4),Rect(x,y+height-4,4,4),Rect(x+width-4,y+height-4,4,4),
            Rect(x+width/2-18+2,y+7+if (expanded) 2 else 0,36,32),Rect(x+7,y+34,width-14,40))
    }

    /** 667 IF_SETPOSITION/IF_SETSIZE bridge; same footer/primitive format as DeathsOffice.scrollScript.
     * Args: component,x,y,width,height,widthMode,heightMode,xMode,yMode. Geometry comes from source7142/7144.
     */
    fun layoutScript(): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        val out = java.io.DataOutputStream(bytes)
        out.writeByte(0)
        fun local(index: Int) { out.writeShort(33); out.writeInt(index) }
        listOf(1,2,7,8,0).forEach(::local); out.writeShort(2000); out.writeByte(0)
        listOf(3,4,5,6,0).forEach(::local); out.writeShort(2001); out.writeByte(0)
        out.writeShort(21); out.writeByte(0)
        out.writeInt(13); listOf(9,0,0,9,0,0).forEach(out::writeShort)
        out.writeByte(0); out.writeShort(1)
        return bytes.toByteArray()
    }

    fun append(group: Int, list: MutableList<C>, sprites: MutableSet<Int>, extras: MutableList<String>, fonts: Map<Int, Int>) {
        fun add(type: Int, x: Int, y: Int, w: Int, h: Int, parent: Int, sprite: Int = -1, text: String = "", ops: List<String> = emptyList(),
                font: Int = 496, colour: Int = 0xff981f, wm: Int = 0, hm: Int = 0, xm: Int = 0, ym: Int = 0): Int {
            val id = list.size
            if (sprite >= 0) sprites += sprite
            list += C(id, type, x, y, w, h, parent, wm, hm, xm, ym, false, sprite = sprite, tiling = sprite >= 0,
                font = if (type == 4) fonts.getValue(font) else -1, text = text, lineHeight = if (type == 4) 16 else 0,
                alignX = 1, alignY = 1, shadow = true, colour = colour, ops = ops)
            return id
        }
        // Claude 17-11: look components up by id - an inserted thinbox (ids appended mid-list) makes list position != id
        // (806: list[8..11] overwrote ids 6..9 and duplicated 10/11 -> CacheTransaction verify failed, tx-20261117-130041 rolled back).
        fun byId(id: Int): C = list.first { it.id == id }
        fun layer(id: Int, x: Int, y: Int, w: Int, h: Int) {
            byId(id).apply { this.x = x; this.y = y; this.w = w; this.h = h }
        }
        fun button(parent: Int, width: Int, height: Int, label: String) {
            // 7139 -> 486 (enabled, not pressed): eight desktop slices913..920, then centred p11 text.
            listOf(intArrayOf(0,0,9,9,913), intArrayOf(width-9,0,9,9,914),
                intArrayOf(0,height-9,9,9,915), intArrayOf(width-9,height-9,9,9,916),
                intArrayOf(0,9,9,height-18,917),intArrayOf(9,0,width-18,9,918),
                intArrayOf(width-9,9,9,height-18,919),intArrayOf(9,height-9,width-18,9,920)).forEach { a ->
                // Source uses a -1px stretch for a 17px-high button; IF3 unsigned size cannot encode it.
                add(5,a[0],a[1],a[2].coerceAtLeast(0),a[3].coerceAtLeast(0),parent,sprite=a[4])
            }
            add(4,0,0,0,0,parent,text=label,font=494,wm=1,hm=1)
        }
        when (group) {
            230 -> {
                check(list.size == 17)
                // 703 -> 711 stoneframe, parent230:4 has resolved width230,height195. Source branch1972=desktop.
                add(5, 0, 0, 2, 2, 4, sprite = 297, wm = 1, hm = 1, xm = 1, ym = 1)
                add(4, 6, 6, 218, 23, 4, text = "Safe Deposit Box") // 18
                listOf(intArrayOf(0,0,32,32,824), intArrayOf(198,0,32,32,825), intArrayOf(0,163,32,32,826),
                    intArrayOf(198,163,32,32,827), intArrayOf(32,-13,166,32,820), intArrayOf(32,175,166,32,822),
                    intArrayOf(-13,32,32,131,821), intArrayOf(210,32,32,131,823), intArrayOf(0,17,32,32,829),
                    intArrayOf(198,17,32,32,830), intArrayOf(11,15,208,32,828)).forEach { a ->
                    add(5,a[0],a[1],a[2],a[3],4,sprite=a[4])
                }
                add(5,204,10,16,16,4,sprite=831,ops=listOf("Close")) // 30, script711 close13
                sprites += 832 // source hover; server adapter may swap on mouse events later
                repeat(10) { slot -> add(5,(slot%5)*39,(slot/5)*38,36,32,5,
                    ops=listOf("Withdraw-1","Withdraw-5","Withdraw-10","Withdraw-X","Withdraw-All","","","","","Examine")) }
                extras += "DMM2 stoneframe17..30 title18 close30; safe slots31..40 from1151/1156 (5x2,39x38)"
            }
            232 -> {
                check(list.size == 3)
                repeat(28) { slot -> add(5,16+(slot%4)*42,4+(slot/4)*36,36,32,2,
                    ops=listOf("Deposit-1","Deposit-5","Deposit-10","Deposit-X","Deposit-All","","","","","Examine")) }
                extras += "DMM2 inventory slots3..30 from1158/1161 (4x7,42x36,offset16/4)"
            }
            819 -> {
                check(list.size == 55)
                // 7142, desktop normal view: 200px info pane; left grid is 231px = two 111px cards.
                layer(9, 12, 35, 200, 53)
                layer(10, 6, 6, 218, 20)
                val filter = byId(13)
                list[list.indexOf(filter)] = C(13,0,0,0,26,0,10,1,1,0,0,false)
                layer(15, 6, 6, 218, 38)
                layer(14, 206, 0, 218, 20)
                // 228(title,16): desktop steel frame, divider and close.
                add(5,0,0,2,2,7,sprite=297,wm=1,hm=1,xm=1,ym=1)
                check(add(4,0,6,12,24,7,text="Deadman Reward Store",wm=1,xm=1) == REWARD_TITLE)
                listOf(intArrayOf(0,0,25,30,310,0,0,0,0), intArrayOf(0,0,25,30,311,0,0,2,0),
                    intArrayOf(0,0,25,30,312,0,0,0,2), intArrayOf(0,0,25,30,313,0,0,2,2),
                    intArrayOf(-15,0,36,60,172,0,1,0,1),intArrayOf(-15,0,36,60,315,0,1,2,1),
                    intArrayOf(0,-15,50,36,314,1,0,1,0),intArrayOf(0,-15,50,36,173,1,0,1,2),
                    intArrayOf(0,14,10,26,2546,1,0,1,0)).forEach { a ->
                    add(5,a[0],a[1],a[2],a[3],7,sprite=a[4],wm=a[5],hm=a[6],xm=a[7],ym=a[8])
                }
                check(add(5,3,6,26,23,7,sprite=535,ops=listOf("Close"),xm=2) == REWARD_CLOSE)
                sprites += 536
                // 7144 + 5288, struct3712: border parameters1387..1395,1478. Each row is twelve children.
                repeat(7) { index ->
                    val x = 3 + index%2*114; val y = 3 + index/2*77
                    val first = add(3,x+1,y+1,109,72,38,colour=0x726551,ops=listOf("Select","Buy-1","Buy-5","Buy-10","Buy-50","","","","","Examine"))
                    check(first == REWARD_FIRST + REWARD_STRIDE*index)
                    // Unfilled outline (5288:8..30); interior and nine-slice sprites have no click ops.
                    add(5,x+4,y+4,103,66,38,sprite=297)
                    listOf(intArrayOf(4,0,103,4,3525),intArrayOf(4,70,103,4,3530),
                        intArrayOf(0,4,4,66,3527),intArrayOf(107,4,4,66,3528),
                        intArrayOf(0,0,4,4,3524),intArrayOf(107,0,4,4,3526),
                        intArrayOf(0,70,4,4,3529),intArrayOf(107,70,4,4,3531)).forEach { a ->
                        add(5,x+a[0],y+a[1],a[2],a[3],38,sprite=a[4])
                    }
                    add(5,x+39,y+7,36,32,38)
                    add(4,x+7,y+34,97,40,38,font=495)
                }
                check(add(4,10,10,161,32,35) == REWARD_INFO_TITLE)
                add(5,72,48,36,32,35)
                add(4,0,85,50,18,35,wm=1)
                add(4,0,107,50,36,35,wm=1,font=495)
                add(4,10,52,161,300,35,font=495)
                byId(35).scrollH = 300
                byId(38).scrollH = 311
                // Desktop7245: (225-18)/4=51px buttons,4px gaps, in native225x44 parent24.
                (26..29).forEach { id -> layer(id, 4+(id-26)*55,26,51,18) }
                extras += "DMM2 OMNISHOP title56 close66 products67..150 stride12 info151..155; DBrow929/table39/currency1206=Deadman Points"
            }
            806 -> {
                check(list.size == 14)
                repeat(28) { slot -> check(add(5,(slot%4)*42,(slot/4)*36,36,32,0,
                    ops=listOf("Select","Sell-1","Sell-5","Sell-10","Sell-50","","","","","Examine")) == SIDE_FIRST+slot) }
                check(add(4,10,3,117,32,6) == SIDE_TITLE)
                add(5,50,41,36,32,6)
                add(4,0,79,50,18,6,wm=1,font=495)
                add(4,0,117,50,36,6,wm=1,font=494)
                check(add(4,0,0,0,0,3,text="Close Info",wm=1,hm=1) == SIDE_CLOSE_TEXT)
                byId(6).scrollH = 160
                // Labels are children of the native operation layers; the layers remain the callback targets.
                (8..11).forEach { id ->
                    val pos = list.indexOfFirst { it.id == id }
                    list[pos] = C(id,0,listOf(0,41,81,122)[id-8],0,34,17,2,0,0,0,2,false,ops=list[pos].ops)
                    button(id,34,17,listOf("1","5","10","50")[id-8])
                }
                extras += "DMM2 OMNISHOP_SIDE inventory14..41 info42..46 buttons47..82"
            }
        }
        if (group == 819) {
            repeat(4) { i -> button(26+i,51,18,listOf("1","5","10","50")[i]) }
            add(5,0,0,0,0,13,sprite=297,wm=1,hm=1) //192: source5771 backing before label
            check(add(5,2,2,16,16,11,sprite=4735) == VIEW_ICON)
            check(add(5,0,0,20,20,12,sprite=8386) == HELP_ICON)
            sprites += setOf(4737,8387)
            list += C(list.size,3,0,0,0,0,13,1,1,1,1,false,colour=0x0e0e0c)
            list += C(list.size,3,0,0,2,2,13,1,1,1,1,false,colour=0x474745)
            check(list.size == FILTER_ARROW)
            sprites += 788
            list += C(list.size,5,2,2,16,16,13,0,0,2,0,false,sprite=788,opacity=125)
            check(add(4,2,2,4,0,13,text="No filters available",font=494,wm=1,hm=1,colour=0x9f9f9f) == FILTER_LABEL)
            // 7147 -> 228: expanded selection/help has its own steel frame on native layer20.
            check(add(5,0,0,2,2,20,sprite=297,wm=1,hm=1,xm=1,ym=1) == INFO_FRAME_FIRST)
            check(add(4,0,6,12,24,20,text="Help",wm=1,xm=1) == INFO_FRAME_TITLE)
            listOf(intArrayOf(0,0,25,30,310,0,0,0,0), intArrayOf(0,0,25,30,311,0,0,2,0),
                intArrayOf(0,0,25,30,312,0,0,0,2), intArrayOf(0,0,25,30,313,0,0,2,2),
                intArrayOf(-15,0,36,60,172,0,1,0,1),intArrayOf(-15,0,36,60,315,0,1,2,1),
                intArrayOf(0,-15,50,36,314,1,0,1,0),intArrayOf(0,-15,50,36,173,1,0,1,2),
                intArrayOf(0,14,10,26,2546,1,0,1,0)).forEach { a ->
                add(5,a[0],a[1],a[2],a[3],20,sprite=a[4],wm=a[5],hm=a[6],xm=a[7],ym=a[8])
            }
            check(add(5,3,6,26,23,20,sprite=535,ops=listOf("Close"),xm=2) == INFO_FRAME_CLOSE)
            extras += "DMM2 OMNISHOP buttons156..191 filterbacking192 controls193..198 expandedframe199..210; layout bridge source7155"
        }
    }
}
