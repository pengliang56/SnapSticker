package com.github.sticker.draw;

import com.github.sticker.screenshot.ScreenshotSelector;
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
    private static final String[] BUTTON_ICONS = {
            // 保存图标（SVG路径）
            "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z",
            // 画笔图标
            "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
            // 矩形图标
            "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z",
            // 贴图图标（图钉）
            "M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z"
    };
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
    private final ScreenshotSelector screenshotSelector;
    private boolean drawMode;

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer, DrawCanvas drawCanvasArea, ScreenshotSelector screenshotSelector) {
        this.drawCanvas = drawCanvasArea;
        this.selectionArea = selectionArea;
        this.parentContainer = parentContainer;
        this.screenshotSelector = screenshotSelector;
        initializeToolbar();
        createSubToolbar();
        parentContainer.getChildren().add(toolbar);
    }

    private void initializeToolbar() {
        baseStyle();
        createButtons();
        setupBottomPositionBinding();
        setupKeyboardToggle();

        // 加载CSS样式
        toolbar.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/toolbar.css")).toExternalForm()
        );
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

    private void createButtons() {
        createSaveButton();
        createBrushButton();
        createRectButton();
        createStickerButton();
    }

    private void updateButtonStyle(Button button, boolean isActive) {
        if (isActive) {
            button.getStyleClass().add("active");
        } else {
            button.getStyleClass().remove("active");
        }
    }

    private Button createIconButton(String svgPath, String tooltipText) {
        Button btn = new Button();
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.WHITE);
        btn.setGraphic(icon);

        btn.getStyleClass().add("tool-button");

        // 悬停效果
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

    private void createSaveButton() {
        Button btn = createIconButton(BUTTON_ICONS[0], "保存截图");
        btn.setOnAction(e -> System.out.println("执行保存操作"));
        toolbar.getChildren().add(btn);
    }

    private void createBrushButton() {
        penButton = createIconButton(BUTTON_ICONS[1], "Pencil");
        penButton.setOnAction(e -> {
            if (currentMode == DrawMode.PEN) {
                handleToolButtonClick(penButton, DrawMode.PEN);
                currentMode = DrawMode.NONE;
            } else {
                handleToolButtonClick(penButton, DrawMode.PEN);
                currentMode = DrawMode.PEN;
            }
        });
        toolbar.getChildren().add(penButton);
    }

    private void createRectButton() {
        rectButton = createIconButton(BUTTON_ICONS[2], "Rectangle");
        rectButton.setOnAction(e -> {
            if (currentMode == DrawMode.RECTANGLE) {
                drawCanvas.activateTool(DrawMode.RECTANGLE);
                currentMode = DrawMode.NONE;
            } else {
                drawCanvas.activateTool(DrawMode.RECTANGLE);
                currentMode = DrawMode.RECTANGLE;
            }
        });
        toolbar.getChildren().add(rectButton);
    }

    private void createStickerButton() {
        Button btn = createIconButton(BUTTON_ICONS[3], "添加贴图");
        btn.setOnAction(e -> System.out.println("打开贴图库"));
        toolbar.getChildren().add(btn);
    }

    private void setupKeyboardToggle() {
        Scene scene = parentContainer.getScene();
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                toggleVisibility();
                e.consume(); // 阻止事件冒泡
            }
        });
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

    private void handleToolButtonClick(Button clickedBtn, DrawMode targetMode) {
        if (activeButton != null) {
            updateButtonStyle(activeButton, false);
        }

        System.out.println("currentMode = " + currentMode);
        System.out.println("targetMode = " + targetMode);
        if (currentMode == targetMode) {
            clickedBtn.getStyleClass().remove("active");
            activeButton = null;
            drawMode(false);

            subToolbar.setVisible(false); // 隐藏子工具栏
            subToolbar.setManaged(false);
        } else {
            if (activeButton != null) {
                activeButton.getStyleClass().remove("active");
            }

            // 激活新工具
            drawCanvas.activateTool(targetMode);
            clickedBtn.getStyleClass().add("active");
            activeButton = clickedBtn;

            drawCanvas.setMouseTransparent(false);
            drawMode(true);
            updateSubToolbarVisibility(targetMode);
            updateButtonStyle(clickedBtn, !(currentMode == targetMode));
        }
    }

    public void drawMode(boolean drawMode) {
        parentContainer.getChildren().forEach(it -> it.setMouseTransparent(drawMode));
        drawCanvas.setMouseTransparent(!drawMode);
        subToolbar.setMouseTransparent(false);
        toolbar.setMouseTransparent(false);
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
        colorPicker.valueProperty().addListener((obs, old, newColor) ->
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
        // 水平居中：主工具栏中心X - 子工具栏宽度的一半
        subToolbar.layoutXProperty().bind(
                toolbar.layoutXProperty().add(toolbar.widthProperty().divide(2))
                        .subtract(subToolbar.widthProperty().divide(2))
        );

        // 垂直位置：主工具栏Y + 主工具栏高度 + 5px间距
        subToolbar.layoutYProperty().bind(
                toolbar.layoutYProperty().add(toolbar.heightProperty()).add(5)
        );
    }

    // 根据工具类型控制子工具栏
    private void updateSubToolbarVisibility(DrawMode mode) {
        boolean show = mode == DrawMode.PEN || mode == DrawMode.RECTANGLE;
        subToolbar.setVisible(show);
        subToolbar.setManaged(show);
        if (show) {
            // 同步初始值
            colorPicker.setValue(drawCanvas.getStrokeColor());
            sizeSlider.setValue(drawCanvas.getStrokeWidth());
            dashedStyleBtn.setSelected(drawCanvas.isStrokeDashed());
        }
    }

/*    public void updateButtonStates() {
        for (Node node : toolbar.getChildren()) {
            if (node instanceof Button btn) {
                DrawMode btnMode = getButtonMode(btn);
                if (btnMode == drawCanvas.getCurrentDrawMode()) {
                    btn.getStyleClass().add("active");
                } else {
                    btn.getStyleClass().remove("active");
                }
            }
        }
    }*/
}