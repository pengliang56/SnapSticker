package com.github.sticker.feature.widget;

import com.github.sticker.draw.DrawMode;
import com.github.sticker.util.ShotScreen;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Context menu for sticker operations.
 * Handles all right-click menu operations for stickers.
 */
public class StickerContextMenu extends ContextMenu {
    private final Stage stage;
    private final StickerPane stickerPane;

    // Menu items that need to be accessed
    private final Menu sizeMenu;
    private final CheckMenuItem shownItem;
    private final CheckMenuItem showToolbarItem;
    private final MenuItem opacityItem;
    private final MenuItem rotationItem;
    private final MenuItem invertedItem;
    private final MenuItem currentZoomItem;

    public StickerContextMenu(Stage stage, StickerPane stickerPane) {
        this.stage = stage;
        this.stickerPane = stickerPane;
        getStyleClass().add("sticker-context-menu");

        // Initialize menu items
        this.sizeMenu = new Menu();
        this.shownItem = new CheckMenuItem("Shadow");
        this.showToolbarItem = new CheckMenuItem("Show Toolbar");
        this.opacityItem = new MenuItem("Opacity: 100%");
        this.rotationItem = new MenuItem("Rotation: 0°");
        this.invertedItem = new MenuItem("Color inverted: No");
        this.currentZoomItem = new MenuItem("100%         Current");

        initializeMenuItems();

        // Add showing property listener to update properties when menu opens
        setOnShowing(event -> {
            if (getOwnerNode() instanceof Rectangle frame) {
                updateImageProperties(frame);
            }
        });
    }

    @Override
    public void show(Node anchor, double screenX, double screenY) {
        if (anchor instanceof Rectangle frame) {
            updateImageProperties(frame);
        }
        super.show(anchor, screenX, screenY);
    }

    private void initializeMenuItems() {
        // Create main menu items
        MenuItem copyItem = new MenuItem("Copy image");
        MenuItem saveItem = new MenuItem("Save image as...");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem replaceItem = new MenuItem("Replace by file...");

        // Create zoom menu
        Menu zoomMenu = createZoomMenu();

        // Create image processing menu
        Menu imageProcessingMenu = createImageProcessingMenu();

        // Create other menu items
        MenuItem viewFolderItem = new MenuItem("View in folder");
        MenuItem closeItem = new MenuItem("Close and save");
        MenuItem destroyItem = new MenuItem("Destroy");

        // Set up event handlers
        setupEventHandlers(copyItem, saveItem, pasteItem, replaceItem,
                viewFolderItem, closeItem, destroyItem, showToolbarItem);

        // Add all items to the menu
        getItems().addAll(
                copyItem, saveItem, new SeparatorMenuItem(),
                zoomMenu, imageProcessingMenu, new SeparatorMenuItem(),
                pasteItem, replaceItem, new SeparatorMenuItem(),
                shownItem, showToolbarItem, new SeparatorMenuItem(),
                viewFolderItem, closeItem, destroyItem, new SeparatorMenuItem(),
                sizeMenu
        );

        // Initialize size menu items
        initializeSizeMenu();
    }

    private void initializeSizeMenu() {
        MenuItem zoomItem = new MenuItem("Zoom: 100%");

        // Set styles
        sizeMenu.setStyle("-fx-text-fill: #666666;");
        zoomItem.setStyle("-fx-text-fill: #666666;");
        rotationItem.setStyle("-fx-text-fill: #666666;");
        opacityItem.setStyle("-fx-text-fill: #666666;");
        invertedItem.setStyle("-fx-text-fill: #666666;");

        // Disable items (they're just for display)
        zoomItem.setDisable(true);
        rotationItem.setDisable(true);
        opacityItem.setDisable(true);
        invertedItem.setDisable(true);

        sizeMenu.getItems().addAll(zoomItem, rotationItem, opacityItem, invertedItem);
    }

    private Menu createZoomMenu() {
        Menu zoomMenu = new Menu("Zoom");

        MenuItem zoom33Item = new MenuItem("33.3%");
        MenuItem zoom50Item = new MenuItem("50%");
        MenuItem zoom100Item = new MenuItem("100%");
        MenuItem zoom200Item = new MenuItem("200%");

        currentZoomItem.setDisable(true);
        CheckMenuItem smoothingItem = new CheckMenuItem("Smoothing");
        smoothingItem.setSelected(true);
        smoothingItem.setDisable(true);

        zoomMenu.getItems().addAll(
                zoom33Item, zoom50Item, zoom100Item, zoom200Item,
                new SeparatorMenuItem(),
                currentZoomItem, smoothingItem
        );

        // Set up zoom event handlers
        zoom33Item.setOnAction(e -> handleZoom(e, 0.333));
        zoom50Item.setOnAction(e -> handleZoom(e, 0.5));
        zoom100Item.setOnAction(e -> handleZoom(e, 1.0));
        zoom200Item.setOnAction(e -> handleZoom(e, 2.0));

        return zoomMenu;
    }

