package com.github.sticker.draw;

import com.github.sticker.feature.StickerStage;
import com.github.sticker.feature.widget.StickerPane;
import com.github.sticker.screenshot.ScreenshotSelector;
import com.github.sticker.util.ShotScreen;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
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
import java.io.File;
import java.io.IOException;

import static com.github.sticker.draw.DrawMode.*;
import static com.github.sticker.draw.Icon.createDirectionalCursor;
import static com.github.sticker.draw.Icon.point;

public class FloatingToolbar {
    private DrawMode currentMode = DrawMode.NONE;
    private Button activeButton;
    private boolean switchDirection = false;
    private boolean drawMode = false;

    // ----------------------------------------------------
    private final Rectangle selectionArea;
    private final Pane parentContainer;


    // ----------------------------------------------------
    private Button penButton;
    private Button rectButton;
    private Button lineButton;
    private Button closeButton;
    private final DrawCanvas drawCanvas;

    // ----------------------------------------------------
    private final HBox toolbar = new HBox(8);
    private final HBox subToolbar = new HBox(10);
    private final ColorPicker colorPicker = new ColorPicker();
    private final Slider sizeSlider = new Slider(1, 20, 3);

    private final ScreenshotSelector screenshotSelector;
    private final StickerStage stickerStage;
    private StickerPane stickerPane;

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

        if (stickerPane == null) {
            createStickerButton();
        }

        createCopyButton();
        createSaveButton();

