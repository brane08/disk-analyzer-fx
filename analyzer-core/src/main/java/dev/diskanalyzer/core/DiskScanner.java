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

    private final Path root;
    private final ScanListener listener;
    private final Semaphore permits = new Semaphore(MAX_PARALLEL);
    private final AtomicLong items = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private volatile boolean cancelled;

    public DiskScanner(Path root, ScanListener listener) {
        this.root = root;
        this.listener = listener;
    }

    public void cancel() {
        cancelled = true;
    }

    public Optional<FileNode> scan() {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Path does not exist: " + root);
        }
        BasicFileAttributes attrs = readAttributes(root);
        if (attrs == null) return Optional.empty();
        FileNode result = isTraversableDirectory(attrs) ? scanRoot() : scan(root, null);
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
                    throw new IllegalStateException(e.getCause());
                }
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
        if (node == null || cancelled) return;
        synchronized (rootNode) {
            rootNode.addChild(node);
            FileNode snapshot = rootNode.copy(null);
            snapshot.sortChildren();
            listener.onPartial(snapshot);
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

        if (!isTraversableDirectory(attrs)) {
            bytes.addAndGet(attrs.size());
            return new FileNode(name, p, false, parent, attrs.size());
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

    /** Directories that are symlinks or "other" (e.g. Windows junctions) are leaves — never entered. */
    static boolean isTraversableDirectory(BasicFileAttributes attrs) {
        return attrs.isDirectory() && !attrs.isSymbolicLink() && !attrs.isOther();
    }

    private static String nameOf(Path p) {
        Path fileName = p.getFileName();
        return fileName == null ? p.toString() : fileName.toString();
    }
}
