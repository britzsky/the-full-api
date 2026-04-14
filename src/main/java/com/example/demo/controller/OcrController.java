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
public class OcrController {

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
    public OcrController(@Value("${file.upload-dir}") String uploadDir) {
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
     * 집계표 type : 1000, 1002, 1003, 1008 외 모두
     * 영수증 파서
     */
    @PostMapping("/receipt-scan")
    public ResponseEntity<?> scanReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "account_id", required = false) String account_id,
            @RequestParam(value = "cell_day", required = false) String cell_day,
            @RequestParam(value = "cell_date", required = false) String cell_date,
            @RequestParam(value = "saleDate", required = false) String saleDate,
            @RequestParam(value = "saveType", required = false) String saveType,
            @RequestParam(value = "receipt_type", required = false) String receiptType,
            @RequestParam(value = "user_id", required = false) String user_id,
            @RequestParam(value = "total", required = false, defaultValue = "0") Integer total) {

        // 파일 저장
        File tempFile = saveFile(file);

        // saleDate: cell_date 우선, 없으면 saleDate 파라미터 사용
        String resolvedSaleDate = (cell_date != null && !cell_date.trim().isEmpty()) ? cell_date : saleDate;

        // ✅ purchase는 "기본적으로 다 들어간다" 전제: requestParam 기반 기본값을 먼저 세팅
        Map<String, Object> purchase = new HashMap<>();
        purchase.put("account_id", account_id);
        purchase.put("type", type != null ? type : 0);
        purchase.put("user_id", user_id);
        purchase.put("saveType", saveType);
        purchase.put("cell_day", cell_day);
        purchase.put("saleDate", resolvedSaleDate);
        purchase.put("payment_dt", resolvedSaleDate);
        purchase.put("receipt_type", receiptType);
        purchase.put("total", total);

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
                // ✅ OCR이 10초 초과 -> fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            } catch (Exception ex) {
                // ✅ OCR 예외 -> fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // 2) receiptType 자동 감지 (OCR 성공했을 때만 의미 있음)
            String resolvedReceiptType = receiptType;

            if (receiptType == null || receiptType.isEmpty()) {
                if (aiAnalyzer != null) {
                    resolvedReceiptType = aiAnalyzer.detectType(doc);
                } else {
                    resolvedReceiptType = "MART_ITEMIZED";
                }
                purchase.put("receipt_type", resolvedReceiptType);
            } else {
                purchase.put("receipt_type", resolvedReceiptType);
            }

            // 3) 파싱 + 10초 타임아웃 (원하면 3~5초로 줄여도 됨)
            Future<BaseReceiptParser.ReceiptResult> parseFuture = executor
                    .submit(() -> ReceiptParserFactory.parse(doc, receiptType));

            BaseReceiptParser.ReceiptResult result;

            try {
                result = parseFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                parseFuture.cancel(true);
                // ✅ 파싱이 10초 초과 -> fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            } catch (Exception ex) {
                // ✅ 파싱 예외 -> fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // 4) 파싱 결과가 없거나 핵심 meta가 없으면 fallback
            if (result == null || result.meta == null) {
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // =========================
            // ✅ 여기부터는 "10초 안에 완료 + result 정상"일 때만 수행
            // =========================

            // saleId 생성(영수증 날짜 기반)
            LocalDate date = DateUtils.parseFlexibleDate(result.meta.saleDate);
            LocalTime nowTime = LocalTime.now();
            LocalDateTime dateTime = LocalDateTime.of(date, nowTime);

            // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
            int year = date.getYear(); // 2026
            int month = date.getMonthValue(); // 1~12

            purchase.put("year", year);
            purchase.put("month", month);

            String saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String receiptDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 집계표 날짜 불일치면 기존 로직 유지(원하면 이 케이스도 fallback으로 바꿀 수 있음)
            if (cell_date != null && !cell_date.isBlank() && !receiptDate.equals(cell_date)) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", 400);
                error.put("message", "선택된 집계표 일자와 영수증 거래일자가 일치하지 않습니다.\n");
                error.put("[집계표]", cell_date);
                error.put("[거래일자]", receiptDate);
                return ResponseEntity.badRequest().body(error);
            }

            String yearStr = date.format(DateTimeFormatter.ofPattern("yyyy"));
            String monthStr = date.format(DateTimeFormatter.ofPattern("MM"));

            // 재업로드 시 기존 sale_id 유지, 없을 때만 새로 생성
            Object existingSaleIdObj = purchase.get("sale_id");
            String finalSaleId = (existingSaleIdObj != null && !String.valueOf(existingSaleIdObj).trim().isEmpty())
                    ? String.valueOf(existingSaleIdObj).trim()
                    : saleId;
            purchase.put("sale_id", finalSaleId);
            purchase.put("saleDate", receiptDate);
            purchase.put("payment_dt", receiptDate);

            if (result.totals.total == 0 || result.totals.total == null) {
                purchase.put("total", total); // total 세팅.
            } else {
                purchase.put("total", result.totals.total); // total 세팅.
            }

            purchase.put("discount", result.totals.discount);
            purchase.put("vat", result.totals.vat);
            purchase.put("taxFree", result.totals.taxFree);
            purchase.put("use_name", result.merchant != null ? result.merchant.name : null);

            // 결제금액
            String approvalAmt = (result.payment != null ? result.payment.approvalAmt : null);
            int iApprovalAmt = 0;

            if (approvalAmt != null && !approvalAmt.isBlank()) {
                String clean = approvalAmt.replaceAll("[^0-9]", "");
                if (!clean.isEmpty())
                    iApprovalAmt = Integer.parseInt(clean);
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

            if (result.payment != null) {
                purchase.put("cardNo", result.payment.cardNo);
                purchase.put("cardBrand", result.payment.cardBrand);
            } else {
                purchase.put("cardNo", null);
                purchase.put("cardBrand", null);
            }

            // 사업자번호
            String merchantBizNoRaw = (result.merchant != null ? result.merchant.bizNo : null);
            String normalizedBizNo = null;
            if (merchantBizNoRaw != null && !merchantBizNoRaw.isBlank()) {
                try {
                    normalizedBizNo = BizNoUtils.normalizeBizNo(merchantBizNoRaw);
                } catch (IllegalArgumentException ex) {
                    normalizedBizNo = merchantBizNoRaw;
                }
            }
            purchase.put("bizNo", normalizedBizNo);
            
            // 씨엔푸드, 대성상회 제외 사업자번호 체크.
            if (type != 25 || type != 45 || type != 1011) {
            	// ✅ 매핑 체크 (기존 로직 유지)
                List<Map<String, Object>> mappingList = accountService.AccountMappingList(account_id);
                boolean hasMapping = false;

                if (normalizedBizNo != null && mappingList != null) {
                    for (Map<String, Object> m : mappingList) {
                        try {
                            Object bizNoObj = m.get("biz_no");
                            if (bizNoObj == null)
                                continue;
                            String formattedBizNo2 = BizNoUtils.normalizeBizNo(bizNoObj.toString());
                            if (formattedBizNo2.equals(normalizedBizNo)) {
                                // purchase.put("type", m.get("type"));
                                hasMapping = true;
                                break;
                            }
                        } catch (IllegalArgumentException ignore) {
                        }
                    }
                }

//                if (!hasMapping) {
//                    Map<String, Object> error = new HashMap<>();
//                    error.put("code", 400);
//                    error.put("message",
//                            "해당 영수증의 사업자번호가 현재 선택한 거래처에 매핑되어 있지 않습니다.\n" +
//                                    "먼저 [거래처 연결]에서 사업자번호를 매핑해 주세요.");
//                    error.put("bizNo", normalizedBizNo != null ? normalizedBizNo : merchantBizNoRaw);
//                    return ResponseEntity.badRequest().body(error);
//                }
            }

            // 상세 저장 리스트
            List<Map<String, Object>> detailList = new ArrayList<>();
            if (result.items != null) {
                for (Item r : result.items) {
                    Map<String, Object> detailMap = new HashMap<>();
                    detailMap.put("sale_id", finalSaleId);
                    detailMap.put("name", r.name);
                    detailMap.put("qty", r.qty);
                    detailMap.put("amount", r.amount);
                    detailMap.put("unitPrice", r.unitPrice);
                    detailMap.put("taxType", taxify(r.taxFlag));
                    detailMap.put("itemType", classify(r.name));
                    detailList.add(detailMap);
                }
            }

            // 재업로드 시: DB에서 기존 receipt_image 경로 조회해서 삭제 대상으로 세팅
            if (purchase.get("receipt_image") == null || String.valueOf(purchase.get("receipt_image")).trim().isEmpty()) {
                try {
                    Map<String, Object> imgParam = new HashMap<>();
                    imgParam.put("sale_id", finalSaleId);
                    imgParam.put("account_id", account_id);
                    String existingImg = accountService.AccountPurchaseReceiptImageBySaleId(imgParam);
                    if (existingImg != null && !existingImg.trim().isEmpty()) {
                        purchase.put("receipt_image", existingImg);
                    }
                } catch (Exception ignore) {}
            }

            // 이미지 저장 + purchase.receipt_image (기존 파일 삭제 후 새 파일 저장)
            attachReceiptImage(purchase, file, finalSaleId);

            // tally 저장값
            String day = "day_" + cell_day;
            int total2 = safeInt(purchase.get("total"));
            purchase.put(day, total2);
            purchase.put("count_year", yearStr);
            purchase.put("count_month", monthStr);

            int iResult = 0;
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
            executor.shutdownNow();
            if (tempFile != null && tempFile.exists())
                tempFile.delete();
        }
    }

    // =========================
    // ✅ fallback: OCR/파싱 실패 시 requestParam만으로 저장
    // =========================
    private Map<String, Object> saveWithRequestParamsOnly(Map<String, Object> purchase, MultipartFile file)
            throws Exception {
        // 재업로드 시 기존 sale_id 유지, 없을 때만 새로 생성
        LocalDateTime now = LocalDateTime.now();
        String saleId = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        Object existingSaleId = purchase.get("sale_id");
        if (existingSaleId == null || String.valueOf(existingSaleId).trim().isEmpty()) {
            purchase.put("sale_id", saleId);
        } else {
            saleId = String.valueOf(existingSaleId).trim();
        }

        // saleDate: resolvedSaleDate(=cell_date or saleDate param) 우선, 없으면 현재
        LocalDate baseDate;
        String saleDateVal = purchase.get("saleDate") != null ? String.valueOf(purchase.get("saleDate")).trim() : "";
        String cellDate = purchase.get("cell_date") != null ? String.valueOf(purchase.get("cell_date")).trim() : "";
        String dateStr = !saleDateVal.isEmpty() ? saleDateVal : (!cellDate.isEmpty() ? cellDate : "");
        try {
            baseDate = !dateStr.isEmpty() ? DateUtils.parseFlexibleDate(dateStr) : LocalDate.now();
        } catch (Exception ignore) {
            baseDate = LocalDate.now();
        }
        // saleDate가 없으면 baseDate로 세팅
        if (saleDateVal.isEmpty()) {
            purchase.put("saleDate", baseDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        purchase.put("count_year", baseDate.format(DateTimeFormatter.ofPattern("yyyy")));
        purchase.put("count_month", baseDate.format(DateTimeFormatter.ofPattern("MM")));

        // total은 requestparam에 없으니 0 (혹은 saveType에 따라 다른 정책 가능)
        purchase.putIfAbsent("discount", 0);
        purchase.putIfAbsent("vat", 0);
        purchase.putIfAbsent("taxFree", 0);

        // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
        int year = baseDate.getYear(); // 2026
        int month = baseDate.getMonthValue(); // 1~12

        purchase.put("year", year);
        purchase.put("month", month);

        // 이미지 저장(원하면 이 케이스에서는 저장 안해도 됨)
        attachReceiptImage(purchase, file, saleId);

        // tally 저장값
        String cellDay = (String) purchase.get("cell_day");
        if (cellDay != null && !cellDay.isBlank()) {
            String dayKey = "day_" + cellDay;
            purchase.put(dayKey, safeInt(purchase.get("total")));
        }

        int iResult = 0;
        iResult += accountService.AccountPurchaseSave(purchase);
        iResult += accountService.TallySheetPaymentSave(purchase);
        // ✅ detail은 저장하지 않음(파싱값 없으니까)

        return purchase;
    }

    // ✅ 이미지 저장 로직: 재업로드 시 기존 파일 삭제 후 새 파일 저장
    private void attachReceiptImage(Map<String, Object> purchase, MultipartFile file, String saleId) throws Exception {
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + "receipt/" + saleId + "/";
        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile());
        String newPath = "/image/" + "receipt" + "/" + saleId + "/" + uniqueFileName;

        // 기존 파일 삭제 (service의 deleteReplacedReceiptImage 활용)
        Object existingImageObj = purchase.get("receipt_image");
        if (existingImageObj != null && !String.valueOf(existingImageObj).trim().isEmpty()) {
            accountService.DeleteOldReceiptImage(String.valueOf(existingImageObj).trim(), newPath);
        }

        purchase.put("receipt_image", newPath);
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
