package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.DrawingToolbar;
import com.github.sticker.draw.FloatingToolbar;
import com.github.sticker.feature.StickerStage;
import com.github.sticker.feature.widget.BorderEffect;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.MouseEvent;

import java.util.List;

/**
 * 贴图面板
 * 包含图片显示和绘图功能
 */
public class StickerPane extends StackPane {
    private final ImageView imageView;
    private final DrawCanvas drawCanvas;
    private FloatingToolbar floatingToolbar;
    private Pane root;
    private final Rectangle frame;
    private Runnable onDestroyCallback;
    private final StickerStage stickerStage;

    double MAX_WIDTH = 8000;
    double MAX_HEIGHT = 6000;

    public StickerPane(WritableImage image, Pane root) {
        this.root = root;
        this.stickerStage = StickerStage.getInstance();
        
        frame = new Rectangle();
        frame.setFocusTraversable(true);
        frame.setFill(Color.TRANSPARENT);
        
        // 设置图片显示
        imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);  // 图片不接收鼠标事件
        imageView.setPickOnBounds(false);     // 禁用边界拾取
        imageView.setFocusTraversable(false); // 禁用焦点遍历

        // 创建绘图画布
        drawCanvas = new DrawCanvas();
        drawCanvas.setMouseTransparent(true);  // 初始时画布不接收鼠标事件
        
        // 确保画布和图片大小跟随frame大小
        imageView.fitWidthProperty().bind(frame.widthProperty());
        imageView.fitHeightProperty().bind(frame.heightProperty());
        drawCanvas.prefWidthProperty().bind(frame.widthProperty());
        drawCanvas.prefHeightProperty().bind(frame.heightProperty());
        
        // 添加组件到面板
        getChildren().addAll(frame, imageView, drawCanvas);
        
        // 设置面板样式
        setStyle("-fx-background-color: transparent;");
        
        // 配置初始大小
        configStackPane();
        
        // 绑定frame位置到StackPane的位置
        frame.xProperty().bind(layoutXProperty());
        frame.yProperty().bind(layoutYProperty());

        // 添加鼠标按下事件监听
        frame.setOnMouseClicked(event -> {
            // 取消其他贴图的焦点
            for (StickerPane sticker : stickerStage.getStickerStageList()) {
                if (sticker != this) {
                    BorderEffect effect = (BorderEffect) sticker.getProperties().get("borderEffect");
                    if (effect != null) {
                        effect.setActive(false);
                    }
                }
            }
            
            // 设置当前贴图为活跃状态
            BorderEffect effect = (BorderEffect) getProperties().get("borderEffect");
            if (effect != null) {
                effect.setActive(true);
            }
            
            frame.requestFocus(); // 获取焦点
        });

        // 添加面板的鼠标事件监听
        setOnMousePressed(event -> {
            // 检查点击是否在frame区域内
            if (!frame.contains(frame.sceneToLocal(event.getSceneX(), event.getSceneY()))) {
                // 将焦点转移到父容器来间接取消frame的焦点
                if (getParent() != null) {
                    getParent().requestFocus();
                }
            }
        });
    }

    private void configStackPane() {
        imageView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                double width = Math.min(newImg.getWidth(), MAX_WIDTH);
                double height = Math.min(newImg.getHeight(), MAX_HEIGHT);

                if (newImg.getWidth() > MAX_WIDTH) {
                    height = MAX_WIDTH * newImg.getHeight() / newImg.getWidth();
                } else if (newImg.getHeight() > MAX_HEIGHT) {
                    width = MAX_HEIGHT * newImg.getWidth() / newImg.getHeight();
                }

                frame.setWidth(width);
                frame.setHeight(height);
                setPrefSize(width, height);
            }
        });
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

    /**
     * 设置是否保持宽高比
     */
    public void setPreserveRatio(boolean preserve) {
        imageView.setPreserveRatio(preserve);
    }

    public void setRoot(Pane root) {
        this.root = root;
    }

    public void setToolbar(Pane root) {
        floatingToolbar = new FloatingToolbar(frame, root, drawCanvas, null, this);
    }

    /**
     * 设置销毁回调，用于通知父容器进行清理
     * @param callback 销毁时的回调函数
     */
    public void setOnDestroyCallback(Runnable callback) {
        this.onDestroyCallback = callback;
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
}