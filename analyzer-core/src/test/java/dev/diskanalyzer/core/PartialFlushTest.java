package dev.diskanalyzer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartialFlushTest {

    @Test
    void suppressedPartialIsFlushedWhileAStragglerIsStillScanning(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("a"));
        Files.write(tmp.resolve("a/f"), new byte[1]);
        Files.createDirectories(tmp.resolve("b"));
        Files.write(tmp.resolve("b/f"), new byte[1]);
        Files.createDirectories(tmp.resolve("slow"));
        for (int i = 0; i < 700; i++) {
            Files.write(tmp.resolve("slow/f" + i), new byte[1]);
        }

        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger stalls = new AtomicInteger();
        List<Integer> childCountsWhileScanning = Collections.synchronizedList(new ArrayList<>());

        FileNode root = new DiskScanner(tmp, new ScanListener() {
            @Override
            public void onProgress(ScanProgress p) {
                if (stalls.getAndIncrement() == 0) {
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            @Override
            public void onPartial(FileNode partial) {
                if (!done.get()) childCountsWhileScanning.add(partial.children().size());
            }
        }, ScanExclusions.none()).scan().orElseThrow();
        done.set(true);

        assertEquals(702, root.size());
        assertTrue(childCountsWhileScanning.contains(2),
                "expected a flushed partial holding the two finished folders: " + childCountsWhileScanning);
    }
}
