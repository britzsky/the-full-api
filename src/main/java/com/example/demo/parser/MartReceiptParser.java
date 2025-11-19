package com.example.demo.parser;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import com.google.cloud.documentai.v1.Document;

public class MartReceiptParser extends BaseReceiptParser {

    @Override
    public ReceiptResult parse(Document doc) {
        ReceiptResult r = new ReceiptResult();

        // 1️⃣ 텍스트 정규화
        String t = text(doc)
                .replace("㎏", "kg").replace("㎖", "ml").replace("ℓ", "L")
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("[^가-힣A-Za-z0-9.,:/()\\-#*=_\\n ]", " ")
                .replaceAll(" +", " ")
                .trim();

        // 2️⃣ 줄 복원
        List<String> lines = rebuildLines(t);
        System.out.println("=== 🧾 NORMALIZED LINES ===");
        lines.forEach(System.out::println);

        // 3️⃣ 섹션 분리
        List<List<String>> sections = splitByLogicalSections(lines);
        List<String> merchantSection = sections.size() > 0 ? sections.get(0) : Collections.emptyList();
        List<String> itemSection     = sections.size() > 1 ? sections.get(1) : Collections.emptyList();
        List<String> totalSection    = sections.size() > 2 ? sections.get(2) : Collections.emptyList();
        List<String> footerSection   = sections.size() > 3 ? sections.get(3) : Collections.emptyList();

        // 4️⃣ 머천트 정보
        String merchantText = String.join("\n", merchantSection);
        r.merchant.name = firstNonNull(
                extract(merchantText, "([가-힣A-Za-z\\s]*?식자재마트|[가-힣A-Za-z\\s]*?마트|베이커리|뚜레쥬르|파리바게뜨)", 1),
                extract(merchantText, "가맹점명[:：]\\s*([^\\n]*)")
        );
        r.merchant.bizNo   = extract(merchantText, "([0-9]{3}-[0-9]{2}-[0-9]{5})");
        r.merchant.tel     = extract(merchantText, "(0\\d{1,2}-\\d{3,4}-\\d{4})");
        r.merchant.address = extract(merchantText, "(서울|인천|부산|대구|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)[^\\n]*\\d[^\\n]*");

        // 5️⃣ 메타정보
        r.meta.saleDate = firstNonNull(
                extract(t, "(?:일시|판매일)[:：]?\\s*((?:20)?\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})", 1),
                extract(t, "(?:20)?\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}")
        );
        r.meta.saleTime  = extract(t, "(?:일시|판매일)[^\\n]*?([01]?\\d|2[0-3]):([0-5]\\d)");
        r.meta.receiptNo = firstNonNull(
                extract(t, "거래\\s?NO[:：]?\\s*([0-9]{8,20})", 1),
                extract(t, "거래NO[:：]?\\s*([0-9]{8,20})", 1)
        );

        // 6️⃣ 품목 파싱
        System.out.println("=== 📦 ITEM SECTION (" + itemSection.size() + " lines) ===");
        itemSection.forEach(System.out::println);
        r.items.addAll(parseItems(itemSection));

        // 7️⃣ 합계/결제/고객 정보
        String combinedTotals = String.join(" ", totalSection) + " " + String.join(" ", footerSection);
        fillTotalsAndPayment(combinedTotals, r);
        fillCustomerAndApproval(combinedTotals, r);
        fillAccountInfo(combinedTotals, r);

        // 8️⃣ 후처리
        postFixTotals(r);
        r.extra.put("item_count", r.items.size());
        return r;
    }

