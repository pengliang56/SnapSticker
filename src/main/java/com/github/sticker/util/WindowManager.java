package com.github.sticker.util;

import com.sun.jna.*;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import javafx.geometry.Rectangle2D;

/**
 * Utility class to handle window-related operations
 */
public class WindowManager {
    private static final User32 user32 = User32.INSTANCE;
    
    // Define the WindowFromPoint function
    private static final Function windowFromPoint = Function.getFunction("user32", "WindowFromPoint");
    
    // Define the GetParent function
    private static final Function getParent = Function.getFunction("user32", "GetParent");

    /**
     * Get the window handle under the current mouse position
     * @return HWND of the window under mouse cursor
     */
    public static HWND getWindowUnderMouse() {
        POINT point = new POINT();
        user32.GetCursorPos(point);
        
        // Call WindowFromPoint directly
        Pointer result = (Pointer) windowFromPoint.invoke(Pointer.class, new Object[]{point.getPointer()});
        HWND hwnd = new HWND(result);
        
        // Get the top-level window
        while (hwnd != null) {
            Pointer parent = (Pointer) getParent.invoke(Pointer.class, new Object[]{hwnd.getPointer()});
            if (parent == null) break;
            hwnd = new HWND(parent);
        }
        
        return hwnd;
    }

    /**
     * Get the window bounds
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
     * @param hwnd Window handle
     * @return true if the window is valid and visible
     */
    public static boolean isValidWindow(HWND hwnd) {
        if (hwnd == null) return false;
        
        // Check if window is visible
        boolean isVisible = user32.IsWindowVisible(hwnd);
        
        // Get window title to check if it's a valid window
        String title = getWindowTitle(hwnd);
        boolean hasTitle = title != null && !title.isEmpty();
        
        return isVisible && hasTitle;
    }

    /**
     * Get the window title
     * @param hwnd Window handle
     * @return Window title
     */
    public static String getWindowTitle(HWND hwnd) {
        char[] buffer = new char[1024];
        user32.GetWindowText(hwnd, buffer, buffer.length);
        return Native.toString(buffer);
    }
} 