package com.github.sticker.feature.widget;

import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
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
        setStyle("-fx-background-color: rgba(0, 0, 0, 0.7);" +
                "-fx-padding: 3 0;" +  // 减小垂直内边距
                "-fx-border-color: white;" +
                "-fx-border-width: 1;" +
                "-fx-font-size: 15px;" +
                "-fx-font-family: 'Segoe UI';");
        
        // 创建左右两个Label和分隔线
        Label sizeLabel = new Label("Size");
        sizeLabel.setStyle("-fx-text-fill: white;");
        Label valueLabel = new Label();
        valueLabel.setStyle("-fx-text-fill: white;");
        Region separator = new Region();
        separator.setStyle("-fx-background-color: white; -fx-pref-width: 1;");
        separator.setPrefHeight(16);  // 减小分隔线高度

        // 创建水平布局
        HBox layout = new HBox();
        layout.setAlignment(Pos.CENTER);
        layout.setSpacing(0);
        layout.getChildren().addAll(
            new Region() {{ setPrefWidth(8); }},  // 减小左边距
            sizeLabel,
            new Region() {{ setPrefWidth(8); }},  // 减小分隔线前的空间
            separator,
            new Region() {{ setPrefWidth(8); }},  // 减小分隔线后的空间
            valueLabel,
            new Region() {{ setPrefWidth(8); }}   // 减小右边距
        );

        // 存储valueLabel的引用以便更新
        getProperties().put("valueLabel", valueLabel);

        // 设置内容
        setGraphic(layout);
        setFont(Font.font("Segoe UI", FontWeight.NORMAL, 15));
        setCursor(Cursor.DEFAULT);
    }

    private void setupPositionBinding() {
        if (owner != null) {
            // 绑定到StickerPane的位置
            StickerPane stickerPane = (StickerPane) owner.getParent();
            layoutXProperty().bind(stickerPane.layoutXProperty());
            layoutYProperty().bind(stickerPane.layoutYProperty().subtract(35));  // 增加与贴图的距离
        }
    }

    private void updateValue(double scale) {
        Label valueLabel = (Label) getProperties().get("valueLabel");
        if (valueLabel != null) {
            valueLabel.setText(String.format("%.0f%%", scale * 100));
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
        updateValue(scale);
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

    /**
     * Get the Rectangle that owns this label
     * @return The owner Rectangle
     */
    public Rectangle getOwner() {
        return owner;
    }
} 