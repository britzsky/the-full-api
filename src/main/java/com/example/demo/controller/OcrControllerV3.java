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

import com.example.demo.model.CardReceiptResponse;
import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.BaseReceiptParser.Item;
import com.example.demo.service.AccountService;
import com.example.demo.service.AiReceiptAnalyzer;
import com.example.demo.service.CardReceiptParseService;
import com.example.demo.service.OcrService;
import com.example.demo.service.OperateService;
import com.example.demo.utils.BizNoUtils;
import com.example.demo.utils.DateUtils;

@RestController
@CrossOrigin(origins = {
        "http://localhost:3000", // 로컬
        "http://172.30.1.48:8080", // 개발 React
        "http://52.64.151.137", // 운영 React
        "http://52.64.151.137:8080", // 운영 React
        "http://thefull.kr", // 운영 도메인
        "http://thefull.kr:8080" // 운영 도메인
})
public class OcrControllerV3 {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OperateService operateService;

    @Autowired
    private CardReceiptParseService cardReceiptParseService;

    @Autowired(required = false)
    private AiReceiptAnalyzer aiAnalyzer; // 향후 자동 분석용 (지금은 사용 안 해도 OK)

    private final String uploadDir;

    @Autowired
    public OcrControllerV3(@Value("${file.upload-dir}") String uploadDir) {
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
    @PostMapping("/receipt-scanV3")
    public ResponseEntity<?> scanReceipt(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "total", required = false) Integer total,
            @RequestParam(value = "type", required = false) Integer type,
            @RequestParam(value = "card_idx", required = false) Integer idx,
            @RequestParam(value = "account_id", required = false) String account_id,
            @RequestParam(value = "sale_id", required = false) String sale_id,
            @RequestParam(value = "receipt_type", required = false) String receipt_type,
            @RequestParam(value = "use_name", required = false) String use_name,
            @RequestParam(value = "cell_date", required = false) String cell_date,
            @RequestParam(value = "saveType", required = false) String saveType,
            @RequestParam(value = "card_brand", required = false) String card_brand,
            @RequestParam(value = "card_no", required = false) String card_no) {

        // 1️⃣ 파일 저장
        File tempFile = saveFile(file);

        // ✅ purchase는 "기본적으로 다 들어간다" 전제: requestParam 기반 기본값을 먼저 세팅
        Map<String, Object> purchase = new HashMap<>();
        purchase.put("total", total != null ? total : 0);
        purchase.put("type", type);  // null이면 그대로 null (프론트에서 보낸 값 유지)
        purchase.put("idx", idx != null ? idx : 0);
        purchase.put("account_id", account_id);
        purchase.put("sale_id", sale_id);
        purchase.put("receipt_type", receipt_type);
        purchase.put("use_name", use_name);
        purchase.put("cell_date", cell_date);
        purchase.put("saveType", saveType);
        purchase.put("card_brand", card_brand);
        purchase.put("card_no", card_no);

        // OCR/파싱 타임아웃용
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            // 1) OCR+파싱 + 10초 타임아웃
            Future<CardReceiptResponse> parseFuture = executor
                    .submit(() -> cardReceiptParseService.parseFile(tempFile, receipt_type));

            CardReceiptResponse res;
            try {
                res = parseFuture.get(10, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                parseFuture.cancel(true); // 인터럽트 시도
                // ✅ 파싱 10초 초과 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            } catch (Exception ex) {
                // ✅ 파싱 예외 -> requestParam 기반 fallback 저장
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            BaseReceiptParser.ReceiptResult result = res.result;

            if (result == null || result.meta == null || result.meta.saleDate == null) {
                return ResponseEntity.ok(saveWithRequestParamsOnly(purchase, file));
            }

            // tb_account_purchase_tally 저장 map
            Map<String, Object> accountMap = new HashMap<String, Object>();
            accountMap.put("account_id", account_id); // account_id 세팅.

            // if (result == null || result.meta == null || result.meta.saleDate == null) {
            // return ResponseEntity.badRequest()
            // .body("❌ 영수증 날짜를 인식하지 못했습니다.");
            // }

            // =========================
            // ✅ 여기부터는 "10초 안에 완료 + result 정상"일 때만 수행
            // =========================

            // 여러 타입의 날짜형식을 매핑.
            LocalDate date = DateUtils.parseFlexibleDate(result.meta.saleDate);
            LocalTime nowTime = LocalTime.now(); // 시:분:초
            LocalDateTime dateTime = LocalDateTime.of(date, nowTime);

            // 원하는 형식으로 출력 (예: 20251009152744)
            String saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String receiptDate = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // tally sheet 테이블 저장을 위한 연,월 세팅.
            String yearStr = date.format(DateTimeFormatter.ofPattern("yyyy"));
            String monthStr = date.format(DateTimeFormatter.ofPattern("MM"));
            String dayStr = date.format(DateTimeFormatter.ofPattern("D"));

            // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
            int year = date.getYear(); // 2026
            int month = date.getMonthValue(); // 1~12

            String merchantName = (result.merchant != null ? result.merchant.name : null);
            if (merchantName == null || merchantName.isBlank()) {
                accountMap.put("use_name", use_name);
            } else {
                accountMap.put("use_name", merchantName);
            }

            if (sale_id == null) {
                accountMap.put("sale_id", saleId); // sale_id 가 없을 때, 생성된 saleId 세팅.
            } else {
                accountMap.put("sale_id", sale_id); // sale_id 가 있으면 전달받은 sale_id 세팅.
            }

            accountMap.put("saleDate", date); // saleDate 세팅.
            accountMap.put("payment_dt", date); // payment_dt 세팅.
            accountMap.put("type", type); // mapping 테이블의 type 값 세팅
            accountMap.put("idx", idx); // 카드 idx 세팅
            accountMap.put("receipt_type", receipt_type); // 영수증 타입 세팅
            accountMap.put("cardBrand", card_brand); // 카드사 세팅
            accountMap.put("cardNo", card_no); // 카드번호 세팅
            accountMap.put("year", year); // 손익표/예산용 year 세팅
            accountMap.put("month", month); // 손익표/예산용 month 세팅

            // 영수증 파싱에서 합계금액을 못구하면 화면에서 입력된 금액으로 세팅.
            if (result.totals.total == 0 || result.totals.total == null) {
                accountMap.put("total", total); // total 세팅.
            } else {
                accountMap.put("total", result.totals.total); // total 세팅.
            }

            accountMap.put("discount", result.totals.discount); // discount 세팅.
            accountMap.put("vat", result.totals.vat); // vat 세팅.
            accountMap.put("taxFree", result.totals.taxFree); // taxFree 세팅.
            accountMap.put("tax", result.totals.taxable); // tax 세팅.

            // 집계표 일자와 영수증 거래일자 미일치 시, 리턴.
            if (!receiptDate.equals(cell_date)) {
                Map<String, Object> error = new HashMap<>();
                error.put("code", 400);
                error.put("message",
                        "선택된 집계표 일자와 영수증 거래일자가 일치하지 않습니다.\n");
                error.put("[집계표]", cell_date);
                error.put("[거래일자]", date);

                return ResponseEntity.badRequest().body(error);
            }

            String approvalAmt = result.payment != null ? result.payment.approvalAmt : null;

            int iApprovalAmt = 0;
            if (approvalAmt != null && !approvalAmt.isBlank()) {
                String clean = approvalAmt.replaceAll("[^0-9]", ""); // 숫자만 남기기
                if (!clean.isEmpty()) {
                    iApprovalAmt = Integer.parseInt(clean);
                }
            }

            if ("cash".equals(result.payment != null ? result.payment.type : null)) {
                accountMap.put("payType", 1);
                accountMap.put("totalCash", iApprovalAmt);
                accountMap.put("totalCard", 0);
            } else {
                accountMap.put("payType", 2);
                accountMap.put("totalCard", iApprovalAmt);
                accountMap.put("totalCash", 0);
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
            accountMap.put("bizNo", normalizedBizNo);

            // 해당 거래처에 등록된 업체 유무를 확인.
            // tb_account_mapping 정보와 비교 후 type 값 세팅.
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
//                            accountMap.put("type", m.get("type"));
                            hasMapping = true;
                            break; // 매칭되면 더 안 돌게
                        }
                    } catch (IllegalArgumentException ex) {
                        // 형식 이상한 사업자번호는 그냥 무시
                        continue;
                    }
                }
            }

