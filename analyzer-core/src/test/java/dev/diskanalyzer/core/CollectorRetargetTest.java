package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorRetargetTest {

    private static FileNode scan(Path p) {
        return new DiskScanner(p, new ScanListener() { }, ScanExclusions.none()).scan().orElseThrow();
    }

    @Test
    void retargetRefreshesSizesAndDropsVanishedItems(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("keep"));
        Files.write(tmp.resolve("keep/f"), new byte[10]);
        Files.createDirectories(tmp.resolve("gone"));
        Files.write(tmp.resolve("gone/f"), new byte[5]);
        FileNode first = scan(tmp);
        Collector collector = new Collector();
        collector.add(Collector.find(first, tmp.resolve("keep")));
        collector.add(Collector.find(first, tmp.resolve("gone")));
        assertEquals(15, collector.reclaimableBytes());

        Files.write(tmp.resolve("keep/g"), new byte[30]);
        Files.delete(tmp.resolve("gone/f"));
        Files.delete(tmp.resolve("gone"));
        collector.retarget(scan(tmp));

        assertEquals(1, collector.items().size());
        assertEquals(40, collector.reclaimableBytes());
    }

    @Test
    void findLocatesNestedNodesAndReturnsNullForOutsiders(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("a/b"));
        Files.write(tmp.resolve("a/b/f"), new byte[1]);
        FileNode root = scan(tmp);

        assertEquals(tmp.resolve("a/b/f"), Collector.find(root, tmp.resolve("a/b/f")).path());
        assertEquals(root, Collector.find(root, tmp));
        assertNull(Collector.find(root, tmp.resolve("a/missing")));
        assertNull(Collector.find(root, tmp.resolveSibling("elsewhere")));
    }

    @Test
    void childrenOfListsEntriesAndToleratesMissingDirs(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("disk1"));
        Files.createDirectories(tmp.resolve("disk2"));

        Set<Path> children = ProtectedPaths.childrenOf(tmp);

        assertEquals(Set.of(tmp.resolve("disk1"), tmp.resolve("disk2")), children);
        assertTrue(ProtectedPaths.childrenOf(tmp.resolve("nope")).isEmpty());
        assertEquals(List.of(), List.copyOf(ProtectedPaths.childrenOf(tmp.resolve("nope"))));
    }
}