        if (stickerPane == null) {
            createCloseButton();
        }
        toolbar.setAlignment(Pos.CENTER_LEFT);
    }

    private void createCloseButton() {
        closeButton = createIconButton(Icon.clode, "Close (C)");
        closeButton.setOnAction(e -> cancleSelection());
        toolbar.getChildren().add(closeButton);
    }

    private void createLineButton() {
        lineButton = createIconButton(Icon.line, "Line (L)");
        lineButton.setOnAction(e -> activateTool(lineButton, DrawMode.LINE));
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
            cancleSelection();
        });
        toolbar.getChildren().add(btn);
    }

    private void createSaveButton() {
        Button btn = createIconButton(Icon.save, "Save to file");
        btn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Screenshot");

            String timestamp = String.format("%1$tY%1$tm%1$td%1$tH%1$tM%1$tS",
                    System.currentTimeMillis());
            String defaultFileName = "SnapSticker_" + timestamp + ".png";

            fileChooser.setInitialFileName(defaultFileName);
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PNG Image", "*.png")
            );
            String userHome = System.getProperty("user.home");
            File picturesDir = new File(userHome, "Pictures");
            if (picturesDir.exists()) {
                fileChooser.setInitialDirectory(picturesDir);
            }

            File file = fileChooser.showSaveDialog(toolbar.getScene().getWindow());
            if (file != null) {
                WritableImage image = snapshotScreen();
                try {
                    ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
                    cancleSelection();
                } catch (IOException ignored) {
                }
            }
        });
        toolbar.getChildren().add(btn);
    }

    private void createBrushButton() {
        penButton = createIconButton(Icon.pencil, "Pencil");
        penButton.setOnAction(e -> activateTool(penButton, PEN));
        toolbar.getChildren().add(penButton);
    }

    private void createRectButton() {
        rectButton = createIconButton(Icon.rectangle, "Rectangle");
        rectButton.setOnAction(e -> activateTool(rectButton, RECTANGLE));
        toolbar.getChildren().add(rectButton);
    }

    private void createStickerButton() {
        Button btn = createIconButton(Icon.tuding, "Pin to screen(F3)");
        btn.setOnAction(e -> createSticker());
        toolbar.getChildren().add(btn);
    }

    private void setupKeyboardToggle() {
        Scene scene = parentContainer.getScene();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                if (selectionArea.isFocused()) {
                    if (stickerStage != null) {
                        stickerPane.getFrame().getProperties().put("showToolbar", !isSwitchDirection());
                    }
                    drawMode(null, DrawMode.SWITCH);
                    e.consume();
                }
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

    public HBox getToolbar() {
        return toolbar;
    }

    public void drawMode(Button handleButton, DrawMode selectMode) {
        drawMode = false;
        switch (selectMode) {
            case PEN, RECTANGLE, LINE -> {
                if (currentMode != selectMode) {
                    drawMode = true;
                }
            }
        }

        boolean finalDrawMode = drawMode;
        parentContainer.getChildren().forEach(it -> it.setMouseTransparent(finalDrawMode));
        drawCanvas.setMouseTransparent(!drawMode);
        subToolbar.setMouseTransparent(false);
        toolbar.setMouseTransparent(false);
        aiToolbar(selectMode, drawMode);
        if (activeButton != null) {
            activeButton.getStyleClass().remove("active");
        }

        if (drawMode) {
            colorPicker.setValue(drawCanvas.getStrokeColor());
            sizeSlider.setValue(drawCanvas.getStrokeWidth());
            handleButton.getStyleClass().add("active");
            drawCanvas.setCursor(createDirectionalCursor(point));
        } else {
            if (handleButton != null) {
                drawCanvas.setCursor(Cursor.DEFAULT);
            }
        }
        currentMode = switchMode(currentMode, selectMode);
        activeButton = handleButton;
    }

    private void createSubToolbar() {
        subToolbar.setPickOnBounds(false);
        subToolbar.setSpacing(16);
        subToolbar.setAlignment(Pos.CENTER);
        subToolbar.setCursor(Cursor.DEFAULT);

        subToolbar.getStyleClass().add("sub-toolbar");

        subToolbar.setManaged(true);
        subToolbar.setVisible(false);

        sizeSlider.setShowTickLabels(false);
        sizeSlider.setShowTickMarks(false);
        sizeSlider.valueProperty().addListener((obs, old, newSize) ->
                drawCanvas.setStrokeWidth(newSize.doubleValue())
        );

        subToolbar.getChildren().addAll(
                sizeSlider,
                createStyledSeparator(),
                createColorButtons()
        );
        updateSubToolbarPosition();
        parentContainer.getChildren().add(subToolbar);
    }

    private HBox createColorButtons() {
        HBox colorBox = new HBox(4);
        colorBox.setAlignment(Pos.CENTER);

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

        btn.setStyle(String.format("""
                -fx-background-color: %s;
                -fx-background-radius: 4;
                -fx-border-color: rgba(255,255,255,0.3);
                -fx-border-radius: 4;
                -fx-border-width: 1;
                """, toRGBA(color)));

        btn.setOnAction(e -> {
            drawCanvas.setStrokeColor(color);
            ImageCursor ic = new ImageCursor(getDrawCursor(color), 15, 15);
            drawCanvas.setCursor(ic);

            updateColorButtonsBorder(btn);
        });

        btn.setOnMouseEntered(e ->
                btn.setStyle(btn.getStyle() + "-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.3), 5, 0, 0, 0);")
        );

        btn.setOnMouseExited(e ->
                btn.setStyle(btn.getStyle().replace("-fx-effect: dropshadow(gaussian, rgba(255,255,255,0.3), 5, 0, 0, 0);", ""))
        );

        return btn;
    }

    private void updateColorButtonsBorder(Button selectedBtn) {
        for (Node node : ((HBox) selectedBtn.getParent()).getChildren()) {
            if (node instanceof Button colorBtn) {
                String style = colorBtn.getStyle();
                if (colorBtn == selectedBtn) {
                    style = style.replace("border-width: 1", "border-width: 2");
                    style = style.replace("rgba(255,255,255,0.3)", "white");
                } else {
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

    public void activateTool(Button handleButton, DrawMode selectedDrawMode) {
        drawMode(handleButton, selectedDrawMode);

        switch (selectedDrawMode) {
            case PEN -> drawCanvas.setupPenTool();
            case RECTANGLE -> drawCanvas.setupRectTool();
            case LINE -> drawCanvas.setupLineTool();
        }
    }

    private void initializeToolbar(boolean visiableToobar) {
        createButtons();
        setupBottomPositionBinding();
        setupKeyboardToggle();

        toolbar.getStyleClass().add("toolbar");
        toolbar.setPickOnBounds(false);
        toolbar.setVisible(visiableToobar);
    }

    private void setupBottomPositionBinding() {
        // 原有的绑定代码
        toolbar.layoutXProperty().bind(
                Bindings.createDoubleBinding(() -> {
                            // 计算理想居中位置
                            double idealX = selectionArea.getX() +
                                    (selectionArea.getWidth() - toolbar.getWidth());

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
    }

    private WritableImage snapshotScreen() {
        return ShotScreen.snapshotScreen(drawCanvas.getScene(), selectionArea);
    }

    private Separator createStyledSeparator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().clear();
        separator.getStyleClass().add("sepaCus");
        return separator;
    }

    private WritableImage getDrawCursor(Paint color) {
        SVGPath svg = new SVGPath();
        svg.setContent(Icon.point);
        svg.setStroke(color);
        svg.setStrokeWidth(1.5);

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return svg.snapshot(params, null);
    }

    private void aiToolbar(DrawMode selectMode, boolean drawMode) {
        if (selectMode == DrawMode.SWITCH) {
            if (subToolbar.isVisible()) {
                FadeTransition subFade = animation(subToolbar, true);
                subFade.setOnFinished(it -> animation(toolbar, switchDirection).play());
                subFade.play();
            } else {
                animation(toolbar, switchDirection).play();
            }
            switchDirection = !switchDirection;
        } else if (selectMode == DrawMode.SHOW) {
            switchDirection = false;
            FadeTransition animation = animation(toolbar, false);
            animation.setOnFinished(it -> animation(subToolbar, true).play());
            animation.play();
        } else if (selectMode == DrawMode.NONE) {
            switchDirection = true;
            FadeTransition animation = animation(toolbar, true);
            animation.setOnFinished(it -> animation(subToolbar, true).play());
            animation.play();
        } else {
            FadeTransition animation = animation(toolbar, false);
            if (drawMode) {
                animation.setOnFinished(it -> animation(subToolbar, false).play());
            } else {
                animation.setOnFinished(it -> animation(subToolbar, true).play());
            }
            animation.play();
        }
    }

    private FadeTransition animation(HBox toolbar, boolean isHide) {
        FadeTransition subFade = new FadeTransition(Duration.millis(150), toolbar);
        if (isHide) {
            if (toolbar.isVisible()) {
                subFade.setFromValue(toolbar.getOpacity());
                subFade.setToValue(0.0);
                subFade.setOnFinished(it -> {
                    toolbar.setVisible(false);
                    toolbar.setMouseTransparent(true); // 启用穿透（不接收鼠标事件）
                });
            }
        } else {
            toolbar.setVisible(true);
            toolbar.setMouseTransparent(false); // 禁用穿透（正常接收事件）

            subFade.setFromValue(toolbar.getOpacity());
            subFade.setToValue(1.0);
        }
        return subFade;
    }

    public boolean getDrawMode() {
        return drawMode;
    }

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer, DrawCanvas drawCanvasArea, ScreenshotSelector screenshotSelector, StickerPane stickerPane, boolean visiableToobar) {
        this.drawCanvas = drawCanvasArea;
        this.selectionArea = selectionArea;
        this.stickerPane = stickerPane;
        this.parentContainer = parentContainer;
        this.screenshotSelector = screenshotSelector;
        this.stickerStage = StickerStage.getInstance();  // 使用单例模式获取实例
        initializeToolbar(visiableToobar);
        createSubToolbar();
        setupStickerShortcut();  // 设置贴图快捷键
        parentContainer.getChildren().add(toolbar);
        if (screenshotSelector != null) {
            parentContainer.getChildren().remove(screenshotSelector.getMagnifier());
            parentContainer.getChildren().add(screenshotSelector.getMagnifier());
        }
    }

    private void setupStickerShortcut() {
        Scene scene = parentContainer.getScene();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F3) {
                createSticker();
                e.consume();
            }
        });
    }

    private void createSticker() {
        Pane root = screenshotSelector.getRoot();
        root.lookupAll("Circle").forEach(circle ->
                circle.setStyle("-fx-opacity: 0;"));
        root.lookupAll("#special-rect-1").forEach(rec ->
                rec.setStyle("-fx-opacity: 0;"));

        new Thread(new Task<>() {
            @Override
            protected Void call() {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }

            @Override
            protected void succeeded() {
                addSticker();
            }
        }).start();
    }

    private void addSticker() {
        WritableImage screenImage = snapshotScreen();
        stickerPane = new StickerPane(screenImage);
        // 设置贴图初始大小为选区大小
        stickerPane.setSize(selectionArea.getWidth(), selectionArea.getHeight());

        // 获取选区的屏幕坐标
        Point2D screenPoint = selectionArea.localToScreen(selectionArea.getX(), selectionArea.getY());

        // 将屏幕坐标转换为相对于贴图窗口的坐标
        Point2D stagePoint = stickerStage.getRoot().screenToLocal(screenPoint.getX(), screenPoint.getY());

        stickerPane.setPosition(stagePoint.getX(), stagePoint.getY());

        // 添加到贴图窗口并显示
        stickerStage.addSticker(stickerPane);

        // 清理截图选择器
        cancleSelection();

        stickerPane.setToolbar(stickerStage.getRoot());
    }

    private void cancleSelection() {
        if (screenshotSelector != null) {
            screenshotSelector.cancelSelection();
        }
    }

    /**
     * 销毁工具栏及其所有资源
     */
    public void destroy() {
        // 移除工具栏
        if (parentContainer != null) {
            parentContainer.getChildren().remove(toolbar);
            parentContainer.getChildren().remove(subToolbar);

            // 移除事件监听
            parentContainer.setOnMouseClicked(null);
            parentContainer.setFocusTraversable(false);
        }

        // 清理绑定
        toolbar.layoutXProperty().unbind();
        toolbar.layoutYProperty().unbind();
        subToolbar.layoutXProperty().unbind();
        subToolbar.layoutYProperty().unbind();

        // 重置工具栏状态
        drawCanvas.setCursor(Cursor.DEFAULT);
        activeButton = null;
        currentMode = DrawMode.NONE;
        drawMode = false;

        // 清空工具栏内容
        toolbar.getChildren().clear();
        subToolbar.getChildren().clear();
    }

    public boolean isSwitchDirection() {
        return switchDirection;
    }

    public void setSwitchDirection(boolean switchDirection) {
        this.switchDirection = switchDirection;
    }
}