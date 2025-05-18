package com.github.sticker.screenshot;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.FloatingToolbar;
import com.github.sticker.draw.Icon;
import com.github.sticker.feature.Magnifier;
import com.github.sticker.util.ScreenManager;
import com.github.sticker.util.StealthWindow;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.ImageCursor;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static com.github.sticker.draw.Icon.createDirectionalCursor;
import static javafx.scene.Cursor.DEFAULT;

/**
 * Handles the screenshot area selection functionality.
 * Creates a transparent overlay window for selecting the screenshot area.
 */
public class ScreenshotSelector {
    private final ScreenManager screenManager;
    private Stage selectorStage;
    private Pane root;
    private double startX, startY;
    private double endX, endY;
    private boolean isSelecting = false;

    // Mask layers
    private Rectangle maskTop, maskBottom, maskLeft, maskRight;
    private Rectangle selectionArea;

    // ---------------------
    private DrawCanvas drawCanvasArea; // Visual border for selection area
    private static final double MASK_OPACITY = 0.5;

    // feature
    private final Magnifier magnifier;

    // Mouse tracking
    private Timeline mouseTracker;
    private static final Duration TRACK_INTERVAL = Duration.millis(16); // ~60fps

    // Custom cursor
    private static final ImageCursor customCursor = createCustomCursor();

    // Screen and taskbar bounds
    private Rectangle2D currentScreenBounds;
    private Rectangle2D taskbarBounds;
    private boolean isTaskbarVisible;
    private Screen currentScreen;      // Track current screen

    private boolean isResizing = false;
    private String resizeDirection = ""; // 记录调整方向：n, s, e, w, ne, nw, se, sw

    private FloatingToolbar floatingToolbar;


    //  /////////////////////////////////////////////////////////////////

    private final List<Rectangle> dragAreas = new ArrayList<>();
    private final List<ChangeListener<Number>> borderListeners = new ArrayList<>();

    //  /////////////////////////////////////////////////////////////////

    /**
     * Constructor for ScreenshotSelector
     *
     * @param screenManager The screen manager instance to handle screen-related operations
     */
    public ScreenshotSelector(ScreenManager screenManager) {
        try {
            this.magnifier = new Magnifier();
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }
        this.screenManager = screenManager;
    }

    /**
     * Start the screenshot selection process
     * Creates a transparent overlay window on the current screen
     */
    public void startSelection() {
        // Reset selection state
        isSelecting = false;  // 初始状态设为false，等待实际开始选择

        // Get current screen and mouse position
        currentScreen = screenManager.getCurrentScreen();
        currentScreenBounds = currentScreen.getBounds();

        // Check taskbar status
        isTaskbarVisible = screenManager.isTaskbarVisible();
        taskbarBounds = screenManager.getTaskbarBounds();

        initRootPane();

        // Create and configure the stage
        Scene scene = initSceneAndSelectorStage();

        // Initialize mask layers
        initializeMaskLayers();

        // Set up event handlers
        setupMouseHandlers(scene);
        setupKeyboardHandlers(scene);

        // Initialize mouse tracking and show magnifier
        initializeMouseTracking();

        // Get initial mouse position and show magnifier
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        magnifier.setVisible(true);
        magnifier.update((int) mousePos.getX(), (int) mousePos.getY());
    }

    private void initRootPane() {
        root = new Pane();
        root.setCache(true);
        root.setCacheHint(CacheHint.SPEED);
        root.setStyle("-fx-background-color: transparent;");
    }

    private Scene initSceneAndSelectorStage() {
        Scene scene = initScene();
        selectorStage = new Stage();
        selectorStage.initStyle(StageStyle.TRANSPARENT);
        selectorStage.setAlwaysOnTop(true);
        selectorStage.setTitle("SnapSticker");

        // Position the stage on the screen
        selectorStage.setX(currentScreenBounds.getMinX());
        selectorStage.setY(currentScreenBounds.getMinY());

        StealthWindow.configure(selectorStage);

        selectorStage.setScene(scene);
        selectorStage.show();
        return scene;
    }

