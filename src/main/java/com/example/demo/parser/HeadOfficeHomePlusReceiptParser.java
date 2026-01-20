package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOfficeHomePlusReceiptParser v14.x
 * - ✅ Homeplus OCR: 라벨 먼저/값 나중(블록형) 구조 대응
 * - ✅ anchor("신용카드매출전표") 이후에서 필드 탐색
 * - ✅ 결제금액: "결제금액~판매자 정보" 섹션에서 콤마 금액만 추출 (주소/날짜 숫자 배제)
 * - ✅ 상품명/주문번호/금액 정확도 강화
 */
public class HeadOfficeHomePlusReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    @Override
    public ReceiptResult parse(Document doc) {
        String rawText = normalizeTextKeepNewlines(text(doc));

        System.out.println("=================================");
        System.out.println("=== 🧾 RAW TEXT (HomePlus/Generic) ===");
        System.out.println(rawText);
        System.out.println("=================================");

        boolean isHomeplus = isHomeplusSlip(rawText);
        boolean isApp = isCoupangAppReceipt(rawText);

        System.out.println("🧭 인식된 유형:");
        System.out.println("  - HomeplusSlip? " + isHomeplus);
        System.out.println("  - CoupangApp?   " + isApp);

        ReceiptResult r;
        if (isHomeplus) {
            r = parseHomeplusSlip(rawText);
        } else if (isApp) {
            r = parseAppVersion(rawText);
        } else {
            r = parseCardVersion(rawText);
        }

        printFullResult(r);
        return r;
    }

    /* ========================= 0) Homeplus 템플릿 감지 ========================= */

    private boolean isHomeplusSlip(String text) {
        String lower = (text == null) ? "" : text.toLowerCase();

        boolean hasBrand = lower.contains("homeplus") || text.contains("홈플러스");
        boolean hasTitle = text.contains("신용카드매출전표") || text.contains("신용카드 매출전표");

        boolean hasPaySection = text.contains("결제금액") && (text.contains("금액") || text.contains("합계"));
        boolean hasSellerSection = text.contains("판매자 정보") || text.contains("판매자정보");
        boolean hasFranchiseSection = text.contains("가맹점 정보") || text.contains("가맹점정보");

        boolean hasKeyFields =
                text.contains("승인번호") &&
                text.contains("주문번호") &&
                (text.contains("품명") || text.contains("품목") || text.contains("상품명")) &&
                text.contains("승인일시");

        boolean result =
                (hasBrand && (hasTitle || (hasPaySection && (hasSellerSection || hasFranchiseSection))))
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

        String[] lines = toLines(text);
        int anchor = indexOfLineContains(lines, "신용카드매출전표"); // ✅ anchor 이후만 탐색
        if (anchor < 0) anchor = indexOfLineContains(lines, "신용카드 매출전표");
        if (anchor < 0) anchor = 0;

        if (DEBUG) System.out.println("[HOMEPLUS] anchorIdx=" + anchor + " line=" + safe(anchor < lines.length ? lines[anchor] : null));

        // ✅ 1) 승인번호/주문번호/품명 : anchor 이후 "값 블록"에서 순서대로 잡는다
        int cursor = anchor + 1;

        // 승인번호(6~12자리 숫자)
        String approvalNo = findNextMatch(lines, cursor, 80, "^[0-9]{6,12}$");
        if (notEmpty(approvalNo)) {
            r.approval.approvalNo = approvalNo;
            cursor = indexOfExact(lines, approvalNo, cursor) + 1;
        }

        // 주문번호(8자리 이상 숫자) - 승인번호 다음에 나오는 큰 숫자
        String orderNo = findNextMatch(lines, cursor, 120, "^[0-9]{8,}$");
        if (notEmpty(orderNo)) {
            r.meta.receiptNo = orderNo;
            cursor = indexOfExact(lines, orderNo, cursor) + 1;
        }

        // 품명: 주문번호 다음 라인들 중 "결제금액" 나오기 전까지 첫 유효 텍스트
        String itemName = findNextProductLineUntil(lines, cursor, 200, "결제금액");
        itemName = cleanField(itemName);

        // "외1건" 처리
        Integer qtyGuess = 1;
        String itemCore = itemName;
        if (notEmpty(itemName)) {
            Matcher m = Pattern.compile("(?s)(.+?)\\s*외\\s*([0-9]+)\\s*건\\s*$").matcher(itemName);
            if (m.find()) {
                itemCore = cleanField(m.group(1));
                Integer extra = toInt(m.group(2));
                if (extra != null && extra >= 0) qtyGuess = 1 + extra;
            }
        }

        if (DEBUG) {
            System.out.println("[HOMEPLUS.scan] approvalNo=" + safe(r.approval.approvalNo));
            System.out.println("[HOMEPLUS.scan] orderNo=" + safe(r.meta.receiptNo));
            System.out.println("[HOMEPLUS.scan] itemName=" + safe(itemName));
        }

        // ✅ 2) 카드종류/카드번호/유효기간/거래유형/할부개월/승인일시 : anchor 이후에서 패턴으로 탐색
        String cardType = findNextContains(lines, anchor + 1, 250, "카드"); // 예: BC카드(페이북)
        // 단, "카드번호/카드종류" 같은 라벨 줄은 제외
        if (notEmpty(cardType) && isLooksLikeLabel(cardType)) cardType = null;

        r.payment.cardBrand = normalizeCardBrand(cleanField(cardType));

        // 카드번호: 숫자 6~20 (샘플은 5130410)
        String cardNo = findNextMatch(lines, anchor + 1, 300, "^[0-9]{6,20}$");
        r.payment.cardMasked = cleanField(cardNo);

        // 유효기간: **/** 또는 12/34 형태
        String validThru = findNextMatch(lines, anchor + 1, 300, "^[0-9\\*]{2}\\s*/\\s*[0-9\\*]{2}$");
        if (DEBUG) System.out.println("[HOMEPLUS] validThru=" + safe(validThru));

        // 거래유형: 정상매출/취소매출
        String tradeType = findNextMatch(lines, anchor + 1, 350, "^(정상매출|취소매출|정상|취소).*$");
        r.payment.type = firstNonNull(cleanField(tradeType), "신용거래");

        // 할부개월: 일시불 또는 N개월
        String installment = findNextMatch(lines, anchor + 1, 350, "^(일시불|[0-9]{1,2}\\s*개월)$");
        if (DEBUG) System.out.println("[HOMEPLUS] installment=" + safe(installment));

        // 승인일시: yyyy-mm-dd hh:mm:ss
        String approveDT = findNextMatch(lines, anchor + 1, 400,
                "^(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)$");

        if (notEmpty(approveDT)) {
            Matcher m = Pattern.compile("^(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)$")
                    .matcher(approveDT.trim());
            if (m.find()) {
                r.meta.saleDate = normalizeDate(m.group(1));
                r.meta.saleTime = normalizeTime(m.group(2));
            }
        } else {
            r.meta.saleDate = normalizeDate(extract(text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1));
            r.meta.saleTime = normalizeTime(extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1));
        }

        // ✅ 3) 결제금액: "결제금액 ~ 판매자 정보" 구간에서 콤마 금액만 추출
        PayNums pay = parseHomeplusPayNumsStrict(text);
        r.totals.taxable = pay.amount;
        r.totals.vat = pay.vat;
        r.totals.total = pay.total;

        // NPE 방지
        if (r.totals.total == null) {
            if (r.totals.taxable != null && r.totals.vat != null) r.totals.total = r.totals.taxable + r.totals.vat;
            else if (r.totals.taxable != null) r.totals.total = r.totals.taxable;
        }

        // ✅ 4) 판매자 정보(지점) / 가맹점 정보(법인)에서 사업자번호/전화번호 추출
        String sellerSection = sliceSection(text, "판매자 정보", "가맹점 정보", 2000);
        if (notEmpty(sellerSection)) {
            String sellerName = firstNonLabelLine(toLines(sellerSection));
            String sellerBiz = firstMatch(sellerSection, "([0-9]{3}-[0-9]{2}-[0-9]{5})");
            String sellerTel = firstMatch(sellerSection, "([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})");
            if (notEmpty(sellerBiz)) r.merchant.bizNo = sellerBiz;
            if (notEmpty(sellerTel)) r.merchant.tel = sellerTel;
            r.merchant.name = firstNonNull(cleanField(sellerName), r.merchant.name);
        }

        String franSection = sliceSection(text, "가맹점 정보", null, 2500);
        String franchiseName = null;
        if (notEmpty(franSection)) {
            String[] fLines = toLines(franSection);
            franchiseName = firstNonNull(
                    findValueAfterLabelSimple(fLines, "가맹점명", 30),
                    findValueAfterLabelSimple(fLines, "가맹점점명", 30)
            );
        }

        // merchant.name 최종 우선순위: 판매자(지점) > 가맹점명(법인) > 홈플러스
        r.merchant.name = firstNonNull(
                r.merchant.name,
                cleanField(franchiseName),
                "홈플러스"
        );

        // ✅ 5) 아이템 1개 구성
        Item it = new Item();
        it.name = notEmpty(itemCore) ? itemCore : "품목";
        it.qty = (qtyGuess != null && qtyGuess > 0) ? qtyGuess : 1;
        it.amount = r.totals.total;
        it.unitPrice = (it.qty != null && it.qty > 0 && r.totals.total != null) ? (r.totals.total / it.qty) : r.totals.total;
        r.items = List.of(it);

        if (DEBUG) {
            System.out.println("[HOMEPLUS] ✅ FINAL => approvalNo=" + safe(r.approval.approvalNo)
                    + ", orderNo=" + safe(r.meta.receiptNo)
                    + ", item=" + safe(it.name)
                    + ", qty=" + safe(it.qty)
                    + ", amount=" + safeInt(it.amount)
                    + ", vat=" + safeInt(r.totals.vat)
                    + ", total=" + safeInt(r.totals.total)
                    + ", seller=" + safe(r.merchant.name)
                    + ", bizNo=" + safe(r.merchant.bizNo)
                    + ", tel=" + safe(r.merchant.tel));
        }

        System.out.println("=== ◀ parseHomeplusSlip END ===");
        return r;
    }

    /* ========================= 결제금액 파싱(엄격) ========================= */

    private static class PayNums {
        Integer amount;
        Integer vat;
        Integer total;
    }

    private PayNums parseHomeplusPayNumsStrict(String fullText) {
        PayNums p = new PayNums();

        // ✅ 결제금액 섹션만 자르기 (숫자 노이즈 제거 핵심)
        String paySection = sliceSection(fullText, "결제금액", "판매자 정보", 1500);

        // ✅ 콤마 포함된 금액만 뽑는다: 52,546 / 5,254원 / 57,800원
        List<Integer> money = new ArrayList<>();
        if (paySection != null) {
            Matcher m = Pattern.compile("(\\d{1,3}(?:,\\d{3})+)\\s*원?").matcher(paySection);
            while (m.find()) {
                Integer v = toInt(m.group(1));
                if (v != null) money.add(v);
            }
        }

        if (DEBUG) System.out.println("[HOMEPLUS.pay.strict] money=" + money);

        // 기대: amount, vat, total (3개)
        if (money.size() >= 3) {
            // 섹션 안에서도 가끔 중복이 들어오면 뒤에서 3개 사용
            List<Integer> last3 = money.subList(money.size() - 3, money.size());
            p.amount = last3.get(0);
            p.vat = last3.get(1);
            p.total = last3.get(2);
        } else if (money.size() == 2) {
            p.amount = money.get(0);
            p.vat = money.get(1);
            p.total = (p.amount != null && p.vat != null) ? (p.amount + p.vat) : null;
        } else if (money.size() == 1) {
            p.total = money.get(0);
            p.amount = money.get(0);
        }

        if (DEBUG) {
            System.out.println("[HOMEPLUS.pay] ✅ amount=" + p.amount + ", vat=" + p.vat + ", total=" + p.total);
        }
        return p;
    }

    /* ========================= 2) 쿠팡 앱 / 3) 기타 (기존 유지용) ========================= */

    private ReceiptResult parseAppVersion(String text) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "쿠팡";
        Item it = new Item();
        it.name = "쿠팡 구매상품";
        it.qty = 1;
        r.items = List.of(it);
        return r;
    }

    private ReceiptResult parseCardVersion(String text) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "Unknown";
        Item it = new Item();
        it.name = "상품";
        it.qty = 1;
        r.items = List.of(it);
        return r;
    }

    private boolean isCoupangAppReceipt(String text) {
        boolean hasCoupay = text.contains("쿠팡(쿠페이)") || text.contains("쿠페이");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = text.contains("카드영수증") || text.contains("구매정보");
        return hasCoupay && hasMemo && !hasCardReceipt;
    }

    /* ========================= 라인/정규 유틸 ========================= */

    private String[] toLines(String text) {
        if (text == null) return new String[0];
        return text.replace("\r", "\n").split("\n");
    }

    private int indexOfLineContains(String[] lines, String needle) {
        if (lines == null || needle == null) return -1;
        for (int i = 0; i < lines.length; i++) {
            String t = cleanField(lines[i]);
            if (t != null && t.contains(needle)) return i;
        }
        return -1;
    }

    private int indexOfExact(String[] lines, String value, int from) {
        if (lines == null || value == null) return -1;
        for (int i = Math.max(0, from); i < lines.length; i++) {
            String t = cleanField(lines[i]);
            if (value.equals(t)) return i;
        }
        return -1;
    }

    private String findNextMatch(String[] lines, int from, int limit, String regex) {
        if (lines == null) return null;
        Pattern p = Pattern.compile(regex);
        int end = Math.min(lines.length, from + limit);
        for (int i = Math.max(0, from); i < end; i++) {
            String t = cleanField(lines[i]);
            if (!notEmpty(t)) continue;
            if (isLooksLikeLabel(t)) continue;
            if (p.matcher(t).matches()) return t;
        }
        return null;
    }

    private String findNextContains(String[] lines, int from, int limit, String keyword) {
        if (lines == null) return null;
        int end = Math.min(lines.length, from + limit);
        for (int i = Math.max(0, from); i < end; i++) {
            String t = cleanField(lines[i]);
            if (!notEmpty(t)) continue;
            if (isLooksLikeLabel(t)) continue;
            if (t.contains(keyword)) return t;
        }
        return null;
    }

    // 주문번호 뒤에서 결제금액 전까지 "상품명 후보"를 잡는다
    private String findNextProductLineUntil(String[] lines, int from, int limit, String stopWord) {
        int end = Math.min(lines.length, from + limit);
        for (int i = Math.max(0, from); i < end; i++) {
            String t = cleanField(lines[i]);
            if (!notEmpty(t)) continue;
            if (t.contains(stopWord)) break;
            if (isLooksLikeLabel(t)) continue;
            if (t.equalsIgnoreCase("homeplus")) continue; // ✅ 로고 텍스트 제거
            if (t.contains("신용카드매출전표")) continue;

            // 숫자만인 라인은 상품명이 아님
            if (t.matches("^[0-9]+$")) continue;

            return t;
        }
        return null;
    }

    // 간단 라벨 다음값 (섹션 안에서는 유효)
    private String findValueAfterLabelSimple(String[] lines, String label, int maxLines) {
        if (lines == null || label == null) return null;
        for (int i = 0; i < lines.length; i++) {
            String ln = cleanField(lines[i]);
            if (!notEmpty(ln)) continue;
            if (ln.equals(label) || ln.startsWith(label)) {
                for (int k = 1; k <= maxLines && (i + k) < lines.length; k++) {
                    String cand = cleanField(lines[i + k]);
                    if (!notEmpty(cand)) continue;
                    if (isLooksLikeLabel(cand)) continue;
                    return cand;
                }
            }
        }
        return null;
    }

    private String firstNonLabelLine(String[] lines) {
        if (lines == null) return null;
        for (String s : lines) {
            String t = cleanField(s);
            if (!notEmpty(t)) continue;
            if (isLooksLikeLabel(t)) continue;
            if (t.contains("판매자 정보") || t.contains("가맹점 정보")) continue;
            return t;
        }
        return null;
    }

    private String firstMatch(String text, String regex) {
        if (text == null) return null;
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private boolean isLooksLikeLabel(String s) {
        if (s == null) return false;
        String t = s.replace(" ", "");
        String[] labels = {
                "승인번호","주문번호","품명","품목","상품명",
                "카드종류","카드번호","유효기간","거래유형","할부개월","승인일시",
                "결제금액","금액","부가세","합계",
                "판매자정보","판매자상호","대표자명","사업자등록번호","전화번호","사업장주소",
                "가맹점정보","가맹점명","가맹점점명","가맹점주소","주소"
        };
        for (String l : labels) {
            String x = l.replace(" ", "");
            if (t.equals(x) || t.startsWith(x)) return true;
        }
        return false;
    }

    /* ========================= print / reflect ========================= */

    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");
        System.out.println("[MERCHANT] name: " + safe(r != null && r.merchant != null ? r.merchant.name : null));
        System.out.println("[META] receiptNo(orderNo): " + safe(r != null && r.meta != null ? r.meta.receiptNo : null));
        System.out.println("[META] saleDate: " + safe(r != null && r.meta != null ? r.meta.saleDate : null));
        System.out.println("[META] saleTime: " + safe(r != null && r.meta != null ? r.meta.saleTime : null));
        System.out.println("[APPROVAL] approvalNo: " + safe(r != null && r.approval != null ? r.approval.approvalNo : null));
        System.out.println("[TOTALS] total: " + safeInt(r != null && r.totals != null ? r.totals.total : null));
        System.out.println("[TOTALS] taxable: " + safeInt(r != null && r.totals != null ? r.totals.taxable : null));
        System.out.println("[TOTALS] vat: " + safeInt(r != null && r.totals != null ? r.totals.vat : null));

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
            }
        }
        System.out.println("---------------------------------");
    }

    protected String reflectFields(Object obj) {
        if (obj == null) return "null";
        StringBuilder sb = new StringBuilder();
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        reflectFieldsInternal(obj, sb, visited, 0, 2);
        return sb.toString();
    }

    private void reflectFieldsInternal(Object obj, StringBuilder sb, Map<Object, Boolean> visited, int depth, int maxDepth) {
        if (obj == null) { sb.append("null"); return; }
        if (visited.containsKey(obj)) { sb.append("(circular-ref)"); return; }
        visited.put(obj, true);

        Class<?> c = obj.getClass();
        sb.append(c.getSimpleName()).append("{");

        Field[] fields = c.getDeclaredFields();
        boolean first = true;

        for (Field f : fields) {
            if (Modifier.isStatic(f.getModifiers())) continue;

            if (!first) sb.append(", ");
            first = false;

            f.setAccessible(true);
            sb.append(f.getName()).append("=");

            try {
                Object v = f.get(obj);
                if (v == null) sb.append("null");
                else if (isPrimitiveLike(v)) sb.append(String.valueOf(v));
                else if (depth >= maxDepth) sb.append(v.getClass().getSimpleName());
                else reflectFieldsInternal(v, sb, visited, depth + 1, maxDepth);
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

    /* ========================= 공통 유틸 ========================= */

    private String normalizeTextKeepNewlines(String s) {
        if (s == null) return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\u00A0]", " ");
        s = s.replaceAll("[\\t\\x0B\\f]+", " ");

        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String x = line.replaceAll(" +", " ").trim();
            if (!x.isEmpty()) sb.append(x).append("\n");
        }
        return sb.toString().trim();
    }

    private String normalizeDate(String date) {
        if (date == null) return null;
        String d = date.trim().replace(".", "-").replace("/", "-").replaceAll("\\s+", " ");
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
        if (time == null) return null;
        return time.trim().replaceAll("\\s+", " ");
    }

    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(Math.min(group, m.groupCount())).trim() : null;
        } catch (Exception e) { return null; }
    }

    protected Integer toInt(String s) {
        try { return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return null; }
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }

    private String cleanField(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\u00A0]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s+", "");
        if (s.equalsIgnoreCase("BC")) return "BC카드";
        if (s.equals("비씨")) return "비씨카드";
        if (s.contains("비씨") && !s.endsWith("카드")) return s + "카드";
        return s;
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    private String sliceSection(String text, String startLabel, String endLabel, int maxLen) {
        if (text == null) return "";
        int s = text.indexOf(startLabel);
        if (s < 0) return "";
        int e = (endLabel == null) ? -1 : text.indexOf(endLabel, s + startLabel.length());
        if (e < 0) e = Math.min(text.length(), s + maxLen);
        return text.substring(s, e);
    }
}
