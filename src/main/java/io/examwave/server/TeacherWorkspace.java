package io.examwave.server;

import io.examwave.common.Domain.*;
import io.examwave.common.Ui;
import io.examwave.store.ExamStore;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Function;

final class TeacherWorkspace {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final Stage stage;
    private final ExamStore store;
    private final Teacher teacher;
    private final Runnable logout;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private ExamCoordinator coordinator;
    private TableView<OnlineView> onlineTable;
    private Label serverState;
    private TextField ipField;
    private TextField portField;

    TeacherWorkspace(Stage stage, ExamStore store, Teacher teacher, Runnable logout) {
        this.stage = stage; this.store = store; this.teacher = teacher; this.logout = logout;
        root.setStyle("-fx-background-color: #F5F6FA;");
        root.setLeft(sidebar());
        root.setCenter(content);
        showDashboard();
        stage.setOnCloseRequest(event -> {
            try { if (coordinator != null) coordinator.stopIfSafe(); }
            catch (Exception failure) { event.consume(); Ui.alert(Alert.AlertType.WARNING, "Chưa thể đóng", failure.getMessage()); }
        });
    }

    Scene scene() { return new Scene(root, 1180, 760); }

    private VBox sidebar() {
        Label logo = new Label("◈  EXAMWAVE");
        logo.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: 900;");
        Label identity = new Label(teacher.fullName() + "\n@" + teacher.username());
        identity.setStyle("-fx-text-fill: #B8C0D8;");
        Button dashboard = nav("Tổng quan", this::showDashboard);
        Button bank = nav("Kho câu hỏi", this::showBank);
        Button exams = nav("Đề & ca thi", this::showExams);
        Button results = nav("Kết quả", this::showResults);
        Button assignments = nav("Bài tập", this::showAssignments);
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Button password = nav("Đổi mật khẩu", this::changePassword);
        Button leave = nav("Đăng xuất", this::leaveWorkspace);
        VBox menu = new VBox(10, logo, identity, new Separator(), dashboard, bank, exams, results, assignments,
                spacer, password, leave);
        menu.setPadding(new Insets(24));
        menu.setPrefWidth(220);
        menu.setStyle("-fx-background-color: #172033;");
        return menu;
    }

    private Button nav(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: #E9ECF5; -fx-font-size: 14px; -fx-padding: 10 12;");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void show(Node node) {
        content.getChildren().setAll(node);
        StackPane.setMargin(node, new Insets(26));
    }

    private void showDashboard() {
        serverState = new Label(coordinator != null && coordinator.isRunning() ? "Đang chạy" : "Đã dừng");
        serverState.setStyle("-fx-font-weight: 800; -fx-text-fill: #12A594;");
        String serverIp = coordinator != null && coordinator.isRunning() ? coordinator.host() : preferredLanIp();
        ipField = new TextField(serverIp); ipField.setPrefWidth(150);
        portField = new TextField("5050"); portField.setPrefWidth(90);
        Button start = Ui.primary("Khởi động");
        Button stop = new Button("Dừng an toàn");
        HBox controls = new HBox(10, new Label("IP máy chủ"), ipField,
                new Label("Cổng TCP"), portField, start, stop, serverState);
        controls.setAlignment(Pos.CENTER_LEFT);
        onlineTable = new TableView<>();
        onlineTable.getColumns().add(column("Mã sinh viên", 130, OnlineView::studentCode));
        onlineTable.getColumns().add(column("Họ tên", 220, OnlineView::studentName));
        onlineTable.getColumns().add(column("IP", 130, OnlineView::ip));
        onlineTable.getColumns().add(column("Trạng thái", 130, OnlineView::status));
        onlineTable.getColumns().add(column("Hoạt động", 240, OnlineView::activity));
        onlineTable.setPlaceholder(new Label("Chưa có sinh viên online"));
        Button warn = new Button("Gửi cảnh báo");
        Button lock = new Button("Khóa tài khoản");
        Button locked = new Button("Danh sách bị khóa");
        HBox moderation = new HBox(10, warn, lock, locked);
        VBox body = new VBox(18, Ui.heading("Trung tâm điều hành"), Ui.card(controls),
                Ui.card(new Label("Sinh viên đang kết nối"), onlineTable, moderation));
        VBox.setVgrow(onlineTable, Priority.ALWAYS);
        show(body);

        start.setOnAction(event -> startServer());
        stop.setOnAction(event -> {
            try { if (coordinator != null) coordinator.stopIfSafe(); coordinator = null; refreshOnline(); }
            catch (Exception failure) { Ui.alert(Alert.AlertType.WARNING, "Chưa thể dừng", failure.getMessage()); }
        });
        warn.setOnAction(event -> moderate(false));
        lock.setOnAction(event -> moderate(true));
        locked.setOnAction(event -> lockedDialog());
        refreshOnline();
    }

