package com.github.sticker.draw;

import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.input.KeyCode;
import javafx.scene.Scene;

public class DrawingToolbar extends ToolBar {
    private final ColorPicker colorPicker;
    private final ComboBox<Double> strokeWidthPicker;
    private Button currentToolButton;
    private final ImageView sticker;
    private boolean isShown = false;

    public DrawingToolbar(ImageView sticker) {
        this.sticker = sticker;
        
        // 设置现代风格
        setStyle("-fx-background-color: rgb(255, 255, 255);" +
                "-fx-background-radius: 8;" +
                "-fx-border-radius: 8;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.2), 10, 0, 0, 2);" +
                "-fx-padding: 6;" +
                "-fx-spacing: 8;");

        // 创建工具按钮
        Button pencilBtn = createToolButton(Icon.pencil, "Pencil");
        Button lineBtn = createToolButton(Icon.line, "Line");
        Button rectangleBtn = createToolButton(Icon.rectangle, "Rectangle");
        Button undoBtn = createToolButton(Icon.undo, "Undo");
        Button redoBtn = createToolButton(Icon.redo, "Redo");

        // 添加分隔符
        Separator separator1 = new Separator(Orientation.VERTICAL);
        separator1.setStyle("-fx-background-color: rgba(0, 0, 0, 0.1);");

        // 创建颜色选择器
        colorPicker = new ColorPicker(Color.BLACK);
        colorPicker.setStyle("-fx-background-color: transparent;");
        colorPicker.setPrefWidth(30);

        // 创建线宽选择器
        strokeWidthPicker = new ComboBox<>();
        strokeWidthPicker.getItems().addAll(1.0, 2.0, 3.0, 4.0, 5.0);
        strokeWidthPicker.setValue(2.0);
        strokeWidthPicker.setStyle("-fx-background-color: transparent;");
        strokeWidthPicker.setPrefWidth(60);

        // 添加所有控件到工具栏
        getItems().addAll(
            pencilBtn, lineBtn, rectangleBtn,
            separator1,
            colorPicker, strokeWidthPicker,
            new Separator(Orientation.VERTICAL),
            undoBtn, redoBtn
        );

        // 设置工具栏初始状态
        setVisible(false);

        // 监听场景变化
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.setOnKeyPressed(null);
            }
            if (newScene != null) {
                setupKeyboardBehavior(newScene);
            }
        });
    }

    private void setupKeyboardBehavior(Scene scene) {
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE && e.getTarget() == scene) {
                toggleVisibility();
                e.consume();
            }
        });
    }

    public void toggleVisibility() {
        isShown = !isShown;
        setVisible(isShown);
    }

    public boolean isShown() {
        return isShown;
    }

    private Button createToolButton(String svgPath, String tooltip) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.TRANSPARENT);
        icon.setStroke(Color.BLACK);
        icon.setStrokeWidth(1.5);
        icon.setScaleX(0.8);
        icon.setScaleY(0.8);

        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setStyle("-fx-background-color: transparent;" +
                "-fx-padding: 5;" +
                "-fx-background-radius: 4;");

        // 添加鼠标悬停效果
        button.setOnMouseEntered(e -> 
            button.setStyle("-fx-background-color: rgba(0, 0, 0, 0.1);" +
                    "-fx-padding: 5;" +
                    "-fx-background-radius: 4;"));

        button.setOnMouseExited(e -> 
            button.setStyle("-fx-background-color: transparent;" +
                    "-fx-padding: 5;" +
                    "-fx-background-radius: 4;"));

        return button;
    }

    public ColorPicker getColorPicker() {
        return colorPicker;
    }

    public ComboBox<Double> getStrokeWidthPicker() {
        return strokeWidthPicker;
    }

    public Button getCurrentToolButton() {
        return currentToolButton;
    }

    public void setCurrentToolButton(Button button) {
        this.currentToolButton = button;
    }
} 