import com.jagex.core.io.Packet;
import java.nio.file.*;
import java.util.Arrays;

/** Actual unmodified native decoder; no window or canonical client edit. */
class ClueStashSettingClientRegression {
    public static void main(String[] args) throws Exception {
        Component[] nodes = new Component[3];
        for (int i = 0; i < nodes.length; i++) {
            byte[] bytes = Files.readAllBytes(Path.of(args[0]).resolve("setting-" + i + ".bin"));
            Packet packet = new Packet(bytes);
            Component node = new Component(); node.id = (261 << 16) | (26 + i); node.decode(packet);
            require(packet.pos == bytes.length, "native byte alignment " + i);
            nodes[i] = node;
        }
        require(nodes[0].type == 0 && Arrays.equals(nodes[0].ops, new String[]{"Toggle"}), "native Toggle action");
        require(nodes[1].graphic == 8515, "source unchecked checkbox");
        require(nodes[2].type == 4 && nodes[2].text.equals("STASH units take equipped items"), "exact source struct label");
        System.out.println("CLUE actual Component: all three STASH setting nodes, source label/checkbox and Toggle decoded OK");
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
