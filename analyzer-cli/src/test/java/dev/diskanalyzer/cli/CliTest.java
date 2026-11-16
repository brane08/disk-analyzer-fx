package dev.diskanalyzer.cli;

import dev.diskanalyzer.core.DiskScanner;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.ScanListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTest {

    private record Result(int code, String out, String err) { }

    private static Result run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine cl = new CommandLine(new Main());
        cl.setOut(new PrintWriter(out, true));
        cl.setErr(new PrintWriter(err, true));
        int code = cl.execute(args);
        return new Result(code, out.toString(), err.toString());
    }

    private static void write(Path file, int bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[bytes]);
    }

    @Test
    void scanPrintsTreeWithSizesLargestFirst(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("small/f"), 10);
        write(tmp.resolve("big/f"), 20);

        Result r = run("scan", tmp.toString(), "--no-color");

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("20 B"), r.out());
        assertTrue(r.out().indexOf("big/") < r.out().indexOf("small/"), r.out());
        assertTrue(r.out().contains("█"), "expected a size bar: " + r.out());
    }

    @Test
    void scanRespectsDepth(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/deepfile"), 10);

        assertFalse(run("scan", tmp.toString(), "--depth", "1", "--no-color").out().contains("deepfile"));
        assertTrue(run("scan", tmp.toString(), "--depth", "2", "--no-color").out().contains("deepfile"));
    }

    @Test
    void topListsLargestFilesFirst(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("one"), 1);
        write(tmp.resolve("d/three"), 3);
        write(tmp.resolve("two"), 2);

        Result r = run("top", tmp.toString(), "-n", "2", "--no-color");

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().indexOf("three") < r.out().indexOf("two"), r.out());
        assertFalse(r.out().contains("one"), r.out());
    }

    @Test
    void topFilesFlagMatchesDefaultAndRejectsBadCombos(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("d/three"), 3);

        Result files = run("top", tmp.toString(), "--files", "--no-color");
        Result both = run("top", tmp.toString(), "--files", "--dirs", "--no-color");
        Result negative = run("top", tmp.toString(), "-n", "-1", "--no-color");

        assertEquals(0, files.code(), files.err());
        assertTrue(files.out().contains("three"), files.out());
        assertEquals(2, both.code());
        assertEquals(2, negative.code());
    }

    @Test
    void searchFindsByName(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("docs/Report.pdf"), 100);
        write(tmp.resolve("other.txt"), 1);

        Result r = run("search", tmp.toString(), "report", "--no-color");

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Report.pdf"));
        assertFalse(r.out().contains("other.txt"));
    }

    @Test
    void cachesListsExistingLocationsOfGivenHome(@TempDir Path home) throws IOException {
        write(home.resolve(".m2/repository/pkg"), 50); // same location on every OS

        Result r = run("caches", "--home", home.toString(), "--no-color");

        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Maven"), r.out());
        assertTrue(r.out().contains("50 B"), r.out());
    }

    @Test
    void cachesWithMissingHomeExitsTwo(@TempDir Path tmp) {
        Result r = run("caches", "--home", tmp.resolve("nope").toString(), "--no-color");

        assertEquals(2, r.code());
        assertTrue(r.err().contains("not found"), r.err());
    }

    @Test
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    void scanWarnsWhenFoldersCannotBeRead(@TempDir Path tmp) throws IOException {
        Path locked = tmp.resolve("locked");
        Files.createDirectories(locked);
        write(tmp.resolve("ok"), 4);
        Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("---------"));
        try {
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(locked), "privileges ignore permission bits");

            Result r = run("scan", tmp.toString(), "--no-color");

            assertEquals(0, r.code(), r.err());
            assertTrue(r.err().contains("1 folder(s) could not be read"), r.err());
        } finally {
            Files.setPosixFilePermissions(locked, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void badPathExitsTwo(@TempDir Path tmp) {
        Result r = run("scan", tmp.resolve("nope").toString(), "--no-color");

        assertEquals(2, r.code());
        assertTrue(r.err().contains("nope"));
    }

    @Test
    void noColorHasNoEscapeCodes(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f"), 10);

        assertFalse(run("scan", tmp.toString(), "--no-color").out().contains("\u001b"));
    }

    @Test
    void colorEnabledEmitsEscapeCodes(@TempDir Path tmp) throws IOException {
        write(tmp.resolve("a/f"), 10);
        FileNode root = new DiskScanner(tmp, new ScanListener() { }).scan().orElseThrow();

        assertTrue(Render.tree(root, 2, 10, Ansi.ON).contains("\u001b["));
    }
}
