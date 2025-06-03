package com.github.sticker.draw;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import static com.github.sticker.draw.Icon.createDirectionalCursor;
import static com.github.sticker.draw.Icon.point;

public class DrawCanvas extends Pane {
    private Path currentPath;
    private final List<Point2D> points = new ArrayList<>();
    private static final double SAMPLE_DISTANCE = 3.0;
    private Point2D lastSampledPoint;

    private Rectangle currentRectangle;
    private double startX, startY;

    private Color strokeColor = Color.RED;
    private double strokeWidth = 2;
    private boolean strokeDashed = false;


    private final BooleanProperty undoStackEmpty = new SimpleBooleanProperty(true);
    private final BooleanProperty redoStackEmpty = new SimpleBooleanProperty(true);

    public BooleanProperty undoStackEmptyProperty() {
        return undoStackEmpty;
    }

    public BooleanProperty redoStackEmptyProperty() {
        return redoStackEmpty;
    }


    public Color getStrokeColor() {
        return strokeColor;
    }

    public double getStrokeWidth() {
        return strokeWidth;
    }

    public boolean isStrokeDashed() {
        return strokeDashed;
    }

    private final Stack<Node> undoStack = new Stack<>();
    private final Stack<Node> redoStack = new Stack<>();

    public DrawCanvas() {
        this.setCache(true);
    }

