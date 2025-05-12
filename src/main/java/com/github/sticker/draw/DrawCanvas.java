package com.github.sticker.draw;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

import java.util.Stack;

public class DrawCanvas extends Pane {
    private Path currentPath;
    private Rectangle tempRect;
    private Color strokeColor = Color.RED;
    private double strokeWidth = 3;
    private boolean strokeDashed = false;

    private Pane parentContainer;

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

    public DrawCanvas(Pane parentContainer) {
        this.setCache(true);

        this.setPickOnBounds(true);
        this.setStyle("-fx-background-color: transparent;");
        this.parentContainer = parentContainer;
    }

    private void saveState(Node node) {
        undoStack.push(node);
        redoStack.clear();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Node node = undoStack.pop();
            this.getChildren().remove(node);
            redoStack.push(node);
        }
    }

    public void activateTool(DrawMode mode) {
        switch (mode) {
            case PEN -> {
                setupPenTool();
            }
            case RECTANGLE -> setupRectTool();
            case NONE -> {
            }
        }
    }

    private void setupPenTool() {
        currentPath = new Path();
        currentPath.setStroke(strokeColor);
        this.setOnMousePressed(e -> {
            currentPath.getElements().add(new MoveTo(e.getX(), e.getY()));
        });

        this.setOnMouseDragged(e -> {
            currentPath.getElements().add(new LineTo(e.getX(), e.getY()));
            if (!getChildren().contains(currentPath)) {
                getChildren().add(currentPath);
            }
        });
        this.setOnMouseReleased(e -> saveState(currentPath));
    }

    private void setupRectTool() {
        if (tempRect != null) {
            getChildren().remove(tempRect);
        }

        tempRect = new Rectangle();
        tempRect.setStroke(strokeColor);
        tempRect.setFill(Color.TRANSPARENT);
        tempRect.setWidth(0);
        tempRect.setHeight(0);

        this.setOnMousePressed(e -> {
            tempRect.setX(e.getX());
            tempRect.setY(e.getY());
        });

        this.setOnMouseDragged(e -> {
            tempRect.setWidth(Math.abs(e.getX() - tempRect.getX()));
            tempRect.setHeight(Math.abs(e.getY() - tempRect.getY()));
            if (!getChildren().contains(tempRect)) {
                getChildren().add(tempRect);
            }
        });

        this.setOnMouseReleased(e -> {
            Rectangle finalRect = createFinalRectangle();
            getChildren().remove(tempRect);
            getChildren().add(finalRect);
        });
        this.setOnMouseReleased(e -> saveState(tempRect));
    }

    private Rectangle createFinalRectangle() {
        return new Rectangle(
                tempRect.getX(), tempRect.getY(),
                tempRect.getWidth(), tempRect.getHeight()
        );
    }

    public void setStrokeColor(Color color) {
        this.strokeColor = color;
        updateCurrentStrokeStyle();
    }

    public void setStrokeWidth(double width) {
        this.strokeWidth = width;
        updateCurrentStrokeStyle();
    }

    public void setStrokeDashed(boolean dashed) {
        this.strokeDashed = dashed;
        updateCurrentStrokeStyle();
    }

    private void updateCurrentStrokeStyle() {
        if (currentPath != null) {
            currentPath.setStrokeLineCap(StrokeLineCap.ROUND);
            currentPath.setStrokeLineJoin(StrokeLineJoin.ROUND);
            currentPath.setStrokeMiterLimit(10);

            currentPath.setStroke(strokeColor);
            currentPath.setStrokeWidth(strokeWidth);
            if (strokeDashed) {
                currentPath.getStrokeDashArray().setAll(5d, 5d);
            } else {
                currentPath.getStrokeDashArray().clear();
            }
        }
        if (tempRect != null) {
            tempRect.setStroke(strokeColor);
            tempRect.setStrokeWidth(strokeWidth);
            if (strokeDashed) {
                tempRect.getStrokeDashArray().setAll(5d, 5d);
            } else {
                tempRect.getStrokeDashArray().clear();
            }
        }
    }
}