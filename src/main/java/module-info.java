module com.github.sticker {
    requires com.github.kwhat.jnativehook;
    requires com.sun.jna.platform;
    requires com.sun.jna;
    requires java.datatransfer;
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.swing;
    requires tess4j;

    opens com.github.sticker to javafx.graphics, com.sun.jna;
    exports com.github.sticker;
}