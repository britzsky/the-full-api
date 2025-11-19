package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;
import java.util.*;
import java.util.regex.*;

/**
 * 편의점 영수증 파서 (GS25 / CU / 세븐일레븐 등)
 * - 안내문 제외, 실제 품목 정확히 추출
 * - 카드/현금 결제정보 완전 대응
 * - 숫자 단독 줄을 품목으로 오인하지 않도록 상태기계 방식 적용
 */
public class ConvenienceReceiptParser extends BaseReceiptParser {

	// ==========================
    // 🔸 편의점 브랜드 타입 정의
    // ==========================
    private enum ConvenienceBrandType {
        GS25, CU, SEVEN, UNKNOWN
    }
	
    @Override
    public ReceiptResult parse(Document doc) {
    	// 🔍 OCR 전체 텍스트 획득
        String rawText = text(doc);

        // 🔎 브랜드 자동 감지
        ConvenienceBrandType brandType = detectBrandType(rawText);
        System.out.println("🏪 Detected Convenience Brand: " + brandType);

        // 🔧 브랜드별 파싱 분기
        switch (brandType) {
            case GS25:
                return parseGs25(doc, rawText);
            case CU:
                return parseCu(doc, rawText);
            case SEVEN:
                return parseSevenEleven(doc, rawText);
            default:
                System.out.println("⚠️ Unknown brand → defaulting to GS25 parser logic");
                return parseGs25(doc, rawText);
        }
    }
    
    // ==========================
    // 🔍 브랜드 감지
    // ==========================
    private ConvenienceBrandType detectBrandType(String text) {
        if (text == null) return ConvenienceBrandType.UNKNOWN;
        String t = text.toUpperCase();

        if (t.contains("GS25")) return ConvenienceBrandType.GS25;
        if (t.contains("CU")) return ConvenienceBrandType.CU;
        if (t.contains("7-ELEVEN") || t.contains("세븐일레븐")) return ConvenienceBrandType.SEVEN;
        return ConvenienceBrandType.UNKNOWN;
    }
    
    // ==========================
    // 🟣 CU 파싱 (현재는 GS25와 동일)
    // ==========================
    private ReceiptResult parseCu(Document doc, String rawText) {
        System.out.println("🔸 CU 파서 실행");
        ReceiptResult r = new ReceiptResult();

        // 1️⃣ 텍스트 정제
        String text = rawText
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?<=\\d)\\.(?=\\d{3}\\b)", ",")
                .replaceAll("(?<=CU)\\s+", "\n")
                .replaceAll("(?<=원)\\s+", "\n")
                .replaceAll("(?=총금액|면세|결제금액|신용카드|카드번호|승인번호)", "\n")
                .replaceAll(" +", " ")
                .trim();

        System.out.println("=== 🧾 NORMALIZED TEXT (CU) ===");
        System.out.println(text);
        System.out.println("==============================");

