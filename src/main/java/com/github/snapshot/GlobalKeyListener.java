package com.github.snapshot;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.UUID;

public class GlobalKeyListener implements NativeKeyListener {
    HashMap<String, PaneImplement> panelContainer = new HashMap<>();

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (e.getKeyCode() == NativeKeyEvent.VC_F1) {
            Platform.runLater(() -> {
                PaneImplement pa = PaneImplement.build();
                String randomNumber = UUID.randomUUID().toString();
                panelContainer.put(randomNumber, pa);
                pa.snapshot(null);
            });
        }
    }
}
