package com.example.demo.controller;

import java.io.File;
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
            "김치", "고춧가루", "된장", "간장", "맛술", "참기름", "들기름", "식초", "소금", "설탕",
            "밀가루", "전분", "치즈", "버터", "우유", "생크림", "요거트",
            "사과", "바나나", "딸기", "배", "포도", "과일");

    // ✅ 소모품 키워드
    private static final List<String> SUPPLY_KEYWORDS = Arrays.asList(
            "칼", "식칼", "도마", "가위", "국자", "집게",
            "행주", "수건", "걸레", "키친타올", "종이타월", "휴지", "물티슈",
            "위생장갑", "고무장갑", "앞치마", "마스크",
            "종이컵", "비닐", "봉투", "랩", "호일", "포장",
            "세제", "주방세제", "락스", "세척제", "소독제",
            "수세미", "스펀지", "필터", "호스", "밥솥",
            "그릇", "식기", "접시", "공기", "쟁반", "바구니", "찬합", "반찬통", "용기",
            "냄비", "솥", "팬", "프라이팬", "볼", "채반", "소쿠리", "체", "카트", "서빙카",
            "다라이", "양푼", "스텐", "타공");

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
            @RequestParam(value = "receiptType", required = false) String receiptType,
            @RequestParam(value = "tallyType", required = false) String tallyType,
            @RequestParam(value = "use_name", required = false) String use_name,
            @RequestParam(value = "total", required = false) String total,
            @RequestParam(value = "cell_date", required = false) String cell_date) {

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
        purchase.put("tallyType", tallyType);
        purchase.put("use_name", use_name);
        purchase.put("total", total);
        purchase.put("cell_date", cell_date);

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
            // 집계표 법인카드는 type에 1002/1003 저장 타입이 들어오므로 receiptType을 OCR 파서 타입으로 사용
            String parserType = resolveParserType(type, receiptType);
            Future<BaseReceiptParser.ReceiptResult> parseFuture = executor
                    .submit(() -> HeadOfficeReceiptParserFactory.parse(doc, parserType));

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

            boolean isAccount = "account".equalsIgnoreCase(saveType);
            boolean isHeadoffice = "headoffice".equalsIgnoreCase(saveType);
            System.out.println("[OCR] saveType=" + saveType + ", isAccount=" + isAccount + ", isHeadoffice=" + isHeadoffice + ", total(user)=" + purchase.get("total") + ", tallyType=" + purchase.get("tallyType"));

            corporateCard.put("account_id", objectValue);
            corporateCard.put("year", year);
            corporateCard.put("month", month);
            corporateCard.put("receipt_type", receiptType);
            // 본사 법인카드 집계표 프로시저는 정수 type이 필요하다.
            corporateCard.put("type", resolveTallyType(type, isAccount));

            corporateCard.put("cardNo", cardNo);
            corporateCard.put("cardBrand", cardBrand);

            corporateCard.put("sale_id", targetSaleId);

            System.out.println("🏪 [OCR] use_name 세팅: " + result.merchant.name);
            corporateCard.put("use_name", result.merchant.name); // use_name 세팅.
            corporateCard.put("payment_dt", date); // payment_dt 세팅.

            if (true) {
                corporateCard.put("discount", result.totals.discount);
                corporateCard.put("vat", result.totals.vat);
                corporateCard.put("taxFree", result.totals.taxFree);
                corporateCard.put("tax", result.totals.taxable);

                String approvalAmt = result.payment != null ? result.payment.approvalAmt : null;
                String paymentCardBrand = result.payment != null ? result.payment.cardBrand : null;
                Integer parsedTotal = result.totals != null ? result.totals.total : null;
                int effectiveTotal = (parsedTotal == null || parsedTotal < 100) ? toInt(total) : parsedTotal;

                if (approvalAmt == null) {
                    corporateCard.put("total", effectiveTotal);
                    if (paymentCardBrand == null) {
                        corporateCard.put("payType", 1);
                        corporateCard.put("totalCash", effectiveTotal);
                        corporateCard.put("totalCard", 0);
                    } else {
                        corporateCard.put("payType", 2);
                        corporateCard.put("totalCard", effectiveTotal);
                        corporateCard.put("totalCash", 0);
                    }
                } else {
                    int iApprovalAmt = 0;
                    if (!approvalAmt.isBlank()) {
                        String clean = approvalAmt.replaceAll("[^0-9\\-]", "");
                        if (!clean.isEmpty())
                            iApprovalAmt = Integer.parseInt(clean);
                    }
                    if (iApprovalAmt < 100) {
                        iApprovalAmt = effectiveTotal;
                    }
                    corporateCard.put("total", iApprovalAmt);
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
            int forcedTallyType = resolveForcedTallyType(tallyType);
            int forcedItemType = resolveForcedItemType(forcedTallyType);

            for (Item r : result.items) {
                Map<String, Object> detailMap = new HashMap<String, Object>();
                detailMap.put("account_id", objectValue);
                detailMap.put("sale_id", targetSaleId);
                detailMap.put("name", r.name);
                detailMap.put("qty", r.qty);
                detailMap.put("amount", r.amount);
                detailMap.put("unitPrice", r.unitPrice);
                detailMap.put("taxType", taxify(r.taxFlag));
                detailMap.put("itemType", forcedItemType > 0 ? forcedItemType : classify(r.name));

                // 본사법인카드 특성상, 디테일에 있는 itemType(소모품, 식재료)에 따라
                // 집계표의 거래처(기타:type 1002, 기타비용:1003) 따로 저장이 되어야 함.
                // 따라서 payment_dt 가 집계표의 날짜와 매핑하기 때문에 detail 에도 적용해야 함.
                detailMap.put("payment_dt", date);

                // 집계표 1002/1003에서 넘어온 행 타입이 있으면 해당 타입으로 저장
                if (forcedTallyType == 1002 || forcedTallyType == 1003) {
                    detailMap.put("type", forcedTallyType);
                } else if (classify(r.name) == 3) {
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
                if (originalFileName == null || originalFileName.isBlank()) {
                    originalFileName = "receipt";
                }
                String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
                Path filePath = dirPath.resolve(uniqueFileName);

                file.transferTo(filePath.toFile()); // 파일 저장

                // 브라우저 접근용 경로 반환
                resultPath = "/image/" + folderValue + "/" + targetSaleId + "/" + uniqueFileName;
                corporateCard.put("receipt_image", resultPath);
            }

            // item을 파싱했지만 전체 amount 합계가 0이면 첫 번째 detail에만 total 채움
            if (!isAccount && !detailList.isEmpty()) {
                int totalAmount = detailList.stream()
                        .mapToInt(m -> toInt(m.get("amount")))
                        .sum();
                if (totalAmount == 0) {
                    // 집계표 경로: corporateCard에 total 없으므로 사용자 입력값 사용
                    int mainTotal = isHeadoffice ? toInt(purchase.get("total")) : toInt(corporateCard.get("total"));
                    if (mainTotal > 0) {
                        detailList.get(0).put("amount", mainTotal);
                    }
                }
                // detail taxType 기준으로 master 합계/과세/면세/부가세 금액을 계산
                applyDetailTaxSummaryToMaster(corporateCard, detailList, true);
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
                applyMasterZeroDefaults(corporateCard);
                iResult += accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
                // headoffice 경로: detail이 없으면 사용자 입력값으로 1건 생성
                if (isHeadoffice && detailList.isEmpty()) {
                    int userTotal = toInt(purchase.get("total"));
                    int typeInt = resolveForcedTallyType(purchase.get("tallyType"));
                    System.out.println("[FALLBACK] detailList 비어있음 → 사용자입력 사용: total=" + userTotal + ", tallyType=" + purchase.get("tallyType") + ", typeInt=" + typeInt);
                    if (typeInt == 0)
                        typeInt = toInt(corporateCard.get("type"));
                    if (typeInt == 0)
                        typeInt = 1002;
                    Map<String, Object> fallbackDetail = new HashMap<>();
                    fallbackDetail.put("sale_id", targetSaleId);
                    fallbackDetail.put("name", "");
                    fallbackDetail.put("qty", 0);
                    fallbackDetail.put("amount", userTotal);
                    fallbackDetail.put("unitPrice", 0);
                    fallbackDetail.put("taxType", 3);
                    int fallbackItemType = resolveForcedItemType(typeInt);
                    fallbackDetail.put("itemType", fallbackItemType > 0 ? fallbackItemType : typeInt == 1003 ? 1 : 2);
                    fallbackDetail.put("type", typeInt);
                    fallbackDetail.put("payment_dt", corporateCard.get("payment_dt"));
                    fallbackDetail.put("year", year);
                    fallbackDetail.put("month", month);
                    fallbackDetail.put("account_id", objectValue);
                    detailList.add(fallbackDetail);
                }
                // headoffice 경로: detail 합계를 master total/vat/taxFree에 반영
                if (isHeadoffice && !detailList.isEmpty()) {
                    applyDetailTaxSummaryToMaster(corporateCard, detailList, true);
                    applyMasterZeroDefaults(corporateCard);
                    accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
                }
                for (Map<String, Object> m : detailList) {
                    iResult += accountService.HeadOfficeCorporateCardPaymentDetailLSave(m);
                    iResult += accountService.TallySheetCorporateCardPaymentSaveV2(m);
                }

                Map<String, Object> detailQuery = new HashMap<>();
                detailQuery.put("sale_id", targetSaleId);
                detailQuery.put("account_id", objectValue);
                detailQuery.put("payment_dt", corporateCard.get("payment_dt"));
                List<Map<String, Object>> savedDetailList = accountService.HeadOfficeCorporateCardPaymentDetailList(detailQuery);
                applyDetailTaxSummaryToMaster(corporateCard, savedDetailList, true);
                applyMasterZeroDefaults(corporateCard);
                accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
                detailList = savedDetailList;
            }

            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("main", corporateCard);
            responseMap.put("item", detailList);
            return ResponseEntity.ok(responseMap);

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
        corporateCard.put("receipt_type", purchase.get("receipt_type"));
        // fallback에서도 집계표 프로시저용 type을 숫자로 보정한다.
        corporateCard.put("type", resolveTallyType(purchase.get("type"), isAccount));

        String cardNo = (String) purchase.get("cardNo");
        String cardBrand = (String) purchase.get("cardBrand");
        corporateCard.put("cardNo", cardNo);
        corporateCard.put("cardBrand", cardBrand);

        // 사용자 입력값 우선, 없으면 현재시각 기준
        String useName = (String) purchase.get("use_name");
        corporateCard.put("use_name", (useName != null && !useName.isBlank()) ? useName : null);

        String cellDate = (String) purchase.get("cell_date");
        if (cellDate != null && !cellDate.isBlank()) {
            try {
                LocalDate parsedDate = LocalDate.parse(cellDate);
                corporateCard.put("payment_dt", parsedDate);
                corporateCard.put("year", parsedDate.getYear());
                corporateCard.put("month", parsedDate.getMonthValue());
            } catch (Exception e) {
                corporateCard.put("payment_dt", now.toLocalDate());
                corporateCard.put("year", now.getYear());
                corporateCard.put("month", now.getMonthValue());
            }
        } else {
            corporateCard.put("payment_dt", now.toLocalDate());
            corporateCard.put("year", now.getYear());
            corporateCard.put("month", now.getMonthValue());
        }

        String totalStr = (String) purchase.get("total");
        int userTotal = 0;
        if (totalStr != null && !totalStr.isBlank()) {
            try {
                userTotal = Integer.parseInt(totalStr.replaceAll("[^0-9\\-]", ""));
            } catch (Exception ignored) {
            }
        }

        // 이미지 저장 및 경로 세팅
        attachReceiptImage(corporateCard, file, targetSaleId, folderValue);

        int iResult = 0;
        if (isAccount) {
            iResult += accountService.AccountCorporateCardPaymentSave(corporateCard);
            iResult += accountService.TallySheetCorporateCardPaymentSave(corporateCard);
        } else {
            // fallback detail 1건 생성 (taxType=3: 알수없음)
            Map<String, Object> detailMap = new HashMap<>();
            detailMap.put("sale_id", targetSaleId);
            detailMap.put("name", "");
            detailMap.put("qty", 0);
            detailMap.put("amount", userTotal);
            detailMap.put("unitPrice", 0);
            detailMap.put("taxType", 3);
            int typeInt = resolveForcedTallyType(purchase.get("tallyType"));
            if (typeInt == 0) {
                Object typeObj = corporateCard.get("type");
                typeInt = (typeObj instanceof Integer) ? (Integer) typeObj : 1002;
            }
            if (typeInt == 0)
                typeInt = 1002;
            int fallbackItemType = resolveForcedItemType(typeInt);
            detailMap.put("itemType", fallbackItemType > 0 ? fallbackItemType : typeInt == 1003 ? 1 : 2);
            detailMap.put("type", typeInt);
            detailMap.put("payment_dt", corporateCard.get("payment_dt"));
            detailMap.put("year", corporateCard.get("year"));
            detailMap.put("month", corporateCard.get("month"));
            detailMap.put("account_id", corporateCard.get("account_id"));

            // detail 합계를 master total에 반영 (taxType=3이므로 vat=0, taxFree=0)
            corporateCard.put("total", userTotal);
            corporateCard.put("vat", 0);
            corporateCard.put("taxFree", 0);
            corporateCard.put("tax", userTotal);

            applyMasterZeroDefaults(corporateCard);
            iResult += accountService.HeadOfficeCorporateCardPaymentSave(corporateCard);
            iResult += accountService.HeadOfficeCorporateCardPaymentDetailLSave(detailMap);
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
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "receipt";
        }
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile());
        String resultPath = "/image/" + folderValue + "/" + saleId + "/" + uniqueFileName;
        corporateCard.put("receipt_image", resultPath);
    }

    // OCR 파서 타입과 집계표 저장 타입을 분리
    private String resolveParserType(String type, String receiptType) {
        if (type != null && !type.isBlank() && !type.matches("^-?\\d+$")) {
            return type;
        }
        if (receiptType != null && !receiptType.isBlank() && !"UNKNOWN".equalsIgnoreCase(receiptType)) {
            return receiptType;
        }
        return "coupang";
    }

    // 집계표 1002/1003 행 타입
    private int resolveForcedTallyType(Object tallyType) {
        int typeInt = toInt(tallyType);
        return (typeInt == 1002 || typeInt == 1003) ? typeInt : 0;
    }

    // 집계표 행 타입별 detail 상품분류값
    private int resolveForcedItemType(int tallyType) {
        if (tallyType == 1002) {
            return 2;
        }
        if (tallyType == 1003) {
            return 1;
        }
        return 0;
    }

    // detail taxType 기준으로 master 세금 금액을 계산
    private void applyDetailTaxSummaryToMaster(Map<String, Object> corporateCard, List<Map<String, Object>> detailList,
            boolean updateTotal) {
        int detailTotal = 0;
        int vat = 0;
        int taxFree = 0;
        int tax = 0;

        for (Map<String, Object> detail : detailList) {
            int amount = toInt(detail.get("amount"));
            int taxType = toInt(detail.get("taxType"));
            detailTotal += amount;

            if (taxType == 1) { // 과세
                int itemVat = amount / 11;
                vat += itemVat;
                tax += amount - itemVat;
            } else if (taxType == 2) { // 면세
                taxFree += amount;
            }
            // taxType=3(알수없음)은 total에는 포함되지만 과세/면세/부가세에는 반영하지 않음
        }

        if (updateTotal) {
            corporateCard.put("total", detailTotal);
        }
        corporateCard.put("vat", vat);
        corporateCard.put("taxFree", taxFree);
        corporateCard.put("tax", tax);
    }

    // master 숫자 필드가 비어 있으면 DB에 빈칸 대신 0으로 저장
    private void applyMasterZeroDefaults(Map<String, Object> corporateCard) {
        putZeroIfBlank(corporateCard, "total");
        putZeroIfBlank(corporateCard, "discount");
        putZeroIfBlank(corporateCard, "vat");
        putZeroIfBlank(corporateCard, "taxFree");
        putZeroIfBlank(corporateCard, "tax");
        putZeroIfBlank(corporateCard, "totalCard");
        putZeroIfBlank(corporateCard, "totalCash");
    }

    // 숫자 컬럼 기본값 보정
    private void putZeroIfBlank(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            map.put(key, 0);
        }
    }

    private int toInt(Object val) {
        if (val == null)
            return 0;
        try {
            return Integer.parseInt(String.valueOf(val).replaceAll("[^0-9\\-]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 집계표 프로시저 p_type은 정수 컬럼이므로 문자열 타입값(CONVENIENCE/MART_ITEMIZED)은 1000으로 보정한다.
    private int resolveTallyType(Object rawType, boolean isAccount) {
        if (isAccount) {
            // account 경로는 별도 프로시저(TallySheetCorporateCardPaymentSave)를 사용하므로 값은 의미가 거의 없음.
            return 1000;
        }
        if (rawType == null)
            return 1000;
        String s = String.valueOf(rawType).trim();
        if (s.isEmpty())
            return 1000;
        if (!s.matches("^-?\\d+$"))
            return 1000;
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
     * OpenCV가 한글/특수문자 경로를 읽지 못하는 문제를 방지하기 위해
     * 임시파일명은 원본 확장자만 유지하고 나머지는 제거한다.
     */
    private File saveFile(MultipartFile file) {
        try {
            String ext = ".jpg";
            String originalName = file.getOriginalFilename();
            if (originalName != null && originalName.contains(".")) {
                String raw = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
                // 허용할 확장자만 사용, 나머지는 .jpg 기본값
                if (raw.matches("\\.(jpg|jpeg|png|pdf|tiff?|bmp|webp)")) {
                    ext = raw;
                }
            }
            File tempFile = File.createTempFile("upload_", ext);
            Files.write(tempFile.toPath(), file.getBytes());
            System.out.println("📂 업로드된 파일 저장 완료: " + tempFile.getAbsolutePath());
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패: " + e.getMessage(), e);
        }
    }
}
