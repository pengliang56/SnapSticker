/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.sticker.service.GlobalKeyListener;
import javafx.application.Application;
import javafx.stage.Stage;

public class ScreenshotStickerApp extends Application {
    public static void main(String[] args) {
        System.out.println("Copyright (c) 2025 Luka. Licensed under MIT.");
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws NativeHookException {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        logger.setLevel(java.util.logging.Level.OFF);
        logger.setUseParentHandlers(false);

        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(new GlobalKeyListener());
    }

    @Override
    public void stop() throws Exception {
        GlobalScreen.unregisterNativeHook();
    }
}