    // -------------------- 섹션 분리 --------------------
    private List<List<String>> splitByLogicalSections(List<String> lines) {
        List<List<String>> sections = new ArrayList<>();
        List<String> current = new ArrayList<>();
        String phase = "merchant";
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (phase.equals("merchant") && trimmed.matches(".*(NO\\.|상품명|단가|수량|금액).*")) {
                sections.add(new ArrayList<>(current)); current.clear(); phase = "items";
            } else if (phase.equals("items") && trimmed.matches(".*(합계|총액|할인|면세|부가세|VAT|현금|카드).*")) {
                sections.add(new ArrayList<>(current)); current.clear(); phase = "totals";
            } else if (phase.equals("totals") && trimmed.matches(".*(고객|적립|승인|영수증|거래NO|감사|계산원).*")) {
                sections.add(new ArrayList<>(current)); current.clear(); phase = "footer";
            }
            current.add(trimmed);
        }
        if (!current.isEmpty()) sections.add(current);
        return sections;
    }

    // -------------------- 영수증 타입 감지 --------------------
    private enum ReceiptPatternType { NUMBERED, TWO_LINE_NUMBERED, INLINE, SPLIT }

    private ReceiptPatternType detectPattern(List<String> lines) {
        boolean hasNoHeader = lines.stream().anyMatch(l -> l.matches(".*\\bNO\\.?\\b.*상품명.*"));
        boolean hasTwoLinePattern = false;

        for (int i = 0; i < lines.size() - 2; i++) {
            String cur = lines.get(i).trim();
            String next = lines.get(i + 1).trim();
            String after = (i + 2 < lines.size()) ? lines.get(i + 2).trim() : "";

            boolean isNameLine = cur.matches("^\\d{1,3}\\s+[가-힣A-Za-z(].*");
            boolean isHeader = next.matches(".*(단가|수량|금액).*");
            boolean isBarcode = next.matches("^\\d{8,13}$") || after.matches("^\\d{8,13}$");
            boolean isPriceLine = after.matches("^(\\d{1,3}(?:,\\d{3})*)\\s+\\d{1,2}\\s+(\\d{1,3}(?:,\\d{3})*)(?:\\s*#)?$")
                                || (i + 3 < lines.size() && lines.get(i + 3).trim()
                                .matches("^(\\d{1,3}(?:,\\d{3})*)\\s+\\d{1,2}\\s+(\\d{1,3}(?:,\\d{3})*)(?:\\s*#)?$"));

            if (isNameLine && isPriceLine && (isBarcode || isHeader)) {
                hasTwoLinePattern = true;
                break;
            }
        }

        if (hasTwoLinePattern) {
            System.out.println("🟧 Pattern detected: TWO_LINE_NUMBERED");
            return ReceiptPatternType.TWO_LINE_NUMBERED;
        }

        // ✅ 일반 번호형
        boolean hasInlinePrices = lines.stream().anyMatch(l ->
                l.matches("^\\d{1,3}\\s+[가-힣A-Za-z].*(\\d{1,3}(?:,\\d{3})*)\\s+\\d{1,2}\\s+(\\d{1,3}(?:,\\d{3})*)"));
        if (hasNoHeader || hasInlinePrices) {
            System.out.println("🟩 Pattern detected: NUMBERED");
            return ReceiptPatternType.NUMBERED;
        }

        boolean hasInline = lines.stream().anyMatch(l ->
                l.matches("^[가-힣A-Za-z].*(\\d{1,3}(?:,\\d{3})*)\\s+(\\d{1,2})\\s+(\\d{1,3}(?:,\\d{3})*)"));
        if (hasInline) {
            System.out.println("🟦 Pattern detected: INLINE");
            return ReceiptPatternType.INLINE;
        }

        System.out.println("⬜ Pattern detected: SPLIT");
        return ReceiptPatternType.SPLIT;
    }

    // -------------------- 품목 파싱 분기 --------------------
    private List<Item> parseItems(List<String> lines) {
        System.out.println("\n=== 🔍 ITEM PARSING START ===");
        List<String> clean = lines.stream()
                .filter(l -> !l.matches(".*(NO\\.|상품명|단가|수량|금액).*"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        ReceiptPatternType type = detectPattern(lines);
        System.out.println("📄 Detected Type: " + type);

        List<Item> items = new ArrayList<>();
        switch (type) {
            case NUMBERED -> items.addAll(parseNumberedItems(clean));
            case TWO_LINE_NUMBERED -> items.addAll(parseTwoLineNumberedItems(clean));
            case INLINE -> items.addAll(parseInlineItems(clean));
            case SPLIT -> items.addAll(parseSplitItems(clean));
        }

        System.out.println("=== ✅ ITEM PARSING DONE (" + items.size() + "개) ===");
        return items;
    }

    // -------------------- TYPE 1: 번호형 --------------------
    private List<Item> parseNumberedItems(List<String> lines) {
        List<Item> items = new ArrayList<>();
        Pattern nextItemStart = Pattern.compile("^(\\d{1,3}\\s+)?[가-힣A-Za-z(]");

        for (int i = 0; i < lines.size(); i++) {
            String nameLine = lines.get(i).trim();
            if (nameLine.matches("^\\d{1,3}\\s+.*"))
                nameLine = nameLine.replaceFirst("^\\d{1,3}\\s+", "").trim();
            if (!nameLine.matches("^[가-힣A-Za-z(].*")) continue;

            Item it = new Item();
            it.name = nameLine;
            int j = i + 1;
            List<String> buf = new ArrayList<>();

            while (j < lines.size()) {
                String s = lines.get(j).trim();
                if (s.isEmpty()) { j++; continue; }
                if (nextItemStart.matcher(s).find()) break;
                buf.add(s);
                j++;
            }

            // ✅ 숫자 8~14자리 (바코드)는 제거
            buf.removeIf(s -> s.matches("^\\d{8,14}$"));

            for (String s : buf) {
                String clean = s.replaceAll("[^0-9,]", "");
                if (clean.isEmpty()) continue;
                Integer val = toInt(clean);
                if (val == null) continue;
                if (s.matches("^\\d{1,2}$") && it.qty == null) it.qty = val;
                else if (val > 1000 && it.unitPrice == null) it.unitPrice = val;
                else if (val > 1000 && it.amount == null) it.amount = val;
            }

            Matcher inline = Pattern.compile("(\\d{1,3}(?:,\\d{3})*)\\s+(\\d{1,2})\\s+(\\d{1,3}(?:,\\d{3})*)").matcher(nameLine);
            if (inline.find()) {
                it.unitPrice = it.unitPrice != null ? it.unitPrice : toInt(inline.group(1));
                it.qty = it.qty != null ? it.qty : toInt(inline.group(2));
                it.amount = it.amount != null ? it.amount : toInt(inline.group(3));
            }

            it.taxFlag = String.join(" ", buf).contains("#") ? "면세" : "과세";
            if (it.amount == null && it.unitPrice != null && it.qty != null)
                it.amount = it.unitPrice * it.qty;

            items.add(it);
            i = j - 1;
        }

        System.out.println("=== 🧾 PARSED ITEMS (NUMBERED) ===");
        for (Item it : items)
            System.out.printf("📦 %s | 단가:%s | 수량:%s | 금액:%s | %s%n",
                    it.name, it.unitPrice, it.qty, it.amount, it.taxFlag);
        return items;
    }

    // -------------------- TYPE 2: 인라인형 --------------------
    private List<Item> parseInlineItems(List<String> lines) {
        List<Item> items = new ArrayList<>();
        for (String line : lines) {
            Matcher m = Pattern.compile("^(.*?)(\\d{1,3}(?:,\\d{3})*)\\s+(\\d{1,2})\\s+(\\d{1,3}(?:,\\d{3})*)").matcher(line);
            if (m.find()) {
                Item it = new Item();
                it.name = m.group(1).trim();
                it.unitPrice = toInt(m.group(2));
                it.qty = toInt(m.group(3));
                it.amount = toInt(m.group(4));
                it.taxFlag = line.contains("#") ? "면세" : "과세";
                items.add(it);
            }
        }
        System.out.println("=== 🧾 PARSED ITEMS (INLINE) ===");
        for (Item it : items)
            System.out.printf("📦 %s | 단가:%s | 수량:%s | 금액:%s | %s%n",
                    it.name, it.unitPrice, it.qty, it.amount, it.taxFlag);
        return items;
    }

    // -------------------- TYPE 3: SPLIT (마트형 단가→수량→금액 구조 전용) --------------------
    private List<Item> parseSplitItems(List<String> lines) {
        List<Item> items = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<List<String>> numericBlocks = new ArrayList<>();
        List<String> currentNums = new ArrayList<>();

        // 1️⃣ 상품명 / 숫자 블록 분리
        for (String l : lines) {
            String line = l.trim();
            if (line.isEmpty()) continue;

            // 상품명 라인
            if (line.matches("^[가-힣A-Za-z(].*")) {
                if (!currentNums.isEmpty()) {
                    numericBlocks.add(new ArrayList<>(currentNums));
                    currentNums.clear();
                }
                names.add(line);
            }
            // 숫자 블록 (단가, 수량, 금액)
            else if (line.matches("^[0-9,\\-#* ]+$")) {
                currentNums.add(line);
            }
        }
        if (!currentNums.isEmpty()) numericBlocks.add(new ArrayList<>(currentNums));

        // 2️⃣ 각 숫자 블록 파싱
        Pattern priceSet = Pattern.compile("(\\d{1,3}(?:,\\d{3})*)");
        List<Item> parsed = new ArrayList<>();

        for (List<String> block : numericBlocks) {
            List<Integer> nums = new ArrayList<>();

            for (String s : block) {
                Matcher m = priceSet.matcher(s);
                while (m.find()) {
                    Integer v = toInt(m.group(1));
                    if (v != null && v > 0) nums.add(v);
                }
            }

            if (nums.isEmpty()) continue;

            // 단가 → 수량 → 금액 순서로 해석
            if (nums.size() >= 3) {
                for (int i = 0; i + 2 < nums.size(); i += 3) {
                    Item it = new Item();
                    it.unitPrice = nums.get(i);
                    it.qty = nums.get(i + 1);
                    it.amount = nums.get(i + 2);
                    parsed.add(it);
                }
            } 
            // (단가, 금액)만 있는 경우 → 수량 1로 간주
            else if (nums.size() == 2) {
                Item it = new Item();
                it.unitPrice = nums.get(0);
                it.qty = 1;
                it.amount = nums.get(1);
                parsed.add(it);
            } 
            // 금액만 있는 경우
            else if (nums.size() == 1) {
                Item it = new Item();
                it.amount = nums.get(0);
                parsed.add(it);
            }
        }

        // 3️⃣ 상품명 ↔ 숫자세트 매칭
        int count = Math.min(names.size(), parsed.size());
        for (int i = 0; i < count; i++) {
            Item base = parsed.get(i);
            base.name = names.get(i);

            base.taxFlag = (lines.stream().anyMatch(l -> l.contains("#"))) ? "면세" : "과세";

            // 수량 보정 (단가 < 금액인데 수량이 null인 경우 1로 간주)
            if (base.qty == null && base.unitPrice != null && base.amount != null && base.amount > base.unitPrice)
                base.qty = 1;

            // 금액 보정
            if (base.amount == null && base.unitPrice != null && base.qty != null)
                base.amount = base.unitPrice * base.qty;

            items.add(base);
        }

        // 남은 상품 처리
        for (int i = count; i < names.size(); i++) {
            Item it = new Item();
            it.name = names.get(i);
            it.taxFlag = "과세";
            items.add(it);
        }

        // 4️⃣ 디버그 출력
        System.out.println("=== 🧾 PARSED ITEMS (마트형 SPLIT) ===");
        for (Item it : items)
            System.out.printf("📦 %s | 단가:%s | 수량:%s | 금액:%s | %s%n",
                    it.name, it.unitPrice, it.qty, it.amount, it.taxFlag);
        return items;
    }
    
    // -------------------- TYPE: 두 줄 번호형 (진안식자재마트 전용) --------------------
    private List<Item> parseTwoLineNumberedItems(List<String> lines) {
        List<Item> items = new ArrayList<>();
        Pattern startLine = Pattern.compile("^\\d{1,3}\\s+.*");
        Pattern barcode = Pattern.compile("^\\d{8,13}$");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!startLine.matcher(line).find()) continue;

            Item it = new Item();
            it.name = line.replaceFirst("^\\d{1,3}\\s+", "").trim();
            
            if (i + 1 < lines.size() && lines.get(i + 1).matches(".*(단가|수량|금액).*")) {
                i++; // 헤더 줄 건너뛰기
            }
            
            // 다음 줄이 바코드인 경우 건너뛰기
            if (i + 1 < lines.size() && barcode.matcher(lines.get(i + 1)).find()) {
                i++;
            }

            // 단가/수량/금액이 그 다음 줄
            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1);
                Matcher m = Pattern.compile("(\\d{1,3}(?:,\\d{3})*)\\s+(\\d{1,2})\\s+(\\d{1,3}(?:,\\d{3})*)").matcher(next);
                if (m.find()) {
                    it.unitPrice = toInt(m.group(1));
                    it.qty = toInt(m.group(2));
                    it.amount = toInt(m.group(3));
                }
            }

            it.taxFlag = (line.contains("#") ? "면세" : "과세");
            items.add(it);
        }

        System.out.println("=== 🧾 PARSED ITEMS (TWO_LINE_NUMBERED) ===");
        for (Item it : items)
            System.out.printf("📦 %s | 단가:%s | 수량:%s | 금액:%s | %s%n",
                    it.name, it.unitPrice, it.qty, it.amount, it.taxFlag);
        return items;
    }
    
    // -------------------- 결제/고객/계좌 --------------------
    private void fillTotalsAndPayment(String t, ReceiptResult r) {
        r.totals.discount = firstInt(t, "(할인금액|할인)[:：]?\\s*(-?[0-9,]+)");
        r.totals.total    = firstInt(t, "(합 ?계|총 ?액|지불금액|내신금액|결제금액)[:：]?\\s*([0-9,]+)");
        boolean hasCard = t.contains("카드");
        boolean hasCash = t.contains("현금");
        if (hasCard) {
            r.payment.type = "card";
            r.payment.cardBrand   = extract(t, "(국민|하나|신한|롯데|BC|삼성|현대) ?카드");
            r.payment.approvalAmt = extract(t, "(승인금액|전표금액|일시불)[:：]?\\s*([0-9,]+)", 2);
        } else if (hasCash) {
            r.payment.type = "cash";
            r.payment.approvalAmt = extract(t, "(현금지불|현금영수증|내신금액|지출증빙)[:：]?\\s*([0-9,]+)", 2);
        }
    }

    private void fillCustomerAndApproval(String t, ReceiptResult r) {
        r.customer.nameOrGroup  = extract(t, "(고객|요양원|전강)[:：]?\\s*([가-힣A-Za-z0-9()]+)", 2);
        r.customer.pointReceived = firstInt(t, "(받은포인트|적립포인트)[:：]?\\s*([0-9,]+)");
        r.customer.pointBalance  = firstInt(t, "(현재포인트|잔여포인트)[:：]?\\s*([0-9,]+)");
        r.approval.approvalNo    = extract(t, "\\(([0-9]{6,9})\\)");
        r.approval.cashReceiptNo = extract(t, "(현금영수증승인|지출증빙)[:：]?/?\\s*([0-9\\-]{5,12})", 2);
    }

    private void fillAccountInfo(String t, ReceiptResult r) {
        String acc = extract(t, "(국민|농협|신한|우리|하나|기업|우체국|수협|새마을|부산|대구|광주|전북|경남)[^0-9\\n]*(\\d{2,3}-\\d{3,4}-\\d{3,4}-\\d{1,3}|\\d{3}-\\d{2,4}-\\d{5,6})");
        if (acc != null) r.extra.put("account_info", acc);
    }

    private void postFixTotals(ReceiptResult r) {
        for (Item it : r.items)
            if (it.amount == null && it.unitPrice != null && it.qty != null)
                it.amount = it.unitPrice * it.qty;
        if (r.totals.total == null && !r.items.isEmpty())
            r.totals.total = r.items.stream().filter(i -> i.amount != null).mapToInt(i -> i.amount).sum();
        if (r.payment.approvalAmt == null && r.totals.total != null)
            r.payment.approvalAmt = String.valueOf(r.totals.total);
    }

    private List<String> rebuildLines(String text) {
        return Arrays.stream(text.split("\\n+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
