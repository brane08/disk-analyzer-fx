package dev.diskanalyzer.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiskScannerTest {

    private static void write(Path file, int bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
    }

    private static FileNode scan(Path root) {
        return new DiskScanner(root, new ScanListener() { }).scan().orElseThrow();
    }

    @Test
    void sumsFileSizesAndCounts(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f1"), 10);
        write(tmp.resolve("b/f2"), 20);

        FileNode root = scan(tmp);

        assertEquals(30, root.size());
        assertEquals(2, root.fileCount());
        assertEquals("b", root.children().get(0).name());
        assertEquals("a", root.children().get(1).name());
    }

    @Test
    void doesNotFollowSymlinkLoop(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f1"), 5);
        Files.createSymbolicLink(tmp.resolve("a/loop"), tmp);

        FileNode root = scan(tmp);

        FileNode a = root.children().stream().filter(c -> c.name().equals("a")).findFirst().orElseThrow();
        FileNode loop = a.children().stream().filter(c -> c.name().equals("loop")).findFirst().orElseThrow();
        assertFalse(loop.isDir());
    }

    @Test
    void skipsUnreadableDirectory(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("ok/f1"), 7);
        Path locked = tmp.resolve("locked");
        Files.createDirectories(locked);
        write(locked.resolve("hidden"), 3);
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            Assumptions.assumeFalse(Files.isReadable(locked), "running with privileges that bypass permissions");
            FileNode root = scan(tmp);
            assertEquals(7, root.size());
            assertEquals(1, root.skippedDirs());
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void cancelReturnsEmpty(@TempDir Path tmp) throws IOException {
        for (int i = 0; i < 600; i++) {
            write(tmp.resolve("d/f" + i), 1);
        }
        AtomicReference<DiskScanner> ref = new AtomicReference<>();
        DiskScanner scanner = new DiskScanner(tmp, new ScanListener() {
            @Override
            public void onProgress(ScanProgress p) {
                ref.get().cancel();
            }
        });
        ref.set(scanner);

        assertEquals(Optional.empty(), scanner.scan());
    }

    @Test
    void fileRootGivesSingleNode(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("only.bin");
        write(f, 42);

        FileNode root = scan(f);

        assertFalse(root.isDir());
        assertEquals(42, root.size());
    }

    @Test
    void missingRootThrows(@TempDir Path tmp) {
        assertThrows(IllegalArgumentException.class,
                () -> new DiskScanner(tmp.resolve("nope"), new ScanListener() { }).scan());
    }

    @Test
    void extensionIgnoresLeadingDotAndMissingExtension(@TempDir Path tmp) throws IOException {
        write(tmp.resolve(".gitignore"), 1);
        write(tmp.resolve("Makefile"), 1);
        write(tmp.resolve("a.TXT"), 1);

        FileNode root = scan(tmp);

        assertEquals("", byName(root, ".gitignore").extension());
        assertEquals("", byName(root, "Makefile").extension());
        assertEquals("txt", byName(root, "a.TXT").extension());
    }

    @Test
    void partialSnapshotsGrowMonotonically(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f1"), 10);
        write(tmp.resolve("b/f2"), 20);
        write(tmp.resolve("c/f3"), 30);
        java.util.List<Long> sizes = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        FileNode root = new DiskScanner(tmp, new ScanListener() {
            @Override
            public void onPartial(FileNode partial) {
                sizes.add(partial.size());
            }
        }).scan().orElseThrow();

        assertEquals(3, sizes.size());
        for (int i = 1; i < sizes.size(); i++) {
            assertTrue(sizes.get(i) >= sizes.get(i - 1), "sizes must not shrink: " + sizes);
        }
        assertEquals(root.size(), sizes.get(sizes.size() - 1));
    }

    @Test
    void partialSnapshotIsDetachedFromLiveTree(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f1"), 10);
        write(tmp.resolve("b/f2"), 20);
        java.util.List<FileNode> partials = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        FileNode root = new DiskScanner(tmp, new ScanListener() {
            @Override
            public void onPartial(FileNode partial) {
                partials.add(partial);
            }
        }).scan().orElseThrow();

        FileNode first = partials.get(0);
        assertNotSame(root, first);
        assertEquals(1, first.children().size());
        assertSame(first, first.children().get(0).parent());
    }

    @Test
    void parallelScanMatchesExpectedTotals(@TempDir Path tmp) throws IOException {
        long expected = 0;
        for (int d = 0; d < 10; d++) {
            for (int f = 0; f < 50; f++) {
                int bytes = d * 3 + f % 7 + 1;
                write(tmp.resolve("d" + d + "/f" + f), bytes);
                expected += bytes;
            }
        }

        FileNode root = scan(tmp);

        assertEquals(expected, root.size());
        assertEquals(500, root.fileCount());
        assertEquals(10, root.children().size());
    }

    @Test
    void otherDirectoryLikeEntryIsNotTraversable() {
        BasicFileAttributes junction = attrs(true, false, true);
        BasicFileAttributes symlinkDir = attrs(true, true, false);
        BasicFileAttributes normalDir = attrs(true, false, false);
        BasicFileAttributes file = attrs(false, false, false);

        assertFalse(DiskScanner.isTraversableDirectory(junction));
        assertFalse(DiskScanner.isTraversableDirectory(symlinkDir));
        assertTrue(DiskScanner.isTraversableDirectory(normalDir));
        assertFalse(DiskScanner.isTraversableDirectory(file));
    }

    @Test
    void symlinkDirectoryIsLeaf(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("target/f"), 4);
        Files.createSymbolicLink(tmp.resolve("link"), tmp.resolve("target"));

        FileNode root = scan(tmp);
        FileNode link = byName(root, "link");

        assertFalse(link.isDir());
        assertEquals(4, byName(root, "target").size());
    }

    private static java.nio.file.attribute.BasicFileAttributes attrs(
            boolean directory, boolean symbolicLink, boolean other) {
        return new java.nio.file.attribute.BasicFileAttributes() {
            @Override public java.nio.file.attribute.FileTime lastModifiedTime() { return null; }
            @Override public java.nio.file.attribute.FileTime lastAccessTime() { return null; }
            @Override public java.nio.file.attribute.FileTime creationTime() { return null; }
            @Override public boolean isRegularFile() { return !directory && !symbolicLink && !other; }
            @Override public boolean isDirectory() { return directory; }
            @Override public boolean isSymbolicLink() { return symbolicLink; }
            @Override public boolean isOther() { return other; }
            @Override public long size() { return 0; }
            @Override public Object fileKey() { return null; }
        };
    }

    private static FileNode byName(FileNode root, String name) {
        return root.children().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }
}
