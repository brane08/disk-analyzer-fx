package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanExclusionsTest {

    @Test
    void excludedFolderIsListedButNotDescended(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("skip"));
        Files.write(tmp.resolve("skip/huge"), new byte[1000]);
        Files.write(tmp.resolve("keep"), new byte[5]);

        FileNode root = new DiskScanner(tmp, new ScanListener() { }, new ScanExclusions(tmp.resolve("skip")))
                .scan().orElseThrow();

        assertEquals(5, root.size());
        FileNode skip = root.children().stream().filter(c -> c.name().equals("skip")).findFirst().orElseThrow();
        assertTrue(skip.isDir());
        assertTrue(skip.children().isEmpty());
    }

    @Test
    void scanRootItselfIsNeverExcluded(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("f"), new byte[3]);

        FileNode root = new DiskScanner(tmp, new ScanListener() { }, new ScanExclusions(tmp)).scan().orElseThrow();

        assertEquals(3, root.size());
    }

    @Test
    void perOsDefaults() {
        assertTrue(ScanExclusions.forOs(Os.LINUX).contains(Path.of("/proc")));
        assertTrue(ScanExclusions.forOs(Os.MAC).contains(Path.of("/System/Volumes")));
        assertFalse(ScanExclusions.forOs(Os.LINUX).contains(Path.of("/usr")));
        assertFalse(ScanExclusions.forOs(Os.WINDOWS).contains(Path.of("/proc")));
    }
}
