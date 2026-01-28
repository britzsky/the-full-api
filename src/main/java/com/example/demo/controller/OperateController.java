package com.example.demo.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.WebConfig;
import com.example.demo.service.OperateService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
public class OperateController {

	private final OperateService operateService;
	private final String uploadDir;
	
    @Autowired
    public OperateController(
	    		OperateService operateService, 
	    		WebConfig webConfig,
	    		@Value("${file.upload-dir}") String uploadDir
    		) {
    	this.operateService = operateService;
    	this.uploadDir = uploadDir;
    }
    
    /*
     * part		: 운영
     * method 	: AccountTallySheetList
     * comment 	: 운영파트 -> 집계표 조회
     */
    @GetMapping("/Operate/TallySheetList")
    public String TallySheetList(@RequestParam Map<String, Object> paramMap) {
    	int digits = 2; // 원하는 자릿수

        // String.format()을 사용하여 숫자 앞에 0 추가
    	// 1. Map에서 "month" 값을 String으로 가져옵니다. (확실하게 String임을 가정)
    	String monthString = paramMap.get("month").toString(); // 안전하게 toString() 호출

    	// 2. String을 정수(int)로 변환합니다.
    	int monthValue = Integer.parseInt(monthString); // 이 부분에서 d 타입에 맞는 정수가 됨

    	// 3. String.format()을 사용하여 숫자 앞에 0 추가
    	String formattedNumber = String.format("%0" + digits + "d", monthValue);
    	
    	paramMap.put("month", formattedNumber);
        
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.TallySheetList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * method 	: NowDateKey
     * comment 	: 거래처 저장을 위한 key 생성 
     */
    private String NowDateKey() {
    	String accountKey = operateService.NowDateKey();
    	return accountKey;
    }
    
    /*
     * part		: 운영
     * method 	: TallySheetSave
     * comment 	: 급식사업부 -> 운영관리 -> 집계표 저장
     */
    @SuppressWarnings("unchecked")
	@PostMapping("Operate/TallySheetSave")
    private String TallySheetSave(@RequestBody Map<String, Object> payload) {
    	
    	int iResult = 0;
    	
    	List<Map<String, Object>> nowList = (List<Map<String, Object>>) payload.get("nowList");
        List<Map<String, Object>> beforeList = (List<Map<String, Object>>) payload.get("beforeList");

        // 본월 집계표 저장
        if (!nowList.isEmpty()) {
        	for (Map<String, Object> paramMap : nowList) {
                iResult += operateService.TallyNowMonthSave(paramMap);
                iResult += operateService.processProfitLoss(paramMap);
            }
        }
    	
        // 이월 집계표 저장
        if (!beforeList.isEmpty()) {
        	for (Map<String, Object> paramMap : beforeList) {
                iResult += operateService.TallyBeforeMonthSave(paramMap);
                iResult += operateService.processProfitLoss(paramMap);
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
     * part		: 운영
     * method 	: PropertiesSave
     * comment 	: 급식사업부 -> 운영관리 -> 기물리스트 저장
     */
    @PostMapping("Operate/PropertiesSave")
    private String PropertiesSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += operateService.PropertiesSave(paramMap);
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
     * part		: 운영
     * method 	: AccountPropertiesList
     * comment 	: 급식사업부 -> 운영관리 -> 기물리스트 조회
     */
    @GetMapping("Operate/PropertiesList")
    public String AccountPropertiesList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.PropertiesList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: AccountPropertiesList
     * comment 	: 급식사업부 -> 운영관리 -> 위생관리 조회
     */
    @GetMapping("Operate/HygieneList")
    public String HygieneList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.HygieneList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: saveFile
     * comment 	: 급식사업부 -> 운영관리 -> 파일업로드
     */
    @PostMapping("Operate/OperateImgUpload")
    private String saveFile( @RequestParam("file") MultipartFile file,
    	    @RequestParam("type") String type,
    	    @RequestParam("gubun") String gubun,
    	    @RequestParam("folder") String folder) throws IOException {
    	
    	String resultPath = "";
    	
        // 프로젝트 루트 대신 static 폴더 경로 사용
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + type + "/" + gubun + "/"+ folder +  "/";
        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath); // 폴더 없으면 생성

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile()); // 파일 저장
        
        // 브라우저 접근용 경로 반환
        resultPath = "/image/" + type + "/" + gubun + "/" + folder + "/" + uniqueFileName;
        
        JsonObject obj = new JsonObject();
    	
    	if(!resultPath.isEmpty()) {
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
     * part		: 운영
     * method 	: HygieneSave
     * comment 	: 급식사업부 -> 운영관리 -> 위생관리 저장
     */
    @PostMapping("Operate/HygieneSave")
    private String HygieneSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
    		
    		System.out.println("idx ==== " + paramMap.get("idx"));
    		System.out.println("account_id ==== " + paramMap.get("account_id").toString());
    		System.out.println("del_yn ==== " + paramMap.get("del_yn").toString());
    		
            iResult += operateService.HygieneSave(paramMap);
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
     * part		: 운영
     * method 	: HandOverSearch
     * comment 	: 급식사업부 -> 운영관리 -> 인수인계서 조회
     */
    @GetMapping("Operate/HandOverSearch")
    public String HandOverSearch(@RequestParam Map<String, Object> paramMap) {
    	Map<String, Object> resultMap = new HashMap<String, Object>();
    	resultMap = operateService.HandOverSearch(paramMap);
    	
    	return new Gson().toJson(resultMap);
    }
    
    /*
     * part		: 운영
     * method 	: HygieneSave
     * comment 	: 급식사업부 -> 운영관리 -> 인수인계서 저장
     */
    @PostMapping("Operate/HandOverSave")
    private String HandOverSave(@RequestBody Map<String, Object> paramMap) {
    	
    	int iResult = 0;
    	
    	iResult += operateService.HandOverSave(paramMap);
    	
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
     * part		: 운영
     * method 	: AccountMappingList
     * comment 	: 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회
     */
    @GetMapping("Operate/AccountMappingList")
    public String AccountMappingList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMappingList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: AccountMappingListV2
     * comment 	: 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 조회 V2
     */
    @GetMapping("Operate/AccountMappingV2List")
    public String AccountMappingV2List(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMappingV2List(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: AccountMappingSave
     * comment 	: 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 매핑 저장
     */
    @PostMapping("Operate/AccountMappingSave")
    private String AccountMappingSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += operateService.AccountMappingSave(paramMap);
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
     * part		: 운영
     * method 	: AccountCreateSave
     * comment 	: 급식사업부 -> 운영관리 -> 집계표, 거래처 관리 Modal 거래처 저장
     */
    @PostMapping("Operate/AccountRetailBusinessSave")
    private String AccountRetailBusinessSave(@RequestBody Map<String, Object> paramMap) {
    	
    	int iResult = 0;
    	iResult = operateService.AccountRetailBusinessSave(paramMap);
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
     * part		: 운영
     * method 	: AccountCreateSave
     * comment 	: 급식사업부 -> 운영관리 -> 집계표 Modal 거래처 저장
     */
    @PostMapping("Operate/AccountRetailBusinessSaveV2")
    private String AccountRetailBusinessSaveV2(@RequestBody List<Map<String, Object>> list) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> row : list) {
    		iResult += operateService.AccountRetailBusinessSave(row);
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
     * part		: 운영
     * method 	: AccountRetailBusinessList
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 거래처관리 조회
     */
    @GetMapping("Operate/AccountRetailBusinessList")
    public String AccountRetailBusinessList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountRetailBusinessList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: AccountMembersFilesList
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증 조회
     */
    @GetMapping("Operate/AccountMembersFilesList")
    public String AccountMembersFilesList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMembersFilesList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: AccountMembersFilesList
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증 타입별 조회
     */
    @GetMapping("Operate/AccountTypeForFileList")
    public String AccountTypeForFileList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountTypeForFileList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: AccountMembersFilesSave
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 면허증 및 자격증 저장
     */
    @PostMapping("Operate/AccountMembersFilesSave")
    private String AccountMembersFilesSave(@RequestBody List<Map<String, Object>> list) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> row : list) {
    		iResult += operateService.AccountMembersFilesSave(row);
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
     * part		: 운영
     * method 	: AccountSubRestaurantList
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 조회
     */
    @GetMapping("Operate/AccountSubRestaurantList")
    public String AccountSubRestaurantList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountSubRestaurantList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: AccountSubRestaurantSave
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 대체업체 저장
     */
    @PostMapping("Operate/AccountSubRestaurantSave")
    private String AccountSubRestaurantSave(@RequestBody List<Map<String, Object>> list) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> row : list) {
    		iResult += operateService.AccountSubRestaurantSave(row);
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
     * part		: 운영/인사
     * method 	: AccountMemberWorkSystemList
     * comment 	: 급식사업부 -> 근무형태 조회
     */
    @GetMapping("Operate/AccountMemberWorkSystemList")
    public String AccountMemberWorkSystemList() {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMemberWorkSystemList();
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영/인사
     * method 	: AccountMemberWorkSystemSave
     * comment 	: 급식사업부 -> 근무형태 저장
     */
    @PostMapping("Operate/AccountMemberWorkSystemSave")
    public String AccountMemberWorkSystemSave(@RequestBody List<Map<String, Object>> list) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> row : list) {
    		iResult += operateService.AccountMemberWorkSystemSave(row);
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
     * part		: 운영
     * method 	: AccountRecordMemberList
     * comment 	: 급식사업부 -> 운영관리 -> 고객사관리 -> 인사기록카드 조회
     */
    @GetMapping("Operate/AccountMemberSheetList")
    public String AccountMemberSheetList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMemberSheetList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영,인사
     * method 	: AccountMemberAllList
     * comment 	: 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 조회
     */
    @GetMapping("Operate/AccountMemberAllList")
    public String AccountMemberAllList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountMemberAllList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영,인사
     * method 	: AccountSubRestaurantSave
     * comment 	: 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 직원관리 저장
     */
    @PostMapping("Operate/AccountMembersSave")
    private String AccountMembersSave(@RequestBody Map<String, Object> paramMap) {

        List<Map<String, Object>> data = (List<Map<String, Object>>) paramMap.get("data");

        int iResult = 0;

        for (Map<String, Object> row : data) {

            // ✅ member_id가 없을 때만 생성
            Object memberIdObj = row.get("member_id");
            String memberId = memberIdObj == null ? "" : String.valueOf(memberIdObj).trim();

            if (memberId.isEmpty()) {
                memberId = operateService.NowDateKey();
                row.put("member_id", memberId);
            }

            iResult += operateService.AccountMembersSave(row);
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
     * part		: 운영/인사
     * method 	: AccountRecMemberList
     * comment 	: 급식사업부 -> 운영 -> 채용관리 -> 현장 채용현황 조회
     */
    @GetMapping("Operate/AccountRecMemberList")
    public String AccountRecMemberList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountRecMemberList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영/인사
     * method 	: AccountRecMembersSave
     * comment 	: 급식사업부 -> 운영 -> 채용관리 -> 현장 채용현황 저장
     */
    @PostMapping("Operate/AccountRecMembersSave")
    private String AccountRecMembersSave(@RequestBody Map<String, Object> paramMap) {

        List<Map<String, Object>> data = (List<Map<String, Object>>) paramMap.get("data");

        int iResult = 0;

        for (Map<String, Object> row : data) {

            // ✅ member_id가 없을 때만 생성
            Object memberIdObj = row.get("member_id");
            String memberId = memberIdObj == null ? "" : String.valueOf(memberIdObj).trim();

            if (memberId.isEmpty()) {
                memberId = operateService.NowDateKey();
                row.put("member_id", memberId);
            }

            iResult += operateService.AccountRecMembersSave(row);
            
            // 현장채용 관리에서 채용확정이면 직원정보를 저장.
            Object useynObj = row.get("use_yn");
        	String useyn = "";

        	if (useynObj != null && !useynObj.toString().isEmpty()) {
        		
        		useyn = useynObj.toString();
        		
        		if (useyn.toString().equals("Y")) {
        			row.put("del_yn", "N");
        			iResult += operateService.AccountMembersSave(row);
        			iResult += operateService.AccountRecordSetRecRecordDataSave(row);
        		}
        		
        		// 현장채용 관리에서 채용취소면 현재 현장직원 테이블에 데이터 존재유무를 체크.
        		// del_yn 을 Y로 업데이트.
        		if (useyn.toString().equals("N") || useyn.toString().equals("D")) {
        			List<Map<String, Object>> resultList = new ArrayList<>();
        			resultList = operateService.AccountMemberAllList(row);
        			if (!resultList.isEmpty()) {
        				String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        				row.put("del_yn", "Y");
        				row.put("del_dt", today);
            			iResult += operateService.AccountMembersSave(row);
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
     * part		: 운영,인사
     * method 	: AccountDispatchMemberAllList
     * comment 	: 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 조회
     */
    @GetMapping("Operate/AccountDispatchMemberAllList")
    public String AccountDispatchMemberAllList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountDispatchMemberAllList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영,인사
     * method 	: AccountSubRestaurantSave
     * comment 	: 급식사업부 -> 운영->현장관리, 인사->현장관리 -> 파출관리 저장
     */
    @PostMapping("Operate/AccountDispatchMembersSave")
    private String AccountDispatchMembersSave(@RequestBody Map<String, Object> paramMap) {

        List<Map<String, Object>> data = (List<Map<String, Object>>) paramMap.get("data");

        int iResult = 0;

        for (Map<String, Object> row : data) {

            // ✅ member_id가 없을 때만 생성
            Object memberIdObj = row.get("member_id");
            String memberId = memberIdObj == null ? "" : String.valueOf(memberIdObj).trim();

            if (memberId.isEmpty()) {
                memberId = operateService.NowDateKey();
                row.put("member_id", memberId);
            }

            iResult += operateService.AccountDispatchMembersSave(row);
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
     * part		: 운영
     * method 	: AccountDinnersNumberList
     * comment 	: 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 조회
     */
    @GetMapping("Operate/AccountDinnersNumberList")
    public String AccountDinnersNumberList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AccountDinnersNumberList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: AccountDinnersNumberSave
     * comment 	: 급식사업부 -> 운영관리 -> 거래처관리 -> 식수현황 저장
     */
    @PostMapping("Operate/AccountDinnersNumberSave")
    private String AccountDinnersNumberSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += operateService.AccountDinnersNumberSave(paramMap);
        }
    	
    	if (iResult > 0) {
			for (Map<String, Object> paramMap : paramList) {
				paramMap.put("year", paramMap.get("diner_year"));
				paramMap.put("month", paramMap.get("diner_month"));
				iResult += operateService.BudgetTotalSave(paramMap);
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
     * part		: 운영
     * method 	: BudgetManageMentList
     * comment 	: 급식사업부 -> 운영관리 -> 예산관리 조회
     */
    @GetMapping("Operate/BudgetManageMentList")
    public String BudgetManageMentList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.BudgetManageMentList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: BudgetStandardList
     * comment 	: 급식사업부 -> 운영관리 -> 예산관리(예산기준) 조회
     */
    @GetMapping("Operate/BudgetStandardList")
    public String BudgetStandardList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.BudgetStandardList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: MealsNumberList
     * comment 	: 급식사업부 -> 운영관리 -> 예산관리(배식횟수) 조회
     */
    @GetMapping("Operate/MealsNumberList")
    public String MealsNumberList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.MealsNumberList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 운영
     * method 	: BudgetTableSave
     * comment 	: 급식사업부 -> 운영관리 -> 예산관리 저장
     */
	@PostMapping("Operate/BudgetTableSave")
	public String BudgetTableSave(@RequestBody Map<String, Object> payload) {
		
		// payload에서 rows만 꺼냄
	    List<Map<String, Object>> rows = (List<Map<String, Object>>) payload.get("rows");
		
		int iResult = 0;
		
		for (Map<String, Object> paramMap : rows) {
			iResult += operateService.BudgetTableSave(paramMap);
        }
		
		if (iResult > 0) {
			for (Map<String, Object> paramMap : rows) {
				iResult += operateService.BudgetTotalSave(paramMap);
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
     * part		: 운영
     * method 	: MealsNumberList
     * comment 	: 현장관리 -> 근태관리 -> 연차 정보 조회
     */
    @GetMapping("Operate/AnnualLeaveList")
    public String AnnualLeaveList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.AnnualLeaveList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: MealsNumberList
     * comment 	: 현장관리 -> 근태관리 -> 초과근무 조회
     */
    @GetMapping("Operate/OverTimeList")
    public String OverTimeList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.OverTimeList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 운영
     * method 	: OperateMemberList
     * comment 	: 운영관리 -> 일정관리 -> 운영팀 조회
     */
    @GetMapping("Operate/OperateMemberList")
    public String OperateMemberList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.OperateMemberList();
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: OperateScheduleList
     * comment 	: 운영관리 -> 일정관리 -> 캘린더 조회
     */
    @GetMapping("Operate/OperateScheduleList")
    public String OperateScheduleList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.OperateScheduleList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영
     * method 	: OperateScheduleTodayList
     * comment 	: 메인화면 -> 운영팀 당일 일정 조회
     */
    @GetMapping("Operate/OperateScheduleTodayList")
    public String OperateScheduleTodayList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = operateService.OperateScheduleTodayList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /* 
	 * part		: 운영
     * method 	: OperateScheduleSave
     * comment 	: 운영관리 -> 일정관리 -> 캘린더 저장
     */
	@PostMapping("Operate/OperateScheduleSave")
	public String OperateScheduleSave(@RequestBody Map<String, Object> paramMap) {
		
		int iResult = 0;
		iResult = operateService.OperateScheduleSave(paramMap);
		
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
    
}
