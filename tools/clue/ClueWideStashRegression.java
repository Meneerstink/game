import com.jagex.core.io.Packet;
import com.jagex.game.runetek6.config.loctype.LocType;
import com.jagex.IndexedImage;
import java.nio.file.*;
import java.util.Arrays;

/** Actual staged client LocType and IndexedImage, no client window, server or cache writer. */
class ClueWideStashRegression {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]);
        for(String mode:new String[]{"legacy","legacy-default","wide","wide-default"}) {
            byte[] bytes=Files.readAllBytes(dir.resolve(mode+".bin"));
            Packet packet=new Packet(bytes); LocType type=new LocType();
            int code; while((code=packet.g1())!=0) type.decode(packet,code);
            int base=mode.startsWith("wide") ? 66313 : 60001;
            int[] expected=mode.endsWith("-default") ? new int[]{base,-1,base+2} : new int[]{base,base+1,-1};
            require(Arrays.equals(expected,type.multiloc),mode+" destinations");
            require(type.multivarbit==14000 && type.multivarp==-1,mode+" selectors");
            require(type.name.equals("STASH codec fixture"),mode+" name");
            require(packet.pos==bytes.length,mode+" decoder alignment");
        }
        if(args.length>1) {
            try(var paths=Files.list(Path.of(args[1]))) {
                for(Path file:(Iterable<Path>)paths.filter(p->p.toString().endsWith(".sprite"))::iterator) {
                    int[] pixels=IndexedImage.load(Files.readAllBytes(file))[0].method9383();
                    byte[] expected=Files.readAllBytes(Path.of(file.toString()+".argb"));
                    java.nio.IntBuffer ints=java.nio.ByteBuffer.wrap(expected).asIntBuffer();
                    int[] argb=new int[ints.remaining()];ints.get(argb);
                    require(Arrays.equals(pixels,argb),"actual client texture pixels "+file);
                }
            }
        }
        System.out.println("CLUE actual client: legacy/wide STASH transforms, defaults, selectors, alignment and texture fixtures OK");
    }
    static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
