package com.github.sticker.draw;

import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.Objects;

public class FloatingToolbar {
    private DrawMode currentMode = DrawMode.NONE;

    private final HBox toolbar = new HBox(8);
    private final HBox subToolbar = new HBox(10);
    private final ColorPicker colorPicker = new ColorPicker();
    private final Slider sizeSlider = new Slider(1, 20, 3);
    private final ToggleButton dashedStyleBtn = new ToggleButton("虚线");

    private final Rectangle selectionArea;
    private final Pane parentContainer;
    private boolean isVisible = true;

    private Button penButton;
    private Button rectButton;
    private final DrawCanvas drawCanvas;
    private Button activeButton;

    private void createButtons() {
        Separator sep = new Separator(Orientation.VERTICAL);
        Separator sep1 = new Separator(Orientation.VERTICAL);
        sep.getStyleClass().add("sep");
        sep1.getStyleClass().add("sep");

        createBrushButton();
        createRectButton();
        toolbar.getChildren().add(sep);

        createUndoButton();
        createRedoButton();
        toolbar.getChildren().add(sep1);

        createCloseButton();
        createStickerButton();
        createCopyButton();
        createSaveButton();
    }

    private void createRedoButton() {
        Button btn = createIconButton(Icon.redo, "Redo");
        btn.setOnAction(e -> System.out.println("Redo"));
        toolbar.getChildren().add(btn);
    }

    private void createUndoButton() {
        Button btn = createIconButton(Icon.undo, "Undo");
        btn.setOnAction(e -> System.out.println("Undo"));
        toolbar.getChildren().add(btn);
    }

    private void createCopyButton() {
        Button btn = createIconButton(Icon.copy, "Copy to Clipboard");
        btn.setOnAction(e -> System.out.println("Copy"));
        toolbar.getChildren().add(btn);
    }

    private void createCloseButton() {
        Button btn = createIconButton(Icon.clode, "Close");
        btn.setOnAction(e -> System.out.println("Close"));
        toolbar.getChildren().add(btn);
    }

    private void createSaveButton() {
        Button btn = createIconButton(Icon.save, "Save to file");
        btn.setOnAction(e -> System.out.println("Save"));
        toolbar.getChildren().add(btn);
    }

    private void createBrushButton() {
        penButton = createIconButton(Icon.pencil, "Pencil");
        penButton.setOnAction(e -> {
            currentMode = DrawMode.switchMode(currentMode, DrawMode.PEN);
            activateTool(penButton);
        });
        toolbar.getChildren().add(penButton);
    }

    private void createRectButton() {
        rectButton = createIconButton(Icon.rectangle, "Rectangle");
        rectButton.setOnAction(e -> {
            currentMode = DrawMode.switchMode(currentMode, DrawMode.RECTANGLE);
            activateTool(rectButton);
        });
        toolbar.getChildren().add(rectButton);
    }

    private void createStickerButton() {
        Button btn = createIconButton(Icon.tuding, "Pin to screen(F3)");
        btn.setOnAction(e -> System.out.println("打开贴图库"));
        toolbar.getChildren().add(btn);
    }

