package io.examwave.student;

import com.fasterxml.jackson.core.type.TypeReference;
import io.examwave.common.Domain.*;
import io.examwave.common.Ui;
import io.examwave.common.Wire;
import io.examwave.common.Wire.Packet;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public final class StudentApp extends Application {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final StudentConnection connection = new StudentConnection();
    private final ExecutorService outbound = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "examwave-student-outbound"); thread.setDaemon(true); return thread;
    });
    private final Map<Long, ToggleGroup> answerGroups = new LinkedHashMap<>();
    private Stage stage;
    private int connectionVersion;
    private String studentName;
    private ExamOffer offer;
    private ExamPaper paper;
    private List<HistoryItem> history = List.of();
    private List<AssignmentItem> assignments = List.of();
    private Timeline timer;
    private long lastAnswerSent;
    private File selectedSubmissionFile;

    @Override public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("ExamWave Student");
        connection.onPacket(this::onPacket);
        connection.onDisconnect(message -> {
            int version = connectionVersion;
            Platform.runLater(() -> {
                if (version != connectionVersion) return;
                connectionVersion++;
                stopTimer();
                showLogin();
                Ui.alert(Alert.AlertType.WARNING, "Mất kết nối", message);
            });
        });
        showLogin();
        stage.show();
    }

    private void showLogin() {
        Label badge = new Label("EXAMWAVE / STUDENT");
        badge.setStyle("-fx-font-weight: 900; -fx-text-fill: #6C5CE7;");
        Label title = new Label("Sẵn sàng cho ca học hôm nay?");
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 34px; -fx-font-weight: 900; -fx-text-fill: #172033;");
        Label intro = new Label("Kết nối trực tiếp tới máy Mac của giảng viên trong cùng mạng LAN.");
        intro.setWrapText(true); intro.setStyle("-fx-text-fill: #667085;");
        VBox hero = new VBox(14, badge, title, intro);
        hero.setPadding(new Insets(42)); hero.setPrefWidth(430);
        hero.setStyle("-fx-background-color: linear-gradient(to bottom right, #ECE8FF, #F8F6FF);");

        TextField host = new TextField("127.0.0.1");
        TextField port = new TextField("5050");
        TextField code = new TextField(); code.setPromptText("B21DCCN001");
        DatePicker birth = new DatePicker(LocalDate.of(2003, 5, 12));
        Button login = Ui.primary("Kết nối và đăng nhập");
        Label state = new Label("Chưa kết nối"); state.setStyle("-fx-text-fill: #667085;");
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(12);
        form.addRow(0, new Label("IP Server"), host);
        form.addRow(1, new Label("Cổng"), port);
        form.addRow(2, new Label("Mã sinh viên"), code);
        form.addRow(3, new Label("Ngày sinh"), birth);
        VBox loginCard = Ui.card(Ui.heading("Đăng nhập"), form, login, state);
        loginCard.setMaxWidth(460);
        StackPane formArea = new StackPane(loginCard); formArea.setPadding(new Insets(42));
        HBox root = new HBox(hero, formArea); HBox.setHgrow(formArea, Priority.ALWAYS);
        stage.setScene(new Scene(root, 940, 600));

        login.setOnAction(event -> {
            try {
                int selectedPort = Integer.parseInt(port.getText().strip());
                String studentCode = code.getText().strip().toUpperCase();
                if (host.getText().isBlank() || studentCode.isBlank() || birth.getValue() == null)
                    throw new IllegalArgumentException("Vui lòng nhập đủ thông tin.");
                int version = ++connectionVersion;
                login.setDisable(true); state.setText("Đang kết nối...");
                Thread.ofVirtual().start(() -> {
                    try {
                        connection.connect(host.getText().strip(), selectedPort);
                        connection.send(Wire.packet("LOGIN", new LoginRequest(studentCode, birth.getValue().toString())));
                    } catch (Exception failure) {
                        Platform.runLater(() -> {
                            if (version != connectionVersion) return;
                            login.setDisable(false); state.setText("Kết nối thất bại");
                            Ui.alert(Alert.AlertType.ERROR, "Không kết nối được", failure.getMessage());
                        });
                    }
                });
            } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Thông tin chưa hợp lệ", failure.getMessage()); }
        });
    }

    private void onPacket(Packet packet) {
        int version = connectionVersion;
        Platform.runLater(() -> {
            if (version != connectionVersion) return;
            try {
                switch (packet.type()) {
                    case "LOGIN_REPLY" -> loginReply(Wire.body(packet, LoginReply.class));
                    case "HISTORY_DATA" -> { history = Wire.JSON.convertValue(packet.body(), new TypeReference<List<HistoryItem>>() {}); if (paper == null) showHome(); }
                    case "OFFER_DATA" -> { offer = packet.body().isNull() ? null : Wire.body(packet, ExamOffer.class); if (paper == null) showHome(); }
                    case "PAPER" -> showPaper(Wire.body(packet, ExamPaper.class));
                    case "GRADE" -> showGrade(Wire.body(packet, Grade.class));
                    case "ASSIGNMENTS_DATA" -> { assignments = Wire.JSON.convertValue(packet.body(), new TypeReference<List<AssignmentItem>>() {}); if (paper == null) showHome(); }
                    case "ASSIGNMENTS_CHANGED" -> send("ASSIGNMENTS", null);
                    case "ASSIGNMENT_SAVED" -> Ui.alert(Alert.AlertType.INFORMATION, "Đã lưu bài nộp", "Bài tập đã được gửi tới giảng viên.");
                    case "FILE_DATA" -> saveReceivedFile(Wire.body(packet, FilePayload.class));
                    case "WARNING" -> nonBlockingNotice("Cảnh báo từ giảng viên", packet.body().asText());
                    case "LOCKED" -> { logout(false); Ui.alert(Alert.AlertType.WARNING, "Tài khoản bị khóa", packet.body().asText()); }
                    case "ERROR" -> Ui.alert(Alert.AlertType.ERROR, "Server báo lỗi", packet.body().asText());
                    case "PONG" -> { }
                    default -> { }
                }
            } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không đọc được dữ liệu", failure.getMessage()); }
        });
    }

    private void loginReply(LoginReply reply) {
        if (!reply.success()) {
            connection.close();
            Ui.alert(Alert.AlertType.ERROR, "Đăng nhập thất bại", reply.message());
            showLogin();
            return;
        }
        studentName = reply.studentName();
        paper = null; offer = null; history = List.of(); assignments = List.of();
        showHome();
    }

    private void showHome() {
        if (studentName == null || paper != null) return;
        BorderPane root = new BorderPane(); root.setStyle("-fx-background-color: #F5F6FA;");
        Label logo = new Label("◈ EXAMWAVE"); logo.setStyle("-fx-font-size: 17px; -fx-font-weight: 900; -fx-text-fill: white;");
        Label person = new Label(studentName); person.setStyle("-fx-text-fill: #C9D0E3;");
        Button logout = new Button("Đăng xuất");
        logout.setStyle("-fx-background-color: transparent; -fx-text-fill: white;");
        logout.setOnAction(event -> logout(true));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, logo, person, spacer, logout); header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 24, 18, 24)); header.setStyle("-fx-background-color: #172033;"); root.setTop(header);

        TabPane tabs = new TabPane(); tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Bài thi", examDashboard()));
        tabs.getTabs().add(new Tab("Bài tập", assignmentDashboard()));
        root.setCenter(tabs);
        stage.setScene(new Scene(root, 1040, 720));
    }

    private Pane examDashboard() {
        Label welcome = Ui.heading("Xin chào, " + studentName);
        Label offerText = new Label(); offerText.setWrapText(true);
        Button join = Ui.primary("Tham gia bài thi");
        if (offer == null) {
            offerText.setText("Chưa có ca thi đang mở. Trang sẽ tự cập nhật khi giảng viên mở đề.");
            join.setDisable(true);
        } else {
            String extra = "";
            if (offer.targetClass() != null) extra += "  [Lớp: " + offer.targetClass() + "]";
            if (offer.maxParticipants() != null) extra += "  [Tối đa: " + offer.maxParticipants() + " SV]";
            offerText.setText(offer.subjectName() + "  •  " + offer.title() + extra + "\nHạn chung: " + format(offer.deadlineEpochMillis()));
            join.setText(offer.rejoin() ? "Làm lại từ đầu" : "Tham gia bài thi");
            join.setDisable(System.currentTimeMillis() >= offer.deadlineEpochMillis());
            join.setOnAction(event -> send("JOIN", offer.roundId()));
        }
        HBox offerCard = new HBox(18, offerText, join); offerCard.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(offerText, Priority.ALWAYS);
        TableView<HistoryItem> table = new TableView<>(FXCollections.observableArrayList(history));
        table.getColumns().add(column("Ca", 70, row -> "#" + row.roundId()));
        table.getColumns().add(column("Đề thi", 260, HistoryItem::title));
        table.getColumns().add(column("Môn", 180, HistoryItem::subjectName));
        table.getColumns().add(column("Điểm", 90, row -> String.format("%.2f", row.score())));
        table.getColumns().add(column("Đúng", 80, row -> row.correct() + "/" + row.total()));
        table.getColumns().add(column("Nộp lúc", 160, row -> format(row.submittedAtEpochMillis())));
        Button refresh = new Button("Làm mới");
        refresh.setOnAction(event -> { send("HISTORY", null); send("OFFER", null); });
        VBox pane = new VBox(18, welcome, Ui.card(new Label("Ca thi đang mở"), offerCard), Ui.card(new HBox(refresh), table));
        VBox.setVgrow(table, Priority.ALWAYS); pane.setPadding(new Insets(24)); return pane;
    }

    private Pane assignmentDashboard() {
        TableView<AssignmentItem> table = new TableView<>(FXCollections.observableArrayList(assignments));
        table.getColumns().add(column("Bài tập", 280, AssignmentItem::title));
        table.getColumns().add(column("Hạn nộp", 160, row -> row.dueAtEpochMillis() == null ? "Không giới hạn" : format(row.dueAtEpochMillis())));
        table.getColumns().add(column("Trạng thái", 130, row -> row.mySubmission() == null ? "Chưa nộp" : "Đã nộp"));
        TextArea details = new TextArea(); details.setEditable(false); details.setWrapText(true);
        TextArea content = new TextArea(); content.setPromptText("Nội dung bài làm"); content.setWrapText(true);
        Label selectedFile = new Label("Chưa chọn tệp mới");
        Button choose = new Button("Chọn tệp bài làm");
        Button submit = Ui.primary("Nộp bài tập");
        Button downloadTask = new Button("Tải tệp đề bài");
        Button downloadMine = new Button("Tải tệp đã nộp");
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            selectedSubmissionFile = null; selectedFile.setText("Chưa chọn tệp mới");
            if (item == null) return;
            details.setText(item.title() + "\n\n" + item.description());
            content.setText(item.mySubmission() == null ? "" : item.mySubmission().content());
            downloadTask.setDisable(item.fileName() == null);
            downloadMine.setDisable(item.mySubmission() == null || item.mySubmission().fileName() == null);
            submit.setText(item.mySubmission() == null ? "Nộp bài tập" : "Cập nhật bài nộp");
        });
        choose.setOnAction(event -> {
            FileChooser picker = new FileChooser(); selectedSubmissionFile = picker.showOpenDialog(stage);
            selectedFile.setText(selectedSubmissionFile == null ? "Chưa chọn tệp mới" : selectedSubmissionFile.getName());
        });
        submit.setOnAction(event -> submitAssignment(table.getSelectionModel().getSelectedItem(), content.getText()));
        downloadTask.setOnAction(event -> requestFile(table.getSelectionModel().getSelectedItem(), false));
        downloadMine.setOnAction(event -> requestFile(table.getSelectionModel().getSelectedItem(), true));
        VBox editor = Ui.card(new Label("Chi tiết"), details, new Label("Bài làm của bạn"), content,
                new HBox(10, choose, selectedFile), new HBox(10, submit, downloadTask, downloadMine));
        SplitPane split = new SplitPane(table, editor); split.setDividerPositions(0.45);
        VBox pane = new VBox(18, Ui.heading("Bài tập"), split); pane.setPadding(new Insets(24)); VBox.setVgrow(split, Priority.ALWAYS); return pane;
    }

    private void submitAssignment(AssignmentItem selected, String content) {
        if (selected == null) { Ui.alert(Alert.AlertType.INFORMATION, "Chưa chọn bài tập", "Chọn một bài tập trong danh sách."); return; }
        try {
            byte[] data = selectedSubmissionFile == null ? null : Files.readAllBytes(selectedSubmissionFile.toPath());
            send("ASSIGN_SUBMIT", new AssignmentTurnIn(selected.id(), content,
                    selectedSubmissionFile == null ? null : selectedSubmissionFile.getName(), data,
                    selectedSubmissionFile == null && selected.mySubmission() != null && selected.mySubmission().fileName() != null));
        } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không đọc được tệp", failure.getMessage()); }
    }

    private void requestFile(AssignmentItem selected, boolean submission) {
        if (selected != null) send("FILE_GET", new FileRequest(selected.id(), submission));
    }

    private void saveReceivedFile(FilePayload file) {
        try {
            FileChooser chooser = new FileChooser(); chooser.setInitialFileName(file.fileName());
            File target = chooser.showSaveDialog(stage); if (target != null) Files.write(target.toPath(), file.data());
        } catch (Exception failure) { Ui.alert(Alert.AlertType.ERROR, "Không lưu được tệp", failure.getMessage()); }
    }

    private void showPaper(ExamPaper paper) {
        this.paper = paper; this.offer = null; answerGroups.clear();
        Label title = Ui.heading(paper.subjectName() + " — " + paper.title());
        Label clock = new Label(); clock.setStyle("-fx-font-size: 20px; -fx-font-weight: 900; -fx-text-fill: #D35400;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(16, title, spacer, clock); header.setAlignment(Pos.CENTER_LEFT); header.setPadding(new Insets(20));
        VBox questions = new VBox(16); questions.setPadding(new Insets(12, 22, 22, 22));
        int index = 1;
        for (PublicQuestion question : paper.questions()) {
            ToggleGroup group = new ToggleGroup(); answerGroups.put(question.id(), group);
            VBox options = new VBox(8);
            char letter = 'A';
            for (String option : question.options()) {
                RadioButton button = new RadioButton(letter + ". " + option); button.setToggleGroup(group); button.setUserData(String.valueOf(letter++));
                button.setWrapText(true); options.getChildren().add(button);
            }
            group.selectedToggleProperty().addListener((obs, old, value) -> sendAnswers());
            Label prompt = new Label("Câu " + index++ + ". " + question.prompt()); prompt.setWrapText(true); prompt.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");
            questions.getChildren().add(Ui.card(prompt, options));
        }
        ScrollPane scroll = new ScrollPane(questions); scroll.setFitToWidth(true); scroll.setStyle("-fx-background: #F5F6FA;");
        Button submit = Ui.primary("Nộp bài"); submit.setOnAction(event -> submitPaper());
        HBox footer = new HBox(submit); footer.setAlignment(Pos.CENTER_RIGHT); footer.setPadding(new Insets(16, 22, 18, 22));
        BorderPane root = new BorderPane(scroll, header, null, footer, null); root.setStyle("-fx-background-color: #F5F6FA;");
        stage.setScene(new Scene(root, 980, 760));
        stopTimer();
        timer = new Timeline(new KeyFrame(Duration.millis(250), event -> {
            long remaining = Math.max(0, paper.deadlineEpochMillis() - System.currentTimeMillis());
            long seconds = (remaining + 999) / 1000;
            clock.setText("Còn %02d:%02d".formatted(seconds / 60, seconds % 60));
            if (remaining == 0) { stopTimer(); submitPaper(); }
        }));
        timer.setCycleCount(Timeline.INDEFINITE); timer.play();
    }

    private void sendAnswers() {
        long now = System.nanoTime();
        if (now - lastAnswerSent < 20_000_000L) return;
        lastAnswerSent = now;
        send("ANSWERS", new AnswerBundle(paper.roundId(), answerSnapshot()));
    }

    private void submitPaper() {
        if (paper == null) return;
        AnswerBundle bundle = new AnswerBundle(paper.roundId(), answerSnapshot());
        send("SUBMIT", bundle);
    }

    private Map<Long, String> answerSnapshot() {
        Map<Long, String> answers = new LinkedHashMap<>();
        answerGroups.forEach((id, group) -> answers.put(id, group.getSelectedToggle() == null ? "" : group.getSelectedToggle().getUserData().toString()));
        return answers;
    }

    private void showGrade(Grade grade) {
        stopTimer(); paper = null; answerGroups.clear();
        Label icon = new Label("✓"); icon.setStyle("-fx-font-size: 58px; -fx-text-fill: #12A594; -fx-font-weight: 900;");
        Label title = Ui.heading("Bài thi đã được ghi nhận");
        Label score = new Label(String.format("%.2f / 10", grade.score()));
        score.setStyle("-fx-font-size: 42px; -fx-font-weight: 900; -fx-text-fill: #6C5CE7;");
        Label detail = new Label("Đúng " + grade.correct() + "/" + grade.total() + " câu");
        Button home = Ui.primary("Về trang chính"); home.setOnAction(event -> { send("HISTORY", null); send("OFFER", null); showHome(); });
        VBox card = Ui.card(icon, title, score, detail, home); card.setAlignment(Pos.CENTER); card.setMaxWidth(520);
        StackPane root = new StackPane(card); root.setPadding(new Insets(40)); root.setStyle("-fx-background-color: #F5F6FA;");
        stage.setScene(new Scene(root, 900, 600));
    }

    private void send(String type, Object body) {
        int version = connectionVersion;
        outbound.execute(() -> {
            if (version != connectionVersion) return;
            try { connection.send(Wire.packet(type, body)); }
            catch (Exception failure) { Platform.runLater(() -> { if (version == connectionVersion) Ui.alert(Alert.AlertType.ERROR, "Không gửi được dữ liệu", failure.getMessage()); }); }
        });
    }

    private void nonBlockingNotice(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING); alert.initOwner(stage); alert.setTitle(title); alert.setHeaderText(title); alert.setContentText(message); alert.show();
    }

    private void logout(boolean showLogin) {
        connectionVersion++; stopTimer(); connection.close(); studentName = null; paper = null; offer = null; history = List.of(); assignments = List.of();
        if (showLogin) showLogin(); else showLogin();
    }

    private void stopTimer() { if (timer != null) timer.stop(); timer = null; }

    private static <T> TableColumn<T, String> column(String title, double width, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title); column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue()))); return column;
    }

    private static String format(long epochMillis) { return TIME.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())); }

    @Override public void stop() { connectionVersion++; stopTimer(); connection.close(); outbound.shutdownNow(); }
    public static void main(String[] args) { launch(args); }
}
