package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CoupangReceiptParser v14.x
 * - 쿠팡 카드영수증(화면형) 포맷 고정: 패턴 기반 추출로 라벨/섹션 섞임에 강함
 * - 결제정보: 블록에서 정규식으로 카드번호/승인번호/거래일시/카드종류/거래종류/할부 추출
 * - 구매정보: 승인번호 이후에서 주문번호(12~20자리) + 금액 4개(과세/비과세/부가세/합계) 추출
 * - 상품명: 주문번호 다음부터 첫 금액 전까지, 라벨/섹션 단어 제거 + 수량 추출/정리
 * - 상점정보: 판매자상호/사업자번호/주소 추출 (사업자번호는 포맷 맞는 것 우선)
 * - 카드금액 필드도 함께 채우도록 훅 제공(applyCardTotals)
 *
 * ✅ v14.1
 * - 파싱 결과를 최대한 상세하게 콘솔에 출력(원본/라인/중간결과/최종결과)
 */
public class HeadOfficeCoupangReceiptParser extends BaseReceiptParser {

    // 날짜/시간
    private static final Pattern DATE_TIME = Pattern.compile(
            "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})\\s*([0-2]?\\d:[0-5]\\d:[0-5]\\d)"
    );

    // 카드번호 마스킹
    private static final Pattern MASKED_CARD = Pattern.compile("\\b\\d{4}\\*{2,}\\d{2,}\\*?\\d{0,4}\\b");

    // 승인번호(6~12자리)
    private static final Pattern APPROVAL_NO = Pattern.compile("\\b\\d{6,12}\\b");

    // 주문번호(쿠팡은 보통 12~20자리)
    private static final Pattern ORDER_NO = Pattern.compile("\\b\\d{12,20}\\b");

    // 금액: 19,400 / 19,400원 / 0원
    private static final Pattern MONEY = Pattern.compile("\\b([0-9]{1,9}(?:,[0-9]{3})*)(?:\\s*원)?\\b");

    // 사업자번호
    private static final Pattern BIZNO_DASH = Pattern.compile("\\b(\\d{3}-\\d{2}-\\d{5})\\b");
    private static final Pattern BIZNO_10 = Pattern.compile("\\b(\\d{10})\\b");

    // 수량 (용량단위는 제외)
    private static final Pattern QTY_UNIT = Pattern.compile("(?i)\\b([0-9]{1,3})\\s*(개|ea|입|팩|봉|병|캔|세트|box|박스)\\b");
    private static final Pattern QTY_X = Pattern.compile("(?i)\\b(?:x\\s*([0-9]{1,3})|([0-9]{1,3})\\s*x)\\b");
    private static final Pattern SIZE_UNIT = Pattern.compile("(?i)\\b\\d+(?:\\.\\d+)?\\s*(kg|g|l|ml|oz|lb|cm|mm|m)\\b");

    // 라벨/섹션 제거용(상품명에서 제거)
    private static final Pattern JUNK_LABELS = Pattern.compile(
            "(카드영수증|결제정보|구매정보|이용상점정보|판매자상호|판매자\\s*사업자등록번호|판매자주소|"
                    + "카드종류|거래종류|할부개월|카드번호|거래일시|승인번호|주문번호|"
                    + "상품명|과세금액|비과세금액|부가세|합계금액)"
    );

    @Override
    public ReceiptResult parse(Document doc) {

        String rawKeepNl = text(doc)
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\u00A0", " ")
                .trim();

        String oneLine = rawKeepNl.replace("\n", " ").replaceAll(" +", " ").trim();

        System.out.println("=== 🧾 RAW TEXT (KEEP NL) ===");
        System.out.println(rawKeepNl);
        System.out.println("=============================");
        System.out.println("=== 🧾 RAW TEXT (ONE LINE) ===");
        System.out.println(oneLine);
        System.out.println("=============================");

        boolean isApp = isCoupangAppReceipt(oneLine, rawKeepNl);
        System.out.println("🧭 인식된 유형: " + (isApp ? "쿠팡앱 결제내역" : "카드영수증"));

        ReceiptResult r = isApp ? parseAppVersion(oneLine) : parseCardVersion(rawKeepNl);

        // ✅ 최종 로그 (최대한 많은 항목)
        printFullResult(r);

        return r;
    }

    /* ========================= 1) 쿠팡 앱 결제내역 ========================= */

    private ReceiptResult parseAppVersion(String oneLine) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "쿠팡";

        System.out.println("[APP] ---- parseAppVersion 시작 ----");

        String totalStr = extract(oneLine, "쿠팡\\(쿠페이\\)\\s*[-]?\\s*([0-9,]+)원", 1);
        if (totalStr == null) totalStr = extract(oneLine, "([0-9,]+)원", 1);
        r.totals.total = toInt(totalStr);
        System.out.println("[APP] totalStr=" + safe(totalStr) + " => total=" + safeInt(r.totals.total));

