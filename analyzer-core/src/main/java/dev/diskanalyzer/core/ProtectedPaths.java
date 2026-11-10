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
    private final List<Path> trees = new ArrayList<>();
    private final Set<Path> exact = new HashSet<>();

    public ProtectedPaths(Path home) {
        this(home, Os.detect());
    }

    public ProtectedPaths(Path home, Os os) {
        Path h = home.toAbsolutePath().normalize();
        exact.add(h);
        for (Path a = h.getParent(); a != null; a = a.getParent()) {
            exact.add(a);
        }
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            exact.add(root);
        }
        switch (os) {
            case MAC -> {
                addTrees("/System", "/usr", "/bin", "/sbin", "/etc", "/private/etc");
                addExact("/", "/Library", "/Applications");
                exact.add(h.resolve("Library"));
            }
            case LINUX -> {
                addTrees("/usr", "/bin", "/sbin", "/etc", "/boot", "/lib", "/lib32", "/lib64",
                        "/dev", "/proc", "/sys", "/run", "/var", "/opt");
                addExact("/", "/home", "/root", "/mnt", "/media", "/tmp");
                exact.add(h.resolve(".config"));
                exact.add(h.resolve(".local"));
            }
            case WINDOWS -> {
                addEnvTree("SystemRoot", "C:\\Windows");
                addEnvTree("ProgramFiles", "C:\\Program Files");
                addEnvTree("ProgramFiles(x86)", "C:\\Program Files (x86)");
                addEnvTree("ProgramData", "C:\\ProgramData");
                String drive = System.getenv("SystemDrive") == null ? "C:" : System.getenv("SystemDrive");
                addTrees(drive + "\\Recovery", drive + "\\System Volume Information", drive + "\\$Recycle.Bin");
                exact.add(h.resolve("AppData"));
                exact.add(h.resolve("AppData").resolve("Local"));
                exact.add(h.resolve("AppData").resolve("Roaming"));
            }
        }
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

    private boolean check(Path n) {
        if (exact.contains(n)) return true;
        for (Path tree : trees) {
            if (n.startsWith(tree) || n.startsWith(realOf(tree))) return true;
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
