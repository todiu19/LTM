package io.examwave.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

public final class Wire {
    public static final int MAX_FRAME_BYTES = 8 * 1024 * 1024;
    public static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Wire() {}

    public record Packet(String type, JsonNode body) {}

    public static Packet packet(String type, Object body) {
        return new Packet(type, body == null ? JSON.nullNode() : JSON.valueToTree(body));
    }

    public static <T> T body(Packet packet, Class<T> type) {
        return JSON.convertValue(packet.body(), type);
    }

    public static void write(DataOutputStream output, Packet packet) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(packet);
        if (bytes.length > MAX_FRAME_BYTES) throw new IOException("Gói dữ liệu vượt quá 8 MB.");
        output.writeInt(bytes.length);
        output.write(bytes);
        output.flush();
    }

    public static Packet read(DataInputStream input) throws IOException {
        final int length;
        try {
            length = input.readInt();
        } catch (EOFException end) {
            return null;
        }
        if (length <= 0 || length > MAX_FRAME_BYTES) throw new IOException("Kích thước gói dữ liệu không hợp lệ.");
        return JSON.readValue(input.readNBytes(length), Packet.class);
    }
}