            // 📌 사업자 매핑 실패 시: 아래 동작(파일 저장, DB 저장)은 의미 없으므로 여기서 종료
            /*
             * if (!hasMapping) {
             * Map<String, Object> error = new HashMap<>();
             * error.put("code", 400);
             * error.put("message",
             * "해당 영수증의 사업자번호가 현재 선택한 거래처에 매핑되어 있지 않습니다.\n" +
             * "먼저 [거래처 연결]에서 사업자번호를 매핑해 주세요.");
             * error.put("bizNo", normalizedBizNo != null ? normalizedBizNo :
             * merchantBizNoRaw);
             * 
             * return ResponseEntity.badRequest().body(error);
             * }
             */
            // tb_account_purchase_tally_detail 저장 map
            List<Map<String, Object>> detailList = new ArrayList<>();

            for (Item r : result.items) {
                Map<String, Object> detailMap = new HashMap<String, Object>();
                detailMap.put("sale_id", saleId);
                detailMap.put("name", r.name);
                detailMap.put("qty", r.qty);
                detailMap.put("amount", r.amount);
                detailMap.put("unitPrice", r.unitPrice);
                detailMap.put("taxType", taxify(r.taxFlag));
                detailMap.put("itemType", classify(r.name, receipt_type));

                detailList.add(detailMap);
            }

            if (!accountMap.isEmpty()) {

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
                accountMap.put("receipt_image", resultPath);
            }

            int iResult = 0;

            // tall sheet 테이블 저장을 위한 값 세팅.
            String day = "day_" + dayStr;
            Object totalObj = accountMap.get("total");
            total = Integer.parseInt(totalObj.toString());

