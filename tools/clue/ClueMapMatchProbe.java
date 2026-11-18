import gg.rsmod.game.tools.importer.*;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

/** Read-only map artwork identity; a shared numeric model ID is never evidence. */
class ClueMapMatchProbe {
    static ModelData osrs(byte[] raw) {
        try { return ModernModelDecoder.INSTANCE.decode(raw,false); }
        catch (IllegalArgumentException | IllegalStateException e) { return Rev667ModelDecoder.INSTANCE.decode(raw,false); }
    }
    public static void main(String[] args) throws Exception {
        var sourceWidgets=new JsonParser().parse(Files.readString(Path.of(args[2]))).getAsJsonObject();
        var result=new LinkedHashMap<Integer,Object>();
        try(var source=new ModernCacheReader(new File(args[0])); var target=new ModernCacheReader(new File(args[1]))) {
            var sourceModels=new LinkedHashMap<Integer,ModelData>();
            for(var group:sourceWidgets.entrySet()) for(var component:group.getValue().getAsJsonObject().entrySet()) {
                var node=component.getValue().getAsJsonObject();
                if(node.get("type").getAsInt()!=6)continue;
                int model=node.get("modelId").getAsInt();
                if(model>=0)sourceModels.put(model,osrs(source.file(7,model,0)));
            }
            for(int group=337;group<=363;group++) {
                if(!target.index(3).getGroups().containsKey(group))continue;
                var rows=new ArrayList<Object>();
                for(var component:target.files(3,group).entrySet()) {
                    int id=InterfaceHookProbeTool.INSTANCE.componentModelId(component.getValue());
                    if(id<0)continue;
                    var raw=target.file(7,id,0);
                    var matches=new ArrayList<Integer>();
                    var row=new LinkedHashMap<String,Object>();
                    try {
                        var model=Rev667ModelDecoder.INSTANCE.decode(raw,false);
                        // Native Mesh scales version<13 by four. Modern OSRS uses
                        // the old geometry units; normalize only exactly divisible native coordinates.
                        if(model.getVersion()>=13) {
                            for(int[] coordinates:new int[][]{model.getVertexX(),model.getVertexY(),model.getVertexZ()}) {
                                for(int coordinate:coordinates) if(coordinate%4!=0) throw new IllegalStateException("Non-integral native geometry unit");
                                for(int i=0;i<coordinates.length;i++)coordinates[i]/=4;
                            }
                        }
                        for(var candidate:sourceModels.entrySet()) {
                            if(ModelConvertTool.INSTANCE.compare(candidate.getValue(),model).isEmpty())matches.add(candidate.getKey());
                        }
                        if(sourceModels.containsKey(id)) row.put("sameNumericIdDifferences",ModelConvertTool.INSTANCE.compare(sourceModels.get(id),model));
                    } catch(IllegalArgumentException | IllegalStateException unsupported) {
                        row.put("unsupported",unsupported.toString());
                    }
                    row.put("component",component.getKey());row.put("model",id);
                    row.put("sha1",CacheItemProbeTool.INSTANCE.sha1(raw));row.put("matches",matches);
                    rows.add(row);
                }
                result.put(group,rows);
            }
        }
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n");
        System.out.println("Read-only native/source map mesh matches exported: "+args[3]);
    }
}
