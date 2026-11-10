package dev.diskanalyzer.ui;

import dev.diskanalyzer.core.CacheCatalog;
import dev.diskanalyzer.core.CacheLocation;
import dev.diskanalyzer.core.DiskScanner;
import dev.diskanalyzer.core.FileNode;
import dev.diskanalyzer.core.ScanListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Developer caches (Xcode, npm, Homebrew, ...) with sizes, ready to add to the collector. */
public final class CachesPane extends VBox {
    private record Entry(String label, FileNode node) { }

    private final CacheCatalog catalog;
    private final Consumer<FileNode> addToCollector;
    private final ListView<Entry> list = new ListView<>();
    private final Label status = new Label();
    private final Button refreshBtn = new Button("Find caches", Icons.of("mdi2m-magnify"));
    private final Button addAllBtn = new Button("Add all to Collector");

    public CachesPane(CacheCatalog catalog, Consumer<FileNode> addToCollector) {
        this.catalog = catalog;
        this.addToCollector = addToCollector;
        setSpacing(8);
        setPadding(new Insets(8));

        list.setPlaceholder(new Label("Press “Find caches” to look for developer caches."));
        list.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Entry e, boolean empty) {
                super.updateItem(e, empty);
                setText(empty || e == null ? null : e.label() + "  —  " + FileNode.human(e.node().size()));
            }
        });
        list.setOnMouseClicked(e -> {
            Entry sel = list.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && sel != null) addToCollector.accept(sel.node());
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        refreshBtn.setOnAction(e -> refresh());
        addAllBtn.setOnAction(e -> list.getItems().forEach(en -> addToCollector.accept(en.node())));
        addAllBtn.setDisable(true);
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        addAllBtn.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(refreshBtn, list, status, addAllBtn);
    }

    private void refresh() {
        refreshBtn.setDisable(true);
        status.setText("Scanning caches…");
        Task<List<Entry>> task = new Task<>() {
            @Override
            protected List<Entry> call() {
                List<Entry> found = new ArrayList<>();
                for (CacheLocation c : catalog.existing()) {
                    new DiskScanner(c.path(), new ScanListener() { }).scan()
                            .ifPresent(n -> found.add(new Entry(c.label(), n)));
                }
                found.sort((a, b) -> Long.compare(b.node().size(), a.node().size()));
                return found;
            }
        };
        task.setOnSucceeded(e -> {
            list.getItems().setAll(task.getValue());
            long total = task.getValue().stream().mapToLong(en -> en.node().size()).sum();
            status.setText(task.getValue().isEmpty() ? "No caches found."
                    : task.getValue().size() + " cache(s) — " + FileNode.human(total) + " total. Double-click to collect.");
            addAllBtn.setDisable(task.getValue().isEmpty());
            refreshBtn.setDisable(false);
        });
        task.setOnFailed(e -> {
            status.setText("Cache scan failed: " + task.getException());
            refreshBtn.setDisable(false);
        });
        Thread t = new Thread(task, "cache-scan");
        t.setDaemon(true);
        t.start();
    }
}
