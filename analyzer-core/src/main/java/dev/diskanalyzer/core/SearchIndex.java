package dev.diskanalyzer.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Case-insensitive literal substring search over file names of a scanned tree. */
public final class SearchIndex {
    private final List<FileNode> nodes = new ArrayList<>();
    private final List<String> lowerNames = new ArrayList<>();

    private SearchIndex() { }

    public static SearchIndex of(FileNode root) {
        SearchIndex index = new SearchIndex();
        index.collect(root);
        return index;
    }

    private void collect(FileNode node) {
        nodes.add(node);
        lowerNames.add(node.name().toLowerCase(Locale.ROOT));
        for (FileNode c : node.children()) {
            collect(c);
        }
    }

    public List<FileNode> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.toLowerCase(Locale.ROOT);
        List<FileNode> hits = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (lowerNames.get(i).contains(q)) hits.add(nodes.get(i));
        }
        hits.sort(Comparator.comparingLong(FileNode::size).reversed());
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : List.copyOf(hits);
    }
}
