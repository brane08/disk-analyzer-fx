package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedPathsTest {
    private static final Path HOME = Path.of("/Users/tester");
    private final ProtectedPaths pp = new ProtectedPaths(HOME, Os.MAC);

    @Test
    void linuxSystemTreesAreProtected() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Path.of("/").toString().equals("/"));
        ProtectedPaths linux = new ProtectedPaths(Path.of("/home/tester"), Os.LINUX);
        for (String p : new String[] {"/", "/home", "/home/tester", "/usr/lib", "/var/lib/x", "/boot/grub",
                "/opt/app", "/lib/modules", "/etc/passwd", "/proc/1"}) {
            assertTrue(linux.isProtected(Path.of(p)), p);
        }
        for (String p : new String[] {"/home/tester/Downloads/x", "/home/tester/.cache/pip", "/tmp/anything"}) {
            assertFalse(linux.isProtected(Path.of(p)), p);
        }
    }

    @Test
    void windowsAndDetectedConfigurationsProtectHome() {
        Path home = Path.of("/Users/tester");
        for (ProtectedPaths w : new ProtectedPaths[] {new ProtectedPaths(home, Os.WINDOWS), new ProtectedPaths(home)}) {
            assertTrue(w.isProtected(home));
            assertFalse(w.isProtected(home.resolve("Downloads/x")));
        }
        assertTrue(new ProtectedPaths(home, Os.WINDOWS).isProtected(home.resolve("AppData")));
        assertTrue(new ProtectedPaths(home, Os.WINDOWS).isProtected(home.resolve("AppData/Local")));
    }

    @Test
    void osIsParsedFromPropertyName() {
        assertTrue(Os.parse("Windows 11") == Os.WINDOWS);
        assertTrue(Os.parse("Mac OS X") == Os.MAC);
        assertTrue(Os.parse("Linux") == Os.LINUX);
    }

    @Test
    void systemRootsAndTheirContentsAreProtected() {
        for (String p : new String[] {"/", "/System", "/System/Library", "/usr/bin", "/bin", "/sbin", "/etc"}) {
            assertTrue(pp.isProtected(Path.of(p)), p);
        }
    }

    @Test
    void homeLibraryAndAncestorsAreProtected() {
        for (String p : new String[] {"/Users", "/Users/tester", "/Users/tester/Library", "/Library", "/Applications"}) {
            assertTrue(pp.isProtected(Path.of(p)), p);
        }
    }

    @Test
    void pathsReachedThroughSymlinkIntoSystemTreeAreProtected(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(Path.of("/usr/bin")));
        Path link = java.nio.file.Files.createSymbolicLink(tmp.resolve("link"), Path.of("/usr"));
        assertTrue(pp.isProtected(link.resolve("bin")));
        assertTrue(pp.isProtected(tmp.resolve("link").resolve("..").resolve("link").resolve("bin")));
    }

    @Test
    void realLocationOfSystemTreeIsProtected() {
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(Path.of("/private/etc")));
        assertTrue(pp.isProtected(Path.of("/private/etc/hosts")));
    }

    @Test
    void userContentIsNotProtected() {
        for (String p : new String[] {"/Users/tester/Downloads/x", "/Users/tester/Library/Caches/Homebrew",
                "/Users/tester/.npm", "/Applications/Foo.app", "/tmp/anything"}) {
            assertFalse(pp.isProtected(Path.of(p)), p);
        }
    }
}
