package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.FloatingToolbar;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import static com.github.sticker.draw.Icon.createDirectionalCursor;
import static com.github.sticker.draw.Icon.point;

/**
 * 贴图面板
 * 包含图片显示和绘图功能
 */
public class StickerPane extends StackPane {
    private final ImageView imageView;
    private final DrawCanvas drawCanvas;
    private FloatingToolbar floatingToolbar;
    private final Rectangle frame;
    private final BorderEffect borderEffect;

    public StickerPane(WritableImage image) {
        setPickOnBounds(false);
        setMouseTransparent(true);

        frame = new Rectangle();
        frame.setFocusTraversable(true);
        frame.setFill(Color.TRANSPARENT);
        frame.setPickOnBounds(true);
        frame.setMouseTransparent(false);

        // 设置图片显示
        imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setPickOnBounds(false);
        imageView.setMouseTransparent(true);
        imageView.setFocusTraversable(false);

        borderEffect = new BorderEffect(frame);
        frame.getProperties().put("borderEffect", borderEffect);
        borderEffect.playBreathingAnimation();

        // 创建绘图画布
        drawCanvas = new DrawCanvas();
        drawCanvas.setPickOnBounds(true);
        drawCanvas.setMouseTransparent(true);

        // 确保画布和图片大小跟随frame大小
        imageView.fitWidthProperty().bind(frame.widthProperty());
        imageView.fitHeightProperty().bind(frame.heightProperty());
        drawCanvas.prefWidthProperty().bind(frame.widthProperty());
        drawCanvas.prefHeightProperty().bind(frame.heightProperty());

        // 绑定旋转属性
        imageView.rotateProperty().bind(frame.rotateProperty());
        drawCanvas.rotateProperty().bind(frame.rotateProperty());
        
        // 设置旋转中心点
        frame.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        imageView.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        drawCanvas.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);

        // 监听旋转变化，调整缩放基准点
        frame.rotateProperty().addListener((obs, oldVal, newVal) -> {
            //updateScaleOrigin();
        });

        StickerScaleLabel scaleLabel = new StickerScaleLabel(this);
        StickerScaleHandler scaleHandler = new StickerScaleHandler(this, scaleLabel);
        this.getFrame().getProperties().put("scaleHandler", scaleHandler);

        // 添加组件到面板
        getChildren().addAll(frame, imageView, drawCanvas, scaleLabel);

        // 设置面板样式
        setStyle("-fx-background-color: transparent;");

        // 绑定frame位置到StackPane的位置
        frame.xProperty().bind(layoutXProperty());
        frame.yProperty().bind(layoutYProperty());

        // 创建并设置事件处理器
        new StickerEventHandler(frame, this, borderEffect);

        // 设置基本属性
        setPickOnBounds(true);
        setMouseTransparent(false);
    }

    /**
     * 获取图片视图组件
     */
    public ImageView getImageView() {
        return imageView;
    }

    /**
     * 获取frame容器
     */
    public Rectangle getFrame() {
        return frame;
    }

    /**
     * 设置贴图大小
     */
    public void setSize(double width, double height) {
        frame.setWidth(width);
        frame.setHeight(height);
    }

    /**
     * 设置贴图位置
     */
    public void setPosition(double x, double y) {
        setLayoutX(x);
        setLayoutY(y);
    }

    /**
     * 清除所有绘制内容
     */
    public void clearDrawing() {
        drawCanvas.getChildren().clear();
    }

    public void setToolbar(Pane root) {
        floatingToolbar = new FloatingToolbar(frame, root, drawCanvas, null, this, false);
        frame.getProperties().put("showToolbar", false);
    }

    /**
     * 销毁当前贴图
     * 清理资源并从父容器移除
     */
    public void destroy() {
        // 清理画布内容
        clearDrawing();

        // 解除绑定
        imageView.fitWidthProperty().unbind();
        imageView.fitHeightProperty().unbind();
        drawCanvas.prefWidthProperty().unbind();
        drawCanvas.prefHeightProperty().unbind();
        frame.xProperty().unbind();
        frame.yProperty().unbind();

        // 清理图片资源
        imageView.setImage(null);

        // 清理工具栏
        if (floatingToolbar != null) {
            floatingToolbar.destroy();
            floatingToolbar = null;
        }

        // 从父容器中移除
        if (getParent() != null) {
            ((Pane) getParent()).getChildren().remove(this);
        }
    }

    public FloatingToolbar getFloatingToolbar() {
        return floatingToolbar;
    }

    public void switchDrawing(boolean activate) {
        if (activate) {
            borderEffect.setActive(true);
            frame.setMouseTransparent(true);
            drawCanvas.setMouseTransparent(false);
        } else {
            frame.setMouseTransparent(false);
            drawCanvas.setMouseTransparent(true);
        }

    }
}