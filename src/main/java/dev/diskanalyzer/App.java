package dev.diskanalyzer;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.control.ToggleButton;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class App extends Application {
    private Stage stage;
    private final TreeTableView<FileNode> table = new TreeTableView<>();
    private final TreemapView treemap = new TreemapView();
    private final SunburstView sunburst = new SunburstView();
    private final ToggleButton treemapToggle = new ToggleButton("Treemap");
    private final Label status =new Label("Choose a folder to analyze.");
    private final Label pathLabel = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final Button scanBtn = new Button("Scan folder…");
    private final Button cancelBtn = new Button("Cancel");
    private final Button upBtn = new Button("↑ Up");
    private DiskScanner scanner;
    private FileNode treemapRoot;
    private boolean syncing;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        progress.setVisible(false);
        progress.setPrefWidth(120);
        cancelBtn.setDisable(true);
        upBtn.setDisable(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, scanBtn, cancelBtn, upBtn, pathLabel, spacer, progress, treemapToggle);
        bar.setPadding(new Insets(8));

        configureTable();

        treemap.visibleProperty().bind(treemapToggle.selectedProperty());
        sunburst.visibleProperty().bind(treemapToggle.selectedProperty().not());
        SplitPane split = new SplitPane(table, new StackPane(sunburst, treemap));
        split.setDividerPositions(0.42);

        status.setPadding(new Insets(4, 8, 4, 8));

        BorderPane root = new BorderPane(split);
        root.setTop(bar);
        root.setBottom(status);

        scanBtn.setOnAction(e -> chooseAndScan());
        cancelBtn.setOnAction(e -> {
            if (scanner != null) scanner.cancel();
        });
        upBtn.setOnAction(e -> {
            if (treemapRoot != null && treemapRoot.parent() != null) {
                select(treemapRoot.parent());
            }
        });

        java.util.function.Consumer<FileNode> hover = n -> {
            if (n != null) {
                status.setText(n.path() + "  —  " + FileNode.human(n.size()) + "  —  " + n.fileCount() + " files");
            }
        };
        treemap.setOnDrill(this::select);
        treemap.setOnHover(hover);
        sunburst.setOnDrill(this::select);
        sunburst.setOnHover(hover);

        stage.setTitle("Disk Analyzer");
        stage.setScene(new Scene(root, 1200, 720));
        stage.show();
    }

    private void configureTable() {
        TreeTableColumn<FileNode, String> name = new TreeTableColumn<>("Name");
        name.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getValue().name()));
        name.setPrefWidth(220);

        TreeTableColumn<FileNode, String> size = new TreeTableColumn<>("Size");
        size.setCellValueFactory(c -> new ReadOnlyStringWrapper(FileNode.human(c.getValue().getValue().size())));
        size.setPrefWidth(90);
        size.setStyle("-fx-alignment: CENTER-RIGHT;");

        TreeTableColumn<FileNode, String> pct = new TreeTableColumn<>("%");
        pct.setCellValueFactory(c -> {
            FileNode n = c.getValue().getValue();
            FileNode p = n.parent();
            String s = (p == null || p.size() == 0) ? "100%" : String.format("%.1f%%", 100.0 * n.size() / p.size());
            return new ReadOnlyStringWrapper(s);
        });
        pct.setPrefWidth(60);
        pct.setStyle("-fx-alignment: CENTER-RIGHT;");

        TreeTableColumn<FileNode, String> files = new TreeTableColumn<>("Files");
        files.setCellValueFactory(c -> new ReadOnlyStringWrapper(String.valueOf(c.getValue().getValue().fileCount())));
        files.setPrefWidth(70);
        files.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(List.of(name, size, pct, files));
        table.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (syncing || item == null) return;
            FileNode n = item.getValue();
            showInTreemap(n.isDir() ? n : n.parent());
        });
    }

    private void chooseAndScan() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder to analyze");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            scan(dir.toPath());
        }
    }

    private void scan(Path path) {
        if (scanner != null) scanner.cancel();
        DiskScanner task = new DiskScanner(path);
        scanner = task;
        pathLabel.setText(path.toString());
        progress.setVisible(true);
        progress.progressProperty().bind(task.progressProperty());
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        status.textProperty().bind(task.messageProperty());
        scanBtn.setDisable(true);
        cancelBtn.setDisable(false);
        table.setRoot(null);
        treemap.setCurrent(null);
        sunburst.setCurrent(null);

        Runnable done = () -> {
            progress.progressProperty().unbind();
            progress.setVisible(false);
            status.textProperty().unbind();
            scanBtn.setDisable(false);
            cancelBtn.setDisable(true);
        };

        task.setOnSucceeded(e -> {
            done.run();
            FileNode root = task.getValue();
            if (root == null) {
                status.setText("Scan produced no result.");
                return;
            }
            FileTreeItem rootItem = new FileTreeItem(root);
            rootItem.setExpanded(true);
            table.setRoot(rootItem);
            showInTreemap(root);
            status.setText(root.fileCount() + " files, " + FileNode.human(root.size()) + " in " + root.path());
        });
        task.setOnCancelled(e -> {
            done.run();
            status.setText("Scan cancelled.");
        });
        task.setOnFailed(e -> {
            done.run();
            status.setText("Scan failed: " + task.getException());
        });

        Thread t = new Thread(task, "disk-scanner");
        t.setDaemon(true);
        t.start();
    }

    private void showInTreemap(FileNode node) {
        treemapRoot = node;
        upBtn.setDisable(node == null || node.parent() == null);
        treemap.setCurrent(node);
        sunburst.setCurrent(node);
    }

    /** Select node in the table (expanding ancestors) and show it in the treemap. */
    private void select(FileNode node) {
        if (table.getRoot() == null) return;
        List<FileNode> chain = new ArrayList<>();
        for (FileNode n = node; n != null; n = n.parent()) chain.add(n);
        Collections.reverse(chain);

        TreeItem<FileNode> item = table.getRoot();
        for (int i = 1; i < chain.size(); i++) {
            item.setExpanded(true);
            TreeItem<FileNode> next = null;
            for (TreeItem<FileNode> c : item.getChildren()) {
                if (c.getValue() == chain.get(i)) {
                    next = c;
                    break;
                }
            }
            if (next == null) return;
            item = next;
        }
        syncing = true;
        try {
            table.getSelectionModel().select(item);
            table.scrollTo(table.getRow(item));
        } finally {
            syncing = false;
        }
        showInTreemap(node.isDir() ? node : node.parent());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
