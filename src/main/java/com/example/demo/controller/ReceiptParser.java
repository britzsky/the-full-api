package com.example.demo.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import com.google.cloud.documentai.v1.Document;

@Component
public class ReceiptParser {

    static {
        nu.pattern.OpenCV.loadLocally();
        System.out.println("✅ OpenCV 로드 완료 (ReceiptParser)");
    }

    // ================================
    // 내부 데이터 구조
    // ================================
    public static class ReceiptItem {
        public String name;
        public int unitPrice;
        public double qty;
        public int total;
        public String barcode;
        public ReceiptItem(String n, int u, double q, int t) {
            name = n; unitPrice = u; qty = q; total = t;
        }

        @Override
        public String toString() {
            return String.format("%s | 단가: %d | 수량: %.2f | 금액: %d", name, unitPrice, qty, total);
        }
    }

    public static class ReceiptResult {
        private String storeName;
        private String storeAddress;
        private String businessNumber;
        private String transactionDate;   // ✅ 거래일자
        private String purchaseTime;      // ✅ 구매시각
        private String cardType;
        private String cardCompany;       // ✅ 카드사
        private String cardNumber;
        private String approvalNumber;
        private int taxableAmount;
        private int vatAmount;
        private int discountAmount;       // ✅ 할인금액
        private int totalAmount;
        private String paymentMethod;
        private List<ReceiptItem> items = new ArrayList<>();
        private String rawText;

        // === getters & setters ===
        public String getStoreName() { return storeName; }
        public void setStoreName(String s) { this.storeName = s; }

        public String getStoreAddress() { return storeAddress; }
        public void setStoreAddress(String s) { this.storeAddress = s; }

        public String getBusinessNumber() { return businessNumber; }
        public void setBusinessNumber(String s) { this.businessNumber = s; }

        public String getTransactionDate() { return transactionDate; }
        public void setTransactionDate(String s) { this.transactionDate = s; }

        public String getPurchaseTime() { return purchaseTime; }
        public void setPurchaseTime(String s) { this.purchaseTime = s; }

        public String getCardType() { return cardType; }
        public void setCardType(String s) { this.cardType = s; }

        public String getCardCompany() { return cardCompany; }
        public void setCardCompany(String s) { this.cardCompany = s; }

        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String s) { this.cardNumber = s; }

        public String getApprovalNumber() { return approvalNumber; }
        public void setApprovalNumber(String s) { this.approvalNumber = s; }

        public int getTaxableAmount() { return taxableAmount; }
        public void setTaxableAmount(int v) { this.taxableAmount = v; }

        public int getVatAmount() { return vatAmount; }
        public void setVatAmount(int v) { this.vatAmount = v; }

        public int getDiscountAmount() { return discountAmount; }
        public void setDiscountAmount(int v) { this.discountAmount = v; }

