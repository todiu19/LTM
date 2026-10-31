package io.examwave.common;

import io.examwave.common.Domain.ResultView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public final class CsvHelper {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    private CsvHelper() {}

    /**
     * Parse CSV text respecting quoted cells, quotes escaping (""), and multiple delimiters (, ; \t).
     */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isBlank()) return rows;

        char delimiter = detectDelimiter(text);
        List<String> currentRow = new ArrayList<>();
        StringBuilder currentCell = new StringBuilder();
        boolean insideQuotes = false;
        int length = text.length();

        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);

            // Skip BOM if present at the very beginning
            if (i == 0 && ch == '\uFEFF') continue;

            if (ch == '"') {
                if (insideQuotes && i + 1 < length && text.charAt(i + 1) == '"') {
                    currentCell.append('"');
                    i++; // skip escaped quote
                } else {
                    insideQuotes = !insideQuotes;
                }
            } else if (ch == delimiter && !insideQuotes) {
                currentRow.add(currentCell.toString().trim());
                currentCell.setLength(0);
            } else if ((ch == '\r' || ch == '\n') && !insideQuotes) {
                if (ch == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
                    i++;
                }
                currentRow.add(currentCell.toString().trim());
                currentCell.setLength(0);
                if (!currentRow.stream().allMatch(String::isBlank)) {
                    rows.add(currentRow);
                }
                currentRow = new ArrayList<>();
            } else {
                currentCell.append(ch);
            }
        }

        if (currentCell.length() > 0 || !currentRow.isEmpty()) {
            currentRow.add(currentCell.toString().trim());
            if (!currentRow.stream().allMatch(String::isBlank)) {
                rows.add(currentRow);
            }
        }

        return rows;
    }

    private static char detectDelimiter(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        int commaCount = 0;
        int semicolonCount = 0;
        int tabCount = 0;
        boolean inQuote = false;
        for (char ch : firstLine.toCharArray()) {
            if (ch == '"') inQuote = !inQuote;
            else if (!inQuote) {
                if (ch == ',') commaCount++;
                else if (ch == ';') semicolonCount++;
                else if (ch == '\t') tabCount++;
            }
        }
        if (semicolonCount > commaCount && semicolonCount > tabCount) return ';';
        if (tabCount > commaCount && tabCount > semicolonCount) return '\t';
        return ',';
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Ngày sinh không được để trống.");
        String clean = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        throw new IllegalArgumentException("Định dạng ngày sinh không hợp lệ: " + raw + " (Cần: dd/MM/yyyy hoặc yyyy-MM-dd)");
    }

    public static String escape(Object value) {
        if (value == null) return "\"\"";
        String str = value.toString();
        return "\"" + str.replace("\"", "\"\"") + "\"";
    }

    /**
     * Generates CSV for exam results with UTF-8 BOM so Excel opens it with proper Vietnamese characters.
     */
    public static String exportResultsCsv(List<ResultView> results) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility
        sb.append('\uFEFF');

        // Header
        sb.append(String.join(",",
                escape("STT"),
                escape("Mã sinh viên"),
                escape("Họ và tên"),
                escape("Lớp"),
                escape("Ca thi"),
                escape("Điểm"),
                escape("Số câu đúng"),
                escape("Tổng số câu"),
                escape("Thời gian nộp bài")
        )).append("\r\n");

        int stt = 1;
        for (ResultView r : results) {
            String submitTime = TIME_FORMAT.format(Instant.ofEpochMilli(r.submittedAtEpochMillis()).atZone(ZoneId.systemDefault()));
            sb.append(String.join(",",
                    escape(stt++),
                    escape(r.studentCode()),
                    escape(r.studentName()),
                    escape(r.className()),
                    escape(r.examTitle()),
                    escape(String.format("%.2f", r.score())),
                    escape(r.correct()),
                    escape(r.total()),
                    escape(submitTime)
            )).append("\r\n");
        }

        return sb.toString();
    }
}

