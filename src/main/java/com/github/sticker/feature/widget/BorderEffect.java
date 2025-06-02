package com.github.sticker.feature.widget;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A reusable border effect component that provides interactive border and shadow effects.
 * Can be used to add highlighting and focus effects to any Node.
 */
public class BorderEffect {
    private final DropShadow effect;
    private final Rectangle target;
    private Timeline breathingAnimation;
    final BooleanProperty rectangleClicked = new SimpleBooleanProperty(false);
    // 默认颜色
    private static final Color DIM_COLOR = Color.rgb(255, 255, 255, 0.3);    // 淡白色状态（非活跃）
    private static final Color ACTIVE_COLOR = Color.rgb(102, 178, 255, 0.8); // 高亮蓝色状态（活跃）
    private static final Color TRANSPARENT = Color.TRANSPARENT;
    private static final Color BACKGROUND_COLOR = Color.rgb(255, 255, 255, 1);

    public BorderEffect(Rectangle target) {
        this.target = target;
        // 创建阴影效果
        effect = new DropShadow();
        effect.setOffsetX(0);
        effect.setOffsetY(0);
        effect.setRadius(10);
        effect.setSpread(0.4);
        effect.setColor(ACTIVE_COLOR);

        // 设置初始效果
        if (target != null) {
            // 设置初始属性
            target.getProperties().put("shadow", true);  // 默认显示阴影
            //target.getProperties().put("effect", effect);  // 存储效果引用

            target.setFill(BACKGROUND_COLOR);
            target.setEffect(effect);

            target.effectProperty().addListener((observable, oldEffect, newEffect) -> {
                boolean hasShadow = newEffect instanceof DropShadow;
                target.getProperties().put("shadow", hasShadow);
                if (hasShadow) {
                    effect.setColor(target.isFocused() ? ACTIVE_COLOR : DIM_COLOR);
                } else {
                    effect.setColor(TRANSPARENT);
                }
            });
        }

        // 创建呼吸动画
        setupBreathingAnimation();
    }

    private void setupBreathingAnimation() {
        breathingAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(effect.radiusProperty(), 10),
                        new KeyValue(effect.colorProperty(), ACTIVE_COLOR)),
                new KeyFrame(Duration.seconds(1),
                        new KeyValue(effect.radiusProperty(), 15),
                        new KeyValue(effect.colorProperty(), Color.rgb(255, 12, 12, 0.8))),
                new KeyFrame(Duration.seconds(2),
                        new KeyValue(effect.radiusProperty(), 10),
                        new KeyValue(effect.colorProperty(), ACTIVE_COLOR))
        );
        breathingAnimation.setRate(2);
        breathingAnimation.setCycleCount(1);  // 只播放一次

        // 动画结束后保持高亮状态
        breathingAnimation.setOnFinished(e -> {
            if (target != null) {
                if (target.getProperties().get("shadow") != null
                        && true == (Boolean) target.getProperties().get("shadow")) {
                    effect.setRadius(10);
                    effect.setColor(target.isFocused() ? ACTIVE_COLOR : DIM_COLOR);
                } else {
                    effect.setRadius(10);
                    effect.setColor(TRANSPARENT);
                }
            }
        });
    }

    public void playBreathingAnimation() {
        if (target != null && target.getProperties().get("shadow") != null
                && true == (Boolean) target.getProperties().get("shadow")) {
            breathingAnimation.play();
        }
    }

    public void setActive(boolean active) {
        if (target != null && target.getProperties().get("shadow") != null
                && true == (Boolean) target.getProperties().get("shadow")) {
            effect.setColor(active ? ACTIVE_COLOR : DIM_COLOR);
        }
    }
} 