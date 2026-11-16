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
    void protectedPathsAreRefused(@TempDir Path tmp) throws IOException {
        Path home = Files.createDirectories(tmp.resolve("home"));
        Path library = Files.createDirectories(home.resolve("Library"));
        Cleaner safe = new Cleaner(new ProtectedPaths(home, Os.MAC), p -> {
            trashed.add(p);
            return true;
        });
        List<FileNode> items = List.of(
                new FileNode("home", home, true, null, 1),
                new FileNode("Library", library, true, null, 1));

        CleanResult r = safe.clean(items, Mode.PERMANENT);

        assertTrue(r.removed().isEmpty());
        assertTrue(trashed.isEmpty());
        assertEquals(2, r.failed().size());
        assertTrue(r.failed().values().stream().allMatch("protected path"::equals));
        assertEquals(0, r.freedBytes());
        assertTrue(Files.isDirectory(library));
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void systemRootsAreProtectedWithoutTouchingThem() {
        ProtectedPaths pp = new ProtectedPaths(HOME, Os.MAC);

        assertTrue(pp.isProtected(Path.of("/")));
        assertTrue(pp.isProtected(Path.of("/System")));
        assertTrue(pp.isProtected(Path.of("/Volumes")));
        assertTrue(pp.isProtected(Path.of("/private")));
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void reparsePointsAreTreatedAsLinks(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("target/f"), 4);
        Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("target"));
        var linkAttrs = Files.readAttributes(link, java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        var dirAttrs = Files.readAttributes(tmp.resolve("target"), java.nio.file.attribute.BasicFileAttributes.class);

        assertTrue(Cleaner.isLinkLike(link, linkAttrs));
        assertFalse(Cleaner.isLinkLike(tmp.resolve("target"), dirAttrs));
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
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void permanentDeleteContinuesPastFailures(@TempDir Path tmp) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                tmp.getFileSystem().supportedFileAttributeViews().contains("posix"));
        write(tmp.resolve("victim/locked/f"), 1);
        write(tmp.resolve("victim/ok/g"), 1);
        FileNode victim = scan(tmp.resolve("victim"));
        Path locked = tmp.resolve("victim/locked");
        Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isWritable(locked));

            CleanResult r = cleaner.clean(List.of(victim), Mode.PERMANENT);

            assertEquals(1, r.failed().size());
            assertTrue(r.failed().get(tmp.resolve("victim")).startsWith("partial delete"), r.failed().toString());
            assertTrue(r.freedBytes() > 0, "bytes of the deleted sibling count as freed");
            assertFalse(r.removed().contains(tmp.resolve("victim")), "victim must not count as removed");
            assertFalse(Files.exists(tmp.resolve("victim/ok")));
            assertTrue(Files.exists(locked.resolve("f")));
        } finally {
            Files.setPosixFilePermissions(locked,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void xdgTrashMovesEntryAndWritesInfo(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("data");
        write(tmp.resolve("my dir/f"), 2);
        write(tmp.resolve("other/my dir/f"), 2);

        assertTrue(Cleaner.xdgTrash(tmp.resolve("my dir"), data));
        assertTrue(Cleaner.xdgTrash(tmp.resolve("other/my dir"), data));

        assertFalse(Files.exists(tmp.resolve("my dir")));
        assertTrue(Files.exists(data.resolve("Trash/files/my dir/f")));
        assertTrue(Files.exists(data.resolve("Trash/files/my dir.1/f")));
        String info = Files.readString(data.resolve("Trash/info/my dir.trashinfo"));
        assertTrue(info.startsWith("[Trash Info]\nPath="));
        assertTrue(info.contains("my%20dir"), info);
        assertTrue(info.contains("DeletionDate="));
    }

    @Test
    void trashAvailableReflectsCapability() {
        boolean first = Cleaner.trashAvailable();

        assertEquals(first, Cleaner.trashAvailable());
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void doesNotWalkIntoSymlinkDirectory(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("target/deep/f"), 4);
        Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("target"));
        FileNode linkNode = new FileNode("link", link, true, null, 4);

        CleanResult r = cleaner.clean(List.of(linkNode), Mode.PERMANENT);

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(tmp.resolve("target/deep/f")));
        assertTrue(r.failed().isEmpty(), r.failed().toString());
        assertEquals(List.of(link), r.removed());
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
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void symlinkIsRemovedWithoutFollowing(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("target/f"), 4);
        Path link = Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("target"));
        FileNode linkNode = new FileNode("link", link, false, null, 0);

        cleaner.clean(List.of(linkNode), Mode.PERMANENT);

        assertFalse(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
        assertTrue(Files.exists(tmp.resolve("target/f")));
    }
}
