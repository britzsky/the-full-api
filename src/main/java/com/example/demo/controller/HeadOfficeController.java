package com.example.demo.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
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
     * comment 	: 본사 -> 캘린더 조회
     */
	@GetMapping("HeadOffice/WeekMenuList")
	public String WeekMenuList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.WeekMenuList(paramMap);
		
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
     * method 	: WeekMenuList
     * comment 	: 본사 -> 관리표 -> 손익표 조회
     */
	@GetMapping("HeadOffice/ProfitLossTableList")
	public String ProfitLossTableList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = headOfficeService.ProfitLossTableList(paramMap);
		
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
}
