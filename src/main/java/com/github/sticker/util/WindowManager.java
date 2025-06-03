package com.github.sticker.util;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import javafx.geometry.Rectangle2D;

/**
 * Utility class to handle window-related operations
 */
public class WindowManager {
    private static final User32 user32 = User32.INSTANCE;

    /**
     * Get the window bounds
     *
     * @param hwnd Window handle
     * @return Rectangle2D representing the window bounds
     */
    public static Rectangle2D getWindowBounds(HWND hwnd) {
        RECT rect = new RECT();
        user32.GetWindowRect(hwnd, rect);
        return new Rectangle2D(
                rect.left,
                rect.top,
                rect.right - rect.left,
                rect.bottom - rect.top
        );
    }

    /**
     * Check if a window is valid and visible
     *
     * @param hwnd Window handle
     * @return true if the window is valid and visible
     */
    public static boolean isValidWindow(HWND hwnd) {
        if (hwnd == null) return false;

        // Check if window is visible
        boolean isVisible = user32.IsWindowVisible(hwnd);

        // Get window title to check if it's a valid window
        String title = getWindowTitle(hwnd);
        boolean hasTitle = !title.isEmpty();

        return isVisible && hasTitle;
    }

    /**
     * Get the window title
     *
     * @param hwnd Window handle
     * @return Window title
     */
    public static String getWindowTitle(HWND hwnd) {
        char[] buffer = new char[1024];
        user32.GetWindowText(hwnd, buffer, buffer.length);
        return Native.toString(buffer);
    }
} 