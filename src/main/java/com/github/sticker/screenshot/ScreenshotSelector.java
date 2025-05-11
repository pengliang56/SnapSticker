package com.github.sticker.screenshot;

import com.github.sticker.util.ScreenManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.*;

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
    private static final double MASK_OPACITY = 0.5;

    // Mouse tracking
    private Timeline mouseTracker;
    private static final Duration TRACK_INTERVAL = Duration.millis(16); // ~60fps

    // Screen and taskbar bounds
    private Rectangle2D currentScreenBounds;
    private Rectangle2D taskbarBounds;
    private boolean isTaskbarVisible;
    private Screen currentScreen;      // Track current screen

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
    private void cleanup() {
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
        isSelecting = false;
    }

    /**
     * Initialize all mask layers for the screenshot selection
     */
    private void initializeMaskLayers() {
        // Create and add all mask layers
        fullscreenMask = createBaseMask();
        selectionArea = createSelectionMask();
        selectionBorder = createSelectionBorder();

        root.getChildren().addAll(fullscreenMask, selectionArea, selectionBorder);

        // Initial mask update based on mouse position
        updateMaskBasedOnMousePosition();
    }

    /**
     * Create the base semi-transparent mask
     *
     * @return Rectangle representing the base mask
     */
    private Rectangle createBaseMask() {
        Rectangle mask = new Rectangle();
        mask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
        mask.setMouseTransparent(false);
        return mask;
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
        mask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
        root.getChildren().set(0, mask);
    }

    private void addBorder(Shape mask) {
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
            mask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
            addBorder(mask);
            root.getChildren().set(0, mask);
        } else {
            // If no taskbar, just use the base mask
            Shape mask = Shape.subtract(fullscreenMask, selectionArea);
            mask.setFill(Color.color(0, 0, 0, MASK_OPACITY));
            addBorder(mask);
            root.getChildren().set(0, mask);
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
        border.getStrokeDashArray().addAll(10.0, 15.0);
        border.setMouseTransparent(false);
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
        // Create a clip shape that only affects the base mask
        Shape clip = Shape.subtract(fullscreenMask, selectionArea);
        clip.setFill(Color.color(0, 0, 0, MASK_OPACITY));

        // Update the base mask with the clip
        root.getChildren().set(0, clip);

        // Ensure selection area is on top and not affected by the clip
        root.getChildren().set(1, selectionArea);
        root.getChildren().set(2, selectionBorder);
    }

    /**
     * Set up mouse event handlers for the selection process
     *
     * @param scene The scene to attach the handlers to
     */
    private void setupMouseHandlers(Scene scene) {
        scene.setOnMousePressed(event -> {
            scene.setCursor(javafx.scene.Cursor.CROSSHAIR);
            startX = event.getScreenX();
            startY = event.getScreenY();
            isSelecting = true;
        });

        scene.setOnMouseDragged(event -> {
            stopMouseTracking();
            if (isSelecting) {
                root.setClip(null);
                updateFullscreen(0, 0, currentScreenBounds.getWidth(), currentScreenBounds.getHeight());
                root.getChildren().set(0, fullscreenMask);

                endX = event.getScreenX();
                endY = event.getScreenY();
                updateSelectionOverlay();
            }
        });

        scene.setOnMouseReleased(event -> handleMouseReleased(event, scene));
    }

    /**
     * 处理鼠标释放事件
     */
    private void handleMouseReleased(javafx.scene.input.MouseEvent event, Scene scene) {
        stopMouseTracking();
        if (isSelecting) {
            // 重置选择状态
            scene.setCursor(javafx.scene.Cursor.DEFAULT);
            endX = event.getScreenX();
            endY = event.getScreenY();
            isSelecting = false;

            // 更新选择区域位置和大小
            updateSelectionAreaPosition();
            
            // 添加拖动事件处理
            setupDragHandlers();
        } else {
            // 如果未选择，则取消选择
            cleanup();
        }
    }

    /**
     * 更新选择区域的位置和大小
     */
    private void updateSelectionAreaPosition() {
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        selectionArea.setX(x);
        selectionArea.setY(y);
        selectionArea.setWidth(width);
        selectionArea.setHeight(height);
        selectionBorder.setX(x);
        selectionBorder.setY(y);
        selectionBorder.setWidth(width);
        selectionBorder.setHeight(height);
    }

    /**
     * 设置拖动事件处理器
     */
    private void setupDragHandlers() {
        final double[] dragDelta = new double[2];
        
        selectionBorder.setOnMousePressed(e -> {
            dragDelta[0] = e.getSceneX() - selectionBorder.getX();
            dragDelta[1] = e.getSceneY() - selectionBorder.getY();
            selectionBorder.getScene().setCursor(javafx.scene.Cursor.MOVE);
            e.consume();
        });

        selectionBorder.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - dragDelta[0];
            double newY = e.getSceneY() - dragDelta[1];
            
            // 使用当前屏幕的完整边界（包括任务栏）
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
            e.consume();
        });

        selectionBorder.setOnMouseReleased(e -> {
            selectionBorder.getScene().setCursor(javafx.scene.Cursor.DEFAULT);
            e.consume();
        });
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
} 