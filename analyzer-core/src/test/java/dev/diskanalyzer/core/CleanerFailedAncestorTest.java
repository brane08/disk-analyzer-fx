package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanerFailedAncestorTest {

    @Test
    void childOfAFailedItemIsReportedNotSilentlyDropped(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("p"));
        Files.write(tmp.resolve("p/c"), new byte[8]);
        FileNode root = new DiskScanner(tmp, new ScanListener() { }, ScanExclusions.none()).scan().orElseThrow();
        FileNode parent = Collector.find(root, tmp.resolve("p"));
        FileNode child = Collector.find(root, tmp.resolve("p/c"));
        Cleaner declining = new Cleaner(new ProtectedPaths(tmp.resolve("home"), Os.LINUX), p -> false);

        CleanResult r = declining.clean(List.of(parent, child), Mode.TRASH);

        assertTrue(r.removed().isEmpty());
        assertEquals(2, r.failed().size());
        assertEquals("trash unavailable", r.failed().get(tmp.resolve("p")));
        assertEquals("not removed: containing item failed", r.failed().get(tmp.resolve("p/c")));
    }
}
