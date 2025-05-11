package com.github.sticker.draw;

import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

public class FloatingToolbar {
    private static final String[] BUTTON_ICONS = {
            // 保存图标（SVG路径）
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h2v2h-2zm0-8h2v6h-2z",
            // 画笔图标
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15h2v2h-2zm0-8h2v6h-2z",
            // 矩形图标
            "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z",
            // 贴图图标
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"
    };

    private final HBox toolbar = new HBox(8);
    private final Rectangle selectionArea;
    private final Pane parentContainer;
    private boolean isVisible = true;

    private Button penButton;
    private Button rectButton;
    private final DrawCanvas drawCanvas;
    private Button activeButton;

    public FloatingToolbar(Rectangle selectionArea, Pane parentContainer) {
        this.drawCanvas = new DrawCanvas(parentContainer);
        this.selectionArea = selectionArea;
        this.parentContainer = parentContainer;
        initializeToolbar();
        parentContainer.getChildren().add(toolbar);
    }

    private void initializeToolbar() {
        baseStyle();

        createButtons();

        setupBottomPositionBinding();

        setupKeyboardToggle();
    }

    private void setupBottomPositionBinding() {
        toolbar.layoutXProperty().bind(
                Bindings.createDoubleBinding(() ->
                                selectionArea.getX() + (selectionArea.getWidth() / 2) - (toolbar.getWidth() / 2),
                        selectionArea.xProperty(), selectionArea.widthProperty(), toolbar.widthProperty()
                )
        );

        // Y轴位置：选区底部 + 10px 间距
        toolbar.layoutYProperty().bind(
                selectionArea.yProperty()
                        .add(selectionArea.heightProperty())
                        .add(10)
        );

        // X轴定位（保持居中且防止左右溢出）
        toolbar.layoutXProperty().bind(
                Bindings.createDoubleBinding(() -> {
                            // 计算理想居中位置
                            double idealX = selectionArea.getX() +
                                    (selectionArea.getWidth() - toolbar.getWidth()) / 2;

                            // 边界约束
                            double minX = 5;
                            double maxX = parentContainer.getWidth() - toolbar.getWidth() - 5;

                            return Math.max(minX, Math.min(idealX, maxX));
                        }, selectionArea.xProperty(), selectionArea.widthProperty(),
                        toolbar.widthProperty(), parentContainer.widthProperty())
        );

        // Y轴动态定位（上下自动切换）
        toolbar.layoutYProperty().bind(
                Bindings.createDoubleBinding(() -> {
                            // 基础参数计算
                            double selectionBottom = selectionArea.getY() + selectionArea.getHeight();
                            double toolbarHeight = toolbar.getHeight();
                            double parentHeight = parentContainer.getHeight();

                            // 默认下方位置
                            double bottomPosition = selectionBottom + 10;

                            // 上方位置
                            double topPosition = selectionArea.getY() - toolbarHeight - 10;

                            // 检测下方空间是否足够
                            boolean canShowBelow = (bottomPosition + toolbarHeight) < (parentHeight - 5);

                            // 智能选择显示位置
                            return canShowBelow ? bottomPosition : topPosition;
                        }, selectionArea.yProperty(), selectionArea.heightProperty(),
                        parentContainer.heightProperty(), toolbar.heightProperty())
        );
    }

    private void createButtons() {
        createSaveButton();
        createBrushButton();
        createRectButton();
        createStickerButton();
    }

    private Button createIconButton(String svgPath, String tooltipText) {
        Button btn = new Button();
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.WHITE);
        btn.setGraphic(icon);

        btn.setStyle(
                "-fx-background-radius: 4;" +
                        "-fx-min-width: 32;" +
                        "-fx-min-height: 32;" +
                        "-fx-background-color: transparent;" +
                        "-fx-cursor: hand;"
        );

        // 悬停效果
        DropShadow hover = new DropShadow(5, Color.gray(0.3));
        btn.setOnMouseEntered(e -> {
            btn.setEffect(hover);
            btn.setStyle(btn.getStyle() + "-fx-background-color: rgba(255,255,255,0.15);");
        });
        btn.setOnMouseExited(e -> {
            btn.setEffect(null);
            btn.setStyle(btn.getStyle().replace("-fx-background-color: rgba(255,255,255,0.15);", ""));
        });

        Tooltip.install(btn, new Tooltip(tooltipText));
        return btn;
    }

    private void createSaveButton() {
        Button btn = createIconButton(BUTTON_ICONS[0], "保存截图");
        btn.setOnAction(e -> System.out.println("执行保存操作"));
        toolbar.getChildren().add(btn);
    }

    private void createBrushButton() {
        penButton = createIconButton(BUTTON_ICONS[1], "Pencil(-)");
        penButton.setOnAction(e -> {
            if(drawCanvas.getCurrentDrawMode() == DrawMode.PEN) {
                drawCanvas.deactivateCurrentTool();
            } else {
                drawCanvas.activateTool(DrawMode.PEN);
            }
        });
        toolbar.getChildren().add(penButton);
    }

    private void createRectButton() {
        rectButton = createIconButton(BUTTON_ICONS[2], "绘制矩形");
        rectButton.setOnAction(e -> {
            if(drawCanvas.getCurrentDrawMode() == DrawMode.RECTANGLE) {
                drawCanvas.deactivateCurrentTool();
            } else {
                drawCanvas.activateTool(DrawMode.RECTANGLE);
            }
        });
        toolbar.getChildren().add(rectButton);
    }

    private void createStickerButton() {
        Button btn = createIconButton(BUTTON_ICONS[3], "添加贴图");
        btn.setOnAction(e -> System.out.println("打开贴图库"));
        toolbar.getChildren().add(btn);
    }

    private void setupKeyboardToggle() {
        Scene scene = parentContainer.getScene();
        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.SPACE) {
                toggleVisibility();
                e.consume(); // 阻止事件冒泡
            }
        });
    }

    private void toggleVisibility() {
        FadeTransition fade = new FadeTransition(Duration.millis(150), toolbar);
        fade.setFromValue(isVisible ? 1.0 : 0.0);
        fade.setToValue(isVisible ? 0.0 : 1.0);
        fade.play();

        toolbar.setMouseTransparent(!isVisible);
        isVisible = !isVisible;
    }

    private void baseStyle() {
        toolbar.setStyle(
                "-fx-background-color: rgba(50, 50, 50, 0.95);" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 6;" +
                        "-fx-spacing: 8;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 2);"
        );
    }

    public HBox getToolbar() {
        return toolbar;
    }

    private void handleToolButtonClick(Button clickedBtn, DrawMode targetMode) {
        // 如果点击的是已激活按钮
        if (drawCanvas.getCurrentDrawMode() == targetMode) {
            drawCanvas.deactivateCurrentTool();
            clickedBtn.getStyleClass().remove("active");
            activeButton = null;
        } else {
            // 取消其他按钮状态
            if (activeButton != null) {
                activeButton.getStyleClass().remove("active");
            }

            // 激活新工具
            drawCanvas.activateTool(targetMode);
            clickedBtn.getStyleClass().add("active");
            activeButton = clickedBtn;
        }
    }

/*    public void updateButtonStates() {
        for (Node node : toolbar.getChildren()) {
            if (node instanceof Button btn) {
                DrawMode btnMode = getButtonMode(btn);
                if (btnMode == drawCanvas.getCurrentDrawMode()) {
                    btn.getStyleClass().add("active");
                } else {
                    btn.getStyleClass().remove("active");
                }
            }
        }
    }*/
}