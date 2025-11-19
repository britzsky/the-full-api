package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;
import java.util.*;
import java.util.regex.*;

/**
 * DeliveryReceiptParser v3.8
 * - "주문 메뉴" + "라이더님께" 섹션 통합 처리
 * - 메뉴 ↔ 가격 ↔ 수량 순서 불규칙 대응
 * - 노이즈(주문상세, 할인 등) 제거
 * - 라이더님께 중복 병합 방지
 */
public class DeliveryReceiptParser extends BaseReceiptParser {

    @Override
    public ReceiptResult parse(Document doc) {
        ReceiptResult r = new ReceiptResult();

        String text = text(doc)
                .replaceAll("[\\t\\x0B\\f\\r]+", "\n")
                .replaceAll(" {2,}", " ")
                .replaceAll("\n{2,}", "\n")
                .trim();

        System.out.println("=== 🛵 RAW TEXT (Delivery cleaned) ===");
        System.out.println(text);
        System.out.println("======================================");

        // ---------------- 상호명 ----------------
        r.merchant.name = firstNonNull(
                extract(text, "(배민|요기요|쿠팡이츠|배달의민족|파리바게뜨|파리바게트|던킨|스타벅스|맥도날드|롯데리아|도미노피자|버거킹|BHC|BBQ|교촌치킨)"),
                extract(text, "(가게명|상호명)\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 2)
        );

        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d)");

        // ---------------- 주문 메뉴 ~ 라이더님께 블록 ----------------
        String unifiedBlock = sliceBlock(
                text,
                "(주문 메뉴|주문메뉴|주문 내역|주문내역)",
                "(가게 사장님께|고객센터|ARS|카카오페이|전화번호|$)"
        );

        String riderBlock = sliceBlock(
                text,
                "(라이더님께|라이더에게)",
                "(가게 사장님께|고객센터|ARS|카카오페이|$)"
        );

        // 라이더님께 블록 병합 (중복 방지)
        if (riderBlock != null) {
            if (unifiedBlock == null) unifiedBlock = riderBlock;
            else if (!unifiedBlock.contains(riderBlock)) unifiedBlock += "\n" + riderBlock;
        }
        if (unifiedBlock == null) unifiedBlock = text;

        System.out.println("=== 🧾 UNIFIED BLOCK ===");
        System.out.println(unifiedBlock);
        System.out.println("==========================");

        // ---------------- 품목 파싱 ----------------
        List<Item> items = parseItemsFromBlock(unifiedBlock);
        r.items = items;

        // ---------------- 금액 정보 ----------------
        r.totals.subtotal = firstInt(text, "(메뉴금액|주문금액|상품금액)\\s*[:：]?\\s*([0-9,]+)");
        r.totals.cash     = firstInt(text, "(배달팁|라이더팁)\\s*[:：]?\\s*([0-9,]+)");
        r.totals.discount = firstInt(text, "(총 할인받은 금액|할인금액|배달팁 할인|쿠폰할인|할인)\\s*[:：]?\\s*[+\\-]?([0-9,]+)");
        r.totals.total    = firstInt(text, "(결제금액|총결제금액|합계금액)\\s*[:：]?\\s*([0-9,]+)");

        // ---------------- 결제수단 ----------------
        r.payment.cardBrand = firstNonNull(
                extract(text, "(카카오페이)"),
                extract(text, "(토스페이)"),
                extract(text, "(배민페이)"),
                extract(text, "(네이버페이)")
        );
        r.payment.type = firstNonNull(
                r.payment.cardBrand,
                extract(text, "(신용카드|현금|체크카드|카드결제|현금결제)")
        );

        // ---------------- 배달주소 ----------------
        String addr = sliceBlock(text, "(배달 주소|배달주소|배송지|주소)", "(결제|전화번호|고객센터|$)");
        if (addr != null) addr = addr.replaceAll("^(배달 주소|배달주소|배송지|주소)\\s*", "").trim();
        r.extra.put("배달주소", addr);

        // ---------------- 총합 보정 ----------------
        if (r.totals.total == null && r.totals.subtotal != null) {
            int total = r.totals.subtotal;
            if (r.totals.discount != null) total -= r.totals.discount;
            if (r.totals.cash != null) total += r.totals.cash;
            r.totals.total = total;
        }

     // ✅ 배달 주소 블록 전체
        String addrBlock = sliceBlock(text, "(배달 주소|배달주소|배송지|주소)", "(결제|전화번호|고객센터|$)");
        if (addrBlock != null) {
            addrBlock = addrBlock.replaceAll("^(배달 주소|배달주소|배송지|주소)\\s*", "").trim();
            
            // 도로명 / 지번 분리
            String roadAddr = extract(addrBlock, "\\(도로명\\)\\s*([가-힣A-Za-z0-9\\s\\-]+)");
            String lotAddr  = extract(addrBlock, "^(?!.*도로명)([가-힣A-Za-z0-9\\s\\-]+)");
            
            if (roadAddr != null) r.extra.put("도로명주소", roadAddr);
            if (lotAddr != null && (roadAddr == null || !lotAddr.contains(roadAddr))) 
                r.extra.put("지번주소", lotAddr);
            
            // 건물/층 정보
            String detailAddr = extract(addrBlock, "(지하|지상|[0-9]+층[가-힣]*)");
            if (detailAddr != null) r.extra.put("상세주소", detailAddr);

            r.extra.put("배달주소", addrBlock);
        }

        // ✅ 라이더님께 요청사항 / 배달 요청 메모
        String riderMsg = sliceBlock(text, "(라이더님께|라이더에게|배달 요청사항)", "(가게 사장님께|고객센터|$)");
        if (riderMsg != null && !riderMsg.isEmpty()) {
            riderMsg = riderMsg
                .replaceAll("^(라이더님께|라이더에게|배달 요청사항)\\s*", "")
                .replaceAll("[•·・▶\\-\\*]+", "")
                .trim();
            r.extra.put("배달요청", riderMsg);
        }

        // ✅ 가게 요청사항
        String storeMsg = sliceBlock(text, "(가게 사장님께|가게 사장에게|가게에 전달)", "(라이더님께|고객센터|$)");
        if (storeMsg != null && !storeMsg.isEmpty()) {
            storeMsg = storeMsg
                .replaceAll("^(가게 사장님께|가게 사장에게|가게에 전달)\\s*", "")
                .replaceAll("[•·・▶\\-\\*]+", "")
                .trim();
            r.extra.put("가게요청", storeMsg);
        }

        // ✅ 결제정보 추가 (카드 / 금액 / 할인)
        String payBlock = sliceBlock(text, "(결제 정보|결제정보|결제금액|카카오페이|배민페이|쿠팡이츠페이)", "(배달 주소|배달주소|배송지|주소|고객센터|$)");
        if (payBlock != null) {
            String payMethod = extract(payBlock, "(카카오페이|토스페이|배민페이|네이버페이|신용카드|체크카드|현금)");
            String payAmt = extract(payBlock, "결제금액\\s*[:：]?\\s*([0-9,]+)원?");
            String discountAmt = extract(payBlock, "(할인금액|총 할인받은 금액)\\s*[:：]?\\s*[\\+\\-]?([0-9,]+)원?");
            if (payMethod != null) r.extra.put("결제수단", payMethod);
            if (payAmt != null) r.extra.put("결제금액", payAmt);
            if (discountAmt != null) r.extra.put("할인금액", discountAmt);
        }
        
        // ✅ 날짜 정보 추출 (주문/결제/배달일자)
        String dateBlock = sliceBlock(text, "(주문일자|결제일|배달일자|배달예정|배송일|픽업일|출고일|수령일|[0-9]{1,2}월\\s*[0-9]{1,2}일)", "(결제정보|고객센터|전화번호|$)");
        if (dateBlock != null) {
            // 주문일자 / 결제일자 / 배달일자 분리
            String orderDate = extract(dateBlock, "(주문일자|주문일)\\s*[:：]?\\s*([0-9./\\-년월일\\s:]+)", 2);
            String payDate   = extract(dateBlock, "(결제일자|결제일|결제시간)\\s*[:：]?\\s*([0-9./\\-년월일\\s:]+)", 2);
            String deliDate  = extract(dateBlock, "(배달일자|배달예정|배송일|픽업일|출고일|수령일)\\s*[:：]?\\s*([0-9./\\-년월일\\s:]+)", 2);
            
            // 형식 보정: "10월 10일(금)" → "2025-10-10"
            if (orderDate == null)
                orderDate = extract(text, "([0-9]{1,2})월\\s*([0-9]{1,2})일");
            if (orderDate != null && !orderDate.contains("20")) {
                orderDate = normalizeDate(orderDate);
            }
            
            if (orderDate != null) r.extra.put("주문일자", orderDate);
            if (payDate != null) r.extra.put("결제일자", payDate);
            if (deliDate != null) r.extra.put("배달일자", deliDate);
        }
        
        // ---------------- 디버깅용 항목 ----------------
        if (r.totals.cash != null) {
            Item tip = new Item(); tip.name = "배달팁"; tip.unitPrice = r.totals.cash; tip.qty = 1; tip.amount = r.totals.cash; items.add(tip);
        }
        if (r.totals.discount != null) {
            Item disc = new Item(); disc.name = "할인"; disc.unitPrice = r.totals.discount; disc.qty = 1; disc.amount = r.totals.discount; items.add(disc);
        }

        // ---------------- 결과 요약 ----------------
        System.out.println("------ ✅ 파싱 결과 요약 ------");
        System.out.println("상호명: " + safe(r.merchant.name));
        System.out.println("품목 수: " + items.size());
        for (Item it : items)
            System.out.println(" · " + it.name + " | 단가:" + safeInt(it.unitPrice) + " x" + safeInt(it.qty) + " = " + safeInt(it.amount));
        System.out.println("--------------------------------");

        return r;
    }

