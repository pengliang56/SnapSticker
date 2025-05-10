package com.github.sticker.screenshot;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Platform;

public class HookKeyListener implements NativeKeyListener {
    private final ScreenshotSelector screenshotSelector;

    public static void start(ScreenshotSelector screenshotSelector) {
        HookKeyListener hookKeyListener = new HookKeyListener(screenshotSelector);
        hookKeyListener.startListening();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
            Platform.runLater(this::takeScreenshot);
        } else if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            Platform.runLater(() -> {
                if (screenshotSelector.isSelecting()) {
                    screenshotSelector.cancelSelection();
                }
            });
        }
    }

    public void startListening() {
        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            System.err.println("UnGave Native Hook: " + ex.getMessage());
        }
    }

    private void takeScreenshot() {
        screenshotSelector.startSelection();
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    private HookKeyListener(ScreenshotSelector screenshotSelector) {
        this.screenshotSelector = screenshotSelector;
    }
}