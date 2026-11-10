package dev.diskanalyzer;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public final class FileTreeItem extends TreeItem<FileNode> {
    private boolean loaded;

    public FileTreeItem(FileNode node) {
        super(node);
    }

    @Override
    public boolean isLeaf() {
        return !getValue().isDir() || getValue().children().isEmpty();
    }

    @Override
    public ObservableList<TreeItem<FileNode>> getChildren() {
        if (!loaded) {
            loaded = true;
            for (FileNode c : getValue().children()) {
                super.getChildren().add(new FileTreeItem(c));
            }
        }
        return super.getChildren();
    }
}