    private Menu createImageProcessingMenu() {
        Menu menu = new Menu("Image processing");

        MenuItem rotateLeftItem = new MenuItem("Rotate left");
        MenuItem rotateRightItem = new MenuItem("Rotate right");
        MenuItem flipHorizontalItem = new MenuItem("Horizontal flip");
        MenuItem flipVerticalItem = new MenuItem("Vertical flip");

        menu.getItems().addAll(
                rotateLeftItem, rotateRightItem,
                new SeparatorMenuItem(),
                flipHorizontalItem, flipVerticalItem
        );

        // Set up event handlers
        rotateLeftItem.setOnAction(e -> handleRotation(e, -90));
        rotateRightItem.setOnAction(e -> handleRotation(e, 90));
        flipHorizontalItem.setOnAction(e -> handleFlip(e, true));
        flipVerticalItem.setOnAction(e -> handleFlip(e, false));

        return menu;
    }

    private void setupEventHandlers(MenuItem copyItem, MenuItem saveItem,
                                    MenuItem pasteItem, MenuItem replaceItem, MenuItem viewFolderItem,
                                    MenuItem closeItem, MenuItem destroyItem, CheckMenuItem showToolbarItem) {

        copyItem.setOnAction(this::handleCopy);
        saveItem.setOnAction(this::handleSave);
        pasteItem.setOnAction(this::handlePaste);
        replaceItem.setOnAction(this::handleReplace);
        viewFolderItem.setOnAction(e -> handleViewFolder());
        closeItem.setOnAction(this::handleClose);
        destroyItem.setOnAction(this::handleDestroy);
        showToolbarItem.setOnAction(this::handleShowToolbar);
        shownItem.setOnAction(this::handleShadowToggle);
    }

