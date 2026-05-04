package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOffice (Auction/Gmarket) CreditCard Sales Slip Parser v1.1
 *
 * ✅ 지원 포맷
 * 1) 연파랑 전표(신용카드 매출전표) - KOREAN_LIGHT
 * 2) 진파랑 전표(Sales Slip (Credit Card)) - SALES_SLIP_EN
 *
 * ✅ 개선점(v1.1)
 * - 라벨 다음 "한 줄/다음 줄" 기반으로 안정 추출 (줄바꿈 타고 넘어가는 문제 해결)
 * - 거래일자/부가세 컬럼 섞임 대응: 거래일자 라벨 근처에서 날짜 탐색
 * - 카드번호에 _, 알파벳 등 섞여도 마스킹 값 추출 + 정규화
 * - 상품명: 라벨 블록에서 라벨/금액 라인 제거 후 가장 유효한 라인 선택
 * - SALES_SLIP_EN 금액테이블: 라벨과 숫자가 분리되어 하단에 나오는 케이스(13410/0/13,410) 대응
 * - 사업자등록번호(bizNo) 추출 강화
 */
public class HeadOfficeGMarketReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    @Override
    public ReceiptResult parse(Document doc) {

        String raw = text(doc);
        if (raw == null) raw = "";

        // ✅ 줄바꿈 유지 + 탭/캐리지리턴 정리
        String text = normalizeKeepNewlines(raw);

        System.out.println("=== 🧾 RAW TEXT (HeadOffice Slip) ===");
        System.out.println(text);
        System.out.println("====================================");

        SlipStyle style = detectStyle(text);
        System.out.println("🧭 인식된 전표 스타일: " + style);

        ReceiptResult r;
        switch (style) {
            case SALES_SLIP_EN:
                r = parseSalesSlipEnglish(text);
                break;
            case KOREAN_LIGHT:
            default:
                r = parseKoreanLightSlip(text);
                break;
        }

        printFullResult(r);
        return r;
    }

    /* ========================= 스타일 감지 ========================= */

    private enum SlipStyle {
        KOREAN_LIGHT,
        SALES_SLIP_EN
    }

    private SlipStyle detectStyle(String text) {
        boolean hasSalesSlip = containsAny(text, "Sales Slip", "판매자정보", "봉사료", "유효기간");
        boolean hasSellerInfo = containsAny(text, "판매자정보", "상호", "사업자등록번호", "과세유형", "사업장주소");
        boolean hasAmountTable = containsAny(text, "금액", "부가세", "합계");

        if (hasSalesSlip && (hasSellerInfo || hasAmountTable)) return SlipStyle.SALES_SLIP_EN;
        return SlipStyle.KOREAN_LIGHT;
    }

    /* ========================= 1) 연파랑 전표 ========================= */

    private ReceiptResult parseKoreanLightSlip(String text) {
        ReceiptResult r = new ReceiptResult();

        if (DEBUG) System.out.println("---- [DEBUG] parseKoreanLightSlip ----");

        // 카드종류 (라벨 다음 1줄/다음줄)
        String cardBrand = debugExtract("cardBrand",
                text,
                "(?m)카드종류\\s*[:：]?\\s*(?:\\n\\s*)?([^\\n\\r]{1,30})",
                1
        );
        r.payment.cardBrand = normalizeCardBrand(cleanField(cardBrand));

        // 카드번호 (라벨 다음 1줄/다음줄) - _, 알파벳 섞여도 우선 잡고 마스킹 정규화
        String cardMasked = debugExtract("cardMasked",
                text,
                "(?m)카드번호\\s*[:：]?\\s*(?:\\n\\s*)?([^\\n\\r]{6,50})",
                1
        );
        r.payment.cardMasked = normalizeCardMasked(cardMasked);

        // 거래종류 (신용구매 등) - 줄바꿈 넘어가지 않게 한 줄만
        String payType = debugExtract("paymentType",
                text,
                "(?m)거래종류\\s*[:：]?\\s*(?:\\n\\s*)?([^\\n\\r]{1,30})",
                1
        );
        r.payment.type = cleanField(payType);

        // 할부구분(일시불 등)
        String installment = debugExtract("installment",
                text,
                "(?m)할부구분\\s*[:：]?\\s*(?:\\n\\s*)?([^\\n\\r]{1,30})",
                1
        );
        if (notEmpty(installment)) r.payment.installment = cleanField(installment);

        // 거래일자: 표의 좌/우 컬럼 섞임 때문에 라벨 근처에서 날짜 패턴 탐색
        String saleDate = debugFindDateNearLabel("saleDate", text, "거래일자");
        r.meta.saleDate = saleDate;
        r.meta.saleTime = null;

        // 승인번호
        r.approval.approvalNo = debugExtract("approvalNo",
                text,
                "(?m)승인번호\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{6,12})",
                1
        );

        // 주문번호 / 배송비결제번호
        String orderNo = debugExtract("orderNo",
                text,
                "(?m)주문번호\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{6,})",
                1
        );
        String shipPayNo = debugExtract("shipPayNo",
                text,
                "(?m)배송비결제번호\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{6,})",
                1
        );
        r.meta.receiptNo = firstNonNull(cleanField(orderNo), cleanField(shipPayNo));

        // 업체명(판매자)
        String merchant = debugExtract("merchantName",
                text,
                "(?s)업체명\\s*[:：]?\\s*(?:\\n\\s*)?([\\s\\S]*?)\\s*(?:\\n\\s*)?(대표자|사업자등록번호|가맹점번호|가맹점주소|문의\\s*연락처|$)",
                1
        );
        merchant = stripKnownNoiseMerchant(cleanField(merchant));
        r.merchant.name = firstNonNull(merchant, "Unknown");

        // 사업자등록번호
        String bizNo = debugExtract("bizNo",
                text,
                "(?m)사업자등록번호\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{3}-[0-9]{2}-[0-9]{5})",
                1
        );
        if (!notEmpty(bizNo)) {
            // fallback: 전체에서 첫 bizNo 패턴
            bizNo = debugExtract("bizNo_fallback",
                    text,
                    "([0-9]{3}-[0-9]{2}-[0-9]{5})",
                    1
            );
        }
        if (notEmpty(bizNo)) r.merchant.bizNo = cleanField(bizNo);

        // 가맹점주소
        String addr = debugExtract("merchantAddr",
                text,
                "(?s)가맹점주소\\s*[:：]?\\s*(?:\\n\\s*)?([\\s\\S]*?)\\s*(?:\\n\\s*)?(문의\\s*연락처|본\\s*확인서|본\\s*영수증|$)",
                1
        );
        addr = cleanField(addr);
        if (notEmpty(addr)) r.merchant.address = addr;

        // 문의 연락처(전화)
        String tel = debugExtract("merchantTel",
                text,
                "(?m)문의\\s*연락처\\s*[:：]?\\s*(?:\\n\\s*)?([0-9\\-]{8,20})",
                1
        );
        tel = cleanField(tel);
        if (notEmpty(tel)) r.merchant.tel = tel;

        // 상품명: 블록 추출 후 라벨/금액 라인 제거하여 최적 라인 선택
        String productBlock = debugExtract("productBlock",
                text,
                "(?s)상품명\\s*[:：]?\\s*(?:\\n\\s*)?([\\s\\S]*?)\\s*(?:\\n\\s*)?(업체명|대표자|사업자등록번호|가맹점번호|가맹점주소|문의\\s*연락처|$)",
                1
        );
        String product = pickBestProductLine(productBlock);
        product = cleanProductName(product);

        // 금액: 연파랑은 보통 거래금액/합계/합계금액/과세금액 등이 라벨로 나옴
        Integer taxable = debugFirstMoney("taxable",
                text,
                "(?m)과세금액\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{1,3}(?:,[0-9]{3})+)",
                1
        );
        Integer vat = debugFirstMoney("vat",
                text,
                // ✅ 부가세는 날짜(2025-12-22) 같은 것과 섞여 들어오는 케이스 방지: 콤마 있는 금액만
                "(?m)부가세\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{1,3}(?:,[0-9]{3})+)",
                1
        );
        Integer total = debugFirstMoney("total_label",
                text,
                "(?m)합계(?:금액)?\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{1,3}(?:,[0-9]{3})+)",
                1
        );
        if (total == null) {
            total = debugFirstMoney("total_tradeAmount",
                    text,
                    "(?m)거래금액\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{1,3}(?:,[0-9]{3})+)",
                    1
            );
        }

        Integer taxFree = debugFirstMoney("taxFree",
                text,
                "(?m)비과세금액\\s*[:：]?\\s*(?:\\n\\s*)?([0-9]{1,3}(?:,[0-9]{3})+)",
                1
        );

        // 간이과세사업자 케이스: 부가세가 비어있는 경우가 많음 (억지 추정하지 않음)
        if (containsAny(text, "간이과세사업자") && vat != null) {
            // "부가세" 옆이 날짜(2025...)에서 잘못 뽑히는 케이스가 있으므로 추가 방어
            // (현재는 콤마 금액만 허용해서 대부분 해결됨)
        }

        r.totals.taxable = taxable;
        r.totals.vat = vat;
        r.totals.total = total;
        r.totals.taxFree = taxFree;

        // Payment approvalAmt 보강
        if (r.payment != null && r.payment.approvalAmt == null) {
        	// ✅ total 변수 스코프 안에서만 사용
        	trySetApprovalAmt(r.payment, total);
        }

        // 아이템 1개
        r.items = buildSingleItem(product, r.totals.total, r.totals.vat);

        if (DEBUG) System.out.println("---- [DEBUG] parseKoreanLightSlip END ----");
        return r;
    }

    /* ========================= 2) 진파랑 Sales Slip ========================= */

    private ReceiptResult parseSalesSlipEnglish(String text) {
        ReceiptResult r = new ReceiptResult();

        if (DEBUG) System.out.println("---- [DEBUG] parseSalesSlipEnglish ----");

        // 라인 목록 출력
        String[] allLines = text.replace("\r", "\n").split("\n");
        for (int i = 0; i < allLines.length; i++) {
            System.out.printf("[GMARKET] L%02d: %s%n", i, allLines[i]);
        }

        // OCR 구조: 라벨 컬럼 전체 → 값 컬럼 전체 (또는 인터리브)
        // → 라벨 다음 줄이 값이 아닐 수 있음 → 전체 텍스트에서 패턴으로 직접 추출

        // ---- 결제정보 ----
        System.out.println("[GMARKET] ---- 결제정보 ----");

        // 주문번호: "5476888886/4421175091" - 슬래시 포함 14자리 이상
        String orderNo = extract(text, "([0-9]{10}/[0-9]{10,13})");
        if (!notEmpty(orderNo)) {
            orderNo = extract(text, "([0-9]{12,20})");
        }
        r.meta.receiptNo = cleanField(orderNo);
        System.out.println("[GMARKET] orderNo=" + safe(r.meta.receiptNo));

        // 카드종류: 비씨/BC/국민 등 한글영문 카드명 (숫자만이면 제외)
        String cardBrand = extract(text, "(비씨|BC카드|BC|국민|신한|현대|롯데|농협|하나|NH|KB|IBK비씨|IBK)");
        r.payment.cardBrand = normalizeCardBrand(cleanField(cardBrand));
        System.out.println("[GMARKET] cardBrand=" + safe(r.payment.cardBrand));

        // 카드번호: "5130-41******-8923" 마스킹 형태
        String cardMasked = extract(text, "([0-9]{4}-[0-9]{2}\\*{4,}-[0-9]{4})");
        r.payment.cardMasked = normalizeCardMasked(cardMasked);
        System.out.println("[GMARKET] cardMasked=" + safe(r.payment.cardMasked));

        // 승인번호: 6~12자리 독립 숫자 (카드번호/주문번호/날짜 제외)
        String approval = extractApprovalNoGmarket(text);
        r.approval.approvalNo = cleanField(approval);
        System.out.println("[GMARKET] approvalNo=" + safe(r.approval.approvalNo));

        // 거래일자: "2026-03-23 11:13:32 AM" 형태
        Matcher dtm = Pattern.compile("(20\\d{2}[./-]\\d{2}[./-]\\d{2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d(?:\\s*(?:AM|PM))?)").matcher(text);
        if (dtm.find()) {
            r.meta.saleDate = dtm.group(1).replace(".", "-").replace("/", "-");
            r.meta.saleTime = dtm.group(2).trim();
        } else {
            r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{2}[./-]\\d{2})");
        }
        System.out.println("[GMARKET] saleDate=" + safe(r.meta.saleDate) + ", saleTime=" + safe(r.meta.saleTime));

        // 거래유형/거래종류: "신용거래", "일시불" 등 고정값
        String tradeType = firstNonNull(
                extract(text, "(신용거래|체크거래|현금거래)"),
                extract(text, "(일시불|할부)")
        );
        r.payment.type = firstNonNull(tradeType, "신용거래");
        System.out.println("[GMARKET] tradeType=" + safe(r.payment.type));

        // ---- 상품정보 ----
        System.out.println("[GMARKET] ---- 상품정보 ----");

        String productBlock = debugExtract("productBlock",
                text,
                "(?s)상품명\\s*[:：]?\\s*(?:\\n\\s*)?([\\s\\S]*?)\\s*(?:\\n\\s*)?(금액|부가세|봉사료|합계|판매자정보|$)",
                1
        );
        String product = pickBestProductLine(productBlock);
        product = cleanProductName(product);
        System.out.println("[GMARKET] product=" + safe(product));

        // ---- 판매자정보 ----
        System.out.println("[GMARKET] ---- 판매자정보 ----");

        // 판매자정보 섹션에서 라벨-값 분리 구조 대응
        String sellerSection = sliceSection(text, "판매자정보", null, 2000);
        String merchant = extractGmarketSellerName(sellerSection);
        r.merchant.name = firstNonNull(merchant, "Unknown");

        String bizNo = extract(sellerSection, "([0-9]{3}-[0-9]{2}-[0-9]{5})");
        if (notEmpty(bizNo)) r.merchant.bizNo = cleanField(bizNo);

        String tel = extract(sellerSection, "([0-9]{2,4}-[0-9]{3,4}-[0-9]{4})");
        if (notEmpty(tel)) r.merchant.tel = cleanField(tel);

        // 주소: "사업장주소" 이후 실제 주소값 (일반과세자 같은 과세유형 제외)
        String addr = extractGmarketAddress(sellerSection);
        if (notEmpty(addr)) r.merchant.address = cleanField(addr);

        System.out.println("[GMARKET] sellerName=" + safe(r.merchant.name));
        System.out.println("[GMARKET] bizNo=" + safe(r.merchant.bizNo));
        System.out.println("[GMARKET] tel=" + safe(r.merchant.tel));
        System.out.println("[GMARKET] address=" + safe(r.merchant.address));

        // ---- 금액정보 ----
        System.out.println("[GMARKET] ---- 금액정보 ----");

        AmountBundle ab = parseSalesSlipAmounts(text);
        if (ab != null) {
            r.totals.taxable = ab.amount;
            r.totals.vat = ab.vat;
            r.totals.total = ab.total;
            trySetApprovalAmt(r.payment, ab.total);
        }

        System.out.println("[GMARKET] taxable=" + safeInt(r.totals.taxable) + ", vat=" + safeInt(r.totals.vat) + ", total=" + safeInt(r.totals.total));

        r.items = buildSingleItem(product, r.totals.total, r.totals.vat);
        System.out.println("[GMARKET] item => name=" + safe(product) + " | taxFlag=" + safe(r.items.isEmpty() ? null : r.items.get(0).taxFlag));

        if (DEBUG) System.out.println("---- [DEBUG] parseSalesSlipEnglish END ----");
        return r;
    }

    /** 지마켓 OCR 라벨-값 분리 구조에서 상호명 추출 */
    private String extractGmarketSellerName(String section) {
        if (section == null) return null;
        String[] lines = section.replace("\r", "\n").split("\n");

        Pattern BIZ = Pattern.compile("^[0-9]{3}-[0-9]{2}-[0-9]{5}$");
        Set<String> LABELS = new HashSet<>(Arrays.asList(
                "상호", "사업자등록번호", "대표자명", "전화번호", "과세유형", "사업장주소", "판매자정보"
        ));

        // 사업자번호 위치 찾기
        int bizIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (BIZ.matcher(lines[i].trim()).matches()) { bizIdx = i; break; }
        }

        if (bizIdx > 0) {
            // 값들을 역방향 수집: 사업자번호 앞 값 목록
            List<Integer> valueLines = new ArrayList<>();
            for (int j = bizIdx - 1; j >= 0; j--) {
                String t = lines[j].trim();
                if (t.isEmpty()) continue;
                if (LABELS.contains(t.replace(" ", ""))) continue;
                if (BIZ.matcher(t).matches()) continue;
                if (t.matches("[0-9\\-/\\*\\s]+")) continue;
                if (t.matches(".*[시도군구].*")) continue;
                if (t.matches("(일반과세자|간이과세자|면세사업자)")) continue;
                valueLines.add(j);
            }
            System.out.println("[GMARKET.seller] bizIdx=" + bizIdx + ", valueLines=" + valueLines);
            // valueLines[0]=대표자명값, valueLines[1]=상호명값
            if (valueLines.size() >= 2) return lines[valueLines.get(1)].trim();
            if (valueLines.size() == 1) return lines[valueLines.get(0)].trim();
        }

        // fallback: "상호" 라벨 다음에 오는 비-라벨 값
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("상호") && i + 1 < lines.length) {
                String next = lines[i + 1].trim();
                if (!LABELS.contains(next.replace(" ", "")) && !next.matches("[0-9\\-]+")) return next;
            }
        }
        return null;
    }

    /** 지마켓 주소 추출 (과세유형 라인 제외) */
    private String extractGmarketAddress(String section) {
        if (section == null) return null;
        String[] lines = section.replace("\r", "\n").split("\n");
        boolean inAddr = false;
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String t = line.trim();
            if (t.replace(" ", "").equals("사업장주소")) { inAddr = true; continue; }
            if (!inAddr) continue;
            if (t.isEmpty()) continue;
            if (t.matches("(일반과세자|간이과세자|면세사업자)")) continue;
            if (t.startsWith("본 영수증") || t.startsWith("본영수증")) break;
            if (sb.length() > 0) sb.append(" ");
            sb.append(t);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** 승인번호: 6~12자리 독립 숫자, 카드번호/주문번호/날짜와 구분 */
    private String extractApprovalNoGmarket(String text) {
        String[] lines = text.replace("\r", "\n").split("\n");
        for (String line : lines) {
            String t = line.trim();
            // 순수 6~12자리 숫자 라인
            if (t.matches("[0-9]{6,12}")) return t;
        }
        return null;
    }

    /* ========================= SalesSlip 금액 파싱 ========================= */

    private static class AmountBundle {
        Integer amount;
        Integer vat;
        Integer svc;
        Integer total;
    }

    /**
     * 예) ... 금액/부가세/봉사료/합계 라벨이 먼저 나오고
     *     맨 아래에
     *       13410
     *       0
     *       13,410
     *     이런 식으로 나오는 케이스 대응
     */
    private AmountBundle parseSalesSlipAmounts(String text) {
        int idx = text.indexOf("금액");
        if (idx < 0) idx = text.indexOf("부가세");
        if (idx < 0) idx = text.indexOf("합계");

        if (idx < 0) return null;

        String tail = text.substring(idx);

        // 라인 단위 숫자만 수집 (bizNo/전화번호/카드번호 등은 대부분 걸러짐)
        List<Integer> nums = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s*(\\d{1,3}(?:,\\d{3})+|\\d{1,8})\\s*$").matcher(tail);
        while (m.find()) {
            Integer v = toInt(m.group(1));
            if (v != null) nums.add(v);
        }

        if (DEBUG) {
            System.out.println("[DEBUG.amounts] candidates=" + nums);
        }

        if (nums.size() < 2) return null;

        AmountBundle ab = new AmountBundle();

        // 흔히 3개: amount, vat, total
        // 4개: amount, vat, svc, total
        if (nums.size() >= 4) {
            // 마지막 4개를 사용 (중간에 0이 하나 더 끼는 케이스 방어)
            List<Integer> last4 = nums.subList(nums.size() - 4, nums.size());
            ab.amount = last4.get(0);
            ab.vat = last4.get(1);
            ab.svc = last4.get(2);
            ab.total = last4.get(3);

            // 합계 검증 후 이상하면 3개 모드로 다운그레이드
            if (!isSumPlausible(ab.amount, ab.vat, ab.svc, ab.total)) {
                List<Integer> last3 = nums.subList(nums.size() - 3, nums.size());
                ab.amount = last3.get(0);
                ab.vat = last3.get(1);
                ab.svc = null;
                ab.total = last3.get(2);
            }
        } else {
            List<Integer> last3 = nums.subList(nums.size() - 3, nums.size());
            ab.amount = last3.get(0);
            ab.vat = last3.get(1);
            ab.svc = null;
            ab.total = last3.get(2);
        }

        // 최종 보정
        if (ab.total == null && ab.amount != null && ab.vat != null) ab.total = ab.amount + ab.vat;
        if (ab.amount == null && ab.total != null && ab.vat != null) ab.amount = ab.total - ab.vat;

        if (DEBUG) {
            System.out.println("[DEBUG.amounts] mapped amount=" + ab.amount + ", vat=" + ab.vat + ", svc=" + ab.svc + ", total=" + ab.total);
        }

        return ab;
    }

    private boolean isSumPlausible(Integer amount, Integer vat, Integer svc, Integer total) {
        if (amount == null || vat == null || total == null) return false;
        int s = amount + vat + (svc == null ? 0 : svc);
        return Math.abs(s - total) <= 1;
    }

    /* ========================= Items ========================= */

    private List<Item> buildSingleItem(String productName, Integer totalAmount) {
        return buildSingleItem(productName, totalAmount, null);
    }

    private List<Item> buildSingleItem(String productName, Integer totalAmount, Integer vat) {
        Item it = new Item();
        it.name = notEmpty(productName) ? productName : "상품";
        it.qty = 1;
        it.amount = totalAmount;
        it.unitPrice = totalAmount;
        if (vat != null && vat > 0) {
            it.taxFlag = "과세";
        } else if (vat != null && vat == 0) {
            it.taxFlag = "면세";
        }
        return List.of(it);
    }

    /* ========================= 상세 로그 ========================= */

    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");

        System.out.println("[MERCHANT] name: " + safe(getMerchantName(r)));
        try { System.out.println("[MERCHANT] (reflection) " + reflectFields(getMerchant(r))); } catch (Exception ignore) {}

        System.out.println("[META] receiptNo(orderNo): " + safe(getMetaReceiptNo(r)));
        System.out.println("[META] saleDate: " + safe(getMetaSaleDate(r)));
        System.out.println("[META] saleTime: " + safe(getMetaSaleTime(r)));
        try { System.out.println("[META] (reflection) " + reflectFields(getMeta(r))); } catch (Exception ignore) {}

        System.out.println("[PAYMENT] type: " + safe(getPaymentType(r)));
        System.out.println("[PAYMENT] cardBrand: " + safe(getPaymentCardBrand(r)));
        System.out.println("[PAYMENT] cardMasked: " + safe(getPaymentCardMasked(r)));
        try { System.out.println("[PAYMENT] (reflection) " + reflectFields(getPayment(r))); } catch (Exception ignore) {}

        System.out.println("[APPROVAL] approvalNo: " + safe(getApprovalNo(r)));
        try { System.out.println("[APPROVAL] (reflection) " + reflectFields(getApproval(r))); } catch (Exception ignore) {}

        System.out.println("[TOTALS] total: " + safeInt(getTotalsTotal(r)));
        System.out.println("[TOTALS] taxable: " + safeInt(getTotalsTaxable(r)));
        System.out.println("[TOTALS] vat: " + safeInt(getTotalsVat(r)));
        System.out.println("[TOTALS] taxFree: " + safeInt(getTotalsTaxFree(r)));
        try { System.out.println("[TOTALS] (reflection) " + reflectFields(getTotals(r))); } catch (Exception ignore) {}

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
                try { System.out.println("    [ITEM reflection] " + reflectFields(it)); } catch (Exception ignore) {}
            }
        }

        try { System.out.println("[ROOT reflection] " + reflectFields(r)); } catch (Exception ignore) {}
        System.out.println("---------------------------------");
    }

    /* ========================= getter (NPE-safe) ========================= */

    private Merchant getMerchant(ReceiptResult r) { return (r == null ? null : r.merchant); }
    private Meta getMeta(ReceiptResult r) { return (r == null ? null : r.meta); }
    private Payment getPayment(ReceiptResult r) { return (r == null ? null : r.payment); }
    private Approval getApproval(ReceiptResult r) { return (r == null ? null : r.approval); }
    private Totals getTotals(ReceiptResult r) { return (r == null ? null : r.totals); }

    private String getMerchantName(ReceiptResult r) { Merchant m = getMerchant(r); return (m == null ? null : m.name); }
    private String getMetaReceiptNo(ReceiptResult r) { Meta m = getMeta(r); return (m == null ? null : m.receiptNo); }
    private String getMetaSaleDate(ReceiptResult r) { Meta m = getMeta(r); return (m == null ? null : m.saleDate); }
    private String getMetaSaleTime(ReceiptResult r) { Meta m = getMeta(r); return (m == null ? null : m.saleTime); }
    private String getPaymentType(ReceiptResult r) { Payment p = getPayment(r); return (p == null ? null : p.type); }
    private String getPaymentCardBrand(ReceiptResult r) { Payment p = getPayment(r); return (p == null ? null : p.cardBrand); }
    private String getPaymentCardMasked(ReceiptResult r) { Payment p = getPayment(r); return (p == null ? null : p.cardMasked); }
    private String getApprovalNo(ReceiptResult r) { Approval a = getApproval(r); return (a == null ? null : a.approvalNo); }
    private Integer getTotalsTotal(ReceiptResult r) { Totals t = getTotals(r); return (t == null ? null : t.total); }
    private Integer getTotalsTaxable(ReceiptResult r) { Totals t = getTotals(r); return (t == null ? null : t.taxable); }
    private Integer getTotalsVat(ReceiptResult r) { Totals t = getTotals(r); return (t == null ? null : t.vat); }
    private Integer getTotalsTaxFree(ReceiptResult r) { Totals t = getTotals(r); return (t == null ? null : t.taxFree); }

    /* ========================= Debug helpers ========================= */

    private String debugExtract(String label, String text, String regex, int group) {
        String v = extract(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extract] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private Integer debugFirstMoney(String label, String text, String regex, int group) {
        String s = extract(text, regex, group);
        Integer n = toInt(s);
        if (DEBUG) {
            System.out.println("[DEBUG.money] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  raw  = " + (s == null ? "null" : ("'" + s + "'")));
            System.out.println("  int  = " + (n == null ? "null" : n));
        }
        return n;
    }

    private String debugFindDateNearLabel(String label, String text, String anchorLabel) {
        String d = findDateNearLabel(text, anchorLabel);
        if (DEBUG) {
            System.out.println("[DEBUG.dateNear] " + label + " anchor=" + anchorLabel + " => " + (d == null ? "null" : ("'" + d + "'")));
        }
        return d;
    }

    /* ========================= Common utils ========================= */

    private String normalizeKeepNewlines(String raw) {
        String x = raw.replace("\r\n", "\n").replace("\r", "\n");
        x = x.replaceAll("[\\t\\x0B\\f]+", " ");
        // 라인 내부 다중 공백 축소 (줄바꿈은 유지)
        x = x.replaceAll("[ ]{2,}", " ");
        return x.trim();
    }

    protected String extract(String text, String regex) { return extract(text, regex, 1); }

    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            return m.find() ? m.group(Math.min(group, m.groupCount())).trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    protected Integer toInt(String s) {
        try {
            return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }

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

        // 흔한 꼬리/라벨 제거
        s = s.replaceAll("(과세금액|비과세금액|부가세|합계금액|합계|거래금액|판매자정보|업체명|대표자|사업자등록번호|가맹점번호|가맹점주소|문의\\s*연락처|할부구분).*", "").trim();

        // 끝 특수문자 정리
        s = s.replaceAll("[,.:/\\-]+$", "").trim();
        return s;
    }

    private String stripKnownNoiseMerchant(String merchant) {
        if (merchant == null) return null;
        merchant = merchant.replaceAll("(Auction전자지불|Auction\\s*전자지불|Gmarket전자지불|지마켓전자지불)$", "").trim();
        merchant = merchant.replaceAll("\\s{2,}", " ").trim();
        return merchant;
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        String x = s.replaceAll("\\s+", "");
        if (x.equalsIgnoreCase("BC")) return "BC카드";
        if (x.equals("비씨")) return "비씨카드";
        if (x.equals("비씨카드")) return "비씨카드";
        if (x.equals("BC카드")) return "BC카드";
        // "비씨카드카드" 같은 이상 케이스 방지
        if (x.endsWith("카드카드")) x = x.substring(0, x.length() - 2);
        // "비씨유효기간" 같은 꼬리 제거
        x = x.replaceAll("(유효기간).*", "");
        return x.trim();
    }

    private String normalizeCardMasked(String s) {
        if (s == null) return null;
        String x = cleanField(s);

        // 카드번호 라인에 다른 라벨이 섞이면 라벨 컷
        x = x.replaceAll("(거래종류|거래유형|유효기간|승인번호|거래일자|주문번호|상품명).*", "").trim();

        // 숫자/마스킹/*/- 외 문자들은 * 로 치환 (DocumentAI에서 _/알파벳 등 섞임 대응)
        x = x.replaceAll("[^0-9\\*Xx\\-]", "*");

        // 연속 * 정리
        x = x.replaceAll("\\*{3,}", "******");
        x = x.replaceAll("-{2,}", "-");
        x = x.replaceAll("\\s+", "").trim();
        return x;
    }

    private String findDateNearLabel(String text, String label) {
        int idx = text.indexOf(label);
        if (idx < 0) return null;
        int end = Math.min(text.length(), idx + 160);
        String near = text.substring(idx, end);
        Matcher m = Pattern.compile("(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})").matcher(near);
        return m.find() ? m.group(1) : null;
    }

    private String sliceSection(String text, String startLabel, String endLabel, int maxLen) {
        int s = text.indexOf(startLabel);
        if (s < 0) return "";
        int e;
        if (endLabel != null) {
            e = text.indexOf(endLabel, s + startLabel.length());
            if (e < 0) e = Math.min(text.length(), s + maxLen);
        } else {
            e = Math.min(text.length(), s + maxLen);
        }
        return text.substring(s, e);
    }

    private String pickBestProductLine(String block) {
        if (block == null) return null;

        // 줄 단위로 후보 생성
        String[] lines = block.replace("\r", "\n").split("\n");
        List<String> candidates = new ArrayList<>();
        for (String ln : lines) {
            String t = cleanField(ln);
            if (!notEmpty(t)) continue;

            // 라벨/노이즈 라인 제거
            if (isNoiseProductLine(t)) continue;

            // 금액만 있는 라인 제거 (단, 800g/3kg 같은 상품명은 허용)
            if (looksLikeMoneyOnly(t)) continue;

            candidates.add(t);
        }

        if (candidates.isEmpty()) {
            // 마지막 fallback: 공백 정리해서 반환
            String x = cleanField(block);
            return notEmpty(x) ? x : null;
        }

        // 가장 긴 라인(정보량 많은 라인)을 상품명으로 선택
        candidates.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return candidates.get(0);
    }

    private boolean isNoiseProductLine(String t) {
        // 상품명 블록에 자주 끼는 라벨/필드
        String[] noise = {
                "카드종류", "유효기간", "거래일자", "거래종류", "거래유형", "승인번호", "카드번호",
                "주문번호", "금액", "부가세", "봉사료", "합계", "판매자정보",
                "상호", "사업자등록번호", "대표자", "대표자명", "전화번호", "과세유형", "사업장주소",
                "업체명", "가맹점번호", "가맹점주소", "문의 연락처", "할부구분"
        };

        for (String n : noise) {
            if (t.equals(n)) return true;
            if (t.startsWith(n + " ")) return true;
            if (t.startsWith(n + ":")) return true;
        }

        // 거래 종류 값 단독 라인도 상품명이 아님
        if (t.equals("신용구매") || t.equals("신용거래") || t.equals("일시불")) return true;

        return false;
    }

    private boolean looksLikeMoneyOnly(String t) {
        // 13,410 / 3500원 같은 "금액만" 라인
        // (단, 800g/3kg 같이 상품명에 숫자+단위가 들어가는 케이스는 여기서 걸리지 않음)
        return t.matches("^\\d{1,3}(?:,\\d{3})+\\s*원?$") || t.matches("^\\d{1,8}\\s*원$");
    }
    
    private void trySetApprovalAmt(Payment payment, Integer amt) {
        if (payment == null || amt == null) return;
        try {
            java.lang.reflect.Field f = payment.getClass().getDeclaredField("approvalAmt");
            f.setAccessible(true);
            f.set(payment, amt);
        } catch (Exception ignore) {
            // approvalAmt 필드가 없으면 그냥 무시
        }
    }
}
