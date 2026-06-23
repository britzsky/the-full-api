package com.example.demo.controller;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.WebConfig;
import com.example.demo.service.HeadOfficeService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@RestController
public class HeadOfficeController {
	
	private final HeadOfficeService headOfficeService;
	private final String uploadDir;
	private static final String DOC_KIND_DRAFT = "draft";
	private static final String DOC_KIND_EXPENDABLE = "expendable";
	private static final String DOC_KIND_PAYMENT = "payment";
	// TODO: 소모품 구매 품의서 결재자 고정 ID는 운영 정책에 맞춰 변경 가능
	private static final String EXPENDABLE_FIXED_PAYER_USER_ID = "iy1";
	// 문서 공통 첨부파일 업로드 제한
	private static final int MAX_HEADOFFICE_DOCUMENT_FILE_COUNT = 10;
	// 평가 첨부파일 업로드 제한 (공지/교육과 별도 관리)
	private static final int MAX_EVALUATION_FILE_COUNT = 20;
	// 문서 공통 첨부파일 허용 확장자(이미지 + PDF + 엑셀)
	private static final Set<String> HEADOFFICE_DOCUMENT_ALLOWED_EXTENSIONS = new HashSet<>(
		Arrays.asList(
			"jpg",
			"jpeg",
			"png",
			"gif",
			"bmp",
			"webp",
			"svg",
			"pdf",
			"xls",
			"xlsx"
		)
	);
	
    @Autowired
    public HeadOfficeController(
		HeadOfficeService headOfficeService,
		WebConfig webConfig,
		@Value("${file.upload-dir}") String uploadDir
	) {
    	this.headOfficeService = headOfficeService;
    	this.uploadDir = uploadDir;
    }
    
