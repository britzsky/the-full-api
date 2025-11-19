package com.example.demo.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.BaseReceiptParser.Item;
import com.example.demo.parser.ReceiptParserFactory;
import com.example.demo.service.OcrService;
import com.example.demo.service.AiReceiptAnalyzer;
import com.google.cloud.documentai.v1.Document;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class OcrController {

    @Autowired
    private OcrService ocrService;

    @Autowired(required = false)
    private AiReceiptAnalyzer aiAnalyzer; // 향후 자동 분석용 (지금은 사용 안 해도 OK)
    
 // ✅ 식재료 키워드
    private static final List<String> FOOD_KEYWORDS = Arrays.asList(
        "쌀", "현미", "찹쌀", "보리",
        "감자", "고구마", "양파", "당근", "마늘", "생강", "무", "배추", "파", "버섯", "양배추",
        "고기", "쇠고기", "소고기", "돼지고기", "돈육", "닭", "계육", "정육", "삼겹살",
        "계란", "달걀", "두부", "콩", "콩나물", "숙주",
        "생선", "연어", "참치", "고등어", "오징어", "새우", "조개", "해물",
        "김치", "고춧가루", "된장", "간장", "맛술", "참기름", "식초", "소금", "설탕",
        "밀가루", "전분", "치즈", "버터", "우유", "생크림", "요거트",
        "사과", "바나나", "딸기", "배", "포도", "과일"
    );

    // ✅ 소모품 키워드
    private static final List<String> SUPPLY_KEYWORDS = Arrays.asList(
        "칼", "식칼", "도마", "가위", "국자", "집게",
        "행주", "수건", "걸레", "키친타올", "종이타월", "휴지", "물티슈",
        "위생장갑", "고무장갑", "앞치마", "마스크",
        "종이컵", "비닐", "봉투", "랩", "호일", "포장",
        "세제", "주방세제", "락스", "세척제", "소독제",
        "수세미", "스펀지", "필터", "호스"
    );

    // ✅ 예외 케이스 (예: "칼국수" → 음식)
    private static final List<String> FOOD_EXCEPTIONS = Arrays.asList(
        "칼국수", "가위살" // '칼','가위' 포함하지만 실제 식재료인 경우
    );
    
    // ✅ 과면세 케이스
    private static final String VAT = "과세";
    private static final String TAX_FREE = "면세";
    
    /**
     * OCR 영수증 스캔 + 파싱
     */
    @PostMapping("/receipt-scan")
    public ResponseEntity<?> scanReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "account_id", required = false) String account_id) {

        try {
            // 1️⃣ 파일 저장
            File tempFile = saveFile(file);

            // 2️⃣ OCR 처리 (Google Document AI)
            //Document doc = ocrService.processReceiptFile(tempFile);
            
            // 2️⃣ OCR 처리 (Google Document AI)
            // [수정된 부분]: processReceiptFile -> processDocumentFile 로 변경
            Document doc = ocrService.processDocumentFile(tempFile);

            // 3️⃣ (선택) AI로 타입 자동 분석
            if (type == null || type.isEmpty()) {
                if (aiAnalyzer != null) {
                    type = aiAnalyzer.detectType(doc);
                    System.out.println("🤖 AI가 감지한 영수증 타입: " + type);
                } else {
                    type = "mart"; // 기본값
                }
            }

            // 4️⃣ 유형별 파서로 파싱
            BaseReceiptParser.ReceiptResult result = ReceiptParserFactory.parse(doc, type);
            
            // tb_account_purchase_tally 저장 map
            Map<String, Object> purchase = new HashMap<String, Object>();
            purchase.put("account_id", account_id);		// account_id 세팅.
            
            // 1️⃣ 입력값을 LocalDate로 변환 (기본적으로 2000년대 기준으로 해석됨 → 2025년)
            DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yy-MM-dd");
            LocalDate date = LocalDate.parse(result.meta.saleDate, inputFormat); // 2025-10-09

            // 2️⃣ 현재 시간 가져오기
            LocalTime nowTime = LocalTime.now(); // 시:분:초

            // 3️⃣ 날짜 + 시간 합치기
            LocalDateTime dateTime = LocalDateTime.of(date, nowTime);

            // 4️⃣ 원하는 형식으로 출력 (예: 20251009152744)
            String saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            
            purchase.put("saleId", saleId);								// saleId 세팅.
            purchase.put("saleDate", date);								// saleDate 세팅.
            purchase.put("total", result.totals.total);					// total 세팅.
            purchase.put("discount", result.totals.discount);			// discount 세팅.
            purchase.put("vat", result.totals.vat);						// vat 세팅.
            purchase.put("taxFree", result.totals.taxFree);				// taxFree 세팅.
            
            if (result.payment.type.equals("cash")) {
            	purchase.put("payType", 1);
            	purchase.put("totalCash", result.payment.approvalAmt);	// totalCash 세팅.
            } else {
            	purchase.put("payType", 2);
            	purchase.put("totalCard", result.payment.approvalAmt);	// totalCard 세팅.
            }
            purchase.put("cardNo", result.payment.cardNo);				// cardNo 세팅.
            purchase.put("cardBrand", result.payment.cardBrand);		// cardBrand 세팅.
            purchase.put("bizNo", result.merchant.bizNo);				// bizNo 세팅.
            purchase.put("type", purchase);								// tb_account_mapping 정보와 비교 후 type 값 세팅.(예정)
            
            
            
            
            // tb_account_purchase_tally_detail 저장 map
            Map<String, Object> purchaseDetail = new HashMap<String, Object>();
            for (Item r : result.items) {
            	purchaseDetail.put("account_id", account_id);
            	purchaseDetail.put("name", r.name);
            	purchaseDetail.put("taxFlag", taxify(r.taxFlag));
            	purchaseDetail.put("unitPrice", r.unitPrice);
            	purchaseDetail.put("qty", r.qty);
            	purchaseDetail.put("amount", r.amount);
            	purchaseDetail.put("itemType", classify(r.name));
            }
            
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("❌ 영수증 처리 중 오류 발생: " + e.getMessage());
        }
    }
    /**
     * ✅ TaxType 으로 결과 반환
     * @return 
     */
    public static int taxify(String taxFlag) {
        if (taxFlag == null || taxFlag.isEmpty()) {
            return 3;
        }

        if (taxFlag.equals(VAT)) {
            return 1;
        }

        if (taxFlag.equals(TAX_FREE)) {
            return 2;
        }

        return 3;
    }
    
    /**
     * ✅ 품목명으로부터 분류 결과 반환
     * @return 
     */
    public static int classify(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return 3;
        }

        // 1) 예외 케이스부터 검사
        for (String ex : FOOD_EXCEPTIONS) {
            if (itemName.contains(ex)) {
                return 3;
            }
        }

        // 2) 식재료 키워드 포함 시
        for (String keyword : FOOD_KEYWORDS) {
            if (itemName.contains(keyword)) {
                return 1;
            }
        }

        // 3) 소모품 키워드 포함 시
        for (String keyword : SUPPLY_KEYWORDS) {
            if (itemName.contains(keyword)) {
                return 2;
            }
        }

        // 4) 해당 없으면 기타
        return 3;
    }

    /**
     * MultipartFile → 임시파일 저장
     */
    private File saveFile(MultipartFile file) {
        try {
            File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(file.getBytes());
            }
            System.out.println("📂 업로드된 파일 저장 완료: " + tempFile.getAbsolutePath());
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + e.getMessage(), e);
        }
    }
}
