import com.jagex.core.io.Packet;
import java.nio.file.*;
import java.util.Arrays;

/** Real, unmodified native Component decoder; all chart nodes, no client window. */
class ClueStashChartClientRegression {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]); Component[] nodes=new Component[130];
        for(int i=0;i<nodes.length;i++) {
            byte[] bytes=Files.readAllBytes(dir.resolve("chart-"+i+".bin"));
            Packet packet=new Packet(bytes); Component node=new Component();node.id=(1300<<16)|i;node.decode(packet);
            require(packet.pos==bytes.length,"component "+i+" alignment");nodes[i]=node;
        }
        for(int tab=30;tab<=34;tab++) {
            Object[] over=nodes[tab].onMouseOver,leave=nodes[tab].onMouseLeave;
            require(over.length==8 && leave.length==8,"three graphic hover "+tab);
            require(over[0].equals(6100) && over[7].equals(tab-30),"native callback and personal tier "+tab);
            for(int i=0;i<3;i++) require(over[2+2*i].equals(9998+i) && leave[2+2*i].equals(10001+i),"source tab sprites "+tab);
        }
        require(nodes[48].graphic==9831,"source frame close graphic");
        require(Arrays.equals(nodes[48].onMouseOver,new Object[]{44,-2147483645,9832}),"source close hover");
        require(Arrays.stream(nodes).anyMatch(n->n.type==3 && n.filled && n.colour==16777215),"source row shading");
        require(Arrays.stream(nodes).anyMatch(n->n.type==4 && n.text.equals("78")),"owner's tier label");
        System.out.println("CLUE real Component: all 130 STASH chart nodes, source hover hooks, row shading and 78 label OK");
    }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
