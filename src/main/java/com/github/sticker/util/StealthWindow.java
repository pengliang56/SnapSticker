package com.github.sticker.util;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

public class StealthWindow {
    public static void configure(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setOnShown(e -> hideTaskbarIcon(stage));
    }

    private static void hideTaskbarIcon(Stage stage) {
        try {
            long hwnd = waitForHandle(stage);
            if (hwnd == 0) return;

            WinDef.HWND hWnd = new WinDef.HWND(new Pointer(hwnd));
            int exStyle = User32.INSTANCE.GetWindowLong(hWnd, WinUser.GWL_EXSTYLE);
            exStyle |= 0x80;
            exStyle &= ~0x40000;
            User32.INSTANCE.SetWindowLong(hWnd, WinUser.GWL_EXSTYLE, exStyle);

            refreshTaskbar();
        } catch (Exception ignored) {

        }
    }

    private static long waitForHandle(Stage stage) {
        int retry = 0;
        while (retry++ < 50) { // 最多等待500ms
            try {
                return com.sun.glass.ui.Window.getWindows().stream()
                        .filter(Objects::nonNull)
                        .filter(w -> Math.abs(w.getX() - stage.getX()) < 1
                                && Math.abs(w.getY() - stage.getY()) < 1
                                && Math.abs(w.getWidth() - stage.getWidth()) < 1)
                        .findFirst()
                        .map(com.sun.glass.ui.Window::getNativeHandle)
                        .orElse(0L);
            } catch (Exception e) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return 0;
    }

    private static void refreshTaskbar() {
        WinDef.HWND hwnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (hwnd != null) {
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_HIDE);
            User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOW);
        }
    }
}