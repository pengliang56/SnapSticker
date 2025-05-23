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
    private static final int MAG_HEIGHT = 120;
    private static final int MAG_WIDTH = (int) (MAG_HEIGHT * 1.3);  // 1.3 times wider
    private final Canvas magnifierCanvas;
    private final Label coordLabel;
    private final Label colorLabel;
    private final Rectangle colorPreview;
    private final Robot robot;
    private final double zoomLevel = 6;

    // Optimize offset constants for better following
    private static final int OFFSET_X = 20;
    private static final int OFFSET_Y = 20;

    private static final Color CROSSHAIR_COLOR = Color.rgb(2, 183, 200, 0.8);

    // Double buffering
    private final Canvas backBuffer;
    private volatile boolean isRendering = false;

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
    private static final int CAPTURE_SIZE = (int) (Math.max(MAG_WIDTH, MAG_HEIGHT) / 6) + 2; // Optimize capture size

    public Magnifier() throws AWTException {
        this.robot = new Robot();
        robot.setAutoDelay(0);

        // Setup UI components with lightweight style
        magnifierCanvas = new Canvas(MAG_WIDTH, MAG_HEIGHT);
        backBuffer = new Canvas(MAG_WIDTH, MAG_HEIGHT);
        
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

        // Style labels with white text and center alignment
        coordLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15; -fx-alignment: center; -fx-padding: 0;");
        colorLabel.setStyle("-fx-text-fill: white; -fx-font-size: 15; -fx-alignment: center; -fx-padding: 0;");
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
        String normalStyle = "-fx-text-fill: rgba(255,255,255,0.6); -fx-font-size: 15; -fx-alignment: center; -fx-padding: 0;";
        // Style for highlighted keys
        String keyStyle = "-fx-text-fill: rgba(255,255,255,0.9); -fx-font-size: 15; -fx-font-weight: bold; -fx-alignment: center; -fx-padding: 0;";

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
        HBox colorBox = new HBox(5);
        colorBox.getChildren().addAll(colorPreview, colorLabel);
        colorBox.setAlignment(Pos.CENTER);
        colorBox.setStyle("-fx-background-color: transparent; -fx-padding: 4; -fx-border-width: 0;");

        VBox infoPanel = new VBox(2);  // Reduced spacing
        infoPanel.getChildren().addAll(coordLabel, colorBox, hintsBox);
        infoPanel.setAlignment(Pos.CENTER);
        infoPanel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.8); -fx-padding: 4; -fx-border-width: 0; -fx-background-insets: 0;");

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
        VBox.setMargin(infoPanel, new Insets(-5, 0, 0, 0));

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

        // Simple position calculation for better responsiveness
        double posX = screenX + OFFSET_X;
        double posY = screenY + OFFSET_Y;

        // Quick bounds adjustment
        if (posX + MAG_WIDTH > screenBounds.getMaxX()) {
            posX = screenX - OFFSET_X - MAG_WIDTH;
        }
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

    @Override
    public void toFront() {
        // No need to override toFront() anymore as we handle window level z-order in update()
        super.toFront();
    }

    private void updateMagnifier(int screenX, int screenY) {
        if (isRendering) {
            return; // Skip if still rendering previous frame
        }
        
        try {
            isRendering = true;
            
            // Calculate capture region centered on cursor
            int captureX = screenX - CAPTURE_SIZE / 2;
            int captureY = screenY - CAPTURE_SIZE / 2;
            
            // Capture smaller region for better performance
            lastCapture = robot.createScreenCapture(
                    new java.awt.Rectangle(captureX, captureY, CAPTURE_SIZE, CAPTURE_SIZE));

            // Process in background thread
            WritableImage output = outputBuffer;
            PixelWriter writer = output.getPixelWriter();
            
            // Convert and scale the image efficiently
            BufferedImage capture = lastCapture;
            if (capture != null) {
                // Get raw pixels for faster processing
                int[] pixels = capture.getRGB(0, 0, CAPTURE_SIZE, CAPTURE_SIZE, null, 0, CAPTURE_SIZE);
                
                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    try {
                        // Draw to back buffer first
                        GraphicsContext backGC = backBuffer.getGraphicsContext2D();
                        backGC.clearRect(0, 0, MAG_WIDTH, MAG_HEIGHT);
                        
                        // Scale and draw captured image
                        for (int y = 0; y < MAG_HEIGHT; y++) {
                            for (int x = 0; x < MAG_WIDTH; x++) {
                                int srcX = (int) (x / zoomLevel);
                                int srcY = (int) (y / zoomLevel);
                                
                                if (srcX >= 0 && srcX < CAPTURE_SIZE && srcY >= 0 && srcY < CAPTURE_SIZE) {
                                    int pixel = pixels[srcY * CAPTURE_SIZE + srcX];
                                    writer.setArgb(x, y, pixel);
                                }
                            }
                        }
                        
                        // Draw the scaled image to back buffer
                        backGC.drawImage(output, 0, 0);
                        
                        // Draw crosshair on back buffer
                        drawCrosshair(backGC);

                        // Draw white border on back buffer
                        backGC.setStroke(Color.WHITE);
                        backGC.setLineWidth(1);
                        backGC.setLineCap(javafx.scene.shape.StrokeLineCap.SQUARE);
                        backGC.setLineJoin(javafx.scene.shape.StrokeLineJoin.MITER);
                        backGC.setMiterLimit(10);
                        backGC.strokeRect(0.5, 0.5, MAG_WIDTH - 1, MAG_HEIGHT - 1);
                        
                        // Swap buffers - draw back buffer to front
                        GraphicsContext frontGC = magnifierCanvas.getGraphicsContext2D();
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

    private void drawCrosshair(GraphicsContext gc) {
        final int centerX = MAG_WIDTH / 2;
        final int centerY = MAG_HEIGHT / 2;
        
        // Calculate pixel-perfect positions
        double alignedX = Math.round(centerX / zoomLevel) * zoomLevel;
        double alignedY = Math.round(centerY / zoomLevel) * zoomLevel;
        
        gc.setGlobalAlpha(0.2);
        gc.setLineWidth(zoomLevel);
        gc.setStroke(CROSSHAIR_COLOR);
        
        // Draw crosshair lines
        gc.strokeLine(0, alignedY, MAG_WIDTH, alignedY);
        gc.strokeLine(alignedX, 0, alignedX, MAG_HEIGHT);
        
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
}
