/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.service;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.awt.*;
import java.awt.image.BufferedImage;

public class PaneImplement {
    private double startX, startY, endX, endY;
    private Pane root;
    private Stage captureStage;
    private Rectangle selectionRect; // 保存选择框
    private Rectangle overlayRect; // 保存用于遮罩的矩形框
    private static final double OVERLAY_OPACITY = 0.3; // 定义遮罩透明度常量

    public void snapshot(Stage primaryStage) {
        root = new Pane();
        Scene scene = new Scene(root, Screen.getPrimary().getBounds().getWidth(), Screen.getPrimary().getBounds().getHeight());

        // 设置背景为透明，只有未选中区域的灰色遮罩
        scene.setFill(Color.TRANSPARENT);

        captureStage = new Stage(StageStyle.TRANSPARENT);
        captureStage.setScene(scene);
        captureStage.setAlwaysOnTop(true);
        captureStage.show();

        // 创建灰色遮罩
        overlayRect = new Rectangle(0, 0, scene.getWidth(), scene.getHeight());
        overlayRect.setFill(Color.color(0, 0, 0, OVERLAY_OPACITY)); // 使用常量设置透明度
        root.getChildren().add(overlayRect); // 将遮罩添加到根面板

        scene.setOnMousePressed(event -> {
            startX = event.getScreenX();
            startY = event.getScreenY();
            root.getChildren().remove(selectionRect); // 清空之前的选择框
            createSelectionRect(); // 创建初始的选择框
        });

        scene.setOnMouseDragged(event -> {
            endX = event.getScreenX();
            endY = event.getScreenY();
            updateSelectionOverlay(); // 更新选择框
        });

        scene.setOnMouseReleased(event -> {
            endX = event.getScreenX();
            endY = event.getScreenY();
            captureStage.close();
            Platform.runLater(() -> showSticker(captureScreenArea(startX, startY, endX, endY)));
        });
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
        selectionRect.setFill(Color.TRANSPARENT);  // 设置选择区域为透明
        selectionRect.setStroke(Color.rgb(30, 144, 255, 0.5));  // 设置矩形框的边框颜色为蓝色
        selectionRect.setStrokeWidth(2);  // 设置边框的宽度

        root.getChildren().add(selectionRect); // 添加选择框到根面板
    }

    /**
     * 更新选择框的大小和位置
     */
    private void updateSelectionOverlay() {
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);

        selectionRect.setX(Math.min(startX, endX));
        selectionRect.setY(Math.min(startY, endY));
        selectionRect.setWidth(width);
        selectionRect.setHeight(height);

        // 创建选中区域的遮罩
        Rectangle clipRect = new Rectangle(
                Math.min(startX, endX),
                Math.min(startY, endY),
                Math.abs(endX - startX),
                Math.abs(endY - startY)
        );

        // 从现有遮罩中"挖出"选中区域
        Shape mask = Shape.subtract(overlayRect, clipRect);
        mask.setFill(Color.color(0, 0, 0, OVERLAY_OPACITY)); // 保持遮罩的透明度

