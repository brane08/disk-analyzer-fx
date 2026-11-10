package dev.diskanalyzer.core;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deletes collected nodes, refusing protected paths. Symlinks are removed, never followed. */
public final class Cleaner {

    @FunctionalInterface
    public interface Trasher {
        boolean moveToTrash(Path p) throws IOException;
    }

    private final ProtectedPaths protectedPaths;
    private final Trasher trasher;

    public Cleaner(ProtectedPaths protectedPaths, Trasher trasher) {
        this.protectedPaths = protectedPaths;
        this.trasher = trasher;
    }

    public Cleaner(ProtectedPaths protectedPaths) {
        this(protectedPaths, Cleaner::desktopTrash);
    }

    private static boolean desktopTrash(Path p) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return false;
        }
        return Desktop.getDesktop().moveToTrash(p.toFile());
    }

    public CleanResult clean(java.util.Collection<FileNode> items, Mode mode) {
        List<Path> removed = new ArrayList<>();
        Map<Path, String> failed = new LinkedHashMap<>();
        long freed = 0;

        List<FileNode> candidates = new ArrayList<>();
        for (FileNode n : items) {
            if (protectedPaths.isProtected(n.path())) {
                failed.put(n.path(), "protected path");
            } else {
                candidates.add(n);
            }
        }

        for (FileNode n : candidates) {
            if (hasAncestorIn(n, candidates)) continue;
            Path p = n.path();
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                failed.put(p, "not found");
                continue;
            }
            try {
                if (mode == Mode.TRASH) {
                    if (!trasher.moveToTrash(p)) {
                        failed.put(p, "trash unavailable");
                        continue;
                    }
                } else {
                    deleteRecursively(p);
                }
                removed.add(p);
                freed += n.size();
            } catch (IOException | RuntimeException e) {
                failed.put(p, String.valueOf(e.getMessage()));
            }
        }
        return new CleanResult(List.copyOf(removed), failed, freed);
    }

    private static boolean hasAncestorIn(FileNode n, List<FileNode> all) {
        for (FileNode other : all) {
            if (other != n && n.path().startsWith(other.path()) && !n.path().equals(other.path())) return true;
        }
        return false;
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