    private void setupKeyboardToggle() {
        Scene scene = parentContainer.getScene();
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                toggleVisibility();
                e.consume();
            }
        });
    }

    private Button createIconButton(String svgPath, String tooltipText) {
        Button btn = new Button();
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.WHITE);
        btn.setGraphic(icon);

        btn.getStyleClass().add("tool-button");

        DropShadow hover = new DropShadow(5, Color.gray(0.3));
        btn.setOnMouseEntered(e -> {
            btn.setEffect(hover);
        });
        btn.setOnMouseExited(e -> {
            btn.setEffect(null);
        });

        Tooltip.install(btn, new Tooltip(tooltipText));
        return btn;
    }

    private void toggleVisibility() {
        FadeTransition fade = new FadeTransition(Duration.millis(150), toolbar);
        fade.setFromValue(isVisible ? 1.0 : 0.0);
        fade.setToValue(isVisible ? 0.0 : 1.0);
        fade.play();

        toolbar.setMouseTransparent(!isVisible);
        isVisible = !isVisible;
    }

    private void baseStyle() {
        toolbar.setStyle(
                "-fx-background-color: rgba(50, 50, 50, 0.95);" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 6;" +
                        "-fx-spacing: 8;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 2);"
        );
    }

    public HBox getToolbar() {
        return toolbar;
    }

    public DrawCanvas getDrawCanvas() {
        return drawCanvas;
    }

    public void drawMode(boolean drawMode) {
        parentContainer.getChildren().forEach(it -> it.setMouseTransparent(drawMode));
        drawCanvas.setMouseTransparent(!drawMode);

        subToolbar.setMouseTransparent(false);
        toolbar.setMouseTransparent(false);

        subToolbar.setVisible(drawMode);
        subToolbar.setManaged(drawMode);
        if (!drawMode) {
            activeButton.getStyleClass().remove("active");
            activeButton = null;
        } else {
            colorPicker.setValue(drawCanvas.getStrokeColor());
            sizeSlider.setValue(drawCanvas.getStrokeWidth());
            dashedStyleBtn.setSelected(drawCanvas.isStrokeDashed());
            activeButton.getStyleClass().add("active");
        }
    }

    private void createSubToolbar() {
        subToolbar.setPickOnBounds(false);
        subToolbar.setStyle(toolbar.getStyle() + "-fx-padding: 6 12 6 12;");
        subToolbar.setVisible(false);
        subToolbar.setManaged(false); // 不参与布局计算
        subToolbar.getStyleClass().add("sub-toolbar");

        // 加载CSS样式
        subToolbar.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/toolbar.css")).toExternalForm()
        );

        // 颜色选择器
        colorPicker.setStyle("-fx-color-label-visible: false;");
        colorPicker.valueProperty().addListener
                ((obs, old, newColor) ->
                        drawCanvas.setStrokeColor(newColor)
                );

        // 大小滑块
        sizeSlider.setShowTickMarks(true);
        sizeSlider.setMajorTickUnit(5);
        sizeSlider.setMinorTickCount(4);
        sizeSlider.valueProperty().addListener((obs, old, newSize) ->
                drawCanvas.setStrokeWidth(newSize.doubleValue())
        );
        Label sizeLabel = new Label("Size:");
        sizeLabel.setTextFill(Color.WHITE);

        // 虚线样式
        dashedStyleBtn.setTextFill(Color.WHITE);
        dashedStyleBtn.selectedProperty().addListener((obs, old, newVal) ->
                drawCanvas.setStrokeDashed(newVal)
        );

        // 布局组装
        subToolbar.getChildren().addAll(
                sizeLabel, sizeSlider,
                new Separator(Orientation.VERTICAL),
                colorPicker,
                new Separator(Orientation.VERTICAL),
                dashedStyleBtn
        );

        // 添加到父容器
        parentContainer.getChildren().add(subToolbar);
    }

    private void updateSubToolbarPosition() {
        subToolbar.layoutXProperty().bind(
                toolbar.layoutXProperty().add(toolbar.widthProperty().divide(2))
                        .subtract(subToolbar.widthProperty().divide(2))
        );

        subToolbar.layoutYProperty().bind(
                toolbar.layoutYProperty().add(toolbar.heightProperty()).add(5)
        );
    }

    public void activateTool(Button handleButton) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("active");
        }

        activeButton = handleButton;
        if (currentMode != DrawMode.NONE) {
            drawMode(true);
        }
        switch (currentMode) {
            case PEN -> drawCanvas.setupPenTool();
            case RECTANGLE -> drawCanvas.setupRectTool();
            case NONE -> drawMode(false);
        }
    }

    private void initializeToolbar() {
        toolbar.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/toolbar.css")).toExternalForm()
        );

        baseStyle();
        createButtons();
        setupBottomPositionBinding();
        setupKeyboardToggle();

        toolbar.getStyleClass().add("toolbar");
        toolbar.setPickOnBounds(false);
    }

    private void setupBottomPositionBinding() {
        toolbar.layoutXProperty().bind(
                Bindings.createDoubleBinding(() ->
                                selectionArea.getX() + (selectionArea.getWidth() / 2) - (toolbar.getWidth() / 2),
                        selectionArea.xProperty(), selectionArea.widthProperty(), toolbar.widthProperty()
                )
        );

        // Y轴位置：选区底部 + 10px 间距
        toolbar.layoutYProperty().bind(
                selectionArea.yProperty()
                        .add(selectionArea.heightProperty())
                        .add(10)
        );

        // X轴定位（保持居中且防止左右溢出）
        toolbar.layoutXProperty().bind(
                Bindings.createDoubleBinding(() -> {
                            // 计算理想居中位置
                            double idealX = selectionArea.getX() +
                                    (selectionArea.getWidth() - toolbar.getWidth()) / 2;

                            // 边界约束
                            double minX = 5;
                            double maxX = parentContainer.getWidth() - toolbar.getWidth() - 5;

                            return Math.max(minX, Math.min(idealX, maxX));
                        }, selectionArea.xProperty(), selectionArea.widthProperty(),
                        toolbar.widthProperty(), parentContainer.widthProperty())
        );

        // Y轴动态定位（上下自动切换）
        toolbar.layoutYProperty().bind(
                Bindings.createDoubleBinding(() -> {
                            // 基础参数计算
                            double selectionBottom = selectionArea.getY() + selectionArea.getHeight();
                            double toolbarHeight = toolbar.getHeight();
                            double parentHeight = parentContainer.getHeight();

                            // 默认下方位置
                            double bottomPosition = selectionBottom + 10;

                            // 上方位置
                            double topPosition = selectionArea.getY() - toolbarHeight - 10;

                            // 检测下方空间是否足够
                            boolean canShowBelow = (bottomPosition + toolbarHeight) < (parentHeight - 5);

                            // 智能选择显示位置
                            return canShowBelow ? bottomPosition : topPosition;
                        }, selectionArea.yProperty(), selectionArea.heightProperty(),
                        parentContainer.heightProperty(), toolbar.heightProperty())
        );
        updateSubToolbarPosition();
    }

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer, DrawCanvas drawCanvasArea) {
        this.drawCanvas = drawCanvasArea;
        this.selectionArea = selectionArea;
        this.parentContainer = parentContainer;
        initializeToolbar();
        createSubToolbar();
        parentContainer.getChildren().add(toolbar);
    }
}