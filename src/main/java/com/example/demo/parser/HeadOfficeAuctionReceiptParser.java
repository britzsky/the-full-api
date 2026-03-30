package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOffice Auction 카드매출전표 파서
 *
 * - 인식 기준: 가맹점번호에 "Auction전자지불"
 * - 처리 대상: 하늘색 "신용카드 매출전표" 포맷
 * - 노이즈 제거: 하단 "지마켓에서 발행한 신용카드 매출전표" 문구 무시
 */
public class HeadOfficeAuctionReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    // 거래일자(YYYY-MM-DD / YYYY.MM.DD / YYYY/MM/DD)
    private static final Pattern SALE_DATE = Pattern.compile("(20\\d{2})[./-](\\d{1,2})[./-](\\d{1,2})");

    // 카드번호 마스킹(4 + 2 + 마스킹 + (선택)뒤 3~4)
    private static final Pattern MASKED_CARD = Pattern.compile(
            "\\b\\d{4}[- ]?\\d{2}\\*{2}[- ]?\\*{4}[- ]?\\*{3,4}\\b|\\b\\d{4}\\*{2,}\\d{2,}\\*?\\d{0,4}\\b"
    );

    // 승인번호(6~12자리)
    private static final Pattern APPROVAL_NO = Pattern.compile("\\b\\d{6,12}\\b");

    // 금액: 20,160 / 20,160원 / 20160
    private static final Pattern MONEY = Pattern.compile("\\b([0-9]{1,9}(?:,[0-9]{3})*)(?:\\s*원)?\\b");

    // 사업자등록번호
    private static final Pattern BIZNO_DASH = Pattern.compile("\\b(\\d{3}-\\d{2}-\\d{5})\\b");
    private static final Pattern BIZNO_10 = Pattern.compile("(\\d{10})");

    private static final String[] MAJOR_LABELS = {
            "카드종류", "카드번호", "거래종류", "거래금액", "거래일자", "부가세",
            "승인번호", "합계", "주문번호", "할부구분", "상품명", "업체명", "대표자",
            "사업자등록번호", "가맹점번호", "가맹점주소", "문의연락처", "신용카드 매출전표"
    };

    @Override
    public ReceiptResult parse(Document doc) {
        String raw = text(doc);
        if (raw == null) raw = "";

        String normalized = normalizeKeepNewlines(raw);
        normalized = stripBottomNoise(normalized);
        List<String> lines = splitLines(normalized);

        if (DEBUG) {
            System.out.println("=== 🧾 RAW TEXT (HeadOffice Auction Slip) ===");
            System.out.println(normalized);
            System.out.println("============================================");
        }

        ReceiptResult r = new ReceiptResult();

        /* ========================= 1) 카드 정보 ========================= */
        String cardBrandRaw = firstNonNull(
                findTextBelowLabel(lines, "카드종류"),
                extractLabelValue(normalized, "카드종류")
        );
        String cardMaskedRaw = firstNonNull(
                findMaskedCardBelowLabel(lines, "카드번호"),
                extractLabelValue(normalized, "카드번호")
        );
        r.payment.cardBrand = normalizeCardBrand(cleanField(cardBrandRaw));
        r.payment.cardMasked = normalizeCardMasked(cleanField(cardMaskedRaw));
        r.payment.cardNo = r.payment.cardMasked;

        /* ========================= 2) 거래정보(종류/금액/일자/부가세) ========================= */
        // 거래종류 / 거래금액
        Pair trade = extractPair(normalized, "거래종류", "거래금액");
        String tradeTypeRaw = firstNonNull(
                findTradeTypeBelowLabel(lines, "거래종류"),
                trade.left,
                extractLabelValue(normalized, "거래종류")
        );
        r.payment.type = cleanField(tradeTypeRaw);
        Integer taxableMoney = firstNonNullInt(
                findMoneyBelowLabel(lines, "거래금액"),
                firstMoney(trade.right),
                firstMoney(extractLabelValue(normalized, "거래금액"))
        );
        r.totals.taxable = taxableMoney;

        // 거래일자 / 부가세
        Pair saleDateVat = extractPair(normalized, "거래일자", "부가세");
        String saleDateRaw = firstNonNull(
                findDateBelowLabel(lines, "거래일자"),
                saleDateVat.left,
                extractLabelValue(normalized, "거래일자")
        );
        String vatRaw = firstNonNull(
                normalizeVatRawCandidate(findVatRawBelowLabel(lines, "부가세")),
                normalizeVatRawCandidate(saleDateVat.right),
                normalizeVatRawCandidate(extractLabelValue(normalized, "부가세"))
        );
        boolean hasVatLabel = normalized.contains("부가세");
        r.meta.saleDate = normalizeDate(saleDateRaw);
        r.totals.vat = firstMoney(vatRaw);

        /* ========================= 3) 승인/합계 ========================= */
        // 승인번호 / 합계
        Pair approvalTotal = extractPair(normalized, "승인번호", "합계");
        String approvalRaw = firstNonNull(
                findApprovalBelowLabel(lines, "승인번호"),
                approvalTotal.left,
                extractLabelValue(normalized, "승인번호")
        );
        String totalRaw = firstNonNull(
                findMoneyRawBelowLabel(lines, "합계"),
                approvalTotal.right,
                extractLabelValue(normalized, "합계")
        );
        r.approval.approvalNo = normalizeApprovalNo(approvalRaw);
        r.totals.total = firstMoney(totalRaw);

        /* ========================= 4) 주문/할부 ========================= */
        // 주문번호 / 할부구분
        Pair orderInstallment = extractPair(normalized, "주문번호", "할부구분");
        String orderNoRaw = firstNonNull(
                findOrderNoBelowLabel(lines, "주문번호"),
                orderInstallment.left,
                extractLabelValue(normalized, "주문번호")
        );
        String installmentRaw = firstNonNull(
                findInstallmentBelowLabel(lines, "할부구분"),
                orderInstallment.right,
                extractLabelValue(normalized, "할부구분")
        );
        r.meta.receiptNo = firstMatch(orderNoRaw, "([0-9]{8,})");
        r.payment.installment = cleanField(installmentRaw);

        /* ========================= 5) 업체정보(업체명/사업자등록번호/가맹점번호) ========================= */
        // 업체명 / 대표자
        Pair merchantPairByLine = findPairValuesBelowLabels(lines, "업체명", "대표자");
        Pair merchantPair = extractPair(normalized, "업체명", "대표자");
        String merchantRaw = firstNonNull(
                merchantPairByLine.left,
                findTextBelowLabel(lines, "업체명"),
                merchantPair.left,
                extractLabelValue(normalized, "업체명")
        );
        String ownerRaw = firstNonNull(
                merchantPairByLine.right,
                findTextBelowLabel(lines, "대표자"),
                merchantPair.right,
                extractLabelValue(normalized, "대표자")
        );
        r.merchant.name = cleanMerchantName(merchantRaw);
        String ownerName = cleanField(ownerRaw);
        if (notEmpty(ownerName)) {
            r.extra.put("merchant_owner_name", ownerName);
        }

        // 사업자등록번호 / 가맹점번호
        Pair bizPairByLine = findPairValuesBelowLabels(lines, "사업자등록번호", "가맹점번호");
        Pair bizMerchantNo = extractPair(normalized, "사업자등록번호", "가맹점번호");
        String bizNoRaw = firstNonNull(
                bizPairByLine.left,
                findBizNoBelowLabel(lines, "사업자등록번호"),
                bizMerchantNo.left,
                extractLabelValue(normalized, "사업자등록번호")
        );
        String merchantNoRaw = firstNonNull(
                bizPairByLine.right,
                findTextBelowLabel(lines, "가맹점번호"),
                bizMerchantNo.right,
                extractLabelValue(normalized, "가맹점번호")
        );
        r.merchant.bizNo = normalizeBizNo(bizNoRaw);
        String merchantNo = normalizeMerchantNo(merchantNoRaw);
        if (notEmpty(merchantNo)) {
            r.approval.merchantNo = merchantNo;
        }

        // 주소/문의연락처
        r.merchant.address = firstNonNull(
                collectAfterLabelUntilStop(lines, "가맹점주소", "문의연락처"),
                cleanField(extractBlock(normalized, "가맹점주소", "문의연락처"))
        );
        r.merchant.tel = normalizePhone(firstNonNull(
                findTextBelowLabel(lines, "문의연락처"),
                extractLabelValue(normalized, "문의연락처")
        ));

        /* ========================= 6) 상품명 ========================= */
        String productName = parseProductName(lines, normalized);
        Item item = new Item();
        item.name = firstNonNull(productName, "상품");
        // 요청사항: 옥션 전표 디테일 수량은 비워서 저장(null)
        item.qty = null;
        String taxFlag = resolveTaxFlag(vatRaw, r.totals.vat, hasVatLabel);
        applyTaxTotalsByFlag(r, taxFlag);
        Integer itemAmount = (r.totals.total != null) ? r.totals.total : r.totals.taxable;
        item.amount = itemAmount;
        item.unitPrice = itemAmount;
        // 요청사항: 부가세 0원/면세사업자일 때만 면세, 그 외(부가세 항목 존재)는 과세
        item.taxFlag = taxFlag;
        r.items = List.of(item);

        // 기본 보정
        if (!notEmpty(r.payment.type)) r.payment.type = "신용구매";
        if (r.totals.total == null && r.totals.taxable != null && r.totals.vat != null) {
            r.totals.total = r.totals.taxable + r.totals.vat;
        }
        if (r.totals.total != null) {
            r.payment.approvalAmt = String.valueOf(r.totals.total);
        }

        // 필수값 fallback
        if (!notEmpty(r.merchant.name)) r.merchant.name = "Unknown";
        if (!notEmpty(r.meta.saleDate)) {
            r.meta.saleDate = normalizeDate(firstMatch(normalized, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})"));
        }
        if (!notEmpty(r.approval.approvalNo)) {
            r.approval.approvalNo = normalizeApprovalNo(normalized);
        }

        if (DEBUG) {
            printDebugResult(r);
        }

        return r;
    }

    private String normalizeKeepNewlines(String raw) {
        String x = raw.replace("\r\n", "\n").replace("\r", "\n");
        x = x.replaceAll("[\\t\\x0B\\f]+", " ");
        x = x.replaceAll("[ ]{2,}", " ");
        return x.trim();
    }

    private String stripBottomNoise(String text) {
        // 옥션 전표 하단에 붙는 지마켓 발행 문구는 파싱 대상에서 제거
        return text
                .replaceAll("(?mi)^.*지마켓에서\\s*발행한\\s*신용카드\\s*매출전표.*$", "")
                .replaceAll("(?mi)^.*주식회사\\s*G마켓에서\\s*발행한.*$", "")
                .trim();
    }

    private String extractLabelValue(String text, String label) {
        String q = Pattern.quote(label);
        String v = extract(text, "(?m)" + q + "\\s*[:：]?\\s*(?:\\n\\s*)?([^\\n\\r]{1,120})", 1);
        v = sanitizeLabelValue(v, label);
        if (notEmpty(v)) return v;
        // 라벨 뒤에 값이 한 줄 더 밀리는 OCR 케이스
        String v2 = extract(text, "(?s)" + q + "\\s*[:：]?\\s*\\n\\s*([^\\n\\r]{1,120})", 1);
        return sanitizeLabelValue(v2, label);
    }

    private String sanitizeLabelValue(String value, String ownerLabel) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        if (ownerLabel != null && x.equals(ownerLabel)) return null;
        if (isMajorHeaderLine(x)) return null;
        return x;
    }

    private Pair extractPair(String text, String leftLabel, String rightLabel) {
        String leftQ = Pattern.quote(leftLabel);
        String rightQ = Pattern.quote(rightLabel);

        // 1) 헤더 아래 한 줄에 "좌/우" 같이 있는 경우
        String left = extract(text,
                "(?s)" + leftQ + "\\s*/\\s*" + rightQ
                        + "\\s*(?:\\n\\s*)?([^\\n\\r/]+?)\\s*(?:/|\\||\\t| {2,})\\s*([^\\n\\r]+)",
                1);
        String right = extract(text,
                "(?s)" + leftQ + "\\s*/\\s*" + rightQ
                        + "\\s*(?:\\n\\s*)?([^\\n\\r/]+?)\\s*(?:/|\\||\\t| {2,})\\s*([^\\n\\r]+)",
                2);
        if (notEmpty(left) || notEmpty(right)) {
            return new Pair(cleanField(left), cleanField(right));
        }

        // 2) 헤더 아래 두 줄에 "좌", 다음 줄 "우"로 분리된 경우
        left = extract(text,
                "(?s)" + leftQ + "\\s*/\\s*" + rightQ
                        + "\\s*(?:\\n\\s*)?([^\\n\\r]+)\\s*(?:\\n\\s*)+([^\\n\\r]+)",
                1);
        right = extract(text,
                "(?s)" + leftQ + "\\s*/\\s*" + rightQ
                        + "\\s*(?:\\n\\s*)?([^\\n\\r]+)\\s*(?:\\n\\s*)+([^\\n\\r]+)",
                2);
        if (notEmpty(left) || notEmpty(right)) {
            return new Pair(cleanField(left), cleanField(right));
        }

        // 3) 라인 스캔 fallback
        List<String> lines = splitLines(text);
        int headerIndex = findPairHeaderIndex(lines, leftLabel, rightLabel);
        if (headerIndex < 0) {
            return new Pair(null, null);
        }

        List<String> values = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size() && i <= headerIndex + 10; i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (isMajorHeaderLine(line)) break;

            String[] split = line.split("\\s*/\\s*", 2);
            if (split.length == 2 && notEmpty(split[0]) && notEmpty(split[1])) {
                values.add(cleanField(split[0]));
                values.add(cleanField(split[1]));
                break;
            }
            values.add(line);
            if (values.size() >= 2) break;
        }

        String leftVal = values.size() >= 1 ? values.get(0) : null;
        String rightVal = values.size() >= 2 ? values.get(1) : null;
        return new Pair(leftVal, rightVal);
    }

    private int findPairHeaderIndex(List<String> lines, String leftLabel, String rightLabel) {
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (line.contains(leftLabel) && line.contains(rightLabel)) {
                return i;
            }
        }
        return -1;
    }

    private Pair findPairValuesBelowLabels(List<String> lines, String leftLabel, String rightLabel) {
        int headerIdx = findPairHeaderIndex(lines, leftLabel, rightLabel);
        if (headerIdx < 0) return new Pair(null, null);

        for (int i = headerIdx + 1; i < lines.size() && i <= headerIdx + 6; i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (isMajorHeaderLine(line)) continue;
            return splitPairValueLine(line, leftLabel, rightLabel);
        }
        return new Pair(null, null);
    }

    private Pair splitPairValueLine(String line, String leftLabel, String rightLabel) {
        String x = cleanField(line);
        if (!notEmpty(x)) return new Pair(null, null);

        // 사업자등록번호 / 가맹점번호는 biz 패턴으로 좌우 분해
        if ("사업자등록번호".equals(leftLabel) && "가맹점번호".equals(rightLabel)) {
            String biz = normalizeBizNo(x);
            String merchantNo = null;

            if (notEmpty(biz)) {
                String rest = x.replaceFirst(".*" + Pattern.quote(biz), "").trim();
                merchantNo = normalizeMerchantNo(rest);
            }
            if (!notEmpty(merchantNo)) {
                merchantNo = normalizeMerchantNo(x.replaceAll("([0-9]{3}-[0-9]{2}-[0-9]{5}|[0-9]{10})", " ").trim());
            }
            return new Pair(biz, merchantNo);
        }

        // 일반 분해: /, |, 탭, 2칸 이상 공백 우선
        String[] split = x.split("\\s*/\\s*|\\s*\\|\\s*|\\t+| {2,}", 2);
        if (split.length == 2 && notEmpty(split[0]) && notEmpty(split[1])) {
            return new Pair(cleanField(split[0]), cleanField(split[1]));
        }

        // 업체명 / 대표자: 마지막 한글 2~4자 이름 추정
        if ("업체명".equals(leftLabel) && "대표자".equals(rightLabel)) {
            Matcher m = Pattern.compile("(.+?)\\s+([가-힣]{2,4})$").matcher(x);
            if (m.find()) {
                return new Pair(cleanField(m.group(1)), cleanField(m.group(2)));
            }
        }

        return new Pair(x, null);
    }

    private boolean isMajorHeaderLine(String line) {
        if (!notEmpty(line)) return false;
        for (String label : MAJOR_LABELS) {
            if (line.contains(label)) return true;
        }
        return false;
    }

    private List<String> splitLines(String text) {
        String[] arr = text.replace("\r", "\n").split("\n");
        List<String> lines = new ArrayList<>();
        for (String line : arr) {
            lines.add(line == null ? "" : line.trim());
        }
        return lines;
    }

    private Integer firstNonNullInt(Integer... arr) {
        if (arr == null) return null;
        for (Integer v : arr) {
            if (v != null) return v;
        }
        return null;
    }

    private int indexOfLabel(List<String> lines, String label) {
        if (lines == null || !notEmpty(label)) return -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (line.equals(label) || line.contains(label)) return i;
        }
        return -1;
    }

    private String inlineValueAfterLabel(String line, String label) {
        String x = cleanField(line);
        if (!notEmpty(x) || !notEmpty(label)) return null;
        int idx = x.indexOf(label);
        if (idx < 0) return null;
        String tail = x.substring(idx + label.length()).replaceFirst("^[\\s:：\\-/|]+", "").trim();
        return sanitizeLabelValue(tail, label);
    }

    private List<String> collectValueCandidates(List<String> lines, String label, int maxCandidates) {
        List<String> out = new ArrayList<>();
        int idx = indexOfLabel(lines, label);
        if (idx < 0) return out;

        String inline = inlineValueAfterLabel(lines.get(idx), label);
        if (notEmpty(inline)) out.add(inline);

        for (int i = idx + 1; i < lines.size() && out.size() < maxCandidates; i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;

            // 라벨 라인은 값 후보에서 제외
            if (isMajorHeaderLine(line)) {
                // 직전까지 후보가 있으면 섹션 종료로 본다.
                if (!out.isEmpty()) break;
                continue;
            }

            out.add(line);
        }
        return out;
    }

    private String findTextBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String x = cleanField(c);
            if (!notEmpty(x)) continue;
            if (x.contains("지마켓에서 발행한")) continue;
            if (isMajorHeaderLine(x)) continue;
            return x;
        }
        return null;
    }

    private String findMaskedCardBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String m = normalizeCardMasked(c);
            if (notEmpty(m)) return m;
        }
        return null;
    }

    private String findTradeTypeBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String x = cleanField(c);
            if (!notEmpty(x)) continue;

            String picked = firstMatch(x, "(신용구매|신용거래|승인거래|정상매출|체크|현금)");
            if (notEmpty(picked)) return picked;

            // 동라인에 금액이 같이 붙어있으면 금액 제거 후 텍스트만 사용
            x = x.replaceAll("[0-9]{1,3}(?:,[0-9]{3})*\\s*원?", " ").replaceAll("\\s{2,}", " ").trim();
            if (!notEmpty(x)) continue;
            if (isMajorHeaderLine(x)) continue;
            return x;
        }
        return null;
    }

    private Integer findMoneyBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            Integer m = firstMoney(c);
            if (m != null) return m;
        }
        return null;
    }

    private String findMoneyRawBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            if (firstMoney(c) != null) return c;
        }
        return null;
    }

    private String findDateBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String d = normalizeDate(c);
            if (notEmpty(d)) return d;
        }
        return null;
    }

    private String findVatRawBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String picked = normalizeVatRawCandidate(c);
            if (notEmpty(picked)) return picked;
        }
        return null;
    }

    private String findApprovalBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String a = normalizeApprovalNo(c);
            if (notEmpty(a)) return a;
        }
        return null;
    }

    private String findOrderNoBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String order = firstMatch(c, "([0-9]{8,})");
            if (notEmpty(order)) return order;
        }
        return null;
    }

    private String findInstallmentBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String inst = firstMatch(c, "(일시불|[0-9]{1,2}\\s*개월|할부)");
            if (notEmpty(inst)) return cleanField(inst);
        }
        return null;
    }

    private String findBizNoBelowLabel(List<String> lines, String label) {
        List<String> cands = collectValueCandidates(lines, label, 4);
        for (String c : cands) {
            String biz = normalizeBizNo(c);
            if (notEmpty(biz)) return biz;
        }
        return null;
    }

    private String extractBlock(String text, String startLabel, String endLabel) {
        String s = Pattern.quote(startLabel);
        String e = Pattern.quote(endLabel);
        String block = extract(text, "(?s)" + s + "\\s*[:：]?\\s*(.*?)\\s*(?:" + e + "|$)", 1);
        return cleanField(block);
    }

    private String parseProductName(List<String> lines, String text) {
        // 1) 라벨 바로 아래 값 우선
        String fromLines = collectAfterLabelUntilStop(lines, "상품명",
                "업체명", "대표자", "사업자등록번호", "가맹점번호", "가맹점주소", "문의연락처");
        String cleanedLines = cleanProductName(fromLines);
        if (notEmpty(cleanedLines)) return cleanedLines;

        // 2) 기존 정규식 fallback
        return parseProductName(text);
    }

    private String parseProductName(String text) {
        String block = extract(text,
                "(?s)상품명\\s*[:：]?\\s*(.*?)\\s*(?:업체명\\s*/\\s*대표자|사업자등록번호\\s*/\\s*가맹점번호|가맹점주소|문의\\s*연락처|$)",
                1);
        if (!notEmpty(block)) {
            block = collectAfterLabelUntilStop(text, "상품명",
                    "업체명", "대표자", "사업자등록번호", "가맹점번호", "가맹점주소", "문의연락처");
        }
        if (!notEmpty(block)) return null;

        List<String> lines = splitLines(block);
        String best = null;
        for (String line : lines) {
            String cleaned = cleanField(line);
            if (!notEmpty(cleaned)) continue;
            if (isMajorHeaderLine(cleaned)) continue;
            if (cleaned.contains("지마켓에서 발행한")) continue;
            if (looksLikeMoneyOnly(cleaned)) continue;

            if (best == null || cleaned.length() > best.length()) {
                best = cleaned;
            }
        }
        return cleanProductName(best);
    }

    private String collectAfterLabelUntilStop(List<String> lines, String startLabel, String... stopLabels) {
        if (lines == null || lines.isEmpty()) return null;

        int start = indexOfLabel(lines, startLabel);
        if (start < 0) return null;

        StringBuilder sb = new StringBuilder();

        String inline = inlineValueAfterLabel(lines.get(start), startLabel);
        if (notEmpty(inline) && !looksLikeMoneyOnly(inline) && !isMajorHeaderLine(inline)) {
            sb.append(inline).append(" ");
        }

        for (int i = start + 1; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;

            if (isMajorHeaderLine(line)) {
                boolean isStopLabel = false;
                for (String s : stopLabels) {
                    if (line.contains(s)) {
                        isStopLabel = true;
                        break;
                    }
                }
                if (isStopLabel || sb.length() > 0) break;
                continue;
            }

            boolean stop = false;
            for (String s : stopLabels) {
                if (line.contains(s)) {
                    stop = true;
                    break;
                }
            }
            if (stop) break;

            if (line.contains("지마켓에서 발행한")) continue;
            if (looksLikeMoneyOnly(line)) continue;
            sb.append(line).append(" ");
        }

        String out = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return out.isEmpty() ? null : out;
    }

    private String collectAfterLabelUntilStop(String text, String startLabel, String... stopLabels) {
        List<String> lines = splitLines(text);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (line.contains(startLabel)) {
                start = i;
                break;
            }
        }
        if (start < 0) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < lines.size(); i++) {
            String line = cleanField(lines.get(i));
            if (!notEmpty(line)) continue;
            if (isMajorHeaderLine(line)) break;

            boolean stop = false;
            for (String s : stopLabels) {
                if (line.contains(s)) {
                    stop = true;
                    break;
                }
            }
            if (stop) break;

            if (line.contains("지마켓에서 발행한")) continue;
            if (looksLikeMoneyOnly(line)) continue;
            sb.append(line).append(" ");
        }
        String out = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return out.isEmpty() ? null : out;
    }

    private boolean looksLikeMoneyOnly(String value) {
        if (!notEmpty(value)) return false;
        return value.matches("^\\d{1,3}(?:,\\d{3})*\\s*원?$");
    }

    private String cleanProductName(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replaceAll("(업체명|대표자|사업자등록번호|가맹점번호|가맹점주소|문의연락처).*", "").trim();
        x = x.replaceAll("^[\\-:]+", "").replaceAll("[\\-:]+$", "").trim();
        return x;
    }

    private String cleanMerchantName(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replaceAll("(?i)auction\\s*전자\\s*지불", "").trim();
        x = x.replaceAll("(대표자|사업자등록번호|가맹점번호|가맹점주소|문의연락처).*", "").trim();
        x = x.replaceAll("\\s{2,}", " ").trim();
        return x;
    }

    private String normalizeCardBrand(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replaceAll("\\s+", "");
        if (x.equalsIgnoreCase("BC") || x.equals("BC카드")) return "BC카드";
        if (x.equals("비씨") || x.equals("비씨카드")) return "비씨카드";
        return x;
    }

    private String normalizeCardMasked(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        x = x.replaceAll("(거래종류|거래일자|승인번호|주문번호|상품명).*", "").trim();
        x = x.replaceAll("x", "*").replaceAll("X", "*");

        String byPattern = firstPattern(MASKED_CARD, x);
        if (notEmpty(byPattern)) {
            return byPattern.replaceAll("\\s+", "");
        }

        String compact = x.replaceAll("[^0-9\\-*]", "");
        byPattern = firstPattern(MASKED_CARD, compact);
        return notEmpty(byPattern) ? byPattern : (compact.isEmpty() ? null : compact);
    }

    private String normalizeDate(String value) {
        if (!notEmpty(value)) return null;
        Matcher m = SALE_DATE.matcher(value);
        if (!m.find()) return null;

        String y = m.group(1);
        String mm = String.format("%02d", Integer.parseInt(m.group(2)));
        String dd = String.format("%02d", Integer.parseInt(m.group(3)));
        return y + "-" + mm + "-" + dd;
    }

    private String normalizeApprovalNo(String value) {
        return firstPattern(APPROVAL_NO, value);
    }

    private String normalizeBizNo(String value) {
        if (!notEmpty(value)) return null;
        if (isMajorHeaderLine(cleanField(value))) return null;

        String withDash = firstPattern(BIZNO_DASH, value);
        if (notEmpty(withDash)) return withDash;

        String digitsOnly = value.replaceAll("[^0-9]", "");
        Matcher m = BIZNO_10.matcher(digitsOnly);
        if (m.find()) {
            String digits = m.group(1);
            return digits.substring(0, 3) + "-" + digits.substring(3, 5) + "-" + digits.substring(5);
        }
        return null;
    }

    private String normalizeMerchantNo(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        if (isMajorHeaderLine(x)) return null;
        x = x.replaceAll("(사업자등록번호|업체명|대표자|가맹점주소|문의연락처).*", "").trim();
        return notEmpty(x) ? x : null;
    }

    private String normalizePhone(String value) {
        String v = cleanField(value);
        if (!notEmpty(v)) return null;
        String phone = firstMatch(v, "([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})");
        return notEmpty(phone) ? phone : v;
    }

    private Integer estimateQuantity(String productName) {
        if (!notEmpty(productName)) return 1;

        // x 20개 / x20 / 20개 / 20입 등
        String[] patterns = {
                "(?i)\\bx\\s*([0-9]{1,4})\\s*(개|ea|입|팩|봉|병|캔|세트)?\\b",
                "(?i)\\b([0-9]{1,4})\\s*(개|ea|입|팩|봉|병|캔|세트)\\b"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(productName);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ignore) {
                }
            }
        }
        return 1;
    }

    private Integer firstMoney(String src) {
        if (!notEmpty(src)) return null;

        Matcher m = MONEY.matcher(src);
        while (m.find()) {
            String num = m.group(1);
            String full = m.group(0);
            if (!notEmpty(num)) continue;

            // 승인번호/주문번호 오인 방지: '원'도 콤마도 없고 길이가 긴 숫자는 금액에서 제외
            String digits = num.replaceAll("[^0-9]", "");
            boolean hasWon = full.contains("원");
            boolean hasComma = num.contains(",");
            if (!hasWon && !hasComma && digits.length() >= 7) continue;

            return toInt(num);
        }
        return null;
    }

    private String firstMatch(String src, String regex) {
        if (src == null) return null;
        return extract(src, regex, 1);
    }

    private String firstPattern(Pattern p, String src) {
        if (p == null || src == null) return null;
        Matcher m = p.matcher(src);
        return m.find() ? m.group(0).trim() : null;
    }

    private String normalizeVatRawCandidate(String value) {
        String x = cleanField(value);
        if (!notEmpty(x)) return null;
        if (isMajorHeaderLine(x)) return null;
        if (isTaxFreeBusiness(x)) return "면세사업자";
        if (looksLikeDateLikeText(x)) return null;
        if (firstMoney(x) != null) return x;
        return null;
    }

    private boolean looksLikeDateLikeText(String value) {
        if (!notEmpty(value)) return false;
        String compact = value.replaceAll("\\s+", "");
        if (SALE_DATE.matcher(compact).find()) return true;
        return compact.matches("^20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2}$");
    }

    private String resolveTaxFlag(String vatRaw, Integer vatValue, boolean hasVatLabel) {
        if (isTaxFreeBusiness(vatRaw)) return "면세";
        if (vatValue != null && vatValue == 0) return "면세";
        if (vatValue != null && vatValue > 0) return "과세";
        return hasVatLabel ? "과세" : "면세";
    }

    private boolean isTaxFreeBusiness(String vatRaw) {
        if (!notEmpty(vatRaw)) return false;
        String compact = vatRaw.replaceAll("\\s+", "");
        if (compact.contains("면세사업자")) return true;
        String koreanOnly = vatRaw.replaceAll("[^가-힣]", "");
        return koreanOnly.contains("면세사업자") || koreanOnly.contains("면세사업");
    }

    private void applyTaxTotalsByFlag(ReceiptResult r, String taxFlag) {
        if (r == null || r.totals == null || !"면세".equals(taxFlag)) return;

        Integer gross = firstNonNullInt(r.totals.total, r.totals.taxable);
        if (r.totals.total == null && gross != null) {
            r.totals.total = gross;
        }
        r.totals.vat = 0;
        r.totals.taxable = 0;
        if (r.totals.taxFree == null) {
            r.totals.taxFree = gross;
        }
    }

    private String cleanField(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\u00A0]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void printDebugResult(ReceiptResult r) {
        System.out.println("------ ✅ Auction 파싱 결과 ------");
        System.out.println("[MERCHANT] name=" + safe(r.merchant.name)
                + ", bizNo=" + safe(r.merchant.bizNo)
                + ", tel=" + safe(r.merchant.tel));
        System.out.println("[META] saleDate=" + safe(r.meta.saleDate)
                + ", orderNo=" + safe(r.meta.receiptNo));
        System.out.println("[PAYMENT] type=" + safe(r.payment.type)
                + ", cardBrand=" + safe(r.payment.cardBrand)
                + ", cardMasked=" + safe(r.payment.cardMasked)
                + ", installment=" + safe(r.payment.installment));
        System.out.println("[APPROVAL] approvalNo=" + safe(r.approval.approvalNo)
                + ", merchantNo=" + safe(r.approval.merchantNo));
        System.out.println("[TOTALS] taxable=" + safeInt(r.totals.taxable)
                + ", vat=" + safeInt(r.totals.vat)
                + ", total=" + safeInt(r.totals.total));
        if (r.items != null && !r.items.isEmpty()) {
            Item it = r.items.get(0);
            System.out.println("[ITEM] name=" + safe(it.name)
                    + ", qty=" + safeInt(it.qty)
                    + ", amount=" + safeInt(it.amount)
                    + ", unitPrice=" + safeInt(it.unitPrice));
        }
        System.out.println("---------------------------------");
    }

    private String safeInt(Integer value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private static class Pair {
        final String left;
        final String right;

        Pair(String left, String right) {
            this.left = left;
            this.right = right;
        }
    }
}
