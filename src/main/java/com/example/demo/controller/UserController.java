package com.example.demo.controller;

import java.util.HashMap;
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
import com.example.demo.service.UserService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@RestController
public class UserController {
	
	private final UserService userService;
	
    @Autowired
    public UserController(UserService userService, WebConfig webConfig) {
    	this.userService = userService;
    }
	
    /*
     * method 	: Login
     * comment 	: 로그인
     */
    @PostMapping("/User/Login")
    public String Login(@RequestBody HashMap<String, Object> map) {
    	
    	Map<String, Object> resultMap =  userService.Login(map);
    	JsonObject obj = new JsonObject();
    	JsonObject data = new JsonObject();
    	
    	System.out.println(resultMap.get("status_code").toString());
    	
    	if(resultMap.get("status_code").toString().equals("400")) {
    		obj.addProperty("code", resultMap.get("status_code").toString());
    		obj.addProperty("msg", "아이디 혹은 비밀번호를 확인하세요.");
        	
    	} else {
    		obj.addProperty("user_id", resultMap.get("user_id").toString());
        	obj.addProperty("user_type", resultMap.get("user_type").toString());
        	obj.addProperty("position", resultMap.get("position").toString());
        	obj.addProperty("department", resultMap.get("department").toString());
        	obj.addProperty("account_id", resultMap.get("account_id").toString());
        	obj.addProperty("code", "status_code");
    	}
    	
    	return obj.toString();
    }
    
    /*
     * method 	: UserRgt
     * comment 	: 사용자 등록
     */
	@PostMapping("/User/UserRgt")
	public String UserRgt(@RequestBody Map<String,Object> paramMap) {
		
		int iResult = 0 ;
		
		// info, detail 꺼내기
	    Map<String, Object> info   = (Map<String, Object>) paramMap.get("info");
	    Map<String, Object> detail = (Map<String, Object>) paramMap.get("detail");

	    System.out.println(info);
	    System.out.println(detail);
	    
	    iResult += userService.UserRgt(info);
	    iResult += userService.UserRgtDetail(detail);
	    
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
     * method 	: UserRecordSheetList
     * comment 	: 신사업(일단,...)근태관리 조회
     */
	@GetMapping("User/UserRecordSheetList")
	public String UserRecordSheetList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.UserRecordSheetList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/*
     * method 	: UserMemberList
     * comment 	: 신사업(일단,...)근태관리 직원정보 조회
     */
	@GetMapping("User/UserMemberList")
	public String UserMemberList(@RequestParam Map<String, Object> paramMap) {
		List<Map<String, Object>> resultList = userService.UserMemberList(paramMap);
		
		return new Gson().toJson(resultList);
	}
	
	/*
     * method 	: ContractEndAccountList
     * comment 	: 3개월 이내 종료업장 조회
     */
	@GetMapping("/User/ContractEndAccountList")
	public String ContractEndAccountList() {
		List<Map<String, Object>> resultList = userService.ContractEndAccountList();
		
		return new Gson().toJson(resultList);
	}
}
