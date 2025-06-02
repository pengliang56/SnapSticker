package com.github.sticker.feature.widget;

import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import static javafx.scene.layout.Region.USE_PREF_SIZE;

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
        setSnapToPixel(true);
        // 设置初始状态
        setVisible(false);
        setOpacity(0);
        
        // 确保标签不参与布局管理
        setManaged(false);
        resize(80, 24);
        
        // 设置位置绑定
        setupPositionBinding();
        
        // Store reference to owner
        getProperties().put("owner", owner);
    }

    private void setupStyle() {
        // 设置样式
        setStyle("-fx-background-color: rgb(249,249,249);" +
                "-fx-padding: 2 4;" +
                "-fx-font-size: 15px;" +
                "-fx-text-fill: black;" +
                "-fx-font-family: 'Segoe UI';" +
                "-fx-background-radius: 3px;" +
                "-fx-alignment: center;");  // 添加文本居中对齐
        
        setFont(Font.font("Segoe UI", FontWeight.NORMAL, 15));
        setCursor(Cursor.DEFAULT);
    }

    private void setupPositionBinding() {
        if (owner != null) {
            layoutXProperty().bind(Bindings.createDoubleBinding(
                    () -> {
                        Point2D coords = owner.localToScene(0, 0);
                        return coords.getX() + 3;  // 加上偏移量
                    },
                    owner.boundsInLocalProperty(),  // 监听节点变化
                    owner.localToSceneTransformProperty()  // 监听变换变化
            ));

            layoutYProperty().bind(Bindings.createDoubleBinding(
                    () -> {
                        Point2D coords = owner.localToScene(0, 0);
                        return coords.getY() + 3;  // 加上偏移量
                    },
                    owner.boundsInLocalProperty(),  // 监听节点变化
                    owner.localToSceneTransformProperty()  // 监听变换变化
            ));
            
            // 设置样式和行为
            setMouseTransparent(true);
            toFront();
        }
    }
    
    private void updatePosition() {
        // 获取owner在场景中的位置
        double ownerX = owner.getLayoutX();
        double ownerY = owner.getLayoutY();

        System.out.println("ownerX: " + ownerX + " ownerY: " + ownerY);
        // 设置标签位置
        setLayoutX(ownerX + 5);
        setLayoutY(ownerY + 5);
        
        System.out.println("Label position updated - X: " + getLayoutX() + ", Y: " + getLayoutY());
        System.out.println("Label computed size - Width: " + computePrefWidth(-1) + ", Height: " + computePrefHeight(-1));
        System.out.println("Label actual size - Width: " + getWidth() + ", Height: " + getHeight());
        System.out.println("Label text: " + getText());
    }

    /**
     * Update the scale display and show the label.
     * @param scale The new scale value (1.0 = 100%)
     */
    public void updateScale(double scale) {
        // Stop any ongoing fade animation
        fadeOut.stop();
        
        // Update text and show label
        setText(String.format("Size: %.0f%%", scale * 100));
        setVisible(true);
        setOpacity(1.0);
        
        // Start fade out timer
        startFadeOutTimer();
    }

    private void startFadeOutTimer() {
        // 5秒后开始淡出
        javafx.animation.Timeline hideTimer = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(Duration.seconds(3), e -> fadeOut.play())
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