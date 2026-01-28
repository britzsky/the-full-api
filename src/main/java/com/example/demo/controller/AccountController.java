package com.example.demo.controller;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.WebConfig;
import com.example.demo.service.AccountService;
import com.example.demo.service.HeadOfficeService;
import com.example.demo.utils.DateUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
public class AccountController {

	private final AccountService accountService;
	private final HeadOfficeService headOfficeService;
	private final String uploadDir;
	
    @Autowired
    public AccountController(
    			AccountService accountService, 
    			HeadOfficeService headOfficeService, 
    			WebConfig webConfig,
    			@Value("${file.upload-dir}") String uploadDir
    		) {
    	this.accountService = accountService;
    	this.headOfficeService = headOfficeService;
    	this.uploadDir = uploadDir;
    }
    
    /*
     * method 	: AccountList
     * comment 	: 거래처 조회
     */
    @GetMapping("/Account/AccountList")
    public String AccountList(@RequestParam(required = false) Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	int iAccountType = Integer.parseInt(paramMap.get("account_type").toString());
    	resultList = accountService.AccountMemberList(iAccountType);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * method 	: AccountDirectList
     * comment 	: 신사업팀 -> 직영점 조회
     */
    @GetMapping("/Account/AccountDirectList")
    public String AccountDirectList() {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountDirectList();
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * method 	: AccountUtilMemberList
     * comment 	: 유틸 직원 조회
     */
    @GetMapping("/Account/AccountUtilMemberList")
    public String AccountUtilMemberList(@RequestParam(required = false) Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountUtilMemberList();
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * method 	: AccountUtilMappingList
     * comment 	: 유틸 직원 매핑 정보 조회
     */
    @GetMapping("/Account/AccountUtilMappingList")
    public String AccountUtilMappingList(@RequestParam(required = false) Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountUtilMappingList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * method 	: AccountUtilMemberMappingSave
     * comment 	: 유틸 직원 매핑 정보 저장
     */
    @PostMapping("/Account/AccountUtilMemberMappingSave")
    private String AccountUtilMemberMappingSave(@RequestBody List<Map<String, Object>> paramList) { 
    	
        int iResult = 0;
        
        for (Map<String, Object> paramMap : paramList) {
        	iResult += accountService.AccountUtilMemberMappingSave(paramMap);
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
     * part		: 현장
     * method 	: AccountTallySheetList
     * comment 	: 거래처 -> 집계표 조회
     */
    @GetMapping("/Account/AccountTallySheetList")
    public String AccountTallySheetList(@RequestParam Map<String, Object> paramMap) {
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
    	resultList = accountService.AccountTallySheetList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 현장
     * method 	: AccountSave
     * comment 	: 거래처 -> 집계표 저장
     */
    @PostMapping("Account/AccountSave")
    public String AccountSave(@RequestParam Map<String, Object> paramMap) {
    	
    	JsonObject obj =new JsonObject();
    	
    	if(accountService.AccountSave(paramMap) > 0) {
    		obj.addProperty("code", 200);
    		obj.addProperty("message", "성공");
    	} else {
    		obj.addProperty("code", 400);
    		obj.addProperty("message", "실패");
    	}
    	
    	return obj.toString();
    }
    
    /*
     * part		: 현장
     * method 	: AccountRecordDispatchList
     * comment 	: 거래처 -> 출근부 -> 파출직원 조회
     */
    @GetMapping("Account/AccountRecordDispatchList")
    public String AccountRecordDispatchList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountRecordDispatchList(paramMap);
    	
    	return new Gson().toJson(resultList); 
    }
    
    /*
     * part		: 현장
     * method 	: AccountRecordMemberList
     * comment 	: 거래처 -> 출근부 -> 직원 조회
     */
    @GetMapping("Account/AccountRecordMemberList")
    public String AccountRecordMemberList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountRecordMemberList(paramMap);
    	
    	return new Gson().toJson(resultList); 
    }
    
    /*
     * part		: 현장
     * method 	: AccountRecordMemberList
     * comment 	: 거래처 -> 출근부 -> 출근현황 조회
     */
    @GetMapping("Account/AccountRecordSheetList")
    public String AccountRecordSheetList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountRecordSheetList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    @GetMapping("Account/AccountMemberRecordTime")
    public String AccountMemberRecordTime(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountMemberRecordTime(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 현장
     * method 	: AccountMemberRecordSave
     * comment 	: 거래처 -> 출근부 -> 출근부 정보 저장
     */
    @PostMapping("Account/AccountRecordSave")
    public String AccountMemberRecordSave(@RequestBody Map<String, List<Map<String, Object>>> payload) {
    	
    	int iResult = 0;
    	
    	List<Map<String, Object>> normalRecords = payload.get("normalRecords");
        List<Map<String, Object>> disRecords = payload.get("disRecords");
        List<Map<String, Object>> recRecords = payload.get("recRecords");
        
        boolean bType = true;
        
        // normalRecords, recRecords 를 조건에 따라 담을 List
        List<Map<String, Object>> objRecords = new ArrayList<Map<String, Object>>();
        if (!normalRecords.isEmpty()) {
        	bType = true;
        	objRecords = normalRecords;
        } else if (!recRecords.isEmpty()) {
        	bType = false;
        	objRecords = recRecords;
        }
        
        for (Map<String, Object> row : objRecords) {
        	if (bType) {
        		iResult += accountService.AccountMemberRecordSave(row);
            	iResult += accountService.processProfitLossV2(row);
        	} else {
        		iResult += accountService.AccountMemberRecRecordSave(row);
            	iResult += accountService.processProfitLossV2(row);
        	}
        	
        	if (row.get("type") != null) {
        		
        		Map<String, Object> annualMap = new HashMap<String, Object>();
        		Map<String, Object> overMap = new HashMap<String, Object>();
        		
        		int iType = (int) row.get("type");
        		int iPositionType = Integer.parseInt(row.get("position_type").toString());
        		
        		// 1. 연, 월, 일로 LocalDate 객체 생성
                LocalDate date = LocalDate.of((int)row.get("record_year"), (int)row.get("record_month"), (int)row.get("record_date"));

                // 2. 원하는 형식(YYYY-MM-DD)의 포맷터 정의
                // 'MM'과 'dd'는 각각 월과 일을 2자리 숫자로 표현하며, 필요시 0으로 채웁니다.
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy-MM");
                
                String ledgerDt = date.format(formatter);			// 각 연차, 반차, 초과 등 부여 혹은 사용 날짜.
                String ledgerMonth = date.format(formatter2);		// 대체근무 연차 부여 사용 기한.
                String position = row.get("position").toString();	// 초과 일 때, 영양사만 지급.
                
                // 초과근무 시간 사용이 있는 지 체크.
                if (iType == 1) {
                	if (position.equals("영양사") || iPositionType == 1) {
                		// 시각 파싱
                		DateTimeFormatter minutFormatter = DateTimeFormatter.ofPattern("H:m");
                		
                        LocalTime startTime = LocalTime.parse(row.get("start_time").toString(), minutFormatter);
                        LocalTime endTime = LocalTime.parse(row.get("end_time").toString(), minutFormatter);
                        LocalTime orgStartTime = LocalTime.parse(row.get("org_start_time").toString(), minutFormatter);
                        LocalTime orgEndTime = LocalTime.parse(row.get("org_end_time").toString(), minutFormatter);
                        
                        Duration startDuration = Duration.between(startTime, orgStartTime);
                        Duration endDuration = Duration.between(endTime, orgEndTime);
                        
                        long startMinutes = startDuration.toMinutes(); // 결과: 90 (분)
                        long endMinutes = endDuration.toMinutes(); // 결과: 90 (분)
                        
                        double decimalHours = startMinutes / 60.0;
                        double decimalHours2 = endMinutes / 60.0;
                        
                        if (decimalHours != 0 || decimalHours2 != 0) {
                        	
                        	if (decimalHours != 0) {
                        		overMap.put("times", -decimalHours);
                        	}
                        	if (decimalHours2 != 0) {
                        		overMap.put("times", -decimalHours2);
                        	}
                        	
                        	overMap.put("member_id", row.get("member_id"));
                        	overMap.put("type", "U");
                        	overMap.put("over_dt", ledgerDt);
                        	overMap.put("reason", "보상시간 사용");
                        }
                	}
                }
                
                // 초과
                if (iType == 3) {
                	if (position.equals("영양사") || iPositionType == 1) {
                		
                        // 초과근무시간 추출.
                		double dOver = Double.parseDouble(row.get("note").toString());
                		
                		overMap.put("member_id", row.get("member_id"));
                		overMap.put("times", dOver);
                		overMap.put("type", "G");
            			overMap.put("over_dt", ledgerDt);
            			overMap.put("reason", "초과근무로 보상시간 부여");
                	}
        		}
                // 대체근무
                if (iType == 8) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", 1);
        			annualMap.put("type", "G");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "대체근무로 인한 연차 부여 " + ledgerMonth);
        		}
        		// 연차
        		if (iType == 9) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", -1);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "개인사정으로 인한 연차 사용");
        		}
        		// 반차
        		if (iType == 10) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", -0.5);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "개인사정으로 인한 반차 사용");
        		}
        		// 대체휴무
        		if (iType == 11) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", 0);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", row.get("note").toString());
        		}
        		// 병가
        		if (iType == 12) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", -1);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "개인사정으로 인한 반차 사용");
        		}
        		// 출산휴가
        		if (iType == 13) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", 0);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "출산휴가");
        		}
        		// 육아휴직
        		if (iType == 14) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", 0);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "육아휴직");
        		}
        		// 하계휴가
        		if (iType == 15) {
        			annualMap.put("member_id", row.get("member_id"));
        			annualMap.put("days", 0);
        			annualMap.put("type", "U");
        			annualMap.put("ledger_dt", ledgerDt);
        			annualMap.put("reason", "하계휴가");
        		}
        		if (!annualMap.isEmpty()) {
        			iResult += accountService.AccountAnnualLeaveLedgerSave(annualMap);
        		}
        		if (!overMap.isEmpty()) {
        			iResult += accountService.AccountOverTimeLedgerSave(overMap);
        		}
        	}
        }
        for (Map<String, Object> row : disRecords) {	
        	iResult += accountService.AccountDispatchRecordSave(row);
        	iResult += accountService.processProfitLossV2(row);
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
    public static String formatNumbers(int year, int month, int day) {
        // C 스타일의 형식 지정자를 사용합니다.
        // %d: 10진수 정수
        // %02d: 2자리로 표현하며, 2자리보다 작으면 앞에 0으로 채웁니다.
        return String.format("%d-%02d-%02d", year, month, day);
    }
    
    /*
     * part		: 현장
     * method 	: AccountMemberRecordSave
     * comment 	: 거래처 -> 출근부 -> 파출직원 저장
     */
    @PostMapping("Account/AccountDispatchMemberSave")
    public String AccountDispatchMemberSave(@RequestParam Map<String, Object> paramMap) {
    	
    	int iResult = 0;
    	
    	// ✅ member_id가 없을 때만 생성
        Object memberIdObj = paramMap.get("member_id");
        String memberId = memberIdObj == null ? "" : String.valueOf(memberIdObj).trim();
    	
        if (memberId.isEmpty()) {
            memberId = accountService.NowDateKey();
            paramMap.put("member_id", memberId);
        }
    	
    	iResult = accountService.AccountDispatchMemberSave(paramMap);
    	
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
     * method 	: AccountInfoList
     * comment 	: 거래처 -> 거래처 상세 조회
     */
    @GetMapping("Account/AccountInfoList")
    public String AccountInfoList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountInfoList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 영업
     * method 	: AccountInfoList_2
     * comment 	: 거래처 -> 거래처 상세 조회
     */
    @GetMapping("Account/AccountInfoList_2")
    public String AccountInfoList_2(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountInfoList_2(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 영업
     * method 	: AccountInfoList_3
     * comment 	: 거래처 -> 거래처 상세 조회
     */
    @GetMapping("Account/AccountInfoList_3")
    public String AccountInfoList_3(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountInfoList_3(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 영업
     * method 	: AccountInfoList_4
     * comment 	: 거래처 -> 거래처 상세 조회
     */
    @GetMapping("Account/AccountInfoList_4")
    public String AccountInfoList_4(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountInfoList_4(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 영업
     * method 	: AccountInfoList_5
     * comment 	: 거래처 -> 거래처 상세 조회
     */
    @GetMapping("Account/AccountInfoList_5")
    public String AccountInfoList_5(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountInfoList_5(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 영업
     * method 	: AccountMemberRecordSave
     * comment 	: 거래처 -> 거래처 상세 저장
     */
    @SuppressWarnings("unchecked")
    @PostMapping("Account/AccountInfoSave")
    public String AccountInfoSave(@RequestBody(required = false) Map<String, Object> payload) {

        JsonObject obj = new JsonObject();

        if (payload == null) {
            obj.addProperty("code", 400);
            obj.addProperty("message", "요청 데이터가 비어 있습니다.");
            return obj.toString();
        }

        int iResult = 0;

        Map<String, Object> formData = (Map<String, Object>) payload.get("formData");
        Map<String, Object> payloadMap = new HashMap<>();

        if (formData != null) {
            payloadMap.putAll(formData);
            // 거래처 기본정보 저장
            iResult += accountService.AccountSave(formData);
        }

        List<Map<String, Object>> priceData   = (List<Map<String, Object>>) payload.get("priceData");
        List<Map<String, Object>> etcData     = (List<Map<String, Object>>) payload.get("etcData");
        List<Map<String, Object>> managerData = (List<Map<String, Object>>) payload.get("managerData");
        List<Map<String, Object>> eventData   = (List<Map<String, Object>>) payload.get("eventData");

        if (etcData != null) {
            for (Map<String, Object> row : etcData) {
                if (row != null) payloadMap.putAll(row);
            }
        }

        if (eventData != null) {
            for (Map<String, Object> row : eventData) {
                if (row != null) payloadMap.putAll(row);
            }
        }

        if (managerData != null) {
            for (Map<String, Object> row : managerData) {
                if (row != null) payloadMap.putAll(row);
            }
        }

        if (priceData != null) {
            for (Map<String, Object> row : priceData) {
                if (row != null) {
                	payloadMap.putAll(row);
                	
                	Object priceObj = row.get("diet_price");
                	int dietPrice = 0;

                	if (priceObj != null && !priceObj.toString().isEmpty()) {
                	    dietPrice = Integer.parseInt(priceObj.toString());
                	}
                	
                	Object beforePriceObj = row.get("before_diet_price");
                	int beforeDietPrice = 0;

                	if (beforePriceObj != null && !beforePriceObj.toString().isEmpty()) {
                		beforeDietPrice = Integer.parseInt(beforePriceObj.toString());
                	}
                	
                	List<Map<String, Object>> resultList = new ArrayList<>();
                	resultList = accountService.AccountDietPriceHistoryList(payloadMap);
                	
                	for (Map<String, Object> dietRow : resultList) {
                		
                		Object dietPriceObj = dietRow.get("diet_price");
                    	int dietPrice2 = 0;

                    	if (dietPriceObj != null && !dietPriceObj.toString().isEmpty()) {
                    		dietPrice2 = Integer.parseInt(dietPriceObj.toString());
                    	}
                		
                		// 식단가와 내역의 식단가가 다르면 저장.
                    	if (dietPrice != dietPrice2) {
                    		iResult += accountService.AccountDietPriceHistorySave(payloadMap);
                    	}
                	}
                } 
            }
        }
        
        iResult += accountService.AccountInfoSave(payloadMap);

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
     * part		: 영업
     * method 	: AccountBusinessImgList
     * comment 	: 거래처 -> 거래처 상세 이미지 조회
     */
    @GetMapping("Account/AccountBusinessImgList")
    public String AccountBusinessImgList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountBusinessImgList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    @PostMapping("Account/AccountBusinessImgUpload")
    public String uploadFiles(
            @RequestParam("account_id") String accountId,
            @RequestParam(value = "business_report", required = false) MultipartFile businessReport,
            @RequestParam(value = "business_regist", required = false) MultipartFile businessRegist,
            @RequestParam(value = "kitchen_drawing", required = false) MultipartFile kitchenDrawing,
            @RequestParam(value = "nutritionist_room_img", required = false) MultipartFile nutritionistRoomImg,
            @RequestParam(value = "chef_lounge_img", required = false) MultipartFile chefLoungeImg
    ) throws IOException {
        Map<String, Object> filePathMap = new HashMap<>();

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
        
        if (nutritionistRoomImg != null && !nutritionistRoomImg.isEmpty()) {
            String path = saveFile(accountId, "nutritionist_room_img", nutritionistRoomImg);
            filePathMap.put("nutritionist_room_img", path);
        }
        
        if (chefLoungeImg != null && !chefLoungeImg.isEmpty()) {
            String path = saveFile(accountId, "chef_lounge_img", chefLoungeImg);
            filePathMap.put("chef_lounge_img", path);
        }
        
        filePathMap.put("account_id", accountId);
        
        System.out.println("account_id == " + filePathMap.get("account_id"));
        System.out.println("business_report == " + filePathMap.get("business_report"));
        System.out.println("business_regist == " + filePathMap.get("business_regist"));
        System.out.println("kitchen_drawing == " + filePathMap.get("kitchen_drawing"));
        System.out.println("nutritionist_room_img == " + filePathMap.get("nutritionist_room_img"));
        System.out.println("chef_lounge_img == " + filePathMap.get("chef_lounge_img"));

        accountService.insertOrUpdateFile(filePathMap);
    	
    	return filePathMap.toString();
    }

    private String saveFile(String accountId, String type, MultipartFile file) throws IOException {
        // 프로젝트 루트 대신 static 폴더 경로 사용
        String staticPath = new File(uploadDir).getAbsolutePath();
        String basePath = staticPath + "/" + accountId + "/" + type + "/";
        Path dirPath = Paths.get(basePath);
        Files.createDirectories(dirPath); // 폴더 없으면 생성

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = UUID.randomUUID() + "_" + originalFileName;
        Path filePath = dirPath.resolve(uniqueFileName);

        file.transferTo(filePath.toFile()); // 파일 저장

        // 브라우저 접근용 경로 반환
        return "/image/" + accountId + "/" + type + "/" + uniqueFileName;
    }
    
    private String saveReceiptFile(String saleId, MultipartFile file) throws IOException {
    	
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
        return "/image/" + "receipt" + "/" + saleId + "/" + uniqueFileName;
    }
    
    /*
     * part		: 회계
     * method 	: AccountDeadlineBalanceList
     * comment 	: 회계 -> 매출마감/미수잔액 조회
     */
    @GetMapping("Account/AccountDeadlineBalanceList")
    public String AccountDeadlineBalanceList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountDeadlineBalanceList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 회계
     * method 	: AccountDeadlineBalanceList
     * comment 	: 회계 -> 매출마감/미수잔액 입금내역 조회
     */
    @GetMapping("Account/AccountDepositHistoryList")
    public String AccountDepositHistoryList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountDepositHistoryList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 회계
     * method 	: AccountDeadlineBalanceSave
     * comment 	: 회계 -> 매출마감/미수잔액 저장
     */
    @PostMapping("Account/AccountDeadlineBalanceSave")
    private String AccountDeadlineBalanceSave(@RequestBody Map<String, Object> paramMap) {
        
    	// 월의 총 일수 구함.
    	LocalDate today = LocalDate.now();
        int lastDay = today.lengthOfMonth(); // 이 달의 마지막 일자 (28,29,30,31 중 하나)
    	
        int iResult = 0;
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) paramMap.get("rows");
        
        if (rows == null || rows.isEmpty()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("code", 400);
            obj.addProperty("message", "데이터(rows)가 누락되었거나 비어있습니다.");
            return obj.toString();
        }

        for (Map<String, Object> param : rows) {
        	// accountDataMap을 사용하여 로직 처리
            if(param.get("balance_price") != null || param.get("before_price") != null) {
                Map<String, Object> balanceMap = new HashMap<String, Object>();
                balanceMap.put("balance_price", param.get("balance_price"));
                balanceMap.put("before_price", param.get("before_price"));
                balanceMap.put("account_id", param.get("account_id")); // account_id 정상 추출!
                
                // 총 미수잔액 저장
                iResult += accountService.AccountBalancePriceSave(balanceMap);
            }
            
            iResult += accountService.processProfitLoss(param);
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
     * part		: 회계
     * method 	: AccountBalancePriceSave
     * comment 	: 회계 -> 매출마감/미수잔액 총 미수금액 저장
     */
    @PostMapping("Account/AccountBalancePriceSave")
    private String AccountBalancePriceSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += accountService.AccountBalancePriceSave(paramMap);
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
     * part		: 회계
     * method 	: AccountDepositHistorySave
     * comment 	: 회계 -> 매출마감/미수잔액 입금내역 저장
     */
    @PostMapping("Account/AccountDepositHistorySave")
    private String AccountDepositHistorySave(@RequestBody Map<String, Object> paramMap) {
    	
    	int iResult = 0;
    	
    	iResult += accountService.AccountDepositHistorySave(paramMap);
    	iResult += accountService.AccountBalancePriceSave(paramMap);
    	
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
     * part		: 회계
     * method 	: AccountDeadlineDifferencePriceSearch
     * comment 	: 회계 -> 타입별 차액 조회
     */
    @GetMapping("Account/AccountDeadlineDifferencePriceSearch")
    public String AccountDeadlineDifferencePriceSearch(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountDeadlineDifferencePriceSearch(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 회계
     * method 	: AccountDeadlineFilesList
     * comment 	: 회계 -> 마감자료 조회
     */
    @GetMapping("Account/AccountDeadlineFilesList")
    public String AccountDeadlineFilesList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountDeadlineFilesList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 회계
     * method 	: AccountDeadlineFilesSave
     * comment 	: 회계 -> 마감자료 저장
     */
    @PostMapping("Account/AccountDeadlineFilesSave")
    private String AccountDeadlineFilesSave(@RequestParam("file") MultipartFile file,
								    	    @RequestParam("account_id") String accountId,
								    	    @RequestParam("year") String year,
								    	    @RequestParam("month") String month,
    										@RequestParam("file_yn") String file_yn) {
    	
    	Map<String, Object> paramMap = new HashMap<String, Object>();
    	
    	paramMap.put("account_id", accountId);
    	paramMap.put("year", year);
    	paramMap.put("month", month);
    	paramMap.put("file_yn", file_yn);
    	
    	try {
    		paramMap.put("deadline_file", saveFile(file, "deadline", "files", accountId));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	int iResult = 0;
    	
    	iResult = accountService.AccountDeadlineFilesSave(paramMap);
    	
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
     * method 	: saveFile
     * comment 	: 거래처 파일업로드 공통
     */
    private String saveFile(MultipartFile file, String type, String gubun, String folder) throws IOException {
    	
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
        
        return resultPath;
    }
    
    @DeleteMapping("Account/AccountDeadlineFilesDelete")
    public String deleteDeadlineFile(@RequestParam("account_id") String accountId,
						             @RequestParam("year") String year,
						             @RequestParam("month") String month,
						             @RequestParam("filePath") String filePath,
						             @RequestParam("file_yn") String file_yn) {
        JsonObject obj = new JsonObject();
        try {
            // ✅ 삭제할 파일 경로 구성
            Path filePath2 = Paths.get(uploadDir + filePath);
            File file = filePath2.toFile();

            if (!file.exists()) {
                obj.addProperty("code", 404);
                obj.addProperty("message", "파일이 존재하지 않습니다: " + filePath2);
                return obj.toString();
            }

            boolean deleted = file.delete();
            
            Map<String, Object> paramMap = new HashMap<String, Object>();
        	
        	paramMap.put("account_id", accountId);
        	paramMap.put("year", year);
        	paramMap.put("month", month);
        	paramMap.put("file_yn", file_yn);
        	paramMap.put("deadline_file", null);
        	
        	int iResult = 0;
        	
        	iResult = accountService.AccountDeadlineFilesSave(paramMap);

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
     * part		: 회계
     * method 	: AccountPurchaseSave
     * comment 	: 회계 -> 매입마감 저장
     */
    @PostMapping("Account/AccountPurchaseSave")
    private String AccountPurchaseSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += accountService.AccountPurchaseSave(paramMap);
            // 여러 타입의 날짜형식을 매핑.
            LocalDate date = DateUtils.parseFlexibleDate(paramMap.get("saleDate").toString());
            // 손익표, 예산 적용을 위해 SaleDate 에서 연도와 월을 추출.
            int year = date.getYear();        // 2026
            int month = date.getMonthValue(); // 1~12
            paramMap.put("year", year);
            paramMap.put("month", month);
            iResult += accountService.TallySheetPaymentSave(paramMap);
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
     * part		: 현장
     * method 	: AccountTallyToPurchaseSave
     * comment 	: 집계표 -> 집계표 수정 시, 매입마감도 반영
     */
    @PostMapping(
      value = "/Account/AccountTallyToPurchaseSave",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE
    )
    public String AccountTallyToPurchaseSave(
        @RequestParam Map<String, String> params,
        @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        int iResult = 0;

        // ✅ String -> Object 맵으로 변환 (서비스에서 Object 기대하면)
        Map<String, Object> paramMap = new HashMap<>(params);

        // ✅ 프론트에서 보내는 필드명에 맞춰 날짜 파싱 (saleDate가 아니라 cell_date)
        LocalDate date = DateUtils.parseFlexibleDate(paramMap.get("saleDate").toString());

        int year = date.getYear();
        int month = date.getMonthValue();
        paramMap.put("year", year);
        paramMap.put("month", month);

        // ✅ file 처리 필요하면 paramMap에 담거나 별도 저장 후 경로 넣기
        if (file != null && !file.isEmpty()) {
        	try {
				String resultPath = saveReceiptFile(paramMap.get("sale_id").toString(), file);
				paramMap.put("receipt_image", resultPath);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        iResult += accountService.AccountPurchaseSave(paramMap);
        iResult += accountService.TallySheetPaymentSave(paramMap);

        JsonObject obj = new JsonObject();
        obj.addProperty("code", iResult > 0 ? 200 : 400);
        obj.addProperty("message", iResult > 0 ? "성공" : "실패");
        return obj.toString();
    }

    
    /*
     * part		: 회계
     * method 	: AccountPurchaseDetailSave
     * comment 	: 회계 -> 매입집계 저장
     */
    @PostMapping("Account/AccountPurchaseDetailSave")
    private String AccountPurchaseDetailSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
            iResult += accountService.AccountPurchaseDetailSave(paramMap);
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
     * part		: 운영,회계
     * method 	: AccountIssueList
     * comment 	: 운영,회계 -> 거래처 이슈 조회
     */
    @GetMapping("Account/AccountIssueList")
    public String AccountDeadlineIssueList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountIssueList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    
    /*
     * part		: 운영,회계
     * method 	: AccountDeadlineIssueSave
     * comment 	: 운영,회계 -> 거래처 이슈 저장
     */
    @PostMapping("Account/AccountIssueSave")
    private String AccountIssueSave(@RequestBody Map<String, Object> paramMap) {
    	
    	// ✅ data 리스트 꺼내기
        List<Map<String, Object>> dataList = (List<Map<String, Object>>) paramMap.get("data");

        int iResult = 0;
        
        for (Map<String, Object> row : dataList) {
        	iResult += accountService.AccountIssueSave(row);
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
    public double roundHalfUpToFirstDecimalSafe(double value) {
        // 1. double 값을 BigDecimal로 변환
        BigDecimal bd = new BigDecimal(String.valueOf(value));
        
        // 2. 소수점 1자리까지 반올림 (HALF_UP 모드)
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        
        // 3. 다시 double로 변환하여 반환
        return bd.doubleValue();
    }
    /*
     * part		: 현장
     * method 	: AccountMappingList
     * comment 	: 현장 -> 집계표 -> 영수증 매장 확인 조회
     */
    public List<Map<String, Object>> AccountMappingList(String account_id) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	resultList = accountService.AccountMappingList(account_id);
    	
    	return resultList;
    }
    /*
     * part		: 회계
     * method 	: AccountPurchaseTallyList
     * comment 	: 회계 -> 매입 -> 매입마감 조회
     */
    @GetMapping("Account/AccountPurchaseTallyList")
    public String AccountPurchaseTallyList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountPurchaseTallyList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: AccountPurchaseTallyList
     * comment 	: 회계 -> 매입 -> 매입집계 조회
     */
    @GetMapping("Account/AccountPurchaseDetailList")
    public String AccountPurchaseDetailList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountPurchaseDetailList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: AccountPurchaseTallyList_tmp
     * comment 	: 회계 -> 매입 -> 매입집계(임시) 조회
     */
    @GetMapping("Account/AccountPurchaseDetailList_tmp")
    public String AccountPurchaseTallyList_tmp(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountPurchaseDetailList_tmp(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 현장
     * method 	: AccountPurchaseTallyPaymentList
     * comment 	: 집계표 -> 결제 리스트 조회
     */
    @GetMapping("Account/AccountPurchaseTallyPaymentList")
    public String AccountPurchaseTallyPaymentList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountPurchaseTallyPaymentList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: HeadOfficeCorporateCardList
     * comment 	: 회계 -> 본사 법인카드 목록 조회
     */
    @GetMapping("Account/HeadOfficeCorporateCardList")
    public String HeadOfficeCorporateCardList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.HeadOfficeCorporateCardList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: HeadOfficeCorporateCardPaymentList
     * comment 	: 회계 -> 본사 법인카드 결제내역 조회
     */
    @GetMapping("Account/HeadOfficeCorporateCardPaymentList")
    public String HeadOfficeCorporateCardPaymentList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	System.out.println(paramMap);
    	resultList = accountService.HeadOfficeCorporateCardPaymentList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: HeadOfficeCorporateCardPaymentDetailList
     * comment 	: 회계 -> 본사 법인카드 결제 상세내역 조회
     */
    @GetMapping("Account/HeadOfficeCorporateCardPaymentDetailList")
    public String HeadOfficeCorporateCardPaymentDetailList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.HeadOfficeCorporateCardPaymentDetailList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: HeadOfficeCorporateCardSave
     * comment 	: 회계 -> 본사 법인카드 저장
     */
    @PostMapping("Account/HeadOfficeCorporateCardSave")
    private String HeadOfficeCorporateCardSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
    		iResult += accountService.HeadOfficeCorporateCardSave(paramMap);
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
     * part		: 회계
     * method 	: HeadOfficeCorporateCardPaymentAllSave
     * comment 	: 회계 -> 본사 법인카드 결제내역 전체 저장
     */
    @PostMapping("Account/HeadOfficeCorporateCardPaymentAllSave")
    private String HeadOfficeCorporateCardPaymentAllSave(@RequestBody Map<String, Object> paramMap) {
    	
    	// 1. 'main' 리스트 추출
        List<Map<String, Object>> mainList = (List<Map<String, Object>>) paramMap.get("main");
        
        // 2. 'item' 리스트 추출
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) paramMap.get("item");
    	
        int iResult = 0;
        
    	if (mainList != null) {
    		for (Map<String, Object> mainMap : mainList) {
        		iResult += accountService.HeadOfficeCorporateCardPaymentSave(mainMap);
        		
        		// 손익표, 예산 프로시저 적용을 위한 연,월 추출.
        		String paymentDate = String.valueOf(mainMap.get("payment_dt")); // "2026-01-01"

        		LocalDate payDate = LocalDate.parse(paymentDate); // 기본 ISO(yyyy-MM-dd) 파싱됨
        		int year = payDate.getYear();        // 2026
        		int month = payDate.getMonthValue(); // 1~12
        		
        		mainMap.put("year", year);
        		mainMap.put("month", month);
        		
        		// 집계표도 다시 적용.
        		iResult += accountService.TallySheetCorporateCardPaymentSaveV2(mainMap);
            }
    	}
    	if (itemList != null) {
    		for (Map<String, Object> itemMap : itemList) {
        		iResult += accountService.HeadOfficeCorporateCardPaymentDetailLSave(itemMap);
        		
        		// 손익표, 예산 프로시저 적용을 위한 연,월 추출.
        		String paymentDate = String.valueOf(itemMap.get("payment_dt")); // "2026-01-01"

        		LocalDate payDate = LocalDate.parse(paymentDate); // 기본 ISO(yyyy-MM-dd) 파싱됨
        		int year = payDate.getYear();        // 2026
        		int month = payDate.getMonthValue(); // 1~12
        		
        		itemMap.put("year", year);
        		itemMap.put("month", month);
        		
        		// 집계표도 다시 적용.
        		iResult += accountService.TallySheetCorporateCardPaymentSaveV2(itemMap);
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
     * part		: 회계
     * method 	: AccountCorporateCardList
     * comment 	: 회계 -> 현장 법인카드 목록 조회
     */
    @GetMapping("Account/AccountCorporateCardList")
    public String AccountCorporateCardList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountCorporateCardList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: AccountCorporateCardPaymentList
     * comment 	: 회계 -> 현장 법인카드 결제내역 조회
     */
    @GetMapping("Account/AccountCorporateCardPaymentList")
    public String AccountCorporateCardPaymentList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountCorporateCardPaymentList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: AccountCorporateCardPaymentDetailList
     * comment 	: 회계 -> 현장 법인카드 결제 상세내역 조회
     */
    @GetMapping("Account/AccountCorporateCardPaymentDetailList")
    public String AccountCorporateCardPaymentDetailList(@RequestParam Map<String, Object> paramMap) {
    	List<Map<String, Object>> resultList = new ArrayList<>();
    	
    	resultList = accountService.AccountCorporateCardPaymentDetailList(paramMap);
    	
    	return new Gson().toJson(resultList);
    }
    /*
     * part		: 회계
     * method 	: AccountCorporateCardSave
     * comment 	: 현장 -> 현장 법인카드 저장
     */
    @PostMapping("Account/AccountCorporateCardSave")
    private String AccountCorporateCardSave(@RequestBody List<Map<String, Object>> paramList) {
    	
    	int iResult = 0;
    	
    	for (Map<String, Object> paramMap : paramList) {
    		iResult += accountService.AccountCorporateCardSave(paramMap);
    		iResult += accountService.TallySheetCorporateCardPaymentSaveV2(paramMap);
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
     * part		: 회계
     * method 	: AccountCorporateCardPaymentAllSave
     * comment 	: 회계 -> 현장 법인카드 결제내역 전체 저장
     */
    @PostMapping("Account/AccountCorporateCardPaymentAllSave")
    private String AccountCorporateCardPaymentAllSave(@RequestBody Map<String, Object> paramMap) {
    	
    	// 1. 'main' 리스트 추출
        List<Map<String, Object>> mainList = (List<Map<String, Object>>) paramMap.get("main");
        
        // 2. 'item' 리스트 추출
        List<Map<String, Object>> itemList = (List<Map<String, Object>>) paramMap.get("item");
    	
        int iResult = 0;
        
    	if (mainList != null) {
    		for (Map<String, Object> mainMap : mainList) {
        		iResult += accountService.AccountCorporateCardPaymentSave(mainMap);
        		
        		// 손익표, 예산 프로시저 적용을 위한 연,월 추출.
        		String paymentDate = String.valueOf(mainMap.get("payment_dt")); // "2026-01-01"

        		LocalDate payDate = LocalDate.parse(paymentDate); // 기본 ISO(yyyy-MM-dd) 파싱됨
        		int year = payDate.getYear();        // 2026
        		int month = payDate.getMonthValue(); // 1~12
        		
        		mainMap.put("year", year);
        		mainMap.put("month", month);
        		
        		// 집계표도 다시 적용.
        		iResult += accountService.TallySheetCorporateCardPaymentSave(mainMap);
            }
    	}
    	if (itemList != null) {
    		for (Map<String, Object> itemMap : itemList) {
        		iResult += accountService.AccountCorporateCardPaymentDetailLSave(itemMap);
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
    
}
