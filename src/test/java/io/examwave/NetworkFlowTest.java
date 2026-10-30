package io.examwave;

import io.examwave.common.Domain.*;
import io.examwave.common.Wire;
import io.examwave.server.ExamCoordinator;
import io.examwave.store.ExamStore;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NetworkFlowTest {
    @Test void studentLogsInJoinsAndSubmitsOverRealTcp() throws Exception {
        ExamStore store = new ExamStore("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        Teacher teacher = store.createTeacher("network.teacher", "Network Teacher", "password-789");
        long subject = store.createSubject(teacher.id(), "Mạng máy tính");
        long question = store.addQuestion(teacher.id(), new QuestionDraft(subject, 1, "IP là?", "Địa chỉ", "Tệp", "Ảnh", "Cổng", "A"));
        long template = store.createTemplate(teacher.id(), new TemplateDraft("Kiểm tra nhanh", subject, 5, List.of(question)));
        try (ExamCoordinator server = new ExamCoordinator(store, teacher)) {
            server.start("127.0.0.1", 0);
            assertEquals("127.0.0.1", server.host());
            RoundInfo round = server.openTemplate(template);
            try (Socket socket = new Socket("127.0.0.1", server.port());
                 DataInputStream input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
                Wire.write(output, Wire.packet("LOGIN", new LoginRequest("B21DCCN001", "2003-05-12")));
                assertEquals("LOGIN_REPLY", Wire.read(input).type());
                Wire.read(input); // history
                assertEquals("OFFER_DATA", Wire.read(input).type());
                Wire.read(input); // assignments
                Wire.write(output, Wire.packet("JOIN", round.id()));
                ExamPaper paper = Wire.body(Wire.read(input), ExamPaper.class);
                Wire.write(output, Wire.packet("SUBMIT", new AnswerBundle(round.id(), Map.of(paper.questions().get(0).id(), "A"))));
                Grade grade = Wire.body(Wire.read(input), Grade.class);
                assertEquals(10.0, grade.score());
            }
        }
    }
}
