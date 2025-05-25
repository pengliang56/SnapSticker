package com.github.sticker.feature;

import com.github.sticker.util.StealthWindow;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.*;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 贴图窗口管理类
 * 创建一个覆盖所有屏幕的透明窗口用于贴图
 */
public class StickerStage {
    private static StickerStage instance;
    private Stage stage;
    private Pane root;
    private Rectangle2D totalBounds;
    private ContextMenu contextMenu;

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

        // 创建场景
        Scene scene = new Scene(root);
        scene.setFill(Color.rgb(0, 0, 0, 0.00));
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/sticker.css")).toExternalForm()
        );

        stage.setScene(scene);
        StealthWindow.configure(stage);
        stage.show();
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
        contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("sticker-context-menu");

        MenuItem copyItem = new MenuItem("Copy image");
        MenuItem saveItem = new MenuItem("Save image as...");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem replaceItem = new MenuItem("Replace by file...");
        MenuItem resetScaleItem = new MenuItem("Reset to 100%");
        MenuItem viewFolderItem = new MenuItem("View in folder");
        MenuItem closeItem = new MenuItem("Close and save");
        MenuItem destroyItem = new MenuItem("Destroy");
        CheckMenuItem shownItem = new CheckMenuItem("Shadow");

        // 设置菜单项事件处理
        copyItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putImage(sticker.getImage());
                    clipboard.setContent(content);
                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        saveItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    saveImage(sticker.getImage());
                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        resetScaleItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 获取原始图片尺寸
                    double originalWidth = sticker.getImage().getWidth();
                    double originalHeight = sticker.getImage().getHeight();

                    // 重置为原始大小
                    sticker.setFitWidth(originalWidth);
                    sticker.setFitHeight(originalHeight);

                    contextMenu.hide(); // 操作完成后隐藏菜单
                }
            }
        });

        viewFolderItem.setOnAction(e -> {
            try {
                // 获取历史文件夹路径
                String userHome = System.getProperty("user.home");
                File picturesDir = new File(userHome, "Pictures");
                File historyDir = new File(picturesDir, "SnapSticker/history");

                // 确保文件夹存在
                if (!historyDir.exists()) {
                    historyDir.mkdirs();
                }

                // 使用系统默认的文件管理器打开文件夹
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    // Windows
                    Runtime.getRuntime().exec("explorer.exe \"" + historyDir.getAbsolutePath() + "\"");
                } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    // macOS
                    Runtime.getRuntime().exec("open \"" + historyDir.getAbsolutePath() + "\"");
                } else {
                    // Linux and others (assuming xdg-open is available)
                    Runtime.getRuntime().exec("xdg-open \"" + historyDir.getAbsolutePath() + "\"");
                }

                contextMenu.hide(); // 操作完成后隐藏菜单
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        pasteItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    if (clipboard.hasImage()) {
                        // 只保存当前位置
                        double currentX = sticker.getX();
                        double currentY = sticker.getY();

                        // 设置新图片
                        Image newImage = clipboard.getImage();
                        sticker.setImage(newImage);

                        // 清除可能存在的大小限制
                        sticker.setFitWidth(0);
                        sticker.setFitHeight(0);

                        // 保持原来的位置
                        sticker.setX(currentX);
                        sticker.setY(currentY);

                        contextMenu.hide(); // 操作完成后隐藏菜单
                    }
                }
            }
        });

        // 添加替换图片的事件处理
        replaceItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Replace Image");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
                    );

                    // 设置初始目录为用户的图片文件夹
                    String userHome = System.getProperty("user.home");
                    File picturesDir = new File(userHome, "Pictures");
                    if (picturesDir.exists()) {
                        fileChooser.setInitialDirectory(picturesDir);
                    }

                    // 显示文件选择器
                    File file = fileChooser.showOpenDialog(stage);
                    if (file != null) {
                        try {
                            // 只记录当前贴图的位置
                            double currentX = sticker.getLayoutX();
                            double currentY = sticker.getLayoutY();

                            // 加载新图片
                            Image newImage = new Image(file.toURI().toString());
                            sticker.setImage(newImage);

                            // 重置大小限制，让图片显示原始尺寸
                            sticker.setFitWidth(0);
                            sticker.setFitHeight(0);
                            sticker.setPreserveRatio(true);

                            // 只恢复位置
                            sticker.setLayoutX(currentX);
                            sticker.setLayoutY(currentY);

                            contextMenu.hide(); // 操作完成后隐藏菜单
                        } catch (Exception ex) {
                            System.err.println("Error loading image: " + ex.getMessage());
                        }
                    }
                }
            }
        });

        closeItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 先移除贴图，让用户可以立即继续操作
                    root.getChildren().remove(sticker);
                    contextMenu.hide(); // 操作完成后隐藏菜单

                    // 如果没有贴图了，隐藏窗口
                    if (root.getChildren().isEmpty()) {
                        hide();
                    }

                    // 异步保存图片到历史文件夹
                    saveToHistory(sticker.getImage());
                }
            }
        });

        destroyItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    root.getChildren().remove(sticker);
                    contextMenu.hide(); // 操作完成后隐藏菜单

                    // 如果没有贴图了，隐藏窗口
                    if (root.getChildren().isEmpty()) {
                        hide();
                    }
                }
            }
        });

        // 添加呼吸灯控制的事件处理
        shownItem.setOnAction(e -> {
            if (e.getTarget() instanceof CheckMenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    boolean isShown = menuItem.isSelected();
                    sticker.getProperties().put("shadow", isShown);
                    
                    // 获取当前效果
                    DropShadow effect = (DropShadow) sticker.getEffect();
                    if (effect != null) {
                        if (isShown) {
                            // 如果是激活状态，显示正常效果
                            if (sticker.isFocused()) {
                                effect.setColor(Color.rgb(102, 178, 255, 0.8));
                            } else {
                                effect.setColor(Color.rgb(255, 255, 255, 0.3));
                            }
                        } else {
                            // 如果是关闭状态，隐藏效果
                            effect.setColor(Color.TRANSPARENT);
                        }
                    }
                }
            }
        });

        contextMenu.getItems().addAll(
                copyItem, saveItem, new SeparatorMenuItem(),
                resetScaleItem, new SeparatorMenuItem(),
                pasteItem, replaceItem, new SeparatorMenuItem(),
                shownItem, new SeparatorMenuItem(),
                viewFolderItem, closeItem, destroyItem
        );

        // 添加菜单显示和隐藏的监听器
        contextMenu.setOnShowing(e -> {
            stage.requestFocus(); // 确保窗口获得焦点
            if (contextMenu.getOwnerNode() instanceof ImageView sticker) {
                Object shadowValue = sticker.getProperties().get("shadow");
                boolean isShown = shadowValue instanceof Boolean && (Boolean) shadowValue;
                shownItem.setSelected(isShown);
            }
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

    private void saveToHistory(Image image) {
        // 使用CompletableFuture异步处理保存操作
        CompletableFuture.runAsync(() -> {
            try {
                // 获取用户图片目录
                String userHome = System.getProperty("user.home");
                File picturesDir = new File(userHome, "Pictures");

                // 创建 SnapSticker/history 目录
                File historyDir = new File(picturesDir, "SnapSticker/history");
                if (!historyDir.exists()) {
                    historyDir.mkdirs();
                }

                // 生成文件名（使用时间戳）
                String timestamp = String.format("%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS",
                        System.currentTimeMillis());
                File outputFile = new File(historyDir, "SnapSticker_" + timestamp + ".png");

                // 保存图片
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", outputFile);

                System.out.println("Image saved to history: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                // 仅打印错误日志，不影响用户操作
                e.printStackTrace();
            }
        });
    }

    private void addStageStyles() {
        Scene scene = stage.getScene();
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/sticker.css")).toExternalForm()
        );
    }

    public void addSticker(ImageView sticker) {
        sticker.getProperties().put("shadow", true);
        setupStickerEffect(sticker);
        setupStickerBehavior(sticker);
        root.getChildren().add(sticker);
    }

    private void setupStickerEffect(ImageView sticker) {
        // 创建阴影效果
        DropShadow borderEffect = new DropShadow();
        borderEffect.setOffsetX(0);
        borderEffect.setOffsetY(0);
        borderEffect.setRadius(10);
        borderEffect.setSpread(0.4);

        // 定义颜色状态
        Color dimColor = Color.rgb(255, 255, 255, 0.3);    // 淡白色状态（非活跃）
        Color activeColor = Color.rgb(102, 178, 255, 0.8); // 高亮蓝色状态（活跃）

        // 用于记录拖拽的偏移量
        Delta dragDelta = new Delta();

        // 设置初始效果
        borderEffect.setColor(activeColor);
        sticker.setEffect(borderEffect);

        // 为贴图添加shadow属性，默认为true
        sticker.getProperties().put("shadow", true);

        // 创建初始呼吸灯动画
        Timeline breathingAnimation = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(borderEffect.radiusProperty(), 10),
                        new KeyValue(borderEffect.colorProperty(), activeColor)),
                new KeyFrame(Duration.seconds(1),
                        new KeyValue(borderEffect.radiusProperty(), 15),
                        new KeyValue(borderEffect.colorProperty(), Color.rgb(255, 102, 102, 0.8))),
                new KeyFrame(Duration.seconds(2),
                        new KeyValue(borderEffect.radiusProperty(), 10),
                        new KeyValue(borderEffect.colorProperty(), activeColor))
        );
        breathingAnimation.setCycleCount(1);  // 只播放一次

        // 动画结束后保持高亮状态
        breathingAnimation.setOnFinished(e -> {
            if (Boolean.TRUE.equals(sticker.getProperties().get("shadow"))) {
                borderEffect.setRadius(10);
                borderEffect.setColor(activeColor);
            } else {
                borderEffect.setRadius(10);
                borderEffect.setColor(dimColor);
            }
        });

        // 确保贴图可以接收鼠标事件和焦点
        sticker.setPickOnBounds(true);
        sticker.setFocusTraversable(true);

        // 修改焦点变化监听
        sticker.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {  // 获得焦点
                if (Boolean.TRUE.equals(sticker.getProperties().get("shadow"))) {
                    borderEffect.setRadius(10);
                    borderEffect.setColor(activeColor);
                }

                // 将贴图移到最前面
                sticker.toFront();

                // 将其他贴图设置为非活跃状态
                root.getChildren().forEach(node -> {
                    if (node instanceof ImageView otherSticker && node != sticker) {
                        DropShadow effect = (DropShadow) otherSticker.getEffect();
                        if (Boolean.TRUE.equals(otherSticker.getProperties().get("shadow"))) {
                            if (effect != null) {
                                effect.setColor(dimColor);
                                effect.setRadius(10);
                            }
                        }
                    }
                });
            } else {  // 失去焦点
                if (Boolean.TRUE.equals(sticker.getProperties().get("shadow"))) {
                    borderEffect.setRadius(10);
                    borderEffect.setColor(dimColor);
                } else {
                    borderEffect.setRadius(10);
                    borderEffect.setColor(Color.TRANSPARENT);
                }
            }
        });

        // 设置拖动事件到图片上
        sticker.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                // 如果菜单正在显示，先隐藏菜单
                if (contextMenu != null && contextMenu.isShowing()) {
                    contextMenu.hide();
                }

                // 记录鼠标点击位置相对于图片的偏移
                dragDelta.x = e.getSceneX() - sticker.getLayoutX();
                dragDelta.y = e.getSceneY() - sticker.getLayoutY();
                sticker.setCursor(Cursor.MOVE);

                // 请求焦点并置于顶层
                sticker.requestFocus();
                sticker.toFront();
            } else if (e.isSecondaryButtonDown()) {
                if (contextMenu != null && contextMenu.isShowing()) {
                    contextMenu.hide();
                }
                contextMenu.show(sticker, e.getScreenX(), e.getScreenY());
                sticker.requestFocus();
                sticker.toFront();
            }
            e.consume();
        });

        sticker.setOnMouseDragged(e -> {
            if (e.isPrimaryButtonDown()) {
                double newX = e.getSceneX() - dragDelta.x;
                double newY = e.getSceneY() - dragDelta.y;
                sticker.setLayoutX(newX);
                sticker.setLayoutY(newY);
            }
            e.consume();
        });

        sticker.setOnMouseReleased(e -> {
            sticker.setCursor(Cursor.DEFAULT);
            e.consume();
        });

        // 初始状态播放一次呼吸动画并请求焦点
        breathingAnimation.play();
    }

    private void setupStickerBehavior(ImageView sticker) {
        final double MIN_SCALE = 0.1;
        final double MAX_SCALE = 10.0;

        // 使用累积的滚动值来实现平滑缩放
        final double[] accumulatedScale = {1.0};

        // 创建缩放比例显示标签
        Label scaleLabel = new Label("100%");
        scaleLabel.setStyle("-fx-background-color: rgba(255, 255, 255, 0.8);" +
                "-fx-text-fill: black;" +
                "-fx-padding: 2 8;" +
                "-fx-background-radius: 3;" +
                "-fx-border-radius: 3;" +
                "-fx-border-color: rgba(204, 204, 204, 0.8);" +
                "-fx-border-width: 1;" +
                "-fx-font-size: 12px;" +
                "-fx-font-weight: bold;");
        scaleLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        scaleLabel.setCursor(Cursor.HAND);
        scaleLabel.setVisible(false);
        scaleLabel.setOpacity(0);
        scaleLabel.setMouseTransparent(true);  // 禁用标签的事件响应

        // 记录原始尺寸
        final double[] originalWidth = {sticker.getFitWidth()};
        final double[] originalHeight = {sticker.getFitHeight()};


        // 设置图片的事件接收
        sticker.setPickOnBounds(true);
        sticker.setMouseTransparent(false);

        // 移除右键菜单请求事件（防止双触发）
        sticker.setOnContextMenuRequested(null);

        // 添加标签到根节点
        root.getChildren().add(scaleLabel);

        // 更新标签位置
        scaleLabel.layoutXProperty().bind(sticker.layoutXProperty());
        scaleLabel.layoutYProperty().bind(sticker.layoutYProperty().subtract(25));

        // 创建淡出动画
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.5), scaleLabel);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> scaleLabel.setVisible(false));

        // 添加滚轮缩放事件到图片上
        sticker.setOnScroll(e -> {
            boolean isCtrlDown = e.isControlDown();
            double scrollAmount = e.getDeltaY();
            double zoomFactor = Math.exp(scrollAmount * 0.002);
            zoomFactor = isCtrlDown ? Math.exp(scrollAmount * 0.001) : zoomFactor;

            double newScale = accumulatedScale[0] * zoomFactor;

            if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
                // 更新缩放值
                accumulatedScale[0] = newScale;

                // 直接设置新的尺寸
                double newWidth = originalWidth[0] * newScale;
                double newHeight = originalHeight[0] * newScale;

                sticker.setFitWidth(newWidth);
                sticker.setFitHeight(newHeight);

                updateScaleLabel(scaleLabel, newScale, fadeOut);
            }

            e.consume();
        });
    }

    // 更新缩放比例标签的辅助方法
    private void updateScaleLabel(Label label, double scale, FadeTransition fadeOut) {
        fadeOut.stop();
        int percentage = (int) (scale * 100);
        label.setText(percentage + "%");
        label.setVisible(true);
        label.setOpacity(1.0);

        // 5秒后开始淡出
        Timeline hideTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> fadeOut.play()));
        hideTimer.play();
    }

    // 用于记录拖拽过程中的偏移量
    private static class Delta {
        double x, y;
    }
} 