package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.DiskScanner;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.ScanListener;
import dev.diskanalyzer.core.ScanProgress;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Runs a {@link DiskScanner} off the FX thread and publishes throttled results on it. */
public final class ScanController {
    private static final double THROTTLE_MS = 250;

    /** A partial tree tagged with the scan (generation) that produced it. */
    private record Partial(long generation, FileNode root) { }

    private final ObjectProperty<FileNode> root = new SimpleObjectProperty<>();
    private final ObjectProperty<ScanProgress> progress = new SimpleObjectProperty<>();
    private final ObjectProperty<String> error = new SimpleObjectProperty<>();
    private final BooleanProperty running = new SimpleBooleanProperty();
    private final AtomicReference<Partial> pendingPartial = new AtomicReference<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private long generation;
    private DiskScanner active;

    public ReadOnlyObjectProperty<FileNode> rootProperty() { return root; }

    public ReadOnlyObjectProperty<ScanProgress> progressProperty() { return progress; }

    public ReadOnlyObjectProperty<String> errorProperty() { return error; }

    public ReadOnlyBooleanProperty runningProperty() { return running; }

    /** Must be called on the FX thread. */
    public void start(Path path) {
        cancel();
        error.set(null);
        progress.set(null);
        root.set(null);
        pendingPartial.set(null);

        long gen = ++generation;
        DiskScanner scanner = new DiskScanner(path, new ScanListener() {
            @Override
            public void onProgress(ScanProgress p) {
                Platform.runLater(() -> {
                    if (gen == generation && running.get()) progress.set(p);
                });
            }

            @Override
            public void onPartial(FileNode partialRoot) {
                pendingPartial.set(new Partial(gen, partialRoot));
                scheduleFlush();
            }
        });
        active = scanner;
        running.set(true);

        Thread t = new Thread(() -> {
            Optional<FileNode> result = Optional.empty();
            String failure = null;
            try {
                result = scanner.scan();
            } catch (Throwable e) {
                failure = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            Optional<FileNode> done = result;
            String failed = failure;
            Platform.runLater(() -> finish(scanner, done, failed));
        }, "disk-scanner");
        t.setDaemon(true);
        t.start();
    }

    /** Must be called on the FX thread. */
    public void cancel() {
        if (active != null) {
            active.cancel();
            active = null;
            generation++;
            running.set(false);
        }
    }

    private void finish(DiskScanner scanner, Optional<FileNode> result, String failure) {
        if (active != scanner) return;
        active = null;
        running.set(false);
        pendingPartial.set(null);
        if (failure != null) {
            error.set(failure);
        } else {
            result.ifPresent(root::set);
        }
    }

    private void scheduleFlush() {
        if (!flushScheduled.compareAndSet(false, true)) return;
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(THROTTLE_MS));
            pause.setOnFinished(e -> {
                flushScheduled.set(false);
                Partial partial = pendingPartial.getAndSet(null);
                if (partial != null && partial.generation() == generation && running.get()) {
                    root.set(partial.root());
                }
            });
            pause.play();
        });
    }
}
