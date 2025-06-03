package com.github.sticker.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class OCRUtil {
    private final Tesseract _o = new Tesseract();
    private static final OCRUtil _oDir = new OCRUtil();
    public static Tesseract getOCR() {
        return _oDir._o;
    }

    public static String asyncOCR(Image image) {
        AtomicReference<String> result = new AtomicReference<>();
        CompletableFuture.runAsync(() -> {
            result.set(ocr(image));
        });
        return result.get();
    }

    public static String ocr(Image image) {
        try {
            return _oDir._o.doOCR(SwingFXUtils.fromFXImage(image, null));
        } catch (TesseractException e) {
            throw new RuntimeException(e);
        }
    }

    private OCRUtil() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "tessdata_" + System.currentTimeMillis());
            copyResourceToFile(new File(tempDir, "eng.traineddata"));
            _o.setDatapath(tempDir.getAbsolutePath());
            _o.setLanguage("eng");
            _o.setPageSegMode(6);
            _o.setVariable("user_defined_dpi", "300");
        } catch (Exception ignored) {
        }
    }

    private void copyResourceToFile(File destFile) throws IOException {
        try (var inputStream = getClass().getResourceAsStream("/mode/eng.traineddata")) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + "/mode/eng.traineddata");
            }
            java.nio.file.Files.copy(
                    inputStream,
                    destFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}
