package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanerTest {
    private static final Path HOME = Path.of("/Users/tester");

    private final List<Path> trashed = new ArrayList<>();
    private final Cleaner cleaner = new Cleaner(new ProtectedPaths(HOME), p -> {
        trashed.add(p);
        return true;
    });

    private static FileNode scan(Path p) {
        return new DiskScanner(p, new ScanListener() { }).scan().orElseThrow();
    }

    private static void write(Path file, int bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
    }

    @Test
    void protectedPathsAreRefused() {
        List<FileNode> items = List.of(
                new FileNode("/", Path.of("/"), true, null, 1),
                new FileNode("tester", HOME, true, null, 1),
                new FileNode("Library", HOME.resolve("Library"), true, null, 1),
                new FileNode("System", Path.of("/System"), true, null, 1));

        CleanResult r = cleaner.clean(items, Mode.TRASH);

        assertTrue(r.removed().isEmpty());
        assertTrue(trashed.isEmpty());
        assertEquals(4, r.failed().size());
        assertTrue(r.failed().values().stream().allMatch("protected path"::equals));
        assertEquals(0, r.freedBytes());
    }

    @Test
    void trashRefusalAndFailuresAreReported(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f"), 1);
        write(tmp.resolve("b/f"), 1);
        FileNode a = scan(tmp.resolve("a"));
        FileNode b = scan(tmp.resolve("b"));

        CleanResult declined = new Cleaner(new ProtectedPaths(HOME), p -> false).clean(List.of(a), Mode.TRASH);
        CleanResult broken = new Cleaner(new ProtectedPaths(HOME), p -> {
            throw new IOException("boom");
        }).clean(List.of(b), Mode.TRASH);

        assertEquals("trash unavailable", declined.failed().get(tmp.resolve("a")));
        assertEquals("boom", broken.failed().get(tmp.resolve("b")));
        assertTrue(declined.removed().isEmpty() && broken.removed().isEmpty());
    }

    @Test
    void defaultTrasherConstructorIsUsable(@TempDir Path tmp) {
        FileNode ghost = new FileNode("ghost", tmp.resolve("ghost"), false, null, 1);

        CleanResult r = new Cleaner(new ProtectedPaths(HOME)).clean(List.of(ghost), Mode.TRASH);

        assertEquals("not found", r.failed().get(tmp.resolve("ghost")));
    }

    @Test
    void permanentDeletesRecursively(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("victim/a/f"), 10);
        write(tmp.resolve("victim/g"), 5);
        FileNode victim = scan(tmp.resolve("victim"));

        CleanResult r = cleaner.clean(List.of(victim), Mode.PERMANENT);

        assertFalse(Files.exists(tmp.resolve("victim")));
        assertEquals(List.of(tmp.resolve("victim")), r.removed());
        assertEquals(15, r.freedBytes());
        assertTrue(r.failed().isEmpty());
    }

    @Test
    void trashUsesInjectedTrasher(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("keep/f"), 3);
        FileNode keep = scan(tmp.resolve("keep"));

        CleanResult r = cleaner.clean(List.of(keep), Mode.TRASH);

        assertEquals(List.of(tmp.resolve("keep")), trashed);
        assertTrue(Files.exists(tmp.resolve("keep/f")));
        assertEquals(3, r.freedBytes());
    }

    @Test
    void missingFileIsReportedAsFailedNotThrown(@TempDir Path tmp) {
        FileNode ghost = new FileNode("ghost", tmp.resolve("ghost"), false, null, 9);

        CleanResult r = cleaner.clean(List.of(ghost), Mode.PERMANENT);

        assertEquals(1, r.failed().size());
        assertEquals(0, r.freedBytes());
    }

    @Test
    void parentAndChildTogetherDoNotFail(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("p/c"), 8);
        FileNode p = scan(tmp.resolve("p"));
        FileNode c = p.children().get(0);

        CleanResult r = cleaner.clean(List.of(c, p), Mode.PERMANENT);

        assertTrue(r.failed().isEmpty());
        assertEquals(8, r.freedBytes());
        assertFalse(Files.exists(tmp.resolve("p")));
    }

    @Test
    void symlinkIsRemovedWithoutFollowing(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("target/f"), 4);
        Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("target"));
        FileNode linkNode = new FileNode("link", link, false, null, 0);

        cleaner.clean(List.of(linkNode), Mode.PERMANENT);

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(tmp.resolve("target/f")));
    }
}
