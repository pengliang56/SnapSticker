package com.github.sticker.feature;

import com.github.sticker.util.StealthWindow;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

/**
 * 贴图窗口管理类
 * 创建一个覆盖所有屏幕的透明窗口用于贴图
 */
public class StickerStage {
    private Stage stage;
    private Pane root;
    private Rectangle2D totalBounds;

    public StickerStage() {
        initializeTotalBounds();
        createStage();
    }

    /**
     * 计算所有屏幕的总边界
     * 包括最左、最右、最上、最下的坐标，创建一个包含所有屏幕的矩形
     */
    private void initializeTotalBounds() {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;

        // 遍历所有屏幕，计算总边界
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            minX = Math.min(minX, bounds.getMinX());
            minY = Math.min(minY, bounds.getMinY());
            maxX = Math.max(maxX, bounds.getMaxX());
            maxY = Math.max(maxY, bounds.getMaxY());
        }

        // 创建包含所有屏幕的矩形
        totalBounds = new Rectangle2D(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * 创建贴图使用的舞台
     */
    private void createStage() {
        stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setTitle("SnapSticker");

        // 设置窗口位置和大小为所有屏幕的总边界
        stage.setX(totalBounds.getMinX());
        stage.setY(totalBounds.getMinY());
        stage.setWidth(totalBounds.getWidth());
        stage.setHeight(totalBounds.getHeight());

        // 创建根面板
        root = new Pane();
        root.setStyle("-fx-background-color: transparent;");

        // 创建场景
        Scene scene = new Scene(root);
        scene.setFill(null);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/index.css")).toExternalForm()
        );

        stage.setScene(scene);
        StealthWindow.configure(stage);
    }

    /**
     * 显示贴图窗口
     */
    public void show() {
        stage.show();
    }

    /**
     * 隐藏贴图窗口
     */
    public void hide() {
        stage.hide();
    }

    /**
     * 获取根面板
     * @return Pane 根面板
     */
    public Pane getRoot() {
        return root;
    }

    /**
     * 获取总边界
     * @return Rectangle2D 总边界
     */
    public Rectangle2D getTotalBounds() {
        return totalBounds;
    }

    /**
     * 获取Stage实例
     * @return Stage 实例
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * 更新窗口边界
     * 在屏幕配置改变时调用
     */
    public void updateBounds() {
        initializeTotalBounds();
        stage.setX(totalBounds.getMinX());
        stage.setY(totalBounds.getMinY());
        stage.setWidth(totalBounds.getWidth());
        stage.setHeight(totalBounds.getHeight());
    }

    /**
     * 清除所有贴图
     */
    public void clearStickers() {
        root.getChildren().clear();
    }

    /**
     * 销毁资源
     */
    public void dispose() {
        if (stage != null) {
            stage.close();
            stage = null;
        }
        root = null;
    }
} 