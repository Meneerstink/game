package gg.rsmod.game.service;

import gg.rsmod.game.action.UnhandledInteractions;
import gg.rsmod.util.io.SharedDataFiles;
import org.junit.Test;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** ROOT_CAUSES 109/N151: execute the actual writers in fresh JVMs with disposable originals. */
public class RuntimeFileIsolationTests {
    private static final String[] MARKERS = {
        "rsps.testServer", "rsps.conformance", "rsps.breachConformance", "rsps.breachSim"
    };

    private static void check(boolean condition, String detail) {
        if (!condition) throw new AssertionError(detail);
    }

    @Test public void everyTestServerMarkerIsolatesPathsAndSeeds() throws Exception {
        for (String marker : MARKERS) child("resolver", marker);
    }

    @Test public void normalServerPathsRetainTheirOriginalSpelling() throws Exception {
        child("normal", null);
    }

    @Test public void diagnosticHeadersAndDuplicatesSurviveIsolation() throws Exception {
        for (String state : new String[]{"missing", "empty", "header"}) child("diagnostic-" + state, "rsps.testServer");
    }

    @Test public void unavailableDiagnosticStorageNeverBreaksAnInteraction() throws Exception {
        child("diagnostic-failure", "rsps.testServer");
    }

    @Test public void aFullDiagnosticRegistryStillWritesOnlyToScratch() throws Exception {
        child("diagnostic-limit", "rsps.testServer");
    }

