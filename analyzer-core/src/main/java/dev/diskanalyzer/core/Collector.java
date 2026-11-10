package dev.diskanalyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Set of nodes marked for cleanup. Never holds a node together with its ancestor or descendant. */
public final class Collector {
    private final List<FileNode> items = new ArrayList<>();

    public void add(FileNode node) {
        for (FileNode existing : items) {
            if (node.path().startsWith(existing.path())) return;
        }
        items.removeIf(existing -> existing.path().startsWith(node.path()));
        items.add(node);
    }

    public void remove(FileNode node) {
        items.remove(node);
    }

    public List<FileNode> items() {
        return List.copyOf(items);
    }

    public long reclaimableBytes() {
        long total = 0;
        for (FileNode n : items) total += n.size();
        return total;
    }

    public void clear() {
        items.clear();
    }
}