    private List<Item> parseItemsFromBlock(String block) {
        List<Item> list = new ArrayList<>();
        if (block == null) return list;

        String[] lines = block.split("\\n+");
        String lastMenuName = null;

        for (int i = 0; i < lines.length; i++) {
            String line = normalizeLine(lines[i]);
            if (line.isEmpty()) continue;

            // ❌ 노이즈 필터링
            if (line.matches(".*(무료배달|할인|아낄 수 있었어요|주문상세|결제금액|파리바게뜨).*")) continue;
            if (line.matches(".*(\\d{1,2}월|\\d{1,2}일|월요일|화요일|수요일|목요일|금요일|토요일|일요일).*")) continue;
            if (line.matches(".*(층|호|도로명|지하|지상|건물|식당|요양원|아파트|호점|마트|점).*")) continue;

            // 1️⃣ 메뉴명만 있는 줄 기억
            if (line.matches("^[가-힣A-Za-z0-9\\s\\(\\)/.-]+$") && !line.contains("가격")) {
                lastMenuName = line.trim();
            }

            // 2️⃣ 가격줄 처리
            Matcher priceLine = Pattern.compile("가격[:：]?\\s*\\(?([0-9,]+)원\\)?").matcher(line);
            if (priceLine.find()) {
                Integer price = toInt(priceLine.group(1));
                boolean matched = false;

                // 아래쪽 10줄 탐색 (메뉴가 늦게 등장하는 경우 대응)
                for (int k = i + 1; k < lines.length && k <= i + 10; k++) {
                    String next = normalizeLine(lines[k]);
                    if (next.matches("^[가-힣A-Za-z0-9\\s\\(\\)/.-]+$") &&
                        !next.contains("가격") &&
                        !next.contains("주문상세") &&
                        !next.contains("결제") &&
                        !next.matches(".*(\\d{1,2}월|\\d{1,2}일|요일).*") &&
                        !next.matches(".*(층|호|도로명|지하|지상|건물|식당|요양원|아파트|호점|마트|점).*")) {

                        if (next.contains("cm") || next.contains("클래식") || next.contains("초코")) {
                            addItemIfNotExists(list, next, price);
                            matched = true;
                            break;
                        }
                    }
                }

                // 그래도 못찾으면 위쪽 5줄 탐색
                if (!matched) {
                    for (int k = i - 1; k >= 0 && k >= i - 5; k--) {
                        String prev = normalizeLine(lines[k]);
                        if (prev.matches("^[가-힣A-Za-z0-9\\s\\(\\)/.-]+$") &&
                            !prev.contains("가격") &&
                            !prev.contains("주문상세") &&
                            !prev.contains("결제") &&
                            !prev.matches(".*(\\d{1,2}월|\\d{1,2}일|요일).*") &&
                            !prev.matches(".*(층|호|도로명|지하|지상|건물|식당|요양원|아파트|호점|마트|점).*")) {

                            if (prev.contains("cm") || prev.contains("클래식") || prev.contains("초코")) {
                                addItemIfNotExists(list, prev, price);
                                matched = true;
                                break;
                            }
                        }
                    }
                }

                // fallback: 마지막 메뉴명 (단, 주소류 제외)
                if (!matched && lastMenuName != null &&
                    !lastMenuName.matches(".*(층|호|도로명|지하|지상|건물|식당|요양원|아파트|호점|마트|점).*")) {
                    addItemIfNotExists(list, lastMenuName, price);
                    lastMenuName = null;
                }
                continue;
            }

            // 3️⃣ “숫자원 1개” 형태 (예: "33,400원 1개")
            Matcher inlinePrice = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+)원\\s*([0-9]{1,2})개").matcher(line);
            if (inlinePrice.find()) {
                Integer price = toInt(inlinePrice.group(1));
                Integer qty = toInt(inlinePrice.group(2));
                if (!list.isEmpty()) {
                    Item last = list.get(list.size() - 1);
                    if (last.unitPrice == null) {
                        last.unitPrice = price;
                        last.qty = qty;
                        last.amount = price * qty;
                    }
                }
                continue;
            }

            // 4️⃣ 수량 줄 ("... 1개")
            Matcher itemStart = Pattern.compile("^([가-힣A-Za-z0-9\\s\\(\\)/.-]+?)\\s*([0-9]{1,2})\\s*개$").matcher(line);
            if (itemStart.find()) {
                String name = itemStart.group(1).trim();
                Integer qty = toInt(itemStart.group(2));

                Optional<Item> existing = list.stream().filter(x -> x.name.contains(name)).findFirst();
                if (existing.isPresent()) {
                    Item it = existing.get();
                    it.qty = qty;
                    if (it.unitPrice != null) it.amount = it.unitPrice * qty;
                } else {
                    Item it = new Item();
                    it.name = name;
                    it.qty = qty;
                    list.add(it);
                }
                continue;
            }
        }
        