    /* 
	 * part		: 본사
     * method 	: WeekMenuSave
     * comment 	: 본사 -> 캘린더 저장
     */
	@PostMapping("HeadOffice/WeekMenuSave")
	public String WeekMenuSave(@RequestBody Map<String, Object> paramMap) {
		
		int iResult = 0;
		iResult = headOfficeService.WeekMenuSave(paramMap);
		
		JsonObject obj =new JsonObject();
    	
    	if(iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
	}
	
	/* 
	 * part		: 본사
     * method 	: WeekMenuList
     * comment 	: 본사 -> 식단표 캘린더 조회
     */
	@GetMapping("HeadOffice/WeekMenuList")
	public String WeekMenuList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.WeekMenuList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: WeekMenuTodayList
     * comment 	: 본사 -> 식단표 당일 조회
     */
	@GetMapping("HeadOffice/WeekMenuTodayList")
	public String WeekMenuTodayList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.WeekMenuTodayList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: EventList
     * comment 	: 본사 -> 캘린더 조회2
     */
	@GetMapping("HeadOffice/EventList")
	public String EventList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EventList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: EventSave
     * comment 	: 본사 -> 캘린더 저장2
     */
	@PostMapping("HeadOffice/EventSave")
	public String EventSave(@RequestBody Map<String, Object> paramMap) {
		
		int iResult = 0;
		iResult = headOfficeService.EventSave(paramMap);
		
		JsonObject obj =new JsonObject();
    	
    	if(iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
	}
	
	/* 
	 * part		: 본사
     * method 	: PeopleCountingList
     * comment 	: 본사 -> 관리표 -> 인원증감 조회
     */
	@GetMapping("HeadOffice/PeopleCountingList")
	public String PeopleCountingList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.PeopleCountingList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: ProfitLossTableSave
     * comment 	: 본사 -> 관리표 -> 손익표 저장
     */
	@PostMapping("HeadOffice/ProfitLossTableSave")
	public String ProfitLossTableSave(@RequestBody Map<String, Object> payload) {

		// payload에서 rows만 꺼냄
	    List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");

		// 인건비 변경 히스토리 저장
		for (Map<String, Object> paramMap : rows) {
			if (!paramMap.containsKey("person_cost")) continue;	// 인건비 미포함 행 제외

			Map<String, Object> existing = headOfficeService.getProfitLossPersonCost(paramMap);	// DB 기존 인건비 조회
			Object orgPriceObj = (existing != null) ? existing.get("person_cost") : null;
			Object newPriceObj = paramMap.get("person_cost");

			long orgPrice = (orgPriceObj != null) ? ((Number) orgPriceObj).longValue() : 0L;	// 기존 금액
			long newPrice = (newPriceObj != null) ? ((Number) newPriceObj).longValue() : 0L;	// 변경 금액

			if (orgPrice != newPrice) {	// 금액 변경 시 히스토리 저장
				Map<String, Object> histParam = new java.util.HashMap<>();
				histParam.put("account_id", paramMap.get("account_id"));	// 업장 아이디
				histParam.put("year", paramMap.get("year"));				// 년도
				histParam.put("month", paramMap.get("month"));				// 월
				histParam.put("mod_id", paramMap.get("update_id"));			// 수정자 아이디
				histParam.put("org_price", orgPrice);						// 기존 인건비
				histParam.put("mod_price", newPrice);						// 변경 인건비
				headOfficeService.savePersonCostHistory(histParam);
			}
		}

		int iResult = 0;

		for (Map<String, Object> paramMap : rows) {
			iResult += headOfficeService.ProfitLossTableSave(paramMap);
        }

		if (iResult > 0) {
			for (Map<String, Object> paramMap : rows) {
				iResult += headOfficeService.processProfitLoss(paramMap);
	        }
		}

		JsonObject obj =new JsonObject();

    	if(iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
    	}

    	return obj.toString();
	}
	
	/*
	 * part		: 본사
     * method 	: PersonCostExcelSave
     * comment 	: 본사 -> 관리표 -> 인건비 엑셀 업로드 저장
     */
	@PostMapping("HeadOffice/PersonCostExcelSave")
	public String PersonCostExcelSave(@RequestBody Map<String, Object> payload) {

		// payload에서 rows만 꺼냄 (account_id, year, month, person_cost, update_id 포함)
		List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");

		int iResult = 0;
		List<String> savedIds   = new java.util.ArrayList<>();	// 등록 성공한 account_id 목록
		List<String> skippedIds = new java.util.ArrayList<>();	// 기존 데이터 존재로 미등록된 account_id 목록
		List<Map<String, Object>> savedRows = new java.util.ArrayList<>();	// 재계산 대상 rows

		// 거래처별 인건비 저장 (기존 인건비가 0이 아니면 스킵)
		for (Map<String, Object> paramMap : rows) {

			Map<String, Object> existing = headOfficeService.getProfitLossPersonCost(paramMap);	// DB 기존 인건비 조회
			Object orgPriceObj = (existing != null) ? existing.get("person_cost") : null;
			Object newPriceObj = paramMap.get("person_cost");

			long orgPrice = (orgPriceObj != null) ? ((Number) orgPriceObj).longValue() : 0L;	// 기존 금액
			long newPrice = (newPriceObj != null) ? ((Number) newPriceObj).longValue() : 0L;	// 변경 금액

			String accountId = String.valueOf(paramMap.get("account_id"));

			// 기존 인건비가 0이 아닌 경우 → 덮어쓰지 않고 스킵
			if (orgPrice != 0L) {
				skippedIds.add(accountId);
				continue;
			}

			if (orgPrice != newPrice) {	// 금액 변경 시에만 히스토리 저장
				Map<String, Object> histParam = new java.util.HashMap<>();
				histParam.put("account_id", paramMap.get("account_id"));	// 업장 아이디
				histParam.put("year", paramMap.get("year"));				// 년도
				histParam.put("month", paramMap.get("month"));				// 월
				histParam.put("mod_id", paramMap.get("update_id"));			// 수정자 아이디
				histParam.put("org_price", orgPrice);						// 기존 인건비
				histParam.put("mod_price", newPrice);						// 변경 인건비
				headOfficeService.savePersonCostHistory(histParam);
			}

			// person_cost INSERT (행 없으면 INSERT, 있으면 person_cost만 덮어씀)
			iResult += headOfficeService.PersonCostExcelSave(paramMap);
			savedIds.add(accountId);
			savedRows.add(paramMap);
		}

		// 등록 성공 시 손익표 합계·비율 재계산 (손익표 프로시저 + 예산 프로시저)
		if (iResult > 0) {
			for (Map<String, Object> paramMap : savedRows) {
				iResult += headOfficeService.processProfitLoss(paramMap);
			}
		}

		// 등록된 account_id 목록, 미등록 account_id 목록을 함께 반환
		JsonArray savedArr = new JsonArray();
		savedIds.forEach(savedArr::add);
		JsonArray skippedArr = new JsonArray();
		skippedIds.forEach(skippedArr::add);

		JsonObject obj = new JsonObject();
		obj.addProperty("code", savedIds.isEmpty() && skippedIds.isEmpty() ? 400 : 200);
		obj.addProperty("message", savedIds.isEmpty() && skippedIds.isEmpty() ? "실패" : "성공");
		obj.add("saved", savedArr);
		obj.add("skipped", skippedArr);

		return obj.toString();
	}

	/*
	 * part		: 본사
     * method 	: ProfitLossTableList
     * comment 	: 본사 -> 관리표 -> 손익표 조회
     */
	@GetMapping("HeadOffice/ProfitLossTableList")
	public String ProfitLossTableList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ProfitLossTableList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
	 * method 	: ExcelDownProfitLossTableList
     * comment 	: 본사 -> 관리표 -> 손익표 엑셀다운
     */
	@GetMapping("HeadOffice/ExcelDownProfitLossTableList")
	public String ExcelDownProfitLossTableList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ExcelDownProfitLossTableList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
	 * method 	: ExcelDownMonthProfitLossTableList
     * comment 	: 본사 -> 관리표 -> 손익표 엑셀다운
     */
	@GetMapping("HeadOffice/ExcelDownMonthProfitLossTableList")
	public String ExcelDownMonthProfitLossTableList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ExcelDownMonthProfitLossTableList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: AccountManagermentTableList
     * comment 	: 거래처 -> 관리표 조회
     */
    @GetMapping("HeadOffice/AccountManagermentTableList")
    public String AccountManagermentTableList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = headOfficeService.AccountManagermentTableList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 본사
     * method 	: AccountMappingPurchaseList
     * comment 	: 본사 -> 관리표 -> 거래처 통계
     */
	@GetMapping("HeadOffice/AccountMappingPurchaseList")
	public String AccountMappingPurchaseList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.AccountMappingPurchaseList(paramMap);
		
		return new Gson().toJson(resultList);
	}

	/* 
	 * part		: 본사
     * method 	: AccountMappingPurchaseDetailList
     * comment 	: 본사 -> 관리표 -> 거래처 통계(업장별 상세)
     */
	@GetMapping("HeadOffice/AccountMappingPurchaseDetailList")
	public String AccountMappingPurchaseDetailList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.AccountMappingPurchaseDetailList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: HeadOfficeElectronicPaymentTypeList
     * comment 	: 본사 -> 전자결재 관리 -> 전자결재 타입 리스트 조회
     */
	@GetMapping("HeadOffice/HeadOfficeElectronicPaymentTypeList")
	public String HeadOfficeElectronicPaymentTypeList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeElectronicPaymentTypeList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: HeadOfficeElectronicPaymentList
     * comment 	: 본사 -> 전자결재 관리 -> 소모품 구매 품의서 조회
     */
	@GetMapping("HeadOffice/HeadOfficeElectronicPaymentList")
	public String HeadOfficePurchaseRequestList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeElectronicPaymentList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentSave
     * comment 	: 본사 -> 전자결재 관리 -> 결재 문서 메인 저장
     */
	@PostMapping("HeadOffice/ElectronicPaymentSave")
	public String ElectronicPaymentSave(@RequestBody Map<String, Object> payload) {

		Map<String, Object> main = getMainPayload(payload);
		List<Map<String, Object>> itemList = getItemPayloadList(payload);
		int iResult = 0;

		if (!main.isEmpty()) {
			// 전자결재 메인 테이블 결재 상태값을 저장 형식에 맞게 정규화한다.
			main.put("charge_sign", normalizeStatusText(main.get("charge_sign"), "4"));
			main.put("tm_sign", normalizeStatusText(main.get("tm_sign"), ""));
			main.put("payer_sign", normalizeStatusText(main.get("payer_sign"), ""));
			main.put("ceo_sign", normalizeStatusText(main.get("ceo_sign"), ""));

			// 작성자(user_id)와 등록자(reg_user_id)는 항상 동일하게 저장
			String writerUserId = String.valueOf(main.getOrDefault("user_id", "")).trim();
			main.put("user_id", writerUserId);
			main.put("reg_user_id", writerUserId);

			// 시행일자는 메인 테이블(start_dt)에 통일 저장한다.
			// - 없으면 draft_dt를 fallback으로 사용
			String startDtText = asText(main.get("start_dt"));
			if (startDtText.isEmpty()) startDtText = asText(main.get("draft_dt"));
			main.put("start_dt", startDtText);

			String docTypeText = String.valueOf(main.getOrDefault("doc_type", "")).trim();
			Map<String, String> docKindByType = buildDocKindByTypeMap();
			String docKind = resolveDocKind(docTypeText, docKindByType);
			// 소모품 구매 품의서는 결재자 1명 고정 정책을 강제한다.
			if (DOC_KIND_EXPENDABLE.equals(docKind)) {
				main.put("tm_user", "");
				main.put("ceo_user", "");
				main.put("payer_user", EXPENDABLE_FIXED_PAYER_USER_ID);
			}

			if (isBlank(main.get("status"))) {
				main.put("status", resolveDocumentStatus(main));
			}

			// 1) 전자결재 메인 저장
			iResult += headOfficeService.ElectronicPaymentSave(main);

			// 2) 본문 저장
			String paymentIdText = String.valueOf(main.getOrDefault("payment_id", "")).trim();
			boolean isDraftDoc = DOC_KIND_DRAFT.equals(docKind);
			boolean isExpenseDoc = DOC_KIND_PAYMENT.equals(docKind);

			if (!paymentIdText.isEmpty()) {
				Map<String, Object> deleteParam = new HashMap<>();
				deleteParam.put("payment_id", paymentIdText);

				if (isDraftDoc) {
					// 기안서 타입: draft 테이블 1건 본문 저장
					headOfficeService.HeadOfficePurchaseRequestDeleteByPaymentId(deleteParam);
					headOfficeService.HeadOfficeDraftDeleteByPaymentId(deleteParam);

					// 프론트 item 1행을 기안서 본문 컬럼(title/details/note)으로 변환
					Map<String, Object> draftBody = buildDraftPayload(itemList, main, paymentIdText);
					iResult += headOfficeService.HeadOfficeDraftSave(draftBody);
				} else if (isExpenseDoc) {
					// 지출결의서 타입: 지출결의서 테이블 단건 본문 저장
					headOfficeService.HeadOfficeDraftDeleteByPaymentId(deleteParam);
					headOfficeService.HeadOfficePurchaseRequestDeleteByPaymentId(deleteParam);
					headOfficeService.HeadOfficePaymentDocDeleteByPaymentId(deleteParam);

					// 지출결의서는 payment_id 1건에 상세(item_name) 여러 행을 저장한다.
					List<Map<String, Object>> paymentDocRows =
						buildPaymentDocRows(itemList, main, paymentIdText);
					for (Map<String, Object> paymentDocRow : paymentDocRows) {
						iResult += headOfficeService.HeadOfficePaymentDocSave(paymentDocRow);
					}
				} else if (itemList != null) {
					// 소모품 구매 품의서 타입(또는 기타 품목형 문서): purchase_request 품목 저장
					headOfficeService.HeadOfficeDraftDeleteByPaymentId(deleteParam);
					headOfficeService.HeadOfficePaymentDocDeleteByPaymentId(deleteParam);
					headOfficeService.HeadOfficePurchaseRequestDeleteByPaymentId(deleteParam);

					List<Map<String, Object>> rowsToSave = new ArrayList<>();

					for (Map<String, Object> item : itemList) {
						if (item == null) continue;

						// 품목명이 비어있는 행은 저장 제외
						String itemName = String.valueOf(item.getOrDefault("item_name", "")).trim();
						if (itemName.isEmpty()) continue;

					Map<String, Object> row = new HashMap<>(item);
					row.remove("idx"); // idx는 AI PK이므로 전달값 제거
					row.put("payment_id", paymentIdText);
					row.put("payment_note", asText(item.get("payment_note")));
					row.put("use_name", asText(item.get("use_name")));
					row.put("buy_yn", normalizeYn(item.get("buy_yn")));
					rowsToSave.add(row);
					}

					// 품목은 개별 insert 대신 배치 insert 1회로 저장해 DB 왕복을 줄인다.
					if (!rowsToSave.isEmpty()) {
						Map<String, Object> bulkParam = new HashMap<>();
						bulkParam.put("items", rowsToSave);
						iResult += headOfficeService.HeadOfficePurchaseRequestBulkSave(bulkParam);
					}
				}
			}
		}

		JsonObject obj = new JsonObject();

		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}

		return obj.toString();
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentManageList
     * comment 	: 본사 -> 전자결재 관리 -> 내 문서/결재대상 목록 조회
     */
	@GetMapping("HeadOffice/ElectronicPaymentManageList")
	public String ElectronicPaymentManageList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ElectronicPaymentManageList(paramMap);
		return new Gson().toJson(resultList);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentManageDetail
     * comment 	: 본사 -> 전자결재 관리 -> 문서 상세(메인/품목) 조회
     */
	@GetMapping("HeadOffice/ElectronicPaymentManageDetail")
	public String ElectronicPaymentManageDetail(@RequestParam Map<String, Object> paramMap) {
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("main", headOfficeService.ElectronicPaymentManageMain(paramMap));
		resultMap.put("items", headOfficeService.ElectronicPaymentManageItems(paramMap));
		resultMap.put("files", headOfficeService.ElectronicPaymentFileList(paramMap));
		return new Gson().toJson(resultMap);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentDocumentFilesUpload
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 이미지 저장
     */
	@PostMapping("HeadOffice/ElectronicPaymentDocumentFilesUpload")
	public String ElectronicPaymentDocumentFilesUpload(
		@RequestParam("payment_id") String paymentId,
		@RequestParam("files") MultipartFile[] files
	) {
		return uploadHeadOfficeDocumentFiles(paymentId, files);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentFilesUpload
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 이미지 저장(레거시 호환)
     */
	@PostMapping("HeadOffice/ElectronicPaymentFilesUpload")
	public String ElectronicPaymentFilesUpload(
		@RequestParam("payment_id") String paymentId,
		@RequestParam("files") MultipartFile[] files
	) {
		return uploadHeadOfficeDocumentFiles(paymentId, files);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentDocumentFileView
     * comment 	: 본사 -> 전자결재 관리 -> 결재문서 첨부파일 미리보기/다운로드용 파일 스트림 조회
     */
	@GetMapping("HeadOffice/ElectronicPaymentDocumentFileView")
	public ResponseEntity<?> ElectronicPaymentDocumentFileView(@RequestParam Map<String, Object> paramMap) {
		try {
			String paymentIdText = asText(paramMap.get("payment_id"));
			String imageOrderText = asText(paramMap.get("image_order"));
			String userIdText = asText(paramMap.get("user_id"));
			if (paymentIdText.isEmpty() || imageOrderText.isEmpty() || userIdText.isEmpty()) {
				return ResponseEntity.badRequest().build();
			}

			Map<String, Object> fileListParam = new HashMap<>();
			fileListParam.put("payment_id", paymentIdText);
			fileListParam.put("user_id", userIdText);
			List<Map<String, Object>> fileList = headOfficeService.ElectronicPaymentFileList(fileListParam);
			Map<String, Object> matchedFile = null;
			for (Map<String, Object> fileRow : fileList) {
				if (parseIntOrZero(fileRow.get("image_order")) == parseIntOrZero(imageOrderText)) {
					matchedFile = fileRow;
					break;
				}
			}
			if (matchedFile == null) {
				return ResponseEntity.notFound().build();
			}

			String imagePathText = asText(matchedFile.get("image_path"));
			Path filePath = resolveElectronicPaymentStoredFilePath(imagePathText);
			if (filePath == null || !Files.exists(filePath)) {
				return ResponseEntity.notFound().build();
			}

			Resource resource = new UrlResource(filePath.toUri());
			if (!resource.exists()) {
				return ResponseEntity.notFound().build();
			}

			String detectedContentType = Files.probeContentType(filePath);
			MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
			if (detectedContentType != null && !detectedContentType.trim().isEmpty()) {
				mediaType = MediaType.parseMediaType(detectedContentType);
			}

			return ResponseEntity.ok()
				.contentType(mediaType)
				.body(resource);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(e.getClass().getName() + ": " + e.getMessage());
		}
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentDocumentFileDelete
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 이미지 삭제
     */
	@DeleteMapping("HeadOffice/ElectronicPaymentDocumentFileDelete")
	public String ElectronicPaymentDocumentFileDelete(@RequestParam Map<String, Object> paramMap) {
		return deleteHeadOfficeDocumentFile(paramMap);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentFileDelete
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 이미지 삭제(레거시 호환)
     */
	@DeleteMapping("HeadOffice/ElectronicPaymentFileDelete")
	public String ElectronicPaymentFileDelete(@RequestParam Map<String, Object> paramMap) {
		return deleteHeadOfficeDocumentFile(paramMap);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentManageSignSave
     * comment 	: 본사 -> 전자결재 관리 -> 팀장/결재자 결재/반려 저장
     */
	@PostMapping("HeadOffice/ElectronicPaymentManageSignSave")
	public String ElectronicPaymentManageSignSave(@RequestBody Map<String, Object> payload) {
		int iResult = headOfficeService.ElectronicPaymentManageSignSave(payload);

		JsonObject obj = new JsonObject();
		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}

		return obj.toString();
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentItemBuyYnSave
     * comment 	: 본사 -> 전자결재 관리 -> 소모품 구매여부 저장
     */
	@PostMapping("HeadOffice/ElectronicPaymentItemBuyYnSave")
	public String ElectronicPaymentItemBuyYnSave(@RequestBody Map<String, Object> payload) {
		int iResult = headOfficeService.ElectronicPaymentItemBuyYnSave(payload);

		JsonObject obj = new JsonObject();
		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}

		return obj.toString();
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentNotificationList
     * comment 	: 네비바 -> 전자결재 알림 목록
     */
	@GetMapping("HeadOffice/ElectronicPaymentNotificationList")
	public String ElectronicPaymentNotificationList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ElectronicPaymentNotificationList(paramMap);
		return new Gson().toJson(resultList);
	}

	/* 
	 * part		: 본사
     * method 	: ElectronicPaymentNotificationReadSave
     * comment 	: 네비바 -> 전자결재 승인/반려 알림 읽음 처리
     */
	@PostMapping("HeadOffice/ElectronicPaymentNotificationReadSave")
	public String ElectronicPaymentNotificationReadSave(@RequestBody Map<String, Object> payload) {
		int iResult = headOfficeService.ElectronicPaymentNotificationReadSave(payload);

		JsonObject obj = new JsonObject();
		if (iResult >= 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}

		return obj.toString();
	}

	/* 
	 * part		: 본사
     * method 	: HeadOfficePurchaseRequestSave
     * comment 	: 본사 -> 전자결재 관리 -> 소모품 구매 품의서 저장
     */
	@PostMapping("HeadOffice/HeadOfficePurchaseRequestSave")
	public String HeadOfficePurchaseRequestSave(@RequestBody Map<String, Object> payload) {
		
	    List<Map<String, Object>> mainList = (List<Map<String, Object>>) payload.get("main");
	    List<Map<String, Object>> itemList = (List<Map<String, Object>>) payload.get("item");
	    
		int iResult = 0;
		
		if (mainList != null) {
			for (Map<String, Object> paramMap : mainList) {
				iResult += headOfficeService.HeadOfficeElectronicPaymentSave(paramMap);
	        }
		}
		
		if (itemList != null) {
			for (Map<String, Object> paramMap : itemList) {
				iResult += headOfficeService.HeadOfficePurchaseRequestSave(paramMap);
	        }
		}
		
		JsonObject obj =new JsonObject();
    	
    	if(iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
	}
	
	/* 
	 * part		: 본사
     * method 	: HeadOfficeDepartmentList
     * comment 	: 본사 -> 전자결재 관리 -> 부서목록 조회
     */
	@GetMapping("HeadOffice/HeadOfficeDepartmentList")
	public String HeadOfficeDepartmentList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeDepartmentList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/* 
	 * part		: 본사
     * method 	: HeadOfficeCompanyUserTree
     * comment 	: 본사 -> 전자결재 관리 -> 부서목록 조회
     */
	@GetMapping("HeadOffice/HeadOfficeCompanyUserTree")
	public String HeadOfficeCompanyUserTree(@RequestParam Map<String, Object> paramMap) {

	    // 1) 부서 목록
	    List<Map<String, Object>> deptList = headOfficeService.HeadOfficeDepartmentList(paramMap);

	    // 2) 부서마다 users 조회해서 붙이기
	    for (Map<String, Object> dept : deptList) {
	        // dept에서 부서키 꺼내기 (컬럼명은 실제 DB 컬럼명에 맞추세요)
	        Object department = dept.get("department"); // 또는 "dept_code", "department_id" 등

	        Map<String, Object> userParam = new HashMap<>(paramMap);
	        userParam.put("department", department);

	        List<Map<String, Object>> users = headOfficeService.HeadOfficeUserListByDepartment(userParam);

	        dept.put("users", users);
	    }

	    return new Gson().toJson(deptList);
	}

	/*
	 * part		: 본사
     * method 	: HeadOfficeUserListByDepartment
     * comment 	: 본사 -> 전자결재 관리 -> 부서목록 선택 시, 부서 직원 조회
     */
	@GetMapping("HeadOffice/HeadOfficeUserListByDepartment")
	public String HeadOfficeUserListByDepartment(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeUserListByDepartment(paramMap);

		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 본사
     * method 	: HeadOfficeScheduleList
     * comment 	: 본사 -> 일정관리 -> 운영팀/영업팀/급식사업부 통합 조회
     */
	@GetMapping("HeadOffice/HeadOfficeScheduleList")
	public String HeadOfficeScheduleList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeScheduleList(paramMap);
		for (Map<String, Object> a : resultList) {
			String userIdsStr = a.get("user_ids") != null ? a.get("user_ids").toString().trim() : "";
			if (!userIdsStr.isEmpty()) {
				String[] userIdsArr = userIdsStr.split(",");
				String userNames = headOfficeService.SelectMultiUserNames(userIdsArr);
				a.put("user_names", userNames != null ? userNames : "");
			} else {
				Object userName = a.get("user_name");
				a.put("user_names", userName != null ? userName.toString() : "");
			}
		}
		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 본사
	 * method 	: NoticeList
	 * comment 	: 본사 -> 공지사항 -> 목록 조회
	 */
	@GetMapping("HeadOffice/NoticeList")
	public String NoticeList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.NoticeList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 본사
	 * method 	: NoticeDetail
	 * comment 	: 본사 -> 공지사항 -> 상세 조회
	 */
	@GetMapping("HeadOffice/NoticeDetail")
	public String NoticeDetail(@RequestParam Map<String, Object> paramMap) {
		Map<String, Object> result = headOfficeService.NoticeDetail(paramMap);
		return new Gson().toJson(result);
	}

	/*
	 * part		: 본사
	 * method 	: NoticeSave
	 * comment 	: 본사 -> 공지사항 -> 등록/수정 (upsert)
	 */
	@PostMapping("HeadOffice/NoticeSave")
	public String NoticeSave(@RequestBody Map<String, Object> paramMap) {
		int iResult = headOfficeService.NoticeSave(paramMap);

		JsonObject obj = new JsonObject();
		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
			// useGeneratedKeys로 채워진 idx (신규 등록 시 생성된 PK, 수정 시 기존 idx)
			Object idxVal = paramMap.get("idx");
			if (idxVal != null) {
				obj.addProperty("idx", asText(idxVal));
			}
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}
		return obj.toString();
	}

	/*
	 * part		: 본사
	 * method 	: NoticeDelete
	 * comment 	: 본사 -> 공지사항 -> 삭제
	 */
	@PostMapping("HeadOffice/NoticeDelete")
	public String NoticeDelete(@RequestBody Map<String, Object> paramMap) {
		int iResult = headOfficeService.NoticeDelete(paramMap);

		JsonObject obj = new JsonObject();
		if (iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
		} else {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
		}
		return obj.toString();
	}

	/*
	 * part		: 본사
	 * method 	: NoticeFileList
	 * comment 	: 본사 -> 공지사항 -> 첨부파일 목록 조회
	 */
	@GetMapping("HeadOffice/NoticeFileList")
	public String NoticeFileList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.NoticeFileList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 본사
	 * method 	: NoticeFilesUpload
	 * comment 	: 본사 -> 공지사항 -> 첨부파일 업로드
	 */
	@PostMapping("HeadOffice/NoticeFilesUpload")
	public String NoticeFilesUpload(
		@RequestParam("notice_idx") String noticeIdx,
		@RequestParam("files") MultipartFile[] files
	) {
		JsonObject obj = new JsonObject();
		try {
			String noticeIdxText = asText(noticeIdx);
			if (noticeIdxText.isEmpty() || files == null || files.length == 0) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "notice_idx 또는 files 파라미터가 비어있습니다.");
				return obj.toString();
			}

			int noticeIdxInt;
			try { noticeIdxInt = Integer.parseInt(noticeIdxText); } catch (Exception e) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "notice_idx가 유효하지 않습니다.");
				return obj.toString();
			}

			Map<String, Object> listParam = new HashMap<>();
			listParam.put("notice_idx", noticeIdxText);
			List<Map<String, Object>> existingFiles = headOfficeService.NoticeFileList(listParam);
			int currentCount = existingFiles == null ? 0 : existingFiles.size();
			if (currentCount >= MAX_HEADOFFICE_DOCUMENT_FILE_COUNT) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "첨부 파일은 최대 10개까지 등록 가능합니다.");
				return obj.toString();
			}

			Path noticeDirPath = resolveNoticeDirPath(noticeIdxText);
			Files.createDirectories(noticeDirPath);

			int nextOrder = headOfficeService.GetNextNoticeFileOrder(noticeIdxInt);
			int availableCount = MAX_HEADOFFICE_DOCUMENT_FILE_COUNT - currentCount;
			List<Map<String, Object>> insertedFiles = new ArrayList<>();

			for (MultipartFile file : files) {
				if (insertedFiles.size() >= availableCount) break;
				if (file == null || file.isEmpty()) continue;

				String originalFileName = asText(file.getOriginalFilename());
				if (originalFileName.isEmpty()) originalFileName = "file";
				String safeFileName = Paths.get(originalFileName).getFileName().toString();
				String uniqueFileName = UUID.randomUUID() + "_" + safeFileName;

				Path filePath = noticeDirPath.resolve(uniqueFileName).normalize();
				if (!filePath.startsWith(noticeDirPath)) continue;
				file.transferTo(filePath.toFile());

				String imagePath = "/image/notice/" + noticeIdxText + "/" + uniqueFileName;

				Map<String, Object> saveParam = new HashMap<>();
				saveParam.put("notice_idx", noticeIdxInt);
				saveParam.put("image_order", nextOrder++);
				saveParam.put("image_path", imagePath);
				saveParam.put("image_name", safeFileName);

				headOfficeService.NoticeFileSave(saveParam);
				insertedFiles.add(saveParam);
			}

			if (insertedFiles.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "업로드 가능한 첨부 파일이 없습니다.");
				return obj.toString();
			}

			obj.addProperty("code", 200);
			obj.addProperty("message", "업로드 성공");
			obj.add("files", new Gson().toJsonTree(insertedFiles));
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "업로드 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part		: 본사
	 * method 	: NoticeFileDelete
	 * comment 	: 본사 -> 공지사항 -> 첨부파일 삭제
	 */
	@DeleteMapping("HeadOffice/NoticeFileDelete")
	public String NoticeFileDelete(@RequestParam Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			String noticeIdxText = asText(paramMap.get("notice_idx"));
			String imagePathText = asText(paramMap.get("image_path"));
			String imageOrderText = asText(paramMap.get("image_order"));
			if (noticeIdxText.isEmpty() || imageOrderText.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "notice_idx/image_order 파라미터가 필요합니다.");
				return obj.toString();
			}

			if (!imagePathText.isEmpty()) {
				String targetFileName = Paths.get(imagePathText).getFileName().toString();
				if (!targetFileName.isEmpty()) {
					Path noticeDirPath = resolveNoticeDirPath(noticeIdxText);
					Path targetPath = noticeDirPath.resolve(targetFileName).normalize();
					if (targetPath.startsWith(noticeDirPath)) {
						Files.deleteIfExists(targetPath);
					}
				}
			}

			headOfficeService.NoticeFileDelete(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "삭제 성공");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	private Path resolveNoticeDirPath(String noticeIdxText) {
		String staticPath = new File(uploadDir).getAbsolutePath();
		return Paths.get(staticPath, "notice", noticeIdxText).normalize();
	}

	/*
	 * part		: 인사
	 * method 	: EducationList
	 * comment 	: 인사 -> 교육 -> 목록 조회
	 */
	@GetMapping("HeadOffice/EducationList")
	public String EducationList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EducationList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 인사
	 * method 	: EducationDetail
	 * comment 	: 인사 -> 교육 -> 상세 조회
	 */
	@GetMapping("HeadOffice/EducationDetail")
	public String EducationDetail(@RequestParam Map<String, Object> paramMap) {
		Map<String, Object> result = headOfficeService.EducationDetail(paramMap);
		return new Gson().toJson(result);
	}

	/*
	 * part		: 인사
	 * method 	: EducationSave
	 * comment 	: 인사 -> 교육 -> 등록/수정 (upsert)
	 */
	@PostMapping("HeadOffice/EducationSave")
	public String EducationSave(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EducationSave(paramMap);
			Object idxObj = paramMap.get("idx");
			obj.addProperty("code", 200);
			obj.addProperty("message", "저장 성공");
			obj.addProperty("idx", idxObj != null ? idxObj.toString() : "");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "저장 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part		: 인사
	 * method 	: EducationDelete
	 * comment 	: 인사 -> 교육 -> 삭제 (소프트 삭제)
	 */
	@PostMapping("HeadOffice/EducationDelete")
	public String EducationDelete(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EducationDelete(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "삭제 성공");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part		: 인사
	 * method 	: EducationFileList
	 * comment 	: 인사 -> 교육 -> 첨부파일 목록 조회
	 */
	@GetMapping("HeadOffice/EducationFileList")
	public String EducationFileList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EducationFileList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part		: 인사
	 * method 	: EducationFilesUpload
	 * comment 	: 인사 -> 교육 -> 첨부파일 업로드
	 */
	@PostMapping("HeadOffice/EducationFilesUpload")
	public String EducationFilesUpload(
		@RequestParam("education_idx") String educationIdx,
		@RequestParam("files") MultipartFile[] files
	) {
		JsonObject obj = new JsonObject();
		try {
			String educationIdxText = asText(educationIdx);
			if (educationIdxText.isEmpty() || files == null || files.length == 0) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "education_idx 또는 files 파라미터가 비어있습니다.");
				return obj.toString();
			}

			int educationIdxInt;
			try { educationIdxInt = Integer.parseInt(educationIdxText); } catch (Exception e) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "education_idx가 유효하지 않습니다.");
				return obj.toString();
			}

			Map<String, Object> listParam = new HashMap<>();
			listParam.put("education_idx", educationIdxText);
			List<Map<String, Object>> existingFiles = headOfficeService.EducationFileList(listParam);
			int currentCount = existingFiles == null ? 0 : existingFiles.size();
			if (currentCount >= MAX_HEADOFFICE_DOCUMENT_FILE_COUNT) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "첨부 파일은 최대 10개까지 등록 가능합니다.");
				return obj.toString();
			}

