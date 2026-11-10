package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
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

    @Test
    void linuxHomeUnderVarIsNotSwallowedByVarTree() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Path.of("/").toString().equals("/"));
        ProtectedPaths linux = new ProtectedPaths(Path.of("/var/home/me"), Os.LINUX);
        assertTrue(linux.isProtected(Path.of("/var/home/me")));
        assertTrue(linux.isProtected(Path.of("/var/home/me/.config")));
        assertTrue(linux.isProtected(Path.of("/var/lib/x")));
        assertFalse(linux.isProtected(Path.of("/var/home/me/Downloads/x")));
        assertFalse(linux.isProtected(Path.of("/var/home/me/.cache/pip")));
    }

    @Test
    void realHomePathIsProtected(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path realHome = tmp.resolve("realHome");
        Files.createDirectories(realHome);
        Path linkHome = Files.createSymbolicLink(tmp.resolve("linkHome"), realHome);

        ProtectedPaths linked = new ProtectedPaths(linkHome, Os.LINUX);

        assertTrue(linked.isProtected(realHome));
        assertTrue(linked.isProtected(linkHome));
        assertFalse(linked.isProtected(realHome.resolve("Downloads/x")));
    }

    @Test
    void linuxRunMediaContentIsNotProtected() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Path.of("/").toString().equals("/"));
        ProtectedPaths linux = new ProtectedPaths(Path.of("/home/tester"), Os.LINUX);
        assertTrue(linux.isProtected(Path.of("/run")));
        assertFalse(linux.isProtected(Path.of("/run/media/user/disk/file")));
    }

    @Test
    void macSensitiveLibraryFoldersAreProtected() {
        for (String p : new String[] {
                "/Users/tester/Library/Application Support",
                "/Users/tester/Library/Keychains",
                "/Users/tester/Library/Mail",
                "/Users/tester/Library/Messages",
                "/Users/tester/Library/Containers",
                "/Volumes",
                "/private"}) {
            assertTrue(pp.isProtected(Path.of(p)), p);
        }
        assertFalse(pp.isProtected(Path.of("/private/tmp/scratch")));
        assertFalse(pp.isProtected(Path.of("/Volumes/USB/photo.jpg")));
    }

    @Test
    void linuxSrvSnapNixAreProtected() {
        org.junit.jupiter.api.Assumptions.assumeTrue(Path.of("/").toString().equals("/"));
        ProtectedPaths linux = new ProtectedPaths(Path.of("/home/tester"), Os.LINUX);
        for (String p : new String[] {"/srv", "/snap", "/nix"}) {
            assertTrue(linux.isProtected(Path.of(p)), p);
        }
    }

    @Test
    void windowsMicrosoftAppDataIsProtected() {
        Path home = Path.of("/Users/tester");
        ProtectedPaths win = new ProtectedPaths(home, Os.WINDOWS);
        assertTrue(win.isProtected(home.resolve("AppData/Local/Microsoft")));
        assertTrue(win.isProtected(home.resolve("AppData/Roaming/Microsoft")));
        assertFalse(win.isProtected(home.resolve("Downloads/x")));
    }
}