        r.payment.cardBrand = firstNonNull(extract(oneLine, "(쿠페이)", 1), extract(oneLine, "(쿠팡페이)", 1));
        r.payment.type = "간편결제";

        r.meta.saleDate = extract(oneLine, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1);
        r.meta.saleTime = extract(oneLine, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1);
        r.meta.receiptNo = extract(oneLine, "(주문번호)\\s*[:：]?\\s*([0-9]{8,})", 2);

        System.out.println("[APP] saleDate=" + safe(r.meta.saleDate) + ", saleTime=" + safe(r.meta.saleTime));
        System.out.println("[APP] orderNo=" + safe(r.meta.receiptNo));
        System.out.println("[APP] payType=" + safe(r.payment.type) + ", cardBrand=" + safe(r.payment.cardBrand));

        Item it = new Item();
        it.name = "쿠팡 구매상품";
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        // 카드 금액 훅(필요 시)
        applyCardTotals(r);

        System.out.println("[APP] ---- parseAppVersion 종료 ----");
        return r;
    }

    /* ========================= 2) 카드영수증(화면형) ========================= */

    private ReceiptResult parseCardVersion(String rawKeepNl) {
        ReceiptResult r = new ReceiptResult();

        System.out.println("[CARD] ---- parseCardVersion 시작 ----");

        List<String> lines = splitLines(rawKeepNl);

        System.out.println("[CARD] lines.size=" + lines.size());
        for (int i = 0; i < lines.size(); i++) {
            System.out.printf("[CARD] L%02d: %s%n", i, lines.get(i));
        }

        // ---- 결제정보: 정규식으로 값 추출(라벨/순서에 영향 없음) ----
        String joined = String.join("\n", lines);

        r.payment.cardMasked = findFirst(MASKED_CARD, joined);
        System.out.println("[CARD] cardMasked=" + safe(r.payment.cardMasked));

        // 거래일시
        Matcher dtm = DATE_TIME.matcher(joined);
        if (dtm.find()) {
            r.meta.saleDate = dtm.group(1);
            r.meta.saleTime = dtm.group(2);
        }
        System.out.println("[CARD] saleDate=" + safe(r.meta.saleDate) + ", saleTime=" + safe(r.meta.saleTime));

        // 승인번호: 거래일시 뒤쪽에서 첫 6~12자리
        r.approval.approvalNo = findApprovalAfterDateTime(lines);
        System.out.println("[CARD] approvalNo=" + safe(r.approval.approvalNo));

        // 카드종류/거래종류/할부개월은 "대표 텍스트"로 추출
        String cardBrandRaw = pickFirstAmong(lines, List.of("IBK비씨카드", "IBK", "BC카드", "비씨", "국민", "KB", "NH", "농협", "삼성", "신한", "현대", "롯데", "하나"));
        r.payment.cardBrand = normalizeCardBrand(cardBrandRaw);
        System.out.println("[CARD] cardBrandRaw=" + safe(cardBrandRaw) + " => cardBrand=" + safe(r.payment.cardBrand));

        String tradeType = pickFirstAmong(lines, List.of("신용거래", "승인거래", "체크", "현금", "정상매출"));
        String installment = pickFirstAmong(lines, List.of("일시불", "할부", "개월"));
        r.payment.type = firstNonNull(tradeType, "신용거래");

        String inst = normalizeInstallment(installment, lines);
        if (inst != null && !inst.isEmpty() && !inst.equals(r.payment.type)) {
            r.payment.type = r.payment.type + "(" + inst + ")";
        }

        System.out.println("[CARD] tradeType=" + safe(tradeType) + ", installmentPicked=" + safe(installment) + ", installmentNorm=" + safe(inst));
        System.out.println("[CARD] payment.type=" + safe(r.payment.type));

        // ---- 구매정보: 승인번호 이후 구간에서 주문번호/상품명/금액 4개 추출 ----
        System.out.println("[CARD] ---- 구매정보 파싱 시작 ----");
        PurchaseParsed p = parsePurchaseFromApprovalOnward(lines, r.approval.approvalNo);

        System.out.println("[CARD] orderNo=" + safe(p.orderNo));
        System.out.println("[CARD] productNameRaw=" + safe(p.productNameRaw));
        System.out.println("[CARD] monies => taxable=" + safeInt(p.taxable)
                + ", taxFree=" + safeInt(p.taxFree)
                + ", vat=" + safeInt(p.vat)
                + ", total=" + safeInt(p.total));

        r.meta.receiptNo = p.orderNo;

        r.totals.taxable = p.taxable;
        r.totals.taxFree = p.taxFree;
        r.totals.vat     = p.vat;
        r.totals.total   = p.total;

        // total fallback
        if (r.totals.total == null) {
            if (r.totals.taxable != null && r.totals.vat != null) r.totals.total = r.totals.taxable + r.totals.vat;
            else if (r.totals.taxFree != null) r.totals.total = r.totals.taxFree;
        }

        System.out.println("[CARD] totals.final => taxable=" + safeInt(r.totals.taxable)
                + ", taxFree=" + safeInt(r.totals.taxFree)
                + ", vat=" + safeInt(r.totals.vat)
                + ", total=" + safeInt(r.totals.total));

        // 상품명 정리 (수량은 무조건 1, 단가=금액)
        ProductName pn = refineProductName(p.productNameRaw);

        System.out.println("[CARD] refineProductName => name=" + safe(pn.name));

        boolean hasTaxable = r.totals.taxable != null && r.totals.taxable > 0
                && r.totals.vat != null && r.totals.vat > 0;
        boolean hasTaxFree = r.totals.taxFree != null && r.totals.taxFree > 0;

        System.out.println("[CARD] hasTaxable=" + hasTaxable + ", hasTaxFree=" + hasTaxFree);

        if (hasTaxable && hasTaxFree) {
            // 과세+면세 혼합 → detail 2건
            int taxableTotal = r.totals.taxable + r.totals.vat;
            int taxFreeTotal = r.totals.taxFree;

            Item itTaxable = new Item();
            itTaxable.name = pn.name;
            itTaxable.qty = 1;
            itTaxable.amount = taxableTotal;
            itTaxable.unitPrice = taxableTotal;
            itTaxable.taxFlag = "과세";

            Item itTaxFree = new Item();
            itTaxFree.name = pn.name;
            itTaxFree.qty = 1;
            itTaxFree.amount = taxFreeTotal;
            itTaxFree.unitPrice = taxFreeTotal;
            itTaxFree.taxFlag = "면세";

            r.items = List.of(itTaxable, itTaxFree);
            System.out.println("[CARD] items=2 (혼합: 과세" + taxableTotal + " + 면세" + taxFreeTotal + ")");
        } else {
            Item it = new Item();
            it.name = pn.name;
            it.qty = 1;
            it.amount = r.totals.total;
            it.unitPrice = r.totals.total;
            it.taxFlag = hasTaxable ? "과세" : hasTaxFree ? "면세" : null;
            r.items = List.of(it);
            System.out.println("[CARD] items=1 taxFlag=" + safe(it.taxFlag));
        }

        // ---- 상점정보: 판매자상호/사업자번호/주소 ----
        System.out.println("[CARD] ---- 상점정보 파싱 시작 ----");
        ShopParsed sp = parseShop(lines);
        System.out.println("[CARD] sellerName=" + safe(sp.sellerName));
        System.out.println("[CARD] bizNo=" + safe(sp.bizNo));
        System.out.println("[CARD] address=" + safe(sp.address));

        r.merchant.name = firstNonNull(sp.sellerName, "카드영수증");
        // ⚠️ ReceiptResult에 bizNo 필드가 없으면 이 줄은 네 DTO에 맞게 수정
        trySetMerchantBizNo(r, sp.bizNo);
        trySetMerchantAddress(r, sp.address);

        // 카드 영수증인데 카드금액 필드가 비는 문제 대응
        applyCardTotals(r);

        System.out.println("[CARD] ---- parseCardVersion 종료 ----");
        return r;
    }

    /* ========================= 구매정보 파싱(승인번호 이후) ========================= */

    private static class PurchaseParsed {
        String orderNo;
        String productNameRaw;
        Integer taxable;
        Integer taxFree;
        Integer vat;
        Integer total;
    }

    private PurchaseParsed parsePurchaseFromApprovalOnward(List<String> lines, String approvalNo) {
        PurchaseParsed p = new PurchaseParsed();

        int start = 0;
        if (approvalNo != null) {
            int idx = indexOfExact(lines, approvalNo);
            if (idx >= 0) start = idx + 1;
        }

        List<String> tail = lines.subList(Math.min(start, lines.size()), lines.size());

        System.out.println("[PURCHASE] approvalNo=" + safe(approvalNo) + ", startIndex=" + start + ", tail.size=" + tail.size());
        for (int i = 0; i < tail.size(); i++) {
            System.out.printf("[PURCHASE] T%02d: %s%n", i, tail.get(i));
        }

        // 1) 주문번호: tail에서 첫 12~20자리 숫자
        p.orderNo = findFirst(ORDER_NO, String.join("\n", tail));
        System.out.println("[PURCHASE] found orderNo=" + safe(p.orderNo));

        // 2) 금액 4개: tail에서 money 후보만 모아서 "마지막 4개"를 (과세/비과세/부가세/합계)로 본다
        List<Integer> monies = new ArrayList<>();
        List<String> moneyLines = new ArrayList<>();
        for (String s : tail) {
            Integer mv = parseMoneyStrict(s);
            if (mv != null) {
                monies.add(mv);
                moneyLines.add(s);
            }
        }

        System.out.println("[PURCHASE] moneyCandidates.count=" + monies.size());
        for (int i = 0; i < monies.size(); i++) {
            System.out.println("[PURCHASE] money#" + i + " = " + monies.get(i) + " (line: " + moneyLines.get(i) + ")");
        }

        if (monies.size() >= 4) {
            int n = monies.size();
            p.taxable = monies.get(n - 4);
            p.taxFree = monies.get(n - 3);
            p.vat     = monies.get(n - 2);
            p.total   = monies.get(n - 1);
        }

        // 3) 상품명 raw: 주문번호 다음 라인부터 "첫 금액" 전까지 텍스트 합치기
        if (p.orderNo != null) {
            int orderIdxInTail = indexOfExact(tail, p.orderNo);
            int firstMoneyLineIdx = firstMoneyLineIndex(tail);

            System.out.println("[PURCHASE] orderIdxInTail=" + orderIdxInTail + ", firstMoneyLineIdx=" + firstMoneyLineIdx);

            if (orderIdxInTail >= 0) {
                int a = orderIdxInTail + 1;
                int b = (firstMoneyLineIdx > 0 ? firstMoneyLineIdx : tail.size());
                if (b > a) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = a; i < b; i++) {
                        String t = tail.get(i).trim();
                        if (t.isEmpty()) continue;
                        sb.append(t).append(" ");
                    }
                    p.productNameRaw = sb.toString().replaceAll("\\s{2,}", " ").trim();
                }
            }
        }

        // fallback
        if (p.productNameRaw == null || p.productNameRaw.isEmpty()) {
            System.out.println("[PURCHASE] productNameRaw empty => fallback mode");
            StringBuilder sb = new StringBuilder();
            for (String s : tail) {
                String t = s.trim();
                if (t.isEmpty()) continue;
                if (ORDER_NO.matcher(t).matches()) continue;
                if (parseMoneyStrict(t) != null) break;
                sb.append(t).append(" ");
            }
            p.productNameRaw = sb.toString().replaceAll("\\s{2,}", " ").trim();
        }

        System.out.println("[PURCHASE] productNameRaw.final=" + safe(p.productNameRaw));

        return p;
    }

    private int firstMoneyLineIndex(List<String> tail) {
        for (int i = 0; i < tail.size(); i++) {
            if (parseMoneyStrict(tail.get(i)) != null) return i;
        }
        return -1;
    }

    /**
     * 돈으로 인정하는 조건(승인번호/주문번호 같은 숫자 배제)
     * - '원' 또는 콤마+숫자3자리 패턴이 있어야 돈으로 인정
     * - 숫자/콤마/원 외에 다른 문자(한글 '원' 제외)가 있으면 금액 아님
     */
    private Integer parseMoneyStrict(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.isEmpty()) return null;

        // '원'을 제거했을 때 숫자/콤마/공백만 남아야 금액으로 인정
        // "5,840원" → "5,840" → 숫자+콤마만 ✅
        // "곰곰 곱슬이 콩나물, 3.5kg, 1박스" → 제거 후 잡문자 남음 ❌
        String stripped = x.replaceAll("원$", "").trim();
        if (!stripped.matches("[0-9,\\s]+")) return null;

        Matcher m = MONEY.matcher(x);
        if (!m.find()) return null;

        boolean hasWon = x.endsWith("원");
        boolean hasComma = stripped.contains(",");
        String digits = m.group(1).replaceAll("[^0-9]", "");

        // 콤마도 원도 없는 순수 숫자(예: 부가세 685)도 금액으로 허용
        // 단, 7자리 이상은 아래 필터에서 걸림(승인번호/주문번호 방어)
        boolean isStandaloneDigits = stripped.matches("[0-9]+");
        if (!hasWon && !hasComma && !isStandaloneDigits) return null;

        // 너무 길면(주문번호급) 배제
        if (digits.length() >= 7) return null;

        return toInt(m.group(1));
    }

    /* ========================= 상품명 정리/수량 ========================= */

    private static class ProductName {
        String name;
        Integer qty;
    }

    private ProductName refineProductName(String raw) {
        ProductName pn = new ProductName();

        String x = (raw == null ? "" : raw).replaceAll("\\s{2,}", " ").trim();
        System.out.println("[PRODUCT] raw=" + safe(raw));
        System.out.println("[PRODUCT] compact=" + safe(x));

        x = JUNK_LABELS.matcher(x).replaceAll(" ");
        x = x.replaceAll("\\s{2,}", " ").trim();
        System.out.println("[PRODUCT] removedLabels=" + safe(x));

        pn.qty = extractQty(x);
        System.out.println("[PRODUCT] qtyFound=" + safeInt(pn.qty));

        x = removeQtyTokens(x);
        System.out.println("[PRODUCT] removedQtyTokens=" + safe(x));

        // 끝에 붙는 단독 "669" 같은 잡숫자 제거
        x = x.replaceAll("\\b\\d{1,4}\\b$", "").trim();
        System.out.println("[PRODUCT] removedTailNumber=" + safe(x));

        pn.name = x.isEmpty() ? "구매상품" : x;
        return pn;
    }

    private Integer extractQty(String text) {
        if (text == null) return null;

        // "총 N건" 우선 (쿠팡 묶음 주문)
        Matcher mt = Pattern.compile("총\\s*([0-9]+)\\s*건").matcher(text);
        if (mt.find()) {
            Integer v = toInt(mt.group(1));
            if (v != null && v > 0) return v;
        }

        Matcher m = QTY_UNIT.matcher(text);
        Integer best = null;
        while (m.find()) {
            Integer v = toInt(m.group(1));
            if (v != null && v > 0) best = (best == null) ? v : Math.max(best, v);
        }

        if (best == null) {
            Matcher mx = QTY_X.matcher(text);
            if (mx.find()) {
                Integer v = toInt(firstNonNull(mx.group(1), mx.group(2)));
                if (v != null && v > 0) best = v;
            }
        }

        // 용량만 있는 경우는 qty로 보지 않음
        if (best != null && SIZE_UNIT.matcher(text).matches()) return null;

        return best;
    }

    private String removeQtyTokens(String text) {
        if (text == null) return null;
        String x = text;
        x = x.replaceAll("(?i)\\b([0-9]{1,3})\\s*(개|ea|입|팩|봉|병|캔|세트|box|박스)\\b", " ");
        x = x.replaceAll("(?i)\\b(x\\s*[0-9]{1,3}|[0-9]{1,3}\\s*x)\\b", " ");
        x = x.replaceAll("\\s{2,}", " ").trim();
        return x;
    }

    /* ========================= 상점정보 ========================= */

    private static class ShopParsed {
        String sellerName;
        String bizNo;
        String address;
    }

    private ShopParsed parseShop(List<String> lines) {
        ShopParsed sp = new ShopParsed();

        sp.sellerName = firstNonNull(
                sellerNameAfterLabel(lines, "판매자상호"),
                sellerNameAfterLabel(lines, "판매자 상호")
        );
        System.out.println("[SHOP] sellerName.afterLabel=" + safe(sp.sellerName));

        sp.bizNo = firstNonNull(
                normalizeBizNo(valueAfterLabel(lines, "판매자 사업자등록번호")),
                findBizNo(lines)
        );
        System.out.println("[SHOP] bizNo=" + safe(sp.bizNo));

        sp.address = collectAfterLabelUntilNextLabel(lines, "판매자주소",
                Set.of("판매자 사업자등록번호", "판매자상호", "카드영수증", "결제정보", "구매정보"));
        System.out.println("[SHOP] address=" + safe(sp.address));

        if (sp.sellerName == null || sp.sellerName.isEmpty()) {
            sp.sellerName = guessSellerName(lines);
            System.out.println("[SHOP] sellerName.guess=" + safe(sp.sellerName));
        }

        return sp;
    }

    private String guessSellerName(List<String> lines) {
        int bizLabel = indexOfContains(lines, "판매자 사업자등록번호");
        if (bizLabel > 0) {
            for (int i = bizLabel - 1; i >= 0; i--) {
                String t = lines.get(i).trim();
                if (t.isEmpty()) continue;
                if (JUNK_LABELS.matcher(t).find()) continue;
                if (BIZNO_DASH.matcher(t).find()) continue;
                if (ORDER_NO.matcher(t).matches()) continue;
                if (APPROVAL_NO.matcher(t).matches()) continue;
                if (parseMoneyStrict(t) != null) continue;
                return t;
            }
        }
        return null;
    }

    private String findBizNo(List<String> lines) {
        String joined = String.join("\n", lines);
        Matcher m1 = BIZNO_DASH.matcher(joined);
        if (m1.find()) return m1.group(1);

        Matcher m2 = BIZNO_10.matcher(joined.replaceAll("[^0-9]", " "));
        if (m2.find()) {
            String d = m2.group(1);
            return d.substring(0, 3) + "-" + d.substring(3, 5) + "-" + d.substring(5);
        }
        return null;
    }

    private String normalizeBizNo(String s) {
        if (s == null) return null;
        String x = s.trim();
        if (x.isEmpty()) return null;

        Matcher m1 = BIZNO_DASH.matcher(x);
        if (m1.find()) return m1.group(1);

        String digits = x.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        }
        return null;
    }

    /* ========================= 카드금액 세팅 훅 ========================= */

    private void applyCardTotals(ReceiptResult r) {
        if (r == null || r.totals == null) return;
        if (r.totals.total == null) return;

        try {
            // ✅ 네 DTO에 맞게 여기서 카드금액 필드 채워
            // 예시(필드명이 다를 수 있음):
            // r.totals.totalCard = r.totals.total;
            // r.totals.card = r.totals.total;
            // r.totals.cash = 0;
            System.out.println("[TOTALS] applyCardTotals: total=" + safeInt(r.totals.total) + " (card totals hook)");
        } catch (Exception e) {
            System.out.println("[TOTALS] applyCardTotals error: " + e.getMessage());
        }
    }

    /* ========================= 결제정보 보조 ========================= */

    private String findApprovalAfterDateTime(List<String> lines) {
        int dtIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (DATE_TIME.matcher(lines.get(i)).find()) {
                dtIdx = i;
                break;
            }
        }
        if (dtIdx >= 0) {
            for (int j = dtIdx + 1; j < Math.min(lines.size(), dtIdx + 8); j++) {
                String t = lines.get(j).trim();
                if (APPROVAL_NO.matcher(t).matches()) return t;
            }
        }
        return findFirst(APPROVAL_NO, String.join("\n", lines));
    }

    private String normalizeInstallment(String picked, List<String> lines) {
        if (picked == null) return null;
        String x = picked.trim();
        if (x.equals("할부")) {
            for (String s : lines) {
                if (s.contains("개월")) return s.trim();
            }
            return null;
        }
        return x;
    }

    /* ========================= 공통 유틸 ========================= */

    private boolean isCoupangAppReceipt(String oneLine, String rawKeepNl) {
        boolean hasCoupay = oneLine.contains("쿠팡(쿠페이)");
        boolean hasMemo = oneLine.contains("거래메모");
        boolean hasCardUI = rawKeepNl.contains("카드영수증") || rawKeepNl.contains("결제정보") || rawKeepNl.contains("상품명");
        return hasCoupay && hasMemo && !hasCardUI;
    }

    private List<String> splitLines(String rawKeepNl) {
        String[] arr = rawKeepNl.split("\\R+");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            String t = s.replaceAll("\\s{2,}", " ").trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private int indexOfExact(List<String> lines, String exact) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(exact)) return i;
        }
        return -1;
    }

    private int indexOfContains(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) return i;
        }
        return -1;
    }

    private String valueAfterLabel(List<String> lines, String label) {
        for (int i = 0; i < lines.size() - 1; i++) {
            if (lines.get(i).trim().equals(label)) {
                String next = lines.get(i + 1).trim();
                if (next.isEmpty()) return null;
                if (next.contains("사업자등록번호") || next.contains("판매자주소")) return null;
                return next;
            }
        }
        return null;
    }

    // 판매자상호 추출: 3가지 패턴 순서대로 시도
    // 패턴1: 사업자등록번호 라벨 바로 앞 줄 (라벨 아닌 유효한 줄)
    // 패턴2: 사업자번호 값 바로 앞 줄 (라벨들 사이에 상호명이 끼어있는 경우)
    private String sellerNameAfterLabel(List<String> lines, String label) {
        int labelIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equals(label)) { labelIdx = i; break; }
        }
        if (labelIdx < 0) return null;

        // 사업자등록번호 라벨 위치
        int bizLabelIdx = -1;
        for (int j = labelIdx + 1; j < lines.size(); j++) {
            if (lines.get(j).trim().contains("사업자등록번호")) { bizLabelIdx = j; break; }
        }

        // 사업자번호 값 위치
        int bizValIdx = -1;
        int searchFrom = bizLabelIdx >= 0 ? bizLabelIdx + 1 : labelIdx + 1;
        for (int j = searchFrom; j < lines.size(); j++) {
            if (BIZNO_DASH.matcher(lines.get(j).trim()).matches()) { bizValIdx = j; break; }
        }

        // 패턴1: 사업자등록번호 라벨 바로 앞에서 역방향 탐색
        if (bizLabelIdx > labelIdx) {
            for (int j = bizLabelIdx - 1; j > labelIdx; j--) {
                String t = lines.get(j).trim();
                if (t.isEmpty()) continue;
                if (JUNK_LABELS.matcher(t).find()) continue;
                if (t.contains("사업자등록번호") || t.contains("판매자주소") || t.contains("판매자상호")) continue;
                if (ORDER_NO.matcher(t).matches()) continue;
                if (parseMoneyStrict(t) != null) continue;
                if (t.matches("[^가-힣A-Za-z0-9()\\[\\]]+")) continue;
                return t;
            }
        }

        // 패턴2: 사업자번호 값 바로 앞에서 역방향 탐색
        if (bizValIdx > 0) {
            for (int j = bizValIdx - 1; j > labelIdx; j--) {
                String t = lines.get(j).trim();
                if (t.isEmpty()) continue;
                if (JUNK_LABELS.matcher(t).find()) continue;
                if (t.contains("사업자등록번호") || t.contains("판매자주소") || t.contains("판매자상호")) continue;
                if (ORDER_NO.matcher(t).matches()) continue;
                if (parseMoneyStrict(t) != null) continue;
                if (t.matches("[^가-힣A-Za-z0-9()\\[\\]]+")) continue;
                return t;
            }
        }

        return null;
    }

    private String collectAfterLabelUntilNextLabel(List<String> lines, String label, Set<String> stopLabels) {
        int idx = indexOfContains(lines, label);
        if (idx < 0) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = idx + 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.isEmpty()) continue;

            boolean stop = false;
            for (String s : stopLabels) {
                if (t.contains(s)) { stop = true; break; }
            }
            if (stop) break;

            if (JUNK_LABELS.matcher(t).find()) continue;
            sb.append(t).append(" ");
        }
        String out = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return out.isEmpty() ? null : out;
    }

    private String findFirst(Pattern p, String text) {
        if (text == null) return null;
        Matcher m = p.matcher(text);
        return m.find() ? m.group(0).trim() : null;
    }

    private String pickFirstAmong(List<String> lines, List<String> keywords) {
        for (String line : lines) {
            for (String k : keywords) {
                if (line.contains(k)) return line.trim();
            }
        }
        return null;
    }

    private Integer firstPositive(Integer... arr) {
        for (Integer v : arr) if (v != null && v > 0) return v;
        return 1;
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        String x = s.replaceAll("\\s+", "").trim();
        if (x.contains("IBK") && x.contains("비씨")) return "IBK비씨카드";
        if (x.contains("BC")) return "BC카드";
        if (x.contains("KB") || x.contains("국민")) return "KB국민카드";
        if (x.contains("NH") || x.contains("농협")) return "NH농협카드";
        return s.trim();
    }

    protected Integer toInt(String s) {
        try {
            return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (!m.find()) return null;
            int g = Math.min(group, m.groupCount());
            String v = (g <= 0) ? m.group(0) : m.group(g);
            return v == null ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return null;
    }

    private void trySetMerchantBizNo(ReceiptResult r, String bizNo) {
        if (r == null || r.merchant == null) return;
        if (bizNo == null || bizNo.isEmpty()) return;

        // 가능한 필드명 후보들
        setIfFieldExists(r.merchant, List.of("bizNo", "biz_no", "businessNo", "business_no", "corpNo", "corp_no"), bizNo);
    }
    
    private void trySetMerchantAddress(ReceiptResult r, String addr) {
        if (r == null || r.merchant == null) return;
        if (addr == null || addr.isEmpty()) return;

        setIfFieldExists(r.merchant, List.of("address", "addr", "merchantAddr", "merchantAddress"), addr);
    }
    
    private void setIfFieldExists(Object target, List<String> fieldCandidates, String value) {
        Class<?> c = target.getClass();
        for (String fn : fieldCandidates) {
            try {
                var f = c.getDeclaredField(fn);
                f.setAccessible(true);

                // String 타입이면 그대로
                if (f.getType() == String.class) {
                    f.set(target, value);
                    return;
                }

                // 다른 타입이면 toString
                f.set(target, value);
                return;

            } catch (NoSuchFieldException ignore) {
                // 다음 후보
            } catch (Exception e) {
                System.out.println("[MERCHANT] set field failed: " + fn + " => " + e.getMessage());
            }
        }
    }

    /* ========================= ✅ 최종 결과 로그(최대한 자세히) ========================= */
    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");

        // Merchant
        System.out.println("[MERCHANT] name: " + safe(getMerchantName(r)));
        try {
            System.out.println("[MERCHANT] (reflection) " + reflectFields2(getMerchant(r)));
        } catch (Exception ignore) {}

        // Meta
        System.out.println("[META] receiptNo(orderNo): " + safe(getMetaReceiptNo(r)));
        System.out.println("[META] saleDate: " + safe(getMetaSaleDate(r)));
        System.out.println("[META] saleTime: " + safe(getMetaSaleTime(r)));
        try {
            System.out.println("[META] (reflection) " + reflectFields2(getMeta(r)));
        } catch (Exception ignore) {}

        // Payment
        System.out.println("[PAYMENT] type: " + safe(getPaymentType(r)));
        System.out.println("[PAYMENT] cardBrand: " + safe(getPaymentCardBrand(r)));
        System.out.println("[PAYMENT] cardMasked: " + safe(getPaymentCardMasked(r)));
        try {
            System.out.println("[PAYMENT] (reflection) " + reflectFields2(getPayment(r)));
        } catch (Exception ignore) {}

        // Approval
        System.out.println("[APPROVAL] approvalNo: " + safe(getApprovalNo(r)));
        try {
            System.out.println("[APPROVAL] (reflection) " + reflectFields2(getApproval(r)));
        } catch (Exception ignore) {}

        // Totals
        System.out.println("[TOTALS] total: " + safeInt(getTotalsTotal(r)));
        System.out.println("[TOTALS] taxable: " + safeInt(getTotalsTaxable(r)));
        System.out.println("[TOTALS] vat: " + safeInt(getTotalsVat(r)));
        System.out.println("[TOTALS] taxFree: " + safeInt(getTotalsTaxFree(r)));
        try {
            System.out.println("[TOTALS] (reflection) " + reflectFields2(getTotals(r)));
        } catch (Exception ignore) {}

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
                    System.out.println("    [ITEM reflection] " + reflectFields2(it));
                } catch (Exception ignore) {}
            }
        }

        // Root reflection
        try {
            System.out.println("[ROOT reflection] " + reflectFields2(r));
        } catch (Exception ignore) {}

        System.out.println("---------------------------------");
    }

    /* ===== null-safe accessors (ReceiptResult 구조가 public field인 전제) ===== */

    private Object getMerchant(ReceiptResult r) {
        return (r == null) ? null : r.merchant;
    }
    private String getMerchantName(ReceiptResult r) {
        return (r != null && r.merchant != null) ? r.merchant.name : null;
    }

    private Object getMeta(ReceiptResult r) {
        return (r == null) ? null : r.meta;
    }
    private String getMetaReceiptNo(ReceiptResult r) {
        return (r != null && r.meta != null) ? r.meta.receiptNo : null;
    }
    private String getMetaSaleDate(ReceiptResult r) {
        return (r != null && r.meta != null) ? r.meta.saleDate : null;
    }
    private String getMetaSaleTime(ReceiptResult r) {
        return (r != null && r.meta != null) ? r.meta.saleTime : null;
    }

    private Object getPayment(ReceiptResult r) {
        return (r == null) ? null : r.payment;
    }
    private String getPaymentType(ReceiptResult r) {
        return (r != null && r.payment != null) ? r.payment.type : null;
    }
    private String getPaymentCardBrand(ReceiptResult r) {
        return (r != null && r.payment != null) ? r.payment.cardBrand : null;
    }
    private String getPaymentCardMasked(ReceiptResult r) {
        return (r != null && r.payment != null) ? r.payment.cardMasked : null;
    }

    private Object getApproval(ReceiptResult r) {
        return (r == null) ? null : r.approval;
    }
    private String getApprovalNo(ReceiptResult r) {
        return (r != null && r.approval != null) ? r.approval.approvalNo : null;
    }

    private Object getTotals(ReceiptResult r) {
        return (r == null) ? null : r.totals;
    }
    private Integer getTotalsTotal(ReceiptResult r) {
        return (r != null && r.totals != null) ? r.totals.total : null;
    }
    private Integer getTotalsTaxable(ReceiptResult r) {
        return (r != null && r.totals != null) ? r.totals.taxable : null;
    }
    private Integer getTotalsVat(ReceiptResult r) {
        return (r != null && r.totals != null) ? r.totals.vat : null;
    }
    private Integer getTotalsTaxFree(ReceiptResult r) {
        return (r != null && r.totals != null) ? r.totals.taxFree : null;
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    /**
     * ✅ DTO 구조가 달라도 최대한 "있는 필드"를 다 찍기 위해 reflection 사용
     * - 접근 불가 필드는 skip
     * - 너무 긴 문자열은 잘라서 출력
     */
    private String reflectFields2(Object obj) {
        if (obj == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        try {
            var cls = obj.getClass();
            var fs = cls.getDeclaredFields();
            for (int i = 0; i < fs.length; i++) {
                var f = fs[i];
                try {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    String vs = String.valueOf(v);
                    if (vs.length() > 200) vs = vs.substring(0, 200) + "...";
                    sb.append(f.getName()).append("=").append(v == null ? "null" : vs);
                    if (i < fs.length - 1) sb.append(", ");
                } catch (Exception ignore) {
                    // skip
                }
            }
        } catch (Exception e) {
            return "{error=" + e.getMessage() + "}";
        }
        sb.append("}");
        return sb.toString();
    }
}
