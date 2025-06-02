package com.github.sticker.feature.widget;

import javafx.animation.FadeTransition;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * A label component that shows the scale percentage of a sticker.
 * It automatically fades out after a short period and can be updated with new scale values.
 */
public class StickerScaleLabel extends Label {
    private final FadeTransition fadeOut;
    private final Rectangle owner;

    public StickerScaleLabel(StickerPane stickerPane) {
        this.owner = stickerPane.getFrame();
        
        // Setup label appearance
        setupStyle();
        
        // Initialize fade transition
        fadeOut = new FadeTransition(Duration.seconds(0.5), this);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> setVisible(false));
        
        // Setup initial state
        setVisible(false);
        setOpacity(0);
        setMouseTransparent(true);
        
        // Bind position to owner
        setupPositionBinding();
        
        // Store reference to owner
        getProperties().put("owner", owner);
    }

    private void setupStyle() {
        setStyle("-fx-background-color: rgba(255, 255, 255, 0.8);" +
                "-fx-text-fill: black;" +
                "-fx-padding: 2 8;" +
                "-fx-background-radius: 3;" +
                "-fx-border-radius: 3;" +
                "-fx-border-color: rgba(204, 204, 204, 0.8);" +
                "-fx-border-width: 1;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;");
        setFont(Font.font("System", FontWeight.BOLD, 12));
        setCursor(Cursor.HAND);
    }

    private void setupPositionBinding() {
        if (owner != null) {
            layoutXProperty().bind(owner.layoutXProperty());
            layoutYProperty().bind(owner.layoutYProperty().subtract(25));
        }
    }

    /**
     * Update the scale display and show the label.
     * @param scale The new scale value (1.0 = 100%)
     */
    public void updateScale(double scale) {
        // Stop any ongoing fade animation
        fadeOut.stop();
        
        // Update text and show label
        setText(formatScale(scale));
        setVisible(true);
        setOpacity(1.0);
        
        // Start fade out timer
        startFadeOutTimer();
    }

    private void startFadeOutTimer() {
        // 5秒后开始淡出
        javafx.animation.Timeline hideTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(5), e -> fadeOut.play())
        );
        hideTimer.play();
    }

    private String formatScale(double scale) {
        return String.format("%.0f%%", scale * 100);
    }

    /**
     * Get the Rectangle that owns this label
     * @return The owner Rectangle
     */
    public Rectangle getOwner() {
        return owner;
    }
} 