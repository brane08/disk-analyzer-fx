package dev.diskanalyzer.core;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
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

    private static final Object TRASH_LOCK = new Object();
    private static Boolean trashAvailableCache;

    public Cleaner(ProtectedPaths protectedPaths, Trasher trasher) {
        this.protectedPaths = protectedPaths;
        this.trasher = trasher;
    }

    public Cleaner(ProtectedPaths protectedPaths) {
        this(protectedPaths, Cleaner::defaultTrash);
    }

    /** True when Desktop MOVE_TO_TRASH works or `gio` is on PATH. Cached after first probe. */
    public static boolean trashAvailable() {
        synchronized (TRASH_LOCK) {
            if (trashAvailableCache == null) {
                trashAvailableCache = desktopTrashSupported() || gioOnPath();
            }
            return trashAvailableCache;
        }
    }

    private static boolean desktopTrashSupported() {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
    }

    private static boolean gioOnPath() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return false;
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (Files.isExecutable(Path.of(dir, "gio"))) return true;
        }
        return false;
    }

    private static boolean defaultTrash(Path p) throws IOException {
        if (desktopTrashSupported()) {
            return Desktop.getDesktop().moveToTrash(p.toFile());
        }
        if (!gioOnPath()) return false;
        Process proc = new ProcessBuilder("gio", "trash", "--", p.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            int code = proc.waitFor();
            return code == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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
                    removed.add(p);
                    freed += n.size();
                } else {
                    long partialFreed = deleteRecursively(p, failed);
                    if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                        removed.add(p);
                        freed += n.size();
                    } else {
                        freed += partialFreed;
                        failed.putIfAbsent(p, "partial delete");
                    }
                }
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

    /** Deletes as much as possible. Records per-path failures; returns bytes from deleted files when root remains. */
    private static long deleteRecursively(Path root, Map<Path, String> failed) throws IOException {
        BasicFileAttributes rootAttrs = Files.readAttributes(root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!DiskScanner.isTraversableDirectory(rootAttrs)) {
            Files.delete(root);
            return rootAttrs.size();
        }
        long[] freed = {0};
        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(root) && !DiskScanner.isTraversableDirectory(attrs)) {
                    try {
                        Files.delete(dir);
                        freed[0] += attrs.size();
                    } catch (IOException e) {
                        failed.put(dir, String.valueOf(e.getMessage()));
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    Files.delete(file);
                    freed[0] += attrs.size();
                } catch (IOException e) {
                    failed.put(file, String.valueOf(e.getMessage()));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                failed.put(file, String.valueOf(exc.getMessage()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    failed.put(dir, String.valueOf(exc.getMessage()));
                }
                try {
                    Files.delete(dir);
                } catch (IOException e) {
                    failed.put(dir, String.valueOf(e.getMessage()));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return freed[0];
    }
}