    private static void child(String mode, String marker) throws Exception {
        Path base = Files.createTempDirectory("rsps-N151-guard-");
        Path cwd = Files.createDirectories(base.resolve("working/nested"));
        Path tmp = Files.createDirectories(base.resolve("private-tmp"));
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        for (Class<?> type : new Class<?>[]{RuntimeFileIsolationTests.class, SharedDataFiles.class,
                UnhandledInteractions.class, kotlin.Unit.class, Test.class}) {
            entries.add(Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        }
        List<String> args = new ArrayList<>(Arrays.asList(
                Paths.get(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx96m",
                "-Djava.io.tmpdir=" + tmp));
        if (marker != null) args.add("-D" + marker + "=true");
        args.addAll(Arrays.asList("-cp", String.join(File.pathSeparator, entries),
                RuntimeFileIsolationTests.class.getName(), mode, base.toString()));
        Path log = base.resolve("probe.txt");
        Process process = new ProcessBuilder(args).directory(cwd.toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile()).start();
        // A 5000-row filesystem sweep can be slow on the owner's loaded laptop; this is a correctness guard.
        boolean ended = process.waitFor(mode.equals("diagnostic-limit") ? 120 : 30, TimeUnit.SECONDS);
        if (!ended) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        check(ended && process.exitValue() == 0, mode + ": " + Files.readString(log));
        // Keep the owned temporary fixture on failure for diagnosis; never clean any source/config/player data.
        try (java.util.stream.Stream<Path> paths = Files.walk(base)) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }

    private static File resolve(String path, boolean create) {
        return SharedDataFiles.INSTANCE.of(path, create);
    }

    private static void resolver(Path base) throws Exception {
        Path scratch = Paths.get(System.getProperty("java.io.tmpdir"), "rsps-conformance-data");
        SharedDataFiles shared = SharedDataFiles.INSTANCE;
        String[] registries = {"data/clans.txt", "data/friends_chat.json", "banned_players.txt",
                "data/killstreak_leaderboard.txt", "data/deadman_emblem_pairs.txt", "data/deadman_pk_daily.txt",
                "data/deadman_emblem_pending.txt", "data/ge/offers.json"};
        for (String name : registries) {
            Path origin = Paths.get(name);
            if (origin.getParent() != null) Files.createDirectories(origin.getParent());
            Files.writeString(origin, "seed " + name);
            File selected = shared.of(name);
            check(selected.toPath().startsWith(scratch), "registry outside scratch: " + name);
            check(Files.readString(selected.toPath()).equals("seed " + name), "seed missing: " + name);
            Files.writeString(selected.toPath(), "private change");
            check(Files.readString(origin).equals("seed " + name), "origin overwritten: " + name);
        }
        Path absolute = Files.createDirectories(base.resolve("absolute")).resolve("seed.txt");
        Files.writeString(absolute, "absolute seed");
        File selected = resolve(absolute.toString(), false);
        check(selected.toPath().startsWith(scratch) && Files.readString(selected.toPath()).equals("absolute seed"), "absolute mapping");
        Path alias = absolute.getParent().resolve("child/../seed.txt");
        check(resolve(alias.toString(), false).equals(selected), "canonical aliases differ");
        Path traversal = base.resolve("escaped.txt");
        Files.writeString(traversal, "traversal seed");
        File traversed = resolve("../../escaped.txt", false);
        check(traversed.toPath().startsWith(scratch), "parent traversal escaped scratch");
        check(Files.readString(traversed.toPath()).equals("traversal seed"), "traversal source not seeded");
        Path second = Files.createDirectories(base.resolve("other-parent")).resolve("seed.txt");
        Files.writeString(second, "different parent");
        File other = resolve(second.toString(), false);
        check(!other.equals(selected) && Files.readString(other.toPath()).equals("different parent"), "basename collision");
        Path absent = base.resolve("absolute/missing.pem"); // Synthetic missing file, never a real RSA key.
        File missing = resolve(absent.toString(), false);
        check(!missing.exists() && !Files.exists(absent), "missing=false created a placeholder");
        check(shared.of(absent.toString()).equals(missing) && missing.length() == 0 && missing.isFile(), "default missing semantics");
        Path parent = Paths.get("data/ge");
        for (String name : new String[]{"guide_prices.json", "guide_index.json", "history.json", "buy_limits.json"}) {
            Files.writeString(parent.resolve(name), "seed " + name);
        }
        File offers = shared.of("data/ge/offers.json");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int n = 0; n < 24; n++) futures.add(pool.submit(() -> {
                for (String name : new String[]{"guide_prices.json", "guide_index.json", "history.json", "buy_limits.json"}) {
                    try {
                        File sibling = shared.sibling(shared.of("data/ge/offers.json"), name);
                        check(sibling.getParentFile().equals(offers.getParentFile()), "sibling parent mismatch");
                        check(Files.readString(sibling.toPath()).equals("seed " + name), "concurrent seed mismatch");
                    } catch (Exception failure) { throw new RuntimeException(failure); }
                }
            }));
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        File noSibling = shared.sibling(offers, "not-present.json");
        check(!noSibling.exists(), "absent sibling created");
        try {
            shared.sibling(offers, "../escaped-sibling.json");
            throw new AssertionError("parent traversal accepted as a sibling");
        } catch (IllegalStateException expected) { /* Same-directory sibling contract. */ }
        File injected = base.resolve("injected/offers.json").toFile();
        check(shared.sibling(injected, "guide_prices.json").equals(new File(injected.getParentFile(), "guide_prices.json")), "injected fixture semantics");
        check(Files.readString(absolute).equals("absolute seed") && Files.readString(traversal).equals("traversal seed"), "original seeds changed");
        check(SharedDataFiles.class.getMethod("of", String.class) != null, "one-argument JVM API disappeared");
    }

    private static void diagnostic(Path base, String state) throws Exception {
        Path origin = Paths.get("logs/unhandled-object-actions.tsv");
        Files.createDirectories(origin.getParent());
        String header = "kind\tid\ttransform\tname\topt\toption\ttype\trot\tx\tz\theight\n";
        if (state.equals("empty")) Files.writeString(origin, "");
        if (state.equals("header")) Files.writeString(origin, header);
        byte[] before = Files.exists(origin) ? Files.readAllBytes(origin) : null;
        UnhandledInteractions registry = UnhandledInteractions.INSTANCE;
        UnhandledInteractions.Key key = new UnhandledInteractions.Key(999001, 999001, 1, 3200, 3200, 0);
        check(registry.record(key, "N151 fixture", "Open", 0, 0, "option"), "first object absent");
        check(!registry.record(key, "N151 fixture", "Open", 0, 0, "option"), "object duplicate");
        check(registry.recordInteraction("button", 999002, 3, "N151 fixture", "fixture"), "first generic absent");
        check(!registry.recordInteraction("button", 999002, 3, "N151 fixture", "fixture"), "generic duplicate");
        File selected = registry.getFILE();
        List<String> lines = Files.readAllLines(selected.toPath());
        check(lines.size() == 3 && lines.get(0).equals(header.trim()), "header/rows changed: " + state);
        check(Arrays.equals(before, Files.exists(origin) ? Files.readAllBytes(origin) : null), "diagnostic source changed");
        check(registry.size() == 2 && registry.count(key) == 2, "diagnostic counts changed");
    }

    public static void main(String[] args) throws Exception {
        Path base = Paths.get(args[1]);
        String mode = args[0];
        if (mode.equals("resolver")) resolver(base);
        else if (mode.equals("normal")) {
            for (String name : new String[]{"./missing/file.txt", base.resolve("absolute.txt").toString()}) {
                check(resolve(name, false).getPath().equals(new File(name).getPath()), "normal path changed");
                check(!resolve(name, false).exists(), "normal resolver created a file");
            }
        } else if (mode.equals("diagnostic-limit")) {
            UnhandledInteractions registry = UnhandledInteractions.INSTANCE;
            int limit = UnhandledInteractions.MAX_ROWS;
            for (int index = 0; index <= limit; index++) {
                boolean added = registry.recordInteraction("button", 999000 + index, 1, "N151 synthetic", "limit");
                check(added == (index < limit), "generic diagnostic limit changed at " + index);
            }
            check(registry.size() == limit, "full registry grew");
            check(registry.getFILE().toPath().startsWith(Paths.get(System.getProperty("java.io.tmpdir"))), "full registry outside scratch");
            check(Files.readAllLines(registry.getFILE().toPath()).size() == limit + 1, "full registry file length");
            check(!Files.exists(Paths.get("logs/unhandled-object-actions.tsv")), "full registry changed cwd");
        } else if (mode.equals("diagnostic-failure")) {
            Path blocked = base.resolve("blocked-tmp");
            Files.writeString(blocked, "not a directory");
            System.setProperty("java.io.tmpdir", blocked.toString());
            check(UnhandledInteractions.INSTANCE.recordInteraction("button", 999003, 1, "N151", "failure"), "failure changed dispatch result");
            check(UnhandledInteractions.INSTANCE.size() == 1, "failure changed diagnostic count");
        } else diagnostic(base, mode.substring("diagnostic-".length()));
        System.out.println("N151 ACTUAL CLASS GUARD OK " + mode);
    }
}