            accountMap.put(day, total);
            accountMap.put("count_year", yearStr);
            accountMap.put("count_month", monthStr);
            accountMap.put("year", yearStr);
            accountMap.put("month", monthStr);

            iResult += accountService.AccountCorporateCardPaymentSave(accountMap);
            iResult += accountService.AccountPurchaseSave(accountMap);
            iResult += accountService.TallySheetCorporateCardPaymentSave(accountMap);

            for (Map<String, Object> m : detailList) {
                iResult += accountService.AccountCorporateCardPaymentDetailLSave(m);
            }

            return ResponseEntity.ok(accountMap);

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
    private Map<String, Object> saveWithRequestParamsOnly(
            Map<String, Object> purchase,
            MultipartFile file) throws Exception {
        Map<String, Object> accountMap = new HashMap<>();

        int total = safeInt(purchase.get("total"));
        int type = safeInt(purchase.get("type"));
        int idx = safeInt(purchase.get("idx"));
        String accountId = (String) purchase.get("account_id");
        String saleIdParam = (String) purchase.get("sale_id");
        String receiptType = (String) purchase.get("receipt_type");
        String useName = (String) purchase.get("use_name");
        String cellDate = (String) purchase.get("cell_date");
        String cardBrand = (String) purchase.get("card_brand");
        String cardNo = (String) purchase.get("card_no");

        // cell_date 기반으로 저장할 연월 세팅(없으면 현재)
        LocalDate baseDate;
        try {
            baseDate = (cellDate != null && !cellDate.isBlank()) ? LocalDate.parse(cellDate) : LocalDate.now();
        } catch (Exception ignore) {
            baseDate = LocalDate.now();
        }

        // sale_id 생성
        LocalDateTime now = LocalDateTime.now();
        String saleId = (saleIdParam != null && !saleIdParam.isBlank())
                ? saleIdParam
                : now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        String yearStr = baseDate.format(DateTimeFormatter.ofPattern("yyyy"));
        String monthStr = baseDate.format(DateTimeFormatter.ofPattern("MM"));
        String dayStr = baseDate.format(DateTimeFormatter.ofPattern("D"));

        accountMap.put("account_id", accountId);
        accountMap.put("use_name", useName);
        accountMap.put("sale_id", saleId);
        accountMap.put("saleDate", baseDate);
        accountMap.put("payment_dt", baseDate);
        accountMap.put("type", type);
        accountMap.put("idx", idx);
        accountMap.put("receipt_type", receiptType);
        accountMap.put("cardBrand", cardBrand);
        accountMap.put("cardNo", cardNo);

        // 금액 관련 기본값
        accountMap.put("total", total);
        accountMap.put("discount", 0);
        accountMap.put("vat", 0);
        accountMap.put("taxFree", 0);
        accountMap.put("tax", 0);

        if (cardBrand == null || cardBrand.isBlank()) {
            accountMap.put("payType", 1);
            accountMap.put("totalCash", total);
            accountMap.put("totalCard", 0);
        } else {
            accountMap.put("payType", 2);
            accountMap.put("totalCard", total);
            accountMap.put("totalCash", 0);
        }

        // 이미지 저장 및 경로 세팅
        attachReceiptImage(accountMap, file, saleId);

        // 기본값 세팅
        String day = "day_" + dayStr;
        accountMap.put(day, total);
        accountMap.put("count_year", yearStr);
        accountMap.put("count_month", monthStr);
        accountMap.put("year", yearStr);
        accountMap.put("month", monthStr);

        // DB 저장 (detail 저장은 없음)
        int iResult = 0;
        iResult += accountService.AccountCorporateCardPaymentSave(accountMap);
        iResult += accountService.AccountPurchaseSave(accountMap);
        iResult += accountService.TallySheetCorporateCardPaymentSave(accountMap);

        return accountMap;
    }

    // ✅ fallback용 이미지 저장 로직 분리
    private void attachReceiptImage(Map<String, Object> accountMap, MultipartFile file, String saleId)
            throws Exception {
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + "receipt/" + saleId + "/";

        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath);

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile());
        String resultPath = "/image/" + "receipt" + "/" + saleId + "/" + uniqueFileName;
        accountMap.put("receipt_image", resultPath);
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
        return classify(itemName, null);
    }

    public static int classify(String itemName, String receiptType) {
        if (itemName == null || itemName.isEmpty()) {
            return defaultByReceiptType(receiptType);
        }

        // 1) 예외 케이스부터 검사
        for (String ex : FOOD_EXCEPTIONS) {
            if (itemName.contains(ex)) {
                return defaultByReceiptType(receiptType);
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

        // 4) 마트/편의점은 식재료로 기본 분류, 나머지는 기타
        return defaultByReceiptType(receiptType);
    }

    private static int defaultByReceiptType(String receiptType) {
        if (receiptType == null) return 3;
        switch (receiptType) {
            case "MART_ITEMIZED":
            case "CONVENIENCE":
                return 1; // 마트/편의점 → 식재료
            default:
                return 3; // 기타
        }
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
