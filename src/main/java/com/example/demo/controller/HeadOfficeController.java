package com.example.demo.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.WebConfig;
import com.example.demo.service.HeadOfficeService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
public class HeadOfficeController {
	
	private final HeadOfficeService headOfficeService;
	
    @Autowired
    public HeadOfficeController(HeadOfficeService headOfficeService, WebConfig webConfig) {
    	this.headOfficeService = headOfficeService;
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
     * method 	: WeekMenuList
     * comment 	: 본사 -> 관리표 -> 손익표 엑섹다운
     */
	@GetMapping("HeadOffice/ExcelDaownProfitLossTableList")
	public String ExcelDaownProfitLossTableList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ExcelDaownProfitLossTableList(paramMap);
		
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
}
