import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Finds the real STASH chart/setting in source UI/scripts/enums; no target cache reader or writer. */
class ClueStashUiProbe {
    static String sha(byte[] raw) throws Exception {
        StringBuilder out = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(raw)) out.append(String.format("%02x", b));
        return out.toString();
    }
    static Map<String,Object> row(int index, int group, int file, byte[] raw) throws Exception {
        var row = new LinkedHashMap<String,Object>();
        row.put("index",index); row.put("group",group); row.put("file",file); row.put("sha256",sha(raw));
        var matches = new ArrayList<String>();
        for (String text : new String(raw,StandardCharsets.ISO_8859_1).split("\\x00"))
            if (text.toLowerCase(Locale.ROOT).contains("stash") || text.contains("S.T.A.S.H"))
                matches.add(text.replaceAll("[\\p{Cntrl}]", " "));
        row.put("strings",matches);
        return matches.isEmpty() ? null : row;
    }
    public static void main(String[] args) throws Exception {
        var rows = new ArrayList<Object>();
        try (var reader = new ModernCacheReader(new File(args[0]))) {
            for (int index : new int[]{3,12,2}) for (int group : reader.index(index).getGroups().keySet()) {
                if(index == 2 && (group == 6 || group == 10)) continue;
                for (var file : reader.files(index,group).entrySet()) {
                    var row = row(index,group,file.getKey(),file.getValue()); if(row != null) rows.add(row);
                }
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n");
        System.out.println("Read-only STASH UI/script/enum matches: "+rows.size());
    }
}
