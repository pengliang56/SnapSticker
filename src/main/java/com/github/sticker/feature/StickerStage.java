package com.github.sticker.feature;

import com.github.sticker.feature.widget.StickerPane;
import com.github.sticker.util.StealthWindow;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 贴图窗口管理类
 * 创建一个覆盖所有屏幕的透明窗口用于贴图
 */
public class StickerStage {
    private static StickerStage instance;
    private Stage stage;
    private Pane root;
    private Rectangle2D totalBounds;
    private final List<StickerPane> stickerStageList = new ArrayList<>();

    public static StickerStage getInstance() {
        if (instance == null) {
            instance = new StickerStage();
        }
        return instance;
    }

    private StickerStage() {
        initializeTotalBounds();
        createStage();
        addStageStyles();
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
        stage.setTitle("Sticker");

        // 设置窗口位置和大小为所有屏幕的总边界
        stage.setX(totalBounds.getMinX());
        stage.setY(totalBounds.getMinY());
        stage.setWidth(totalBounds.getWidth());
        stage.setHeight(totalBounds.getHeight());

        // 创建根面板
        root = new Pane();
        root.setCache(true);
        root.setCacheHint(CacheHint.SPEED);
        root.setStyle("-fx-background-color: transparent;");
        root.setPickOnBounds(false);
        // 创建场景
        Scene scene = new Scene(root);
        scene.setFill(null);

        stage.setScene(scene);
        StealthWindow.configure(stage);
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
     *
     * @return Pane 根面板
     */
    public Pane getRoot() {
        return root;
    }

    /**
     * 获取Stage实例
     *
     * @return Stage 实例
     */
    public Stage getStage() {
        return stage;
    }


    /**
     * 清除所有贴图
     */
    public void clearStickers() {
        root.getChildren().clear();
        hide();
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
        instance = null;
    }

    private void addStageStyles() {
        Scene scene = stage.getScene();
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/sticker.css")).toExternalForm()
        );
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/index.css")).toExternalForm()
        );
    }

    public void addSticker(StickerPane stickerPane) {
        stickerStageList.add(stickerPane);
        root.getChildren().add(stickerPane);
    }

    /**
     * 获取贴图列表
     * @return 贴图列表
     */
    public List<StickerPane> getStickerStageList() {
        return stickerStageList;
    }
} 