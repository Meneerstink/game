import com.jagex.core.io.Packet;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.rsmod.game.tools.importer.CluePuzzleInterfaceBake;
import gg.rsmod.game.tools.importer.OsrsInterfacePortTool;
import java.io.File;
import java.util.*;

/** Decode source688's baked tile/model layers with the actual native client Component class. */
class CluePuzzleClientRegression {
    static void require(boolean value,String label) { if(!value)throw new AssertionError(label); }
    public static void main(String[] args) throws Exception {
        var root=new ObjectMapper().readTree(new File(args[0]));
        var pictures=CluePuzzleInterfaceBake.INSTANCE.pictures(root.get("puzzlePictures"));
        var models=new HashMap<Integer,Integer>();
        models.put(3438,73438);models.put(4695,74695);
        for(var picture:pictures)for(int model:picture.getModels())models.put(model,model+70000);
        var fixtures=CluePuzzleInterfaceBake.INSTANCE.components(root.get("306"),pictures,models,Map.of(539,9000,540,9001));
        int modelCount=0,moveCount=0;
        for(var fixture:fixtures) {
            byte[] raw=OsrsInterfacePortTool.INSTANCE.encode(fixture);
            var packet=new Packet(raw);
            var c=new Component();c.slot=(1234<<16)|fixture.getId();
            c.decode(packet);
            String at="puzzle:"+fixture.getId();
            require(packet.pos==raw.length,at+" alignment");
            require(c.type==fixture.getType(),at+" type");
            require(c.layer==(fixture.getParent()<0?-1:(1234<<16)|fixture.getParent()),at+" parent");
            require(c.originalX==fixture.getX() && c.originalY==fixture.getY(),at+" position");
            require(c.originalWidth==fixture.getW() && c.originalHeight==fixture.getH(),at+" size");
            if(fixture.getId()>=31) {
                require(c.type==6 && c.obj==fixture.getModel() && c.obj>65535,at+" wide tile model");
                require(c.xan2d==513 && c.yan2d==0 && c.zan2d==0 && c.zoom2d==1400,at+" source688 camera");
                require(c.hidden,at+" initially hidden atlas");
                require(c.originalWidth==49 && c.originalHeight==49,at+" tile dimensions");
                modelCount++;
            } else if(fixture.getId()>=6) {
                int cell=fixture.getId()-6;
                require(c.type==4 && Arrays.equals(c.ops,new String[]{"Move"}),at+" Move");
                require(c.originalX==cell%5*50 && c.originalY==cell/5*50,at+" source688 stride");
                moveCount++;
            }
        }
        require(modelCount==168 && moveCount==25,"complete seven-picture atlas");
        System.out.println("CLUE puzzle native decoder: 199 components, 168 real models, 25 source Move cells OK");
    }
}
