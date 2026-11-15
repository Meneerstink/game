import gg.rsmod.game.tools.importer.*;
import com.google.gson.*;
import java.io.File;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Read-only source-cache evidence for the selected steps. No target-cache API. */
class ClueStepAssetProbe {
    static String hash(byte[] raw) throws Exception {
        return HexFormatCompat.hex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
    public static void main(String[] args) throws Exception {
        var result = new LinkedHashMap<String,Object>();
        var npcs = new LinkedHashMap<Integer,Object>();
        var scenes = new ArrayList<Object>();
        var steps = new JsonParser().parse(Files.readString(Path.of(args[1]))).getAsJsonArray();
        try (var source = new ModernCacheReader(new File(args[0]))) {
            var defs = source.files(2, 9);
            for (int id : new int[]{1774,1775,1776,6584,7311,1777,1778,7312,2954,2955,6587,6588,7307,7308,7309,7310,306,5081,6586,7306,8635,8636,8637}) {
                byte[] raw=defs.get(id);
                if (raw == null) throw new IllegalStateException("Missing pinned source npc " + id);
                var row = new LinkedHashMap<String,Object>();
                row.put("definition", OsrsNpcProbeTool.INSTANCE.decodeOsrs(id,raw));
                row.put("sha256",hash(raw));
                npcs.put(id,row);
            }
            var regions = new HashMap<Integer,ModernRegion>();
            var objects = source.files(2,6);
            for (var value : steps) {
                var step=value.getAsJsonObject();
                String action=step.get("action").getAsString();
                if (!action.equals("SEARCH") && !action.equals("EMOTE")) continue;
                for (var point : step.getAsJsonArray("points")) {
                    var p=point.getAsJsonArray(); int x=p.get(0).getAsInt(), z=p.get(1).getAsInt(), plane=p.get(2).getAsInt();
                    int regionId=((x>>6)<<8)|(z>>6);
                    var placements=new ArrayList<Object>();
                    for (int rx=(x-12)>>6;rx<=(x+12)>>6;rx++) for (int rz=(z-12)>>6;rz<=(z+12)>>6;rz++) {
                      int sceneId=(rx<<8)|rz;
                      var region=regions.get(sceneId);
                      if (region==null) {
                        // Read source 2686 directly even when the installed runtime
                        // jars predate the importer's unnamed-map-group adapter.
                        if (!source.index(5).getHasNames()) {
                          var files=source.files(5,sceneId);
                          byte[] terrain=Objects.requireNonNull(files.get(0)), locations=Objects.requireNonNull(files.get(1));
                          region=new ModernRegion(rx,rz,ModernRegion.Companion.decodeTiles(terrain),
                              ModernRegion.Companion.decodeLocs(locations),terrain.length,locations.length);
                        } else region=ModernRegion.Companion.load(source,sceneId,null);
                        regions.put(sceneId,region);
                      }
                      for (var loc : region.getLocs()) {
                        int lx=region.getRx()*64+loc.getLocalX(), lz=region.getRz()*64+loc.getLocalZ();
                        // Search footprint only. This is not a gameplay completion radius.
                        if ((!action.equals("EMOTE") && loc.getPlane()!=plane) || Math.max(Math.abs(lx-x),Math.abs(lz-z))>(action.equals("EMOTE")?12:1)) continue;
                        var raw=objects.get(loc.getId()); var def=ModernObjectDef.Companion.decode(loc.getId(),raw);
                        var forms=new LinkedHashMap<Integer,Object>();
                        boolean stash=def.getName().toLowerCase(Locale.ROOT).contains("inconspicuous");
                        if (def.getTransforms()!=null) for(int id:def.getTransforms()) {
                            if(id<0 || !objects.containsKey(id))continue;
                            var form=ModernObjectDef.Companion.decode(id,objects.get(id));
                            forms.put(id,form);
                            stash |= form.getName().toLowerCase(Locale.ROOT).contains("inconspicuous");
                        }
                        if (action.equals("EMOTE") && !stash) continue;
                        var row=new LinkedHashMap<String,Object>();
                        row.put("id",loc.getId()); row.put("point",new int[]{lx,lz,loc.getPlane()});
                        int bridge=region.getTiles()[1][loc.getLocalX()][loc.getLocalZ()].getSettings() & 2;
                        row.put("effectivePlane",loc.getPlane()>0 && bridge!=0 ? loc.getPlane()-1 : loc.getPlane());
                        row.put("type",loc.getType()); row.put("rotation",loc.getRotation());
                        row.put("definition",def); row.put("forms",forms); row.put("sha256",hash(raw));
                        placements.add(row);
                      }
                    }
                    var row=new LinkedHashMap<String,Object>();
                    row.put("key",step.get("key").getAsString()); row.put("region",regionId);
                    row.put("placements",placements); scenes.add(row);
                }
            }
        }
        result.put("source","openrs2-2686");result.put("npcs",npcs);result.put("scenes",scenes);
        Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(result)+"\n");
        System.out.println("Read-only source evidence: " + npcs.size()+" NPCs, "+scenes.size()+" step scenes");
    }
    // java.util.HexFormat is unavailable on the project's Java 11.
    static class HexFormatCompat { static String hex(byte[] raw) { var out=new StringBuilder(); for(byte b:raw)out.append(String.format("%02x",b&255));return out.toString(); } }
}
