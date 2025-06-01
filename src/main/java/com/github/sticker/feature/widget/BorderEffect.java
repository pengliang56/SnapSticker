package com.github.sticker.feature.widget;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
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
    private final BooleanProperty shadowEnabled = new SimpleBooleanProperty(true);
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
            target.getProperties().put("effect", effect);  // 存储效果引用
            
            target.setFill(BACKGROUND_COLOR);
            target.setEffect(effect);
            
            // 监听焦点变化
            target.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    effect.setColor(ACTIVE_COLOR);
                } else {
                    effect.setColor(DIM_COLOR);
                }
                // if (shadowEnabled.get()) {
                //     effect.setColor(newVal ? ACTIVE_COLOR : DIM_COLOR);
                // }
            });
        }
        
        // 创建呼吸动画
        setupBreathingAnimation();
        
        // 监听shadowEnabled属性
        shadowEnabled.addListener((obs, oldVal, newVal) -> {
            if (target != null) {
                target.getProperties().put("shadow", newVal);  // 更新阴影属性
                if (newVal) {
                    effect.setColor(target.isFocused() ? ACTIVE_COLOR : DIM_COLOR);
                } else {
                    effect.setColor(TRANSPARENT);
                }
            }
        });
    }

    private void setupBreathingAnimation() {
        breathingAnimation = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(effect.radiusProperty(), 10),
                new KeyValue(effect.colorProperty(), ACTIVE_COLOR)),
            new KeyFrame(Duration.seconds(1),
                new KeyValue(effect.radiusProperty(), 15),
                new KeyValue(effect.colorProperty(), Color.rgb(255, 102, 102, 0.8))),
            new KeyFrame(Duration.seconds(2),
                new KeyValue(effect.radiusProperty(), 10),
                new KeyValue(effect.colorProperty(), ACTIVE_COLOR))
        );
        breathingAnimation.setCycleCount(1);  // 只播放一次
        
        // 动画结束后保持高亮状态
        breathingAnimation.setOnFinished(e -> {
            if (target != null) {
                if (shadowEnabled.get()) {
                    effect.setRadius(10);
                    effect.setColor(target.isFocused() ? ACTIVE_COLOR : DIM_COLOR);
                } else {
                    effect.setRadius(10);
                    effect.setColor(TRANSPARENT);
                }
            }
        });
    }

    /**
     * 播放呼吸动画效果
     */
    public void playBreathingAnimation() {
        if (target != null && shadowEnabled.get()) {
            breathingAnimation.play();
        }
    }

    /**
     * 设置是否启用阴影效果
     * @param enabled true启用，false禁用
     */
    public void setShadowEnabled(boolean enabled) {
        shadowEnabled.set(enabled);
    }

    /**
     * 获取阴影启用状态
     * @return 是否启用阴影
     */
    public boolean isShadowEnabled() {
        return shadowEnabled.get();
    }

    /**
     * 获取阴影启用状态属性
     * @return shadowEnabled属性
     */
    public BooleanProperty shadowEnabledProperty() {
        return shadowEnabled;
    }

    /**
     * 设置活跃状态
     * @param active true为活跃状态，false为非活跃状态
     */
    public void setActive(boolean active) {
        if (target != null && shadowEnabled.get()) {
            effect.setColor(active ? ACTIVE_COLOR : DIM_COLOR);
        }
    }

    /**
     * 获取当前效果对象
     * @return DropShadow效果对象
     */
    public DropShadow getEffect() {
        return effect;
    }
} 