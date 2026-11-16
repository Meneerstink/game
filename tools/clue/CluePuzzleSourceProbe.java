import gg.rsmod.game.tools.importer.ModernCacheReader;
import gg.rsmod.game.tools.importer.ModernItemDefDecoder;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read only cache2686's seven Treasure Trail picture enums, tile models and raw provenance. */
class CluePuzzleSourceProbe {
    static String hash(byte[] raw) throws Exception {
        StringBuilder out=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b&255));
        return out.toString();
    }
    static Map<Integer,Integer> integers(byte[] raw) throws Exception {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(raw));
        Map<Integer,Integer> values=new LinkedHashMap<>();
        while(true) {
            int op=in.readUnsignedByte();
            if(op==0)break;
            if(op==1 || op==2)in.readUnsignedByte();
            else if(op==4)in.readInt();
            else if(op==6) {
                int n=in.readUnsignedShort();
                for(int i=0;i<n;i++)if(values.put(in.readInt(),in.readInt())!=null)throw new IOException("Duplicate enum key");
            } else throw new IOException("Unsupported enum opcode "+op);
        }
        if(in.available()!=0)throw new IOException("Enum trailing bytes");
        return values;
    }
    public static void main(String[] args) throws Exception {
        Gson gson=new GsonBuilder().setPrettyPrinting().create();
        Map<Integer,Object> enums=new LinkedHashMap<>(), items=new LinkedHashMap<>(), models=new LinkedHashMap<>();
        List<Object> pictures=new ArrayList<>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            Map<Integer,Integer> forward=integers(source.file(2,8,1864));
            Map<Integer,Integer> inverse=integers(source.file(2,8,1865));
            Set<Integer> enumIds=new LinkedHashSet<>(Arrays.asList(1864,1865));
            for(int selector:new int[]{1,2,3,4,5,6,9}) {
                int f=forward.get(selector), r=inverse.get(selector);
                enumIds.add(f);enumIds.add(r);
                Map<Integer,Integer> pieces=integers(source.file(2,8,f));
                Map<Integer,Integer> reverse=integers(source.file(2,8,r));
                if(pieces.size()!=24 || reverse.size()!=24)throw new IOException("Not 24 tiles: "+selector);
                List<Integer> itemIds=new ArrayList<>(), modelIds=new ArrayList<>();
                for(int position=0;position<24;position++) {
                    int item=pieces.get(position);
                    if(reverse.get(item)!=position)throw new IOException("Inverse enum mismatch");
                    byte[] bytes=source.file(2,10,item);
                    JsonObject def=gson.toJsonTree(ModernItemDefDecoder.INSTANCE.decode(item,bytes)).getAsJsonObject();
                    int model=def.getAsJsonObject("params").get("186").getAsInt();
                    if(model!=def.get("inventoryModel").getAsInt())throw new IOException("Tile model parameter mismatch");
                    items.put(item,Map.of("sha256",hash(bytes),"definition",def));
                    models.put(model,hash(source.file(7,model,0)));
                    itemIds.add(item);modelIds.add(model);
                }
                pictures.add(Map.of("selector",selector,"forwardEnum",f,"inverseEnum",r,"items",itemIds,"models",modelIds));
            }
            for(int id:enumIds) {
                byte[] bytes=source.file(2,8,id);
                enums.put(id,Map.of("sha256",hash(bytes),"rawBase64",Base64.getEncoder().encodeToString(bytes),"values",integers(bytes)));
            }
        }
        Files.writeString(Path.of(args[1]),gson.toJson(Map.of("cache",2686,"pictures",pictures,"enums",enums,"items",items,"modelSha256",models))+"\n");
        System.out.println("Read-only puzzle evidence: "+pictures.size()+" pictures, "+items.size()+" tile definitions, "+models.size()+" models");
    }
}
