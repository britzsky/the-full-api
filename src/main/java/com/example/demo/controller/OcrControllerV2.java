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
import com.example.demo.parser.HeadOfficeReceiptParserFactory;
import com.example.demo.service.AccountService;
import com.example.demo.service.AiReceiptAnalyzer;
import com.example.demo.service.OcrService;
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
public class OcrControllerV2 {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private AccountService accountService;

    @Autowired(required = false)
    private AiReceiptAnalyzer aiAnalyzer; // 향후 자동 분석용 (지금은 사용 안 해도 OK)

    private final String uploadDir;

    @Autowired
    public OcrControllerV2(@Value("${file.upload-dir}") String uploadDir) {
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
            "수세미", "스펀지", "필터", "호스", "밥솥");

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
    @PostMapping("/Corporate/receipt-scan")
    public ResponseEntity<?> scanReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sale_id", required = false) String sale_id,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "objectValue", required = false) String objectValue,
            @RequestParam(value = "folderValue", required = false) String folderValue,
            @RequestParam(value = "cardNo", required = false) String cardNo,
            @RequestParam(value = "cardBrand", required = false) String cardBrand,
            @RequestParam(value = "saveType", required = false) String saveType,
            @RequestParam(value = "receiptType", required = false) String receiptType) {

        // 파일 저장
        File tempFile = saveFile(file);

        // ✅ purchase는 "기본적으로 다 들어간다" 전제: requestParam 기반 기본값을 먼저 세팅
        Map<String, Object> purchase = new HashMap<>();
        
        if (sale_id != null && !sale_id.isBlank()) {
            purchase.put("sale_id", sale_id);
        }
        
        purchase.put("account_id", objectValue);
        purchase.put("type", type);
        purchase.put("saveType", saveType);
        purchase.put("receipt_type", receiptType);
        purchase.put("folderValue", folderValue);
        purchase.put("cardNo", cardNo);
        purchase.put("cardBrand", cardBrand);

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

            // AI로 타입 자동 분석
            // if (type == null || type.isEmpty()) {
            // if (aiAnalyzer != null) {
            // type = aiAnalyzer.detectType(doc);
            // System.out.println("🤖 AI가 감지한 영수증 타입: " + type);
            // } else {
            // type = "mart"; // 기본값
            // }
            // }

            // 2) 파싱 + 10초 타임아웃
            Future<BaseReceiptParser.ReceiptResult> parseFuture = executor
                    .submit(() -> HeadOfficeReceiptParserFactory.parse(doc, type));

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

            // 3) 파싱 결과가 없으면 fallback
            if (result == null) {
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // ✅ accountcorporatecardsheet(/card-receipt/parse)와 동작 차이를 줄이기 위해
            // 날짜를 못 읽은 경우에도 전체 저장 플로우는 진행한다.
            if (result.meta == null) {
                result.meta = new BaseReceiptParser.Meta();
            }
            if (result.meta.saleDate == null || result.meta.saleDate.isBlank()) {
                result.meta.saleDate = LocalDate.now().toString();
            }

            // 입력값을 LocalDate로 변환 (기본적으로 2000년대 기준으로 해석됨 → 2025년)
            // DateTimeFormatter inputFormat = DateTimeFormatter.ofPattern("yy-MM-dd");
            // LocalDate date = LocalDate.parse(result.meta.saleDate, inputFormat); //
            // 2025-10-09

            // if (result == null || result.meta == null || result.meta.saleDate == null) {
            // return ResponseEntity.badRequest()
            // .body("❌ 영수증 날짜를 인식하지 못했습니다.");
            // }

            // =========================
            // ✅ 여기부터는 "10초 안에 완료 + result 정상"일 때만 수행
            // =========================

            // 4) saleId 생성(영수증 날짜 기반)
            LocalDate date = DateUtils.parseFlexibleDate(result.meta.saleDate);
            LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.now());
            String saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            // 재업로드/수정 시 전달된 sale_id를 우선 사용한다.
            String targetSaleId = (sale_id != null && !sale_id.isBlank()) ? sale_id : saleId;

            // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
            int year = date.getYear(); // 2026
            int month = date.getMonthValue(); // 1~12

            // 5) DB 저장 payload 만들기
            Map<String, Object> corporateCard = new HashMap<>();

            boolean isAccount = "account".equalsIgnoreCase(saveType); // ✅ NPE 방지

            corporateCard.put("account_id", objectValue);
            corporateCard.put("year", year);
            corporateCard.put("month", month);
            corporateCard.put("receipt_type", receiptType);
            // 본사 법인카드 집계표 프로시저는 정수 type이 필요하다.
            corporateCard.put("type", resolveTallyType(type, isAccount));

            corporateCard.put("cardNo", cardNo);
            corporateCard.put("cardBrand", cardBrand);
            
            corporateCard.put("sale_id", targetSaleId);
            
            corporateCard.put("use_name", result.merchant.name); // use_name 세팅.
            corporateCard.put("payment_dt", date); // payment_dt 세팅.
            corporateCard.put("discount", result.totals.discount); // discount 세팅.
            corporateCard.put("vat", result.totals.vat); // vat 세팅.
            corporateCard.put("taxFree", result.totals.taxFree); // taxFree 세팅.
            corporateCard.put("tax", result.totals.taxable); // tax 세팅.

            String approvalAmt = result.payment != null ? result.payment.approvalAmt : null;

            String paymentCardBrand = result.payment != null ? result.payment.cardBrand : null;

            if (approvalAmt == null) {
                corporateCard.put("total", result.totals.total);

                if (paymentCardBrand == null) {
                    corporateCard.put("payType", 1);
                    corporateCard.put("totalCash", result.totals.total);
                    corporateCard.put("totalCard", 0);
                } else {
                    corporateCard.put("payType", 2);
                    corporateCard.put("totalCard", result.totals.total);
                    corporateCard.put("totalCash", 0);
                }

            } else {
                int iApprovalAmt = 0;
                if (approvalAmt != null && !approvalAmt.isBlank()) {
                    String clean = approvalAmt.replaceAll("[^0-9]", ""); // 숫자만 남기기
                    if (!clean.isEmpty()) {
                        iApprovalAmt = Integer.parseInt(clean);
                    }
                }

                // total(int 컬럼)에는 콤마 제거된 숫자값으로 저장해야 Data truncated(1265)를 피할 수 있다.
                corporateCard.put("total", iApprovalAmt); // total 세팅.

                if (paymentCardBrand == null) {
                    corporateCard.put("payType", 1);
                    corporateCard.put("totalCash", iApprovalAmt);
                    corporateCard.put("totalCard", 0);
                } else {
                    corporateCard.put("payType", 2);
                    corporateCard.put("totalCard", iApprovalAmt);
                    corporateCard.put("totalCash", 0);
                }
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
            corporateCard.put("bizNo", normalizedBizNo);

            // tb_account_purchase_tally_detail 저장 map
            List<Map<String, Object>> detailList = new ArrayList<>();

            for (Item r : result.items) {
                Map<String, Object> detailMap = new HashMap<String, Object>();
                detailMap.put("account_id", objectValue);
                detailMap.put("sale_id", targetSaleId);
                detailMap.put("name", r.name);
                detailMap.put("qty", r.qty);
                detailMap.put("amount", r.amount);
                detailMap.put("unitPrice", r.unitPrice);
                detailMap.put("taxType", taxify(r.taxFlag));
                detailMap.put("itemType", classify(r.name));

                // 본사법인카드 특성상, 디테일에 있는 itemType(소모품, 식재료)에 따라
                // 집계표의 거래처(기타:type 1002, 기타비용:1003) 따로 저장이 되어야 함.
                // 따라서 payment_dt 가 집계표의 날짜와 매핑하기 때문에 detail 에도 적용해야 함.
                detailMap.put("payment_dt", date);

                // 상품구분 3:알수없음 은 우선 소모품 type:1002 로 저장
                if (classify(r.name) == 3) {
                    detailMap.put("type", 1002);
                } else if (classify(r.name) == 1) { // 식재료면 type:1003
                    detailMap.put("type", 1003);
                } else { // 소모품은 type:1002
                    detailMap.put("type", 1002);
                }
                // 상단에서 파싱한 year,month 세팅(손익표 적용을 위해 필요)
                detailMap.put("year", year);
                detailMap.put("month", month);

                detailList.add(detailMap);
            }

            if (!corporateCard.isEmpty()) {

                String resultPath = "";

                // 프로젝트 루트 대신 static 폴더 경로 사용
                String staticPath = new File(uploadDir).getAbsolutePath();
                String basePath = staticPath + "/" + folderValue + "/" + targetSaleId + "/";

                Path dirPath = Paths.get(basePath);
                Files.createDirectories(dirPath); // 폴더 없으면 생성

                String originalFileName = file.getOriginalFilename();
                String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
                Path filePath = dirPath.resolve(uniqueFileName);

                file.transferTo(filePath.toFile()); // 파일 저장

                // 브라우저 접근용 경로 반환
                resultPath = "/image/" + folderValue + "/" + targetSaleId + "/" + uniqueFileName;
                corporateCard.put("receipt_image", resultPath);
            }

            // DB 저장
            int iResult = 0;
            if (isAccount) {
                iResult += accountService.AccountCorporateCardPaymentSave(corporateCard);
                iResult += accountService.TallySheetCorporateCardPaymentSave(corporateCard);
                for (Map<String, Object> m : detailList) {
                    iResult += accountService.AccountCorporateCardPaymentDetailLSave(m);
                }
            } else {
                iResult += accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
                for (Map<String, Object> m : detailList) {
                    iResult += accountService.HeadOfficeCorporateCardPaymentDetailLSave(m);
                    iResult += accountService.TallySheetCorporateCardPaymentSaveV2(m);
                }
            }

            return ResponseEntity.ok(corporateCard);

        } catch (Exception e) {
        	try {
				return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
			} catch (Exception e1) {
				return ResponseEntity.internalServerError()
			            .body("❌ 영수증 처리 중 오류 발생: " + e.getMessage());
			}
        } finally {
            executor.shutdownNow(); // 타임아웃 스레드 정리
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
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

        Map<String, Object> corporateCard = new HashMap<>();

        // 기본 폴더 보정
        String folderValue = (String) purchase.get("folderValue");
        if (folderValue == null || folderValue.isBlank()) {
            folderValue = "card";
        }
        
        // sale_id 생성
        LocalDateTime now = LocalDateTime.now();
        String saleId = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        // ✅ purchase에 sale_id가 없을 수 있으므로 null-safe 처리
        Object saleObj = purchase.get("sale_id");
        String targetSaleId;
        if (saleObj == null || saleObj.toString().isBlank()) {
            targetSaleId = saleId;
        } else {
            targetSaleId = saleObj.toString();
        }
        corporateCard.put("sale_id", targetSaleId);
        
        String saveType = (String) purchase.get("saveType");
        boolean isAccount = "account".equalsIgnoreCase(saveType);

        corporateCard.put("account_id", purchase.get("account_id"));
        corporateCard.put("year", now.getYear());
        corporateCard.put("month", now.getMonthValue());
        corporateCard.put("receipt_type", purchase.get("receipt_type"));
        // fallback에서도 집계표 프로시저용 type을 숫자로 보정한다.
        corporateCard.put("type", resolveTallyType(purchase.get("type"), isAccount));

        String cardNo = (String) purchase.get("cardNo");
        String cardBrand = (String) purchase.get("cardBrand");
        corporateCard.put("cardNo", cardNo);
        corporateCard.put("cardBrand", cardBrand);
        
        corporateCard.put("use_name", null);
        corporateCard.put("payment_dt", now.toLocalDate());

        // 금액 관련 기본값
        corporateCard.put("total", 0);
        corporateCard.put("discount", 0);
        corporateCard.put("vat", 0);
        corporateCard.put("taxFree", 0);
        corporateCard.put("tax", 0);

        if (cardBrand == null || cardBrand.isBlank()) {
            corporateCard.put("payType", 1);
            corporateCard.put("totalCash", 0);
            corporateCard.put("totalCard", 0);
        } else {
            corporateCard.put("payType", 2);
            corporateCard.put("totalCard", 0);
            corporateCard.put("totalCash", 0);
        }

        // 이미지 저장 및 경로 세팅
        attachReceiptImage(corporateCard, file, targetSaleId, folderValue);

        // DB 저장 (detail 저장은 없음)
        int iResult = 0;
        if (isAccount) {
            iResult += accountService.AccountCorporateCardPaymentSave(corporateCard);
            iResult += accountService.TallySheetCorporateCardPaymentSave(corporateCard);
        } else {
            iResult += accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
            iResult += accountService.TallySheetCorporateCardPaymentSaveV2(corporateCard);
        }

        return corporateCard;
    }

    // ✅ fallback용 이미지 저장 로직 분리
    private void attachReceiptImage(Map<String, Object> corporateCard, MultipartFile file, String saleId,
            String folderValue) throws Exception {
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + folderValue + "/" + saleId + "/";

        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile());
        String resultPath = "/image/" + folderValue + "/" + saleId + "/" + uniqueFileName;
        corporateCard.put("receipt_image", resultPath);
    }

    // 집계표 프로시저 p_type은 정수 컬럼이므로 문자열 타입값(CONVENIENCE/MART_ITEMIZED)은 1000으로 보정한다.
    private int resolveTallyType(Object rawType, boolean isAccount) {
        if (isAccount) {
            // account 경로는 별도 프로시저(TallySheetCorporateCardPaymentSave)를 사용하므로 값은 의미가 거의 없음.
            return 1000;
        }
        if (rawType == null) return 1000;
        String s = String.valueOf(rawType).trim();
        if (s.isEmpty()) return 1000;
        if (!s.matches("^-?\\d+$")) return 1000;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    /**
     * ✅ TaxType 으로 결과 반환
     * 
     * @return
     */
    public static int taxify(String taxFlag) {
        if (taxFlag == null) {
            return 3;
        }

        String normalized = taxFlag.trim();
        if (normalized.isEmpty()) {
            return 3;
        }
        if (normalized.contains(TAX_FREE)) {
            return 2;
        }
        if (normalized.contains(VAT)) {
            return 1;
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
            // 기본은 식재료로 둬서 "예산미발행(3)" 과다 분류를 줄인다.
            return 1;
        }

        // 1) 예외 케이스부터 검사
        for (String ex : FOOD_EXCEPTIONS) {
            if (itemName.contains(ex)) {
                return 1;
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

        // 4) 해당 없으면 기본 식재료
        return 1;
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
