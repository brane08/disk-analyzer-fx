package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymlinkRootScanTest {

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void symlinkedRootIsScannedAsTheDirectoryItPointsTo(@TempDir Path tmp) throws IOException {
        Path target = Files.createDirectories(tmp.resolve("target"));
        Files.write(target.resolve("f"), new byte[7]);
        Path link = Files.createSymbolicLink(tmp.resolve("link"), target);

        FileNode root = new DiskScanner(link, new ScanListener() { }).scan().orElseThrow();

        assertTrue(root.isDir());
        assertEquals(7, root.size());
    }
}
