package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CoupangReceiptParser v10.x
 * - 카드영수증(결제정보/구매정보/이용상점정보) 라벨 기반 파싱 강화
 * - 판매자상호 우선 추출(쿠팡 고정 제거)
 * - 상품명 라벨 기반(줄바꿈 포함) 추출 → 아이템 안정화
 * - 카드종류(BC카드/IBK비씨카드 등) 더 정확히 추출
 */
public class CoupangReceiptParser extends BaseReceiptParser {

    @Override
    public ReceiptResult parse(Document doc) {
        String rawText = text(doc)
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll(" +", " ")
                .trim();

        System.out.println("=== 🧾 RAW TEXT (Coupang) ===");
        System.out.println(rawText);
        System.out.println("=================================");

        boolean isApp = isCoupangAppReceipt(rawText);
        System.out.println("🧭 인식된 유형: " + (isApp ? "쿠팡앱 결제내역" : "카드영수증"));

        ReceiptResult r = isApp ? parseAppVersion(rawText) : parseCardVersion(rawText);

        // ✅ 로그 출력
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");
        System.out.println("상호: " + safe(r.merchant.name));
        System.out.println("주문번호: " + safe(r.meta.receiptNo));
        System.out.println("거래일시: " + safe(r.meta.saleDate) + " " + safe(r.meta.saleTime));
        System.out.println("결제수단: " + safe(r.payment.type) + " / " + safe(r.payment.cardBrand));
        System.out.println("카드번호: " + safe(r.payment.cardMasked));
        System.out.println("승인번호: " + safe(r.approval.approvalNo));
        System.out.println("합계금액: " + safeInt(r.totals.total));
        System.out.println("과세금액: " + safeInt(r.totals.taxable) +
                " / 부가세: " + safeInt(r.totals.vat) +
                " / 비과세금액: " + safeInt(r.totals.taxFree));
        System.out.println("품목 수: " + (r.items != null ? r.items.size() : 0));
        if (r.items != null) {
            for (Item it : r.items) {
                System.out.println("  · " + safe(it.name)
                        + " | 수량:" + safe(it.qty)
                        + " | 금액:" + safeInt(it.amount));
            }
        }
        System.out.println("---------------------------------");
        return r;
    }

    /* ========================= 1) 쿠팡 앱 결제내역 ========================= */
    private ReceiptResult parseAppVersion(String text) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "쿠팡";

        String totalStr = extract(text, "쿠팡\\(쿠페이\\)\\s*[-]?([0-9,]+)원");
        if (totalStr == null) totalStr = extract(text, "(-?[0-9,]+)원");
        r.totals.total = toInt(totalStr);

