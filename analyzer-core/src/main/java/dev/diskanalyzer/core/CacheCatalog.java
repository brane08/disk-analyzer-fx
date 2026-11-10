package dev.diskanalyzer.core;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Well-known developer cache folders under a home directory. */
public final class CacheCatalog {
    private final List<CacheLocation> candidates = new ArrayList<>();

    public CacheCatalog(Path home) {
        this(home, Os.detect());
    }

    public CacheCatalog(Path home, Os os) {
        switch (os) {
            case MAC -> {
                add(home, "Xcode DerivedData", "Library/Developer/Xcode/DerivedData");
                add(home, "Xcode Simulator Caches", "Library/Developer/CoreSimulator/Caches");
                add(home, "npm", ".npm");
                add(home, "Homebrew", "Library/Caches/Homebrew");
                add(home, "pip", "Library/Caches/pip");
            }
            case LINUX -> {
                add(home, "npm", ".npm");
                add(home, "pip", ".cache/pip");
                add(home, "Yarn", ".cache/yarn");
                add(home, "Thumbnails", ".cache/thumbnails");
            }
            case WINDOWS -> {
                add(home, "npm", "AppData/Local/npm-cache");
                add(home, "pip", "AppData/Local/pip/Cache");
                add(home, "NuGet", ".nuget/packages");
                add(home, "Temp", "AppData/Local/Temp");
            }
        }
        add(home, "Gradle", ".gradle/caches");
        add(home, "Maven", ".m2/repository");
        add(home, "Cargo", ".cargo/registry");
    }

    private void add(Path home, String label, String relative) {
        candidates.add(new CacheLocation(label, home.resolve(relative)));
    }

    public List<CacheLocation> existing() {
        List<CacheLocation> found = new ArrayList<>();
        for (CacheLocation c : candidates) {
            if (Files.isDirectory(c.path(), LinkOption.NOFOLLOW_LINKS)) found.add(c);
        }
        return List.copyOf(found);
    }
}
