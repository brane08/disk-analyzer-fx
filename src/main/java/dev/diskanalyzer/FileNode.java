package dev.diskanalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FileNode {
    private final String name;
    private final Path path;
    private final boolean dir;
    private final FileNode parent;
    private final List<FileNode> children = new ArrayList<>();
    private long size;
    private long fileCount;

    FileNode(String name, Path path, boolean dir, FileNode parent, long size) {
        this.name = name;
        this.path = path;
        this.dir = dir;
        this.parent = parent;
        this.size = size;
        this.fileCount = dir ? 0 : 1;
    }

    void addChild(FileNode child) {
        children.add(child);
        size += child.size;
        fileCount += child.fileCount;
    }

    void sortChildren() {
        children.sort(Comparator.comparingLong(FileNode::size).reversed());
    }

    public String name() { return name; }
    public Path path() { return path; }
    public boolean isDir() { return dir; }
    public FileNode parent() { return parent; }
    public List<FileNode> children() { return children; }
    public long size() { return size; }
    public long fileCount() { return fileCount; }

    public String extension() {
        int i = name.lastIndexOf('.');
        return (i > 0 && i < name.length() - 1) ? name.substring(i + 1).toLowerCase() : "";
    }

    @Override
    public String toString() { return name; }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double v = bytes;
        int u = -1;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        return String.format("%.1f %s", v, units[u]);
    }
}
