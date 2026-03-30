package com.example.demo.controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * method 	: WeekMenuList
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
     * comment 	: 본사 -> 관리표 -> 손익표 엑섹다운
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
			// 결재문서 키가 비어있으면 요청번호를 결재문서 키로 사용
			Object paymentId = main.get("payment_id");
			if (paymentId == null || String.valueOf(paymentId).trim().isEmpty()) {
				main.put("payment_id", String.valueOf(main.getOrDefault("request_no", "")).trim());
			}

			// 프론트에서 상태코드가 없을 수 있어 기본값/대체값으로 정규화
			main.put("charge_sign", normalizeStatusText(main.get("charge_sign"), "4"));
			main.put("tm_sign", normalizeStatusText(main.get("tm_sign"), main.get("tm_status")));
			main.put("payer_sign", normalizeStatusText(main.get("payer_sign"), main.get("payer_status")));
			main.put("ceo_sign", normalizeStatusText(main.get("ceo_sign"), main.get("ceo_status")));

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
			String paymentNote = String.valueOf(main.getOrDefault("payment_note", "")).trim();
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
						row.put("payment_note", paymentNote);
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
     * method 	: ProfitLossTableSave
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
     * method 	: HeadOfficeDepartmentList
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
     * method 	: HeadOfficeDepartmentList
     * comment 	: 본사 -> 전자결재 관리 -> 부서목록 선택 시, 부서 직원 조회
     */
	@GetMapping("HeadOffice/HeadOfficeUserListByDepartment")
	public String HeadOfficeUserListByDepartment(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.HeadOfficeUserListByDepartment(paramMap);
		
		return new Gson().toJson(resultList);
	}

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

			String title = asText(item.get("item_name"));
			String details = asText(item.get("use_note"));
			String note = asText(item.get("note"));
			if (title.isEmpty() && details.isEmpty() && note.isEmpty()) continue;

			draft.put("title", title);
			draft.put("details", details);
			draft.put("note", note);
			break;
		}

		return draft;
	}

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
			if (place.isEmpty()) place = asText(item.get("site_name"));

			// use_note: 결제업체명, use_name: 용도
			String useNote = asText(item.get("use_note"));
			if (useNote.isEmpty()) useNote = asText(item.get("account_name"));
			if (useNote.isEmpty()) useNote = asText(item.get("note"));

			String useName = asText(item.get("use_name"));
			if (useName.isEmpty()) useName = asText(item.get("item_name"));

			String content = asText(item.get("content"));
			if (content.isEmpty()) content = asText(item.get("payment_note"));

			String accountNumber = asText(item.get("account_number"));
			String bizNo = asText(item.get("biz_no"));
			// account_name 컬럼은 예금주 용도로 저장
			String accountName = asText(item.get("account_name"));
			if (accountName.isEmpty()) accountName = asText(item.get("depositor_name"));
			int paymentType = resolvePaymentTypeCode(item.get("payment_type"), item.get("payment_method"));
			String paymentTypeDetail = resolvePaymentTypeDetail(item, paymentType);

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
		row.put("account_number", accountNumber);
		row.put("biz_no", bizNo);
		row.put("account_name", accountName);
		return row;
	}

	// 전자결재 타입 테이블(tb_electronic_payment_type) 기준으로
	// doc_type -> 문서종류(draft/expendable/payment) 매핑을 구성한다.
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
	private String resolveDocKind(String docType, Map<String, String> docKindByType) {
		String key = asText(docType).toUpperCase();
		if (key.isEmpty()) return "";
		if (docKindByType == null) return "";
		return asText(docKindByType.get(key));
	}

	// doc_name 문자열을 내부 문서종류 키로 변환한다.
	private String detectDocKindByName(String docName) {
		String key = asText(docName).replaceAll("\\s+", "");
		if (key.contains("소모품") && key.contains("품의서")) return DOC_KIND_EXPENDABLE;
		if (key.contains("기안서")) return DOC_KIND_DRAFT;
		if (key.contains("지출결의서")) return DOC_KIND_PAYMENT;
		return "";
	}

	// 문서 공통 첨부 파일 저장 로직
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

	private Path resolveElectronicPaymentDirPath(String paymentIdText) {
		String staticPath = new File(uploadDir).getAbsolutePath();
		return Paths.get(staticPath, "electronic_payment", paymentIdText).normalize();
	}

	private String extractFileExtensionLower(String fileName) {
		String safeName = asText(fileName);
		int dotIndex = safeName.lastIndexOf(".");
		if (dotIndex < 0 || dotIndex >= safeName.length() - 1) return "";
		return safeName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
	}

	private boolean isSupportedHeadOfficeDocumentExtension(String extension) {
		String ext = asText(extension).toLowerCase(Locale.ROOT);
		if (ext.isEmpty()) return false;
		return HEADOFFICE_DOCUMENT_ALLOWED_EXTENSIONS.contains(ext);
	}

	private String asText(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	private int parseIntOrZero(Object value) {
		String raw = asText(value).replace(",", "");
		if (raw.isEmpty()) return 0;
		try {
			return Integer.parseInt(raw);
		} catch (Exception e) {
			return 0;
		}
	}

	// 지급구분 문자열(cash/card/transfer/auto/other)을 DB 코드(1~5)로 변환
	private int toPaymentTypeCodeByMethod(String methodText) {
		String method = asText(methodText).toLowerCase(Locale.ROOT);
		if ("cash".equals(method)) return 1;
		if ("card".equals(method)) return 2;
		if ("transfer".equals(method)) return 3;
		if ("auto".equals(method)) return 4;
		if ("other".equals(method)) return 5;
		return 0;
	}

	// item payload에서 payment_type/payment_method를 읽어 최종 지급구분 코드로 정규화
	private int resolvePaymentTypeCode(Object paymentTypeObj, Object paymentMethodObj) {
		int paymentType = parseIntOrZero(paymentTypeObj);
		if (paymentType >= 1 && paymentType <= 5) return paymentType;
		return toPaymentTypeCodeByMethod(asText(paymentMethodObj));
	}

	// item payload에서 지급구분 상세값을 읽는다.
	// - 신규 컬럼(payment_type_detail) 우선
	// - 구버전 payload(payment_method별 상세 필드) fallback 지원
	private String resolvePaymentTypeDetail(Map<String, Object> item, int paymentType) {
		String explicitDetail = asText(item.get("payment_type_detail"));
		if (!explicitDetail.isEmpty()) return explicitDetail;

		if (paymentType == 1) return asText(item.get("cash_receipt_text"));
		if (paymentType == 2) {
			String cardTail = asText(item.get("card_tail")).replaceAll("[^\\d]", "");
			if (cardTail.length() > 4) cardTail = cardTail.substring(cardTail.length() - 4);
			return cardTail;
		}
		if (paymentType == 3) return asText(item.get("transfer_receipt_text"));
		if (paymentType == 4) return asText(item.get("auto_text"));
		if (paymentType == 5) return asText(item.get("other_text"));
		return "";
	}

	private String normalizeYn(Object value) {
		return "Y".equals(asText(value).toUpperCase()) ? "Y" : "N";
	}

	private String normalizeStatusText(Object currentValue, Object fallbackValue) {
		// 현재값 우선, 없으면 대체값 사용
		if (!isBlank(currentValue)) return String.valueOf(currentValue).trim();
		if (!isBlank(fallbackValue)) return String.valueOf(fallbackValue).trim();
		return "";
	}

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

	private int parseStatus(Object statusObj) {
		// 숫자형 상태코드 파싱 실패 시 0 처리
		if (isBlank(statusObj)) return 0;

		try {
			return Integer.parseInt(String.valueOf(statusObj).trim());
		} catch (Exception e) {
			return 0;
		}
	}

	private boolean isBlank(Object value) {
		return value == null || String.valueOf(value).trim().isEmpty();
	}
}
