package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.FileNode;

/** Node currently being dragged inside the app (the dragboard only carries the path string). */
final class DragState {
    static FileNode node;

    private DragState() { }
}
