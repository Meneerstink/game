import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Strict read-only DB-row evidence from cache2686, not RuneLite's gameplay tables.
 * Framing references: primary RuneLite cache DBRowLoader / DBTableLoader / ScriptVarType.
 */
class ClueDbRowProbe {
    static int u8(ByteBuffer b){return Byte.toUnsignedInt(b.get());}
    static int smart(ByteBuffer b){return (b.get(b.position())&255)<128?u8(b):Short.toUnsignedInt(b.getShort())-32768;}
    static String string(ByteBuffer b){int start=b.position();while(b.get()!=0){}return new String(b.array(),start,b.position()-start-1,Charset.forName("windows-1252"));}
    static Map<String,Object> decode(byte[] raw) throws Exception {
        var result=new LinkedHashMap<String,Object>();
        var buf=ByteBuffer.wrap(raw);
        boolean ended=false;
        while(buf.hasRemaining()) {
            int code=u8(buf);
            if(code==0){if(buf.hasRemaining())throw new IllegalStateException("Trailing DB row bytes");ended=true;break;}
            if(code==4) {
                int table=0,shift=0,part;
                do{part=u8(buf);if(shift>=35)throw new IllegalStateException("Invalid table varint");table|=(part&127)<<shift;shift+=7;}while((part&128)!=0);
                result.put("table",table);
            } else if(code==3) {
                int count=u8(buf);var columns=new LinkedHashMap<Integer,Object>();
                for(int column=u8(buf);column!=255;column=u8(buf)) {
                    if(column>=count || columns.containsKey(column))throw new IllegalStateException("Invalid column "+column);
                    int[] types=new int[u8(buf)];for(int i=0;i<types.length;i++)types[i]=smart(buf);
                    int fields=smart(buf);var values=new ArrayList<Object>();
                    for(int i=0;i<fields;i++)for(int type:types) {
                        if(type==36) values.add(string(buf));
                        else if(Set.of(0,1,6,8,9,10,11,13,14,17,22,23,26,30,31,32,33,39,41,59,73,74,118,209).contains(type)) values.add(buf.getInt());
                        else throw new IllegalStateException("Unreviewed DB field type "+type);
                    }
                    columns.put(column,Map.of("types",types,"values",values));
                }
                result.put("columnCount",count);result.put("columns",columns);
            } else throw new IllegalStateException("Unsupported DB row opcode "+code);
        }
        if(!ended)throw new IllegalStateException("Missing DB row terminator");
        var hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(raw))hash.append(String.format("%02x",b));
        result.put("sha256",hash.toString());result.put("bytes",raw.length);return result;
    }
    public static void main(String[] args) throws Exception {
        var rows=new LinkedHashMap<Integer,Object>();
        try(var reader=new ModernCacheReader(new File(args[0]))) {
            var rawRows=reader.files(2,38);
            var selected=new LinkedHashSet<Integer>();
            if(args[2].equals("all"))selected.addAll(rawRows.keySet());
            else for(String text:args[2].split(","))selected.add(Integer.parseInt(text));
            for(int id:selected) {
                byte[] raw=rawRows.get(id);
                if(raw==null)throw new IllegalStateException("Missing DB row "+id);
                rows.put(id,decode(raw));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n");
        System.out.println("Read-only source DB rows count="+rows.size()+" -> "+args[1]);
    }
}
