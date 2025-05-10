/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.sticker.screenshot.HookKeyListener;
import com.github.sticker.screenshot.ScreenshotSelector;
import com.github.sticker.util.ScreenManager;
import com.github.sticker.screenshot.SystemTrayManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.awt.*;

/**
 * Main application class for the Screenshot Sticker tool.
 * This application allows users to capture screenshots and create floating stickers.
 */
public class ScreenshotStickerApp extends Application {
    private SystemTrayManager systemTrayManager;

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
            // Initialize screen manager
            ScreenManager screenManager = new ScreenManager();
            setupPrimaryStage(primaryStage);

            ScreenshotSelector screenshotSelector = new ScreenshotSelector(screenManager);

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
        stage.setTitle("Screenshot Sticker");
        // Additional stage setup will be added here
    }

    /**
     * Clean up resources when the application is stopped
     */
    @Override
    public void stop() {
        if (systemTrayManager != null) {
            systemTrayManager.cleanup();
        }

        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
    }
}
