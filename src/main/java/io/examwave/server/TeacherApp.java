package io.examwave.server;

import io.examwave.common.Domain.Teacher;
import io.examwave.common.Ui;
import io.examwave.store.ExamStore;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public final class TeacherApp extends Application {
    private ExamStore store;
    private Stage stage;
    private TeacherWorkspace workspace;

    @Override public void start(Stage stage) {
        this.stage = stage;
        this.store = new ExamStore();
        stage.setTitle("ExamWave Teacher");
        showLogin();
        stage.show();
    }

    private void showLogin() {
        Label brand = new Label("EXAMWAVE");
        brand.setStyle("-fx-font-size: 13px; -fx-font-weight: 900; -fx-text-fill: #6C5CE7;");
        Label title = new Label("Không gian giảng viên");
        title.setStyle("-fx-font-size: 30px; -fx-font-weight: 900; -fx-text-fill: #172033;");
        Label subtitle = new Label("Quản lý lớp thi trong mạng LAN — dữ liệu nằm trên máy Mac này.");
        subtitle.setStyle("-fx-text-fill: #667085;");

        TextField username = new TextField();
        username.setPromptText("Tên đăng nhập");
        PasswordField password = new PasswordField();
        password.setPromptText("Mật khẩu");
        Button login = Ui.primary("Đăng nhập");
        Button create = new Button("Tạo tài khoản mới");

        VBox form = Ui.card(new Label("Tài khoản"), username, password, new HBox(10, login, create));
        form.setMaxWidth(430);
        VBox content = new VBox(12, brand, title, subtitle, form);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setMaxWidth(650);
        StackPane root = new StackPane(content);
        root.setPadding(new Insets(48));
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #F3F0FF, #F7FAFC);");

        login.setOnAction(event -> {
            try {
                Teacher teacher = store.authenticateTeacher(username.getText(), password.getText())
                        .orElseThrow(() -> new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng."));
                enter(teacher);
            } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không đăng nhập được", failure.getMessage()); }
        });
        create.setOnAction(event -> createTeacher(username.getText()));
        stage.setScene(new Scene(root, 920, 620));
    }

    private void createTeacher(String suggestedUsername) {
        Dialog<Teacher> dialog = new Dialog<>();
        dialog.setTitle("Tạo tài khoản giảng viên");
        TextField username = new TextField(suggestedUsername);
        TextField name = new TextField();
        PasswordField password = new PasswordField();
        PasswordField confirmation = new PasswordField();
        GridPane form = new GridPane();
        form.setHgap(12); form.setVgap(12); form.setPadding(new Insets(18));
        form.addRow(0, new Label("Tên đăng nhập"), username);
        form.addRow(1, new Label("Họ tên"), name);
        form.addRow(2, new Label("Mật khẩu"), password);
        form.addRow(3, new Label("Nhập lại"), confirmation);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, new ButtonType("Tạo tài khoản", ButtonBar.ButtonData.OK_DONE));
        dialog.setResultConverter(button -> {
            if (button.getButtonData() != ButtonBar.ButtonData.OK_DONE) return null;
            if (!password.getText().equals(confirmation.getText())) throw new IllegalArgumentException("Mật khẩu nhập lại chưa khớp.");
            return store.createTeacher(username.getText(), name.getText(), password.getText());
        });
        try { dialog.showAndWait().ifPresent(this::enter); }
        catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không tạo được tài khoản", failure.getMessage()); }
    }

    private void enter(Teacher teacher) {
        workspace = new TeacherWorkspace(stage, store, teacher, this::showLogin);
        stage.setScene(workspace.scene());
        stage.setTitle("ExamWave Teacher — " + teacher.fullName());
    }

    @Override public void stop() {
        if (workspace != null) workspace.forceClose();
        if (store != null) store.close();
    }

    public static void main(String[] args) { launch(args); }
}
