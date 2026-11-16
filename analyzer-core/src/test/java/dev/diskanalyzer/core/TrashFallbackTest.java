package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrashFallbackTest {

    private record FakeAttrs(boolean symlink, boolean other) implements BasicFileAttributes {
        public FileTime lastModifiedTime() { return FileTime.fromMillis(0); }
        public FileTime lastAccessTime() { return FileTime.fromMillis(0); }
        public FileTime creationTime() { return FileTime.fromMillis(0); }
        public boolean isRegularFile() { return false; }
        public boolean isDirectory() { return true; }
        public boolean isSymbolicLink() { return symlink; }
        public boolean isOther() { return other; }
        public long size() { return 0; }
        public Object fileKey() { return null; }
    }

    @Test
    void dataHomeHonoursXdgOtherwiseDefaultsUnderHome() {
        assertEquals(Path.of("/x/data"), Cleaner.dataHome("/x/data", "/home/u"));
        assertEquals(Path.of("/home/u", ".local", "share"), Cleaner.dataHome(null, "/home/u"));
        assertEquals(Path.of("/home/u", ".local", "share"), Cleaner.dataHome("  ", "/home/u"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void onlyLinuxHasAFileBasedFallback(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("a"), new byte[1]);
        Files.write(tmp.resolve("b"), new byte[1]);

        assertFalse(Cleaner.fallbackTrash(tmp.resolve("a"), Os.MAC, tmp.resolve("data")));
        assertFalse(Cleaner.fallbackTrash(tmp.resolve("a"), Os.WINDOWS, tmp.resolve("data")));
        assertTrue(Files.exists(tmp.resolve("a")));

        assertTrue(Cleaner.fallbackTrash(tmp.resolve("b"), Os.LINUX, tmp.resolve("data")));
        assertFalse(Files.exists(tmp.resolve("b")));
    }

    @Test
    void trashInfoDatesAlwaysCarrySeconds(@TempDir Path tmp) throws IOException {
        Files.write(tmp.resolve("f"), new byte[1]);

        Cleaner.xdgTrash(tmp.resolve("f"), tmp.resolve("data"));

        String info = Files.readString(tmp.resolve("data/Trash/info/f.trashinfo"));
        assertTrue(info.matches("(?s).*DeletionDate=\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\n"), info);
    }

    @Test
    void symlinkAttributesAreAlwaysLinks(@TempDir Path tmp) {
        assertTrue(Cleaner.isLinkLike(tmp, new FakeAttrs(true, false)));
        assertFalse(Cleaner.isLinkLike(tmp, new FakeAttrs(false, false)));
    }

    @Test
    void otherReparsePointsThatResolveElsewhereOrFailAreLinks(@TempDir Path tmp) {
        assertTrue(Cleaner.isLinkLike(tmp.resolve("missing"), new FakeAttrs(false, true)));
    }

    @Test
    void otherEntriesThatResolveToThemselvesAreOrdinaryFolders(@TempDir Path tmp) throws IOException {
        Path real = tmp.toRealPath();

        assertFalse(Cleaner.isLinkLike(real, new FakeAttrs(false, true)));
    }
}
