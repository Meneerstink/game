import com.jagex.core.io.Packet;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.rsmod.game.tools.importer.*;
import java.io.File;
import java.util.*;

/** Decodes the22source paper components using the real staged667Component. No window/cache writes. */
class ClueBoardClientRegression {
    static void check(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) throws Exception {
        var root=new ObjectMapper().readTree(new File(args[0]));
        var models=Map.of(3044,70000,16344,70001);
        var sprites=Map.of(535,9000,536,9001,541,9002,542,9003);
        int count=0;
        for(int group:new int[]{221,625}) {
            var rows=root.get(Integer.toString(group));
            for(var fixture:ClueInterfaceImportTool.INSTANCE.components(group,rows,Map.of(497,100),models,sprites)) {
                var source=rows.get(Integer.toString(fixture.getId()));
                byte[] bytes=OsrsInterfacePortTool.INSTANCE.encode(fixture);var packet=new Packet(bytes);
                var c=new Component();c.slot=(group<<16)|fixture.getId();c.id=c.slot;c.decode(packet);
                String at=group+":"+fixture.getId();
                check(packet.pos==bytes.length,at+" byte alignment");
                check(c.type==source.get("type").asInt(),at+" type");
                check(c.originalX==source.get("originalX").asInt() && c.originalY==source.get("originalY").asInt(),at+" origin");
                check(c.originalWidth==source.get("originalWidth").asInt() && c.originalHeight==source.get("originalHeight").asInt(),at+" size");
                check(c.layer==source.get("parentId").asInt(),at+" parent");
                if(c.type==6) {
                    check(c.obj==models.get(source.get("modelId").asInt()),at+" wide model");
                    check(c.xan2d==source.get("rotationX").asInt() && c.yan2d==source.get("rotationZ").asInt() &&
                        c.zan2d==source.get("rotationY").asInt() && c.zoom2d==source.get("modelZoom").asInt(),at+" raw camera order");
                }
                if(source.has("actions") && source.get("actions").size()>0) {
                    check(Arrays.equals(c.ops,new String[]{"Close"}),at+" Close");
                    check(Arrays.equals(c.onMouseOver,new Object[]{44,-2147483645,group==221?9001:9003}),at+" hover");
                    check(Arrays.equals(c.onMouseLeave,new Object[]{44,-2147483645,group==221?9000:9002}),at+" leave");
                }
                count++;
            }
        }
        check(count==22,"complete source papers");
        System.out.println("Native board decoder:22components,2wide models,source cameras/parents/Close/desktop hover OK");
    }
}
