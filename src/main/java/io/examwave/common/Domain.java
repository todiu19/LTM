package io.examwave.common;

import java.util.List;
import java.util.Map;

public final class Domain {
    private Domain() {}

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 20_000;

    public record Teacher(long id, String username, String fullName) {}
    public record Student(String code, String name, String className) {
        public Student(String code, String name) { this(code, name, "Chưa phân lớp"); }
    }
    public record LoginRequest(String studentCode, String birthDate) {}
    public record LoginReply(boolean success, String message, String studentName) {}

    public record StudentClassItem(long id, String name, long studentCount) {}
    public record StudentView(String code, String fullName, String birthDate, String className,
                              boolean locked, String lockReason) {}
    public record StudentImportSummary(int totalProcessed, int inserted, int updated, List<String> errors) {}

    public record SubjectItem(long id, String name, long questionCount) {}
    public record BankQuestion(long id, long subjectId, String subjectName, int difficulty,
                               String prompt, String optionA, String optionB,
                               String optionC, String optionD, String correctOption) {}
    public record QuestionDraft(long subjectId, int difficulty, String prompt,
                                String optionA, String optionB, String optionC,
                                String optionD, String correctOption) {}
    public record QuestionImportSummary(int totalProcessed, int inserted, List<String> errors) {}

    public record TemplateItem(long id, String title, String subjectName,
                               int durationMinutes, int questionCount) {}
    public record TemplateDraft(String title, long subjectId, int durationMinutes,
                                List<Long> questionIds) {}
    public record MatrixExamDraft(String title, long subjectId, int easyCount,
                                  int mediumCount, int hardCount, int durationMinutes) {}

    public record ExamOffer(long roundId, String title, String subjectName, String targetClass,
                            Integer maxParticipants, long deadlineEpochMillis, boolean rejoin) {
        public ExamOffer(long roundId, String title, String subjectName, long deadlineEpochMillis, boolean rejoin) {
            this(roundId, title, subjectName, null, null, deadlineEpochMillis, rejoin);
        }
    }
    public record PublicQuestion(long id, String prompt, List<String> options) {}
    public record ExamPaper(long roundId, String title, String subjectName,
                            long deadlineEpochMillis, List<PublicQuestion> questions) {}
    public record AnswerBundle(long roundId, Map<Long, String> answers) {}
    public record Grade(long roundId, double score, int correct, int total, String message) {}
    public record HistoryItem(long roundId, String title, String subjectName, double score,
                              int correct, int total, long submittedAtEpochMillis) {}

    public record RoundSummary(long id, String title, String subjectName, String targetClass,
                               Integer maxParticipants, long participantCount,
                               long openedAtEpochMillis, long deadlineEpochMillis, boolean active) {}
    public record OpenRoundDraft(long templateId, String title, String targetClass,
                                 Integer maxParticipants, int durationMinutes) {}
    public record SplitSessionDraft(long templateId, String targetClass,
                                    int studentsPerRound, int durationMinutes) {}

    // Chi tiết bài thi từng câu
    public record QuestionDetailView(int index, String prompt, String optionA, String optionB,
                                     String optionC, String optionD, String selectedOption,
                                     String correctOption, boolean isCorrect) {}
    public record AttemptDetailView(long attemptId, long roundId, String examTitle, String studentCode,
                                    String studentName, String className, double score,
                                    int correct, int total, int violations,
                                    long submittedAtEpochMillis, List<QuestionDetailView> questions) {}

    // Thống kê & Phổ điểm
    public record ExamStatistics(int totalSubmissions, double maxScore, double minScore, double averageScore,
                                 int excellentCount, int goodCount, int averageCount, int failCount,
                                 Map<String, Integer> scoreRanges) {}

    // Chống gian lận & Giám sát
    public record ViolationReport(long roundId, String reason, int count) {}
    public record BroadcastMessage(String sender, String content, long timestampEpochMillis) {}

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
                             String status, String activity, int violations) {
        public OnlineView(String studentCode, String studentName, String ip, String status, String activity) {
            this(studentCode, studentName, ip, status, activity, 0);
        }
    }

    public record ResultView(long roundId, String examTitle, String studentCode,
                             String studentName, String className, double score, int correct,
                             int total, int violations, long submittedAtEpochMillis) {
        public ResultView(long roundId, String examTitle, String studentCode,
                          String studentName, String className, double score, int correct,
                          int total, long submittedAtEpochMillis) {
            this(roundId, examTitle, studentCode, studentName, className, score, correct, total, 0, submittedAtEpochMillis);
        }
        public ResultView(long roundId, String examTitle, String studentCode,
                          String studentName, double score, int correct,
                          int total, long submittedAtEpochMillis) {
            this(roundId, examTitle, studentCode, studentName, "Chưa phân lớp", score, correct, total, 0, submittedAtEpochMillis);
        }
    }

    public record LockedView(String studentCode, String studentName, String reason) {}
    public record RoundInfo(long id, long teacherId, String title, String subjectName,
                            String targetClass, Integer maxParticipants,
                            long openedAtEpochMillis, long deadlineEpochMillis) {
        public RoundInfo(long id, long teacherId, String title, String subjectName,
                         long openedAtEpochMillis, long deadlineEpochMillis) {
            this(id, teacherId, title, subjectName, null, null, openedAtEpochMillis, deadlineEpochMillis);
        }
    }
}
