package io.examwave.common;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public final class Ui {
    public static final String INK = "#172033";
    public static final String MUTED = "#667085";
    public static final String ACCENT = "#6C5CE7";
    public static final String SURFACE = "#FFFFFF";
    public static final String CANVAS = "#F5F6FA";

    private Ui() {}

    public static VBox card(Node... nodes) {
        VBox box = new VBox(12, nodes);
        box.setPadding(new Insets(18));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 16;"
                + "-fx-border-color: #E8EAF0; -fx-border-radius: 16;");
        return box;
    }

    public static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 24px; -fx-font-weight: 800; -fx-text-fill: " + INK + ";");
        return label;
    }

    public static Button primary(String text) {
        Button button = new Button(text);
        button.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white;"
                + "-fx-font-weight: 700; -fx-background-radius: 10; -fx-padding: 9 16;");
        return button;
    }

    public static void alert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "" : message);
        alert.showAndWait();
    }
}