    private void startServer() {
        try {
            String ip = ipField.getText().strip();
            if (ip.isBlank()) throw new IllegalArgumentException("IP máy chủ không được để trống.");
            InetAddress.getByName(ip);
            int port = Integer.parseInt(portField.getText().strip());
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Cổng không hợp lệ.");
            if (coordinator == null || !coordinator.isRunning()) {
                coordinator = new ExamCoordinator(store, teacher);
                coordinator.setObserver(() -> Platform.runLater(this::refreshOnline));
                coordinator.start(ip, port);
            }
            refreshOnline();
        } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không khởi động được", failure.getMessage()); }
    }

    private void refreshOnline() {
        if (onlineTable == null) return;
        boolean live = coordinator != null && coordinator.isRunning();
        serverState.setText(live ? "Đang chạy tại " + coordinator.host() + ":" + coordinator.port() : "Đã dừng");
        ipField.setDisable(live);
        portField.setDisable(live);
        onlineTable.getItems().setAll(live ? coordinator.onlineStudents() : List.of());
    }

    private static String preferredLanIp() {
        String fallback = "0.0.0.0";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    if (address.isSiteLocalAddress()) return address.getHostAddress();
                    fallback = address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private void moderate(boolean lock) {
        OnlineView selected = onlineTable.getSelectionModel().getSelectedItem();
        if (selected == null) { Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn sinh viên", "Chọn một dòng trong bảng."); return; }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(lock ? "Khóa tài khoản" : "Cảnh báo");
        dialog.setHeaderText(selected.studentCode() + " — " + selected.studentName());
        dialog.setContentText(lock ? "Lý do:" : "Nội dung:");
        dialog.showAndWait().ifPresent(text -> {
            try { if (lock) coordinator.lock(selected.studentCode(), text); else coordinator.warn(selected.studentCode(), text); }
            catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không thực hiện được", failure.getMessage()); }
        });
    }

    private void lockedDialog() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Tài khoản bị khóa");
        ListView<LockedView> list = new ListView<>(FXCollections.observableArrayList(store.lockedStudents()));
        list.setCellFactory(view -> new ListCell<>() { @Override protected void updateItem(LockedView item, boolean empty) {
            super.updateItem(item, empty); setText(empty || item == null ? null : item.studentCode() + " — " + item.studentName() + "\n" + item.reason());
        }});
        Button unlock = Ui.primary("Mở khóa đã chọn");
        unlock.setOnAction(event -> {
            LockedView selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) { store.setLocked(selected.studentCode(), false, null); list.getItems().setAll(store.lockedStudents()); }
        });
        dialog.getDialogPane().setContent(new VBox(12, list, unlock));
        dialog.getDialogPane().setPrefSize(560, 430);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private void showBank() {
        ComboBox<SubjectItem> subjects = new ComboBox<>();
        subjectConverter(subjects);
        TableView<BankQuestion> questions = new TableView<>();
        questions.getColumns().add(column("Môn", 160, BankQuestion::subjectName));
        questions.getColumns().add(column("Độ khó", 80, q -> Integer.toString(q.difficulty())));
        questions.getColumns().add(column("Câu hỏi", 520, BankQuestion::prompt));
        Button addSubject = new Button("Thêm môn");
        Button addQuestion = Ui.primary("Tạo câu hỏi");
        Runnable reload = () -> {
            subjects.getItems().setAll(store.listSubjects(teacher.id()));
            Long id = subjects.getValue() == null ? null : subjects.getValue().id();
            questions.getItems().setAll(store.listQuestions(teacher.id(), id));
        };
        subjects.setOnAction(event -> questions.getItems().setAll(store.listQuestions(teacher.id(),
                subjects.getValue() == null ? null : subjects.getValue().id())));
        addSubject.setOnAction(event -> new TextInputDialog().showAndWait().ifPresent(name -> {
            try { store.createSubject(teacher.id(), name); reload.run(); }
            catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không thêm được môn", failure.getMessage()); }
        }));
        addQuestion.setOnAction(event -> questionDialog(subjects.getValue(), reload));
        VBox body = new VBox(18, Ui.heading("Kho câu hỏi"), Ui.card(new HBox(10, subjects, addSubject, addQuestion)), Ui.card(questions));
        VBox.setVgrow(questions, Priority.ALWAYS); show(body); reload.run();
    }

    private void questionDialog(SubjectItem initial, Runnable reload) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Câu hỏi mới");
        ComboBox<SubjectItem> subject = new ComboBox<>(FXCollections.observableArrayList(store.listSubjects(teacher.id())));
        subjectConverter(subject); subject.setValue(initial);
        Spinner<Integer> difficulty = new Spinner<>(1, 6, 1);
        TextArea prompt = new TextArea(); prompt.setPromptText("Nội dung câu hỏi"); prompt.setPrefRowCount(3);
        TextField a = new TextField(), b = new TextField(), c = new TextField(), d = new TextField();
        a.setPromptText("Lựa chọn A"); b.setPromptText("Lựa chọn B"); c.setPromptText("Lựa chọn C"); d.setPromptText("Lựa chọn D");
        ComboBox<String> correct = new ComboBox<>(FXCollections.observableArrayList("A", "B", "C", "D")); correct.setValue("A");
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10); form.setPadding(new Insets(12));
        form.addRow(0, new Label("Môn"), subject, new Label("Độ khó"), difficulty);
        form.add(prompt, 0, 1, 4, 1); form.add(a, 0, 2, 4, 1); form.add(b, 0, 3, 4, 1);
        form.add(c, 0, 4, 4, 1); form.add(d, 0, 5, 4, 1); form.addRow(6, new Label("Đáp án đúng"), correct);
        dialog.getDialogPane().setContent(form);
        ButtonType save = new ButtonType("Lưu câu hỏi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            if (subject.getValue() == null) throw new IllegalArgumentException("Hãy chọn môn học.");
            store.addQuestion(teacher.id(), new QuestionDraft(subject.getValue().id(), difficulty.getValue(),
                    prompt.getText(), a.getText(), b.getText(), c.getText(), d.getText(), correct.getValue()));
            return null;
        });
        try { dialog.showAndWait(); reload.run(); }
        catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không lưu được câu hỏi", failure.getMessage()); }
    }

    private void showExams() {
        TableView<TemplateItem> table = new TableView<>();
        table.getColumns().add(column("Tên đề", 350, TemplateItem::title));
        table.getColumns().add(column("Môn", 220, TemplateItem::subjectName));
        table.getColumns().add(column("Thời gian", 120, item -> item.durationMinutes() + " phút"));
        table.getColumns().add(column("Số câu", 90, item -> Integer.toString(item.questionCount())));
        Button create = Ui.primary("Tạo đề mới");
        Button open = new Button("Mở ca thi");
        Runnable reload = () -> table.getItems().setAll(store.listTemplates(teacher.id()));
        create.setOnAction(event -> templateDialog(reload));
        open.setOnAction(event -> {
            TemplateItem selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) { Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn đề", "Chọn một đề trong bảng."); return; }
            try {
                if (coordinator == null || !coordinator.isRunning()) throw new IllegalStateException("Hãy khởi động Server tại trang Tổng quan.");
                RoundInfo round = coordinator.openTemplate(selected.id());
                Ui.alert(Alert.AlertType.INFORMATION, "Đã mở ca thi", round.title() + "\nHạn: " + format(round.deadlineEpochMillis()));
            } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không mở được ca thi", failure.getMessage()); }
        });
        VBox body = new VBox(18, Ui.heading("Đề và ca thi"), Ui.card(new HBox(10, create, open)), Ui.card(table));
        VBox.setVgrow(table, Priority.ALWAYS); show(body); reload.run();
    }

    private void templateDialog(Runnable reload) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Tạo đề thi");
        TextField title = new TextField(); title.setPromptText("Tên đề");
        Spinner<Integer> duration = new Spinner<>(1, 360, 45);
        ComboBox<SubjectItem> subject = new ComboBox<>(FXCollections.observableArrayList(store.listSubjects(teacher.id())));
        subjectConverter(subject);
        TableView<BankQuestion> questions = new TableView<>();
        questions.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        questions.getColumns().add(column("Độ khó", 80, q -> Integer.toString(q.difficulty())));
        questions.getColumns().add(column("Câu hỏi", 500, BankQuestion::prompt));
        subject.setOnAction(event -> questions.getItems().setAll(subject.getValue() == null ? List.of() : store.listQuestions(teacher.id(), subject.getValue().id())));
        VBox pane = new VBox(12, new HBox(10, title, subject, duration), new Label("Chọn nhiều câu bằng Command/Shift"), questions);
        pane.setPrefSize(760, 500); dialog.getDialogPane().setContent(pane);
        ButtonType save = new ButtonType("Lưu đề", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            if (subject.getValue() == null) throw new IllegalArgumentException("Chọn môn thi.");
            store.createTemplate(teacher.id(), new TemplateDraft(title.getText(), subject.getValue().id(), duration.getValue(),
                    questions.getSelectionModel().getSelectedItems().stream().map(BankQuestion::id).toList()));
            return null;
        });
        try { dialog.showAndWait(); reload.run(); }
        catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không tạo được đề", failure.getMessage()); }
    }

    private void showResults() {
        TableView<ResultView> table = new TableView<>();
        table.getColumns().add(column("Ca", 70, row -> "#" + row.roundId()));
        table.getColumns().add(column("Đề thi", 260, ResultView::examTitle));
        table.getColumns().add(column("Sinh viên", 240, row -> row.studentCode() + " — " + row.studentName()));
        table.getColumns().add(column("Điểm", 90, row -> String.format("%.2f", row.score())));
        table.getColumns().add(column("Đúng", 80, row -> row.correct() + "/" + row.total()));
        table.getColumns().add(column("Nộp lúc", 150, row -> format(row.submittedAtEpochMillis())));
        Button refresh = Ui.primary("Làm mới"); refresh.setOnAction(event -> table.getItems().setAll(store.results(teacher.id())));
        VBox body = new VBox(18, Ui.heading("Kết quả thi"), Ui.card(refresh), Ui.card(table));
        VBox.setVgrow(table, Priority.ALWAYS); show(body); table.getItems().setAll(store.results(teacher.id()));
    }

    private void showAssignments() {
        TableView<AssignmentItem> assignments = new TableView<>();
        assignments.getColumns().add(column("Bài tập", 300, AssignmentItem::title));
        assignments.getColumns().add(column("Hạn", 160, item -> item.dueAtEpochMillis() == null ? "Không giới hạn" : format(item.dueAtEpochMillis())));
        assignments.getColumns().add(column("Đã nộp", 90, item -> Long.toString(item.submissionCount())));
        TableView<SubmissionView> submissions = new TableView<>();
        submissions.getColumns().add(column("Sinh viên", 230, row -> row.studentCode() + " — " + row.studentName()));
        submissions.getColumns().add(column("Nội dung", 300, SubmissionView::content));
        submissions.getColumns().add(column("Tệp", 160, row -> row.fileName() == null ? "—" : row.fileName()));
        assignments.getSelectionModel().selectedItemProperty().addListener((obs, old, item) ->
                submissions.getItems().setAll(item == null ? List.of() : store.assignmentSubmissions(teacher.id(), item.id())));
        Button create = Ui.primary("Giao bài tập");
        Button download = new Button("Tải tệp bài nộp");
        Runnable reload = () -> assignments.getItems().setAll(store.assignmentsForTeacher(teacher.id()));
        create.setOnAction(event -> assignmentDialog(reload));
        download.setOnAction(event -> downloadSubmission(submissions.getSelectionModel().getSelectedItem()));
        SplitPane split = new SplitPane(assignments, submissions); split.setDividerPositions(0.48);
        VBox body = new VBox(18, Ui.heading("Bài tập và bài nộp"), Ui.card(new HBox(10, create, download)), split);
        VBox.setVgrow(split, Priority.ALWAYS); show(body); reload.run();
    }

    private void assignmentDialog(Runnable reload) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Giao bài tập");
        TextField title = new TextField(); title.setPromptText("Tên bài tập");
        TextArea description = new TextArea(); description.setPromptText("Yêu cầu bài làm");
        TextField due = new TextField(); due.setPromptText("Hạn nộp, ví dụ 30/10/2026 21:00 (có thể bỏ trống)");
        Label fileLabel = new Label("Chưa chọn tệp");
        final File[] selected = new File[1];
        Button choose = new Button("Đính kèm tệp");
        choose.setOnAction(event -> { FileChooser picker = new FileChooser(); selected[0] = picker.showOpenDialog(stage); fileLabel.setText(selected[0] == null ? "Chưa chọn tệp" : selected[0].getName()); });
        VBox pane = new VBox(12, title, description, due, new HBox(10, choose, fileLabel)); pane.setPrefWidth(600);
        dialog.getDialogPane().setContent(pane);
        ButtonType save = new ButtonType("Giao bài", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.setResultConverter(button -> {
            if (button != save) return null;
            try {
                Long dueAt = due.getText().isBlank() ? null : LocalDateTime.parse(due.getText().strip(), TIME)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                byte[] data = selected[0] == null ? null : Files.readAllBytes(selected[0].toPath());
                store.createAssignment(teacher.id(), new AssignmentDraft(title.getText(), description.getText(), dueAt,
                        selected[0] == null ? null : selected[0].getName(), data), System.currentTimeMillis());
                if (coordinator != null) coordinator.assignmentsChanged();
            } catch (Exception failure) { throw new IllegalArgumentException(failure.getMessage(), failure); }
            return null;
        });
        try { dialog.showAndWait(); reload.run(); }
        catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không giao được bài", failure.getMessage()); }
    }

    private void downloadSubmission(SubmissionView selected) {
        if (selected == null || selected.fileName() == null) { Ui.alert(Alert.AlertType.INFORMATION, "Chưa có tệp", "Chọn bài nộp có tệp đính kèm."); return; }
        try {
            FileChooser chooser = new FileChooser(); chooser.setInitialFileName(selected.fileName());
            File target = chooser.showSaveDialog(stage); if (target == null) return;
            Files.write(target.toPath(), store.teacherSubmissionFile(teacher.id(), selected.id()).data());
        } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không tải được tệp", failure.getMessage()); }
    }

    private void changePassword() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Đổi mật khẩu");
        PasswordField old = new PasswordField(), next = new PasswordField(); old.setPromptText("Mật khẩu hiện tại"); next.setPromptText("Mật khẩu mới");
        dialog.getDialogPane().setContent(new VBox(10, old, next));
        ButtonType save = new ButtonType("Đổi mật khẩu", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.setResultConverter(button -> { if (button == save) store.changeTeacherPassword(teacher.id(), old.getText(), next.getText()); return null; });
        try { dialog.showAndWait(); }
        catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không đổi được mật khẩu", failure.getMessage()); }
    }

    private void leaveWorkspace() {
        try {
            if (coordinator != null) coordinator.stopIfSafe();
            coordinator = null;
            logout.run();
        } catch (Exception failure) { Ui.alert(Alert.AlertType.WARNING, "Chưa thể đăng xuất", failure.getMessage()); }
    }

    void forceClose() { if (coordinator != null) coordinator.close(); }

    private static <T> TableColumn<T, String> column(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue()))); return column;
    }

    private static void subjectConverter(ComboBox<SubjectItem> box) {
        box.setConverter(new StringConverter<>() {
            @Override public String toString(SubjectItem item) { return item == null ? "Tất cả môn" : item.name(); }
            @Override public SubjectItem fromString(String text) { return null; }
        });
        box.setPrefWidth(230);
    }

    private static String format(long epochMillis) {
        return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
    }
}
