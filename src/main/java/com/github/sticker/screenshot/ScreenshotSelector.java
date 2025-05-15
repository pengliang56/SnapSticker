package com.github.sticker.screenshot;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.FloatingToolbar;
import com.github.sticker.draw.Icon;
import com.github.sticker.util.ScreenManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private Rectangle fullscreenMask;        // Base semi-transparent mask
    private Rectangle selectionArea;   // Selection area mask
    private Rectangle selectionBorder; // Visual border for selection area
    private DrawCanvas drawCanvasArea; // Visual border for selection area
    private static final double MASK_OPACITY = 0.5;

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

    private static final double RESIZE_THRESHOLD = 5.0; // 边框调整的检测范围
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
        this.screenManager = screenManager;
    }

    /**
     * Start the screenshot selection process
     * Creates a transparent overlay window on the current screen
     */
    public void startSelection() {
        // Reset selection state
        isSelecting = true;

        // Get current screen and mouse position
        currentScreen = screenManager.getCurrentScreen();
        currentScreenBounds = currentScreen.getBounds();

        // Check taskbar status
        isTaskbarVisible = screenManager.isTaskbarVisible();
        taskbarBounds = screenManager.getTaskbarBounds();

        initRootPane();

        // Create and configure the stage
        Scene scene = initSceneAndSelectorStage();
        scene.setCursor(customCursor);

        // Initialize mask layers
        initializeMaskLayers();

        // Set up event handlers
        setupMouseHandlers(scene);
        setupKeyboardHandlers(scene);

        initializeMouseTracking();
    }

    private void initRootPane() {
        root = new Pane();
        root.setCache(true);
        root.setCacheHint(CacheHint.SPEED);
        root.setStyle("-fx-background-color: transparent;");
    }

    private Scene initSceneAndSelectorStage() {
        selectorStage = new Stage();
        selectorStage.initStyle(StageStyle.TRANSPARENT);
        selectorStage.setAlwaysOnTop(true);

        // Create the scene with screen dimensions
        Scene scene = new Scene(root, currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
        scene.setFill(Color.TRANSPARENT);

        // Position the stage on the screen
        selectorStage.setX(currentScreenBounds.getMinX());
        selectorStage.setY(currentScreenBounds.getMinY());
        selectorStage.setScene(scene);

        // Show the stage and start tracking
        selectorStage.show();
        return scene;
    }

    /**
     * Initialize mouse tracking functionality
     */
    private void initializeMouseTracking() {
        mouseTracker = new Timeline(
                new KeyFrame(TRACK_INTERVAL, event -> updateMaskBasedOnMousePosition())
        );
        mouseTracker.setCycleCount(Timeline.INDEFINITE);
        mouseTracker.play();
    }

    /**
     * Update the mask based on current mouse position
     */
    private void updateMaskBasedOnMousePosition() {
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        double mouseX = mousePosition.getX();
        double mouseY = mousePosition.getY();

        // Check if mouse has moved to a different screen
        Screen newScreen = screenManager.getCurrentScreen();
        if (newScreen != currentScreen) {
            // Mouse has moved to a different screen, reinitialize the selector
            cleanup();
            startSelection();
            return;
        }

        // Check if mouse is over taskbar
        boolean isOverTaskbar = isTaskbarVisible && taskbarBounds != null &&
                taskbarBounds.contains(mouseX, mouseY);

        // Update mask based on mouse position
        if (isOverTaskbar) {
            updateTaskbarMask();
        } else {
            updateScreenMask(false);
        }
    }

    public void stopMouseTracking() {
        if (mouseTracker != null) {
            mouseTracker.stop();
            mouseTracker = null;
        }
    }

    /**
     * Clean up resources before reinitializing
     */
    public void cleanup() {
        stopMouseTracking();
        if (selectorStage != null) {
            selectorStage.close();
            selectorStage = null;
        }
        if (root != null) {
            root.getChildren().clear();
            root = null;
        }
        fullscreenMask = null;
        selectionArea = null;
        selectionBorder = null;
        drawCanvasArea = null;
        isSelecting = false;
        isResizing = false;
        resizeDirection = "";
    }

    /**
     * Initialize all mask layers for the screenshot selection
     */
    private void initializeMaskLayers() {
        // Create and add all mask layers
        fullscreenMask = createBaseMask();
        selectionArea = createSelectionMask();
        selectionBorder = createSelectionBorder();
        createDrawCanvsaArea();
        root.getChildren().addAll(drawCanvasArea, fullscreenMask, selectionArea, selectionBorder);

        // Initial mask update based on mouse position
        updateMaskBasedOnMousePosition();
    }

    private void createDrawCanvsaArea() {
        drawCanvasArea = new DrawCanvas();
        drawCanvasArea.setLayoutX(0);
        drawCanvasArea.setLayoutY(0);
        drawCanvasArea.setPrefSize(currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
        drawCanvasArea.setMouseTransparent(true);
    }

    /**
     * Create the base semi-transparent mask
     *
     * @return Rectangle representing the base mask
     */
    private Rectangle createBaseMask() {
        Rectangle mask = new Rectangle();
        mask.setFill(Color.color(0, 0, 0, 0));
        mask.setMouseTransparent(false);
        return mask;
    }

    /**
     * 处理遮罩点击事件
     */
    private void handleMaskClick(javafx.scene.input.MouseEvent event) {
        System.out.println("handleMaskClick(javafx.scene.input.MouseEvent event) ");
        if (!isSelecting && selectionArea != null) {
            double clickX = event.getScreenX();
            double clickY = event.getScreenY();

            // 获取当前选择区域的尺寸
            double currentWidth = selectionArea.getWidth();
            double currentHeight = selectionArea.getHeight();

            // 计算新的选择区域边界
            double newStartX = Math.min(selectionArea.getX(), clickX);
            double newStartY = Math.min(selectionArea.getY(), clickY);
            double newEndX = Math.max(selectionArea.getX() + currentWidth, clickX);
            double newEndY = Math.max(selectionArea.getY() + currentHeight, clickY);

            // 更新选择区域
            selectionArea.setX(newStartX);
            selectionArea.setY(newStartY);
            selectionArea.setWidth(newEndX - newStartX);
            selectionArea.setHeight(newEndY - newStartY);

            // 更新边框
            selectionBorder.setX(newStartX);
            selectionBorder.setY(newStartY);
            selectionBorder.setWidth(newEndX - newStartX);
            selectionBorder.setHeight(newEndY - newStartY);

            // 更新遮罩
            selectClip();
        }
    }

    /**
     * Update the mask to cover the taskbar
     */
    private void updateTaskbarMask() {
        if (!isTaskbarVisible || taskbarBounds == null) {
            updateScreenMask(false);
            return;
        }

        // Convert screen coordinates to scene coordinates
        double x = taskbarBounds.getMinX() - currentScreenBounds.getMinX();
        double y = taskbarBounds.getMinY() - currentScreenBounds.getMinY();

        // Create taskbar mask
        updateFullscreen(x, y, taskbarBounds.getWidth(), taskbarBounds.getHeight());

        // Update the mask in the scene
        Shape mask = Shape.subtract(fullscreenMask, selectionArea);
        addBorder(mask);
        root.getChildren().set(1, mask);
    }

    private void addBorder(Shape mask) {
        mask.setFill(Color.color(0, 0, 0, 0.01));
        mask.setMouseTransparent(false);
        mask.setStroke(Color.RED);
        mask.setStrokeWidth(2);
        mask.setStrokeType(StrokeType.INSIDE);
    }

    /**
     * Update the mask to cover the screen (excluding taskbar)
     */
    private void updateScreenMask(boolean ignoreTaskbar) {
        // Set base mask to cover entire screen
        updateFullscreen(0, 0, currentScreenBounds.getWidth(), currentScreenBounds.getHeight());

        // If taskbar is visible, create a cut-out for it
        if (isTaskbarVisible && taskbarBounds != null && !ignoreTaskbar) {
            // Convert taskbar coordinates to scene coordinates
            double taskbarX = taskbarBounds.getMinX() - currentScreenBounds.getMinX();
            double taskbarY = taskbarBounds.getMinY() - currentScreenBounds.getMinY();

            // Create a cut-out for the taskbar
            Rectangle taskbarCutout = new Rectangle(
                    taskbarX,
                    taskbarY,
                    taskbarBounds.getWidth(),
                    taskbarBounds.getHeight()
            );
            // Subtract the taskbar area from the base mask
            Shape mask = Shape.subtract(fullscreenMask, taskbarCutout);
            addBorder(mask);
            root.getChildren().set(1, mask);
        } else {
            // If no taskbar, just use the base mask
            Shape mask = Shape.subtract(fullscreenMask, selectionArea);
            addBorder(mask);
            root.getChildren().set(1, mask);
        }
    }

    /**
     * Create the selection area mask
     *
     * @return Rectangle representing the selection mask
     */
    private Rectangle createSelectionMask() {
        Rectangle mask = new Rectangle();
        mask.setFill(Color.TRANSPARENT);
        mask.setMouseTransparent(true);
        return mask;
    }

    private Rectangle createSelectionBorder() {
        Rectangle border = new Rectangle();
        border.setFill(Color.rgb(0, 0, 0, 0.01));
        border.setStroke(Color.rgb(0, 120, 215));
        border.setStrokeWidth(2);
        border.setStrokeType(StrokeType.OUTSIDE);
        return border;
    }

    /**
     * Update the selection overlay during dragging
     */
    private void updateSelectionOverlay() {
        // Calculate selection bounds
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        // Update all mask layers
        updateSelectionMask(x, y, width, height);
        updateSelectionBorder(x, y, width, height);
        selectClip();
    }

    private void updateFullscreen(double x, double y, double width, double height) {
        fullscreenMask.setX(x);
        fullscreenMask.setY(y);
        fullscreenMask.setWidth(width);
        fullscreenMask.setHeight(height);
    }

    /**
     * Update the selection mask position and size
     *
     * @param x      X coordinate
     * @param y      Y coordinate
     * @param width  Width of selection
     * @param height Height of selection
     */
    private void updateSelectionMask(double x, double y, double width, double height) {
        selectionArea.setX(x);
        selectionArea.setY(y);
        selectionArea.setWidth(width);
        selectionArea.setHeight(height);
    }

    private void updateSelectionBorder(double x, double y, double width, double height) {
        selectionBorder.setX(x);
        selectionBorder.setY(y);
        selectionBorder.setWidth(width);
        selectionBorder.setHeight(height);
    }

    /**
     * Update the base mask by creating a "cut out" effect
     */
    private void selectClip() {
        Shape clip = Shape.subtract(fullscreenMask, selectionArea);
        clip.setFill(Color.color(0, 0, 0, MASK_OPACITY));
        root.getChildren().set(1, clip);
    }

    /**
     * Set up mouse event handlers for the selection process
     *
     * @param scene The scene to attach the handlers to
     */
    private void setupMouseHandlers(Scene scene) {
        scene.setOnMousePressed(event -> {
            if (isSelecting) {
                stopMouseTracking();
                fullscreenMask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
                startX = event.getScreenX();
                startY = event.getScreenY();
            }
        });

        scene.setOnMouseDragged(event -> {
            if (isSelecting) {
                root.setClip(null);
                updateFullscreen(0, 0, currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
                root.getChildren().set(1, fullscreenMask);

                endX = event.getScreenX();
                endY = event.getScreenY();
                updateSelectionOverlay();
            }
        });

        scene.setOnMouseReleased(event -> {
            if (isSelecting) {
                handleMouseReleased(event, scene);
            }
        });
    }

    private void handleMouseReleased(javafx.scene.input.MouseEvent event, Scene scene) {
        scene.setCursor(DEFAULT);
        endX = event.getScreenX();
        endY = event.getScreenY();
        isSelecting = false;

        updateSelectionAreaPosition();

        // 设置新的处理器
        setupDragHandlers();

        if (floatingToolbar != null) {
            root.getChildren().remove(floatingToolbar.getToolbar());
            floatingToolbar = null;
        }
        floatingToolbar = new FloatingToolbar(selectionBorder, root, drawCanvasArea, this);
    }

    /**
     * 更新选择区域的位置和大小
     */
    private void updateSelectionAreaPosition() {
        // 保存当前尺寸
        double currentWidth = selectionArea.getWidth();
        double currentHeight = selectionArea.getHeight();

        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        // 如果是新的选择（宽度或高度为0），使用计算出的尺寸
        // 否则保持原有尺寸
        if (currentWidth <= 0 || currentHeight <= 0) {
            selectionArea.setWidth(width);
            selectionArea.setHeight(height);
            selectionBorder.setWidth(width);
            selectionBorder.setHeight(height);
        } else {
            selectionArea.setWidth(currentWidth);
            selectionArea.setHeight(currentHeight);
            selectionBorder.setWidth(currentWidth);
            selectionBorder.setHeight(currentHeight);
        }

        selectionArea.setX(x);
        selectionArea.setY(y);
        selectionBorder.setX(x);
        selectionBorder.setY(y);
    }

    /**
     * 设置拖动事件处理器
     */
    public void setupDragHandlers() {
        final double[] dragDelta = new double[2];

        // 创建全屏检测区域
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

        ChangeListener<Number> positionListener =
                (obs, oldVal, newVal)
                        -> updateAreas.accept(selectionBorder);

        // 添加检测区域到场景
        root.getChildren().addAll(dragAreas);

        // 设置检测区域事件
        setupAreaEvents(topArea, "n", CURSOR_N, dragDelta);
        setupAreaEvents(bottomArea, "s", CURSOR_S, dragDelta);
        setupAreaEvents(leftArea, "w", CURSOR_W, dragDelta);
        setupAreaEvents(rightArea, "e", CURSOR_E, dragDelta);
        setupAreaEvents(topLeftArea, "nw", CURSOR_NW, dragDelta);
        setupAreaEvents(topRightArea, "ne", CURSOR_NE, dragDelta);
        setupAreaEvents(bottomLeftArea, "sw", CURSOR_SW, dragDelta);
        setupAreaEvents(bottomRightArea, "se", CURSOR_SE, dragDelta);

        // 更新检测区域位置
        selectionBorder.xProperty().addListener(positionListener);
        selectionBorder.yProperty().addListener(positionListener);
        selectionBorder.widthProperty().addListener(positionListener);
        selectionBorder.heightProperty().addListener(positionListener);

        // 初始更新检测区域位置
        updateAreas.accept(selectionBorder);

        // 设置选择区域的鼠标进入/退出事件
        selectionBorder.setOnMouseMoved(e -> {
            selectionBorder.getScene().setCursor(CURSOR_MOVE);
        });

        selectionBorder.setOnMousePressed(e -> {
            dragDelta[0] = e.getSceneX() - selectionBorder.getX();
            dragDelta[1] = e.getSceneY() - selectionBorder.getY();
            e.consume();
        });

        selectionBorder.setOnMouseDragged(e -> {
            handleDrag(e, dragDelta);
            e.consume();
        });

        selectionBorder.setOnMouseReleased(e -> {
            isResizing = false;
            resizeDirection = "";
        });
    }

    private void setupAreaEvents(Rectangle area, String direction, javafx.scene.Cursor cursor, double[] dragDelta) {
        area.setOnMouseMoved(e -> {
            area.getScene().setCursor(cursor);
        });

        area.setOnMousePressed(e -> {
            isResizing = true;
            resizeDirection = direction;
            dragDelta[0] = e.getSceneX();
            dragDelta[1] = e.getSceneY();
            area.getScene().setCursor(cursor);

            // 立即调整到鼠标位置
            double mouseX = e.getSceneX();
            double mouseY = e.getSceneY();
            double currentX = selectionBorder.getX();
            double currentY = selectionBorder.getY();
            double currentWidth = selectionBorder.getWidth();
            double currentHeight = selectionBorder.getHeight();

            switch (direction) {
                case "n":
                    selectionBorder.setY(mouseY);
                    selectionBorder.setHeight(currentHeight + (currentY - mouseY));
                    selectionArea.setY(mouseY);
                    selectionArea.setHeight(currentHeight + (currentY - mouseY));
                    break;
                case "s":
                    selectionBorder.setHeight(mouseY - currentY);
                    selectionArea.setHeight(mouseY - currentY);
                    break;
                case "e":
                    selectionBorder.setWidth(mouseX - currentX);
                    selectionArea.setWidth(mouseX - currentX);
                    break;
                case "w":
                    selectionBorder.setX(mouseX);
                    selectionBorder.setWidth(currentWidth + (currentX - mouseX));
                    selectionArea.setX(mouseX);
                    selectionArea.setWidth(currentWidth + (currentX - mouseX));
                    break;
                case "ne":
                    // 调整上边框
                    selectionBorder.setY(mouseY);
                    selectionBorder.setHeight(currentHeight + (currentY - mouseY));
                    selectionArea.setY(mouseY);
                    selectionArea.setHeight(currentHeight + (currentY - mouseY));
                    // 调整右边框
                    selectionBorder.setWidth(mouseX - currentX);
                    selectionArea.setWidth(mouseX - currentX);
                    break;
                case "nw":
                    selectionBorder.setX(mouseX);
                    selectionBorder.setY(mouseY);
                    selectionBorder.setWidth(currentWidth + (currentX - mouseX));
                    selectionBorder.setHeight(currentHeight + (currentY - mouseY));
                    selectionArea.setX(mouseX);
                    selectionArea.setY(mouseY);
                    selectionArea.setWidth(currentWidth + (currentX - mouseX));
                    selectionArea.setHeight(currentHeight + (currentY - mouseY));
                    break;
                case "se":
                    // 只调整右边框和下边框
                    selectionBorder.setWidth(mouseX - currentX);
                    selectionBorder.setHeight(mouseY - currentY);
                    selectionArea.setWidth(mouseX - currentX);
                    selectionArea.setHeight(mouseY - currentY);
                    break;
                case "sw":
                    selectionBorder.setX(mouseX);
                    selectionBorder.setWidth(currentWidth + (currentX - mouseX));
                    selectionBorder.setHeight(mouseY - currentY);
                    selectionArea.setX(mouseX);
                    selectionArea.setWidth(currentWidth + (currentX - mouseX));
                    selectionArea.setHeight(mouseY - currentY);
                    break;
            }

            // 确保最小尺寸
            if (selectionBorder.getWidth() < 10) {
                if (direction.contains("w")) {
                    selectionBorder.setX(currentX + currentWidth - 10);
                    selectionArea.setX(currentX + currentWidth - 10);
                }
                selectionBorder.setWidth(10);
                selectionArea.setWidth(10);
            }
            if (selectionBorder.getHeight() < 10) {
                if (direction.contains("n")) {
                    selectionBorder.setY(currentY + currentHeight - 10);
                    selectionArea.setY(currentY + currentHeight - 10);
                }
                selectionBorder.setHeight(10);
                selectionArea.setHeight(10);
            }

            selectClip();
            e.consume();
        });

        area.setOnMouseDragged(e -> {
            if (isResizing) {
                handleResize(e, dragDelta);
            }
            e.consume();
        });
    }

    private void handleResize(javafx.scene.input.MouseEvent e, double[] dragDelta) {
        double deltaX = e.getSceneX() - dragDelta[0];
        double deltaY = e.getSceneY() - dragDelta[1];
        double newX = selectionBorder.getX();
        double newY = selectionBorder.getY();
        double newWidth = selectionBorder.getWidth();
        double newHeight = selectionBorder.getHeight();
        boolean flipped = false;

        // 根据调整方向更新位置和大小
        switch (resizeDirection) {
            case "n": // 上边框
                newY += deltaY;
                newHeight -= deltaY;
                if (newHeight < 10) {
                    newY = selectionBorder.getY() + selectionBorder.getHeight() - 10;
                    newHeight = 10;
                    resizeDirection = "s";
                    flipped = true;
                }
                break;
            case "s": // 下边框
                newHeight += deltaY;
                if (newHeight < 10) {
                    newHeight = 10;
                    resizeDirection = "n";
                    flipped = true;
                }
                break;
            case "e": // 右边框
                newWidth += deltaX;
                if (newWidth < 10) {
                    newWidth = 10;
                    resizeDirection = "w";
                    flipped = true;
                }
                break;
            case "w": // 左边框
                newX += deltaX;
                newWidth -= deltaX;
                if (newWidth < 10) {
                    newX = selectionBorder.getX() + selectionBorder.getWidth() - 10;
                    newWidth = 10;
                    resizeDirection = "e";
                    flipped = true;
                }
                break;
            case "ne": // 右上角
                newY += deltaY;
                newHeight -= deltaY;
                newWidth += deltaX;
                if (newHeight < 10) {
                    newY = selectionBorder.getY() + selectionBorder.getHeight() - 10;
                    newHeight = 10;
                    resizeDirection = "se";
                    flipped = true;
                }
                if (newWidth < 10) {
                    newWidth = 10;
                    resizeDirection = "nw";
                    flipped = true;
                }
                break;
            case "nw": // 左上角
                newX += deltaX;
                newY += deltaY;
                newWidth -= deltaX;
                newHeight -= deltaY;
                if (newHeight < 10) {
                    newY = selectionBorder.getY() + selectionBorder.getHeight() - 10;
                    newHeight = 10;
                    resizeDirection = "sw";
                    flipped = true;
                }
                if (newWidth < 10) {
                    newX = selectionBorder.getX() + selectionBorder.getWidth() - 10;
                    newWidth = 10;
                    resizeDirection = "ne";
                    flipped = true;
                }
                break;
            case "se": // 右下角
                newWidth += deltaX;
                newHeight += deltaY;
                if (newHeight < 10) {
                    newHeight = 10;
                    resizeDirection = "ne";
                    flipped = true;
                }
                if (newWidth < 10) {
                    newWidth = 10;
                    resizeDirection = "sw";
                    flipped = true;
                }
                break;
            case "sw": // 左下角
                newX += deltaX;
                newWidth -= deltaX;
                newHeight += deltaY;
                if (newHeight < 10) {
                    newHeight = 10;
                    resizeDirection = "nw";
                    flipped = true;
                }
                if (newWidth < 10) {
                    newX = selectionBorder.getX() + selectionBorder.getWidth() - 10;
                    newWidth = 10;
                    resizeDirection = "se";
                    flipped = true;
                }
                break;
        }

        // 更新选择区域和边框
        selectionArea.setX(newX);
        selectionArea.setY(newY);
        selectionArea.setWidth(newWidth);
        selectionArea.setHeight(newHeight);
        selectionBorder.setX(newX);
        selectionBorder.setY(newY);
        selectionBorder.setWidth(newWidth);
        selectionBorder.setHeight(newHeight);

        // 更新遮罩
        selectClip();

        // 更新拖动起点
        dragDelta[0] = e.getSceneX();
        dragDelta[1] = e.getSceneY();

        // 如果发生翻转，更新光标
        if (flipped) {
            selectionBorder.getScene().setCursor(getResizeCursor(resizeDirection));
        }
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

    private void handleDrag(javafx.scene.input.MouseEvent e, double[] dragDelta) {
        double newX = e.getSceneX() - dragDelta[0];
        double newY = e.getSceneY() - dragDelta[1];

        // 限制边界（不能移出屏幕）
        Rectangle2D screenBounds = currentScreen.getBounds();
        double minX = screenBounds.getMinX();
        double minY = screenBounds.getMinY();
        double maxX = screenBounds.getMaxX() - selectionBorder.getWidth();
        double maxY = screenBounds.getMaxY() - selectionBorder.getHeight();

        double finalX = Math.max(minX, Math.min(maxX, newX));
        double finalY = Math.max(minY, Math.min(maxY, newY));

        selectionArea.setX(finalX);
        selectionArea.setY(finalY);
        selectionBorder.setX(finalX);
        selectionBorder.setY(finalY);

        selectClip();
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
        return selectionBorder;
    }

    private static ImageCursor createCustomCursor() {
        return createDirectionalCursor(Icon.point);
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