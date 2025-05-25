package com.github.sticker.feature;

import com.github.sticker.util.StealthWindow;
import com.github.sticker.draw.Icon;
import com.github.sticker.draw.DrawingToolbar;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.ColorAdjust;
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
    
    // 菜单项
    private Menu sizeMenu;
    private CheckMenuItem shownItem;
    private MenuItem opacityItem;
    private MenuItem rotationItem;
    private MenuItem invertedItem;

    // 绘图工具栏相关
    private DrawingToolbar currentToolbar;

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

    /**
     * 移除贴图及其相关组件
     */
    private void removeSticker(ImageView sticker) {
        // 移除相关的工具栏和标签
        root.getChildren().removeIf(node -> 
            (node instanceof DrawingToolbar && node.getProperties().get("owner") == sticker) ||
            (node instanceof Label && node.getProperties().get("owner") == sticker)
        );
        
        // 移除贴图
        root.getChildren().remove(sticker);
        
        // 如果没有贴图了，隐藏窗口
        if (root.getChildren().stream().noneMatch(node -> node instanceof ImageView)) {
            hide();
        }
    }

    private void initializeContextMenu() {
        contextMenu = new ContextMenu();
        contextMenu.getStyleClass().add("sticker-context-menu");

        MenuItem copyItem = new MenuItem("Copy image");
        MenuItem saveItem = new MenuItem("Save image as...");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem replaceItem = new MenuItem("Replace by file...");
        
        // 创建缩放菜单
        Menu zoomMenu = new Menu("Zoom");
        
        // 第一组：预设缩放比例
        MenuItem zoom33Item = new MenuItem("33.3%");
        MenuItem zoom50Item = new MenuItem("50%");
        MenuItem zoom100Item = new MenuItem("100%");
        MenuItem zoom200Item = new MenuItem("200%");
        
        // 第二组：当前缩放和平滑选项
        MenuItem currentZoomItem = new MenuItem("100%         Current");
        currentZoomItem.setDisable(true);  // 禁用当前缩放项（仅用于显示）
        CheckMenuItem smoothingItem = new CheckMenuItem("Smoothing");
        smoothingItem.setSelected(true);   // 默认选中
        smoothingItem.setDisable(true);    // 暂时禁用

        // 添加分隔符和所有项目
        zoomMenu.getItems().addAll(
            zoom33Item, zoom50Item, zoom100Item, zoom200Item,
            new SeparatorMenuItem(),
            currentZoomItem, smoothingItem
        );

        // 创建图片处理菜单
        Menu imageProcessingMenu = new Menu("Image processing");
        MenuItem rotateLeftItem = new MenuItem("Rotate left");
        MenuItem rotateRightItem = new MenuItem("Rotate right");
        MenuItem flipHorizontalItem = new MenuItem("Horizontal flip");
        MenuItem flipVerticalItem = new MenuItem("Vertical flip");

        imageProcessingMenu.getItems().addAll(
            rotateLeftItem, rotateRightItem,
            new SeparatorMenuItem(),
            flipHorizontalItem, flipVerticalItem
        );

        MenuItem viewFolderItem = new MenuItem("View in folder");
        MenuItem closeItem = new MenuItem("Close and save");
        MenuItem destroyItem = new MenuItem("Destroy");
        shownItem = new CheckMenuItem("Shadow");
        CheckMenuItem showToolbarItem = new CheckMenuItem("Show toolbar");

        // 创建贴图大小和属性菜单
        sizeMenu = new Menu();  // 使用Menu替代MenuItem，作为属性的父菜单
        MenuItem zoomItem = new MenuItem("Zoom: 100%");
        rotationItem = new MenuItem("Rotation: 0°");
        opacityItem = new MenuItem("Opacity: 100%");
        invertedItem = new MenuItem("Color inverted: No");
        sizeMenu.getItems().addAll(zoomItem, rotationItem, opacityItem, invertedItem);

        // 设置菜单项样式
        sizeMenu.setStyle("-fx-text-fill: #666666;");
        zoomItem.setStyle("-fx-text-fill: #666666;");
        rotationItem.setStyle("-fx-text-fill: #666666;");
        opacityItem.setStyle("-fx-text-fill: #666666;");
        invertedItem.setStyle("-fx-text-fill: #666666;");

        // 禁用这些菜单项，使其只作为信息显示
        zoomItem.setDisable(true);
        rotationItem.setDisable(true);
        opacityItem.setDisable(true);
        invertedItem.setDisable(true);

        // 设置图片处理事件
        rotateLeftItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 逆时针旋转90度
                    sticker.setRotate((sticker.getRotate() - 90) % 360);
                    updateStickerSize(sticker);
                    contextMenu.hide();
                }
            }
        });

        rotateRightItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 顺时针旋转90度
                    sticker.setRotate((sticker.getRotate() + 90) % 360);
                    updateStickerSize(sticker);
                    contextMenu.hide();
                }
            }
        });

        flipHorizontalItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 水平翻转
                    sticker.setScaleX(sticker.getScaleX() * -1);
                    contextMenu.hide();
                }
            }
        });

        flipVerticalItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    // 垂直翻转
                    sticker.setScaleY(sticker.getScaleY() * -1);
                    contextMenu.hide();
                }
            }
        });

        // 设置缩放菜单事件处理
        zoom33Item.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    applyZoom(sticker, 0.333);
                    contextMenu.hide();
                }
            }
        });

        zoom50Item.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    applyZoom(sticker, 0.5);
                    contextMenu.hide();
                }
            }
        });

        zoom100Item.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    applyZoom(sticker, 1.0);
                    contextMenu.hide();
                }
            }
        });

        zoom200Item.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    applyZoom(sticker, 2.0);
                    contextMenu.hide();
                }
            }
        });

        // 禁用当前缩放项（仅用于显示）
        currentZoomItem.setDisable(true);

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
                    // 先移除贴图和相关组件，让用户可以立即继续操作
                    removeSticker(sticker);
                    contextMenu.hide(); // 操作完成后隐藏菜单

                    // 异步保存图片到历史文件夹
                    saveToHistory(sticker.getImage());
                }
            }
        });

        destroyItem.setOnAction(e -> {
            if (e.getTarget() instanceof MenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    removeSticker(sticker);
                    contextMenu.hide(); // 操作完成后隐藏菜单
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

        // 设置工具栏显示切换事件
        showToolbarItem.setOnAction(e -> {
            if (e.getTarget() instanceof CheckMenuItem menuItem) {
                if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                    for (Node node : root.getChildren()) {
                        if (node instanceof DrawingToolbar toolbar && toolbar.getProperties().get("owner") == sticker) {
                            toolbar.toggleVisibility();
                            menuItem.setSelected(toolbar.isShown());
                            break;
                        }
                    }
                }
            }
        });

        contextMenu.getItems().addAll(
                copyItem, saveItem, new SeparatorMenuItem(),
                zoomMenu, imageProcessingMenu, new SeparatorMenuItem(),
                pasteItem, replaceItem, new SeparatorMenuItem(),
                shownItem, showToolbarItem, new SeparatorMenuItem(),
                viewFolderItem, closeItem, destroyItem, new SeparatorMenuItem(),
                sizeMenu
        );
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
        // 设置shadow属性为true
        sticker.getProperties().put("shadow", Boolean.TRUE);
        setupStickerEffect(sticker);
        setupStickerBehavior(sticker);
        root.getChildren().add(sticker);
        
        // 确保窗口显示并置顶
        stage.show();
        stage.setAlwaysOnTop(true);
        stage.toFront();
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
        final double MAX_SCALE = 5.0;  // 限制最大缩放比例为 500%

        // 使用累积的滚动值来实现平滑缩放
        final double[] accumulatedScale = {1.0};

        // 创建工具栏
        DrawingToolbar toolbar = new DrawingToolbar(sticker);
        toolbar.getProperties().put("owner", sticker);
        currentToolbar = toolbar;

        // 添加工具栏到根节点
        root.getChildren().add(toolbar);

        // 使用boundsInLocal来获取实际显示尺寸，避免受缩放影响
        // 绑定工具栏位置到贴图底部右侧，保持5px间距
        toolbar.layoutXProperty().bind(sticker.layoutXProperty().add(sticker.fitWidthProperty()).subtract(toolbar.widthProperty()));
        toolbar.layoutYProperty().bind(sticker.layoutYProperty().add(sticker.fitHeightProperty()).add(5));


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

        // 创建一个标志来控制滚轮缩放
        final boolean[] isContextMenuShowing = {false};

        // 添加菜单显示和隐藏的监听器
        contextMenu.setOnShowing(e -> {
            isContextMenuShowing[0] = true;
            // 更新尺寸显示
            updateStickerSize(sticker);

            stage.requestFocus(); // 确保窗口获得焦点
            if (contextMenu.getOwnerNode() instanceof ImageView stickerTemp) {
                updateContextMenuItems(stickerTemp);
            }
        });

        contextMenu.setOnHiding(e -> {
            isContextMenuShowing[0] = false;
        });

        // 添加滚轮缩放事件到图片上
        sticker.setOnScroll(e -> {
            // 如果菜单正在显示，则不处理滚轮事件
            if (isContextMenuShowing[0]) {
                e.consume();
                return;
            }

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

    // 添加更新贴图尺寸的辅助方法
    private void updateStickerSize(ImageView sticker) {
        if (contextMenu.getOwnerNode() == sticker) {
            // 获取当前实际显示的尺寸
            double width = sticker.getBoundsInLocal().getWidth();
            double height = sticker.getBoundsInLocal().getHeight();
            
            // 如果尺寸为0，使用原始图片尺寸
            if (width == 0 || height == 0) {
                width = sticker.getImage().getWidth();
                height = sticker.getImage().getHeight();
            }
            
            // 计算缩放比例
            double scale = calculateScale(sticker);
            
            // 更新菜单显示
            for (MenuItem item : contextMenu.getItems()) {
                if (item instanceof Menu menu) {
                    // 更新尺寸显示
                    if (menu.getText().isEmpty() || menu.getText().matches("\\d+ × \\d+")) {
                        menu.setText(String.format("%d × %d", Math.round(width), Math.round(height)));
                        
                        // 更新属性菜单中的Zoom项
                        for (MenuItem subItem : menu.getItems()) {
                            if (subItem.getText() != null && subItem.getText().startsWith("Zoom:")) {
                                subItem.setText("Zoom: " + formatScale(scale));
                                break;
                            }
                        }
                    }
                    // 更新Zoom菜单中的Current项
                    else if ("Zoom".equals(menu.getText())) {
                        for (MenuItem subItem : menu.getItems()) {
                            if (subItem.getText() != null && subItem.getText().contains("Current")) {
                                subItem.setText(formatScale(scale) + "    Current");
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // 应用缩放的辅助方法
    private void applyZoom(ImageView sticker, double scale) {
        // 获取原始图片尺寸
        double originalWidth = sticker.getImage().getWidth();
        double originalHeight = sticker.getImage().getHeight();

        // 清除任何之前的变换
        sticker.setScaleX(1.0);
        sticker.setScaleY(1.0);

        // 应用新的缩放
        sticker.setFitWidth(originalWidth * scale);
        sticker.setFitHeight(originalHeight * scale);
        sticker.setPreserveRatio(true);

        // 更新当前缩放显示
        updateCurrentZoom(sticker, scale);

        // 显示缩放标签
        showScaleLabel(sticker, scale);
    }

    private void showScaleLabel(ImageView sticker, double scale) {
        // 创建或获取标签
        Label existingLabel = null;
        for (Node node : root.getChildren()) {
            if (node instanceof Label label && label.getProperties().containsKey("scale_label") 
                && label.getProperties().get("owner") == sticker) {
                existingLabel = label;
                break;
            }
        }

        // 创建或使用现有标签
        final Label scaleLabel;
        if (existingLabel != null) {
            scaleLabel = existingLabel;
        } else {
            scaleLabel = new Label();
            scaleLabel.getProperties().put("scale_label", true);
            scaleLabel.getProperties().put("owner", sticker);
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
            scaleLabel.setMouseTransparent(true);  // 禁用标签的事件响应
            root.getChildren().add(scaleLabel);

            // 绑定位置到贴图上方
            scaleLabel.layoutXProperty().bind(sticker.layoutXProperty());
            scaleLabel.layoutYProperty().bind(sticker.layoutYProperty().subtract(25));
        }

        // 更新标签文本和显示状态
        scaleLabel.setText(formatScale(scale));
        scaleLabel.setVisible(true);
        scaleLabel.setOpacity(1.0);

        // 创建淡出动画
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(0.5), scaleLabel);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(e -> scaleLabel.setVisible(false));

        // 5秒后开始淡出
        Timeline hideTimer = new Timeline(new KeyFrame(Duration.seconds(5), e -> fadeOut.play()));
        hideTimer.play();
    }

    // 更新当前缩放显示
    private void updateCurrentZoom(ImageView sticker, double scale) {
        for (MenuItem item : contextMenu.getItems()) {
            if (item instanceof Menu menu && "Zoom".equals(menu.getText())) {
                for (MenuItem subItem : menu.getItems()) {
                    String itemText = subItem.getText();
                    if (itemText != null && itemText.contains("Current")) {
                        subItem.setText(formatScale(scale) + "    Current");
                        break;
                    }
                }
                break;
            }
        }
    }

    // 用于记录拖拽过程中的偏移量
    private static class Delta {
        double x, y;
    }

    // 更新菜单显示的统一方法
    private void updateContextMenuItems(ImageView sticker) {
        // 更新Shadow选中状态
        Object shadowValue = sticker.getProperties().get("shadow");
        boolean isShown = shadowValue instanceof Boolean ? (Boolean) shadowValue : true; // 默认为true
        shownItem.setSelected(isShown);

        // 更新尺寸信息
        int width = (int) sticker.getFitWidth();
        int height = (int) sticker.getFitHeight();
        if (width == 0 || height == 0) {
            // 如果FitWidth/FitHeight为0，使用原始图片尺寸
            width = (int) sticker.getImage().getWidth();
            height = (int) sticker.getImage().getHeight();
        }
        sizeMenu.setText(String.format("%d × %d", width, height));

        // 计算缩放比例
        double scale = calculateScale(sticker);
        
        // 更新当前缩放显示
        for (MenuItem item : contextMenu.getItems()) {
            if (item instanceof Menu menu && "Zoom".equals(menu.getText())) {
                for (MenuItem subItem : menu.getItems()) {
                    String itemText = subItem.getText();
                    if (itemText != null && itemText.contains("Current")) {
                        subItem.setText(String.format("%.1f%%         Current", scale * 100));
                        break;
                    }
                }
                break;
            }
        }

        // 更新其他属性
        double opacity = sticker.getOpacity();
        int opacityPercentage = (int) (opacity * 100);
        opacityItem.setText(String.format("Opacity: %d%%", opacityPercentage));

        // 获取旋转角度
        double rotation = sticker.getRotate();
        rotationItem.setText(String.format("Rotation: %.1f°", rotation));

        // 检查是否有颜色反转效果
        boolean isInverted = sticker.getEffect() instanceof ColorAdjust;
        invertedItem.setText("Color inverted: " + (isInverted ? "Yes" : "No"));
    }

    // 统一计算缩放比例的方法
    private double calculateScale(ImageView sticker) {
        if (sticker.getFitWidth() > 0) {
            // 使用设置的适应宽度来计算比例
            return sticker.getFitWidth() / sticker.getImage().getWidth();
        }
        // 使用实际显示尺寸计算比例
        return sticker.getBoundsInLocal().getWidth() / sticker.getImage().getWidth();
    }

    // 格式化缩放比例显示
    private String formatScale(double scale) {
        return String.format("%.0f%%", scale * 100);
    }
} 