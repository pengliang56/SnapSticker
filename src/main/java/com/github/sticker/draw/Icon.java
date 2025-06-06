package com.github.sticker.draw;

import javafx.scene.Group;
import javafx.scene.ImageCursor;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

public interface Icon {
    String undo = "M9 15 3 9m0 0 6-6M3 9h12a6 6 0 0 1 0 12h-3";
    String redo = "m15 15 6-6m0 0-6-6m6 6H9a6 6 0 0 0 0 12h3";
    String clode = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    String tuding = "M12.776 0.939a.65.65 0 0 1 .460.190l6.435 6.435a.65.65 0 0 1 0 .919c-.624.624-1.394.766-1.954.766-.231 0-.437-.023-.598-.051l-4.074 4.074a7.8 7.8 0 0 1 .208 1.316c.060 0.912-.042 2.039-.936 2.933a.65.65 0 0 1-.919 0l-3.677-3.677-4.137 4.137c-.254.254-1.588 1.098-1.842.844s.666-1.589.919-1.842l4.137-4.137-3.677-3.677a.65.65 0 0 1 0-.919c.895-.895 2.022-.977 2.933-.936a7.8 7.8 0 0 1 1.316.208l4.074-4.074a3.9 3.9 0 0 1-.052-.600c0-.559.140-1.329.767-1.956a.65.65 0 0 1 .459-.190";
    String copy = "M16.5 8.25V6a2.25 2.25 0 0 0-2.25-2.25H6A2.25 2.25 0 0 0 3.75 6v8.25A2.25 2.25 0 0 0 6 16.5h2.25m8.25-8.25H18a2.25 2.25 0 0 1 2.25 2.25V18A2.25 2.25 0 0 1 18 20.25h-7.5A2.25 2.25 0 0 1 8.25 18v-1.5m8.25-8.25h-6a2.25 2.25 0 0 0-2.25 2.25v6";
    String save = "M6.72 13.829c-.24.03-.48.062-.72.096m.72-.096a42.415 42.415 0 0 1 10.56 0m-10.56 0L6.34 18m10.94-4.171c.24.03.48.062.72.096m-.72-.096L17.66 18m0 0 .229 2.523a1.125 1.125 0 0 1-1.12 1.227H7.231c-.662 0-1.18-.568-1.12-1.227L6.34 18m11.318 0h1.091A2.25 2.25 0 0 0 21 15.75V9.456c0-1.081-.768-2.015-1.837-2.175a48.055 48.055 0 0 0-1.913-.247M6.34 18H5.25A2.25 2.25 0 0 1 3 15.75V9.456c0-1.081.768-2.015 1.837-2.175a48.041 48.041 0 0 1 1.913-.247m10.5 0a48.536 48.536 0 0 0-10.5 0m10.5 0V3.375c0-.621-.504-1.125-1.125-1.125h-8.25c-.621 0-1.125.504-1.125 1.125v3.659M18 10.5h.008v.008H18V10.5Zm-3 0h.008v.008H15V10.5Z";
    String rectangle = "M5.25 7.5A2.25 2.25 0 0 1 7.5 5.25h9a2.25 2.25 0 0 1 2.25 2.25v9a2.25 2.25 0 0 1-2.25 2.25h-9a2.25 2.25 0 0 1-2.25-2.25v-9Z";
    String pencil = "m16.862 4.487 1.687-1.688a1.875 1.875 0 1 1 2.652 2.652L6.832 19.82a4.5 4.5 0 0 1-1.897 1.13l-2.685.8.8-2.685a4.5 4.5 0 0 1 1.13-1.897L16.863 4.487Zm0 0L19.5 7.125";
    String line = "M2.25 18 9 11.25l4.306 4.306a11.95 11.95 0 0 1 5.814-5.518l2.74-1.22m0 0-5.94-2.281m5.94 2.28-2.28 5.941";

    String point = "M12 4.5v15m7.5-7.5h-15";
    String arrowDownLeft = "m19.5 4.5-15 15m0 0h11.25m-11.25 0V8.25";
    String arrowDownRight = "m4.5 4.5 15 15m0 0V8.25m0 11.25H8.25";
    String arrowDown = "M19.5 13.5 12 21m0 0-7.5-7.5M12 21V3";

    String arrowUp = "M4.5 10.5 12 3m0 0 7.5 7.5M12 3v18";
    String arrowUpRight = "m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25";
    String arrowUpLeft = "m19.5 19.5-15-15m0 0v11.25m0-11.25h11.25";
    String arrowLeft = "M10.5 19.5 3 12m0 0 7.5-7.5M3 12h18";
    String arrowRight = "M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3";
    String arrowsPointingOut = "M3.75 3.75v4.5m0-4.5h4.5m-4.5 0L9 9M3.75 20.25v-4.5m0 4.5h4.5m-4.5 0L9 15M20.25 3.75h-4.5m4.5 0v4.5m0-4.5L15 9m5.25 11.25h-4.5m4.5 0v-4.5m0 4.5L15 15";

    static ImageCursor createDirectionalCursor(String svgPath) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setStroke(Color.rgb(13, 120, 171));
        icon.setStrokeWidth(2);

        Group group = new Group(icon);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        Image cursorImage = group.snapshot(params, null);
        return new ImageCursor(cursorImage, cursorImage.getWidth() / 2, cursorImage.getHeight() / 2);
    }
}
