package com.example.demo.controller;

import java.io.File;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.model.CardReceiptResponse;
import com.example.demo.parser.BaseReceiptParser;
import com.example.demo.parser.BaseReceiptParser.Item;
import com.example.demo.service.AccountService;
import com.example.demo.service.CardReceiptParseService;
import com.example.demo.utils.DateUtils;

@RestController
@RequestMapping("/card-receipt/")
public class CardReceiptController {

    @Autowired
    private CardReceiptParseService cardReceiptParseService;

    @Autowired
    private AccountService accountService;

    private final String uploadDir;

    @Autowired
    public CardReceiptController(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parse(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "objectValue", required = false) String objectValue,
            @RequestParam(value = "folderValue", required = false) String folderValue,
            @RequestParam(value = "cardNo", required = false) String cardNo,
            @RequestParam(value = "cardBrand", required = false) String cardBrand,
            @RequestParam(value = "saveType", required = false) String saveType
    ) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("file is empty");
            }
            if (folderValue == null || folderValue.isBlank()) folderValue = "card";

            // ✅ 0) 파일 저장
            String saleIdForPath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

            String staticPath = new File(uploadDir).getAbsolutePath();
            String basePath = staticPath + "/" + folderValue + "/" + saleIdForPath + "/";
            Path dirPath = Paths.get(basePath);
            Files.createDirectories(dirPath);

            String originalFileName = file.getOriginalFilename();
            String uniqueFileName = UUID.randomUUID() + "_" + (originalFileName == null ? "receipt" : originalFileName);
            Path savedPath = dirPath.resolve(uniqueFileName);

            try (var in = file.getInputStream()) {
                Files.copy(in, savedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String resultPath = "/image/" + folderValue + "/" + saleIdForPath + "/" + uniqueFileName;

            // ✅ 1)  파싱 (type 있으면 강제, 없으면 자동)
            CardReceiptResponse res = cardReceiptParseService.parseFile(savedPath.toFile(), type);
            BaseReceiptParser.ReceiptResult result = res.result;

            if (result == null || result.meta == null || result.meta.saleDate == null) {
                return ResponseEntity.badRequest().body("❌ 영수증 날짜를 인식하지 못했습니다.");
            }

            // ✅ 2) saleId 생성(영수증 날짜 기반)
            LocalDate date = DateUtils.parseFlexibleDate(result.meta.saleDate);
            LocalDateTime dateTime = LocalDateTime.of(date, LocalTime.now());
            String saleId = dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            
            // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
            int year = date.getYear();        // 2026
            int month = date.getMonthValue(); // 1~12
            
            // ✅ 3) DB 저장 payload 만들기
            Map<String, Object> corporateCard = new HashMap<>();

            boolean isAccount = "account".equalsIgnoreCase(saveType); // ✅ NPE 방지
            if (isAccount) {
                corporateCard.put("account_id", objectValue);
                corporateCard.put("year", year);
                corporateCard.put("month", month);
            } else {
            	corporateCard.put("account_id", objectValue);
                corporateCard.put("year", year);
                corporateCard.put("month", month);
            }

            corporateCard.put("cardNo", cardNo);
            corporateCard.put("cardBrand", cardBrand);
            corporateCard.put("sale_id", saleId);
            corporateCard.put("use_name", result.merchant != null ? result.merchant.name : null);
            corporateCard.put("bizNo", result.merchant != null ? result.merchant.bizNo : null);
            corporateCard.put("payment_dt", date);
            corporateCard.put("total", result.totals != null ? result.totals.total : null);
            corporateCard.put("discount", result.totals != null ? result.totals.discount : null);
            corporateCard.put("vat", result.totals != null ? result.totals.vat : null);
            corporateCard.put("taxFree", result.totals != null ? result.totals.taxFree : null);
            corporateCard.put("tax", result.totals != null ? result.totals.taxable : null);
            corporateCard.put("receipt_image", resultPath);

            // detailList
            List<Map<String, Object>> detailList = new ArrayList<>();
            if (result.items != null) {
                for (Item it : result.items) {
                    Map<String, Object> detailMap = new HashMap<>();
                    detailMap.put("sale_id", saleId);
                    detailMap.put("name", it.name);
                    detailMap.put("qty", it.qty);
                    detailMap.put("amount", it.amount);
                    detailMap.put("unitPrice", it.unitPrice);
                    detailMap.put("taxType", taxify(it.taxFlag));
                    detailMap.put("itemType", classify(it.name));
                    detailList.add(detailMap);
                }
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
                iResult += accountService.TallySheetCorporateCardPaymentSaveV2(corporateCard);
                for (Map<String, Object> m : detailList) {
                    iResult += accountService.HeadOfficeCorporateCardPaymentDetailLSave(m);
                }
            }

            // 필요하면 res.type/confidence도 같이 반환 가능
            //corporateCard.put("parsed_type", res.type.name());
            //corporateCard.put("parsed_confidence", res.confidence);

            return ResponseEntity.ok(corporateCard);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("parse failed: " + e.getMessage());
        }
    }

    // ---------------- 너 기존 classify/taxify 그대로 붙여넣기 ----------------
    private static final String VAT = "과세";
    private static final String TAX_FREE = "면세";

    public static int taxify(String taxFlag) {
        if (taxFlag == null || taxFlag.isEmpty()) return 3;
        if (taxFlag.equals(VAT)) return 1;
        if (taxFlag.equals(TAX_FREE)) return 2;
        return 3;
    }

    // 아래 키워드/예외 리스트는 네 기존 코드 그대로 복붙하면 됨
    private static final List<String> FOOD_KEYWORDS = Arrays.asList("쌀","현미","찹쌀","보리","감자","고구마","양파","당근","마늘","생강","무","배추","파","버섯","양배추",
            "고기","쇠고기","소고기","돼지고기","돈육","닭","계육","정육","삼겹살","계란","달걀","두부","콩","콩나물","숙주",
            "생선","연어","참치","고등어","오징어","새우","조개","해물","김치","고춧가루","된장","간장","맛술","참기름","식초","소금","설탕",
            "밀가루","전분","치즈","버터","우유","생크림","요거트","사과","바나나","딸기","배","포도","과일");

    private static final List<String> SUPPLY_KEYWORDS = Arrays.asList("칼","식칼","도마","가위","국자","집게",
            "행주","수건","걸레","키친타올","종이타월","휴지","물티슈",
            "위생장갑","고무장갑","앞치마","마스크",
            "종이컵","비닐","봉투","랩","호일","포장",
            "세제","주방세제","락스","세척제","소독제",
            "수세미","스펀지","필터","호스");

    private static final List<String> FOOD_EXCEPTIONS = Arrays.asList("칼국수", "가위살");

    public static int classify(String itemName) {
        if (itemName == null || itemName.isEmpty()) return 3;

        for (String ex : FOOD_EXCEPTIONS) {
            if (itemName.contains(ex)) return 3; // ✅ 네 기존 로직 유지
        }
        for (String keyword : FOOD_KEYWORDS) {
            if (itemName.contains(keyword)) return 1;
        }
        for (String keyword : SUPPLY_KEYWORDS) {
            if (itemName.contains(keyword)) return 2;
        }
        return 3;
    }
}
