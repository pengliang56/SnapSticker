package com.github.sticker.draw;

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;

import java.util.Stack;

public class DrawCanvas extends Pane {
    private DrawMode currentMode = DrawMode.NONE;
    private Path currentPath;
    private Rectangle tempRect;
    private Color strokeColor = Color.BLUE;
    private Pane parentContainer;

    private final Stack<Node> undoStack = new Stack<>();
    private final Stack<Node> redoStack = new Stack<>();

    public DrawCanvas(Pane parentContainer) {
        deactivateCurrentTool();
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

    public DrawMode getCurrentDrawMode() {
        return currentMode;
    }

    public void activateTool(DrawMode mode) {
        if (currentMode == mode) {
            deactivateCurrentTool();
            return;
        }

        this.setMouseTransparent(false);
        switch (mode) {
            case PEN -> {
                System.out.println("初始化画笔配置");
                setupPenTool();
            }
            case RECTANGLE -> setupRectTool();
            case NONE -> {} // 无操作
        }
    }

    public void deactivateCurrentTool() {
        currentMode = DrawMode.NONE;

        // 清理事件监听器
        this.setOnMousePressed(null);
        this.setOnMouseDragged(null);
        this.setOnMouseReleased(null);

        // 清理临时图形
        if (tempRect != null) {
            this.getChildren().remove(tempRect);
            tempRect = null;
        }
        if (currentPath != null) {
            this.getChildren().remove(currentPath);
            currentPath = null;
        }

        // 允许事件穿透
        this.setMouseTransparent(true);
        
        // 禁用绘图模式时，启用选择区域的鼠标事件
        if (getParent() != null) {
            getParent().setMouseTransparent(false);
        }
    }

    private void cleanupTempShapes() {
        // 清理未完成的临时图形
        if (tempRect != null && this.getChildren().contains(tempRect)) {
            this.getChildren().remove(tempRect);
        }
        if (currentPath != null && this.getChildren().contains(currentPath)) {
            this.getChildren().remove(currentPath);
        }
    }

    private void setupPenTool() {
        if (currentPath != null) {
            getChildren().remove(currentPath);
        }
        currentPath = new Path();
        currentPath.setStroke(strokeColor);
        System.out.println("setupPenTool" + currentPath);
        this.setOnMousePressed(e -> {
            System.out.println("画笔: 鼠标按下 @(" + e.getX() + "," + e.getY() + ")");
            currentPath.getElements().add(new MoveTo(e.getX(), e.getY()));
        });

        this.setOnMouseDragged(e -> {
            System.out.println("画笔: 鼠标拖动 @(" + e.getX() + "," + e.getY() + ")");
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
}