package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOffice Daiso 영수증 파서
 *
 * - 인식 대상: 다이소 오프라인 POS 영수증(열전사 영수증)
 * - 핵심 추출: 거래일자, 부가세, 합계, 상품명, 업체명, 사업자등록번호, 가맹점번호
 * - 포맷 특성: 상품명이 줄바꿈되거나 상품코드([12345]) 라인이 끼어드는 케이스 대응
 */
public class HeadOfficeDaisoReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    private static final Pattern SALE_DATE = Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})");
    private static final Pattern SALE_TIME = Pattern.compile("([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)");
    private static final Pattern DATE_TIME = Pattern.compile(
            "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})\\s*([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)"
    );

    private static final Pattern MONEY = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*)");
    private static final Pattern APPROVAL_NO = Pattern.compile("\\b([0-9]{6,12})\\b");
    private static final Pattern MASKED_CARD = Pattern.compile("\\b([0-9]{4,8}\\*{2,}[0-9\\*]{2,})\\b");
    private static final Pattern BIZNO_DASH = Pattern.compile("\\b(\\d{3}-\\d{2}-\\d{5})\\b");
    private static final Pattern BIZNO_10 = Pattern.compile("\\b(\\d{10})\\b");
    private static final Pattern DAISO_MARKER = Pattern.compile("(아\\s*성\\s*다\\s*이\\s*소|다\\s*이\\s*소)");

    @Override
    public ReceiptResult parse(Document doc) {
        // OCR 원문을 줄 단위로 다루기 위해 개행을 유지한 정규화 텍스트를 만든다.
        String raw = text(doc);
        if (raw == null) raw = "";

        String normalized = normalizeKeepNewlines(raw);
        List<String> lines = splitLines(normalized);

        if (DEBUG) {
            System.out.println("=== 🧾 RAW TEXT (HeadOffice Daiso Slip) ===");
            System.out.println(normalized);
            System.out.println("==========================================");
        }

        ReceiptResult r = new ReceiptResult();

        /* ========================= 1) 카드/승인 정보 ========================= */
        r.payment.cardBrand = firstNonNull(
                firstMatch(normalized, "(비씨카드|BC카드|국민카드|신한카드|삼성카드|현대카드|우리카드|롯데카드|농협카드|하나카드)"),
                "비씨카드"
        );

        String cardMaskedRaw = firstPattern(MASKED_CARD, normalized);
        r.payment.cardMasked = normalizeCardMasked(cardMaskedRaw);
        r.payment.cardNo = r.payment.cardMasked;
        r.payment.type = "신용카드";

        r.approval.approvalNo = firstNonNull(
                firstMatch(normalized, "(?m)승인번호\\s*[:：]?\\s*([0-9]{6,12})"),
                findApprovalNearCard(lines)
        );

        /* ========================= 2) 거래일시 ========================= */
        String saleDateRaw = null;
        String saleTimeRaw = null;

        Matcher dtm = DATE_TIME.matcher(normalized);
        if (dtm.find()) {
            saleDateRaw = dtm.group(1);
            saleTimeRaw = dtm.group(2);
        }
        if (!notEmpty(saleDateRaw)) {
            saleDateRaw = firstMatch(normalized, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        }
        if (!notEmpty(saleTimeRaw)) {
            saleTimeRaw = firstMatch(normalized, "([0-2]?\\d:[0-5]\\d(?::[0-5]\\d)?)");
        }

        r.meta.saleDate = normalizeDate(saleDateRaw);
        r.meta.saleTime = normalizeTime(saleTimeRaw);

        /* ========================= 3) 금액(과세/부가세/합계) ========================= */
        // 금액은 라벨 기준으로 우선 추출하고, 누락 시 합계/부가세 관계식으로 보정한다.
        Integer taxable = firstNonNullInt(
                findAmountByLabel(lines, "과\\s*세\\s*합\\s*계"),
                findAmountByLabel(lines, "공급가액")
        );
        Integer vat = firstNonNullInt(
                findAmountByLabel(lines, "부\\s*가\\s*가\\s*치\\s*세"),
                findAmountByLabel(lines, "부\\s*가\\s*세(?:\\s*액)?")
        );
        Integer total = firstNonNullInt(
                findAmountByLabel(lines, "판\\s*매\\s*합\\s*계"),
                findAmountByLabel(lines, "신용카드"),
                findAmountByLabel(lines, "승인금액")
        );
        boolean taxExemptBusiness = containsTaxExemptBusiness(normalized);

        if (total == null && taxable != null && vat != null) {
            total = taxable + vat;
        }
        if (taxable == null && total != null && vat != null) {
            taxable = Math.max(total - vat, 0);
        }
        if (vat == null && taxExemptBusiness) {
            vat = 0;
        }
        if (vat == null && total != null && taxable != null) {
            vat = Math.max(total - taxable, 0);
        }

        r.totals.taxable = taxable;
        r.totals.vat = vat;
        r.totals.total = total;
        r.totals.taxFree = (vat != null && vat == 0) ? firstNonNullInt(total, taxable, 0) : 0;
        r.payment.approvalAmt = (total == null) ? null : String.valueOf(total);

        /* ========================= 4) 업체 정보 ========================= */
        // 다이소 전표로 판별되면 사용처는 항상 "다이소"로 고정(요청사항 반영)
        boolean daisoDetected = isDaisoSlip(normalized);
        String merchantRaw = parseMerchantName(lines);
        if (daisoDetected) {
            // 요청사항: 다이소 전표는 사용처를 "다이소"로 고정
            r.merchant.name = "다이소";
            if (notEmpty(merchantRaw)) {
                r.extra.put("merchant_raw_name", merchantRaw);
            }
        } else {
            r.merchant.name = firstNonNull(merchantRaw, "다이소");
        }

        // 사업자번호는 "사업자:" 라벨 값을 최우선으로 보고, 실패하면 전체 텍스트 fallback.
        r.merchant.bizNo = firstNonNull(
                firstMatch(normalized, "(?im)사업자\\s*[:：]?\\s*(\\d{3}-\\d{2}-\\d{5})"),
                formatBizNo(firstMatch(normalized, "(?im)사업자\\s*[:：]?\\s*(\\d{10})")),
                normalizeBizNo(normalized)
        );
        // 가맹점번호는 POS/NI 키워드 인접 숫자를 사용한다.
        r.approval.merchantNo = firstNonNull(
                firstMatch(normalized, "(?im)\\[?\\s*POS\\s*([0-9]{5,10})\\s*[-\\]]?"),
                firstMatch(normalized, "(?im)\\bNI\\s*\\*{0,6}\\s*([0-9]{4,10})\\b")
        );

        r.merchant.address = firstNonNull(
                firstMatch(normalized, "(?m)(?:주소|매장)\\s*[:：]\\s*([^\\n\\r]{4,120})"),
                findAddressLike(lines)
        );
        r.merchant.tel = firstNonNull(
                firstMatch(normalized, "(?m)(?:전화|TEL|고객만족실|고객만족센터)\\s*[:：]?\\s*([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})"),
                firstMatch(normalized, "([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})")
        );

        /* ========================= 5) 상품명(상세 아이템) ========================= */
        String taxFlag = (vat != null && vat == 0) ? "면세" : "과세";
        List<Item> parsedItems = parseItems(lines, total, taxFlag);
        if (parsedItems.isEmpty()) {
            Item fallback = new Item();
            fallback.name = firstNonNull(findFirstProductLikeLine(lines), "상품");
            fallback.qty = 1;
            fallback.amount = total;
            fallback.unitPrice = total;
            fallback.taxFlag = taxFlag;
            parsedItems = List.of(fallback);
        }
        r.items = parsedItems;

        // 요약영역 인식 실패 시, 품목 금액 합계로 총액을 보정한다.
        if (r.totals.total == null) {
            Integer itemSum = sumItemAmounts(parsedItems);
            if (itemSum != null && itemSum > 0) {
                r.totals.total = itemSum;
                r.payment.approvalAmt = String.valueOf(itemSum);
                if (r.totals.vat != null && r.totals.taxable == null) {
                    r.totals.taxable = Math.max(itemSum - r.totals.vat, 0);
                }
            }
        }

        if (!notEmpty(r.meta.receiptNo)) {
            // 영수증 번호/바코드 번호가 길게 찍히는 경우 fallback 저장
            r.meta.receiptNo = firstMatch(normalized, "(?m)^\\s*(\\d{16,22})\\s*$");
        }

        if (DEBUG) {
            printDebugResult(r);
        }

        return r;
    }

    /**
     * 상품영역에서 품목을 "행 단위"로 분리해 Item 리스트를 만든다.
     * - [상품코드] 라인은 품목 경계로 사용
     * - "상품명 단가 수량 금액" 한 줄 완성형은 즉시 1개 품목으로 확정
     * - 그 외는 블록으로 묶어(상품명 라인 + 금액 라인) 1개 품목으로 조합
     */
    private List<Item> parseItems(List<String> lines, Integer total, String taxFlag) {
        List<Item> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return out;

        int start = findStartIndex(lines);
        int end = findEndIndex(lines, start);
        if (start < 0 || end <= start) return out;

        Pattern itemInline = Pattern.compile(
                "^(.*?[가-힣A-Za-z].*?)\\s+([0-9]{1,3}(?:,[0-9]{3})*)\\s+([0-9]{1,3})\\s+([0-9]{1,3}(?:,[0-9]{3})*)$"
        );
        Pattern amountTriple = Pattern.compile(
                "^([0-9]{1,3}(?:,[0-9]{3})*)\\s+([0-9]{1,3})\\s+([0-9]{1,3}(?:,[0-9]{3})*)$"
        );

        List<String> block = new ArrayList<>();
        for (int i = start + 1; i < end; i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (isSummaryLine(line)) break;

            // 코드 라인이 나오면 이전 블록을 1개 품목으로 확정한다.
            if (isCodeLine(line)) {
                addIfMeaningful(out, buildItemFromBlock(block, itemInline, amountTriple, taxFlag));
                block.clear();
                continue;
            }

            // "상품명 단가 수량 금액" 한 줄 완성형은 즉시 품목으로 저장한다.
            Item inline = parseInlineItemLine(line, itemInline, taxFlag);
            if (inline != null) {
                addIfMeaningful(out, buildItemFromBlock(block, itemInline, amountTriple, taxFlag));
                block.clear();
                out.add(inline);
                continue;
            }

            // 블록에는 상품명 후보/금액트리플 후보만 넣어 노이즈를 줄인다.
            if (looksLikeProductLine(line) || amountTriple.matcher(line).find()) {
                block.add(line);
            }
        }

        // 마지막 블록도 처리
        addIfMeaningful(out, buildItemFromBlock(block, itemInline, amountTriple, taxFlag));

        if (out.isEmpty() && total != null) {
            Item fallback = new Item();
            fallback.name = firstNonNull(findFirstProductLikeLine(lines), "상품");
            fallback.qty = 1;
            fallback.amount = total;
            fallback.unitPrice = total;
            fallback.taxFlag = taxFlag;
            out.add(fallback);
        }

        return out;
    }

    private Item parseInlineItemLine(String line, Pattern itemInline, String taxFlag) {
        if (!notEmpty(line) || itemInline == null) return null;
        Matcher m = itemInline.matcher(line);
        if (!m.find()) return null;

        Item it = new Item();
        it.name = cleanItemName(m.group(1));
        it.unitPrice = toInt(m.group(2));
        it.qty = toInt(m.group(3));
        it.amount = toInt(m.group(4));
        it.taxFlag = taxFlag;
        return notEmpty(it.name) ? it : null;
    }

    private Item buildItemFromBlock(List<String> block, Pattern itemInline, Pattern amountTriple, String taxFlag) {
        if (block == null || block.isEmpty()) return null;

        String name = null;
        Integer unitPrice = null;
        Integer qty = null;
        Integer amount = null;

        // 1) 블록 안에 완성형 라인이 있으면 해당 라인을 우선 사용
        for (String raw : block) {
            String line = cleanField(raw);
            if (!notEmpty(line)) continue;
            Matcher mInline = itemInline.matcher(line);
            if (mInline.find()) {
                name = cleanItemName(mInline.group(1));
                unitPrice = toInt(mInline.group(2));
                qty = toInt(mInline.group(3));
                amount = toInt(mInline.group(4));
                break;
            }
        }

        // 2) 상품명이 비면 첫 상품명 후보 라인을 사용
        if (!notEmpty(name)) {
            for (String raw : block) {
                String line = cleanField(raw);
                if (!looksLikeProductLine(line)) continue;
                name = cleanItemName(line);
                if (notEmpty(name)) break;
            }
        }

        // 3) 금액트리플(단가 수량 합계) 라인 보완
        if (unitPrice == null || qty == null || amount == null) {
            for (String raw : block) {
                String line = cleanField(raw);
                Matcher mTriple = amountTriple.matcher(line);
                if (!mTriple.find()) continue;
                unitPrice = firstNonNullInt(unitPrice, toInt(mTriple.group(1)));
                qty = firstNonNullInt(qty, toInt(mTriple.group(2)));
                amount = firstNonNullInt(amount, toInt(mTriple.group(3)));
                break;
            }
        }

        if (amount == null && unitPrice != null && qty != null) {
            amount = unitPrice * qty;
        }
        if (qty == null) qty = 1;
        if (unitPrice == null && amount != null && qty > 0) {
            unitPrice = amount / qty;
        }

        if (!notEmpty(name) && amount == null && unitPrice == null) {
            return null;
        }

        Item it = new Item();
        it.name = firstNonNull(name, "상품");
        it.unitPrice = unitPrice;
        it.qty = qty;
        it.amount = amount;
        it.taxFlag = taxFlag;
        return it;
    }

    private void addIfMeaningful(List<Item> out, Item it) {
        if (out == null || it == null) return;
        if (!notEmpty(it.name) && it.amount == null && it.unitPrice == null) return;
        out.add(it);
    }

    private Integer sumItemAmounts(List<Item> items) {
        if (items == null || items.isEmpty()) return null;
        int sum = 0;
        boolean hasValue = false;
        for (Item it : items) {
            if (it == null || it.amount == null) continue;
            sum += it.amount;
            hasValue = true;
        }
        return hasValue ? sum : null;
    }

    /**
     * 상품영역 시작 지점 탐색
     * - 1순위: POS 라인
     * - 2순위: 거래일시 라인
     */
    private int findStartIndex(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (line.toUpperCase().contains("POS")) return i;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (DATE_TIME.matcher(line).find()) return i;
        }
        return -1;
    }

    // 상품영역 종료는 합계/결제 요약 라벨이 시작되는 첫 라인으로 본다.
    private int findEndIndex(List<String> lines, int start) {
        for (int i = Math.max(0, start + 1); i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (isSummaryLine(line)) return i;
        }
        return lines.size();
    }

    // 합계/결제 라인 여부(공백 흔들림 대응을 위해 공백 제거 후 검사)
    private boolean isSummaryLine(String line) {
        if (!notEmpty(line)) return false;
        String compact = line.replaceAll("\\s+", "");
        return compact.contains("과세합계")
                || compact.contains("판매합계")
                || compact.contains("신용카드")
                || compact.contains("부가가치세")
                || compact.contains("부가세")
                || compact.contains("승인금액");
    }

    // 상품코드 단독 라인([12345])은 상품명으로 취급하지 않는다.
    private boolean isCodeLine(String line) {
        if (!notEmpty(line)) return false;
        return line.matches("^\\[\\s*[0-9]{4,8}\\s*\\]$")
                || line.matches("^\\[?\\s*[0-9]{4,8}\\s*-?\\s*\\]?$");
    }

    // 상품명 후보 필터: 숫자-only/교환안내/고객센터 안내 문구 제외
    private boolean looksLikeProductLine(String line) {
        if (!notEmpty(line)) return false;
        if (line.matches("^[0-9,\\s]+$")) return false;
        if (line.contains("교환") || line.contains("환불") || line.contains("고객만족")) return false;
        return line.matches(".*[가-힣A-Za-z].*");
    }

    // Item 파싱 실패 시 첫 상품명 후보를 fallback 이름으로 사용한다.
    private String findFirstProductLikeLine(List<String> lines) {
        int start = findStartIndex(lines);
        int end = findEndIndex(lines, start);
        for (int i = Math.max(0, start + 1); i < end; i++) {
            String line = cleanField(lines.get(i));
            if (!looksLikeProductLine(line)) continue;
            if (isCodeLine(line)) continue;
            return cleanItemName(line);
        }
        return null;
    }

    /**
     * 라벨 기반 금액 추출
     * - 라벨과 같은 줄에서 먼저 찾고
     * - 없으면 다음 1~2줄에서 찾는다.
     */
    private Integer findAmountByLabel(List<String> lines, String labelRegex) {
        if (lines == null || !notEmpty(labelRegex)) return null;
        Pattern label = Pattern.compile("(?i)" + labelRegex);
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (!label.matcher(line).find()) continue;

            Integer inLine = lastMoney(line);
            if (inLine != null) return inLine;

            for (int j = i + 1; j < lines.size() && j <= i + 2; j++) {
                String next = cleanField(lines.get(j));
                if (!notEmpty(next)) continue;
                Integer v = lastMoney(next);
                if (v != null) return v;
            }
        }
        return null;
    }

    /**
     * 상호명 추출
     * - 상단 20줄 내 "아성다이소/다이소" 키워드 탐색
     * - 바로 다음 줄이 "xx점"이면 지점명으로 병합
     */
    private String parseMerchantName(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;

        int scanLimit = Math.min(lines.size(), 20);
        int idx = -1;
        for (int i = 0; i < scanLimit; i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (line.contains("아성다이소")) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            for (int i = 0; i < scanLimit; i++) {
                String line = cleanField(lines.get(i));
                if (!notEmpty(line)) continue;
                if (line.contains("다이소") && !line.contains("멤버십") && !line.contains("홈페이지")) {
                    idx = i;
                    break;
                }
            }
        }
        if (idx < 0) return null;

        String name = cleanMerchantLine(lines.get(idx));
        if (idx + 1 < lines.size()) {
            String next = cleanMerchantLine(lines.get(idx + 1));
            if (notEmpty(next) && next.endsWith("점") && (!notEmpty(name) || !name.contains(next))) {
                name = notEmpty(name) ? (name + " " + next) : next;
            }
        }
        return cleanField(name);
    }

    // 광고/멤버십/홈페이지 안내 문구를 상호명에서 제거한다.
    private String cleanMerchantLine(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replace("\"", "")
                .replace("“", "")
                .replace("”", "")
                .replace("‘", "")
                .replace("’", "")
                .trim();
        x = x.replaceAll("^(국민가게,?\\s*다이소)$", "").trim();
        x = x.replaceAll("다이소\\s*멤버십.*$", "").trim();
        x = x.replaceAll(".*홈페이지에\\s*접속하셔서.*$", "").trim();
        x = x.replaceAll("\\s{2,}", " ").trim();
        return x;
    }

    // "면세사업자" 문구가 있으면 부가세 0원 판단 보정에 사용한다.
    private boolean containsTaxExemptBusiness(String text) {
        if (!notEmpty(text)) return false;
        return Pattern.compile("면\\s*세\\s*사\\s*업\\s*자").matcher(text).find();
    }

    // 다이소 문구(띄어쓰기 깨짐 포함) 존재 여부로 전표 종류를 판별한다.
    private boolean isDaisoSlip(String text) {
        if (!notEmpty(text)) return false;
        return DAISO_MARKER.matcher(text).find();
    }

    // 주소 후보 탐색: 일반 주소 키워드를 포함하면서 안내/전화 라인은 제외
    private String findAddressLike(List<String> lines) {
        for (String lineRaw : lines) {
            String line = cleanField(lineRaw);
            if (!notEmpty(line)) continue;
            if (!(line.contains("시") || line.contains("구") || line.contains("로") || line.contains("길"))) {
                continue;
            }
            if (line.contains("사업자") || line.contains("대표자") || line.contains("고객만족")) continue;
            if (line.matches(".*\\d{2,4}-\\d{3,4}-\\d{4}.*")) continue;
            return line;
        }
        return null;
    }

    // 승인번호 라벨 주변(같은 줄~다음 2줄)에서 6~12자리 숫자를 찾는다.
    private String findApprovalNearCard(List<String> lines) {
        if (lines == null) return null;
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (!line.contains("승인번호")) continue;
            String app = firstPattern(APPROVAL_NO, line);
            if (notEmpty(app)) return app;
            for (int j = i + 1; j < lines.size() && j <= i + 2; j++) {
                String next = cleanField(lines.get(j));
                app = firstPattern(APPROVAL_NO, next);
                if (notEmpty(app)) return app;
            }
        }
        return null;
    }

    // YYYY-MM-DD 형태로 날짜를 통일한다.
    private String normalizeDate(String value) {
        if (!notEmpty(value)) return null;
        Matcher m = SALE_DATE.matcher(value);
        if (!m.find()) return null;

        String y = m.group(1);
        String mm = String.format("%02d", Integer.parseInt(m.group(2)));
        String dd = String.format("%02d", Integer.parseInt(m.group(3)));
        return y + "-" + mm + "-" + dd;
    }

    // HH:mm(또는 HH:mm:ss) 형태로 시간 문자열을 정리한다.
    private String normalizeTime(String value) {
        if (!notEmpty(value)) return null;
        Matcher m = SALE_TIME.matcher(value);
        if (!m.find()) return null;
        String t = m.group(1);
        if (t.length() == 4) t = "0" + t; // 9:01 -> 09:01
        return t;
    }

    // 카드번호는 공백 제거 후 마스킹 패턴으로 재검증한다.
    private String normalizeCardMasked(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replaceAll("\\s+", "");
        String m = firstPattern(MASKED_CARD, x);
        return notEmpty(m) ? m : x;
    }

    // 사업자번호를 000-00-00000 포맷으로 통일한다.
    private String normalizeBizNo(String src) {
        if (!notEmpty(src)) return null;
        String dashed = firstPattern(BIZNO_DASH, src);
        if (notEmpty(dashed)) return dashed;
        String digitsOnly = src.replaceAll("[^0-9]", "");
        Matcher m = BIZNO_10.matcher(digitsOnly);
        if (!m.find()) return null;
        String d = m.group(1);
        return d.substring(0, 3) + "-" + d.substring(3, 5) + "-" + d.substring(5);
    }

    // 숫자 10자리 문자열을 사업자번호 포맷으로 변환한다.
    private String formatBizNo(String src) {
        if (!notEmpty(src)) return null;
        String digits = src.replaceAll("[^0-9]", "");
        if (digits.length() != 10) return null;
        return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
    }

    private Integer firstNonNullInt(Integer... values) {
        if (values == null) return null;
        for (Integer v : values) if (v != null) return v;
        return null;
    }

    // 문자열 내 마지막 금액 토큰을 반환(한 줄에 금액이 여러 개 있을 수 있음)
    private Integer lastMoney(String src) {
        if (!notEmpty(src)) return null;
        Matcher m = MONEY.matcher(src);
        Integer out = null;
        while (m.find()) {
            out = toInt(m.group(1));
        }
        return out;
    }

    // 정규식 첫 매칭값(group 1) 추출 래퍼
    private String firstMatch(String src, String regex) {
        if (!notEmpty(src) || !notEmpty(regex)) return null;
        return extract(src, regex, 1);
    }

    // Pattern 기반 첫 매칭값(group 1) 추출 래퍼
    private String firstPattern(Pattern p, String src) {
        if (p == null || !notEmpty(src)) return null;
        Matcher m = p.matcher(src);
        if (!m.find()) return null;
        return m.group(1).trim();
    }

    // OCR 노이즈를 줄이되 개행은 유지해서 라인 파싱이 가능하게 만든다.
    private String normalizeKeepNewlines(String raw) {
        String x = raw.replace("\r\n", "\n").replace("\r", "\n");
        x = x.replaceAll("[\\t\\x0B\\f]+", " ");
        x = x.replaceAll("[ ]{2,}", " ");
        return x.trim();
    }

    // 개행 기준 라인 분리(각 라인은 trim 처리)
    private List<String> splitLines(String text) {
        String[] arr = text.replace("\r", "\n").split("\n");
        List<String> out = new ArrayList<>();
        for (String s : arr) {
            out.add(s == null ? "" : s.trim());
        }
        return out;
    }

    // 일반 필드 정리: non-breaking space/중복 공백 제거
    private String cleanField(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\u00A0]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // 상품명 정리: 앞뒤 특수기호 제거 + 공백 정규화
    private String cleanItemName(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        // 상품명 뒤에 OCR로 붙는 금액(1,000원 이상)은 이름에서 제거한다.
        x = stripTrailingLargeMoney(x);
        x = x.replaceAll("^[-:·•]+", "").replaceAll("[-:·•]+$", "").trim();
        x = x.replaceAll("\\s{2,}", " ").trim();
        return x;
    }

    /**
     * 상품명 끝에 붙은 금액 토큰 제거
     * - 1,000 이상이면 제거
     * - 500 등 소액 숫자는 제거하지 않음(요청사항)
     */
    private String stripTrailingLargeMoney(String src) {
        String out = src;
        Pattern tailMoney = Pattern.compile("^(.*?)[\\s,/-]*([0-9]{1,3}(?:,[0-9]{3})+|[1-9][0-9]{3,})(?:\\s*원)?$");

        // 꼬리에 금액 토큰이 여러 개 붙는 경우를 대비해 반복 제거
        for (int i = 0; i < 3; i++) {
            Matcher m = tailMoney.matcher(out.trim());
            if (!m.matches()) break;

            Integer money = toInt(m.group(2));
            if (money == null || money < 1000) break;

            out = m.group(1) == null ? "" : m.group(1).trim();
            if (!notEmpty(out)) break;
        }
        return out;
    }

    private boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void printDebugResult(ReceiptResult r) {
        System.out.println("------ ✅ Daiso 파싱 결과 ------");
        System.out.println("[MERCHANT] name=" + safeVal(r.merchant.name)
                + ", bizNo=" + safeVal(r.merchant.bizNo)
                + ", tel=" + safeVal(r.merchant.tel)
                + ", address=" + safeVal(r.merchant.address));
        System.out.println("[META] saleDate=" + safeVal(r.meta.saleDate)
                + ", saleTime=" + safeVal(r.meta.saleTime)
                + ", receiptNo=" + safeVal(r.meta.receiptNo));
        System.out.println("[PAYMENT] type=" + safeVal(r.payment.type)
                + ", cardBrand=" + safeVal(r.payment.cardBrand)
                + ", cardMasked=" + safeVal(r.payment.cardMasked)
                + ", approvalAmt=" + safeVal(r.payment.approvalAmt));
        System.out.println("[APPROVAL] approvalNo=" + safeVal(r.approval.approvalNo)
                + ", merchantNo=" + safeVal(r.approval.merchantNo));
        System.out.println("[TOTALS] taxable=" + safeInt(r.totals.taxable)
                + ", vat=" + safeInt(r.totals.vat)
                + ", taxFree=" + safeInt(r.totals.taxFree)
                + ", total=" + safeInt(r.totals.total));
        if (r.items != null) {
            for (int i = 0; i < r.items.size(); i++) {
                Item it = r.items.get(i);
                System.out.println("[ITEM#" + i + "] name=" + safeVal(it.name)
                        + ", qty=" + safeInt(it.qty)
                        + ", amount=" + safeInt(it.amount)
                        + ", unitPrice=" + safeInt(it.unitPrice)
                        + ", taxFlag=" + safeVal(it.taxFlag));
            }
        }
        System.out.println("--------------------------------");
    }

    private String safeVal(String value) {
        return value == null ? "null" : value;
    }

    private String safeInt(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