			Path educationDirPath = resolveEducationDirPath(educationIdxText);
			Files.createDirectories(educationDirPath);

			int nextOrder = headOfficeService.GetNextEducationFileOrder(educationIdxInt);
			int availableCount = MAX_HEADOFFICE_DOCUMENT_FILE_COUNT - currentCount;
			List<Map<String, Object>> insertedFiles = new ArrayList<>();

			for (MultipartFile file : files) {
				if (insertedFiles.size() >= availableCount) break;
				if (file == null || file.isEmpty()) continue;

				String originalFileName = asText(file.getOriginalFilename());
				if (originalFileName.isEmpty()) originalFileName = "file";
				String safeFileName = Paths.get(originalFileName).getFileName().toString();
				String uniqueFileName = UUID.randomUUID() + "_" + safeFileName;

				Path filePath = educationDirPath.resolve(uniqueFileName).normalize();
				if (!filePath.startsWith(educationDirPath)) continue;
				file.transferTo(filePath.toFile());

				String imagePath = "/image/education/" + educationIdxText + "/" + uniqueFileName;

				Map<String, Object> saveParam = new HashMap<>();
				saveParam.put("education_idx", educationIdxInt);
				saveParam.put("image_order", nextOrder++);
				saveParam.put("image_path", imagePath);
				saveParam.put("image_name", safeFileName);

				headOfficeService.EducationFileSave(saveParam);
				insertedFiles.add(saveParam);
			}

