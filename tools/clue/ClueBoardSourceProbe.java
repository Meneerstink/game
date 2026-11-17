import gg.rsmod.game.tools.importer.ModernCacheReader;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Locate board text in the actual pinned source, read-only; never opens a target writer. */
class ClueBoardSourceProbe {
    public static void main(String[] args) throws Exception {
        var rows = new ArrayList<Object>();
        var words = Pattern.compile("(?i)treasure trail|scroll box|mimic scroll|clue scroll");
        var strings = Pattern.compile("[ -~]{5,}");
        try (var source = new ModernCacheReader(new File(args[0]))) {
            for (int id : source.index(12).getGroups().keySet()) {
                byte[] raw = source.file(12,id,0);
                if (raw == null) continue;
                String text = new String(raw, StandardCharsets.ISO_8859_1);
                if (!words.matcher(text).find()) continue;
                var matches = new ArrayList<String>();
                var matcher = strings.matcher(text);
                while (matcher.find()) if (words.matcher(matcher.group()).find()) matches.add(matcher.group());
                rows.add(Map.of("script",id,"strings",matches,"sha256",HexFormatCompat.hash(raw)));
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(rows)+"\n");
        System.out.println("Read-only source script candidates: "+rows.size());
    }
    private static class HexFormatCompat {
        static String hash(byte[] raw) throws Exception {
            var out = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(raw)) out.append(String.format("%02x",b));
            return out.toString();
        }
    }
}