    private void saveState(Node node) {
        undoStack.push(node);
        redoStack.clear();
        if (undoStack.size() > 50) {
            undoStack.remove(0);
        }
        undoStackEmpty.set(undoStack.isEmpty());
        redoStackEmpty.set(redoStack.isEmpty());
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Node node = undoStack.pop();
            this.getChildren().remove(node);
            redoStack.push(node);
            requestLayout();

            undoStackEmpty.set(undoStack.isEmpty());
            redoStackEmpty.set(redoStack.isEmpty());
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Node node = redoStack.pop();
            this.getChildren().add(node);
            undoStack.push(node);
            requestLayout();

            undoStackEmpty.set(undoStack.isEmpty());
            redoStackEmpty.set(redoStack.isEmpty());
        }
    }

    private boolean isPointInBounds(double x, double y) {
        return x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
    }

    void setupPenTool() {
        // Clear any existing event handlers
        this.setOnMousePressed(null);
        this.setOnMouseDragged(null);
        this.setOnMouseReleased(null);

        this.setOnMousePressed(e -> {
            if (!isPointInBounds(e.getX(), e.getY())) return;
            
            points.clear();
            lastSampledPoint = new Point2D(e.getX(), e.getY());
            points.add(lastSampledPoint);

            currentPath = new Path();
            if (strokeDashed) currentPath.getStrokeDashArray().setAll(5d, 10d);

            currentPath.setStroke(strokeColor);
            currentPath.setStrokeWidth(strokeWidth);
            currentPath.getElements().add(new MoveTo(e.getX(), e.getY()));
            getChildren().add(currentPath);
        });

        this.setOnMouseDragged(e -> {
            if (currentPath == null) return;
            if (!isPointInBounds(e.getX(), e.getY())) return;

            this.setCursor(Cursor.NONE);
            Point2D currentPoint = new Point2D(e.getX(), e.getY());
            if (currentPoint.distance(lastSampledPoint) >= SAMPLE_DISTANCE) {
                points.add(currentPoint);
                lastSampledPoint = currentPoint;

                if (points.size() >= 3) {
                    Point2D p0 = points.get(points.size() - 3);
                    Point2D p1 = points.get(points.size() - 2);
                    Point2D p2 = points.get(points.size() - 1);

                    currentPath.getElements().add(
                            new QuadCurveTo(
                                    p1.getX(), p1.getY(),
                                    (p1.getX() + p2.getX()) / 2, (p1.getY() + p2.getY()) / 2
                            )
                    );
                } else if (points.size() == 2) {
                    currentPath.getElements().add(new LineTo(currentPoint.getX(), currentPoint.getY()));
                }
            }
        });

        this.setOnMouseReleased(e -> {
            if (currentPath != null && points.size() >= 3) {
                Point2D p1 = points.get(points.size() - 2);
                Point2D p2 = points.get(points.size() - 1);
                currentPath.getElements().add(
                        new QuadCurveTo(
                                p1.getX(), p1.getY(),
                                p2.getX(), p2.getY()
                        )
                );
                saveState(currentPath);
            }
            currentPath = null;
            this.setCursor(createDirectionalCursor(point));
        });
    }

    public void setupLineTool() {
        final double[] startX = new double[1];
        final double[] startY = new double[1];
        final Line[] previewLine = new Line[1];

        this.setOnMousePressed(e -> {
            if (!isPointInBounds(e.getX(), e.getY())) return;
            
            startX[0] = e.getX();
            startY[0] = e.getY();

            previewLine[0] = new Line(
                    startX[0], startY[0],
                    startX[0], startY[0]
            );

            previewLine[0].setStroke(getStrokeColor());
            previewLine[0].setStrokeWidth(getStrokeWidth());
            if (isStrokeDashed()) {
                previewLine[0].getStrokeDashArray().addAll(5d, 5d);
            }

            getChildren().add(previewLine[0]);
        });

        this.setOnMouseDragged(e -> {
            if (previewLine[0] == null) return;
            
            double endX = Math.min(Math.max(0, e.getX()), getWidth());
            double endY = Math.min(Math.max(0, e.getY()), getHeight());
            
            this.setCursor(Cursor.NONE);
            previewLine[0].setEndX(endX);
            previewLine[0].setEndY(endY);
        });

        this.setOnMouseReleased(e -> {
            if (previewLine[0] != null) {
                Line finalLine = new Line(
                        startX[0], startY[0],
                        e.getX(), e.getY()
                );

                finalLine.setStroke(getStrokeColor());
                finalLine.setStrokeWidth(getStrokeWidth());
                if (isStrokeDashed()) {
                    finalLine.getStrokeDashArray().addAll(5d, 5d);
                }

                getChildren().remove(previewLine[0]);
                getChildren().add(finalLine);
                saveState(finalLine);
                previewLine[0] = null;
            }
            this.setCursor(createDirectionalCursor(point));
        });
    }

    void setupRectTool() {
        this.setOnMousePressed(e -> {
            if (!isPointInBounds(e.getX(), e.getY())) return;
            
            currentRectangle = new Rectangle();
            currentRectangle.setStroke(strokeColor);
            currentRectangle.setFill(Color.TRANSPARENT);
            currentRectangle.setStrokeWidth(strokeWidth);

            startX = e.getX();
            startY = e.getY();
            currentRectangle.setX(startX);
            currentRectangle.setY(startY);
            currentRectangle.setWidth(0);
            currentRectangle.setHeight(0);

            if (strokeDashed) currentRectangle.getStrokeDashArray().setAll(5d, 10d);
            if (!getChildren().contains(currentRectangle)) {
                getChildren().add(currentRectangle);
            }
        });

        this.setOnMouseDragged(e -> {
            if (currentRectangle == null) return;
            
            this.setCursor(Cursor.NONE);
            double currentX = Math.min(Math.max(0, e.getX()), getWidth());
            double currentY = Math.min(Math.max(0, e.getY()), getHeight());

            double newX = Math.min(startX, currentX);
            double newY = Math.min(startY, currentY);
            double newWidth = Math.abs(currentX - startX);
            double newHeight = Math.abs(currentY - startY);

            // Ensure rectangle stays within bounds
            if (newX + newWidth > getWidth()) {
                newWidth = getWidth() - newX;
            }
            if (newY + newHeight > getHeight()) {
                newHeight = getHeight() - newY;
            }

            currentRectangle.setX(newX);
            currentRectangle.setY(newY);
            currentRectangle.setWidth(newWidth);
            currentRectangle.setHeight(newHeight);
        });

        this.setOnMouseReleased(e -> {
            if (currentRectangle != null) {
                saveState(currentRectangle);
            }
            this.setCursor(createDirectionalCursor(point));
        });
    }

    public void setStrokeColor(Color color) {
        this.strokeColor = color;
    }

    public void setStrokeWidth(double width) {
        this.strokeWidth = width;
    }

    public void setStrokeDashed(boolean dashed) {
        this.strokeDashed = dashed;
    }
}