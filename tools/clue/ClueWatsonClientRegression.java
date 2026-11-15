import com.jagex.core.io.Packet;
import com.jagex.game.runetek6.config.npctype.NPCType;
import java.nio.file.*;
import java.util.Arrays;

/** Real unmodified 667 NPCType decoder; no client window, server or cache writes. */
class ClueWatsonClientRegression {
    public static void main(String[] args) throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));
        Packet packet=new Packet(bytes); NPCType type=new NPCType();
        type.op=new String[5]; type.decode(packet);
        require(type.name.equals("Watson") && type.basId==4,"identity and movement set");
        require(type.multinpcVarbit==-1 && type.multinpcVarp==2408,"private selector");
        require(Arrays.equals(type.multinpcs,new int[]{15234,15235,15235}),"before/after/fallback representations");
        require("Talk-to".equals(type.op[0]) && type.op[2]==null,"unchanged root options");
        require(packet.pos==bytes.length,"decoder alignment");
        System.out.println("CLUE real NPCType: Watson private selector, source identity, movement, forms and fallback OK");
    }
    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
}
