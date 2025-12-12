package com.example.demo.utils;

public class BizNoUtils {

    /**
     * 사업자번호를 정규화 + 검증 후 XXX-XX-XXXXX 형식으로 리턴
     * 유효하지 않으면 IllegalArgumentException 발생
     */
    public static String normalizeBizNo(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("사업자번호가 null 입니다.");
        }

        // 1) 숫자만 남기기 (하이픈/공백/텍스트 모두 제거)
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 10) {
            throw new IllegalArgumentException("사업자번호는 숫자 10자리여야 합니다. 입력값=" + raw);
        }

        // 2) 유효성 체크 (체크섬)
        if (!isValidBizNo(digits)) {
            throw new IllegalArgumentException("유효하지 않은 사업자번호입니다. 입력값=" + raw);
        }

        // 3) 형식 변환: XXX-XX-XXXXX
        return String.format(
            "%s-%s-%s",
            digits.substring(0, 3),
            digits.substring(3, 5),
            digits.substring(5)
        );
    }

    /**
     * 사업자번호 유효성 체크 (숫자/하이픈/공백 섞여 있어도 처리)
     */
    public static boolean isValidBizNo(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        String digits = raw.replaceAll("\\D", ""); // 숫자만
        if (!digits.matches("\\d{10}")) {          // 정확히 10자리
            return false;
        }

        // 체크섬 알고리즘
        int[] weights = {1, 3, 7, 1, 3, 7, 1, 3, 5};
        int sum = 0;

        // 앞 9자리 * 가중치
        for (int i = 0; i < 9; i++) {
            int d = digits.charAt(i) - '0';
            sum += d * weights[i];
        }

        // 9번째 자리의 특수 처리: (d9 * 5) / 10 의 몫을 추가
        int d9 = digits.charAt(8) - '0';
        sum += (d9 * 5) / 10;

        int checkDigit = (10 - (sum % 10)) % 10;      // 계산된 마지막 자리
        int last = digits.charAt(9) - '0';            // 실제 마지막 자리

        return checkDigit == last;
    }
}