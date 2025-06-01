package com.github.sticker.feature.widget;

import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.shape.Rectangle;

/**
 * 处理贴图缩放功能的组件。
 * 提供平滑的缩放效果和边界控制。
 * 缩放速率会随着当前缩放值的增大而逐渐减小。
 */
public class StickerScaleHandler {
    private final Rectangle frame;
    private final StickerScaleLabel scaleLabel;
    private static final double minScale = 0.1;
    private static final double maxScale = 5.0;
    private double accumulatedScale = 1.0;
    private final double originalWidth;
    private final double originalHeight;

    // 缩放参数
    private static final double BASE_ZOOM_FACTOR = 0.0008;  // 基础缩放因子
    private static final double CTRL_ZOOM_FACTOR = 0.0004;  // Ctrl按下时的缩放因子
    private static final double SCALE_THRESHOLD = 1.0;      // 从100%开始减缓
    private static final double SLOW_SCALE_THRESHOLD = 4.0; // 400%时达到最慢
    private static final double DAMPING_POWER = 2.5;        // 增加衰减幂次，使衰减更快
    private static final double MIN_ZOOM_FACTOR = 0.0001;   // 最小缩放因子

    /**
     * 创建缩放处理器
     * @param frame 要处理的容器
     * @param scaleLabel 显示缩放比例的标签
     */
    public StickerScaleHandler(Rectangle frame, StickerScaleLabel scaleLabel) {
        this.frame = frame;
        this.scaleLabel = scaleLabel;
        
        // 记录原始尺寸
        this.originalWidth = frame.getWidth();
        this.originalHeight = frame.getHeight();
        
        // 设置滚轮事件处理
        setupScrollHandler();
    }

    private void setupScrollHandler() {
        frame.setOnScroll(this::handleScroll);
    }

    private void handleScroll(ScrollEvent event) {
        // 计算新的缩放值
        double newScale = calculateNewScale(event);
        if (newScale != accumulatedScale) {
            applyScale(newScale);
        }
        event.consume();
    }

    private double calculateNewScale(ScrollEvent event) {
        boolean isCtrlDown = event.isControlDown();
        double scrollAmount = event.getDeltaY();
        
        // 根据当前缩放值计算动态缩放因子
        double dynamicZoomFactor = calculateDynamicZoomFactor(isCtrlDown);
        
        // 应用缩放
        double zoomFactor = Math.exp(scrollAmount * dynamicZoomFactor);
        double newScale = accumulatedScale * zoomFactor;

        // 确保缩放在允许的范围内
        return Math.min(Math.max(newScale, minScale), maxScale);
    }

    /**
     * 计算动态缩放因子
     * 从100%开始逐渐减缓，400%时达到最小速度
     */
    private double calculateDynamicZoomFactor(boolean isCtrlDown) {
        // 基础缩放因子
        double baseZoom = isCtrlDown ? CTRL_ZOOM_FACTOR : BASE_ZOOM_FACTOR;
        
        // 如果当前缩放值小于等于阈值（100%），使用基础缩放因子
        if (accumulatedScale <= SCALE_THRESHOLD) {
            return baseZoom;
        }
        
        // 如果已经达到或超过400%，使用最小缩放因子
        if (accumulatedScale >= SLOW_SCALE_THRESHOLD) {
            return MIN_ZOOM_FACTOR;
        }
        
        // 计算100%到400%之间的进度
        double scaleProgress = (accumulatedScale - SCALE_THRESHOLD) / (SLOW_SCALE_THRESHOLD - SCALE_THRESHOLD);
        
        // 使用指数衰减计算缩放因子
        double dampingFactor = Math.pow(1 - scaleProgress, DAMPING_POWER);
        double dampedZoom = baseZoom * dampingFactor;
        
        // 确保缩放因子不会小于最小值
        return Math.max(dampedZoom, MIN_ZOOM_FACTOR);
    }

    /**
     * 应用指定的缩放比例
     * @param scale 要应用的缩放比例
     */
    public void applyScale(double scale) {
        accumulatedScale = scale;

        // 应用新的尺寸
        frame.setWidth(originalWidth * scale);
        frame.setHeight(originalHeight * scale);

        // 更新标签显示
        scaleLabel.updateScale(scale);
    }

    /**
     * 获取当前缩放比例
     * @return 当前缩放比例
     */
    public double getCurrentScale() {
        return accumulatedScale;
    }

    /**
     * 获取原始宽度
     * @return 原始宽度
     */
    public double getOriginalWidth() {
        return originalWidth;
    }

    /**
     * 获取原始高度
     * @return 原始高度
     */
    public double getOriginalHeight() {
        return originalHeight;
    }

    /**
     * 重置缩放到100%
     */
    public void resetScale() {
        applyScale(1.0);
    }

    /**
     * 设置是否启用缩放功能
     * @param enabled true启用，false禁用
     */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            frame.setOnScroll(this::handleScroll);
        } else {
            frame.setOnScroll(null);
        }
    }
} 