        public int getTotalAmount() { return totalAmount; }
        public void setTotalAmount(int v) { this.totalAmount = v; }

        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String s) { this.paymentMethod = s; }

        public List<ReceiptItem> getItems() { return items; }

        public String getRawText() { return rawText; }
        public void setRawText(String s) { this.rawText = s; }
    }


    // ================================
    // 1️⃣ 이미지 기울기 보정
    // ================================
    public String deskewImage(String imagePath) {
        // 기존 deskew 로직 유지
        try {
            File f = new File(imagePath);
            if (!f.exists()) return imagePath;

            Mat src = Imgcodecs.imread(imagePath, Imgcodecs.IMREAD_GRAYSCALE);
            if (src.empty()) return imagePath;

            Mat binary = new Mat();
            Imgproc.threshold(src, binary, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);

            Mat nonZero = new Mat();
            Core.findNonZero(binary, nonZero);
            if (nonZero.empty()) return imagePath;

            RotatedRect box = Imgproc.minAreaRect(new MatOfPoint2f(nonZero));
            double angle = box.angle;
            if (angle < -45) angle += 90;

            Point center = new Point(src.width() / 2, src.height() / 2);
            Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1);
            Mat deskewed = new Mat();
            Imgproc.warpAffine(src, deskewed, rotMat, src.size(), Imgproc.INTER_CUBIC);

            String output = imagePath.replace(".jpg", "_deskew.jpg").replace(".png", "_deskew.png");
            Imgcodecs.imwrite(output, deskewed);
            System.out.println("📐 Deskew 완료 → " + output);
            return output;
        } catch (Exception e) {
            System.err.println("⚠️ Deskew 오류: " + e.getMessage());
            return imagePath;
        }
    }

    // ================================
    // 2️⃣ Document AI 결과 파싱
    // ================================
    public ReceiptResult parseDocumentAI(Document doc) {
        ReceiptResult result = new ReceiptResult();
        if (doc == null) return result;

        String text = doc.getText();
        result.setRawText(text);

        // 텍스트 정규화 후 필드 및 품목 추출
        extractExtraFields(result, normalizeText(text));
        
        return result;
    }

    // ================================
    // 3️⃣ 필드 추출 (정규식)
    // ================================
    private void extractExtraFields(ReceiptResult result, String text) {
        
        // 상호명
        result.setStoreName(findFirst(text, "(주식회사|\\(주\\)|㈜)?\\s?[가-힣A-Za-z0-9& ]{2,30}(마트|편의점|점|식자재|센터|코스트코|이마트|GS25|CU)", 0));

        // 사업자등록번호
        result.setBusinessNumber(findFirst(text, "([0-9]{3}-[0-9]{2}-[0-9]{5})", 0));

        // 주소
        result.setStoreAddress(findFirst(text, "(도로명|로|길|시|구|동)[가-힣0-9\\-\\s]+", 0));

        // 날짜/시간 (쿠팡/일반 영수증 형식 모두 커버)
        String dateRegex = "(20[0-9]{2}[\\.\\-/]?[01]?[0-9][\\.\\-/]?[0-3]?[0-9])";
        result.setTransactionDate(findFirst(text, "(거래일시|거래일자|승인일시|일시)[:\\s]*" + dateRegex, 2));
        if (isEmpty(result.getTransactionDate())) {
             result.setTransactionDate(findFirst(text, dateRegex, 1));
        }

        // 구매시간
        result.setPurchaseTime(findFirst(text, "(\\d{2}:\\d{2}:\\d{2})", 1));
        if (isEmpty(result.getPurchaseTime())) {
             result.setPurchaseTime(findFirst(text, "(\\d{2}:\\d{2})", 1));
        }

        // 카드/결제 정보
        result.setCardCompany(findFirst(text, "(농협|신한|국민|삼성|롯데|현대|BC|하나|우리|기업|씨티)카드", 0));
        result.setCardType(findFirst(text, "(신용|체크|법인|개인)카드", 0));
        result.setCardNumber(findFirst(text, "(\\d{4}-?\\d{2}\\*{2}-\\*{4}-\\d{4})", 0));
        result.setApprovalNumber(findFirst(text, "(승인번호|승인\\s*번호)[:\\s]*(\\d{6,})", 2));
        result.setPaymentMethod(findFirst(text, "(신용카드|체크카드|현금|쿠페이|카카오페이|페이코|네이버페이)", 0));

        // 금액 정보
        result.setDiscountAmount(toInt(findFirst(text, "(할인|쿠폰|DC)[^0-9]*([0-9,]+)", 2)));
        result.setTaxableAmount(toInt(findFirst(text, "과세.?([0-9,]+)", 1)));
        result.setVatAmount(toInt(findFirst(text, "부가.?세.?([0-9,]+)", 1)));
        result.setTotalAmount(toInt(findFirst(text, "(합계|총|결제|사용|구매|금액)[:\\s]*([0-9,]+)", 2)));

        // 품목 파싱
        extractItems(result, text);
    }

    // ================================
    // 4️⃣ 품목 파싱 (보강됨)
    // ================================
    private void extractItems(ReceiptResult result, String text) {
        String[] lines = text.split("\\n");
        
        // 1. 일반 영수증 패턴: 상품명 단가 수량 금액 (4자리 패턴)
        Pattern basicPattern = Pattern.compile("([가-힣A-Za-z0-9\\(\\)\\[\\]\\s\\-]+)\\s+([0-9,]+)\\s+([0-9\\.]+)\\s+([0-9,]+)");
        
        // 2. 쿠팡/앱 영수증 패턴: 상품명... 금액 (긴 상품명 후 바로 금액)
        // 상품명 (2글자 이상) + 수량 (옵션: \d+개) + 공백 + 금액
        Pattern appPattern = Pattern.compile("(상품명|거래내용|주문내용)[:\\s]*\\s*([가-힣A-Za-z0-9\\(\\)\\[\\]\\s\\-,]+)\\s+([0-9,]+)원?");
        
        // 3. 쿠팡 영수증 품목 (긴 상품명 + 금액 + 수량)
        Pattern coupangItemPattern = Pattern.compile("([가-힣A-Za-z0-9\\(\\)\\[\\]\\s\\-,]+)\\s+([0-9,]+)원\\s*[,]?\\s*([0-9\\.]+)개");


        for (String l : lines) {
            
            // 3-1. 쿠팡 스타일 품목 처리 (상품명, 금액, 수량)
            Matcher mCoupang = coupangItemPattern.matcher(l);
            if (mCoupang.find()) {
                 String name = mCoupang.group(1).trim();
                 int total = toInt(mCoupang.group(2));
                 double qty = toDouble(mCoupang.group(3), 1);
                 int unit = (int) (total / qty);
                 result.getItems().add(new ReceiptItem(name, unit, qty, total));
                 continue; // 품목 발견 시 다음 줄로 이동
            }

            // 1-1. 기본 4자리 패턴 처리
            Matcher mBasic = basicPattern.matcher(l);
            if (mBasic.find()) {
                String name = mBasic.group(1).trim();
                int unit = toInt(mBasic.group(2));
                double qty = toDouble(mBasic.group(3), 1);
                int total = toInt(mBasic.group(4));
                result.getItems().add(new ReceiptItem(name, unit, qty, total));
                continue; // 품목 발견 시 다음 줄로 이동
            }

            // 2-1. 앱 스타일 패턴 처리 (상품명: 긴 설명 금액)
            Matcher mApp = appPattern.matcher(l);
            if (mApp.find()) {
                // 그룹 2: 상품명, 그룹 3: 금액
                String name = mApp.group(2).trim();
                int total = toInt(mApp.group(3));
                if (!name.contains("과세") && !name.contains("비과세")) {
                    result.getItems().add(new ReceiptItem(name, total, 1, total));
                    continue; // 품목 발견 시 다음 줄로 이동
                }
            }
            
            // 기타 품목 (배달팁, 서비스료, 수수료, 부가세/과세금액)은 3-1에서 금액 정보와 함께 엮이는 경우가 많아,
            // 별도의 엔티티 타입 파싱이 없는 정규식 방식에서는 완벽하게 분리하기 어려움.
            // 여기서는 상품명만 추출하는 것에 집중함.
        }
    }

    // ================================
    // 유틸
    // ================================
    private String normalizeText(String t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            // 전각문자 (Full-width range: FF01~FF5E) → 반각문자로 변환
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char)(c - 0xFEE0));
            } else if (c == 0x3000) { // 전각 스페이스 → 일반 스페이스
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        // 불필요한 공백 정리 및 줄바꿈 정리
        return sb.toString().replaceAll("\\s+", " ").replaceAll("([^\\n])\\s(과세금액|비과세금액|부가세)", "$1\n$2").trim();
    }

    private String findFirst(String text, String pattern, int group) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        return m.find() ? m.group(group == 0 ? 0 : group).trim() : "";
    }

    private int toInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            // 마이너스 부호를 허용
            return Integer.parseInt(s.replaceAll("[^0-9\\-]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double toDouble(String s, double def) {
        if (s == null || s.isEmpty()) return def;
        try { return Double.parseDouble(s.replaceAll("[^0-9\\.]", "")); }
        catch (Exception e) { return def; }
    }
    
    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}