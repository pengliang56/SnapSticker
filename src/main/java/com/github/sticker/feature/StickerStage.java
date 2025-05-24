package com.github.sticker.feature;

import com.github.sticker.util.StealthWindow;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.Cursor;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 贴图窗口管理类
 * 创建一个覆盖所有屏幕的透明窗口用于贴图
 */
public class StickerStage {
    private Stage stage;
    private Pane root;
    private Rectangle2D totalBounds;
    private ContextMenu contextMenu;

    public StickerStage() {
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
                Objects.requireNonNull(getClass().getResource("/styles/sticker.css")).toExternalForm()
        );

        // 添加点击事件来隐藏菜单
        scene.setOnMousePressed(e -> {
            if (contextMenu != null && contextMenu.isShowing()) {
                // 检查点击是否在菜单外部
                if (!isClickInMenu(e.getScreenX(), e.getScreenY())) {
                    contextMenu.hide();
                }
            }
        });

        stage.setScene(scene);
        stage.show();
        //StealthWindow.configure(stage);
    }

    private boolean isClickInMenu(double screenX, double screenY) {
        if (contextMenu == null || !contextMenu.isShowing()) {
            return false;
        }

        // 获取菜单的屏幕坐标和大小
        Window menuWindow = contextMenu.getScene().getWindow();
        if (menuWindow == null) {
            return false;
        }

        double menuX = menuWindow.getX();
        double menuY = menuWindow.getY();
        double menuWidth = menuWindow.getWidth();
        double menuHeight = menuWindow.getHeight();

        // 检查点击是否在菜单区域内
        return screenX >= menuX && screenX <= (menuX + menuWidth) &&
               screenY >= menuY && screenY <= (menuY + menuHeight);
    }

    /**
     * 显示贴图窗口
     */
    public void show() {
        //StealthWindow.configure(stage);
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

    private void initializeContextMenu() {
        contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("sticker-context-menu");

        MenuItem copyItem = new MenuItem("Copy image");
        MenuItem saveItem = new MenuItem("Save image as...");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem closeItem = new MenuItem("Close");
        MenuItem destroyItem = new MenuItem("Destroy");

        // 设置菜单项事件处理
        copyItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem) {
                MenuItem menuItem = (MenuItem) e.getTarget();
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView) {
                    ImageView sticker = (ImageView) menuItem.getParentPopup().getOwnerNode();
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putImage(sticker.getImage());
                    clipboard.setContent(content);
                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        saveItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem) {
                MenuItem menuItem = (MenuItem) e.getTarget();
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView) {
                    ImageView sticker = (ImageView) menuItem.getParentPopup().getOwnerNode();
                    saveImage(sticker.getImage());
                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        pasteItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem) {
                MenuItem menuItem = (MenuItem) e.getTarget();
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView) {
                    ImageView sticker = (ImageView) menuItem.getParentPopup().getOwnerNode();
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    if (clipboard.hasImage()) {
                        sticker.setImage(clipboard.getImage());
                        contextMenu.hide(); // 操作完成后隐藏菜单
                    }
                }
            }
        });

        closeItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem) {
                MenuItem menuItem = (MenuItem) e.getTarget();
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView) {
                    ImageView sticker = (ImageView) menuItem.getParentPopup().getOwnerNode();
                    root.getChildren().remove(sticker);
                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        destroyItem.setOnAction(e -> {
            clearStickers();
            hide();
            contextMenu.hide(); // 操作完成后隐藏菜单
        });

        contextMenu.getItems().addAll(
            copyItem, saveItem, new SeparatorMenuItem(),
            pasteItem, new SeparatorMenuItem(),
            closeItem, destroyItem
        );

        // 添加菜单显示和隐藏的监听器
        contextMenu.setOnShowing(e -> {
            stage.requestFocus(); // 确保窗口获得焦点
        });
    }

    private void saveImage(Image image) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Image");
        String timestamp = String.format("%1$tY%1$tm%1$td%1$tH%1$tM%1$tS",
                System.currentTimeMillis());
        fileChooser.setInitialFileName("SnapSticker_" + timestamp + ".png");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Image", "*.png")
        );
        
        String userHome = System.getProperty("user.home");
        File picturesDir = new File(userHome, "Pictures");
        if (picturesDir.exists()) {
            fileChooser.setInitialDirectory(picturesDir);
        }

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            } catch (IOException ignored) {
            }
        }
    }

    private void addStageStyles() {
        Scene scene = stage.getScene();
        scene.getStylesheets().add(
            Objects.requireNonNull(getClass().getResource("/styles/sticker.css")).toExternalForm()
        );
    }

    public void addSticker(ImageView sticker) {
        setupStickerBehavior(sticker);
        setupStickerEffect(sticker);
        root.getChildren().add(sticker);
    }

    private void setupStickerEffect(ImageView sticker) {
        // 创建阴影效果
        DropShadow borderEffect = new DropShadow();
        borderEffect.setOffsetX(0);
        borderEffect.setOffsetY(0);
        borderEffect.setColor(Color.TRANSPARENT);
        borderEffect.setSpread(0.4);
        sticker.setEffect(borderEffect);

        // 创建颜色变换的初始动画
        Timeline colorBreathingAnimation = new Timeline(
            // 开始 - 淡蓝色
            new KeyFrame(Duration.ZERO, 
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.8))),
            // 过渡到淡红色
            new KeyFrame(Duration.seconds(1), 
                new KeyValue(borderEffect.radiusProperty(), 15),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(255, 102, 102, 0.8))),
            // 过渡到淡黄色
            new KeyFrame(Duration.seconds(2), 
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(255, 204, 102, 0.8))),
            // 再次过渡到淡蓝色，完成循环
            new KeyFrame(Duration.seconds(3), 
                new KeyValue(borderEffect.radiusProperty(), 15),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.8)))
        );
        colorBreathingAnimation.setCycleCount(1); // 只播放一次

        // 创建固定的蓝色边框效果（获得焦点时）
        Timeline focusedEffect = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.2))),
            new KeyFrame(Duration.millis(200),
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.8)))
        );
        focusedEffect.setCycleCount(1);

        // 创建固定的蓝色边框效果（失去焦点时）
        Timeline unfocusedEffect = new Timeline(
            new KeyFrame(Duration.ZERO,
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.8))),
            new KeyFrame(Duration.millis(200),
                new KeyValue(borderEffect.radiusProperty(), 10),
                new KeyValue(borderEffect.colorProperty(), Color.rgb(102, 178, 255, 0.2)))
        );
        unfocusedEffect.setCycleCount(1);

        // 开始颜色变换动画
        colorBreathingAnimation.play();
        
        // 颜色变换动画结束后设置固定效果
        colorBreathingAnimation.setOnFinished(event -> {
            // 设置初始状态（较弱的蓝色效果）
            borderEffect.setRadius(10);
            borderEffect.setColor(Color.rgb(102, 178, 255, 0.2));
            
            // 添加鼠标事件监听
            sticker.setOnMouseEntered(e -> focusedEffect.play());
            sticker.setOnMouseExited(e -> unfocusedEffect.play());
        });

        // 确保贴图可以接收鼠标事件
        sticker.setPickOnBounds(true);
    }

    private void setupStickerBehavior(ImageView sticker) {
        final Delta dragDelta = new Delta();

        sticker.setOnContextMenuRequested(e -> {
            contextMenu.show(sticker, e.getScreenX(), e.getScreenY());
            // 显示菜单时触发高亮效果
            Timeline focusedEffect = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.2))),
                new KeyFrame(Duration.millis(200),
                    new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.8)))
            );
            focusedEffect.play();
        });

        // 监听菜单隐藏事件
        contextMenu.setOnHidden(e -> {
            // 如果鼠标不在贴图上，则恢复暗淡效果
            if (!sticker.isHover()) {
                Timeline unfocusedEffect = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.8))),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.2)))
                );
                unfocusedEffect.play();
            }
        });

        sticker.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                dragDelta.x = sticker.getX() - e.getScreenX();
                dragDelta.y = sticker.getY() - e.getScreenY();
                sticker.setCursor(Cursor.MOVE);
            }
        });

        sticker.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                sticker.setX(e.getScreenX() + dragDelta.x);
                sticker.setY(e.getScreenY() + dragDelta.y);
            }
        });

        sticker.setOnMouseReleased(e -> sticker.setCursor(Cursor.HAND));
        sticker.setOnMouseEntered(e -> {
            sticker.setCursor(Cursor.HAND);
            // 只有在菜单没有显示时才触发高亮效果
            if (!contextMenu.isShowing()) {
                Timeline focusedEffect = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.2))),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.8)))
                );
                focusedEffect.play();
            }
        });
        sticker.setOnMouseExited(e -> {
            // 只有在菜单没有显示时才触发暗淡效果
            if (!contextMenu.isShowing()) {
                Timeline unfocusedEffect = new Timeline(
                    new KeyFrame(Duration.ZERO,
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.8))),
                    new KeyFrame(Duration.millis(200),
                        new KeyValue(((DropShadow)sticker.getEffect()).colorProperty(), Color.rgb(102, 178, 255, 0.2)))
                );
                unfocusedEffect.play();
            }
        });
    }

    // 用于记录拖拽过程中的偏移量
    private static class Delta {
        double x, y;
    }
} 