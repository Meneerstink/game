package gg.rsmod.plugins.content.runtime;

import gg.rsmod.game.Server;
import gg.rsmod.game.model.World;
import gg.rsmod.game.plugin.KotlinPlugin;
import gg.rsmod.game.plugin.PluginMetadata;
import gg.rsmod.game.service.game.DumpEntityIdService;
import gg.rsmod.game.service.rsa.RsaService;
import gg.rsmod.plugins.content.mechanics.multi.MultiService;
import gg.rsmod.plugins.content.mechanics.worldedit.WorldEdits;
import gg.rsmod.util.ServerProperties;
import gg.rsmod.util.io.SharedDataFiles;
import sun.misc.Unsafe;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Only synthetic fixtures; no server constructor, boot, cache decoding, gameplay or real key path. */
public class RuntimeStartupFileIsolationTests {
    static final Unsafe UNSAFE;
    static {
        try { Field f = Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE = (Unsafe)f.get(null); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    static class FixturePlugin extends KotlinPlugin { FixturePlugin() { super(null, null, null); } }
    static <T> T inert(Class<T> type) throws Exception { return type.cast(UNSAFE.allocateInstance(type)); }
    static void check(boolean good, String detail) { if (!good) throw new AssertionError(detail); }
    static Path selected(String path) { return SharedDataFiles.INSTANCE.of(path).toPath(); }
    static boolean test() { return SharedDataFiles.INSTANCE.testServer(); }
    static void unchanged(Path origin, byte[] before) throws Exception {
        check(Arrays.equals(before, Files.exists(origin) ? Files.readAllBytes(origin) : null), "origin changed: " + origin);
    }
    public static void main(String[] args) throws Exception {
        Path base = Paths.get(args[1]);
        Server server = inert(Server.class); World world = inert(World.class);
        switch (args[0]) {
            case "metadata": {
                FixturePlugin plugin = inert(FixturePlugin.class);
                PluginMetadata metadata = new PluginMetadata("N151_fixture", "synthetic", "fixture only",
                        Collections.emptySet(), Collections.singletonMap("n151", 17));
                Path original = Paths.get("plugins/configs/N151_fixture.json");
                plugin.load_metadata(metadata);
                check(plugin.<Integer>getProperty("n151") == 17, "metadata defaults not reloaded");
                check(Files.exists(selected(original.toString())), "metadata private output absent");
                if (test()) check(!Files.exists(original), "metadata created original");
                Path seed = Paths.get("plugins/configs/N151_seed.json"); Files.createDirectories(seed.getParent());
                Files.writeString(seed, "{\"properties\":{\"n151\":23}}"); byte[] before = Files.readAllBytes(seed);
                plugin.load_metadata(new PluginMetadata("N151_seed", null, null, Collections.emptySet(), Collections.emptyMap()));
                check(plugin.<Integer>getProperty("n151") == 23, "metadata seeded properties absent");
                unchanged(seed, before);
                break;
            }
            case "multi": {
                Path origin = Paths.get("data/cfg/areas/multi.yml"); Files.createDirectories(origin.getParent());
                Files.writeString(origin, "regions:\n- 990001\nchunks:\n- 990002\n"); byte[] before = Files.readAllBytes(origin);
                MultiService service = new MultiService();
                service.init(server, world, new ServerProperties().loadMap(Collections.singletonMap("areas", origin.toAbsolutePath().toString())));
                check(service.getMultiRegions().contains(990001) && service.getMultiChunks().contains(990002), "multi seed not loaded");
                service.appendMultiCombatChunks(new ArrayList<>(Collections.singletonList(990003)));
                service.appendMultiCombatRegions(Collections.singletonList(990004));
                String updated = Files.readString(selected(origin.toString()));
                check(updated.contains("990003") && updated.contains("990004"), "multi writes absent");
                if (test()) unchanged(origin, before);
                break;
            }
            case "world": {
                Path data = Files.createDirectories(base.resolve("synthetic-data"));
                System.setProperty("rsps.data", data.toString());
                Path original = data.resolve("world_edits.json");
                Files.writeString(original, "{\"edits\":[{\"op\":\"N151_fixture_only\",\"id\":999001,\"x\":0,\"z\":0,\"level\":0}]}");
                byte[] before = Files.readAllBytes(original);
                WorldEdits edits = WorldEdits.INSTANCE;
                check(edits.load().size() == 1, "world seed not loaded");
                edits.save(Collections.emptyList()); check(edits.load().isEmpty(), "world reload did not read private write");
                if (test()) unchanged(original, before);
                break;
            }
            case "dump": {
                Path original = base.resolve("synthetic-id-output");
                Map<String,Object> properties = new HashMap<>(); properties.put("dump", true);
                properties.put("cache-path", base.resolve("never-open-cache").toString());
                properties.put("output-path", original.toString());
                DumpEntityIdService service = new DumpEntityIdService();
                service.init(server, world, new ServerProperties().loadMap(properties));
                if (test()) check(!Files.exists(original), "dump init created original directory");
                Method method = DumpEntityIdService.class.getDeclaredMethod("generateWriter", String.class); method.setAccessible(true);
                try (PrintWriter writer = (PrintWriter)method.invoke(service, "N151Fixture.kt")) { writer.println("// synthetic fixture"); }
                check(Files.readString(selected(original.resolve("N151Fixture.kt").toString())).contains("synthetic fixture"), "dump output absent");
                if (test()) check(!Files.exists(original), "dump wrote original");
                break;
            }
            case "rsa": {
                // Generate a disposable fixture key ONLY. Never read the repository's configured/default RSA path.
                Path original = base.resolve("synthetic-rsa/key.pem");
                Map<String,Object> properties = new HashMap<>(); properties.put("path", original.toString());
                properties.put("bit-count", 2048);
                RsaService service = new RsaService();
                service.init(server, world, new ServerProperties().loadMap(properties));
                Field field = RsaService.class.getDeclaredField("keyPath"); field.setAccessible(true);
                Path chosen = (Path)field.get(service);
                check(Files.size(chosen) > 0 && Files.exists(chosen.resolveSibling("modulus.txt")), "RSA fixture pair absent");
                check(service.getModulus().signum() > 0 && service.getExponent().signum() > 0, "RSA fixture not parsed");
                if (test()) check(!Files.exists(original) && chosen.startsWith(Paths.get(System.getProperty("java.io.tmpdir"))), "RSA wrote fixture origin");
                byte[] before = Files.readAllBytes(chosen);
                RsaService reloaded = new RsaService(); reloaded.init(server, world, new ServerProperties().loadMap(properties));
                check(reloaded.getModulus().equals(service.getModulus()), "RSA fixture regenerated at relog"); unchanged(chosen, before);
                break;
            }
            default: throw new AssertionError("unknown case");
        }
        System.out.println("N151 STARTUP ADMIN OK " + args[0]);
    }

    @org.junit.Test public void metadataWritesStayPrivate() throws Exception { checkFamily("metadata"); }
    @org.junit.Test public void multiReadsAndBothWritersStayPrivate() throws Exception { checkFamily("multi"); }
    @org.junit.Test public void worldEditLoadSaveAndReloadStayPrivate() throws Exception { checkFamily("world"); }
    @org.junit.Test public void dumpInitializationAndWriterStayPrivate() throws Exception { checkFamily("dump"); }
    @org.junit.Test public void rsaFixtureGenerationAndReloadStayPrivate() throws Exception { checkFamily("rsa"); }

    private static void checkFamily(String mode) throws Exception {
        // Normal first: every old-build failure then demonstrates isolation, not a broken non-test control.
        for (String marker : new String[]{null, "rsps.testServer", "rsps.conformance", "rsps.breachConformance", "rsps.breachSim"}) {
            Path base = Files.createTempDirectory("rsps-N151-startup-");
            Path cwd = Files.createDirectories(base.resolve("cwd"));
            Path tmp = Files.createDirectories(base.resolve("tmp"));
            Path config = base.resolve("quiet.xml");
            Files.writeString(config, "<Configuration status=\"off\"><Loggers><Root level=\"off\"/></Loggers></Configuration>");
            LinkedHashSet<String> entries = new LinkedHashSet<>();
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) entries.add(entry);
            // Gradle workers keep test dependencies on a URLClassLoader instead of java.class.path.
            for (ClassLoader loader = RuntimeStartupFileIsolationTests.class.getClassLoader(); loader != null; loader = loader.getParent()) {
                if (loader instanceof java.net.URLClassLoader) {
                    for (java.net.URL url : ((java.net.URLClassLoader)loader).getURLs()) {
                        if (url.getProtocol().equals("file")) entries.add(Paths.get(url.toURI()).toString());
                    }
                }
            }
            List<String> args = new ArrayList<>(Arrays.asList(
                    Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx96m",
                    "-Djava.io.tmpdir="+tmp, "-Drsps.root="+base.resolve("isolated-root"),
                    "-Drsps.data="+base.resolve("synthetic-data"), "-Dlog4j.configurationFile="+config));
            if (marker != null) args.add("-D"+marker+"=true");
            args.addAll(Arrays.asList("-cp", String.join(File.pathSeparator, entries),
                    RuntimeStartupFileIsolationTests.class.getName(), mode, base.toString()));
            Path log = base.resolve("probe.txt");
            Process child = new ProcessBuilder(args).directory(cwd.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean ended = child.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!ended) { child.destroyForcibly(); child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS); }
            check(ended && child.exitValue()==0, mode+"/"+marker+": "+Files.readString(log));
            check(Files.readString(log).contains("N151 STARTUP ADMIN OK "+mode), "missing completion marker");
            // Retain owned synthetic fixtures for diagnosis; no recursive cleanup or product-data access.
        }
    }
}
