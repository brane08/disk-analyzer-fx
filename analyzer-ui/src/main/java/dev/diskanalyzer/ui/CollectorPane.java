package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.Cleaner;
import dev.diskanalyzer.core.CleanResult;
import dev.diskanalyzer.core.Collector;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.Mode;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Map;

/** Drop target holding items to clean up, with reclaimable-space preview. */
public final class CollectorPane extends VBox {
    private final Collector collector;
    private final Cleaner cleaner;
    private final Runnable onCleaned;
    private final ObservableList<FileNode> view = FXCollections.observableArrayList();
    private final Label summary = new Label();
    private final Button trashBtn = new Button("Move to Trash", Icons.of("mdi2t-trash-can-outline"));
    private final Button deleteBtn = new Button("Delete Permanently", Icons.of("mdi2d-delete-forever"));
    private final Button clearBtn = new Button("Clear");
    private boolean cleaning;

    public CollectorPane(Collector collector, Cleaner cleaner, Runnable onCleaned) {
        this.collector = collector;
        this.cleaner = cleaner;
        this.onCleaned = onCleaned;
        setSpacing(8);
        setPadding(new Insets(8));

        Label hint = new Label("Drag files or folders here, or right-click → Add to Collector.");
        hint.setWrapText(true);

        ListView<FileNode> list = new ListView<>(view);
        list.setPlaceholder(new Label("Collector is empty"));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FileNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : n.path() + "  —  " + FileNode.human(n.size()));
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        trashBtn.setOnAction(e -> clean(Mode.TRASH));
        deleteBtn.setOnAction(e -> clean(Mode.PERMANENT));
        clearBtn.setOnAction(e -> {
            collector.clear();
            refresh();
        });
        list.setOnKeyPressed(e -> {
            FileNode sel = list.getSelectionModel().getSelectedItem();
            if (sel != null && (e.getCode() == javafx.scene.input.KeyCode.DELETE
                    || e.getCode() == javafx.scene.input.KeyCode.BACK_SPACE)) {
                collector.remove(sel);
                refresh();
            }
        });

        setOnDragOver(e -> {
            if (DragState.node != null) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        setOnDragDropped(e -> {
            boolean ok = DragState.node != null;
            if (ok) add(DragState.node);
            e.setDropCompleted(ok);
            e.consume();
        });

        getChildren().addAll(hint, list, summary, trashBtn, deleteBtn, clearBtn);
        trashBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn.setMaxWidth(Double.MAX_VALUE);
        refresh();
    }

    public void add(FileNode node) {
        if (cleaning) return;
        collector.add(node);
        refresh();
    }

    /** Refreshes collected items against a completed scan so sizes are current and vanished items drop out. */
    public void retarget(FileNode root) {
        if (cleaning) return;
        collector.retarget(root);
        refresh();
    }

    private void refresh() {
        view.setAll(collector.items());
        boolean empty = view.isEmpty();
        summary.setText(cleaning ? "Cleaning…" : empty ? "Nothing to reclaim"
                : view.size() + " item(s) — " + FileNode.human(collector.reclaimableBytes()) + " to reclaim");
        boolean locked = empty || cleaning;
        trashBtn.setDisable(locked);
        deleteBtn.setDisable(locked);
        clearBtn.setDisable(locked);
    }

    private void clean(Mode mode) {
        if (cleaning) return;
        StringBuilder paths = new StringBuilder();
        view.stream().limit(15).forEach(n -> paths.append("\n• ").append(n.path()));
        if (view.size() > 15) paths.append("\n… and ").append(view.size() - 15).append(" more");
        boolean permanent = mode == Mode.PERMANENT;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                (permanent ? "Permanently delete " : "Move to Trash ") + view.size() + " item(s) ("
                        + FileNode.human(collector.reclaimableBytes()) + ")?"
                        + (permanent ? " This cannot be undone." : "") + paths,
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(permanent ? "Delete permanently" : "Move to Trash");
        useScaledGraphic(confirm, "mdi2h-help-circle-outline");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        java.util.List<FileNode> items = java.util.List.copyOf(collector.items());
        cleaning = true;
        refresh();
        Thread.ofVirtual().name("collector-clean").start(() -> {
            CleanResult result;
            try {
                result = cleaner.clean(items, mode);
            } catch (Throwable e) {
                java.util.Map<Path, String> failed = new java.util.LinkedHashMap<>();
                for (FileNode n : items) failed.put(n.path(), String.valueOf(e.getMessage()));
                result = new CleanResult(java.util.List.of(), failed, 0);
            }
            CleanResult finished = result;
            Platform.runLater(() -> finishClean(finished, mode, items));
        });
    }

    private void finishClean(CleanResult result, Mode mode, java.util.List<FileNode> cleaned) {
        cleaning = false;
        collector.clear();
        for (FileNode n : cleaned) {
            if (result.failed().containsKey(n.path())) collector.add(n); // keep failures so they can be retried
        }
        refresh();

        StringBuilder msg = new StringBuilder("Freed " + FileNode.human(result.freedBytes())
                + " (" + result.removed().size() + " item(s)).");
        if (!result.failed().isEmpty()) {
            msg.append("\n\nNot removed:");
            for (Map.Entry<Path, String> f : result.failed().entrySet()) {
                msg.append("\n• ").append(f.getKey()).append(" — ").append(f.getValue());
            }
        }
        Alert done = new Alert(result.failed().isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                msg.toString(), ButtonType.OK);
        done.setHeaderText(mode == Mode.TRASH ? "Moved to Trash" : "Deleted");
        useScaledGraphic(done, result.failed().isEmpty() ? "mdi2i-information-outline" : "mdi2a-alert-outline");
        done.showAndWait();
        onCleaned.run();
    }

    /** Replaces the stock fixed-size dialog image with a font icon that scales with the UI; keeps the stock one if unavailable. */
    private static void useScaledGraphic(Alert alert, String literal) {
        javafx.scene.Node graphic = Icons.dialog(literal);
        if (graphic != null) alert.setGraphic(graphic);
    }
}