        // ✅ 5️⃣ 동일 이름 중 중복 정리 (단가 높은 항목만 유지)
        Map<String, Item> unique = new LinkedHashMap<>();
        for (Item it : list) {
            if (!unique.containsKey(it.name)) {
                unique.put(it.name, it);
            } else {
                Item exist = unique.get(it.name);
                // 단가가 더 크면 갱신 (예: 33,400원 → 39,900원)
                if (it.unitPrice != null && (exist.unitPrice == null || it.unitPrice > exist.unitPrice)) {
                    unique.put(it.name, it);
                }
            }
        }
        list = new ArrayList<>(unique.values());
        
        return list;
    }

    // ✅ 중복 방지용 헬퍼
    private void addItemIfNotExists(List<Item> list, String name, Integer price) {
        if (list.stream().noneMatch(x -> x.name.equals(name) && Objects.equals(x.unitPrice, price))) {
            Item it = new Item();
            it.name = name;
            it.unitPrice = price;
            it.qty = 1;
            it.amount = price;
            list.add(it);
        }
    }

    private void finalizeAmount(Item it, List<Item> list) {
        if (it.qty == null || it.qty == 0) it.qty = 1;
        if (it.unitPrice != null) it.amount = it.unitPrice * it.qty;
        list.add(it);
    }

    // =====================================================
    // 🔹 공통 유틸
    // =====================================================
    private String sliceBlock(String text, String startRegex, String endRegex) {
        Pattern pStart = Pattern.compile(startRegex);
        Matcher ms = pStart.matcher(text);
        if (!ms.find()) return null;
        int start = ms.start();
        Pattern pEnd = Pattern.compile(endRegex);
        Matcher me = pEnd.matcher(text);
        int end = text.length();
        while (me.find()) {
            if (me.start() > start) { end = me.start(); break; }
        }
        return text.substring(start, end).trim();
    }

    private String normalizeLine(String line) {
        return line == null ? "" : line.replaceAll("^[•·・>▶\\-\\*]+\\s*", "").trim();
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

    protected String extract(String text, String regex) { return extract(text, regex, 1); }
    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(Math.min(group, m.groupCount())).trim() : null;
        } catch (Exception e) { return null; }
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.isEmpty()) return s;
        return null;
    }
    
    // ✅ 날짜 형식 보정 ("10월 9일" → "2025-10-09")
    private String normalizeDate(String raw) {
        try {
            Matcher m = Pattern.compile("([0-9]{1,2})월\\s*([0-9]{1,2})일").matcher(raw);
            if (m.find()) {
                int month = Integer.parseInt(m.group(1));
                int day = Integer.parseInt(m.group(2));
                Calendar cal = Calendar.getInstance();
                int year = cal.get(Calendar.YEAR);
                return String.format("%04d-%02d-%02d", year, month, day);
            }
        } catch (Exception ignore) {}
        return raw;
    }
}
