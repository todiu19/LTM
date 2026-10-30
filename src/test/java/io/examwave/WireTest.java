package io.examwave;

import io.examwave.common.Domain.LoginRequest;
import io.examwave.common.Wire;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WireTest {
    @Test void packetUsesLengthPrefixedJsonFrame() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Wire.write(new DataOutputStream(bytes), Wire.packet("LOGIN", new LoginRequest("B21DCCN001", "2003-05-12")));
        Wire.Packet packet = Wire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals("LOGIN", packet.type());
        assertEquals("B21DCCN001", Wire.body(packet, LoginRequest.class).studentCode());
    }
}
