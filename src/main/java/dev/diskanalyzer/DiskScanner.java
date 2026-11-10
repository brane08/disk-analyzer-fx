package dev.diskanalyzer;

import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

public final class DiskScanner extends Task<FileNode> {
    private final Path root;
    private long visited;

    public DiskScanner(Path root) {
        this.root = root;
    }

    @Override
    protected FileNode call() {
        FileNode result = scan(root, null);
        if (isCancelled() || result == null) {
            return null;
        }
        return result;
    }

    private FileNode scan(Path p, FileNode parent) {
        if (isCancelled()) return null;
        BasicFileAttributes attrs;
        try {
            attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException e) {
            return null;
        }
        Path fileName = p.getFileName();
        String name = fileName == null ? p.toString() : fileName.toString();

        if (++visited % 500 == 0) {
            updateMessage("Scanned " + visited + " items — " + p);
        }

        if (!attrs.isDirectory()) {
            return new FileNode(name, p, false, parent, attrs.size());
        }

        FileNode node = new FileNode(name, p, true, parent, 0);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
            for (Path child : stream) {
                if (isCancelled()) return null;
                FileNode c = scan(child, node);
                if (c != null) node.addChild(c);
            }
        } catch (IOException | SecurityException e) {
            // unreadable directory: keep what we have
        }
        node.sortChildren();
        return node;
    }
}