        // 2️⃣ 기본 정보
        r.merchant.name = safeExtract(text, "(CU\\s*[가-힣A-Za-z0-9]*점)", 1);
        r.merchant.address = safeExtract(text, "([가-힣]+시\\s*[가-힣]+구\\s*[가-힣0-9\\s]+\\d+번?)", 1);
        r.meta.saleDate = safeExtract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1);
        r.meta.saleTime = safeExtract(text, "([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 1);

        // 3️⃣ 품목 추출
        List<Item> items = new ArrayList<>();
        String[] lines = text.split("\\n");

        Pattern itemPattern = Pattern.compile("^[*]?[가-힣A-Za-z0-9()\\-\\s]+\\s+(\\d{1,3})\\s+([0-9,]{3,})$");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // ❌ 잡문 제거
            if (line.matches(".*(총|합계|면세|POS|식품선도유지|품목|구매액|결제금액).*")) continue;

            Matcher m = itemPattern.matcher(line);
            if (m.find()) {
                String name = line.replaceAll("\\s+\\d{1,3}\\s+[0-9,]{3,}", "").replace("*", "").trim();
                int qty = toInt(m.group(1));
                int amt = toInt(m.group(2));

                // 이름 필터
                if (name.length() < 2 || name.matches(".*(면세|합계|총액|결제|POS).*")) continue;

                Item it = new Item();
                it.name = name;
                it.qty = qty;
                it.amount = amt;
                it.unitPrice = amt / Math.max(1, qty);
                items.add(it);
            }
        }

        // ✅ 중복 제거 (같은 이름+금액 중복시 하나만)
        Set<String> seen = new HashSet<>();
        items.removeIf(i -> !seen.add(i.name + "|" + i.amount));

        r.items = items;

        // 4️⃣ 결제정보
        r.payment.type = "신용카드";
        r.payment.cardNo = safeExtract(text, "카드번호[:\\s]*([0-9\\-\\*xX]+)", 1);
        r.payment.cardBrand = safeExtract(text, "카드회사[:\\s]*[0-9]+\\s*([가-힣A-Za-z]+)", 1);
        r.payment.approvalAmt = safeExtract(text, "결제금액[:\\s]*([0-9,]+)", 1);
        r.approval.approvalNo = safeExtract(text, "승인번호[:\\s]*([0-9]{6,12})", 1);

        // 5️⃣ 총합
        r.totals.total = firstInt(text, "결제금액[:\\s]*([0-9,]+)");
        r.totals.vat = null;
        r.totals.taxFree = firstInt(text, "면세물품가액[:\\s]*([0-9,]+)");

        System.out.println("📋 품목 수: " + r.items.size());
        for (Item i : r.items)
            System.out.println("  → " + i.name + " | 수량:" + i.qty + " | 금액:" + i.amount);

        System.out.println("💳 카드: " + r.payment.cardBrand + " / " + r.payment.cardNo + " / 승인번호 " + r.approval.approvalNo);
        System.out.println("💰 결제금액: " + r.totals.total);

        return r;
    }
    
    // ==========================
    // 🟢 세븐일레븐 파싱 (현재는 GS25와 동일)
    // ==========================
    private ReceiptResult parseSevenEleven(Document doc, String rawText) {
        System.out.println("🟢 세븐일레븐 파서 실행");
        return parseGs25(doc, rawText); // 임시: 동일 로직
    }
    
    // ==========================
    // 🏪 GS25 파싱 로직 (기존 로직 그대로)
    // ==========================
    private ReceiptResult parseGs25(Document doc, String rawText) {
        ReceiptResult r = new ReceiptResult();

        // 1️⃣ OCR 텍스트 정제
        String text = rawText
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("(?<=\\d)\\.(?=\\d{3}\\b)", ",") // 4.800 → 4,800
                .replaceAll("(?<=점|마트|편의점)\\s+", "\n")
                .replaceAll("(?<=\\d)\\s+(?=[가-힣])", "\n")
                .replaceAll("(?<=[가-힣])\\s+(?=\\d{1,3}[.,]\\d{3})", "\n")
                .replaceAll("(?<=원)\\s+", "\n")
                .replaceAll("(?=과세|부가세|합계|총액|신용카드|현금|승인번호)", "\n")
                .replaceAll(" +", " ")
                .trim();

        System.out.println("=== 🧾 NORMALIZED TEXT ===");
        System.out.println(text);
        System.out.println("==========================");

        // 2️⃣ 점포/거래 정보
        r.merchant.name = firstNonNull(
                safeExtract(text, "(GS25\\s*[가-힣A-Za-z0-9]*점)", 1),
                safeExtract(text, "(CU\\s*[가-힣A-Za-z0-9]*점)", 1),
                safeExtract(text, "(세븐일레븐\\s*[가-힣A-Za-z0-9]*점)", 1)
        );
        r.merchant.address = safeExtract(text, "([가-힣]+시\\s*[가-힣]+구\\s*[가-힣0-9\\s]+\\d+번)", 1);

        r.meta.saleDate = safeExtract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1);
        r.meta.saleTime = safeExtract(text, "([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)", 1);

        System.out.println("📍 점포명: " + r.merchant.name);
        System.out.println("🕒 거래일시: " + r.meta.saleDate + " " + r.meta.saleTime);

        // 3️⃣ 품목 영역 컷팅
        String[] lines = text.split("\\n");
        int startIdx = findFirstItemLineIndex(lines);
        int endIdx   = findLineIndexBeforeTotals(lines);

        System.out.println("🔎 ITEM SECTION idx: start=" + startIdx + ", end=" + endIdx);
        StringBuilder itemSb = new StringBuilder();
        for (int i = startIdx; i < endIdx && i < lines.length; i++) {
            itemSb.append(lines[i]).append("\n");
        }
        String itemSection = itemSb.toString().trim();

        System.out.println("=== 📦 ITEM SECTION TEXT ===");
        System.out.println(itemSection);
        System.out.println("============================");

        // 4️⃣ 품목 파싱
        r.items = parseItems(itemSection);

        System.out.println("📋 추출된 품목 수: " + r.items.size());
        for (Item i : r.items)
            System.out.println("  → " + i.name + " | 수량: " + i.qty + " | 금액: " + i.amount);

        // 5️⃣ 합계/결제 정보
        r.totals.vat   = firstInt(text, "(부가세)\\s*([0-9,]+)");
        r.totals.total = firstInt(text, "(합계|총액|결제금액|계)\\s*([0-9,]+)");

        r.payment.type       = safeExtract(text, "(신용카드|현금|카카오페이|KB페이|네이버페이|토스페이|삼성페이)", 1);
        r.payment.cardBrand  = firstNonNull(
                safeExtract(text, "신용카드\\(([^)]+)\\)", 1),
                safeExtract(text, "\\(([^)]+)페이\\)", 1)
        );
        r.payment.cardNo     = safeExtract(text, "카드번호\\s*([0-9\\-\\*xX]+)", 1);
        r.payment.approvalAmt= firstNonNull(
                safeExtract(text, "사용금액\\s*([0-9,]+)원?", 1),
                safeExtract(text, "(결제금액)\\s*([0-9,]+)원?", 2)
        );
        r.payment.approvalTime = r.meta.saleTime;
        r.approval.approvalNo  = safeExtract(text, "승인번호\\s*([0-9]{6,12})", 1);
        r.payment.merchant     = safeExtract(text, "매입사[:：]\\s*([가-힣A-Za-z]+)", 1);

        System.out.println("💰 총액: " + r.totals.total + " / VAT: " + r.totals.vat);
        System.out.println("💳 유형: " + r.payment.type + ", 브랜드: " + r.payment.cardBrand);
        System.out.println("💳 카드번호: " + r.payment.cardNo + ", 승인번호: " + r.approval.approvalNo + ", 매입사: " + r.payment.merchant);
        System.out.println("💳 사용금액: " + r.payment.approvalAmt);

        return r;
    }
    
    // ---------- 품목 영역 시작/끝 탐지 ----------
    private int findFirstItemLineIndex(String[] lines) {
        int first = 0;
        for (int i = 0; i < lines.length; i++) {
            String cur = lines[i].trim();

            // "합계수량/금액" 등장 → 바로 위 줄이 품목 라인(예: 하이퍼트로피컬 1)
            if (cur.contains("합계수량") || cur.contains("수량/금액")) {
                if (i > 0) return i - 1;
            }
            // "합" 다음줄이 "계수량/금액"인 분리형
            if (cur.equals("합") && i + 1 < lines.length && lines[i + 1].contains("계수량")) {
                return Math.max(0, i - 1);
            }
            // "이름 수량" 꼴(= 최소 한글 + 공백 + 수량)
            if (cur.matches(".*[가-힣A-Za-z]+\\s+\\d{1,2}$")) {
                return i;
            }
        }
        return first;
    }

    private int findLineIndexBeforeTotals(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i];
            if (s.contains("과세") || s.contains("매출") || s.contains("부가세") || s.contains("신용카드")) {
                // 과세/부가세/신용카드 블록은 품목이 아님 → 그 직전까지
                return Math.max(0, i);
            }
        }
        return lines.length;
    }

    // ---------- 품목 파서(상태기계) ----------
    private List<Item> parseItems(String itemSection) {
        List<Item> items = new ArrayList<>();
        String[] ls = itemSection.split("\\n");

        // 유틸
        Pattern pNameQtyInline = Pattern.compile("^([가-힣A-Za-z0-9()\\-\\s]{2,}?)\\s+(\\d{1,2})$"); // "하이퍼트로피컬 1"
        Pattern pNameQtyAmt    = Pattern.compile("^([가-힣A-Za-z0-9()\\-\\s]{2,}?)\\s+(\\d{1,2})\\s+([0-9,]{3,})$"); // "이름 1 4,800"
        Pattern pNumber        = Pattern.compile("^[0-9,]{3,}$");

        for (int i = 0; i < ls.length; i++) {
            String line = ls[i].trim();
            if (line.isEmpty()) continue;
            if (line.contains("합계수량") || line.contains("수량/금액")) continue; // 표 머리글 제거

            // 1) "이름 수량 금액" 한 줄
            Matcher mAll = pNameQtyAmt.matcher(line);
            if (mAll.find()) {
                Item it = new Item();
                it.name   = mAll.group(1).trim();
                it.qty    = toInt(mAll.group(2));
                it.amount = toInt(mAll.group(3));
                it.unitPrice = it.amount / Math.max(1, it.qty);
                if (isValidItemName(it.name)) {
                    System.out.println("📦 [INLINE-3] " + it.name + " | " + it.qty + " | " + it.amount);
                    items.add(it);
                }
                continue;
            }

         // 2) "이름 수량" → 뒤에서 금액 찾아줌 (가장 큰 숫자 선택)
            Matcher mNameQty = pNameQtyInline.matcher(line);
            if (mNameQty.find()) {
                String name = mNameQty.group(1).trim();
                Integer qty = toInt(mNameQty.group(2));
                if (!isValidItemName(name)) continue;

                List<Integer> candidates = new ArrayList<>();
                int j = i + 1;
                while (j < ls.length) {
                    String nxt = ls[j].trim();
                    if (nxt.isEmpty()) { j++; continue; }
                    if (nxt.contains("과세") || nxt.contains("부가세") || nxt.contains("신용카드")) break;
                    // 다음 품목 신호면 중단
                    if (pNameQtyInline.matcher(nxt).find() || pNameQtyAmt.matcher(nxt).find()) break;
                    // 숫자 줄은 후보로 추가
                    if (pNumber.matcher(nxt).matches()) {
                        int val = toInt(nxt);
                        if (val >= 1000) candidates.add(val); // 너무 작은건 제외
                    }
                    j++;
                }
                if (!candidates.isEmpty()) {
                    int amount = Collections.max(candidates); // 가장 큰 숫자를 금액으로
                    Item it = new Item();
                    it.name = name;
                    it.qty = qty;
                    it.amount = amount;
                    it.unitPrice = amount / Math.max(1, qty);
                    System.out.println("📦 [NAME+QTY → PICK MAX] " + it.name + " | " + it.qty + " | " + it.amount);
                    items.add(it);
                }
                continue;
            }

            // 3) 그 외: 숫자만 있는 줄, 표 머릿글, 안내문 등은 무시
        }

        // 잡소리 제거
        items.removeIf(it ->
                it.name == null ||
                it.name.matches(".*(합계|총액|부가세|과세|결제|매출|수량/금액).*")
        );

        return items;
    }

    private boolean isValidItemName(String name) {
        if (name == null) return false;
        String n = name.trim();
        if (n.length() < 2) return false;
        if (n.matches("^[0-9,]+$")) return false;     // 숫자만
        if (n.matches(".*(시|구|동)\\s*\\d+번$")) return false; // 주소 꼬리
        // 안내문 키워드 배제
        if (n.matches(".*(정부방침|교환|환불|영수증|지참|카드결제|가능|일부상품|제외|합계수량).*")) return false;
        return true;
    }

    // ---------- 안전 extract ----------
    private String safeExtract(String text, String regex, int groupIndex) {
        if (text == null || regex == null) return null;
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (!m.find()) return null;
            int c = m.groupCount();
            if (c == 0) return m.group(0).trim();
            if (groupIndex <= c) return m.group(groupIndex).trim();
            return m.group(1).trim();
        } catch (Exception e) {
            System.err.println("⚠️ safeExtract error for [" + regex + "] → " + e.getMessage());
            return null;
        }
    }

    // ---------- 유틸 ----------
    protected Integer toInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.replaceAll("[^0-9]", "")); }
        catch (Exception e) { return null; }
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return null;
    }

    protected Integer firstInt(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return toInt(m.group(m.groupCount()));
        } catch (Exception ignore) {}
        return null;
    }
}
