package com.github.sticker.draw;

import com.github.sticker.screenshot.ScreenshotSelector;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class FloatingToolbar {
    private DrawMode currentMode = DrawMode.NONE;

    private final HBox toolbar = new HBox(8);
    private final HBox subToolbar = new HBox(10);
    private final ColorPicker colorPicker = new ColorPicker();
    private final Slider sizeSlider = new Slider(1, 20, 3);

    private final Rectangle selectionArea;
    private final Pane parentContainer;
    private boolean isVisible = true;

    private Button penButton;
    private Button rectButton;
    private Button lineButton;
    private final DrawCanvas drawCanvas;
    private Button activeButton;

    private final ScreenshotSelector screenshotSelector;

    private void createButtons() {
        Separator group1Separator = createStyledSeparator();
        Separator group2Separator = createStyledSeparator();

        createBrushButton();
        createLineButton();
        createRectButton();
        toolbar.getChildren().add(group1Separator);

        createUndoButton();
        createRedoButton();
        toolbar.getChildren().add(group2Separator);

        // createCloseButton();
        createStickerButton();
        createCopyButton();
        createSaveButton();
        toolbar.setAlignment(Pos.CENTER_LEFT);
    }

    private void createLineButton() {
        lineButton = createIconButton(Icon.line, "Line (L)");
        lineButton.setOnAction(e -> {
            currentMode = DrawMode.switchMode(currentMode, DrawMode.LINE);
            activateTool(lineButton);
        });
        toolbar.getChildren().add(lineButton);
    }

    private void createRedoButton() {
        Button btn = createIconButton(Icon.redo, "Redo");
        btn.setOnAction(e -> drawCanvas.redo());

        btn.setDisable(true);
        drawCanvas.redoStackEmptyProperty().addListener((obs, old, empty) ->
                btn.setDisable(empty)
        );
        toolbar.getChildren().add(btn);
    }

    private void createUndoButton() {
        Button btn = createIconButton(Icon.undo, "Undo");
        btn.setOnAction(e -> drawCanvas.undo());

        btn.setDisable(true);
        drawCanvas.undoStackEmptyProperty().addListener((obs, old, empty) ->
                btn.setDisable(empty)
        );
        toolbar.getChildren().add(btn);
    }

    private void createCopyButton() {
        Button btn = createIconButton(Icon.copy, "Copy to Clipboard");
        btn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putImage(snapshotScreen());
            clipboard.setContent(content);
            screenshotSelector.cancelSelection();
        });
        toolbar.getChildren().add(btn);
    }

    private void createCloseButton() {
        Button btn = createIconButton(Icon.clode, "Close");
        btn.setOnAction(e -> screenshotSelector.cancelSelection());
        toolbar.getChildren().add(btn);
    }

    private void createSaveButton() {
        Button btn = createIconButton(Icon.save, "Save to file");
        btn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Screenshot");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png")
            );
            File file = fileChooser.showSaveDialog(toolbar.getScene().getWindow());
            if (file != null) {
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(snapshotScreen(), null), "png", file);
                } catch (IOException ignored) {
                }
            }
        });
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
        icon.setStroke(Color.WHITE);
        icon.setFill(Color.TRANSPARENT);
        icon.setStrokeWidth(2);
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
            drawCanvas.setCursor(Cursor.DEFAULT);
        } else {
            colorPicker.setValue(drawCanvas.getStrokeColor());
            sizeSlider.setValue(drawCanvas.getStrokeWidth());
            activeButton.getStyleClass().add("active");
            drawCanvas.setCursor(new ImageCursor(getDrawCursor(Color.RED), 15, 15));
        }
    }

    private void createSubToolbar() {
        subToolbar.setPickOnBounds(false);
        subToolbar.setSpacing(16);
        subToolbar.setAlignment(Pos.CENTER);

        // 基础样式
        subToolbar.setStyle("""
            -fx-background-color: rgba(45, 45, 45, 0.95);
            -fx-background-radius: 8;
            -fx-padding: 8 16;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 2);
            """);

        subToolbar.setVisible(false);
        subToolbar.setManaged(false);

        // 大小滑块美化
        sizeSlider.setStyle("""
            -fx-pref-width: 100;
            """);
        sizeSlider.setShowTickLabels(false);
        sizeSlider.setShowTickMarks(false);
        sizeSlider.valueProperty().addListener((obs, old, newSize) ->
                drawCanvas.setStrokeWidth(newSize.doubleValue())
        );

        // 创建颜色快速选择按钮组
        HBox colorButtons = createColorButtons();

        // 创建分隔符
        Separator separator = createStyledSeparator();

        // 布局组装
        subToolbar.getChildren().addAll(
                sizeSlider,
                separator,
                colorButtons
        );

        parentContainer.getChildren().add(subToolbar);
    }

    private HBox createColorButtons() {
        HBox colorBox = new HBox(4);
        colorBox.setAlignment(Pos.CENTER);

        // 预定义颜色数组
        Color[] colors = {
                Color.WHITE,
                Color.RED,
                Color.ORANGE,
                Color.YELLOW,
                Color.GREEN,
                Color.BLUE,
                Color.PURPLE,
                Color.BLACK
        };

        // 创建颜色按钮
        for (Color color : colors) {
            Button colorBtn = createColorButton(color);
            colorBox.getChildren().add(colorBtn);
        }

        return colorBox;
    }

    private Button createColorButton(Color color) {
        Button btn = new Button();
        btn.setMinSize(20, 20);
        btn.setPrefSize(20, 20);
        btn.setMaxSize(20, 20);

        // 按钮样式
        btn.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 4;
            -fx-border-color: rgba(255,255,255,0.3);
            -fx-border-radius: 4;
            -fx-border-width: 1;
            """, toRGBA(color)));

        // 点击事件
        btn.setOnAction(e -> {
            drawCanvas.setStrokeColor(color);
            ImageCursor ic = new ImageCursor(getDrawCursor(color), 15, 15);
            drawCanvas.setCursor(ic);

            // 更新所有颜色按钮的边框
            updateColorButtonsBorder(btn);
        });

        // 鼠标悬停效果
        btn.setOnMouseEntered(e ->
                btn.setStyle(btn.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.3), 5, 0, 0, 0);")
        );

        btn.setOnMouseExited(e ->
                btn.setStyle(btn.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.3), 5, 0, 0, 0);", ""))
        );

        return btn;
    }

    private void updateColorButtonsBorder(Button selectedBtn) {
        // 更新所有颜色按钮的边框
        for (Node node : ((HBox) selectedBtn.getParent()).getChildren()) {
            if (node instanceof Button colorBtn) {
                String style = colorBtn.getStyle();
                if (colorBtn == selectedBtn) {
                    // 选中的按钮加粗边框
                    style = style.replace("border-width: 1", "border-width: 2");
                    style = style.replace("rgba(255,255,255,0.3)", "white");
                } else {
                    // 其他按钮恢复默认边框
                    style = style.replace("border-width: 2", "border-width: 1");
                    style = style.replace("white", "rgba(255,255,255,0.3)");
                }
                colorBtn.setStyle(style);
            }
        }
    }

    private String toRGBA(Color color) {
        return String.format("rgba(%d, %d, %d, %.1f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
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
            case LINE -> drawCanvas.setupLineTool();
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

    private WritableImage snapshotScreen() {
        Rectangle selectionArea = screenshotSelector.getSelectionArea();

        // 获取屏幕选区坐标和尺寸
        double x = selectionArea.getX();
        double y = selectionArea.getY();
        double width = selectionArea.getWidth();
        double height = selectionArea.getHeight();

        // 1. 获取屏幕截图
        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to create Robot instance", e);
        }

        // 将JavaFX坐标转换为AWT屏幕坐标
        Point2D sceneCoords = drawCanvas.localToScreen(x, y);
        java.awt.Rectangle awtRect = new java.awt.Rectangle(
                (int) sceneCoords.getX(),
                (int) sceneCoords.getY(),
                (int) width,
                (int) height
        );

        BufferedImage screenImage = robot.createScreenCapture(awtRect);

        // 2. 获取画布内容
        WritableImage canvasImage = drawCanvas.snapshot(null, null);

        // 3. 合并两张图片
        WritableImage finalImage = new WritableImage((int) width, (int) height);
        PixelWriter writer = finalImage.getPixelWriter();

        // 先绘制屏幕截图
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int argb = screenImage.getRGB(i, j);
                writer.setArgb(i, j, argb);
            }
        }

        // 再叠加绘制画布内容（只绘制非透明像素）
        PixelReader reader = canvasImage.getPixelReader();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                Color color = reader.getColor((int) (x + i), (int) (y + j));
                if (color.getOpacity() > 0) { // 只绘制不透明的像素
                    writer.setColor(i, j, color);
                }
            }
        }

        return finalImage;
    }

    private Separator createStyledSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().clear();

        separator.setStyle("""
                -fx-background-color: null;
                -fx-border-color: #a0a0a0;
                -fx-border-width: 0 2 0 0;
                -fx-padding: 0;
                -fx-min-height: 20px;
                -fx-pref-height: 20px;
                -fx-max-height: 20px;
                -fx-alignment: center;
                -fx-content-display: center;
                """);
        return separator;
    }

    private WritableImage getDrawCursor(Paint color) {
        String verticalCrosshair = "M15 0 L15 30 " +    // 主垂直线
                "M0 15 L30 15 " +    // 主水平线（保持基本十字结构）
                "M15 14 L15 16";     // 中心强化点（更细的垂直线段）

        SVGPath svg = new SVGPath();
        svg.setContent(verticalCrosshair);
        svg.setStroke(color);
        svg.setStrokeWidth(1.5);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return svg.snapshot(params, null);
    }

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer, DrawCanvas drawCanvasArea, ScreenshotSelector screenshotSelector) {
        this.drawCanvas = drawCanvasArea;
        this.selectionArea = selectionArea;
        this.parentContainer = parentContainer;
        this.screenshotSelector = screenshotSelector;
        initializeToolbar();
        createSubToolbar();
        parentContainer.getChildren().add(toolbar);
    }
}