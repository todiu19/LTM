package io.examwave;

import io.examwave.common.CsvHelper;
import io.examwave.common.Domain.*;
import io.examwave.store.ExamStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    @Test void classManagementAndCsvImportWorks() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.classes", "Giảng viên Lớp", "password-123");

        long classId = store.createClass(teacher.id(), "CNTT-K21");
        assertTrue(classId > 0);

        String csvData = """
                Mã SV,Họ và tên,Ngày sinh,Lớp
                B21DCCN888,Hoàng Văn Em,15/09/2003,CNTT-K21
                B21DCCN999,Vũ Thị Lan,2003-10-25,CNTT-K22
                B21DCCN777,Phạm Minh Tuấn,01/01/2003,
                """;

        StudentImportSummary summary = store.importStudentsFromCsv(teacher.id(), csvData, "CNTT-K21");
        assertEquals(3, summary.totalProcessed());
        assertEquals(3, summary.inserted());
        assertEquals(0, summary.updated());
        assertTrue(summary.errors().isEmpty());

        List<StudentView> k21 = store.listStudents("CNTT-K21");
        assertEquals(2, k21.size()); // B21DCCN888 và B21DCCN777 (fallback)

        List<StudentView> k22 = store.listStudents("CNTT-K22");
        assertEquals(1, k22.size());
        assertEquals("Vũ Thị Lan", k22.get(0).fullName());
    }

    @Test void splitExamRoundsAndMaxParticipantsEnforced() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.rounds", "Giảng viên Ca", "password-123");
        long subject = store.createSubject(teacher.id(), "Hệ điều hành");
        long q = store.addQuestion(teacher.id(), new QuestionDraft(subject, 1, "Thread là gì?", "Luồng", "Bộ nhớ", "Ổ cứng", "Mạng", "A"));
        long template = store.createTemplate(teacher.id(), new TemplateDraft("Kiểm tra 15p", subject, 15, List.of(q)));

        String className = "D21-SPECIAL";
        store.saveStudent("SV001", "Sinh viên 1", LocalDate.of(2003, 1, 1), className);
        store.saveStudent("SV002", "Sinh viên 2", LocalDate.of(2003, 1, 2), className);
        store.saveStudent("SV003", "Sinh viên 3", LocalDate.of(2003, 1, 3), className);

        long now = System.currentTimeMillis();
        // Chia 3 sinh viên thành các ca mỗi ca tối đa 2 người -> sẽ sinh ra 2 ca
        List<RoundSummary> rounds = store.splitExamRounds(teacher.id(),
                new SplitSessionDraft(template, className, 2, 15), now);
        assertEquals(2, rounds.size());

        RoundSummary round1 = rounds.get(0);
        RoundSummary round2 = rounds.get(1);

        // SV001 thuộc ca 1 -> join thành công
        ExamPaper paper1 = store.joinRound(teacher.id(), "SV001", round1.id(), now + 10);
        assertNotNull(paper1);

        // SV003 thuộc ca 2 -> nếu cố join ca 1 sẽ bị từ chối
        assertThrows(IllegalStateException.class, () ->
                store.joinRound(teacher.id(), "SV003", round1.id(), now + 15));

        // SV003 join ca 2 -> thành công
        ExamPaper paper2 = store.joinRound(teacher.id(), "SV003", round2.id(), now + 20);
        assertNotNull(paper2);
    }

    @Test void maxParticipantsLimitEnforced() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.limit", "Giảng viên Limit", "password-123");
        long subject = store.createSubject(teacher.id(), "Mạng");
        long q = store.addQuestion(teacher.id(), new QuestionDraft(subject, 1, "IP là?", "A", "B", "C", "D", "A"));
        long template = store.createTemplate(teacher.id(), new TemplateDraft("Thi 1 người", subject, 10, List.of(q)));

        long now = System.currentTimeMillis();
        // Mở ca giới hạn tối đa 1 người
        RoundInfo round = store.openRound(teacher.id(),
                new OpenRoundDraft(template, "Ca thi 1 người", null, 1, 10), now);

        // Sinh viên 1 join
        store.joinRound(teacher.id(), "B21DCCN001", round.id(), now + 5);

        // Sinh viên 2 cố join -> ca đã đủ 1 người
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                store.joinRound(teacher.id(), "B21DCCN002", round.id(), now + 10));
        assertTrue(ex.getMessage().contains("đủ số lượng sinh viên"));

        // Sinh viên 1 rejoin lại -> vẫn được phép
        assertNotNull(store.joinRound(teacher.id(), "B21DCCN001", round.id(), now + 15));
    }

    @Test void exportResultsToCsvWorks() {
        ExamStore store = store();
        Teacher teacher = store.createTeacher("teacher.csv", "Giảng viên CSV", "password-123");
        long subject = store.createSubject(teacher.id(), "Cơ sở dữ liệu");
        long q = store.addQuestion(teacher.id(), new QuestionDraft(subject, 1, "SQL là?", "Ngôn ngữ truy vấn", "B", "C", "D", "A"));
        long template = store.createTemplate(teacher.id(), new TemplateDraft("Thi SQL", subject, 20, List.of(q)));

        store.saveStudent("SV99", "Đặng Thị Thảo", LocalDate.of(2003, 7, 7), "D21HTTT01");
        long now = System.currentTimeMillis();
        RoundInfo round = store.openTemplate(teacher.id(), template, now);

        ExamPaper paper = store.joinRound(teacher.id(), "SV99", round.id(), now + 5);
        store.submit(teacher.id(), "SV99", new AnswerBundle(round.id(), Map.of(paper.questions().get(0).id(), "A")), now + 10);

        List<ResultView> results = store.results(teacher.id(), round.id(), "D21HTTT01");
        assertEquals(1, results.size());
        assertEquals("D21HTTT01", results.get(0).className());
        assertEquals(10.0, results.get(0).score());
        assertEquals(1, store.results(teacher.id(), null, "Tất cả lớp").size());

        String csv = CsvHelper.exportResultsCsv(results);
        assertTrue(csv.startsWith("\uFEFF")); // UTF-8 BOM
        assertTrue(csv.contains("SV99"));
        assertTrue(csv.contains("Đặng Thị Thảo"));
        assertTrue(csv.contains("D21HTTT01"));
        assertTrue(csv.contains("10.00"));
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
