package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;
import java.util.*;
import java.util.regex.*;

/**
 * CoupangReceiptParser v9.x
 * - 카드영수증 + 쿠팡앱 결제내역 자동 판별
 * - 다품목 분리, 수량 보정
 * - 쿠팡(쿠페이) 금액 최우선으로 총액 확정
 * - 쿠팡 특수 케이스(품목 금액이 따로 떨어진 레이아웃) 사후 보정
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

    /* 1️⃣ 쿠팡 앱 결제내역 */
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
        r.meta.receiptNo = extract(text, "(주문번호)\\s*[:：]?\\s*([0-9]{8,})", 2);

        // 거래메모 → 품목명
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

    /* 2️⃣ 카드영수증 */
    private ReceiptResult parseCardVersion(String text) {
        ReceiptResult r = new ReceiptResult();

        r.merchant.name = firstNonNull(
                extract(text, "(쿠팡\\(주\\)|쿠팡주식회사|쿠팡)"),
                "쿠팡"
        );

        r.payment.cardBrand = firstNonNull(
                extract(text, "(농협|하나|국민|신한|롯데|현대|BC|NH|KB)"),
                extract(text, "(농협카드|하나카드)")
        );

        r.payment.cardMasked = extract(text, "(\\d{4}\\*+\\d{2,4}\\*?\\d*)");
        r.payment.type = firstNonNull(
                extract(text, "(신용거래|현금거래|일시불|할부)"),
                "신용거래"
        );

        r.meta.receiptNo = extract(text, "(주문번호)\\s*[:：]?\\s*([0-9]{8,})", 2);
        r.approval.approvalNo = extract(text, "(승인번호)\\s*[:：]?\\s*([0-9]{6,12})", 2);

        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");

        // 세부 금액 (참고용)
        r.totals.taxable  = firstInt(text, "과세금액[^0-9]*([0-9,]+)");
        r.totals.vat      = firstInt(text, "부가세[^0-9]*([0-9,]+)");
        r.totals.taxFree  = firstInt(text, "비과세금액[^0-9]*([0-9,]+)");

        if (r.totals.taxable != null && !text.contains("부가세")) {
            r.totals.taxable = null;
        }

        // 총 결제액 우선 쿠팡(쿠페이)에서
        Integer grandTotalFromCoupay = null;
        {
            Matcher mPay = Pattern.compile(
                    "쿠팡\\(쿠페이\\)\\s*-?\\s*([0-9]{1,3}(?:,[0-9]{3})*)"
            ).matcher(text);
            if (mPay.find()) {
                grandTotalFromCoupay = toInt(mPay.group(1));
            }
        }

        // 보조 소스들 (이전 로직 그대로 유지)
        Integer grandTotalFromItems = null;
        {
            List<Integer> perItemTotals = new ArrayList<>();
            Matcher mItemTotals = Pattern.compile(
                    "(합계금액|총액|결제금액)[^0-9]{0,10}([0-9]{1,3}(?:,[0-9]{3})+)"
            ).matcher(text);

            while (mItemTotals.find()) {
                Integer v = toInt(mItemTotals.group(2));
                if (v != null) perItemTotals.add(v);
            }

            if (!perItemTotals.isEmpty()) {
                int sum = 0;
                for (Integer v : perItemTotals) sum += v;
                grandTotalFromItems = sum;
            }
        }

        Integer fallbackTotal = firstInt(
                text,
                "(합계금액|총액|결제금액)[^0-9]{0,10}([0-9]{1,3}(?:,[0-9]{3})+)"
        );
        if (fallbackTotal == null) {
            if (r.totals.taxFree != null && r.totals.taxFree > 0) {
                fallbackTotal = r.totals.taxFree;
            } else if (r.totals.taxable != null && r.totals.vat != null) {
                fallbackTotal = r.totals.taxable + r.totals.vat;
            }
        }

        // 최종 total
        r.totals.total = grandTotalFromCoupay;
        if (r.totals.total == null) {
            r.totals.total = firstNonNullInt(
                    grandTotalFromItems,
                    fallbackTotal
            );
        }

        // 품목 리스트 + 사후 보정
        r.items = parseCardItems(text, r.totals.total);

        return r;
    }

    /* 3️⃣ 품목 파서 + 사후 보정 */
    private List<Item> parseCardItems(String text, Integer totalAmount) {
        List<Item> list = new ArrayList<>();

        // 1. 전처리
        String[] lines = text.split("\\n|\\r|\\s{3,}");
        List<String> cleanLines = new ArrayList<>();
        for (String l : lines) {
            l = l.replaceAll("[^가-힣A-Za-z0-9,./()\\-원 ]", "").trim();
            if (!l.isEmpty()) cleanLines.add(l);
        }

        // 2. 상품 블록 분리
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

        // 3. 각 블록 파싱 → 일단 item.amount 채우기(현 방식)
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
            name = name.replaceAll("주문번호\\s*[0-9]{6,}", "")
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

            Integer itemTotal = null;
            Matcher mAmt = Pattern.compile("(합계금액|총액|결제금액)[^0-9]{0,10}([0-9,]+)").matcher(joined);
            if (mAmt.find()) {
                itemTotal = toInt(mAmt.group(2));
            } else {
                List<Integer> amounts = new ArrayList<>();
                String[] linesInBlock = joined.split("\\s{0,}\\b");

                for (String line : linesInBlock) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    Matcher mLine = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)\\s*원?").matcher(trimmed);
                    while (mLine.find()) {
                        Integer v = toInt(mLine.group(1));
                        if (v == null || v <= 500) continue;
                        if (trimmed.matches(".*(과세금액|비과세금액|부가세|합계금액|총액|결제금액|이용상점정보).*"))
                            continue;
                        if (totalAmount != null && v >= totalAmount * 0.9)
                            continue;
                        amounts.add(v);
                    }
                }

                if (!amounts.isEmpty()) {
                    itemTotal = Collections.max(amounts);
                } else {
                    itemTotal = totalAmount;
                }
            }

            Item it = new Item();
            it.name = name;
            it.qty = qty;
            it.amount = itemTotal;
            it.unitPrice = (qty > 0 ? itemTotal / qty : itemTotal);

            list.add(it);
        }

        // 4. "총 N건" 케이스 수량 보정 (너 기존 로직 유지)
        Matcher totalCount = Pattern.compile("총\\s*([0-9]+)\\s*건").matcher(text);
        if (totalCount.find() && !list.isEmpty()) {
            int n = toInt(totalCount.group(1));
            Item last = list.get(list.size() - 1);
            last.qty = n;
            last.unitPrice = (last.amount != null && n > 0) ? last.amount / n : last.amount;
        }

        // 5. ✅ 사후 보정 단계: 이 영수증처럼 item.amount 가 전부 totalAmount 로만 들어간 경우 교정
        //    5.1 영수증 전체에서 품목별 최종 금액 후보 뽑기
        List<Integer> finalItemAmounts = new ArrayList<>();

        // 패턴: 과세금액 ... 부가세 ... <최종>원
        {
            Pattern p = Pattern.compile(
                    "과세금액[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)\\s*원?" +
                            ".*?부가세[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)\\s*원?" +
                            ".*?([0-9]{1,3}(?:,[0-9]{3})+)\\s*원",
                    Pattern.DOTALL
            );
            Matcher m = p.matcher(text);
            while (m.find()) {
                Integer cand = toInt(m.group(3));
                if (cand != null) {
                    if (cand > 500 && (totalAmount == null || cand < totalAmount * 0.9)) {
                        finalItemAmounts.add(cand);
                    }
                }
            }
        }

        // 패턴: 합계금액 #####
        {
            Pattern p2 = Pattern.compile(
                    "합계금액[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)"
            );
            Matcher m2 = p2.matcher(text);
            while (m2.find()) {
                Integer cand = toInt(m2.group(1));
                if (cand != null) {
                    if (cand > 500 && (totalAmount == null || cand < totalAmount * 0.9)) {
                        finalItemAmounts.add(cand);
                    }
                }
            }
        }

        Collections.sort(finalItemAmounts); // ex [5,420, 13,560]

        //    5.2 각 아이템에 꽂아주기:
        int idx = 0;
        for (Item it : list) {
            boolean looksLikeFallback =
                    it.amount != null &&
                            totalAmount != null &&
                            Math.abs(it.amount - totalAmount) < (totalAmount * 0.2);

            if (looksLikeFallback && idx < finalItemAmounts.size()) {
                it.amount = finalItemAmounts.get(idx);
                if (it.qty != null && it.qty > 0) {
                    it.unitPrice = it.amount / it.qty;
                } else {
                    it.unitPrice = it.amount;
                }
                idx++;
            }
        }

        // 6. 아무것도 못 뽑았으면 마지막 fallback
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

    /* 유형 감지 */
    private boolean isCoupangAppReceipt(String text) {
        boolean hasCoupay = text.contains("쿠팡(쿠페이)");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = text.contains("카드영수증") || text.contains("구매정보");
        return hasCoupay && hasMemo && !hasCardReceipt;
    }

    /* 공통 유틸 */
    protected String extract(String text, String regex) { return extract(text, regex, 1); }
    protected String extract(String text, String regex, int group) {
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
}
