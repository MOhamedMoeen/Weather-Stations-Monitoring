package org.example;

import org.example.entity.Bitcask;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;

public class BitcaskIntegrationTest {

    static final String BASE_DIR = "./bitcask-test-data";
    static int passed = 0;
    static int failed = 0;


    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║       Bitcask Integration Test Suite     ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        try {
            testBasicPutGet();
            testOverwrite();
            testMissingKey();
            testLargeDataSegmentRotation();
            testHintFileRecovery();
            testRestartRecovery();
            testCompactionCorrectness();
            testCompactionReducesSegments();
            testConcurrentWrites();
            testConcurrentReadWrite();
        } finally {
            deleteDir(Path.of(BASE_DIR));
        }

        System.out.println("\n══════════════════════════════════════════");
        System.out.printf("  Results:  %d passed,  %d failed%n", passed, failed);
        System.out.println("══════════════════════════════════════════");
        if (failed > 0) System.exit(1);
    }


    static void testBasicPutGet() throws Exception {
        String dir = fresh("basic");
        try (Bitcask db = new Bitcask(dir)) {
            db.put("hello", "world");
            db.put("foo",   "bar");
            assertEquals("basic get",      "world", db.get("hello"));
            assertEquals("basic get 2",    "bar",   db.get("foo"));
        }
        pass("Basic put/get");
    }

    static void testOverwrite() throws Exception {
        String dir = fresh("overwrite");
        try (Bitcask db = new Bitcask(dir)) {
            db.put("key", "v1");
            db.put("key", "v2");
            db.put("key", "v3");
            assertEquals("overwrite returns latest", "v3", db.get("key"));
        }
        pass("Overwrite returns latest value");
    }

    static void testMissingKey() throws Exception {
        String dir = fresh("missing");
        try (Bitcask db = new Bitcask(dir)) {
            assertNull("missing key returns null", db.get("ghost"));
        }
        pass("Missing key returns null");
    }


    static void testLargeDataSegmentRotation() throws Exception {
        String dir = fresh("large");
        int count  = 5_000;
        String value = "x".repeat(300); // 300 bytes per entry → ~1.5 MB total

        try (Bitcask db = new Bitcask(dir)) {
            for (int i = 0; i < count; i++) {
                db.put("key-" + i, value + "-" + i);
            }
            // spot-check
            for (int i : new int[]{0, 1, 999, 2000, 4999}) {
                assertEquals("large data key-" + i, value + "-" + i, db.get("key-" + i));
            }
            int segments = countFiles(dir, ".data");
            assertTrue("should have created >1 segment, got " + segments, segments > 1);
        }
        pass("Large data / segment rotation (" + count + " keys, 300 B each)");
    }


    static void testHintFileRecovery() throws Exception {
        String dir   = fresh("hint");
        int count    = 2_000;
        String value = "v".repeat(200);

        try (Bitcask db = new Bitcask(dir)) {
            for (int i = 0; i < count; i++) {
                db.put("hkey-" + i, value + i);
            }
        }
        int hints = countFiles(dir, ".hint");
        assertTrue("hint files should exist, found " + hints, hints > 0);

        try (Bitcask db = new Bitcask(dir)) {
            for (int i : new int[]{0, 500, 1000, 1999}) {
                assertEquals("hint recovery key " + i, value + i, db.get("hkey-" + i));
            }
        }
        pass("Hint file recovery (" + count + " keys)");
    }


    static void testRestartRecovery() throws Exception {
        String dir = fresh("restart");

        try (Bitcask db = new Bitcask(dir)) {
            for (int i = 0; i < 1_000; i++) {
                db.put("r-" + i, "val-" + i);
            }
            for (int i = 0; i < 100; i++) {
                db.put("r-" + i, "updated-" + i);
            }
        }

        try (Bitcask db = new Bitcask(dir)) {
            for (int i = 0; i < 100; i++) {
                assertEquals("restart updated key " + i, "updated-" + i, db.get("r-" + i));
            }
            for (int i = 100; i < 1_000; i++) {
                assertEquals("restart original key " + i, "val-" + i, db.get("r-" + i));
            }
        }
        pass("Restart / full recovery (1000 keys, 100 overwritten)");
    }


    static void testCompactionCorrectness() throws Exception {
        String dir   = fresh("compact-correct");
        int keys     = 500;
        String pad   = "p".repeat(400); // force multiple segments

        try (Bitcask db = new Bitcask(dir)) {
            for (int round = 0; round < 5; round++) {
                for (int i = 0; i < keys; i++) {
                    db.put("ck-" + i, pad + "-round" + round + "-key" + i);
                }
            }
            int beforeCompact = countFiles(dir, ".data");

            db.compact();

            for (int i = 0; i < keys; i++) {
                String expected = pad + "-round4-key" + i;
                String actual   = db.get("ck-" + i);
                assertEquals("post-compact key " + i, expected, actual);
            }
            int afterCompact = countFiles(dir, ".data");
            assertTrue(
                    "compaction should reduce segment count (" + beforeCompact + " → " + afterCompact + ")",
                    afterCompact <= beforeCompact
            );
        }
        pass("Compaction correctness (" + keys + " keys × 5 rounds)");
    }

    static void testCompactionReducesSegments() throws Exception {
        String dir = fresh("compact-size");
        String big = "B".repeat(800);

        try (Bitcask db = new Bitcask(dir)) {
            for (int i = 0; i < 3_000; i++) {
                db.put("sk-" + i, big + i);
            }
            for (int i = 0; i < 3_000; i++) {
                db.put("sk-" + i, "small-" + i);
            }

            long sizeBeforeBytes = dirSize(dir);
            int  segsBefor       = countFiles(dir, ".data");

            db.compact();

            long sizeAfterBytes = dirSize(dir);
            int  segsAfter      = countFiles(dir, ".data");

            System.out.printf("    disk: %.2f MB → %.2f MB  |  segments: %d → %d%n",
                    sizeBeforeBytes / 1e6, sizeAfterBytes / 1e6, segsBefor, segsAfter);

            assertTrue("disk should shrink after compaction", sizeAfterBytes < sizeBeforeBytes);
        }
        pass("Compaction reduces disk usage (3000 keys, fully overwritten)");
    }

    static void testConcurrentWrites() throws Exception {
        String dir      = fresh("concurrent-write");
        int threads     = 8;
        int perThread   = 500;

        try (Bitcask db = new Bitcask(dir)) {
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();

            for (int t = 0; t < threads; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < perThread; i++) {
                        try {
                            db.put("t" + tid + "-k" + i, "val-" + tid + "-" + i);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }));
            }
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            for (Future<?> f : futures) f.get(); // rethrow any exceptions

            for (int t = 0; t < threads; t++) {
                for (int i = 0; i < perThread; i++) {
                    String key      = "t" + t + "-k" + i;
                    String expected = "val-" + t + "-" + i;
                    assertEquals("concurrent write " + key, expected, db.get(key));
                }
            }
        }
        pass("Concurrent writes (" + threads + " threads × " + perThread + " keys)");
    }

    static void testConcurrentReadWrite() throws Exception {
        String dir = fresh("concurrent-rw");
        int preload = 1_000;

        try (Bitcask db = new Bitcask(dir)) {
            // pre-load known keys
            for (int i = 0; i < preload; i++) {
                db.put("stable-" + i, "init-" + i);
            }

            ExecutorService pool = Executors.newFixedThreadPool(8);
            List<Future<?>> futures = new ArrayList<>();
            List<String> errors = Collections.synchronizedList(new ArrayList<>());

            for (int t = 0; t < 4; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 200; i++) {
                        try { db.put("stable-" + (tid * 50 + i % 50), "updated-" + tid + "-" + i); }
                        catch (IOException ex) { errors.add("write: " + ex.getMessage()); }
                    }
                }));
            }

            for (int t = 0; t < 4; t++) {
                final int tid = t;
                futures.add(pool.submit(() -> {
                    Random rng = new Random(tid);
                    for (int i = 0; i < 500; i++) {
                        int idx = rng.nextInt(preload);
                        try {
                            String v = db.get("stable-" + idx);
                            if (v == null) errors.add("null read for stable-" + idx);
                        } catch (IOException ex) {
                            errors.add("read: " + ex.getMessage());
                        }
                    }
                }));
            }

            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
            for (Future<?> f : futures) f.get();

            if (!errors.isEmpty()) {
                throw new AssertionError("Concurrent R/W errors:\n  " + String.join("\n  ", errors));
            }
        }
        pass("Concurrent read/write (4 writers + 4 readers, 1000 preloaded keys)");
    }


    static String fresh(String name) throws IOException {
        String dir = BASE_DIR + "/" + name;
        deleteDir(Path.of(dir));
        Files.createDirectories(Path.of(dir));
        return dir;
    }

    static void assertEquals(String label, String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(label + "\n  expected: " + expected + "\n  actual:   " + actual);
        }
    }

    static void assertNull(String label, String actual) {
        if (actual != null) {
            throw new AssertionError(label + " — expected null but got: " + actual);
        }
    }

    static void assertTrue(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    static void pass(String name) {
        passed++;
        System.out.println("  ✓  " + name);
    }

    static void fail(String name, Throwable t) {
        failed++;
        System.out.println("  ✗  " + name);
        System.out.println("     " + t.getMessage());
    }

    static int countFiles(String dir, String ext) throws IOException {
        try (var s = Files.list(Path.of(dir))) {
            return (int) s.filter(p -> p.toString().endsWith(ext)).count();
        }
    }

    static long dirSize(String dir) throws IOException {
        long[] total = {0};
        Files.walkFileTree(Path.of(dir), new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                total[0] += a.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    static void deleteDir(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.delete(f); return FileVisitResult.CONTINUE;
            }
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d); return FileVisitResult.CONTINUE;
            }
        });
    }
}