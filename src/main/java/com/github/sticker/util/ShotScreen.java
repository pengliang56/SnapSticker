package com.github.sticker.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;


import java.awt.*;
import java.awt.image.BufferedImage;

public class ShotScreen {
    public static WritableImage snapshotScreen(Scene scene, Rectangle selectionArea) {
        double x = selectionArea.getX();
        double y = selectionArea.getY();
        double width = selectionArea.getWidth();
        double height = selectionArea.getHeight();

        Robot robot;
        try {
            robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to create Robot instance", e);
        }

        Point2D sceneCoords = scene.getRoot().localToScreen(x, y);

        java.awt.Rectangle awtRect = new java.awt.Rectangle(
                (int) sceneCoords.getX(),
                (int) sceneCoords.getY(),
                (int) width,
                (int) height
        );

        BufferedImage screenImage = robot.createScreenCapture(awtRect);
        WritableImage screenContent = new WritableImage((int) width, (int) height);
        SwingFXUtils.toFXImage(screenImage, screenContent);
        return screenContent;
    }
}