			if (insertedFiles.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "업로드 가능한 첨부 파일이 없습니다.");
				return obj.toString();
			}

			obj.addProperty("code", 200);
			obj.addProperty("message", "업로드 성공");
			obj.add("files", new Gson().toJsonTree(insertedFiles));
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "업로드 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part		: 인사
	 * method 	: EducationFileDelete
	 * comment 	: 인사 -> 교육 -> 첨부파일 삭제
	 */
	@DeleteMapping("HeadOffice/EducationFileDelete")
	public String EducationFileDelete(@RequestParam Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			String educationIdxText = asText(paramMap.get("education_idx"));
			String imagePathText = asText(paramMap.get("image_path"));
			String imageOrderText = asText(paramMap.get("image_order"));
			if (educationIdxText.isEmpty() || imageOrderText.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "education_idx/image_order 파라미터가 필요합니다.");
				return obj.toString();
			}

			if (!imagePathText.isEmpty()) {
				String targetFileName = Paths.get(imagePathText).getFileName().toString();
				if (!targetFileName.isEmpty()) {
					Path educationDirPath = resolveEducationDirPath(educationIdxText);
					Path targetPath = educationDirPath.resolve(targetFileName).normalize();
					if (targetPath.startsWith(educationDirPath)) {
						Files.deleteIfExists(targetPath);
					}
				}
			}

			headOfficeService.EducationFileDelete(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "삭제 성공");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	private Path resolveEducationDirPath(String educationIdxText) {
		String staticPath = new File(uploadDir).getAbsolutePath();
		return Paths.get(staticPath, "education", educationIdxText).normalize();
	}

	// ──────────────────────────────────────────────────────────────────────────
	// 인사 → 평가 (tb_hr_evaluation / tb_hr_evaluation_file)
	// ──────────────────────────────────────────────────────────────────────────

	/*
	 * part    : 인사
	 * method  : EvaluationFormInit
	 * comment : 인사 -> 평가 -> 작성 폼 초기화 단일 API (3개 → 1개 통합)
	 *            - types : 평가 문서 타입 목록 (tb_hr_evaluation_type, 대분류/중분류/소분류 셀렉터용)
	 *            - users : 전체 사용자 목록 dept_name 포함 (tb_user)
	 *                      → 프론트에서 groupBy 로 부서 목록 파생
	 *                      → 부서 변경 시 클라이언트 필터링으로 작성자 목록 파생 (추가 API 없음)
	 */
	@GetMapping("HeadOffice/EvaluationFormInit")
	public String EvaluationFormInit() {
		List<Map<String, Object>> types = headOfficeService.EvaluationFormTypes();
		List<Map<String, Object>> users = headOfficeService.EvaluationFormUsers();

		JsonObject obj = new JsonObject();
		obj.add("types", new Gson().toJsonTree(types));
		obj.add("users", new Gson().toJsonTree(users));
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationList
	 * comment : 인사 -> 평가 -> 목록 조회
	 */
	@GetMapping("HeadOffice/EvaluationTypeList")
	public String EvaluationTypeList() {
		List<Map<String, Object>> resultList = headOfficeService.EvaluationTypeList();
		return new Gson().toJson(resultList);
	}

	@PostMapping("HeadOffice/EvaluationTypeSave")
	public String EvaluationTypeSave(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			int affected = headOfficeService.EvaluationTypeSave(paramMap);
			if (affected > 0) {
				obj.addProperty("code", 200);
				obj.addProperty("message", "저장 성공");
			} else {
				obj.addProperty("code", 400);
				obj.addProperty("message", "저장 실패: 변경된 데이터가 없습니다.");
			}
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "저장 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	@PostMapping("HeadOffice/EvaluationTypeDelete")
	public String EvaluationTypeDelete(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			int affected = headOfficeService.EvaluationTypeDelete(paramMap);
			if (affected > 0) {
				obj.addProperty("code", 200);
				obj.addProperty("message", "삭제 성공");
			} else {
				obj.addProperty("code", 400);
				obj.addProperty("message", "삭제 실패: 변경된 데이터가 없습니다.");
			}
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	@GetMapping("HeadOffice/EvaluationList")
	public String EvaluationList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EvaluationList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part    : 인사
	 * method  : EvaluationDetail
	 * comment : 인사 -> 평가 -> 상세 조회 (KPI 행만, 레거시 호환용)
	 */
	@GetMapping("HeadOffice/EvaluationDetail")
	public String EvaluationDetail(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> result = headOfficeService.EvaluationDetail(paramMap);
		return new Gson().toJson(result);
	}

	/*
	 * part    : 인사
	 * method  : EvaluationDetailWithFiles
	 * comment : 인사 -> 평가 -> 상세 + 첨부파일 통합 조회 (프론트 API 1회 호출로 처리)
	 *            - detail: 동일 세션 KPI 행 배열
	 *            - files : 첨부파일 목록 (notice_idx = 세션 대표 idx 기준)
	 */
	@GetMapping("HeadOffice/EvaluationDetailWithFiles")
	public String EvaluationDetailWithFiles(@RequestParam Map<String, Object> paramMap) {
		// KPI 상세 조회
		List<Map<String, Object>> detail = headOfficeService.EvaluationDetail(paramMap);

		// 첨부파일 조회 (notice_idx = idx 동일 사용)
		Map<String, Object> fileParams = new HashMap<>();
		fileParams.put("evaluation_idx", paramMap.get("idx"));
		List<Map<String, Object>> files = headOfficeService.EvaluationFileList(fileParams);

		JsonObject obj = new JsonObject();
		obj.add("detail", new Gson().toJsonTree(detail));
		obj.add("files",  new Gson().toJsonTree(files));
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationSave
	 * comment : 인사 -> 평가 -> 다수 KPI 행 일괄 저장, 첫 번째 idx 반환
	 */
	@SuppressWarnings("unchecked")
	@PostMapping("HeadOffice/EvaluationSave")
	public String EvaluationSave(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			String userId      = asText(paramMap.get("user_id"));
			String startTime   = asText(paramMap.get("start_time"));
			String endTime     = asText(paramMap.get("end_time"));
			// 문서구분 (tb_hr_evaluation_type.doc_type)
			String docType     = asText(paramMap.get("doc_type"));
			// 작성자 담당확인 도장 - 저장 시 항상 '4' 고정 (tm_sign/hr_sign 방식과 동일)
			String chargeSign  = "4";
			// 팀장ID / 인사팀장ID / 실장ID - 프론트에서 자동 감지
			String tmUser      = asText(paramMap.get("tm_user"));
			String hrUser      = asText(paramMap.get("hr_user"));
			String hpUser      = asText(paramMap.get("hp_user"));
			String ceoUser     = asText(paramMap.get("ceo_user"));
			// 수정 시: 기존 세션 대표 idx → 소프트 삭제 후 재삽입 (upsert, 별도 UPDATE SQL 불필요)
			String editIdx     = asText(paramMap.get("edit_idx"));
			Object itemsObj    = paramMap.get("items");

			if (userId.isEmpty() || startTime.isEmpty() || endTime.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "user_id / start_time / end_time 는 필수입니다.");
				return obj.toString();
			}
			if (!(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "items 배열이 비어있습니다.");
				return obj.toString();
			}

			List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;
			int firstIdx = -1;

			// upsert: edit_idx가 있으면 기존 KPI 행 삭제 + 헤더 소프트삭제 후 재삽입
			if (!editIdx.isEmpty()) {
				Map<String, Object> periodParam = new HashMap<>();
				periodParam.put("idx",            editIdx);
				periodParam.put("new_start_time", startTime);
				periodParam.put("new_end_time",   endTime);
				headOfficeService.EvaluationUpdatePeriod(periodParam);

				Map<String, Object> deleteParam = new HashMap<>();
				deleteParam.put("idx", editIdx);
				headOfficeService.EvaluationDelete(deleteParam);  // KPI 삭제 + 헤더 소프트삭제
			}

			// document_id 생성: {doc_type}-{YYYYMMDDHHmmss}{seq_padded_3}
			String documentId = "";
			if (!docType.isEmpty()) {
				String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
				int seq = headOfficeService.EvaluationCountByDocTypeAndDate(docType) + 1;
				documentId = docType + "-" + stamp + String.format("%03d", seq);
			}

			// 헤더 1건 저장 (tb_hr_evaluation)
			Map<String, Object> headerRow = new HashMap<>();
			headerRow.put("user_id",     userId);
			headerRow.put("start_time",  startTime);
			headerRow.put("end_time",    endTime);
			headerRow.put("doc_type",    docType);
			headerRow.put("charge_sign", chargeSign);
			headerRow.put("tm_user",     tmUser);
			headerRow.put("hr_user",     hrUser);
			headerRow.put("hp_user",     hpUser);
			headerRow.put("ceo_user",    ceoUser);
			headerRow.put("document_id", documentId);
			headOfficeService.EvaluationSave(headerRow);
			Object generatedIdx = headerRow.get("idx");
			if (generatedIdx != null) {
				try { firstIdx = Integer.parseInt(generatedIdx.toString()); } catch (Exception ignored) {}
			}

			// KPI 행 저장 (tb_hr_evaluation_kpi, document_id = 문서번호)
			for (Map<String, Object> item : items) {
				Map<String, Object> kpiRow = new HashMap<>();
				kpiRow.put("document_id", documentId);
				kpiRow.put("type",        item.get("type"));
				kpiRow.put("goal",        item.get("goal"));
				kpiRow.put("measurement", item.get("measurement"));
				kpiRow.put("weight",      item.get("weight"));
				kpiRow.put("performance", item.get("performance"));
				kpiRow.put("content",     item.get("content"));
				headOfficeService.EvaluationKpiSave(kpiRow);
			}

			// upsert: 기존 파일을 새 first_idx로 재연결 (소프트 삭제된 old_idx에 묶인 파일 보존)
			if (!editIdx.isEmpty() && firstIdx > 0) {
				try {
					int oldIdx = Integer.parseInt(editIdx);
					if (oldIdx != firstIdx) {
						headOfficeService.MigrateEvaluationFiles(oldIdx, firstIdx);
					}
				} catch (Exception ignored) {}
			}

			obj.addProperty("code", 200);
			obj.addProperty("message", "저장 성공");
			obj.addProperty("first_idx", firstIdx > 0 ? firstIdx : 0);
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "저장 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationDelete
	 * comment : 인사 -> 평가 -> 삭제 (소프트 삭제)
	 */
	@PostMapping("HeadOffice/EvaluationDelete")
	public String EvaluationDelete(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationDelete(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "삭제 성공");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationFileList
	 * comment : 인사 -> 평가 -> 첨부파일 목록 조회
	 */
	@GetMapping("HeadOffice/EvaluationFileList")
	public String EvaluationFileList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EvaluationFileList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part    : 인사
	 * method  : EvaluationFilesUpload
	 * comment : 인사 -> 평가 -> 첨부파일 업로드
	 */
	@PostMapping("HeadOffice/EvaluationFilesUpload")
	public String EvaluationFilesUpload(
		@RequestParam("evaluation_idx") String evaluationIdx,
		@RequestParam("files") MultipartFile[] files
	) {
		JsonObject obj = new JsonObject();
		try {
			String idxText = asText(evaluationIdx);
			if (idxText.isEmpty() || files == null || files.length == 0) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "evaluation_idx 또는 files 파라미터가 비어있습니다.");
				return obj.toString();
			}

			int idxInt;
			try { idxInt = Integer.parseInt(idxText); } catch (Exception e) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "evaluation_idx가 유효하지 않습니다.");
				return obj.toString();
			}

			Map<String, Object> listParam = new HashMap<>();
			listParam.put("evaluation_idx", idxText);
			List<Map<String, Object>> existingFiles = headOfficeService.EvaluationFileList(listParam);
			int currentCount = existingFiles == null ? 0 : existingFiles.size();
			if (currentCount >= MAX_EVALUATION_FILE_COUNT) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "첨부 파일은 최대 20개까지 등록 가능합니다.");
				return obj.toString();
			}

			Path evalDirPath = resolveEvaluationDirPath(idxText);
			Files.createDirectories(evalDirPath);

			int nextOrder = headOfficeService.GetNextEvaluationFileOrder(idxInt);
			int available = MAX_EVALUATION_FILE_COUNT - currentCount;
			List<Map<String, Object>> inserted = new ArrayList<>();

			for (MultipartFile file : files) {
				if (inserted.size() >= available) break;
				if (file == null || file.isEmpty()) continue;

				String originalName = asText(file.getOriginalFilename());
				if (originalName.isEmpty()) originalName = "file";
				String safeName   = Paths.get(originalName).getFileName().toString();
				String uniqueName = UUID.randomUUID() + "_" + safeName;

				Path filePath = evalDirPath.resolve(uniqueName).normalize();
				if (!filePath.startsWith(evalDirPath)) continue;
				file.transferTo(filePath.toFile());

				String imagePath = "/image/evaluation/" + idxText + "/" + uniqueName;

				Map<String, Object> saveParam = new HashMap<>();
				saveParam.put("evaluation_idx", idxInt);
				saveParam.put("image_order",    nextOrder++);
				saveParam.put("image_path",     imagePath);
				saveParam.put("image_name",     safeName);
				headOfficeService.EvaluationFileSave(saveParam);
				inserted.add(saveParam);
			}

			if (inserted.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "업로드 가능한 파일이 없습니다.");
				return obj.toString();
			}

			obj.addProperty("code", 200);
			obj.addProperty("message", "업로드 성공");
			obj.add("files", new Gson().toJsonTree(inserted));
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "업로드 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationFileDelete
	 * comment : 인사 -> 평가 -> 첨부파일 삭제
	 */
	@DeleteMapping("HeadOffice/EvaluationFileDelete")
	public String EvaluationFileDelete(@RequestParam Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			String idxText        = asText(paramMap.get("evaluation_idx"));
			String imagePathText  = asText(paramMap.get("image_path"));
			String imageOrderText = asText(paramMap.get("image_order"));
			if (idxText.isEmpty() || imageOrderText.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "evaluation_idx / image_order 파라미터가 필요합니다.");
				return obj.toString();
			}
			if (!imagePathText.isEmpty()) {
				String targetFileName = Paths.get(imagePathText).getFileName().toString();
				if (!targetFileName.isEmpty()) {
					Path evalDirPath = resolveEvaluationDirPath(idxText);
					Path targetPath  = evalDirPath.resolve(targetFileName).normalize();
					if (targetPath.startsWith(evalDirPath)) {
						Files.deleteIfExists(targetPath);
					}
				}
			}
			headOfficeService.EvaluationFileDelete(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "삭제 성공");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "삭제 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	private Path resolveEvaluationDirPath(String idxText) {
		String staticPath = new File(uploadDir).getAbsolutePath();
		return Paths.get(staticPath, "evaluation", idxText).normalize();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationPerformanceUpdate
	 * comment : 인사 -> 평가 -> 실적 업데이트
	 *            - 팀장 확인 후 작성자가 실적(%)만 수정할 때 호출
	 *            - items: [{ idx, performance }] 배열로 전달, 각 KPI 행 idx 기준으로 performance만 갱신
	 *            - tm_sign, hr_sign 등 확인 도장은 유지됨
	 */
	@PostMapping("HeadOffice/EvaluationPerformanceUpdate")
	public String EvaluationPerformanceUpdate(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationPerformanceUpdate(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "실적 저장 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실적 저장 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationTeamLeaderConfirm
	 * comment : 인사 -> 평가 -> 팀장 확인 처리
	 *            - 같은 부서의 팀장(position=1)이 확인 버튼을 눌렀을 때 호출
	 *            - 동일 세션(user_id + start_time + end_time)의 모든 KPI 행에 팀장 확인 정보를 기록
	 */
	@PostMapping("HeadOffice/EvaluationTeamLeaderConfirm")
	public String EvaluationTeamLeaderConfirm(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			// opinion 파라미터: tm_opinion 컬럼에 저장 (빈 값이면 무시)
			headOfficeService.EvaluationTeamLeaderConfirm(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "팀장 확인 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "팀장 확인 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationHpLeaderConfirm
	 * comment : 인사 -> 평가 -> 실장 확인 처리
	 *            - department=8 팀장(position=1)이 dept 4·5 문서 확인 시 호출
	 *            - 팀장 확인(tm_sign='4') 완료 후에만 처리됨
	 *            - hp_opinion 저장, hr_read_dt=NULL (인사팀장 알림 발송)
	 */
	@PostMapping("HeadOffice/EvaluationHpLeaderConfirm")
	public String EvaluationHpLeaderConfirm(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationHpLeaderConfirm(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "실장 확인 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "실장 확인 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationHrLeaderConfirm
	 * comment : 인사 -> 평가 -> 인사팀장 확인 처리
	 *            - 인사팀(department=3) 팀장(position=1)이 확인 버튼을 눌렀을 때 호출
	 *            - 동일 세션의 모든 KPI 행에 payer_sign='4' 기록
	 */
	@PostMapping("HeadOffice/EvaluationHrLeaderConfirm")
	public String EvaluationHrLeaderConfirm(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationHrLeaderConfirm(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "인사팀장 확인 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "인사팀장 확인 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part    : 인사
	 * method  : EvaluationNotificationList
	 * comment : 인사 -> 평가 -> 알림 목록 (네비바 뱃지용)
	 *            - 팀장확인요청 : tm_user가 본인이고 아직 팀장 미확인 상태
	 *            - 인사팀장확인요청 : payer_user가 본인이고 인사팀장 미확인 상태
	 *            - 확인완료 : 본인 작성 문서가 최종 확인 완료됐으나 열람 안 한 경우
	 */
	@GetMapping("HeadOffice/EvaluationNotificationList")
	public String EvaluationNotificationList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.EvaluationNotificationList(paramMap);
		return new Gson().toJson(resultList);
	}

	/*
	 * part    : 인사
	 * method  : EvaluationNotificationReadSave
	 * comment : 인사 -> 평가 -> 알림 읽음 처리
	 *            - notify_type='확인완료' → reg_read_dt 갱신 (이후 알림 목록에서 제거)
	 *            - notify_type='팀장확인요청' → tm_read_dt 갱신
	 *            - notify_type='인사팀장확인요청' → payer_read_dt 갱신
	 */
	@PostMapping("HeadOffice/EvaluationCeoLeaderConfirm")
	public String EvaluationCeoLeaderConfirm(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationCeoLeaderConfirm(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "대표 확인 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "대표 확인 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	@PostMapping("HeadOffice/EvaluationNotificationReadSave")
	public String EvaluationNotificationReadSave(@RequestBody Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();
		try {
			headOfficeService.EvaluationNotificationReadSave(paramMap);
			obj.addProperty("code", 200);
			obj.addProperty("message", "알림 읽음 처리 완료");
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "알림 읽음 처리 실패: " + e.getMessage());
		}
		return obj.toString();
	}

	/*
	 * part		: 본사
     * method 	: getMainPayload
     * comment 	: 본사 -> 전자결재 관리 -> 요청 payload에서 메인 데이터 추출
     */
	@SuppressWarnings("unchecked")
	private Map<String, Object> getMainPayload(Map<String, Object> payload) {
		// payload.main 우선 사용, 없으면 payload 전체를 main으로 해석
		if (payload == null) return new HashMap<>();

		Object mainObj = payload.get("main");
		if (mainObj instanceof Map) {
			return new HashMap<>((Map<String, Object>) mainObj);
		}

		return new HashMap<>(payload);
	}

	/* 
	 * part		: 본사
     * method 	: getItemPayloadList
     * comment 	: 본사 -> 전자결재 관리 -> 요청 payload에서 품목 목록 추출
     */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> getItemPayloadList(Map<String, Object> payload) {
		if (payload == null) return new ArrayList<>();

		Object itemObj = payload.get("item");
		if (!(itemObj instanceof List)) return new ArrayList<>();

		List<Map<String, Object>> result = new ArrayList<>();
		for (Object rowObj : (List<Object>) itemObj) {
			if (rowObj instanceof Map) {
				result.add(new HashMap<>((Map<String, Object>) rowObj));
			}
		}
		return result;
	}

	/* 
	 * part		: 본사
     * method 	: buildDraftPayload
     * comment 	: 본사 -> 전자결재 관리 -> 기안서 본문 저장 파라미터 생성
     */
	private Map<String, Object> buildDraftPayload(
		List<Map<String, Object>> itemList,
		Map<String, Object> main,
		String paymentIdText
	) {
		// 기안서 타입은 본문을 1건으로 관리한다.
		Map<String, Object> draft = new HashMap<>();
		draft.put("payment_id", paymentIdText);
		draft.put("title", "");
		draft.put("details", "");
		draft.put("note", "");

		if (itemList == null) return draft;

		for (Map<String, Object> item : itemList) {
			if (item == null) continue;

			String title = asText(item.get("title"));
			if (title.isEmpty()) title = asText(item.get("item_name"));
			String details = asText(item.get("details"));
			if (details.isEmpty()) details = asText(item.get("use_note"));
			String note = asText(item.get("note"));
			if (title.isEmpty() && details.isEmpty() && note.isEmpty()) continue;

			draft.put("title", title);
			draft.put("details", details);
			draft.put("note", note);
			break;
		}

		return draft;
	}

	/* 
	 * part		: 본사
     * method 	: buildPaymentDocRows
     * comment 	: 본사 -> 전자결재 관리 -> 지출결의서 상세 행 파라미터 목록 생성
     */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> buildPaymentDocRows(
		List<Map<String, Object>> itemList,
		Map<String, Object> main,
		String paymentIdText
	) {
		// 지출결의서는 payment_id 1건에 item_name(세부내역) 여러 행을 저장한다.
		List<Map<String, Object>> rows = new ArrayList<>();
		if (itemList == null) return rows;

		int resolvedTotal = 0;
		String fallbackTitle = asText(main.get("title"));

		for (Map<String, Object> item : itemList) {
			if (item == null) continue;

			String title = asText(item.get("title"));
			if (title.isEmpty()) title = fallbackTitle;
			if (title.isEmpty()) title = asText(item.get("item_name"));

			String place = asText(item.get("place"));

			// use_note: 결제업체명, use_name: 용도
			String useNote = asText(item.get("use_note"));

			String useName = asText(item.get("use_name"));

			String content = asText(item.get("content"));

			String accountNumber = asText(item.get("account_number"));
			String bizNo = asText(item.get("biz_no"));
			// account_name 컬럼은 예금주 용도로 저장
			String accountName = asText(item.get("account_name"));
			String requestDt = asText(item.get("request_dt"));
			if (requestDt.isEmpty()) requestDt = asText(main.get("start_dt"));
			int paymentType = resolvePaymentTypeCode(item.get("payment_type"));
			String paymentTypeDetail = resolvePaymentTypeDetail(item);

			int qty = parseIntOrZero(item.get("qty"));
			if (qty <= 0) qty = 1;
			int price = parseIntOrZero(item.get("price"));
			int amount = parseIntOrZero(item.get("amount"));
			int tax = parseIntOrZero(item.get("tax"));
			int total = parseIntOrZero(item.get("total"));
			if (total > 0 && resolvedTotal <= 0) resolvedTotal = total;

			Object detailRowsObj = item.get("detail_rows");
			if (detailRowsObj instanceof List) {
				List<Object> detailRows = (List<Object>) detailRowsObj;
				for (Object detailRowObj : detailRows) {
					if (!(detailRowObj instanceof Map)) continue;
					Map<String, Object> detailRow = (Map<String, Object>) detailRowObj;

					String detailItemName = asText(detailRow.get("detail_text"));
					if (detailItemName.isEmpty()) detailItemName = asText(detailRow.get("item_name"));
					if (detailItemName.isEmpty()) continue;

					int rowQty = parseIntOrZero(detailRow.get("qty"));
					if (rowQty <= 0) rowQty = qty > 0 ? qty : 1;
					int rowPrice = parseIntOrZero(detailRow.get("price"));
					int rowAmount = parseIntOrZero(detailRow.get("amount"));
					int rowTax = parseIntOrZero(detailRow.get("tax"));
					int rowTotal = parseIntOrZero(detailRow.get("total"));
					if (rowTotal <= 0) rowTotal = rowAmount + rowTax;
					if (rowPrice <= 0) rowPrice = rowTotal;
					if (rowTotal <= 0 && rowQty > 0 && rowPrice > 0) rowTotal = rowQty * rowPrice;

					rows.add(
						createPaymentDocRow(
							paymentIdText,
							title,
							place,
							useNote,
							useName,
							detailItemName,
							content,
							rowQty,
							rowPrice,
							rowAmount,
							rowTax,
							rowTotal,
							paymentType,
							paymentTypeDetail,
							requestDt,
							accountNumber,
							bizNo,
							accountName
						)
					);
				}
				continue;
			}

			String itemName = asText(item.get("item_name"));
			if (itemName.isEmpty()) itemName = asText(item.get("detail_text"));
			if (itemName.isEmpty()) itemName = content;
			if (itemName.isEmpty()) continue;

			int rowTotal = parseIntOrZero(item.get("row_total"));
			if (rowTotal <= 0) rowTotal = total;
			if (rowTotal <= 0) rowTotal = amount + tax;
			if (price <= 0) price = rowTotal;
			if (rowTotal <= 0 && qty > 0 && price > 0) rowTotal = qty * price;

			rows.add(
				createPaymentDocRow(
					paymentIdText,
					title,
					place,
					useNote,
					useName,
					itemName,
					content,
					qty,
					price,
					amount,
					tax,
						rowTotal,
						paymentType,
						paymentTypeDetail,
						requestDt,
						accountNumber,
						bizNo,
						accountName
				)
			);
		}

		if (rows.isEmpty()) return rows;

		// total은 문서 전체 합계이므로 같은 payment_id의 모든 행에 동일 값으로 저장한다.
		if (resolvedTotal <= 0) {
			for (Map<String, Object> row : rows) {
				resolvedTotal += parseIntOrZero(row.get("price"));
			}
		}
		for (Map<String, Object> row : rows) {
			row.put("total", resolvedTotal);
		}

		return rows;
	}

	/* 
	 * part		: 본사
     * method 	: createPaymentDocRow
     * comment 	: 본사 -> 전자결재 관리 -> 지출결의서 상세 1건 파라미터 생성
     */
	private Map<String, Object> createPaymentDocRow(
		String paymentIdText,
		String title,
		String place,
		String useNote,
		String useName,
		String itemName,
		String content,
		int qty,
		int price,
		int amount,
		int tax,
		int total,
		int paymentType,
		String paymentTypeDetail,
		String requestDt,
		String accountNumber,
		String bizNo,
		String accountName
	) {
		Map<String, Object> row = new HashMap<>();
		row.put("payment_id", paymentIdText);
		row.put("title", title);
		row.put("place", place);
		row.put("use_note", useNote);
		row.put("use_name", useName);
		row.put("item_name", itemName);
		row.put("content", content);
		row.put("qty", qty);
		row.put("price", price);
		row.put("amount", amount);
		row.put("tax", tax);
		row.put("total", total);
		row.put("payment_type", paymentType > 0 ? paymentType : null);
		row.put("payment_type_detail", paymentTypeDetail);
		row.put("request_dt", requestDt);
		row.put("account_number", accountNumber);
		row.put("biz_no", bizNo);
		row.put("account_name", accountName);
		return row;
	}

	// 전자결재 타입 테이블(tb_electronic_payment_type) 기준으로
	// doc_type -> 문서종류(draft/expendable/payment) 매핑을 구성한다.
	/* 
	 * part		: 본사
     * method 	: buildDocKindByTypeMap
     * comment 	: 본사 -> 전자결재 관리 -> 문서 타입별 문서종류 매핑 생성
     */
	private Map<String, String> buildDocKindByTypeMap() {
		Map<String, String> result = new HashMap<>();

		List<Map<String, Object>> typeRows =
			headOfficeService.HeadOfficeElectronicPaymentTypeList(new HashMap<>());
		if (typeRows == null) return result;

		for (Map<String, Object> row : typeRows) {
			if (row == null) continue;
			String docType = asText(row.get("doc_type")).toUpperCase();
			String docName = asText(row.get("doc_name"));
			if (docType.isEmpty() || docName.isEmpty()) continue;
			result.put(docType, detectDocKindByName(docName));
		}

		return result;
	}

	// 요청으로 전달된 doc_type 코드가 어떤 문서종류인지 판정한다.
	// - 기준 데이터: tb_electronic_payment_type.doc_type/doc_name
	/* 
	 * part		: 본사
     * method 	: resolveDocKind
     * comment 	: 본사 -> 전자결재 관리 -> doc_type으로 문서종류 판정
     */
	private String resolveDocKind(String docType, Map<String, String> docKindByType) {
		String key = asText(docType).toUpperCase();
		if (key.isEmpty()) return "";
		if (docKindByType == null) return "";
		return asText(docKindByType.get(key));
	}

	// doc_name 문자열을 내부 문서종류 키로 변환한다.
	/* 
	 * part		: 본사
     * method 	: detectDocKindByName
     * comment 	: 본사 -> 전자결재 관리 -> 문서명으로 내부 문서종류 키 판정
     */
	private String detectDocKindByName(String docName) {
		String key = asText(docName).replaceAll("\\s+", "");
		if (key.contains("소모품") && key.contains("품의서")) return DOC_KIND_EXPENDABLE;
		if (key.contains("기안서")) return DOC_KIND_DRAFT;
		if (key.contains("지출결의서")) return DOC_KIND_PAYMENT;
		return "";
	}

	// 문서 공통 첨부 파일 저장 로직
	/* 
	 * part		: 본사
     * method 	: uploadHeadOfficeDocumentFiles
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 파일 업로드 및 메타 저장
     */
	private String uploadHeadOfficeDocumentFiles(String paymentId, MultipartFile[] files) {
		JsonObject obj = new JsonObject();

		try {
			String paymentIdText = asText(paymentId);
			if (paymentIdText.isEmpty() || files == null || files.length == 0) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "payment_id 또는 files 파라미터가 비어있습니다.");
				return obj.toString();
			}

			Path paymentDirPath = resolveElectronicPaymentDirPath(paymentIdText);
			Files.createDirectories(paymentDirPath);

			Map<String, Object> fileListParam = new HashMap<>();
			fileListParam.put("payment_id", paymentIdText);
			List<Map<String, Object>> existingFiles = headOfficeService.ElectronicPaymentFileList(fileListParam);
			int currentCount = existingFiles == null ? 0 : existingFiles.size();
			if (currentCount >= MAX_HEADOFFICE_DOCUMENT_FILE_COUNT) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "첨부 파일은 최대 10개까지 등록 가능합니다.");
				return obj.toString();
			}

			int nextOrder = headOfficeService.GetNextElectronicPaymentImageOrder(paymentIdText);
			int availableCount = MAX_HEADOFFICE_DOCUMENT_FILE_COUNT - currentCount;
			List<Map<String, Object>> insertedFiles = new ArrayList<>();
			int skippedUnsupportedCount = 0;

			for (MultipartFile file : files) {
				if (insertedFiles.size() >= availableCount) break;
				if (file == null || file.isEmpty()) continue;

				String originalFileName = asText(file.getOriginalFilename());
				if (originalFileName.isEmpty()) originalFileName = "file";
				String safeFileName = Paths.get(originalFileName).getFileName().toString();
				String fileExtension = extractFileExtensionLower(safeFileName);
				if (!isSupportedHeadOfficeDocumentExtension(fileExtension)) {
					skippedUnsupportedCount++;
					continue;
				}
				String uniqueFileName = UUID.randomUUID() + "_" + safeFileName;

				Path filePath = paymentDirPath.resolve(uniqueFileName).normalize();
				if (!filePath.startsWith(paymentDirPath)) continue;
				file.transferTo(filePath.toFile());

				String imagePath = "/image/electronic_payment/" + paymentIdText + "/" + uniqueFileName;

				Map<String, Object> saveParam = new HashMap<>();
				saveParam.put("payment_id", paymentIdText);
				saveParam.put("image_order", nextOrder++);
				saveParam.put("image_path", imagePath);
				saveParam.put("image_name", safeFileName);

				headOfficeService.SaveElectronicPaymentFile(saveParam);
				insertedFiles.add(saveParam);
			}

			if (insertedFiles.isEmpty()) {
				obj.addProperty("code", 400);
				if (skippedUnsupportedCount > 0) {
					obj.addProperty("message", "지원하지 않는 파일 형식입니다. (이미지/PDF/XLS/XLSX만 허용)");
				} else {
					obj.addProperty("message", "업로드 가능한 첨부 파일이 없습니다.");
				}
				return obj.toString();
			}

			obj.addProperty("code", 200);
			obj.addProperty("message", "업로드 성공");
			obj.add("images", new Gson().toJsonTree(insertedFiles));
		} catch (Exception e) {
			obj.addProperty("code", 400);
			obj.addProperty("message", "업로드 실패: " + e.getMessage());
		}

		return obj.toString();
	}

	// 문서 공통 첨부 이미지 삭제 로직
	/* 
	 * part		: 본사
     * method 	: deleteHeadOfficeDocumentFile
     * comment 	: 본사 -> 전자결재 관리 -> 문서 공통 첨부 파일 및 메타 삭제
     */
	private String deleteHeadOfficeDocumentFile(Map<String, Object> paramMap) {
		JsonObject obj = new JsonObject();

		try {
			String paymentIdText = asText(paramMap.get("payment_id"));
			String imagePathText = asText(paramMap.get("image_path"));
			String imageOrderText = asText(paramMap.get("image_order"));
			if (paymentIdText.isEmpty() || imagePathText.isEmpty() || imageOrderText.isEmpty()) {
				obj.addProperty("code", 400);
				obj.addProperty("message", "payment_id/image_path/image_order 파라미터가 필요합니다.");
				return obj.toString();
			}

			String targetFileName = Paths.get(imagePathText).getFileName().toString();
			if (!targetFileName.isEmpty()) {
				Path paymentDirPath = resolveElectronicPaymentDirPath(paymentIdText);
				Path targetPath = paymentDirPath.resolve(targetFileName).normalize();
				if (targetPath.startsWith(paymentDirPath)) {
					Files.deleteIfExists(targetPath);
				}
			}

			int iResult = headOfficeService.ElectronicPaymentFileDelete(paramMap);
			if (iResult > 0) {
				obj.addProperty("code", 200);
				obj.addProperty("message", "파일 삭제 성공");
			} else {
				obj.addProperty("code", 404);
				obj.addProperty("message", "삭제 대상 파일 메타가 없습니다.");
			}
		} catch (Exception e) {
			obj.addProperty("code", 500);
			obj.addProperty("message", "서버 오류: " + e.getMessage());
		}

		return obj.toString();
	}

	/* 
	 * part		: 본사
     * method 	: resolveElectronicPaymentDirPath
     * comment 	: 본사 -> 전자결재 관리 -> 결재문서 첨부 저장 경로 생성
     */
	private Path resolveElectronicPaymentDirPath(String paymentIdText) {
		String staticPath = new File(uploadDir).getAbsolutePath();
		return Paths.get(staticPath, "electronic_payment", paymentIdText).normalize();
	}

	/* 
	 * part		: 본사
     * method 	: resolveElectronicPaymentStoredFilePath
     * comment 	: 본사 -> 전자결재 관리 -> DB image_path를 실제 저장 파일 경로로 변환
     */
	private Path resolveElectronicPaymentStoredFilePath(String imagePathText) {
		String normalizedPath = decodeUriPathRepeatedly(imagePathText).replace("\\", "/");
		String relativePath = normalizedPath;
		if (relativePath.startsWith("/")) {
			relativePath = relativePath.substring(1);
		}
		if (relativePath.startsWith("image/")) {
			relativePath = relativePath.substring("image/".length());
		}

		Path basePath = Paths.get(new File(uploadDir).getAbsolutePath()).normalize();
		Path filePath = basePath.resolve(relativePath).normalize();
		if (!filePath.startsWith(basePath)) {
			return null;
		}
		return filePath;
	}

	/* 
	 * part		: 본사
     * method 	: decodeUriPathRepeatedly
     * comment 	: 본사 -> 전자결재 관리 -> 인코딩된 DB 경로를 최대 3회까지 복원
     */
	private String decodeUriPathRepeatedly(String value) {
		String current = asText(value);
		if (current.isEmpty()) return "";

		for (int i = 0; i < 3; i++) {
			try {
				String next = URLDecoder.decode(current, StandardCharsets.UTF_8);
				if (next.equals(current)) break;
				current = next;
			} catch (Exception e) {
				break;
			}
		}
		return current;
	}

	/* 
	 * part		: 본사
     * method 	: extractFileExtensionLower
     * comment 	: 본사 -> 전자결재 관리 -> 파일 확장자 소문자 추출
     */
	private String extractFileExtensionLower(String fileName) {
		String safeName = asText(fileName);
		int dotIndex = safeName.lastIndexOf(".");
		if (dotIndex < 0 || dotIndex >= safeName.length() - 1) return "";
		return safeName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
	}

	/* 
	 * part		: 본사
     * method 	: isSupportedHeadOfficeDocumentExtension
     * comment 	: 본사 -> 전자결재 관리 -> 첨부 허용 확장자 여부 확인
     */
	private boolean isSupportedHeadOfficeDocumentExtension(String extension) {
		String ext = asText(extension).toLowerCase(Locale.ROOT);
		if (ext.isEmpty()) return false;
		return HEADOFFICE_DOCUMENT_ALLOWED_EXTENSIONS.contains(ext);
	}

	/* 
	 * part		: 본사
     * method 	: asText
     * comment 	: 본사 -> 전자결재 관리 -> 객체 값을 공백 제거 문자열로 변환
     */
	private String asText(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	/* 
	 * part		: 본사
     * method 	: parseIntOrZero
     * comment 	: 본사 -> 전자결재 관리 -> 숫자값 파싱 실패 시 0으로 변환
     */
	private int parseIntOrZero(Object value) {
		String raw = asText(value).replace(",", "");
		if (raw.isEmpty()) return 0;
		try {
			return Integer.parseInt(raw);
		} catch (Exception e) {
			return 0;
		}
	}

	// item payload에서 payment_type 값을 읽어 최종 지급구분 코드로 정규화
	/* 
	 * part		: 본사
     * method 	: resolvePaymentTypeCode
     * comment 	: 본사 -> 전자결재 관리 -> 지급구분 코드 정규화
     */
	private int resolvePaymentTypeCode(Object paymentTypeObj) {
		int paymentType = parseIntOrZero(paymentTypeObj);
		if (paymentType >= 1 && paymentType <= 5) return paymentType;
		return 0;
	}

	// item payload에서 지급구분 상세값을 읽는다.
	/* 
	 * part		: 본사
     * method 	: resolvePaymentTypeDetail
     * comment 	: 본사 -> 전자결재 관리 -> 지급구분 상세값 정규화
     */
	private String resolvePaymentTypeDetail(Map<String, Object> item) {
		return asText(item.get("payment_type_detail"));
	}

	/* 
	 * part		: 본사
     * method 	: normalizeYn
     * comment 	: 본사 -> 전자결재 관리 -> Y/N 값 정규화
     */
	private String normalizeYn(Object value) {
		return "Y".equals(asText(value).toUpperCase()) ? "Y" : "N";
	}

	/* 
	 * part		: 본사
     * method 	: normalizeStatusText
     * comment 	: 본사 -> 전자결재 관리 -> 결재 상태값 우선순위 정규화
     */
	private String normalizeStatusText(Object currentValue, Object fallbackValue) {
		// 현재값 우선, 없으면 대체값 사용
		if (!isBlank(currentValue)) return String.valueOf(currentValue).trim();
		if (!isBlank(fallbackValue)) return String.valueOf(fallbackValue).trim();
		return "";
	}

	/* 
	 * part		: 본사
     * method 	: resolveDocumentStatus
     * comment 	: 본사 -> 전자결재 관리 -> 결재자 상태를 기준으로 문서 상태 계산
     */
	private int resolveDocumentStatus(Map<String, Object> main) {
		// 상태 규칙
		// 1: 아무도 결재 안함
		// 2: 한 명 이상 결재함(진행중)
		// 3: 한 명 이상 반려함
		// 4: 모든 결재자 결재 완료
		int tm = parseStatus(main.get("tm_sign"));
		int ceo = parseStatus(main.get("ceo_sign"));
		int payer = parseStatus(main.get("payer_sign"));

		boolean hasTm = !isBlank(main.get("tm_user"));
		boolean hasCeo = !isBlank(main.get("ceo_user"));
		boolean hasPayer = !isBlank(main.get("payer_user"));
		boolean hasAnyApprover = hasTm || hasCeo || hasPayer;

		boolean rejected = (hasTm && tm == 3) || (hasCeo && ceo == 3) || (hasPayer && payer == 3);
		if (rejected) return 3;

		boolean allApproved =
			(!hasTm || tm == 4) &&
			(!hasCeo || ceo == 4) &&
			(!hasPayer || payer == 4);
		if (hasAnyApprover && allApproved) return 4;

		boolean anyApproved = (hasTm && tm == 4) || (hasCeo && ceo == 4) || (hasPayer && payer == 4);
		if (anyApproved) return 2;

		return 1;
	}

	/* 
	 * part		: 본사
     * method 	: parseStatus
     * comment 	: 본사 -> 전자결재 관리 -> 상태 코드를 숫자로 파싱
     */
	private int parseStatus(Object statusObj) {
		// 숫자형 상태코드 파싱 실패 시 0 처리
		if (isBlank(statusObj)) return 0;

		try {
			return Integer.parseInt(String.valueOf(statusObj).trim());
		} catch (Exception e) {
			return 0;
		}
	}

	/* 
	 * part		: 본사
     * method 	: isBlank
     * comment 	: 본사 -> 전자결재 관리 -> 값의 공백 여부 확인
     */
	private boolean isBlank(Object value) {
		return value == null || String.valueOf(value).trim().isEmpty();
	}
}

