package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputGuardsTest {

    @Test
    void collectorRejectsNull() {
        assertThrows(NullPointerException.class, () -> new Collector().add(null));
    }

    @Test
    void searchWithNonPositiveLimitReturnsNothing() {
        FileNode root = new FileNode("root", Path.of("/root"), true, null, 0);
        SearchIndex index = SearchIndex.of(root);

        assertTrue(index.search("root", -1).isEmpty());
        assertTrue(index.search("root", 0).isEmpty());
    }
}
