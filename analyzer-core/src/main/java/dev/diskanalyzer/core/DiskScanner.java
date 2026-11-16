package dev.diskanalyzer.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Scans a directory tree. Top-level children of the root are scanned in parallel on virtual
 * threads; listener callbacks may therefore arrive from any thread.
 */
public final class DiskScanner {
    private static final int PROGRESS_EVERY = 500;
    private static final int MAX_PARALLEL = 8;
    private static final long PARTIAL_MIN_INTERVAL_NANOS = 250_000_000L;
    private long lastPartialNanos = System.nanoTime() - PARTIAL_MIN_INTERVAL_NANOS;
    private final java.util.concurrent.atomic.AtomicInteger pendingTopLevel =
            new java.util.concurrent.atomic.AtomicInteger();

    private Path root;
    private final ScanListener listener;
    private final Semaphore permits = new Semaphore(MAX_PARALLEL);
    private final AtomicLong items = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private volatile boolean cancelled;

    private final ScanExclusions excluded;
    private boolean partialDirty;

    public DiskScanner(Path root, ScanListener listener) {
        this(root, listener, ScanExclusions.forOs(Os.detect()));
    }

    DiskScanner(Path root, ScanListener listener, ScanExclusions excluded) {
        this.root = root;
        this.listener = listener;
        this.excluded = excluded;
    }

    public void cancel() {
        cancelled = true;
    }

    public Optional<FileNode> scan() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }
        if (Files.isSymbolicLink(root)) {
            try {
                root = root.toRealPath();
            } catch (IOException | SecurityException e) {
                return Optional.empty();
            }
        }
        BasicFileAttributes attrs = readAttributes(root);
        if (attrs == null) return Optional.empty();
        FileNode result = attrs.isDirectory() && !Cleaner.isLinkLike(root, attrs) ? scanRoot() : scan(root, null);
        return cancelled ? Optional.empty() : Optional.ofNullable(result);
    }

    private FileNode scanRoot() {
        FileNode rootNode = new FileNode(nameOf(root), root, true, null, 0);
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            stream.forEach(children::add);
        } catch (IOException | SecurityException | java.nio.file.DirectoryIteratorException e) {
            rootNode.markUnreadable();
            skipped.incrementAndGet();
        }

        pendingTopLevel.set(children.size());
        Thread flusher = Thread.ofVirtual().name("scan-partial-flush").start(() -> flushLoop(rootNode));
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (Path child : children) {
                futures.add(pool.submit(() -> scanTopLevel(child, rootNode)));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    cancelled = true;
                    Thread.currentThread().interrupt();
                    break;
                } catch (ExecutionException e) {
                    cancelled = true; // stop the other workers instead of letting them scan to the end
                    throw new IllegalStateException(e.getCause());
                }
            }
        } finally {
            flusher.interrupt();
            try {
                flusher.join();
            } catch (InterruptedException e) {
                cancelled = true;
                Thread.currentThread().interrupt();
            }
        }
        rootNode.sortChildren();
        return rootNode;
    }

    private void scanTopLevel(Path child, FileNode rootNode) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            cancelled = true;
            Thread.currentThread().interrupt();
            return;
        }
        FileNode node;
        try {
            node = scan(child, rootNode);
        } finally {
            permits.release();
        }
        synchronized (rootNode) {
            boolean add = node != null && !cancelled;
            if (add) rootNode.addChild(node);
            boolean last = pendingTopLevel.decrementAndGet() == 0;
            if (!add) return;
            if (last || System.nanoTime() - lastPartialNanos >= PARTIAL_MIN_INTERVAL_NANOS) {
                emitPartial(rootNode);
            } else {
                partialDirty = true;
            }
        }
    }

    /** Must be called holding the rootNode lock. */
    private void emitPartial(FileNode rootNode) {
        lastPartialNanos = System.nanoTime();
        partialDirty = false;
        FileNode snapshot = rootNode.copy(null);
        snapshot.sortChildren();
        listener.onPartial(snapshot);
    }

    /** Publishes a suppressed partial once the throttle interval has passed, so slow stragglers don't hide finished work. */
    private void flushLoop(FileNode rootNode) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(PARTIAL_MIN_INTERVAL_NANOS / 1_000_000L);
            } catch (InterruptedException e) {
                return;
            }
            synchronized (rootNode) {
                if (partialDirty && !cancelled
                        && System.nanoTime() - lastPartialNanos >= PARTIAL_MIN_INTERVAL_NANOS) {
                    emitPartial(rootNode);
                }
            }
        }
    }

    private FileNode scan(Path p, FileNode parent) {
        if (cancelled) return null;
        BasicFileAttributes attrs = readAttributes(p);
        if (attrs == null) return null;
        String name = nameOf(p);

        long n = items.incrementAndGet();
        if (n % PROGRESS_EVERY == 0) {
            listener.onProgress(new ScanProgress(n, bytes.get(), skipped.get(), p));
        }

        if (!attrs.isDirectory() || Cleaner.isLinkLike(p, attrs)) {
            long size = attrs.isDirectory() ? 0 : attrs.size();
            bytes.addAndGet(size);
            return new FileNode(name, p, false, parent, size);
        }
        if (!p.equals(root) && excluded.contains(p)) {
            return new FileNode(name, p, true, parent, 0);
        }

        FileNode node = new FileNode(name, p, true, parent, 0);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
            for (Path child : stream) {
                if (cancelled) return null;
                FileNode c = scan(child, node);
                if (c != null) node.addChild(c);
            }
        } catch (IOException | SecurityException | java.nio.file.DirectoryIteratorException e) {
            node.markUnreadable();
            skipped.incrementAndGet();
        }
        node.sortChildren();
        return node;
    }

    private static BasicFileAttributes readAttributes(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException e) {
            return null;
        }
    }

    private static String nameOf(Path p) {
        Path fileName = p.getFileName();
        return fileName == null ? p.toString() : fileName.toString();
    }
}
