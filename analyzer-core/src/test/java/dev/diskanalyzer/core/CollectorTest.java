package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorTest {

    private final FileNode parent = new FileNode("p", Path.of("/x/p"), true, null, 0);
    private final FileNode child = new FileNode("c", Path.of("/x/p/c"), false, parent, 40);
    private final FileNode sibling = new FileNode("s", Path.of("/x/p/s"), false, parent, 60);
    private final FileNode other = new FileNode("o", Path.of("/x/o"), false, null, 5);

    CollectorTest() {
        parent.addChild(child);
        parent.addChild(sibling);
    }

    @Test
    void ignoresDescendantOfCollectedAncestor() {
        Collector c = new Collector();
        c.add(parent);
        c.add(child);
        assertEquals(List.of(parent), c.items());
    }

    @Test
    void ancestorReplacesDescendants() {
        Collector c = new Collector();
        c.add(child);
        c.add(sibling);
        c.add(parent);
        assertEquals(List.of(parent), c.items());
    }

    @Test
    void reclaimableCountsOnce() {
        Collector c = new Collector();
        c.add(child);
        c.add(parent);
        c.add(other);
        assertEquals(105, c.reclaimableBytes());
    }

    @Test
    void siblingsDoNotCollide() {
        Collector c = new Collector();
        c.add(child);
        c.add(sibling);
        assertEquals(2, c.items().size());
        assertEquals(100, c.reclaimableBytes());
    }

    @Test
    void removeAndClear() {
        Collector c = new Collector();
        c.add(child);
        c.add(other);
        c.remove(child);
        assertEquals(List.of(other), c.items());
        c.clear();
        assertEquals(0, c.reclaimableBytes());
    }
}
