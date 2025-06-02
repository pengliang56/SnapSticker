package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawMode;
import com.github.sticker.feature.StickerStage;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * 贴图事件处理器
 * 处理贴图的鼠标事件和焦点事件
 */
public class StickerEventHandler {
    private final Rectangle frame;
    private final StickerPane stickerPane;
    private final StickerStage stickerStage;
    private final BorderEffect borderEffect;
    private StickerContextMenu contextMenu;

    private final Stage stage = StickerStage.getInstance().getStage();
    private final Pane root;

    // 用于记录拖拽的偏移量
    private double dragStartX;
    private double dragStartY;

    public StickerEventHandler(Pane root, Rectangle frame, StickerPane stickerPane, BorderEffect borderEffect) {
        this.root = root;
        this.frame = frame;
        this.stickerPane = stickerPane;
        this.stickerStage = StickerStage.getInstance();
        this.borderEffect = borderEffect;
        setupBasicProperties(stickerPane);
        setupEventHandlers();
    }

    private void setupBasicProperties(StickerPane stickerPane) {
        // 设置初始焦点属性
        frame.setFocusTraversable(true);
        
        StickerScaleLabel scaleLabel = new StickerScaleLabel(stickerPane);
        root.getChildren().add(scaleLabel);
        StickerScaleHandler scaleHandler = new StickerScaleHandler(stickerPane, scaleLabel);
        stickerPane.getFrame().getProperties().put("scaleHandler", scaleHandler);

        contextMenu = new StickerContextMenu(stage, stickerPane) {
            @Override
            protected void applyZoom(Rectangle frame, double scale) {
                StickerEventHandler.this.applyZoom(frame, scale);
            }

            @Override
            protected void updateStickerSize(Rectangle frame) {
                StickerEventHandler.this.updateStickerSize(frame);
            }
        };

        frame.setOnContextMenuRequested(event -> {
            if (contextMenu != null && contextMenu.isShowing()) {
                contextMenu.hide();
            }
            // 显示菜单时禁用缩放
            StickerScaleHandler scale = (StickerScaleHandler) stickerPane.getProperties().get("scaleHandler");
            if (scale != null) {
                scale.setEnabled(false);
            }

            // 更新属性并显示菜单
            contextMenu.show(frame, event.getScreenX(), event.getScreenY());
            stickerPane.toFront();
        });
    }

    private void setupEventHandlers() {
        // 设置鼠标事件处理
        frame.setOnMouseClicked(this::handleClicked);
        frame.setOnMousePressed(this::handleMousePressed);
        frame.setOnMouseDragged(this::handleMouseDragged);
        frame.setOnMouseReleased(this::handleMouseReleased);
        
        // 设置焦点事件处理
        frame.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                handleFocusGained();
            } else {
                handleFocusLost();
            }
        });
    }

    private void handleClicked(MouseEvent event) {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        }
    }

    private void handleMousePressed(MouseEvent event) {
        // 记录拖拽起始位置
        dragStartX = event.getSceneX() - stickerPane.getLayoutX();
        dragStartY = event.getSceneY() - stickerPane.getLayoutY();
        
        // 设置鼠标样式
        frame.setCursor(Cursor.MOVE);
        
        // 取消其他贴图的焦点和效果
        stickerStage.getStickerStageList().forEach(sticker -> {
            if (sticker != stickerPane) {
                BorderEffect effect = (BorderEffect) sticker.getProperties().get("borderEffect");
                if (effect != null) {
                    effect.setActive(false);
                }
                // 显式移除其他贴图的焦点
                Rectangle otherFrame = sticker.getFrame();
                if (otherFrame != null) {
                    otherFrame.setFocusTraversable(false);
                }
            }
        });
        
        // 确保当前贴图可以接收焦点并设置焦点
        frame.setFocusTraversable(true);
        frame.requestFocus();
        
        // 如果是右键点击，不要消费事件，让它继续传播到ContextMenuRequest
        if (!event.isSecondaryButtonDown()) {
            event.consume();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (event.isPrimaryButtonDown()) {
            // 计算新位置
            double newX = event.getSceneX() - dragStartX;
            double newY = event.getSceneY() - dragStartY;
            
            // 更新贴图位置
            stickerPane.setLayoutX(newX);
            stickerPane.setLayoutY(newY);
        }
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        frame.setCursor(Cursor.DEFAULT);
        event.consume();
    }

    private void handleFocusGained() {
        stickerPane.toFront();
        frame.requestFocus();
        borderEffect.setActive(true);
    }

    private void handleFocusLost() {
        borderEffect.setActive(false);
        if (stickerPane.getFloatingToolbar() != null) {
            stickerPane.getFloatingToolbar().drawMode(null, DrawMode.NONE);
        }
    }

    private void applyZoom(Rectangle frame, double scale) {
        StickerScaleHandler scaleHandler = (StickerScaleHandler) frame.getProperties().get("scaleHandler");
        if (scaleHandler != null) {
            scaleHandler.applyScale(scale);
            scaleHandler.setEnabled(true);  // 重新启用缩放功能
            updateStickerSize(frame);
        }
    }

    private void updateStickerSize(Rectangle frame) {
        if (contextMenu != null) {
            contextMenu.updateImageProperties(frame);
        }
    }
} 