package io.examwave;

import io.examwave.common.Domain.*;
import io.examwave.store.ExamStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExamStoreTest {
    private ExamStore store() {
        return new ExamStore("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    }

    @Test void completeExamLifecycleIsScopedAndScored() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.one", "Giảng viên Một", "password-123");
        assertTrue(store.authenticateTeacher("teacher.one", "password-123").isPresent());
        long subject = store.createSubject(teacher.id(), "Lập trình mạng");
        long q1 = store.addQuestion(teacher.id(), new QuestionDraft(subject, 2, "TCP là gì?", "Giao thức", "CSDL", "UI", "Ảnh", "A"));
        long q2 = store.addQuestion(teacher.id(), new QuestionDraft(subject, 3, "JSON dùng để?", "Nén", "Trao đổi dữ liệu", "Vẽ", "Mã hóa", "B"));
        long template = store.createTemplate(teacher.id(), new TemplateDraft("Giữa kỳ", subject, 30, List.of(q1, q2)));
        long now = System.currentTimeMillis();
        RoundInfo round = store.openTemplate(teacher.id(), template, now);
        Student student = store.authenticateStudent("B21DCCN001", "2003-05-12").orElseThrow();
        ExamPaper paper = store.joinRound(teacher.id(), student.code(), round.id(), now + 10);
        assertEquals(2, paper.questions().size());
        Grade grade = store.submit(teacher.id(), student.code(),
                new AnswerBundle(round.id(), Map.of(paper.questions().get(0).id(), "A", paper.questions().get(1).id(), "B")), now + 20);
        assertEquals(10.0, grade.score());
        assertEquals(1, store.history(teacher.id(), student.code()).size());
        assertTrue(store.offer(teacher.id(), student.code(), now + 30).isEmpty());
    }

    @Test void assignmentsCanBeSubmittedAndReplacedBeforeDeadline() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.two", "Giảng viên Hai", "password-456");
        long now = System.currentTimeMillis();
        long assignment = store.createAssignment(teacher.id(),
                new AssignmentDraft("Socket chat", "Nộp mã nguồn", now + 60_000, "guide.txt", "guide".getBytes()), now);
        SubmissionView first = store.submitAssignment(teacher.id(), "B21DCCN001",
                new AssignmentTurnIn(assignment, "Bản đầu", "answer.txt", "v1".getBytes(), false), now + 1_000);
        assertEquals("answer.txt", first.fileName());
        SubmissionView second = store.submitAssignment(teacher.id(), "B21DCCN001",
                new AssignmentTurnIn(assignment, "Bản mới", null, null, true), now + 2_000);
        assertEquals("Bản mới", second.content());
        assertEquals("answer.txt", second.fileName());
        assertArrayEquals("v1".getBytes(), store.assignmentFile(teacher.id(), "B21DCCN001",
                new FileRequest(assignment, true)).data());
    }

    @Test void accountLockPersists() {
        ExamStore store = store();
        store.setLocked("B21DCCN002", true, "Vi phạm quy chế");
        assertThrows(IllegalStateException.class,
                () -> store.authenticateStudent("B21DCCN002", "2003-08-20"));
        store.setLocked("B21DCCN002", false, null);
        assertTrue(store.authenticateStudent("B21DCCN002", "2003-08-20").isPresent());
    }
}
