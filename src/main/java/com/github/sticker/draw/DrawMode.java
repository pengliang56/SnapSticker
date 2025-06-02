package com.github.sticker.draw;

public enum DrawMode {
    NONE,
    PEN,
    RECTANGLE,
    SWITCH,
    LINE,
    SHOW
    ;

    public static DrawMode switchMode(DrawMode currentMode, DrawMode handler) {
        if (currentMode != handler) {
            return handler;
        } else {
            return NONE;
        }
    }
}