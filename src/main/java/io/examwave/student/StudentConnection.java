package io.examwave.student;

import io.examwave.common.Wire;
import io.examwave.common.Wire.Packet;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class StudentConnection implements AutoCloseable {
    private volatile Socket socket;
    private volatile DataOutputStream output;
    private volatile boolean closed = true;
    private volatile Consumer<Packet> packets = ignored -> {};
    private volatile Consumer<String> disconnected = ignored -> {};
    private ScheduledExecutorService heartbeat;

    public void onPacket(Consumer<Packet> listener) { packets = listener == null ? ignored -> {} : listener; }
    public void onDisconnect(Consumer<String> listener) { disconnected = listener == null ? ignored -> {} : listener; }

    public synchronized void connect(String host, int port) throws IOException {
        close();
        Socket next = new Socket();
        next.connect(new InetSocketAddress(host, port), 5_000);
        next.setKeepAlive(true);
        next.setSoTimeout(20_000);
        socket = next;
        output = new DataOutputStream(new BufferedOutputStream(next.getOutputStream()));
        closed = false;
        Thread.ofVirtual().name("examwave-student-reader").start(() -> readLoop(next));
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "examwave-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            try { send(Wire.packet("PING", null)); } catch (Exception ignored) { }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void readLoop(Socket current) {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(current.getInputStream()))) {
            Packet packet;
            while (isCurrent(current) && (packet = Wire.read(input)) != null) packets.accept(packet);
            if (isCurrent(current)) disconnected.accept("Server đã đóng kết nối.");
        } catch (Exception failure) {
            if (isCurrent(current)) disconnected.accept(failure.getMessage() == null ? "Mất kết nối." : failure.getMessage());
        } finally {
            closeIfCurrent(current);
        }
    }

    public synchronized void send(Packet packet) {
        if (closed || output == null) throw new IllegalStateException("Chưa kết nối Server.");
        try { Wire.write(output, packet); }
        catch (IOException failure) { throw new IllegalStateException("Không gửi được dữ liệu.", failure); }
    }

    private synchronized boolean isCurrent(Socket candidate) { return !closed && socket == candidate; }
    private synchronized void closeIfCurrent(Socket candidate) { if (socket == candidate) close(); }

    @Override public synchronized void close() {
        closed = true;
        if (heartbeat != null) heartbeat.shutdownNow();
        heartbeat = null;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        socket = null;
        output = null;
    }
}
