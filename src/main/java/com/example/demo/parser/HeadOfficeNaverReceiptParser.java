package com.example.demo.parser;

import com.google.cloud.documentai.v1.Document;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HeadOfficeNaverReceiptParser v13.1
 * - Homeplus(홈플러스) 신용카드매출전표 + CoupangApp + Generic Card + ✅ Naver "카드 영수증" 지원
 * - 라벨 기반(DOTALL) 우선 + fallback
 * - 디버그 로그 극대화
 * - ✅ Naver 금액 파싱 candidates=[] 이슈 수정: "라인 단독 숫자" -> "어디든 숫자"로 추출
 * - ✅ Naver 상호명 폭주 방지 + (주)크로바케미칼 같은 케이스 정확히 잡기
 */
public class HeadOfficeNaverReceiptParser extends BaseReceiptParser {

    private static final boolean DEBUG = true;

    @Override
    public ReceiptResult parse(Document doc) {

        // ✅ 줄바꿈 유지 정규화 (라벨 파싱 안정화)
        String rawText = normalizeTextKeepNewlines(text(doc));

        System.out.println("=================================");
        System.out.println("=== 🧾 RAW TEXT (HomePlus/Generic) ===");
        System.out.println(rawText);
        System.out.println("=================================");

        boolean isHomeplus = isHomeplusSlip(rawText);
        boolean isApp = isCoupangAppReceipt(rawText);
        boolean isNaverCard = isNaverCardReceipt(rawText); // ✅ 추가

        System.out.println("🧭 인식된 유형:");
        System.out.println("  - HomeplusSlip? " + isHomeplus);
        System.out.println("  - CoupangApp?   " + isApp);
        System.out.println("  - NaverCard?    " + isNaverCard);

        ReceiptResult r;
        if (isHomeplus) {
            r = parseHomeplusSlip(rawText);
        } else if (isApp) {
            r = parseAppVersion(rawText);
        } else if (isNaverCard) {
            r = parseNaverCardReceipt(rawText); // ✅ 추가
        } else {
            r = parseCardVersion(rawText);
        }

        // ✅ 최종 결과 상세 출력
        printFullResult(r);

        return r;
    }

    /* ========================= ✅ 네이버 "카드 영수증" 감지 ========================= */

    private boolean isNaverCardReceipt(String text) {
        if (text == null) return false;

        boolean hasTitle = text.contains("카드 영수증");
        boolean hasSeller = text.contains("판매자 정보") || text.contains("판매자정보") || text.contains("판매자상호");
        boolean hasFranchise = text.contains("가맹점 정보") || text.contains("가맹점정보") || text.contains("가맹점명");
        boolean hasAmounts = text.contains("승인금액") && (text.contains("공급가액") || text.contains("부가세액")) && text.contains("합계");

        return hasTitle && hasSeller && hasFranchise && hasAmounts;
    }

    /* ========================= ✅ 네이버 "카드 영수증" 파싱 ========================= */

