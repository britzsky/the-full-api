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

    // ✅ 연도가 오늘 기준 허용 범위를 벗어나면 파싱 실패 처리 → 호출부의 사용자 입력 날짜(cell_date 등) fallback 유도
    //    2자리 연도 포맷("uu-MM-dd" 등)은 특정 세기로 고정 매핑되기 때문에, 라벨 없이 매칭된 엉뚱한 두 자리 숫자가
    //    오래된 과거 연도로 오인식되는 것 방지
    private static final int PAST_YEAR_TOLERANCE = 5; // 오늘 기준 과거 허용 연차
    private static final int FUTURE_YEAR_TOLERANCE = 1; // 오늘 기준 미래 허용 연차

    public static LocalDate parseFlexibleDate(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("날짜 문자열이 비어 있습니다.");
        }

        String value = text.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate parsed = LocalDate.parse(value, formatter);
                if (!isPlausibleYear(parsed.getYear())) {
                    // 연도 범위 벗어남 → 이 포맷은 버리고 다음 포맷 계속 시도
                    continue;
                }
                return parsed;
            } catch (DateTimeParseException ignored) {
                // 실패하면 다음 포맷 시도
            }
        }

        // 모든 포맷 실패 또는 연도 범위 벗어남 → 파싱 실패 처리
        throw new DateTimeParseException("지원하지 않거나 비정상적인 날짜 형식입니다: " + value, value, 0);
    }

    private static boolean isPlausibleYear(int year) {
        int currentYear = LocalDate.now().getYear();
        return year >= currentYear - PAST_YEAR_TOLERANCE && year <= currentYear + FUTURE_YEAR_TOLERANCE;
    }
}
