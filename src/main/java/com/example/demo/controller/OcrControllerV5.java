package com.example.demo.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.BaseReceiptParser.Item;
import com.example.demo.parser.ReceiptParserFactory;
import com.example.demo.service.AccountService;
import com.example.demo.service.AiReceiptAnalyzer;
import com.example.demo.service.OcrService;
import com.example.demo.service.OperateService;
import com.example.demo.utils.BizNoUtils;
import com.example.demo.utils.DateUtils;
import com.google.cloud.documentai.v1.Document;

@RestController
@CrossOrigin(origins = {
        "http://localhost:3000", // 로컬
        "http://172.30.1.48:8080", // 개발 React
        "http://52.64.151.137", // 운영 React
        "http://52.64.151.137:8080", // 운영 React
        "http://thefull.kr", // 운영 도메인
        "http://thefull.kr:8080" // 운영 도메인
})
public class OcrControllerV5 {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OperateService operateService;

    @Autowired(required = false)
    private AiReceiptAnalyzer aiAnalyzer; // 향후 자동 분석용 (지금은 사용 안 해도 OK)

    private final String uploadDir;

    @Autowired
    public OcrControllerV5(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    // ✅ 식재료 키워드
    private static final List<String> FOOD_KEYWORDS = Arrays.asList(
            "쌀", "현미", "찹쌀", "보리",
            "감자", "고구마", "양파", "당근", "마늘", "생강", "무", "배추", "파", "버섯", "양배추",
            "고기", "쇠고기", "소고기", "돼지고기", "돈육", "닭", "계육", "정육", "삼겹살",
            "계란", "달걀", "두부", "콩", "콩나물", "숙주",
            "생선", "연어", "참치", "고등어", "오징어", "새우", "조개", "해물",
            "김치", "고춧가루", "된장", "간장", "맛술", "참기름", "식초", "소금", "설탕",
            "밀가루", "전분", "치즈", "버터", "우유", "생크림", "요거트",
            "사과", "바나나", "딸기", "배", "포도", "과일");

    // ✅ 소모품 키워드
    private static final List<String> SUPPLY_KEYWORDS = Arrays.asList(
            "칼", "식칼", "도마", "가위", "국자", "집게",
            "행주", "수건", "걸레", "키친타올", "종이타월", "휴지", "물티슈",
            "위생장갑", "고무장갑", "앞치마", "마스크",
            "종이컵", "비닐", "봉투", "랩", "호일", "포장",
            "세제", "주방세제", "락스", "세척제", "소독제",
            "수세미", "스펀지", "필터", "호스");

    // ✅ 예외 케이스 (예: "칼국수" → 음식)
    private static final List<String> FOOD_EXCEPTIONS = Arrays.asList(
            "칼국수", "가위살" // '칼','가위' 포함하지만 실제 식재료인 경우
    );

    // ✅ 과면세 케이스
    private static final String VAT = "과세";
    private static final String TAX_FREE = "면세";

    /**
     * OCR 영수증 스캔 + 파싱
     * 집계표 type : 1008
     * 개인결제 전용 영수증 파서
     */
    @PostMapping("/receipt-scanV5")
    public ResponseEntity<?> scanReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) int type,
            @RequestParam(value = "account_id", required = false) String account_id,
            @RequestParam(value = "receipt_type", required = false) String receiptType,
            @RequestParam(value = "user_id", required = false) String user_id,
            @RequestParam(value = "saleDate", required = false) String saleDate,
            @RequestParam(value = "sale_id", required = false) String sale_id) {

        // 파일 저장
        File tempFile = saveFile(file);

        // ✅ purchase는 "기본적으로 다 들어간다" 전제: requestParam 기반 기본값을 먼저 세팅
        Map<String, Object> purchase = new HashMap<>();
        purchase.put("account_id", account_id);
        purchase.put("type", type);
        purchase.put("user_id", user_id);
        purchase.put("receipt_type", receiptType);
        purchase.put("saleDate", saleDate);
        purchase.put("sale_id", sale_id);

        // OCR/파싱 타임아웃용
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {

            // 1) OCR + 10초 타임아웃
            Future<Document> docFuture = executor.submit(() -> ocrService.processDocumentFile(tempFile));

            Document doc;
            try {
                doc = docFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                docFuture.cancel(true); // 인터럽트 시도
                // ✅ OCR이 10초 초과 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            } catch (Exception ex) {
                // ✅ OCR 예외 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // // AI로 타입 자동 분석
            // String resolvedReceiptType = receiptType;
            //
            // if (receiptType == null || receiptType.isEmpty()) {
            // if (aiAnalyzer != null) {
            // resolvedReceiptType = aiAnalyzer.detectType(doc);
            // System.out.println("🤖 AI가 감지한 영수증 타입: " + receiptType);
            // } else {
            // resolvedReceiptType = "MART_ITEMIZED"; // 기본값
            // }
            // purchase.put("receipt_type", receiptType);
            // }

            // 2) 파싱 + 10초 타임아웃 (원하면 3~5초로 줄여도 됨)
            Future<BaseReceiptParser.ReceiptResult> parseFuture = executor
                    .submit(() -> ReceiptParserFactory.parse(doc, receiptType));

            BaseReceiptParser.ReceiptResult result;
            try {
                result = parseFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                parseFuture.cancel(true); // 인터럽트 시도
                // ✅ 파싱 10초 초과 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            } catch (Exception ex) {
                // ✅ 파싱 예외 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }
            
            String saleId = "";
            String receiptDate = "";
            String yearStr = "";
            String monthStr = "";
            LocalDate date;
            String dayz = "";
            
            if (result == null || result.meta == null || result.meta.saleDate == null) {
//                return ResponseEntity.badRequest()
//                        .body("❌ 영수증 날짜를 인식하지 못했습니다.");
            	
            	date = DateUtils.parseFlexibleDate(saleDate);
            	LocalTime nowTime = LocalTime.now(); // 시:분:초
                LocalDateTime dateTime = LocalDateTime.of(date, nowTime);

                // 원하는 형식으로 출력 (예: 20251009152744)
                saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                receiptDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                // tally sheet 테이블 저장을 위한 연,월 세팅.
                yearStr = date.format(DateTimeFormatter.ofPattern("yyyy"));
                monthStr = date.format(DateTimeFormatter.ofPattern("MM"));
                
                // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
                int year = date.getYear();							 	// 2026
                int month = date.getMonthValue(); 						// 1~12
                int day = date.getDayOfMonth();
                dayz = String.valueOf(day);
                
                purchase.put("year", year);
                purchase.put("month", month);
            	purchase.put("saleDate", date); 
            	
            } else {
            	// =========================
                // ✅ 여기부터는 "10초 안에 완료 + result 정상"일 때만 수행
                // =========================
            	date = DateUtils.parseFlexibleDate(result.meta.saleDate);
                LocalTime nowTime = LocalTime.now(); // 시:분:초
                LocalDateTime dateTime = LocalDateTime.of(date, nowTime);

                // 원하는 형식으로 출력 (예: 20251009152744)
                saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                receiptDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                // tally sheet 테이블 저장을 위한 연,월 세팅.
                yearStr = date.format(DateTimeFormatter.ofPattern("yyyy"));
                monthStr = date.format(DateTimeFormatter.ofPattern("MM"));

                // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
                int year = date.getYear();							 	// 2026
                int month = date.getMonthValue(); 						// 1~12
                int day = date.getDayOfMonth();
                dayz = String.valueOf(day);
                
                purchase.put("year", year);
                purchase.put("month", month);
                purchase.put("saleDate", date); 						// saleDate 세팅.
            }

            String targetSaleId = (sale_id != null && !sale_id.isBlank()) ? sale_id : saleId;

            purchase.put("account_id", account_id); 				// account_id 세팅.
            purchase.put("sale_id", targetSaleId); 				// saleId 세팅.
            
            Integer parsedTotal = (result.totals != null ? result.totals.total : null);
            if (parsedTotal == null || parsedTotal == 0) {
            	// 파싱 실패/0원인 경우 사용자 입력값 사용
            	purchase.put("total", 0);
            } else {
            	purchase.put("total", parsedTotal);
            }
            
            purchase.put("discount", result.totals.discount); 		// discount 세팅.
            purchase.put("vat", result.totals.vat); 				// vat 세팅.
            purchase.put("taxFree", result.totals.taxFree); 		// taxFree 세팅.
            purchase.put("tax", result.totals.taxable); 			// tax 세팅.
            purchase.put("type", type); 							// type 세팅.
            purchase.put("use_name", result.merchant.name); 		// use_name 세팅.
            purchase.put("user_id", user_id); 						// user_id 세팅.

            String approvalAmt = result.payment != null ? result.payment.approvalAmt : null;

            int iApprovalAmt = 0;
            if (approvalAmt != null && !approvalAmt.isBlank()) {
                String clean = approvalAmt.replaceAll("[^0-9]", ""); // 숫자만 남기기
                if (!clean.isEmpty()) {
                    iApprovalAmt = Integer.parseInt(clean);
                }
            }

            if ("cash".equals(result.payment != null ? result.payment.type : null)) {
                purchase.put("payType", 1);
                purchase.put("totalCash", iApprovalAmt);
                purchase.put("totalCard", 0);
            } else {
                purchase.put("payType", 2);
                purchase.put("totalCard", iApprovalAmt);
                purchase.put("totalCash", 0);
            }

            // payment 정보 세팅 (null-safe)
            if (result.payment != null) {
                purchase.put("cardNo", result.payment.cardNo);
                purchase.put("cardBrand", result.payment.cardBrand);
            } else {
                purchase.put("cardNo", null);
                purchase.put("cardBrand", null);
            }

            // merchant 사업자번호 원본/정규화
            String merchantBizNoRaw = (result.merchant != null ? result.merchant.bizNo : null);
            String normalizedBizNo = null;
            if (merchantBizNoRaw != null && !merchantBizNoRaw.isBlank()) {
                try {
                    normalizedBizNo = BizNoUtils.normalizeBizNo(merchantBizNoRaw);
                } catch (IllegalArgumentException ex) {
                    // 형식이 이상하면 일단 원본으로라도 저장
                    normalizedBizNo = merchantBizNoRaw;
                }
            }
            purchase.put("bizNo", normalizedBizNo);

            // tb_account_purchase_tally_detail 저장 map
            List<Map<String, Object>> detailList = new ArrayList<>();

            for (Item r : result.items) {
                Map<String, Object> detailMap = new HashMap<String, Object>();
                detailMap.put("sale_id", targetSaleId);
                detailMap.put("name", r.name);
                detailMap.put("qty", r.qty);
                detailMap.put("amount", r.amount);
                detailMap.put("unitPrice", r.unitPrice);
                detailMap.put("taxType", taxify(r.taxFlag));
                detailMap.put("itemType", classify(r.name));

                detailList.add(detailMap);
            }

            if (!purchase.isEmpty()) {

                String resultPath = "";

                // 프로젝트 루트 대신 static 폴더 경로 사용
                String staticPath = new File(uploadDir).getAbsolutePath();
                String basePath = staticPath + "/" + "receipt/" + saleId + "/";

                Path dirPath = Paths.get(basePath);
                Files.createDirectories(dirPath); // 폴더 없으면 생성

                String originalFileName = file.getOriginalFilename();
                String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
                Path filePath = dirPath.resolve(uniqueFileName);

                file.transferTo(filePath.toFile()); // 파일 저장

                // 브라우저 접근용 경로 반환
                resultPath = "/image/" + "receipt" + "/" + saleId + "/" + uniqueFileName;
                purchase.put("receipt_image", resultPath);
            }

            int iResult = 0;

            // tall sheet 테이블 저장을 위한 값 세팅.
            String day = "day_" + dayz;
            int total2 = 0;
            Object totalObj = purchase.get("total");
            total2 = Integer.parseInt(totalObj.toString());

            purchase.put(day, total2);
            purchase.put("count_year", yearStr);
            purchase.put("count_month", monthStr);

            iResult += accountService.AccountPurchaseSave(purchase);
            iResult += accountService.TallySheetPaymentSave(purchase);

            for (Map<String, Object> m : detailList) {
                iResult += accountService.AccountPurchaseDetailSave(m);
            }

            return ResponseEntity.ok(purchase);

        } catch (Exception e) {
        	try {
				return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
			} catch (Exception e1) {
				return ResponseEntity.internalServerError()
			            .body("❌ 영수증 처리 중 오류 발생: " + e.getMessage());
			}
        } finally {
            executor.shutdownNow(); // 타임아웃 스레드 정리
            // 🔹 temp 파일 삭제
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    System.out.println("⚠ 임시 파일 삭제 실패: " + tempFile.getAbsolutePath());
                }
            }
        }
    }

    // =========================
    // ✅ fallback: OCR/파싱 실패 시 requestParam만으로 저장
    // =========================
    private Map<String, Object> saveWithRequestParamsOnly(Map<String, Object> purchase, MultipartFile file)
            throws Exception {
        // sale_id 생성
        LocalDateTime now = LocalDateTime.now();
        Object saleIdObj = purchase.get("sale_id");
        String saleId = (saleIdObj != null && !String.valueOf(saleIdObj).isBlank())
                ? String.valueOf(saleIdObj)
                : now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        purchase.put("sale_id", saleId);

        // cell_date 기준으로 count_year/count_month 세팅 (없으면 오늘)
        LocalDate baseDate;
        String cellDate = (String) purchase.get("cell_date");
        try {
            baseDate = (cellDate != null && !cellDate.isBlank()) ? LocalDate.parse(cellDate) : LocalDate.now();
        } catch (Exception ignore) {
            baseDate = LocalDate.now();
        }
        // fallback에서도 프로시저에 필요한 날짜를 채움
        purchase.put("saleDate", baseDate);
        purchase.put("payment_dt", baseDate);
        purchase.put("count_year", baseDate.format(DateTimeFormatter.ofPattern("yyyy")));
        purchase.put("count_month", baseDate.format(DateTimeFormatter.ofPattern("MM")));

        purchase.putIfAbsent("discount", 0);
        purchase.putIfAbsent("tax", 0);
        purchase.putIfAbsent("vat", 0);
        purchase.putIfAbsent("taxFree", 0);

        int year = baseDate.getYear();
        int month = baseDate.getMonthValue();
        purchase.put("year", year);
        purchase.put("month", month);

        // 이미지 저장 및 경로 세팅
        attachReceiptImage(purchase, file, saleId);

        // tally 기본값
        String cellDay = (String) purchase.get("cell_day");
        if (cellDay != null && !cellDay.isBlank()) {
            String dayKey = "day_" + cellDay;
            purchase.put(dayKey, safeInt(purchase.get("total")));
        }

        // DB 저장 (detail 저장은 없음)
        int iResult = 0;
        iResult += accountService.AccountPurchaseSave(purchase);
        iResult += accountService.TallySheetPaymentSave(purchase);

        return purchase;
    }

    // ✅ fallback용 이미지 저장 로직 분리
    private void attachReceiptImage(Map<String, Object> purchase, MultipartFile file, String saleId) throws Exception {
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + "receipt/" + saleId + "/";
        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile());
        String resultPath = "/image/" + "receipt" + "/" + saleId + "/" + uniqueFileName;
        purchase.put("receipt_image", resultPath);
    }

    private int safeInt(Object v) {
        if (v == null)
            return 0;
        try {
            return Integer.parseInt(String.valueOf(v).replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * ✅ TaxType 으로 결과 반환
     * 
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
     * 
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
