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

    private static boolean desktopTrash(Path p) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return Desktop.getDesktop().moveToTrash(p.toFile());
        }
        return fallbackTrash(p, Os.detect(),
                dataHome(System.getenv("XDG_DATA_HOME"), System.getProperty("user.home")));
    }

    static Path dataHome(String xdgDataHome, String userHome) {
        return xdgDataHome == null || xdgDataHome.isBlank()
                ? Path.of(userHome, ".local", "share")
                : Path.of(xdgDataHome);
    }

    /** Used when the desktop has no Trash support: only Linux has a file-based fallback. */
    static boolean fallbackTrash(Path p, Os os, Path dataHome) throws IOException {
        return os == Os.LINUX && xdgTrash(p, dataHome);
    }

    /** Freedesktop.org trash: moves {@code p} into {@code dataHome/Trash/files} with a .trashinfo record. */
    static boolean xdgTrash(Path p, Path dataHome) throws IOException {
        Path files = Files.createDirectories(dataHome.resolve("Trash").resolve("files"));
        Path info = Files.createDirectories(dataHome.resolve("Trash").resolve("info"));
        Path abs = p.toAbsolutePath().normalize();
        String base = abs.getFileName().toString();
        String name = base;
        Path infoFile = null;
        for (int i = 1; infoFile == null; i++) {
            if (!Files.exists(files.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                try {
                    infoFile = Files.createFile(info.resolve(name + ".trashinfo"));
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    // taken: try the next suffix
                }
            }
            if (infoFile == null) name = base + "." + i;
        }
        String rawPath = abs.toUri().getRawPath();
        if (rawPath.length() > 1 && rawPath.endsWith("/")) rawPath = rawPath.substring(0, rawPath.length() - 1);
        try {
            Path parentDir = abs.getParent() == null ? abs : abs.getParent();
            if (!Files.getFileStore(parentDir).equals(Files.getFileStore(files))) {
                throw new IOException("different filesystem than the Trash; use permanent delete");
            }
            Files.writeString(infoFile, "[Trash Info]\nPath=" + rawPath + "\nDeletionDate="
                    + java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "\n");
            Files.move(abs, files.resolve(name));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(infoFile);
            throw e;
        }
        return true;
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

        List<FileNode> covered = new ArrayList<>();
        for (FileNode n : candidates) {
            if (hasAncestorIn(n, candidates)) {
                covered.add(n);
                continue;
            }
            Path p = n.path();
            if (!Files.exists(p, LinkOption.NOFOLLOW_LINKS)) {
                failed.put(p, "not found");
                continue;
            }
            if (isMountPoint(p)) {
                failed.put(p, "mount point");
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
        List<Path> failedParents = new ArrayList<>(failed.keySet());
        for (FileNode n : covered) {
            if (failedParents.stream().anyMatch(f -> n.path().startsWith(f))) {
                failed.put(n.path(), "not removed: containing item failed");
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

    /** Symlinks and Windows junctions/reparse points: removed as a single entry, never descended into. */
    static boolean isLinkLike(Path path, BasicFileAttributes attrs) {
        if (attrs.isSymbolicLink()) return true;
        if (!attrs.isOther()) return false;
        // Other reparse points (junctions, mount points) resolve elsewhere; cloud placeholders
        // (OneDrive etc.) resolve to themselves and are ordinary folders.
        try {
            return !path.toRealPath().equals(path.toAbsolutePath().normalize());
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private static java.nio.file.FileStore storeOf(Path p) {
        try {
            return Files.getFileStore(p);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** True when {@code p} (not a symlink) lives on a different filesystem than its parent, i.e. it is a mount point. */
    static boolean isMountPoint(Path p) {
        if (Files.isSymbolicLink(p)) return false;
        Path parent = p.toAbsolutePath().getParent();
        if (parent == null) return false;
        java.nio.file.FileStore own = storeOf(p);
        java.nio.file.FileStore up = storeOf(parent);
        return own != null && up != null && !own.equals(up);
    }

    /** Deletes as much of the tree as possible; throws the first failure after the walk completes. */
    private static void deleteRecursively(Path root) throws IOException {
        IOException[] first = new IOException[1];
        java.nio.file.FileStore rootStore = storeOf(root);
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // A broken or access-denied junction/symlink can't be opened as a directory: remove it as a link.
                try {
                    BasicFileAttributes a = Files.readAttributes(file, BasicFileAttributes.class,
                            LinkOption.NOFOLLOW_LINKS);
                    if (isLinkLike(file, a)) {
                        tryDelete(file);
                        return FileVisitResult.CONTINUE;
                    }
                } catch (IOException | RuntimeException ignored) {
                    // fall through and report the original failure
                }
                if (first[0] == null) first[0] = exc;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isLinkLike(dir, attrs)) {
                    tryDelete(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (rootStore != null && !dir.equals(root) && !rootStore.equals(storeOf(dir))) {
                    // never delete across a mount boundary (bind mounts, nested volumes)
                    if (first[0] == null) first[0] = new IOException("not crossing into another filesystem: " + dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                tryDelete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null && first[0] == null) first[0] = exc;
                tryDelete(dir);
                return FileVisitResult.CONTINUE;
            }

            private void tryDelete(Path p) {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    if (first[0] == null) first[0] = e;
                }
            }
        });
        if (first[0] != null) throw first[0];
    }
}
