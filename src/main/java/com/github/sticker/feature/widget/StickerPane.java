package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawCanvas;
import com.github.sticker.draw.DrawingToolbar;
import com.github.sticker.draw.FloatingToolbar;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * 贴图面板
 * 包含图片显示和绘图功能
 */
public class StickerPane extends StackPane {
    private final ImageView imageView;
    private final DrawCanvas drawCanvas;
    private FloatingToolbar floatingToolbar;
    private Pane root;

    double MAX_WIDTH = 8000;
    double MAX_HEIGHT = 6000;

    public StickerPane(WritableImage image, Pane root) {
        // 设置图片显示
        imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        // 创建绘图画布
        drawCanvas = new DrawCanvas();
        drawCanvas.setMouseTransparent(true);  // 初始时画布不接收鼠标事件
        
        // 确保画布大小跟随贴图大小
        drawCanvas.prefWidthProperty().bind(imageView.fitWidthProperty());
        drawCanvas.prefHeightProperty().bind(imageView.fitHeightProperty());
        
        // 添加组件到面板
        getChildren().addAll(imageView, drawCanvas);
        
        // 设置面板样式
        setStyle("-fx-background-color: transparent;");
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
     * 设置贴图大小
     */
    public void setSize(double width, double height) {
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
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
//        configStackPane();
//        Rectangle rect = new Rectangle();
//        rect.setStroke(Color.RED);
//        rect.setFill(Color.TRANSPARENT);
//        rect.xProperty().bind(imageView.layoutXProperty());
//        rect.yProperty().bind(imageView.layoutYProperty());
//        rect.widthProperty().bind(imageView.fitWidthProperty());
//        rect.heightProperty().bind(imageView.fitHeightProperty());

        floatingToolbar = new FloatingToolbar(null, root, drawCanvas, null, this);
    }
}