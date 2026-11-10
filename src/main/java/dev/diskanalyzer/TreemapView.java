package dev.diskanalyzer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class TreemapView extends Region {
    private record Cell(FileNode node, double x, double y, double w, double h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final Canvas canvas = new Canvas();
    private final List<Cell> cells = new ArrayList<>();
    private FileNode current;
    private Cell hovered;

    private Consumer<FileNode> onDrill = n -> { };
    private Consumer<FileNode> onHover = n -> { };

    public TreemapView() {
        getChildren().add(canvas);
        setMinSize(100, 100);
        canvas.setOnMouseMoved(e -> {
            Cell c = cellAt(e.getX(), e.getY());
            if (c != hovered) {
                hovered = c;
                draw();
                onHover.accept(c == null ? null : c.node());
            }
        });
        canvas.setOnMouseExited(e -> {
            hovered = null;
            draw();
            onHover.accept(null);
        });
        canvas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Cell c = cellAt(e.getX(), e.getY());
                if (c != null && c.node().isDir() && !c.node().children().isEmpty()) {
                    onDrill.accept(c.node());
                }
            }
        });
    }

    public void setOnDrill(Consumer<FileNode> h) { this.onDrill = h; }

    public void setOnHover(Consumer<FileNode> h) { this.onHover = h; }

    public void setCurrent(FileNode node) {
        this.current = node;
        this.hovered = null;
        relayout();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        relayout();
    }

    private Cell cellAt(double x, double y) {
        for (Cell c : cells) {
            if (c.contains(x, y)) return c;
        }
        return null;
    }

    private void relayout() {
        cells.clear();
        if (current != null && getWidth() > 0 && getHeight() > 0) {
            List<FileNode> items = current.children().stream().filter(n -> n.size() > 0).toList();
            squarify(items, 0, 0, getWidth(), getHeight());
        }
        draw();
    }

    // Squarified treemap (Bruls, Huizing, van Wijk). Items must be sorted by size descending.
    private void squarify(List<FileNode> items, double x, double y, double w, double h) {
        double total = 0;
        for (FileNode n : items) total += n.size();
        if (total <= 0) return;
        double scale = w * h / total;

        int i = 0;
        while (i < items.size() && w > 0.5 && h > 0.5) {
            double side = Math.min(w, h);
            int j = i;
            double rowSum = 0;
            double worst = Double.MAX_VALUE;
            while (j < items.size()) {
                double newSum = rowSum + items.get(j).size() * scale;
                double rMax = items.get(i).size() * scale;
                double rMin = items.get(j).size() * scale;
                double nw = Math.max(side * side * rMax / (newSum * newSum),
                        newSum * newSum / (side * side * rMin));
                if (j > i && nw > worst) break;
                worst = nw;
                rowSum = newSum;
                j++;
            }
            double thick = rowSum / side;
            double off = 0;
            boolean wide = w >= h;
            for (int k = i; k < j; k++) {
                double len = items.get(k).size() * scale / thick;
                if (wide) {
                    cells.add(new Cell(items.get(k), x, y + off, thick, len));
                } else {
                    cells.add(new Cell(items.get(k), x + off, y, len, thick));
                }
                off += len;
            }
            if (wide) {
                x += thick;
                w -= thick;
            } else {
                y += thick;
                h -= thick;
            }
            i = j;
        }
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#1e1e1e"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setFont(Font.font(11));
        for (Cell c : cells) {
            if (c.w() < 1 || c.h() < 1) continue;
            g.setFill(colorFor(c.node(), c == hovered));
            g.fillRect(c.x(), c.y(), c.w() - 1, c.h() - 1);
            if (c.w() > 56 && c.h() > 30) {
                g.save();
                g.beginPath();
                g.rect(c.x(), c.y(), c.w() - 1, c.h() - 1);
                g.clip();
                g.setFill(Color.WHITE);
                g.fillText(c.node().name(), c.x() + 4, c.y() + 14);
                g.setFill(Color.web("#ffffffcc"));
                g.fillText(FileNode.human(c.node().size()), c.x() + 4, c.y() + 27);
                g.restore();
            }
        }
    }

    private static Color colorFor(FileNode n, boolean hover) {
        String key = n.isDir() ? n.name() : n.extension();
        double hue = Math.floorMod(key.hashCode() * 2654435761L, 360);
        double sat = n.isDir() ? 0.45 : 0.65;
        double bri = (n.isDir() ? 0.55 : 0.75) + (hover ? 0.2 : 0);
        return Color.hsb(hue, sat, Math.min(bri, 1.0));
    }
}
