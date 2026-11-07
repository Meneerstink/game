import com.jagex.js5.Js5Index;
import com.jagex.js5.ResourceProvider;
import com.jagex.js5.js5;
import com.jagex.core.io.Packet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.RandomAccessFile;
import javax.imageio.ImageIO;

/** Offline evidence only: real 667 texture evaluation, no game/client startup.
 * Cache files open read-only. Compile against the existing client snapshot jars.
 * Arguments: cache-directory output-directory comma-separated-texture-ids.
 */
public final class N601TextureDump {
    static final class Provider extends ResourceProvider {
        final String cache;
        final int archive;
        final Js5Index index;
        Provider(String cache, int archive) throws Exception {
            this.cache = cache;
            this.archive = archive;
            byte[] raw = read(255, archive);
            index = new Js5Index(raw, Packet.getcrc(raw.length, raw), null);
        }
        byte[] read(int archive, int group) throws Exception {
            try (RandomAccessFile idx = new RandomAccessFile(new File(cache, "main_file_cache.idx" + archive), "r");
                 RandomAccessFile dat = new RandomAccessFile(new File(cache, "main_file_cache.dat2"), "r")) {
                if ((long) group * 6 + 6 > idx.length()) return null;
                idx.seek((long) group * 6);
                int size = (idx.readUnsignedByte() << 16) | (idx.readUnsignedByte() << 8) | idx.readUnsignedByte();
                int sector = (idx.readUnsignedByte() << 16) | (idx.readUnsignedByte() << 8) | idx.readUnsignedByte();
                if (size == 0 || sector == 0) return null;
                byte[] raw = new byte[size];
                int pos = 0, chunk = 0;
                while (pos < size) {
                    if (sector == 0) throw new IllegalStateException("short sector chain");
                    dat.seek((long) sector * 520);
                    int actual = group > 65535 ? dat.readInt() : dat.readUnsignedShort();
                    int part = dat.readUnsignedShort();
                    int next = (dat.readUnsignedByte() << 16) | (dat.readUnsignedByte() << 8) | dat.readUnsignedByte();
                    int ix = dat.readUnsignedByte();
                    if (actual != group || part != chunk++ || ix != archive) throw new IllegalStateException("sector mismatch");
                    int length = Math.min(group > 65535 ? 510 : 512, size - pos);
                    dat.readFully(raw, pos, length);
                    pos += length;
                    sector = next;
                }
                return raw;
            }
        }
        public void requestGroup(int group) { }
        public int completePercentage(int group) { return 100; }
        public byte[] fetchgroup(int group) {
            try { return read(archive, group); }
            catch (Exception ex) { throw new IllegalStateException(ex); }
        }
        public Js5Index index() { return index; }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("cache-directory output-directory texture-ids");
        File out = new File(args[1]);
        if (!out.isDirectory()) throw new IllegalArgumentException("output directory must exist");
        Js5TextureSource source = new Js5TextureSource(new js5(new Provider(args[0], 26), false, 1),
            new js5(new Provider(args[0], 9), false, 1), new js5(new Provider(args[0], 8), false, 1));
        if (args[2].equals("dependencies")) {
            for (int id = 0; id < source.textureCount; id++) {
                if (source.textureMetrics[id] == null) continue;
                Texture texture = source.list(id);
                System.out.println(id + "\t" + java.util.Arrays.toString(texture.anIntArray824)
                    + "\t" + java.util.Arrays.toString(texture.anIntArray823));
            }
            return;
        }
        for (String text : args[2].split(",")) {
            int id = Integer.parseInt(text);
            int[] pixels = source.argbOutput(0.7f, id, 128, 128);
            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 128, 128, pixels, 0, 128);
            ImageIO.write(image, "png", new File(out, "667-texture-" + id + ".png"));
            System.out.println("TEXTURE " + id + " OK");
        }
    }
}
