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
import javafx.scene.text.Text;
import javafx.scene.effect.BlendMode;

import java.util.List;
import java.util.ArrayList;
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
    private final Pane selectionPane; // 用于文本选择的透明层
    private final List<TextSelection> textSelections; // 存储所有文本选择区域
    private TextSelection currentSelection; // 当前正在选择的文本
    private double startX, startY; // 选择起始点

    // 内部类：表示一个可选择的文本区域
    private class TextSelection {
        private final Text text;
        private final Rectangle highlight;
        private final double x, y, width, height;
        private final String content;
        private static final Color HOVER_COLOR = Color.rgb(0, 120, 215, 0.2);    // 悬停时的浅蓝色
        private static final Color SELECTED_COLOR = Color.rgb(0, 120, 215, 0.4); // 选中时的深蓝色
        private static final Color TRANSPARENT_TEXT = Color.TRANSPARENT;          // 透明文本

        public TextSelection(String content, double x, double y, double width, double height) {
            this.content = content;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;

            // 创建文本节点，设置为透明
            text = new Text(content);
            text.setFont(new Font("System", 20));
            text.setFill(TRANSPARENT_TEXT);
            text.setX(x);
            text.setY(y + height);
            text.setMouseTransparent(true);

            // 创建高亮背景
            highlight = new Rectangle(x, y, width, height);
            highlight.setFill(Color.TRANSPARENT);
            highlight.setMouseTransparent(false);

            // 设置鼠标事件
            setupMouseEvents();
        }

        private void setupMouseEvents() {
            // 鼠标进入时显示可选择状态
            highlight.setOnMouseEntered(e -> {
                if (this != currentSelection) {
                    highlight.setFill(HOVER_COLOR);
                }
                selectionPane.setCursor(Cursor.TEXT);
                e.consume();
            });

            // 鼠标离开时恢复透明
            highlight.setOnMouseExited(e -> {
                if (this != currentSelection) {
                    highlight.setFill(Color.TRANSPARENT);
                }
                selectionPane.setCursor(Cursor.DEFAULT);
                e.consume();
            });

            // 处理双击事件
            highlight.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    // 如果已有选中的文本，取消其选中状态
                    if (currentSelection != null && currentSelection != this) {
                        currentSelection.setSelected(false);
                    }
                    // 设置当前文本为选中状态
                    currentSelection = this;
                    setSelected(true);
                    
                    // 确保面板获得焦点以接收键盘事件
                    StickerPane.this.requestFocus();
                    
                    System.out.println("Selected text: " + content);
                } else if (e.getClickCount() == 1) {
                    // 单击时只显示hover效果
                    if (this != currentSelection) {
                        highlight.setFill(HOVER_COLOR);
                    }
                }
                e.consume();
            });
        }

        public void setSelected(boolean selected) {
            highlight.setFill(selected ? SELECTED_COLOR : Color.TRANSPARENT);
        }

        public boolean contains(double x, double y) {
            return x >= this.x && x <= this.x + width &&
                   y >= this.y && y <= this.y + height;
        }

        public void addToPane(Pane pane) {
            pane.getChildren().addAll(highlight, text);
        }

        public void removeFromPane(Pane pane) {
            pane.getChildren().removeAll(highlight, text);
        }

        public String getContent() {
            return content;
        }
    }

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

        // 初始化文本选择相关的组件
        selectionPane = new Pane();
        selectionPane.setPickOnBounds(false);
        selectionPane.setMouseTransparent(false);
        textSelections = new ArrayList<>();

        // 设置选择层的大小绑定
        selectionPane.prefWidthProperty().bind(frame.widthProperty());
        selectionPane.prefHeightProperty().bind(frame.heightProperty());

        // 设置键盘事件处理
        setupKeyboardEvents();

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
        getChildren().addAll(frame, imageView, textCanvas, selectionPane, drawCanvas, scaleLabel);

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
     * 设置键盘事件处理
     */
    private void setupKeyboardEvents() {
        this.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.C && currentSelection != null) {
                // 复制选中的文本到剪贴板
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(currentSelection.getContent());
                clipboard.setContent(content);
                
                System.out.println("Copied text: " + currentSelection.getContent());
                e.consume();
            }
        });
        
        // 确保面板可以接收键盘事件
        this.setFocusTraversable(true);
    }

    /**
     * 添加OCR识别的文字到指定位置
     */
    public void addOcrText(String text, double x, double y, double width, double height) {
        // 创建新的文本选择区域
        TextSelection selection = new TextSelection(text, x, y, width, height);
        textSelections.add(selection);
        selection.addToPane(selectionPane);
        
        // 调试信息
        System.out.println(String.format(
            "Added selectable text '%s' at position (%.1f,%.1f)",
            text, x, y
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
        if (currentSelection != null) {
            currentSelection.setSelected(false);
            currentSelection = null;
        }
        selectionPane.getChildren().clear();
        textSelections.clear();
    }

    /**
     * 设置OCR文字层的可见性
     */
    public void setOcrTextVisible(boolean visible) {
        selectionPane.setVisible(visible);
    }

    /**
     * 启用或禁用文字选择功能
     * @param enabled true 启用文字选择，false 禁用文字选择
     */
    public void setTextSelectionEnabled(boolean enabled) {
        selectionPane.setMouseTransparent(!enabled);
        selectionPane.setCursor(enabled ? Cursor.DEFAULT : Cursor.NONE);
        if (!enabled && currentSelection != null) {
            currentSelection.setSelected(false);
            currentSelection = null;
        }
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