        r.payment.cardBrand = firstNonNull(extract(text, "(쿠페이)"), extract(text, "(쿠팡페이)"));
        r.payment.type = "간편결제";
        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");
        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);

        String memoItem = firstNonNull(
                extract(text, "거래메모\\s*([가-힣A-Za-z0-9\\s:/,\\.]{2,30})"),
                extract(text, "([가-힣A-Za-z0-9]+\\s?(절단미역|쌀강정|세제|쿠키|강정|미역))")
        );

        Item it = new Item();
        it.name = (memoItem != null ? memoItem : "쿠팡 구매상품").trim();
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        return r;
    }

    /* ========================= 2) 카드영수증 ========================= */
    private ReceiptResult parseCardVersion(String text) {
        ReceiptResult r = new ReceiptResult();

        // ✅ (핵심) 판매자상호 우선 추출 (이용상점정보 영역)
        String sellerName = extractDot(text,
                "(?s)판매자상호\\s*([\\s\\S]*?)\\s*(판매자\\s*사업자등록번호|판매자주소|$)", 1);
        sellerName = cleanField(sellerName);

        // fallback: 쿠팡류/일반
        r.merchant.name = firstNonNull(
                notEmpty(sellerName) ? sellerName : null,
                extract(text, "(쿠팡\\(주\\)|쿠팡주식회사|쿠팡)"),
                "쿠팡"
        );

        // ✅ 카드사(카드종류) 라벨 기반 우선
        String cardType = extractDot(text,
                "(?s)카드종류\\s*([가-힣A-Za-z0-9\\s]*?카드)\\s*(거래종류|할부개월|카드번호|거래일시|승인번호|$)", 1);
        cardType = cleanField(cardType);

        r.payment.cardBrand = firstNonNull(
                notEmpty(cardType) ? cardType : null,
                extract(text, "(IBK비씨카드|IBK\\s*비씨카드|BC카드|비씨카드|BC)"),
                extract(text, "(농협|하나|국민|신한|롯데|현대|NH|KB)"),
                extract(text, "(농협카드|하나카드|국민카드|신한카드|롯데카드|현대카드)")
        );
        r.payment.cardBrand = normalizeCardBrand(r.payment.cardBrand);

        // 카드번호(마스킹)
        r.payment.cardMasked = firstNonNull(
                extract(text, "(\\d{4}\\*+\\d{2,6}\\*?\\d{0,6})"),
                extract(text, "(\\d{4}\\*{4,}\\d{3,4}\\*?)")
        );

        // 거래종류 라벨 기반 우선
        String tradeType = extractDot(text,
                "(?s)거래종류\\s*([가-힣A-Za-z0-9\\s]{2,20})\\s*(할부개월|카드번호|거래일시|승인번호|$)", 1);
        tradeType = cleanField(tradeType);

        r.payment.type = firstNonNull(
                notEmpty(tradeType) ? tradeType : null,
                extract(text, "(신용거래|현금거래|일시불|할부)"),
                "신용거래"
        );

        // 주문번호/승인번호/거래일시
        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);
        r.approval.approvalNo = extract(text, "(승인\\s*번호)\\s*[:：]?\\s*([0-9]{6,12})", 2);

        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");

        // 세부 금액
        r.totals.taxable  = firstInt(text, "과세금액[^0-9]*([0-9,]+)");
        r.totals.vat      = firstInt(text, "부가세[^0-9]*([0-9,]+)");
        r.totals.taxFree  = firstInt(text, "비과세금액[^0-9]*([0-9,]+)");

        if (r.totals.taxable != null && !text.contains("부가세")) {
            r.totals.taxable = null;
        }

        // ✅ 합계금액(=총 결제액) 라벨 우선
        Integer totalFromLabel = firstInt(text, "합계금액[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        if (totalFromLabel == null) {
            totalFromLabel = firstInt(text, "(총액|결제금액)[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        }

        // 기존 쿠페이 우선 로직(있을 때만)
        Integer grandTotalFromCoupay = null;
        {
            Matcher mPay = Pattern.compile("쿠팡\\(쿠페이\\)\\s*-?\\s*([0-9]{1,3}(?:,[0-9]{3})*)")
                    .matcher(text);
            if (mPay.find()) grandTotalFromCoupay = toInt(mPay.group(1));
        }

        Integer fallbackTotal = totalFromLabel;
        if (fallbackTotal == null) {
            if (r.totals.taxFree != null && r.totals.taxFree > 0) {
                fallbackTotal = r.totals.taxFree;
            } else if (r.totals.taxable != null && r.totals.vat != null) {
                fallbackTotal = r.totals.taxable + r.totals.vat;
            }
        }

        r.totals.total = firstNonNullInt(grandTotalFromCoupay, fallbackTotal);

        // ✅ 품목: "상품명 라벨 기반"을 최우선으로
        r.items = parseCardItemsV2_LabelFirst(text, r.totals.total);

        return r;
    }

    /**
     * 카드영수증 품목 파싱 (라벨 기반 우선)
     * - 상품명은 줄바꿈 포함해서 '과세금액/비과세금액/부가세/합계금액' 전까지 먹는다
     * - 금액은 totalAmount(합계금액) 사용 (스샷 포맷은 1품목 1합계)
     */
    private List<Item> parseCardItemsV2_LabelFirst(String text, Integer totalAmount) {
        // 1) 상품명 라벨 기반(가장 안정적)
        String product = extractDot(text,
                "(?s)상품명\\s*([\\s\\S]*?)\\s*(과세금액|비과세금액|부가세|합계금액|이용상점정보|$)", 1);
        product = cleanProductName(product);

        if (notEmpty(product)) {
            Item it = new Item();
            it.name = product;

            Integer qty = null;
            Matcher q1 = Pattern.compile("총\\s*([0-9]+)\\s*건").matcher(product);
            if (q1.find()) qty = toInt(q1.group(1));
            if (qty == null) {
                Matcher q2 = Pattern.compile("([0-9]+)\\s*개(?!\\s*포함)").matcher(product);
                if (q2.find()) qty = toInt(q2.group(1));
            }
            if (qty == null) qty = 1;

            it.qty = qty;
            it.amount = totalAmount;
            it.unitPrice = (qty != null && qty > 0 && totalAmount != null) ? (totalAmount / qty) : totalAmount;

            return List.of(it);
        }

        // 2) (fallback) 기존 블록 파서
        return parseCardItemsLegacy(text, totalAmount);
    }

    /* 기존 parseCardItems를 "레거시"로 남겨두는 fallback */
    private List<Item> parseCardItemsLegacy(String text, Integer totalAmount) {
        List<Item> list = new ArrayList<>();

        String[] lines = text.split("\\n|\\r|\\s{3,}");
        List<String> cleanLines = new ArrayList<>();
        for (String l : lines) {
            l = l.replaceAll("[^가-힣A-Za-z0-9,./()\\-원 ]", "").trim();
            if (!l.isEmpty()) cleanLines.add(l);
        }

        List<List<String>> blocks = new ArrayList<>();
        List<String> cur = null;

        for (String l : cleanLines) {
            if (l.contains("상품명")) {
                if (cur != null && !cur.isEmpty()) blocks.add(cur);
                cur = new ArrayList<>();
            } else if (l.matches(".*(합계금액|과세금액|비과세금액|부가세|총액|결제금액).*")) {
                if (cur != null && !cur.isEmpty()) {
                    blocks.add(cur);
                    cur = null;
                }
            } else if (cur != null) {
                cur.add(l);
            }
        }
        if (cur != null && !cur.isEmpty()) blocks.add(cur);

        for (List<String> block : blocks) {
            String joined = String.join(" ", block)
                    .replaceAll("\\s{2,}", " ")
                    .replaceAll("(쿠팡\\(쿠페이\\)|저장|확인|구매정보|이용상점정보).*", "")
                    .trim();
            if (joined.isEmpty()) continue;

            String name = joined;
            name = name.replaceAll(
                    "(과세금액|비과세금액|합계금액|부가세|총액|결제금액|" +
                            "거래정보|거래일시|거래내용|이용상점정보|구매정보|" +
                            "쿠팡\\(쿠페이\\)|저장|확인|검색|카드영수증).*", ""
            );
            name = name.replaceAll("주문\\s*번호\\s*[0-9]{6,}", "")
                    .replaceAll("\\b[0-9]{9,}\\b", "")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
            name = name.replaceAll("[,.:]+$", "").trim();
            name = name.replaceAll("[^가-힣A-Za-z0-9,()\\-\\s]", "").trim();

            Integer qty = null;
            Matcher q1 = Pattern.compile("총\\s*([0-9]+)\\s*건").matcher(joined);
            if (q1.find()) qty = toInt(q1.group(1));
            else {
                Matcher q2 = Pattern.compile("([0-9]+)\\s*개(?!\\s*포함)").matcher(joined);
                if (q2.find()) qty = toInt(q2.group(1));
            }
            if (qty == null) qty = 1;

            Item it = new Item();
            it.name = name;
            it.qty = qty;
            it.amount = totalAmount;
            Integer unitPrice = null;
            if (totalAmount != null && qty != null && qty > 0) {
                unitPrice = totalAmount / qty; // totalAmount != null 이면 오토언박싱 안전
            }
            it.unitPrice = unitPrice;
            list.add(it);
        }

        if (list.isEmpty()) {
            Item it = new Item();
            it.name = "쿠팡 상품";
            it.qty = 1;
            it.amount = totalAmount;
            it.unitPrice = totalAmount;
            list.add(it);
        }

        return list;
    }

    /* ========================= 유형 감지 ========================= */
    private boolean isCoupangAppReceipt(String text) {
        boolean hasCoupay = text.contains("쿠팡(쿠페이)");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = text.contains("카드영수증") || text.contains("구매정보");
        return hasCoupay && hasMemo && !hasCardReceipt;
    }

    /* ========================= 공통 유틸 ========================= */
    protected String extract(String text, String regex) { return extract(text, regex, 1); }
    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(Math.min(group, m.groupCount())).trim() : null;
        } catch (Exception e) { return null; }
    }

    // ✅ DOTALL 인라인 regex를 더 자주 쓰기 위해 별도 함수
    protected String extractDot(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(Math.min(group, m.groupCount())).trim() : null;
        } catch (Exception e) { return null; }
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    protected Integer toInt(String s) {
        try { return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return null; }
    }

    protected Integer firstInt(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return toInt(m.group(m.groupCount()));
        } catch (Exception ignore) {}
        return null;
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return null;
    }

    private Integer firstNonNullInt(Integer... nums) {
        for (Integer n : nums) {
            if (n != null && n > 0) return n;
        }
        return null;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String cleanField(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\u00A0]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String cleanProductName(String s) {
        if (s == null) return null;
        s = s.replaceAll("[\\u00A0]+", " ");
        s = s.replaceAll("\\s+", " ").trim();

        // 흔한 꼬리 제거
        s = s.replaceAll("(과세금액|비과세금액|부가세|합계금액).*", "").trim();
        s = s.replaceAll("(주문\\s*번호\\s*[:：]?\\s*[0-9]{8,}).*", "").trim();

        // 끝 특수문자 정리
        s = s.replaceAll("[,.:/\\-]+$", "").trim();
        return s;
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s+", "");
        // 표시 통일(원하면 더 추가)
        if (s.equalsIgnoreCase("BC")) return "BC카드";
        if (s.contains("비씨") && !s.endsWith("카드")) return s + "카드";
        if (s.equals("BC카드")) return "BC카드";
        if (s.equals("IBK비씨카드") || s.equals("IBK비씨카드카드")) return "IBK비씨카드";
        return s;
    }
}
