package utils;

import javafx.scene.Scene;

public class ThemeManager {

    private static boolean isDarkMode = false;

    public static void applyTheme(Scene scene) {
        if (isDarkMode) {
            if (!scene.getStylesheets().contains("/views/css/dark.css")) {
                scene.getStylesheets().add("/views/css/dark.css");
            }
            scene.getRoot().getStyleClass().add("root-dark");
        } else {
            scene.getStylesheets().remove("/views/css/dark.css");
            scene.getRoot().getStyleClass().remove("root-dark");
        }
    }

    public static void toggleTheme(Scene scene) {
        isDarkMode = !isDarkMode;
        applyTheme(scene);
    }

    public static boolean isDarkMode() {
        return isDarkMode;
    }
}