    private Scene initScene() {
        Scene scene = new Scene(root, currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
        scene.setFill(Color.rgb(0, 0, 0, 0.01));
        scene.setCursor(customCursor);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/index.css")).toExternalForm()
        );
        return scene;
    }

    public void stopMouseTracking() {
        if (mouseTracker != null) {
            mouseTracker.stop();
            mouseTracker = null;
        }
    }

    private void initializeMouseTracking() {
        mouseTracker = new Timeline(
                new KeyFrame(TRACK_INTERVAL, event -> updateScreenMask())
        );
        mouseTracker.setCycleCount(Timeline.INDEFINITE);
        mouseTracker.play();
    }

    private void updateScreenMask() {
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        double mouseX = mousePosition.getX();
        double mouseY = mousePosition.getY();

        boolean isOverTaskbar = isTaskbarVisible && taskbarBounds != null &&
                taskbarBounds.contains(mouseX, mouseY);

        // If taskbar is visible, create a cut-out for it
        if (isOverTaskbar & isTaskbarVisible && taskbarBounds != null) {
            updateDrawCanvasMask(drawCanvasArea,
                    taskbarBounds.getMinX() - currentScreenBounds.getMinX(),
                    taskbarBounds.getMinY() - currentScreenBounds.getMinY(),
                    taskbarBounds.getWidth(), taskbarBounds.getHeight());
        } else {
            // If no taskbar, just use the base mask
            updateDrawCanvasMask(drawCanvasArea, 0, 0, currentScreenBounds.getWidth(),
                    currentScreenBounds.getHeight() - taskbarBounds.getHeight());
        }
    }

    /**
     * Initialize all mask layers for the screenshot selection
     */
    private void initializeMaskLayers() {
        selectionArea = createSelectionMask();
        createSelectCalculatedMask();
        createDrawCanvsaArea();

        root.getChildren().addAll(
                drawCanvasArea,
                maskTop, maskBottom, maskLeft, maskRight,
                selectionArea, magnifier);
    }

    private void createSelectCalculatedMask() {
        maskTop = new Rectangle();
        maskBottom = new Rectangle();
        maskLeft = new Rectangle();
        maskRight = new Rectangle();
        initStyle(maskTop);
        initStyle(maskBottom);
        initStyle(maskLeft);
        initStyle(maskRight);
    }

    private void initStyle(Rectangle mask) {
        mask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
        mask.setMouseTransparent(true);
    }


