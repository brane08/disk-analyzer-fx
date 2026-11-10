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
 * but their contents (e.g. ~/Library/Caches/x) may be deleted — even when home lives under a
 * protected tree such as {@code /var/home/me}.
 */
public final class ProtectedPaths {
    private final List<Path> trees = new ArrayList<>();
    private final Set<Path> exact = new HashSet<>();
    private final Set<Path> homes = new HashSet<>();

    public ProtectedPaths(Path home) {
        this(home, Os.detect());
    }

    public ProtectedPaths(Path home, Os os) {
        Path h = home.toAbsolutePath().normalize();
        registerHome(h, os);
        try {
            Path real = h.toRealPath();
            if (!real.equals(h)) {
                registerHome(real, os);
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // home may not exist yet in tests; absolute+normalize still applies
        }
        for (Path root : FileSystems.getDefault().getRootDirectories()) {
            exact.add(root);
        }
        switch (os) {
            case MAC -> {
                addTrees("/System", "/usr", "/bin", "/sbin", "/etc", "/private/etc");
                addExact("/", "/Library", "/Applications", "/Volumes", "/private");
            }
            case LINUX -> {
                addTrees("/usr", "/bin", "/sbin", "/etc", "/boot", "/lib", "/lib32", "/lib64",
                        "/dev", "/proc", "/sys", "/var", "/opt");
                addExact("/", "/home", "/root", "/mnt", "/media", "/tmp", "/run", "/srv", "/snap", "/nix");
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
    }

    private void registerHome(Path home, Os os) {
        homes.add(home);
        protectExactAndAncestors(home);
        switch (os) {
            case MAC -> {
                exact.add(home.resolve("Library"));
                exact.add(home.resolve("Library").resolve("Application Support"));
                exact.add(home.resolve("Library").resolve("Keychains"));
                exact.add(home.resolve("Library").resolve("Mail"));
                exact.add(home.resolve("Library").resolve("Messages"));
                exact.add(home.resolve("Library").resolve("Containers"));
            }
            case LINUX -> {
                exact.add(home.resolve(".config"));
                exact.add(home.resolve(".local"));
            }
            case WINDOWS -> {
                exact.add(home.resolve("AppData"));
                exact.add(home.resolve("AppData").resolve("Local"));
                exact.add(home.resolve("AppData").resolve("Roaming"));
                exact.add(home.resolve("AppData").resolve("Local").resolve("Microsoft"));
                exact.add(home.resolve("AppData").resolve("Roaming").resolve("Microsoft"));
            }
        }
    }

    private void protectExactAndAncestors(Path path) {
        exact.add(path);
        for (Path a = path.getParent(); a != null; a = a.getParent()) {
            exact.add(a);
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
            if (!(n.startsWith(tree) || n.startsWith(realOf(tree)))) continue;
            if (insideRegisteredHome(n)) continue;
            return true;
        }
        return false;
    }

    /** True for paths strictly inside a registered home (home itself is exact-protected). */
    private boolean insideRegisteredHome(Path n) {
        for (Path home : homes) {
            if (n.startsWith(home) && !n.equals(home)) return true;
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