        // 更新遮罩
        root.getChildren().set(0, mask);
        // 确保选择框在最上层
        if (root.getChildren().size() > 1) {
            root.getChildren().set(1, selectionRect);
        } else {
            root.getChildren().add(selectionRect);
        }
    }

    /**
     * 截取指定屏幕区域
     */
    private WritableImage captureScreenArea(double x1, double y1, double x2, double y2) {
        try {
            // 使用 java.awt.Rectangle 转换
            java.awt.Rectangle rect = new java.awt.Rectangle(
                    (int) Math.min(x1, x2),
                    (int) Math.min(y1, y2),
                    (int) Math.abs(x2 - x1),
                    (int) Math.abs(y2 - y1)
            );
            Robot robot = new Robot();
            BufferedImage bufferedImage = robot.createScreenCapture(rect);
            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 显示贴图窗口
     */
    private void showSticker(WritableImage image) {
        if (image == null) return;

        ImageView imageView = new ImageView(image);

        // 创建一个基础容器
        StackPane container = new StackPane();
        container.setBackground(null);

        // 设置容器大小
        double width = image.getWidth();
        double height = image.getHeight();
        container.setPrefWidth(width);
        container.setPrefHeight(height);

        // 创建发光边框层 - 使用更多层次和更柔和的透明度
        Rectangle glowBorder5 = new Rectangle(width + 12, height + 12);
        glowBorder5.setFill(null);
        glowBorder5.setStroke(Color.valueOf("#c8c8ff"));
        glowBorder5.setStrokeWidth(2);
        glowBorder5.setOpacity(0.05);
        glowBorder5.setArcWidth(4);
        glowBorder5.setArcHeight(4);

        Rectangle glowBorder4 = new Rectangle(width + 9, height + 9);
        glowBorder4.setFill(null);
        glowBorder4.setStroke(Color.valueOf("#c8c8ff"));
        glowBorder4.setStrokeWidth(2);
        glowBorder4.setOpacity(0.08);
        glowBorder4.setArcWidth(4);
        glowBorder4.setArcHeight(4);

        Rectangle glowBorder3 = new Rectangle(width + 7, height + 7);
        glowBorder3.setFill(null);
        glowBorder3.setStroke(Color.valueOf("#bebeff"));
        glowBorder3.setStrokeWidth(2);
        glowBorder3.setOpacity(0.1);
        glowBorder3.setArcWidth(4);
        glowBorder3.setArcHeight(4);

        Rectangle glowBorder2 = new Rectangle(width + 5, height + 5);
        glowBorder2.setFill(null);
        glowBorder2.setStroke(Color.valueOf("#b4b4ff"));
        glowBorder2.setStrokeWidth(2);
        glowBorder2.setOpacity(0.12);
        glowBorder2.setArcWidth(4);
        glowBorder2.setArcHeight(4);

        Rectangle glowBorder1 = new Rectangle(width + 3, height + 3);
        glowBorder1.setFill(null);
        glowBorder1.setStroke(Color.valueOf("#aaaaff"));
        glowBorder1.setStrokeWidth(2);
        glowBorder1.setOpacity(0.15);
        glowBorder1.setArcWidth(4);
        glowBorder1.setArcHeight(4);

        Rectangle border = new Rectangle(width, height);
        border.setFill(null);
        border.setStroke(Color.valueOf("#a5a5de"));
        border.setStrokeWidth(1.5);
        border.setOpacity(0.6);
        border.setArcWidth(4);
        border.setArcHeight(4);

        // 添加所有层到容器
        container.getChildren().addAll(glowBorder5, glowBorder4, glowBorder3, glowBorder2, glowBorder1, border, imageView);

        Scene scene = new Scene(container);
        scene.setFill(Color.TRANSPARENT);

        Stage stickerStage = new Stage(StageStyle.TRANSPARENT);
        stickerStage.setScene(scene);
        stickerStage.setAlwaysOnTop(true);
        stickerStage.show();

        // 拖动功能
        final Delta dragDelta = new Delta();
        container.setOnMousePressed((MouseEvent mouseEvent) -> {
            dragDelta.x = stickerStage.getX() - mouseEvent.getScreenX();
            dragDelta.y = stickerStage.getY() - mouseEvent.getScreenY();
        });

        container.setOnMouseDragged((MouseEvent mouseEvent) -> {
            stickerStage.setX(mouseEvent.getScreenX() + dragDelta.x);
            stickerStage.setY(mouseEvent.getScreenY() + dragDelta.y);
        });

        // 设置透明度
        stickerStage.getScene().getRoot().setOpacity(1);


/*        // 鼠标右键逻辑
        ContextMenu contextMenu = new ContextMenu();

        javafx.scene.control.MenuItem closeItem = new javafx.scene.control.MenuItem("关闭贴图");
        closeItem.setOnAction(e -> stickerStage.close());

        javafx.scene.control.MenuItem saveItem = new javafx.scene.control.MenuItem("保存为图片");
        saveItem.setOnAction(e -> {
            // TODO: 实现保存 image 到文件的逻辑
            System.out.println("你可以在这里实现保存逻辑");
        });

        contextMenu.getItems().addAll(saveItem, closeItem);

        container.setOnMousePressed(event -> {
            if (event.isSecondaryButtonDown()) {
                contextMenu.show(container, event.getScreenX(), event.getScreenY());
            } else {
                contextMenu.hide();
            }
        });*/
    }

    private static class Delta {
        double x, y;
    }

    public static PaneImplement build() {
        return new PaneImplement();
    }
}