    private void handleZoom(javafx.event.ActionEvent e, double scale) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                applyZoom(frame, scale);
                hide();
            }
        }
    }

    private void handleRotation(javafx.event.ActionEvent e, double angle) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                frame.setRotate((frame.getRotate() + angle) % 360);
                updateStickerSize(frame);
                hide();
            }
        }
    }

    private void handleFlip(javafx.event.ActionEvent e, boolean horizontal) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentMenu().getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                if (horizontal) {
                    frame.setScaleX(frame.getScaleX() * -1);
                } else {
                    frame.setScaleY(frame.getScaleY() * -1);
                }
                hide();
            }
        }
    }

    private void handleCopy(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putImage(stickerPane.getImageView().getImage());
            clipboard.setContent(content);
            hide();
        }
    }

    private void handleSave(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            saveImage(stickerPane.getImageView().getImage());
            hide();
        }
    }

    private void handlePaste(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                if (clipboard.hasImage()) {
                    updateStickerImage(frame, clipboard.getImage());
                    hide();
                }
            }
        }
    }

    private void handleReplace(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                FileChooser fileChooser = createImageFileChooser();
                File file = fileChooser.showOpenDialog(stage);
                if (file != null) {
                    try {
                        Image newImage = new Image(file.toURI().toString());
                        updateStickerImage(frame, newImage);
                        hide();
                    } catch (Exception ex) {
                        System.err.println("Error loading image: " + ex.getMessage());
                    }
                }
            }
        }
    }

    private void handleViewFolder() {
        try {
            File historyDir = getHistoryDirectory();
            openInFileExplorer(historyDir);
            hide();
        } catch (IOException ignored) {

        }
    }

    private void handleClose(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentPopup().getOwnerNode() instanceof ImageView sticker) {
                removeSticker();
                hide();
                saveToHistory();
            }
        }
    }

    private void handleDestroy(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof MenuItem menuItem) {
            if (menuItem.getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                removeSticker();
                hide();
            }
        }
    }

    private void handleShowToolbar(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof CheckMenuItem) {
            stickerPane.getFloatingToolbar().drawMode(null, DrawMode.SWITCH);
        }
    }

    private void handleShadowToggle(javafx.event.ActionEvent e) {
        if (e.getTarget() instanceof CheckMenuItem menuItem) {
            if (menuItem.getParentPopup().getOwnerNode() instanceof Rectangle frame) {
                boolean isShown = menuItem.isSelected();
                stickerPane.getFrame().getProperties().put("shadow", isShown);
                updateShadowEffect(frame, isShown);
            }
        }
    }

    // Helper methods
    private void updateShadowEffect(Rectangle frame, boolean isShown) {
        DropShadow effect = (DropShadow) frame.getEffect();
        if (effect != null) {
            if (isShown) {
                effect.setColor(frame.isFocused() ?
                        Color.rgb(102, 178, 255, 0.8) :
                        Color.rgb(255, 255, 255, 0.3));
            } else {
                effect.setColor(Color.TRANSPARENT);
            }
        }
    }

    private void updateStickerImage(Rectangle frame, Image newImage) {
        ImageView sticker = stickerPane.getImageView();
        sticker.setImage(newImage);
        frame.setHeight(newImage.getHeight());
        frame.setWidth(newImage.getWidth());
    }

    private void removeSticker() {
        stickerPane.destroy();

    }

    private FileChooser createImageFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Replace Image");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );

        File picturesDir = new File(System.getProperty("user.home"), "Pictures");
        if (picturesDir.exists()) {
            fileChooser.setInitialDirectory(picturesDir);
        }

        return fileChooser;
    }

    private File getHistoryDirectory() {
        File picturesDir = new File(System.getProperty("user.home"), "Pictures");
        File historyDir = new File(picturesDir, "SnapSticker/history");
        if (!historyDir.exists()) {
            historyDir.mkdirs();
        }
        return historyDir;
    }

    private void openInFileExplorer(File directory) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Runtime.getRuntime().exec("explorer.exe \"" + directory.getAbsolutePath() + "\"");
        } else if (os.contains("mac")) {
            Runtime.getRuntime().exec("open \"" + directory.getAbsolutePath() + "\"");
        } else {
            Runtime.getRuntime().exec("xdg-open \"" + directory.getAbsolutePath() + "\"");
        }
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

        File picturesDir = new File(System.getProperty("user.home"), "Pictures");
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

    private void saveToHistory() {
        CompletableFuture.runAsync(() -> {
            try {
                File historyDir = getHistoryDirectory();
                String timestamp = String.format("%1$tY%1$tm%1$td_%1$tH%1$tM%1$tS",
                        System.currentTimeMillis());
                File outputFile = new File(historyDir, "SnapSticker_" + timestamp + ".png");
                ImageIO.write(SwingFXUtils.fromFXImage(ShotScreen.snapshotScreen(stickerPane.getScene(), stickerPane.getFrame()), null), "png", outputFile);
                System.out.println("Image saved to history: " + outputFile.getAbsolutePath());
            } catch (IOException ignored) {

            }
        });
    }

    // Methods that need to be implemented in StickerStage
    protected void applyZoom(Rectangle frame, double scale) {
        // This will be implemented in StickerStage
    }

    protected void updateStickerSize(Rectangle frame) {
        // This will be implemented in StickerStage
    }

    public void updateImageProperties(Rectangle frame) {
        // Get the actual dimensions of the sticker
        StickerScaleHandler scaleHandler = (StickerScaleHandler) frame.getProperties().get("scaleHandler");
        if (scaleHandler != null) {
            double width = scaleHandler.getOriginalWidth() * scaleHandler.getCurrentScale();
            double height = scaleHandler.getOriginalHeight() * scaleHandler.getCurrentScale();

            // Update size menu text with actual dimensions
            sizeMenu.setText(String.format("%d × %d", Math.round(width), Math.round(height)));

            // Update scale percentage
            double scale = scaleHandler.getCurrentScale();
            currentZoomItem.setText(String.format("%.0f%%         Current", scale * 100));
            sizeMenu.getItems().get(0).setText(String.format("Zoom: %.0f%%", scale * 100));
        }

        // Update other properties
        double opacity = frame.getOpacity();
        opacityItem.setText(String.format("Opacity: %d%%", (int) (opacity * 100)));

        double rotation = frame.getRotate();
        rotationItem.setText(String.format("Rotation: %.1f°", rotation));

        boolean isInverted = frame.getEffect() instanceof ColorAdjust;
        invertedItem.setText("Color inverted: " + (isInverted ? "Yes" : "No"));

        // Update shadow state
        shownItem.setSelected(Boolean.TRUE.equals(frame.getProperties().get("shadow")));

        showToolbarItem.setSelected(Boolean.TRUE.equals(frame.getProperties().get("showToolbar")));
    }

    private double calculateScale(ImageView sticker) {
        StickerScaleHandler scaleHandler = (StickerScaleHandler) sticker.getProperties().get("scaleHandler");
        return scaleHandler != null ? scaleHandler.getCurrentScale() : 1.0;
    }

    // Getters for menu items
    public Menu getSizeMenu() {
        return sizeMenu;
    }

    public CheckMenuItem getShownItem() {
        return shownItem;
    }

    public MenuItem getOpacityItem() {
        return opacityItem;
    }

    public MenuItem getRotationItem() {
        return rotationItem;
    }

    public MenuItem getInvertedItem() {
        return invertedItem;
    }

    public MenuItem getCurrentZoomItem() {
        return currentZoomItem;
    }
} 