    private ReceiptResult parseNaverCardReceipt(String text) {
        System.out.println("=== ▶ parseNaverCardReceipt START ===");

        ReceiptResult r = new ReceiptResult();

        // 1) 카드사/승인번호: "비씨/50138672"
        String cardAndApproval = debugExtract("cardCompanyAndApproval", text,
                "(?m)카드사\\s*/\\s*승인번호\\s*\\n\\s*([^\\n]+)", 1);

        if (notEmpty(cardAndApproval)) {
            String[] parts = cardAndApproval.split("/");
            if (parts.length >= 1) r.payment.cardBrand = normalizeCardBrand(cleanField(parts[0]));
            if (parts.length >= 2) r.approval.approvalNo = cleanField(parts[1]);
        }

        // fallback: 승인번호만이라도
        if (!notEmpty(r.approval.approvalNo)) {
            r.approval.approvalNo = debugExtract("approvalNo_fallback", text,
                    "(?m)승인번호\\s*[:：]?\\s*([0-9]{6,12})", 1);
        }

        // 2) 카드번호(유효기간)
        String cardMaskedRaw = debugExtract("cardMaskedRaw", text,
                "(?m)카드번호\\(유효기간\\)\\s*\\n\\s*([^\\n]+)", 1);
        r.payment.cardMasked = normalizeCardMasked(cardMaskedRaw);

        // 3) 거래종류/할부: "신용(법인) / 일시불"
        String tradeInstall = debugExtract("tradeInstall", text,
                "(?m)거래종류\\s*/\\s*할부\\s*\\n\\s*([^\\n]+)", 1);
        if (notEmpty(tradeInstall)) {
            String[] parts = tradeInstall.split("/");
            r.payment.type = cleanField(parts[0]);
            if (parts.length >= 2) r.payment.installment = cleanField(parts[1]);
        }

        // 4) 결제일자: "2026-01-14 10:47:36"
        r.meta.saleDate = normalizeDate(debugExtract("saleDate", text,
                "(?m)결제일자\\s*\\n\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1));
        r.meta.saleTime = normalizeTime(debugExtract("saleTime", text,
                "(?m)결제일자\\s*\\n\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 2));

        // 5) 상품명 + 주문번호 (블록)
        String productBlock = debugExtractDot("productBlock", text,
                "(?s)상품명\\s*\\n([\\s\\S]*?)\\n\\s*판매자\\s*정보", 1);

        // 주문번호는 PD... 형태가 많음
        String orderNo = extract(productBlock == null ? "" : productBlock, "(PD[0-9A-Za-z]+)", 1);
        r.meta.receiptNo = cleanField(orderNo);

        // 상품명 라인 추출: 라벨 제거 후 가장 긴 라인
        String productName = pickBestProductLine(productBlock);
        // 상품명에 주문번호가 붙으면 제거
        if (notEmpty(orderNo) && notEmpty(productName)) {
            productName = productName.replace(orderNo, "").trim();
        }
        productName = cleanProductName(productName);

        // 6) ✅ 상호명(판매자상호) 이 OCR에서 비는 경우가 많음
        //    우선: "판매자상호" 다음 라인
        String sellerName = extractValueAfterLabel(text, "판매자상호", 12);
        sellerName = cleanField(sellerName);

        //    다음: "가맹점명" 다음 라인에서 (네이버파이낸셜 제외) → (주)크로바케미칼 케이스 해결
        String franchiseFirst = extractValueAfterLabel(text, "가맹점명", 12);
        franchiseFirst = cleanField(franchiseFirst);
        if (notEmpty(franchiseFirst) && !franchiseFirst.contains("네이버")) {
            // 이 타입에서 실제 판매자 상호가 여기 붙는 OCR이 많음
            sellerName = franchiseFirst;
        }

        //    fallback: 판매자/가맹점 섹션에서 회사명 후보 스캔 (네이버파이낸셜 제외)
        if (!notEmpty(sellerName) || isLooksLikeLabel(sellerName)) {
            String section = sliceSection(text, "가맹점 정보", "금액", 2000);
            String cand = findCompanyLikeLine(section, "네이버파이낸셜", "네이버");
            if (notEmpty(cand)) sellerName = cand;
        }
        if (!notEmpty(sellerName) || isLooksLikeLabel(sellerName)) {
            String section = sliceSection(text, "판매자 정보", "가맹점 정보", 2000);
            String cand = findCompanyLikeLine(section, "네이버파이낸셜", "네이버");
            if (notEmpty(cand)) sellerName = cand;
        }

        r.merchant.name = firstNonNull(cleanField(sellerName), "Unknown");

        // 7) 판매자 사업자등록번호: 두 개 중 판매자 쪽(네이버파이낸셜이 아닌 것) 우선
        List<String> bizNos = findAllBizNo(text);
        String sellerBiz = null;
        for (String b : bizNos) {
            // 네이버파이낸셜(가맹점) 사업자번호는 제외
            if ("524-86-01528".equals(b)) continue;
            sellerBiz = b;
            break;
        }
        if (!notEmpty(sellerBiz)) sellerBiz = (bizNos.isEmpty() ? null : bizNos.get(0));
        r.merchant.bizNo = cleanField(sellerBiz);

        // 8) 판매자 전화번호/주소 (판매자/가맹점 영역 혼재 가능 → 넓게 스캔)
        String sectionTelAddr = sliceSection(text, "판매자 정보", "금액", 4000);

        String tel = debugExtract("sellerTel", sectionTelAddr,
                "(?m)전화번호\\s*\\n\\s*([0-9\\-]{8,20})", 1);
        if (notEmpty(tel)) r.merchant.tel = cleanField(tel);

        String addr = debugExtractDot("sellerAddr", sectionTelAddr,
                "(?s)(사업장주소|주소)\\s*\\n\\s*([\\s\\S]*?)\\s*(?:\\n\\s*(가맹점\\s*정보|금액)|$)", 2);
        if (notEmpty(addr)) r.merchant.address = cleanField(addr);

        // 9) ✅ 금액: "금액" 섹션에서 숫자 5개(승인/공급/부가세/봉사료/합계)
        AmountsNav a = parseNaverAmounts(text);
        if (a != null) {
            r.totals.taxable = a.supply;
            r.totals.vat = a.vat;
            r.totals.total = a.total;

            // approvalAmt가 String이면 변환해서 세팅
            if (r.payment != null && r.payment.approvalAmt == null && a.approval != null) {
                r.payment.approvalAmt = String.valueOf(a.approval);
            }
        }

        // 10) 아이템 1개
        Item it = new Item();
        it.name = notEmpty(productName) ? productName : "상품";
        it.qty = 1;
        it.amount = r.totals.total;
        it.unitPrice = r.totals.total;
        r.items = List.of(it);

        System.out.println("[NAVER] merchant=" + safe(r.merchant.name)
                + ", bizNo=" + safe(r.merchant.bizNo)
                + ", total=" + safeInt(r.totals.total)
                + ", approvalAmt=" + safe(r.payment.approvalAmt));

        System.out.println("=== ◀ parseNaverCardReceipt END ===");
        return r;
    }

    private static class AmountsNav {
        Integer approval; // 승인금액
        Integer supply;   // 공급가액
        Integer vat;      // 부가세액
        Integer svc;      // 봉사료
        Integer total;    // 합계
    }

    /**
     * ✅ FIX: 기존 "라인 단독 숫자" 정규식은 OCR 특수공백/제어문자 때문에 candidates=[]가 자주 발생
     * -> "어디에 있든 금액 형태(콤마 포함)"를 모두 뽑고, 뒤에서 5개를 매핑
     */
    private AmountsNav parseNaverAmounts(String text) {
        if (text == null) return null;
        int idx = text.indexOf("금액");
        if (idx < 0) return null;

        String tail = text.substring(idx);

        // 숫자 수집: 34,700 / 31,546 / 3,154 / 0 / 34,700
        List<Integer> nums = new ArrayList<>();

        // ✅ 콤마 금액 우선(네이버 영수증은 거의 콤마형)
        Matcher m = Pattern.compile("(\\d{1,3}(?:,\\d{3})+)").matcher(tail);
        while (m.find()) {
            Integer v = toInt(m.group(1));
            if (v != null) nums.add(v);
        }

        // 봉사료가 "0"처럼 단독 숫자로만 나올 수 있어 보완
        if (nums.size() < 5) {
            Matcher m2 = Pattern.compile("(?m)^\\s*(\\d{1,8})\\s*$").matcher(tail);
            while (m2.find()) {
                Integer v = toInt(m2.group(1));
                if (v != null) nums.add(v);
            }
        }

        if (DEBUG) {
            System.out.println("[DEBUG.naverAmounts] nums=" + nums);
        }

        if (nums.size() < 5) return null;

        // 뒤에서 5개(노이즈 섞였을 때 대비)
        List<Integer> last5 = nums.subList(nums.size() - 5, nums.size());

        AmountsNav a = new AmountsNav();
        a.approval = last5.get(0);
        a.supply = last5.get(1);
        a.vat = last5.get(2);
        a.svc = last5.get(3);
        a.total = last5.get(4);
        return a;
    }

    private String normalizeCardMasked(String raw) {
        if (raw == null) return null;
        String x = cleanField(raw);
        // 괄호 유효기간 제거
        x = x.replaceAll("\\(.*?\\)", "").trim();
        // _ 같은 이상문자 -> *
        x = x.replaceAll("[^0-9\\*\\-]", "*");
        x = x.replaceAll("\\*{2,}", "****");
        return x;
    }

    private String extractValueAfterLabel(String text, String label, int maxLines) {
        if (text == null) return null;
        String[] lines = text.replace("\r", "\n").split("\n");
        for (int i = 0; i < lines.length; i++) {
            String ln = cleanField(lines[i]);
            if (!notEmpty(ln)) continue;

            String lnNoSpace = ln.replace(" ", "");
            String labelNoSpace = label.replace(" ", "");
            if (lnNoSpace.equals(labelNoSpace) || lnNoSpace.startsWith(labelNoSpace)) {
                for (int k = 1; k <= maxLines && (i + k) < lines.length; k++) {
                    String cand = cleanField(lines[i + k]);
                    if (!notEmpty(cand)) continue;
                    if (isLooksLikeLabel(cand)) continue;

                    // 섹션 헤더면 중단
                    if (cand.contains("가맹점 정보") || cand.contains("금액") || cand.contains("판매자 정보")) break;

                    return cand;
                }
                return null;
            }
        }
        return null;
    }

    private boolean isLooksLikeLabel(String s) {
        if (s == null) return false;
        String t = s.replace(" ", "");
        String[] labels = {
                "대표자명","사업자등록번호","전화번호","사업장주소",
                "가맹점정보","가맹점명","가맹점번호","주소",
                "승인금액","공급가액","부가세액","봉사료","합계",
                "상품주문번호","상품주문","주문번호","상품명"
        };
        for (String l : labels) {
            if (t.equals(l) || t.startsWith(l)) return true;
        }
        return false;
    }

    private String findCompanyLikeLine(String section, String... blacklistContains) {
        if (section == null) return null;
        String[] lines = section.replace("\r", "\n").split("\n");
        String best = null;

        for (String line : lines) {
            String t = cleanField(line);
            if (!notEmpty(t)) continue;
            if (isLooksLikeLabel(t)) continue;

            boolean blocked = false;
            for (String b : blacklistContains) {
                if (b != null && !b.isEmpty() && t.contains(b)) { blocked = true; break; }
            }
            if (blocked) continue;

            // 사람 이름(2~4글자) 같은 건 제외
            if (t.matches("^[가-힣]{2,4}$")) continue;

            // 회사명 형태 힌트
            if (t.contains("(주)") || t.contains("주식회사") || t.contains("회사") ||
                    t.endsWith("케미칼") || t.endsWith("상사") || t.endsWith("마트") || t.endsWith("점")) {
                if (best == null || t.length() > best.length()) best = t;
            }
        }
        return best;
    }

    private List<String> findAllBizNo(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        Matcher m = Pattern.compile("([0-9]{3}-[0-9]{2}-[0-9]{5})").matcher(text);
        while (m.find()) {
            String v = m.group(1);
            if (!list.contains(v)) list.add(v);
        }
        return list;
    }

    private String sliceSection(String text, String startLabel, String endLabel, int maxLen) {
        if (text == null) return "";
        int s = text.indexOf(startLabel);
        if (s < 0) return "";
        int e = (endLabel == null)
                ? Math.min(text.length(), s + maxLen)
                : text.indexOf(endLabel, s + startLabel.length());
        if (e < 0) e = Math.min(text.length(), s + maxLen);
        return text.substring(s, e);
    }

    private String pickBestProductLine(String block) {
        if (block == null) return null;
        String[] lines = block.replace("\r", "\n").split("\n");
        String best = null;
        for (String ln : lines) {
            String t = cleanField(ln);
            if (!notEmpty(t)) continue;
            if (isLooksLikeLabel(t)) continue;
            if (best == null || t.length() > best.length()) best = t;
        }
        return best;
    }

    /* ========================= 0) Homeplus 템플릿 감지 ========================= */

    private boolean isHomeplusSlip(String text) {
        String lower = (text == null) ? "" : text.toLowerCase();

        boolean hasBrand = lower.contains("homeplus") || text.contains("홈플러스");
        boolean hasTitle = text.contains("신용카드매출전표") || text.contains("신용카드 매출전표");

        boolean hasPaySection = text.contains("결제금액") && (text.contains("금액") || text.contains("합계"));
        boolean hasSellerSection = text.contains("판매자 정보") || text.contains("판매자정보") || text.contains("판매자상호");
        boolean hasFranchiseSection = text.contains("가맹점 정보") || text.contains("가맹점정보") || text.contains("가맹점점명");

        boolean hasKeyFields =
                text.contains("승인번호") &&
                        (text.contains("주문번호") || text.contains("주 문 번 호") || text.contains("주문 번호")) &&
                        (text.contains("품명") || text.contains("품목") || text.contains("상품명")) &&
                        (text.contains("승인일시") || text.contains("승인 일시"));

        // ✅ Homeplus 로고가 OCR에서 누락될 수 있으니, 타이틀+섹션+키필드 조합으로도 인정
        boolean result =
                (hasBrand && (hasTitle || (hasPaySection && (hasSellerSection || hasFranchiseSection))))
                        || (hasTitle && hasPaySection && (hasSellerSection || hasFranchiseSection) && hasKeyFields);

        System.out.println("[DETECT] Homeplus hasBrand=" + hasBrand
                + ", hasTitle=" + hasTitle
                + ", hasPaySection=" + hasPaySection
                + ", hasSellerSection=" + hasSellerSection
                + ", hasFranchiseSection=" + hasFranchiseSection
                + ", hasKeyFields=" + hasKeyFields
                + " => " + result);

        return result;
    }

    /* ========================= 1) Homeplus 전용 파싱 ========================= */

    private ReceiptResult parseHomeplusSlip(String text) {
        System.out.println("=== ▶ parseHomeplusSlip START ===");

        ReceiptResult r = new ReceiptResult();

        // 1) 승인번호 / 주문번호
        r.approval.approvalNo = firstNonNull(
                debugExtract("approvalNo#1", text, "승인번호\\s*[:：]?\\s*([0-9]{6,12})", 1),
                debugExtract("approvalNo#2", text, "승\\s*인\\s*번\\s*호\\s*[:：]?\\s*([0-9]{6,12})", 1)
        );

        r.meta.receiptNo = firstNonNull(
                debugExtract("orderNo#1", text, "주문번호\\s*[:：]?\\s*([0-9]{8,})", 1),
                debugExtract("orderNo#2", text, "주\\s*문\\s*번\\s*호\\s*[:：]?\\s*([0-9]{8,})", 1),
                debugExtract("orderNo#3", text, "주문\\s*번호\\s*[:：]?\\s*([0-9]{8,})", 1)
        );

        // 2) 품명(=상품명 역할)
        String itemName = firstNonNull(
                debugExtractDot("itemName#1", text,
                        "(?s)품명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보|$)",
                        1
                ),
                debugExtractDot("itemName#2", text,
                        "(?s)(품목|상품명)\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보|$)",
                        2
                )
        );
        itemName = cleanField(itemName);

        // "외 N건" 처리
        Integer qtyGuess = 1;
        String itemCore = itemName;

        if (notEmpty(itemName)) {
            Matcher m = Pattern.compile("(?s)(.+?)\\s*외\\s*([0-9]+)\\s*건\\s*$").matcher(itemName);
            if (m.find()) {
                itemCore = cleanField(m.group(1));
                Integer extra = toInt(m.group(2));
                if (extra != null && extra >= 0) qtyGuess = 1 + extra;
                System.out.println("[HOMEPLUS] itemName has '외N건' => core=" + itemCore + ", qtyGuess=" + qtyGuess);
            }
        }

        // 3) 카드종류 / 카드번호 / 거래유형 / 할부개월
        String cardType = firstNonNull(
                debugExtractDot("cardType#1", text,
                        "(?s)카드종류\\s*[:：]?\\s*([\\s\\S]*?)\\s*(카드번호|유효기간|거래유형|할부개월|승인일시|$)", 1),
                debugExtract("cardType#2", text,
                        "카드종류\\s*[:：]?\\s*([가-힣A-Za-z0-9()\\-\\s]{2,30})", 1)
        );
        cardType = cleanField(cardType);

        r.payment.cardBrand = normalizeCardBrand(firstNonNull(
                cardType,
                debugExtract("cardBrand#fallback", text, "(IBK비씨카드|IBK\\s*비씨카드|BC\\s*카드\\(.*?\\)|BC\\s*카드|BC카드|비씨카드|BC|국민|신한|현대|롯데|농협|하나|NH|KB)", 1)
        ));

        // 카드번호: 마스킹/하이픈/부분숫자 등 다양
        String cardNo = firstNonNull(
                debugExtract("cardNo#1", text, "카드번호\\s*[:：]?\\s*([0-9]{4}[- ]?[0-9]{2}\\*+[- ]?\\*+[- ]?\\*+)", 1),
                debugExtract("cardNo#2", text, "카드번호\\s*[:：]?\\s*([0-9\\-* ]{7,25})", 1),
                debugExtract("cardNo#3", text, "카드번호\\s*[:：]?\\s*([0-9]{6,20})", 1),
                debugExtractDot("cardNo#4_near", text,
                        "(?s)카드번호\\s*[:：]?\\s*([\\s\\S]{0,40})\\s*(유효기간|거래유형|할부개월|승인일시|$)", 1)
        );
        cardNo = cleanField(cardNo);
        if (notEmpty(cardNo) && cardNo.length() > 25) {
            String refined = extract(cardNo, "([0-9]{4}[- ]?[0-9\\*\\- ]{3,20})", 1);
            if (refined != null) cardNo = refined;
        }
        r.payment.cardMasked = cardNo;

        String tradeType = firstNonNull(
                debugExtractDot("tradeType#1", text,
                        "(?s)거래유형\\s*[:：]?\\s*([\\s\\S]*?)\\s*(할부개월|승인일시|결제금액|$)", 1),
                debugExtract("tradeType#2", text,
                        "거래유형\\s*[:：]?\\s*(정상매출|취소매출|정상|취소|승인|매출)", 1)
        );
        tradeType = cleanField(tradeType);
        r.payment.type = firstNonNull(tradeType, "신용거래");

        String installment = firstNonNull(
                debugExtractDot("installment#1", text,
                        "(?s)할부개월\\s*[:：]?\\s*([\\s\\S]*?)\\s*(승인일시|결제금액|$)", 1),
                debugExtract("installment#2", text, "할부개월\\s*[:：]?\\s*(일시불|[0-9]{1,2}개월)", 1)
        );
        installment = cleanField(installment);
        System.out.println("[HOMEPLUS] installment=" + safe(installment));

        // 4) 승인일시: "2025-12-31 11:21:27"
        String datePart = firstNonNull(
                debugExtract("approveDate#1", text,
                        "승인일시\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1),
                debugExtract("approveDate#fallback", text, "(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})", 1)
        );

        String timePart = firstNonNull(
                debugExtract("approveTime#1", text,
                        "승인일시\\s*[:：]?\\s*(20\\d{2}[-./]\\d{1,2}[-./]\\d{1,2})\\s+([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 2),
                debugExtract("approveTime#fallback", text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)", 1)
        );

        r.meta.saleDate = normalizeDate(datePart);
        r.meta.saleTime = normalizeTime(timePart);

        // 5) 결제금액 블록
        Integer amount = firstNonNullInt(
                debugInt("amount#1", text, "금액\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("amount#2", text, "결제금액[\\s\\S]*?금액\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1)
        );
        Integer vat = firstNonNullInt(
                debugInt("vat#1", text, "부가세\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("vat#2", text, "결제금액[\\s\\S]*?부가세\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1)
        );
        Integer total = firstNonNullInt(
                debugInt("total#1", text, "합계\\s*[:：]?\\s*([0-9]{1,3}(?:,[0-9]{3})*)\\s*원?", 1),
                debugInt("total#2", text, "결제금액[\\s\\S]*?합계\\s*([0-9]{1,3}(?:,[0-9]{3})*)", 1)
        );

        r.totals.taxable = amount;
        r.totals.vat = vat;
        r.totals.total = total;

        if (r.totals.total == null) {
            if (amount != null && vat != null) r.totals.total = amount + vat;
            else if (amount != null) r.totals.total = amount;
        }

        // 6) 판매자상호 / 가맹점점명
        String seller = firstNonNull(
                debugExtractDot("seller#1", text,
                        "(?s)판매자상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|가맹점\\s*정보|가맹점정보|$)", 1),
                debugExtractDot("seller#2", text,
                        "(?s)판매자\\s*정보[\\s\\S]*?판매자상호\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|$)", 1)
        );
        seller = cleanField(seller);

        String franchiseName = firstNonNull(
                debugExtractDot("franchise#1", text,
                        "(?s)가맹점점명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|가맹점주소|전화번호|$)", 1),
                debugExtractDot("franchise#2", text,
                        "(?s)가맹점\\s*정보[\\s\\S]*?가맹점점명\\s*[:：]?\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|가맹점주소|전화번호|$)", 1)
        );
        franchiseName = cleanField(franchiseName);

        String merchantName = firstNonNull(
                notEmpty(seller) ? seller : null,
                notEmpty(franchiseName) ? franchiseName : null,
                extract(text, "(홈플러스\\s*[가-힣A-Za-z0-9()\\-]*점)", 1),
                (text.toLowerCase().contains("homeplus") ? "Homeplus" : null),
                "홈플러스"
        );
        r.merchant.name = merchantName;

        // 7) 아이템 구성
        Item it = new Item();
        it.name = notEmpty(itemCore) ? itemCore : (notEmpty(itemName) ? itemName : "품목");
        it.qty = (qtyGuess != null && qtyGuess > 0) ? qtyGuess : 1;
        it.amount = r.totals.total;
        it.unitPrice = (it.qty != null && it.qty > 0 && r.totals.total != null) ? (r.totals.total / it.qty) : r.totals.total;

        r.items = List.of(it);

        System.out.println("[HOMEPLUS] ✅ FINAL ITEM => name=" + it.name + ", qty=" + it.qty +
                ", amount=" + safeInt(it.amount) + ", unitPrice=" + safeInt(it.unitPrice));
        System.out.println("=== ◀ parseHomeplusSlip END ===");

        return r;
    }

    /* ========================= 2) 쿠팡 앱 결제내역 ========================= */

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

        return r;
    }

    /* ========================= 3) 기존 카드영수증 ========================= */

    private ReceiptResult parseCardVersion(String text) {
        ReceiptResult r = new ReceiptResult();

        // ✅ 판매자상호 regex 종료조건 강화 (폭주 방지)
        String sellerName = extractDot(text,
                "(?s)판매자상호\\s*([\\s\\S]*?)\\s*(대표자명|사업자등록번호|전화번호|사업장주소|가맹점\\s*정보|금액|$)", 1);
        sellerName = cleanField(sellerName);

        r.merchant.name = firstNonNull(
                notEmpty(sellerName) ? sellerName : null,
                extract(text, "(쿠팡\\(주\\)|쿠팡주식회사|쿠팡)"),
                "쿠팡"
        );

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

        r.payment.cardMasked = firstNonNull(
                extract(text, "(\\d{4}\\*+\\d{2,6}\\*?\\d{0,6})"),
                extract(text, "(\\d{4}\\*{4,}\\d{3,4}\\*?)"),
                extract(text, "카드번호\\s*[:：]?\\s*([0-9\\-*]{7,25})", 1)
        );

        String tradeType = extractDot(text,
                "(?s)거래종류\\s*([가-힣A-Za-z0-9\\s]{2,20})\\s*(할부개월|카드번호|거래일시|승인번호|$)", 1);
        tradeType = cleanField(tradeType);

        r.payment.type = firstNonNull(
                notEmpty(tradeType) ? tradeType : null,
                extract(text, "(신용거래|현금거래|일시불|할부)"),
                "신용거래"
        );

        r.meta.receiptNo = extract(text, "(주문\\s*번호)\\s*[:：]?\\s*([0-9]{8,})", 2);
        r.approval.approvalNo = extract(text, "(승인\\s*번호)\\s*[:：]?\\s*([0-9]{6,12})", 2);

        r.meta.saleDate = extract(text, "(20\\d{2}[./-]\\d{1,2}[./-]\\d{1,2})");
        r.meta.saleTime = extract(text, "([0-2]?\\d:[0-5]\\d:[0-5]\\d)");

        r.totals.taxable  = firstInt(text, "과세금액[^0-9]*([0-9,]+)");
        r.totals.vat      = firstInt(text, "부가세[^0-9]*([0-9,]+)");
        r.totals.taxFree  = firstInt(text, "비과세금액[^0-9]*([0-9,]+)");

        Integer totalFromLabel = firstInt(text, "합계금액[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        if (totalFromLabel == null) {
            totalFromLabel = firstInt(text, "(총액|결제금액)[^0-9]*([0-9]{1,3}(?:,[0-9]{3})+)");
        }
        r.totals.total = totalFromLabel;

        r.items = parseCardItemsV2_LabelFirst(text, r.totals.total);

        return r;
    }

    private List<Item> parseCardItemsV2_LabelFirst(String text, Integer totalAmount) {
        String product = extractDot(text,
                "(?s)상품명\\s*([\\s\\S]*?)\\s*(과세금액|비과세금액|부가세|합계금액|이용상점정보|$)", 1);
        product = cleanProductName(product);

        if (notEmpty(product)) {
            Item it = new Item();
            it.name = product;
            it.qty = 1;
            it.amount = totalAmount;
            it.unitPrice = totalAmount;
            return List.of(it);
        }

        Item it = new Item();
        it.name = "상품";
        it.qty = 1;
        it.amount = totalAmount;
        it.unitPrice = totalAmount;
        return List.of(it);
    }

    /* ========================= 유형 감지 (기존) ========================= */

    private boolean isCoupangAppReceipt(String text) {
        boolean hasCoupay = text.contains("쿠팡(쿠페이)") || text.contains("쿠페이");
        boolean hasMemo = text.contains("거래메모");
        boolean hasCardReceipt = text.contains("카드영수증") || text.contains("구매정보");
        return hasCoupay && hasMemo && !hasCardReceipt;
    }

    /* ========================= printFullResult (요청 버전) ========================= */

    private void printFullResult(ReceiptResult r) {
        System.out.println("------ ✅ 최종 파싱 결과 요약 ------");

        // Merchant
        System.out.println("[MERCHANT] name: " + safe(getMerchantName(r)));
        try { System.out.println("[MERCHANT] (reflection) " + reflectFields(getMerchant(r))); }
        catch (Exception ignore) {}

        // Meta
        System.out.println("[META] receiptNo(orderNo): " + safe(getMetaReceiptNo(r)));
        System.out.println("[META] saleDate: " + safe(getMetaSaleDate(r)));
        System.out.println("[META] saleTime: " + safe(getMetaSaleTime(r)));
        try { System.out.println("[META] (reflection) " + reflectFields(getMeta(r))); }
        catch (Exception ignore) {}

        // Payment
        System.out.println("[PAYMENT] type: " + safe(getPaymentType(r)));
        System.out.println("[PAYMENT] cardBrand: " + safe(getPaymentCardBrand(r)));
        System.out.println("[PAYMENT] cardMasked: " + safe(getPaymentCardMasked(r)));
        System.out.println("[PAYMENT] approvalAmt: " + safe(getPaymentApprovalAmt(r)));
        try { System.out.println("[PAYMENT] (reflection) " + reflectFields(getPayment(r))); }
        catch (Exception ignore) {}

        // Approval
        System.out.println("[APPROVAL] approvalNo: " + safe(getApprovalNo(r)));
        try { System.out.println("[APPROVAL] (reflection) " + reflectFields(getApproval(r))); }
        catch (Exception ignore) {}

        // Totals
        System.out.println("[TOTALS] total: " + safeInt(getTotalsTotal(r)));
        System.out.println("[TOTALS] taxable: " + safeInt(getTotalsTaxable(r)));
        System.out.println("[TOTALS] vat: " + safeInt(getTotalsVat(r)));
        System.out.println("[TOTALS] taxFree: " + safeInt(getTotalsTaxFree(r)));
        try { System.out.println("[TOTALS] (reflection) " + reflectFields(getTotals(r))); }
        catch (Exception ignore) {}

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
                try { System.out.println("    [ITEM reflection] " + reflectFields(it)); }
                catch (Exception ignore) {}
            }
        }

        // Root reflection
        try { System.out.println("[ROOT reflection] " + reflectFields(r)); }
        catch (Exception ignore) {}

        System.out.println("---------------------------------");
    }

    /* ========================= safe getters ========================= */

    private Merchant getMerchant(ReceiptResult r) { return (r == null ? null : r.merchant); }
    private Meta getMeta(ReceiptResult r) { return (r == null ? null : r.meta); }
    private Payment getPayment(ReceiptResult r) { return (r == null ? null : r.payment); }
    private Approval getApproval(ReceiptResult r) { return (r == null ? null : r.approval); }
    private Totals getTotals(ReceiptResult r) { return (r == null ? null : r.totals); }

    private String getMerchantName(ReceiptResult r) { return (getMerchant(r) == null ? null : getMerchant(r).name); }
    private String getMetaReceiptNo(ReceiptResult r) { return (getMeta(r) == null ? null : getMeta(r).receiptNo); }
    private String getMetaSaleDate(ReceiptResult r) { return (getMeta(r) == null ? null : getMeta(r).saleDate); }
    private String getMetaSaleTime(ReceiptResult r) { return (getMeta(r) == null ? null : getMeta(r).saleTime); }
    private String getPaymentType(ReceiptResult r) { return (getPayment(r) == null ? null : getPayment(r).type); }
    private String getPaymentCardBrand(ReceiptResult r) { return (getPayment(r) == null ? null : getPayment(r).cardBrand); }
    private String getPaymentCardMasked(ReceiptResult r) { return (getPayment(r) == null ? null : getPayment(r).cardMasked); }
    private String getPaymentApprovalAmt(ReceiptResult r) { return (getPayment(r) == null ? null : getPayment(r).approvalAmt); }
    private String getApprovalNo(ReceiptResult r) { return (getApproval(r) == null ? null : getApproval(r).approvalNo); }

    private Integer getTotalsTotal(ReceiptResult r) { return (getTotals(r) == null ? null : getTotals(r).total); }
    private Integer getTotalsTaxable(ReceiptResult r) { return (getTotals(r) == null ? null : getTotals(r).taxable); }
    private Integer getTotalsVat(ReceiptResult r) { return (getTotals(r) == null ? null : getTotals(r).vat); }
    private Integer getTotalsTaxFree(ReceiptResult r) { return (getTotals(r) == null ? null : getTotals(r).taxFree); }

    /* ========================= Debug extract helpers ========================= */

    private String debugExtract(String label, String text, String regex, int group) {
        String v = extract(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extract] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private String debugExtractDot(String label, String text, String regex, int group) {
        String v = extractDot(text, regex, group);
        if (DEBUG) {
            System.out.println("[DEBUG.extractDot] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  => " + (v == null ? "null" : ("'" + v + "'")));
        }
        return v;
    }

    private Integer debugInt(String label, String text, String regex, int group) {
        String s = extract(text, regex, group);
        Integer n = toInt(s);
        if (DEBUG) {
            System.out.println("[DEBUG.int] " + label);
            System.out.println("  regex = " + regex);
            System.out.println("  raw  = " + (s == null ? "null" : ("'" + s + "'")));
            System.out.println("  int  = " + (n == null ? "null" : n));
        }
        return n;
    }

    private Integer firstNonNullInt(Integer... nums) {
        for (Integer n : nums) if (n != null) return n;
        return null;
    }

    /* ========================= 공통 유틸 ========================= */

    private String normalizeTextKeepNewlines(String s) {
        if (s == null) return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = s.replaceAll("[\\u00A0]", " ");       // NBSP
        s = s.replaceAll("[\\t\\x0B\\f]+", " ");  // tab류

        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String x = line.replaceAll(" +", " ").trim();
            if (!x.isEmpty()) sb.append(x).append("\n");
        }
        return sb.toString().trim();
    }

    private String normalizeDate(String date) {
        if (date == null) return null;
        String d = date.trim()
                .replace(".", "-")
                .replace("/", "-")
                .replaceAll("\\s+", " ");

        Matcher m = Pattern.compile("(20\\d{2})-([0-9]{1,2})-([0-9]{1,2})").matcher(d);
        if (m.find()) {
            String yy = m.group(1);
            int mm = Integer.parseInt(m.group(2));
            int dd = Integer.parseInt(m.group(3));
            return yy + "-" + String.format("%02d", mm) + "-" + String.format("%02d", dd);
        }
        return d;
    }

    private String normalizeTime(String time) {
        if (time == null) return null;
        return time.trim().replaceAll("\\s+", " ");
    }

    protected String extract(String text, String regex) { return extract(text, regex, 1); }

    protected String extract(String text, String regex, int group) {
        try {
            if (text == null) return null;
            Matcher m = Pattern.compile(regex).matcher(text);
            if (!m.find()) return null;
            int g = Math.min(group, m.groupCount());
            return m.group(g).trim();
        } catch (Exception e) {
            return null;
        }
    }

    protected String extractDot(String text, String regex, int group) {
        try {
            if (text == null) return null;
            Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
            if (!m.find()) return null;
            int g = Math.min(group, m.groupCount());
            return m.group(g).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(Object o) { return (o == null ? "" : String.valueOf(o)); }
    private String safeInt(Integer n) { return (n == null ? "null" : n.toString()); }

    protected Integer toInt(String s) {
        try { return (s == null) ? null : Integer.parseInt(s.replaceAll("[^0-9-]", "")); }
        catch (Exception e) { return null; }
    }

    protected Integer firstInt(String text, String regex) {
        try {
            if (text == null) return null;
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return toInt(m.group(m.groupCount()));
        } catch (Exception ignore) {}
        return null;
    }

    protected String firstNonNull(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
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
        s = s.replaceAll("(카드종류|카드번호|유효기간|거래유형|할부개월|승인일시|결제금액|판매자\\s*정보|가맹점\\s*정보).*", "").trim();
        s = s.replaceAll("[,.:/\\-]+$", "").trim();
        return s;
    }

    private String normalizeCardBrand(String s) {
        if (s == null) return null;
        s = s.replaceAll("\\s+", "");
        if (s.equalsIgnoreCase("BC")) return "BC카드";
        if (s.equals("비씨")) return "비씨카드";
        if (s.contains("비씨") && !s.endsWith("카드")) return s + "카드";
        if (s.equals("BC카드")) return "BC카드";
        if (s.equals("IBK비씨카드") || s.equals("IBK비씨카드카드")) return "IBK비씨카드";
        return s;
    }

    /* ========================= reflectFields ========================= */

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
