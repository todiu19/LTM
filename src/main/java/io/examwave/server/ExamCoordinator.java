package io.examwave.server;

import io.examwave.common.Domain.*;
import io.examwave.common.Wire;
import io.examwave.common.Wire.Packet;
import io.examwave.store.ExamStore;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExamCoordinator implements AutoCloseable {
    private final ExamStore store;
    private final Teacher teacher;
    private final Map<String, Peer> students = new ConcurrentHashMap<>();
    private final Map<AttemptKey, Map<Long, String>> liveAnswers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "examwave-deadline-clock");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Runnable observer = () -> {};
    private volatile boolean running;
    private volatile ServerSocket listener;
    private volatile Long announcedRound;

    private record AttemptKey(long roundId, String studentCode) {}

    public ExamCoordinator(ExamStore store, Teacher teacher) {
        this.store = Objects.requireNonNull(store);
        this.teacher = Objects.requireNonNull(teacher);
    }

    public void setObserver(Runnable observer) {
        this.observer = observer == null ? () -> {} : observer;
    }

    public synchronized void start(int port) throws IOException {
        start("0.0.0.0", port);
    }

    public synchronized void start(String host, int port) throws IOException {
        if (running) return;
        listener = new ServerSocket();
        listener.bind(new InetSocketAddress(host, port));
        running = true;
        Thread.ofVirtual().name("examwave-accept").start(this::acceptLoop);
        clock.scheduleWithFixedDelay(this::deadlineTick, 250, 500, TimeUnit.MILLISECONDS);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = listener.accept();
                socket.setKeepAlive(true);
                socket.setSoTimeout(20_000);
                Thread.ofVirtual().name("examwave-peer").start(new Peer(socket));
            } catch (SocketException stopped) {
                if (running) System.err.println("Socket Server: " + stopped.getMessage());
            } catch (IOException failure) {
                if (running) System.err.println("Không nhận được Client: " + failure.getMessage());
            }
        }
    }

    public RoundInfo openTemplate(long templateId) {
        if (!running) throw new IllegalStateException("Hãy khởi động Server trước.");
        RoundInfo round = store.openTemplate(teacher.id(), templateId, System.currentTimeMillis());
        announcedRound = round.id();
        for (Peer peer : students.values()) {
            peer.status = "ONLINE";
            peer.activity = "Sẵn sàng";
            peer.sendOffer();
        }
        changed();
        return round;
    }

    public void warn(String studentCode, String message) {
        String text = message == null ? "" : message.strip();
        if (text.isBlank() || text.length() > 1000) throw new IllegalArgumentException("Cảnh báo phải có từ 1 đến 1.000 ký tự.");
        Peer peer = students.get(studentCode);
        if (peer == null) throw new IllegalStateException("Sinh viên không còn online.");
        peer.send(Wire.packet("WARNING", text));
    }

    public void lock(String studentCode, String reason) {
        store.setLocked(studentCode, true, reason);
        Peer peer = students.get(studentCode);
        if (peer != null) {
            peer.send(Wire.packet("LOCKED", "Tài khoản đã bị khóa: " + reason.strip()));
            peer.close();
        }
        changed();
    }

    public void unlock(String studentCode) {
        store.setLocked(studentCode, false, null);
        changed();
    }

    public void assignmentsChanged() {
        for (Peer peer : students.values()) {
            peer.send(Wire.packet("ASSIGNMENTS_CHANGED", null));
            peer.sendAssignments();
        }
    }

    public List<OnlineView> onlineStudents() {
        return students.values().stream().map(peer -> new OnlineView(peer.student.code(), peer.student.name(),
                peer.socket.getInetAddress().getHostAddress(), peer.status, peer.activity)).sorted(
                java.util.Comparator.comparing(OnlineView::studentCode)).toList();
    }

    public boolean isRunning() { return running; }
    public String host() { return listener == null ? "" : listener.getInetAddress().getHostAddress(); }
    public int port() { return listener == null ? -1 : listener.getLocalPort(); }

    public synchronized void stopIfSafe() {
        if (store.activeRound(teacher.id(), System.currentTimeMillis()).isPresent())
            throw new IllegalStateException("Ca thi đang diễn ra nên chưa thể dừng Server.");
        close();
    }

    private void deadlineTick() {
        if (!running) return;
        long now = System.currentTimeMillis();
        try {
            for (ExamStore.PendingAttempt pending : store.expiredAttempts(now)) {
                if (pending.teacherId() != teacher.id()) continue;
                AttemptKey key = new AttemptKey(pending.roundId(), pending.studentCode());
                Grade grade = store.submit(teacher.id(), pending.studentCode(),
                        new AnswerBundle(pending.roundId(), liveAnswers.getOrDefault(key, Map.of())),
                        pending.deadlineEpochMillis());
                liveAnswers.remove(key);
                Peer peer = students.get(pending.studentCode());
                if (peer != null) {
                    peer.status = "SUBMITTED";
                    peer.activity = "Đã chấm tự động";
                    peer.send(Wire.packet("GRADE", grade));
                    peer.sendHistory();
                }
            }
            store.closeExpiredRounds(now);
            Optional<RoundInfo> active = store.activeRound(teacher.id(), now);
            if (active.isEmpty() && announcedRound != null) {
                announcedRound = null;
                students.values().forEach(Peer::sendOffer);
            }
            changed();
        } catch (Exception failure) {
            System.err.println("Tác vụ hết giờ: " + failure.getMessage());
        }
    }

    private void changed() {
        try { observer.run(); } catch (Exception ignored) { }
    }

    @Override
    public synchronized void close() {
        running = false;
        try { if (listener != null) listener.close(); } catch (IOException ignored) { }
        new ArrayList<>(students.values()).forEach(Peer::close);
        students.clear();
        clock.shutdownNow();
        changed();
    }

    private final class Peer implements Runnable {
        private final Socket socket;
        private DataInputStream input;
        private DataOutputStream output;
        private volatile Student student;
        private volatile boolean closed;
        private volatile String status = "ONLINE";
        private volatile String activity = "Sảnh chờ";
        private volatile Long currentRound;

        private Peer(Socket socket) { this.socket = socket; }

        @Override public void run() {
            try {
                input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                Packet first = Wire.read(input);
                if (first == null || !"LOGIN".equals(first.type())) throw new IllegalArgumentException("Kết nối phải đăng nhập trước.");
                login(Wire.body(first, LoginRequest.class));
                while (!closed) {
                    Packet packet = Wire.read(input);
                    if (packet == null) break;
                    long receivedAt = System.currentTimeMillis();
                    handle(packet, receivedAt);
                }
            } catch (Exception failure) {
                if (!closed) send(Wire.packet("ERROR", failure.getMessage() == null ? "Kết nối lỗi." : failure.getMessage()));
            } finally {
                close();
            }
        }

        private void login(LoginRequest request) {
            Optional<Student> authenticated = store.authenticateStudent(request.studentCode(), request.birthDate());
            if (authenticated.isEmpty()) {
                send(Wire.packet("LOGIN_REPLY", new LoginReply(false, "Sai mã sinh viên hoặc ngày sinh.", null)));
                throw new IllegalArgumentException("Đăng nhập thất bại.");
            }
            student = authenticated.get();
            Peer previous = students.put(student.code(), this);
            if (previous != null && previous != this) previous.close();
            send(Wire.packet("LOGIN_REPLY", new LoginReply(true, "Đăng nhập thành công.", student.name())));
            sendHistory();
            sendOffer();
            sendAssignments();
            changed();
        }

        private void handle(Packet packet, long receivedAt) {
            if (student == null) throw new IllegalStateException("Bạn chưa đăng nhập.");
            if (store.isLocked(student.code())) {
                send(Wire.packet("LOCKED", "Tài khoản đã bị khóa."));
                close();
                return;
            }
            switch (packet.type()) {
                case "PING" -> send(Wire.packet("PONG", null));
                case "HISTORY" -> sendHistory();
                case "OFFER" -> sendOffer();
                case "JOIN" -> join(packet.body().asLong(), receivedAt);
                case "ANSWERS" -> updateAnswers(Wire.body(packet, AnswerBundle.class), receivedAt);
                case "SUBMIT" -> submit(Wire.body(packet, AnswerBundle.class), receivedAt);
                case "ASSIGNMENTS" -> sendAssignments();
                case "ASSIGN_SUBMIT" -> submitAssignment(Wire.body(packet, AssignmentTurnIn.class), receivedAt);
                case "FILE_GET" -> send(Wire.packet("FILE_DATA", store.assignmentFile(teacher.id(), student.code(), Wire.body(packet, FileRequest.class))));
                default -> send(Wire.packet("ERROR", "Yêu cầu không được hỗ trợ: " + packet.type()));
            }
        }

        private void join(long roundId, long receivedAt) {
            ExamPaper paper = store.joinRound(teacher.id(), student.code(), roundId, receivedAt);
            currentRound = roundId;
            liveAnswers.put(new AttemptKey(roundId, student.code()), Map.of());
            status = "DOING_EXAM";
            activity = paper.title();
            send(Wire.packet("PAPER", paper));
            changed();
        }

        private void updateAnswers(AnswerBundle bundle, long receivedAt) {
            if (!Objects.equals(currentRound, bundle.roundId()) || !store.roundAccepts(teacher.id(), bundle.roundId(), receivedAt)) return;
            Map<Long, String> snapshot = bundle.answers() == null ? Map.of() : Map.copyOf(bundle.answers());
            liveAnswers.put(new AttemptKey(bundle.roundId(), student.code()), snapshot);
        }

        private void submit(AnswerBundle bundle, long receivedAt) {
            if (!Objects.equals(currentRound, bundle.roundId())) throw new IllegalArgumentException("Bài nộp không thuộc ca đang làm.");
            updateAnswers(bundle, receivedAt);
            Map<Long, String> answers = liveAnswers.getOrDefault(new AttemptKey(bundle.roundId(), student.code()),
                    bundle.answers() == null ? Map.of() : bundle.answers());
            Grade grade = store.submit(teacher.id(), student.code(), new AnswerBundle(bundle.roundId(), answers), receivedAt);
            liveAnswers.remove(new AttemptKey(bundle.roundId(), student.code()));
            status = "SUBMITTED";
            activity = "Đã nộp " + grade.correct() + "/" + grade.total();
            send(Wire.packet("GRADE", grade));
            sendHistory();
            sendOffer();
            changed();
        }

        private void submitAssignment(AssignmentTurnIn turnIn, long receivedAt) {
            SubmissionView saved = store.submitAssignment(teacher.id(), student.code(), turnIn, receivedAt);
            send(Wire.packet("ASSIGNMENT_SAVED", saved));
            sendAssignments();
        }

        private void sendHistory() {
            if (student != null) send(Wire.packet("HISTORY_DATA", store.history(teacher.id(), student.code())));
        }

        private void sendOffer() {
            if (student != null) send(Wire.packet("OFFER_DATA", store.offer(teacher.id(), student.code(), System.currentTimeMillis()).orElse(null)));
        }

        private void sendAssignments() {
            if (student != null) send(Wire.packet("ASSIGNMENTS_DATA", store.assignmentsForStudent(teacher.id(), student.code())));
        }

        private synchronized void send(Packet packet) {
            if (closed || output == null) return;
            try { Wire.write(output, packet); }
            catch (IOException failure) { close(); }
        }

        private void close() {
            if (closed) return;
            closed = true;
            if (student != null) students.remove(student.code(), this);
            try { socket.close(); } catch (IOException ignored) { }
            changed();
        }
    }
}
