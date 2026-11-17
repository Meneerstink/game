import com.jagex.core.io.Packet;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.rsmod.game.tools.importer.CluePuzzleInterfaceBake;
import gg.rsmod.game.tools.importer.OsrsInterfacePortTool;
import gg.rsmod.game.tools.importer.ModelData;
import gg.rsmod.game.tools.importer.Rev667ModelEncoder;
import com.jagex.graphics.Mesh;
import com.jagex.math.Trig1;
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
            if (fixture.getId()==1) {
                require(c.filled && c.colour==3611648 && c.originalWidth==265 && c.originalHeight==265,at+" opaque source backing");
            }
            if (fixture.getId()==2 || fixture.getId()==3) {
                require(c.modelOriginX==0 && c.modelOriginY==0,at+" source offsets must not move screen origin");
            }
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
        if (args.length>1) {
            var geometry=new ObjectMapper().readTree(new File(args[1]));
            for (int component:new int[]{2,3}) {
                var n=root.get("306").get(Integer.toString(component));
                var raw=geometry.get(n.get("modelId").asText()).get("source");
                int count=raw.get("x").size();
                var mesh=new ModelData(count,0,0);
                for(int v=0;v<count;v++) {
                    mesh.getVertexX()[v]=raw.get("x").get(v).asInt();
                    mesh.getVertexY()[v]=raw.get("y").get(v).asInt();
                    mesh.getVertexZ()[v]=raw.get("z").get(v).asInt();
                }
                CluePuzzleInterfaceBake.INSTANCE.adaptFrameModel(n.get("modelId").asInt(),mesh,root.get("306"));
                var nativeMesh=new Mesh(Rev667ModelEncoder.INSTANCE.encode(mesh));
                require(nativeMesh.version<13,"native coarse frame units");
                nativeMesh.upscale();
                int pitch=n.get("rotationX").asInt(),zoom=n.get("modelZoom").asInt();
                int ox=n.get("offsetX2d").asInt(),oy=n.get("offsetY2d").asInt();
                var matrix=new JavaMatrix();
                matrix.translate(0,Trig1.SIN[pitch<<3]*(zoom<<2)>>14,Trig1.COS[pitch<<3]*(zoom<<2)>>14);
                matrix.rotateAxisX(pitch<<3);
                int[] point=new int[3];
                double sin=Math.sin(pitch*Math.PI/1024),cos=Math.cos(pitch*Math.PI/1024);
                for(int v=0;v<count;v++) {
                    double x=raw.get("x").get(v).asInt()+ox;
                    double y=raw.get("y").get(v).asInt()+sin*zoom+oy;
                    double z=raw.get("z").get(v).asInt()+cos*zoom+oy;
                    double depth=y*sin+z*cos;
                    double sx=512*x/depth,sy=512*(y*cos-z*sin)/depth;
                    matrix.project(nativeMesh.vertexX[v],nativeMesh.vertexY[v],nativeMesh.vertexZ[v],point);
                    require(Math.abs(sx-512.0*point[0]/point[2])<1,"frame "+component+" x projection vertex "+v);
                    require(Math.abs(sy-512.0*point[1]/point[2])<1,"frame "+component+" y projection vertex "+v);
                }
                System.out.println("Source/667 frame "+component+": "+count+" native Mesh/JavaMatrix vertices agree within one pixel; all viewport centres");
            }
        }
        System.out.println("CLUE puzzle native decoder: 199 components, 168 real models, 25 source Move cells OK");
    }
}
