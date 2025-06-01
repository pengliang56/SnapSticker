package com.github.sticker.feature;

import com.github.sticker.draw.DrawingToolbar;
import com.github.sticker.feature.widget.*;
import com.github.sticker.util.ScreenManager;
import com.github.sticker.util.StealthWindow;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
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
    private StickerContextMenu contextMenu;
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
        initializeContextMenu();
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
     * 获取总边界
     *
     * @return Rectangle2D 总边界
     */
    public Rectangle2D getTotalBounds() {
        return totalBounds;
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


    private void initializeContextMenu() {
        contextMenu = new StickerContextMenu(stage, root) {
            @Override
            protected void applyZoom(ImageView sticker, double scale) {
                StickerStage.this.applyZoom(sticker, scale);
            }

            @Override
            protected void updateStickerSize(ImageView sticker) {
                StickerStage.this.updateStickerSize(sticker);
            }
        };
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
        // 创建边框效果
        stickerStageList.add(stickerPane);
        setupStickerBehavior(stickerPane);
        root.getChildren().add(stickerPane);
        stage.show();
        stage.setAlwaysOnTop(true);
        stage.toFront();

        BorderEffect borderEffect = new BorderEffect(stickerPane.getFrame());
        stickerPane.getProperties().put("borderEffect", borderEffect);
        borderEffect.playBreathingAnimation();
    }

    private void setupStickerBehavior(StickerPane stickerPane) {
        final double MIN_SCALE = 0.1;
        final double MAX_SCALE = 5.0;  // 限制最大缩放比例为 500%

        // 用于记录拖拽的偏移量
        Delta dragDelta = new Delta();

        // 创建缩放标签
        StickerScaleLabel scaleLabel = new StickerScaleLabel(stickerPane.getImageView());
        root.getChildren().add(scaleLabel);

        // 创建缩放处理器
        StickerScaleHandler scaleHandler = new StickerScaleHandler(stickerPane.getFrame(), scaleLabel, MIN_SCALE, MAX_SCALE);
        stickerPane.getProperties().put("scaleHandler", scaleHandler);

        // 设置基本属性
        setupBasicProperties(stickerPane);

        // 设置鼠标事件处理
        setupMouseHandlers(stickerPane, dragDelta);
    }

    private void setupToolbar(ImageView sticker) {
        DrawingToolbar toolbar = new DrawingToolbar(sticker);
        toolbar.getProperties().put("owner", sticker);
        // currentToolbar = toolbar;

        // 添加工具栏到根节点
        root.getChildren().add(toolbar);

        // 绑定工具栏位置到贴图底部右侧，保持5px间距
        toolbar.layoutXProperty().bind(
                sticker.layoutXProperty()
                        .add(sticker.fitWidthProperty())
                        .subtract(toolbar.widthProperty())
        );
        toolbar.layoutYProperty().bind(
                sticker.layoutYProperty()
                        .add(sticker.fitHeightProperty())
                        .add(5)
        );
    }

    private void setupBasicProperties(StickerPane stickerPane) {
        stickerPane.setPickOnBounds(true);
        stickerPane.setMouseTransparent(false);
        stickerPane.setOnContextMenuRequested(null);  // 防止双触发
    }

    private void setupMouseHandlers(StickerPane stickerPane, Delta dragDelta) {
        stickerPane.setOnMousePressed(e -> handleMousePressed(stickerPane, e, dragDelta));
        stickerPane.setOnMouseDragged(e -> handleMouseDragged(stickerPane, e, dragDelta));
        stickerPane.setOnMouseReleased(e -> {
            stickerPane.setCursor(Cursor.DEFAULT);
            e.consume();
        });
    }

    private void handleMousePressed(StickerPane stickerPane, javafx.scene.input.MouseEvent e, Delta dragDelta) {
        if (e.isPrimaryButtonDown()) {
            handlePrimaryButtonPress(stickerPane, e, dragDelta);
        } else if (e.isSecondaryButtonDown()) {
            handleSecondaryButtonPress(stickerPane, e);
        }
        e.consume();
    }

    private void handlePrimaryButtonPress(StickerPane stickerPane, javafx.scene.input.MouseEvent e, Delta dragDelta) {
        // 如果菜单正在显示，先隐藏菜单并禁用缩放
        if (contextMenu != null && contextMenu.isShowing()) {
            contextMenu.hide();
            StickerScaleHandler scaleHandler = (StickerScaleHandler) stickerPane.getProperties().get("scaleHandler");
            if (scaleHandler != null) {
                scaleHandler.setEnabled(true);
            }
        }

        // 记录鼠标点击位置相对于贴图的偏移
        dragDelta.x = e.getSceneX() - stickerPane.getLayoutX();
        dragDelta.y = e.getSceneY() - stickerPane.getLayoutY();
        stickerPane.setCursor(Cursor.MOVE);

        // 请求焦点并置于顶层
        stickerPane.requestFocus();
        stickerPane.toFront();

        // 更新其他贴图的状态
        updateOtherStickersState(stickerPane);
    }

    private void handleSecondaryButtonPress(StickerPane stickerPane, javafx.scene.input.MouseEvent e) {
        if (contextMenu != null && contextMenu.isShowing()) {
            contextMenu.hide();
        }
        // 显示菜单时禁用缩放
        StickerScaleHandler scaleHandler = (StickerScaleHandler) stickerPane.getProperties().get("scaleHandler");
        if (scaleHandler != null) {
            scaleHandler.setEnabled(false);
        }

        // 更新属性并显示菜单
        contextMenu.show(stickerPane.getImageView(), e.getScreenX(), e.getScreenY());
        stickerPane.requestFocus();
        stickerPane.toFront();
    }

    private void handleMouseDragged(StickerPane stickerPane, javafx.scene.input.MouseEvent e, Delta dragDelta) {
        if (e.isPrimaryButtonDown()) {
            double newX = e.getSceneX() - dragDelta.x;
            double newY = e.getSceneY() - dragDelta.y;
            stickerPane.setLayoutX(newX);
            stickerPane.setLayoutY(newY);
        }
        e.consume();
    }

    // 应用缩放的辅助方法
    private void applyZoom(ImageView sticker, double scale) {
        StickerScaleHandler scaleHandler = (StickerScaleHandler) sticker.getProperties().get("scaleHandler");
        if (scaleHandler != null) {
            scaleHandler.applyScale(scale);
            scaleHandler.setEnabled(true);  // 重新启用缩放功能
            updateStickerSize(sticker);
        }
    }

    private void updateStickerSize(ImageView sticker) {
        if (contextMenu != null) {
            contextMenu.updateImageProperties(sticker);
        }
    }

    private void updateOtherStickersState(StickerPane activeSticker) {
        // 将其他贴图设置为非活跃状态
        root.getChildren().forEach(node -> {
            if (node instanceof StickerPane otherSticker && node != activeSticker) {
                BorderEffect effect = (BorderEffect) otherSticker.getProperties().get("borderEffect");
                if (effect != null) {
                    effect.setActive(false);
                }
            }
        });

        // 设置当前贴图为活跃状态
        BorderEffect effect = (BorderEffect) activeSticker.getProperties().get("borderEffect");
        if (effect != null) {
            effect.setActive(true);
        }
    }

    /**
     * 获取贴图列表
     * @return 贴图列表
     */
    public List<StickerPane> getStickerStageList() {
        return stickerStageList;
    }

    private static class Delta {
        double x, y;
    }
} 