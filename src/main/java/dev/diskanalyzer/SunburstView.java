package dev.diskanalyzer;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** DaisyDisk-style concentric-ring view of the directory tree. */
public final class SunburstView extends Region {
    private static final int LEVELS = 5;
    private static final double MIN_SWEEP_DEG = 0.6;

    /** start/sweep are clockwise degrees from 12 o'clock; depth is 1-based ring index. */
    private record Arc(FileNode node, int depth, double start, double sweep, Color color) { }

    private final Canvas canvas = new Canvas();
    private final List<Arc> arcs = new ArrayList<>();
    private FileNode current;
    private Arc hovered;
    private boolean centerHovered;

    private Consumer<FileNode> onDrill = n -> { };
    private Consumer<FileNode> onHover = n -> { };

    public SunburstView() {
        getChildren().add(canvas);
        setMinSize(100, 100);
        canvas.setOnMouseMoved(e -> {
            Arc a = arcAt(e.getX(), e.getY());
            boolean center = a == null && inCenter(e.getX(), e.getY());
            if (a != hovered || center != centerHovered) {
                hovered = a;
                centerHovered = center;
                draw();
                onHover.accept(a == null ? (center ? current : null) : a.node());
            }
        });
        canvas.setOnMouseExited(e -> {
            hovered = null;
            centerHovered = false;
            draw();
            onHover.accept(null);
        });
        canvas.setOnMouseClicked(e -> {
            Arc a = arcAt(e.getX(), e.getY());
            if (a != null) {
                if (a.node().isDir() && !a.node().children().isEmpty()) onDrill.accept(a.node());
            } else if (inCenter(e.getX(), e.getY()) && current != null && current.parent() != null) {
                onDrill.accept(current.parent());
            }
        });
    }

    public void setOnDrill(Consumer<FileNode> h) { this.onDrill = h; }

    public void setOnHover(Consumer<FileNode> h) { this.onHover = h; }

    public void setCurrent(FileNode node) {
        this.current = node;
        this.hovered = null;
        this.centerHovered = false;
        relayout();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        relayout();
    }

    private double cx() { return getWidth() / 2; }

    private double cy() { return getHeight() / 2; }

    private double maxR() { return Math.min(getWidth(), getHeight()) / 2 - 6; }

    private double centerR() { return maxR() * 0.24; }

    private double ringW() { return (maxR() - centerR()) / LEVELS; }

    private boolean inCenter(double x, double y) {
        return Math.hypot(x - cx(), y - cy()) <= centerR();
    }

    private Arc arcAt(double x, double y) {
        double dx = x - cx();
        double dy = y - cy();
        double r = Math.hypot(dx, dy);
        if (r <= centerR() || r > maxR()) return null;
        int depth = (int) ((r - centerR()) / ringW()) + 1;
        double ang = Math.toDegrees(Math.atan2(dx, -dy));
        if (ang < 0) ang += 360;
        for (Arc a : arcs) {
            if (a.depth() == depth && ang >= a.start() && ang < a.start() + a.sweep()) return a;
        }
        return null;
    }

    private void relayout() {
        arcs.clear();
        if (current != null && current.size() > 0 && maxR() > 0) {
            layoutChildren(current, 1, 0, 360, null);
        }
        draw();
    }

    private void layoutChildren(FileNode parent, int depth, double start, double sweep, Color topColor) {
        if (depth > LEVELS || parent.size() <= 0) return;
        double angle = start;
        int idx = 0;
        for (FileNode c : parent.children()) {
            double s = sweep * c.size() / parent.size();
            if (s < MIN_SWEEP_DEG) break; // sorted by size desc: the rest are smaller
            Color color = depth == 1 ? baseColor(c, idx++) : shade(topColor, depth);
            arcs.add(new Arc(c, depth, angle, s, color));
            if (c.isDir()) layoutChildren(c, depth + 1, angle, s, depth == 1 ? color : topColor);
            angle += s;
        }
    }

    private static Color baseColor(FileNode n, int idx) {
        double hue = Math.floorMod((long) n.name().hashCode() * 2654435761L, 360);
        return Color.hsb(hue, 0.6, 0.85);
    }

    private static Color shade(Color top, int depth) {
        return Color.hsb(top.getHue(), Math.max(0.25, top.getSaturation() - 0.06 * (depth - 1)),
                Math.max(0.35, top.getBrightness() - 0.07 * (depth - 1)));
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#1e1e1e"));
        g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (current == null) return;

        double cx = cx();
        double cy = cy();
        double w = ringW();
        g.setLineCap(StrokeLineCap.BUTT);
        g.setLineWidth(w - 1.5);
        for (Arc a : arcs) {
            double r = centerR() + (a.depth() - 0.5) * w;
            Color c = a == hovered ? a.color().brighter() : a.color();
            if (!a.node().isDir()) c = c.deriveColor(0, 0.8, 0.9, 1);
            g.setStroke(c);
            double gap = Math.min(0.3, a.sweep() / 4);
            g.strokeArc(cx - r, cy - r, 2 * r, 2 * r, 90 - a.start() - gap, -(a.sweep() - 2 * gap), ArcType.OPEN);
        }

        g.setFill(centerHovered && current.parent() != null ? Color.web("#3a3a3a") : Color.web("#2a2a2a"));
        g.fillOval(cx - centerR() + 2, cy - centerR() + 2, 2 * centerR() - 4, 2 * centerR() - 4);
        g.setFill(Color.WHITE);
        g.setTextAlign(TextAlignment.CENTER);
        g.setFont(Font.font(Math.max(10, centerR() / 7)));
        g.fillText(current.name(), cx, cy - 4, 2 * centerR() - 16);
        g.setFill(Color.web("#ffffffaa"));
        g.fillText(FileNode.human(current.size()), cx, cy + 14, 2 * centerR() - 16);
        if (current.parent() != null) {
            g.fillText("↑ back", cx, cy + 30, 2 * centerR() - 16);
        }
    }
}
