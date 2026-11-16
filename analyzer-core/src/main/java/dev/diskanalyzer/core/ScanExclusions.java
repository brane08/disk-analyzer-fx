package dev.diskanalyzer.core;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Folders that are listed but never descended into during a scan: pseudo filesystems and macOS firmlink mirrors. */
final class ScanExclusions {
    private final Set<Path> paths = new HashSet<>();

    ScanExclusions(Path... excluded) {
        for (Path p : excluded) paths.add(p.toAbsolutePath().normalize());
    }

    static ScanExclusions none() {
        return new ScanExclusions();
    }

    static ScanExclusions forOs(Os os) {
        return switch (os) {
            case LINUX -> new ScanExclusions(Path.of("/proc"), Path.of("/sys"), Path.of("/dev"), Path.of("/run"));
            case MAC -> new ScanExclusions(Path.of("/dev"), Path.of("/System/Volumes"));
            case WINDOWS -> none();
        };
    }

    boolean contains(Path p) {
        return paths.contains(p.toAbsolutePath().normalize());
    }
}
