package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOfficeNaverReceiptParser v13.1
 * - Homeplus(홈플러스) 신용카드매출전표 + CoupangApp + Generic Card + ✅ Naver "카드 영수증" 지원
 * - 라벨 기반(DOTALL) 우선 + fallback
 * - 디버그 로그 극대화
 * - ✅ Naver 금액 파싱 candidates=[] 이슈 수정: "라인 단독 숫자" -> "어디든 숫자"로 추출
 * - ✅ Naver 상호명 폭주 방지 + (주)크로바케미칼 같은 케이스 정확히 잡기
 */
public class HeadOfficeNaverReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    @Override
    public ReceiptResult parse(Document doc) {

        // ✅ 줄바꿈 유지 정규화 (라벨 파싱 안정화)
        String rawText = normalizeTextKeepNewlines(text(doc));

        System.out.println("=================================");
        System.out.println("=== 🧾 RAW TEXT (HomePlus/Generic) ===");
        System.out.println(rawText);
        System.out.println("=================================");

        boolean isHomeplus = isHomeplusSlip(rawText);
        boolean isApp = isCoupangAppReceipt(rawText);
        boolean isNaverCard = isNaverCardReceipt(rawText); // ✅ 추가

        System.out.println("🧭 인식된 유형:");
        System.out.println("  - HomeplusSlip? " + isHomeplus);
        System.out.println("  - CoupangApp?   " + isApp);
        System.out.println("  - NaverCard?    " + isNaverCard);

        ReceiptResult r;
        if (isHomeplus) {
            r = parseHomeplusSlip(rawText);
        } else if (isApp) {
            r = parseAppVersion(rawText);
        } else if (isNaverCard) {
            r = parseNaverCardReceipt(rawText); // ✅ 추가
        } else {
            r = parseCardVersion(rawText);
        }

        // ✅ 최종 결과 상세 출력
        printFullResult(r);

        return r;
    }

    /* ========================= ✅ 네이버 "카드 영수증" 감지 ========================= */

    private boolean isNaverCardReceipt(String text) {
        if (text == null)
            return false;

        boolean hasTitle = text.contains("카드 영수증")
                || (text.contains("카드사/승인번호") && text.contains("결제일자"));
        boolean hasPayInfo = text.contains("카드사/승인번호")
                || text.contains("카드번호")
                || text.contains("거래종류/할부");
        boolean hasProduct = text.contains("상품명")
                && (text.contains("상품 주문번호") || text.contains("상품주문번호"));
        boolean hasSeller = text.contains("판매자 정보") || text.contains("판매자정보") || text.contains("판매자상호");
        boolean hasFranchise = text.contains("가맹점 정보") || text.contains("가맹점정보") || text.contains("가맹점명");
        // 취소 영수증은 승인금액+취소금액, 일반 영수증은 승인금액과 공급가액/부가세액 조합으로 판별
        boolean hasAmounts = text.contains("금액") &&
                (text.contains("승인금액") || text.contains("공급가액") || text.contains("부가세액")
                        || text.contains("취소금액")) &&
                text.contains("합계");

        return (hasTitle || (hasPayInfo && hasProduct)) && hasSeller && hasFranchise && hasAmounts;
    }

    /* ========================= ✅ 네이버 "카드 영수증" 파싱 ========================= */

    private ReceiptResult parseNaverCardReceipt(String text) {
        System.out.println("[NAVER] ---- parseNaverCardReceipt 시작 ----");

        // 라인 목록 출력 (쿠팡 파서 스타일)
        String[] allLines = text.replace("\r", "\n").split("\n");
        for (int i = 0; i < allLines.length; i++) {
            System.out.printf("[NAVER] L%02d: %s%n", i, allLines[i]);
        }

        ReceiptResult r = new ReceiptResult();

        // ---- 결제정보 ----
        System.out.println("[NAVER] ---- 결제정보 파싱 ----");

        // 카드사/승인번호: "비씨/32009723" 형태
        Matcher cm = Pattern.compile("([가-힣A-Za-z]+)\\s*/\\s*([0-9]{6,12})").matcher(text);
        if (cm.find()) {
            r.payment.cardBrand = normalizeCardBrand(cleanField(cm.group(1)));
            r.approval.approvalNo = cleanField(cm.group(2));
        }
        System.out.println(
                "[NAVER] cardBrand=" + safe(r.payment.cardBrand) + ", approvalNo=" + safe(r.approval.approvalNo));

        // 카드번호: "5130-****-****-8923(**/**)" 형태
        String cardNo = firstNonNull(
                extractNaverCardNo(text),
                extract(text, "([0-9]{4}[-_][0-9*]{4}[-_][0-9*]{4}[-_][0-9*]{4})"));
        r.payment.cardMasked = normalizeCardMasked(cardNo);
        System.out.println("[NAVER] cardMasked=" + safe(r.payment.cardMasked));

        // 거래종류/할부
        Matcher tradeMatcher = Pattern.compile("(신용|체크|직불)\\s*[（(]?(법인|개인)?[）)]?\\s*/\\s*(일시불|[0-9]+개월)")
                .matcher(text);
        String tradeType = tradeMatcher.find() ? tradeMatcher.group().trim() : null;
        if (notEmpty(tradeType)) {
            r.payment.type = tradeType;
        } else {
            r.payment.type = firstNonNull(
                    extract(text, "(신용\\(법인\\))"),
                    extract(text, "(신용|체크|직불)"));
        }
        System.out.println("[NAVER] paymentType=" + safe(r.payment.type));

        // 취소 영수증 여부 감지
        boolean isCancelled = text.contains("취소된 결제건") || text.contains("취소금액") || text.contains("취소일자");
        System.out.println("[NAVER] isCancelled=" + isCancelled);

        // 결제일자 / 취소일자
        // 취소 영수증은 "결제일자"와 "취소일자" 두 줄이 모두 있음 → 결제일자 기준으로 파싱
        String paymentDateTime = extractDot(text,
                "(?s)결제일자\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2}\\s+[0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1);
        String dateTarget = notEmpty(paymentDateTime) ? paymentDateTime : text;
        Matcher dtm = Pattern.compile("(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)")
                .matcher(dateTarget);
        if (dtm.find()) {
            r.meta.saleDate = normalizeDate(dtm.group(1));
            r.meta.saleTime = normalizeTime(dtm.group(2));
        } else {
            r.meta.saleDate = normalizeDate(extract(text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})"));
        }
        System.out.println("[NAVER] saleDate=" + safe(r.meta.saleDate) + ", saleTime=" + safe(r.meta.saleTime));

        // ---- 상품정보 ----
        System.out.println("[NAVER] ---- 상품정보 파싱 ----");

        String productBlock = extractDot(text, "(?s)상품명\\s*([\\s\\S]*?)\\s*(?:판매자\\s*정보|판매자정보)", 1);
        String productName = null;
        String orderNo = null;

        if (notEmpty(productBlock)) {
            orderNo = extract(productBlock, "([PDF][A-Z]?[0-9]{10,})");
            // 상품명 블록에서 노이즈 줄 제거 후 후보 선택
            StringBuilder pnSb = new StringBuilder();
            for (String ln : productBlock.replace("\r", "\n").split("\n")) {
                String t = ln.trim();
                if (t.isEmpty())
                    continue;
                if (isLooksLikeLabel(t))
                    continue;
                if (notEmpty(orderNo) && t.contains(orderNo))
                    continue; // 주문번호 줄
                if (isCardNumberLikeLine(t))
                    continue; // 카드번호 패턴
                if (t.matches("20\\d{2}-\\d{2}-\\d{2}.*"))
                    continue; // 날짜 줄
                if (t.matches("[가-힣A-Za-z]+\\([가-힣A-Za-z]+\\)/[가-힣]+"))
                    continue; // 거래종류 줄 (신용(법인)/일시불)
                pnSb.append(t).append("\n");
            }
            productName = pnSb.toString();
            productName = cleanProductName(productName);
        }
        if (!notEmpty(orderNo)) {
            orderNo = extract(text, "상품\\s*주문번호\\s*([PD][0-9A-Za-z]+)");
            if (!notEmpty(orderNo))
                orderNo = extract(text, "([PD][A-Z]?[0-9]{15,})");
        }
        r.meta.receiptNo = cleanField(orderNo);

        System.out.println("[NAVER] productName=" + safe(productName));
        System.out.println("[NAVER] orderNo=" + safe(orderNo));

        // ---- 판매자정보 ----
        System.out.println("[NAVER] ---- 판매자정보 파싱 ----");

        String sellerName = extractNaverSellerName(text);
        r.merchant.name = firstNonNull(cleanField(sellerName), "Unknown");

        List<String> bizNos = findAllBizNo(text);
        String sellerBiz = null;
        for (String b : bizNos) {
            if ("524-86-01528".equals(b))
                continue;
            sellerBiz = b;
            break;
        }
        if (!notEmpty(sellerBiz))
            sellerBiz = (bizNos.isEmpty() ? null : bizNos.get(0));
        r.merchant.bizNo = cleanField(sellerBiz);

        r.merchant.tel = extract(text, "([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})");

        String addr = extractDot(text,
                "(?s)사업장주소\\s*([\\s\\S]*?)\\s*(?:가맹점\\s*정보|가맹점정보|금액|$)", 1);
        if (notEmpty(addr))
            r.merchant.address = cleanField(addr);

        System.out.println("[NAVER] sellerName=" + safe(r.merchant.name));
        System.out.println("[NAVER] bizNo=" + safe(r.merchant.bizNo));
        System.out.println("[NAVER] tel=" + safe(r.merchant.tel));
        System.out.println("[NAVER] address=" + safe(r.merchant.address));

        // ---- 금액정보 ----
        System.out.println("[NAVER] ---- 금액정보 파싱 ----");

        AmountsNav a = parseNaverAmounts(text);
        if (a != null) {
            r.totals.vat = a.vat;
            if (isCancelled) {
                // 취소 영수증: taxable=0, total=취소금액(음수)
                r.totals.taxable = 0;
                Integer cancelAmt = (a.cancel != null) ? a.cancel
                        : (a.approval != null ? -Math.abs(a.approval) : 0);
                r.totals.total = cancelAmt;
                if (r.payment != null)
                    r.payment.approvalAmt = String.valueOf(cancelAmt);
            } else {
                r.totals.taxable = a.supply;
                r.totals.total = (a.approval != null && a.approval > 0) ? a.approval : a.total;
                if (r.payment != null && a.approval != null) {
                    r.payment.approvalAmt = String.valueOf(a.approval);
                }
            }
        }

        System.out.println("[NAVER] taxable(공급가액)=" + safeInt(r.totals.taxable));
        System.out.println("[NAVER] vat(부가세액)=" + safeInt(r.totals.vat));
        System.out.println("[NAVER] total=" + safeInt(r.totals.total));
        System.out.println("[NAVER] approvalAmt=" + safe(r.payment.approvalAmt));

        // taxFlag: 부가세액 > 0 → 과세, == 0 → 면세
        String taxFlag = null;
        if (r.totals.vat != null && r.totals.vat > 0) {
            taxFlag = "과세";
        } else if (r.totals.vat != null && r.totals.vat == 0) {
            taxFlag = "면세";
        }
        System.out.println("[NAVER] taxFlag=" + safe(taxFlag));

        // ---- 아이템 (항상 1개) ----
        Item it = new Item();
        it.name = notEmpty(productName) ? productName : "상품";
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        it.taxFlag = taxFlag;
        r.items = List.of(it);

        System.out.println("[NAVER] item => name=" + safe(it.name) + " | qty=1 | amount=" + safeInt(it.amount)
                + " | taxFlag=" + safe(taxFlag));
        System.out.println("[NAVER] ---- parseNaverCardReceipt 종료 ----");
        return r;
    }

    /**
     * 네이버 영수증에서 판매자상호 추출
     * OCR 구조: 라벨 컬럼 전체 → 값 컬럼 전체 (또는 인터리브)
     * 전략: "판매자 정보" ~ "가맹점 정보" 구간에서 사업자번호(XXX-XX-XXXXX) 바로 앞 줄
     */
    private String extractNaverSellerName(String text) {
        if (text == null)
            return null;
        String[] lines = text.replace("\r", "\n").split("\n");

        // "판매자 정보" 구간 시작/끝 인덱스
        int sellerSectionStart = -1;
        int sellerSectionEnd = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim().replace(" ", "");
            if (t.equals("판매자정보") || t.equals("판매자정보:")) {
                sellerSectionStart = i;
            }
            if (sellerSectionStart >= 0 && (t.equals("가맹점정보") || t.startsWith("가맹점명"))) {
                sellerSectionEnd = i;
                break;
            }
        }

        if (sellerSectionStart < 0)
            sellerSectionStart = 0;

        // 판매자 구간에서 사업자번호(XXX-XX-XXXXX) 위치 찾기 (524-86-01528 제외)
        Pattern BIZ = Pattern.compile("^[0-9]{3}-[0-9]{2}-[0-9]{5}$");
        int bizIdx = -1;
        for (int i = sellerSectionStart; i < sellerSectionEnd; i++) {
            String t = lines[i].trim();
            if (BIZ.matcher(t).matches() && !"524-86-01528".equals(t)) {
                bizIdx = i;
                break;
            }
        }

        System.out.println(
                "[NAVER.seller] sellerSection=[" + sellerSectionStart + "," + sellerSectionEnd + "], bizIdx=" + bizIdx);

        // 패턴1: "판매자상호" 라벨 바로 다음 줄이 값인 경우 (라벨/사업자번호/전화번호/주소 제외)
        int sellerLabelIdx = -1;
        for (int i = sellerSectionStart; i < sellerSectionEnd; i++) {
            if (lines[i].trim().replace(" ", "").equals("판매자상호")) {
                sellerLabelIdx = i;
                break;
            }
        }
        if (sellerLabelIdx >= 0) {
            for (int j = sellerLabelIdx + 1; j < sellerSectionEnd; j++) {
                String t = lines[j].trim();
                if (t.isEmpty())
                    continue;
                String tNoSpc = t.replace(" ", "");
                if (tNoSpc.equals("대표자명") || tNoSpc.equals("사업자등록번호")
                        || tNoSpc.equals("전화번호") || tNoSpc.equals("사업장주소") || tNoSpc.equals("판매자정보"))
                    break;
                if (t.matches("[0-9]{2,4}-[0-9]{3,4}-[0-9]{4}"))
                    break;
                if (BIZ.matcher(t).matches())
                    break;
                if (t.matches("[0-9\\-/\\*\\s]+"))
                    continue;
                System.out.println("[NAVER.seller] pattern1 found at line " + j + ": " + t);
                return t;
            }
        }

        // 패턴2: 값 컬럼 순서 활용
        // 네이버 OCR에서 값은 순서대로 나옴: 상호명 → 대표자명(사람) → 사업자번호 → 전화번호
        // → 사업자번호(bizIdx) 기준으로 "값 역방향 목록"을 수집해서 두번째 값이 상호명
        if (bizIdx > sellerSectionStart) {
            List<Integer> valueLines = new ArrayList<>();
            for (int j = bizIdx - 1; j > sellerSectionStart; j--) {
                String t = lines[j].trim();
                if (t.isEmpty())
                    continue;
                String tNoSpc = t.replace(" ", "");
                // 라벨이면 건너뜀 (값만 수집)
                if (tNoSpc.equals("판매자상호") || tNoSpc.equals("대표자명") || tNoSpc.equals("사업자등록번호")
                        || tNoSpc.equals("전화번호") || tNoSpc.equals("사업장주소") || tNoSpc.equals("판매자정보"))
                    continue;
                if (BIZ.matcher(t).matches())
                    continue;
                if (t.matches("[0-9\\-/\\*\\s]+"))
                    continue;
                valueLines.add(j);
            }
            System.out.println("[NAVER.seller] valueLines(역방향)=" + valueLines);
            // valueLines[0] = 사업자번호 바로 앞 값 = 대표자명(사람이름)
            // valueLines[1] = 그 앞 값 = 상호명
            if (valueLines.size() >= 2) {
                String candidate = lines[valueLines.get(1)].trim();
                System.out.println("[NAVER.seller] pattern2 found: " + candidate);
                return candidate;
            } else if (valueLines.size() == 1) {
                // 대표자명 없이 상호명만 있는 경우
                String candidate = lines[valueLines.get(0)].trim();
                System.out.println("[NAVER.seller] pattern2(단독) found: " + candidate);
                return candidate;
            }
        }

        return null;
    }

    private static class AmountsNav {
        Integer approval; // 승인금액
        Integer cancel; // 취소금액 (취소 영수증)
        Integer supply; // 공급가액
        Integer vat; // 부가세액
        Integer svc; // 봉사료
        Integer total; // 합계
    }

    /**
     * 네이버 영수증 금액 파싱
     * OCR 구조: 라벨 컬럼(승인금액/공급가액/부가세액/봉사료/합계) 먼저, 값 컬럼 나중에
     * → 금액 섹션에서 라벨 순서 위치 파악 후 숫자 순서대로 매핑
     */
    private AmountsNav parseNaverAmounts(String text) {
        if (text == null)
            return null;

        String[] lines = text.replace("\r", "\n").split("\n");

        // 금액 섹션 시작 찾기
        int amtSectionStart = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("금액")) {
                amtSectionStart = i;
                break;
            }
        }
        if (amtSectionStart < 0)
            return null;

        // 금액 섹션에서 라벨 순서 파악 (취소금액 포함)
        List<String> labels = new ArrayList<>();
        List<Integer> labelIdxs = new ArrayList<>();
        String[] expectedLabels = { "승인금액", "취소금액", "공급가액", "부가세액", "봉사료", "합계" };
        Set<String> expectedSet = new HashSet<>(Arrays.asList(expectedLabels));

        for (int i = amtSectionStart; i < lines.length; i++) {
            String t = lines[i].trim();
            if (expectedSet.contains(t)) {
                labels.add(t);
                labelIdxs.add(i);
            }
        }

        // 라벨 바로 뒤에 오는 숫자를 라벨 순서대로 매핑
        // OCR 구조: 라벨 줄들이 먼저 나오고, 값 줄들이 이어서 나옴
        // → 첫 번째 라벨 이후부터 숫자/0 값을 순서대로 수집 (라벨 줄은 건너뜀)
        List<Integer> nums = new ArrayList<>();
        Pattern AMT = Pattern.compile("^-?\\d{1,3}(?:,\\d{3})*$");
        int valueStart = labelIdxs.isEmpty() ? amtSectionStart : labelIdxs.get(0) + 1;
        for (int i = valueStart; i < lines.length; i++) {
            String t = lines[i].trim();
            if (expectedSet.contains(t)) continue; // 라벨 줄 건너뜀
            if (t.matches("^-?\\d{1,3}(?:,\\d{3})*$") || t.equals("0")) {
                nums.add(toInt(t));
            }
        }

        System.out.println("[DEBUG.naverAmounts] labels=" + labels + ", nums=" + nums);

        AmountsNav a = new AmountsNav();

        // 라벨 순서대로 nums 1:1 매핑
        if (labels.size() >= 4 && nums.size() >= labels.size()) {
            for (int i = 0; i < labels.size(); i++) {
                switch (labels.get(i)) {
                    case "승인금액":
                        a.approval = nums.get(i);
                        break;
                    case "취소금액":
                        a.cancel = nums.get(i);
                        break;
                    case "공급가액":
                        a.supply = nums.get(i);
                        break;
                    case "부가세액":
                        a.vat = nums.get(i);
                        break;
                    case "봉사료":
                        a.svc = nums.get(i);
                        break;
                    case "합계":
                        a.total = nums.get(i);
                        break;
                }
            }
        } else if (nums.size() >= 4) {
            // fallback: 마지막 값들을 합계→봉사료→부가세→공급가→승인 역순으로
            List<Integer> rev = new ArrayList<>(nums.subList(Math.max(0, nums.size() - 5), nums.size()));
            Collections.reverse(rev);
            if (rev.size() > 0)
                a.total = rev.get(0);
            if (rev.size() > 1)
                a.svc = rev.get(1);
            if (rev.size() > 2)
                a.vat = rev.get(2);
            if (rev.size() > 3)
                a.supply = rev.get(3);
            if (rev.size() > 4)
                a.approval = rev.get(4);
        }

        System.out.println("[DEBUG.naverAmounts] approval=" + a.approval + ", cancel=" + a.cancel
                + ", supply=" + a.supply + ", vat=" + a.vat + ", svc=" + a.svc + ", total=" + a.total);

        // 합계가 없거나 0이면 승인금액으로 대체
        if (a.total == null || a.total == 0)
            a.total = a.approval;
        if (a.approval == null || a.approval == 0)
            a.approval = a.total;

        return a;
    }

    private String normalizeCardMasked(String raw) {
        if (raw == null)
            return null;
        String x = cleanField(raw);
        // 괄호 유효기간 제거
        x = x.replaceAll("\\(.*?\\)", "").trim();
        // _ 같은 이상문자 -> *
        x = x.replace("_", "-");
        x = x.replaceAll("[^0-9\\*\\-]", "*");
        x = x.replaceAll("\\*{2,}", "****");
        String digits = x.replaceAll("[^0-9]", "");
        if (digits.length() >= 8) {
            return digits.substring(0, 4) + "-****-****-" + digits.substring(digits.length() - 4);
        }
        return x;
    }

    // 네이버 OCR에서 카드번호가 여러 줄로 분리된 경우 라벨 아래 값을 이어붙인다.
    private String extractNaverCardNo(String text) {
        if (text == null)
            return null;
        String[] lines = text.replace("\r", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String label = lines[i].trim().replace(" ", "");
            if (!label.startsWith("카드번호"))
                continue;

            StringBuilder sb = new StringBuilder();
            for (int j = i + 1; j < lines.length; j++) {
                String t = cleanField(lines[j]);
                if (!notEmpty(t))
                    continue;
                if (t.replace(" ", "").startsWith("거래종류") || t.contains("결제일자") || t.contains("상품명"))
                    break;
                if (isCardNumberLikeLine(t)) {
                    sb.append(t);
                }
            }
            String merged = sb.toString();
            if (notEmpty(merged))
                return merged;
        }
        return null;
    }

    // 카드번호 전체 또는 일부처럼 보이는 줄을 상품명 후보에서 제외한다.
    private boolean isCardNumberLikeLine(String line) {
        if (line == null)
            return false;
        String compact = line.replaceAll("\\s+", "");
        return compact.matches(".*[0-9*]{2,}[-_][0-9*]{2,}.*")
                || compact.matches(".*[0-9]{4}[-_*0-9]+.*")
                || compact.matches("[*\\-_/()0-9]{6,}");
    }

    private String extractValueAfterLabel(String text, String label, int maxLines) {
        if (text == null)
            return null;
        String[] lines = text.replace("\r", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String ln = cleanField(lines[i]);
            if (!notEmpty(ln))
                continue;

            String lnNoSpace = ln.replace(" ", "");
            String labelNoSpace = label.replace(" ", "");
            if (lnNoSpace.equals(labelNoSpace) || lnNoSpace.startsWith(labelNoSpace)) {
                for (int k = 1; k <= maxLines && (i + k) < lines.length; k++) {
                    String cand = cleanField(lines[i + k]);
                    if (!notEmpty(cand))
                        continue;
                    if (isLooksLikeLabel(cand))
                        continue;

                    // 섹션 헤더면 중단
                    if (cand.contains("가맹점 정보") || cand.contains("금액") || cand.contains("판매자 정보"))
                        break;

                    return cand;
                }
                return null;
            }
        }
        return null;
    }

    private boolean isLooksLikeLabel(String s) {
        if (s == null)
            return false;
        String t = s.replace(" ", "");
        String[] labels = {
                "대표자명", "사업자등록번호", "전화번호", "사업장주소",
                "가맹점정보", "가맹점명", "가맹점번호", "주소",
                "승인금액", "공급가액", "부가세액", "봉사료", "합계",
                "상품주문번호", "상품주문", "주문번호", "상품명"
        };
        for (String l : labels) {
            if (t.equals(l) || t.startsWith(l))
                return true;
        }
        return false;
    }

    private String findCompanyLikeLine(String section, String... blacklistContains) {
        if (section == null)
            return null;
        String[] lines = section.replace("\r", "\n").split("\n");
        String best = null;

        for (String line : lines) {
            String t = cleanField(line);
            if (!notEmpty(t))
                continue;
            if (isLooksLikeLabel(t))
                continue;

            boolean blocked = false;
            for (String b : blacklistContains) {
                if (b != null && !b.isEmpty() && t.contains(b)) {
                    blocked = true;
                    break;
                }
            }
            if (blocked)
                continue;

            // 사람 이름(2~4글자) 같은 건 제외
            if (t.matches("^[가-힣]{2,4}$"))
                continue;

            // 회사명 형태 힌트
            if (t.contains("(주)") || t.contains("주식회사") || t.contains("회사") ||
                    t.endsWith("케미칼") || t.endsWith("상사") || t.endsWith("마트") || t.endsWith("점")) {
                if (best == null || t.length() > best.length())
                    best = t;
            }
        }
        return best;
    }

    private List<String> findAllBizNo(String text) {
        List<String> list = new ArrayList<>();
        if (text == null)
            return list;
        Matcher m = Pattern.compile("([0-9]{3}-[0-9]{2}-[0-9]{5})").matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (!list.contains(v))
                list.add(v);
        }
        return list;
    }

    private String sliceSection(String text, String startLabel, String endLabel, int maxLen) {
        if (text == null)
            return "";
        int s = text.indexOf(startLabel);
        if (s < 0)
            return "";
        int e = (endLabel == null)
                ? Math.min(text.length(), s + maxLen)
                : text.indexOf(endLabel, s + startLabel.length());
        if (e < 0)
            e = Math.min(text.length(), s + maxLen);
        return text.substring(s, e);
    }

    private String pickBestProductLine(String block) {
        if (block == null)
            return null;
        String[] lines = block.replace("\r", "\n").split("\n");
        String best = null;
        for (String ln : lines) {
            String t = cleanField(ln);
            if (!notEmpty(t))
                continue;
            if (isLooksLikeLabel(t))
                continue;
            if (best == null || t.length() > best.length())
                best = t;
        }
        return best;
    }

    /* ========================= 0) Homeplus 템플릿 감지 ========================= */

    private boolean isHomeplusSlip(String text) {
        String lower = (text == null) ? "" : text.toLowerCase();

        boolean hasBrand = lower.contains("homeplus") || text.contains("홈플러스");
        boolean hasTitle = text.contains("신용카드매출전표") || text.contains("신용카드 매출전표");

        boolean hasPaySection = text.contains("결제금액") && (text.contains("금액") || text.contains("합계"));
        boolean hasSellerSection = text.contains("판매자 정보") || text.contains("판매자정보") || text.contains("판매자상호");
        boolean hasFranchiseSection = text.contains("가맹점 정보") || text.contains("가맹점정보") || text.contains("가맹점점명");

        boolean hasKeyFields = text.contains("승인번호") &&
                (text.contains("주문번호") || text.contains("주 문 번 호") || text.contains("주문 번호")) &&
                (text.contains("품명") || text.contains("품목") || text.contains("상품명")) &&
                (text.contains("승인일시") || text.contains("승인 일시"));

        // ✅ Homeplus 로고가 OCR에서 누락될 수 있으니, 타이틀+섹션+키필드 조합으로도 인정
        boolean result = (hasBrand && (hasTitle || (hasPaySection && (hasSellerSection || hasFranchiseSection))))
                || (hasTitle && hasPaySection && (hasSellerSection || hasFranchiseSection) && hasKeyFields);

        System.out.println("[DETECT] Homeplus hasBrand=" + hasBrand
                + ", hasTitle=" + hasTitle
                + ", hasPaySection=" + hasPaySection
                + ", hasSellerSection=" + hasSellerSection
                + ", hasFranchiseSection=" + hasFranchiseSection
                + ", hasKeyFields=" + hasKeyFields
                + " => " + result);

        return result;
    }

    /* ========================= 1) Homeplus 전용 파싱 ========================= */

    private ReceiptResult parseHomeplusSlip(String text) {
        System.out.println("=== ▶ parseHomeplusSlip START ===");

        ReceiptResult r = new ReceiptResult();

        // 1) 승인번호 / 주문번호
        r.approval.approvalNo = firstNonNull(
                debugExtract("approvalNo#1", text, "승인번호\\s*[:：]?\\s*([0-9]{6,12})", 1),
                debugExtract("approvalNo#2", text, "승\\s*인\\s*번\\s*호\\s*[:：]?\\s*([0-9]{6,12})", 1));

        r.meta.receiptNo = firstNonNull(
                debugExtract("orderNo#1", text, "주문번호\\s*[:：]?\\s*([0-9]{8,})", 1),
                debugExtract("orderNo#2", text, "주\\s*문\\s*번\\s*호\\s*[:：]?\\s*([0-9]{8,})", 1),
                debugExtract("orderNo#3", text, "주문\\s*번호\\s*[:：]?\\s*([0-9]{8,})", 1));

        // 2) 품명(=상품명 역할)
        String itemName = firstNonNull(
                debugExtractDot("itemName#1", text,
                        "(?s)품명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보|$)",
                        1),
                debugExtractDot("itemName#2", text,
                        "(?s)(품목|상품명)\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보|$)",
                        2));
        itemName = cleanField(itemName);

        // "외 N건" 처리
        Integer qtyGuess = 1;
        String itemCore = itemName;

        if (notEmpty(itemName)) {
            Matcher m = Pattern.compile("(?s)(.+?)\\s*외\\s*([0-9]+)\\s*건\\s*$").matcher(itemName);
            if (m.find()) {
                itemCore = cleanField(m.group(1));
                Integer extra = toInt(m.group(2));
                if (extra != null && extra >= 0)
                    qtyGuess = 1 + extra;
                System.out.println("[HOMEPLUS] itemName has '외N건' => core=" + itemCore + ", qtyGuess=" + qtyGuess);
            }
        }

        // 3) 카드종류 / 카드번호 / 거래유형 / 할부개월
        String cardType = firstNonNull(
                debugExtractDot("cardType#1", text,
                        "(?s)카드종류\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드번호|유효기간|거래유형|할부개월|승인일시|$)", 1),
                debugExtract("cardType#2", text,
                        "카드종류\\s*[:：]?\\s*([가-힣A-Za-z0-9()\\-\\s]{2,30})", 1));
        cardType = cleanField(cardType);

        r.payment.cardBrand = normalizeCardBrand(firstNonNull(
                cardType,
                debugExtract("cardBrand#fallback", text,
                        "(IBK비씨카드|IBK\\s*비씨카드|BC\\s*카드\\(.*?\\)|BC\\s*카드|BC카드|비씨카드|BC|국민|신한|현대|롯데|농협|하나|NH|KB)", 1)));

        // 카드번호: 마스킹/하이픈/부분숫자 등 다양
        String cardNo = firstNonNull(
                debugExtract("cardNo#1", text, "카드번호\\s*[:：]?\\s*([0-9]{4}[- ]?[0-9]{2}\\*+[- ]?\\*+[- ]?\\*+)", 1),
                debugExtract("cardNo#2", text, "카드번호\\s*[:：]?\\s*([0-9\\-* ]{7,25})", 1),
                debugExtract("cardNo#3", text, "카드번호\\s*[:：]?\\s*([0-9]{6,20})", 1),
                debugExtractDot("cardNo#4_near", text,
                        "(?s)카드번호\\s*[:：]?\\s*([\\s\\S]{0,40})\\s*(유효기간|거래유형|할부개월|승인일시|$)", 1));
        cardNo = cleanField(cardNo);
        if (notEmpty(cardNo) && cardNo.length() > 25) {
            String refined = extract(cardNo, "([0-9]{4}[- ]?[0-9\\*\\- ]{3,20})", 1);
            if (refined != null)
                cardNo = refined;
        }
        r.payment.cardMasked = cardNo;

        String tradeType = firstNonNull(
                debugExtractDot("tradeType#1", text,
                        "(?s)거래유형\\s*[:：]?\\s*([\\s\\S]*?)\\s*(할부개월|승인일시|결제금액|$)", 1),
                debugExtract("tradeType#2", text,
                        "거래유형\\s*[:：]?\\s*(정상매출|취소매출|정상|취소|승인|매출)", 1));
        tradeType = cleanField(tradeType);
        r.payment.type = firstNonNull(tradeType, "신용거래");

        String installment = firstNonNull(
                debugExtractDot("installment#1", text,
                        "(?s)할부개월\\s*[:：]?\\s*([\\s\\S]*?)\\s*(승인일시|결제금액|$)", 1),
                debugExtract("installment#2", text, "할부개월\\s*[:：]?\\s*(일시불|[0-9]{1,2}개월)", 1));
        installment = cleanField(installment);
        System.out.println("[HOMEPLUS] installment=" + safe(installment));

        // 4) 승인일시: "2025-12-31 11:21:27"
        String datePart = firstNonNull(
                debugExtract("approveDate#1", text,
                        "승인일시\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1),
                debugExtract("approveDate#fallback", text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1));

        String timePart = firstNonNull(
                debugExtract("approveTime#1", text,
                        "승인일시\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 2),
                debugExtract("approveTime#fallback", text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1));

        r.meta.saleDate = normalizeDate(datePart);
        r.meta.saleTime = normalizeTime(timePart);

        // 5) 결제금액 블록
        Integer amount = firstNonNullInt(
                debugInt("amount#1", text, "금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("amount#2", text, "결제금액[\\s\\S]*?금액\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1));
        Integer vat = firstNonNullInt(
                debugInt("vat#1", text, "부가세\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("vat#2", text, "결제금액[\\s\\S]*?부가세\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1));
        Integer total = firstNonNullInt(
                debugInt("total#1", text, "합계\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("total#2", text, "결제금액[\\s\\S]*?합계\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1));

        r.totals.taxable = amount;
        r.totals.vat = vat;
        r.totals.total = total;

        if (r.totals.total == null) {
            if (amount != null && vat != null)
                r.totals.total = amount + vat;
            else if (amount != null)
                r.totals.total = amount;
        }

        // 6) 판매자상호 / 가맹점점명
        String seller = firstNonNull(
                debugExtractDot("seller#1", text,
                        "(?s)판매자상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|가맹점\\s*정보|가맹점정보|$)", 1),
                debugExtractDot("seller#2", text,
                        "(?s)판매자\\s*정보[\\s\\S]*?판매자상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|$)", 1));
        seller = cleanField(seller);

        String franchiseName = firstNonNull(
                debugExtractDot("franchise#1", text,
                        "(?s)가맹점점명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|가맹점주소|전화번호|$)", 1),
                debugExtractDot("franchise#2", text,
                        "(?s)가맹점\\s*정보[\\s\\S]*?가맹점점명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|가맹점주소|전화번호|$)", 1));
        franchiseName = cleanField(franchiseName);

        String merchantName = firstNonNull(
                notEmpty(seller) ? seller : null,
                notEmpty(franchiseName) ? franchiseName : null,
                extract(text, "(홈플러스\\s*[가-힣A-Za-z0-9()\\-]*점)", 1),
                (text.toLowerCase().contains("homeplus") ? "Homeplus" : null),
                "홈플러스");
        r.merchant.name = merchantName;

        // 7) 아이템 구성
        Item it = new Item();
        it.name = notEmpty(itemCore) ? itemCore : (notEmpty(itemName) ? itemName : "품목");
        it.qty = (qtyGuess != null && qtyGuess > 0) ? qtyGuess : 1;
        it.amount = r.totals.total;
        it.unitPrice = (it.qty != null && it.qty > 0 && r.totals.total != null) ? (r.totals.total / it.qty)
                : r.totals.total;

        r.items = List.of(it);

        System.out.println("[HOMEPLUS] ✅ FINAL ITEM => name=" + it.name + ", qty=" + it.qty +
                ", amount=" + safeInt(it.amount) + ", unitPrice=" + safeInt(it.unitPrice));
        System.out.println("=== ◀ parseHomeplusSlip END ===");

        return r;
    }

    /* ========================= 2) 쿠팡 앱 결제내역 ========================= */

    private ReceiptResult parseAppVersion(String text) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "쿠팡";

        String totalStr = extract(text, "쿠팡\\(쿠페이\\)\\s*[-]?\\s*([0-9,]+)\\s*원");
        if (totalStr == null)
            totalStr = extract(text, "(-?[0-9,]+)\\s*원");
        r.totals.total = toInt(totalStr);

        r.payment.cardBrand = firstNonNull(extract(text, "(쿠페이)"), extract(text, "(쿠팡페이)"));
        r.payment.type = "간편결제";
        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");
        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);

        String memoItem = firstNonNull(
                extractDot(text, "(?s)거래메모\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s:/,\\.\\-()]{2,60})\\s*(결제|승인|$)", 1),
                extract(text, "([가-힣A-Za-z0-9]+\\s?(절단미역|쌀강정|세제|쿠키|강정|미역))"));

        Item it = new Item();
        it.name = (memoItem != null ? memoItem : "쿠팡 구매상품").trim();
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        return r;
    }

    /* ========================= 3) 기존 카드영수증 ========================= */

    private ReceiptResult parseCardVersion(String text) {
        ReceiptResult r = new ReceiptResult();

        // ✅ 판매자상호 regex 종료조건 강화 (폭주 방지)
        String sellerName = extractDot(text,
                "(?s)판매자상호\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|사업장주소|가맹점\\s*정보|금액|$)", 1);
        sellerName = cleanField(sellerName);

        r.merchant.name = firstNonNull(
                notEmpty(sellerName) ? sellerName : null,
                extract(text, "(쿠팡\\(주\\)|쿠팡주식회사|쿠팡)"),
                "쿠팡");

        String cardType = extractDot(text,
                "(?s)카드종류\\s*([가-힣A-Za-z0-9\\s]*?카드)\\s*(거래종류|할부개월|카드번호|거래일시|승인번호|$)", 1);
        cardType = cleanField(cardType);

        r.payment.cardBrand = firstNonNull(
                notEmpty(cardType) ? cardType : null,
                extract(text, "(IBK비씨카드|IBK\\s*비씨카드|BC카드|비씨카드|BC)"),
                extract(text, "(농협|하나|국민|신한|롯데|현대|NH|KB)"),
                extract(text, "(농협카드|하나카드|국민카드|신한카드|롯데카드|현대카드)"));
        r.payment.cardBrand = normalizeCardBrand(r.payment.cardBrand);

        r.payment.cardMasked = firstNonNull(
                extract(text, "(\\d{4}\\*+\\d{2,6}\\*?\\d{0,6})"),
                extract(text, "(\\d{4}\\*{4,}\\d{3,4}\\*?)"),
                extract(text, "카드번호\\s*[:：]?\\s*([0-9\\-*]{7,25})", 1));

        String tradeType = extractDot(text,
                "(?s)거래종류\\s*([가-힣A-Za-z0-9\\s]{2,20})\\s*(할부개월|카드번호|거래일시|승인번호|$)", 1);
        tradeType = cleanField(tradeType);

        r.payment.type = firstNonNull(
                notEmpty(tradeType) ? tradeType : null,
                extract(text, "(신용거래|현금거래|일시불|할부)"),
                "신용거래");

        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);
        r.approval.approvalNo = extract(text, "(승인\\s*번호)\\s*[:：]?\\s*([0-9]{6,12})", 2);

        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");

        r.totals.taxable = firstInt(text, "과세금액[^0-9]*([0-9,]+)");
        r.totals.vat = firstInt(text, "부가세[^0-9]*([0-9,]+)");
        r.totals.taxFree = firstInt(text, "비과세금액[^0-9]*([0-9,]+)");

        Integer totalFromLabel = firstInt(text, "합계금액[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        if (totalFromLabel == null) {
            totalFromLabel = firstInt(text, "(총액|결제금액)[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        }
        r.totals.total = totalFromLabel;

        r.items = parseCardItemsV2_LabelFirst(text, r.totals.total);

        return r;
    }

    private List<Item> parseCardItemsV2_LabelFirst(String text, Integer totalAmount) {
        String product = extractDot(text,
                "(?s)상품명\\s*([\\s\\S]*?)\\s*(과세금액|비과세금액|부가세|합계금액|이용상점정보|$)", 1);
        product = cleanProductName(product);

        if (notEmpty(product)) {
            Item it = new Item();
            it.name = product;
            it.qty = 1;
            it.amount = totalAmount;
            it.unitPrice = totalAmount;
            return List.of(it);
        }

        Item it = new Item();
        it.name = "상품";
        it.qty = 1;
        it.amount = totalAmount;
        it.unitPrice = totalAmount;
        return List.of(it);
    }

    /* ========================= 유형 감지 (기존) ========================= */

    private boolean isCoupangAppReceipt(String text) {
        boolean hasCoupay = text.contains("쿠팡(쿠페이)") || text.contains("쿠페이");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = text.contains("카드영수증") || text.contains("구매정보");
        return hasCoupay && hasMemo && !hasCardReceipt;
    }

    /*
     * ========================= printFullResult (요청 버전) =========================
     */

    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");

        // Merchant
        System.out.println("[MERCHANT] name: " + safe(getMerchantName(r)));
        try {
            System.out.println("[MERCHANT] (reflection) " + reflectFields(getMerchant(r)));
        } catch (Exception ignore) {
        }

        // Meta
        System.out.println("[META] receiptNo(orderNo): " + safe(getMetaReceiptNo(r)));
        System.out.println("[META] saleDate: " + safe(getMetaSaleDate(r)));
        System.out.println("[META] saleTime: " + safe(getMetaSaleTime(r)));
        try {
            System.out.println("[META] (reflection) " + reflectFields(getMeta(r)));
        } catch (Exception ignore) {
        }

        // Payment
        System.out.println("[PAYMENT] type: " + safe(getPaymentType(r)));
        System.out.println("[PAYMENT] cardBrand: " + safe(getPaymentCardBrand(r)));
        System.out.println("[PAYMENT] cardMasked: " + safe(getPaymentCardMasked(r)));
        System.out.println("[PAYMENT] approvalAmt: " + safe(getPaymentApprovalAmt(r)));
        try {
            System.out.println("[PAYMENT] (reflection) " + reflectFields(getPayment(r)));
        } catch (Exception ignore) {
        }

        // Approval
        System.out.println("[APPROVAL] approvalNo: " + safe(getApprovalNo(r)));
        try {
            System.out.println("[APPROVAL] (reflection) " + reflectFields(getApproval(r)));
        } catch (Exception ignore) {
        }

        // Totals
        System.out.println("[TOTALS] total: " + safeInt(getTotalsTotal(r)));
        System.out.println("[TOTALS] taxable: " + safeInt(getTotalsTaxable(r)));
        System.out.println("[TOTALS] vat: " + safeInt(getTotalsVat(r)));
        System.out.println("[TOTALS] taxFree: " + safeInt(getTotalsTaxFree(r)));
        try {
            System.out.println("[TOTALS] (reflection) " + reflectFields(getTotals(r)));
        } catch (Exception ignore) {
        }

        // Items
        int itemCount = (r != null && r.items != null) ? r.items.size() : 0;
        System.out.println("[ITEMS] count: " + itemCount);
        if (r != null && r.items != null) {
            for (int i = 0; i < r.items.size(); i++) {
                Item it = r.items.get(i);
                System.out.println("  · item#" + i
                        + " name=" + safe(it != null ? it.name : null)
                        + " | qty=" + safe(it != null ? it.qty : null)
                        + " | amount=" + safeInt(it != null ? it.amount : null)
                        + " | unitPrice=" + safeInt(it != null ? it.unitPrice : null));
                try {
                    System.out.println("    [ITEM reflection] " + reflectFields(it));
                } catch (Exception ignore) {
                }
            }
        }

        // Root reflection
        try {
            System.out.println("[ROOT reflection] " + reflectFields(r));
        } catch (Exception ignore) {
        }

        System.out.println("---------------------------------");
    }

    /* ========================= safe getters ========================= */

    private Merchant getMerchant(ReceiptResult r) {
        return (r == null ? null : r.merchant);
    }

    private Meta getMeta(ReceiptResult r) {
        return (r == null ? null : r.meta);
    }

    private Payment getPayment(ReceiptResult r) {
        return (r == null ? null : r.payment);
    }

    private Approval getApproval(ReceiptResult r) {
        return (r == null ? null : r.approval);
    }

    private Totals getTotals(ReceiptResult r) {
        return (r == null ? null : r.totals);
    }

    private String getMerchantName(ReceiptResult r) {
        return (getMerchant(r) == null ? null : getMerchant(r).name);
    }

    private String getMetaReceiptNo(ReceiptResult r) {
        return (getMeta(r) == null ? null : getMeta(r).receiptNo);
    }

    private String getMetaSaleDate(ReceiptResult r) {
        return (getMeta(r) == null ? null : getMeta(r).saleDate);
    }

    private String getMetaSaleTime(ReceiptResult r) {
        return (getMeta(r) == null ? null : getMeta(r).saleTime);
    }

    private String getPaymentType(ReceiptResult r) {
        return (getPayment(r) == null ? null : getPayment(r).type);
    }

    private String getPaymentCardBrand(ReceiptResult r) {
        return (getPayment(r) == null ? null : getPayment(r).cardBrand);
    }

    private String getPaymentCardMasked(ReceiptResult r) {
        return (getPayment(r) == null ? null : getPayment(r).cardMasked);
    }

    private String getPaymentApprovalAmt(ReceiptResult r) {
        return (getPayment(r) == null ? null : getPayment(r).approvalAmt);
    }

    private String getApprovalNo(ReceiptResult r) {
        return (getApproval(r) == null ? null : getApproval(r).approvalNo);
    }

    private Integer getTotalsTotal(ReceiptResult r) {
        return (getTotals(r) == null ? null : getTotals(r).total);
    }

    private Integer getTotalsTaxable(ReceiptResult r) {
        return (getTotals(r) == null ? null : getTotals(r).taxable);
    }

    private Integer getTotalsVat(ReceiptResult r) {
        return (getTotals(r) == null ? null : getTotals(r).vat);
    }

    private Integer getTotalsTaxFree(ReceiptResult r) {
        return (getTotals(r) == null ? null : getTotals(r).taxFree);
    }

    /* ========================= Debug extract helpers ========================= */

    private String debugExtract(String label, String text, String regex, int group) {
        String v = extract(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extract] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private String debugExtractDot(String label, String text, String regex, int group) {
        String v = extractDot(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extractDot] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private Integer debugInt(String label, String text, String regex, int group) {
        String s = extract(text, regex, group);
        Integer n = toInt(s);
        if (DEBUG) {
            System.out.println("[DEBUG.int] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  raw  = " + (s == null ? "null" : ("'" + s + "'")));
            System.out.println("  int  = " + (n == null ? "null" : n));
        }
        return n;
    }

    private Integer firstNonNullInt(Integer... nums) {
        for (Integer n : nums)
            if (n != null)
                return n;
        return null;
    }

    /* ========================= 공통 유틸 ========================= */

    private String normalizeTextKeepNewlines(String s) {
        if (s == null)
            return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\u00A0]", " "); // NBSP
        s = s.replaceAll("[\\t\\x0B\\f]+", " "); // tab류

        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String x = line.replaceAll(" +", " ").trim();
            if (!x.isEmpty())
                sb.append(x).append("\n");
        }
        return sb.toString().trim();
    }

    private String normalizeDate(String date) {
        if (date == null)
            return null;
        String d = date.trim()
                .replace(".", "-")
                .replace("/", "-")
                .replaceAll("\\s+", " ");

        Matcher m = Pattern.compile("(20\\d{2})-([0-9]{1,2})-([0-9]{1,2})").matcher(d);
        if (m.find()) {
            String yy = m.group(1);
            int mm = Integer.parseInt(m.group(2));
            int dd = Integer.parseInt(m.group(3));
            return yy + "-" + String.format("%02d", mm) + "-" + String.format("%02d", dd);
        }
        return d;
    }

    private String normalizeTime(String time) {
        if (time == null)
            return null;
        return time.trim().replaceAll("\\s+", " ");
    }

    protected String extract(String text, String regex) {
        return extract(text, regex, 1);
    }

    protected String extract(String text, String regex, int group) {
        try {
            if (text == null)
                return null;
            Matcher m = Pattern.compile(regex).matcher(text);
            if (!m.find())
                return null;
            int g = Math.min(group, m.groupCount());
            return m.group(g).trim();
        } catch (Exception e) {
            return null;
        }
    }

    protected String extractDot(String text, String regex, int group) {
        try {
            if (text == null)
                return null;
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
            if (!m.find())
                return null;
            int g = Math.min(group, m.groupCount());
            return m.group(g).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(Object o) {
        return (o == null ? "" : String.valueOf(o));
    }

    private String safeInt(Integer n) {
        return (n == null ? "null" : n.toString());
    }

    protected Integer toInt(String s) {
        try {
            return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    protected Integer firstInt(String text, String regex) {
        try {
            if (text == null)
                return null;
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find())
                return toInt(m.group(m.groupCount()));
        } catch (Exception ignore) {
        }
        return null;
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr)
            if (s != null && !s.trim().isEmpty())
                return s.trim();
        return null;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String cleanField(String s) {
        if (s == null)
            return null;
        return s.replaceAll("[\\u00A0]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanProductName(String s) {
        if (s == null)
            return null;
        s = s.replaceAll("[\\u00A0]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보).*", "").trim();
        s = s.replaceAll("[,.:/\\-]+$", "").trim();
        return s;
    }

    private String normalizeCardBrand(String s) {
        if (s == null)
            return null;
        s = s.replaceAll("\\s+", "");
        if (s.equalsIgnoreCase("BC"))
            return "BC카드";
        if (s.equals("비씨"))
            return "비씨카드";
        if (s.contains("비씨") && !s.endsWith("카드"))
            return s + "카드";
        if (s.equals("BC카드"))
            return "BC카드";
        if (s.equals("IBK비씨카드") || s.equals("IBK비씨카드카드"))
            return "IBK비씨카드";
        return s;
    }

    /* ========================= reflectFields ========================= */

    protected String reflectFields(Object obj) {
        if (obj == null)
            return "null";
        StringBuilder sb = new StringBuilder();
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        reflectFieldsInternal(obj, sb, visited, 0, 2);
        return sb.toString();
    }

    private void reflectFieldsInternal(Object obj, StringBuilder sb, Map<Object, Boolean> visited, int depth,
            int maxDepth) {
        if (obj == null) {
            sb.append("null");
            return;
        }
        if (visited.containsKey(obj)) {
            sb.append("(circular-ref)");
            return;
        }
        visited.put(obj, true);

        Class<?> c = obj.getClass();
        sb.append(c.getSimpleName()).append("{");

        Field[] fields = c.getDeclaredFields();
        boolean first = true;

        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers()))
                continue;

            if (!first)
                sb.append(", ");
            first = false;

            f.setAccessible(true);
            sb.append(f.getName()).append("=");

            try {
                Object v = f.get(obj);
                if (v == null)
                    sb.append("null");
                else if (isPrimitiveLike(v))
                    sb.append(String.valueOf(v));
                else if (depth >= maxDepth)
                    sb.append(v.getClass().getSimpleName());
                else
                    reflectFieldsInternal(v, sb, visited, depth + 1, maxDepth);
            } catch (Exception e) {
                sb.append("(error:").append(e.getClass().getSimpleName()).append(")");
            }
        }

        sb.append("}");
    }

    private boolean isPrimitiveLike(Object v) {
        return v instanceof String
                || v instanceof Number
                || v instanceof Boolean
                || v instanceof Character
                || v.getClass().isPrimitive()
                || v.getClass().isEnum();
    }
}
