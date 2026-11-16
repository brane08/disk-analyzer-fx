package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnreadableFoldersTest {

    private static FileNode dir(String name, FileNode parent) {
        return new FileNode(name, Path.of("/t").resolve(name), true, parent, 0);
    }

    @Test
    void listsOnlyUnreadableFoldersInTreeOrderAndHonoursTheLimit() {
        FileNode root = dir("root", null);
        FileNode a = dir("a", root);
        a.markUnreadable();
        FileNode b = dir("b", root);
        FileNode inner = dir("inner", b);
        inner.markUnreadable();
        b.addChild(inner);
        FileNode ok = dir("ok", root);
        root.addChild(a);
        root.addChild(b);
        root.addChild(ok);

        assertEquals(2, root.skippedDirs());
        assertEquals(List.of(a.path(), inner.path()), root.unreadableFolders(10));
        assertEquals(List.of(a.path()), root.unreadableFolders(1));
        assertTrue(ok.unreadableFolders(10).isEmpty());
    }

    @Test
    void anUnreadableFolderWithUnreadableDescendantsListsBoth() {
        FileNode root = dir("root", null);
        FileNode inner = dir("inner", root);
        inner.markUnreadable();
        root.addChild(inner);
        root.markUnreadable();

        assertEquals(List.of(root.path(), inner.path()), root.unreadableFolders(10));
    }

    @Test
    void copyKeepsTheUnreadableFlag() {
        FileNode root = dir("root", null);
        root.markUnreadable();

        FileNode copy = root.copy(null);

        assertTrue(copy.isUnreadable());
        assertEquals(1, copy.skippedDirs());
        assertFalse(dir("x", null).isUnreadable());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void scannerFlagsAFolderItCannotOpen(@TempDir Path tmp) throws IOException {
        Path locked = Files.createDirectories(tmp.resolve("locked"));
        Files.write(tmp.resolve("ok"), new byte[1]);
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(locked), "privileges ignore permission bits");

            FileNode root = new DiskScanner(tmp, new ScanListener() { }, ScanExclusions.none()).scan().orElseThrow();

            assertEquals(1, root.skippedDirs());
            assertEquals(List.of(locked), root.unreadableFolders(10));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void hintsDifferPerOsAndStateTheCount() {
        assertTrue(ScanHints.unreadableMessage(3, Os.MAC).startsWith("3 folder(s) could not be read"));
        assertTrue(ScanHints.unreadableMessage(3, Os.MAC).contains("Full Disk Access"));
        assertTrue(ScanHints.unreadableMessage(2, Os.LINUX).contains("permissions"));
        assertTrue(ScanHints.unreadableMessage(2, Os.WINDOWS).contains("administrator"));
    }
}
