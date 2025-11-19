package com.example.demo.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.WebConfig;
import com.example.demo.service.BusinessService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
@CrossOrigin(origins = {
    "http://localhost:3000",       // 개발용
    "http://172.30.1.48:8080"      // 운영 React
})
public class BusinessController {

    private final OcrController ocrController;

	private final BusinessService businessService;
	
    @Autowired
    public BusinessController(BusinessService businessService, WebConfig webConfig, OcrController ocrController) {
    	this.businessService = businessService;
    	this.ocrController = ocrController;
    }
    
    /* 
	 * part		: 영업
     * method 	: BusinessTeleAccountList
     * comment 	: 고객사 관리 -> TM영업 업체 조회
     */
    @GetMapping("Business/BusinessTeleAccountList")
    public String BusinessTeleAccountList(@RequestParam(required = false) Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.BusinessTeleAccountList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 영업
     * method 	: AccountBusinessImgList
     * comment 	: 고객사 정보 -> 거래처 상세 이미지 조회
     */
    @GetMapping("Business/AccountBusinessImgList")
    public String AccountBusinessImgList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.AccountBusinessImgList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 영업
     * method 	: AccountBusinessImgUpload
     * comment 	: 거래처 -> 영업관리 -> 파일업로드
     
    @PostMapping("Business/BusinessImgUpload")
    public void uploadFiles(
            @RequestParam("account_id") String accountId,
            @RequestParam(value = "business_report", required = false) MultipartFile businessReport,
            @RequestParam(value = "business_regist", required = false) MultipartFile businessRegist,
            @RequestParam(value = "kitchen_drawing", required = false) MultipartFile kitchenDrawing
    ) throws IOException {
        Map<String, String> filePathMap = new HashMap<>();

        if (businessReport != null && !businessReport.isEmpty()) {
            String path = saveFile(accountId, "business_report", businessReport);
            filePathMap.put("business_report", path);
        }

        if (businessRegist != null && !businessRegist.isEmpty()) {
            String path = saveFile(accountId, "business_regist", businessRegist);
            filePathMap.put("business_regist", path);
        }

        if (kitchenDrawing != null && !kitchenDrawing.isEmpty()) {
            String path = saveFile(accountId, "kitchen_drawing", kitchenDrawing);
            filePathMap.put("kitchen_drawing", path);
        }

        // Map을 MyBatis로 저장
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("account_id", accountId);
        paramMap.putAll(filePathMap);

        businessService.insertOrUpdateFile(paramMap);
    }
    */
    /* 
	 * part		: 영업
     * method 	: saveFile
     * comment 	: 고객사 관리 -> 파일조회
     */
    @PostMapping("Business/BusinessImgUpload")
    private String saveFile( @RequestParam("file") MultipartFile file,
    	    @RequestParam("type") String type,
    	    @RequestParam("gubun") String gubun,
    	    @RequestParam("folder") String folder) throws IOException {
    	
    	int iResult = 0;
    	String resultPath = "";
    	
        // 프로젝트 루트 대신 static 폴더 경로 사용
        String staticPath = new File("src/main/resources/static").getAbsolutePath();
        String basePath = staticPath + "/image/" + type + "/" + gubun + "/"+ folder +  "/";
        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath); // 폴더 없으면 생성

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile()); // 파일 저장
        
        // 브라우저 접근용 경로 반환
        resultPath = "/image/" + type + "/" + gubun + "/" + folder + "/" + uniqueFileName;
        
        // Map을 MyBatis로 저장
        Map<String, Object> paramMap = new HashMap<>();
        
        if (type.equals("car")) {
        	paramMap.put("car_number", folder);
        	paramMap.put("service_dt", gubun);
        	paramMap.put("exterior_image", "/image/" + type + "/" + gubun + "/" + folder + "/" + uniqueFileName);
        	
        	iResult += businessService.CarSave(paramMap);
        }
        
        JsonObject obj =new JsonObject();
    	
    	if(iResult > 0) {
			obj.addProperty("code", 200);
			obj.addProperty("message", "성공");
			obj.addProperty("image_path", resultPath);
    	} else {
    		obj.addProperty("code", 400);
			obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
        //businessService.insertOrUpdateFile(paramMap);
    }
    
