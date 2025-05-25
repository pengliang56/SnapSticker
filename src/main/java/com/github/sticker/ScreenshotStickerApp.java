/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.sticker.feature.StickerStage;
import com.github.sticker.screenshot.HookKeyListener;
import com.github.sticker.screenshot.ScreenshotSelector;
import com.github.sticker.screenshot.SystemTrayManager;
import com.github.sticker.util.ScreenManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;

/**
 * Main application class for the Screenshot Sticker tool.
 * This application allows users to capture screenshots and create floating stickers.
 */
public class ScreenshotStickerApp extends Application {
    private SystemTrayManager systemTrayManager;
    private ScreenshotSelector screenshotSelector = null;
    private StickerStage stickerStage = null;

    /**
     * Application entry point
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initialize the application
     *
     * @param primaryStage the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        // Initialize system tray
        if (!SystemTray.isSupported()) {
            Platform.exit();
        } else {
            System.setProperty("prism.order", "d3d,sw");
            System.setProperty("prism.vsync", "true");
            System.setProperty("javafx.taskbar.icon", "icon.ico");

            checkAndHandleExistingInstance();
            // Initialize screen manager
            ScreenManager screenManager = new ScreenManager();
            setupPrimaryStage(primaryStage);

            screenshotSelector = new ScreenshotSelector(screenManager);
            Platform.runLater(() -> {
                stickerStage = StickerStage.getInstance();
            });
            systemTrayManager = new SystemTrayManager(screenshotSelector);
            systemTrayManager.initialize();

            HookKeyListener.start(screenshotSelector);
        }
    }

    /**
     * Set up the primary stage with basic configuration
     *
     * @param stage the primary stage to configure
     */
    private void setupPrimaryStage(Stage stage) {
        stage.setTitle("SnapSticker");
        stage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.ico")));

        // Create a minimal utility stage that will only show in taskbar
        Stage utilityStage = new Stage(javafx.stage.StageStyle.UTILITY);
        utilityStage.setTitle("SnapSticker");
        utilityStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icon.ico")));
        utilityStage.setWidth(1);
        utilityStage.setHeight(1);
        utilityStage.setX(-1000);
        utilityStage.setY(-1000);
        utilityStage.show();

        // Hide the main stage
        stage.hide();
    }

    /**
     * Clean up resources when the application is stopped
     */
    @Override
    public void stop() {
        if (systemTrayManager != null) {
            systemTrayManager.cleanup();
        }

        screenshotSelector.dispose();
        stickerStage.dispose();
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException ignored) {

        }

        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            for (TrayIcon icon : tray.getTrayIcons()) {
                tray.remove(icon);
            }
        }

        Platform.exit();
        System.exit(0);
    }

    @SuppressWarnings("all")
    private void checkAndHandleExistingInstance() {
        try {
            new ServerSocket(12345);
        } catch (IOException e) {
            System.exit(0);
        }
    }
}
