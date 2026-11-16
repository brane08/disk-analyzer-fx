package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
class ProtectedPathsCaseTest {
    private static final Path HOME = Path.of("/Users/tester");

    @Test
    void macMatchesRegardlessOfCase() {
        ProtectedPaths pp = new ProtectedPaths(HOME, Os.MAC);

        assertTrue(pp.isProtected(Path.of("/system/Library")));
        assertTrue(pp.isProtected(Path.of("/USERS/TESTER/library")));
        assertTrue(pp.isProtected(HOME.resolve("Library/Keychains")));
        assertTrue(pp.isProtected(HOME.resolve("Library/Mail")));
    }

    @Test
    void macStillAllowsCachesInsideLibrary() {
        ProtectedPaths pp = new ProtectedPaths(HOME, Os.MAC);

        assertFalse(pp.isProtected(HOME.resolve("Library/Caches/Homebrew")));
    }

    @Test
    void linuxStaysCaseSensitive() {
        ProtectedPaths pp = new ProtectedPaths(Path.of("/home/tester"), Os.LINUX);

        assertTrue(pp.isProtected(Path.of("/usr/lib")));
        assertFalse(pp.isProtected(Path.of("/USR_data/file")));
    }
}
