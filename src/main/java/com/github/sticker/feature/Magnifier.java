package com.github.sticker.feature;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Magnifier extends VBox {
    // Magnifier dimensions
    private static final int MAG_HEIGHT = 100;
    private static final int MAG_WIDTH = 150;

    private final Canvas magnifierCanvas;
    private final Label coordLabel;
    private final Label colorLabel;
    private final Rectangle colorPreview;
    private final Robot robot;
    private final double zoomLevel = 6;

    // Larger capture buffer for smoother movement
    private static final int BUFFER_SCALE = 2;  // Capture a larger area for smooth movement
    private static final int BASE_CAPTURE_SIZE = (int) (Math.max(MAG_WIDTH, MAG_HEIGHT) / 6) * BUFFER_SCALE;
    private final int CAPTURE_SIZE;
    private final int CAPTURE_CENTER_OFFSET;

    private boolean isLeftSide = true;

    // Optimize offset constants for better following
    private static final int OFFSET_X = 20;
    private static final int OFFSET_Y = 20;

    private static final Color CROSSHAIR_COLOR = Color.rgb(2, 183, 200, 0.8);

    // Double buffering
    private final Canvas backBuffer;
    private volatile boolean isRendering = false;
    private final WritableImage[] imageBuffers;
    private int currentBufferIndex = 0;
    private static final int BUFFER_COUNT = 3;  // Triple buffering

    // Color format state
    private boolean showHexFormat = false;
    private Color currentColor = Color.BLACK;

    // Performance optimization
    private final ScheduledExecutorService updateExecutor;
    private final AtomicBoolean updatePending = new AtomicBoolean(false);
    private volatile int currentX, currentY;
    private volatile BufferedImage lastCapture;
    private volatile WritableImage outputBuffer;
    private static final int UPDATE_INTERVAL_MS = 8; // 120 FPS

    private static final Color CHECKER_COLOR1 = Color.rgb(255, 255, 255, 1.0);  // 白色
    private static final Color CHECKER_COLOR2 = Color.rgb(220, 220, 220, 1.0);  // 浅灰色
    private static final Color BORDER_COLOR = Color.rgb(30, 109, 235);  // 蓝色边框
    private static final int CHECKER_SIZE = 2;  // 放大后的2个像素大小

    private boolean isDragging = false;
    private boolean isOutOfScreen = false;

    public Magnifier() throws AWTException {
        this.robot = new Robot();
        robot.setAutoDelay(0);

        // Initialize capture sizes based on zoom level
        CAPTURE_SIZE = (int) (BASE_CAPTURE_SIZE * (zoomLevel / 6.0));
        CAPTURE_CENTER_OFFSET = CAPTURE_SIZE / 2;

        // Setup UI components with lightweight style
        magnifierCanvas = new Canvas(MAG_WIDTH, MAG_HEIGHT);
        backBuffer = new Canvas(MAG_WIDTH, MAG_HEIGHT);
        
        // Initialize multiple image buffers
        imageBuffers = new WritableImage[BUFFER_COUNT];
        for (int i = 0; i < BUFFER_COUNT; i++) {
            imageBuffers[i] = new WritableImage(MAG_WIDTH, MAG_HEIGHT);
        }
        
        // Enable hardware acceleration
        magnifierCanvas.setCache(true);
        magnifierCanvas.setCacheHint(javafx.scene.CacheHint.SPEED);
        
        coordLabel = new Label();
        colorLabel = new Label();
        colorPreview = new Rectangle(12, 12);

        // Initialize buffers
        outputBuffer = new WritableImage(MAG_WIDTH, MAG_HEIGHT);

        // Setup update executor with optimized thread
        updateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Magnifier-Update");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });

        setupUI();
        setupKeyboardHandlers();
        getStyleClass().add("magnifier");
        startUpdateLoop();
    }

    private void setupUI() {
        // Remove all styling from canvas
        magnifierCanvas.setStyle(null);

        // Style color preview
        colorPreview.setStroke(Color.WHITE);
        colorPreview.setWidth(12);
        colorPreview.setHeight(12);

        // Style labels with white text and center alignment
        String labelStyle = "-fx-text-fill: white; -fx-font-size: 14; -fx-alignment: center; -fx-padding: 0;";
        coordLabel.setStyle(labelStyle);
        colorLabel.setStyle(labelStyle);
        coordLabel.setAlignment(Pos.CENTER);
        colorLabel.setAlignment(Pos.CENTER);

        // Create hint labels with highlighted keys
        Label pressLabel1 = new Label("Press ");
        Label keyLabel1 = new Label("C");
        Label restLabel1 = new Label(" to copy color");
        Label pressLabel2 = new Label("Press ");
        Label keyLabel2 = new Label("Shift");
        Label restLabel2 = new Label(" to switch");
        Label restLabel3 = new Label("between RGB/HEX");

        // Style for normal text
        String normalStyle = "-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 14; -fx-alignment: center; -fx-padding: 0;";
        // Style for highlighted keys
        String keyStyle = "-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 14; -fx-font-weight: bold; -fx-alignment: center; -fx-padding: 0;";

        pressLabel1.setStyle(normalStyle);
        keyLabel1.setStyle(keyStyle);
        restLabel1.setStyle(normalStyle);
        pressLabel2.setStyle(normalStyle);
        keyLabel2.setStyle(keyStyle);
        restLabel2.setStyle(normalStyle);
        restLabel3.setStyle(normalStyle);

        // Create hint boxes
        HBox hint1 = new HBox(0);
        hint1.setAlignment(Pos.CENTER);
        hint1.getChildren().addAll(pressLabel1, keyLabel1, restLabel1);

        HBox hint2 = new HBox(0);
        hint2.setAlignment(Pos.CENTER);
        hint2.getChildren().addAll(pressLabel2, keyLabel2, restLabel2);

        HBox hint3 = new HBox(0);
        hint3.setAlignment(Pos.CENTER);
        hint3.getChildren().add(restLabel3);

        // Create hints container
        VBox hintsBox = new VBox(1);
        hintsBox.getChildren().addAll(hint1, hint2, hint3);
        hintsBox.setAlignment(Pos.CENTER);
        hintsBox.setStyle("-fx-background-color: transparent; -fx-padding: 2 4 4 4; -fx-border-width: 0;");

        // Create info panel with semi-transparent black background
        HBox colorBox = new HBox(4);
        colorBox.getChildren().addAll(colorPreview, colorLabel);
        colorBox.setAlignment(Pos.CENTER);
        colorBox.setStyle("-fx-background-color: transparent; -fx-padding: 3; -fx-border-width: 0;");

        VBox infoPanel = new VBox(2);
        infoPanel.getChildren().addAll(coordLabel, colorBox, hintsBox);
        infoPanel.setAlignment(Pos.CENTER);
        infoPanel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-padding: 3; -fx-border-width: 0; -fx-background-insets: 0;");

        // Remove all margins and make full width
        infoPanel.setMaxWidth(Double.MAX_VALUE);
        colorBox.setMaxWidth(Double.MAX_VALUE);
        hintsBox.setMaxWidth(Double.MAX_VALUE);
        hint1.setMaxWidth(Double.MAX_VALUE);
        hint2.setMaxWidth(Double.MAX_VALUE);
        hint3.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(magnifierCanvas, null);
        HBox.setMargin(colorPreview, null);
        HBox.setMargin(colorLabel, null);

        // Add negative margin to move info panel up
        VBox.setMargin(infoPanel, new Insets(-4, 0, 0, 0));

        // Main container styling - remove ALL spacing
        setSpacing(0);
        setPadding(Insets.EMPTY);
        setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-border-width: 0; -fx-background-insets: 0;");

        // Force managed layout
        magnifierCanvas.setManaged(true);
        infoPanel.setManaged(true);

        // Clear and add children
        getChildren().clear();
        getChildren().addAll(magnifierCanvas, infoPanel);
    }

    private void setupKeyboardHandlers() {
        // Add key event filter to the scene
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
            }
        });
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.SHIFT) {
            showHexFormat = !showHexFormat;
            updateColorLabel(currentColor);
            event.consume();
        } else if (event.getCode() == KeyCode.C) {
            copyColorToClipboard();
            event.consume();
        }
    }

    private void copyColorToClipboard() {
        String colorText = showHexFormat ?
                String.format("#%02X%02X%02X",
                        (int) (currentColor.getRed() * 255),
                        (int) (currentColor.getGreen() * 255),
                        (int) (currentColor.getBlue() * 255)) :
                String.format("%d, %d, %d",
                        (int) (currentColor.getRed() * 255),
                        (int) (currentColor.getGreen() * 255),
                        (int) (currentColor.getBlue() * 255));

        StringSelection stringSelection = new StringSelection(colorText);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private void updateColorLabel(Color color) {
        currentColor = color;
        if (showHexFormat) {
            colorLabel.setText(String.format("#%02X%02X%02X",
                    (int) (color.getRed() * 255),
                    (int) (color.getGreen() * 255),
                    (int) (color.getBlue() * 255)));
        } else {
            colorLabel.setText(String.format(" %d ,  %d ,  %d",
                    (int) (color.getRed() * 255),
                    (int) (color.getGreen() * 255),
                    (int) (color.getBlue() * 255)));
        }
    }

    private void startUpdateLoop() {
        updateExecutor.scheduleAtFixedRate(() -> {
            if (updatePending.get()) {
                updatePending.set(false);
                try {
                    updateMagnifier(currentX, currentY);
                } catch (Exception e) {
                    // Ignore errors to keep the loop running
                }
            }
        }, 0, UPDATE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void update(int screenX, int screenY) {
        // Get screen bounds
        javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();

        // Calculate total height including info panel
        double totalHeight = MAG_HEIGHT + getHeight() - magnifierCanvas.getHeight();

        // Calculate position maintaining current side
        double posX = isLeftSide ? screenX + OFFSET_X : screenX - OFFSET_X - MAG_WIDTH;
        double posY = screenY + OFFSET_Y;

        // Check if we need to switch sides
        if (isLeftSide && posX + MAG_WIDTH > screenBounds.getMaxX()) {
            isLeftSide = false;
            posX = screenX - OFFSET_X - MAG_WIDTH;
        } else if (!isLeftSide && posX < screenBounds.getMinX()) {
            isLeftSide = true;
            posX = screenX + OFFSET_X;
        }

        // Adjust Y position if too close to bottom edge
        if (posY + totalHeight > screenBounds.getMaxY()) {
            posY = screenY - OFFSET_Y - totalHeight;
        }

        // Direct position update
        setLayoutX(posX);
        setLayoutY(posY);

        // Keep window on top
        if (getScene() != null && getScene().getWindow() instanceof javafx.stage.Stage) {
            ((javafx.stage.Stage) getScene().getWindow()).setAlwaysOnTop(true);
        }

        // Update state and request refresh
        currentX = screenX;
        currentY = screenY;
        updatePending.set(true);
    }

    private void updateMagnifier(int screenX, int screenY) {
        if (isRendering) {
            return;
        }
        
        try {
            isRendering = true;
            
            // Calculate the center of the magnified view
            int magnifierCenterX = MAG_WIDTH / 2;
            int magnifierCenterY = MAG_HEIGHT / 2;
            
            // 固定取样区域大小
            int captureWidth = (int)(MAG_WIDTH / zoomLevel);
            int captureHeight = (int)(MAG_HEIGHT / zoomLevel);
            
            // 计算取样区域的位置，确保以取样点为中心
            int captureX = screenX - captureWidth / 2;
            int captureY = screenY - captureHeight / 2;
            
            // 获取屏幕边界
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getBounds();
            
            // 检查是否超出屏幕边界
            isOutOfScreen = captureX < screenBounds.getMinX() || 
                           captureY < screenBounds.getMinY() ||
                           captureX + captureWidth > screenBounds.getMaxX() ||
                           captureY + captureHeight > screenBounds.getMaxY();

            // 计算实际可以从屏幕捕获的区域
            int validX = Math.max((int)screenBounds.getMinX(), captureX);
            int validY = Math.max((int)screenBounds.getMinY(), captureY);
            int validWidth = Math.min(captureWidth, (int)screenBounds.getWidth() - (validX - (int)screenBounds.getMinX()));
            int validHeight = Math.min(captureHeight, (int)screenBounds.getHeight() - (validY - (int)screenBounds.getMinY()));

            // 创建一个和捕获区域大小相同的缓冲区
            BufferedImage finalCapture = new BufferedImage(captureWidth, captureHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = finalCapture.createGraphics();

            // 如果有可见的屏幕区域，先捕获它
            if (validWidth > 0 && validHeight > 0) {
                // 计算实际的屏幕坐标
                java.awt.Rectangle screenRect = new java.awt.Rectangle(
                    validX,
                    validY,
                    validWidth,
                    validHeight
                );
                
                // 捕获可见的屏幕区域
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);
                
                // 计算在finalCapture中的绘制位置
                int drawX = validX - captureX;
                int drawY = validY - captureY;
                
                // 绘制捕获的屏幕内容
                g2d.drawImage(screenCapture, drawX, drawY, null);
            }

            // 绘制棋盘格（只在超出屏幕的区域）
            if (isOutOfScreen) {
                // 左边超出部分
                if (captureX < screenBounds.getMinX()) {
                    int outWidth = (int)(screenBounds.getMinX() - captureX);
                    drawCheckerPattern(g2d, 0, 0, outWidth, captureHeight);
                }
                
                // 右边超出部分
                if (captureX + captureWidth > screenBounds.getMaxX()) {
                    int startX = (int)(screenBounds.getMaxX() - captureX);
                    drawCheckerPattern(g2d, startX, 0, captureWidth - startX, captureHeight);
                }
                
                // 上边超出部分
                if (captureY < screenBounds.getMinY()) {
                    int outHeight = (int)(screenBounds.getMinY() - captureY);
                    drawCheckerPattern(g2d, 0, 0, captureWidth, outHeight);
                }
                
                // 下边超出部分
                if (captureY + captureHeight > screenBounds.getMaxY()) {
                    int startY = (int)(screenBounds.getMaxY() - captureY);
                    drawCheckerPattern(g2d, 0, startY, captureWidth, captureHeight - startY);
                }
            }

            g2d.dispose();
            lastCapture = finalCapture;

            // Get next buffer
            currentBufferIndex = (currentBufferIndex + 1) % BUFFER_COUNT;
            WritableImage output = imageBuffers[currentBufferIndex];
            PixelWriter writer = output.getPixelWriter();
            
            // Convert and scale the image efficiently
            if (lastCapture != null) {
                // Get raw pixels for faster processing
                int[] pixels = lastCapture.getRGB(0, 0, captureWidth, captureHeight, null, 0, captureWidth);
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    try {
                        GraphicsContext backGC = backBuffer.getGraphicsContext2D();
                        backGC.clearRect(0, 0, MAG_WIDTH, MAG_HEIGHT);
                        
                        // 先绘制背景和边框
                        backGC.setFill(Color.BLACK);
                        backGC.fillRect(1, 1, MAG_WIDTH - 2, MAG_HEIGHT - 2);
                        
                        // 在边框内部绘制图像
                        backGC.save();
                        backGC.beginPath();
                        backGC.rect(1, 1, MAG_WIDTH - 2, MAG_HEIGHT - 2);
                        backGC.clip();
                        
                        // Scale and draw captured image
                        for (int y = 0; y < MAG_HEIGHT; y++) {
                            for (int x = 0; x < MAG_WIDTH; x++) {
                                int sourceX = (int)(x / zoomLevel);
                                int sourceY = (int)(y / zoomLevel);
                                
                                if (sourceX >= 0 && sourceX < captureWidth && sourceY >= 0 && sourceY < captureHeight) {
                                    int pixel = pixels[sourceY * captureWidth + sourceX];
                                    writer.setArgb(x, y, pixel);
                                }
                            }
                        }
                        
                        backGC.setImageSmoothing(false);
                        backGC.drawImage(output, 1, 1, MAG_WIDTH - 2, MAG_HEIGHT - 2);
                        backGC.restore();
                        
                        // Draw crosshair at exact center
                        drawCrosshair(backGC, magnifierCenterX, magnifierCenterY);

                        // Draw border with single pixel width
                        Color borderColor = isOutOfScreen ? BORDER_COLOR : Color.WHITE;
                        backGC.setStroke(borderColor);
                        backGC.setLineWidth(1.0);
                        backGC.setGlobalAlpha(1.0);
                        
                        // Draw the border using moveTo and lineTo for precise control
                        backGC.beginPath();
                        // Top line
                        backGC.moveTo(0, 0.5);
                        backGC.lineTo(MAG_WIDTH, 0.5);
                        // Right line
                        backGC.moveTo(MAG_WIDTH - 0.5, 0);
                        backGC.lineTo(MAG_WIDTH - 0.5, MAG_HEIGHT);
                        // Bottom line
                        backGC.moveTo(MAG_WIDTH, MAG_HEIGHT - 0.5);
                        backGC.lineTo(0, MAG_HEIGHT - 0.5);
                        // Left line
                        backGC.moveTo(0.5, MAG_HEIGHT);
                        backGC.lineTo(0.5, 0);
                        backGC.stroke();
                        
                        // Swap buffers
                        GraphicsContext frontGC = magnifierCanvas.getGraphicsContext2D();
                        frontGC.setImageSmoothing(false);
                        frontGC.clearRect(0, 0, MAG_WIDTH, MAG_HEIGHT);
                        frontGC.drawImage(backBuffer.snapshot(null, null), 0, 0);
                        
                        // Update color info
                        Color pixelColor = getPixelColor(screenX, screenY);
                        updateInfo(screenX, screenY, pixelColor);
                    } finally {
                        isRendering = false;
                    }
                });
            }
        } catch (Exception e) {
            isRendering = false;
            System.out.println("Error updating magnifier: " + e.getMessage());
        }
    }

    private void drawCrosshair(GraphicsContext gc, int centerX, int centerY) {
        // Ensure crosshair is centered on the actual pixel
        double alignedX = centerX;
        double alignedY = centerY;
        
        gc.setGlobalAlpha(0.2);
        gc.setLineWidth(zoomLevel);
        gc.setStroke(CROSSHAIR_COLOR);
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.SQUARE);
        
        // Draw crosshair lines with pixel-perfect alignment
        gc.strokeLine(alignedX, 0, alignedX, MAG_HEIGHT);
        gc.strokeLine(0, alignedY, MAG_WIDTH, alignedY);
        
        gc.setGlobalAlpha(1.0);
    }

    private Color getPixelColor(int x, int y) {
        try {
            BufferedImage pixel = robot.createScreenCapture(new java.awt.Rectangle(x, y, 1, 1));
            java.awt.Color awtColor = new java.awt.Color(pixel.getRGB(0, 0));
            return Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    private void updateInfo(int x, int y, Color color) {
        coordLabel.setText(String.format("( %d , %d )", x, y));
        updateColorLabel(color);
        colorPreview.setFill(color);
    }

    public void switchShowMagnifier(MouseEvent event, boolean show) {
        this.setVisible(show);
        if (show) {
            this.setLayoutX(event.getX());
            this.setLayoutY(event.getY());
            this.update((int) event.getX(), (int) event.getY());
        }
    }

    public void dispose() {
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }
    }

    private void drawCheckerPattern(Graphics2D g2d, int x, int y, int width, int height) {
        for (int py = y; py < y + height; py += CHECKER_SIZE) {
            for (int px = x; px < x + width; px += CHECKER_SIZE) {
                g2d.setColor(((px / CHECKER_SIZE + py / CHECKER_SIZE) % 2 == 0) ? 
                    new java.awt.Color((float)CHECKER_COLOR1.getRed(), 
                                     (float)CHECKER_COLOR1.getGreen(), 
                                     (float)CHECKER_COLOR1.getBlue()) :
                    new java.awt.Color((float)CHECKER_COLOR2.getRed(), 
                                     (float)CHECKER_COLOR2.getGreen(), 
                                     (float)CHECKER_COLOR2.getBlue()));
                g2d.fillRect(px, py, CHECKER_SIZE, CHECKER_SIZE);
            }
        }
    }
}
