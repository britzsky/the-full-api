package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOfficeGenericReceiptParser v12.x
 *
 * - 하늘색 "신용카드 매출전표"(국문 라벨) 파싱
 * - 파란색 "Sales Slip (Credit Card)"(진파랑) 파싱
 * - ✅ 회색 "신용카드 매출전표 / SALES SLIP(CREDIT CARD)"(한/영 병기, SEQ NO/ORDER NO/APPROVAL NO/AMOUNT/TAXES/TOTAL) 파싱 추가
 * - 라벨 기반(DOTALL) 우선 / 실패 시 fallback
 * - 디버그 로그 + printFullResult(reflection) 제공
 */
public class HeadOffice11PostReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    @Override
    public ReceiptResult parse(Document doc) {
        String rawText = normalizeText(text(doc));

        System.out.println("=================================");
        System.out.println("=== 🧾 RAW TEXT (Generic Card Slip) ===");
        System.out.println(rawText);
        System.out.println("=================================");

        // ✅ 템플릿 감지
        SlipTemplate template = detectTemplate(rawText);
        System.out.println("🧭 인식된 템플릿: " + template);

        ReceiptResult r;
        if (template == SlipTemplate.COUPANG_APP) {
            r = parseAppVersion(rawText);
        } else if (template == SlipTemplate.GRAY_BILINGUAL_SLIP) {
            r = parseGrayBilingualSlip(rawText);
        } else if (template == SlipTemplate.BLUE_SALES_SLIP) {
            r = parseBlueSalesSlip(rawText);
        } else {
            r = parseLightSalesSlip(rawText);
        }

        // ✅ 요약 로그
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");
        System.out.println("템플릿: " + template);
        System.out.println("상호: " + safe(r.merchant != null ? r.merchant.name : null));
        System.out.println("주문번호/식별번호: " + safe(r.meta != null ? r.meta.receiptNo : null));
        System.out.println("거래일시: " + safe(r.meta != null ? r.meta.saleDate : null) + " " + safe(r.meta != null ? r.meta.saleTime : null));
        System.out.println("결제수단: " + safe(r.payment != null ? r.payment.type : null) + " / " + safe(r.payment != null ? r.payment.cardBrand : null));
        System.out.println("카드번호: " + safe(r.payment != null ? r.payment.cardMasked : null));
        System.out.println("승인번호: " + safe(r.approval != null ? r.approval.approvalNo : null));
        System.out.println("합계금액(total): " + safeInt(r.totals != null ? r.totals.total : null));
        System.out.println("과세금액: " + safeInt(r.totals != null ? r.totals.taxable : null) +
                " / 부가세: " + safeInt(r.totals != null ? r.totals.vat : null) +
                " / 비과세금액: " + safeInt(r.totals != null ? r.totals.taxFree : null));

        System.out.println("품목 수: " + (r.items != null ? r.items.size() : 0));
        if (r.items != null) {
            for (Item it : r.items) {
                System.out.println("  · " + safe(it.name)
                        + " | 수량:" + safe(it.qty)
                        + " | 단가:" + safeInt(it.unitPrice)
                        + " | 금액:" + safeInt(it.amount));
            }
        }
        System.out.println("---------------------------------");

        // ✅ 요청: 쿠팡 파서처럼 더 세세한 출력
        printFullResult(r);

        return r;
    }

    /* ========================= 템플릿 감지 ========================= */

    private enum SlipTemplate {
        COUPANG_APP,
        GRAY_BILINGUAL_SLIP,  // ✅ 추가
        BLUE_SALES_SLIP,
        LIGHT_SALES_SLIP
    }

    private SlipTemplate detectTemplate(String text) {
        // 1) 쿠팡앱 결제내역(기존)
        boolean hasCoupay = containsAny(text, "쿠팡(쿠페이)", "쿠페이");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = containsAny(text, "카드영수증", "구매정보");
        if (hasCoupay && hasMemo && !hasCardReceipt) return SlipTemplate.COUPANG_APP;

        // 2) ✅ 회색(한/영 병기) 전표 특징
        //    - SEQ NO, ORDER NO, CARD NO, APPROVAL NO
        //    - AMOUNT / TAXES / TOTAL
        boolean hasSeq = containsAny(text, "SEQ NO", "SEQ");
        boolean hasOrderNoEn = containsAny(text, "ORDER NO");
        boolean hasApprovalEn = containsAny(text, "APPROVAL NO");
        boolean hasAmountTableEn = containsAny(text, "AMOUNT", "TAXES", "TOTAL");
        boolean hasShopName = containsAny(text, "SHOP NAME", "SELLER ADDRESS", "SHOP NO");

        if ((hasSeq && hasApprovalEn && hasAmountTableEn) || (hasOrderNoEn && hasApprovalEn && hasShopName)) {
            return SlipTemplate.GRAY_BILINGUAL_SLIP;
        }

        // 3) 파란색 Sales Slip 특징(기존)
        //    - 판매자정보/봉사료/과세유형/사업장주소 등
        boolean hasBlueTitle = containsAny(text, "Sales Slip", "Credit Card", "Sales Slip (Credit Card)");
        boolean hasSellerInfo = containsAny(text, "판매자정보", "판매자 정보");
        boolean hasBlueFields = containsAny(text, "봉사료", "과세유형", "사업장주소", "유효기간");
        if (hasSellerInfo || (hasBlueTitle && hasBlueFields)) return SlipTemplate.BLUE_SALES_SLIP;

        return SlipTemplate.LIGHT_SALES_SLIP;
    }
    /* ========================= 1) 쿠팡 앱 결제내역(기존 유지) ========================= */

    private ReceiptResult parseAppVersion(String text) {
        ReceiptResult r = new ReceiptResult();
        r.merchant.name = "쿠팡";

        String totalStr = extract(text, "쿠팡\\(쿠페이\\)\\s*[-]?\\s*([0-9,]+)\\s*원");
        if (totalStr == null) totalStr = extract(text, "(-?[0-9,]+)\\s*원");
        r.totals.total = toInt(totalStr);

        r.payment.cardBrand = firstNonNull(extract(text, "(쿠페이)"), extract(text, "(쿠팡페이)"));
        r.payment.type = "간편결제";
        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");
        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);

        String memoItem = firstNonNull(
                extractDot(text, "(?s)거래메모\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s:/,\\.\\-()]{2,60})\\s*(결제|승인|$)", 1),
                extract(text, "([가-힣A-Za-z0-9]+\\s?(절단미역|쌀강정|세제|쿠키|강정|미역))")
        );

        Item it = new Item();
        it.name = (memoItem != null ? memoItem : "쿠팡 구매상품").trim();
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        System.out.println("[APP] memoItem=" + it.name + ", total=" + safeInt(r.totals.total));
        return r;
    }

    /* ========================= 2) ✅ 회색(한/영 병기) 전표 파싱 ========================= */

    private ReceiptResult parseGrayBilingualSlip(String text) {
        System.out.println("=== ▶ parseGrayBilingualSlip START ===");

        ReceiptResult r = new ReceiptResult();

        // 주문번호/ORDER NO (17자리 등 길 수 있음)
        r.meta.receiptNo = firstNonNull(
                debugExtract("orderNo_ko", text, "주문번호\\s*/?\\s*ORDER\\s*NO\\.?\\s*[:：]?\\s*([0-9]{8,30})", 1),
                debugExtract("orderNo_en", text, "ORDER\\s*NO\\.?\\s*[:：]?\\s*([0-9]{8,30})", 1)
        );

        // 카드종류/CARD TYPE
        String cardBrand = firstNonNull(
                debugExtract("cardType_mix", text, "카드종류\\s*/?\\s*CARD\\s*TYPE\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1),
                debugExtract("cardType_en", text, "CARD\\s*TYPE\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1)
        );
        r.payment.cardBrand = normalizeCardBrand(cleanField(cardBrand));

        // 카드번호/CARD NO
        String cardNo = firstNonNull(
                debugExtract("cardNo_mix", text, "카드번호\\s*/?\\s*CARD\\s*NO\\.?\\s*[:：]?\\s*([0-9\\-*]{7,25})", 1),
                debugExtract("cardNo_en", text, "CARD\\s*NO\\.?\\s*[:：]?\\s*([0-9\\-*]{7,25})", 1),
                debugExtract("cardNo_fallback", text, "([0-9]{4}[0-9]{2}\\*+\\*+\\*+[0-9]{3,4})", 1)
        );
        r.payment.cardMasked = normalizeMaskedCard(cleanField(cardNo));

        // 거래일자/TRANS DATE (날짜만)
        String transDate = firstNonNull(
                debugExtract("transDate_mix", text, "거래일자\\s*/?\\s*TRANS\\s*DATE\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1),
                debugExtract("transDate_en", text, "TRANS\\s*DATE\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1)
        );
        r.meta.saleDate = normalizeDate(transDate);
        r.meta.saleTime = null; // 회색 전표는 일시는 안 나오거나 별도 필드일 수 있음

        // 거래종류/TRANS CLASS
        String transClass = firstNonNull(
                debugExtract("transClass_mix", text, "거래종류\\s*/?\\s*TRANS\\s*CLASS\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1),
                debugExtract("transClass_en", text, "TRANS\\s*CLASS\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1)
        );
        r.payment.type = notEmpty(transClass) ? cleanField(transClass) : "신용거래";

        // 할부/INSTALLMENT
        String installment = firstNonNull(
                debugExtract("installment_mix", text, "할부\\s*/?\\s*INSTALLMENT\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1),
                debugExtract("installment_en", text, "INSTALLMENT\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+)", 1)
        );
        // ReceiptResult에 할부필드가 없으니 로그만 남김
        System.out.println("[GRAY] installment=" + safe(installment));

        // 품명/DESCRIPTION (다음 라벨 전까지)
        String desc = firstNonNull(
                debugExtractDot("desc_mix", text,
                        "(?s)품명\\s*/?\\s*DESCRIPTION\\s*[:：]?\\s*([\\s\\S]*?)\\s*(거래유형\\s*/?\\s*TRANS\\s*TYPE|통신판매업자\\s*상호\\s*/?\\s*SHOP\\s*NAME|SHOP\\s*NAME|AMOUNT|TAXES|TOTAL|$)",
                        1),
                debugExtractDot("desc_en", text,
                        "(?s)DESCRIPTION\\s*[:：]?\\s*([\\s\\S]*?)\\s*(TRANS\\s*TYPE|SHOP\\s*NAME|AMOUNT|TAXES|TOTAL|$)",
                        1)
        );
        desc = cleanProductName(desc);

        // SHOP NAME (통신판매업자 상호/SHOP NAME) → merchant
        String shopName = firstNonNull(
                debugExtractDot("shopName_mix", text,
                        "(?s)통신판매업자\\s*상호\\s*/?\\s*SHOP\\s*NAME\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자\\s*/?\\s*MASTER|사업자등록번호\\s*/?\\s*SHOP\\s*NO\\.|SELLER\\s*PHONE|SELLER\\s*ADDRESS|$)",
                        1),
                debugExtractDot("shopName_en", text,
                        "(?s)SHOP\\s*NAME\\s*[:：]?\\s*([\\s\\S]*?)\\s*(MASTER|SHOP\\s*NO\\.|SELLER\\s*PHONE|SELLER\\s*ADDRESS|$)",
                        1)
        );
        shopName = cleanField(shopName);
        r.merchant.name = notEmpty(shopName) ? shopName : "가맹점";

        // 승인번호/APPROVAL NO
        r.approval.approvalNo = firstNonNull(
                debugExtract("approval_mix", text, "(승인번호\\s*/?\\s*APPROVAL\\s*NO\\.?|APPROVAL\\s*NO\\.?|승인번호)\\s*[:：]?\\s*([0-9]{6,12})", 2),
                debugExtract("approval_fallback", text, "APPROVAL\\s*NO\\.?\\s*[:：]?\\s*([0-9]{6,12})", 1)
        );

        // ✅ 금액 테이블: AMOUNT / TAXES / TOTAL (자리 띄어쓰기 OCR 대응)
        Integer amount = extractSpacedMoneyAfterLabel(text, "(?i)AMOUNT");
        Integer taxes  = extractSpacedMoneyAfterLabel(text, "(?i)TAXES");
        Integer total  = extractSpacedMoneyAfterLabel(text, "(?i)TOTAL");

        System.out.println("[GRAY] amount=" + safeInt(amount) + ", taxes=" + safeInt(taxes) + ", total=" + safeInt(total));

        // fallback: 한글(금액/세금/합계)로도 한번 더
        if (amount == null) amount = extractSpacedMoneyAfterLabel(text, "금액");
        if (taxes == null)  taxes  = extractSpacedMoneyAfterLabel(text, "세금|부가세");
        if (total == null)  total  = extractSpacedMoneyAfterLabel(text, "합계");

        // total 없으면 amount+taxes로 추정
        if (total == null && amount != null) {
            total = amount + (taxes != null ? taxes : 0);
            System.out.println("[GRAY] total missing -> inferred total=amount+taxes = " + total);
        }

        r.totals.taxable = amount;
        r.totals.vat = taxes;
        r.totals.total = total;
        r.totals.taxFree = null;

        // 아이템 1개 구성
        Item it = new Item();
        it.name = notEmpty(desc) ? desc : "품목";
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        System.out.println("[GRAY] ✅ FINAL => merchant=" + safe(r.merchant.name)
                + ", orderNo=" + safe(r.meta.receiptNo)
                + ", approval=" + safe(r.approval.approvalNo)
                + ", total=" + safeInt(r.totals.total)
                + ", item=" + safe(it.name));

        System.out.println("=== ◀ parseGrayBilingualSlip END ===");
        return r;
    }

    /**
     * AMOUNT/TAXES/TOTAL 뒤에 나오는 금액을 최대한 관대하게 추출
     * - OCR이 "₩ 9 0 9 1" 처럼 자리별로 띄워도 digits만 모아서 9091로 만든다.
     */
    private Integer extractSpacedMoneyAfterLabel(String text, String labelRegex) {
        if (text == null) return null;

        // label 이후 1~80자 정도를 먹고, 그 안에서 숫자/콤마/공백을 최대한 관대하게 추출
        String regex = labelRegex + "[^0-9]{0,20}([0-9][0-9\\s,]{0,30})";
        String raw = extract(text, regex, 1);
        if (DEBUG) {
            System.out.println("[DEBUG.money] label=" + labelRegex);
            System.out.println("  regex=" + regex);
            System.out.println("  raw=" + (raw == null ? "null" : ("'" + raw + "'")));
        }
        if (raw == null) return null;

        // digits만 모음
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;

        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return null;
        }
    }

    /* ========================= 3) 파란색 Sales Slip (기존) ========================= */

    private ReceiptResult parseBlueSalesSlip(String text) {
        System.out.println("=== ▶ parseBlueSalesSlip START ===");

        ReceiptResult r = new ReceiptResult();

        r.meta.receiptNo = firstNonNull(
                extract(text, "주문번호\\s*[:：]?\\s*([0-9]{8,})", 1),
                extract(text, "주문\\s*번호\\s*[:：]?\\s*([0-9]{8,})", 1)
        );
        System.out.println("[BLUE] receiptNo=" + safe(r.meta.receiptNo));

        String cardBrand = firstNonNull(
                extractDot(text, "(?s)카드종류\\s*[:：]?\\s*([가-힣A-Za-z0-9\\s]+?)\\s*(유효기간|카드번호|승인번호|거래일시|$)", 1),
                extract(text, "(비씨|BC|BC카드|비씨카드|국민|신한|현대|롯데|농협|하나|KB|NH)", 1)
        );
        r.payment.cardBrand = normalizeCardBrand(cleanField(cardBrand));
        System.out.println("[BLUE] cardBrand(raw)=" + cardBrand + " => " + r.payment.cardBrand);

        r.payment.cardMasked = firstNonNull(
                extract(text, "카드번호\\s*[:：]?\\s*([0-9]{4}[- ]?[0-9]{2}\\*{2}[- ]?\\*{4}[- ]?\\*{4}[- ]?[0-9]{3,4})", 1),
                extract(text, "([0-9]{4}\\*+\\d{2,6}\\*?\\d{0,6})", 1)
        );
        r.payment.cardMasked = normalizeMaskedCard(r.payment.cardMasked);
        System.out.println("[BLUE] cardMasked=" + safe(r.payment.cardMasked));

        r.approval.approvalNo = firstNonNull(
                extract(text, "승인번호\\s*[:：]?\\s*([0-9]{6,12})", 1),
                extract(text, "승인\\s*번호\\s*[:：]?\\s*([0-9]{6,12})", 1)
        );
        System.out.println("[BLUE] approvalNo=" + safe(r.approval.approvalNo));

        String dt = extractDot(text, "(?s)거래일시\\s*[:：]?\\s*([0-9]{4}[-./][0-9]{1,2}[-./][0-9]{1,2}\\s+[0-9]{1,2}:[0-9]{2}:[0-9]{2}\\s*(AM|PM)?)", 1);
        System.out.println("[BLUE] datetime=" + safe(dt));
        if (notEmpty(dt)) {
            String date = extract(dt, "([0-9]{4}[-./][0-9]{1,2}[-./][0-9]{1,2})");
            String time = extract(dt, "([0-9]{1,2}:[0-9]{2}:[0-9]{2}\\s*(AM|PM)?)");
            r.meta.saleDate = normalizeDate(date);
            r.meta.saleTime = normalizeTime(time);
        } else {
            r.meta.saleDate = normalizeDate(extract(text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})"));
            r.meta.saleTime = normalizeTime(extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d\\s*(AM|PM)?)"));
        }
        System.out.println("[BLUE] saleDate=" + safe(r.meta.saleDate) + ", saleTime=" + safe(r.meta.saleTime));

        Integer amount = firstInt(text, "금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        Integer vat = firstInt(text, "부가세\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        Integer tip = firstInt(text, "봉사료\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        Integer total = firstInt(text, "합계\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");

        System.out.println("[BLUE] amount=" + safeInt(amount) + ", vat=" + safeInt(vat) + ", tip=" + safeInt(tip) + ", total=" + safeInt(total));

        if (total == null) {
            if (amount != null) {
                total = amount;
                if (vat != null) total += vat;
                if (tip != null) total += tip;
            }
        }
        r.totals.total = total;
        r.totals.vat = vat;

        if (r.totals.total != null && r.totals.vat != null && r.totals.total >= r.totals.vat) {
            r.totals.taxable = r.totals.total - r.totals.vat;
            System.out.println("[BLUE] inferred taxable = total - vat = " + r.totals.taxable);
        }

        String merchant = extractDot(text,
                "(?s)(판매자정보|판매자\\s*정보)\\s*.*?상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(사업자등록번호|대표자명|과세유형|전화번호|사업장주소|$)", 2);
        merchant = cleanField(merchant);

        // 판매자상호 레이블 뒤에 대표자명 레이블이 오고 그 다음 줄에 상호명이 있는 구조 처리
        // ex) "판매자상호\n대표자명\n주방다움\n홍세미"
        if (!notEmpty(merchant)) {
            merchant = extract(text,
                    "판매자상호\\s*대표자명\\s*([^\\n]+)");
            merchant = cleanField(merchant);
        }

        if (!notEmpty(merchant)) {
            merchant = extractDot(text, "(?s)상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(사업자등록번호|대표자명|과세유형|전화번호|사업장주소|$)", 1);
            merchant = cleanField(merchant);
        }

        r.merchant.name = notEmpty(merchant) ? merchant : "가맹점";
        System.out.println("[BLUE] merchant=" + r.merchant.name);

        r.payment.type = firstNonNull(
                cleanField(extractDot(text, "(?s)거래유형\\s*[:：]?\\s*([\\s\\S]*?)\\s*(거래종류|일시불|$)", 1)),
                cleanField(extractDot(text, "(?s)거래종류\\s*[:：]?\\s*([\\s\\S]*?)\\s*(상품명|금액|$)", 1)),
                "신용거래"
        );
        System.out.println("[BLUE] payment.type=" + r.payment.type);

        r.items = parseItemsByProductLabel(text, r.totals.total,
                "(금액|부가세|봉사료|합계|판매자정보|판매자\\s*정보|상호|사업자등록번호|$)");

        System.out.println("=== ◀ parseBlueSalesSlip END ===");
        return r;
    }

    /* ========================= 4) 하늘색(국문) 전표 ========================= */

    private ReceiptResult parseLightSalesSlip(String text) {
        System.out.println("=== ▶ parseLightSalesSlip START ===");

        ReceiptResult r = new ReceiptResult();

        String cardBrand = extractDot(text,
                "(?s)카드종류\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드번호|거래종류|거래금액|$)", 1);
        cardBrand = cleanField(cardBrand);
        r.payment.cardBrand = normalizeCardBrand(firstNonNull(cardBrand, extract(text, "(비씨|BC|BC카드|비씨카드|국민|신한|현대|롯데|농협|하나|KB|NH)", 1)));
        System.out.println("[LIGHT] cardBrand(raw)=" + safe(cardBrand) + " => " + safe(r.payment.cardBrand));

        r.payment.cardMasked = firstNonNull(
                extract(text, "카드번호\\s*[:：]?\\s*([0-9\\-*]{7,25})", 1),
                extract(text, "([0-9]{4}\\*+\\d{2,6}\\*?\\d{0,6})", 1)
        );
        r.payment.cardMasked = normalizeMaskedCard(r.payment.cardMasked);
        System.out.println("[LIGHT] cardMasked=" + safe(r.payment.cardMasked));

        String tradeType = extractDot(text,
                "(?s)거래종류\\s*[:：]?\\s*([\\s\\S]*?)\\s*(거래금액|거래일자|승인번호|$)", 1);
        tradeType = cleanField(tradeType);
        r.payment.type = notEmpty(tradeType) ? tradeType : "신용거래";
        System.out.println("[LIGHT] tradeType=" + safe(r.payment.type));

        String date = firstNonNull(
                extract(text, "거래일자\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1),
                extract(text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1)
        );
        r.meta.saleDate = normalizeDate(date);
        r.meta.saleTime = null;
        System.out.println("[LIGHT] saleDate=" + safe(r.meta.saleDate));

        r.approval.approvalNo = firstNonNull(
                extract(text, "승인번호\\s*[:：]?\\s*([0-9]{6,12})", 1),
                extract(text, "승인\\s*번호\\s*[:：]?\\s*([0-9]{6,12})", 1)
        );
        System.out.println("[LIGHT] approvalNo=" + safe(r.approval.approvalNo));

        String orderNo = firstNonNull(
                extract(text, "주문번호\\s*[:：]?\\s*([0-9]{8,})", 1),
                extract(text, "배송비결제번호\\s*[:：]?\\s*([0-9]{6,})", 1),
                extract(text, "배숭비결제번호\\s*[:：]?\\s*([0-9]{6,})", 1)
        );
        r.meta.receiptNo = orderNo;
        System.out.println("[LIGHT] receiptNo=" + safe(r.meta.receiptNo));

        Integer total = firstInt(text, "합계\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        Integer total2 = firstInt(text, "합계금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        Integer tradeAmount = firstInt(text, "거래금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");

        r.totals.total = firstNonNullInt(total, total2, tradeAmount);
        System.out.println("[LIGHT] totals.total=" + safeInt(r.totals.total) +
                " (합계=" + safeInt(total) + ", 합계금액=" + safeInt(total2) + ", 거래금액=" + safeInt(tradeAmount) + ")");

        r.totals.taxable = firstInt(text, "과세금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        r.totals.taxFree = firstInt(text, "비과세금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");
        r.totals.vat = firstInt(text, "부가세\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)");

        System.out.println("[LIGHT] taxable=" + safeInt(r.totals.taxable) + ", vat=" + safeInt(r.totals.vat) + ", taxFree=" + safeInt(r.totals.taxFree));

        String merchant = extractDot(text,
                "(?s)업체명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자|사업자등록번호|가맹점번호|가맹점주소|문의연락처|$)", 1);
        merchant = cleanField(merchant);

        if (!notEmpty(merchant)) {
            merchant = extractDot(text,
                    "(?s)판매자상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(사업자등록번호|판매자주소|가맹점주소|$)", 1);
            merchant = cleanField(merchant);
        }

        r.merchant.name = notEmpty(merchant) ? merchant : "가맹점";
        System.out.println("[LIGHT] merchant=" + r.merchant.name);

        r.items = parseItemsByProductLabel(text, r.totals.total,
                "(과세금액|비과세금액|부가세|합계금액|합계|업체명|대표자|사업자등록번호|가맹점번호|가맹점주소|문의연락처|$)");

        System.out.println("=== ◀ parseLightSalesSlip END ===");
        return r;
    }

    /* ========================= 품목 파싱(공통) ========================= */

    private List<Item> parseItemsByProductLabel(String text, Integer totalAmount, String endLabelRegex) {
        System.out.println("[ITEM] --- parseItemsByProductLabel ---");
        System.out.println("[ITEM] totalAmount=" + safeInt(totalAmount));
        System.out.println("[ITEM] endLabelRegex=" + endLabelRegex);

        String product = extractDot(text,
                "(?s)상품명\\s*[:：]?\\s*([\\s\\S]*?)\\s*" + endLabelRegex,
                1
        );
        product = cleanProductName(product);

        System.out.println("[ITEM] product(rawExtract)=" + safe(product));

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

            System.out.println("[ITEM] ✅ item.name=" + it.name);
            System.out.println("[ITEM] ✅ item.qty=" + it.qty);
            System.out.println("[ITEM] ✅ item.amount=" + safeInt(it.amount));
            System.out.println("[ITEM] ✅ item.unitPrice=" + safeInt(it.unitPrice));

            return List.of(it);
        }

        System.out.println("[ITEM] ❌ product label parse failed -> fallback item 생성");
        Item it = new Item();
        it.name = "품목";
        it.qty = 1;
        it.amount = totalAmount;
        it.unitPrice = totalAmount;
        return List.of(it);
    }

    /* ========================= printFullResult (요청한 출력) ========================= */

    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ FULL RESULT (debug) ------");

        // Merchant
        System.out.println("[MERCHANT] name: " + safe(getMerchantName(r)));
        try { System.out.println("[MERCHANT] (reflection) " + reflectFields(getMerchant(r))); } catch (Exception ignore) {}

        // Meta
        System.out.println("[META] receiptNo(orderNo): " + safe(getMetaReceiptNo(r)));
        System.out.println("[META] saleDate: " + safe(getMetaSaleDate(r)));
        System.out.println("[META] saleTime: " + safe(getMetaSaleTime(r)));
        try { System.out.println("[META] (reflection) " + reflectFields(getMeta(r))); } catch (Exception ignore) {}

        // Payment
        System.out.println("[PAYMENT] type: " + safe(getPaymentType(r)));
        System.out.println("[PAYMENT] cardBrand: " + safe(getPaymentCardBrand(r)));
        System.out.println("[PAYMENT] cardMasked: " + safe(getPaymentCardMasked(r)));
        try { System.out.println("[PAYMENT] (reflection) " + reflectFields(getPayment(r))); } catch (Exception ignore) {}

        // Approval
        System.out.println("[APPROVAL] approvalNo: " + safe(getApprovalNo(r)));
        try { System.out.println("[APPROVAL] (reflection) " + reflectFields(getApproval(r))); } catch (Exception ignore) {}

        // Totals
        System.out.println("[TOTALS] total: " + safeInt(getTotalsTotal(r)));
        System.out.println("[TOTALS] taxable: " + safeInt(getTotalsTaxable(r)));
        System.out.println("[TOTALS] vat: " + safeInt(getTotalsVat(r)));
        System.out.println("[TOTALS] taxFree: " + safeInt(getTotalsTaxFree(r)));
        try { System.out.println("[TOTALS] (reflection) " + reflectFields(getTotals(r))); } catch (Exception ignore) {}

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
                try { System.out.println("    [ITEM reflection] " + reflectFields(it)); } catch (Exception ignore) {}
            }
        }

        try { System.out.println("[ROOT reflection] " + reflectFields(r)); } catch (Exception ignore) {}
        System.out.println("---------------------------------");
    }

    /* ========================= getters (NPE-safe) ========================= */

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
            System.out.println("  regex=" + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private String debugExtractDot(String label, String text, String regex, int group) {
        String v = extractDot(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extractDot] " + label);
            System.out.println("  regex=" + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    /* ========================= 공통 유틸 ========================= */

    private String normalizeText(String s) {
        if (s == null) return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\u00A0]", " ");
        s = s.replaceAll("[\\t\\x0B\\f]+", " ");
        s = s.replaceAll(" +", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    protected String extract(String text, String regex) { return extract(text, regex, 1); }

    protected String extract(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) {
                int g = Math.min(group, m.groupCount());
                return m.group(g) != null ? m.group(g).trim() : null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    protected String extractDot(String text, String regex, int group) {
        try {
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
            if (m.find()) {
                int g = Math.min(group, m.groupCount());
                return m.group(g) != null ? m.group(g).trim() : null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    protected Integer toInt(String s) {
        try {
            return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    protected Integer firstInt(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return toInt(m.group(m.groupCount()));
        } catch (Exception ignore) {}
        return null;
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private Integer firstNonNullInt(Integer... nums) {
        for (Integer n : nums) {
            if (n != null && n >= 0) return n;
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
        s = s.replaceAll("(과세금액|비과세금액|부가세|합계금액|합계|금액|판매자정보|판매자\\s*정보|업체명|상호|AMOUNT|TAXES|TOTAL).*", "").trim();
        s = s.replaceAll("[,.:/\\-]+$", "").trim();
        return s;
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        String x = s.replaceAll("\\s+", "");
        if (x.equalsIgnoreCase("BC") || x.equals("비씨")) return "BC카드";
        if (x.contains("비씨") && !x.endsWith("카드")) return x + "카드";
        if (x.equals("비씨카드")) return "BC카드";
        if (x.equals("BC카드")) return "BC카드";
        if (x.equals("IBK비씨카드") || x.equals("IBK비씨카드카드")) return "IBK비씨카드";
        return s.trim();
    }

    private String normalizeMaskedCard(String s) {
        if (s == null) return null;
        String x = s.trim().replaceAll("\\s+", "");
        x = x.replaceAll("--+", "-");
        return x;
    }

    private String normalizeDate(String date) {
        if (date == null) return null;
        String d = date.trim().replace(".", "-").replace("/", "-");
        Matcher m = Pattern.compile("(20\\d{2})-([0-9]{1,2})-([0-9]{1,2})").matcher(d);
        if (m.find()) {
            String yy = m.group(1);
            String mm = String.format("%02d", Integer.parseInt(m.group(2)));
            String dd = String.format("%02d", Integer.parseInt(m.group(3)));
            return yy + "-" + mm + "-" + dd;
        }
        return d;
    }

    private String normalizeTime(String time) {
        if (time == null) return null;
        return time.trim().replaceAll("\\s+", " ");
    }

    /* ========================= reflectFields (빨간줄 방지: 클래스 내 포함) ========================= */

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
}
