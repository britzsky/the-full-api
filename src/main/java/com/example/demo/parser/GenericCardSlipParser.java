package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericCardSlipParser extends BaseReceiptParser {

    // 사업자번호(예: 122-30-69169)
    private static final Pattern BIZ_NO = Pattern.compile("\\b\\d{3}[- ]?\\d{2}[- ]?\\d{5}\\b");
    // 전화번호
    private static final Pattern TEL = Pattern.compile("\\b0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4}\\b");
    // 승인일시
    private static final Pattern DT = Pattern.compile("\\b(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)\\b");

    // 마스킹 카드번호(끝이 199* 처럼 *로 끝나는 것도 허용)
    private static final Pattern MASKED_PAN_1 =
            Pattern.compile("\\b\\d{4}[-\\s]?\\d{3,4}[-\\s]?[*Xx]{2,}[-\\s]?[0-9*Xx]{3,4}\\b");
    private static final Pattern MASKED_PAN_2 =
            Pattern.compile("\\b\\d{4}[*Xx]{2,10}\\d{3,4}\\b");

    // "원"이 붙은 금액만!
    private static final Pattern MONEY_WON =
            Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{4,})\\s*원");

    @Override
    public ReceiptResult parse(Document doc) {
        ReceiptResult r = new ReceiptResult();

        String raw = text(doc);
        String text = normalize(raw);

        System.out.println("=== 🧾 NORMALIZED LINES (카드전표) ===");
        Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(System.out::println);

        // 1) 일시(날짜/시간)
        extractDateTime(text, r);

        // 2) 승인번호
        r.approval.approvalNo = firstNonNull(
                extract(text, "(승인번호)\\s*[:：-]?\\s*([0-9]{5,12})", 2),
                extract(text, "(승\\s*인\\s*번\\s*호)\\s*[:：-]?\\s*([0-9]{5,12})", 2)
        );

        // 3) 카드번호(마스킹만 인정)  ✅ 숫자 이어붙이기 금지
        String maskedPan = extractMaskedPanLoose(text);
        r.payment.cardNo = maskedPan;
        r.payment.cardMasked = maskedPan;

        // 4) 할부
        r.payment.installment = firstNonNull(
                extract(text, "(일시불)", 1),
                extract(text, "(할부)\\s*([0-9]{1,2})\\s*개월", 2),
                extract(text, "(할부)\\s*[:：-]?\\s*([0-9]{1,2})", 2)
        );

        // 5) 결제수단/카드사(대충)
        r.payment.type = firstNonNull(
                extract(text, "(신용카드|체크카드|간편결제|삼성페이|네이버페이|카카오페이|토스페이|애플페이|구글페이)", 1),
                "신용카드"
        );

        // ✅ IBK/비씨카드 등 추가
        r.payment.cardBrand = firstNonNull(
                extract(text, "(IBK|국민|KB|신한|삼성|현대|롯데|하나|NH|농협|BC|비씨|우리)\\s*(카드)?", 1),
                extract(text, "(VISA|MASTER|MASTERCARD|AMEX|JCB)", 1)
        );

        // 6) 총액/부가세/공급가액/할인 후보 랭킹  ✅ '원' 기반으로만 추출 + OCR 띄어쓰기 대응
        extractTotalsByRanking(text, r);

        // 7) 상호/사업자/전화/주소
        extractMerchantInfo(text, r);

        // 8) VAN/TID/가맹점번호 등
        r.approval.merchantNo = firstNonNull(
                extract(text, "(가맹점번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2),
                extract(text, "(가맹점\\s*번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2),
                extract(text, "(가맹점NO|가맹점\\s*NO)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2)
        );
        r.approval.tid = firstNonNull(
                extract(text, "(TID)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2),
                extract(text, "(단말기번호|단말기\\s*번호)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{4,})", 2)
        );
        r.approval.van = firstNonNull(
                extract(text, "(VAN)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{2,})", 2),
                extract(text, "(밴사|밴)\\s*[:：-]?\\s*([0-9A-Za-z\\-]{2,})", 2),
                extract(text, "(KICC|KSNET|NICE|SMARTRO|KIS|KOVAN)", 1)
        );

        if (r.totals.total != null) r.payment.approvalAmt = String.valueOf(r.totals.total);

        System.out.println("=== ✅ PARSED CARD SLIP ===");
        System.out.println("📌 거래일자: " + r.meta.saleDate + " " + r.meta.saleTime);
        System.out.println("📌 가맹점: " + r.merchant.name + " / 사업자번호: " + r.merchant.bizNo);
        System.out.println("📌 카드: " + r.payment.cardBrand + " / " + r.payment.cardNo);
        System.out.println("📌 승인번호: " + r.approval.approvalNo + " / 합계: " + r.totals.total);

        return r;
    }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw
                .replace("\u00A0", " ")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?<=\\d)\\.(?=\\d{3}\\b)", ",")
                .replaceAll(" +", " ")
                .trim();
    }

    private void extractDateTime(String text, ReceiptResult r) {
        // 날짜/시간(전표형)
        Matcher m = DT.matcher(text);
        if (m.find()) {
            String yyyy = m.group(1);
            String mm = pad2(m.group(2));
            String dd = pad2(m.group(3));
            String time = m.group(4);

            r.meta.saleDate = yyyy + "-" + mm + "-" + dd;
            r.meta.saleTime = time;
            r.approval.authDateTime = r.meta.saleDate + " " + r.meta.saleTime;
            return;
        }

        // fallback
        r.meta.saleDate = firstNonNull(
                extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1),
                extract(text, "(20\\d{2}년\\s*\\d{1,2}월\\s*\\d{1,2}일)", 1)
        );
        r.meta.saleTime = firstNonNull(extract(text, "([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 1));
        r.approval.authDateTime = firstNonNull(
                extract(text, "(승인일시)\\s*[:：-]?\\s*(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}\\s*[0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 2),
                (r.meta.saleDate != null && r.meta.saleTime != null) ? (r.meta.saleDate + " " + r.meta.saleTime) : null
        );
    }

    private static String pad2(String s) {
        if (s == null) return null;
        s = s.trim();
        return (s.length() == 1) ? "0" + s : s;
    }

    private void extractTotalsByRanking(String text, ReceiptResult r) {
        // ✅ 사업자번호/전화번호 제거 (69169 같은 오탐 금액 방지)
        String cleaned = BIZ_NO.matcher(text).replaceAll(" ");
        cleaned = TEL.matcher(cleaned).replaceAll(" ");

        // ✅ OCR에서 "합  계"처럼 띄어쓰기 들어가도 분리되게
        String t = cleaned
                .replaceAll("(?=합\\s*계|총\\s*액|결\\s*제\\s*금\\s*액|승\\s*인\\s*금\\s*액|공\\s*급\\s*가\\s*액|부\\s*가\\s*세|할\\s*인|면\\s*세|과\\s*세)", "\n")
                .replaceAll("\\n+", "\n");

        String[] lines = t.split("\\n");

        List<Cand> totalCands = new ArrayList<>();
        List<Cand> vatCands = new ArrayList<>();
        List<Cand> supplyCands = new ArrayList<>();
        List<Cand> discCands = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // ✅ 금액 후보는 "원"이 있는 것만!
            List<Integer> monies = extractAllMoneyWonOnly(line);
            if (monies.isEmpty()) continue;

            String key = stripAllSpaces(line); // OCR 띄어쓰기 대응용

            for (Integer money : monies) {
                int base = 0;

                // ✅ 키워드는 공백 제거 버전으로 판단
                if (containsAny(key, "합계", "총액", "결제금액", "승인금액", "총결제")) base += 10;
                if (containsAny(key, "부가세", "VAT")) base += 7;
                if (containsAny(key, "공급가액")) base += 7;
                if (containsAny(key, "할인", "DC", "쿠폰")) base += 6;
                if (containsAny(key, "면세", "과세")) base += 2;

                if (i > lines.length * 0.75) base += 1;

                if (containsAny(key, "부가세", "VAT")) vatCands.add(new Cand(money, base + 2, line));
                else if (containsAny(key, "공급가액")) supplyCands.add(new Cand(money, base + 2, line));
                else if (containsAny(key, "할인", "DC", "쿠폰")) discCands.add(new Cand(money, base + 1, line));
                else totalCands.add(new Cand(money, base, line));
            }
        }

        r.totals.total = pickBest(totalCands);
        r.totals.vat = pickBest(vatCands);
        r.totals.taxable = pickBest(supplyCands);
        r.totals.discount = pickBest(discCands);

        if (r.totals.total != null && r.totals.taxable != null && r.totals.vat != null) {
            int sum = r.totals.taxable + r.totals.vat;
            int tol = Math.max(10, (int) (r.totals.total * 0.02));
            if (Math.abs(sum - r.totals.total) > tol) {
                Integer better = pickBest(filterByKeyword(totalCands, "결제금액", "승인금액"));
                if (better != null) r.totals.total = better;
            }
        }

        r.totals.taxFree = firstInt(text, "(면세)[^0-9]*([0-9,]+)");
    }

    private String stripAllSpaces(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "");
    }

    private List<Cand> filterByKeyword(List<Cand> cands, String... keys) {
        List<Cand> out = new ArrayList<>();
        for (Cand c : cands) {
            String ctx = (c.context == null) ? "" : stripAllSpaces(c.context);
            for (String k : keys) {
                if (ctx.contains(stripAllSpaces(k))) {
                    out.add(new Cand(c.amount, c.score + 3, c.context));
                    break;
                }
            }
        }
        return out;
    }

    private void extractMerchantInfo(String text, ReceiptResult r) {
        r.merchant.bizNo = firstNonNull(
                extract(text, "\\b(\\d{3}[- ]?\\d{2}[- ]?\\d{5})\\b", 1)
        );

        r.merchant.tel = firstNonNull(
                extract(text, "(0\\d{1,2}[- ]?\\d{3,4}[- ]?\\d{4})", 1)
        );

        r.merchant.address = firstNonNull(
                extract(text, "([가-힣]+시\\s*[가-힣]+(구|군)\\s*[가-힣0-9\\s\\-]+\\d+번?[^\\n]*)", 1),
                extract(text, "([가-힣]+도\\s*[가-힣]+시\\s*[가-힣]+(구|군)\\s*[가-힣0-9\\s\\-]+)", 1),
                extract(text, "([가-힣0-9\\s\\-]+(로|길|번길)\\s*\\d+[^\\n]*)", 1)
        );

        // ✅ 1) 라벨 기반 상호 추출을 "안전하게" 변경
        // - 상호, 가맹점명: 콜론 없어도 되는 경우가 있어 기존처럼 허용
        // - 가맹점: 반드시 ':' 같은 구분자가 있을 때만 허용 (가맹점NO 오탐 방지)
        String name1 = firstNonNull(
                extract(text, "(상호|가맹점명)\\s*[:：-]?\\s*([가-힣A-Za-z0-9()\\- ]{2,30})", 2),
                extract(text, "(가맹점)\\s*[:：-]\\s*([가-힣A-Za-z0-9()\\- ]{2,30})", 2) // ✅ 콜론/대시 필수
        );

        name1 = cleanMerchantName(name1);

        // ✅ 2) name1이 전표영역(승인/매입/NO 등)이면 버림 → name2/name3로 넘어가게
        if (name1 != null) {
            String chk = stripAllSpaces(name1);
            if (containsAny(chk, "승인", "승인번호", "매입", "가맹점NO", "가맹점번호", "NO", "NOCVM", "NO-CVM")) {
                name1 = null;
            }
            // 주소도 상호로 들어오면 버림
            if (name1 != null && isLikelyAddressLine(name1)) {
                name1 = null;
            }
        }

        // ✅ 3) 사업자번호 근처에서 “위쪽 우선 + 주소 제외”로 상호 찾기
        String name2 = null;
        if (r.merchant.bizNo != null) name2 = merchantNearBizNoPreferUp(text, r.merchant.bizNo);
        name2 = cleanMerchantName(name2);

        // ✅ 4) 상단 추정
        String name3 = guessTopName(text);
        name3 = cleanMerchantName(name3);

        r.merchant.name = firstNonNull(name1, name2, name3);
    }

    /**
     * 사업자번호 주변에서 상호를 찾되,
     * - 사업자번호 "위쪽" 라인에 가중치
     * - 주소처럼 보이는 라인은 강하게 제외
     */
    private String merchantNearBizNoPreferUp(String text, String bizNoRaw) {
        String bizNoDigits = bizNoRaw.replaceAll("[^0-9]", "");
        String[] lines = text.split("\\r?\\n");

        int idx = -1;
        for (int i = 0; i < lines.length; i++) {
            String onlyNum = lines[i].replaceAll("[^0-9]", "");
            if (onlyNum.contains(bizNoDigits)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return null;

        String best = null;
        int bestScore = Integer.MIN_VALUE;

        int from = Math.max(0, idx - 8);
        int to = Math.min(lines.length - 1, idx + 2);

        for (int i = from; i <= to; i++) {
            String cand = lines[i].trim();
            if (cand.isEmpty()) continue;

            int score = 0;

            if (i < idx) score += 3;   // 위쪽 우선
            else score -= 1;

            if (looksLikeMerchantName(cand)) score += 6;
            else score -= 4;

            if (isLikelyAddressLine(cand)) score -= 10;

            int digits = countDigits(cand);
            if (digits == 0) score += 2;
            if (digits >= 3) score -= 2;

            if (cand.length() > 20) score -= 1;

            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }

        return bestScore >= 4 ? best : null;
    }

    private String guessTopName(String text) {
        String[] lines = text.split("\\r?\\n");
        String best = null;
        int bestScore = -999;

        for (int i = 0; i < Math.min(7, lines.length); i++) {
            String cand = lines[i].trim();
            if (cand.isEmpty()) continue;

            int s = 0;

            // ✅ 주소면 바로 탈락급
            if (isLikelyAddressLine(cand)) s -= 10;

            if (looksLikeMerchantName(cand)) s += 3;
            if (cand.length() >= 4) s += 1;
            if (cand.contains("㈜") || cand.contains("(주)") || cand.contains("주식회사")) s += 1;

            if (s > bestScore) {
                bestScore = s;
                best = cand;
            }
        }

        return bestScore >= 3 ? best : null;
    }

    private boolean looksLikeMerchantName(String s) {
        if (s == null) return false;
        s = s.trim();
        if (s.length() < 2) return false;

        // ✅ 주소 라인 제외(구매처가 주소로 찍히는 문제 해결 핵심)
        if (isLikelyAddressLine(s)) return false;

        // ✅ 카드전표 영역 문구 제외(너 전표에서 use_name 오염 방지)
        if (containsAny(stripAllSpaces(s), "승인", "카드", "일시불", "할부", "매입", "단말기", "고객용",
                "가맹점번호", "가맹점NO", "부가세", "합계", "결제금액", "NO-CVM", "NOCVM"))
            return false;

        int digit = 0, letter = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) digit++;
            if (Character.isLetter(c) || (c >= '가' && c <= '힣')) letter++;
        }
        if (letter < 2) return false;

        // 숫자 비중이 크면 주소/번호 가능성↑
        return digit <= s.length() / 3;
    }

    private boolean isLikelyAddressLine(String s) {
        if (s == null) return false;
        String x = stripAllSpaces(s);

        boolean hasRegion = x.matches(".*[가-힣]+(시|도).*[가-힣]+(구|군).*");
        boolean hasRoad = x.matches(".*(로|길|번길).*\\d+.*");
        boolean hasDongHo = x.matches(".*(동|읍|면|리|호|층).*\\d*.*");
        boolean hasAddrWords = containsAny(x, "번길", "로", "길", "아파트", "빌라", "상가", "지하", "층", "호");

        int digits = countDigits(x);

        return hasRegion || hasRoad || (hasDongHo && digits >= 2) || (hasAddrWords && digits >= 1);
    }

    private int countDigits(String s) {
        int c = 0;
        for (char ch : s.toCharArray()) if (Character.isDigit(ch)) c++;
        return c;
    }

    private String cleanMerchantName(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = s.replaceAll("(고객용|승인|카드|영수증|매출전표)$", "").trim();
        if (s.length() < 2) return null;
        return s;
    }

    private static class Cand {
        final Integer amount;
        final int score;
        final String context;
        Cand(Integer amount, int score, String context) {
            this.amount = amount; this.score = score; this.context = context;
        }
    }

    private Integer pickBest(List<Cand> cands) {
        if (cands == null || cands.isEmpty()) return null;
        cands.sort((a, b) -> {
            int s = Integer.compare(b.score, a.score);
            if (s != 0) return s;
            return Integer.compare(b.amount, a.amount);
        });
        return cands.get(0).amount;
    }

    // ✅ "원"이 붙은 금액만 추출
    private List<Integer> extractAllMoneyWonOnly(String line) {
        List<Integer> out = new ArrayList<>();
        if (line == null) return out;

        Matcher m = MONEY_WON.matcher(line);
        while (m.find()) {
            Integer v = toInt(m.group(1));
            if (v == null || v <= 0) continue;
            out.add(v);
        }
        return out;
    }

    // ✅ 마스킹 카드번호만 반환 (못 찾으면 null)
    private String extractMaskedPanLoose(String text) {
        if (text == null) return null;

        Matcher m1 = MASKED_PAN_1.matcher(text);
        if (m1.find()) return m1.group().replaceAll("\\s+", "");

        Matcher m2 = MASKED_PAN_2.matcher(text);
        if (m2.find()) return m2.group();

        return null;
    }
}
