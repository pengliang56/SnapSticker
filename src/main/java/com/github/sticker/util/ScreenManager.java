/**
 * Copyright (c) 2025 Luka. All rights reserved.
 * <p>
 * This code is licensed under the MIT License.
 * See LICENSE in the project root for license information.
 */
package com.github.sticker.util;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.awt.*;

/**
 * Manages screen-related functionality for the application.
 * Handles multi-monitor support and screen selection.
 */
public class ScreenManager {
    private Screen currentScreen;
    private static final User32 user32 = User32.INSTANCE;

    /**
     * Get the screen where the mouse cursor is currently located
     *
     * @return Screen object representing the current screen
     */
    public Screen getCurrentScreen() {
        Point mousePosition = MouseInfo.getPointerInfo().getLocation();
        double mouseX = mousePosition.getX();
        double mouseY = mousePosition.getY();

        // Find the screen containing the mouse cursor
        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            if (bounds.contains(mouseX, mouseY)) {
                currentScreen = screen;
                return screen;
            }
        }

        // If no screen is found, return the primary screen
        currentScreen = Screen.getPrimary();
        return currentScreen;
    }

    /**
     * Get the bounds of the current screen
     *
     * @return Rectangle2D representing the bounds of the current screen
     */
    public Rectangle2D getCurrentScreenBounds() {
        if (currentScreen == null) {
            getCurrentScreen();
        }
        return currentScreen.getBounds();
    }

    /**
     * Check if the given coordinates are within the current screen bounds
     *
     * @param x X coordinate
     * @param y Y coordinate
     * @return true if the coordinates are within the current screen bounds
     */
    public boolean isWithinCurrentScreen(double x, double y) {
        if (currentScreen == null) {
            getCurrentScreen();
        }
        return currentScreen.getBounds().contains(x, y);
    }

    /**
     * Get the number of available screens
     *
     * @return number of screens
     */
    public int getScreenCount() {
        return Screen.getScreens().size();
    }

    /**
     * Get the taskbar bounds
     * @return Rectangle2D representing the taskbar bounds, or null if not found
     */
    public Rectangle2D getTaskbarBounds() {
        // Find the taskbar window
        HWND taskbar = user32.FindWindow("Shell_TrayWnd", null);
        if (taskbar == null) {
            return null;
        }

        // Get the taskbar bounds
        RECT rect = new RECT();
        if (!user32.GetWindowRect(taskbar, rect)) {
            return null;
        }

        return new Rectangle2D(
            rect.left,
            rect.top,
            rect.right - rect.left,
            rect.bottom - rect.top
        );
    }

    /**
     * Check if the taskbar is visible
     * @return true if the taskbar is visible
     */
    public boolean isTaskbarVisible() {
        HWND taskbar = user32.FindWindow("Shell_TrayWnd", null);
        if (taskbar == null) {
            return false;
        }
        return user32.IsWindowVisible(taskbar);
    }
} 