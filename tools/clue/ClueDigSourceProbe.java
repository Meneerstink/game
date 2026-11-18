import gg.rsmod.game.tools.importer.*;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only source sequences plus existing target pets; no cache writer. */
class ClueDigSourceProbe {
    static String sha(byte[] raw) throws Exception {
        var out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))out.append(String.format("%02x",b));return out.toString();
    }
    public static void main(String[] args) throws Exception {
        var report=new LinkedHashMap<String,Object>();var sequences=new LinkedHashMap<Integer,Object>();
        try(var source=new ModernCacheReader(new File(args[0]))) {
            for(int id:new int[]{830,3676,14498,14499}) {
                byte[] raw=source.file(2,12,id);var row=new LinkedHashMap<String,Object>();row.put("present",raw!=null);
                if(raw!=null) {row.put("sha256",sha(raw));row.put("sequence",OsrsFxImportTool.INSTANCE.decodeOsrsSeq(raw,Map.of()));}
                sequences.put(id,row);
            }
            report.put("sourceSequences",sequences);
            var npc = OsrsNpcProbeTool.INSTANCE.decodeOsrs(6296, source.file(2,9,6296));
            report.put("bloodhound2686", npc);
            var ready = OsrsFxImportTool.INSTANCE.decodeOsrsSeq(source.file(2,12,npc.getStand()), Map.of());
            var bases = new TreeSet<Integer>();
            for (int frame : ready.getFrames()) {
                byte[] bytes = source.file(0,frame>>>16,frame&65535);
                bases.add(((bytes[0]&255)<<8)|(bytes[1]&255));
            }
            report.put("bloodhoundReadyBases2686",bases);
            report.put("bloodhoundBase1491Sha2562686", sha(source.file(1,1491,0)));
        }
        for(int i=1;i<=2;i++)try(var target=new ModernCacheReader(new File(args[i]))) {
            var rows=new LinkedHashMap<Integer,Object>();
            for(int id:new int[]{14631,14844}) {
                byte[] raw=target.file(18,id>>>7,id&127);var row=new LinkedHashMap<String,Object>();row.put("sha256",sha(raw));
                var npc=new gg.rsmod.game.fs.def.NpcDef(id);npc.decode(io.netty.buffer.Unpooled.wrappedBuffer(raw));
                row.put("name",npc.getName());row.put("options",npc.getOptions());rows.put(id,row);
            }
            report.put("target"+i,rows);
        }
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.println("Source dig sequences and both actual target pet definitions recorded read-only");
    }
}
