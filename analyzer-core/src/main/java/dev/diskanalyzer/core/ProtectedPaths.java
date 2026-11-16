package dev.diskanalyzer.core;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Paths that must never be deleted. System trees are protected with everything inside them;
 * the home folder, well-known per-OS folders and all ancestors of home are protected themselves
 * but their contents (e.g. ~/Library/Caches/x) may be deleted.
 */
public final class ProtectedPaths {
    private List<Path> trees = new ArrayList<>();
    private Set<Path> exact = new HashSet<>();
    private final boolean caseInsensitive;

    public ProtectedPaths(Path home) {
        this(home, Os.detect());
    }

    public ProtectedPaths(Path home, Os os) {
        caseInsensitive = os != Os.LINUX;
        Path h = home.toAbsolutePath().normalize();
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            exact.add(root);
        }
        addHome(h, os);
        Path realHome = realOf(h);
        if (!realHome.equals(h)) addHome(realHome, os); // scans resolve symlinked roots, so protect both spellings
        switch (os) {
            case MAC -> {
                addTrees("/System", "/usr", "/bin", "/sbin", "/etc", "/private/etc", "/private/var", "/var");
                addExact("/", "/Library", "/Applications", "/private", "/Users", "/Volumes");
                exact.addAll(childrenOf(Path.of("/Volumes")));
            }
            case LINUX -> {
                addTrees("/usr", "/bin", "/sbin", "/etc", "/boot", "/lib", "/lib32", "/lib64",
                        "/dev", "/proc", "/sys", "/run", "/var", "/opt");
                addExact("/", "/home", "/root", "/mnt", "/media", "/tmp", "/srv", "/snap", "/nix");
                exact.addAll(childrenOf(Path.of("/mnt")));
                for (Path media : childrenOf(Path.of("/media"))) {
                    exact.add(media);
                    exact.addAll(childrenOf(media)); // udisks mounts at /media/<user>/<label>
                }
            }
            case WINDOWS -> {
                addEnvTree("SystemRoot", "C:\\Windows");
                addEnvTree("ProgramFiles", "C:\\Program Files");
                addEnvTree("ProgramFiles(x86)", "C:\\Program Files (x86)");
                addEnvTree("ProgramData", "C:\\ProgramData");
                String drive = System.getenv("SystemDrive") == null ? "C:" : System.getenv("SystemDrive");
                addTrees(drive + "\\Recovery", drive + "\\System Volume Information", drive + "\\$Recycle.Bin");
            }
        }
        if (caseInsensitive) {
            Set<Path> foldedExact = new HashSet<>();
            for (Path p : exact) foldedExact.add(fold(p));
            exact = foldedExact;
            List<Path> foldedTrees = new ArrayList<>();
            for (Path p : trees) foldedTrees.add(fold(p));
            trees = foldedTrees;
        }
    }

    /** Home folder, its ancestors, credentials folders and well-known per-OS app-data folders. */
    private void addHome(Path h, Os os) {
        exact.add(h);
        for (Path a = h.getParent(); a != null; a = a.getParent()) {
            exact.add(a);
        }
        trees.add(h.resolve(".ssh"));
        trees.add(h.resolve(".gnupg"));
        switch (os) {
            case MAC -> {
                exact.add(h.resolve("Library"));
                for (String sub : List.of("Application Support", "Preferences", "Containers",
                        "Group Containers", "Mobile Documents", "Keychains", "Mail")) {
                    exact.add(h.resolve("Library").resolve(sub));
                }
            }
            case LINUX -> {
                exact.add(h.resolve(".config"));
                exact.add(h.resolve(".local"));
                exact.add(h.resolve(".local").resolve("share"));
                exact.add(h.resolve(".local").resolve("state"));
            }
            case WINDOWS -> {
                exact.add(h.resolve("AppData"));
                exact.add(h.resolve("AppData").resolve("Local"));
                exact.add(h.resolve("AppData").resolve("Roaming"));
                exact.add(h.resolve("AppData").resolve("Local").resolve("Microsoft"));
                exact.add(h.resolve("AppData").resolve("LocalLow"));
            }
        }
    }

    private Path fold(Path p) {
        return caseInsensitive ? Path.of(p.toString().toLowerCase(java.util.Locale.ROOT)) : p;
    }

    /** Immediate children of {@code dir} (mount points, per-user media folders); empty when unreadable. */
    static Set<Path> childrenOf(Path dir) {
        Set<Path> children = new HashSet<>();
        try (java.nio.file.DirectoryStream<Path> stream = java.nio.file.Files.newDirectoryStream(dir)) {
            for (Path c : stream) children.add(c);
        } catch (java.io.IOException | RuntimeException e) {
            // missing or unreadable: nothing to protect
        }
        return children;
    }

    private void addTrees(String... paths) {
        for (String p : paths) trees.add(Path.of(p));
    }

    private void addExact(String... paths) {
        for (String p : paths) exact.add(Path.of(p));
    }

    private void addEnvTree(String var, String fallback) {
        String v = System.getenv(var);
        trees.add(Path.of(v == null || v.isBlank() ? fallback : v));
    }

    public boolean isProtected(Path p) {
        Path n = p.toAbsolutePath().normalize();
        return check(n) || check(resolveParent(n));
    }

    private boolean check(Path raw) {
        Path n = fold(raw);
        if (exact.contains(n)) return true;
        for (Path tree : trees) {
            if (n.startsWith(tree) || n.startsWith(fold(realOf(tree)))) return true;
        }
        return false;
    }

    /** Real path of the parent folder (symlinks resolved) plus the entry's own name; the entry itself is not followed. */
    private static Path resolveParent(Path n) {
        Path parent = n.getParent();
        if (parent == null) return n;
        return realOf(parent).resolve(n.getFileName());
    }

    private static Path realOf(Path p) {
        try {
            return p.toRealPath();
        } catch (java.io.IOException | RuntimeException e) {
            return p;
        }
    }
}
