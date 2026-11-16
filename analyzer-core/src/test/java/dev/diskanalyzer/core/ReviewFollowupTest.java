package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
class ReviewFollowupTest {

    private static FileNode scan(Path p) {
        return new DiskScanner(p, new ScanListener() { }, ScanExclusions.none()).scan().orElseThrow();
    }

    @Test
    void symlinkedHomeKeepsHomeProtectionsOnTheRealPath(@TempDir Path tmp) throws IOException {
        Path real = Files.createDirectories(tmp.resolve("data/u"));
        Path link = Files.createSymbolicLink(tmp.resolve("home-link"), real);

        ProtectedPaths pp = new ProtectedPaths(link, Os.LINUX);

        assertTrue(pp.isProtected(real));
        assertTrue(pp.isProtected(real.resolve(".config")));
        assertTrue(pp.isProtected(link.resolve(".config")));
        assertFalse(pp.isProtected(real.resolve("Downloads/file")));
    }

    @Test
    void credentialFoldersAreProtectedWithEverythingInside() {
        Path home = Path.of("/Users/tester");
        ProtectedPaths mac = new ProtectedPaths(home, Os.MAC);
        ProtectedPaths linux = new ProtectedPaths(Path.of("/home/tester"), Os.LINUX);

        assertTrue(mac.isProtected(home.resolve(".ssh/id_ed25519")));
        assertTrue(mac.isProtected(home.resolve(".gnupg")));
        assertTrue(linux.isProtected(Path.of("/home/tester/.ssh/known_hosts")));
        assertTrue(linux.isProtected(Path.of("/home/tester/.local/share")));
        assertFalse(linux.isProtected(Path.of("/home/tester/.cache/pip")));
    }

    @Test
    void macTempFoldersUnderPrivateVarAreProtected() {
        ProtectedPaths mac = new ProtectedPaths(Path.of("/Users/tester"), Os.MAC);

        assertTrue(mac.isProtected(Path.of("/private/var/folders/ab/xyz/T/file")));
        assertTrue(mac.isProtected(Path.of("/Volumes")));
    }

    @Test
    void retargetKeepsItemsFromOtherScans(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("a"));
        Files.write(tmp.resolve("a/f"), new byte[4]);
        Files.createDirectories(tmp.resolve("b"));
        Files.write(tmp.resolve("b/f"), new byte[9]);
        FileNode aScan = scan(tmp.resolve("a"));
        FileNode bScan = scan(tmp.resolve("b"));
        Collector collector = new Collector();
        collector.add(aScan);

        collector.retarget(bScan);

        assertEquals(1, collector.items().size());
        assertEquals(4, collector.reclaimableBytes());
    }

    @Test
    void mountPointCheckIgnoresOrdinaryFolders(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("plain"));

        assertFalse(Cleaner.isMountPoint(dir));
        assertFalse(Cleaner.isMountPoint(Files.createSymbolicLink(tmp.resolve("l"), dir)));
        assertFalse(Cleaner.isMountPoint(Path.of("/")));
    }

    @Test
    void trashSkipsNamesAlreadyTakenInFiles(@TempDir Path tmp) throws IOException {
        Path data = tmp.resolve("data");
        Files.createDirectories(data.resolve("Trash/files/f"));
        Files.write(tmp.resolve("f"), new byte[1]);

        assertTrue(Cleaner.xdgTrash(tmp.resolve("f"), data));

        assertTrue(Files.exists(data.resolve("Trash/files/f.1")));
        assertTrue(Files.exists(data.resolve("Trash/info/f.1.trashinfo")));
    }

    @Test
    void deleteDoesNotLeaveALinkedTargetBehindWhenTheTreeContainsSymlinks(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("victim"));
        Files.write(tmp.resolve("keep"), new byte[3]);
        Files.createSymbolicLink(tmp.resolve("victim/link"), tmp.resolve("keep"));
        FileNode victim = Collector.find(scan(tmp), tmp.resolve("victim"));
        Cleaner cleaner = new Cleaner(new ProtectedPaths(tmp.resolve("home"), Os.LINUX), p -> true);

        CleanResult r = cleaner.clean(java.util.List.of(victim), Mode.PERMANENT);

        assertTrue(r.failed().isEmpty(), r.failed().toString());
        assertFalse(Files.exists(tmp.resolve("victim")));
        assertTrue(Files.exists(tmp.resolve("keep")));
    }
}
