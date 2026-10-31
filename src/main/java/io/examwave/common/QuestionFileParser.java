package io.examwave.common;

import io.examwave.common.Domain.QuestionDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuestionFileParser {
    private QuestionFileParser() {}

    public record ParsedQuestionRow(String subjectName, int difficulty, String prompt,
                                    String optionA, String optionB, String optionC,
                                    String optionD, String correctOption) {}

    public static List<ParsedQuestionRow> parse(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Kiểm tra xem là định dạng CSV hay định dạng văn bản dạng đoạn (Câu 1: ... A. ... B. ...)
        if (looksLikeTextQuiz(text)) {
            return parseTextQuiz(text);
        } else {
            return parseCsvQuiz(text);
        }
    }

    private static boolean looksLikeTextQuiz(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("câu 1") || lower.contains("cau 1") || lower.contains("đáp án:") || lower.contains("dap an:") || lower.contains("answer:");
    }

    /**
     * Parse text format:
     * Câu 1: Prompt...
     * A. Option A
     * B. Option B
     * C. Option C
     * D. Option D
     * Đáp án: A
     * Độ khó: 2 (optional)
     */
    private static List<ParsedQuestionRow> parseTextQuiz(String text) {
        List<ParsedQuestionRow> result = new ArrayList<>();
        // Tách các câu hỏi bằng từ khóa "Câu X" hoặc số thứ tự "1." ở đầu dòng
        String[] lines = text.split("\\r?\\n");
        StringBuilder currentPrompt = new StringBuilder();
        String optA = "", optB = "", optC = "", optD = "";
        String answer = "";
        int difficulty = 1;
        boolean inQuestion = false;

        Pattern qPattern = Pattern.compile("^(?:câu\\s*\\d+|cau\\s*\\d+|\\d+)[\\.\\:\\)]\\s*(.*)", Pattern.CASE_INSENSITIVE);
        Pattern optPattern = Pattern.compile("^([A-D])[\\.\\:\\)]\\s*(.*)", Pattern.CASE_INSENSITIVE);
        Pattern ansPattern = Pattern.compile("^(?:đáp\\s*án|dap\\s*an|answer)[\\:\\s]+([A-D])", Pattern.CASE_INSENSITIVE);
        Pattern diffPattern = Pattern.compile("^(?:độ\\s*khó|do\\s*kho|difficulty)[\\:\\s]+([1-6])", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher qm = qPattern.matcher(trimmed);
            if (qm.find()) {
                if (inQuestion && !currentPrompt.toString().isBlank() && !optA.isBlank() && !answer.isBlank()) {
                    result.add(new ParsedQuestionRow(null, difficulty, currentPrompt.toString().trim(),
                            optA.trim(), optB.trim(), optC.trim(), optD.trim(), answer.trim().toUpperCase(Locale.ROOT)));
                }
                inQuestion = true;
                currentPrompt = new StringBuilder(qm.group(1));
                optA = optB = optC = optD = answer = "";
                difficulty = 1;
                continue;
            }

            Matcher am = ansPattern.matcher(trimmed);
            if (am.find()) {
                answer = am.group(1);
                continue;
            }

            Matcher dm = diffPattern.matcher(trimmed);
            if (dm.find()) {
                try { difficulty = Integer.parseInt(dm.group(1)); } catch (Exception ignored) {}
                continue;
            }

            Matcher om = optPattern.matcher(trimmed);
            if (om.find()) {
                String optChar = om.group(1).toUpperCase(Locale.ROOT);
                String optContent = om.group(2);
                switch (optChar) {
                    case "A" -> optA = optContent;
                    case "B" -> optB = optContent;
                    case "C" -> optC = optContent;
                    case "D" -> optD = optContent;
                }
                continue;
            }

            if (inQuestion && optA.isEmpty()) {
                currentPrompt.append("\n").append(trimmed);
            }
        }

        if (inQuestion && !currentPrompt.toString().isBlank() && !optA.isBlank() && !answer.isBlank()) {
            result.add(new ParsedQuestionRow(null, difficulty, currentPrompt.toString().trim(),
                    optA.trim(), optB.trim(), optC.trim(), optD.trim(), answer.trim().toUpperCase(Locale.ROOT)));
        }

        return result;
    }

    /**
     * Parse CSV format:
     * Cột 8: Môn, Độ khó, Câu hỏi, A, B, C, D, Đáp án
     * Cột 7: Độ khó, Câu hỏi, A, B, C, D, Đáp án (môn theo ngữ cảnh)
     * Cột 6: Câu hỏi, A, B, C, D, Đáp án (độ khó mặc định 1, môn theo ngữ cảnh)
     */
    private static List<ParsedQuestionRow> parseCsvQuiz(String text) {
        List<ParsedQuestionRow> result = new ArrayList<>();
        List<List<String>> rows = CsvHelper.parseCsv(text);

        for (List<String> row : rows) {
            if (row.isEmpty()) continue;
            String firstCell = row.get(0).toLowerCase(Locale.ROOT);
            if (firstCell.contains("môn") || firstCell.contains("câu hỏi") || firstCell.contains("prompt") || firstCell.contains("độ khó")) {
                continue; // bỏ qua header
            }

            String subjectName = null;
            int difficulty = 1;
            String prompt, a, b, c, d, ans;

            if (row.size() >= 8) {
                subjectName = row.get(0);
                difficulty = parseDifficulty(row.get(1));
                prompt = row.get(2);
                a = row.get(3); b = row.get(4); c = row.get(5); d = row.get(6);
                ans = row.get(7);
            } else if (row.size() >= 7) {
                difficulty = parseDifficulty(row.get(0));
                prompt = row.get(1);
                a = row.get(2); b = row.get(3); c = row.get(4); d = row.get(5);
                ans = row.get(6);
            } else if (row.size() >= 6) {
                prompt = row.get(0);
                a = row.get(1); b = row.get(2); c = row.get(3); d = row.get(4);
                ans = row.get(5);
            } else {
                continue;
            }

            if (!prompt.isBlank() && !a.isBlank() && !b.isBlank() && !ans.isBlank()) {
                String cleanAns = cleanAnswer(ans);
                if (cleanAns != null) {
                    result.add(new ParsedQuestionRow(subjectName, difficulty, prompt.trim(),
                            a.trim(), b.trim(), c.trim(), d.trim(), cleanAns));
                }
            }
        }
        return result;
    }

    private static int parseDifficulty(String str) {
        try {
            int d = Integer.parseInt(str.trim());
            return Math.max(1, Math.min(6, d));
        } catch (Exception e) {
            return 1;
        }
    }

    private static String cleanAnswer(String ans) {
        if (ans == null) return null;
        String upper = ans.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("A") || upper.equals("1")) return "A";
        if (upper.startsWith("B") || upper.equals("2")) return "B";
        if (upper.startsWith("C") || upper.equals("3")) return "C";
        if (upper.startsWith("D") || upper.equals("4")) return "D";
        return null;
    }
}
