package dev.diskanalyzer.core;

import java.util.ArrayList;
import java.util.List;

/** Set of nodes marked for cleanup. Never holds a node together with its ancestor or descendant. */
public final class Collector {
    private final List<FileNode> items = new ArrayList<>();

    public void add(FileNode node) {
        java.util.Objects.requireNonNull(node, "node");
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

    /** Re-points items at the nodes of a fresh scan (current sizes); items no longer present are dropped. */
    public void retarget(FileNode root) {
        List<FileNode> updated = new ArrayList<>();
        for (FileNode n : items) {
            if (!n.path().startsWith(root.path())) {
                updated.add(n); // collected from another scan (e.g. the Caches tab): nothing to compare against
                continue;
            }
            FileNode fresh = find(root, n.path());
            if (fresh != null) updated.add(fresh);
        }
        items.clear();
        items.addAll(updated);
    }

    /** Finds the node for {@code p} under {@code root}, or null. */
    public static FileNode find(FileNode root, java.nio.file.Path p) {
        if (root.path().equals(p)) return root;
        if (!p.startsWith(root.path())) return null;
        FileNode n = root;
        outer:
        while (true) {
            for (FileNode c : n.children()) {
                if (p.equals(c.path())) return c;
                if (p.startsWith(c.path())) {
                    n = c;
                    continue outer;
                }
            }
            return null;
        }
    }

    public void clear() {
        items.clear();
    }
}
