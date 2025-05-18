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
import java.awt.*;
import java.awt.image.BufferedImage;
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
    private final DrawCanvas drawCanvas;

    // ----------------------------------------------------
    private final HBox toolbar = new HBox(8);
    private final HBox subToolbar = new HBox(10);
    private final ColorPicker colorPicker = new ColorPicker();
    private final Slider sizeSlider = new Slider(1, 20, 3);

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
            screenshotSelector.cancelSelection();
        });
        toolbar.getChildren().add(btn);
    }

    private void createSaveButton() {
        Button btn = createIconButton(Icon.save, "Save to file");
        btn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Screenshot");

            String defaultFileName = String.format("SnapSticker_%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS.png",
                    System.currentTimeMillis());

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
        btn.setOnAction(e -> System.out.println("打开贴图库"));
        toolbar.getChildren().add(btn);
    }

    private void setupKeyboardToggle() {
        Scene scene = parentContainer.getScene();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                drawMode(null, DrawMode.SWITCH);
                e.consume();
            }
        });

        parentContainer.setFocusTraversable(true);
        parentContainer.setOnMouseClicked(e -> parentContainer.requestFocus());
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
        System.out.println("selectionMode: " + currentMode + " selectMode: " + selectMode);
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

    private void initializeToolbar() {
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
    }

    private WritableImage snapshotScreen() {
        Rectangle selectionArea = screenshotSelector.getSelectionArea();

        double x = selectionArea.getX();
        double y = selectionArea.getY();
        double width = selectionArea.getWidth();
        double height = selectionArea.getHeight();

        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to create Robot instance", e);
        }

        Scene scene = drawCanvas.getScene();
        Point2D sceneCoords = scene.getRoot().localToScreen(x, y);

        java.awt.Rectangle awtRect = new java.awt.Rectangle(
                (int) sceneCoords.getX(),
                (int) sceneCoords.getY(),
                (int) width,
                (int) height
        );

        BufferedImage screenImage = robot.createScreenCapture(awtRect);

        WritableImage finalImage = new WritableImage((int) width, (int) height);
        SwingFXUtils.toFXImage(screenImage, finalImage);
        return finalImage;
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
            switchDirection = !switchDirection;
            if (subToolbar.isVisible()) {
                FadeTransition subFade = animation(subToolbar, true);
                subFade.setOnFinished(it -> animation(toolbar, switchDirection).play());
                subFade.play();
            } else {
                animation(toolbar, switchDirection).play();
            }
        } else if (selectMode == DrawMode.NONE) {
            // todo
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

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer, DrawCanvas drawCanvasArea, ScreenshotSelector screenshotSelector) {
        this.drawCanvas = drawCanvasArea;
        this.selectionArea = selectionArea;
        this.parentContainer = parentContainer;
        this.screenshotSelector = screenshotSelector;
        initializeToolbar();
        createSubToolbar();
        parentContainer.getChildren().add(toolbar);
        parentContainer.getChildren().remove(screenshotSelector.getMagnifier());
        parentContainer.getChildren().add(screenshotSelector.getMagnifier());
    }
}