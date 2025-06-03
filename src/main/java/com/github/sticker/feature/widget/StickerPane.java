package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.FloatingToolbar;
import com.github.sticker.util.OCRUtil;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private final Canvas textCanvas; // 使用Canvas替代TextFlow

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

        // 创建文本绘制Canvas
        textCanvas = new Canvas();
        textCanvas.setPickOnBounds(false);
        textCanvas.setMouseTransparent(true);

        // 确保画布和图片大小跟随frame大小
        imageView.fitWidthProperty().bind(frame.widthProperty());
        imageView.fitHeightProperty().bind(frame.heightProperty());
        drawCanvas.prefWidthProperty().bind(frame.widthProperty());
        drawCanvas.prefHeightProperty().bind(frame.heightProperty());
        
        // 绑定Canvas大小
        textCanvas.widthProperty().bind(frame.widthProperty());
        textCanvas.heightProperty().bind(frame.heightProperty());

        // 绑定旋转属性
        imageView.rotateProperty().bind(frame.rotateProperty());
        drawCanvas.rotateProperty().bind(frame.rotateProperty());
        textCanvas.rotateProperty().bind(frame.rotateProperty());
        
        // 设置旋转中心点
        frame.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        imageView.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        drawCanvas.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);
        textCanvas.setRotationAxis(javafx.scene.transform.Rotate.Z_AXIS);

        // 监听旋转变化，调整缩放基准点
        frame.rotateProperty().addListener((obs, oldVal, newVal) -> {
            //updateScaleOrigin();
        });

        StickerScaleLabel scaleLabel = new StickerScaleLabel(this);
        StickerScaleHandler scaleHandler = new StickerScaleHandler(this, scaleLabel);
        this.getFrame().getProperties().put("scaleHandler", scaleHandler);

        // 添加组件到面板
        getChildren().addAll(frame, imageView, textCanvas, drawCanvas, scaleLabel);

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

        // 异步执行OCR识别
        CompletableFuture.runAsync(() -> {
            List<OCRUtil.OCRResult> results = OCRUtil.ocr(image);
            System.out.println(results);
            // 在JavaFX线程中更新UI
            Platform.runLater(() -> {
                clearOcrText();
                for (OCRUtil.OCRResult result : results) {
                    addOcrText(
                        result.getText(),
                        result.getX(),
                        result.getY(),
                        result.getWidth(),
                        result.getHeight()
                    );
                }
                setOcrTextVisible(true);
            });
        });
    }

    /**
     * 添加OCR识别的文字到指定位置
     */
    public void addOcrText(String text, double x, double y, double width, double height) {
        GraphicsContext gc = textCanvas.getGraphicsContext2D();
        
        // 使用固定的20号字体
        gc.setFill(Color.RED);
        gc.setFont(new Font("System", 20));
        
        // 直接在指定位置绘制文本
        gc.fillText(text, x, y + height);
        
        // 调试信息
        System.out.println(String.format(
            "Drawing text '%s' at position (%.1f,%.1f)",
            text, x, y + height
        ));
    }

    /**
     * 计算合适的字体大小
     */
    private double calculateFontSize(GraphicsContext gc, String text, double targetWidth, double targetHeight) {
        // 从一个初始字体大小开始
        double fontSize = 20;
        gc.setFont(new Font("System", fontSize));
        
        // 创建临时Text对象来测量文本大小
        javafx.scene.text.Text tempText = new javafx.scene.text.Text(text);
        tempText.setFont(gc.getFont());
        
        double textWidth = tempText.getBoundsInLocal().getWidth();
        double textHeight = tempText.getBoundsInLocal().getHeight();
        
        // 计算缩放比例
        double widthRatio = targetWidth / textWidth;
        double heightRatio = targetHeight / textHeight;
        
        // 使用较小的比例来确保文本完全适应目标区域
        double ratio = Math.min(widthRatio, heightRatio);
        
        // 计算最终字体大小，并限制在合理范围内
        fontSize = Math.max(8, Math.min(50, fontSize * ratio * 0.9)); // 0.9是为了留一些边距
        
        System.out.println(String.format(
            "Text: '%s', Target size: %.1fx%.1f, Calculated font size: %.1f",
            text, targetWidth, targetHeight, fontSize
        ));
        
        return fontSize;
    }

    /**
     * 清除所有OCR文字
     */
    public void clearOcrText() {
        GraphicsContext gc = textCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, textCanvas.getWidth(), textCanvas.getHeight());
    }

    /**
     * 设置OCR文字层的可见性
     */
    public void setOcrTextVisible(boolean visible) {
        textCanvas.setVisible(visible);
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
        // 清理OCR文字
        clearOcrText();

        // 解除绑定
        imageView.fitWidthProperty().unbind();
        imageView.fitHeightProperty().unbind();
        drawCanvas.prefWidthProperty().unbind();
        drawCanvas.prefHeightProperty().unbind();
        textCanvas.widthProperty().unbind();
        textCanvas.heightProperty().unbind();
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

    /**
     * 执行OCR识别并显示文字
     */
    public void performOCR() {
        // 清除之前的OCR文字
        clearOcrText();
        
        // 获取当前图片
        Image image = imageView.getImage();
        if (image == null) {
            return;
        }
        
        // 执行OCR识别
        List<OCRUtil.OCRResult> results = OCRUtil.ocr(image);
        
        // 显示识别结果
        for (OCRUtil.OCRResult result : results) {
            addOcrText(
                result.getText(),
                result.getX(),
                result.getY(),
                result.getWidth(),
                result.getHeight()
            );
        }
        
        // 显示文字层
        setOcrTextVisible(true);
    }
}