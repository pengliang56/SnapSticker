/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.service;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.geometry.Rectangle2D;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.RenderingHints;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PaneImplement {
    private double startX, startY, endX, endY;
    private Pane root;
    private Stage captureStage;
    private Rectangle selectionRect; // 保存选择框
    private Rectangle overlayRect; // 保存用于遮罩的矩形框
    private static final double OVERLAY_OPACITY = 0.5; // 增加遮罩透明度
    // 添加调试标志
    private static final boolean DEBUG = true;
    // 添加容器引用到类变量中，以便在所有方法中访问
    private StackPane imageContainer;
    // 添加静态标志，确保同一时间只有一个截图窗口
    private static final AtomicBoolean CAPTURE_IN_PROGRESS = new AtomicBoolean(false);
    // 边框控制点容器
    private StackPane borderHandles;

    // 截图选择后等待确认的临时存储
    private double finalX1, finalY1, finalX2, finalY2;
    private boolean isMovingSelection = false; // 是否正在移动整个选区
    private double moveStartX, moveStartY; // 移动开始位置

    // 调整大小控制
    private boolean isResizing = false;
    private String resizeDirection = ""; // "n", "s", "e", "w", "center"
    private double resizeStartX, resizeStartY;

    public void snapshot(Stage primaryStage) {
        try {
            // 避免同时进行多个截图
            if (!CAPTURE_IN_PROGRESS.compareAndSet(false, true)) {
                debugLog("已有截图正在进行，忽略本次请求");
                return;
            }

            debugLog("开始截图流程");

            // 获取鼠标所在屏幕
            Screen currentScreen = getCurrentMouseScreen();
            Rectangle2D screenBounds = currentScreen.getBounds();

            // 创建一个透明的全屏窗口
            Stage screenshotStage = new Stage();
            screenshotStage.initStyle(StageStyle.TRANSPARENT);

            if (primaryStage != null) {
                screenshotStage.initOwner(primaryStage);
            }

            // 保存为当前截图窗口
            captureStage = screenshotStage;

            // 创建截图的根面板
            root = new Pane();
            root.setStyle("-fx-background-color: rgba(0, 0, 0, 0);"); // 完全透明背景

            // 创建半透明遮罩
            overlayRect = new Rectangle(
                    screenBounds.getWidth(),
                    screenBounds.getHeight());
            overlayRect.setFill(Color.color(0, 0, 0, OVERLAY_OPACITY)); // 半透明黑色遮罩
            root.getChildren().add(overlayRect);

            // 创建场景
            Scene scene = new Scene(root, screenBounds.getWidth(), screenBounds.getHeight());
            scene.setFill(Color.TRANSPARENT); // 设置场景背景为透明

            // 设置场景上的ESC键全局捕获 - 这是最重要的一层保障
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("ESC键被按下 - 立即取消截图");

                    // 尝试取消截图
                    try {
                        cancelCapture();
                    } catch (Exception ex) {
                        debugLog("取消截图时出错: " + ex.getMessage());

                        // 如果取消失败，尝试强制关闭窗口
                        try {
                            if (captureStage != null)
                                captureStage.close();
                        } catch (Exception ignored) {
                        }

                        // 确保状态被重置
                        CAPTURE_IN_PROGRESS.set(false);

                        // 通知全局监听器
                        try {
                            GlobalKeyListener.getInstance().notifyScreenshotFullyCompleted();
                        } catch (Exception ignored) {
                        }
                    }

                    e.consume(); // 确保事件不再传播
                    return;
                }
            });

            // 设置截图窗口
            screenshotStage.setScene(scene);
            screenshotStage.setX(screenBounds.getMinX());
            screenshotStage.setY(screenBounds.getMinY());
            screenshotStage.setWidth(screenBounds.getWidth());
            screenshotStage.setHeight(screenBounds.getHeight());

            // 设置窗口置顶，确保它覆盖其他窗口
            screenshotStage.setAlwaysOnTop(true);

            // 显示窗口
            screenshotStage.show();

            // 设置初始UI元素和鼠标事件
            setupInitialCaptureUI(scene);
            setupMouseEvents(scene);

            // 添加全局键盘事件监听器 - 这是第二层保障
            scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("ESC键被按下(事件过滤器) - 立即取消截图");
                    cancelCapture();
                    e.consume();
                }
            });

            // 添加窗口关闭事件监听器 - 确保清理资源
            screenshotStage.setOnCloseRequest(e -> {
                debugLog("截图窗口关闭事件被触发");
                resetCaptureState();
            });

            debugLog("截图窗口已创建并显示");
        } catch (Exception e) {
            debugLog("截图初始化出错: " + e.getMessage());
            e.printStackTrace();

            // 确保重置状态
            CAPTURE_IN_PROGRESS.set(false);

            // 尝试通知全局监听器
            try {
                GlobalKeyListener.getInstance().notifyScreenshotFullyCompleted();
            } catch (Exception ex) {
                debugLog("通知截图完成时出错: " + ex.getMessage());
            }
        }
    }

    /**
     * 取消截图操作
     */
    public void cancelCapture() {
        try {
            debugLog("取消截图操作被调用");

            // 检查截图窗口是否存在
            if (captureStage != null) {
                debugLog("关闭截图窗口");

                // 先移除所有可能阻塞ESC键的事件处理器
                if (captureStage.getScene() != null) {
                    Scene scene = captureStage.getScene();
                    scene.setOnKeyPressed(null);
                    scene.setOnKeyReleased(null);
                    scene.setOnMousePressed(null);
                    scene.setOnMouseDragged(null);
                    scene.setOnMouseReleased(null);
                    scene.setOnMouseMoved(null);
                    // 移除所有事件过滤器
                    scene.removeEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    });
                    scene.removeEventFilter(MouseEvent.ANY, event -> {
                    });
                    debugLog("已移除所有事件处理器");
                }

                // 保存引用以便关闭，并立即清空类变量
                Stage stageToClose = captureStage;
                captureStage = null;

                // 确保关闭窗口，使用try-catch增强健壮性
                try {
                    stageToClose.close();
                    debugLog("截图窗口已关闭");
                } catch (Exception e) {
                    debugLog("关闭截图窗口时出错: " + e.getMessage());
                }
            } else {
                debugLog("截图窗口已为null，无需关闭");
            }

            // 只清理选择框和遮罩相关的UI元素
            if (root != null) {
                // 移除选择框
                if (selectionRect != null) {
                    root.getChildren().remove(selectionRect);
                    selectionRect = null;
                }

                // 移除遮罩
                if (overlayRect != null) {
                    root.getChildren().remove(overlayRect);
                    overlayRect = null;
                }

                // 移除边框控制点
                if (borderHandles != null) {
                    root.getChildren().remove(borderHandles);
                    borderHandles.getChildren().clear();
                    borderHandles = null;
                }

                // 移除所有其他遮罩层
                root.getChildren().removeIf(node -> node instanceof Shape);

                root = null;
            }

            // 重置状态标志
            CAPTURE_IN_PROGRESS.set(false);

            // 通知GlobalKeyListener
            try {
                GlobalKeyListener.getInstance().notifyScreenshotFullyCompleted();
                debugLog("已通知GlobalKeyListener截图已取消");
            } catch (Exception e) {
                debugLog("通知GlobalKeyListener时出错: " + e.getMessage());
            }

            debugLog("截图已完全取消");
        } catch (Exception e) {
            debugLog("取消截图时发生严重错误: " + e.getMessage());
            e.printStackTrace();

            // 确保重置状态
            CAPTURE_IN_PROGRESS.set(false);
        }
    }

    /**
     * 重置截图状态 - 确保可以立即进行下一次截图
     */
    private void resetCaptureState() {
        try {
            // 重置状态标志
            CAPTURE_IN_PROGRESS.set(false);

            // 通知GlobalKeyListener
            try {
                notifyScreenshotFullyCompleted();
                debugLog("通知已完全完成截图");
            } catch (Exception e) {
                debugLog("通知截图完成时出错: " + e.getMessage());
            }

            // 立即调用垃圾回收器，帮助释放资源
            System.gc();

            debugLog("截图状态已重置，可以立即再次截图");
        } catch (Exception e) {
            debugLog("重置截图状态时出错: " + e.getMessage());

            // 确保至少重置标志位
            CAPTURE_IN_PROGRESS.set(false);
        }
    }

    /**
     * 显示截图窗口后的初始化 - 添加提示信息和十字光标
     */
    private void setupInitialCaptureUI(Scene scene) {
        // 添加提示文本
        javafx.scene.text.Text helpText = new javafx.scene.text.Text("按住鼠标左键并拖动以选择区域，ESC键取消");
        helpText.setFill(Color.WHITE);
        helpText.setStroke(Color.BLACK);
        helpText.setStrokeWidth(0.5);
        helpText.setFont(javafx.scene.text.Font.font("Microsoft YaHei", javafx.scene.text.FontWeight.BOLD, 14));

        // 创建一个半透明的背景框
        Rectangle textBg = new Rectangle();
        textBg.setFill(Color.color(0, 0, 0, 0.6));
        textBg.setArcWidth(10);
        textBg.setArcHeight(10);

        // 将文本和背景组合
        StackPane helpPane = new StackPane();
        helpPane.getChildren().addAll(textBg, helpText);
        helpPane.setPadding(new javafx.geometry.Insets(10, 15, 10, 15));

        // 调整背景大小
        textBg.widthProperty().bind(helpText.layoutBoundsProperty().map(b -> b.getWidth() + 30));
        textBg.heightProperty().bind(helpText.layoutBoundsProperty().map(b -> b.getHeight() + 20));

        // 设置提示到顶部中间位置
        helpPane.setLayoutX((scene.getWidth() - helpText.getLayoutBounds().getWidth()) / 2 - 15);
        helpPane.setLayoutY(50);

        // 添加到根面板
        root.getChildren().add(helpPane);

        // 创建自定义十字光标
        createCustomCrosshairCursor(scene);

        // 创建屏幕边框高亮 - 显示我们在当前屏幕上
        createScreenBorder(scene);

        // 设置淡出动画 - 3秒后渐隐
        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(
                javafx.util.Duration.seconds(3), helpPane);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setDelay(javafx.util.Duration.seconds(2));
        fadeOut.play();

        debugLog("添加了截图提示信息和十字光标");
    }

    /**
     * 创建屏幕边框高亮，显示我们在哪个屏幕上
     */
    private void createScreenBorder(Scene scene) {
        // 创建高亮边框 - 比屏幕小一点，这样可以看到边界
        Rectangle border = new Rectangle(
                5, 5,
                scene.getWidth() - 10,
                scene.getHeight() - 10);
        border.setFill(null); // 透明填充
        border.setStroke(Color.DODGERBLUE); // 使用蓝色
        border.setStrokeWidth(2); // 粗线
        border.getStrokeDashArray().addAll(10.0, 5.0); // 虚线效果
        border.setMouseTransparent(true); // 不干扰鼠标事件

        // 添加闪烁动画效果
        javafx.animation.FadeTransition pulse = new javafx.animation.FadeTransition(
                javafx.util.Duration.seconds(1), border);
        pulse.setFromValue(0.2);
        pulse.setToValue(0.8);
        pulse.setCycleCount(10); // 闪烁4次
        pulse.setAutoReverse(true);
        pulse.play();

        // 添加到根面板的最底层
        if (!root.getChildren().isEmpty()) {
            root.getChildren().add(0, border);
        } else {
            root.getChildren().add(border);
        }

        // 添加四个角的标记
        createCornerMarker(5, 5, scene); // 左上
        createCornerMarker(scene.getWidth() - 5, 5, scene); // 右上
        createCornerMarker(5, scene.getHeight() - 5, scene); // 左下
        createCornerMarker(scene.getWidth() - 5, scene.getHeight() - 5, scene); // 右下

        debugLog("添加了屏幕边框高亮");
    }

    /**
     * 创建屏幕角标记
     */
    private void createCornerMarker(double x, double y, Scene scene) {
        // 创建L形角标
        Group cornerGroup = new Group();
        cornerGroup.setMouseTransparent(true);

        // 水平线
        Rectangle hLine = new Rectangle(20, 3);
        hLine.setFill(Color.DODGERBLUE);
        hLine.setArcWidth(3);
        hLine.setArcHeight(3);

        // 垂直线
        Rectangle vLine = new Rectangle(3, 20);
        vLine.setFill(Color.DODGERBLUE);
        vLine.setArcWidth(3);
        vLine.setArcHeight(3);

        // 根据位置调整标记的方向和位置
        if (x <= 10 && y <= 10) { // 左上角
            // 不需要调整位置
            cornerGroup.getChildren().addAll(hLine, vLine);
        } else if (x >= scene.getWidth() - 10 && y <= 10) { // 右上角
            hLine.setLayoutX(-20 + 3); // 调整水平线位置，留出与垂直线的交叠
            cornerGroup.getChildren().addAll(hLine, vLine);
            cornerGroup.setLayoutX(x - 3); // 整体右移
        } else if (x <= 10 && y >= scene.getHeight() - 10) { // 左下角
            vLine.setLayoutY(-20 + 3); // 调整垂直线位置，留出与水平线的交叠
            cornerGroup.getChildren().addAll(hLine, vLine);
            cornerGroup.setLayoutY(y - 3); // 整体下移
        } else { // 右下角
            hLine.setLayoutX(-20 + 3); // 水平线左移
            vLine.setLayoutY(-20 + 3); // 垂直线上移
            cornerGroup.getChildren().addAll(hLine, vLine);
            cornerGroup.setLayoutX(x - 3); // 整体右移
            cornerGroup.setLayoutY(y - 3); // 整体下移
        }

        // 添加到根面板
        root.getChildren().add(cornerGroup);

        // 添加闪烁效果
        javafx.animation.FadeTransition pulse = new javafx.animation.FadeTransition(
                javafx.util.Duration.seconds(1), cornerGroup);
        pulse.setFromValue(0.3);
        pulse.setToValue(1.0);
        pulse.setCycleCount(10); // 调整为与屏幕边框相同的闪烁次数
        pulse.setAutoReverse(true);
        pulse.play();

        debugLog("添加了屏幕角标记");
    }

    /**
     * 创建自定义十字光标
     */
    private void createCustomCrosshairCursor(Scene scene) {
        try {
            // 创建一个完整的十字光标
            StackPane crosshair = new StackPane();
            crosshair.setMouseTransparent(true); // 确保不干扰鼠标事件

            // 水平线 - 更长更明显
            Rectangle hLine = new Rectangle(30, 2);
            hLine.setFill(Color.WHITE);
            hLine.setStroke(Color.BLACK);
            hLine.setStrokeWidth(0.5);

            // 垂直线 - 更长更明显
            Rectangle vLine = new Rectangle(2, 30);
            vLine.setFill(Color.WHITE);
            vLine.setStroke(Color.BLACK);
            vLine.setStrokeWidth(0.5);

            // 中心点 - 添加一个小点使十字中心更明显
            Circle centerPoint = new Circle(3);
            centerPoint.setFill(Color.RED); // 红色中心点
            centerPoint.setStroke(Color.WHITE);
            centerPoint.setStrokeWidth(0.5);

            // 组合所有元素
            crosshair.getChildren().addAll(hLine, vLine, centerPoint);
            root.getChildren().add(crosshair);

            // 确保十字光标在其他元素之上
            crosshair.setViewOrder(-100);

            // 初始隐藏，直到鼠标移动
            crosshair.setVisible(true);

            // 让十字线跟随鼠标移动 - 使用单独的处理器，确保高优先级
            scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
                crosshair.setLayoutX(e.getX() - 15); // 居中定位
                crosshair.setLayoutY(e.getY() - 15); // 居中定位
                crosshair.setVisible(true);
            });

            // 拖动时仍然显示十字线，方便定位
            scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
                crosshair.setLayoutX(e.getX() - 15);
                crosshair.setLayoutY(e.getY() - 15);
                crosshair.setVisible(true);
            });

            // 设置系统光标为NONE，完全使用我们的自定义光标
            scene.setCursor(javafx.scene.Cursor.NONE);

            debugLog("创建了自定义十字光标，已添加到场景");
        } catch (Exception e) {
            debugLog("创建自定义光标时出错: " + e.getMessage());
            e.printStackTrace();
            scene.setCursor(javafx.scene.Cursor.CROSSHAIR); // 回退到系统十字光标
        }
    }

    /**
     * 设置鼠标相关的事件处理
     */
    private void setupMouseEvents(Scene scene) {
        scene.setOnMousePressed(event -> {
            // 保存起始位置
            startX = event.getScreenX();
            startY = event.getScreenY();

            // 添加ESC键处理 - 确保在鼠标操作过程中也能响应ESC
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("鼠标操作过程中ESC键被按下 - 完全取消截图");
                    // 直接调用完全取消截图的方法，而不是只隐藏选择框
                    cancelCapture();
                    e.consume(); // 消费事件，防止传播
                }
            });

            // 检查是否已经有选择框，且用户点击了选择框内部或边缘
            if (selectionRect != null && isInsideSelectionArea(startX, startY)) {
                // 检查是否点击了边缘调整点
                String resizePoint = getResizeDirection(startX, startY);
                if (!resizePoint.equals("none")) {
                    // 开始调整大小
                    isResizing = true;
                    resizeDirection = resizePoint;
                    resizeStartX = startX;
                    resizeStartY = startY;
                    debugLog("开始调整选择框大小: " + resizeDirection);
                } else if (isNearSelectionCenter(startX, startY)) {
                    // 开始移动整个选择框
                    isMovingSelection = true;
                    moveStartX = startX;
                    moveStartY = startY;
                    debugLog("开始移动整个选择框");
                } else {
                    // 创建新的选择框
                    root.getChildren().remove(selectionRect);
                    createSelectionRect();
                    debugLog("开始创建新的选择区域: X=" + startX + ", Y=" + startY);
                }
            } else {
                // 没有选择框或点击了区域外，创建新的选择框
                if (selectionRect != null) {
                    root.getChildren().remove(selectionRect);
                }
                createSelectionRect();
                debugLog("开始选择区域: X=" + startX + ", Y=" + startY);
            }
        });

        scene.setOnMouseDragged(event -> {
            // 当前鼠标位置
            double currentX = event.getScreenX();
            double currentY = event.getScreenY();

            // 确保在拖动过程中也能响应ESC键
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("拖动过程中ESC键被按下 - 完全取消截图");
                    // 完全取消截图
                    cancelCapture();
                    e.consume();
                }
            });

            if (isResizing) {
                // 调整选择框大小
                resizeSelectionRect(currentX, currentY);
            } else if (isMovingSelection) {
                // 移动整个选择框
                moveSelectionRect(currentX, currentY);
            } else {
                // 正常拖动创建/调整选择框
                endX = currentX;
                endY = currentY;
                updateSelectionOverlay();
            }
        });

        scene.setOnMouseReleased(event -> {
            // 当前鼠标位置
            double currentX = event.getScreenX();
            double currentY = event.getScreenY();

            // 确保松开鼠标后也能响应ESC键
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("松开鼠标后ESC键被按下 - 完全取消截图");
                    // 完全取消截图
                    cancelCapture();
                    e.consume();
                }
            });

            if (isResizing) {
                // 完成调整大小
                resizeSelectionRect(currentX, currentY);
                isResizing = false;
                debugLog("完成调整选择框大小");
            } else if (isMovingSelection) {
                // 完成移动
                moveSelectionRect(currentX, currentY);
                isMovingSelection = false;
                debugLog("完成移动选择框");
            } else {
                // 完成普通选择
                endX = currentX;
                endY = currentY;
                updateSelectionOverlay();

                debugLog("完成选择区域: X=" + endX + ", Y=" + endY +
                        ", 宽度=" + Math.abs(endX - startX) +
                        ", 高度=" + Math.abs(endY - startY));

                // 验证选择区域大小
                if (Math.abs(endX - startX) < 5 || Math.abs(endY - startY) < 5) {
                    debugLog("选择区域太小，重置选择");
                    // 不取消截图，只重置选择
                    if (selectionRect != null) {
                        root.getChildren().remove(selectionRect);
                        selectionRect = null;
                    }
                    return;
                }
            }

            // 保存最终的选择区域坐标
            updateFinalCoordinates();

            // 通知GlobalKeyListener区域选择已完成，等待确认
            notifyScreenshotCompleted();
        });

        // 鼠标移动时改变光标，提示用户可以调整大小或移动
        scene.setOnMouseMoved(event -> {
            if (selectionRect != null) {
                double mouseX = event.getScreenX();
                double mouseY = event.getScreenY();

                // 确保在移动过程中也能响应ESC键
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        debugLog("移动过程中ESC键被按下 - 完全取消截图");
                        // 完全取消截图而不只是隐藏选择框
                        cancelCapture();
                        e.consume();
                    }
                });

                String direction = getResizeDirection(mouseX, mouseY);
                if (!direction.equals("none")) {
                    // 根据方向设置不同的光标
                    switch (direction) {
                        case "n":
                        case "s":
                            scene.setCursor(javafx.scene.Cursor.V_RESIZE);
                            break;
                        case "e":
                        case "w":
                            scene.setCursor(javafx.scene.Cursor.H_RESIZE);
                            break;
                        default:
                            scene.setCursor(javafx.scene.Cursor.DEFAULT);
                    }
                } else if (isNearSelectionCenter(mouseX, mouseY)) {
                    // 在中心区域显示移动光标
                    scene.setCursor(javafx.scene.Cursor.MOVE);
                } else {
                    // 恢复默认光标
                    scene.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            }
        });

        // 全局ESC键处理 - 添加事件过滤器，确保最高优先级处理ESC键
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                debugLog("全局ESC事件过滤器 - 完全取消截图");
                // 直接完全取消截图操作
                cancelCapture();
                e.consume();
            }
        });
    }

    /**
     * 更新最终的选择区域坐标
     */
    private void updateFinalCoordinates() {
        if (selectionRect != null) {
            finalX1 = selectionRect.getX();
            finalY1 = selectionRect.getY();
            finalX2 = selectionRect.getX() + selectionRect.getWidth();
            finalY2 = selectionRect.getY() + selectionRect.getHeight();

            debugLog("更新最终坐标: X1=" + finalX1 + ", Y1=" + finalY1 +
                    ", X2=" + finalX2 + ", Y2=" + finalY2);
        }
    }

    /**
     * 检查某个点是否在选择区域内部或边缘
     */
    private boolean isInsideSelectionArea(double x, double y) {
        if (selectionRect == null)
            return false;

        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 扩大选择区域的边缘检测范围
        double margin = 10;
        return x >= rectX - margin && x <= rectX + rectWidth + margin &&
                y >= rectY - margin && y <= rectY + rectHeight + margin;
    }

    /**
     * 获取调整大小的方向
     * 返回: "n", "s", "e", "w" 或 "none"
     */
    private String getResizeDirection(double x, double y) {
        if (selectionRect == null)
            return "none";

        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();
        double margin = 10; // 检测边缘的边距

        // 检查是否在北边(上边)
        if (Math.abs(y - rectY) <= margin && x >= rectX && x <= rectX + rectWidth) {
            return "n";
        }

        // 检查是否在南边(下边)
        if (Math.abs(y - (rectY + rectHeight)) <= margin && x >= rectX && x <= rectX + rectWidth) {
            return "s";
        }

        // 检查是否在东边(右边)
        if (Math.abs(x - (rectX + rectWidth)) <= margin && y >= rectY && y <= rectY + rectHeight) {
            return "e";
        }

        // 检查是否在西边(左边)
        if (Math.abs(x - rectX) <= margin && y >= rectY && y <= rectY + rectHeight) {
            return "w";
        }

        return "none";
    }

    /**
     * 检查是否在选择区域中心附近
     */
    private boolean isNearSelectionCenter(double x, double y) {
        if (selectionRect == null)
            return false;

        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 检查是否在边缘内部但不在边缘
        return x > rectX + 10 && x < rectX + rectWidth - 10 &&
                y > rectY + 10 && y < rectY + rectHeight - 10;
    }

    /**
     * 调整选择框大小
     */
    private void resizeSelectionRect(double currentX, double currentY) {
        if (selectionRect == null)
            return;

        // 确保在调整大小过程中ESC键生效
        if (captureStage != null && captureStage.getScene() != null) {
            Scene scene = captureStage.getScene();
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("调整大小过程中ESC键被按下 - 立即取消截图");
                    cancelCapture();
                    e.consume();
                }
            });
        }

        // 获取矩形当前位置和大小
        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 获取场景/屏幕边界
        double sceneWidth = 0;
        double sceneHeight = 0;

        if (captureStage != null && captureStage.getScene() != null) {
            Scene scene = captureStage.getScene();
            sceneWidth = scene.getWidth();
            sceneHeight = scene.getHeight();
        } else {
            // 如果场景不可用，尝试获取屏幕尺寸
            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            sceneWidth = screenBounds.getWidth();
            sceneHeight = screenBounds.getHeight();
        }

        // 临时变量储存新的位置和大小
        double newX = rectX;
        double newY = rectY;
        double newWidth = rectWidth;
        double newHeight = rectHeight;

        // 根据不同方向调整大小
        switch (resizeDirection) {
            case "n": // 北/上
                double deltaY = currentY - rectY;
                newHeight = rectHeight - deltaY;

                // 设置最小高度
                if (newHeight < 10) {
                    newHeight = 10;
                    newY = rectY + rectHeight - 10;
                } else {
                    newY = currentY;

                    // 确保不超出上边界
                    if (newY < 0) {
                        double heightIncrease = -newY;
                        newY = 0;
                        newHeight = rectHeight + heightIncrease;
                    }
                }
                break;

            case "s": // 南/下
                double yDelta = currentY - (rectY + rectHeight);
                newHeight = rectHeight + yDelta;

                // 设置最小高度
                if (newHeight < 10) {
                    newHeight = 10;
                } else {
                    // 确保不超出下边界
                    if (rectY + newHeight > sceneHeight) {
                        newHeight = sceneHeight - rectY;
                    }
                }
                break;

            case "e": // 东/右
                double xDelta = currentX - (rectX + rectWidth);
                newWidth = rectWidth + xDelta;

                // 设置最小宽度
                if (newWidth < 10) {
                    newWidth = 10;
                } else {
                    // 确保不超出右边界
                    if (rectX + newWidth > sceneWidth) {
                        newWidth = sceneWidth - rectX;
                    }
                }
                break;

            case "w": // 西/左
                double deltaX = currentX - rectX;
                newWidth = rectWidth - deltaX;

                // 设置最小宽度
                if (newWidth < 10) {
                    newWidth = 10;
                    newX = rectX + rectWidth - 10;
                } else {
                    newX = currentX;

                    // 确保不超出左边界
                    if (newX < 0) {
                        double widthIncrease = -newX;
                        newX = 0;
                        newWidth = rectWidth + widthIncrease;
                    }
                }
                break;
        }

        // 应用新的位置和大小
        selectionRect.setX(newX);
        selectionRect.setY(newY);
        selectionRect.setWidth(newWidth);
        selectionRect.setHeight(newHeight);

        // 更新遮罩和控制点
        updateSelectionMask();

        // 关键修复：调整大小后更新最终坐标值
        updateFinalCoordinates();
    }

    /**
     * 移动整个选择框
     */
    private void moveSelectionRect(double currentX, double currentY) {
        if (selectionRect == null)
            return;

        // 确保在移动选择框过程中ESC键生效
        if (captureStage != null && captureStage.getScene() != null) {
            Scene scene = captureStage.getScene();
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("移动选择框过程中ESC键被按下 - 立即取消截图");
                    cancelCapture();
                    e.consume();
                }
            });
        }

        double deltaX = currentX - moveStartX;
        double deltaY = currentY - moveStartY;

        // 更新起点，用于下一次移动计算
        moveStartX = currentX;
        moveStartY = currentY;

        // 获取当前矩形位置和大小
        double newX = selectionRect.getX() + deltaX;
        double newY = selectionRect.getY() + deltaY;
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 获取当前场景的边界
        double sceneWidth = 0;
        double sceneHeight = 0;

        if (captureStage != null && captureStage.getScene() != null) {
            Scene scene = captureStage.getScene();
            sceneWidth = scene.getWidth();
            sceneHeight = scene.getHeight();
        } else {
            // 如果场景不可用，尝试获取屏幕尺寸
            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            sceneWidth = screenBounds.getWidth();
            sceneHeight = screenBounds.getHeight();
        }

        // 边界检查：确保矩形不会移出屏幕
        // 左边界
        if (newX < 0) {
            newX = 0;
        }
        // 上边界
        if (newY < 0) {
            newY = 0;
        }
        // 右边界 - 至少保留5像素在屏幕内
        if (newX + rectWidth > sceneWidth) {
            newX = sceneWidth - rectWidth;
            // 如果矩形比屏幕宽，至少保持左边在屏幕内
            if (newX < 0) {
                newX = 0;
            }
        }
        // 下边界 - 至少保留5像素在屏幕内
        if (newY + rectHeight > sceneHeight) {
            newY = sceneHeight - rectHeight;
            // 如果矩形比屏幕高，至少保持上边在屏幕内
            if (newY < 0) {
                newY = 0;
            }
        }

        // 移动选择框
        selectionRect.setX(newX);
        selectionRect.setY(newY);

        // 更新遮罩和控制点
        updateSelectionMask();

        // 关键修复：移动后更新最终坐标值
        updateFinalCoordinates();
    }

    /**
     * 仅更新选择区域的遮罩和控制点，不改变选择框本身
     */
    private void updateSelectionMask() {
        if (selectionRect == null || overlayRect == null)
            return;

        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 创建选中区域的遮罩
        Rectangle clipRect = new Rectangle(rectX, rectY, rectWidth, rectHeight);

        // 从现有遮罩中"挖出"选中区域
        Shape mask = Shape.subtract(overlayRect, clipRect);
        mask.setFill(Color.color(0, 0, 0, OVERLAY_OPACITY)); // 使用更深的遮罩颜色

        // 更新遮罩 - 先检查子节点列表索引是否合法
        if (root.getChildren().isEmpty()) {
            root.getChildren().add(mask);
        } else {
            // 更安全的方式替换第一个元素
            Node firstChild = root.getChildren().get(0);
            int index = root.getChildren().indexOf(firstChild);
            if (index >= 0) {
                root.getChildren().remove(index);
                root.getChildren().add(index, mask);
            } else {
                root.getChildren().add(0, mask);
            }
        }

        // 更新边框控制点
        updateBorderHandles();
    }

    /**
     * 通知GlobalKeyListener截图已完成
     */
    private void notifyScreenshotCompleted() {
        try {
            // 静态访问全局监听器
            GlobalKeyListener.getInstance().notifyScreenshotCompleted();
            debugLog("已通知GlobalKeyListener截图区域选择完成");
        } catch (Exception e) {
            debugLog("通知GlobalKeyListener时出错: " + e.getMessage());
        }

        // 作为备份策略，也在这里做一次日志记录
        debugLog("截图区域选择已完成，等待确认");
    }

    /**
     * 获取鼠标当前所在的屏幕
     */
    private Screen getCurrentMouseScreen() {
        try {
            // 获取鼠标当前位置
            Point mousePosition = MouseInfo.getPointerInfo().getLocation();
            double mouseX = mousePosition.getX();
            double mouseY = mousePosition.getY();

            debugLog("当前鼠标位置: X=" + mouseX + ", Y=" + mouseY);

            // 在所有屏幕中查找鼠标所在的屏幕
            for (Screen screen : Screen.getScreens()) {
                javafx.geometry.Rectangle2D bounds = screen.getBounds();
                if (bounds.contains(mouseX, mouseY)) {
                    debugLog("鼠标在屏幕: " + bounds.getMinX() + "," + bounds.getMinY() +
                            " 尺寸: " + bounds.getWidth() + "x" + bounds.getHeight());
                    return screen;
                }
            }

            // 如果找不到，返回主屏幕
            debugLog("无法确定鼠标所在屏幕，使用主屏幕");
            return Screen.getPrimary();
        } catch (Exception e) {
            debugLog("获取鼠标屏幕时出错: " + e.getMessage());
            e.printStackTrace();
            // 出错时返回主屏幕
            return Screen.getPrimary();
        }
    }

    /**
     * 创建初始的选择框
     */
    private void createSelectionRect() {
        selectionRect = new Rectangle();
        selectionRect.setX(startX);
        selectionRect.setY(startY);
        selectionRect.setWidth(0);
        selectionRect.setHeight(0);
        selectionRect.setFill(Color.TRANSPARENT); // 设置选择区域为透明
        selectionRect.setStroke(Color.DODGERBLUE); // 改为与边框相同的蓝色
        selectionRect.setStrokeWidth(2); // 设置边框的宽度
        selectionRect.getStrokeDashArray().addAll(10.0, 5.0); // 添加虚线效果，与屏幕边框保持一致

        root.getChildren().add(selectionRect); // 添加选择框到根面板

        // 创建边框控制点的容器
        borderHandles = new StackPane();
        borderHandles.setMouseTransparent(true); // 确保它不会干扰鼠标事件
        root.getChildren().add(borderHandles);

        // 添加选择框的闪烁动画效果
        applyPulseAnimationToSelection(selectionRect);

        debugLog("创建了选择框和边框控制点容器");
    }

    /**
     * 应用闪烁动画到选择框
     */
    private void applyPulseAnimationToSelection(Rectangle rect) {
        javafx.animation.FadeTransition pulse = new javafx.animation.FadeTransition(
                javafx.util.Duration.seconds(0.8), rect);
        pulse.setFromValue(0.4);
        pulse.setToValue(1.0);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE); // 无限循环
        pulse.setAutoReverse(true);
        pulse.play();

        debugLog("应用了选择框闪烁效果");
    }

    /**
     * 更新选择框的大小和位置，并添加边框控制点
     */
    private void updateSelectionOverlay() {
        // 获取场景/屏幕边界
        double sceneWidth = 0;
        double sceneHeight = 0;

        if (captureStage != null && captureStage.getScene() != null) {
            Scene scene = captureStage.getScene();
            sceneWidth = scene.getWidth();
            sceneHeight = scene.getHeight();
        } else {
            // 如果场景不可用，尝试获取屏幕尺寸
            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            sceneWidth = screenBounds.getWidth();
            sceneHeight = screenBounds.getHeight();
        }

        // 边界限制：确保终点坐标不超出屏幕
        double limitedEndX = Math.min(Math.max(endX, 0), sceneWidth);
        double limitedEndY = Math.min(Math.max(endY, 0), sceneHeight);

        // 计算宽度和高度
        double width = Math.abs(limitedEndX - startX);
        double height = Math.abs(limitedEndY - startY);

        // 计算左上角坐标
        double x = Math.min(startX, limitedEndX);
        double y = Math.min(startY, limitedEndY);

        // 边界限制：确保起始点不超出屏幕
        x = Math.max(0, Math.min(x, sceneWidth));
        y = Math.max(0, Math.min(y, sceneHeight));

        // 如果矩形会超出右边界，调整宽度
        if (x + width > sceneWidth) {
            width = sceneWidth - x;
        }

        // 如果矩形会超出下边界，调整高度
        if (y + height > sceneHeight) {
            height = sceneHeight - y;
        }

        // 更新选择框
        selectionRect.setX(x);
        selectionRect.setY(y);
        selectionRect.setWidth(width);
        selectionRect.setHeight(height);

        // 创建选中区域的遮罩
        Rectangle clipRect = new Rectangle(x, y, width, height);
        clipRect.setFill(Color.TRANSPARENT);

        // 从现有遮罩中"挖出"选中区域
        Shape mask = Shape.subtract(overlayRect, clipRect);
        mask.setFill(Color.color(0, 0, 0, OVERLAY_OPACITY));

        // 清除所有旧的遮罩
        root.getChildren().removeIf(node -> node instanceof Shape && node != selectionRect);

        // 添加新的遮罩
        root.getChildren().add(0, mask);

        // 确保选择框在正确的位置
        if (!root.getChildren().contains(selectionRect)) {
            root.getChildren().add(selectionRect);
            applyPulseAnimationToSelection(selectionRect);
        }

        // 更新或创建边框控制点
        updateBorderHandles();

        // 更新最终坐标
        updateFinalCoordinates();
    }

    /**
     * 更新边框控制点 - 在选择框的四边中心添加控制点
     */
    private void updateBorderHandles() {
        if (borderHandles == null || !root.getChildren().contains(borderHandles)) {
            // 如果borderHandles丢失，重新创建
            borderHandles = new StackPane();
            borderHandles.setMouseTransparent(true);
            root.getChildren().add(borderHandles);
        }

        // 清除旧的控制点
        borderHandles.getChildren().clear();

        // 获取当前选择框的位置和大小
        double rectX = selectionRect.getX();
        double rectY = selectionRect.getY();
        double rectWidth = selectionRect.getWidth();
        double rectHeight = selectionRect.getHeight();

        // 只有在选择框大小足够大时才显示控制点
        if (rectWidth < 20 || rectHeight < 20)
            return;

        // 创建四个边中心的控制点
        createHandle(rectX + rectWidth / 2, rectY); // 上边中心
        createHandle(rectX + rectWidth, rectY + rectHeight / 2); // 右边中心
        createHandle(rectX + rectWidth / 2, rectY + rectHeight); // 下边中心
        createHandle(rectX, rectY + rectHeight / 2); // 左边中心

        // 将控制点容器定位到0,0位置，因为内部的控制点已经定位
        borderHandles.setLayoutX(0);
        borderHandles.setLayoutY(0);

        debugLog("更新了选择框边框控制点");
    }

    /**
     * 创建单个边框控制点
     */
    private void createHandle(double x, double y) {
        // 创建外圈 - 白色背景
        Circle outerCircle = new Circle(6);
        outerCircle.setFill(Color.WHITE);
        outerCircle.setStroke(Color.BLACK);
        outerCircle.setStrokeWidth(0.5);

        // 创建内圈 - 蓝色填充
        Circle innerCircle = new Circle(4);
        innerCircle.setFill(Color.DODGERBLUE);

        // 将两个圆组合
        StackPane handle = new StackPane();
        handle.getChildren().addAll(outerCircle, innerCircle);
        handle.setLayoutX(x - 6); // 根据圆心定位左上角
        handle.setLayoutY(y - 6);

        // 添加到容器
        borderHandles.getChildren().add(handle);
    }

    /**
     * 截取指定屏幕区域
     */
    private WritableImage captureScreenArea(double x1, double y1, double x2, double y2) {
        try {
            // 计算矩形区域
            int captureX = (int) Math.min(x1, x2);
            int captureY = (int) Math.min(y1, y2);
            int captureWidth = (int) Math.abs(x2 - x1);
            int captureHeight = (int) Math.abs(y2 - y1);

            // 确保宽高至少为1
            captureWidth = Math.max(1, captureWidth);
            captureHeight = Math.max(1, captureHeight);

            debugLog("截取区域: X=" + captureX + ", Y=" + captureY +
                    ", 宽度=" + captureWidth + ", 高度=" + captureHeight);

            // 使用 java.awt.Rectangle 转换
            java.awt.Rectangle rect = new java.awt.Rectangle(
                    captureX, captureY, captureWidth, captureHeight);

            // 创建一个Robot实例来获取屏幕截图
            Robot robot = new Robot();

            // 快速但关键的性能措施：
            // 在截取前，临时隐藏窗口，避免任何UI元素被截取进来
            if (captureStage != null && captureStage.isShowing()) {
                // 注意：这是一个同步操作，会立即隐藏窗口，无需等待JavaFX线程
                captureStage.hide();
                debugLog("截图前临时隐藏了窗口");
            }

            // 截图
            BufferedImage bufferedImage = robot.createScreenCapture(rect);

            // 确保图像为标准RGB类型以提高性能
            BufferedImage finalImage;

            // 仅当图像类型不是标准RGB时才进行转换
            if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB) {
                finalImage = new BufferedImage(
                        bufferedImage.getWidth(),
                        bufferedImage.getHeight(),
                        BufferedImage.TYPE_INT_RGB);

                Graphics2D g2d = finalImage.createGraphics();

                // 关闭所有渲染提示以提高速度
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);

                // 直接绘制图像
                g2d.drawImage(bufferedImage, 0, 0, null);
                g2d.dispose();
            } else {
                // 如果已经是RGB类型，直接使用
                finalImage = bufferedImage;
            }

            debugLog("截图成功: " + finalImage.getWidth() + "x" + finalImage.getHeight());

            // 转换为JavaFX图像对象
            WritableImage fxImage = SwingFXUtils.toFXImage(finalImage, null);

            return fxImage;
        } catch (Exception e) {
            debugLog("截图过程中出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 简单检查鼠标是否在上下文菜单附近
     */
    private boolean isMouseNearContextMenu() {
        try {
            // 获取鼠标当前位置
            Point mousePosition = MouseInfo.getPointerInfo().getLocation();

            // 简单地假设菜单显示在鼠标右键点击位置附近
            // 这不是100%准确，但足以处理大多数情况，避免复杂的菜单边界计算
            double mouseX = mousePosition.getX();
            double mouseY = mousePosition.getY();

            // 检查是否在控件内或其周围（添加一个合理的边距）
            // 获取贴图窗口的位置和大小
            if (Platform.isFxApplicationThread() && imageContainer != null) {
                Scene scene = imageContainer.getScene();
                if (scene != null && scene.getWindow() != null) {
                    Window window = scene.getWindow();
                    double winX = window.getX();
                    double winY = window.getY();
                    double winWidth = window.getWidth();
                    double winHeight = window.getHeight();

                    // 添加边距检查鼠标是否在窗口或其周围
                    double margin = 150; // 允许一定边距用于菜单
                    return mouseX >= winX - margin && mouseX <= winX + winWidth + margin &&
                            mouseY >= winY - margin && mouseY <= winY + winHeight + margin;
                }
            }

            return false;
        } catch (Exception e) {
            debugLog("检查鼠标位置时出错: " + e.getMessage());
            return false;
        }
    }

    /**
     * 请求垃圾回收并刷新UI，防止旧界面元素残留
     */
    private void requestGCAndUIRefresh() {
        try {
            // 请求Java垃圾收集，可能帮助释放旧的UI资源
            System.gc();

            // 在JavaFX线程上执行短暂的等待，帮助UI更新
            CountDownLatch refreshLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    // 简单延时，给UI线程一些时间进行清理
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // 忽略中断
                } finally {
                    refreshLatch.countDown();
                }
            });

            // 等待UI刷新完成，但设置很短的超时
            refreshLatch.await(50, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            debugLog("请求UI刷新时出错: " + e.getMessage());
        }
    }

    /**
     * 将图像复制到系统剪贴板
     */
    private void copyImageToClipboard(Image image) {
        try {
            debugLog("复制图像到剪贴板");
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putImage(image);
            clipboard.setContent(content);
            debugLog("图像已复制到剪贴板");
        } catch (Exception e) {
            debugLog("复制到剪贴板失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将图像保存到文件
     */
    private void saveImageAs(Image image, Stage owner) {
        try {
            debugLog("准备保存图像...");

            // 创建文件选择器
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("保存截图");

            // 设置文件类型过滤器
            FileChooser.ExtensionFilter pngFilter = new FileChooser.ExtensionFilter("PNG 图像", "*.png");
            FileChooser.ExtensionFilter jpgFilter = new FileChooser.ExtensionFilter("JPEG 图像", "*.jpg", "*.jpeg");
            fileChooser.getExtensionFilters().addAll(pngFilter, jpgFilter);
            fileChooser.setSelectedExtensionFilter(pngFilter);

            // 设置初始文件名
            String timestamp = String.format("%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS", System.currentTimeMillis());
            fileChooser.setInitialFileName("截图_" + timestamp + ".png");

            // 显示保存对话框
            File file = fileChooser.showSaveDialog(owner);

            if (file != null) {
                // 保存图像
                String fileName = file.getName().toLowerCase();
                String formatName = "png"; // 默认格式

                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    formatName = "jpg";
                }

                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
                ImageIO.write(bufferedImage, formatName, file);

                debugLog("图像已保存到: " + file.getAbsolutePath());
            } else {
                debugLog("用户取消了保存操作");
            }
        } catch (IOException e) {
            debugLog("保存图像失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 清理旧的资源，防止内存泄漏和UI叠加问题
     */
    private void cleanupResources() {
        debugLog("清理旧的截图资源");

        // 如果存在旧的截图窗口，确保它被关闭
        if (captureStage != null && captureStage.isShowing()) {
            try {
                // 先移除所有事件处理器
                if (captureStage.getScene() != null) {
                    Scene scene = captureStage.getScene();
                    scene.setOnKeyPressed(null);
                    scene.setOnKeyReleased(null);
                    scene.setOnMousePressed(null);
                    scene.setOnMouseDragged(null);
                    scene.setOnMouseReleased(null);
                    scene.setOnMouseMoved(null);
                    // 移除事件过滤器
                    scene.removeEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    });
                    scene.removeEventFilter(MouseEvent.ANY, event -> {
                    });
                    debugLog("已移除所有事件处理器");
                }

                // 记录旧窗口的所有者以便也关闭它
                Stage oldOwner = null;
                if (captureStage.getOwner() instanceof Stage) {
                    oldOwner = (Stage) captureStage.getOwner();
                }

                // 关闭主窗口
                captureStage.close();

                // 如果所有者存在，关闭它
                if (oldOwner != null && oldOwner.isShowing()) {
                    oldOwner.close();
                }

                debugLog("已关闭旧的截图窗口");
            } catch (Exception e) {
                debugLog("关闭旧截图窗口时出错: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 重置选择框和遮罩引用 - 只保存引用，不修改UI
        final StackPane handlesToClean = borderHandles;

        // 清空指向这些对象的引用
        selectionRect = null;
        overlayRect = null;
        root = null;
        borderHandles = null;
        captureStage = null;

        // 如果有UI元素需要清理，确保在JavaFX线程上执行
        if (handlesToClean != null) {
            try {
                if (Platform.isFxApplicationThread()) {
                    // 已经在JavaFX线程上，直接清理
                    handlesToClean.getChildren().clear();
                    debugLog("直接清理了边框控制点");
                } else {
                    // 不在JavaFX线程上，使用runLater安排清理
                    Platform.runLater(() -> {
                        try {
                            handlesToClean.getChildren().clear();
                            debugLog("异步清理了边框控制点");
                        } catch (Exception ex) {
                            debugLog("异步清理边框控制点时出错: " + ex.getMessage());
                        }
                    });
                }
            } catch (Exception e) {
                debugLog("清理边框控制点时出错: " + e.getMessage());
            }
        }

        // 建议垃圾回收
        System.gc();
    }

    private static class Delta {
        double x, y;
    }

    public static PaneImplement build() {
        debugLog("创建PaneImplement实例");
        return new PaneImplement();
    }

    /**
     * 调试日志辅助方法
     */
    private static void debugLog(String message) {
        if (DEBUG) {
            System.out.println("[截图] " + message);
        }
    }

    /**
     * 确认截图并捕获选定区域
     */
    public void confirmCapture() {
        try {
            if (captureStage == null || !captureStage.isShowing()) {
                debugLog("无法确认截图：截图窗口已关闭");
                notifyScreenshotFullyCompleted(); // 确保状态被重置
                return;
            }

            debugLog("确认截取选中区域...");

            // 保存当前选择区域的坐标
            final double fx1 = Math.min(finalX1, finalX2);
            final double fy1 = Math.min(finalY1, finalY2);
            final double fx2 = Math.max(finalX1, finalX2);
            final double fy2 = Math.max(finalY1, finalY2);

            // 快速方式：先截图，截图过程会临时隐藏窗口
            final WritableImage capturedImage = captureScreenArea(fx1, fy1, fx2, fy2);

            // 截图后确保窗口重新可见 - captureScreenArea中会隐藏窗口
            if (captureStage != null && !captureStage.isShowing()) {
                captureStage.show();
            }

            // 立即重置状态标志，允许新的截图操作
            CAPTURE_IN_PROGRESS.set(false);

            // 直接关闭窗口 - 无需等待
            if (captureStage != null) {
                try {
                    Stage stageToClose = captureStage;
                    captureStage = null;
                    stageToClose.close();

                    // 关闭所有者窗口
                    if (stageToClose.getOwner() instanceof Stage) {
                        ((Stage) stageToClose.getOwner()).close();
                    }

                    debugLog("截图窗口已关闭");
                } catch (Exception e) {
                    debugLog("关闭窗口时出错: " + e.getMessage());
                }
            }

            // 清理资源
            cleanupResources();

            // 重置状态，允许新的截图操作
            resetCaptureState();

            // 显示贴图 - 直接在当前线程执行，更快速
            if (capturedImage != null) {
                try {
                    showSticker(capturedImage);
                } catch (Exception e) {
                    debugLog("显示截图时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                debugLog("截图失败：截取的图像为null");
            }
        } catch (Exception e) {
            debugLog("确认截图过程中发生异常: " + e.getMessage());
            e.printStackTrace();

            // 确保重置状态和资源
            CAPTURE_IN_PROGRESS.set(false);
            cleanupResources();
            notifyScreenshotFullyCompleted();
        }
    }

    /**
     * 通知GlobalKeyListener截图完全完成
     */
    private void notifyScreenshotFullyCompleted() {
        try {
            // 静态访问全局监听器
            GlobalKeyListener.getInstance().notifyScreenshotFullyCompleted();
            debugLog("已通知GlobalKeyListener截图完全完成");
        } catch (Exception e) {
            debugLog("通知GlobalKeyListener时出错: " + e.getMessage());
        }

        // 作为备份策略，也在这里做一次日志记录
        debugLog("截图已完成，允许下一次截图");
    }

    private void showSticker(WritableImage image) {
        if (image == null) {
            debugLog("无法显示贴图: 图像为null");
            return;
        }

        debugLog("开始创建贴图窗口，图像尺寸: " + image.getWidth() + "x" + image.getHeight());

        try {
            // 确保任何可能存在的截图窗口都被关闭
            if (captureStage != null) {
                try {
                    Stage oldStage = captureStage;
                    captureStage = null;
                    oldStage.close();
                    if (oldStage.getOwner() instanceof Stage) {
                        ((Stage) oldStage.getOwner()).close();
                    }
                    debugLog("关闭了存在的截图窗口");
                } catch (Exception e) {
                    debugLog("关闭旧截图窗口出错: " + e.getMessage());
                }
            }

            // 确保界面实例被释放
            selectionRect = null;
            overlayRect = null;
            root = null;
            if (borderHandles != null) {
                borderHandles.getChildren().clear();
                borderHandles = null;
            }

            // 确保状态标志被重置
            CAPTURE_IN_PROGRESS.set(false);

            ImageView imageView = new ImageView(image);

            // 创建贴图容器
            StackPane imageContainer = new StackPane();
            imageContainer.setBackground(null);

            // 设置容器大小
            double width = image.getWidth();
            double height = image.getHeight();
            imageContainer.setPrefWidth(width); // 增加容器大小以容纳边框
            imageContainer.setPrefHeight(height);

            // 创建一个内部容器来包含图片和边框
            StackPane contentContainer = new StackPane();
            contentContainer.setPrefWidth(width);
            contentContainer.setPrefHeight(height);

            // 创建白色边框 - 使用更大的尺寸来确保边框完全显示
            Rectangle border = new Rectangle(width, height);
            border.setFill(null);
            border.setStroke(Color.POWDERBLUE);
            border.setStrokeWidth(2);
            border.setArcWidth(2);
            border.setArcHeight(2);
            // 将边框居中
            // border.setX(-3.5);
            // border.setY(-3.5);

            // 设置图片视图的大小
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);

            // 先添加图片，再添加边框，确保边框在最上层
            contentContainer.getChildren().addAll(imageView, border);

            // 创建缩放控制
            // final double[] scale = {1.0}; // 初始缩放比例
            // imageContainer.setOnScroll(event -> {
            //     double delta = event.getDeltaY() > 0 ? 0.1 : -0.1; // 每次改变10%
            //     double newScale = Math.min(2.0, Math.max(0.2, scale[0] + delta)); // 限制在20%-200%之间
                
            //     if (newScale != scale[0]) {
            //         scale[0] = newScale;
                    
            //         // 更新容器大小
            //         contentContainer.setPrefWidth(width * newScale);
            //         contentContainer.setPrefHeight(height * newScale);
            //         imageContainer.setPrefWidth(width * newScale + 7);
            //         imageContainer.setPrefHeight(height * newScale + 7);
                    
            //         // 更新边框大小
            //         border.setWidth(width * newScale + 7);
            //         border.setHeight(height * newScale + 7);
            //         border.setX(-3.5);
            //         border.setY(-3.5);
                    
            //         // 更新图片大小
            //         imageView.setFitWidth(width * newScale);
            //         imageView.setFitHeight(height * newScale);
            //     }
            // });

            // 添加所有层到容器
            imageContainer.getChildren().add(contentContainer);

            // 创建透明背景的场景
            Scene scene = new Scene(imageContainer);
            scene.setFill(null); // 确保背景完全透明

            // 创建透明风格的舞台
            Stage stickerStage = new Stage(StageStyle.TRANSPARENT);
            stickerStage.setScene(scene);
            stickerStage.setAlwaysOnTop(true);
            
            // 设置初始窗口大小，考虑边框宽度
            stickerStage.setWidth(width + 7);
            stickerStage.setHeight(height + 7);

            // 为贴图窗口添加ESC键处理
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    debugLog("贴图窗口中ESC键被按下 - 忽略ESC键");
                    e.consume(); // 只消费事件，不执行任何操作
                }
            });

            // 防止在任务栏和菜单栏中显示图标
            stickerStage.setTitle(""); // 设置空标题
            stickerStage.getIcons().clear(); // 清除图标

            // 设置窗口不在任务栏显示
            try {
                // 创建一个不可见的所有者窗口
                Stage ownerStage = new Stage(StageStyle.UTILITY);
                ownerStage.setOpacity(0);
                ownerStage.setWidth(1);
                ownerStage.setHeight(1);
                ownerStage.setX(-32000);
                ownerStage.setY(-32000);
                ownerStage.show();

                // 设置贴图窗口的所有者
                stickerStage.initOwner(ownerStage);
                debugLog("成功设置贴图窗口的所有者");
            } catch (Exception e) {
                debugLog("设置贴图窗口所有者失败: " + e.getMessage());
                e.printStackTrace();
            }

            // 设置贴图窗口在选择区域的原始位置
            double captureX = finalX1;
            double captureY = finalY1;
            stickerStage.setX(captureX);
            stickerStage.setY(captureY);
            debugLog("将贴图窗口放置在选择框最终位置: X=" + captureX + ", Y=" + captureY);

            // 创建右键菜单
            ContextMenu contextMenu = new ContextMenu();

            // 复制到剪贴板选项
            MenuItem copyItem = new MenuItem("复制到剪贴板");
            copyItem.setOnAction(event -> {
                copyImageToClipboard(image);
                contextMenu.hide();
            });

            // 另存为选项
            MenuItem saveAsItem = new MenuItem("另存为...");
            saveAsItem.setOnAction(event -> {
                saveImageAs(image, stickerStage);
                contextMenu.hide();
            });

            // 关闭选项
            MenuItem closeItem = new MenuItem("关闭");
            closeItem.setOnAction(event -> {
                debugLog("关闭贴图窗口");
                if (stickerStage.getOwner() != null) {
                    ((Stage) stickerStage.getOwner()).close();
                }
                stickerStage.close();
            });

            // 添加菜单项到上下文菜单
            contextMenu.getItems().addAll(copyItem, saveAsItem, closeItem);

            // 鼠标事件处理
            // 单击隐藏上下文菜单
            imageContainer.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    // 显示右键菜单
                    contextMenu.show(imageContainer, event.getScreenX(), event.getScreenY());
                } else if (event.getButton() == MouseButton.PRIMARY) {
                    // 左键点击时隐藏菜单
                    contextMenu.hide();
                }
            });

            // 当鼠标离开贴图区域时也隐藏菜单
            imageContainer.setOnMouseExited(event -> {
                // 检查鼠标是否在菜单上
                if (contextMenu.isShowing() && !isMouseNearContextMenu()) {
                    // 给一个较长的延迟，避免意外关闭
                    new Thread(() -> {
                        try {
                            Thread.sleep(500); // 增加到500毫秒延迟
                            Platform.runLater(() -> {
                                // 再次检查是否应该关闭
                                if (contextMenu.isShowing() && !isMouseNearContextMenu()) {
                                    contextMenu.hide();
                                }
                            });
                        } catch (InterruptedException e) {
                            // 忽略中断
                        }
                    }).start();
                }
            });

            // 添加鼠标进入菜单区域的处理
            contextMenu.setOnShowing(event -> {
                // 菜单显示时，暂时禁用鼠标离开事件
                imageContainer.setOnMouseExited(null);
            });

            // 添加菜单隐藏事件处理
            contextMenu.setOnHidden(event -> {
                // 菜单隐藏后，重新启用鼠标离开事件
                imageContainer.setOnMouseExited(e -> {
                    if (contextMenu.isShowing() && !isMouseNearContextMenu()) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);
                                Platform.runLater(() -> {
                                    if (contextMenu.isShowing() && !isMouseNearContextMenu()) {
                                        contextMenu.hide();
                                    }
                                });
                            } catch (InterruptedException ex) {
                                // 忽略中断
                            }
                        }).start();
                    }
                });
            });

            // 显示贴图窗口前确保内容准备就绪
            stickerStage.setOnShown(event -> {
                // 确保所有UI元素尺寸正确
                imageContainer.applyCss();
                imageContainer.layout();
            });

            debugLog("显示贴图窗口");
            stickerStage.show();

            // 拖动功能
            final Delta dragDelta = new Delta();
            imageContainer.setOnMousePressed((MouseEvent mouseEvent) -> {
                // 只有左键才能拖动
                if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                    dragDelta.x = stickerStage.getX() - mouseEvent.getScreenX();
                    dragDelta.y = stickerStage.getY() - mouseEvent.getScreenY();
                }
            });

            imageContainer.setOnMouseDragged((MouseEvent mouseEvent) -> {
                // 只有左键才能拖动
                if (mouseEvent.getButton() == MouseButton.PRIMARY) {
                    stickerStage.setX(mouseEvent.getScreenX() + dragDelta.x);
                    stickerStage.setY(mouseEvent.getScreenY() + dragDelta.y);
                }
            });

            // 重要: 在显示贴图窗口后立即重置截图状态
            // 这样用户可以立即进行下一次截图，不需要等待超时
            resetCaptureState();

            debugLog("贴图窗口创建完成");
        } catch (Exception e) {
            debugLog("显示贴图时出错: " + e.getMessage());
            e.printStackTrace();
            // 确保在出错时也重置状态
            resetCaptureState();
        }
    }
}
