package com.example.demo.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class DateUtils {

    // 시도해볼 날짜 포맷들
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd"), // 2025-10-16
            DateTimeFormatter.ofPattern("uuuu/MM/dd"), // 2025/10/16
            DateTimeFormatter.ofPattern("uuuu.MM.dd"), // 2025.10.16
            DateTimeFormatter.ofPattern("uuuu.MM.dd."), // 2025.10.16.
            DateTimeFormatter.ofPattern("uu-MM-dd"), // 25-10-16
            DateTimeFormatter.ofPattern("uu/MM/dd"), // 25/10/16 (혹시 모를 케이스)
            DateTimeFormatter.ofPattern("uu.MM.dd"), // 25.10.16
            DateTimeFormatter.ofPattern("uu.MM.dd.") // 25.10.16.

    );

    public static LocalDate parseFlexibleDate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("날짜 문자열이 비어 있습니다.");
        }

        String value = text.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 실패하면 다음 포맷 시도
            }
        }

        // 모든 포맷이 실패하면 예외 던지기
        throw new DateTimeParseException("지원하지 않는 날짜 형식입니다: " + value, value, 0);
    }
}
