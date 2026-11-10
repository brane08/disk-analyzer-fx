package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class FileNodeTest {

    private static FileNode tree() {
        FileNode root = new FileNode("root", Path.of("/r"), true, null, 0);
        FileNode small = new FileNode("small.TXT", Path.of("/r/small.TXT"), false, root, 10);
        FileNode big = new FileNode("big", Path.of("/r/big"), false, root, 500);
        root.addChild(small);
        root.addChild(big);
        return root;
    }

    @Test
    void addChildAccumulatesSizeAndCounts() {
        FileNode root = tree();
        assertEquals(510, root.size());
        assertEquals(2, root.fileCount());
    }

    @Test
    void sortChildrenOrdersLargestFirst() {
        FileNode root = tree();
        root.sortChildren();
        assertEquals("big", root.children().get(0).name());
        assertEquals("big", root.children().get(0).toString());
    }

    @Test
    void copyIsDeepAndRewiresParent() {
        FileNode root = tree();
        root.markUnreadable();

        FileNode copy = root.copy(null);

        assertNotSame(root, copy);
        assertNotSame(root.children().get(0), copy.children().get(0));
        assertSame(copy, copy.children().get(0).parent());
        assertEquals(root.size(), copy.size());
        assertEquals(root.fileCount(), copy.fileCount());
        assertEquals(1, copy.skippedDirs());
        assertEquals(root.path(), copy.path());
        assertEquals(root.isDir(), copy.isDir());
    }

    @Test
    void extensionIsLowercasedAndEmptyWithoutOne() {
        FileNode root = tree();
        assertEquals("txt", root.children().get(0).extension());
        assertEquals("", root.children().get(1).extension());
    }

    @Test
    void humanFormatsUnits() {
        assertEquals("0 B", FileNode.human(0));
        assertEquals("1023 B", FileNode.human(1023));
        assertEquals("1.0 KB", FileNode.human(1024));
        assertEquals("1.5 MB", FileNode.human(1536L * 1024));
        assertEquals("1.0 GB", FileNode.human(1L << 30));
        assertEquals("1.0 TB", FileNode.human(1L << 40));
    }
}