    /* 
	 * part		: 영업
     * method 	: BusinessTeleAccountSave
     * comment 	: 고객사 관리 -> TM관리 저장
     */
    @PostMapping("Business/BusinessTeleAccountSave")
    private String BusinessTeleAccountSave(@RequestBody List<Map<String, Object>> param) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> row : param) {	
    		// 이건 일자별 테이블 저장.
    		if (row.get("act_dt") != null || row.get("act_type") != null || row.get("memo") != null) {
    			Map<String,Object> dailyMap = new HashMap<String,Object>();
    			dailyMap.put("idx", Integer.parseInt(row.get("idx").toString()));
    			dailyMap.put("act_dt", row.get("act_dt").toString());
    			dailyMap.put("act_type", Integer.parseInt(row.get("act_type").toString()));
    			dailyMap.put("memo", row.get("memo").toString());
    			
    			iResult += BusinessDailySave(dailyMap);
    		}
    		// 상태가 계약완료이면 거래처 등록.
    		if (row.get("contract_type") != null && row.get("contract_type").equals(2)) {
    			Map<String,Object> accountMap = new HashMap<String,Object>();
    			accountMap.put("account_name", row.get("account_name").toString());
    			
    			System.out.println(accountMap.get("account_name"));
    			
    			if (row.get("manager") != null) {
    				accountMap.put("manager_name", row.get("manager").toString());
    			}
    			
    			iResult += BusinessContractSuccessSave(accountMap);
    		}
    		
    		row.put("idx", Integer.parseInt(row.get("idx").toString()));
    		
    		if(row.get("contract_type") != null) {
    			row.put("contract_type", Integer.parseInt(row.get("contract_type").toString()));
    		}
    		
    		iResult += BusinessTeleInfoSave(row);
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
	 * part		: 영업
     * method 	: NowDateKey
     * comment 	: 거래처 저장을 위한 key 생성 
     */
    private String NowDateKey() {
    	String accountKey = businessService.NowDateKey();
    	return accountKey;
    }
    /* 
	 * part		: 영업
     * method 	: BusinessTeleInfoSave
     * comment 	: 고객사 관리 -> TM 업장 정보 저장
     */
    private int BusinessTeleInfoSave(Map<String, Object>param) {
    	int iResult = 0;
    	iResult = businessService.BusinessTeleInfoSave(param);
    	return iResult;
    }
    /* 
	 * part		: 영업
     * method 	: BusinessDailySave
     * comment 	: 고객사 관리 -> TM 일자별 저장
     */
    private int BusinessDailySave(Map<String, Object>param) {
    	int iResult = 0;
    	iResult = businessService.BusinessDailySave(param);
    	return iResult;
    }
    /* 
	 * part		: 영업
     * method 	: BusinessContractSuccessSave
     * comment 	: 고객사 관리 -> TM 업장 계약완료 거래처 등록, 거래처 정보까지 저장.
     */
    private int BusinessContractSuccessSave(Map<String, Object>param) {
    	String accountKey = NowDateKey();
    	int iResult = 0;
    	param.put("account_id", accountKey);
    	iResult += businessService.BusinessContractSuccessSave(param);
    	if(iResult > 0) {
    		iResult += businessService.BusinessContractSuccessSave_2(param);
    	}
    	return iResult;
    }
    /* 
	 * part		: 영업
     * method 	: CarSelectList
     * comment 	: 고객사 관리 -> 법인차량 Select box 조회.
     */
    @GetMapping("Business/CarSelectList")
    private String CarSelectList(@RequestParam Map<String, Object>param) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.CarSelectList(param);
    	
    	return new Gson().toJson(resultList);
    }
    /* 
	 * part		: 영업
     * method 	: CarList
     * comment 	: 고객사 관리 -> 법인차량 조회.
     */
    @GetMapping("Business/CarList")
    private String CarList(@RequestParam Map<String, Object>param) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.CarList(param);
    	
    	return new Gson().toJson(resultList);
    }
    /* 
	 * part		: 영업
     * method 	: CarSave
     * comment 	: 고객사 관리 -> 법인차량 저장
     */
    @PostMapping("Business/CarSave")
    private String CarSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += businessService.CarSave(paramMap);
        }
    	
    	JsonObject obj = new JsonObject();
    	
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
	 * part		: 영업
     * method 	: CarNewSave
     * comment 	: 고객사 관리 -> 법인차량 등록 저장.
     */
    @PostMapping("Business/CarNewSave")
    private String CarNewSave(@RequestParam Map<String, Object> paramMap) {
    	
    	JsonObject obj =new JsonObject();
    	
    	if(businessService.CarNewSave(paramMap) > 0) {
    		obj.addProperty("code", 200);
    		obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
    		obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
    }
    
    /* 
	 * part		: 영업
     * method 	: CookWearList
     * comment 	: 고객사 관리 -> 조리복 재고 조회.
     */
    @GetMapping("Business/CookWearList")
    private String CookWearList(@RequestParam Map<String, Object>param) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.CookWearList(param);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 영업
     * method 	: CookWearList
     * comment 	: 고객사 관리 -> 조리복 분출 내역 조회.
     */
    @GetMapping("Business/CookWearOutList")
    private String CookWearOutList(@RequestParam Map<String, Object>param) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.CookWearOutList(param);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 영업
     * method 	: CookWearList
     * comment 	: 고객사 관리 -> 조리복 신규 주문 조회
     */
    @GetMapping("Business/CookWearNewList")
    private String CookWearNewList(@RequestParam Map<String, Object>param) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = businessService.CookWearNewList(param);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 영업
     * method 	: CarSave
     * comment 	: 고객사 관리 -> 조리복 재고 저장
     */
    @SuppressWarnings("unchecked")
	@PostMapping("Business/CookWearSave")
    private String CookWearSave(@RequestBody Map<String, Object> payload) {
    	
    	int iResult = 0;
    	
    	// stockList 꺼내기
        Map<String, Object> stockList = (Map<String, Object>) payload.get("stockList");
        List<Map<String, Object>> stockItems = (List<Map<String, Object>>) stockList.get("list");

        // outList 꺼내기
        Map<String, Object> outList = (Map<String, Object>) payload.get("outList");
        List<Map<String, Object>> outItems = (List<Map<String, Object>>) outList.get("list");

        // newList 꺼내기
        Map<String, Object> newList = (Map<String, Object>) payload.get("newList");
        List<Map<String, Object>> newItems = (List<Map<String, Object>>) newList.get("list");
        
        // 조리복 재고현황 저장
        if (!stockItems.isEmpty()) {
        	for (Map<String, Object> paramMap : stockItems) {
        		paramMap.put("orign_qty", paramMap.get("current_qty"));
                iResult += businessService.CookWearSave(paramMap);
            }
        }
    	
        // 조리복 분출현황 저장
        if (!outItems.isEmpty()) {
        	for (Map<String, Object> paramMap : outItems) {
                iResult += businessService.CookWearOutSave(paramMap);
            }
        }
        
        // 조리복 주문현황 저장
        if (!newItems.isEmpty()) {
        	for (Map<String, Object> paramMap : newItems) {
                iResult += businessService.CookWearNewSave(paramMap);
            }
        }
        
    	JsonObject obj = new JsonObject();
    	
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
	 * part		: 영업
     * method 	: AccountEventList
     * comment 	: 고객사 관리 -> 고객사 행사관리 조회
     */
    @GetMapping("Business/AccountEventList")
    private String AccountEventList(@RequestParam Map<String, Object> param) {

        List<Map<String, Object>> eventList = businessService.AccountEventList(param);

        for (Map<String, Object> row : eventList) {
            Object eventId = row.get("event_id");

            if (eventId != null) {
                List<Map<String, Object>> files = businessService.EventFileList(eventId);
                row.put("images", files);   // 이미지 배열로 넣는다!
            }
        }

        return new Gson().toJson(eventList);
    }
    
    /* 
	 * part		: 영업
     * method 	: AccountEventFileDelete
     * comment 	: 고객사 관리 -> 고객사 행사관리 이미지 삭제
     */
    @DeleteMapping("Business/AccountEventFileDelete")
    public String AccountEventFileDelete(@RequestParam Map<String, Object> param) {
        JsonObject obj = new JsonObject();
        try {
            // ✅ 삭제할 파일 경로 구성
            Path filePath2 = Paths.get("src/main/resources/static" + param.get("image_path"));
            File file = filePath2.toFile();

            if (!file.exists()) {
                obj.addProperty("code", 404);
                obj.addProperty("message", "파일이 존재하지 않습니다: " + filePath2);
                return obj.toString();
            }

            boolean deleted = file.delete();
        	
        	int iResult = 0;
        	
        	iResult = businessService.AccountEventFileDelete(param);

            if (deleted && iResult > 0) {
                obj.addProperty("code", 200);
                obj.addProperty("message", "파일 삭제 성공");
            } else {
                obj.addProperty("code", 500);
                obj.addProperty("message", "파일 삭제 실패 (삭제 권한 또는 잠금 확인)");
            }

        } catch (Exception e) {
            obj.addProperty("code", 500);
            obj.addProperty("message", "서버 오류: " + e.getMessage());
        }

        return obj.toString();
    }
    
    /* 
	 * part		: 영업
     * method 	: AccountEventSave
     * comment 	: 고객사 관리 -> 고객사 행사관리 저장
     */
	@PostMapping("Business/AccountEventSave")
    public Map<String, Object> AccountEventSave(@RequestBody Map<String, Object> param) {
		
		System.out.println(" ===== param :: " + param);
		System.out.println(" ===== account_id :: " + param.get("account_id"));
		System.out.println(" ===== event_name :: " + param.get("event_name"));
		System.out.println(" ===== event_dt :: " + param.get("event_dt"));
		System.out.println(" ===== user_id :: " + param.get("user_id"));
		
        if (param.get("event_id") == null) {
            // 신규 저장
        	businessService.EventSave(param); 
            // param 안에 event_id 자동 매핑됨
        } else {
            // 수정 저장
        	businessService.EventUpdate(param);
        }
        return param; // event_id 포함됨
    }
	
	 /* 
	 * part		: 영업
     * method 	: AccountEventSave
     * comment 	: 고객사 관리 -> 고객사 행사관리 수정
     */
	@PostMapping("Business/AccountEventUpdate")
    public Map<String, Object> AccountEventUpdate(@RequestBody Map<String, Object> param) {
		
        if (param.get("event_id") == null) {
            // 신규 저장
        	businessService.EventSave(param); 
            // param 안에 event_id 자동 매핑됨
        } else {
            // 수정 저장
        	businessService.EventUpdate(param);
        }
        return param; // event_id 포함됨
    }
	
	/* 
	 * part		: 영업
     * method 	: AccountEventSave
     * comment 	: 고객사 관리 -> 고객사 행사관리 이미지 저장
     */
	@PostMapping("Business/AccountEventFilesUpload")
	private String uploadEventFiles(
	        @RequestParam("event_id") int eventId,
	        @RequestParam("files") MultipartFile[] files
	) throws IOException {

	    JsonObject obj = new JsonObject();

	    try {

	        // ------------------------------
	        // 1) 이미지 저장 경로 구성
	        // ------------------------------
	        String staticPath = new File("src/main/resources/static").getAbsolutePath();
	        String basePath = staticPath + "/image/event/" + eventId + "/";
	        Path dirPath = Paths.get(basePath);
	        Files.createDirectories(dirPath); // 폴더 없으면 생성

	        // ------------------------------
	        // 2) DB에 저장할 image_order 조회
	        // ------------------------------
	        int nextOrder = businessService.GetNextImageOrder(eventId); 
	        // 없으면 1부터 시작

	        List<Map<String, Object>> insertedFiles = new ArrayList<>();

	        // ------------------------------
	        // 3) 업로드 파일 저장 처리
	        // ------------------------------
	        for (MultipartFile file : files) {

	            String originalFileName = file.getOriginalFilename();
	            String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;

	            Path filePath = dirPath.resolve(uniqueFileName);
	            file.transferTo(filePath.toFile());

	            String imagePath = "/image/event/" + eventId + "/" + uniqueFileName;

	            // ------------------------------
	            // 4) DB 저장
	            // ------------------------------
	            Map<String, Object> param = new HashMap<>();
	            param.put("event_id", eventId);
	            param.put("image_order", nextOrder++);
	            param.put("image_path", imagePath);
	            param.put("image_name", originalFileName);

	            businessService.SaveEventFile(param);

	            insertedFiles.add(param);
	        }

	        // ------------------------------
	        // 5) 응답 구성
	        // ------------------------------
	        obj.addProperty("code", 200);
	        obj.addProperty("message", "업로드 성공");
	        obj.add("images", new Gson().toJsonTree(insertedFiles));

	    } catch (Exception e) {
	        obj.addProperty("code", 400);
	        obj.addProperty("message", "업로드 실패: " + e.getMessage());
	    }

	    return obj.toString();
	}

}