    private void createDrawCanvsaArea() {
        drawCanvasArea = new DrawCanvas();
        drawCanvasArea.setLayoutX(0);
        drawCanvasArea.setLayoutY(0);
        drawCanvasArea.setPrefSize(currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
    }

    /**
     * Update the mask to cover the taskbar
     */
    private void updateDrawCanvasMask(DrawCanvas drawCanvas, double x, double y, double width, double height) {
        drawCanvas.setLayoutX(x);
        drawCanvas.setLayoutY(y);
        drawCanvas.setPrefSize(width, height);
        drawCanvas.setStyle("-fx-border-color: #1e6deb; -fx-border-width: 3;");
    }

    private void realTimeSelection() {
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        selectionArea.setX(x);
        selectionArea.setY(y);
        selectionArea.setWidth(width);
        selectionArea.setHeight(height);

        double screenWidth = currentScreenBounds.getWidth();
        double screenHeight = currentScreenBounds.getHeight();

        // 更新上方遮罩
        maskTop.setX(0);
        maskTop.setY(0);
        maskTop.setWidth(screenWidth);
        maskTop.setHeight(y);

        // 更新下方遮罩
        maskBottom.setX(0);
        maskBottom.setY(y + height);
        maskBottom.setWidth(screenWidth);
        maskBottom.setHeight(screenHeight - (y + height));

        // 更新左侧遮罩
        maskLeft.setX(0);
        maskLeft.setY(y);
        maskLeft.setWidth(x);
        maskLeft.setHeight(height);

        // 更新右侧遮罩
        maskRight.setX(x + width);
        maskRight.setY(y);
        maskRight.setWidth(screenWidth - (x + width));
        maskRight.setHeight(height);
    }

    /**
     * Create the selection area mask
     *
     * @return Rectangle representing the selection mask
     */
    private Rectangle createSelectionMask() {
        Rectangle mask = new Rectangle();
        mask.setCache(true);
        mask.setCacheHint(CacheHint.SPEED);
        mask.setFill(Color.rgb(0, 0, 0, 0.01));
        mask.setStroke(Color.rgb(0, 99, 250, 1));
        mask.setStrokeWidth(2);
        mask.setStrokeType(StrokeType.OUTSIDE);
        return mask;
    }

    /**
     * Set up mouse event handlers for the selection process
     *
     * @param scene The scene to attach the handlers to
     */
    private void setupMouseHandlers(Scene scene) {
        scene.setOnMouseMoved(e -> {
            if (isSelecting) {
                boolean drawMode = floatingToolbar != null && floatingToolbar.getDrawMode();
                if (drawMode) {
                    magnifier.switchShowMagnifier(e, false);
                } else {
                    boolean isInside = isMouseInSelectionArea(e.getX(), e.getY());
                    magnifier.switchShowMagnifier(e, isInside);
                }
            } else {
                magnifier.switchShowMagnifier(e, true);
            }
        });

        scene.setOnMousePressed(event -> {
            magnifier.switchShowMagnifier(event, !(floatingToolbar != null && floatingToolbar.getDrawMode()));
            if (!isSelecting) {
                drawCanvasArea.setStyle(null);
                stopMouseTracking();

                startX = event.getScreenX();
                startY = event.getScreenY();
            }
        });

        scene.setOnMouseDragged(event -> {
            magnifier.switchShowMagnifier(event, !(floatingToolbar != null && floatingToolbar.getDrawMode()));
            if (!isSelecting) {
                endX = event.getScreenX();
                endY = event.getScreenY();
                realTimeSelection();
            }
        });

        scene.setOnMouseReleased(event -> {
            if (!isSelecting) {
                handleMouseReleased(event);
                // 检查鼠标是否在选区内
                boolean isInside = isMouseInSelectionArea(event.getX(), event.getY());
                magnifier.switchShowMagnifier(event, isInside);
                isSelecting = true;
            }
        });
    }

    private boolean isMouseInSelectionArea(double mouseX, double mouseY) {
        if (selectionArea == null) return false;

        double x = selectionArea.getX();
        double y = selectionArea.getY();
        double width = selectionArea.getWidth();
        double height = selectionArea.getHeight();

        return mouseX >= x && mouseX <= (x + width) &&
                mouseY >= y && mouseY <= (y + height);
    }

    private void handleMouseReleased(javafx.scene.input.MouseEvent event) {
        endX = event.getScreenX();
        endY = event.getScreenY();
        isSelecting = false;  // 选择完成，重置状态

        updateSelectionAreaPosition();
        setupDragHandlers();

        if (floatingToolbar != null) {
            root.getChildren().remove(floatingToolbar.getToolbar());
            floatingToolbar = null;
        }
        floatingToolbar = new FloatingToolbar(selectionArea, root, drawCanvasArea, this);
    }

    private void updateSelectionAreaPosition() {
        double currentWidth = selectionArea.getWidth();
        double currentHeight = selectionArea.getHeight();

        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        if (currentWidth <= 0 || currentHeight <= 0) {
            selectionArea.setWidth(width);
            selectionArea.setHeight(height);
        } else {
            selectionArea.setWidth(currentWidth);
            selectionArea.setHeight(currentHeight);
        }

        selectionArea.setX(x);
        selectionArea.setY(y);
    }

    /**
     * 设置拖动事件处理器
     */
    public void setupDragHandlers() {
        final double[] dragDelta = new double[2];

        // Clear existing drag areas first
        root.getChildren().removeAll(dragAreas);
        dragAreas.clear();

        Rectangle topArea = new Rectangle();
        Rectangle bottomArea = new Rectangle();
        Rectangle leftArea = new Rectangle();
        Rectangle rightArea = new Rectangle();
        Rectangle topLeftArea = new Rectangle();
        Rectangle topRightArea = new Rectangle();
        Rectangle bottomLeftArea = new Rectangle();
        Rectangle bottomRightArea = new Rectangle();

        dragAreas.addAll(Arrays.asList(
                topArea, bottomArea, leftArea, rightArea,
                topLeftArea, topRightArea, bottomLeftArea, bottomRightArea
        ));

        // 设置检测区域为透明
        for (Rectangle area : dragAreas) {
            area.setFill(Color.TRANSPARENT);
            area.setMouseTransparent(false);
        }

        // 更新检测区域位置和大小的函数
        Consumer<Rectangle> updateAreas = rect -> {
            double x = rect.getX();
            double y = rect.getY();
            double width = rect.getWidth();
            double height = rect.getHeight();
            double screenWidth = currentScreenBounds.getWidth();
            double screenHeight = currentScreenBounds.getHeight();

            // 设置上下区域
            topArea.setX(0);
            topArea.setY(0);
            topArea.setWidth(screenWidth);
            topArea.setHeight(y);

            bottomArea.setX(0);
            bottomArea.setY(y + height);
            bottomArea.setWidth(screenWidth);
            bottomArea.setHeight(screenHeight - (y + height));

            // 设置左右区域
            leftArea.setX(0);
            leftArea.setY(y);
            leftArea.setWidth(x);
            leftArea.setHeight(height);

            rightArea.setX(x + width);
            rightArea.setY(y);
            rightArea.setWidth(screenWidth - (x + width));
            rightArea.setHeight(height);

            // 设置角落区域
            double cornerSize = Math.min(width, height) / 2;
            topLeftArea.setX(0);
            topLeftArea.setY(0);
            topLeftArea.setWidth(x);
            topLeftArea.setHeight(y);

            topRightArea.setX(x + width);
            topRightArea.setY(0);
            topRightArea.setWidth(screenWidth - (x + width));
            topRightArea.setHeight(y);

            bottomLeftArea.setX(0);
            bottomLeftArea.setY(y + height);
            bottomLeftArea.setWidth(x);
            bottomLeftArea.setHeight(screenHeight - (y + height));

            bottomRightArea.setX(x + width);
            bottomRightArea.setY(y + height);
            bottomRightArea.setWidth(screenWidth - (x + width));
            bottomRightArea.setHeight(screenHeight - (y + height));
        };

        // Remove any existing listeners
        for (ChangeListener<Number> listener : borderListeners) {
            selectionArea.xProperty().removeListener(listener);
            selectionArea.yProperty().removeListener(listener);
            selectionArea.widthProperty().removeListener(listener);
            selectionArea.heightProperty().removeListener(listener);
        }
        borderListeners.clear();

        ChangeListener<Number> positionListener =
                (obs, oldVal, newVal) -> updateAreas.accept(selectionArea);
        borderListeners.add(positionListener);

        // Add the listeners
        selectionArea.xProperty().addListener(positionListener);
        selectionArea.yProperty().addListener(positionListener);
        selectionArea.widthProperty().addListener(positionListener);
        selectionArea.heightProperty().addListener(positionListener);

        // Add drag areas to scene if they're not already there
        root.getChildren().addAll(dragAreas);

        // 设置事件处理器
        setupAreaEvents(topArea, "n", CURSOR_N, dragDelta);
        setupAreaEvents(bottomArea, "s", CURSOR_S, dragDelta);
        setupAreaEvents(leftArea, "w", CURSOR_W, dragDelta);
        setupAreaEvents(rightArea, "e", CURSOR_E, dragDelta);
        setupAreaEvents(topLeftArea, "nw", CURSOR_NW, dragDelta);
        setupAreaEvents(topRightArea, "ne", CURSOR_NE, dragDelta);
        setupAreaEvents(bottomLeftArea, "sw", CURSOR_SW, dragDelta);
        setupAreaEvents(bottomRightArea, "se", CURSOR_SE, dragDelta);

        updateAreas.accept(selectionArea);

        selectionArea.setOnMouseMoved(e -> {
            selectionArea.getScene().setCursor(CURSOR_MOVE);
        });

        selectionArea.setOnMousePressed(e -> {
            magnifier.switchShowMagnifier(e, false);
            dragDelta[0] = e.getSceneX() - selectionArea.getX();
            dragDelta[1] = e.getSceneY() - selectionArea.getY();
            e.consume();
        });

        selectionArea.setOnMouseDragged(e -> {
            handleDrag(e, dragDelta);
            e.consume();
        });

        selectionArea.setOnMouseReleased(e -> {
            magnifier.switchShowMagnifier(e, true);
            isResizing = false;
            resizeDirection = "";
        });
    }

    private void setupAreaEvents(Rectangle area, String direction, javafx.scene.Cursor cursor, double[] dragDelta) {
        area.setOnMouseEntered(e -> {
            area.getScene().setCursor(cursor);
            e.consume();
        });

        area.setOnMouseExited(e -> {
            area.getScene().setCursor(cursor);
            e.consume();
        });

        area.setOnMousePressed(e -> {
            isResizing = true;
            resizeDirection = direction;

            // Get current mouse position and selection area dimensions
            double mouseX = e.getScreenX();
            double mouseY = e.getScreenY();
            double width = selectionArea.getWidth();
            double height = selectionArea.getHeight();
            double x = selectionArea.getX();
            double y = selectionArea.getY();

            // Adjust selection area based on which edge/corner was clicked
            switch (direction) {
                case "n" -> { // Top edge
                    double deltaY = mouseY - y;
                    selectionArea.setY(mouseY);
                    selectionArea.setHeight(height - deltaY);
                }
                case "s" -> { // Bottom edge
                    selectionArea.setHeight(mouseY - y);
                }
                case "e" -> { // Right edge
                    selectionArea.setWidth(mouseX - x);
                }
                case "w" -> { // Left edge
                    double deltaX = mouseX - x;
                    selectionArea.setX(mouseX);
                    selectionArea.setWidth(width - deltaX);
                }
                case "ne" -> { // Top-right corner
                    double deltaY = mouseY - y;
                    selectionArea.setY(mouseY);
                    selectionArea.setHeight(height - deltaY);
                    selectionArea.setWidth(mouseX - x);
                }
                case "nw" -> { // Top-left corner
                    double deltaY = mouseY - y;
                    double deltaX = mouseX - x;
                    selectionArea.setX(mouseX);
                    selectionArea.setY(mouseY);
                    selectionArea.setWidth(width - deltaX);
                    selectionArea.setHeight(height - deltaY);
                }
                case "se" -> { // Bottom-right corner
                    selectionArea.setWidth(mouseX - x);
                    selectionArea.setHeight(mouseY - y);
                }
                case "sw" -> { // Bottom-left corner
                    double deltaX = mouseX - x;
                    selectionArea.setX(mouseX);
                    selectionArea.setWidth(width - deltaX);
                    selectionArea.setHeight(mouseY - y);
                }
            }

            // Update coordinates for real-time selection
            startX = selectionArea.getX();
            startY = selectionArea.getY();
            endX = startX + selectionArea.getWidth();
            endY = startY + selectionArea.getHeight();

            // Update masks
            realTimeSelection();

            // Update drag delta for subsequent drag operations
            dragDelta[0] = e.getSceneX();
            dragDelta[1] = e.getSceneY();

            area.getScene().setCursor(cursor);
            e.consume();
        });

        area.setOnMouseDragged(e -> {
            if (isResizing) {
                handleResize(e, dragDelta);
            }
            e.consume();
        });

        area.setOnMouseReleased(e -> {
            isResizing = false;
            resizeDirection = "";
            e.consume();
        });
    }

    private void handleResize(javafx.scene.input.MouseEvent e, double[] dragDelta) {
        double deltaX = e.getSceneX() - dragDelta[0];
        double deltaY = e.getSceneY() - dragDelta[1];
        double newX = selectionArea.getX();
        double newY = selectionArea.getY();
        double newWidth = selectionArea.getWidth();
        double newHeight = selectionArea.getHeight();
        boolean flipped = false;

        // 根据调整方向更新位置和大小
        switch (resizeDirection) {
            case "n": // 上边框
                if (deltaY > newHeight - 10) {
                    // 向下翻转
                    resizeDirection = "s";
                    flipped = true;
                    newY = newY + newHeight;
                    newHeight = 10;
                } else {
                    newY += deltaY;
                    newHeight -= deltaY;
                }
                break;
            case "s": // 下边框
                newHeight += deltaY;
                if (newHeight < 10) {
                    // 向上翻转
                    newHeight = 10;
                    resizeDirection = "n";
                    flipped = true;
                    newY = newY + newHeight - 10;
                }
                break;
            case "e": // 右边框
                newWidth += deltaX;
                if (newWidth < 10) {
                    // 向左翻转
                    newWidth = 10;
                    resizeDirection = "w";
                    flipped = true;
                    newX = newX + newWidth - 10;
                }
                break;
            case "w": // 左边框
                if (deltaX > newWidth - 10) {
                    // 向右翻转
                    resizeDirection = "e";
                    flipped = true;
                    newX = newX + newWidth;
                    newWidth = 10;
                } else {
                    newX += deltaX;
                    newWidth -= deltaX;
                }
                break;
            case "ne": // 右上角
                // 处理上边框
                if (deltaY > newHeight - 10) {
                    // 向下翻转
                    resizeDirection = "se";
                    flipped = true;
                    newY = newY + newHeight;
                    newHeight = 10;
                } else {
                    newY += deltaY;
                    newHeight -= deltaY;
                }
                // 处理右边框
                newWidth += deltaX;
                if (newWidth < 10) {
                    // 向左翻转
                    newWidth = 10;
                    resizeDirection = "nw";
                    flipped = true;
                    newX = newX + newWidth - 10;
                }
                break;
            case "nw": // 左上角
                // 处理上边框
                if (deltaY > newHeight - 10) {
                    // 向下翻转
                    resizeDirection = "sw";
                    flipped = true;
                    newY = newY + newHeight;
                    newHeight = 10;
                } else {
                    newY += deltaY;
                    newHeight -= deltaY;
                }
                // 处理左边框
                if (deltaX > newWidth - 10) {
                    // 向右翻转
                    resizeDirection = "ne";
                    flipped = true;
                    newX = newX + newWidth;
                    newWidth = 10;
                } else {
                    newX += deltaX;
                    newWidth -= deltaX;
                }
                break;
            case "se": // 右下角
                // 处理下边框
                newHeight += deltaY;
                if (newHeight < 10) {
                    // 向上翻转
                    newHeight = 10;
                    resizeDirection = "ne";
                    flipped = true;
                    newY = newY + newHeight - 10;
                }
                // 处理右边框
                newWidth += deltaX;
                if (newWidth < 10) {
                    // 向左翻转
                    newWidth = 10;
                    resizeDirection = "sw";
                    flipped = true;
                    newX = newX + newWidth - 10;
                }
                break;
            case "sw": // 左下角
                // 处理下边框
                newHeight += deltaY;
                if (newHeight < 10) {
                    // 向上翻转
                    newHeight = 10;
                    resizeDirection = "nw";
                    flipped = true;
                    newY = newY + newHeight - 10;
                }
                // 处理左边框
                if (deltaX > newWidth - 10) {
                    // 向右翻转
                    resizeDirection = "se";
                    flipped = true;
                    newX = newX + newWidth;
                    newWidth = 10;
                } else {
                    newX += deltaX;
                    newWidth -= deltaX;
                }
                break;
        }

        // 更新选择区域
        selectionArea.setX(newX);
        selectionArea.setY(newY);
        selectionArea.setWidth(newWidth);
        selectionArea.setHeight(newHeight);

        // 更新起点和终点坐标，用于realTimeSelection
        startX = newX;
        startY = newY;
        endX = newX + newWidth;
        endY = newY + newHeight;

        // 实时更新遮罩
        realTimeSelection();

        // 更新拖动起点
        dragDelta[0] = e.getSceneX();
        dragDelta[1] = e.getSceneY();

        // 如果发生翻转，更新光标
        if (flipped) {
            selectionArea.getScene().setCursor(getResizeCursor(resizeDirection));
        }
    }

    private void handleDrag(javafx.scene.input.MouseEvent e, double[] dragDelta) {
        double newX = e.getSceneX() - dragDelta[0];
        double newY = e.getSceneY() - dragDelta[1];

        // 限制边界（不能移出屏幕）
        Rectangle2D screenBounds = currentScreen.getBounds();
        double minX = screenBounds.getMinX();
        double minY = screenBounds.getMinY();
        double maxX = screenBounds.getMaxX() - selectionArea.getWidth();
        double maxY = screenBounds.getMaxY() - selectionArea.getHeight();

        double finalX = Math.max(minX, Math.min(maxX, newX));
        double finalY = Math.max(minY, Math.min(maxY, newY));

        selectionArea.setX(finalX);
        selectionArea.setY(finalY);

        // 更新起点和终点坐标，用于realTimeSelection
        startX = finalX;
        startY = finalY;
        endX = finalX + selectionArea.getWidth();
        endY = finalY + selectionArea.getHeight();

        // 实时更新遮罩
        realTimeSelection();
    }

    private javafx.scene.Cursor getResizeCursor(String direction) {
        return switch (direction) {
            case "n" -> CURSOR_N;
            case "s" -> CURSOR_S;
            case "e" -> CURSOR_E;
            case "w" -> CURSOR_W;
            case "ne" -> CURSOR_NE;
            case "nw" -> CURSOR_NW;
            case "se" -> CURSOR_SE;
            case "sw" -> CURSOR_SW;
            default -> DEFAULT;
        };
    }

    /**
     * Set up keyboard event handlers
     *
     * @param scene The scene to attach the handlers to
     */
    private void setupKeyboardHandlers(Scene scene) {
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ESCAPE:
                    cancelSelection();
                    break;
                case ENTER:
                    if (!isSelecting) {
                        finishSelection();
                    }
                    break;
            }
        });
    }

    /**
     * Finish the selection process and capture the selected area
     */
    private void finishSelection() {
        cleanup();
        // The actual screenshot capture will be implemented here
    }

    /**
     * Check if screenshot selection is in progress
     *
     * @return true if selection is active, false otherwise
     */
    public boolean isSelecting() {
        return isSelecting;
    }

    /**
     * Cancel the screenshot selection process
     * Cleans up resources and closes the selection window
     */
    public void cancelSelection() {
        cleanup();
    }

    public Rectangle getSelectionArea() {
        return selectionArea;
    }

    public Magnifier getMagnifier() {
        return magnifier;
    }

    private static ImageCursor createCustomCursor() {
        return createDirectionalCursor(Icon.point);
    }

    /**
     * Clean up resources before reinitializing
     */
    public void cleanup() {
        stopMouseTracking();
        magnifier.setVisible(false);
        if (selectorStage != null) {
            selectorStage.close();
            selectorStage = null;
        }
        if (root != null) {
            root.getChildren().clear();
            root = null;
        }
        selectionArea = null;
        drawCanvasArea = null;
        isSelecting = false;
        isResizing = false;
        maskTop = null;
        maskBottom = null;
        maskLeft = null;
        maskRight = null;
        resizeDirection = "";
    }

    private static final ImageCursor CURSOR_N = createDirectionalCursor(Icon.arrowUp);
    private static final ImageCursor CURSOR_S = createDirectionalCursor(Icon.arrowDown);
    private static final ImageCursor CURSOR_E = createDirectionalCursor(Icon.arrowRight);
    private static final ImageCursor CURSOR_W = createDirectionalCursor(Icon.arrowLeft);
    private static final ImageCursor CURSOR_NE = createDirectionalCursor(Icon.arrowUpRight);
    private static final ImageCursor CURSOR_NW = createDirectionalCursor(Icon.arrowUpLeft);
    private static final ImageCursor CURSOR_SE = createDirectionalCursor(Icon.arrowDownRight);
    private static final ImageCursor CURSOR_SW = createDirectionalCursor(Icon.arrowDownLeft);
    private static final ImageCursor CURSOR_MOVE = createDirectionalCursor(Icon.arrowsPointingOut);
} 