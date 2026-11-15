import com.jagex.core.io.Packet;
import java.nio.file.*;

/** Run only from this worktree's staged client patch, with the real server-encoded fixtures. */
class ClueWideInterfaceRegression {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]);
        for (String mode: new String[]{"legacy","wide","missing"}) {
            byte[] bytes=Files.readAllBytes(dir.resolve(mode+".bin"));
            Packet packet=new Packet(bytes);
            Component component=new Component(); component.decode(packet);
            int expected=mode.equals("legacy") ? 60002 : mode.equals("wide") ? 66085 : -1;
            require(component.obj==expected,mode+" model: "+component.obj);
            require(component.type==6,mode+" type");
            require(component.xan2d==104 && component.yan2d==0 && component.zan2d==761 && component.zoom2d==914,mode+" camera");
            require(packet.pos==bytes.length,mode+" decoder alignment");
        }
        byte[] closeBytes=Files.readAllBytes(dir.resolve("close.bin"));
        Packet closePacket=new Packet(closeBytes);
        Component close=new Component(); close.decode(closePacket);
        require(close.graphic==9000,"close normal sprite");
        require(java.util.Arrays.equals(close.onMouseOver,new Object[]{44,-2147483645,9001}),"close hover hook");
        require(java.util.Arrays.equals(close.onMouseLeave,new Object[]{44,-2147483645,9000}),"close leave hook");
        require(closePacket.pos==closeBytes.length,"close hook decoder alignment");
        long crc=0x1abde5423L;
        for (int toolkit=0;toolkit<32;toolkit++) {
            for (int id: new int[]{0,548,60002,65534}) {
                long old=(crc&0x3FFFFFFFFFL)|((long)id<<38)|(1L<<54)|((long)toolkit<<59);
                require(old==Component.interfaceModelCacheKey(crc,id,1,toolkit),"legacy cache key");
            }
            require(Component.interfaceModelCacheKey(crc,548,1,toolkit)!=Component.interfaceModelCacheKey(crc,66084,1,toolkit),"wide/low cache collision");
        }
        System.out.println("CLUE IF3 client regression: legacy/wide/absent decode + camera + byte alignment + cache keys OK");
    }
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
