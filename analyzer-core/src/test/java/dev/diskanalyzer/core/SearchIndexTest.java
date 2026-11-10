package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTest {

    private static FileNode tree() {
        FileNode root = new FileNode("root", Path.of("/root"), true, null, 0);
        FileNode docs = new FileNode("Docs", Path.of("/root/Docs"), true, root, 0);
        docs.addChild(new FileNode("Report.PDF", Path.of("/root/Docs/Report.PDF"), false, docs, 500));
        docs.addChild(new FileNode("résumé.txt", Path.of("/root/Docs/résumé.txt"), false, docs, 5));
        docs.addChild(new FileNode("abc", Path.of("/root/Docs/abc"), false, docs, 9));
        root.addChild(docs);
        root.addChild(new FileNode("report-final.md", Path.of("/root/report-final.md"), false, root, 50));
        return root;
    }

    private static List<String> names(List<FileNode> nodes) {
        return nodes.stream().map(FileNode::name).toList();
    }

    @Test
    void matchesCaseInsensitively() {
        List<String> found = names(SearchIndex.of(tree()).search("REPORT", 10));
        assertTrue(found.contains("Report.PDF"));
        assertTrue(found.contains("report-final.md"));
    }

    @Test
    void ordersBySizeDescending() {
        assertEquals(List.of("Report.PDF", "report-final.md"),
                names(SearchIndex.of(tree()).search("report", 10)));
    }

    @Test
    void respectsLimit() {
        assertEquals(1, SearchIndex.of(tree()).search("report", 1).size());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        assertTrue(SearchIndex.of(tree()).search("", 10).isEmpty());
        assertTrue(SearchIndex.of(tree()).search("   ", 10).isEmpty());
    }

    @Test
    void treatsRegexCharsLiterally() {
        assertTrue(SearchIndex.of(tree()).search("a.c", 10).isEmpty());
        assertEquals(List.of("abc"), names(SearchIndex.of(tree()).search("abc", 10)));
    }

    @Test
    void matchesUnicode() {
        assertEquals(List.of("résumé.txt"), names(SearchIndex.of(tree()).search("É", 10)));
    }
}
