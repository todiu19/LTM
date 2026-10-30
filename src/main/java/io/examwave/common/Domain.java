package io.examwave.common;

import java.util.List;
import java.util.Map;

public final class Domain {
    private Domain() {}

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 20_000;

    public record Teacher(long id, String username, String fullName) {}
    public record Student(String code, String name) {}
    public record LoginRequest(String studentCode, String birthDate) {}
    public record LoginReply(boolean success, String message, String studentName) {}

    public record SubjectItem(long id, String name, long questionCount) {}
    public record BankQuestion(long id, long subjectId, String subjectName, int difficulty,
                               String prompt, String optionA, String optionB,
                               String optionC, String optionD, String correctOption) {}
    public record QuestionDraft(long subjectId, int difficulty, String prompt,
                                String optionA, String optionB, String optionC,
                                String optionD, String correctOption) {}

    public record TemplateItem(long id, String title, String subjectName,
                               int durationMinutes, int questionCount) {}
    public record TemplateDraft(String title, long subjectId, int durationMinutes,
                                List<Long> questionIds) {}

    public record ExamOffer(long roundId, String title, String subjectName,
                            long deadlineEpochMillis, boolean rejoin) {}
    public record PublicQuestion(long id, String prompt, List<String> options) {}
    public record ExamPaper(long roundId, String title, String subjectName,
                            long deadlineEpochMillis, List<PublicQuestion> questions) {}
    public record AnswerBundle(long roundId, Map<Long, String> answers) {}
    public record Grade(long roundId, double score, int correct, int total, String message) {}
    public record HistoryItem(long roundId, String title, String subjectName, double score,
                              int correct, int total, long submittedAtEpochMillis) {}

    public record AssignmentItem(long id, String title, String description,
                                 long createdAtEpochMillis, Long dueAtEpochMillis,
                                 String fileName, long fileSize, long submissionCount,
                                 SubmissionView mySubmission) {}
    public record AssignmentDraft(String title, String description, Long dueAtEpochMillis,
                                  String fileName, byte[] fileData) {}
    public record AssignmentTurnIn(long assignmentId, String content, String fileName,
                                   byte[] fileData, boolean keepExistingFile) {}
    public record SubmissionView(long id, String studentCode, String studentName,
                                 String content, String fileName, long fileSize,
                                 long submittedAtEpochMillis) {}
    public record FileRequest(long assignmentId, boolean submission) {}
    public record FilePayload(long assignmentId, boolean submission, String fileName, byte[] data) {}

    public record OnlineView(String studentCode, String studentName, String ip,
                             String status, String activity) {}
    public record ResultView(long roundId, String examTitle, String studentCode,
                             String studentName, double score, int correct,
                             int total, long submittedAtEpochMillis) {}
    public record LockedView(String studentCode, String studentName, String reason) {}
    public record RoundInfo(long id, long teacherId, String title, String subjectName,
                            long openedAtEpochMillis, long deadlineEpochMillis) {}
}
