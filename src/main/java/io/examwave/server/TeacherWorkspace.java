package io.examwave.server;

import io.examwave.common.CsvHelper;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    Scene scene() { return new Scene(root, 1220, 780); }

    private VBox sidebar() {
        Label logo = new Label("◈  EXAMWAVE");
        logo.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: 900;");
        Label identity = new Label(teacher.fullName() + "\n@" + teacher.username());
        identity.setStyle("-fx-text-fill: #B8C0D8;");
        Button dashboard = nav("Tổng quan", this::showDashboard);
        Button bank = nav("Kho câu hỏi", this::showBank);
        Button exams = nav("Đề & ca thi", this::showExams);
        Button classes = nav("Lớp & Sinh viên", this::showClassesAndStudents);
        Button results = nav("Kết quả", this::showResults);
        Button assignments = nav("Bài tập", this::showAssignments);
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Button password = nav("Đổi mật khẩu", this::changePassword);
        Button leave = nav("Đăng xuất", this::leaveWorkspace);
        VBox menu = new VBox(10, logo, identity, new Separator(), dashboard, bank, exams, classes, results, assignments,
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

    // --- KHO CÂU HỎI ---

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

    // --- LỚP HỌC & SINH VIÊN (QUẢN LÝ LỚP, NHẬP CSV) ---

    private void showClassesAndStudents() {
        ComboBox<String> classFilter = new ComboBox<>();
        classFilter.setPromptText("Chọn lớp học");
        classFilter.setPrefWidth(200);

        Button addClass = new Button("Thêm lớp");
        Button deleteClass = new Button("Xóa lớp");
        Button importCsv = Ui.primary("Nhập danh sách từ CSV");
        Button addStudent = new Button("Thêm sinh viên");

        TableView<StudentView> table = new TableView<>();
        table.getColumns().add(column("Mã sinh viên", 130, StudentView::code));
        table.getColumns().add(column("Họ và tên", 220, StudentView::fullName));
        table.getColumns().add(column("Ngày sinh", 120, StudentView::birthDate));
        table.getColumns().add(column("Lớp", 140, StudentView::className));
        table.getColumns().add(column("Trạng thái", 220, s -> s.locked() ? "Đã khóa (" + s.lockReason() + ")" : "Bình thường"));

        Label countLabel = new Label("Tổng số sinh viên: 0");
        countLabel.setStyle("-fx-font-weight: 700; -fx-text-fill: #667085;");

        Runnable reloadClasses = () -> {
            String current = classFilter.getValue();
            List<StudentClassItem> classes = store.listClasses(teacher.id());
            List<String> items = new ArrayList<>();
            items.add("Tất cả lớp");
            for (StudentClassItem c : classes) {
                items.add(c.name());
            }
            classFilter.getItems().setAll(items);
            if (current != null && items.contains(current)) {
                classFilter.setValue(current);
            } else {
                classFilter.setValue("Tất cả lớp");
            }
        };

        Runnable reloadStudents = () -> {
            String sel = classFilter.getValue();
            List<StudentView> list = store.listStudents(sel);
            table.getItems().setAll(list);
            countLabel.setText("Tổng số sinh viên: " + list.size());
        };

        classFilter.setOnAction(event -> reloadStudents.run());

        addClass.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Thêm lớp học");
            dialog.setHeaderText("Tạo lớp học mới");
            dialog.setContentText("Tên/Mã lớp (ví dụ D21CQCN01-B):");
            dialog.showAndWait().ifPresent(name -> {
                try {
                    store.createClass(teacher.id(), name);
                    reloadClasses.run();
                } catch (Exception failure) {
                    Ui.alert(Alert.AlertType.ERROR, "Không thêm được lớp", failure.getMessage());
                }
            });
        });

        deleteClass.setOnAction(event -> {
            String current = classFilter.getValue();
            if (current == null || current.isBlank() || "Tất cả lớp".equalsIgnoreCase(current)) {
                Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn lớp", "Hãy chọn một lớp cụ thể từ danh sách để xóa.");
                return;
            }
            Optional<StudentClassItem> found = store.listClasses(teacher.id()).stream()
                    .filter(c -> c.name().equalsIgnoreCase(current)).findFirst();
            if (found.isEmpty() || found.get().id() == 0) {
                Ui.alert(Alert.AlertType.WARNING, "Không thể xóa", "Lớp này được phát hiện từ dữ liệu sinh viên.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc chắn muốn xóa lớp " + current + "? Sinh viên thuộc lớp này sẽ chuyển về 'Chưa phân lớp'.", ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    store.deleteClass(teacher.id(), found.get().id());
                    reloadClasses.run();
                    reloadStudents.run();
                }
            });
        });

        importCsv.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Chọn file CSV danh sách sinh viên");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("File CSV (*.csv)", "*.csv"),
                    new FileChooser.ExtensionFilter("File văn bản (*.txt)", "*.txt"),
                    new FileChooser.ExtensionFilter("Tất cả các file (*.*)", "*.*")
            );
            File file = chooser.showOpenDialog(stage);
            if (file == null) return;

            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                String fallbackClass = classFilter.getValue();
                if ("Tất cả lớp".equalsIgnoreCase(fallbackClass)) fallbackClass = "Chưa phân lớp";

                StudentImportSummary summary = store.importStudentsFromCsv(teacher.id(), content, fallbackClass);

                StringBuilder msg = new StringBuilder();
                msg.append("Đã xử lý ").append(summary.totalProcessed()).append(" dòng.\n")
                        .append("• Thêm mới: ").append(summary.inserted()).append(" sinh viên\n")
                        .append("• Cập nhật: ").append(summary.updated()).append(" sinh viên\n");

                if (!summary.errors().isEmpty()) {
                    msg.append("\nCó ").append(summary.errors().size()).append(" dòng bị bỏ qua hoặc lỗi:\n");
                    for (int i = 0; i < Math.min(summary.errors().size(), 5); i++) {
                        msg.append("- ").append(summary.errors().get(i)).append("\n");
                    }
                    if (summary.errors().size() > 5) {
                        msg.append("... và ").append(summary.errors().size() - 5).append(" lỗi khác.");
                    }
                }

                Ui.alert(summary.errors().isEmpty() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                        "Kết quả nhập CSV", msg.toString());

                reloadClasses.run();
                reloadStudents.run();
            } catch (Exception failure) {
                Ui.alert(Alert.AlertType.ERROR, "Không đọc được file CSV", failure.getMessage());
            }
        });

        addStudent.setOnAction(event -> studentDialog(classFilter.getValue(), () -> {
            reloadClasses.run();
            reloadStudents.run();
        }));

        HBox topBar = new HBox(10, new Label("Lớp:"), classFilter, addClass, deleteClass, importCsv, addStudent);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label hintLabel = new Label("💡 Định dạng CSV hỗ trợ: [Mã SV, Họ tên, Ngày sinh, Lớp] hoặc [STT, Mã SV, Họ tên, Ngày sinh, Lớp]. Ngày sinh: dd/MM/yyyy hoặc yyyy-MM-dd.");
        hintLabel.setStyle("-fx-text-fill: #667085; -fx-font-size: 12px;");

        VBox body = new VBox(18, Ui.heading("Quản lý lớp và sinh viên"), Ui.card(topBar), Ui.card(countLabel, table, hintLabel));
        VBox.setVgrow(table, Priority.ALWAYS);
        show(body);

        reloadClasses.run();
        reloadStudents.run();
    }

    private void studentDialog(String currentClass, Runnable reload) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Thêm / Cập nhật sinh viên");
        TextField code = new TextField(); code.setPromptText("B21DCCN001");
        TextField name = new TextField(); name.setPromptText("Nguyễn Văn An");
        DatePicker birth = new DatePicker(LocalDate.of(2003, 1, 1));
        TextField className = new TextField(currentClass == null || "Tất cả lớp".equalsIgnoreCase(currentClass) ? "" : currentClass);
        className.setPromptText("D21CQCN01-B");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Mã sinh viên:"), code);
        grid.addRow(1, new Label("Họ và tên:"), name);
        grid.addRow(2, new Label("Ngày sinh:"), birth);
        grid.addRow(3, new Label("Lớp:"), className);

        dialog.getDialogPane().setContent(grid);
        ButtonType save = new ButtonType("Lưu sinh viên", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, save);
        dialog.setResultConverter(btn -> {
            if (btn == save) {
                store.saveStudent(code.getText(), name.getText(), birth.getValue(), className.getText());
            }
            return null;
        });

        try {
            dialog.showAndWait();
            reload.run();
        } catch (Exception failure) {
            Ui.alert(Alert.AlertType.ERROR, "Không lưu được sinh viên", failure.getMessage());
        }
    }

    // --- ĐỀ VÀ CA THI (CHIA CA, GIỚI HẠN SĨ SỐ) ---

    private void showExams() {
        // 1. Bảng danh sách đề thi
        TableView<TemplateItem> templateTable = new TableView<>();
        templateTable.getColumns().add(column("Tên đề", 320, TemplateItem::title));
        templateTable.getColumns().add(column("Môn", 180, TemplateItem::subjectName));
        templateTable.getColumns().add(column("Thời gian", 110, item -> item.durationMinutes() + " phút"));
        templateTable.getColumns().add(column("Số câu", 80, item -> Integer.toString(item.questionCount())));
        templateTable.setPrefHeight(200);

        Button createTemplate = Ui.primary("Tạo đề mới");
        Button openRoundBtn = new Button("Mở ca thi");
        Button splitRoundsBtn = new Button("⚡ Chia ca thi tự động");

        // 2. Bảng danh sách ca thi
        TableView<RoundSummary> roundTable = new TableView<>();
        roundTable.getColumns().add(column("Mã ca", 70, r -> "#" + r.id()));
        roundTable.getColumns().add(column("Tên ca thi", 280, RoundSummary::title));
        roundTable.getColumns().add(column("Môn", 160, RoundSummary::subjectName));
        roundTable.getColumns().add(column("Lớp áp dụng", 130, r -> r.targetClass() == null ? "Tất cả" : r.targetClass()));
        roundTable.getColumns().add(column("Sĩ số", 110, r -> r.maxParticipants() == null ? (r.participantCount() + " (KGH)") : (r.participantCount() + "/" + r.maxParticipants())));
        roundTable.getColumns().add(column("Hạn nộp", 140, r -> format(r.deadlineEpochMillis())));
        roundTable.getColumns().add(column("Trạng thái", 120, r -> r.active() ? (System.currentTimeMillis() < r.deadlineEpochMillis() ? "🟢 Đang mở" : "🟡 Hết giờ") : "⚪ Đã đóng"));

        Button activateRound = new Button("Kích hoạt lại ca");
        Button closeRound = new Button("Đóng ca thi");
        Button refreshRounds = new Button("Làm mới ca thi");

        Runnable reloadTemplates = () -> templateTable.getItems().setAll(store.listTemplates(teacher.id()));
        Runnable reloadRounds = () -> roundTable.getItems().setAll(store.listRounds(teacher.id()));

        createTemplate.setOnAction(event -> templateDialog(reloadTemplates));

        openRoundBtn.setOnAction(event -> {
            TemplateItem selected = templateTable.getSelectionModel().getSelectedItem();
            openRoundCustomDialog(selected, () -> {
                reloadRounds.run();
                if (coordinator != null) coordinator.notifyRoundsChanged();
            });
        });

        splitRoundsBtn.setOnAction(event -> {
            TemplateItem selected = templateTable.getSelectionModel().getSelectedItem();
            splitSessionDialog(selected, () -> {
                reloadRounds.run();
                if (coordinator != null) coordinator.notifyRoundsChanged();
            });
        });

        activateRound.setOnAction(event -> {
            RoundSummary selected = roundTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn ca thi", "Hãy chọn một ca thi từ bảng phía dưới.");
                return;
            }
            TextInputDialog minDialog = new TextInputDialog("45");
            minDialog.setTitle("Kích hoạt ca thi");
            minDialog.setHeaderText("Mở lại ca: " + selected.title());
            minDialog.setContentText("Thời gian làm bài tính từ bây giờ (phút):");
            minDialog.showAndWait().ifPresent(mins -> {
                try {
                    int duration = Integer.parseInt(mins.strip());
                    store.activateRound(teacher.id(), selected.id(), duration, System.currentTimeMillis());
                    reloadRounds.run();
                    if (coordinator != null) coordinator.notifyRoundsChanged();
                    Ui.alert(Alert.AlertType.INFORMATION, "Đã kích hoạt ca thi", "Ca thi #" + selected.id() + " đã được mở lại.");
                } catch (Exception failure) {
                    Ui.alert(Alert.AlertType.ERROR, "Lỗi kích hoạt", failure.getMessage());
                }
            });
        });

        closeRound.setOnAction(event -> {
            RoundSummary selected = roundTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn ca thi", "Hãy chọn một ca thi từ bảng phía dưới.");
                return;
            }
            store.closeRound(teacher.id(), selected.id());
            reloadRounds.run();
            if (coordinator != null) coordinator.notifyRoundsChanged();
        });

        refreshRounds.setOnAction(event -> reloadRounds.run());

        HBox templateControls = new HBox(10, createTemplate, openRoundBtn, splitRoundsBtn);
        HBox roundControls = new HBox(10, activateRound, closeRound, refreshRounds);

        VBox templateCard = Ui.card(new Label("1. Ngân hàng Đề thi"), templateControls, templateTable);
        VBox roundCard = Ui.card(new Label("2. Các ca thi đã thiết lập & đang diễn ra"), roundControls, roundTable);
        VBox.setVgrow(roundTable, Priority.ALWAYS);

        VBox body = new VBox(18, Ui.heading("Đề và ca thi"), templateCard, roundCard);
        VBox.setVgrow(roundCard, Priority.ALWAYS);
        show(body);

        reloadTemplates.run();
        reloadRounds.run();
    }

    private void openRoundCustomDialog(TemplateItem preselected, Runnable onSuccess) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Mở ca thi");
        dialog.setHeaderText("Cấu hình ca thi mới");

        ComboBox<TemplateItem> templateCombo = new ComboBox<>(FXCollections.observableArrayList(store.listTemplates(teacher.id())));
        templateCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TemplateItem item) { return item == null ? "Chọn đề" : item.title() + " (" + item.subjectName() + ")"; }
            @Override public TemplateItem fromString(String text) { return null; }
        });
        if (preselected != null) templateCombo.setValue(preselected);

        TextField titleField = new TextField();
        titleField.setPromptText("Tên ca thi (để trống sẽ lấy theo tên đề)");

        ComboBox<String> classCombo = new ComboBox<>();
        List<String> classes = new ArrayList<>();
        classes.add("Tất cả");
        for (StudentClassItem c : store.listClasses(teacher.id())) classes.add(c.name());
        classCombo.getItems().setAll(classes);
        classCombo.setValue("Tất cả");

        TextField maxStudentsField = new TextField();
        maxStudentsField.setPromptText("Số lượng tối đa / ca (để trống = không giới hạn)");

        Spinner<Integer> durationSpinner = new Spinner<>(5, 360, 45);

        templateCombo.setOnAction(e -> {
            TemplateItem sel = templateCombo.getValue();
            if (sel != null && titleField.getText().isBlank()) {
                titleField.setText(sel.title());
                durationSpinner.getValueFactory().setValue(sel.durationMinutes());
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Đề thi:"), templateCombo);
        grid.addRow(1, new Label("Tên ca thi:"), titleField);
        grid.addRow(2, new Label("Lớp áp dụng:"), classCombo);
        grid.addRow(3, new Label("Số lượng người / ca:"), maxStudentsField);
        grid.addRow(4, new Label("Thời gian làm bài (phút):"), durationSpinner);

        dialog.getDialogPane().setContent(grid);
        ButtonType openBtn = new ButtonType("Mở ca thi ngay", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, openBtn);

        dialog.setResultConverter(btn -> {
            if (btn == openBtn) {
                TemplateItem item = templateCombo.getValue();
                if (item == null) throw new IllegalArgumentException("Hãy chọn đề thi.");
                Integer max = null;
                if (!maxStudentsField.getText().isBlank()) {
                    try { max = Integer.parseInt(maxStudentsField.getText().strip()); }
                    catch (NumberFormatException e) { throw new IllegalArgumentException("Số lượng người phải là số nguyên."); }
                }

                String targetClass = "Tất cả".equalsIgnoreCase(classCombo.getValue()) ? null : classCombo.getValue();
                String title = titleField.getText().isBlank() ? item.title() : titleField.getText().strip();
                int dur = durationSpinner.getValue();

                if (coordinator != null && coordinator.isRunning()) {
                    coordinator.openRound(new OpenRoundDraft(item.id(), title, targetClass, max, dur));
                } else {
                    store.openRound(teacher.id(), new OpenRoundDraft(item.id(), title, targetClass, max, dur), System.currentTimeMillis());
                }
            }
            return null;
        });

        try {
            dialog.showAndWait();
            onSuccess.run();
        } catch (Exception failure) {
            Ui.alert(Alert.AlertType.ERROR, "Không mở được ca thi", failure.getMessage());
        }
    }

    private void splitSessionDialog(TemplateItem preselected, Runnable onSuccess) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Chia ca thi tự động");
        dialog.setHeaderText("Chia sinh viên trong lớp thành các ca thi dựa trên số lượng người / ca");

        ComboBox<TemplateItem> templateCombo = new ComboBox<>(FXCollections.observableArrayList(store.listTemplates(teacher.id())));
        templateCombo.setConverter(new StringConverter<>() {
            @Override public String toString(TemplateItem item) { return item == null ? "Chọn đề" : item.title() + " (" + item.subjectName() + ")"; }
            @Override public TemplateItem fromString(String text) { return null; }
        });
        if (preselected != null) templateCombo.setValue(preselected);

        ComboBox<String> classCombo = new ComboBox<>();
        List<String> classes = new ArrayList<>();
        for (StudentClassItem c : store.listClasses(teacher.id())) classes.add(c.name());
        classCombo.getItems().setAll(classes);
        if (!classes.isEmpty()) classCombo.setValue(classes.get(0));

        Spinner<Integer> perRoundSpinner = new Spinner<>(1, 500, 25);
        Spinner<Integer> durationSpinner = new Spinner<>(5, 360, 45);

        Label previewLabel = new Label("Chọn lớp để xem sĩ số...");
        previewLabel.setStyle("-fx-text-fill: #6C5CE7; -fx-font-weight: 700;");

        Runnable updatePreview = () -> {
            String cName = classCombo.getValue();
            if (cName != null) {
                List<StudentView> svs = store.listStudents(cName);
                int total = svs.size();
                int per = perRoundSpinner.getValue();
                int numRounds = per > 0 ? (int) Math.ceil((double) total / per) : 0;
                previewLabel.setText("Lớp " + cName + " có " + total + " SV → Sẽ chia thành " + numRounds + " ca thi (" + per + " người/ca).");
            }
        };

        classCombo.setOnAction(e -> updatePreview.run());
        perRoundSpinner.valueProperty().addListener((obs, old, val) -> updatePreview.run());
        updatePreview.run();

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Đề thi:"), templateCombo);
        grid.addRow(1, new Label("Lớp học:"), classCombo);
        grid.addRow(2, new Label("Số lượng người / ca:"), perRoundSpinner);
        grid.addRow(3, new Label("Thời gian làm bài (phút):"), durationSpinner);
        grid.add(previewLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);
        ButtonType createBtn = new ButtonType("Tạo các ca thi", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, createBtn);

        dialog.setResultConverter(btn -> {
            if (btn == createBtn) {
                TemplateItem item = templateCombo.getValue();
                if (item == null) throw new IllegalArgumentException("Hãy chọn đề thi.");
                String targetClass = classCombo.getValue();
                if (targetClass == null || targetClass.isBlank()) throw new IllegalArgumentException("Hãy chọn lớp học.");
                int per = perRoundSpinner.getValue();
                int dur = durationSpinner.getValue();

                SplitSessionDraft draft = new SplitSessionDraft(item.id(), targetClass, per, dur);
                List<RoundSummary> created;
                if (coordinator != null && coordinator.isRunning()) {
                    created = coordinator.splitExamRounds(draft);
                } else {
                    created = store.splitExamRounds(teacher.id(), draft, System.currentTimeMillis());
                }
                Platform.runLater(() -> Ui.alert(Alert.AlertType.INFORMATION, "Chia ca thành công",
                        "Đã tạo thành công " + created.size() + " ca thi cho lớp " + targetClass + "."));
            }
            return null;
        });

        try {
            dialog.showAndWait();
            onSuccess.run();
        } catch (Exception failure) {
            Ui.alert(Alert.AlertType.ERROR, "Không thể chia ca thi", failure.getMessage());
        }
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

    // --- KẾT QUẢ THI & XUẤT CSV ---

    private void showResults() {
        ComboBox<String> roundFilter = new ComboBox<>();
        roundFilter.setPrefWidth(220);
        roundFilter.setPromptText("Tất cả ca thi");

        ComboBox<String> classFilter = new ComboBox<>();
        classFilter.setPrefWidth(160);
        classFilter.setPromptText("Tất cả lớp");

        Button filterBtn = new Button("Lọc kết quả");
        Button exportCsvBtn = Ui.primary("Xuất kết quả ra file CSV");

        TableView<ResultView> table = new TableView<>();
        table.getColumns().add(column("Ca", 70, row -> "#" + row.roundId()));
        table.getColumns().add(column("Đề thi", 240, ResultView::examTitle));
        table.getColumns().add(column("Mã SV", 120, ResultView::studentCode));
        table.getColumns().add(column("Họ tên", 180, ResultView::studentName));
        table.getColumns().add(column("Lớp", 120, ResultView::className));
        table.getColumns().add(column("Điểm", 80, row -> String.format("%.2f", row.score())));
        table.getColumns().add(column("Đúng", 80, row -> row.correct() + "/" + row.total()));
        table.getColumns().add(column("Nộp lúc", 140, row -> format(row.submittedAtEpochMillis())));

        Label resultSummary = new Label("Tổng cộng: 0 bài thi");
        resultSummary.setStyle("-fx-font-weight: 700; -fx-text-fill: #667085;");

        // Tải danh sách bộ lọc
        List<String> roundItems = new ArrayList<>();
        roundItems.add("Tất cả ca thi");
        for (RoundSummary r : store.listRounds(teacher.id())) {
            roundItems.add("#" + r.id() + " - " + r.title());
        }
        roundFilter.getItems().setAll(roundItems);
        roundFilter.setValue("Tất cả ca thi");

        List<String> classItems = new ArrayList<>();
        classItems.add("Tất cả lớp");
        for (StudentClassItem c : store.listClasses(teacher.id())) {
            classItems.add(c.name());
        }
        classFilter.getItems().setAll(classItems);
        classFilter.setValue("Tất cả lớp");

        Runnable reloadResults = () -> {
            Long roundId = null;
            String roundVal = roundFilter.getValue();
            if (roundVal != null && roundVal.startsWith("#")) {
                int dash = roundVal.indexOf(" - ");
                if (dash > 1) {
                    try { roundId = Long.parseLong(roundVal.substring(1, dash)); } catch (Exception ignored) {}
                }
            }
            String classVal = classFilter.getValue();
            if ("Tất cả lớp".equalsIgnoreCase(classVal)) {
                classVal = null;
            }
            List<ResultView> results = store.results(teacher.id(), roundId, classVal);
            table.getItems().setAll(results);
            resultSummary.setText("Tổng cộng: " + results.size() + " bài nộp");
        };

        filterBtn.setOnAction(e -> reloadResults.run());
        roundFilter.setOnAction(e -> reloadResults.run());
        classFilter.setOnAction(e -> reloadResults.run());

        exportCsvBtn.setOnAction(event -> {
            List<ResultView> currentData = table.getItems();
            if (currentData.isEmpty()) {
                Ui.alert(Alert.AlertType.INFORMATION, "Không có dữ liệu", "Không có kết quả nào để xuất CSV.");
                return;
            }

            FileChooser chooser = new FileChooser();
            chooser.setTitle("Lưu file CSV kết quả làm bài");
            chooser.setInitialFileName("KetQuaThi_" + LocalDate.now() + ".csv");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("File CSV (*.csv)", "*.csv"),
                    new FileChooser.ExtensionFilter("Tất cả các file (*.*)", "*.*")
            );
            File file = chooser.showSaveDialog(stage);
            if (file == null) return;

            try {
                String csvText = CsvHelper.exportResultsCsv(currentData);
                Files.writeString(file.toPath(), csvText, StandardCharsets.UTF_8);
                Ui.alert(Alert.AlertType.INFORMATION, "Xuất CSV thành công",
                        "Đã xuất " + currentData.size() + " dòng kết quả ra file:\n" + file.getAbsolutePath());
            } catch (Exception failure) {
                Ui.alert(Alert.AlertType.ERROR, "Lỗi khi lưu file", failure.getMessage());
            }
        });

        HBox filterBar = new HBox(10, new Label("Ca thi:"), roundFilter, new Label("Lớp:"), classFilter, filterBtn, exportCsvBtn);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(18, Ui.heading("Kết quả thi"), Ui.card(filterBar), Ui.card(resultSummary, table));
        VBox.setVgrow(table, Priority.ALWAYS);
        show(body);
        reloadResults.run();
    }

    // --- BÀI TẬP ---

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
